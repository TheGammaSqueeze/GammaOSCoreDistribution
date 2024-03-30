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

package com.android.server.wm.traces.common.layers

import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.RectF

/**
 * Represents a single Layer trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 **/
open class LayerTraceEntry constructor(
    override val timestamp: Long,
    override val hwcBlob: String,
    override val where: String,
    override val displays: Array<Display>,
    _rootLayers: Array<Layer>
) : BaseLayerTraceEntry() {
    override val flattenedLayers: Array<Layer> = fillFlattenedLayers(_rootLayers)

    private fun fillFlattenedLayers(rootLayers: Array<Layer>): Array<Layer> {
        val opaqueLayers = mutableListOf<Layer>()
        val transparentLayers = mutableListOf<Layer>()
        val layers = mutableListOf<Layer>()
        // some of the flickerlib traces are old and don't have the display data on the trace
        val mainDisplaySize = displays.firstOrNull { !it.isVirtual }?.layerStackSpace ?: Rect.EMPTY
        val roots = rootLayers.fillOcclusionState(
            opaqueLayers, transparentLayers, mainDisplaySize.toRectF()).toMutableList()
        while (roots.isNotEmpty()) {
            val layer = roots.removeAt(0)
            layers.add(layer)
            roots.addAll(layer.children)
        }
        return layers.toTypedArray()
    }

    private fun Array<Layer>.topDownTraversal(): List<Layer> {
        return this
                .sortedBy { it.z }
                .flatMap { it.topDownTraversal() }
    }

    private fun Layer.topDownTraversal(): List<Layer> {
        val traverseList = mutableListOf(this)

        this.children.sortedBy { it.z }
                .forEach { childLayer ->
                    traverseList.addAll(childLayer.topDownTraversal())
                }

        return traverseList
    }

    private fun Array<Layer>.fillOcclusionState(
        opaqueLayers: MutableList<Layer>,
        transparentLayers: MutableList<Layer>,
        displaySize: RectF
    ): Array<Layer> {
        val traversalList = topDownTraversal().reversed()

        traversalList.forEach { layer ->
            val visible = layer.isVisible

            if (visible) {
                val occludedBy = opaqueLayers
                    .filter { it.contains(layer, displaySize) && !it.hasRoundedCorners }
                    .toTypedArray()
                layer.addOccludedBy(occludedBy)
                val partiallyOccludedBy = opaqueLayers
                    .filter { it.overlaps(layer, displaySize) && it !in layer.occludedBy }
                    .toTypedArray()
                layer.addPartiallyOccludedBy(partiallyOccludedBy)
                val coveredBy = transparentLayers
                    .filter { it.overlaps(layer, displaySize) }
                    .toTypedArray()
                layer.addCoveredBy(coveredBy)

                if (layer.isOpaque) {
                    opaqueLayers.add(layer)
                } else {
                    transparentLayers.add(layer)
                }
            }
        }

        return this
    }
}