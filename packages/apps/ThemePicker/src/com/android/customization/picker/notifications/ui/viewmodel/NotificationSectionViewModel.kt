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

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.customization.picker.notifications.domain.interactor.NotificationsInteractor
import com.android.wallpaper.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Models UI state for a section that lets the user control the notification settings. */
class NotificationSectionViewModel
@VisibleForTesting
constructor(
    private val interactor: NotificationsInteractor,
) : ViewModel() {

    /** A string resource ID for the subtitle. */
    @StringRes
    val subtitleStringResourceId: Flow<Int> =
        interactor.settings.map { model ->
            when (model.isShowNotificationsOnLockScreenEnabled) {
                true -> R.string.show_notifications_on_lock_screen
                false -> R.string.hide_notifications_on_lock_screen
            }
        }

    /** Whether the switch should be on. */
    val isSwitchOn: Flow<Boolean> =
        interactor.settings.map { model -> model.isShowNotificationsOnLockScreenEnabled }

    /** Notifies that the section has been clicked. */
    fun onClicked() {
        viewModelScope.launch { interactor.toggleShowNotificationsOnLockScreenEnabled() }
    }

    class Factory(
        private val interactor: NotificationsInteractor,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificationSectionViewModel(
                interactor = interactor,
            )
                as T
        }
    }
}
