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

package com.android.server.sdksandbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Broadcast Receiver for receiving new Sdk install requests and
 * verifying Sdk code before running it in Sandbox.
 * @hide
 */
public class SdkSandboxVerifierReceiver extends BroadcastReceiver {

    private static final String TAG = "SdkSandboxManager";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());


    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_NEEDS_VERIFICATION.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "Received sdk sandbox verification intent " + intent.toString());
        Log.d(TAG, "Extras " + intent.getExtras());

        MAIN_HANDLER.post(() -> verifySdkHandler(context, intent));
    }

    private void verifySdkHandler(Context context, Intent intent) {
        int verificationId = intent.getIntExtra(PackageManager.EXTRA_VERIFICATION_ID, -1);
        String verificationRootHash = intent.getStringExtra(
                PackageManager.EXTRA_VERIFICATION_ROOT_HASH);

        String apkPath = intent.getData().getSchemeSpecificPart();

        boolean validSdk = true;

        for (String apkWithHash : verificationRootHash.split(";")) {
            String apk = apkWithHash.split(":")[0];

            //TODO(b/206445674): move verify call to sdk sandbox apk
            //TODO(b/206445674): call verify and pass the apk Path
        }

        // TODO(b/206445674): store results from the verifier.
        if (validSdk) {
            context.getPackageManager().verifyPendingInstall(
                    verificationId,
                    PackageManager.VERIFICATION_ALLOW);
        } else {
            context.getPackageManager().verifyPendingInstall(
                    verificationId,
                    PackageManager.VERIFICATION_REJECT);
        }
    }
}
