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

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;
import static com.android.server.nearby.fastpair.FastPairManager.isThroughFastPair2InitialPairing;

import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.android.server.nearby.common.bluetooth.fastpair.FastPairConnection;
import com.android.server.nearby.common.bluetooth.fastpair.Preferences;
import com.android.server.nearby.fastpair.cache.DiscoveryItem;
import com.android.server.nearby.fastpair.footprint.FootprintsDeviceManager;
import com.android.server.nearby.fastpair.halfsheet.FastPairHalfSheetManager;
import com.android.server.nearby.fastpair.notification.FastPairNotificationManager;
import com.android.server.nearby.intdefs.FastPairEventIntDefs;

/** Base class for pairing progress handler. */
public abstract class PairingProgressHandlerBase {
    protected final Context mContext;
    protected final DiscoveryItem mItem;
    @Nullable
    private FastPairEventIntDefs.ErrorCode mRescueFromError;

    protected abstract int getPairStartEventCode();

    protected abstract int getPairEndEventCode();

    protected PairingProgressHandlerBase(Context context, DiscoveryItem item) {
        this.mContext = context;
        this.mItem = item;
    }


    /**
     * Pairing progress init function.
     */
    public static PairingProgressHandlerBase create(
            Context context,
            DiscoveryItem item,
            @Nullable String companionApp,
            @Nullable byte[] accountKey,
            FootprintsDeviceManager footprints,
            FastPairNotificationManager notificationManager,
            FastPairHalfSheetManager fastPairHalfSheetManager,
            boolean isRetroactivePair) {
        PairingProgressHandlerBase pairingProgressHandlerBase;
        // Disable half sheet on subsequent pairing
        if (item.getAuthenticationPublicKeySecp256R1() != null
                && accountKey != null) {
            // Subsequent pairing
            pairingProgressHandlerBase =
                    new NotificationPairingProgressHandler(
                            context, item, companionApp, accountKey, notificationManager);
        } else {
            pairingProgressHandlerBase =
                    new HalfSheetPairingProgressHandler(context, item, companionApp, accountKey);
        }


        Log.v("PairingHandler",
                "PairingProgressHandler:Create "
                        + item.getMacAddress() + " for pairing");
        return pairingProgressHandlerBase;
    }


    /**
     * Function calls when pairing start.
     */
    public void onPairingStarted() {
        Log.v("PairingHandler", "PairingProgressHandler:onPairingStarted");
    }

    /**
     * Waits for screen to unlock.
     */
    public void onWaitForScreenUnlock() {
        Log.v("PairingHandler", "PairingProgressHandler:onWaitForScreenUnlock");
    }

    /**
     * Function calls when screen unlock.
     */
    public void onScreenUnlocked() {
        Log.v("PairingHandler", "PairingProgressHandler:onScreenUnlocked");
    }

    /**
     * Calls when the handler is ready to pair.
     */
    public void onReadyToPair() {
        Log.v("PairingHandler", "PairingProgressHandler:onReadyToPair");
    }

    /**
     * Helps to set up pairing preference.
     */
    public void onSetupPreferencesBuilder(Preferences.Builder builder) {
        Log.v("PairingHandler", "PairingProgressHandler:onSetupPreferencesBuilder");
    }

    /**
     * Calls when pairing setup complete.
     */
    public void onPairingSetupCompleted() {
        Log.v("PairingHandler", "PairingProgressHandler:onPairingSetupCompleted");
    }

    /** Called while pairing if needs to handle the passkey confirmation by Ui. */
    public void onHandlePasskeyConfirmation(BluetoothDevice device, int passkey) {
        Log.v("PairingHandler", "PairingProgressHandler:onHandlePasskeyConfirmation");
    }

    /**
     * In this callback, we know if it is a real initial pairing by existing account key, and do
     * following things:
     * <li>1, optIn footprint for initial pairing.
     * <li>2, write the device name to provider
     * <li>2.1, generate default personalized name for initial pairing or get the personalized name
     * from footprint for subsequent pairing.
     * <li>2.2, set alias name for the bluetooth device.
     * <li>2.3, update the device name for connection to write into provider for initial pair.
     * <li>3, suppress battery notifications until oobe finishes.
     *
     * @return display name of the pairing device
     */
    @Nullable
    public String onPairedCallbackCalled(
            FastPairConnection connection,
            byte[] accountKey,
            FootprintsDeviceManager footprints,
            String address) {
        Log.v("PairingHandler",
                "PairingProgressHandler:onPairedCallbackCalled with address: "
                        + address);

        byte[] existingAccountKey = connection.getExistingAccountKey();
        optInFootprintsForInitialPairing(footprints, mItem, accountKey, existingAccountKey);
        // Add support for naming the device
        return null;
    }

    /**
     * Gets the related info from db use account key.
     */
    @Nullable
    public byte[] getKeyForLocalCache(
            byte[] accountKey, FastPairConnection connection,
            FastPairConnection.SharedSecret sharedSecret) {
        Log.v("PairingHandler", "PairingProgressHandler:getKeyForLocalCache");
        return accountKey != null ? accountKey : connection.getExistingAccountKey();
    }

    /**
     * Function handles pairing fail.
     */
    public void onPairingFailed(Throwable throwable) {
        Log.w("PairingHandler", "PairingProgressHandler:onPairingFailed");
    }

    /**
     * Function handles pairing success.
     */
    public void onPairingSuccess(String address) {
        Log.v("PairingHandler", "PairingProgressHandler:onPairingSuccess with address: "
                + maskBluetoothAddress(address));
    }

    private static void optInFootprintsForInitialPairing(
            FootprintsDeviceManager footprints,
            DiscoveryItem item,
            byte[] accountKey,
            @Nullable byte[] existingAccountKey) {
        if (isThroughFastPair2InitialPairing(item, accountKey) && existingAccountKey == null) {
            // enable the save to footprint
            Log.v("PairingHandler", "footprint should call opt in here");
        }
    }

    /**
     * Returns {@code true} if the PairingProgressHandler is running at the background.
     *
     * <p>In order to keep the following status notification shows as a heads up, we must wait for
     * the screen unlocked to continue.
     */
    public boolean skipWaitingScreenUnlock() {
        return false;
    }
}

