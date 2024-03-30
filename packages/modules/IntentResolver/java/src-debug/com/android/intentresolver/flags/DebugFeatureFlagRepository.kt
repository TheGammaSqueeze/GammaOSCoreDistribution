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

package com.android.intentresolver.flags

import android.util.SparseBooleanArray
import androidx.annotation.GuardedBy
import com.android.systemui.flags.BooleanFlag
import com.android.systemui.flags.FlagManager
import com.android.systemui.flags.ReleasedFlag
import com.android.systemui.flags.UnreleasedFlag
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
internal class DebugFeatureFlagRepository(
    private val flagManager: FlagManager,
    private val deviceConfig: DeviceConfigProxy,
) : FeatureFlagRepository {
    @GuardedBy("self")
    private val cache = hashMapOf<String, Boolean>()

    override fun isEnabled(flag: UnreleasedFlag): Boolean = isFlagEnabled(flag)

    override fun isEnabled(flag: ReleasedFlag): Boolean = isFlagEnabled(flag)

    private fun isFlagEnabled(flag: BooleanFlag): Boolean {
        synchronized(cache) {
            cache[flag.name]?.let { return it }
        }
        val flagValue = readFlagValue(flag)
        return synchronized(cache) {
            // the first read saved in the cache wins
            cache.getOrPut(flag.name) { flagValue }
        }
    }

    private fun readFlagValue(flag: BooleanFlag): Boolean {
        val localOverride = runCatching {
            flagManager.isEnabled(flag.name)
        }.getOrDefault(null)
        val remoteOverride = deviceConfig.isEnabled(flag)

        // Only check for teamfood if the default is false
        // and there is no server override.
        if (remoteOverride == null
            && !flag.default
            && localOverride == null
            && !flag.isTeamfoodFlag
            && flag.teamfood
        ) {
            return flagManager.isTeamfoodEnabled
        }
        return localOverride ?: remoteOverride ?: flag.default
    }

    companion object {
        /** keep in sync with [com.android.systemui.flags.Flags] */
        private const val TEAMFOOD_FLAG_NAME = "teamfood"

        private val BooleanFlag.isTeamfoodFlag: Boolean
            get() = name == TEAMFOOD_FLAG_NAME

        private val FlagManager.isTeamfoodEnabled: Boolean
            get() = runCatching {
                isEnabled(TEAMFOOD_FLAG_NAME) ?: false
            }.getOrDefault(false)
    }
}
