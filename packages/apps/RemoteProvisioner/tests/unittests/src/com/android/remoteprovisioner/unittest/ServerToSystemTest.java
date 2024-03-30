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

package com.android.remoteprovisioner.unittest;

import static android.hardware.security.keymint.SecurityLevel.TRUSTED_ENVIRONMENT;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static android.security.keystore.KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.ActivityThread;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.security.GenerateRkpKey;
import android.security.KeyStoreException;
import android.security.NetworkSecurityPolicy;
import android.security.keystore.KeyGenParameterSpec;
import android.security.remoteprovisioning.AttestationPoolStatus;
import android.security.remoteprovisioning.IRemoteProvisioning;
import android.security.remoteprovisioning.ImplInfo;
import android.system.keystore2.ResponseCode;
import android.util.Base64;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.remoteprovisioner.GeekResponse;
import com.android.remoteprovisioner.PeriodicProvisioner;
import com.android.remoteprovisioner.Provisioner;
import com.android.remoteprovisioner.ProvisionerMetrics;
import com.android.remoteprovisioner.RemoteProvisioningException;
import com.android.remoteprovisioner.ServerInterface;
import com.android.remoteprovisioner.SettingsManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.ProviderException;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;

import fi.iki.elonen.NanoHTTPD;

@RunWith(AndroidJUnit4.class)
public class ServerToSystemTest {

    private static final String Tag = "ServerToSystemTest";
    private static final boolean IS_TEST_MODE = false;
    private static final String SERVICE = "android.security.remoteprovisioning";
    private static final String RKP_ONLY_PROP = "remote_provisioning.tee.rkp_only";

    private static final byte[] GEEK_RESPONSE = Base64.decode(
            "g4KCAYOEQ6EBJqBYTaUBAgMmIAEhWCD3FIrbl/TMU+/SZBHE43UfZh+kcQxsz/oJRoB0h1TyrSJY"
                    + "IF5/W/bs5PYZzP8TN/0PociT2xgGdsRd5tdqd4bDLa+PWEAvl45C+74HLZVHhUeTQLAf1JtHpMRE"
                    + "qfKhB4cQx5/LEfS/n+g74Oc0TBX8e8N+MwX00TQ87QIEYHoV4HnTiv8khEOhASagWE2lAQIDJiAB"
                    + "IVggUYCsz4+WjOwPUOGpG7eQhjSL48OsZQJNtPYxDghGMjkiWCBU65Sd/ra05HM6JU4vH52dvfpm"
                    + "wRGL6ZaMQ+Qw9tp2q1hAmDj7NDpl23OYsSeiFXTyvgbnjSJO3fC/wgF0xLcpayQctdjSZvpE7/Uw"
                    + "LAR07ejGYNrOn1ZXJ3Qh096Tj+O4zYRDoQEmoFhxpgECAlggg5/4/RAcEp+SQcdbjeRO9BkTmscb"
                    + "bacOlfJkU12nHcEDOBggASFYIBakUhJjs4ZWUNjf8qCofbzZbqdoYOqMXPGT5ZcZDazeIlggib7M"
                    + "bD9esDk0r5e6ONEWHaHMHWTTjEhO+HKBGzs+Me5YQPrazy2rpTAMc8Xlq0mSWWBE+sTyM+UEsmwZ"
                    + "ZOkc42Q7NIYAZS313a+qAcmvg8lO+FqU6GWTUeMYHjmAp2lLM82CAoOEQ6EBJ6BYKqQBAQMnIAYh"
                    + "WCCZue7dXuRS9oXGTGLcPmGrV0h9dTcprXaAMtKzy2NY2VhAHiIIS6S3pMjXTgMO/rivFEynO2+l"
                    + "zdzaecYrZP6ZOa9254D6ZgCFDQeYKqyRXKclFEkGNHXKiid62eNaSesCA4RDoQEnoFgqpAEBAycg"
                    + "BiFYIOovhQ6eagxc973Z+igyv9pV6SCiUQPJA5MYzqAVKezRWECCa8ddpjZXt8dxEq0cwmqzLCMq"
                    + "3RQwy4IUtonF0x4xu7hQIUpJTbqRDG8zTYO8WCsuhNvFWQ+YYeLB6ony0K4EhEOhASegWE6lAQEC"
                    + "WCBvktEEbXHYp46I2NFWgV+W0XiD5jAbh+2/INFKO/5qLgM4GCAEIVggtl0cS5qDOp21FVk3oSb7"
                    + "D9/nnKwB1aTsyDopAIhYJTlYQICyn9Aynp1K/rAl8sLSImhGxiCwqugWrGShRYObzElUJX+rFgVT"
                    + "8L01k/PGu1lOXvneIQcUo7ako4uPgpaWugNYHQAAAYBINcxrASC0rWP9VTSO7LdABvcdkv7W2vh+"
                    + "onV0aW1lX3RvX3JlZnJlc2hfaG91cnMYSHgabnVtX2V4dHJhX2F0dGVzdGF0aW9uX2tleXMU",
            Base64.DEFAULT);

    // Same as GEEK_RESPONSE, but the "num_extra_attestation_keys" value is 0, disabling RKP.
    private static final byte[] GEEK_RESPONSE_RKP_DISABLED = Base64.decode(
            "g4KCAYOEQ6EBJqBYTaUBAgMmIAEhWCD3FIrbl/TMU+/SZBHE43UfZh+kcQxsz/oJRoB0h1TyrSJY"
                    + "IF5/W/bs5PYZzP8TN/0PociT2xgGdsRd5tdqd4bDLa+PWEAvl45C+74HLZVHhUeTQLAf1JtHpMRE"
                    + "qfKhB4cQx5/LEfS/n+g74Oc0TBX8e8N+MwX00TQ87QIEYHoV4HnTiv8khEOhASagWE2lAQIDJiAB"
                    + "IVggUYCsz4+WjOwPUOGpG7eQhjSL48OsZQJNtPYxDghGMjkiWCBU65Sd/ra05HM6JU4vH52dvfpm"
                    + "wRGL6ZaMQ+Qw9tp2q1hAmDj7NDpl23OYsSeiFXTyvgbnjSJO3fC/wgF0xLcpayQctdjSZvpE7/Uw"
                    + "LAR07ejGYNrOn1ZXJ3Qh096Tj+O4zYRDoQEmoFhxpgECAlggg5/4/RAcEp+SQcdbjeRO9BkTmscb"
                    + "bacOlfJkU12nHcEDOBggASFYIBakUhJjs4ZWUNjf8qCofbzZbqdoYOqMXPGT5ZcZDazeIlggib7M"
                    + "bD9esDk0r5e6ONEWHaHMHWTTjEhO+HKBGzs+Me5YQPrazy2rpTAMc8Xlq0mSWWBE+sTyM+UEsmwZ"
                    + "ZOkc42Q7NIYAZS313a+qAcmvg8lO+FqU6GWTUeMYHjmAp2lLM82CAoOEQ6EBJ6BYKqQBAQMnIAYh"
                    + "WCCZue7dXuRS9oXGTGLcPmGrV0h9dTcprXaAMtKzy2NY2VhAHiIIS6S3pMjXTgMO/rivFEynO2+l"
                    + "zdzaecYrZP6ZOa9254D6ZgCFDQeYKqyRXKclFEkGNHXKiid62eNaSesCA4RDoQEnoFgqpAEBAycg"
                    + "BiFYIOovhQ6eagxc973Z+igyv9pV6SCiUQPJA5MYzqAVKezRWECCa8ddpjZXt8dxEq0cwmqzLCMq"
                    + "3RQwy4IUtonF0x4xu7hQIUpJTbqRDG8zTYO8WCsuhNvFWQ+YYeLB6ony0K4EhEOhASegWE6lAQEC"
                    + "WCBvktEEbXHYp46I2NFWgV+W0XiD5jAbh+2/INFKO/5qLgM4GCAEIVggtl0cS5qDOp21FVk3oSb7"
                    + "D9/nnKwB1aTsyDopAIhYJTlYQICyn9Aynp1K/rAl8sLSImhGxiCwqugWrGShRYObzElUJX+rFgVT"
                    + "8L01k/PGu1lOXvneIQcUo7ako4uPgpaWugNYHQAAAYBINcxrASC0rWP9VTSO7LdABvcdkv7W2vh+"
                    + "onV0aW1lX3RvX3JlZnJlc2hfaG91cnMYSHgabnVtX2V4dHJhX2F0dGVzdGF0aW9uX2tleXMA",
            Base64.DEFAULT);

    private static Context sContext;
    private static IRemoteProvisioning sBinder;
    private static int sCurve = 0;

    private Duration mDuration;

    // Helper class that sets rkp_only to true if it's not already set, then restores the state on
    // close. Intended to be used in a try expression: try (RkpOnlyContext c = new RkpOnlyContext())
    private static class ForceRkpOnlyContext implements AutoCloseable {
        private final boolean mOriginalPropertyValue;

        ForceRkpOnlyContext() {
            mOriginalPropertyValue = SystemProperties.getBoolean(RKP_ONLY_PROP, false);
            if (!mOriginalPropertyValue) {
                SystemProperties.set(RKP_ONLY_PROP, "true");
            }
        }

        @Override
        public void close() {
            if (!mOriginalPropertyValue) {
                SystemProperties.set(RKP_ONLY_PROP, "false");
            }
        }
    }

    private void assertPoolStatus(int total, int attested,
                                  int unassigned, int expiring, Duration time) throws Exception {
        AttestationPoolStatus pool = sBinder.getPoolStatus(time.toMillis(), TRUSTED_ENVIRONMENT);
        assertEquals(total, pool.total);
        assertEquals(attested, pool.attested);
        assertEquals(unassigned, pool.unassigned);
        assertEquals(expiring, pool.expiring);
    }

    private static Certificate[] generateKeyStoreKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias);
        }
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM_EC,
                "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN)
                .setAttestationChallenge("challenge".getBytes())
                .build();
        keyPairGenerator.initialize(spec);
        keyPairGenerator.generateKeyPair();
        Certificate[] certs = keyStore.getCertificateChain(spec.getKeystoreAlias());
        keyStore.deleteEntry(alias);
        return certs;
    }

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
        SettingsManager.clearPreferences(sContext);
        sBinder.deleteAllKeys();
        mDuration = Duration.ofMillis(System.currentTimeMillis());
    }

    @After
    public void tearDown() throws Exception {
        SettingsManager.clearPreferences(sContext);
        sBinder.deleteAllKeys();
    }

    @Test
    public void testFullRoundTrip() throws Exception {
        ProvisionerMetrics metrics = ProvisionerMetrics.createScheduledAttemptMetrics(sContext);
        int numTestKeys = 1;
        assertPoolStatus(0, 0, 0, 0, mDuration);
        sBinder.generateKeyPair(IS_TEST_MODE, TRUSTED_ENVIRONMENT);
        assertPoolStatus(numTestKeys, 0, 0, 0, mDuration);
        GeekResponse geek = ServerInterface.fetchGeek(sContext, metrics);
        assertEquals(0, SettingsManager.getErrDataBudgetConsumed(sContext));
        assertNotNull(geek);
        int numProvisioned =
                Provisioner.provisionCerts(numTestKeys, TRUSTED_ENVIRONMENT,
                                           geek.getGeekChain(sCurve), geek.getChallenge(), sBinder,
                                           sContext, metrics);
        assertEquals(0, SettingsManager.getErrDataBudgetConsumed(sContext));
        assertEquals(numTestKeys, numProvisioned);
        assertPoolStatus(numTestKeys, numTestKeys, numTestKeys, 0, mDuration);
        // Certificate duration sent back from the server may change, however ~6 months should be
        // pretty safe.
        assertPoolStatus(numTestKeys, numTestKeys, numTestKeys,
                         numTestKeys, mDuration.plusDays(180));
    }

    @Test
    public void testPeriodicProvisionerRoundTrip() throws Exception {
        PeriodicProvisioner provisioner = TestWorkerBuilder.from(
                sContext,
                PeriodicProvisioner.class,
                Executors.newSingleThreadExecutor()).build();
        assertEquals(provisioner.doWork(), ListenableWorker.Result.success());
        AttestationPoolStatus pool = sBinder.getPoolStatus(mDuration.toMillis(),
                TRUSTED_ENVIRONMENT);
        assertTrue("Pool must not be empty", pool.total > 0);
        assertEquals("All keys must be attested", pool.total, pool.attested);
        assertEquals("Nobody should have consumed keys yet", pool.total, pool.unassigned);
        assertEquals("All keys should be freshly generated", 0, pool.expiring);
    }

    @Test
    public void testPeriodicProvisionerNoop() throws Exception {
        // Similar to the PeriodicProvisioner round trip, except first we actually populate the
        // key pool to ensure that the PeriodicProvisioner just noops.
        PeriodicProvisioner provisioner = TestWorkerBuilder.from(
                sContext,
                PeriodicProvisioner.class,
                Executors.newSingleThreadExecutor()).build();
        assertEquals(provisioner.doWork(), ListenableWorker.Result.success());
        final AttestationPoolStatus pool = sBinder.getPoolStatus(mDuration.toMillis(),
                TRUSTED_ENVIRONMENT);
        assertTrue("Pool must not be empty", pool.total > 0);
        assertEquals("All keys must be attested", pool.total, pool.attested);
        assertEquals("Nobody should have consumed keys yet", pool.total, pool.unassigned);
        assertEquals("All keys should be freshly generated", 0, pool.expiring);

        // The metrics host test will perform additional validation by ensuring correct metrics
        // are recorded.
        assertEquals(provisioner.doWork(), ListenableWorker.Result.success());
        assertPoolStatus(pool.total, pool.attested, pool.unassigned, pool.expiring, mDuration);
    }

    @Test
    public void testPeriodicProvisionerDataBudgetEmpty() throws Exception {
        // Check the data budget in order to initialize a rolling window.
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null /* curTime */));
        SettingsManager.consumeErrDataBudget(sContext, SettingsManager.FAILURE_DATA_USAGE_MAX);

        PeriodicProvisioner provisioner = TestWorkerBuilder.from(
                sContext,
                PeriodicProvisioner.class,
                Executors.newSingleThreadExecutor()).build();
        assertEquals(provisioner.doWork(), ListenableWorker.Result.failure());
        AttestationPoolStatus pool = sBinder.getPoolStatus(mDuration.toMillis(),
                TRUSTED_ENVIRONMENT);
        assertTrue("Keys should have been generated", pool.total > 0);
        assertEquals("No keys should be attested", 0, pool.attested);
        assertEquals("No keys should have been assigned", 0, pool.unassigned);
        assertEquals("No keys can possibly be expiring yet", 0, pool.expiring);
    }

    @Test
    public void testPeriodicProvisionerProvisioningDisabled() throws Exception {
        // We need to run an HTTP server that returns a config indicating no keys are needed
        final NanoHTTPD server = new NanoHTTPD("localhost", 0) {
            @Override
            public Response serve(IHTTPSession session) {
                consumeRequestBody((HTTPSession) session);
                if (session.getUri().contains(":fetchEekChain")) {
                    return newFixedLengthResponse(Response.Status.OK, "application/cbor",
                            new ByteArrayInputStream(GEEK_RESPONSE_RKP_DISABLED),
                            GEEK_RESPONSE_RKP_DISABLED.length);
                }
                Assert.fail("Unexpected HTTP request: " + session.getUri());
                return null;
            }

            void consumeRequestBody(HTTPSession session) {
                try {
                    session.getInputStream().readNBytes((int) session.getBodySize());
                } catch (IOException e) {
                    Assert.fail("Error reading request bytes: " + e.toString());
                }
            }
        };
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

        final boolean cleartextPolicy =
                NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted();
        NetworkSecurityPolicy.getInstance().setCleartextTrafficPermitted(true);
        SettingsManager.setDeviceConfig(sContext, 1 /* extraKeys */, mDuration /* expiringBy */,
                "http://localhost:" + server.getListeningPort() + "/");

        PeriodicProvisioner provisioner = TestWorkerBuilder.from(
                sContext,
                PeriodicProvisioner.class,
                Executors.newSingleThreadExecutor()).build();
        assertEquals(provisioner.doWork(), ListenableWorker.Result.success());
        assertPoolStatus(0, 0, 0, 0, mDuration);
    }

    @Test
    public void testFallback() throws Exception {
        ProvisionerMetrics metrics = ProvisionerMetrics.createScheduledAttemptMetrics(sContext);
        Assume.assumeFalse(
                "Skipping test as this system does not support fallback from RKP keys",
                SystemProperties.getBoolean(RKP_ONLY_PROP, false));

        // Feed a fake URL into the device config to ensure that remote provisioning fails.
        SettingsManager.setDeviceConfig(sContext, 1 /* extraKeys */, mDuration /* expiringBy */,
                                        "Not even a URL" /* url */);
        int numTestKeys = 1;
        assertPoolStatus(0, 0, 0, 0, mDuration);
        // Note that due to the GenerateRkpKeyService, this call to generate an attested key will
        // still cause the service to generate keys up the number specified as `extraKeys` in the
        // `setDeviceConfig`. This will provide us 1 key for the followup call to provisionCerts.
        Certificate[] fallbackKeyCerts1 = generateKeyStoreKey("test1");

        SettingsManager.clearPreferences(sContext);
        GeekResponse geek = ServerInterface.fetchGeek(sContext, metrics);
        int numProvisioned =
                Provisioner.provisionCerts(numTestKeys, TRUSTED_ENVIRONMENT,
                                           geek.getGeekChain(sCurve), geek.getChallenge(), sBinder,
                                           sContext, metrics);
        assertEquals(numTestKeys, numProvisioned);
        assertPoolStatus(numTestKeys, numTestKeys, numTestKeys, 0, mDuration);
        Certificate[] provisionedKeyCerts = generateKeyStoreKey("test2");
        sBinder.deleteAllKeys();
        sBinder.generateKeyPair(IS_TEST_MODE, TRUSTED_ENVIRONMENT);

        SettingsManager.setDeviceConfig(sContext, 2 /* extraKeys */, mDuration /* expiringBy */,
                                        "Not even a URL" /* url */);
        // Even if there is an unsigned key hanging around, fallback should still occur.
        Certificate[] fallbackKeyCerts2 = generateKeyStoreKey("test3");
        assertTrue(fallbackKeyCerts1.length == fallbackKeyCerts2.length);
        for (int i = 1; i < fallbackKeyCerts1.length; i++) {
            assertArrayEquals("Cert: " + i, fallbackKeyCerts1[i].getEncoded(),
                              fallbackKeyCerts2[i].getEncoded());
        }
        assertTrue(provisionedKeyCerts.length > 0);
        // Match against the batch provisioned key, which should be the second entry in the array.
        assertFalse("Provisioned and fallback attestation key intermediate certificates match.",
                    Arrays.equals(fallbackKeyCerts1[1].getEncoded(),
                              provisionedKeyCerts[1].getEncoded()));
    }

    @Test
    public void testDataBudgetEmptyFetchGeek() throws Exception {
        // Check the data budget in order to initialize a rolling window.
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null /* curTime */));
        SettingsManager.consumeErrDataBudget(sContext, SettingsManager.FAILURE_DATA_USAGE_MAX);
        ProvisionerMetrics metrics = ProvisionerMetrics.createScheduledAttemptMetrics(sContext);
        try {
            ServerInterface.fetchGeek(sContext, metrics);
            fail("Network transaction should not have proceeded.");
        } catch (RemoteProvisioningException e) {
            return;
        }
    }

    @Test
    public void testDataBudgetEmptySignCerts() throws Exception {
        // Check the data budget in order to initialize a rolling window.
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null /* curTime */));
        SettingsManager.consumeErrDataBudget(sContext, SettingsManager.FAILURE_DATA_USAGE_MAX);
        ProvisionerMetrics metrics = ProvisionerMetrics.createScheduledAttemptMetrics(sContext);
        try {
            ServerInterface.requestSignedCertificates(sContext, null, null, metrics);
            fail("Network transaction should not have proceeded.");
        } catch (RemoteProvisioningException e) {
            return;
        }
    }

    @Test
    public void testDataBudgetEmptyCallGenerateRkpKeyService() throws Exception {
        // Check the data budget in order to initialize a rolling window.
        assertTrue(SettingsManager.hasErrDataBudget(sContext, null /* curTime */));
        SettingsManager.consumeErrDataBudget(sContext, SettingsManager.FAILURE_DATA_USAGE_MAX);
        GenerateRkpKey keyGen = new GenerateRkpKey(ActivityThread.currentApplication());
        keyGen.notifyKeyGenerated(SECURITY_LEVEL_TRUSTED_ENVIRONMENT);
        // Nothing to check here. This test is primarily used by the Metrics host test to
        // validate that correct metrics are logged.
    }

    @Test
    public void testGenerateKeyRkpOnly() throws Exception {
        try (ForceRkpOnlyContext c = new ForceRkpOnlyContext()) {
            Certificate[] certs = generateKeyStoreKey("this-better-work");
            assertTrue(certs.length > 0);
        }
    }

    @Test
    public void testRetryableRkpError() throws Exception {
        try (ForceRkpOnlyContext c = new ForceRkpOnlyContext()) {
            SettingsManager.setDeviceConfig(sContext, 1 /* extraKeys */, mDuration /* expiringBy */,
                    "Not even a URL" /* url */);
            generateKeyStoreKey("should-never-succeed");
            Assert.fail("Expected a keystore exception");
        } catch (ProviderException e) {
            Assert.assertTrue(e.getCause() instanceof KeyStoreException);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            Assert.assertEquals(ResponseCode.OUT_OF_KEYS, keyStoreException.getErrorCode());
            Assert.assertTrue(keyStoreException.isTransientFailure());
            Assert.assertEquals(KeyStoreException.RETRY_WITH_EXPONENTIAL_BACKOFF,
                    keyStoreException.getRetryPolicy());
        }
    }

    private void setAirplaneMode(boolean enable) throws Exception {
        ConnectivityManager cm = sContext.getSystemService(ConnectivityManager.class);
        try (PermissionContext c = TestApis.permissions().withPermission(
                Manifest.permission.NETWORK_SETTINGS)) {
            cm.setAirplaneMode(enable);

            // Now wait a "reasonable" time for the network to go down
            for (int i = 0; i < 100; ++i) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                Log.e(Tag, "Checking active network... " + networkInfo);
                if (enable) {
                    if (networkInfo == null || !networkInfo.isConnected()) {
                        Log.e(Tag, "Successfully disconnected from to the network.");
                        return;
                    }
                } else if (networkInfo != null && networkInfo.isConnected()) {
                    Log.e(Tag, "Successfully reconnected to the network.");
                    return;
                }
                Thread.sleep(300);
            }
        }
        Assert.fail("Failed to successfully " + (enable ? "enable" : "disable") + " airplane mode");
    }

    @Test
    public void testRetryWithoutNetworkTee() throws Exception {
        setAirplaneMode(true);
        try (ForceRkpOnlyContext c = new ForceRkpOnlyContext()) {
            assertPoolStatus(0, 0, 0, 0, mDuration);
            generateKeyStoreKey("should-never-succeed");
            Assert.fail("Expected a keystore exception");
        } catch (ProviderException e) {
            Assert.assertTrue(e.getCause() instanceof KeyStoreException);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            Assert.assertEquals(ResponseCode.OUT_OF_KEYS, keyStoreException.getErrorCode());
            Assert.assertTrue(keyStoreException.isTransientFailure());
            Assert.assertEquals(KeyStoreException.RETRY_WHEN_CONNECTIVITY_AVAILABLE,
                    keyStoreException.getRetryPolicy());
        } finally {
            setAirplaneMode(false);
        }
    }

    @Test
    public void testRetryNeverWhenDeviceNotRegistered() throws Exception {
        final NanoHTTPD server = new NanoHTTPD("localhost", 0) {
            @Override
            public Response serve(IHTTPSession session) {
                // We must consume all bytes in the request, else they get interpreted as a
                // sepearate (bad) request by the HTTP server.
                consumeRequestBody((HTTPSession) session);
                if (session.getUri().contains(":fetchEekChain")) {
                    return newFixedLengthResponse(Response.Status.OK, "application/cbor",
                            new ByteArrayInputStream(GEEK_RESPONSE), GEEK_RESPONSE.length);
                } else if (session.getUri().contains(":signCertificates")) {
                    Response.IStatus status = new Response.IStatus() {
                        @Override
                        public String getDescription() {
                            return "444 Device Not Registered";
                        }

                        @Override
                        public int getRequestStatus() {
                            return 444;
                        }
                    };
                    return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT,
                            "device not registered");
                }
                Assert.fail("Unexpected HTTP request: " + session.getUri());
                return null;
            }

            void consumeRequestBody(HTTPSession session) {
                try {
                    session.getInputStream().readNBytes((int) session.getBodySize());
                } catch (IOException e) {
                    Assert.fail("Error reading request bytes: " + e.toString());
                }
            }
        };
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

        final boolean cleartextPolicy =
                NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted();
        NetworkSecurityPolicy.getInstance().setCleartextTrafficPermitted(true);
        SettingsManager.setDeviceConfig(sContext, 1 /* extraKeys */, mDuration /* expiringBy */,
                "http://localhost:" + server.getListeningPort() + "/");

        try (ForceRkpOnlyContext c = new ForceRkpOnlyContext()) {
            assertPoolStatus(0, 0, 0, 0, mDuration);
            generateKeyStoreKey("should-never-succeed");
            Assert.fail("Expected a keystore exception");
        } catch (ProviderException e) {
            Assert.assertTrue(e.getCause() instanceof KeyStoreException);
            KeyStoreException keyStoreException = (KeyStoreException) e.getCause();
            Assert.assertEquals(ResponseCode.OUT_OF_KEYS, keyStoreException.getErrorCode());
            Assert.assertFalse(keyStoreException.isTransientFailure());
            Assert.assertEquals(KeyStoreException.RETRY_NEVER, keyStoreException.getRetryPolicy());
        } finally {
            NetworkSecurityPolicy.getInstance().setCleartextTrafficPermitted(cleartextPolicy);
            SettingsManager.clearPreferences(sContext);
            server.stop();
        }
    }
}
