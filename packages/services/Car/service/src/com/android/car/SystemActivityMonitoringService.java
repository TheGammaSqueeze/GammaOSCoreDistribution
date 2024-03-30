/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.app.ActivityManagerHelper.ProcessObserverCallback;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Service to monitor AMS for new Activity or Service launching.
 */
public class SystemActivityMonitoringService implements CarServiceBase {
    private static final int INVALID_STACK_ID = -1;
    private final Context mContext;
    private final ProcessObserverCallback mProcessObserver = new ProcessObserver();

    private final HandlerThread mMonitorHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final ActivityMonitorHandler mHandler = new ActivityMonitorHandler(
            mMonitorHandlerThread.getLooper(), this);

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final Map<Integer, Set<Integer>> mForegroundUidPids = new ArrayMap<>();

    public SystemActivityMonitoringService(Context context) {
        mContext = context;
    }

    @Override
    public void init() {
        // Monitoring both listeners are necessary as there are cases where one listener cannot
        // monitor activity change.
        ActivityManagerHelper.registerProcessObserverCallback(mProcessObserver);
    }

    @Override
    public void release() {
        ActivityManagerHelper.unregisterProcessObserverCallback(mProcessObserver);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*SystemActivityMonitoringService*");
        writer.println(" Top Tasks per display:");
        synchronized (mLock) {
            writer.println(" Foreground uid-pids:");
            for (Integer key : mForegroundUidPids.keySet()) {
                Set<Integer> pids = mForegroundUidPids.get(key);
                if (pids == null) {
                    continue;
                }
                writer.println("uid:" + key + ", pids:" + Arrays.toString(pids.toArray()));
            }
        }
    }

    public boolean isInForeground(int pid, int uid) {
        synchronized (mLock) {
            Set<Integer> pids = mForegroundUidPids.get(uid);
            if (pids == null) {
                return false;
            }
            if (pids.contains(pid)) {
                return true;
            }
        }
        return false;
    }

    private void handleForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        synchronized (mLock) {
            if (foregroundActivities) {
                Set<Integer> pids = mForegroundUidPids.get(uid);
                if (pids == null) {
                    pids = new ArraySet<Integer>();
                    mForegroundUidPids.put(uid, pids);
                }
                pids.add(pid);
            } else {
                doHandlePidGoneLocked(pid, uid);
            }
        }
    }

    private void handleProcessDied(int pid, int uid) {
        synchronized (mLock) {
            doHandlePidGoneLocked(pid, uid);
        }
    }

    @GuardedBy("mLock")
    private void doHandlePidGoneLocked(int pid, int uid) {
        Set<Integer> pids = mForegroundUidPids.get(uid);
        if (pids != null) {
            pids.remove(pid);
            if (pids.isEmpty()) {
                mForegroundUidPids.remove(uid);
            }
        }
    }

    private class ProcessObserver extends ProcessObserverCallback {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            if (Slogf.isLoggable(CarLog.TAG_AM, Log.INFO)) {
                Slogf.i(CarLog.TAG_AM,
                        String.format("onForegroundActivitiesChanged uid %d pid %d fg %b",
                                uid, pid, foregroundActivities));
            }
            mHandler.requestForegroundActivitiesChanged(pid, uid, foregroundActivities);
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            mHandler.requestProcessDied(pid, uid);
        }
    }

    private static final class ActivityMonitorHandler extends Handler {
        private static final String TAG = ActivityMonitorHandler.class.getSimpleName();

        private static final int MSG_FOREGROUND_ACTIVITIES_CHANGED = 1;
        private static final int MSG_PROCESS_DIED = 2;

        private final WeakReference<SystemActivityMonitoringService> mService;

        private ActivityMonitorHandler(Looper looper, SystemActivityMonitoringService service) {
            super(looper);
            mService = new WeakReference<SystemActivityMonitoringService>(service);
        }

        private void requestForegroundActivitiesChanged(int pid, int uid,
                boolean foregroundActivities) {
            Message msg = obtainMessage(MSG_FOREGROUND_ACTIVITIES_CHANGED, pid, uid,
                    Boolean.valueOf(foregroundActivities));
            sendMessage(msg);
        }

        private void requestProcessDied(int pid, int uid) {
            Message msg = obtainMessage(MSG_PROCESS_DIED, pid, uid);
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            SystemActivityMonitoringService service = mService.get();
            if (service == null) {
                Slogf.i(TAG, "handleMessage null service");
                return;
            }
            switch (msg.what) {
                case MSG_FOREGROUND_ACTIVITIES_CHANGED:
                    service.handleForegroundActivitiesChanged(msg.arg1, msg.arg2,
                            (Boolean) msg.obj);
                    break;
                case MSG_PROCESS_DIED:
                    service.handleProcessDied(msg.arg1, msg.arg2);
                    break;
            }
        }
    }
}
