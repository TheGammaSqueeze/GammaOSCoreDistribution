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

package android.server.biometrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.platform.test.annotations.Presubmit;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;

import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple tests.
 */
@Presubmit
public class BiometricSimpleTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Simple";

    /**
     * Tests that enrollments created via {@link BiometricTestSession} show up in the
     * biometric dumpsys.
     */
    @Test
    public void testEnroll() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId())){
                enrollForSensor(session, prop.getSensorId());
            }
        }
    }

    /**
     * Tests that the sensorIds retrieved via {@link BiometricManager#getSensorProperties()} and
     * the dumpsys are consistent with each other.
     */
    @Test
    public void testSensorPropertiesAndDumpsysMatch() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final BiometricServiceState state = getCurrentState();

        assertEquals(mSensorProperties.size(), state.mSensorStates.sensorStates.size());
        for (SensorProperties prop : mSensorProperties) {
            assertTrue(state.mSensorStates.sensorStates.containsKey(prop.getSensorId()));
        }
    }

    /**
     * Tests that the PackageManager features and biometric dumpsys are consistent with each other.
     */
    @Test
    public void testPackageManagerAndDumpsysMatch() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final BiometricServiceState state = getCurrentState();
        final PackageManager pm = mContext.getPackageManager();
        if (mSensorProperties.isEmpty()) {
            assertTrue(state.mSensorStates.sensorStates.isEmpty());

            final File initGsiRc = new File("/system/system_ext/etc/init/init.gsi.rc");
            if (!initGsiRc.exists()) {
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT));
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_FACE));
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_IRIS));
            }

            assertTrue(state.mSensorStates.sensorStates.isEmpty());
        } else {
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                    state.mSensorStates.containsModality(SensorStateProto.FINGERPRINT));
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FACE),
                    state.mSensorStates.containsModality(SensorStateProto.FACE));
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_IRIS),
                    state.mSensorStates.containsModality(SensorStateProto.IRIS));
        }
    }

    @Test
    public void testCanAuthenticate_whenNoSensors() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        if (mSensorProperties.isEmpty()) {
            assertEquals(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK));
            assertEquals(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG));
        }
    }

    @Test
    public void testInvalidInputs() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (int i = 0; i < 32; i++) {
            final int authenticator = 1 << i;
            // If it's a public constant, no need to test
            if (Utils.isPublicAuthenticatorConstant(authenticator)) {
                continue;
            }

            // Test canAuthenticate(int)
            assertThrows("Invalid authenticator in canAuthenticate must throw exception: "
                            + authenticator,
                    Exception.class,
                    () -> mBiometricManager.canAuthenticate(authenticator));

            // Test BiometricPrompt
            assertThrows("Invalid authenticator in authenticate must throw exception: "
                            + authenticator,
                    Exception.class,
                    () -> showBiometricPromptWithAuthenticators(authenticator));
        }
    }

    /**
     * When device credential is not enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2) BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 3) @deprecated BiometricPrompt#setDeviceCredentialAllowed(true)
     */
    @Test
    public void testWhenCredentialNotEnrolled() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        // First case above
        final int result = mBiometricManager.canAuthenticate(BiometricManager
                .Authenticators.DEVICE_CREDENTIAL);
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, result);

        // Second case above
        BiometricPrompt.AuthenticationCallback callback =
                mock(BiometricPrompt.AuthenticationCallback.class);
        showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL),
                any());

        // Third case above. Since the deprecated API is intended to allow credential in addition
        // to biometrics, we should be receiving BIOMETRIC_ERROR_NO_BIOMETRICS.
        final boolean noSensors = mSensorProperties.isEmpty();
        int expectedError;
        if (noSensors) {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL;
        } else if (hasOnlyConvenienceSensors()) {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT;
        } else {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS;
        }
        callback = mock(BiometricPrompt.AuthenticationCallback.class);
        showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(expectedError),
                any());
    }

    private boolean hasOnlyConvenienceSensors() {
        for (SensorProperties sensor : mSensorProperties) {
            if (sensor.getSensorStrength() != SensorProperties.STRENGTH_CONVENIENCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * When device credential is enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2a) Successfully authenticating BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 2b) Cancelling authentication for the above
     * 3a) @deprecated BiometricPrompt#setDeviceCredentialALlowed(true)
     * 3b) Cancelling authentication for the above
     * 4) Cancelling auth for options 2) and 3)
     */
    @Test
    public void testWhenCredentialEnrolled() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            // First case above
            final int result = mBiometricManager.canAuthenticate(BiometricManager
                    .Authenticators.DEVICE_CREDENTIAL);
            assertEquals(BiometricManager.BIOMETRIC_SUCCESS, result);

            // 2a above
            BiometricPrompt.AuthenticationCallback callback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 2b above
            CancellationSignal cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());

            // 3a above
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 3b above
            cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());
        }
    }

    @Test
    public void testSimpleBiometricAuth_convenience() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() != SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth_convenience, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                // Let's just try to check+auth against WEAK, since CONVENIENCE isn't even
                // exposed to public BiometricPrompt APIs (as intended).
                final int authenticatorStrength = Authenticators.BIOMETRIC_WEAK;
                assertNotEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                enrollForSensor(session, props.getSensorId());

                assertNotEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPrompt(props.getSensorId(), 0 /* userId */,
                        true /* requireConfirmation */, callback, new CancellationSignal());

                verify(callback).onAuthenticationError(anyInt(), anyObject());
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     *
     * TODO(b/236763921): fix this test and unignore.
     */
    @Ignore
    @Test
    public void testSimpleBiometricAuth_nonConvenience() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                enrollForSensor(session, props.getSensorId());

                assertEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                final Random random = new Random();
                final String randomTitle = String.valueOf(random.nextInt(10000));
                final String randomSubtitle = String.valueOf(random.nextInt(10000));
                final String randomDescription = String.valueOf(random.nextInt(10000));
                final String randomNegativeButtonText = String.valueOf(random.nextInt(10000));

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPromptWithContents(props.getSensorId(), 0 /* userId */,
                        true /* requireConfirmation */, callback, randomTitle, randomSubtitle,
                        randomDescription, randomNegativeButtonText);

                final UiObject2 actualTitle = findView(TITLE_VIEW);
                final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
                final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
                final UiObject2 actualNegativeButton = findView(BUTTON_ID_NEGATIVE);
                assertEquals(randomTitle, actualTitle.getText());
                assertEquals(randomSubtitle, actualSubtitle.getText());
                assertEquals(randomDescription, actualDescription.getText());
                assertEquals(randomNegativeButtonText, actualNegativeButton.getText());

                // Finish auth
                successfullyAuthenticate(session, 0 /* userId */);

                ArgumentCaptor<BiometricPrompt.AuthenticationResult> resultCaptor =
                        ArgumentCaptor.forClass(BiometricPrompt.AuthenticationResult.class);
                verify(callback).onAuthenticationSucceeded(resultCaptor.capture());
                assertEquals("Must be TYPE_BIOMETRIC",
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC,
                        resultCaptor.getValue().getAuthenticationType());
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when credential auth is requested.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @Test
    public void testSimpleCredentialAuth() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        try (CredentialSession session = new CredentialSession()){
            session.setCredential();

            final Random random = new Random();
            final String randomTitle = String.valueOf(random.nextInt(10000));
            final String randomSubtitle = String.valueOf(random.nextInt(10000));
            final String randomDescription = String.valueOf(random.nextInt(10000));

            CountDownLatch latch = new CountDownLatch(1);
            BiometricPrompt.AuthenticationCallback callback =
                    new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(
                        BiometricPrompt.AuthenticationResult result) {
                    assertEquals("Must be TYPE_CREDENTIAL",
                            BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL,
                            result.getAuthenticationType());
                    latch.countDown();
                }
            };
            showCredentialOnlyBiometricPromptWithContents(callback, new CancellationSignal(),
                    true /* shouldShow */, randomTitle, randomSubtitle, randomDescription);

            final UiObject2 actualTitle = findView(TITLE_VIEW);
            final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
            final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
            assertEquals(randomTitle, actualTitle.getText());
            assertEquals(randomSubtitle, actualSubtitle.getText());
            assertEquals(randomDescription, actualDescription.getText());

            // Finish auth
            successfullyEnterCredential();
            latch.await(3, TimeUnit.SECONDS);
        }
    }

    /**
     * Tests that cancelling auth succeeds, and that ERROR_CANCELED is received.
     */
    @Test
    public void testBiometricCancellation() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {
                enrollForSensor(session, props.getSensorId());

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                CancellationSignal cancellationSignal = new CancellationSignal();

                showDefaultBiometricPrompt(props.getSensorId(), 0 /* userId */,
                        true /* requireConfirmation */, callback, cancellationSignal);

                cancelAuthentication(cancellationSignal);
                verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                        any());
                verifyNoMoreInteractions(callback);
            }
        }
    }
}
