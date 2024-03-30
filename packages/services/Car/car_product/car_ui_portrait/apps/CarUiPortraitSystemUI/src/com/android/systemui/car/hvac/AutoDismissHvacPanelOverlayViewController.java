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

package com.android.systemui.car.hvac;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.wm.shell.animation.FlingAnimationUtils;

import javax.inject.Inject;

/**
 * An extension of {@link HvacPanelOverlayViewController} which auto dismisses the panel if there
 * is no activity for some configured amount of time.
 */
@SysUISingleton
public class AutoDismissHvacPanelOverlayViewController extends HvacPanelOverlayViewController {

    private final Resources mResources;
    private final Handler mHandler;
    private final Context mContext;

    private HvacPanelView mHvacPanelView;
    private int mAutoDismissDurationMs;
    private float mPreviousHandleBarPositionY;
    private boolean mIsDragging;
    private ObjectAnimator mAnimation;

    private final Runnable mAutoDismiss = () -> {
        if (isPanelExpanded()) {
            toggle();
        }
    };

    @Inject
    public AutoDismissHvacPanelOverlayViewController(Context context,
            @Main Resources resources,
            HvacController hvacController,
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            FlingAnimationUtils.Builder flingAnimationUtilsBuilder,
            CarDeviceProvisionedController carDeviceProvisionedController,
            @Main Handler handler,
            ConfigurationController configurationController,
            UiModeManager uiModeManager) {
        super(context, resources, hvacController, overlayViewGlobalStateController,
                flingAnimationUtilsBuilder, carDeviceProvisionedController, configurationController,
                uiModeManager);
        mResources = resources;
        mHandler = handler;
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAutoDismissDurationMs = mResources.getInteger(R.integer.config_hvacAutoDismissDurationMs);

        mHvacPanelView = getLayout().findViewById(R.id.hvac_panel);
        mHvacPanelView.setMotionEventHandler(event -> {
            if (!isPanelExpanded()) {
                return;
            }

            mHandler.removeCallbacks(mAutoDismiss);
            mHandler.postDelayed(mAutoDismiss, mAutoDismissDurationMs);
        });
    }

    @Override
    protected void onAnimateExpandPanel() {
        super.onAnimateExpandPanel();

        mHandler.postDelayed(mAutoDismiss, mAutoDismissDurationMs);
    }

    @Override
    protected void onAnimateCollapsePanel() {
        super.onAnimateCollapsePanel();

        mHandler.removeCallbacks(mAutoDismiss);
    }

    @Override
    protected void animate(float from, float to, float velocity, boolean isClosing) {
        if (isAnimating()) {
            return;
        }
        mIsAnimating = true;
        setIsTracking(true);

        ObjectAnimator animation = ObjectAnimator
                .ofFloat(getLayout(), "translationY", from, to);

        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mIsAnimating = false;
                setIsTracking(false);
                mOpeningVelocity = DEFAULT_FLING_VELOCITY;
                mClosingVelocity = DEFAULT_FLING_VELOCITY;
                if (isClosing) {
                    resetPanelVisibility();
                } else {
                    onExpandAnimationEnd();
                    setPanelExpanded(true);
                    setViewClipBounds((int) to);
                }
            }
        });
        getFlingAnimationUtils().apply(animation, from, to, Math.abs(velocity));
        animation.start();
    }

    @Override
    protected int getCurrentStartPosition(Rect clipBounds) {
        if (mIsDragging) {
            return (int) mPreviousHandleBarPositionY;
        }

        return mAnimateDirection > 0 ? clipBounds.bottom : clipBounds.top;
    }

    @Override
    protected void setUpHandleBar() {
        Integer handleBarViewId = getHandleBarViewId();
        if (handleBarViewId == null) return;
        View handleBar = getLayout().findViewById(handleBarViewId);
        if (handleBar == null) return;
        GestureDetector handleBarCloseGestureDetector =
                new GestureDetector(mContext, new HandleBarCloseGestureListener());
        handleBar.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                    maybeCompleteAnimation(event);
                    mPreviousHandleBarPositionY = 0;
                    mIsDragging = false;
                    // Intentionally not breaking here, since handleBarClosureGestureDetector's
                    // onTouchEvent should still be called with MotionEvent.ACTION_UP.
                default:
                    handleBarCloseGestureDetector.onTouchEvent(event);
                    return true;
            }
        });
    }

    /**
     * A GestureListener to be installed on the handle bar.
     */
    private class HandleBarCloseGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
                float distanceY) {

            mIsDragging = true;
            calculatePercentageFromEndingEdge(event2.getRawY());
            calculatePercentageCursorPositionOnScreen(event2.getRawY());
            // To prevent the jump in the clip bounds while closing the panel using
            // the handle bar, we should calculate the height using the diff of event1 and event2.
            // This will help the notification shade to clip smoothly as the event2 value changes
            // as event1 value will be fixed.
            float diff = mAnimateDirection * (event1.getRawY() - event2.getRawY());
            float y = mAnimateDirection > 0
                    ? getLayout().getHeight() - diff
                    : diff;
            // Ensure the position is within the overlay panel.
            y = Math.max(0, Math.min(y, getLayout().getHeight()));
            ObjectAnimator animation = ObjectAnimator
                    .ofFloat(getLayout(), "translationY", mPreviousHandleBarPositionY, y);
            mPreviousHandleBarPositionY = y;
            if (mAnimation != null && mAnimation.isRunning()) {
                mAnimation.cancel();
            }
            animation.start();
            mAnimation = animation;
            return true;
        }
    }
}
