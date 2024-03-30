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

/**
 * A callback interface for the host activity that uses {@link CarTaskView} and its derivatives.
 */
interface CarTaskViewCallbacks {
    /**
     * Called when the underlying {@link CarTaskView} instance is created.
     *
     * @param taskView the new newly created {@link CarTaskView} instance.
     */
    void onTaskViewCreated(CarTaskView taskView);

    /**
     * Called when the underlying {@link CarTaskView} is ready. A {@link CarTaskView} can be
     * considered ready when it has completed all the set up that is required.
     * This callback is only triggered once.
     *
     * For {@link LaunchRootCarTaskView}, this is called once the launch root task has been
     * fully set up.
     * For {@link SemiControlledCarTaskView} & {@link ControlledCarTaskView} this is called when
     * the surface is created.
     */
    void onTaskViewReady();
}


