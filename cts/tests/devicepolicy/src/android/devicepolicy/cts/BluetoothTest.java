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

package android.devicepolicy.cts;


import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_AUTOMOTIVE;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_BLUETOOTH;
import static com.android.bedstead.nene.permissions.CommonPermissions.BLUETOOTH_CONNECT;
import static com.android.bedstead.nene.permissions.CommonPermissions.LOCAL_MAC_ADDRESS;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.Bluetooth;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.UUID;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_BLUETOOTH)
public final class BluetoothTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final BluetoothManager sBluetoothManager =
            sContext.getSystemService(BluetoothManager.class);
    private static final BluetoothAdapter sBluetoothAdapter = sBluetoothManager.getAdapter();

    private static final String VALID_ADDRESS = "01:02:03:04:05:06";
    private static final byte[] VALID_ADDRESS_BYTES =
            new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

    @Postsubmit(reason = "new test")
    @EnsureBluetoothEnabled
    @PolicyAppliesTest(policy = Bluetooth.class)
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE) // TODO(b/220306133): bluetooth from background
    public void disable_bluetoothIsDisabled() {
        BlockingBroadcastReceiver r = sDeviceState.registerBroadcastReceiverForUser(
                sDeviceState.dpc().user(), BluetoothAdapter.ACTION_STATE_CHANGED,
                this::isStateDisabled);

        try (PermissionContext p =
                     sDeviceState.dpc().permissions().withPermission(BLUETOOTH_CONNECT)) {
            assertThat(sDeviceState.dpc().bluetoothManager().getAdapter().disable()).isTrue();
            r.awaitForBroadcast();

            Poll.forValue("Bluetooth Enabled for DPC",
                    () -> sDeviceState.dpc().bluetoothManager().getAdapter().isEnabled())
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
            assertThat(TestApis.bluetooth().isEnabled()).isFalse();
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureBluetoothDisabled
    @PolicyAppliesTest(policy = Bluetooth.class)
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE) // TODO(b/220306133): bluetooth from background
    public void enable_bluetoothIsEnabled() {
        BlockingBroadcastReceiver r = sDeviceState.registerBroadcastReceiverForUser(
                sDeviceState.dpc().user(), BluetoothAdapter.ACTION_STATE_CHANGED,
                this::isStateEnabled);

        try (PermissionContext p =
                     sDeviceState.dpc().permissions().withPermission(BLUETOOTH_CONNECT)) {
            // For some reason it doesn't always immediately recognise that the permission has
            // been granted to the DPC
            Poll.forValue("return value for enable from adapter",
                    () -> sDeviceState.dpc().bluetoothManager().getAdapter().enable())
                    .toBeEqualTo(true)
                    .errorOnFail().await();
            r.awaitForBroadcast();

            Poll.forValue("Bluetooth Enabled for DPC",
                    () -> sDeviceState.dpc().bluetoothManager().getAdapter().isEnabled())
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
            assertThat(TestApis.bluetooth().isEnabled()).isTrue();
        }
    }

    @Test
    @RequireRunOnWorkProfile
    @EnsureHasPermission(BLUETOOTH_CONNECT)
    @Postsubmit(reason = "new test")
    @EnsureBluetoothEnabled
    public void listenUsingRfcommWithServiceRecord_inManagedProfile_returnsValidSocket()
            throws IOException {
        BluetoothServerSocket socket = null;
        try {
            socket = sBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    "test", UUID.randomUUID());

            assertThat(socket).isNotNull();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private boolean isStateEnabled(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                == BluetoothAdapter.STATE_ON;
    }

    private boolean isStateDisabled(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                == BluetoothAdapter.STATE_OFF;
    }

    @Test
    @RequireRunOnWorkProfile
    @EnsureHasPermission({LOCAL_MAC_ADDRESS, BLUETOOTH_CONNECT})
    @Postsubmit(reason = "new test")
    @EnsureBluetoothEnabled
    public void getAddress_inManagedProfile_returnsValidAddress() {
        assertThat(BluetoothAdapter.checkBluetoothAddress(sBluetoothAdapter.getAddress())).isTrue();
    }

    @Test
    @RequireRunOnWorkProfile
    @Postsubmit(reason = "new test")
    @EnsureBluetoothDisabled // This method should work even with bluetooth disabled
    public void getRemoteDevice_inManagedProfile_validAddress_works() {
        BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(VALID_ADDRESS);

        assertThat(device.getAddress()).isEqualTo(VALID_ADDRESS);
    }

    @Test
    @RequireRunOnWorkProfile
    @Postsubmit(reason = "new test")
    @EnsureBluetoothDisabled // This method should work even with bluetooth disabled
    public void getRemoteDevice_inManagedProfile_validAddressBytes_works() {
        BluetoothDevice device = sBluetoothAdapter.getRemoteDevice(VALID_ADDRESS_BYTES);

        assertThat(device.getAddress()).isEqualTo(VALID_ADDRESS);
    }
}
