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
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;

import java.util.concurrent.ExecutionException;

/**
 * Helper class to send NFC events.
 */
public class NfcSender {

    private static final String NFC_PACKAGE = "DS_PKG";
    private static final String NFC_TAG = "DS_TAG";

    /**
     * Callback to update sender status.
     */
    public interface Callback {

        void onSend(String message);
    }

    private final String mAddress;
    private final Activity mActivity;
    private final Callback mCallback;
    private final SenderCallback mSenderCallback;
    private String mSessage;

    public NfcSender(String address, Activity activity, Callback callback) {
        this.mCallback = callback;
        this.mAddress = address;
        this.mActivity = activity;
        DeviceShadowEnvironment.addDevice(address);
        this.mSenderCallback = new SenderCallback();
    }

    public void startSend(String message) throws InterruptedException, ExecutionException {
        this.mSessage = message;
        DeviceShadowEnvironment.run(mAddress, new Runnable() {
            @Override
            public void run() {
                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(mActivity);
                nfcAdapter.setNdefPushMessageCallback(mSenderCallback, mActivity);
                nfcAdapter.setOnNdefPushCompleteCallback(mSenderCallback, mActivity);
            }
        }).get();
    }

    class SenderCallback implements CreateNdefMessageCallback, OnNdefPushCompleteCallback {

        @Override
        public NdefMessage createNdefMessage(NfcEvent event) {
            NdefMessage msg = new NdefMessage(new NdefRecord[]{
                    NdefRecord.createExternal(NFC_PACKAGE, NFC_TAG, mSessage.getBytes())
            });
            return msg;
        }

        @Override
        public void onNdefPushComplete(NfcEvent event) {
            mCallback.onSend(mSessage);
        }
    }
}
