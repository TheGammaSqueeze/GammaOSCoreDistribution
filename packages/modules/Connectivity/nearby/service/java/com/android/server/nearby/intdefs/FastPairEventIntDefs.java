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

package com.android.server.nearby.intdefs;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Holds integer definitions for FastPair. */
public class FastPairEventIntDefs {

    /** Fast Pair Bond State. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    BondState.UNKNOWN_BOND_STATE,
                    BondState.NONE,
                    BondState.BONDING,
                    BondState.BONDED,
            })
    public @interface BondState {
        int UNKNOWN_BOND_STATE = 0;
        int NONE = 10;
        int BONDING = 11;
        int BONDED = 12;
    }

    /** Fast Pair error code. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    ErrorCode.UNKNOWN_ERROR_CODE,
                    ErrorCode.OTHER_ERROR,
                    ErrorCode.TIMEOUT,
                    ErrorCode.INTERRUPTED,
                    ErrorCode.REFLECTIVE_OPERATION_EXCEPTION,
                    ErrorCode.EXECUTION_EXCEPTION,
                    ErrorCode.PARSE_EXCEPTION,
                    ErrorCode.MDH_REMOTE_EXCEPTION,
                    ErrorCode.SUCCESS_RETRY_GATT_ERROR,
                    ErrorCode.SUCCESS_RETRY_GATT_TIMEOUT,
                    ErrorCode.SUCCESS_RETRY_SECRET_HANDSHAKE_ERROR,
                    ErrorCode.SUCCESS_RETRY_SECRET_HANDSHAKE_TIMEOUT,
                    ErrorCode.SUCCESS_SECRET_HANDSHAKE_RECONNECT,
                    ErrorCode.SUCCESS_ADDRESS_ROTATE,
                    ErrorCode.SUCCESS_SIGNAL_LOST,
            })
    public @interface ErrorCode {
        int UNKNOWN_ERROR_CODE = 0;

        // Check the other fields for a more specific error code.
        int OTHER_ERROR = 1;

        // The operation timed out.
        int TIMEOUT = 2;

        // The thread was interrupted.
        int INTERRUPTED = 3;

        // Some reflective call failed (should never happen).
        int REFLECTIVE_OPERATION_EXCEPTION = 4;

        // A Future threw an exception (should never happen).
        int EXECUTION_EXCEPTION = 5;

        // Parsing something (e.g. BR/EDR Handover data) failed.
        int PARSE_EXCEPTION = 6;

        // A failure at MDH.
        int MDH_REMOTE_EXCEPTION = 7;

        // For errors on GATT connection and retry success
        int SUCCESS_RETRY_GATT_ERROR = 8;

        // For timeout on GATT connection and retry success
        int SUCCESS_RETRY_GATT_TIMEOUT = 9;

        // For errors on secret handshake and retry success
        int SUCCESS_RETRY_SECRET_HANDSHAKE_ERROR = 10;

        // For timeout on secret handshake and retry success
        int SUCCESS_RETRY_SECRET_HANDSHAKE_TIMEOUT = 11;

        // For secret handshake fail and restart GATT connection success
        int SUCCESS_SECRET_HANDSHAKE_RECONNECT = 12;

        // For address rotate and retry with new address success
        int SUCCESS_ADDRESS_ROTATE = 13;

        // For signal lost and retry with old address still success
        int SUCCESS_SIGNAL_LOST = 14;
    }

    /** Fast Pair BrEdrHandover Error Code. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    BrEdrHandoverErrorCode.UNKNOWN_BR_EDR_HANDOVER_ERROR_CODE,
                    BrEdrHandoverErrorCode.CONTROL_POINT_RESULT_CODE_NOT_SUCCESS,
                    BrEdrHandoverErrorCode.BLUETOOTH_MAC_INVALID,
                    BrEdrHandoverErrorCode.TRANSPORT_BLOCK_INVALID,
            })
    public @interface BrEdrHandoverErrorCode {
        int UNKNOWN_BR_EDR_HANDOVER_ERROR_CODE = 0;
        int CONTROL_POINT_RESULT_CODE_NOT_SUCCESS = 1;
        int BLUETOOTH_MAC_INVALID = 2;
        int TRANSPORT_BLOCK_INVALID = 3;
    }

    /** Fast Pair CreateBound Error Code. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    CreateBondErrorCode.UNKNOWN_BOND_ERROR_CODE,
                    CreateBondErrorCode.BOND_BROKEN,
                    CreateBondErrorCode.POSSIBLE_MITM,
                    CreateBondErrorCode.NO_PERMISSION,
                    CreateBondErrorCode.INCORRECT_VARIANT,
                    CreateBondErrorCode.FAILED_BUT_ALREADY_RECEIVE_PASS_KEY,
            })
    public @interface CreateBondErrorCode {
        int UNKNOWN_BOND_ERROR_CODE = 0;
        int BOND_BROKEN = 1;
        int POSSIBLE_MITM = 2;
        int NO_PERMISSION = 3;
        int INCORRECT_VARIANT = 4;
        int FAILED_BUT_ALREADY_RECEIVE_PASS_KEY = 5;
    }

    /** Fast Pair Connect Error Code. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    ConnectErrorCode.UNKNOWN_CONNECT_ERROR_CODE,
                    ConnectErrorCode.UNSUPPORTED_PROFILE,
                    ConnectErrorCode.GET_PROFILE_PROXY_FAILED,
                    ConnectErrorCode.DISCONNECTED,
                    ConnectErrorCode.LINK_KEY_CLEARED,
                    ConnectErrorCode.FAIL_TO_DISCOVERY,
                    ConnectErrorCode.DISCOVERY_NOT_FINISHED,
            })
    public @interface ConnectErrorCode {
        int UNKNOWN_CONNECT_ERROR_CODE = 0;
        int UNSUPPORTED_PROFILE = 1;
        int GET_PROFILE_PROXY_FAILED = 2;
        int DISCONNECTED = 3;
        int LINK_KEY_CLEARED = 4;
        int FAIL_TO_DISCOVERY = 5;
        int DISCOVERY_NOT_FINISHED = 6;
    }

    private FastPairEventIntDefs() {}
}
