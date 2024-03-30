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

import static org.robolectric.util.ReflectionHelpers.callConstructor;

import android.Manifest.permission;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothManager;
import android.content.AttributionSource;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.ParcelUuid;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.Bluelet;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.AdapterDelegate.Event;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.AdapterDelegate.State;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.connection.RfcommDelegate;
import com.android.libraries.testing.deviceshadower.internal.common.BroadcastManager;
import com.android.libraries.testing.deviceshadower.internal.common.Interrupter;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A container class of a real-world Bluetooth device.
 */
public class BlueletImpl implements Bluelet {

    enum PairingConfirmation {
        UNKNOWN,
        CONFIRMED,
        DENIED
    }

    /**
     * See hidden {@link #EXTRA_REASON} and reason values in {@link BluetoothDevice}.
     */
    static final int REASON_SUCCESS = 0;
    /**
     * See hidden {@link #EXTRA_REASON} and reason values in {@link BluetoothDevice}.
     */
    static final int UNBOND_REASON_AUTH_FAILED = 1;
    /**
     * See hidden {@link #EXTRA_REASON} and reason values in {@link BluetoothDevice}.
     */
    static final int UNBOND_REASON_AUTH_CANCELED = 3;

    /**
     * Hidden in {@link BluetoothDevice}.
     */
    private static final String EXTRA_REASON = "android.bluetooth.device.extra.REASON";

    private static final Logger LOGGER = Logger.create("BlueletImpl");

    private static final ImmutableMap<Integer, Integer> PROFILE_STATE_TO_ADAPTER_STATE =
            ImmutableMap.<Integer, Integer>builder()
                    .put(BluetoothProfile.STATE_CONNECTED, BluetoothAdapter.STATE_CONNECTED)
                    .put(BluetoothProfile.STATE_CONNECTING, BluetoothAdapter.STATE_CONNECTING)
                    .put(BluetoothProfile.STATE_DISCONNECTING, BluetoothAdapter.STATE_DISCONNECTING)
                    .put(BluetoothProfile.STATE_DISCONNECTED, BluetoothAdapter.STATE_DISCONNECTED)
                    .build();

    public static void reset() {
        RfcommDelegate.reset();
    }

    public final String address;
    String mName;
    ParcelUuid[] mProfileUuids = new ParcelUuid[0];
    int mPhonebookAccessPermission;
    int mMessageAccessPermission;
    int mSimAccessPermission;
    final BluetoothAdapter mAdapter;
    int mPassKey;

    private CreateBondOutcome mCreateBondOutcome = CreateBondOutcome.SUCCESS;
    private int mCreateBondFailureReason;
    private IoCapabilities mIoCapabilities = IoCapabilities.NO_INPUT_NO_OUTPUT;
    private boolean mRefuseConnections;
    private FetchUuidsTiming mFetchUuidsTiming = FetchUuidsTiming.AFTER_BONDING;
    private boolean mEnableCVE20192225;

    private final Interrupter mInterrupter;
    private final AdapterDelegate mAdapterDelegate;
    private final RfcommDelegate mRfcommDelegate;
    private final GattDelegate mGattDelegate;
    private final BluetoothBroadcastHandler mBluetoothBroadcastHandler;
    private final Map<String, Integer> mRemoteAddressToBondState = new HashMap<>();
    private final Map<String, PairingConfirmation> mRemoteAddressToPairingConfirmation =
            new HashMap<>();
    private final Map<Integer, Integer> mProfileTypeToConnectionState = new HashMap<>();
    private final Set<BluetoothDevice> mBondedDevices = new HashSet<>();

    public BlueletImpl(String address, BroadcastManager broadcastManager) {
        this.address = address;
        this.mName = address;
        this.mAdapter = callConstructor(BluetoothAdapter.class,
                ClassParameter.from(IBluetoothManager.class, new IBluetoothManagerImpl()),
                ClassParameter.from(AttributionSource.class,
                        AttributionSource.myAttributionSource()));
        mBluetoothBroadcastHandler = new BluetoothBroadcastHandler(broadcastManager);
        mInterrupter = new Interrupter();
        mAdapterDelegate = new AdapterDelegate(address, mBluetoothBroadcastHandler);
        mRfcommDelegate = new RfcommDelegate(address, mBluetoothBroadcastHandler, mInterrupter);
        mGattDelegate = new GattDelegate(address);
    }

    @Override
    public Bluelet setAdapterInitialState(int state) throws IllegalArgumentException {
        LOGGER.d(String.format("Address: %s, setAdapterInitialState(%d)", address, state));
        Preconditions.checkArgument(
                state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_ON,
                "State must be BluetoothAdapter.STATE_ON or BluetoothAdapter.STATE_OFF.");
        mAdapterDelegate.setState(State.lookup(state));
        return this;
    }

    @Override
    public Bluelet setBluetoothClass(int bluetoothClass) {
        mAdapterDelegate.setBluetoothClass(bluetoothClass);
        return this;
    }

    @Override
    public Bluelet setScanMode(int scanMode) {
        mAdapterDelegate.setScanMode(scanMode);
        return this;
    }

    @Override
    public Bluelet setProfileUuids(ParcelUuid... profileUuids) {
        this.mProfileUuids = profileUuids;
        return this;
    }

    @Override
    public Bluelet setIoCapabilities(IoCapabilities ioCapabilities) {
        this.mIoCapabilities = ioCapabilities;
        return this;
    }

    @Override
    public Bluelet setCreateBondOutcome(CreateBondOutcome outcome, int failureReason) {
        mCreateBondOutcome = outcome;
        mCreateBondFailureReason = failureReason;
        return this;
    }

    @Override
    public Bluelet setRefuseConnections(boolean refuse) {
        mRefuseConnections = refuse;
        return this;
    }

    @Override
    public Bluelet setRefuseGattConnections(boolean refuse) {
        getGattDelegate().setRefuseConnections(refuse);
        return this;
    }

    @Override
    public Bluelet setFetchUuidsTiming(FetchUuidsTiming fetchUuidsTiming) {
        this.mFetchUuidsTiming = fetchUuidsTiming;
        return this;
    }

    @Override
    public Bluelet addBondedDevice(String address) {
        this.mBondedDevices.add(mAdapter.getRemoteDevice(address));
        return this;
    }

    @Override
    public Bluelet enableCVE20192225(boolean value) {
        this.mEnableCVE20192225 = value;
        return this;
    }

    IoCapabilities getIoCapabilities() {
        return mIoCapabilities;
    }

    CreateBondOutcome getCreateBondOutcome() {
        return mCreateBondOutcome;
    }

    int getCreateBondFailureReason() {
        return mCreateBondFailureReason;
    }

    public boolean getRefuseConnections() {
        return mRefuseConnections;
    }

    public FetchUuidsTiming getFetchUuidsTiming() {
        return mFetchUuidsTiming;
    }

    BluetoothDevice[] getBondedDevices() {
        return mBondedDevices.toArray(new BluetoothDevice[0]);
    }

    public boolean getEnableCVE20192225() {
        return mEnableCVE20192225;
    }

    public void enableAdapter() {
        LOGGER.d(String.format("Address: %s, enableAdapter()", address));
        // TODO(b/200231384): async enabling, configurable delay, failure path
        if (VERSION.SDK_INT < 23) {
            mAdapterDelegate.processEvent(Event.USER_TURN_ON);
            mAdapterDelegate.processEvent(Event.BREDR_STARTED);
        } else {
            mAdapterDelegate.processEvent(Event.BLE_TURN_ON);
            mAdapterDelegate.processEvent(Event.BLE_STARTED);
            mAdapterDelegate.processEvent(Event.USER_TURN_ON);
            mAdapterDelegate.processEvent(Event.BREDR_STARTED);
        }
    }

    public void disableAdapter() {
        LOGGER.d(String.format("Address: %s, disableAdapter()", address));
        // TODO(b/200231384): async disabling, configurable delay, failure path
        if (VERSION.SDK_INT < 23) {
            mAdapterDelegate.processEvent(Event.USER_TURN_OFF);
            mAdapterDelegate.processEvent(Event.BREDR_STOPPED);
        } else {
            mAdapterDelegate.processEvent(Event.BLE_TURN_OFF);
            mAdapterDelegate.processEvent(Event.BREDR_STOPPED);
            mAdapterDelegate.processEvent(Event.USER_TURN_OFF);
            mAdapterDelegate.processEvent(Event.BLE_STOPPED);
        }
    }

    public AdapterDelegate getAdapterDelegate() {
        return mAdapterDelegate;
    }

    public RfcommDelegate getRfcommDelegate() {
        return mRfcommDelegate;
    }

    public GattDelegate getGattDelegate() {
        return mGattDelegate;
    }

    public BluetoothAdapter getAdapter() {
        return mAdapter;
    }

    public void setInterruptible(int identifier) {
        LOGGER.d(String.format("Address: %s, setInterruptible(%d)", address, identifier));
        mInterrupter.setInterruptible(identifier);
    }

    public void interrupt(int identifier) {
        LOGGER.d(String.format("Address: %s, interrupt(%d)", address, identifier));
        mInterrupter.interrupt(identifier);
    }

    @VisibleForTesting
    public void setAdapterState(int state) throws IllegalArgumentException {
        State s = State.lookup(state);
        if (s == null) {
            throw new IllegalArgumentException();
        }
        mAdapterDelegate.setState(s);
    }

    public int getBondState(String remoteAddress) {
        return mRemoteAddressToBondState.containsKey(remoteAddress)
                ? mRemoteAddressToBondState.get(remoteAddress)
                : BluetoothDevice.BOND_NONE;
    }

    public void setBondState(String remoteAddress, int bondState, int failureReason) {
        Intent intent =
                newDeviceIntent(BluetoothDevice.ACTION_BOND_STATE_CHANGED, remoteAddress)
                        .putExtra(BluetoothDevice.EXTRA_BOND_STATE, bondState)
                        .putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                getBondState(remoteAddress));

        if (failureReason != REASON_SUCCESS) {
            intent.putExtra(EXTRA_REASON, failureReason);
        }

        LOGGER.d(
                String.format(
                        "Address: %s, Bluetooth Bond State Change Intent: remote=%s, %s -> %s "
                                + "(reason=%s)",
                        address, remoteAddress, getBondState(remoteAddress), bondState,
                        failureReason));
        mRemoteAddressToBondState.put(remoteAddress, bondState);
        mBluetoothBroadcastHandler.mBroadcastManager.sendBroadcast(
                intent, android.Manifest.permission.BLUETOOTH);
    }

    public void onPairingRequest(String remoteAddress, int variant, int key) {
        Intent intent =
                newDeviceIntent(BluetoothDevice.ACTION_PAIRING_REQUEST, remoteAddress)
                        .putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, variant)
                        .putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, key);

        LOGGER.d(
                String.format(
                        "Address: %s, Bluetooth Pairing Request Intent: remote=%s, variant=%s, "
                                + "key=%s", address, remoteAddress, variant, key));
        mBluetoothBroadcastHandler.mBroadcastManager.sendBroadcast(intent, permission.BLUETOOTH);
    }

    public PairingConfirmation getPairingConfirmation(String remoteAddress) {
        PairingConfirmation confirmation = mRemoteAddressToPairingConfirmation.get(remoteAddress);
        return confirmation == null ? PairingConfirmation.UNKNOWN : confirmation;
    }

    public void setPairingConfirmation(String remoteAddress, PairingConfirmation confirmation) {
        mRemoteAddressToPairingConfirmation.put(remoteAddress, confirmation);
    }

    public void onFetchedUuids(String remoteAddress, ParcelUuid[] profileUuids) {
        Intent intent =
                newDeviceIntent(BluetoothDevice.ACTION_UUID, remoteAddress)
                        .putExtra(BluetoothDevice.EXTRA_UUID, profileUuids);

        LOGGER.d(
                String.format(
                        "Address: %s, Bluetooth Found UUIDs Intent: remoteAddress=%s, uuids=%s",
                        address, remoteAddress, Arrays.toString(profileUuids)));
        mBluetoothBroadcastHandler.mBroadcastManager.sendBroadcast(
                intent, android.Manifest.permission.BLUETOOTH);
    }

    private static int maxProfileState(int a, int b) {
        // Prefer connected > connecting > disconnecting > disconnected.
        switch (a) {
            case BluetoothProfile.STATE_CONNECTED:
                return a;
            case BluetoothProfile.STATE_CONNECTING:
                return b == BluetoothProfile.STATE_CONNECTED ? b : a;
            case BluetoothProfile.STATE_DISCONNECTING:
                return b == BluetoothProfile.STATE_CONNECTED
                        || b == BluetoothProfile.STATE_CONNECTING
                        ? b
                        : a;
            case BluetoothProfile.STATE_DISCONNECTED:
            default:
                return b;
        }
    }

    public int getAdapterConnectionState() {
        int maxState = BluetoothProfile.STATE_DISCONNECTED;
        for (int state : mProfileTypeToConnectionState.values()) {
            maxState = maxProfileState(maxState, state);
        }
        return PROFILE_STATE_TO_ADAPTER_STATE.get(maxState);
    }

    public int getProfileConnectionState(int profileType) {
        return mProfileTypeToConnectionState.containsKey(profileType)
                ? mProfileTypeToConnectionState.get(profileType)
                : BluetoothProfile.STATE_DISCONNECTED;
    }

    public void setProfileConnectionState(int profileType, int state, String remoteAddress) {
        int previousAdapterState = getAdapterConnectionState();
        mProfileTypeToConnectionState.put(profileType, state);
        int adapterState = getAdapterConnectionState();
        if (previousAdapterState != adapterState) {
            Intent intent =
                    newDeviceIntent(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED, remoteAddress)
                            .putExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE,
                                    previousAdapterState)
                            .putExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, adapterState);

            LOGGER.d(
                    "Adapter Connection State Changed Intent: "
                            + previousAdapterState
                            + " -> "
                            + adapterState);
            mBluetoothBroadcastHandler.mBroadcastManager.sendBroadcast(
                    intent, android.Manifest.permission.BLUETOOTH);
        }
    }

    static class BluetoothBroadcastHandler implements AdapterDelegate.Callback,
            RfcommDelegate.Callback {

        private final BroadcastManager mBroadcastManager;

        BluetoothBroadcastHandler(BroadcastManager broadcastManager) {
            this.mBroadcastManager = broadcastManager;
        }

        @Override
        public void onAdapterStateChange(State prevState, State newState) {
            int prev = prevState.getValue();
            int cur = newState.getValue();
            LOGGER.d("Bluetooth State Change Intent: " + State.lookup(prev) + " -> " + State.lookup(
                    cur));
            Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prev);
            intent.putExtra(BluetoothAdapter.EXTRA_STATE, cur);
            mBroadcastManager.sendBroadcast(intent, android.Manifest.permission.BLUETOOTH);
        }

        @Override
        public void onBleStateChange(State prevState, State newState) {
            int prev = prevState.getValue();
            int cur = newState.getValue();
            LOGGER.d("BLE State Change Intent: " + State.lookup(prev) + " -> " + State.lookup(cur));
            Intent intent = new Intent(BluetoothConstants.ACTION_BLE_STATE_CHANGED);
            intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prev);
            intent.putExtra(BluetoothAdapter.EXTRA_STATE, cur);
            mBroadcastManager.sendBroadcast(intent, android.Manifest.permission.BLUETOOTH);
        }

        @Override
        public void onConnectionStateChange(String remoteAddress, boolean isConnected) {
            LOGGER.d("Bluetooth Connection State Change Intent, isConnected: " + isConnected);
            Intent intent =
                    isConnected
                            ? newDeviceIntent(BluetoothDevice.ACTION_ACL_CONNECTED, remoteAddress)
                            : newDeviceIntent(BluetoothDevice.ACTION_ACL_DISCONNECTED,
                                    remoteAddress);
            mBroadcastManager.sendBroadcast(intent, android.Manifest.permission.BLUETOOTH);
        }

        @Override
        public void onDiscoveryStarted() {
            LOGGER.d("Bluetooth discovery started.");
            Intent intent = new Intent(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            mBroadcastManager.sendBroadcast(intent, android.Manifest.permission.BLUETOOTH);
        }

        @Override
        public void onDiscoveryFinished() {
            LOGGER.d("Bluetooth discovery finished.");
            Intent intent = new Intent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            mBroadcastManager.sendBroadcast(intent, android.Manifest.permission.BLUETOOTH);
        }

        @Override
        public void onDeviceFound(String address, int bluetoothClass, String name) {
            LOGGER.d("Bluetooth device found, address: " + address);
            Intent intent =
                    newDeviceIntent(BluetoothDevice.ACTION_FOUND, address)
                            .putExtra(
                                    BluetoothDevice.EXTRA_CLASS,
                                    callConstructor(
                                            BluetoothClass.class,
                                            ClassParameter.from(int.class, bluetoothClass)))
                            .putExtra(BluetoothDevice.EXTRA_NAME, name);
            // TODO(b/200231384): support rssi
            // TODO(b/200231384): send broadcast with additional ACCESS_COARSE_LOCATION permission
            // once broadcast permission is implemented.
            mBroadcastManager.sendBroadcast(intent, android.Manifest.permission.BLUETOOTH);
        }
    }

    private static Intent newDeviceIntent(String action, String address) {
        return new Intent(action)
                .putExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address));
    }
}
