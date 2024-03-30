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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link Preferences}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PreferencesTest {

    private static final int FIRST_INT = 1505;
    private static final int SECOND_INT = 1506;
    private static final boolean FIRST_BOOL = true;
    private static final boolean SECOND_BOOL = false;
    private static final short FIRST_SHORT = 32;
    private static final short SECOND_SHORT = 73;
    private static final long FIRST_LONG = 9838L;
    private static final long SECOND_LONG = 93935L;
    private static final String FIRST_STRING = "FIRST_STRING";
    private static final String SECOND_STRING = "SECOND_STRING";
    private static final byte[] FIRST_BYTES = new byte[] {7, 9};
    private static final byte[] SECOND_BYTES = new byte[] {2};
    private static final ImmutableSet<Integer> FIRST_INT_SETS = ImmutableSet.of(6, 8);
    private static final ImmutableSet<Integer> SECOND_INT_SETS = ImmutableSet.of(6, 8);
    private static final Preferences.ExtraLoggingInformation FIRST_EXTRA_LOGGING_INFO =
            Preferences.ExtraLoggingInformation.builder().setModelId("000006").build();
    private static final Preferences.ExtraLoggingInformation SECOND_EXTRA_LOGGING_INFO =
            Preferences.ExtraLoggingInformation.builder().setModelId("000007").build();

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattOperationTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setGattOperationTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getGattOperationTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getGattOperationTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setGattOperationTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getGattOperationTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattConnectionTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setGattConnectionTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getGattConnectionTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getGattConnectionTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setGattConnectionTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getGattConnectionTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothToggleTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setBluetoothToggleTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getBluetoothToggleTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getBluetoothToggleTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setBluetoothToggleTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getBluetoothToggleTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothToggleSleepSeconds() {
        Preferences prefs =
                Preferences.builder().setBluetoothToggleSleepSeconds(FIRST_INT).build();
        assertThat(prefs.getBluetoothToggleSleepSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getBluetoothToggleSleepSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setBluetoothToggleSleepSeconds(SECOND_INT).build();
        assertThat(prefs2.getBluetoothToggleSleepSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testClassicDiscoveryTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setClassicDiscoveryTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getClassicDiscoveryTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getClassicDiscoveryTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setClassicDiscoveryTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getClassicDiscoveryTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNumDiscoverAttempts() {
        Preferences prefs =
                Preferences.builder().setNumDiscoverAttempts(FIRST_INT).build();
        assertThat(prefs.getNumDiscoverAttempts()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getNumDiscoverAttempts())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setNumDiscoverAttempts(SECOND_INT).build();
        assertThat(prefs2.getNumDiscoverAttempts()).isEqualTo(SECOND_INT);
    }


    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testDiscoveryRetrySleepSeconds() {
        Preferences prefs =
                Preferences.builder().setDiscoveryRetrySleepSeconds(FIRST_INT).build();
        assertThat(prefs.getDiscoveryRetrySleepSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getDiscoveryRetrySleepSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setDiscoveryRetrySleepSeconds(SECOND_INT).build();
        assertThat(prefs2.getDiscoveryRetrySleepSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSdpTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setSdpTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getSdpTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getSdpTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setSdpTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getSdpTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNumSdpAttempts() {
        Preferences prefs =
                Preferences.builder().setNumSdpAttempts(FIRST_INT).build();
        assertThat(prefs.getNumSdpAttempts()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getNumSdpAttempts())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setNumSdpAttempts(SECOND_INT).build();
        assertThat(prefs2.getNumSdpAttempts()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNumCreateBondAttempts() {
        Preferences prefs =
                Preferences.builder().setNumCreateBondAttempts(FIRST_INT).build();
        assertThat(prefs.getNumCreateBondAttempts()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getNumCreateBondAttempts())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setNumCreateBondAttempts(SECOND_INT).build();
        assertThat(prefs2.getNumCreateBondAttempts()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNumConnectAttempts() {
        Preferences prefs =
                Preferences.builder().setNumConnectAttempts(FIRST_INT).build();
        assertThat(prefs.getNumConnectAttempts()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getNumConnectAttempts())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setNumConnectAttempts(SECOND_INT).build();
        assertThat(prefs2.getNumConnectAttempts()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNumWriteAccountKeyAttempts() {
        Preferences prefs =
                Preferences.builder().setNumWriteAccountKeyAttempts(FIRST_INT).build();
        assertThat(prefs.getNumWriteAccountKeyAttempts()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getNumWriteAccountKeyAttempts())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setNumWriteAccountKeyAttempts(SECOND_INT).build();
        assertThat(prefs2.getNumWriteAccountKeyAttempts()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothStatePollingMillis() {
        Preferences prefs =
                Preferences.builder().setBluetoothStatePollingMillis(FIRST_INT).build();
        assertThat(prefs.getBluetoothStatePollingMillis()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getBluetoothStatePollingMillis())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setBluetoothStatePollingMillis(SECOND_INT).build();
        assertThat(prefs2.getBluetoothStatePollingMillis()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNumAttempts() {
        Preferences prefs =
                Preferences.builder().setNumAttempts(FIRST_INT).build();
        assertThat(prefs.getNumAttempts()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getNumAttempts())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setNumAttempts(SECOND_INT).build();
        assertThat(prefs2.getNumAttempts()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testRemoveBondTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setRemoveBondTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getRemoveBondTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getRemoveBondTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setRemoveBondTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getRemoveBondTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testRemoveBondSleepMillis() {
        Preferences prefs =
                Preferences.builder().setRemoveBondSleepMillis(FIRST_INT).build();
        assertThat(prefs.getRemoveBondSleepMillis()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getRemoveBondSleepMillis())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setRemoveBondSleepMillis(SECOND_INT).build();
        assertThat(prefs2.getRemoveBondSleepMillis()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testCreateBondTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setCreateBondTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getCreateBondTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getCreateBondTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setCreateBondTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getCreateBondTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testHidCreateBondTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setHidCreateBondTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getHidCreateBondTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getHidCreateBondTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setHidCreateBondTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getHidCreateBondTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testProxyTimeoutSeconds() {
        Preferences prefs =
                Preferences.builder().setProxyTimeoutSeconds(FIRST_INT).build();
        assertThat(prefs.getProxyTimeoutSeconds()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getProxyTimeoutSeconds())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setProxyTimeoutSeconds(SECOND_INT).build();
        assertThat(prefs2.getProxyTimeoutSeconds()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWriteAccountKeySleepMillis() {
        Preferences prefs =
                Preferences.builder().setWriteAccountKeySleepMillis(FIRST_INT).build();
        assertThat(prefs.getWriteAccountKeySleepMillis()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getWriteAccountKeySleepMillis())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setWriteAccountKeySleepMillis(SECOND_INT).build();
        assertThat(prefs2.getWriteAccountKeySleepMillis()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testPairFailureCounts() {
        Preferences prefs =
                Preferences.builder().setPairFailureCounts(FIRST_INT).build();
        assertThat(prefs.getPairFailureCounts()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getPairFailureCounts())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setPairFailureCounts(SECOND_INT).build();
        assertThat(prefs2.getPairFailureCounts()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testCreateBondTransportType() {
        Preferences prefs =
                Preferences.builder().setCreateBondTransportType(FIRST_INT).build();
        assertThat(prefs.getCreateBondTransportType()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getCreateBondTransportType())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setCreateBondTransportType(SECOND_INT).build();
        assertThat(prefs2.getCreateBondTransportType()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattConnectRetryTimeoutMillis() {
        Preferences prefs =
                Preferences.builder().setGattConnectRetryTimeoutMillis(FIRST_INT).build();
        assertThat(prefs.getGattConnectRetryTimeoutMillis()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getGattConnectRetryTimeoutMillis())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setGattConnectRetryTimeoutMillis(SECOND_INT).build();
        assertThat(prefs2.getGattConnectRetryTimeoutMillis()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testNumSdpAttemptsAfterBonded() {
        Preferences prefs =
                Preferences.builder().setNumSdpAttemptsAfterBonded(FIRST_INT).build();
        assertThat(prefs.getNumSdpAttemptsAfterBonded()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getNumSdpAttemptsAfterBonded())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setNumSdpAttemptsAfterBonded(SECOND_INT).build();
        assertThat(prefs2.getNumSdpAttemptsAfterBonded()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSameModelIdPairedDeviceCount() {
        Preferences prefs =
                Preferences.builder().setSameModelIdPairedDeviceCount(FIRST_INT).build();
        assertThat(prefs.getSameModelIdPairedDeviceCount()).isEqualTo(FIRST_INT);
        assertThat(prefs.toBuilder().build().getSameModelIdPairedDeviceCount())
                .isEqualTo(FIRST_INT);

        Preferences prefs2 =
                Preferences.builder().setSameModelIdPairedDeviceCount(SECOND_INT).build();
        assertThat(prefs2.getSameModelIdPairedDeviceCount()).isEqualTo(SECOND_INT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testIgnoreDiscoveryError() {
        Preferences prefs =
                Preferences.builder().setIgnoreDiscoveryError(FIRST_BOOL).build();
        assertThat(prefs.getIgnoreDiscoveryError()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getIgnoreDiscoveryError())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setIgnoreDiscoveryError(SECOND_BOOL).build();
        assertThat(prefs2.getIgnoreDiscoveryError()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testToggleBluetoothOnFailure() {
        Preferences prefs =
                Preferences.builder().setToggleBluetoothOnFailure(FIRST_BOOL).build();
        assertThat(prefs.getToggleBluetoothOnFailure()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getToggleBluetoothOnFailure())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setToggleBluetoothOnFailure(SECOND_BOOL).build();
        assertThat(prefs2.getToggleBluetoothOnFailure()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothStateUsesPolling() {
        Preferences prefs =
                Preferences.builder().setBluetoothStateUsesPolling(FIRST_BOOL).build();
        assertThat(prefs.getBluetoothStateUsesPolling()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getBluetoothStateUsesPolling())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setBluetoothStateUsesPolling(SECOND_BOOL).build();
        assertThat(prefs2.getBluetoothStateUsesPolling()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnableBrEdrHandover() {
        Preferences prefs =
                Preferences.builder().setEnableBrEdrHandover(FIRST_BOOL).build();
        assertThat(prefs.getEnableBrEdrHandover()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnableBrEdrHandover())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnableBrEdrHandover(SECOND_BOOL).build();
        assertThat(prefs2.getEnableBrEdrHandover()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWaitForUuidsAfterBonding() {
        Preferences prefs =
                Preferences.builder().setWaitForUuidsAfterBonding(FIRST_BOOL).build();
        assertThat(prefs.getWaitForUuidsAfterBonding()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getWaitForUuidsAfterBonding())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setWaitForUuidsAfterBonding(SECOND_BOOL).build();
        assertThat(prefs2.getWaitForUuidsAfterBonding()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testReceiveUuidsAndBondedEventBeforeClose() {
        Preferences prefs =
                Preferences.builder().setReceiveUuidsAndBondedEventBeforeClose(FIRST_BOOL).build();
        assertThat(prefs.getReceiveUuidsAndBondedEventBeforeClose()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getReceiveUuidsAndBondedEventBeforeClose())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setReceiveUuidsAndBondedEventBeforeClose(SECOND_BOOL).build();
        assertThat(prefs2.getReceiveUuidsAndBondedEventBeforeClose()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testRejectPhonebookAccess() {
        Preferences prefs =
                Preferences.builder().setRejectPhonebookAccess(FIRST_BOOL).build();
        assertThat(prefs.getRejectPhonebookAccess()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getRejectPhonebookAccess())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setRejectPhonebookAccess(SECOND_BOOL).build();
        assertThat(prefs2.getRejectPhonebookAccess()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testRejectMessageAccess() {
        Preferences prefs =
                Preferences.builder().setRejectMessageAccess(FIRST_BOOL).build();
        assertThat(prefs.getRejectMessageAccess()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getRejectMessageAccess())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setRejectMessageAccess(SECOND_BOOL).build();
        assertThat(prefs2.getRejectMessageAccess()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testRejectSimAccess() {
        Preferences prefs =
                Preferences.builder().setRejectSimAccess(FIRST_BOOL).build();
        assertThat(prefs.getRejectSimAccess()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getRejectSimAccess())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setRejectSimAccess(SECOND_BOOL).build();
        assertThat(prefs2.getRejectSimAccess()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSkipDisconnectingGattBeforeWritingAccountKey() {
        Preferences prefs =
                Preferences.builder().setSkipDisconnectingGattBeforeWritingAccountKey(FIRST_BOOL)
                        .build();
        assertThat(prefs.getSkipDisconnectingGattBeforeWritingAccountKey()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getSkipDisconnectingGattBeforeWritingAccountKey())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setSkipDisconnectingGattBeforeWritingAccountKey(SECOND_BOOL)
                        .build();
        assertThat(prefs2.getSkipDisconnectingGattBeforeWritingAccountKey()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testMoreEventLogForQuality() {
        Preferences prefs =
                Preferences.builder().setMoreEventLogForQuality(FIRST_BOOL).build();
        assertThat(prefs.getMoreEventLogForQuality()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getMoreEventLogForQuality())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setMoreEventLogForQuality(SECOND_BOOL).build();
        assertThat(prefs2.getMoreEventLogForQuality()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testRetryGattConnectionAndSecretHandshake() {
        Preferences prefs =
                Preferences.builder().setRetryGattConnectionAndSecretHandshake(FIRST_BOOL).build();
        assertThat(prefs.getRetryGattConnectionAndSecretHandshake()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getRetryGattConnectionAndSecretHandshake())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setRetryGattConnectionAndSecretHandshake(SECOND_BOOL).build();
        assertThat(prefs2.getRetryGattConnectionAndSecretHandshake()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testRetrySecretHandshakeTimeout() {
        Preferences prefs =
                Preferences.builder().setRetrySecretHandshakeTimeout(FIRST_BOOL).build();
        assertThat(prefs.getRetrySecretHandshakeTimeout()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getRetrySecretHandshakeTimeout())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setRetrySecretHandshakeTimeout(SECOND_BOOL).build();
        assertThat(prefs2.getRetrySecretHandshakeTimeout()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testLogUserManualRetry() {
        Preferences prefs =
                Preferences.builder().setLogUserManualRetry(FIRST_BOOL).build();
        assertThat(prefs.getLogUserManualRetry()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getLogUserManualRetry())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setLogUserManualRetry(SECOND_BOOL).build();
        assertThat(prefs2.getLogUserManualRetry()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testIsDeviceFinishCheckAddressFromCache() {
        Preferences prefs =
                Preferences.builder().setIsDeviceFinishCheckAddressFromCache(FIRST_BOOL).build();
        assertThat(prefs.getIsDeviceFinishCheckAddressFromCache()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getIsDeviceFinishCheckAddressFromCache())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setIsDeviceFinishCheckAddressFromCache(SECOND_BOOL).build();
        assertThat(prefs2.getIsDeviceFinishCheckAddressFromCache()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testLogPairWithCachedModelId() {
        Preferences prefs =
                Preferences.builder().setLogPairWithCachedModelId(FIRST_BOOL).build();
        assertThat(prefs.getLogPairWithCachedModelId()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getLogPairWithCachedModelId())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setLogPairWithCachedModelId(SECOND_BOOL).build();
        assertThat(prefs2.getLogPairWithCachedModelId()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testDirectConnectProfileIfModelIdInCache() {
        Preferences prefs =
                Preferences.builder().setDirectConnectProfileIfModelIdInCache(FIRST_BOOL).build();
        assertThat(prefs.getDirectConnectProfileIfModelIdInCache()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getDirectConnectProfileIfModelIdInCache())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setDirectConnectProfileIfModelIdInCache(SECOND_BOOL).build();
        assertThat(prefs2.getDirectConnectProfileIfModelIdInCache()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAcceptPasskey() {
        Preferences prefs =
                Preferences.builder().setAcceptPasskey(FIRST_BOOL).build();
        assertThat(prefs.getAcceptPasskey()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getAcceptPasskey())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setAcceptPasskey(SECOND_BOOL).build();
        assertThat(prefs2.getAcceptPasskey()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testProviderInitiatesBondingIfSupported() {
        Preferences prefs =
                Preferences.builder().setProviderInitiatesBondingIfSupported(FIRST_BOOL).build();
        assertThat(prefs.getProviderInitiatesBondingIfSupported()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getProviderInitiatesBondingIfSupported())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setProviderInitiatesBondingIfSupported(SECOND_BOOL).build();
        assertThat(prefs2.getProviderInitiatesBondingIfSupported()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAttemptDirectConnectionWhenPreviouslyBonded() {
        Preferences prefs =
                Preferences.builder()
                        .setAttemptDirectConnectionWhenPreviouslyBonded(FIRST_BOOL).build();
        assertThat(prefs.getAttemptDirectConnectionWhenPreviouslyBonded()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getAttemptDirectConnectionWhenPreviouslyBonded())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder()
                        .setAttemptDirectConnectionWhenPreviouslyBonded(SECOND_BOOL).build();
        assertThat(prefs2.getAttemptDirectConnectionWhenPreviouslyBonded()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAutomaticallyReconnectGattWhenNeeded() {
        Preferences prefs =
                Preferences.builder().setAutomaticallyReconnectGattWhenNeeded(FIRST_BOOL).build();
        assertThat(prefs.getAutomaticallyReconnectGattWhenNeeded()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getAutomaticallyReconnectGattWhenNeeded())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setAutomaticallyReconnectGattWhenNeeded(SECOND_BOOL).build();
        assertThat(prefs2.getAutomaticallyReconnectGattWhenNeeded()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSkipConnectingProfiles() {
        Preferences prefs =
                Preferences.builder().setSkipConnectingProfiles(FIRST_BOOL).build();
        assertThat(prefs.getSkipConnectingProfiles()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getSkipConnectingProfiles())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setSkipConnectingProfiles(SECOND_BOOL).build();
        assertThat(prefs2.getSkipConnectingProfiles()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testIgnoreUuidTimeoutAfterBonded() {
        Preferences prefs =
                Preferences.builder().setIgnoreUuidTimeoutAfterBonded(FIRST_BOOL).build();
        assertThat(prefs.getIgnoreUuidTimeoutAfterBonded()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getIgnoreUuidTimeoutAfterBonded())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setIgnoreUuidTimeoutAfterBonded(SECOND_BOOL).build();
        assertThat(prefs2.getIgnoreUuidTimeoutAfterBonded()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSpecifyCreateBondTransportType() {
        Preferences prefs =
                Preferences.builder().setSpecifyCreateBondTransportType(FIRST_BOOL).build();
        assertThat(prefs.getSpecifyCreateBondTransportType()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getSpecifyCreateBondTransportType())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setSpecifyCreateBondTransportType(SECOND_BOOL).build();
        assertThat(prefs2.getSpecifyCreateBondTransportType()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testIncreaseIntentFilterPriority() {
        Preferences prefs =
                Preferences.builder().setIncreaseIntentFilterPriority(FIRST_BOOL).build();
        assertThat(prefs.getIncreaseIntentFilterPriority()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getIncreaseIntentFilterPriority())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setIncreaseIntentFilterPriority(SECOND_BOOL).build();
        assertThat(prefs2.getIncreaseIntentFilterPriority()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEvaluatePerformance() {
        Preferences prefs =
                Preferences.builder().setEvaluatePerformance(FIRST_BOOL).build();
        assertThat(prefs.getEvaluatePerformance()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEvaluatePerformance())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEvaluatePerformance(SECOND_BOOL).build();
        assertThat(prefs2.getEvaluatePerformance()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnableNamingCharacteristic() {
        Preferences prefs =
                Preferences.builder().setEnableNamingCharacteristic(FIRST_BOOL).build();
        assertThat(prefs.getEnableNamingCharacteristic()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnableNamingCharacteristic())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnableNamingCharacteristic(SECOND_BOOL).build();
        assertThat(prefs2.getEnableNamingCharacteristic()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnableFirmwareVersionCharacteristic() {
        Preferences prefs =
                Preferences.builder().setEnableFirmwareVersionCharacteristic(FIRST_BOOL).build();
        assertThat(prefs.getEnableFirmwareVersionCharacteristic()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnableFirmwareVersionCharacteristic())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnableFirmwareVersionCharacteristic(SECOND_BOOL).build();
        assertThat(prefs2.getEnableFirmwareVersionCharacteristic()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testKeepSameAccountKeyWrite() {
        Preferences prefs =
                Preferences.builder().setKeepSameAccountKeyWrite(FIRST_BOOL).build();
        assertThat(prefs.getKeepSameAccountKeyWrite()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getKeepSameAccountKeyWrite())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setKeepSameAccountKeyWrite(SECOND_BOOL).build();
        assertThat(prefs2.getKeepSameAccountKeyWrite()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testIsRetroactivePairing() {
        Preferences prefs =
                Preferences.builder().setIsRetroactivePairing(FIRST_BOOL).build();
        assertThat(prefs.getIsRetroactivePairing()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getIsRetroactivePairing())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setIsRetroactivePairing(SECOND_BOOL).build();
        assertThat(prefs2.getIsRetroactivePairing()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSupportHidDevice() {
        Preferences prefs =
                Preferences.builder().setSupportHidDevice(FIRST_BOOL).build();
        assertThat(prefs.getSupportHidDevice()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getSupportHidDevice())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setSupportHidDevice(SECOND_BOOL).build();
        assertThat(prefs2.getSupportHidDevice()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnablePairingWhileDirectlyConnecting() {
        Preferences prefs =
                Preferences.builder().setEnablePairingWhileDirectlyConnecting(FIRST_BOOL).build();
        assertThat(prefs.getEnablePairingWhileDirectlyConnecting()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnablePairingWhileDirectlyConnecting())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnablePairingWhileDirectlyConnecting(SECOND_BOOL).build();
        assertThat(prefs2.getEnablePairingWhileDirectlyConnecting()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAcceptConsentForFastPairOne() {
        Preferences prefs =
                Preferences.builder().setAcceptConsentForFastPairOne(FIRST_BOOL).build();
        assertThat(prefs.getAcceptConsentForFastPairOne()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getAcceptConsentForFastPairOne())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setAcceptConsentForFastPairOne(SECOND_BOOL).build();
        assertThat(prefs2.getAcceptConsentForFastPairOne()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnable128BitCustomGattCharacteristicsId() {
        Preferences prefs =
                Preferences.builder().setEnable128BitCustomGattCharacteristicsId(FIRST_BOOL)
                        .build();
        assertThat(prefs.getEnable128BitCustomGattCharacteristicsId()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnable128BitCustomGattCharacteristicsId())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnable128BitCustomGattCharacteristicsId(SECOND_BOOL)
                        .build();
        assertThat(prefs2.getEnable128BitCustomGattCharacteristicsId()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnableSendExceptionStepToValidator() {
        Preferences prefs =
                Preferences.builder().setEnableSendExceptionStepToValidator(FIRST_BOOL).build();
        assertThat(prefs.getEnableSendExceptionStepToValidator()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnableSendExceptionStepToValidator())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnableSendExceptionStepToValidator(SECOND_BOOL).build();
        assertThat(prefs2.getEnableSendExceptionStepToValidator()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnableAdditionalDataTypeWhenActionOverBle() {
        Preferences prefs =
                Preferences.builder().setEnableAdditionalDataTypeWhenActionOverBle(FIRST_BOOL)
                        .build();
        assertThat(prefs.getEnableAdditionalDataTypeWhenActionOverBle()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnableAdditionalDataTypeWhenActionOverBle())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnableAdditionalDataTypeWhenActionOverBle(SECOND_BOOL)
                        .build();
        assertThat(prefs2.getEnableAdditionalDataTypeWhenActionOverBle()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testCheckBondStateWhenSkipConnectingProfiles() {
        Preferences prefs =
                Preferences.builder().setCheckBondStateWhenSkipConnectingProfiles(FIRST_BOOL)
                        .build();
        assertThat(prefs.getCheckBondStateWhenSkipConnectingProfiles()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getCheckBondStateWhenSkipConnectingProfiles())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setCheckBondStateWhenSkipConnectingProfiles(SECOND_BOOL)
                        .build();
        assertThat(prefs2.getCheckBondStateWhenSkipConnectingProfiles()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testHandlePasskeyConfirmationByUi() {
        Preferences prefs =
                Preferences.builder().setHandlePasskeyConfirmationByUi(FIRST_BOOL).build();
        assertThat(prefs.getHandlePasskeyConfirmationByUi()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getHandlePasskeyConfirmationByUi())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setHandlePasskeyConfirmationByUi(SECOND_BOOL).build();
        assertThat(prefs2.getHandlePasskeyConfirmationByUi()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testEnablePairFlowShowUiWithoutProfileConnection() {
        Preferences prefs =
                Preferences.builder().setEnablePairFlowShowUiWithoutProfileConnection(FIRST_BOOL)
                        .build();
        assertThat(prefs.getEnablePairFlowShowUiWithoutProfileConnection()).isEqualTo(FIRST_BOOL);
        assertThat(prefs.toBuilder().build().getEnablePairFlowShowUiWithoutProfileConnection())
                .isEqualTo(FIRST_BOOL);

        Preferences prefs2 =
                Preferences.builder().setEnablePairFlowShowUiWithoutProfileConnection(SECOND_BOOL)
                        .build();
        assertThat(prefs2.getEnablePairFlowShowUiWithoutProfileConnection()).isEqualTo(SECOND_BOOL);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBrHandoverDataCharacteristicId() {
        Preferences prefs =
                Preferences.builder().setBrHandoverDataCharacteristicId(FIRST_SHORT).build();
        assertThat(prefs.getBrHandoverDataCharacteristicId()).isEqualTo(FIRST_SHORT);
        assertThat(prefs.toBuilder().build().getBrHandoverDataCharacteristicId())
                .isEqualTo(FIRST_SHORT);

        Preferences prefs2 =
                Preferences.builder().setBrHandoverDataCharacteristicId(SECOND_SHORT).build();
        assertThat(prefs2.getBrHandoverDataCharacteristicId()).isEqualTo(SECOND_SHORT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothSigDataCharacteristicId() {
        Preferences prefs =
                Preferences.builder().setBluetoothSigDataCharacteristicId(FIRST_SHORT).build();
        assertThat(prefs.getBluetoothSigDataCharacteristicId()).isEqualTo(FIRST_SHORT);
        assertThat(prefs.toBuilder().build().getBluetoothSigDataCharacteristicId())
                .isEqualTo(FIRST_SHORT);

        Preferences prefs2 =
                Preferences.builder().setBluetoothSigDataCharacteristicId(SECOND_SHORT).build();
        assertThat(prefs2.getBluetoothSigDataCharacteristicId()).isEqualTo(SECOND_SHORT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testFirmwareVersionCharacteristicId() {
        Preferences prefs =
                Preferences.builder().setFirmwareVersionCharacteristicId(FIRST_SHORT).build();
        assertThat(prefs.getFirmwareVersionCharacteristicId()).isEqualTo(FIRST_SHORT);
        assertThat(prefs.toBuilder().build().getFirmwareVersionCharacteristicId())
                .isEqualTo(FIRST_SHORT);

        Preferences prefs2 =
                Preferences.builder().setFirmwareVersionCharacteristicId(SECOND_SHORT).build();
        assertThat(prefs2.getFirmwareVersionCharacteristicId()).isEqualTo(SECOND_SHORT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBrTransportBlockDataDescriptorId() {
        Preferences prefs =
                Preferences.builder().setBrTransportBlockDataDescriptorId(FIRST_SHORT).build();
        assertThat(prefs.getBrTransportBlockDataDescriptorId()).isEqualTo(FIRST_SHORT);
        assertThat(prefs.toBuilder().build().getBrTransportBlockDataDescriptorId())
                .isEqualTo(FIRST_SHORT);

        Preferences prefs2 =
                Preferences.builder().setBrTransportBlockDataDescriptorId(SECOND_SHORT).build();
        assertThat(prefs2.getBrTransportBlockDataDescriptorId()).isEqualTo(SECOND_SHORT);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattConnectShortTimeoutMs() {
        Preferences prefs =
                Preferences.builder().setGattConnectShortTimeoutMs(FIRST_LONG).build();
        assertThat(prefs.getGattConnectShortTimeoutMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getGattConnectShortTimeoutMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setGattConnectShortTimeoutMs(SECOND_LONG).build();
        assertThat(prefs2.getGattConnectShortTimeoutMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattConnectLongTimeoutMs() {
        Preferences prefs =
                Preferences.builder().setGattConnectLongTimeoutMs(FIRST_LONG).build();
        assertThat(prefs.getGattConnectLongTimeoutMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getGattConnectLongTimeoutMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setGattConnectLongTimeoutMs(SECOND_LONG).build();
        assertThat(prefs2.getGattConnectLongTimeoutMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattConnectShortTimeoutRetryMaxSpentTimeMs() {
        Preferences prefs =
                Preferences.builder().setGattConnectShortTimeoutRetryMaxSpentTimeMs(FIRST_LONG)
                        .build();
        assertThat(prefs.getGattConnectShortTimeoutRetryMaxSpentTimeMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getGattConnectShortTimeoutRetryMaxSpentTimeMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setGattConnectShortTimeoutRetryMaxSpentTimeMs(SECOND_LONG)
                        .build();
        assertThat(prefs2.getGattConnectShortTimeoutRetryMaxSpentTimeMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAddressRotateRetryMaxSpentTimeMs() {
        Preferences prefs =
                Preferences.builder().setAddressRotateRetryMaxSpentTimeMs(FIRST_LONG).build();
        assertThat(prefs.getAddressRotateRetryMaxSpentTimeMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getAddressRotateRetryMaxSpentTimeMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setAddressRotateRetryMaxSpentTimeMs(SECOND_LONG).build();
        assertThat(prefs2.getAddressRotateRetryMaxSpentTimeMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testPairingRetryDelayMs() {
        Preferences prefs =
                Preferences.builder().setPairingRetryDelayMs(FIRST_LONG).build();
        assertThat(prefs.getPairingRetryDelayMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getPairingRetryDelayMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setPairingRetryDelayMs(SECOND_LONG).build();
        assertThat(prefs2.getPairingRetryDelayMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSecretHandshakeShortTimeoutMs() {
        Preferences prefs =
                Preferences.builder().setSecretHandshakeShortTimeoutMs(FIRST_LONG).build();
        assertThat(prefs.getSecretHandshakeShortTimeoutMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getSecretHandshakeShortTimeoutMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setSecretHandshakeShortTimeoutMs(SECOND_LONG).build();
        assertThat(prefs2.getSecretHandshakeShortTimeoutMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSecretHandshakeLongTimeoutMs() {
        Preferences prefs =
                Preferences.builder().setSecretHandshakeLongTimeoutMs(FIRST_LONG).build();
        assertThat(prefs.getSecretHandshakeLongTimeoutMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getSecretHandshakeLongTimeoutMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setSecretHandshakeLongTimeoutMs(SECOND_LONG).build();
        assertThat(prefs2.getSecretHandshakeLongTimeoutMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSecretHandshakeShortTimeoutRetryMaxSpentTimeMs() {
        Preferences prefs =
                Preferences.builder().setSecretHandshakeShortTimeoutRetryMaxSpentTimeMs(FIRST_LONG)
                        .build();
        assertThat(prefs.getSecretHandshakeShortTimeoutRetryMaxSpentTimeMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getSecretHandshakeShortTimeoutRetryMaxSpentTimeMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setSecretHandshakeShortTimeoutRetryMaxSpentTimeMs(SECOND_LONG)
                        .build();
        assertThat(prefs2.getSecretHandshakeShortTimeoutRetryMaxSpentTimeMs())
                .isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSecretHandshakeLongTimeoutRetryMaxSpentTimeMs() {
        Preferences prefs =
                Preferences.builder().setSecretHandshakeLongTimeoutRetryMaxSpentTimeMs(FIRST_LONG)
                        .build();
        assertThat(prefs.getSecretHandshakeLongTimeoutRetryMaxSpentTimeMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getSecretHandshakeLongTimeoutRetryMaxSpentTimeMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setSecretHandshakeLongTimeoutRetryMaxSpentTimeMs(SECOND_LONG)
                        .build();
        assertThat(prefs2.getSecretHandshakeLongTimeoutRetryMaxSpentTimeMs())
                .isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSecretHandshakeRetryAttempts() {
        Preferences prefs =
                Preferences.builder().setSecretHandshakeRetryAttempts(FIRST_LONG).build();
        assertThat(prefs.getSecretHandshakeRetryAttempts()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getSecretHandshakeRetryAttempts())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setSecretHandshakeRetryAttempts(SECOND_LONG).build();
        assertThat(prefs2.getSecretHandshakeRetryAttempts()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSecretHandshakeRetryGattConnectionMaxSpentTimeMs() {
        Preferences prefs =
                Preferences.builder()
                        .setSecretHandshakeRetryGattConnectionMaxSpentTimeMs(FIRST_LONG).build();
        assertThat(prefs.getSecretHandshakeRetryGattConnectionMaxSpentTimeMs())
                .isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getSecretHandshakeRetryGattConnectionMaxSpentTimeMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setSecretHandshakeRetryGattConnectionMaxSpentTimeMs(
                        SECOND_LONG).build();
        assertThat(prefs2.getSecretHandshakeRetryGattConnectionMaxSpentTimeMs())
                .isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSignalLostRetryMaxSpentTimeMs() {
        Preferences prefs =
                Preferences.builder().setSignalLostRetryMaxSpentTimeMs(FIRST_LONG).build();
        assertThat(prefs.getSignalLostRetryMaxSpentTimeMs()).isEqualTo(FIRST_LONG);
        assertThat(prefs.toBuilder().build().getSignalLostRetryMaxSpentTimeMs())
                .isEqualTo(FIRST_LONG);

        Preferences prefs2 =
                Preferences.builder().setSignalLostRetryMaxSpentTimeMs(SECOND_LONG).build();
        assertThat(prefs2.getSignalLostRetryMaxSpentTimeMs()).isEqualTo(SECOND_LONG);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testCachedDeviceAddress() {
        Preferences prefs =
                Preferences.builder().setCachedDeviceAddress(FIRST_STRING).build();
        assertThat(prefs.getCachedDeviceAddress()).isEqualTo(FIRST_STRING);
        assertThat(prefs.toBuilder().build().getCachedDeviceAddress())
                .isEqualTo(FIRST_STRING);

        Preferences prefs2 =
                Preferences.builder().setCachedDeviceAddress(SECOND_STRING).build();
        assertThat(prefs2.getCachedDeviceAddress()).isEqualTo(SECOND_STRING);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testPossibleCachedDeviceAddress() {
        Preferences prefs =
                Preferences.builder().setPossibleCachedDeviceAddress(FIRST_STRING).build();
        assertThat(prefs.getPossibleCachedDeviceAddress()).isEqualTo(FIRST_STRING);
        assertThat(prefs.toBuilder().build().getPossibleCachedDeviceAddress())
                .isEqualTo(FIRST_STRING);

        Preferences prefs2 =
                Preferences.builder().setPossibleCachedDeviceAddress(SECOND_STRING).build();
        assertThat(prefs2.getPossibleCachedDeviceAddress()).isEqualTo(SECOND_STRING);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSupportedProfileUuids() {
        Preferences prefs =
                Preferences.builder().setSupportedProfileUuids(FIRST_BYTES).build();
        assertThat(prefs.getSupportedProfileUuids()).isEqualTo(FIRST_BYTES);
        assertThat(prefs.toBuilder().build().getSupportedProfileUuids())
                .isEqualTo(FIRST_BYTES);

        Preferences prefs2 =
                Preferences.builder().setSupportedProfileUuids(SECOND_BYTES).build();
        assertThat(prefs2.getSupportedProfileUuids()).isEqualTo(SECOND_BYTES);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testGattConnectionAndSecretHandshakeNoRetryGattError() {
        Preferences prefs =
                Preferences.builder().setGattConnectionAndSecretHandshakeNoRetryGattError(
                        FIRST_INT_SETS).build();
        assertThat(prefs.getGattConnectionAndSecretHandshakeNoRetryGattError())
                .isEqualTo(FIRST_INT_SETS);
        assertThat(prefs.toBuilder().build().getGattConnectionAndSecretHandshakeNoRetryGattError())
                .isEqualTo(FIRST_INT_SETS);

        Preferences prefs2 =
                Preferences.builder().setGattConnectionAndSecretHandshakeNoRetryGattError(
                        SECOND_INT_SETS).build();
        assertThat(prefs2.getGattConnectionAndSecretHandshakeNoRetryGattError())
                .isEqualTo(SECOND_INT_SETS);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testExtraLoggingInformation() {
        Preferences prefs =
                Preferences.builder().setExtraLoggingInformation(FIRST_EXTRA_LOGGING_INFO).build();
        assertThat(prefs.getExtraLoggingInformation()).isEqualTo(FIRST_EXTRA_LOGGING_INFO);
        assertThat(prefs.toBuilder().build().getExtraLoggingInformation())
                .isEqualTo(FIRST_EXTRA_LOGGING_INFO);

        Preferences prefs2 =
                Preferences.builder().setExtraLoggingInformation(SECOND_EXTRA_LOGGING_INFO).build();
        assertThat(prefs2.getExtraLoggingInformation()).isEqualTo(SECOND_EXTRA_LOGGING_INFO);
    }
}
