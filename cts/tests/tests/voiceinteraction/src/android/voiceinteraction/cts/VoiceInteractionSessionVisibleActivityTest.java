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

package android.voiceinteraction.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertThrows;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.activities.EmptyActivity;
import android.voiceinteraction.cts.testcore.VoiceInteractionSessionControl;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.Executor;

/**
 * Tests for reliable visible activity lookup related functions.
 */
@AppModeFull(reason = "DirectActionsTest is enough")
public class VoiceInteractionSessionVisibleActivityTest extends AbstractVoiceInteractionTestCase {
    private static final String TAG =
            VoiceInteractionSessionVisibleActivityTest.class.getSimpleName();

    private final @NonNull SessionControl mSessionControl = new SessionControl();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ActivityScenario<EmptyActivity> mActivityScenario;

    @After
    public void cleanup() throws Exception {
        if (mActivityScenario != null) {
            mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
            mActivityScenario = null;
        }
    }

    @Test
    public void testVoiceInteractionSession_registerVisibleActivityCallback_beforeOnCreate()
            throws Throwable {
        assertThrows(IllegalStateException.class,
                () -> new VoiceInteractionSession(mContext,
                        mHandler).registerVisibleActivityCallback(mock(Executor.class),
                        mock(VoiceInteractionSession.VisibleActivityCallback.class)));
    }

    @Test
    public void testVoiceInteractionSession_registerVisibleActivityCallback_withoutExecutor()
            throws Throwable {
        // Start a VoiceInteractionSession and make sure the session has been created.
        mSessionControl.startVoiceInteractionSession();

        try {
            // Register the VisibleActivityCallback with null executor, it will cause
            // NullPointerException.
            final Bundle result = mSessionControl.registerVisibleActivityCallback(
                    Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_EXECUTOR);

            // Verify if getting the NullPointerException.
            assertThat(result).isNotNull();
            assertThat(
                    result.getSerializable(Utils.VISIBLE_ACTIVITY_KEY_RESULT).getClass()).isEqualTo(
                    NullPointerException.class);
        } finally {
            mSessionControl.unregisterVisibleActivityCallback();
            mSessionControl.stopVoiceInteractionSession();
        }
    }

    @Test
    public void testVoiceInteractionSession_registerVisibleActivityCallback_withoutCallback()
            throws Throwable {
        // Start a VoiceInteractionSession and make sure the session has been created.
        mSessionControl.startVoiceInteractionSession();

        try {
            // Register the VisibleActivityCallback with null callback, it will cause
            // NullPointerException.
            final Bundle result = mSessionControl.registerVisibleActivityCallback(
                    Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_WITHOUT_CALLBACK);

            // Verify if getting the NullPointerException.
            assertThat(result).isNotNull();
            assertThat(
                    result.getSerializable(Utils.VISIBLE_ACTIVITY_KEY_RESULT).getClass()).isEqualTo(
                    NullPointerException.class);
        } finally {
            mSessionControl.unregisterVisibleActivityCallback();
            mSessionControl.stopVoiceInteractionSession();
        }
    }

    @Test
    public void testVoiceInteractionSession_unregisterVisibleActivityCallback_withoutCallback()
            throws Throwable {
        assertThrows(NullPointerException.class,
                () -> new VoiceInteractionSession(mContext,
                        mHandler).unregisterVisibleActivityCallback(/* callback= */ null));
    }

    @Test
    public void testReceiveVisibleActivityCallbackAfterStartNewOrFinishActivity() throws Exception {
        // Start a VoiceInteractionSession and make sure the session has been created.
        mSessionControl.startVoiceInteractionSession();

        try {
            // Register the VisibleActivityCallback first, the VisibleActivityCallback.onVisible
            // or VisibleActivityCallback.onInvisible that will be called when visible activities
            // have been changed.
            final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(mContext,
                    Utils.VISIBLE_ACTIVITY_CALLBACK_ONVISIBLE_INTENT);
            receiver.register();

            // Register the VisibleActivityCallback and the VisibleActivityCallback.onVisible will
            // be called immediately with current visible activities.
            mSessionControl.registerVisibleActivityCallback(
                    Utils.VISIBLE_ACTIVITY_CALLBACK_REGISTER_NORMAL);

            // Verify if the VisibleActivityCallback.onVisible has been called.
            Intent intent = receiver.awaitForBroadcast(Utils.OPERATION_TIMEOUT_MS);
            receiver.unregisterQuietly();

            assertThat(intent).isNotNull();
            assertThat(intent.getBooleanExtra(Utils.VISIBLE_ACTIVITY_KEY_RESULT, false)).isTrue();

            // After starting a new activity, the VisibleActivityCallback.onVisible and
            // VisibleActivityCallback.onInvisible should be called due to visible activities have
            // been changed.
            performActivityChangeAndVerifyCallback(Utils.ACTIVITY_NEW);

            // After finishing an activity, the VisibleActivityCallback.onVisible and
            // VisibleActivityCallback.onInvisible should be called due to visible activities have
            // been changed.
            performActivityChangeAndVerifyCallback(Utils.ACTIVITY_FINISH);

        } finally {
            mSessionControl.unregisterVisibleActivityCallback();
            mSessionControl.stopVoiceInteractionSession();
        }
    }

    private void performActivityChangeAndVerifyCallback(int activityChange) throws Exception {
        // Sleep one second to reduce the impact of changing activity state.
        Thread.sleep(1000);

        final BlockingBroadcastReceiver onVisibleReceiver = new BlockingBroadcastReceiver(
                mContext, Utils.VISIBLE_ACTIVITY_CALLBACK_ONVISIBLE_INTENT);
        onVisibleReceiver.register();

        final BlockingBroadcastReceiver onInvisibleReceiver = new BlockingBroadcastReceiver(
                mContext, Utils.VISIBLE_ACTIVITY_CALLBACK_ONINVISIBLE_INTENT);
        onInvisibleReceiver.register();

        Log.v(TAG, "performActivityChange : " + activityChange);
        switch (activityChange) {
            case Utils.ACTIVITY_NEW:
                // Start a new activity
                final Intent intentActivity = new Intent(mContext, EmptyActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mActivityScenario = ActivityScenario.launch(intentActivity);
                mActivityScenario.moveToState(Lifecycle.State.RESUMED);
                break;
            case Utils.ACTIVITY_FINISH:
                // Finish an activity
                mActivityScenario.moveToState(Lifecycle.State.DESTROYED);
                mActivityScenario = null;
                break;
        }

        // Verify if the VisibleActivityCallback.onVisible is called
        final Intent onVisibleIntent = onVisibleReceiver.awaitForBroadcast(
                Utils.OPERATION_TIMEOUT_MS);
        onVisibleReceiver.unregisterQuietly();

        assertThat(onVisibleIntent).isNotNull();
        assertThat(
                onVisibleIntent.getBooleanExtra(Utils.VISIBLE_ACTIVITY_KEY_RESULT, false)).isTrue();

        // Verify if the VisibleActivityCallback.onInvisible is called
        final Intent onInvisibleIntent = onInvisibleReceiver.awaitForBroadcast(
                Utils.OPERATION_TIMEOUT_MS);
        onInvisibleReceiver.unregisterQuietly();

        assertThat(onInvisibleIntent).isNotNull();
        assertThat(onInvisibleIntent.getBooleanExtra(Utils.VISIBLE_ACTIVITY_KEY_RESULT,
                false)).isTrue();
    }

    private final class SessionControl extends VoiceInteractionSessionControl {

        SessionControl() {
            super(mContext);
        }

        private void startVoiceInteractionSession() throws Exception {
            final Intent intent = new Intent();
            intent.putExtra(Utils.VOICE_INTERACTION_KEY_CLASS,
                    "android.voiceinteraction.service.DirectActionsSession");
            intent.setClassName("android.voiceinteraction.service",
                    "android.voiceinteraction.service.VoiceInteractionMain");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startVoiceInteractionSession(intent);
        }

        Bundle registerVisibleActivityCallback(int callbackParameter) throws Exception {
            final Bundle arguments = new Bundle();
            arguments.putInt(Utils.VISIBLE_ACTIVITY_CMD_REGISTER_CALLBACK, callbackParameter);
            final Bundle result = executeCommand(
                    Utils.VISIBLE_ACTIVITY_CMD_REGISTER_CALLBACK, null /*directAction*/,
                    arguments, null /*postActionCommand*/);
            return result;
        }

        boolean unregisterVisibleActivityCallback() throws Exception {
            final Bundle result = executeCommand(
                    Utils.VISIBLE_ACTIVITY_CMD_UNREGISTER_CALLBACK, null /*directAction*/,
                    null /*arguments*/, null /*postActionCommand*/);
            return result.getBoolean(Utils.VISIBLE_ACTIVITY_KEY_RESULT);
        }
    }
}
