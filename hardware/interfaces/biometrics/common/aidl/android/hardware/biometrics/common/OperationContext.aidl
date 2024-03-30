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

package android.hardware.biometrics.common;

import android.hardware.biometrics.common.OperationReason;

/**
 * Additional context associated with an operation.
 */
@VintfStability
parcelable OperationContext {
    /**
     * An identifier for the logical action that the user is engaged in. These identifiers are
     * not guaranteed to be unique. However, the framework will not reuse identifiers within
     * short periods of time so they can be made unique, if needed, by appending a timestamp.
     *
     * Zero if the reason is OperationReason.UNKNOWN.
     */
    int id = 0;

    /**
     * A logical reason for this operation.
     *
     * This should be interpreted as a hint to enable optimizations or tracing. The
     * framework may choose to use OperationReason.UNKNOWN at any time based on the device's
     * policy.
     */
    OperationReason reason = OperationReason.UNKNOWN;

    /* Flag indicating that the display is in AOD mode. */
    boolean isAod = false;

    /** Flag indicating that crypto was requested. */
    boolean isCrypto = false;
}
