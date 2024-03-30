/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.os.UserManager.DISALLOW_CONFIG_LOCATION;
import static android.os.UserManager.DISALLOW_SHARE_LOCATION;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LocationStateSwitchPreferenceControllerTest {
    private LifecycleOwner mLifecycleOwner;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private SwitchPreference mSwitchPreference;
    private LocationStateSwitchPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private UserHandle mUserHandle;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private LocationManager mLocationManager;
    @Mock
    private UserManager mUserManager;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mUserHandle = UserHandle.of(UserHandle.myUserId());

        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mPreferenceController = new LocationStateSwitchPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
    }

    @Test
    public void onIntentReceived_updateUi() {
        initializePreference(/* checked= */ false, /* enabled= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(broadcastReceiverArgumentCaptor.capture(), any());

        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        broadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(LocationManager.MODE_CHANGED_ACTION));
        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void onPreferenceClicked_locationDisabled_shouldEnable_notShowDialog() {
        initializePreference(/* checked= */ false, /* enabled= */ true);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isChecked()).isTrue();
        verify(mLocationManager).setLocationEnabledForUser(/* enabled= */ true, mUserHandle);
        verify(mFragmentController, never()).showDialog(any(ConfirmationDialogFragment.class),
                any());
    }

    @Test
    public void onPreferenceClicked_locationEnabled_shouldStayEnable_showDialog() {
        initializePreference(/* checked= */ true, /* enabled= */ true);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isChecked()).isTrue();
        verify(mLocationManager, never()).setLocationEnabledForUser(/* enabled= */ anyBoolean(),
                any());
        verify(mFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void confirmDialog_turnOffLocation() throws Throwable {
        initializePreference(/* checked= */ true, /* enabled= */ true);

        mSwitchPreference.performClick();

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mFragmentController).showDialog(dialogCaptor.capture(),
                eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mLocationManager).setLocationEnabledForUser(/* enabled= */ false,
                mUserHandle);
    }

    @Test
    public void cancelDialog_LocationStaysOn() throws Throwable {
        initializePreference(/* checked= */ true, /* enabled= */ true);

        mSwitchPreference.performClick();

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mFragmentController).showDialog(dialogCaptor.capture(),
                eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mLocationManager, never()).setLocationEnabledForUser(/* enabled= */ false,
                mUserHandle);
    }

    @Test
    public void onPolicyChanged_enabled_setsSwitchEnabled() {
        initializePreference(/* checked= */ false, /* enabled= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
    }

    @Test
    public void onPolicyChanged_disabled_setsSwitchDisabled() {
        initializePreference(/* checked= */ false, /* enabled= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ false);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void locationOn_disallowConfigLocation_interactionDisabled() {
        verifyLocationStateWithRestriction(/* locationOnByDefault= */true,
                DISALLOW_CONFIG_LOCATION, /* expectedLocationToBeOn= */ true);
    }

    @Test
    public void locationOff_disallowConfigLocation_interactionDisabled() {
        verifyLocationStateWithRestriction(/* locationOnByDefault= */false,
                DISALLOW_CONFIG_LOCATION, /* expectedLocationToBeOn= */ false);
    }

    @Test
    public void locationOn_disallowShareLocation_interactionDisabled_locationTurnedOff() {
        verifyLocationStateWithRestriction(/* locationOnByDefault= */true,
                DISALLOW_SHARE_LOCATION, /* expectedLocationToBeOn= */ false);
    }

    @Test
    public void locationOff_disallowShareLocation_interactionDisabled_locationRemainsOff() {
        verifyLocationStateWithRestriction(/* locationOnByDefault= */false,
                DISALLOW_SHARE_LOCATION, /* expectedLocationToBeOn= */ false);
    }

    public void verifyLocationStateWithRestriction(boolean locationOnByDefault, String restriction,
            boolean expectedLocationToBeOn) {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager,  restriction,
                /* restricted= */true);
        initializePreference(/* checked= */ locationOnByDefault, /* enabled= */ true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
        if (expectedLocationToBeOn) {
            assertThat(mSwitchPreference.isChecked()).isTrue();
        } else {
            assertThat(mSwitchPreference.isChecked()).isFalse();
        }
    }

    @Test
    public void disallowConfigLocation_onInteraction_showsDialog() {
        initializePreference(/* checked= */ true, /* enabled= */ true);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager,  DISALLOW_CONFIG_LOCATION,
                /* restricted= */true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    @Test
    public void disallowShareLocation_onInteraction_showsDialog() {
        initializePreference(/* checked= */ true, /* enabled= */ true);
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager,  DISALLOW_SHARE_LOCATION,
                /* restricted= */true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    private void initializePreference(boolean checked, boolean enabled) {
        when(mLocationManager.isLocationEnabled()).thenReturn(checked);
        mSwitchPreference.setChecked(checked);
        mSwitchPreference.setEnabled(enabled);
    }

    private AlertDialog showDialog(ConfirmationDialogFragment dialog) throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            dialog.show(mActivityTestRule.getActivity().getSupportFragmentManager(),
                    /* tag= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return (AlertDialog) dialog.getDialog();
    }
}
