/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.wallpaper.widget.BottomActionBar.BottomAction.APPLY;
import static com.android.wallpaper.widget.BottomActionBar.BottomAction.EDIT;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.android.wallpaper.R;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.SetWallpaperViewModel;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LargeScreenMultiPanesChecker;
import com.android.wallpaper.module.UserEventLogger;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.module.WallpaperSetter;
import com.android.wallpaper.util.FullScreenAnimation;
import com.android.wallpaper.util.PreviewUtils;
import com.android.wallpaper.util.ResourceUtils;
import com.android.wallpaper.widget.BottomActionBar;
import com.android.wallpaper.widget.BottomActionBar.BottomSheetContent;
import com.android.wallpaper.widget.WallpaperInfoView;

import com.google.android.material.tabs.TabLayout;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Base Fragment to display the UI for previewing an individual wallpaper
 */
public abstract class PreviewFragment extends AppbarFragment implements
        SetWallpaperDialogFragment.Listener, SetWallpaperErrorDialogFragment.Listener,
        LoadWallpaperErrorDialogFragment.Listener {

    public static final Interpolator ALPHA_OUT = new PathInterpolator(0f, 0f, 0.8f, 1f);

    /**
     * User can view wallpaper and attributions in full screen, but "Set wallpaper" button is
     * hidden.
     */
    static final int MODE_VIEW_ONLY = 0;

    /**
     * User can view wallpaper and attributions in full screen and click "Set wallpaper" to set the
     * wallpaper with pan and crop position to the device.
     */
    static final int MODE_CROP_AND_SET_WALLPAPER = 1;

    /**
     * Possible preview modes for the fragment.
     */
    @IntDef({
            MODE_VIEW_ONLY,
            MODE_CROP_AND_SET_WALLPAPER})
    public @interface PreviewMode {
    }

    public static final String ARG_WALLPAPER = "wallpaper";
    public static final String ARG_PREVIEW_MODE = "preview_mode";
    public static final String ARG_VIEW_AS_HOME = "view_as_home";
    public static final String ARG_FULL_SCREEN = "view_full_screen";
    public static final String ARG_TESTING_MODE_ENABLED = "testing_mode_enabled";

    private static final String TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT =
            "load_wallpaper_error_dialog";
    private static final String TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT =
            "set_wallpaper_error_dialog";
    private static final int UNUSED_REQUEST_CODE = 1;
    private static final String TAG = "PreviewFragment";

    /**
     * When true, enables a test mode of operation -- in which certain UI features are disabled to
     * allow for UI tests to run correctly. Works around issue in ProgressDialog currently where the
     * dialog constantly keeps the UI thread alive and blocks a test forever.
     */
    protected boolean mTestingModeEnabled;

    protected WallpaperInfo mWallpaper;
    protected WallpaperPreviewBitmapTransformation mPreviewBitmapTransformation;
    protected WallpaperSetter mWallpaperSetter;
    protected UserEventLogger mUserEventLogger;
    protected BottomActionBar mBottomActionBar;
    // For full screen animations.
    protected View mRootView;
    protected FullScreenAnimation mFullScreenAnimation;
    @PreviewMode
    protected int mPreviewMode;
    protected boolean mViewAsHome;
    // For full screen preview in a separate Activity.
    protected boolean mShowInFullScreen;

    protected SetWallpaperViewModel mSetWallpaperViewModel;
    protected ViewModelProvider mViewModelProvider;
    protected Optional<Integer> mLastSelectedTabPositionOptional = Optional.empty();
    private OnBackPressedCallback mOnBackPressedCallback;

    /**
     * Staged error dialog fragments that were unable to be shown when the hosting activity didn't
     * allow committing fragment transactions.
     */
    private SetWallpaperErrorDialogFragment mStagedSetWallpaperErrorDialogFragment;
    private LoadWallpaperErrorDialogFragment mStagedLoadWallpaperErrorDialogFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context appContext = getContext().getApplicationContext();
        Injector injector = InjectorProvider.getInjector();

        mUserEventLogger = injector.getUserEventLogger(appContext);
        mWallpaper = getArguments().getParcelable(ARG_WALLPAPER);
        mPreviewBitmapTransformation = new WallpaperPreviewBitmapTransformation(
                appContext, isRtl());

        //noinspection ResourceType
        mPreviewMode = getArguments().getInt(ARG_PREVIEW_MODE);
        mViewAsHome = getArguments().getBoolean(ARG_VIEW_AS_HOME);
        mShowInFullScreen = getArguments().getBoolean(ARG_FULL_SCREEN);

        mTestingModeEnabled = getArguments().getBoolean(ARG_TESTING_MODE_ENABLED);
        mWallpaperSetter = new WallpaperSetter(injector.getWallpaperPersister(appContext),
                injector.getPreferences(appContext), mUserEventLogger, mTestingModeEnabled);

        mViewModelProvider = new ViewModelProvider(requireActivity());
        mSetWallpaperViewModel = mViewModelProvider.get(SetWallpaperViewModel.class);
    }

    @Override
    protected int getToolbarColorId() {
        return android.R.color.transparent;
    }

    @Override
    @CallSuper
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResId(), container, false);
        setUpToolbar(view);

        mRootView = view;
        mFullScreenAnimation = new FullScreenAnimation(view);
        mFullScreenAnimation.setShowInFullScreen(mShowInFullScreen);

        getActivity().getWindow().getDecorView().setOnApplyWindowInsetsListener(
                (v, windowInsets) -> {
                    v.setPadding(
                            v.getPaddingLeft(),
                            0,
                            v.getPaddingRight(),
                            0);

                    mFullScreenAnimation.setWindowInsets(windowInsets);
                    mFullScreenAnimation.placeViews(v);

                    // Update preview header's padding top to align status bar height.
                    View previewHeader = v.findViewById(R.id.preview_header);
                    previewHeader.setPadding(previewHeader.getPaddingLeft(),
                            mFullScreenAnimation.getStatusBarHeight(),
                            previewHeader.getPaddingRight(), previewHeader.getPaddingBottom());

                    return windowInsets.CONSUMED;
                }
        );

        return view;
    }

    @Override
    protected void onBottomActionBarReady(BottomActionBar bottomActionBar) {
        super.onBottomActionBarReady(bottomActionBar);
        mBottomActionBar = bottomActionBar;
        if (!mShowInFullScreen) {
            mBottomActionBar.setActionClickListener(EDIT, (view) -> {
                // Starts a full preview Activity when in multi-pane resolution
                LargeScreenMultiPanesChecker multiPanesChecker = new LargeScreenMultiPanesChecker();
                if (multiPanesChecker.isMultiPanesEnabled(getContext())) {
                    showInFullScreenActivity(mWallpaper);
                } else {
                    mFullScreenAnimation.startAnimation(/* toFullScreen= */ true);
                }
                mBottomActionBar.deselectAction(EDIT);
            });
        } else {
            bottomActionBar.post(
                    () -> mFullScreenAnimation.startAnimation(/* toFullScreen= */ true));
        }
        setFullScreenActions(mRootView.findViewById(R.id.fullscreen_buttons_container));

        if (mOnBackPressedCallback == null) {
            mOnBackPressedCallback = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (mFullScreenAnimation.isFullScreen() && !mShowInFullScreen) {
                        mFullScreenAnimation.startAnimation(/* toFullScreen= */ false);
                        return;
                    }
                    if (mBottomActionBar != null && !mBottomActionBar.isBottomSheetCollapsed()) {
                        mBottomActionBar.collapseBottomSheetIfExpanded();
                        return;
                    }
                    getActivity().finish();
                }
            };
            getActivity().getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback);
        }
    }

    private void showInFullScreenActivity(WallpaperInfo wallpaperInfo) {
        if (wallpaperInfo == null) {
            return;
        }
        startActivity(FullPreviewActivity.newIntent(getActivity(), wallpaperInfo,
                        /* viewAsHome= */ mLastSelectedTabPositionOptional.orElse(0) == 0),
                ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle());
    }

    protected void setFullScreenActions(View container) {
        if (!mShowInFullScreen) {
            // Update the button text for the current workspace visibility.
            Button hideUiPreviewButton = container.findViewById(R.id.hide_ui_preview_button);
            hideUiPreviewButton.setText(mFullScreenAnimation.getWorkspaceVisibility()
                    ? R.string.hide_ui_preview_text
                    : R.string.show_ui_preview_text);
            hideUiPreviewButton.setOnClickListener(
                    (button) -> {
                        boolean visible = mFullScreenAnimation.getWorkspaceVisibility();
                        // Update the button text for the next workspace visibility.
                        ((Button) button).setText(visible
                                ? R.string.show_ui_preview_text
                                : R.string.hide_ui_preview_text);
                        mFullScreenAnimation.setWorkspaceVisibility(!visible);
                        button.announceForAccessibility(
                                visible ? getString(R.string.hint_hide_ui_preview)
                                        : getString(R.string.hint_show_ui_preview));
                    }
            );
            container.findViewById(R.id.set_as_wallpaper_button).setOnClickListener(
                    unused -> onSetWallpaperClicked(null, getCurrentWallpaperInfo()));
        } else {
            container.findViewById(R.id.hide_ui_preview_button).setVisibility(View.GONE);
            container.findViewById(R.id.set_as_wallpaper_button).setVisibility(View.GONE);
            setUpToolbarMenu(R.menu.fullpreview_menu);
            setUpToolbarMenuClickListener(R.id.action_hide_ui, view -> {
                boolean visible = mFullScreenAnimation.getWorkspaceVisibility();
                mFullScreenAnimation.setWorkspaceVisibility(!visible);
                View hideUiView = view.findViewById(R.id.hide_ui_view);
                RippleDrawable ripple = (RippleDrawable) hideUiView.getBackground();
                LayerDrawable layerDrawable = (LayerDrawable) ripple.getDrawable(/* index= */ 0);
                Drawable backgroundDrawable = layerDrawable.getDrawable(/* index= */ 0);
                backgroundDrawable.setTint(!visible ? ResourceUtils.getColorAttr(getActivity(),
                        com.android.internal.R.attr.colorAccentSecondary)
                        : ResourceUtils.getColorAttr(getActivity(),
                                com.android.internal.R.attr.colorAccentPrimary));
            });
            setUpToolbarMenuClickListener(R.id.action_set_wallpaper,
                    view -> mWallpaperSetter.requestDestination(getActivity(), getFragmentManager(),
                            this, mWallpaper instanceof LiveWallpaperInfo));
        }

        mFullScreenAnimation.ensureBottomActionBarIsCorrectlyLocated();
    }

    protected WallpaperInfo getCurrentWallpaperInfo() {
        return mWallpaper;
    }

    protected List<String> getAttributions(Context context) {
        return mWallpaper.getAttributions(context);
    }

    @LayoutRes
    protected abstract int getLayoutResId();

    protected WorkspaceSurfaceHolderCallback createWorkspaceSurfaceCallback(
            SurfaceView workspaceSurface) {
        return new WorkspaceSurfaceHolderCallback(
                workspaceSurface,
                new PreviewUtils(
                        getContext(),
                        getContext().getString(R.string.grid_control_metadata_name)));
    }

    @Override
    public void onResume() {
        super.onResume();

        WallpaperPreferences preferences =
                InjectorProvider.getInjector().getPreferences(getActivity());
        preferences.setLastAppActiveTimestamp(new Date().getTime());

        // Show the staged 'load wallpaper' or 'set wallpaper' error dialog fragments if there is
        // one that was unable to be shown earlier when this fragment's hosting activity didn't
        // allow committing fragment transactions.
        if (mStagedLoadWallpaperErrorDialogFragment != null) {
            mStagedLoadWallpaperErrorDialogFragment.show(
                    requireFragmentManager(), TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT);
            mStagedLoadWallpaperErrorDialogFragment = null;
        }
        if (mStagedSetWallpaperErrorDialogFragment != null) {
            mStagedSetWallpaperErrorDialogFragment.show(
                    requireFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
            mStagedSetWallpaperErrorDialogFragment = null;
        }

        mSetWallpaperViewModel.getStatus().observe(requireActivity(), setWallpaperStatus -> {
            switch (setWallpaperStatus) {
                case SUCCESS:
                    // Give a few millis before finishing to allow for the dialog dismiss
                    // and animations to finish
                    Handler.getMain().postDelayed(() -> finishActivity(true), 300);
                    break;
                case ERROR:
                    showSetWallpaperErrorDialog(mSetWallpaperViewModel.getDestination());
                    break;
                default:
                    // Do nothing in this case, either status is pending, or unknown
            }
        });
    }

    protected abstract boolean isLoaded();

    @Override
    public void onSet(int destination) {
        mSetWallpaperViewModel.setDestination(destination);
        setCurrentWallpaper(destination);
    }

    @Override
    public void onDialogDismissed(boolean withItemSelected) {
        mBottomActionBar.deselectAction(APPLY);
    }

    @Override
    public void onClickTryAgain(@Destination int wallpaperDestination) {
        mSetWallpaperViewModel.setDestination(wallpaperDestination);
        setCurrentWallpaper(wallpaperDestination);
    }

    @Override
    public void onClickOk() {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWallpaperSetter.cleanUp();
    }

    @Override
    public CharSequence getDefaultTitle() {
        return getContext().getString(R.string.preview);
    }

    protected void onSetWallpaperClicked(View button, WallpaperInfo wallpaperInfo) {
        mWallpaperSetter.requestDestination(getActivity(), getFragmentManager(), this,
                wallpaperInfo instanceof LiveWallpaperInfo);
    }

    protected void setUpTabs(TabLayout tabs) {
        tabs.addTab(tabs.newTab().setText(R.string.home_screen_message));
        tabs.addTab(tabs.newTab().setText(R.string.lock_screen_message));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mLastSelectedTabPositionOptional = Optional.of(tab.getPosition());
                updateScreenPreview(/* isHomeSelected= */ tab.getPosition() == 0);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // The TabLayout only contains below tabs
        // 0. Home tab
        // 1. Lock tab
        int tabPosition = mLastSelectedTabPositionOptional.orElseGet(() -> mViewAsHome ? 0 : 1);
        tabs.getTabAt(tabPosition).select();
        updateScreenPreview(/* isHomeSelected= */ tabPosition == 0);
    }

    protected abstract void updateScreenPreview(boolean isHomeSelected);

    /**
     * Sets current wallpaper to the device based on current zoom and scroll state.
     *
     * @param destination The wallpaper destination i.e. home vs. lockscreen vs. both.
     */
    protected abstract void setCurrentWallpaper(@Destination int destination);

    protected void finishActivity(boolean success) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (success) {
            try {
                Toast.makeText(activity,
                        R.string.wallpaper_set_successfully_message, Toast.LENGTH_SHORT).show();
            } catch (NotFoundException e) {
                Log.e(TAG, "Could not show toast " + e);
            }
            setResult(activity);
        }
        activity.finish();
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    protected void setResult(Activity activity) {
        activity.setResult(Activity.RESULT_OK);
    }

    protected void showSetWallpaperErrorDialog(@Destination int wallpaperDestination) {
        SetWallpaperErrorDialogFragment newFragment = SetWallpaperErrorDialogFragment.newInstance(
                R.string.set_wallpaper_error_message, wallpaperDestination);
        newFragment.setTargetFragment(this, UNUSED_REQUEST_CODE);

        // Show 'set wallpaper' error dialog now if it's safe to commit fragment transactions,
        // otherwise stage it for later when the hosting activity is in a state to commit fragment
        // transactions.
        BasePreviewActivity activity = (BasePreviewActivity) requireActivity();
        if (activity.isSafeToCommitFragmentTransaction()) {
            newFragment.show(requireFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
        } else {
            mStagedSetWallpaperErrorDialogFragment = newFragment;
        }
    }

    /**
     * Shows 'load wallpaper' error dialog now or stage it to be shown when the hosting activity is
     * in a state that allows committing fragment transactions.
     */
    protected void showLoadWallpaperErrorDialog() {
        LoadWallpaperErrorDialogFragment dialogFragment =
                LoadWallpaperErrorDialogFragment.newInstance();
        dialogFragment.setTargetFragment(this, UNUSED_REQUEST_CODE);

        // Show 'load wallpaper' error dialog now or stage it to be shown when the hosting
        // activity is in a state that allows committing fragment transactions.
        BasePreviewActivity activity = (BasePreviewActivity) getActivity();
        if (activity != null && activity.isSafeToCommitFragmentTransaction()) {
            dialogFragment.show(requireFragmentManager(), TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT);
        } else {
            mStagedLoadWallpaperErrorDialogFragment = dialogFragment;
        }
    }

    /**
     * Returns whether layout direction is RTL (or false for LTR). Since native RTL layout support
     * was added in API 17, returns false for versions lower than 17.
     */
    protected boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
    }

    protected final class WallpaperInfoContent extends BottomSheetContent<WallpaperInfoView> {

        @Nullable
        private Intent mExploreIntent;
        private CharSequence mActionLabel;

        protected WallpaperInfoContent(Context context) {
            super(context);
        }

        @Override
        public int getViewId() {
            return R.layout.wallpaper_info_view;
        }

        @Override
        public void onViewCreated(WallpaperInfoView view) {
            if (mWallpaper == null) {
                return;
            }

            if (mActionLabel == null) {
                setUpExploreIntentAndLabel(() -> populateWallpaperInfo(view));
            } else {
                populateWallpaperInfo(view);
            }
        }

        private void setUpExploreIntentAndLabel(@Nullable Runnable callback) {
            Context context = getContext();
            if (context == null) {
                return;
            }

            WallpaperInfoHelper.loadExploreIntent(context, mWallpaper,
                    (actionLabel, exploreIntent) -> {
                        mActionLabel = actionLabel;
                        mExploreIntent = exploreIntent;
                        if (callback != null) {
                            callback.run();
                        }
                    }
            );
        }

        private void onExploreClicked(View button) {
            Context context = getContext();
            if (context == null) {
                return;
            }

            mUserEventLogger.logActionClicked(mWallpaper.getCollectionId(context),
                    mWallpaper.getActionLabelRes(context));

            startActivity(mExploreIntent);
        }

        private void populateWallpaperInfo(WallpaperInfoView view) {
            view.populateWallpaperInfo(
                    mWallpaper,
                    mActionLabel,
                    WallpaperInfoHelper.shouldShowExploreButton(
                            getContext(), mExploreIntent),
                    this::onExploreClicked);
        }
    }

    protected void updatePreviewHeader(View fragmentView) {
        View previewHeader = fragmentView.findViewById(R.id.preview_header);
        previewHeader.setBackgroundColor(getContext().getColor(R.color.toolbar_color));
    }
}
