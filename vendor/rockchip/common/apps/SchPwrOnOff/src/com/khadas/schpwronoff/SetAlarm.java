package com.khadas.schpwronoff;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.DialogInterface.OnKeyListener;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.widget.TimePicker;
import android.widget.Toast;
import android.view.View;
import android.content.res.Resources;

import java.util.Calendar;
import android.app.AlertDialog;
import android.view.Window;
import java.lang.reflect.Field;
import android.widget.Button;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.NumberPicker;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import android.util.Log;

/**
 * Manages each alarm
 */
public class SetAlarm extends PreferenceActivity {

    private static final String TAG = "SetAlarm";
    private Preference mTimePref;
    private RepeatPreference mRepeatPref;
    private MenuItem mTestAlarmItem;

    private int mId;
    private boolean mEnabled;
    private boolean is24format;
    private int mHour;
    private int mMinutes;
    private static final int MENU_BACK = android.R.id.home;
    private static final int MENU_REVET = Menu.FIRST;
    private static final int MENU_SAVE = Menu.FIRST + 1;
    private String mPrevTitle;
    private static final int DIALOG_TIMEPICKER = 1;

    private CustomNumberPicker mHourSpinner;
    private CustomNumberPicker mMinuteSpinner;
    private CustomNumberPicker mAmPmSpinner;
    private Button mBtnOk;
    private Button mBtnCancel;
    private Context mContext;
    private AlertDialog mAlertDialog;
    private final static int HOUR_MAX_VALUE = 23;
    private final static int HOUR_MIN_VALUE = 0;
    private final static int MINUTE_MAX_VALUE = 59;
    private final static int MINUTE_MIN_VALUE = 0;
    private final static int HOUR_MIDDLE_VALUE = 12;
    private final static int AMPM_MIN_VALUE = 0;
    private final static int AMPM_MAX_VALUE = 1;
    private final static int AM_VALUE = 0;
    private final static int PM_VALUE = 1;
    private int KeyCount;

    public final static String ACTION_UPDATEALARM_INTENT = "action.updatealarm.intent";
    private BroadcastReceiver mAlarmUpdateReceiver;
    /**
     * Set an alarm. Requires an Alarms.ALARM_ID to be passed in as an extra.
     * FIXME: Pass an Alarm object like every other Activity.
     * @param savedInstanceState Bundle
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.schpwr_alarm_prefs);

        mContext = this;
        PreferenceScreen view = getPreferenceScreen();
        mTimePref = view.findPreference("time");
        mRepeatPref = (RepeatPreference) view.findPreference("setRepeat");

        mId = getIntent().getIntExtra(Alarms.ALARM_ID, 0);
        Log.d(TAG, "onCreate " + "bundle extra is " + mId);

        mPrevTitle = getTitle().toString();
        if (mId == 1) {
            setTitle(R.string.schedule_power_on_set);
        } else {
            setTitle(R.string.schedule_power_off_set);
        }
        Log.d(TAG, "In SetAlarm, alarm id = " + mId);

        // load alarm details from database
        Alarm alarm = Alarms.getAlarm(getContentResolver(), mId);
        if (alarm != null) {
            mEnabled = alarm.mEnabled;
            mHour = alarm.mHour;
            mMinutes = alarm.mMinutes;
            if (mRepeatPref != null) {
                mRepeatPref.setDaysOfWeek(alarm.mDaysOfWeek);
            }
        }
        updateTime();
        //setHasOptionsMenu(true);
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Resources resources = getResources();
		int margin = (int) resources.getDimension(R.dimen.listview_side_margin);
        getListView().setPadding(margin, 0, margin, 0);

        if(mAlarmUpdateReceiver==null){
            mAlarmUpdateReceiver = new AlarmUpdateReceiver();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_UPDATEALARM_INTENT);
        registerReceiver(mAlarmUpdateReceiver, filter, null, null);

    }
    @Override
    protected void onResume() {
        if(mAlarmUpdateReceiver==null){
            mAlarmUpdateReceiver = new AlarmUpdateReceiver();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_UPDATEALARM_INTENT);
        registerReceiver(mAlarmUpdateReceiver, filter, null, null);
        super.onResume();
    }

    @Override
    protected void onStop() {
         Log.d(TAG,"onStop");
        if(mAlarmUpdateReceiver!=null){
            unregisterReceiver(mAlarmUpdateReceiver);
            mAlarmUpdateReceiver = null;
        }

        super.onStop();
    }


    public class AlarmUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            Log.d(TAG,"AlarmUpdateReceiver");
            saveAlarm();
        }

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mTimePref) {
            Log.d(TAG, "showDialog(DIALOG_TIMEPICKER)");
            showTimeDialog();
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_REVET:
            finish();
            return true;
        case MENU_SAVE:
            Log.d(TAG, "option save menu");
            saveAlarm();
            finish();
            return true;
		case MENU_BACK:
			finish();
			return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }


    private void updateTime() {
        Log.d(TAG, "updateTime " + mId);
        mTimePref.setSummary(Alarms.formatTime(this, mHour, mMinutes, mRepeatPref.getDaysOfWeek()));
    }

    private void saveAlarm() {
        final String alert = Alarms.ALARM_ALERT_SILENT;
        mEnabled |= mRepeatPref.mIsPressedPositive;
        Alarms.setAlarm(this, mId, mEnabled, mHour, mMinutes, mRepeatPref.getDaysOfWeek(), true, "", alert);

        if (mEnabled) {
            popAlarmSetToast(this.getApplicationContext(), mHour, mMinutes, mRepeatPref.getDaysOfWeek(), mId);
        }
    }

    /**
     * Display a toast that tells the user how long until the alarm goes off. This helps prevent "am/pm" mistakes.
     */
    static void popAlarmSetToast(Context context, int hour, int minute, Alarm.DaysOfWeek daysOfWeek, int mId) {
        String toastText = formatToast(context, hour, minute, daysOfWeek, mId);
        Log.d(TAG, "toast text: " + toastText);
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show();
    }

    /**
     * format "Alarm set for 2 days 7 hours and 53 minutes from now"
     */
    static String formatToast(Context context, int hour, int minute, Alarm.DaysOfWeek daysOfWeek, int id) {
        long alarm = Alarms.calculateAlarm(hour, minute, daysOfWeek).getTimeInMillis();
        long delta = alarm - System.currentTimeMillis();

        final int millisUnit = 1000;
        final int timeUnit = 60;
        final int dayOfHoursUnit = 24;

        long hours = delta / (millisUnit * timeUnit * timeUnit);
        long minutes = delta / (millisUnit * timeUnit) % timeUnit;
        long days = hours / dayOfHoursUnit;
        hours = hours % dayOfHoursUnit;

        String daySeq = (days == 0) ? "" : (days == 1) ? context.getString(R.string.day) : context.getString(R.string.days,
                Long.toString(days));

        String minSeq = (minutes == 0) ? "" : (minutes == 1) ? context.getString(R.string.minute) : context.getString(
                R.string.minutes, Long.toString(minutes));

        String hourSeq = (hours == 0) ? "" : (hours == 1) ? context.getString(R.string.hour) : context.getString(
                R.string.hours, Long.toString(hours));

        boolean dispDays = days > 0;
        boolean dispHour = hours > 0;
        boolean dispMinute = minutes > 0;

        final int dispMinutesOffset = 4;
        final int pwrOnOFFStringOffset = 8;

        int index = (dispDays ? 1 : 0) | (dispHour ? 2 : 0) | (dispMinute ? dispMinutesOffset : 0);

        String[] formats = context.getResources().getStringArray(R.array.alarm_set);
        if (id == 2) {
            index += pwrOnOFFStringOffset;
        }
        return String.format(formats[index], daySeq, hourSeq, minSeq);
    }
    
    @Override
     public void onConfigurationChanged(Configuration newConfig) {
       super.onConfigurationChanged(newConfig);
       Log.d(TAG, "onConfigurationChanged: " + newConfig.orientation + ",remove timer picker dialog");
    }

    private void showTimeDialog(){
       mAlertDialog = new AlertDialog.Builder(this).create();
       mAlertDialog.setOnKeyListener(new OnKeyListener() {
          @Override
          public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
              int action = event.getAction();
              if(action == 0){
                 KeyCount++;
              }else if(action == 1){
                 KeyCount = 0;
              }
              switch(keyCode){
                 case KeyEvent.KEYCODE_DPAD_DOWN:
                 case KeyEvent.KEYCODE_DPAD_UP:
                      if(action == 1)
                         break;
                      if(mHourSpinner.isFocused()){
                         if(KeyCount == 1) break;
                         int hourValue;
                         if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN){
                             hourValue = mHourSpinner.getValue()+KeyCount;
                             if(hourValue>mHourSpinner.getMaxValue())
                                hourValue = mHourSpinner.getMaxValue();
                         }else{
                             hourValue = mHourSpinner.getValue()-KeyCount;
                            if(hourValue<mHourSpinner.getMinValue())
                               hourValue = mHourSpinner.getMinValue();
                         }
                         mHourSpinner.setValue(hourValue);
                         break;
                       }else if(mMinuteSpinner.isFocused()){
                         if(KeyCount == 1) break;
                         int minuteValue;
                         if(keyCode == KeyEvent.KEYCODE_DPAD_DOWN){
                             minuteValue = mMinuteSpinner.getValue()+KeyCount;
                             if(minuteValue>mMinuteSpinner.getMaxValue())
                                minuteValue = mMinuteSpinner.getMaxValue();
                         }else{
                             minuteValue = mMinuteSpinner.getValue()-KeyCount;
                             if(minuteValue<mMinuteSpinner.getMinValue())
                                 minuteValue = mMinuteSpinner.getMinValue();
                         }
                         mMinuteSpinner.setValue(minuteValue);
                         break;
                       }

                      if(mAmPmSpinner!=null){
                          if(mAmPmSpinner.isFocused())
                              break;
                       }

                      if(!mHourSpinner.isFocusable())
                          mHourSpinner.setFocusable(true);
                      if(!mMinuteSpinner.isFocusable())
                          mMinuteSpinner.setFocusable(true);
                      if(mAmPmSpinner!=null){
                          if(!mAmPmSpinner.isFocusable())
                              mAmPmSpinner.setFocusable(true);
                       }

                      mBtnCancel.setFocusable(true);
                      break;
                  case KeyEvent.KEYCODE_DPAD_LEFT:
                  case KeyEvent.KEYCODE_DPAD_RIGHT:
                       if(action == 1)
                           break;
                       if(!mHourSpinner.isFocusable())
                           mHourSpinner.setFocusable(true);
                       if(!mMinuteSpinner.isFocusable())
                           mMinuteSpinner.setFocusable(true);
                       if(mAmPmSpinner!=null){
                           if(!mAmPmSpinner.isFocusable())
                               mAmPmSpinner.setFocusable(true);
                        }

                       mBtnCancel.setFocusable(true);
                       break;
                  case KeyEvent.KEYCODE_DPAD_CENTER:
                       if(action == 0)
                           break;
                       if(mBtnCancel.isFocused())
                           break;
                       mHourSpinner.setFocusable(false);
                       mMinuteSpinner.setFocusable(false);
                       if(mAmPmSpinner!=null)
                          mAmPmSpinner.setFocusable(false);
                       mBtnCancel.setFocusable(false);
                       mHourSpinner.setNextFocusDownId(R.id.btn_ok);
                       break;

              }
            return false;
          }
       });
       mAlertDialog.show();
       Window window = mAlertDialog.getWindow();
       Resources mResources= getResources();
       int dividerColor = mResources.getColor(R.color.settime_numberpicker_divider_color);
       if(DateFormat.is24HourFormat(this)){
           is24format = true;
           window.setContentView(R.layout.is24format_dialog);
           window.setLayout((int)mResources.getDimension(R.dimen.settime_is24format_dialog_width), (int)mResources.getDimension(R.dimen.settime_is24format_dialog_height));
       }else{
           is24format = false;
           window.setContentView(R.layout.is12format_dialog);
           window.setLayout((int)mResources.getDimension(R.dimen.settime_is12format_dialog_width), (int)mResources.getDimension(R.dimen.settime_is12format_dialog_height));
       }

       Calendar mCalendar=Calendar.getInstance();
       int mCurrentHour=mCalendar.get(Calendar.HOUR_OF_DAY);
       int mCurrentMinute=mCalendar.get(Calendar.MINUTE);

       mHourSpinner = (CustomNumberPicker) window.findViewById(R.id.hour);
       mMinuteSpinner = (CustomNumberPicker) window.findViewById(R.id.minute);
       mAmPmSpinner = (CustomNumberPicker) window.findViewById(R.id.ampm);
       mHourSpinner.setMinValue(HOUR_MIN_VALUE);
       mMinuteSpinner.setMaxValue(MINUTE_MAX_VALUE);
       mMinuteSpinner.setMinValue(MINUTE_MIN_VALUE);
       mMinuteSpinner.setValue(mCurrentMinute);

       if(DateFormat.is24HourFormat(this)){
           mHourSpinner.setMaxValue(HOUR_MAX_VALUE);
           mHourSpinner.setValue(mCurrentHour);
           mMinuteSpinner.setNextFocusRightId(R.id.btn_ok);
       }else{
           mHourSpinner.setMaxValue(HOUR_MIDDLE_VALUE);
           String[] ampm =getResources().getStringArray(R.array.ampm_strings);
           mAmPmSpinner.setDisplayedValues(ampm);
           mAmPmSpinner.setMinValue(AMPM_MIN_VALUE);
           mAmPmSpinner.setMaxValue(AMPM_MAX_VALUE);
           if(mCurrentHour > HOUR_MIDDLE_VALUE){
               mHourSpinner.setValue(mCurrentHour - HOUR_MIDDLE_VALUE);
               mAmPmSpinner.setValue(PM_VALUE);
           }else{
               mHourSpinner.setValue(mCurrentHour);
               mAmPmSpinner.setValue(AM_VALUE);
           }
           mAmPmSpinner.setNextFocusRightId(R.id.btn_ok);
           setNumberPickerDividerColor(mAmPmSpinner,dividerColor);
           mAmPmSpinner.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
       }

       setNumberPickerDividerColor(mHourSpinner,dividerColor);
       setNumberPickerDividerColor(mMinuteSpinner,dividerColor);
       mHourSpinner.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
       mMinuteSpinner.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

       mBtnOk = (Button) window.findViewById(R.id.btn_ok);
       mBtnOk.setOnClickListener(new View.OnClickListener() {
               public void onClick(View v) {
               if(is24format) {
                   mHour = mHourSpinner.getValue();
                   mMinutes = mMinuteSpinner.getValue();
                } else {
                   int mAmPm = mAmPmSpinner.getValue();
                   if (mAmPm == AM_VALUE) {
                     mHour = mHourSpinner.getValue();
                   } else {
                     mHour = mHourSpinner.getValue() + 12 ;
                   }
                   mMinutes = mMinuteSpinner.getValue();
               }
               updateTime();
               mEnabled = true;
               mAlertDialog.dismiss();
               saveAlarm();
               }
       });

       mBtnCancel = (Button) window.findViewById(R.id.btn_cancel);
       mBtnCancel.setOnClickListener(new View.OnClickListener() {
               public void onClick(View v) {
               mAlertDialog.dismiss();
               }
       });
    }

    public static void setNumberPickerDividerColor(NumberPicker numberPicker, int color) {
        Field[] pickerFields = NumberPicker.class.getDeclaredFields();
        for (Field SelectionDividerField : pickerFields) {
            if (SelectionDividerField.getName().equals("mSelectionDivider")) {
                SelectionDividerField.setAccessible(true);
                try {
                     SelectionDividerField.set(numberPicker, new ColorDrawable(color));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }
}
