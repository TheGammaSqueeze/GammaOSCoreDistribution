/**
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

package com.android.remoteprovisioner;

import android.security.remoteprovisioning.AttestationPoolStatus;
import android.util.Log;

public class StatsProcessor {
    public static final double LIMIT_SCALER = .4;

    private static final String TAG = "RemoteProvisioningService.KeyPoolStats";

    private StatsProcessor() {}

    public static int calcMinUnassignedToTriggerProvisioning(int extraSignedKeysAvailable) {
        return (int) Math.ceil(LIMIT_SCALER * extraSignedKeysAvailable);
    }

    /**
     * Creates a PoolStats. Takes an {@Code AttestationPoolStatus} and calculates different
     * pieces of status to inform the caller if any action needs to be taken to reprovision the pool
     * and what action is needed in terms of keys to generate.
     *
     * @parameter pool the current status of the keypool in Keystore2
     * @parameter extraSignedKeysAvailable how many extra attested keys should ideally be available
     *                                     for assignment.
     * @return the PoolStats object describing higher level info about the state of the key pool.
     */
    public static PoolStats processPool(
            AttestationPoolStatus pool, int extraSignedKeysAvailable) {
        PoolStats stats = new PoolStats();
        stats.unattestedKeys = pool.total - pool.attested;
        stats.keysInUse = pool.attested - pool.unassigned;
        stats.idealTotalSignedKeys = stats.keysInUse + extraSignedKeysAvailable;
        // If nothing is expiring, and the amount of available unassigned keys is sufficient,
        // then do nothing. Otherwise, generate the complete amount of idealTotalSignedKeys. It will
        // reduce network usage if the app just provisions an entire new batch in one go, rather
        // than consistently grabbing just a few at a time as the expiration dates become
        // misaligned.
        stats.provisioningNeeded =
                pool.unassigned - pool.expiring
                <= calcMinUnassignedToTriggerProvisioning(extraSignedKeysAvailable);
        if (!stats.provisioningNeeded) {
            Log.i(TAG, "Sufficient keys are available, no CSR needed.");
            stats.keysToGenerate = 0;
        } else {
            stats.keysToGenerate = Math.max(0, stats.idealTotalSignedKeys - stats.unattestedKeys);
        }
        return stats;
    }

    public static class PoolStats {
        public int unattestedKeys;
        public int keysInUse;
        public int idealTotalSignedKeys;
        public int keysToGenerate;
        public boolean provisioningNeeded = true;
    }
}
