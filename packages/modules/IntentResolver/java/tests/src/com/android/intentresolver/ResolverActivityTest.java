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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.intentresolver.MatcherUtils.first;
import static com.android.intentresolver.ResolverWrapperActivity.sOverrides;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.intentresolver.widget.ResolverDrawerLayout;
import com.android.internal.R;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolver activity instrumentation tests
 */
@RunWith(AndroidJUnit4.class)
public class ResolverActivityTest {
    protected Intent getConcreteIntentForLaunch(Intent clientIntent) {
        clientIntent.setClass(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext(),
                ResolverWrapperActivity.class);
        return clientIntent;
    }

    @Rule
    public ActivityTestRule<ResolverWrapperActivity> mActivityRule =
            new ActivityTestRule<>(ResolverWrapperActivity.class, false, false);

    @Before
    public void setup() {
        // TODO: use the other form of `adoptShellPermissionIdentity()` where we explicitly list the
        // permissions we require (which we'll read from the manifest at runtime).
        androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        sOverrides.reset();
    }

    @Test
    public void twoOptionsAndUserSelectsOne() throws InterruptedException {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        Espresso.registerIdlingResources(activity.getAdapter().getLabelIdlingResource());
        waitForIdle();

        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);
        onView(withText(toChoose.activityInfo.name))
                .perform(click());
        onView(withId(R.id.button_once))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Ignore // Failing - b/144929805
    @Test
    public void setMaxHeight() throws Exception {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        waitForIdle();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        final View viewPager = activity.findViewById(R.id.profile_pager);
        final int initialResolverHeight = viewPager.getHeight();

        activity.runOnUiThread(() -> {
            ResolverDrawerLayout layout = (ResolverDrawerLayout)
                    activity.findViewById(
                            R.id.contentPanel);
            ((ResolverDrawerLayout.LayoutParams) viewPager.getLayoutParams()).maxHeight
                    = initialResolverHeight - 1;
            // Force a relayout
            layout.invalidate();
            layout.requestLayout();
        });
        waitForIdle();
        assertThat("Drawer should be capped at maxHeight",
                viewPager.getHeight() == (initialResolverHeight - 1));

        activity.runOnUiThread(() -> {
            ResolverDrawerLayout layout = (ResolverDrawerLayout)
                    activity.findViewById(
                            R.id.contentPanel);
            ((ResolverDrawerLayout.LayoutParams) viewPager.getLayoutParams()).maxHeight
                    = initialResolverHeight + 1;
            // Force a relayout
            layout.invalidate();
            layout.requestLayout();
        });
        waitForIdle();
        assertThat("Drawer should not change height if its height is less than maxHeight",
                viewPager.getHeight() == initialResolverHeight);
    }

    @Ignore // Failing - b/144929805
    @Test
    public void setShowAtTopToTrue() throws Exception {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        waitForIdle();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        final View viewPager = activity.findViewById(R.id.profile_pager);
        final View divider = activity.findViewById(R.id.divider);
        final RelativeLayout profileView =
                (RelativeLayout) activity.findViewById(R.id.profile_button).getParent();
        assertThat("Drawer should show at bottom by default",
                profileView.getBottom() + divider.getHeight() == viewPager.getTop()
                        && profileView.getTop() > 0);

        activity.runOnUiThread(() -> {
            ResolverDrawerLayout layout = (ResolverDrawerLayout)
                    activity.findViewById(
                            R.id.contentPanel);
            layout.setShowAtTop(true);
        });
        waitForIdle();
        assertThat("Drawer should show at top with new attribute",
                profileView.getBottom() + divider.getHeight() == viewPager.getTop()
                        && profileView.getTop() == 0);
    }

    @Test
    public void hasLastChosenActivity() throws Exception {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos = createResolvedComponentsForTest(2);
        ResolveInfo toChoose = resolvedComponentInfos.get(0).getResolveInfoAt(0);

        setupResolverControllers(resolvedComponentInfos);
        when(sOverrides.resolverListController.getLastChosen())
                .thenReturn(resolvedComponentInfos.get(0).getResolveInfoAt(0));

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();

        // The other entry is filtered to the last used slot
        assertThat(activity.getAdapter().getCount(), is(1));
        assertThat(activity.getAdapter().getPlaceholderCount(), is(1));

        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        onView(withId(R.id.button_once)).perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    public void hasOtherProfileOneOption() throws Exception {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        markWorkProfileUserAvailable();

        ResolveInfo toChoose = personalResolvedComponentInfos.get(1).getResolveInfoAt(0);
        Intent sendIntent = createSendImageIntent();
        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        Espresso.registerIdlingResources(activity.getAdapter().getLabelIdlingResource());
        waitForIdle();

        // The other entry is filtered to the last used slot
        assertThat(activity.getAdapter().getCount(), is(1));

        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };
        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId= */ 10);
        // We pick the first one as there is another one in the work profile side
        onView(first(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name)))
                .perform(click());
        onView(withId(R.id.button_once))
                .perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    public void hasOtherProfileTwoOptionsAndUserSelectsOne() throws Exception {
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        setupResolverControllers(resolvedComponentInfos);

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        Espresso.registerIdlingResources(activity.getAdapter().getLabelIdlingResource());
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Confirm that the button bar is disabled by default
        onView(withId(R.id.button_once)).check(matches(not(isEnabled())));

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(2);

        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        onView(withId(R.id.button_once)).perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }


    @Test
    public void hasLastChosenActivityAndOtherProfile() throws Exception {
        // In this case we prefer the other profile and don't display anything about the last
        // chosen activity.
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        ResolveInfo toChoose = resolvedComponentInfos.get(1).getResolveInfoAt(0);

        setupResolverControllers(resolvedComponentInfos);
        when(sOverrides.resolverListController.getLastChosen())
                .thenReturn(resolvedComponentInfos.get(1).getResolveInfoAt(0));

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        Espresso.registerIdlingResources(activity.getAdapter().getLabelIdlingResource());
        waitForIdle();

        // The other entry is filtered to the other profile slot
        assertThat(activity.getAdapter().getCount(), is(2));

        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        // Confirm that the button bar is disabled by default
        onView(withId(R.id.button_once)).check(matches(not(isEnabled())));

        // Make a stable copy of the components as the original list may be modified
        List<ResolvedComponentInfo> stableCopy =
                createResolvedComponentsForTestWithOtherProfile(2);

        onView(withText(stableCopy.get(1).getResolveInfoAt(0).activityInfo.name))
                .perform(click());
        onView(withId(R.id.button_once)).perform(click());
        waitForIdle();
        assertThat(chosen[0], is(toChoose));
    }

    @Test
    public void testWorkTab_displayedWhenWorkProfileUserAvailable() {
        Intent sendIntent = createSendImageIntent();
        markWorkProfileUserAvailable();

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();

        onView(withId(R.id.tabs)).check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_hiddenWhenWorkProfileUserNotAvailable() {
        Intent sendIntent = createSendImageIntent();

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();

        onView(withId(R.id.tabs)).check(matches(not(isDisplayed())));
    }

    @Test
    public void testWorkTab_workTabListPopulatedBeforeGoingToTab() throws InterruptedException {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId = */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos,
                new ArrayList<>(workResolvedComponentInfos));
        Intent sendIntent = createSendImageIntent();
        markWorkProfileUserAvailable();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();

        assertThat(activity.getCurrentUserHandle().getIdentifier(), is(0));
        // The work list adapter must be populated in advance before tapping the other tab
        assertThat(activity.getWorkListAdapter().getCount(), is(4));
    }

    @Test
    public void testWorkTab_workTabUsesExpectedAdapter() {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        markWorkProfileUserAvailable();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());

        assertThat(activity.getCurrentUserHandle().getIdentifier(), is(10));
        assertThat(activity.getWorkListAdapter().getCount(), is(4));
    }

    @Test
    public void testWorkTab_personalTabUsesExpectedAdapter() {
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        markWorkProfileUserAvailable();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());

        assertThat(activity.getCurrentUserHandle().getIdentifier(), is(10));
        assertThat(activity.getPersonalListAdapter().getCount(), is(2));
    }

    @Test
    public void testWorkTab_workProfileHasExpectedNumberOfTargets() throws InterruptedException {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();

        onView(withText(R.string.resolver_work_tab))
                .perform(click());
        waitForIdle();
        assertThat(activity.getWorkListAdapter().getCount(), is(4));
    }

    @Test
    public void testWorkTab_selectingWorkTabAppOpensAppInWorkProfile() throws InterruptedException {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab))
                .perform(click());
        waitForIdle();
        onView(first(allOf(withText(workResolvedComponentInfos.get(0)
                .getResolveInfoAt(0).activityInfo.applicationInfo.name), isCompletelyDisplayed())))
                .perform(click());
        onView(withId(R.id.button_once))
                .perform(click());

        waitForIdle();
        assertThat(chosen[0], is(workResolvedComponentInfos.get(0).getResolveInfoAt(0)));
    }

    @Test
    public void testWorkTab_noPersonalApps_workTabHasExpectedNumberOfTargets()
            throws InterruptedException {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(1);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab))
                .perform(click());

        waitForIdle();
        assertThat(activity.getWorkListAdapter().getCount(), is(4));
    }

    @Test
    public void testWorkTab_headerIsVisibleInPersonalTab() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(1);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createOpenWebsiteIntent();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        TextView headerText = activity.findViewById(R.id.title);
        String initialText = headerText.getText().toString();
        assertFalse(initialText.isEmpty(), "Header text is empty.");
        assertThat(headerText.getVisibility(), is(View.VISIBLE));
    }

    @Test
    public void testWorkTab_switchTabs_headerStaysSame() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(1);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createOpenWebsiteIntent();

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        TextView headerText = activity.findViewById(R.id.title);
        String initialText = headerText.getText().toString();
        onView(withText(R.string.resolver_work_tab))
                .perform(click());

        waitForIdle();
        String currentText = headerText.getText().toString();
        assertThat(headerText.getVisibility(), is(View.VISIBLE));
        assertThat(String.format("Header text is not the same when switching tabs, personal profile"
                        + " header was %s but work profile header is %s", initialText, currentText),
                TextUtils.equals(initialText, currentText));
    }

    @Test
    public void testWorkTab_noPersonalApps_canStartWorkApps()
            throws InterruptedException {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3, /* userId= */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos = createResolvedComponentsForTest(4);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab))
                .perform(click());
        waitForIdle();
        onView(first(allOf(
                withText(workResolvedComponentInfos.get(0)
                        .getResolveInfoAt(0).activityInfo.applicationInfo.name),
                isDisplayed())))
                .perform(click());
        onView(withId(R.id.button_once))
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
        sOverrides.hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();
        onView(withId(R.id.contentPanel))
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
        sOverrides.isQuietModeEnabled = true;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withId(R.id.contentPanel))
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
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withId(R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_xProfileOff_noAppsAvailable_workOff_xProfileOffEmptyStateShown() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");
        sOverrides.isQuietModeEnabled = true;
        sOverrides.hasCrossProfileIntents = false;

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withId(R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testMiniResolver() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(1);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(1);
        // Personal profile only has a browser
        personalResolvedComponentInfos.get(0).getResolveInfoAt(0).handleAllWebDataURI = true;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withId(R.id.open_cross_profile)).check(matches(isDisplayed()));
    }

    @Test
    public void testMiniResolver_noCurrentProfileTarget() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(1);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();

        // Need to ensure mini resolver doesn't trigger here.
        assertNotMiniResolver();
    }

    private void assertNotMiniResolver() {
        try {
            onView(withId(R.id.open_cross_profile)).check(matches(isDisplayed()));
        } catch (NoMatchingViewException e) {
            return;
        }
        fail("Mini resolver present but shouldn't be");
    }

    @Test
    public void testWorkTab_noAppsAvailable_workOff_noAppsAvailableEmptyStateShown() {
        markWorkProfileUserAvailable();
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTest(3);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(0);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");
        sOverrides.isQuietModeEnabled = true;

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();
        onView(withId(R.id.contentPanel))
                .perform(swipeUp());
        onView(withText(R.string.resolver_work_tab)).perform(click());
        waitForIdle();

        onView(withText(R.string.resolver_no_work_apps_available))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testWorkTab_onePersonalTarget_emptyStateOnWorkTarget_autolaunch() {
        markWorkProfileUserAvailable();
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(2, /* userId */ 10);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets);
        sOverrides.hasCrossProfileIntents = false;
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
        Intent sendIntent = createSendImageIntent();
        sendIntent.setType("TestType");
        ResolveInfo[] chosen = new ResolveInfo[1];
        sOverrides.onSafelyStartCallback = targetInfo -> {
            chosen[0] = targetInfo.getResolveInfo();
            return true;
        };

        mActivityRule.launchActivity(sendIntent);
        waitForIdle();

        assertThat(chosen[0], is(personalResolvedComponentInfos.get(1).getResolveInfoAt(0)));
    }

    @Test
    public void testLayoutWithDefault_withWorkTab_neverShown() throws RemoteException {
        markWorkProfileUserAvailable();

        // In this case we prefer the other profile and don't display anything about the last
        // chosen activity.
        Intent sendIntent = createSendImageIntent();
        List<ResolvedComponentInfo> resolvedComponentInfos =
                createResolvedComponentsForTest(2);

        setupResolverControllers(resolvedComponentInfos);
        when(sOverrides.resolverListController.getLastChosen())
                .thenReturn(resolvedComponentInfos.get(1).getResolveInfoAt(0));

        final ResolverWrapperActivity activity = mActivityRule.launchActivity(sendIntent);
        Espresso.registerIdlingResources(activity.getAdapter().getLabelIdlingResource());
        waitForIdle();

        // The other entry is filtered to the last used slot
        assertThat(activity.getAdapter().hasFilteredItem(), is(false));
        assertThat(activity.getAdapter().getCount(), is(2));
        assertThat(activity.getAdapter().getPlaceholderCount(), is(2));
    }

    private Intent createSendImageIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        sendIntent.setType("image/jpeg");
        return sendIntent;
    }

    private Intent createOpenWebsiteIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_VIEW);
        sendIntent.setData(Uri.parse("https://google.com"));
        return sendIntent;
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

    private void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void markWorkProfileUserAvailable() {
        ResolverWrapperActivity.sOverrides.workProfileUserHandle = UserHandle.of(10);
    }

    private void setupResolverControllers(
            List<ResolvedComponentInfo> personalResolvedComponentInfos) {
        setupResolverControllers(personalResolvedComponentInfos, new ArrayList<>());
    }

    private void setupResolverControllers(
            List<ResolvedComponentInfo> personalResolvedComponentInfos,
            List<ResolvedComponentInfo> workResolvedComponentInfos) {
        when(sOverrides.resolverListController.getResolversForIntentAsUser(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class),
                eq(UserHandle.SYSTEM)))
                        .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(sOverrides.workResolverListController.getResolversForIntentAsUser(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class),
                eq(UserHandle.SYSTEM)))
                        .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(sOverrides.workResolverListController.getResolversForIntentAsUser(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class),
                eq(UserHandle.of(10))))
                        .thenReturn(new ArrayList<>(workResolvedComponentInfos));
    }
}
