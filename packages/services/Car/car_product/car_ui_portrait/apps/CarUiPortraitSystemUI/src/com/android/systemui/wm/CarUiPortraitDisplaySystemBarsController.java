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

package com.android.systemui.wm;

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
import static android.view.InsetsState.ITYPE_NAVIGATION_BAR;
import static android.view.InsetsState.ITYPE_STATUS_BAR;

import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Slog;
import android.util.SparseArray;
import android.view.IWindowManager;
import android.view.InsetsVisibilities;
import android.view.WindowInsets;
import android.widget.Toast;

import com.android.car.ui.R;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controller that expands upon {@link DisplaySystemBarsController} but allows for immersive
 * mode overrides and notification in other SystemUI classes via the provided methods and callbacks.
 */
@WMSingleton
public class CarUiPortraitDisplaySystemBarsController extends DisplaySystemBarsController {
    private static final String TAG = "CarUiPortraitDisplaySystemBarsController";
    private SparseArray<CarUiPortraitPerDisplay> mCarUiPerDisplaySparseArray;

    private int mCurrentDrivingState = DRIVING_STATE_UNKNOWN;

    private final CarDrivingStateManager.CarDrivingStateEventListener mDrivingStateEventListener =
            this::handleDrivingStateChange;

    public CarUiPortraitDisplaySystemBarsController(Context context,
            IWindowManager wmService,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            Handler mainHandler) {
        super(context, wmService, displayController, displayInsetsController, mainHandler);

        Car car = Car.createCar(context);
        if (car != null) {
            CarDrivingStateManager mDrivingStateManager =
                    (CarDrivingStateManager) car.getCarManager(Car.CAR_DRIVING_STATE_SERVICE);
            mDrivingStateManager.registerListener(mDrivingStateEventListener);
            mDrivingStateEventListener.onDrivingStateChanged(
                    mDrivingStateManager.getCurrentCarDrivingState());
        } else {
            Slog.e(TAG, "Failed to initialize car");
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        CarUiPortraitPerDisplay pd = new CarUiPortraitPerDisplay(displayId);
        pd.register();
        if (mCarUiPerDisplaySparseArray == null) {
            mCarUiPerDisplaySparseArray = new SparseArray<>();
            BarControlPolicy.reloadFromSetting(mContext);
            BarControlPolicy.registerContentObserver(mContext, mHandler, () -> {
                int size = mCarUiPerDisplaySparseArray.size();
                for (int i = 0; i < size; i++) {
                    mCarUiPerDisplaySparseArray.valueAt(i)
                            .updateDisplayWindowRequestedVisibilities();
                }
            });
        }
        mCarUiPerDisplaySparseArray.put(displayId, pd);
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        CarUiPortraitPerDisplay pd = mCarUiPerDisplaySparseArray.get(displayId);
        pd.unregister();
        mCarUiPerDisplaySparseArray.remove(displayId);
    }

    /**
     * Request an immersive mode override for a particular display id. This request will override
     * the usual BarControlPolicy until the package or requested visibilites change.
     */
    public void requestImmersiveMode(int displayId, boolean immersive) {
        CarUiPortraitPerDisplay display = mCarUiPerDisplaySparseArray.get(displayId);
        if (display == null) {
            return;
        }
        display.setImmersiveMode(immersive);
    }

    /**
     * Request an immersive mode override for a particular display id specifically for setup wizard.
     * This request will override the usual BarControlPolicy and will persist until explicitly
     * revoked.
     */
    public void requestImmersiveModeForSUW(int displayId, boolean immersive) {
        CarUiPortraitPerDisplay display = mCarUiPerDisplaySparseArray.get(displayId);
        if (display == null) {
            return;
        }
        display.setImmersiveModeForSUW(immersive);
    }

    /**
     * Register an immersive mode callback for a particular display.
     */
    public void registerCallback(int displayId, Callback callback) {
        CarUiPortraitPerDisplay display = mCarUiPerDisplaySparseArray.get(displayId);
        if (display == null) {
            return;
        }
        display.addCallbackForDisplay(callback);
    }

    /**
     * Unregister an immersive mode callback for a particular display.
     */
    public void unregisterCallback(int displayId, Callback callback) {
        CarUiPortraitPerDisplay display = mCarUiPerDisplaySparseArray.get(displayId);
        if (display == null) {
            return;
        }
        display.removeCallbackForDisplay(callback);
    }

    private void handleDrivingStateChange(CarDrivingStateEvent event) {
        mCurrentDrivingState = event.eventValue;
        if (mCarUiPerDisplaySparseArray != null) {
            for (int i = 0; i < mCarUiPerDisplaySparseArray.size(); i++) {
                mCarUiPerDisplaySparseArray.valueAt(i).onDrivingStateChanged();
            }
        }
    }

    class CarUiPortraitPerDisplay extends DisplaySystemBarsController.PerDisplay {
        private final int[] mImmersiveVisibilities = new int[] {0, WindowInsets.Type.systemBars()};
        private final List<Callback> mCallbacks = new ArrayList<>();
        private InsetsVisibilities mWindowRequestedVisibilities;
        private InsetsVisibilities mAppliedVisibilities = new InsetsVisibilities();
        private boolean mImmersiveOverride = false;
        private boolean mImmersiveForSUW = false;

        CarUiPortraitPerDisplay(int displayId) {
            super(displayId);
        }

        @Override
        public void topFocusedWindowChanged(ComponentName component,
                InsetsVisibilities requestedVisibilities) {
            boolean requestedVisibilitiesChanged = false;
            if (requestedVisibilities != null) {
                if (!requestedVisibilities.equals(mWindowRequestedVisibilities)) {
                    mWindowRequestedVisibilities = requestedVisibilities;
                    boolean immersive = !mWindowRequestedVisibilities.getVisibility(
                            ITYPE_STATUS_BAR) && !mWindowRequestedVisibilities.getVisibility(
                            ITYPE_NAVIGATION_BAR);
                    notifyOnImmersiveRequestedChanged(component, immersive);
                    if (!immersive) {
                        mImmersiveOverride = false;
                        requestedVisibilitiesChanged = true;
                    }
                }
            } else if (mWindowRequestedVisibilities != null) {
                mWindowRequestedVisibilities = null;
                notifyOnImmersiveRequestedChanged(component, false);
                requestedVisibilitiesChanged = true;
            }
            String packageName = component != null ? component.getPackageName() : null;
            if (Objects.equals(mPackageName, packageName) && !requestedVisibilitiesChanged) {
                return;
            }
            mPackageName = packageName;
            mImmersiveOverride = false; // reset override when changing application
            updateDisplayWindowRequestedVisibilities();
        }

        @Override
        protected void updateDisplayWindowRequestedVisibilities() {
            if (mPackageName == null && !mImmersiveOverride && !mImmersiveForSUW) {
                return;
            }
            int[] barVisibilities = mImmersiveOverride || mImmersiveForSUW
                    ? mImmersiveVisibilities
                    : BarControlPolicy.getBarVisibilities(mPackageName);
            updateRequestedVisibilities(barVisibilities[0], /* visible= */ true);
            updateRequestedVisibilities(barVisibilities[1], /* visible= */ false);

            // Return if the requested visibility is already applied.
            if (mAppliedVisibilities.equals(mRequestedVisibilities)) {
                return;
            }
            mAppliedVisibilities.set(mRequestedVisibilities);

            showInsets(barVisibilities[0], /* fromIme= */ false);
            hideInsets(barVisibilities[1], /* fromIme= */ false);

            boolean immersiveState = mImmersiveOverride || mImmersiveForSUW || (
                    (barVisibilities[1] & (WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars())) == (
                            WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()));
            notifyOnImmersiveStateChanged(immersiveState);

            try {
                mWmService.updateDisplayWindowRequestedVisibilities(mDisplayId,
                        mRequestedVisibilities);
            } catch (RemoteException e) {
                Slog.w(TAG, "Unable to update window manager service.");
            }
        }

        void setImmersiveMode(boolean immersive) {
            if (mImmersiveOverride == immersive) {
                return;
            }
            if (immersive && mCurrentDrivingState == DRIVING_STATE_MOVING) {
                Toast.makeText(mContext,
                        R.string.car_ui_restricted_while_driving, Toast.LENGTH_LONG).show();
                return;
            }
            mImmersiveOverride = immersive;
            updateDisplayWindowRequestedVisibilities();
        }

        void setImmersiveModeForSUW(boolean immersive) {
            if (mImmersiveForSUW == immersive) {
                return;
            }
            mImmersiveForSUW = immersive;
            updateDisplayWindowRequestedVisibilities();
        }

        void addCallbackForDisplay(Callback callback) {
            if (mCallbacks.contains(callback)) return;
            mCallbacks.add(callback);
        }

        void removeCallbackForDisplay(Callback callback) {
            mCallbacks.remove(callback);
        }

        void notifyOnImmersiveStateChanged(boolean immersive) {
            for (Callback callback : mCallbacks) {
                callback.onImmersiveStateChanged(immersive);
            }
        }

        void notifyOnImmersiveRequestedChanged(ComponentName component, boolean requested) {
            for (Callback callback : mCallbacks) {
                callback.onImmersiveRequestedChanged(component, requested);
            }
        }

        void onDrivingStateChanged() {
            if (mImmersiveOverride && mCurrentDrivingState == DRIVING_STATE_MOVING) {
                mImmersiveOverride = false;
                updateDisplayWindowRequestedVisibilities();
            }
        }
    }

    /**
     * Callback for notifying changes to the immersive and immersive request states.
     */
    public interface Callback {
        /**
         * Callback triggered when the current package's requested visibilities change has caused
         * an immersive request change.
         */
        void onImmersiveRequestedChanged(ComponentName component, boolean requested);

        /**
         * Callback triggered when the immersive override state changes.
         */
        void onImmersiveStateChanged(boolean immersive);
    }
}
