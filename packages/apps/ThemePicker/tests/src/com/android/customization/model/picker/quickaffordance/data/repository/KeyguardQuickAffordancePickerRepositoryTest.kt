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
 *
 */

package com.android.customization.model.picker.quickaffordance.data.repository

import androidx.test.filters.SmallTest
import com.android.customization.picker.quickaffordance.data.repository.KeyguardQuickAffordancePickerRepository
import com.android.systemui.shared.customization.data.content.CustomizationProviderContract
import com.android.systemui.shared.customization.data.content.FakeCustomizationProviderClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class KeyguardQuickAffordancePickerRepositoryTest {

    private lateinit var underTest: KeyguardQuickAffordancePickerRepository

    private lateinit var testScope: TestScope
    private lateinit var client: FakeCustomizationProviderClient

    @Before
    fun setUp() {
        client = FakeCustomizationProviderClient()
        val coroutineDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(coroutineDispatcher)
        Dispatchers.setMain(coroutineDispatcher)

        underTest =
            KeyguardQuickAffordancePickerRepository(
                client = client,
                backgroundDispatcher = coroutineDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isFeatureEnabled - enabled`() =
        testScope.runTest {
            client.setFlag(
                CustomizationProviderContract.FlagsTable
                    .FLAG_NAME_CUSTOM_LOCK_SCREEN_QUICK_AFFORDANCES_ENABLED,
                true,
            )
            val values = mutableListOf<Boolean>()
            val job = launch { underTest.isFeatureEnabled.toList(values) }

            assertThat(values.last()).isTrue()

            job.cancel()
        }

    @Test
    fun `isFeatureEnabled - not enabled`() =
        testScope.runTest {
            client.setFlag(
                CustomizationProviderContract.FlagsTable
                    .FLAG_NAME_CUSTOM_LOCK_SCREEN_QUICK_AFFORDANCES_ENABLED,
                false,
            )
            val values = mutableListOf<Boolean>()
            val job = launch { underTest.isFeatureEnabled.toList(values) }

            assertThat(values.last()).isFalse()

            job.cancel()
        }
}
