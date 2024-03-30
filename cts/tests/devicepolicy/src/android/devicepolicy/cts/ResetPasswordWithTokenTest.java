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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.Context;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.ResetPasswordWithToken;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

// TODO(b/191640667): Parameterize the length limit tests with multiple limits
@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
public final class ResetPasswordWithTokenTest {

    private static final String NOT_COMPLEX_PASSWORD = "1234";
    private static final String VALID_PASSWORD = NOT_COMPLEX_PASSWORD;
    private static final String NUMERIC_PASSWORD_LENGTH_3 = "123";
    private static final String NUMERIC_PASSWORD_REPEATING_LENGTH_4 = "4444";
    private static final String NUMERIC_PASSWORD_RANDOM_LENGTH_4 = "3829";
    private static final String NUMERIC_PASSWORD_LENGTH_4 = NOT_COMPLEX_PASSWORD;
    private static final String NUMERIC_PASSWORD_LENGTH_6 = "264828";
    private static final String ALPHABETIC_PASSWORD_LENGTH_4 = "abcd";
    private static final String ALPHABETIC_PASSWORD_ALL_UPPERCASE_LENGTH_4 = "ABCD";
    private static final String ALPHANUMERIC_PASSWORD_LENGTH_4 = "12ab";
    private static final String ALPHANUMERIC_PASSWORD_WITH_UPPERCASE_LENGTH_4 = "abC1";
    private static final String ALPHANUMERIC_PASSWORD_LENGTH_8 = "1a2b3c4e";
    private static final String COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4 = "12a_";
    private static final String COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7 = "abc123.";

    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();
    private static final byte[] BAD_TOKEN = "abcdefghijklmnopqrstuvwxyz012345678*".getBytes();

    private static final String RESET_PASSWORD_TOKEN_DISABLED =
            "Cannot reset password token as it is disabled for the primary user";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private final KeyguardManager sLocalKeyguardManager =
            sContext.getSystemService(KeyguardManager.class);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void setResetPasswordToken_validToken_passwordTokenSet() {
        try {
            boolean possible = canSetResetPasswordToken(TOKEN);

            assertThat(sDeviceState.dpc().devicePolicyManager().isResetPasswordTokenActive(
                    DPC_COMPONENT_NAME) || !possible).isTrue();
        } finally {
            // Remove password token
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(DPC_COMPONENT_NAME);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, VALID_PASSWORD, TOKEN, /* flags = */ 0)).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_badToken_failure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, VALID_PASSWORD, BAD_TOKEN, /* flags = */ 0)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_noPassword_deviceIsNotSecure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, /* password = */ null, TOKEN, /* flags = */ 0);

        // Device is not secure when no password is set
        assertThat(sLocalKeyguardManager.isDeviceSecure()).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_password_deviceIsSecure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, VALID_PASSWORD, TOKEN, /* flags = */ 0);

            // Device is secure when a password is set
            assertThat(sLocalKeyguardManager.isDeviceSecure()).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void resetPasswordWithToken_passwordDoesNotSatisfyRestriction_failure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password cannot be set as it does not satisfy the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, NOT_COMPLEX_PASSWORD, TOKEN, /* flags = */ 0)).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void resetPasswordWithToken_passwordSatisfiesRestriction_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password can be set as it satisfies the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7, TOKEN,
                    /* flags = */ 0)).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_logged() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, VALID_PASSWORD, TOKEN, /* flags = */ 0);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.RESET_PASSWORD_WITH_TOKEN_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().componentName().getPackageName())).wasLogged();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordDoesNotSatisfyRestriction_false() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, NOT_COMPLEX_PASSWORD, TOKEN, /* flags = */ 0);
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password is insufficient because it does not satisfy the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordSatisfiesRestriction_true() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(DPC_COMPONENT_NAME,
                    COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7, TOKEN, /* flags = */ 0);
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password is sufficient because it satisfies the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordNoLongerSatisfiesRestriction_false() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(DPC_COMPONENT_NAME,
                    PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 1,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(DPC_COMPONENT_NAME,
                    COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7, TOKEN, /* flags = */ 0);
            // Set a slightly stronger password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                    DPC_COMPONENT_NAME, 2);

            // Password is no longer sufficient because it does not satisfy the new restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_SOMETHING);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordQuality(
                    DPC_COMPONENT_NAME)).isEqualTo(PASSWORD_QUALITY_SOMETHING);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_something_passwordWithAMinLengthOfFourRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_SOMETHING);

            assertPasswordSucceeds(NUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_3); // Password too short
            assertPasswordFails(/* password = */ null);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_numeric_passwordWithAtLeastOneNumberOrLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_NUMERIC);

            assertPasswordSucceeds(NUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_3); // Password too short
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_alphabetic_passwordWithAtLeastOneLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_ALPHABETIC);

            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_alphanumeric_passwordWithBothALetterAndANumberRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_ALPHANUMERIC);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_complex_passwordWithAMinLengthOfFourRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_3); // Password too short
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    // setPasswordMinimumLength is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLength_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumLength(DPC_COMPONENT_NAME, 4);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLength(DPC_COMPONENT_NAME)).isEqualTo(4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLength_six_passwordWithAMinLengthOfSixRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7);
            assertPasswordFails(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumUpperCase is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void setPasswordMinimumUpperCase_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumUpperCase(DPC_COMPONENT_NAME, 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumUpperCase(DPC_COMPONENT_NAME)).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumUpperCase_one_passwordWithAtLeastOneUpperCaseLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 1);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_WITH_UPPERCASE_LENGTH_4);
            assertPasswordFails(ALPHANUMERIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumLowerCase is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void setPasswordMinimumLowerCase_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumLowerCase(DPC_COMPONENT_NAME, 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLowerCase(DPC_COMPONENT_NAME)).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLowerCase_one_passwordWithAtLeaseOneLowerCaseLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 1,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_ALL_UPPERCASE_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void setPasswordMinimumLetters_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumLetters(DPC_COMPONENT_NAME, 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLetters(DPC_COMPONENT_NAME)).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLetters_one_passwordWithAtLeastOneLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 1,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void setPasswordMinimumNumeric_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumNumeric(DPC_COMPONENT_NAME, 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNumeric(DPC_COMPONENT_NAME)).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumNumeric_one_passwordWithAtLeastOneNumberRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 1,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void setPasswordMinimumSymbols_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumSymbols(DPC_COMPONENT_NAME, 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumSymbols(DPC_COMPONENT_NAME)).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumSymbols_one_passwordWithAtLeastOneSymbolRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 1,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
            assertPasswordFails(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumNonLetter is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void setPasswordMinimumNonLetter_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumNonLetter(DPC_COMPONENT_NAME, 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNonLetter(DPC_COMPONENT_NAME)).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumNonLetter_one_passwordWithAtLeastOneNonLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 1,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setRequiredPasswordComplexity_passwordQualityAlreadySet_clearsPasswordQuality() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    DPC_COMPONENT_NAME, PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordQuality(
                    DPC_COMPONENT_NAME)).isEqualTo(PASSWORD_QUALITY_UNSPECIFIED);
            assertThat(sDeviceState.dpc().devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_passwordComplexityAlreadySet_clearsPasswordComplexity() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(DPC_COMPONENT_NAME,
                    PASSWORD_QUALITY_COMPLEX);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordQuality(
                    DPC_COMPONENT_NAME)).isEqualTo(PASSWORD_QUALITY_COMPLEX);
            assertThat(sDeviceState.dpc().devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_NONE);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void setRequiredPasswordComplexity_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            assertThat(sDeviceState.dpc().devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void setRequiredPasswordComplexity_low_passwordThatMeetsLowPasswordBandRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_LOW);

            assertPasswordSucceeds(NUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_3); // Password too short
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void setRequiredPasswordComplexity_medium_passwordThatMeetsMediumPasswordBandRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(NUMERIC_PASSWORD_RANDOM_LENGTH_4);
            assertPasswordFails(NUMERIC_PASSWORD_REPEATING_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void setRequiredPasswordComplexity_high_passwordThatMeetsHighPasswordBandRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_HIGH);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_8);
            assertPasswordFails(NUMERIC_PASSWORD_LENGTH_6);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void getPasswordComplexity_passwordThatMeetsLowPasswordBand_lowPasswordComplexity() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Set password that meets low password band
            assertPasswordSucceeds(NUMERIC_PASSWORD_LENGTH_4);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_LOW);
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void getPasswordComplexity_passwordThatMeetsMediumPasswordBand_mediumPasswordComplexity() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Set password that meets medium password band
            assertPasswordSucceeds(NUMERIC_PASSWORD_LENGTH_6);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void getPasswordComplexity_passwordThatMeetsHighPasswordBand_highPasswordComplexity() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Set password that meets high password band
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_8);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_HIGH);
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void clearResetPasswordToken_passwordTokenIsResetAndUnableToSetNewPassword() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(DPC_COMPONENT_NAME);

            assertThat(sDeviceState.dpc().devicePolicyManager().isResetPasswordTokenActive(
                    DPC_COMPONENT_NAME)).isFalse();
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    DPC_COMPONENT_NAME, VALID_PASSWORD, TOKEN, /* flags = */ 0)).isFalse();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLength_featureUnsupported_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLength(
                DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLength(DPC_COMPONENT_NAME, 42);

        assertWithMessage("getPasswordMinimumLength()")
                .that(sDeviceState.dpc().devicePolicyManager()
                        .getPasswordMinimumLength(DPC_COMPONENT_NAME))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumNumeric_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNumeric(
                DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(DPC_COMPONENT_NAME, 42);

        assertWithMessage("getPasswordMinimumNumeric()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNumeric(
                        DPC_COMPONENT_NAME))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLowerCase_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLowerCase(
                DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(DPC_COMPONENT_NAME,
                42);

        assertWithMessage("getPasswordMinimumLowerCase()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLowerCase(
                        DPC_COMPONENT_NAME))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumUpperCase_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumUpperCase(
                DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(DPC_COMPONENT_NAME,
                42);

        assertWithMessage("getPasswordMinimumUpperCase()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumUpperCase(
                        DPC_COMPONENT_NAME))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLetters_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLetters(
                DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(DPC_COMPONENT_NAME, 42);

        assertWithMessage("getPasswordMinimumLetters()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLetters(
                        DPC_COMPONENT_NAME))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumSymbols_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumSymbols(
                DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(DPC_COMPONENT_NAME, 42);

        assertWithMessage("getPasswordMinimumSymbols()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumSymbols(
                        DPC_COMPONENT_NAME))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumNonLetter_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNonLetter(
                DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(DPC_COMPONENT_NAME,
                42);

        assertWithMessage("getPasswordMinimumNonLetter()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNonLetter(
                        DPC_COMPONENT_NAME))
                .isEqualTo(valueBefore);
    }

    private void assertPasswordSucceeds(String password) {
        assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, password, TOKEN, /* flags = */ 0)).isTrue();
        assertThat(sDeviceState.dpc().devicePolicyManager().isActivePasswordSufficient()).isTrue();
    }

    private void assertPasswordFails(String password) {
        assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, password, TOKEN, /* flags = */ 0)).isFalse();
    }

    private void removeAllPasswordRestrictions() {
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                DPC_COMPONENT_NAME, PASSWORD_QUALITY_UNSPECIFIED);
        sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                PASSWORD_COMPLEXITY_NONE);
        setComplexPasswordRestrictions(/* minLength */ 0, /* minSymbols */ 0, /* minNonLetter */ 0,
                /* minNumeric */ 0, /* minLetters */ 0, /* minLowerCase */ 0, /* minUpperCase */ 0);
    }

    private void setComplexPasswordRestrictions(int minLength, int minSymbols, int minNonLetter,
            int minNumeric, int minLetters, int minLowerCase, int minUpperCase) {
        RemoteDevicePolicyManager dpm = sDeviceState.dpc().devicePolicyManager();
        dpm.setPasswordMinimumLength(DPC_COMPONENT_NAME, minLength);
        dpm.setPasswordMinimumSymbols(DPC_COMPONENT_NAME, minSymbols);
        dpm.setPasswordMinimumNonLetter(DPC_COMPONENT_NAME, minNonLetter);
        dpm.setPasswordMinimumNumeric(DPC_COMPONENT_NAME, minNumeric);
        dpm.setPasswordMinimumLetters(DPC_COMPONENT_NAME, minLetters);
        dpm.setPasswordMinimumLowerCase(DPC_COMPONENT_NAME, minLowerCase);
        dpm.setPasswordMinimumUpperCase(DPC_COMPONENT_NAME, minUpperCase);
    }

    private void removePasswordAndToken(byte[] token) {
        sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                DPC_COMPONENT_NAME, /* password = */ null, token, /* flags = */ 0);
        sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(DPC_COMPONENT_NAME);
    }


    // If ResetPasswordWithTokenTest for managed profile is executed before device owner and
    // primary user profile owner tests, password reset token would have been disabled for the
    // primary user, so executing ResetPasswordWithTokenTest on user 0 would fail. We allow this
    // and do not fail the test in this case.
    private boolean canSetResetPasswordToken(byte[] token) {
        try {
            sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                    DPC_COMPONENT_NAME, token);
            return true;
        } catch (SecurityException e) {
            if (allowFailure(e)) {
                return false;
            } else {
                throw e;
            }
        }
    }

    // Password token is disabled for the primary user, allow failure.
    private static boolean allowFailure(SecurityException e) {
        return !sDeviceState.dpc().devicePolicyManager().isManagedProfile(DPC_COMPONENT_NAME)
                && e.getMessage().equals("Escrow token is disabled on the current user");
    }
}
