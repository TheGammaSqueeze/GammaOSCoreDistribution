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

package android.hardware.tv.tuner;

import android.hardware.tv.tuner.FrontendIsdbsCoderate;
import android.hardware.tv.tuner.FrontendIsdbsModulation;
import android.hardware.tv.tuner.FrontendIsdbsRolloff;
import android.hardware.tv.tuner.FrontendIsdbsStreamIdType;

/**
 * Signal Settings for ISDBS Frontend.
 * @hide
 */
@VintfStability
parcelable FrontendIsdbsSettings {
    /**
     * Signal frequency in Hertz
     */
    long frequency;

    /**
     * Signal end frequency in Hertz used by scan
     */
    long endFrequency;

    int streamId;

    FrontendIsdbsStreamIdType streamIdType = FrontendIsdbsStreamIdType.UNDEFINED;

    FrontendIsdbsModulation modulation = FrontendIsdbsModulation.UNDEFINED;

    FrontendIsdbsCoderate coderate = FrontendIsdbsCoderate.UNDEFINED;

    /**
     * Symbols per second
     */
    int symbolRate;

    FrontendIsdbsRolloff rolloff = FrontendIsdbsRolloff.UNDEFINED;
}
