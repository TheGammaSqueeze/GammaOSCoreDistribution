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

import static android.os.UserManager.DISALLOW_CONFIG_LOCATION;
import static android.os.UserManager.DISALLOW_SHARE_LOCATION;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AdasLocationSwitchPreferenceControllerTest {
    private LifecycleOwner mLifecycleOwner;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private CarUiTwoActionSwitchPreference mSwitchPreference;
    private AdasLocationSwitchPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

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

        mSwitchPreference = new CarUiTwoActionSwitchPreference(mContext);
        mPreferenceController = new AdasLocationSwitchPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
    }

    @Test
    public void onAdasIntentReceived_updateUi() {
        initializePreference(/* checked= */false, /* enabled= */true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> intentFilterCaptor = ArgumentCaptor.forClass(
                IntentFilter.class);
        verify(mContext, times(2))
                .registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                        intentFilterCaptor.capture(), eq(Context.RECEIVER_NOT_EXPORTED));
        List<IntentFilter> actions = intentFilterCaptor.getAllValues();
        assertTrue(actions.get(0).hasAction(LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED));

        when(mLocationManager.isAdasGnssLocationEnabled()).thenReturn(true);
        broadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED));

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isTrue();
    }

    @Test
    public void onLocationIntentReceived_updateUi() {
        initializePreference(/* checked= */false, /* enabled= */true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> intentFilterCaptor = ArgumentCaptor.forClass(
                IntentFilter.class);

        verify(mContext, times(2))
                .registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                        intentFilterCaptor.capture(), eq(Context.RECEIVER_NOT_EXPORTED));
        List<IntentFilter> actions = intentFilterCaptor.getAllValues();
        assertTrue(actions.get(1).hasAction(LocationManager.MODE_CHANGED_ACTION));

        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        broadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(LocationManager.MODE_CHANGED_ACTION));

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isFalse();
    }

    @Test
    public void onPreferenceClicked_adasDisabled_shouldEnable_notShowDialog() {
        initializePreference(/* checked= */false, /* enabled= */true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mSwitchPreference.performSecondaryActionClick();

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isTrue();
        assertThat(mSwitchPreference.isSecondaryActionChecked()).isTrue();
        verify(mLocationManager).setAdasGnssLocationEnabled(true);
        verify(mFragmentController, never())
                .showDialog(any(ConfirmationDialogFragment.class), any());
    }

    @Test
    public void onPreferenceClicked_adasEnabled_shouldStayEnable_showDialog() {
        initializePreference(/* checked= */true, /* enabled= */true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mSwitchPreference.performSecondaryActionClick();

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isTrue();
        assertThat(mSwitchPreference.isSecondaryActionChecked()).isTrue();
        verify(mLocationManager, never()).setLocationEnabledForUser(anyBoolean(), any());
        verify(mFragmentController)
                .showDialog(any(ConfirmationDialogFragment.class),
                        eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void confirmDialog_turnOffDriverAssistance() throws Throwable {
        initializePreference(/* checked= */true, /* enabled= */true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mSwitchPreference.performSecondaryActionClick();

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mFragmentController).showDialog(dialogCaptor.capture(),
                eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);

        // Confirm action is the listener of negative button.
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mLocationManager).setAdasGnssLocationEnabled(false);
    }

    @Test
    public void onPolicyChanged_enabled_setsSwitchEnabled() {
        initializePreference(/* checked= */ true, /* enabled= */ false);
        when(mLocationManager.isLocationEnabled()).thenReturn(false);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isTrue();
    }

    @Test
    public void onPolicyChanged_disabled_setsSwitchDisabled() {
        initializePreference(/* checked= */ true, /* enabled= */ true);
        when(mLocationManager.isLocationEnabled()).thenReturn(false);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ false);

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isFalse();
    }

    @Test
    public void onPolicyChange_enabled_locationEnabled_switchStaysDisabled() {
        initializePreference(/* checked= */ true, /* enabled= */ false);
        when(mLocationManager.isLocationEnabled()).thenReturn(true);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isFalse();
    }

    @Test
    public void adasLocationOnAndEnabled_disallowConfigLocation_switchStaysChecked() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager,  DISALLOW_CONFIG_LOCATION,
                /* restricted= */true);
        when(mLocationManager.isLocationEnabled()).thenReturn(false);

        initializePreference(/* checked= */ true, /* enabled= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isFalse();
        assertThat(mSwitchPreference.isSecondaryActionChecked()).isTrue();
    }
    @Test
    public void adasLocationOnAndEnabled_disallowShareLocation_setsSwitchUnchecked() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager,  DISALLOW_SHARE_LOCATION,
                /* restricted= */true);
        when(mLocationManager.isLocationEnabled()).thenReturn(false);

        initializePreference(/* checked= */ true, /* enabled= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mSwitchPreference.isSecondaryActionEnabled()).isFalse();
        assertThat(mSwitchPreference.isSecondaryActionChecked()).isFalse();
    }

    @Test
    public void disallowConfigLocation_switchTapped_showsDialog() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager,  DISALLOW_CONFIG_LOCATION,
                /* restricted= */true);
        when(mLocationManager.isLocationEnabled()).thenReturn(false);
        initializePreference(/* checked= */ true, /* enabled= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    @Test
    public void disallowShareLocation_switchTapped_showsDialog() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager,  DISALLOW_SHARE_LOCATION,
                /* restricted= */true);
        when(mLocationManager.isLocationEnabled()).thenReturn(false);
        initializePreference(/* checked= */ true, /* enabled= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    @Test
    public void cancelDialog_DriverAssistanceStaysOn() throws Throwable {
        initializePreference(/* checked= */true, /* enabled= */true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mSwitchPreference.performSecondaryActionClick();

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mFragmentController).showDialog(dialogCaptor.capture(),
                eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);

        // Cancel action is the listener of positive button.
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mLocationManager, never()).setAdasGnssLocationEnabled(false);
    }

    private void initializePreference(boolean checked, boolean enabled) {
        when(mLocationManager.isAdasGnssLocationEnabled()).thenReturn(checked);
        mSwitchPreference.setSecondaryActionChecked(checked);
        mSwitchPreference.setSecondaryActionEnabled(enabled);
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
