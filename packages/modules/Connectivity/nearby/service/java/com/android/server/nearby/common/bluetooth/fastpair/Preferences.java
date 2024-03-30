/*
 * Copyright 2021 The Android Open Source Project
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

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothUuids.get16BitUuid;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.FirmwareVersionCharacteristic;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Shorts;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * Preferences that tweak the Fast Pairing process: timeouts, number of retries... All preferences
 * have default values which should be reasonable for all clients.
 */
public class Preferences {

    private final int mGattOperationTimeoutSeconds;
    private final int mGattConnectionTimeoutSeconds;
    private final int mBluetoothToggleTimeoutSeconds;
    private final int mBluetoothToggleSleepSeconds;
    private final int mClassicDiscoveryTimeoutSeconds;
    private final int mNumDiscoverAttempts;
    private final int mDiscoveryRetrySleepSeconds;
    private final boolean mIgnoreDiscoveryError;
    private final int mSdpTimeoutSeconds;
    private final int mNumSdpAttempts;
    private final int mNumCreateBondAttempts;
    private final int mNumConnectAttempts;
    private final int mNumWriteAccountKeyAttempts;
    private final boolean mToggleBluetoothOnFailure;
    private final boolean mBluetoothStateUsesPolling;
    private final int mBluetoothStatePollingMillis;
    private final int mNumAttempts;
    private final boolean mEnableBrEdrHandover;
    private final short mBrHandoverDataCharacteristicId;
    private final short mBluetoothSigDataCharacteristicId;
    private final short mFirmwareVersionCharacteristicId;
    private final short mBrTransportBlockDataDescriptorId;
    private final boolean mWaitForUuidsAfterBonding;
    private final boolean mReceiveUuidsAndBondedEventBeforeClose;
    private final int mRemoveBondTimeoutSeconds;
    private final int mRemoveBondSleepMillis;
    private final int mCreateBondTimeoutSeconds;
    private final int mHidCreateBondTimeoutSeconds;
    private final int mProxyTimeoutSeconds;
    private final boolean mRejectPhonebookAccess;
    private final boolean mRejectMessageAccess;
    private final boolean mRejectSimAccess;
    private final int mWriteAccountKeySleepMillis;
    private final boolean mSkipDisconnectingGattBeforeWritingAccountKey;
    private final boolean mMoreEventLogForQuality;
    private final boolean mRetryGattConnectionAndSecretHandshake;
    private final long mGattConnectShortTimeoutMs;
    private final long mGattConnectLongTimeoutMs;
    private final long mGattConnectShortTimeoutRetryMaxSpentTimeMs;
    private final long mAddressRotateRetryMaxSpentTimeMs;
    private final long mPairingRetryDelayMs;
    private final long mSecretHandshakeShortTimeoutMs;
    private final long mSecretHandshakeLongTimeoutMs;
    private final long mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs;
    private final long mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs;
    private final long mSecretHandshakeRetryAttempts;
    private final long mSecretHandshakeRetryGattConnectionMaxSpentTimeMs;
    private final long mSignalLostRetryMaxSpentTimeMs;
    private final ImmutableSet<Integer> mGattConnectionAndSecretHandshakeNoRetryGattError;
    private final boolean mRetrySecretHandshakeTimeout;
    private final boolean mLogUserManualRetry;
    private final int mPairFailureCounts;
    private final String mCachedDeviceAddress;
    private final String mPossibleCachedDeviceAddress;
    private final int mSameModelIdPairedDeviceCount;
    private final boolean mIsDeviceFinishCheckAddressFromCache;
    private final boolean mLogPairWithCachedModelId;
    private final boolean mDirectConnectProfileIfModelIdInCache;
    private final boolean mAcceptPasskey;
    private final byte[] mSupportedProfileUuids;
    private final boolean mProviderInitiatesBondingIfSupported;
    private final boolean mAttemptDirectConnectionWhenPreviouslyBonded;
    private final boolean mAutomaticallyReconnectGattWhenNeeded;
    private final boolean mSkipConnectingProfiles;
    private final boolean mIgnoreUuidTimeoutAfterBonded;
    private final boolean mSpecifyCreateBondTransportType;
    private final int mCreateBondTransportType;
    private final boolean mIncreaseIntentFilterPriority;
    private final boolean mEvaluatePerformance;
    private final Preferences.ExtraLoggingInformation mExtraLoggingInformation;
    private final boolean mEnableNamingCharacteristic;
    private final boolean mEnableFirmwareVersionCharacteristic;
    private final boolean mKeepSameAccountKeyWrite;
    private final boolean mIsRetroactivePairing;
    private final int mNumSdpAttemptsAfterBonded;
    private final boolean mSupportHidDevice;
    private final boolean mEnablePairingWhileDirectlyConnecting;
    private final boolean mAcceptConsentForFastPairOne;
    private final int mGattConnectRetryTimeoutMillis;
    private final boolean mEnable128BitCustomGattCharacteristicsId;
    private final boolean mEnableSendExceptionStepToValidator;
    private final boolean mEnableAdditionalDataTypeWhenActionOverBle;
    private final boolean mCheckBondStateWhenSkipConnectingProfiles;
    private final boolean mHandlePasskeyConfirmationByUi;
    private final boolean mEnablePairFlowShowUiWithoutProfileConnection;

    private Preferences(
            int gattOperationTimeoutSeconds,
            int gattConnectionTimeoutSeconds,
            int bluetoothToggleTimeoutSeconds,
            int bluetoothToggleSleepSeconds,
            int classicDiscoveryTimeoutSeconds,
            int numDiscoverAttempts,
            int discoveryRetrySleepSeconds,
            boolean ignoreDiscoveryError,
            int sdpTimeoutSeconds,
            int numSdpAttempts,
            int numCreateBondAttempts,
            int numConnectAttempts,
            int numWriteAccountKeyAttempts,
            boolean toggleBluetoothOnFailure,
            boolean bluetoothStateUsesPolling,
            int bluetoothStatePollingMillis,
            int numAttempts,
            boolean enableBrEdrHandover,
            short brHandoverDataCharacteristicId,
            short bluetoothSigDataCharacteristicId,
            short firmwareVersionCharacteristicId,
            short brTransportBlockDataDescriptorId,
            boolean waitForUuidsAfterBonding,
            boolean receiveUuidsAndBondedEventBeforeClose,
            int removeBondTimeoutSeconds,
            int removeBondSleepMillis,
            int createBondTimeoutSeconds,
            int hidCreateBondTimeoutSeconds,
            int proxyTimeoutSeconds,
            boolean rejectPhonebookAccess,
            boolean rejectMessageAccess,
            boolean rejectSimAccess,
            int writeAccountKeySleepMillis,
            boolean skipDisconnectingGattBeforeWritingAccountKey,
            boolean moreEventLogForQuality,
            boolean retryGattConnectionAndSecretHandshake,
            long gattConnectShortTimeoutMs,
            long gattConnectLongTimeoutMs,
            long gattConnectShortTimeoutRetryMaxSpentTimeMs,
            long addressRotateRetryMaxSpentTimeMs,
            long pairingRetryDelayMs,
            long secretHandshakeShortTimeoutMs,
            long secretHandshakeLongTimeoutMs,
            long secretHandshakeShortTimeoutRetryMaxSpentTimeMs,
            long secretHandshakeLongTimeoutRetryMaxSpentTimeMs,
            long secretHandshakeRetryAttempts,
            long secretHandshakeRetryGattConnectionMaxSpentTimeMs,
            long signalLostRetryMaxSpentTimeMs,
            ImmutableSet<Integer> gattConnectionAndSecretHandshakeNoRetryGattError,
            boolean retrySecretHandshakeTimeout,
            boolean logUserManualRetry,
            int pairFailureCounts,
            String cachedDeviceAddress,
            String possibleCachedDeviceAddress,
            int sameModelIdPairedDeviceCount,
            boolean isDeviceFinishCheckAddressFromCache,
            boolean logPairWithCachedModelId,
            boolean directConnectProfileIfModelIdInCache,
            boolean acceptPasskey,
            byte[] supportedProfileUuids,
            boolean providerInitiatesBondingIfSupported,
            boolean attemptDirectConnectionWhenPreviouslyBonded,
            boolean automaticallyReconnectGattWhenNeeded,
            boolean skipConnectingProfiles,
            boolean ignoreUuidTimeoutAfterBonded,
            boolean specifyCreateBondTransportType,
            int createBondTransportType,
            boolean increaseIntentFilterPriority,
            boolean evaluatePerformance,
            @Nullable Preferences.ExtraLoggingInformation extraLoggingInformation,
            boolean enableNamingCharacteristic,
            boolean enableFirmwareVersionCharacteristic,
            boolean keepSameAccountKeyWrite,
            boolean isRetroactivePairing,
            int numSdpAttemptsAfterBonded,
            boolean supportHidDevice,
            boolean enablePairingWhileDirectlyConnecting,
            boolean acceptConsentForFastPairOne,
            int gattConnectRetryTimeoutMillis,
            boolean enable128BitCustomGattCharacteristicsId,
            boolean enableSendExceptionStepToValidator,
            boolean enableAdditionalDataTypeWhenActionOverBle,
            boolean checkBondStateWhenSkipConnectingProfiles,
            boolean handlePasskeyConfirmationByUi,
            boolean enablePairFlowShowUiWithoutProfileConnection) {
        this.mGattOperationTimeoutSeconds = gattOperationTimeoutSeconds;
        this.mGattConnectionTimeoutSeconds = gattConnectionTimeoutSeconds;
        this.mBluetoothToggleTimeoutSeconds = bluetoothToggleTimeoutSeconds;
        this.mBluetoothToggleSleepSeconds = bluetoothToggleSleepSeconds;
        this.mClassicDiscoveryTimeoutSeconds = classicDiscoveryTimeoutSeconds;
        this.mNumDiscoverAttempts = numDiscoverAttempts;
        this.mDiscoveryRetrySleepSeconds = discoveryRetrySleepSeconds;
        this.mIgnoreDiscoveryError = ignoreDiscoveryError;
        this.mSdpTimeoutSeconds = sdpTimeoutSeconds;
        this.mNumSdpAttempts = numSdpAttempts;
        this.mNumCreateBondAttempts = numCreateBondAttempts;
        this.mNumConnectAttempts = numConnectAttempts;
        this.mNumWriteAccountKeyAttempts = numWriteAccountKeyAttempts;
        this.mToggleBluetoothOnFailure = toggleBluetoothOnFailure;
        this.mBluetoothStateUsesPolling = bluetoothStateUsesPolling;
        this.mBluetoothStatePollingMillis = bluetoothStatePollingMillis;
        this.mNumAttempts = numAttempts;
        this.mEnableBrEdrHandover = enableBrEdrHandover;
        this.mBrHandoverDataCharacteristicId = brHandoverDataCharacteristicId;
        this.mBluetoothSigDataCharacteristicId = bluetoothSigDataCharacteristicId;
        this.mFirmwareVersionCharacteristicId = firmwareVersionCharacteristicId;
        this.mBrTransportBlockDataDescriptorId = brTransportBlockDataDescriptorId;
        this.mWaitForUuidsAfterBonding = waitForUuidsAfterBonding;
        this.mReceiveUuidsAndBondedEventBeforeClose = receiveUuidsAndBondedEventBeforeClose;
        this.mRemoveBondTimeoutSeconds = removeBondTimeoutSeconds;
        this.mRemoveBondSleepMillis = removeBondSleepMillis;
        this.mCreateBondTimeoutSeconds = createBondTimeoutSeconds;
        this.mHidCreateBondTimeoutSeconds = hidCreateBondTimeoutSeconds;
        this.mProxyTimeoutSeconds = proxyTimeoutSeconds;
        this.mRejectPhonebookAccess = rejectPhonebookAccess;
        this.mRejectMessageAccess = rejectMessageAccess;
        this.mRejectSimAccess = rejectSimAccess;
        this.mWriteAccountKeySleepMillis = writeAccountKeySleepMillis;
        this.mSkipDisconnectingGattBeforeWritingAccountKey =
                skipDisconnectingGattBeforeWritingAccountKey;
        this.mMoreEventLogForQuality = moreEventLogForQuality;
        this.mRetryGattConnectionAndSecretHandshake = retryGattConnectionAndSecretHandshake;
        this.mGattConnectShortTimeoutMs = gattConnectShortTimeoutMs;
        this.mGattConnectLongTimeoutMs = gattConnectLongTimeoutMs;
        this.mGattConnectShortTimeoutRetryMaxSpentTimeMs =
                gattConnectShortTimeoutRetryMaxSpentTimeMs;
        this.mAddressRotateRetryMaxSpentTimeMs = addressRotateRetryMaxSpentTimeMs;
        this.mPairingRetryDelayMs = pairingRetryDelayMs;
        this.mSecretHandshakeShortTimeoutMs = secretHandshakeShortTimeoutMs;
        this.mSecretHandshakeLongTimeoutMs = secretHandshakeLongTimeoutMs;
        this.mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs =
                secretHandshakeShortTimeoutRetryMaxSpentTimeMs;
        this.mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs =
                secretHandshakeLongTimeoutRetryMaxSpentTimeMs;
        this.mSecretHandshakeRetryAttempts = secretHandshakeRetryAttempts;
        this.mSecretHandshakeRetryGattConnectionMaxSpentTimeMs =
                secretHandshakeRetryGattConnectionMaxSpentTimeMs;
        this.mSignalLostRetryMaxSpentTimeMs = signalLostRetryMaxSpentTimeMs;
        this.mGattConnectionAndSecretHandshakeNoRetryGattError =
                gattConnectionAndSecretHandshakeNoRetryGattError;
        this.mRetrySecretHandshakeTimeout = retrySecretHandshakeTimeout;
        this.mLogUserManualRetry = logUserManualRetry;
        this.mPairFailureCounts = pairFailureCounts;
        this.mCachedDeviceAddress = cachedDeviceAddress;
        this.mPossibleCachedDeviceAddress = possibleCachedDeviceAddress;
        this.mSameModelIdPairedDeviceCount = sameModelIdPairedDeviceCount;
        this.mIsDeviceFinishCheckAddressFromCache = isDeviceFinishCheckAddressFromCache;
        this.mLogPairWithCachedModelId = logPairWithCachedModelId;
        this.mDirectConnectProfileIfModelIdInCache = directConnectProfileIfModelIdInCache;
        this.mAcceptPasskey = acceptPasskey;
        this.mSupportedProfileUuids = supportedProfileUuids;
        this.mProviderInitiatesBondingIfSupported = providerInitiatesBondingIfSupported;
        this.mAttemptDirectConnectionWhenPreviouslyBonded =
                attemptDirectConnectionWhenPreviouslyBonded;
        this.mAutomaticallyReconnectGattWhenNeeded = automaticallyReconnectGattWhenNeeded;
        this.mSkipConnectingProfiles = skipConnectingProfiles;
        this.mIgnoreUuidTimeoutAfterBonded = ignoreUuidTimeoutAfterBonded;
        this.mSpecifyCreateBondTransportType = specifyCreateBondTransportType;
        this.mCreateBondTransportType = createBondTransportType;
        this.mIncreaseIntentFilterPriority = increaseIntentFilterPriority;
        this.mEvaluatePerformance = evaluatePerformance;
        this.mExtraLoggingInformation = extraLoggingInformation;
        this.mEnableNamingCharacteristic = enableNamingCharacteristic;
        this.mEnableFirmwareVersionCharacteristic = enableFirmwareVersionCharacteristic;
        this.mKeepSameAccountKeyWrite = keepSameAccountKeyWrite;
        this.mIsRetroactivePairing = isRetroactivePairing;
        this.mNumSdpAttemptsAfterBonded = numSdpAttemptsAfterBonded;
        this.mSupportHidDevice = supportHidDevice;
        this.mEnablePairingWhileDirectlyConnecting = enablePairingWhileDirectlyConnecting;
        this.mAcceptConsentForFastPairOne = acceptConsentForFastPairOne;
        this.mGattConnectRetryTimeoutMillis = gattConnectRetryTimeoutMillis;
        this.mEnable128BitCustomGattCharacteristicsId = enable128BitCustomGattCharacteristicsId;
        this.mEnableSendExceptionStepToValidator = enableSendExceptionStepToValidator;
        this.mEnableAdditionalDataTypeWhenActionOverBle = enableAdditionalDataTypeWhenActionOverBle;
        this.mCheckBondStateWhenSkipConnectingProfiles = checkBondStateWhenSkipConnectingProfiles;
        this.mHandlePasskeyConfirmationByUi = handlePasskeyConfirmationByUi;
        this.mEnablePairFlowShowUiWithoutProfileConnection =
                enablePairFlowShowUiWithoutProfileConnection;
    }

    /**
     * Timeout for each GATT operation (not for the whole pairing process).
     */
    public int getGattOperationTimeoutSeconds() {
        return mGattOperationTimeoutSeconds;
    }

    /**
     * Timeout for Gatt connection operation.
     */
    public int getGattConnectionTimeoutSeconds() {
        return mGattConnectionTimeoutSeconds;
    }

    /**
     * Timeout for Bluetooth toggle.
     */
    public int getBluetoothToggleTimeoutSeconds() {
        return mBluetoothToggleTimeoutSeconds;
    }

    /**
     * Sleep time for Bluetooth toggle.
     */
    public int getBluetoothToggleSleepSeconds() {
        return mBluetoothToggleSleepSeconds;
    }

    /**
     * Timeout for classic discovery.
     */
    public int getClassicDiscoveryTimeoutSeconds() {
        return mClassicDiscoveryTimeoutSeconds;
    }

    /**
     * Number of discovery attempts allowed.
     */
    public int getNumDiscoverAttempts() {
        return mNumDiscoverAttempts;
    }

    /**
     * Sleep time between discovery retry.
     */
    public int getDiscoveryRetrySleepSeconds() {
        return mDiscoveryRetrySleepSeconds;
    }

    /**
     * Whether to ignore error incurred during discovery.
     */
    public boolean getIgnoreDiscoveryError() {
        return mIgnoreDiscoveryError;
    }

    /**
     * Timeout for Sdp.
     */
    public int getSdpTimeoutSeconds() {
        return mSdpTimeoutSeconds;
    }

    /**
     * Number of Sdp attempts allowed.
     */
    public int getNumSdpAttempts() {
        return mNumSdpAttempts;
    }

    /**
     * Number of create bond attempts allowed.
     */
    public int getNumCreateBondAttempts() {
        return mNumCreateBondAttempts;
    }

    /**
     * Number of connect attempts allowed.
     */
    public int getNumConnectAttempts() {
        return mNumConnectAttempts;
    }

    /**
     * Number of write account key attempts allowed.
     */
    public int getNumWriteAccountKeyAttempts() {
        return mNumWriteAccountKeyAttempts;
    }

    /**
     * Returns whether it is OK toggle bluetooth to retry upon failure.
     */
    public boolean getToggleBluetoothOnFailure() {
        return mToggleBluetoothOnFailure;
    }

    /**
     * Whether to get Bluetooth state using polling.
     */
    public boolean getBluetoothStateUsesPolling() {
        return mBluetoothStateUsesPolling;
    }

    /**
     * Polling time when retrieving Bluetooth state.
     */
    public int getBluetoothStatePollingMillis() {
        return mBluetoothStatePollingMillis;
    }

    /**
     * The number of times to attempt a generic operation, before giving up.
     */
    public int getNumAttempts() {
        return mNumAttempts;
    }

    /**
     * Returns whether BrEdr handover is enabled.
     */
    public boolean getEnableBrEdrHandover() {
        return mEnableBrEdrHandover;
    }

    /**
     * Returns characteristic Id for Br Handover data.
     */
    public short getBrHandoverDataCharacteristicId() {
        return mBrHandoverDataCharacteristicId;
    }

    /**
     * Returns characteristic Id for Bluethoth Sig data.
     */
    public short getBluetoothSigDataCharacteristicId() {
        return mBluetoothSigDataCharacteristicId;
    }

    /**
     * Returns characteristic Id for Firmware version.
     */
    public short getFirmwareVersionCharacteristicId() {
        return mFirmwareVersionCharacteristicId;
    }

    /**
     * Returns descripter Id for Br transport block data.
     */
    public short getBrTransportBlockDataDescriptorId() {
        return mBrTransportBlockDataDescriptorId;
    }

    /**
     * Whether to wait for Uuids after bonding.
     */
    public boolean getWaitForUuidsAfterBonding() {
        return mWaitForUuidsAfterBonding;
    }

    /**
     * Whether to get received Uuids and bonded events before close.
     */
    public boolean getReceiveUuidsAndBondedEventBeforeClose() {
        return mReceiveUuidsAndBondedEventBeforeClose;
    }

    /**
     * Timeout for remove bond operation.
     */
    public int getRemoveBondTimeoutSeconds() {
        return mRemoveBondTimeoutSeconds;
    }

    /**
     * Sleep time for remove bond operation.
     */
    public int getRemoveBondSleepMillis() {
        return mRemoveBondSleepMillis;
    }

    /**
     * This almost always succeeds (or fails) in 2-10 seconds (Taimen running O -> Nexus 6P sim).
     */
    public int getCreateBondTimeoutSeconds() {
        return mCreateBondTimeoutSeconds;
    }

    /**
     * Timeout for creating bond with Hid devices.
     */
    public int getHidCreateBondTimeoutSeconds() {
        return mHidCreateBondTimeoutSeconds;
    }

    /**
     * Timeout for get proxy operation.
     */
    public int getProxyTimeoutSeconds() {
        return mProxyTimeoutSeconds;
    }

    /**
     * Whether to reject phone book access.
     */
    public boolean getRejectPhonebookAccess() {
        return mRejectPhonebookAccess;
    }

    /**
     * Whether to reject message access.
     */
    public boolean getRejectMessageAccess() {
        return mRejectMessageAccess;
    }

    /**
     * Whether to reject sim access.
     */
    public boolean getRejectSimAccess() {
        return mRejectSimAccess;
    }

    /**
     * Sleep time for write account key operation.
     */
    public int getWriteAccountKeySleepMillis() {
        return mWriteAccountKeySleepMillis;
    }

    /**
     * Whether to skip disconneting gatt before writing account key.
     */
    public boolean getSkipDisconnectingGattBeforeWritingAccountKey() {
        return mSkipDisconnectingGattBeforeWritingAccountKey;
    }

    /**
     * Whether to get more event log for quality improvement.
     */
    public boolean getMoreEventLogForQuality() {
        return mMoreEventLogForQuality;
    }

    /**
     * Whether to retry gatt connection and secrete handshake.
     */
    public boolean getRetryGattConnectionAndSecretHandshake() {
        return mRetryGattConnectionAndSecretHandshake;
    }

    /**
     * Short Gatt connection timeoout.
     */
    public long getGattConnectShortTimeoutMs() {
        return mGattConnectShortTimeoutMs;
    }

    /**
     * Long Gatt connection timeout.
     */
    public long getGattConnectLongTimeoutMs() {
        return mGattConnectLongTimeoutMs;
    }

    /**
     * Short Timeout for Gatt connection, including retry.
     */
    public long getGattConnectShortTimeoutRetryMaxSpentTimeMs() {
        return mGattConnectShortTimeoutRetryMaxSpentTimeMs;
    }

    /**
     * Timeout for address rotation, including retry.
     */
    public long getAddressRotateRetryMaxSpentTimeMs() {
        return mAddressRotateRetryMaxSpentTimeMs;
    }

    /**
     * Returns pairing retry delay time.
     */
    public long getPairingRetryDelayMs() {
        return mPairingRetryDelayMs;
    }

    /**
     * Short timeout for secrete handshake.
     */
    public long getSecretHandshakeShortTimeoutMs() {
        return mSecretHandshakeShortTimeoutMs;
    }

    /**
     * Long timeout for secret handshake.
     */
    public long getSecretHandshakeLongTimeoutMs() {
        return mSecretHandshakeLongTimeoutMs;
    }

    /**
     * Short timeout for secret handshake, including retry.
     */
    public long getSecretHandshakeShortTimeoutRetryMaxSpentTimeMs() {
        return mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs;
    }

    /**
     * Long timeout for secret handshake, including retry.
     */
    public long getSecretHandshakeLongTimeoutRetryMaxSpentTimeMs() {
        return mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs;
    }

    /**
     * Number of secrete handshake retry allowed.
     */
    public long getSecretHandshakeRetryAttempts() {
        return mSecretHandshakeRetryAttempts;
    }

    /**
     * Timeout for secrete handshake and gatt connection, including retry.
     */
    public long getSecretHandshakeRetryGattConnectionMaxSpentTimeMs() {
        return mSecretHandshakeRetryGattConnectionMaxSpentTimeMs;
    }

    /**
     * Timeout for signal lost handling, including retry.
     */
    public long getSignalLostRetryMaxSpentTimeMs() {
        return mSignalLostRetryMaxSpentTimeMs;
    }

    /**
     * Returns error for gatt connection and secrete handshake, without retry.
     */
    public ImmutableSet<Integer> getGattConnectionAndSecretHandshakeNoRetryGattError() {
        return mGattConnectionAndSecretHandshakeNoRetryGattError;
    }

    /**
     * Whether to retry upon secrete handshake timeout.
     */
    public boolean getRetrySecretHandshakeTimeout() {
        return mRetrySecretHandshakeTimeout;
    }

    /**
     * Wehther to log user manual retry.
     */
    public boolean getLogUserManualRetry() {
        return mLogUserManualRetry;
    }

    /**
     * Returns number of pairing failure counts.
     */
    public int getPairFailureCounts() {
        return mPairFailureCounts;
    }

    /**
     * Returns cached device address.
     */
    public String getCachedDeviceAddress() {
        return mCachedDeviceAddress;
    }

    /**
     * Returns possible cached device address.
     */
    public String getPossibleCachedDeviceAddress() {
        return mPossibleCachedDeviceAddress;
    }

    /**
     * Returns count of paired devices from the same model Id.
     */
    public int getSameModelIdPairedDeviceCount() {
        return mSameModelIdPairedDeviceCount;
    }

    /**
     * Whether the bonded device address is in the Cache .
     */
    public boolean getIsDeviceFinishCheckAddressFromCache() {
        return mIsDeviceFinishCheckAddressFromCache;
    }

    /**
     * Whether to log pairing info when cached model Id is hit.
     */
    public boolean getLogPairWithCachedModelId() {
        return mLogPairWithCachedModelId;
    }

    /**
     * Whether to directly connnect to a profile of a device, whose model Id is in cache.
     */
    public boolean getDirectConnectProfileIfModelIdInCache() {
        return mDirectConnectProfileIfModelIdInCache;
    }

    /**
     * Whether to auto-accept
     * {@link android.bluetooth.BluetoothDevice#PAIRING_VARIANT_PASSKEY_CONFIRMATION}.
     * Only the Fast Pair Simulator (which runs on an Android device) sends this. Since real
     * Bluetooth headphones don't have displays, they use secure simple pairing (no pin code
     * confirmation; we get no pairing request broadcast at all). So we may want to turn this off in
     * prod.
     */
    public boolean getAcceptPasskey() {
        return mAcceptPasskey;
    }

    /**
     * Returns Uuids for supported profiles.
     */
    @SuppressWarnings("mutable")
    public byte[] getSupportedProfileUuids() {
        return mSupportedProfileUuids;
    }

    /**
     * If true, after the Key-based Pairing BLE handshake, we wait for the headphones to send a
     * pairing request to us; if false, we send the request to them.
     */
    public boolean getProviderInitiatesBondingIfSupported() {
        return mProviderInitiatesBondingIfSupported;
    }

    /**
     * If true, the first step will be attempting to connect directly to our supported profiles when
     * a device has previously been bonded. This will help with performance on subsequent bondings
     * and help to increase reliability in some cases.
     */
    public boolean getAttemptDirectConnectionWhenPreviouslyBonded() {
        return mAttemptDirectConnectionWhenPreviouslyBonded;
    }

    /**
     * If true, closed Gatt connections will be reopened when they are needed again. Otherwise, they
     * will remain closed until they are explicitly reopened.
     */
    public boolean getAutomaticallyReconnectGattWhenNeeded() {
        return mAutomaticallyReconnectGattWhenNeeded;
    }

    /**
     * If true, we'll finish the pairing process after we've created a bond instead of after
     * connecting a profile.
     */
    public boolean getSkipConnectingProfiles() {
        return mSkipConnectingProfiles;
    }

    /**
     * If true, continues the pairing process if we've timed out due to not receiving UUIDs from the
     * headset. We can still attempt to connect to A2DP afterwards. If false, Fast Pair will fail
     * after this step since we're expecting to receive the UUIDs.
     */
    public boolean getIgnoreUuidTimeoutAfterBonded() {
        return mIgnoreUuidTimeoutAfterBonded;
    }

    /**
     * If true, a specific transport type will be included in the create bond request, which will be
     * used for dual mode devices. Otherwise, we'll use the platform defined default which is
     * BluetoothDevice.TRANSPORT_AUTO. See {@link #getCreateBondTransportType()}.
     */
    public boolean getSpecifyCreateBondTransportType() {
        return mSpecifyCreateBondTransportType;
    }

    /**
     * The transport type to use when creating a bond when
     * {@link #getSpecifyCreateBondTransportType() is true. This should be one of
     * BluetoothDevice.TRANSPORT_AUTO, BluetoothDevice.TRANSPORT_BREDR,
     * or BluetoothDevice.TRANSPORT_LE.
     */
    public int getCreateBondTransportType() {
        return mCreateBondTransportType;
    }

    /**
     * Whether to increase intent filter priority.
     */
    public boolean getIncreaseIntentFilterPriority() {
        return mIncreaseIntentFilterPriority;
    }

    /**
     * Whether to evaluate performance.
     */
    public boolean getEvaluatePerformance() {
        return mEvaluatePerformance;
    }

    /**
     * Returns extra logging information.
     */
    @Nullable
    public ExtraLoggingInformation getExtraLoggingInformation() {
        return mExtraLoggingInformation;
    }

    /**
     * Whether to enable naming characteristic.
     */
    public boolean getEnableNamingCharacteristic() {
        return mEnableNamingCharacteristic;
    }

    /**
     * Whether to enable firmware version characteristic.
     */
    public boolean getEnableFirmwareVersionCharacteristic() {
        return mEnableFirmwareVersionCharacteristic;
    }

    /**
     * If true, even Fast Pair identifies a provider have paired with the account, still writes the
     * identified account key to the provider.
     */
    public boolean getKeepSameAccountKeyWrite() {
        return mKeepSameAccountKeyWrite;
    }

    /**
     * If true, run retroactive pairing.
     */
    public boolean getIsRetroactivePairing() {
        return mIsRetroactivePairing;
    }

    /**
     * If it's larger than 0, {@link android.bluetooth.BluetoothDevice#fetchUuidsWithSdp} would be
     * triggered with number of attempts after device is bonded and no profiles were automatically
     * discovered".
     */
    public int getNumSdpAttemptsAfterBonded() {
        return mNumSdpAttemptsAfterBonded;
    }

    /**
     * If true, supports HID device for fastpair.
     */
    public boolean getSupportHidDevice() {
        return mSupportHidDevice;
    }

    /**
     * If true, we'll enable the pairing behavior to handle the state transition from BOND_BONDED to
     * BOND_BONDING when directly connecting profiles.
     */
    public boolean getEnablePairingWhileDirectlyConnecting() {
        return mEnablePairingWhileDirectlyConnecting;
    }

    /**
     * If true, we will accept the user confirmation when bonding with FastPair 1.0 devices.
     */
    public boolean getAcceptConsentForFastPairOne() {
        return mAcceptConsentForFastPairOne;
    }

    /**
     * If it's larger than 0, we will retry connecting GATT within the timeout.
     */
    public int getGattConnectRetryTimeoutMillis() {
        return mGattConnectRetryTimeoutMillis;
    }

    /**
     * If true, then uses the new custom GATT characteristics {go/fastpair-128bit-gatt}.
     */
    public boolean getEnable128BitCustomGattCharacteristicsId() {
        return mEnable128BitCustomGattCharacteristicsId;
    }

    /**
     * If true, then sends the internal pair step or Exception to Validator by Intent.
     */
    public boolean getEnableSendExceptionStepToValidator() {
        return mEnableSendExceptionStepToValidator;
    }

    /**
     * If true, then adds the additional data type in the handshake packet when action over BLE.
     */
    public boolean getEnableAdditionalDataTypeWhenActionOverBle() {
        return mEnableAdditionalDataTypeWhenActionOverBle;
    }

    /**
     * If true, then checks the bond state when skips connecting profiles in the pairing shortcut.
     */
    public boolean getCheckBondStateWhenSkipConnectingProfiles() {
        return mCheckBondStateWhenSkipConnectingProfiles;
    }

    /**
     * If true, the passkey confirmation will be handled by the half-sheet UI.
     */
    public boolean getHandlePasskeyConfirmationByUi() {
        return mHandlePasskeyConfirmationByUi;
    }

    /**
     * If true, then use pair flow to show ui when pairing is finished without connecting profile.
     */
    public boolean getEnablePairFlowShowUiWithoutProfileConnection() {
        return mEnablePairFlowShowUiWithoutProfileConnection;
    }

    @Override
    public String toString() {
        return "Preferences{"
                + "gattOperationTimeoutSeconds=" + mGattOperationTimeoutSeconds + ", "
                + "gattConnectionTimeoutSeconds=" + mGattConnectionTimeoutSeconds + ", "
                + "bluetoothToggleTimeoutSeconds=" + mBluetoothToggleTimeoutSeconds + ", "
                + "bluetoothToggleSleepSeconds=" + mBluetoothToggleSleepSeconds + ", "
                + "classicDiscoveryTimeoutSeconds=" + mClassicDiscoveryTimeoutSeconds + ", "
                + "numDiscoverAttempts=" + mNumDiscoverAttempts + ", "
                + "discoveryRetrySleepSeconds=" + mDiscoveryRetrySleepSeconds + ", "
                + "ignoreDiscoveryError=" + mIgnoreDiscoveryError + ", "
                + "sdpTimeoutSeconds=" + mSdpTimeoutSeconds + ", "
                + "numSdpAttempts=" + mNumSdpAttempts + ", "
                + "numCreateBondAttempts=" + mNumCreateBondAttempts + ", "
                + "numConnectAttempts=" + mNumConnectAttempts + ", "
                + "numWriteAccountKeyAttempts=" + mNumWriteAccountKeyAttempts + ", "
                + "toggleBluetoothOnFailure=" + mToggleBluetoothOnFailure + ", "
                + "bluetoothStateUsesPolling=" + mBluetoothStateUsesPolling + ", "
                + "bluetoothStatePollingMillis=" + mBluetoothStatePollingMillis + ", "
                + "numAttempts=" + mNumAttempts + ", "
                + "enableBrEdrHandover=" + mEnableBrEdrHandover + ", "
                + "brHandoverDataCharacteristicId=" + mBrHandoverDataCharacteristicId + ", "
                + "bluetoothSigDataCharacteristicId=" + mBluetoothSigDataCharacteristicId + ", "
                + "firmwareVersionCharacteristicId=" + mFirmwareVersionCharacteristicId + ", "
                + "brTransportBlockDataDescriptorId=" + mBrTransportBlockDataDescriptorId + ", "
                + "waitForUuidsAfterBonding=" + mWaitForUuidsAfterBonding + ", "
                + "receiveUuidsAndBondedEventBeforeClose=" + mReceiveUuidsAndBondedEventBeforeClose
                + ", "
                + "removeBondTimeoutSeconds=" + mRemoveBondTimeoutSeconds + ", "
                + "removeBondSleepMillis=" + mRemoveBondSleepMillis + ", "
                + "createBondTimeoutSeconds=" + mCreateBondTimeoutSeconds + ", "
                + "hidCreateBondTimeoutSeconds=" + mHidCreateBondTimeoutSeconds + ", "
                + "proxyTimeoutSeconds=" + mProxyTimeoutSeconds + ", "
                + "rejectPhonebookAccess=" + mRejectPhonebookAccess + ", "
                + "rejectMessageAccess=" + mRejectMessageAccess + ", "
                + "rejectSimAccess=" + mRejectSimAccess + ", "
                + "writeAccountKeySleepMillis=" + mWriteAccountKeySleepMillis + ", "
                + "skipDisconnectingGattBeforeWritingAccountKey="
                + mSkipDisconnectingGattBeforeWritingAccountKey + ", "
                + "moreEventLogForQuality=" + mMoreEventLogForQuality + ", "
                + "retryGattConnectionAndSecretHandshake=" + mRetryGattConnectionAndSecretHandshake
                + ", "
                + "gattConnectShortTimeoutMs=" + mGattConnectShortTimeoutMs + ", "
                + "gattConnectLongTimeoutMs=" + mGattConnectLongTimeoutMs + ", "
                + "gattConnectShortTimeoutRetryMaxSpentTimeMs="
                + mGattConnectShortTimeoutRetryMaxSpentTimeMs + ", "
                + "addressRotateRetryMaxSpentTimeMs=" + mAddressRotateRetryMaxSpentTimeMs + ", "
                + "pairingRetryDelayMs=" + mPairingRetryDelayMs + ", "
                + "secretHandshakeShortTimeoutMs=" + mSecretHandshakeShortTimeoutMs + ", "
                + "secretHandshakeLongTimeoutMs=" + mSecretHandshakeLongTimeoutMs + ", "
                + "secretHandshakeShortTimeoutRetryMaxSpentTimeMs="
                + mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs + ", "
                + "secretHandshakeLongTimeoutRetryMaxSpentTimeMs="
                + mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs + ", "
                + "secretHandshakeRetryAttempts=" + mSecretHandshakeRetryAttempts + ", "
                + "secretHandshakeRetryGattConnectionMaxSpentTimeMs="
                + mSecretHandshakeRetryGattConnectionMaxSpentTimeMs + ", "
                + "signalLostRetryMaxSpentTimeMs=" + mSignalLostRetryMaxSpentTimeMs + ", "
                + "gattConnectionAndSecretHandshakeNoRetryGattError="
                + mGattConnectionAndSecretHandshakeNoRetryGattError + ", "
                + "retrySecretHandshakeTimeout=" + mRetrySecretHandshakeTimeout + ", "
                + "logUserManualRetry=" + mLogUserManualRetry + ", "
                + "pairFailureCounts=" + mPairFailureCounts + ", "
                + "cachedDeviceAddress=" + mCachedDeviceAddress + ", "
                + "possibleCachedDeviceAddress=" + mPossibleCachedDeviceAddress + ", "
                + "sameModelIdPairedDeviceCount=" + mSameModelIdPairedDeviceCount + ", "
                + "isDeviceFinishCheckAddressFromCache=" + mIsDeviceFinishCheckAddressFromCache
                + ", "
                + "logPairWithCachedModelId=" + mLogPairWithCachedModelId + ", "
                + "directConnectProfileIfModelIdInCache=" + mDirectConnectProfileIfModelIdInCache
                + ", "
                + "acceptPasskey=" + mAcceptPasskey + ", "
                + "supportedProfileUuids=" + Arrays.toString(mSupportedProfileUuids) + ", "
                + "providerInitiatesBondingIfSupported=" + mProviderInitiatesBondingIfSupported
                + ", "
                + "attemptDirectConnectionWhenPreviouslyBonded="
                + mAttemptDirectConnectionWhenPreviouslyBonded + ", "
                + "automaticallyReconnectGattWhenNeeded=" + mAutomaticallyReconnectGattWhenNeeded
                + ", "
                + "skipConnectingProfiles=" + mSkipConnectingProfiles + ", "
                + "ignoreUuidTimeoutAfterBonded=" + mIgnoreUuidTimeoutAfterBonded + ", "
                + "specifyCreateBondTransportType=" + mSpecifyCreateBondTransportType + ", "
                + "createBondTransportType=" + mCreateBondTransportType + ", "
                + "increaseIntentFilterPriority=" + mIncreaseIntentFilterPriority + ", "
                + "evaluatePerformance=" + mEvaluatePerformance + ", "
                + "extraLoggingInformation=" + mExtraLoggingInformation + ", "
                + "enableNamingCharacteristic=" + mEnableNamingCharacteristic + ", "
                + "enableFirmwareVersionCharacteristic=" + mEnableFirmwareVersionCharacteristic
                + ", "
                + "keepSameAccountKeyWrite=" + mKeepSameAccountKeyWrite + ", "
                + "isRetroactivePairing=" + mIsRetroactivePairing + ", "
                + "numSdpAttemptsAfterBonded=" + mNumSdpAttemptsAfterBonded + ", "
                + "supportHidDevice=" + mSupportHidDevice + ", "
                + "enablePairingWhileDirectlyConnecting=" + mEnablePairingWhileDirectlyConnecting
                + ", "
                + "acceptConsentForFastPairOne=" + mAcceptConsentForFastPairOne + ", "
                + "gattConnectRetryTimeoutMillis=" + mGattConnectRetryTimeoutMillis + ", "
                + "enable128BitCustomGattCharacteristicsId="
                + mEnable128BitCustomGattCharacteristicsId + ", "
                + "enableSendExceptionStepToValidator=" + mEnableSendExceptionStepToValidator + ", "
                + "enableAdditionalDataTypeWhenActionOverBle="
                + mEnableAdditionalDataTypeWhenActionOverBle + ", "
                + "checkBondStateWhenSkipConnectingProfiles="
                + mCheckBondStateWhenSkipConnectingProfiles + ", "
                + "handlePasskeyConfirmationByUi=" + mHandlePasskeyConfirmationByUi + ", "
                + "enablePairFlowShowUiWithoutProfileConnection="
                + mEnablePairFlowShowUiWithoutProfileConnection
                + "}";
    }

    /**
     * Converts an instance to a builder.
     */
    public Builder toBuilder() {
        return new Preferences.Builder(this);
    }

    /**
     * Constructs a builder.
     */
    public static Builder builder() {
        return new Preferences.Builder()
                .setGattOperationTimeoutSeconds(3)
                .setGattConnectionTimeoutSeconds(15)
                .setBluetoothToggleTimeoutSeconds(10)
                .setBluetoothToggleSleepSeconds(2)
                .setClassicDiscoveryTimeoutSeconds(10)
                .setNumDiscoverAttempts(3)
                .setDiscoveryRetrySleepSeconds(1)
                .setIgnoreDiscoveryError(false)
                .setSdpTimeoutSeconds(10)
                .setNumSdpAttempts(3)
                .setNumCreateBondAttempts(3)
                .setNumConnectAttempts(1)
                .setNumWriteAccountKeyAttempts(3)
                .setToggleBluetoothOnFailure(false)
                .setBluetoothStateUsesPolling(true)
                .setBluetoothStatePollingMillis(1000)
                .setNumAttempts(2)
                .setEnableBrEdrHandover(false)
                .setBrHandoverDataCharacteristicId(get16BitUuid(
                        Constants.TransportDiscoveryService.BrHandoverDataCharacteristic.ID))
                .setBluetoothSigDataCharacteristicId(get16BitUuid(
                        Constants.TransportDiscoveryService.BluetoothSigDataCharacteristic.ID))
                .setFirmwareVersionCharacteristicId(get16BitUuid(FirmwareVersionCharacteristic.ID))
                .setBrTransportBlockDataDescriptorId(
                        get16BitUuid(
                                Constants.TransportDiscoveryService.BluetoothSigDataCharacteristic
                                        .BrTransportBlockDataDescriptor.ID))
                .setWaitForUuidsAfterBonding(true)
                .setReceiveUuidsAndBondedEventBeforeClose(true)
                .setRemoveBondTimeoutSeconds(5)
                .setRemoveBondSleepMillis(1000)
                .setCreateBondTimeoutSeconds(15)
                .setHidCreateBondTimeoutSeconds(40)
                .setProxyTimeoutSeconds(2)
                .setRejectPhonebookAccess(false)
                .setRejectMessageAccess(false)
                .setRejectSimAccess(false)
                .setAcceptPasskey(true)
                .setSupportedProfileUuids(Constants.getSupportedProfiles())
                .setWriteAccountKeySleepMillis(2000)
                .setProviderInitiatesBondingIfSupported(false)
                .setAttemptDirectConnectionWhenPreviouslyBonded(false)
                .setAutomaticallyReconnectGattWhenNeeded(false)
                .setSkipDisconnectingGattBeforeWritingAccountKey(false)
                .setSkipConnectingProfiles(false)
                .setIgnoreUuidTimeoutAfterBonded(false)
                .setSpecifyCreateBondTransportType(false)
                .setCreateBondTransportType(0 /*BluetoothDevice.TRANSPORT_AUTO*/)
                .setIncreaseIntentFilterPriority(true)
                .setEvaluatePerformance(false)
                .setKeepSameAccountKeyWrite(true)
                .setEnableNamingCharacteristic(false)
                .setEnableFirmwareVersionCharacteristic(false)
                .setIsRetroactivePairing(false)
                .setNumSdpAttemptsAfterBonded(1)
                .setSupportHidDevice(false)
                .setEnablePairingWhileDirectlyConnecting(true)
                .setAcceptConsentForFastPairOne(true)
                .setGattConnectRetryTimeoutMillis(0)
                .setEnable128BitCustomGattCharacteristicsId(true)
                .setEnableSendExceptionStepToValidator(true)
                .setEnableAdditionalDataTypeWhenActionOverBle(true)
                .setCheckBondStateWhenSkipConnectingProfiles(true)
                .setHandlePasskeyConfirmationByUi(false)
                .setMoreEventLogForQuality(true)
                .setRetryGattConnectionAndSecretHandshake(true)
                .setGattConnectShortTimeoutMs(7000)
                .setGattConnectLongTimeoutMs(15000)
                .setGattConnectShortTimeoutRetryMaxSpentTimeMs(10000)
                .setAddressRotateRetryMaxSpentTimeMs(15000)
                .setPairingRetryDelayMs(100)
                .setSecretHandshakeShortTimeoutMs(3000)
                .setSecretHandshakeLongTimeoutMs(10000)
                .setSecretHandshakeShortTimeoutRetryMaxSpentTimeMs(5000)
                .setSecretHandshakeLongTimeoutRetryMaxSpentTimeMs(7000)
                .setSecretHandshakeRetryAttempts(3)
                .setSecretHandshakeRetryGattConnectionMaxSpentTimeMs(15000)
                .setSignalLostRetryMaxSpentTimeMs(15000)
                .setGattConnectionAndSecretHandshakeNoRetryGattError(ImmutableSet.of())
                .setRetrySecretHandshakeTimeout(false)
                .setLogUserManualRetry(true)
                .setPairFailureCounts(0)
                .setEnablePairFlowShowUiWithoutProfileConnection(true)
                .setPairFailureCounts(0)
                .setLogPairWithCachedModelId(true)
                .setDirectConnectProfileIfModelIdInCache(false)
                .setCachedDeviceAddress("")
                .setPossibleCachedDeviceAddress("")
                .setSameModelIdPairedDeviceCount(0)
                .setIsDeviceFinishCheckAddressFromCache(true);
    }

    /**
     * Constructs a builder from GmsLog.
     */
    // TODO(b/206668142): remove this builder once api is ready.
    public static Builder builderFromGmsLog() {
        return new Preferences.Builder()
                .setGattOperationTimeoutSeconds(10)
                .setGattConnectionTimeoutSeconds(15)
                .setBluetoothToggleTimeoutSeconds(10)
                .setBluetoothToggleSleepSeconds(2)
                .setClassicDiscoveryTimeoutSeconds(13)
                .setNumDiscoverAttempts(3)
                .setDiscoveryRetrySleepSeconds(1)
                .setIgnoreDiscoveryError(true)
                .setSdpTimeoutSeconds(10)
                .setNumSdpAttempts(0)
                .setNumCreateBondAttempts(3)
                .setNumConnectAttempts(2)
                .setNumWriteAccountKeyAttempts(3)
                .setToggleBluetoothOnFailure(false)
                .setBluetoothStateUsesPolling(true)
                .setBluetoothStatePollingMillis(1000)
                .setNumAttempts(2)
                .setEnableBrEdrHandover(false)
                .setBrHandoverDataCharacteristicId((short) 11265)
                .setBluetoothSigDataCharacteristicId((short) 11266)
                .setFirmwareVersionCharacteristicId((short) 10790)
                .setBrTransportBlockDataDescriptorId((short) 11267)
                .setWaitForUuidsAfterBonding(true)
                .setReceiveUuidsAndBondedEventBeforeClose(true)
                .setRemoveBondTimeoutSeconds(5)
                .setRemoveBondSleepMillis(1000)
                .setCreateBondTimeoutSeconds(15)
                .setHidCreateBondTimeoutSeconds(40)
                .setProxyTimeoutSeconds(2)
                .setRejectPhonebookAccess(false)
                .setRejectMessageAccess(false)
                .setRejectSimAccess(false)
                .setAcceptPasskey(true)
                .setSupportedProfileUuids(Constants.getSupportedProfiles())
                .setWriteAccountKeySleepMillis(2000)
                .setProviderInitiatesBondingIfSupported(false)
                .setAttemptDirectConnectionWhenPreviouslyBonded(true)
                .setAutomaticallyReconnectGattWhenNeeded(true)
                .setSkipDisconnectingGattBeforeWritingAccountKey(true)
                .setSkipConnectingProfiles(false)
                .setIgnoreUuidTimeoutAfterBonded(true)
                .setSpecifyCreateBondTransportType(false)
                .setCreateBondTransportType(0 /*BluetoothDevice.TRANSPORT_AUTO*/)
                .setIncreaseIntentFilterPriority(true)
                .setEvaluatePerformance(true)
                .setKeepSameAccountKeyWrite(true)
                .setEnableNamingCharacteristic(true)
                .setEnableFirmwareVersionCharacteristic(true)
                .setIsRetroactivePairing(false)
                .setNumSdpAttemptsAfterBonded(1)
                .setSupportHidDevice(false)
                .setEnablePairingWhileDirectlyConnecting(true)
                .setAcceptConsentForFastPairOne(true)
                .setGattConnectRetryTimeoutMillis(18000)
                .setEnable128BitCustomGattCharacteristicsId(true)
                .setEnableSendExceptionStepToValidator(true)
                .setEnableAdditionalDataTypeWhenActionOverBle(true)
                .setCheckBondStateWhenSkipConnectingProfiles(true)
                .setHandlePasskeyConfirmationByUi(false)
                .setMoreEventLogForQuality(true)
                .setRetryGattConnectionAndSecretHandshake(true)
                .setGattConnectShortTimeoutMs(7000)
                .setGattConnectLongTimeoutMs(15000)
                .setGattConnectShortTimeoutRetryMaxSpentTimeMs(10000)
                .setAddressRotateRetryMaxSpentTimeMs(15000)
                .setPairingRetryDelayMs(100)
                .setSecretHandshakeShortTimeoutMs(3000)
                .setSecretHandshakeLongTimeoutMs(10000)
                .setSecretHandshakeShortTimeoutRetryMaxSpentTimeMs(5000)
                .setSecretHandshakeLongTimeoutRetryMaxSpentTimeMs(7000)
                .setSecretHandshakeRetryAttempts(3)
                .setSecretHandshakeRetryGattConnectionMaxSpentTimeMs(15000)
                .setSignalLostRetryMaxSpentTimeMs(15000)
                .setGattConnectionAndSecretHandshakeNoRetryGattError(ImmutableSet.of(257))
                .setRetrySecretHandshakeTimeout(false)
                .setLogUserManualRetry(true)
                .setPairFailureCounts(0)
                .setEnablePairFlowShowUiWithoutProfileConnection(true)
                .setPairFailureCounts(0)
                .setLogPairWithCachedModelId(true)
                .setDirectConnectProfileIfModelIdInCache(true)
                .setCachedDeviceAddress("")
                .setPossibleCachedDeviceAddress("")
                .setSameModelIdPairedDeviceCount(0)
                .setIsDeviceFinishCheckAddressFromCache(true);
    }

    /**
     * Preferences builder.
     */
    public static class Builder {

        private int mGattOperationTimeoutSeconds;
        private int mGattConnectionTimeoutSeconds;
        private int mBluetoothToggleTimeoutSeconds;
        private int mBluetoothToggleSleepSeconds;
        private int mClassicDiscoveryTimeoutSeconds;
        private int mNumDiscoverAttempts;
        private int mDiscoveryRetrySleepSeconds;
        private boolean mIgnoreDiscoveryError;
        private int mSdpTimeoutSeconds;
        private int mNumSdpAttempts;
        private int mNumCreateBondAttempts;
        private int mNumConnectAttempts;
        private int mNumWriteAccountKeyAttempts;
        private boolean mToggleBluetoothOnFailure;
        private boolean mBluetoothStateUsesPolling;
        private int mBluetoothStatePollingMillis;
        private int mNumAttempts;
        private boolean mEnableBrEdrHandover;
        private short mBrHandoverDataCharacteristicId;
        private short mBluetoothSigDataCharacteristicId;
        private short mFirmwareVersionCharacteristicId;
        private short mBrTransportBlockDataDescriptorId;
        private boolean mWaitForUuidsAfterBonding;
        private boolean mReceiveUuidsAndBondedEventBeforeClose;
        private int mRemoveBondTimeoutSeconds;
        private int mRemoveBondSleepMillis;
        private int mCreateBondTimeoutSeconds;
        private int mHidCreateBondTimeoutSeconds;
        private int mProxyTimeoutSeconds;
        private boolean mRejectPhonebookAccess;
        private boolean mRejectMessageAccess;
        private boolean mRejectSimAccess;
        private int mWriteAccountKeySleepMillis;
        private boolean mSkipDisconnectingGattBeforeWritingAccountKey;
        private boolean mMoreEventLogForQuality;
        private boolean mRetryGattConnectionAndSecretHandshake;
        private long mGattConnectShortTimeoutMs;
        private long mGattConnectLongTimeoutMs;
        private long mGattConnectShortTimeoutRetryMaxSpentTimeMs;
        private long mAddressRotateRetryMaxSpentTimeMs;
        private long mPairingRetryDelayMs;
        private long mSecretHandshakeShortTimeoutMs;
        private long mSecretHandshakeLongTimeoutMs;
        private long mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs;
        private long mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs;
        private long mSecretHandshakeRetryAttempts;
        private long mSecretHandshakeRetryGattConnectionMaxSpentTimeMs;
        private long mSignalLostRetryMaxSpentTimeMs;
        private ImmutableSet<Integer> mGattConnectionAndSecretHandshakeNoRetryGattError;
        private boolean mRetrySecretHandshakeTimeout;
        private boolean mLogUserManualRetry;
        private int mPairFailureCounts;
        private String mCachedDeviceAddress;
        private String mPossibleCachedDeviceAddress;
        private int mSameModelIdPairedDeviceCount;
        private boolean mIsDeviceFinishCheckAddressFromCache;
        private boolean mLogPairWithCachedModelId;
        private boolean mDirectConnectProfileIfModelIdInCache;
        private boolean mAcceptPasskey;
        private byte[] mSupportedProfileUuids;
        private boolean mProviderInitiatesBondingIfSupported;
        private boolean mAttemptDirectConnectionWhenPreviouslyBonded;
        private boolean mAutomaticallyReconnectGattWhenNeeded;
        private boolean mSkipConnectingProfiles;
        private boolean mIgnoreUuidTimeoutAfterBonded;
        private boolean mSpecifyCreateBondTransportType;
        private int mCreateBondTransportType;
        private boolean mIncreaseIntentFilterPriority;
        private boolean mEvaluatePerformance;
        private Preferences.ExtraLoggingInformation mExtraLoggingInformation;
        private boolean mEnableNamingCharacteristic;
        private boolean mEnableFirmwareVersionCharacteristic;
        private boolean mKeepSameAccountKeyWrite;
        private boolean mIsRetroactivePairing;
        private int mNumSdpAttemptsAfterBonded;
        private boolean mSupportHidDevice;
        private boolean mEnablePairingWhileDirectlyConnecting;
        private boolean mAcceptConsentForFastPairOne;
        private int mGattConnectRetryTimeoutMillis;
        private boolean mEnable128BitCustomGattCharacteristicsId;
        private boolean mEnableSendExceptionStepToValidator;
        private boolean mEnableAdditionalDataTypeWhenActionOverBle;
        private boolean mCheckBondStateWhenSkipConnectingProfiles;
        private boolean mHandlePasskeyConfirmationByUi;
        private boolean mEnablePairFlowShowUiWithoutProfileConnection;

        private Builder() {
        }

        private Builder(Preferences source) {
            this.mGattOperationTimeoutSeconds = source.getGattOperationTimeoutSeconds();
            this.mGattConnectionTimeoutSeconds = source.getGattConnectionTimeoutSeconds();
            this.mBluetoothToggleTimeoutSeconds = source.getBluetoothToggleTimeoutSeconds();
            this.mBluetoothToggleSleepSeconds = source.getBluetoothToggleSleepSeconds();
            this.mClassicDiscoveryTimeoutSeconds = source.getClassicDiscoveryTimeoutSeconds();
            this.mNumDiscoverAttempts = source.getNumDiscoverAttempts();
            this.mDiscoveryRetrySleepSeconds = source.getDiscoveryRetrySleepSeconds();
            this.mIgnoreDiscoveryError = source.getIgnoreDiscoveryError();
            this.mSdpTimeoutSeconds = source.getSdpTimeoutSeconds();
            this.mNumSdpAttempts = source.getNumSdpAttempts();
            this.mNumCreateBondAttempts = source.getNumCreateBondAttempts();
            this.mNumConnectAttempts = source.getNumConnectAttempts();
            this.mNumWriteAccountKeyAttempts = source.getNumWriteAccountKeyAttempts();
            this.mToggleBluetoothOnFailure = source.getToggleBluetoothOnFailure();
            this.mBluetoothStateUsesPolling = source.getBluetoothStateUsesPolling();
            this.mBluetoothStatePollingMillis = source.getBluetoothStatePollingMillis();
            this.mNumAttempts = source.getNumAttempts();
            this.mEnableBrEdrHandover = source.getEnableBrEdrHandover();
            this.mBrHandoverDataCharacteristicId = source.getBrHandoverDataCharacteristicId();
            this.mBluetoothSigDataCharacteristicId = source.getBluetoothSigDataCharacteristicId();
            this.mFirmwareVersionCharacteristicId = source.getFirmwareVersionCharacteristicId();
            this.mBrTransportBlockDataDescriptorId = source.getBrTransportBlockDataDescriptorId();
            this.mWaitForUuidsAfterBonding = source.getWaitForUuidsAfterBonding();
            this.mReceiveUuidsAndBondedEventBeforeClose = source
                    .getReceiveUuidsAndBondedEventBeforeClose();
            this.mRemoveBondTimeoutSeconds = source.getRemoveBondTimeoutSeconds();
            this.mRemoveBondSleepMillis = source.getRemoveBondSleepMillis();
            this.mCreateBondTimeoutSeconds = source.getCreateBondTimeoutSeconds();
            this.mHidCreateBondTimeoutSeconds = source.getHidCreateBondTimeoutSeconds();
            this.mProxyTimeoutSeconds = source.getProxyTimeoutSeconds();
            this.mRejectPhonebookAccess = source.getRejectPhonebookAccess();
            this.mRejectMessageAccess = source.getRejectMessageAccess();
            this.mRejectSimAccess = source.getRejectSimAccess();
            this.mWriteAccountKeySleepMillis = source.getWriteAccountKeySleepMillis();
            this.mSkipDisconnectingGattBeforeWritingAccountKey = source
                    .getSkipDisconnectingGattBeforeWritingAccountKey();
            this.mMoreEventLogForQuality = source.getMoreEventLogForQuality();
            this.mRetryGattConnectionAndSecretHandshake = source
                    .getRetryGattConnectionAndSecretHandshake();
            this.mGattConnectShortTimeoutMs = source.getGattConnectShortTimeoutMs();
            this.mGattConnectLongTimeoutMs = source.getGattConnectLongTimeoutMs();
            this.mGattConnectShortTimeoutRetryMaxSpentTimeMs = source
                    .getGattConnectShortTimeoutRetryMaxSpentTimeMs();
            this.mAddressRotateRetryMaxSpentTimeMs = source.getAddressRotateRetryMaxSpentTimeMs();
            this.mPairingRetryDelayMs = source.getPairingRetryDelayMs();
            this.mSecretHandshakeShortTimeoutMs = source.getSecretHandshakeShortTimeoutMs();
            this.mSecretHandshakeLongTimeoutMs = source.getSecretHandshakeLongTimeoutMs();
            this.mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs = source
                    .getSecretHandshakeShortTimeoutRetryMaxSpentTimeMs();
            this.mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs = source
                    .getSecretHandshakeLongTimeoutRetryMaxSpentTimeMs();
            this.mSecretHandshakeRetryAttempts = source.getSecretHandshakeRetryAttempts();
            this.mSecretHandshakeRetryGattConnectionMaxSpentTimeMs = source
                    .getSecretHandshakeRetryGattConnectionMaxSpentTimeMs();
            this.mSignalLostRetryMaxSpentTimeMs = source.getSignalLostRetryMaxSpentTimeMs();
            this.mGattConnectionAndSecretHandshakeNoRetryGattError = source
                    .getGattConnectionAndSecretHandshakeNoRetryGattError();
            this.mRetrySecretHandshakeTimeout = source.getRetrySecretHandshakeTimeout();
            this.mLogUserManualRetry = source.getLogUserManualRetry();
            this.mPairFailureCounts = source.getPairFailureCounts();
            this.mCachedDeviceAddress = source.getCachedDeviceAddress();
            this.mPossibleCachedDeviceAddress = source.getPossibleCachedDeviceAddress();
            this.mSameModelIdPairedDeviceCount = source.getSameModelIdPairedDeviceCount();
            this.mIsDeviceFinishCheckAddressFromCache = source
                    .getIsDeviceFinishCheckAddressFromCache();
            this.mLogPairWithCachedModelId = source.getLogPairWithCachedModelId();
            this.mDirectConnectProfileIfModelIdInCache = source
                    .getDirectConnectProfileIfModelIdInCache();
            this.mAcceptPasskey = source.getAcceptPasskey();
            this.mSupportedProfileUuids = source.getSupportedProfileUuids();
            this.mProviderInitiatesBondingIfSupported = source
                    .getProviderInitiatesBondingIfSupported();
            this.mAttemptDirectConnectionWhenPreviouslyBonded = source
                    .getAttemptDirectConnectionWhenPreviouslyBonded();
            this.mAutomaticallyReconnectGattWhenNeeded = source
                    .getAutomaticallyReconnectGattWhenNeeded();
            this.mSkipConnectingProfiles = source.getSkipConnectingProfiles();
            this.mIgnoreUuidTimeoutAfterBonded = source.getIgnoreUuidTimeoutAfterBonded();
            this.mSpecifyCreateBondTransportType = source.getSpecifyCreateBondTransportType();
            this.mCreateBondTransportType = source.getCreateBondTransportType();
            this.mIncreaseIntentFilterPriority = source.getIncreaseIntentFilterPriority();
            this.mEvaluatePerformance = source.getEvaluatePerformance();
            this.mExtraLoggingInformation = source.getExtraLoggingInformation();
            this.mEnableNamingCharacteristic = source.getEnableNamingCharacteristic();
            this.mEnableFirmwareVersionCharacteristic = source
                    .getEnableFirmwareVersionCharacteristic();
            this.mKeepSameAccountKeyWrite = source.getKeepSameAccountKeyWrite();
            this.mIsRetroactivePairing = source.getIsRetroactivePairing();
            this.mNumSdpAttemptsAfterBonded = source.getNumSdpAttemptsAfterBonded();
            this.mSupportHidDevice = source.getSupportHidDevice();
            this.mEnablePairingWhileDirectlyConnecting = source
                    .getEnablePairingWhileDirectlyConnecting();
            this.mAcceptConsentForFastPairOne = source.getAcceptConsentForFastPairOne();
            this.mGattConnectRetryTimeoutMillis = source.getGattConnectRetryTimeoutMillis();
            this.mEnable128BitCustomGattCharacteristicsId = source
                    .getEnable128BitCustomGattCharacteristicsId();
            this.mEnableSendExceptionStepToValidator = source
                    .getEnableSendExceptionStepToValidator();
            this.mEnableAdditionalDataTypeWhenActionOverBle = source
                    .getEnableAdditionalDataTypeWhenActionOverBle();
            this.mCheckBondStateWhenSkipConnectingProfiles = source
                    .getCheckBondStateWhenSkipConnectingProfiles();
            this.mHandlePasskeyConfirmationByUi = source.getHandlePasskeyConfirmationByUi();
            this.mEnablePairFlowShowUiWithoutProfileConnection = source
                    .getEnablePairFlowShowUiWithoutProfileConnection();
        }

        /**
         * Set gatt operation timeout.
         */
        public Builder setGattOperationTimeoutSeconds(int value) {
            this.mGattOperationTimeoutSeconds = value;
            return this;
        }

        /**
         * Set gatt connection timeout.
         */
        public Builder setGattConnectionTimeoutSeconds(int value) {
            this.mGattConnectionTimeoutSeconds = value;
            return this;
        }

        /**
         * Set bluetooth toggle timeout.
         */
        public Builder setBluetoothToggleTimeoutSeconds(int value) {
            this.mBluetoothToggleTimeoutSeconds = value;
            return this;
        }

        /**
         * Set bluetooth toggle sleep time.
         */
        public Builder setBluetoothToggleSleepSeconds(int value) {
            this.mBluetoothToggleSleepSeconds = value;
            return this;
        }

        /**
         * Set classic discovery timeout.
         */
        public Builder setClassicDiscoveryTimeoutSeconds(int value) {
            this.mClassicDiscoveryTimeoutSeconds = value;
            return this;
        }

        /**
         * Set number of discover attempts allowed.
         */
        public Builder setNumDiscoverAttempts(int value) {
            this.mNumDiscoverAttempts = value;
            return this;
        }

        /**
         * Set discovery retry sleep time.
         */
        public Builder setDiscoveryRetrySleepSeconds(int value) {
            this.mDiscoveryRetrySleepSeconds = value;
            return this;
        }

        /**
         * Set whether to ignore discovery error.
         */
        public Builder setIgnoreDiscoveryError(boolean value) {
            this.mIgnoreDiscoveryError = value;
            return this;
        }

        /**
         * Set sdp timeout.
         */
        public Builder setSdpTimeoutSeconds(int value) {
            this.mSdpTimeoutSeconds = value;
            return this;
        }

        /**
         * Set number of sdp attempts allowed.
         */
        public Builder setNumSdpAttempts(int value) {
            this.mNumSdpAttempts = value;
            return this;
        }

        /**
         * Set number of allowed attempts to create bond.
         */
        public Builder setNumCreateBondAttempts(int value) {
            this.mNumCreateBondAttempts = value;
            return this;
        }

        /**
         * Set number of connect attempts allowed.
         */
        public Builder setNumConnectAttempts(int value) {
            this.mNumConnectAttempts = value;
            return this;
        }

        /**
         * Set number of write account key attempts allowed.
         */
        public Builder setNumWriteAccountKeyAttempts(int value) {
            this.mNumWriteAccountKeyAttempts = value;
            return this;
        }

        /**
         * Set whether to retry by bluetooth toggle on failure.
         */
        public Builder setToggleBluetoothOnFailure(boolean value) {
            this.mToggleBluetoothOnFailure = value;
            return this;
        }

        /**
         * Set whether to use polling to set bluetooth status.
         */
        public Builder setBluetoothStateUsesPolling(boolean value) {
            this.mBluetoothStateUsesPolling = value;
            return this;
        }

        /**
         * Set Bluetooth state polling timeout.
         */
        public Builder setBluetoothStatePollingMillis(int value) {
            this.mBluetoothStatePollingMillis = value;
            return this;
        }

        /**
         * Set number of attempts.
         */
        public Builder setNumAttempts(int value) {
            this.mNumAttempts = value;
            return this;
        }

        /**
         * Set whether to enable BrEdr handover.
         */
        public Builder setEnableBrEdrHandover(boolean value) {
            this.mEnableBrEdrHandover = value;
            return this;
        }

        /**
         * Set Br handover data characteristic Id.
         */
        public Builder setBrHandoverDataCharacteristicId(short value) {
            this.mBrHandoverDataCharacteristicId = value;
            return this;
        }

        /**
         * Set Bluetooth Sig data characteristic Id.
         */
        public Builder setBluetoothSigDataCharacteristicId(short value) {
            this.mBluetoothSigDataCharacteristicId = value;
            return this;
        }

        /**
         * Set Firmware version characteristic id.
         */
        public Builder setFirmwareVersionCharacteristicId(short value) {
            this.mFirmwareVersionCharacteristicId = value;
            return this;
        }

        /**
         * Set Br transport block data descriptor id.
         */
        public Builder setBrTransportBlockDataDescriptorId(short value) {
            this.mBrTransportBlockDataDescriptorId = value;
            return this;
        }

        /**
         * Set whether to wait for Uuids after bonding.
         */
        public Builder setWaitForUuidsAfterBonding(boolean value) {
            this.mWaitForUuidsAfterBonding = value;
            return this;
        }

        /**
         * Set whether to receive Uuids and bonded event before close.
         */
        public Builder setReceiveUuidsAndBondedEventBeforeClose(boolean value) {
            this.mReceiveUuidsAndBondedEventBeforeClose = value;
            return this;
        }

        /**
         * Set remove bond timeout.
         */
        public Builder setRemoveBondTimeoutSeconds(int value) {
            this.mRemoveBondTimeoutSeconds = value;
            return this;
        }

        /**
         * Set remove bound sleep time.
         */
        public Builder setRemoveBondSleepMillis(int value) {
            this.mRemoveBondSleepMillis = value;
            return this;
        }

        /**
         * Set create bond timeout.
         */
        public Builder setCreateBondTimeoutSeconds(int value) {
            this.mCreateBondTimeoutSeconds = value;
            return this;
        }

        /**
         * Set Hid create bond timeout.
         */
        public Builder setHidCreateBondTimeoutSeconds(int value) {
            this.mHidCreateBondTimeoutSeconds = value;
            return this;
        }

        /**
         * Set proxy timeout.
         */
        public Builder setProxyTimeoutSeconds(int value) {
            this.mProxyTimeoutSeconds = value;
            return this;
        }

        /**
         * Set whether to reject phone book access.
         */
        public Builder setRejectPhonebookAccess(boolean value) {
            this.mRejectPhonebookAccess = value;
            return this;
        }

        /**
         * Set whether to reject message access.
         */
        public Builder setRejectMessageAccess(boolean value) {
            this.mRejectMessageAccess = value;
            return this;
        }

        /**
         * Set whether to reject slim access.
         */
        public Builder setRejectSimAccess(boolean value) {
            this.mRejectSimAccess = value;
            return this;
        }

        /**
         * Set whether to accept passkey.
         */
        public Builder setAcceptPasskey(boolean value) {
            this.mAcceptPasskey = value;
            return this;
        }

        /**
         * Set supported profile Uuids.
         */
        public Builder setSupportedProfileUuids(byte[] value) {
            this.mSupportedProfileUuids = value;
            return this;
        }

        /**
         * Set whether to collect more event log for quality.
         */
        public Builder setMoreEventLogForQuality(boolean value) {
            this.mMoreEventLogForQuality = value;
            return this;
        }

        /**
         * Set supported profile Uuids.
         */
        public Builder setSupportedProfileUuids(short... uuids) {
            return setSupportedProfileUuids(Bytes.toBytes(ByteOrder.BIG_ENDIAN, uuids));
        }

        /**
         * Set write account key sleep time.
         */
        public Builder setWriteAccountKeySleepMillis(int value) {
            this.mWriteAccountKeySleepMillis = value;
            return this;
        }

        /**
         * Set whether to do provider initialized bonding if supported.
         */
        public Builder setProviderInitiatesBondingIfSupported(boolean value) {
            this.mProviderInitiatesBondingIfSupported = value;
            return this;
        }

        /**
         * Set whether to try direct connection when the device is previously bonded.
         */
        public Builder setAttemptDirectConnectionWhenPreviouslyBonded(boolean value) {
            this.mAttemptDirectConnectionWhenPreviouslyBonded = value;
            return this;
        }

        /**
         * Set whether to automatically reconnect gatt when needed.
         */
        public Builder setAutomaticallyReconnectGattWhenNeeded(boolean value) {
            this.mAutomaticallyReconnectGattWhenNeeded = value;
            return this;
        }

        /**
         * Set whether to skip disconnecting gatt before writing account key.
         */
        public Builder setSkipDisconnectingGattBeforeWritingAccountKey(boolean value) {
            this.mSkipDisconnectingGattBeforeWritingAccountKey = value;
            return this;
        }

        /**
         * Set whether to skip connecting profiles.
         */
        public Builder setSkipConnectingProfiles(boolean value) {
            this.mSkipConnectingProfiles = value;
            return this;
        }

        /**
         * Set whether to ignore Uuid timeout after bonded.
         */
        public Builder setIgnoreUuidTimeoutAfterBonded(boolean value) {
            this.mIgnoreUuidTimeoutAfterBonded = value;
            return this;
        }

        /**
         * Set whether to include transport type in create bound request.
         */
        public Builder setSpecifyCreateBondTransportType(boolean value) {
            this.mSpecifyCreateBondTransportType = value;
            return this;
        }

        /**
         * Set transport type used in create bond request.
         */
        public Builder setCreateBondTransportType(int value) {
            this.mCreateBondTransportType = value;
            return this;
        }

        /**
         * Set whether to increase intent filter priority.
         */
        public Builder setIncreaseIntentFilterPriority(boolean value) {
            this.mIncreaseIntentFilterPriority = value;
            return this;
        }

        /**
         * Set whether to evaluate performance.
         */
        public Builder setEvaluatePerformance(boolean value) {
            this.mEvaluatePerformance = value;
            return this;
        }

        /**
         * Set extra logging info.
         */
        public Builder setExtraLoggingInformation(ExtraLoggingInformation value) {
            this.mExtraLoggingInformation = value;
            return this;
        }

        /**
         * Set whether to enable naming characteristic.
         */
        public Builder setEnableNamingCharacteristic(boolean value) {
            this.mEnableNamingCharacteristic = value;
            return this;
        }

        /**
         * Set whether to keep writing the account key to the provider, that has already paired with
         * the account.
         */
        public Builder setKeepSameAccountKeyWrite(boolean value) {
            this.mKeepSameAccountKeyWrite = value;
            return this;
        }

        /**
         * Set whether to enable firmware version characteristic.
         */
        public Builder setEnableFirmwareVersionCharacteristic(boolean value) {
            this.mEnableFirmwareVersionCharacteristic = value;
            return this;
        }

        /**
         * Set whether it is retroactive pairing.
         */
        public Builder setIsRetroactivePairing(boolean value) {
            this.mIsRetroactivePairing = value;
            return this;
        }

        /**
         * Set number of allowed sdp attempts after bonded.
         */
        public Builder setNumSdpAttemptsAfterBonded(int value) {
            this.mNumSdpAttemptsAfterBonded = value;
            return this;
        }

        /**
         * Set whether to support Hid device.
         */
        public Builder setSupportHidDevice(boolean value) {
            this.mSupportHidDevice = value;
            return this;
        }

        /**
         * Set wehther to enable the pairing behavior to handle the state transition from
         * BOND_BONDED to BOND_BONDING when directly connecting profiles.
         */
        public Builder setEnablePairingWhileDirectlyConnecting(boolean value) {
            this.mEnablePairingWhileDirectlyConnecting = value;
            return this;
        }

        /**
         * Set whether to accept consent for fast pair one.
         */
        public Builder setAcceptConsentForFastPairOne(boolean value) {
            this.mAcceptConsentForFastPairOne = value;
            return this;
        }

        /**
         * Set Gatt connect retry timeout.
         */
        public Builder setGattConnectRetryTimeoutMillis(int value) {
            this.mGattConnectRetryTimeoutMillis = value;
            return this;
        }

        /**
         * Set whether to enable 128 bit custom gatt characteristic Id.
         */
        public Builder setEnable128BitCustomGattCharacteristicsId(boolean value) {
            this.mEnable128BitCustomGattCharacteristicsId = value;
            return this;
        }

        /**
         * Set whether to send exception step to validator.
         */
        public Builder setEnableSendExceptionStepToValidator(boolean value) {
            this.mEnableSendExceptionStepToValidator = value;
            return this;
        }

        /**
         * Set wehther to add the additional data type in the handshake when action over BLE.
         */
        public Builder setEnableAdditionalDataTypeWhenActionOverBle(boolean value) {
            this.mEnableAdditionalDataTypeWhenActionOverBle = value;
            return this;
        }

        /**
         * Set whether to check bond state when skip connecting profiles.
         */
        public Builder setCheckBondStateWhenSkipConnectingProfiles(boolean value) {
            this.mCheckBondStateWhenSkipConnectingProfiles = value;
            return this;
        }

        /**
         * Set whether to handle passkey confirmation by UI.
         */
        public Builder setHandlePasskeyConfirmationByUi(boolean value) {
            this.mHandlePasskeyConfirmationByUi = value;
            return this;
        }

        /**
         * Set wehther to retry gatt connection and secret handshake.
         */
        public Builder setRetryGattConnectionAndSecretHandshake(boolean value) {
            this.mRetryGattConnectionAndSecretHandshake = value;
            return this;
        }

        /**
         * Set gatt connect short timeout.
         */
        public Builder setGattConnectShortTimeoutMs(long value) {
            this.mGattConnectShortTimeoutMs = value;
            return this;
        }

        /**
         * Set gatt connect long timeout.
         */
        public Builder setGattConnectLongTimeoutMs(long value) {
            this.mGattConnectLongTimeoutMs = value;
            return this;
        }

        /**
         * Set gatt connection short timoutout, including retry.
         */
        public Builder setGattConnectShortTimeoutRetryMaxSpentTimeMs(long value) {
            this.mGattConnectShortTimeoutRetryMaxSpentTimeMs = value;
            return this;
        }

        /**
         * Set address rotate timeout, including retry.
         */
        public Builder setAddressRotateRetryMaxSpentTimeMs(long value) {
            this.mAddressRotateRetryMaxSpentTimeMs = value;
            return this;
        }

        /**
         * Set pairing retry delay time.
         */
        public Builder setPairingRetryDelayMs(long value) {
            this.mPairingRetryDelayMs = value;
            return this;
        }

        /**
         * Set secret handshake short timeout.
         */
        public Builder setSecretHandshakeShortTimeoutMs(long value) {
            this.mSecretHandshakeShortTimeoutMs = value;
            return this;
        }

        /**
         * Set secret handshake long timeout.
         */
        public Builder setSecretHandshakeLongTimeoutMs(long value) {
            this.mSecretHandshakeLongTimeoutMs = value;
            return this;
        }

        /**
         * Set secret handshake short timeout retry max spent time.
         */
        public Builder setSecretHandshakeShortTimeoutRetryMaxSpentTimeMs(long value) {
            this.mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs = value;
            return this;
        }

        /**
         * Set secret handshake long timeout retry max spent time.
         */
        public Builder setSecretHandshakeLongTimeoutRetryMaxSpentTimeMs(long value) {
            this.mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs = value;
            return this;
        }

        /**
         * Set secret handshake retry attempts allowed.
         */
        public Builder setSecretHandshakeRetryAttempts(long value) {
            this.mSecretHandshakeRetryAttempts = value;
            return this;
        }

        /**
         * Set secret handshake retry gatt connection max spent time.
         */
        public Builder setSecretHandshakeRetryGattConnectionMaxSpentTimeMs(long value) {
            this.mSecretHandshakeRetryGattConnectionMaxSpentTimeMs = value;
            return this;
        }

        /**
         * Set signal loss retry max spent time.
         */
        public Builder setSignalLostRetryMaxSpentTimeMs(long value) {
            this.mSignalLostRetryMaxSpentTimeMs = value;
            return this;
        }

        /**
         * Set gatt connection and secret handshake no retry gatt error.
         */
        public Builder setGattConnectionAndSecretHandshakeNoRetryGattError(
                ImmutableSet<Integer> value) {
            this.mGattConnectionAndSecretHandshakeNoRetryGattError = value;
            return this;
        }

        /**
         * Set retry secret handshake timeout.
         */
        public Builder setRetrySecretHandshakeTimeout(boolean value) {
            this.mRetrySecretHandshakeTimeout = value;
            return this;
        }

        /**
         * Set whether to log user manual retry.
         */
        public Builder setLogUserManualRetry(boolean value) {
            this.mLogUserManualRetry = value;
            return this;
        }

        /**
         * Set pair falure counts.
         */
        public Builder setPairFailureCounts(int counts) {
            this.mPairFailureCounts = counts;
            return this;
        }

        /**
         * Set whether to use pair flow to show ui when pairing is finished without connecting
         * profile..
         */
        public Builder setEnablePairFlowShowUiWithoutProfileConnection(boolean value) {
            this.mEnablePairFlowShowUiWithoutProfileConnection = value;
            return this;
        }

        /**
         * Set whether to log pairing with cached module Id.
         */
        public Builder setLogPairWithCachedModelId(boolean value) {
            this.mLogPairWithCachedModelId = value;
            return this;
        }

        /**
         * Set possible cached device address.
         */
        public Builder setPossibleCachedDeviceAddress(String value) {
            this.mPossibleCachedDeviceAddress = value;
            return this;
        }

        /**
         * Set paired device count from the same module Id.
         */
        public Builder setSameModelIdPairedDeviceCount(int value) {
            this.mSameModelIdPairedDeviceCount = value;
            return this;
        }

        /**
         * Set whether the bonded device address is from cache.
         */
        public Builder setIsDeviceFinishCheckAddressFromCache(boolean value) {
            this.mIsDeviceFinishCheckAddressFromCache = value;
            return this;
        }

        /**
         * Set whether to directly connect profile if modelId is in cache.
         */
        public Builder setDirectConnectProfileIfModelIdInCache(boolean value) {
            this.mDirectConnectProfileIfModelIdInCache = value;
            return this;
        }

        /**
         * Set cached device address.
         */
        public Builder setCachedDeviceAddress(String value) {
            this.mCachedDeviceAddress = value;
            return this;
        }

        /**
         * Builds a Preferences instance.
         */
        public Preferences build() {
            return new Preferences(
                    this.mGattOperationTimeoutSeconds,
                    this.mGattConnectionTimeoutSeconds,
                    this.mBluetoothToggleTimeoutSeconds,
                    this.mBluetoothToggleSleepSeconds,
                    this.mClassicDiscoveryTimeoutSeconds,
                    this.mNumDiscoverAttempts,
                    this.mDiscoveryRetrySleepSeconds,
                    this.mIgnoreDiscoveryError,
                    this.mSdpTimeoutSeconds,
                    this.mNumSdpAttempts,
                    this.mNumCreateBondAttempts,
                    this.mNumConnectAttempts,
                    this.mNumWriteAccountKeyAttempts,
                    this.mToggleBluetoothOnFailure,
                    this.mBluetoothStateUsesPolling,
                    this.mBluetoothStatePollingMillis,
                    this.mNumAttempts,
                    this.mEnableBrEdrHandover,
                    this.mBrHandoverDataCharacteristicId,
                    this.mBluetoothSigDataCharacteristicId,
                    this.mFirmwareVersionCharacteristicId,
                    this.mBrTransportBlockDataDescriptorId,
                    this.mWaitForUuidsAfterBonding,
                    this.mReceiveUuidsAndBondedEventBeforeClose,
                    this.mRemoveBondTimeoutSeconds,
                    this.mRemoveBondSleepMillis,
                    this.mCreateBondTimeoutSeconds,
                    this.mHidCreateBondTimeoutSeconds,
                    this.mProxyTimeoutSeconds,
                    this.mRejectPhonebookAccess,
                    this.mRejectMessageAccess,
                    this.mRejectSimAccess,
                    this.mWriteAccountKeySleepMillis,
                    this.mSkipDisconnectingGattBeforeWritingAccountKey,
                    this.mMoreEventLogForQuality,
                    this.mRetryGattConnectionAndSecretHandshake,
                    this.mGattConnectShortTimeoutMs,
                    this.mGattConnectLongTimeoutMs,
                    this.mGattConnectShortTimeoutRetryMaxSpentTimeMs,
                    this.mAddressRotateRetryMaxSpentTimeMs,
                    this.mPairingRetryDelayMs,
                    this.mSecretHandshakeShortTimeoutMs,
                    this.mSecretHandshakeLongTimeoutMs,
                    this.mSecretHandshakeShortTimeoutRetryMaxSpentTimeMs,
                    this.mSecretHandshakeLongTimeoutRetryMaxSpentTimeMs,
                    this.mSecretHandshakeRetryAttempts,
                    this.mSecretHandshakeRetryGattConnectionMaxSpentTimeMs,
                    this.mSignalLostRetryMaxSpentTimeMs,
                    this.mGattConnectionAndSecretHandshakeNoRetryGattError,
                    this.mRetrySecretHandshakeTimeout,
                    this.mLogUserManualRetry,
                    this.mPairFailureCounts,
                    this.mCachedDeviceAddress,
                    this.mPossibleCachedDeviceAddress,
                    this.mSameModelIdPairedDeviceCount,
                    this.mIsDeviceFinishCheckAddressFromCache,
                    this.mLogPairWithCachedModelId,
                    this.mDirectConnectProfileIfModelIdInCache,
                    this.mAcceptPasskey,
                    this.mSupportedProfileUuids,
                    this.mProviderInitiatesBondingIfSupported,
                    this.mAttemptDirectConnectionWhenPreviouslyBonded,
                    this.mAutomaticallyReconnectGattWhenNeeded,
                    this.mSkipConnectingProfiles,
                    this.mIgnoreUuidTimeoutAfterBonded,
                    this.mSpecifyCreateBondTransportType,
                    this.mCreateBondTransportType,
                    this.mIncreaseIntentFilterPriority,
                    this.mEvaluatePerformance,
                    this.mExtraLoggingInformation,
                    this.mEnableNamingCharacteristic,
                    this.mEnableFirmwareVersionCharacteristic,
                    this.mKeepSameAccountKeyWrite,
                    this.mIsRetroactivePairing,
                    this.mNumSdpAttemptsAfterBonded,
                    this.mSupportHidDevice,
                    this.mEnablePairingWhileDirectlyConnecting,
                    this.mAcceptConsentForFastPairOne,
                    this.mGattConnectRetryTimeoutMillis,
                    this.mEnable128BitCustomGattCharacteristicsId,
                    this.mEnableSendExceptionStepToValidator,
                    this.mEnableAdditionalDataTypeWhenActionOverBle,
                    this.mCheckBondStateWhenSkipConnectingProfiles,
                    this.mHandlePasskeyConfirmationByUi,
                    this.mEnablePairFlowShowUiWithoutProfileConnection);
        }
    }

    /**
     * Whether a given Uuid is supported.
     */
    public boolean isSupportedProfile(short profileUuid) {
        return Constants.PROFILES.containsKey(profileUuid)
                && Shorts.contains(
                Bytes.toShorts(ByteOrder.BIG_ENDIAN, getSupportedProfileUuids()), profileUuid);
    }

    /**
     * Information that will be used for logging.
     */
    public static class ExtraLoggingInformation {

        private final String mModelId;

        private ExtraLoggingInformation(String modelId) {
            this.mModelId = modelId;
        }

        /**
         * Returns model Id.
         */
        public String getModelId() {
            return mModelId;
        }

        /**
         * Converts an instance to a builder.
         */
        public Builder toBuilder() {
            return new Builder(this);
        }

        /**
         * Creates a builder for ExtraLoggingInformation.
         */
        public static Builder builder() {
            return new ExtraLoggingInformation.Builder();
        }

        @Override
        public String toString() {
            return "ExtraLoggingInformation{" + "modelId=" + mModelId + "}";
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof ExtraLoggingInformation) {
                Preferences.ExtraLoggingInformation that = (Preferences.ExtraLoggingInformation) o;
                return this.mModelId.equals(that.getModelId());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mModelId);
        }

        /**
         * Extra logging information builder.
         */
        public static class Builder {

            private String mModelId;

            private Builder() {
            }

            private Builder(ExtraLoggingInformation source) {
                this.mModelId = source.getModelId();
            }

            /**
             * Set model ID.
             */
            public Builder setModelId(String modelId) {
                this.mModelId = modelId;
                return this;
            }

            /**
             * Builds extra logging information.
             */
            public ExtraLoggingInformation build() {
                return new ExtraLoggingInformation(mModelId);
            }
        }
    }
}
