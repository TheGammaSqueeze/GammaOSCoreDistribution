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

package com.android.safetycenter.config

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [ParseException]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class ParseExceptionTest {

    @Test
    fun propagatesMessage() {
        val message = "error message"

        val exception = ParseException(message)

        assertThat(exception).hasMessageThat().isEqualTo(message)
        assertThat(exception).hasCauseThat().isNull()
    }

    @Test
    fun propagatesMessageAndCause() {
        val message = "error message"
        val cause = Exception("error message for cause")

        val exception = ParseException(message, cause)

        assertThat(exception).hasMessageThat().isEqualTo(message)
        assertThat(exception).hasCauseThat().isEqualTo(cause)
    }
}
