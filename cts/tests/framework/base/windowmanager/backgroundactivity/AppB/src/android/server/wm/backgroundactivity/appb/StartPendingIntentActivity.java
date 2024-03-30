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
 * limitations under the License
 */

package android.server.wm.backgroundactivity.appb;

import static android.server.wm.backgroundactivity.appb.Components.StartPendingIntentActivity.ALLOW_BAL_EXTRA;
import static android.server.wm.backgroundactivity.appb.Components.StartPendingIntentReceiver.PENDING_INTENT_EXTRA;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

/**
 * Receive pending intent from AppA and launch it
 */
public class StartPendingIntentActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent intent = getIntent();
        final PendingIntent pendingIntent = intent.getParcelableExtra(PENDING_INTENT_EXTRA);
        final boolean allowBal = intent.getBooleanExtra(ALLOW_BAL_EXTRA, false);

        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentBackgroundActivityLaunchAllowed(allowBal);
            Bundle bundle = options.toBundle();
            pendingIntent.send(/* context */ null, /* code */0, /* intent */
                    null, /* onFinished */null, /* handler */
                    null, /* requiredPermission */ null, bundle);
        } catch (PendingIntent.CanceledException e) {
            throw new AssertionError(e);
        }
    }
}
