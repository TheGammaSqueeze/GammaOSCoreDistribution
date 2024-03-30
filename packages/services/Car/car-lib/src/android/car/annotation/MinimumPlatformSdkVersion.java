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

package android.car.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Minimum platform sdk version this method / type / field can be used.
 *
 * @deprecated use {@link ApiRequirements} instead.
 *
 * @hide
 */
@Retention(SOURCE)
@Target({ANNOTATION_TYPE, FIELD, TYPE, METHOD})
@Deprecated
public @interface MinimumPlatformSdkVersion {

    /**
     * Represents the minimum version of Android Platform required to call this API.
     *
     * <p> Represents the minimum version of the Android platform (as defined by
     * {@link android.os.Build.VERSION#SDK_INT}) that is required to call this API.
     */
    int majorVersion();

    /**
     * Represents the minor version of the Android platform required to call this API.
     *
     * <p> Represents the minimum minor version of the Android platform (as defined by
     * {@link android.car.Car#PLATFORM_VERSION_MINOR_INT}) that is required to call this API.
     *
     * <p> The standard Android SDK doesn't provide an API to check incremental versions of the
     * platform, but Car needs them as the Car APIs can be updated separately from the platform, and
     * in some cases some APIs might depend on services that are not updatable by Car. This version
     * would be incresed when Car API version is changed with the same {@link #majorVersion}.
     */
    int minorVersion() default 0;
}
