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
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.android.server.nearby.common.bluetooth.fastpair.FastPairConnection;
import com.android.server.nearby.common.locator.Locator;
import com.android.server.nearby.fastpair.cache.DiscoveryItem;
import com.android.server.nearby.fastpair.footprint.FootprintsDeviceManager;
import com.android.server.nearby.fastpair.halfsheet.FastPairHalfSheetManager;
import com.android.server.nearby.intdefs.NearbyEventIntDefs;

/** Pairing progress handler that handle pairing come from half sheet. */
public final class HalfSheetPairingProgressHandler extends PairingProgressHandlerBase {

    private final FastPairHalfSheetManager mFastPairHalfSheetManager;
    private final boolean mIsSubsequentPair;
    private final DiscoveryItem mItemResurface;

    HalfSheetPairingProgressHandler(
            Context context,
            DiscoveryItem item,
            @Nullable String companionApp,
            @Nullable byte[] accountKey) {
        super(context, item);
        this.mFastPairHalfSheetManager = Locator.get(context, FastPairHalfSheetManager.class);
        this.mIsSubsequentPair =
                item.getAuthenticationPublicKeySecp256R1() != null && accountKey != null;
        this.mItemResurface = item;
    }

    @Override
    protected int getPairStartEventCode() {
        return mIsSubsequentPair ? NearbyEventIntDefs.EventCode.SUBSEQUENT_PAIR_START
                : NearbyEventIntDefs.EventCode.MAGIC_PAIR_START;
    }

    @Override
    protected int getPairEndEventCode() {
        return mIsSubsequentPair ? NearbyEventIntDefs.EventCode.SUBSEQUENT_PAIR_END
                : NearbyEventIntDefs.EventCode.MAGIC_PAIR_END;
    }

    @Override
    public void onPairingStarted() {
        super.onPairingStarted();
        // Half sheet is not in the foreground reshow half sheet, also avoid showing HalfSheet on TV
        if (!mFastPairHalfSheetManager.getHalfSheetForegroundState()) {
            mFastPairHalfSheetManager.showPairingHalfSheet(mItemResurface);
        }
        mFastPairHalfSheetManager.disableDismissRunnable();
    }

    @Override
    public void onHandlePasskeyConfirmation(BluetoothDevice device, int passkey) {
        super.onHandlePasskeyConfirmation(device, passkey);
        mFastPairHalfSheetManager.showPasskeyConfirmation(device, passkey);
    }

    @Nullable
    @Override
    public String onPairedCallbackCalled(
            FastPairConnection connection,
            byte[] accountKey,
            FootprintsDeviceManager footprints,
            String address) {
        String deviceName = super.onPairedCallbackCalled(connection, accountKey,
                footprints, address);
        mFastPairHalfSheetManager.showPairingSuccessHalfSheet(address);
        mFastPairHalfSheetManager.disableDismissRunnable();
        return deviceName;
    }

    @Override
    public void onPairingFailed(Throwable throwable) {
        super.onPairingFailed(throwable);
        mFastPairHalfSheetManager.disableDismissRunnable();
        mFastPairHalfSheetManager.showPairingFailed();
        mFastPairHalfSheetManager.notifyPairingProcessDone(
                /* success= */ false, /* publicAddress= */ null, mItem);
        // fix auto rebond issue
        mFastPairHalfSheetManager.destroyBluetoothPairController();
    }

    @Override
    public void onPairingSuccess(String address) {
        super.onPairingSuccess(address);
        mFastPairHalfSheetManager.disableDismissRunnable();
        mFastPairHalfSheetManager
                .notifyPairingProcessDone(/* success= */ true, address, mItem);
        mFastPairHalfSheetManager.destroyBluetoothPairController();
    }
}

