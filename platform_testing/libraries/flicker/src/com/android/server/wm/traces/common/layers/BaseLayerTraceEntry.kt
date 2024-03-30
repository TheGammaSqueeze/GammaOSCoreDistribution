/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wm.traces.common.layers

import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.ITraceEntry
import com.android.server.wm.traces.common.prettyTimestamp

abstract class BaseLayerTraceEntry : ITraceEntry {
    abstract val hwcBlob: String
    abstract val where: String
    abstract val displays: Array<Display>
    val stableId: String get() = this::class.simpleName ?: error("Unable to determine class")
    val name: String get() = prettyTimestamp(timestamp)

    abstract val flattenedLayers: Array<Layer>
    val visibleLayers: Array<Layer>
        get() = flattenedLayers.filter { it.isVisible }.toTypedArray()

    // for winscope
    val isVisible: Boolean = true
    val children: Array<Layer>
        get() = flattenedLayers.filter { it.isRootLayer }.toTypedArray()

    fun getLayerWithBuffer(name: String): Layer? {
        return flattenedLayers.firstOrNull {
            it.name.contains(name) && it.activeBuffer.isNotEmpty
        }
    }

    fun getLayerById(layerId: Int): Layer? = this.flattenedLayers.firstOrNull { it.id == layerId }

    /**
     * Checks if any layer in the screen is animating.
     *
     * The screen is animating when a layer is not simple rotation, of when the pip overlay
     * layer is visible
     */
    fun isAnimating(windowName: String = ""): Boolean {
        val layers = visibleLayers.filter { it.name.contains(windowName) }
        val layersAnimating = layers.any { layer -> !layer.transform.isSimpleRotation }
        val pipAnimating = isVisible(FlickerComponentName.PIP_CONTENT_OVERLAY.toWindowName())
        return layersAnimating || pipAnimating
    }

    /**
     * Check if at least one window which matches provided window name is visible.
     */
    fun isVisible(windowName: String): Boolean =
        visibleLayers.any { it.name.contains(windowName) }

    fun asTrace(): LayersTrace = LayersTrace(arrayOf(this))

    override fun toString(): String {
        return "${prettyTimestamp(timestamp)} (timestamp=$timestamp)"
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseLayerTraceEntry && other.timestamp == this.timestamp
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + hwcBlob.hashCode()
        result = 31 * result + where.hashCode()
        result = 31 * result + displays.contentHashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + flattenedLayers.contentHashCode()
        return result
    }
}