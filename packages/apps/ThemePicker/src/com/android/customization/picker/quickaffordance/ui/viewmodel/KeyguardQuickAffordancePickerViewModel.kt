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

package com.android.customization.picker.quickaffordance.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordancePickerInteractor
import com.android.systemui.shared.customization.data.content.CustomizationProviderContract
import com.android.systemui.shared.keyguard.shared.model.KeyguardQuickAffordanceSlots
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardQuickAffordancePreviewConstants
import com.android.wallpaper.R
import com.android.wallpaper.module.CurrentWallpaperInfoFactory
import com.android.wallpaper.picker.common.button.ui.viewmodel.ButtonStyle
import com.android.wallpaper.picker.common.button.ui.viewmodel.ButtonViewModel
import com.android.wallpaper.picker.common.dialog.ui.viewmodel.DialogViewModel
import com.android.wallpaper.picker.common.icon.ui.viewmodel.Icon
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Models UI state for a lock screen quick affordance picker experience. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyguardQuickAffordancePickerViewModel
private constructor(
    context: Context,
    private val quickAffordanceInteractor: KeyguardQuickAffordancePickerInteractor,
    private val wallpaperInfoFactory: CurrentWallpaperInfoFactory,
    activityStarter: (Intent) -> Unit,
) : ViewModel() {

    @SuppressLint("StaticFieldLeak") private val applicationContext = context.applicationContext

    val preview =
        ScreenPreviewViewModel(
            previewUtils =
                PreviewUtils(
                    context = applicationContext,
                    authority =
                        applicationContext.getString(
                            R.string.lock_screen_preview_provider_authority,
                        ),
                ),
            initialExtrasProvider = {
                Bundle().apply {
                    putString(
                        KeyguardQuickAffordancePreviewConstants.KEY_INITIALLY_SELECTED_SLOT_ID,
                        selectedSlotId.value,
                    )
                    putBoolean(
                        KeyguardQuickAffordancePreviewConstants.KEY_HIGHLIGHT_QUICK_AFFORDANCES,
                        true,
                    )
                }
            },
            wallpaperInfoProvider = {
                suspendCancellableCoroutine { continuation ->
                    wallpaperInfoFactory.createCurrentWallpaperInfos(
                        { homeWallpaper, lockWallpaper, _ ->
                            continuation.resume(lockWallpaper ?: homeWallpaper, null)
                        },
                        /* forceRefresh= */ true,
                    )
                }
            },
        )

    /** A locally-selected slot, if the user ever switched from the original one. */
    private val _selectedSlotId = MutableStateFlow<String?>(null)
    /** The ID of the selected slot. */
    val selectedSlotId: StateFlow<String> =
        combine(
                quickAffordanceInteractor.slots,
                _selectedSlotId,
            ) { slots, selectedSlotIdOrNull ->
                if (selectedSlotIdOrNull != null) {
                    slots.first { slot -> slot.id == selectedSlotIdOrNull }
                } else {
                    // If we haven't yet selected a new slot locally, default to the first slot.
                    slots[0]
                }
            }
            .map { selectedSlot -> selectedSlot.id }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = "",
            )

    /** View-models for each slot, keyed by slot ID. */
    val slots: Flow<Map<String, KeyguardQuickAffordanceSlotViewModel>> =
        combine(
            quickAffordanceInteractor.slots,
            quickAffordanceInteractor.affordances,
            quickAffordanceInteractor.selections,
            selectedSlotId,
        ) { slots, affordances, selections, selectedSlotId ->
            slots.associate { slot ->
                val selectedAffordanceIds =
                    selections
                        .filter { selection -> selection.slotId == slot.id }
                        .map { selection -> selection.affordanceId }
                        .toSet()
                val selectedAffordances =
                    affordances.filter { affordance ->
                        selectedAffordanceIds.contains(affordance.id)
                    }
                val isSelected = selectedSlotId == slot.id
                slot.id to
                    KeyguardQuickAffordanceSlotViewModel(
                        name = getSlotName(slot.id),
                        isSelected = isSelected,
                        selectedQuickAffordances =
                            selectedAffordances.map { affordanceModel ->
                                OptionItemViewModel<Icon>(
                                    key =
                                        MutableStateFlow("${slot.id}::${affordanceModel.id}")
                                            as StateFlow<String>,
                                    payload =
                                        Icon.Loaded(
                                            drawable =
                                                getAffordanceIcon(affordanceModel.iconResourceId),
                                            contentDescription = null,
                                        ),
                                    text = Text.Loaded(affordanceModel.name),
                                    isSelected = MutableStateFlow(true) as StateFlow<Boolean>,
                                    onClicked = flowOf(null),
                                    onLongClicked = null,
                                    isEnabled = true,
                                )
                            },
                        maxSelectedQuickAffordances = slot.maxSelectedQuickAffordances,
                        onClicked =
                            if (isSelected) {
                                null
                            } else {
                                { _selectedSlotId.tryEmit(slot.id) }
                            },
                    )
            }
        }

    /**
     * The set of IDs of the currently-selected affordances. These change with user selection of new
     * or different affordances in the currently-selected slot or when slot selection changes.
     */
    private val selectedAffordanceIds: Flow<Set<String>> =
        combine(
                quickAffordanceInteractor.selections,
                selectedSlotId,
            ) { selections, selectedSlotId ->
                selections
                    .filter { selection -> selection.slotId == selectedSlotId }
                    .map { selection -> selection.affordanceId }
                    .toSet()
            }
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                replay = 1,
            )

    /** The list of all available quick affordances for the selected slot. */
    val quickAffordances: Flow<List<OptionItemViewModel<Icon>>> =
        quickAffordanceInteractor.affordances.map { affordances ->
            val isNoneSelected = selectedAffordanceIds.map { it.isEmpty() }.stateIn(viewModelScope)
            listOf(
                none(
                    slotId = selectedSlotId,
                    isSelected = isNoneSelected,
                    onSelected =
                        combine(
                            isNoneSelected,
                            selectedSlotId,
                        ) { isSelected, selectedSlotId ->
                            if (!isSelected) {
                                {
                                    viewModelScope.launch {
                                        quickAffordanceInteractor.unselectAll(selectedSlotId)
                                    }
                                }
                            } else {
                                null
                            }
                        }
                )
            ) +
                affordances.map { affordance ->
                    val affordanceIcon = getAffordanceIcon(affordance.iconResourceId)
                    val isSelectedFlow: StateFlow<Boolean> =
                        selectedAffordanceIds
                            .map { it.contains(affordance.id) }
                            .stateIn(viewModelScope)
                    OptionItemViewModel<Icon>(
                        key =
                            selectedSlotId
                                .map { slotId -> "$slotId::${affordance.id}" }
                                .stateIn(viewModelScope),
                        payload = Icon.Loaded(drawable = affordanceIcon, contentDescription = null),
                        text = Text.Loaded(affordance.name),
                        isSelected = isSelectedFlow,
                        onClicked =
                            if (affordance.isEnabled) {
                                combine(
                                    isSelectedFlow,
                                    selectedSlotId,
                                ) { isSelected, selectedSlotId ->
                                    if (!isSelected) {
                                        {
                                            viewModelScope.launch {
                                                quickAffordanceInteractor.select(
                                                    slotId = selectedSlotId,
                                                    affordanceId = affordance.id,
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    }
                                }
                            } else {
                                flowOf {
                                    showEnablementDialog(
                                        icon = affordanceIcon,
                                        name = affordance.name,
                                        instructions = affordance.enablementInstructions,
                                        actionText = affordance.enablementActionText,
                                        actionComponentName =
                                            affordance.enablementActionComponentName,
                                    )
                                }
                            },
                        onLongClicked =
                            if (affordance.configureIntent != null) {
                                { activityStarter(affordance.configureIntent) }
                            } else {
                                null
                            },
                        isEnabled = affordance.isEnabled,
                    )
                }
        }

    @SuppressLint("UseCompatLoadingForDrawables")
    val summary: Flow<KeyguardQuickAffordanceSummaryViewModel> =
        slots.map { slots ->
            val icon2 =
                (slots[KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END]
                        ?.selectedQuickAffordances
                        ?.firstOrNull())
                    ?.payload
            val icon1 =
                (slots[KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START]
                        ?.selectedQuickAffordances
                        ?.firstOrNull())
                    ?.payload

            KeyguardQuickAffordanceSummaryViewModel(
                description = toDescriptionText(context, slots),
                icon1 = icon1
                        ?: if (icon2 == null) {
                            Icon.Resource(
                                res = R.drawable.link_off,
                                contentDescription = null,
                            )
                        } else {
                            null
                        },
                icon2 = icon2,
            )
        }

    private val _dialog = MutableStateFlow<DialogViewModel?>(null)
    /**
     * The current dialog to show. If `null`, no dialog should be shown.
     *
     * When the dialog is dismissed, [onDialogDismissed] must be called.
     */
    val dialog: Flow<DialogViewModel?> = _dialog.asStateFlow()

    /** Notifies that the dialog has been dismissed in the UI. */
    fun onDialogDismissed() {
        _dialog.value = null
    }

    private fun showEnablementDialog(
        icon: Drawable,
        name: String,
        instructions: List<String>,
        actionText: String?,
        actionComponentName: String?,
    ) {
        _dialog.value =
            DialogViewModel(
                icon =
                    Icon.Loaded(
                        drawable = icon,
                        contentDescription = null,
                    ),
                title = Text.Loaded(name),
                message =
                    Text.Loaded(
                        buildString {
                            instructions.forEachIndexed { index, instruction ->
                                if (index > 0) {
                                    append('\n')
                                }

                                append(instruction)
                            }
                        }
                    ),
                buttons =
                    listOf(
                        ButtonViewModel(
                            text = actionText?.let { Text.Loaded(actionText) }
                                    ?: Text.Resource(
                                        R.string
                                            .keyguard_affordance_enablement_dialog_dismiss_button,
                                    ),
                            style = ButtonStyle.Primary,
                            onClicked = {
                                actionComponentName.toIntent()?.let { intent ->
                                    applicationContext.startActivity(intent)
                                }
                            }
                        ),
                    ),
            )
    }

    /** Returns a view-model for the special "None" option. */
    @SuppressLint("UseCompatLoadingForDrawables")
    private suspend fun none(
        slotId: StateFlow<String>,
        isSelected: StateFlow<Boolean>,
        onSelected: Flow<(() -> Unit)?>,
    ): OptionItemViewModel<Icon> {
        return OptionItemViewModel<Icon>(
            key = slotId.map { "$it::none" }.stateIn(viewModelScope),
            payload = Icon.Resource(res = R.drawable.link_off, contentDescription = null),
            text = Text.Resource(res = R.string.keyguard_affordance_none),
            isSelected = isSelected,
            onClicked = onSelected,
            onLongClicked = null,
            isEnabled = true,
        )
    }

    private fun getSlotName(slotId: String): String {
        return applicationContext.getString(
            when (slotId) {
                KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START ->
                    R.string.keyguard_slot_name_bottom_start
                KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END ->
                    R.string.keyguard_slot_name_bottom_end
                else -> error("No name for slot with ID of \"$slotId\"!")
            }
        )
    }

    private suspend fun getAffordanceIcon(@DrawableRes iconResourceId: Int): Drawable {
        return quickAffordanceInteractor.getAffordanceIcon(iconResourceId)
    }

    private fun String?.toIntent(): Intent? {
        if (isNullOrEmpty()) {
            return null
        }

        val splitUp =
            split(
                CustomizationProviderContract.LockScreenQuickAffordances.AffordanceTable
                    .COMPONENT_NAME_SEPARATOR
            )
        check(splitUp.size == 1 || splitUp.size == 2) {
            "Illegal component name \"$this\". Must be either just an action or a package and an" +
                " action separated by a" +
                " \"${CustomizationProviderContract.LockScreenQuickAffordances.AffordanceTable.COMPONENT_NAME_SEPARATOR}\"!"
        }

        return Intent(splitUp.last()).apply {
            if (splitUp.size > 1) {
                setPackage(splitUp[0])
            }
        }
    }

    private fun toDescriptionText(
        context: Context,
        slots: Map<String, KeyguardQuickAffordanceSlotViewModel>,
    ): Text {
        val bottomStartAffordanceName =
            slots[KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_START]
                ?.selectedQuickAffordances
                ?.firstOrNull()
                ?.text
        val bottomEndAffordanceName =
            slots[KeyguardQuickAffordanceSlots.SLOT_ID_BOTTOM_END]
                ?.selectedQuickAffordances
                ?.firstOrNull()
                ?.text

        return when {
            bottomStartAffordanceName != null && bottomEndAffordanceName != null -> {
                Text.Loaded(
                    context.getString(
                        R.string.keyguard_quick_affordance_two_selected_template,
                        bottomStartAffordanceName.asString(context),
                        bottomEndAffordanceName.asString(context),
                    )
                )
            }
            bottomStartAffordanceName != null -> bottomStartAffordanceName
            bottomEndAffordanceName != null -> bottomEndAffordanceName
            else -> Text.Resource(R.string.keyguard_quick_affordance_none_selected)
        }
    }

    class Factory(
        private val context: Context,
        private val quickAffordanceInteractor: KeyguardQuickAffordancePickerInteractor,
        private val wallpaperInfoFactory: CurrentWallpaperInfoFactory,
        private val activityStarter: (Intent) -> Unit,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return KeyguardQuickAffordancePickerViewModel(
                context = context,
                quickAffordanceInteractor = quickAffordanceInteractor,
                wallpaperInfoFactory = wallpaperInfoFactory,
                activityStarter = activityStarter,
            )
                as T
        }
    }
}
