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

package android.server.wm.jetpack.utils;

import static android.server.wm.jetpack.utils.ExtensionUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.utils.ExtensionUtil.getWindowExtensions;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.getActivityBounds;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.getMaximumActivityBounds;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.getResumedActivityById;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.isActivityResumed;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.startActivityFromActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.LayoutDirection;
import android.util.Log;
import android.util.Pair;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.extensions.core.util.function.Predicate;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;
import androidx.window.extensions.embedding.SplitRule;

import com.android.compatibility.common.util.PollingCheck;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for activity embedding tests.
 */
public class ActivityEmbeddingUtil {

    public static final String TAG = "ActivityEmbeddingTests";
    public static final long WAIT_FOR_LIFECYCLE_TIMEOUT_MS = 3000;
    public static final float DEFAULT_SPLIT_RATIO = 0.5f;
    public static final float UNEVEN_CONTAINERS_DEFAULT_SPLIT_RATIO = 0.7f;
    public static final String EMBEDDED_ACTIVITY_ID = "embedded_activity_id";

    @NonNull
    public static SplitPairRule createWildcardSplitPairRule(boolean shouldClearTop) {
        // Build the split pair rule
        return createSplitPairRuleBuilderWithJava8Predicate(
                // Any activity be split with any activity
                activityActivityPair -> true,
                // Any activity can launch any split intent
                activityIntentPair -> true,
                // Allow any parent bounds to show the split containers side by side
                windowMetrics -> true)
                .setSplitRatio(DEFAULT_SPLIT_RATIO)
                .setShouldClearTop(shouldClearTop)
                .build();
    }

    @NonNull
    public static SplitPairRule createWildcardSplitPairRuleWithPrimaryActivityClass(
            Class<? extends Activity> activityClass, boolean shouldClearTop) {
        return createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(activityClass,
                shouldClearTop).build();
    }

    @NonNull
    public static SplitPairRule.Builder createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
            Class<? extends Activity> activityClass, boolean shouldClearTop) {
        // Build the split pair rule
        return createSplitPairRuleBuilderWithJava8Predicate(
                // The specified activity be split any activity
                activityActivityPair -> activityActivityPair.first.getClass().equals(activityClass),
                // The specified activity can launch any split intent
                activityIntentPair -> activityIntentPair.first.getClass().equals(activityClass),
                // Allow any parent bounds to show the split containers side by side
                windowMetrics -> true)
                .setSplitRatio(DEFAULT_SPLIT_RATIO)
                .setShouldClearTop(shouldClearTop);
    }

    @NonNull
    public static SplitPairRule createWildcardSplitPairRule() {
        return createWildcardSplitPairRule(false /* shouldClearTop */);
    }

    /**
     * A wrapper to create {@link SplitPairRule} builder with Java 8 Predicate to prevent ambiguous
     * issue when using lambda expressions.
     * <p>
     * It should only be used if
     * {@link #createSplitPairRuleBuilder(Predicate, Predicate, Predicate)} cannot be called prior
     * to {@link ExtensionUtil#EXTENSION_VERSION_2}.
     */
    @NonNull
    public static SplitPairRule.Builder createSplitPairRuleBuilderWithJava8Predicate(
            @NonNull java.util.function.Predicate<Pair<Activity, Activity>> activitiesPairPredicate,
            @NonNull java.util.function.Predicate<Pair<Activity, Intent>>
                    activityIntentPairPredicate,
            @NonNull java.util.function.Predicate<WindowMetrics> windowMetricsPredicate) {
        return new SplitPairRule.Builder(activitiesPairPredicate, activityIntentPairPredicate,
                windowMetricsPredicate);
    }

    public static TestActivity startActivityAndVerifyNotSplit(
            @NonNull Activity activityLaunchingFrom) {
        final String secondActivityId = "secondActivityId";
        // Launch second activity
        startActivityFromActivity(activityLaunchingFrom, TestActivityWithId.class,
                secondActivityId);
        // Verify both activities are in the correct lifecycle state
        waitAndAssertResumed(secondActivityId);
        assertFalse(isActivityResumed(activityLaunchingFrom));
        TestActivity secondActivity = getResumedActivityById(secondActivityId);
        // Verify the second activity is not split with the first
        verifyFillsTask(secondActivity);
        return secondActivity;
    }

    public static Activity startActivityAndVerifySplit(@NonNull Activity activityLaunchingFrom,
            @NonNull Activity expectedPrimaryActivity, @NonNull Class secondActivityClass,
            @NonNull SplitPairRule splitPairRule, @NonNull String secondaryActivityId,
            int expectedCallbackCount,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) {
        // Set the expected callback count
        splitInfoConsumer.setCount(expectedCallbackCount);

        // Start second activity
        startActivityFromActivity(activityLaunchingFrom, secondActivityClass, secondaryActivityId);

        // A split info callback should occur after the new activity is launched because the split
        // states have changed.
        List<SplitInfo> activeSplitStates = null;
        try {
            activeSplitStates = splitInfoConsumer.waitAndGet();
        } catch (InterruptedException e) {
            fail("startActivityAndVerifySplit() InterruptedException");
        }
        if (activeSplitStates == null) {
            fail("Didn't receive updated split info");
        }

        // Wait for secondary activity to be resumed and verify that the newly sent split info
        // contains the secondary activity.
        waitAndAssertResumed(secondaryActivityId);
        final Activity secondaryActivity = getResumedActivityById(secondaryActivityId);
        assertSplitInfoTopSplitIsCorrect(activeSplitStates, expectedPrimaryActivity,
                secondaryActivity);

        assertValidSplit(expectedPrimaryActivity, secondaryActivity, splitPairRule);

        // Return second activity for easy access in calling method
        return secondaryActivity;
    }

    public static void startActivityAndVerifyNoCallback(@NonNull Activity activityLaunchingFrom,
            @NonNull Class secondActivityClass, @NonNull String secondaryActivityId,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) throws Exception {
        // We expect the actual count to be 0. Set to 1 to trigger the timeout and verify no calls.
        splitInfoConsumer.setCount(1);

        // Start second activity
        startActivityFromActivity(activityLaunchingFrom, secondActivityClass, secondaryActivityId);

        // A split info callback should occur after the new activity is launched because the split
        // states have changed.
        List<SplitInfo> activeSplitStates = splitInfoConsumer.waitAndGet();
        assertNull("Received SplitInfo value but did not expect none.", activeSplitStates);
    }

    public static Activity startActivityAndVerifySplit(@NonNull Activity primaryActivity,
            @NonNull Class secondActivityClass, @NonNull SplitPairRule splitPairRule,
            @NonNull String secondActivityId, int expectedCallbackCount,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) {
        return startActivityAndVerifySplit(primaryActivity /* activityLaunchingFrom */,
                primaryActivity, secondActivityClass, splitPairRule, secondActivityId,
                expectedCallbackCount, splitInfoConsumer);
    }

    public static Activity startActivityAndVerifySplit(@NonNull Activity primaryActivity,
            @NonNull Class secondActivityClass, @NonNull SplitPairRule splitPairRule,
            @NonNull String secondActivityId,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) {
        return startActivityAndVerifySplit(primaryActivity, secondActivityClass, splitPairRule,
                secondActivityId, 1 /* expectedCallbackCount */, splitInfoConsumer);
    }

    /**
     * Attempts to start an activity from a different UID into a split, verifies that a new split
     * is active.
     */
    public static void startActivityCrossUidInSplit(@NonNull Activity primaryActivity,
            @NonNull ComponentName secondActivityComponent, @NonNull SplitPairRule splitPairRule,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer,
            @NonNull String secondActivityId, boolean verifySplitState) {
        startActivityFromActivity(primaryActivity, secondActivityComponent, secondActivityId,
                Bundle.EMPTY);
        if (!verifySplitState) {
            return;
        }

        // Get updated split info
        splitInfoConsumer.setCount(1);
        List<SplitInfo> activeSplitStates = null;
        try {
            activeSplitStates = splitInfoConsumer.waitAndGet();
        } catch (InterruptedException e) {
            fail("startActivityCrossUidInSplit() InterruptedException");
        }
        assertNotNull(activeSplitStates);
        assertFalse(activeSplitStates.isEmpty());
        // Verify that the primary activity is on top of the primary stack
        SplitInfo topSplit = activeSplitStates.get(activeSplitStates.size() - 1);
        List<Activity> primaryStackActivities = topSplit.getPrimaryActivityStack()
                .getActivities();
        assertEquals(primaryActivity,
                primaryStackActivities.get(primaryStackActivities.size() - 1));
        // Verify that the secondary stack is reported as empty to developers
        assertTrue(topSplit.getSecondaryActivityStack().getActivities().isEmpty());

        assertValidSplit(primaryActivity, null /* secondaryActivity */,
                splitPairRule);
    }

    /**
     * Attempts to start an activity from a different UID into a split, verifies that activity
     * did not start on splitContainer successfully and no new split is active.
     */
    public static void startActivityCrossUidInSplit_expectFail(@NonNull Activity primaryActivity,
            @NonNull ComponentName secondActivityComponent,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) {
        startActivityFromActivity(primaryActivity, secondActivityComponent, "secondActivityId",
                    Bundle.EMPTY);

        // No split should be active, primary activity should be covered by the new one.
        assertNoSplit(primaryActivity, splitInfoConsumer);
    }

    /**
     * Asserts that there is no split with the provided primary activity.
     */
    public static void assertNoSplit(@NonNull Activity primaryActivity,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) {
        waitForVisible(primaryActivity, false /* visible */);
        List<SplitInfo> activeSplitStates = splitInfoConsumer.getLastReportedValue();
        assertTrue(activeSplitStates == null || activeSplitStates.isEmpty());
    }

    @Nullable
    public static Activity getSecondActivity(@Nullable List<SplitInfo> activeSplitStates,
            @NonNull Activity primaryActivity, @NonNull String secondaryClassId) {
        if (activeSplitStates == null) {
            Log.d(TAG, "Null split states");
            return null;
        }
        Log.d(TAG, "Active split states: " + activeSplitStates);
        for (SplitInfo splitInfo : activeSplitStates) {
            // Find the split info whose top activity in the primary container is the primary
            // activity we are looking for
            Activity primaryContainerTopActivity = getPrimaryStackTopActivity(splitInfo);
            if (primaryActivity.equals(primaryContainerTopActivity)) {
                Activity secondActivity = getSecondaryStackTopActivity(splitInfo);
                // See if this activity is the secondary activity we expect
                if (secondActivity != null && secondActivity instanceof TestActivityWithId
                        && secondaryClassId.equals(((TestActivityWithId) secondActivity).getId())) {
                    return secondActivity;
                }
            }
        }
        Log.d(TAG, "Second activity was not found: " + secondaryClassId);
        return null;
    }

    /**
     * Waits for and verifies a valid split. Can accept a null secondary activity if it belongs to
     * a different process, in which case it will only verify the primary one.
     */
    public static void assertValidSplit(@NonNull Activity primaryActivity,
            @Nullable Activity secondaryActivity, SplitRule splitRule) {
        waitAndAssertResumed(secondaryActivity != null
                ? Arrays.asList(primaryActivity, secondaryActivity)
                : Collections.singletonList(primaryActivity));

        // Compute the layout direction
        int layoutDir = splitRule.getLayoutDirection();
        if (layoutDir == LayoutDirection.LOCALE) {
            layoutDir = primaryActivity.getResources().getConfiguration().getLayoutDirection();
        }

        // Compute the expected bounds
        final float splitRatio = splitRule.getSplitRatio();
        final Rect parentBounds = getMaximumActivityBounds(primaryActivity);
        final Rect expectedPrimaryActivityBounds = new Rect();
        final Rect expectedSecondaryActivityBounds = new Rect();
        getExpectedPrimaryAndSecondaryBounds(layoutDir, splitRatio, parentBounds,
                expectedPrimaryActivityBounds, expectedSecondaryActivityBounds);

        final ActivityEmbeddingComponent activityEmbeddingComponent = getWindowExtensions()
                .getActivityEmbeddingComponent();

        // Verify that both activities are embedded and that the bounds are correct
        assertTrue(activityEmbeddingComponent.isActivityEmbedded(primaryActivity));
        assertEquals(expectedPrimaryActivityBounds, getActivityBounds(primaryActivity));
        if (secondaryActivity != null) {
            assertTrue(activityEmbeddingComponent.isActivityEmbedded(secondaryActivity));
            assertEquals(expectedSecondaryActivityBounds, getActivityBounds(secondaryActivity));
        }
    }

    public static void verifyFillsTask(Activity activity) {
        assertEquals(getMaximumActivityBounds(activity), getActivityBounds(activity));
    }

    public static void waitForFillsTask(Activity activity) {
        PollingCheck.waitFor(WAIT_FOR_LIFECYCLE_TIMEOUT_MS, () -> getActivityBounds(activity)
                .equals(getMaximumActivityBounds(activity)));
    }

    private static boolean waitForResumed(
            @NonNull List<Activity> activityList) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_FOR_LIFECYCLE_TIMEOUT_MS) {
            boolean allActivitiesResumed = true;
            for (Activity activity : activityList) {
                allActivitiesResumed &= WindowManagerJetpackTestBase.isActivityResumed(activity);
                if (!allActivitiesResumed) {
                    break;
                }
            }
            if (allActivitiesResumed) {
                return true;
            }
        }
        return false;
    }

    private static boolean waitForResumed(@NonNull String activityId) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_FOR_LIFECYCLE_TIMEOUT_MS) {
            if (getResumedActivityById(activityId) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean waitForResumed(@NonNull Activity activity) {
        return waitForResumed(Arrays.asList(activity));
    }

    public static void waitAndAssertResumed(@NonNull String activityId) {
        assertTrue("Activity with id=" + activityId + " should be resumed",
                waitForResumed(activityId));
    }

    public static void waitAndAssertResumed(@NonNull Activity activity) {
        assertTrue(activity + " should be resumed", waitForResumed(activity));
    }

    public static void waitAndAssertResumed(@NonNull List<Activity> activityList) {
        assertTrue("All activities in this list should be resumed:" + activityList,
                waitForResumed(activityList));
    }

    public static void waitAndAssertNotResumed(@NonNull String activityId) {
        assertFalse("Activity with id=" + activityId + " should not be resumed",
                waitForResumed(activityId));
    }

    public static boolean waitForVisible(@NonNull Activity activity, boolean visible) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_FOR_LIFECYCLE_TIMEOUT_MS) {
            if (WindowManagerJetpackTestBase.isActivityVisible(activity) == visible) {
                return true;
            }
        }
        return false;
    }

    public static void waitAndAssertVisible(@NonNull Activity activity) {
        assertTrue(activity + " should be visible",
                waitForVisible(activity, true /* visible */));
    }

    public static void waitAndAssertNotVisible(@NonNull Activity activity) {
        assertTrue(activity + " should not be visible",
                waitForVisible(activity, false /* visible */));
    }

    private static boolean waitForFinishing(@NonNull Activity activity) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_FOR_LIFECYCLE_TIMEOUT_MS) {
            if (activity.isFinishing()) {
                return true;
            }
        }
        return activity.isFinishing();
    }

    public static void waitAndAssertFinishing(@NonNull Activity activity) {
        assertTrue(activity + " should be finishing", waitForFinishing(activity));
    }

    @Nullable
    public static Activity getPrimaryStackTopActivity(SplitInfo splitInfo) {
        List<Activity> primaryActivityStack = splitInfo.getPrimaryActivityStack().getActivities();
        if (primaryActivityStack.isEmpty()) {
            return null;
        }
        return primaryActivityStack.get(primaryActivityStack.size() - 1);
    }

    @Nullable
    public static Activity getSecondaryStackTopActivity(SplitInfo splitInfo) {
        List<Activity> secondaryActivityStack = splitInfo.getSecondaryActivityStack()
                .getActivities();
        if (secondaryActivityStack.isEmpty()) {
            return null;
        }
        return secondaryActivityStack.get(secondaryActivityStack.size() - 1);
    }

    public static void getExpectedPrimaryAndSecondaryBounds(int layoutDir, float splitRatio,
            @NonNull Rect inParentBounds, @NonNull Rect outPrimaryActivityBounds,
            @NonNull Rect outSecondaryActivityBounds) {
        assertTrue(layoutDir == LayoutDirection.LTR || layoutDir == LayoutDirection.RTL);

        // Normalize the split ratio so that parent left + (parent width * split ratio) is always
        // the position of the split divider in the parent.
        if (layoutDir == LayoutDirection.RTL) {
            splitRatio = 1 - splitRatio;
        }

        // Create the left and right container bounds
        final Rect leftContainerBounds = new Rect(inParentBounds.left, inParentBounds.top,
                (int) (inParentBounds.left + inParentBounds.width() * splitRatio),
                inParentBounds.bottom);
        final Rect rightContainerBounds = new Rect(
                (int) (inParentBounds.left + inParentBounds.width() * splitRatio),
                inParentBounds.top, inParentBounds.right, inParentBounds.bottom);

        // Assign the primary and secondary bounds depending on layout direction
        if (layoutDir == LayoutDirection.LTR) {
            /*******************|*********************
             * primary activity | secondary activity *
             *******************|*********************/
            outPrimaryActivityBounds.set(leftContainerBounds);
            outSecondaryActivityBounds.set(rightContainerBounds);
        } else {
            /*********************|*******************
             * secondary activity | primary activity *
             *********************|*******************/
            outPrimaryActivityBounds.set(rightContainerBounds);
            outSecondaryActivityBounds.set(leftContainerBounds);
        }
    }

    public static void assumeActivityEmbeddingSupportedDevice() {
        assumeExtensionSupportedDevice();
        assumeTrue("Device does not support ActivityEmbedding",
                Objects.requireNonNull(getWindowExtensions())
                        .getActivityEmbeddingComponent() != null);
    }

    private static void assertSplitInfoTopSplitIsCorrect(@NonNull List<SplitInfo> splitInfoList,
            @NonNull Activity primaryActivity, @NonNull Activity secondaryActivity) {
        assertFalse("Split info callback should not be empty", splitInfoList.isEmpty());
        final SplitInfo topSplit = splitInfoList.get(splitInfoList.size() - 1);
        assertEquals("Expect primary activity to match the top of the primary stack",
                primaryActivity, getPrimaryStackTopActivity(topSplit));
        assertEquals("Expect secondary activity to match the top of the secondary stack",
                secondaryActivity, getSecondaryStackTopActivity(topSplit));
    }
}
