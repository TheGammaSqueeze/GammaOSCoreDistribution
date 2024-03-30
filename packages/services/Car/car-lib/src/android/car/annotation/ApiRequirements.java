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

package android.car.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.annotation.TestApi;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Defines in which version of Car and platform SDK the annotated API is supported.
 *
 * @hide
 */
@Retention(RUNTIME)
@Target({ANNOTATION_TYPE, FIELD, TYPE, METHOD})
@TestApi
public @interface ApiRequirements {

    /**
     * Indicates the minimum Car SDK version required for the annotated API.
     *
     * <p>Clients can check it calling {@code Car.getCarApiVersion.isAtLeast(
     * CarApiVersion.VERSION_CODES.VERSION_DEFINED_IN_THIS_ANNOTATION)}.
     */
    CarVersion minCarVersion();

    /**
     * Indicates the minimum Platform SDK version required for the annotated API.
     *
     * <p>Clients can check it calling {@code Car.getPlatformApiVersion.isAtLeast(
     * PlatformApiVersion.VERSION_CODES.VERSION_DEFINED_IN_THIS_ANNOTATION)}.
     */
    PlatformVersion minPlatformVersion();

    @SuppressWarnings("Enum")
    enum CarVersion {

        TIRAMISU_0(android.car.CarVersion.VERSION_CODES.TIRAMISU_0),
        TIRAMISU_1(android.car.CarVersion.VERSION_CODES.TIRAMISU_1),
        TIRAMISU_2(android.car.CarVersion.VERSION_CODES.TIRAMISU_2),
        TIRAMISU_3(android.car.CarVersion.VERSION_CODES.TIRAMISU_3);

        private final android.car.CarVersion mVersion;

        CarVersion(android.car.CarVersion version) {
            mVersion = version;
        }

        /**
         * Gets the {@link CarVersion} associated with it.
         */
        public android.car.CarVersion get() {
            return mVersion;
        }
    }

    @SuppressWarnings("Enum")
    enum PlatformVersion {

        TIRAMISU_0(android.car.PlatformVersion.VERSION_CODES.TIRAMISU_0),
        TIRAMISU_1(android.car.PlatformVersion.VERSION_CODES.TIRAMISU_1),
        TIRAMISU_2(android.car.PlatformVersion.VERSION_CODES.TIRAMISU_2),
        TIRAMISU_3(android.car.PlatformVersion.VERSION_CODES.TIRAMISU_3);

        private final android.car.PlatformVersion mVersion;

        PlatformVersion(android.car.PlatformVersion version) {
            mVersion = version;
        }

        /**
         * Gets the {@link PlatformVersion} associated with it.
         */
        public android.car.PlatformVersion get() {
            return mVersion;
        }
    }
}
