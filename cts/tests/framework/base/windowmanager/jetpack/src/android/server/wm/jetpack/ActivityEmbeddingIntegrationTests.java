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

import static android.server.wm.jetpack.signed.Components.SIGNED_EMBEDDING_ACTIVITY;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EMBEDDED_ACTIVITY_ID;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilderWithJava8Predicate;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifyNoCallback;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.ExtensionUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.utils.ExtensionUtil.assumeHasDisplayFeatures;
import static android.server.wm.jetpack.utils.ExtensionUtil.assumeVendorApiLevelAtLeast;
import static android.server.wm.jetpack.utils.ExtensionUtil.getExtensionWindowLayoutComponent;
import static android.server.wm.jetpack.utils.ExtensionUtil.getExtensionWindowLayoutInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import android.app.Activity;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitPairRule;
import androidx.window.extensions.layout.WindowLayoutComponent;
import androidx.window.extensions.layout.WindowLayoutInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests integration
 * with other features.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingIntegrationTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingIntegrationTests extends ActivityEmbeddingTestBase {
    private WindowLayoutComponent mWindowLayoutComponent;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        assumeExtensionSupportedDevice();
        mWindowLayoutComponent = getExtensionWindowLayoutComponent();
        assumeNotNull(mWindowLayoutComponent);
    }

    /**
     * Tests that display features are still reported when using ActivityEmbedding.
     */
    @Test
    public void testDisplayFeaturesWithEmbedding() throws Exception {
        TestConfigChangeHandlingActivity primaryActivity = startActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        WindowLayoutInfo windowLayoutInfo = getExtensionWindowLayoutInfo(primaryActivity);
        assumeHasDisplayFeatures(windowLayoutInfo);

        // Launch a second activity in a split. Use a very small split ratio, so that the secondary
        // activity occupies most of the screen.
        SplitPairRule splitPairRule = createSplitPairRuleBuilderWithJava8Predicate(
                activityActivityPair -> true,
                activityIntentPair -> true,
                windowMetrics -> true
        )
                .setSplitRatio(0.1f)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        Activity secondaryActivity = startActivityAndVerifySplit(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity" /* secondActivityId */, mSplitInfoConsumer);

        // Verify that an embedded activity still observes the same number of features
        WindowLayoutInfo newWindowLayoutInfo = getExtensionWindowLayoutInfo(secondaryActivity);
        assertEquals(windowLayoutInfo.getDisplayFeatures().size(),
                newWindowLayoutInfo.getDisplayFeatures().size());

        // Need to reset primary activity bounds change counter because entering the split already
        // triggered a bounds change.
        primaryActivity.resetBoundsChangeCounter();

        // Finish the secondary activity and verify that the primary activity still receives the
        // display features
        secondaryActivity.finish();
        assertTrue(primaryActivity.waitForBoundsChange());
        assertEquals(getMaximumActivityBounds(primaryActivity),
                getActivityBounds(primaryActivity));

        newWindowLayoutInfo = getExtensionWindowLayoutInfo(primaryActivity);
        assertEquals(windowLayoutInfo.getDisplayFeatures().size(),
                newWindowLayoutInfo.getDisplayFeatures().size());
    }

    /**
     * Tests that clearing the split info consumer stops notifying unregistered consumer.
     */
    @Test
    public void testClearSplitInfoCallback() throws Exception {
        assumeVendorApiLevelAtLeast(2); // TODO(b/244450254): harden the requirement in U.
        mActivityEmbeddingComponent.clearSplitInfoCallback();
        TestConfigChangeHandlingActivity primaryActivity = startActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Launch a second activity in a split. Use a very small split ratio, so that the secondary
        // activity occupies most of the screen.
        SplitPairRule splitPairRule = createSplitPairRuleBuilderWithJava8Predicate(
                activityActivityPair -> true,
                activityIntentPair -> true,
                windowMetrics -> true
        )
                .setSplitRatio(0.1f)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        startActivityAndVerifyNoCallback(primaryActivity,
                TestActivityWithId.class,
                "secondaryActivity" /* secondActivityId */,
                mSplitInfoConsumer);
    }

    /**
     * Tests that display features are still reported when using ActivityEmbedding. Same as above,
     * but using different packages for the host and embedded activities.
     * Fixed in CL: If2dbc337c4b8cb909914cc28ae4db28a82ff9de3
     */
    @Test
    public void testDisplayFeaturesWithEmbedding_differentPackage() throws Exception {
        // Start an activity to collect the window layout info.
        TestConfigChangeHandlingActivity initialActivity = startActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        WindowLayoutInfo windowLayoutInfo = getExtensionWindowLayoutInfo(initialActivity);
        assumeHasDisplayFeatures(windowLayoutInfo);

        // Start an activity that will attempt to embed TestActivityKnownEmbeddingCerts. It will be
        // put in a new task, since it has a different affinity.
        Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_EMBED_ACTIVITY, true);
        extras.putFloat(EXTRA_SPLIT_RATIO, 0.1f);
        startActivityNoWait(mContext, SIGNED_EMBEDDING_ACTIVITY, extras);

        waitAndAssertResumed(EMBEDDED_ACTIVITY_ID);
        TestActivityWithId secondaryActivity = getResumedActivityById(EMBEDDED_ACTIVITY_ID);
        assertNotNull(secondaryActivity);
        assertTrue(mActivityEmbeddingComponent.isActivityEmbedded(secondaryActivity));

        // Verify that an embedded activity from a different package observes the same number of
        // features as the initial one.
        WindowLayoutInfo newWindowLayoutInfo = getExtensionWindowLayoutInfo(secondaryActivity);
        assertEquals(windowLayoutInfo.getDisplayFeatures().size(),
                newWindowLayoutInfo.getDisplayFeatures().size());
    }
}
