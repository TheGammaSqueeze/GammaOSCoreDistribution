/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.bass_client;

import android.annotation.NonNull;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.IBluetoothLeBroadcastAssistantCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.bluetooth.btservice.ServiceFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bass Utility functions
 */
class BassUtils {
    private static final String TAG = "BassUtils";

    // Using ArrayList as KEY to hashmap. May be not risk
    // in this case as It is used to track the callback to cancel Scanning later
    private final Map<ArrayList<IBluetoothLeBroadcastAssistantCallback>, ScanCallback>
            mLeAudioSourceScanCallbacks =
            new HashMap<ArrayList<IBluetoothLeBroadcastAssistantCallback>, ScanCallback>();
    private final Map<BluetoothDevice, ScanCallback> mBassAutoAssist =
            new HashMap<BluetoothDevice, ScanCallback>();

    /*LE Scan related members*/
    private boolean mBroadcastersAround = false;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner mLeScanner = null;
    private BassClientService mService = null;
    private ServiceFactory mFactory = new ServiceFactory();

    BassUtils(BassClientService service) {
        mService = service;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    void cleanUp() {
        if (mLeAudioSourceScanCallbacks != null) {
            mLeAudioSourceScanCallbacks.clear();
        }
        if (mBassAutoAssist != null) {
            mBassAutoAssist.clear();
        }
    }

    private final Handler mAutoAssistScanHandler =
            new Handler() {
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    switch (msg.what) {
                        case BassConstants.AA_START_SCAN:
                            Message m = obtainMessage(BassConstants.AA_SCAN_TIMEOUT);
                            sendMessageDelayed(m, BassConstants.AA_SCAN_TIMEOUT_MS);
                            mService.startSearchingForSources(null);
                            break;
                        case BassConstants.AA_SCAN_SUCCESS:
                            // Able to find to desired desired Source Device
                            ScanResult scanRes = (ScanResult) msg.obj;
                            BluetoothDevice dev = scanRes.getDevice();
                            mService.stopSearchingForSources();
                            mService.selectSource(dev, scanRes, true);
                            break;
                        case BassConstants.AA_SCAN_FAILURE:
                            // Not able to find the given source
                            break;
                        case BassConstants.AA_SCAN_TIMEOUT:
                            mService.stopSearchingForSources();
                            break;
                    }
                }
            };

    @NonNull Handler getAutoAssistScanHandler() {
        return mAutoAssistScanHandler;
    }

    void triggerAutoAssist(BluetoothLeBroadcastReceiveState recvState) {
        Message msg = mAutoAssistScanHandler.obtainMessage(BassConstants.AA_START_SCAN);
        msg.obj = recvState.getSourceDevice();
        mAutoAssistScanHandler.sendMessage(msg);
    }

    static boolean containUuid(List<ScanFilter> filters, ParcelUuid uuid) {
        for (ScanFilter filter: filters) {
            if (filter.getServiceUuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    static int parseBroadcastId(byte[] broadcastIdBytes) {
        int broadcastId;
        broadcastId = (0x00FF0000 & (broadcastIdBytes[2] << 16));
        broadcastId |= (0x0000FF00 & (broadcastIdBytes[1] << 8));
        broadcastId |= (0x000000FF & broadcastIdBytes[0]);
        return broadcastId;
    }

    static void log(String msg) {
        if (BassConstants.BASS_DBG) {
            Log.d(TAG, msg);
        }
    }

    static void printByteArray(byte[] array) {
        log("Entire byte Array as string: " + Arrays.toString(array));
        log("printitng byte by bte");
        for (int i = 0; i < array.length; i++) {
            log("array[" + i + "] :" + Byte.toUnsignedInt(array[i]));
        }
    }

    static void reverse(byte[] address) {
        int len = address.length;
        for (int i = 0; i < len / 2; ++i) {
            byte b = address[i];
            address[i] = address[len - 1 - i];
            address[len - 1 - i] = b;
        }
    }
}
