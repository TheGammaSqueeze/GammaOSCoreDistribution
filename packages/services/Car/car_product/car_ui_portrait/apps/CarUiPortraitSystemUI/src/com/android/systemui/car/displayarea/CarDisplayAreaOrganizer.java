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

package com.android.systemui.car.displayarea;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.systemui.car.displayarea.CarDisplayAreaController.BACKGROUND_LAYER_INDEX;
import static com.android.systemui.car.displayarea.CarDisplayAreaController.CONTROL_BAR_LAYER_INDEX;

import android.annotation.NonNull;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.systemui.R;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Organizer for controlling the policies defined in
 * {@link com.android.server.wm.CarDisplayAreaPolicyProvider}
 */
public class CarDisplayAreaOrganizer extends DisplayAreaOrganizer {

    /**
     * The display partition to launch applications by default.
     */
    public static final int FOREGROUND_DISPLAY_AREA_ROOT = FEATURE_VENDOR_FIRST + 1;
    /**
     * Background applications task container.
     */
    public static final int BACKGROUND_TASK_CONTAINER = FEATURE_VENDOR_FIRST + 2;
    /**
     * Control bar task container.
     */
    public static final int CONTROL_BAR_DISPLAY_AREA = FEATURE_VENDOR_FIRST + 4;
    public static final int FEATURE_TITLE_BAR = FEATURE_VENDOR_FIRST + 5;
    static final int FEATURE_VOICE_PLATE = FEATURE_VENDOR_FIRST + 6;
    private static final String TAG = "CarDisplayAreaOrganizer";
    private final ComponentName mControlBarActivityName;
    private final List<ComponentName> mBackGroundActivities;

    private final Context mContext;
    private final SyncTransactionQueue mTransactionQueue;
    private final Rect mForegroundApplicationDisplayBounds = new Rect();
    private final Rect mBackgroundApplicationDisplayBounds = new Rect();
    private final CarDisplayAreaAnimationController mAnimationController;
    private final Handler mHandlerForAnimation;
    private final ArrayMap<WindowContainerToken, SurfaceControl> mDisplayAreaTokenMap =
            new ArrayMap();
    private final Car.CarServiceLifecycleListener mCarServiceLifecycleListener =
            new Car.CarServiceLifecycleListener() {
                @Override
                public void onLifecycleChanged(@NonNull Car car, boolean ready) {
                    if (ready) {
                        CarActivityManager carAm = (CarActivityManager) car.getCarManager(
                                Car.CAR_ACTIVITY_SERVICE);
                        for (ComponentName backgroundCmp : mBackGroundActivities) {
                            CarDisplayAreaUtils.setPersistentActivity(carAm, backgroundCmp,
                                    BACKGROUND_TASK_CONTAINER, "Background");
                        }
                        CarDisplayAreaUtils.setPersistentActivity(carAm, mControlBarActivityName,
                                CONTROL_BAR_DISPLAY_AREA, "ControlBar");
                    }
                }
            };
    DisplayAreaAnimationRunnable mDisplayAreaAnimationRunnable = null;
    private WindowContainerToken mBackgroundDisplayToken;
    private WindowContainerToken mForegroundDisplayToken;
    private int mDpiDensity = -1;
    private DisplayAreaAppearedInfo mBackgroundApplicationDisplay;
    private DisplayAreaAppearedInfo mForegroundApplicationDisplay;
    private DisplayAreaAppearedInfo mControlBarDisplay;
    private boolean mIsRegistered = false;
    private boolean mIsDisplayAreaAnimating = false;
    private DisplayAreaComponent.FOREGROUND_DA_STATE mToState;
    private CarDisplayAreaAnimationCallback mDisplayAreaAnimationCallback =
            new CarDisplayAreaAnimationCallback() {
                @Override
                public void onAnimationStart(
                        CarDisplayAreaAnimationController
                                .CarDisplayAreaTransitionAnimator animator) {

                    mIsDisplayAreaAnimating = true;

                    mTransactionQueue.runInSync(tx -> {
                        // Update the foreground panel layer index to animate on top of the
                        // background DA.
                        tx.setLayer(mBackgroundApplicationDisplay.getLeash(),
                                BACKGROUND_LAYER_INDEX);
                        tx.setLayer(mForegroundApplicationDisplay.getLeash(),
                                BACKGROUND_LAYER_INDEX + 1);
                        tx.setLayer(mControlBarDisplay.getLeash(),
                                CONTROL_BAR_LAYER_INDEX);
                    });
                }

                @Override
                public void onAnimationEnd(SurfaceControl.Transaction tx,
                        CarDisplayAreaAnimationController
                                .CarDisplayAreaTransitionAnimator animator) {
                    mIsDisplayAreaAnimating = false;
                    mAnimationController.removeAnimator(animator.getToken());
                    if (mAnimationController.isAnimatorsConsumed()) {
                        WindowContainerTransaction wct = new WindowContainerTransaction();
                        if (mToState == DisplayAreaComponent.FOREGROUND_DA_STATE.DEFAULT) {
                            // Foreground DA opens to default height.
                            updateBackgroundDisplayBounds(wct);
                        } else if (mToState
                                == DisplayAreaComponent.FOREGROUND_DA_STATE.FULL_TO_DEFAULT) {
                            updateForegroundDisplayBounds(wct);
                            updateBackgroundDisplayBounds(wct);
                        }
                        else if (mToState == DisplayAreaComponent.FOREGROUND_DA_STATE.CONTROL_BAR) {
                            Intent homeActivityIntent = new Intent(Intent.ACTION_MAIN);
                            homeActivityIntent.addCategory(Intent.CATEGORY_HOME);
                            homeActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            mContext.startActivity(homeActivityIntent);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(
                        CarDisplayAreaAnimationController
                                .CarDisplayAreaTransitionAnimator animator) {
                    mIsDisplayAreaAnimating = false;
                    mAnimationController.removeAnimator(animator.getToken());
                }
            };

    @Inject
    public CarDisplayAreaOrganizer(Executor executor, Context context, SyncTransactionQueue tx) {
        super(executor);
        mContext = context;
        mTransactionQueue = tx;
        mControlBarActivityName = ComponentName.unflattenFromString(
                context.getResources().getString(R.string.config_controlBarActivity));
        mBackGroundActivities = new ArrayList<>();
        String[] backgroundActivities = mContext.getResources().getStringArray(
                R.array.config_backgroundActivities);
        for (String backgroundActivity : backgroundActivities) {
            mBackGroundActivities
                    .add(ComponentName.unflattenFromString(backgroundActivity));
        }
        mAnimationController = new CarDisplayAreaAnimationController(mContext);
        mHandlerForAnimation = mContext.getMainThreadHandler();

        Car.createCar(context, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                mCarServiceLifecycleListener);
    }

    int getDpiDensity() {
        if (mDpiDensity != -1) {
            return mDpiDensity;
        }

        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        Resources displayResources = mContext.createDisplayContext(display).getResources();
        mDpiDensity = displayResources.getConfiguration().densityDpi;

        return mDpiDensity;
    }

    boolean isDisplayAreaAnimating() {
        return mIsDisplayAreaAnimating;
    }

    // WCT will be queued in updateBackgroundDisplayBounds().
    private void updateForegroundDisplayBounds(WindowContainerTransaction wct) {
        Rect foregroundApplicationDisplayBound = mForegroundApplicationDisplayBounds;
        WindowContainerToken foregroundDisplayToken =
                mForegroundApplicationDisplay.getDisplayAreaInfo().token;

        int foregroundDisplayWidthDp =
                foregroundApplicationDisplayBound.width() * DisplayMetrics.DENSITY_DEFAULT
                        / getDpiDensity();
        int foregroundDisplayHeightDp =
                foregroundApplicationDisplayBound.height() * DisplayMetrics.DENSITY_DEFAULT
                        / getDpiDensity();
        wct.setBounds(foregroundDisplayToken, foregroundApplicationDisplayBound);
        wct.setScreenSizeDp(foregroundDisplayToken, foregroundDisplayWidthDp,
                foregroundDisplayHeightDp);
        wct.setSmallestScreenWidthDp(foregroundDisplayToken,
                Math.min(foregroundDisplayWidthDp, foregroundDisplayHeightDp));
    }

    private void updateBackgroundDisplayBounds(WindowContainerTransaction wct) {
        Rect backgroundApplicationDisplayBound = mBackgroundApplicationDisplayBounds;
        WindowContainerToken backgroundDisplayToken =
                mBackgroundApplicationDisplay.getDisplayAreaInfo().token;

        int backgroundDisplayWidthDp =
                backgroundApplicationDisplayBound.width() * DisplayMetrics.DENSITY_DEFAULT
                        / getDpiDensity();
        int backgroundDisplayHeightDp =
                backgroundApplicationDisplayBound.height() * DisplayMetrics.DENSITY_DEFAULT
                        / getDpiDensity();
        wct.setBounds(backgroundDisplayToken, backgroundApplicationDisplayBound);
        wct.setScreenSizeDp(backgroundDisplayToken, backgroundDisplayWidthDp,
                backgroundDisplayHeightDp);
        wct.setSmallestScreenWidthDp(backgroundDisplayToken,
                Math.min(backgroundDisplayWidthDp, backgroundDisplayHeightDp));
        mTransactionQueue.queue(wct);

        mTransactionQueue.runInSync(t -> {
            // Do not set window crop on backgroundApplicationDisplay. Its windowCrop should remain
            // full screen so that IME doesn't get cropped.
            t.setPosition(mBackgroundApplicationDisplay.getLeash(),
                    backgroundApplicationDisplayBound.left,
                    backgroundApplicationDisplayBound.top);
        });
    }

    void resetWindowsOffset() {
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mDisplayAreaTokenMap.forEach(
                (token, leash) -> {
                    CarDisplayAreaAnimationController.CarDisplayAreaTransitionAnimator animator =
                            mAnimationController.getAnimatorMap().remove(token);
                    if (animator != null && animator.isRunning()) {
                        animator.cancel();
                    }
                    tx.setPosition(leash, /* x= */ 0, /* y= */ 0)
                            .setWindowCrop(leash, /* width= */ -1, /* height= */ -1)
                            .setCornerRadius(leash, /* cornerRadius= */ -1);
                });
        tx.apply();
    }

    /**
     * Offsets the windows by a given offset on Y-axis, triggered also from screen rotation.
     * Directly perform manipulation/offset on the leash.
     */
    void scheduleOffset(int fromPos, int toPos,
            Rect finalBackgroundBounds, Rect finalForegroundBounds,
            DisplayAreaAppearedInfo backgroundApplicationDisplay,
            DisplayAreaAppearedInfo foregroundDisplay,
            DisplayAreaAppearedInfo controlBarDisplay,
            DisplayAreaComponent.FOREGROUND_DA_STATE toState,
            int durationMs) {
        mToState = toState;
        mBackgroundApplicationDisplay = backgroundApplicationDisplay;
        mForegroundApplicationDisplay = foregroundDisplay;
        mControlBarDisplay = controlBarDisplay;
        mDisplayAreaTokenMap.forEach(
                (token, leash) -> {
                    if (token == mBackgroundDisplayToken) {
                        mBackgroundApplicationDisplayBounds.set(finalBackgroundBounds);
                    } else if (token == mForegroundDisplayToken) {
                        mForegroundApplicationDisplayBounds.set(finalForegroundBounds);
                        animateWindows(token, leash, fromPos, toPos, durationMs);
                    }
                });

        if (mToState == DisplayAreaComponent.FOREGROUND_DA_STATE.CONTROL_BAR) {
            WindowContainerTransaction wct = new WindowContainerTransaction();
            updateBackgroundDisplayBounds(wct);
        }
    }

    void animateWindows(WindowContainerToken token, SurfaceControl leash, float fromPos,
            float toPos, int durationMs) {
        CarDisplayAreaAnimationController.CarDisplayAreaTransitionAnimator
                animator =
                mAnimationController.getAnimator(token, leash, fromPos, toPos);


        if (animator != null) {
            if (mDisplayAreaAnimationRunnable != null) {
                mDisplayAreaAnimationRunnable.stopAnimation();
                mHandlerForAnimation.removeCallbacks(mDisplayAreaAnimationRunnable);
            }
            mDisplayAreaAnimationRunnable = new DisplayAreaAnimationRunnable(animator, durationMs);
            mHandlerForAnimation.post(mDisplayAreaAnimationRunnable);
        }
    }

    @Override
    public void onDisplayAreaAppeared(@NonNull DisplayAreaInfo displayAreaInfo,
            @NonNull SurfaceControl leash) {
        if (displayAreaInfo.featureId == BACKGROUND_TASK_CONTAINER) {
            mBackgroundDisplayToken = displayAreaInfo.token;
        } else if (displayAreaInfo.featureId == FOREGROUND_DISPLAY_AREA_ROOT) {
            mForegroundDisplayToken = displayAreaInfo.token;
        }
        mDisplayAreaTokenMap.put(displayAreaInfo.token, leash);
    }

    @Override
    public void onDisplayAreaVanished(@NonNull DisplayAreaInfo displayAreaInfo) {
        if (!mIsRegistered) {
            mDisplayAreaTokenMap.remove(displayAreaInfo.token);
        }
    }

    @Override
    public List<DisplayAreaAppearedInfo> registerOrganizer(int displayAreaFeature) {
        List<DisplayAreaAppearedInfo> displayAreaInfos =
                super.registerOrganizer(displayAreaFeature);
        for (DisplayAreaAppearedInfo info : displayAreaInfos) {
            onDisplayAreaAppeared(info.getDisplayAreaInfo(), info.getLeash());
        }
        mIsRegistered = true;
        return displayAreaInfos;
    }

    @Override
    public void unregisterOrganizer() {
        super.unregisterOrganizer();
        mIsRegistered = false;
    }

    /**
     * A custom runnable with a flag to stop running the code within the {@link #run()} method when
     * the runnable is in the message queue. In such cases calling
     * {@link #removeCallbacksAndMessages(null)} won't work it only stops pending messages
     * (Runnables) not currently running runnable.
     */
    private class DisplayAreaAnimationRunnable implements Runnable {
        private final CarDisplayAreaAnimationController.CarDisplayAreaTransitionAnimator mAnimator;
        private final int mDurationMs;
        private boolean mStopAnimation = false;

        DisplayAreaAnimationRunnable(
                CarDisplayAreaAnimationController.CarDisplayAreaTransitionAnimator animator,
                int durationMs) {
            mAnimator = animator;
            mDurationMs = durationMs;
        }

        @Override
        public void run() {
            if (mStopAnimation) {
                return;
            }

            mAnimator.addDisplayAreaAnimationCallback(mDisplayAreaAnimationCallback)
                    .setDuration(mDurationMs)
                    .start();
        }

        public void stopAnimation() {
            // we don't call animator.cancel() here because if there is only one animation call
            // such as just to open the DA then it will get canceled here.
            mStopAnimation = true;
        }
    }
}
