/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.server.appsearch;

import static android.app.appsearch.AppSearchResult.throwableToFailedResult;
import static android.os.Process.INVALID_UID;

import static com.android.server.appsearch.util.ServiceImplHelper.invokeCallbackOnError;
import static com.android.server.appsearch.util.ServiceImplHelper.invokeCallbackOnResult;

import android.annotation.ElapsedRealtimeLong;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchMigrationHelper;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.SearchResultPage;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaResponse;
import android.app.appsearch.StorageInfo;
import android.app.appsearch.VisibilityDocument;
import android.app.appsearch.aidl.AppSearchResultParcel;
import android.app.appsearch.aidl.IAppSearchBatchResultCallback;
import android.app.appsearch.aidl.IAppSearchManager;
import android.app.appsearch.aidl.IAppSearchObserverProxy;
import android.app.appsearch.aidl.IAppSearchResultCallback;
import android.app.appsearch.aidl.DocumentsParcel;
import android.app.appsearch.exceptions.AppSearchException;
import android.app.appsearch.observer.ObserverSpec;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;

import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.appsearch.external.localstorage.stats.CallStats;
import com.android.server.appsearch.external.localstorage.stats.OptimizeStats;
import com.android.server.appsearch.external.localstorage.visibilitystore.VisibilityStore;
import com.android.server.appsearch.observer.AppSearchObserverProxy;
import com.android.server.appsearch.stats.StatsCollector;
import com.android.server.appsearch.util.ExecutorManager;
import com.android.server.appsearch.util.ServiceImplHelper;
import com.android.server.appsearch.visibilitystore.FrameworkCallerAccess;
import com.android.server.usage.StorageStatsManagerLocal;
import com.android.server.usage.StorageStatsManagerLocal.StorageStatsAugmenter;

import com.google.android.icing.proto.PersistType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The main service implementation which contains AppSearch's platform functionality.
 *
 * @hide
 */
public class AppSearchManagerService extends SystemService {
    private static final String TAG = "AppSearchManagerService";

    /**
     * An executor for system activity not tied to any particular user.
     *
     * <p>NOTE: Never call shutdownNow(). AppSearchManagerService persists forever even as
     * individual users are added and removed -- without this pool the service will be broken. And,
     * clients waiting for callbacks will never receive anything and will hang.
     */
    private static final Executor SHARED_EXECUTOR = ExecutorManager.createDefaultExecutorService();

    private final Context mContext;
    private final ExecutorManager mExecutorManager = new ExecutorManager();

    private PackageManager mPackageManager;
    private ServiceImplHelper mServiceImplHelper;
    private AppSearchUserInstanceManager mAppSearchUserInstanceManager;

    public AppSearchManagerService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        publishBinderService(Context.APP_SEARCH_SERVICE, new Stub());
        mPackageManager = getContext().getPackageManager();
        mServiceImplHelper = new ServiceImplHelper(mContext, mExecutorManager);
        mAppSearchUserInstanceManager = AppSearchUserInstanceManager.getInstance();
        registerReceivers();
        LocalManagerRegistry.getManager(StorageStatsManagerLocal.class)
                .registerStorageStatsAugmenter(new AppSearchStorageStatsAugmenter(), TAG);
    }

    @Override
    public void onBootPhase(/* @BootPhase */ int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            StatsCollector.getInstance(mContext, SHARED_EXECUTOR);
        }
    }

    private void registerReceivers() {
        mContext.registerReceiverForAllUsers(
                new UserActionReceiver(),
                new IntentFilter(Intent.ACTION_USER_REMOVED),
                /*broadcastPermission=*/ null,
                /*scheduler=*/ null);

        //TODO(b/145759910) Add a direct callback when user clears the data instead of relying on
        // broadcasts
        IntentFilter packageChangedFilter = new IntentFilter();
        packageChangedFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        packageChangedFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        packageChangedFilter.addDataScheme("package");
        packageChangedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiverForAllUsers(
                new PackageChangedReceiver(),
                packageChangedFilter,
                /*broadcastPermission=*/ null,
                /*scheduler=*/ null);
    }

    private class UserActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            Objects.requireNonNull(context);
            Objects.requireNonNull(intent);
            if (Intent.ACTION_USER_REMOVED.equals(intent.getAction())) {
                UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
                if (userHandle == null) {
                    Log.e(TAG,
                            "Extra " + Intent.EXTRA_USER + " is missing in the intent: " + intent);
                    return;
                }
                // We can handle user removal the same way as user stopping: shut down the executor
                // and close icing. The data of AppSearch is saved in the "credential encrypted"
                // system directory of each user. That directory will be auto-deleted when a user is
                // removed, so we don't need it handle it specially.
                onUserStopping(userHandle);
            } else {
                Log.e(TAG, "Received unknown intent: " + intent);
            }
        }
    }

    private class PackageChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            Objects.requireNonNull(context);
            Objects.requireNonNull(intent);

            switch (intent.getAction()) {
                case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                case Intent.ACTION_PACKAGE_DATA_CLEARED:
                    String packageName = intent.getData().getSchemeSpecificPart();
                    if (packageName == null) {
                        Log.e(TAG, "Package name is missing in the intent: " + intent);
                        return;
                    }
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, INVALID_UID);
                    if (uid == INVALID_UID) {
                        Log.e(TAG, "uid is missing in the intent: " + intent);
                        return;
                    }
                    handlePackageRemoved(packageName, uid);
                    break;
                default:
                    Log.e(TAG, "Received unknown intent: " + intent);
            }
        }
    }

    private void handlePackageRemoved(@NonNull String packageName, int uid) {
        UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        try {
            if (mServiceImplHelper.isUserLocked(userHandle)) {
                // We cannot access a locked user's directory and remove package data from it.
                // We should remove those uninstalled package data when the user is unlocking.
                return;
            }
            // Only clear the package's data if AppSearch exists for this user.
            if (AppSearchModule.getAppSearchDir(userHandle).exists()) {
                Context userContext = mContext.createContextAsUser(userHandle, /*flags=*/ 0);
                AppSearchUserInstance instance =
                        mAppSearchUserInstanceManager.getOrCreateUserInstance(
                                userContext,
                                userHandle,
                                FrameworkAppSearchConfig.getInstance(SHARED_EXECUTOR));
                instance.getAppSearchImpl().clearPackageData(packageName);
                dispatchChangeNotifications(instance);
                instance.getLogger().removeCachedUidForPackage(packageName);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to remove data for package: " + packageName, t);
        }
    }

    @Override
    public void onUserUnlocking(@NonNull TargetUser user) {
        Objects.requireNonNull(user);
        UserHandle userHandle = user.getUserHandle();
        mServiceImplHelper.setUserIsLocked(userHandle, false);
        mExecutorManager.getOrCreateUserExecutor(userHandle).execute(() -> {
            try {
                // Only clear the package's data if AppSearch exists for this user.
                if (AppSearchModule.getAppSearchDir(userHandle).exists()) {
                    Context userContext = mContext.createContextAsUser(userHandle, /*flags=*/ 0);
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getOrCreateUserInstance(
                                    userContext,
                                    userHandle,
                                    FrameworkAppSearchConfig.getInstance(SHARED_EXECUTOR));
                    List<PackageInfo> installedPackageInfos = userContext
                            .getPackageManager()
                            .getInstalledPackages(/*flags=*/0);
                    Set<String> packagesToKeep = new ArraySet<>(installedPackageInfos.size());
                    for (int i = 0; i < installedPackageInfos.size(); i++) {
                        packagesToKeep.add(installedPackageInfos.get(i).packageName);
                    }
                    packagesToKeep.add(VisibilityStore.VISIBILITY_PACKAGE_NAME);
                    instance.getAppSearchImpl().prunePackageData(packagesToKeep);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unable to prune packages for " + user, t);
            }
        });
    }

    @Override
    public void onUserStopping(@NonNull TargetUser user) {
        Objects.requireNonNull(user);
        onUserStopping(user.getUserHandle());
    }

    private void onUserStopping(@NonNull UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        Log.i(TAG, "Shutting down AppSearch for user " + userHandle);
        try {
            mServiceImplHelper.setUserIsLocked(userHandle, true);
            mExecutorManager.shutDownAndRemoveUserExecutor(userHandle);
            mAppSearchUserInstanceManager.closeAndRemoveUserInstance(userHandle);
            Log.i(TAG, "Removed AppSearchImpl instance for: " + userHandle);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to remove data for: " + userHandle, t);
        }
    }

    private class Stub extends IAppSearchManager.Stub {
        @Override
        public void setSchema(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull List<Bundle> schemaBundles,
                @NonNull List<Bundle> visibilityBundles,
                boolean forceOverride,
                int schemaVersion,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(schemaBundles);
            Objects.requireNonNull(visibilityBundles);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                try {
                    List<AppSearchSchema> schemas = new ArrayList<>(schemaBundles.size());
                    for (int i = 0; i < schemaBundles.size(); i++) {
                        schemas.add(new AppSearchSchema(schemaBundles.get(i)));
                    }
                    List<VisibilityDocument> visibilityDocuments =
                            new ArrayList<>(visibilityBundles.size());
                    for (int i = 0; i < visibilityBundles.size(); i++) {
                        visibilityDocuments.add(
                                new VisibilityDocument(visibilityBundles.get(i)));
                    }
                    instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    // TODO(b/173532925): Implement logging for statsBuilder
                    SetSchemaResponse setSchemaResponse = instance.getAppSearchImpl().setSchema(
                            callerAttributionSource.getPackageName(),
                            databaseName,
                            schemas,
                            visibilityDocuments,
                            forceOverride,
                            schemaVersion,
                            /*setSchemaStatsBuilder=*/ null);
                    ++operationSuccessCount;
                    invokeCallbackOnResult(callback,
                            AppSearchResult.newSuccessfulResult(setSchemaResponse.getBundle()));

                    // Schedule a task to dispatch change notifications. See requirements for where
                    // the method is called documented in the method description.
                    dispatchChangeNotifications(instance);

                    // setSchema will sync the schemas in the request to AppSearch, any existing
                    // schemas which  is not included in the request will be delete if we force
                    // override incompatible schemas. And all documents of these types will be
                    // deleted as well. We should checkForOptimize for these deletion.
                    checkForOptimize(targetUser, instance);
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setPackageName(callerAttributionSource.getPackageName())
                                .setDatabase(databaseName)
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(CallStats.CALL_TYPE_SET_SCHEMA)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }

        @Override
        public void getSchema(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String targetPackageName,
                @NonNull String databaseName,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(targetPackageName);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);

                    boolean callerHasSystemAccess = instance.getVisibilityChecker()
                            .doesCallerHaveSystemAccess(callerAttributionSource.getPackageName());
                    GetSchemaResponse response =
                            instance.getAppSearchImpl().getSchema(
                                    targetPackageName,
                                    databaseName,
                                    new FrameworkCallerAccess(callerAttributionSource,
                                            callerHasSystemAccess));
                    invokeCallbackOnResult(
                            callback,
                            AppSearchResult.newSuccessfulResult(response.getBundle()));
                } catch (Throwable t) {
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                }
            });
        }

        @Override
        public void getNamespaces(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    List<String> namespaces =
                            instance.getAppSearchImpl().getNamespaces(
                                    callerAttributionSource.getPackageName(), databaseName);
                    invokeCallbackOnResult(
                            callback, AppSearchResult.newSuccessfulResult(namespaces));
                } catch (Throwable t) {
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                }
            });
        }

        @Override
        public void putDocuments(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull DocumentsParcel documentsParcel,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchBatchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(documentsParcel);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                try {
                    AppSearchBatchResult.Builder<String, Void> resultBuilder =
                            new AppSearchBatchResult.Builder<>();
                    instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    List<GenericDocument> documents = documentsParcel.getDocuments();
                    for (int i = 0; i < documents.size(); i++) {
                        GenericDocument document = documents.get(i);
                        try {
                            instance.getAppSearchImpl().putDocument(
                                    callerAttributionSource.getPackageName(),
                                    databaseName,
                                    document,
                                    /*sendChangeNotifications=*/ true,
                                    instance.getLogger());
                            resultBuilder.setSuccess(document.getId(), /*value=*/ null);
                            ++operationSuccessCount;
                        } catch (Throwable t) {
                            resultBuilder.setResult(document.getId(), throwableToFailedResult(t));
                            AppSearchResult<Void> result = throwableToFailedResult(t);
                            resultBuilder.setResult(document.getId(), result);
                            // Since we can only include one status code in the atom,
                            // for failures, we would just save the one for the last failure
                            statusCode = result.getResultCode();
                            ++operationFailureCount;
                        }
                    }
                    // Now that the batch has been written. Persist the newly written data.
                    instance.getAppSearchImpl().persistToDisk(PersistType.Code.LITE);
                    invokeCallbackOnResult(callback, resultBuilder.build());

                    // Schedule a task to dispatch change notifications. See requirements for where
                    // the method is called documented in the method description.
                    dispatchChangeNotifications(instance);

                    // The existing documents with same ID will be deleted, so there may be some
                    // resources that could be released after optimize().
                    checkForOptimize(
                            targetUser, instance, /*mutateBatchSize=*/ documents.size());
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnError(callback, t);
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setPackageName(callerAttributionSource.getPackageName())
                                .setDatabase(databaseName)
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(CallStats.CALL_TYPE_PUT_DOCUMENTS)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }

        @Override
        public void getDocuments(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String targetPackageName,
                @NonNull String databaseName,
                @NonNull String namespace,
                @NonNull List<String> ids,
                @NonNull Map<String, List<String>> typePropertyPaths,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchBatchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(targetPackageName);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(ids);
            Objects.requireNonNull(typePropertyPaths);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                boolean global =
                        !callerAttributionSource.getPackageName().equals(targetPackageName);
                try {
                    AppSearchBatchResult.Builder<String, Bundle> resultBuilder =
                            new AppSearchBatchResult.Builder<>();
                    instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    for (int i = 0; i < ids.size(); i++) {
                        String id = ids.get(i);
                        try {
                            GenericDocument document;
                            if (global) {
                                boolean callerHasSystemAccess = instance.getVisibilityChecker()
                                        .doesCallerHaveSystemAccess(callerAttributionSource
                                                .getPackageName());
                                document = instance.getAppSearchImpl().globalGetDocument(
                                        targetPackageName,
                                        databaseName,
                                        namespace,
                                        id,
                                        typePropertyPaths,
                                        new FrameworkCallerAccess(callerAttributionSource,
                                                callerHasSystemAccess));
                            } else {
                                document = instance.getAppSearchImpl().getDocument(
                                        targetPackageName,
                                        databaseName,
                                        namespace,
                                        id,
                                        typePropertyPaths);
                            }
                            ++operationSuccessCount;
                            resultBuilder.setSuccess(id, document.getBundle());
                        } catch (Throwable t) {
                            // Since we can only include one status code in the atom,
                            // for failures, we would just save the one for the last failure
                            AppSearchResult<Bundle> result = throwableToFailedResult(t);
                            resultBuilder.setResult(id, result);
                            statusCode = result.getResultCode();
                            ++operationFailureCount;
                        }
                    }
                    invokeCallbackOnResult(callback, resultBuilder.build());
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnError(callback, t);
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        int callType = global?
                                CallStats.CALL_TYPE_GLOBAL_GET_DOCUMENT_BY_ID:
                                CallStats.CALL_TYPE_GET_DOCUMENTS;
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setPackageName(targetPackageName)
                                .setDatabase(databaseName)
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(callType)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }

        @Override
        public void query(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull String queryExpression,
                @NonNull Bundle searchSpecBundle,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(queryExpression);
            Objects.requireNonNull(searchSpecBundle);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                try {
                    instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    SearchResultPage searchResultPage = instance.getAppSearchImpl().query(
                            callerAttributionSource.getPackageName(),
                            databaseName,
                            queryExpression,
                            new SearchSpec(searchSpecBundle),
                            instance.getLogger());
                    ++operationSuccessCount;
                    invokeCallbackOnResult(
                            callback,
                            AppSearchResult.newSuccessfulResult(searchResultPage.getBundle()));
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setPackageName(callerAttributionSource.getPackageName())
                                .setDatabase(databaseName)
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(CallStats.CALL_TYPE_SEARCH)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }

        @Override
        public void globalQuery(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String queryExpression,
                @NonNull Bundle searchSpecBundle,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(queryExpression);
            Objects.requireNonNull(searchSpecBundle);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                try {
                    instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);

                    boolean callerHasSystemAccess = instance.getVisibilityChecker()
                            .doesCallerHaveSystemAccess(callerAttributionSource.getPackageName());

                    SearchResultPage searchResultPage = instance.getAppSearchImpl().globalQuery(
                            queryExpression,
                            new SearchSpec(searchSpecBundle),
                            new FrameworkCallerAccess(callerAttributionSource,
                                    callerHasSystemAccess),
                            instance.getLogger());
                    ++operationSuccessCount;
                    invokeCallbackOnResult(
                            callback,
                            AppSearchResult.newSuccessfulResult(searchResultPage.getBundle()));
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setPackageName(callerAttributionSource.getPackageName())
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(CallStats.CALL_TYPE_GLOBAL_SEARCH)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }

        @Override
        public void getNextPage(
                @NonNull AttributionSource callerAttributionSource,
                long nextPageToken,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    // TODO(b/173532925): Implement logging for statsBuilder
                    SearchResultPage searchResultPage =
                            instance.getAppSearchImpl().getNextPage(
                                    callerAttributionSource.getPackageName(), nextPageToken,
                                    /*statsBuilder=*/ null);
                    invokeCallbackOnResult(
                            callback,
                            AppSearchResult.newSuccessfulResult(searchResultPage.getBundle()));
                } catch (Throwable t) {
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                }
            });
        }

        @Override
        public void invalidateNextPageToken(
                @NonNull AttributionSource callerAttributionSource,
                long nextPageToken,
                @NonNull UserHandle userHandle) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(userHandle);

            try {
                UserHandle targetUser = mServiceImplHelper.verifyIncomingCall(
                        callerAttributionSource, userHandle);
                mExecutorManager.getOrCreateUserExecutor(targetUser).execute(() -> {
                    try {
                        AppSearchUserInstance instance =
                                mAppSearchUserInstanceManager.getUserInstance(targetUser);
                        instance.getAppSearchImpl().invalidateNextPageToken(
                                callerAttributionSource.getPackageName(), nextPageToken);
                    } catch (Throwable t) {
                        Log.e(TAG, "Unable to invalidate the query page token", t);
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "Unable to invalidate the query page token", t);
            }
        }

        @Override
        public void writeQueryResultsToFile(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull ParcelFileDescriptor fileDescriptor,
                @NonNull String queryExpression,
                @NonNull Bundle searchSpecBundle,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(fileDescriptor);
            Objects.requireNonNull(queryExpression);
            Objects.requireNonNull(searchSpecBundle);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    // we don't need to append the file. The file is always brand new.
                    try (DataOutputStream outputStream = new DataOutputStream(
                            new FileOutputStream(fileDescriptor.getFileDescriptor()))) {
                        SearchResultPage searchResultPage = instance.getAppSearchImpl().query(
                                callerAttributionSource.getPackageName(),
                                databaseName,
                                queryExpression,
                                new SearchSpec(searchSpecBundle),
                                /*logger=*/ null);
                        while (!searchResultPage.getResults().isEmpty()) {
                            for (int i = 0; i < searchResultPage.getResults().size(); i++) {
                                AppSearchMigrationHelper.writeBundleToOutputStream(
                                        outputStream, searchResultPage.getResults().get(i)
                                                .getGenericDocument().getBundle());
                            }
                            // TODO(b/173532925): Implement logging for statsBuilder
                            searchResultPage = instance.getAppSearchImpl().getNextPage(
                                    callerAttributionSource.getPackageName(),
                                    searchResultPage.getNextPageToken(),
                                    /*statsBuilder=*/ null);
                        }
                    }
                    invokeCallbackOnResult(callback, AppSearchResult.newSuccessfulResult(null));
                } catch (Throwable t) {
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                }
            });
        }

        @Override
        public void putDocumentsFromFile(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull ParcelFileDescriptor fileDescriptor,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(fileDescriptor);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);

                    GenericDocument document;
                    ArrayList<Bundle> migrationFailureBundles = new ArrayList<>();
                    try (DataInputStream inputStream = new DataInputStream(
                            new FileInputStream(fileDescriptor.getFileDescriptor()))) {
                        while (true) {
                            try {
                                document = AppSearchMigrationHelper
                                        .readDocumentFromInputStream(inputStream);
                            } catch (EOFException e) {
                                // nothing wrong, we just finish the reading.
                                break;
                            }
                            try {
                                // Per this method's documentation, individual document change
                                // notifications are not dispatched.
                                instance.getAppSearchImpl().putDocument(
                                        callerAttributionSource.getPackageName(),
                                        databaseName,
                                        document,
                                        /*sendChangeNotifications=*/ false,
                                        /*logger=*/ null);
                            } catch (Throwable t) {
                                migrationFailureBundles.add(new SetSchemaResponse.MigrationFailure(
                                        document.getNamespace(),
                                        document.getId(),
                                        document.getSchemaType(),
                                        AppSearchResult.throwableToFailedResult(t))
                                        .getBundle());
                            }
                        }
                    }
                    instance.getAppSearchImpl().persistToDisk(PersistType.Code.FULL);
                    invokeCallbackOnResult(callback,
                            AppSearchResult.newSuccessfulResult(migrationFailureBundles));
                } catch (Throwable t) {
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                }
            });
        }

        @Override
        public void reportUsage(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String targetPackageName,
                @NonNull String databaseName,
                @NonNull String namespace,
                @NonNull String documentId,
                long usageTimeMillis,
                boolean systemUsage,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(targetPackageName);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(documentId);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                try {
                    String callingPackageName = callerAttributionSource.getPackageName();
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    if (systemUsage) {
                        if (!instance.getVisibilityChecker().doesCallerHaveSystemAccess(
                                callerAttributionSource.getPackageName())) {
                            throw new AppSearchException(AppSearchResult.RESULT_SECURITY_ERROR,
                                    callingPackageName
                                            + " does not have access to report system usage");
                        }
                    } else {
                        if (!callingPackageName.equals(targetPackageName)) {
                            throw new AppSearchException(AppSearchResult.RESULT_SECURITY_ERROR,
                                    "Cannot report usage to different package: "
                                            + targetPackageName + " from package: "
                                            + callingPackageName);
                        }
                    }

                    instance.getAppSearchImpl().reportUsage(targetPackageName, databaseName,
                            namespace, documentId, usageTimeMillis, systemUsage);
                    invokeCallbackOnResult(
                            callback, AppSearchResult.newSuccessfulResult(/*value=*/ null));
                } catch (Throwable t) {
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                }
            });
        }

        @Override
        public void removeByDocumentId(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull String namespace,
                @NonNull List<String> ids,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchBatchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(ids);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                try {
                    AppSearchBatchResult.Builder<String, Void> resultBuilder =
                            new AppSearchBatchResult.Builder<>();
                    instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    for (int i = 0; i < ids.size(); i++) {
                        String id = ids.get(i);
                        try {
                            instance.getAppSearchImpl().remove(
                                    callerAttributionSource.getPackageName(),
                                    databaseName,
                                    namespace,
                                    id,
                                    /*removeStatsBuilder=*/ null);
                            ++operationSuccessCount;
                            resultBuilder.setSuccess(id, /*result= */ null);
                        } catch (Throwable t) {
                            AppSearchResult<Void> result = throwableToFailedResult(t);
                            resultBuilder.setResult(id, result);
                            // Since we can only include one status code in the atom,
                            // for failures, we would just save the one for the last failure
                            statusCode = result.getResultCode();
                            ++operationFailureCount;
                        }
                    }
                    // Now that the batch has been written. Persist the newly written data.
                    instance.getAppSearchImpl().persistToDisk(PersistType.Code.LITE);
                    invokeCallbackOnResult(callback, resultBuilder.build());

                    // Schedule a task to dispatch change notifications. See requirements for where
                    // the method is called documented in the method description.
                    dispatchChangeNotifications(instance);

                    checkForOptimize(targetUser, instance, ids.size());
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnError(callback, t);
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setPackageName(callerAttributionSource.getPackageName())
                                .setDatabase(databaseName)
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_ID)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }

        @Override
        public void removeByQuery(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull String queryExpression,
                @NonNull Bundle searchSpecBundle,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchResultCallback callback) {
            // TODO(b/173532925) log CallStats once we have CALL_TYPE_REMOVE_BY_QUERY added
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(queryExpression);
            Objects.requireNonNull(searchSpecBundle);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                try {
                    instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    instance.getAppSearchImpl().removeByQuery(
                            callerAttributionSource.getPackageName(),
                            databaseName,
                            queryExpression,
                            new SearchSpec(searchSpecBundle),
                            /*removeStatsBuilder=*/ null);
                    // Now that the batch has been written. Persist the newly written data.
                    instance.getAppSearchImpl().persistToDisk(PersistType.Code.LITE);
                    ++operationSuccessCount;
                    invokeCallbackOnResult(callback, AppSearchResult.newSuccessfulResult(null));

                    // Schedule a task to dispatch change notifications. See requirements for where
                    // the method is called documented in the method description.
                    dispatchChangeNotifications(instance);

                    checkForOptimize(targetUser, instance);
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setPackageName(callerAttributionSource.getPackageName())
                                .setDatabase(databaseName)
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(CallStats.CALL_TYPE_REMOVE_DOCUMENTS_BY_SEARCH)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }

        @Override
        public void getStorageInfo(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String databaseName,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(databaseName);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    StorageInfo storageInfo = instance.getAppSearchImpl().getStorageInfoForDatabase(
                            callerAttributionSource.getPackageName(), databaseName);
                    Bundle storageInfoBundle = storageInfo.getBundle();
                    invokeCallbackOnResult(
                            callback, AppSearchResult.newSuccessfulResult(storageInfoBundle));
                } catch (Throwable t) {
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                }
            });
        }

        @Override
        public void persistToDisk(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(userHandle);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            try {
                UserHandle targetUser = mServiceImplHelper.verifyIncomingCall(
                        callerAttributionSource, userHandle);
                mExecutorManager.getOrCreateUserExecutor(targetUser).execute(() -> {
                    @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                    AppSearchUserInstance instance = null;
                    int operationSuccessCount = 0;
                    int operationFailureCount = 0;
                    try {
                        instance = mAppSearchUserInstanceManager.getUserInstance(targetUser);
                        instance.getAppSearchImpl().persistToDisk(PersistType.Code.FULL);
                        ++operationSuccessCount;
                    } catch (Throwable t) {
                        ++operationFailureCount;
                        statusCode = throwableToFailedResult(t).getResultCode();
                        Log.e(TAG, "Unable to persist the data to disk", t);
                    } finally {
                        if (instance != null) {
                            int estimatedBinderLatencyMillis =
                                    2 * (int) (totalLatencyStartTimeMillis
                                            - binderCallStartTimeMillis);
                            int totalLatencyMillis =
                                    (int) (SystemClock.elapsedRealtime()
                                            - totalLatencyStartTimeMillis);
                            instance.getLogger().logStats(new CallStats.Builder()
                                    .setStatusCode(statusCode)
                                    .setTotalLatencyMillis(totalLatencyMillis)
                                    .setCallType(CallStats.CALL_TYPE_FLUSH)
                                    // TODO(b/173532925) check the existing binder call latency
                                    // chart is good enough for us:
                                    // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                    .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                    .setNumOperationsSucceeded(operationSuccessCount)
                                    .setNumOperationsFailed(operationFailureCount)
                                    .build());
                        }
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "Unable to persist the data to disk", t);
            }
        }

        @Override
        public AppSearchResultParcel<Void> registerObserverCallback(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String targetPackageName,
                @NonNull Bundle observerSpecBundle,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchObserverProxy observerProxyStub) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(targetPackageName);
            Objects.requireNonNull(observerSpecBundle);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(observerProxyStub);

            // Note: registerObserverCallback is performed on the binder thread, unlike most
            // AppSearch APIs
            try {
                UserHandle targetUser = mServiceImplHelper.verifyIncomingCall(
                        callerAttributionSource, userHandle);
                long callingIdentity = Binder.clearCallingIdentity();
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);

                    // Prepare a new ObserverProxy linked to this binder.
                    AppSearchObserverProxy observerProxy =
                            new AppSearchObserverProxy(observerProxyStub);

                    // Watch for client disconnection, unregistering the observer if it happens.
                    observerProxyStub.asBinder().linkToDeath(
                            () -> instance.getAppSearchImpl()
                                    .unregisterObserverCallback(targetPackageName, observerProxy),
                            /*flags=*/ 0);

                    // Register the observer.
                    boolean callerHasSystemAccess = instance.getVisibilityChecker()
                            .doesCallerHaveSystemAccess(callerAttributionSource.getPackageName());
                    instance.getAppSearchImpl().registerObserverCallback(
                            new FrameworkCallerAccess(
                                    callerAttributionSource, callerHasSystemAccess),
                            targetPackageName,
                            new ObserverSpec(observerSpecBundle),
                            mExecutorManager.getOrCreateUserExecutor(targetUser),
                            new AppSearchObserverProxy(observerProxyStub));

                    return new AppSearchResultParcel<>(AppSearchResult.newSuccessfulResult(null));
                } finally {
                    Binder.restoreCallingIdentity(callingIdentity);
                }
            } catch (Throwable t) {
                return new AppSearchResultParcel<>(throwableToFailedResult(t));
            }
        }

        @Override
        public AppSearchResultParcel<Void> unregisterObserverCallback(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull String observedPackage,
                @NonNull UserHandle userHandle,
                @NonNull IAppSearchObserverProxy observerProxyStub) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(observedPackage);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(observerProxyStub);

            // Note: unregisterObserverCallback is performed on the binder thread, unlike most
            // AppSearch APIs
            try {
                UserHandle targetUser = mServiceImplHelper.verifyIncomingCall(
                        callerAttributionSource, userHandle);
                long callingIdentity = Binder.clearCallingIdentity();
                try {
                    AppSearchUserInstance instance =
                            mAppSearchUserInstanceManager.getUserInstance(targetUser);
                    instance.getAppSearchImpl().unregisterObserverCallback(
                            observedPackage,
                            new AppSearchObserverProxy(observerProxyStub));
                    return new AppSearchResultParcel<>(AppSearchResult.newSuccessfulResult(null));
                } finally {
                    Binder.restoreCallingIdentity(callingIdentity);
                }
            } catch (Throwable t) {
                return new AppSearchResultParcel<>(throwableToFailedResult(t));
            }
        }

        @Override
        public void initialize(
                @NonNull AttributionSource callerAttributionSource,
                @NonNull UserHandle userHandle,
                @ElapsedRealtimeLong long binderCallStartTimeMillis,
                @NonNull IAppSearchResultCallback callback) {
            Objects.requireNonNull(callerAttributionSource);
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(callback);

            long totalLatencyStartTimeMillis = SystemClock.elapsedRealtime();
            UserHandle targetUser = mServiceImplHelper.verifyIncomingCallWithCallback(
                    callerAttributionSource, userHandle, callback);
            if (targetUser == null) {
                return;  // Verification failed; verifyIncomingCall triggered callback.
            }
            mServiceImplHelper.executeLambdaForUserAsync(targetUser, callback, () -> {
                @AppSearchResult.ResultCode int statusCode = AppSearchResult.RESULT_OK;
                AppSearchUserInstance instance = null;
                int operationSuccessCount = 0;
                int operationFailureCount = 0;
                try {
                    Context targetUserContext = mContext.createContextAsUser(targetUser,
                            /*flags=*/ 0);
                    instance = mAppSearchUserInstanceManager.getOrCreateUserInstance(
                            targetUserContext,
                            targetUser,
                            FrameworkAppSearchConfig.getInstance(SHARED_EXECUTOR));
                    ++operationSuccessCount;
                    invokeCallbackOnResult(callback, AppSearchResult.newSuccessfulResult(null));
                } catch (Throwable t) {
                    ++operationFailureCount;
                    statusCode = throwableToFailedResult(t).getResultCode();
                    invokeCallbackOnResult(callback, throwableToFailedResult(t));
                } finally {
                    if (instance != null) {
                        int estimatedBinderLatencyMillis =
                                2 * (int) (totalLatencyStartTimeMillis - binderCallStartTimeMillis);
                        int totalLatencyMillis =
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartTimeMillis);
                        instance.getLogger().logStats(new CallStats.Builder()
                                .setStatusCode(statusCode)
                                .setTotalLatencyMillis(totalLatencyMillis)
                                .setCallType(CallStats.CALL_TYPE_INITIALIZE)
                                // TODO(b/173532925) check the existing binder call latency chart
                                // is good enough for us:
                                // http://dashboards/view/_72c98f9a_91d9_41d4_ab9a_bc14f79742b4
                                .setEstimatedBinderLatencyMillis(estimatedBinderLatencyMillis)
                                .setNumOperationsSucceeded(operationSuccessCount)
                                .setNumOperationsFailed(operationFailureCount)
                                .build());
                    }
                }
            });
        }
    }

    private class AppSearchStorageStatsAugmenter implements StorageStatsAugmenter {
        @Override
        public void augmentStatsForPackageForUser(
                @NonNull PackageStats stats,
                @NonNull String packageName,
                @NonNull UserHandle userHandle,
                boolean canCallerAccessAllStats) {
            Objects.requireNonNull(stats);
            Objects.requireNonNull(packageName);
            Objects.requireNonNull(userHandle);

            try {
                mServiceImplHelper.verifyUserUnlocked(userHandle);
                AppSearchUserInstance instance =
                        mAppSearchUserInstanceManager.getUserInstanceOrNull(userHandle);
                if (instance == null) {
                    // augment storage info from file
                    UserStorageInfo userStorageInfo =
                            mAppSearchUserInstanceManager.getOrCreateUserStorageInfoInstance(
                                    userHandle);
                    stats.dataSize +=
                            userStorageInfo.getSizeBytesForPackage(packageName);
                } else {
                    stats.dataSize += instance.getAppSearchImpl()
                            .getStorageInfoForPackage(packageName).getSizeBytes();
                }
            } catch (Throwable t) {
                Log.e(
                        TAG,
                        "Unable to augment storage stats for "
                                + userHandle
                                + " packageName "
                                + packageName,
                        t);
            }
        }

        @Override
        public void augmentStatsForUid(
                @NonNull PackageStats stats, int uid, boolean canCallerAccessAllStats) {
            Objects.requireNonNull(stats);

            UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
            try {
                mServiceImplHelper.verifyUserUnlocked(userHandle);
                String[] packagesForUid = mPackageManager.getPackagesForUid(uid);
                if (packagesForUid == null) {
                    return;
                }
                AppSearchUserInstance instance =
                        mAppSearchUserInstanceManager.getUserInstanceOrNull(userHandle);
                if (instance == null) {
                    // augment storage info from file
                    UserStorageInfo userStorageInfo =
                            mAppSearchUserInstanceManager.getOrCreateUserStorageInfoInstance(
                                    userHandle);
                    for (int i = 0; i < packagesForUid.length; i++) {
                        stats.dataSize += userStorageInfo.getSizeBytesForPackage(
                                packagesForUid[i]);
                    }
                } else {
                    for (int i = 0; i < packagesForUid.length; i++) {
                        stats.dataSize += instance.getAppSearchImpl()
                                .getStorageInfoForPackage(packagesForUid[i]).getSizeBytes();
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unable to augment storage stats for uid " + uid, t);
            }
        }

        @Override
        public void augmentStatsForUser(
                @NonNull PackageStats stats, @NonNull UserHandle userHandle) {
            // TODO(b/179160886): this implementation could incur many jni calls and a lot of
            //  in-memory processing from getStorageInfoForPackage. Instead, we can just compute the
            //  size of the icing dir (or use the overall StorageInfo without interpolating it).
            Objects.requireNonNull(stats);
            Objects.requireNonNull(userHandle);

            try {
                mServiceImplHelper.verifyUserUnlocked(userHandle);
                AppSearchUserInstance instance =
                        mAppSearchUserInstanceManager.getUserInstanceOrNull(userHandle);
                if (instance == null) {
                    // augment storage info from file
                    UserStorageInfo userStorageInfo =
                            mAppSearchUserInstanceManager.getOrCreateUserStorageInfoInstance(
                                    userHandle);
                    stats.dataSize += userStorageInfo.getTotalSizeBytes();
                } else {
                    List<PackageInfo> packagesForUser = mPackageManager.getInstalledPackagesAsUser(
                            /*flags=*/0, userHandle.getIdentifier());
                    if (packagesForUser != null) {
                        for (int i = 0; i < packagesForUser.size(); i++) {
                            String packageName = packagesForUser.get(i).packageName;
                            stats.dataSize += instance.getAppSearchImpl()
                                    .getStorageInfoForPackage(packageName).getSizeBytes();
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unable to augment storage stats for " + userHandle, t);
            }
        }
    }

    /**
     * Dispatches change notifications if there are any to dispatch.
     *
     * <p>This method is async; notifications are dispatched onto their own registered executors.
     *
     * <p>IMPORTANT: You must always call this within the background task that contains the
     * operation that mutated the index. If you called it outside of that task, it could start
     * before the task completes, causing notifications to be missed.
     */
    @WorkerThread
    private void dispatchChangeNotifications(@NonNull AppSearchUserInstance instance) {
        instance.getAppSearchImpl().dispatchAndClearChangeNotifications();
    }

    @WorkerThread
    private void checkForOptimize(
            @NonNull UserHandle targetUser,
            @NonNull AppSearchUserInstance instance,
            int mutateBatchSize) {
        if (mServiceImplHelper.isUserLocked(targetUser)) {
            // We shouldn't schedule any task to locked user.
            return;
        }
        mExecutorManager.getOrCreateUserExecutor(targetUser).execute(() -> {
            long totalLatencyStartMillis = SystemClock.elapsedRealtime();
            OptimizeStats.Builder builder = new OptimizeStats.Builder();
            try {
                instance.getAppSearchImpl().checkForOptimize(mutateBatchSize, builder);
            } catch (Exception e) {
                Log.w(TAG, "Error occurred when check for optimize", e);
            } finally {
                OptimizeStats oStats = builder
                        .setTotalLatencyMillis(
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartMillis))
                        .build();
                if (oStats.getOriginalDocumentCount() > 0) {
                    // see if optimize has been run by checking originalDocumentCount
                    instance.getLogger().logStats(oStats);
                }
            }
        });
    }

    @WorkerThread
    private void checkForOptimize(
            @NonNull UserHandle targetUser,
            @NonNull AppSearchUserInstance instance) {
        if (mServiceImplHelper.isUserLocked(targetUser)) {
            // We shouldn't schedule any task to locked user.
            return;
        }
        mExecutorManager.getOrCreateUserExecutor(targetUser).execute(() -> {
            long totalLatencyStartMillis = SystemClock.elapsedRealtime();
            OptimizeStats.Builder builder = new OptimizeStats.Builder();
            try {
                instance.getAppSearchImpl().checkForOptimize(builder);
            } catch (Exception e) {
                Log.w(TAG, "Error occurred when check for optimize", e);
            } finally {
                OptimizeStats oStats = builder
                        .setTotalLatencyMillis(
                                (int) (SystemClock.elapsedRealtime() - totalLatencyStartMillis))
                        .build();
                if (oStats.getOriginalDocumentCount() > 0) {
                    // see if optimize has been run by checking originalDocumentCount
                    instance.getLogger().logStats(oStats);
                }
            }
        });
    }
}
