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

package com.google.android.tv.btservices.settings;

import static com.google.android.tv.btservices.settings.ConnectedDevicesSliceProvider.ACTION_TOGGLE_CHANGED;
import static com.google.android.tv.btservices.settings.SlicesUtil.CEC_SLICE_URI;
import static com.google.android.tv.btservices.settings.SlicesUtil.EXTRAS_SLICE_URI;
import static com.google.android.tv.btservices.settings.SlicesUtil.GENERAL_SLICE_URI;
import static com.google.android.tv.btservices.settings.SlicesUtil.notifyToGoBack;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.android.tv.btservices.PowerUtils;

import java.util.ArrayList;

/**
 * This broadcast receiver handles two cases:
 * (a) CEC control toggle.
 * (b) Handle the followup pending intent for "rename"/"forget" preference to notify TvSettings UI
 * flow to go back.
 */
public class SliceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "SliceBroadcastReceiver";
    static final String CEC = "CEC";
    static final String TOGGLE_TYPE = "TOGGLE_TYPE";
    static final String TOGGLE_STATE = "TOGGLE_STATE";
    private static final String ACTION_UPDATE_SLICE = "UPDATE_SLICE";
    private static final String ACTION_BACK_AND_UPDATE_SLICE = "BACK_AND_UPDATE_SLICE";
    private static final String PARAM_URIS = "URIS";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final boolean isChecked = intent.getBooleanExtra(TOGGLE_STATE, false);
        if (action == null) {
            return;
        }
        switch (action) {
            case ACTION_TOGGLE_CHANGED:
                if (CEC.equals(intent.getStringExtra(TOGGLE_TYPE))) {
                    PowerUtils.enableCecControl(context, isChecked);
                    context.getContentResolver().notifyChange(CEC_SLICE_URI, null);
                    context.getContentResolver().notifyChange(GENERAL_SLICE_URI, null);
                }
                break;
            case ACTION_BACK_AND_UPDATE_SLICE:
                notifyToGoBack(context, Uri.parse(intent.getStringExtra(EXTRAS_SLICE_URI)));
            case ACTION_UPDATE_SLICE:
                ArrayList<String> uris = intent.getStringArrayListExtra(PARAM_URIS);
                uris.forEach(uri -> {
                    context.getContentResolver().notifyChange(Uri.parse(uri), null);
                });
            default:
                // no-op
        }
    }

    public static PendingIntent updateSliceIntent(
            Context context, int requestCode, ArrayList<String> uris, String updatedUri) {
        Intent i = new Intent(context, SliceBroadcastReceiver.class)
                .setAction(ACTION_UPDATE_SLICE)
                .putStringArrayListExtra(PARAM_URIS, uris)
                .setData(Uri.parse(updatedUri));
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent backAndUpdateSliceIntent(
            Context context, int requestCode, ArrayList<String> uris, String navigatingBackUri) {
        Intent i = new Intent(context, SliceBroadcastReceiver.class)
                .setAction(ACTION_BACK_AND_UPDATE_SLICE)
                .putStringArrayListExtra(PARAM_URIS, uris)
                .putExtra(EXTRAS_SLICE_URI, navigatingBackUri)
                .setData(Uri.parse(navigatingBackUri));
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
