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

import com.android.server.wm.traces.common.ConditionList
import com.android.server.wm.traces.common.DeviceStateDump
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isImeShown
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerColorAlphaOne
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerTransformFlagSet
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerVisible
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.Transform
import com.android.server.wm.traces.common.service.PlatformConsts
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.common.windowmanager.WindowManagerState

/**
 * This processor creates tags when the keyboard starts and finishes appearing.
 * @param logger logs by invoking any event messages
 */
class ImeAppearProcessor(logger: (String) -> Unit) : TransitionProcessor(logger) {
    override val transition = Transition.IME_APPEAR
    override fun getInitialState(tags: MutableMap<Long, MutableList<Tag>>) =
        WaitInputMethodVisible(tags)

    /**
     * FSM state that waits until the InputMethod is visible in both WM and SF.
     */
    inner class WaitInputMethodVisible(
        tags: MutableMap<Long, MutableList<Tag>>
    ) : BaseState(tags) {
        private val newImeVisible = isImeShown(PlatformConsts.DEFAULT_DISPLAY)
        private val prevImeInvisible = newImeVisible.negate()

        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            if (previous == null) return this

            return if (newImeVisible.isSatisfied(current) &&
                prevImeInvisible.isSatisfied(previous)) {
                processInputMethodVisible(current)
            } else {
                this
            }
        }

        private fun processInputMethodVisible(
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            logger.invoke("(${current.layerState.timestamp}) IME appear started.")
            // add factory method as well
            val inputMethodLayer = current.layerState.visibleLayers.first {
                it.name.contains(FlickerComponentName.IME.toLayerName())
            }
            addStartTransitionTag(current, transition, layerId = inputMethodLayer.id)
            return WaitImeAppearFinished(tags, inputMethodLayer.id)
        }
    }

    /**
     * FSM state to check when the Ime Appear has finished by opaque color alpha of input method
     * and it has finished transforming and scaling.
     */
    inner class WaitImeAppearFinished(
        tags: MutableMap<Long, MutableList<Tag>>,
        private val layerId: Int
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            val isImeAppearFinished = isImeAppearFinished.isSatisfied(current)

            return if (isImeAppearFinished) {
                // tag on the last complete state at the start
                logger.invoke("(${current.layerState.timestamp}) Ime appear end detected.")
                addEndTransitionTag(current, transition, layerId = layerId)
                // return to start to wait for a second IME appear
                WaitInputMethodVisible(tags)
            } else {
                logger.invoke("(${current.layerState.timestamp}) Ime appear hasn't finished.")
                this
            }
        }

        private val isImeAppearFinished = ConditionList(listOf(
            isLayerVisible(FlickerComponentName.IME),
            isLayerColorAlphaOne(FlickerComponentName.IME),
            isLayerTransformFlagSet(FlickerComponentName.IME, Transform.TRANSLATE_VAL),
            isLayerTransformFlagSet(FlickerComponentName.IME, Transform.SCALE_VAL).negate()
        ))
    }
}