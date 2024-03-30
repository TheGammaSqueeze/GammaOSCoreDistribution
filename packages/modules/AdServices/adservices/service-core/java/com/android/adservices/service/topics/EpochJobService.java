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

package com.android.adservices.service.topics;

import static com.android.adservices.service.AdServicesConfig.TOPICS_EPOCH_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesConfig;

/**
 * Epoch computation job. This will be run approximately once per epoch to
 * compute Topics.
 */
public final class EpochJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d("EpochJobService.onStartJob");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("EpochJobService.onStopJob");
        return false;
    }

    /** Schedule the Job */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job = new JobInfo.Builder(TOPICS_EPOCH_JOB_ID,
                new ComponentName(context, EpochJobService.class))
                .setRequiresCharging(true)
                .setPeriodic(AdServicesConfig.getTopicsEpochJobPeriodMs(),
                        AdServicesConfig.getTopicsEpochJobFlexMs())
                .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Epoch job ...");
    }
}
