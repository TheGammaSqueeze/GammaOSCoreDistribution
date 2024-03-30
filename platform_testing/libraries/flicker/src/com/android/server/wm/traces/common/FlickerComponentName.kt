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

import kotlin.text.Regex.Companion.escape

/**
 * Create a new component identifier.
 *
 * This is a version of Android's ComponentName class for flicker. This is necessary because
 * flicker codebase it also compiled into KotlinJS for use into Winscope
 *
 * @param packageName The name of the package that the component exists in.  Can
 * not be null.
 * @param className The name of the class inside of <var>pkg</var> that
 * implements the component.  Can not be null.
 */
data class FlickerComponentName(
    val packageName: String,
    val className: String
) {
    /**
     * Obtains the activity name from the component name.
     *
     * See [ComponentName.toWindowName] for additional information
     */
    fun toActivityName(): String {
        return when {
            packageName.isNotEmpty() && className.isNotEmpty() -> {
                val sb = StringBuilder(packageName.length + className.length)
                appendShortString(sb, packageName, className)
                return sb.toString()
            }
            packageName.isNotEmpty() -> packageName
            className.isNotEmpty() -> className
            else -> error("Component name should have an activity of class name")
        }
    }

    /**
     * Obtains the window name from the component name.
     *
     * [ComponentName] builds the string representation as PKG/CLASS, however this doesn't
     * work for system components such as IME, NavBar and StatusBar, Toast.
     *
     * If the component doesn't have a package name, assume it's a system component and return only
     * the class name
     */
    fun toWindowName(): String {
        return when {
            packageName.isNotEmpty() && className.isNotEmpty() -> "$packageName/$className"
            packageName.isNotEmpty() -> packageName
            className.isNotEmpty() -> className
            else -> error("Component name should have an activity of class name")
        }
    }

    fun toShortWindowName(): String {
        return when {
            packageName.isNotEmpty() && className.isNotEmpty() ->
                "$packageName/${className.removePrefix(packageName)}"
            packageName.isNotEmpty() -> packageName
            className.isNotEmpty() -> className
            else -> error("Component name should have an activity of class name")
        }
    }

    /**
     * Obtains the layer name from the component name.
     *
     * See [toWindowName] for additional information
     */
    fun toLayerName(): String {
        var result = this.toWindowName()
        if (result.contains("/") && !result.contains("#")) {
            result = "$result#"
        }

        return result
    }

    fun toActivityRecordFilter(): Regex {
        return Regex("ActivityRecord\\{.*${escape(this.toShortWindowName())}")
    }

    private fun appendShortString(sb: StringBuilder, packageName: String, className: String) {
        sb.append(packageName).append('/')
        appendShortClassName(sb, packageName, className)
    }

    private fun appendShortClassName(sb: StringBuilder, packageName: String, className: String) {
        if (className.startsWith(packageName)) {
            val packageNameLength = packageName.length
            val classNameLength = className.length
            if (classNameLength > packageNameLength && className[packageNameLength] == '.') {
                sb.append(className, packageNameLength, classNameLength)
                return
            }
        }
        sb.append(className)
    }

    companion object {
        val NAV_BAR = FlickerComponentName("", "NavigationBar0")
        val STATUS_BAR = FlickerComponentName("", "StatusBar")
        val ROTATION = FlickerComponentName("", "RotationLayer")
        val BACK_SURFACE = FlickerComponentName("", "BackColorSurface")
        val IME = FlickerComponentName("", "InputMethod")
        val IME_SNAPSHOT = FlickerComponentName("", "IME-snapshot-surface")
        val SPLASH_SCREEN = FlickerComponentName("", "Splash Screen")
        val SNAPSHOT = FlickerComponentName("", "SnapshotStartingWindow")
        val LETTERBOX = FlickerComponentName("", "Letterbox")
        val WALLPAPER_BBQ_WRAPPER =
                FlickerComponentName("", "Wallpaper BBQ wrapper")
        val PIP_CONTENT_OVERLAY = FlickerComponentName("", "PipContentOverlay")

        fun unflattenFromString(str: String): FlickerComponentName {
            val sep = str.indexOf('/')
            if (sep < 0 || sep + 1 >= str.length) {
                error("Missing package/class separator")
            }
            val pkg = str.substring(0, sep)
            var cls = str.substring(sep + 1)
            if (cls.isNotEmpty() && cls[0] == '.') {
                cls = pkg + cls
            }
            return FlickerComponentName(pkg, cls)
        }
    }
}