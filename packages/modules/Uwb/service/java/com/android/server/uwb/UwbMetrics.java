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

import android.util.SparseArray;
import android.uwb.RangingMeasurement;

import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.proto.UwbStatsLog;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;

/**
 * A class to collect and report UWB metrics.
 */
public class UwbMetrics {
    private static final String TAG = "UwbMetrics";

    private static final int MAX_RANGING_SESSIONS = 128;
    private static final int MAX_RANGING_REPORTS = 1024;
    public static final int INVALID_DISTANCE = 0xFFFF;
    private static final int ONE_SECOND_IN_MS = 1000;
    private static final int TEN_SECOND_IN_MS = 10 * 1000;
    private static final int ONE_MIN_IN_MS = 60 * 1000;
    private static final int TEN_MIN_IN_MS = 600 * 1000;
    private static final int ONE_HOUR_IN_MS = 3600 * 1000;
    private final UwbInjector mUwbInjector;
    private final Deque<RangingSessionStats> mRangingSessionList = new ArrayDeque<>();
    private final SparseArray<RangingSessionStats> mOpenedSessionMap = new SparseArray<>();
    private final Deque<RangingReportEvent> mRangingReportList = new ArrayDeque<>();
    private int mNumApps = 0;
    private long mLastRangingDataLogTimeMs;
    private final Object mLock = new Object();

    /**
     * The class storing the stats of a ranging session.
     */
    public class RangingSessionStats {
        private int mSessionId;
        private int mChannel = 9;
        private long mStartTimeWallClockMs;
        private long mStartTimeSinceBootMs;
        private int mInitLatencyMs;
        private int mInitStatus;
        private int mActiveDuration;
        private int mRangingCount;
        private int mValidRangingCount;
        private boolean mHasValidRangingSinceStart;
        private int mStartCount;
        private int mStartFailureCount;
        private int mStartNoValidReportCount;
        private int mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__UNKNOWN_STS;
        private boolean mIsInitiator;
        private boolean mIsController;
        private boolean mIsDiscoveredByFramework = false;
        private boolean mIsOutOfBand = true;

        RangingSessionStats(int sessionId) {
            mSessionId = sessionId;
            mStartTimeWallClockMs = mUwbInjector.getWallClockMillis();
        }

        /**
         * Parse UWB profile parameters
         */
        public void parseParams(Params params) {
            if (params instanceof FiraOpenSessionParams) {
                parseFiraParams((FiraOpenSessionParams) params);
            } else if (params instanceof CccOpenRangingParams) {
                parseCccParams((CccOpenRangingParams) params);
            }
        }

        private void parseFiraParams(FiraOpenSessionParams params) {
            if (params.getStsConfig() == FiraParams.STS_CONFIG_STATIC) {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__STATIC;
            } else if (params.getStsConfig() == FiraParams.STS_CONFIG_DYNAMIC) {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__DYNAMIC;
            } else {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__PROVISIONED;
            }

            mIsInitiator = params.getDeviceRole() == FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
            mIsController = params.getDeviceType() == FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
            mChannel = params.getChannelNumber();
        }

        private void parseCccParams(CccOpenRangingParams params) {
            mChannel = params.getChannel();
        }

        private void convertInitStatus(int status) {
            mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__GENERAL_FAILURE;
            switch (status) {
                case UwbUciConstants.STATUS_CODE_OK:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SUCCESS;
                    break;
                case UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SESSION_EXCEEDED;
                    break;
                case UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SESSION_DUPLICATE;
                    break;
                case UwbUciConstants.STATUS_CODE_INVALID_PARAM:
                case UwbUciConstants.STATUS_CODE_INVALID_RANGE:
                case UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__BAD_PARAMS;
                    break;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("rangingStartTime=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mStartTimeWallClockMs);
                sb.append(mStartTimeWallClockMs == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", sessionId=").append(mSessionId);
                sb.append(", initLatencyMs=").append(mInitLatencyMs);
                sb.append(", activeDurationMs=").append(mActiveDuration);
                sb.append(", rangingCount=").append(mRangingCount);
                sb.append(", validRangingCount=").append(mValidRangingCount);
                sb.append(", startCount").append(mStartCount);
                sb.append(", startFailureCount").append(mStartFailureCount);
                sb.append(", startNoValidReportCount").append(mStartNoValidReportCount);
                sb.append(", initStatus=").append(mInitStatus);
                sb.append(", channel=").append(mChannel);
                sb.append(", initiator=").append(mIsInitiator);
                sb.append(", controller=").append(mIsController);
                sb.append(", discoveredByFramework=").append(mIsDiscoveredByFramework);
                return sb.toString();
            }
        }
    }

    private class RangingReportEvent {
        private int mSessionId;
        private int mNlos;
        private int mDistanceCm;
        private int mAzimuthDegree;
        private int mAzimuthFom;
        private int mElevationDegree;
        private int mElevationFom;
        private long mWallClockMillis;

        RangingReportEvent(int sessionId, int nlos, int distanceCm,
                int azimuthDegree, int azimuthFom,
                int elevationDegree, int elevationFom) {
            mSessionId = sessionId;
            mWallClockMillis = mUwbInjector.getWallClockMillis();
            mNlos = nlos;
            mDistanceCm = distanceCm;
            mAzimuthDegree = azimuthDegree;
            mAzimuthFom = azimuthFom;
            mElevationDegree = elevationDegree;
            mElevationFom = elevationFom;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("time=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mWallClockMillis);
                sb.append(mWallClockMillis == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", sessionId=").append(mSessionId);
                sb.append(", Nlos=").append(mNlos);
                sb.append(", DistanceCm=").append(mDistanceCm);
                sb.append(", AzimuthDegree=").append(mAzimuthDegree);
                sb.append(", AzimuthFom=").append(mAzimuthFom);
                sb.append(", ElevationDegree=").append(mElevationDegree);
                sb.append(", ElevationFom=").append(mElevationFom);
                return sb.toString();
            }
        }
    }

    public UwbMetrics(UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }

    /**
     * Log the ranging session initialization event
     */
    public void logRangingInitEvent(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            // If past maximum events, start removing the oldest
            while (mRangingSessionList.size() >= MAX_RANGING_SESSIONS) {
                mRangingSessionList.removeFirst();
            }
            RangingSessionStats session = new RangingSessionStats(uwbSession.getSessionId());
            session.parseParams(uwbSession.getParams());
            session.convertInitStatus(status);
            mRangingSessionList.add(session);
            mOpenedSessionMap.put(uwbSession.getSessionId(), session);
            UwbStatsLog.write(UwbStatsLog.UWB_SESSION_INITED, uwbSession.getProfileType(),
                    session.mStsType, session.mIsInitiator,
                    session.mIsController, session.mIsDiscoveredByFramework, session.mIsOutOfBand,
                    session.mChannel, session.mInitStatus,
                    session.mInitLatencyMs, session.mInitLatencyMs / 20);
        }
    }

    /**
     * Log the ranging session start event
     */
    public void longRangingStartEvent(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            session.mStartCount++;
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                session.mStartFailureCount++;
                session.mStartTimeSinceBootMs = 0;
                session.mHasValidRangingSinceStart = false;
                return;
            }
            session.mStartTimeSinceBootMs = mUwbInjector.getElapsedSinceBootMillis();
        }
    }

    /**
     * Log the ranging session stop event
     */
    public void longRangingStopEvent(UwbSession uwbSession) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            if (session.mStartTimeSinceBootMs == 0) {
                return;
            }
            if (!session.mHasValidRangingSinceStart) {
                session.mStartNoValidReportCount++;
            }
            session.mHasValidRangingSinceStart = false;
            session.mActiveDuration += (int) (mUwbInjector.getElapsedSinceBootMillis()
                    - session.mStartTimeSinceBootMs);
            session.mStartTimeSinceBootMs = 0;
        }
    }

    /**
     * Log the ranging session close event
     */
    public void logRangingCloseEvent(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            RangingSessionStats session = mOpenedSessionMap.get(uwbSession.getSessionId());
            if (session == null) {
                return;
            }
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                return;
            }
            // Ranging may close without stop event
            if (session.mStartTimeSinceBootMs != 0) {
                session.mActiveDuration += (int) (mUwbInjector.getElapsedSinceBootMillis()
                        - session.mStartTimeSinceBootMs);
                if (!session.mHasValidRangingSinceStart) {
                    session.mStartNoValidReportCount++;
                }
                session.mStartTimeSinceBootMs = 0;
                session.mHasValidRangingSinceStart = false;
            }

            UwbStatsLog.write(UwbStatsLog.UWB_SESSION_CLOSED, uwbSession.getProfileType(),
                    session.mStsType, session.mIsInitiator,
                    session.mIsController, session.mIsDiscoveredByFramework, session.mIsOutOfBand,
                    session.mActiveDuration, getDurationBucket(session.mActiveDuration),
                    session.mRangingCount, session.mValidRangingCount,
                    getCountBucket(session.mRangingCount),
                    getCountBucket(session.mValidRangingCount),
                    session.mStartCount,
                    session.mStartFailureCount,
                    session.mStartNoValidReportCount);
            mOpenedSessionMap.delete(uwbSession.getSessionId());
        }
    }

    private int getDurationBucket(int durationMs) {
        if (durationMs <= ONE_SECOND_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__WITHIN_ONE_SEC;
        } else if (durationMs <= TEN_SECOND_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__ONE_TO_TEN_SEC;
        } else if (durationMs <= ONE_MIN_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__TEN_SEC_TO_ONE_MIN;
        } else if (durationMs <= TEN_MIN_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__ONE_TO_TEN_MIN;
        } else if (durationMs <= ONE_HOUR_IN_MS) {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__TEN_MIN_TO_ONE_HOUR;
        } else {
            return UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__MORE_THAN_ONE_HOUR;
        }
    }

    private int getCountBucket(int count) {
        if (count <= 0) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__ZERO;
        } else if (count <= 5) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__ONE_TO_FIVE;
        } else if (count <= 20) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__FIVE_TO_TWENTY;
        } else if (count <= 100) {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__TWENTY_TO_ONE_HUNDRED;
        } else if (count <= 500) {
            return UwbStatsLog
                    .UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__ONE_HUNDRED_TO_FIVE_HUNDRED;
        } else {
            return UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__MORE_THAN_FIVE_HUNDRED;
        }
    }

    /**
     * Log the usage of API from a new App
     */
    public void logNewAppUsage() {
        synchronized (mLock) {
            mNumApps++;
        }
    }

    /**
     * Log the ranging measurement result
     */
    public void logRangingResult(int profileType, UwbRangingData rangingData) {
        synchronized (mLock) {
            if (rangingData.getRangingMeasuresType()
                    != UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY
                    || rangingData.getNoOfRangingMeasures() < 1) {
                return;
            }

            UwbTwoWayMeasurement[] uwbTwoWayMeasurement = rangingData.getRangingTwoWayMeasures();
            UwbTwoWayMeasurement measurement = uwbTwoWayMeasurement[0];

            int sessionId = (int) rangingData.getSessionId();
            RangingSessionStats session = mOpenedSessionMap.get(sessionId);
            if (session != null) {
                session.mRangingCount++;
            }

            int rangingStatus = measurement.getRangingStatus();
            if (rangingStatus != UwbUciConstants.STATUS_CODE_OK) {
                return;
            }

            if (session != null) {
                session.mValidRangingCount++;
                if (!session.mHasValidRangingSinceStart) {
                    session.mHasValidRangingSinceStart = true;
                    writeFirstValidRangingResultSinceStart(profileType, session);
                }
            }
            int distanceCm = measurement.getDistance();
            int azimuthDegree = (int) measurement.getAoaAzimuth();
            int azimuthFom = measurement.getAoaAzimuthFom();
            int elevationDegree = (int) measurement.getAoaElevation();
            int elevationFom = measurement.getAoaElevationFom();
            int nlos = getNlos(measurement);

            while (mRangingReportList.size() >= MAX_RANGING_REPORTS) {
                mRangingReportList.removeFirst();
            }
            RangingReportEvent report = new RangingReportEvent(sessionId, nlos, distanceCm,
                    azimuthDegree, azimuthFom, elevationDegree, elevationFom);
            mRangingReportList.add(report);

            long currTimeMs = mUwbInjector.getElapsedSinceBootMillis();
            if ((currTimeMs - mLastRangingDataLogTimeMs) < mUwbInjector.getDeviceConfigFacade()
                    .getRangingResultLogIntervalMs()) {
                return;
            }
            mLastRangingDataLogTimeMs = currTimeMs;

            boolean isDistanceValid = distanceCm != INVALID_DISTANCE;
            boolean isAzimuthValid = azimuthFom > 0;
            boolean isElevationValid = elevationFom > 0;
            int distance50Cm = isDistanceValid ? distanceCm / 50 : 0;
            int azimuth10Degree = isAzimuthValid ? azimuthDegree / 10 : 0;
            int elevation10Degree = isElevationValid ? elevationDegree / 10 : 0;
            UwbStatsLog.write(UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED, profileType, nlos,
                    isDistanceValid, distanceCm, distance50Cm, RangingMeasurement.RSSI_UNKNOWN,
                    isAzimuthValid, azimuthDegree, azimuth10Degree, azimuthFom,
                    isElevationValid, elevationDegree, elevation10Degree, elevationFom);
        }
    }

    private void writeFirstValidRangingResultSinceStart(int profileType,
            RangingSessionStats session) {
        int latencyMs = (int) (mUwbInjector.getElapsedSinceBootMillis()
                - session.mStartTimeSinceBootMs);
        UwbStatsLog.write(UwbStatsLog.UWB_FIRST_RANGING_RECEIVED,
                profileType, latencyMs, latencyMs / 200);
    }

    private int getNlos(UwbTwoWayMeasurement measurement) {
        int nlos = measurement.getNLoS();
        if (nlos == 0) {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__LOS;
        } else if (nlos == 1) {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS;
        } else {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS_UNKNOWN;
        }
    }

    private int mNumDeviceInitSuccess = 0;
    private int mNumDeviceInitFailure = 0;
    private int mNumDeviceStatusError = 0;
    private int mNumUciGenericError = 0;

    /**
     * Increment the count of device initialization success
     */
    public synchronized void incrementDeviceInitSuccessCount() {
        mNumDeviceInitSuccess++;
    }

    /**
     * Increment the count of device initialization failure
     */
    public synchronized void incrementDeviceInitFailureCount() {
        mNumDeviceInitFailure++;
        UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__INIT_ERROR);
    }

    /**
     * Increment the count of device status error
     */
    public synchronized void incrementDeviceStatusErrorCount() {
        mNumDeviceStatusError++;
        UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__DEVICE_STATUS_ERROR);
    }

    /**
     * Increment the count of UCI generic error which will trigger UCI command retry
     */
    public synchronized void incrementUciGenericErrorCount() {
        mNumUciGenericError++;
        UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__UCI_GENERIC_ERROR);
    }

    /**
     * Dump the UWB logs
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mLock) {
            pw.println("---- Dump of UwbMetrics ----");
            pw.println("---- mRangingSessionList ----");
            for (RangingSessionStats stats: mRangingSessionList) {
                pw.println(stats.toString());
            }
            pw.println("---- mOpenedSessionMap ----");
            for (int i = 0; i < mOpenedSessionMap.size(); i++) {
                pw.println(mOpenedSessionMap.valueAt(i).toString());
            }
            pw.println("---- mRangingReportList ----");
            for (RangingReportEvent event: mRangingReportList) {
                pw.println(event.toString());
            }
            pw.println("mNumApps=" + mNumApps);
            pw.println("---- Device operation success/error count ----");
            pw.println("mNumDeviceInitSuccess = " + mNumDeviceInitSuccess);
            pw.println("mNumDeviceInitFailure = " + mNumDeviceInitFailure);
            pw.println("mNumDeviceStatusError = " + mNumDeviceStatusError);
            pw.println("mNumUciGenericError = " + mNumUciGenericError);
        }
    }
}
