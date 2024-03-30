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

import com.android.server.nearby.intdefs.FastPairEventIntDefs.BrEdrHandoverErrorCode;

import com.google.errorprone.annotations.FormatMethod;

/**
 * Thrown when BR/EDR Handover fails.
 */
public class TdsException extends Exception {

    final @BrEdrHandoverErrorCode int mErrorCode;

    @FormatMethod
    TdsException(@BrEdrHandoverErrorCode int errorCode, String format, Object... objects) {
        super(String.format(format, objects));
        this.mErrorCode = errorCode;
    }

    /** Returns error code. */
    public @BrEdrHandoverErrorCode int getErrorCode() {
        return mErrorCode;
    }
}

