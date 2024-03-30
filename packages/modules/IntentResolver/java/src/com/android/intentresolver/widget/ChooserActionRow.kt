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

package com.android.intentresolver.widget

import android.annotation.LayoutRes
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import com.android.intentresolver.R
import com.android.intentresolver.widget.ActionRow.Action

class ChooserActionRow : LinearLayout, ActionRow {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        orientation = HORIZONTAL
    }

    @LayoutRes
    private val itemLayout = R.layout.chooser_action_button
    private val itemMargin =
        context.resources.getDimensionPixelSize(R.dimen.resolver_icon_margin) / 2
    private var actions: List<Action> = emptyList()

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        setActions(actions)
    }

    override fun setActions(actions: List<Action>) {
        removeAllViews()
        this.actions = ArrayList(actions)
        for (action in actions) {
            addAction(action)
        }
    }

    private fun addAction(action: Action) {
        val b = LayoutInflater.from(context).inflate(itemLayout, null) as Button
        if (action.icon != null) {
            val size = resources
                .getDimensionPixelSize(R.dimen.chooser_action_button_icon_size)
            action.icon.setBounds(0, 0, size, size)
            b.setCompoundDrawablesRelative(action.icon, null, null, null)
        }
        b.text = action.label ?: ""
        b.setOnClickListener {
            action.onClicked.run()
        }
        b.id = action.id
        addView(b)
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        super.generateDefaultLayoutParams().apply {
            setMarginsRelative(itemMargin, 0, itemMargin, 0)
        }
}
