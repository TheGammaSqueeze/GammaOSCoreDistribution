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

package android.car;

import static android.car.test.util.AnnotationHelper.checkForAnnotation;

import android.car.annotation.AddedInOrBefore;
import android.car.annotation.ApiRequirements;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.R;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public final class AnnotationTest {
    private static final String TAG = AnnotationTest.class.getSimpleName();

    @Test
    public void testCarAPIApiRequirementsAnnotation() throws Exception {
        checkForAnnotation(readFile(R.raw.car_api_classes), ApiRequirements.class,
                AddedInOrBefore.class);
    }

    @Test
    public void testCarBuiltInAPIAddedInAnnotation() throws Exception {
        checkForAnnotation(readFile(R.raw.car_built_in_api_classes),
                android.car.builtin.annotation.AddedIn.class);
    }

    private String[] readFile(int resourceId) throws IOException {
        try (InputStream configurationStream = ApplicationProvider.getApplicationContext()
                .getResources().openRawResource(resourceId)) {
            return new String(configurationStream.readAllBytes()).split("\n");
        }
    }
}
