package com.khadas.schpwronoff;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

public class AlarmInitReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmInitReceiver";

    /**
     * Sets alarm on ACTION_BOOT_COMPLETED. Resets alarm on TIME_SET, TIMEZONE_CHANGED
     * @param context Context
     * @param intent Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
       Log.d(TAG, "AlarmInitReceiver" + action);
        int userId = UserHandle.myUserId();  
     Log.d(TAG, "userId = " + userId);
        if (userId != UserHandle.USER_OWNER) {
     Log.d(TAG, "not owner , return ,don't start AlarmReceiverService");
            return;
        }
        if (context.getContentResolver() == null) {
      Log.e(TAG, "AlarmInitReceiver: FAILURE unable to get content resolver.  Alarms inactive.");
            return;
        }
        AlarmReceiverService.processBroadcastIntent(context, intent);
    }
}
