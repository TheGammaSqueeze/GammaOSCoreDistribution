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

package android.voiceinteraction.cts.testcore;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.TestVoiceInteractionServiceActivity;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

/**
 * A helper class to perform an intent to start the Voice Interaction Service and receive Hotword
 * Detection Result.
 */
public class VoiceInteractionDetectionHelper {
    private static final int TEST_RESULT_AWAIT_TIMEOUT_MS = 10 * 1000;

    public static void perform(
            ActivityScenarioRule<TestVoiceInteractionServiceActivity> activityTestRule,
            int testType, int serviceType) {
        activityTestRule.getScenario().onActivity(
                activity -> activity.triggerHotwordDetectionServiceTest(
                        serviceType, testType));
    }

    public static void testHotwordDetection(
            ActivityScenarioRule<TestVoiceInteractionServiceActivity> activityTestRule,
            Context context, int testType, String expectedIntent, int expectedResult,
            int serviceType) {
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(context,
                expectedIntent);
        receiver.register();
        perform(activityTestRule, testType, serviceType);
        final Intent intent = receiver.awaitForBroadcast(TEST_RESULT_AWAIT_TIMEOUT_MS);
        receiver.unregisterQuietly();

        assertThat(intent).isNotNull();
        assertThat(intent.getIntExtra(Utils.KEY_TEST_RESULT, -1)).isEqualTo(expectedResult);
    }

    @NonNull
    public static Parcelable performAndGetDetectionResult(
            ActivityScenarioRule<TestVoiceInteractionServiceActivity> activityTestRule,
            Context context, int testType, int serviceType) {
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(context,
                Utils.HOTWORD_DETECTION_SERVICE_ONDETECT_RESULT_INTENT);
        receiver.register();
        perform(activityTestRule, testType, serviceType);
        final Intent intent = receiver.awaitForBroadcast(TEST_RESULT_AWAIT_TIMEOUT_MS);
        receiver.unregisterQuietly();

        assertThat(intent).isNotNull();
        final Parcelable result = intent.getParcelableExtra(Utils.KEY_TEST_RESULT);
        assertThat(result).isNotNull();
        return result;
    }
}
