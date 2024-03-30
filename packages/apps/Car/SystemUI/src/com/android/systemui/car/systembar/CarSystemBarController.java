/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import android.annotation.LayoutRes;
import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.os.Build;
import android.util.ArraySet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import com.android.car.ui.FocusParkingView;
import com.android.car.ui.utils.ViewUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.car.privacy.CameraPrivacyElementsProviderImpl;
import com.android.systemui.car.privacy.CameraQcPanel;
import com.android.systemui.car.privacy.MicPrivacyElementsProviderImpl;
import com.android.systemui.car.privacy.MicQcPanel;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.statusicon.StatusIconPanelController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import dagger.Lazy;

/** A single class which controls the navigation bar views. */
@SysUISingleton
public class CarSystemBarController {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;

    private static final String TAG = CarSystemBarController.class.getSimpleName();

    private final Context mContext;
    private final CarSystemBarViewFactory mCarSystemBarViewFactory;
    private final CarServiceProvider mCarServiceProvider;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final ConfigurationController mConfigurationController;
    private final ButtonSelectionStateController mButtonSelectionStateController;
    private final ButtonRoleHolderController mButtonRoleHolderController;
    private final Lazy<UserNameViewController> mUserNameViewControllerLazy;
    private final Lazy<MicPrivacyChipViewController> mMicPrivacyChipViewControllerLazy;
    private final Lazy<CameraPrivacyChipViewController> mCameraPrivacyChipViewControllerLazy;
    private final Lazy<MicPrivacyElementsProviderImpl> mMicPrivacyElementsProviderLazy;
    private final Lazy<CameraPrivacyElementsProviderImpl> mCameraPrivacyElementsProviderLazy;

    private final boolean mShowTop;
    private final boolean mShowBottom;
    private final boolean mShowLeft;
    private final boolean mShowRight;
    private final int mPrivacyChipXOffset;

    @IdRes
    private int mTopFocusedViewId;
    @IdRes
    private int mBottomFocusedViewId;
    @IdRes
    private int mLeftFocusedViewId;
    @IdRes
    private int mRightFocusedViewId;

    private final Set<View.OnTouchListener> mTopBarTouchListeners = new ArraySet<>();
    private final Set<View.OnTouchListener> mBottomBarTouchListeners = new ArraySet<>();
    private final Set<View.OnTouchListener> mLeftBarTouchListeners = new ArraySet<>();
    private final Set<View.OnTouchListener> mRightBarTouchListeners = new ArraySet<>();

    private NotificationsShadeController mNotificationsShadeController;
    private HvacPanelController mHvacPanelController;
    private StatusIconPanelController mMicPanelController;
    private StatusIconPanelController mCameraPanelController;
    private StatusIconPanelController mProfilePanelController;
    private HvacPanelOverlayViewController mHvacPanelOverlayViewController;

    private CarSystemBarView mTopView;
    private CarSystemBarView mBottomView;
    private CarSystemBarView mLeftView;
    private CarSystemBarView mRightView;

    // Saved StatusBarManager.DisableFlags
    private int mStatusBarState;
    // Saved StatusBarManager.Disable2Flags
    private int mStatusBarState2;
    private int mLockTaskMode;

    @Inject
    public CarSystemBarController(Context context,
            CarSystemBarViewFactory carSystemBarViewFactory,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<UserNameViewController> userNameViewControllerLazy,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Lazy<MicPrivacyElementsProviderImpl> micPrivacyElementsProvider,
            Lazy<CameraPrivacyElementsProviderImpl> cameraPrivacyElementsProvider) {
        mContext = context;
        mCarSystemBarViewFactory = carSystemBarViewFactory;
        mCarServiceProvider = carServiceProvider;
        mBroadcastDispatcher = broadcastDispatcher;
        mConfigurationController = configurationController;
        mButtonSelectionStateController = buttonSelectionStateController;
        mUserNameViewControllerLazy = userNameViewControllerLazy;
        mMicPrivacyChipViewControllerLazy = micPrivacyChipViewControllerLazy;
        mCameraPrivacyChipViewControllerLazy = cameraPrivacyChipViewControllerLazy;
        mButtonRoleHolderController = buttonRoleHolderController;
        mMicPrivacyElementsProviderLazy = micPrivacyElementsProvider;
        mCameraPrivacyElementsProviderLazy = cameraPrivacyElementsProvider;

        // Read configuration.
        mShowTop = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.TOP);
        mShowBottom = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.BOTTOM);
        mShowLeft = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.LEFT);
        mShowRight = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.RIGHT);

        mPrivacyChipXOffset = -context.getResources()
                .getDimensionPixelOffset(R.dimen.privacy_chip_horizontal_padding);
    }

    /**
     * Hides all system bars.
     */
    public void hideBars() {
        setTopWindowVisibility(View.GONE);
        setBottomWindowVisibility(View.GONE);
        setLeftWindowVisibility(View.GONE);
        setRightWindowVisibility(View.GONE);
    }

    /**
     * Shows all system bars.
     */
    public void showBars() {
        setTopWindowVisibility(View.VISIBLE);
        setBottomWindowVisibility(View.VISIBLE);
        setLeftWindowVisibility(View.VISIBLE);
        setRightWindowVisibility(View.VISIBLE);
    }

    /** Clean up */
    public void removeAll() {
        mButtonSelectionStateController.removeAll();
        mButtonRoleHolderController.removeAll();
        mUserNameViewControllerLazy.get().removeAll();
        mMicPrivacyChipViewControllerLazy.get().removeAll();
        mCameraPrivacyChipViewControllerLazy.get().removeAll();

        if (mMicPanelController != null) {
            mMicPanelController.destroyPanel();
        }
        if (mCameraPanelController != null) {
            mCameraPanelController.destroyPanel();
        }
        if (mProfilePanelController != null) {
            mProfilePanelController.destroyPanel();
        }
        mMicPanelController = null;
        mCameraPanelController = null;
        mProfilePanelController = null;
    }

    /** Gets the top window if configured to do so. */
    @Nullable
    public ViewGroup getTopWindow() {
        return mShowTop ? mCarSystemBarViewFactory.getTopWindow() : null;
    }

    /** Gets the bottom window if configured to do so. */
    @Nullable
    public ViewGroup getBottomWindow() {
        return mShowBottom ? mCarSystemBarViewFactory.getBottomWindow() : null;
    }

    /** Gets the left window if configured to do so. */
    @Nullable
    public ViewGroup getLeftWindow() {
        return mShowLeft ? mCarSystemBarViewFactory.getLeftWindow() : null;
    }

    /** Gets the right window if configured to do so. */
    @Nullable
    public ViewGroup getRightWindow() {
        return mShowRight ? mCarSystemBarViewFactory.getRightWindow() : null;
    }

    /** Toggles the top nav bar visibility. */
    public boolean setTopWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getTopWindow(), visibility);
    }

    /** Toggles the bottom nav bar visibility. */
    public boolean setBottomWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getBottomWindow(), visibility);
    }

    /** Toggles the left nav bar visibility. */
    public boolean setLeftWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getLeftWindow(), visibility);
    }

    /** Toggles the right nav bar visibility. */
    public boolean setRightWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getRightWindow(), visibility);
    }

    private boolean setWindowVisibility(ViewGroup window, @View.Visibility int visibility) {
        if (window == null) {
            return false;
        }

        if (window.getVisibility() == visibility) {
            return false;
        }

        window.setVisibility(visibility);
        return true;
    }

    /**
     * Sets the system bar states - {@code StatusBarManager.DisableFlags},
     * {@code StatusBarManager.Disable2Flags}, lock task mode. When there is a change in state,
     * and refreshes the system bars.
     *
     * @param state {@code StatusBarManager.DisableFlags}
     * @param state2 {@code StatusBarManager.Disable2Flags}
     */
    public void setSystemBarStates(int state, int state2) {
        int diff = (state ^ mStatusBarState) | (state2 ^ mStatusBarState2);
        int lockTaskMode = getLockTaskModeState();
        if (diff == 0 && mLockTaskMode == lockTaskMode) {
            if (DEBUG) {
                Log.d(TAG, "setSystemBarStates(): status bar states unchanged: state: "
                        + state + " state2: " +  state2 + " lockTaskMode: " + mLockTaskMode);
            }
            return;
        }
        mStatusBarState = state;
        mStatusBarState2 = state2;
        mLockTaskMode = lockTaskMode;
        refreshSystemBar();
    }

    @VisibleForTesting
    protected int getStatusBarState() {
        return mStatusBarState;
    }

    @VisibleForTesting
    protected int getStatusBarState2() {
        return mStatusBarState2;
    }

    @VisibleForTesting
    protected int getLockTaskMode() {
        return mLockTaskMode;
    }

    /**
     * Refreshes system bar views and sets the visibility of certain components based on
     * {@link StatusBarManager} flags and lock task mode.
     * <ul>
     * <li>Home button will be disabled when {@code StatusBarManager.DISABLE_HOME} is set.
     * <li>Phone call button will be disable in lock task mode.
     * <li>App grid button will be disable when {@code StatusBarManager.DISABLE_HOME} is set.
     * <li>Notification button will be disable when
     * {@code StatusBarManager.DISABLE_NOTIFICATION_ICONS} is set.
     * <li>Quick settings and user switcher will be hidden when in lock task mode or when
     * {@code StatusBarManager.DISABLE2_QUICK_SETTINGS} is set.
     * </ul>
     */
    public void refreshSystemBar() {
        boolean homeDisabled = ((mStatusBarState & StatusBarManager.DISABLE_HOME) > 0);
        boolean notificationDisabled =
                ((mStatusBarState & StatusBarManager.DISABLE_NOTIFICATION_ICONS) > 0);
        boolean locked = (mLockTaskMode == ActivityManager.LOCK_TASK_MODE_LOCKED);
        boolean qcDisabled =
                ((mStatusBarState2 & StatusBarManager.DISABLE2_QUICK_SETTINGS) > 0) || locked;
        boolean systemIconsDisabled =
                ((mStatusBarState2 & StatusBarManager.DISABLE2_SYSTEM_ICONS) > 0) || locked;

        setDisabledSystemBarButton(R.id.home, homeDisabled, "home");
        setDisabledSystemBarButton(R.id.phone_nav, locked, "phone_nav");
        setDisabledSystemBarButton(R.id.grid_nav, homeDisabled, "grid_nav");
        setDisabledSystemBarButton(R.id.notifications, notificationDisabled, "notifications");

        setDisabledSystemBarContainer(R.id.qc_entry_points_container, qcDisabled,
                "qc_entry_points_container");
        setDisabledSystemBarContainer(R.id.user_name_container, qcDisabled,
                "user_name_container");
        setDisabledSystemBarContainer(R.id.read_only_icons_container, systemIconsDisabled,
                "read_only_icons_container");

        if (DEBUG) {
            Log.d(TAG, "refreshSystemBar: locked?: " + locked
                    + " homeDisabled: " + homeDisabled
                    + " notificationDisabled: " + notificationDisabled
                    + " qcDisabled: " + qcDisabled
                    + " systemIconsDisabled: " + systemIconsDisabled);
        }
    }

    private int getLockTaskModeState() {
        return mContext.getSystemService(ActivityManager.class).getLockTaskModeState();
    }

    private void setDisabledSystemBarButton(int viewId, boolean disabled,
                @Nullable String buttonName) {
        for (CarSystemBarView barView : getAllAvailableSystemBarViews()) {
            barView.setDisabledSystemBarButton(viewId, disabled,
                    () -> showAdminSupportDetailsDialog(), buttonName);
        }
    }

    private void setDisabledSystemBarContainer(int viewId, boolean disabled,
                @Nullable String viewName) {
        for (CarSystemBarView barView : getAllAvailableSystemBarViews()) {
            barView.setVisibilityByViewId(viewId, viewName,
                    disabled ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void showAdminSupportDetailsDialog() {
        // TODO(b/205891123): launch AdminSupportDetailsDialog after moving
        // AdminSupportDetailsDialog out of CarSettings since CarSettings is not and should not
        // be allowlisted for lock task mode.
        Toast.makeText(mContext, "This action is unavailable for your profile",
                Toast.LENGTH_LONG).show();
    }

    /** Gets the top navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getTopBar(boolean isSetUp) {
        if (!mShowTop) {
            return null;
        }

        mTopView = mCarSystemBarViewFactory.getTopBar(isSetUp);
        setupBar(mTopView, mTopBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);

        if (isSetUp) {
            // We do not want the privacy chips or the profile picker to be clickable in
            // unprovisioned mode.
            mMicPanelController = setupSensorQcPanel(mMicPanelController, R.id.mic_privacy_chip,
                    R.layout.qc_mic_panel);
            mCameraPanelController = setupSensorQcPanel(mCameraPanelController,
                    R.id.camera_privacy_chip, R.layout.qc_camera_panel);
            setupProfilePanel();
        }

        return mTopView;
    }

    /** Gets the bottom navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getBottomBar(boolean isSetUp) {
        if (!mShowBottom) {
            return null;
        }

        mBottomView = mCarSystemBarViewFactory.getBottomBar(isSetUp);
        setupBar(mBottomView, mBottomBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);
        return mBottomView;
    }

    /** Gets the left navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getLeftBar(boolean isSetUp) {
        if (!mShowLeft) {
            return null;
        }

        mLeftView = mCarSystemBarViewFactory.getLeftBar(isSetUp);
        setupBar(mLeftView, mLeftBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);
        return mLeftView;
    }

    /** Gets the right navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getRightBar(boolean isSetUp) {
        if (!mShowRight) {
            return null;
        }

        mRightView = mCarSystemBarViewFactory.getRightBar(isSetUp);
        setupBar(mRightView, mRightBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);
        return mRightView;
    }

    private void setupBar(CarSystemBarView view, Set<View.OnTouchListener> statusBarTouchListeners,
            NotificationsShadeController notifShadeController,
            HvacPanelController hvacPanelController,
            HvacPanelOverlayViewController hvacPanelOverlayViewController) {
        view.setStatusBarWindowTouchListeners(statusBarTouchListeners);
        view.setNotificationsPanelController(notifShadeController);
        view.setHvacPanelController(hvacPanelController);
        view.registerHvacPanelOverlayViewController(hvacPanelOverlayViewController);
        mButtonSelectionStateController.addAllButtonsWithSelectionState(view);
        mButtonRoleHolderController.addAllButtonsWithRoleName(view);
        mUserNameViewControllerLazy.get().addUserNameView(view);
        mMicPrivacyChipViewControllerLazy.get().addPrivacyChipView(view);
        mCameraPrivacyChipViewControllerLazy.get().addPrivacyChipView(view);
    }

    private StatusIconPanelController setupSensorQcPanel(
            @Nullable StatusIconPanelController panelController, int chipId,
            @LayoutRes int panelLayoutRes) {
        if (panelController == null) {
            panelController = new StatusIconPanelController(mContext, mCarServiceProvider,
                    mBroadcastDispatcher, mConfigurationController);
        }

        panelController.setOnQcViewsFoundListener(qcViews -> qcViews.forEach(qcView -> {
            if (qcView.getLocalQCProvider() instanceof MicQcPanel) {
                MicQcPanel micQcPanel = (MicQcPanel) qcView.getLocalQCProvider();
                micQcPanel.setControllers(mMicPrivacyChipViewControllerLazy.get(),
                        mMicPrivacyElementsProviderLazy.get());
            } else if (qcView.getLocalQCProvider() instanceof CameraQcPanel) {
                CameraQcPanel cameraQcPanel = (CameraQcPanel) qcView.getLocalQCProvider();
                cameraQcPanel.setControllers(mCameraPrivacyChipViewControllerLazy.get(),
                        mCameraPrivacyElementsProviderLazy.get());
            }
        }));

        panelController.attachPanel(mTopView.requireViewById(chipId), panelLayoutRes,
                R.dimen.car_sensor_qc_panel_width, mPrivacyChipXOffset,
                panelController.getDefaultYOffset(), Gravity.TOP | Gravity.END);

        return panelController;
    }

    private void setupProfilePanel() {
        View profilePickerView = mTopView.findViewById(R.id.user_name);
        if (mProfilePanelController == null && profilePickerView != null) {
            boolean profilePanelDisabledWhileDriving = mContext.getResources().getBoolean(
                    R.bool.config_profile_panel_disabled_while_driving);
            mProfilePanelController = new StatusIconPanelController(mContext, mCarServiceProvider,
                    mBroadcastDispatcher, mConfigurationController,
                    profilePanelDisabledWhileDriving);
            mProfilePanelController.attachPanel(profilePickerView, R.layout.qc_profile_switcher,
                    R.dimen.car_profile_quick_controls_panel_width, Gravity.TOP | Gravity.END);
        }
    }

    /** Sets a touch listener for the top navigation bar. */
    public void registerTopBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mTopBarTouchListeners.add(listener);
        if (setModified && mTopView != null) {
            mTopView.setStatusBarWindowTouchListeners(mTopBarTouchListeners);
        }
    }

    /** Sets a touch listener for the bottom navigation bar. */
    public void registerBottomBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mBottomBarTouchListeners.add(listener);
        if (setModified && mBottomView != null) {
            mBottomView.setStatusBarWindowTouchListeners(mBottomBarTouchListeners);
        }
    }

    /** Sets a touch listener for the left navigation bar. */
    public void registerLeftBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mLeftBarTouchListeners.add(listener);
        if (setModified && mLeftView != null) {
            mLeftView.setStatusBarWindowTouchListeners(mLeftBarTouchListeners);
        }
    }

    /** Sets a touch listener for the right navigation bar. */
    public void registerRightBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mRightBarTouchListeners.add(listener);
        if (setModified && mRightView != null) {
            mRightView.setStatusBarWindowTouchListeners(mRightBarTouchListeners);
        }
    }

    /** Sets a notification controller which toggles the notification panel. */
    public void registerNotificationController(
            NotificationsShadeController notificationsShadeController) {
        mNotificationsShadeController = notificationsShadeController;
        if (mTopView != null) {
            mTopView.setNotificationsPanelController(mNotificationsShadeController);
        }
        if (mBottomView != null) {
            mBottomView.setNotificationsPanelController(mNotificationsShadeController);
        }
        if (mLeftView != null) {
            mLeftView.setNotificationsPanelController(mNotificationsShadeController);
        }
        if (mRightView != null) {
            mRightView.setNotificationsPanelController(mNotificationsShadeController);
        }
    }

    /** Sets an HVAC controller which toggles the HVAC panel. */
    public void registerHvacPanelController(HvacPanelController hvacPanelController) {
        mHvacPanelController = hvacPanelController;
        if (mTopView != null) {
            mTopView.setHvacPanelController(mHvacPanelController);
        }
        if (mBottomView != null) {
            mBottomView.setHvacPanelController(mHvacPanelController);
        }
        if (mLeftView != null) {
            mLeftView.setHvacPanelController(mHvacPanelController);
        }
        if (mRightView != null) {
            mRightView.setHvacPanelController(mHvacPanelController);
        }
    }

    /** Sets the HVACPanelOverlayViewController for views to listen to the panel's state. */
    public void registerHvacPanelOverlayViewController(
            HvacPanelOverlayViewController hvacPanelOverlayViewController) {
        mHvacPanelOverlayViewController = hvacPanelOverlayViewController;
        if (mTopView != null) {
            mTopView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
        if (mBottomView != null) {
            mBottomView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
        if (mLeftView != null) {
            mLeftView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
        if (mRightView != null) {
            mRightView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
    }

    /**
     * Shows all of the navigation buttons on the valid instances of {@link CarSystemBarView}.
     */
    public void showAllNavigationButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
        if (mBottomView != null) {
            mBottomView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
        if (mLeftView != null) {
            mLeftView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
        if (mRightView != null) {
            mRightView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
    }

    /**
     * Shows all of the keyguard specific buttons on the valid instances of
     * {@link CarSystemBarView}.
     */
    public void showAllKeyguardButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
        if (mBottomView != null) {
            mBottomView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
        if (mLeftView != null) {
            mLeftView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
        if (mRightView != null) {
            mRightView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
    }

    /**
     * Shows all of the occlusion state buttons on the valid instances of
     * {@link CarSystemBarView}.
     */
    public void showAllOcclusionButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
        if (mBottomView != null) {
            mBottomView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
        if (mLeftView != null) {
            mLeftView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
        if (mRightView != null) {
            mRightView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
    }

    /** Toggles whether the notifications icon has an unseen indicator or not. */
    public void toggleAllNotificationsUnseenIndicator(boolean isSetUp, boolean hasUnseen) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.toggleNotificationUnseenIndicator(hasUnseen);
        }
        if (mBottomView != null) {
            mBottomView.toggleNotificationUnseenIndicator(hasUnseen);
        }
        if (mLeftView != null) {
            mLeftView.toggleNotificationUnseenIndicator(hasUnseen);
        }
        if (mRightView != null) {
            mRightView.toggleNotificationUnseenIndicator(hasUnseen);
        }
    }

    /** Interface for controlling the notifications shade. */
    public interface NotificationsShadeController {
        /** Toggles the visibility of the notifications shade. */
        void togglePanel();

        /** Returns {@code true} if the panel is open. */
        boolean isNotificationPanelOpen();
    }

    /** Interface for controlling the HVAC panel. */
    public interface HvacPanelController {
        /** Toggles the visibility of the HVAC shade. */
        void togglePanel();

        /** Returns {@code true} if the panel is open. */
        boolean isHvacPanelOpen();
    }

    private void checkAllBars(boolean isSetUp) {
        mTopView = getTopBar(isSetUp);
        mBottomView = getBottomBar(isSetUp);
        mLeftView = getLeftBar(isSetUp);
        mRightView = getRightBar(isSetUp);
    }

    private List<CarSystemBarView> getAllAvailableSystemBarViews() {
        List<CarSystemBarView> barViews = new ArrayList<>();
        if (mTopView != null) {
            barViews.add(mTopView);
        }
        if (mBottomView != null) {
            barViews.add(mBottomView);
        }
        if (mLeftView != null) {
            barViews.add(mLeftView);
        }
        if (mRightView != null) {
            barViews.add(mRightView);
        }
        return barViews;
    }

    /** Gets the selected Quick Controls class name. */
    protected String getSelectedQuickControlsClassName() {
        return mCarSystemBarViewFactory.getSelectedQuickControlsClassName();
    }

    /** Calls onClick for the given Quick Controls class name. */
    protected void callQuickControlsOnClickFromClassName(String clsName) {
        mCarSystemBarViewFactory.callQuickControlsOnClickFromClassName(clsName);
    }

    /** Resets the cached Views. */
    protected void resetCache() {
        mCarSystemBarViewFactory.resetCache();
    }

    /** Stores the ID of the View that is currently focused and hides the focus. */
    protected void cacheAndHideFocus() {
        mTopFocusedViewId = cacheAndHideFocus(mTopView);
        if (mTopFocusedViewId != View.NO_ID) return;
        mBottomFocusedViewId = cacheAndHideFocus(mBottomView);
        if (mBottomFocusedViewId != View.NO_ID) return;
        mLeftFocusedViewId = cacheAndHideFocus(mLeftView);
        if (mLeftFocusedViewId != View.NO_ID) return;
        mRightFocusedViewId = cacheAndHideFocus(mRightView);
    }

    @VisibleForTesting
    int cacheAndHideFocus(@Nullable View rootView) {
        if (rootView == null) return View.NO_ID;
        View focusedView = rootView.findFocus();
        if (focusedView == null || focusedView instanceof FocusParkingView) return View.NO_ID;
        int focusedViewId = focusedView.getId();
        ViewUtils.hideFocus(rootView);
        return focusedViewId;
    }

    /** Requests focus on the View that matches the cached ID. */
    protected void restoreFocus() {
        if (restoreFocus(mTopView, mTopFocusedViewId)) return;
        if (restoreFocus(mBottomView, mBottomFocusedViewId)) return;
        if (restoreFocus(mLeftView, mLeftFocusedViewId)) return;
        restoreFocus(mRightView, mRightFocusedViewId);
    }

    private boolean restoreFocus(@Nullable View rootView, @IdRes int viewToFocusId) {
        if (rootView == null || viewToFocusId == View.NO_ID) return false;
        View focusedView = rootView.findViewById(viewToFocusId);
        if (focusedView == null) return false;
        focusedView.requestFocus();
        return true;
    }
}
