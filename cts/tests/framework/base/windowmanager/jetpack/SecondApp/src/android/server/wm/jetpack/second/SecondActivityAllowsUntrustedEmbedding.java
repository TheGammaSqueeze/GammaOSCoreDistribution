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

package android.server.wm.jetpack.second;

import static android.server.wm.jetpack.second.Components.EXTRA_LAUNCH_NON_EMBEDDABLE_ACTIVITY;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * A test activity that belongs to UID different from the main test app and allows untrusted
 * embedding.
 * <p>Recognizes an {@link Components#EXTRA_LAUNCH_NON_EMBEDDABLE_ACTIVITY} in the incoming intent
 * and starts a non-embeddable activity if the value is {@code true}.
 */
public class SecondActivityAllowsUntrustedEmbedding extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNewIntent(intent);
    }

    private void handleNewIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null && extras.getBoolean(EXTRA_LAUNCH_NON_EMBEDDABLE_ACTIVITY)) {
            // SecondActivity is not opted into embedding in AndroidManifest
            startActivity(new Intent(this, SecondActivity.class));
        }
    }
}
