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

package com.android.adservices.service;

/**
 * Configs for AdServices.
 * These configs will be backed by PH Flags.
 */
public class AdServicesConfig {
    /**
     * Job Id for idle maintenance job ({@link MaintenanceJobService}).
     */
    public static final int MAINTENANCE_JOB_ID = 1;
    public static long MAINTENANCE_JOB_PERIOD_MS = 86_400_000; // 1 day.
    public static long MAINTENANCE_JOB_FLEX_MS = 3 * 60 * 1000;  // 3 hours.

    /**
     * Returns the max time period (in millis) between each idle maintenance job run.
     */
    public static long getMaintenanceJobPeriodMs() {
        return MAINTENANCE_JOB_PERIOD_MS;
    }

    /**
     * Returns flex for the Epoch computation job in Millisecond.
     */
    public static long getMaintenanceJobFlexMs() {
        return MAINTENANCE_JOB_FLEX_MS;
    }

    /**
     * Job Id for Topics Epoch Computation Job ({@link EpochJobService})
     */
    public static final int TOPICS_EPOCH_JOB_ID = 2;
    public static long TOPICS_EPOCH_JOB_PERIOD_MS = 7 * 86_400_000; // 7 days.
    public static long TOPICS_EPOCH_JOB_FLEX_MS = 5 * 60 * 1000; // 3 hours.

    /**
     * Returns the max time period (in millis) between each epoch computation job run.
     */
    public static long getTopicsEpochJobPeriodMs() {
        return TOPICS_EPOCH_JOB_PERIOD_MS;
    }

    /**
     * Returns flex for the Epoch computation job in Millisecond.
     */
    public static long getTopicsEpochJobFlexMs() {
        return TOPICS_EPOCH_JOB_FLEX_MS;
    }
}
