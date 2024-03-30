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

import com.android.server.wm.flicker.assertions.Assertion
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.traces.FlickerFailureStrategy
import com.android.server.wm.flicker.traces.FlickerSubjectException
import com.android.server.wm.flicker.traces.region.RegionSubject
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.layers.Layer
import com.android.server.wm.traces.common.layers.BaseLayerTraceEntry
import com.android.server.wm.traces.common.layers.LayersTrace
import com.google.common.truth.ExpectFailure
import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.FailureStrategy
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Subject

/**
 * Truth subject for [BaseLayerTraceEntry] objects, used to make assertions over behaviors that
 * occur on a single SurfaceFlinger state.
 *
 * To make assertions over a specific state from a trace it is recommended to create a subject
 * using [LayersTraceSubject.assertThat](myTrace) and select the specific state using:
 *     [LayersTraceSubject.first]
 *     [LayersTraceSubject.last]
 *     [LayersTraceSubject.entry]
 *
 * Alternatively, it is also possible to use [LayerTraceEntrySubject.assertThat](myState) or
 * Truth.assertAbout([LayerTraceEntrySubject.getFactory]), however they will provide less debug
 * information because it uses Truth's default [FailureStrategy].
 *
 * Example:
 *    val trace = LayersTraceParser.parseFromTrace(myTraceFile)
 *    val subject = LayersTraceSubject.assertThat(trace).first()
 *        .contains("ValidLayer")
 *        .notContains("ImaginaryLayer")
 *        .coversExactly(DISPLAY_AREA)
 *        .invoke { myCustomAssertion(this) }
 */
class LayerTraceEntrySubject private constructor(
    fm: FailureMetadata,
    val entry: BaseLayerTraceEntry,
    val trace: LayersTrace?,
    override val parent: FlickerSubject?
) : FlickerSubject(fm, entry) {
    override val timestamp: Long get() = entry.timestamp
    override val selfFacts = listOf(Fact.fact("Entry", entry))

    val subjects by lazy {
        entry.flattenedLayers.map { LayerSubject.assertThat(it, this, timestamp) }
    }

    /**
     * Executes a custom [assertion] on the current subject
     */
    operator fun invoke(assertion: Assertion<BaseLayerTraceEntry>): LayerTraceEntrySubject =
        apply {
            assertion(this.entry)
        }

    /**
     * Asserts that the current SurfaceFlinger state doesn't contain layers
     */
    fun isEmpty(): LayerTraceEntrySubject = apply {
        check("Entry should not be empty")
            .that(entry.flattenedLayers)
            .isEmpty()
    }

    /**
     * Asserts that the current SurfaceFlinger state contains layers
     */
    fun isNotEmpty(): LayerTraceEntrySubject = apply {
        check("Entry should not be empty")
            .that(entry.flattenedLayers)
            .isNotEmpty()
    }

    /**
     * Obtains the region occupied by all layers with name containing [components]
     *
     * @param components Components to search for
     * @param useCompositionEngineRegionOnly If true, uses only the region calculated from the
     *   Composition Engine (CE) -- visibleRegion in the proto definition. Otherwise calculates
     *   the visible region when the information is not available from the CE
     */
    @JvmOverloads
    fun visibleRegion(
        vararg components: FlickerComponentName,
        useCompositionEngineRegionOnly: Boolean = true
    ): RegionSubject {
        val layerNames = components.map { it.toLayerName() }
        val selectedLayers = if (components.isEmpty()) {
            // No filters so use all subjects
            subjects
        } else {
            subjects.filter {
                subject -> layerNames.any {
                    layerName -> subject.name.contains(layerName)
                }
            }
        }

        if (selectedLayers.isEmpty()) {
            val str = if (layerNames.isNotEmpty()) layerNames.joinToString() else "<any>"
            fail(listOf(
                Fact.fact(ASSERTION_TAG, "visibleRegion($str)"),
                Fact.fact("Use composition engine region", useCompositionEngineRegionOnly),
                Fact.fact("Could not find layers", str))
            )
        }

        val visibleLayers = selectedLayers.filter { it.isVisible }
        return if (useCompositionEngineRegionOnly) {
            val visibleAreas = visibleLayers.mapNotNull { it.layer?.visibleRegion }.toTypedArray()
            RegionSubject.assertThat(visibleAreas, this, timestamp)
        } else {
            val visibleAreas = visibleLayers.mapNotNull { it.layer?.screenBounds }.toTypedArray()
            RegionSubject.assertThat(visibleAreas, this, timestamp)
        }
    }

    /**
     * Asserts the state contains a [Layer] with [Layer.name] containing [component].
     *
     * @param component Name of the layers to search
     */
    fun contains(component: FlickerComponentName): LayerTraceEntrySubject = apply {
        val layerName = component.toLayerName()
        val found = entry.flattenedLayers.any { it.name.contains(layerName) }
        if (!found) {
            fail(Fact.fact(ASSERTION_TAG, "contains(${component.toLayerName()})"),
                Fact.fact("Could not find", layerName))
        }
    }

    /**
     * Asserts the state doesn't contain a [Layer] with [Layer.name] containing any of
     *
     * @param component Name of the layers to search
     */
    fun notContains(component: FlickerComponentName): LayerTraceEntrySubject = apply {
        val layerName = component.toLayerName()
        val foundEntry = subjects.firstOrNull { it.name.contains(layerName) }
        foundEntry?.fail(Fact.fact(ASSERTION_TAG, "notContains(${component.toLayerName()})"),
            Fact.fact("Could find", foundEntry))
    }

    /**
     * Asserts that a [Layer] with [Layer.name] containing [component] is visible.
     *
     * @param component Name of the layers to search
     */
    fun isVisible(component: FlickerComponentName): LayerTraceEntrySubject = apply {
        contains(component)
        var target: FlickerSubject? = null
        var reason = listOf<Fact>()
        val layerName = component.toLayerName()
        val filteredLayers = subjects
            .filter { it.name.contains(layerName) }
        for (layer in filteredLayers) {
            if (layer.layer?.isHiddenByParent == true) {
                reason = listOf(Fact.fact("Hidden by parent", layer.layer.parent?.name))
                target = layer
                continue
            }
            if (layer.isInvisible) {
                reason = layer.layer?.visibilityReason
                    ?.map { Fact.fact("Is Invisible", it) }
                    ?: emptyList()
                target = layer
                continue
            }
            reason = emptyList()
            target = null
            break
        }

        if (reason.isNotEmpty()) {
            target?.fail(
                Fact.fact(ASSERTION_TAG, "isVisible(${component.toLayerName()})"),
                *reason.toTypedArray())
        }
    }

    /**
     * Asserts that a [Layer] with [Layer.name] containing [component] doesn't exist or
     * is invisible.
     *
     * @param component Name of the layers to search
     */
    fun isInvisible(component: FlickerComponentName): LayerTraceEntrySubject = apply {
        try {
            isVisible(component)
        } catch (e: FlickerSubjectException) {
            val cause = e.cause
            require(cause is AssertionError)
            ExpectFailure.assertThat(cause).factKeys().isNotEmpty()
            return@apply
        }
        val layerName = component.toLayerName()
        val foundEntry = subjects
                .firstOrNull { it.name.contains(layerName) && it.isVisible }
        foundEntry?.fail(Fact.fact(ASSERTION_TAG, "isInvisible(${component.toLayerName()})"),
            Fact.fact("Is visible", foundEntry))
    }

    /**
     * Asserts that the entry contains a visible splash screen [Layer] for a [layer] with
     * [Layer.name] containing any of [component].
     *
     * @param component Name of the layer to search
     */
    fun isSplashScreenVisibleFor(component: FlickerComponentName): LayerTraceEntrySubject = apply {
        var target: FlickerSubject? = null
        var reason: Fact? = null
        val layerActivityRecordFilter = component.toActivityRecordFilter()
        val filteredLayers = subjects
                .filter { layerActivityRecordFilter.containsMatchIn(it.name) }

        if (filteredLayers.isEmpty()) {
            fail(Fact.fact(ASSERTION_TAG, "isSplashScreenVisibleFor(${component.toLayerName()})"),
                Fact.fact("Could not find Activity Record layer", component.toShortWindowName()))
            return this
        }

        // Check the matched activity record layers for containing splash screens
        for (layer in filteredLayers) {
            val splashScreenContainers =
                layer.layer?.children?.filter { it.name.contains("Splash Screen") }
            val splashScreenLayers = splashScreenContainers?.flatMap {
                it.children.filter { childLayer ->
                    childLayer.name.contains("Splash Screen")
                }
            }

            if (splashScreenLayers?.all { it.isHiddenByParent || !it.isVisible } ?: true) {
                reason = Fact.fact("No splash screen visible for", layer.name)
                target = layer
                continue
            }
            reason = null
            target = null
            break
        }

        reason?.run {
            target?.fail(
                Fact.fact(ASSERTION_TAG, "isSplashScreenVisibleFor(${component.toLayerName()})"),
                reason)
        }
    }

    /**
     * Obtains a [LayerSubject] for the first occurrence of a [Layer] with [Layer.name]
     * containing [component].
     * Always returns a subject, event when the layer doesn't exist. To verify if layer
     * actually exists in the hierarchy use [LayerSubject.exists] or [LayerSubject.doesNotExist]
     *
     * @return LayerSubject that can be used to make assertions on a single layer matching
     */
    fun layer(component: FlickerComponentName): LayerSubject {
        val name = component.toLayerName()
        return layer {
            it.name.contains(name)
        }
    }

    /**
     * Obtains a [LayerSubject] for the first occurrence of a [Layer] with [Layer.name]
     * containing [name] in [frameNumber].
     *
     * Always returns a subject, event when the layer doesn't exist. To verify if layer
     * actually exists in the hierarchy use [LayerSubject.exists] or [LayerSubject.doesNotExist]
     *
     * @return LayerSubject that can be used to make assertions on a single layer matching
     * [name] and [frameNumber].
     */
    fun layer(name: String, frameNumber: Long): LayerSubject {
        return layer(name) {
            it.name.contains(name) && it.currFrame == frameNumber
        }
    }

    /**
     * Obtains a [LayerSubject] for the first occurrence of a [Layer] matching [predicate]
     *
     * Always returns a subject, event when the layer doesn't exist. To verify if layer
     * actually exists in the hierarchy use [LayerSubject.exists] or [LayerSubject.doesNotExist]
     *
     * @param predicate to search for a layer
     * @param name Name of the subject to use when not found (optional)
     *
     * @return [LayerSubject] that can be used to make assertions
     */
    @JvmOverloads
    fun layer(name: String = "", predicate: (Layer) -> Boolean): LayerSubject {
        return subjects.firstOrNull {
            it.layer?.run { predicate(this) } ?: false
        } ?: LayerSubject.assertThat(name, this, timestamp)
    }

    override fun toString(): String {
        return "LayerTraceEntrySubject($entry)"
    }

    companion object {
        /**
         * Boiler-plate Subject.Factory for LayersTraceSubject
         */
        private fun getFactory(
            trace: LayersTrace?,
            parent: FlickerSubject?
        ): Factory<Subject, BaseLayerTraceEntry> =
            Factory { fm, subject -> LayerTraceEntrySubject(fm, subject, trace, parent) }

        /**
         * Creates a [LayerTraceEntrySubject] to representing a SurfaceFlinger state[entry],
         * which can be used to make assertions.
         *
         * @param entry SurfaceFlinger trace entry
         * @param parent Trace that contains this entry (optional)
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(
            entry: BaseLayerTraceEntry,
            trace: LayersTrace? = null,
            parent: FlickerSubject? = null
        ): LayerTraceEntrySubject {
            val strategy = FlickerFailureStrategy()
            val subject = StandardSubjectBuilder.forCustomFailureStrategy(strategy)
                .about(getFactory(trace, parent))
                .that(entry) as LayerTraceEntrySubject
            strategy.init(subject)
            return subject
        }

        /**
         * Static method for getting the subject factory (for use with assertAbout())
         */
        @JvmStatic
        @JvmOverloads
        fun entries(
            trace: LayersTrace? = null,
            parent: FlickerSubject? = null
        ): Factory<Subject, BaseLayerTraceEntry> = getFactory(trace, parent)
    }
}