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

package android.car.builtin.power;

import android.annotation.SystemApi;
import android.car.builtin.annotation.AddedIn;
import android.car.builtin.annotation.PlatformVersion;
import android.content.Context;
import android.os.PowerManager;

/**
 * Helper for PowerManager related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class PowerManagerHelper {

    private PowerManagerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Gets the maximum supported screen brightness setting.
     * This wraps {@link PowerManager.getMaximumScreenBrightnessSetting}.
     *
     * @param context Context to use.
     * @return The maximum value that can be set by the user.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int getMaximumScreenBrightnessSetting(Context context) {
        return context.getSystemService(PowerManager.class).getMaximumScreenBrightnessSetting();
    }

    /**
     * Gets the minimum supported screen brightness setting.
     * This wraps {@link PowerManager.getMinimumScreenBrightnessSetting}.
     *
     * @param context Context to use.
     * @return The minimum value that can be set by the user.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int getMinimumScreenBrightnessSetting(Context context) {
        return context.getSystemService(PowerManager.class).getMinimumScreenBrightnessSetting();
    }

    /**
     * Forces the {@link com.android.server.display.DisplayGroup#DEFAULT default display group}
     * to turn on or off.
     *
     * @param context Context to use.
     * @param on Whether to turn the display on or off.
     * @param upTime The time when the request was issued, in the
     * {@link SystemClock#uptimeMillis()} time base.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void setDisplayState(Context context, boolean on, long upTime) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (on) {
            powerManager.wakeUp(upTime, PowerManager.WAKE_REASON_UNKNOWN, "wake up by CarService");
        } else {
            powerManager.goToSleep(upTime, PowerManager.GO_TO_SLEEP_REASON_APPLICATION,
                    /* flag= */ 0);
        }
    }

    /** Turns off the device. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static void shutdown(Context context, boolean confirm, String reason, boolean wait) {
        context.getSystemService(PowerManager.class).shutdown(confirm, reason, wait);
    }
}
