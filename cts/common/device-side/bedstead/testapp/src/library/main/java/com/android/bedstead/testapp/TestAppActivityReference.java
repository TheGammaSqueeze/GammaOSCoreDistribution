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

package com.android.bedstead.testapp;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.content.Intent;
import android.os.Bundle;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.eventlib.events.activities.ActivityEvents;

/**
 * A reference to an activity in a test app for which there may or may not be an instance.
 */
public abstract class TestAppActivityReference {

    final TestAppInstance mInstance;
    final ComponentReference mComponent;

    TestAppActivityReference(
            TestAppInstance instance,
            ComponentReference component) {
        mInstance = instance;
        mComponent = component;
    }

    /** Gets the {@link TestAppInstance} this activity exists in. */
    public TestAppInstance testAppInstance() {
        return mInstance;
    }

    /** Gets the {@link ComponentReference} for this activity. */
    public ComponentReference component() {
        return mComponent;
    }

    /**
     * Starts the activity.
     */
    public com.android.bedstead.nene.activities.Activity<TestAppActivity> start() {
        Intent intent = new Intent();
        intent.setComponent(mComponent.componentName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);

        if (mInstance.user().equals(TestApis.users().instrumented())) {
            TestApis.context().instrumentedContext().startActivity(intent);
        } else {
            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                TestApis.context().androidContextAsUser(mInstance.user()).startActivity(intent);
            }
        }

        events().activityStarted().waitForEvent();

        return TestApis.activities().wrap(
                TestAppActivity.class, new TestAppActivityImpl(mInstance, mComponent));
    }

    /**
     * Starts the activity.
     */
    public com.android.bedstead.nene.activities.Activity<TestAppActivity> start(Bundle options) {
        // TODO(scottjonathan): Use a connected call to ensure this succeeds cross-user
        Intent intent = new Intent();
        intent.setComponent(mComponent.componentName());
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            TestApis.context().instrumentedContext().startActivity(intent, options);
        }

        events().activityStarted().waitForEvent();

        return TestApis.activities().wrap(
                TestAppActivity.class, new TestAppActivityImpl(mInstance, mComponent));
    }

    /**
     * Query events for this activity.
     */
    public ActivityEvents events() {
        return ActivityEvents.forActivity(new TestAppActivityImpl(mInstance, mComponent));
    }
}
