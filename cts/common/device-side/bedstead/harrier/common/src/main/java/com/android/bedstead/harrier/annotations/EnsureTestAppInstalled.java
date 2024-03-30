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

import static com.android.bedstead.harrier.annotations.AnnotationRunPrecedence.MIDDLE;

import com.android.bedstead.harrier.UserType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test requires the given test app to be installed on the given user.
 *
 * <p>You should use {@code Devicestate} to ensure that the device enters
 * the correct state for the method. You can use {@code Devicestate#delegate()} to interact with
 * the delegate.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(EnsureTestAppInstalledGroup.class)
public @interface EnsureTestAppInstalled {

    int ENSURE_TEST_APP_INSTALLED_WEIGHT = MIDDLE;

    String DEFAULT_TEST_APP_KEY = "testApp";

    /** A key which uniquely identifies the test app for the test. */
    String key() default DEFAULT_TEST_APP_KEY;

    /** The package name of the testapp. */
    String packageName();

    /** The user the testApp should be installed on. */
    UserType onUser() default UserType.INSTRUMENTED_USER;

    /**
     * Whether this testApp should be returned by calls to {@code DeviceState#policyManager()}.
     *
     * <p>Only one policy manager per test should be marked as primary.
     */
    boolean isPrimary() default false;

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
    int weight() default ENSURE_TEST_APP_INSTALLED_WEIGHT;
}
