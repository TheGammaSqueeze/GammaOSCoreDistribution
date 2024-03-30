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
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.hasLayersAnimating
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isAppTransitionIdle
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerColorAlphaOne
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerTransformFlagSet
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerVisible
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isWMStateComplete
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.Transform
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.common.windowmanager.WindowManagerState

/**
 * This processor creates tags when the pip window is exited directly to home screen or a
 * different app altogether.
 * @param logger logs by invoking any event messages
 */
class PipExitProcessor(logger: (String) -> Unit) : TransitionProcessor(logger) {
    override val transition = Transition.PIP_EXIT
    private val scalingWindows =
        HashMap<String, DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>>()
    private val areLayersAnimating = hasLayersAnimating()
    private val wmStateIdle = isAppTransitionIdle(/* default display */ 0)
    private val wmStateComplete = isWMStateComplete()

    override fun getInitialState(tags: MutableMap<Long, MutableList<Tag>>) =
            WaitPinnedWindowSwipedOrFading(tags)

    /**
     * Initial FSM state which waits until the app window in pip mode starts to change opacity.
     */
    inner class WaitPinnedWindowSwipedOrFading(
        tags: MutableMap<Long, MutableList<Tag>>
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            if (previous == null) return this
            // There can only ever be one pinned window at a time
            val nextPinnedWindow = next.wmState.pinnedWindows.firstOrNull()
            val currPinnedWindow = current.wmState.pinnedWindows.firstOrNull() ?: return this

            if (nextPinnedWindow == null) {
                val dump = scalingWindows[currPinnedWindow.token]
                if (dump != null) {
                    // Pip app unpinned so let's tag.
                    addStartTransitionTag(dump, transition,
                        layerId = currPinnedWindow.layerId,
                        windowToken = currPinnedWindow.token
                    )
                    addEndTransitionTag(previous, transition,
                        layerId = currPinnedWindow.layerId,
                        windowToken = currPinnedWindow.token
                    )
                }
                return this
            }

            // close pip by swiping
            val isScaling = isLayerTransformFlagSet(currPinnedWindow.layerId, Transform.SCALE_VAL)
            val movingPinnedWindow =
                isScaling.isSatisfied(current) && isScaling.negate().isSatisfied(previous)

            // close pip by pressing dismiss button
            val colorAlphaIsOne = isLayerColorAlphaOne(currPinnedWindow.layerId)
            val pinnedWindowFading = colorAlphaIsOne.negate().isSatisfied(current) &&
                colorAlphaIsOne.isSatisfied(previous) &&
                isScaling.negate().isSatisfied(current)

            return when {
                movingPinnedWindow -> {
                    // Record last time when pip app started scaling
                    scalingWindows[currPinnedWindow.token] = current
                    this
                }
                pinnedWindowFading -> {
                    addStartTransitionTag(previous, transition,
                        layerId = currPinnedWindow.layerId,
                        windowToken = currPinnedWindow.token
                    )
                    WaitUntilPipColorAlphaIsOneAndInvisible(tags, currPinnedWindow.layerId)
                }
                else -> {
                    this
                }
            }
        }
    }

    inner class WaitUntilPipColorAlphaIsOneAndInvisible(
        tags: MutableMap<Long, MutableList<Tag>>,
        private val layerId: Int
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            val layerInvisible = isLayerVisible(layerId).negate().isSatisfied(current)
            val layerColorAlphaOne = isLayerColorAlphaOne(layerId).isSatisfied(current)
            val isStableState = wmStateIdle.isSatisfied(current) ||
                wmStateComplete.isSatisfied(current) ||
                areLayersAnimating.negate().isSatisfied(current)

            return if (layerInvisible && layerColorAlphaOne && isStableState) {
                addEndTransitionTag(current, transition, layerId = layerId)
                WaitPinnedWindowSwipedOrFading(tags)
            } else {
                this
            }
        }
    }
}
