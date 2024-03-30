/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.FEATURE_CAMERA;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.CameraPolicy;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequireFeature(FEATURE_CAMERA)
@RunWith(BedsteadJUnit4.class)
public final class CameraPolicyTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final CameraManager sCameraManager =
            sContext.getSystemService(CameraManager.class);
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static AppOpsManager sAppOpsManager = sContext.getSystemService(AppOpsManager.class);

    private static final String TAG = "CameraUtils";

    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = CameraPolicy.class)
    public void setCameraDisabledTrue_policyDoesNotApply_cameraNotDisabled() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), true);

        assertThat(sLocalDevicePolicyManager
                .getCameraDisabled(null)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = CameraPolicy.class)
    public void setCameraDisabledTrue_cameraDisabledLocally() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), true);

        assertThat(sLocalDevicePolicyManager
                .getCameraDisabled(null)).isTrue();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = CameraPolicy.class)
    public void setCameraDisabledFalse_cameraEnabledLocally() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false);

        assertThat(sLocalDevicePolicyManager
                .getCameraDisabled(null)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CameraPolicy.class)
    public void setCameraDisabledTrue_cameraDisabledAtDPC() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), true);

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(null)).isTrue();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CameraPolicy.class)
    public void setCameraDisabledFalse_cameraEnabledAtDPC() {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false);

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getCameraDisabled(null)).isFalse();
    }

    @Ignore("b/201753989 Properly define behaviour of setCameraDisabled on secondary user POs")
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = CameraPolicy.class)
    public void setCameraDisabledTrue_policyNotAllowedToBeSet_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), true));
    }

    @Ignore("b/201753989 Properly define behaviour of setCameraDisabled on secondary user POs")
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = CameraPolicy.class)
    public void setCameraDisabledFalse_policyNotAllowedToBeSet_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false));
    }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CAMERA)
    @CanSetPolicyTest(policy = CameraPolicy.class)
    public void openCamera_cameraEnabled_successful() throws Exception {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), false);

        PollingCheck.waitFor(() ->
                sAppOpsManager.unsafeCheckOp(
                        AppOpsManager.OPSTR_CAMERA,
                        android.os.Process.myUid(),
                        sContext.getOpPackageName()) == 0);

        assertCanOpenCamera();
    }

    @Postsubmit(reason = "new test")
    @EnsureHasPermission(CAMERA)
    @CanSetPolicyTest(policy = CameraPolicy.class)
    public void openCamera_cameraDisabled_unsuccessful() throws Exception {
        sDeviceState.dpc().devicePolicyManager()
                .setCameraDisabled(sDeviceState.dpc().componentName(), true);

        PollingCheck.waitFor(() ->
                sAppOpsManager.unsafeCheckOp(
                        AppOpsManager.OPSTR_CAMERA,
                        android.os.Process.myUid(),
                        sContext.getOpPackageName()) == 1);

        assertCanNotOpenCamera();
    }

    private void assertCanOpenCamera() throws Exception {
        boolean successfullyOpened;

        String[] cameraIdList = sCameraManager.getCameraIdList();
        if (cameraIdList == null || cameraIdList.length == 0) {
            successfullyOpened = false;
        } else {
            String cameraId = cameraIdList[0];
            // TODO(b/202012931): Use a BlockingCallback once it is possible to use it in
            // conjunction with StateCallback
            CameraPolicyTest.CameraCallback callback = new CameraPolicyTest.CameraCallback();
            sCameraManager.openCamera(cameraId, callback, new Handler(Looper.getMainLooper()));
            successfullyOpened = callback.waitForResult();
        }

        assertThat(successfullyOpened).isTrue();
    }

    private void assertCanNotOpenCamera() {
        boolean successfullyOpened;

        try {
            String[] cameraIdList = sCameraManager.getCameraIdList();
            if (cameraIdList == null || cameraIdList.length == 0) {
                successfullyOpened = false;
            } else {
                String cameraId = cameraIdList[0];
                CameraPolicyTest.CameraCallback callback = new CameraPolicyTest.CameraCallback();
                sCameraManager.openCamera(cameraId, callback, new Handler(Looper.getMainLooper()));
                successfullyOpened = callback.waitForResult();
            }
        } catch (Exception e) {
            Log.d(TAG,
                    "Exception when opening camera but we expected to not be able to open it",
                    e);
            successfullyOpened = false;
        }

        assertThat(successfullyOpened).isFalse();
    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private static class CameraCallback extends CameraDevice.StateCallback {

        private static final int OPEN_TIMEOUT_SECONDS = 5;

        private final CountDownLatch mLatch = new CountDownLatch(1);

        private AtomicBoolean mResult = new AtomicBoolean(false);

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.d(TAG, "open camera successfully");
            mResult.set(true);
            if (cameraDevice != null) {
                cameraDevice.close();
            }
            mLatch.countDown();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "disconnect camera");
            mLatch.countDown();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "Fail to open camera, error code = " + error);
            mLatch.countDown();
        }

        public boolean waitForResult() throws InterruptedException {
            mLatch.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return mResult.get();
        }
    }

}
