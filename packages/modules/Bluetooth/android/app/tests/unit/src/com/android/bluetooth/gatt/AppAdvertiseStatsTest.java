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

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.PeriodicAdvertisingParameters;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * Test cases for {@link AppAdvertiseStats}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppAdvertiseStatsTest {

    @Mock
    private ContextMap map;

    @Mock
    private GattService service;

    @Test
    public void constructor() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        assertThat(appAdvertiseStats.mContextMap).isEqualTo(map);
        assertThat(appAdvertiseStats.mGattService).isEqualTo(service);
    }

    @Test
    public void recordAdvertiseStart() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        assertThat(appAdvertiseStats.mAdvertiserRecords.size())
                .isEqualTo(0);

        int duration = 1;
        int maxExtAdvEvents = 2;

        appAdvertiseStats.recordAdvertiseStart(duration, maxExtAdvEvents);

        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();
        AdvertiseData advertiseData = new AdvertiseData.Builder().build();
        AdvertiseData scanResponse = new AdvertiseData.Builder().build();
        PeriodicAdvertisingParameters periodicParameters =
                new PeriodicAdvertisingParameters.Builder().build();
        AdvertiseData periodicData = new AdvertiseData.Builder().build();

        appAdvertiseStats.recordAdvertiseStart(
                parameters,
                advertiseData,
                scanResponse,
                periodicParameters,
                periodicData,
                duration,
                maxExtAdvEvents
        );

        int numOfExpectedRecords = 2;

        assertThat(appAdvertiseStats.mAdvertiserRecords.size())
                .isEqualTo(numOfExpectedRecords);
    }

    @Test
    public void recordAdvertiseStop() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        int duration = 1;
        int maxExtAdvEvents = 2;

        assertThat(appAdvertiseStats.mAdvertiserRecords.size())
                .isEqualTo(0);

        appAdvertiseStats.recordAdvertiseStart(duration, maxExtAdvEvents);

        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();
        AdvertiseData advertiseData = new AdvertiseData.Builder().build();
        AdvertiseData scanResponse = new AdvertiseData.Builder().build();
        PeriodicAdvertisingParameters periodicParameters =
                new PeriodicAdvertisingParameters.Builder().build();
        AdvertiseData periodicData = new AdvertiseData.Builder().build();

        appAdvertiseStats.recordAdvertiseStart(
                parameters,
                advertiseData,
                scanResponse,
                periodicParameters,
                periodicData,
                duration,
                maxExtAdvEvents
        );

        appAdvertiseStats.recordAdvertiseStop();

        int numOfExpectedRecords = 2;

        assertThat(appAdvertiseStats.mAdvertiserRecords.size())
                .isEqualTo(numOfExpectedRecords);
    }

    @Test
    public void enableAdvertisingSet() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        int duration = 1;
        int maxExtAdvEvents = 2;

        assertThat(appAdvertiseStats.mAdvertiserRecords.size())
                .isEqualTo(0);

        appAdvertiseStats.enableAdvertisingSet(true, duration, maxExtAdvEvents);
        appAdvertiseStats.enableAdvertisingSet(false, duration, maxExtAdvEvents);

        int numOfExpectedRecords = 1;

        assertThat(appAdvertiseStats.mAdvertiserRecords.size())
                .isEqualTo(numOfExpectedRecords);
    }

    @Test
    public void setAdvertisingData() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        AdvertiseData advertiseData = new AdvertiseData.Builder().build();
        appAdvertiseStats.setAdvertisingData(advertiseData);

        appAdvertiseStats.setAdvertisingData(advertiseData);
    }

    @Test
    public void setScanResponseData() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        AdvertiseData scanResponse = new AdvertiseData.Builder().build();
        appAdvertiseStats.setScanResponseData(scanResponse);

        appAdvertiseStats.setScanResponseData(scanResponse);
    }

    @Test
    public void setAdvertisingParameters() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();
        appAdvertiseStats.setAdvertisingParameters(parameters);
    }

    @Test
    public void setPeriodicAdvertisingParameters() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        PeriodicAdvertisingParameters periodicParameters =
                new PeriodicAdvertisingParameters.Builder().build();
        appAdvertiseStats.setPeriodicAdvertisingParameters(periodicParameters);
    }

    @Test
    public void setPeriodicAdvertisingData() {
        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        AdvertiseData periodicData = new AdvertiseData.Builder().build();
        appAdvertiseStats.setPeriodicAdvertisingData(periodicData);

        appAdvertiseStats.setPeriodicAdvertisingData(periodicData);
    }

    @Test
    public void testDump_doesNotCrash() throws Exception {
        StringBuilder sb = new StringBuilder();

        int appUid = 0;
        int id = 1;
        String name = "name";

        AppAdvertiseStats appAdvertiseStats = new AppAdvertiseStats(appUid, id, name, map, service);

        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder().build();
        AdvertiseData advertiseData = new AdvertiseData.Builder().build();
        AdvertiseData scanResponse = new AdvertiseData.Builder().build();
        PeriodicAdvertisingParameters periodicParameters =
                new PeriodicAdvertisingParameters.Builder().build();
        AdvertiseData periodicData = new AdvertiseData.Builder().build();
        int duration = 1;
        int maxExtAdvEvents = 2;

        appAdvertiseStats.recordAdvertiseStart(
                parameters,
                advertiseData,
                scanResponse,
                periodicParameters,
                periodicData,
                duration,
                maxExtAdvEvents
        );

        AppAdvertiseStats.dumpToString(sb, appAdvertiseStats);
    }
}
