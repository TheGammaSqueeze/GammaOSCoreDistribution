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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.ActivityManager;
import android.app.sdksandbox.IRemoteSdkCallback;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeRemoteSdkCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.ISdkSandboxToSdkSandboxManagerCallback;
import com.android.server.LocalManagerRegistry;
import com.android.server.pm.PackageManagerLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Unit tests for {@link SdkSandboxManagerService}.
 */
public class SdkSandboxManagerServiceUnitTest {

    private SdkSandboxManagerService mService;
    private ActivityManager mAmSpy;
    private FakeSdkSandboxService mSdkSandboxService;
    private FakeSdkSandboxProvider mProvider;
    private MockitoSession mStaticMockSession = null;

    private static final String SDK_PROVIDER_PACKAGE = "com.android.codeprovider";
    private static final String SDK_PROVIDER_RESOURCES_PACKAGE =
            "com.android.codeproviderresources";
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";

    @Before
    public void setup() {
        mStaticMockSession = ExtendedMockito.mockitoSession()
            .mockStatic(LocalManagerRegistry.class)
            .startMocking();

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Context spyContext = Mockito.spy(context);

        ActivityManager am = context.getSystemService(ActivityManager.class);
        mAmSpy = Mockito.spy(Objects.requireNonNull(am));

        Mockito.when(spyContext.getSystemService(ActivityManager.class)).thenReturn(mAmSpy);

        // Required to access <sdk-library> information.
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.ACCESS_SHARED_LIBRARIES, Manifest.permission.INSTALL_PACKAGES);
        mSdkSandboxService = new FakeSdkSandboxService();
        mProvider = new FakeSdkSandboxProvider(mSdkSandboxService);

        // Populate LocalManagerRegistry
        ExtendedMockito.doReturn(Mockito.mock(PackageManagerLocal.class))
            .when(() -> LocalManagerRegistry.getManager(PackageManagerLocal.class));

        mService = new SdkSandboxManagerService(spyContext, mProvider);
    }

    @After
    public void tearDown() throws Exception {
        mStaticMockSession.finishMocking();
    }

    /** Mock the ActivityManager::killUid to avoid SecurityException thrown in test. **/
    private void disableKillUid() {
        Mockito.doNothing().when(mAmSpy).killUid(Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    public void testLoadSdkIsSuccessful() throws Exception {
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
        // Assume SupplementalProcess loads successfully
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void testLoadSdkNonExistentCallingPackage() throws Exception {
        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> mService.loadSdk("does.not.exist", SDK_PROVIDER_PACKAGE, new Bundle(),
                        new FakeRemoteSdkCallback())
        );
        assertThat(thrown).hasMessageThat().contains("does.not.exist not found");
    }

    @Test
    public void testLoadSdkIncorrectCallingPackage() throws Exception {
        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> mService.loadSdk(SDK_PROVIDER_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(),
                        new FakeRemoteSdkCallback())
        );
        assertThat(thrown).hasMessageThat().contains("does not belong to uid");
    }

    @Test
    public void testLoadSdkPackageDoesNotExist() throws Exception {
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mService.loadSdk(TEST_PACKAGE, "does.not.exist", new Bundle(), callback);

        // Verify loading failed
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_NOT_FOUND);
        assertThat(callback.getLoadSdkErrorMsg()).contains("not found for loading");
    }

    @Test
    public void testLoadSdk_errorFromSdkSandbox() throws Exception {
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeError();

        // Verify loading failed
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode()).isEqualTo(
                SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR);
    }

    @Test
    public void testLoadSdk_successOnFirstLoad_errorOnLoadAgain() throws Exception {
        // Load it once
        {
            FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
            mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
            // Assume SupplementalProcess loads successfully
            mSdkSandboxService.sendLoadCodeSuccessful();
            assertThat(callback.isLoadSdkSuccessful()).isTrue();
        }

        // Load it again
        {
            FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
            mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
            // Verify loading failed
            assertThat(callback.isLoadSdkSuccessful()).isFalse();
            assertThat(callback.getLoadSdkErrorCode()).isEqualTo(
                    SdkSandboxManager.LOAD_SDK_SDK_ALREADY_LOADED);
            assertThat(callback.getLoadSdkErrorMsg()).contains("has been loaded already");
        }
    }

    @Test
    public void testLoadSdk_errorOnFirstLoad_canBeLoadedAgain() throws Exception {
        // Load code, but make it fail
        {
            FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
            mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
            // Assume SupplementalProcess load fails
            mSdkSandboxService.sendLoadCodeError();
            assertThat(callback.isLoadSdkSuccessful()).isFalse();
        }

        // Caller should be able to retry loading the code
        {
            FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
            mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
            // Assume SupplementalProcess loads successfully
            mSdkSandboxService.sendLoadCodeSuccessful();
            assertThat(callback.isLoadSdkSuccessful()).isTrue();
        }
    }

    @Test
    public void testRequestSurfacePackageSdkNotLoaded() {
        // Trying to request package without using proper sdkToken should fail
        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> mService.requestSurfacePackage(new Binder(), new Binder(),
                        0, new Bundle())
        );
        assertThat(thrown).hasMessageThat().contains("sdkToken is invalid");
    }

    @Test
    public void testRequestSurfacePackage() throws Exception {
        // 1. We first need to collect a proper sdkToken by calling loadCode
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        // Verify sdkToken is not null
        IBinder sdkToken = callback.getSdkToken();
        assertThat(sdkToken).isNotNull();

        // 2. Call request package with the retrieved sdkToken
        mService.requestSurfacePackage(sdkToken, new Binder(), 0, new Bundle());
        mSdkSandboxService.sendSurfacePackageReady();
        assertThat(callback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testRequestSurfacePackageFailedAfterAppDied() throws Exception {
        disableKillUid();

        FakeRemoteSdkCallback callback = Mockito.spy(new FakeRemoteSdkCallback());
        Mockito.doReturn(Mockito.mock(Binder.class)).when(callback).asBinder();

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient = ArgumentCaptor
                .forClass(IBinder.DeathRecipient.class);

        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        Mockito.verify(callback.asBinder())
                .linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        // After App Died
        SecurityException thrown = assertThrows(
                SecurityException.class,
                () -> mService.requestSurfacePackage(callback.getSdkToken(), new Binder(),
                        0, new Bundle())
        );
        assertThat(thrown).hasMessageThat().contains("sdkToken is invalid");
    }

    @Test
    public void testSurfacePackageError() throws Exception {
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
        // Assume SurfacePackage encounters an error.
        mSdkSandboxService.sendSurfacePackageError(
                SdkSandboxManager.SURFACE_PACKAGE_INTERNAL_ERROR, "bad surface");
        assertThat(callback.getSurfacePackageErrorMsg()).contains("bad surface");
        assertThat(callback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test(expected = SecurityException.class)
    public void testDumpWithoutPermission() {
        mService.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), new String[0]);
    }

    @Test
    public void testSupplementalProcessUnbindingWhenAppDied() throws Exception {
        disableKillUid();

        IRemoteSdkCallback.Stub callback = Mockito.spy(IRemoteSdkCallback.Stub.class);
        int callingUid = Binder.getCallingUid();
        assertThat(mProvider.getBoundServiceForApp(callingUid)).isNull();

        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient = ArgumentCaptor
                .forClass(IBinder.DeathRecipient.class);
        Mockito.verify(callback.asBinder(), Mockito.times(1))
                .linkToDeath(deathRecipient.capture(), Mockito.eq(0));

        assertThat(mProvider.getBoundServiceForApp(callingUid)).isNotNull();
        deathRecipient.getValue().binderDied();
        assertThat(mProvider.getBoundServiceForApp(callingUid)).isNull();
    }

    /* Tests resources defined in CodeProviderWithResources may be read. */
    @Test
    public void testCodeContextResourcesAndAssets() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(SDK_PROVIDER_RESOURCES_PACKAGE,
                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        assertThat(info).isNotNull();
        SandboxedSdkContext sandboxedSdkContext = new SandboxedSdkContext(context, info);
        Resources resources = sandboxedSdkContext.getResources();

        int integerId = resources.getIdentifier("test_integer", "integer",
                SDK_PROVIDER_RESOURCES_PACKAGE);
        assertThat(integerId).isNotEqualTo(0);
        assertThat(resources.getInteger(integerId)).isEqualTo(1234);

        int stringId = resources.getIdentifier("test_string", "string",
                SDK_PROVIDER_RESOURCES_PACKAGE);
        assertThat(stringId).isNotEqualTo(0);
        assertThat(resources.getString(stringId)).isEqualTo("Test String");

        AssetManager assetManager = resources.getAssets();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(assetManager.open("test-asset.txt")));
        assertThat(reader.readLine()).isEqualTo("This is a test asset");
    }

    /** Tests that only allowed intents may be sent from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToSendBroadcast() {
        SdkSandboxManagerLocal mSdkSandboxManagerLocal = mService.getLocalManager();
        Intent allowedIntent = new Intent(Intent.ACTION_VIEW);
        mSdkSandboxManagerLocal.enforceAllowedToSendBroadcast(allowedIntent);

        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_ON);
        assertThrows(SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToSendBroadcast(disallowedIntent));
    }

    /** Tests that only allowed activities may be started from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToStartActivity() {
        SdkSandboxManagerLocal mSdkSandboxManagerLocal = mService.getLocalManager();
        Intent allowedIntent = new Intent(Intent.ACTION_VIEW);
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(allowedIntent);

        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_OFF);
        assertThrows(SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(disallowedIntent));
    }

    @Test
    public void testGetSdkSandboxProcessNameForInstrumentation() throws Exception {
        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        final ApplicationInfo info = pm.getApplicationInfo(TEST_PACKAGE, 0);
        final String processName = localManager.getSdkSandboxProcessNameForInstrumentation(info);
        assertThat(processName).isEqualTo(TEST_PACKAGE + "_sdk_sandbox_instr");
    }

    @Test
    public void testNotifyInstrumentationStarted_killsSandboxProcess() throws Exception {
        disableKillUid();

        // First load SDK.
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(mProvider.getBoundServiceForApp(Process.myUid())).isNotNull();

        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        localManager.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());

        // Verify that sdk sandbox was killed
        Mockito.verify(mAmSpy, Mockito.only())
                .killUid(Mockito.eq(Process.toSdkSandboxUid(Process.myUid())), Mockito.anyString());
        assertThat(mProvider.getBoundServiceForApp(Process.myUid())).isNull();
    }

    @Test
    public void testNotifyInstrumentationStarted_doesNotAllowLoadSdk() throws Exception {
        disableKillUid();

        // First load SDK.
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(mProvider.getBoundServiceForApp(Process.myUid())).isNotNull();

        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        localManager.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());
        assertThat(mProvider.getBoundServiceForApp(Process.myUid())).isNull();

        // Try load again, it should throw SecurityException
        FakeRemoteSdkCallback callback2 = new FakeRemoteSdkCallback();
        SecurityException e = assertThrows(
                SecurityException.class,
                () -> mService.loadSdk(
                        TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback2));
        assertThat(e).hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");
    }

    @Test
    public void testNotifyInstrumentationFinished_canLoadSdk() throws Exception {
        disableKillUid();

        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        localManager.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());
        assertThat(mProvider.getBoundServiceForApp(Process.myUid())).isNull();

        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        // Try loading, it should throw SecurityException
        SecurityException e = assertThrows(
                SecurityException.class,
                () -> mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback));
        assertThat(e).hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");

        localManager.notifyInstrumentationFinished(TEST_PACKAGE, Process.myUid());

        FakeRemoteSdkCallback callback2 = new FakeRemoteSdkCallback();
        // Now loading should work
        mService.loadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, new Bundle(), callback2);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();
        assertThat(mProvider.getBoundServiceForApp(Process.myUid())).isNotNull();
    }

    @Test
    public void testEnforceAllowedToStartOrBindService() {
        SdkSandboxManagerLocal mSdkSandboxManagerLocal = mService.getLocalManager();

        Intent disallowedIntent = new Intent();
        disallowedIntent.setComponent(new ComponentName("nonexistent.package", "test"));
        assertThrows(SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(disallowedIntent));
    }

    /**
     * Fake service provider that returns local instance of {@link SdkSandboxServiceProvider}
     */
    private static class FakeSdkSandboxProvider implements SdkSandboxServiceProvider {
        private final ISdkSandboxService mSdkSandboxService;
        private final ArrayMap<Integer, ISdkSandboxService> mService = new ArrayMap<>();

        FakeSdkSandboxProvider(ISdkSandboxService service) {
            mSdkSandboxService = service;
        }

        @Override
        public void bindService(int callingUid, String callingPackage,
                ServiceConnection serviceConnection) {
            if (mService.containsKey(callingUid)) {
                return;
            }
            mService.put(callingUid, mSdkSandboxService);
            serviceConnection.onServiceConnected(null, mSdkSandboxService.asBinder());
        }

        @Override
        public void unbindService(int callingUid) {
            mService.remove(callingUid);
        }

        @Nullable
        @Override
        public ISdkSandboxService getBoundServiceForApp(int callingUid) {
            return mService.get(callingUid);
        }

        @Override
        public void setBoundServiceForApp(int callingUid, @Nullable ISdkSandboxService service) {
            mService.put(callingUid, service);
        }
    }

    public static class FakeSdkSandboxService extends ISdkSandboxService.Stub {
        private ISdkSandboxToSdkSandboxManagerCallback mSdkSandboxToManagerCallback;
        private final ISdkSandboxManagerToSdkSandboxCallback mManagerToSdkCallback;

        boolean mSurfacePackageRequested = false;

        FakeSdkSandboxService() {
            mManagerToSdkCallback = new FakeManagerToSdkCallback();
        }

        @Override
        public void loadSdk(IBinder codeToken, ApplicationInfo info, String codeProviderClassName,
                Bundle params, ISdkSandboxToSdkSandboxManagerCallback callback) {
            mSdkSandboxToManagerCallback = callback;
        }

        void sendLoadCodeSuccessful() throws RemoteException {
            mSdkSandboxToManagerCallback.onLoadSdkSuccess(new Bundle(), mManagerToSdkCallback);
        }

        void sendLoadCodeError() throws RemoteException {
            mSdkSandboxToManagerCallback.onLoadSdkError(
                    SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR, "Internal error");
        }

        void sendSurfacePackageReady() throws RemoteException {
            if (mSurfacePackageRequested) {
                mSdkSandboxToManagerCallback.onSurfacePackageReady(
                        /*hostToken=*/null, /*displayId=*/0, /*params=*/null);
            }
        }

        void sendSurfacePackageError(int errorCode, String errorMsg) throws RemoteException {
            mSdkSandboxToManagerCallback.onSurfacePackageError(errorCode, errorMsg);
        }

        private class FakeManagerToSdkCallback extends ISdkSandboxManagerToSdkSandboxCallback.Stub {
            @Override
            public void onSurfacePackageRequested(IBinder hostToken,
                    int displayId, Bundle extraParams) {
                mSurfacePackageRequested = true;
            }
        }
    }
}
