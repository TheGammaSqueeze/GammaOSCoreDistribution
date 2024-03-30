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

package android.car.builtin.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.TaskInfo;
import android.car.builtin.annotation.AddedIn;
import android.car.builtin.annotation.PlatformVersion;

/**
 * Provides the access to the hidden fields of {@code android.app.TaskInfo}.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class TaskInfoHelper {

    /** Gets the id of the display this task is associated with. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int getDisplayId(@NonNull TaskInfo task) {
        return task.displayId;
    }

    /** Gets the id of the user the task was running as if this is a leaf task. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int getUserId(@NonNull TaskInfo task) {
        return task.userId;
    }

    /** Returns whether the task is actually visible or not */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean isVisible(@NonNull TaskInfo task) {
        return task.isVisible && task.isRunning && !task.isSleeping;
    }

    /** Returns the string representation of the task */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static String toString(@Nullable TaskInfo task) {
        if (task == null) {
            return "null";
        }
        return "TaskInfo{"
                + "taskId=" + task.taskId
                + " userId=" + task.userId
                + " displayId=" + task.displayId
                + " isFocused=" + task.isFocused
                + " isVisible=" + task.isVisible
                + " isRunning=" + task.isRunning
                + " isSleeping=" + task.isSleeping
                + " topActivity=" + task.topActivity
                + " baseIntent=" + task.baseIntent + " baseActivity=" + task.baseActivity
                + "}";
    }

    private TaskInfoHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
