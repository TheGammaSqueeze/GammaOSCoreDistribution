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
 * This processor creates tags when an app is launched.
 * @param logger logs by invoking any event messages
 */
class AppLaunchProcessor(logger: (String) -> Unit) : TransitionProcessor(logger) {
    override val transition = Transition.APP_LAUNCH
    private val windowsBecomeVisible =
        HashMap<Int, DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>>()

    override fun getInitialState(tags: MutableMap<Long, MutableList<Tag>>) =
        WaitUntilWindowIsInVisibleActivity(tags)

    /**
     * FSM state that stores any newly visible window activities (start tag)
     * and when their layers stop scaling (end tag).
     */
    inner class WaitUntilWindowIsInVisibleActivity(
        tags: MutableMap<Long, MutableList<Tag>>
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            if (previous == null) return this
            val prevVisibleWindows = previous.wmState.visibleWindows
            val newlyVisibleWindows = current.wmState.visibleWindows.filterNot { window ->
                prevVisibleWindows.any { it.token == window.token }
            }

            // Wait until layer is no longer scaling
            val appLaunchedLayers = windowsBecomeVisible.filterKeys { layerId ->
                val currDumpLayer = current.layerState.getLayerById(layerId)
                (previous.layerState.getLayerById(layerId)?.isScaling == true &&
                currDumpLayer?.isScaling == false)
            }

            // Only want to tag when one app is being launched.
            // Other scenarios like app pairs enter are ignored.
            if (newlyVisibleWindows.size == 1) {
                windowsBecomeVisible[newlyVisibleWindows.first().layerId] = previous
            } else if (appLaunchedLayers.isNotEmpty()) {
                val firstDump = appLaunchedLayers.entries.first()
                val layerId = firstDump.key
                addStartTransitionTag(firstDump.value, transition,
                    layerId = layerId,
                    timestamp = firstDump.value.layerState.timestamp
                )
                addEndTransitionTag(current, transition,
                    layerId = layerId,
                    timestamp = current.layerState.timestamp
                )
                windowsBecomeVisible.clear()
            }
            return this
        }
    }
}
