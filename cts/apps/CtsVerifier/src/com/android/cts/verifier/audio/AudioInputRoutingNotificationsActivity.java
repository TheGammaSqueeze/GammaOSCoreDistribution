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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.R;

import org.hyphonate.megaaudio.recorder.JavaRecorder;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

/*
 * Tests AudioRecord (re)Routing messages.
 */
public class AudioInputRoutingNotificationsActivity extends AudioWiredDeviceBaseActivity {
    private static final String TAG = "AudioInputRoutingNotificationsActivity";

    Button recordBtn;
    Button stopBtn;
    TextView mInfoView;

    Context mContext;

    int mNumRoutingNotifications;

    OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;
    int mNumFrames;

    private JavaRecorder mAudioRecorder;
    private AudioRecordRoutingChangeListener mRouteChangeListener;
    private boolean mIsRecording;

    // ignore messages sent as a consequence of starting the player
    private static final int NUM_IGNORE_MESSAGES = 2;

    boolean mRoutingNotificationReceived;

    // ReportLog schema
    protected static final String SECTION_INPUT_ROUTING = "audio_in_routing_notifications";

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (mAudioRecorder == null) {
                return; // failed to create the recorder
            }

            if (v.getId() == R.id.audio_routingnotification_recordBtn) {
                startRecording();
            } else if (v.getId() == R.id.audio_routingnotification_recordStopBtn) {
                stopRecording();
            }
        }
    }

    private void startRecording() {
        if (!mIsRecording) {
            mNumRoutingNotifications = 0;

            mAudioRecorder.startStream();

            AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
            audioRecord.addOnRoutingChangedListener(mRouteChangeListener,
                    new Handler());

            mIsRecording = true;
            enableTestButtons(false);
        }
    }

    private void stopRecording() {
        if (mIsRecording) {
            mAudioRecorder.stopStream();

            AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
            audioRecord.removeOnRoutingChangedListener(mRouteChangeListener);

            mIsRecording = false;
            enableTestButtons(true);
        }
    }

    private class AudioRecordRoutingChangeListener implements AudioRecord.OnRoutingChangedListener {
        public void onRoutingChanged(AudioRecord audioRecord) {
            // Starting recording triggers routing messages, so ignore the first one.
            mNumRoutingNotifications++;
            if (mNumRoutingNotifications <= NUM_IGNORE_MESSAGES) {
                return;
            }

            TextView textView =
                    (TextView)findViewById(R.id.audio_routingnotification_audioRecord_change);
            String msg = mContext.getResources().getString(
                    R.string.audio_routingnotification_recordRoutingMsg);
            AudioDeviceInfo routedDevice = audioRecord.getRoutedDevice();
            mConnectedPeripheralName = AudioDeviceUtils.formatDeviceName(routedDevice);
            textView.setText(msg + ": " + mConnectedPeripheralName);
            mRoutingNotificationReceived = true;
            calculatePass();
        }
    }

    @Override
    protected void enableTestButtons(boolean enabled) {
        recordBtn.setEnabled(enabled);
        stopBtn.setEnabled(!enabled);
    }

    @Override
    protected void calculatePass() {
        getPassButton().setEnabled(mRoutingNotificationReceived || !mSupportsWiredPeripheral);
        if (mRoutingNotificationReceived) {
            ((TextView) findViewById(R.id.audio_routingnotification_testresult)).setText(
                    "Test PASSES - Routing notification received");
        } else if (!mSupportsWiredPeripheral) {
            ((TextView) findViewById(
                    R.id.audio_routingnotification_testresult)).setText(
                            "Test PASSES - No peripheral support");
        }
    }

    protected void storeTestResults() {
        super.storeTestResults();

        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_ROUTING_RECEIVED,
                mRoutingNotificationReceived ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_input_routingnotifications_test);

        Button btn;
        recordBtn = (Button) findViewById(R.id.audio_routingnotification_recordBtn);
        recordBtn.setOnClickListener(mBtnClickListener);
        stopBtn = (Button) findViewById(R.id.audio_routingnotification_recordStopBtn);
        stopBtn.setOnClickListener(mBtnClickListener);

        enableTestButtons(false);

        mInfoView = (TextView) findViewById(R.id.info_text);

        mContext = this;

        // Setup Recorder
        mNumFrames = Recorder.calcMinBufferFrames(NUM_CHANNELS, SAMPLE_RATE);

        RecorderBuilder builder = new RecorderBuilder();
        try {
            mAudioRecorder = (JavaRecorder) builder
                    .setRecorderType(RecorderBuilder.TYPE_JAVA)
                    .setAudioSinkProvider(new NopAudioSinkProvider())
                    .build();
            mAudioRecorder.setupStream(NUM_CHANNELS, SAMPLE_RATE, mNumFrames);
        } catch (RecorderBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaRecorder build.");
        }

        mRouteChangeListener = new AudioRecordRoutingChangeListener();
        AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
        audioRecord.addOnRoutingChangedListener(mRouteChangeListener, new Handler());

        // "Honor System" buttons
        super.setup();
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_input_routingnotifications_test,
                R.string.audio_input_routingnotification_instructions, -1);
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_INPUT_ROUTING);
    }

    @Override
    public void onBackPressed () {
        stopRecording();
        super.onBackPressed();
    }
}
