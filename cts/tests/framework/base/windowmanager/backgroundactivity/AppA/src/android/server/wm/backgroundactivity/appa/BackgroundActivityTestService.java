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

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.appa.Components.APP_A_BACKGROUND_ACTIVITY;
import static android.server.wm.backgroundactivity.appa.Components.APP_A_START_ACTIVITY_RECEIVER;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.storage.StorageManager;

public class BackgroundActivityTestService extends Service {
    private final IBackgroundActivityTestService mBinder = new MyBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    private class MyBinder extends IBackgroundActivityTestService.Stub {
        @Override
        public PendingIntent generatePendingIntent(boolean isBroadcast) {
            if (isBroadcast) {
                // Create a pendingIntent to launch send broadcast to appA and appA will start
                // background activity.
                Intent newIntent = new Intent();
                newIntent.setComponent(APP_A_START_ACTIVITY_RECEIVER);
                return PendingIntent.getBroadcast(BackgroundActivityTestService.this, 0, newIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                // Create a pendingIntent to launch appA's BackgroundActivity
                Intent newIntent = new Intent();
                newIntent.setComponent(APP_A_BACKGROUND_ACTIVITY);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return PendingIntent.getActivity(BackgroundActivityTestService.this, 0, newIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
        }

        @Override
        public void getAndStartManageSpaceActivity() {
            final long token = Binder.clearCallingIdentity();
            try {
                StorageManager stm = getSystemService(StorageManager.class);
                PendingIntent pi = stm.getManageSpaceActivityIntent(getPackageName(), 0);
                pi.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
                throw new IllegalStateException("Unable to send PendingIntent");
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }
}
