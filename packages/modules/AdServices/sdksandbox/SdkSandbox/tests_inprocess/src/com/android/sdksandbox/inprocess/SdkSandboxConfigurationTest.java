/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.sdksandbox.inprocess;

import static android.content.Context.MODE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SELinux;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Tests to check some basic properties of the Sdk Sandbox processes.
 */
@RunWith(JUnit4.class)
public class SdkSandboxConfigurationTest {

    private static final String TEST_PKG = "com.android.sdksandbox.inprocesstests";

    /**
     * Tests that uid belongs to the sdk sandbox processes uid range.
     */
    @Test
    public void testUidBelongsToSdkSandboxRange() throws Exception {
        int myUid = Process.myUid();
        assertWithMessage(myUid + " is not a SdkSandbox uid").that(Process.isSdkSandbox()).isTrue();
    }

    /**
     * Tests that sdk sandbox processes are running under the {@code sdk_sandbox} selinux domain.
     */
    @Test
    public void testCorrectSelinuxDomain() throws Exception {
        final String ctx = SELinux.getContext();
        assertThat(ctx).contains("u:r:sdk_sandbox");
    }

    /**
     * Tests that client app is visible to the sdk sandbox.
     */
    @Test
    public void testClientAppIsVisibleToSdkSandbox() throws Exception {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final PackageManager pm = ctx.getPackageManager();
        final PackageInfo info = pm.getPackageInfo(TEST_PKG, 0);
        assertThat(info.applicationInfo.uid).isEqualTo(
                Process.getAppUidForSdkSandboxUid(Process.myUid()));
    }

    /**
     * Tests that {@link Context#getDataDir()} returns correct value for the CE storage of the
     * sak sandbox.
     */
    @Test
    public void testGetDataDir_CE() throws Exception {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File dir = ctx.getDataDir();
        assertThat(dir.getAbsolutePath()).isEqualTo(
                "/data/misc_ce/0/sdksandbox/" + TEST_PKG + "/shared");
    }

    /**
     * Tests that {@link Context#getDataDir()} returns correct value for the DE storage of the
     * sak sandbox.
     */
    @Test
    public void testGetDataDir_DE() throws Exception {
        final Context ctx =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .createDeviceProtectedStorageContext();
        final File dir = ctx.getDataDir();
        assertThat(dir.getAbsolutePath()).isEqualTo(
                "/data/misc_de/0/sdksandbox/" + TEST_PKG + "/shared");
    }

    /**
     * Tests that sdk sandbox process can write to it's CE storage.
     */
    @Test
    public void testCanWriteToDataDir_CE() throws Exception {
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                ctx.openFileOutput("random_ce_file", MODE_PRIVATE))) {
            writer.write("I am an sdk sandbox");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ctx.openFileInput("random_ce_file")))) {
            String line = reader.readLine();
            assertThat(line).isEqualTo("I am an sdk sandbox");
        }
    }

    /**
     * Tests that sdk sandbox process can write to it's DE storage.
     */
    @Test
    public void testCanWriteToDataDir_DE() throws Exception {
        final Context ctx =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .createDeviceProtectedStorageContext();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                ctx.openFileOutput("random_de_file", MODE_PRIVATE))) {
            writer.write("I am also an sdk sandbox");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ctx.openFileInput("random_de_file")))) {
            String line = reader.readLine();
            assertThat(line).isEqualTo("I am also an sdk sandbox");
        }
    }
}
