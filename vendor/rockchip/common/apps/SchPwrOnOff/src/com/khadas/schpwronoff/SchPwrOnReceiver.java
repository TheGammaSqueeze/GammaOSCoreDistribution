package com.khadas.schpwronoff;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/**
 * Glue class: connects AlarmAlert IntentReceiver to AlarmAlert activity.
 * Passes through Alarm ID.
 */
public class SchPwrOnReceiver extends BroadcastReceiver {
    private static final String TAG = "SchPwrOnReceiver";
    /**
     * If the alarm is older than STALE_WINDOW seconds, ignore.
     * It is probably the result of a time or timezone change
     */
    private static final int STALE_WINDOW = 60 * 30;

    @Override
    public void onReceive(Context context, Intent intent) {
        Alarm alarm = null;
        // Grab the alarm from the intent. Since the remote AlarmManagerService
        // fills in the Intent to add some extra data, it must unparcel the
        // Alarm object. It throws a ClassNotFoundException when unparcelling.
        // To avoid this, do the marshalling ourselves.
        final byte[] data = intent.getByteArrayExtra(Alarms.ALARM_RAW_DATA);
        if (data != null) {
            Parcel in = Parcel.obtain();
            in.unmarshall(data, 0, data.length);
            in.setDataPosition(0);
            alarm = Alarm.CREATOR.createFromParcel(in);
        }

        if (alarm == null) {
            Log.d(TAG, "SchPwrOnReceiver failed to parse the alarm from the intent");
            return;
        }
        final int stateWindowTimeoff = 1000;
        // Intentionally verbose: always log the alarm time to provide useful
        // information in bug reports.
        long now = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS aaa", Locale.US);
        Log.d(TAG, "SchPwrOnReceiver.onReceive() id " + alarm.mId + " setFor "
                + format.format(new Date(alarm.mTime)));

        if (now > alarm.mTime + STALE_WINDOW * stateWindowTimeoff) {
            Log.d(TAG, "SchPwrOnReceiver ignoring stale alarm");
            return;
        }

        // Maintain a cpu wake lock until the AlarmAlert and AlarmKlaxon can
        // pick it up.
        // AlarmAlertWakeLock.acquireCpuWakeLock(context);

        /* Close dialogs and window shade */
        // Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        // context.sendBroadcast(closeDialogs);

        Log.d(TAG, "SchPwrOnReceiver.onReceive() id " + alarm.mId + " time out ");
        // Decide which activity to start based on the state of the keyguard.
        if (alarm.mId == 1) {
            if (alarm.mDaysOfWeek.isRepeatSet()) {
                // Enable the next alert if there is one. The above call to
                // enableAlarm will call setNextAlert so avoid calling it twice.
                Log.d(TAG, "SchPwrOnReceiver.onReceive(): isRepeatSet()");
                Alarms.setNextAlertPowerOn(context,false);
            } else {
                Log.d(TAG, "SchPwrOnReceiver.onReceive(): not isRepeatSet()");
                Alarms.enableAlarm(context, alarm.mId, false);
            }
        } else if (alarm.mId == 2) {
            Log.d(TAG, "SchPwrOnReceiver.onReceive() id " + alarm.mId + " get power off time out ");
        }
    }
}
