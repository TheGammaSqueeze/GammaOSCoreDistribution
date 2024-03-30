/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.library.data;

import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY;
import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SERVICE;
import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SHORTCUT;
import static com.android.tv.settings.library.ManagerUtil.STATE_ACCESSIBILITY_SHORTCUT_SERVICE;
import static com.android.tv.settings.library.ManagerUtil.STATE_ADVANCED_DISPLAY;
import static com.android.tv.settings.library.ManagerUtil.STATE_ADVANCED_VOLUME;
import static com.android.tv.settings.library.ManagerUtil.STATE_ALARMS_AND_REMINDERS;
import static com.android.tv.settings.library.ManagerUtil.STATE_ALL_APPS;
import static com.android.tv.settings.library.ManagerUtil.STATE_APPS;
import static com.android.tv.settings.library.ManagerUtil.STATE_APP_MANAGEMENT;
import static com.android.tv.settings.library.ManagerUtil.STATE_APP_USAGE_ACCESS;
import static com.android.tv.settings.library.ManagerUtil.STATE_AVAILABLE_KEYBOARD;
import static com.android.tv.settings.library.ManagerUtil.STATE_DAYDREAM;
import static com.android.tv.settings.library.ManagerUtil.STATE_DISPLAY_SOUND;
import static com.android.tv.settings.library.ManagerUtil.STATE_ENERGY_SAVER;
import static com.android.tv.settings.library.ManagerUtil.STATE_FONT_SCALE;
import static com.android.tv.settings.library.ManagerUtil.STATE_HDR_FORMAT_SELECTION;
import static com.android.tv.settings.library.ManagerUtil.STATE_HIGH_POWER;
import static com.android.tv.settings.library.ManagerUtil.STATE_KEYBOARD;
import static com.android.tv.settings.library.ManagerUtil.STATE_LANGUAGE;
import static com.android.tv.settings.library.ManagerUtil.STATE_LEGAL;
import static com.android.tv.settings.library.ManagerUtil.STATE_LOCATION;
import static com.android.tv.settings.library.ManagerUtil.STATE_MATCH_CONTENT_FRAME;
import static com.android.tv.settings.library.ManagerUtil.STATE_NETWORK;
import static com.android.tv.settings.library.ManagerUtil.STATE_NOTIFICATION_ACCESS;
import static com.android.tv.settings.library.ManagerUtil.STATE_PICTURE_IN_PICTURE;
import static com.android.tv.settings.library.ManagerUtil.STATE_PRIVACY;
import static com.android.tv.settings.library.ManagerUtil.STATE_RESOLUTION_SELECTION;
import static com.android.tv.settings.library.ManagerUtil.STATE_SENSOR;
import static com.android.tv.settings.library.ManagerUtil.STATE_SPECIAL_ACCESS;
import static com.android.tv.settings.library.ManagerUtil.STATE_STORAGE;
import static com.android.tv.settings.library.ManagerUtil.STATE_STORAGE_SUMMARY;
import static com.android.tv.settings.library.ManagerUtil.STATE_SYSTEM_ALERT_WINDOW;
import static com.android.tv.settings.library.ManagerUtil.STATE_SYSTEM_DATE_TIME;
import static com.android.tv.settings.library.ManagerUtil.STATE_WIFI_DETAILS;
import static com.android.tv.settings.library.ManagerUtil.STATE_WRITE_SETTINGS;

import android.content.Context;

import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.about.LegalState;
import com.android.tv.settings.library.accessibility.AccessibilityServiceState;
import com.android.tv.settings.library.accessibility.AccessibilityShortcutServiceState;
import com.android.tv.settings.library.accessibility.AccessibilityShortcutState;
import com.android.tv.settings.library.accessibility.AccessibilityState;
import com.android.tv.settings.library.device.apps.AllAppsState;
import com.android.tv.settings.library.device.apps.AppManagementState;
import com.android.tv.settings.library.device.apps.AppsState;
import com.android.tv.settings.library.device.apps.specialaccess.AlarmsAndRemindersState;
import com.android.tv.settings.library.device.apps.specialaccess.AppUsageAccessState;
import com.android.tv.settings.library.device.apps.specialaccess.HighPowerState;
import com.android.tv.settings.library.device.apps.specialaccess.NotificationAccessState;
import com.android.tv.settings.library.device.apps.specialaccess.PictureInPictureState;
import com.android.tv.settings.library.device.apps.specialaccess.SpecialAppAccessState;
import com.android.tv.settings.library.device.apps.specialaccess.SystemAlertWindowState;
import com.android.tv.settings.library.device.apps.specialaccess.WriteSettingsState;
import com.android.tv.settings.library.device.display.daydream.DaydreamState;
import com.android.tv.settings.library.device.display.daydream.EnergySaverState;
import com.android.tv.settings.library.device.display.displaysound.AdvancedDisplayState;
import com.android.tv.settings.library.device.display.displaysound.AdvancedVolumeState;
import com.android.tv.settings.library.device.display.displaysound.DisplaySoundState;
import com.android.tv.settings.library.device.display.displaysound.FontScaleState;
import com.android.tv.settings.library.device.display.displaysound.HdrFormatSelectionState;
import com.android.tv.settings.library.device.display.displaysound.MatchContentFrameRateState;
import com.android.tv.settings.library.device.display.displaysound.ResolutionSelectionState;
import com.android.tv.settings.library.device.storage.StorageState;
import com.android.tv.settings.library.device.storage.StorageSummaryState;
import com.android.tv.settings.library.inputmethod.AvailableVirtualKeyboadState;
import com.android.tv.settings.library.inputmethod.KeyboardState;
import com.android.tv.settings.library.network.NetworkState;
import com.android.tv.settings.library.network.WifiDetailsState;
import com.android.tv.settings.library.privacy.LocationState;
import com.android.tv.settings.library.privacy.PrivacyState;
import com.android.tv.settings.library.privacy.SensorState;
import com.android.tv.settings.library.system.DateTimeState;
import com.android.tv.settings.library.system.LanguageState;

/** Manager to handle creation and removal of the {@link State}. */
public class StateManager {
    private StateManager() {
    }

    public static State createState(
            Context context, int stateIdentifier, UIUpdateCallback uiUpdateCallback) {
        State state = null;
        switch (stateIdentifier) {
            case STATE_NETWORK:
                state = new NetworkState(context, uiUpdateCallback);
                break;
            case STATE_WIFI_DETAILS:
                state = new WifiDetailsState(context, uiUpdateCallback);
                break;
            case STATE_ALL_APPS:
                state = new AllAppsState(context, uiUpdateCallback);
                break;
            case STATE_APPS:
                state = new AppsState(context, uiUpdateCallback);
                break;
            case STATE_APP_MANAGEMENT:
                state = new AppManagementState(context, uiUpdateCallback);
                break;
            case STATE_SPECIAL_ACCESS:
                state = new SpecialAppAccessState(context, uiUpdateCallback);
                break;
            case STATE_HIGH_POWER:
                state = new HighPowerState(context, uiUpdateCallback);
                break;
            case STATE_NOTIFICATION_ACCESS:
                state = new NotificationAccessState(context, uiUpdateCallback);
                break;
            case STATE_APP_USAGE_ACCESS:
                state = new AppUsageAccessState(context, uiUpdateCallback);
                break;
            case STATE_PICTURE_IN_PICTURE:
                state = new PictureInPictureState(context, uiUpdateCallback);
                break;
            case STATE_ALARMS_AND_REMINDERS:
                state = new AlarmsAndRemindersState(context, uiUpdateCallback);
                break;
            case STATE_SYSTEM_ALERT_WINDOW:
                state = new SystemAlertWindowState(context, uiUpdateCallback);
                break;
            case STATE_WRITE_SETTINGS:
                state = new WriteSettingsState(context, uiUpdateCallback);
                break;
            case STATE_SYSTEM_DATE_TIME:
                state = new DateTimeState(context, uiUpdateCallback);
                break;
            case STATE_KEYBOARD:
                state = new KeyboardState(context, uiUpdateCallback);
                break;
            case STATE_AVAILABLE_KEYBOARD:
                state = new AvailableVirtualKeyboadState(context, uiUpdateCallback);
                break;
            case STATE_LANGUAGE:
                state = new LanguageState(context, uiUpdateCallback);
                break;
            case STATE_ACCESSIBILITY:
                state = new AccessibilityState(context, uiUpdateCallback);
                break;
            case STATE_ACCESSIBILITY_SERVICE:
                state = new AccessibilityServiceState(context, uiUpdateCallback);
                break;
            case STATE_ACCESSIBILITY_SHORTCUT:
                state = new AccessibilityShortcutState(context, uiUpdateCallback);
                break;
            case STATE_ACCESSIBILITY_SHORTCUT_SERVICE:
                state = new AccessibilityShortcutServiceState(context, uiUpdateCallback);
            case STATE_STORAGE:
                state = new StorageState(context, uiUpdateCallback);
                break;
            case STATE_STORAGE_SUMMARY:
                state = new StorageSummaryState(context, uiUpdateCallback);
                break;
            case STATE_ENERGY_SAVER:
                state = new EnergySaverState(context, uiUpdateCallback);
                break;
            case STATE_DAYDREAM:
                state = new DaydreamState(context, uiUpdateCallback);
                break;
            case STATE_DISPLAY_SOUND:
                state = new DisplaySoundState(context, uiUpdateCallback);
                break;
            case STATE_FONT_SCALE:
                state = new FontScaleState(context, uiUpdateCallback);
                break;
            case STATE_MATCH_CONTENT_FRAME:
                state = new MatchContentFrameRateState(context, uiUpdateCallback);
                break;
            case STATE_ADVANCED_DISPLAY:
                state = new AdvancedDisplayState(context, uiUpdateCallback);
                break;
            case STATE_ADVANCED_VOLUME:
                state = new AdvancedVolumeState(context, uiUpdateCallback);
                break;
            case STATE_HDR_FORMAT_SELECTION:
                state = new HdrFormatSelectionState(context, uiUpdateCallback);
                break;
            case STATE_LOCATION:
                state = new LocationState(context, uiUpdateCallback);
                break;
            case STATE_SENSOR:
                state = new SensorState(context, uiUpdateCallback);
                break;
            case STATE_PRIVACY:
                state = new PrivacyState(context, uiUpdateCallback);
                break;
            case STATE_RESOLUTION_SELECTION:
                state = new ResolutionSelectionState(context, uiUpdateCallback);
                break;
            case STATE_LEGAL:
                state = new LegalState(context, uiUpdateCallback);
                break;
            default:
                // no-op
        }
        return state;
    }
}
