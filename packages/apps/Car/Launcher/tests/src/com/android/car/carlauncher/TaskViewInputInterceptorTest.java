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

package com.android.car.carlauncher;

import static android.view.WindowManager.LayoutParams.INPUT_FEATURE_SPY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.UiAutomation;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class TaskViewInputInterceptorTest extends AbstractExtendedMockitoTestCase {
    private static final UiAutomation UI_AUTOMATION =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final List<ControlledCarTaskView> mControlledCarTaskViews = new ArrayList<>();

    @Rule
    public ActivityScenarioRule mActivityRule = new ActivityScenarioRule<>(TestActivity.class);

    @Mock TaskViewManager mTaskViewManager;
    @Captor ArgumentCaptor<WindowManager.LayoutParams> mLayoutParamsArgumentCaptor;
    @Captor ArgumentCaptor<View> mSpyWindowArgumentCaptor;
    private CountDownLatch mIdleHandlerLatch = new CountDownLatch(1);
    private TestActivity mActivity;
    private TaskViewInputInterceptor mTaskViewInputInterceptor;
    private ActivityScenario<TestActivity> mScenario;

    @Before
    public void setup() throws Exception {
        mScenario = mActivityRule.getScenario();
        mScenario.onActivity(activity -> mActivity = activity);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        runOnMainAndWait(
                () -> {
                    mTaskViewInputInterceptor =
                            new TaskViewInputInterceptor(mActivity, mTaskViewManager);
                });

        doReturn(mControlledCarTaskViews).when(mTaskViewManager).getControlledTaskViews();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mActivity == null) {
            return;
        }
        mActivity.finishCompletely();
    }

    @Test
    public void init_addsSpyWindow() throws Exception {
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());

        verify(mActivity.mSpyWm).addView(any(), mLayoutParamsArgumentCaptor.capture());
        assertThat(mLayoutParamsArgumentCaptor.getValue().inputFeatures)
                .isEqualTo(INPUT_FEATURE_SPY);
    }

    @Test
    public void init_again_doesNothing() throws Exception {
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());

        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());

        verify(mActivity.mSpyWm, times(1)).addView(any(), any());
    }

    @Test
    public void activityStopped_removesSpyWindow() throws Exception {
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());

        mScenario.moveToState(Lifecycle.State.CREATED);

        verify(mActivity.mSpyWm).removeView(any());
    }

    @Test
    public void activityStoppedStarted_addsSpyWindow() throws Exception {
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());
        mScenario.moveToState(Lifecycle.State.CREATED);

        mScenario.moveToState(Lifecycle.State.STARTED);

        verify(mActivity.mSpyWm, times(2)).addView(any(), any());
    }

    @Test
    public void singleTap_insideTaskView_capturingEnabled_noCapturing() throws Exception {
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());
        verify(mActivity.mSpyWm).addView(mSpyWindowArgumentCaptor.capture(), any());
        createControlledCarTaskView(new Rect(10, 0, 30, 100), /* capturingEnabled= */ true);

        View spyWindow = mSpyWindowArgumentCaptor.getValue();
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent downEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_DOWN,
                        /* x= */ 11,
                        /* y= */ 2,
                        /* metaState= */ 0);
        MotionEvent moveEventOnTheDownEventLocation =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_MOVE,
                        /* x= */ 11,
                        /* y= */ 2,
                        /* metaState= */ 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent upEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_UP,
                        /* x= */ 11,
                        /* y= */ 2,
                        /* metaState= */ 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);

        // Act
        spyWindow.dispatchTouchEvent(downEvent);
        spyWindow.dispatchTouchEvent(moveEventOnTheDownEventLocation);
        spyWindow.dispatchTouchEvent(upEvent);

        // Assert
        verify(mActivity.mSpyInputManager, times(0)).pilferPointers(any());
    }

    @Test
    public void longPress_insideTaskView_capturingEnabled_capturesGesture() throws Exception {
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());
        verify(mActivity.mSpyWm).addView(mSpyWindowArgumentCaptor.capture(), any());
        ControlledCarTaskView taskView =
                createControlledCarTaskView(new Rect(10, 0, 30, 100), /* capturingEnabled= */ true);
        View.OnLongClickListener taskViewLongClickListener = mock(View.OnLongClickListener.class);
        doReturn(taskViewLongClickListener).when(taskView).getOnLongClickListener();
        View spyWindow = mSpyWindowArgumentCaptor.getValue();

        // Act
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent downEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_DOWN,
                        /* x= */ 11,
                        /* y= */ 2,
                        /* metaState= */ 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        runOnMainAndWait(() -> spyWindow.dispatchTouchEvent(downEvent));
        waitForLongPressTimeout();

        // Assert
        verify(mActivity.mSpyInputManager, times(1)).pilferPointers(any());
        verify(taskViewLongClickListener).onLongClick(any());
    }

    @Test
    public void longPress_insideTaskView_capturingDisabled_noCapturing() throws Exception {
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());
        verify(mActivity.mSpyWm).addView(mSpyWindowArgumentCaptor.capture(), any());
        ControlledCarTaskView taskView =
                createControlledCarTaskView(
                        new Rect(10, 0, 30, 100), /* capturingEnabled= */ false);
        View.OnLongClickListener taskViewLongClickListener = mock(View.OnLongClickListener.class);
        doReturn(taskViewLongClickListener).when(taskView).getOnLongClickListener();
        View spyWindow = mSpyWindowArgumentCaptor.getValue();

        // Act
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent downEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_DOWN,
                        /* x= */ 11,
                        /* y= */ 2,
                        /* metaState= */ 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        runOnMainAndWait(() -> spyWindow.dispatchTouchEvent(downEvent));
        waitForLongPressTimeout();

        // Assert
        verify(mActivity.mSpyInputManager, times(0)).pilferPointers(any());
        verifyZeroInteractions(taskViewLongClickListener);
    }

    private void waitForLongPressTimeout() throws InterruptedException {
        CountDownLatch l = new CountDownLatch(1);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> l.countDown(), ViewConfiguration.getLongPressTimeout());
        l.await(1, TimeUnit.SECONDS);
    }

    @Test
    public void swipeGesture_whenActionDownInsideTaskView_capturingEnabled_capturesGesture()
            throws Exception {
        // Arrange
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());
        verify(mActivity.mSpyWm).addView(mSpyWindowArgumentCaptor.capture(), any());
        createControlledCarTaskView(new Rect(10, 0, 30, 100), /* capturingEnabled= */ true);

        View spyWindow = mSpyWindowArgumentCaptor.getValue();
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent downEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_DOWN,
                        /* x= */ 11,
                        /* y= */ 2,
                        /* metaState= */ 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent moveEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_MOVE,
                        /* x= */ 12,
                        /* y= */ 7,
                        /* metaState= */ 0);
        moveEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent upEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_UP,
                        /* x= */ 12,
                        /* y= */ 9,
                        /* metaState= */ 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);

        // Act
        runOnMainAndWait(
                () -> {
                    spyWindow.dispatchTouchEvent(downEvent);
                    spyWindow.dispatchTouchEvent(moveEvent);
                    spyWindow.dispatchTouchEvent(upEvent);
                });

        // Assert
        verify(mActivity.mSpyInputManager, times(2)).pilferPointers(any());
        assertThat(mActivity.mEventsReceived.size()).isEqualTo(3);
        assertThat(mActivity.mEventsReceived.get(0).getAction()).isEqualTo(MotionEvent.ACTION_DOWN);
        assertThat(mActivity.mEventsReceived.get(0).getX()).isEqualTo(12);
        assertThat(mActivity.mEventsReceived.get(0).getY()).isEqualTo(7);
        assertThat(mActivity.mEventsReceived.get(1).getAction()).isEqualTo(MotionEvent.ACTION_MOVE);
        assertThat(mActivity.mEventsReceived.get(1).getX()).isEqualTo(12);
        assertThat(mActivity.mEventsReceived.get(1).getY()).isEqualTo(7);
        assertThat(mActivity.mEventsReceived.get(2).getAction()).isEqualTo(MotionEvent.ACTION_UP);
        assertThat(mActivity.mEventsReceived.get(2).getX()).isEqualTo(12);
        assertThat(mActivity.mEventsReceived.get(2).getY()).isEqualTo(9);
    }

    @Test
    public void swipeGesture_whenActionDownInsideTaskView_capturingDisabled_noCapturing()
            throws Exception {
        // Arrange
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());
        verify(mActivity.mSpyWm).addView(mSpyWindowArgumentCaptor.capture(), any());
        createControlledCarTaskView(new Rect(10, 0, 30, 100), /* capturingEnabled= */ false);

        View spyWindow = mSpyWindowArgumentCaptor.getValue();
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent downEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_DOWN,
                        /* x= */ 11,
                        /* y= */ 2,
                        /* metaState= */ 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent moveEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_MOVE,
                        /* x= */ 12,
                        /* y= */ 7,
                        /* metaState= */ 0);
        moveEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent upEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_UP,
                        /* x= */ 12,
                        /* y= */ 9,
                        /* metaState= */ 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);

        // Act
        runOnMainAndWait(
                () -> {
                    spyWindow.dispatchTouchEvent(downEvent);
                    spyWindow.dispatchTouchEvent(moveEvent);
                    spyWindow.dispatchTouchEvent(upEvent);
                });

        // Assert
        verify(mActivity.mSpyInputManager, times(0)).pilferPointers(any());
        assertThat(mActivity.mEventsReceived.size()).isEqualTo(0);
    }

    @Test
    public void swipeGesture_whenActionDownOutsideTaskView_capturingEnabled_noCapturing()
            throws Exception {
        // Arrange
        runOnMainAndWait(() -> mTaskViewInputInterceptor.init());
        verify(mActivity.mSpyWm).addView(mSpyWindowArgumentCaptor.capture(), any());
        createControlledCarTaskView(new Rect(10, 0, 30, 100), /* capturingEnabled= */ true);

        View spyWindow = mSpyWindowArgumentCaptor.getValue();
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent downEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_DOWN,
                        /* x= */ 8,
                        /* y= */ 2,
                        /* metaState= */ 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent moveEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_MOVE,
                        /* x= */ 8,
                        /* y= */ 7,
                        /* metaState= */ 0);
        moveEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent upEvent =
                MotionEvent.obtain(
                        /* downTime= */ eventTime,
                        /* eventTime= */ eventTime,
                        MotionEvent.ACTION_UP,
                        /* x= */ 8,
                        /* y= */ 9,
                        /* metaState= */ 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);

        // Act
        runOnMainAndWait(
                () -> {
                    spyWindow.dispatchTouchEvent(downEvent);
                    spyWindow.dispatchTouchEvent(moveEvent);
                    spyWindow.dispatchTouchEvent(upEvent);
                });

        // Assert
        verify(mActivity.mSpyInputManager, times(0)).pilferPointers(any());
        assertThat(mActivity.mEventsReceived.size()).isEqualTo(0);
    }

    private ControlledCarTaskView createControlledCarTaskView(
            Rect bounds, boolean capturingEnabled) {
        ControlledCarTaskView taskView = mock(ControlledCarTaskView.class);

        doAnswer(
                invocation -> {
                    Rect r = invocation.getArgument(0);
                    r.set(bounds);
                    return null;
                })
                .when(taskView)
                .getBoundsOnScreen(any());

        doReturn(
                ControlledCarTaskViewConfig.builder()
                        .setActivityIntent(new Intent())
                        .setCaptureGestures(capturingEnabled)
                        .setCaptureLongPress(capturingEnabled)
                        .build())
                .when(taskView)
                .getConfig();
        mControlledCarTaskViews.add(taskView);
        return taskView;
    }

    private void runOnMainAndWait(Runnable r) throws Exception {
        mActivity
                .getMainExecutor()
                .execute(
                        () -> {
                            r.run();
                            mIdleHandlerLatch.countDown();
                            mIdleHandlerLatch = new CountDownLatch(1);
                        });
        mIdleHandlerLatch.await(5, TimeUnit.SECONDS);
    }

    public static class TestActivity extends Activity {
        private static final int FINISH_TIMEOUT_MS = 1000;
        private final CountDownLatch mDestroyed = new CountDownLatch(1);
        private final List<MotionEvent> mEventsReceived = new ArrayList<>();
        private WindowManager mSpyWm;
        private InputManager mSpyInputManager;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mSpyWm = spy(getSystemService(WindowManager.class));
            mSpyInputManager = spy(getSystemService(InputManager.class));
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // MotionEvent.obtain() is important otherwise the event is recycled and by the time
            // assertion happens, the values might be changed
            mEventsReceived.add(MotionEvent.obtain(event));
            return super.onTouchEvent(event);
        }

        @Override
        public Object getSystemService(@NonNull String name) {
            if (name.equals(Context.WINDOW_SERVICE)) {
                if (mSpyWm != null) {
                    return mSpyWm;
                }
            }
            if (name.equals(Context.INPUT_SERVICE)) {
                if (mSpyInputManager != null) {
                    return mSpyInputManager;
                }
            }
            return super.getSystemService(name);
        }

        void finishCompletely() throws InterruptedException {
            finish();
            mDestroyed.await(FINISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
