package com.android.wallpaper.module;

import static android.app.WallpaperManager.FLAG_LOCK;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.UserEventLogger.WallpaperSetFailureReason;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.picker.SetWallpaperDialogFragment;
import com.android.wallpaper.picker.SetWallpaperDialogFragment.Listener;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.ThrowableAnalyzer;
import com.android.wallpaper.util.WallpaperCropUtils;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Helper class used to set the current wallpaper. It handles showing the destination request dialog
 * and actually setting the wallpaper on a given destination.
 * It is expected to be instantiated within a Fragment or Activity, and {@link #cleanUp()} should
 * be called from its owner's onDestroy method (or equivalent).
 */
public class WallpaperSetter {

    private static final String TAG = "WallpaperSetter";
    private static final String PROGRESS_DIALOG_NO_TITLE = null;
    private static final boolean PROGRESS_DIALOG_INDETERMINATE = true;

    private static final int UNUSED_REQUEST_CODE = 1;
    private static final String TAG_SET_WALLPAPER_DIALOG_FRAGMENT = "set_wallpaper_dialog";

    private final WallpaperPersister mWallpaperPersister;
    private final WallpaperPreferences mPreferences;
    private final boolean mTestingModeEnabled;
    private final UserEventLogger mUserEventLogger;
    private ProgressDialog mProgressDialog;
    private Optional<Integer> mCurrentScreenOrientation = Optional.empty();

    public WallpaperSetter(WallpaperPersister wallpaperPersister,
            WallpaperPreferences preferences, UserEventLogger userEventLogger,
            boolean isTestingModeEnabled) {
        mTestingModeEnabled = isTestingModeEnabled;
        mWallpaperPersister = wallpaperPersister;
        mPreferences = preferences;
        mUserEventLogger = userEventLogger;
    }

    /**
     * Sets current wallpaper to the device with the minimum scale to fit the screen size.
     *
     * @param containerActivity main Activity that owns the current fragment
     * @param wallpaper info for the actual wallpaper to set
     * @param destination the wallpaper destination i.e. home vs. lockscreen vs. both.
     * @param callback optional callback to be notified when the wallpaper is set.
     */
    public void setCurrentWallpaper(Activity containerActivity, WallpaperInfo wallpaper,
                                    @Destination final int destination,
                                    @Nullable SetWallpaperCallback callback) {
        Asset wallpaperAsset = wallpaper.getAsset(containerActivity.getApplicationContext());
        wallpaperAsset.decodeRawDimensions(containerActivity, dimensions -> {
            if (dimensions == null) {
                Log.e(TAG, "Raw wallpaper's dimensions are null");
                return;
            }

            Display defaultDisplay = containerActivity.getWindowManager().getDefaultDisplay();
            Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(defaultDisplay);
            Rect visibleRawWallpaperRect =
                    WallpaperCropUtils.calculateVisibleRect(dimensions, screenSize);
            float wallpaperScale = WallpaperCropUtils.calculateMinZoom(dimensions, screenSize);
            Rect cropRect = WallpaperCropUtils.calculateCropRect(
                    containerActivity.getApplicationContext(), defaultDisplay,
                    dimensions, visibleRawWallpaperRect, wallpaperScale);

            setCurrentWallpaper(containerActivity, wallpaper, wallpaperAsset, destination,
                    wallpaperScale, cropRect, null, callback);
        });
    }

    /**
     * Sets current wallpaper to the device based on current zoom and scroll state.
     *
     * @param containerActivity main Activity that owns the current fragment
     * @param wallpaper info for the actual wallpaper to set
     * @param wallpaperAsset  Wallpaper asset from which to retrieve image data.
     * @param destination The wallpaper destination i.e. home vs. lockscreen vs. both.
     * @param wallpaperScale Scaling factor applied to the source image before setting the
     *                       wallpaper to the device.
     * @param cropRect Desired crop area of the wallpaper in post-scale units. If null, then the
     *                 wallpaper image will be set without any scaling or cropping.
     * @param callback optional callback to be notified when the wallpaper is set.
     */
    public void setCurrentWallpaper(Activity containerActivity, WallpaperInfo wallpaper,
            @Nullable Asset wallpaperAsset, @Destination final int destination,
            float wallpaperScale, @Nullable Rect cropRect, WallpaperColors wallpaperColors,
            @Nullable SetWallpaperCallback callback) {
        if (wallpaper instanceof LiveWallpaperInfo) {
            setCurrentLiveWallpaper(containerActivity, (LiveWallpaperInfo) wallpaper, destination,
                    wallpaperColors, callback);
            return;
        }
        mPreferences.setPendingWallpaperSetStatus(
                WallpaperPreferences.WALLPAPER_SET_PENDING);

        // Save current screen rotation so we can temporarily disable rotation while setting the
        // wallpaper and restore after setting the wallpaper finishes.
        saveAndLockScreenOrientationIfNeeded(containerActivity);

        // Clear MosaicView tiles and Glide's cache and pools to reclaim memory for final cropped
        // bitmap.
        Glide.get(containerActivity).clearMemory();

        // ProgressDialog endlessly updates the UI thread, keeping it from going idle which therefore
        // causes Espresso to hang once the dialog is shown.
        if (!mTestingModeEnabled && !containerActivity.isFinishing()) {
            int themeResId = (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP)
                    ? R.style.ProgressDialogThemePreL : R.style.LightDialogTheme;
            mProgressDialog = new ProgressDialog(containerActivity, themeResId);

            mProgressDialog.setTitle(PROGRESS_DIALOG_NO_TITLE);
            mProgressDialog.setMessage(containerActivity.getString(
                            R.string.set_wallpaper_progress_message));
            mProgressDialog.setIndeterminate(PROGRESS_DIALOG_INDETERMINATE);
            if (containerActivity instanceof LifecycleOwner) {
                ((LifecycleOwner) containerActivity).getLifecycle().addObserver(
                        new LifecycleEventObserver() {
                            @Override
                            public void onStateChanged(@NonNull LifecycleOwner source,
                                    @NonNull Event event) {
                                if (event == Event.ON_DESTROY) {
                                    if (mProgressDialog != null) {
                                        mProgressDialog.dismiss();
                                        mProgressDialog = null;
                                    }
                                }
                            }
                        });
            }
            mProgressDialog.show();
        }

        mWallpaperPersister.setIndividualWallpaper(
                wallpaper, wallpaperAsset, cropRect,
                wallpaperScale, destination, new SetWallpaperCallback() {
                    @Override
                    public void onSuccess(WallpaperInfo wallpaperInfo,
                            @Destination int destination) {
                        onWallpaperApplied(wallpaper, containerActivity);
                        if (callback != null) {
                            callback.onSuccess(wallpaper, destination);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        onWallpaperApplyError(throwable, containerActivity);
                        if (callback != null) {
                            callback.onError(throwable);
                        }
                    }
                });
    }

    private void setCurrentLiveWallpaper(Activity activity, LiveWallpaperInfo wallpaper,
            @Destination final int destination, WallpaperColors colors,
            @Nullable SetWallpaperCallback callback) {
        try {
            // Save current screen rotation so we can temporarily disable rotation while setting the
            // wallpaper and restore after setting the wallpaper finishes.
            saveAndLockScreenOrientationIfNeeded(activity);

            WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
            if (destination == WallpaperPersister.DEST_LOCK_SCREEN
                    && !wallpaperManager.isLockscreenLiveWallpaperEnabled()) {
                throw new IllegalArgumentException(
                    "Live wallpaper cannot be applied on lock screen only");
            }

            setWallpaperComponent(wallpaperManager, wallpaper, destination);
            wallpaperManager.setWallpaperOffsetSteps(0.5f /* xStep */, 0.0f /* yStep */);
            wallpaperManager.setWallpaperOffsets(
                    activity.getWindow().getDecorView().getRootView().getWindowToken(),
                    0.5f /* xOffset */, 0.0f /* yOffset */);
            mPreferences.storeLatestWallpaper(WallpaperPersister.destinationToFlags(destination),
                    wallpaper.getWallpaperId(), wallpaper, colors);
            onWallpaperApplied(wallpaper, activity);
            if (callback != null) {
                callback.onSuccess(wallpaper, destination);
            }
            mWallpaperPersister.onLiveWallpaperSet(destination);
        } catch (RuntimeException | IOException e) {
            onWallpaperApplyError(e, activity);
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    private void setWallpaperComponent(WallpaperManager wallpaperManager,
            LiveWallpaperInfo wallpaper, int destination) throws IOException {
        try {
            Method m = wallpaperManager.getClass().getMethod("setWallpaperComponentWithFlags",
                    ComponentName.class, int.class);
            wallpaperManager.setWallpaperComponentWithFlags(
                    wallpaper.getWallpaperComponent().getComponent(),
                    WallpaperPersister.destinationToFlags(destination));
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "setWallpaperComponentWithFlags not available, using setWallpaperComponent");
            wallpaperManager.setWallpaperComponent(
                    wallpaper.getWallpaperComponent().getComponent());
        }
        if (!wallpaperManager.isLockscreenLiveWallpaperEnabled()
                && destination == WallpaperPersister.DEST_BOTH) {
            wallpaperManager.clear(FLAG_LOCK);
        }
    }

    /**
     * Sets current live wallpaper to the device (restore case)
     *
     * @param context The context for initiating wallpaper manager
     * @param wallpaper Information for the actual wallpaper to set
     * @param destination The wallpaper destination i.e. home vs. lockscreen vs. both
     * @param colors The {@link WallpaperColors} for placeholder of quickswitching
     * @param callback Optional callback to be notified when the wallpaper is set.
     */
    public void setCurrentLiveWallpaper(Context context, LiveWallpaperInfo wallpaper,
            @Destination final int destination, @Nullable WallpaperColors colors,
            @Nullable SetWallpaperCallback callback) {
        try {
            if (destination == WallpaperPersister.DEST_LOCK_SCREEN) {
                throw new IllegalArgumentException(
                        "Live wallpaper cannot be applied on lock screen only");
            }
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            setWallpaperComponent(wallpaperManager, wallpaper, destination);
            mPreferences.storeLatestWallpaper(WallpaperPersister.destinationToFlags(destination),
                    wallpaper.getWallpaperId(),
                    wallpaper, colors != null ? colors :
                            WallpaperColors.fromBitmap(wallpaper.getThumbAsset(context)
                                    .getLowResBitmap(context)));
            // Not call onWallpaperApplied() as no UI is presented.
            if (callback != null) {
                callback.onSuccess(wallpaper, destination);
            }
            mWallpaperPersister.onLiveWallpaperSet(destination);
        } catch (RuntimeException | IOException e) {
            // Not call onWallpaperApplyError() as no UI is presented.
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    private void onWallpaperApplied(WallpaperInfo wallpaper, Activity containerActivity) {
        mUserEventLogger.logWallpaperSet(
                wallpaper.getCollectionId(containerActivity),
                wallpaper.getWallpaperId(), wallpaper.getEffectNames());
        mPreferences.setPendingWallpaperSetStatus(
                WallpaperPreferences.WALLPAPER_SET_NOT_PENDING);
        mUserEventLogger.logWallpaperSetResult(
                UserEventLogger.WALLPAPER_SET_RESULT_SUCCESS);
        cleanUp();
        restoreScreenOrientationIfNeeded(containerActivity);
    }

    private void onWallpaperApplyError(Throwable throwable, Activity containerActivity) {
        mPreferences.setPendingWallpaperSetStatus(
                WallpaperPreferences.WALLPAPER_SET_NOT_PENDING);
        mUserEventLogger.logWallpaperSetResult(
                UserEventLogger.WALLPAPER_SET_RESULT_FAILURE);
        @WallpaperSetFailureReason int failureReason = ThrowableAnalyzer.isOOM(
                throwable)
                ? UserEventLogger.WALLPAPER_SET_FAILURE_REASON_OOM
                : UserEventLogger.WALLPAPER_SET_FAILURE_REASON_OTHER;
        mUserEventLogger.logWallpaperSetFailureReason(failureReason);

        cleanUp();
        restoreScreenOrientationIfNeeded(containerActivity);
    }

    /**
     * Call this method to clean up this instance's state.
     */
    public void cleanUp() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Show a dialog asking the user for the Wallpaper's destination
     * (eg, "Home screen", "Lock Screen")
     * @param isLiveWallpaper whether the wallpaper that we want to set is a live wallpaper.
     * @param listener {@link SetWallpaperDialogFragment.Listener} that will receive the response.
     * @see Destination
     */
    public void requestDestination(Activity activity, FragmentManager fragmentManager,
                                   Listener listener, boolean isLiveWallpaper) {
        requestDestination(activity, fragmentManager, R.string.set_wallpaper_dialog_message,
                listener, isLiveWallpaper);
    }

    /**
     * Show a dialog asking the user for the Wallpaper's destination
     * (eg, "Home screen", "Lock Screen")
     * @param isLiveWallpaper whether the wallpaper that we want to set is a live wallpaper.
     * @param listener {@link SetWallpaperDialogFragment.Listener} that will receive the response.
     * @param titleResId title for the dialog
     * @see Destination
     */
    public void requestDestination(Activity activity, FragmentManager fragmentManager,
            @StringRes int titleResId, Listener listener, boolean isLiveWallpaper) {
        saveAndLockScreenOrientationIfNeeded(activity);
        Listener listenerWrapper = new Listener() {
            @Override
            public void onSet(int destination) {
                if (listener != null) {
                    listener.onSet(destination);
                }
            }

            @Override
            public void onDialogDismissed(boolean withItemSelected) {
                if (!withItemSelected) {
                    restoreScreenOrientationIfNeeded(activity);
                }
                if (listener != null) {
                    listener.onDialogDismissed(withItemSelected);
                }
            }
        };

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
        SetWallpaperDialogFragment setWallpaperDialog = new SetWallpaperDialogFragment();
        setWallpaperDialog.setTitleResId(titleResId);
        setWallpaperDialog.setListener(listenerWrapper);
        if (wallpaperManager.isLockscreenLiveWallpaperEnabled()) {
            setWallpaperDialog.show(fragmentManager, TAG_SET_WALLPAPER_DIALOG_FRAGMENT);
            return;
        }

        WallpaperStatusChecker wallpaperStatusChecker =
                InjectorProvider.getInjector().getWallpaperStatusChecker();
        boolean isLiveWallpaperSet =
                WallpaperManager.getInstance(activity).getWallpaperInfo() != null;
        // Alternative of ag/15567276
        boolean isBuiltIn = !isLiveWallpaperSet
                && !wallpaperStatusChecker.isHomeStaticWallpaperSet(activity);

        if ((isLiveWallpaperSet || isBuiltIn)
                && !wallpaperStatusChecker.isLockWallpaperSet(activity)) {
            if (isLiveWallpaper) {
                // If lock wallpaper is live and we're setting a live wallpaper, we can only
                // set it to both, so bypass the dialog.
                listener.onSet(WallpaperPersister.DEST_BOTH);
                restoreScreenOrientationIfNeeded(activity);
                return;
            }
            // if the lock wallpaper is a live wallpaper, we cannot set a home-only static one
            setWallpaperDialog.setHomeOptionAvailable(false);
        }
        if (isLiveWallpaper) {
            setWallpaperDialog.setLockOptionAvailable(false);
        }
        setWallpaperDialog.show(fragmentManager, TAG_SET_WALLPAPER_DIALOG_FRAGMENT);
    }

    private void saveAndLockScreenOrientationIfNeeded(Activity activity) {
        if (!mCurrentScreenOrientation.isPresent()) {
            mCurrentScreenOrientation = Optional.of(activity.getRequestedOrientation());
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }
    }

    private void restoreScreenOrientationIfNeeded(Activity activity) {
        mCurrentScreenOrientation.ifPresent(orientation -> {
            if (activity.getRequestedOrientation() != orientation) {
                activity.setRequestedOrientation(orientation);
            }
            mCurrentScreenOrientation = Optional.empty();
        });
    }
}