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

import static com.android.server.uwb.DeviceConfigFacade.DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.RangingMeasurement;

import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.proto.UwbStatsLog;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

/**
 * Unit tests for {@link com.android.server.uwb.UwbMetrics}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbMetricsTest {
    private static final int CHANNEL_DEFAULT = 5;
    private static final int DISTANCE_DEFAULT_CM = 100;
    private static final int ELEVATION_DEFAULT_DEGREE = 50;
    private static final int AZIMUTH_DEFAULT_DEGREE = 56;
    private static final int ELEVATION_FOM_DEFAULT = 90;
    private static final int AZIMUTH_FOM_DEFAULT = 60;
    private static final int NLOS_DEFAULT = 1;
    private static final int VALID_RANGING_COUNT = 5;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private DeviceConfigFacade mDeviceConfigFacade;
    private UwbTwoWayMeasurement[] mTwoWayMeasurements = new UwbTwoWayMeasurement[1];
    @Mock
    private UwbTwoWayMeasurement mTwoWayMeasurement;
    @Mock
    private UwbRangingData mRangingData;
    @Mock
    private UwbSession mUwbSession;
    @Mock
    private FiraOpenSessionParams mFiraParams;

    private UwbMetrics mUwbMetrics;
    private MockitoSession mMockSession;
    private long mElapsedTimeMs;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setElapsedTimeMs(1000L);
        mTwoWayMeasurements[0] = mTwoWayMeasurement;
        when(mRangingData.getSessionId()).thenReturn(1L);
        when(mRangingData.getNoOfRangingMeasures()).thenReturn(1);
        when(mRangingData.getRangingMeasuresType()).thenReturn(
                (int) UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY);
        when(mTwoWayMeasurement.getRangingStatus()).thenReturn(FiraParams.STATUS_CODE_OK);
        when(mRangingData.getRangingTwoWayMeasures()).thenReturn(mTwoWayMeasurements);

        when(mUwbSession.getSessionId()).thenReturn(1);
        when(mUwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        when(mUwbSession.getProfileType()).thenReturn(
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA);
        when(mUwbSession.getParams()).thenReturn(mFiraParams);
        when(mFiraParams.getStsConfig()).thenReturn(FiraParams.STS_CONFIG_STATIC);
        when(mFiraParams.getDeviceRole()).thenReturn(FiraParams.RANGING_DEVICE_ROLE_INITIATOR);
        when(mFiraParams.getDeviceType()).thenReturn(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER);
        when(mFiraParams.getChannelNumber()).thenReturn(CHANNEL_DEFAULT);

        when(mTwoWayMeasurement.getDistance()).thenReturn(DISTANCE_DEFAULT_CM);
        when(mTwoWayMeasurement.getAoaAzimuth()).thenReturn((float) AZIMUTH_DEFAULT_DEGREE);
        when(mTwoWayMeasurement.getAoaAzimuthFom()).thenReturn(AZIMUTH_FOM_DEFAULT);
        when(mTwoWayMeasurement.getAoaElevation()).thenReturn((float) ELEVATION_DEFAULT_DEGREE);
        when(mTwoWayMeasurement.getAoaElevationFom()).thenReturn(ELEVATION_FOM_DEFAULT);
        when(mTwoWayMeasurement.getNLoS()).thenReturn(NLOS_DEFAULT);
        when(mDeviceConfigFacade.getRangingResultLogIntervalMs())
                .thenReturn(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);

        mUwbMetrics = new UwbMetrics(mUwbInjector);
        mMockSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(UwbStatsLog.class)
                .startMocking();
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        mMockSession.finishMocking();
    }

    private void setElapsedTimeMs(long elapsedTimeMs) {
        mElapsedTimeMs = elapsedTimeMs;
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(mElapsedTimeMs);
    }

    private void addElapsedTimeMs(long durationMs) {
        mElapsedTimeMs += durationMs;
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(mElapsedTimeMs);
    }

    @Test
    public void testLogRangingSessionAllEvents() throws Exception {
        mUwbMetrics.logRangingInitEvent(mUwbSession, UwbUciConstants.STATUS_CODE_OK);
        ExtendedMockito.verify(() -> UwbStatsLog.write(
                UwbStatsLog.UWB_SESSION_INITED,
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                UwbStatsLog.UWB_SESSION_INITIATED__STS__STATIC, true,
                true, false, true,
                CHANNEL_DEFAULT, UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SUCCESS,
                0, 0
        ));

        mUwbMetrics.longRangingStartEvent(mUwbSession, UwbUciConstants.STATUS_CODE_FAILED);
        addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        mUwbMetrics.longRangingStartEvent(mUwbSession, UwbUciConstants.STATUS_CODE_OK);
        addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);

        for (int i = 0; i < VALID_RANGING_COUNT; i++) {
            addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
            mUwbMetrics.logRangingResult(UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                    mRangingData);
        }
        when(mTwoWayMeasurement.getRangingStatus()).thenReturn(UwbUciConstants.STATUS_CODE_FAILED);
        mUwbMetrics.logRangingResult(UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                mRangingData);

        mUwbMetrics.logRangingCloseEvent(mUwbSession, UwbUciConstants.STATUS_CODE_FAILED);
        addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        mUwbMetrics.logRangingCloseEvent(mUwbSession, UwbUciConstants.STATUS_CODE_OK);

        ExtendedMockito.verify(() -> UwbStatsLog.write(UwbStatsLog.UWB_FIRST_RANGING_RECEIVED,
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS * 2,
                DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS * 2 / 200));

        ExtendedMockito.verify(() -> UwbStatsLog.write(UwbStatsLog.UWB_SESSION_CLOSED,
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                UwbStatsLog.UWB_SESSION_INITIATED__STS__STATIC, true,
                true, false, true,
                DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS * (VALID_RANGING_COUNT + 2),
                UwbStatsLog.UWB_SESSION_CLOSED__DURATION_BUCKET__TEN_SEC_TO_ONE_MIN,
                VALID_RANGING_COUNT + 1, VALID_RANGING_COUNT,
                UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__FIVE_TO_TWENTY,
                UwbStatsLog.UWB_SESSION_CLOSED__RANGING_COUNT_BUCKET__ONE_TO_FIVE,
                2, 1, 0));
    }

    @Test
    public void testLogRangingSessionInitFiraInvalidParams() throws Exception {
        when(mFiraParams.getStsConfig()).thenReturn(FiraParams.STS_CONFIG_DYNAMIC);
        when(mFiraParams.getDeviceRole()).thenReturn(FiraParams.RANGING_DEVICE_ROLE_RESPONDER);
        when(mFiraParams.getDeviceType()).thenReturn(FiraParams.RANGING_DEVICE_TYPE_CONTROLEE);

        mUwbMetrics.logRangingInitEvent(mUwbSession,
                UwbUciConstants.STATUS_CODE_INVALID_PARAM);
        ExtendedMockito.verify(() -> UwbStatsLog.write(
                UwbStatsLog.UWB_SESSION_INITED,
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                UwbStatsLog.UWB_SESSION_INITIATED__STS__DYNAMIC, false,
                false, false, true,
                CHANNEL_DEFAULT, UwbStatsLog.UWB_SESSION_INITIATED__STATUS__BAD_PARAMS,
                0, 0
        ));
    }

    @Test
    public void testLoggingRangingResultValidDistanceAngle() throws Exception {
        addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        mUwbMetrics.logRangingResult(UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                mRangingData);

        ExtendedMockito.verify(() -> UwbStatsLog.write(
                UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED,
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS,
                true, DISTANCE_DEFAULT_CM, DISTANCE_DEFAULT_CM / 50,
                RangingMeasurement.RSSI_UNKNOWN,
                true, AZIMUTH_DEFAULT_DEGREE, AZIMUTH_DEFAULT_DEGREE / 10, AZIMUTH_FOM_DEFAULT,
                true, ELEVATION_DEFAULT_DEGREE, ELEVATION_DEFAULT_DEGREE / 10, ELEVATION_FOM_DEFAULT
        ));
    }

    @Test
    public void testLoggingRangingResultSmallLoggingInterval() throws Exception {
        mUwbMetrics.logRangingResult(UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                mRangingData);

        ExtendedMockito.verify(() -> UwbStatsLog.write(
                UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED,
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS,
                true, DISTANCE_DEFAULT_CM, DISTANCE_DEFAULT_CM / 50,
                RangingMeasurement.RSSI_UNKNOWN,
                true, AZIMUTH_DEFAULT_DEGREE, AZIMUTH_DEFAULT_DEGREE / 10, AZIMUTH_FOM_DEFAULT,
                true, ELEVATION_DEFAULT_DEGREE, ELEVATION_DEFAULT_DEGREE / 10, ELEVATION_FOM_DEFAULT
        ), times(0));
    }

    @Test
    public void testLoggingRangingResultInvalidDistance() throws Exception {
        addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        when(mTwoWayMeasurement.getDistance()).thenReturn(UwbMetrics.INVALID_DISTANCE);
        when(mTwoWayMeasurement.getAoaAzimuth()).thenReturn((float) -10.0);
        when(mTwoWayMeasurement.getAoaAzimuthFom()).thenReturn(0);
        when(mTwoWayMeasurement.getAoaElevation()).thenReturn((float) -20.0);
        when(mTwoWayMeasurement.getAoaElevationFom()).thenReturn(0);
        when(mTwoWayMeasurement.getNLoS()).thenReturn(0);

        mUwbMetrics.logRangingResult(UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__CCC,
                mRangingData);

        ExtendedMockito.verify(() -> UwbStatsLog.write(
                UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED,
                UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__CCC,
                UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__LOS,
                false, UwbMetrics.INVALID_DISTANCE, 0,
                RangingMeasurement.RSSI_UNKNOWN,
                false, -10, 0, 0,
                false, -20, 0, 0
        ));
    }

    @Test
    public void testReportDeviceSuccessErrorCount() throws Exception {
        mUwbMetrics.incrementDeviceInitFailureCount();
        ExtendedMockito.verify(() -> UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__INIT_ERROR));
        mUwbMetrics.incrementDeviceInitSuccessCount();
        mUwbMetrics.incrementDeviceStatusErrorCount();
        ExtendedMockito.verify(() -> UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__DEVICE_STATUS_ERROR));
        mUwbMetrics.incrementUciGenericErrorCount();
        ExtendedMockito.verify(() -> UwbStatsLog.write(UwbStatsLog.UWB_DEVICE_ERROR_REPORTED,
                UwbStatsLog.UWB_DEVICE_ERROR_REPORTED__TYPE__UCI_GENERIC_ERROR));
    }

    @Test
    public void testDumpStatsNoCrash() throws Exception {
        mUwbMetrics.logRangingInitEvent(mUwbSession, UwbUciConstants.STATUS_CODE_OK);
        mUwbMetrics.logRangingInitEvent(mUwbSession,
                UwbUciConstants.STATUS_CODE_INVALID_PARAM);

        addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        mUwbMetrics.logRangingResult(UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__CCC, mRangingData);
        addElapsedTimeMs(DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        mUwbMetrics.logRangingResult(UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA,
                mRangingData);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        mUwbMetrics.dump(null, writer, null);
    }
}
