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

package com.android.server.wm.flicker.service.assertors

import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.flicker.traces.layers.LayerSubject
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowStateSubject
import com.android.server.wm.traces.common.layers.Layer
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.TransitionTag
import com.android.server.wm.traces.common.windowmanager.windows.WindowState

/**
 * Base calss for a FASS assertion
 */
abstract class BaseAssertion {
    private var failureSubject: FlickerSubject? = null

    /**
     * Assertion name
     */
    val name: String = this::class.java.simpleName

    /**
     * Run specific assertion evaluation block
     *
     * @param tag a list with all [TransitionTag]s
     * @param wmSubject Window Manager trace subject
     * @param layerSubject Surface Flinger trace subject
     */
    protected abstract fun doEvaluate(
        tag: Tag,
        wmSubject: WindowManagerTraceSubject,
        layerSubject: LayersTraceSubject
    )

    /**
     * Evaluate the assertion on a transition [Tag] in a [WindowManagerTraceSubject] and
     * [LayersTraceSubject]
     *
     * @param tag a list with all [TransitionTag]s
     * @param wmSubject Window Manager trace subject
     * @param layerSubject Surface Flinger trace subject
     */
    fun evaluate(
        tag: Tag,
        wmSubject: WindowManagerTraceSubject,
        layerSubject: LayersTraceSubject
    ) {
        try {
            doEvaluate(tag, wmSubject, layerSubject)
        } catch (e: FlickerSubjectException) {
            failureSubject = e.subject
            throw e
        }
    }

    /**
     * Returns the layer responsible for the failure, if any
     *
     * @param tag a list with all [TransitionTag]s
     * @param wmSubject Window Manager trace subject
     * @param layerSubject Surface Flinger trace subject
     */
    open fun getFailureLayer(
        tag: Tag,
        wmSubject: WindowManagerTraceSubject,
        layerSubject: LayersTraceSubject
    ): Layer? {
        val failureSubject = failureSubject
        return if (failureSubject is LayerSubject) {
            failureSubject.layer
        } else {
            null
        }
    }

    /**
     * Returns the window responsible for the last failure, if any
     *
     * @param tag a list with all [TransitionTag]s
     * @param wmSubject Window Manager trace subject
     * @param layerSubject Surface Flinger trace subject
     */
    open fun getFailureWindow(
        tag: Tag,
        wmSubject: WindowManagerTraceSubject,
        layerSubject: LayersTraceSubject
    ): WindowState? {
        val failureSubject = failureSubject
        return if (failureSubject is WindowStateSubject) {
            failureSubject.windowState
        } else {
            null
        }
    }
}