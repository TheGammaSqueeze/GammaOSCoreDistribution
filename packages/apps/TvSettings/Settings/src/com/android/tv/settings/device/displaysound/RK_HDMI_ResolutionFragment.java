package com.android.tv.settings.device.displaysound;

import static com.android.tv.settings.library.overlay.FlavorUtils.FLAVOR_CLASSIC;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;
import androidx.annotation.Keep;
import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This Fragment is responsible for allowing the user to set HDMI resolution preferences.
 */
@Keep
public class RK_HDMI_ResolutionFragment extends SettingsPreferenceFragment {

    private static final String HDMI_PROP = "persist.vendor.resolution.HDMI-A-0";
    private static final String FB_PROP = "persist.vendor.framebuffer.hdmi";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.rk_hdmi_resolution, rootKey);

        String currentHdmiResolution = getProperty(HDMI_PROP);
        String currentFbResolution = getProperty(FB_PROP);

        setupRadioPreference("default_resolution", "", "", 
            currentHdmiResolution.isEmpty() && currentFbResolution.isEmpty());
        setupRadioPreference("resolution_1080p60", "1920x1080@60", "1920x1080",
            currentHdmiResolution.equals("1920x1080@60") && currentFbResolution.equals("1920x1080"));
        setupRadioPreference("resolution_720p60", "1280x720@60", "1280x720",
            currentHdmiResolution.equals("1280x720@60") && currentFbResolution.equals("1280x720"));
        setupRadioPreference("resolution_1440p60", "2560x1440@60", "2560x1440",
            currentHdmiResolution.equals("2560x1440@60") && currentFbResolution.equals("2560x1440"));
        setupRadioPreference("resolution_1200_1080p60", "1920x1200@60", "1920x1080",
            currentHdmiResolution.equals("1920x1200@60") && currentFbResolution.equals("1920x1080"));
        setupRadioPreference("resolution_1200_720p60", "1920x1200@60", "1280x720",
            currentHdmiResolution.equals("1920x1200@60") && currentFbResolution.equals("1280x720"));
        setupRadioPreference("resolution_1024x768p60", "1024x768@60", "1024x768",
            currentHdmiResolution.equals("1024x768@60") && currentFbResolution.equals("1024x768"));
        setupRadioPreference("resolution_800x600p60", "800x600@60", "800x600",
            currentHdmiResolution.equals("800x600@60") && currentFbResolution.equals("800x600"));
        setupRadioPreference("resolution_720x480p60", "720x480@60", "720x480",
            currentHdmiResolution.equals("720x480@60") && currentFbResolution.equals("720x480"));
        setupRadioPreference("resolution_640x480p60", "640x480@60", "640x480",
            currentHdmiResolution.equals("640x480@60") && currentFbResolution.equals("640x480"));
    }

    private void setupRadioPreference(String key, String hdmiResolution, String framebufferResolution, boolean isChecked) {
        RadioPreference preference = (RadioPreference) findPreference(key);
        if (preference != null) {
            preference.setChecked(isChecked);
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if ((Boolean) newValue && !preference.isChecked()) {  // Ensure selection change only occurs if not already checked
                    try {
                        if (hdmiResolution.isEmpty() && framebufferResolution.isEmpty()) {
                            executeCommand(new String[]{"setprop", HDMI_PROP, ""});
                            executeCommand(new String[]{"setprop", FB_PROP, ""});
                        } else {
                            executeCommand(new String[]{"setprop", HDMI_PROP, hdmiResolution});
                            executeCommand(new String[]{"setprop", FB_PROP, framebufferResolution});
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    clearOtherChecks(key);
                }
                return true;
            });
        }
    }

    private void clearOtherChecks(String key) {
        String[] keys = {
            "default_resolution", "resolution_1080p60", "resolution_720p60",
            "resolution_1440p60", "resolution_1200_1080p60", "resolution_1200_720p60",
            "resolution_1024x768p60", "resolution_800x600p60", 
            "resolution_720x480p60", "resolution_640x480p60"};
        for (String otherKey : keys) {
            if (!otherKey.equals(key)) {
                RadioPreference otherPref = (RadioPreference) findPreference(otherKey);
                if (otherPref != null) {
                    otherPref.setChecked(false);
                }
            }
        }
    }

    private String getProperty(String property) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"getprop", property});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine().trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private void executeCommand(String[] command) throws IOException {
        Process process = new ProcessBuilder(command).start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getPageId() {
        return TvSettingsEnums.DISPLAY_SOUND_RK_HDMI_RESOLUTION;
    }
}
