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

package com.android.server.nearby.fastpair;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.nearby.FastPairDevice;

import com.android.server.nearby.common.locator.LocatorContextWrapper;
import com.android.server.nearby.fastpair.halfsheet.FastPairHalfSheetManager;
import com.android.server.nearby.fastpair.notification.FastPairNotificationManager;
import com.android.server.nearby.provider.FastPairDataProvider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import service.proto.Rpcs;

public class FastPairAdvHandlerTest {
    @Mock
    private Context mContext;
    @Mock
    private FastPairDataProvider mFastPairDataProvider;
    @Mock
    private FastPairHalfSheetManager mFastPairHalfSheetManager;
    @Mock
    private FastPairNotificationManager mFastPairNotificationManager;
    private static final String BLUETOOTH_ADDRESS = "AA:BB:CC:DD";
    private static final int CLOSE_RSSI = -80;
    private static final int FAR_AWAY_RSSI = -120;
    private static final int TX_POWER = -70;
    private static final byte[] INITIAL_BYTE_ARRAY = new byte[]{0x01, 0x02, 0x03};

    LocatorContextWrapper mLocatorContextWrapper;
    FastPairAdvHandler mFastPairAdvHandler;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mLocatorContextWrapper = new LocatorContextWrapper(mContext);
        mLocatorContextWrapper.getLocator().overrideBindingForTest(
                FastPairHalfSheetManager.class, mFastPairHalfSheetManager
        );
        mLocatorContextWrapper.getLocator().overrideBindingForTest(
                FastPairNotificationManager.class, mFastPairNotificationManager
        );
        when(mFastPairDataProvider.loadFastPairAntispoofKeyDeviceMetadata(any()))
                .thenReturn(Rpcs.GetObservedDeviceResponse.getDefaultInstance());
        mFastPairAdvHandler = new FastPairAdvHandler(mLocatorContextWrapper, mFastPairDataProvider);
    }

    @Test
    public void testInitialBroadcast() {
        FastPairDevice fastPairDevice = new FastPairDevice.Builder()
                .setData(INITIAL_BYTE_ARRAY)
                .setBluetoothAddress(BLUETOOTH_ADDRESS)
                .setRssi(CLOSE_RSSI)
                .setTxPower(TX_POWER)
                .build();

        mFastPairAdvHandler.handleBroadcast(fastPairDevice);

        verify(mFastPairHalfSheetManager).showHalfSheet(any());
    }

    @Test
    public void testInitialBroadcast_farAway_notShowHalfSheet() {
        FastPairDevice fastPairDevice = new FastPairDevice.Builder()
                .setData(INITIAL_BYTE_ARRAY)
                .setBluetoothAddress(BLUETOOTH_ADDRESS)
                .setRssi(FAR_AWAY_RSSI)
                .setTxPower(TX_POWER)
                .build();

        mFastPairAdvHandler.handleBroadcast(fastPairDevice);

        verify(mFastPairHalfSheetManager, never()).showHalfSheet(any());
    }

    @Test
    public void testSubsequentBroadcast() {
        byte[] fastPairRecordWithBloomFilter =
                new byte[]{
                        (byte) 0x02,
                        (byte) 0x01,
                        (byte) 0x02, // Flags
                        (byte) 0x02,
                        (byte) 0x0A,
                        (byte) 0xEB, // Tx Power (-20)
                        (byte) 0x0B,
                        (byte) 0x16,
                        (byte) 0x2C,
                        (byte) 0xFE, // FastPair Service Data
                        (byte) 0x00, // Flags (model ID length = 3)
                        (byte) 0x40, // Account key hash flags (length = 4, type = 0)
                        (byte) 0x11,
                        (byte) 0x22,
                        (byte) 0x33,
                        (byte) 0x44, // Account key hash (0x11223344)
                        (byte) 0x11, // Account key salt flags (length = 1, type = 1)
                        (byte) 0x55, // Account key salt
                };
        FastPairDevice fastPairDevice = new FastPairDevice.Builder()
                .setData(fastPairRecordWithBloomFilter)
                .setBluetoothAddress(BLUETOOTH_ADDRESS)
                .setRssi(CLOSE_RSSI)
                .setTxPower(TX_POWER)
                .build();

        mFastPairAdvHandler.handleBroadcast(fastPairDevice);

        verify(mFastPairHalfSheetManager, never()).showHalfSheet(any());
    }
}
