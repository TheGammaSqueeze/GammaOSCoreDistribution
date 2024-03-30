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

package com.android.car.internal;

import android.car.ICarResultReceiver;
import android.content.Context;
import android.os.UserHandle;
import android.util.SparseArray;

/**
 * This is a base class for implementing NotificationHelper in car service builtin.
 *
 * <p> This is used as an interface between builtin and updatable car service. Do not change it
 * without compatibility check. Adding a new method is ok.
 *
 * @hide
 */
public abstract class NotificationHelperBase {

    /*
     * NOTE: IDs in the range {@code [RESOURCE_OVERUSE_NOTIFICATION_BASE_ID,
     * RESOURCE_OVERUSE_NOTIFICATION_BASE_ID + RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET)} are
     * reserved for car watchdog's resource overuse notifications.
     */
    /** Base notification id for car watchdog's resource overuse notifications. */
    public static final int RESOURCE_OVERUSE_NOTIFICATION_BASE_ID = 150;

    /** Maximum notification offset for car watchdog's resource overuse notifications. */
    public static final int RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET = 20;

    private final Context mContext;

    /** Creates with given {@code Context} which is used to create {@code Notification}. */
    public NotificationHelperBase(Context context) {
        mContext = context;
    }

    /** Returns {@code Cotext} to use to create {@code Notification}. */
    public Context getContext() {
        return mContext;
    }

    /**
     * Shows the user disclaimer notification. Actual impl should be done by a child class.
     */
    public void showUserDisclaimerNotification(UserHandle user) {};

    /**
     * Cancels the user disclaimer notification. Actual impl should be done by a child class.
     */
    public void cancelUserDisclaimerNotification(UserHandle user) {};

    /**
     * Shows the user car watchdog's resource overuse notifications. Actual impl should be done by
     * a child class.
     */
    public void showResourceOveruseNotificationsAsUser(
            UserHandle user, SparseArray<String> headsUpNotificationPackagesById,
            SparseArray<String> notificationCenterPackagesById) {};

    /**
     * Sends the notification warning the user about the factory reset. Actual impl should be done
     * by a child class.
     */
    public void showFactoryResetNotification(ICarResultReceiver callback) {};

    /**
     * Cancels a specific notification as a user. Actual impl should be done by a child class.
     */
    public void cancelNotificationAsUser(UserHandle user, int notificationId) {};
}
