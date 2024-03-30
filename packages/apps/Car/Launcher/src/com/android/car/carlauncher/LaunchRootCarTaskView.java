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

package com.android.car.carlauncher;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.car.carlauncher.TaskViewManager.DBG;

import android.app.Activity;
import android.app.ActivityManager;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.util.concurrent.Executor;

/**
 * A {@link CarTaskView} that can act as a default app container. A default app container is the
 * container where all apps open by default.
 */
final class LaunchRootCarTaskView extends CarTaskView {
    private static final String TAG = LaunchRootCarTaskView.class.getSimpleName();

    private final Executor mCallbackExecutor;
    private final LaunchRootCarTaskViewCallbacks mCallbacks;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final SyncTransactionQueue mSyncQueue;
    private final ShellTaskOrganizer.TaskListener mRootTaskListener;

    private ActivityManager.RunningTaskInfo mLaunchRootTask;

    private final ShellTaskOrganizer.TaskListener mRootTaskListenerWrapper =
            new ShellTaskOrganizer.TaskListener() {
                @Override
                public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                        SurfaceControl leash) {
                    // The first call to onTaskAppeared() is always for the root-task.
                    if (mLaunchRootTask == null && !taskInfo.hasParentTask()) {
                        setRootTaskAsLaunchRoot(taskInfo);
                        LaunchRootCarTaskView.this.onTaskAppeared(taskInfo, leash);
                        mCallbackExecutor.execute(() -> mCallbacks.onTaskViewReady());
                        return;
                    }

                    if (DBG) {
                        Log.d(TAG, "onTaskAppeared " + taskInfo.taskId + " - "
                                + taskInfo.baseActivity);
                    }


                    mRootTaskListener.onTaskAppeared(taskInfo, leash);
                }

                @Override
                public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                    if (mLaunchRootTask != null
                            && mLaunchRootTask.taskId == taskInfo.taskId) {
                        LaunchRootCarTaskView.this.onTaskInfoChanged(taskInfo);
                        if (DBG) {
                            Log.d(TAG, "got onTaskInfoChanged for the launch root task. Not "
                                    + "forwarding this to root task listener");
                        }
                        return;
                    }
                    if (DBG) {
                        Log.d(TAG, "onTaskInfoChanged " + taskInfo.taskId + " - "
                                + taskInfo.baseActivity);
                    }

                    mRootTaskListener.onTaskInfoChanged(taskInfo);
                }

                @Override
                public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                    if (DBG) {
                        Log.d(TAG, "onTaskVanished " + taskInfo.taskId + " - "
                                + taskInfo.baseActivity);
                    }
                    if (mLaunchRootTask != null
                            && mLaunchRootTask.taskId == taskInfo.taskId) {
                        LaunchRootCarTaskView.this.onTaskVanished(taskInfo);
                        if (DBG) {
                            Log.d(TAG, "got onTaskVanished for the launch root task. Not "
                                    + "forwarding this to root task listener");
                        }
                        return;
                    }

                    mRootTaskListener.onTaskVanished(taskInfo);
                }

                @Override
                public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) {
                    mRootTaskListener.onBackPressedOnTaskRoot(taskInfo);
                }
            };

    public LaunchRootCarTaskView(Activity context,
            ShellTaskOrganizer organizer,
            SyncTransactionQueue syncQueue,
            Executor callbackExecutor,
            LaunchRootCarTaskViewCallbacks callbacks,
            ShellTaskOrganizer.TaskListener rootTaskListener) {
        super(context, organizer, syncQueue);
        mCallbacks = callbacks;
        mCallbackExecutor = callbackExecutor;
        mShellTaskOrganizer = organizer;
        mSyncQueue = syncQueue;
        mRootTaskListener = rootTaskListener;

        mCallbackExecutor.execute(() -> mCallbacks.onTaskViewCreated(this));
    }

    @Override
    protected void onCarTaskViewInitialized() {
        super.onCarTaskViewInitialized();
        mShellTaskOrganizer.getExecutor().execute(() -> {
            // Should run on shell's executor
            mShellTaskOrganizer.createRootTask(DEFAULT_DISPLAY,
                    WINDOWING_MODE_MULTI_WINDOW,
                    mRootTaskListenerWrapper);
        });
    }

    @Override
    protected void notifyReleased() {
        super.notifyReleased();
        clearLaunchRootTask();
    }

    private void clearLaunchRootTask() {
        if (mLaunchRootTask == null) {
            Log.w(TAG, "Unable to clear launch root task because it is not created.");
            return;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setLaunchRoot(mLaunchRootTask.token, null, null);
        mSyncQueue.queue(wct);
        // Should run on shell's executor
        mShellTaskOrganizer.deleteRootTask(mLaunchRootTask.token);
        mLaunchRootTask = null;
    }

    private void setRootTaskAsLaunchRoot(ActivityManager.RunningTaskInfo taskInfo) {
        mLaunchRootTask = taskInfo;
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setLaunchRoot(taskInfo.token,
                        new int[]{WINDOWING_MODE_FULLSCREEN, WINDOWING_MODE_UNDEFINED},
                        new int[]{ACTIVITY_TYPE_STANDARD})
                .reorder(taskInfo.token, true);
        mSyncQueue.queue(wct);
    }
}
