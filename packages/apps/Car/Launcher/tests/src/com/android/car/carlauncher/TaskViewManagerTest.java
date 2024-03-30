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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT;

import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.CarUserManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.window.TaskAppearedInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.carlauncher.taskstack.TaskStackChangeListeners;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.TaskView;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.startingsurface.StartingWindowController;
import com.android.wm.shell.sysui.ShellInit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class TaskViewManagerTest extends AbstractExtendedMockitoTestCase {
    @Rule
    public ActivityScenarioRule mActivityRule = new ActivityScenarioRule<>(TestActivity.class);

    @Mock
    private ShellTaskOrganizer mOrganizer;
    @Mock
    private SyncTransactionQueue mSyncQueue;
    @Mock
    private HandlerExecutor mShellExecutor;
    @Mock
    private CarActivityManager mCarActivityManager;
    @Mock
    private Car mCar;
    @Mock
    private TaskStackChangeListeners mTaskStackChangeListeners;
    @Mock
    private CarUserManager mCarUserManager;
    @Mock
    private WindowContainerToken mToken;

    @Mock
    private StartingWindowController mStartingWindowController;
    @Mock
    private TaskViewInputInterceptor mTaskViewInputInterceptor;

    @Captor
    private ArgumentCaptor<TaskStackListener> mTaskStackListenerArgumentCaptor;
    @Captor
    private ArgumentCaptor<CarUserManager.UserLifecycleListener>
            mUserLifecycleListenerArgumentCaptor;

    private TestActivity mActivity;
    private Car.CarServiceLifecycleListener mCarServiceLifecycleListener;
    private ActivityTaskManager mSpyActivityTaskManager;
    private CountDownLatch mIdleHandlerLatch = new CountDownLatch(1);
    private SurfaceControl mLeash;

    @Override
    protected void onSessionBuilder(@NonNull CustomMockitoSessionBuilder builder) {
        builder.spyStatic(ActivityTaskManager.class);
        builder.spyStatic(Car.class);
        builder.spyStatic(TaskStackChangeListeners.class);
    }

    @Before
    public void setUp() {
        ExtendedMockito.doAnswer(invocation -> {
            mCarServiceLifecycleListener = invocation.getArgument(3);
            return mCar;
        }).when(() -> Car.createCar(any(), any(), anyLong(), any()));
        when(mCar.getCarManager(eq(Car.CAR_ACTIVITY_SERVICE))).thenReturn(mCarActivityManager);
        when(mCar.getCarManager(eq(Car.CAR_USER_SERVICE))).thenReturn(mCarUserManager);

        ExtendedMockito.doReturn(mTaskStackChangeListeners).when(() ->
                TaskStackChangeListeners.getInstance());
        doNothing().when(mTaskStackChangeListeners).registerTaskStackListener(
                mTaskStackListenerArgumentCaptor.capture());

        doNothing().when(mCarUserManager).addListener(any(), any(),
                mUserLifecycleListenerArgumentCaptor.capture());

        mLeash = new SurfaceControl.Builder(null)
                .setName("test")
                .build();

        doAnswer((InvocationOnMock invocationOnMock) -> {
            SyncTransactionQueue.TransactionRunnable r =
                    invocationOnMock.getArgument(0);
            r.runWithTransaction(new SurfaceControl.Transaction());
            return null;
        }).when(mSyncQueue).runInSync(any());

        doAnswer((InvocationOnMock invocationOnMock) -> {
            Runnable r = invocationOnMock.getArgument(0);
            r.run();
            return null;
        }).when(mShellExecutor).execute(any());
        doReturn(mShellExecutor).when(mOrganizer).getExecutor();

        mSpyActivityTaskManager = spy(ActivityTaskManager.getInstance());
        ExtendedMockito.doReturn(mSpyActivityTaskManager).when(() ->
                ActivityTaskManager.getInstance());

        ActivityScenario<TestActivity> scenario = mActivityRule.getScenario();
        scenario.onActivity(activity -> mActivity = activity);
    }

    @After
    public void tearDown() throws InterruptedException {
        mActivity.finishCompletely();
    }

    private TaskAppearedInfo createMultiWindowTask(int taskId) {
        ActivityManager.RunningTaskInfo taskInfo =
                new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_MULTI_WINDOW);
        taskInfo.parentTaskId = INVALID_TASK_ID;
        taskInfo.token = mock(WindowContainerToken.class);
        taskInfo.isVisible = true;
        return new TaskAppearedInfo(taskInfo, new SurfaceControl());
    }

    private TaskAppearedInfo createMultiWindowTask(int taskId, IBinder token) {
        TaskAppearedInfo taskInfo = createMultiWindowTask(taskId);
        when(taskInfo.getTaskInfo().token.asBinder()).thenReturn(token);
        return taskInfo;
    }

    @Test
    public void init_cleansUpExistingMultiWindowTasks() {
        TaskAppearedInfo existingTask1 = createMultiWindowTask(/* taskId= */ 1);
        TaskAppearedInfo existingTask2 = createMultiWindowTask(/* taskId= */ 2);
        doReturn(ImmutableList.of(existingTask1, existingTask2))
                .when(mOrganizer).registerOrganizer();
        ExtendedMockito.doReturn(false).when(mSpyActivityTaskManager).removeTask(anyInt());

        createTaskViewManager();

        verify(mSpyActivityTaskManager).removeTask(eq(1));
        verify(mSpyActivityTaskManager).removeTask(eq(2));
    }

    @Test
    public void testCreateControlledTaskView() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();

        Intent activityIntent = new Intent();
        Set<String> packagesThatCanRestart = ImmutableSet.of("com.random.package");
        ControlledCarTaskViewCallbacks controlledCarTaskViewCallbacks = mock(
                ControlledCarTaskViewCallbacks.class);
        when(controlledCarTaskViewCallbacks.getDependingPackageNames())
                .thenReturn(packagesThatCanRestart);

        taskViewManager.createControlledCarTaskView(
                mActivity.getMainExecutor(),
                ControlledCarTaskViewConfig.builder()
                        .setActivityIntent(activityIntent)
                        .setAutoRestartOnCrash(false)
                        .build(),
                controlledCarTaskViewCallbacks
        );

        runOnMainAndWait(() -> {});
        verify(controlledCarTaskViewCallbacks).onTaskViewCreated(any());
        verifyZeroInteractions(mTaskViewInputInterceptor);
    }

    @Test
    public void testCreateControlledTaskView_initializesInterceptor_whenCapturingEvents() throws
            Exception {
        TaskViewManager taskViewManager = createTaskViewManager();

        Intent activityIntent = new Intent();
        Set<String> packagesThatCanRestart = ImmutableSet.of("com.random.package");
        ControlledCarTaskViewCallbacks controlledCarTaskViewCallbacks = mock(
                ControlledCarTaskViewCallbacks.class);
        when(controlledCarTaskViewCallbacks.getDependingPackageNames())
                .thenReturn(packagesThatCanRestart);

        taskViewManager.createControlledCarTaskView(
                mActivity.getMainExecutor(),
                ControlledCarTaskViewConfig.builder()
                        .setActivityIntent(activityIntent)
                        .setAutoRestartOnCrash(false)
                        .setCaptureLongPress(true)
                        .build(),
                controlledCarTaskViewCallbacks
        );

        runOnMainAndWait(() -> {});
        verify(mTaskViewInputInterceptor).init();
    }

    @Test
    public void testCreateControlledTaskView_callsOnReadyWhenVisible() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        Intent activityIntent = new Intent("ACTION_VIEW");
        Set<String> packagesThatCanRestart = ImmutableSet.of("com.random.package");
        ControlledCarTaskViewCallbacks controlledCarTaskViewCallbacks = mock(
                ControlledCarTaskViewCallbacks.class);
        when(controlledCarTaskViewCallbacks.getDependingPackageNames())
                .thenReturn(packagesThatCanRestart);
        taskViewManager.createControlledCarTaskView(
                mActivity.getMainExecutor(),
                ControlledCarTaskViewConfig.builder()
                        .setActivityIntent(activityIntent)
                        .setAutoRestartOnCrash(false)
                        .build(),
                controlledCarTaskViewCallbacks
        );
        ControlledCarTaskView taskView = spy(taskViewManager.getControlledTaskViews().get(0));
        doNothing().when(taskView).startActivity();

        taskView.surfaceCreated(mock(SurfaceHolder.class));

        runOnMainAndWait(() -> {});
        verify(controlledCarTaskViewCallbacks).onTaskViewCreated(any());
        verify(controlledCarTaskViewCallbacks).onTaskViewReady();
    }

    @Test
    public void testCreateLaunchRootTaskView() throws Exception {
        LaunchRootCarTaskViewCallbacks taskViewCallbacks =
                mock(LaunchRootCarTaskViewCallbacks.class);
        TaskViewManager taskViewManager = createTaskViewManager();

        taskViewManager.createLaunchRootTaskView(
                mActivity.getMainExecutor(),
                taskViewCallbacks
        );
        runOnMainAndWait(() -> {});
        TaskView taskView = taskViewManager.getLaunchRootCarTaskView();
        taskView.surfaceCreated(mock(SurfaceHolder.class));

        runOnMainAndWait(() -> {});
        verify(taskViewCallbacks).onTaskViewCreated(any());
        verify(mOrganizer).createRootTask(eq(DEFAULT_DISPLAY),
                eq(WINDOWING_MODE_MULTI_WINDOW),
                any(ShellTaskOrganizer.TaskListener.class));
    }

    @Test
    public void testCreateLaunchRootTaskView_callsOnReadyWhenVisible() throws Exception {
        TaskAppearedInfo fakeLaunchRootTaskInfo  = createMultiWindowTask(1);
        LaunchRootCarTaskViewCallbacks taskViewCallbacks =
                mock(LaunchRootCarTaskViewCallbacks.class);
        TaskViewManager taskViewManager = createTaskViewManager();
        doAnswer(invocation -> {
            ShellTaskOrganizer.TaskListener listener = invocation.getArgument(2);
            listener.onTaskAppeared(fakeLaunchRootTaskInfo.getTaskInfo(), mLeash);
            return null;
        }).when(mOrganizer).createRootTask(eq(DEFAULT_DISPLAY),
                eq(WINDOWING_MODE_MULTI_WINDOW),
                any(ShellTaskOrganizer.TaskListener.class));

        taskViewManager.createLaunchRootTaskView(
                mActivity.getMainExecutor(),
                taskViewCallbacks
        );
        runOnMainAndWait(() -> {});
        TaskView taskView = taskViewManager.getLaunchRootCarTaskView();
        taskView.surfaceCreated(mock(SurfaceHolder.class));

        runOnMainAndWait(() -> {});
        verify(taskViewCallbacks).onTaskViewCreated(any());
        verify(taskViewCallbacks).onTaskViewReady();
        ArgumentCaptor<WindowContainerTransaction> wctCaptor = ArgumentCaptor.forClass(
                WindowContainerTransaction.class);
        verify(mSyncQueue, atLeastOnce()).queue(wctCaptor.capture());
        List<WindowContainerTransaction> wcts = wctCaptor.getAllValues();
        assertWithMessage("There must be a WindowContainerTransaction to set the"
                + " root task as the launch root.")
                .that(wcts.stream()
                        .flatMap(wct -> wct.getHierarchyOps().stream())
                        .map(WindowContainerTransaction.HierarchyOp::getType)
                        .anyMatch(type -> type == HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT))
                .isTrue();
    }

    @Test
    public void testLaunchRootTaskView_onBackPressed_removesTopTask() throws Exception {
        IBinder task1Token = new Binder();
        IBinder task2Token = new Binder();
        IBinder task3Token = new Binder();
        ActivityManager.RunningTaskInfo task1 = createMultiWindowTask(1, task1Token).getTaskInfo();
        ActivityManager.RunningTaskInfo task2 = createMultiWindowTask(2, task2Token).getTaskInfo();
        ActivityManager.RunningTaskInfo task3 = createMultiWindowTask(3, task3Token).getTaskInfo();
        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);
        runOnMainAndWait(() -> {});
        // Set up a LaunchRootTaskView
        AtomicReference<ShellTaskOrganizer.TaskListener> rootTaskListener = new AtomicReference<>();
        ActivityManager.RunningTaskInfo launchRootTask =
                setUpLaunchRootTaskView(taskViewManager, rootTaskListener, /* rootTaskId = */ 100);
        runOnMainAndWait(() -> {});
        // Trigger a taskAppeared on the launch root task to mimic the task appearance.
        rootTaskListener.get().onTaskAppeared(task1, mLeash);
        rootTaskListener.get().onTaskAppeared(task2, mLeash);
        rootTaskListener.get().onTaskAppeared(task3, mLeash);
        rootTaskListener.get().onTaskInfoChanged(task1);
        // The resultant stack top to bottom is task1, task3, task2
        runOnMainAndWait(() -> {});

        // Act
        // Press back button 3 times, trigger corresponding task vanishing as well. In real
        // scenario, removeTask() will trigger onTaskVanished.
        rootTaskListener.get().onBackPressedOnTaskRoot(launchRootTask);
        rootTaskListener.get().onTaskVanished(task1);
        rootTaskListener.get().onBackPressedOnTaskRoot(launchRootTask);
        rootTaskListener.get().onTaskVanished(task3);
        rootTaskListener.get().onBackPressedOnTaskRoot(launchRootTask);

        // Assert
        ArgumentCaptor<WindowContainerTransaction> wctCaptor = ArgumentCaptor.forClass(
                WindowContainerTransaction.class);
        verify(mSyncQueue, atLeastOnce()).queue(wctCaptor.capture());
        List<WindowContainerTransaction> wcts = wctCaptor.getAllValues();
        List<WindowContainerTransaction.HierarchyOp> removeTaskOps =
                wcts.stream().flatMap(wct -> wct.getHierarchyOps().stream())
                        .filter(op -> op.getType() == HIERARCHY_OP_TYPE_REMOVE_TASK)
                        .collect(Collectors.toList());
        assertWithMessage("There must be a WindowContainerTransaction to remove"
                + " 2 of the 3 tasks.")
                .that(removeTaskOps.size())
                .isEqualTo(2);
        assertThat(removeTaskOps.get(0).getContainer()).isEqualTo(task1Token);
        assertThat(removeTaskOps.get(1).getContainer()).isEqualTo(task3Token);
        assertThat(taskViewManager.getRootTaskCount()).isEqualTo(1);
        assertThat(taskViewManager.getTopTaskInLaunchRootTask().taskId).isEqualTo(2);
    }

    @Test
    public void testCreateSemiControlledTaskView_launchRootTaskViewAbsent_throwsError()
            throws Exception {
        SemiControlledCarTaskViewCallbacks taskViewCallbacks =
                mock(SemiControlledCarTaskViewCallbacks.class);
        TaskViewManager taskViewManager = createTaskViewManager();

        // The exception happens in the current stack because mShellExecutor simply calls .run()
        assertThrows(IllegalStateException.class, () -> {
            taskViewManager.createSemiControlledTaskView(
                    mActivity.getMainExecutor(),
                    taskViewCallbacks
            );
        });

        runOnMainAndWait(() -> {});
        verifyZeroInteractions(taskViewCallbacks);
    }

    @Test
    public void testCreateSemiControlledTaskView() throws Exception {
        SemiControlledCarTaskViewCallbacks taskViewCallbacks =
                mock(SemiControlledCarTaskViewCallbacks.class);
        when(taskViewCallbacks.shouldStartInTaskView(any())).thenReturn(true);
        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        AtomicReference<ShellTaskOrganizer.TaskListener> listener = new AtomicReference<>();
        setUpLaunchRootTaskView(taskViewManager, listener, /* rootTaskId = */ 1);

        taskViewManager.createSemiControlledTaskView(
                mActivity.getMainExecutor(),
                taskViewCallbacks
        );
        runOnMainAndWait(() -> {});
        // Trigger surfaceCreated on SemiControlledTaskView so that taskView can get into the
        // initialized state.
        SemiControlledCarTaskView semiControlledCarTaskView =
                taskViewManager.getSemiControlledTaskViews().get(0);
        semiControlledCarTaskView.surfaceCreated(mock(SurfaceHolder.class));
        runOnMainAndWait(() -> {});

        verify(taskViewCallbacks).onTaskViewCreated(any());
        verify(taskViewCallbacks).onTaskViewReady();
    }

    @Test
    public void testSemiControlledTaskAppeared_reparentedCorrectly() throws Exception {
        SemiControlledCarTaskViewCallbacks taskViewCallbacks =
                mock(SemiControlledCarTaskViewCallbacks.class);
        when(taskViewCallbacks.shouldStartInTaskView(any())).thenReturn(true);

        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        // Set up a LaunchRootTaskView
        AtomicReference<ShellTaskOrganizer.TaskListener> rootTaskListener = new AtomicReference<>();
        setUpLaunchRootTaskView(taskViewManager, rootTaskListener, /* rootTaskId = */ 1);
        // Set up a SemiControlledTaskView
        taskViewManager.createSemiControlledTaskView(
                mActivity.getMainExecutor(),
                taskViewCallbacks
        );
        runOnMainAndWait(() -> {});
        SemiControlledCarTaskView semiControlledCarTaskView =
                taskViewManager.getSemiControlledTaskViews().get(0);
        semiControlledCarTaskView.surfaceCreated(mock(SurfaceHolder.class));
        runOnMainAndWait(() -> {});
        TaskView.Listener mockListener = mock(TaskView.Listener.class);
        semiControlledCarTaskView.setListener(mActivity.getMainExecutor(), mockListener);

        // Act
        // Trigger a taskAppeared on the launch root task to mimic the task appearance.
        rootTaskListener.get().onTaskAppeared(createMultiWindowTask(2).getTaskInfo(), mLeash);
        runOnMainAndWait(() -> {});

        // Assert
        // Verify if the task was reparented in the SemiControlledTaskView
        verify(mockListener).onTaskCreated(eq(2), any());
    }

    @Test
    public void testSemiControlledTaskVanished_reparentedCorrectly() throws Exception {
        SemiControlledCarTaskViewCallbacks taskViewCallbacks =
                mock(SemiControlledCarTaskViewCallbacks.class);
        when(taskViewCallbacks.shouldStartInTaskView(any())).thenReturn(true);

        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        // Set up a LaunchRootTaskView
        AtomicReference<ShellTaskOrganizer.TaskListener> rootTaskListener = new AtomicReference<>();
        setUpLaunchRootTaskView(taskViewManager, rootTaskListener, /* rootTaskId = */ 1);
        // Set up a SemiControlledTaskView
        taskViewManager.createSemiControlledTaskView(
                mActivity.getMainExecutor(),
                taskViewCallbacks
        );
        runOnMainAndWait(() -> {});
        SemiControlledCarTaskView semiControlledCarTaskView =
                taskViewManager.getSemiControlledTaskViews().get(0);
        semiControlledCarTaskView.surfaceCreated(mock(SurfaceHolder.class));
        runOnMainAndWait(() -> {});
        TaskView.Listener mockListener = mock(TaskView.Listener.class);
        semiControlledCarTaskView.setListener(mActivity.getMainExecutor(), mockListener);

        ActivityManager.RunningTaskInfo semiControlledTaskInfo = createMultiWindowTask(2)
                .getTaskInfo();
        rootTaskListener.get().onTaskAppeared(semiControlledTaskInfo, mLeash);
        runOnMainAndWait(() -> {});

        // Act
        // Trigger a taskVanished on the launch root task
        rootTaskListener.get().onTaskVanished(semiControlledTaskInfo);
        runOnMainAndWait(() -> {});

        // Assert
        // Verify if the task was removed from the SemiControlledTaskView
        verify(mockListener).onTaskRemovalStarted(/* taskId = */ eq(2));
    }

    private ActivityManager.RunningTaskInfo setUpLaunchRootTaskView(TaskViewManager taskViewManager,
            AtomicReference<ShellTaskOrganizer.TaskListener> listener,
            int rootTaskId) throws Exception {
        ActivityManager.RunningTaskInfo launchRootTaskInfo =
                createMultiWindowTask(rootTaskId).getTaskInfo();
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(2));
            listener.get().onTaskAppeared(launchRootTaskInfo, mLeash);
            return null;
        }).when(mOrganizer).createRootTask(eq(DEFAULT_DISPLAY),
                eq(WINDOWING_MODE_MULTI_WINDOW),
                any(ShellTaskOrganizer.TaskListener.class));
        taskViewManager.createLaunchRootTaskView(
                mActivity.getMainExecutor(),
                mock(LaunchRootCarTaskViewCallbacks.class)
        );
        runOnMainAndWait(() -> {});
        LaunchRootCarTaskView launchRootCarTaskView = taskViewManager.getLaunchRootCarTaskView();
        launchRootCarTaskView.surfaceCreated(mock(SurfaceHolder.class));
        runOnMainAndWait(() -> {});
        return launchRootTaskInfo;
    }

    @Test
    public void testInit_registersTaskMonitor() throws Exception {
        createTaskViewManager();
        runOnMainAndWait(() -> {});

        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        verify(mCarActivityManager).registerTaskMonitor();
    }

    @Test
    public void testTaskAppeared_launchRootTaskView_updatesCarActivityManager() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);
        // Set up a LaunchRootTaskView
        AtomicReference<ShellTaskOrganizer.TaskListener> rootTaskListener = new AtomicReference<>();
        setUpLaunchRootTaskView(taskViewManager, rootTaskListener, /* rootTaskId = */ 1);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();

        // Act
        rootTaskListener.get().onTaskAppeared(taskInfo, mLeash);
        runOnMainAndWait(() -> {});

        // Assert
        verify(mCarActivityManager).onTaskAppeared(taskInfo);
    }

    @Test
    public void testTaskInfoChanged_launchRootTaskView_updatesCarActivityManager()
            throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);
        // Set up a LaunchRootTaskView
        AtomicReference<ShellTaskOrganizer.TaskListener> rootTaskListener = new AtomicReference<>();
        setUpLaunchRootTaskView(taskViewManager, rootTaskListener, /* rootTaskId = */ 1);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();

        // Act
        rootTaskListener.get().onTaskInfoChanged(taskInfo);
        runOnMainAndWait(() -> {});

        // Assert
        verify(mCarActivityManager).onTaskInfoChanged(taskInfo);
    }

    @Test
    public void testTaskVanished_launchRootTaskView_updatesCarActivityManager() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);
        // Set up a LaunchRootTaskView
        AtomicReference<ShellTaskOrganizer.TaskListener> rootTaskListener = new AtomicReference<>();
        setUpLaunchRootTaskView(taskViewManager, rootTaskListener, /* rootTaskId = */ 1);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();

        // Act
        rootTaskListener.get().onTaskVanished(taskInfo);
        runOnMainAndWait(() -> {});

        // Assert
        verify(mCarActivityManager).onTaskVanished(taskInfo);
    }

    @Test
    public void testHostActivityDestroyed_releasesAllTaskViews() throws Exception {
        testReleaseAllTaskViews(() -> {
            ActivityScenario<TestActivity> scenario = mActivityRule.getScenario();
            scenario.moveToState(Lifecycle.State.DESTROYED);
        });
    }

    private void setUpControlledTaskView(TaskViewManager taskViewManager, Intent activityIntent,
            Set<String> packagesThatCanRestart) throws Exception {
        ControlledCarTaskViewCallbacks controlledCarTaskViewCallbacks = mock(
                ControlledCarTaskViewCallbacks.class);
        when(controlledCarTaskViewCallbacks.getDependingPackageNames())
                .thenReturn(packagesThatCanRestart);
        taskViewManager.createControlledCarTaskView(
                mActivity.getMainExecutor(),
                ControlledCarTaskViewConfig.builder()
                        .setActivityIntent(activityIntent)
                        .setAutoRestartOnCrash(false)
                        .build(),
                controlledCarTaskViewCallbacks
        );

        int lastIndex = Math.min(0, taskViewManager.getControlledTaskViews().size() - 1);
        ControlledCarTaskView taskView = spy(taskViewManager.getControlledTaskViews()
                .get(lastIndex));
        doNothing().when(taskView).startActivity();

        taskView.surfaceCreated(mock(SurfaceHolder.class));
        runOnMainAndWait(() -> {});
    }

    @Test
    public void testRestartControlledTask_whenHostActivityFocussed() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        // Send onTaskVanished to mimic the task removal behavior.
        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.random.package2"));
        ControlledCarTaskView controlledCarTaskView =
                taskViewManager.getControlledTaskViews().get(0);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();
        controlledCarTaskView.onTaskAppeared(taskInfo, mLeash);
        controlledCarTaskView.onTaskVanished(taskInfo);
        assertThat(controlledCarTaskView.getTaskId()).isEqualTo(INVALID_TASK_ID);

        // Stub the taskview with a spy to assert on startActivity.
        ControlledCarTaskView spiedTaskView = spy(controlledCarTaskView);
        doNothing().when(spiedTaskView).startActivity();
        taskViewManager.getControlledTaskViews().set(0, spiedTaskView);

        // Act
        mTaskStackListenerArgumentCaptor.getValue().onTaskFocusChanged(mActivity.getTaskId(),
                /* focused = */ true);

        // Assert
        verify(spiedTaskView).startActivity();
    }

    @Test
    public void testControlledTaskNotRestarted_ifAlreadyRunning_whenHostActivityFocussed()
            throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.random.package2"));
        ControlledCarTaskView controlledCarTaskView =
                taskViewManager.getControlledTaskViews().get(0);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();
        controlledCarTaskView.onTaskAppeared(taskInfo, mLeash);

        // Stub the taskview with a spy to assert on startActivity.
        ControlledCarTaskView spiedTaskView = spy(controlledCarTaskView);
        doNothing().when(spiedTaskView).startActivity();
        taskViewManager.getControlledTaskViews().set(0, spiedTaskView);

        // Act
        mTaskStackListenerArgumentCaptor.getValue().onTaskFocusChanged(mActivity.getTaskId(),
                /* focused = */ true);

        // Assert
        verify(spiedTaskView, times(0)).startActivity();
    }

    @Test
    public void testRestartControlledTask_whenHostActivityRestarted() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        // Send onTaskVanished to mimic the task removal behavior.
        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.random.package2"));
        ControlledCarTaskView controlledCarTaskView =
                taskViewManager.getControlledTaskViews().get(0);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();
        controlledCarTaskView.onTaskAppeared(taskInfo, mLeash);
        controlledCarTaskView.onTaskVanished(taskInfo);
        assertThat(controlledCarTaskView.getTaskId()).isEqualTo(INVALID_TASK_ID);

        // Stub the taskview with a spy to assert on startActivity.
        ControlledCarTaskView spiedTaskView = spy(controlledCarTaskView);
        doNothing().when(spiedTaskView).startActivity();
        taskViewManager.getControlledTaskViews().set(0, spiedTaskView);

        // Act
        mTaskStackListenerArgumentCaptor.getValue().onActivityRestartAttempt(
                createMultiWindowTask(mActivity.getTaskId()).getTaskInfo(),
                /* homeTaskVisible = */ true, false,
                /* focused = */ true);

        // Assert
        verify(spiedTaskView).startActivity();
    }

    @Test
    public void testRestartControlledTask_whenPackageThatCanRestartChanged() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        // Send onTaskVanished to mimic the task removal behavior.
        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.relevant.package"));
        ControlledCarTaskView controlledCarTaskView =
                taskViewManager.getControlledTaskViews().get(0);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();
        controlledCarTaskView.onTaskAppeared(taskInfo, mLeash);
        controlledCarTaskView.onTaskVanished(taskInfo);
        assertThat(controlledCarTaskView.getTaskId()).isEqualTo(INVALID_TASK_ID);

        // Stub the taskview with a spy to assert on startActivity.
        ControlledCarTaskView spiedTaskView = spy(controlledCarTaskView);
        doNothing().when(spiedTaskView).startActivity();
        taskViewManager.getControlledTaskViews().set(0, spiedTaskView);

        // Act
        taskViewManager.getPackageBroadcastReceiver().onReceive(mActivity,
                new Intent().setData(Uri.parse("package:com.relevant.package")));

        // Assert
        verify(spiedTaskView).startActivity();
    }

    @Test
    public void testControlledTaskNotRestarted_whenARandomPackageChanged() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        // Send onTaskVanished to mimic the task removal behavior.
        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.relevant.package"));
        ControlledCarTaskView controlledCarTaskView =
                taskViewManager.getControlledTaskViews().get(0);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();
        controlledCarTaskView.onTaskAppeared(taskInfo, mLeash);
        controlledCarTaskView.onTaskVanished(taskInfo);
        assertThat(controlledCarTaskView.getTaskId()).isEqualTo(INVALID_TASK_ID);

        // Stub the taskview with a spy to assert on startActivity.
        ControlledCarTaskView spiedTaskView = spy(controlledCarTaskView);
        doNothing().when(spiedTaskView).startActivity();
        taskViewManager.getControlledTaskViews().set(0, spiedTaskView);

        // Act
        taskViewManager.getPackageBroadcastReceiver().onReceive(mActivity,
                new Intent().setData(Uri.parse("package:com.random.package")));

        // Assert
        verify(spiedTaskView, times(0)).startActivity();
    }

    // User switch related tests.

    @Test
    public void testRestartControlledTask_onUserUnlocked() throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);

        // Send onTaskVanished to mimic the task removal behavior.
        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.random.package2"));
        ControlledCarTaskView controlledCarTaskView =
                taskViewManager.getControlledTaskViews().get(0);
        ActivityManager.RunningTaskInfo taskInfo = createMultiWindowTask(2).getTaskInfo();
        controlledCarTaskView.onTaskAppeared(taskInfo, mLeash);
        controlledCarTaskView.onTaskVanished(taskInfo);
        assertThat(controlledCarTaskView.getTaskId()).isEqualTo(INVALID_TASK_ID);

        // Stub the taskview with a spy to assert on startActivity.
        ControlledCarTaskView spiedTaskView = spy(controlledCarTaskView);
        doNothing().when(spiedTaskView).startActivity();
        taskViewManager.getControlledTaskViews().set(0, spiedTaskView);

        // Act
        mUserLifecycleListenerArgumentCaptor.getValue().onEvent(
                new CarUserManager.UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED,
                        mActivity.getUserId()));

        // Assert
        verify(spiedTaskView).startActivity();
    }

    @Test
    public void testUserSwitch_releasesAllTaskViews() throws Exception {
        testReleaseAllTaskViews(/* actionBlock= */ () ->
                mUserLifecycleListenerArgumentCaptor.getValue().onEvent(
                        new CarUserManager.UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
                                /* from= */ mActivity.getUserId(), /* to= */ 20))
        );
    }

    private void testReleaseAllTaskViews(Runnable actionBlock) throws Exception {
        TaskViewManager taskViewManager = createTaskViewManager();
        runOnMainAndWait(() -> {});
        mCarServiceLifecycleListener.onLifecycleChanged(mCar, true);
        // Create a few TaskViews
        AtomicReference<ShellTaskOrganizer.TaskListener> listener = new AtomicReference<>();
        setUpLaunchRootTaskView(taskViewManager, listener, /* rootTaskId = */ 1);
        LaunchRootCarTaskView launchRootCarTaskView = taskViewManager.getLaunchRootCarTaskView();

        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.random.package"));
        ControlledCarTaskView controlledCarTaskView = taskViewManager.getControlledTaskViews()
                .get(0);
        setUpControlledTaskView(taskViewManager, new Intent("ACTION_VIEW"),
                ImmutableSet.of("com.random.package2"));
        ControlledCarTaskView controlledCarTaskView2 = taskViewManager.getControlledTaskViews()
                .get(1);

        SemiControlledCarTaskViewCallbacks taskViewCallbacks =
                mock(SemiControlledCarTaskViewCallbacks.class);
        when(taskViewCallbacks.shouldStartInTaskView(any())).thenReturn(true);
        taskViewManager.createSemiControlledTaskView(
                mActivity.getMainExecutor(),
                taskViewCallbacks
        );
        runOnMainAndWait(() -> {});
        taskViewManager.createSemiControlledTaskView(
                mActivity.getMainExecutor(),
                taskViewCallbacks
        );
        runOnMainAndWait(() -> {});
        // Trigger surfaceCreated on SemiControlledTaskView.
        SemiControlledCarTaskView semiControlledCarTaskView =
                taskViewManager.getSemiControlledTaskViews().get(0);
        semiControlledCarTaskView.surfaceCreated(mock(SurfaceHolder.class));
        runOnMainAndWait(() -> {});
        SemiControlledCarTaskView semiControlledCarTaskView2 =
                taskViewManager.getSemiControlledTaskViews().get(1);
        semiControlledCarTaskView2.surfaceCreated(mock(SurfaceHolder.class));
        runOnMainAndWait(() -> {});

        // Act
        actionBlock.run();

        // Assert
        assertThat(launchRootCarTaskView.isInitialized()).isFalse();
        assertThat(controlledCarTaskView.isInitialized()).isFalse();
        assertThat(controlledCarTaskView2.isInitialized()).isFalse();
        assertThat(semiControlledCarTaskView.isInitialized()).isFalse();
        assertThat(semiControlledCarTaskView2.isInitialized()).isFalse();

        assertThat(taskViewManager.getSemiControlledTaskViews()).isEmpty();
        assertThat(taskViewManager.getLaunchRootCarTaskView()).isNull();
        assertThat(taskViewManager.getControlledTaskViews()).isEmpty();

        verify(mOrganizer).unregisterOrganizer();
        verify(mTaskViewInputInterceptor).release();
    }

    private TaskViewManager createTaskViewManager() {
        // InstrumentationTestRunner prepares a looper, but AndroidJUnitRunner does not.
        // http://b/25897652.
        Looper looper = Looper.myLooper();
        if (looper == null) {
            Looper.prepare();
        }

        TaskViewManager taskViewManager =  new TaskViewManager(mActivity, mShellExecutor,
                mOrganizer, mSyncQueue, new ShellInit(mShellExecutor), mStartingWindowController);
        taskViewManager.setTaskViewInputInterceptor(mTaskViewInputInterceptor);
        return taskViewManager;
    }

    private void runOnMainAndWait(Runnable r) throws Exception {
        mActivity.getMainExecutor().execute(() -> {
            r.run();
            mIdleHandlerLatch.countDown();
            mIdleHandlerLatch = new CountDownLatch(1);
        });
        mIdleHandlerLatch.await(5, TimeUnit.SECONDS);
    }

    public static class TestActivity extends Activity {
        private static final int FINISH_TIMEOUT_MS = 1000;
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
        }

        void finishCompletely() throws InterruptedException {
            finish();
            mDestroyed.await(FINISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
