/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.fastpair.provider.simulator.app;

import static android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event.Code.ACCOUNT_KEY;
import static android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event.Code.ACKNOWLEDGE;
import static android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event.Code.BLUETOOTH_ADDRESS_BLE;
import static android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event.Code.BLUETOOTH_ADDRESS_PUBLIC;

import android.nearby.fastpair.provider.FastPairSimulator;
import android.nearby.fastpair.provider.FastPairSimulator.BatteryValue;
import android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Command;
import android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Command.BatteryInfo;
import android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event;
import android.nearby.fastpair.provider.simulator.testing.InputStreamListener;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/** Listener for input stream of the remote device. */
public class RemoteDeviceListener implements InputStreamListener {
    private static final String TAG = RemoteDeviceListener.class.getSimpleName();

    private final MainActivity mMainActivity;
    @Nullable
    private FastPairSimulator mFastPairSimulator;

    public RemoteDeviceListener(MainActivity mainActivity) {
        this.mMainActivity = mainActivity;
    }

    @Override
    public void onInputData(ByteString byteString) {
        Command command;
        try {
            command = Command.parseFrom(byteString);
        } catch (InvalidProtocolBufferException e) {
            Log.w(TAG, String.format("%s input data is not a Command",
                    mMainActivity.mRemoteDeviceId), e);
            return;
        }

        mMainActivity.runOnUiThread(() -> {
            Log.d(TAG, String.format("%s new command %s",
                    mMainActivity.mRemoteDeviceId, command.getCode()));
            switch (command.getCode()) {
                case POLLING:
                    mMainActivity.sendEventToRemoteDevice(
                            Event.newBuilder().setCode(ACKNOWLEDGE));
                    break;
                case RESET:
                    mMainActivity.reset();
                    break;
                case SHOW_BATTERY:
                    onShowBattery(command.getBatteryInfo());
                    break;
                case HIDE_BATTERY:
                    onHideBattery();
                    break;
                case REQUEST_BLUETOOTH_ADDRESS_BLE:
                    onRequestBleAddress();
                    break;
                case REQUEST_BLUETOOTH_ADDRESS_PUBLIC:
                    onRequestPublicAddress();
                    break;
                case REQUEST_ACCOUNT_KEY:
                    ByteString accountKey = mMainActivity.getAccontKey();
                    if (accountKey == null) {
                        break;
                    }
                    mMainActivity.sendEventToRemoteDevice(
                            Event.newBuilder().setCode(ACCOUNT_KEY)
                                    .setAccountKey(accountKey));
                    break;
            }
        });
    }

    @Override
    public void onClose() {
        Log.d(TAG, String.format("%s input stream is closed", mMainActivity.mRemoteDeviceId));
    }

    void setFastPairSimulator(FastPairSimulator fastPairSimulator) {
        this.mFastPairSimulator = fastPairSimulator;
    }

    private void onShowBattery(@Nullable BatteryInfo batteryInfo) {
        if (mFastPairSimulator == null || batteryInfo == null) {
            Log.w(TAG, "skip showing battery");
            return;
        }

        if (batteryInfo.getBatteryValuesCount() != 3) {
            Log.w(TAG, String.format("skip showing battery: count is not valid %d",
                    batteryInfo.getBatteryValuesCount()));
            return;
        }

        Log.d(TAG, String.format("Show battery %s", batteryInfo));

        if (batteryInfo.hasSuppressNotification()) {
            mFastPairSimulator.setSuppressBatteryNotification(
                    batteryInfo.getSuppressNotification());
        }
        mFastPairSimulator.setBatteryValues(
                convertFrom(batteryInfo.getBatteryValues(0)),
                convertFrom(batteryInfo.getBatteryValues(1)),
                convertFrom(batteryInfo.getBatteryValues(2)));
        mFastPairSimulator.startAdvertising();
    }

    private void onHideBattery() {
        if (mFastPairSimulator == null) {
            return;
        }

        mFastPairSimulator.clearBatteryValues();
        mFastPairSimulator.startAdvertising();
    }

    private void onRequestBleAddress() {
        if (mFastPairSimulator == null) {
            return;
        }

        mMainActivity.sendEventToRemoteDevice(
                Event.newBuilder()
                        .setCode(BLUETOOTH_ADDRESS_BLE)
                        .setBleAddress(mFastPairSimulator.getBleAddress()));
    }

    private void onRequestPublicAddress() {
        if (mFastPairSimulator == null) {
            return;
        }

        mMainActivity.sendEventToRemoteDevice(
                Event.newBuilder()
                        .setCode(BLUETOOTH_ADDRESS_PUBLIC)
                        .setPublicAddress(mFastPairSimulator.getBluetoothAddress()));
    }

    private static BatteryValue convertFrom(BatteryInfo.BatteryValue batteryValue) {
        return new BatteryValue(batteryValue.getCharging(), batteryValue.getLevel());
    }
}
