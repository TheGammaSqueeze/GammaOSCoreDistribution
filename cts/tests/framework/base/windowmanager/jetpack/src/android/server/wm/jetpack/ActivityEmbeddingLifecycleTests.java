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

package android.server.wm.jetpack;

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRuleBuilderWithPrimaryActivityClass;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRuleWithPrimaryActivityClass;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotVisible;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.ExtensionUtil.getWindowExtensions;
import static android.server.wm.lifecycle.LifecycleConstants.ON_CREATE;
import static android.server.wm.lifecycle.LifecycleConstants.ON_DESTROY;
import static android.server.wm.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.lifecycle.TransitionVerifier.checkOrder;
import static android.server.wm.lifecycle.TransitionVerifier.transition;

import static androidx.window.extensions.embedding.SplitRule.FINISH_ALWAYS;
import static androidx.window.extensions.embedding.SplitRule.FINISH_NEVER;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.server.wm.jetpack.utils.JavaConsumerAdapter;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestActivityWithId2;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.server.wm.jetpack.utils.TestValueCountConsumer;
import android.server.wm.lifecycle.EventLog;
import android.server.wm.lifecycle.EventLog.EventLogClient;
import android.server.wm.lifecycle.EventTracker;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests the invocation
 * and order of lifecycle callbacks.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingLifecycleTests
 */
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingLifecycleTests extends ActivityEmbeddingTestBase {
    private static final String TEST_OWNER = "TEST_OWNER";
    private static final String ON_SPLIT_STATES_UPDATED = "ON_SPLIT_STATES_UPDATED";

    private EventLogClient mEventLogClient;
    private EventLog mEventLog;
    private EventTracker mLifecycleTracker;
    private LifecycleCallbacks mLifecycleCallbacks;

    @Override
    public void setUp() {
        super.setUp();
        mSplitInfoConsumer = new SplitInfoLifecycleConsumer<>();
        if (getWindowExtensions().getVendorApiLevel() >= 2) {
            mActivityEmbeddingComponent.setSplitInfoCallback(mSplitInfoConsumer);
        } else {
            mActivityEmbeddingComponent.setSplitInfoCallback(
                    new JavaConsumerAdapter<>(mSplitInfoConsumer)
            );
        }

        mEventLogClient = EventLogClient.create(TEST_OWNER, mInstrumentation.getTargetContext(),
                Uri.parse("content://android.server.wm.jetpack.logprovider"));

        // Log transitions for all activities that belong to this app.
        mEventLog = new EventLog();
        mEventLog.clear();

        // Track transitions and allow waiting for pending activity states.
        mLifecycleTracker = new EventTracker(mEventLog);
        mLifecycleCallbacks = new LifecycleCallbacks();
        mApplication.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
    }

    @Override
    public void tearDown() {
        super.tearDown();
        mApplication.unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
        if (mEventLogClient != null) {
            mEventLogClient.close();
        }
    }

    /**
     * Tests launching activities to the side from the primary activity, each next one replacing the
     * previous one.
     */
    @Test
    public void testSecondaryActivityLaunch_replacing() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, true /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        startActivityAndVerifySplit(primaryActivity, TestActivityWithId.class, splitPairRule,
                "secondaryActivity1" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_CREATE),
                transition(TestActivityWithId.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Init split states", mLifecycleTracker.waitForConditionWithTimeout(() ->
                checkOrder(mEventLog, expected)));
        mEventLog.clear();

        // Launch a replacing secondary activity
        Activity secondaryActivity2 = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected2 = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestActivityWithId2.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Replace secondary container activity",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected2)));
        waitAndAssertResumed(primaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        // Destroy may happen after the secondaryActivity2 becomes visible and IDLE.
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
    }

    /**
     * Tests launching activities to the side from the primary activity, each next one launching on
     * top of the previous one.
     */
    @Test
    public void testSecondaryActivityLaunch_nonReplacing() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity1 = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_CREATE),
                transition(TestActivityWithId.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Init split states", mLifecycleTracker.waitForConditionWithTimeout(() ->
                checkOrder(mEventLog, expected)));
        mEventLog.clear();

        // Launch a secondary activity on top
        Activity secondaryActivity2 = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected2 = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestActivityWithId2.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Launch second secondary activity",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected2)));
        waitAndAssertResumed(primaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        waitAndAssertNotVisible(secondaryActivity1);
    }

    /**
     * Tests launching several layers of secondary activities.
     */
    @Test
    public void testSecondaryActivityLaunch_multiSplit() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_CREATE),
                transition(TestActivityWithId.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Init split states", mLifecycleTracker.waitForConditionWithTimeout(() ->
                checkOrder(mEventLog, expected)));
        mEventLog.clear();

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplit(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        List<Pair<String, String>> expected2 = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_PAUSE),
                transition(TestActivityWithId2.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Launch second secondary activity to side",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected2)));
        waitAndAssertNotVisible(primaryActivity);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
    }

    /**
     * Tests finishing activities in split - finishing secondary activity only.
     */
    @Test
    public void testSplitFinish_secondaryOnly() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish secondary activity
        secondaryActivity.finish();
        waitAndAssertSplitStatesUpdated();
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertResumed(primaryActivity);
    }

    /**
     * Tests finishing activities in split - finishing secondary should trigger finishing of the
     * primary one.
     */
    @Test
    public void testSplitFinish_secondaryWithDependent() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_ALWAYS)
                .setFinishSecondaryWithPrimary(FINISH_ALWAYS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish secondary activity, should trigger finishing of the primary as well
        secondaryActivity.finish();
        List<Pair<String, String>> expected = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestConfigChangeHandlingActivity.class, ON_PAUSE));
        assertTrue("Finish secondary activity with dependents",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected)));
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestConfigChangeHandlingActivity.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing primary container only, the secondary should
     * remain.
     */
    @Test
    public void testSplitFinish_primaryOnly() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_NEVER)
                .setFinishSecondaryWithPrimary(FINISH_NEVER)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish primary activity
        primaryActivity.finish();
        waitAndAssertSplitStatesUpdated();
        waitAndAssertActivityOnDestroy(TestConfigChangeHandlingActivity.class);
        waitAndAssertResumed(secondaryActivity);
    }

    /**
     * Tests finishing activities in split - finishing primary container only, the secondary should
     * remain.
     */
    @Test
    public void testSplitFinish_primaryWithDependent() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_ALWAYS)
                .setFinishSecondaryWithPrimary(FINISH_ALWAYS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        startActivityAndVerifySplit(primaryActivity, TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish primary activity should trigger finishing of the secondary as well.
        primaryActivity.finish();
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_PAUSE),
                transition(TestActivityWithId.class, ON_PAUSE));
        assertTrue("Finish primary activity with dependents",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected)));
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestConfigChangeHandlingActivity.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing the last created container in multi-split.
     */
    @Test
    public void testSplitFinish_lastMultiSplit() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplit(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        mEventLog.clear();

        // Finish the last activity
        secondaryActivity2.finish();
        waitAndAssertSplitStatesUpdated();
        waitAndAssertActivityOnDestroy(TestActivityWithId2.class);
        waitAndAssertResumed(primaryActivity);
    }

    /**
     * Tests finishing activities in split - finishing a container in the middle of a multi-split.
     * There is no matching split rule for top and bottom containers, and they will overlap after
     * the one in the middle is finished.
     */
    @Test
    public void testSplitFinish_midMultiSplitOnly_noSplitRule() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplit(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        mEventLog.clear();

        // Finish the middle activity
        secondaryActivity.finish();
        waitAndAssertResumed(secondaryActivity2);
        waitAndAssertNotVisible(primaryActivity);
        List<Pair<String, String>> expected = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestConfigChangeHandlingActivity.class, ON_STOP));
        assertTrue("Finish middle activity in multi-split",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected)));
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing a container in the middle of a multi-split.
     * Even though there is a matching split rule for top and bottom containers, and they will still
     * overlap after the one in the middle is finished - the split rules are only applied when new
     * activities are started.
     */
    @Test
    public void testSplitFinish_midMultiSplitOnly_withSplitRule() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRule(false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        Activity secondaryActivity2 = startActivityAndVerifySplit(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        mEventLog.clear();

        // Finish the middle activity
        secondaryActivity.finish();
        waitAndAssertResumed(secondaryActivity2);
        waitAndAssertNotVisible(primaryActivity);
        List<Pair<String, String>> expected = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestConfigChangeHandlingActivity.class, ON_STOP));
        assertTrue("Finish middle activity in multi-split",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected)));
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing a container in the middle of a multi-split.
     */
    @Test
    public void testSplitFinish_midMultiSplitWithDependents() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        // Launch a secondary activity to side
        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_ALWAYS)
                .setFinishSecondaryWithPrimary(FINISH_ALWAYS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplit(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        mEventLog.clear();

        // Finish the middle activity
        secondaryActivity.finish();
        waitAndAssertResumed(primaryActivity);
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId2.class);
        waitAndAssertSplitStatesUpdated();
    }

    private void waitAndAssertActivityOnDestroy(Class<? extends Activity> activityClass) {
        mLifecycleTracker.waitAndAssertActivityCurrentState(activityClass, ON_DESTROY);
    }

    private void waitAndAssertSplitStatesUpdated() {
        assertTrue("Split state change must be observed",
                mLifecycleTracker.waitForConditionWithTimeout(() -> mEventLog.getLog().contains(
                        transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED))));
    }

    private final class LifecycleCallbacks implements
            Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            mEventLogClient.onCallback(ON_CREATE, activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            mEventLogClient.onCallback(ON_START, activity);
        }

        @Override
        public void onActivityResumed(Activity activity) {
            mEventLogClient.onCallback(ON_RESUME, activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            mEventLogClient.onCallback(ON_PAUSE, activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            mEventLogClient.onCallback(ON_STOP, activity);
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            mEventLogClient.onCallback(ON_DESTROY, activity);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }
    }

    private final class SplitInfoLifecycleConsumer<T> extends TestValueCountConsumer<T> {
        @Override
        public void accept(T value) {
            super.accept(value);
            mEventLogClient.onCallback(ON_SPLIT_STATES_UPDATED, TEST_OWNER);
        }
    }
}
