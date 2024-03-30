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

package com.android.remoteprovisioner.unittest;

import static android.hardware.security.keymint.SecurityLevel.TRUSTED_ENVIRONMENT;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.hardware.security.keymint.DeviceInfo;
import android.hardware.security.keymint.ProtectedData;
import android.os.Build;
import android.os.ServiceManager;
import android.security.IGenerateRkpKeyService.Status;
import android.security.remoteprovisioning.IRemoteProvisioning;
import android.security.remoteprovisioning.ImplInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.remoteprovisioner.CborUtils;
import com.android.remoteprovisioner.GeekResponse;
import com.android.remoteprovisioner.ProvisionerMetrics;
import com.android.remoteprovisioner.RemoteProvisioningException;
import com.android.remoteprovisioner.ServerInterface;
import com.android.remoteprovisioner.SettingsManager;
import com.android.remoteprovisioner.SystemInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

@RunWith(AndroidJUnit4.class)
public class KeyRegisteredTest {

    private static final String SERVICE = "android.security.remoteprovisioning";

    private static Context sContext;
    private static IRemoteProvisioning sBinder;
    private static int sCurve;

    @BeforeClass
    public static void init() throws Exception {
        sContext = ApplicationProvider.getApplicationContext();
        sBinder =
              IRemoteProvisioning.Stub.asInterface(ServiceManager.getService(SERVICE));
        assertNotNull(sBinder);
        ImplInfo[] info = sBinder.getImplementationInfo();
        for (int i = 0; i < info.length; i++) {
            if (info[i].secLevel == TRUSTED_ENVIRONMENT) {
                sCurve = info[i].supportedCurve;
                break;
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        sBinder.deleteAllKeys();
    }

    @After
    public void tearDown() throws Exception {
        sBinder.deleteAllKeys();
    }

    private void requestCerts(int numKeys, int secLevel, byte[] geekChain, byte[] challenge,
                              IRemoteProvisioning binder, Context context,
                              ProvisionerMetrics metrics) throws Exception {
        DeviceInfo deviceInfo = new DeviceInfo();
        ProtectedData protectedData = new ProtectedData();
        byte[] macedKeysToSign = SystemInterface.generateCsr(SettingsManager.isTestMode(), numKeys,
                secLevel, geekChain, challenge, protectedData, deviceInfo, binder, metrics);
        String fingerprint = Build.FINGERPRINT;
        // The backend should provision test certs if the build isn't a user build.
        // Registration status only factors into user builds, so set a debug property that
        // will instruct the underlying provisioning code to appear as a user build to the
        // backend if it isn't.
        if (!Build.TYPE.equals("user")) {
            fingerprint = fingerprint.replace("userdebug", "user");
            fingerprint = fingerprint.replace("eng", "user");
        }
        Map unverifiedDeviceInfo = new Map();
        unverifiedDeviceInfo.put(new UnicodeString("fingerprint"),
                                 new UnicodeString(fingerprint));
        byte[] certificateRequest =
                CborUtils.buildCertificateRequest(deviceInfo.deviceInfo,
                                                  challenge,
                                                  protectedData.protectedData,
                                                  macedKeysToSign,
                                                  unverifiedDeviceInfo);
        List<byte[]> certChains = ServerInterface.requestSignedCertificates(context,
                        certificateRequest, challenge, metrics);
    }

    @Test
    public void testKeyRegisteredTee() throws Exception {
        try {
            ProvisionerMetrics metrics = ProvisionerMetrics.createScheduledAttemptMetrics(sContext);
            int numTestKeys = 1;
            sBinder.generateKeyPair(SettingsManager.isTestMode(), TRUSTED_ENVIRONMENT);
            GeekResponse geek = ServerInterface.fetchGeek(sContext, metrics);
            assertNotNull(geek);
            requestCerts(numTestKeys, TRUSTED_ENVIRONMENT, geek.getGeekChain(sCurve),
                         geek.getChallenge(), sBinder, sContext, metrics);
        } catch (RemoteProvisioningException e) {
            // Any exception will be a failure here, but specifically call out DEVICE_NOT_REGISTERED
            // as a registration failure before throwing whatever other problem may have occurred.
            assertNotEquals("Device isn't registered.",
                    Status.DEVICE_NOT_REGISTERED, e.getErrorCode());
            throw e;
        }
    }
}
