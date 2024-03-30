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

package com.android.server.wm.traces.common.windowmanager.windows

/**
 * Represents the attributes of a WindowState in the window manager hierarchy
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 */
data class WindowLayoutParams(
    val type: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val horizontalMargin: Float,
    val verticalMargin: Float,
    val gravity: Int,
    val softInputMode: Int,
    val format: Int,
    val windowAnimations: Int,
    val alpha: Float,
    val screenBrightness: Float,
    val buttonBrightness: Float,
    val rotationAnimation: Int,
    val preferredRefreshRate: Float,
    val preferredDisplayModeId: Int,
    val hasSystemUiListeners: Boolean,
    val inputFeatureFlags: Int,
    val userActivityTimeout: Long,
    val colorMode: Int,
    val flags: Int,
    val privateFlags: Int,
    val systemUiVisibilityFlags: Int,
    val subtreeSystemUiVisibilityFlags: Int,
    val appearance: Int,
    val behavior: Int,
    val fitInsetsTypes: Int,
    val fitInsetsSides: Int,
    val fitIgnoreVisibility: Boolean
) {
    val isValidNavBarType: Boolean = this.type == TYPE_NAVIGATION_BAR

    companion object {
        /**
         * @see WindowManager.LayoutParams
         */
        private const val TYPE_NAVIGATION_BAR = 2019
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowLayoutParams) return false

        if (type != other.type) return false
        if (x != other.x) return false
        if (y != other.y) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (horizontalMargin != other.horizontalMargin) return false
        if (verticalMargin != other.verticalMargin) return false
        if (gravity != other.gravity) return false
        if (softInputMode != other.softInputMode) return false
        if (format != other.format) return false
        if (windowAnimations != other.windowAnimations) return false
        if (alpha != other.alpha) return false
        if (screenBrightness != other.screenBrightness) return false
        if (buttonBrightness != other.buttonBrightness) return false
        if (rotationAnimation != other.rotationAnimation) return false
        if (preferredRefreshRate != other.preferredRefreshRate) return false
        if (preferredDisplayModeId != other.preferredDisplayModeId) return false
        if (hasSystemUiListeners != other.hasSystemUiListeners) return false
        if (inputFeatureFlags != other.inputFeatureFlags) return false
        if (userActivityTimeout != other.userActivityTimeout) return false
        if (colorMode != other.colorMode) return false
        if (flags != other.flags) return false
        if (privateFlags != other.privateFlags) return false
        if (systemUiVisibilityFlags != other.systemUiVisibilityFlags) return false
        if (subtreeSystemUiVisibilityFlags != other.subtreeSystemUiVisibilityFlags) return false
        if (appearance != other.appearance) return false
        if (behavior != other.behavior) return false
        if (fitInsetsTypes != other.fitInsetsTypes) return false
        if (fitInsetsSides != other.fitInsetsSides) return false
        if (fitIgnoreVisibility != other.fitIgnoreVisibility) return false
        if (isValidNavBarType != other.isValidNavBarType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + x
        result = 31 * result + y
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + horizontalMargin.hashCode()
        result = 31 * result + verticalMargin.hashCode()
        result = 31 * result + gravity
        result = 31 * result + softInputMode
        result = 31 * result + format
        result = 31 * result + windowAnimations
        result = 31 * result + alpha.hashCode()
        result = 31 * result + screenBrightness.hashCode()
        result = 31 * result + buttonBrightness.hashCode()
        result = 31 * result + rotationAnimation
        result = 31 * result + preferredRefreshRate.hashCode()
        result = 31 * result + preferredDisplayModeId
        result = 31 * result + hasSystemUiListeners.hashCode()
        result = 31 * result + inputFeatureFlags
        result = 31 * result + userActivityTimeout.hashCode()
        result = 31 * result + colorMode
        result = 31 * result + flags
        result = 31 * result + privateFlags
        result = 31 * result + systemUiVisibilityFlags
        result = 31 * result + subtreeSystemUiVisibilityFlags
        result = 31 * result + appearance
        result = 31 * result + behavior
        result = 31 * result + fitInsetsTypes
        result = 31 * result + fitInsetsSides
        result = 31 * result + fitIgnoreVisibility.hashCode()
        result = 31 * result + isValidNavBarType.hashCode()
        return result
    }
}