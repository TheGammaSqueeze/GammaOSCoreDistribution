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

package com.android.intentresolver;

import static android.app.Activity.RESULT_OK;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_CHOOSER_TARGET;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_DEFAULT;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER;
import static com.android.intentresolver.ChooserListAdapter.CALLER_TARGET_SCORE_BOOST;
import static com.android.intentresolver.ChooserListAdapter.SHORTCUT_TARGET_SCORE_BOOST;
import static com.android.intentresolver.MatcherUtils.first;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertNull;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager.ShareShortcutInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.service.chooser.ChooserAction;
import android.service.chooser.ChooserTarget;
import android.util.HashedStringCache;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.BoundedDiagnosingMatcher;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.flags.Flags;
import com.android.intentresolver.shortcuts.ShortcutLoader;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.flags.BooleanFlag;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Instrumentation tests for the IntentResolver module's Sharesheet (ChooserActivity).
 * TODO: remove methods that supported running these tests against arbitrary ChooserActivity
 * subclasses. Those were left over from an earlier version where IntentResolver's ChooserActivity
 * inherited from the framework version at com.android.internal.app.ChooserActivity, and this test
 * file inherited from the framework's version as well. Once the migration to the IntentResolver
 * package is complete, that aspect of the test design can revert to match the style of the
 * framework tests prior to ag/16482932.
 * TODO: this can simply be renamed to "ChooserActivityTest" if that's ever unambiguous (i.e., if
 * there's no risk of confusion with the framework tests that currently share the same name).
 */
@RunWith(Parameterized.class)
public class UnbundledChooserActivityTest {

    /* --------
     * Subclasses should copy the following section verbatim (or alternatively could specify some
     * additional @Parameterized.Parameters, as long as the correct parameters are used to
     * initialize the ChooserActivityTest). The subclasses should also be @RunWith the
     * `Parameterized` runner.
     * --------
     */

    private static final Function<PackageManager, PackageManager> DEFAULT_PM = pm -> pm;
    private static final Function<PackageManager, PackageManager> NO_APP_PREDICTION_SERVICE_PM =
            pm -> {
                PackageManager mock = Mockito.spy(pm);
                when(mock.getAppPredictionServicePackageName()).thenReturn(null);
                return mock;
            };

    private static final List<BooleanFlag> ALL_FLAGS =
            Arrays.asList(
                    Flags.SHARESHEET_CUSTOM_ACTIONS,
                    Flags.SHARESHEET_RESELECTION_ACTION,
                    Flags.SHARESHEET_IMAGE_AND_TEXT_PREVIEW,
                    Flags.SHARESHEET_SCROLLABLE_IMAGE_PREVIEW);

    private static final Map<BooleanFlag, Boolean> ALL_FLAGS_OFF =
            createAllFlagsOverride(false);
    private static final Map<BooleanFlag, Boolean> ALL_FLAGS_ON =
            createAllFlagsOverride(true);

    @Parameterized.Parameters
    public static Collection packageManagers() {
        return Arrays.asList(new Object[][] {
                // Default PackageManager and all flags off
                { DEFAULT_PM, ALL_FLAGS_OFF },
                // Default PackageManager and all flags on
                { DEFAULT_PM, ALL_FLAGS_ON },
                // No App Prediction Service and all flags off
                { NO_APP_PREDICTION_SERVICE_PM, ALL_FLAGS_OFF },
                // No App Prediction Service and all flags on
                { NO_APP_PREDICTION_SERVICE_PM, ALL_FLAGS_ON }
        });
    }

    private static Map<BooleanFlag, Boolean> createAllFlagsOverride(boolean value) {
        HashMap<BooleanFlag, Boolean> overrides = new HashMap<>(ALL_FLAGS.size());
        for (BooleanFlag flag : ALL_FLAGS) {
            overrides.put(flag, value);
        }
        return overrides;
    }

    /* --------
     * Subclasses can override the following methods to customize test behavior.
     * --------
     */

    /**
     * Perform any necessary per-test initialization steps (subclasses may add additional steps
     * before and/or after calling up to the superclass implementation).
     */
    @CallSuper
    protected void setup() {
        // TODO: use the other form of `adoptShellPermissionIdentity()` where we explicitly list the
        // permissions we require (which we'll read from the manifest at runtime).
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        cleanOverrideData();
        ChooserActivityOverrideData.getInstance().featureFlagRepository =
                new TestFeatureFlagRepository(mFlags);
    }

    /**
     * Given an intent that was constructed in a test, perform any additional configuration to
     * specify the appropriate concrete ChooserActivity subclass. The activity launched by this
     * intent must descend from android.intentresolver.ChooserActivity (for our ActivityTestRule), and
     * must also implement the android.intentresolver.IChooserWrapper interface (since test code will
     * assume the ability to make unsafe downcasts).
     */
    protected Intent getConcreteIntentForLaunch(Intent clientIntent) {
        clientIntent.setClass(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                com.android.intentresolver.ChooserWrapperActivity.class);
        return clientIntent;
    }

    /**
     * Whether {@code #testIsAppPredictionServiceAvailable} should verify the behavior after
     * changing the availability conditions at runtime. In the unbundled chooser, the availability
     * is cached at start and will never be re-evaluated.
     * TODO: remove when we no longer want to test the system's on-the-fly evaluation.
     */
    protected boolean shouldTestTogglingAppPredictionServiceAvailabilityAtRuntime() {
        return false;
    }

    /* --------
     * The code in this section is unorthodox and can be simplified/reverted when we no longer need
     * to support the parallel chooser implementations.
     * --------
     */

    @Rule
    public final TestRule mRule;

    // Shared test code references the activity under test as ChooserActivity, the common ancestor
    // of any (inheritance-based) chooser implementation. For testing purposes, that activity will
    // usually be cast to IChooserWrapper to expose instrumentation.
    private ActivityTestRule<ChooserActivity> mActivityRule =
            new ActivityTestRule<>(ChooserActivity.class, false, false) {
                @Override
                public ChooserActivity launchActivity(Intent clientIntent) {
                    return super.launchActivity(getConcreteIntentForLaunch(clientIntent));
                }
            };

    @Before
    public final void doPolymorphicSetup() {
        // The base class needs a @Before-annotated setup for when it runs against the system
        // chooser, while subclasses need to be able to specify their own setup behavior. Notably
        // the unbundled chooser, running in user-space, needs to take additional steps before it
        // can run #cleanOverrideData() (which writes to DeviceConfig).
        setup();
    }

    /* --------
     * Subclasses can ignore the remaining code and inherit the full suite of tests.
     * --------
     */

    private static final String TEST_MIME_TYPE = "application/TestType";

    private static final int CONTENT_PREVIEW_IMAGE = 1;
    private static final int CONTENT_PREVIEW_FILE = 2;
    private static final int CONTENT_PREVIEW_TEXT = 3;

    private final Function<PackageManager, PackageManager> mPackageManagerOverride;
    private final Map<BooleanFlag, Boolean> mFlags;


    public UnbundledChooserActivityTest(
                Function<PackageManager, PackageManager> packageManagerOverride,
                Map<BooleanFlag, Boolean> flags) {
        mPackageManagerOverride = packageManagerOverride;
        mFlags = flags;

        mRule = RuleChain
                .outerRule(new FeatureFlagRule(flags))
                .around(mActivityRule);
    }

    private void setDeviceConfigProperty(
            @NonNull String propertyName,
            @NonNull String value) {
        // TODO: consider running with {@link #runWithShellPermissionIdentity()} to more narrowly
        // request WRITE_DEVICE_CONFIG permissions if we get rid of the broad grant we currently
        // configure in {@link #setup()}.
        // TODO: is it really appropriate that this is always set with makeDefault=true?
        boolean valueWasSet = DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                propertyName,
                value,
                true /* makeDefault */);
        if (!valueWasSet) {
            throw new IllegalStateException(
                        "Could not set " + propertyName + " to " + value);
        }
    }

    public void cleanOverrideData() {
        ChooserActivityOverrideData.getInstance().reset();
        ChooserActivityOverrideData.getInstance().createPackageManager = mPackageManagerOverride;

        setDeviceConfigProperty(
                SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                Boolean.toString(true));
    }

    @Test
    public void customTitle() throws InterruptedException {
        Intent viewIntent = createViewTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        final IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(
                Intent.createChooser(viewIntent, "chooser test"));

        waitForIdle();
        assertThat(activity.getAdapter().getCount(), is(2));
        assertThat(activity.getAdapter().getServiceTargetCount(), is(0));
        onView(withId(android.R.id.title)).check(matches(withText("chooser test")));
    }

    @Test
    public void customTitleIgnoredForSendIntents() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "chooser test"));
        waitForIdle();
        onView(withId(android.R.id.title))
                .check(matches(withText(com.android.internal.R.string.whichSendApplication)));
    }

    @Test
    public void emptyTitle() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(android.R.id.title))
                .check(matches(withText(com.android.internal.R.string.whichSendApplication)));
    }

    @Test
    public void emptyPreviewTitleAndThumbnail() throws InterruptedException {
        Intent sendIntent = createSendTextIntentWithPreview(null, null);
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(not(isDisplayed())));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void visiblePreviewTitleWithoutThumbnail() throws InterruptedException {
        String previewTitle = "My Content Preview Title";
        Intent sendIntent = createSendTextIntentWithPreview(previewTitle, null);
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(withText(previewTitle)));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void visiblePreviewTitleWithInvalidThumbnail() throws InterruptedException {
        String previewTitle = "My Content Preview Title";
        Intent sendIntent = createSendTextIntentWithPreview(previewTitle,
                Uri.parse("tel:(+49)12345789"));
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void visiblePreviewTitleAndThumbnail() throws InterruptedException {
        String previewTitle = "My Content Preview Title";
        Intent sendIntent = createSendTextIntentWithPreview(previewTitle,
                Uri.parse("android.resource://com.android.frameworks.coretests/"
                        + R.drawable.test320x240));
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_title))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_thumbnail))
                .check(matches(isDisplayed()));
    }

    @Test @Ignore
    public void twoOptionsAndUserSelectsOne() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        assertThat(activity.getAdapter().getCount(), is(2));
        onView(withId(com.android.internal.R.id.profile_button)).check(doesNotExist());

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test @Ignore
    public void fourOptionsStackedIntoOneTarget() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();

        // create just enough targets to ensure the a-z list should be shown
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(1);

        // next create 4 targets in a single app that should be stacked into a single target
        String packageName = "xxx.yyy";
        String appName = "aaa";
        ComponentName cn = new ComponentName(packageName, appName);
        Intent intent = new Intent("fakeIntent");
        List<ResolvedComponentInfo> infosToStack = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ResolveInfo resolveInfo = ResolverDataProvider.createResolveInfo(i,
                    UserHandle.USER_CURRENT);
            resolveInfo.activityInfo.applicationInfo.name = appName;
            resolveInfo.activityInfo.applicationInfo.packageName = packageName;
            resolveInfo.activityInfo.packageName = packageName;
            resolveInfo.activityInfo.name = "ccc" + i;
            infosToStack.add(new ResolvedComponentInfo(cn, intent, resolveInfo));
        }
        resolvedComponentInfos.addAll(infosToStack);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // expect 1 unique targets + 1 group + 4 ranked app targets
        assertThat(activity.getAdapter().getCount(), is(6));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        onView(allOf(withText(appName), hasSibling(withText("")))).perform(click());
        waitForIdle();

        // clicking will launch a dialog to choose the activity within the app
        onView(withText(appName)).check(matches(isDisplayed()));
        int i = 0;
        for (ResolvedComponentInfo rci: infosToStack) {
            onView(withText("ccc" + i)).check(matches(isDisplayed()));
            ++i;
        }
    }

    @Test @Ignore
    public void updateChooserCountsAndModelAfterUserSelection() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        UsageStatsManager usm = activity.getUsageStatsManager();
        verify(ChooserActivityOverrideData.getInstance().resolverListController, times(1))
                .topK(any(List.class), anyInt());
        assertThat(activity.getIsSelected(), is(false));
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            return true;
        };
        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();
        verify(ChooserActivityOverrideData.getInstance().resolverListController, times(1))
                .updateChooserCounts(Mockito.anyString(), anyInt(), Mockito.anyString());
        verify(ChooserActivityOverrideData.getInstance().resolverListController, times(1))
                .updateModel(toChoose.activityInfo.getComponentName());
        assertThat(activity.getIsSelected(), is(true));
    }

    @Ignore // b/148158199
    @Test
    public void noResultsFromPackageManager() {
        setupResolverControllers(null);
        Intent sendIntent = createSendTextIntent();
        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        final IChooserWrapper wrapper = (IChooserWrapper) activity;

        waitForIdle();
        assertThat(activity.isFinishing(), is(false));

        onView(withId(android.R.id.empty)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.profile_pager)).check(matches(not(isDisplayed())));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> wrapper.getAdapter().handlePackagesChanged()
        );
        // backward compatibility. looks like we finish when data is empty after package change
        assertThat(activity.isFinishing(), is(true));
    }

    @Test
    public void autoLaunchSingleResult() throws InterruptedException {
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(1);
        setupResolverControllers(resolvedComponentInfos);

        Intent sendIntent = createSendTextIntent();
        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        assertThat(chosen[0], is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));
        assertThat(activity.isFinishing(), is(true));
    }

    @Test @Ignore
    public void hasOtherProfileOneOption() {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        markWorkProfileUserAvailable();

        ResolveInfo toChoose = personalResolvedComponentInfos.get(1).getResolveInfoAt(0);
        Intent sendIntent = createSendTextIntent();
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(1));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId= */ 10);
        waitForIdle();

        onView(first(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name)))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test @Ignore
    public void hasOtherProfileTwoOptionsAndUserSelectsOne() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        setupResolverControllers(resolvedComponentInfos);
        when(ChooserActivityOverrideData.getInstance().resolverListController.getLastChosen())
                .thenReturn(resolvedComponentInfos.get(0).getResolveInfoAt(0));

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(3);
        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test @Ignore
    public void hasLastChosenActivityAndOtherProfile() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // The other entry is filtered to the last used slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(3);
        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_IMAGE_AND_TEXT_PREVIEW_NAME },
            values = { true })
    public void testImagePlusTextSharing_ExcludeText() {
        Intent sendIntent = createSendImageIntent(
                Uri.parse("android.resource://com.android.frameworks.coretests/"
                        + R.drawable.test320x240));
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;
        sendIntent.putExtra(Intent.EXTRA_TEXT, "https://google.com/search?q=google");

        List<ResolvedComponentInfo> resolvedComponentInfos = Arrays.asList(
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.imageviewer", "ImageTarget"),
                        sendIntent),
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.textviewer", "UriTarget"),
                        new Intent("VIEW_TEXT"))
        );

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.include_text_action))
                .check(matches(isDisplayed()))
                .perform(click());
        waitForIdle();

        AtomicReference<Intent> launchedIntentRef = new AtomicReference<>();
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            launchedIntentRef.set(targetInfo.getTargetIntent());
            return true;
        };

        onView(withText(resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(launchedIntentRef.get().hasExtra(Intent.EXTRA_TEXT)).isFalse();
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_IMAGE_AND_TEXT_PREVIEW_NAME },
            values = { true })
    public void testImagePlusTextSharing_RemoveAndAddBackText() {
        Intent sendIntent = createSendImageIntent(
                Uri.parse("android.resource://com.android.frameworks.coretests/"
                        + R.drawable.test320x240));
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;
        final String text = "https://google.com/search?q=google";
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);

        List<ResolvedComponentInfo> resolvedComponentInfos = Arrays.asList(
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.imageviewer", "ImageTarget"),
                        sendIntent),
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.textviewer", "UriTarget"),
                        new Intent("VIEW_TEXT"))
        );

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.include_text_action))
                .check(matches(isDisplayed()))
                .perform(click());
        waitForIdle();
        onView(withId(R.id.include_text_action))
                .perform(click());
        waitForIdle();

        AtomicReference<Intent> launchedIntentRef = new AtomicReference<>();
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            launchedIntentRef.set(targetInfo.getTargetIntent());
            return true;
        };

        onView(withText(resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(launchedIntentRef.get().getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(text);
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_IMAGE_AND_TEXT_PREVIEW_NAME },
            values = { true })
    public void testImagePlusTextSharing_TextExclusionDoesNotAffectAlternativeIntent() {
        Intent sendIntent = createSendImageIntent(
                Uri.parse("android.resource://com.android.frameworks.coretests/"
                        + R.drawable.test320x240));
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;
        sendIntent.putExtra(Intent.EXTRA_TEXT, "https://google.com/search?q=google");

        Intent alternativeIntent = createSendTextIntent();
        final String text = "alternative intent";
        alternativeIntent.putExtra(Intent.EXTRA_TEXT, text);

        List<ResolvedComponentInfo> resolvedComponentInfos = Arrays.asList(
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.imageviewer", "ImageTarget"),
                        sendIntent),
                ResolverDataProvider.createResolvedComponentInfo(
                        new ComponentName("org.textviewer", "UriTarget"),
                        alternativeIntent)
        );

        setupResolverControllers(resolvedComponentInfos);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(R.id.include_text_action))
                .check(matches(isDisplayed()))
                .perform(click());
        waitForIdle();

        AtomicReference<Intent> launchedIntentRef = new AtomicReference<>();
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            launchedIntentRef.set(targetInfo.getTargetIntent());
            return true;
        };

        onView(withText(resolvedComponentInfos.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        waitForIdle();
        assertThat(launchedIntentRef.get().getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(text);
    }

    @Test
    public void copyTextToClipboard() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_copy_button)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_copy_button)).perform(click());
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboard.getPrimaryClip();
        assertThat("testing intent sending", is(clipData.getItemAt(0).getText()));

        ClipDescription clipDescription = clipData.getDescription();
        assertThat("text/plain", is(clipDescription.getMimeType(0)));

        assertEquals(mActivityRule.getActivityResult().getResultCode(), RESULT_OK);
    }

    @Test
    public void copyTextToClipboardLogging() {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_copy_button)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_copy_button)).perform(click());

        ChooserActivityLogger logger = activity.getChooserActivityLogger();
        verify(logger, times(1)).logActionSelected(eq(ChooserActivityLogger.SELECTION_TYPE_COPY));
    }

    @Test
    @Ignore
    public void testNearbyShareLogging() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_nearby_button))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_nearby_button)).perform(click());

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }



    @Test @Ignore
    public void testEditImageLogs() throws Exception {
        Intent sendIntent = createSendImageIntent(
                Uri.parse("android.resource://com.android.frameworks.coretests/"
                        + R.drawable.test320x240));

        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_edit_button)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_edit_button)).perform(click());

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }


    @Test
    public void oneVisibleImagePreview() {
        Uri uri = Uri.parse("android.resource://com.android.frameworks.coretests/"
                + R.drawable.test320x240);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_image_area))
                .check((view, exception) -> {
                    if (exception != null) {
                        throw exception;
                    }
                    ViewGroup parent = (ViewGroup) view;
                    ArrayList<View> visibleViews = new ArrayList<>();
                    for (int i = 0, count = parent.getChildCount(); i < count; i++) {
                        View child = parent.getChildAt(i);
                        if (child.getVisibility() == View.VISIBLE) {
                            visibleViews.add(child);
                        }
                    }
                    assertThat(visibleViews.size(), is(1));
                    assertThat(
                            "image preview view is fully visible",
                            isDisplayed().matches(visibleViews.get(0)));
                });
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_SCROLLABLE_IMAGE_PREVIEW_NAME },
            values = { false })
    public void twoVisibleImagePreview() {
        Uri uri = Uri.parse("android.resource://com.android.frameworks.coretests/"
                + R.drawable.test320x240);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_image_1_large))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_image_2_large))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_image_2_small))
                .check(matches(not(isDisplayed())));
        onView(withId(com.android.internal.R.id.content_preview_image_3_small))
                .check(matches(not(isDisplayed())));
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_SCROLLABLE_IMAGE_PREVIEW_NAME },
            values = { false })
    public void threeOrMoreVisibleImagePreview() {
        Uri uri = Uri.parse("android.resource://com.android.frameworks.coretests/"
                + R.drawable.test320x240);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_image_1_large))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_image_2_large))
                .check(matches(not(isDisplayed())));
        onView(withId(com.android.internal.R.id.content_preview_image_2_small))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_image_3_small))
                .check(matches(isDisplayed()));
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_SCROLLABLE_IMAGE_PREVIEW_NAME },
            values = { true })
    public void testManyVisibleImagePreview_ScrollableImagePreview() {
        Uri uri = Uri.parse("android.resource://com.android.frameworks.coretests/"
                + R.drawable.test320x240);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_image_area))
                .perform(RecyclerViewActions.scrollToLastPosition())
                .check((view, exception) -> {
                    if (exception != null) {
                        throw exception;
                    }
                    RecyclerView recyclerView = (RecyclerView) view;
                    assertThat(recyclerView.getAdapter().getItemCount(), is(uris.size()));
                });
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_IMAGE_AND_TEXT_PREVIEW_NAME },
            values = { true })
    public void testImageAndTextPreview() {
        final Uri uri = Uri.parse("android.resource://com.android.frameworks.coretests/"
                + R.drawable.test320x240);
        final String sharedText = "text-" + System.currentTimeMillis();

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sharedText);
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withText(sharedText))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testOnCreateLogging() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "logger test"));
        ChooserActivityLogger logger = activity.getChooserActivityLogger();
        waitForIdle();

        verify(logger).logChooserActivityShown(eq(false), eq(TEST_MIME_TYPE), anyLong());
    }

    @Test
    public void testOnCreateLoggingFromWorkProfile() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        ChooserActivityOverrideData.getInstance().alternateProfileSetting =
                MetricsEvent.MANAGED_PROFILE;

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "logger test"));
        ChooserActivityLogger logger = activity.getChooserActivityLogger();
        waitForIdle();

        verify(logger).logChooserActivityShown(eq(true), eq(TEST_MIME_TYPE), anyLong());
    }

    @Test
    public void testEmptyPreviewLogging() {
        Intent sendIntent = createSendTextIntentWithPreview(null, null);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(
                        Intent.createChooser(sendIntent, "empty preview logger test"));
        ChooserActivityLogger logger = activity.getChooserActivityLogger();
        waitForIdle();

        verify(logger).logChooserActivityShown(eq(false), eq(null), anyLong());
    }

    @Test
    public void testTitlePreviewLogging() {
        Intent sendIntent = createSendTextIntentWithPreview("TestTitle", null);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // Second invocation is from onCreate
        ChooserActivityLogger logger = activity.getChooserActivityLogger();
        Mockito.verify(logger, times(1)).logActionShareWithPreview(eq(CONTENT_PREVIEW_TEXT));
    }

    @Test
    public void testImagePreviewLogging() {
        Uri uri = Uri.parse("android.resource://com.android.frameworks.coretests/"
                + R.drawable.test320x240);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);
        ChooserActivityOverrideData.getInstance().previewThumbnail = createBitmap();
        ChooserActivityOverrideData.getInstance().isImageType = true;

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        ChooserActivityLogger logger = activity.getChooserActivityLogger();
        Mockito.verify(logger, times(1)).logActionShareWithPreview(eq(CONTENT_PREVIEW_IMAGE));
    }

    @Test
    public void oneVisibleFilePreview() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(withText("app.pdf")));
        onView(withId(com.android.internal.R.id.content_preview_file_icon))
                .check(matches(isDisplayed()));
    }


    @Test
    public void moreThanOneVisibleFilePreview() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(withText("app.pdf + 2 files")));
        onView(withId(com.android.internal.R.id.content_preview_file_icon))
                .check(matches(isDisplayed()));
    }

    @Test
    public void contentProviderThrowSecurityException() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        ChooserActivityOverrideData.getInstance().resolverForceException = true;

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(withText("app.pdf")));
        onView(withId(com.android.internal.R.id.content_preview_file_icon))
                .check(matches(isDisplayed()));
    }

    @Test
    public void contentProviderReturnsNoColumns() throws InterruptedException {
        Uri uri = Uri.parse("content://com.android.frameworks.coretests/app.pdf");

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        uris.add(uri);

        Intent sendIntent = createSendUriIntentWithPreview(uris);

        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        Cursor cursor = mock(Cursor.class);
        when(cursor.getCount()).thenReturn(1);
        Mockito.doNothing().when(cursor).close();
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getColumnIndex(Mockito.anyString())).thenReturn(-1);

        ChooserActivityOverrideData.getInstance().resolverCursor = cursor;

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.content_preview_filename))
                .check(matches(withText("app.pdf + 1 file")));
        onView(withId(com.android.internal.R.id.content_preview_file_icon))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testGetBaseScore() {
        final float testBaseScore = 0.89f;

        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resolverListController
                        .getScore(Mockito.isA(DisplayResolveInfo.class)))
                .thenReturn(testBaseScore);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        final DisplayResolveInfo testDri =
                activity.createTestDisplayResolveInfo(sendIntent,
                ResolverDataProvider.createResolveInfo(3, 0), "testLabel", "testInfo", sendIntent,
                /* resolveInfoPresentationGetter */ null);
        final ChooserListAdapter adapter = activity.getAdapter();

        assertThat(adapter.getBaseScore(null, 0), is(CALLER_TARGET_SCORE_BOOST));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_DEFAULT), is(testBaseScore));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_CHOOSER_TARGET), is(testBaseScore));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE),
                is(testBaseScore * SHORTCUT_TARGET_SCORE_BOOST));
        assertThat(adapter.getBaseScore(testDri, TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER),
                is(testBaseScore * SHORTCUT_TARGET_SCORE_BOOST));
    }

    // This test is too long and too slow and should not be taken as an example for future tests.
    @Test
    public void testDirectTargetSelectionLogging() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).queryShortcuts(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(1, "");
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly one selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        ArgumentCaptor<HashedStringCache.HashResult> hashCaptor =
                ArgumentCaptor.forClass(HashedStringCache.HashResult.class);
        verify(activity.getChooserActivityLogger(), times(1)).logShareTargetSelected(
                eq(ChooserActivityLogger.SELECTION_TYPE_SERVICE),
                /* packageName= */ any(),
                /* positionPicked= */ anyInt(),
                /* directTargetAlsoRanked= */ eq(-1),
                /* numCallerProvided= */ anyInt(),
                /* directTargetHashed= */ hashCaptor.capture(),
                /* isPinned= */ anyBoolean(),
                /* successfullySelected= */ anyBoolean(),
                /* selectionCost= */ anyLong());
        String hashedName = hashCaptor.getValue().hashedString;
        assertThat(
                "Hash is not predictable but must be obfuscated",
                hashedName, is(not(name)));
    }

    // This test is too long and too slow and should not be taken as an example for future tests.
    @Test
    public void testDirectTargetLoggingWithRankedAppTarget() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).queryShortcuts(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                1,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly one selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        verify(activity.getChooserActivityLogger(), times(1)).logShareTargetSelected(
                eq(ChooserActivityLogger.SELECTION_TYPE_SERVICE),
                /* packageName= */ any(),
                /* positionPicked= */ anyInt(),
                /* directTargetAlsoRanked= */ eq(0),
                /* numCallerProvided= */ anyInt(),
                /* directTargetHashed= */ any(),
                /* isPinned= */ anyBoolean(),
                /* successfullySelected= */ anyBoolean(),
                /* selectionCost= */ anyLong());
    }

    @Test
    public void testShortcutTargetWithApplyAppLimits() {
        // Set up resources
        ChooserActivityOverrideData.getInstance().resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resources
                        .getInteger(R.integer.config_maxShortcutTargetsPerApp))
                .thenReturn(1);
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper) mActivityRule
                .launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).queryShortcuts(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                2,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly one selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(0).getDisplayLabel(),
                is("testTitle0"));
    }

    @Test
    public void testShortcutTargetWithoutApplyAppLimits() {
        setDeviceConfigProperty(
                SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                Boolean.toString(false));
        // Set up resources
        ChooserActivityOverrideData.getInstance().resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resources
                        .getInteger(R.integer.config_maxShortcutTargetsPerApp))
                .thenReturn(1);
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).queryShortcuts(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                2,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 4 targets (2 apps, 2 direct)",
                activeAdapter.getCount(),
                is(4));
        assertThat(
                "Chooser should have exactly two selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(2));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activeAdapter.getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(0).getDisplayLabel(),
                is("testTitle0"));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(1).getDisplayLabel(),
                is("testTitle1"));
    }

    @Test
    public void testLaunchWithCallerProvidedTarget() {
        setDeviceConfigProperty(
                SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                Boolean.toString(false));
        // Set up resources
        ChooserActivityOverrideData.getInstance().resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resources
                        .getInteger(R.integer.config_maxShortcutTargetsPerApp))
                .thenReturn(1);

        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // set caller-provided target
        Intent chooserIntent = Intent.createChooser(createSendTextIntent(), null);
        String callerTargetLabel = "Caller Target";
        ChooserTarget[] targets = new ChooserTarget[] {
                new ChooserTarget(
                        callerTargetLabel,
                        Icon.createWithBitmap(createBitmap()),
                        0.1f,
                        resolvedComponentInfos.get(0).name,
                        new Bundle())
        };
        chooserIntent.putExtra(Intent.EXTRA_CHOOSER_TARGETS, targets);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                createShortcutLoaderFactory();

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1)).queryShortcuts(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                true,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[0],
                new HashMap<>(),
                new HashMap<>());
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        final ChooserListAdapter activeAdapter = activity.getAdapter();
        assertThat(
                "Chooser should have 3 targets (2 apps, 1 direct)",
                activeAdapter.getCount(),
                is(3));
        assertThat(
                "Chooser should have exactly two selectable direct target",
                activeAdapter.getSelectableServiceTargetCount(),
                is(1));
        assertThat(
                "The display label must match",
                activeAdapter.getItem(0).getDisplayLabel(),
                is(callerTargetLabel));
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_CUSTOM_ACTIONS_NAME },
            values = { true })
    public void testLaunchWithCustomAction() throws InterruptedException {
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        final String customActionLabel = "Custom Action";
        final String testAction = "test-broadcast-receiver-action";
        Intent chooserIntent = Intent.createChooser(createSendTextIntent(), null);
        chooserIntent.putExtra(
                Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS,
                new ChooserAction[] {
                        new ChooserAction.Builder(
                                Icon.createWithResource("", Resources.ID_NULL),
                                customActionLabel,
                                PendingIntent.getBroadcast(
                                        testContext,
                                        123,
                                        new Intent(testAction),
                                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT))
                                .build()
                });
        // Start activity
        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        BroadcastReceiver testReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastInvoked.countDown();
            }
        };
        testContext.registerReceiver(testReceiver, new IntentFilter(testAction));

        try {
            onView(withText(customActionLabel)).perform(click());
            broadcastInvoked.await();
        } finally {
            testContext.unregisterReceiver(testReceiver);
        }
    }

    @Test
    @RequireFeatureFlags(
            flags = { Flags.SHARESHEET_RESELECTION_ACTION_NAME },
            values = { true })
    public void testLaunchWithShareModification() throws InterruptedException {
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        final String modifyShareAction = "test-broadcast-receiver-action";
        Intent chooserIntent = Intent.createChooser(createSendTextIntent(), null);
        chooserIntent.putExtra(
                Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION,
                PendingIntent.getBroadcast(
                        testContext,
                        123,
                        new Intent(modifyShareAction),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT));
        // Start activity
        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        final CountDownLatch broadcastInvoked = new CountDownLatch(1);
        BroadcastReceiver testReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastInvoked.countDown();
            }
        };
        testContext.registerReceiver(testReceiver, new IntentFilter(modifyShareAction));

        try {
            onView(withText(R.string.select_text)).perform(click());
            broadcastInvoked.await();
        } finally {
            testContext.unregisterReceiver(testReceiver);
        }
    }

    @Test
    public void testUpdateMaxTargetsPerRow_columnCountIsUpdated() throws InterruptedException {
        updateMaxTargetsPerRowResource(/* targetsPerRow= */ 4);
        givenAppTargets(/* appCount= */ 16);
        Intent sendIntent = createSendTextIntent();
        final ChooserActivity activity =
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));

        updateMaxTargetsPerRowResource(/* targetsPerRow= */ 6);
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> activity.onConfigurationChanged(
                        InstrumentationRegistry.getInstrumentation()
                                .getContext().getResources().getConfiguration()));

        waitForIdle();
        onView(withId(com.android.internal.R.id.resolver_list))
                .check(matches(withGridColumnCount(6)));
    }

    // This test is too long and too slow and should not be taken as an example for future tests.
    @Test @Ignore
    public void testDirectTargetLoggingWithAppTargetNotRankedPortrait()
            throws InterruptedException {
        testDirectTargetLoggingWithAppTargetNotRanked(Configuration.ORIENTATION_PORTRAIT, 4);
    }

    @Test @Ignore
    public void testDirectTargetLoggingWithAppTargetNotRankedLandscape()
            throws InterruptedException {
        testDirectTargetLoggingWithAppTargetNotRanked(Configuration.ORIENTATION_LANDSCAPE, 8);
    }

    private void testDirectTargetLoggingWithAppTargetNotRanked(
            int orientation, int appTargetsExpected) {
        Configuration configuration =
                new Configuration(InstrumentationRegistry.getInstrumentation().getContext()
                        .getResources().getConfiguration());
        configuration.orientation = orientation;

        ChooserActivityOverrideData.getInstance().resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resources
                        .getConfiguration())
                .thenReturn(configuration);

        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(15);
        setupResolverControllers(resolvedComponentInfos);

        // Create direct share target
        List<ChooserTarget> serviceTargets = createDirectShareTargets(1,
                resolvedComponentInfos.get(14).getResolveInfoAt(0).activityInfo.packageName);
        ResolveInfo ri = ResolverDataProvider.createResolveInfo(16, 0);

        // Start activity
        final IChooserWrapper wrapper = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        // Insert the direct share target
        Map<ChooserTarget, ShortcutInfo> directShareToShortcutInfos = new HashMap<>();
        directShareToShortcutInfos.put(serviceTargets.get(0), null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> wrapper.getAdapter().addServiceResults(
                        wrapper.createTestDisplayResolveInfo(sendIntent,
                                ri,
                                "testLabel",
                                "testInfo",
                                sendIntent,
                                /* resolveInfoPresentationGetter */ null),
                        serviceTargets,
                        TARGET_TYPE_CHOOSER_TARGET,
                        directShareToShortcutInfos,
                        /* directShareToAppTargets */ null)
        );

        assertThat(
                String.format("Chooser should have %d targets (%d apps, 1 direct, 15 A-Z)",
                        appTargetsExpected + 16, appTargetsExpected),
                wrapper.getAdapter().getCount(), is(appTargetsExpected + 16));
        assertThat("Chooser should have exactly one selectable direct target",
                wrapper.getAdapter().getSelectableServiceTargetCount(), is(1));
        assertThat("The resolver info must match the resolver info used to create the target",
                wrapper.getAdapter().getItem(0).getResolveInfo(), is(ri));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        ChooserActivityLogger logger = wrapper.getChooserActivityLogger();
        verify(logger, times(1)).logShareTargetSelected(
                eq(ChooserActivityLogger.SELECTION_TYPE_SERVICE),
                /* packageName= */ any(),
                /* positionPicked= */ anyInt(),
                // The packages sholdn't match for app target and direct target:
                /* directTargetAlsoRanked= */ eq(-1),
                /* numCallerProvided= */ anyInt(),
                /* directTargetHashed= */ any(),
                /* isPinned= */ anyBoolean(),
                /* successfullySelected= */ anyBoolean(),
                /* selectionCost= */ anyLong());
    }

    @Test
    public void testWorkTab_displayedWhenWorkProfileUserAvailable() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        markWorkProfileUserAvailable();

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();

        onView(withId(android.R.id.tabs)).check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_hiddenWhenWorkProfileUserNotAvailable() {
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();

        onView(withId(android.R.id.tabs)).check(matches(not(isDisplayed())));
    }

    @Test
    public void testWorkTab_eachTabUsesExpectedAdapter() {
        int personalProfileTargets = 3;
        int otherProfileTargets = 1;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(
                        personalProfileTargets + otherProfileTargets, /* userID */ 10);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(
                workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        markWorkProfileUserAvailable();

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();

        assertThat(activity.getCurrentUserHandle().getIdentifier(), is(0));
        onView(withText(R.string.resolver_work_tab)).perform(click());
        assertThat(activity.getCurrentUserHandle().getIdentifier(), is(10));
        assertThat(activity.getPersonalListAdapter().getCount(), is(personalProfileTargets));
        assertThat(activity.getWorkListAdapter().getCount(), is(workProfileTargets));
    }

    @Test
    public void testWorkTab_workProfileHasExpectedNumberOfTargets() throws InterruptedException {
        markWorkProfileUserAvailable();
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        assertThat(activity.getWorkListAdapter().getCount(), is(workProfileTargets));
    }

    @Test @Ignore
    public void testWorkTab_selectingWorkTabAppOpensAppInWorkProfile() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(first(allOf(
                withText(workResolvedComponentInfos.get(0)
                        .getResolveInfoAt(0).activityInfo.applicationInfo.name),
                isDisplayed())))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(workResolvedComponentInfos.get(0).getResolveInfoAt(0)));
    }

    @Test
    public void testWorkTab_crossProfileIntentsDisabled_personalToWork_emptyStateShown() {
        markWorkProfileUserAvailable();
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());

        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_workProfileDisabled_emptyStateShown() {
        markWorkProfileUserAvailable();
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().isQuietModeEnabled = true;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_turn_on_work_apps))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_noWorkAppsAvailable_emptyStateShown() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Ignore // b/220067877
    @Test
    public void testWorkTab_xProfileOff_noAppsAvailable_workOff_xProfileOffEmptyStateShown() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        ChooserActivityOverrideData.getInstance().isQuietModeEnabled = true;
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_noAppsAvailable_workOff_noAppsAvailableEmptyStateShown() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        ChooserActivityOverrideData.getInstance().isQuietModeEnabled = true;
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Test @Ignore("b/222124533")
    public void testAppTargetLogging() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // TODO(b/222124533): other test cases use a timeout to make sure that the UI is fully
        // populated; without one, this test flakes. Ideally we should address the need for a
        // timeout everywhere instead of introducing one to fix this particular test.

        assertThat(activity.getAdapter().getCount(), is(2));
        onView(withId(com.android.internal.R.id.profile_button)).check(doesNotExist());

        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        waitForIdle();

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Test
    public void testDirectTargetLogging() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                new SparseArray<>();
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> {
                    Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>> pair =
                            new Pair<>(mock(ShortcutLoader.class), callback);
                    shortcutLoaders.put(userHandle.getIdentifier(), pair);
                    return pair.first;
                };

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1))
                .queryShortcuts(appTargets.capture());

        // send shortcuts
        assertThat(
                "Wrong number of app targets",
                appTargets.getValue().length,
                is(resolvedComponentInfos.size()));
        List<ChooserTarget> serviceTargets = createDirectShareTargets(1,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                // TODO: test another value as well
                false,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        assertThat("Chooser should have 3 targets (2 apps, 1 direct)",
                activity.getAdapter().getCount(), is(3));
        assertThat("Chooser should have exactly one selectable direct target",
                activity.getAdapter().getSelectableServiceTargetCount(), is(1));
        assertThat(
                "The resolver info must match the resolver info used to create the target",
                activity.getAdapter().getItem(0).getResolveInfo(),
                is(resolvedComponentInfos.get(0).getResolveInfoAt(0)));

        // Click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name))
                .perform(click());
        waitForIdle();

        ChooserActivityLogger logger = activity.getChooserActivityLogger();
        ArgumentCaptor<Integer> typeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(logger, times(1)).logShareTargetSelected(
                eq(ChooserActivityLogger.SELECTION_TYPE_SERVICE),
                /* packageName= */ any(),
                /* positionPicked= */ anyInt(),
                /* directTargetAlsoRanked= */ anyInt(),
                /* numCallerProvided= */ anyInt(),
                /* directTargetHashed= */ any(),
                /* isPinned= */ anyBoolean(),
                /* successfullySelected= */ anyBoolean(),
                /* selectionCost= */ anyLong());
    }

    @Test
    public void testDirectTargetPinningDialog() {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // create test shortcut loader factory, remember loaders and their callbacks
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                new SparseArray<>();
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> {
                    Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>> pair =
                            new Pair<>(mock(ShortcutLoader.class), callback);
                    shortcutLoaders.put(userHandle.getIdentifier(), pair);
                    return pair.first;
                };

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        // verify that ShortcutLoader was queried
        ArgumentCaptor<DisplayResolveInfo[]> appTargets =
                ArgumentCaptor.forClass(DisplayResolveInfo[].class);
        verify(shortcutLoaders.get(0).first, times(1))
                .queryShortcuts(appTargets.capture());

        // send shortcuts
        List<ChooserTarget> serviceTargets = createDirectShareTargets(
                1,
                resolvedComponentInfos.get(0).getResolveInfoAt(0).activityInfo.packageName);
        ShortcutLoader.Result result = new ShortcutLoader.Result(
                // TODO: test another value as well
                false,
                appTargets.getValue(),
                new ShortcutLoader.ShortcutResultInfo[] {
                        new ShortcutLoader.ShortcutResultInfo(
                                appTargets.getValue()[0],
                                serviceTargets
                        )
                },
                new HashMap<>(),
                new HashMap<>()
        );
        activity.getMainExecutor().execute(() -> shortcutLoaders.get(0).second.accept(result));
        waitForIdle();

        // Long-click on the direct target
        String name = serviceTargets.get(0).getTitle().toString();
        onView(withText(name)).perform(longClick());
        waitForIdle();

        onView(withId(R.id.chooser_dialog_content)).check(matches(isDisplayed()));
    }

    @Test @Ignore
    public void testEmptyDirectRowLogging() throws InterruptedException {
        Intent sendIntent = createSendTextIntent();
        // We need app targets for direct targets to get displayed
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        setupResolverControllers(resolvedComponentInfos);

        // Start activity
        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));

        // Thread.sleep shouldn't be a thing in an integration test but it's
        // necessary here because of the way the code is structured
        Thread.sleep(3000);

        assertThat("Chooser should have 2 app targets",
                activity.getAdapter().getCount(), is(2));
        assertThat("Chooser should have no direct targets",
                activity.getAdapter().getSelectableServiceTargetCount(), is(0));

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Ignore // b/220067877
    @Test
    public void testCopyTextToClipboardLogging() throws Exception {
        Intent sendIntent = createSendTextIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, null));
        waitForIdle();

        onView(withId(com.android.internal.R.id.chooser_copy_button)).check(matches(isDisplayed()));
        onView(withId(com.android.internal.R.id.chooser_copy_button)).perform(click());

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Test @Ignore("b/222124533")
    public void testSwitchProfileLogging() throws InterruptedException {
        markWorkProfileUserAvailable();
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        final IChooserWrapper activity = (IChooserWrapper)
                mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();
        onView(withText(R.string.resolver_personal_tab)).perform(click());
        waitForIdle();

        // TODO(b/211669337): Determine the expected SHARESHEET_DIRECT_LOAD_COMPLETE events.
    }

    @Test
    public void testWorkTab_onePersonalTarget_emptyStateOnWorkTarget_autolaunch() {
        markWorkProfileUserAvailable();
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendTextIntent();
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "Test"));
        waitForIdle();

        assertThat(chosen[0], is(personalResolvedComponentInfos.get(1).getResolveInfoAt(0)));
    }

    @Test
    public void testOneInitialIntent_noAutolaunch() {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(1);
        setupResolverControllers(personalResolvedComponentInfos);
        Intent chooserIntent = createChooserIntent(createSendTextIntent(),
                new Intent[] {new Intent("action.fake")});
        ResolveInfo[] chosen = new ResolveInfo[1];
        ChooserActivityOverrideData.getInstance().onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        ResolveInfo ri = createFakeResolveInfo();
        when(
                ChooserActivityOverrideData
                        .getInstance().packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(ri);
        waitForIdle();

        IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        assertNull(chosen[0]);
        assertThat(activity
                .getPersonalListAdapter().getCallerTargetCount(), is(1));
    }

    @Test
    public void testWorkTab_withInitialIntents_workTabDoesNotIncludePersonalInitialIntents() {
        markWorkProfileUserAvailable();
        int workProfileTargets = 1;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent[] initialIntents = {
                new Intent("action.fake1"),
                new Intent("action.fake2")
        };
        Intent chooserIntent = createChooserIntent(createSendTextIntent(), initialIntents);
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(createFakeResolveInfo());
        waitForIdle();

        IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        assertThat(activity.getPersonalListAdapter().getCallerTargetCount(), is(2));
        assertThat(activity.getWorkListAdapter().getCallerTargetCount(), is(0));
    }

    @Test
    public void testWorkTab_xProfileIntentsDisabled_personalToWork_nonSendIntent_emptyStateShown() {
        markWorkProfileUserAvailable();
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        ChooserActivityOverrideData.getInstance().hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent[] initialIntents = {
                new Intent("action.fake1"),
                new Intent("action.fake2")
        };
        Intent chooserIntent = createChooserIntent(new Intent(), initialIntents);
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(createFakeResolveInfo());

        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());

        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_noWorkAppsAvailable_nonSendIntent_emptyStateShown() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent[] initialIntents = {
                new Intent("action.fake1"),
                new Intent("action.fake2")
        };
        Intent chooserIntent = createChooserIntent(new Intent(), initialIntents);
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(createFakeResolveInfo());

        mActivityRule.launchActivity(chooserIntent);
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testDeduplicateCallerTargetRankedTarget() {
        // Create 4 ranked app targets.
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos);
        // Create caller target which is duplicate with one of app targets
        Intent chooserIntent = createChooserIntent(createSendTextIntent(),
                new Intent[] {new Intent("action.fake")});
        ChooserActivityOverrideData.getInstance().packageManager = mock(PackageManager.class);
        ResolveInfo ri = ResolverDataProvider.createResolveInfo(0,
                UserHandle.USER_CURRENT);
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .packageManager
                        .resolveActivity(any(Intent.class), any()))
                .thenReturn(ri);
        waitForIdle();

        IChooserWrapper activity = (IChooserWrapper) mActivityRule.launchActivity(chooserIntent);
        waitForIdle();

        // Total 4 targets (1 caller target, 3 ranked targets)
        assertThat(activity.getAdapter().getCount(), is(4));
        assertThat(activity.getAdapter().getCallerTargetCount(), is(1));
        assertThat(activity.getAdapter().getRankedTargetCount(), is(3));
    }

    @Test
    public void test_query_shortcut_loader_for_the_selected_tab() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        ShortcutLoader personalProfileShortcutLoader = mock(ShortcutLoader.class);
        ShortcutLoader workProfileShortcutLoader = mock(ShortcutLoader.class);
        final SparseArray<ShortcutLoader> shortcutLoaders = new SparseArray<>();
        shortcutLoaders.put(0, personalProfileShortcutLoader);
        shortcutLoaders.put(10, workProfileShortcutLoader);
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> shortcutLoaders.get(userHandle.getIdentifier(), null);
        Intent sendIntent = createSendTextIntent();
        sendIntent.setType(TEST_MIME_TYPE);

        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "work tab test"));
        waitForIdle();
        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        waitForIdle();

        verify(personalProfileShortcutLoader, times(1)).queryShortcuts(any());

        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        verify(workProfileShortcutLoader, times(1)).queryShortcuts(any());
    }

    private Intent createChooserIntent(Intent intent, Intent[] initialIntents) {
        Intent chooserIntent = new Intent();
        chooserIntent.setAction(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "some title");
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent);
        chooserIntent.setType("text/plain");
        if (initialIntents != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents);
        }
        return chooserIntent;
    }

    /* This is a "test of a test" to make sure that our inherited test class
     * is successfully configured to operate on the unbundled-equivalent
     * ChooserWrapperActivity.
     *
     * TODO: remove after unbundling is complete.
     */
    @Test
    public void testWrapperActivityHasExpectedConcreteType() {
        final ChooserActivity activity = mActivityRule.launchActivity(
                Intent.createChooser(new Intent("ACTION_FOO"), "foo"));
        waitForIdle();
        assertThat(activity).isInstanceOf(com.android.intentresolver.ChooserWrapperActivity.class);
    }

    private ResolveInfo createFakeResolveInfo() {
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.name = "FakeActivityName";
        ri.activityInfo.packageName = "fake.package.name";
        ri.activityInfo.applicationInfo = new ApplicationInfo();
        ri.activityInfo.applicationInfo.packageName = "fake.package.name";
        return ri;
    }

    private Intent createSendTextIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        sendIntent.setType("text/plain");
        return sendIntent;
    }

    private Intent createSendImageIntent(Uri imageThumbnail) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, imageThumbnail);
        sendIntent.setType("image/png");
        if (imageThumbnail != null) {
            ClipData.Item clipItem = new ClipData.Item(imageThumbnail);
            sendIntent.setClipData(new ClipData("Clip Label", new String[]{"image/png"}, clipItem));
        }

        return sendIntent;
    }

    private Intent createSendTextIntentWithPreview(String title, Uri imageThumbnail) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        sendIntent.putExtra(Intent.EXTRA_TITLE, title);
        if (imageThumbnail != null) {
            ClipData.Item clipItem = new ClipData.Item(imageThumbnail);
            sendIntent.setClipData(new ClipData("Clip Label", new String[]{"image/png"}, clipItem));
        }

        return sendIntent;
    }

    private Intent createSendUriIntentWithPreview(ArrayList<Uri> uris) {
        Intent sendIntent = new Intent();

        if (uris.size() > 1) {
            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris);
        } else {
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        }

        return sendIntent;
    }

    private Intent createViewTextIntent() {
        Intent viewIntent = new Intent();
        viewIntent.setAction(Intent.ACTION_VIEW);
        viewIntent.putExtra(Intent.EXTRA_TEXT, "testing intent viewing");
        return viewIntent;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTest(int numberOfResults) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfo(i));
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithOtherProfile(
            int numberOfResults) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            if (i == 0) {
                infoList.add(ResolverDataProvider.createResolvedComponentInfoWithOtherId(i));
            } else {
                infoList.add(ResolverDataProvider.createResolvedComponentInfo(i));
            }
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithOtherProfile(
            int numberOfResults, int userId) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            if (i == 0) {
                infoList.add(
                        ResolverDataProvider.createResolvedComponentInfoWithOtherId(i, userId));
            } else {
                infoList.add(ResolverDataProvider.createResolvedComponentInfo(i));
            }
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithUserId(
            int numberOfResults, int userId) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfoWithOtherId(i, userId));
        }
        return infoList;
    }

    private List<ChooserTarget> createDirectShareTargets(int numberOfResults, String packageName) {
        Icon icon = Icon.createWithBitmap(createBitmap());
        String testTitle = "testTitle";
        List<ChooserTarget> targets = new ArrayList<>();
        for (int i = 0; i < numberOfResults; i++) {
            ComponentName componentName;
            if (packageName.isEmpty()) {
                componentName = ResolverDataProvider.createComponentName(i);
            } else {
                componentName = new ComponentName(packageName, packageName + ".class");
            }
            ChooserTarget tempTarget = new ChooserTarget(
                    testTitle + i,
                    icon,
                    (float) (1 - ((i + 1) / 10.0)),
                    componentName,
                    null);
            targets.add(tempTarget);
        }
        return targets;
    }

    private void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private Bitmap createBitmap() {
        int width = 200;
        int height = 200;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPaint(paint);

        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setTextSize(14.f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Hi!", (width / 2.f), (height / 2.f), paint);

        return bitmap;
    }

    private List<ShareShortcutInfo> createShortcuts(Context context) {
        Intent testIntent = new Intent("TestIntent");

        List<ShareShortcutInfo> shortcuts = new ArrayList<>();
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut1")
                        .setIntent(testIntent).setShortLabel("label1").setRank(3).build(), // 0  2
                new ComponentName("package1", "class1")));
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut2")
                        .setIntent(testIntent).setShortLabel("label2").setRank(7).build(), // 1  3
                new ComponentName("package2", "class2")));
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut3")
                        .setIntent(testIntent).setShortLabel("label3").setRank(1).build(), // 2  0
                new ComponentName("package3", "class3")));
        shortcuts.add(new ShareShortcutInfo(
                new ShortcutInfo.Builder(context, "shortcut4")
                        .setIntent(testIntent).setShortLabel("label4").setRank(3).build(), // 3  2
                new ComponentName("package4", "class4")));

        return shortcuts;
    }

    private void markWorkProfileUserAvailable() {
        ChooserActivityOverrideData.getInstance().workProfileUserHandle = UserHandle.of(10);
    }

    private void setupResolverControllers(
            List<ResolvedComponentInfo> personalResolvedComponentInfos) {
        setupResolverControllers(personalResolvedComponentInfos, new ArrayList<>());
    }

    private void setupResolverControllers(
            List<ResolvedComponentInfo> personalResolvedComponentInfos,
            List<ResolvedComponentInfo> workResolvedComponentInfos) {
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resolverListController
                        .getResolversForIntentAsUser(
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.isA(List.class),
                                eq(UserHandle.SYSTEM)))
                .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .workResolverListController
                        .getResolversForIntentAsUser(
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.isA(List.class),
                                eq(UserHandle.SYSTEM)))
                .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .workResolverListController
                        .getResolversForIntentAsUser(
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.anyBoolean(),
                                Mockito.isA(List.class),
                                eq(UserHandle.of(10))))
                .thenReturn(new ArrayList<>(workResolvedComponentInfos));
    }

    private static GridRecyclerSpanCountMatcher withGridColumnCount(int columnCount) {
        return new GridRecyclerSpanCountMatcher(Matchers.is(columnCount));
    }

    private static class GridRecyclerSpanCountMatcher extends
            BoundedDiagnosingMatcher<View, RecyclerView> {

        private final Matcher<Integer> mIntegerMatcher;

        private GridRecyclerSpanCountMatcher(Matcher<Integer> integerMatcher) {
            super(RecyclerView.class);
            this.mIntegerMatcher = integerMatcher;
        }

        @Override
        protected void describeMoreTo(Description description) {
            description.appendText("RecyclerView grid layout span count to match: ");
            this.mIntegerMatcher.describeTo(description);
        }

        @Override
        protected boolean matchesSafely(RecyclerView view, Description mismatchDescription) {
            int spanCount = ((GridLayoutManager) view.getLayoutManager()).getSpanCount();
            if (this.mIntegerMatcher.matches(spanCount)) {
                return true;
            } else {
                mismatchDescription.appendText("RecyclerView grid layout span count was ")
                        .appendValue(spanCount);
                return false;
            }
        }
    }

    private void givenAppTargets(int appCount) {
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTest(appCount);
        setupResolverControllers(resolvedComponentInfos);
    }

    private void updateMaxTargetsPerRowResource(int targetsPerRow) {
        ChooserActivityOverrideData.getInstance().resources = Mockito.spy(
                InstrumentationRegistry.getInstrumentation().getContext().getResources());
        when(
                ChooserActivityOverrideData
                        .getInstance()
                        .resources
                        .getInteger(R.integer.config_chooser_max_targets_per_row))
                .thenReturn(targetsPerRow);
    }

    private SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>>
            createShortcutLoaderFactory() {
        SparseArray<Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>>> shortcutLoaders =
                new SparseArray<>();
        ChooserActivityOverrideData.getInstance().shortcutLoaderFactory =
                (userHandle, callback) -> {
                    Pair<ShortcutLoader, Consumer<ShortcutLoader.Result>> pair =
                            new Pair<>(mock(ShortcutLoader.class), callback);
                    shortcutLoaders.put(userHandle.getIdentifier(), pair);
                    return pair.first;
                };
        return shortcutLoaders;
    }
}
