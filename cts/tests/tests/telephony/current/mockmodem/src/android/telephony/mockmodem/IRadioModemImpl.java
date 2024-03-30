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

package android.telephony.mockmodem;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.modem.IRadioModem;
import android.hardware.radio.modem.IRadioModemIndication;
import android.hardware.radio.modem.IRadioModemResponse;
import android.hardware.radio.modem.RadioState;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

public class IRadioModemImpl extends IRadioModem.Stub {
    private static final String TAG = "MRMDM";

    private final MockModemService mService;
    private IRadioModemResponse mRadioModemResponse;
    private IRadioModemIndication mRadioModemIndication;

    private int mForceRadioPowerError = -1;

    private static MockModemConfigInterface[] sMockModemConfigInterfaces;
    private Object mCacheUpdateMutex;
    private final Handler mHandler;
    private int mSubId;

    // ***** Events
    static final int EVENT_BASEBAND_VERSION_CHANGED = 1;
    static final int EVENT_DEVICE_IDENTITY_CHANGED = 2;
    static final int EVENT_RADIO_STATE_CHANGED = 3;

    // ***** Cache of modem attributes/status
    private String mBasebandVer;
    private String mImei;
    private String mImeiSv;
    private String mEsn;
    private String mMeid;
    private int mRadioState;

    public IRadioModemImpl(
            MockModemService service, MockModemConfigInterface[] interfaces, int instanceId) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
        sMockModemConfigInterfaces = interfaces;
        mCacheUpdateMutex = new Object();
        mHandler = new IRadioModemHandler();
        mSubId = instanceId;

        // Register events
        sMockModemConfigInterfaces[mSubId].registerForBasebandVersionChanged(
                mHandler, EVENT_BASEBAND_VERSION_CHANGED, null);
        sMockModemConfigInterfaces[mSubId].registerForDeviceIdentityChanged(
                mHandler, EVENT_DEVICE_IDENTITY_CHANGED, null);
        sMockModemConfigInterfaces[mSubId].registerForRadioStateChanged(
                mHandler, EVENT_RADIO_STATE_CHANGED, null);
    }

    /** Handler class to handle callbacks */
    private final class IRadioModemHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            synchronized (mCacheUpdateMutex) {
                switch (msg.what) {
                    case EVENT_BASEBAND_VERSION_CHANGED:
                        Log.d(TAG, "Received EVENT_BASEBAND_VERSION_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mBasebandVer = (String) ar.result;
                            Log.i(TAG, "Basedband version = " + mBasebandVer);
                        } else {
                            Log.e(
                                    TAG,
                                    msg.what
                                            + " failure. Not update baseband version."
                                            + ar.exception);
                        }
                        break;
                    case EVENT_DEVICE_IDENTITY_CHANGED:
                        Log.d(TAG, "Received EVENT_DEVICE_IDENTITY_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            String[] deviceIdentity = (String[]) ar.result;
                            mImei = deviceIdentity[0];
                            mImeiSv = deviceIdentity[1];
                            mEsn = deviceIdentity[2];
                            mMeid = deviceIdentity[3];
                            Log.i(
                                    TAG,
                                    "Device identity: IMEI = "
                                            + mImei
                                            + " IMEISV = "
                                            + mImeiSv
                                            + " ESN = "
                                            + mEsn
                                            + " MEID ="
                                            + mMeid);
                        } else {
                            Log.e(
                                    TAG,
                                    msg.what
                                            + " failure. Not update device identity."
                                            + ar.exception);
                        }
                        break;
                    case EVENT_RADIO_STATE_CHANGED:
                        Log.d(TAG, "Received EVENT_RADIO_STATE_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mRadioState = (int) ar.result;
                            Log.i(TAG, "Radio state: " + mRadioState);
                        } else {
                            Log.e(TAG, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                }
            }
        }
    }

    // Implementation of IRadioModem functions
    @Override
    public void setResponseFunctions(
            IRadioModemResponse radioModemResponse, IRadioModemIndication radioModemIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioModemResponse = radioModemResponse;
        mRadioModemIndication = radioModemIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void enableModem(int serial, boolean on) {
        Log.d(TAG, "getNumOfLiveModems " + on);

        // TODO: cache value
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.enableModemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to enableModem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getBasebandVersion(int serial) {
        Log.d(TAG, "getBasebandVersion");

        String baseband;

        synchronized (mCacheUpdateMutex) {
            baseband = mBasebandVer;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.getBasebandVersionResponse(rsp, baseband);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getBasebandVersion from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getDeviceIdentity(int serial) {
        Log.d(TAG, "getDeviceIdentity");

        String imei, imeisv, esn, meid;

        synchronized (mCacheUpdateMutex) {
            imei = mImei;
            imeisv = mImeiSv;
            esn = mEsn;
            meid = mMeid;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.getDeviceIdentityResponse(rsp, imei, imeisv, esn, meid);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getDeviceIdentity from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getHardwareConfig(int serial) {
        Log.d(TAG, "getHardwareConfig");

        android.hardware.radio.modem.HardwareConfig[] config =
                new android.hardware.radio.modem.HardwareConfig[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getHardwareConfigResponse(rsp, config);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getHardwareConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getModemActivityInfo(int serial) {
        Log.d(TAG, "getModemActivityInfo");

        android.hardware.radio.modem.ActivityStatsInfo activityInfo =
                new android.hardware.radio.modem.ActivityStatsInfo();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getModemActivityInfoResponse(rsp, activityInfo);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getModemActivityInfo from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getModemStackStatus(int serial) {
        Log.d(TAG, "getModemStackStatus");

        boolean isEnabled = false;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getModemStackStatusResponse(rsp, isEnabled);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getModemStackStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getRadioCapability(int serial) {
        Log.d(TAG, "getRadioCapability");

        android.hardware.radio.modem.RadioCapability rc =
                new android.hardware.radio.modem.RadioCapability();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.getRadioCapabilityResponse(rsp, rc);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvReadItem(int serial, int itemId) {
        Log.d(TAG, "nvReadItem");

        // TODO: cache value
        String result = "";

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvReadItemResponse(rsp, result);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvReadItem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvResetConfig(int serial, int resetType) {
        Log.d(TAG, "nvResetConfig");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvResetConfigResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvResetConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvWriteCdmaPrl(int serial, byte[] prl) {
        Log.d(TAG, "nvWriteCdmaPrl");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvWriteCdmaPrlResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvWriteCdmaPrl from AIDL. Exception" + ex);
        }
    }

    @Override
    public void nvWriteItem(int serial, android.hardware.radio.modem.NvWriteItem item) {
        Log.d(TAG, "nvWriteItem");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.nvWriteItemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to nvWriteItem from AIDL. Exception" + ex);
        }
    }

    @Override
    public void requestShutdown(int serial) {
        Log.d(TAG, "requestShutdown");

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.requestShutdownResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to requestShutdown from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendDeviceState(int serial, int deviceStateType, boolean state) {
        Log.d(TAG, "sendDeviceState");

        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioModemResponse.sendDeviceStateResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendDeviceState from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(TAG, "responseAcknowledgement");
    }

    @Override
    public void setRadioCapability(int serial, android.hardware.radio.modem.RadioCapability rc) {
        Log.d(TAG, "setRadioCapability");

        // TODO: cache value
        android.hardware.radio.modem.RadioCapability respRc =
                new android.hardware.radio.modem.RadioCapability();

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioModemResponse.setRadioCapabilityResponse(rsp, respRc);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setRadioCapability from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setRadioPower(
            int serial,
            boolean powerOn,
            boolean forEmergencyCall,
            boolean preferredForEmergencyCall) {
        Log.d(TAG, "setRadioPower");
        RadioResponseInfo rsp = null;

        // Check if the error response needs to be modified
        if (mForceRadioPowerError != -1) {
            rsp = mService.makeSolRsp(serial, mForceRadioPowerError);
        } else {
            synchronized (mCacheUpdateMutex) {
                if (powerOn) {
                    mRadioState = MockModemConfigInterface.RADIO_STATE_ON;
                } else {
                    mRadioState = MockModemConfigInterface.RADIO_STATE_OFF;
                }
                sMockModemConfigInterfaces[mSubId].setRadioState(mRadioState, TAG);
            }
            rsp = mService.makeSolRsp(serial);
        }

        try {
            mRadioModemResponse.setRadioPowerResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setRadioPower from AIDL. Exception" + ex);
        }

        if (rsp.error == RadioError.NONE) {
            if (powerOn) {
                radioStateChanged(RadioState.ON);
            } else {
                radioStateChanged(RadioState.OFF);
            }
        }
    }

    /**
     * Sent when setRadioCapability() completes. Returns the same RadioCapability as
     * getRadioCapability() and is the same as the one sent by setRadioCapability().
     *
     * @param radioCapability Current radio capability
     */
    public void radioCapabilityIndication(
            android.hardware.radio.modem.RadioCapability radioCapability) {
        Log.d(TAG, "radioCapabilityIndication");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.radioCapabilityIndication(
                        RadioIndicationType.UNSOLICITED, radioCapability);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to radioCapabilityIndication from AIDL. Exception" + ex);
            }
        } else {

            Log.e(TAG, "null mRadioModemIndication");
        }
    }

    /**
     * Indicates when radio state changes.
     *
     * @param radioState Current radio state
     */
    public void radioStateChanged(int radioState) {
        Log.d(TAG, "radioStateChanged");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.radioStateChanged(
                        RadioIndicationType.UNSOLICITED, radioState);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to radioStateChanged from AIDL. Exception" + ex);
            }
        } else {

            Log.e(TAG, "null mRadioModemIndication");
        }
    }

    /** Indicates the ril connects and returns the version. */
    public void rilConnected() {
        Log.d(TAG, "rilConnected");

        if (mRadioModemIndication != null) {
            try {
                mRadioModemIndication.rilConnected(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to rilConnected from AIDL. Exception" + ex);
            }
        } else {

            Log.e(TAG, "null mRadioModemIndication");
        }
    }

    public void forceErrorResponse(int requestId, int error) {
        switch (requestId) {
            case RIL_REQUEST_RADIO_POWER:
                mForceRadioPowerError = error;
                break;
            default:
                break;
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioModem.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioModem.VERSION;
    }
}
