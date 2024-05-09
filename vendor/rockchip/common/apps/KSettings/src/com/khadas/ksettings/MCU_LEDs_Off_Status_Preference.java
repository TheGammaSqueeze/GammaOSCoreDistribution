package com.khadas.ksettings;

import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;


public class MCU_LEDs_Off_Status_Preference extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    private ListPreference MCU_LEDS_OFF_MODE_Preference;
    private static final String MCU_LEDS_OFF_MODE_KEY = "MCU_LEDS_OFF_MODE_KEY";

    private MCURedOffStatusSeekBarPreference MCURedOffBl_Preference;
    private static final String MCU_RED_LED_OFF_BL_KEY = "MCU_RED_LED_OFF_BL_KEY";
    private MCUGreenOffStatusSeekBarPreference MCUGreenOffBl_Preference;
    private static final String MCU_GREEN_LED_OFF_BL_KEY = "MCU_GREEN_LED_OFF_BL_KEY";
    private MCUBlueOffStatusSeekBarPreference MCUBlueOffBl_Preference;
    private static final String MCU_BLUE_LED_OFF_BL_KEY = "MCU_BLUE_LED_OFF_BL_KEY";

    private static final int MSG_UI_BL = 254;
    private static boolean status_flag = true;
    private String value;

    private Handler uiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UI_BL:
                    value = SystemProperties.get("persist.sys.mcu_red_off_bl_value");
                    if(value.equals("")){
                        value = "255";
                    }
                    MCURedOffBl_Preference.setSummary("" + value);
                    MCURedOffBl_Preference.setEnabled(true);

                    value = SystemProperties.get("persist.sys.mcu_green_off_bl_value");
                    if(value.equals("")){
                        value = "0";
                    }
                    MCUGreenOffBl_Preference.setSummary("" + value);
                    MCUGreenOffBl_Preference.setEnabled(true);

                    value = SystemProperties.get("persist.sys.mcu_blue_off_bl_value");
                    if(value.equals("")){
                        value = "0";
                    }
                    MCUBlueOffBl_Preference.setSummary("" + value);
                    MCUBlueOffBl_Preference.setEnabled(true);

                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.mcu_leds_off_status_control);

        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        MCU_LEDS_OFF_MODE_Preference = (ListPreference) findPreference(MCU_LEDS_OFF_MODE_KEY);
        bindPreferenceSummaryToValue(MCU_LEDS_OFF_MODE_Preference);
        MCURedOffBl_Preference = (MCURedOffStatusSeekBarPreference) findPreference(MCU_RED_LED_OFF_BL_KEY);
        MCUGreenOffBl_Preference = (MCUGreenOffStatusSeekBarPreference) findPreference(MCU_GREEN_LED_OFF_BL_KEY);
        MCUBlueOffBl_Preference = (MCUBlueOffStatusSeekBarPreference) findPreference(MCU_BLUE_LED_OFF_BL_KEY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status_flag = true;
        new Thread (new Runnable() {
            @Override
            public void run() {
                // do ui operate
                while (status_flag){
                    uiHandler.removeMessages(MSG_UI_BL);
                    uiHandler.sendEmptyMessageDelayed(MSG_UI_BL,100);
                    //Message msg= uiHandler.obtainMessage(MSG_UI_BL,"HW Addr:"+hwaddr+"  IP Addr: "+ip);
                    //uiHandler.sendMessageDelayed(msg,100);

                    try {
                        Thread.sleep(1 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

    }

    @Override
    protected void onStop() {
        super.onStop();
        status_flag = false;
    }

    /**
     * bindPreferenceSummaryToValue 拷贝至as自动生成的preferences的代码，用于绑定显示实时值
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            String key = preference.getKey();
            String val;
            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                // Set the summary to reflect the new value.
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
                //Off Status
                if(MCU_LEDS_OFF_MODE_KEY.equals(key)){
                    Log.d("hlm","OFF status===" + index);
                    val = Integer.toHexString(index);
                    if(index<=15 && index >=0) {
                        try {
                            ComApi.execCommand(new String[]{"sh", "-c", "echo 0x240"+ val +" > /sys/class/mcu/mculed"});
                            SystemProperties.set("persist.sys.mcu_leds_off_modes_value", val);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
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
