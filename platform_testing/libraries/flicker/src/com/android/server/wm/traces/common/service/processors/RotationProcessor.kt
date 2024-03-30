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
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.RectF
import com.android.server.wm.traces.common.WindowManagerConditionsFactory
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.common.windowmanager.WindowManagerState

/**
 * Processor to detect rotations.
 *
 * First check the WM state for a rotation change, then wait the SF rotation
 * to occur and both nav and status bars to appear
 */
class RotationProcessor(logger: (String) -> Unit) : TransitionProcessor(logger) {
    override val transition = Transition.ROTATION
    override fun getInitialState(tags: MutableMap<Long, MutableList<Tag>>) = InitialState(tags)

    /**
     * Initial FSM state, obtains the current display size and start searching
     * for display size changes
     */
    inner class InitialState(
        tags: MutableMap<Long, MutableList<Tag>>
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            val currDisplayRect = current.wmState.displaySize()
            logger.invoke("(${current.wmState.timestamp}) Initial state. " +
                "Display size $currDisplayRect")
            return WaitDisplayRectChange(tags, currDisplayRect)
        }
    }

    /**
     * FSM state when the display size has not changed since [InitialState]
     */
    inner class WaitDisplayRectChange(
        tags: MutableMap<Long, MutableList<Tag>>,
        private val currDisplayRect: RectF
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            val newWmDisplayRect = current.wmState.displaySize()
            val newLayersDisplayRect = current.layerState.screenBounds()

            return when {
                // WM display changed first (Regular rotation)
                // SF display changed first (Seamless rotation)
                newWmDisplayRect != currDisplayRect || newLayersDisplayRect != currDisplayRect -> {
                    requireNotNull(previous) { "Should have a previous state" }
                    val rect = if (newWmDisplayRect != currDisplayRect) {
                        newWmDisplayRect
                    } else {
                        newLayersDisplayRect
                    }
                    processDisplaySizeChange(previous, rect)
                }
                else -> {
                    logger.invoke("(${current.wmState.timestamp}) No display size change")
                    this
                }
            }
        }

        private fun processDisplaySizeChange(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            newDisplayRect: RectF
        ): FSMState {
            logger.invoke("(${previous.wmState.timestamp}) Display size changed " +
                "to $newDisplayRect")
            // tag on the last complete state at the start
            logger.invoke("(${previous.wmState.timestamp}) Tagging transition start")
            addStartTransitionTag(previous, transition)
            return WaitRotationFinished(tags)
        }
    }

    /**
     * FSM state for when the animation occurs in the SF trace
     */
    inner class WaitRotationFinished(tags: MutableMap<Long, MutableList<Tag>>) : BaseState(tags) {
        private val rotationLayerExists = WindowManagerConditionsFactory
            .isLayerVisible(FlickerComponentName.ROTATION)
        private val backSurfaceLayerExists = WindowManagerConditionsFactory
            .isLayerVisible(FlickerComponentName.BACK_SURFACE)
        private val areLayersAnimating = WindowManagerConditionsFactory.hasLayersAnimating()
        private val wmStateIdle = WindowManagerConditionsFactory
            .isAppTransitionIdle(/* default display */ 0)
        private val wmStateComplete = WindowManagerConditionsFactory.isWMStateComplete()

        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            val anyLayerAnimating = areLayersAnimating.isSatisfied(current)
            val rotationLayerExists = rotationLayerExists.isSatisfied(current)
            val blackSurfaceLayerExists = backSurfaceLayerExists.isSatisfied(current)
            val wmStateIdle = wmStateIdle.isSatisfied(current)
            val wmStateComplete = wmStateComplete.isSatisfied(current)

            val newWmDisplayRect = current.wmState.displaySize()
            val newLayersDisplayRect = current.layerState.screenBounds()
            val displaySizeDifferent = newWmDisplayRect != newLayersDisplayRect

            val inRotation = anyLayerAnimating || rotationLayerExists || blackSurfaceLayerExists ||
                displaySizeDifferent || !wmStateIdle || !wmStateComplete
            logger.invoke("(${current.layerState.timestamp}) " +
                "In rotation? $inRotation (" +
                "anyLayerAnimating=$anyLayerAnimating, " +
                "blackSurfaceLayerExists=$blackSurfaceLayerExists, " +
                "rotationLayerExists=$rotationLayerExists, " +
                "wmStateIdle=$wmStateIdle, " +
                "wmStateComplete=$wmStateComplete, " +
                "displaySizeDifferent=$displaySizeDifferent)")
            return if (inRotation) {
                this
            } else {
                // tag on the last complete state at the start
                logger.invoke("(${current.layerState.timestamp}) Tagging transition end")
                addEndTransitionTag(current, transition)
                // return to start to wait for a second rotation
                val lastDisplayRect = current.wmState.displaySize()
                WaitDisplayRectChange(tags, lastDisplayRect)
            }
        }
    }

    companion object {
        private fun BaseLayerTraceEntry.screenBounds() = this.displays.minByOrNull { it.id }
            ?.layerStackSpace?.toRectF() ?: this.children
            .sortedBy { it.id }
            .firstOrNull { it.isRootLayer }
            ?.screenBounds ?: error("Unable to identify screen bounds (display is empty in proto)")

        private fun WindowManagerState.displaySize() = getDefaultDisplay()
            ?.displayRect?.toRectF() ?: RectF.EMPTY
    }
}