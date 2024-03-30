/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.appsearch.contactsindexer;

import android.annotation.UserIdInt;
import android.app.appsearch.util.LogUtil;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.LocalManagerRegistry;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ContactsIndexerMaintenanceService extends JobService {
    private static final String TAG = "ContactsIndexerMaintena";

    /**
     * Generate job ids in the range (MIN_INDEXER_JOB_ID, MAX_INDEXER_JOB_ID) to avoid conflicts
     * with other jobs scheduled by the system service. The range corresponds to 21475 job ids,
     * which is the maximum number of user ids in the system.
     *
     * @see com.android.server.pm.UserManagerService#MAX_USER_ID
     */
    public static final int MIN_INDEXER_JOB_ID = 16942831; // corresponds to ag/16942831
    private static final int MAX_INDEXER_JOB_ID = 16964306; // 16942831 + 21475

    private static final String EXTRA_USER_ID = "user_id";

    private static final Executor EXECUTOR = new ThreadPoolExecutor(/*corePoolSize=*/ 1,
            /*maximumPoolSize=*/ 1, /*keepAliveTime=*/ 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());

    /**
     * A mapping of userId-to-CancellationSignal. Since we schedule a separate job for each user,
     * this JobService might be executing simultaneously for the various users, so we need to keep
     * track of the cancellation signal for each user update so we stop the appropriate update
     * when necessary.
     */
    @GuardedBy("mSignals")
    private final SparseArray<CancellationSignal> mSignals = new SparseArray<>();

    /**
     * Schedules a full update job for the given device-user.
     *
     * @param userId Device user id for whom the full update job should be scheduled.
     * @param periodic True to indicate that the job should be repeated.
     * @param intervalMillis Millisecond interval for which this job should repeat.
     */
    static void scheduleFullUpdateJob(Context context, @UserIdInt int userId,
            boolean periodic, long intervalMillis) {
        int jobId = MIN_INDEXER_JOB_ID + userId;
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        ComponentName component =
                new ComponentName(context, ContactsIndexerMaintenanceService.class);
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(EXTRA_USER_ID, userId);
        JobInfo.Builder jobInfoBuilder =
                new JobInfo.Builder(jobId, component)
                        .setExtras(extras)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setPersisted(true);

        if (periodic) {
            // Specify a flex value of 1/2 the interval so that the job is scheduled to run
            // in the [interval/2, interval) time window, assuming the other conditions are
            // met. This avoids the scenario where the next full-update job is started within
            // a short duration of the previous run.
            jobInfoBuilder.setPeriodic(intervalMillis, /*flexMillis=*/ intervalMillis/2);
        }
        JobInfo jobInfo = jobInfoBuilder.build();
        JobInfo pendingJobInfo = jobScheduler.getPendingJob(jobId);
        // Don't reschedule a pending job if the parameters haven't changed.
        if (jobInfo.equals(pendingJobInfo)) {
            return;
        }
        jobScheduler.schedule(jobInfo);
        if (LogUtil.DEBUG) {
            Log.v(TAG, "Scheduled full update job " + jobId + " for user " + userId);
        }
    }

    static void cancelFullUpdateJob(Context context, @UserIdInt int userId) {
        int jobId = MIN_INDEXER_JOB_ID + userId;
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        jobScheduler.cancel(jobId);
        if (LogUtil.DEBUG) {
            Log.v(TAG, "Canceled full update job " + jobId + " for user " + userId);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        int userId = params.getExtras().getInt(EXTRA_USER_ID, /*defaultValue=*/ -1);
        if (userId == -1) {
            return false;
        }

        if (LogUtil.DEBUG) {
            Log.v(TAG, "Full update job started for user " + userId);
        }
        final CancellationSignal oldSignal;
        synchronized (mSignals) {
            oldSignal = mSignals.get(userId);
        }
        if (oldSignal != null) {
            // This could happen if we attempt to schedule a new job for the user while there's
            // one already running.
            Log.w(TAG, "Old update job still running for user " + userId);
            oldSignal.cancel();
        }
        final CancellationSignal signal = new CancellationSignal();
        synchronized (mSignals) {
            mSignals.put(userId, signal);
        }
        EXECUTOR.execute(() -> {
            ContactsIndexerManagerService.LocalService service =
                    LocalManagerRegistry.getManager(
                            ContactsIndexerManagerService.LocalService.class);
            service.doFullUpdateForUser(userId, signal);
            jobFinished(params, signal.isCanceled());
            synchronized (mSignals) {
                if (signal == mSignals.get(userId)) {
                    mSignals.remove(userId);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        final int userId = params.getExtras().getInt(EXTRA_USER_ID, /* defaultValue */ -1);
        if (userId == -1) {
            return false;
        }
        // This will only run on S+ builds, so no need to do a version check.
        if (LogUtil.DEBUG) {
            Log.d(TAG,
                    "Stopping update job for user " + userId + " because "
                            + params.getStopReason());
        }
        synchronized (mSignals) {
            final CancellationSignal signal = mSignals.get(userId);
            if (signal != null) {
                signal.cancel();
                mSignals.remove(userId);
                // We had to stop the job early. Request reschedule.
                return true;
            }
        }
        Log.e(TAG, "JobScheduler stopped an update that wasn't happening...");
        return false;
    }
}
