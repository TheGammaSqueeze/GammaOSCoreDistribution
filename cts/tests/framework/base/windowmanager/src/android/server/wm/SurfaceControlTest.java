/*
 * Copyright 2019 The Android Open Source Project
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

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.view.cts.surfacevalidator.ASurfaceControlTestActivity.MultiRectChecker;
import static android.view.cts.surfacevalidator.ASurfaceControlTestActivity.RectChecker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.Executor;

@Presubmit
public class SurfaceControlTest {
    private static final int DEFAULT_SURFACE_SIZE = 100;
    /**
     * Use a rect that doesn't include 1 pixel in the border since some composers add blending at
     * the edges. It's easier to just ignore those pixels and ensure the rest are correct.
     */
    private static final Rect DEFAULT_RECT = new Rect(1, 1, DEFAULT_SURFACE_SIZE - 1,
            DEFAULT_SURFACE_SIZE - 1);

    @Rule
    public ActivityScenarioRule<ASurfaceControlTestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(ASurfaceControlTestActivity.class);

    @Rule
    public TestName mName = new TestName();
    private ASurfaceControlTestActivity mActivity;

    private abstract class SurfaceHolderCallback implements
            SurfaceHolder.Callback {

        public abstract void addChildren(SurfaceControl parent);

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            addChildren(mActivity.getSurfaceView().getSurfaceControl());
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {}

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {}
    }

    private void verifyTest(SurfaceHolder.Callback callback, PixelChecker pixelChecker,
            int numOfTransaction) {
        mActivity.verifyTest(callback, pixelChecker, mName, numOfTransaction);
    }

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    @Test
    public void testLifecycle() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        final SurfaceControl sc = b.setName("CTS").build();

        assertTrue("Failed to build SurfaceControl", sc != null);
        assertTrue(sc.isValid());
        sc.release();
        assertFalse(sc.isValid());
    }

    @Test
    public void testSameSurface() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        final SurfaceControl sc = b.setName("CTS").build();
        SurfaceControl copy = new SurfaceControl(sc, "SurfaceControlTest.testSameSurface");
        assertTrue(copy.isSameSurface(sc));
        sc.release();
        copy.release();
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent) {
        return new SurfaceControl.Builder()
            .setBufferSize(DEFAULT_SURFACE_SIZE, DEFAULT_SURFACE_SIZE)
            .setName("CTS surface")
            .setParent(parent)
            .build();

    }

    void fillWithColor(SurfaceControl sc, int color) {
        Surface s = new Surface(sc);

        Canvas c = s.lockCanvas(null);
        c.drawColor(color);
        s.unlockCanvasAndPost(c);
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent, int color) {
        final SurfaceControl sc = buildDefaultSurface(parent);
        fillWithColor(sc, color);
        return sc;
    }

    private SurfaceControl buildDefaultRedSurface(SurfaceControl parent) {
        return buildDefaultSurface(parent, Color.RED);
    }
    private SurfaceControl buildSmallRedSurface(SurfaceControl parent) {
        SurfaceControl surfaceControl = new SurfaceControl.Builder()
                .setBufferSize(DEFAULT_SURFACE_SIZE / 2, DEFAULT_SURFACE_SIZE / 2)
                .setName("CTS surface")
                .setParent(parent)
                .build();
        fillWithColor(surfaceControl, Color.RED);
        return surfaceControl;
    }

    /**
     * Verify that showing a 100x100 surface filled with RED produces roughly 10,000 red pixels.
     */
    @Test
    public void testShow() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback() {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);

                        makeTransactionWithListener().setVisibility(sc, true).apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, PixelColor.RED), 1);
    }

    /**
     * The same setup as testShow, however we hide the surface and verify that we don't see Red.
     */
    @Test
    public void testHide() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);

                        makeTransactionWithListener().setVisibility(sc, false).apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, PixelColor.BLACK), 1);
    }

    /**
     * Like testHide but we reparent the surface off-screen instead.
     */
    @Test
    public void testReparentOff() throws Throwable {
        final SurfaceControl sc = buildDefaultRedSurface(null);
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        makeTransactionWithListener().reparent(sc, parent).apply();
                        makeTransactionWithListener().reparent(sc, null).apply();
                    }
                },
                new RectChecker(DEFAULT_RECT, PixelColor.BLACK), 2);
      // Since the SurfaceControl is parented off-screen, if we release our reference
      // it may completely die. If this occurs while the render thread is still rendering
      // the RED background we could trigger a crash. For this test defer destroying the
      // Surface until we have collected our test results.
      if (sc != null) {
        sc.release();
      }
    }

    /**
     * Here we use the same red-surface set up but construct it off-screen and then re-parent it.
     */
    @Test
    public void testReparentOn() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(null);

                        makeTransactionWithListener().setVisibility(sc, true)
                            .reparent(sc, parent)
                            .apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, PixelColor.RED), 1);
    }

    /**
     * Test that a surface with Layer "2" appears over a surface with Layer "1".
     */
    @Test
    public void testSetLayer() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);
                        final SurfaceControl sc2 = buildDefaultSurface(parent, Color.GREEN);

                        makeTransactionWithListener().setVisibility(sc, true)
                            .setVisibility(sc2, true)
                            .setLayer(sc, 1)
                            .setLayer(sc2, 2)
                            .apply();

                        sc.release();
                    }
                },
                new RectChecker(DEFAULT_RECT, PixelColor.GREEN), 1);
    }

    /**
     * Try setting the position of a surface with the top-left corner off-screen.
     */
    @Test
    public void testSetGeometry_dstBoundsOffScreen() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);
                        makeTransactionWithListener().setVisibility(sc, true)
                            .setGeometry(sc, null, new Rect(-50, -50, 50, 50), Surface.ROTATION_0)
                            .apply();
                        sc.release();
                    }
                },

                // The rect should be offset by -50 pixels
                new MultiRectChecker(DEFAULT_RECT) {
                    final PixelColor red = new PixelColor(PixelColor.RED);
                    final PixelColor black = new PixelColor(PixelColor.BLACK);
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x < 50 && y < 50) {
                            return red;
                        } else {
                            return black;
                        }
                    }
                }, 1);
    }

    /**
     * Try setting the position of a surface with the top-left corner on-screen.
     */
    @Test
    public void testSetGeometry_dstBoundsOnScreen() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);
                        makeTransactionWithListener().setVisibility(sc, true)
                            .setGeometry(sc, null, new Rect(50, 50, 150, 150), Surface.ROTATION_0)
                            .apply();

                        sc.release();
                    }
                },

                // The rect should be offset by 50 pixels
                new MultiRectChecker(DEFAULT_RECT) {
                    final PixelColor red = new PixelColor(PixelColor.RED);
                    final PixelColor black = new PixelColor(PixelColor.BLACK);
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 50 && y >= 50) {
                            return red;
                        } else {
                            return black;
                        }
                    }
                }, 1);
    }

    /**
     * Try scaling a surface
     */
    @Test
    public void testSetGeometry_dstBoundsScaled() throws Throwable {
        verifyTest(
                new SurfaceHolderCallback () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildSmallRedSurface(parent);
                        makeTransactionWithListener().setVisibility(sc, true)
                            .setGeometry(sc, new Rect(0, 0, DEFAULT_SURFACE_SIZE / 2, DEFAULT_SURFACE_SIZE / 2),
                                    new Rect(0, 0, DEFAULT_SURFACE_SIZE , DEFAULT_SURFACE_SIZE),
                                    Surface.ROTATION_0)
                            .apply();
                        sc.release();
                    }
                },

                new RectChecker(DEFAULT_RECT, PixelColor.RED), 1);
    }

    private SurfaceControl.Transaction makeTransactionWithListener() {
        return new SurfaceControl.Transaction().addTransactionCommittedListener(
                new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                }, new SurfaceControl.TransactionCommittedListener() {
                    @Override
                    public void onTransactionCommitted() {
                        mActivity.transactionCommitted();
                    }
                }
        );
    }
}
