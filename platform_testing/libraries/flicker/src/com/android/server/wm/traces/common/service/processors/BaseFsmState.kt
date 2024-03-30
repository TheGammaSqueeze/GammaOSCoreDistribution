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

package com.android.server.wm.traces.common.service.processors

import com.android.server.wm.traces.common.DeviceStateDump
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.common.windowmanager.WindowManagerState

/**
 * Base state for the FSM, check if there are more WM and SF states to process
 * and ensure there is always a 1:1 correspondence between start and end tags.
 * If the location of the end of the transition wasn't found, add an end tag at end of trace.
 */
abstract class BaseFsmState(
    tags: MutableMap<Long, MutableList<Tag>>,
    internal val logger: (String) -> Unit,
    internal val transition: Transition
) : FSMState(tags) {
    protected abstract fun doProcessState(
        previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
        current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
        next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
    ): FSMState

    override fun process(
        previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
        current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
        next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?
    ): FSMState? {
        return if (next == null) {
            // last state
            val timestamp = current.layerState.timestamp
            logger.invoke("($timestamp) Trace has reached the end")
            if (hasOpenTag()) {
                logger.invoke("($timestamp) Has an open tag, closing it on the last SF state")
                addEndTransitionTag(current, transition)
            }
            null
        } else {
            doProcessState(previous, current, next)
        }
    }
}