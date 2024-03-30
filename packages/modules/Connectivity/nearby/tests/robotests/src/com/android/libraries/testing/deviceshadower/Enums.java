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

package com.android.libraries.testing.deviceshadower;

/**
 * Contains Enums used by DeviceShadower in interface and internally.
 */
public interface Enums {

    /**
     * Represents vague distance between two devicelets.
     */
    enum Distance {
        NEAR,
        MID,
        FAR,
        AWAY,
    }

    /**
     * Abstract base interface for operations.
     */
    interface Operation {

    }

    /**
     * NFC operations.
     */
    enum NfcOperation implements Operation {
        GET_ADAPTER,
        ENABLE,
        DISABLE,
    }

}
