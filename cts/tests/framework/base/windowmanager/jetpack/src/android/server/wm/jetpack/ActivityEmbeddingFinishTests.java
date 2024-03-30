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

package android.server.wm.jetpack;


import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.DEFAULT_SPLIT_RATIO;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifyNotSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.verifyFillsTask;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertFinishing;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;

import static androidx.window.extensions.embedding.SplitRule.FINISH_ADJACENT;
import static androidx.window.extensions.embedding.SplitRule.FINISH_ALWAYS;
import static androidx.window.extensions.embedding.SplitRule.FINISH_NEVER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;
import android.view.WindowMetrics;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * finish scenarios.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingFinishTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingFinishTests extends ActivityEmbeddingTestBase {

    /**
     * Tests that finishing the primary activity results in the secondary activity resizing to fill
     * the task.
     */
    @Test
    public void testFinishPrimary() throws InterruptedException {
        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);
        TestActivity secondaryActivity = (TestActivity) startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule, "secondaryActivity", mSplitInfoConsumer);

        // Finishing the primary activity should cause the secondary activity to resize to fill the
        // task.
        primaryActivity.finish();
        assertTrue(secondaryActivity.waitForBoundsChange());
        assertEquals(getMaximumActivityBounds(secondaryActivity),
                getActivityBounds(secondaryActivity));

        // Verify that there are no split states
        List<SplitInfo> splitInfoList = mSplitInfoConsumer.waitAndGet();
        assertTrue(splitInfoList.isEmpty());
    }

    /**
     * Tests that finishing the secondary activity results in the primary activity resizing to fill
     * the task.
     */
    @Test
    public void testFinishSecondary() throws InterruptedException {
        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        TestActivity primaryActivity = startActivityNewTask(TestActivityWithId.class);
        TestActivity secondaryActivity = (TestActivity) startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule, "secondaryActivity", mSplitInfoConsumer);

        // Need to reset primary activity bounds change counter because entering the split already
        // triggered a bounds change.
        primaryActivity.resetBoundsChangeCounter();

        // Finishing the secondary activity should cause the primary activity to resize to fill the
        // task.
        secondaryActivity.finish();
        assertTrue(primaryActivity.waitForBoundsChange());
        assertEquals(getMaximumActivityBounds(primaryActivity),
                getActivityBounds(primaryActivity));

        // Verify that there are no split states
        List<SplitInfo> splitInfoList = mSplitInfoConsumer.waitAndGet();
        assertTrue(splitInfoList.isEmpty());
    }

    /**
     * Tests that when finishPrimaryWithSecondary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_NEVER} when the activities are
     * stacked, then finishing the secondary activity does not cause the primary activity to finish.
     */
    @Test
    public void testFinishPrimaryWithSecondary_ActivitiesStacked_FinishNever() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .preventSplitActivities().setFinishPrimaryWithSecondary(FINISH_NEVER).start();
        // Verify the paired finish behavior
        activityPair.second.finish();
        waitAndAssertResumed(activityPair.first);
    }

    /**
     * Tests that when finishPrimaryWithSecondary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ADJACENT} when the activities
     * are stacked, then finishing the secondary activity does not cause the primary activity to
     * finish because the activities were not adjacent.
     */
    @Test
    public void testFinishPrimaryWithSecondary_ActivitiesStacked_FinishAdjacent() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .preventSplitActivities().setFinishPrimaryWithSecondary(FINISH_ADJACENT).start();
        // Verify the paired finish behavior
        activityPair.second.finish();
        waitAndAssertResumed(activityPair.first);
    }

    /**
     * Tests that when finishPrimaryWithSecondary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ALWAYS} when the activities are
     * stacked, then finishing the secondary activity causes the primary activity to finish even
     * though the activities are stacked.
     */
    @Test
    public void testFinishPrimaryWithSecondary_ActivitiesStacked_FinishAlways() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .preventSplitActivities().setFinishPrimaryWithSecondary(FINISH_ALWAYS).start();
        // Verify the paired finish behavior
        activityPair.second.finish();
        waitAndAssertFinishing(activityPair.first);
    }

    /**
     * Tests that when finishPrimaryWithSecondary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_NEVER} when the activities are
     * split, then finishing the secondary activity does not cause the primary activity to finish.
     */
    @Test
    public void testFinishPrimaryWithSecondary_ActivitiesSplit_FinishNever() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .setFinishPrimaryWithSecondary(FINISH_NEVER).start();
        // Verify the paired finish behavior
        activityPair.first.resetBoundsChangeCounter();
        activityPair.second.finish();
        assertTrue(activityPair.first.waitForBoundsChange());
        verifyFillsTask(activityPair.first);
    }

    /**
     * Tests that when finishPrimaryWithSecondary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ADJACENT} when the activities
     * are split, then finishing the secondary activity causes the primary activity to finish
     * because the activities were in a split.
     */
    @Test
    public void testFinishPrimaryWithSecondary_ActivitiesSplit_FinishAdjacent() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .setFinishPrimaryWithSecondary(FINISH_ADJACENT).start();
        // Verify the paired finish behavior
        activityPair.second.finish();
        waitAndAssertFinishing(activityPair.first);
    }

    /**
     * Tests that when finishPrimaryWithSecondary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ALWAYS} when the activities are
     * split, then finishing the secondary activity causes the primary activity to finish.
     */
    @Test
    public void testFinishPrimaryWithSecondary_ActivitiesSplit_FinishAlways() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .setFinishPrimaryWithSecondary(FINISH_ALWAYS).start();
        // Verify the paired finish behavior
        activityPair.second.finish();
        waitAndAssertFinishing(activityPair.first);
    }

    /**
     * Tests that when finishSecondaryWithPrimary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_NEVER} when the activities are
     * stacked, then finishing the primary activity does not cause the secondary activity to finish.
     */
    @Test
    public void testFinishSecondaryWithPrimary_ActivitiesStacked_FinishNever() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .preventSplitActivities().setFinishSecondaryWithPrimary(FINISH_NEVER).start();
        // Verify the paired finish behavior
        activityPair.first.finish();
        waitAndAssertResumed(activityPair.second);
    }

    /**
     * Tests that when finishSecondaryWithPrimary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ADJACENT} when the activities
     * are stacked, then finishing the primary activity does not cause the secondary activity to
     * finish because the activities were not adjacent.
     */
    @Test
    public void testFinishSecondaryWithPrimary_ActivitiesStacked_FinishAdjacent() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .preventSplitActivities().setFinishSecondaryWithPrimary(FINISH_ADJACENT).start();
        // Verify the paired finish behavior
        activityPair.first.finish();
        waitAndAssertResumed(activityPair.second);
    }

    /**
     * Tests that when finishSecondaryWithPrimary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ALWAYS} when the activities are
     * stacked, then finishing the primary activity causes the secondary activity to finish even
     * though the activities are stacked.
     */
    @Test
    public void testFinishSecondaryWithPrimary_ActivitiesStacked_FinishAlways() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .preventSplitActivities().setFinishSecondaryWithPrimary(FINISH_ALWAYS).start();
        // Verify the paired finish behavior
        activityPair.first.finish();
        waitAndAssertFinishing(activityPair.second);
    }

    /**
     * Tests that when finishSecondaryWithPrimary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_NEVER} when the activities are
     * split, then finishing the primary activity does not cause the secondary activity to finish.
     */
    @Test
    public void testFinishSecondaryWithPrimary_ActivitiesSplit_FinishNever() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .setFinishSecondaryWithPrimary(FINISH_NEVER).start();
        // Verify the paired finish behavior
        activityPair.second.resetBoundsChangeCounter();
        activityPair.first.finish();
        assertTrue(activityPair.second.waitForBoundsChange());
        verifyFillsTask(activityPair.second);
    }

    /**
     * Tests that when finishSecondaryWithPrimary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ADJACENT} when the activities
     * are split, then finishing the primary activity causes the secondary activity to finish
     * because the activities were in a split.
     */
    @Test
    public void testFinishSecondaryWithPrimary_ActivitiesSplit_FinishAdjacent() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .setFinishSecondaryWithPrimary(FINISH_ADJACENT).start();
        // Verify the paired finish behavior
        activityPair.first.finish();
        waitAndAssertFinishing(activityPair.second);
    }

    /**
     * Tests that when finishSecondaryWithPrimary is set to
     * {@link androidx.window.extensions.embedding.SplitRule.FINISH_ALWAYS} when the activities are
     * split, then finishing the primary activity causes the secondary activity to finish.
     */
    @Test
    public void testFinishSecondaryWithPrimary_ActivitiesSplit_FinishAlways() {
        // Setup test scenario
        Pair<TestActivity, TestActivity> activityPair = new PairedFinishBehaviorScenario()
                .setFinishSecondaryWithPrimary(FINISH_ALWAYS).start();
        // Verify the paired finish behavior
        activityPair.first.finish();
        waitAndAssertFinishing(activityPair.second);
    }

    /**
     * Utility class to set up a paired finish behavior test. The class makes it easy to specify
     * whether the two test activities should be stacked or split and set the split pair rules
     * with a specific paired finish behavior.
     */
    private class PairedFinishBehaviorScenario {
        // Set the unset value to never be equal to any of the possible values
        private static final int UNSET_PAIRED_FINISH_BEHAVIOR = FINISH_NEVER + FINISH_ADJACENT
                + FINISH_ALWAYS;
        private int mFinishPrimaryWithSecondary = UNSET_PAIRED_FINISH_BEHAVIOR;
        private int mFinishSecondaryWithPrimary = UNSET_PAIRED_FINISH_BEHAVIOR;
        private boolean mShouldPreventSideBySideActivities;

        public PairedFinishBehaviorScenario preventSplitActivities() {
            mShouldPreventSideBySideActivities = true;
            return this;
        }

        public PairedFinishBehaviorScenario setFinishPrimaryWithSecondary(
                int finishPrimaryWithSecondary) {
            mFinishPrimaryWithSecondary = finishPrimaryWithSecondary;
            return this;
        }

        public PairedFinishBehaviorScenario setFinishSecondaryWithPrimary(
                int finishSecondaryWithPrimary) {
            mFinishSecondaryWithPrimary = finishSecondaryWithPrimary;
            return this;
        }

        public Pair<TestActivity, TestActivity> start() {
            // Set the split pair rule
            Predicate<WindowMetrics> parentWindowMetricsPredicate =
                    mShouldPreventSideBySideActivities
                            ? windowMetrics -> false : windowMetrics -> true;
            SplitPairRule.Builder splitPairRuleBuilder = new SplitPairRule.Builder(
                    activityActivityPair -> true /* any two activities can be split */,
                    activityIntentPair -> true /* any intent will put an activity into a split */,
                    parentWindowMetricsPredicate).setSplitRatio(DEFAULT_SPLIT_RATIO);
            // Only set paired finish behavior if an explicit value is set, otherwise use the
            // default library implementation.
            if (mFinishPrimaryWithSecondary != UNSET_PAIRED_FINISH_BEHAVIOR) {
                splitPairRuleBuilder.setFinishPrimaryWithSecondary(mFinishPrimaryWithSecondary);
            }
            if (mFinishSecondaryWithPrimary != UNSET_PAIRED_FINISH_BEHAVIOR) {
                splitPairRuleBuilder.setFinishSecondaryWithPrimary(mFinishSecondaryWithPrimary);
            }
            final SplitPairRule splitPairRule = splitPairRuleBuilder.build();
            mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

            // Launch the two activities
            TestActivity primaryActivity = startActivityNewTask(
                    TestConfigChangeHandlingActivity.class);
            TestActivity secondaryActivity;
            if (mShouldPreventSideBySideActivities) {
                secondaryActivity = startActivityAndVerifyNotSplit(primaryActivity);
            } else {
                secondaryActivity = (TestActivity) startActivityAndVerifySplit(primaryActivity,
                        TestActivityWithId.class, splitPairRule, "secondaryActivity",
                        mSplitInfoConsumer);
            }
            return new Pair<>(primaryActivity, secondaryActivity);
        }
    }
}
