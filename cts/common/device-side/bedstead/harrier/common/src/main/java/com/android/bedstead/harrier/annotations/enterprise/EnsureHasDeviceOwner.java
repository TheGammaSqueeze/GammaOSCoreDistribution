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

package com.android.bedstead.harrier.annotations.enterprise;

import static com.android.bedstead.harrier.annotations.AnnotationRunPrecedence.MIDDLE;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_DEVICE_ADMIN;

import com.android.bedstead.harrier.annotations.AnnotationRunPrecedence;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.RequireFeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test requires that a device owner is available on the device.
 *
 * <p>Your test configuration may be configured so that this test is only run on a device which has
 * a device owner. Otherwise, you can use {@code Devicestate} to ensure that the device enters
 * the correct state for the method. If using {@code Devicestate}, you can use
 * {@code Devicestate#deviceOwner()} to interact with the device owner.
 *
 * <p>When running on a device with a headless system user, enforcing this with {@code Devicestate}
 * will also result in the profile owner of the current user being set to the same device policy
 * controller.
 *
 * <p>If {@code Devicestate} is required to set the device owner (because there isn't one already)
 * then all users and accounts may be removed from the device.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RequireFeature(FEATURE_DEVICE_ADMIN)
public @interface EnsureHasDeviceOwner {

    int DO_PO_WEIGHT = MIDDLE;

     /** Behaviour if the device owner cannot be set. */
    FailureMode failureMode() default FailureMode.FAIL;

    /**
     * Whether this DPC should be returned by calls to {@code Devicestate#dpc()} or
     * {@code Devicestate#policyManager()}}.
     *
     * <p>Only one policy manager per test should be marked as primary.
     */
    boolean isPrimary() default false;

    /**
     * Affiliation ids to be set for the device owner.
     */
    String[] affiliationIds() default {};

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
    int weight() default DO_PO_WEIGHT;
}
