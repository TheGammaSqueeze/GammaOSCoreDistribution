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

package com.android.libraries.testing.deviceshadower.testcases;

import android.app.Application;

import com.android.libraries.testing.deviceshadower.DeviceShadowEnvironment;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowLocalSocket;
import com.android.libraries.testing.deviceshadower.shadows.bluetooth.ShadowParcelFileDescriptor;
import com.android.libraries.testing.deviceshadower.shadows.common.DeviceShadowContextImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Base class for all DeviceShadower client.
 */
@Config(
        // sdk = 21,
        shadows = {
                DeviceShadowContextImpl.class,
                ShadowParcelFileDescriptor.class,
                ShadowLocalSocket.class
        })
public class BaseTestCase {

    protected Application mContext = RuntimeEnvironment.application;

    /**
     * Test Watcher which logs test starting and finishing so log messages are easier to read.
     */
    @Rule
    public TestWatcher watcher = new TestWatcher() {
        @Override
        protected void succeeded(Description description) {
            super.succeeded(description);
            logMessage(
                    String.format("Test %s finished successfully.", description.getDisplayName()));
        }

        @Override
        protected void failed(Throwable e, Description description) {
            super.failed(e, description);
            logMessage(String.format("Test %s failed.", description.getDisplayName()));
        }

        @Override
        protected void skipped(AssumptionViolatedException e, Description description) {
            super.skipped(e, description);
            logMessage(String.format("Test %s is skipped.", description.getDisplayName()));
        }

        @Override
        protected void starting(Description description) {
            super.starting(description);
            logMessage(String.format("Test %s started.", description.getDisplayName()));
        }

        @Override
        protected void finished(Description description) {
            super.finished(description);
        }

        private void logMessage(String message) {
            System.out.println("\n*** " + message);
        }
    };

    @Before
    public void setUp() throws Exception {
        DeviceShadowEnvironment.init();
    }

    @After
    public void tearDown() throws Exception {
        DeviceShadowEnvironment.reset();
    }
}
