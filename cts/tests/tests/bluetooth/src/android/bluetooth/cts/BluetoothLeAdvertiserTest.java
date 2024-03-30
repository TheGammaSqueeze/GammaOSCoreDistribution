/*
 * Copyright 2022 The Android Open Source Project
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

import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.bluetooth.le.AdvertisingSetCallback.ADVERTISE_SUCCESS;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.test.AndroidTestCase;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothLeAdvertiserTest extends AndroidTestCase {
    private static final int TIMEOUT_MS = 5000;
    private static final AdvertisingSetParameters ADVERTISING_SET_PARAMETERS =
            new AdvertisingSetParameters.Builder().setLegacyMode(true).build();

    private boolean mHasBluetooth;
    private UiAutomation mUiAutomation;
    private BluetoothAdapter mAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private TestAdvertisingSetCallback mCallback;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        mAdvertiser = mAdapter.getBluetoothLeAdvertiser();
        mCallback = new TestAdvertisingSetCallback();
    }

    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            mAdvertiser.stopAdvertisingSet(mCallback);
            assertTrue(mCallback.mAdvertisingSetStoppedLatch.await(TIMEOUT_MS,
                    TimeUnit.MILLISECONDS));
            mAdvertiser = null;
            mAdapter = null;
        }
    }

    public void test_startAdvertisingSetWithCallbackAndHandler() throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS, null, null, null, null,
                mCallback, new Handler(Looper.getMainLooper()));
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);
    }


    public void test_startAdvertisingSetWithDurationAndCallback() throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS, null, null, null, null,
                0, 0, mCallback);
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);
    }


    public void test_startAdvertisingSetWithDurationCallbackAndHandler()
            throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS, null, null, null, null,
                0, 0, mCallback, new Handler(Looper.getMainLooper()));
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);
    }

    private static class TestAdvertisingSetCallback extends AdvertisingSetCallback {
        public CountDownLatch mAdvertisingSetStartedLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingEnabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingDisabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingSetStoppedLatch = new CountDownLatch(1);

        public AtomicInteger mAdvertisingSetStartedStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingEnabledStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingDisabledStatus = new AtomicInteger();

        public AtomicReference<AdvertisingSet> mAdvertisingSet = new AtomicReference();

        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                int status) {
            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
            mAdvertisingSetStartedStatus.set(status);
            mAdvertisingSet.set(advertisingSet);
            mAdvertisingSetStartedLatch.countDown();
        }

        @Override
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                int status) {
            super.onAdvertisingEnabled(advertisingSet, enable, status);
            if (enable) {
                mAdvertisingEnabledStatus.set(status);
                mAdvertisingEnabledLatch.countDown();
            } else {
                mAdvertisingDisabledStatus.set(status);
                mAdvertisingDisabledLatch.countDown();
            }
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            super.onAdvertisingSetStopped(advertisingSet);
            mAdvertisingSetStoppedLatch.countDown();
        }

    }
}
