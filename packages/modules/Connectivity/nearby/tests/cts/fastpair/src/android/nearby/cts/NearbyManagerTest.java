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

package android.nearby.cts;

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;
import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;
import static android.provider.DeviceConfig.NAMESPACE_TETHERING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.cts.BTAdapterUtils;
import android.content.Context;
import android.nearby.BroadcastCallback;
import android.nearby.BroadcastRequest;
import android.nearby.NearbyDevice;
import android.nearby.NearbyManager;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PrivateCredential;
import android.nearby.ScanCallback;
import android.nearby.ScanRequest;
import android.os.Build;
import android.provider.DeviceConfig;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TODO(b/215435939) This class doesn't include any logic yet. Because SELinux denies access to
 * NearbyManager.
 */
@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class NearbyManagerTest {
    private static final byte[] SALT = new byte[]{1, 2};
    private static final byte[] SECRETE_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] META_DATA_ENCRYPTION_KEY = new byte[14];
    private static final byte[] AUTHENTICITY_KEY = new byte[]{0, 1, 1, 1};
    private static final String DEVICE_NAME = "test_device";
    private static final int BLE_MEDIUM = 1;

    private Context mContext;
    private NearbyManager mNearbyManager;
    private UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private ScanRequest mScanRequest = new ScanRequest.Builder()
            .setScanType(ScanRequest.SCAN_TYPE_FAST_PAIR)
            .setScanMode(ScanRequest.SCAN_MODE_LOW_LATENCY)
            .setBleEnabled(true)
            .build();
    private  ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onDiscovered(@NonNull NearbyDevice device) {
        }

        @Override
        public void onUpdated(@NonNull NearbyDevice device) {
        }

        @Override
        public void onLost(@NonNull NearbyDevice device) {
        }
    };
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() {
        mUiAutomation.adoptShellPermissionIdentity(READ_DEVICE_CONFIG, WRITE_DEVICE_CONFIG,
                BLUETOOTH_PRIVILEGED);
        DeviceConfig.setProperty(NAMESPACE_TETHERING,
                "nearby_enable_presence_broadcast_legacy",
                "true", false);

        mContext = InstrumentationRegistry.getContext();
        mNearbyManager = mContext.getSystemService(NearbyManager.class);

        enableBluetooth();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_startAndStopScan() {
        mNearbyManager.startScan(mScanRequest, EXECUTOR, mScanCallback);
        mNearbyManager.stopScan(mScanCallback);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_startScan_noPrivilegedPermission() {
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mNearbyManager
                .startScan(mScanRequest, EXECUTOR, mScanCallback));
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_stopScan_noPrivilegedPermission() {
        mNearbyManager.startScan(mScanRequest, EXECUTOR, mScanCallback);
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mNearbyManager.stopScan(mScanCallback));
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testStartStopBroadcast() throws InterruptedException {
        PrivateCredential credential = new PrivateCredential.Builder(SECRETE_ID, AUTHENTICITY_KEY,
                META_DATA_ENCRYPTION_KEY, DEVICE_NAME)
                .setIdentityType(IDENTITY_TYPE_PRIVATE)
                .build();
        BroadcastRequest broadcastRequest =
                new PresenceBroadcastRequest.Builder(
                        Collections.singletonList(BLE_MEDIUM), SALT, credential)
                        .addAction(123)
                        .build();

        CountDownLatch latch = new CountDownLatch(1);
        BroadcastCallback callback = status -> {
            latch.countDown();
            assertThat(status).isEqualTo(BroadcastCallback.STATUS_OK);
        };
        mNearbyManager.startBroadcast(broadcastRequest, Executors.newSingleThreadExecutor(),
                callback);
        latch.await(10, TimeUnit.SECONDS);
        mNearbyManager.stopBroadcast(callback);
    }

    private void enableBluetooth() {
        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = manager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            assertThat(BTAdapterUtils.enableAdapter(bluetoothAdapter, mContext)).isTrue();
        }
    }
}
