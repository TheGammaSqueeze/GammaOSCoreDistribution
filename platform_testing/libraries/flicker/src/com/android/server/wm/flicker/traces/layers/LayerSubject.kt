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
package com.android.server.wm.flicker.traces.layers

import android.graphics.Point
import com.android.server.wm.flicker.assertions.Assertion
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.traces.FlickerFailureStrategy
import com.android.server.wm.flicker.traces.region.RegionSubject
import com.android.server.wm.traces.common.Size
import com.android.server.wm.traces.common.layers.Layer
import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.FailureStrategy
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Subject.Factory

/**
 * Truth subject for [Layer] objects, used to make assertions over behaviors that occur on a
 * single layer of a SurfaceFlinger state.
 *
 * To make assertions over a layer from a state it is recommended to create a subject
 * using [LayerTraceEntrySubject.layer](layerName)
 *
 * Alternatively, it is also possible to use [LayerSubject.assertThat](myLayer) or
 * Truth.assertAbout([LayerSubject.getFactory]), however they will provide less debug
 * information because it uses Truth's default [FailureStrategy].
 *
 * Example:
 *    val trace = LayersTraceParser.parseFromTrace(myTraceFile)
 *    val subject = LayersTraceSubject.assertThat(trace).first()
 *        .layer("ValidLayer")
 *        .exists()
 *        .hasBufferSize(BUFFER_SIZE)
 *        .invoke { myCustomAssertion(this) }
 */
class LayerSubject private constructor(
    fm: FailureMetadata,
    override val parent: FlickerSubject,
    override val timestamp: Long,
    val layer: Layer?,
    private val layerName: String? = null
) : FlickerSubject(fm, layer) {
    val isEmpty: Boolean get() = layer == null
    val isNotEmpty: Boolean get() = !isEmpty
    val isVisible: Boolean get() = layer?.isVisible == true
    val isInvisible: Boolean get() = layer?.isVisible == false
    val name: String get() = layer?.name ?: ""

    /**
     * Visible region calculated by the Composition Engine
     */
    val visibleRegion: RegionSubject get() =
        RegionSubject.assertThat(layer?.visibleRegion, this, timestamp)

    /**
     * Visible region calculated by the Composition Engine (when available) or calculated
     * based on the layer bounds and transform
     */
    val screenBounds: RegionSubject get() =
        RegionSubject.assertThat(layer?.screenBounds, this, timestamp)

    override val selfFacts = if (layer != null) {
        listOf(Fact.fact("Frame", layer.currFrame), Fact.fact("Layer", layer.name))
    } else {
        listOf(Fact.fact("Layer name", layerName))
    }

    /**
     * If the [layer] exists, executes a custom [assertion] on the current subject
     */
    operator fun invoke(assertion: Assertion<Layer>): LayerSubject = apply {
        layer ?: return exists()
        assertion(this.layer)
    }

    /**
     * Asserts that current subject doesn't exist in the layer hierarchy
     */
    fun doesNotExist(): LayerSubject = apply {
        check("doesNotExist").that(layer).isNull()
    }

    /**
     * Asserts that current subject exists in the layer hierarchy
     */
    fun exists(): LayerSubject = apply {
        check("$layerName does not exists").that(layer).isNotNull()
    }

    @Deprecated("Prefer hasBufferSize(bounds)")
    fun hasBufferSize(size: Point): LayerSubject = apply {
        val bounds = Size(size.x, size.y)
        hasBufferSize(bounds)
    }

    /**
     * Asserts that current subject has an [Layer.activeBuffer] with width equals to [Point.x]
     * and height equals to [Point.y]
     *
     * @param size expected buffer size
     */
    fun hasBufferSize(size: Size): LayerSubject = apply {
        layer ?: return exists()
        val bufferSize = Size(layer.activeBuffer.width, layer.activeBuffer.height)
        check("Incorrect buffer size").that(bufferSize).isEqualTo(size)
    }

    /**
     * Asserts that current subject has an [Layer.screenBounds] with width equals to [Point.x]
     * and height equals to [Point.y]
     *
     * @param size expected layer bounds size
     */
    fun hasLayerSize(size: Point): LayerSubject = apply {
        layer ?: return exists()
        val layerSize = Point(layer.screenBounds.width.toInt(), layer.screenBounds.height.toInt())
        check("Incorrect number of layers").that(layerSize).isEqualTo(size)
    }

    /**
     * Asserts that current subject has an [Layer.effectiveScalingMode] equals to
     * [expectedScalingMode]
     */
    fun hasScalingMode(expectedScalingMode: Int): LayerSubject = apply {
        layer ?: return exists()
        val actualScalingMode = layer.effectiveScalingMode
        check("Incorrect scaling mode").that(actualScalingMode).isEqualTo(expectedScalingMode)
    }

    /**
     * Asserts that current subject has an [Layer.bufferTransform] orientation equals to
     * [expectedOrientation]
     */
    fun hasBufferOrientation(expectedOrientation: Int): LayerSubject = apply {
        layer ?: return exists()
        // see Transform::getOrientation
        val bufferTransformType = layer.bufferTransform.type ?: 0
        val actualOrientation = (bufferTransformType shr 8) and 0xFF
        check("hasBufferTransformOrientation()")
                .that(actualOrientation).isEqualTo(expectedOrientation)
    }

    override fun toString(): String {
        return "Layer:${layer?.name} frame#${layer?.currFrame}"
    }

    companion object {
        /**
         * Boiler-plate Subject.Factory for LayerSubject
         */
        @JvmStatic
        fun getFactory(parent: FlickerSubject, timestamp: Long, name: String?) =
            Factory { fm: FailureMetadata, subject: Layer? ->
                LayerSubject(fm, parent, timestamp, subject, name)
            }

        /**
         * User-defined parent point for existing layers
         */
        @JvmStatic
        fun assertThat(
            layer: Layer?,
            parent: FlickerSubject,
            timestamp: Long
        ): LayerSubject {
            val strategy = FlickerFailureStrategy()
            val subject = StandardSubjectBuilder.forCustomFailureStrategy(strategy)
                .about(getFactory(parent, timestamp, name = null))
                .that(layer) as LayerSubject
            strategy.init(subject)
            return subject
        }

        /**
         * User-defined parent point for non existing layers
         */
        @JvmStatic
        internal fun assertThat(
            name: String,
            parent: FlickerSubject,
            timestamp: Long
        ): LayerSubject {
            val strategy = FlickerFailureStrategy()
            val subject = StandardSubjectBuilder.forCustomFailureStrategy(strategy)
                .about(getFactory(parent, timestamp, name))
                .that(null) as LayerSubject
            strategy.init(subject)
            return subject
        }
    }
}