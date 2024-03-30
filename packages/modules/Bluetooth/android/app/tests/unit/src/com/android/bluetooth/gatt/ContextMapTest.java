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

package com.android.bluetooth.gatt;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.Binder;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.UUID;

/**
 * Test cases for {@link ContextMap}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextMapTest {

    private GattService mService;

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Mock
    private AdapterService mAdapterService;

    @Mock
    private AppAdvertiseStats appAdvertiseStats;

    @Spy
    private BluetoothMethodProxy mMapMethodProxy = BluetoothMethodProxy.getInstance();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mMapMethodProxy);

        TestUtils.setAdapterService(mAdapterService);
        doReturn(true).when(mAdapterService).isStartedProfile(anyString());

        TestUtils.startService(mServiceRule, GattService.class);
        mService = GattService.getGattService();
    }

    @After
    public void tearDown() throws Exception {
        if (!GattService.isEnabled()) {
            return;
        }

        BluetoothMethodProxy.setInstanceForTesting(null);

        doReturn(false).when(mAdapterService).isStartedProfile(anyString());
        TestUtils.stopService(mServiceRule, GattService.class);
        mService = GattService.getGattService();

        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void getByMethods() {
        ContextMap contextMap = new ContextMap<>();

        int id = 12345;
        contextMap.add(id, null, mService);

        contextMap.add(UUID.randomUUID(), null, null, null, mService);

        int appUid = Binder.getCallingUid();
        String appName = mService.getPackageManager().getNameForUid(appUid);

        ContextMap.App contextMapById = contextMap.getById(appUid);
        assertThat(contextMapById.name).isEqualTo(appName);

        ContextMap.App contextMapByName = contextMap.getByName(appName);
        assertThat(contextMapByName.name).isEqualTo(appName);
    }

    @Test
    public void advertisingSetAndData() {
        ContextMap contextMap = new ContextMap<>();

        int appUid = Binder.getCallingUid();
        int id = 12345;
        String appName = mService.getPackageManager().getNameForUid(appUid);
        doReturn(appAdvertiseStats).when(mMapMethodProxy)
                .createAppAdvertiseStats(appUid, id, appName, contextMap, mService);

        contextMap.add(id, null, mService);

        int duration = 60;
        int maxExtAdvEvents = 100;
        contextMap.enableAdvertisingSet(id, true, duration, maxExtAdvEvents);
        verify(appAdvertiseStats).enableAdvertisingSet(true, duration, maxExtAdvEvents);

        AdvertiseData advertiseData = new AdvertiseData.Builder().build();
        contextMap.setAdvertisingData(id, advertiseData);
        verify(appAdvertiseStats).setAdvertisingData(advertiseData);

        AdvertiseData scanResponse = new AdvertiseData.Builder().build();
        contextMap.setScanResponseData(id, scanResponse);
        verify(appAdvertiseStats).setScanResponseData(scanResponse);

        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();
        contextMap.setAdvertisingParameters(id, parameters);
        verify(appAdvertiseStats).setAdvertisingParameters(parameters);

        PeriodicAdvertisingParameters periodicParameters =
                new PeriodicAdvertisingParameters.Builder().build();
        contextMap.setPeriodicAdvertisingParameters(id, periodicParameters);
        verify(appAdvertiseStats).setPeriodicAdvertisingParameters(periodicParameters);

        AdvertiseData periodicData = new AdvertiseData.Builder().build();
        contextMap.setPeriodicAdvertisingData(id, periodicData);
        verify(appAdvertiseStats).setPeriodicAdvertisingData(periodicData);

        contextMap.onPeriodicAdvertiseEnabled(id, true);
        verify(appAdvertiseStats).onPeriodicAdvertiseEnabled(true);

        AppAdvertiseStats toBeRemoved = contextMap.getAppAdvertiseStatsById(id);
        assertThat(toBeRemoved).isNotNull();

        contextMap.removeAppAdvertiseStats(id);

        AppAdvertiseStats isRemoved = contextMap.getAppAdvertiseStatsById(id);
        assertThat(isRemoved).isNull();
    }

    @Test
    public void emptyStop_doesNotCrash() throws Exception {
        ContextMap contextMap = new ContextMap<>();

        int id = 12345;
        contextMap.recordAdvertiseStop(id);
    }

    @Test
    public void testDump_doesNotCrash() throws Exception {
        StringBuilder sb = new StringBuilder();

        ContextMap contextMap = new ContextMap<>();

        int id = 12345;
        contextMap.add(id, null, mService);

        contextMap.add(UUID.randomUUID(), null, null, null, mService);

        contextMap.recordAdvertiseStop(id);

        int idSecond = 54321;
        contextMap.add(idSecond, null, mService);

        contextMap.dump(sb);

        contextMap.dumpAdvertiser(sb);
    }
}
