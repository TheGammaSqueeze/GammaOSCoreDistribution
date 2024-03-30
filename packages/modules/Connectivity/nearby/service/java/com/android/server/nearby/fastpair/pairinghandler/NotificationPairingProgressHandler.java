/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.nearby.fastpair.pairinghandler;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.server.nearby.common.bluetooth.fastpair.FastPairConnection;
import com.android.server.nearby.fastpair.cache.DiscoveryItem;
import com.android.server.nearby.fastpair.footprint.FootprintsDeviceManager;
import com.android.server.nearby.fastpair.notification.FastPairNotificationManager;
import com.android.server.nearby.intdefs.NearbyEventIntDefs;

/** Pairing progress handler for pairing coming from notifications. */
@SuppressWarnings("nullness")
public class NotificationPairingProgressHandler extends PairingProgressHandlerBase {
    private final FastPairNotificationManager mFastPairNotificationManager;
    @Nullable
    private final String mCompanionApp;
    @Nullable
    private final byte[] mAccountKey;
    private final boolean mIsSubsequentPair;

    NotificationPairingProgressHandler(
            Context context,
            DiscoveryItem item,
            @Nullable String companionApp,
            @Nullable byte[] accountKey,
            FastPairNotificationManager mFastPairNotificationManager) {
        super(context, item);
        this.mFastPairNotificationManager = mFastPairNotificationManager;
        this.mCompanionApp = companionApp;
        this.mAccountKey = accountKey;
        this.mIsSubsequentPair =
                item.getAuthenticationPublicKeySecp256R1() != null && accountKey != null;
    }

    @Override
    public int getPairStartEventCode() {
        return mIsSubsequentPair ? NearbyEventIntDefs.EventCode.SUBSEQUENT_PAIR_START
                : NearbyEventIntDefs.EventCode.MAGIC_PAIR_START;
    }

    @Override
    public int getPairEndEventCode() {
        return mIsSubsequentPair ? NearbyEventIntDefs.EventCode.SUBSEQUENT_PAIR_END
                : NearbyEventIntDefs.EventCode.MAGIC_PAIR_END;
    }

    @Override
    public void onReadyToPair() {
        super.onReadyToPair();
        mFastPairNotificationManager.showConnectingNotification();
    }

    @Override
    public String onPairedCallbackCalled(
            FastPairConnection connection,
            byte[] accountKey,
            FootprintsDeviceManager footprints,
            String address) {
        String deviceName = super.onPairedCallbackCalled(connection, accountKey, footprints,
                address);

        int batteryLevel = -1;

        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter != null) {
            // Need to check battery level here set that to -1 for now
            batteryLevel = -1;
        } else {
            Log.v(
                    "NotificationPairingProgressHandler",
                    "onPairedCallbackCalled getBatteryLevel failed,"
                            + " adapter is null");
        }
        mFastPairNotificationManager.showPairingSucceededNotification(
                !TextUtils.isEmpty(mCompanionApp) ? mCompanionApp : null,
                batteryLevel,
                deviceName,
                address);
        return deviceName;
    }

    @Override
    public void onPairingFailed(Throwable throwable) {
        super.onPairingFailed(throwable);
        mFastPairNotificationManager.showPairingFailedNotification(mAccountKey);
        mFastPairNotificationManager.notifyPairingProcessDone(
                /* success= */ false,
                /* forceNotify= */ false,
                /* privateAddress= */ mItem.getMacAddress(),
                /* publicAddress= */ null);
    }

    @Override
    public void onPairingSuccess(String address) {
        super.onPairingSuccess(address);
        mFastPairNotificationManager.notifyPairingProcessDone(
                /* success= */ true,
                /* forceNotify= */ false,
                /* privateAddress= */ mItem.getMacAddress(),
                /* publicAddress= */ address);
    }
}

