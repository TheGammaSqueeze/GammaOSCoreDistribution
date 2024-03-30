/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.JavaPlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;

@CddTest(requirement = "7.8.2.1/C-1-1,C-1-2,C-1-3,C-1-4,C-2-1")
public class AnalogHeadsetAudioActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener {
    private static final String TAG = AnalogHeadsetAudioActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    private AudioManager    mAudioManager;

    // UI
    private TextView mHasPortQueryText;
    private Button mHasAnalogPortYesBtn;
    private Button mHasAnalogPortNoBtn;

    private Button mPlayButton;
    private Button mStopButton;
    private Button mPlaybackSuccessBtn;
    private Button mPlaybackFailBtn;
    private TextView mPlaybackStatusTxt;

    private TextView mHeadsetNameText;
    private TextView mHeadsetPlugMessage;

    private TextView mButtonsPromptTxt;
    private TextView mHeadsetHookText;
    private TextView mHeadsetVolUpText;
    private TextView mHeadsetVolDownText;

    // Devices
    private AudioDeviceInfo mHeadsetDeviceInfo;
    private boolean mHasHeadsetPort;
    private boolean mPlugIntentReceived;
    private boolean mPlaybackSuccess;

    // Intents
    private HeadsetPlugReceiver mHeadsetPlugReceiver;

    // Buttons
    private boolean mHasHeadsetHook;
    private boolean mHasPlayPause;
    private boolean mHasVolUp;
    private boolean mHasVolDown;

    private TextView mResultsTxt;

    // Player
    protected boolean mIsPlaying = false;

    // Mega Player
    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;

    JavaPlayer mAudioPlayer;

    // ReportLog Schema
    private static final String SECTION_ANALOG_HEADSET = "analog_headset_activity";
    private static final String KEY_HAS_HEADSET_PORT = "has_headset_port";
    private static final String KEY_HEADSET_PLUG_INTENT_STATE = "intent_received_state";
    private static final String KEY_CLAIMS_HEADSET_PORT = "claims_headset_port";
    private static final String KEY_HEADSET_CONNECTED = "headset_connected";
    private static final String KEY_KEYCODE_HEADSETHOOK = "keycode_headset_hook";
    private static final String KEY_KEYCODE_PLAY_PAUSE = "keycode_play_pause";
    private static final String KEY_KEYCODE_VOLUME_UP = "keycode_volume_up";
    private static final String KEY_KEYCODE_VOLUME_DOWN = "keycode_volume_down";

    public AnalogHeadsetAudioActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.audio_headset_audio_activity);

        mHeadsetNameText = (TextView)findViewById(R.id.headset_analog_name);
        mHeadsetPlugMessage = (TextView)findViewById(R.id.headset_analog_plug_message);

        // Analog Port?
        mHasPortQueryText = (TextView)findViewById(R.id.analog_headset_query) ;
        mHasAnalogPortYesBtn = (Button)findViewById(R.id.headset_analog_port_yes);
        mHasAnalogPortYesBtn.setOnClickListener(this);
        mHasAnalogPortNoBtn = (Button)findViewById(R.id.headset_analog_port_no);
        mHasAnalogPortNoBtn.setOnClickListener(this);

        // Player Controls.
        mPlayButton = (Button)findViewById(R.id.headset_analog_play);
        mPlayButton.setOnClickListener(this);
        mStopButton = (Button)findViewById(R.id.headset_analog_stop);
        mStopButton.setOnClickListener(this);
        mPlaybackStatusTxt = (TextView)findViewById(R.id.analog_headset_playback_status);

        // Play Status
        mPlaybackSuccessBtn = (Button)findViewById(R.id.headset_analog_play_yes);
        mPlaybackSuccessBtn.setOnClickListener(this);
        mPlaybackFailBtn = (Button)findViewById(R.id.headset_analog_play_no);
        mPlaybackFailBtn.setOnClickListener(this);
        mPlaybackSuccessBtn.setEnabled(false);
        mPlaybackFailBtn.setEnabled(false);

        // Keycodes
        mButtonsPromptTxt = (TextView)findViewById(R.id.analog_headset_keycodes_prompt);
        mHeadsetHookText = (TextView)findViewById(R.id.headset_keycode_headsethook);
        mHeadsetVolUpText = (TextView)findViewById(R.id.headset_keycode_volume_up);
        mHeadsetVolDownText = (TextView)findViewById(R.id.headset_keycode_volume_down);

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        if (isTelevisionOrFixedVolume()) {
            mButtonsPromptTxt.setVisibility(View.GONE);
            mHeadsetHookText.setVisibility(View.GONE);
            mHeadsetVolUpText.setVisibility(View.GONE);
            mHeadsetVolDownText.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.headset_keycodes)).setVisibility(View.GONE);
        }

        mResultsTxt = (TextView)findViewById(R.id.headset_results);

        setupPlayer();

        mAudioManager.registerAudioDeviceCallback(new ConnectListener(), new Handler());

        mHeadsetPlugReceiver = new HeadsetPlugReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(mHeadsetPlugReceiver, filter);

        showKeyMessagesState();

        setInfoResources(R.string.analog_headset_test, isTelevisionOrFixedVolume()
                ? R.string.analog_headset_test_info_tv : R.string.analog_headset_test_info, -1);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
    }

    //
    // Reporting
    //
    private boolean calculatePass() {
        if (!mHasHeadsetPort) {
            mResultsTxt.setText(getResources().getString(R.string.analog_headset_pass_noheadset));
            return true;
        } else {
            boolean pass = mPlugIntentReceived &&
                    mHeadsetDeviceInfo != null &&
                    mPlaybackSuccess &&
                    (isTelevisionOrFixedVolume()
                    || ((mHasHeadsetHook || mHasPlayPause) && mHasVolUp && mHasVolDown));
            if (pass) {
                mResultsTxt.setText(getResources().getString(R.string.analog_headset_pass));
            } else if (!isReportLogOkToPass()) {
                mResultsTxt.setText(getResources().getString(R.string.audio_general_reportlogtest));
            }
            return pass;
        }
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_ANALOG_HEADSET);
    }

    @Override
    public void recordTestResults() {
        getReportLog().submit();
    }

    private void reportHeadsetPort(boolean has) {
        mHasHeadsetPort = has;
        getReportLog().addValue(
                KEY_HAS_HEADSET_PORT,
                has ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        enablePlayerButtons(has && mHeadsetDeviceInfo != null);

        if (!has) {
            // no port, so can't test. Let them pass
            getPassButton().setEnabled(calculatePass());
        }
    }

    private void reportPlugIntent(Intent intent) {
        // NOTE: This is a "sticky" intent meaning that if a headset has EVER been plugged in
        // (since a reboot), we will receive this intent.
        Resources resources = getResources();

        // [C-1-4] MUST trigger ACTION_HEADSET_PLUG upon a plug insert,
        // but only after all contacts on plug are touching their relevant segments on the jack.
        mPlugIntentReceived = true;

        // state - 0 for unplugged, 1 for plugged.
        // name - Headset type, human readable string
        // microphone - 1 if headset has a microphone, 0 otherwise

        int state = intent.getIntExtra("state", -1);
        if (state != -1) {
            StringBuilder sb = new StringBuilder();
            sb.append(resources.getString(R.string.analog_headset_action_received)
                    + resources.getString(
                            state == 0 ? R.string.analog_headset_unplugged
                                       : R.string.analog_headset_plugged));

            String name = intent.getStringExtra("name");
            if (name != null) {
                sb.append(" - " + name);
            }

            int hasMic = intent.getIntExtra("microphone", 0);
            if (hasMic == 1) {
                sb.append(resources.getString(R.string.analog_headset_mic));
            }

            mHeadsetPlugMessage.setText(sb.toString());

            // If we receive this intent, there is no need to ask if there is an analog jack.
            reportHeadsetPort(true);

            mHasPortQueryText.setText(getResources().getString(
                    R.string.analog_headset_port_detected));
            mHasAnalogPortYesBtn.setVisibility(View.GONE);
            mHasAnalogPortNoBtn.setVisibility(View.GONE);

            mPlaybackStatusTxt.setText(getResources().getString(
                    R.string.analog_headset_playback_prompt));
        }

        getReportLog().addValue(
                KEY_HEADSET_PLUG_INTENT_STATE,
                state,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    private void reportPlaybackStatus(boolean success) {
        // [C-1-1] MUST support audio playback to stereo headphones
        // and stereo headsets with a microphone.
        mPlaybackSuccess = success;

        mPlaybackSuccessBtn.setEnabled(success);
        mPlaybackFailBtn.setEnabled(!success);

        getPassButton().setEnabled(calculatePass());

        getReportLog().addValue(
                KEY_CLAIMS_HEADSET_PORT,
                success ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        if (success) {
            mButtonsPromptTxt.setText(getResources().getString(
                    R.string.analog_headset_press_buttons));
        }
    }

    //
    // UI
    //
    private void showConnectedDevice() {
        if (mHeadsetDeviceInfo != null) {
            mHeadsetNameText.setText(getResources().getString(
                    R.string.analog_headset_headset_connected));
        } else {
            mHeadsetNameText.setText(getResources().getString(R.string.analog_headset_no_headset));
        }
    }

    private void enablePlayerButtons(boolean enabled) {
        mPlayButton.setEnabled(enabled);
        mStopButton.setEnabled(enabled);
    }

    private void showKeyMessagesState() {
        mHeadsetHookText.setTextColor((mHasHeadsetHook || mHasPlayPause)
                ? Color.WHITE : Color.GRAY);
        mHeadsetVolUpText.setTextColor(mHasVolUp ? Color.WHITE : Color.GRAY);
        mHeadsetVolDownText.setTextColor(mHasVolDown ? Color.WHITE : Color.GRAY);
    }

    //
    // Player
    //
    protected void setupPlayer() {
        //
        // Allocate the source provider for the sort of signal we want to play
        //
        AudioSourceProvider sourceProvider = new SinAudioSourceProvider();
        try {
            PlayerBuilder builder = new PlayerBuilder();
            mAudioPlayer = (JavaPlayer)builder
                    // choose one or the other of these for a Java or an Oboe player
                    .setPlayerType(PlayerBuilder.TYPE_JAVA)
                    // .setPlayerType(PlayerBuilder.PLAYER_OBOE)
                    .setSourceProvider(sourceProvider)
                    .build();
        } catch (PlayerBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaPlayer build.");
        }
    }

    protected void startPlay() {
        if (!mIsPlaying) {
            //TODO - explain the choice of 96 here.
            mAudioPlayer.setupStream(NUM_CHANNELS, SAMPLE_RATE, 96);
            mAudioPlayer.startStream();
            mIsPlaying = true;

            mPlayButton.setEnabled(false);
            mStopButton.setEnabled(true);
        }
    }

    protected void stopPlay() {
        if (mIsPlaying) {
            mAudioPlayer.stopStream();
            mAudioPlayer.teardownStream();
            mIsPlaying = false;

            mPlayButton.setEnabled(true);
            mStopButton.setEnabled(false);

            mPlaybackStatusTxt.setText(getResources().getString(
                    R.string.analog_headset_playback_query));
        }
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.headset_analog_port_yes) {
            reportHeadsetPort(true);
        } else if (id == R.id.headset_analog_port_no) {
            reportHeadsetPort(false);
        } else if (id == R.id.headset_analog_play) {
            startPlay();
        } else if (id == R.id.headset_analog_stop) {
            stopPlay();
            mPlaybackSuccessBtn.setEnabled(true);
            mPlaybackFailBtn.setEnabled(true);
        } else if (id == R.id.headset_analog_play_yes) {
            reportPlaybackStatus(true);
        } else if (id == R.id.headset_analog_play_no) {
            reportPlaybackStatus(false);
        }
    }

    //
    // Devices
    //
    private void scanPeripheralList(AudioDeviceInfo[] devices) {
        mHeadsetDeviceInfo = null;
        for(AudioDeviceInfo devInfo : devices) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                mHeadsetDeviceInfo = devInfo;
                break;
            }
        }

        getReportLog().addValue(
                KEY_HEADSET_CONNECTED,
                mHeadsetDeviceInfo != null ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportHeadsetPort(mHeadsetDeviceInfo != null);

        showConnectedDevice();
    }

    private class ConnectListener extends AudioDeviceCallback {
        /*package*/ ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            Log.i(TAG, "onAudioDevicesAdded() num:" + addedDevices.length);

            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            Log.i(TAG, "onAudioDevicesRemoved() num:" + removedDevices.length);

            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }
    }

    private class HeadsetPlugReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            reportPlugIntent(intent);
        }
    }

    //
    // Keycodes
    //
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Log.i(TAG, "onKeyDown(" + keyCode + ")");
        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
                mHasHeadsetHook = true;
                showKeyMessagesState();
                getPassButton().setEnabled(calculatePass());
                getReportLog().addValue(
                        KEY_KEYCODE_HEADSETHOOK, 1, ResultType.NEUTRAL, ResultUnit.NONE);
                break;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                mHasPlayPause = true;
                showKeyMessagesState();
                getPassButton().setEnabled(calculatePass());
                getReportLog().addValue(
                        KEY_KEYCODE_PLAY_PAUSE, 1, ResultType.NEUTRAL, ResultUnit.NONE);
                break;

            case KeyEvent.KEYCODE_VOLUME_UP:
                mHasVolUp = true;
                showKeyMessagesState();
                getPassButton().setEnabled(calculatePass());
                getReportLog().addValue(
                        KEY_KEYCODE_VOLUME_UP, 1, ResultType.NEUTRAL, ResultUnit.NONE);
                break;

            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mHasVolDown = true;
                showKeyMessagesState();
                getPassButton().setEnabled(calculatePass());
                getReportLog().addValue(
                        KEY_KEYCODE_VOLUME_DOWN, 1, ResultType.NEUTRAL, ResultUnit.NONE);
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isTelevisionOrFixedVolume() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || mAudioManager.isVolumeFixed();
    }
}
