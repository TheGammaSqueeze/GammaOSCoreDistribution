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

package android.view.cts;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Region;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.runner.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class SetTouchableRegionTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private ActivityScenario<CtsActivity> mScenario;

    class MotionRecordingView extends View {
        public MotionRecordingView(Context context) {
            super(context);
        }

        boolean mGotEvent = false;
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
    MotionRecordingView mMotionRecordingView;
    View mPopupView;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        // Launch activity in fullscreen windowing mode
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(mInstrumentation.getTargetContext(), CtsActivity.class);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        mScenario = ActivityScenario.launch(intent, options.toBundle());
        mScenario.onActivity(activity -> {
            mActivity = activity;
        });
    }

    void tapSync() {
        mInstrumentation.waitForIdleSync();
        assertFalse(mMotionRecordingView.gotEvent());

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, null, mMotionRecordingView);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testClickthroughRegion() throws Throwable {
        mScenario.onActivity(activity -> {
            mMotionRecordingView = new MotionRecordingView(mActivity);
            mActivity.setContentView(mMotionRecordingView);
        });
        tapSync();
        // We have a view filling our entire hierarchy and so a tap should reach it
        assertTrue(mMotionRecordingView.gotEvent());

        mScenario.onActivity(activity -> {
            mPopupView = new View(mActivity);
            PopupWindow popup = new PopupWindow(mPopupView,
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.FILL_PARENT);
            popup.showAtLocation(mMotionRecordingView, Gravity.NO_GRAVITY, 0, 0);
        });
        mMotionRecordingView.reset();
        tapSync();
        // However now we have covered ourselves with a FILL_PARENT popup window
        // and so the tap should not reach us
        assertFalse(mMotionRecordingView.gotEvent());

        mScenario.onActivity(activity -> {
            mPopupView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        tapSync();
        // But now we have punched a touchable region hole in the popup window and
        // we should be reachable again.
        assertTrue(mMotionRecordingView.gotEvent());
    }
}
