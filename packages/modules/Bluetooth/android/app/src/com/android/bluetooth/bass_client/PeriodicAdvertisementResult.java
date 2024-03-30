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

import android.bluetooth.BluetoothDevice;
import android.util.Log;

/**
 * Periodic Advertisement Result
 */
public class PeriodicAdvertisementResult {
    private static final String TAG = PeriodicAdvertisementResult.class.getSimpleName();

    private BluetoothDevice mDevice;
    private int mAddressType;
    private int mAdvSid;
    private int mSyncHandle;
    private int mPAInterval;
    private int mBroadcastId;

    PeriodicAdvertisementResult(BluetoothDevice device,
                                int addressType,
                                int syncHandle,
                                int advSid,
                                int paInterval,
                                int broadcastId) {
        mDevice = device;
        mAddressType = addressType;
        mAdvSid = advSid;
        mSyncHandle = syncHandle;
        mPAInterval = paInterval;
        mBroadcastId = broadcastId;
    }

    /**
     * Update Sync handle
     */
    public void updateSyncHandle(int syncHandle) {
        mSyncHandle = syncHandle;
    }

    /**
     * Get Sync handle
     */
    public int getSyncHandle() {
        return mSyncHandle;
    }

    /**
     * Update Adv ID
     */
    public void updateAdvSid(int advSid) {
        mAdvSid = advSid;
    }

    /**
     * Get Adv ID
     */
    public int getAdvSid() {
        return mAdvSid;
    }

    /**
     * Update address type
     */
    public void updateAddressType(int addressType) {
        mAddressType = addressType;
    }

    /**
     * Get address type
     */
    public int getAddressType() {
        return mAddressType;
    }

    /**
     * Update Adv interval
     */
    public void updateAdvInterval(int advInterval) {
        mPAInterval = advInterval;
    }

    /**
     * Get Adv interval
     */
    public int getAdvInterval() {
        return mPAInterval;
    }

    /**
     * Update broadcast ID
     */
    public void updateBroadcastId(int broadcastId) {
        mBroadcastId = broadcastId;
    }

    /**
     * Get broadcast ID
     */
    public int getBroadcastId() {
        return mBroadcastId;
    }

    /**
     * print
     */
    public void print() {
        log("-- PeriodicAdvertisementResult --");
        log("mDevice:" + mDevice);
        log("mAddressType:" + mAddressType);
        log("mAdvSid:" + mAdvSid);
        log("mSyncHandle:" + mSyncHandle);
        log("mPAInterval:" + mPAInterval);
        log("mBroadcastId:" + mBroadcastId);
        log("-- END: PeriodicAdvertisementResult --");
    }

    static void log(String msg) {
        if (BassConstants.BASS_DBG) {
            Log.d(TAG, msg);
        }
    }
}
