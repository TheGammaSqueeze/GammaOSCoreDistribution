/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.bluetooth.sap;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SapServiceTest {
    private static final int TIMEOUT_MS = 5_000;

    private SapService mService = null;
    private BluetoothAdapter mAdapter = null;
    private Context mTargetContext;

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    private BluetoothDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue("Ignore test when SapService is not enabled",
                SapService.isEnabled());
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        doReturn(true, false).when(mAdapterService).isStartedProfile(anyString());
        TestUtils.startService(mServiceRule, SapService.class);
        mService = SapService.getSapService();
        assertThat(mService).isNotNull();
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mDevice = TestUtils.getTestDevice(mAdapter, 0);
    }

    @After
    public void tearDown() throws Exception {
        if (!SapService.isEnabled()) {
            return;
        }
        TestUtils.stopService(mServiceRule, SapService.class);
        mService = SapService.getSapService();
        assertThat(mService).isNull();
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void testGetSapService() {
        assertThat(mService).isEqualTo(SapService.getSapService());
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    /**
     * Test stop SAP Service
     */
    @Test
    public void testStopSapService() throws Exception {
        AtomicBoolean stopResult = new AtomicBoolean();
        AtomicBoolean startResult = new AtomicBoolean();
        CountDownLatch latch = new CountDownLatch(1);

        // SAP Service is already running: test stop(). Note: must be done on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                stopResult.set(mService.stop());
                startResult.set(mService.start());
                latch.countDown();
            }
        });

        assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(stopResult.get()).isTrue();
        assertThat(startResult.get()).isTrue();
    }

    /**
     * Test get connection policy for BluetoothDevice
     */
    @Test
    public void testGetConnectionPolicy() {
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.SAP))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        assertThat(mService.getConnectionPolicy(mDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.SAP))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        assertThat(mService.getConnectionPolicy(mDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.SAP))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        assertThat(mService.getConnectionPolicy(mDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
    }

    @Test
    public void testGetRemoteDevice() {
        assertThat(mService.getRemoteDevice()).isNull();
    }

    @Test
    public void testGetRemoteDeviceName() {
        assertThat(SapService.getRemoteDeviceName()).isNull();
    }
}


