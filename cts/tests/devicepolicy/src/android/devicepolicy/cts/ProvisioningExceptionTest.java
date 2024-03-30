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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.ProvisioningException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProvisioningExceptionTest {

    private static final Exception CAUSE = new Exception();
    private static final int PROVISIONING_ERROR = ProvisioningException.ERROR_PRE_CONDITION_FAILED;
    private static final String MESSAGE = "test failure message";

    @Test
    public void constructor_works() {
        ProvisioningException exception =
                new ProvisioningException(CAUSE, PROVISIONING_ERROR, MESSAGE);

        assertThat(exception.getCause()).isEqualTo(CAUSE);
        assertThat(exception.getProvisioningError()).isEqualTo(PROVISIONING_ERROR);
        assertThat(exception.getMessage()).isEqualTo(MESSAGE);
    }

    @Test
    public void constructor_noErrorMessage_nullByDefault() {
        ProvisioningException exception = new ProvisioningException(CAUSE, PROVISIONING_ERROR);

        assertThat(exception.getMessage()).isNull();
    }
}
