/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.helpers.nfc;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import android.util.Log;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to receive NFC events.
 */
public class NfcReceiver {

    private static final String TAG = "NfcReceiver";

    /**
     * Callback to receive message.
     */
    public interface Callback {

        void onReceive(String message);
    }

    private final String mAddress;
    private final Activity mActivity;
    private CountDownLatch mReceiveLatch;

    private final BroadcastReceiver mReceiver;
    private final IntentFilter mFilter;

    public NfcReceiver(String address, Activity activity, final Callback callback) {
        this(address, activity, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
                    processIntent(callback, intent);
                }
            }
        });
        DeviceShadowEnvironment.addDevice(address);
    }

    public NfcReceiver(
            final String address, Activity activity, final BroadcastReceiver clientReceiver) {
        this.mAddress = address;
        this.mActivity = activity;

        this.mFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.v(TAG, "Receive broadcast on device " + address);
                clientReceiver.onReceive(context, intent);
                mReceiveLatch.countDown();
            }
        };
        DeviceShadowEnvironment.addDevice(address);
    }

    public void startReceive() throws InterruptedException, ExecutionException {
        mReceiveLatch = new CountDownLatch(1);

        DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mActivity.getApplication().registerReceiver(mReceiver, mFilter);
            }
        }).get();
    }

    public void waitUntilReceive(long timeoutMillis) throws InterruptedException {
        mReceiveLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void stopReceive() throws InterruptedException, ExecutionException {
        DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                mActivity.getApplication().unregisterReceiver(mReceiver);
            }
        }).get();
    }

    static void processIntent(Callback callback, Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null && rawMsgs.length > 0) {
            // only one message sent during the beam
            NdefMessage msg = (NdefMessage) rawMsgs[0];
            if (callback != null) {
                callback.onReceive(new String(msg.getRecords()[0].getPayload()));
            }
        }
    }

}
