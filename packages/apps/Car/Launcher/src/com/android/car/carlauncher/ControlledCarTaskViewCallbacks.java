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

import java.util.Collections;
import java.util.Set;

/**
 * A callback interface for {@link ControlledCarTaskView}.
 */
public interface ControlledCarTaskViewCallbacks extends
        CarTaskViewCallbacks {
    /**
     * @return a set of package names which the task in the ControlledCarTaskView depends upon.
     * When any of these packages are changed, it will lead to restart of the task.
     */
    default Set<String> getDependingPackageNames() {
        return Collections.emptySet();
    }
}
