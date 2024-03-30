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

import android.service.notification.StatusBarNotification;

import com.android.bedstead.nene.exceptions.NeneException;
import com.android.queryable.Queryable;
import com.android.queryable.queries.NotificationQuery;
import com.android.queryable.queries.NotificationQueryHelper;
import com.android.queryable.queries.StringQuery;
import com.android.queryable.queries.StringQueryHelper;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;

/** Query for notifications. */
public final class NotificationListenerQuery implements Queryable {

    private final StringQueryHelper<NotificationListenerQuery> mPackageName =
            new StringQueryHelper<>(this);
    private final NotificationQueryHelper<NotificationListenerQuery> mNotification =
            new NotificationQueryHelper<>(this);

    private boolean mHasStartedFetchingResults = false;
    private final Deque<StatusBarNotification> mStatusBarNotifications;
    private int mSkippedPollResults = 0;

    NotificationListenerQuery(Deque<StatusBarNotification> statusBarNotifications) {
        mStatusBarNotifications = statusBarNotifications;
    }

    /** Query by package name. */
    public StringQuery<NotificationListenerQuery> wherePackageName() {
        if (mHasStartedFetchingResults) {
            throw new IllegalStateException("Cannot modify query after fetching results");
        }
        return mPackageName;
    }

    /** Query by notification content. */
    public NotificationQuery<NotificationListenerQuery> whereNotification() {
        if (mHasStartedFetchingResults) {
            throw new IllegalStateException("Cannot modify query after fetching results");
        }
        return mNotification;
    }

    /** Poll for matching notifications. */
    public StatusBarNotification poll() {
        return poll(Duration.ofSeconds(30));
    }

    /** Poll for matching notifications. */
    public StatusBarNotification poll(Duration timeout) {
        mHasStartedFetchingResults = true;
        Instant endTime = Instant.now().plus(timeout);

        while (Instant.now().isBefore(endTime)) {
            StatusBarNotification nextResult = get(mSkippedPollResults);
            if (nextResult != null) {
                mSkippedPollResults++;
                return nextResult;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new NeneException("Interrupted while polling", e);
            }
        }

        return null;
    }

    private StatusBarNotification get(int skipResults) {
        mHasStartedFetchingResults = true;
        for (StatusBarNotification m : mStatusBarNotifications) {
            if (matches(m)) {
                skipResults -= 1;
                if (skipResults < 0) {
                    return m;
                }
            }
        }

        return null;
    }

    private boolean matches(StatusBarNotification sbn) {
        return StringQueryHelper.matches(mPackageName, sbn.getPackageName())
                && NotificationQueryHelper.matches(mNotification, sbn.getNotification());
    }

    @Override
    public String describeQuery(String fieldName) {
        return "{" + Queryable.joinQueryStrings(
                mPackageName.describeQuery("packageName"),
                mNotification.describeQuery("notification")
        ) + "}";
    }

    @Override
    public String toString() {
        return describeQuery("");
    }
}
