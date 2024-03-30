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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.test.MockAnswerUtil;
import android.os.Handler;
import android.os.test.TestLooper;
import android.provider.DeviceConfig;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class DeviceConfigFacadeTest {
    @Mock UwbInjector mUwbInjector;

    final ArgumentCaptor<DeviceConfig.OnPropertiesChangedListener>
            mOnPropertiesChangedListenerCaptor =
            ArgumentCaptor.forClass(DeviceConfig.OnPropertiesChangedListener.class);

    private DeviceConfigFacade mDeviceConfigFacade;
    private TestLooper mLooper = new TestLooper();
    private MockitoSession mSession;

    /**
     * Setup the mocks and an instance of WifiConfigManager before each test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // static mocking
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig.class, withSettings().lenient())
                .startMocking();
        // Have DeviceConfig return the default value passed in.
        when(DeviceConfig.getBoolean(anyString(), anyString(), anyBoolean()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public boolean answer(String namespace, String field, boolean def) {
                        return def;
                    }
                });
        when(DeviceConfig.getInt(anyString(), anyString(), anyInt()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public int answer(String namespace, String field, int def) {
                        return def;
                    }
                });
        when(DeviceConfig.getLong(anyString(), anyString(), anyLong()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public long answer(String namespace, String field, long def) {
                        return def;
                    }
                });
        when(DeviceConfig.getString(anyString(), anyString(), anyString()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public String answer(String namespace, String field, String def) {
                        return def;
                    }
                });

        mDeviceConfigFacade = new DeviceConfigFacade(new Handler(mLooper.getLooper()),
                mUwbInjector);
        verify(() -> DeviceConfig.addOnPropertiesChangedListener(anyString(), any(),
                mOnPropertiesChangedListenerCaptor.capture()));
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        mSession.finishMocking();
    }

    /**
     * Verifies that default values are set correctly
     */
    @Test
    public void testDefaultValue() throws Exception {
        assertEquals(DeviceConfigFacade.DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS,
                mDeviceConfigFacade.getRangingResultLogIntervalMs());
        assertEquals(false, mDeviceConfigFacade.isDeviceErrorBugreportEnabled());
        assertEquals(DeviceConfigFacade.DEFAULT_BUG_REPORT_MIN_INTERVAL_MS,
                mDeviceConfigFacade.getBugReportMinIntervalMs());
    }

    /**
     * Verifies that all fields are updated properly.
     */
    @Test
    public void testFieldUpdates() throws Exception {
        // Simulate updating the fields
        when(DeviceConfig.getInt(anyString(), eq("ranging_result_log_interval_ms"),
                anyInt())).thenReturn(4000);
        when(DeviceConfig.getBoolean(anyString(), eq("device_error_bugreport_enabled"),
                anyBoolean())).thenReturn(true);
        when(DeviceConfig.getInt(anyString(), eq("bug_report_min_interval_ms"),
                anyInt())).thenReturn(10 * 3600_000);

        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);

        // Verifying fields are updated to the new values
        assertEquals(4000, mDeviceConfigFacade.getRangingResultLogIntervalMs());
        assertEquals(true, mDeviceConfigFacade.isDeviceErrorBugreportEnabled());
        assertEquals(10 * 3600_000, mDeviceConfigFacade.getBugReportMinIntervalMs());
    }
}
