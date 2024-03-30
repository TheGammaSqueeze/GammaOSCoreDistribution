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
 */
package com.android.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class AddWifiNetworkPreferenceTest {

    private Context mContext;
    private AddWifiNetworkPreference mPreference;

    @Mock
    private UserManager mUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        mPreference = new AddWifiNetworkPreference(mContext);

    }

    @Test
    public void updatePreferenceForRestriction_isAddWifiConfigAllowed_prefIsEnabled() {
        mPreference.mIsAddWifiConfigAllow = true;

        mPreference.updatePreferenceForRestriction();

        assertThat(mPreference.isEnabled()).isTrue();
        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void updatePreferenceForRestriction_isAddWifiConfigNotAllowed_prefIsDisabled() {
        mPreference.mIsAddWifiConfigAllow = false;

        mPreference.updatePreferenceForRestriction();

        assertThat(mPreference.isEnabled()).isFalse();
        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.not_allowed_by_ent));
    }
}
