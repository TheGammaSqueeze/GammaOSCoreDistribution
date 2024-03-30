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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static org.junit.Assert.assertArrayEquals;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.OobData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SystemBluetoothTest extends AndroidTestCase {
    private static final String TAG = SystemBluetoothTest.class.getSimpleName();

    private static final long DEFAULT_DISCOVERY_TIMEOUT_MS = 12800;
    private static final int DISCOVERY_START_TIMEOUT = 500;
    private static final String BLE_SCAN_ALWAYS_AVAILABLE = "ble_scan_always_enabled";

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private ReentrantLock mDiscoveryStartedLock;
    private Condition mConditionDiscoveryStarted;
    private boolean mIsDiscoveryStarted;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test enable/disable silence mode and check whether the device is in correct state.
     */
    public void testSilenceMode() {
        if (!mHasBluetooth) {
            return;
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertTrue(device.setSilenceMode(true));
        assertFalse(device.isInSilenceMode());

        assertTrue(device.setSilenceMode(false));
        assertFalse(device.isInSilenceMode());
    }

    /**
     * Test whether the metadata would be stored in Bluetooth storage successfully,
     * also test whether OnMetadataChangedListener would callback correct values when
     * metadata is changed..
     */
    public void testSetGetMetadata() {
        if (!mHasBluetooth) {
            return;
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        byte[] testByteData = "Test Data".getBytes();
        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        BluetoothAdapter.OnMetadataChangedListener listener =
                new BluetoothAdapter.OnMetadataChangedListener() {
                    @Override
                    public void onMetadataChanged(BluetoothDevice dev, int key, byte[] value) {
                        assertEquals(dev, device);
                        assertEquals(key, BluetoothDevice.METADATA_MANUFACTURER_NAME);
                        assertArrayEquals(value, testByteData);
                    }
                };

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        assertTrue(adapter.addOnMetadataChangedListener(device,
                getContext().getMainExecutor(), listener));
        assertTrue(device.setMetadata(
                BluetoothDevice.METADATA_MANUFACTURER_NAME, testByteData));
        assertArrayEquals(device.getMetadata(
                BluetoothDevice.METADATA_MANUFACTURER_NAME), testByteData);
        assertTrue(adapter.removeOnMetadataChangedListener(device, listener));
    }

    public void testDiscoveryEndMillis() {
        if (!mHasBluetooth) {
            return;
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mDiscoveryStartedLock = new ReentrantLock();
        mConditionDiscoveryStarted = mDiscoveryStartedLock.newCondition();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mDiscoveryStartedReceiver, filter);

        mAdapter.startDiscovery();
        assertTrue(waitForDiscoveryStart());
        long discoveryEndTime = mAdapter.getDiscoveryEndMillis();
        long currentTime = System.currentTimeMillis();
        assertTrue(discoveryEndTime > currentTime);
        assertTrue(discoveryEndTime - currentTime < DEFAULT_DISCOVERY_TIMEOUT_MS);
        mContext.unregisterReceiver(mDiscoveryStartedReceiver);
    }

    /**
     * Tests whether the static function BluetoothUuid#containsAnyUuid properly identifies whether
     * the ParcelUuid arrays have at least one common element.
     */
    public void testContainsAnyUuid() {
        if (!mHasBluetooth) {
            return;
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        ParcelUuid[] deviceAUuids = new ParcelUuid[]{BluetoothUuid.A2DP_SOURCE, BluetoothUuid.HFP,
                BluetoothUuid.ADV_AUDIO_DIST, BluetoothUuid.AVRCP_CONTROLLER,
                BluetoothUuid.BASE_UUID, BluetoothUuid.HID, BluetoothUuid.HEARING_AID};
        ParcelUuid[] deviceBUuids = new ParcelUuid[]{BluetoothUuid.A2DP_SINK, BluetoothUuid.BNEP,
                BluetoothUuid.AVRCP_TARGET, BluetoothUuid.HFP_AG,
                BluetoothUuid.HOGP, BluetoothUuid.HSP_AG};
        ParcelUuid[] deviceCUuids = new ParcelUuid[]{BluetoothUuid.HSP, BluetoothUuid.MAP,
                BluetoothUuid.MAS, BluetoothUuid.MNS, BluetoothUuid.NAP,
                BluetoothUuid.OBEX_OBJECT_PUSH, BluetoothUuid.PANU, BluetoothUuid.PBAP_PCE,
                BluetoothUuid.PBAP_PSE, BluetoothUuid.SAP, BluetoothUuid.A2DP_SOURCE};
        assertTrue(BluetoothUuid.containsAnyUuid(null, null));
        assertTrue(BluetoothUuid.containsAnyUuid(new ParcelUuid[]{}, null));
        assertTrue(BluetoothUuid.containsAnyUuid(null, new ParcelUuid[]{}));
        assertFalse(BluetoothUuid.containsAnyUuid(null, deviceAUuids));
        assertFalse(BluetoothUuid.containsAnyUuid(deviceAUuids, null));
        assertFalse(BluetoothUuid.containsAnyUuid(deviceAUuids, deviceBUuids));
        assertTrue(BluetoothUuid.containsAnyUuid(deviceAUuids, deviceCUuids));
        assertTrue(BluetoothUuid.containsAnyUuid(deviceBUuids, deviceBUuids));
    }

    public void testParseUuidFrom() {
        if (!mHasBluetooth) {
            return;
        }

        byte[] uuid16 = new byte[]{0x0B, 0x11};
        assertEquals(BluetoothUuid.A2DP_SINK, BluetoothUuid.parseUuidFrom(uuid16));

        byte[] uuid32 = new byte[]{(byte) 0xF0, (byte) 0xFD, 0x00, 0x00};
        assertEquals(BluetoothUuid.HEARING_AID, BluetoothUuid.parseUuidFrom(uuid32));

        byte[] uuid128 = new byte[]{(byte) 0xFB, 0x34, (byte) 0x9B, 0x5F, (byte) 0x80, 0x00, 0x00,
                (byte) 0x80, 0x00, 0x10, 0x00, 0x00, 0x1F, 0x11, 0x00, 0x00};
        assertEquals(BluetoothUuid.HFP_AG, BluetoothUuid.parseUuidFrom(uuid128));
    }

    public void testCanBondWithoutDialog() {
        if (!mHasBluetooth) {
            return;
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        // Verify the method returns false on a device that doesn't meet the criteria
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertFalse(testDevice.canBondWithoutDialog());
    }

    public void testBleOnlyMode() {
        if (!mHasBluetooth) {
            return;
        }

        int originalScanAlwaysAvailableValue = 0;

        try {
            originalScanAlwaysAvailableValue = Settings.Global.getInt(mContext.getContentResolver(),
                    BLE_SCAN_ALWAYS_AVAILABLE);
        } catch (Settings.SettingNotFoundException e) { // Uses 0 or not available as original
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        // Allows BLE scanning to be performed even if the adapter is off
        Settings.Global.putInt(mContext.getContentResolver(), BLE_SCAN_ALWAYS_AVAILABLE, 1);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mAdapter.isEnabled());

        assertTrue(BTAdapterUtils.enableBLE(mAdapter, mContext));
        assertTrue(BTAdapterUtils.disableBLE(mAdapter, mContext));

        Settings.Global.putInt(mContext.getContentResolver(), BLE_SCAN_ALWAYS_AVAILABLE,
                originalScanAlwaysAvailableValue);
    }

    public void testSetGetOwnAddressType() {
        if (!mHasBluetooth) {
            return;
        }

        AdvertisingSetParameters.Builder paramsBuilder = new AdvertisingSetParameters.Builder();

        assertEquals(paramsBuilder,
                paramsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT));
        assertEquals(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT,
                paramsBuilder.build().getOwnAddressType());

        assertEquals(paramsBuilder,
                paramsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC));
        assertEquals(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC,
                paramsBuilder.build().getOwnAddressType());

        assertEquals(paramsBuilder,
                paramsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM));
        assertEquals(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM,
                paramsBuilder.build().getOwnAddressType());

        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        assertEquals(settingsBuilder,
                settingsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT));
        assertEquals(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT,
                settingsBuilder.build().getOwnAddressType());

        assertEquals(settingsBuilder,
                settingsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC));
        assertEquals(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC,
                settingsBuilder.build().getOwnAddressType());

        assertEquals(settingsBuilder,
                settingsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM));
        assertEquals(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM,
                settingsBuilder.build().getOwnAddressType());
    }

    public void testGetSupportedProfiles() {
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        List<Integer> profiles = mAdapter.getSupportedProfiles();
        assertNotNull(profiles);
    }

    public void testEnableNoAutoConnect() {
        if (!mHasBluetooth) {
            return;
        }

        // Assert that when Bluetooth is already enabled, the method immediately returns true
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        assertTrue(mAdapter.enableNoAutoConnect());
    }

    private boolean isBluetoothPersistedOff() {
        // A value of "0" in Settings.Global.BLUETOOTH_ON means the OFF state was persisted
        return (Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.BLUETOOTH_ON, -1) == 0);
    }

    public void testDisableBluetoothPersistFalse() {
        if (!mHasBluetooth) {
            return;
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, /* persist= */ false, mContext));
        assertFalse(isBluetoothPersistedOff());
    }

    public void testDisableBluetoothPersistTrue() {
        if (!mHasBluetooth) {
            return;
        }

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, /* persist= */ true, mContext));
        assertTrue(isBluetoothPersistedOff());
    }

    public void testSetLowLatencyAudioAllowed() {
        if (!mHasBluetooth) {
            return;
        }
        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(device.setLowLatencyAudioAllowed(true));
        assertFalse(device.setLowLatencyAudioAllowed(false));

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        assertTrue(device.setLowLatencyAudioAllowed(true));
        assertTrue(device.setLowLatencyAudioAllowed(false));
    }

    public void testGenerateLocalOobData() {
        if (!mHasBluetooth) {
            return;
        }

        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
            }
        };
        BluetoothAdapter.OobDataCallback callback = new BluetoothAdapter.OobDataCallback() {
            @Override
            public void onOobData(int transport, @NonNull OobData oobData) {
                fail("Should have failed to generate local oob data as Bluetooth is disabled");
            }

            @Override
            public void onError(int errorCode) {
            }
        };

        try {
            mAdapter.generateLocalOobData(BluetoothDevice.TRANSPORT_AUTO, executor, callback);
            fail("generateLocalOobData should throw an IllegalArgumentException due to invalid "
                    + "transport");
        } catch (IllegalArgumentException ignored) {
        }

        try {
            mAdapter.generateLocalOobData(BluetoothDevice.TRANSPORT_BREDR, executor, null);
            fail("generateLocalOobData should throw a NullPointerException due to passing a null "
                    + "callback");
        } catch (NullPointerException ignored) {
        }

        mAdapter.generateLocalOobData(BluetoothDevice.TRANSPORT_BREDR, executor, callback);
    }

    public void testSetScanMode() {
        if (!mHasBluetooth) {
            return;
        }

        assertEquals(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED,
                mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE));

        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        try {
            mAdapter.setScanMode(0);
            fail("Invalid scan mode");
        } catch (IllegalArgumentException ignored) {
        }

        /* TODO(rahulsabnis): Fix the callback system so these work as intended
        assertEquals(BluetoothStatusCodes.SUCCESS,
                mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_NONE));
        assertEquals(BluetoothAdapter.SCAN_MODE_NONE, mAdapter.getScanMode());
        assertEquals(BluetoothStatusCodes.SUCCESS,
                mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE));
        assertEquals(BluetoothAdapter.SCAN_MODE_CONNECTABLE, mAdapter.getScanMode());

        assertEquals(BluetoothStatusCodes.SUCCESS,
                mAdapter.setDiscoverableTimeout(Duration.ofSeconds(1)));
        assertEquals(BluetoothStatusCodes.SUCCESS,
                mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE));
        assertEquals(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, mAdapter.getScanMode());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals(BluetoothAdapter.SCAN_MODE_CONNECTABLE, mAdapter.getScanMode());
        */
    }

    private boolean waitForDiscoveryStart() {
        mDiscoveryStartedLock.lock();
        try {
            // Wait for discovery to be started
            while (!mIsDiscoveryStarted) {
                if (!mConditionDiscoveryStarted.await(
                        DISCOVERY_START_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout while waiting for discovery to start");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForDiscoveryStart: interrrupted");
        } finally {
            mDiscoveryStartedLock.unlock();
        }
        return mIsDiscoveryStarted;
    }

    private final BroadcastReceiver mDiscoveryStartedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                Log.i(TAG, "Discovery started");
                mDiscoveryStartedLock.lock();
                mIsDiscoveryStarted = true;
                try {
                    mConditionDiscoveryStarted.signal();
                } catch (IllegalMonitorStateException ex) {
                } finally {
                    mDiscoveryStartedLock.unlock();
                }
            }
        }
    };
}
