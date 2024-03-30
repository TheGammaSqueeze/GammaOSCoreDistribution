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
import com.android.server.wm.traces.common.WindowManagerConditionsFactory
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerTransformFlagSet
import com.android.server.wm.traces.common.WindowManagerConditionsFactory.isLayerTransformIdentity
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.Transform
import com.android.server.wm.traces.common.service.PlatformConsts.TYPE_APPLICATION_STARTING
import com.android.server.wm.traces.common.service.PlatformConsts.TYPE_BASE_APPLICATION
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.tags.Transition
import com.android.server.wm.traces.common.windowmanager.WindowManagerState
import com.android.server.wm.traces.common.windowmanager.windows.WindowState

/**
 * This processor creates tags when an app is closed.
 * @param logger logs by invoking any event messages
 */
class AppCloseProcessor(logger: (String) -> Unit) : TransitionProcessor(logger) {
    override val transition = Transition.APP_CLOSE
    private val areLayersAnimating = WindowManagerConditionsFactory.hasLayersAnimating()
    private val wmStateIdle = WindowManagerConditionsFactory
        .isAppTransitionIdle(/* default display */ 0)
    private val wmStateComplete = WindowManagerConditionsFactory.isWMStateComplete()
    private val translatingWindows =
        HashMap<String, DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>>()

    override fun getInitialState(tags: MutableMap<Long, MutableList<Tag>>) =
        RetrieveClosingAppLayerId(tags)

    /**
     * Initial FSM state that passes the current app launch activity if any to the next state.
     * Closing app is also not transforming and has transform identity
     */
    inner class RetrieveClosingAppLayerId(
        tags: MutableMap<Long, MutableList<Tag>>
    ) : BaseState(tags) {
        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            val isStableState = wmStateIdle.isSatisfied(current) ||
                wmStateComplete.isSatisfied(current) ||
                areLayersAnimating.negate().isSatisfied(current)
            val currentAppWindows = current.wmState.rootTasks.flatMap { task ->
                // No app launch activities and only resuming activities
                val activities = task.activities.filter { activity ->
                    activity.state == "RESUMED" && activity.isVisible &&
                    activity.children.filterIsInstance<WindowState>().none { window ->
                        window.attributes.type == TYPE_APPLICATION_STARTING
                    }
                }
                activities.flatMap { activity ->
                    activity.children.filterIsInstance<WindowState>().filter { window ->
                        window.isVisible && window.attributes.type == TYPE_BASE_APPLICATION
                    }
                }
            }

            // Only one closing app. This processor ignores app pairs situations.
            if (currentAppWindows.size == 1 && isStableState) {
                val isNotTransforming = isLayerTransformIdentity(currentAppWindows.first().layerId)
                    .isSatisfied(current)
                if (isNotTransforming) {
                    return WaitLayerAnimationComplete(tags, currentAppWindows.first())
                }
            }
            return this
        }
    }

    /**
     * FSM State that waits until the closing app has finished and stopped transforming.
     */
    inner class WaitLayerAnimationComplete(
        tags: MutableMap<Long, MutableList<Tag>>,
        private val appWindow: WindowState
    ) : BaseState(tags) {
        private val layerId = appWindow.layerId
        private val isTranslating = isLayerTransformFlagSet(layerId, Transform.TRANSLATE_VAL)

        override fun doProcessState(
            previous: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>?,
            current: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>,
            next: DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>
        ): FSMState {
            if (previous == null) return this
            val startedTransforming = isTranslating.isSatisfied(current) &&
                isTranslating.negate().isSatisfied(previous)
            val isStableState = wmStateIdle.isSatisfied(current) ||
                wmStateComplete.isSatisfied(current) ||
                areLayersAnimating.negate().isSatisfied(current)

            val finishedClosing = isLayerTransformIdentity(layerId).isSatisfied(current) &&
                current.layerState.getLayerById(layerId)?.isHiddenByParent ?: false

            if (startedTransforming) {
                translatingWindows[appWindow.token] = current
            } else if (finishedClosing && isStableState) {
                val deviceStateDump = translatingWindows[appWindow.token]
                if (deviceStateDump != null) {
                    addStartTransitionTag(deviceStateDump, transition,
                        layerId = layerId,
                        windowToken = appWindow.token
                    )
                    addEndTransitionTag(current, transition,
                        layerId = layerId,
                        windowToken = appWindow.token
                    )
                    return RetrieveClosingAppLayerId(tags)
                }
            }
            return this
        }
    }
}
