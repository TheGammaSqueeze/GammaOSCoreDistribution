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

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioIndicationType;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.config.IRadioConfig;
import android.hardware.radio.config.IRadioConfigIndication;
import android.hardware.radio.config.IRadioConfigResponse;
import android.hardware.radio.config.PhoneCapability;
import android.hardware.radio.config.SimSlotStatus;
import android.hardware.radio.config.SlotPortMapping;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

public class IRadioConfigImpl extends IRadioConfig.Stub {
    private static final String TAG = "MRCFG";

    private final MockModemService mService;
    private IRadioConfigResponse mRadioConfigResponse;
    private IRadioConfigIndication mRadioConfigIndication;
    private static MockModemConfigInterface[] sMockModemConfigInterfaces;
    private Object mCacheUpdateMutex;
    private final Handler mHandler;
    private int mSubId;

    // ***** Events
    static final int EVENT_NUM_OF_LIVE_MODEM_CHANGED = 1;
    static final int EVENT_PHONE_CAPABILITY_CHANGED = 2;
    static final int EVENT_SIM_SLOT_STATUS_CHANGED = 3;

    // ***** Cache of modem attributes/status
    private int mSlotNum = 1;
    private byte mNumOfLiveModems = 1;
    private PhoneCapability mPhoneCapability = new PhoneCapability();
    private SimSlotStatus[] mSimSlotStatus;

    public IRadioConfigImpl(
            MockModemService service, MockModemConfigInterface[] interfaces, int instanceId) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
        sMockModemConfigInterfaces = interfaces;
        mSlotNum = mService.getNumPhysicalSlots();
        mSimSlotStatus = new SimSlotStatus[mSlotNum];
        mCacheUpdateMutex = new Object();
        mHandler = new IRadioConfigHandler();
        mSubId = instanceId;

        // Register events
        sMockModemConfigInterfaces[mSubId].registerForNumOfLiveModemChanged(
                mHandler, EVENT_NUM_OF_LIVE_MODEM_CHANGED, null);
        sMockModemConfigInterfaces[mSubId].registerForPhoneCapabilityChanged(
                mHandler, EVENT_PHONE_CAPABILITY_CHANGED, null);
        sMockModemConfigInterfaces[mSubId].registerForSimSlotStatusChanged(
                mHandler, EVENT_SIM_SLOT_STATUS_CHANGED, null);
    }

    /** Handler class to handle callbacks */
    private final class IRadioConfigHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            synchronized (mCacheUpdateMutex) {
                switch (msg.what) {
                    case EVENT_NUM_OF_LIVE_MODEM_CHANGED:
                        Log.d(TAG, "Received EVENT_NUM_OF_LIVE_MODEM_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mNumOfLiveModems = (byte) ar.result;
                            Log.i(TAG, "Number of live modem: " + mNumOfLiveModems);
                        } else {
                            Log.e(TAG, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                    case EVENT_PHONE_CAPABILITY_CHANGED:
                        Log.d(TAG, "Received EVENT_PHONE_CAPABILITY_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mPhoneCapability = (PhoneCapability) ar.result;
                            Log.i(TAG, "Phone capability: " + mPhoneCapability);
                        } else {
                            Log.e(TAG, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                    case EVENT_SIM_SLOT_STATUS_CHANGED:
                        Log.d(TAG, "Received EVENT_SIM_SLOT_STATUS_CHANGED");
                        ar = (AsyncResult) msg.obj;
                        if (ar != null && ar.exception == null) {
                            mSimSlotStatus = (SimSlotStatus[]) ar.result;
                            for (int i = 0; i < mSlotNum; i++) {
                                Log.i(TAG, "Sim slot status: " + mSimSlotStatus[i]);
                            }
                            unsolSimSlotsStatusChanged();
                        } else {
                            Log.e(TAG, msg.what + " failure. Exception: " + ar.exception);
                        }
                        break;
                }
            }
        }
    }

    // Implementation of IRadioConfig functions
    @Override
    public void getHalDeviceCapabilities(int serial) {
        Log.d(TAG, "getHalDeviceCapabilities");

        boolean modemReducedFeatureSet1 = false;

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.getHalDeviceCapabilitiesResponse(rsp, modemReducedFeatureSet1);
        } catch (RemoteException ex) {
            Log.e(
                    TAG,
                    "Failed to invoke getHalDeviceCapabilitiesResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getNumOfLiveModems(int serial) {
        Log.d(TAG, "getNumOfLiveModems");
        byte numoflivemodem;

        synchronized (mCacheUpdateMutex) {
            numoflivemodem = mNumOfLiveModems;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioConfigResponse.getNumOfLiveModemsResponse(rsp, numoflivemodem);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke getNumOfLiveModemsResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getPhoneCapability(int serial) {
        Log.d(TAG, "getPhoneCapability");
        PhoneCapability phoneCapability;

        synchronized (mCacheUpdateMutex) {
            phoneCapability = mPhoneCapability;
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioConfigResponse.getPhoneCapabilityResponse(rsp, phoneCapability);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke getPhoneCapabilityResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSimSlotsStatus(int serial) {
        Log.d(TAG, "getSimSlotsStatus");
        SimSlotStatus[] slotStatus;

        synchronized (mCacheUpdateMutex) {
            if (mSlotNum < 1) {
                Log.d(TAG, "No slot information is retured.");
                slotStatus = null;
            } else {
                slotStatus = mSimSlotStatus;
            }
        }

        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioConfigResponse.getSimSlotsStatusResponse(rsp, slotStatus);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke getSimSlotsStatusResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setNumOfLiveModems(int serial, byte numOfLiveModems) {
        Log.d(TAG, "setNumOfLiveModems");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.setNumOfLiveModemsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke setNumOfLiveModemsResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setPreferredDataModem(int serial, byte modemId) {
        Log.d(TAG, "setPreferredDataModem");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.setPreferredDataModemResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke setPreferredDataModemResponse from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setResponseFunctions(
            IRadioConfigResponse radioConfigResponse,
            IRadioConfigIndication radioConfigIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioConfigResponse = radioConfigResponse;
        mRadioConfigIndication = radioConfigIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void setSimSlotsMapping(int serial, SlotPortMapping[] slotMap) {
        Log.d(TAG, "setSimSlotsMapping");
        // TODO: cache value

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioConfigResponse.setSimSlotsMappingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to invoke setSimSlotsMappingResponse from AIDL. Exception" + ex);
        }
    }

    public void unsolSimSlotsStatusChanged() {
        Log.d(TAG, "unsolSimSlotsStatusChanged");
        SimSlotStatus[] slotStatus;

        if (mRadioConfigIndication != null) {
            synchronized (mCacheUpdateMutex) {
                if (mSlotNum < 1) {
                    Log.d(TAG, "No slot information is retured.");
                    slotStatus = null;
                } else {
                    slotStatus = mSimSlotStatus;
                }
            }

            try {
                mRadioConfigIndication.simSlotsStatusChanged(
                        RadioIndicationType.UNSOLICITED, slotStatus);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to invoke simSlotsStatusChanged from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioConfigIndication");
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioConfig.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioConfig.VERSION;
    }
}
