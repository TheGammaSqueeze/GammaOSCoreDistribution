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

package com.android.server.nearby.common.bluetooth.fastpair;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothDevice.ERROR;
import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.google.common.base.Strings;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Pairs to Bluetooth classic devices with passkey confirmation.
 */
// TODO(b/202524672): Add class unit test.
public class BluetoothClassicPairer {

    private static final String TAG = BluetoothClassicPairer.class.getSimpleName();
    /**
     * Hidden, see {@link BluetoothDevice}.
     */
    private static final String EXTRA_REASON = "android.bluetooth.device.extra.REASON";

    private final Context mContext;
    private final BluetoothDevice mDevice;
    private final Preferences mPreferences;
    private final PasskeyConfirmationHandler mPasskeyConfirmationHandler;

    public BluetoothClassicPairer(
            Context context,
            BluetoothDevice device,
            Preferences preferences,
            PasskeyConfirmationHandler passkeyConfirmationHandler) {
        this.mContext = context;
        this.mDevice = device;
        this.mPreferences = preferences;
        this.mPasskeyConfirmationHandler = passkeyConfirmationHandler;
    }

    /**
     * Pairs with the device. Throws a {@link PairingException} if any error occurs.
     */
    @WorkerThread
    public void pair() throws PairingException {
        Log.i(TAG, "BluetoothClassicPairer, createBond with " + maskBluetoothAddress(mDevice)
                + ", type=" + mDevice.getType());
        try (BondedReceiver bondedReceiver = new BondedReceiver()) {
            if (mDevice.createBond()) {
                bondedReceiver.await(mPreferences.getCreateBondTimeoutSeconds(), SECONDS);
            } else {
                throw new PairingException(
                        "BluetoothClassicPairer, createBond got immediate error");
            }
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new PairingException("BluetoothClassicPairer, createBond failed", e);
        }
    }

    protected boolean isPaired() {
        return mDevice.getBondState() == BOND_BONDED;
    }

    /**
     * Receiver that closes after bonding has completed.
     */
    private class BondedReceiver extends DeviceIntentReceiver {

        private BondedReceiver() {
            super(
                    mContext,
                    mPreferences,
                    mDevice,
                    BluetoothDevice.ACTION_PAIRING_REQUEST,
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        }

        /**
         * Called with ACTION_PAIRING_REQUEST and ACTION_BOND_STATE_CHANGED about the interesting
         * device (see {@link DeviceIntentReceiver}).
         *
         * <p>The ACTION_PAIRING_REQUEST intent provides the passkey which will be sent to the
         * {@link PasskeyConfirmationHandler} for showing the UI, and the ACTION_BOND_STATE_CHANGED
         * will provide the result of the bonding.
         */
        @Override
        protected void onReceiveDeviceIntent(Intent intent) {
            String intentAction = intent.getAction();
            BluetoothDevice remoteDevice = intent.getParcelableExtra(EXTRA_DEVICE);
            if (Strings.isNullOrEmpty(intentAction)
                    || remoteDevice == null
                    || !remoteDevice.getAddress().equals(mDevice.getAddress())) {
                Log.w(TAG,
                        "BluetoothClassicPairer, receives " + intentAction
                                + " from unexpected device " + maskBluetoothAddress(remoteDevice));
                return;
            }
            switch (intentAction) {
                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    handlePairingRequest(
                            remoteDevice,
                            intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, ERROR),
                            intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, ERROR));
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    handleBondStateChanged(
                            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, ERROR),
                            intent.getIntExtra(EXTRA_REASON, ERROR));
                    break;
                default:
                    break;
            }
        }

        private void handlePairingRequest(BluetoothDevice device, int variant, int passkey) {
            Log.i(TAG,
                    "BluetoothClassicPairer, pairing request, " + device + ", " + variant + ", "
                            + passkey);
            // Prevent Bluetooth Settings from getting the pairing request and showing its own UI.
            abortBroadcast();
            mPasskeyConfirmationHandler.onPasskeyConfirmation(device, passkey);
        }

        private void handleBondStateChanged(int bondState, int reason) {
            Log.i(TAG,
                    "BluetoothClassicPairer, bond state changed to " + bondState + ", reason="
                            + reason);
            switch (bondState) {
                case BOND_BONDING:
                    // Don't close!
                    return;
                case BOND_BONDED:
                    close();
                    return;
                case BOND_NONE:
                default:
                    closeWithError(
                            new PairingException(
                                    "BluetoothClassicPairer, createBond failed, reason:" + reason));
            }
        }
    }

    // Applies UsesPermission annotation will create circular dependency.
    @SuppressLint("MissingPermission")
    static void setPairingConfirmation(BluetoothDevice device, boolean confirm) {
        Log.i(TAG, "BluetoothClassicPairer: setPairingConfirmation " + maskBluetoothAddress(device)
                + ", confirm: " + confirm);
        device.setPairingConfirmation(confirm);
    }
}
