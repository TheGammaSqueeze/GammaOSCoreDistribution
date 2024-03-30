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

import com.android.server.wm.flicker.FlickerTagProto
import com.android.server.wm.flicker.FlickerTagStateProto
import com.android.server.wm.flicker.FlickerTagTraceProto
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.parser.tags.TagTraceParserUtil
import com.google.common.truth.Truth.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [TagTraceParserUtil] and [FlickerTagProto] tests. To run this test: `atest
 * FlickerLibTest:TagParserTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TagParserTest {
    private val tag = FlickerTagProto.newBuilder()
                .setLayerId(1)
                .setTaskId(2)
                .setIsStartTag(true)
                .setId(123)
                .setTransition(FlickerTagProto.Transition.APP_CLOSE)
                .setWindowToken("token")
                .build()
    private val state = FlickerTagStateProto.newBuilder()
                .setTimestamp(100)
                .addTags(tag)
                .build()
    private val trace = FlickerTagTraceProto.newBuilder()
                .addStates(state)
                .build()
    private val traceBytes = trace.toByteArray()

    @Test
    fun canParseTags() {
        val tagTrace = TagTraceParserUtil.parseFromTrace(traceBytes)
        val tagState = tagTrace.entries.first()
        val tag = tagState.tags.first()

        assertThat(tagState.timestamp).isEqualTo(100)
        assertThat(tag.layerId).isEqualTo(1)
        assertThat(tag.taskId).isEqualTo(2)
        assertThat(tag.id).isEqualTo(123)
        assertThat(tag.isStartTag).isTrue()
        assertThat(tag.transition).isEqualTo(Transition.APP_CLOSE)
        assertThat(tag.windowToken).isEqualTo("token")
    }
}