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

import static com.android.systemui.car.displayarea.CarDisplayAreaAnimationController.CarDisplayAreaTransitionAnimator;

import android.view.SurfaceControl;

/**
 * Display area animation callback.
 */
public interface CarDisplayAreaAnimationCallback {
    /**
     * Called when display area animation is started.
     */
    default void onAnimationStart(CarDisplayAreaTransitionAnimator animator) {
    }

    /**
     * Called when display area animation is ended.
     */
    default void onAnimationEnd(SurfaceControl.Transaction tx,
            CarDisplayAreaTransitionAnimator animator) {
    }

    /**
     * Called when display area animation is cancelled.
     */
    default void onAnimationCancel(CarDisplayAreaTransitionAnimator animator) {
    }

    /**
     * Called when display area animator is updating position
     */
    default void onAnimationUpdate(float xPos, float yPos) {
    }
}
