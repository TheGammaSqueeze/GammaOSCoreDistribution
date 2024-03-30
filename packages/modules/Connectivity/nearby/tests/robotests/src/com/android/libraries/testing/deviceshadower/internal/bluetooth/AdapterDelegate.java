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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass.Device;
import android.os.Build.VERSION;

import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.DeviceletImpl;
import com.android.libraries.testing.deviceshadower.internal.common.NamedRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;

/**
 * Class handling Bluetooth Adapter State change. Currently async event processing is not supported,
 * and there is no deferred operation when adapter is in a pending state.
 */
class AdapterDelegate {

    /**
     * Callback for adapter
     */
    public interface Callback {

        void onAdapterStateChange(State prevState, State newState);

        void onBleStateChange(State prevState, State newState);

        void onDiscoveryStarted();

        void onDiscoveryFinished();

        void onDeviceFound(String address, int bluetoothClass, String name);
    }

    @GuardedBy("this")
    private State mCurrentState;

    private final String mAddress;
    private final Callback mCallback;
    private AtomicBoolean mIsDiscovering = new AtomicBoolean(false);
    private final AtomicInteger mScanMode = new AtomicInteger(BluetoothAdapter.SCAN_MODE_NONE);
    private int mBluetoothClass = Device.PHONE_SMART;

    AdapterDelegate(String address, Callback callback) {
        this.mAddress = address;
        this.mCurrentState = State.OFF;
        this.mCallback = callback;
    }

    synchronized void processEvent(Event event) {
        State newState = TRANSITION[mCurrentState.ordinal()][event.ordinal()];
        if (newState == null) {
            return;
        }
        State prevState = mCurrentState;
        mCurrentState = newState;
        handleStateChange(prevState, newState);
    }

    private void handleStateChange(State prevState, State newState) {
        // TODO(b/200231384): fake service bind/unbind on state change
        if (prevState.equals(newState)) {
            return;
        }
        if (VERSION.SDK_INT < 23) {
            mCallback.onAdapterStateChange(prevState, newState);
        } else {
            mCallback.onBleStateChange(prevState, newState);
            if (newState.equals(State.BLE_TURNING_ON)
                    || newState.equals(State.BLE_TURNING_OFF)
                    || newState.equals(State.OFF)
                    || (newState.equals(State.BLE_ON) && prevState.equals(State.BLE_TURNING_ON))) {
                return;
            }
            if (newState.equals(State.BLE_ON)) {
                newState = State.OFF;
            } else if (prevState.equals(State.BLE_ON)) {
                prevState = State.OFF;
            }
            mCallback.onAdapterStateChange(prevState, newState);
        }
    }

    synchronized State getState() {
        return mCurrentState;
    }

    synchronized void setState(State state) {
        mCurrentState = state;
    }

    void setBluetoothClass(int bluetoothClass) {
        this.mBluetoothClass = bluetoothClass;
    }

    int getBluetoothClass() {
        return mBluetoothClass;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    void startDiscovery() {
        synchronized (this) {
            if (mIsDiscovering.get()) {
                return;
            }
            mIsDiscovering.set(true);
        }

        mCallback.onDiscoveryStarted();

        NamedRunnable onDeviceFound =
                NamedRunnable.create(
                        "BluetoothAdapter.onDeviceFound",
                        new Runnable() {
                            @Override
                            public void run() {
                                List<DeviceletImpl> devices =
                                        DeviceShadowEnvironmentImpl.getDeviceletImpls();
                                for (DeviceletImpl devicelet : devices) {
                                    BlueletImpl bluelet = devicelet.blueletImpl();
                                    if (mAddress.equals(devicelet.getAddress())
                                            || bluelet.getAdapterDelegate().mScanMode.get()
                                                    != BluetoothAdapter
                                            .SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                                        continue;
                                    }
                                    mCallback.onDeviceFound(
                                            bluelet.address,
                                            bluelet.getAdapterDelegate().mBluetoothClass,
                                            bluelet.mName);
                                }
                                finishDiscovery();
                            }
                        });

        DeviceShadowEnvironmentImpl.runOnUi(mAddress, onDeviceFound);
    }

    void cancelDiscovery() {
        finishDiscovery();
    }

    boolean isDiscovering() {
        return mIsDiscovering.get();
    }

    void setScanMode(int scanMode) {
        // TODO(b/200231384): broadcast scan mode change.
        this.mScanMode.set(scanMode);
    }

    int getScanMode() {
        return mScanMode.get();
    }

    private void finishDiscovery() {
        synchronized (this) {
            if (!mIsDiscovering.get()) {
                return;
            }
            mIsDiscovering.set(false);
        }
        mCallback.onDiscoveryFinished();
    }

    enum State {
        OFF(BluetoothAdapter.STATE_OFF),
        TURNING_ON(BluetoothAdapter.STATE_TURNING_ON),
        ON(BluetoothAdapter.STATE_ON),
        TURNING_OFF(BluetoothAdapter.STATE_TURNING_OFF),
        // States for API23+
        BLE_TURNING_ON(BluetoothConstants.STATE_BLE_TURNING_ON),
        BLE_ON(BluetoothConstants.STATE_BLE_ON),
        BLE_TURNING_OFF(BluetoothConstants.STATE_BLE_TURNING_OFF);

        private static final Map<Integer, State> LOOKUP = new HashMap<>();

        static {
            for (State state : State.values()) {
                LOOKUP.put(state.getValue(), state);
            }
        }

        static State lookup(int value) {
            return LOOKUP.get(value);
        }

        private final int mValue;

        State(int value) {
            this.mValue = value;
        }

        int getValue() {
            return mValue;
        }
    }

    /*
     * Represents Bluetooth events which can trigger adapter state change.
     */
    enum Event {
        USER_TURN_ON,
        USER_TURN_OFF,
        BREDR_STARTED,
        BREDR_STOPPED,
        // Events for API23+
        BLE_TURN_ON,
        BLE_TURN_OFF,
        BLE_STARTED,
        BLE_STOPPED
    }

    private static final State[][] TRANSITION =
            new State[State.values().length][Event.values().length];

    static {
        if (VERSION.SDK_INT < 23) {
            // transition table before API23
            TRANSITION[State.OFF.ordinal()][Event.USER_TURN_ON.ordinal()] = State.TURNING_ON;
            TRANSITION[State.TURNING_ON.ordinal()][Event.BREDR_STARTED.ordinal()] = State.ON;
            TRANSITION[State.ON.ordinal()][Event.USER_TURN_OFF.ordinal()] = State.TURNING_OFF;
            TRANSITION[State.TURNING_OFF.ordinal()][Event.BREDR_STOPPED.ordinal()] = State.OFF;
        } else {
            // transition table starting from API23
            TRANSITION[State.OFF.ordinal()][Event.BLE_TURN_ON.ordinal()] = State.BLE_TURNING_ON;
            TRANSITION[State.BLE_TURNING_ON.ordinal()][Event.BLE_STARTED.ordinal()] = State.BLE_ON;
            TRANSITION[State.BLE_ON.ordinal()][Event.USER_TURN_ON.ordinal()] = State.TURNING_ON;
            TRANSITION[State.TURNING_ON.ordinal()][Event.BREDR_STARTED.ordinal()] = State.ON;
            TRANSITION[State.ON.ordinal()][Event.BLE_TURN_OFF.ordinal()] = State.TURNING_OFF;
            TRANSITION[State.TURNING_OFF.ordinal()][Event.BREDR_STOPPED.ordinal()] = State.BLE_ON;
            TRANSITION[State.BLE_ON.ordinal()][Event.USER_TURN_OFF.ordinal()] =
                    State.BLE_TURNING_OFF;
            TRANSITION[State.BLE_TURNING_OFF.ordinal()][Event.BLE_STOPPED.ordinal()] = State.OFF;
        }
    }
}
