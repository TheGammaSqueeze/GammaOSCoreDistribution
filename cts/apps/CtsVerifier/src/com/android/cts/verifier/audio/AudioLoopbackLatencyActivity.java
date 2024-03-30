/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.content.res.Resources;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.AudioUtils;
import com.android.cts.verifier.audio.audiolib.StatUtils;

/**
 * CtsVerifier Audio Loopback Latency Test
 */
@CddTest(requirement = "5.10/C-1-2,C-1-5")
public class AudioLoopbackLatencyActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioLoopbackLatencyActivity";

    // JNI load
    static {
        try {
            System.loadLibrary("audioloopback_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading Audio Loopback JNI library");
            Log.e(TAG, "e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }
    protected AudioManager mAudioManager;

    // UI
    TextView[] mResultsText = new TextView[NUM_TEST_ROUTES];

    TextView mAudioLevelText;
    SeekBar mAudioLevelSeekbar;

    TextView mTestStatusText;
    ProgressBar mProgressBar;
    int mMaxLevel;

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();
    private Button[] mStartButtons = new Button[NUM_TEST_ROUTES];

    String mYesString;
    String mNoString;

    String mPassString;
    String mFailString;
    String mNotTestedString;
    String mNotRequiredString;
    String mRequiredString;

    // These flags determine the maximum allowed latency
    private boolean mClaimsProAudio;
    private boolean mClaimsLowLatency;
    private boolean mClaimsMediaPerformance;
    private boolean mClaimsOutput;
    private boolean mClaimsInput;

    // Useful info
    private boolean mSupportsMMAP = AudioUtils.isMMapSupported();
    private boolean mSupportsMMAPExclusive = AudioUtils.isMMapExclusiveSupported();

    // Peripheral(s)
    private static final int NUM_TEST_ROUTES =       3;
    private static final int TESTROUTE_DEVICE =      0; // device speaker + mic
    private static final int TESTROUTE_ANALOG_JACK = 1;
    private static final int TESTROUTE_USB =         2;
    private int mTestRoute = TESTROUTE_DEVICE;

    // Loopback Logic
    private NativeAnalyzerThread mNativeAnalyzerThread = null;

    protected static final int NUM_TEST_PHASES = 5;
    protected int mTestPhase = 0;

    private static final double CONFIDENCE_THRESHOLD_AMBIENT = 0.6;
    private static final double CONFIDENCE_THRESHOLD_WIRED = 0.6;

    public static final double LATENCY_NOT_MEASURED = 0.0;
    public static final double LATENCY_BASIC = 500.0;
    public static final double LATENCY_PRO_AUDIO_AT_LEAST_ONE = 25.0;
    public static final double LATENCY_PRO_AUDIO_ANALOG = 20.0;
    public static final double LATENCY_PRO_AUDIO_USB = 25.0;
    public static final double LATENCY_MPC_AT_LEAST_ONE = 80.0;

    // The audio stream callback threads should stop and close
    // in less than a few hundred msec. This is a generous timeout value.
    private static final int STOP_TEST_TIMEOUT_MSEC = 2 * 1000;

    private TestSpec[] mTestSpecs = new TestSpec[NUM_TEST_ROUTES];
    class TestSpec {
        private static final String TAG = "AudioLoopbackLatencyActivity.TestSpec";
        // impossibly low latencies (indicating something in the test went wrong).
        protected static final double LOWEST_REASONABLE_LATENCY_MILLIS = 1.0;

        final int mRouteId;

        // runtime assigned device ID
        static final int DEVICEID_NONE = -1;
        int mInputDeviceId;
        int mOutputDeviceId;

        String mDeviceName;

        double[] mLatencyMS = new double[NUM_TEST_PHASES];
        double[] mConfidence = new double[NUM_TEST_PHASES];

        double mMeanLatencyMS;
        double mMeanAbsoluteDeviation;
        double mMeanConfidence;
        double mRequiredConfidence;

        boolean mRouteAvailable; // Have we seen this route/device at any time
        boolean mRouteConnected; // is the route available NOW
        boolean mTestRun;

        TestSpec(int routeId, double requiredConfidence) {
            mRouteId = routeId;
            mRequiredConfidence = requiredConfidence;

            mInputDeviceId = DEVICEID_NONE;
            mOutputDeviceId = DEVICEID_NONE;
        }

        void startTest() {
            mTestRun = true;

            java.util.Arrays.fill(mLatencyMS, 0.0);
            java.util.Arrays.fill(mConfidence, 0.0);
        }

        void recordPhase(int phase, double latencyMS, double confidence) {
            mLatencyMS[phase] = latencyMS;
            mConfidence[phase] = confidence;
        }

        void handleTestCompletion() {
            mMeanLatencyMS = StatUtils.calculateMean(mLatencyMS);
            mMeanAbsoluteDeviation =
                    StatUtils.calculateMeanAbsoluteDeviation(
                            mMeanLatencyMS, mLatencyMS, mLatencyMS.length);
            mMeanConfidence = StatUtils.calculateMean(mConfidence);
        }

        boolean isMeasurementValid() {
            return mTestRun && mMeanLatencyMS > 1.0 && mMeanConfidence >= mRequiredConfidence;
        }

        String getResultString() {
            String result;

            if (!mRouteAvailable) {
                result = "Route Not Available";
            } else if (!mTestRun) {
                result = "Test Not Run";
            } else if (mMeanConfidence < mRequiredConfidence) {
                result = String.format(
                        "Test Finished\nInsufficient Confidence (%.2f < %.2f). No Results.",
                        mMeanConfidence, mRequiredConfidence);
            } else if (mMeanLatencyMS <= LOWEST_REASONABLE_LATENCY_MILLIS) {
                result = String.format(
                        "Test Finished\nLatency unrealistically low (%.2f < %.2f). No Results.",
                        mMeanLatencyMS, LOWEST_REASONABLE_LATENCY_MILLIS);
            } else {
                result = String.format(
                        "Test Finished\nMean Latency:%.2f ms\n"
                                + "Mean Absolute Deviation: %.2f\n"
                                + "Confidence: %.2f\n"
                                + "Low Latency Path: %s",
                        mMeanLatencyMS,
                        mMeanAbsoluteDeviation,
                        mMeanConfidence,
                        mNativeAnalyzerThread.isLowLatencyStream() ? mYesString : mNoString);
            }

            return result;
        }

        // ReportLog Schema (per route)
        private static final String KEY_ROUTEINDEX = "route_index";
        private static final String KEY_LATENCY = "latency";
        private static final String KEY_CONFIDENCE = "confidence";
        private static final String KEY_MEANABSDEVIATION = "mean_absolute_deviation";
        private static final String KEY_IS_PERIPHERAL_ATTACHED = "is_peripheral_attached";
        private static final String KEY_INPUT_PERIPHERAL_NAME = "input_peripheral";
        private static final String KEY_OUTPUT_PERIPHERAL_NAME = "output_peripheral";
        private static final String KEY_TEST_PERIPHERAL = "test_peripheral";

        void recordTestResults(CtsVerifierReportLog reportLog) {
            reportLog.addValue(
                    KEY_ROUTEINDEX,
                    mRouteId,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_LATENCY,
                    mMeanLatencyMS,
                    ResultType.LOWER_BETTER,
                    ResultUnit.MS);

            reportLog.addValue(
                    KEY_CONFIDENCE,
                    mMeanConfidence,
                    ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_MEANABSDEVIATION,
                    mMeanAbsoluteDeviation,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_TEST_PERIPHERAL,
                    mDeviceName,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.audio_loopback_latency_activity);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_loopback_latency_test, R.string.audio_loopback_info, -1);

        mRequireReportLogToPass = true;

        mClaimsOutput = AudioSystemFlags.claimsOutput(this);
        mClaimsInput = AudioSystemFlags.claimsInput(this);
        mClaimsProAudio = AudioSystemFlags.claimsProAudio(this);
        mClaimsLowLatency = AudioSystemFlags.claimsLowLatencyAudio(this);
        mClaimsMediaPerformance = Build.VERSION.MEDIA_PERFORMANCE_CLASS != 0;

        // Setup test specifications
        double mustLatency;

        // Speaker/Mic Path
        mTestSpecs[TESTROUTE_DEVICE] =
                new TestSpec(TESTROUTE_DEVICE, CONFIDENCE_THRESHOLD_AMBIENT);
        mTestSpecs[TESTROUTE_DEVICE].mRouteAvailable = true;    // Always

        // Analog Jack Path
        mTestSpecs[TESTROUTE_ANALOG_JACK] =
                new TestSpec(TESTROUTE_ANALOG_JACK, CONFIDENCE_THRESHOLD_WIRED);

        // USB Path
        mTestSpecs[TESTROUTE_USB] =
                new TestSpec(TESTROUTE_USB, CONFIDENCE_THRESHOLD_WIRED);

        // Setup UI
        Resources resources = getResources();
        mYesString = resources.getString(R.string.audio_general_yes);
        mNoString = resources.getString(R.string.audio_general_no);
        mPassString = resources.getString(R.string.audio_general_pass);
        mFailString = resources.getString(R.string.audio_general_fail);
        mNotTestedString = resources.getString(R.string.audio_general_not_tested);
        mNotRequiredString = resources.getString(R.string.audio_general_not_required);
        mRequiredString = resources.getString(R.string.audio_general_required);

        // Pro Audio
        ((TextView) findViewById(R.id.audio_loopback_pro_audio)).setText(
                (mClaimsProAudio ? mYesString : mNoString));

        // Low Latency
        ((TextView) findViewById(R.id.audio_loopback_low_latency)).setText(
                (mClaimsLowLatency ? mYesString : mNoString));

        // Media Performance Class
        ((TextView) findViewById(R.id.audio_loopback_mpc)).setText(
                (mClaimsMediaPerformance ? mYesString : mNoString));

        // MMAP
        ((TextView) findViewById(R.id.audio_loopback_mmap)).setText(
                (mSupportsMMAP ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_mmap_exclusive)).setText(
                (mSupportsMMAPExclusive ? mYesString : mNoString));

        // Individual Test Results
        mResultsText[TESTROUTE_DEVICE] =
                (TextView) findViewById(R.id.audio_loopback_speakermicpath_info);
        mResultsText[TESTROUTE_ANALOG_JACK] =
                (TextView) findViewById(R.id.audio_loopback_headsetpath_info);
        mResultsText[TESTROUTE_USB] =
                (TextView) findViewById(R.id.audio_loopback_usbpath_info);

        mStartButtons[TESTROUTE_DEVICE] =
                (Button) findViewById(R.id.audio_loopback_speakermicpath_btn);
        mStartButtons[TESTROUTE_DEVICE].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_ANALOG_JACK] =
                (Button) findViewById(R.id.audio_loopback_headsetpath_btn);
        mStartButtons[TESTROUTE_ANALOG_JACK].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_USB] = (Button) findViewById(R.id.audio_loopback_usbpath_btn);
        mStartButtons[TESTROUTE_USB].setOnClickListener(mBtnClickListener);

        mAudioManager = getSystemService(AudioManager.class);

        mAudioManager.registerAudioDeviceCallback(new ConnectListener(), new Handler());

        connectLoopbackUI();

        enableStartButtons(true);

        handleTestCompletion(false);
    }

    //
    // UI State
    //
    private void enableStartButtons(boolean enable) {
        if (enable) {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(mTestSpecs[routeId].mRouteConnected);
            }
        } else {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(false);
            }
        }
    }

    private void connectLoopbackUI() {
        mAudioLevelText = (TextView)findViewById(R.id.audio_loopback_level_text);
        mAudioLevelSeekbar = (SeekBar)findViewById(R.id.audio_loopback_level_seekbar);
        mMaxLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setMax(mMaxLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(0.7 * mMaxLevel), 0);
        refreshLevel();

        mAudioLevelSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        progress, 0);
                Log.i(TAG,"Level set to: " + progress);
                refreshLevel();
            }
        });

        mTestStatusText = (TextView) findViewById(R.id.audio_loopback_status_text);
        mProgressBar = (ProgressBar) findViewById(R.id.audio_loopback_progress_bar);
        showWait(false);
    }

    //
    // Peripheral Connection Logic
    //
    void clearDeviceIds() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mInputDeviceId = testSpec.mInputDeviceId = TestSpec.DEVICEID_NONE;
        }
    }

    void clearDeviceConnected() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mRouteConnected = false;
        }
    }

    void scanPeripheralList(AudioDeviceInfo[] devices) {
        clearDeviceIds();
        clearDeviceConnected();

        for (AudioDeviceInfo devInfo : devices) {
            switch (devInfo.getType()) {
                // TESTROUTE_DEVICE (i.e. Speaker & Mic)
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_DEVICE].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_DEVICE].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_DEVICE].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_DEVICE].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_DEVICE].mDeviceName = devInfo.getProductName().toString();
                    break;

                // TESTROUTE_ANALOG_JACK
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_AUX_LINE:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mDeviceName =
                            devInfo.getProductName().toString();
                    break;

                // TESTROUTE_USB
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_USB].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_USB].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_USB].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_USB].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_USB].mDeviceName = devInfo.getProductName().toString();
            }

            enableStartButtons(true);
        }
    }

    private class ConnectListener extends AudioDeviceCallback {
        ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }
    }

    /**
     * refresh Audio Level seekbar and text
     */
    private void refreshLevel() {
        int currentLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setProgress(currentLevel);

        String levelText = String.format("%s: %d/%d",
                getResources().getString(R.string.audio_loopback_level_text),
                currentLevel, mMaxLevel);
        mAudioLevelText.setText(levelText);
    }

    //
    // show active progress bar
    //
    protected void showWait(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    //
    // Common logging
    //

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, "audio_loopback_latency_activity");
    }

    // Test-Schema
    private static final String KEY_SAMPLE_RATE = "sample_rate";
    private static final String KEY_IS_PRO_AUDIO = "is_pro_audio";
    private static final String KEY_IS_LOW_LATENCY = "is_low_latency";
    private static final String KEY_TEST_MMAP = "supports_mmap";
    private static final String KEY_TEST_MMAPEXCLUSIVE = "supports_mmap_exclusive";
    private static final String KEY_LEVEL = "level";

    private void recordRouteResults(int routeIndex) {
        if (mTestSpecs[routeIndex].mTestRun) {
            CtsVerifierReportLog reportLog = getReportLog();

            int audioLevel = mAudioLevelSeekbar.getProgress();
            reportLog.addValue(
                    KEY_LEVEL,
                    audioLevel,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IS_PRO_AUDIO,
                    mClaimsProAudio,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_TEST_MMAP,
                    mSupportsMMAP,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_TEST_MMAPEXCLUSIVE,
                    mSupportsMMAPExclusive,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_SAMPLE_RATE,
                    mNativeAnalyzerThread.getSampleRate(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    KEY_IS_LOW_LATENCY,
                    mNativeAnalyzerThread.isLowLatencyStream(),
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            mTestSpecs[routeIndex].recordTestResults(reportLog);

            reportLog.submit();
        }
    }

    @Override
    public void recordTestResults() {
        for (int route = 0; route < NUM_TEST_ROUTES; route++) {
            recordRouteResults(route);
        }
    }

    private void startAudioTest(Handler messageHandler, int testRouteId) {
        enableStartButtons(false);
        mResultsText[testRouteId].setText("Running...");

        mTestRoute = testRouteId;

        mTestSpecs[mTestRoute].startTest();

        getPassButton().setEnabled(false);

        mTestPhase = 0;

        mNativeAnalyzerThread = new NativeAnalyzerThread(this);
        if (mNativeAnalyzerThread != null) {
            mNativeAnalyzerThread.setMessageHandler(messageHandler);
            // This value matches AAUDIO_INPUT_PRESET_VOICE_RECOGNITION
            mNativeAnalyzerThread.setInputPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            startTestPhase();
        } else {
            Log.e(TAG, "Couldn't allocate native analyzer thread");
            mTestStatusText.setText(getResources().getString(R.string.audio_loopback_failure));
        }
    }

    private void startTestPhase() {
        if (mNativeAnalyzerThread != null) {
            Log.i(TAG, "mTestRoute: " + mTestRoute
                    + " mInputDeviceId: " + mTestSpecs[mTestRoute].mInputDeviceId
                    + " mOutputDeviceId: " + mTestSpecs[mTestRoute].mOutputDeviceId);
            mNativeAnalyzerThread.startTest(
                    mTestSpecs[mTestRoute].mInputDeviceId, mTestSpecs[mTestRoute].mOutputDeviceId);

            // what is this for?
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTestPhaseCompletion() {
        if (mNativeAnalyzerThread != null && mTestPhase < NUM_TEST_PHASES) {
            double latency = mNativeAnalyzerThread.getLatencyMillis();
            double confidence = mNativeAnalyzerThread.getConfidence();
            TestSpec testSpec = mTestSpecs[mTestRoute];
            testSpec.recordPhase(mTestPhase, latency, confidence);

            String result = String.format(
                    "Test %d Finished\nLatency: %.2f ms\nConfidence: %.2f\n",
                    mTestPhase, latency, confidence);

            mTestStatusText.setText(result);
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
                // Thread.sleep(/*STOP_TEST_TIMEOUT_MSEC*/500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            mTestPhase++;
            if (mTestPhase >= NUM_TEST_PHASES) {
                handleTestCompletion(true);
            } else {
                startTestPhase();
            }
        }
    }

    private void handleTestCompletion(boolean showResult) {
        TestSpec testSpec = mTestSpecs[mTestRoute];
        testSpec.handleTestCompletion();

        // Make sure the test thread is finished. It should already be done.
        if (mNativeAnalyzerThread != null) {
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mResultsText[mTestRoute].setText(testSpec.getResultString());

        LoopbackLatencyRequirements requirements = new LoopbackLatencyRequirements();
        boolean pass = isReportLogOkToPass()
                && requirements.evaluate(mClaimsProAudio,
                        Build.VERSION.MEDIA_PERFORMANCE_CLASS,
                        mTestSpecs[TESTROUTE_DEVICE].isMeasurementValid()
                                ? mTestSpecs[TESTROUTE_DEVICE].mMeanLatencyMS : 0.0,
                        mTestSpecs[TESTROUTE_ANALOG_JACK].isMeasurementValid()
                                ? mTestSpecs[TESTROUTE_ANALOG_JACK].mMeanLatencyMS :  0.0,
                        mTestSpecs[TESTROUTE_USB].isMeasurementValid()
                                ? mTestSpecs[TESTROUTE_USB].mMeanLatencyMS : 0.0);

        getPassButton().setEnabled(pass);

        StringBuilder sb = new StringBuilder();
        if (!isReportLogOkToPass()) {
            sb.append(getResources().getString(R.string.audio_general_reportlogtest) + "\n");
        }
        sb.append(requirements.getResultsString());
        if (showResult) {
            sb.append("\n" + (pass ? mPassString : mFailString));
        }
        mTestStatusText.setText(sb.toString());

        showWait(false);
        enableStartButtons(true);
    }

    /**
     * handler for messages from audio thread
     */
    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED:
                    Log.v(TAG,"got message native rec started!!");
                    showWait(true);
                    mTestStatusText.setText(String.format("[phase: %d] - Test Running...",
                            (mTestPhase + 1)));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_OPEN_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText("Test Error opening streams.");
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText("Test Error while recording.");
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                    mTestStatusText.setText("Test FAILED due to errors.");
                    handleTestCompletion(true);
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_ANALYZING:
                    mTestStatusText.setText(String.format("[phase: %d] - Analyzing ...",
                            mTestPhase + 1));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE:
                    handleTestPhaseCompletion();
                    break;
                default:
                    break;
            }
        }
    };

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audio_loopback_speakermicpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_DEVICE);
            } else if (id == R.id.audio_loopback_headsetpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_ANALOG_JACK);
            }  else if (id == R.id.audio_loopback_usbpath_btn) {
                startAudioTest(mMessageHandler, TESTROUTE_USB);
            }
        }
    }

    class LoopbackLatencyRequirements {
        public static final int MPC_NONE = 0;
        public static final int MPC_R = Build.VERSION_CODES.R;
        public static final int MPC_S = Build.VERSION_CODES.S;
        public static final int MPC_T = Build.VERSION_CODES.TIRAMISU;

        String mResultsString = new String();

        String getResultsString() {
            return mResultsString;
        }

        private boolean checkLatency(double measured, double limit) {
            return measured == LATENCY_NOT_MEASURED || measured <= limit;
        }

        public boolean evaluate(boolean proAudio,
                                       int mediaPerformanceClass,
                                       double deviceLatency,
                                       double analogLatency,
                                       double usbLatency) {

            // Required to test the Mic/Speaker path
            boolean internalPathRun = deviceLatency != LATENCY_NOT_MEASURED;

            // All devices must be under the basic limit.
            boolean basicPass = checkLatency(deviceLatency, LATENCY_BASIC)
                    && checkLatency(analogLatency, LATENCY_BASIC)
                    && checkLatency(usbLatency, LATENCY_BASIC);

            // For Media Performance Class T the RT latency must be <= 80 msec on one path.
            boolean mpcAtLeastOnePass = (mediaPerformanceClass < MPC_T)
                    || checkLatency(deviceLatency, LATENCY_MPC_AT_LEAST_ONE)
                    || checkLatency(analogLatency, LATENCY_MPC_AT_LEAST_ONE)
                    || checkLatency(usbLatency, LATENCY_MPC_AT_LEAST_ONE);

            // For ProAudio, the RT latency must be <= 25 msec on one path.
            boolean proAudioAtLeastOnePass = !proAudio
                    || checkLatency(deviceLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE)
                    || checkLatency(analogLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE)
                    || checkLatency(usbLatency, LATENCY_PRO_AUDIO_AT_LEAST_ONE);

            String supplementalText = "";
            // For ProAudio, analog and USB have specific limits
            boolean proAudioLimitsPass = !proAudio;
            if (proAudio) {
                if (analogLatency > 0.0) {
                    proAudioLimitsPass = analogLatency <= LATENCY_PRO_AUDIO_ANALOG;
                } else if (usbLatency > 0.0) {
                    // USB audio must be supported if 3.5mm jack not supported
                    proAudioLimitsPass =  usbLatency <= LATENCY_PRO_AUDIO_USB;
                }
            }

            boolean pass =
                    internalPathRun &&
                    basicPass &&
                    mpcAtLeastOnePass &&
                    proAudioAtLeastOnePass &&
                    proAudioLimitsPass;

            // Build the results explanation
            StringBuilder sb = new StringBuilder();
            if (proAudio) {
                sb.append("[Pro Audio]");
            } else if (mediaPerformanceClass != MPC_NONE) {
                sb.append("[MPC %d]" + mediaPerformanceClass);
            } else {
                sb.append("[Basic Audio]");
            }
            sb.append(" ");

            sb.append("\nSpeaker/Mic: " + (deviceLatency != LATENCY_NOT_MEASURED
                    ? String.format("%.2fms ", deviceLatency)
                    : (mNotTestedString + " - " + mRequiredString)));
            sb.append("\nHeadset: " + (analogLatency != LATENCY_NOT_MEASURED
                    ? String.format("%.2fms ", analogLatency)
                    : (mNotTestedString + " - " + mNotRequiredString)));
            sb.append("\nUSB: " + (usbLatency != LATENCY_NOT_MEASURED
                    ? String.format("%.2fms ", usbLatency)
                    : (mNotTestedString + " - " + mNotRequiredString)));

            sb.append(supplementalText);
            mResultsString = sb.toString();

            return pass;
        }
    }
}
