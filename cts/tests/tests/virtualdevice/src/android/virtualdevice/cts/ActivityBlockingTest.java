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
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.REAL_GET_TASKS;
import static android.Manifest.permission.WAKE_LOCK;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createActivityOptions;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createResultReceiver;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.ActivityListener;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.EmptyActivity;
import android.virtualdevice.cts.util.FakeAssociationRule;
import android.virtualdevice.cts.util.TestAppHelper;
import android.virtualdevice.cts.util.VirtualDeviceTestUtils.OnReceiveResultListener;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.internal.app.BlockedAppStreamingActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Tests for blocking of activities that should not be shown on the virtual device.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityBlockingTest {

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            REAL_GET_TASKS,
            WAKE_LOCK);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    @Nullable private VirtualDevice mVirtualDevice;
    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock
    private OnReceiveResultListener mOnReceiveResultListener;
    private ResultReceiver mResultReceiver;
    private ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        final PackageManager packageManager = context.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        // TODO(b/261155110): Re-enable tests once freeform mode is supported in Virtual Display.
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        mResultReceiver = createResultReceiver(mOnReceiveResultListener);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void nonTrustedDisplay_startNonEmbeddableActivity_shouldThrowSecurityException() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(DEFAULT_VIRTUAL_DEVICE_PARAMS,
                /* virtualDisplayFlags= */ 0);

        Intent intent = TestAppHelper.createNoEmbedIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertThrows(SecurityException.class, () ->
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .startActivity(intent, createActivityOptions(virtualDisplay)));
    }

    @Test
    public void cannotDisplayOnRemoteActivity_shouldBeBlockedFromLaunching() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(DEFAULT_VIRTUAL_DEVICE_PARAMS,
                /* virtualDisplayFlags= */ 0);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(getApplicationContext(), EmptyActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));

        emptyActivity.startActivity(TestAppHelper.createCannotDisplayOnRemoteIntent(
                /* newTask= */ true, mResultReceiver));
        verify(mOnReceiveResultListener, after(3000).never())
                .onReceiveResult(anyInt(), any());

        emptyActivity.startActivity(TestAppHelper.createCannotDisplayOnRemoteIntent(
                /* newTask= */ false, mResultReceiver));
        verify(mOnReceiveResultListener, after(3000).never())
                .onReceiveResult(anyInt(), any());
    }

    @Test
    public void trustedDisplay_startNonEmbeddableActivity_shouldSucceed() {
        VirtualDisplay virtualDisplay = createVirtualDisplay(DEFAULT_VIRTUAL_DEVICE_PARAMS,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);

        Intent intent = TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .startActivity(intent, createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, timeout(3000)).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result ->
                        result.getInt(TestAppHelper.EXTRA_DISPLAY)
                                == virtualDisplay.getDisplay().getDisplayId()));
    }

    @Test
    public void setAllowedActivities_shouldBlockNonAllowedActivities() {
        Context context = getApplicationContext();
        ComponentName emptyActivityComponentName = new ComponentName(context, EmptyActivity.class);
        VirtualDisplay virtualDisplay = createVirtualDisplay(new VirtualDeviceParams.Builder()
                        .setAllowedActivities(Set.of(emptyActivityComponentName))
                        .build(),
                /* virtualDisplayFlags= */ 0);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent().setComponent(emptyActivityComponentName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));

        emptyActivity.startActivity(
                TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver));

        verify(mOnReceiveResultListener, after(3000).never())
                .onReceiveResult(anyInt(), any());
    }

    @Test
    public void setBlockedActivities_shouldBlockActivityFromLaunching() {
        Context context = getApplicationContext();
        ComponentName emptyActivityComponentName = new ComponentName(context, EmptyActivity.class);
        VirtualDisplay virtualDisplay = createVirtualDisplay(new VirtualDeviceParams.Builder()
                        .setBlockedActivities(Set.of(TestAppHelper.MAIN_ACTIVITY_COMPONENT))
                        .build(),
                /* virtualDisplayFlags= */ 0);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent().setComponent(emptyActivityComponentName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        createActivityOptions(virtualDisplay));

        emptyActivity.startActivity(
                TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver));

        verify(mOnReceiveResultListener, after(3000).never())
                .onReceiveResult(anyInt(), any());
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        Context context = getApplicationContext();
        ComponentName emptyActivityComponentName = new ComponentName(context, EmptyActivity.class);

        VirtualDisplay virtualDisplay = createVirtualDisplay(new VirtualDeviceParams.Builder()
                        .setAllowedCrossTaskNavigations(Set.of(emptyActivityComponentName))
                        .build(),
                /* virtualDisplayFlags= */ 0);

        mActivityListener = mock(ActivityListener.class);
        mVirtualDevice.addActivityListener(context.getMainExecutor(), mActivityListener);

        Intent startIntent = new Intent(context, EmptyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(startIntent, createActivityOptions(virtualDisplay));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        emptyActivity.setCallback(callback);

        emptyActivity.startActivity(
                TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, after(3000).never())
                .onReceiveResult(anyInt(), any());

        verify(mActivityListener, timeout(3000).atLeastOnce()).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()),
                eq(new ComponentName("android", BlockedAppStreamingActivity.class.getName())));

        emptyActivity.finish();
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldAllowNavigations() {
        Context context = getApplicationContext();

        Intent allowedIntent = TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                 .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        VirtualDisplay virtualDisplay = createVirtualDisplay(new VirtualDeviceParams.Builder()
                        .setAllowedCrossTaskNavigations(Set.of(allowedIntent.getComponent()))
                        .build(),
                /* virtualDisplayFlags= */ 0);

        mActivityListener = mock(ActivityListener.class);
        mVirtualDevice.addActivityListener(context.getMainExecutor(), mActivityListener);


        Intent startIntent = new Intent(context, EmptyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        startIntent, createActivityOptions(virtualDisplay));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        emptyActivity.setCallback(callback);

        emptyActivity.startActivity(allowedIntent,
                createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, timeout(3000)).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result ->
                        result.getInt(TestAppHelper.EXTRA_DISPLAY)
                                == virtualDisplay.getDisplay().getDisplayId()));

        verify(mActivityListener, timeout(3000).atLeastOnce()).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()),
                eq(allowedIntent.getComponent()));

        emptyActivity.finish();
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldAllowNavigations() {
        Context context = getApplicationContext();
        ComponentName emptyActivityComponentName = new ComponentName(context, EmptyActivity.class);

        VirtualDisplay virtualDisplay = createVirtualDisplay(new VirtualDeviceParams.Builder()
                        .setBlockedCrossTaskNavigations(Set.of(emptyActivityComponentName))
                        .build(),
                /* virtualDisplayFlags= */ 0);

        mActivityListener = mock(ActivityListener.class);
        mVirtualDevice.addActivityListener(context.getMainExecutor(), mActivityListener);

        Intent startIntent = new Intent(context, EmptyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(startIntent, createActivityOptions(virtualDisplay));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        emptyActivity.setCallback(callback);

        Intent allowedIntent = TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                 .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        emptyActivity.startActivity(
                allowedIntent,
                createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, timeout(3000)).onReceiveResult(
                eq(Activity.RESULT_OK),
                argThat(result ->
                        result.getInt(TestAppHelper.EXTRA_DISPLAY)
                                == virtualDisplay.getDisplay().getDisplayId()));

        verify(mActivityListener, timeout(3000).atLeastOnce()).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()),
                eq(allowedIntent.getComponent()));

        emptyActivity.finish();
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        Context context = getApplicationContext();

        Intent allowedIntent = TestAppHelper.createActivityLaunchedReceiverIntent(mResultReceiver)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        VirtualDisplay virtualDisplay = createVirtualDisplay(new VirtualDeviceParams.Builder()
                        .setBlockedCrossTaskNavigations(Set.of(allowedIntent.getComponent()))
                        .build(),
                /* virtualDisplayFlags= */ 0);

        mActivityListener = mock(ActivityListener.class);
        mVirtualDevice.addActivityListener(context.getMainExecutor(), mActivityListener);

        Intent startIntent = new Intent(context, EmptyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        EmptyActivity emptyActivity = (EmptyActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(startIntent, createActivityOptions(virtualDisplay));

        EmptyActivity.Callback callback = mock(EmptyActivity.Callback.class);
        emptyActivity.setCallback(callback);

        emptyActivity.startActivity(allowedIntent,
                createActivityOptions(virtualDisplay));

        verify(mOnReceiveResultListener, after(3000).never())
                .onReceiveResult(anyInt(), any());

        verify(mActivityListener, timeout(3000).atLeastOnce()).onTopActivityChanged(
                eq(virtualDisplay.getDisplay().getDisplayId()),
                eq(new ComponentName("android", BlockedAppStreamingActivity.class.getName())));

        emptyActivity.finish();
    }

    private VirtualDisplay createVirtualDisplay(@NonNull VirtualDeviceParams virtualDeviceParams,
            int virtualDisplayFlags) {
        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(), virtualDeviceParams);
        ImageReader reader = ImageReader.newInstance(/* width= */ 100, /* height= */ 100,
                PixelFormat.RGBA_8888, /* maxImages= */ 1);
        return mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                reader.getSurface(),
                virtualDisplayFlags,
                Runnable::run,
                mVirtualDisplayCallback);
    }
}

