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

package android.voicerecognition.cts;

import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_END_SEGMENTED_SESSION;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_ERROR;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_RESULTS;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_SEGMENTS_RESULTS;
import static android.voicerecognition.cts.CallbackMethod.CALLBACK_METHOD_UNSPECIFIED;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_CANCEL;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_DESTROY;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_START_LISTENING;
import static android.voicerecognition.cts.RecognizerMethod.RECOGNIZER_METHOD_STOP_LISTENING;
import static android.voicerecognition.cts.TestObjects.START_LISTENING_INTENT;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.Intent;
import android.os.SystemClock;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.PollingCheck;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Abstract implementation for {@link android.speech.SpeechRecognizer} CTS tests. */
abstract class AbstractRecognitionServiceTest {
    private static final String TAG = AbstractRecognitionServiceTest.class.getSimpleName();

    private static final long INDICATOR_DISMISS_TIMEOUT = 5000L;
    private static final long WAIT_TIMEOUT_MS = 30000L; // 30 secs
    private static final long SEQUENCE_TEST_WAIT_TIMEOUT_MS = 5000L;

    private static final String CTS_VOICE_RECOGNITION_SERVICE =
            "android.recognitionservice.service/android.recognitionservice.service"
                    + ".CtsVoiceRecognitionService";

    private static final String IN_PACKAGE_RECOGNITION_SERVICE =
            "android.voicerecognition.cts/android.voicerecognition.cts.CtsRecognitionService";

    @Rule
    public ActivityTestRule<SpeechRecognitionActivity> mActivityTestRule =
            new ActivityTestRule<>(SpeechRecognitionActivity.class);

    private UiDevice mUiDevice;
    private SpeechRecognitionActivity mActivity;

    abstract void setCurrentRecognizer(SpeechRecognizer recognizer, String component);

    abstract boolean isOnDeviceTest();

    @Nullable
    abstract String customRecognizer();

    @Before
    public void setup() {
        prepareDevice();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mActivity = mActivityTestRule.getActivity();
        mActivity.init(isOnDeviceTest(), customRecognizer());
    }

    @Test
    public void testStartListening() throws Throwable {
        setCurrentRecognizer(mActivity.mRecognizer, CTS_VOICE_RECOGNITION_SERVICE);
        mUiDevice.waitForIdle();

        mActivity.startListening();
        try {
            // startListening() will call noteProxyOpNoTrow(), if the permission check pass then the
            // RecognitionService.onStartListening() will be called. Otherwise, a TimeoutException
            // will be thrown.
            assertThat(mActivity.mCountDownLatch.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            assertWithMessage("onStartListening() not called. " + e).fail();
        }
        // Wait for the privacy indicator to disappear to avoid the test becoming flaky.
        SystemClock.sleep(INDICATOR_DISMISS_TIMEOUT);
    }

    @Test
    public void testCanCheckForSupport() throws Throwable {
        mUiDevice.waitForIdle();
        assertThat(mActivity.mRecognizer).isNotNull();
        setCurrentRecognizer(mActivity.mRecognizer, IN_PACKAGE_RECOGNITION_SERVICE);
        mUiDevice.waitForIdle();

        List<RecognitionSupport> supportResults = new ArrayList<>();
        List<Integer> errors = new ArrayList<>();
        RecognitionSupportCallback supportCallback = new RecognitionSupportCallback() {
            @Override
            public void onSupportResult(@NonNull RecognitionSupport recognitionSupport) {
                supportResults.add(recognitionSupport);
            }

            @Override
            public void onError(int error) {
                errors.add(error);
            }
        };
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mActivity.checkRecognitionSupport(intent, supportCallback);
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> supportResults.size() + errors.size() > 0);
        assertThat(supportResults).isEmpty();
        assertThat(errors).containsExactly(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT);

        errors.clear();
        RecognitionSupport rs = new RecognitionSupport.Builder()
                .setInstalledOnDeviceLanguages(new ArrayList<>(List.of("es")))
                .addInstalledOnDeviceLanguage("en")
                .setPendingOnDeviceLanguages(new ArrayList<>(List.of("ru")))
                .addPendingOnDeviceLanguage("jp")
                .setSupportedOnDeviceLanguages(new ArrayList<>(List.of("pt")))
                .addSupportedOnDeviceLanguage("de")
                .setOnlineLanguages(new ArrayList<>(List.of("zh")))
                .addOnlineLanguage("fr")
                .build();
        CtsRecognitionService.sConsumerQueue.add(c -> c.onSupportResult(rs));

        mActivity.checkRecognitionSupport(intent, supportCallback);
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> supportResults.size() + errors.size() > 0);
        assertThat(errors).isEmpty();
        assertThat(supportResults).containsExactly(rs);
        assertThat(rs.getInstalledOnDeviceLanguages())
                .isEqualTo(List.of("es", "en"));
        assertThat(rs.getPendingOnDeviceLanguages())
                .isEqualTo(List.of("ru", "jp"));
        assertThat(rs.getSupportedOnDeviceLanguages())
                .isEqualTo(List.of("pt", "de"));
        assertThat(rs.getOnlineLanguages())
                .isEqualTo(List.of("zh", "fr"));
    }

    @Test
    public void testCanTriggerModelDownload() throws Throwable {
        mUiDevice.waitForIdle();
        assertThat(mActivity.mRecognizer).isNotNull();
        setCurrentRecognizer(mActivity.mRecognizer, IN_PACKAGE_RECOGNITION_SERVICE);
        mUiDevice.waitForIdle();

        CtsRecognitionService.sDownloadTriggers.clear();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mActivity.triggerModelDownload(intent);
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> CtsRecognitionService.sDownloadTriggers.size() > 0);
        assertThat(CtsRecognitionService.sDownloadTriggers).hasSize(1);
    }

    @Test
    public void sequenceTest_startListening_stopListening_results() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_STOP_LISTENING),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_UNSPECIFIED,
                        CALLBACK_METHOD_RESULTS),
                /* expected service methods propagated: */ ImmutableList.of(true, true),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_RESULTS)
                );
    }

    /** Tests that stopListening() is ignored after results(). */
    @Test
    public void sequenceTest_startListening_results_stopListening() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_STOP_LISTENING),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_RESULTS),
                /* expected service methods propagated: */ ImmutableList.of(true, false),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_RESULTS,
                        CALLBACK_METHOD_ERROR)
                );
    }

    /** Tests that cancel() is ignored after results(). */
    @Test
    public void sequenceTest_startListening_results_cancel() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_CANCEL),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_RESULTS),
                /* expected service methods propagated: */ ImmutableList.of(true, false),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_RESULTS)
        );
    }

    /** Tests that we can kick off execution again after results(). */
    @Test
    public void sequenceTest_startListening_results_startListening_results() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_START_LISTENING),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_RESULTS,
                        CALLBACK_METHOD_RESULTS),
                /* expected service methods propagated: */ ImmutableList.of(true, true),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_RESULTS,
                        CALLBACK_METHOD_RESULTS)
        );
    }

    @Test
    public void setSequenceTest_startListening_segment_endofsession() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_STOP_LISTENING
                        ),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_SEGMENTS_RESULTS,
                        CALLBACK_METHOD_END_SEGMENTED_SESSION
                        ),
                /* expected service methods propagated: */ ImmutableList.of(true, true, true),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_SEGMENTS_RESULTS,
                        CALLBACK_METHOD_END_SEGMENTED_SESSION
                )
        );
    }

    @Test
    public void sequenceTest_startListening_cancel() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_CANCEL),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_UNSPECIFIED,
                        CALLBACK_METHOD_UNSPECIFIED),
                /* expected service methods propagated: */ ImmutableList.of(true, true),
                /* expected callback methods invoked: */ ImmutableList.of()
        );
    }

    @Test
    public void sequenceTest_startListening_startListening() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_START_LISTENING),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_UNSPECIFIED),
                /* expected service methods propagated: */ ImmutableList.of(true, false),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_ERROR)
        );
    }

    @Test
    public void sequenceTest_startListening_stopListening_cancel() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_STOP_LISTENING,
                        RECOGNIZER_METHOD_CANCEL),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_UNSPECIFIED,
                        CALLBACK_METHOD_UNSPECIFIED,
                        CALLBACK_METHOD_UNSPECIFIED),
                /* expected service methods propagated: */ ImmutableList.of(true, true, true),
                /* expected callback methods invoked: */ ImmutableList.of()
        );
    }

    @Test
    public void sequenceTest_startListening_error_cancel() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_CANCEL),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_ERROR),
                /* expected service methods propagated: */ ImmutableList.of(true, false),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_ERROR)
        );
    }

    @Test
    public void sequenceTest_startListening_stopListening_destroy() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_STOP_LISTENING,
                        RECOGNIZER_METHOD_DESTROY),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_UNSPECIFIED,
                        CALLBACK_METHOD_UNSPECIFIED,
                        CALLBACK_METHOD_UNSPECIFIED),
                /* expected service methods propagated: */ ImmutableList.of(true, true, true),
                /* expected callback methods invoked: */ ImmutableList.of()
        );
    }

    @Test
    public void sequenceTest_startListening_error_destroy() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_DESTROY),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_ERROR),
                /* expected service methods propagated: */ ImmutableList.of(true, false),
                /* expected callback methods invoked: */ ImmutableList.of(
                        CALLBACK_METHOD_ERROR)
        );
    }

    @Test
    public void sequenceTest_startListening_destroy_destroy() {
        executeSequenceTest(
                /* service methods to call: */ ImmutableList.of(
                        RECOGNIZER_METHOD_START_LISTENING,
                        RECOGNIZER_METHOD_DESTROY,
                        RECOGNIZER_METHOD_DESTROY),
                /* callback methods to call: */ ImmutableList.of(
                        CALLBACK_METHOD_UNSPECIFIED,
                        CALLBACK_METHOD_UNSPECIFIED),
                /* expected service methods propagated: */ ImmutableList.of(true, true, false),
                /* expected callback methods invoked: */ ImmutableList.of()
        );
    }

    private void executeSequenceTest(
            List<RecognizerMethod> recognizerMethodsToCall,
            List<CallbackMethod> callbackMethodInstructions,
            List<Boolean> expectedRecognizerServiceMethodsToPropagate,
            List<CallbackMethod> expectedClientCallbackMethods) {
        mUiDevice.waitForIdle();
        SpeechRecognizer speechRecognizer = mActivity.mRecognizer;
        assertThat(speechRecognizer).isNotNull();
        setCurrentRecognizer(speechRecognizer, IN_PACKAGE_RECOGNITION_SERVICE);

        mActivity.mCallbackMethodsInvoked.clear();
        CtsRecognitionService.sInvokedRecognizerMethods.clear();
        CtsRecognitionService.sInstructedCallbackMethods.clear();
        CtsRecognitionService.sInstructedCallbackMethods.addAll(callbackMethodInstructions);

        List<RecognizerMethod> expectedServiceMethods = new ArrayList<>();

        for (int i = 0; i < recognizerMethodsToCall.size(); i++) {
            RecognizerMethod recognizerMethod = recognizerMethodsToCall.get(i);
            Log.i(TAG, "Sending service method " + recognizerMethod.name());

            switch (recognizerMethod) {
                case RECOGNIZER_METHOD_UNSPECIFIED:
                    fail();
                    break;
                case RECOGNIZER_METHOD_START_LISTENING:
                    mActivity.startListening(START_LISTENING_INTENT);
                    break;
                case RECOGNIZER_METHOD_STOP_LISTENING:
                    mActivity.stopListening();
                    break;
                case RECOGNIZER_METHOD_CANCEL:
                    mActivity.cancel();
                    break;
                case RECOGNIZER_METHOD_DESTROY:
                    mActivity.destroyRecognizer();
                    break;
                default:
                    fail();
            }

            if (expectedRecognizerServiceMethodsToPropagate.get(i)) {
                expectedServiceMethods.add(
                        RECOGNIZER_METHOD_DESTROY != recognizerMethod
                                ? recognizerMethod
                                : RECOGNIZER_METHOD_CANCEL);
                PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                        () -> CtsRecognitionService.sInvokedRecognizerMethods.size()
                                == expectedServiceMethods.size());
            }
        }

        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> CtsRecognitionService.sInstructedCallbackMethods.isEmpty());
        PollingCheck.waitFor(SEQUENCE_TEST_WAIT_TIMEOUT_MS,
                () -> mActivity.mCallbackMethodsInvoked.size()
                        >= expectedClientCallbackMethods.size());

        assertThat(CtsRecognitionService.sInvokedRecognizerMethods).isEqualTo(expectedServiceMethods);
        assertThat(mActivity.mCallbackMethodsInvoked).isEqualTo(expectedClientCallbackMethods);
        assertThat(CtsRecognitionService.sInstructedCallbackMethods).isEmpty();
    }

    private static void prepareDevice() {
        // Unlock screen.
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        // Dismiss keyguard, in case it's set as "Swipe to unlock".
        runShellCommand("wm dismiss-keyguard");
    }
}
