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

package android.quickaccesswallet;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Extends {@link TestQuickAccessWalletService} to allow for a different manifest configuration.
 */
public class QuickAccessWalletDelegateTargetActivityService extends TestQuickAccessWalletService {

    @Nullable
    @Override
    public PendingIntent getTargetActivityPendingIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName("android.sample.quickaccesswallet.app",
                "android.sample.quickaccesswallet.app.QuickAccessWalletDelegateTargetActivity");
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }
}
