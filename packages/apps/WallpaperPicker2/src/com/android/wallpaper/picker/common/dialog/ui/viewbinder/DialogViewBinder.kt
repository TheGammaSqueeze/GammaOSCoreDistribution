/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */

package com.android.wallpaper.picker.common.dialog.ui.viewbinder

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.android.wallpaper.R
import com.android.wallpaper.picker.common.button.ui.viewbinder.ButtonViewBinder
import com.android.wallpaper.picker.common.dialog.ui.viewmodel.DialogViewModel
import com.android.wallpaper.picker.common.icon.ui.viewbinder.IconViewBinder
import com.android.wallpaper.picker.common.text.ui.viewbinder.TextViewBinder

object DialogViewBinder {
    /** Returns a shown dialog that's bound to the given [DialogViewModel]. */
    fun show(
        context: Context,
        viewModel: DialogViewModel,
        onDismissed: (() -> Unit)? = null,
        @LayoutRes dialogLayoutResourceId: Int = R.layout.dialog_view,
        @LayoutRes buttonLayoutResourceId: Int = R.layout.dialog_button,
    ): Dialog {
        val view = LayoutInflater.from(context).inflate(dialogLayoutResourceId, null)
        val icon: ImageView = view.requireViewById(R.id.icon)
        val title: TextView = view.requireViewById(R.id.title)
        val message: TextView = view.requireViewById(R.id.message)
        val buttonContainer: ViewGroup = view.requireViewById(R.id.button_container)

        viewModel.icon?.let {
            IconViewBinder.bind(
                view = icon,
                viewModel = it,
            )
            icon.isVisible = true
        }
            ?: run { icon.isVisible = false }

        viewModel.title?.let {
            TextViewBinder.bind(
                view = title,
                viewModel = it,
            )
            title.isVisible = true
        }
            ?: run { title.isVisible = false }

        viewModel.message?.let {
            TextViewBinder.bind(
                view = message,
                viewModel = it,
            )
            message.isVisible = true
        }
            ?: run { message.isVisible = false }

        val dialog =
            AlertDialog.Builder(context, R.style.LightDialogTheme)
                .setView(view)
                .apply {
                    if (viewModel.onDismissed != null || onDismissed != null) {
                        setOnDismissListener {
                            onDismissed?.invoke()
                            viewModel.onDismissed?.invoke()
                        }
                    }
                }
                .create()

        buttonContainer.removeAllViews()
        viewModel.buttons.forEach { buttonViewModel ->
            buttonContainer.addView(
                ButtonViewBinder.create(
                    parent = buttonContainer,
                    viewModel =
                        buttonViewModel.copy(
                            onClicked = {
                                buttonViewModel.onClicked?.invoke()
                                dialog.dismiss()
                            },
                        ),
                    buttonLayoutResourceId = buttonLayoutResourceId,
                )
            )
        }

        dialog.show()
        return dialog
    }
}
