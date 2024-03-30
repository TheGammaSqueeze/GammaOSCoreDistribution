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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccProtocolVersion;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccRangingError;
import com.google.uwb.support.ccc.CccRangingReconfiguredParams;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.ccc.CccStartRangingParams;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CccTests {

    @Test
    public void testOpenRangingParams() {
        CccProtocolVersion protocolVersion = CccParams.PROTOCOL_VERSION_1_0;
        @CccParams.UwbConfig int uwbConfig = CccParams.UWB_CONFIG_1;
        CccPulseShapeCombo pulseShapeCombo =
                new CccPulseShapeCombo(
                        CccParams.PULSE_SHAPE_PRECURSOR_FREE, CccParams.PULSE_SHAPE_PRECURSOR_FREE);
        int sessionId = 10;
        int ranMultiplier = 128;
        @CccParams.Channel int channel = CccParams.UWB_CHANNEL_9;
        @CccParams.ChapsPerSlot int chapsPerSlot = CccParams.CHAPS_PER_SLOT_6;
        int numResponderNodes = 9;
        @CccParams.SlotsPerRound int numSlotsPerRound = CccParams.SLOTS_PER_ROUND_12;
        @CccParams.SyncCodeIndex int syncCodeIdx = 22;
        @CccParams.HoppingConfigMode int hoppingConfigMode = CccParams.HOPPING_CONFIG_MODE_ADAPTIVE;
        @CccParams.HoppingSequence int hoppingSequence = CccParams.HOPPING_SEQUENCE_AES;

        CccOpenRangingParams params =
                new CccOpenRangingParams.Builder()
                        .setProtocolVersion(protocolVersion)
                        .setUwbConfig(uwbConfig)
                        .setPulseShapeCombo(pulseShapeCombo)
                        .setSessionId(sessionId)
                        .setRanMultiplier(ranMultiplier)
                        .setChannel(channel)
                        .setNumChapsPerSlot(chapsPerSlot)
                        .setNumResponderNodes(numResponderNodes)
                        .setNumSlotsPerRound(numSlotsPerRound)
                        .setSyncCodeIndex(syncCodeIdx)
                        .setHoppingConfigMode(hoppingConfigMode)
                        .setHoppingSequence(hoppingSequence)
                        .build();

        assertEquals(params.getProtocolVersion(), protocolVersion);
        assertEquals(params.getUwbConfig(), uwbConfig);
        assertEquals(
                params.getPulseShapeCombo().getInitiatorTx(), pulseShapeCombo.getInitiatorTx());
        assertEquals(
                params.getPulseShapeCombo().getResponderTx(), pulseShapeCombo.getResponderTx());
        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getRanMultiplier(), ranMultiplier);
        assertEquals(params.getChannel(), channel);
        assertEquals(params.getNumChapsPerSlot(), chapsPerSlot);
        assertEquals(params.getNumResponderNodes(), numResponderNodes);
        assertEquals(params.getNumSlotsPerRound(), numSlotsPerRound);
        assertEquals(params.getSyncCodeIndex(), syncCodeIdx);
        assertEquals(params.getHoppingConfigMode(), hoppingConfigMode);
        assertEquals(params.getHoppingSequence(), hoppingSequence);

        CccOpenRangingParams fromBundle = CccOpenRangingParams.fromBundle(params.toBundle());
        assertEquals(fromBundle.getProtocolVersion(), protocolVersion);
        assertEquals(fromBundle.getUwbConfig(), uwbConfig);
        assertEquals(
                fromBundle.getPulseShapeCombo().getInitiatorTx(), pulseShapeCombo.getInitiatorTx());
        assertEquals(
                fromBundle.getPulseShapeCombo().getResponderTx(), pulseShapeCombo.getResponderTx());
        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getRanMultiplier(), ranMultiplier);
        assertEquals(fromBundle.getChannel(), channel);
        assertEquals(fromBundle.getNumChapsPerSlot(), chapsPerSlot);
        assertEquals(fromBundle.getNumResponderNodes(), numResponderNodes);
        assertEquals(fromBundle.getNumSlotsPerRound(), numSlotsPerRound);
        assertEquals(fromBundle.getSyncCodeIndex(), syncCodeIdx);
        assertEquals(fromBundle.getHoppingConfigMode(), hoppingConfigMode);
        assertEquals(fromBundle.getHoppingSequence(), hoppingSequence);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingError() {
        @CccParams.ProtocolError int error = CccParams.PROTOCOL_ERROR_SE_BUSY;
        CccRangingError params = new CccRangingError.Builder().setError(error).build();

        assertEquals(params.getError(), error);

        CccRangingError fromBundle = CccRangingError.fromBundle(params.toBundle());
        assertEquals(fromBundle.getError(), error);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingReconfiguredParams() {
        CccRangingReconfiguredParams params = new CccRangingReconfiguredParams.Builder().build();

        CccRangingReconfiguredParams fromBundle =
                CccRangingReconfiguredParams.fromBundle(params.toBundle());

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testStartRangingParams() {
        int sessionId = 10;
        int ranMultiplier = 128;

        CccStartRangingParams params =
                new CccStartRangingParams.Builder()
                        .setSessionId(sessionId)
                        .setRanMultiplier(ranMultiplier)
                        .build();

        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getRanMultiplier(), ranMultiplier);

        CccStartRangingParams fromBundle = CccStartRangingParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getRanMultiplier(), ranMultiplier);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingStartedParams() {
        int hopModeKey = 98876444;
        int startingStsIndex = 246802468;
        @CccParams.SyncCodeIndex int syncCodeIndex = 10;
        long uwbTime0 = 50;
        int ranMultiplier = 10;

        CccRangingStartedParams params =
                new CccRangingStartedParams.Builder()
                        .setHopModeKey(hopModeKey)
                        .setStartingStsIndex(startingStsIndex)
                        .setSyncCodeIndex(syncCodeIndex)
                        .setUwbTime0(uwbTime0)
                        .setRanMultiplier(ranMultiplier)
                        .build();

        assertEquals(params.getHopModeKey(), hopModeKey);
        assertEquals(params.getStartingStsIndex(), startingStsIndex);
        assertEquals(params.getSyncCodeIndex(), syncCodeIndex);
        assertEquals(params.getUwbTime0(), uwbTime0);
        assertEquals(params.getRanMultiplier(), ranMultiplier);

        CccRangingStartedParams fromBundle = CccRangingStartedParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getHopModeKey(), hopModeKey);
        assertEquals(fromBundle.getStartingStsIndex(), startingStsIndex);
        assertEquals(fromBundle.getSyncCodeIndex(), syncCodeIndex);
        assertEquals(fromBundle.getUwbTime0(), uwbTime0);
        assertEquals(fromBundle.getRanMultiplier(), ranMultiplier);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testSpecificationParams() {
        CccProtocolVersion[] protocolVersions =
                new CccProtocolVersion[] {
                    new CccProtocolVersion(1, 0),
                    new CccProtocolVersion(2, 0),
                    new CccProtocolVersion(2, 1)
                };

        Integer[] uwbConfigs = new Integer[] {CccParams.UWB_CONFIG_0, CccParams.UWB_CONFIG_1};
        CccPulseShapeCombo[] pulseShapeCombos =
                new CccPulseShapeCombo[] {
                    new CccPulseShapeCombo(
                            CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                            CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE),
                    new CccPulseShapeCombo(
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE,
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE),
                    new CccPulseShapeCombo(
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL,
                            CccParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL)
                };
        int ranMultiplier = 200;
        Integer[] chapsPerSlots =
                new Integer[] {CccParams.CHAPS_PER_SLOT_4, CccParams.CHAPS_PER_SLOT_12};
        Integer[] syncCodes =
                new Integer[] {10, 23};
        Integer[] channels = new Integer[] {CccParams.UWB_CHANNEL_5, CccParams.UWB_CHANNEL_9};
        Integer[] hoppingConfigModes =
                new Integer[] {
                    CccParams.HOPPING_CONFIG_MODE_ADAPTIVE, CccParams.HOPPING_CONFIG_MODE_CONTINUOUS
                };
        Integer[] hoppingSequences =
                new Integer[] {CccParams.HOPPING_SEQUENCE_AES, CccParams.HOPPING_SEQUENCE_DEFAULT};

        CccSpecificationParams.Builder paramsBuilder = new CccSpecificationParams.Builder();
        for (CccProtocolVersion p : protocolVersions) {
            paramsBuilder.addProtocolVersion(p);
        }

        for (int uwbConfig : uwbConfigs) {
            paramsBuilder.addUwbConfig(uwbConfig);
        }

        for (CccPulseShapeCombo pulseShapeCombo : pulseShapeCombos) {
            paramsBuilder.addPulseShapeCombo(pulseShapeCombo);
        }

        paramsBuilder.setRanMultiplier(ranMultiplier);

        for (int chapsPerSlot : chapsPerSlots) {
            paramsBuilder.addChapsPerSlot(chapsPerSlot);
        }

        for (int syncCode : syncCodes) {
            paramsBuilder.addSyncCode(syncCode);
        }

        for (int channel : channels) {
            paramsBuilder.addChannel(channel);
        }

        for (int hoppingConfigMode : hoppingConfigModes) {
            paramsBuilder.addHoppingConfigMode(hoppingConfigMode);
        }

        for (int hoppingSequence : hoppingSequences) {
            paramsBuilder.addHoppingSequence(hoppingSequence);
        }

        CccSpecificationParams params = paramsBuilder.build();
        assertArrayEquals(params.getProtocolVersions().toArray(), protocolVersions);
        assertArrayEquals(params.getUwbConfigs().toArray(), uwbConfigs);
        assertArrayEquals(params.getPulseShapeCombos().toArray(), pulseShapeCombos);
        assertEquals(params.getRanMultiplier(), ranMultiplier);
        assertArrayEquals(params.getChapsPerSlot().toArray(), chapsPerSlots);
        assertArrayEquals(params.getSyncCodes().toArray(), syncCodes);
        assertArrayEquals(params.getChannels().toArray(), channels);
        assertArrayEquals(params.getHoppingConfigModes().toArray(), hoppingConfigModes);
        assertArrayEquals(params.getHoppingSequences().toArray(), hoppingSequences);

        CccSpecificationParams fromBundle = CccSpecificationParams.fromBundle(params.toBundle());
        assertArrayEquals(fromBundle.getProtocolVersions().toArray(), protocolVersions);
        assertArrayEquals(fromBundle.getUwbConfigs().toArray(), uwbConfigs);
        assertArrayEquals(fromBundle.getPulseShapeCombos().toArray(), pulseShapeCombos);
        assertEquals(fromBundle.getRanMultiplier(), ranMultiplier);
        assertArrayEquals(fromBundle.getChapsPerSlot().toArray(), chapsPerSlots);
        assertArrayEquals(fromBundle.getSyncCodes().toArray(), syncCodes);
        assertArrayEquals(fromBundle.getChannels().toArray(), channels);
        assertArrayEquals(fromBundle.getHoppingConfigModes().toArray(), hoppingConfigModes);
        assertArrayEquals(fromBundle.getHoppingSequences().toArray(), hoppingSequences);

        verifyProtocolPresent(params);
        assertTrue(params.equals(fromBundle));

        // Add random channel to params builder to force inequality.
        paramsBuilder.addChannel(0);
        // Rebuild params.
        params = paramsBuilder.build();
        // Test that params and fromBundle are not equal.
        assertTrue(!params.equals(fromBundle));
    }

    private void verifyProtocolPresent(Params params) {
        assertTrue(Params.isProtocol(params.toBundle(), CccParams.PROTOCOL_NAME));
    }

    private void verifyBundlesEqual(Params params, Params fromBundle) {
        assertTrue(PersistableBundle.kindofEquals(params.toBundle(), fromBundle.toBundle()));
    }
}
