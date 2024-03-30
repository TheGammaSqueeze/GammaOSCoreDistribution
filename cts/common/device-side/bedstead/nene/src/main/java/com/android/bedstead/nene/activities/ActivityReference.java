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

package com.android.bedstead.nene.activities;

import android.content.ComponentName;

import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.packages.Package;

/**
 * A representation of an activity on device which may or may not exist.
 */
public final class ActivityReference extends ComponentReference {
    public ActivityReference(Package packageName, String className) {
        super(packageName, className);
    }

    public ActivityReference(ComponentName component) {
        super(component);
    }

    public ActivityReference(ComponentReference component) {
        super(component.componentName());
    }

    @Override
    public String toString() {
        return "ActivityReference{component=" + super.toString() + "}";
    }
}
