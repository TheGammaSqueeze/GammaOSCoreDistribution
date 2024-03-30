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

package com.android.systemui.car.keyguard;

import android.app.ActivityManager;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardPatternView;

/**
 * Car specific {@link KeyguardPatternView} to simply animations.
 *
 * TODO(b/204220809): Instead of extending {@KeyguardPatternView} separate shared logic into
 * interface.
 */
public class CarKeyguardPatternView extends KeyguardPatternView {

    private static final String TAG = "CarKeyguardPatternView";

    public CarKeyguardPatternView(Context context) {
        super(context);
    }

    public CarKeyguardPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // TODO: Remove this code block when b/158386500 is addressed properly.
        LockPatternUtils utils = new LockPatternUtils(getContext());
        int currentUser = ActivityManager.getCurrentUser();
        if (!utils.isVisiblePatternEnabled(currentUser)) {
            utils.setVisiblePatternEnabled(true, currentUser);
            Log.w(TAG, "Pattern is inivisble for the current user. Setting it to be visible.");
        }
    }
}
