package com.khadas.schpwronoff;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Glue class: connects AlarmAlert IntentReceiver to AlarmAlert activity. Passes
 * through Alarm ID.
 */
public class SchPwrOffReceiver extends BroadcastReceiver {
    private static final String TAG = "SchPwrOffReceiver";
    /**
     * If the alarm is older than STALE_WINDOW seconds, ignore. It is probably
     * the result of a time or timezone change
     */
    private static final int STALE_WINDOW = 60 * 30;
    private static final String SHUTDOWN_IPO = "android.intent.action.ACTION_SHUTDOWN_IPO";
    private static final String ACTION_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN";
    Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "intent action " + String.valueOf(intent.getAction()));
        if (SHUTDOWN_IPO.equals(intent.getAction())) {
            if (ShutdownActivity.sCountDownTimer != null) {
                Log.d(TAG, "ShutdownActivity.sCountDownTimer != null");
                ShutdownActivity.sCountDownTimer.cancel();
                ShutdownActivity.sCountDownTimer = null;
            }
            return;
        // ALPS00881041 hold a cpu wake lock, if Shutdown thread received the shutdown request, release the lock
        } else if (ACTION_SHUTDOWN.equals(intent.getAction())) {
            SchPwrWakeLock.releaseCpuWakeLock();
			Log.d(TAG,"ACTION_SHUTDOWN");
			Alarms.setNextAlertPowerOn(context,true);
            return;
        }

        mContext = context;
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
            Log.d(TAG, "SchPwrOffReceiver failed to parse the alarm from the intent");
            return;
        }

        final int millisInSeconds = 1000;
        // Intentionally verbose: always log the alarm time to provide useful
        // information in bug reports.
        long now = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss.SSS aaa", Locale.US);
        Log.d(TAG, "SchPwrOffReceiver.onReceive() id " + alarm.mId + " setFor " + format.format(new Date(alarm.mTime)));

        if (now > alarm.mTime + STALE_WINDOW * millisInSeconds) {
            Log.d(TAG, "SchPwrOffReceiver ignoring stale alarm");
            Log.d(TAG, "now = " + now);
            Log.d(TAG, "stale time = " + (alarm.mTime + STALE_WINDOW * millisInSeconds));
            return;
        }

        // Maintain a cpu wake lock until the AlarmAlert and AlarmKlaxon can
        // pick it up.
        // AlarmAlertWakeLock.acquireCpuWakeLock(context);

        /* Close dialogs and window shade */
        Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(closeDialogs);
        final int schduleTimeOff = 900;
        // Decide which activity to start based on the state of the keyguard.

        if (alarm.mId == 1) {
            Log.d(TAG, "SchPwrOffReceiver.onReceive() id " + alarm.mId + " get power on time out ");
        } else if (alarm.mId == 2) {
            boolean isInCall = false;
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            isInCall = telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
            Log.d(TAG, "SchPwrOffReceiver.onReceive() id " + alarm.mId + " in call " + isInCall);

            if (isInCall || isAlarmBoot()) {
                Log.d(TAG, "SchPwrOffReceiver.onReceive() id " + alarm.mId + " isAlarmboot= " + isAlarmBoot());
            } else {
                Intent i = new Intent(context, ShutdownActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
//                PendingIntent pendingIntent = PendingIntent.getActivity(context, getResultCode(), i,
//                        PendingIntent.FLAG_ONE_SHOT);
//                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//                am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + schduleTimeOff, pendingIntent);
                mContext.startActivity(i);
            }

            // Disable this alarm if it does not repeat.
            if (alarm.mDaysOfWeek.isRepeatSet()) {
                // Enable the next alert if there is one. The above call to
                // enableAlarm will call setNextAlert so avoid calling it twice.
                Log.d(TAG, "SchPwrOffReceiver.onReceive(): not isRepeatSet()");
                Alarms.setNextAlertPowerOff(context);
            } else {
                Log.d(TAG, "SchPwrOffReceiver.onReceive(): isRepeatSet() ");
                Alarms.enableAlarm(context, alarm.mId, false);
            }
        }
    }

    /**
     * check if is alarm boot; if it alarm boot, we don't fire the
     * shutdownactivity pop dialog.
     *
     * @return boolean true or false
     */
    private static boolean isAlarmBoot() {
        String bootReason = SystemProperties.get("sys.boot.reason");
        boolean ret = (bootReason != null && bootReason.equals("1")) ? true : false;
        return ret;
    }
}
