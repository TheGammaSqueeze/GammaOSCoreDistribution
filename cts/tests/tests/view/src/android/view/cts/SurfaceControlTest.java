/*
 * Copyright 2018 The Android Open Source Project
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

package android.view.cts;

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.view.cts.util.ASurfaceControlTestUtils.getBufferId;
import static android.view.cts.util.ASurfaceControlTestUtils.getQuadrantBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.getSolidBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity.MultiRectChecker;
import android.view.cts.surfacevalidator.ASurfaceControlTestActivity.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.hardware.SyncFenceUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SurfaceControlTest {
    static {
        System.loadLibrary("ctsview_jni");
    }

    private static final String TAG = SurfaceControlTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;

    private static final PixelColor RED = new PixelColor(PixelColor.RED);
    private static final PixelColor BLUE = new PixelColor(PixelColor.BLUE);
    private static final PixelColor MAGENTA = new PixelColor(PixelColor.MAGENTA);
    private static final PixelColor GREEN = new PixelColor(PixelColor.GREEN);
    private static final PixelColor YELLOW = new PixelColor(PixelColor.YELLOW);

    @Rule
    public ActivityScenarioRule<ASurfaceControlTestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(ASurfaceControlTestActivity.class);

    @Rule
    public TestName mName = new TestName();

    private ASurfaceControlTestActivity mActivity;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    SurfaceControl getHostSurfaceControl() {
        return mActivity.getSurfaceControl();
    }

    ///////////////////////////////////////////////////////////////////////////
    // SurfaceHolder.Callbacks
    ///////////////////////////////////////////////////////////////////////////

    private abstract class BasicSurfaceHolderCallback implements SurfaceHolder.Callback {
        private final Set<SurfaceControl> mSurfaceControls = new HashSet<>();
        private final Set<HardwareBuffer> mBuffers = new HashSet<>();

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Canvas canvas = holder.lockCanvas();
            canvas.drawColor(Color.YELLOW);
            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            for (SurfaceControl surfaceControl : mSurfaceControls) {
                transaction.reparent(surfaceControl, null);
            }
            transaction.apply();
            mSurfaceControls.clear();

            for (HardwareBuffer buffer : mBuffers) {
                buffer.close();
            }
            mBuffers.clear();
        }

        public SurfaceControl createFromWindow(SurfaceHolder surfaceHolder) {
            assertNotNull("No parent?", getHostSurfaceControl());
            SurfaceControl surfaceControl = new SurfaceControl.Builder()
                    .setParent(getHostSurfaceControl())
                    .setName("SurfaceControl_createFromWindowLayer")
                    .setHidden(false)
                    .build();
            mSurfaceControls.add(surfaceControl);
            return surfaceControl;
        }

        public SurfaceControl create(SurfaceControl parentSurfaceControl) {
            assertNotNull("No parent?", parentSurfaceControl);
            SurfaceControl surfaceControl = new SurfaceControl.Builder()
                    .setParent(parentSurfaceControl)
                    .setName("SurfaceControl_create")
                    .setHidden(false)
                    .build();
            mSurfaceControls.add(surfaceControl);
            return surfaceControl;
        }

        public void setSolidBuffer(SurfaceControl surfaceControl,
                int width, int height, int color) {
            HardwareBuffer buffer = getSolidBuffer(width, height, color);
            assertNotNull("failed to make solid buffer", buffer);
            new SurfaceControl.Transaction()
                    .setBuffer(surfaceControl, buffer)
                    .apply();
            mBuffers.add(buffer);
        }

        public void setSolidBuffer(SurfaceControl surfaceControl,
                int width, int height, int color, int dataSpace) {
            HardwareBuffer buffer = getSolidBuffer(width, height, color);
            assertNotNull("failed to make solid buffer", buffer);
            new SurfaceControl.Transaction()
                    .setBuffer(surfaceControl, buffer)
                    .setDataSpace(surfaceControl, dataSpace)
                    .apply();
            mBuffers.add(buffer);
        }

        public void setSolidBuffer(SurfaceControl surfaceControl,
                int width, int height, int color, SyncFence fence) {
            HardwareBuffer buffer = getSolidBuffer(width, height, color);
            assertNotNull("failed to make solid buffer", buffer);
            new SurfaceControl.Transaction()
                    .setBuffer(surfaceControl, buffer, fence)
                    .apply();
            mBuffers.add(buffer);
        }

        public void setQuadrantBuffer(SurfaceControl surfaceControl,
                int width, int height, int colorTopLeft, int colorTopRight, int colorBottomRight,
                int colorBottomLeft) {
            HardwareBuffer buffer = getQuadrantBuffer(width, height, colorTopLeft, colorTopRight,
                    colorBottomRight, colorBottomLeft);
            assertNotNull("failed to make solid buffer", buffer);
            new SurfaceControl.Transaction()
                    .setBuffer(surfaceControl, buffer)
                    .apply();
            mBuffers.add(buffer);
        }

        public void setSourceToDefaultDest(SurfaceControl surfaceControl, Rect src) {
            float scaleY = DEFAULT_LAYOUT_HEIGHT / (float) src.height();
            float scaleX = DEFAULT_LAYOUT_WIDTH / (float) src.width();
            new SurfaceControl.Transaction()
                    .setCrop(surfaceControl, new Rect(0, 0,
                            DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT))
                    .setPosition(surfaceControl, -src.left * scaleX, -src.top * scaleY)
                    .setScale(surfaceControl, scaleX, scaleY)
                    .apply();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////

    private void verifyTest(SurfaceHolder.Callback callback, PixelChecker pixelChecker) {
        mActivity.verifyTest(callback, pixelChecker, mName);
    }

    // INTRO: The following tests run a series of commands and verify the
    // output based on the number of pixels with a certain color on the display.
    //
    // The interface being tested is a NDK api but the only way to record the display
    // through public apis is in through the SDK. So the test logic and test verification
    // is in Java but the hooks that call into the NDK api are jni code.
    //
    // The set up is done during the surfaceCreated callback. In most cases, the
    // test uses the opportunity to create a child layer through createFromWindow and
    // performs operations on the child layer.
    //
    // When there is no visible buffer for the layer(s) the color defaults to black.
    // The test cases allow a +/- 10% error rate. This is based on the error
    // rate allowed in the SurfaceViewSyncTests

    @Test
    public void testSurfaceControl_createFromWindow() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        createFromWindow(holder);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceControl_create() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceControl_acquire() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer_parentAndChild() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(parentSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.BLUE);
                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer_childOnly() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setVisibility_show() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setVisibility(surfaceControl, true)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setVisibility_hide() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setVisibility(surfaceControl, false)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferOpaque_opaque() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.TRANSLUCENT_RED);
                        new SurfaceControl.Transaction()
                                .setOpaque(surfaceControl, true)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferOpaque_translucent() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.TRANSLUCENT_RED);
                        new SurfaceControl.Transaction()
                                .setOpaque(surfaceControl, false)
                                .apply();
                    }
                },
                // setBufferOpaque is an optimization that can be used by SurfaceFlinger.
                // It isn't required to affect SurfaceFlinger's behavior.
                //
                // Ideally we would check for a specific blending of red with a layer below
                // it. Unfortunately we don't know what blending the layer will use and
                // we don't know what variation the GPU/DPU/blitter might have. Although
                // we don't know what shade of red might be present, we can at least check
                // that the optimization doesn't cause the framework to drop the buffer entirely.
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_small() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, .4f, .4f)
                                .setPosition(surfaceControl, 10, 10)
                                .apply();
                    }
                },
                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        }
                        return YELLOW;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_smallScaleFirst() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, .4f, .4f)
                                .apply();
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, 10, 10)
                                .apply();
                    }
                },
                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        }
                        return YELLOW;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_smallPositionFirst() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, 10, 10)
                                .apply();
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, .4f, .4f)
                                .apply();
                    }
                },
                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 50 && y >= 10 && y < 50) {
                            return RED;
                        }
                        return YELLOW;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_parentSmall() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setScale(parentSurfaceControl, .4f, .4f)
                                .setPosition(parentSurfaceControl, 10, 10)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //1600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1440 && pixelCount < 1760;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_childSmall() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setScale(childSurfaceControl, .4f, .4f)
                                .setPosition(childSurfaceControl, 10, 10)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //1600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1440 && pixelCount < 1760;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_extraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setScale(surfaceControl, 3f, 3f)
                                .setPosition(surfaceControl, -100, -100)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_childExtraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setScale(childSurfaceControl, 3f, 3f)
                                .setPosition(childSurfaceControl, -100, -100)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_negativeOffset() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, -20, -30)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //5600 (w = 80, h = 70)
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 5000 && pixelCount < 6000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_outOfParentBounds() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl, 50, 50)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_twoLayers() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);

                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.BLUE);
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl1, 10, 10)
                                .setScale(surfaceControl1, .2f, .3f)
                                .apply();
                        new SurfaceControl.Transaction()
                                .setPosition(surfaceControl2, 70, 20)
                                .setScale(surfaceControl2, .2f, .3f)
                                .apply();
                    }
                },

                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        if (x >= 10 && x < 30 && y >= 10 && y < 40) {
                            return RED;
                        } else if (x >= 70 && x < 90 && y >= 20 && y < 50) {
                            return BLUE;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                    }
                },

                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return GREEN;
                        } else {
                            return MAGENTA;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_small() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setSourceToDefaultDest(surfaceControl, new Rect(60, 10, 90, 90));
                    }
                },
                new PixelChecker(PixelColor.MAGENTA) { //5000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 4500 && pixelCount < 5500;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_smallCentered() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setSourceToDefaultDest(surfaceControl, new Rect(40, 40, 60, 60));
                    }
                },

                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return GREEN;
                        } else {
                            return MAGENTA;
                        }
                    }

                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        // There will be sampling artifacts along the center line, ignore those
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropRect_extraLarge() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        new SurfaceControl.Transaction()
                                .setCrop(surfaceControl, new Rect(-50, -50, 150, 150))
                                .apply();
                    }
                },

                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return GREEN;
                        } else {
                            return MAGENTA;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropRect_badOffset() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        new SurfaceControl.Transaction()
                                .setCrop(surfaceControl, new Rect(-50, -50, 50, 50))
                                .apply();
                    }
                },
                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return RED;
                        } else {
                            return YELLOW;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropRect_invalidCropWidth() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setCrop(surfaceControl, new Rect(0, 0, -1, 100))
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    @Test
    public void testSurfaceTransaction_setCropRect_invalidCropHeight() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setCrop(surfaceControl, new Rect(0, 0, 100, -1))
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    @Test
    public void testSurfaceTransaction_setTransform_flipH() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        new SurfaceControl.Transaction()
                                .setBufferTransform(surfaceControl, 1)
                                .apply();
                    }
                },
                new MultiRectChecker(new Rect(0, 0, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT)) {
                    @Override
                    public PixelColor getExpectedColor(int x, int y) {
                        int halfWidth = DEFAULT_LAYOUT_WIDTH / 2;
                        int halfHeight = DEFAULT_LAYOUT_HEIGHT / 2;
                        if (x < halfWidth && y < halfHeight) {
                            return BLUE;
                        } else if (x >= halfWidth && y < halfHeight) {
                            return RED;
                        } else if (x < halfWidth && y >= halfHeight) {
                            return MAGENTA;
                        } else {
                            return GREEN;
                        }
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setTransform_rotate180() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setSourceToDefaultDest(surfaceControl, new Rect(0, 50, 50, 100));
                        new SurfaceControl.Transaction()
                                .setBufferTransform(surfaceControl, 3)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.BLUE) { // 10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDamageRegion_all() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);

                        HardwareBuffer blueBuffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.BLUE);
                        Region damageRegion = new Region();
                        damageRegion.op(0, 0, 25, 25, Region.Op.UNION);
                        damageRegion.op(25, 25, 100, 100, Region.Op.UNION);
                        new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, blueBuffer)
                                .setDamageRegion(surfaceControl, damageRegion)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.BLUE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_zero() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, 0)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_positive() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, 5)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_negative() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, -15)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_max() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, Integer.MAX_VALUE)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_min() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl1 = createFromWindow(holder);
                        SurfaceControl surfaceControl2 = createFromWindow(holder);
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        new SurfaceControl.Transaction()
                                .setLayer(surfaceControl1, 1)
                                .setLayer(surfaceControl2, Integer.MIN_VALUE)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDataSpace_srgb() {
        final int darkRed = 0xFF00006F;
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                darkRed, DataSpace.DATASPACE_SRGB);
                    }
                },
                new PixelChecker(darkRed) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDataSpace_display_p3() {
        final int darkRed = 0xFF00006F;
        long converted = Color.convert(0x6F / 255.f, 0f, 0f, 1f,
                ColorSpace.get(ColorSpace.Named.DISPLAY_P3), ColorSpace.get(ColorSpace.Named.SRGB));
        assertTrue(Color.isSrgb(converted));
        int argb = Color.toArgb(converted);
        // PixelChecker uses a ABGR for some reason (endian mismatch with native?), swizzle to match
        int asABGR = 0xFF000000 | Color.red(argb);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                darkRed, DataSpace.DATASPACE_DISPLAY_P3);
                    }
                },
                new PixelChecker(asABGR) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_nullSyncFence() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.BLUE, null);
                    }
                },
                new PixelChecker(PixelColor.BLUE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_invalidSyncFence() {
        SyncFence fence = SyncFenceUtil.createUselessFence();
        if (fence == null) {
            // Extension not supported
            Log.d(TAG, "Skipping test, EGL_ANDROID_native_fence_sync not available");
            return;
        }
        fence.close();
        assertFalse(fence.isValid());
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.BLUE, fence);
                    }
                },
                new PixelChecker(PixelColor.BLUE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_withSyncFence() {
        SyncFence fence = SyncFenceUtil.createUselessFence();
        if (fence == null) {
            // Extension not supported
            Log.d(TAG, "Skipping test, EGL_ANDROID_native_fence_sync not available");
            return;
        }
        assertTrue(fence.isValid());
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.BLUE, fence);
                    }
                },
                new PixelChecker(PixelColor.BLUE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceControl_scaleToZero() {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl parentSurfaceControl = createFromWindow(holder);
                        SurfaceControl childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(parentSurfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                        setSolidBuffer(childSurfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        new SurfaceControl.Transaction()
                                .setScale(childSurfaceControl, 0, 0)
                                .apply();
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
    }

    @Test
    public void testSurfaceTransaction_negativeScaleX() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setScale(surfaceControl, -1, 1)
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    @Test
    public void testSurfaceTransaction_negativeScaleY() {
        final AtomicBoolean caughtException = new AtomicBoolean(false);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.YELLOW);
                        try {
                            new SurfaceControl.Transaction()
                                    .setScale(surfaceControl, 1, -1)
                                    .apply();
                        } catch (IllegalArgumentException e) {
                            caughtException.set(true);
                        }
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );
        assertTrue(caughtException.get());
    }

    /**
     * @param delayMs delay between calling setBuffer
     */
    private void releaseBufferCallbackHelper(long delayMs) throws InterruptedException {
        final int setBufferCount = 3;
        CountDownLatch releaseCounter = new CountDownLatch(setBufferCount);
        long[] bufferIds = new long[setBufferCount];
        long[] receivedCallbacks = new long[setBufferCount];
        SyncFence[] receivedFences = new SyncFence[setBufferCount];
        long timeBeforeTest = System.nanoTime();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        for (int i = 0; i < setBufferCount; i++) {
                            HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                    DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                            final int receiveIndex = i;
                            final long bufferId = getBufferId(buffer);
                            bufferIds[receiveIndex] = bufferId;
                            new SurfaceControl.Transaction()
                                    .setBuffer(surfaceControl, buffer, null,
                                            (SyncFence fence) -> {
                                                receivedCallbacks[receiveIndex] = bufferId;
                                                receivedFences[receiveIndex] = fence;
                                                releaseCounter.countDown();
                                            })
                                    .apply();
                            buffer.close();
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException e) {
                            }
                        }
                        setSolidBuffer(surfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < setBufferCount; i++) {
            assertEquals(bufferIds[i], receivedCallbacks[i]);
            if (i > 0) {
                assertNotEquals(bufferIds[i], bufferIds[i - 1]);
            }
            SyncFence fence = receivedFences[i];
            assertNotNull(fence);
            if (fence.isValid()) {
                fence.awaitForever();
                assertTrue(fence.getSignalTime() > timeBeforeTest);
                assertTrue(fence.getSignalTime() < System.nanoTime());
                fence.close();
            }
        }
    }

    @Test
    public void testReleaseBufferCallback() throws InterruptedException {
        releaseBufferCallbackHelper(0 /* delayMs */);
    }

    /**
     * This test is different than above {@link #testReleaseBufferCallback} since it's testing the
     * scenario where the buffers are sent a bit slower. This helps test a more real case where
     * we're more likely not to overwrite the buffers before latched. The delay isn't to fix
     * flakiness, but to actually try to test the case where buffers are latched and not just
     * directly overwritten. We could pace with commit callback, but that creates behavior that
     * users of setBuffer may not do, which could result in different output.
     *
     * Instead set 100ms delay between each buffer since that should help give time to SF to latch
     * a buffer before sending another one.
     */
    @Test
    public void testReleaseBufferCallback_Slow() throws InterruptedException {
        releaseBufferCallbackHelper(100 /* delayMs */);
    }

    @Test
    public void testReleaseBufferCallbackSameBuffer() throws InterruptedException {
        final int setBufferCount = 3;
        CountDownLatch releaseCounter = new CountDownLatch(setBufferCount);
        long[] bufferIds = new long[setBufferCount];
        long[] receivedCallbacks = new long[setBufferCount];
        SyncFence[] receivedFences = new SyncFence[setBufferCount];
        long timeBeforeTest = System.nanoTime();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                        for (int i = 0; i < setBufferCount; i++) {
                            final int receiveIndex = i;
                            final long bufferId = getBufferId(buffer);
                            bufferIds[receiveIndex] = bufferId;
                            new SurfaceControl.Transaction()
                                    .setBuffer(surfaceControl, buffer, null,
                                            (SyncFence fence) -> {
                                                receivedCallbacks[receiveIndex] = bufferId;
                                                receivedFences[receiveIndex] = fence;
                                                releaseCounter.countDown();
                                            })
                                    .apply();
                        }
                        buffer.close();
                        setSolidBuffer(surfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < setBufferCount; i++) {
            assertEquals(bufferIds[i], receivedCallbacks[i]);
            if (i > 0) {
                assertEquals(bufferIds[i], bufferIds[i - 1]);
            }
            SyncFence fence = receivedFences[i];
            assertNotNull(fence);
            if (fence.isValid()) {
                fence.awaitForever();
                assertTrue(fence.getSignalTime() > timeBeforeTest);
                assertTrue(fence.getSignalTime() < System.nanoTime());
                fence.close();
            }
        }
    }

    @Test
    public void testReleaseBufferCallbackNullBuffer() throws InterruptedException {
        CountDownLatch releaseCounter = new CountDownLatch(1);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                        SurfaceControl.Transaction t = new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer, null,
                                        (SyncFence fence) -> releaseCounter.countDown());
                        t.setBuffer(surfaceControl, (HardwareBuffer) null).apply();
                        buffer.close();
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testReleaseBufferCallbackNullBufferThenNonNull() throws InterruptedException {
        CountDownLatch releaseCounter = new CountDownLatch(2);
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        SurfaceControl surfaceControl = createFromWindow(holder);
                        HardwareBuffer buffer = getSolidBuffer(DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                        SurfaceControl.Transaction t = new SurfaceControl.Transaction()
                                .setBuffer(surfaceControl, buffer, null,
                                        (SyncFence fence) -> releaseCounter.countDown());
                        t.setBuffer(surfaceControl, (HardwareBuffer) null);
                        t.setBuffer(surfaceControl, buffer, null,
                                (SyncFence fence) -> releaseCounter.countDown()).apply();
                        setSolidBuffer(surfaceControl,
                                DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT, PixelColor.YELLOW);
                        buffer.close();
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int matchingPixelCount, int width, int height) {
                        return matchingPixelCount > 9000 && matchingPixelCount < 11000;
                    }
                }
        );

        assertTrue(releaseCounter.await(5, TimeUnit.SECONDS));
    }
}
