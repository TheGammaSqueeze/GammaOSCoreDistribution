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

package com.android.server.wm.flicker.service

import com.android.server.wm.flicker.FlickerErrorProto
import com.android.server.wm.flicker.FlickerErrorStateProto
import com.android.server.wm.flicker.FlickerErrorTraceProto
import com.android.server.wm.traces.parser.errors.ErrorTraceParserUtil
import com.google.common.truth.Truth.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [ErrorTraceParserUtil] and [FlickerErrorProto] tests. To run this test: `atest
 * FlickerLibTest:ErrorParserTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ErrorParserTest {
    private val error = FlickerErrorProto.newBuilder()
            .setLayerId(1)
            .setTaskId(2)
            .setWindowToken("token")
            .setMessage("Error!")
            .setStacktrace("stacktrace of error")
            .setAssertionName("LayerIsVisibleAtStart")
            .build()
    private val state = FlickerErrorStateProto.newBuilder()
            .setTimestamp(100)
            .addErrors(error)
            .build()
    private val trace = FlickerErrorTraceProto.newBuilder()
            .addStates(state)
            .build()
    private val traceBytes = trace.toByteArray()

    @Test
    fun canParseErrors() {
        val errorTrace = ErrorTraceParserUtil.parseFromTrace(traceBytes)
        val errorState = errorTrace.entries.first()
        val error = errorState.errors.first()

        assertThat(errorState.timestamp).isEqualTo(100)
        assertThat(error.layerId).isEqualTo(1)
        assertThat(error.taskId).isEqualTo(2)
        assertThat(error.message).isEqualTo("Error!")
        assertThat(error.stacktrace).isEqualTo("stacktrace of error")
        assertThat(error.windowToken).isEqualTo("token")
    }
}