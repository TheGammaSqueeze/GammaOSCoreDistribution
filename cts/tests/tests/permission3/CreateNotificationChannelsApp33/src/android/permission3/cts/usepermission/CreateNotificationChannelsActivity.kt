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

package android.permission3.cts.usepermission

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper

const val EXTRA_DELETE_CHANNELS_ON_CLOSE = "extra_delete_channels_on_close"
const val EXTRA_CREATE_CHANNELS = "extra_create"
const val EXTRA_REQUEST_PERMISSIONS = "extra_request_permissions"
const val EXTRA_REQUEST_PERMISSIONS_DELAYED = "extra_request_permissions_delayed"
const val CHANNEL_ID = "channel_id"
const val DELAY_MS = 1000L

class CreateNotificationChannelsActivity : Activity() {
    lateinit var notificationManager: NotificationManager
    override fun onStart() {
        val handler = Handler(Looper.getMainLooper())
        notificationManager = baseContext.getSystemService(NotificationManager::class.java)!!
        if (intent.getBooleanExtra(EXTRA_CREATE_CHANNELS, false)) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                        "Foreground Services", NotificationManager.IMPORTANCE_HIGH))
            }
        }

        if (intent.getBooleanExtra(EXTRA_REQUEST_PERMISSIONS, false)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        } else if (intent.getBooleanExtra(EXTRA_REQUEST_PERMISSIONS_DELAYED, false)) {
            handler.postDelayed({
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }, DELAY_MS)
        }

        super.onStart()
    }

    override fun onPause() {
        if (intent.getBooleanExtra(EXTRA_DELETE_CHANNELS_ON_CLOSE, false)) {
            notificationManager.deleteNotificationChannel(CHANNEL_ID)
        }
        super.onPause()
    }
}