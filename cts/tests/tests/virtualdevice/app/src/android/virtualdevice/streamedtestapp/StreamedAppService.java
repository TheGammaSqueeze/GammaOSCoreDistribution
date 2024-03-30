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

package android.virtualdevice.streamedtestapp;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;
import android.virtualdevice.cts.IStreamedTestApp;

/**
 * Service for communicating between the CTS test and this streamed test app.
 */
public class StreamedAppService extends Service {

    private static final String TAG = "StreamedAppService";

    /**
     * Tell this service to start {@link MainActivity}.
     */
    private static final String ACTION_START_MAIN_ACTIVITY =
            "android.virtualdevice.streamedtestapp.START_MAIN_ACTIVITY";

    /**
     * Tell this service to do nothing.
     */
    private static final String ACTION_NO_OP = "android.virtualdevice.streamedtestapp.NO_OP";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_START_MAIN_ACTIVITY:
                Intent activityIntent = new Intent(this, MainActivity.class)
                        .setAction(MainActivity.ACTION_CALL_RESULT_RECEIVER)
                        .putExtras(intent)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(activityIntent);
                break;
            case ACTION_NO_OP:
                break;
            default:
                Log.w(TAG, "Unknown action: " + intent.getAction());
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new IStreamedTestApp.Stub() {

            @Override
            public PendingIntent createActivityPendingIntent(ResultReceiver resultReceiver) {
                Intent intent = new Intent(MainActivity.ACTION_CALL_RESULT_RECEIVER)
                        .setClass(StreamedAppService.this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_ACTIVITY_LAUNCHED_RECEIVER, resultReceiver);
                return PendingIntent.getActivity(
                        StreamedAppService.this, 1, intent, PendingIntent.FLAG_IMMUTABLE);
            }

            @Override
            public PendingIntent createServicePendingIntent(
                    boolean trampoline, ResultReceiver resultReceiver) {
                Intent intent = new Intent(trampoline ? ACTION_START_MAIN_ACTIVITY : ACTION_NO_OP)
                        .setClass(StreamedAppService.this, StreamedAppService.class);
                intent.putExtra(MainActivity.EXTRA_ACTIVITY_LAUNCHED_RECEIVER, resultReceiver);
                return PendingIntent.getService(
                        StreamedAppService.this, 1, intent, PendingIntent.FLAG_IMMUTABLE);
            }
        };
    }
}
