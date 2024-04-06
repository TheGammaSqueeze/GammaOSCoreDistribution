package com.khadas.schpwronoff;

import android.content.Context;
import android.os.PowerManager;

import android.util.Log;

/**
 * Hold a wakelock that can be acquired in the AlarmReceiver and released in the
 * AlarmAlert activity
 */
class SchPwrWakeLock {
    private static final String TAG = "SchPwrWakeLock";
    private static PowerManager.WakeLock sScreenOnWakeLock;
    // M: ALPS00850405, ALPS00881041 hold a cpu wake lock
    private static PowerManager.WakeLock sCpuWakeLock;

    static void acquireCpuWakeLock(Context context) {
        Log.d(TAG, "Acquiring screen on and cpu wake lock");
        if (sScreenOnWakeLock != null && sCpuWakeLock != null) {
            return;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        sScreenOnWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, "SchPwrOnOff");
        sScreenOnWakeLock.acquire();
        // M: ALPS00850405, ALPS00881041 hold a cpu wake lock
        // Wake lock that ensures that the CPU is running. The screen might not
        // be on.
        sCpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShutdownActivity");
        sCpuWakeLock.setReferenceCounted(false);
        sCpuWakeLock.acquire();
    }

    static void releaseCpuWakeLock() {
        if (sScreenOnWakeLock != null) {
            Log.d(TAG, "Releasing screen on wake lock");
            sScreenOnWakeLock.release();
            sScreenOnWakeLock = null;
        }
        if (sCpuWakeLock != null) {
            Log.d(TAG, "Releasing cpu wake lock");
            sCpuWakeLock.release();
            sCpuWakeLock = null;
        }
    }
}
