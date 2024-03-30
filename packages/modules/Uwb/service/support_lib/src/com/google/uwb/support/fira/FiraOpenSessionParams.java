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

package com.google.uwb.support.fira;

import static com.android.internal.util.Preconditions.checkArgument;
import static com.android.internal.util.Preconditions.checkNotNull;

import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED;

import static java.util.Objects.requireNonNull;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.uwb.support.base.RequiredParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UWB parameters used to open a FiRa session.
 *
 * <p>This is passed as a bundle to the service API {@link UwbManager#openRangingSession}.
 */
public class FiraOpenSessionParams extends FiraParams {
    private final FiraProtocolVersion mProtocolVersion;

    private final int mSessionId;
    @RangingDeviceType private final int mDeviceType;
    @RangingDeviceRole private final int mDeviceRole;
    @RangingRoundUsage private final int mRangingRoundUsage;
    @MultiNodeMode private final int mMultiNodeMode;

    private final UwbAddress mDeviceAddress;

    // Dest address list
    private final List<UwbAddress> mDestAddressList;

    private final int mInitiationTimeMs;
    private final int mSlotDurationRstu;
    private final int mSlotsPerRangingRound;
    private final int mRangingIntervalMs;
    private final int mBlockStrideLength;
    private final int mHoppingMode;

    @IntRange(from = 0, to = 65535)
    private final int mMaxRangingRoundRetries;

    private final int mSessionPriority;
    @MacAddressMode final int mMacAddressMode;
    private final boolean mHasResultReportPhase;
    @MeasurementReportType private final int mMeasurementReportType;

    @IntRange(from = 1, to = 10)
    private final int mInBandTerminationAttemptCount;

    @UwbChannel private final int mChannelNumber;
    private final int mPreambleCodeIndex;
    @RframeConfig private final int mRframeConfig;
    @PrfMode private final int mPrfMode;
    @PreambleDuration private final int mPreambleDuration;
    @SfdIdValue private final int mSfdId;
    @StsSegmentCountValue private final int mStsSegmentCount;
    @StsLength private final int mStsLength;
    @PsduDataRate private final int mPsduDataRate;
    @BprfPhrDataRate private final int mBprfPhrDataRate;
    @MacFcsType private final int mFcsType;
    private final boolean mIsTxAdaptivePayloadPowerEnabled;
    @StsConfig private final int mStsConfig;
    private final int mSubSessionId;
    @AoaType private final int mAoaType;

    // 2-byte long array
    @Nullable private final byte[] mVendorId;

    // 6-byte long array
    @Nullable private final byte[] mStaticStsIV;

    private final boolean mIsKeyRotationEnabled;
    private final int mKeyRotationRate;
    @AoaResultRequestMode private final int mAoaResultRequest;
    @RangeDataNtfConfig private final int mRangeDataNtfConfig;
    private final int mRangeDataNtfProximityNear;
    private final int mRangeDataNtfProximityFar;
    private final boolean mHasTimeOfFlightReport;
    private final boolean mHasAngleOfArrivalAzimuthReport;
    private final boolean mHasAngleOfArrivalElevationReport;
    private final boolean mHasAngleOfArrivalFigureOfMeritReport;
    private final int mNumOfMsrmtFocusOnRange;
    private final int mNumOfMsrmtFocusOnAoaAzimuth;
    private final int mNumOfMsrmtFocusOnAoaElevation;

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_PROTOCOL_VERSION = "protocol_version";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_DEVICE_TYPE = "device_type";
    private static final String KEY_DEVICE_ROLE = "device_role";
    private static final String KEY_RANGING_ROUND_USAGE = "ranging_round_usage";
    private static final String KEY_MULTI_NODE_MODE = "multi_node_mode";
    private static final String KEY_DEVICE_ADDRESS = "device_address";
    private static final String KEY_DEST_ADDRESS_LIST = "dest_address_list";
    private static final String KEY_INITIATION_TIME_MS = "initiation_time_ms";
    private static final String KEY_SLOT_DURATION_RSTU = "slot_duration_rstu";
    private static final String KEY_SLOTS_PER_RANGING_ROUND = "slots_per_ranging_round";
    private static final String KEY_RANGING_INTERVAL_MS = "ranging_interval_ms";
    private static final String KEY_BLOCK_STRIDE_LENGTH = "block_stride_length";
    private static final String KEY_HOPPING_MODE = "hopping_mode";
    private static final String KEY_MAX_RANGING_ROUND_RETRIES = "max_ranging_round_retries";
    private static final String KEY_SESSION_PRIORITY = "session_priority";
    private static final String KEY_MAC_ADDRESS_MODE = "mac_address_mode";
    private static final String KEY_IN_BAND_TERMINATION_ATTEMPT_COUNT =
            "in_band_termination_attempt_count";
    private static final String KEY_CHANNEL_NUMBER = "channel_number";
    private static final String KEY_PREAMBLE_CODE_INDEX = "preamble_code_index";
    private static final String KEY_RFRAME_CONFIG = "rframe_config";
    private static final String KEY_PRF_MODE = "prf_mode";
    private static final String KEY_PREAMBLE_DURATION = "preamble_duration";
    private static final String KEY_SFD_ID = "sfd_id";
    private static final String KEY_STS_SEGMENT_COUNT = "sts_segment_count";
    private static final String KEY_STS_LENGTH = "sts_length";
    private static final String KEY_PSDU_DATA_RATE = "psdu_data_rate";
    private static final String KEY_BPRF_PHR_DATA_RATE = "bprf_phr_data_rate";
    private static final String KEY_FCS_TYPE = "fcs_type";
    private static final String KEY_IS_TX_ADAPTIVE_PAYLOAD_POWER_ENABLED =
            "is_tx_adaptive_payload_power_enabled";
    private static final String KEY_STS_CONFIG = "sts_config";
    private static final String KEY_SUB_SESSION_ID = "sub_session_id";
    private static final String KEY_VENDOR_ID = "vendor_id";
    private static final String KEY_STATIC_STS_IV = "static_sts_iv";
    private static final String KEY_IS_KEY_ROTATION_ENABLED = "is_key_rotation_enabled";
    private static final String KEY_KEY_ROTATION_RATE = "key_rotation_rate";
    private static final String KEY_AOA_RESULT_REQUEST = "aoa_result_request";
    private static final String KEY_RANGE_DATA_NTF_CONFIG = "range_data_ntf_config";
    private static final String KEY_RANGE_DATA_NTF_PROXIMITY_NEAR = "range_data_ntf_proximity_near";
    private static final String KEY_RANGE_DATA_NTF_PROXIMITY_FAR = "range_data_ntf_proximity_far";
    private static final String KEY_HAS_TIME_OF_FLIGHT_REPORT = "has_time_of_flight_report";
    private static final String KEY_HAS_ANGLE_OF_ARRIVAL_AZIMUTH_REPORT =
            "has_angle_of_arrival_azimuth_report";
    private static final String KEY_HAS_ANGLE_OF_ARRIVAL_ELEVATION_REPORT =
            "has_angle_of_arrival_elevation_report";
    private static final String KEY_HAS_ANGLE_OF_ARRIVAL_FIGURE_OF_MERIT_REPORT =
            "has_angle_of_arrival_figure_of_merit_report";
    private static final String KEY_HAS_RESULT_REPORT_PHASE = "has_result_report_phase";
    private static final String KEY_MEASUREMENT_REPORT_TYPE = "measurement_report_type";
    private static final String KEY_AOA_TYPE = "aoa_type";
    private static final String KEY_NUM_OF_MSRMT_FOCUS_ON_RANGE =
            "num_of_msrmt_focus_on_range";
    private static final String KEY_NUM_OF_MSRMT_FOCUS_ON_AOA_AZIMUTH =
            "num_of_msrmt_focus_on_aoa_azimuth";
    private static final String KEY_NUM_OF_MSRMT_FOCUS_ON_AOA_ELEVATION =
            "num_of_msrmt_focus_on_aoa_elevation";

    private FiraOpenSessionParams(
            FiraProtocolVersion protocolVersion,
            int sessionId,
            @RangingDeviceType int deviceType,
            @RangingDeviceRole int deviceRole,
            @RangingRoundUsage int rangingRoundUsage,
            @MultiNodeMode int multiNodeMode,
            UwbAddress deviceAddress,
            List<UwbAddress> destAddressList,
            int initiationTimeMs,
            int slotDurationRstu,
            int slotsPerRangingRound,
            int rangingIntervalMs,
            int blockStrideLength,
            int hoppingMode,
            @IntRange(from = 0, to = 65535) int maxRangingRoundRetries,
            int sessionPriority,
            @MacAddressMode int macAddressMode,
            boolean hasResultReportPhase,
            @MeasurementReportType int measurementReportType,
            @IntRange(from = 1, to = 10) int inBandTerminationAttemptCount,
            @UwbChannel int channelNumber,
            int preambleCodeIndex,
            @RframeConfig int rframeConfig,
            @PrfMode int prfMode,
            @PreambleDuration int preambleDuration,
            @SfdIdValue int sfdId,
            @StsSegmentCountValue int stsSegmentCount,
            @StsLength int stsLength,
            @PsduDataRate int psduDataRate,
            @BprfPhrDataRate int bprfPhrDataRate,
            @MacFcsType int fcsType,
            boolean isTxAdaptivePayloadPowerEnabled,
            @StsConfig int stsConfig,
            int subSessionId,
            @Nullable byte[] vendorId,
            @Nullable byte[] staticStsIV,
            boolean isKeyRotationEnabled,
            int keyRotationRate,
            @AoaResultRequestMode int aoaResultRequest,
            @RangeDataNtfConfig int rangeDataNtfConfig,
            int rangeDataNtfProximityNear,
            int rangeDataNtfProximityFar,
            boolean hasTimeOfFlightReport,
            boolean hasAngleOfArrivalAzimuthReport,
            boolean hasAngleOfArrivalElevationReport,
            boolean hasAngleOfArrivalFigureOfMeritReport,
            @AoaType int aoaType,
            int numOfMsrmtFocusOnRange,
            int numOfMsrmtFocusOnAoaAzimuth,
            int numOfMsrmtFocusOnAoaElevation) {
        mProtocolVersion = protocolVersion;
        mSessionId = sessionId;
        mDeviceType = deviceType;
        mDeviceRole = deviceRole;
        mRangingRoundUsage = rangingRoundUsage;
        mMultiNodeMode = multiNodeMode;
        mDeviceAddress = deviceAddress;
        mDestAddressList = destAddressList;
        mInitiationTimeMs = initiationTimeMs;
        mSlotDurationRstu = slotDurationRstu;
        mSlotsPerRangingRound = slotsPerRangingRound;
        mRangingIntervalMs = rangingIntervalMs;
        mBlockStrideLength = blockStrideLength;
        mHoppingMode = hoppingMode;
        mMaxRangingRoundRetries = maxRangingRoundRetries;
        mSessionPriority = sessionPriority;
        mMacAddressMode = macAddressMode;
        mHasResultReportPhase = hasResultReportPhase;
        mMeasurementReportType = measurementReportType;
        mInBandTerminationAttemptCount = inBandTerminationAttemptCount;
        mChannelNumber = channelNumber;
        mPreambleCodeIndex = preambleCodeIndex;
        mRframeConfig = rframeConfig;
        mPrfMode = prfMode;
        mPreambleDuration = preambleDuration;
        mSfdId = sfdId;
        mStsSegmentCount = stsSegmentCount;
        mStsLength = stsLength;
        mPsduDataRate = psduDataRate;
        mBprfPhrDataRate = bprfPhrDataRate;
        mFcsType = fcsType;
        mIsTxAdaptivePayloadPowerEnabled = isTxAdaptivePayloadPowerEnabled;
        mStsConfig = stsConfig;
        mSubSessionId = subSessionId;
        mVendorId = vendorId;
        mStaticStsIV = staticStsIV;
        mIsKeyRotationEnabled = isKeyRotationEnabled;
        mKeyRotationRate = keyRotationRate;
        mAoaResultRequest = aoaResultRequest;
        mRangeDataNtfConfig = rangeDataNtfConfig;
        mRangeDataNtfProximityNear = rangeDataNtfProximityNear;
        mRangeDataNtfProximityFar = rangeDataNtfProximityFar;
        mHasTimeOfFlightReport = hasTimeOfFlightReport;
        mHasAngleOfArrivalAzimuthReport = hasAngleOfArrivalAzimuthReport;
        mHasAngleOfArrivalElevationReport = hasAngleOfArrivalElevationReport;
        mHasAngleOfArrivalFigureOfMeritReport = hasAngleOfArrivalFigureOfMeritReport;
        mAoaType = aoaType;
        mNumOfMsrmtFocusOnRange = numOfMsrmtFocusOnRange;
        mNumOfMsrmtFocusOnAoaAzimuth = numOfMsrmtFocusOnAoaAzimuth;
        mNumOfMsrmtFocusOnAoaElevation = numOfMsrmtFocusOnAoaElevation;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public int getSessionId() {
        return mSessionId;
    }

    @RangingDeviceType
    public int getDeviceType() {
        return mDeviceType;
    }

    @RangingDeviceRole
    public int getDeviceRole() {
        return mDeviceRole;
    }

    @RangingRoundUsage
    public int getRangingRoundUsage() {
        return mRangingRoundUsage;
    }

    @MultiNodeMode
    public int getMultiNodeMode() {
        return mMultiNodeMode;
    }

    public UwbAddress getDeviceAddress() {
        return mDeviceAddress;
    }

    public List<UwbAddress> getDestAddressList() {
        return Collections.unmodifiableList(mDestAddressList);
    }

    public int getInitiationTimeMs() {
        return mInitiationTimeMs;
    }

    public int getSlotDurationRstu() {
        return mSlotDurationRstu;
    }

    public int getSlotsPerRangingRound() {
        return mSlotsPerRangingRound;
    }

    public int getRangingIntervalMs() {
        return mRangingIntervalMs;
    }

    public int getBlockStrideLength() {
        return mBlockStrideLength;
    }

    public int getHoppingMode() {
        return mHoppingMode;
    }

    @IntRange(from = 0, to = 65535)
    public int getMaxRangingRoundRetries() {
        return mMaxRangingRoundRetries;
    }

    public int getSessionPriority() {
        return mSessionPriority;
    }

    @MacAddressMode
    public int getMacAddressMode() {
        return mMacAddressMode;
    }

    public boolean hasResultReportPhase() {
        return mHasResultReportPhase;
    }

    @MeasurementReportType
    public int getMeasurementReportType() {
        return mMeasurementReportType;
    }

    @IntRange(from = 1, to = 10)
    public int getInBandTerminationAttemptCount() {
        return mInBandTerminationAttemptCount;
    }

    @UwbChannel
    public int getChannelNumber() {
        return mChannelNumber;
    }

    public int getPreambleCodeIndex() {
        return mPreambleCodeIndex;
    }

    @RframeConfig
    public int getRframeConfig() {
        return mRframeConfig;
    }

    @PrfMode
    public int getPrfMode() {
        return mPrfMode;
    }

    @PreambleDuration
    public int getPreambleDuration() {
        return mPreambleDuration;
    }

    @SfdIdValue
    public int getSfdId() {
        return mSfdId;
    }

    @StsSegmentCountValue
    public int getStsSegmentCount() {
        return mStsSegmentCount;
    }

    @StsLength
    public int getStsLength() {
        return mStsLength;
    }

    @PsduDataRate
    public int getPsduDataRate() {
        return mPsduDataRate;
    }

    @BprfPhrDataRate
    public int getBprfPhrDataRate() {
        return mBprfPhrDataRate;
    }

    @MacFcsType
    public int getFcsType() {
        return mFcsType;
    }

    public boolean isTxAdaptivePayloadPowerEnabled() {
        return mIsTxAdaptivePayloadPowerEnabled;
    }

    @StsConfig
    public int getStsConfig() {
        return mStsConfig;
    }

    public int getSubSessionId() {
        return mSubSessionId;
    }

    @Nullable
    public byte[] getVendorId() {
        return mVendorId;
    }

    @Nullable
    public byte[] getStaticStsIV() {
        return mStaticStsIV;
    }

    public boolean isKeyRotationEnabled() {
        return mIsKeyRotationEnabled;
    }

    public int getKeyRotationRate() {
        return mKeyRotationRate;
    }

    @AoaResultRequestMode
    public int getAoaResultRequest() {
        return mAoaResultRequest;
    }

    @RangeDataNtfConfig
    public int getRangeDataNtfConfig() {
        return mRangeDataNtfConfig;
    }

    public int getRangeDataNtfProximityNear() {
        return mRangeDataNtfProximityNear;
    }

    public int getRangeDataNtfProximityFar() {
        return mRangeDataNtfProximityFar;
    }

    public boolean hasTimeOfFlightReport() {
        return mHasTimeOfFlightReport;
    }

    public boolean hasAngleOfArrivalAzimuthReport() {
        return mHasAngleOfArrivalAzimuthReport;
    }

    public boolean hasAngleOfArrivalElevationReport() {
        return mHasAngleOfArrivalElevationReport;
    }

    public boolean hasAngleOfArrivalFigureOfMeritReport() {
        return mHasAngleOfArrivalFigureOfMeritReport;
    }

    @AoaType
    public int getAoaType() {
        return mAoaType;
    }

    public int getNumOfMsrmtFocusOnRange() {
        return mNumOfMsrmtFocusOnRange;
    }

    public int getNumOfMsrmtFocusOnAoaAzimuth() {
        return mNumOfMsrmtFocusOnAoaAzimuth;
    }

    public int getNumOfMsrmtFocusOnAoaElevation() {
        return mNumOfMsrmtFocusOnAoaElevation;
    }

    @Nullable
    private static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = bytes[i];
        }
        return values;
    }

    @Nullable
    private static byte[] intArrayToByteArray(@Nullable int[] values) {
        if (values == null) {
            return null;
        }
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putString(KEY_PROTOCOL_VERSION, mProtocolVersion.toString());
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_DEVICE_TYPE, mDeviceType);
        bundle.putInt(KEY_DEVICE_ROLE, mDeviceRole);
        bundle.putInt(KEY_RANGING_ROUND_USAGE, mRangingRoundUsage);
        bundle.putInt(KEY_MULTI_NODE_MODE, mMultiNodeMode);
        // Always store address as long in bundle.
        bundle.putLong(KEY_DEVICE_ADDRESS, uwbAddressToLong(mDeviceAddress));

        // Dest Address list needs to be converted to long array.
        long[] destAddressList = new long[mDestAddressList.size()];
        int i = 0;
        for (UwbAddress destAddress : mDestAddressList) {
            destAddressList[i++] = uwbAddressToLong(destAddress);
        }
        bundle.putLongArray(KEY_DEST_ADDRESS_LIST, destAddressList);

        bundle.putInt(KEY_INITIATION_TIME_MS, mInitiationTimeMs);
        bundle.putInt(KEY_SLOT_DURATION_RSTU, mSlotDurationRstu);
        bundle.putInt(KEY_SLOTS_PER_RANGING_ROUND, mSlotsPerRangingRound);
        bundle.putInt(KEY_RANGING_INTERVAL_MS, mRangingIntervalMs);
        bundle.putInt(KEY_BLOCK_STRIDE_LENGTH, mBlockStrideLength);
        bundle.putInt(KEY_HOPPING_MODE, mHoppingMode);
        bundle.putInt(KEY_MAX_RANGING_ROUND_RETRIES, mMaxRangingRoundRetries);
        bundle.putInt(KEY_SESSION_PRIORITY, mSessionPriority);
        bundle.putInt(KEY_MAC_ADDRESS_MODE, mMacAddressMode);
        bundle.putBoolean(KEY_HAS_RESULT_REPORT_PHASE, mHasResultReportPhase);
        bundle.putInt(KEY_MEASUREMENT_REPORT_TYPE, mMeasurementReportType);
        bundle.putInt(KEY_IN_BAND_TERMINATION_ATTEMPT_COUNT, mInBandTerminationAttemptCount);
        bundle.putInt(KEY_CHANNEL_NUMBER, mChannelNumber);
        bundle.putInt(KEY_PREAMBLE_CODE_INDEX, mPreambleCodeIndex);
        bundle.putInt(KEY_RFRAME_CONFIG, mRframeConfig);
        bundle.putInt(KEY_PRF_MODE, mPrfMode);
        bundle.putInt(KEY_PREAMBLE_DURATION, mPreambleDuration);
        bundle.putInt(KEY_SFD_ID, mSfdId);
        bundle.putInt(KEY_STS_SEGMENT_COUNT, mStsSegmentCount);
        bundle.putInt(KEY_STS_LENGTH, mStsLength);
        bundle.putInt(KEY_PSDU_DATA_RATE, mPsduDataRate);
        bundle.putInt(KEY_BPRF_PHR_DATA_RATE, mBprfPhrDataRate);
        bundle.putInt(KEY_FCS_TYPE, mFcsType);
        bundle.putBoolean(
                KEY_IS_TX_ADAPTIVE_PAYLOAD_POWER_ENABLED, mIsTxAdaptivePayloadPowerEnabled);
        bundle.putInt(KEY_STS_CONFIG, mStsConfig);
        if (mStsConfig == STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY) {
            bundle.putInt(KEY_SUB_SESSION_ID, mSubSessionId);
        }
        bundle.putIntArray(KEY_VENDOR_ID, byteArrayToIntArray(mVendorId));
        bundle.putIntArray(KEY_STATIC_STS_IV, byteArrayToIntArray(mStaticStsIV));
        bundle.putBoolean(KEY_IS_KEY_ROTATION_ENABLED, mIsKeyRotationEnabled);
        bundle.putInt(KEY_KEY_ROTATION_RATE, mKeyRotationRate);
        bundle.putInt(KEY_AOA_RESULT_REQUEST, mAoaResultRequest);
        bundle.putInt(KEY_RANGE_DATA_NTF_CONFIG, mRangeDataNtfConfig);
        bundle.putInt(KEY_RANGE_DATA_NTF_PROXIMITY_NEAR, mRangeDataNtfProximityNear);
        bundle.putInt(KEY_RANGE_DATA_NTF_PROXIMITY_FAR, mRangeDataNtfProximityFar);
        bundle.putBoolean(KEY_HAS_TIME_OF_FLIGHT_REPORT, mHasTimeOfFlightReport);
        bundle.putBoolean(KEY_HAS_ANGLE_OF_ARRIVAL_AZIMUTH_REPORT, mHasAngleOfArrivalAzimuthReport);
        bundle.putBoolean(
                KEY_HAS_ANGLE_OF_ARRIVAL_ELEVATION_REPORT, mHasAngleOfArrivalElevationReport);
        bundle.putBoolean(
                KEY_HAS_ANGLE_OF_ARRIVAL_FIGURE_OF_MERIT_REPORT,
                mHasAngleOfArrivalFigureOfMeritReport);
        bundle.putInt(KEY_AOA_TYPE, mAoaType);
        bundle.putInt(KEY_NUM_OF_MSRMT_FOCUS_ON_RANGE, mNumOfMsrmtFocusOnRange);
        bundle.putInt(KEY_NUM_OF_MSRMT_FOCUS_ON_AOA_AZIMUTH, mNumOfMsrmtFocusOnAoaAzimuth);
        bundle.putInt(KEY_NUM_OF_MSRMT_FOCUS_ON_AOA_ELEVATION, mNumOfMsrmtFocusOnAoaElevation);
        return bundle;
    }

    public static FiraOpenSessionParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseBundleVersion1(bundle);

            default:
                throw new IllegalArgumentException("unknown bundle version");
        }
    }

    private static FiraOpenSessionParams parseBundleVersion1(PersistableBundle bundle) {
        int macAddressMode = bundle.getInt(KEY_MAC_ADDRESS_MODE);
        int addressByteLength = 2;
        if (macAddressMode == MAC_ADDRESS_MODE_8_BYTES) {
            addressByteLength = 8;
        }
        UwbAddress deviceAddress =
                longToUwbAddress(bundle.getLong(KEY_DEVICE_ADDRESS), addressByteLength);

        long[] destAddresses = bundle.getLongArray(KEY_DEST_ADDRESS_LIST);
        List<UwbAddress> destAddressList = new ArrayList<>();
        for (long address : destAddresses) {
            destAddressList.add(longToUwbAddress(address, addressByteLength));
        }

        return new FiraOpenSessionParams.Builder()
                .setProtocolVersion(
                        FiraProtocolVersion.fromString(
                                requireNonNull(bundle.getString(KEY_PROTOCOL_VERSION))))
                .setSessionId(bundle.getInt(KEY_SESSION_ID))
                .setDeviceType(bundle.getInt(KEY_DEVICE_TYPE))
                .setDeviceRole(bundle.getInt(KEY_DEVICE_ROLE))
                .setRangingRoundUsage(bundle.getInt(KEY_RANGING_ROUND_USAGE))
                .setMultiNodeMode(bundle.getInt(KEY_MULTI_NODE_MODE))
                .setDeviceAddress(deviceAddress)
                .setDestAddressList(destAddressList)
                .setInitiationTimeMs(bundle.getInt(KEY_INITIATION_TIME_MS))
                .setSlotDurationRstu(bundle.getInt(KEY_SLOT_DURATION_RSTU))
                .setSlotsPerRangingRound(bundle.getInt(KEY_SLOTS_PER_RANGING_ROUND))
                .setRangingIntervalMs(bundle.getInt(KEY_RANGING_INTERVAL_MS))
                .setBlockStrideLength(bundle.getInt(KEY_BLOCK_STRIDE_LENGTH))
                .setHoppingMode(bundle.getInt(KEY_HOPPING_MODE))
                .setMaxRangingRoundRetries(bundle.getInt(KEY_MAX_RANGING_ROUND_RETRIES))
                .setSessionPriority(bundle.getInt(KEY_SESSION_PRIORITY))
                .setMacAddressMode(bundle.getInt(KEY_MAC_ADDRESS_MODE))
                .setHasResultReportPhase(bundle.getBoolean(KEY_HAS_RESULT_REPORT_PHASE))
                .setMeasurementReportType(bundle.getInt(KEY_MEASUREMENT_REPORT_TYPE))
                .setInBandTerminationAttemptCount(
                        bundle.getInt(KEY_IN_BAND_TERMINATION_ATTEMPT_COUNT))
                .setChannelNumber(bundle.getInt(KEY_CHANNEL_NUMBER))
                .setPreambleCodeIndex(bundle.getInt(KEY_PREAMBLE_CODE_INDEX))
                .setRframeConfig(bundle.getInt(KEY_RFRAME_CONFIG))
                .setPrfMode(bundle.getInt(KEY_PRF_MODE))
                .setPreambleDuration(bundle.getInt(KEY_PREAMBLE_DURATION))
                .setSfdId(bundle.getInt(KEY_SFD_ID))
                .setStsSegmentCount(bundle.getInt(KEY_STS_SEGMENT_COUNT))
                .setStsLength(bundle.getInt(KEY_STS_LENGTH))
                .setPsduDataRate(bundle.getInt(KEY_PSDU_DATA_RATE))
                .setBprfPhrDataRate(bundle.getInt(KEY_BPRF_PHR_DATA_RATE))
                .setFcsType(bundle.getInt(KEY_FCS_TYPE))
                .setIsTxAdaptivePayloadPowerEnabled(
                        bundle.getBoolean(KEY_IS_TX_ADAPTIVE_PAYLOAD_POWER_ENABLED))
                .setStsConfig(bundle.getInt(KEY_STS_CONFIG))
                .setSubSessionId(bundle.getInt(KEY_SUB_SESSION_ID))
                .setVendorId(intArrayToByteArray(bundle.getIntArray(KEY_VENDOR_ID)))
                .setStaticStsIV(intArrayToByteArray(bundle.getIntArray(KEY_STATIC_STS_IV)))
                .setIsKeyRotationEnabled(bundle.getBoolean(KEY_IS_KEY_ROTATION_ENABLED))
                .setKeyRotationRate(bundle.getInt(KEY_KEY_ROTATION_RATE))
                .setAoaResultRequest(bundle.getInt(KEY_AOA_RESULT_REQUEST))
                .setRangeDataNtfConfig(bundle.getInt(KEY_RANGE_DATA_NTF_CONFIG))
                .setRangeDataNtfProximityNear(bundle.getInt(KEY_RANGE_DATA_NTF_PROXIMITY_NEAR))
                .setRangeDataNtfProximityFar(bundle.getInt(KEY_RANGE_DATA_NTF_PROXIMITY_FAR))
                .setHasTimeOfFlightReport(bundle.getBoolean(KEY_HAS_TIME_OF_FLIGHT_REPORT))
                .setHasAngleOfArrivalAzimuthReport(
                        bundle.getBoolean(KEY_HAS_ANGLE_OF_ARRIVAL_AZIMUTH_REPORT))
                .setHasAngleOfArrivalElevationReport(
                        bundle.getBoolean(KEY_HAS_ANGLE_OF_ARRIVAL_ELEVATION_REPORT))
                .setHasAngleOfArrivalFigureOfMeritReport(
                        bundle.getBoolean(KEY_HAS_ANGLE_OF_ARRIVAL_FIGURE_OF_MERIT_REPORT))
                .setAoaType(bundle.getInt(KEY_AOA_TYPE))
                .setMeasurementFocusRatio(
                        bundle.getInt(KEY_NUM_OF_MSRMT_FOCUS_ON_RANGE),
                        bundle.getInt(KEY_NUM_OF_MSRMT_FOCUS_ON_AOA_AZIMUTH),
                        bundle.getInt(KEY_NUM_OF_MSRMT_FOCUS_ON_AOA_ELEVATION))
                .build();
    }

    public FiraProtocolVersion getProtocolVersion() {
        return mProtocolVersion;
    }

    /** Builder */
    public static final class Builder {
        private final RequiredParam<FiraProtocolVersion> mProtocolVersion = new RequiredParam<>();

        private final RequiredParam<Integer> mSessionId = new RequiredParam<>();
        private final RequiredParam<Integer> mDeviceType = new RequiredParam<>();
        private final RequiredParam<Integer> mDeviceRole = new RequiredParam<>();

        /** UCI spec default: DS-TWR with deferred mode */
        @RangingRoundUsage
        private int mRangingRoundUsage = RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;

        private final RequiredParam<Integer> mMultiNodeMode = new RequiredParam<>();
        private UwbAddress mDeviceAddress = null;
        private List<UwbAddress> mDestAddressList = null;

        /** UCI spec default: 0ms */
        private int mInitiationTimeMs = 0;

        /** UCI spec default: 2400 RSTU (2 ms). */
        private int mSlotDurationRstu = 2400;

        /** UCI spec default: 30 slots per ranging round. */
        private int mSlotsPerRangingRound = 30;

        /** UCI spec default: RANGING_INTERVAL 200 ms */
        private int mRangingIntervalMs = 200;

        /** UCI spec default: no block striding. */
        private int mBlockStrideLength = 0;

        /** UCI spec default: no hopping. */
        private int mHoppingMode = HOPPING_MODE_DISABLE;

        /** UCI spec default: Termination is disabled and ranging round attempt is infinite */
        @IntRange(from = 0, to = 65535)
        private int mMaxRangingRoundRetries = 0;

        /** UCI spec default: priority 50 */
        private int mSessionPriority = 50;

        /** UCI spec default: 2-byte short address */
        @MacAddressMode private int mMacAddressMode = MAC_ADDRESS_MODE_2_BYTES;

        /** UCI spec default: RANGING_ROUND_CONTROL bit 0 default 1 */
        private boolean mHasResultReportPhase = true;

        /** UCI spec default: RANGING_ROUND_CONTROL bit 7 default 0 */
        @MeasurementReportType
        private int mMeasurementReportType = MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;

        /** UCI spec default: in-band termination signal will be sent once. */
        @IntRange(from = 1, to = 10)
        private int mInBandTerminationAttemptCount = 1;

        /** UCI spec default: Channel 9, which is the only mandatory channel. */
        @UwbChannel private int mChannelNumber = UWB_CHANNEL_9;

        /** UCI spec default: index 10 */
        @UwbPreambleCodeIndex private int mPreambleCodeIndex = UWB_PREAMBLE_CODE_INDEX_10;

        /** UCI spec default: SP3 */
        private int mRframeConfig = RFRAME_CONFIG_SP3;

        /** UCI spec default: BPRF */
        @PrfMode private int mPrfMode = PRF_MODE_BPRF;

        /** UCI spec default: 64 symbols */
        @PreambleDuration private int mPreambleDuration = PREAMBLE_DURATION_T64_SYMBOLS;

        /** UCI spec default: ID 2 */
        @SfdIdValue private int mSfdId = SFD_ID_VALUE_2;

        /** UCI spec default: one STS segment */
        @StsSegmentCountValue private int mStsSegmentCount = STS_SEGMENT_COUNT_VALUE_1;

        /** UCI spec default: 64 symbols */
        @StsLength private int mStsLength = STS_LENGTH_64_SYMBOLS;

        /** UCI spec default: 6.81Mb/s */
        @PsduDataRate private int mPsduDataRate = PSDU_DATA_RATE_6M81;

        /** UCI spec default: 850kb/s */
        @BprfPhrDataRate private int mBprfPhrDataRate = BPRF_PHR_DATA_RATE_850K;

        /** UCI spec default: CRC-16 */
        @MacFcsType private int mFcsType = MAC_FCS_TYPE_CRC_16;

        /** UCI spec default: adaptive payload power for TX disabled */
        private boolean mIsTxAdaptivePayloadPowerEnabled = false;

        /** UCI spec default: static STS */
        @StsConfig private int mStsConfig = STS_CONFIG_STATIC;

        /**
         * Per UCI spec, only required when STS config is
         * STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY.
         */
        private final RequiredParam<Integer> mSubSessionId = new RequiredParam<>();

        /** STATIC STS only. For Key generation. 16-bit long */
        @Nullable private byte[] mVendorId = null;

        /** STATIC STS only. For Key generation. 48-bit long */
        @Nullable private byte[] mStaticStsIV = null;

        /** UCI spec default: no key rotation */
        private boolean mIsKeyRotationEnabled = false;

        /** UCI spec default: 0 */
        private int mKeyRotationRate = 0;

        /** UCI spec default: AoA enabled. */
        @AoaResultRequestMode
        private int mAoaResultRequest = AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;

        /** UCI spec default: Ranging notification enabled. */
        @RangeDataNtfConfig private int mRangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_ENABLE;

        /** UCI spec default: 0 (No low-bound filtering) */
        private int mRangeDataNtfProximityNear = 0;

        /** UCI spec default: 20000 cm (or 200 meters) */
        private int mRangeDataNtfProximityFar = 20000;

        /** UCI spec default: RESULT_REPORT_CONFIG bit 0 is 1 */
        private boolean mHasTimeOfFlightReport = true;

        /** UCI spec default: RESULT_REPORT_CONFIG bit 1 is 0 */
        private boolean mHasAngleOfArrivalAzimuthReport = false;

        /** UCI spec default: RESULT_REPORT_CONFIG bit 2 is 0 */
        private boolean mHasAngleOfArrivalElevationReport = false;

        /** UCI spec default: RESULT_REPORT_CONFIG bit 3 is 0 */
        private boolean mHasAngleOfArrivalFigureOfMeritReport = false;

        /** Not defined in UCI, we use Azimuth-only as default */
        @AoaType private int mAoaType = AOA_TYPE_AZIMUTH;

        /** Interleaving ratios are not set by default */
        private int mNumOfMsrmtFocusOnRange = 0;
        private int mNumOfMsrmtFocusOnAoaAzimuth = 0;
        private int mNumOfMsrmtFocusOnAoaElevation = 0;

        public Builder() {}

        public Builder(@NonNull Builder builder) {
            mProtocolVersion.set(builder.mProtocolVersion.get());
            mSessionId.set(builder.mSessionId.get());
            mDeviceType.set(builder.mDeviceType.get());
            mDeviceRole.set(builder.mDeviceRole.get());
            mRangingRoundUsage = builder.mRangingRoundUsage;
            mMultiNodeMode.set(builder.mMultiNodeMode.get());
            mDeviceAddress = builder.mDeviceAddress;
            mDestAddressList = builder.mDestAddressList;
            mInitiationTimeMs = builder.mInitiationTimeMs;
            mSlotDurationRstu = builder.mSlotDurationRstu;
            mSlotsPerRangingRound = builder.mSlotsPerRangingRound;
            mRangingIntervalMs = builder.mRangingIntervalMs;
            mBlockStrideLength = builder.mBlockStrideLength;
            mHoppingMode = builder.mHoppingMode;
            mMaxRangingRoundRetries = builder.mMaxRangingRoundRetries;
            mSessionPriority = builder.mSessionPriority;
            mMacAddressMode = builder.mMacAddressMode;
            mHasResultReportPhase = builder.mHasResultReportPhase;
            mMeasurementReportType = builder.mMeasurementReportType;
            mInBandTerminationAttemptCount = builder.mInBandTerminationAttemptCount;
            mChannelNumber = builder.mChannelNumber;
            mPreambleCodeIndex = builder.mPreambleCodeIndex;
            mRframeConfig = builder.mRframeConfig;
            mPrfMode = builder.mPrfMode;
            mPreambleDuration = builder.mPreambleDuration;
            mSfdId = builder.mSfdId;
            mStsSegmentCount = builder.mStsSegmentCount;
            mStsLength = builder.mStsLength;
            mPsduDataRate = builder.mPsduDataRate;
            mBprfPhrDataRate = builder.mBprfPhrDataRate;
            mFcsType = builder.mFcsType;
            mIsTxAdaptivePayloadPowerEnabled = builder.mIsTxAdaptivePayloadPowerEnabled;
            mStsConfig = builder.mStsConfig;
            if (builder.mSubSessionId.isSet()) mSubSessionId.set(builder.mSubSessionId.get());
            mVendorId = builder.mVendorId;
            mStaticStsIV = builder.mStaticStsIV;
            mIsKeyRotationEnabled = builder.mIsKeyRotationEnabled;
            mKeyRotationRate = builder.mKeyRotationRate;
            mAoaResultRequest = builder.mAoaResultRequest;
            mRangeDataNtfConfig = builder.mRangeDataNtfConfig;
            mRangeDataNtfProximityNear = builder.mRangeDataNtfProximityNear;
            mRangeDataNtfProximityFar = builder.mRangeDataNtfProximityFar;
            mHasTimeOfFlightReport = builder.mHasTimeOfFlightReport;
            mHasAngleOfArrivalAzimuthReport = builder.mHasAngleOfArrivalAzimuthReport;
            mHasAngleOfArrivalElevationReport = builder.mHasAngleOfArrivalElevationReport;
            mHasAngleOfArrivalFigureOfMeritReport = builder.mHasAngleOfArrivalFigureOfMeritReport;
            mAoaType = builder.mAoaType;
        }

        public FiraOpenSessionParams.Builder setProtocolVersion(FiraProtocolVersion version) {
            mProtocolVersion.set(version);
            return this;
        }

        public FiraOpenSessionParams.Builder setSessionId(int sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public FiraOpenSessionParams.Builder setDeviceType(@RangingDeviceType int deviceType) {
            mDeviceType.set(deviceType);
            return this;
        }

        public FiraOpenSessionParams.Builder setDeviceRole(@RangingDeviceRole int deviceRole) {
            mDeviceRole.set(deviceRole);
            return this;
        }

        public FiraOpenSessionParams.Builder setRangingRoundUsage(
                @RangingRoundUsage int rangingRoundUsage) {
            mRangingRoundUsage = rangingRoundUsage;
            return this;
        }

        public FiraOpenSessionParams.Builder setMultiNodeMode(@MultiNodeMode int multiNodeMode) {
            mMultiNodeMode.set(multiNodeMode);
            return this;
        }

        public FiraOpenSessionParams.Builder setDeviceAddress(UwbAddress deviceAddress) {
            mDeviceAddress = deviceAddress;
            return this;
        }

        public FiraOpenSessionParams.Builder setDestAddressList(List<UwbAddress> destAddressList) {
            mDestAddressList = destAddressList;
            return this;
        }

        public FiraOpenSessionParams.Builder setInitiationTimeMs(int initiationTimeMs) {
            mInitiationTimeMs = initiationTimeMs;
            return this;
        }

        public FiraOpenSessionParams.Builder setSlotDurationRstu(int slotDurationRstu) {
            mSlotDurationRstu = slotDurationRstu;
            return this;
        }

        public FiraOpenSessionParams.Builder setSlotsPerRangingRound(int slotsPerRangingRound) {
            mSlotsPerRangingRound = slotsPerRangingRound;
            return this;
        }

        public FiraOpenSessionParams.Builder setRangingIntervalMs(int rangingIntervalMs) {
            mRangingIntervalMs = rangingIntervalMs;
            return this;
        }

        public FiraOpenSessionParams.Builder setBlockStrideLength(int blockStrideLength) {
            mBlockStrideLength = blockStrideLength;
            return this;
        }

        public FiraOpenSessionParams.Builder setHoppingMode(int hoppingMode) {
            this.mHoppingMode = hoppingMode;
            return this;
        }

        public FiraOpenSessionParams.Builder setMaxRangingRoundRetries(
                @IntRange(from = 0, to = 65535) int maxRangingRoundRetries) {
            mMaxRangingRoundRetries = maxRangingRoundRetries;
            return this;
        }

        public FiraOpenSessionParams.Builder setSessionPriority(int sessionPriority) {
            mSessionPriority = sessionPriority;
            return this;
        }

        public FiraOpenSessionParams.Builder setMacAddressMode(int macAddressMode) {
            this.mMacAddressMode = macAddressMode;
            return this;
        }

        public FiraOpenSessionParams.Builder setHasResultReportPhase(boolean hasResultReportPhase) {
            mHasResultReportPhase = hasResultReportPhase;
            return this;
        }

        public FiraOpenSessionParams.Builder setMeasurementReportType(
                @MeasurementReportType int measurementReportType) {
            mMeasurementReportType = measurementReportType;
            return this;
        }

        public FiraOpenSessionParams.Builder setInBandTerminationAttemptCount(
                @IntRange(from = 1, to = 10) int inBandTerminationAttemptCount) {
            mInBandTerminationAttemptCount = inBandTerminationAttemptCount;
            return this;
        }

        public FiraOpenSessionParams.Builder setChannelNumber(@UwbChannel int channelNumber) {
            mChannelNumber = channelNumber;
            return this;
        }

        public FiraOpenSessionParams.Builder setPreambleCodeIndex(
                @UwbPreambleCodeIndex int preambleCodeIndex) {
            mPreambleCodeIndex = preambleCodeIndex;
            return this;
        }

        public FiraOpenSessionParams.Builder setRframeConfig(@RframeConfig int rframeConfig) {
            mRframeConfig = rframeConfig;
            return this;
        }

        public FiraOpenSessionParams.Builder setPrfMode(@PrfMode int prfMode) {
            mPrfMode = prfMode;
            return this;
        }

        public FiraOpenSessionParams.Builder setPreambleDuration(
                @PreambleDuration int preambleDuration) {
            mPreambleDuration = preambleDuration;
            return this;
        }

        public FiraOpenSessionParams.Builder setSfdId(@SfdIdValue int sfdId) {
            mSfdId = sfdId;
            return this;
        }

        public FiraOpenSessionParams.Builder setStsSegmentCount(
                @StsSegmentCountValue int stsSegmentCount) {
            mStsSegmentCount = stsSegmentCount;
            return this;
        }

        public FiraOpenSessionParams.Builder setStsLength(@StsLength int stsLength) {
            mStsLength = stsLength;
            return this;
        }

        public FiraOpenSessionParams.Builder setPsduDataRate(@PsduDataRate int psduDataRate) {
            mPsduDataRate = psduDataRate;
            return this;
        }

        public FiraOpenSessionParams.Builder setBprfPhrDataRate(
                @BprfPhrDataRate int bprfPhrDataRate) {
            mBprfPhrDataRate = bprfPhrDataRate;
            return this;
        }

        public FiraOpenSessionParams.Builder setFcsType(@MacFcsType int fcsType) {
            mFcsType = fcsType;
            return this;
        }

        public FiraOpenSessionParams.Builder setIsTxAdaptivePayloadPowerEnabled(
                boolean isTxAdaptivePayloadPowerEnabled) {
            mIsTxAdaptivePayloadPowerEnabled = isTxAdaptivePayloadPowerEnabled;
            return this;
        }

        public FiraOpenSessionParams.Builder setStsConfig(@StsConfig int stsConfig) {
            mStsConfig = stsConfig;
            return this;
        }

        public FiraOpenSessionParams.Builder setSubSessionId(int subSessionId) {
            mSubSessionId.set(subSessionId);
            return this;
        }

        public FiraOpenSessionParams.Builder setVendorId(@Nullable byte[] vendorId) {
            mVendorId = vendorId;
            return this;
        }

        public FiraOpenSessionParams.Builder setStaticStsIV(@Nullable byte[] staticStsIV) {
            mStaticStsIV = staticStsIV;
            return this;
        }

        public FiraOpenSessionParams.Builder setIsKeyRotationEnabled(boolean isKeyRotationEnabled) {
            mIsKeyRotationEnabled = isKeyRotationEnabled;
            return this;
        }

        public FiraOpenSessionParams.Builder setKeyRotationRate(int keyRotationRate) {
            mKeyRotationRate = keyRotationRate;
            return this;
        }

        public FiraOpenSessionParams.Builder setAoaResultRequest(
                @AoaResultRequestMode int aoaResultRequest) {
            mAoaResultRequest = aoaResultRequest;
            return this;
        }

        public FiraOpenSessionParams.Builder setRangeDataNtfConfig(
                @RangeDataNtfConfig int rangeDataNtfConfig) {
            mRangeDataNtfConfig = rangeDataNtfConfig;
            return this;
        }

        public FiraOpenSessionParams.Builder setRangeDataNtfProximityNear(
                int rangeDataNtfProximityNear) {
            mRangeDataNtfProximityNear = rangeDataNtfProximityNear;
            return this;
        }

        public FiraOpenSessionParams.Builder setRangeDataNtfProximityFar(
                int rangeDataNtfProximityFar) {
            mRangeDataNtfProximityFar = rangeDataNtfProximityFar;
            return this;
        }

        public FiraOpenSessionParams.Builder setHasTimeOfFlightReport(
                boolean hasTimeOfFlightReport) {
            mHasTimeOfFlightReport = hasTimeOfFlightReport;
            return this;
        }

        public FiraOpenSessionParams.Builder setHasAngleOfArrivalAzimuthReport(
                boolean hasAngleOfArrivalAzimuthReport) {
            mHasAngleOfArrivalAzimuthReport = hasAngleOfArrivalAzimuthReport;
            return this;
        }

        public FiraOpenSessionParams.Builder setHasAngleOfArrivalElevationReport(
                boolean hasAngleOfArrivalElevationReport) {
            mHasAngleOfArrivalElevationReport = hasAngleOfArrivalElevationReport;
            return this;
        }

        public FiraOpenSessionParams.Builder setHasAngleOfArrivalFigureOfMeritReport(
                boolean hasAngleOfArrivalFigureOfMeritReport) {
            mHasAngleOfArrivalFigureOfMeritReport = hasAngleOfArrivalFigureOfMeritReport;
            return this;
        }

        public FiraOpenSessionParams.Builder setAoaType(int aoaType) {
            mAoaType = aoaType;
            return this;
        }

       /**
        * After the session has been started, the device starts by
        * performing numOfMsrmtFocusOnRange range-only measurements (no
        * AoA), then it proceeds with numOfMsrmtFocusOnAoaAzimuth AoA
        * azimuth measurements followed by numOfMsrmtFocusOnAoaElevation
        * AoA elevation measurements.
        * If this is not invoked, the focus of each measurement is left
        * to the UWB vendor.
        *
        * Only valid when {@link #setAoaResultRequest(int)} is set to
        * {@link FiraParams#AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED}.
        */
        public FiraOpenSessionParams.Builder setMeasurementFocusRatio(
                int numOfMsrmtFocusOnRange,
                int numOfMsrmtFocusOnAoaAzimuth,
                int numOfMsrmtFocusOnAoaElevation) {
            mNumOfMsrmtFocusOnRange = numOfMsrmtFocusOnRange;
            mNumOfMsrmtFocusOnAoaAzimuth = numOfMsrmtFocusOnAoaAzimuth;
            mNumOfMsrmtFocusOnAoaElevation = numOfMsrmtFocusOnAoaElevation;
            return this;
        }

        private void checkAddress() {
            checkArgument(
                    mMacAddressMode == MAC_ADDRESS_MODE_2_BYTES
                            || mMacAddressMode == MAC_ADDRESS_MODE_8_BYTES);
            int addressByteLength = UwbAddress.SHORT_ADDRESS_BYTE_LENGTH;
            if (mMacAddressMode == MAC_ADDRESS_MODE_8_BYTES) {
                addressByteLength = UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH;
            }

            // Make sure address length matches the address mode
            checkArgument(mDeviceAddress != null && mDeviceAddress.size() == addressByteLength);
            checkNotNull(mDestAddressList);
            for (UwbAddress destAddress : mDestAddressList) {
                checkArgument(destAddress != null && destAddress.size() == addressByteLength);
            }
        }

        private void checkStsConfig() {
            if (mStsConfig == STS_CONFIG_STATIC) {
                // These two fields are used by Static STS only.
                checkArgument(mVendorId != null && mVendorId.length == 2);
                checkArgument(mStaticStsIV != null && mStaticStsIV.length == 6);
            }

            if (mStsConfig != STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY) {
                // Sub Session ID is used for dynamic individual key STS only.
                if (!mSubSessionId.isSet()) {
                    mSubSessionId.set(0);
                }
            }
        }

        private void checkInterleavingRatio() {
            if (mAoaResultRequest != AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED) {
                checkArgument(mNumOfMsrmtFocusOnRange == 0);
                checkArgument(mNumOfMsrmtFocusOnAoaAzimuth == 0);
                checkArgument(mNumOfMsrmtFocusOnAoaElevation == 0);
            } else {
                // at-least one of the ratio params should be set for interleaving mode.
                checkArgument(mNumOfMsrmtFocusOnRange > 0
                        || mNumOfMsrmtFocusOnAoaAzimuth > 0
                        || mNumOfMsrmtFocusOnAoaElevation > 0);
            }
        }

        public FiraOpenSessionParams build() {
            checkAddress();
            checkStsConfig();
            checkInterleavingRatio();
            return new FiraOpenSessionParams(
                    mProtocolVersion.get(),
                    mSessionId.get(),
                    mDeviceType.get(),
                    mDeviceRole.get(),
                    mRangingRoundUsage,
                    mMultiNodeMode.get(),
                    mDeviceAddress,
                    mDestAddressList,
                    mInitiationTimeMs,
                    mSlotDurationRstu,
                    mSlotsPerRangingRound,
                    mRangingIntervalMs,
                    mBlockStrideLength,
                    mHoppingMode,
                    mMaxRangingRoundRetries,
                    mSessionPriority,
                    mMacAddressMode,
                    mHasResultReportPhase,
                    mMeasurementReportType,
                    mInBandTerminationAttemptCount,
                    mChannelNumber,
                    mPreambleCodeIndex,
                    mRframeConfig,
                    mPrfMode,
                    mPreambleDuration,
                    mSfdId,
                    mStsSegmentCount,
                    mStsLength,
                    mPsduDataRate,
                    mBprfPhrDataRate,
                    mFcsType,
                    mIsTxAdaptivePayloadPowerEnabled,
                    mStsConfig,
                    mSubSessionId.get(),
                    mVendorId,
                    mStaticStsIV,
                    mIsKeyRotationEnabled,
                    mKeyRotationRate,
                    mAoaResultRequest,
                    mRangeDataNtfConfig,
                    mRangeDataNtfProximityNear,
                    mRangeDataNtfProximityFar,
                    mHasTimeOfFlightReport,
                    mHasAngleOfArrivalAzimuthReport,
                    mHasAngleOfArrivalElevationReport,
                    mHasAngleOfArrivalFigureOfMeritReport,
                    mAoaType,
                    mNumOfMsrmtFocusOnRange,
                    mNumOfMsrmtFocusOnAoaAzimuth,
                    mNumOfMsrmtFocusOnAoaElevation);
        }
    }
}
