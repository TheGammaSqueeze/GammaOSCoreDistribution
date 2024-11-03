package com.android.tv.settings.device.displaysound;

import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import java.io.IOException;
import android.content.Intent;

/**
 * Fragment for color settings in TV Settings.
 */
@Keep
public class ColorSettingsFragment extends SettingsPreferenceFragment {

    private static final String COLOR_SETTINGS_KEY = "color_settings";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.color_settings, rootKey);

        Preference colorSettingsPreference = findPreference(COLOR_SETTINGS_KEY);
        if (colorSettingsPreference != null) {
            colorSettingsPreference.setOnPreferenceClickListener(preference -> {
                // Launch ColorControlActivity as a popup
                Intent intent = new Intent(getContext(), ColorControlActivity.class);
                startActivity(intent);
                return true;
            });
        }
    }

    @Override
    public int getPageId() {
        return TvSettingsEnums.DISPLAY_SOUND_COLOR_SETTINGS;
    }
}
