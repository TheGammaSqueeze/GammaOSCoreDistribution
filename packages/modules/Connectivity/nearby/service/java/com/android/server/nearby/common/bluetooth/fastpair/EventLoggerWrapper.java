/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.server.nearby.common.bluetooth.fastpair;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.android.server.nearby.common.bluetooth.fastpair.Preferences.ExtraLoggingInformation;
import com.android.server.nearby.intdefs.NearbyEventIntDefs.EventCode;

import javax.annotation.Nullable;

/**
 * Convenience wrapper around EventLogger.
 */
// TODO(b/202559985): cleanup EventLoggerWrapper.
class EventLoggerWrapper {

    EventLoggerWrapper(@Nullable EventLogger eventLogger) {
    }

    /**
     * Binds to the logging service. This operation blocks until binding has completed or timed
     * out.
     */
    void bind(
            Context context, String address,
            @Nullable ExtraLoggingInformation extraLoggingInformation) {
    }

    boolean isBound() {
        return false;
    }

    void unbind(Context context) {
    }

    void setCurrentEvent(@EventCode int code) {
    }

    void setCurrentProfile(short profile) {
    }

    void logCurrentEventFailed(Exception e) {
    }

    void logCurrentEventSucceeded() {
    }

    void setDevice(@Nullable BluetoothDevice device) {
    }

    boolean isCurrentEvent() {
        return false;
    }
}
