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

package com.android.remoteprovisioner.unittest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;
import android.security.remoteprovisioning.AttestationPoolStatus;

import androidx.test.runner.AndroidJUnit4;

import com.android.remoteprovisioner.StatsProcessor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StatsProcessorTest {

    private AttestationPoolStatus mPool = new AttestationPoolStatus();
    private int mNumExtraKeys;

    @Before
    public void setUp() {
        mNumExtraKeys = 0;
        mPool.expiring = 0;
        mPool.unassigned = 0;
        mPool.attested = 0;
        mPool.total = 0;
    }

    @Presubmit
    @Test
    public void testMinUnassignedBoundary() throws Exception {
        mNumExtraKeys = 5;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        // Add an unassigned key to avoid the threshold for triggering reprovisioning.
        mPool.unassigned += 1;
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertFalse(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(7, stats.keysInUse);
        assertEquals(12, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
        // Now test provisioning needed boundary
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        mPool.expiring = 0;
        stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(8, stats.keysInUse);
        assertEquals(13, stats.idealTotalSignedKeys);
        assertEquals(13, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testStatsNoProvisioning() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        // Add an unassigned key to avoid the threshold for triggering reprovisioning.
        mPool.unassigned += 1;
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertFalse(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(7, stats.keysInUse);
        assertEquals(11, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testStatsProvisioning1() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(8, stats.keysInUse);
        assertEquals(12, stats.idealTotalSignedKeys);
        assertEquals(12, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testStatsProvisioning2() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        mPool.unassigned -= 1;
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(9, stats.keysInUse);
        assertEquals(13, stats.idealTotalSignedKeys);
        assertEquals(13, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testStatsProvisioningSomePreGenerated() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 15;
        mPool.attested = 10;
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(5, stats.unattestedKeys);
        assertEquals(8, stats.keysInUse);
        assertEquals(12, stats.idealTotalSignedKeys);
        assertEquals(7, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testStatsProvisioningAllPreGenerated() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 22;
        mPool.attested = 10;
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(12, stats.unattestedKeys);
        assertEquals(8, stats.keysInUse);
        assertEquals(12, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testStatsProvisioningTonsPreGenerated() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 33;
        mPool.attested = 10;
        mPool.unassigned = StatsProcessor.calcMinUnassignedToTriggerProvisioning(mNumExtraKeys);
        assertEquals(2, mPool.unassigned);
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(23, stats.unattestedKeys);
        assertEquals(8, stats.keysInUse);
        assertEquals(12, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNeeded1() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 5;
        mPool.expiring = 6;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNeeded2() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 5;
        mPool.expiring = 10;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNeeded3() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 5;
        mPool.expiring = 5;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNeeded4() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 10;
        mPool.expiring = 10;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(0, stats.keysInUse);
        assertEquals(4, stats.idealTotalSignedKeys);
        assertEquals(4, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNeeded5() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 5;
        mPool.expiring = 3;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(9, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNotNeeded1() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 5;
        mPool.expiring = 0;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertFalse(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNotNeeded2() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 5;
        mPool.expiring = 2;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertFalse(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(0, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testExpiringProvisioningNeededSomeKeysPregenerated() throws Exception {
        mNumExtraKeys = 4;
        mPool.total = 12;
        mPool.attested = 10;
        mPool.unassigned = 5;
        mPool.expiring = 6;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(2, stats.unattestedKeys);
        assertEquals(5, stats.keysInUse);
        assertEquals(9, stats.idealTotalSignedKeys);
        assertEquals(7, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testBothExpiringAndBelowMinimumExtraKeysAvailable() throws Exception {
        mNumExtraKeys = 5;
        mPool.total = 10;
        mPool.attested = 10;
        mPool.unassigned = 1;
        mPool.expiring = 6;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(0, stats.unattestedKeys);
        assertEquals(9, stats.keysInUse);
        assertEquals(14, stats.idealTotalSignedKeys);
        assertEquals(14, stats.keysToGenerate);
    }

    @Presubmit
    @Test
    public void testBothExpiringAndBelowMinimumExtraKeysAvailableWithPreGenKeys() throws Exception {
        mNumExtraKeys = 5;
        mPool.total = 14;
        mPool.attested = 10;
        mPool.unassigned = 1;
        mPool.expiring = 6;
        StatsProcessor.PoolStats stats = StatsProcessor.processPool(mPool, mNumExtraKeys);
        assertTrue(stats.provisioningNeeded);
        assertEquals(4, stats.unattestedKeys);
        assertEquals(9, stats.keysInUse);
        assertEquals(14, stats.idealTotalSignedKeys);
        assertEquals(10, stats.keysToGenerate);
    }
}
