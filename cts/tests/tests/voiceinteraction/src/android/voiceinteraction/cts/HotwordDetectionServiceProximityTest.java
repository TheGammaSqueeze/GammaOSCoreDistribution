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

import static android.voiceinteraction.cts.testcore.VoiceInteractionDetectionHelper.performAndGetDetectionResult;
import static android.voiceinteraction.cts.testcore.VoiceInteractionDetectionHelper.testHotwordDetection;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.attentionservice.cts.CtsTestAttentionService;
import android.os.Parcelable;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.service.voice.HotwordDetectedResult;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.service.EventPayloadParcelable;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for using the Attention Service inside VoiceInteractionService using
 * a basic HotwordDetectionService.
 */
@ApiTest(apis = {"android.service.voice.HotwordDetectedResult#getExtras"})
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public final class HotwordDetectionServiceProximityTest
        extends AbstractVoiceInteractionBasicTestCase {
    private static final String ATTENTION_SERVICE = "attention";

    @ClassRule
    public static final RequiredServiceRule ATTENTION_SERVICE_RULE =
            new RequiredServiceRule(ATTENTION_SERVICE);

    @Rule
    public final DeviceConfigStateChangerRule mEnableAttentionManagerServiceRule =
            new DeviceConfigStateChangerRule(sInstrumentation.getTargetContext(),
                    DeviceConfig.NAMESPACE_ATTENTION_MANAGER_SERVICE,
                    SERVICE_ENABLED,
                    "true");

    private static final String EXTRA_PROXIMITY =
            "android.service.voice.extra.PROXIMITY";

    private static Instrumentation sInstrumentation = InstrumentationRegistry.getInstrumentation();

    private static final String SERVICE_ENABLED = "service_enabled";
    private static final String FAKE_SERVICE_PACKAGE =
            HotwordDetectionServiceProximityTest.class.getPackage().getName();
    private static final double PROXIMITY_NEAR_METERS = 2.0;
    private static final double PROXIMITY_FAR_METERS = 6.0;
    private static final int PROXIMITY_UNKNOWN = -1;
    private static final int PROXIMITY_NEAR = 1;
    private static final int PROXIMITY_FAR = 2;
    private static final boolean ENABLE_PROXIMITY_RESULT = true;

    @BeforeClass
    public static void enableAttentionService() throws InterruptedException {
        CtsTestAttentionService.reset();
        assertThat(setTestableAttentionService(FAKE_SERVICE_PACKAGE)).isTrue();
        assertThat(getAttentionServiceComponent()).contains(FAKE_SERVICE_PACKAGE);
        runShellCommand("cmd attention call checkAttention");
    }

    @AfterClass
    public static void clearAttentionService() {
        runShellCommand("cmd attention clearTestableAttentionService");
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromDsp() {
        // Create AlwaysOnHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

        // by default, proximity should not be returned.
        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                null);

        // when proximity is unknown, proximity should not be returned.
        CtsTestAttentionService.respondProximity(PROXIMITY_UNKNOWN);

        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                null);

        CtsTestAttentionService.respondProximity(PROXIMITY_NEAR_METERS);

        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                PROXIMITY_NEAR);

        CtsTestAttentionService.respondProximity(PROXIMITY_FAR_METERS);

        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                PROXIMITY_FAR);

        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_DSP_DESTROY_DETECTOR,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromMic_noUpdates() {
        // Create SoftwareHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_FROM_SOFTWARE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_MIC_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                null);

        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_DESTROY_DETECTOR,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromMic_unknownProximity() {
        // Create SoftwareHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_FROM_SOFTWARE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

        CtsTestAttentionService.respondProximity(PROXIMITY_UNKNOWN);

        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_MIC_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                null);

        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_DESTROY_DETECTOR,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromMic_updatedProximity() {
        // Create SoftwareHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_FROM_SOFTWARE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

        CtsTestAttentionService.respondProximity(PROXIMITY_NEAR_METERS);

        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_MIC_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                PROXIMITY_NEAR);

        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_DESTROY_DETECTOR,
                Utils.HOTWORD_DETECTION_SERVICE_SOFTWARE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);
    }

    @Test
    @RequiresDevice
    public void testAttentionService_onDetectFromExternalSource_doesNotReceiveProximity() {
        // Create SoftwareHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

        CtsTestAttentionService.respondProximity(PROXIMITY_FAR);

        verifyProximityBundle(
                performAndGetDetectionResult(mActivityTestRule, mContext,
                        Utils.HOTWORD_DETECTION_SERVICE_EXTERNAL_SOURCE_ONDETECT_TEST,
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC),
                null);

        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_DSP_DESTROY_DETECTOR,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);
    }

    // simply check that the proximity values are equal.
    private void verifyProximityBundle(Parcelable result, Integer expected) {
        assertThat(result).isInstanceOf(EventPayloadParcelable.class);
        HotwordDetectedResult hotwordDetectedResult =
                ((EventPayloadParcelable) result).mHotwordDetectedResult;
        assertThat(hotwordDetectedResult).isNotNull();
        if (expected == null || !ENABLE_PROXIMITY_RESULT) {
            assertThat(hotwordDetectedResult.getExtras().containsKey(EXTRA_PROXIMITY)).isFalse();
        } else {
            assertThat(hotwordDetectedResult.getExtras().containsKey(EXTRA_PROXIMITY)).isTrue();
            assertThat(hotwordDetectedResult.getExtras().getInt(EXTRA_PROXIMITY))
                    .isEqualTo(expected);
        }
    }

    private static String getAttentionServiceComponent() {
        return runShellCommand("cmd attention getAttentionServiceComponent");
    }

    private static boolean setTestableAttentionService(String service) {
        return runShellCommand("cmd attention setTestableAttentionService " + service)
                .equals("true");
    }

    @Override
    public String getVoiceInteractionService() {
        return "android.voiceinteraction.cts/"
                + "android.voiceinteraction.service.BasicVoiceInteractionService";
    }
}

