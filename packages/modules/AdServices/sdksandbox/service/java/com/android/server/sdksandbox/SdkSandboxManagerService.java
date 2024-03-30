/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.sdksandbox;

import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_SERVICE;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.sdksandbox.IRemoteSdkCallback;
import android.app.sdksandbox.ISdkSandboxManager;
import android.app.sdksandbox.SdkSandboxManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceControlViewHost;
import android.webkit.WebViewUpdateService;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.ISdkSandboxToSdkSandboxManagerCallback;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.pm.PackageManagerLocal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Implementation of {@link SdkSandboxManager}.
 *
 * @hide
 */
public class SdkSandboxManagerService extends ISdkSandboxManager.Stub {

    private static final String TAG = "SdkSandboxManager";
    private static final String PROPERTY_SDK_PROVIDER_CLASS_NAME =
            "android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME";

    private final Context mContext;
    private final SdkTokenManager mSdkTokenManager = new SdkTokenManager();

    private final ActivityManager mActivityManager;
    private final Handler mHandler;
    private final PackageManagerLocal mPackageManagerLocal;

    private final SdkSandboxServiceProvider mServiceProvider;

    private final Object mLock = new Object();

    // For communication between app<-ManagerService->RemoteCode for each codeToken
    // TODO(b/208824602): Remove from this map when an app dies.
    @GuardedBy("mLock")
    private final ArrayMap<IBinder, AppAndRemoteSdkLink> mAppAndRemoteSdkLinks = new ArrayMap<>();
    // TODO: Following 2 should be keyed by (packageName, uid) pair
    @GuardedBy("mLock")
    private final ArrayMap<Integer, HashSet<Integer>> mAppLoadedSdkUids = new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArraySet<Integer> mRunningInstrumentations = new ArraySet<>();

    private final SdkSandboxManagerLocal mLocalManager;


    SdkSandboxManagerService(Context context, SdkSandboxServiceProvider provider) {
        mContext = context;
        mServiceProvider = provider;
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        // Start the handler thread.
        HandlerThread handlerThread = new HandlerThread("SdkSandboxManagerServiceHandler");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        mPackageManagerLocal = LocalManagerRegistry.getManager(PackageManagerLocal.class);
        registerBroadcastReceivers();

        mLocalManager = new LocalImpl();
    }

    private void registerBroadcastReceivers() {
        // Register for package removal
        final IntentFilter packageRemovedIntentFilter = new IntentFilter();
        packageRemovedIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageRemovedIntentFilter.addDataScheme("package");
        BroadcastReceiver packageRemovedIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int sdkUid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                if (sdkUid == -1) {
                    return;
                }
                final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                if (replacing) {
                    mHandler.post(() -> onSdkUpdating(sdkUid));
                }
            }
        };
        mContext.registerReceiver(packageRemovedIntentReceiver, packageRemovedIntentFilter,
                /*broadcastPermission=*/null, mHandler);

        // Register for package addition and update
        final IntentFilter packageAddedIntentFilter = new IntentFilter();
        packageAddedIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageAddedIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageAddedIntentFilter.addDataScheme("package");
        BroadcastReceiver packageAddedIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String packageName = intent.getData().getSchemeSpecificPart();
                final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                // TODO(b/223386213): We could miss broadcast or app might be started before we
                // handle broadcast.
                mHandler.post(
                        () -> reconcileSdkData(packageName, uid, /* forInstrumentation= */ false));
            }
        };
        mContext.registerReceiver(packageAddedIntentReceiver, packageAddedIntentFilter,
                /*broadcastPermission=*/null, mHandler);
    }

    private void onSdkUpdating(int sdkUid) {
        final ArrayList<Integer> appUids = new ArrayList<>();
        synchronized (mLock) {
            for (Map.Entry<Integer, HashSet<Integer>> appEntry :
                    mAppLoadedSdkUids.entrySet()) {
                final int appUid = appEntry.getKey();
                final HashSet<Integer> loadedCodeUids = appEntry.getValue();

                if (loadedCodeUids.contains(sdkUid)) {
                    appUids.add(appUid);
                }
            }
        }
        for (Integer appUid : appUids) {
            Log.i(TAG, "Killing app " + appUid + " containing code " + sdkUid);
            mActivityManager.killUid(appUid, "Package updating");
        }
    }

    /**
     * Returns list of sdks {@code packageName} uses
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    List<SharedLibraryInfo> getSdksUsed(String packageName) {
        List<SharedLibraryInfo> result = new ArrayList<>();
        PackageManager pm = mContext.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(
                    packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            List<SharedLibraryInfo> sharedLibraries = info.getSharedLibraryInfos();
            for (int i = 0; i < sharedLibraries.size(); i++) {
                final SharedLibraryInfo sharedLib = sharedLibraries.get(i);
                if (sharedLib.getType() != SharedLibraryInfo.TYPE_SDK_PACKAGE) {
                    continue;
                }
                result.add(sharedLib);
            }
            return result;
        } catch (PackageManager.NameNotFoundException ignored) {
            return Collections.emptyList();
        }
    }

    // Returns a random string.
    private static String getRandomString() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private void reconcileSdkData(String packageName, int uid, boolean forInstrumentation) {
        final List<SharedLibraryInfo> sdksUsed = getSdksUsed(packageName);
        if (sdksUsed.isEmpty()) {
            if (forInstrumentation) {
                Log.w(TAG,
                        "Running instrumentation for the sdk-sandbox process belonging to client "
                                + "app "
                                + packageName + " (uid = " + uid
                                + "). However client app doesn't depend on any SDKs. Only "
                                + "creating \"shared\" sdk sandbox data sub directory");
            } else {
                return;
            }
        }
        final List<String> subDirNames = new ArrayList<>();
        subDirNames.add("shared");
        for (int i = 0; i < sdksUsed.size(); i++) {
            final SharedLibraryInfo sdk = sdksUsed.get(i);
            //TODO(b/223386213): We need to scan the sdk package directory so that we don't create
            //multiple subdirectories for the same sdk, due to passing different random suffix.
            subDirNames.add(sdk.getName() + "@" + getRandomString());
        }
        final UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        final int userId = userHandle.getIdentifier();
        final int appId = UserHandle.getAppId(uid);
        final int flags = mContext.getSystemService(UserManager.class).isUserUnlocked(userHandle)
                ? PackageManagerLocal.FLAG_STORAGE_CE | PackageManagerLocal.FLAG_STORAGE_DE
                : PackageManagerLocal.FLAG_STORAGE_DE;

        try {
            //TODO(b/224719352): Pass actual seinfo from here
            mPackageManagerLocal.reconcileSdkData(/*volumeUuid=*/null, packageName, subDirNames,
                    userId, appId, /*previousAppId=*/-1, /*seInfo=*/"default", flags);
        } catch (Exception e) {
            // We will retry when sdk gets loaded
            Log.w(TAG, "Failed to reconcileSdkData for " + packageName + " subDirNames: "
                    + String.join(", ", subDirNames) + " error: " + e.getMessage());
        }
    }

    @Override
    public void loadSdk(String callingPackage, String name, Bundle params,
            IRemoteSdkCallback callback) {
        final int callingUid = Binder.getCallingUid();
        synchronized (mLock) {
            if (mRunningInstrumentations.contains(callingUid)) {
                throw new SecurityException(
                        "Currently running instrumentation of this sdk sandbox process");
            }
        }
        enforceCallingPackage(callingPackage, callingUid);
        final long token = Binder.clearCallingIdentity();
        try {
            loadSdkWithClearIdentity(callingUid, callingPackage, name, params, callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void loadSdkWithClearIdentity(int callingUid, String callingPackage, String name,
            Bundle params, IRemoteSdkCallback callback) {
        // Step 1: create unique identity for the {callingUid, name} pair
        final IBinder sdkToken = mSdkTokenManager.createOrGetSdkToken(callingUid, name);

        // Ensure we are not already loading sdk for this sdkToken. That's determined by
        // checking if we already have an AppAndRemoteCodeLink for the sdkToken.
        final AppAndRemoteSdkLink link = new AppAndRemoteSdkLink(sdkToken, callback);
        synchronized (mLock) {
            if (mAppAndRemoteSdkLinks.putIfAbsent(sdkToken, link) != null) {
                link.sendLoadSdkErrorToApp(SdkSandboxManager.LOAD_SDK_SDK_ALREADY_LOADED,
                        name + " is being loaded or has been loaded already");
                return;
            }
        }
        // Step 2: fetch the installed code in device
        SdkProviderInfo sdkProviderInfo = createSdkProviderInfo(name, callingUid);

        String errorMsg = "";
        if (sdkProviderInfo == null) {
            errorMsg = name + " not found for loading";
        } else if (TextUtils.isEmpty(sdkProviderInfo.getSdkProviderClassName())) {
            errorMsg = name + " did not set " + PROPERTY_SDK_PROVIDER_CLASS_NAME;
        }

        if (!TextUtils.isEmpty(errorMsg)) {
            Log.w(TAG, errorMsg);
            link.sendLoadSdkErrorToApp(SdkSandboxManager.LOAD_SDK_SDK_NOT_FOUND, errorMsg);
            return;
        }

        // TODO(b/204991850): ensure requested code is included in the AndroidManifest.xml
        invokeSdkSandboxServiceToLoadSdk(callingUid, callingPackage, sdkToken, sdkProviderInfo,
                params, link);

        // Register a death recipient to clean up sdkToken and unbind its service after app dies.
        try {
            callback.asBinder().linkToDeath(() -> {
                onAppDeath(sdkToken, callingUid);
            }, 0);
        } catch (RemoteException re) {
            // App has already died, cleanup sdk token and link, and unbind its service
            onAppDeath(sdkToken, callingUid);
        }
    }

    private void enforceCallingPackage(String callingPackage, int callingUid) {
        int packageUid = -1;
        PackageManager pm = mContext.createContextAsUser(
                UserHandle.getUserHandleForUid(callingUid), 0).getPackageManager();
        try {
            packageUid = pm.getPackageUid(callingPackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(callingPackage + " not found");
        }
        if (packageUid != callingUid) {
            throw new SecurityException(callingPackage + " does not belong to uid " + callingUid);
        }
    }

    private void onAppDeath(IBinder sdkToken, int appUid) {
        cleanUp(sdkToken);
        final int sdkSandboxUid = Process.toSdkSandboxUid(appUid);
        mServiceProvider.unbindService(appUid);
        synchronized (mLock) {
            mAppLoadedSdkUids.remove(appUid);
        }
        Log.i(TAG, "Killing sdk sandbox process " + sdkSandboxUid);
        mActivityManager.killUid(sdkSandboxUid, "App " + appUid + " has died");
    }

    @Override
    public void requestSurfacePackage(IBinder sdkToken, IBinder hostToken,
            int displayId, Bundle params) {
        //TODO(b/204991850): verify that sdkToken belongs to the callingUid
        final long token = Binder.clearCallingIdentity();
        try {
            requestSurfacePackageWithClearIdentity(sdkToken,
                    hostToken, displayId, params);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void requestSurfacePackageWithClearIdentity(IBinder sdkToken,
            IBinder hostToken, int displayId, Bundle params) {
        synchronized (mLock) {
            if (!mAppAndRemoteSdkLinks.containsKey(sdkToken)) {
                throw new SecurityException("sdkToken is invalid");
            }
            final AppAndRemoteSdkLink link = mAppAndRemoteSdkLinks.get(sdkToken);
            link.requestSurfacePackageToCode(hostToken, displayId, params);
        }
    }

    @Override
    public void sendData(int id, Bundle params) {
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mContext.enforceCallingPermission(android.Manifest.permission.DUMP,
                "Can't dump " + TAG);

        // TODO(b/211575098): Use IndentingPrintWriter for better formatting
        synchronized (mLock) {
            writer.println("mAppAndRemoteSdkLinks size: " + mAppAndRemoteSdkLinks.size());
        }

        writer.println("mSdkTokenManager:");
        mSdkTokenManager.dump(writer);
        writer.println();

        writer.println("mServiceProvider:");
        mServiceProvider.dump(writer);
        writer.println();
    }


    private void invokeSdkSandboxServiceToLoadSdk(
            int callingUid, String callingPackage, IBinder sdkToken, SdkProviderInfo sdkInfo,
            Bundle params, AppAndRemoteSdkLink link) {
        // check first if service already bound
        ISdkSandboxService service = mServiceProvider.getBoundServiceForApp(callingUid);
        if (service != null) {
            loadSdkForService(callingUid, sdkToken, sdkInfo, params, link, service);
            return;
        }

        mServiceProvider.bindService(
                callingUid,
                callingPackage,
                new ServiceConnection() {
                    private boolean mIsServiceBound = false;

                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        final ISdkSandboxService mService =
                                ISdkSandboxService.Stub.asInterface(service);
                        Log.i(TAG, "Sdk sandbox has been bound");
                        mServiceProvider.setBoundServiceForApp(callingUid, mService);

                        // Ensuring the code is not loaded again if connection restarted
                        if (!mIsServiceBound) {
                            loadSdkForService(callingUid, sdkToken, sdkInfo, params, link,
                                    mService);
                            mIsServiceBound = true;
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        // Sdk sandbox crashed or killed, system will start it again.
                        // TODO(b/204991850): Handle restarts differently
                        //  (e.g. Exponential backoff retry strategy)
                        mServiceProvider.setBoundServiceForApp(callingUid, null);
                    }

                    @Override
                    public void onBindingDied(ComponentName name) {
                        mServiceProvider.setBoundServiceForApp(callingUid, null);
                        mServiceProvider.unbindService(callingUid);
                        mServiceProvider.bindService(callingUid, callingPackage, this);
                    }

                    @Override
                    public void onNullBinding(ComponentName name) {
                        link.sendLoadSdkErrorToApp(
                                SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                                "Failed to bind the service");
                    }
                }
        );
    }

    private void loadSdkForService(
            int callingUid, IBinder sdkToken, SdkProviderInfo sdkProviderInfo, Bundle params,
            AppAndRemoteSdkLink link, ISdkSandboxService service) {
        try {
            service.loadSdk(sdkToken, sdkProviderInfo.getApplicationInfo(),
                    sdkProviderInfo.getSdkProviderClassName(), params, link);

            onSdkLoaded(callingUid, sdkProviderInfo.getApplicationInfo().uid);
        } catch (RemoteException e) {
            String errorMsg = "Failed to load code";
            Log.w(TAG, errorMsg, e);
            link.sendLoadSdkErrorToApp(
                    SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR, errorMsg);
        }
    }

    private void onSdkLoaded(int appUid, int sdkUid) {
        synchronized (mLock) {
            final HashSet<Integer> sdkUids = mAppLoadedSdkUids.get(appUid);
            if (sdkUids != null) {
                sdkUids.add(sdkUid);
            } else {
                mAppLoadedSdkUids.put(appUid, new HashSet<>(Collections.singletonList(sdkUid)));
            }
        }
    }

    /**
     * Clean up all internal data structures related to {@code sdkToken}
     */
    private void cleanUp(IBinder sdkToken) {
        // Destroy the sdkToken first, to free up the {callingUid, name} pair
        mSdkTokenManager.destroy(sdkToken);
        // Now clean up rest of the state which is using an obsolete sdkToken
        synchronized (mLock) {
            mAppAndRemoteSdkLinks.remove(sdkToken);
        }
    }

    private void enforceAllowedToStartOrBindService(Intent intent) {
        ComponentName component = intent.getComponent();
        String errorMsg = "SDK sandbox uid may not bind to or start a service: ";
        if (component == null) {
            throw new SecurityException(errorMsg + "intent component must be non-null.");
        }
        String componentPackageName = component.getPackageName();
        if (componentPackageName != null) {
            // TODO(b/225327125): Allowlist AdServices.
            if (!componentPackageName.equals(
                    WebViewUpdateService.getCurrentWebViewPackageName())) {
                throw new SecurityException(errorMsg + "component package name "
                        + componentPackageName + " is not allowlisted.");
            }
        } else {
            throw new SecurityException(errorMsg
                    + "the intent's component package name must be non-null.");
        }
    }

    private SdkProviderInfo createSdkProviderInfo(String sharedLibraryName, int callingUid) {
        try {
            PackageManager pm = mContext.getPackageManager();
            String[] packageNames = pm.getPackagesForUid(callingUid);
            for (int i = 0; i < packageNames.length; i++) {
                ApplicationInfo info = pm.getApplicationInfo(
                        packageNames[i], PackageManager.GET_SHARED_LIBRARY_FILES);
                List<SharedLibraryInfo> sharedLibraries = info.getSharedLibraryInfos();
                for (int j = 0; j < sharedLibraries.size(); j++) {
                    SharedLibraryInfo sharedLibrary = sharedLibraries.get(j);
                    if (sharedLibrary.getType() != SharedLibraryInfo.TYPE_SDK_PACKAGE) {
                        continue;
                    }

                    if (!sharedLibraryName.equals(sharedLibrary.getName())) {
                        continue;
                    }

                    String sdkProviderClassName = pm.getProperty(PROPERTY_SDK_PROVIDER_CLASS_NAME,
                            sharedLibrary.getDeclaringPackage().getPackageName()).getString();

                    ApplicationInfo applicationInfo = pm.getPackageInfo(
                            sharedLibrary.getDeclaringPackage(),
                            PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES).applicationInfo;
                    return new SdkProviderInfo(applicationInfo, sdkProviderClassName);
                }
            }
            return null;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    @ThreadSafe
    private static class SdkTokenManager {
        // Keep track of codeToken for each unique pair of {callingUid, name}
        @GuardedBy("mSdkTokens")
        final ArrayMap<Pair<Integer, String>, IBinder> mSdkTokens = new ArrayMap<>();
        @GuardedBy("mSdkTokens")
        final ArrayMap<IBinder, Pair<Integer, String>> mReverseSdkTokens = new ArrayMap<>();

        /**
         * For the given {callingUid, name} pair, create unique {@code sdkToken} or
         * return existing one.
         */
        public IBinder createOrGetSdkToken(int callingUid, String name) {
            final Pair<Integer, String> pair = Pair.create(callingUid, name);
            synchronized (mSdkTokens) {
                if (!mSdkTokens.containsKey(pair)) {
                    final IBinder sdkToken = new Binder();
                    mSdkTokens.put(pair, sdkToken);
                    mReverseSdkTokens.put(sdkToken, pair);
                }
                return mSdkTokens.get(pair);
            }
        }

        public void destroy(IBinder sdkToken) {
            synchronized (mSdkTokens) {
                mSdkTokens.remove(mReverseSdkTokens.get(sdkToken));
                mReverseSdkTokens.remove(sdkToken);
            }
        }

        void dump(PrintWriter writer) {
            synchronized (mSdkTokens) {
                if (mSdkTokens.isEmpty()) {
                    writer.println("mSdkTokens is empty");
                } else {
                    writer.print("mSdkTokens size: ");
                    writer.println(mSdkTokens.size());
                    for (Pair<Integer, String> pair : mSdkTokens.keySet()) {
                        writer.printf("callingUid: %s, name: %s", pair.first, pair.second);
                        writer.println();
                    }
                }
            }
        }
    }

    /**
     * A callback object to establish a link between the app calling into manager service
     * and the remote code being loaded in SdkSandbox.
     *
     * Overview of communication:
     * 1. App to ManagerService: App calls into this service via app context
     * 2. ManagerService to App: {@link AppAndRemoteSdkLink} holds reference to
     * {@link IRemoteSdkCallback} object which provides call back into the app.
     * 3. RemoteCode to ManagerService: {@link AppAndRemoteSdkLink} extends
     * {@link ISdkSandboxToSdkSandboxManagerCallback} interface. We
     * pass on this object to {@link ISdkSandboxService} so that remote code
     * can call back into ManagerService
     * 4. ManagerService to RemoteCode: When code is loaded for the first time and remote
     * code calls back with successful result, it also sends reference to
     * {@link ISdkSandboxManagerToSdkSandboxCallback} callback object.
     * ManagerService uses this to callback into the remote code.
     *
     * We maintain a link for each unique {app, remoteCode} pair, which is identified with
     * {@code codeToken}.
     */
    private class AppAndRemoteSdkLink extends
            ISdkSandboxToSdkSandboxManagerCallback.Stub {
        // The codeToken for which this channel has been created
        private final IBinder mSdkToken;
        private final IRemoteSdkCallback mManagerToAppCallback;

        @GuardedBy("this")
        private ISdkSandboxManagerToSdkSandboxCallback mManagerToCodeCallback;

        AppAndRemoteSdkLink(IBinder sdkToken, IRemoteSdkCallback managerToAppCallback) {
            mSdkToken = sdkToken;
            mManagerToAppCallback = managerToAppCallback;
        }

        @Override
        public void onLoadSdkSuccess(
                Bundle params, ISdkSandboxManagerToSdkSandboxCallback callback) {
            // Keep reference to callback so that manager service can
            // callback to remote code loaded.
            synchronized (this) {
                mManagerToCodeCallback = callback;
            }
            sendLoadSdkSuccessToApp(params);
        }

        @Override
        public void onLoadSdkError(int errorCode, String errorMsg) {
            sendLoadSdkErrorToApp(errorCode, errorMsg);
        }

        @Override
        public void onSurfacePackageReady(SurfaceControlViewHost.SurfacePackage surfacePackage,
                int surfacePackageId, Bundle params) {
            sendSurfacePackageReadyToApp(surfacePackage, surfacePackageId, params);
        }

        @Override
        public void onSurfacePackageError(int errorCode, String errorMsg) {
            sendSurfacePackageErrorToApp(errorCode, errorMsg);
        }

        private void sendLoadSdkSuccessToApp(Bundle params) {
            try {
                mManagerToAppCallback.onLoadSdkSuccess(mSdkToken, params);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onLoadCodeSuccess", e);
            }
        }

        void sendLoadSdkErrorToApp(int errorCode, String errorMsg) {
            // Since loadSdk failed, manager should no longer concern itself with communication
            // between the app and a non-existing remote code.
            cleanUp(mSdkToken);

            try {
                mManagerToAppCallback.onLoadSdkFailure(errorCode, errorMsg);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onLoadCodeFailure", e);
            }
        }

        void sendSurfacePackageErrorToApp(int errorCode, String errorMsg) {
            try {
                mManagerToAppCallback.onSurfacePackageError(errorCode, errorMsg);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSurfacePackageError", e);
            }
        }

        private void sendSurfacePackageReadyToApp(
                SurfaceControlViewHost.SurfacePackage surfacePackage,
                int surfacePackageId, Bundle params) {
            try {
                mManagerToAppCallback.onSurfacePackageReady(surfacePackage,
                        surfacePackageId, params);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSurfacePackageReady callback", e);
            }
        }

        void requestSurfacePackageToCode(IBinder hostToken, int displayId, Bundle params) {
            try {
                synchronized (this) {
                    mManagerToCodeCallback.onSurfacePackageRequested(hostToken, displayId, params);
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to requestSurfacePackage", e);
                // TODO(b/204991850): send request surface package error back to app
            }
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxManagerLocal getLocalManager() {
        return mLocalManager;
    }

    private void notifyInstrumentationStarted(
            @NonNull String clientAppPackageName, int clientAppUid) {
        Log.d(TAG, "notifyInstrumentationStarted: clientApp = " + clientAppPackageName
                + " clientAppUid = " + clientAppUid);
        synchronized (mLock) {
            mServiceProvider.unbindService(clientAppUid);
            int sdkSandboxUid = Process.toSdkSandboxUid(clientAppUid);
            mActivityManager.killUid(sdkSandboxUid, "instrumentation started");
            mRunningInstrumentations.add(clientAppUid);
        }
        // TODO(b/223386213): we need to check if there is reconcileSdkData task already enqueued
        //  because the instrumented client app was just installed.
        reconcileSdkData(clientAppPackageName, clientAppUid, /* forInstrumentation= */ true);
    }

    private void notifyInstrumentationFinished(
            @NonNull String clientAppPackageName, int clientAppUid) {
        Log.d(TAG, "notifyInstrumentationFinished: clientApp = " + clientAppPackageName
                + " clientAppUid = " + clientAppUid);
        synchronized (mLock) {
            mRunningInstrumentations.remove(clientAppUid);
        }
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            SdkSandboxServiceProvider provider =
                    new SdkSandboxServiceProviderImpl(getContext());
            SdkSandboxManagerService service =
                    new SdkSandboxManagerService(getContext(), provider);
            publishBinderService(SDK_SANDBOX_SERVICE, service);
            LocalManagerRegistry.addManager(
                    SdkSandboxManagerLocal.class, service.getLocalManager());
        }
    }

    /**
     * Class which retrieves and stores the sdkProviderClassName and ApplicationInfo
     */
    private class SdkProviderInfo {

        private ApplicationInfo mApplicationInfo;
        private String mSdkProviderClassName;

        private SdkProviderInfo(ApplicationInfo applicationInfo, String sdkProviderClassName) {
            mApplicationInfo = applicationInfo;
            mSdkProviderClassName = sdkProviderClassName;
        }

        public String getSdkProviderClassName() {
            return mSdkProviderClassName;
        }

        public ApplicationInfo getApplicationInfo() {
            return mApplicationInfo;
        }
    }

    private class LocalImpl implements SdkSandboxManagerLocal {

        @NonNull
        @Override
        public String getSdkSandboxProcessNameForInstrumentation(
                @NonNull ApplicationInfo clientAppInfo) {
            return clientAppInfo.processName + "_sdk_sandbox_instr";
        }

        @Override
        public void notifyInstrumentationStarted(
                @NonNull String clientAppPackageName, int clientAppUid) {
            SdkSandboxManagerService.this.notifyInstrumentationStarted(
                    clientAppPackageName, clientAppUid);
        }

        @Override
        public void notifyInstrumentationFinished(
                @NonNull String clientAppPackageName, int clientAppUid) {
            SdkSandboxManagerService.this.notifyInstrumentationFinished(
                    clientAppPackageName, clientAppUid);
        }

        @Override
        public void enforceAllowedToSendBroadcast(@NonNull Intent intent) {
            // TODO(b/209599396): Have a meaningful allowlist.
            if (intent.getAction() != null && !Intent.ACTION_VIEW.equals(intent.getAction())) {
                throw new SecurityException("Intent " + intent.getAction()
                        + " may not be broadcast from an SDK sandbox uid");
            }
        }

        @Override
        public void enforceAllowedToStartActivity(@NonNull Intent intent) {
            enforceAllowedToSendBroadcast(intent);
        }

        @Override
        public void enforceAllowedToStartOrBindService(@NonNull Intent intent) {
            SdkSandboxManagerService.this.enforceAllowedToStartOrBindService(intent);
        }
    }
}
