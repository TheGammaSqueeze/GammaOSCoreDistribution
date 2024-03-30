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

package com.android.permissioncontroller.permission.ui.widget

import android.content.Context
import android.text.Html
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.KotlinUtils

class SafetyProtectionSectionView : LinearLayout {

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {}

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {}

    init {
        gravity = Gravity.CENTER
        orientation = HORIZONTAL
        visibility = if (KotlinUtils.shouldShowSafetyProtectionResources(context)) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (KotlinUtils.shouldShowSafetyProtectionResources(context)) {
            LayoutInflater.from(context).inflate(R.layout.safety_protection_section, this)
            val safetyProtectionDisplayTextView =
                requireViewById<TextView>(R.id.safety_protection_display_text)
            safetyProtectionDisplayTextView!!.setText(Html.fromHtml(
                context.getString(android.R.string.safety_protection_display_text), 0))
        }
    }
}