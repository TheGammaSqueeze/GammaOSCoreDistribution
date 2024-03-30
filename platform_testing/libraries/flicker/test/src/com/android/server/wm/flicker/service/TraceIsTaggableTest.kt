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

import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.SampleAppHelper
import com.android.server.wm.flicker.monitor.withTracing
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.hasLayersAnimating
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isAppTransitionIdle
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isWMStateComplete
import com.android.server.wm.traces.common.service.TaggingEngine
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.parser.windowmanager.WindowManagerStateHelper
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test

/**
 * Contains a test that records a trace and then tag it.
 *
 * To run this test: `atest FlickerLibTest:TraceIsTaggableTest`
 */
class TraceIsTaggableTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val wmHelper = WindowManagerStateHelper(instrumentation)

    @Before
    fun setup() {
        device.pressHome()
        wmHelper.waitForHomeActivityVisible()
    }

    @Test
    fun canCreateTagsFromDeviceTrace() {

        // Generates trace of opening the messaging application from home screen
        val trace = withTracing {
            SampleAppHelper(instrumentation).launchViaIntent(wmHelper)

            // Wait until transition is fully completed
            wmHelper.waitFor(
                hasLayersAnimating().negate(),
                isAppTransitionIdle(Display.DEFAULT_DISPLAY),
                isWMStateComplete()
            )
        }

        val engine = TaggingEngine(
            requireNotNull(trace.wmTrace),
            requireNotNull(trace.layersTrace)
        ) { }

        val tagStates = engine.run().entries
        Truth.assertThat(tagStates.size).isEqualTo(2)

        val startTag = tagStates.first().tags
        val endTag = tagStates.last().tags
        Truth.assertThat(startTag.size).isEqualTo(1)
        Truth.assertThat(endTag.size).isEqualTo(1)

        Truth.assertThat(startTag.first().transition).isEqualTo(Transition.APP_LAUNCH)
        Truth.assertThat(endTag.first().transition).isEqualTo(Transition.APP_LAUNCH)
    }
}
