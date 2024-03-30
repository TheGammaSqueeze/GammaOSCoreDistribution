/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import static com.android.car.carlauncher.CarLauncher.TAG;
import static com.android.wm.shell.ShellTaskOrganizer.TASK_LISTENER_TYPE_FULLSCREEN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.Application.ActivityLifecycleCallbacks;
import android.app.TaskInfo;
import android.app.TaskStackListener;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.user.CarUserManager;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.TaskAppearedInfo;
import android.window.WindowContainerTransaction;

import com.android.car.carlauncher.taskstack.TaskStackChangeListeners;
import com.android.internal.annotations.VisibleForTesting;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.common.annotations.ShellMainThread;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.startingsurface.StartingWindowController;
import com.android.wm.shell.startingsurface.phone.PhoneStartingWindowTypeAlgorithm;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;


/**
 * A manager for creating {@link ControlledCarTaskView}, {@link LaunchRootCarTaskView} &
 * {@link SemiControlledCarTaskView}.
 */
public final class TaskViewManager {
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String SCHEME_PACKAGE = "package";

    private final AtomicReference<CarActivityManager> mCarActivityManagerRef =
            new AtomicReference<>();
    @ShellMainThread
    private final HandlerExecutor mShellExecutor;
    private final SyncTransactionQueue mSyncQueue;
    private final ShellTaskOrganizer mTaskOrganizer;
    private TaskViewInputInterceptor mTaskViewInputInterceptor;
    private final int mHostTaskId;
    private final LinkedHashMap<Integer, ActivityManager.RunningTaskInfo> mLaunchRootStack =
            new LinkedHashMap<>();

    // All TaskView are bound to the Host Activity if it exists.
    @ShellMainThread
    private final List<ControlledCarTaskView> mControlledTaskViews = new ArrayList<>();
    @ShellMainThread
    private final List<SemiControlledCarTaskView> mSemiControlledTaskViews = new ArrayList<>();
    @ShellMainThread
    private LaunchRootCarTaskView mLaunchRootCarTaskView = null;

    private CarUserManager mCarUserManager;
    private Activity mContext;

    private final ShellTaskOrganizer.TaskListener mRootTaskListener =
            new ShellTaskOrganizer.TaskListener() {
                @Override
                public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                        SurfaceControl leash) {
                    // Called for a task appearing the launch root. Route it to the appropriate
                    // semi-controlled taskview;
                    for (SemiControlledCarTaskView taskView : mSemiControlledTaskViews) {
                        if (taskView.getCallbacks().shouldStartInTaskView(taskInfo)) {
                            if (taskView.isInitialized()) {
                                taskView.onTaskAppeared(taskInfo, leash);
                            }
                            return;
                        }
                    }

                    // TODO(b/228077499): Fix for the case when a task is started in the
                    // launch-root-task right after the initialization of launch-root-task, it
                    // remains blank.
                    mSyncQueue.runInSync(t -> t.show(leash));

                    CarActivityManager carAm = mCarActivityManagerRef.get();
                    if (carAm != null) {
                        carAm.onTaskAppeared(taskInfo);
                        mLaunchRootStack.put(taskInfo.taskId, taskInfo);
                    } else {
                        Log.w(TAG, "CarActivityManager is null, skip onTaskAppeared: TaskInfo"
                                + " = " + taskInfo);
                    }
                }

                @Override
                public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                    for (SemiControlledCarTaskView taskView : mSemiControlledTaskViews) {
                        if (taskView.getCallbacks().shouldStartInTaskView(taskInfo)) {
                            if (taskView.isInitialized()) {
                                // onLocationChanged() is crucial. If this is not called, the
                                // further activities opened by the current activity do not open in
                                // the correct size.
                                // TODO(b/234879199): Explore more for a better solution.
                                taskView.onLocationChanged();
                                taskView.onTaskInfoChanged(taskInfo);
                            }
                            // Semi-controlled apps are assumed to be Distraction optimised and
                            // hence not reported to CarActivityManager.
                            return;
                        }
                    }

                    // Uncontrolled apps by default launch in the launch root so nothing needs to
                    // be done here for them.
                    CarActivityManager carAm = mCarActivityManagerRef.get();
                    if (carAm != null) {
                        carAm.onTaskInfoChanged(taskInfo);
                        if (taskInfo.isVisible && mLaunchRootStack.containsKey(taskInfo.taskId)) {
                            // Remove the task and insert again so that it jumps to the end of
                            // the queue.
                            mLaunchRootStack.remove(taskInfo.taskId);
                            mLaunchRootStack.put(taskInfo.taskId, taskInfo);
                        }
                    } else {
                        Log.w(TAG, "CarActivityManager is null, skip onTaskInfoChanged: TaskInfo"
                                + " = " + taskInfo);
                    }
                }

                @Override
                public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                    for (SemiControlledCarTaskView taskView : mSemiControlledTaskViews) {
                        if (taskView.getCallbacks().shouldStartInTaskView(taskInfo)) {
                            if (taskView.isInitialized()) {
                                taskView.onTaskVanished(taskInfo);
                            }
                            return;
                        }
                    }

                    CarActivityManager carAm = mCarActivityManagerRef.get();
                    if (carAm != null) {
                        carAm.onTaskVanished(taskInfo);
                        if (mLaunchRootStack.containsKey(taskInfo.taskId)) {
                            mLaunchRootStack.remove(taskInfo.taskId);
                        }
                    } else {
                        Log.w(TAG, "CarActivityManager is null, skip onTaskAppeared: TaskInfo"
                                + " = " + taskInfo);
                    }
                }

                @Override
                public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) {
                    for (SemiControlledCarTaskView taskView : mSemiControlledTaskViews) {
                        if (taskView.getCallbacks().shouldStartInTaskView(taskInfo)) {
                            // Do nothing
                            Log.d(TAG, "onBackPressedOnTaskRoot received for a "
                                    + "SemiControlledCarTaskView, do nothing.");
                            return;
                        }
                    }
                    if (mLaunchRootStack.size() == 1) {
                        Log.d(TAG, "Cannot remove last task from launch root.");
                        return;
                    }
                    if (mLaunchRootStack.size() == 0) {
                        Log.d(TAG, "Launch root is empty, do nothing.");
                        return;
                    }

                    ActivityManager.RunningTaskInfo topTask = getTopTaskInLaunchRootTask();
                    WindowContainerTransaction wct = new WindowContainerTransaction();
                    // removeTask() will trigger onTaskVanished which will remove the task locally
                    // from mLaunchRootStack
                    wct.removeTask(topTask.token);
                    mSyncQueue.queue(wct);
                }
            };

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskFocusChanged(int taskId, boolean focused) {
            boolean hostFocused = taskId == mHostTaskId && focused;
            if (DBG) {
                Log.d(TAG, "onTaskFocusChanged: taskId=" + taskId
                        + ", hostFocused=" + hostFocused);
            }
            if (!hostFocused) {
                return;
            }

            for (int i = mControlledTaskViews.size() - 1; i >= 0; --i) {
                ControlledCarTaskView taskView = mControlledTaskViews.get(i);
                if (taskView.getTaskId() == INVALID_TASK_ID) {
                    // If the task in TaskView is crashed when host is in background,
                    // We'd like to restart it when host becomes foreground and focused.
                    taskView.startActivity();
                }
            }
        }

        @Override
        public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
            if (DBG) {
                Log.d(TAG, "onActivityRestartAttempt: taskId=" + task.taskId
                        + ", homeTaskVisible=" + homeTaskVisible + ", wasVisible=" + wasVisible);
            }
            if (mHostTaskId != task.taskId) {
                return;
            }
            WindowContainerTransaction wct = new WindowContainerTransaction();
            for (int i = mControlledTaskViews.size() - 1; i >= 0; --i) {
                // showEmbeddedTasks() will restart the crashed tasks too.
                mControlledTaskViews.get(i).showEmbeddedTask(wct);
            }
            if (mLaunchRootCarTaskView != null) {
                mLaunchRootCarTaskView.showEmbeddedTask(wct);
            }
            for (int i = mSemiControlledTaskViews.size() - 1; i >= 0; --i) {
                mSemiControlledTaskViews.get(i).showEmbeddedTask(wct);
            }
            mSyncQueue.queue(wct);
        }
    };

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener = event -> {
        if (DBG) {
            Log.d(TAG, "UserLifecycleListener.onEvent: For User "
                    + mContext.getUserId()
                    + ", received an event " + event);
        }

        // When user-unlocked, if task isn't launched yet, then try to start it.
        if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED
                && mContext.getUserId() == event.getUserId()) {
            for (int i = mControlledTaskViews.size() - 1; i >= 0; --i) {
                ControlledCarTaskView taskView = mControlledTaskViews.get(i);
                if (taskView.getTaskId() == INVALID_TASK_ID) {
                    taskView.startActivity();
                }
            }
        }

        // When user-switching, onDestroy in the previous user's Host app isn't called.
        // So try to release the resource explicitly.
        if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_SWITCHING
                && mContext.getUserId() == event.getPreviousUserId()) {
            release();
        }
    };

    private final BroadcastReceiver mPackageBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) Log.d(TAG, "onReceive: intent=" + intent);

            if (!isHostVisible()) {
                return;
            }

            String packageName = intent.getData().getSchemeSpecificPart();
            for (int i = mControlledTaskViews.size() - 1; i >= 0; --i) {
                ControlledCarTaskView taskView = mControlledTaskViews.get(i);
                if (taskView.getTaskId() == INVALID_TASK_ID
                        && taskView.getDependingPackageNames().contains(packageName)) {
                    taskView.startActivity();
                }
            }
        }
    };

    public TaskViewManager(Activity context, HandlerExecutor handlerExecutor) {
        this(context, handlerExecutor, new ShellTaskOrganizer(handlerExecutor),
                new TransactionPool(), new ShellCommandHandler(), new ShellInit(handlerExecutor));
    }

    private TaskViewManager(Activity context, HandlerExecutor handlerExecutor,
                    ShellTaskOrganizer taskOrganizer, TransactionPool transactionPool,
                    ShellCommandHandler shellCommandHandler, ShellInit shellinit) {
        this(context, handlerExecutor, taskOrganizer,
                new SyncTransactionQueue(transactionPool, handlerExecutor),
                shellinit,
                new StartingWindowController(context, shellinit,
                        new ShellController(shellinit, shellCommandHandler, handlerExecutor),
                        taskOrganizer,
                        handlerExecutor,
                        new PhoneStartingWindowTypeAlgorithm(),
                        new IconProvider(context),
                        transactionPool));
    }

    @VisibleForTesting
    TaskViewManager(Activity context, HandlerExecutor handlerExecutor,
                    ShellTaskOrganizer shellTaskOrganizer, SyncTransactionQueue syncQueue,
                    ShellInit shellInit, StartingWindowController startingWindowController) {
        if (DBG) Slog.d(TAG, "TaskViewManager(): " + context);
        mContext = context;
        mShellExecutor = handlerExecutor;
        mTaskOrganizer = shellTaskOrganizer;
        mHostTaskId = mContext.getTaskId();
        mSyncQueue = syncQueue;
        mTaskViewInputInterceptor = new TaskViewInputInterceptor(context, this);

        initCar();
        shellInit.init();
        initTaskOrganizer(mCarActivityManagerRef);
        mContext.registerActivityLifecycleCallbacks(mActivityLifecycleCallbacks);
    }

    private void initCar() {
        Car.createCar(/* context= */ mContext, /* handler= */ null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) {
                        Log.w(TAG, "CarService looks crashed");
                        mCarActivityManagerRef.set(null);
                        return;
                    }
                    setCarUserManager((CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE));
                    UserLifecycleEventFilter filter = new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
                    mCarUserManager.addListener(mContext.getMainExecutor(), filter,
                            mUserLifecycleListener);
                    CarActivityManager carAM = (CarActivityManager) car.getCarManager(
                            Car.CAR_ACTIVITY_SERVICE);
                    mCarActivityManagerRef.set(carAM);

                    carAM.registerTaskMonitor();
                });

        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);

        IntentFilter packageIntentFilter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
        packageIntentFilter.addDataScheme(SCHEME_PACKAGE);
        mContext.registerReceiver(mPackageBroadcastReceiver, packageIntentFilter);
    }

    // TODO(b/239958124A): Remove this method when unit tests for TaskViewManager have been added.
    /**
     * This method only exists for the container activity to set mock car user manager in tests.
     */
    void setCarUserManager(CarUserManager carUserManager) {
        mCarUserManager = carUserManager;
    }

    private void initTaskOrganizer(AtomicReference<CarActivityManager> carActivityManagerRef) {
        FullscreenTaskListener fullscreenTaskListener = new CarFullscreenTaskMonitorListener(
                carActivityManagerRef, mSyncQueue);
        mTaskOrganizer.addListenerForType(fullscreenTaskListener, TASK_LISTENER_TYPE_FULLSCREEN);
        List<TaskAppearedInfo> taskAppearedInfos = mTaskOrganizer.registerOrganizer();
        cleanUpExistingTaskViewTasks(taskAppearedInfos);
    }

    /**
     * Creates a {@link ControlledCarTaskView}.
     *
     * @param callbackExecutor the executor which the {@link ControlledCarTaskViewCallbacks} will
     *                         be executed on.
     * @param controlledCarTaskViewConfig the configuration for the underlying
     * {@link ControlledCarTaskView}.
     * @param taskViewCallbacks the callbacks for the underlying TaskView.
     */
    public void createControlledCarTaskView(
            Executor callbackExecutor,
            ControlledCarTaskViewConfig controlledCarTaskViewConfig,
            ControlledCarTaskViewCallbacks taskViewCallbacks) {
        mShellExecutor.execute(() -> {
            ControlledCarTaskView taskView = new ControlledCarTaskView(mContext, mTaskOrganizer,
                    mSyncQueue, callbackExecutor, controlledCarTaskViewConfig, taskViewCallbacks,
                    mContext.getSystemService(UserManager.class), this);
            mControlledTaskViews.add(taskView);

            if (controlledCarTaskViewConfig.mCaptureGestures
                    || controlledCarTaskViewConfig.mCaptureLongPress) {
                mTaskViewInputInterceptor.init();
            }
        });

    }

    /**
     * Creates a {@link LaunchRootCarTaskView}.
     *
     * @param callbackExecutor the executor which the {@link LaunchRootCarTaskViewCallbacks} will be
     *                         executed on.
     * @param taskViewCallbacks the callbacks for the underlying TaskView.
     */
    public void createLaunchRootTaskView(Executor callbackExecutor,
            LaunchRootCarTaskViewCallbacks taskViewCallbacks) {
        mShellExecutor.execute(() -> {
            if (mLaunchRootCarTaskView != null) {
                throw new IllegalStateException("Cannot create more than one launch root task");
            }
            mLaunchRootCarTaskView = new LaunchRootCarTaskView(mContext, mTaskOrganizer,
                    mSyncQueue, callbackExecutor, taskViewCallbacks, mRootTaskListener);
        });
    }

    /**
     * Creates a {@link SemiControlledCarTaskView}.
     *
     * @param callbackExecutor the executor which the {@link SemiControlledCarTaskViewCallbacks}
     *                         will be executed on.
     * @param taskViewCallbacks the callbacks for the underlying TaskView.
     */
    public void createSemiControlledTaskView(Executor callbackExecutor,
            SemiControlledCarTaskViewCallbacks taskViewCallbacks) {
        mShellExecutor.execute(() -> {
            if (mLaunchRootCarTaskView == null) {
                throw new IllegalStateException("Cannot create a semi controlled taskview without a"
                        + " launch root taskview");
            }
            SemiControlledCarTaskView taskView = new SemiControlledCarTaskView(mContext,
                    mTaskOrganizer, mSyncQueue, callbackExecutor, taskViewCallbacks);
            mSemiControlledTaskViews.add(taskView);
        });
    }

    /**
     * Releases {@link TaskViewManager} and unregisters the underlying {@link ShellTaskOrganizer}.
     * It also removes all TaskViews which are created by this {@link TaskViewManager}.
     */
    private void release() {
        mShellExecutor.execute(() -> {
            if (DBG) Slog.d(TAG, "TaskViewManager.release");

            if (mCarUserManager != null) {
                mCarUserManager.removeListener(mUserLifecycleListener);
            }
            TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
            mContext.unregisterReceiver(mPackageBroadcastReceiver);

            CarActivityManager carAM = mCarActivityManagerRef.get();
            if (carAM != null) {
                carAM.unregisterTaskMonitor();
                mCarActivityManagerRef.set(null);
            }

            for (int i = mControlledTaskViews.size() - 1; i >= 0; --i) {
                mControlledTaskViews.get(i).release();
            }
            mControlledTaskViews.clear();

            for (int i = mSemiControlledTaskViews.size() - 1; i >= 0; --i) {
                mSemiControlledTaskViews.get(i).release();
            }
            mSemiControlledTaskViews.clear();

            if (mLaunchRootCarTaskView != null) {
                mLaunchRootCarTaskView.release();
                mLaunchRootCarTaskView = null;
            }

            mContext.unregisterActivityLifecycleCallbacks(mActivityLifecycleCallbacks);
            mTaskOrganizer.unregisterOrganizer();
            mTaskViewInputInterceptor.release();
        });
    }

    boolean isHostVisible() {
        // This code relies on Activity#isVisibleForAutofill() instead of maintaining a custom
        // activity state.
        return mContext.isVisibleForAutofill();
    }

    private final ActivityLifecycleCallbacks mActivityLifecycleCallbacks =
            new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull Activity activity,
                        @Nullable Bundle savedInstanceState) {}

                @Override
                public void onActivityStarted(@NonNull Activity activity) {}

                @Override
                public void onActivityResumed(@NonNull Activity activity) {}

                @Override
                public void onActivityPaused(@NonNull Activity activity) {}

                @Override
                public void onActivityStopped(@NonNull Activity activity) {}

                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity,
                        @NonNull Bundle outState) {}

                @Override
                public void onActivityDestroyed(@NonNull Activity activity) {
                    release();
                }
            };

    private static void cleanUpExistingTaskViewTasks(List<TaskAppearedInfo> taskAppearedInfos) {
        ActivityTaskManager atm = ActivityTaskManager.getInstance();
        for (TaskAppearedInfo taskAppearedInfo : taskAppearedInfos) {
            TaskInfo taskInfo = taskAppearedInfo.getTaskInfo();
            // Only TaskView tasks have WINDOWING_MODE_MULTI_WINDOW.
            if (taskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
                if (DBG) Slog.d(TAG, "Found the dangling task, removing: " + taskInfo.taskId);
                atm.removeTask(taskInfo.taskId);
            }
        }
    }

    @VisibleForTesting
    List<ControlledCarTaskView> getControlledTaskViews() {
        return mControlledTaskViews;
    }

    @VisibleForTesting
    LaunchRootCarTaskView getLaunchRootCarTaskView() {
        return mLaunchRootCarTaskView;
    }

    @VisibleForTesting
    List<SemiControlledCarTaskView> getSemiControlledTaskViews() {
        return mSemiControlledTaskViews;
    }

    @VisibleForTesting
    BroadcastReceiver getPackageBroadcastReceiver() {
        return mPackageBroadcastReceiver;
    }

    @VisibleForTesting
    /** Only meant for testing, should not be used by real code. */
    void setTaskViewInputInterceptor(TaskViewInputInterceptor taskViewInputInterceptor) {
        mTaskViewInputInterceptor = taskViewInputInterceptor;
    }

    //TODO(b/266154272): Move this logic inside LaunchRootCarTaskView
    public int getRootTaskCount() {
        return mLaunchRootStack.size();
    }

    /**
     * Returns the {@link android.app.ActivityManager.RunningTaskInfo} of the top task inside the
     * launch root car task view.
     */
    @VisibleForTesting
    public ActivityManager.RunningTaskInfo getTopTaskInLaunchRootTask() {
        if (mLaunchRootStack.size() == 0) {
            return null;
        }
        ActivityManager.RunningTaskInfo[] infos = mLaunchRootStack.values().toArray(
                new ActivityManager.RunningTaskInfo[mLaunchRootStack.size()]);
        return infos[infos.length - 1];
    }
}
