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

import androidx.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Registered notification listener for receiving notifications on device.
 */
public final class NotificationListener implements AutoCloseable {

    private final Notifications mNotifications;

    private final Deque<StatusBarNotification> mStatusBarNotifications =
            new ArrayDeque<>();

    NotificationListener(Notifications notifications) {
        mNotifications = notifications;
    }

    @Override
    public void close() {
        mNotifications.removeListener(this);
    }

    /**
     * Query received notifications.
     */
    public NotificationListenerQuery query() {
        return new NotificationListenerQuery(mStatusBarNotifications);
    }

    /**
     * Poll for received notifications.
     */
    public @Nullable StatusBarNotification poll() {
        return query().poll();
    }

    /**
     * Poll for received notifications within {@link Duration}.
     */
    public @Nullable StatusBarNotification poll(Duration duration) {
        return query().poll(duration);
    }

    void onNotificationPosted(StatusBarNotification sbn) {
        mStatusBarNotifications.addLast(sbn);
    }
}
