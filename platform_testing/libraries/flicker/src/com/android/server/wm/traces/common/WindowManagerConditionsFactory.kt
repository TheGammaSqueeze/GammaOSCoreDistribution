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

package com.android.server.wm.traces.common

import com.android.server.wm.traces.common.layers.Layer
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.Transform
import com.android.server.wm.traces.common.layers.Transform.Companion.isFlagSet
import com.android.server.wm.traces.common.service.PlatformConsts
import com.android.server.wm.traces.common.windowmanager.WindowManagerState
import com.android.server.wm.traces.common.windowmanager.windows.WindowState

typealias DUMP = DeviceStateDump<WindowManagerState, BaseLayerTraceEntry>

object WindowManagerConditionsFactory {
    private val navBarWindowName = FlickerComponentName.NAV_BAR.toWindowName()
    private val navBarLayerName = FlickerComponentName.NAV_BAR.toLayerName()
    private val statusBarWindowName = FlickerComponentName.STATUS_BAR.toWindowName()
    private val statusBarLayerName = FlickerComponentName.STATUS_BAR.toLayerName()

    /**
     * Condition to check if the nav bar window is visible
     */
    fun isNavBarVisible(): Condition<DUMP> =
        ConditionList(listOf(
            isNavBarWindowVisible(), isNavBarLayerVisible(), isNavBarLayerOpaque()))

    /**
     * Condition to check if the nav bar window is visible
     */
    fun isNavBarWindowVisible(): Condition<DUMP> =
        Condition("isNavBarWindowVisible") {
            it.wmState.isWindowVisible(navBarWindowName)
        }

    /**
     * Condition to check if the nav bar layer is visible
     */
    fun isNavBarLayerVisible(): Condition<DUMP> =
        isLayerVisible(navBarLayerName)

    /**
     * Condition to check if the nav bar layer is opaque
     */
    fun isNavBarLayerOpaque(): Condition<DUMP> =
        Condition("isNavBarLayerOpaque") {
            (it.layerState.getLayerWithBuffer(navBarLayerName)
                ?.color?.a ?: 0f) == 1f
        }

    /**
     * Condition to check if the status bar window is visible
     */
    fun isStatusBarVisible(): Condition<DUMP> =
        ConditionList(listOf(
            isStatusBarWindowVisible(), isStatusBarLayerVisible(), isStatusBarLayerOpaque()))

    /**
     * Condition to check if the nav bar window is visible
     */
    fun isStatusBarWindowVisible():
        Condition<DUMP> =
        Condition("isStatusBarWindowVisible") {
            it.wmState.isWindowVisible(statusBarWindowName)
        }

    /**
     * Condition to check if the nav bar layer is visible
     */
    fun isStatusBarLayerVisible(): Condition<DUMP> =
        isLayerVisible(statusBarLayerName)

    /**
     * Condition to check if the nav bar layer is opaque
     */
    fun isStatusBarLayerOpaque(): Condition<DUMP> =
        Condition("isStatusBarLayerOpaque") {
            (it.layerState.getLayerWithBuffer(statusBarLayerName)
                ?.color?.a ?: 0f) == 1f
        }

    fun isHomeActivityVisible(): Condition<DUMP> =
        Condition("isHomeActivityVisible") {
            it.wmState.homeActivity?.isVisible == true
        }

    fun isAppTransitionIdle(
        displayId: Int
    ): Condition<DUMP> =
        Condition("isAppTransitionIdle[$displayId]") {
            it.wmState.getDisplay(displayId)
                ?.appTransitionState == WindowManagerState.APP_STATE_IDLE
        }

    fun containsActivity(
        component: FlickerComponentName
    ): Condition<DUMP> =
        Condition("containsActivity[${component.toActivityName()}]") {
            it.wmState.containsActivity(component.toActivityName())
        }

    fun containsWindow(
        component: FlickerComponentName
    ): Condition<DUMP> =
        Condition("containsWindow[${component.toWindowName()}]") {
            it.wmState.containsWindow(component.toWindowName())
        }

    fun isWindowSurfaceShown(
        windowName: String
    ): Condition<DUMP> =
        Condition("isWindowSurfaceShown[$windowName]") {
            it.wmState.isWindowSurfaceShown(windowName)
        }

    fun isWindowSurfaceShown(
        component: FlickerComponentName
    ): Condition<DUMP> =
        isWindowSurfaceShown(component.toWindowName())

    fun isActivityVisible(
        component: FlickerComponentName
    ): Condition<DUMP> =
        Condition("isActivityVisible[${component.toActivityName()}]") {
            it.wmState.isActivityVisible(component.toActivityName())
        }

    fun isWMStateComplete(): Condition<DUMP> =
        Condition("isWMStateComplete") {
            it.wmState.isComplete()
        }

    fun hasRotation(
        expectedRotation: Int,
        displayId: Int
    ): Condition<DUMP> {
        val hasRotationCondition = Condition<DUMP>(
            "hasRotation[$expectedRotation, display=$displayId]") {
            val currRotation = it.wmState.getRotation(displayId)
            currRotation == expectedRotation
        }
        return ConditionList(listOf(
            hasRotationCondition,
            isLayerVisible(FlickerComponentName.ROTATION).negate(),
            isLayerVisible(FlickerComponentName.BACK_SURFACE).negate(),
            hasLayersAnimating().negate()
        ))
    }

    fun isWindowVisible(
        component: FlickerComponentName,
        displayId: Int = 0
    ): Condition<DUMP> =
        ConditionList(
            containsActivity(component),
            containsWindow(component),
            isActivityVisible(component),
            isWindowSurfaceShown(component),
            isAppTransitionIdle(displayId))

    fun isLayerVisible(
        layerName: String
    ): Condition<DUMP> =
        Condition("isLayerVisible[$layerName]") {
            it.layerState.isVisible(layerName)
        }

    fun isLayerVisible(
        layerId: Int
    ): Condition<DUMP> =
        Condition("isLayerVisible[layerId=$layerId]") {
            it.layerState.getLayerById(layerId)?.isVisible ?: false
        }

    fun isLayerColorAlphaOne(
        component: FlickerComponentName
    ): Condition<DUMP> =
        Condition("isLayerColorAlphaOne[${component.toLayerName()}]") {
            val layers = it.layerState.getVisibleLayersByName(component)
            layers.any { layer -> layer.color.a == 1.0f }
        }

    fun isLayerColorAlphaOne(
        layerId: Int
    ): Condition<DUMP> =
        Condition("isLayerColorAlphaOne[$layerId]") {
            val layer = it.layerState.getLayerById(layerId)
            layer?.color?.a == 1.0f
        }

    fun isLayerTransformFlagSet(
        component: FlickerComponentName,
        transform: Int
    ): Condition<DUMP> =
        Condition("isLayerTransformFlagSet[" +
            "${component.toLayerName()},transform=$transform]") {
            val layers = it.layerState.getVisibleLayersByName(component)
            layers.any { layer -> isTransformFlagSet(layer, transform) }
        }

    fun isLayerTransformFlagSet(
        layerId: Int,
        transform: Int
    ): Condition<DUMP> =
        Condition("isLayerTransformFlagSet[$layerId, $transform]") {
            val layer = it.layerState.getLayerById(layerId)
            layer?.transform?.type?.isFlagSet(transform) ?: false
        }

    fun isLayerTransformIdentity(
        layerId: Int
    ): Condition<DUMP> =
        ConditionList(listOf(
            isLayerTransformFlagSet(layerId, Transform.SCALE_VAL).negate(),
            isLayerTransformFlagSet(layerId, Transform.TRANSLATE_VAL).negate(),
            isLayerTransformFlagSet(layerId, Transform.ROTATE_VAL).negate()
        ))

    private fun isTransformFlagSet(layer: Layer, transform: Int): Boolean =
        layer.transform.type?.isFlagSet(transform) ?: false

    fun BaseLayerTraceEntry.getVisibleLayersByName(
        component: FlickerComponentName
    ): List<Layer> = visibleLayers.filter { it.name.contains(component.toLayerName()) }

    fun isLayerVisible(
        component: FlickerComponentName
    ): Condition<DUMP> =
        isLayerVisible(component.toLayerName())

    fun hasLayersAnimating(): Condition<DUMP> =
        Condition("hasLayersAnimating") {
            it.layerState.isAnimating()
        }

    fun isPipWindowLayerSizeMatch(
        layerId: Int
    ): Condition<DUMP> =
        Condition("isPipWindowLayerSizeMatch[layerId=$layerId]") {
            val pipWindow = it.wmState.pinnedWindows
                .firstOrNull { pinnedWindow -> pinnedWindow.layerId == layerId }
                ?: error("Unable to find window with layerId $layerId")
            val windowHeight = pipWindow.frame.height.toFloat()
            val windowWidth = pipWindow.frame.width.toFloat()

            val pipLayer = it.layerState.getLayerById(layerId)
            val layerHeight = pipLayer?.sourceBounds?.height
                ?: error("Unable to find layer with id $layerId")
            val layerWidth = pipLayer.sourceBounds.width

            windowHeight == layerHeight && windowWidth == layerWidth
        }

    fun hasPipWindow(): Condition<DUMP> =
        Condition("hasPipWindow") {
            it.wmState.hasPipWindow()
        }

    fun isImeShown(
        displayId: Int
    ): Condition<DUMP> =
        ConditionList(listOf(
            isImeOnDisplay(displayId),
            isLayerVisible(FlickerComponentName.IME),
            isImeSurfaceShown(),
            isWindowSurfaceShown(FlickerComponentName.IME.toWindowName())
        ))

    private fun isImeOnDisplay(
        displayId: Int
    ): Condition<DUMP> =
        Condition("isImeOnDisplay[$displayId]") {
            it.wmState.inputMethodWindowState?.displayId == displayId
        }

    private fun isImeSurfaceShown():
        Condition<DUMP> =
        Condition("isImeSurfaceShown") {
            it.wmState.inputMethodWindowState?.isSurfaceShown == true
        }

    fun isAppLaunchEnded(taskId: Int):
        Condition<DUMP> =
        Condition("containsVisibleAppLaunchWindow[taskId=$taskId]") { dump ->
            val windowStates = dump.wmState.getRootTask(taskId)?.activities?.flatMap {
                it.children.filterIsInstance<WindowState>()
            }
            windowStates != null && windowStates.none { window ->
                window.attributes.type == PlatformConsts.TYPE_APPLICATION_STARTING &&
                    window.isVisible
            }
        }
}