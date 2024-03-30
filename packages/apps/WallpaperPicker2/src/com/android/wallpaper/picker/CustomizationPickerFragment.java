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
package com.android.wallpaper.picker;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.android.settingslib.activityembedding.ActivityEmbeddingUtils;
import com.android.wallpaper.R;
import com.android.wallpaper.model.CustomizationSectionController;
import com.android.wallpaper.model.CustomizationSectionController.CustomizationSectionNavigationController;
import com.android.wallpaper.model.PermissionRequester;
import com.android.wallpaper.model.WallpaperPreviewNavigator;
import com.android.wallpaper.module.CustomizationSections;
import com.android.wallpaper.module.FragmentFactory;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.customization.ui.binder.CustomizationPickerBinder;
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel;
import com.android.wallpaper.picker.customization.ui.viewmodel.WallpaperQuickSwitchViewModel;
import com.android.wallpaper.util.ActivityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import kotlinx.coroutines.DisposableHandle;

/** The Fragment UI for customization sections. */
public class CustomizationPickerFragment extends AppbarFragment implements
        CustomizationSectionNavigationController {

    private static final String TAG = "CustomizationPickerFragment";
    private static final String SCROLL_POSITION_Y = "SCROLL_POSITION_Y";
    protected static final String KEY_IS_USE_REVAMPED_UI = "is_use_revamped_ui";
    private static final String KEY_START_FROM_LOCK_SCREEN = "start_from_lock_screen";
    private DisposableHandle mBinding;

    /** Returns a new instance of {@link CustomizationPickerFragment}. */
    public static CustomizationPickerFragment newInstance(
            boolean isUseRevampedUi,
            boolean startFromLockScreen) {
        final CustomizationPickerFragment fragment = new CustomizationPickerFragment();
        final Bundle args = new Bundle();
        args.putBoolean(KEY_IS_USE_REVAMPED_UI, isUseRevampedUi);
        args.putBoolean(KEY_START_FROM_LOCK_SCREEN, startFromLockScreen);
        fragment.setArguments(args);
        return fragment;
    }

    // Note that the section views will be displayed by the list ordering.
    private final List<CustomizationSectionController<?>> mSectionControllers = new ArrayList<>();
    private NestedScrollView mNestedScrollView;
    @Nullable
    private Bundle mBackStackSavedInstanceState;
    private final FragmentFactory mFragmentFactory;
    @Nullable
    private CustomizationPickerViewModel mViewModel;

    public CustomizationPickerFragment() {
        mFragmentFactory = InjectorProvider.getInjector().getFragmentFactory();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        final boolean shouldUseRevampedUi = shouldUseRevampedUi();
        final int layoutId = shouldUseRevampedUi
                ? R.layout.toolbar_container_layout
                : R.layout.collapsing_toolbar_container_layout;
        final View view = inflater.inflate(layoutId, container, false);
        if (ActivityUtils.isLaunchedFromSettingsRelated(getActivity().getIntent())) {
            setUpToolbar(view, !ActivityEmbeddingUtils.shouldHideNavigateUpButton(
                    getActivity(), /* isSecondLayerPage= */ true));
        } else {
            setUpToolbar(view, /* upArrow= */ false);
        }

        final Injector injector = InjectorProvider.getInjector();
        if (shouldUseRevampedUi) {
            setContentView(view, R.layout.fragment_tabbed_customization_picker);
            mViewModel = new ViewModelProvider(
                    this,
                    CustomizationPickerViewModel.newFactory(
                            this,
                            savedInstanceState,
                            injector.getUndoInteractor(requireContext()))
            ).get(CustomizationPickerViewModel.class);
            final Bundle arguments = getArguments();
            mViewModel.setInitialScreen(
                    arguments != null && arguments.getBoolean(KEY_START_FROM_LOCK_SCREEN));

            setUpToolbarMenu(R.menu.undoable_customization_menu);
            final Bundle finalSavedInstanceState = savedInstanceState;
            if (mBinding != null) {
                mBinding.dispose();
            }
            mBinding = CustomizationPickerBinder.bind(
                    view,
                    getToolbarId(),
                    mViewModel,
                    this,
                    isOnLockScreen -> filterAvailableSections(
                            getSectionControllers(
                                isOnLockScreen
                                        ? CustomizationSections.Screen.LOCK_SCREEN
                                        : CustomizationSections.Screen.HOME_SCREEN,
                                finalSavedInstanceState)));
        } else {
            setContentView(view, R.layout.fragment_customization_picker);
        }

        if (mBackStackSavedInstanceState != null) {
            savedInstanceState = mBackStackSavedInstanceState;
            mBackStackSavedInstanceState = null;
        }

        mNestedScrollView = view.findViewById(R.id.scroll_container);

        if (!shouldUseRevampedUi) {
            ViewGroup sectionContainer = view.findViewById(R.id.section_container);
            sectionContainer.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        windowInsets.getSystemWindowInsetBottom());
                return windowInsets.consumeSystemWindowInsets();
            });

            initSections(savedInstanceState);
            mSectionControllers.forEach(controller ->
                    mNestedScrollView.post(() -> {
                                final Context context = getContext();
                                if (context == null) {
                                    Log.w(TAG, "Adding section views with null context");
                                    return;
                                }
                                sectionContainer.addView(controller.createView(context));
                            }
                    )
            );

            final Bundle savedInstanceStateRef = savedInstanceState;
            // Post it to the end of adding views to ensure restoring view state the last task.
            view.post(() -> restoreViewState(savedInstanceStateRef));
        }

        return view;
    }

    private void setContentView(View view, int layoutResId) {
        final ViewGroup parent = view.findViewById(R.id.content_frame);
        if (parent != null) {
            parent.removeAllViews();
        }
        LayoutInflater.from(view.getContext()).inflate(layoutResId, parent);
    }

    private void restoreViewState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mNestedScrollView.post(() ->
                    mNestedScrollView.setScrollY(savedInstanceState.getInt(SCROLL_POSITION_Y)));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        onSaveInstanceStateInternal(savedInstanceState);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected int getToolbarId() {
        return shouldUseRevampedUi() ? R.id.toolbar : R.id.action_bar;
    }

    @Override
    protected int getToolbarColorId() {
        return shouldUseRevampedUi() ? R.color.toolbar_color : android.R.color.transparent;
    }

    @Override
    public CharSequence getDefaultTitle() {
        return getString(R.string.app_name);
    }

    @Override
    public boolean onBackPressed() {
        // TODO(b/191120122) Improve glitchy animation in Settings.
        if (ActivityUtils.isLaunchedFromSettingsSearch(getActivity().getIntent())) {
            mSectionControllers.forEach(CustomizationSectionController::onTransitionOut);
        }
        return super.onBackPressed();
    }

    @Override
    public void onDestroyView() {
        // When add to back stack, #onDestroyView would be called, but #onDestroy wouldn't. So
        // storing the state in variable to restore when back to foreground. If it's not a back
        // stack case (i,e, config change), the variable would not be retained, see
        // https://developer.android.com/guide/fragments/saving-state.
        mBackStackSavedInstanceState = new Bundle();
        onSaveInstanceStateInternal(mBackStackSavedInstanceState);

        mSectionControllers.forEach(CustomizationSectionController::release);
        mSectionControllers.clear();
        super.onDestroyView();
    }

    @Override
    public void navigateTo(Fragment fragment) {
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        fragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
        fragmentManager.executePendingTransactions();
    }

    @Override
    public void navigateTo(String destinationId) {
        final Fragment fragment = mFragmentFactory.create(destinationId);

        if (fragment != null) {
            navigateTo(fragment);
        }
    }

    /** Saves state of the fragment. */
    private void onSaveInstanceStateInternal(Bundle savedInstanceState) {
        if (mNestedScrollView != null) {
            savedInstanceState.putInt(SCROLL_POSITION_Y, mNestedScrollView.getScrollY());
        }
        mSectionControllers.forEach(c -> c.onSaveInstanceState(savedInstanceState));
    }

    private void initSections(@Nullable Bundle savedInstanceState) {
        // Release and clear if any.
        mSectionControllers.forEach(CustomizationSectionController::release);
        mSectionControllers.clear();

        mSectionControllers.addAll(
                filterAvailableSections(
                        getSectionControllers(
                            null,
                            savedInstanceState)));
    }

    private List<CustomizationSectionController<?>> getSectionControllers(
            @Nullable CustomizationSections.Screen screen,
            @Nullable Bundle savedInstanceState) {
        final Injector injector = InjectorProvider.getInjector();

        WallpaperQuickSwitchViewModel wallpaperQuickSwitchViewModel = new ViewModelProvider(
                getActivity(),
                WallpaperQuickSwitchViewModel.newFactory(
                        this,
                        savedInstanceState,
                        injector.getWallpaperInteractor(requireContext())))
                .get(WallpaperQuickSwitchViewModel.class);

        CustomizationSections sections = injector.getCustomizationSections(getActivity());
        if (screen == null) {
            return sections.getAllSectionControllers(
                    getActivity(),
                    getViewLifecycleOwner(),
                    injector.getWallpaperColorsViewModel(),
                    getPermissionRequester(),
                    getWallpaperPreviewNavigator(),
                    this,
                    savedInstanceState,
                    injector.getDisplayUtils(getActivity()));
        } else {
            return sections.getRevampedUISectionControllersForScreen(
                    screen,
                    getActivity(),
                    getViewLifecycleOwner(),
                    injector.getWallpaperColorsViewModel(),
                    getPermissionRequester(),
                    getWallpaperPreviewNavigator(),
                    this,
                    savedInstanceState,
                    injector.getCurrentWallpaperInfoFactory(requireContext()),
                    injector.getDisplayUtils(getActivity()),
                    wallpaperQuickSwitchViewModel,
                    injector.getWallpaperInteractor(requireContext()));
        }
    }

    /** Returns a filtered list containing only the available section controllers. */
    protected List<CustomizationSectionController<?>> filterAvailableSections(
            List<CustomizationSectionController<?>> controllers) {
        return controllers.stream()
                .filter(controller -> {
                    if (controller.isAvailable(getContext())) {
                        return true;
                    } else {
                        controller.release();
                        Log.d(TAG, "Section is not available: " + controller);
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private PermissionRequester getPermissionRequester() {
        return (PermissionRequester) getActivity();
    }

    private WallpaperPreviewNavigator getWallpaperPreviewNavigator() {
        return (WallpaperPreviewNavigator) getActivity();
    }

    private boolean shouldUseRevampedUi() {
        final Bundle args = getArguments();
        if (args != null && args.containsKey(KEY_IS_USE_REVAMPED_UI)) {
            return args.getBoolean(KEY_IS_USE_REVAMPED_UI);
        } else {
            throw new IllegalStateException(
                    "Must contain KEY_IS_USE_REVAMPED_UI argument, did you instantiate directly"
                            + " instead of using the newInstance function?");
        }
    }
}
