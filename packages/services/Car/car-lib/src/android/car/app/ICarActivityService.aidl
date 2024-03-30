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

package android.car.app;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import java.util.List;


/** @hide */
interface ICarActivityService {
    /**
     * Designates the given {@code activity} to be launched in {@code TaskDisplayArea} of
     * {@code featureId} in the display of {@code displayId}.
     */
    int setPersistentActivity(in ComponentName activity, int displayId, int featureId) = 0;

    /**
     * Registers the caller as TaskMonitor, which can provide Task lifecycle events to CarService.
     * The caller should provide a binder token, which is used to check if the given TaskMonitor is
     * live and the reported events are from the legitimate TaskMonitor.
     */
    void registerTaskMonitor(in IBinder token) = 1;

    /**
     * Reports that a Task is created.
     */
    void onTaskAppeared(in IBinder token, in RunningTaskInfo taskInfo) = 2;

    /**
     * Reports that a Task is vanished.
     */
    void onTaskVanished(in IBinder token, in RunningTaskInfo taskInfo) = 3;

    /**
     * Reports that some Task's states are changed.
     */
    void onTaskInfoChanged(in IBinder token, in RunningTaskInfo taskInfo) = 4;

    /**
     * Unregisters the caller from TaskMonitor.
     */
    void unregisterTaskMonitor(in IBinder token) = 5;

    /**
     * Returns all the visible tasks ordered in top to bottom manner.
     */
    List<RunningTaskInfo> getVisibleTasks() = 6;
}

