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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @hide
 */
public class RangingManager extends android.uwb.IUwbRangingCallbacks.Stub {
    private static final String TAG = "Uwb.RangingManager";

    private final IUwbAdapter mAdapter;
    private final Hashtable<SessionHandle, RangingSession> mRangingSessionTable = new Hashtable<>();
    private int mNextSessionId = 1;

    public RangingManager(IUwbAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Open a new ranging session
     *
     * @param attributionSource Attribution source to use for the enforcement of
     *                          {@link android.Manifest.permission#ULTRAWIDEBAND_RANGING} runtime
     *                          permission.
     * @param params the parameters that define the ranging session
     * @param executor {@link Executor} to run callbacks
     * @param callbacks {@link RangingSession.Callback} to associate with the {@link RangingSession}
     *                  that is being opened.
     * @param chipId identifier of UWB chip to be used in ranging session, or {@code null} if
     *                the default chip should be used
     * @return a {@link CancellationSignal} that may be used to cancel the opening of the
     *         {@link RangingSession}.
     */
    public CancellationSignal openSession(@NonNull AttributionSource attributionSource,
            @NonNull PersistableBundle params,
            @NonNull Executor executor,
            @NonNull RangingSession.Callback callbacks,
            @Nullable String chipId) {
        if (chipId != null) {
            try {
                List<String> validChipIds = mAdapter.getChipIds();
                if (!validChipIds.contains(chipId)) {
                    throw new IllegalArgumentException("openSession - received invalid chipId: "
                            + chipId);
                }
            } catch (RemoteException e)  {
                e.rethrowFromSystemServer();
            }
        }

        synchronized (this) {
            SessionHandle sessionHandle = new SessionHandle(mNextSessionId++);
            RangingSession session =
                    new RangingSession(executor, callbacks, mAdapter, sessionHandle, chipId);
            mRangingSessionTable.put(sessionHandle, session);
            try {
                mAdapter.openRanging(attributionSource,
                        sessionHandle,
                        this,
                        params,
                        chipId);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }

            CancellationSignal cancellationSignal = new CancellationSignal();
            cancellationSignal.setOnCancelListener(() -> session.close());
            return cancellationSignal;
        }
    }

    private boolean hasSession(SessionHandle sessionHandle) {
        return mRangingSessionTable.containsKey(sessionHandle);
    }

    @Override
    public void onRangingOpened(SessionHandle sessionHandle) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG,
                        "onRangingOpened - received unexpected SessionHandle: " + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingOpened();
        }
    }

    @Override
    public void onRangingOpenFailed(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG,
                        "onRangingOpenedFailed - received unexpected SessionHandle: "
                                + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingOpenFailed(convertToReason(reason), parameters);
            mRangingSessionTable.remove(sessionHandle);
        }
    }

    @Override
    public void onRangingReconfigured(SessionHandle sessionHandle, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG,
                        "onRangingReconfigured - received unexpected SessionHandle: "
                                + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingReconfigured(parameters);
        }
    }

    @Override
    public void onRangingReconfigureFailed(SessionHandle sessionHandle,
            @RangingChangeReason int reason, PersistableBundle params) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingReconfigureFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingReconfigureFailed(convertToReason(reason), params);
        }
    }


    @Override
    public void onRangingStarted(SessionHandle sessionHandle, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG,
                        "onRangingStarted - received unexpected SessionHandle: " + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingStarted(parameters);
        }
    }

    @Override
    public void onRangingStartFailed(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle params) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingStartFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingStartFailed(convertToReason(reason), params);
        }
    }

    @Override
    public void onRangingStopped(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle params) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingStopped - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingStopped(convertToReason(reason), params);
        }
    }

    @Override
    public void onRangingStopFailed(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingStopFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingStopFailed(convertToReason(reason), parameters);
        }
    }

    @Override
    public void onRangingClosed(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle params) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingClosed - received unexpected SessionHandle: " + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingClosed(convertToReason(reason), params);
            mRangingSessionTable.remove(sessionHandle);
        }
    }

    @Override
    public void onRangingResult(SessionHandle sessionHandle, RangingReport result) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingResult - received unexpected SessionHandle: " + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingResult(result);
        }
    }

    @Override
    public void onControleeAdded(SessionHandle sessionHandle, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onControleeAdded - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onControleeAdded(parameters);
        }
    }

    @Override
    public void onControleeAddFailed(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onControleeAddFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onControleeAddFailed(reason, parameters);
        }
    }

    @Override
    public void onControleeRemoved(SessionHandle sessionHandle, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onControleeRemoved - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onControleeRemoved(parameters);
        }
    }

    @Override
    public void onControleeRemoveFailed(SessionHandle sessionHandle,
            @RangingChangeReason int reason, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onControleeRemoveFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onControleeRemoveFailed(reason, parameters);
        }
    }

    @Override
    public void onRangingPaused(SessionHandle sessionHandle, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingPaused - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingPaused(parameters);
        }
    }

    @Override
    public void onRangingPauseFailed(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingPauseFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingPauseFailed(reason, parameters);
        }
    }

    @Override
    public void onRangingResumed(SessionHandle sessionHandle, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingResumed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingResumed(parameters);
        }
    }

    @Override
    public void onRangingResumeFailed(SessionHandle sessionHandle, @RangingChangeReason int reason,
            PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onRangingResumeFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onRangingResumeFailed(reason, parameters);
        }
    }

    @Override
    public void onDataSent(SessionHandle sessionHandle, UwbAddress remoteDeviceAddress,
            PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onDataSent - received unexpected SessionHandle: " + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onDataSent(remoteDeviceAddress, parameters);
        }
    }

    @Override
    public void onDataSendFailed(SessionHandle sessionHandle, UwbAddress remoteDeviceAddress,
            @RangingChangeReason int reason, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onDataSendFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onDataSendFailed(remoteDeviceAddress, reason, parameters);
        }
    }

    @Override
    public void onDataReceived(SessionHandle sessionHandle, UwbAddress remoteDeviceAddress,
            PersistableBundle parameters, byte[] data) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onDataReceived - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onDataReceived(remoteDeviceAddress, parameters, data);
        }
    }

    @Override
    public void onDataReceiveFailed(SessionHandle sessionHandle, UwbAddress remoteDeviceAddress,
            @RangingChangeReason int reason, PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onDataReceiveFailed - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onDataReceiveFailed(remoteDeviceAddress, reason, parameters);
        }
    }

    @Override
    public void onServiceDiscovered(SessionHandle sessionHandle,
            @NonNull PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onServiceDiscovered - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onServiceDiscovered(parameters);
        }
    }


    @Override
    public void onServiceConnected(SessionHandle sessionHandle,
            @NonNull PersistableBundle parameters) {
        synchronized (this) {
            if (!hasSession(sessionHandle)) {
                Log.w(TAG, "onServiceConnected - received unexpected SessionHandle: "
                        + sessionHandle);
                return;
            }

            RangingSession session = mRangingSessionTable.get(sessionHandle);
            session.onServiceConnected(parameters);
        }
    }

    // TODO(b/211025367): Remove this conversion and use direct API values.
    @RangingSession.Callback.Reason
    private static int convertToReason(@RangingChangeReason int reason) {
        switch (reason) {
            case RangingChangeReason.LOCAL_API:
                return RangingSession.Callback.REASON_LOCAL_REQUEST;

            case RangingChangeReason.MAX_SESSIONS_REACHED:
                return RangingSession.Callback.REASON_MAX_SESSIONS_REACHED;

            case RangingChangeReason.SYSTEM_POLICY:
                return RangingSession.Callback.REASON_SYSTEM_POLICY;

            case RangingChangeReason.REMOTE_REQUEST:
                return RangingSession.Callback.REASON_REMOTE_REQUEST;

            case RangingChangeReason.PROTOCOL_SPECIFIC:
                return RangingSession.Callback.REASON_PROTOCOL_SPECIFIC_ERROR;

            case RangingChangeReason.BAD_PARAMETERS:
                return RangingSession.Callback.REASON_BAD_PARAMETERS;

            case RangingChangeReason.MAX_RR_RETRY_REACHED:
                return RangingSession.Callback.REASON_MAX_RR_RETRY_REACHED;

            case RangingChangeReason.UNKNOWN:
            default:
                return RangingSession.Callback.REASON_UNKNOWN;
        }
    }
}
