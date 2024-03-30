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

package android.nearby.integration.untrusted

import android.content.Context
import android.content.ContentResolver
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith


@RunWith(AndroidJUnit4::class)
class FastPairSettingsProviderTest {
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setUp() {
        contentResolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    }

    /** Verify untrusted app can read Fast Pair scan enabled setting. */
    @Test
    fun testSettingsFastPairScan_fromUnTrustedApp_readsSucceed() {
        Settings.Secure.getInt(contentResolver,
                "fast_pair_scan_enabled", /* default value */ -1)
    }

    /** Verify untrusted app can't write Fast Pair scan enabled setting. */
    @Test
    fun testSettingsFastPairScan_fromUnTrustedApp_writesFailed() {
        assertFailsWith<SecurityException> {
            Settings.Secure.putInt(contentResolver, "fast_pair_scan_enabled", 1)
        }
    }
}
