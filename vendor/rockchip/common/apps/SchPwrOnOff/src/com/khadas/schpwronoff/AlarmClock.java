package com.khadas.schpwronoff;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.view.KeyEvent;

import android.util.Log;


import java.text.DateFormatSymbols;
import java.util.Calendar;

/**
 * AlarmClock application.
 */
public class AlarmClock extends PreferenceActivity implements OnItemClickListener {
    private static final String TAG = "AlarmClock";
    static final String PREFERENCES = "AlarmClock";
    static final String PREF_CLOCK_FACE = "face";
    static final String PREF_SHOW_CLOCK = "show_clock";

    /** Cap alarm count at this number */
    static final int MAX_ALARM_COUNT = 12;

    /**
     * This must be false for production. If true, turns on logging, test code, etc.
     */
    static final boolean DEBUG = true;

    private LayoutInflater mFactory;
    private ListView mAlarmsList;
    private Cursor mCursor;
    private String mAm;
    private String mPm;
    private boolean mUserCheckedFlag;

	private Context mContext;
    /*
     * FIXME: it would be nice for this to live in an xml config file.
     */

    private class AlarmTimeAdapter extends CursorAdapter {
        public AlarmTimeAdapter(Context context, Cursor cursor) {
            super(context, cursor);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View ret = mFactory.inflate(R.layout.schpwr_alarm_time, parent, false);
            ((TextView) ret.findViewById(R.id.am)).setText(mAm);
            ((TextView) ret.findViewById(R.id.pm)).setText(mPm);

            DigitalClock digitalClock = (DigitalClock) ret.findViewById(R.id.digitalClock);
            if (digitalClock != null) {
                digitalClock.setLive(false);
            }
            Log.d(TAG, "newView " + cursor.getPosition());
            Log.d(TAG, "newView " + cursor.getPosition());
            return ret;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
             Log.d(TAG, "bindView");
        	 Log.d(TAG, "bindView");
            final Alarm alarm = new Alarm(cursor);
            final Context cont = context;
            Switch onButton = (Switch) view.findViewById(R.id.alarmButton);
            if (onButton != null) {
                mUserCheckedFlag = false;
                onButton.setChecked(alarm.mEnabled);
                mUserCheckedFlag = true;
                onButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (!mUserCheckedFlag) {
                            Log.d(TAG, "onCheckedChanged user click return ");
                        	
                            return;
                        }
                        Log.d(TAG, "isChecked = " + isChecked);
                        Alarms.enableAlarm(cont, alarm.mId, isChecked);
                        if (isChecked) {
                            SetAlarm.popAlarmSetToast(cont, alarm.mHour, alarm.mMinutes, alarm.mDaysOfWeek, alarm.mId);
                        }
                    }
                });
            }

            ImageView onOffView = (ImageView) view.findViewById(R.id.power_on_off);
            if (onOffView != null) {
                onOffView.setImageDrawable(getResources().getDrawable(
                        (alarm.mId == 1) ? R.drawable.ic_settings_schpwron : R.drawable.ic_settings_schpwroff));
            }

            DigitalClock digitalClock = (DigitalClock) view.findViewById(R.id.digitalClock);

            // set the alarm text
            final Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, alarm.mHour);
            c.set(Calendar.MINUTE, alarm.mMinutes);
            if (digitalClock != null) {
                digitalClock.updateTime(c);
            }

            // Set the repeat text or leave it blank if it does not repeat.
            TextView daysOfWeekView = (TextView) digitalClock.findViewById(R.id.daysOfWeek);
            final String daysOfWeekStr = alarm.mDaysOfWeek.toString(context, false);
            if (daysOfWeekView != null) {
                if (daysOfWeekStr != null && daysOfWeekStr.length() != 0) {
                    daysOfWeekView.setText(daysOfWeekStr);
                    daysOfWeekView.setVisibility(View.VISIBLE);
                } else {
                    daysOfWeekView.setVisibility(View.GONE);
                }
            }
        }
    };

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        if (item.getItemId() == R.id.enable_alarm) {
            final Cursor c = (Cursor) mAlarmsList.getAdapter().getItem(info.position);
            final Alarm alarm = new Alarm(c);
            Alarms.enableAlarm(this, alarm.mId, !alarm.mEnabled);
            if (!alarm.mEnabled) {
                SetAlarm.popAlarmSetToast(this, alarm.mHour, alarm.mMinutes, alarm.mDaysOfWeek, alarm.mId);
            }
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
		mContext = this;
        String[] ampm = new DateFormatSymbols().getAmPmStrings();
        mAm = ampm[0];
        mPm = ampm[1];
        mFactory = LayoutInflater.from(this);
        mCursor = Alarms.getAlarmsCursor(this.getContentResolver());
        Log.d(TAG, "mCursor.getCount() " + mCursor.getCount());

        //add which is in onCreateView()
        View v = mFactory.inflate(R.layout.schpwr_alarm_clock, null);
        setContentView(v);
        mAlarmsList = (ListView) v.findViewById(android.R.id.list);
        if (mAlarmsList != null) {
            mAlarmsList.setAdapter(new AlarmTimeAdapter(this, mCursor));
            mAlarmsList.setVerticalScrollBarEnabled(true);
            mAlarmsList.setOnItemClickListener(this);
            mAlarmsList.setOnCreateContextMenuListener(this);
        }
        registerForContextMenu(mAlarmsList);

		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        View viewFocus = getCurrentFocus();
        int viewId = -1;
        int position = -1;
        if (viewFocus != null) {
            viewId = viewFocus.getId();
            if (viewFocus instanceof ListView) {
                position = ((ListView) viewFocus).getSelectedItemPosition();
            }
        }

        super.onConfigurationChanged(newConfig);

        if (viewId >= 0 && position >= 0) {
            ListView mListView = (ListView) findViewById(viewId);
            mListView.requestFocus();
            mListView.setSelection(position);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCursor.close();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        // Inflate the menu from xml.
        getMenuInflater().inflate(R.menu.schpwr_context_menu, menu);

        // Use the current item to create a custom view for the header.
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final Cursor c = (Cursor) mAlarmsList.getAdapter().getItem(info.position);
        final Alarm alarm = new Alarm(c);

        // Construct the Calendar to compute the time.
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, alarm.mHour);
        cal.set(Calendar.MINUTE, alarm.mMinutes);
        final String time = Alarms.formatTime(this, cal);

        // Inflate the custom view and set each TextView's text.
        final View v = mFactory.inflate(R.layout.schpwr_context_menu_header, null);
        TextView textView = (TextView) v.findViewById(R.id.header_time);
        if (textView != null) {
            textView.setText(time);
        }

        // Set the custom view on the menu.
        menu.setHeaderView(v);
        // Change the text to "disable" if the alarm is already enabled.
        if (alarm.mEnabled) {
            menu.findItem(R.id.enable_alarm).setTitle(R.string.disable_schpwr);
        } else {
            menu.findItem(R.id.enable_alarm).setTitle(R.string.enable_schpwr);
        }
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item){

		if(item.getItemId() == android.R.id.home){
			  finish();
			  return true;
		}
		return super.onOptionsItemSelected(item);
	}
    @Override
    public void onItemClick(AdapterView parent, View v, int pos, long id) {
        Log.d(TAG, "onItemClick, id is " + id);
        Intent intent = new Intent();
        intent.setClass(this, com.khadas.schpwronoff.SetAlarm.class);
        final Bundle bundle = new Bundle();
        bundle.putInt(Alarms.ALARM_ID, (int) id);
        intent.putExtras(bundle);
        startActivity(intent);
    }

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// TODO Auto-generated method stub
		int keyCode = event.getKeyCode();
		if(keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ){
			final Cursor c = (Cursor) mAlarmsList.getAdapter().getItem(getSelectedItemPosition());
			final Alarm alarm = new Alarm(c);
			if(keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			 if(alarm.mEnabled)
               Alarms.enableAlarm(mContext, alarm.mId, false);
			}else{
             if(!alarm.mEnabled){
            Alarms.enableAlarm(mContext, alarm.mId, true);
            SetAlarm.popAlarmSetToast(mContext, alarm.mHour, alarm.mMinutes, alarm.mDaysOfWeek, alarm.mId);          }
			}

			return true;
        }
	     return super.dispatchKeyEvent(event);
	}

}
