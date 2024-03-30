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

package com.android.libraries.testing.deviceshadower.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetooth;
import android.bluetooth.OobData;
import android.content.AttributionSource;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;

import com.android.libraries.testing.deviceshadower.Bluelet.CreateBondOutcome;
import com.android.libraries.testing.deviceshadower.Bluelet.FetchUuidsTiming;
import com.android.libraries.testing.deviceshadower.Bluelet.IoCapabilities;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.AdapterDelegate.State;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.BlueletImpl.PairingConfirmation;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.common.base.Preconditions;

import java.util.Random;

/**
 * Implementation of IBluetooth interface.
 */
public class IBluetoothImpl implements IBluetooth {

    private static final Logger LOGGER = Logger.create("BlueletImpl");

    private enum PairingVariant {
        JUST_WORKS,
        /**
         * AKA Passkey Confirmation.
         */
        NUMERIC_COMPARISON,
        PASSKEY_INPUT,
        CONSENT
    }

    /**
     * User will be prompted to accept or deny the incoming pairing request.
     */
    private static final int PAIRING_VARIANT_CONSENT = 3;

    /**
     * User will be prompted to enter the passkey displayed on remote device. This is used for
     * Bluetooth 2.1 pairing.
     */
    private static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;

    public IBluetoothImpl() {
    }

    @Override
    public String getAddress() {
        return localBlueletImpl().address;
    }

    @Override
    public String getName() {
        return localBlueletImpl().mName;
    }

    @Override
    public boolean setName(String name) {
        localBlueletImpl().mName = name;
        return true;
    }

    @Override
    public int getRemoteClass(BluetoothDevice device) {
        return remoteBlueletImpl(device.getAddress()).getAdapterDelegate().getBluetoothClass();
    }

    @Override
    public String getRemoteName(BluetoothDevice device) {
        return remoteBlueletImpl(device.getAddress()).mName;
    }

    @Override
    public int getRemoteType(BluetoothDevice device, AttributionSource attributionSource) {
        return BluetoothDevice.DEVICE_TYPE_LE;
    }

    @Override
    public ParcelUuid[] getRemoteUuids(BluetoothDevice device) {
        return remoteBlueletImpl(device.getAddress()).mProfileUuids;
    }

    @Override
    public boolean fetchRemoteUuids(BluetoothDevice device) {
        localBlueletImpl().onFetchedUuids(device.getAddress(), getRemoteUuids(device));
        return true;
    }

    @Override
    public int getBondState(BluetoothDevice device, AttributionSource attributionSource) {
        return localBlueletImpl().getBondState(device.getAddress());
    }

    @Override
    public boolean createBond(BluetoothDevice device, int transport, OobData remoteP192Data,
            OobData remoteP256Data, AttributionSource attributionSource) {
        setBondState(device.getAddress(), BluetoothDevice.BOND_BONDING, BlueletImpl.REASON_SUCCESS);

        BlueletImpl remoteBluelet = remoteBlueletImpl(device.getAddress());
        BlueletImpl localBluelet = localBlueletImpl();

        // Like the real Bluetooth stack, choose a pairing variant based on IO Capabilities.
        // https://blog.bluetooth.com/bluetooth-pairing-part-2-key-generation-methods
        PairingVariant variant = PairingVariant.JUST_WORKS;
        if (localBluelet.getIoCapabilities() == IoCapabilities.DISPLAY_YES_NO) {
            if (remoteBluelet.getIoCapabilities() == IoCapabilities.DISPLAY_YES_NO) {
                variant = PairingVariant.NUMERIC_COMPARISON;
            } else if (remoteBluelet.getIoCapabilities() == IoCapabilities.KEYBOARD_ONLY) {
                variant = PairingVariant.PASSKEY_INPUT;
            } else if (remoteBluelet.getIoCapabilities() == IoCapabilities.NO_INPUT_NO_OUTPUT
                    && localBluelet.getEnableCVE20192225()) {
                // After CVE-2019-2225, Bluetooth decides to ask consent instead of JustWorks.
                variant = PairingVariant.CONSENT;
            }
        }

        // Bonding doesn't complete until the passkey is confirmed on both devices. The passkey is a
        // positive 6-digit integer, generated by the Bluetooth stack.
        int passkey = new Random().nextInt(999999) + 1;
        switch (variant) {
            case NUMERIC_COMPARISON:
                localBluelet.onPairingRequest(
                        remoteBluelet.address, BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION,
                        passkey);
                remoteBluelet.onPairingRequest(
                        localBluelet.address, BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION,
                        passkey);
                break;
            case JUST_WORKS:
                // Bonding completes immediately, with no PAIRING_REQUEST broadcast.
                finishBonding(device);
                break;
            case PASSKEY_INPUT:
                localBluelet.onPairingRequest(
                        remoteBluelet.address, PAIRING_VARIANT_DISPLAY_PASSKEY, passkey);
                localBluelet.mPassKey = passkey;
                remoteBluelet.onPairingRequest(
                        localBluelet.address, PAIRING_VARIANT_DISPLAY_PASSKEY, passkey);
                break;
            case CONSENT:
                localBluelet.onPairingRequest(remoteBluelet.address,
                        PAIRING_VARIANT_CONSENT, /* key= */ 0);
                if (remoteBluelet.getIoCapabilities() == IoCapabilities.NO_INPUT_NO_OUTPUT) {
                    remoteBluelet.setPairingConfirmation(localBluelet.address,
                            PairingConfirmation.CONFIRMED);
                } else {
                    remoteBluelet.onPairingRequest(
                            localBluelet.address, PAIRING_VARIANT_CONSENT, /* key= */ 0);
                }
                break;
        }
        return true;
    }

    private void finishBonding(BluetoothDevice device) {
        BlueletImpl remoteBluelet = remoteBlueletImpl(device.getAddress());
        finishBonding(
                device, remoteBluelet.getCreateBondOutcome(),
                remoteBluelet.getCreateBondFailureReason());
    }

    private void finishBonding(BluetoothDevice device, CreateBondOutcome outcome,
            int failureReason) {
        switch (outcome) {
            case SUCCESS:
                setBondState(device.getAddress(), BluetoothDevice.BOND_BONDED,
                        BlueletImpl.REASON_SUCCESS);
                break;
            case FAILURE:
                setBondState(device.getAddress(), BluetoothDevice.BOND_NONE, failureReason);
                break;
            case TIMEOUT:
                // Send nothing.
                break;
        }
    }

    @Override
    public boolean setPairingConfirmation(BluetoothDevice device, boolean confirmed,
            AttributionSource attributionSource) {
        localBlueletImpl()
                .setPairingConfirmation(
                        device.getAddress(),
                        confirmed ? PairingConfirmation.CONFIRMED : PairingConfirmation.DENIED);

        PairingConfirmation remoteConfirmation =
                remoteBlueletImpl(device.getAddress()).getPairingConfirmation(
                        localBlueletImpl().address);
        if (confirmed && remoteConfirmation == PairingConfirmation.CONFIRMED) {
            LOGGER.d(String.format("CONFIRMED"));
            finishBonding(device);
        } else if (!confirmed || remoteConfirmation == PairingConfirmation.DENIED) {
            LOGGER.d(String.format("NOT CONFIRMED"));
            finishBonding(device, CreateBondOutcome.FAILURE, BlueletImpl.UNBOND_REASON_AUTH_FAILED);
        }
        return true;
    }

    @Override
    public boolean setPasskey(BluetoothDevice device, int passkey) {
        BlueletImpl remoteBluelet = remoteBlueletImpl(device.getAddress());
        if (passkey == remoteBluelet.mPassKey) {
            finishBonding(device);
        } else {
            finishBonding(device, CreateBondOutcome.FAILURE, BlueletImpl.UNBOND_REASON_AUTH_FAILED);
        }
        return true;
    }

    @Override
    public boolean cancelBondProcess(BluetoothDevice device) {
        finishBonding(device, CreateBondOutcome.FAILURE, BlueletImpl.UNBOND_REASON_AUTH_CANCELED);
        return true;
    }

    @Override
    public boolean removeBond(BluetoothDevice device) {
        setBondState(device.getAddress(), BluetoothDevice.BOND_NONE, BlueletImpl.REASON_SUCCESS);
        return true;
    }

    @Override
    public BluetoothDevice[] getBondedDevices() {
        return localBlueletImpl().getBondedDevices();
    }

    @Override
    public int getAdapterConnectionState() {
        return localBlueletImpl().getAdapterConnectionState();
    }

    @Override
    public int getProfileConnectionState(int profile) {
        return localBlueletImpl().getProfileConnectionState(profile);
    }

    @Override
    public int getPhonebookAccessPermission(BluetoothDevice device) {
        return remoteBlueletImpl(device.getAddress()).mPhonebookAccessPermission;
    }

    @Override
    public boolean setPhonebookAccessPermission(BluetoothDevice device, int value) {
        remoteBlueletImpl(device.getAddress()).mPhonebookAccessPermission = value;
        return true;
    }

    @Override
    public int getMessageAccessPermission(BluetoothDevice device) {
        return remoteBlueletImpl(device.getAddress()).mMessageAccessPermission;
    }

    @Override
    public boolean setMessageAccessPermission(BluetoothDevice device, int value) {
        remoteBlueletImpl(device.getAddress()).mMessageAccessPermission = value;
        return true;
    }

    @Override
    public int getSimAccessPermission(BluetoothDevice device) {
        return remoteBlueletImpl(device.getAddress()).mSimAccessPermission;
    }

    @Override
    public boolean setSimAccessPermission(BluetoothDevice device, int value) {
        remoteBlueletImpl(device.getAddress()).mSimAccessPermission = value;
        return true;
    }

    private static void setBondState(String remoteAddress, int state, int failureReason) {
        BlueletImpl remoteBluelet = remoteBlueletImpl(remoteAddress);

        if (remoteBluelet.getFetchUuidsTiming() == FetchUuidsTiming.BEFORE_BONDING) {
            fetchUuidsOnBondedState(remoteAddress, state);
        }

        remoteBluelet.setBondState(localBlueletImpl().address, state, failureReason);
        localBlueletImpl().setBondState(remoteAddress, state, failureReason);

        if (remoteBluelet.getFetchUuidsTiming() == FetchUuidsTiming.AFTER_BONDING) {
            fetchUuidsOnBondedState(remoteAddress, state);
        }
    }

    private static void fetchUuidsOnBondedState(String remoteAddress, int state) {
        if (state == BluetoothDevice.BOND_BONDED) {
            remoteBlueletImpl(remoteAddress)
                    .onFetchedUuids(localBlueletImpl().address, localBlueletImpl().mProfileUuids);
            localBlueletImpl()
                    .onFetchedUuids(remoteAddress, remoteBlueletImpl(remoteAddress).mProfileUuids);
        }
    }

    @Override
    public int getScanMode() {
        return localBlueletImpl().getAdapterDelegate().getScanMode();
    }

    @Override
    public boolean setScanMode(int mode, int duration) {
        localBlueletImpl().getAdapterDelegate().setScanMode(mode);
        return true;
    }

    @Override
    public int getDiscoverableTimeout() {
        return -1;
    }

    @Override
    public boolean setDiscoverableTimeout(int timeout) {
        return true;
    }

    @Override
    public boolean startDiscovery() {
        localBlueletImpl().getAdapterDelegate().startDiscovery();
        return true;
    }

    @Override
    public boolean cancelDiscovery() {
        localBlueletImpl().getAdapterDelegate().cancelDiscovery();
        return true;
    }

    @Override
    public boolean isDiscovering() {
        return localBlueletImpl().getAdapterDelegate().isDiscovering();

    }

    @Override
    public boolean isEnabled() {
        return localBlueletImpl().getAdapterDelegate().getState().equals(State.ON);
    }

    @Override
    public int getState() {
        return localBlueletImpl().getAdapterDelegate().getState().getValue();
    }

    @Override
    public boolean enable() {
        localBlueletImpl().enableAdapter();
        return true;
    }

    @Override
    public boolean disable() {
        localBlueletImpl().disableAdapter();
        return true;
    }

    @Override
    public ParcelFileDescriptor connectSocket(BluetoothDevice device, int type, ParcelUuid uuid,
            int port, int flag) {
        Preconditions.checkArgument(
                port == BluetoothConstants.SOCKET_CHANNEL_CONNECT_WITH_UUID,
                "Connect to port is not supported.");
        Preconditions.checkArgument(
                type == BluetoothConstants.TYPE_RFCOMM,
                "Only Rfcomm socket is supported.");
        return localBlueletImpl().getRfcommDelegate()
                .connectSocket(device.getAddress(), uuid.getUuid());
    }

    @Override
    public ParcelFileDescriptor createSocketChannel(int type, String serviceName, ParcelUuid uuid,
            int port, int flag) {
        Preconditions.checkArgument(
                port == BluetoothConstants.SERVER_SOCKET_CHANNEL_AUTO_ASSIGN,
                "Listen on port is not supported.");
        Preconditions.checkArgument(
                type == BluetoothConstants.TYPE_RFCOMM,
                "Only Rfcomm socket is supported.");
        return localBlueletImpl().getRfcommDelegate().createSocketChannel(serviceName, uuid);
    }

    @Override
    public boolean isMultiAdvertisementSupported() {
        return maxAdvertiseInstances() > 1;
    }

    @Override
    public boolean isPeripheralModeSupported() {
        return maxAdvertiseInstances() > 0;
    }

    private int maxAdvertiseInstances() {
        return localBlueletImpl().getGattDelegate().getMaxAdvertiseInstances();
    }

    @Override
    public boolean isOffloadedFilteringSupported() {
        return localBlueletImpl().getGattDelegate().isOffloadedFilteringSupported();
    }

    private static BlueletImpl localBlueletImpl() {
        return DeviceShadowEnvironmentImpl.getLocalBlueletImpl();
    }

    private static BlueletImpl remoteBlueletImpl(String address) {
        return DeviceShadowEnvironmentImpl.getBlueletImpl(address);
    }
}
