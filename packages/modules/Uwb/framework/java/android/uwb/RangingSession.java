/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * This class provides a way to control an active UWB ranging session.
 * <p>It also defines the required {@link RangingSession.Callback} that must be implemented
 * in order to be notified of UWB ranging results and status events related to the
 * {@link RangingSession}.
 *
 * <p>To get an instance of {@link RangingSession}, first use
 * {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)} to request to open a
 * session. Once the session is opened, a {@link RangingSession} object is provided through
 * {@link RangingSession.Callback#onOpened(RangingSession)}. If opening a session fails, the failure
 * is reported through {@link RangingSession.Callback#onOpenFailed(int, PersistableBundle)} with the
 * failure reason.
 *
 * @hide
 */
@SystemApi
public final class RangingSession implements AutoCloseable {
    private static final String TAG = "Uwb.RangingSession";
    private final SessionHandle mSessionHandle;
    private final IUwbAdapter mAdapter;
    private final Executor mExecutor;
    private final Callback mCallback;
    private final String mChipId;

    private enum State {
        /**
         * The state of the {@link RangingSession} until
         * {@link RangingSession.Callback#onOpened(RangingSession)} is invoked
         */
        INIT,

        /**
         * The {@link RangingSession} is initialized and ready to begin ranging
         */
        IDLE,

        /**
         * The {@link RangingSession} is actively ranging
         */
        ACTIVE,

        /**
         * The {@link RangingSession} is closed and may not be used for ranging.
         */
        CLOSED
    }

    private State mState = State.INIT;

    /**
     * Interface for receiving {@link RangingSession} events
     */
    public interface Callback {
        /**
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                REASON_UNKNOWN,
                REASON_LOCAL_REQUEST,
                REASON_REMOTE_REQUEST,
                REASON_BAD_PARAMETERS,
                REASON_GENERIC_ERROR,
                REASON_MAX_SESSIONS_REACHED,
                REASON_SYSTEM_POLICY,
                REASON_PROTOCOL_SPECIFIC_ERROR,
                REASON_MAX_RR_RETRY_REACHED,
                REASON_SERVICE_DISCOVERY_FAILURE,
                REASON_SERVICE_CONNECTION_FAILURE,
                REASON_SE_NOT_SUPPORTED,
                REASON_SE_INTERACTION_FAILURE,
        })
        @interface Reason {}

        /**
         * Indicates that the session was closed or failed to open due to an unknown reason
         */
        int REASON_UNKNOWN = 0;

        /**
         * Indicates that the session was closed or failed to open because
         * {@link AutoCloseable#close()} or {@link RangingSession#close()} was called
         */
        int REASON_LOCAL_REQUEST = 1;

        /**
         * Indicates that the session was closed or failed to open due to an explicit request from
         * the remote device.
         */
        int REASON_REMOTE_REQUEST = 2;

        /**
         * Indicates that the session was closed or failed to open due to erroneous parameters
         */
        int REASON_BAD_PARAMETERS = 3;

        /**
         * Indicates an error on this device besides the error code already listed
         */
        int REASON_GENERIC_ERROR = 4;

        /**
         * Indicates that the number of currently open sessions supported by the device and
         * additional sessions may not be opened.
         */
        int REASON_MAX_SESSIONS_REACHED = 5;

        /**
         * Indicates that the local system policy caused the change, such
         * as privacy policy, power management policy, permissions, and more.
         */
        int REASON_SYSTEM_POLICY = 6;

        /**
         * Indicates a protocol specific error. The associated {@link PersistableBundle} should be
         * consulted for additional information.
         */
        int REASON_PROTOCOL_SPECIFIC_ERROR = 7;

        /**
         * Indicates that the max number of retry attempts for a ranging attempt has been reached.
         */
        int REASON_MAX_RR_RETRY_REACHED = 9;

        /**
         * Indicates a failure to discover the service after activation.
         */
        int REASON_SERVICE_DISCOVERY_FAILURE = 10;

        /**
         * Indicates a failure to connect to the service after discovery.
         */
        int REASON_SERVICE_CONNECTION_FAILURE = 11;

        /**
         * The device doesn’t support FiRA Applet.
         */
        int REASON_SE_NOT_SUPPORTED = 12;

        /**
         * SE interactions failed.
         */
        int REASON_SE_INTERACTION_FAILURE = 13;

        /**
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                CONTROLEE_FAILURE_REASON_MAX_CONTROLEE_REACHED,
        })
        @interface ControleeFailureReason {}

        /**
         * Indicates that the session has reached the max number of controlees supported by the
         * device. This is applicable to only one to many sessions and sent in response to a
         * request to add a new controlee to an ongoing session.
         */
        int CONTROLEE_FAILURE_REASON_MAX_CONTROLEE_REACHED = 0;

        /**
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {
                DATA_FAILURE_REASON_DATA_SIZE_TOO_LARGE,
        })
        @interface DataFailureReason {}

        /**
         * Indicates that the size of the data being sent or received is too large.
         */
        int DATA_FAILURE_REASON_DATA_SIZE_TOO_LARGE = 10;

        /**
         * Invoked when {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}
         * is successful
         *
         * @param session the newly opened {@link RangingSession}
         */
        void onOpened(@NonNull RangingSession session);

        /**
         * Invoked if {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}}
         * fails
         *
         * @param reason the failure reason
         * @param params protocol specific parameters
         */
        void onOpenFailed(@Reason int reason, @NonNull PersistableBundle params);

        /**
         * Invoked either,
         *  - when {@link RangingSession#start(PersistableBundle)} is successful if the session is
         *    using a custom profile, OR
         *  - when platform starts ranging after OOB discovery + negotiation if the session is
         *    using a platform defined profile.
         * @param sessionInfo session specific parameters from the lower layers
         */
        void onStarted(@NonNull PersistableBundle sessionInfo);

        /**
         * Invoked either,
         *   - when {@link RangingSession#start(PersistableBundle)} fails if
         *     the session is using a custom profile, OR
         *   - when platform fails ranging after OOB discovery + negotiation if the
         *     session is using a platform defined profile.
         *
         * @param reason the failure reason
         * @param params protocol specific parameters
         */
        void onStartFailed(@Reason int reason, @NonNull PersistableBundle params);

        /**
         * Invoked when a request to reconfigure the session succeeds
         *
         * @param params the updated ranging configuration
         */
        void onReconfigured(@NonNull PersistableBundle params);

        /**
         * Invoked when a request to reconfigure the session fails
         *
         * @param reason reason the session failed to be reconfigured
         * @param params protocol specific failure reasons
         */
        void onReconfigureFailed(@Reason int reason, @NonNull PersistableBundle params);

        /**
         * Invoked when a request to stop the session succeeds
         *
         * @param reason reason for the session stop
         * @param parameters protocol specific parameters related to the stop reason
         */
        void onStopped(@Reason int reason, @NonNull PersistableBundle parameters);

        /**
         * Invoked when a request to stop the session fails
         *
         * @param reason reason the session failed to be stopped
         * @param params protocol specific failure reasons
         */
        void onStopFailed(@Reason int reason, @NonNull PersistableBundle params);

       /**
         * Invoked when session is either closed spontaneously, or per user request via
         * {@link RangingSession#close()} or {@link AutoCloseable#close()}.
         *
         * @param reason reason for the session closure
         * @param parameters protocol specific parameters related to the close reason
         */
        void onClosed(@Reason int reason, @NonNull PersistableBundle parameters);

        /**
         * Called once per ranging interval even when a ranging measurement fails
         *
         * @param rangingReport ranging report for this interval's measurements
         */
        void onReportReceived(@NonNull RangingReport rangingReport);

        /**
         * Invoked when a new controlee is added to an ongoing one-to many session.
         *
         * @param parameters protocol specific parameters for the new controlee
         */
        default void onControleeAdded(@NonNull PersistableBundle parameters) {}

        /**
         * Invoked when a new controlee is added to an ongoing one-to many session.
         *
         * @param reason reason for the controlee add failure
         * @param parameters protocol specific parameters related to the failure
         */
        default void onControleeAddFailed(
                @ControleeFailureReason int reason, @NonNull PersistableBundle parameters) {}

        /**
         * Invoked when an existing controlee is removed from an ongoing one-to many session.
         *
         * @param parameters protocol specific parameters for the existing controlee
         */
        default void onControleeRemoved(@NonNull PersistableBundle parameters) {}

        /**
         * Invoked when a new controlee is added to an ongoing one-to many session.
         *
         * @param reason reason for the controlee remove failure
         * @param parameters protocol specific parameters related to the failure
         */
        default void onControleeRemoveFailed(
                @ControleeFailureReason int reason, @NonNull PersistableBundle parameters) {}

        /**
         * Invoked when an ongoing session is successfully pauseed.
         *
         * @param parameters protocol specific parameters sent for suspension
         */
        default void onPaused(@NonNull PersistableBundle parameters) {}

        /**
         * Invoked when an ongoing session suspension fails.
         *
         * @param reason reason for the suspension failure
         * @param parameters protocol specific parameters for suspension failure
         */
        default void onPauseFailed(@Reason int reason, @NonNull PersistableBundle parameters) {}

        /**
         * Invoked when a pauseed session is successfully resumed.
         *
         * @param parameters protocol specific parameters sent for suspension
         */
        default void onResumed(@NonNull PersistableBundle parameters) {}

        /**
         * Invoked when a pauseed session resumption fails.
         *
         * @param reason reason for the resumption failure
         * @param parameters protocol specific parameters for resumption failure
         */
        default void onResumeFailed(@Reason int reason, @NonNull PersistableBundle parameters) {}

        /**
         * Invoked when data is successfully sent via {@link RangingSession#sendData(UwbAddress,
         * PersistableBundle, byte[])}.
         *
         * @param remoteDeviceAddress remote device's address
         * @param parameters protocol specific parameters sent for suspension
         */
        default void onDataSent(@NonNull UwbAddress remoteDeviceAddress,
                @NonNull PersistableBundle parameters) {}

        /**
         * Invoked when data send to a remote device via {@link RangingSession#sendData(UwbAddress,
         * PersistableBundle, byte[])} fails.
         *
         * @param remoteDeviceAddress remote device's address
         * @param reason reason for the resumption failure
         * @param parameters protocol specific parameters for resumption failure
         */
        default void onDataSendFailed(@NonNull UwbAddress remoteDeviceAddress,
                @DataFailureReason int reason, @NonNull PersistableBundle parameters) {}

        /**
         * Invoked when data is received successfully from a remote device.
         * The data is received piggybacked over RRM (initiator -> responder) or
         * RIM (responder -> initiator).
         * <p> This is only functional on a FIRA 2.0 compliant device.
         *
         * @param remoteDeviceAddress remote device's address
         * @param data Raw data received
         * @param parameters protocol specific parameters for the received data
         */
        default void onDataReceived(@NonNull UwbAddress remoteDeviceAddress,
                @NonNull PersistableBundle parameters, @NonNull byte[] data) {}

        /**
         * Invoked when data receive from a remote device fails.
         *
         * @param remoteDeviceAddress remote device's address
         * @param reason reason for the reception failure
         * @param parameters protocol specific parameters for resumption failure
         */
        default void onDataReceiveFailed(@NonNull UwbAddress remoteDeviceAddress,
                @DataFailureReason int reason, @NonNull PersistableBundle parameters) {}

        /**
         * Invoked when service is discovered via OOB.
         * <p>
         * If this a one to many session, this can be invoked multiple times to indicate different
         * peers being discovered.
         * </p>
         *
         * @param parameters protocol specific params for discovered service.
         */
        default void onServiceDiscovered(@NonNull PersistableBundle parameters) {}

        /**
         * Invoked when service is connected via OOB.
         * <p>
         * If this a one to many session, this can be invoked multiple times to indicate different
         * peers being connected.
         * </p>
         *
         * @param parameters protocol specific params for connected service.
         */
        default void onServiceConnected(@NonNull PersistableBundle parameters) {}
    }

    /**
     * @hide
     */
    public RangingSession(Executor executor, Callback callback, IUwbAdapter adapter,
            SessionHandle sessionHandle) {
        this(executor, callback, adapter, sessionHandle, /* chipId= */ null);
    }

    /**
     * @hide
     */
    public RangingSession(Executor executor, Callback callback, IUwbAdapter adapter,
            SessionHandle sessionHandle, String chipId) {
        mState = State.INIT;
        mExecutor = executor;
        mCallback = callback;
        mAdapter = adapter;
        mSessionHandle = sessionHandle;
        mChipId = chipId;
    }

    /**
     * @hide
     */
    public boolean isOpen() {
        return mState == State.IDLE || mState == State.ACTIVE;
    }

    /**
     * If the session uses custom profile,
     *    Begins ranging for the session.
     *    <p>On successfully starting a ranging session,
     *    {@link RangingSession.Callback#onStarted(PersistableBundle)} is invoked.
     *    <p>On failure to start the session,
     *    {@link RangingSession.Callback#onStartFailed(int, PersistableBundle)}
     *    is invoked.
     *
     * If the session uses platform defined profile (like PACS),
     *    Begins OOB discovery for the service. Once the service is discovered,
     *    UWB session params are negotiated via OOB and a UWB session will be
     *    started.
     *    <p>On successfully discovering a service,
     *    {@link RangingSession.Callback#onServiceDiscovered(PersistableBundle)} is invoked.
     *    <p>On successfully connecting to a service,
     *    {@link RangingSession.Callback#onServiceConnected(PersistableBundle)} is invoked.
     *    <p>On successfully starting a ranging session,
     *    {@link RangingSession.Callback#onStarted(PersistableBundle)} is invoked.
     *    <p>On failure to start the session,
     *    {@link RangingSession.Callback#onStartFailed(int, PersistableBundle)}
     *    is invoked.
     *
     * @param params configuration parameters for starting the session
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void start(@NonNull PersistableBundle params) {
        if (mState != State.IDLE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.startRanging(mSessionHandle, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Attempts to reconfigure the session with the given parameters
     * <p>This call may be made when the session is open.
     *
     * <p>On successfully reconfiguring the session
     * {@link RangingSession.Callback#onReconfigured(PersistableBundle)} is invoked.
     *
     * <p>On failure to reconfigure the session,
     * {@link RangingSession.Callback#onReconfigureFailed(int, PersistableBundle)} is invoked.
     *
     * @param params the parameters to reconfigure and their new values
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void reconfigure(@NonNull PersistableBundle params) {
        if (mState != State.ACTIVE && mState != State.IDLE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.reconfigureRanging(mSessionHandle, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Stops actively ranging
     *
     * <p>A session that has been stopped may be resumed by calling
     * {@link RangingSession#start(PersistableBundle)} without the need to open a new session.
     *
     * <p>Stopping a {@link RangingSession} is useful when the lower layers should not discard
     * the parameters of the session, or when a session needs to be able to be resumed quickly.
     *
     * <p>If the {@link RangingSession} is no longer needed, use {@link RangingSession#close()} to
     * completely close the session and allow lower layers of the stack to perform necessarily
     * cleanup.
     *
     * <p>Stopped sessions may be closed by the system at any time. In such a case,
     * {@link RangingSession.Callback#onClosed(int, PersistableBundle)} is invoked.
     *
     * <p>On failure to stop the session,
     * {@link RangingSession.Callback#onStopFailed(int, PersistableBundle)} is invoked.
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void stop() {
        if (mState != State.ACTIVE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.stopRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Close the ranging session
     *
     * <p>After calling this function, in order resume ranging, a new {@link RangingSession} must
     * be opened by calling
     * {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}.
     *
     * <p>If this session is currently ranging, it will stop and close the session.
     * <p>If the session is in the process of being opened, it will attempt to stop the session from
     * being opened.
     * <p>If the session is already closed, the registered
     * {@link Callback#onClosed(int, PersistableBundle)} callback will still be invoked.
     *
     * <p>{@link Callback#onClosed(int, PersistableBundle)} will be invoked using the same callback
     * object given to {@link UwbManager#openRangingSession(PersistableBundle, Executor, Callback)}
     * when the {@link RangingSession} was opened. The callback will be invoked after each call to
     * {@link #close()}, even if the {@link RangingSession} is already closed.
     */
    @Override
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void close() {
        if (mState == State.CLOSED) {
            mExecutor.execute(() -> mCallback.onClosed(
                    Callback.REASON_LOCAL_REQUEST, new PersistableBundle()));
            return;
        }

        try {
            mAdapter.closeRanging(mSessionHandle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Add a new controlee to an ongoing session.
     * <p>This call may be made when the session is open.
     *
     * <p>On successfully adding a new controlee to the session
     * {@link RangingSession.Callback#onControleeAdded(PersistableBundle)} is invoked.
     *
     * <p>On failure to add a new controlee to the session,
     * {@link RangingSession.Callback#onControleeAddFailed(int, PersistableBundle)} is invoked.
     *
     * @param params the parameters for the new controlee
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void addControlee(@NonNull PersistableBundle params) {
        if (mState != State.ACTIVE && mState != State.IDLE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.addControlee(mSessionHandle, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove an existing controlee from an ongoing session.
     * <p>This call may be made when the session is open.
     *
     * <p>On successfully removing an existing controlee from the session
     * {@link RangingSession.Callback#onControleeRemoved(PersistableBundle)} is invoked.
     *
     * <p>On failure to remove an existing controlee from the session,
     * {@link RangingSession.Callback#onControleeRemoveFailed(int, PersistableBundle)} is invoked.
     *
     * @param params the parameters for the existing controlee
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void removeControlee(@NonNull PersistableBundle params) {
        if (mState != State.ACTIVE && mState != State.IDLE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.removeControlee(mSessionHandle, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Pauses an ongoing ranging session.
     *
     * <p>A session that has been pauseed may be resumed by calling
     * {@link RangingSession#resume(PersistableBundle)} without the need to open a new session.
     *
     * <p>Pauseing a {@link RangingSession} is useful when the lower layers should skip a few
     * ranging rounds for a session without stopping it.
     *
     * <p>If the {@link RangingSession} is no longer needed, use {@link RangingSession#stop()} or
     * {@link RangingSession#close()} to completely close the session.
     *
     * <p>On successfully pausing the session,
     * {@link RangingSession.Callback#onRangingPaused(PersistableBundle)} is invoked.
     *
     * <p>On failure to pause the session,
     * {@link RangingSession.Callback#onRangingPauseFailed(int, PersistableBundle)} is invoked.
     *
     * @param params protocol specific parameters for pausing the session
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void pause(@NonNull PersistableBundle params) {
        if (mState != State.ACTIVE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.pause(mSessionHandle, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Resumes a pauseed ranging session.
     *
     * <p>A session that has been previously pauseed using
     * {@link RangingSession#pause(PersistableBundle)} can be resumed by calling
     * {@link RangingSession#resume(PersistableBundle)}.
     *
     * <p>On successfully resuming the session,
     * {@link RangingSession.Callback#onRangingResumed(PersistableBundle)} is invoked.
     *
     * <p>On failure to resume the session,
     * {@link RangingSession.Callback#onRangingResumeFailed(int, PersistableBundle)} is invoked.
     *
     * @param params protocol specific parameters the resuming the session
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void resume(@NonNull PersistableBundle params) {
        if (mState != State.ACTIVE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.resume(mSessionHandle, params);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Send data to a remote device which is part of this ongoing session.
     * The data is sent by piggybacking the provided data over RRM (initiator -> responder) or
     * RIM (responder -> initiator).
     * <p>This is only functional on a FIRA 2.0 compliant device.
     *
     * <p>On successfully sending the data,
     * {@link RangingSession.Callback#onDataSent(UwbAddress, PersistableBundle)} is invoked.
     *
     * <p>On failure to send the data,
     * {@link RangingSession.Callback#onDataSendFailed(UwbAddress, int, PersistableBundle)} is
     * invoked.
     *
     * @param remoteDeviceAddress remote device's address
     * @param params protocol specific parameters the sending the data
     * @param data Raw data to be sent
     */
    @RequiresPermission(Manifest.permission.UWB_PRIVILEGED)
    public void sendData(@NonNull UwbAddress remoteDeviceAddress,
            @NonNull PersistableBundle params, @NonNull byte[] data) {
        if (mState != State.ACTIVE) {
            throw new IllegalStateException();
        }

        try {
            mAdapter.sendData(mSessionHandle, remoteDeviceAddress, params, data);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public void onRangingOpened() {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingOpened invoked for a closed session");
            return;
        }

        mState = State.IDLE;
        executeCallback(() -> mCallback.onOpened(this));
    }

    /**
     * @hide
     */
    public void onRangingOpenFailed(@Callback.Reason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingOpenFailed invoked for a closed session");
            return;
        }

        mState = State.CLOSED;
        executeCallback(() -> mCallback.onOpenFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingStarted(@NonNull PersistableBundle parameters) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStarted invoked for a closed session");
            return;
        }

        mState = State.ACTIVE;
        executeCallback(() -> mCallback.onStarted(parameters));
    }

    /**
     * @hide
     */
    public void onRangingStartFailed(@Callback.Reason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStartFailed invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onStartFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingReconfigured(@NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingReconfigured invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onReconfigured(params));
    }

    /**
     * @hide
     */
    public void onRangingReconfigureFailed(@Callback.Reason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingReconfigureFailed invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onReconfigureFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingStopped(@Callback.Reason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStopped invoked for a closed session");
            return;
        }

        mState = State.IDLE;
        executeCallback(() -> mCallback.onStopped(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingStopFailed(@Callback.Reason int reason,
            @NonNull PersistableBundle params) {
        if (mState == State.CLOSED) {
            Log.w(TAG, "onRangingStopFailed invoked for a closed session");
            return;
        }

        executeCallback(() -> mCallback.onStopFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingClosed(@Callback.Reason int reason,
            @NonNull PersistableBundle parameters) {
        mState = State.CLOSED;
        executeCallback(() -> mCallback.onClosed(reason, parameters));
    }

    /**
     * @hide
     */
    public void onRangingResult(@NonNull RangingReport report) {
        if (!isOpen()) {
            Log.w(TAG, "onRangingResult invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onReportReceived(report));
    }

    /**
     * @hide
     */
    public void onControleeAdded(@NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onControleeAdded invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onControleeAdded(params));
    }

    /**
     * @hide
     */
    public void onControleeAddFailed(@Callback.ControleeFailureReason int reason,
            @NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onControleeAddFailed invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onControleeAddFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onControleeRemoved(@NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onControleeRemoved invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onControleeRemoved(params));
    }

    /**
     * @hide
     */
    public void onControleeRemoveFailed(@Callback.ControleeFailureReason int reason,
            @NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onControleeRemoveFailed invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onControleeRemoveFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingPaused(@NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onRangingPaused invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onPaused(params));
    }

    /**
     * @hide
     */
    public void onRangingPauseFailed(@Callback.Reason int reason,
            @NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onRangingPauseFailed invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onPauseFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onRangingResumed(@NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onRangingResumed invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onResumed(params));
    }

    /**
     * @hide
     */
    public void onRangingResumeFailed(@Callback.Reason int reason,
            @NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onRangingResumeFailed invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onResumeFailed(reason, params));
    }

    /**
     * @hide
     */
    public void onDataSent(@NonNull UwbAddress remoteDeviceAddress,
            @NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onDataSent invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onDataSent(remoteDeviceAddress, params));
    }

    /**
     * @hide
     */
    public void onDataSendFailed(@NonNull UwbAddress remoteDeviceAddress,
            @Callback.DataFailureReason int reason, @NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onDataSendFailed invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onDataSendFailed(remoteDeviceAddress, reason, params));
    }

    /**
     * @hide
     */
    public void onDataReceived(@NonNull UwbAddress remoteDeviceAddress,
            @NonNull PersistableBundle params, @NonNull byte[] data) {
        if (!isOpen()) {
            Log.w(TAG, "onDataReceived invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onDataReceived(remoteDeviceAddress, params, data));
    }

    /**
     * @hide
     */
    public void onDataReceiveFailed(@NonNull UwbAddress remoteDeviceAddress,
            @Callback.DataFailureReason int reason, @NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onDataReceiveFailed invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onDataReceiveFailed(remoteDeviceAddress, reason, params));
    }

    /**
     * @hide
     */
    public void onServiceDiscovered(@NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onServiceDiscovered invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onServiceDiscovered(params));
    }

    /**
     * @hide
     */
    public void onServiceConnected(@NonNull PersistableBundle params) {
        if (!isOpen()) {
            Log.w(TAG, "onServiceConnected invoked for non-open session");
            return;
        }

        executeCallback(() -> mCallback.onServiceConnected(params));
    }

    /**
     * @hide
     */
    private void executeCallback(@NonNull Runnable runnable) {
        final long identity = Binder.clearCallingIdentity();
        try {
            mExecutor.execute(runnable);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
}
