/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import static com.android.cellbroadcastreceiver.CellBroadcastReceiver.VDBG;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager.CellBroadcastChannelRange;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This service manages enabling and disabling ranges of message identifiers
 * that the radio should listen for. It operates independently of the other
 * services and runs at boot time and after exiting airplane mode.
 *
 * Note that the entire range of emergency channels is enabled. Test messages
 * and lower priority broadcasts are filtered out in CellBroadcastAlertService
 * if the user has not enabled them in settings.
 *
 * TODO: add notification to re-enable channels after a radio reset.
 */
public class CellBroadcastConfigService extends IntentService {
    private static final String TAG = "CellBroadcastConfigService";

    @VisibleForTesting
    public static final String ACTION_ENABLE_CHANNELS = "ACTION_ENABLE_CHANNELS";
    public static final String ACTION_UPDATE_SETTINGS_FOR_CARRIER = "UPDATE_SETTINGS_FOR_CARRIER";

    public CellBroadcastConfigService() {
        super(TAG);          // use class name for worker thread name
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ACTION_ENABLE_CHANNELS.equals(intent.getAction())) {
            try {
                SubscriptionManager subManager = (SubscriptionManager) getApplicationContext()
                        .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

                if (subManager != null) {
                    // Retrieve all the active subscription indice and enable cell broadcast
                    // messages on all subs. The duplication detection will be done at the
                    // frameworks.
                    int[] subIds = getActiveSubIdList(subManager);
                    if (subIds.length != 0) {
                        for (int subId : subIds) {
                            log("Enable CellBroadcast on sub " + subId);
                            enableCellBroadcastChannels(subId);
                            enableCellBroadcastRoamingChannelsAsNeeded(subId);
                        }
                    } else {
                        // For no sim scenario.
                        enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
                        enableCellBroadcastRoamingChannelsAsNeeded(
                                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "exception enabling cell broadcast channels", ex);
            }
        } else if (ACTION_UPDATE_SETTINGS_FOR_CARRIER.equals(intent.getAction())) {
            Context c = getApplicationContext();
            if (CellBroadcastSettings.hasAnyPreferenceChanged(c)) {
                Log.d(TAG, "Preference has changed from user set, posting notification.");

                CellBroadcastAlertService.createNotificationChannels(c);
                Intent settingsIntent = new Intent(c, CellBroadcastSettings.class);
                PendingIntent pi = PendingIntent.getActivity(c,
                        CellBroadcastAlertService.SETTINGS_CHANGED_NOTIFICATION_ID, settingsIntent,
                        PendingIntent.FLAG_ONE_SHOT
                                | PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE);

                Notification.Builder builder = new Notification.Builder(c,
                        CellBroadcastAlertService.NOTIFICATION_CHANNEL_SETTINGS_UPDATES)
                        .setCategory(Notification.CATEGORY_SYSTEM)
                        .setContentTitle(c.getString(R.string.notification_cb_settings_changed_title))
                        .setContentText(c.getString(R.string.notification_cb_settings_changed_text))
                        .setSmallIcon(R.drawable.ic_settings_gear_outline_24dp)
                        .setContentIntent(pi)
                        .setAutoCancel(true);
                NotificationManager notificationManager = c.getSystemService(
                        NotificationManager.class);
                notificationManager.notify(
                        CellBroadcastAlertService.SETTINGS_CHANGED_NOTIFICATION_ID,
                        builder.build());
            }
            Log.e(TAG, "Reset all preferences");
            CellBroadcastSettings.resetAllPreferences(getApplicationContext());
        }
    }

    @NonNull
    private int[] getActiveSubIdList(SubscriptionManager subMgr) {
        List<SubscriptionInfo> subInfos = subMgr.getActiveSubscriptionInfoList();
        int size = subInfos != null ? subInfos.size() : 0;
        int[] subIds = new int[size];
        for (int i = 0; i < size; i++) {
            subIds[i] = subInfos.get(i).getSubscriptionId();
        }
        return subIds;
    }

    private void resetCellBroadcastChannels(int subId) {
        SmsManager manager;
        if (subId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            manager = SmsManager.getSmsManagerForSubscriptionId(subId);
        } else {
            manager = SmsManager.getDefault();
        }
        // SmsManager.resetAllCellBroadcastRanges is a new @SystemAPI in S. We need to support
        // backward compatibility as the module need to run on R build as well.
        if (SdkLevel.isAtLeastS()) {
            manager.resetAllCellBroadcastRanges();
        } else {
            try {
                Method method = SmsManager.class.getDeclaredMethod("resetAllCellBroadcastRanges");
                method.invoke(manager);
            } catch (Exception e) {
                log("Can't reset cell broadcast ranges. e=" + e);
            }
        }
    }

    /**
     * Enable cell broadcast messages channels. Messages can be only received on the
     * enabled channels.
     *
     * @param subId Subscription index
     */
    @VisibleForTesting
    public void enableCellBroadcastChannels(int subId) {
        resetCellBroadcastChannels(subId);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources res = getResources(subId, null);

        // boolean for each user preference checkbox, true for checked, false for unchecked
        // Note: If enableAlertsMasterToggle is false, it disables ALL emergency broadcasts
        // except for always-on alerts e.g, presidential. i.e. to receive CMAS severe alerts, both
        // enableAlertsMasterToggle AND enableCmasSevereAlerts must be true.
        boolean enableAlertsMasterToggle = prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);

        boolean enableEtwsAlerts = enableAlertsMasterToggle;

        // CMAS Presidential must be always on (See 3GPP TS 22.268 Section 6.2) regardless
        // user's preference
        boolean enablePresidential = true;

        boolean enableCmasExtremeAlerts = enableAlertsMasterToggle && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);

        boolean enableCmasSevereAlerts = enableAlertsMasterToggle && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);

        boolean enableCmasAmberAlerts = enableAlertsMasterToggle && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);

        boolean enableTestAlerts = enableAlertsMasterToggle
                && CellBroadcastSettings.isTestAlertsToggleVisible(getApplicationContext())
                && prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_TEST_ALERTS, false);

        boolean enableExerciseAlerts = enableAlertsMasterToggle
                && res.getBoolean(R.bool.show_separate_exercise_settings)
                && prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_EXERCISE_ALERTS, false);

        boolean enableOperatorDefined = enableAlertsMasterToggle
                && res.getBoolean(R.bool.show_separate_operator_defined_settings)
                && prefs.getBoolean(CellBroadcastSettings.KEY_OPERATOR_DEFINED_ALERTS, false);

        boolean enableAreaUpdateInfoAlerts = res.getBoolean(
                R.bool.config_showAreaUpdateInfoSettings)
                && prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS,
                false);

        boolean enablePublicSafetyMessagesChannelAlerts = enableAlertsMasterToggle
                && prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_PUBLIC_SAFETY_MESSAGES,
                true);
        boolean enableStateLocalTestAlerts = enableAlertsMasterToggle
                && (prefs.getBoolean(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS,
                false)
                || (!res.getBoolean(R.bool.show_state_local_test_settings)
                && res.getBoolean(R.bool.state_local_test_alerts_enabled_default)));

        boolean enableEmergencyAlerts = enableAlertsMasterToggle && prefs.getBoolean(
                CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);

        setCellBroadcastChannelsEnabled(subId, null, enableAlertsMasterToggle, enableEtwsAlerts,
                enablePresidential, enableCmasExtremeAlerts, enableCmasSevereAlerts,
                enableCmasAmberAlerts, enableTestAlerts, enableExerciseAlerts,
                enableOperatorDefined, enableAreaUpdateInfoAlerts,
                enablePublicSafetyMessagesChannelAlerts, enableStateLocalTestAlerts,
                enableEmergencyAlerts, true);
    }

    private void setCellBroadcastChannelsEnabled(int subId, @NonNull String operator,
            boolean enableAlertsMasterToggle, boolean enableEtwsAlerts, boolean enablePresidential,
            boolean enableCmasExtremeAlerts, boolean enableCmasSevereAlerts,
            boolean enableCmasAmberAlerts, boolean enableTestAlerts, boolean enableExerciseAlerts,
            boolean enableOperatorDefined, boolean enableAreaUpdateInfoAlerts,
            boolean enablePublicSafetyMessagesChannelAlerts, boolean enableStateLocalTestAlerts,
            boolean enableEmergencyAlerts, boolean enableGeoFencingTriggerMessage) {

        if (VDBG) {
            log("setCellBroadcastChannelsEnabled for " + subId + ", operator: " + operator);
            log("enableAlertsMasterToggle = " + enableAlertsMasterToggle);
            log("enableEtwsAlerts = " + enableEtwsAlerts);
            log("enablePresidential = " + enablePresidential);
            log("enableCmasExtremeAlerts = " + enableCmasExtremeAlerts);
            log("enableCmasSevereAlerts = " + enableCmasSevereAlerts);
            log("enableCmasAmberAlerts = " + enableCmasAmberAlerts);
            log("enableTestAlerts = " + enableTestAlerts);
            log("enableExerciseAlerts = " + enableExerciseAlerts);
            log("enableOperatorDefinedAlerts = " + enableOperatorDefined);
            log("enableAreaUpdateInfoAlerts = " + enableAreaUpdateInfoAlerts);
            log("enablePublicSafetyMessagesChannelAlerts = "
                    + enablePublicSafetyMessagesChannelAlerts);
            log("enableStateLocalTestAlerts = " + enableStateLocalTestAlerts);
            log("enableEmergencyAlerts = " + enableEmergencyAlerts);
            log("enableGeoFencingTriggerMessage = " + enableGeoFencingTriggerMessage);
        }

        boolean isEnableOnly = !TextUtils.isEmpty(operator);
        CellBroadcastChannelManager channelManager = new CellBroadcastChannelManager(
                getApplicationContext(), subId, operator);

        /** Enable CMAS series messages. */

        // Enable/Disable Presidential messages.
        setCellBroadcastRange(subId, isEnableOnly, enablePresidential,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.cmas_presidential_alerts_channels_range_strings));

        // Enable/Disable CMAS extreme messages.
        setCellBroadcastRange(subId, isEnableOnly, enableCmasExtremeAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.cmas_alert_extreme_channels_range_strings));

        // Enable/Disable CMAS severe messages.
        setCellBroadcastRange(subId, isEnableOnly, enableCmasSevereAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.cmas_alerts_severe_range_strings));

        // Enable/Disable CMAS amber alert messages.
        setCellBroadcastRange(subId, isEnableOnly, enableCmasAmberAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.cmas_amber_alerts_channels_range_strings));

        // Enable/Disable test messages.
        setCellBroadcastRange(subId, isEnableOnly, enableTestAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.required_monthly_test_range_strings));

        // Enable/Disable exercise test messages.
        // This could either controlled by main test toggle or separate exercise test toggle.
        setCellBroadcastRange(subId, isEnableOnly, enableTestAlerts || enableExerciseAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.exercise_alert_range_strings));

        // Enable/Disable operator defined test messages.
        // This could either controlled by main test toggle or separate operator defined test toggle
        setCellBroadcastRange(subId, isEnableOnly, enableTestAlerts || enableOperatorDefined,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.operator_defined_alert_range_strings));

        // Enable/Disable GSM ETWS messages.
        setCellBroadcastRange(subId, isEnableOnly, enableEtwsAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.etws_alerts_range_strings));

        // Enable/Disable GSM ETWS test messages.
        setCellBroadcastRange(subId, isEnableOnly, enableTestAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.etws_test_alerts_range_strings));

        // Enable/Disable GSM public safety messages.
        setCellBroadcastRange(subId, isEnableOnly, enablePublicSafetyMessagesChannelAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.public_safety_messages_channels_range_strings));

        // Enable/Disable GSM state/local test alerts.
        setCellBroadcastRange(subId, isEnableOnly, enableStateLocalTestAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.state_local_test_alert_range_strings));

        // Enable/Disable GSM geo-fencing trigger messages.
        setCellBroadcastRange(subId, isEnableOnly, enableGeoFencingTriggerMessage,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.geo_fencing_trigger_messages_range_strings));

        // Enable non-CMAS series messages.
        setCellBroadcastRange(subId, isEnableOnly, enableEmergencyAlerts,
                channelManager.getCellBroadcastChannelRanges(
                        R.array.emergency_alerts_channels_range_strings));

        // Enable/Disable additional channels based on carrier specific requirement.
        List<CellBroadcastChannelRange> ranges =
                channelManager.getCellBroadcastChannelRanges(
                        R.array.additional_cbs_channels_strings);

        for (CellBroadcastChannelRange range: ranges) {
            boolean enableAlerts;
            switch (range.mAlertType) {
                case AREA:
                    enableAlerts = enableAreaUpdateInfoAlerts;
                    break;
                case TEST:
                    enableAlerts = enableTestAlerts;
                    break;
                default:
                    enableAlerts = enableAlertsMasterToggle;
            }
            setCellBroadcastRange(subId, isEnableOnly, enableAlerts,
                    new ArrayList<>(Arrays.asList(range)));
        }
    }

    /**
     * Enable cell broadcast messages channels. Messages can be only received on the
     * enabled channels.
     *
     * @param subId Subscription index
     */
    @VisibleForTesting
    public void enableCellBroadcastRoamingChannelsAsNeeded(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            subId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
        }

        String roamingOperator = CellBroadcastReceiver.getRoamingOperatorSupported(this);
        if (roamingOperator.isEmpty()) {
            return;
        }

        log("enableCellBroadcastRoamingChannels for roaming network:" + roamingOperator);
        Resources res = getResources(subId, roamingOperator);

        // Get default config for roaming network as the settings are based on sim
        boolean enablePresidential = true;

        boolean enableAlertsMasterToggle = res.getBoolean(R.bool.master_toggle_enabled_default);

        boolean enableEtwsAlerts = enableAlertsMasterToggle;

        boolean enableCmasExtremeAlerts = enableAlertsMasterToggle && res.getBoolean(
                R.bool.extreme_threat_alerts_enabled_default);

        boolean enableCmasSevereAlerts = enableAlertsMasterToggle && res.getBoolean(
                R.bool.severe_threat_alerts_enabled_default);

        boolean enableCmasAmberAlerts = enableAlertsMasterToggle && res.getBoolean(
                R.bool.amber_alerts_enabled_default);

        boolean enableTestAlerts = enableAlertsMasterToggle && CellBroadcastSettings
                .isTestAlertsToggleVisible(getApplicationContext(), roamingOperator)
                && res.getBoolean(R.bool.test_alerts_enabled_default);

        boolean enableExerciseAlerts = enableAlertsMasterToggle
                && res.getBoolean(R.bool.show_separate_exercise_settings)
                && res.getBoolean(R.bool.test_exercise_alerts_enabled_default);

        boolean enableOperatorDefined = enableAlertsMasterToggle
                && res.getBoolean(R.bool.show_separate_operator_defined_settings)
                && res.getBoolean(R.bool.test_operator_defined_alerts_enabled_default);

        boolean enableAreaUpdateInfoAlerts = res.getBoolean(
                R.bool.config_showAreaUpdateInfoSettings)
                && res.getBoolean(R.bool.area_update_info_alerts_enabled_default);

        boolean enablePublicSafetyMessagesChannelAlerts = enableAlertsMasterToggle
                && res.getBoolean(R.bool.public_safety_messages_enabled_default);
        boolean enableStateLocalTestAlerts = enableAlertsMasterToggle
                && res.getBoolean(R.bool.state_local_test_alerts_enabled_default);

        boolean enableEmergencyAlerts = enableAlertsMasterToggle && res.getBoolean(
                R.bool.emergency_alerts_enabled_default);

        setCellBroadcastChannelsEnabled(subId, roamingOperator, enableAlertsMasterToggle,
                enableEtwsAlerts, enablePresidential, enableCmasExtremeAlerts,
                enableCmasSevereAlerts, enableCmasAmberAlerts, enableTestAlerts,
                enableExerciseAlerts, enableOperatorDefined, enableAreaUpdateInfoAlerts,
                enablePublicSafetyMessagesChannelAlerts, enableStateLocalTestAlerts,
                enableEmergencyAlerts, true);
    }

    /**
     * Enable/disable cell broadcast with messages id range
     * @param subId Subscription index
     * @param isEnableOnly, True for enabling channel only for roaming network
     * @param enable True for enabling cell broadcast with id range, otherwise for disabling
     * @param ranges Cell broadcast id ranges
     */
    private void setCellBroadcastRange(int subId, boolean isEnableOnly,
            boolean enable, List<CellBroadcastChannelRange> ranges) {
        SmsManager manager;
        if (subId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            manager = SmsManager.getSmsManagerForSubscriptionId(subId);
        } else {
            manager = SmsManager.getDefault();
        }

        if (ranges != null) {
            for (CellBroadcastChannelRange range: ranges) {
                if (range.mAlwaysOn) {
                    log("mAlwaysOn is set to true, enable the range: " + range.mStartId
                            + ":" + range.mEndId);
                    enable = true;
                }

                if (enable) {
                    if (VDBG) {
                        log("enableCellBroadcastRange[" + range.mStartId + "-" + range.mEndId
                                + "], type:" + range.mRanType);
                    }
                    manager.enableCellBroadcastRange(range.mStartId, range.mEndId, range.mRanType);
                } else if (!isEnableOnly) {
                    if (VDBG) {
                        log("disableCellBroadcastRange[" + range.mStartId + "-" + range.mEndId
                                + "], type:" + range.mRanType);
                    }
                    manager.disableCellBroadcastRange(range.mStartId, range.mEndId, range.mRanType);
                }
            }
        }
    }


    /**
     * Get resource according to the operator or subId
     * @param subId Subscription index
     * @param operator Operator numeric, the resource will be retrieved by it if it is no null,
     * otherwise, by the sub id.
     */
    @VisibleForTesting
    public Resources getResources(int subId, String operator) {
        if (operator == null) {
            return CellBroadcastSettings.getResources(this, subId);
        }
        return CellBroadcastSettings.getResourcesByOperator(this, subId, operator);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
