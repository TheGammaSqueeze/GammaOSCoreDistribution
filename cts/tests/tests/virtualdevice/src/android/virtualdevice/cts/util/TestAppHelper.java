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

package android.virtualdevice.cts.util;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ResultReceiver;
import android.virtualdevice.cts.IStreamedTestApp;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Helper for interacting with {@code android.virtualdevice.streamedtestapp}.
 */
public class TestAppHelper {
    static final String PACKAGE_NAME = "android.virtualdevice.streamedtestapp";
    static final String MAIN_ACTIVITY = "android.virtualdevice.streamedtestapp.MainActivity";
    static final String NO_EMBED_ACTIVITY = "android.virtualdevice.streamedtestapp.NoEmbedActivity";
    static final String CANNOT_DISPLAY_ON_REMOTE_ACTIVITY =
            "android.virtualdevice.streamedtestapp.CannotDisplayOnRemoteActivity";
    static final String STREAMED_APP_SERVICE =
            "android.virtualdevice.streamedtestapp.StreamedAppService";

    /** @see android.virtualdevice.streamedtestapp.MainActivity */
    static final String ACTION_TEST_CAMERA =
            "android.virtualdevice.streamedtestapp.TEST_CAMERA";
    /** @see android.virtualdevice.streamedtestapp.MainActivity */
    public static final String EXTRA_CAMERA_ID = "cameraId";
    /** @see android.virtualdevice.streamedtestapp.MainActivity */
    public static final String EXTRA_CAMERA_RESULT = "cameraResult";
    /** @see android.virtualdevice.streamedtestapp.MainActivity */
    public static final String EXTRA_CAMERA_ON_ERROR_CODE = "cameraOnErrorCode";

    /** @see android.virtualdevice.streamedtestapp.MainActivity */
    static final String ACTION_TEST_CLIPBOARD =
            "android.virtualdevice.streamedtestapp.TEST_CLIPBOARD";
    /** @see android.virtualdevice.streamedtestapp.MainActivity */
    static final String EXTRA_CLIPBOARD_STRING = "clipboardString";

    static final String ACTION_CALL_RESULT_RECEIVER =
            "android.virtualdevice.streamedtestapp.CALL_RESULT_RECEIVER";
    static final String EXTRA_ACTIVITY_LAUNCHED_RECEIVER = "activityLaunchedReceiver";
    public static final String EXTRA_DISPLAY = "display";

    /** @see android.virtualdevice.streamedtestapp.MainActivity */
    public static final String ACTION_CALL_IS_DEVICE_SECURE =
            PACKAGE_NAME + ".ACTION_CALL_IS_DEVICE_SECURE";

    public static final String EXTRA_IS_DEVICE_SECURE = "isDeviceSecure";

    public static final ComponentName MAIN_ACTIVITY_COMPONENT = new ComponentName(
            PACKAGE_NAME, MAIN_ACTIVITY);

    public static Intent createCameraAccessTestIntent() {
        return new Intent(ACTION_TEST_CAMERA)
                .setComponent(MAIN_ACTIVITY_COMPONENT);
    }

    public static Intent createClipboardTestIntent(String clipboardString) {
        return new Intent(ACTION_TEST_CLIPBOARD)
                .setComponent(MAIN_ACTIVITY_COMPONENT)
                .putExtra(EXTRA_CLIPBOARD_STRING, clipboardString);
    }

    public static Intent createNoActionIntent() {
        return new Intent().setComponent(MAIN_ACTIVITY_COMPONENT);
    }

    public static Intent createNoEmbedIntent() {
        return new Intent().setClassName(PACKAGE_NAME, NO_EMBED_ACTIVITY);
    }

    public static Intent createCannotDisplayOnRemoteIntent(boolean newTask,
            ResultReceiver resultReceiver) {
        Intent intent = new Intent(ACTION_CALL_RESULT_RECEIVER)
                .setClassName(PACKAGE_NAME, CANNOT_DISPLAY_ON_REMOTE_ACTIVITY)
                .putExtra(EXTRA_ACTIVITY_LAUNCHED_RECEIVER, resultReceiver);
        if (newTask) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        return intent;
    }

    public static Intent createActivityLaunchedReceiverIntent(ResultReceiver resultReceiver) {
        return new Intent(ACTION_CALL_RESULT_RECEIVER)
                .setComponent(MAIN_ACTIVITY_COMPONENT)
                .putExtra(EXTRA_ACTIVITY_LAUNCHED_RECEIVER, resultReceiver);
    }

    public static Intent createKeyguardManagerIsDeviceSecureTestIntent() {
        return new Intent(ACTION_CALL_IS_DEVICE_SECURE)
                .setComponent(MAIN_ACTIVITY_COMPONENT);
    }

    public static ServiceConnectionFuture<IStreamedTestApp> createTestAppService() {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ServiceConnectionFuture<IStreamedTestApp> connection =
                new ServiceConnectionFuture<IStreamedTestApp>(IStreamedTestApp.Stub::asInterface);
        boolean bindResult = targetContext.bindService(
                new Intent().setClassName(PACKAGE_NAME, STREAMED_APP_SERVICE),
                connection,
                Context.BIND_AUTO_CREATE);
        assertThat(bindResult).isTrue();
        return connection;
    }

    public static class ServiceConnectionFuture<T extends IInterface> implements ServiceConnection {

        private final CompletableFuture<T> mFuture = new CompletableFuture<>();
        private final Function<IBinder, T> mAsInterfaceFunc;

        ServiceConnectionFuture(Function<IBinder, T> asInterfaceFunc) {
            mAsInterfaceFunc = asInterfaceFunc;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mFuture.complete(mAsInterfaceFunc.apply(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // If the future is already completed, then it will stay completed with the old value.
            mFuture.completeExceptionally(new Exception("Service disconnected"));
        }

        public CompletableFuture<T> getFuture() {
            return mFuture;
        }
    }
}
