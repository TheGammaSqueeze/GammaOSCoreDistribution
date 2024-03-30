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

package com.android.server.nearby.fastpair;

import android.text.TextUtils;

import com.android.server.nearby.common.bluetooth.fastpair.Preferences;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

/**
 * This is fast pair connection preference
 */
public class FlagUtils {
    private static final int GATT_OPERATION_TIME_OUT_SECOND = 10;
    private static final int GATT_CONNECTION_TIME_OUT_SECOND = 15;
    private static final int BLUETOOTH_TOGGLE_TIME_OUT_SECOND = 10;
    private static final int BLUETOOTH_TOGGLE_SLEEP_TIME_OUT_SECOND = 2;
    private static final int CLASSIC_DISCOVERY_TIME_OUT_SECOND = 13;
    private static final int NUM_DISCOVER_ATTEMPTS = 3;
    private static final int DISCOVERY_RETRY_SLEEP_SECONDS = 1;
    private static final int SDP_TIME_OUT_SECONDS = 10;
    private static final int NUM_SDP_ATTEMPTS = 0;
    private static final int NUM_CREATED_BOND_ATTEMPTS = 3;
    private static final int NUM_CONNECT_ATTEMPT = 2;
    private static final int NUM_WRITE_ACCOUNT_KEY_ATTEMPT = 3;
    private static final boolean TOGGLE_BLUETOOTH_ON_FAILURE = false;
    private static final boolean BLUETOOTH_STATE_POOLING = true;
    private static final int BLUETOOTH_STATE_POOLING_MILLIS = 1000;
    private static final int NUM_ATTEMPTS = 2;
    private static final short BREDR_HANDOVER_DATA_CHARACTERISTIC_ID = 11265; // 0x2c01
    private static final short BLUETOOTH_SIG_DATA_CHARACTERISTIC_ID = 11266; // 0x2c02
    private static final short TRANSPORT_BLOCK_DATA_CHARACTERISTIC_ID = 11267; // 0x2c03
    private static final boolean WAIT_FOR_UUID_AFTER_BONDING = true;
    private static final boolean RECEIVE_UUID_AND_BONDED_EVENT_BEFORE_CLOSE = true;
    private static final int REMOVE_BOND_TIME_OUT_SECONDS = 5;
    private static final int REMOVE_BOND_SLEEP_MILLIS = 1000;
    private static final int CREATE_BOND_TIME_OUT_SECONDS = 15;
    private static final int HIDE_CREATED_BOND_TIME_OUT_SECONDS = 40;
    private static final int PROXY_TIME_OUT_SECONDS = 2;
    private static final boolean REJECT_ACCESS = false;
    private static final boolean ACCEPT_PASSKEY = true;
    private static final int WRITE_ACCOUNT_KEY_SLEEP_MILLIS = 2000;
    private static final boolean PROVIDER_INITIATE_BONDING = false;
    private static final boolean SPECIFY_CREATE_BOND_TRANSPORT_TYPE = false;
    private static final int CREATE_BOND_TRANSPORT_TYPE = 0;
    private static final boolean KEEP_SAME_ACCOUNT_KEY_WRITE = true;
    private static final boolean ENABLE_NAMING_CHARACTERISTIC = true;
    private static final boolean CHECK_FIRMWARE_VERSION = true;
    private static final int SDP_ATTEMPTS_AFTER_BONDED = 1;
    private static final boolean SUPPORT_HID = false;
    private static final boolean ENABLE_PAIRING_WHILE_DIRECTLY_CONNECTING = true;
    private static final boolean ACCEPT_CONSENT_FOR_FP_ONE = true;
    private static final int GATT_CONNECT_RETRY_TIMEOUT_MILLIS = 18000;
    private static final boolean ENABLE_128BIT_CUSTOM_GATT_CHARACTERISTIC = true;
    private static final boolean ENABLE_SEND_EXCEPTION_STEP_TO_VALIDATOR = true;
    private static final boolean ENABLE_ADDITIONAL_DATA_TYPE_WHEN_ACTION_OVER_BLE = true;
    private static final boolean CHECK_BOND_STATE_WHEN_SKIP_CONNECTING_PROFILE = true;
    private static final boolean MORE_LOG_FOR_QUALITY = true;
    private static final boolean RETRY_GATT_CONNECTION_AND_SECRET_HANDSHAKE = true;
    private static final int GATT_CONNECT_SHORT_TIMEOUT_MS = 7000;
    private static final int GATT_CONNECTION_LONG_TIME_OUT_MS = 15000;
    private static final int GATT_CONNECT_SHORT_TIMEOUT_RETRY_MAX_SPENT_TIME_MS = 1000;
    private static final int ADDRESS_ROTATE_RETRY_MAX_SPENT_TIME_MS = 15000;
    private static final int PAIRING_RETRY_DELAY_MS = 100;
    private static final int HANDSHAKE_SHORT_TIMEOUT_MS = 3000;
    private static final int HANDSHAKE_LONG_TIMEOUT_MS = 1000;
    private static final int SECRET_HANDSHAKE_SHORT_TIMEOUT_RETRY_MAX_SPENT_TIME_MS = 5000;
    private static final int SECRET_HANDSHAKE_LONG_TIMEOUT_RETRY_MAX_SPENT_TIME_MS = 7000;
    private static final int SECRET_HANDSHAKE_RETRY_ATTEMPTS = 3;
    private static final int SECRET_HANDSHAKE_RETRY_GATT_CONNECTION_MAX_SPENT_TIME_MS = 15000;
    private static final int SIGNAL_LOST_RETRY_MAX_SPENT_TIME_MS = 15000;
    private static final boolean RETRY_SECRET_HANDSHAKE_TIMEOUT = false;
    private static final boolean LOG_USER_MANUAL_RETRY = true;
    private static final boolean ENABLE_PAIR_FLOW_SHOW_UI_WITHOUT_PROFILE_CONNECTION = false;
    private static final boolean LOG_USER_MANUAL_CITY = true;
    private static final boolean LOG_PAIR_WITH_CACHED_MODEL_ID = true;
    private static final boolean DIRECT_CONNECT_PROFILE_IF_MODEL_ID_IN_CACHE = false;

    public static Preferences.Builder getPreferencesBuilder() {
        return Preferences.builder()
                .setGattOperationTimeoutSeconds(GATT_OPERATION_TIME_OUT_SECOND)
                .setGattConnectionTimeoutSeconds(GATT_CONNECTION_TIME_OUT_SECOND)
                .setBluetoothToggleTimeoutSeconds(BLUETOOTH_TOGGLE_TIME_OUT_SECOND)
                .setBluetoothToggleSleepSeconds(BLUETOOTH_TOGGLE_SLEEP_TIME_OUT_SECOND)
                .setClassicDiscoveryTimeoutSeconds(CLASSIC_DISCOVERY_TIME_OUT_SECOND)
                .setNumDiscoverAttempts(NUM_DISCOVER_ATTEMPTS)
                .setDiscoveryRetrySleepSeconds(DISCOVERY_RETRY_SLEEP_SECONDS)
                .setSdpTimeoutSeconds(SDP_TIME_OUT_SECONDS)
                .setNumSdpAttempts(NUM_SDP_ATTEMPTS)
                .setNumCreateBondAttempts(NUM_CREATED_BOND_ATTEMPTS)
                .setNumConnectAttempts(NUM_CONNECT_ATTEMPT)
                .setNumWriteAccountKeyAttempts(NUM_WRITE_ACCOUNT_KEY_ATTEMPT)
                .setToggleBluetoothOnFailure(TOGGLE_BLUETOOTH_ON_FAILURE)
                .setBluetoothStateUsesPolling(BLUETOOTH_STATE_POOLING)
                .setBluetoothStatePollingMillis(BLUETOOTH_STATE_POOLING_MILLIS)
                .setNumAttempts(NUM_ATTEMPTS)
                .setBrHandoverDataCharacteristicId(BREDR_HANDOVER_DATA_CHARACTERISTIC_ID)
                .setBluetoothSigDataCharacteristicId(BLUETOOTH_SIG_DATA_CHARACTERISTIC_ID)
                .setBrTransportBlockDataDescriptorId(TRANSPORT_BLOCK_DATA_CHARACTERISTIC_ID)
                .setWaitForUuidsAfterBonding(WAIT_FOR_UUID_AFTER_BONDING)
                .setReceiveUuidsAndBondedEventBeforeClose(
                        RECEIVE_UUID_AND_BONDED_EVENT_BEFORE_CLOSE)
                .setRemoveBondTimeoutSeconds(REMOVE_BOND_TIME_OUT_SECONDS)
                .setRemoveBondSleepMillis(REMOVE_BOND_SLEEP_MILLIS)
                .setCreateBondTimeoutSeconds(CREATE_BOND_TIME_OUT_SECONDS)
                .setHidCreateBondTimeoutSeconds(HIDE_CREATED_BOND_TIME_OUT_SECONDS)
                .setProxyTimeoutSeconds(PROXY_TIME_OUT_SECONDS)
                .setRejectPhonebookAccess(REJECT_ACCESS)
                .setRejectMessageAccess(REJECT_ACCESS)
                .setRejectSimAccess(REJECT_ACCESS)
                .setAcceptPasskey(ACCEPT_PASSKEY)
                .setWriteAccountKeySleepMillis(WRITE_ACCOUNT_KEY_SLEEP_MILLIS)
                .setProviderInitiatesBondingIfSupported(PROVIDER_INITIATE_BONDING)
                .setAttemptDirectConnectionWhenPreviouslyBonded(true)
                .setAutomaticallyReconnectGattWhenNeeded(true)
                .setSkipDisconnectingGattBeforeWritingAccountKey(true)
                .setIgnoreUuidTimeoutAfterBonded(true)
                .setSpecifyCreateBondTransportType(SPECIFY_CREATE_BOND_TRANSPORT_TYPE)
                .setCreateBondTransportType(CREATE_BOND_TRANSPORT_TYPE)
                .setIncreaseIntentFilterPriority(true)
                .setEvaluatePerformance(false)
                .setKeepSameAccountKeyWrite(KEEP_SAME_ACCOUNT_KEY_WRITE)
                .setEnableNamingCharacteristic(ENABLE_NAMING_CHARACTERISTIC)
                .setEnableFirmwareVersionCharacteristic(CHECK_FIRMWARE_VERSION)
                .setNumSdpAttemptsAfterBonded(SDP_ATTEMPTS_AFTER_BONDED)
                .setSupportHidDevice(SUPPORT_HID)
                .setEnablePairingWhileDirectlyConnecting(
                        ENABLE_PAIRING_WHILE_DIRECTLY_CONNECTING)
                .setAcceptConsentForFastPairOne(ACCEPT_CONSENT_FOR_FP_ONE)
                .setGattConnectRetryTimeoutMillis(GATT_CONNECT_RETRY_TIMEOUT_MILLIS)
                .setEnable128BitCustomGattCharacteristicsId(
                        ENABLE_128BIT_CUSTOM_GATT_CHARACTERISTIC)
                .setEnableSendExceptionStepToValidator(ENABLE_SEND_EXCEPTION_STEP_TO_VALIDATOR)
                .setEnableAdditionalDataTypeWhenActionOverBle(
                        ENABLE_ADDITIONAL_DATA_TYPE_WHEN_ACTION_OVER_BLE)
                .setCheckBondStateWhenSkipConnectingProfiles(
                        CHECK_BOND_STATE_WHEN_SKIP_CONNECTING_PROFILE)
                .setMoreEventLogForQuality(MORE_LOG_FOR_QUALITY)
                .setRetryGattConnectionAndSecretHandshake(
                        RETRY_GATT_CONNECTION_AND_SECRET_HANDSHAKE)
                .setGattConnectShortTimeoutMs(GATT_CONNECT_SHORT_TIMEOUT_MS)
                .setGattConnectLongTimeoutMs(GATT_CONNECTION_LONG_TIME_OUT_MS)
                .setGattConnectShortTimeoutRetryMaxSpentTimeMs(
                        GATT_CONNECT_SHORT_TIMEOUT_RETRY_MAX_SPENT_TIME_MS)
                .setAddressRotateRetryMaxSpentTimeMs(ADDRESS_ROTATE_RETRY_MAX_SPENT_TIME_MS)
                .setPairingRetryDelayMs(PAIRING_RETRY_DELAY_MS)
                .setSecretHandshakeShortTimeoutMs(HANDSHAKE_SHORT_TIMEOUT_MS)
                .setSecretHandshakeLongTimeoutMs(HANDSHAKE_LONG_TIMEOUT_MS)
                .setSecretHandshakeShortTimeoutRetryMaxSpentTimeMs(
                        SECRET_HANDSHAKE_SHORT_TIMEOUT_RETRY_MAX_SPENT_TIME_MS)
                .setSecretHandshakeLongTimeoutRetryMaxSpentTimeMs(
                        SECRET_HANDSHAKE_LONG_TIMEOUT_RETRY_MAX_SPENT_TIME_MS)
                .setSecretHandshakeRetryAttempts(SECRET_HANDSHAKE_RETRY_ATTEMPTS)
                .setSecretHandshakeRetryGattConnectionMaxSpentTimeMs(
                        SECRET_HANDSHAKE_RETRY_GATT_CONNECTION_MAX_SPENT_TIME_MS)
                .setSignalLostRetryMaxSpentTimeMs(SIGNAL_LOST_RETRY_MAX_SPENT_TIME_MS)
                .setGattConnectionAndSecretHandshakeNoRetryGattError(
                        getGattConnectionAndSecretHandshakeNoRetryGattError())
                .setRetrySecretHandshakeTimeout(RETRY_SECRET_HANDSHAKE_TIMEOUT)
                .setLogUserManualRetry(LOG_USER_MANUAL_RETRY)
                .setEnablePairFlowShowUiWithoutProfileConnection(
                        ENABLE_PAIR_FLOW_SHOW_UI_WITHOUT_PROFILE_CONNECTION)
                .setLogUserManualRetry(LOG_USER_MANUAL_CITY)
                .setLogPairWithCachedModelId(LOG_PAIR_WITH_CACHED_MODEL_ID)
                .setDirectConnectProfileIfModelIdInCache(
                        DIRECT_CONNECT_PROFILE_IF_MODEL_ID_IN_CACHE);
    }

    private static ImmutableSet<Integer> getGattConnectionAndSecretHandshakeNoRetryGattError() {
        ImmutableSet.Builder<Integer> noRetryGattErrorsBuilder = ImmutableSet.builder();
        // When GATT connection fail we will not retry on error code 257
        for (String errorCode :
                Splitter.on(",").split("257,")) {
            if (!TextUtils.isDigitsOnly(errorCode)) {
                continue;
            }

            try {
                noRetryGattErrorsBuilder.add(Integer.parseInt(errorCode));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return noRetryGattErrorsBuilder.build();
    }
}
