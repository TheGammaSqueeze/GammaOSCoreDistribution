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
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerTransformFlagSet
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerTransformIdentity
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.Transform
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.common.windowmanager.WindowManagerState

/**
 * This processor creates tags when an pip window starts to expand from window to full screen app.
 * @param logger logs by invoking any event messages
 */
class PipExpandProcessor(logger: (String) -> Unit) : TransitionProcessor(logger) {
    override val transition = Transition.PIP_EXPAND
    private val scalingLayers =
        HashMap<Int, DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>>()

    override fun getInitialState(tags: MutableMap<Long, MutableList<Tag>>) =
        WaitUntilAppIsNoLongerPinned(tags)

    /**
     * We wait until the pip app is no longer pinned and is ready to expand.
     */
    inner class WaitUntilAppIsNoLongerPinned(
        tags: MutableMap<Long, MutableList<Tag>>
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            // Pinned window is no longer pinned
            val prevPinnedWindows = previous?.wmState?.pinnedWindows?.toList() ?: emptyList()
            val currPinnedWindows = current.wmState.pinnedWindows.toList()

            if (prevPinnedWindows.isNotEmpty() && currPinnedWindows.isEmpty()) {
                return WaitUntilAppCompletesExpanding(tags, prevPinnedWindows.first().layerId)
            }
            return this
        }
    }

    /**
     * FSMState when app has been unpinned and we track its corresponding layer.
     * We record every time the layer is scaling and check it has transform identity
     * with increased bounds.
     */
    inner class WaitUntilAppCompletesExpanding(
        tags: MutableMap<Long, MutableList<Tag>>,
        private val layerId: Int
    ) : BaseState(tags) {
        private val isScaling = isLayerTransformFlagSet(layerId, Transform.SCALE_VAL)
        private val isIdentity = isLayerTransformIdentity(layerId)
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            if (previous == null) return this
            if (previous.wmState.pinnedWindows.isNotEmpty()) {
                return WaitUntilAppIsNoLongerPinned(tags)
            }

            val startedScaling = isScaling.isSatisfied(current) &&
                isScaling.negate().isSatisfied(previous)

            val currLayerBounds = current.layerState.getLayerById(layerId)?.bounds ?: return this
            val prevLayerBounds = previous.layerState.getLayerById(layerId)?.bounds ?: return this
            val finishedExpanding = isScaling.isSatisfied(previous) &&
                isIdentity.isSatisfied(current) &&
                currLayerBounds.height > prevLayerBounds.height &&
                currLayerBounds.width > prevLayerBounds.width

            if (startedScaling) {
                scalingLayers[layerId] = current
            } else if (finishedExpanding) {
                val dump = scalingLayers[layerId]
                if (dump != null) {
                    addStartTransitionTag(current, transition,
                        layerId = layerId,
                        timestamp = dump.layerState.timestamp
                    )
                    addEndTransitionTag(current, transition,
                        layerId = layerId,
                        timestamp = current.layerState.timestamp
                    )
                    scalingLayers.clear()
                    return WaitUntilAppIsNoLongerPinned(tags)
                }
            }
            return this
        }
    }
}
