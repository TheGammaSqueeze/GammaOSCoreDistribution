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

package com.android.car.systeminterface;

import static com.android.car.systeminterface.SystemPowerControlHelper.SUSPEND_RESULT_SUCCESS;

import android.car.builtin.power.PowerManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Pair;

import com.android.car.procfsinspector.ProcessInfo;
import com.android.car.procfsinspector.ProcfsInspector;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Interface that abstracts system status (booted, sleeping, ...) operations
 */
public interface SystemStateInterface {
    static final String TAG = SystemStateInterface.class.getSimpleName();
    void shutdown();
    /**
     * Put the device into Suspend to RAM mode
     * @return boolean true if suspend succeeded
     */
    boolean enterDeepSleep();

    /**
     * Puts the device into Suspend-to-disk (hibernation)
     *
     * @return boolean {@code true} if hibernation succeeded
    */
    boolean enterHibernation();

    void scheduleActionForBootCompleted(Runnable action, Duration delay);

    default boolean isWakeupCausedByTimer() {
        //TODO bug: 32061842, check wake up reason and do necessary operation information should
        // come from kernel. it can be either power on or wake up for maintenance
        // power on will involve GPIO trigger from power controller
        // its own wakeup will involve timer expiration.
        return false;
    }

    /**
     * Gets whether the device supports deep sleep
     */
    default boolean isSystemSupportingDeepSleep() {
        return true;
    }

    /**
     * Gets whether the device supports hibernation
     */
    default boolean isSystemSupportingHibernation() {
        return true;
    }

    /**
     * @deprecated see {@link ProcfsInspector}
     */
    @Deprecated
    default List<ProcessInfo> getRunningProcesses() {
        return ProcfsInspector.readProcessTable();
    }

    /**
     * Default implementation that is used internally.
     */
    @VisibleForTesting
    class DefaultImpl implements SystemStateInterface {
        private static final int MAX_WAIT_FOR_HELPER_SEC = 10;
        private static final Duration MIN_BOOT_COMPLETE_ACTION_DELAY = Duration.ofSeconds(10);
        private static final int SUSPEND_TRY_TIMEOUT_MS = 1_000;

        private final Context mContext;
        private List<Pair<Runnable, Duration>> mActionsList = new ArrayList<>();
        private ScheduledExecutorService mExecutorService;
        private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                    for (Pair<Runnable, Duration> action : mActionsList) {
                        mExecutorService.schedule(action.first,
                                action.second.toMillis(), TimeUnit.MILLISECONDS);
                    }
                }
            }
        };

        @VisibleForTesting
        public DefaultImpl(Context context) {
            mContext = context;
        }

        @Override
        public void shutdown() {
            PowerManagerHelper.shutdown(mContext, /* confirm= */ false , /* reason= */ null,
                    /* wait= */ true);
        }

        @Override
        public boolean enterDeepSleep() {
            // TODO(b/32061842) Set wake up time via VHAL

            boolean deviceEnteredSleep = false;
            try {
                int retVal = SystemPowerControlHelper.forceDeepSleep();
                deviceEnteredSleep = retVal == SUSPEND_RESULT_SUCCESS;
            } catch (Exception e) {
                Slogf.e(TAG, "Unable to enter deep sleep", e);
            }
            return deviceEnteredSleep;
        }

        @Override
        public boolean enterHibernation() {
            boolean deviceHibernated = false;
            try {
                int retVal = SystemPowerControlHelper.forceHibernate();
                deviceHibernated = retVal == SUSPEND_RESULT_SUCCESS;
            } catch (Exception e) {
                Slogf.e(TAG, "Unable to enter hibernation", e);
            }
            return deviceHibernated;
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay) {
            if (MIN_BOOT_COMPLETE_ACTION_DELAY.compareTo(delay) < 0) {
                // TODO: consider adding some degree of randomness here
                delay = MIN_BOOT_COMPLETE_ACTION_DELAY;
            }
            if (mActionsList.isEmpty()) {
                final int corePoolSize = 1;
                mExecutorService = Executors.newScheduledThreadPool(corePoolSize);
                IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
                mContext.registerReceiver(mBroadcastReceiver, intentFilter,
                        Context.RECEIVER_NOT_EXPORTED);
            }
            mActionsList.add(Pair.create(action, delay));
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            return SystemPowerControlHelper.isSystemSupportingDeepSleep();
        }

        @Override
        public boolean isSystemSupportingHibernation() {
            return SystemPowerControlHelper.isSystemSupportingHibernation();
        }
    }
}
