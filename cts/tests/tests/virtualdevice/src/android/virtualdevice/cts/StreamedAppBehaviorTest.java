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

package android.virtualdevice.cts;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.READ_CLIPBOARD_IN_BACKGROUND;
import static android.Manifest.permission.WAKE_LOCK;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createActivityOptions;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.ActivityListener;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.EmptyActivity;
import android.virtualdevice.cts.util.FakeAssociationRule;
import android.virtualdevice.cts.util.TestAppHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class StreamedAppBehaviorTest {

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            READ_CLIPBOARD_IN_BACKGROUND,
            WAKE_LOCK);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    @Nullable private VirtualDevice mVirtualDevice;
    @Nullable private VirtualDisplay mVirtualDisplay;
    private Context mContext;
    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock
    private ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = getApplicationContext();
        final PackageManager packageManager = mContext.getPackageManager();
        assumeTrue(packageManager
                .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        // TODO(b/261155110): Re-enable tests once freeform mode is supported in Virtual Display.
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));
        mVirtualDeviceManager = mContext.getSystemService(VirtualDeviceManager.class);
        mVirtualDevice =
                mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ 0,
                Runnable::run,
                mVirtualDisplayCallback);
    }

    @After
    public void tearDown() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void appsInVirtualDevice_shouldNotHaveAccessToClipboard() {
        ClipboardManager clipboardManager = mContext.getSystemService(ClipboardManager.class);
        clipboardManager.setPrimaryClip(
                new ClipData(
                        "CTS test clip",
                        new String[] { "application/text" },
                        new ClipData.Item("clipboard content from test")));

        EmptyActivity activity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(mContext, EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(mVirtualDisplay));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        activity.setCallback(callback);

        int requestCode = 1;
        activity.startActivityForResult(
                TestAppHelper.createClipboardTestIntent("clipboard content from app"),
                requestCode,
                createActivityOptions(mVirtualDisplay));

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(callback, timeout(10000)).onActivityResult(
                eq(requestCode), eq(Activity.RESULT_OK), intentArgumentCaptor.capture());
        Intent resultData = intentArgumentCaptor.getValue();
        // This is important to get us off of the virtual display so we can read the clipboard
        activity.finish();

        assertThat(resultData).isNotNull();
        ClipData appReadClipData = resultData.getParcelableExtra("readClip");
        assertThat(appReadClipData).isNull();
        verify(mActivityListener, timeout(3000))
                .onDisplayEmpty(eq(mVirtualDisplay.getDisplay().getDisplayId()));
        assertThat(clipboardManager.getPrimaryClip().getItemAt(0).getText().toString())
                .isEqualTo("clipboard content from test");
    }

    @Test
    public void appsInVirtualDevice_shouldNotHaveAccessToCamera() throws CameraAccessException {
        CameraManager manager = mContext.getSystemService(CameraManager.class);
        String[] cameras = manager.getCameraIdList();
        assume().that(cameras).isNotNull();

        for (String cameraId : cameras) {
            EmptyActivity activity =
                    (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                            .startActivitySync(
                                    new Intent(mContext, EmptyActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                    createActivityOptions(mVirtualDisplay));

            EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
            activity.setCallback(callback);

            int requestCode = 1;
            activity.startActivityForResult(
                    TestAppHelper.createCameraAccessTestIntent().putExtra(
                            TestAppHelper.EXTRA_CAMERA_ID, cameraId),
                    requestCode,
                    createActivityOptions(mVirtualDisplay));

            ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
            verify(callback, timeout(10000)).onActivityResult(
                    eq(requestCode), eq(Activity.RESULT_OK), intentArgumentCaptor.capture());
            Intent resultData = intentArgumentCaptor.getValue();
            activity.finish();

            assertThat(resultData).isNotNull();
            String result = resultData.getStringExtra(TestAppHelper.EXTRA_CAMERA_RESULT);
            assertThat(result).isAnyOf("onDisconnected", "onError");
            if (result.equals("onError")) {
                int error = resultData.getIntExtra(TestAppHelper.EXTRA_CAMERA_ON_ERROR_CODE, -1);
                assertThat(error).isEqualTo(CameraDevice.StateCallback.ERROR_CAMERA_DISABLED);
            }
        }
    }

    @Test
    public void isDeviceSecure_shouldReturnFalseOnVirtualDisplay() {
        EmptyActivity activity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(mContext, EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(mVirtualDisplay));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        activity.setCallback(callback);

        int requestCode = 1;
        activity.startActivityForResult(
                TestAppHelper.createKeyguardManagerIsDeviceSecureTestIntent(),
                requestCode,
                createActivityOptions(mVirtualDisplay));

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(callback, timeout(5000)).onActivityResult(
                eq(requestCode), eq(Activity.RESULT_OK), intentArgumentCaptor.capture());
        Intent resultData = intentArgumentCaptor.getValue();
        // This is important to get us off of the virtual display
        activity.finish();

        assertThat(resultData).isNotNull();
        boolean isDeviceSecure = resultData.getBooleanExtra(
                TestAppHelper.EXTRA_IS_DEVICE_SECURE, true);
        assertThat(isDeviceSecure).isFalse();
    }
}
