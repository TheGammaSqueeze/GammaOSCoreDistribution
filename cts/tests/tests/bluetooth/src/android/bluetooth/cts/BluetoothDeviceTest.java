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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothDevice.ACCESS_ALLOWED;
import static android.bluetooth.BluetoothDevice.ACCESS_REJECTED;
import static android.bluetooth.BluetoothDevice.ACCESS_UNKNOWN;
import static android.bluetooth.BluetoothDevice.TRANSPORT_AUTO;
import static android.bluetooth.BluetoothDevice.TRANSPORT_BREDR;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.OobData;
import android.content.AttributionSource;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;

import java.io.UnsupportedEncodingException;

public class BluetoothDeviceTest extends AndroidTestCase {

    private boolean mHasBluetooth;
    private boolean mHasCompanionDevice;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    private final String mFakeDeviceAddress = "00:11:22:AA:BB:CC";
    private BluetoothDevice mFakeDevice;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        mHasCompanionDevice = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_COMPANION_DEVICE_SETUP);

        if (mHasBluetooth && mHasCompanionDevice) {
            BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
            mAdapter = manager.getAdapter();
            mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
            mFakeDevice = mAdapter.getRemoteDevice(mFakeDeviceAddress);
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth && mHasCompanionDevice) {
            mAdapter = null;
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    public void test_setAlias_getAlias() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();

        AttributionSource source = AttributionSource.myAttributionSource();
        assertEquals("android.bluetooth.cts", source.getPackageName());

        // Verifies that when there is no alias, we return the device name
        assertNull(mFakeDevice.getAlias());

        assertThrows(IllegalArgumentException.class, () -> mFakeDevice.setAlias(""));

        String testDeviceAlias = "Test Device Alias";

        // This should throw a SecurityException because there is no CDM association
        assertThrows("BluetoothDevice.setAlias without"
                + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                SecurityException.class, () -> mFakeDevice.setAlias(testDeviceAlias));

        runShellCommand(String.format(
                "cmd companiondevice associate %d %s %s", userId, packageName, mFakeDeviceAddress));
        String output = runShellCommand("dumpsys companiondevice");
        assertTrue("Package name missing from output", output.contains(packageName));
        assertTrue("Device address missing from output",
                output.toLowerCase().contains(mFakeDeviceAddress.toLowerCase()));

        // Takes time to update the CDM cache, so sleep to ensure the association is cached
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Device properties don't exist for non-existent BluetoothDevice, so calling setAlias with
         * permissions should return false
         */
        assertEquals(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED, mFakeDevice
                .setAlias(testDeviceAlias));
        runShellCommand(String.format("cmd companiondevice disassociate %d %s %s", userId,
                    packageName, mFakeDeviceAddress));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertNull(mFakeDevice.getAlias());
        assertEquals(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED,
                mFakeDevice.setAlias(testDeviceAlias));
    }

    public void test_getIdentityAddress() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows("No BLUETOOTH_PRIVILEGED permission",
                SecurityException.class, () -> mFakeDevice.getIdentityAddress());
    }

    public void test_getAnonymizedAddress() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        assertEquals(mFakeDevice.getAnonymizedAddress(), "XX:XX:XX:AA:BB:CC");
    }

    public void test_getBatteryLevel() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        assertEquals(BluetoothDevice.BATTERY_LEVEL_UNKNOWN, mFakeDevice.getBatteryLevel());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getBatteryLevel());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertEquals(BluetoothDevice.BATTERY_LEVEL_BLUETOOTH_OFF, mFakeDevice.getBatteryLevel());
    }

    public void test_isBondingInitiatedLocally() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        assertFalse(mFakeDevice.isBondingInitiatedLocally());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isBondingInitiatedLocally());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.isBondingInitiatedLocally());
    }

    public void test_prepareToEnterProcess() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        mFakeDevice.prepareToEnterProcess(null);
    }

    public void test_setPin() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        assertFalse(mFakeDevice.setPin((String) null));
        assertFalse(mFakeDevice.setPin("12345678901234567")); // check PIN too big

        assertFalse(mFakeDevice.setPin("123456")); //device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.setPin("123456"));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.setPin("123456"));
    }

    public void test_connect_disconnect() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.connect());
        assertThrows(SecurityException.class, () -> mFakeDevice.disconnect());
    }

    public void test_cancelBondProcess() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.cancelBondProcess());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.cancelBondProcess());
    }

    public void test_createBond() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.createBond(TRANSPORT_AUTO));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.createBond(TRANSPORT_AUTO));
    }

    public void test_createBondOutOfBand() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        OobData data = new OobData.ClassicBuilder(
                new byte[16], new byte[2], new byte[7]).build();

        assertThrows(IllegalArgumentException.class, () -> mFakeDevice.createBondOutOfBand(
                TRANSPORT_AUTO, null, null));

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice
                .createBondOutOfBand(TRANSPORT_AUTO, data, null));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    public void test_getUuids() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        assertNull(mFakeDevice.getUuids());
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getUuids());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertNull(mFakeDevice.getUuids());
    }

    public void test_isEncrypted() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        //Device is not connected
        assertFalse(mFakeDevice.isEncrypted());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isEncrypted());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.isEncrypted());
    }

    public void test_removeBond() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        //Device is not bonded
        assertFalse(mFakeDevice.removeBond());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.removeBond());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.removeBond());
    }

    public void test_setPinByteArray() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        assertThrows(NullPointerException.class, () -> mFakeDevice.setPin((byte[]) null));

        // check PIN too big
        assertFalse(mFakeDevice.setPin(convertPinToBytes("12345678901234567")));
        assertFalse(mFakeDevice.setPin(convertPinToBytes("123456"))); // device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPin(convertPinToBytes("123456")));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.setPin(convertPinToBytes("123456")));
    }

    public void test_connectGatt() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        assertThrows(NullPointerException.class, () -> mFakeDevice
                .connectGatt(getContext(), false, null,
                TRANSPORT_AUTO, BluetoothDevice.PHY_LE_1M_MASK));

        assertThrows(NullPointerException.class, () ->
                mFakeDevice.connectGatt(getContext(), false, null,
                TRANSPORT_AUTO, BluetoothDevice.PHY_LE_1M_MASK, null));
    }

    public void test_fetchUuidsWithSdp() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        // TRANSPORT_AUTO doesn't need BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_BREDR));
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_LE));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO));
    }

    public void test_messageAccessPermission() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setMessageAccessPermission(ACCESS_ALLOWED));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setMessageAccessPermission(ACCESS_UNKNOWN));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setMessageAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.setMessageAccessPermission(ACCESS_UNKNOWN));
        assertEquals(ACCESS_UNKNOWN, mFakeDevice.getMessageAccessPermission());
        assertTrue(mFakeDevice.setMessageAccessPermission(ACCESS_ALLOWED));
        assertEquals(ACCESS_ALLOWED, mFakeDevice.getMessageAccessPermission());
        assertTrue(mFakeDevice.setMessageAccessPermission(ACCESS_REJECTED));
        assertEquals(ACCESS_REJECTED, mFakeDevice.getMessageAccessPermission());
    }

    public void test_phonebookAccessPermission() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPhonebookAccessPermission(ACCESS_ALLOWED));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPhonebookAccessPermission(ACCESS_UNKNOWN));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setPhonebookAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.setPhonebookAccessPermission(ACCESS_UNKNOWN));
        assertEquals(ACCESS_UNKNOWN, mFakeDevice.getPhonebookAccessPermission());
        assertTrue(mFakeDevice.setPhonebookAccessPermission(ACCESS_ALLOWED));
        assertEquals(ACCESS_ALLOWED, mFakeDevice.getPhonebookAccessPermission());
        assertTrue(mFakeDevice.setPhonebookAccessPermission(ACCESS_REJECTED));
        assertEquals(ACCESS_REJECTED, mFakeDevice.getPhonebookAccessPermission());
    }

    public void test_simAccessPermission() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setSimAccessPermission(ACCESS_ALLOWED));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setSimAccessPermission(ACCESS_UNKNOWN));
        assertThrows(SecurityException.class, () -> mFakeDevice
                .setSimAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertTrue(mFakeDevice.setSimAccessPermission(ACCESS_UNKNOWN));
        assertEquals(ACCESS_UNKNOWN, mFakeDevice.getSimAccessPermission());
        assertTrue(mFakeDevice.setSimAccessPermission(ACCESS_ALLOWED));
        assertEquals(ACCESS_ALLOWED, mFakeDevice.getSimAccessPermission());
        assertTrue(mFakeDevice.setSimAccessPermission(ACCESS_REJECTED));
        assertEquals(ACCESS_REJECTED, mFakeDevice.getSimAccessPermission());
    }

    private byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] pinBytes;
        try {
            pinBytes = pin.getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
            return null;
        }
        return pinBytes;
    }
}
