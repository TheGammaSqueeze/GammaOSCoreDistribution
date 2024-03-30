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

package android.car.builtin.view;

import android.annotation.SystemApi;
import android.car.builtin.annotation.AddedIn;
import android.car.builtin.annotation.PlatformVersion;
import android.view.Display;
import android.view.DisplayAddress;

/**
 * Provide access to {@code android.view.Display} calls.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class DisplayHelper {

    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int INVALID_PORT = -1;

    /** Display type: Physical display connected through an internal port. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int TYPE_INTERNAL = Display.TYPE_INTERNAL;
    /** Display type: Physical display connected through an external port. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int TYPE_EXTERNAL = Display.TYPE_EXTERNAL;
    /** Display type: Virtual display. */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int TYPE_VIRTUAL = Display.TYPE_VIRTUAL;

    private DisplayHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the physical port number of the display, or {@code INVALID_PORT} if the display isn't
     *         the physical one.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int getPhysicalPort(Display display) {
        DisplayAddress address = display.getAddress();
        if (address instanceof DisplayAddress.Physical) {
            DisplayAddress.Physical physicalAddress = (DisplayAddress.Physical) address;
            if (physicalAddress != null) {
                return physicalAddress.getPort();
            }
        }
        return INVALID_PORT;
    }

    /**
     * @return the uniqueId of the given display.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static String getUniqueId(Display display) {
        return display.getUniqueId();
    }

    /**
     * Gets the display type.
     * @see Display.getType()
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int getType(Display display) {
        return display.getType();
    }
}
