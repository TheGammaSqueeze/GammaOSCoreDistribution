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

package com.android.car.settings.location;

import static android.car.hardware.power.PowerComponent.LOCATION;
import static android.os.UserManager.DISALLOW_CONFIG_LOCATION;
import static android.os.UserManager.DISALLOW_SHARE_LOCATION;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;

import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PowerPolicyListener;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;

/**
 * Enables/disables ADAS (Advanced Driver-assistance systems) GNSS bypass via SwitchPreference.
 */
public class AdasLocationSwitchPreferenceController extends
        PreferenceController<CarUiTwoActionSwitchPreference> {
    private final Context mContext;
    private final LocationManager mLocationManager;

    private final BroadcastReceiver mAdasReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    private final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Turns Driver assistance on when main location switch is on. Location service don't
            // support the case where main location switch on and Driver assistance off
            if (mLocationManager.isLocationEnabled()) {
                mLocationManager.setAdasGnssLocationEnabled(true);
            }
            refreshUi();
        }
    };

    private static final IntentFilter INTENT_FILTER_ADAS_GNSS_ENABLED_CHANGED = new IntentFilter(
            LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED);

    private static final IntentFilter INTENT_FILTER_LOCATION_MODE_CHANGED = new IntentFilter(
            LocationManager.MODE_CHANGED_ACTION);
    @VisibleForTesting
    final PowerPolicyListener mPowerPolicyListener;

    public AdasLocationSwitchPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mContext = context;
        mLocationManager = context.getSystemService(LocationManager.class);
        mPowerPolicyListener = new PowerPolicyListener(context, LOCATION,
                isOn -> {
                    handlePowerPolicyChange(getPreference(), isOn);
                });
    }

    @Override
    protected Class<CarUiTwoActionSwitchPreference> getPreferenceType() {
        return CarUiTwoActionSwitchPreference.class;
    }

    @Override
    protected int getAvailabilityStatus() {
        if (hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_LOCATION)
                || hasUserRestrictionByDpm(getContext(), DISALLOW_SHARE_LOCATION)) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    @Override
    protected void updateState(CarUiTwoActionSwitchPreference preference) {
        updateSwitchPreference(preference, mLocationManager.isAdasGnssLocationEnabled());
    }

    @Override
    protected void onCreateInternal() {
        getPreference().setOnSecondaryActionClickListener(isChecked -> {
            if (!isChecked) {
                getFragmentController().showDialog(getConfirmationDialog(),
                        ConfirmationDialogFragment.TAG);
                refreshUi();
            } else {
                mLocationManager.setAdasGnssLocationEnabled(true);
            }
        });
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            if (hasUserRestrictionByDpm(getContext(), DISALLOW_SHARE_LOCATION)) {
                showActionDisabledByAdminDialog(DISALLOW_SHARE_LOCATION);
                return;
            }
            if (hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_LOCATION)) {
                showActionDisabledByAdminDialog(DISALLOW_CONFIG_LOCATION);
                return;
            }
        });
    }

    @Override
    protected void onStartInternal() {
        mContext.registerReceiver(mAdasReceiver, INTENT_FILTER_ADAS_GNSS_ENABLED_CHANGED,
                Context.RECEIVER_NOT_EXPORTED);
        mContext.registerReceiver(mLocationReceiver, INTENT_FILTER_LOCATION_MODE_CHANGED,
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResumeInternal() {
        mPowerPolicyListener.handleCurrentPolicy();
    }

    @Override
    protected void onStopInternal() {
        mContext.unregisterReceiver(mAdasReceiver);
        mContext.unregisterReceiver(mLocationReceiver);
    }

    @Override
    protected void onDestroyInternal() {
        mPowerPolicyListener.release();
    }

    private void updateSwitchPreference(CarUiTwoActionSwitchPreference preference,
            boolean enabled) {
        if (enabled && hasUserRestrictionByDpm(getContext(), DISALLOW_SHARE_LOCATION)) {
            preference.setSecondaryActionChecked(false);
            preference.setSecondaryActionEnabled(false);
        } else {
            preference.setSecondaryActionChecked(enabled);
            preference.setSecondaryActionEnabled(!mLocationManager.isLocationEnabled());
        }
    }

    private void handlePowerPolicyChange(CarUiTwoActionSwitchPreference preference,
            boolean enabled) {
        if (hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_LOCATION)
                || hasUserRestrictionByDpm(getContext(), DISALLOW_SHARE_LOCATION)
                ||  mLocationManager.isLocationEnabled()) {
            preference.setSecondaryActionEnabled(false);
            return;
        }
        preference.setSecondaryActionEnabled(enabled);
    }

    /**
     * Assigns confirm action as negative button listener and cancel action as positive button
     * listener, because the UX design requires the cancel button has to be on right and the confirm
     * button on left.
     */
    private ConfirmationDialogFragment getConfirmationDialog() {
        return new ConfirmationDialogFragment.Builder(getContext())
                .setMessage(mContext
                        .getString(R.string.location_driver_assistance_toggle_off_warning))
                .setNegativeButton(mContext
                        .getString(R.string.driver_assistance_warning_confirm_label), arguments -> {
                                mLocationManager.setAdasGnssLocationEnabled(false);
                        })
                .setPositiveButton(android.R.string.cancel,
                        /* rejectListener= */ null)
                .build();
    }

    private void showActionDisabledByAdminDialog(String restrictionType) {
        getFragmentController().showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(getContext(),
                        restrictionType),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }
}
