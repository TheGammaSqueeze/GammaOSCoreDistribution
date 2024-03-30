/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.garagemode;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.util.Utils.isEventOfType;

import android.car.builtin.job.JobSchedulerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarStatsLogHelper;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that interacts with JobScheduler through JobSchedulerHelper, controls system idleness and
 * monitor jobs which are in GarageMode interest.
 */
class GarageMode {

    private static final String TAG = CarLog.tagFor(GarageMode.class) + "_"
            + GarageMode.class.getSimpleName();

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_ON =
            "com.android.server.jobscheduler.GARAGE_MODE_ON";

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_OFF =
            "com.android.server.jobscheduler.GARAGE_MODE_OFF";

    @VisibleForTesting
    static final long JOB_SNAPSHOT_INITIAL_UPDATE_MS = 10_000; // 10 seconds

    private static final long JOB_SNAPSHOT_UPDATE_FREQUENCY_MS = 1_000; // 1 second
    private static final long USER_STOP_CHECK_INTERVAL_MS = 100; // 100 milliseconds
    private static final int ADDITIONAL_CHECKS_TO_DO = 1;
    // Values for eventlog (car_pwr_mgr_garage_mode)
    private static final int GARAGE_MODE_EVENT_LOG_START = 0;
    private static final int GARAGE_MODE_EVENT_LOG_FINISH = 1;
    private static final int GARAGE_MODE_EVENT_LOG_CANCELLED = 2;

    private final Context mContext;
    private final Controller mController;
    private final Object mLock = new Object();
    private final Handler mHandler;

    @GuardedBy("mLock")
    private boolean mGarageModeActive;
    @GuardedBy("mLock")
    private int mAdditionalChecksToDo = ADDITIONAL_CHECKS_TO_DO;
    @GuardedBy("mLock")
    private boolean mIdleCheckerIsRunning;
    @GuardedBy("mLock")
    private List<String> mPendingJobs = new ArrayList<>();

    private final GarageModeRecorder mGarageModeRecorder;

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            boolean garageModeActive;
            synchronized (mLock) {
                garageModeActive = mGarageModeActive;
            }
            if (!garageModeActive) {
                Slogf.d(TAG, "Garage Mode is inactive. Stopping the idle-job checker.");
                finish();
                return;
            }
            int numberRunning = JobSchedulerHelper.getNumberOfRunningJobsAtIdle(mContext);
            if (numberRunning > 0) {
                Slogf.d(TAG, "%d jobs are still running. Need to wait more ...", numberRunning);
                synchronized (mLock) {
                    mAdditionalChecksToDo = ADDITIONAL_CHECKS_TO_DO;
                }
            } else {
                // No idle-mode jobs are running.
                // Are there any scheduled idle jobs that could run now?
                int numberReadyToRun = JobSchedulerHelper.getNumberOfPendingJobs(mContext);
                if (numberReadyToRun == 0) {
                    Slogf.d(TAG, "No jobs are running. No jobs are pending. Exiting Garage Mode.");
                    finish();
                    return;
                }
                int numAdditionalChecks;
                synchronized (mLock) {
                    numAdditionalChecks = mAdditionalChecksToDo;
                    if (mAdditionalChecksToDo > 0) {
                        mAdditionalChecksToDo--;
                    }
                }
                if (numAdditionalChecks == 0) {
                    Slogf.d(TAG, "No jobs are running. Waited too long for %d pending jobs. Exiting"
                            + " Garage Mode.", numberReadyToRun);
                    finish();
                    return;
                }
                Slogf.d(TAG, "No jobs are running. Waiting %d more cycles for %d pending jobs.",
                        numAdditionalChecks, numberReadyToRun);
            }
            mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_UPDATE_FREQUENCY_MS);
        }
    };

    private final Runnable mStartBackgroundUsers = new Runnable() {
        @Override
        public void run() {
            ArrayList<Integer> startedUsers = CarLocalServices.getService(CarUserService.class)
                    .startAllBackgroundUsersInGarageMode();
            Slogf.i(TAG, "Started background user during garage mode: %s", startedUsers);
            synchronized (mLock) {
                // Stop stopping background users if there is any users left from last Garage mode,
                // they would be stopped later.
                mBackgroundUserStopInProcess = false;
                mStartedBackgroundUsers.addAll(startedUsers);
            }
        }
    };

    private final Runnable mStopUserCheckRunnable = new Runnable() {

        @Override
        public void run() {
            int userToStop = UserHandle.SYSTEM.getIdentifier(); // BG user never becomes system user
            synchronized (mLock) {
                if (mStartedBackgroundUsers.isEmpty() || !mBackgroundUserStopInProcess) return;
                userToStop = mStartedBackgroundUsers.valueAt(0);
            }
            // All jobs done or stopped.
            if (JobSchedulerHelper.getNumberOfRunningJobsAtIdle(mContext) == 0) {
                // Keep user until job scheduling is stopped. Otherwise, it can crash jobs.
                if (userToStop != UserHandle.SYSTEM.getIdentifier()) {
                    CarLocalServices.getService(CarUserService.class)
                            .stopBackgroundUserInGagageMode(userToStop);
                    synchronized (mLock) {
                        Slogf.i(TAG, "Stopping background user:%d remaining users:%d", userToStop,
                                mStartedBackgroundUsers.size() - 1);
                    }
                }
                synchronized (mLock) {
                    mStartedBackgroundUsers.remove(userToStop);
                    if (mStartedBackgroundUsers.isEmpty()) {
                        Slogf.i(TAG, "All background users have stopped");
                        mBackgroundUserStopInProcess = false;
                        return;
                    }
                }
            } else {
                // Poll again later
                mHandler.postDelayed(mStopUserCheckRunnable, USER_STOP_CHECK_INTERVAL_MS);
            }
        }
    };

    @GuardedBy("mLock")
    private Runnable mCompletor;
    @GuardedBy("mLock")
    private ArraySet<Integer> mStartedBackgroundUsers = new ArraySet<>();

    /**
     * True when stopping of the background users is in process.
     *
     * <p> When garage mode exits, all background users started during GarageMode would be stopped
     * one by one. mBackgroundUserStopInProcess would be true when stopping of the background users
     * is in process.
     */
    @GuardedBy("mLock")
    private boolean mBackgroundUserStopInProcess;

    GarageMode(Context context, Controller controller) {
        mContext = context;
        mController = controller;
        mGarageModeActive = false;
        mHandler = controller.getHandler();
        mGarageModeRecorder = new GarageModeRecorder(Clock.systemUTC());
    }

    void init() {
        UserLifecycleEventFilter userStoppedEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED).build();
        CarLocalServices.getService(CarUserService.class)
                .addUserLifecycleListener(userStoppedEventFilter, mUserLifecycleListener);
    }

    void release() {
        CarLocalServices.getService(CarUserService.class)
                .removeUserLifecycleListener(mUserLifecycleListener);
    }

    /**
     * When background users are queued to stop, this user lifecycle listener will ensure to stop
     * them one by one by queuing next user when previous user is stopped.
     */
    private final UserLifecycleListener mUserLifecycleListener = new UserLifecycleListener() {
        @Override
        public void onEvent(UserLifecycleEvent event) {
            if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_STOPPED)) {
                return;
            }

            synchronized (mLock) {
                if (mBackgroundUserStopInProcess) {
                    mHandler.removeCallbacks(mStopUserCheckRunnable);
                    Slogf.i(TAG, "Background user stopped event received. User Id: %d. Queueing to "
                            + "stop next background user.", event.getUserId());
                    mHandler.post(mStopUserCheckRunnable);
                }
            }
        }
    };

    boolean isGarageModeActive() {
        synchronized (mLock) {
            return mGarageModeActive;
        }
    }

    @VisibleForTesting
    ArraySet<Integer> getStartedBackgroundUsers() {
        synchronized (mLock) {
            return mStartedBackgroundUsers;
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            mGarageModeRecorder.dump(writer);
            if (!mGarageModeActive) { //TODO(b/217739337) print value of mGarageModeActive
                return;
            }
            writer.printf("GarageMode idle checker is %srunning\n",
                    (mIdleCheckerIsRunning ? "" : "not "));
        }

        int numJobs = JobSchedulerHelper.getNumberOfRunningJobsAtIdle(mContext);
        if (numJobs > 0) {
            writer.printf("GarageMode is waiting for %d jobs:\n", numJobs);
        } else {
            numJobs = JobSchedulerHelper.getNumberOfPendingJobs(mContext);
            writer.printf("GarageMode is waiting for %d pending idle jobs:\n", numJobs);
        }
    }

    void enterGarageMode(Runnable completor) {
        Slogf.i(TAG, "Entering GarageMode");
        CarPowerManagementService carPowerService = CarLocalServices.getService(
                CarPowerManagementService.class);
        if (carPowerService != null
                && carPowerService.garageModeShouldExitImmediately()) {
            if (completor != null) {
                completor.run();
            }
            synchronized (mLock) {
                mGarageModeActive = false;
            }
            return;
        }
        synchronized (mLock) {
            mGarageModeActive = true;
            mCompletor = completor;
        }
        broadcastSignalToJobScheduler(true);
        mGarageModeRecorder.startSession();
        CarStatsLogHelper.logGarageModeStart();
        EventLogHelper.writeGarageModeEvent(GARAGE_MODE_EVENT_LOG_START);
        startMonitoringThread();
        mHandler.post(mStartBackgroundUsers);
    }

    void cancel() {
        broadcastSignalToJobScheduler(false);
        synchronized (mLock) {
            if (mCompletor != null) {
                mCompletor.run();
                mCompletor = null;
            }
            if (mGarageModeActive) {
                cleanupGarageModeLocked();
                Slogf.i(TAG, "GarageMode is cancelled");
                EventLogHelper.writeGarageModeEvent(GARAGE_MODE_EVENT_LOG_CANCELLED);
                mGarageModeRecorder.cancelSession();
            }
        }
    }

    void finish() {
        synchronized (mLock) {
            if (!mIdleCheckerIsRunning) {
                Slogf.i(TAG, "Finishing Garage Mode. Idle checker is not running.");
                return;
            }
            mIdleCheckerIsRunning = false;
        }
        broadcastSignalToJobScheduler(false);
        EventLogHelper.writeGarageModeEvent(GARAGE_MODE_EVENT_LOG_FINISH);
        CarStatsLogHelper.logGarageModeStop();
        mGarageModeRecorder.finishSession();
        synchronized (mLock) {
            if (mCompletor != null) {
                mCompletor.run();
                mCompletor = null;
            }
            cleanupGarageModeLocked();
            Slogf.i(TAG, "GarageMode is completed normally");
        }
    }

    @GuardedBy("mLock")
    private void cleanupGarageModeLocked() {
        Slogf.i(TAG, "Cleaning up GarageMode");
        mGarageModeActive = false;
        stopMonitoringThread();
        if (mIdleCheckerIsRunning) {
            // The idle checker has not completed.
            // Schedule it now so it completes promptly.
            mHandler.post(mRunnable);
        }
        startBackgroundUserStoppingLocked();
    }

    @GuardedBy("mLock")
    private void startBackgroundUserStoppingLocked() {
        if (!mStartedBackgroundUsers.isEmpty() && !mBackgroundUserStopInProcess) {
            Slogf.i(TAG, "Stopping of background user queued. Total background users to stop: "
                    + "%d", mStartedBackgroundUsers.size());
            mHandler.post(mStopUserCheckRunnable);
            mBackgroundUserStopInProcess = true;
        }
    }

    private void broadcastSignalToJobScheduler(boolean enableGarageMode) {
        Intent i = new Intent();
        i.setAction(enableGarageMode ? ACTION_GARAGE_MODE_ON : ACTION_GARAGE_MODE_OFF);
        i.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_NO_ABORT);
        mController.sendBroadcast(i);
    }

    private void startMonitoringThread() {
        synchronized (mLock) {
            mIdleCheckerIsRunning = true;
            mAdditionalChecksToDo = ADDITIONAL_CHECKS_TO_DO;
        }
        mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_INITIAL_UPDATE_MS);
    }

    private void stopMonitoringThread() {
        mHandler.removeCallbacks(mRunnable);
    }
}
