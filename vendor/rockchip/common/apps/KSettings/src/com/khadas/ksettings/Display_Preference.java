package com.khadas.ksettings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.widget.Toast;
import android.util.Log;

import java.io.IOException;


public class Display_Preference extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    private ListPreference Screen_rotation_Preference;
    private ListPreference Screen_density_Preference;

    private static final String DISPLAY_ROTATION_KEY = "DISPLAY_ROTATION_KEY";
    private static final String DISPLAY_DENSITY_KEY = "DISPLAY_DENSITY_KEY";


    private static Context mContext;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.display);

        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mContext = this;

        Screen_rotation_Preference = (ListPreference) findPreference(DISPLAY_ROTATION_KEY);
        bindPreferenceSummaryToValue(Screen_rotation_Preference);
		Screen_rotation_Preference.setOnPreferenceClickListener(this);

        Screen_density_Preference = (ListPreference) findPreference(DISPLAY_DENSITY_KEY);
        bindPreferenceSummaryToValue(Screen_density_Preference);
    }

    /**
     * bindPreferenceSummaryToValue 拷贝至as自动生成的preferences的代码，用于绑定显示实时值
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            String key = preference.getKey();
            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                // Set the summary to reflect the new value.
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

                if (DISPLAY_DENSITY_KEY.equals(key)){
                    String command = index >= 0 ? listPreference.getEntries()[index].toString() : "";
                    try {
                        ComApi.execCommand(new String[]{"sh", "-c", "wm density " + command});
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }else if(DISPLAY_ROTATION_KEY.equals(key)){
                    Settings.System.putInt(mContext.getContentResolver(),"user_rotation",index);
                    SystemProperties.set("persist.sys.user_rotation",""+index);
                }

            }  else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }


    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        final String key = preference.getKey();
        if (DISPLAY_ROTATION_KEY.equals(key)){
            SystemProperties.set("set.user_rotation","true");
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        SystemProperties.set("set.user_rotation","false");
    }
}
