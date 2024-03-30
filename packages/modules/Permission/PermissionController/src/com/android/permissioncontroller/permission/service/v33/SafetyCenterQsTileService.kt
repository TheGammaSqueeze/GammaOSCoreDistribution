/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.service.v33

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.safetycenter.SafetyCenterManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.android.permissioncontroller.R

/**
 * The service backing a Quick Settings Tile which will take users to the Safety Center QS Fragment.
 */
class SafetyCenterQsTileService : TileService() {
    private var disabled = false

    override fun onBind(intent: Intent?): IBinder? {
        val scManager = getSystemService(SafetyCenterManager::class.java)!!
        if (!scManager.isSafetyCenterEnabled) {
            packageManager.setComponentEnabledSetting(ComponentName(this, this::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
            disabled = true
        }

        return super.onBind(intent)
    }
    override fun onStartListening() {
        super.onStartListening()
        if (disabled) {
            return
        }

        qsTile.label = getString(R.string.safety_privacy_qs_tile_title)
        qsTile.subtitle = getString(R.string.safety_privacy_qs_tile_subtitle)
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(Intent.ACTION_VIEW_SAFETY_CENTER_QS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivityAndCollapse(intent)
    }
}