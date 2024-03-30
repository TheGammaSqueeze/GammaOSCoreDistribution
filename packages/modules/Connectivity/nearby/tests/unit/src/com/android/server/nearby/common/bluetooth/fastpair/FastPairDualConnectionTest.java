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

package com.android.server.nearby.common.bluetooth.fastpair;

import static com.android.server.nearby.common.bluetooth.fastpair.FastPairDualConnection.GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED;
import static com.android.server.nearby.common.bluetooth.fastpair.FastPairDualConnection.GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST;
import static com.android.server.nearby.common.bluetooth.fastpair.FastPairDualConnection.GATT_ERROR_CODE_TIMEOUT;
import static com.android.server.nearby.common.bluetooth.fastpair.FastPairDualConnection.appendMoreErrorCode;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doNothing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.platform.test.annotations.Presubmit;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link FastPairDualConnection}.
 */
@Presubmit
@SmallTest
public class FastPairDualConnectionTest extends TestCase {

    private static final String BLE_ADDRESS = "00:11:22:33:FF:EE";
    private static final String MASKED_BLE_ADDRESS = "MASKED_BLE_ADDRESS";
    private static final short[] PROFILES = {Constants.A2DP_SINK_SERVICE_UUID};
    private static final int NUM_CONNECTION_ATTEMPTS = 1;
    private static final boolean ENABLE_PAIRING_BEHAVIOR = true;
    private static final BluetoothDevice BLUETOOTH_DEVICE = BluetoothAdapter.getDefaultAdapter()
            .getRemoteDevice("11:22:33:44:55:66");
    private static final String DEVICE_NAME = "DEVICE_NAME";
    private static final byte[] ACCOUNT_KEY = new byte[]{1, 3};
    private static final byte[] HASH_VALUE = new byte[]{7};

    private TestEventLogger mEventLogger;
    @Mock private TimingLogger mTimingLogger;
    @Mock private BluetoothAudioPairer mBluetoothAudioPairer;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        BluetoothAudioPairer.enableTestMode();
        FastPairDualConnection.enableTestMode();
        MockitoAnnotations.initMocks(this);

        doNothing().when(mBluetoothAudioPairer).connect(anyShort(), anyBoolean());
        mEventLogger = new TestEventLogger();
    }

    private FastPairDualConnection newFastPairDualConnection(
            String bleAddress, Preferences.Builder prefsBuilder) {
        return new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                bleAddress,
                prefsBuilder.build(),
                mEventLogger,
                mTimingLogger);
    }

    private FastPairDualConnection newFastPairDualConnection2(
            String bleAddress, Preferences.Builder prefsBuilder) {
        return new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                bleAddress,
                prefsBuilder.build(),
                mEventLogger);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testFastPairDualConnectionConstructor() {
        assertThat(newFastPairDualConnection(BLE_ADDRESS, Preferences.builder())).isNotNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testFastPairDualConnectionConstructor2() {
        assertThat(newFastPairDualConnection2(BLE_ADDRESS, Preferences.builder())).isNotNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAttemptConnectProfiles() {
        try {
            new FastPairDualConnection(
                    ApplicationProvider.getApplicationContext(),
                    BLE_ADDRESS,
                    Preferences.builder().build(),
                    mEventLogger,
                    mTimingLogger)
                    .attemptConnectProfiles(
                            mBluetoothAudioPairer,
                            MASKED_BLE_ADDRESS,
                            PROFILES,
                            NUM_CONNECTION_ATTEMPTS,
                            ENABLE_PAIRING_BEHAVIOR);
        } catch (PairingException e) {
            // Mocked pair doesn't throw Pairing Exception.
        }
    }


    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAppendMoreErrorCode_gattError() {
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED,
                        new BluetoothGattException("Test", 133)))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED + 133);
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED,
                        new BluetoothGattException("Test", 257)))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED + 257);
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED, new BluetoothException("Test")))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED);
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED,
                        new BluetoothOperationTimeoutException("Test")))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED + GATT_ERROR_CODE_TIMEOUT);
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST,
                        new BluetoothGattException("Test", 41)))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST + 41);
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST,
                        new BluetoothGattException("Test", 788)))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST + 788);
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST, new BluetoothException("Test")))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST);
        assertThat(
                appendMoreErrorCode(
                        GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST,
                        new BluetoothOperationTimeoutException("Test")))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST + GATT_ERROR_CODE_TIMEOUT);
        assertThat(appendMoreErrorCode(GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST, /* cause= */ null))
                .isEqualTo(GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testUnpairNotCrash() {
        try {
            new FastPairDualConnection(
                    ApplicationProvider.getApplicationContext(),
                    BLE_ADDRESS,
                    Preferences.builder().build(),
                    mEventLogger,
                    mTimingLogger).unpair(BLUETOOTH_DEVICE);
        } catch (ExecutionException | InterruptedException | ReflectionException
                | TimeoutException | PairingException e) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSetFastPairHistory() {
        new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().build(),
                mEventLogger,
                mTimingLogger).setFastPairHistory(ImmutableList.of());
    }


    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSetGetProviderDeviceName() {
        FastPairDualConnection connection = new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().build(),
                mEventLogger,
                mTimingLogger);
        connection.setProviderDeviceName(DEVICE_NAME);
        connection.getProviderDeviceName();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetExistingAccountKey() {
        FastPairDualConnection connection = new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().build(),
                mEventLogger,
                mTimingLogger);
        connection.getExistingAccountKey();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testPair() {
        FastPairDualConnection connection = new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().setNumSdpAttempts(0)
                        .setLogPairWithCachedModelId(false).build(),
                mEventLogger,
                mTimingLogger);
        try {
            connection.pair();
        } catch (BluetoothException | InterruptedException | ReflectionException
                | ExecutionException | TimeoutException | PairingException e) {
        }
    }


    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetPublicAddress() {
        FastPairDualConnection connection = new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().setNumSdpAttempts(0)
                        .setLogPairWithCachedModelId(false).build(),
                mEventLogger,
                mTimingLogger);
        connection.getPublicAddress();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testShouldWriteAccountKeyForExistingCase() {
        FastPairDualConnection connection = new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().setNumSdpAttempts(0)
                        .setLogPairWithCachedModelId(false).build(),
                mEventLogger,
                mTimingLogger);
        connection.shouldWriteAccountKeyForExistingCase(ACCOUNT_KEY);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testReadFirmwareVersion() {
        FastPairDualConnection connection = new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().setNumSdpAttempts(0)
                        .setLogPairWithCachedModelId(false).build(),
                mEventLogger,
                mTimingLogger);
        try {
            connection.readFirmwareVersion();
        } catch (BluetoothException | InterruptedException | ExecutionException
                | TimeoutException e) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testHistoryItem() {
        FastPairDualConnection connection = new FastPairDualConnection(
                ApplicationProvider.getApplicationContext(),
                BLE_ADDRESS,
                Preferences.builder().setNumSdpAttempts(0)
                        .setLogPairWithCachedModelId(false).build(),
                mEventLogger,
                mTimingLogger);
        ImmutableList.Builder<FastPairHistoryItem> historyBuilder = ImmutableList.builder();
        FastPairHistoryItem historyItem1 =
                FastPairHistoryItem.create(
                        ByteString.copyFrom(ACCOUNT_KEY), ByteString.copyFrom(HASH_VALUE));
        historyBuilder.add(historyItem1);

        connection.setFastPairHistory(historyBuilder.build());
        assertThat(connection.mPairedHistoryFinder.isInPairedHistory("11:22:33:44:55:88"))
                .isFalse();
    }

    static class TestEventLogger implements EventLogger {

        private List<Item> mLogs = new ArrayList<>();

        @Override
        public void logEventSucceeded(Event event) {
            mLogs.add(new Item(event));
        }

        @Override
        public void logEventFailed(Event event, Exception e) {
            mLogs.add(new ItemFailed(event, e));
        }

        List<Item> getErrorLogs() {
            return mLogs.stream().filter(item -> item instanceof ItemFailed)
                    .collect(Collectors.toList());
        }

        List<Item> getLogs() {
            return mLogs;
        }

        List<Item> getLast() {
            return mLogs.subList(mLogs.size() - 1, mLogs.size());
        }

        BluetoothDevice getDevice() {
            return Iterables.getLast(mLogs).mEvent.getBluetoothDevice();
        }

        public static class Item {

            final Event mEvent;

            Item(Event event) {
                this.mEvent = event;
            }

            @Override
            public String toString() {
                return "Item{" + "event=" + mEvent + '}';
            }
        }

        public static class ItemFailed extends Item {

            final Exception mException;

            ItemFailed(Event event, Exception e) {
                super(event);
                this.mException = e;
            }

            @Override
            public String toString() {
                return "ItemFailed{" + "event=" + mEvent + ", exception=" + mException + '}';
            }
        }
    }
}
