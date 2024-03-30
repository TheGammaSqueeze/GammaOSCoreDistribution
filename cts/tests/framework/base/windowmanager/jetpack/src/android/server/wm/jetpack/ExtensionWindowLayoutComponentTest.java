/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.server.wm.jetpack.utils.ExtensionUtil.EXTENSION_VERSION_2;
import static android.server.wm.jetpack.utils.ExtensionUtil.assertEqualWindowLayoutInfo;
import static android.server.wm.jetpack.utils.ExtensionUtil.assumeHasDisplayFeatures;
import static android.server.wm.jetpack.utils.ExtensionUtil.getExtensionWindowLayoutInfo;
import static android.server.wm.jetpack.utils.ExtensionUtil.isExtensionVersionAtLeast;
import static android.server.wm.jetpack.utils.SidecarUtil.assumeSidecarSupportedDevice;
import static android.server.wm.jetpack.utils.SidecarUtil.getSidecarInterface;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.window.extensions.layout.FoldingFeature.STATE_FLAT;
import static androidx.window.extensions.layout.FoldingFeature.STATE_HALF_OPENED;
import static androidx.window.extensions.layout.FoldingFeature.TYPE_FOLD;
import static androidx.window.extensions.layout.FoldingFeature.TYPE_HINGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.IgnoreOrientationRequestSession;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.server.wm.jetpack.utils.TestValueCountJavaConsumer;
import android.server.wm.jetpack.utils.WindowExtensionTestRule;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.window.extensions.layout.DisplayFeature;
import androidx.window.extensions.layout.FoldingFeature;
import androidx.window.extensions.layout.WindowLayoutComponent;
import androidx.window.extensions.layout.WindowLayoutInfo;
import androidx.window.sidecar.SidecarDisplayFeature;
import androidx.window.sidecar.SidecarInterface;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Tests for the {@link androidx.window.extensions.layout.WindowLayoutComponent} implementation
 * provided on the device (and only if one is available).
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ExtensionWindowLayoutComponentTest
 */
@Presubmit
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ExtensionWindowLayoutComponentTest extends WindowManagerJetpackTestBase {

    private TestActivity mActivity;
    private WindowLayoutComponent mWindowLayoutComponent;
    private WindowLayoutInfo mWindowLayoutInfo;

    @Rule
    public final WindowExtensionTestRule mWindowExtensionTestRule =
            new WindowExtensionTestRule(WindowLayoutComponent.class);

    @Before
    @Override
    public void setUp() {
        super.setUp();
        mWindowLayoutComponent =
                (WindowLayoutComponent) mWindowExtensionTestRule.getExtensionComponent();
        assumeNotNull(mWindowLayoutComponent);
        mActivity = startActivityNewTask(TestActivity.class);
    }

    private Context createContextWithNonActivityWindow() {
        Display defaultDisplay = mContext.getSystemService(DisplayManager.class).getDisplay(
                DEFAULT_DISPLAY);
        Context windowContext = mContext.createWindowContext(defaultDisplay,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);

        mInstrumentation.runOnMainSync(() -> {
            final View view = new View(windowContext);
            WindowManager wm = windowContext.getSystemService(WindowManager.class);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            wm.addView(view, params);
        });
        return windowContext;
    }

    private void assumeExtensionVersionSupportsWindowContextLayout() {
        assumeTrue("This test should only be run on devices with version: ",
                isExtensionVersionAtLeast(EXTENSION_VERSION_2));
    }

    /**
     * Test adding and removing a window layout change listener.
     */
    @Test
    @ApiTest(apis = {
            "androidx.window.extensions.layout.WindowLayoutComponent#addWindowLayoutInfoListener"})
    public void testWindowLayoutComponent_onWindowLayoutChangeListener() throws Exception {
        // Set activity to portrait
        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_PORTRAIT);

        // Create the callback, onWindowLayoutChanged should only be called twice in this
        // test, not the third time when the orientation will change because the listener will be
        // removed.
        TestValueCountJavaConsumer<WindowLayoutInfo> windowLayoutInfoConsumer =
                new TestValueCountJavaConsumer<>();
        windowLayoutInfoConsumer.setCount(1);

        // Add window layout listener for mWindowToken - onWindowLayoutChanged should be called
        mWindowLayoutComponent.addWindowLayoutInfoListener(mActivity, windowLayoutInfoConsumer);
        // Initial registration invokes a consumer callback synchronously, clear the queue to
        // make sure there's no residual value or from the first orientation change.
        windowLayoutInfoConsumer.clearQueue();

        // Change the activity orientation - onWindowLayoutChanged should be called
        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_LANDSCAPE);

        // Check we have received exactly one layout update.
        assertNotNull(windowLayoutInfoConsumer.waitAndGet());

        // Remove the listener
        mWindowLayoutComponent.removeWindowLayoutInfoListener(windowLayoutInfoConsumer);
        windowLayoutInfoConsumer.clearQueue();

        // Change the activity orientation - onWindowLayoutChanged should NOT be called
        setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                ORIENTATION_PORTRAIT);
        WindowLayoutInfo lastValue = windowLayoutInfoConsumer.waitAndGet();
        assertNull(lastValue);
    }

    @Test
    @ApiTest(apis = {
            "androidx.window.extensions.layout.WindowLayoutComponent#addWindowLayoutInfoListener"})
    public void testWindowLayoutComponent_windowLayoutInfoListener() {
        TestValueCountJavaConsumer<WindowLayoutInfo> windowLayoutInfoConsumer =
                new TestValueCountJavaConsumer<>();
        // Test that adding and removing callback succeeds
        mWindowLayoutComponent.addWindowLayoutInfoListener(mActivity, windowLayoutInfoConsumer);
        mWindowLayoutComponent.removeWindowLayoutInfoListener(windowLayoutInfoConsumer);
    }

    @ApiTest(apis = {"androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    @Test
    public void testWindowLayoutComponent_providesWindowLayoutFromActivity()
            throws ExecutionException, InterruptedException, TimeoutException {
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);
        for (DisplayFeature displayFeature : mWindowLayoutInfo.getDisplayFeatures()) {
            // Check that the feature bounds are valid
            final Rect featureRect = displayFeature.getBounds();
            // Feature cannot have negative width or height
            assertHasNonNegativeDimensions(featureRect);
            // The feature cannot have zero area
            assertNotBothDimensionsZero(featureRect);
            // The feature cannot be outside the activity bounds
            assertTrue(getActivityBounds(mActivity).contains(featureRect));

            if (displayFeature instanceof FoldingFeature) {
                // Check that the folding feature has a valid type and state
                final FoldingFeature foldingFeature = (FoldingFeature) displayFeature;
                final int featureType = foldingFeature.getType();
                assertThat(featureType).isIn(Range.range(
                        TYPE_FOLD, BoundType.CLOSED,
                        TYPE_HINGE, BoundType.CLOSED));
                final int featureState = foldingFeature.getState();
                assertThat(featureState).isIn(Range.range(
                        STATE_FLAT, BoundType.CLOSED,
                        STATE_HALF_OPENED, BoundType.CLOSED));
            }
        }
    }

    @Test
    @ApiTest(apis = {
            "androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    public void testWindowLayoutComponent_providesWindowLayoutFromWindowContext()
            throws ExecutionException, InterruptedException, TimeoutException {
        assumeExtensionVersionSupportsWindowContextLayout();
        Context windowContext = createContextWithNonActivityWindow();

        mWindowLayoutInfo = getExtensionWindowLayoutInfo(windowContext);
        assumeHasDisplayFeatures(mWindowLayoutInfo);

        // Verify that window layouts and metrics are reasonable.
        WindowManager mWm = windowContext.getSystemService(WindowManager.class);
        final WindowMetrics currentMetrics = mWm.getCurrentWindowMetrics();

        for (DisplayFeature displayFeature : mWindowLayoutInfo.getDisplayFeatures()) {
            final Rect featureRect = displayFeature.getBounds();
            assertHasNonNegativeDimensions(featureRect);
            assertNotBothDimensionsZero(featureRect);
            assertTrue(currentMetrics.getBounds().contains(featureRect));
        }
    }

    @Test
    @ApiTest(apis = {
            "androidx.window.extensions.layout.WindowLayoutComponent#addWindowLayoutInfoListener"})
    public void testWindowLayoutComponent_windowLayoutMatchesBetweenActivityAndWindowContext()
            throws ExecutionException, InterruptedException, TimeoutException {
        assumeExtensionVersionSupportsWindowContextLayout();
        Context windowContext = createContextWithNonActivityWindow();

        WindowLayoutInfo windowLayoutInfoFromContext = getExtensionWindowLayoutInfo(windowContext);

        TestConfigChangeHandlingActivity configHandlingActivity = startFullScreenActivityNewTask(
                        TestConfigChangeHandlingActivity.class, null);
        WindowLayoutInfo windowLayoutInfoFromActivity = getExtensionWindowLayoutInfo(
                configHandlingActivity);

        assertEquals(windowLayoutInfoFromContext, windowLayoutInfoFromActivity);
    }

    @ApiTest(apis = {"androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    @Test
    public void testGetWindowLayoutInfo_configChanged_windowLayoutUpdates()
            throws InterruptedException {
        assumeSupportsRotation();
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);

        final TestConfigChangeHandlingActivity activity = startFullScreenActivityNewTask(
                        TestConfigChangeHandlingActivity.class, null);

        try (IgnoreOrientationRequestSession session =
                     new IgnoreOrientationRequestSession(false /* enable */)) {
            setActivityOrientationActivityHandlesOrientationChanges(activity,
                    ORIENTATION_PORTRAIT);
            final WindowLayoutInfo portraitWindowLayoutInfo = getExtensionWindowLayoutInfo(
                    activity);
            final Rect portraitBounds = getActivityBounds(activity);
            final Rect portraitMaximumBounds = getMaximumActivityBounds(activity);

            setActivityOrientationActivityHandlesOrientationChanges(activity,
                    ORIENTATION_LANDSCAPE);
            final WindowLayoutInfo landscapeWindowLayoutInfo = getExtensionWindowLayoutInfo(
                    activity);
            final Rect landscapeBounds = getActivityBounds(activity);
            final Rect landscapeMaximumBounds = getMaximumActivityBounds(activity);

            final boolean doesDisplayRotateForOrientation = doesDisplayRotateForOrientation(
                    portraitMaximumBounds, landscapeMaximumBounds);
            assertEqualWindowLayoutInfo(portraitWindowLayoutInfo, landscapeWindowLayoutInfo,
                    portraitBounds, landscapeBounds, doesDisplayRotateForOrientation);
        }
    }

    @ApiTest(apis = {"androidx.window.extensions.layout.WindowLayoutInfo#getDisplayFeatures"})
    @Test
    public void testGetWindowLayoutInfo_enterExitPip_windowLayoutInfoMatches()
            throws InterruptedException {
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);

        TestConfigChangeHandlingActivity configHandlingActivity = startActivityNewTask(
                        TestConfigChangeHandlingActivity.class, null);

        final WindowLayoutInfo initialInfo = getExtensionWindowLayoutInfo(
                configHandlingActivity);

        enterPipActivityHandlesConfigChanges(configHandlingActivity);
        exitPipActivityHandlesConfigChanges(configHandlingActivity);

        final WindowLayoutInfo updatedInfo = getExtensionWindowLayoutInfo(
                configHandlingActivity);

        assertEquals(initialInfo, updatedInfo);
    }

    /*
     * Similar to #testGetWindowLayoutInfo_configChanged_windowLayoutUpdates, here we trigger
     * rotations with a full screen activity on one Display Area, verify that WindowLayoutInfo
     * are updated with callbacks.
     */
    @FlakyTest(bugId = 254056760)
    @Test
    @ApiTest(apis = {
            "androidx.window.extensions.layout.WindowLayoutComponent#addWindowLayoutInfoListener",
            "androidx.window.extensions.layout.WindowLayoutComponent#removeWindowLayoutInfoListener"
    })
    public void testWindowLayoutComponent_updatesWindowLayoutFromContextAfterRotation()
            throws InterruptedException {
        assumeExtensionVersionSupportsWindowContextLayout();
        assumeSupportsRotation();

        try (IgnoreOrientationRequestSession session =
                     new IgnoreOrientationRequestSession(false /* enable */)) {
            final TestConfigChangeHandlingActivity activity = startFullScreenActivityNewTask(
                    TestConfigChangeHandlingActivity.class, null);

            setActivityOrientationActivityHandlesOrientationChanges(activity,
                    ORIENTATION_PORTRAIT);

            WindowLayoutInfo firstWindowLayout = getExtensionWindowLayoutInfo(activity);
            final Rect firstBounds = getActivityBounds(activity);
            final Rect firstMaximumBounds = getMaximumActivityBounds(activity);

            setActivityOrientationActivityHandlesOrientationChanges(activity,
                    ORIENTATION_LANDSCAPE);

            WindowLayoutInfo secondWindowLayout = getExtensionWindowLayoutInfo(activity);
            final Rect secondBounds = getActivityBounds(activity);
            final Rect secondMaximumBounds = getMaximumActivityBounds(activity);

            final boolean doesDisplayRotateForOrientation = doesDisplayRotateForOrientation(
                    firstMaximumBounds, secondMaximumBounds);
            assertEqualWindowLayoutInfo(firstWindowLayout, secondWindowLayout,
                    firstBounds, secondBounds, doesDisplayRotateForOrientation);
        }
    }

    @Test
    @ApiTest(apis = {
            "androidx.window.extensions.layout.WindowLayoutComponent#addWindowLayoutInfoListener"})
    public void testGetWindowLayoutInfo_windowRecreated_windowLayoutUpdates()
            throws  InterruptedException {
        assumeSupportsRotation();

        try (IgnoreOrientationRequestSession session =
                     new IgnoreOrientationRequestSession(false /* enable */)) {
            mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
            assumeHasDisplayFeatures(mWindowLayoutInfo);

            setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                    ORIENTATION_PORTRAIT);
            final WindowLayoutInfo portraitWindowLayoutInfo =
                    getExtensionWindowLayoutInfo(mActivity);
            final Rect portraitBounds = getActivityBounds(mActivity);
            final Rect portraitMaximumBounds = getMaximumActivityBounds(mActivity);

            setActivityOrientationActivityDoesNotHandleOrientationChanges(mActivity,
                    ORIENTATION_LANDSCAPE);
            final WindowLayoutInfo landscapeWindowLayoutInfo =
                    getExtensionWindowLayoutInfo(mActivity);
            final Rect landscapeBounds = getActivityBounds(mActivity);
            final Rect landscapeMaximumBounds = getMaximumActivityBounds(mActivity);

            final boolean doesDisplayRotateForOrientation = doesDisplayRotateForOrientation(
                    portraitMaximumBounds, landscapeMaximumBounds);
            assertEqualWindowLayoutInfo(portraitWindowLayoutInfo, landscapeWindowLayoutInfo,
                    portraitBounds, landscapeBounds, doesDisplayRotateForOrientation);
        }
    }

    /**
     * Tests that if sidecar is also present, then it returns the same display features as
     * extensions.
     */
    @Test
    public void testSidecarHasSameDisplayFeatures()
            throws ExecutionException, InterruptedException, TimeoutException {
        assumeSidecarSupportedDevice(mActivity);
        mWindowLayoutInfo = getExtensionWindowLayoutInfo(mActivity);
        assumeHasDisplayFeatures(mWindowLayoutInfo);

        // Retrieve and sort the extension folding features
        final List<FoldingFeature> extensionFoldingFeatures = new ArrayList<>(
                mWindowLayoutInfo.getDisplayFeatures())
                .stream()
                .filter(d -> d instanceof FoldingFeature)
                .map(d -> (FoldingFeature) d)
                .collect(Collectors.toList());

        // Retrieve and sort the sidecar display features in the same order as the extension
        // display features
        final SidecarInterface sidecarInterface = getSidecarInterface(mActivity);
        final List<SidecarDisplayFeature> sidecarDisplayFeatures = sidecarInterface
                .getWindowLayoutInfo(getActivityWindowToken(mActivity)).displayFeatures;

        // Check that the display features are the same
        assertEquals(extensionFoldingFeatures.size(), sidecarDisplayFeatures.size());
        final int nFeatures = extensionFoldingFeatures.size();
        if (nFeatures == 0) {
            return;
        }
        final boolean[] extensionDisplayFeatureMatched = new boolean[nFeatures];
        final boolean[] sidecarDisplayFeatureMatched = new boolean[nFeatures];
        for (int extensionIndex = 0; extensionIndex < nFeatures; extensionIndex++) {
            if (extensionDisplayFeatureMatched[extensionIndex]) {
                // A match has already been found for this extension folding feature
                continue;
            }
            final FoldingFeature extensionFoldingFeature = extensionFoldingFeatures
                    .get(extensionIndex);
            for (int sidecarIndex = 0; sidecarIndex < nFeatures; sidecarIndex++) {
                if (sidecarDisplayFeatureMatched[sidecarIndex]) {
                    // A match has already been found for this sidecar display feature
                    continue;
                }
                final SidecarDisplayFeature sidecarDisplayFeature = sidecarDisplayFeatures
                        .get(sidecarIndex);
                // Check that the bounds, type, and state match
                if (extensionFoldingFeature.getBounds().equals(sidecarDisplayFeature.getRect())
                        && extensionFoldingFeature.getType() == sidecarDisplayFeature.getType()
                        && areExtensionAndSidecarDeviceStateEqual(
                                extensionFoldingFeature.getState(),
                                sidecarInterface.getDeviceState().posture)) {
                    // Match found
                    extensionDisplayFeatureMatched[extensionIndex] = true;
                    sidecarDisplayFeatureMatched[sidecarIndex] = true;
                }
            }
        }

        // Check that a match was found for each display feature
        for (int i = 0; i < nFeatures; i++) {
            assertTrue(extensionDisplayFeatureMatched[i] && sidecarDisplayFeatureMatched[i]);
        }
    }
}
