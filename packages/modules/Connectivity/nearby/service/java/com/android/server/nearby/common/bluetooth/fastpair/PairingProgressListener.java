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

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Callback interface for pairing progress. */
public interface PairingProgressListener {

    /** Fast Pair Bond State. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    PairingEvent.START,
                    PairingEvent.SUCCESS,
                    PairingEvent.FAILED,
                    PairingEvent.UNKNOWN,
            })
    public @interface PairingEvent {
        int START = 0;
        int SUCCESS = 1;
        int FAILED = 2;
        int UNKNOWN = 3;
    }

    /** Returns enum based on the ordinal index. */
    static @PairingEvent int fromOrdinal(int ordinal) {
        switch (ordinal) {
            case 0:
                return PairingEvent.START;
            case 1:
                return PairingEvent.SUCCESS;
            case 2:
                return PairingEvent.FAILED;
            case 3:
                return PairingEvent.UNKNOWN;
            default:
                return PairingEvent.UNKNOWN;
        }
    }

    /** Callback function upon pairing progress update. */
    void onPairingProgressUpdating(@PairingEvent int event, String message);
}
