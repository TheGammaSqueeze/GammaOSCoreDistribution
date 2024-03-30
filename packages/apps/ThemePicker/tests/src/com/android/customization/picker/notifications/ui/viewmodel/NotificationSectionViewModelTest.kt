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

package com.android.customization.picker.notifications.ui.viewmodel

import androidx.test.filters.SmallTest
import com.android.customization.picker.notifications.data.repository.NotificationsRepository
import com.android.customization.picker.notifications.domain.interactor.NotificationsInteractor
import com.android.customization.picker.notifications.domain.interactor.NotificationsSnapshotRestorer
import com.android.wallpaper.testing.FakeSecureSettingsRepository
import com.android.wallpaper.testing.FakeSnapshotStore
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
class NotificationSectionViewModelTest {

    private lateinit var underTest: NotificationSectionViewModel

    private lateinit var testScope: TestScope
    private lateinit var interactor: NotificationsInteractor

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        interactor =
            NotificationsInteractor(
                repository =
                    NotificationsRepository(
                        scope = testScope.backgroundScope,
                        backgroundDispatcher = testDispatcher,
                        secureSettingsRepository = FakeSecureSettingsRepository(),
                    ),
                snapshotRestorer = {
                    NotificationsSnapshotRestorer(
                            interactor = interactor,
                        )
                        .apply { runBlocking { setUpSnapshotRestorer(FakeSnapshotStore()) } }
                },
            )

        underTest =
            NotificationSectionViewModel(
                interactor = interactor,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggles back and forth`() =
        testScope.runTest {
            val subtitleStringResId = collectLastValue(underTest.subtitleStringResourceId)
            val isSwitchOn = collectLastValue(underTest.isSwitchOn)

            val initialSubtitleStringRes = subtitleStringResId()
            val initialIsSwitchOn = isSwitchOn()

            underTest.onClicked()
            assertThat(subtitleStringResId()).isNotEqualTo(initialSubtitleStringRes)
            assertThat(isSwitchOn()).isNotEqualTo(initialIsSwitchOn)

            underTest.onClicked()
            assertThat(subtitleStringResId()).isEqualTo(initialSubtitleStringRes)
            assertThat(isSwitchOn()).isEqualTo(initialIsSwitchOn)
        }
}
