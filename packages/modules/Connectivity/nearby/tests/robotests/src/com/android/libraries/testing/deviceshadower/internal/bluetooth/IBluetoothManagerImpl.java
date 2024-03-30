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

import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;

/**
 * Implementation of IBluetoothManager interface
 */
public class IBluetoothManagerImpl implements IBluetoothManager {

    private final IBluetooth mFakeBluetoothService = new IBluetoothImpl();
    private final IBluetoothGatt mFakeGattService = new IBluetoothGattImpl();

    @Override
    public String getAddress() {
        return mFakeBluetoothService.getAddress();
    }

    @Override
    public String getName() {
        return mFakeBluetoothService.getName();
    }

    @Override
    public IBluetooth registerAdapter(IBluetoothManagerCallback callback) {
        return mFakeBluetoothService;
    }

    @Override
    public IBluetoothGatt getBluetoothGatt() {
        return mFakeGattService;
    }

    @Override
    public boolean enable() {
        mFakeBluetoothService.enable();
        return true;
    }

    @Override
    public boolean disable(boolean persist) {
        mFakeBluetoothService.disable();
        return true;
    }
}
