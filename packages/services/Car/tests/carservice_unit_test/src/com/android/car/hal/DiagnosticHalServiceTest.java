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

package com.android.car.hal;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.diagnostic.CarDiagnosticEvent;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.ServiceSpecificException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

@RunWith(MockitoJUnitRunner.class)
public class DiagnosticHalServiceTest {

    private static final long TEST_TIMESTAMP = 1L;
    private static final long TEST_TIMESTAMP2 = 2L;
    private static final int TEST_INT32_VALUE = 2;
    private static final float TEST_FLOAT_VALUE = 3.0f;
    private static final String TEST_STRING = "1234";

    @Mock
    private VehicleHal mVehicle;
    @Mock
    private DiagnosticHalService.DiagnosticListener mListener;

    private DiagnosticHalService mService;
    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*aidl=*/true);

    @Before
    public void setUp() {
        when(mVehicle.getHalPropValueBuilder()).thenReturn(mPropValueBuilder);
        mService = new DiagnosticHalService(mVehicle);
        mService.setDiagnosticListener(mListener);

        VehiclePropConfig freezeFrameConfig = new VehiclePropConfig();
        freezeFrameConfig.configString = new String();
        freezeFrameConfig.prop = VehicleProperty.OBD2_FREEZE_FRAME;
        // 8 custom int sensors and 1 custom float sensors.
        freezeFrameConfig.configArray = new int[]{8, 1};

        VehiclePropConfig liveFrameConfig = new VehiclePropConfig();
        liveFrameConfig.configString = new String();
        liveFrameConfig.prop = VehicleProperty.OBD2_LIVE_FRAME;
        // 8 custom int sensors and 1 custom float sensors.
        liveFrameConfig.configArray = new int[]{8, 1};

        mService.takeProperties(new ArrayList<HalPropConfig>(Arrays.asList(
                new AidlHalPropConfig(freezeFrameConfig),
                new AidlHalPropConfig(liveFrameConfig))));
    }

    private HalPropValue getTestFreezeFrame() {
        // 32 system int sensors + 8 custom int sensors + 71 system float sensors
        // + 1 custom float sensor.
        // Have value for system sensor 1 and system float sensor 1.
        int[] int32Values = new int[]{0, TEST_INT32_VALUE};
        float[] floatValues = new float[]{0.0f, TEST_FLOAT_VALUE};
        byte[] byteValues = new byte[14];
        // Set sensor 1 as active: 0000 0010
        byteValues[0] = (byte) 0x02;
        // Set sensor 1 as active: 0000 0010
        byteValues[5] = (byte) 0x02;

        return mPropValueBuilder.build(VehicleProperty.OBD2_FREEZE_FRAME,
                /*areaId=*/0, /*timestamp=*/TEST_TIMESTAMP, /*status=*/0,
                int32Values, floatValues, new long[0], TEST_STRING, byteValues);
    }

    private HalPropValue getTestLiveFrame() {
        // 32 system int sensors + 8 custom int sensors + 71 system float sensors
        // + 1 custom float sensor.
        // Have value for system sensor 1 and system float sensor 1.
        int[] int32Values = new int[]{0, TEST_INT32_VALUE};
        float[] floatValues = new float[]{0.0f, TEST_FLOAT_VALUE};
        byte[] byteValues = new byte[14];
        // Set sensor 1 as active: 0000 0010
        byteValues[0] = (byte) 0x02;
        // Set sensor 1 as active: 0000 0010
        byteValues[5] = (byte) 0x02;

        return mPropValueBuilder.build(VehicleProperty.OBD2_LIVE_FRAME,
                /*areaId=*/0, /*timestamp=*/TEST_TIMESTAMP, /*status=*/0,
                int32Values, floatValues, new long[0], TEST_STRING, byteValues);
    }

    private CarDiagnosticEvent getTestFreezeEvent() {
        return CarDiagnosticEvent.Builder.newFreezeFrameBuilder()
                .setTimeStamp(TEST_TIMESTAMP)
                .withIntValue(1, TEST_INT32_VALUE)
                .withFloatValue(1, TEST_FLOAT_VALUE)
                .withDtc(TEST_STRING).build();
    }

    private CarDiagnosticEvent getTestLiveEvent() {
        return CarDiagnosticEvent.Builder.newLiveFrameBuilder()
                .setTimeStamp(TEST_TIMESTAMP)
                .withIntValue(1, TEST_INT32_VALUE)
                .withFloatValue(1, TEST_FLOAT_VALUE)
                .withDtc(TEST_STRING).build();
    }

    @Test
    public void testOnHalEvents() {
        // Check the argument when called because the events would be cleared after the call.
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            assertThat(args[0]).isEqualTo(new LinkedList<CarDiagnosticEvent>(Arrays.asList(
                    getTestFreezeEvent(), getTestLiveEvent())));
            return null;
        }).when(mListener).onDiagnosticEvents(any());

        mService.onHalEvents(new ArrayList<HalPropValue>(Arrays.asList(
                getTestFreezeFrame(), getTestLiveFrame())));
    }

    @Test
    public void testGetCurrentLiveFrame_ok() {
        when(mVehicle.get(VehicleProperty.OBD2_LIVE_FRAME)).thenReturn(getTestLiveFrame());

        assertThat(mService.getCurrentLiveFrame()).isEqualTo(getTestLiveEvent());
    }

    @Test
    public void testGetCurrentLiveFrame_ServiceSpecificException() {
        when(mVehicle.get(VehicleProperty.OBD2_LIVE_FRAME)).thenThrow(
                new ServiceSpecificException(0));

        assertThat(mService.getCurrentLiveFrame()).isNull();
    }

    @Test
    public void testGetCurrentLiveFrame_IllegalArgumentException() {
        when(mVehicle.get(VehicleProperty.OBD2_LIVE_FRAME)).thenThrow(
                new IllegalArgumentException());

        assertThat(mService.getCurrentLiveFrame()).isNull();
    }

    @Test
    public void testGetFreezeFrameTimestamps_ok() {
        HalPropValue value = mPropValueBuilder.build(VehicleProperty.OBD2_FREEZE_FRAME_INFO, 0,
                new long[]{TEST_TIMESTAMP, TEST_TIMESTAMP2});
        when(mVehicle.get(VehicleProperty.OBD2_FREEZE_FRAME_INFO)).thenReturn(value);

        assertThat(mService.getFreezeFrameTimestamps()).isEqualTo(
                new long[]{TEST_TIMESTAMP, TEST_TIMESTAMP2});
    }

    @Test
    public void testGetFreezeFrameTimestamps_ServiceSpecificException() {
        when(mVehicle.get(VehicleProperty.OBD2_FREEZE_FRAME_INFO)).thenThrow(
                new ServiceSpecificException(0));

        assertThat(mService.getFreezeFrameTimestamps()).isNull();
    }

    @Test
    public void testGetFreezeFrameTimestamps_IllegalArgumentException() {
        when(mVehicle.get(VehicleProperty.OBD2_FREEZE_FRAME_INFO)).thenThrow(
                new IllegalArgumentException());

        assertThat(mService.getFreezeFrameTimestamps()).isNull();
    }

    @Test
    public void testGetFreezeFrame_ok() {
        HalPropValue value = mPropValueBuilder.build(
                VehicleProperty.OBD2_FREEZE_FRAME, 0, TEST_TIMESTAMP);
        when(mVehicle.get(value)).thenReturn(getTestFreezeFrame());

        assertThat(mService.getFreezeFrame(TEST_TIMESTAMP)).isEqualTo(getTestFreezeEvent());
    }

    @Test
    public void testGetFreezeFrame_ServiceSpecificException() {
        when(mVehicle.get(any())).thenThrow(new ServiceSpecificException(0));

        assertThat(mService.getFreezeFrame(TEST_TIMESTAMP)).isNull();
    }

    @Test
    public void testGetFreezeFrame_IllegalArgumentException() {
        when(mVehicle.get(any())).thenThrow(new IllegalArgumentException());

        assertThat(mService.getFreezeFrame(TEST_TIMESTAMP)).isNull();
    }

    @Test
    public void testClearFreezeFrames_ok() {
        HalPropValue value = mPropValueBuilder.build(
                VehicleProperty.OBD2_FREEZE_FRAME_CLEAR, 0,
                new long[]{TEST_TIMESTAMP, TEST_TIMESTAMP2});

        mService.clearFreezeFrames(TEST_TIMESTAMP, TEST_TIMESTAMP2);

        verify(mVehicle).set(value);
    }

    @Test
    public void testClearFreezeFrames_ServiceSpecificException() {
        doThrow(new ServiceSpecificException(0)).when(mVehicle).set(any());

        mService.clearFreezeFrames(TEST_TIMESTAMP, TEST_TIMESTAMP2);
    }

    @Test
    public void testClearFreezeFrames_IllegalArgumentException() {
        doThrow(new IllegalArgumentException()).when(mVehicle).set(any());

        mService.clearFreezeFrames(TEST_TIMESTAMP, TEST_TIMESTAMP2);
    }
}
