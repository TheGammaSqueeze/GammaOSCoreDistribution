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

package com.android.car.settings.bluetooth;

import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.settings.common.ColoredSwitchPreference;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class PairNewDevicePreferenceControllerUnitTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

    private LifecycleOwner mLifecycleOwner;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private SwitchPreference mSwitchPreference;
    private PairNewDevicePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private UserManager mUserManager;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new PairNewDevicePreferenceController(mContext, /* preferenceKey= */
                "key", mFragmentController, mCarUxRestrictions);
        mSwitchPreference = new ColoredSwitchPreference(mContext);
        mSwitchPreference.setFragment(FragmentController.class.getCanonicalName());
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
    }

    @Test
    public void restrictedByDpm_availabilityIsAvailableForViewing() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void restrictedByDpm_showsDisabledByAdminDialog() {
        EnterpriseTestUtils.mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        mSwitchPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(), eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }
}
