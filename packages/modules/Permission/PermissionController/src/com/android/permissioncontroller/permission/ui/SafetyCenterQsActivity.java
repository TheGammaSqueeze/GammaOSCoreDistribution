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

package com.android.permissioncontroller.permission.ui;

import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.os.Bundle;
import android.permission.PermissionGroupUsage;
import android.permission.PermissionManager;

import androidx.fragment.app.FragmentActivity;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.permission.ui.handheld.v33.SafetyCenterQsFragment;

import java.util.ArrayList;
import java.util.Random;

/**
 * Activity for the Safety Center Quick Settings Activity
 */
public class SafetyCenterQsActivity extends FragmentActivity {

    @Override
    @SuppressWarnings("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SdkLevel.isAtLeastT()) {
            finish();
            return;
        }

        long sessionId = getIntent().getLongExtra(Constants.EXTRA_SESSION_ID, INVALID_SESSION_ID);
        while (sessionId == INVALID_SESSION_ID) {
            sessionId = new Random().nextLong();
        }
        ArrayList<PermissionGroupUsage> permissionUsages = getIntent().getParcelableArrayListExtra(
                PermissionManager.EXTRA_PERMISSION_USAGES);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                SafetyCenterQsFragment.newInstance(sessionId, permissionUsages)).commit();
    }
}
