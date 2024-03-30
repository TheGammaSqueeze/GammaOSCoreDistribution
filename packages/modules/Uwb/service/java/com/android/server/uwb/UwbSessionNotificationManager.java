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
package com.android.server.uwb;

import android.annotation.NonNull;
import android.os.PersistableBundle;
import android.util.Log;
import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.params.TlvUtil;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccRangingReconfiguredParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UwbSessionNotificationManager {
    private static final String TAG = "UwbSessionNotiManager";
    private final UwbInjector mUwbInjector;

    public UwbSessionNotificationManager(@NonNull UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }

    public void onRangingResult(UwbSession uwbSession, UwbRangingData rangingData) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        boolean permissionGranted = mUwbInjector.checkUwbRangingPermissionForDataDelivery(
                uwbSession.getAttributionSource(), "uwb ranging result");
        if (!permissionGranted) {
            Log.e(TAG, "Not delivering ranging result because of permission denial"
                    + sessionHandle);
            return;
        }
        try {
            uwbRangingCallbacks.onRangingResult(
                    sessionHandle,
                    getRangingReport(rangingData, uwbSession.getProtocolName(),
                            uwbSession.getParams(), mUwbInjector.getElapsedSinceBootNanos()));
            Log.i(TAG, "IUwbRangingCallbacks - onRangingResult");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingResult : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingOpened(UwbSession uwbSession) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingOpened(sessionHandle);
            Log.i(TAG, "IUwbRangingCallbacks - onRangingOpened");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingOpened : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingOpenFailed(UwbSession uwbSession, int status) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();

        try {
            uwbRangingCallbacks.onRangingOpenFailed(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onRangingOpenFailed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingOpenFailed : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingStarted(UwbSession uwbSession, Params rangingStartedParams) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingStarted(sessionHandle, rangingStartedParams.toBundle());
            Log.i(TAG, "IUwbRangingCallbacks - onRangingStarted");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingStarted : Failed");
            e.printStackTrace();
        }
    }


    public void onRangingStartFailed(UwbSession uwbSession, int status) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingStartFailed(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onRangingStartFailed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingStartFailed : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingStoppedWithUciReasonCode(UwbSession uwbSession, int reasonCode)  {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingStopped(sessionHandle,
                    UwbSessionNotificationHelper.convertUciReasonCodeToApiReasonCode(reasonCode),
                    new PersistableBundle());
            Log.i(TAG, "IUwbRangingCallbacks - onRangingStopped");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingStopped : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingStopped(UwbSession uwbSession, int status)  {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingStopped(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(
                            status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onRangingStopped");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingStopped : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingStopFailed(UwbSession uwbSession, int status) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingStopFailed(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(
                            status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onRangingStopFailed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingStopFailed : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingReconfigured(UwbSession uwbSession) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        PersistableBundle params;
        if (Objects.equals(uwbSession.getProtocolName(), CccParams.PROTOCOL_NAME)) {
            // Why are there no params defined for this bundle?
            params = new CccRangingReconfiguredParams.Builder().build().toBundle();
        } else {
            // No params defined for FiRa reconfigure.
            params = new PersistableBundle();
        }
        try {
            uwbRangingCallbacks.onRangingReconfigured(sessionHandle, params);
            Log.i(TAG, "IUwbRangingCallbacks - onRangingReconfigured");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingReconfigured : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingReconfigureFailed(UwbSession uwbSession, int status) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingReconfigureFailed(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(
                            status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onRangingReconfigureFailed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingReconfigureFailed : Failed");
            e.printStackTrace();
        }
    }

    public void onControleeAdded(UwbSession uwbSession) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onControleeAdded(sessionHandle, new PersistableBundle());
            Log.i(TAG, "IUwbRangingCallbacks - onControleeAdded");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onControleeAdded: Failed");
            e.printStackTrace();
        }
    }

    public void onControleeAddFailed(UwbSession uwbSession, int status) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onControleeAddFailed(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(
                            status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onControleeAddFailed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onControleeAddFailed : Failed");
            e.printStackTrace();
        }
    }

    public void onControleeRemoved(UwbSession uwbSession) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onControleeRemoved(sessionHandle, new PersistableBundle());
            Log.i(TAG, "IUwbRangingCallbacks - onControleeRemoved");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onControleeRemoved: Failed");
            e.printStackTrace();
        }
    }

    public void onControleeRemoveFailed(UwbSession uwbSession, int status) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onControleeRemoveFailed(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(
                            status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onControleeRemoveFailed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onControleeRemoveFailed : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingClosed(UwbSession uwbSession, int status) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingClosed(sessionHandle,
                    UwbSessionNotificationHelper.convertUciStatusToApiReasonCode(
                            status),
                    UwbSessionNotificationHelper.convertUciStatusToParam(
                            uwbSession.getProtocolName(), status));
            Log.i(TAG, "IUwbRangingCallbacks - onRangingClosed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingClosed : Failed");
            e.printStackTrace();
        }
    }

    public void onRangingClosedWithApiReasonCode(
            UwbSession uwbSession, @RangingChangeReason int reasonCode) {
        SessionHandle sessionHandle = uwbSession.getSessionHandle();
        IUwbRangingCallbacks uwbRangingCallbacks = uwbSession.getIUwbRangingCallbacks();
        try {
            uwbRangingCallbacks.onRangingClosed(sessionHandle, reasonCode, new PersistableBundle());
            Log.i(TAG, "IUwbRangingCallbacks - onRangingClosed");
        } catch (Exception e) {
            Log.e(TAG, "IUwbRangingCallbacks - onRangingClosed : Failed");
            e.printStackTrace();
        }
    }

    private static RangingReport getRangingReport(
            @NonNull UwbRangingData rangingData, String protocolName,
            Params sessionParams, long elapsedRealtimeNanos) {
        if (rangingData.getRangingMeasuresType()
                != UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY) {
            return null;
        }
        boolean isAoaAzimuthEnabled = true;
        boolean isAoaElevationEnabled = true;
        boolean isDestAoaAzimuthEnabled = false;
        boolean isDestAoaElevationEnabled = false;
        // For FIRA sessions, check if AOA is enabled for the session or not.
        if (protocolName.equals(FiraParams.PROTOCOL_NAME)) {
            FiraOpenSessionParams openSessionParams = (FiraOpenSessionParams) sessionParams;
            switch (openSessionParams.getAoaResultRequest()) {
                case FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT:
                    isAoaAzimuthEnabled = false;
                    isAoaElevationEnabled = false;
                    break;
                case FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS:
                case FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_INTERLEAVED:
                    isAoaAzimuthEnabled = true;
                    isAoaElevationEnabled = true;
                    break;
                case FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY:
                    isAoaAzimuthEnabled = true;
                    isAoaElevationEnabled = false;
                    break;
                case FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY:
                    isAoaAzimuthEnabled = false;
                    isAoaElevationEnabled = true;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid AOA result req");
            }
            if (openSessionParams.hasResultReportPhase()) {
                if (openSessionParams.hasAngleOfArrivalAzimuthReport()) {
                    isDestAoaAzimuthEnabled = true;
                }
                if (openSessionParams.hasAngleOfArrivalElevationReport()) {
                    isDestAoaElevationEnabled = true;
                }
            }
        }
        List<RangingMeasurement> rangingMeasurements = new ArrayList<>();
        UwbTwoWayMeasurement[] uwbTwoWayMeasurement = rangingData.getRangingTwoWayMeasures();
        for (int i = 0; i < rangingData.getNoOfRangingMeasures(); ++i) {
            UwbAddress macAddress = UwbAddress.fromBytes(TlvUtil.getReverseBytes(
                    uwbTwoWayMeasurement[i].getMacAddress()));
            int rangingStatus = uwbTwoWayMeasurement[i].getRangingStatus();
            DistanceMeasurement distanceMeasurement = null;
            AngleOfArrivalMeasurement angleOfArrivalMeasurement = null;
            AngleOfArrivalMeasurement destinationAngleOfArrivalMeasurement = null;
            int los = uwbTwoWayMeasurement[i].mNLoS;

            if (rangingStatus == FiraParams.STATUS_CODE_OK) {
                // Distance measurement is mandatory
                distanceMeasurement = new DistanceMeasurement.Builder()
                        .setMeters(uwbTwoWayMeasurement[i].getDistance() / (double) 100)
                        .setErrorMeters(0)
                        // TODO: Need to fetch distance FOM once it is added to UCI spec.
                        .setConfidenceLevel(0)
                        .build();
                // Aoa measurement is optional based on configuration.
                if (isAoaAzimuthEnabled || isAoaElevationEnabled) {
                    AngleMeasurement azimuthAngleMeasurement = null;
                    AngleMeasurement altitudeAngleMeasurement = null;
                    if (isAoaAzimuthEnabled) {
                        azimuthAngleMeasurement = new AngleMeasurement(
                                UwbUtil.degreeToRadian(uwbTwoWayMeasurement[i].getAoaAzimuth()),
                                0, uwbTwoWayMeasurement[i].getAoaAzimuthFom() / (double) 100);
                    }
                    if (isAoaElevationEnabled) {
                        altitudeAngleMeasurement = new AngleMeasurement(
                                UwbUtil.degreeToRadian(uwbTwoWayMeasurement[i].getAoaElevation()),
                                0, uwbTwoWayMeasurement[i].getAoaElevationFom() / (double) 100);
                    }
                    // AngleOfArrivalMeasurement
                    angleOfArrivalMeasurement = new AngleOfArrivalMeasurement.Builder(
                            azimuthAngleMeasurement)
                            .setAltitude(altitudeAngleMeasurement)
                            .build();
                }
                if (isDestAoaAzimuthEnabled || isDestAoaElevationEnabled) {
                    AngleMeasurement destinationAzimuthAngleMeasurement = null;
                    AngleMeasurement destinationAltitudeAngleMeasurement = null;
                    if (isDestAoaAzimuthEnabled) {
                        destinationAzimuthAngleMeasurement = new AngleMeasurement(
                                UwbUtil.degreeToRadian(uwbTwoWayMeasurement[i].getAoaDestAzimuth()),
                                0, uwbTwoWayMeasurement[i].getAoaDestAzimuthFom() / (double) 100);
                    }
                    if (isDestAoaElevationEnabled) {
                        destinationAltitudeAngleMeasurement = new AngleMeasurement(
                                UwbUtil.degreeToRadian(
                                        uwbTwoWayMeasurement[i].getAoaDestElevation()),
                                0, uwbTwoWayMeasurement[i].getAoaDestElevationFom() / (double) 100);
                    }
                    // Dest AngleOfArrivalMeasurement
                    destinationAngleOfArrivalMeasurement = new AngleOfArrivalMeasurement.Builder(
                            destinationAzimuthAngleMeasurement)
                            .setAltitude(destinationAltitudeAngleMeasurement)
                            .build();
                }
            }
            rangingMeasurements.add(new RangingMeasurement.Builder()
                    .setRemoteDeviceAddress(macAddress)
                    .setStatus(rangingStatus)
                    .setElapsedRealtimeNanos(elapsedRealtimeNanos)
                    .setDistanceMeasurement(distanceMeasurement)
                    .setAngleOfArrivalMeasurement(angleOfArrivalMeasurement)
                    .setDestinationAngleOfArrivalMeasurement(destinationAngleOfArrivalMeasurement)
                    .setLineOfSight(los)
                    .build());
        }
        if (rangingMeasurements.size() == 1) {
            return new RangingReport.Builder().addMeasurement(rangingMeasurements.get(0)).build();
        } else {
            return new RangingReport.Builder().addMeasurements(rangingMeasurements).build();
        }
    }
}
