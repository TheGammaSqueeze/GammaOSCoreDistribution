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
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.UNEVEN_CONTAINERS_DEFAULT_SPLIT_RATIO;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assertValidSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilderWithJava8Predicate;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotVisible;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitForFillsTask;
import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.platform.test.annotations.FlakyTest;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.LayoutDirection;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Set;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * split bounds.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingBoundsTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingBoundsTests extends ActivityEmbeddingTestBase {

    /**
     * Tests that when two activities are in a split and the parent bounds shrink such that
     * they can no longer support split activities, then the activities become stacked.
     */
    @Test
    public void testParentWindowMetricsPredicate() {
        // Launch primary activity
        final Activity primaryActivity = startActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Set split pair rule such that if the parent bounds is any smaller than it is now, then
        // the parent cannot support a split.
        final int originalTaskWidth = getTaskWidth();
        final int originalTaskHeight = getTaskHeight();
        final SplitPairRule splitPairRule = createSplitPairRuleBuilderWithJava8Predicate(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> parentWindowMetrics.getBounds().width() >= originalTaskWidth
                        && parentWindowMetrics.getBounds().height() >= originalTaskHeight)
                .setSplitRatio(DEFAULT_SPLIT_RATIO).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the secondary activity
        final String secondaryActivityId = "secondaryActivityId";
        final TestActivity secondaryActivity = (TestActivity) startActivityAndVerifySplit(
                primaryActivity, TestActivityWithId.class, splitPairRule, secondaryActivityId,
                mSplitInfoConsumer);

        // Resize the display multiple times to verify that the activities are correctly split or
        // stacked depending on the parent bounds. Resizing multiple times simulates a foldable
        // display is that folded and unfolded multiple times while running the same app.
        final int numTimesToResize = 2;
        final Size originalDisplaySize = mReportedDisplayMetrics.getSize();
        for (int i = 0; i < numTimesToResize; i++) {
            // Shrink the display by 10% to make the activities stacked
            mReportedDisplayMetrics.setSize(new Size((int) (originalDisplaySize.getWidth() * 0.9),
                    (int) (originalDisplaySize.getHeight() * 0.9)));
            waitForFillsTask(secondaryActivity);
            waitAndAssertNotVisible(primaryActivity);

            // Return the display to its original size and verify that the activities are split
            secondaryActivity.resetBoundsChangeCounter();
            mReportedDisplayMetrics.setSize(originalDisplaySize);
            assertTrue(secondaryActivity.waitForBoundsChange());
            assertValidSplit(primaryActivity, secondaryActivity, splitPairRule);
        }
    }

    /**
     * Tests that the activity bounds for activities in a split match the LTR layout direction
     * provided in the {@link SplitPairRule}.
     */
    @Test
    public void testLayoutDirection_LTR() {
        // Create a split pair rule with layout direction LTR and a split ratio that results in
        // uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(LayoutDirection.LTR);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is LTR, which is
        // checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplit(primaryActivity, TestActivityWithId.class, splitPairRule,
                "secondaryActivityId", mSplitInfoConsumer);
    }

    /**
     * Tests that the activity bounds for activities in a split match the RTL layout direction
     * provided in the {@link SplitPairRule}.
     */
    @Test
    public void testLayoutDirection_RTL() {
        // Create a split pair rule with layout direction RTL and a split ratio that results in
        // uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(LayoutDirection.RTL);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is RTL, which is
        // checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplit(primaryActivity, TestActivityWithId.class, splitPairRule,
                "secondaryActivityId", mSplitInfoConsumer);
    }

    /**
     * Tests that the activity bounds for activities in a split match the Locale layout direction
     * provided in the {@link SplitPairRule}.
     */
    @Test
    public void testLayoutDirection_Locale() {
        // Create a split pair rule with layout direction LOCALE and a split ratio that results in
        // uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(LayoutDirection.LOCALE);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is the device locale,
        // which is checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplit(primaryActivity, TestActivityWithId.class, splitPairRule,
                "secondaryActivityId", mSplitInfoConsumer);
    }

    /**
     * Tests that when two activities enter a split, then their split ratio matches what is in their
     * {@link SplitPairRule}, and is not assumed to be 0.5 or match the split ratio of the previous
     * top-most activity split.
     */
    @FlakyTest(bugId = 213322133)
    @Test
    public void testSplitRatio() {
        final String activityAId = "activityA";
        final String activityBId = "activityB";
        final String activityCId = "activityC";
        final float activityABSplitRatio = 0.37f;
        final float activityBCSplitRatio = 0.85f;

        // Create a split rule for activity A and activity B where the split ratio is 0.37.
        final SplitPairRule splitPairRuleAB = createSplitPairRuleBuilderWithJava8Predicate(
                activityActivityPair -> false /* activityPairPredicate */,
                activityIntentPair -> matchesActivityIntentPair(activityIntentPair, activityAId,
                        activityBId) /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(activityABSplitRatio).build();

        // Create a split rule for activity B and activity C where the split ratio is 0.65.
        final SplitPairRule splitPairRuleBC = createSplitPairRuleBuilderWithJava8Predicate(
                activityActivityPair -> false /* activityPairPredicate */,
                activityIntentPair -> matchesActivityIntentPair(activityIntentPair, activityBId,
                        activityCId) /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(activityBCSplitRatio).build();

        // Register the two split pair rules
        mActivityEmbeddingComponent.setEmbeddingRules(Set.of(splitPairRuleAB, splitPairRuleBC));

        // Launch the activity A and B split and verify that the split ratio is 0.37 in
        // {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity activityA = startActivityNewTask(TestActivityWithId.class, activityAId);
        Activity activityB = startActivityAndVerifySplit(activityA, TestActivityWithId.class,
                splitPairRuleAB, activityBId, mSplitInfoConsumer);

        // Launch the activity B and C split and verify that the split ratio is 0.65 in
        // {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity activityC = startActivityAndVerifySplit(activityB, TestActivityWithId.class,
                splitPairRuleBC, activityCId, mSplitInfoConsumer);

        // Finish activity C so that activity A and B are in a split again. Verify that the split
        // ratio returns to 0.37 in {@link ActivityEmbeddingUtil#assertValidSplit}.
        activityC.finish();
        assertValidSplit(activityA, activityB, splitPairRuleAB);
    }

    private SplitPairRule createUnevenWidthSplitPairRule(int layoutDir) {
        return createSplitPairRuleBuilderWithJava8Predicate(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(UNEVEN_CONTAINERS_DEFAULT_SPLIT_RATIO)
                .setLayoutDirection(layoutDir).build();
    }

    private boolean matchesActivityIntentPair(@NonNull Pair<Activity, Intent> activityIntentPair,
            @NonNull String primaryActivityId, @NonNull String secondaryActivityId) {
        if (!(activityIntentPair.first instanceof TestActivityWithId)) {
            return false;
        }
        return primaryActivityId.equals(((TestActivityWithId) activityIntentPair.first).getId())
                && secondaryActivityId.equals(activityIntentPair.second.getStringExtra(
                KEY_ACTIVITY_ID));
    }
}
