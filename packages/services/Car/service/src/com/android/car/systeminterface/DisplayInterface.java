/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.systeminterface;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static com.android.car.util.BrightnessUtils.GAMMA_SPACE_MAX;
import static com.android.car.util.BrightnessUtils.convertGammaToLinear;
import static com.android.car.util.BrightnessUtils.convertLinearToGamma;
import static com.android.car.util.Utils.getContentResolverForUser;
import static com.android.car.util.Utils.isEventOfType;

import android.car.builtin.power.PowerManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.util.Log;
import android.view.Display;

import com.android.car.CarLog;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;

/**
 * Interface that abstracts display operations
 */
public interface DisplayInterface {

    /**
     * Sets the required services.
     *
     * @param carPowerManagementService {@link CarPowerManagementService} to listen to car power
     *                                  management changes
     * @param carUserService            {@link CarUserService} to listen to service life cycle
     *                                  changes
     */
    void init(CarPowerManagementService carPowerManagementService, CarUserService carUserService);

    /**
     * Sets display brightness.
     *
     * @param brightness Level from 0 to 100%
     */
    void setDisplayBrightness(int brightness);

    /**
     * Turns on or off display.
     *
     * @param on {@code true} to turn on, {@code false} to turn off.
     */
    void setDisplayState(boolean on);

    /**
     * Starts monitoring the display state change.
     * <p> When there is a change, {@link CarPowerManagementService} is notified.
     */
    void startDisplayStateMonitoring();

    /**
     * Stops monitoring the display state change.
     */
    void stopDisplayStateMonitoring();

    /**
     * Gets the current on/off state of display.
     */
    boolean isDisplayEnabled();

    /**
     * Refreshing display brightness. Used when user is switching and car turned on.
     */
    void refreshDisplayBrightness();

    /**
     * Default implementation of display operations
     */
    class DefaultImpl implements DisplayInterface {
        private static final String TAG = DisplayInterface.class.getSimpleName();
        private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);
        private final Context mContext;
        private final DisplayManager mDisplayManager;
        private final Object mLock = new Object();
        private final int mMaximumBacklight;
        private final int mMinimumBacklight;
        private final WakeLockInterface mWakeLockInterface;
        @GuardedBy("mLock")
        private CarPowerManagementService mCarPowerManagementService;
        @GuardedBy("mLock")
        private CarUserService mCarUserService;
        @GuardedBy("mLock")
        private boolean mDisplayStateSet;
        @GuardedBy("mLock")
        private int mLastBrightnessLevel = -1;

        private final ContentObserver mBrightnessObserver =
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        refreshDisplayBrightness();
                    }
                };

        private final DisplayManager.DisplayListener mDisplayListener = new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                //ignore
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                //ignore
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    handleMainDisplayChanged();
                }
            }
        };

        DefaultImpl(Context context, WakeLockInterface wakeLockInterface) {
            mContext = context;
            mDisplayManager = context.getSystemService(DisplayManager.class);
            mMaximumBacklight = PowerManagerHelper.getMaximumScreenBrightnessSetting(context);
            mMinimumBacklight = PowerManagerHelper.getMinimumScreenBrightnessSetting(context);
            mWakeLockInterface = wakeLockInterface;
        }

        private final UserLifecycleListener mUserLifecycleListener = event -> {
            if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING)) {
                return;
            }
            if (DEBUG) {
                Slogf.d(TAG, "DisplayInterface.DefaultImpl.onEvent(%s)", event);
            }

            onUsersUpdate();
        };

        @Override
        public void refreshDisplayBrightness() {
            CarPowerManagementService carPowerManagementService = null;
            synchronized (mLock) {
                carPowerManagementService = mCarPowerManagementService;
            }
            if (carPowerManagementService == null) {
                Slogf.e(CarLog.TAG_POWER, "Could not set brightness: "
                        + "no CarPowerManagementService");
                return;
            }
            int gamma = GAMMA_SPACE_MAX;
            try {
                int linear = System.getInt(
                        getContentResolverForUser(mContext, UserHandle.CURRENT.getIdentifier()),
                        System.SCREEN_BRIGHTNESS);
                gamma = convertLinearToGamma(linear, mMinimumBacklight, mMaximumBacklight);
            } catch (SettingNotFoundException e) {
                Slogf.e(CarLog.TAG_POWER, "Could not get SCREEN_BRIGHTNESS: ", e);
            }
            int percentBright = (gamma * 100 + ((GAMMA_SPACE_MAX + 1) / 2)) / GAMMA_SPACE_MAX;
            carPowerManagementService.sendDisplayBrightness(percentBright);
        }

        private void handleMainDisplayChanged() {
            boolean isOn = isMainDisplayOn();
            CarPowerManagementService service;
            synchronized (mLock) {
                if (mDisplayStateSet == isOn) { // same as what is set
                    return;
                }
                service = mCarPowerManagementService;
            }
            service.handleMainDisplayChanged(isOn);
        }

        private boolean isMainDisplayOn() {
            Display disp = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            return disp.getState() == Display.STATE_ON;
        }

        @Override
        public void setDisplayBrightness(int percentBright) {
            synchronized (mLock) {
                if (percentBright == mLastBrightnessLevel) {
                    // We have already set the value last time. Skipping
                    return;
                }
                mLastBrightnessLevel = percentBright;
            }
            int gamma = (percentBright * GAMMA_SPACE_MAX + 50) / 100;
            int linear = convertGammaToLinear(gamma, mMinimumBacklight, mMaximumBacklight);
            System.putInt(
                    getContentResolverForUser(mContext, UserHandle.CURRENT.getIdentifier()),
                    System.SCREEN_BRIGHTNESS,
                    linear);
        }

        @Override
        public void init(CarPowerManagementService carPowerManagementService,
                CarUserService carUserService) {
            synchronized (mLock) {
                mCarPowerManagementService = carPowerManagementService;
                mCarUserService = carUserService;
                mDisplayStateSet = isMainDisplayOn();
            }
        }

        @Override
        public void startDisplayStateMonitoring() {
            CarPowerManagementService carPowerManagementService;
            CarUserService carUserService;
            synchronized (mLock) {
                carPowerManagementService = mCarPowerManagementService;
                carUserService = mCarUserService;
            }
            UserLifecycleEventFilter userSwitchingEventFilter =
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
            carUserService.addUserLifecycleListener(userSwitchingEventFilter,
                    mUserLifecycleListener);
            getContentResolverForUser(mContext, UserHandle.ALL.getIdentifier())
                    .registerContentObserver(System.getUriFor(System.SCREEN_BRIGHTNESS),
                            false,
                            mBrightnessObserver);
            mDisplayManager.registerDisplayListener(mDisplayListener,
                    carPowerManagementService.getHandler());
            refreshDisplayBrightness();
        }

        @Override
        public void stopDisplayStateMonitoring() {
            CarUserService carUserService;
            synchronized (mLock) {
                carUserService = mCarUserService;
            }
            carUserService.removeUserLifecycleListener(mUserLifecycleListener);
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
            getContentResolverForUser(mContext, UserHandle.ALL.getIdentifier())
                    .unregisterContentObserver(mBrightnessObserver);
        }

        @Override
        public void setDisplayState(boolean on) {
            synchronized (mLock) {
                mDisplayStateSet = on;
            }
            if (on) {
                mWakeLockInterface.switchToFullWakeLock();
                Slogf.i(CarLog.TAG_POWER, "on display");
                PowerManagerHelper.setDisplayState(mContext, /* on= */ true,
                        SystemClock.uptimeMillis());
            } else {
                mWakeLockInterface.switchToPartialWakeLock();
                Slogf.i(CarLog.TAG_POWER, "off display");
                PowerManagerHelper.setDisplayState(mContext, /* on= */ false,
                        SystemClock.uptimeMillis());
            }
        }

        @Override
        public boolean isDisplayEnabled() {
            return isMainDisplayOn();
        }

        private void onUsersUpdate() {
            synchronized (mLock) {
                if (mCarPowerManagementService == null) {
                    // CarPowerManagementService is not connected yet
                    return;
                }
                // We need to reset last value
                mLastBrightnessLevel = -1;
            }
            refreshDisplayBrightness();
        }
    }
}
