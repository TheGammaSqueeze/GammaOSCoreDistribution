package com.khadas.ksettings;

import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;


public class SYS_LEDs_Preference extends PreferenceActivity implements Preference.OnPreferenceClickListener {
	
    private ListPreference LED_Red_Preference;
    private ListPreference LED_Green_Preference;
    private ListPreference LED_Blue_Preference;

    private static final String Red_LED_KEY = "Red_LED_KEY";
    private static final String Green_LED_KEY = "Green_LED_KEY";
    private static final String Blue_LED_KEY = "Blue_LED_KEY";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.sys_leds_control);

        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        LED_Red_Preference = (ListPreference) findPreference(Red_LED_KEY);
        bindPreferenceSummaryToValue(LED_Red_Preference);
        LED_Green_Preference = (ListPreference) findPreference(Green_LED_KEY);
        bindPreferenceSummaryToValue(LED_Green_Preference);		
        LED_Blue_Preference = (ListPreference) findPreference(Blue_LED_KEY);
        bindPreferenceSummaryToValue(LED_Blue_Preference);

    }

    @Override
    protected void onResume() {
        super.onResume();
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

                if (Red_LED_KEY.equals(key)){
                    //Log.d("wjh","1===" + index);
                    switch(index){
                        case 0:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo off > /sys/class/leds/red_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 1:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo default-on > /sys/class/leds/red_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 2:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo timer > /sys/class/leds/red_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 3:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo heartbeat > /sys/class/leds/red_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                    SystemProperties.set("persist.sys.Red_led_control", "" + index);

                }
                else if (Green_LED_KEY.equals(key)){
                    //Log.d("wjh","1===" + index);
                    switch(index){
                        case 0:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo off > /sys/class/leds/green_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 1:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo default-on > /sys/class/leds/green_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 2:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo timer > /sys/class/leds/green_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 3:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo heartbeat > /sys/class/leds/green_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                    SystemProperties.set("persist.sys.Green_led_control", "" + index);

                }else if(Blue_LED_KEY.equals(key)){
                    //Log.d("wjh","1===" + index);
                    switch(index){
                        case 0:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo off > /sys/class/leds/blue_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 1:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo default-on > /sys/class/leds/blue_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 2:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo timer > /sys/class/leds/blue_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case 3:
                            try {
                                ComApi.execCommand(new String[]{"sh", "-c", "echo heartbeat > /sys/class/leds/blue_led/trigger"});
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                    SystemProperties.set("persist.sys.Blue_led_control", "" + index);

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
        return true;
    }
}
