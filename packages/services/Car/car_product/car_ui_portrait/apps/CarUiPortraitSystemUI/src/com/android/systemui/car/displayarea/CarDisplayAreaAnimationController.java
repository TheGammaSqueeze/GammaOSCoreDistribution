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

package com.android.systemui.car.displayarea;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.window.WindowContainerToken;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller class of display area animations (between different states).
 */
public class CarDisplayAreaAnimationController {
    private static final String TAG = "CarDisplayAreaAnimationController";
    private static final float FRACTION_START = 0f;
    private static final float FRACTION_END = 1f;

    public static final int TRANSITION_DIRECTION_NONE = 0;
    public static final int TRANSITION_DIRECTION_TRIGGER = 1;
    public static final int TRANSITION_DIRECTION_EXIT = 2;

    private final CarDisplayAreaTransactionHelper mSurfaceTransactionHelper;
    private final ArrayMap<WindowContainerToken,
            CarDisplayAreaTransitionAnimator>
            mAnimatorMap = new ArrayMap<>();

    /**
     * Constructor of CarDisplayAreaAnimationController
     */
    public CarDisplayAreaAnimationController(Context context) {
        mSurfaceTransactionHelper = new CarDisplayAreaTransactionHelper(context);
    }

    @SuppressWarnings("unchecked")
    CarDisplayAreaTransitionAnimator getAnimator(
            WindowContainerToken token, SurfaceControl leash,
            float startPos, float endPos) {
        CarDisplayAreaTransitionAnimator animator = mAnimatorMap.get(token);
        if (animator == null) {
            mAnimatorMap.put(token, setupDisplayAreaTransitionAnimator(
                    CarDisplayAreaTransitionAnimator.ofYOffset(
                            token, leash, startPos, endPos)));
        } else if (animator.isRunning()) {
            animator.updateEndValue(endPos);
        } else {
            animator.cancel();
            mAnimatorMap.put(token, setupDisplayAreaTransitionAnimator(
                    CarDisplayAreaTransitionAnimator.ofYOffset(
                            token, leash, startPos, endPos)));
        }
        return mAnimatorMap.get(token);
    }

    ArrayMap<WindowContainerToken,
            CarDisplayAreaTransitionAnimator> getAnimatorMap() {
        return mAnimatorMap;
    }

    boolean isAnimatorsConsumed() {
        return mAnimatorMap.isEmpty();
    }

    void removeAnimator(WindowContainerToken token) {
        CarDisplayAreaTransitionAnimator animator = mAnimatorMap.remove(token);
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    CarDisplayAreaTransitionAnimator setupDisplayAreaTransitionAnimator(
            CarDisplayAreaTransitionAnimator animator) {
        animator.setSurfaceTransactionHelper(mSurfaceTransactionHelper);
        animator.setInterpolator(new CarCubicBezierInterpolator(0.5f, 0, 0, 1));
        animator.setFloatValues(FRACTION_START, FRACTION_END);
        return animator;
    }

    /**
     * Animator for display area transition animation which supports both alpha and bounds
     * animation.
     */
    public abstract static class CarDisplayAreaTransitionAnimator extends
            ValueAnimator implements
            ValueAnimator.AnimatorUpdateListener,
            ValueAnimator.AnimatorListener {

        private final SurfaceControl mLeash;
        private final WindowContainerToken mToken;
        private float mStartValue;
        private float mEndValue;
        private float mCurrentValue;

        private final List<CarDisplayAreaAnimationCallback>
                mDisplayAreaAnimationCallbacks =
                new ArrayList<>();
        private CarDisplayAreaTransactionHelper mSurfaceTransactionHelper;
        private final CarDisplayAreaTransactionHelper.SurfaceControlTransactionFactory
                mSurfaceControlTransactionFactory;

        int mTransitionDirection;

        private CarDisplayAreaTransitionAnimator(WindowContainerToken token,
                SurfaceControl leash,
                float startValue, float endValue) {
            mLeash = leash;
            mToken = token;
            mStartValue = startValue;
            mEndValue = endValue;
            addListener(this);
            addUpdateListener(this);
            mSurfaceControlTransactionFactory = SurfaceControl.Transaction::new;
            mTransitionDirection = TRANSITION_DIRECTION_NONE;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mCurrentValue = mStartValue;
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationStart(this)
            );
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mCurrentValue = mEndValue;
            SurfaceControl.Transaction tx = newSurfaceControlTransaction();
            onEndTransaction(mLeash, tx);
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationEnd(tx, this)
            );
            mDisplayAreaAnimationCallbacks.clear();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCurrentValue = mEndValue;
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationCancel(this)
            );
            mDisplayAreaAnimationCallbacks.clear();
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            SurfaceControl.Transaction tx = newSurfaceControlTransaction();
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationUpdate(0f, mCurrentValue)
            );
            applySurfaceControlTransaction(mLeash, tx, animation.getAnimatedFraction());
        }

        void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
        }

        void onEndTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
        }

        abstract void applySurfaceControlTransaction(SurfaceControl leash,
                SurfaceControl.Transaction tx, float fraction);

        CarDisplayAreaTransactionHelper getSurfaceTransactionHelper() {
            return mSurfaceTransactionHelper;
        }

        void setSurfaceTransactionHelper(CarDisplayAreaTransactionHelper helper) {
            mSurfaceTransactionHelper = helper;
        }

        CarDisplayAreaTransitionAnimator addDisplayAreaAnimationCallback(
                CarDisplayAreaAnimationCallback callback) {
            mDisplayAreaAnimationCallbacks.add(callback);
            return this;
        }

        WindowContainerToken getToken() {
            return mToken;
        }

        float getStartValue() {
            return mStartValue;
        }

        float getEndValue() {
            return mEndValue;
        }

        void setCurrentValue(float value) {
            mCurrentValue = value;
        }

        /**
         * Updates the {@link #mEndValue}.
         */
        void updateEndValue(float endValue) {
            mEndValue = endValue;
        }

        SurfaceControl.Transaction newSurfaceControlTransaction() {
            return mSurfaceControlTransactionFactory.getTransaction();
        }

        @VisibleForTesting
        static CarDisplayAreaTransitionAnimator ofYOffset(
                WindowContainerToken token,
                SurfaceControl leash, float startValue, float endValue) {

            return new CarDisplayAreaTransitionAnimator(
                    token, leash, startValue, endValue) {

                private float getCastedFractionValue(float start, float end, float fraction) {
                    return ((end - start) * fraction) + start;
                }

                @Override
                void applySurfaceControlTransaction(SurfaceControl leash,
                        SurfaceControl.Transaction tx, float fraction) {
                    float start = getStartValue();
                    float end = getEndValue();
                    float currentValue = getCastedFractionValue(start, end, fraction);
                    setCurrentValue(currentValue);
                    getSurfaceTransactionHelper()
                            .round(tx, leash)
                            .translate(tx, leash, currentValue);
                    tx.apply();
                }

                @Override
                void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
                    getSurfaceTransactionHelper()
                            .round(tx, leash)
                            .translate(tx, leash, getStartValue());
                    tx.apply();
                }
            };
        }
    }
}

