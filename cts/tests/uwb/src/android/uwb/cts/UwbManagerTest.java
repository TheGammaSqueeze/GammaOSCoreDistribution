/*
 * Copyright 2021 The Android Open Source Project
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

package android.uwb.cts;

import static android.Manifest.permission.UWB_PRIVILEGED;
import static android.Manifest.permission.UWB_RANGING;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_DISABLED;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiAutomation;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.os.Process;
import android.permission.PermissionManager;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.uwb.RangingReport;
import android.uwb.RangingSession;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.multichip.ChipInfoParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test of {@link UwbManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get UwbManager in instant app mode")
public class UwbManagerTest {
    private static final String TAG = "UwbManagerTest";

    private final Context mContext = InstrumentationRegistry.getContext();
    private UwbManager mUwbManager;
    private String mDefaultChipId;

    @Before
    public void setup() throws Exception {
        mUwbManager = mContext.getSystemService(UwbManager.class);
        assumeTrue(UwbTestUtils.isUwbSupported(mContext));
        assertThat(mUwbManager).isNotNull();

        // Ensure UWB is toggled on.
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            if (!mUwbManager.isUwbEnabled()) {
                try {
                    setUwbEnabledAndWaitForCompletion(true);
                } catch (Exception e) {
                    fail("Exception while processing UWB toggle " + e);
                }
            }
            mDefaultChipId = mUwbManager.getDefaultChipId();
        });
    }

    // Should be invoked with shell permissions.
    private void setUwbEnabledAndWaitForCompletion(boolean enabled) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        int adapterState = enabled ? STATE_ENABLED_INACTIVE : STATE_DISABLED;
        AdapterStateCallback adapterStateCallback =
                new AdapterStateCallback(countDownLatch, adapterState);
        try {
            mUwbManager.registerAdapterStateCallback(
                    Executors.newSingleThreadExecutor(), adapterStateCallback);
            mUwbManager.setUwbEnabled(enabled);
            assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(mUwbManager.isUwbEnabled()).isEqualTo(enabled);
            assertThat(adapterStateCallback.state).isEqualTo(adapterState);
        } finally {
            mUwbManager.unregisterAdapterStateCallback(adapterStateCallback);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfo() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            PersistableBundle persistableBundle = mUwbManager.getSpecificationInfo();
            assertThat(persistableBundle).isNotNull();
            assertThat(persistableBundle.isEmpty()).isFalse();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            PersistableBundle persistableBundle =
                    mUwbManager.getSpecificationInfo(mDefaultChipId);
            assertThat(persistableBundle).isNotNull();
            assertThat(persistableBundle.isEmpty()).isFalse();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetChipInfos() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            List<PersistableBundle> chipInfos = mUwbManager.getChipInfos();
            assertThat(chipInfos).hasSize(1);
            ChipInfoParams chipInfoParams = ChipInfoParams.fromBundle(chipInfos.get(0));
            assertThat(chipInfoParams.getChipId()).isEqualTo(mDefaultChipId);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithInvalidChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThrows(IllegalArgumentException.class,
                    () -> mUwbManager.getSpecificationInfo("invalidChipId"));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithoutUwbPrivileged() {
        try {
            mUwbManager.getSpecificationInfo();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithChipIdWithoutUwbPrivileged() {
        try {
            mUwbManager.getSpecificationInfo(mDefaultChipId);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }


    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanos() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThat(mUwbManager.elapsedRealtimeResolutionNanos() >= 0L).isTrue();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThat(mUwbManager.elapsedRealtimeResolutionNanos(mDefaultChipId) >= 0L)
                    .isTrue();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithInvalidChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThrows(IllegalArgumentException.class,
                    () -> mUwbManager.elapsedRealtimeResolutionNanos("invalidChipId"));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithoutUwbPrivileged() {
        try {
            mUwbManager.elapsedRealtimeResolutionNanos();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithChipIdWithoutUwbPrivileged() {
        try {
            mUwbManager.elapsedRealtimeResolutionNanos(mDefaultChipId);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testAddServiceProfileWithoutUwbPrivileged() {
        try {
            mUwbManager.addServiceProfile(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testRemoveServiceProfileWithoutUwbPrivileged() {
        try {
            mUwbManager.removeServiceProfile(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }


    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetAllServiceProfilesWithoutUwbPrivileged() {
        try {
            mUwbManager.getAllServiceProfiles();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetAdfProvisioningAuthoritiesWithoutUwbPrivileged() {
        try {
            mUwbManager.getAdfProvisioningAuthorities(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetAdfCertificateInfoWithoutUwbPrivileged() {
        try {
            mUwbManager.getAdfCertificateInfo(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetChipInfosWithoutUwbPrivileged() {
        try {
            mUwbManager.getChipInfos();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciWithoutUwbPrivileged() {
        try {
            mUwbManager.sendVendorUciMessage(10, 0, new byte[0]);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    private class AdfProvisionStateCallback extends UwbManager.AdfProvisionStateCallback {
        private final CountDownLatch mCountDownLatch;

        public boolean onSuccessCalled;
        public boolean onFailedCalled;

        AdfProvisionStateCallback(@NonNull CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onProfileAdfsProvisioned(@NonNull PersistableBundle params) {
            onSuccessCalled = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onProfileAdfsProvisionFailed(int reason, @NonNull PersistableBundle params) {
            onFailedCalled = true;
            mCountDownLatch.countDown();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testProvisionProfileAdfByScriptWithoutUwbPrivileged() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdfProvisionStateCallback adfProvisionStateCallback =
                new AdfProvisionStateCallback(countDownLatch);
        try {
            mUwbManager.provisionProfileAdfByScript(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    adfProvisionStateCallback);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testRemoveProfileAdfWithoutUwbPrivileged() {
        try {
            mUwbManager.removeProfileAdf(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    private class UwbVendorUciCallback implements UwbManager.UwbVendorUciCallback {
        private final CountDownLatch mRspCountDownLatch;
        private final CountDownLatch mNtfCountDownLatch;

        public int gid;
        public int oid;
        public byte[] payload;

        UwbVendorUciCallback(
                @NonNull CountDownLatch rspCountDownLatch,
                @NonNull CountDownLatch ntfCountDownLatch) {
            mRspCountDownLatch = rspCountDownLatch;
            mNtfCountDownLatch = ntfCountDownLatch;
        }

        @Override
        public void onVendorUciResponse(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mRspCountDownLatch.countDown();
        }

        @Override
        public void onVendorUciNotification(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mNtfCountDownLatch.countDown();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testRegisterVendorUciCallbackWithoutUwbPrivileged() {
        UwbManager.UwbVendorUciCallback cb =
                new UwbVendorUciCallback(new CountDownLatch(1), new CountDownLatch(1));
        try {
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testUnregisterVendorUciCallbackWithoutUwbPrivileged() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        UwbManager.UwbVendorUciCallback cb =
                new UwbVendorUciCallback(new CountDownLatch(1), new CountDownLatch(1));
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);
        } catch (SecurityException e) {
            /* pass */
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        try {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testInvalidCallbackUnregisterVendorUciCallback() {
        UwbManager.UwbVendorUciCallback cb =
                new UwbVendorUciCallback(new CountDownLatch(1), new CountDownLatch(1));
        try {
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);
        } catch (SecurityException e) {
            /* registration failed */
        }
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.unregisterUwbVendorUciCallback(cb);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private class RangingSessionCallback implements RangingSession.Callback {
        private CountDownLatch mCtrlCountDownLatch;
        private CountDownLatch mResultCountDownLatch;

        public boolean onOpenedCalled;
        public boolean onOpenFailedCalled;
        public boolean onStartedCalled;
        public boolean onStartFailedCalled;
        public boolean onClosedCalled;
        public RangingSession rangingSession;
        public RangingReport rangingReport;

        RangingSessionCallback(
                @NonNull CountDownLatch ctrlCountDownLatch) {
            this(ctrlCountDownLatch, null /* resultCountDownLaynch */);
        }

        RangingSessionCallback(
                @NonNull CountDownLatch ctrlCountDownLatch,
                @Nullable CountDownLatch resultCountDownLatch) {
            mCtrlCountDownLatch = ctrlCountDownLatch;
            mResultCountDownLatch = resultCountDownLatch;
        }

        public void replaceCtrlCountDownLatch(@NonNull CountDownLatch countDownLatch) {
            mCtrlCountDownLatch = countDownLatch;
        }

        public void onOpened(@NonNull RangingSession session) {
            onOpenedCalled = true;
            rangingSession = session;
            mCtrlCountDownLatch.countDown();
        }

        public void onOpenFailed(@Reason int reason, @NonNull PersistableBundle params) {
            onOpenFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onStarted(@NonNull PersistableBundle sessionInfo) {
            onStartedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onStartFailed(@Reason int reason, @NonNull PersistableBundle params) {
            onStartFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onReconfigured(@NonNull PersistableBundle params) { }

        public void onReconfigureFailed(@Reason int reason, @NonNull PersistableBundle params) { }

        public void onStopped(@Reason int reason, @NonNull PersistableBundle parameters) { }

        public void onStopFailed(@Reason int reason, @NonNull PersistableBundle params) { }

        public void onClosed(@Reason int reason, @NonNull PersistableBundle parameters) {
            onClosedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onReportReceived(@NonNull RangingReport rangingReport) {
            if (mResultCountDownLatch != null) {
                this.rangingReport = rangingReport;
                mResultCountDownLatch.countDown();
            }
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithInvalidChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(countDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Try to start a ranging session with invalid params, should fail.
            assertThrows(IllegalArgumentException.class, () -> mUwbManager.openRangingSession(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    "invalidChipId"));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithChipIdWithBadParams() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(countDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Try to start a ranging session with invalid params, should fail.
            cancellationSignal = mUwbManager.openRangingSession(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on start failed callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isFalse();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithInvalidChipIdWithBadParams() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(countDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Try to start a ranging session with invalid params, should fail.
            cancellationSignal = mUwbManager.openRangingSession(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on start failed callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isFalse();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Simulates the app holding UWB_RANGING permission, but not UWB_PRIVILEGED.
     */
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithoutUwbPrivileged() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_RANGING permission
            uiAutomation.adoptShellPermissionIdentity(UWB_RANGING);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)));
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithChipIdWithoutUwbPrivileged() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_RANGING permission
            uiAutomation.adoptShellPermissionIdentity(UWB_RANGING);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)),
                    mDefaultChipId);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Simulates the app holding UWB_PRIVILEGED permission, but not UWB_RANGING.
     */
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithoutUwbRanging() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity(UWB_PRIVILEGED);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)));
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithChipIdWithoutUwbRanging() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity(UWB_PRIVILEGED);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)),
                    mDefaultChipId);
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private AttributionSource getShellAttributionSourceWithRenouncedPermissions(
            @Nullable Set<String> renouncedPermissions) {
        try {
            AttributionSource shellAttributionSource =
                    new AttributionSource.Builder(Process.SHELL_UID)
                            .setPackageName("com.android.shell")
                            .setRenouncedPermissions(renouncedPermissions)
                            .build();
            PermissionManager permissionManager =
                    mContext.getSystemService(PermissionManager.class);
            permissionManager.registerAttributionSource(shellAttributionSource);
            return shellAttributionSource;
        } catch (SecurityException e) {
            fail("Failed to create shell attribution source" + e);
            return null;
        }
    }

    private Context createShellContextWithRenouncedPermissionsAndAttributionSource(
            @Nullable Set<String> renouncedPermissions) {
        return mContext.createContext(new ContextParams.Builder()
                .setRenouncedPermissions(renouncedPermissions)
                .setNextAttributionSource(
                        getShellAttributionSourceWithRenouncedPermissions(renouncedPermissions))
                .build());
    }

    /**
     * Simulates the calling app holding UWB_PRIVILEGED permission and UWB_RANGING permission, but
     * the proxied app not holding UWB_RANGING permission.
     */
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testOpenRangingSessionWithoutUwbRangingInNextAttributeSource() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_PRIVILEGED permission
            uiAutomation.adoptShellPermissionIdentity();
            Context shellContextWithUwbRangingRenounced =
                    createShellContextWithRenouncedPermissionsAndAttributionSource(
                            Set.of(UWB_RANGING));
            UwbManager uwbManagerWithUwbRangingRenounced =
                    shellContextWithUwbRangingRenounced.getSystemService(UwbManager.class);
            uwbManagerWithUwbRangingRenounced.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)));
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testOpenRangingSessionWithChipIdWithoutUwbRangingInNextAttributeSource() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_PRIVILEGED permission
            uiAutomation.adoptShellPermissionIdentity();
            Context shellContextWithUwbRangingRenounced =
                    createShellContextWithRenouncedPermissionsAndAttributionSource(
                            Set.of(UWB_RANGING));
            UwbManager uwbManagerWithUwbRangingRenounced =
                    shellContextWithUwbRangingRenounced.getSystemService(UwbManager.class);
            uwbManagerWithUwbRangingRenounced.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)),
                    mDefaultChipId);
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSession() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch, resultCountDownLatch);
        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(1, 1))
                .setSessionId(1)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x5, 0x6})
                .setStaticStsIV(new byte[]{0x5, 0x6, 0x9, 0xa, 0x4, 0x6})
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {0x5, 6}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[] {0x5, 6})))
                .build();
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Try to start a ranging session with invalid params, should fail.
            cancellationSignal = mUwbManager.openRangingSession(
                    firaOpenSessionParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isTrue();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isFalse();
            assertThat(rangingSessionCallback.rangingSession).isNotNull();

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStartedCalled).isTrue();
            assertThat(rangingSessionCallback.onStartFailedCalled).isFalse();

            // Wait for the on ranging report callback.
            assertThat(resultCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.rangingReport).isNotNull();

            // Check the UWB state.
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);

            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();
        } finally {
            if (cancellationSignal != null) {
                countDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                // Close session.
                cancellationSignal.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(rangingSessionCallback.onClosedCalled).isTrue();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private class AdapterStateCallback implements UwbManager.AdapterStateCallback {
        private final CountDownLatch mCountDownLatch;
        private final @State Integer mWaitForState;
        public int state;
        public int reason;

        AdapterStateCallback(@NonNull CountDownLatch countDownLatch,
                @Nullable @State Integer waitForState) {
            mCountDownLatch = countDownLatch;
            mWaitForState = waitForState;
        }

        public void onStateChanged(@State int state, @StateChangedReason int reason) {
            this.state = state;
            this.reason = reason;
            if (mWaitForState != null) {
                if (mWaitForState == state) {
                    mCountDownLatch.countDown();
                }
            } else {
                mCountDownLatch.countDown();
            }
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-4"})
    public void testUwbStateToggle() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThat(mUwbManager.isUwbEnabled()).isTrue();
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_INACTIVE);
            // Toggle the state
            setUwbEnabledAndWaitForCompletion(false);
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_DISABLED);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciMessage() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        UwbVendorUciCallback cb =
                new UwbVendorUciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Send random payload with a vendor gid.
            byte[] payload = new byte[100];
            new Random().nextBytes(payload);
            int gid = 9;
            int oid = 1;
            mUwbManager.sendVendorUciMessage(gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } catch (SecurityException e) {
            /* pass */
        } finally {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciMessageWithFragmentedPackets() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        UwbVendorUciCallback cb =
                new UwbVendorUciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Send random payload > 255 bytes with a vendor gid.
            byte[] payload = new byte[400];
            new Random().nextBytes(payload);
            int gid = 9;
            int oid = 1;
            mUwbManager.sendVendorUciMessage(gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } catch (SecurityException e) {
            /* pass */
        } finally {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
