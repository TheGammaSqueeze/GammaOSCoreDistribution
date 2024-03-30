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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.net.MacAddress;

import com.android.server.wifi.SupplicantStaIfaceHal.SupplicantEventCode;

import java.util.Objects;

/**
 * Stores supplicant event information passed from WifiMonitor.
 */
public class SupplicantEventInfo {
    SupplicantEventInfo(@SupplicantEventCode int eventCode, @NonNull MacAddress bssid,
            @NonNull String reasonString) {
        this.eventCode = eventCode;
        this.bssid = Objects.requireNonNull(bssid);
        this.reasonString = Objects.requireNonNull(reasonString);
    }

    public final @SupplicantEventCode int eventCode;
    @NonNull public final MacAddress bssid;
    @NonNull public final String reasonString;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" eventCode: ").append(SupplicantStaIfaceHal
                .supplicantEventCodeToString(eventCode));
        sb.append(" bssid: ").append(bssid);
        sb.append(" reasonString: ").append(reasonString);
        return sb.toString();
    }
}
