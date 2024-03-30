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

package com.android.systemui.car.statusicon.ui;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import android.car.Car;
import android.car.user.CarUserManager;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.dagger.qualifiers.Main;

import javax.inject.Inject;

/**
 * A controller for the read-only icon that shows phone call active status.
 */
public class PhoneCallStatusIconController extends StatusIconController {

    private static final String TAG = PhoneCallStatusIconController.class.getSimpleName();

    private final Context mContext;
    private final TelecomManager mTelecomManager;
    private final CarServiceProvider mCarServiceProvider;

    private boolean mUserLifecycleListenerRegistered;

    final BroadcastReceiver mPhoneStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                updateStatus();
            }
        }
    };

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener =
            new CarUserManager.UserLifecycleListener() {
                @Override
                public void onEvent(CarUserManager.UserLifecycleEvent event) {
                    mContext.getMainExecutor().execute(() -> updateStatus());
                }
            };

    @Inject
    PhoneCallStatusIconController(
            Context context,
            @Main Resources resources,
            CarServiceProvider carServiceProvider) {
        mContext = context;
        mTelecomManager = context.getSystemService(TelecomManager.class);
        mCarServiceProvider = carServiceProvider;
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        context.registerReceiverForAllUsers(mPhoneStateChangeReceiver,
                filter,  /* broadcastPermission= */ null, /* scheduler= */ null);
        registerForUserChangeEvents();
        setIconDrawableToDisplay(resources.getDrawable(R.drawable.ic_phone, context.getTheme()));
        updateStatus();
    }

    @Override
    protected void updateStatus() {
        setIconVisibility(mTelecomManager.isInCall());
        onStatusUpdated();
    }

    private void registerForUserChangeEvents() {
        mCarServiceProvider.addListener(car -> {
            CarUserManager carUserManager = (CarUserManager) car.getCarManager(
                    Car.CAR_USER_SERVICE);
            if (carUserManager != null && !mUserLifecycleListenerRegistered) {
                UserLifecycleEventFilter filter = new UserLifecycleEventFilter.Builder()
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
                carUserManager.addListener(Runnable::run, filter, mUserLifecycleListener);
                mUserLifecycleListenerRegistered = true;
            } else {
                Log.e(TAG, "CarUserManager could not be obtained.");
            }
        });
    }

    @Override
    protected int getId() {
        return R.id.qc_phone_call_status_icon;
    }
}
