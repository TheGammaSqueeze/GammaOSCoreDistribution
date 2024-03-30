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

package com.android.server.nearby.fastpair.notification;


import android.annotation.Nullable;
import android.content.Context;

import com.android.server.nearby.fastpair.cache.DiscoveryItem;

/**
 * Responsible for show notification logic.
 */
public class FastPairNotificationManager {

    /**
     * FastPair notification manager that handle notification ui for fast pair.
     */
    public FastPairNotificationManager(Context context, DiscoveryItem item, boolean useLargeIcon,
            int notificationId) {
    }
    /**
     * FastPair notification manager that handle notification ui for fast pair.
     */
    public FastPairNotificationManager(Context context, DiscoveryItem item, boolean useLargeIcon) {

    }

    /**
     * Shows pairing in progress notification.
     */
    public void showConnectingNotification() {}

    /**
     * Shows success notification
     */
    public void showPairingSucceededNotification(
            @Nullable String companionApp,
            int batteryLevel,
            @Nullable String deviceName,
            String address) {

    }

    /**
     * Shows failed notification.
     */
    public void showPairingFailedNotification(byte[] accountKey) {

    }

    /**
     * Notify the pairing process is done.
     */
    public void notifyPairingProcessDone(boolean success, boolean forceNotify,
            String privateAddress, String publicAddress) {}
}
