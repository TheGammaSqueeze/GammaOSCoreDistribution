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

package com.android.libraries.testing.deviceshadower.internal;

import com.android.libraries.testing.deviceshadower.Bluelet;
import com.android.libraries.testing.deviceshadower.Devicelet;
import com.android.libraries.testing.deviceshadower.Enums.Distance;
import com.android.libraries.testing.deviceshadower.Nfclet;
import com.android.libraries.testing.deviceshadower.Smslet;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.BlueletImpl;
import com.android.libraries.testing.deviceshadower.internal.common.BroadcastManager;
import com.android.libraries.testing.deviceshadower.internal.common.Scheduler;
import com.android.libraries.testing.deviceshadower.internal.nfc.NfcletImpl;
import com.android.libraries.testing.deviceshadower.internal.sms.SmsletImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * DeviceletImpl is the implementation to hold different medium-let in DeviceShadowEnvironment.
 */
public class DeviceletImpl implements Devicelet {

    private final BlueletImpl mBluelet;
    private final NfcletImpl mNfclet;
    private final SmsletImpl mSmslet;
    private final BroadcastManager mBroadcastManager;
    private final String mAddress;
    private final Map<String, Distance> mDistanceMap = new HashMap<>();
    private final Scheduler mServiceScheduler;
    private final Scheduler mUiScheduler;

    public DeviceletImpl(String address) {
        this.mAddress = address;
        this.mServiceScheduler = new Scheduler(address + "-service");
        this.mUiScheduler = new Scheduler(address + "-main");
        this.mBroadcastManager = new BroadcastManager(mUiScheduler);
        this.mBluelet = new BlueletImpl(address, mBroadcastManager);
        this.mNfclet = new NfcletImpl();
        this.mSmslet = new SmsletImpl();
    }

    @Override
    public Bluelet bluetooth() {
        return mBluelet;
    }

    public BlueletImpl blueletImpl() {
        return mBluelet;
    }

    @Override
    public Nfclet nfc() {
        return mNfclet;
    }

    public NfcletImpl nfcletImpl() {
        return mNfclet;
    }

    @Override
    public Smslet sms() {
        return mSmslet;
    }

    public SmsletImpl smsletImpl() {
        return mSmslet;
    }

    public BroadcastManager getBroadcastManager() {
        return mBroadcastManager;
    }

    @Override
    public String getAddress() {
        return mAddress;
    }

    Scheduler getServiceScheduler() {
        return mServiceScheduler;
    }

    Scheduler getUiScheduler() {
        return mUiScheduler;
    }

    /**
     * Update distance to remote device.
     *
     * @return true if distance updated.
     */
    /*package*/ boolean updateDistance(String remoteAddress, Distance distance) {
        Distance currentDistance = mDistanceMap.get(remoteAddress);
        if (currentDistance == null || !distance.equals(currentDistance)) {
            mDistanceMap.put(remoteAddress, distance);
            return true;
        }
        return false;
    }

    /*package*/ void onDistanceChange(DeviceletImpl remote, Distance distance) {
        if (distance == Distance.NEAR) {
            mNfclet.onNear(remote.mNfclet);
        }
    }

}
