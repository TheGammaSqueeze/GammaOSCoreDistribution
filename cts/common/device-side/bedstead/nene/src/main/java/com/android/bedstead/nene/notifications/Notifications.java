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

import static android.Manifest.permission.MANAGE_NOTIFICATION_LISTENERS;

import static com.android.bedstead.nene.utils.Tags.USES_DEVICESTATE;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.nene.utils.Versions;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Helper methods related to notifications. */
public final class Notifications {
    public static final Notifications sInstance = new Notifications();

    private final Set<NotificationListener> mRegisteredListeners =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean mListenerAccessIsGranted;

    static final ComponentReference LISTENER_COMPONENT = TestApis.packages().component(
            new ComponentName(
                    TestApis.context().instrumentedContext().getPackageName(),
                    NeneNotificationListenerService.class.getCanonicalName())
    );

    private Notifications() {

    }

    /**
     * Creates a {@link NotificationListener}.
     *
     * <p>This is required before interacting with notifications in any way. It is recommended that
     * you do this in a try() block so that the {@link NotificationListener} closes when you are
     * finished with it.
     */
    public NotificationListener createListener() {
        if (Tags.hasTag(USES_DEVICESTATE) && !Tags.hasTag(Tags.USES_NOTIFICATIONS)) {
            throw new NeneException(
                    "Tests which use Notifications must be annotated @NotificationsTest");
        }

        NotificationListener notificationListener = new NotificationListener(this);
        mRegisteredListeners.add(notificationListener);
        initListenerIfRequired();

        return notificationListener;
    }

    void removeListener(NotificationListener listener) {
        mRegisteredListeners.remove(listener);
        teardownListenerIfRequired();
    }

    private void initListenerIfRequired() {
        if (mRegisteredListeners.isEmpty()) {
            return;
        }

        if (mListenerAccessIsGranted) {
            return;
        }

        mListenerAccessIsGranted = true;
        setNotificationListenerAccessGranted(
                LISTENER_COMPONENT, /* granted= */ true, TestApis.users().instrumented());
    }

    private void teardownListenerIfRequired() {
        if (!mRegisteredListeners.isEmpty()) {
            return;
        }

        if (!mListenerAccessIsGranted) {
            return;
        }

        mListenerAccessIsGranted = false;
        setNotificationListenerAccessGranted(
                LISTENER_COMPONENT, /* granted= */ false, TestApis.users().instrumented());
    }

    void onNotificationPosted(StatusBarNotification sbn) {
        for (NotificationListener notificationListener : mRegisteredListeners) {
            notificationListener.onNotificationPosted(sbn);
        }
    }

    /**
     * See {@link NotificationManager#setNotificationListenerAccessGranted(ComponentName, boolean)}.
     */
    public void setNotificationListenerAccessGranted(
            ComponentReference listener, boolean granted, UserReference user) {
        if (listener == null || user == null) {
            throw new NullPointerException();
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            String command = granted ? "allow_listener" : "disallow_listener";
            try {
                ShellCommand.builder("cmd notification")
                        .addOperand(command)
                        .addOperand(listener.componentName().flattenToShortString())
                        .allowEmptyOutput(true)
                        .validate(String::isEmpty)
                        .execute();
            } catch (AdbException e) {
                throw new NeneException(
                            "Error setting notification listener access " + granted, e);
            }

            return;
        }

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_NOTIFICATION_LISTENERS)) {
            TestApis.context().androidContextAsUser(user)
                    .getSystemService(NotificationManager.class)
                    .setNotificationListenerAccessGranted(
                            listener.componentName(), granted, /* userGranted= */ false);
        }
    }
}
