package com.khadas.ksettings;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;


public class Status_bar_Preference extends PreferenceActivity implements Preference.OnPreferenceClickListener {


    private Context mContext;
    private SwitchPreference status_upper_Preference;
    private SwitchPreference status_bottom_Preference;
    private static final String STATUS_BAR_UPPER = "Status_bar_upper_key";
    private static final String STATUS_BAR_BOTTOM = "Status_bar_bottom_key";

    public static final String STATUS_BAR_SERVICE = "statusbar";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_control);

        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mContext = this;

        initPreference();
    }

    private void initPreference() {
        status_upper_Preference = (SwitchPreference)findPreference(STATUS_BAR_UPPER);
        //int mistake_touch_mode_on  = Settings.System.getInt(AliDisplaySettings.this.getContentResolver(),"sys.mistaketouch.switch",0);
        if(SystemProperties.getInt("persist.sys.show_upper_bar",1) == 1) {
            status_upper_Preference.setChecked(true);
        }else{
            status_upper_Preference.setChecked(false);
        }
        status_upper_Preference.setOnPreferenceClickListener(this);

        status_bottom_Preference = (SwitchPreference)findPreference(STATUS_BAR_BOTTOM);
        //int mistake_touch_mode_on  = Settings.System.getInt(AliDisplaySettings.this.getContentResolver(),"sys.mistaketouch.switch",0);
        if(SystemProperties.getInt("persist.sys.show_bottom_bar",1) == 1) {
            status_bottom_Preference.setChecked(true);
        }else{
            status_bottom_Preference.setChecked(false);
        }
        status_bottom_Preference.setOnPreferenceClickListener(this);
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        final String key = preference.getKey();
        if (STATUS_BAR_UPPER.equals(key)){
            if (status_upper_Preference.isChecked()) {
                sendBroadcast(new Intent("com.android.show_upper_bar"));
            }else {
                sendBroadcast(new Intent("com.android.hide_upper_bar"));
            }
        }else if(STATUS_BAR_BOTTOM.equals(key)){
            if (status_bottom_Preference.isChecked()) {
                sendBroadcast(new Intent("com.android.show_bottom_bar"));
            }else {
                sendBroadcast(new Intent("com.android.hide_bottom_bar"));
            }
        }
        return true;

    }
}
