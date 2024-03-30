/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.customization.picker.grid;

import static com.android.wallpaper.widget.BottomActionBar.BottomAction.APPLY_TEXT;

import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.CustomizationOption;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionViewModel;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.customization.picker.WallpaperPreviewer;
import com.android.customization.widget.OptionSelectorController;
import com.android.customization.widget.OptionSelectorController.CheckmarkStyle;
import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.AppbarFragment;
import com.android.wallpaper.util.LaunchUtils;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.widget.BottomActionBar;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

/**
 * Fragment that contains the UI for selecting and applying a GridOption.
 */
public class GridFragment extends AppbarFragment {

    private static final String TAG = "GridFragment";

    private WallpaperInfo mHomeWallpaper;
    private RecyclerView mOptionsContainer;
    private OptionSelectorController<GridOption> mOptionsController;
    private GridOptionsManager mGridManager;
    private ContentLoadingProgressBar mLoading;
    private ConstraintLayout mContent;
    private View mError;
    private BottomActionBar mBottomActionBar;
    private ThemesUserEventLogger mEventLogger;
    private GridOptionPreviewer mGridOptionPreviewer;
    private GridOptionViewModel mGridOptionViewModel;

    private final Callback mApplyGridCallback = new Callback() {
        @Override
        public void onSuccess() {
            mGridManager.fetchOptions(unused -> {}, true);
            Toast.makeText(getContext(), R.string.applied_grid_msg, Toast.LENGTH_SHORT).show();
            getActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            getActivity().finish();

            // Go back to launcher home
            LaunchUtils.launchHome(getContext());
        }

        @Override
        public void onError(@Nullable Throwable throwable) {
            // Since we disabled it when clicked apply button.
            mBottomActionBar.enableActions();
            mBottomActionBar.hide();
            mGridOptionViewModel.setBottomActionBarVisible(false);
            //TODO(chihhangchuang): handle
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGridOptionViewModel = new ViewModelProvider(requireActivity()).get(
                GridOptionViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_grid_picker, container, /* attachToRoot */ false);
        setUpToolbar(view);
        mContent = view.findViewById(R.id.content_section);
        mOptionsContainer = view.findViewById(R.id.options_container);
        AccessibilityManager accessibilityManager =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isEnabled()) {
            // Make Talkback focus won't reset when notifyDataSetChange
            mOptionsContainer.setItemAnimator(null);
        }

        // Set aspect ratio on the preview card dynamically.
        Point mScreenSize;
        ScreenSizeCalculator screenSizeCalculator = ScreenSizeCalculator.getInstance();
        mScreenSize = screenSizeCalculator.getScreenSize(
                requireActivity().getWindowManager().getDefaultDisplay());
        ConstraintSet set = new ConstraintSet();
        set.clone(mContent);
        String ratio = String.format(Locale.US, "%d:%d", mScreenSize.x, mScreenSize.y);
        set.setDimensionRatio(R.id.preview_card_container, ratio);
        set.applyTo(mContent);

        mLoading = view.findViewById(R.id.loading_indicator);
        mError = view.findViewById(R.id.error_section);

        // For nav bar edge-to-edge effect.
        view.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            v.setPadding(
                    v.getPaddingLeft(),
                    windowInsets.getSystemWindowInsetTop(),
                    v.getPaddingRight(),
                    windowInsets.getSystemWindowInsetBottom());
            return windowInsets.consumeSystemWindowInsets();
        });

        // Clear memory cache whenever grid fragment view is being loaded.
        Glide.get(getContext()).clearMemory();

        mGridManager = GridOptionsManager.getInstance(getContext());
        mEventLogger = (ThemesUserEventLogger) InjectorProvider.getInjector()
                .getUserEventLogger(getContext());
        setUpOptions();

        SurfaceView wallpaperSurface = view.findViewById(R.id.wallpaper_preview_surface);
        WallpaperPreviewer wallpaperPreviewer = new WallpaperPreviewer(getLifecycle(),
                getActivity(), view.findViewById(R.id.wallpaper_preview_image), wallpaperSurface,
                view.findViewById(R.id.grid_fadein_scrim));
        // Loads current Wallpaper.
        CurrentWallpaperInfoFactory factory = InjectorProvider.getInjector()
                .getCurrentWallpaperInfoFactory(getContext().getApplicationContext());
        factory.createCurrentWallpaperInfos((homeWallpaper, lockWallpaper, presentationMode) -> {
            mHomeWallpaper = homeWallpaper;
            wallpaperPreviewer.setWallpaper(mHomeWallpaper, /* listener= */ null);
        }, false);

        mGridOptionPreviewer = new GridOptionPreviewer(mGridManager,
                view.findViewById(R.id.grid_preview_container));

        return view;
    }

    @Override
    public boolean onBackPressed() {
        mGridOptionViewModel.setSelectedOption(null);
        mGridOptionViewModel.setBottomActionBarVisible(false);
        return super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGridOptionPreviewer != null) {
            mGridOptionPreviewer.release();
        }
    }

    @Override
    public CharSequence getDefaultTitle() {
        return getString(R.string.grid_title);
    }

    @Override
    protected void onBottomActionBarReady(BottomActionBar bottomActionBar) {
        super.onBottomActionBarReady(bottomActionBar);
        mBottomActionBar = bottomActionBar;
        mBottomActionBar.showActionsOnly(APPLY_TEXT);
        mBottomActionBar.setActionClickListener(APPLY_TEXT,
                v -> applyGridOption(mGridOptionViewModel.getSelectedOption()));
        mBottomActionBar.setActionAccessibilityTraversalAfter(APPLY_TEXT,
                mOptionsContainer.getId());
    }

    private void applyGridOption(GridOption gridOption) {
        mBottomActionBar.disableActions();
        mGridManager.apply(gridOption, mApplyGridCallback);
    }

    private void setUpOptions() {
        hideError();
        mLoading.show();
        mGridManager.fetchOptions(new OptionsFetchedListener<GridOption>() {
            @Override
            public void onOptionsLoaded(List<GridOption> options) {
                mLoading.hide();
                mOptionsController = new OptionSelectorController<>(
                        mOptionsContainer, options, /* useGrid= */ false,
                        CheckmarkStyle.CENTER_CHANGE_COLOR_WHEN_NOT_SELECTED);
                mOptionsController.initOptions(mGridManager);
                GridOption previouslySelectedOption = findEquivalent(options,
                        mGridOptionViewModel.getSelectedOption());
                mGridOptionViewModel.setSelectedOption(
                        previouslySelectedOption != null
                                ? previouslySelectedOption
                                : getActiveOption(options));

                mOptionsController.setSelectedOption(mGridOptionViewModel.getSelectedOption());
                onOptionSelected(mGridOptionViewModel.getSelectedOption());
                restoreBottomActionBarVisibility();

                mOptionsController.addListener(selectedOption -> {
                    String title = selectedOption.getTitle();
                    int stringId = R.string.option_previewed_description;
                    if (selectedOption.isActive(mGridManager)) {
                        stringId = R.string.option_applied_previewed_description;
                    }
                    CharSequence cd = getContext().getString(stringId, title);
                    mOptionsContainer.announceForAccessibility(cd);
                    onOptionSelected(selectedOption);
                    mBottomActionBar.show();
                    mGridOptionViewModel.setBottomActionBarVisible(true);
                });
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                if (throwable != null) {
                    Log.e(TAG, "Error loading grid options", throwable);
                }
                showError();
            }
        }, /*reload= */ true);
    }

    private GridOption getActiveOption(List<GridOption> options) {
        return options.stream()
                .filter(option -> option.isActive(mGridManager))
                .findAny()
                // For development only, as there should always be a grid set.
                .orElse(options.get(0));
    }

    @Nullable
    private GridOption findEquivalent(List<GridOption> options, GridOption target) {
        return options.stream()
                .filter(option -> option.equals(target))
                .findAny()
                .orElse(null);
    }

    private void hideError() {
        mContent.setVisibility(View.VISIBLE);
        mError.setVisibility(View.GONE);
    }

    private void showError() {
        mLoading.hide();
        mContent.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void onOptionSelected(CustomizationOption selectedOption) {
        mGridOptionViewModel.setSelectedOption((GridOption) selectedOption);
        mEventLogger.logGridSelected(mGridOptionViewModel.getSelectedOption());
        mGridOptionPreviewer.setGridOption(mGridOptionViewModel.getSelectedOption());
    }

    private void restoreBottomActionBarVisibility() {
        if (mGridOptionViewModel.getBottomActionBarVisible()) {
            mBottomActionBar.show();
        } else {
            mBottomActionBar.hide();
        }
    }
}
