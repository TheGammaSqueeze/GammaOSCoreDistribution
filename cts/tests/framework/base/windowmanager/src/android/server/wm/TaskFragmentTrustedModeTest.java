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

package android.server.wm;

import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.jetpack.second.Components.SECOND_UNTRUSTED_EMBEDDING_ACTIVITY;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assumeActivityEmbeddingSupportedDevice;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState.Task;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests that verifies the behaviors of embedding activities in different trusted modes.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:TaskFragmentTrustedModeTest
 */
@Presubmit
public class TaskFragmentTrustedModeTest extends TaskFragmentOrganizerTestBase {

    private final ComponentName mTranslucentActivity = new ComponentName(mContext,
            TranslucentActivity.class);

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeActivityEmbeddingSupportedDevice();
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when there is an overlay over the task fragment.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_overlayTaskFragment() {
        // Create a task fragment with activity in untrusted mode.
        final TaskFragmentInfo tf = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Start a translucent activity over the TaskFragment.
        createTaskFragment(mTranslucentActivity, partialOverlayBounds(tf));
        waitAndAssertResumedActivity(mTranslucentActivity, "Translucent activity must be resumed.");

        // The task fragment must be made invisible when there is an overlay activity in it.
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        waitAndAssertStoppedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, overlayMessage);
        assertFalse(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());

        // The activity that appeared on top would stay resumed
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                mTranslucentActivity).isVisible());
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when an activity from another process is started on top.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_startActivityInTaskFragment() {
        // Create a task fragment with activity in untrusted mode.
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Start an activity with a different UID in the TaskFragment.
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .startActivityInTaskFragment(taskFragmentInfo.getFragmentToken(), mOwnerToken,
                        new Intent().setComponent(mTranslucentActivity),
                        null /* activityOptions */);
        mTaskFragmentOrganizer.applyTransaction(wct);
        waitAndAssertResumedActivity(mTranslucentActivity, "Translucent activity must be resumed.");

        // Some activities in the task fragment must be made invisible when there is an overlay.
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        waitAndAssertStoppedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, overlayMessage);

        // The activity that appeared on top would stay resumed, and the task fragment is still
        // visible.
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());
    }

    /**
     * Verifies the visibility of a task fragment that has overlays on top of activities embedded
     * in untrusted mode when an activity from another process is reparented on top.
     */
    @Test
    public void testUntrustedModeTaskFragmentVisibility_reparentActivityInTaskFragment() {
        final Activity translucentActivity = startActivity(TranslucentActivity.class);

        // Create a task fragment with activity in untrusted mode.
        final TaskFragmentInfo taskFragmentInfo = createTaskFragment(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Reparent a translucent activity with a different UID to the TaskFragment.
        final IBinder embeddedActivityToken = getActivityToken(translucentActivity);
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .reparentActivityToTaskFragment(taskFragmentInfo.getFragmentToken(),
                        embeddedActivityToken);
        mTaskFragmentOrganizer.applyTransaction(wct);
        waitAndAssertResumedActivity(mTranslucentActivity, "Translucent activity must be resumed.");

        // Some activities in the task fragment must be made invisible when there is an overlay.
        final String overlayMessage = "Activities embedded in untrusted mode should be made "
                + "invisible in a task fragment with overlay";
        waitAndAssertStoppedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, overlayMessage);

        // The activity that appeared on top would stay resumed, and the task fragment is still
        // visible
        assertTrue(overlayMessage, mWmState.hasActivityState(mTranslucentActivity, STATE_RESUMED));
        assertTrue(overlayMessage, mWmState.isActivityVisible(mTranslucentActivity));
        assertTrue(overlayMessage, mWmState.getTaskFragmentByActivity(
                SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).isVisible());

        // Finishing the overlay activity must make TaskFragment visible again.
        translucentActivity.finish();
        waitAndAssertResumedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                "Activity must be resumed without overlays");
        assertTrue("Activity must be visible without overlays",
                mWmState.isActivityVisible(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY));
    }

    /**
     * Verifies that when the TaskFragment has embedded activities in untrusted mode, it is
     * disallowed to set bounds that is outside of its parent bounds.
     */
    @Test
    public void testUntrustedModeTaskFragment_setBoundsOutsideOfParentBounds() {
        final Task parentTask = mWmState.getRootTask(mOwnerTaskId);
        final Rect parentBounds = new Rect(parentTask.getBounds());
        // Create a TaskFragment with activity embedded in untrusted mode.
        final TaskFragmentInfo info = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Try to set bounds that is outside of its parent bounds.
        mTaskFragmentOrganizer.resetLatch();
        final Rect taskFragBounds = new Rect(parentBounds);
        taskFragBounds.right++;
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .setBounds(info.getToken(), taskFragBounds);

        // It is disallowed to set TaskFragment bounds to outside of its parent bounds.
        assertThrows(SecurityException.class, () -> mTaskFragmentOrganizer.applyTransaction(wct));
    }

    /**
     * Verifies that when the TaskFragment has embedded activities in untrusted mode, it is
     * disallowed to set app bounds that is outside of its parent app bounds.
     */
    @Test
    public void testUntrustedModeTaskFragment_setAppBoundsOutsideOfParentAppBounds() {
        final Task parentTask = mWmState.getRootTask(mOwnerTaskId);
        final Rect parentAppBounds =
                new Rect(parentTask.mFullConfiguration.windowConfiguration.getAppBounds());
        // Create a TaskFragment with activity embedded in untrusted mode.
        final TaskFragmentInfo info = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Try to set app bounds that is outside of its parent app bounds.
        mTaskFragmentOrganizer.resetLatch();
        final Rect taskFragAppBounds = new Rect(parentAppBounds);
        taskFragAppBounds.right++;
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .setAppBounds(info.getToken(), taskFragAppBounds);

        // It is disallowed to set TaskFragment app bounds to outside of its parent app bounds.
        assertThrows(SecurityException.class, () -> mTaskFragmentOrganizer.applyTransaction(wct));
    }

    /**
     * Verifies that when the TaskFragment has embedded activities in untrusted mode, it is
     * disallowed to set screenWidthDp/screenHeightDp/smallestScreenWidthDp greater than parent's.
     */
    @Test
    public void testUntrustedModeTaskFragment_setSetScreenWidthHeightGreaterThanParent() {
        final Task parentTask = mWmState.getRootTask(mOwnerTaskId);
        final int screenWidthDp = parentTask.mFullConfiguration.screenWidthDp;
        final int screenHeightDp = parentTask.mFullConfiguration.screenHeightDp;
        final int smallestScreenWidthDp = parentTask.mFullConfiguration.smallestScreenWidthDp;
        // Create a TaskFragment with activity embedded in untrusted mode.
        final TaskFragmentInfo info = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);

        // Try to set screenWidthDp greater than parent's.
        mTaskFragmentOrganizer.resetLatch();
        final WindowContainerTransaction wct0 = new WindowContainerTransaction()
                .setScreenSizeDp(info.getToken(), screenWidthDp + 1, screenHeightDp);

        // It is disallowed to set TaskFragment screenWidthDp to be greater than parent's.
        assertThrows(SecurityException.class, () -> mTaskFragmentOrganizer.applyTransaction(wct0));

        // Try to set screenHeightDp greater than parent's.
        mTaskFragmentOrganizer.resetLatch();
        final WindowContainerTransaction wct1 = new WindowContainerTransaction()
                .setScreenSizeDp(info.getToken(), screenWidthDp, screenHeightDp + 1);

        // It is disallowed to set TaskFragment screenHeightDp to be greater than parent's.
        assertThrows(SecurityException.class, () -> mTaskFragmentOrganizer.applyTransaction(wct1));

        // Try to set smallestScreenWidthDp greater than parent's.
        mTaskFragmentOrganizer.resetLatch();
        final WindowContainerTransaction wct2 = new WindowContainerTransaction()
                .setSmallestScreenWidthDp(info.getToken(), smallestScreenWidthDp + 1);

        // It is disallowed to set TaskFragment smallestScreenWidthDp to be greater than parent's.
        assertThrows(SecurityException.class, () -> mTaskFragmentOrganizer.applyTransaction(wct2));
    }

    /**
     * Verifies that when the TaskFragment bounds is outside of its parent bounds, it is disallowed
     * to start activity in untrusted mode.
     */
    @Test
    public void testUntrustedModeTaskFragment_startActivityInTaskFragmentOutsideOfParentBounds() {
        final Task parentTask = mWmState.getRootTask(mOwnerTaskId);
        final Rect parentBounds = new Rect(parentTask.getBounds());
        final IBinder errorCallbackToken = new Binder();
        final WindowContainerTransaction wct = new WindowContainerTransaction()
                .setErrorCallbackToken(errorCallbackToken);

        // We check if the TaskFragment bounds is in its parent bounds before launching activity in
        // untrusted mode.
        final Rect taskFragBounds = new Rect(parentBounds);
        taskFragBounds.right++;
        createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY, taskFragBounds, wct);

        // It is disallowed to start activity to TaskFragment with bounds outside of its parent
        // in untrusted mode.
        assertTaskFragmentError(errorCallbackToken, SecurityException.class);
    }

    /**
     * Verifies that when the TaskFragment bounds is outside of its parent bounds, it is disallowed
     * to reparent children of a TaskFragment to another in untrusted mode.
     */
    @Test
    public void testUntrustedModeTaskFragment_reparentChildrenOutsideOfParentBounds() {
        // Create a TaskFragment with activity in trusted mode with bounds outside of its parent.
        final Task parentTask = mWmState.getRootTask(mOwnerTaskId);
        final Rect parentBounds = new Rect(parentTask.getBounds());
        final Rect taskFragBounds = new Rect(parentBounds);
        taskFragBounds.right++;
        final TaskFragmentCreationParams params1 = generateTaskFragCreationParams(
                taskFragBounds);
        final IBinder taskFragToken = params1.getFragmentToken();
        final WindowContainerTransaction wct1 = new WindowContainerTransaction()
                .createTaskFragment(params1)
                .reparentActivityToTaskFragment(taskFragToken, mOwnerToken);
        mTaskFragmentOrganizer.applyTransaction(wct1);
        mTaskFragmentOrganizer.waitForTaskFragmentCreated();
        final TaskFragmentInfo info1 = mTaskFragmentOrganizer.getTaskFragmentInfo(taskFragToken);

        // Create a TaskFragment with activity in untrusted mode.
        mTaskFragmentOrganizer.resetLatch();
        final TaskFragmentInfo info2 = createTaskFragment(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY);
        waitAndAssertResumedActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                "Untrusted embedding activity must be resumed.");
        final Rect activityBounds = new Rect(mWmState
                .getActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).getBounds());

        // Reparent children of the untrusted TaskFragment to the TaskFragment with larger bounds.
        mTaskFragmentOrganizer.resetLatch();
        final IBinder errorCallbackToken = new Binder();
        final WindowContainerTransaction wct2 = new WindowContainerTransaction()
                .setErrorCallbackToken(errorCallbackToken)
                .reparentChildren(info2.getToken(), info1.getToken());
        mTaskFragmentOrganizer.applyTransaction(wct2);

        // It is disallowed to reparent children to TaskFragment with bounds outside of its parent
        // in untrusted mode.
        assertTaskFragmentError(errorCallbackToken, SecurityException.class);
        mWmState.waitForAppTransitionIdleOnDisplay(mOwnerActivity.getDisplayId());
        assertEquals(activityBounds,
                mWmState.getActivity(SECOND_UNTRUSTED_EMBEDDING_ACTIVITY).getBounds());
    }

    /**
     * Creates bounds for a container that would appear on top and partially occlude the provided
     * one.
     */
    @NonNull
    private Rect partialOverlayBounds(@NonNull TaskFragmentInfo info) {
        final Rect baseBounds = info.getConfiguration().windowConfiguration.getBounds();
        final Rect result = new Rect(baseBounds);
        result.inset(50 /* left */, 50 /* top */, 50 /* right */, 50 /* bottom */);
        return result;
    }

    /** Asserts that the organizer received an error callback. */
    private void assertTaskFragmentError(@NonNull IBinder errorCallbackToken,
            @NonNull Class<? extends Throwable> exceptionClass) {
        mTaskFragmentOrganizer.waitForTaskFragmentError();
        assertThat(mTaskFragmentOrganizer.getThrowable()).isInstanceOf(exceptionClass);
        assertThat(mTaskFragmentOrganizer.getErrorCallbackToken()).isEqualTo(errorCallbackToken);
    }

    public static class TranslucentActivity extends FocusableActivity {}
}
