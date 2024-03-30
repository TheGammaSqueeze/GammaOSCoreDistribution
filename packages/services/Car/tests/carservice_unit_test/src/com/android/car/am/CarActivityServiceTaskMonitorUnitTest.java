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
package com.android.car;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.wm.shell.ShellTaskOrganizer.TASK_LISTENER_TYPE_FULLSCREEN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Instrumentation.ActivityMonitor;
import android.app.TaskInfo;
import android.car.test.util.DisplayUtils.VirtualDisplaySession;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.am.CarActivityService;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarActivityServiceTaskMonitorUnitTest {
    private static final String TAG = CarActivityServiceTaskMonitorUnitTest.class.getSimpleName();

    private static final long ACTIVITY_TIMEOUT_MS = 5000;
    private static final long DEFAULT_TIMEOUT_MS = 10_000;
    private static final int SLEEP_MS = 50;
    private static CopyOnWriteArrayList<Activity> sTestActivities = new CopyOnWriteArrayList<>();

    private CarActivityService mService;
    private final IBinder mToken = new Binder();
    private ShellTaskOrganizer mTaskOrganizer;
    private FullscreenTaskListener mFullscreenTaskListener;

    @Before
    public void setUp() throws Exception {
        mService = new CarActivityService(getContext());
        mService.init();
        mService.registerTaskMonitor(mToken);
        setUpTaskOrganizer();
    }

    @After
    public void tearDown() {
        tearDownTaskOrganizer();
        for (Activity activity : sTestActivities) {
            activity.finish();
        }
        mService.registerActivityLaunchListener(null);
        mService.unregisterTaskMonitor(mToken);
        mService.release();
        mService = null;
    }

    private void setUpTaskOrganizer() throws Exception {
        Context context = getContext();
        HandlerExecutor mExecutor = new HandlerExecutor(context.getMainThreadHandler());
        mTaskOrganizer = new ShellTaskOrganizer(mExecutor);
        TransactionPool transactionPool = new TransactionPool();
        SyncTransactionQueue syncQueue = new SyncTransactionQueue(transactionPool, mExecutor);
        mFullscreenTaskListener = new TestTaskListener(syncQueue);
        mTaskOrganizer.addListenerForType(mFullscreenTaskListener, TASK_LISTENER_TYPE_FULLSCREEN);
        mTaskOrganizer.registerOrganizer();
    }

    private void tearDownTaskOrganizer() {
        mTaskOrganizer.removeListener(mFullscreenTaskListener);
        mTaskOrganizer.unregisterOrganizer();
    }

    private class TestTaskListener extends FullscreenTaskListener {
        TestTaskListener(SyncTransactionQueue syncQueue) {
            super(syncQueue);
        }

        @Override
        public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
            super.onTaskAppeared(taskInfo, leash);
            mService.onTaskAppeared(mToken, taskInfo);
        }

        @Override
        public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
            super.onTaskInfoChanged(taskInfo);
            mService.onTaskInfoChanged(mToken, taskInfo);
        }

        @Override
        public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
            super.onTaskVanished(taskInfo);
            mService.onTaskVanished(mToken, taskInfo);
        }
    }

    @Test
    public void testActivityLaunch() throws Exception {
        ComponentName activityA = toComponentName(getTestContext(), ActivityA.class);
        FilteredLaunchListener listenerA = new FilteredLaunchListener(activityA);
        mService.registerActivityLaunchListener(listenerA);
        startActivity(activityA);
        listenerA.assertTopTaskActivityLaunched();

        ComponentName activityB = toComponentName(getTestContext(), ActivityB.class);
        FilteredLaunchListener listenerB = new FilteredLaunchListener(activityB);
        mService.registerActivityLaunchListener(listenerB);
        startActivity(activityB);
        listenerB.assertTopTaskActivityLaunched();
    }

    @Test
    public void testActivityBlocking() throws Exception {
        ComponentName denyListedActivity = toComponentName(getTestContext(), ActivityC.class);
        ComponentName blockingActivity = toComponentName(getTestContext(), BlockingActivity.class);
        Intent blockingIntent = new Intent();
        blockingIntent.setComponent(blockingActivity);

        // start a black listed activity
        FilteredLaunchListener listenerDenyListed =
                new FilteredLaunchListener(denyListedActivity);
        mService.registerActivityLaunchListener(listenerDenyListed);
        startActivity(denyListedActivity);
        listenerDenyListed.assertTopTaskActivityLaunched();

        // Instead of start activity, invoke blockActivity.
        FilteredLaunchListener listenerBlocking = new FilteredLaunchListener(blockingActivity);
        mService.registerActivityLaunchListener(listenerBlocking);
        mService.blockActivity(listenerDenyListed.mTopTask, blockingIntent);
        listenerBlocking.assertTopTaskActivityLaunched();
    }

    @Test
    public void testRemovesFromTopTasks() throws Exception {
        ComponentName activityA = toComponentName(getTestContext(), ActivityA.class);
        FilteredLaunchListener listenerA = new FilteredLaunchListener(activityA);
        mService.registerActivityLaunchListener(listenerA);
        Activity launchedActivity = startActivity(activityA);
        listenerA.assertTopTaskActivityLaunched();
        assertTrue(topTasksHasComponent(activityA));

        getInstrumentation().runOnMainSync(launchedActivity::finish);
        waitUntil(() -> !topTasksHasComponent(activityA));
    }

    @Test
    public void testGetTopTasksOnMultiDisplay() throws Exception {
        // TaskOrganizer gets the callbacks only on the tasks launched in the actual Surface.
        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            int virtualDisplayId = session.createDisplayWithDefaultDisplayMetricsAndWait(
                    getTestContext(), /* isPrivate= */ false).getDisplayId();

            ComponentName activityA = toComponentName(getTestContext(), ActivityA.class);
            FilteredLaunchListener
                    listenerA = new FilteredLaunchListener(activityA);
            mService.registerActivityLaunchListener(listenerA);
            startActivity(activityA, Display.DEFAULT_DISPLAY);
            listenerA.assertTopTaskActivityLaunched();
            assertTrue(topTasksHasComponent(activityA));

            ComponentName activityB = toComponentName(getTestContext(), ActivityB.class);
            FilteredLaunchListener
                    listenerB = new FilteredLaunchListener(activityB);
            mService.registerActivityLaunchListener(listenerB);
            startActivity(activityB, virtualDisplayId);
            listenerB.assertTopTaskActivityLaunched();
            assertTrue(topTasksHasComponent(activityB));
            assertTrue(topTasksHasComponent(activityA));

            ComponentName activityC = toComponentName(getTestContext(), ActivityC.class);
            FilteredLaunchListener
                    listenerC = new FilteredLaunchListener(activityC);
            mService.registerActivityLaunchListener(listenerC);
            startActivity(activityC, virtualDisplayId);
            listenerC.assertTopTaskActivityLaunched();
            assertTrue(topTasksHasComponent(activityC));
            assertFalse(topTasksHasComponent(activityB));
            assertTrue(topTasksHasComponent(activityA));
        }
    }

    @Test
    public void testGetTopTasksOnDefaultDisplay() throws Exception {
        ComponentName activityA = toComponentName(getTestContext(), ActivityA.class);
        FilteredLaunchListener
                listenerA = new FilteredLaunchListener(activityA);
        mService.registerActivityLaunchListener(listenerA);
        startActivity(activityA, Display.DEFAULT_DISPLAY);
        listenerA.assertTopTaskActivityLaunched();
        assertTrue(topTasksHasComponent(activityA));

        ComponentName activityB = toComponentName(getTestContext(), ActivityB.class);
        FilteredLaunchListener
                listenerB = new FilteredLaunchListener(activityB);
        mService.registerActivityLaunchListener(listenerB);
        startActivity(activityB, Display.DEFAULT_DISPLAY);
        listenerB.assertTopTaskActivityLaunched();
        assertTrue(topTasksHasComponent(activityB));
        assertFalse(topTasksHasComponent(activityA));
    }

    @Test
    public void testGetTaskInfoForTopActivity() throws Exception {
        ComponentName activityA = toComponentName(getTestContext(), ActivityA.class);
        FilteredLaunchListener
                listenerA = new FilteredLaunchListener(activityA);
        mService.registerActivityLaunchListener(listenerA);
        startActivity(activityA);
        listenerA.assertTopTaskActivityLaunched();

        TaskInfo taskInfo = mService.getTaskInfoForTopActivity(activityA);
        assertNotNull(taskInfo);
        assertEquals(activityA, taskInfo.topActivity);
    }

    @Test
    public void testRestartTask() throws Exception {
        ComponentName activityA = toComponentName(getTestContext(), ActivityA.class);
        FilteredLaunchListener listenerA = new FilteredLaunchListener(activityA);
        mService.registerActivityLaunchListener(listenerA);
        startActivity(activityA);
        listenerA.assertTopTaskActivityLaunched();

        ComponentName activityB = toComponentName(getTestContext(), ActivityB.class);
        FilteredLaunchListener listenerB = new FilteredLaunchListener(activityB);
        mService.registerActivityLaunchListener(listenerB);
        startActivity(activityB);
        listenerB.assertTopTaskActivityLaunched();

        FilteredLaunchListener listenerRestartA = new FilteredLaunchListener(activityA);
        mService.registerActivityLaunchListener(listenerRestartA);

        // ActivityA and ActivityB are in the same package, so ActivityA becomes the root task of
        // ActivityB, so when we restarts ActivityB, it'll start ActivityA.
        TaskInfo taskInfo = mService.getTaskInfoForTopActivity(activityB);
        mService.restartTask(taskInfo.taskId);

        listenerRestartA.assertTopTaskActivityLaunched();
    }

    private void waitUntil(BooleanSupplier condition) {
        for (long i = DEFAULT_TIMEOUT_MS / SLEEP_MS; !condition.getAsBoolean() && i > 0; --i) {
            SystemClock.sleep(SLEEP_MS);
        }
        if (!condition.getAsBoolean()) {
            throw new RuntimeException("failed while waiting for condition to become true");
        }
    }

    private boolean topTasksHasComponent(ComponentName component) {
        for (TaskInfo topTaskInfoContainer : mService.getVisibleTasks()) {
            if (topTaskInfoContainer.topActivity.equals(component)) {
                return true;
            }
        }
        return false;
    }

    /** Activity that closes itself after some timeout to clean up the screen. */
    public static class TempActivity extends Activity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sTestActivities.add(this);
        }
    }

    public static class ActivityA extends TempActivity {}

    public static class ActivityB extends TempActivity {}

    public static class ActivityC extends TempActivity {}

    public static class BlockingActivity extends TempActivity {}

    private static Context getContext() {
        return getInstrumentation().getTargetContext();
    }

    private static Context getTestContext() {
        return getInstrumentation().getContext();
    }

    private static ComponentName toComponentName(Context ctx, Class<?> cls) {
        return ComponentName.createRelative(ctx, cls.getName());
    }

    private static Activity startActivity(ComponentName name) {
        return startActivity(name, Display.DEFAULT_DISPLAY);
    }

    private static Activity startActivity(ComponentName name, int displayId) {
        ActivityMonitor monitor = new ActivityMonitor(name.getClassName(), null, false);
        getInstrumentation().addMonitor(monitor);

        Intent intent = new Intent();
        intent.setComponent(name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Bundle bundle = null;
        if (displayId != Display.DEFAULT_DISPLAY) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            bundle = options.toBundle();
        }
        getContext().startActivity(intent, bundle);
        return monitor.waitForActivityWithTimeout(ACTIVITY_TIMEOUT_MS);
    }

    private class FilteredLaunchListener
            implements CarActivityService.ActivityLaunchListener {

        private final ComponentName mDesiredComponent;
        private final CountDownLatch mActivityLaunched = new CountDownLatch(1);
        private TaskInfo mTopTask;

        /**
         * Creates an instance of an
         * {@link com.android.car.am.CarActivityService.ActivityLaunchListener}
         * that filters based on the component name or does not filter if component name is null.
         */
        FilteredLaunchListener(@NonNull ComponentName desiredComponent) {
            mDesiredComponent = desiredComponent;
        }

        @Override
        public void onActivityLaunch(TaskInfo topTask) {
            // Ignore activities outside of this test case
            if (!getTestContext().getPackageName().equals(topTask.topActivity.getPackageName())) {
                Log.d(TAG, "Component launched from other package: "
                        + topTask.topActivity.getClassName());
                return;
            }
            if (!topTask.topActivity.equals(mDesiredComponent)) {
                Log.d(TAG, String.format("Unexpected component: %s. Expected: %s",
                        topTask.topActivity.getClassName(), mDesiredComponent));
                return;
            }
            if (mTopTask == null) {  // We are interested in the first one only.
                mTopTask = topTask;
            }
            mActivityLaunched.countDown();
        }

        void assertTopTaskActivityLaunched() throws InterruptedException {
            assertTrue(mActivityLaunched.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }
}
