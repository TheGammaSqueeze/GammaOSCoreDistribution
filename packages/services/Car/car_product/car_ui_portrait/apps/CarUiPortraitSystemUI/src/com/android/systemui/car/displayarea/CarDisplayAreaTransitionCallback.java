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

import android.graphics.Rect;

/**
 * The start or stop display area transition callback.
 */
public interface CarDisplayAreaTransitionCallback {
    /**
     * Called when one display area entering or exiting transition starting
     */
    default void onStartTransition(boolean isEntering) {
    }

    /**
     * Called when start display area transition finished
     */
    default void onStartFinished(Rect bounds) {
    }

    /**
     * Called when stop one display area transition finished
     */
    default void onStopFinished(Rect bounds) {
    }
}
