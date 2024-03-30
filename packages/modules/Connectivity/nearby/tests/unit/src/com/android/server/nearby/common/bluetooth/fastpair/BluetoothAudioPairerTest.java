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

package com.android.server.nearby.common.bluetooth.fastpair;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.MockitoAnnotations.initMocks;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.Iterables;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Unit tests for {@link BluetoothAudioPairer}. */
@Presubmit
@SmallTest
public class BluetoothAudioPairerTest extends TestCase {

    private static final byte[] SECRET = new byte[]{3, 0};
    private static final boolean PRIVATE_INITIAL_PAIRING = false;
    private static final String EVENT_NAME = "EVENT_NAME";
    private static final BluetoothDevice BLUETOOTH_DEVICE = BluetoothAdapter.getDefaultAdapter()
            .getRemoteDevice("11:22:33:44:55:66");
    private static final int BOND_TIMEOUT_SECONDS = 1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        initMocks(this);
        BluetoothAudioPairer.enableTestMode();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testKeyBasedPairingInfoConstructor() {
        assertThat(new BluetoothAudioPairer.KeyBasedPairingInfo(
                SECRET,
                null /* GattConnectionManager */,
                PRIVATE_INITIAL_PAIRING)).isNotNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothAudioPairerConstructor() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            assertThat(new BluetoothAudioPairer(
                    context,
                    BLUETOOTH_DEVICE,
                    Preferences.builder().build(),
                    new EventLoggerWrapper(new TestEventLogger()),
                    null /* KeyBasePairingInfo */,
                    null /*PasskeyConfirmationHandler */,
                    new TimingLogger(EVENT_NAME, Preferences.builder().build()))).isNotNull();
        } catch (PairingException e) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothAudioPairerUnpairNoCrash() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            new BluetoothAudioPairer(
                    context,
                    BLUETOOTH_DEVICE,
                    Preferences.builder().build(),
                    new EventLoggerWrapper(new TestEventLogger()),
                    null /* KeyBasePairingInfo */,
                    null /*PasskeyConfirmationHandler */,
                    new TimingLogger(EVENT_NAME, Preferences.builder().build())).unpair();
        } catch (PairingException | InterruptedException | ExecutionException
                | TimeoutException e) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothAudioPairerPairNoCrash() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            new BluetoothAudioPairer(
                    context,
                    BLUETOOTH_DEVICE,
                    Preferences.builder().setCreateBondTimeoutSeconds(BOND_TIMEOUT_SECONDS).build(),
                    new EventLoggerWrapper(new TestEventLogger()),
                    null /* KeyBasePairingInfo */,
                    null /*PasskeyConfirmationHandler */,
                    new TimingLogger(EVENT_NAME, Preferences.builder().build())).pair();
        } catch (PairingException | InterruptedException | ExecutionException
                | TimeoutException e) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothAudioPairerConnectNoCrash() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            new BluetoothAudioPairer(
                    context,
                    BLUETOOTH_DEVICE,
                    Preferences.builder().setCreateBondTimeoutSeconds(BOND_TIMEOUT_SECONDS).build(),
                    new EventLoggerWrapper(new TestEventLogger()),
                    null /* KeyBasePairingInfo */,
                    null /*PasskeyConfirmationHandler */,
                    new TimingLogger(EVENT_NAME, Preferences.builder().build()))
                    .connect(Constants.A2DP_SINK_SERVICE_UUID, true /* enable pairing behavior */);
        } catch (PairingException | InterruptedException | ExecutionException
                | TimeoutException | ReflectionException e) {
        }
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
