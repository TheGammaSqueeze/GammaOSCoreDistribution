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

import android.platform.test.annotations.Presubmit;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothAdapter;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

/**
 * Unit tests for {@link GattConnectionManager}.
 */
@Presubmit
@SmallTest
public class GattConnectionManagerTest extends TestCase {

    private static final String FAST_PAIR_ADDRESS = "BB:BB:BB:BB:BB:1E";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        GattConnectionManager.enableTestMode();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattConnectionManagerConstructor() throws Exception {
        GattConnectionManager manager = createManager(Preferences.builder());
        try {
            manager.getConnection();
        } catch (ExecutionException e) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testIsNoRetryError() {
        Preferences preferences =
                Preferences.builder()
                        .setGattConnectionAndSecretHandshakeNoRetryGattError(
                                ImmutableSet.of(257, 999))
                        .build();

        assertThat(
                GattConnectionManager.isNoRetryError(
                        preferences, new BluetoothGattException("Test", 133)))
                .isFalse();
        assertThat(
                GattConnectionManager.isNoRetryError(
                        preferences, new BluetoothGattException("Test", 257)))
                .isTrue();
        assertThat(
                GattConnectionManager.isNoRetryError(
                        preferences, new BluetoothGattException("Test", 999)))
                .isTrue();
        assertThat(GattConnectionManager.isNoRetryError(
                preferences, new BluetoothException("Test")))
                .isFalse();
        assertThat(
                GattConnectionManager.isNoRetryError(
                        preferences, new BluetoothOperationTimeoutException("Test")))
                .isFalse();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetTimeoutNotOverShortRetryMaxSpentTimeGetShort() {
        Preferences preferences = Preferences.builder().build();

        assertThat(
                createManager(Preferences.builder(), () -> {})
                        .getTimeoutMs(
                                preferences.getGattConnectShortTimeoutRetryMaxSpentTimeMs() - 1))
                .isEqualTo(preferences.getGattConnectShortTimeoutMs());
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetTimeoutOverShortRetryMaxSpentTimeGetLong() {
        Preferences preferences = Preferences.builder().build();

        assertThat(
                createManager(Preferences.builder(), () -> {})
                        .getTimeoutMs(
                                preferences.getGattConnectShortTimeoutRetryMaxSpentTimeMs() + 1))
                .isEqualTo(preferences.getGattConnectLongTimeoutMs());
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGetTimeoutRetryNotEnabledGetOrigin() {
        Preferences preferences = Preferences.builder().build();

        assertThat(
                createManager(
                        Preferences.builder().setRetryGattConnectionAndSecretHandshake(false),
                        () -> {})
                        .getTimeoutMs(0))
                .isEqualTo(Duration.ofSeconds(
                        preferences.getGattConnectionTimeoutSeconds()).toMillis());
    }

    private GattConnectionManager createManager(Preferences.Builder prefs) {
        return createManager(prefs, () -> {});
    }

    private GattConnectionManager createManager(
            Preferences.Builder prefs, ToggleBluetoothTask toggleBluetooth) {
        return createManager(prefs, toggleBluetooth,
                /* fastPairSignalChecker= */ null);
    }

    private GattConnectionManager createManager(
            Preferences.Builder prefs,
            ToggleBluetoothTask toggleBluetooth,
            @Nullable FastPairConnection.FastPairSignalChecker fastPairSignalChecker) {
        return new GattConnectionManager(
                ApplicationProvider.getApplicationContext(),
                prefs.build(),
                new EventLoggerWrapper(null),
                BluetoothAdapter.getDefaultAdapter(),
                toggleBluetooth,
                FAST_PAIR_ADDRESS,
                new TimingLogger("GattConnectionManager", prefs.build()),
                fastPairSignalChecker,
                /* setMtu= */ false);
    }
}
