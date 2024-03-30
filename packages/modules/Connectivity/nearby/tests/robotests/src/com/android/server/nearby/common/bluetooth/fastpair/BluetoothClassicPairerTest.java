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

import static org.robolectric.Shadows.shadowOf;

import android.Manifest.permission;
import android.bluetooth.BluetoothAdapter;

import com.android.libraries.testing.deviceshadower.Bluelet.IoCapabilities;
import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowBluetoothDevice;
import com.android.libraries.testing.deviceshadower.testcases.BluetoothTestCase;

import com.google.common.base.VerifyException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;

/**
 * Tests for {@link BluetoothClassicPairer}.
 */
@RunWith(RobolectricTestRunner.class)
public class BluetoothClassicPairerTest extends BluetoothTestCase {

    private static final String LOCAL_DEVICE_ADDRESS = "AA:AA:AA:AA:AA:01";

    /**
     * The remote device's Bluetooth Classic address.
     */
    private static final String REMOTE_DEVICE_PUBLIC_ADDRESS = "BB:BB:BB:BB:BB:0C";

    private Preferences.Builder mPrefsBuilder;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPrefsBuilder = Preferences.builder().setCreateBondTimeoutSeconds(10);

        ShadowBluetoothDevice.resetPairingConfirmation();
        shadowOf(mContext)
                .grantPermissions(
                        permission.BLUETOOTH, permission.BLUETOOTH_ADMIN,
                        permission.BLUETOOTH_PRIVILEGED);

        DeviceShadowEnvironment.addDevice(LOCAL_DEVICE_ADDRESS)
                .bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON)
                .setIoCapabilities(IoCapabilities.DISPLAY_YES_NO);
        DeviceShadowEnvironment.addDevice(REMOTE_DEVICE_PUBLIC_ADDRESS)
                .bluetooth()
                .setAdapterInitialState(BluetoothAdapter.STATE_ON)
                .setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
                .setIoCapabilities(IoCapabilities.DISPLAY_YES_NO);

        // By default, code runs as if it's on this virtual "device".
        DeviceShadowEnvironment.setLocalDevice(LOCAL_DEVICE_ADDRESS);
    }

    @Test
    public void pair_setPairingConfirmationTrue_deviceBonded() throws Exception {
    // TODO(b/217195327): replace deviceshadower with injector.
    /*
        AtomicReference<BluetoothDevice> targetRemoteDevice = new AtomicReference<>();
        BluetoothClassicPairer bluetoothClassicPairer =
                new BluetoothClassicPairer(
                        mContext,
                        BluetoothAdapter.getDefaultAdapter()
                                .getRemoteDevice(REMOTE_DEVICE_PUBLIC_ADDRESS),
                        mPrefsBuilder.build(),
                        (BluetoothDevice remoteDevice, int key) -> {
                            targetRemoteDevice.set(remoteDevice);
                            // Confirms at remote device to pair with local one.
                            setPairingConfirmationAtRemoteDevice(true);

                            // Confirms to pair with remote device.
                            remoteDevice.setPairingConfirmation(true);
                        });

        bluetoothClassicPairer.pair();

        assertThat(targetRemoteDevice.get()).isNotNull();
        assertThat(targetRemoteDevice.get().getAddress()).isEqualTo(REMOTE_DEVICE_PUBLIC_ADDRESS);
        assertThat(targetRemoteDevice.get().getBondState()).isEqualTo(BluetoothDevice.BOND_BONDED);
        assertThat(bluetoothClassicPairer.isPaired()).isTrue();
    */
    }

    @Test
    public void pair_setPairingConfirmationFalse_throwsExceptionDeviceNotBonded() throws Exception {
    // TODO(b/217195327): replace deviceshadower with injector.
    /*
        AtomicReference<BluetoothDevice> targetRemoteDevice = new AtomicReference<>();
        BluetoothClassicPairer bluetoothClassicPairer =
                new BluetoothClassicPairer(
                        mContext,
                        BluetoothAdapter.getDefaultAdapter()
                                .getRemoteDevice(REMOTE_DEVICE_PUBLIC_ADDRESS),
                        mPrefsBuilder.build(),
                        (BluetoothDevice remoteDevice, int key) -> {
                            targetRemoteDevice.set(remoteDevice);
                            // Confirms at remote device to pair with local one.
                            setPairingConfirmationAtRemoteDevice(true);

                            // Confirms NOT to pair with remote device.
                            remoteDevice.setPairingConfirmation(false);
                        });

        assertThrows(PairingException.class, bluetoothClassicPairer::pair);

        assertThat(targetRemoteDevice.get()).isNotNull();
        assertThat(targetRemoteDevice.get().getAddress()).isEqualTo(REMOTE_DEVICE_PUBLIC_ADDRESS);
        assertThat(targetRemoteDevice.get().getBondState()).isNotEqualTo(
                BluetoothDevice.BOND_BONDED);
        assertThat(bluetoothClassicPairer.isPaired()).isFalse();
    */
    }

    @Test
    public void pair_setPairingConfirmationIgnored_throwsExceptionDeviceNotBonded()
            throws Exception {
    // TODO(b/217195327): replace deviceshadower with injector.
    /*
        AtomicReference<BluetoothDevice> targetRemoteDevice = new AtomicReference<>();
        BluetoothClassicPairer bluetoothClassicPairer =
                new BluetoothClassicPairer(
                        mContext,
                        BluetoothAdapter.getDefaultAdapter()
                                .getRemoteDevice(REMOTE_DEVICE_PUBLIC_ADDRESS),
                        mPrefsBuilder.build(),
                        (BluetoothDevice remoteDevice, int key) -> {
                            targetRemoteDevice.set(remoteDevice);
                            // Confirms at remote device to pair with local one.
                            setPairingConfirmationAtRemoteDevice(true);

                            // Ignores the setPairingConfirmation.
                        });

        assertThrows(PairingException.class, bluetoothClassicPairer::pair);
        assertThat(targetRemoteDevice.get()).isNotNull();
        assertThat(targetRemoteDevice.get().getAddress()).isEqualTo(REMOTE_DEVICE_PUBLIC_ADDRESS);
        assertThat(targetRemoteDevice.get().getBondState()).isNotEqualTo(
                BluetoothDevice.BOND_BONDED);
        assertThat(bluetoothClassicPairer.isPaired()).isFalse();
    */
    }

    private static void setPairingConfirmationAtRemoteDevice(boolean confirm) {
        try {
            DeviceShadowEnvironment.run(REMOTE_DEVICE_PUBLIC_ADDRESS,
                    () -> BluetoothAdapter.getDefaultAdapter()
                            .getRemoteDevice(LOCAL_DEVICE_ADDRESS)
                            .setPairingConfirmation(confirm)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new VerifyException("failed to set pairing confirmation at remote device", e);
        }
    }
}
