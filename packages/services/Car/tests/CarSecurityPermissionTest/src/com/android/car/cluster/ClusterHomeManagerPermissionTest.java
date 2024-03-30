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

package com.android.car.cluster;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.testng.Assert.expectThrows;

import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

/**
 * This test ensures that {@link SecurityException} is thrown when client doesn't have
 * {@code android.car.permission.CAR_INSTRUMENT_CLUSTER_CONTROL} permission granted.
 *
 * Test will skip when {@link ClusterHomeManager} is not available.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ClusterHomeManagerPermissionTest {
    public static final String EXPECTED_ERROR_MESSAGE =
            "requires permission android.car.permission.CAR_INSTRUMENT_CLUSTER_CONTROL";
    public static final String EXPECTED_ERROR_MESSAGE_MISSING_MONITOR_NAVIGATION_STATE_PERMISSION =
            "requires permission android.car.permission.CAR_MONITOR_CLUSTER_NAVIGATION_STATE";
    private Car mCar;

    private ClusterHomeManager mClusterHomeManager;

    @Mock
    private ClusterHomeManager.ClusterStateListener mMockClusterStateListener;

    @Mock
    private ClusterHomeManager.ClusterNavigationStateListener mMockClusterNavigationStateListener;

    @Mock
    private Executor mMockExecutor;

    @Before
    public void setUp() {
        mCar = Car.createCar(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        assertThat(mCar).isNotNull();

        mClusterHomeManager = (ClusterHomeManager) mCar.getCarManager(Car.CLUSTER_HOME_SERVICE);
        assumeNotNull(mClusterHomeManager);
    }

    @After
    public void tearDown() {
        mCar.disconnect();
    }

    @Test
    public void testRegisterClusterStateListener_requiresPermission() {
        SecurityException thrown = expectThrows(SecurityException.class,
                () -> mClusterHomeManager.registerClusterStateListener(mMockExecutor,
                        mMockClusterStateListener));
        assertThat(thrown.getMessage()).isEqualTo(EXPECTED_ERROR_MESSAGE);
    }

    @Test
    public void testRegisterClusterNavigationStateListener_requiresPermission() {
        SecurityException thrown = expectThrows(SecurityException.class,
                () -> mClusterHomeManager.registerClusterNavigationStateListener(mMockExecutor,
                        mMockClusterNavigationStateListener));
        assertThat(thrown.getMessage())
                .isEqualTo(EXPECTED_ERROR_MESSAGE_MISSING_MONITOR_NAVIGATION_STATE_PERMISSION);
    }

    @Test
    public void testGetClusterState_requiresPermission() {
        SecurityException thrown = expectThrows(SecurityException.class,
                () -> mClusterHomeManager.getClusterState());
        assertThat(thrown.getMessage()).isEqualTo(EXPECTED_ERROR_MESSAGE);
    }

    @Test
    public void testUnReportState_requiresPermission() {
        SecurityException thrown = expectThrows(SecurityException.class,
                () -> mClusterHomeManager.reportState(anyInt(), anyInt(), any(byte[].class)));
        assertThat(thrown.getMessage()).isEqualTo(EXPECTED_ERROR_MESSAGE);
    }

    @Test
    public void testRequestDisplay_requiresPermission() {
        SecurityException thrown = expectThrows(SecurityException.class,
                () -> mClusterHomeManager.requestDisplay(anyInt()));
        assertThat(thrown.getMessage()).isEqualTo(EXPECTED_ERROR_MESSAGE);
    }

    @Test
    public void testStartFixedActivityModeAsUser_requiresPermission() {
        SecurityException thrown = expectThrows(SecurityException.class,
                () -> mClusterHomeManager.startFixedActivityModeAsUser(any(Intent.class), any(
                        Bundle.class), anyInt()));
        assertThat(thrown.getMessage()).isEqualTo(EXPECTED_ERROR_MESSAGE);
    }

    @Test
    public void testStopFixedActivityMode_requiresPermission() {
        SecurityException thrown = expectThrows(SecurityException.class,
                () -> mClusterHomeManager.stopFixedActivityMode());
        assertThat(thrown.getMessage()).isEqualTo(EXPECTED_ERROR_MESSAGE);
    }
}
