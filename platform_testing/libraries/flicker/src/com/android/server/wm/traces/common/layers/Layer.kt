/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.server.wm.traces.common.ActiveBuffer
import com.android.server.wm.traces.common.Color
import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.RectF
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.layers.Transform.Companion.isFlagSet

/**
 * Represents a single layer with links to its parent and child layers.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 **/
data class Layer(
    val name: String,
    val id: Int,
    val parentId: Int,
    val z: Int,
    val visibleRegion: Region?,
    val activeBuffer: ActiveBuffer,
    val flags: Int,
    val bounds: RectF,
    val color: Color,
    private val _isOpaque: Boolean,
    val shadowRadius: Float,
    val cornerRadius: Float,
    val type: String,
    private val _screenBounds: RectF?,
    val transform: Transform,
    val sourceBounds: RectF,
    val currFrame: Long,
    val effectiveScalingMode: Int,
    val bufferTransform: Transform,
    val hwcCompositionType: Int,
    val hwcCrop: RectF,
    val hwcFrame: Rect,
    val backgroundBlurRadius: Int,
    val crop: Rect?,
    val isRelativeOf: Boolean,
    val zOrderRelativeOfId: Int,
    val stackId: Int
) {
    val stableId: String = "$type $id $name"
    var parent: Layer? = null
    var zOrderRelativeOf: Layer? = null
    var zOrderRelativeParentOf: Int = 0

    /**
     * Checks if the [Layer] is a root layer in the hierarchy
     *
     * @return
     */
    val isRootLayer: Boolean get() = parent == null

    private val _children = mutableListOf<Layer>()
    private val _occludedBy = mutableListOf<Layer>()
    private val _partiallyOccludedBy = mutableListOf<Layer>()
    private val _coveredBy = mutableListOf<Layer>()
    val children: Array<Layer>
        get() = _children.toTypedArray()
    val occludedBy: Array<Layer>
        get() = _occludedBy.toTypedArray()
    val partiallyOccludedBy: Array<Layer>
        get() = _partiallyOccludedBy.toTypedArray()
    val coveredBy: Array<Layer>
        get() = _coveredBy.toTypedArray()
    var isMissing: Boolean = false
        internal set

    val isScaling: Boolean
        get() = isTransformFlagSet(Transform.SCALE_VAL)
    val isTranslating: Boolean
        get() = isTransformFlagSet(Transform.TRANSLATE_VAL)
    val isRotating: Boolean
        get() = isTransformFlagSet(Transform.ROTATE_VAL)

    private fun isTransformFlagSet(transform: Int): Boolean =
            this.transform.type?.isFlagSet(transform) ?: false

    /**
     * Checks if the layer's active buffer is empty
     *
     * An active buffer is empty if it is not in the proto or if its height or width are 0
     *
     * @return
     */
    val isActiveBufferEmpty: Boolean get() = activeBuffer.isEmpty

    /**
     * Checks if the layer is hidden, that is, if its flags contain 0x1 (FLAG_HIDDEN)
     *
     * @return
     */
    val isHiddenByPolicy: Boolean
        get() {
            return (flags and /* FLAG_HIDDEN */0x1) != 0x0 ||
                // offscreen layer root has a unique layer id
                id == 0x7FFFFFFD
        }

    /**
     * Checks if the layer is visible.
     *
     * A layer is visible if:
     * - it has an active buffer or has effects
     * - is not hidden
     * - is not transparent
     * - not occluded by other layers
     *
     * @return
     */
    val isVisible: Boolean
        get() {
            return when {
                isHiddenByParent -> false
                isHiddenByPolicy -> false
                isActiveBufferEmpty && !hasEffects -> false
                !fillsColor -> false
                occludedBy.isNotEmpty() -> false
                visibleRegion?.isEmpty ?: false -> false
                else -> !bounds.isEmpty
            }
        }

    val isOpaque: Boolean = if (color.a != 1.0f) false else _isOpaque

    /**
     * Checks if the [Layer] has a color
     *
     * @return
     */
    val fillsColor: Boolean get() = color.isNotEmpty

    /**
     * Checks if the [Layer] draws a shadow
     *
     * @return
     */
    val drawsShadows: Boolean get() = shadowRadius > 0

    /**
     * Checks if the [Layer] has blur
     *
     * @return
     */
    val hasBlur: Boolean get() = backgroundBlurRadius > 0

    /**
     * Checks if the [Layer] has rounded corners
     *
     * @return
     */
    val hasRoundedCorners: Boolean get() = cornerRadius > 0

    /**
     * Checks if the [Layer] draws has effects, which include:
     * - is a color layer
     * - is an effects layers which [fillsColor] or [drawsShadows]
     *
     * @return
     */
    val hasEffects: Boolean
        get() {
            // Support previous color layer
            if (isColorLayer) {
                return true
            }

            // Support newer effect layer
            return isEffectLayer && (fillsColor || drawsShadows)
        }

    /**
     * Checks if the [Layer] type is BufferStateLayer or BufferQueueLayer
     *
     * @return
     */
    val isBufferLayer: Boolean
        get() = type == "BufferStateLayer" || type == "BufferQueueLayer"

    /**
     * Checks if the [Layer] type is ColorLayer
     *
     * @return
     */
    val isColorLayer: Boolean get() = type == "ColorLayer"

    /**
     * Checks if the [Layer] type is ContainerLayer
     *
     * @return
     */
    val isContainerLayer: Boolean get() = type == "ContainerLayer"

    /**
     * Checks if the [Layer] type is EffectLayer
     *
     * @return
     */
    val isEffectLayer: Boolean get() = type == "EffectLayer"

    /**
     * Checks if the [Layer] is hidden by its parent
     *
     * @return
     */
    val isHiddenByParent: Boolean
        get() = !isRootLayer &&
            (parent?.isHiddenByPolicy == true || parent?.isHiddenByParent == true)

    /**
     * Gets a description of why the layer is (in)visible
     *
     * @return
     */
    val visibilityReason: Array<String>
        get() {
            if (isVisible) {
                return emptyArray()
            }
            val reasons = mutableListOf<String>()
            if (isContainerLayer) reasons.add("ContainerLayer")
            if (isHiddenByPolicy) reasons.add("Flag is hidden")
            if (isHiddenByParent) reasons.add("Hidden by parent ${parent?.name}")
            if (isBufferLayer && isActiveBufferEmpty) reasons.add("Buffer is empty")
            if (color.isEmpty) reasons.add("Alpha is 0")
            if (crop?.isEmpty == true) reasons.add("Crop is 0x0")
            if (bounds.isEmpty) reasons.add("Bounds is 0x0")
            if (!transform.isValid) reasons.add("Transform is invalid")
            if (isRelativeOf && zOrderRelativeOf == null) {
                reasons.add("RelativeOf layer has been removed")
            }
            if (isEffectLayer && !fillsColor && !drawsShadows && !hasBlur) {
                reasons.add("Effect layer does not have color fill, shadow or blur")
            }
            if (_occludedBy.isNotEmpty()) {
                val occludedByIds = _occludedBy.joinToString(", ") { it.id.toString() }
                reasons.add("Layer is occluded by: $occludedByIds")
            }
            if (visibleRegion?.isEmpty == true) {
                reasons.add("Visible region calculated by Composition Engine is empty")
            }
            if (reasons.isEmpty()) reasons.add("Unknown")
            return reasons.toTypedArray()
        }

    val screenBounds: RectF = when {
        visibleRegion?.isNotEmpty == true -> visibleRegion.toRectF()
        _screenBounds != null -> _screenBounds
        else -> transform.apply(bounds)
    }

    val absoluteZ: String
        get() {
            val zOrderRelativeOf = zOrderRelativeOf
            return buildString {
                when {
                    zOrderRelativeOf != null -> append(zOrderRelativeOf.absoluteZ).append(",")
                    parent != null -> append(parent?.absoluteZ).append(",")
                }
                append(z)
            }
        }

    /**
     * Returns true iff the [innerLayer] screen bounds are inside or equal to this layer's
     * [screenBounds] and neither layers are rotating.
     */
    fun contains(innerLayer: Layer, crop: RectF = RectF.EMPTY): Boolean {
        return if (!this.transform.isSimpleRotation || !innerLayer.transform.isSimpleRotation) {
            false
        } else {
            val thisBounds: RectF
            val innerLayerBounds: RectF
            if (crop.isNotEmpty) {
                thisBounds = this.screenBounds.crop(crop)
                innerLayerBounds = innerLayer.screenBounds.crop(crop)
            } else {
                thisBounds = this.screenBounds
                innerLayerBounds = innerLayer.screenBounds
            }
            thisBounds.contains(innerLayerBounds)
        }
    }

    fun addChild(childLayer: Layer) {
        _children.add(childLayer)
    }

    fun addOccludedBy(layers: Array<Layer>) {
        _occludedBy.addAll(layers)
    }

    fun addPartiallyOccludedBy(layers: Array<Layer>) {
        _partiallyOccludedBy.addAll(layers)
    }

    fun addCoveredBy(layers: Array<Layer>) {
        _coveredBy.addAll(layers)
    }

    fun overlaps(other: Layer, crop: RectF = RectF.EMPTY): Boolean {
        val thisBounds: RectF
        val otherBounds: RectF
        if (crop.isNotEmpty) {
            thisBounds = this.screenBounds.crop(crop)
            otherBounds = other.screenBounds.crop(crop)
        } else {
            thisBounds = this.screenBounds
            otherBounds = other.screenBounds
        }
        return !thisBounds.intersection(otherBounds).isEmpty
    }

    override fun toString(): String {
        return buildString {
            append(name)

            if (activeBuffer.isNotEmpty) {
                append(" buffer:$activeBuffer")
                append(" frame#$currFrame")
            }

            if (isVisible) {
                append(" visible:$visibleRegion")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Layer) return false

        if (name != other.name) return false
        if (id != other.id) return false
        if (parentId != other.parentId) return false
        if (z != other.z) return false
        if (visibleRegion != other.visibleRegion) return false
        if (activeBuffer != other.activeBuffer) return false
        if (flags != other.flags) return false
        if (bounds != other.bounds) return false
        if (color != other.color) return false
        if (shadowRadius != other.shadowRadius) return false
        if (cornerRadius != other.cornerRadius) return false
        if (type != other.type) return false
        if (transform != other.transform) return false
        if (sourceBounds != other.sourceBounds) return false
        if (currFrame != other.currFrame) return false
        if (effectiveScalingMode != other.effectiveScalingMode) return false
        if (bufferTransform != other.bufferTransform) return false
        if (hwcCompositionType != other.hwcCompositionType) return false
        if (hwcCrop != other.hwcCrop) return false
        if (hwcFrame != other.hwcFrame) return false
        if (backgroundBlurRadius != other.backgroundBlurRadius) return false
        if (crop != other.crop) return false
        if (isRelativeOf != other.isRelativeOf) return false
        if (zOrderRelativeOfId != other.zOrderRelativeOfId) return false
        if (stableId != other.stableId) return false
        if (parent != other.parent) return false
        if (zOrderRelativeOf != other.zOrderRelativeOf) return false
        if (zOrderRelativeParentOf != other.zOrderRelativeParentOf) return false
        if (isMissing != other.isMissing) return false
        if (isOpaque != other.isOpaque) return false
        if (screenBounds != other.screenBounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + id
        result = 31 * result + parentId
        result = 31 * result + z
        result = 31 * result + (visibleRegion?.hashCode() ?: 0)
        result = 31 * result + activeBuffer.hashCode()
        result = 31 * result + flags
        result = 31 * result + bounds.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + shadowRadius.hashCode()
        result = 31 * result + cornerRadius.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + transform.hashCode()
        result = 31 * result + sourceBounds.hashCode()
        result = 31 * result + currFrame.hashCode()
        result = 31 * result + effectiveScalingMode
        result = 31 * result + bufferTransform.hashCode()
        result = 31 * result + hwcCompositionType
        result = 31 * result + hwcCrop.hashCode()
        result = 31 * result + hwcFrame.hashCode()
        result = 31 * result + backgroundBlurRadius
        result = 31 * result + (crop?.hashCode() ?: 0)
        result = 31 * result + isRelativeOf.hashCode()
        result = 31 * result + zOrderRelativeOfId
        result = 31 * result + stableId.hashCode()
        result = 31 * result + (parent?.hashCode() ?: 0)
        result = 31 * result + (zOrderRelativeOf?.hashCode() ?: 0)
        result = 31 * result + zOrderRelativeParentOf
        result = 31 * result + isMissing.hashCode()
        result = 31 * result + isOpaque.hashCode()
        result = 31 * result + screenBounds.hashCode()
        return result
    }
}