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

package android.server.wm.jetpack.utils;

import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.getActivityBounds;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test activity that can verify whether the layout changes. Copied from
 * androidx.window.TestActivity.
 */
public class TestActivity extends Activity implements View.OnLayoutChangeListener {

    private int mRootViewId;
    private CountDownLatch mLayoutLatch;
    private CountDownLatch mBoundsChangeLatch;
    private final Rect mPreviousBounds = new Rect();
    private static CountDownLatch sResumeLatch = new CountDownLatch(1);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View contentView = new TestContentViewForConfigurationChange(this);
        mRootViewId = View.generateViewId();
        contentView.setId(mRootViewId);
        setContentView(contentView);

        resetLayoutCounter();
        resetBoundsChangeCounter();
        getWindow().getDecorView().addOnLayoutChangeListener(this);
    }

    int getWidth() {
        return findViewById(mRootViewId).getWidth();
    }

    int getHeight() {
        return findViewById(mRootViewId).getHeight();
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        mLayoutLatch.countDown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sResumeLatch.countDown();
    }

    /**
     * Resets layout counter when waiting for a layout to happen before calling
     * {@link #waitForLayout()}.
     */
    public void resetLayoutCounter() {
        mLayoutLatch = new CountDownLatch(1);
    }

    /**
     * Resets bounds change counter when waiting for a bounds change to happen before calling
     * {@link #waitForBoundsChange()}.
     */
    public void resetBoundsChangeCounter() {
        mPreviousBounds.set(getActivityBounds(this));
        mBoundsChangeLatch = new CountDownLatch(1);
    }

    /**
     * Blocks and waits for the next layout to happen. {@link #resetLayoutCounter()} must be called
     * before calling this method.
     * @return {@code true} if the layout happened before the timeout count reached zero and
     *         {@code false} if the waiting time elapsed before the layout happened.
     */
    public boolean waitForLayout() {
        try {
            return mLayoutLatch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Blocks and waits for the next bounds change to happen. {@link #resetBoundsChangeCounter()}
     * must be called before calling this method.
     * @return {@code true} if the bounds change happened before the timeout count reached zero and
     *         {@code false} if the waiting time elapsed before the bounds change happened.
     */
    public boolean waitForBoundsChange() {
        try {
            return mBoundsChangeLatch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Resets layout counter when waiting for a layout to happen before calling
     * {@link #waitForOnResume()}.
     */
    public static void resetResumeCounter() {
        sResumeLatch = new CountDownLatch(1);
    }

    /**
     * Same as {@link #waitForLayout()}, but waits for onResume() to be called for any activity of
     * this class. This can be used to track activity re-creation.
     * @return {@code true} if the onResume() happened before the timeout count reached zero and
     *         {@code false} if the waiting time elapsed before the onResume() happened.
     */
    public static boolean waitForOnResume() {
        try {
            return sResumeLatch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Sometimes activity configuration change does not trigger when embedding status change, need
     * to use View's instead.
     */
    class TestContentViewForConfigurationChange extends View {
        public TestContentViewForConfigurationChange(Context context) {
            super(context);
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            final Rect newActivityBounds = getActivityBounds(TestActivity.this);
            if (!newActivityBounds.equals(mPreviousBounds)) {
                mPreviousBounds.set(newActivityBounds);
                mBoundsChangeLatch.countDown();
            }
        }
    }
}
