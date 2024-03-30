/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.server.wm.MockImeHelper.createManagedMockImeSession;
import static android.view.SurfaceControlViewHost.SurfacePackage;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.os.Binder;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.server.wm.scvh.Components;
import android.server.wm.shared.ICrossProcessSurfaceControlViewHostTestService;
import android.util.ArrayMap;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.WidgetTestUtils;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ensure end-to-end functionality of SurfaceControlViewHost.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:SurfaceControlViewHostTests
 */
@Presubmit
public class SurfaceControlViewHostTests extends ActivityManagerTestBase implements SurfaceHolder.Callback {
    private final ActivityTestRule<ConfigChangeHandlingActivity> mActivityRule =
        new ActivityTestRule<>(ConfigChangeHandlingActivity.class);

    private ActivityScenario<ConfigChangeHandlingActivity> mScenario;

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private SurfaceView mSurfaceView;
    private ViewGroup mViewParent;

    private SurfaceControlViewHost mVr;
    private View mEmbeddedView;
    private WindowManager.LayoutParams mEmbeddedLayoutParams;

    private volatile boolean mClicked = false;
    private volatile boolean mPopupClicked = false;
    private PopupWindow mPopupWindow;

    private SurfaceControlViewHost.SurfacePackage mRemoteSurfacePackage;

    private final Map<String,
        FutureConnection<ICrossProcessSurfaceControlViewHostTestService>> mConnections =
            new ArrayMap<>();
    private ICrossProcessSurfaceControlViewHostTestService mTestService = null;
    private static final long TIMEOUT_MS = 3000L;

    /*
     * Configurable state to control how the surfaceCreated callback
     * will initialize the embedded view hierarchy.
     */
    int mEmbeddedViewWidth = 100;
    int mEmbeddedViewHeight = 100;

    private static final int DEFAULT_SURFACE_VIEW_WIDTH = 100;
    private static final int DEFAULT_SURFACE_VIEW_HEIGHT = 100;
    MockImeSession mImeSession;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mClicked = false;
        mEmbeddedLayoutParams = null;
        mRemoteSurfacePackage = null;

        if (supportsInstallableIme()) {
            mImeSession = createManagedMockImeSession(this);
        }

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.launchActivity(null);
        mInstrumentation.waitForIdleSync();
    }

    @After
    public void tearDown() throws Throwable {
        for (FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connection :
                 mConnections.values()) {
            mInstrumentation.getContext().unbindService(connection);
        }
        mConnections.clear();
    }

    private void addSurfaceView(int width, int height) throws Throwable {
        addSurfaceView(width, height, true);
    }

    private void addSurfaceView(int width, int height, boolean onTop) throws Throwable {
        addSurfaceView(width, height, onTop, 0 /* leftMargin */, 0 /* topMargin */);
    }

    private void addSurfaceView(int width, int height, boolean onTop, int leftMargin, int topMargin)
            throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(onTop);
            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    width, height, Gravity.LEFT | Gravity.TOP);
            lp.leftMargin = leftMargin;
            lp.topMargin = topMargin;
            content.addView(mSurfaceView, lp);
            mViewParent = content;
            mActivity.setContentView(content,
                    new ViewGroup.LayoutParams(width + leftMargin, height + topMargin));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    private void addViewToSurfaceView(SurfaceView sv, View v, int width, int height) {
        mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(), sv.getHostToken());


        if (mEmbeddedLayoutParams == null) {
            mVr.setView(v, width, height);
        } else {
            mVr.setView(v, mEmbeddedLayoutParams);
        }

        sv.setChildSurfacePackage(mVr.getSurfacePackage());

        assertEquals(v, mVr.getView());
    }

    private void requestSurfaceViewFocus() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.setFocusableInTouchMode(true);
            mSurfaceView.requestFocusFromTouch();
        });
    }

    private void assertWindowFocused(final View view, boolean hasWindowFocus) {
        final CountDownLatch latch = new CountDownLatch(1);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                view, () -> {
                    if (view.hasWindowFocus() == hasWindowFocus) {
                        latch.countDown();
                        return;
                    }
                    view.getViewTreeObserver().addOnWindowFocusChangeListener(
                            new ViewTreeObserver.OnWindowFocusChangeListener() {
                                @Override
                                public void onWindowFocusChanged(boolean newFocusState) {
                                    if (hasWindowFocus == newFocusState) {
                                        view.getViewTreeObserver()
                                                .removeOnWindowFocusChangeListener(this);
                                        latch.countDown();
                                    }
                                }
                            });
                }
        );

        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                fail();
            }
        } catch (InterruptedException e) {
            fail();
        }
    }

    private void waitUntilEmbeddedViewDrawn() throws Throwable {
        // We use frameCommitCallback because we need to ensure HWUI
        // has actually queued the frame.
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedView.getViewTreeObserver().registerFrameCommitCallback(
                latch::countDown);
            mEmbeddedView.invalidate();
        });
        assertTrue(latch.await(1, TimeUnit.SECONDS));

    }

    private String getTouchableRegionFromDump() {
        final String output = runCommandAndPrintOutput("dumpsys window windows");
        boolean foundWindow = false;
        for (String line : output.split("\\n")) {
            if (line.contains("ConfigChangeHandlingActivity")) {
                foundWindow = true;
            }
            if (foundWindow && line.contains("touchable region")) {
                return line;
            }
        }
        return null;
    }

    private boolean waitForTouchableRegionChanged(String originalTouchableRegion) {
        int retries = 0;
        while (retries < 50) {
            if (getTouchableRegionFromDump() != originalTouchableRegion) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
        }
        return false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mTestService == null) {
            addViewToSurfaceView(mSurfaceView, mEmbeddedView,
                mEmbeddedViewWidth, mEmbeddedViewHeight);
        } else if (mRemoteSurfacePackage == null) {
            try {
                mRemoteSurfacePackage = mTestService.getSurfacePackage(mSurfaceView.getHostToken());
            } catch (Exception e) {
            }
            mSurfaceView.setChildSurfacePackage(mRemoteSurfacePackage);
        } else {
            mSurfaceView.setChildSurfacePackage(mRemoteSurfacePackage);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    @Test
    public void testEmbeddedViewReceivesInput() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testEmbeddedViewReceivesRawInputCoordinatesInDisplaySpace() throws Throwable {
        final UiAutomation uiAutomation = mInstrumentation.getUiAutomation();
        final int viewX = DEFAULT_SURFACE_VIEW_WIDTH / 2;
        final int viewY = DEFAULT_SURFACE_VIEW_HEIGHT / 2;

        // Verify the input coordinates received by the embedded view in three different locations.
        for (int i = 0; i < 3; i++) {
            final List<MotionEvent> events = new ArrayList<>();
            mEmbeddedView = new View(mActivity);
            mEmbeddedView.setOnTouchListener((v, e) -> events.add(e));

            // Add a margin to the SurfaceView to offset the embedded view's location on the screen.
            final int leftMargin = i * 20;
            final int topMargin = i * 10;
            addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, true /*onTop*/,
                    leftMargin, topMargin);
            mInstrumentation.waitForIdleSync();
            waitUntilEmbeddedViewDrawn();

            final int[] surfaceLocation = new int[2];
            mSurfaceView.getLocationOnScreen(surfaceLocation);

            final int displayX = surfaceLocation[0] + viewX;
            final int displayY = surfaceLocation[1] + viewY;
            final long downTime = SystemClock.uptimeMillis();
            CtsTouchUtils.injectDownEvent(uiAutomation, downTime, displayX, displayY,
                    null /*eventInjectionListener*/);
            CtsTouchUtils.injectUpEvent(uiAutomation, downTime, true /*useCurrentEventTime*/,
                    displayX, displayY, null /*eventInjectionListener*/);

            assertEquals("Expected to capture all injected events.", 2, events.size());
            final float epsilon = 0.001f;
            events.forEach(e -> {
                assertEquals("Expected to get the x coordinate in View space.",
                        viewX, e.getX(), epsilon);
                assertEquals("Expected to get the y coordinate in View space.",
                        viewY, e.getY(), epsilon);
                assertEquals("Expected to get raw x coordinate in Display space.",
                        displayX, e.getRawX(), epsilon);
                assertEquals("Expected to get raw y coordinate in Display space.",
                        displayY, e.getRawY(), epsilon);
            });
        }
    }

    private static int getGlEsVersion(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
        if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
            return getMajorVersion(configInfo.reqGlEsVersion);
        } else {
            return 1; // Lack of property means OpenGL ES version 1
        }
    }

    /** @see FeatureInfo#getGlEsVersion() */
    private static int getMajorVersion(int glEsVersion) {
        return ((glEsVersion & 0xffff0000) >> 16);
    }

    @Test
    @RequiresDevice
    @FlakyTest(bugId = 152103238)
    public void testEmbeddedViewIsHardwareAccelerated() throws Throwable {
        // Hardware accel may not be supported on devices without GLES 2.0
        if (getGlEsVersion(mActivity) < 2) {
            return;
        }
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        // If we don't support hardware acceleration on the main activity the embedded
        // view also won't be.
        if (!mSurfaceView.isHardwareAccelerated()) {
            return;
        }

        assertTrue(mEmbeddedView.isHardwareAccelerated());
    }

    @Test
    public void testEmbeddedViewResizes() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        final int bigEdgeLength = mEmbeddedViewWidth * 3;

        // We make the SurfaceView more than twice as big as the embedded view
        // so that a touch in the middle of the SurfaceView won't land
        // on the embedded view.
        addSurfaceView(bigEdgeLength, bigEdgeLength);
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
                mVr.relayout(bigEdgeLength, bigEdgeLength);
        });
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // But after the click should hit.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testEmbeddedViewReleases() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);

        mActivityRule.runOnUiThread(() -> {
            mVr.release();
        });
        mInstrumentation.waitForIdleSync();

        mClicked = false;
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);
    }

    @Test
    public void testDisableInputTouch() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        mEmbeddedLayoutParams = new WindowManager.LayoutParams(mEmbeddedViewWidth,
            mEmbeddedViewHeight, WindowManager.LayoutParams.TYPE_APPLICATION, 0,
            PixelFormat.OPAQUE);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
                mEmbeddedLayoutParams.flags |= FLAG_NOT_TOUCHABLE;
                mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
                mEmbeddedLayoutParams.flags &= ~FLAG_NOT_TOUCHABLE;
                mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // When surface view is no longer focused, it should transfer focus back to the host window.
        mActivityRule.runOnUiThread(() -> mSurfaceView.setFocusable(false));
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    @Test
    public void testImeVisible() throws Throwable {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());
        EditText editText = new EditText(mActivity);

        mEmbeddedView = editText;
        editText.setBackgroundColor(Color.BLUE);
        editText.setPrivateImeOptions("Hello reader! This is a random string");
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        final ImeEventStream stream = mImeSession.openEventStream();
        expectEvent(stream, editorMatcher("onStartInputView",
            editText.getPrivateImeOptions()), TIMEOUT_MS);
    }

    @Test
    public void testNotFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mEmbeddedLayoutParams = new WindowManager.LayoutParams(mEmbeddedViewWidth,
                mEmbeddedViewHeight, WindowManager.LayoutParams.TYPE_APPLICATION, 0,
                PixelFormat.OPAQUE);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedLayoutParams.flags |= FLAG_NOT_FOCUSABLE;
            mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, nothing should happen since the embedded view is not
        // focusable.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    private static class SurfaceCreatedCallback implements SurfaceHolder.Callback {
        private final CountDownLatch mSurfaceCreated;
        SurfaceCreatedCallback(CountDownLatch latch) {
            mSurfaceCreated = latch;
        }
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceCreated.countDown();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {}

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    }

    @Test
    public void testCanCopySurfacePackage() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT));
            mSurfaceView.getHolder().addCallback(new SurfaceCreatedCallback(surfaceCreated));

            // Create an embedded view.
            mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    mSurfaceView.getHostToken());
            mEmbeddedView = new Button(mActivity);
            mEmbeddedView.setOnClickListener((View v) -> mClicked = true);
            mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);

        });
        surfaceCreated.await();

        // Make a copy of the SurfacePackage and release the original package.
        SurfacePackage surfacePackage = mVr.getSurfacePackage();
        SurfacePackage copy = new SurfacePackage(surfacePackage);
        surfacePackage.release();
        mSurfaceView.setChildSurfacePackage(copy);

        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Check if SurfacePackage copy remains valid even though the original package has
        // been released.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testTransferSurfacePackage() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        CountDownLatch surface2Created = new CountDownLatch(1);
        CountDownLatch viewDetached = new CountDownLatch(1);
        AtomicReference<SurfacePackage> surfacePackageRef = new AtomicReference<>(null);
        AtomicReference<SurfacePackage> surfacePackageCopyRef = new AtomicReference<>(null);
        AtomicReference<SurfaceView> secondSurfaceRef = new AtomicReference<>(null);

        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                    DEFAULT_SURFACE_VIEW_HEIGHT, Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                    DEFAULT_SURFACE_VIEW_HEIGHT));
            mSurfaceView.getHolder().addCallback(new SurfaceCreatedCallback(surfaceCreated));

            // Create an embedded view.
            mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    mSurfaceView.getHostToken());
            mEmbeddedView = new Button(mActivity);
            mEmbeddedView.setOnClickListener((View v) -> mClicked = true);
            mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);

            SurfacePackage surfacePackage = mVr.getSurfacePackage();
            surfacePackageRef.set(surfacePackage);
            surfacePackageCopyRef.set(new SurfacePackage(surfacePackage));

            // Assign the surface package to the first surface
            mSurfaceView.setChildSurfacePackage(surfacePackage);


            // Create the second surface view to which we'll assign the surface package copy
            SurfaceView secondSurface = new SurfaceView(mActivity);
            secondSurfaceRef.set(secondSurface);

            mSurfaceView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    viewDetached.countDown();
                }
            });

            secondSurface.getHolder().addCallback(new SurfaceCreatedCallback(surface2Created));

        });
        surfaceCreated.await();

        // Add the second surface view and assign it the surface package copy
        mActivityRule.runOnUiThread(() -> {
            ViewGroup content = (ViewGroup) mSurfaceView.getParent();
            content.addView(secondSurfaceRef.get(),
                    new FrameLayout.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                            DEFAULT_SURFACE_VIEW_HEIGHT, Gravity.TOP | Gravity.LEFT));
            secondSurfaceRef.get().setZOrderOnTop(true);
            surfacePackageRef.get().release();
            secondSurfaceRef.get().setChildSurfacePackage(surfacePackageCopyRef.get());

            content.removeView(mSurfaceView);
        });

        // Wait for the first surface to be removed
        surface2Created.await();
        viewDetached.await();

        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Check if SurfacePackage copy remains valid even though the original package has
        // been released and the original surface view removed.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule,
                secondSurfaceRef.get());
        assertTrue(mClicked);
    }

    @Test
    public void testCanReplaceSurfacePackage() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        {
            CountDownLatch surfaceCreated = new CountDownLatch(1);
            mActivityRule.runOnUiThread(() -> {
                final FrameLayout content = new FrameLayout(mActivity);
                mSurfaceView = new SurfaceView(mActivity);
                mSurfaceView.setZOrderOnTop(true);
                content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                        DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, 
                        Gravity.LEFT | Gravity.TOP));
                mActivity.setContentView(content, new ViewGroup.LayoutParams(
                        DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT));
                mSurfaceView.getHolder().addCallback(new SurfaceCreatedCallback(surfaceCreated));

                // Create an embedded view without click handling.
                mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                        mSurfaceView.getHostToken());
                mEmbeddedView = new Button(mActivity);
                mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);

            });
            surfaceCreated.await();
            mSurfaceView.setChildSurfacePackage(mVr.getSurfacePackage());
            mInstrumentation.waitForIdleSync();
            waitUntilEmbeddedViewDrawn();
        }

        {
            CountDownLatch hostReady = new CountDownLatch(1);
            // Create a second surface view and wait for its surface to be created.
            mActivityRule.runOnUiThread(() -> {
                // Create an embedded view.
                mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                        mSurfaceView.getHostToken());
                mEmbeddedView = new Button(mActivity);
                mEmbeddedView.setOnClickListener((View v) -> mClicked = true);
                mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);
                hostReady.countDown();

            });
            hostReady.await();
            mSurfaceView.setChildSurfacePackage(mVr.getSurfacePackage());
            mInstrumentation.waitForIdleSync();
            waitUntilEmbeddedViewDrawn();
        }

        // Check to see if the click went through - this only would happen if the surface package
        // was replaced
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    class MotionRecordingSurfaceView extends SurfaceView {
        boolean mGotEvent = false;
        MotionRecordingSurfaceView(Context c) {
            super(c);
        }
        public boolean onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            synchronized (this) {
                mGotEvent = true;
            }
            return true;
        }
        boolean gotEvent() {
            synchronized (this) {
                return mGotEvent;
            }
        }
        void reset() {
            synchronized (this) {
                mGotEvent = false;
            }
        }
    }

    class TouchPunchingView extends View {
        public TouchPunchingView(Context context) {
            super(context);
        }

        void punchHoleInTouchableRegion() {
            getRootSurfaceControl().setTouchableRegion(new Region());
        }
    }

    private void addMotionRecordingSurfaceView(int width, int height) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new MotionRecordingSurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                width, height, Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(width, height));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    class ForwardingSurfaceView extends SurfaceView {
        SurfaceControlViewHost.SurfacePackage mPackage;

        ForwardingSurfaceView(Context c) {
            super(c);
        }

        @Override
        protected void onDetachedFromWindow() {
            mPackage.notifyDetachedFromWindow();
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            mPackage.notifyConfigurationChanged(newConfig);
        }

        @Override
        public void setChildSurfacePackage(SurfaceControlViewHost.SurfacePackage p) {
            super.setChildSurfacePackage(p);
            mPackage = p;
        }
    }

    class DetachRecordingView extends View {
        boolean mDetached = false;
        DetachRecordingView(Context c) {
            super(c);
        }

        @Override
        protected void onDetachedFromWindow() {
            mDetached = true;
        }
    }

    class ConfigRecordingView extends View {
        CountDownLatch mLatch;
        ConfigRecordingView(Context c, CountDownLatch latch) {
            super(c);
            mLatch = latch;
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            mLatch.countDown();
        }
    }

    private void addForwardingSurfaceView(int width, int height) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new ForwardingSurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                width, height, Gravity.LEFT | Gravity.TOP));
            mViewParent = content;
            mActivity.setContentView(content, new ViewGroup.LayoutParams(width, height));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    @Test
    public void testEmbeddedViewCanSetTouchableRegion() throws Throwable {
        TouchPunchingView tpv;
        mEmbeddedView = tpv = new TouchPunchingView(mActivity);

        addMotionRecordingSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        mInstrumentation.waitForIdleSync();

        MotionRecordingSurfaceView mrsv = (MotionRecordingSurfaceView)mSurfaceView;
        assertFalse(mrsv.gotEvent());
        mActivityRule.runOnUiThread(() -> {
            tpv.punchHoleInTouchableRegion();
        });
        mInstrumentation.waitForIdleSync();
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        mInstrumentation.waitForIdleSync();
        assertTrue(mrsv.gotEvent());
    }

    @Test
    public void forwardDetachedFromWindow() throws Throwable {
        DetachRecordingView drv = new DetachRecordingView(mActivity);
        mEmbeddedView = drv;
        addForwardingSurfaceView(100, 100);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        
        assertFalse(drv.mDetached);
        mActivityRule.runOnUiThread(() -> {
            mViewParent.removeView(mSurfaceView);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(drv.mDetached);
    }

    @Test
    public void forwardConfigurationChange() throws Throwable {
        if (!supportsOrientationRequest()) {
            return;
        }
        final CountDownLatch embeddedConfigLatch = new CountDownLatch(1);
        ConfigRecordingView crv = new ConfigRecordingView(mActivity, embeddedConfigLatch);
        mEmbeddedView = crv;
        addForwardingSurfaceView(100, 100);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        mActivityRule.runOnUiThread(() -> {
            int orientation = mActivity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            } else {
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }
            mActivity.setRequestedOrientation(orientation);
        });
        embeddedConfigLatch.await(3, TimeUnit.SECONDS);
        mInstrumentation.waitForIdleSync();
        mActivityRule.runOnUiThread(() -> {
                assertEquals(mEmbeddedView.getResources().getConfiguration().orientation,
                             mSurfaceView.getResources().getConfiguration().orientation);
        });
    }

    @Test
    public void testEmbeddedViewReceivesInputOnBottom() throws Throwable {
        // Close the activity that was launched by setup
        mActivityRule.finishActivity();

        // Launch activity in fullscreen windowing mode
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(mInstrumentation.getTargetContext(), ConfigChangeHandlingActivity.class);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        mScenario = ActivityScenario.launch(intent, options.toBundle());
        mScenario.onActivity(activity -> {
            mActivity = activity;
        });

        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, false);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // We should receive no input until we punch a hole
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, mSurfaceView);
        mInstrumentation.waitForIdleSync();
        assertFalse(mClicked);

        String originalRegion = getTouchableRegionFromDump();

        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region(0,0,1,1));
        });
        mInstrumentation.waitForIdleSync();
        // ViewRootImpl sends the touchable region to the WM via a one-way call, which is great
        // for performance...however not so good for testability, we have no way
        // to verify it has arrived! It doesn't make so much sense to bloat
        // the system image size with a completion callback for just this one test
        // so we settle for some inelegant spin-polling on the WM dump.
        // In the future when we revisit WM/Client interface and transactionalize
        // everything, we should have a standard way to wait on the completion of async
        // operations
        waitForTouchableRegionChanged(originalRegion);

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, mSurfaceView);
        mInstrumentation.waitForIdleSync();
        assertTrue(mClicked);
    }

    private ICrossProcessSurfaceControlViewHostTestService getService() throws Exception {
        return mConnections.computeIfAbsent("android.server.wm.scvh", this::connect).get(TIMEOUT_MS);
    }

    private static ComponentName repackage(String packageName, ComponentName baseComponent) {
        return new ComponentName(packageName, baseComponent.getClassName());
    }

    private FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connect(
            String packageName) {
        FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connection =
                new FutureConnection<>(
                    ICrossProcessSurfaceControlViewHostTestService.Stub::asInterface);
        Intent intent = new Intent();
        intent.setComponent(repackage(packageName,
            Components.CrossProcessSurfaceControlViewHostTestService.COMPONENT));
        assertTrue(mInstrumentation.getContext().bindService(intent,
            connection, Context.BIND_AUTO_CREATE));
        return connection;
    }

    @Test
    public void testHostInputTokenAllowsObscuredTouches() throws Throwable {
        // Close the activity that was launched by setup
        mActivityRule.finishActivity();

        // Launch activity in fullscreen windowing mode
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(mInstrumentation.getTargetContext(), ConfigChangeHandlingActivity.class);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        mScenario = ActivityScenario.launch(intent, options.toBundle());
        mScenario.onActivity(activity -> {
            mActivity = activity;
        });

        SurfaceControlViewHost.SurfacePackage p = null;

        mTestService = getService();
        assertTrue(mTestService != null);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, false);
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        mInstrumentation.waitForIdleSync();
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, mSurfaceView);
        mInstrumentation.waitForIdleSync();

        assertTrue(mTestService.getViewIsTouchedAndObscured());
    }

    @Test
    public void testNoHostInputTokenDisallowsObscuredTouches() throws Throwable {
        mTestService = getService();
        mRemoteSurfacePackage = mTestService.getSurfacePackage(new Binder());
        assertTrue(mRemoteSurfacePackage != null);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT, false);
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        mInstrumentation.waitForIdleSync();
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        mInstrumentation.waitForIdleSync();

        assertFalse(mTestService.getViewIsTouched());
    }

    @Test
    public void testPopupWindowReceivesInput() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mActivityRule.runOnUiThread(() -> {
            PopupWindow pw = new PopupWindow();
            mPopupWindow = pw;
            Button popupButton = new Button(mActivity);
            popupButton.setOnClickListener((View v) -> {
                mPopupClicked = true;
            });
            pw.setWidth(DEFAULT_SURFACE_VIEW_WIDTH);
            pw.setHeight(DEFAULT_SURFACE_VIEW_HEIGHT);
            pw.setContentView(popupButton);
            pw.showAsDropDown(mEmbeddedView);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mPopupClicked);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
            mPopupWindow.dismiss();
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        mInstrumentation.waitForIdleSync();
        assertTrue(mClicked);
    }
}
