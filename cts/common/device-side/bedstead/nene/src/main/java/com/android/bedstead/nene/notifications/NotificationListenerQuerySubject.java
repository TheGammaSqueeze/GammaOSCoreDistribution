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

package com.android.bedstead.nene.notifications;

import static com.google.common.truth.Truth.assertAbout;

import androidx.annotation.Nullable;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import java.time.Duration;

/** {@link Subject} for {@link NotificationListenerQuery}. */
public final class NotificationListenerQuerySubject extends Subject {

    @Nullable
    private final NotificationListenerQuery mActual;

    private NotificationListenerQuerySubject(FailureMetadata metadata,
            @Nullable NotificationListenerQuery actual) {
        super(metadata, actual);
        this.mActual = actual;
    }

    /**
     * Assertions about {@link NotificationListenerQuery}.
     */
    public static Factory<NotificationListenerQuerySubject, NotificationListenerQuery> notificationListenerQuery() {
        return NotificationListenerQuerySubject::new;
    }

    /**
     * Assertions about {@link NotificationListenerQuery}.
     */
    public static NotificationListenerQuerySubject assertThat(
            @Nullable NotificationListenerQuery actual) {
        return assertAbout(notificationListenerQuery()).that(actual);
    }

    /**
     * Asserts that a notification was posted (that {@link NotificationListenerQuery#poll()} returns
     * non-null).
     */
    public void wasPosted() {
        if (mActual.poll() == null) {
            // TODO(b/197315353): Add non-matching notifications
            failWithoutActual(
                    Fact.simpleFact("Expected notification to have been posted matching: "
                            + mActual + " but it was not posted."));
        }
    }

    /**
     * Asserts that a notification was posted (that {@link NotificationListenerQuery#poll(Duration)}
     * returns non-null).
     */
    public void wasPostedWithin(Duration timeout) {
        if (mActual.poll(timeout) == null) {
            // TODO(b/197315353): Add non-matching notifications
            failWithoutActual(Fact.simpleFact("Expected notification to have been posted matching: "
                    + mActual + " but it was not posted."));
        }
    }
}
