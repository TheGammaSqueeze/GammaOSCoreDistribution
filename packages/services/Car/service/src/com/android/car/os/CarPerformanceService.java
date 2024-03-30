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

package com.android.car.os;

import static android.car.PlatformVersion.VERSION_CODES;
import static android.car.os.CpuAvailabilityMonitoringConfig.CPUSET_ALL;
import static android.car.os.CpuAvailabilityMonitoringConfig.CPUSET_BACKGROUND;
import static android.car.os.CpuAvailabilityMonitoringConfig.IGNORE_PERCENT_LOWER_BOUND;
import static android.car.os.CpuAvailabilityMonitoringConfig.IGNORE_PERCENT_UPPER_BOUND;
import static android.car.os.CpuAvailabilityMonitoringConfig.TIMEOUT_ACTION_NOTIFICATION;
import static android.car.os.CpuAvailabilityMonitoringConfig.TIMEOUT_ACTION_REMOVE;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.internal.util.VersionUtils.assertPlatformVersionAtLeast;

import android.annotation.NonNull;
import android.car.Car;
import android.car.builtin.os.BinderHelper;
import android.car.builtin.util.Slogf;
import android.car.os.CpuAvailabilityMonitoringConfig;
import android.car.os.ICarPerformanceService;
import android.car.os.ICpuAvailabilityChangeListener;
import android.car.os.ThreadPolicyWithPriority;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.watchdog.CarWatchdogService;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Service to implement CarPerformanceManager API.
 */
public final class CarPerformanceService extends ICarPerformanceService.Stub
        implements CarServiceBase {
    static final String TAG = CarLog.tagFor(CarPerformanceService.class);

    private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);

    private CarWatchdogService mCarWatchdogService;
    private final Context mContext;
    private final RemoteCallbackList<ICpuAvailabilityChangeListener>
            mCpuAvailabilityChangeListeners = new RemoteCallbackList<>();

    public CarPerformanceService(Context context) {
        mContext = context;
    }

    @Override
    public void init() {
        mCarWatchdogService = CarLocalServices.getService(CarWatchdogService.class);
        // TODO(b/217422127): Start performance monitoring on a looper handler.
        if (DEBUG) {
            Slogf.d(TAG, "CarPerformanceService is initialized");
        }
    }

    @Override
    public void release() {
        // TODO(b/156400843): Disconnect from the watchdog daemon helper instance.
        mCpuAvailabilityChangeListeners.kill();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("*%s*\n", getClass().getSimpleName());
        writer.increaseIndent();
        writer.println("CPU availability change listeners:");
        writer.increaseIndent();
        BinderHelper.dumpRemoteCallbackList(mCpuAvailabilityChangeListeners, writer);
        writer.decreaseIndent();
        writer.decreaseIndent();
    }

    /**
     * Adds {@link android.car.performance.ICpuAvailabilityChangeListener} for CPU availability
     * change notifications.
     */
    @Override
    public void addCpuAvailabilityChangeListener(@NonNull CpuAvailabilityMonitoringConfig config,
            @NonNull ICpuAvailabilityChangeListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_COLLECT_CAR_CPU_INFO);
        Objects.requireNonNull(config, "Configuration must be non-null");
        Objects.requireNonNull(listener, "Listener must be non-null");
        verifyCpuAvailabilityMonitoringConfig(config);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        CpuAvailabilityChangeListenerInfo listenerInfo =
                new CpuAvailabilityChangeListenerInfo(config, callingPid, callingUid);
        if (!mCpuAvailabilityChangeListeners.register(listener, listenerInfo)) {
            Slogf.w(TAG,
                    "Failed to add CPU availability change listener %s as it is already registered",
                    listenerInfo);
            throw new IllegalStateException(
                    "Failed to add CPU availability change listener as it is already registered"
                    + listenerInfo);
        }
    }

    /**
     * Removes the previously added {@link android.car.performance.ICpuAvailabilityChangeListener}.
     */
    @Override
    public void removeCpuAvailabilityChangeListener(ICpuAvailabilityChangeListener listener) {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_COLLECT_CAR_CPU_INFO);
        Objects.requireNonNull(listener, "Listener must be non-null");

        // Note: RemoteCallbackList already handles removing the listener on binderDeath. However,
        // when any internal state needs to be cleared for a listener beyond just unregistering
        // the listener, override RemoteCallbackList.onCallbackDied methods to clean up the internal
        // state on binder death and on removeCpuAvailabilityChangeListener.
        mCpuAvailabilityChangeListeners.unregister(listener);
    }

    /**
     * Sets the thread priority for a specific thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws IllegalArgumentException If the given policy/priority is not valid.
     * @throws IllegalStateException If the provided tid does not belong to the calling process.
     * @throws RemoteException If binder error happens.
     * @throws SecurityException If permission check failed.
     * @throws ServiceSpecificException If the operation failed.
     * @throws UnsupportedOperationException If the current android release doesn't support the API.
     */
    @Override
    public void setThreadPriority(int tid, ThreadPolicyWithPriority threadPolicyWithPriority)
            throws RemoteException {
        assertPlatformVersionAtLeast(VERSION_CODES.TIRAMISU_1);
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MANAGE_THREAD_PRIORITY);

        int pid = Binder.getCallingPid();
        int uid = Binder.getCallingUid();
        mCarWatchdogService.setThreadPriority(pid, tid, uid, threadPolicyWithPriority.getPolicy(),
                threadPolicyWithPriority.getPriority());
    }

    /**
     * Gets the thread scheduling policy and priority for the specified thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws IllegalStateException If the operation failed or the provided tid does not belong to
     *         the calling process.
     * @throws RemoteException If binder error happens.
     * @throws SecurityException If permission check failed.
     * @throws UnsupportedOperationException If the current android release doesn't support the API.
     */
    @Override
    public ThreadPolicyWithPriority getThreadPriority(int tid) throws RemoteException {
        assertPlatformVersionAtLeast(VERSION_CODES.TIRAMISU_1);
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MANAGE_THREAD_PRIORITY);

        int pid = Binder.getCallingPid();
        int uid = Binder.getCallingUid();
        try {
            int[] result = mCarWatchdogService.getThreadPriority(pid, tid, uid);
            return new ThreadPolicyWithPriority(result[0], result[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "current scheduling policy doesn't support getting priority, error: ", e);
        }
    }

    private void verifyCpuAvailabilityMonitoringConfig(CpuAvailabilityMonitoringConfig config) {
        int lowerBoundPercent = config.getLowerBoundPercent();
        int upperBoundPercent = config.getUpperBoundPercent();

        Preconditions.checkArgument(lowerBoundPercent != IGNORE_PERCENT_LOWER_BOUND
                        || upperBoundPercent == IGNORE_PERCENT_UPPER_BOUND,
                "Cannot ignore both lower bound percent(%d) and upper bound percent(%d) values",
                lowerBoundPercent, upperBoundPercent);

        Preconditions.checkArgument(lowerBoundPercent > 0 && upperBoundPercent < 100
                        && lowerBoundPercent < upperBoundPercent,
                "Must provide valid lower bound percent(%d) and upper bound percent(%d) values",
                lowerBoundPercent, upperBoundPercent);

        int cpuset = config.getCpuset();
        Preconditions.checkArgumentInRange(cpuset, CPUSET_ALL, CPUSET_BACKGROUND, "cpuset");

        int timeoutAction = config.getTimeoutAction();
        Preconditions.checkArgumentInRange(timeoutAction, TIMEOUT_ACTION_NOTIFICATION,
                TIMEOUT_ACTION_REMOVE, "timeout action");
    }

    private static final class CpuAvailabilityChangeListenerInfo {
        public final CpuAvailabilityMonitoringConfig config;
        public final int pid;
        public final int uid;

        CpuAvailabilityChangeListenerInfo(@NonNull CpuAvailabilityMonitoringConfig config, int pid,
                int uid) {
            this.config = config;
            this.pid = pid;
            this.uid = uid;
        }

        @Override
        public String toString() {
            return new StringBuilder("CpuAvailabilityChangeListenerInfo{ ")
                    .append(", config = ").append(config)
                    .append(", pid = ").append(pid)
                    .append(", uid = ").append(uid)
                    .append(" }").toString();
        }
    }
}
