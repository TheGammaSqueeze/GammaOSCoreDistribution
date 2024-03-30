/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.proto;

import android.net.ipsec.ike.exceptions.IkeIOException;
import android.net.ipsec.ike.exceptions.IkeInternalException;

import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.IwlanStatsLog;

public class MetricsAtom {
    private int mMessageId;
    private int mApnType;
    private boolean mIsHandover;
    private String mEpdgServerAddress;
    private int mSourceRat;
    private boolean mIsCellularRoaming;
    private boolean mIsNetworkConnected;
    private int mTransportType;
    private int mSetupRequestResult;
    private int mIwlanError;
    private int mDataCallFailCause;
    private int mProcessingDurationMillis;
    private int mEpdgServerSelectionDurationMillis;
    private int mIkeTunnelEstablishmentDurationMillis;
    private int mTunnelState;
    private int mHandoverFailureMode;
    private int mRetryDurationMillis;
    private int mWifiSignalValue;
    private String mIwlanErrorWrappedClassname;
    private String mIwlanErrorWrappedStackFirstFrame;

    public void setMessageId(int messageId) {
        this.mMessageId = messageId;
    }

    public void setApnType(int apnType) {
        this.mApnType = apnType;
    }

    public void setIsHandover(boolean isHandover) {
        this.mIsHandover = isHandover;
    }

    public void setEpdgServerAddress(String epdgServerAddress) {
        this.mEpdgServerAddress = epdgServerAddress;
    }

    public void setSourceRat(int sourceRat) {
        this.mSourceRat = sourceRat;
    }

    public void setIsCellularRoaming(boolean isCellularRoaming) {
        this.mIsCellularRoaming = isCellularRoaming;
    }

    public void setIsNetworkConnected(boolean isNetworkConnected) {
        this.mIsNetworkConnected = isNetworkConnected;
    }

    public void setTransportType(int transportType) {
        this.mTransportType = transportType;
    }

    public void setSetupRequestResult(int setupRequestResult) {
        this.mSetupRequestResult = setupRequestResult;
    }

    public void setIwlanError(int iwlanError) {
        this.mIwlanError = iwlanError;
    }

    public void setDataCallFailCause(int dataCallFailCause) {
        this.mDataCallFailCause = dataCallFailCause;
    }

    public void setProcessingDurationMillis(int processingDurationMillis) {
        this.mProcessingDurationMillis = processingDurationMillis;
    }

    public void setEpdgServerSelectionDurationMillis(int epdgServerSelectionDurationMillis) {
        this.mEpdgServerSelectionDurationMillis = epdgServerSelectionDurationMillis;
    }

    public void setIkeTunnelEstablishmentDurationMillis(int ikeTunnelEstablishmentDurationMillis) {
        this.mIkeTunnelEstablishmentDurationMillis = ikeTunnelEstablishmentDurationMillis;
    }

    public void setTunnelState(int tunnelState) {
        this.mTunnelState = tunnelState;
    }

    public void setHandoverFailureMode(int handoverFailureMode) {
        this.mHandoverFailureMode = handoverFailureMode;
    }

    public void setRetryDurationMillis(int retryDurationMillis) {
        this.mRetryDurationMillis = retryDurationMillis;
    }

    public void setWifiSignalValue(int wifiSignalValue) {
        this.mWifiSignalValue = wifiSignalValue;
    }

    public void setIwlanErrorWrappedClassnameAndStack(IwlanError iwlanError) {
        Throwable iwlanErrorWrapped = iwlanError.getException();
        if (iwlanErrorWrapped instanceof IkeInternalException
                || iwlanErrorWrapped instanceof IkeIOException) {
            iwlanErrorWrapped = iwlanErrorWrapped.getCause();
        }

        if (iwlanErrorWrapped == null) {
            this.mIwlanErrorWrappedClassname = null;
            this.mIwlanErrorWrappedStackFirstFrame = null;
            return;
        }

        this.mIwlanErrorWrappedClassname = iwlanErrorWrapped.getClass().getCanonicalName();

        StackTraceElement[] iwlanErrorWrappedStackTraceElements = iwlanErrorWrapped.getStackTrace();
        this.mIwlanErrorWrappedStackFirstFrame =
                iwlanErrorWrappedStackTraceElements.length != 0
                        ? iwlanErrorWrappedStackTraceElements[0].toString()
                        : null;
    }

    public String getIwlanErrorWrappedClassname() {
        return mIwlanErrorWrappedClassname;
    }

    public String getIwlanErrorWrappedStackFirstFrame() {
        return mIwlanErrorWrappedStackFirstFrame;
    }

    public void sendMetricsData() {
        if (mMessageId == IwlanStatsLog.IWLAN_SETUP_DATA_CALL_RESULT_REPORTED) {
            IwlanStatsLog.write(
                    mMessageId,
                    mApnType,
                    mIsHandover,
                    mEpdgServerAddress,
                    mSourceRat,
                    mIsCellularRoaming,
                    mIsNetworkConnected,
                    mTransportType,
                    mSetupRequestResult,
                    mIwlanError,
                    mDataCallFailCause,
                    mProcessingDurationMillis,
                    mEpdgServerSelectionDurationMillis,
                    mIkeTunnelEstablishmentDurationMillis,
                    mTunnelState,
                    mHandoverFailureMode,
                    mRetryDurationMillis,
                    mIwlanErrorWrappedClassname,
                    mIwlanErrorWrappedStackFirstFrame);
            return;
        } else if (mMessageId == IwlanStatsLog.IWLAN_PDN_DISCONNECTED_REASON_REPORTED) {
            IwlanStatsLog.write(
                    mMessageId,
                    mDataCallFailCause,
                    mIsNetworkConnected,
                    mTransportType,
                    mWifiSignalValue);
            return;
        }
    }
}
