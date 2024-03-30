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
package com.android.wallpaper.model;

import static android.Manifest.permission.READ_MEDIA_IMAGES;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.RenderEffect;
import android.graphics.Shader.TileMode;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.service.wallpaper.WallpaperService;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.BitmapCachingAsset;
import com.android.wallpaper.asset.CurrentWallpaperAssetVN;
import com.android.wallpaper.model.WallpaperInfo.ColorInfo;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.UserEventLogger;
import com.android.wallpaper.picker.CategorySelectorFragment;
import com.android.wallpaper.picker.MyPhotosStarter;
import com.android.wallpaper.picker.WallpaperSectionView;
import com.android.wallpaper.picker.WorkspaceSurfaceHolderCallback;
import com.android.wallpaper.util.DisplayUtils;
import com.android.wallpaper.util.PreviewUtils;
import com.android.wallpaper.util.ResourceUtils;
import com.android.wallpaper.util.VideoWallpaperUtils;
import com.android.wallpaper.util.WallpaperConnection;
import com.android.wallpaper.util.WallpaperSurfaceCallback;
import com.android.wallpaper.widget.LockScreenPreviewer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * The class to control the wallpaper section view.
 */
public class WallpaperSectionController implements
        CustomizationSectionController<WallpaperSectionView>,
        LifecycleObserver {

    private static final String PERMISSION_READ_WALLPAPER_INTERNAL =
            "android.permission.READ_WALLPAPER_INTERNAL";
    private static final int SETTINGS_APP_INFO_REQUEST_CODE = 1;

    private CardView mHomePreviewCard;
    private ContentLoadingProgressBar mHomePreviewProgress;
    private SurfaceView mWorkspaceSurface;
    private WorkspaceSurfaceHolderCallback mWorkspaceSurfaceCallback;
    private SurfaceView mHomeWallpaperSurface;
    private WallpaperSurfaceCallback mHomeWallpaperSurfaceCallback;
    private ImageView mHomeFadeInScrim;
    private SurfaceView mLockWallpaperSurface;
    private WallpaperSurfaceCallback mLockWallpaperSurfaceCallback;
    private CardView mLockscreenPreviewCard;
    private ViewGroup mLockPreviewContainer;
    private ImageView mLockFadeInScrim;
    private ContentLoadingProgressBar mLockscreenPreviewProgress;
    private WallpaperConnection mHomeWallpaperConnection;
    private WallpaperConnection mLockWallpaperConnection;

    // The wallpaper information which is currently shown on the home preview.
    private WallpaperInfo mHomePreviewWallpaperInfo;
    // The wallpaper information which is currently shown on the lock preview.
    private WallpaperInfo mLockPreviewWallpaperInfo;

    private LockScreenPreviewer mLockScreenPreviewer;

    private final Activity mActivity;
    private final Context mAppContext;
    private final LifecycleOwner mLifecycleOwner;
    private final PermissionRequester mPermissionRequester;
    private final WallpaperColorsViewModel mWallpaperColorsViewModel;
    @Nullable
    private final LiveData<Boolean> mOnThemingChanged;
    private final CustomizationSectionNavigationController mSectionNavigationController;
    private final WallpaperPreviewNavigator mWallpaperPreviewNavigator;
    private final Bundle mSavedInstanceState;
    private final DisplayUtils mDisplayUtils;

    public WallpaperSectionController(Activity activity, LifecycleOwner lifecycleOwner,
            PermissionRequester permissionRequester, WallpaperColorsViewModel colorsViewModel,
            @Nullable LiveData<Boolean> onThemingChanged,
            CustomizationSectionNavigationController sectionNavigationController,
            WallpaperPreviewNavigator wallpaperPreviewNavigator,
            Bundle savedInstanceState,
            DisplayUtils displayUtils) {
        mActivity = activity;
        mLifecycleOwner = lifecycleOwner;
        mPermissionRequester = permissionRequester;
        mAppContext = mActivity.getApplicationContext();
        mWallpaperColorsViewModel = colorsViewModel;
        mOnThemingChanged = onThemingChanged;
        mSectionNavigationController = sectionNavigationController;
        mWallpaperPreviewNavigator = wallpaperPreviewNavigator;
        mSavedInstanceState = savedInstanceState;
        mDisplayUtils = displayUtils;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    @MainThread
    public void onResume() {
        refreshCurrentWallpapers(/* forceRefresh= */ true);
        updateLivePreviewVisibility(true);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    @MainThread
    public void onPause() {
        updateLivePreviewVisibility(false);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    @MainThread
    public void onStop() {
        disconnectHomeLiveWallpaper();
        disconnectLockLiveWallpaper();
    }

    @Override
    public boolean shouldRetainInstanceWhenSwitchingTabs() {
        return true;
    }

    @Override
    public boolean isAvailable(Context context) {
        return true;
    }

    @Override
    public WallpaperSectionView createView(Context context) {
        WallpaperSectionView wallpaperSectionView = (WallpaperSectionView) LayoutInflater.from(
                context).inflate(R.layout.wallpaper_section_view, /* root= */ null);
        mHomePreviewCard = wallpaperSectionView.findViewById(R.id.home_preview);
        mHomePreviewCard.setContentDescription(mAppContext.getString(
                R.string.wallpaper_preview_card_content_description));
        mWorkspaceSurface = mHomePreviewCard.findViewById(R.id.workspace_surface);
        mHomePreviewProgress = mHomePreviewCard.findViewById(R.id.wallpaper_preview_spinner);
        mWorkspaceSurfaceCallback = new WorkspaceSurfaceHolderCallback(
                mWorkspaceSurface,
                new PreviewUtils(
                        mAppContext, mAppContext.getString(R.string.grid_control_metadata_name)));
        mHomeWallpaperSurface = mHomePreviewCard.findViewById(R.id.wallpaper_surface);
        mHomeFadeInScrim = mHomePreviewCard.findViewById(R.id.wallpaper_fadein_scrim);

        Future<ColorInfo> colorFuture = CompletableFuture.completedFuture(
                new ColorInfo(/* wallpaperColors= */ null,
                        ResourceUtils.getColorAttr(mActivity, android.R.attr.colorSecondary)));

        mHomeWallpaperSurfaceCallback = new WallpaperSurfaceCallback(mActivity, mHomePreviewCard,
                mHomeWallpaperSurface, colorFuture, () -> {
            if (mHomePreviewWallpaperInfo != null) {
                maybeLoadThumbnail(mHomePreviewWallpaperInfo, mHomeWallpaperSurfaceCallback,
                        mDisplayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(mActivity), true);
            }
        });

        mLockscreenPreviewCard = wallpaperSectionView.findViewById(R.id.lock_preview);
        mLockscreenPreviewCard.setContentDescription(mAppContext.getString(
                R.string.lockscreen_wallpaper_preview_card_content_description));
        mLockscreenPreviewProgress = mLockscreenPreviewCard.findViewById(
                R.id.wallpaper_preview_spinner);
        mLockscreenPreviewCard.findViewById(R.id.workspace_surface).setVisibility(View.GONE);
        mLockWallpaperSurface = mLockscreenPreviewCard.findViewById(R.id.wallpaper_surface);
        mLockFadeInScrim = mLockscreenPreviewCard.findViewById(R.id.wallpaper_fadein_scrim);
        mLockWallpaperSurfaceCallback = new WallpaperSurfaceCallback(mActivity,
                mLockscreenPreviewCard, mLockWallpaperSurface, colorFuture, () -> {
            if (mLockPreviewWallpaperInfo != null) {
                maybeLoadThumbnail(mLockPreviewWallpaperInfo, mLockWallpaperSurfaceCallback,
                        mDisplayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(mActivity), false);
            }
        });
        mLockPreviewContainer = mLockscreenPreviewCard.findViewById(
                R.id.lock_screen_preview_container);
        mLockPreviewContainer.setVisibility(View.INVISIBLE);
        mLockScreenPreviewer = new LockScreenPreviewer(mLifecycleOwner.getLifecycle(), context,
                mLockPreviewContainer);

        setupCurrentWallpaperPreview(wallpaperSectionView);
        final int shortDuration = mAppContext.getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        fadeWallpaperPreview(true, shortDuration);
        mLifecycleOwner.getLifecycle().addObserver(this);
        updateWallpaperSurface();
        updateWorkspaceSurface();

        wallpaperSectionView.findViewById(R.id.wallpaper_picker_entry).setOnClickListener(
                v -> mSectionNavigationController.navigateTo(new CategorySelectorFragment()));

        if (mOnThemingChanged != null) {
            mOnThemingChanged.observe(mLifecycleOwner, update ->
                    updateWorkspacePreview(mWorkspaceSurface, mWorkspaceSurfaceCallback,
                            mWallpaperColorsViewModel.getHomeWallpaperColors().getValue())
            );
        }

        return wallpaperSectionView;
    }

    private void updateLivePreviewVisibility(boolean visible) {
        if (mHomeWallpaperConnection != null) {
            mHomeWallpaperConnection.setVisibility(visible);
        }
        if (mLockWallpaperConnection != null) {
            mLockWallpaperConnection.setVisibility(visible);
        }
    }

    private void disconnectHomeLiveWallpaper() {
        if (mHomeWallpaperConnection != null) {
            mHomeWallpaperConnection.disconnect();
            mHomeWallpaperConnection = null;
        }
    }

    private void disconnectLockLiveWallpaper() {
        if (mLockWallpaperConnection != null) {
            mLockWallpaperConnection.disconnect();
            mLockWallpaperConnection = null;
        }
    }

    private void updateWorkspacePreview(SurfaceView workspaceSurface,
            WorkspaceSurfaceHolderCallback callback, @Nullable WallpaperColors colors) {
        // Reattach SurfaceView to trigger #surfaceCreated to update preview for different option.
        ViewGroup parent = (ViewGroup) workspaceSurface.getParent();
        int viewIndex = parent.indexOfChild(workspaceSurface);
        parent.removeView(workspaceSurface);
        if (callback != null) {
            callback.resetLastSurface();
            callback.setHideBottomRow(false);
            callback.setWallpaperColors(colors);
            callback.maybeRenderPreview();
        }
        parent.addView(workspaceSurface, viewIndex);
    }

    @Override
    public void release() {
        if (mLockScreenPreviewer != null) {
            mLockScreenPreviewer.release();
            mLockScreenPreviewer = null;
        }
        if (mHomeWallpaperSurfaceCallback != null) {
            mHomeWallpaperSurfaceCallback.cleanUp();
        }
        if (mLockWallpaperSurfaceCallback != null) {
            mLockWallpaperSurfaceCallback.cleanUp();
        }
        if (mWorkspaceSurfaceCallback != null) {
            mWorkspaceSurfaceCallback.cleanUp();
        }
        mLifecycleOwner.getLifecycle().removeObserver(this);
    }

    private void setupCurrentWallpaperPreview(View rootView) {
        if (canShowCurrentWallpaper()) {
            showCurrentWallpaper(rootView, true);
        } else {
            showCurrentWallpaper(rootView, false);

            Button mAllowAccessButton = rootView
                    .findViewById(R.id.permission_needed_allow_access_button);
            mAllowAccessButton.setOnClickListener(view ->
                    mPermissionRequester.requestExternalStoragePermission(
                            new MyPhotosStarter.PermissionChangedListener() {

                                @Override
                                public void onPermissionsGranted() {
                                    showCurrentWallpaper(rootView, true);
                                }

                                @Override
                                public void onPermissionsDenied(boolean dontAskAgain) {
                                    if (!dontAskAgain) {
                                        return;
                                    }
                                    showPermissionNeededDialog();
                                }
                            })
            );

            // Replace explanation text with text containing the Wallpapers app name which replaces
            // the placeholder.
            Resources resources = mAppContext.getResources();
            String appName = resources.getString(R.string.app_name);
            String explanation = resources.getString(R.string.permission_needed_explanation,
                    appName);
            TextView explanationView = rootView.findViewById(R.id.permission_needed_explanation);
            explanationView.setText(explanation);
        }
    }

    private boolean canShowCurrentWallpaper() {
        return isPermissionGranted(mAppContext, PERMISSION_READ_WALLPAPER_INTERNAL)
                || isPermissionGranted(mAppContext, READ_MEDIA_IMAGES);
    }

    private boolean isPermissionGranted(Context context, String permission) {
        return context.getPackageManager().checkPermission(permission,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    private void showCurrentWallpaper(View rootView, boolean show) {
        rootView.findViewById(R.id.home_preview)
                .setVisibility(show ? View.VISIBLE : View.GONE);
        rootView.findViewById(R.id.lock_preview)
                .setVisibility(show ? View.VISIBLE : View.GONE);
        rootView.findViewById(R.id.permission_needed)
                .setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showPermissionNeededDialog() {
        String permissionNeededMessage = mAppContext.getResources().getString(
                R.string.permission_needed_explanation_go_to_settings);
        AlertDialog dialog = new AlertDialog.Builder(mAppContext, R.style.LightDialogTheme)
                .setMessage(permissionNeededMessage)
                .setPositiveButton(android.R.string.ok, /* onClickListener= */ null)
                .setNegativeButton(
                        R.string.settings_button_label,
                        (dialogInterface, i) -> {
                            Intent appInfoIntent = new Intent();
                            appInfoIntent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package",
                                    mAppContext.getPackageName(), /* fragment= */ null);
                            appInfoIntent.setData(uri);
                            mActivity.startActivityForResult(appInfoIntent,
                                    SETTINGS_APP_INFO_REQUEST_CODE);
                        })
                .create();
        dialog.show();
    }

    /**
     * Obtains the {@link WallpaperInfo} object(s) representing the wallpaper(s) currently set to
     * the device from the {@link CurrentWallpaperInfoFactory}.
     */
    private void refreshCurrentWallpapers(boolean forceRefresh) {
        CurrentWallpaperInfoFactory factory = InjectorProvider.getInjector()
                .getCurrentWallpaperInfoFactory(mAppContext);

        factory.createCurrentWallpaperInfos(
                (homeWallpaper, lockWallpaper, presentationMode) -> {
                    // A config change may have destroyed the activity since the refresh
                    // started, so check for that.
                    if (!isActivityAlive()) {
                        return;
                    }

                    mHomePreviewWallpaperInfo = homeWallpaper;
                    mLockPreviewWallpaperInfo =
                            lockWallpaper == null ? homeWallpaper : lockWallpaper;

                    mHomePreviewWallpaperInfo.computeColorInfo(mAppContext);
                    if (lockWallpaper != null) {
                        lockWallpaper.computeColorInfo(mAppContext);
                    }
                    updatePreview(mHomePreviewWallpaperInfo, true);
                    updatePreview(mLockPreviewWallpaperInfo, false);

                    WallpaperManager manager = WallpaperManager.getInstance(mAppContext);

                    WallpaperColors homeColors =
                            manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                    onHomeWallpaperColorsChanged(homeColors);
                    WallpaperColors lockColors = homeColors;

                    if (lockWallpaper != null) {
                        lockColors = manager.getWallpaperColors(WallpaperManager.FLAG_LOCK);

                    }
                    onLockWallpaperColorsChanged(lockColors);

                    // If we need to do the scrim fade, show the scrim first.
                    if (VideoWallpaperUtils.needsFadeIn(mHomePreviewWallpaperInfo)) {
                        mHomeFadeInScrim.animate().cancel();
                        mHomeFadeInScrim.setAlpha(1f);
                        mHomeFadeInScrim.setVisibility(View.VISIBLE);
                    }
                    if (VideoWallpaperUtils.needsFadeIn(mLockPreviewWallpaperInfo)) {
                        mLockFadeInScrim.animate().cancel();
                        mLockFadeInScrim.setAlpha(1f);
                        mLockFadeInScrim.setVisibility(View.VISIBLE);
                    }
                }, forceRefresh);
    }

    private void updatePreview(WallpaperInfo wallpaperInfo, boolean isHomeWallpaper) {
        if (wallpaperInfo == null) {
            return;
        }

        if (!isActivityAlive()) {
            return;
        }

        UserEventLogger eventLogger = InjectorProvider.getInjector().getUserEventLogger(
                mAppContext);

        WallpaperSurfaceCallback surfaceCallback = isHomeWallpaper
                ? mHomeWallpaperSurfaceCallback : mLockWallpaperSurfaceCallback;
        // Load thumb regardless of live wallpaper to make sure we have a placeholder while
        // the live wallpaper initializes in that case.
        maybeLoadThumbnail(wallpaperInfo, surfaceCallback,
                mDisplayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(mActivity), isHomeWallpaper);

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mActivity);
        if (wallpaperManager.isLockscreenLiveWallpaperEnabled()) {
            if (isHomeWallpaper) {
                disconnectHomeLiveWallpaper();
            } else {
                disconnectLockLiveWallpaper();
            }
            if (wallpaperInfo instanceof LiveWallpaperInfo) {
                setUpLiveWallpaperPreview(wallpaperInfo, isHomeWallpaper);
            }
        } else {
            if (isHomeWallpaper) {
                disconnectHomeLiveWallpaper();
                if (wallpaperInfo instanceof LiveWallpaperInfo) {
                    setUpLiveWallpaperPreviewLegacy(wallpaperInfo);
                }
            }
        }

        View preview = isHomeWallpaper ? mHomePreviewCard : mLockscreenPreviewCard;
        preview.setOnClickListener(view -> {
            mWallpaperPreviewNavigator.showViewOnlyPreview(wallpaperInfo, isHomeWallpaper);
            eventLogger.logCurrentWallpaperPreviewed();
        });
    }

    @NonNull
    private Asset maybeLoadThumbnail(WallpaperInfo wallpaperInfo,
            WallpaperSurfaceCallback surfaceCallback, boolean offsetToStart, boolean isHome) {
        ImageView liveThumbnailView = isHome ? mHomeFadeInScrim : mLockFadeInScrim;
        ImageView imageView = VideoWallpaperUtils.needsFadeIn(wallpaperInfo) ? liveThumbnailView
                : surfaceCallback.getHomeImageWallpaper();
        Asset thumbAsset = wallpaperInfo.getThumbAsset(mAppContext);
        // Respect offsetToStart only for CurrentWallpaperAssetVN otherwise true.
        offsetToStart = !(thumbAsset instanceof CurrentWallpaperAssetVN) || offsetToStart;
        thumbAsset = new BitmapCachingAsset(mAppContext, thumbAsset);
        if (imageView != null && imageView.getDrawable() == null) {
            if (VideoWallpaperUtils.needsFadeIn(wallpaperInfo)) {
                imageView.setRenderEffect(
                        RenderEffect.createBlurEffect(50f, 50f, TileMode.CLAMP));
            }
            thumbAsset.loadPreviewImage(mActivity, imageView,
                    ResourceUtils.getColorAttr(mActivity, android.R.attr.colorSecondary),
                    offsetToStart);
        }
        return thumbAsset;
    }

    private void onHomeWallpaperColorsChanged(WallpaperColors wallpaperColors) {
        if (wallpaperColors != null && wallpaperColors.equals(
                mWallpaperColorsViewModel.getHomeWallpaperColors().getValue())) {
            return;
        }
        mWallpaperColorsViewModel.setHomeWallpaperColors(wallpaperColors);
    }

    private void onLockWallpaperColorsChanged(WallpaperColors wallpaperColors) {
        if (wallpaperColors != null && wallpaperColors.equals(
                mWallpaperColorsViewModel.getLockWallpaperColors().getValue())) {
            return;
        }
        mWallpaperColorsViewModel.setLockWallpaperColors(wallpaperColors);
        if (mLockScreenPreviewer != null) {
            mLockScreenPreviewer.setColor(wallpaperColors);
        }
    }

    private void setUpLiveWallpaperPreview(WallpaperInfo wallpaper, boolean isHomeWallpaper) {
        if (!isActivityAlive() || !WallpaperConnection.isPreviewAvailable()) {
            return;
        }

        final boolean isHomeBoth = (mHomePreviewWallpaperInfo == mLockPreviewWallpaperInfo);
        if (isHomeBoth && !isHomeWallpaper) {
            // If home and lock are the same the preview is handled by mirroring the home preview,
            // so the lock preview is a no-op.
            return;
        }

        final SurfaceView mainSurface =
                isHomeWallpaper ? mHomeWallpaperSurface : mLockWallpaperSurface;
        final SurfaceView mirrorSurface = isHomeBoth ? mLockWallpaperSurface : null;
        final WallpaperConnection connection = new WallpaperConnection(
                getWallpaperIntent(wallpaper.getWallpaperComponent()), mActivity,
                new WallpaperConnection.WallpaperConnectionListener() {
                    @Override
                    public void onWallpaperColorsChanged(WallpaperColors colors, int displayId) {
                        if (isHomeWallpaper) {
                            onHomeWallpaperColorsChanged(colors);
                            if (isHomeBoth && mLockScreenPreviewer != null) {
                                mLockScreenPreviewer.setColor(colors);
                                onLockWallpaperColorsChanged(colors);
                            }
                        } else {
                            onLockWallpaperColorsChanged(colors);
                        }
                    }
                },
                mainSurface, mirrorSurface);

        connection.setVisibility(true);
        if (isHomeWallpaper) {
            mHomeWallpaperConnection = connection;
        } else {
            mLockWallpaperConnection = connection;
        }
        mainSurface.post(() -> {
            if (mHomeWallpaperConnection != null && !mHomeWallpaperConnection.connect()) {
                mHomeWallpaperConnection = null;
            }
            if (mLockWallpaperConnection != null && !mLockWallpaperConnection.connect()) {
                mLockWallpaperConnection = null;
            }
        });
    }

    private void setUpLiveWallpaperPreviewLegacy(WallpaperInfo homeWallpaper) {
        if (!isActivityAlive()) {
            return;
        }

        if (WallpaperConnection.isPreviewAvailable()) {
            final boolean isLockLive = mLockPreviewWallpaperInfo instanceof LiveWallpaperInfo;
            mHomeWallpaperConnection = new WallpaperConnection(
                    getWallpaperIntent(homeWallpaper.getWallpaperComponent()), mActivity,
                    new WallpaperConnection.WallpaperConnectionListener() {
                        @Override
                        public void onWallpaperColorsChanged(WallpaperColors colors,
                                int displayId) {
                            if (isLockLive && mLockScreenPreviewer != null) {
                                mLockScreenPreviewer.setColor(colors);
                                onLockWallpaperColorsChanged(colors);
                            }
                            onHomeWallpaperColorsChanged(colors);
                        }

                        @Override
                        public void onEngineShown() {
                            if (VideoWallpaperUtils.needsFadeIn(homeWallpaper)) {
                                mHomeFadeInScrim.animate().alpha(0.0f)
                                        .setDuration(VideoWallpaperUtils.TRANSITION_MILLIS)
                                        .withEndAction(() -> mHomeFadeInScrim.setVisibility(
                                                View.INVISIBLE));
                                if (isLockLive) {
                                    mLockFadeInScrim.animate().alpha(0.0f)
                                            .setDuration(VideoWallpaperUtils.TRANSITION_MILLIS)
                                            .withEndAction(() -> mLockFadeInScrim.setVisibility(
                                                    View.INVISIBLE));
                                }
                            }
                        }
                    },
                    mHomeWallpaperSurface, isLockLive ? mLockWallpaperSurface : null);

            mHomeWallpaperConnection.setVisibility(true);
            mHomeWallpaperSurface.post(() -> {
                if (mHomeWallpaperConnection != null && !mHomeWallpaperConnection.connect()) {
                    mHomeWallpaperConnection = null;
                }
            });
        }
    }

    private Intent getWallpaperIntent(android.app.WallpaperInfo info) {
        return new Intent(WallpaperService.SERVICE_INTERFACE)
                .setClassName(info.getPackageName(), info.getServiceName());
    }

    private void updateWallpaperSurface() {
        mHomeWallpaperSurface.getHolder().addCallback(mHomeWallpaperSurfaceCallback);
        mHomeWallpaperSurface.setZOrderMediaOverlay(true);
        mLockWallpaperSurface.getHolder().addCallback(mLockWallpaperSurfaceCallback);
        mLockWallpaperSurface.setZOrderMediaOverlay(true);
    }

    private void updateWorkspaceSurface() {
        mWorkspaceSurface.setZOrderMediaOverlay(true);
        mWorkspaceSurface.getHolder().addCallback(mWorkspaceSurfaceCallback);
    }

    private boolean isActivityAlive() {
        return !mActivity.isDestroyed() && !mActivity.isFinishing();
    }

    // TODO(b/276439056) Remove these animations as they have no effect
    private void fadeWallpaperPreview(boolean isFadeIn, int duration) {
        setupFade(mHomePreviewCard, mHomePreviewProgress, duration, isFadeIn);
        setupFade(mLockscreenPreviewCard, mLockscreenPreviewProgress, duration, isFadeIn);
    }

    private void setupFade(CardView cardView, ContentLoadingProgressBar progressBar, int duration,
            boolean fadeIn) {
        cardView.setAlpha(fadeIn ? 0.0f : 1.0f);
        cardView.animate()
                .alpha(fadeIn ? 1.0f : 0.0f)
                .setDuration(duration)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationCancel(Animator animator) {
                        progressBar.hide();
                        setWallpaperPreviewsVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        progressBar.hide();
                        setWallpaperPreviewsVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {
                    }

                    @Override
                    public void onAnimationStart(Animator animator) {
                        setWallpaperPreviewsVisibility(View.INVISIBLE);
                    }
                });
        progressBar.animate()
                .alpha(fadeIn ? 1.0f : 0.0f)
                .setDuration(duration * 2)
                .setStartDelay(duration)
                .withStartAction(progressBar::show)
                .withEndAction(progressBar::hide);
    }

    private void setWallpaperPreviewsVisibility(int visibility) {
        if (mHomeWallpaperSurface != null) {
            mHomeWallpaperSurface.setVisibility(visibility);
        }
        if (mLockWallpaperSurface != null) {
            mLockWallpaperSurface.setVisibility(visibility);
        }
        if (mWorkspaceSurface != null) {
            mWorkspaceSurface.setVisibility(visibility);
        }
        if (mLockPreviewContainer != null) {
            mLockPreviewContainer.setVisibility(visibility);
        }
    }

    @Override
    public void onTransitionOut() {
        if (mHomeWallpaperSurface != null) {
            mHomeWallpaperSurface.setUseAlpha();
            mHomeWallpaperSurface.setAlpha(0f);
        }
        if (mLockWallpaperSurface != null) {
            mLockWallpaperSurface.setUseAlpha();
            mLockWallpaperSurface.setAlpha(0f);
        }
        if (mWorkspaceSurface != null) {
            mWorkspaceSurface.setUseAlpha();
            mWorkspaceSurface.setAlpha(0f);
        }
        if (mLockPreviewContainer != null) {
            mLockPreviewContainer.setAlpha(0f);
        }
    }
}
