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

package com.android.car.settings.qc;

import static com.android.car.qc.QCItem.QC_ACTION_TOGGLE_STATE;
import static com.android.car.qc.QCItem.QC_TYPE_ACTION_SWITCH;
import static com.android.car.settings.qc.QCUtils.getActionDisabledDialogIntent;
import static com.android.car.settings.qc.SettingsQCRegistry.WIFI_ROW_URI;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.UserManager;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.car.settings.enterprise.EnterpriseUtils;

/**
 * QCItem for showing a wifi row element.
 * The row contains a wifi icon, the status or the currently connected wifi SSID, and a switch
 * to enable/disable wifi.
 */
public class WifiRow extends SettingsQCItem {
    private final WifiManager mWifiManager;

    public WifiRow(Context context) {
        super(context);
        mWifiManager = context.getSystemService(WifiManager.class);
    }

    @Override
    QCItem getQCItem() {
        boolean wifiEnabled = mWifiManager.isWifiEnabled();
        Icon icon = Icon.createWithResource(getContext(), WifiQCUtils.getIcon(mWifiManager));

        String userRestriction = UserManager.DISALLOW_CONFIG_WIFI;
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                userRestriction);
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                userRestriction);

        QCActionItem wifiToggle = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH)
                .setChecked(wifiEnabled)
                .setAction(getBroadcastIntent())
                .setEnabled(!hasUmRestrictions && !hasDpmRestrictions)
                .setClickableWhileDisabled(hasDpmRestrictions)
                .setDisabledClickAction(getActionDisabledDialogIntent(getContext(),
                        userRestriction))
                .build();

        QCRow wifiRow = new QCRow.Builder()
                .setIcon(icon)
                .setTitle(getContext().getString(R.string.wifi_settings))
                .setSubtitle(WifiQCUtils.getSubtitle(getContext(), mWifiManager))
                .addEndItem(wifiToggle)
                .build();

        return new QCList.Builder()
                .addRow(wifiRow)
                .build();
    }

    @Override
    Uri getUri() {
        return WIFI_ROW_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        boolean newState = intent.getBooleanExtra(QC_ACTION_TOGGLE_STATE,
                !mWifiManager.isWifiEnabled());
        mWifiManager.setWifiEnabled(newState);
    }

    @Override
    Class getBackgroundWorkerClass() {
        return WifiRowWorker.class;
    }
}
