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

import com.android.server.nearby.intdefs.FastPairEventIntDefs.CreateBondErrorCode;

/** Thrown when binding (pairing) with a bluetooth device fails. */
public class CreateBondException extends PairingException {
    final @CreateBondErrorCode int mErrorCode;
    int mReason;

    CreateBondException(@CreateBondErrorCode int errorCode, int reason, String format,
            Object... objects) {
        super(format, objects);
        this.mErrorCode = errorCode;
        this.mReason = reason;
    }

    /** Returns error code. */
    public @CreateBondErrorCode int getErrorCode() {
        return mErrorCode;
    }

    /** Returns reason. */
    public int getReason() {
        return mReason;
    }
}
