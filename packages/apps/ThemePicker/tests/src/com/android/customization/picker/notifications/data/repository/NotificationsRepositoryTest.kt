/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.customization.picker.notifications.data.repository

import android.provider.Settings
import androidx.test.filters.SmallTest
import com.android.customization.picker.notifications.shared.model.NotificationSettingsModel
import com.android.wallpaper.testing.FakeSecureSettingsRepository
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(JUnit4::class)
class NotificationsRepositoryTest {

    private lateinit var underTest: NotificationsRepository

    private lateinit var testScope: TestScope
    private lateinit var secureSettingsRepository: FakeSecureSettingsRepository

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        secureSettingsRepository = FakeSecureSettingsRepository()

        underTest =
            NotificationsRepository(
                scope = testScope.backgroundScope,
                backgroundDispatcher = testDispatcher,
                secureSettingsRepository = secureSettingsRepository,
            )
    }

    @Test
    fun settings() =
        testScope.runTest {
            val settings = collectLastValue(underTest.settings)

            secureSettingsRepository.set(
                name = Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS,
                value = 1,
            )
            assertThat(settings())
                .isEqualTo(NotificationSettingsModel(isShowNotificationsOnLockScreenEnabled = true))

            secureSettingsRepository.set(
                name = Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS,
                value = 0,
            )
            assertThat(settings())
                .isEqualTo(
                    NotificationSettingsModel(isShowNotificationsOnLockScreenEnabled = false)
                )
        }

    @Test
    fun setSettings() =
        testScope.runTest {
            val settings = collectLastValue(underTest.settings)

            val model1 = NotificationSettingsModel(isShowNotificationsOnLockScreenEnabled = true)
            underTest.setSettings(model1)
            assertThat(settings()).isEqualTo(model1)

            val model2 = NotificationSettingsModel(isShowNotificationsOnLockScreenEnabled = false)
            underTest.setSettings(model2)
            assertThat(settings()).isEqualTo(model2)
        }
}
