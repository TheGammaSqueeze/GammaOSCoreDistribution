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

package com.android.server.wm.traces.common.service

object PlatformConsts {
    /**
     * The default Display id, which is the id of the primary display assuming there is one.
     *
     * Duplicated from [Display.DEFAULT_DISPLAY] because this class is used by JVM and KotlinJS
     */
    const val DEFAULT_DISPLAY = 0

    /**
     * Window type: an application window that serves as the "base" window
     * of the overall application
     *
     * Duplicated from [WindowManager.LayoutParams.TYPE_BASE_APPLICATION] because this class
     * is used by JVM and KotlinJS
     */
    const val TYPE_BASE_APPLICATION = 1

    /**
     * Window type: special application window that is displayed while the
     * application is starting
     *
     * Duplicated from [WindowManager.LayoutParams.TYPE_APPLICATION_STARTING] because this class
     * is used by JVM and KotlinJS
     */
    const val TYPE_APPLICATION_STARTING = 3

    /**
     * Rotation constant: 0 degree rotation (natural orientation)
     *
     * Duplicated from [Surface.ROTATION_0] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_0 = 0

    /**
     * Rotation constant: 90 degree rotation.
     *
     * Duplicated from [Surface.ROTATION_90] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_90 = 1

    /**
     * Rotation constant: 180 degree rotation.
     *
     * Duplicated from [Surface.ROTATION_180] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_180 = 2

    /**
     * Rotation constant: 270 degree rotation.
     *
     * Duplicated from [Surface.ROTATION_270] because this class is used by JVM and KotlinJS
     */
    const val ROTATION_270 = 3
}