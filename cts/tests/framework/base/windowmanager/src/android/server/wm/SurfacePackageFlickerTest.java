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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.server.wm.WindowManagerState.getLogicalDisplaySize;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Binder;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.view.cts.surfacevalidator.AnimationFactory;
import android.view.cts.surfacevalidator.CapturedActivity;
import android.view.cts.surfacevalidator.ISurfaceValidatorTestCase;
import android.view.cts.surfacevalidator.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;
import android.view.cts.surfacevalidator.SurfaceControlTestCase;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CountDownLatch;

public class SurfacePackageFlickerTest {
    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;
    private static final int DEFAULT_BUFFER_WIDTH = 640;
    private static final int DEFAULT_BUFFER_HEIGHT = 480;

    @Rule
    public final ActivityScenarioRule<CapturedActivity> mActivityRule =
                createFullscreenActivityScenarioRule(CapturedActivity.class);

    @Rule
    public TestName mName = new TestName();
    private CapturedActivity mActivity;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        mActivity.dismissPermissionDialog();
        mActivity.setLogicalDisplaySize(getLogicalDisplaySize());
    }

    /**
     * Want to be especially sure we don't leave up the permission dialog, so try and dismiss
     * after test.
     */
    @After
    public void tearDown() throws UiObjectNotFoundException {
        mActivity.dismissPermissionDialog();
    }

    class SurfacePackageTestCase implements ISurfaceValidatorTestCase {
        private final FrameLayout.LayoutParams mLayoutParams;
        private final PixelChecker mPixelChecker;
        private SurfaceView mSurfaceView;
        private SurfaceControlViewHost mSurfaceControlViewHost;
        private FrameLayout mParent;
        private final CountDownLatch mFirstDrawLatch = new CountDownLatch(1);


        private final Runnable mRecreateSurfaceViewCallback = new Runnable() {
                public void run() {
                    if (mSurfaceControlViewHost == null) {
                        return;
                    }
                    mParent.removeView(mSurfaceView);
                    mSurfaceView = new SurfaceView(mActivity);
                    mSurfaceView.setZOrderOnTop(true);
                    mParent.addView(mSurfaceView, mLayoutParams);
                    mSurfaceView.setChildSurfacePackage(mSurfaceControlViewHost.getSurfacePackage());
                    mParent.post(mRecreateSurfaceViewCallback);
                }
        };

        public SurfacePackageTestCase(PixelChecker pixelChecker,
                                      int layoutWidth, int layoutHeight) {
            mLayoutParams = new FrameLayout.LayoutParams(layoutWidth, layoutHeight,
                Gravity.LEFT | Gravity.TOP);
            mPixelChecker = pixelChecker;
        }

        @Override
        public void start(Context context, FrameLayout parent) {
            mParent = parent;
            mSurfaceView = new SurfaceView(context);
            mSurfaceView.setZOrderOnTop(true);
            mParent.addView(mSurfaceView, mLayoutParams);

            final View v = new View(mActivity);
            v.setBackgroundColor(Color.GREEN);

            mSurfaceControlViewHost = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                new Binder());
            mSurfaceControlViewHost.setView(v, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT);
            mSurfaceView.setChildSurfacePackage(mSurfaceControlViewHost.getSurfacePackage());

            v.getViewTreeObserver().registerFrameCommitCallback(() -> {
                parent.post(mRecreateSurfaceViewCallback);
                mFirstDrawLatch.countDown();
            });
        }

        public void waitForReady() {
            try {
                mFirstDrawLatch.await();
            } catch (Exception e) {
                // Oh well
            }
        }

        @Override
        public void end() {
            mSurfaceControlViewHost.release();
            mParent.removeAllViews();
            mSurfaceControlViewHost = null;
        }

        @Override
        public boolean hasAnimation() {
            return true;
        }

        @Override
        public PixelChecker getChecker() {
            return mPixelChecker;
        }

        @Override
        public Rect getBoundsToCheck(FrameLayout parent) {
            View boundsView = mParent;
            Rect boundsToCheck = new Rect(0, 0, boundsView.getWidth(), boundsView.getHeight());
            int[] topLeft = new int[2];
            boundsView.getLocationOnScreen(topLeft);
            boundsToCheck.offset(topLeft[0], topLeft[1]);
            return boundsToCheck;
        }
    }

    @Test
    public void testSurfacePackageNoFlicker() throws Throwable {
        // The basic operation of this test is to continually recreate
        // SurfaceViews hosting a single green SurfacePackage.
        // We verify that removing the old SurfaceView at the
        // "same time" as reparenting the SurfacePackage to the new one
        // results in a flicker free process.
        PixelChecker pixelChecker = new PixelChecker(PixelColor.GREEN) {
            @Override
            public boolean checkPixels(int pixelCount, int width, int height) {
                return pixelCount == DEFAULT_LAYOUT_WIDTH*DEFAULT_LAYOUT_HEIGHT;
            }
        };
        SurfacePackageTestCase t = new SurfacePackageTestCase(
                pixelChecker, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT);
        mActivity.verifyTest(t, mName);
    }
}
