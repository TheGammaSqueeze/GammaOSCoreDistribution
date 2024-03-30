/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.tbs;

import android.bluetooth.BluetoothLeCall;
import android.bluetooth.IBluetoothLeCallControl;
import android.bluetooth.IBluetoothLeCallControlCallback;
import android.content.AttributionSource;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.UUID;

public class TbsService extends ProfileService {

    private static final String TAG = "TbsService";
    private static final boolean DBG = true;

    private static TbsService sTbsService;

    private final TbsGeneric mTbsGeneric = new TbsGeneric();

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileCcpServerEnabled().orElse(false);
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new TbsServerBinder(this);
    }

    @Override
    protected void create() {
        if (DBG) {
            Log.d(TAG, "create()");
        }
    }

    @Override
    protected boolean start() {

        if (DBG) {
            Log.d(TAG, "start()");
        }
        if (sTbsService != null) {
            throw new IllegalStateException("start() called twice");
        }

        // Mark service as started
        setTbsService(this);

        mTbsGeneric.init(new TbsGatt(this));

        return true;
    }

    @Override
    protected boolean stop() {
        if (DBG) {
            Log.d(TAG, "stop()");
        }
        if (sTbsService == null) {
            Log.w(TAG, "stop() called before start()");
            return true;
        }

        // Mark service as stopped
        setTbsService(null);

        if (mTbsGeneric != null) {
            mTbsGeneric.cleanup();
        }

        return true;
    }

    @Override
    protected void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }
    }

    /**
     * Get the TbsService instance
     *
     * @return TbsService instance
     */
    public static synchronized TbsService getTbsService() {
        if (sTbsService == null) {
            Log.w(TAG, "getTbsService: service is NULL");
            return null;
        }

        if (!sTbsService.isAvailable()) {
            Log.w(TAG, "getTbsService: service is not available");
            return null;
        }

        return sTbsService;
    }

    private static synchronized void setTbsService(TbsService instance) {
        if (DBG) {
            Log.d(TAG, "setTbsService: set to=" + instance);
        }

        sTbsService = instance;
    }

    /** Binder object: must be a static class or memory leak may occur */
    @VisibleForTesting
    static class TbsServerBinder extends IBluetoothLeCallControl.Stub implements IProfileServiceBinder {
        private TbsService mService;

        private TbsService getService(AttributionSource source) {
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                Log.w(TAG, "TbsService call not allowed for non-active user");
                return null;
            }

            if (mService != null) {
                if (DBG) {
                    Log.d(TAG, "Service available");
                }

                enforceBluetoothPrivilegedPermission(mService);
                return mService;
            }

            return null;
        }

        TbsServerBinder(TbsService service) {
            mService = service;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public void registerBearer(String token, IBluetoothLeCallControlCallback callback, String uci,
                List<String> uriSchemes, int capabilities, String providerName, int technology,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.registerBearer(token, callback, uci, uriSchemes, capabilities, providerName,
                        technology);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void unregisterBearer(String token,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.unregisterBearer(token);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void requestResult(int ccid, int requestId, int result,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.requestResult(ccid, requestId, result);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void callAdded(int ccid, BluetoothLeCall call,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.callAdded(ccid, call);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void callRemoved(int ccid, ParcelUuid callId, int reason,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.callRemoved(ccid, callId.getUuid(), reason);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void callStateChanged(int ccid, ParcelUuid callId, int state,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.callStateChanged(ccid, callId.getUuid(), state);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void currentCallsList(int ccid, List<BluetoothLeCall> calls,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.currentCallsList(ccid, calls);
            } else {
                Log.w(TAG, "Service not active");
            }
        }

        @Override
        public void networkStateChanged(int ccid, String providerName, int technology,
                AttributionSource source) {
            TbsService service = getService(source);
            if (service != null) {
                service.networkStateChanged(ccid, providerName, technology);
            } else {
                Log.w(TAG, "Service not active");
            }
        }
    }

    @VisibleForTesting
    void registerBearer(String token, IBluetoothLeCallControlCallback callback, String uci,
            List<String> uriSchemes, int capabilities, String providerName, int technology) {
        if (DBG) {
            Log.d(TAG, "registerBearer: token=" + token);
        }

        boolean success = mTbsGeneric.addBearer(token, callback, uci, uriSchemes, capabilities,
                providerName, technology);
        if (success) {
            try {
                callback.asBinder().linkToDeath(() -> {
                    Log.e(TAG, token + " application died, removing...");
                    unregisterBearer(token);
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        if (DBG) {
            Log.d(TAG, "registerBearer: token=" + token + " success=" + success);
        }
    }

    @VisibleForTesting
    void unregisterBearer(String token) {
        if (DBG) {
            Log.d(TAG, "unregisterBearer: token=" + token);
        }

        mTbsGeneric.removeBearer(token);
    }

    @VisibleForTesting
    public void requestResult(int ccid, int requestId, int result) {
        if (DBG) {
            Log.d(TAG, "requestResult: ccid=" + ccid + " requestId=" + requestId + " result="
                    + result);
        }

        mTbsGeneric.requestResult(ccid, requestId, result);
    }

    @VisibleForTesting
    void callAdded(int ccid, BluetoothLeCall call) {
        if (DBG) {
            Log.d(TAG, "callAdded: ccid=" + ccid + " call=" + call);
        }

        mTbsGeneric.callAdded(ccid, call);
    }

    @VisibleForTesting
    void callRemoved(int ccid, UUID callId, int reason) {
        if (DBG) {
            Log.d(TAG, "callRemoved: ccid=" + ccid + " callId=" + callId + " reason=" + reason);
        }

        mTbsGeneric.callRemoved(ccid, callId, reason);
    }

    @VisibleForTesting
    void callStateChanged(int ccid, UUID callId, int state) {
        if (DBG) {
            Log.d(TAG, "callStateChanged: ccid=" + ccid + " callId=" + callId + " state=" + state);
        }

        mTbsGeneric.callStateChanged(ccid, callId, state);
    }

    @VisibleForTesting
    void currentCallsList(int ccid, List<BluetoothLeCall> calls) {
        if (DBG) {
            Log.d(TAG, "currentCallsList: ccid=" + ccid + " calls=" + calls);
        }

        mTbsGeneric.currentCallsList(ccid, calls);
    }

    @VisibleForTesting
    void networkStateChanged(int ccid, String providerName, int technology) {
        if (DBG) {
            Log.d(TAG, "networkStateChanged: ccid=" + ccid + " providerName=" + providerName
                    + " technology=" + technology);
        }

        mTbsGeneric.networkStateChanged(ccid, providerName, technology);
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
    }
}
