/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.accessibility;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.media.AudioManager;
import android.os.VibrationAttributes;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.testutils.shadow.ShadowInteractionJankMonitor;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.core.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowInteractionJankMonitor.class})
public class HapticFeedbackIntensityPreferenceControllerTest {

    private static final String PREFERENCE_KEY = "preference_key";
    private static final int OFF = 0;
    private static final int ON = 1;

    @Mock private PreferenceScreen mScreen;
    @Mock private AudioManager mAudioManager;

    private Lifecycle mLifecycle;
    private Context mContext;
    private Vibrator mVibrator;
    private HapticFeedbackIntensityPreferenceController mController;
    private SeekBarPreference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycle = new Lifecycle(() -> mLifecycle);
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mAudioManager);
        when(mAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        mVibrator = mContext.getSystemService(Vibrator.class);
        mController = new HapticFeedbackIntensityPreferenceController(mContext, PREFERENCE_KEY,
                Vibrator.VIBRATION_INTENSITY_HIGH);
        mLifecycle.addObserver(mController);
        mPreference = new SeekBarPreference(mContext);
        mPreference.setSummary("Test summary");
        when(mScreen.findPreference(mController.getPreferenceKey())).thenReturn(mPreference);
        mController.displayPreference(mScreen);
    }

    @Test
    public void verifyConstants() {
        assertThat(mController.getPreferenceKey()).isEqualTo(PREFERENCE_KEY);
        assertThat(mController.getAvailabilityStatus())
                .isEqualTo(BasePreferenceController.AVAILABLE);
        assertThat(mController.getMin()).isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);
        assertThat(mController.getMax()).isEqualTo(Vibrator.VIBRATION_INTENSITY_HIGH);
    }

    @Test
    public void missingSetting_shouldReturnDefault() {
        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_INTENSITY, /* value= */ null);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress())
                .isEqualTo(mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_TOUCH));
    }

    @Test
    public void updateState_ringerModeUpdates_shouldNotAffectSettings() {
        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_LOW);

        when(mAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_LOW);
        assertThat(mPreference.isEnabled()).isTrue();

        when(mAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_SILENT);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_LOW);
        assertThat(mPreference.isEnabled()).isTrue();

        when(mAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_VIBRATE);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_LOW);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void updateState_hapticFeedbackDisabled_shouldDisplayAlwaysOff() {
        updateSetting(Settings.System.HAPTIC_FEEDBACK_ENABLED, OFF);

        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_HIGH);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);

        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);

        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_LOW);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);

        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_OFF);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);
    }

    @Test
    public void updateState_shouldDisplayIntensityInSliderPosition() {
        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_HIGH);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_HIGH);

        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_MEDIUM);

        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_LOW);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_LOW);

        updateSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_OFF);
        mController.updateState(mPreference);
        assertThat(mPreference.getProgress()).isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);
    }

    @Test
    @Ignore
    public void setProgress_updatesIntensityAndDependentSettings() throws Exception {
        mController.setSliderPosition(Vibrator.VIBRATION_INTENSITY_OFF);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY))
                .isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_ENABLED)).isEqualTo(OFF);

        mController.setSliderPosition(Vibrator.VIBRATION_INTENSITY_LOW);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY))
                .isEqualTo(Vibrator.VIBRATION_INTENSITY_LOW);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_ENABLED)).isEqualTo(ON);

        mController.setSliderPosition(Vibrator.VIBRATION_INTENSITY_MEDIUM);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY))
                .isEqualTo(Vibrator.VIBRATION_INTENSITY_MEDIUM);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_ENABLED)).isEqualTo(ON);

        mController.setSliderPosition(Vibrator.VIBRATION_INTENSITY_HIGH);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY))
                .isEqualTo(Vibrator.VIBRATION_INTENSITY_HIGH);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_ENABLED)).isEqualTo(ON);

        mController.setSliderPosition(Vibrator.VIBRATION_INTENSITY_OFF);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY))
                .isEqualTo(Vibrator.VIBRATION_INTENSITY_OFF);
        assertThat(readSetting(Settings.System.HAPTIC_FEEDBACK_ENABLED)).isEqualTo(OFF);
    }

    private void updateSetting(String key, int value) {
        Settings.System.putInt(mContext.getContentResolver(), key, value);
    }

    private int readSetting(String settingKey) throws Settings.SettingNotFoundException {
        return Settings.System.getInt(mContext.getContentResolver(), settingKey);
    }
}
