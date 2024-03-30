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

package com.android.remoteprovisioner.testapk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.testing.TestWorkerBuilder;

import com.android.remoteprovisioner.WidevineProvisioner;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class WidevineTest {

    private static boolean sSupportsWidevine = true;
    private static final String TAG = "RemoteProvisionerWidevineTest";
    private static MediaDrm sDrm;

    @BeforeClass
    public static void init() throws Exception {
        try {
            sDrm = new MediaDrm(WidevineProvisioner.WIDEVINE_UUID);
        } catch (UnsupportedSchemeException e) {
            Log.i(TAG, "Device doesn't support widevine, all tests should pass.");
            sSupportsWidevine = false;
        }
    }

    private boolean isProvisioning4() {
        if (!sDrm.getPropertyString("provisioningModel").equals("BootCertificateChain")) {
            // Not a provisioning 4.0 device.
            return false;
        }
        return true;
    }

    private boolean isProvisioned() {
        int systemId = Integer.parseInt(sDrm.getPropertyString("systemId"));
        if (systemId != Integer.MAX_VALUE) {
            return true;
        }
        return false;
    }

    @Test
    public void testIfProvisioningNeededIsConsistentWithSystemStatus() {
        if (!sSupportsWidevine) return;
        assertEquals(isProvisioning4() && !isProvisioned(),
                     WidevineProvisioner.isWidevineProvisioningNeeded());
    }

    @Test
    public void testProvisionWidevine() {
        if (!sSupportsWidevine) return;
        if (!isProvisioning4()) {
            Log.i(TAG, "Not a provisioning 4.0 device.");
            return;
        }
        WidevineProvisioner prov = TestWorkerBuilder.from(
                ApplicationProvider.getApplicationContext(),
                WidevineProvisioner.class,
                Executors.newSingleThreadExecutor()).build();
        assertFalse(isProvisioned());
        assertEquals(ListenableWorker.Result.success(), prov.doWork());
        assertTrue(isProvisioned());
    }
}
