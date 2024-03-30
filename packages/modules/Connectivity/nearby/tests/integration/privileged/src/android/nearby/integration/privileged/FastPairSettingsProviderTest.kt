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

package android.nearby.integration.privileged

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

data class FastPairSettingsFlag(val name: String, val value: Int) {
    override fun toString() = name
}

@RunWith(Parameterized::class)
class FastPairSettingsProviderTest(private val flag: FastPairSettingsFlag) {

    /** Verify privileged app can enable/disable Fast Pair scan. */
    @Test
    fun testSettingsFastPairScan_fromPrivilegedApp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val contentResolver = appContext.contentResolver

        Settings.Secure.putInt(contentResolver, "fast_pair_scan_enabled", flag.value)

        val actualValue = Settings.Secure.getInt(
                contentResolver, "fast_pair_scan_enabled", /* default value */ -1)
        assertThat(actualValue).isEqualTo(flag.value)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}Succeed")
        fun fastPairScanFlags() = listOf(
            FastPairSettingsFlag(name = "disable", value = 0),
            FastPairSettingsFlag(name = "enable", value = 1),
        )
    }
}
