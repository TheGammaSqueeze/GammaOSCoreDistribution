/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.app;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.annotation.AddedInOrBefore;
import android.car.annotation.ApiRequirements;
import android.car.user.CarUserManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

/**
 * API to manage {@link android.app.Activity} in Car.
 *
 * @hide
 */
@SystemApi
@TestApi
public final class CarActivityManager extends CarManagerBase {
    private static final String TAG = CarUserManager.class.getSimpleName();

    /** Indicates that the operation was successful. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int RESULT_SUCCESS = 0;
    /** Indicates that the operation was failed with the unknown reason. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int RESULT_FAILURE = -1;
    /**
     * Indicates that the operation was failed because the requester isn't the current user or
     * the system user
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int RESULT_INVALID_USER = -2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "RESULT_", value = {
            RESULT_SUCCESS,
            RESULT_FAILURE,
            RESULT_INVALID_USER,
    })
    @Target({ElementType.TYPE_USE})
    public @interface ResultTypeEnum {}

    /**
     * Internal error code for throwing {@link ActivityNotFoundException} from service.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int ERROR_CODE_ACTIVITY_NOT_FOUND = -101;

    private final ICarActivityService mService;
    private IBinder mTaskMonitorToken;

    /**
     * @hide
     */
    public CarActivityManager(@NonNull Car car, @NonNull IBinder service) {
        this(car, ICarActivityService.Stub.asInterface(service));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public CarActivityManager(@NonNull Car car, @NonNull ICarActivityService service) {
        super(car);

        mService = service;
    }

    /**
     * Designates the given {@code activity} to be launched in {@code TaskDisplayArea} of
     * {@code featureId} in the display of {@code displayId}.
     * <p>Note: this will not affect the existing {@link Activity}.
     * Note: You can map assign {@code Activity} to one {@code TaskDisplayArea} only. If
     * you assign it to the multiple {@code TaskDisplayArea}s, then the last one wins.
     * Note: The requester should be the current user or the system user, if not, the operation will
     * be failed with {@code RESULT_INVALID_USER}.
     *
     * @param activity {@link Activity} to designate
     * @param displayId {@code Display} where {@code TaskDisplayArea} is located in
     * @param featureId {@code TaskDisplayArea} where {@link Activity} is launched in, if it is
     *         {@code DisplayAreaOrganizer.FEATURE_UNDEFINED}, then it'll remove the existing one.
     * @return {@code ResultTypeEnum}. {@code RESULT_SUCCESS} if the operation is successful,
     *         otherwise, {@code RESULT_XXX} depending on the type of the error.
     * @throws {@link IllegalArgumentException} if {@code displayId} or {@code featureId} is
     *         invalid. {@link ActivityNotFoundException} if {@code activity} is not found
     *         when it tries to remove.
     */
    @RequiresPermission(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)
    @ResultTypeEnum
    @AddedInOrBefore(majorVersion = 33)
    public int setPersistentActivity(
            @NonNull ComponentName activity, int displayId, int featureId) {
        try {
            return mService.setPersistentActivity(activity, displayId, featureId);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            throw e;
        } catch (ServiceSpecificException e) {
            return handleServiceSpecificFromCarService(e);
        } catch (RemoteException | RuntimeException e) {
            return handleExceptionFromCarService(e, RESULT_FAILURE);
        }
    }

    /** @hide */
    @Override
    @AddedInOrBefore(majorVersion = 33)
    protected void onCarDisconnected() {
        mTaskMonitorToken = null;
    }

    private int handleServiceSpecificFromCarService(ServiceSpecificException e)
            throws ActivityNotFoundException {
        if (e.errorCode == ERROR_CODE_ACTIVITY_NOT_FOUND) {
            throw new ActivityNotFoundException(e.getMessage());
        }
        // don't know what this is
        throw new IllegalStateException(e);
    }

    /**
     * Registers the caller as TaskMonitor, which can provide Task lifecycle events to CarService.
     * The caller should provide a binder token, which is used to check if the given TaskMonitor is
     * live and the reported events are from the legitimate TaskMonitor.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @AddedInOrBefore(majorVersion = 33)
    public boolean registerTaskMonitor() {
        IBinder token = new Binder();
        try {
            mService.registerTaskMonitor(token);
            mTaskMonitorToken = token;
            return true;
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return false;
    }

    /**
     * Reports that a Task is created.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @AddedInOrBefore(majorVersion = 33)
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {
        if (!hasValidToken()) return;
        try {
            mService.onTaskAppeared(mTaskMonitorToken, taskInfo);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Reports that a Task is vanished.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @AddedInOrBefore(majorVersion = 33)
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        if (!hasValidToken()) return;
        try {
            mService.onTaskVanished(mTaskMonitorToken, taskInfo);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Reports that some Task's states are changed.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @AddedInOrBefore(majorVersion = 33)
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (!hasValidToken()) return;
        try {
            mService.onTaskInfoChanged(mTaskMonitorToken, taskInfo);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Unregisters the caller from TaskMonitor.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @AddedInOrBefore(majorVersion = 33)
    public void unregisterTaskMonitor() {
        if (!hasValidToken()) return;
        try {
            mService.unregisterTaskMonitor(mTaskMonitorToken);
            mTaskMonitorToken = null;
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns all the visible tasks. The order is not guaranteed.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_1,
             minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @NonNull
    public List<ActivityManager.RunningTaskInfo> getVisibleTasks() {
        try {
            return mService.getVisibleTasks();
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return Collections.emptyList();
    }

    private boolean hasValidToken() {
        boolean valid = mTaskMonitorToken != null;
        if (!valid) {
            Log.w(TAG, "Has invalid token, skip the operation: "
                    + new Throwable().getStackTrace()[1].getMethodName());
        }
        return valid;
    }
}
