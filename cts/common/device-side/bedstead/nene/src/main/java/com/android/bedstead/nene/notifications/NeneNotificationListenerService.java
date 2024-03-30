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

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.bedstead.nene.TestApis;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link NotificationListenerService} for use with Nene.
 *
 * <p>To access this use {@link TestApis#notifications()}.
 */
public final class NeneNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "NeneNotificationListenerService";

    private static AtomicReference<NeneNotificationListenerService> sInstance =
            new AtomicReference<>();
    private static CountDownLatch sConnectedLatch = new CountDownLatch(1);

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "Listener connected");
        sInstance.set(this);
        sConnectedLatch.countDown();
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "Listener disconnected");
        sInstance.set(null);
        sConnectedLatch = new CountDownLatch(1);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notifications.sInstance.onNotificationPosted(sbn);
    }
}
