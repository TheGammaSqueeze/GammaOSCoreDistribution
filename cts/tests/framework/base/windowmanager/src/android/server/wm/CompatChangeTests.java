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

package android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.provider.DeviceConfig.NAMESPACE_CONSTRAIN_DISPLAY_APIS;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Surface.ROTATION_90;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import android.server.wm.WindowManagerTestBase.FocusableActivity;
import android.util.Size;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.GestureNavRule;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

/**
 * The test is focused on compatibility changes that have an effect on WM logic, and tests that
 * enabling these changes has the correct effect.
 *
 * This is achieved by launching a custom activity with certain properties (e.g., a resizeable
 * portrait activity) that behaves in a certain way (e.g., enter size compat mode after resizing the
 * display) and enabling a compatibility change (e.g., {@link ActivityInfo#FORCE_RESIZE_APP}) that
 * changes that behavior (e.g., not enter size compat mode).
 *
 * The behavior without enabling a compatibility change is also tested as a baseline.
 *
 * <p>Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:CompatChangeTests
 */
@Presubmit
public final class CompatChangeTests extends MultiDisplayTestBase {
    private static final ComponentName RESIZEABLE_PORTRAIT_ACTIVITY =
            component(ResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_PORTRAIT_ACTIVITY =
            component(NonResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_LANDSCAPE_ACTIVITY =
            component(NonResizeableLandscapeActivity.class);
    private static final ComponentName NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY =
            component(NonResizeableNonFixedOrientationActivity.class);
    private static final ComponentName NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY =
            component(NonResizeableAspectRatioActivity.class);
    private static final ComponentName NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY =
            component(NonResizeableLargeAspectRatioActivity.class);
    private static final ComponentName SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY =
            component(SupportsSizeChangesPortraitActivity.class);

    // Fixed orientation min aspect ratio
    private static final float FIXED_ORIENTATION_MIN_ASPECT_RATIO = 1.03f;
    // The min aspect ratio of NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY (as defined in the manifest).
    private static final float ACTIVITY_MIN_ASPECT_RATIO = 1.6f;
    // The min aspect ratio of NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY (as defined in the
    // manifest). This needs to be higher than the aspect ratio of any device, which according to
    // CDD is at most 21:9.
    private static final float ACTIVITY_LARGE_MIN_ASPECT_RATIO = 4f;

    private static final float FLOAT_EQUALITY_DELTA = 0.01f;

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @ClassRule
    public static GestureNavRule GESTURE_NAV_RULE = new GestureNavRule();

    private DisplayMetricsSession mDisplayMetricsSession;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        GESTURE_NAV_RULE.assumeGestureNavigationMode();

        mDisplayMetricsSession =
                createManagedDisplayMetricsSession(DEFAULT_DISPLAY);
        createManagedLetterboxAspectRatioSession(FIXED_ORIENTATION_MIN_ASPECT_RATIO);
        createManagedConstrainDisplayApisFlagsSession();
    }

    /**
     * Test that a non-resizeable portrait activity enters size compat mode after resizing the
     * display.
     */
    @Test
    public void testSizeCompatForNonResizeableActivity() {
        runSizeCompatTest(
                NON_RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Test that a non-resizeable portrait activity doesn't enter size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_RESIZE_APP})
    public void testSizeCompatForNonResizeableActivityForceResizeEnabled() {
        runSizeCompatTest(
                NON_RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a resizeable portrait activity doesn't enter size compat mode after resizing
     * the display.
     */
    @Test
    public void testSizeCompatForResizeableActivity() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a non-resizeable portrait activity that supports size changes doesn't enter size
     * compat mode after resizing the display.
     */
    @Test
    public void testSizeCompatForSupportsSizeChangesActivity() {
        runSizeCompatTest(
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a resizeable portrait activity enters size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_NON_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_NON_RESIZE_APP})
    public void testSizeCompatForResizeableActivityForceNonResizeEnabled() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Test that a non-resizeable portrait activity that supports size changes enters size compat
     * mode after resizing the display, when the {@link ActivityInfo#FORCE_NON_RESIZE_APP} compat
     * change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_NON_RESIZE_APP})
    public void testSizeCompatForSupportsSizeChangesActivityForceNonResizeEnabled() {
        runSizeCompatTest(
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode results in sandboxed
     * Display APIs.
     */
    @Test
    public void testSandboxForNonResizableAspectRatioActivity() {
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ true);
        assertSandboxedByBounds(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY, /* isSandboxed= */
                true);
    }

     // =================
     // NEVER_SANDBOX test cases
     // =================
     // Validates that an activity forced into size compat mode never has sandboxing applied to the
     // max bounds. It is expected that an activity in size compat mode normally always has
     // sandboxing applied.

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the {@link ActivityInfo#NEVER_SANDBOX_DISPLAY_APIS} compat change is
     * enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.NEVER_SANDBOX_DISPLAY_APIS})
    public void testSandboxForNonResizableAspectRatioActivityNeverSandboxDisplayApisEnabled() {
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the
     * Display APIs sandboxed when the 'never_constrain_display_apis_all_packages' Device Config
     * flag is true.
     */
    @Test
    public void testSandboxForNonResizableActivityNeverSandboxDeviceConfigAllPackagesFlagTrue() {
        setNeverConstrainDisplayApisAllPackagesFlag("true");
        // Setting 'never_constrain_display_apis' as well to make sure it is ignored.
        setNeverConstrainDisplayApisFlag("com.android.other::");
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with an open ended range.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageUnboundedInNeverSandboxDeviceConfigFlag() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + "::");
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with a version range that matches the installed version of the package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageWithinRangeInNeverSandboxDeviceConfig() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        long version = getPackageVersion(activity);
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":" + String.valueOf(
                        version - 1) + ":" + String.valueOf(version + 1));
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with a version range that doesn't match the installed version of the package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageOutsideRangeInNeverSandboxDeviceConfig() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        long version = getPackageVersion(activity);
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":" + String.valueOf(
                        version + 1) + ":");
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag doesn't contain the
     * test package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageNotInNeverSandboxDeviceConfigFlag() {
        setNeverConstrainDisplayApisFlag("com.android.other::,com.android.other2::");
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag is empty.
     */
    @Test
    public void testSandboxForNonResizableActivityNeverSandboxDeviceConfigFlagEmpty() {
        setNeverConstrainDisplayApisFlag("");
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains an invalid
     * entry for the test package.
     */
    @Test
    public void testSandboxForNonResizableActivityInvalidEntryInNeverSandboxDeviceConfigFlag() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":::");
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ true);
    }

    // =================
    // ALWAYS_SANDBOX test cases
    // =================
    // Validates that an activity simply in letterbox mode has sandboxing applied to the max
    // bounds when ALWAYS_SANDBOX is set. Without the flag, we would not expect a letterbox activity
    // to be sandboxed, unless it is also eligible for size compat mode.

    /**
     * Test that a portrait activity not eligible for size compat mode does have the
     * Display APIs sandboxed when the {@link ActivityInfo#ALWAYS_SANDBOX_DISPLAY_APIS} compat
     * change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.ALWAYS_SANDBOX_DISPLAY_APIS})
    public void testSandboxForResizableActivityAlwaysSandboxDisplayApisEnabled() {
        runLetterboxSandboxTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* isSandboxed= */ true);
    }

    /**
     * Test that a portrait activity not eligible for size compat mode does not have the
     * Display APIs sandboxed when the 'always_constrain_display_apis' Device Config flag is empty.
     */
    @Test
    public void testSandboxResizableActivityAlwaysSandboxDeviceConfigFlagEmpty() {
        setAlwaysConstrainDisplayApisFlag("");
        runLetterboxSandboxTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* isSandboxed= */ false);
    }

    /**
     * Test that a portrait activity not eligible for size compat mode does have the Display
     * APIs sandboxed when the 'always_constrain_display_apis' Device Config flag contains the test
     * package.
     */
    @Test
    public void testSandboxResizableActivityPackageInAlwaysSandboxDeviceConfigFlag() {
        ComponentName activity = RESIZEABLE_PORTRAIT_ACTIVITY;
        setAlwaysConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + "::");
        runLetterboxSandboxTest(activity, /* isSandboxed= */ true);
    }

    /**
     * Test that only applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} has no effect on its
     * own. The aspect ratio of the activity should be the same as that of the task, which should be
     * in line with that of the display.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO})
    public void testOverrideMinAspectRatioMissingSpecificOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that only applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * its own without the presence of {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioMissingGeneralOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation is fixed to landscape.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioForLandscapeActivity() {
        runMinAspectRatioTest(NON_RESIZEABLE_LANDSCAPE_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation isn't fixed.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    @DisableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY})
    public void testOverrideMinAspectRatioForNonFixedOrientationActivityPortraitOnlyDisabled() {
        runMinAspectRatioTest(NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY, /* expected= */
                OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation is fixed to landscape.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    @DisableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY})
    public void testOverrideMinAspectRatioForLandscapeActivityPortraitOnlyDisabled() {
        runMinAspectRatioTest(NON_RESIZEABLE_LANDSCAPE_ACTIVITY, /* expected= */
                OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation isn't fixed.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioForNonFixedOrientationActivity() {
        runMinAspectRatioTest(NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} sets the min aspect
     * ratio to {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioLargeAspectRatio() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_MEDIUM} sets the min aspect
     * ratio to {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioMediumAspectRatio() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE);
    }

    /**
     * Test that applying multiple min aspect ratio overrides result in the largest one taking
     * effect.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioBothAspectRatios() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that the min aspect ratio of the activity as defined in the manifest is ignored if
     * there is an override for a larger min aspect ratio present (16:9 > 1.6).
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioActivityMinAspectRatioSmallerThanOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that the min aspect ratio of the activity as defined in the manifest is upheld if
     * there is an override for a smaller min aspect ratio present (3:2 < 1.6).
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioActivityMinAspectRatioLargerThanOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY,
                /* expected= */ ACTIVITY_MIN_ASPECT_RATIO);
    }

    /**
     * Launches the provided activity into size compat mode twice. The first time, the display
     * is resized to be half the size. The second time, the display is resized to be twice the
     * original size.
     *
     * @param activity                    the activity under test.
     * @param windowingMode               the launch windowing mode for the activity
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatTest(ComponentName activity, int windowingMode,
            boolean inSizeCompatModeAfterResize) {
        mWmState.computeState();
        WindowManagerState.DisplayContent originalDC = mWmState.getDisplay(DEFAULT_DISPLAY);

        runSizeCompatTest(activity, windowingMode, /* resizeRatio= */ 0.5,
                inSizeCompatModeAfterResize);
        waitForRestoreDisplay(originalDC);
        runSizeCompatTest(activity, windowingMode, /* resizeRatio= */ 2,
                inSizeCompatModeAfterResize);
    }

    /**
     * Launches the provided activity on the default display, initially not in size compat mode.
     * After resizing the display, verifies if activity is in size compat mode or not
     *
     * @param activity                    the activity under test
     * @param windowingMode               the launch windowing mode for the activity
     * @param resizeRatio                 the ratio to resize the display
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatTest(ComponentName activity, int windowingMode, double resizeRatio,
            boolean inSizeCompatModeAfterResize) {
        launchActivity(activity, windowingMode);

        assertSizeCompatMode(activity, /* expectedInSizeCompatMode= */ false);

        resizeDisplay(activity, resizeRatio);

        assertSizeCompatMode(activity, inSizeCompatModeAfterResize);
    }

    private void assertSizeCompatMode(ComponentName activity, boolean expectedInSizeCompatMode) {
        WindowManagerState.Activity activityContainer = mWmState.getActivity(activity);
        assertNotNull(activityContainer);
        if (expectedInSizeCompatMode) {
            assertTrue("The Window must be in size compat mode",
                    activityContainer.inSizeCompatMode());
        } else {
            assertFalse("The Window must not be in size compat mode",
                    activityContainer.inSizeCompatMode());
        }
    }

    private void runSizeCompatModeSandboxTest(ComponentName activity,
            boolean isSandboxed) {
        runSizeCompatModeSandboxTest(activity, isSandboxed,
                /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Similar to {@link #runSizeCompatTest(ComponentName, int, boolean)}, but the activity is
     * expected to be in size compat mode after resizing the display.
     *
     * @param activity                    the activity under test
     * @param isSandboxed                 when {@code true},
     * {@link android.app.WindowConfiguration#getMaxBounds()}
     *                                    are sandboxed to the activity bounds. Otherwise, they
     *                                    inherit the
     *                                    DisplayArea bounds
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatModeSandboxTest(ComponentName activity, boolean isSandboxed,
            boolean inSizeCompatModeAfterResize) {
        assertThat(getInitialDisplayAspectRatio()).isLessThan(ACTIVITY_LARGE_MIN_ASPECT_RATIO);

        mWmState.computeState();
        WindowManagerState.DisplayContent originalDC = mWmState.getDisplay(DEFAULT_DISPLAY);

        runSizeCompatTest(activity, WINDOWING_MODE_FULLSCREEN, /* resizeRatio= */ 0.5,
                inSizeCompatModeAfterResize);
        assertSandboxedByProvidesMaxBounds(activity, isSandboxed);
        waitForRestoreDisplay(originalDC);
        runSizeCompatTest(activity, WINDOWING_MODE_FULLSCREEN, /* resizeRatio=*/ 2,
                inSizeCompatModeAfterResize);
        assertSandboxedByProvidesMaxBounds(activity, isSandboxed);
    }

    /**
     * Similar to {@link #runSizeCompatModeSandboxTest(ComponentName, boolean)}, but the
     * activity is put into letterbox mode after resizing the display.
     *
     * @param activityName the activity under test
     * @param isSandboxed  when {@code true}, {@link android.app.WindowConfiguration#getMaxBounds()}
     *                     are sandboxed to the activity bounds. Otherwise, they inherit the
     *                     DisplayArea bounds
     */
    private void runLetterboxSandboxTest(ComponentName activityName, boolean isSandboxed) {
        assertThat(getInitialDisplayAspectRatio()).isLessThan(ACTIVITY_LARGE_MIN_ASPECT_RATIO);
        // Initialize display to portrait orientation.
        final RotationSession rotationSession = createManagedRotationSession();
        Size originalDisplaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        if (originalDisplaySize.getHeight() < originalDisplaySize.getWidth()) {
            // Device is landscape
            rotationSession.set(ROTATION_90);
        } else if (originalDisplaySize.getHeight() == originalDisplaySize.getWidth()) {
            // Device is square, so skip this test case (portrait activity will never be
            // letterboxed)
            return;
        }

        // Launch portrait activity
        launchActivity(activityName, WINDOWING_MODE_FULLSCREEN);

        // Change display to landscape should force portrait resizeable activity into letterbox.
        changeDisplayAspectRatioAndWait(activityName, /* aspectRatio= */ 2);
        assertSandboxedByProvidesMaxBounds(activityName, isSandboxed);
    }

    private void assertSandboxedByBounds(ComponentName activityName, boolean isSandboxed) {
        mWmState.computeState(new WaitForValidActivityState(activityName));
        final WindowManagerState.Activity activity = mWmState.getActivity(activityName);
        assertNotNull(activity);
        final Rect activityBounds = activity.getBounds();
        final Rect maxBounds = activity.getMaxBounds();
        WindowManagerState.DisplayArea tda = mWmState.getTaskDisplayArea(activityName);
        assertNotNull(tda);
        if (isSandboxed) {
            assertEquals(
                    "The window has max bounds sandboxed to the window bounds",
                    activityBounds, maxBounds);
        } else {
            assertEquals(
                    "The window is not sandboxed, with max bounds reflecting the DisplayArea",
                    tda.getBounds(), maxBounds);
        }
    }

    private void assertSandboxedByProvidesMaxBounds(ComponentName activityName, boolean isSandboxed) {
        mWmState.computeState(new WaitForValidActivityState(activityName));
        final WindowManagerState.Activity activity = mWmState.getActivity(activityName);
        assertNotNull(activity);
        if (isSandboxed) {
            assertTrue(
                    "The window should have max bounds sandboxed to the window bounds",
                    activity.providesMaxBounds());
        } else {
            assertFalse(
                    "The window should not be sandboxed; max bounds should reflect the DisplayArea",
                    activity.providesMaxBounds());
        }
    }

    private class ConstrainDisplayApisFlagsSession implements AutoCloseable {
        private Properties mInitialProperties;

        ConstrainDisplayApisFlagsSession() {
            runWithShellPermission(
                    () -> {
                        mInitialProperties = DeviceConfig.getProperties(
                                NAMESPACE_CONSTRAIN_DISPLAY_APIS);
                        try {
                            DeviceConfig.setProperties(new Properties.Builder(
                                    NAMESPACE_CONSTRAIN_DISPLAY_APIS).build());
                        } catch (Exception e) {
                        }
                    });
        }

        @Override
        public void close() {
            runWithShellPermission(
                    () -> {
                        try {
                            DeviceConfig.setProperties(mInitialProperties);
                        } catch (Exception e) {
                        }
                    });
        }
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    private ConstrainDisplayApisFlagsSession createManagedConstrainDisplayApisFlagsSession() {
        return mObjectTracker.manage(new ConstrainDisplayApisFlagsSession());
    }

    private void setNeverConstrainDisplayApisFlag(@Nullable String value) {
        setConstrainDisplayApisFlag("never_constrain_display_apis", value);
    }

    private void setNeverConstrainDisplayApisAllPackagesFlag(@Nullable String value) {
        setConstrainDisplayApisFlag("never_constrain_display_apis_all_packages", value);
    }

    private void setAlwaysConstrainDisplayApisFlag(@Nullable String value) {
        setConstrainDisplayApisFlag("always_constrain_display_apis", value);
    }

    private void setConstrainDisplayApisFlag(String flagName, @Nullable String value) {
        runWithShellPermission(
                () -> {
                    DeviceConfig.setProperty(NAMESPACE_CONSTRAIN_DISPLAY_APIS, flagName,
                            value, /* makeDefault= */ false);
                });
    }

    /**
     * Launches the provided activity and verifies that its min aspect ratio is equal to {@code
     * expected}.
     *
     * @param activity the activity under test.
     * @param expected the expected min aspect ratio in both portrait and landscape displays.
     */
    private void runMinAspectRatioTest(ComponentName activity, float expected) {
        launchActivity(activity);
        WindowManagerState.Activity activityContainer = mWmState.getActivity(activity);
        assertNotNull(activityContainer);
        assertEquals(expected,
                activityContainer.getMinAspectRatio(),
                FLOAT_EQUALITY_DELTA);
    }

    /**
     * Restore the display size and ensure configuration changes are complete.
     */
    private void restoreDisplay(ComponentName activity) {
        final Rect originalBounds = mWmState.getActivity(activity).getBounds();
        mDisplayMetricsSession.restoreDisplayMetrics();
        // Ensure configuration changes are complete after resizing the display.
        waitForActivityBoundsChanged(activity, originalBounds);
    }

    /**
     * Wait for the display to be restored to the original display content.
     */
    private void waitForRestoreDisplay(WindowManagerState.DisplayContent originalDisplayContent) {
        mWmState.waitForWithAmState(wmState -> {
            mDisplayMetricsSession.restoreDisplayMetrics();
            WindowManagerState.DisplayContent dc = mWmState.getDisplay(DEFAULT_DISPLAY);
            return dc.equals(originalDisplayContent);
        }, "waiting for display to be restored");
    }


    /**
     * Resize the display and ensure configuration changes are complete.
     */
    private void resizeDisplay(ComponentName activity, double sizeRatio) {
        Size originalDisplaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        final Rect originalBounds = mWmState.getActivity(activity).getBounds();
        mDisplayMetricsSession.changeDisplayMetrics(sizeRatio, /* densityRatio= */ 1);
        mWmState.computeState(new WaitForValidActivityState(activity));

        Size currentDisplaySize = mDisplayMetricsSession.getDisplayMetrics().getSize();
        assumeFalse("If a display size is capped, resizing may be a no-op",
                originalDisplaySize.equals(currentDisplaySize));

        // Ensure configuration changes are complete after resizing the display.
        waitForActivityBoundsChanged(activity, originalBounds);
    }

    /**
     * Resize the display to given aspect ratio in landscape orientation, and ensure configuration
     * changes are complete.
     */
    private void changeDisplayAspectRatioAndWait(ComponentName activity, double aspectRatio) {
        mWmState.computeState(new WaitForValidActivityState(activity));
        Size originalDisplaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        final Rect originalBounds = mWmState.getActivity(activity).getBounds();
        mDisplayMetricsSession.changeAspectRatio(aspectRatio,
                /* orientation= */ ORIENTATION_LANDSCAPE);
        mWmState.computeState(new WaitForValidActivityState(activity));

        Size currentDisplaySize = mDisplayMetricsSession.getDisplayMetrics().getSize();
        assumeFalse("If a display size is capped, resizing may be a no-op",
                originalDisplaySize.equals(currentDisplaySize));

        // Ensure configuration changes are complete after resizing the display.
        waitForActivityBoundsChanged(activity, originalBounds);
    }

    /**
     * Waits until the given activity has updated task bounds.
     */
    private void waitForActivityBoundsChanged(ComponentName activityName, Rect priorActivityBounds) {
        mWmState.waitForWithAmState(wmState -> {
            WindowManagerState.Activity activity = wmState.getActivity(activityName);
            return activity != null && !activity.getBounds().equals(priorActivityBounds);
        }, "checking activity bounds updated");
    }

    private float getInitialDisplayAspectRatio() {
        Size size = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        return Math.max(size.getHeight(), size.getWidth())
                / (float) (Math.min(size.getHeight(), size.getWidth()));
    }

    private void launchActivity(ComponentName activity) {
        launchActivity(activity, WINDOWING_MODE_FULLSCREEN);
    }

    private void launchActivity(ComponentName activity, int windowingMode) {
        getLaunchActivityBuilder()
                .setDisplayId(DEFAULT_DISPLAY)
                .setTargetActivity(activity)
                .setWindowingMode(windowingMode)
                .setUseInstrumentation()
                .allowMultipleInstances(false)
                .execute();
    }

    private long getPackageVersion(ComponentName activity) {
        try {
            return mContext.getPackageManager().getPackageInfo(activity.getPackageName(),
                    /* flags= */ 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static ComponentName component(Class<? extends Activity> activity) {
        return new ComponentName(getInstrumentation().getContext(), activity);
    }

    public static class ResizeablePortraitActivity extends FocusableActivity {
    }

    public static class NonResizeablePortraitActivity extends FocusableActivity {
    }

    public static class NonResizeableLandscapeActivity extends FocusableActivity {
    }

    public static class NonResizeableNonFixedOrientationActivity extends FocusableActivity {
    }

    public static class NonResizeableAspectRatioActivity extends FocusableActivity {
    }

    public static class NonResizeableLargeAspectRatioActivity extends FocusableActivity {
    }

    public static class SupportsSizeChangesPortraitActivity extends FocusableActivity {
    }
}
