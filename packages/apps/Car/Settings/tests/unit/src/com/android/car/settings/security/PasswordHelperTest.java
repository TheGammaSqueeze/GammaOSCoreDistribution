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

package com.android.car.settings.security;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD_OR_PIN;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.app.admin.PasswordMetrics;
import android.content.Context;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.setupwizardlib.InitialLockSetupConstants.LockTypes;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settingslib.utils.StringUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class PasswordHelperTest {
    // Never valid
    private static final String SHORT_SEQUENTIAL_PASSWORD = "111";
    // Only valid when None/Low complexity
    private static final String MEDIUM_SEQUENTIAL_PASSWORD = "22222";
    private static final String LONG_SEQUENTIAL_PASSWORD = "111111111";
    // Valid for None/Low/Medium complexity
    private static final String MEDIUM_ALPHANUMERIC_PASSWORD = "a11r1";
    private static final String MEDIUM_PIN_PASSWORD = "11397";
    // Valid for all complexities
    private static final String LONG_ALPHANUMERIC_PASSWORD = "a11r1t131";
    private static final String LONG_PIN_PASSWORD = "113982125";

    private static final String PASSWORD_MESSAGE = "helper.validate(%s)";
    private static final String ERROR_MESSAGE = "helper.convertErrorCodeToMessages()";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private int mUserId;
    private PasswordMetrics mPasswordMetrics;

    @Mock
    LockPatternUtils mLockPatternUtils;
    @Mock
    LockscreenCredential mExistingCredential;
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        mUserId = UserHandle.myUserId();
        mPasswordMetrics = new PasswordMetrics(CREDENTIAL_TYPE_PASSWORD_OR_PIN);
        when(mLockPatternUtils.getPasswordHistoryHashFactor(any(), anyInt()))
                .thenReturn(new byte[0]);
    }

    @Test
    public void passwordComplexityNone_shortSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_NONE);

        LockscreenCredential password = LockscreenCredential.createPassword(
                SHORT_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 4,
                R.string.lockpassword_password_too_short);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PASSWORD,
                        /* credentialBytes */ SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages).containsExactly(expectedError);
    }

    @Test
    public void passwordComplexityNone_mediumSequentialPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_NONE);

        LockscreenCredential password = LockscreenCredential.createPassword(
                MEDIUM_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PASSWORD,
                /* credentialBytes */ MEDIUM_SEQUENTIAL_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }

    @Test
    public void passwordComplexityLow_shortSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_LOW);

        LockscreenCredential password = LockscreenCredential.createPassword(
                SHORT_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 4,
                R.string.lockpassword_password_too_short);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PASSWORD,
                        /* credentialBytes */ SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages)
                .containsExactly(expectedError);
    }

    @Test
    public void passwordComplexityLow_mediumSequentialPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_LOW);

        LockscreenCredential password = LockscreenCredential.createPassword(
                MEDIUM_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PASSWORD,
                /* credentialBytes */ MEDIUM_SEQUENTIAL_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }

    @Test
    public void passwordComplexityMedium_shortSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_MEDIUM);

        LockscreenCredential password = LockscreenCredential.createPassword(
                SHORT_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 4,
                R.string.lockpassword_password_too_short);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PASSWORD,
                        /* credentialBytes */ SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages)
                .containsExactly(expectedError);
    }

    @Test
    public void passwordComplexityMedium_mediumSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_MEDIUM);

        LockscreenCredential password = LockscreenCredential.createPassword(
                MEDIUM_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 4,
                R.string.lockpassword_pin_no_sequential_digits);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PASSWORD,
                        /* credentialBytes */ MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages)
                .containsExactly(expectedError);
    }

    @Test
    public void passwordComplexityMedium_mediumAlphanumericPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_MEDIUM);

        LockscreenCredential password = LockscreenCredential.createPassword(
                MEDIUM_ALPHANUMERIC_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_ALPHANUMERIC_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_ALPHANUMERIC_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_ALPHANUMERIC_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PASSWORD,
                /* credentialBytes */ MEDIUM_ALPHANUMERIC_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_ALPHANUMERIC_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }

    @Test
    public void passwordComplexityHigh_mediumSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_HIGH);

        LockscreenCredential password = LockscreenCredential.createPassword(
                MEDIUM_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedFirstError = StringUtil.getIcuPluralsString(mContext, /* count= */ 6,
                R.string.lockpassword_password_too_short);
        String expectedSecondError = mContext.getString(
                R.string.lockpassword_pin_no_sequential_digits);
        String combinedError = passwordHelper.getCombinedErrorMessage(
                /* messages */ List.of(expectedFirstError, expectedSecondError));
        // the error message displayed in SUW should be the first error detected
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PASSWORD,
                        /* credentialBytes */ MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(combinedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        // PasswordMetrics considers a password "numeric" based on whether it is a pin or a
        // password. So even though "2222" is numeric, it will have the minimum length requirement
        // of a password.
        assertWithMessage(ERROR_MESSAGE).that(messages)
                .containsExactly(StringUtil.getIcuPluralsString(mContext, /* count= */ 6,
                        R.string.lockpassword_password_too_short),
                        mContext.getString(R.string.lockpassword_pin_no_sequential_digits));
    }

    @Test
    public void passwordComplexityHigh_mediumAlphanumericPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_HIGH);

        LockscreenCredential password = LockscreenCredential.createPassword(
                MEDIUM_ALPHANUMERIC_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_ALPHANUMERIC_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_ALPHANUMERIC_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_ALPHANUMERIC_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 6,
                R.string.lockpassword_password_too_short);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_ALPHANUMERIC_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PASSWORD,
                        /* credentialBytes */ MEDIUM_ALPHANUMERIC_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages).containsExactly(expectedError);
    }

    @Test
    public void passwordComplexityHigh_longSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_HIGH);

        LockscreenCredential password = LockscreenCredential.createPassword(
                LONG_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, LONG_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, LONG_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(LONG_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = mContext.getString(R.string.lockpassword_pin_no_sequential_digits);
        assertWithMessage(PASSWORD_MESSAGE, LONG_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PASSWORD,
                        /* credentialBytes */ LONG_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages).containsExactly(expectedError);
    }

    @Test
    public void passwordComplexityHigh_longAlphanumericPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ false, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_HIGH);

        LockscreenCredential password = LockscreenCredential.createPassword(
                LONG_ALPHANUMERIC_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, LONG_ALPHANUMERIC_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, LONG_ALPHANUMERIC_PASSWORD)
                .that(passwordHelper.validateSetupWizard(LONG_ALPHANUMERIC_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PASSWORD,
                /* credentialBytes */ LONG_ALPHANUMERIC_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, LONG_ALPHANUMERIC_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }

    @Test
    public void pinComplexityNone_shortSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_NONE);

        LockscreenCredential password = LockscreenCredential.createPin(
                SHORT_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 4,
                R.string.lockpassword_pin_too_short);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PIN,
                        /* credentialBytes */ SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages).containsExactly(expectedError);
    }

    @Test
    public void pinComplexityNone_mediumSequentialPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_NONE);

        LockscreenCredential password = LockscreenCredential.createPin(
                MEDIUM_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PIN,
                /* credentialBytes */ MEDIUM_SEQUENTIAL_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }

    @Test
    public void pinComplexityLow_shortSequentialPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_LOW);

        LockscreenCredential password = LockscreenCredential.createPin(
                SHORT_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 4,
                R.string.lockpassword_pin_too_short);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PIN,
                        /* credentialBytes */ SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages).containsExactly(expectedError);
    }

    @Test
    public void pinComplexityLow_mediumSequentialPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_LOW);

        LockscreenCredential password = LockscreenCredential.createPin(
                MEDIUM_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PIN,
                /* credentialBytes */ MEDIUM_SEQUENTIAL_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }

    @Test
    public void pinComplexityMedium_shortSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_MEDIUM);

        LockscreenCredential password = LockscreenCredential.createPin(
                SHORT_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 4,
                R.string.lockpassword_pin_too_short);
        assertWithMessage(PASSWORD_MESSAGE, SHORT_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PIN,
                        /* credentialBytes */ SHORT_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages)
                .containsExactly(expectedError);
    }

    @Test
    public void pinComplexityMedium_mediumSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_MEDIUM);

        LockscreenCredential password = LockscreenCredential.createPin(
                MEDIUM_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);
        String expectedError = mContext.getString(R.string.lockpassword_pin_no_sequential_digits);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PIN,
                        /* credentialBytes */ MEDIUM_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages).containsExactly(expectedError);
    }

    @Test
    public void pinComplexityMedium_mediumPinPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_MEDIUM);

        LockscreenCredential password = LockscreenCredential.createPin(
                MEDIUM_PIN_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_PIN_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_PIN_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_PIN_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PIN,
                /* credentialBytes */ MEDIUM_PIN_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_PIN_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }

    @Test
    public void pinComplexityHigh_mediumPinPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_HIGH);

        LockscreenCredential password = LockscreenCredential.createPin(
                MEDIUM_PIN_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_PIN_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, MEDIUM_PIN_PASSWORD)
                .that(passwordHelper.validateSetupWizard(MEDIUM_PIN_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = StringUtil.getIcuPluralsString(mContext, /* count= */ 8,
                R.string.lockpassword_pin_too_short);
        assertWithMessage(PASSWORD_MESSAGE, LONG_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PIN,
                        /* credentialBytes */ MEDIUM_PIN_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages)
                .containsExactly(expectedError);
    }

    @Test
    public void pinComplexityHigh_longSequentialPassword_invalid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_HIGH);

        LockscreenCredential password = LockscreenCredential.createPin(
                LONG_SEQUENTIAL_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, LONG_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isFalse();
        assertWithMessage(PASSWORD_MESSAGE, LONG_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizard(LONG_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.ERROR_CODE);

        String expectedError = mContext.getString(R.string.lockpassword_pin_no_sequential_digits);
        assertWithMessage(PASSWORD_MESSAGE, LONG_SEQUENTIAL_PASSWORD)
                .that(passwordHelper.validateSetupWizardAndReturnError(
                        /* lockType */ LockTypes.PIN,
                        /* credentialBytes */ LONG_SEQUENTIAL_PASSWORD.getBytes()))
                .isEqualTo(expectedError);

        List<String> messages = passwordHelper.convertErrorCodeToMessages();
        assertWithMessage(ERROR_MESSAGE).that(messages).containsExactly(expectedError);
    }

    @Test
    public void pinComplexityHigh_longPinPassword_valid() {
        PasswordHelper passwordHelper = new PasswordHelper(mContext, /* isPin= */ true, mUserId,
                mLockPatternUtils, mPasswordMetrics, PASSWORD_COMPLEXITY_HIGH);

        LockscreenCredential password = LockscreenCredential.createPin(
                LONG_PIN_PASSWORD);
        assertWithMessage(PASSWORD_MESSAGE, LONG_PIN_PASSWORD)
                .that(passwordHelper.validate(password, mExistingCredential)).isTrue();
        assertWithMessage(PASSWORD_MESSAGE, LONG_PIN_PASSWORD)
                .that(passwordHelper.validateSetupWizard(LONG_PIN_PASSWORD.getBytes()))
                .isEqualTo(PasswordHelper.NO_ERROR);
        String noErrorMessage = passwordHelper.validateSetupWizardAndReturnError(
                /* lockType */ LockTypes.PIN,
                /* credentialBytes */ LONG_PIN_PASSWORD.getBytes());
        assertWithMessage(PASSWORD_MESSAGE, LONG_PIN_PASSWORD)
                .that(noErrorMessage)
                .isEqualTo(PasswordHelper.NO_ERROR_MESSAGE);
    }
}
