/*
 * Copyright (C) 2017-2021 The LineageOS Project
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

package org.lineageos.setupwizard;

import static org.lineageos.setupwizard.SetupWizardApp.LOGV;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import org.lineageos.setupwizard.util.PhoneMonitor;
import org.lineageos.setupwizard.util.SetupWizardUtils;

public class SetupWizardExitActivity extends BaseSetupWizardActivity {

    private static final String TAG = SetupWizardExitActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            Log.v(TAG, "onCreate savedInstanceState=" + savedInstanceState);
        }
        if (SetupWizardUtils.isOwner()) {
            SetupWizardUtils.enableCaptivePortalDetection(this);
        }
        PhoneMonitor.onSetupFinished();
        if (!SetupWizardUtils.isManagedProfile(this)) {
            markSetupAsCompleted();
            rebootDevice();
        }
        finish();
        applyForwardTransition(TRANSITION_ID_FADE);
        Intent i = new Intent();
        i.setClassName(getPackageName(), SetupWizardExitService.class.getName());
        startService(i);
    }

    private void markSetupAsCompleted() {
        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
        Settings.Secure.putInt(getContentResolver(), "user_setup_complete", 1);
    }

    private void rebootDevice() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        powerManager.reboot(null);
    }
}
