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

package com.google.uwb.support;

import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED;
import static com.google.uwb.support.fira.FiraParams.AOA_TYPE_AZIMUTH_AND_ELEVATION;
import static com.google.uwb.support.fira.FiraParams.BPRF_PHR_DATA_RATE_6M81;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_8_BYTES;
import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_32;
import static com.google.uwb.support.fira.FiraParams.MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_STATUS_ERROR_MULTICAST_LIST_FULL;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_MANY_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T32_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_HPRF;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_7M80;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP1;
import static com.google.uwb.support.fira.FiraParams.SFD_ID_VALUE_3;
import static com.google.uwb.support.fira.FiraParams.STATE_CHANGE_REASON_CODE_ERROR_INVALID_RANGING_INTERVAL;
import static com.google.uwb.support.fira.FiraParams.STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
import static com.google.uwb.support.fira.FiraParams.STS_LENGTH_128_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.STS_SEGMENT_COUNT_VALUE_2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.fira.FiraControleeParams;
import com.google.uwb.support.fira.FiraMulticastListUpdateStatusCode;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.fira.FiraStateChangeReasonCode;
import com.google.uwb.support.fira.FiraStatusCode;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FiraTests {
    @Test
    public void testOpenSessionParams() {
        FiraProtocolVersion protocolVersion = FiraParams.PROTOCOL_VERSION_1_1;
        int sessionId = 10;
        int deviceType = RANGING_DEVICE_TYPE_CONTROLEE;
        int deviceRole = RANGING_DEVICE_ROLE_INITIATOR;
        int rangingRoundUsage = RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
        int multiNodeMode = MULTI_NODE_MODE_MANY_TO_MANY;
        int addressMode = MAC_ADDRESS_MODE_8_BYTES;
        UwbAddress deviceAddress = UwbAddress.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        UwbAddress destAddress1 = UwbAddress.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        UwbAddress destAddress2 =
                UwbAddress.fromBytes(new byte[] {(byte) 0xFF, (byte) 0xFE, 3, 4, 5, 6, 7, 8});
        List<UwbAddress> destAddressList = new ArrayList<>();
        destAddressList.add(destAddress1);
        destAddressList.add(destAddress2);
        int initiationTimeMs = 100;
        int slotDurationRstu = 2400;
        int slotsPerRangingRound = 10;
        int rangingIntervalMs = 100;
        int blockStrideLength = 2;
        int maxRangingRoundRetries = 3;
        int sessionPriority = 100;
        boolean hasResultReportPhase = true;
        int measurementReportType = MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;
        int inBandTerminationAttemptCount = 8;
        int channelNumber = 10;
        int preambleCodeIndex = 12;
        int rframeConfig = RFRAME_CONFIG_SP1;
        int prfMode = PRF_MODE_HPRF;
        int preambleDuration = PREAMBLE_DURATION_T32_SYMBOLS;
        int sfdId = SFD_ID_VALUE_3;
        int stsSegmentCount = STS_SEGMENT_COUNT_VALUE_2;
        int stsLength = STS_LENGTH_128_SYMBOLS;
        int psduDataRate = PSDU_DATA_RATE_7M80;
        int bprfPhrDataRate = BPRF_PHR_DATA_RATE_6M81;
        int fcsType = MAC_FCS_TYPE_CRC_32;
        boolean isTxAdaptivePayloadPowerEnabled = true;
        int stsConfig = STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
        int subSessionId = 24;
        byte[] vendorId = new byte[] {(byte) 0xFE, (byte) 0xDC};
        byte[] staticStsIV = new byte[] {(byte) 0xDF, (byte) 0xCE, (byte) 0xAB, 0x12, 0x34, 0x56};
        boolean isKeyRotationEnabled = true;
        int keyRotationRate = 15;
        int aoaResultRequest = AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED;
        int rangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;
        int rangeDataNtfProximityNear = 50;
        int rangeDataNtfProximityFar = 200;
        boolean hasTimeOfFlightReport = true;
        boolean hasAngleOfArrivalAzimuthReport = true;
        boolean hasAngleOfArrivalElevationReport = true;
        boolean hasAngleOfArrivalFigureOfMeritReport = true;
        int aoaType = AOA_TYPE_AZIMUTH_AND_ELEVATION;
        int numOfMsrmtFocusOnRange = 1;
        int numOfMsrmtFocusOnAoaAzimuth = 2;
        int numOfMsrmtFocusOnAoaElevation = 3;

        FiraOpenSessionParams params =
                new FiraOpenSessionParams.Builder()
                        .setProtocolVersion(protocolVersion)
                        .setSessionId(sessionId)
                        .setDeviceType(deviceType)
                        .setDeviceRole(deviceRole)
                        .setRangingRoundUsage(rangingRoundUsage)
                        .setMultiNodeMode(multiNodeMode)
                        .setDeviceAddress(deviceAddress)
                        .setDestAddressList(destAddressList)
                        .setInitiationTimeMs(initiationTimeMs)
                        .setSlotDurationRstu(slotDurationRstu)
                        .setSlotsPerRangingRound(slotsPerRangingRound)
                        .setRangingIntervalMs(rangingIntervalMs)
                        .setBlockStrideLength(blockStrideLength)
                        .setMaxRangingRoundRetries(maxRangingRoundRetries)
                        .setSessionPriority(sessionPriority)
                        .setMacAddressMode(addressMode)
                        .setHasResultReportPhase(hasResultReportPhase)
                        .setMeasurementReportType(measurementReportType)
                        .setInBandTerminationAttemptCount(inBandTerminationAttemptCount)
                        .setChannelNumber(channelNumber)
                        .setPreambleCodeIndex(preambleCodeIndex)
                        .setRframeConfig(rframeConfig)
                        .setPrfMode(prfMode)
                        .setPreambleDuration(preambleDuration)
                        .setSfdId(sfdId)
                        .setStsSegmentCount(stsSegmentCount)
                        .setStsLength(stsLength)
                        .setPsduDataRate(psduDataRate)
                        .setBprfPhrDataRate(bprfPhrDataRate)
                        .setFcsType(fcsType)
                        .setIsTxAdaptivePayloadPowerEnabled(isTxAdaptivePayloadPowerEnabled)
                        .setStsConfig(stsConfig)
                        .setSubSessionId(subSessionId)
                        .setVendorId(vendorId)
                        .setStaticStsIV(staticStsIV)
                        .setIsKeyRotationEnabled(isKeyRotationEnabled)
                        .setKeyRotationRate(keyRotationRate)
                        .setAoaResultRequest(aoaResultRequest)
                        .setRangeDataNtfConfig(rangeDataNtfConfig)
                        .setRangeDataNtfProximityNear(rangeDataNtfProximityNear)
                        .setRangeDataNtfProximityFar(rangeDataNtfProximityFar)
                        .setHasTimeOfFlightReport(hasTimeOfFlightReport)
                        .setHasAngleOfArrivalAzimuthReport(hasAngleOfArrivalAzimuthReport)
                        .setHasAngleOfArrivalElevationReport(hasAngleOfArrivalElevationReport)
                        .setHasAngleOfArrivalFigureOfMeritReport(
                                hasAngleOfArrivalFigureOfMeritReport)
                        .setAoaType(aoaType)
                        .setMeasurementFocusRatio(
                                numOfMsrmtFocusOnRange,
                                numOfMsrmtFocusOnAoaAzimuth,
                                numOfMsrmtFocusOnAoaElevation)
                        .build();

        assertEquals(params.getProtocolVersion(), protocolVersion);
        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getDeviceType(), deviceType);
        assertEquals(params.getDeviceRole(), deviceRole);
        assertEquals(params.getRangingRoundUsage(), rangingRoundUsage);
        assertEquals(params.getMultiNodeMode(), multiNodeMode);
        assertEquals(params.getDeviceAddress(), deviceAddress);
        assertEquals(params.getDestAddressList().size(), destAddressList.size());
        for (int i = 0; i < destAddressList.size(); i++) {
            assertEquals(params.getDestAddressList().get(i), destAddressList.get(i));
        }

        assertEquals(params.getInitiationTimeMs(), initiationTimeMs);
        assertEquals(params.getSlotDurationRstu(), slotDurationRstu);
        assertEquals(params.getSlotsPerRangingRound(), slotsPerRangingRound);
        assertEquals(params.getRangingIntervalMs(), rangingIntervalMs);
        assertEquals(params.getBlockStrideLength(), blockStrideLength);
        assertEquals(params.getMaxRangingRoundRetries(), maxRangingRoundRetries);
        assertEquals(params.getSessionPriority(), sessionPriority);
        assertEquals(params.getMacAddressMode(), addressMode);
        assertEquals(params.hasResultReportPhase(), hasResultReportPhase);
        assertEquals(params.getMeasurementReportType(), measurementReportType);
        assertEquals(params.getInBandTerminationAttemptCount(), inBandTerminationAttemptCount);
        assertEquals(params.getChannelNumber(), channelNumber);
        assertEquals(params.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(params.getRframeConfig(), rframeConfig);
        assertEquals(params.getPrfMode(), prfMode);
        assertEquals(params.getPreambleDuration(), preambleDuration);
        assertEquals(params.getSfdId(), sfdId);
        assertEquals(params.getStsSegmentCount(), stsSegmentCount);
        assertEquals(params.getStsLength(), stsLength);
        assertEquals(params.getPsduDataRate(), psduDataRate);
        assertEquals(params.getBprfPhrDataRate(), bprfPhrDataRate);
        assertEquals(params.getFcsType(), fcsType);
        assertEquals(params.isTxAdaptivePayloadPowerEnabled(), isTxAdaptivePayloadPowerEnabled);
        assertEquals(params.getStsConfig(), stsConfig);
        assertEquals(params.getSubSessionId(), subSessionId);
        assertArrayEquals(params.getVendorId(), vendorId);
        assertArrayEquals(params.getStaticStsIV(), staticStsIV);
        assertEquals(params.isKeyRotationEnabled(), isKeyRotationEnabled);
        assertEquals(params.getKeyRotationRate(), keyRotationRate);
        assertEquals(params.getAoaResultRequest(), aoaResultRequest);
        assertEquals(params.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(params.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear);
        assertEquals(params.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar);
        assertEquals(params.hasTimeOfFlightReport(), hasTimeOfFlightReport);
        assertEquals(params.hasAngleOfArrivalAzimuthReport(), hasAngleOfArrivalAzimuthReport);
        assertEquals(params.hasAngleOfArrivalElevationReport(), hasAngleOfArrivalElevationReport);
        assertEquals(
                params.hasAngleOfArrivalFigureOfMeritReport(),
                hasAngleOfArrivalFigureOfMeritReport);
        assertEquals(params.getAoaType(), aoaType);
        assertEquals(params.getNumOfMsrmtFocusOnRange(), numOfMsrmtFocusOnRange);
        assertEquals(params.getNumOfMsrmtFocusOnAoaAzimuth(), numOfMsrmtFocusOnAoaAzimuth);
        assertEquals(params.getNumOfMsrmtFocusOnAoaElevation(), numOfMsrmtFocusOnAoaElevation);

        FiraOpenSessionParams fromBundle = FiraOpenSessionParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getRangingRoundUsage(), rangingRoundUsage);
        assertEquals(fromBundle.getMultiNodeMode(), multiNodeMode);

        assertEquals(fromBundle.getDeviceAddress(), deviceAddress);
        assertEquals(fromBundle.getDestAddressList().size(), destAddressList.size());
        for (int i = 0; i < destAddressList.size(); i++) {
            assertEquals(fromBundle.getDestAddressList().get(i), destAddressList.get(i));
        }

        assertEquals(fromBundle.getInitiationTimeMs(), initiationTimeMs);
        assertEquals(fromBundle.getSlotDurationRstu(), slotDurationRstu);
        assertEquals(fromBundle.getSlotsPerRangingRound(), slotsPerRangingRound);
        assertEquals(fromBundle.getRangingIntervalMs(), rangingIntervalMs);
        assertEquals(fromBundle.getBlockStrideLength(), blockStrideLength);
        assertEquals(fromBundle.getMaxRangingRoundRetries(), maxRangingRoundRetries);
        assertEquals(fromBundle.getSessionPriority(), sessionPriority);
        assertEquals(fromBundle.getMacAddressMode(), addressMode);
        assertEquals(fromBundle.hasResultReportPhase(), hasResultReportPhase);
        assertEquals(fromBundle.getMeasurementReportType(), measurementReportType);
        assertEquals(fromBundle.getInBandTerminationAttemptCount(), inBandTerminationAttemptCount);
        assertEquals(fromBundle.getChannelNumber(), channelNumber);
        assertEquals(fromBundle.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(fromBundle.getRframeConfig(), rframeConfig);
        assertEquals(fromBundle.getPrfMode(), prfMode);
        assertEquals(fromBundle.getPreambleDuration(), preambleDuration);
        assertEquals(fromBundle.getSfdId(), sfdId);
        assertEquals(fromBundle.getStsSegmentCount(), stsSegmentCount);
        assertEquals(fromBundle.getStsLength(), stsLength);
        assertEquals(fromBundle.getPsduDataRate(), psduDataRate);
        assertEquals(fromBundle.getBprfPhrDataRate(), bprfPhrDataRate);
        assertEquals(fromBundle.getFcsType(), fcsType);
        assertEquals(fromBundle.isTxAdaptivePayloadPowerEnabled(), isTxAdaptivePayloadPowerEnabled);
        assertEquals(fromBundle.getStsConfig(), stsConfig);
        assertEquals(fromBundle.getSubSessionId(), subSessionId);
        assertArrayEquals(fromBundle.getVendorId(), vendorId);
        assertArrayEquals(fromBundle.getStaticStsIV(), staticStsIV);
        assertEquals(fromBundle.isKeyRotationEnabled(), isKeyRotationEnabled);
        assertEquals(fromBundle.getKeyRotationRate(), keyRotationRate);
        assertEquals(fromBundle.getAoaResultRequest(), aoaResultRequest);
        assertEquals(fromBundle.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(fromBundle.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear);
        assertEquals(fromBundle.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar);
        assertEquals(fromBundle.hasTimeOfFlightReport(), hasTimeOfFlightReport);
        assertEquals(fromBundle.hasAngleOfArrivalAzimuthReport(), hasAngleOfArrivalAzimuthReport);
        assertEquals(
                fromBundle.hasAngleOfArrivalElevationReport(), hasAngleOfArrivalElevationReport);
        assertEquals(
                fromBundle.hasAngleOfArrivalFigureOfMeritReport(),
                hasAngleOfArrivalFigureOfMeritReport);
        assertEquals(fromBundle.getAoaType(), aoaType);
        assertEquals(fromBundle.getNumOfMsrmtFocusOnRange(), numOfMsrmtFocusOnRange);
        assertEquals(fromBundle.getNumOfMsrmtFocusOnAoaAzimuth(), numOfMsrmtFocusOnAoaAzimuth);
        assertEquals(fromBundle.getNumOfMsrmtFocusOnAoaElevation(), numOfMsrmtFocusOnAoaElevation);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingReconfigureParams() {
        int action = MULTICAST_LIST_UPDATE_ACTION_DELETE;
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int blockStrideLength = 5;
        int rangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;
        int rangeDataProximityNear = 100;
        int rangeDataProximityFar = 500;

        int[] subSessionIdList = new int[] {3, 4};
        FiraRangingReconfigureParams params =
                new FiraRangingReconfigureParams.Builder()
                        .setAction(action)
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        assertEquals((int) params.getAction(), action);
        assertArrayEquals(params.getAddressList(), addressList);
        assertArrayEquals(params.getSubSessionIdList(), subSessionIdList);
        FiraRangingReconfigureParams fromBundle =
                FiraRangingReconfigureParams.fromBundle(params.toBundle());
        assertEquals((int) fromBundle.getAction(), action);
        assertArrayEquals(fromBundle.getAddressList(), addressList);
        assertArrayEquals(fromBundle.getSubSessionIdList(), subSessionIdList);
        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);

        params =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(blockStrideLength)
                        .setRangeDataNtfConfig(rangeDataNtfConfig)
                        .setRangeDataProximityNear(rangeDataProximityNear)
                        .setRangeDataProximityFar(rangeDataProximityFar)
                        .build();
        assertEquals((int) params.getBlockStrideLength(), blockStrideLength);
        assertEquals((int) params.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals((int) params.getRangeDataProximityNear(), rangeDataProximityNear);
        assertEquals((int) params.getRangeDataProximityFar(), rangeDataProximityFar);
        fromBundle = FiraRangingReconfigureParams.fromBundle(params.toBundle());
        assertEquals((int) fromBundle.getBlockStrideLength(), blockStrideLength);
        assertEquals((int) fromBundle.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals((int) fromBundle.getRangeDataProximityNear(), rangeDataProximityNear);
        assertEquals((int) fromBundle.getRangeDataProximityFar(), rangeDataProximityFar);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testControleeParams() {
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int[] subSessionIdList = new int[] {3, 4};
        FiraControleeParams params =
                new FiraControleeParams.Builder()
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        assertArrayEquals(params.getAddressList(), addressList);
        assertArrayEquals(params.getSubSessionIdList(), subSessionIdList);
        FiraControleeParams fromBundle =
                FiraControleeParams.fromBundle(params.toBundle());
        assertArrayEquals(fromBundle.getAddressList(), addressList);
        assertArrayEquals(fromBundle.getSubSessionIdList(), subSessionIdList);
        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testStatusCode() {
        int statusCode = STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT;
        FiraStatusCode params = new FiraStatusCode.Builder().setStatusCode(statusCode).build();
        assertEquals(params.getStatusCode(), statusCode);
        assertTrue(FiraStatusCode.isBundleValid(params.toBundle()));
        FiraStatusCode fromBundle = FiraStatusCode.fromBundle(params.toBundle());
        assertEquals(fromBundle.getStatusCode(), statusCode);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testMulticastListUpdateStatusCode() {
        int statusCode = MULTICAST_LIST_UPDATE_STATUS_ERROR_MULTICAST_LIST_FULL;
        FiraMulticastListUpdateStatusCode params =
                new FiraMulticastListUpdateStatusCode.Builder().setStatusCode(statusCode).build();
        assertEquals(params.getStatusCode(), statusCode);
        assertTrue(FiraMulticastListUpdateStatusCode.isBundleValid(params.toBundle()));

        FiraMulticastListUpdateStatusCode fromBundle =
                FiraMulticastListUpdateStatusCode.fromBundle(params.toBundle());
        assertEquals(fromBundle.getStatusCode(), statusCode);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testStateChangeReasonCode() {
        int reasonCode = STATE_CHANGE_REASON_CODE_ERROR_INVALID_RANGING_INTERVAL;
        FiraStateChangeReasonCode params =
                new FiraStateChangeReasonCode.Builder().setReasonCode(reasonCode).build();
        assertEquals(reasonCode, params.getReasonCode());
        assertTrue(FiraStateChangeReasonCode.isBundleValid(params.toBundle()));

        FiraStateChangeReasonCode fromBundle =
                FiraStateChangeReasonCode.fromBundle(params.toBundle());
        assertEquals(reasonCode, fromBundle.getReasonCode());

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    private void verifyProtocolPresent(Params params) {
        assertTrue(Params.isProtocol(params.toBundle(), FiraParams.PROTOCOL_NAME));
    }

    private void verifyBundlesEqual(Params params, Params fromBundle) {
        PersistableBundle.kindofEquals(params.toBundle(), fromBundle.toBundle());
    }

    @Test
    public void testSpecificationParams() {
        FiraProtocolVersion minPhyVersionSupported = new FiraProtocolVersion(1, 0);
        FiraProtocolVersion maxPhyVersionSupported = new FiraProtocolVersion(2, 0);
        FiraProtocolVersion minMacVersionSupported = new FiraProtocolVersion(1, 2);
        FiraProtocolVersion maxMacVersionSupported = new FiraProtocolVersion(1, 2);
        List<Integer> supportedChannels = List.of(5, 6, 8, 9);
        EnumSet<FiraParams.AoaCapabilityFlag> aoaCapabilities =
                EnumSet.of(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);

        EnumSet<FiraParams.DeviceRoleCapabilityFlag> deviceRoleCapabilities =
                EnumSet.allOf(FiraParams.DeviceRoleCapabilityFlag.class);
        boolean hasBlockStridingSupport = true;
        boolean hasNonDeferredModeSupport = true;
        boolean hasInitiationTimeSupport = true;
        EnumSet<FiraParams.MultiNodeCapabilityFlag> multiNodeCapabilities =
                EnumSet.allOf(FiraParams.MultiNodeCapabilityFlag.class);
        EnumSet<FiraParams.PrfCapabilityFlag> prfCapabilities =
                EnumSet.allOf(FiraParams.PrfCapabilityFlag.class);
        EnumSet<FiraParams.RangingRoundCapabilityFlag> rangingRoundCapabilities =
                EnumSet.allOf(FiraParams.RangingRoundCapabilityFlag.class);
        EnumSet<FiraParams.RframeCapabilityFlag> rframeCapabilities =
                EnumSet.allOf(FiraParams.RframeCapabilityFlag.class);
        EnumSet<FiraParams.StsCapabilityFlag> stsCapabilities =
                EnumSet.allOf(FiraParams.StsCapabilityFlag.class);
        EnumSet<FiraParams.PsduDataRateCapabilityFlag> psduDataRateCapabilities =
                EnumSet.allOf(FiraParams.PsduDataRateCapabilityFlag.class);
        EnumSet<FiraParams.BprfParameterSetCapabilityFlag> bprfCapabilities =
                EnumSet.allOf(FiraParams.BprfParameterSetCapabilityFlag.class);
        EnumSet<FiraParams.HprfParameterSetCapabilityFlag> hprfCapabilities =
                EnumSet.allOf(FiraParams.HprfParameterSetCapabilityFlag.class);

        FiraSpecificationParams params =
                new FiraSpecificationParams.Builder()
                        .setMinPhyVersionSupported(minPhyVersionSupported)
                        .setMaxPhyVersionSupported(maxPhyVersionSupported)
                        .setMinMacVersionSupported(minMacVersionSupported)
                        .setMaxMacVersionSupported(maxMacVersionSupported)
                        .setSupportedChannels(supportedChannels)
                        .setAoaCapabilities(aoaCapabilities)
                        .setDeviceRoleCapabilities(deviceRoleCapabilities)
                        .hasBlockStridingSupport(hasBlockStridingSupport)
                        .hasNonDeferredModeSupport(hasNonDeferredModeSupport)
                        .hasInitiationTimeSupport(hasInitiationTimeSupport)
                        .setMultiNodeCapabilities(multiNodeCapabilities)
                        .setPrfCapabilities(prfCapabilities)
                        .setRangingRoundCapabilities(rangingRoundCapabilities)
                        .setRframeCapabilities(rframeCapabilities)
                        .setStsCapabilities(stsCapabilities)
                        .setPsduDataRateCapabilities(psduDataRateCapabilities)
                        .setBprfParameterSetCapabilities(bprfCapabilities)
                        .setHprfParameterSetCapabilities(hprfCapabilities)
                        .build();
        assertEquals(minPhyVersionSupported, params.getMinPhyVersionSupported());
        assertEquals(maxPhyVersionSupported, params.getMaxPhyVersionSupported());
        assertEquals(minMacVersionSupported, params.getMinMacVersionSupported());
        assertEquals(maxMacVersionSupported, params.getMaxMacVersionSupported());
        assertEquals(supportedChannels, params.getSupportedChannels());
        assertEquals(aoaCapabilities, params.getAoaCapabilities());
        assertEquals(deviceRoleCapabilities, params.getDeviceRoleCapabilities());
        assertEquals(hasBlockStridingSupport, params.hasBlockStridingSupport());
        assertEquals(hasNonDeferredModeSupport, params.hasNonDeferredModeSupport());
        assertEquals(hasInitiationTimeSupport, params.hasInitiationTimeSupport());
        assertEquals(multiNodeCapabilities, params.getMultiNodeCapabilities());
        assertEquals(prfCapabilities, params.getPrfCapabilities());
        assertEquals(rangingRoundCapabilities, params.getRangingRoundCapabilities());
        assertEquals(rframeCapabilities, params.getRframeCapabilities());
        assertEquals(stsCapabilities, params.getStsCapabilities());
        assertEquals(psduDataRateCapabilities, params.getPsduDataRateCapabilities());
        assertEquals(bprfCapabilities, params.getBprfParameterSetCapabilities());
        assertEquals(hprfCapabilities, params.getHprfParameterSetCapabilities());

        FiraSpecificationParams fromBundle = FiraSpecificationParams.fromBundle(params.toBundle());
        assertEquals(minPhyVersionSupported, fromBundle.getMinPhyVersionSupported());
        assertEquals(maxPhyVersionSupported, fromBundle.getMaxPhyVersionSupported());
        assertEquals(minMacVersionSupported, fromBundle.getMinMacVersionSupported());
        assertEquals(maxMacVersionSupported, fromBundle.getMaxMacVersionSupported());
        assertEquals(supportedChannels, fromBundle.getSupportedChannels());
        assertEquals(aoaCapabilities, fromBundle.getAoaCapabilities());
        assertEquals(deviceRoleCapabilities, fromBundle.getDeviceRoleCapabilities());
        assertEquals(hasBlockStridingSupport, fromBundle.hasBlockStridingSupport());
        assertEquals(hasNonDeferredModeSupport, fromBundle.hasNonDeferredModeSupport());
        assertEquals(hasInitiationTimeSupport, params.hasInitiationTimeSupport());
        assertEquals(multiNodeCapabilities, fromBundle.getMultiNodeCapabilities());
        assertEquals(prfCapabilities, fromBundle.getPrfCapabilities());
        assertEquals(rangingRoundCapabilities, fromBundle.getRangingRoundCapabilities());
        assertEquals(rframeCapabilities, fromBundle.getRframeCapabilities());
        assertEquals(stsCapabilities, fromBundle.getStsCapabilities());
        assertEquals(psduDataRateCapabilities, fromBundle.getPsduDataRateCapabilities());
        assertEquals(bprfCapabilities, fromBundle.getBprfParameterSetCapabilities());
        assertEquals(hprfCapabilities, fromBundle.getHprfParameterSetCapabilities());
        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }
}
