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

import static com.android.car.carlauncher.TaskViewManager.DBG;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.graphics.Rect;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A controlled {@link CarTaskView} is fully managed by the {@link TaskViewManager}.
 * The underlying task will be restarted if it is crashed.
 *
 * It should be used when:
 * <ul>
 *     <li>The underlying task is meant to be started by the host and be there forever.</li>
 * </ul>
 */
final class ControlledCarTaskView extends CarTaskView {
    private static final String TAG = ControlledCarTaskView.class.getSimpleName();

    private final Executor mCallbackExecutor;
    private final ControlledCarTaskViewCallbacks mCallbacks;
    private final UserManager mUserManager;
    private final TaskViewManager mTaskViewManager;
    private final ControlledCarTaskViewConfig mConfig;

    ControlledCarTaskView(
            Activity context,
            ShellTaskOrganizer organizer,
            SyncTransactionQueue syncQueue,
            Executor callbackExecutor,
            ControlledCarTaskViewConfig controlledCarTaskViewConfig,
            ControlledCarTaskViewCallbacks callbacks,
            UserManager userManager,
            TaskViewManager taskViewManager) {
        super(context, organizer, syncQueue);
        mCallbackExecutor = callbackExecutor;
        mConfig = controlledCarTaskViewConfig;
        mCallbacks = callbacks;
        mUserManager = userManager;
        mTaskViewManager = taskViewManager;

        mCallbackExecutor.execute(() -> mCallbacks.onTaskViewCreated(this));
    }

    @Override
    protected void onCarTaskViewInitialized() {
        super.onCarTaskViewInitialized();
        startActivity();
        mCallbackExecutor.execute(() -> mCallbacks.onTaskViewReady());
    }

    /**
     * Starts the underlying activity.
     */
    public void startActivity() {
        if (!mUserManager.isUserUnlocked()) {
            if (DBG) Log.d(TAG, "Can't start activity due to user is isn't unlocked");
            return;
        }

        // Don't start activity when the display is off. This can happen when the taskview is not
        // attached to a window.
        if (getDisplay() == null) {
            Log.w(TAG, "Can't start activity because display is not available in "
                    + "taskview yet.");
            return;
        }
        // Don't start activity when the display is off for ActivityVisibilityTests.
        if (getDisplay().getState() != Display.STATE_ON) {
            Log.w(TAG, "Can't start activity due to the display is off");
            return;
        }

        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext,
                /* enterResId= */ 0, /* exitResId= */ 0);
        Rect launchBounds = new Rect();
        getBoundsOnScreen(launchBounds);
        if (DBG) {
            Log.d(TAG, "Starting (" + mConfig.mActivityIntent.getComponent() + ") on "
                    + launchBounds);
        }
        startActivity(
                PendingIntent.getActivity(mContext, /* requestCode= */ 0,
                        mConfig.mActivityIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT),
                /* fillInIntent= */ null, options, launchBounds);
    }

    /** Gets the config used to build this controlled car task view. */
    ControlledCarTaskViewConfig getConfig() {
        return mConfig;
    }

    /**
     * See {@link ControlledCarTaskViewCallbacks#getDependingPackageNames()}.
     */
    Set<String> getDependingPackageNames() {
        return mCallbacks.getDependingPackageNames();
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskVanished(taskInfo);
        if (mConfig.mAutoRestartOnCrash && mTaskViewManager.isHostVisible()) {
            // onTaskVanished can be called when the host is in the background. In this case
            // embedded activity should not be started.
            Log.i(TAG, "Restarting task " + taskInfo.baseActivity
                    + " in ControlledCarTaskView");
            startActivity();
        }
    }

    @Override
    public void showEmbeddedTask(WindowContainerTransaction wct) {
        if (mTaskInfo == null) {
            if (DBG) {
                Log.d(TAG, "Embedded task not available, starting it now.");
            }
            startActivity();
            return;
        }
        super.showEmbeddedTask(wct);
    }
}
