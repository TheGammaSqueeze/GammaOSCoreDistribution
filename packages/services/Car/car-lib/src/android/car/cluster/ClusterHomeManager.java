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

package android.car.cluster;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.annotation.AddedInOrBefore;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/** @hide */
public class ClusterHomeManager extends CarManagerBase {
    private static final String TAG = ClusterHomeManager.class.getSimpleName();
    /**
     * When the client reports ClusterHome state and if there is no UI in the sub area, it can
     * reports UI_TYPE_CLUSTER_NONE instead.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int UI_TYPE_CLUSTER_NONE = -1;
    @AddedInOrBefore(majorVersion = 33)
    public static final int UI_TYPE_CLUSTER_HOME = 0;

    /** @hide */
    @IntDef(flag = true, prefix = { "CONFIG_" }, value = {
            CONFIG_DISPLAY_ON_OFF,
            CONFIG_DISPLAY_BOUNDS,
            CONFIG_DISPLAY_INSETS,
            CONFIG_UI_TYPE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Config {}

    /** Bit fields indicates which fields of {@link ClusterState} are changed */
    @AddedInOrBefore(majorVersion = 33)
    public static final int CONFIG_DISPLAY_ON_OFF = 0x01;
    @AddedInOrBefore(majorVersion = 33)
    public static final int CONFIG_DISPLAY_BOUNDS = 0x02;
    @AddedInOrBefore(majorVersion = 33)
    public static final int CONFIG_DISPLAY_INSETS = 0x04;
    @AddedInOrBefore(majorVersion = 33)
    public static final int CONFIG_UI_TYPE = 0x08;
    @AddedInOrBefore(majorVersion = 33)
    public static final int CONFIG_DISPLAY_ID = 0x10;

    /**
     * Callback for ClusterHome to get notifications when cluster state changes.
     */
    public interface ClusterStateListener {
        /**
         * Called when ClusterOS changes the cluster display state, the geometry of cluster display,
         * or the uiType.
         * @param state newly updated {@link ClusterState}
         * @param changes the flag indicates which fields are updated
         */
        @AddedInOrBefore(majorVersion = 33)
        void onClusterStateChanged(ClusterState state, @Config int changes);
    }

    /**
     * Callback for ClusterHome to get notifications when cluster navigation state changes.
     */
    public interface ClusterNavigationStateListener {
        /** Called when the App who owns the navigation focus casts the new navigation state. */
        @AddedInOrBefore(majorVersion = 33)
        void onNavigationState(byte[] navigationState);
    }

    private static class ClusterStateListenerRecord {
        final Executor mExecutor;
        final ClusterStateListener mListener;
        ClusterStateListenerRecord(Executor executor, ClusterStateListener listener) {
            mExecutor = executor;
            mListener = listener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ClusterStateListenerRecord)) {
                return false;
            }
            return mListener == ((ClusterStateListenerRecord) obj).mListener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public int hashCode() {
            return mListener.hashCode();
        }
    }

    private static class ClusterNavigationStateListenerRecord {
        final Executor mExecutor;
        final ClusterNavigationStateListener mListener;

        ClusterNavigationStateListenerRecord(Executor executor,
                ClusterNavigationStateListener listener) {
            mExecutor = executor;
            mListener = listener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ClusterNavigationStateListenerRecord)) {
                return false;
            }
            return mListener == ((ClusterNavigationStateListenerRecord) obj).mListener;
        }

        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        @Override
        public int hashCode() {
            return mListener.hashCode();
        }
    }

    private final IClusterHomeService mService;
    private final IClusterStateListenerImpl mClusterStateListenerBinderCallback;
    private final IClusterNavigationStateListenerImpl mClusterNavigationStateListenerBinderCallback;
    private final CopyOnWriteArrayList<ClusterStateListenerRecord> mStateListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ClusterNavigationStateListenerRecord>
            mNavigationStateListeners = new CopyOnWriteArrayList<>();

    /** @hide */
    @VisibleForTesting
    public ClusterHomeManager(Car car, IBinder service) {
        super(car);
        mService = IClusterHomeService.Stub.asInterface(service);
        mClusterStateListenerBinderCallback = new IClusterStateListenerImpl(this);
        mClusterNavigationStateListenerBinderCallback =
                new IClusterNavigationStateListenerImpl(this);
    }

    /**
     * Registers the callback for ClusterHome.
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @AddedInOrBefore(majorVersion = 33)
    public void registerClusterStateListener(
            @NonNull Executor executor, @NonNull ClusterStateListener callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        ClusterStateListenerRecord clusterStateListenerRecord =
                new ClusterStateListenerRecord(executor, callback);
        if (!mStateListeners.addIfAbsent(clusterStateListenerRecord)) {
            return;
        }
        if (mStateListeners.size() == 1) {
            try {
                mService.registerClusterStateListener(mClusterStateListenerBinderCallback);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Registers the callback for ClusterHome.
     */
    @RequiresPermission(Car.PERMISSION_CAR_MONITOR_CLUSTER_NAVIGATION_STATE)
    @AddedInOrBefore(majorVersion = 33)
    public void registerClusterNavigationStateListener(
            @NonNull Executor executor, @NonNull ClusterNavigationStateListener callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        ClusterNavigationStateListenerRecord clusterStateListenerRecord =
                new ClusterNavigationStateListenerRecord(executor, callback);
        if (!mNavigationStateListeners.addIfAbsent(clusterStateListenerRecord)) {
            return;
        }
        if (mNavigationStateListeners.size() == 1) {
            try {
                mService.registerClusterNavigationStateListener(
                        mClusterNavigationStateListenerBinderCallback);
            } catch (RemoteException e) {
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Unregisters the callback.
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @AddedInOrBefore(majorVersion = 33)
    public void unregisterClusterStateListener(@NonNull ClusterStateListener callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        if (!mStateListeners
                .remove(new ClusterStateListenerRecord(/* executor= */ null, callback))) {
            return;
        }
        if (mStateListeners.isEmpty()) {
            try {
                mService.unregisterClusterStateListener(mClusterStateListenerBinderCallback);
            } catch (RemoteException ignored) {
                // ignore for unregistering
            }
        }
    }

    /**
     * Unregisters the callback.
     */
    @RequiresPermission(Car.PERMISSION_CAR_MONITOR_CLUSTER_NAVIGATION_STATE)
    @AddedInOrBefore(majorVersion = 33)
    public void unregisterClusterNavigationStateListener(
            @NonNull ClusterNavigationStateListener callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        if (!mNavigationStateListeners.remove(new ClusterNavigationStateListenerRecord(
                /* executor= */ null, callback))) {
            return;
        }
        if (mNavigationStateListeners.isEmpty()) {
            try {
                mService.unregisterClusterNavigationStateListener(
                        mClusterNavigationStateListenerBinderCallback);
            } catch (RemoteException ignored) {
                // ignore for unregistering
            }
        }
    }

    private static class IClusterStateListenerImpl extends IClusterStateListener.Stub {
        private final WeakReference<ClusterHomeManager> mManager;

        private IClusterStateListenerImpl(ClusterHomeManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onClusterStateChanged(@NonNull ClusterState state, @Config int changes) {
            ClusterHomeManager manager = mManager.get();
            if (manager != null) {
                for (ClusterStateListenerRecord cb : manager.mStateListeners) {
                    cb.mExecutor.execute(
                            () -> cb.mListener.onClusterStateChanged(state, changes));
                }
            }
        }
    }

    private static class IClusterNavigationStateListenerImpl extends
            IClusterNavigationStateListener.Stub {
        private final WeakReference<ClusterHomeManager> mManager;

        private IClusterNavigationStateListenerImpl(ClusterHomeManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onNavigationStateChanged(@NonNull byte[] navigationState) {
            ClusterHomeManager manager = mManager.get();
            if (manager != null) {
                for (ClusterNavigationStateListenerRecord lr : manager.mNavigationStateListeners) {
                    lr.mExecutor.execute(() -> lr.mListener.onNavigationState(navigationState));
                }
            }
        }
    }

    /**
     * Reports the current ClusterUI state.
     * @param uiTypeMain uiType that ClusterHome tries to show in main area
     * @param uiTypeSub uiType that ClusterHome tries to show in sub area
     * @param uiAvailability the byte array to represent the availability of ClusterUI.
     *    0 indicates non-available and 1 indicates available.
     *    Index 0 is reserved for ClusterHome, The other indexes are followed by OEM's definition.
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @AddedInOrBefore(majorVersion = 33)
    public void reportState(int uiTypeMain, int uiTypeSub, @NonNull byte[] uiAvailability) {
        try {
            mService.reportState(uiTypeMain, uiTypeSub, uiAvailability);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Requests to turn the cluster display on to show some ClusterUI.
     * @param uiType uiType that ClusterHome tries to show in main area
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @AddedInOrBefore(majorVersion = 33)
    public void requestDisplay(int uiType) {
        try {
            mService.requestDisplay(uiType);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns the current {@code ClusterState}.
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @Nullable
    @AddedInOrBefore(majorVersion = 33)
    public ClusterState getClusterState() {
        ClusterState state = null;
        try {
            state = mService.getClusterState();
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return state;
    }

    /**
     * Start an activity as specified user. The activity is considered as in fixed mode for
     * the cluster display and will be re-launched if the activity crashes, the package
     * is updated or goes to background for whatever reason.
     * Only one activity can exist in fixed mode for the display and calling this multiple
     * times with different {@code Intent} will lead into making all previous activities into
     * non-fixed normal state (= will not be re-launched.)
     * @param intent the Intent to start
     * @param options additional options for how the Activity should be started
     * @param userId the user the new activity should run as
     * @return true if it launches the given Intent as FixedActivity successfully
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @AddedInOrBefore(majorVersion = 33)
    public boolean startFixedActivityModeAsUser(
            Intent intent, @Nullable Bundle options, int userId) {
        try {
            return mService.startFixedActivityModeAsUser(intent, options, userId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
        return false;
    }

    /**
     * The activity launched on the cluster display is no longer in fixed mode. Re-launching or
     * finishing should not trigger re-launching any more. Note that Activity for non-current user
     * will be auto-stopped and there is no need to call this for user switching. Note that this
     * does not stop the activity but it will not be re-launched any more.
     */
    @RequiresPermission(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL)
    @AddedInOrBefore(majorVersion = 33)
    public void stopFixedActivityMode() {
        try {
            mService.stopFixedActivityMode();
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    protected void onCarDisconnected() {
        mStateListeners.clear();
        mNavigationStateListeners.clear();
    }
}
