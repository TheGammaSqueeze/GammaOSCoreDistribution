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

package com.android.libraries.testing.deviceshadower.internal.nfc;

import android.content.Intent;
import android.nfc.BeamShareData;
import android.nfc.IAppCallback;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;

import com.android.libraries.testing.deviceshadower.Enums.NfcOperation;
import com.android.libraries.testing.deviceshadower.Nfclet;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.common.Interrupter;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import javax.annotation.concurrent.GuardedBy;

/**
 * Implementation of Nfclet.
 */
public class NfcletImpl implements Nfclet {

    private static final Logger LOGGER = Logger.create("NfcletImpl");

    IAppCallback mAppCallback;
    private final Interrupter mInterrupter;

    @GuardedBy("this")
    private int mCurrentState;

    public NfcletImpl() {
        mInterrupter = new Interrupter();
        mCurrentState = NfcAdapter.STATE_OFF;
    }

    public void onNear(NfcletImpl remote) {
        if (remote.mAppCallback != null) {
            LOGGER.v("NFC receiver get beam share data from remote");
            BeamShareData data = remote.mAppCallback.createBeamShareData();
            DeviceShadowEnvironmentImpl.getLocalDeviceletImpl().getBroadcastManager()
                    .sendBroadcast(createNdefDiscoveredIntent(data), null);
        }
        if (mAppCallback != null) {
            LOGGER.v("NFC sender onNdefPushComplete");
            mAppCallback.onNdefPushComplete();
        }
    }

    public synchronized int getState() {
        return mCurrentState;
    }

    public boolean enable() {
        if (shouldInterrupt(NfcOperation.ENABLE)) {
            return false;
        }
        LOGGER.v("Enable NFC Adapter");
        updateState(NfcAdapter.STATE_TURNING_ON);
        updateState(NfcAdapter.STATE_ON);
        return true;
    }

    public boolean disable() {
        if (shouldInterrupt(NfcOperation.DISABLE)) {
            return false;
        }
        LOGGER.v("Disable NFC Adapter");
        updateState(NfcAdapter.STATE_TURNING_OFF);
        updateState(NfcAdapter.STATE_OFF);
        return true;
    }

    @Override
    public synchronized Nfclet setInitialState(int state) {
        mCurrentState = state;
        return this;
    }

    @Override
    public Nfclet setInterruptOperation(NfcOperation operation) {
        mInterrupter.addInterruptOperation(operation);
        return this;
    }

    public boolean shouldInterrupt(NfcOperation operation) {
        return mInterrupter.shouldInterrupt(operation);
    }

    private synchronized void updateState(int state) {
        if (mCurrentState != state) {
            mCurrentState = state;
            DeviceShadowEnvironmentImpl.getLocalDeviceletImpl().getBroadcastManager()
                    .sendBroadcast(createAdapterStateChangedIntent(state), null);
        }
    }

    private Intent createAdapterStateChangedIntent(int state) {
        Intent intent = new Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        intent.putExtra(NfcAdapter.EXTRA_ADAPTER_STATE, state);
        return intent;
    }

    private Intent createNdefDiscoveredIntent(BeamShareData data) {
        Intent intent = new Intent();
        intent.setAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{data.ndefMessage});
        // TODO(b/200231384): uncomment when uri and mime type implemented.
        // ndefUri = message.getRecords()[0].toUri();
        // ndefMimeType = message.getRecords()[0].toMimeType();
        return intent;
    }
}
