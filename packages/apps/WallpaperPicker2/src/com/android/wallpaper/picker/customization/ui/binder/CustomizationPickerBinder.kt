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
 *
 */

package com.android.wallpaper.picker.customization.ui.binder

import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.model.CustomizationSectionController
import com.android.wallpaper.picker.SectionView
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel
import com.android.wallpaper.picker.undo.ui.binder.RevertToolbarButtonBinder
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.launch

typealias SectionController = CustomizationSectionController<*>

/** Binds view to view-model for the customization picker. */
object CustomizationPickerBinder {

    /**
     * Binds the given view and view-model, keeping the UI up-to-date and listening to user input.
     *
     * @param view The root of the UI to keep up-to-date and observe for user input.
     * @param toolbarViewId The view ID of the toolbar view.
     * @param viewModel The view-model to observe UI state from and report user input to.
     * @param lifecycleOwner An owner of the lifecycle, so we can stop doing work when the lifecycle
     *   cleans up.
     * @param sectionControllerProvider A function that can provide the list of [SectionController]
     *   instances to show, based on the given passed-in value of "isOnLockScreen".
     * @return A [DisposableHandle] to use to dispose of the binding before another binding is about
     *   to be created by a subsequent call to this function.
     */
    @JvmStatic
    fun bind(
        view: View,
        @IdRes toolbarViewId: Int,
        viewModel: CustomizationPickerViewModel,
        lifecycleOwner: LifecycleOwner,
        sectionControllerProvider: (isOnLockScreen: Boolean) -> List<SectionController>,
    ): DisposableHandle {
        RevertToolbarButtonBinder.bind(
            view = view.requireViewById(toolbarViewId),
            viewModel = viewModel.undo,
            lifecycleOwner = lifecycleOwner,
        )

        CustomizationPickerTabsBinder.bind(
            view = view,
            viewModel = viewModel,
            lifecycleOwner = lifecycleOwner,
        )

        val sectionContainer = view.findViewById<ViewGroup>(R.id.section_container)
        sectionContainer.setOnApplyWindowInsetsListener { v: View, windowInsets: WindowInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets.consumeSystemWindowInsets()
        }
        sectionContainer.updateLayoutParams<FrameLayout.LayoutParams> {
            // We don't want the top margin from the XML because our tabs have that as padding so
            // they can be collapsed into the toolbar with spacing from the actual title text.
            topMargin = 0
        }

        val job =
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.isOnLockScreen.collect { isOnLockScreen ->
                            // These are the available section controllers we should use now.
                            val newSectionControllers =
                                sectionControllerProvider.invoke(isOnLockScreen).filter {
                                    it.isAvailable(view.context)
                                }

                            check(
                                newSectionControllers[0].shouldRetainInstanceWhenSwitchingTabs()
                            ) {
                                "We are not recreating the first section when the users switching" +
                                    " between the home screen and lock screen tab. The first" +
                                    " section should always retain."
                            }

                            val firstTime = sectionContainer.childCount == 0
                            if (!firstTime) {
                                // Remove all views, except the very first one, which we assume is
                                // for
                                // the wallpaper preview section.
                                sectionContainer.removeViews(1, sectionContainer.childCount - 1)

                                // The old controllers for the removed views should be released,
                                // except
                                // for the very first one, which is for the wallpaper preview
                                // section;
                                // that one we keep but just tell it that we switched screens.
                                sectionContainer.children
                                    .mapNotNull { it.tag as? SectionController }
                                    .forEachIndexed { index, oldController ->
                                        if (index == 0) {
                                            // We assume that index 0 is the wallpaper preview
                                            // section.
                                            // We keep it because it's an expensive section (as it
                                            // needs
                                            // to maintain a wallpaper connection that seems to be
                                            // making assumptions about its SurfaceView always
                                            // remaining
                                            // attached to the window).
                                            oldController.onScreenSwitched(isOnLockScreen)
                                        } else {
                                            // All other old controllers will be thrown out so let's
                                            // release them.
                                            oldController.release()
                                        }
                                    }
                            }

                            // Let's add the new controllers and views.
                            newSectionControllers.forEachIndexed { index, controller ->
                                if (firstTime || index > 0) {
                                    val addedView =
                                        controller.createView(
                                            view.context,
                                            CustomizationSectionController.ViewCreationParams(
                                                isOnLockScreen = isOnLockScreen,
                                            )
                                        )
                                    addedView?.tag = controller
                                    sectionContainer.addView(addedView)
                                }
                            }
                        }
                    }
                }

                // This happens when the lifecycle is stopped.
                sectionContainer.children
                    .mapNotNull { it.tag as? CustomizationSectionController<out SectionView> }
                    .forEach { controller -> controller.release() }
                sectionContainer.removeAllViews()
            }
        return DisposableHandle { job.cancel() }
    }
}
