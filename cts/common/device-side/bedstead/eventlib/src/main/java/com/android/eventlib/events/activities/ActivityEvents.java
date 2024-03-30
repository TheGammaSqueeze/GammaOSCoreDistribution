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

package com.android.eventlib.events.activities;

import android.content.ComponentName;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.activities.Activity;
import com.android.bedstead.nene.activities.NeneActivity;
import com.android.bedstead.nene.users.UserReference;

/**
 * Quick access to event queries about activities.
 */
public interface ActivityEvents {
    /** Access events for activity. */
    static ActivityEvents forActivity(NeneActivity activity) {
        return new ActivityEventsImpl(activity);
    }

    /** Access events for activity. */
    static ActivityEvents forActivity(Activity<? extends NeneActivity> activity) {
        return new ActivityEventsImpl(activity.activity());
    }

    /** Access events for activity on the instrumented user. */
    static ActivityEvents forActivity(ComponentName componentName) {
        return forActivity(componentName, TestApis.users().instrumented());
    }

    /** Access events for activity. */
    static ActivityEvents forActivity(ComponentName componentName, UserReference user) {
        return new ActivityEventsImpl(componentName, user.userHandle());
    }

    /**
     * Query for when an activity is created.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ActivityCreatedEvent.ActivityCreatedEventQuery activityCreated();

    /**
     * Query for when an activity is destroyed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ActivityDestroyedEvent.ActivityDestroyedEventQuery activityDestroyed();

    /**
     * Query for when an activity is paused.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ActivityPausedEvent.ActivityPausedEventQuery activityPaused();

    /**
     * Query for when an activity is restarted.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ActivityRestartedEvent.ActivityRestartedEventQuery activityRestarted();

    /**
     * Query for when an activity is resumed.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ActivityResumedEvent.ActivityResumedEventQuery activityResumed();

    /**
     * Query for when an activity is started.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ActivityStartedEvent.ActivityStartedEventQuery activityStarted();

    /**
     * Query for when an activity is stopped.
     *
     * <p>Additional filters can be added to the returned object.
     *
     * <p>{@code #poll} can be used to fetch results, and the result can be asserted on.
     */
    ActivityStoppedEvent.ActivityStoppedEventQuery activityStopped();
}
