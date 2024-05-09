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

package com.android.tv.settings.device.displaysound;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import android.provider.Settings;

import androidx.preference.PreferenceGroup;

import com.android.tv.settings.RadioPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.androidx.fragment.FragmentController;

@RunWith(RobolectricTestRunner.class)
public class ScreenTimeoutFragmentTest {
    @Spy
    private ScreenTimeoutFragment mScreenTimeoutFragment;

    @Mock
    private PreferenceGroup mPreferenceGroup;

    private RadioPreference mAutoPreference;

    private RadioPreference mNeverPreference;

    private RadioPreference mAlwaysPreference;

    private static final String KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS =
            "screen_timeout_seamless";
    private static final String KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS =
            "screen_timeout_non_seamless";
    private static final String KEY_MATCH_CONTENT_FRAME_RATE_NEVER =
            "screen_timeout_never";

    private static final int BAD_MATCH_CONTENT_FRAME_RATE_VALUE = -1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(RuntimeEnvironment.application).when(mScreenTimeoutFragment).getContext();
        mScreenTimeoutFragment.onAttach(RuntimeEnvironment.application);

        doReturn(mPreferenceGroup).when(mScreenTimeoutFragment).getPreferenceGroup();

        mAutoPreference = new RadioPreference(mScreenTimeoutFragment.getContext());
        mAutoPreference.setKey(KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS);
        mPreferenceGroup.addPreference(mAutoPreference);
        doReturn(mAutoPreference).when(mScreenTimeoutFragment)
                .getRadioPreference(eq(KEY_MATCH_CONTENT_FRAME_RATE_SEAMLESS));
        mNeverPreference = new RadioPreference(mScreenTimeoutFragment.getContext());
        mNeverPreference.setKey(KEY_MATCH_CONTENT_FRAME_RATE_NEVER);
        mPreferenceGroup.addPreference(mNeverPreference);
        doReturn(mNeverPreference).when(mScreenTimeoutFragment)
                .getRadioPreference(eq(KEY_MATCH_CONTENT_FRAME_RATE_NEVER));
        mAlwaysPreference = new RadioPreference(mScreenTimeoutFragment.getContext());
        mAlwaysPreference.setKey(KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS);
        mPreferenceGroup.addPreference(mAlwaysPreference);
        doReturn(mAlwaysPreference).when(mScreenTimeoutFragment)
                .getRadioPreference(eq(KEY_MATCH_CONTENT_FRAME_RATE_NON_SEAMLESS));
    }

    @Test
    public void testOnPreferenceTreeClick_autoSelected_otherRadioButtonsDisabled() {
        mScreenTimeoutFragment.onPreferenceTreeClick(mAutoPreference);
        assertThat(mNeverPreference.isChecked()).isFalse();
        assertThat(mAlwaysPreference.isChecked()).isFalse();
    }

    @Test
    public void testOnPreferenceTreeClick_noneSelected_otherRadioButtonsDisabled() {
        mScreenTimeoutFragment.onPreferenceTreeClick(mNeverPreference);
        assertThat(mAutoPreference.isChecked()).isFalse();
        assertThat(mAlwaysPreference.isChecked()).isFalse();
    }

    @Test
    public void testOnPreferenceTreeClick_alwaysSelected_otherRadioButtonsDisabled() {
        mScreenTimeoutFragment.onPreferenceTreeClick(mAlwaysPreference);
        assertThat(mAutoPreference.isChecked()).isFalse();
        assertThat(mNeverPreference.isChecked()).isFalse();
    }

    @Test
    public void testOnPreferenceTreeClick_autoSelected_settingModified() {
        mScreenTimeoutFragment.onPreferenceTreeClick(mAutoPreference);
        // Since Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY is the default value,
        // this action will not do anything
        assertThat(Settings.Secure.getInt(
                mScreenTimeoutFragment.getContext().getContentResolver(),
                Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                BAD_MATCH_CONTENT_FRAME_RATE_VALUE)).isEqualTo(
                        BAD_MATCH_CONTENT_FRAME_RATE_VALUE);
    }

    @Test
    public void testOnPreferenceTreeClick_neverSelected_settingModified() {
        mScreenTimeoutFragment.onPreferenceTreeClick(mNeverPreference);
        assertThat(Settings.Secure.getInt(
                mScreenTimeoutFragment.getContext().getContentResolver(),
                Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                BAD_MATCH_CONTENT_FRAME_RATE_VALUE)).isEqualTo(
                        Settings.Secure.MATCH_CONTENT_FRAMERATE_NEVER);
    }

    @Test
    public void testOnPreferenceTreeClick_alwaysSelected_settingModified() {
        mScreenTimeoutFragment.onPreferenceTreeClick(mAlwaysPreference);
        assertThat(Settings.Secure.getInt(
                mScreenTimeoutFragment.getContext().getContentResolver(),
                Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                BAD_MATCH_CONTENT_FRAME_RATE_VALUE)).isEqualTo(
                    Settings.Secure.MATCH_CONTENT_FRAMERATE_ALWAYS);
    }

    @Test
    public void testOnPreferenceTreeClick_neverSelected_then_autoSelected_settingModified() {
        mScreenTimeoutFragment.onPreferenceTreeClick(mNeverPreference);
        mScreenTimeoutFragment.onPreferenceTreeClick(mAutoPreference);
        assertThat(Settings.Secure.getInt(
                mScreenTimeoutFragment.getContext().getContentResolver(),
                Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                BAD_MATCH_CONTENT_FRAME_RATE_VALUE)).isEqualTo(
                    Settings.Secure.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY);
    }

    @Test
    public void testDefaultPreference() {
        FragmentController.of(mScreenTimeoutFragment)
            .create();
        assertThat(mAutoPreference.isChecked()).isTrue();
        assertThat(mNeverPreference.isChecked()).isFalse();
        assertThat(mAlwaysPreference.isChecked()).isFalse();
    }
}
