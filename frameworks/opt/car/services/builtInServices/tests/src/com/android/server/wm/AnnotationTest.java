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

package com.android.server.wm;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.annotation.AddedIn;

import static android.car.test.util.AnnotationHelper.checkForAnnotation;

import org.junit.Test;

public class AnnotationTest {
    private static final String[] CAR_SERVICE_HELPER_SERVICE_CLASSES = new String[] {
            "com.android.internal.car.CarServiceHelperInterface",
            "com.android.internal.car.CarServiceHelperServiceUpdatable",
            "com.android.server.wm.ActivityOptionsWrapper",
            "com.android.server.wm.ActivityRecordWrapper",
            "com.android.server.wm.CalculateParams",
            "com.android.server.wm.CarLaunchParamsModifierInterface",
            "com.android.server.wm.CarLaunchParamsModifierUpdatable",
            "com.android.server.wm.LaunchParamsWrapper",
            "com.android.server.wm.RequestWrapper",
            "com.android.server.wm.TaskDisplayAreaWrapper",
            "com.android.server.wm.TaskWrapper",
            "com.android.server.wm.WindowLayoutWrapper"
            };
    @Test
    public void testCarHelperServiceAPIAddedInAnnotation() throws Exception {
        checkForAnnotation(CAR_SERVICE_HELPER_SERVICE_CLASSES, AddedIn.class);
    }
}

