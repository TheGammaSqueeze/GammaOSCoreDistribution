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

package com.android.remoteprovisioner.hosttest;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class RemoteProvisionerWidevineTests extends BaseHostJUnit4Test {
    private static final String TEST_PACKAGE_NAME = "com.android.remoteprovisioner.testapk";
    private static final String WV_CERT_LOCATION = "/data/vendor/mediadrm/IDM1013/L1/oemcert.bin";

    private void deleteWidevineCert() throws Exception {
        assertTrue("Test requires ability to get root.", getDevice().enableAdbRoot());
        getDevice().executeShellCommand("rm " + WV_CERT_LOCATION);
    }

    private void runTest(String testClassName, String testMethodName) throws Exception {
        testClassName = TEST_PACKAGE_NAME + "." + testClassName;
        assertTrue(runDeviceTests(TEST_PACKAGE_NAME, testClassName, testMethodName));
    }

    @Test
    public void testIfProvisioningNeededIsConsistentWithSystemStatus() throws Exception {
        runTest("WidevineTest", "testIfProvisioningNeededIsConsistentWithSystemStatus");
    }

    @Test
    public void testWipeAndReprovisionCert() throws Exception {
        deleteWidevineCert();
        runTest("WidevineTest", "testProvisionWidevine");
    }
}
