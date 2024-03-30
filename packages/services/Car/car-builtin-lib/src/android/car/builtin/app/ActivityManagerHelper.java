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

package android.car.builtin.app;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.car.builtin.annotation.AddedIn;
import android.car.builtin.annotation.PlatformVersion;
import android.car.builtin.util.Slogf;
import android.os.Bundle;
import android.os.RemoteException;

import java.util.concurrent.Callable;

/**
 * Provide access to {@code android.app.IActivityManager} calls.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class ActivityManagerHelper {

    /** Invalid task ID. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID;

    private static final String TAG = "CAR.AM";  // CarLog.TAG_AM

    // Lazy initialization holder class idiom for static fields; See go/ej3e-83 for the detail.
    private static class ActivityManagerHolder {
        static final IActivityManager sAm = ActivityManager.getService();
    }

    private static IActivityManager getActivityManager() {
        return ActivityManagerHolder.sAm;
    }

    private ActivityManagerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * See {@code android.app.IActivityManager.startUserInBackground}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean startUserInBackground(@UserIdInt int userId) {
        return runRemotely(() -> getActivityManager().startUserInBackground(userId),
                "error while startUserInBackground %d", userId);
    }

    /**
     * See {@code android.app.IActivityManager.startUserInForegroundWithListener}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean startUserInForeground(@UserIdInt int userId) {
        return runRemotely(
                () -> getActivityManager().startUserInForegroundWithListener(
                        userId, /* listener= */ null),
                "error while startUserInForeground %d", userId);
    }

    /**
     * See {@code android.app.IActivityManager.stopUserWithDelayedLocking}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int stopUserWithDelayedLocking(@UserIdInt int userId, boolean force) {
        return runRemotely(
                () -> getActivityManager().stopUserWithDelayedLocking(
                        userId, force, /* callback= */ null),
                "error while stopUserWithDelayedLocking %d", userId);
    }

    /**
     * Check {@code android.app.IActivityManager.unlockUser}.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean unlockUser(@UserIdInt int userId) {
        return runRemotely(() -> getActivityManager().unlockUser(userId,
                /* token= */ null, /* secret= */ null, /* listener= */ null),
                "error while unlocking user %d", userId);
    }

    /**
     * Stops all task for the user.
     *
     * @throws IllegalStateException if ActivityManager binder throws RemoteException
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void stopAllTasksForUser(@UserIdInt int userId) {
        try {
            IActivityManager am = getActivityManager();
            for (RootTaskInfo info : am.getAllRootTaskInfos()) {
                for (int i = 0; i < info.childTaskIds.length; i++) {
                    if (info.childTaskUserIds[i] == userId) {
                        int taskId = info.childTaskIds[i];
                        if (!am.removeTask(taskId)) {
                            Slogf.w(TAG, "could not remove task " + taskId);
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            throw logAndReThrow(e, "could not get stack info for user %d", userId);
        }
    }

    /**
     * Creates an ActivityOptions from the Bundle generated from ActivityOptions.
     */
    @NonNull
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static ActivityOptions createActivityOptions(@NonNull Bundle bOptions) {
        return new ActivityOptions(bOptions);
    }

    private static <T> T runRemotely(Callable<T> callable, String format, Object...args) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw logAndReThrow(e, format, args);
        }
    }

    @SuppressWarnings("AnnotateFormatMethod")
    private static RuntimeException logAndReThrow(Exception e, String format, Object...args) {
        String msg = String.format(format, args);
        Slogf.e(TAG, msg, e);
        return new IllegalStateException(msg, e);
    }

    /**
     * Makes the root task of the given taskId focused.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void setFocusedRootTask(int taskId) {
        try {
            getActivityManager().setFocusedRootTask(taskId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to setFocusedRootTask", e);
        }
    }

    /**
     * Removes the given task.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean removeTask(int taskId) {
        try {
            return getActivityManager().removeTask(taskId);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to removeTask", e);
        }
        return false;
    }

    /**
     * Callback to monitor Processes in the system
     */
    public abstract static class ProcessObserverCallback {
        /** Called when the foreground Activities are changed. */
        @AddedIn(PlatformVersion.TIRAMISU_0)
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        }
        /** Called when the Process is died. */
        @AddedIn(PlatformVersion.TIRAMISU_0)
        public void onProcessDied(int pid, int uid) {}

        @AddedIn(PlatformVersion.TIRAMISU_0)
        final IProcessObserver.Stub mIProcessObserver = new IProcessObserver.Stub() {
            @Override
            public void onForegroundActivitiesChanged(
                    int pid, int uid, boolean foregroundActivities) throws RemoteException {
                ProcessObserverCallback.this.onForegroundActivitiesChanged(
                        pid, uid, foregroundActivities);
            }

            @Override
            public void onForegroundServicesChanged(int pid, int uid, int fgServiceTypes)
                    throws RemoteException {
                // Not used
            }

            @Override
            public void onProcessDied(int pid, int uid) throws RemoteException {
                ProcessObserverCallback.this.onProcessDied(pid, uid);
            }
        };
    }

    /**
     * Registers a callback to be invoked when the process states are changed.
     * @param callback a callback to register
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void registerProcessObserverCallback(ProcessObserverCallback callback) {
        try {
            getActivityManager().registerProcessObserver(callback.mIProcessObserver);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to register ProcessObserver", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Unregisters the given callback.
     * @param callback a callback to unregister
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void unregisterProcessObserverCallback(ProcessObserverCallback callback) {
        try {
            getActivityManager().unregisterProcessObserver(callback.mIProcessObserver);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Failed to unregister listener", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Same as {@link ActivityManager#checkComponentPermission(String, int, int, boolean).
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int checkComponentPermission(@NonNull String permission, int uid, int owningUid,
            boolean exported) {
        return ActivityManager.checkComponentPermission(permission, uid, owningUid, exported);
    }
}
