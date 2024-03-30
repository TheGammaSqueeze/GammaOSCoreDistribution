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

package com.android.bedstead.harrier.annotations;

import static com.android.bedstead.harrier.OptionalBoolean.ANY;
import static com.android.bedstead.harrier.UserType.PRIMARY_USER;
import static com.android.bedstead.harrier.annotations.AnnotationRunPrecedence.MIDDLE;

import com.android.bedstead.harrier.OptionalBoolean;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.annotations.meta.EnsureHasProfileAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test method should run on a user which has a work profile.
 *
 * <p>Use of this annotation implies
 * {@code RequireFeature("android.software.managed_users", SKIP)}.
 *
 * <p>Your test configuration may be configured so that this test is only run on a user which has
 * a work profile. Otherwise, you can use {@code Devicestate} to ensure that the device enters
 * the correct state for the method.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EnsureHasProfileAnnotation(value = "android.os.usertype.profile.MANAGED", hasProfileOwner = true)
@RequireFeature("android.software.managed_users")
@EnsureHasNoDeviceOwner // TODO: This should only apply on Android R+
public @interface EnsureHasWorkProfile {
    /** Which user type the work profile should be attached to. */
    UserType forUser() default PRIMARY_USER; // Currently only the primary user is supported

    /** Whether the instrumented test app should be installed in the work profile. */
    OptionalBoolean installInstrumentedApp() default ANY;

    /**
     * Whether the profile owner's DPC should be returned by calls to {@code Devicestate#dpc()}.
     *
     * <p>Only one device policy controller per test should be marked as primary.
     */
    boolean dpcIsPrimary() default false;

    /**
     * If true, uses the {@code DevicePolicyManager#getParentProfileInstance(ComponentName)}
     * instance of the dpc when calling to .dpc()
     *
     * <p>Only used if {@link #dpcIsPrimary()} is true.
     */
    boolean useParentInstanceOfDpc() default false;

    /**
     * Should we ensure that we are switched to the parent of the profile.
     */
    OptionalBoolean switchedToParentUser() default ANY;

    /**
     * Weight sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower weight will be resolved before annotations with a higher weight.
     *
     * <p>If there is an order requirement between annotations, ensure that the weight of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Weight can be set to a {@link AnnotationRunPrecedence} constant, or to any {@link int}.
     */
    int weight() default MIDDLE;
}
