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

import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

abstract class AudioWiredDeviceBaseActivity extends PassFailButtons.Activity {
    private static final String TAG = AudioWiredDeviceBaseActivity.class.getSimpleName();

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    private Button mSupportsBtn;
    private Button mDoesntSupportBtn;

    protected boolean mSupportsWiredPeripheral = true;
    protected String mConnectedPeripheralName;

    protected abstract void enableTestButtons(boolean enabled);
    protected abstract void calculatePass();

    // ReportLog schema
    private static final String KEY_WIRED_PORT_SUPPORTED = "wired_port_supported";
    protected static final String KEY_SUPPORTS_PERIPHERALS = "supports_wired_peripherals";
    protected static final String KEY_ROUTING_RECEIVED = "routing_received";
    protected static final String KEY_CONNECTED_PERIPHERAL = "routing_connected_peripheral";

    AudioWiredDeviceBaseActivity() {
    }

    private void recordWiredPortFound(boolean found) {
        getReportLog().addValue(
                "User Reported Wired Port",
                found ? 1.0 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    protected void setup() {
        // The "Honor" system buttons
        (mSupportsBtn =
                (Button) findViewById(R.id.audio_wired_no)).setOnClickListener(mBtnClickListener);
        (mDoesntSupportBtn =
                (Button) findViewById(R.id.audio_wired_yes)).setOnClickListener(mBtnClickListener);
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audio_wired_no) {
                Log.i(TAG, "User denies wired device existence");
                mSupportsWiredPeripheral = false;
            } else if (id == R.id.audio_wired_yes) {
                Log.i(TAG, "User confirms wired device existence");
                mSupportsWiredPeripheral = true;
            }

            Log.i(TAG, "Wired Device Support:" + mSupportsWiredPeripheral);
            enableTestButtons(mSupportsWiredPeripheral);
            recordWiredPortFound(mSupportsWiredPeripheral);
            calculatePass();
        }
    }

    protected void storeTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_WIRED_PORT_SUPPORTED,
                mSupportsWiredPeripheral ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CONNECTED_PERIPHERAL,
                mConnectedPeripheralName,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public void recordTestResults() {
        storeTestResults();

        getReportLog().submit();
    }
}
