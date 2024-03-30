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
import android.hardware.radio.data.DataProfileInfo;
import android.hardware.radio.data.IRadioData;
import android.hardware.radio.data.IRadioDataIndication;
import android.hardware.radio.data.IRadioDataResponse;
import android.hardware.radio.data.KeepaliveRequest;
import android.hardware.radio.data.LinkAddress;
import android.hardware.radio.data.SliceInfo;
import android.os.RemoteException;
import android.util.Log;

public class IRadioDataImpl extends IRadioData.Stub {
    private static final String TAG = "MRDATA";

    private final MockModemService mService;
    private IRadioDataResponse mRadioDataResponse;
    private IRadioDataIndication mRadioDataIndication;

    public IRadioDataImpl(MockModemService service) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
    }

    // Implementation of IRadioData functions
    @Override
    public void setResponseFunctions(
            IRadioDataResponse radioDataResponse, IRadioDataIndication radioDataIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioDataResponse = radioDataResponse;
        mRadioDataIndication = radioDataIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);

        unsolEmptyDataCallList();
    }

    @Override
    public void allocatePduSessionId(int serial) {
        Log.d(TAG, "allocatePduSessionId");
        int id = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.allocatePduSessionIdResponse(rsp, id);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to allocatePduSessionId from AIDL. Exception" + ex);
        }
    }

    @Override
    public void cancelHandover(int serial, int callId) {
        Log.d(TAG, "cancelHandover");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.cancelHandoverResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to cancelHandover from AIDL. Exception" + ex);
        }
    }

    @Override
    public void deactivateDataCall(int serial, int cid, int reason) {
        Log.d(TAG, "deactivateDataCall");
        RadioResponseInfo rsp = mService.makeSolRsp(serial);
        try {
            mRadioDataResponse.deactivateDataCallResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to deactivateDataCall from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getDataCallList(int serial) {
        Log.d(TAG, "getDataCallList");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.getDataCallListResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getDataCallList from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSlicingConfig(int serial) {
        Log.d(TAG, "getSlicingConfig");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.getSlicingConfigResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getSlicingConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void releasePduSessionId(int serial, int id) {
        Log.d(TAG, "releasePduSessionId");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.releasePduSessionIdResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to releasePduSessionId from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(TAG, "responseAcknowledgement");
    }

    @Override
    public void setDataAllowed(int serial, boolean allow) {
        Log.d(TAG, "setDataAllowed");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.setDataAllowedResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setDataAllowed from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setDataProfile(int serial, DataProfileInfo[] profiles) {
        Log.d(TAG, "setDataProfile");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.setDataProfileResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setDataProfile from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setDataThrottling(
            int serial, byte dataThrottlingAction, long completionDurationMillis) {
        Log.d(TAG, "setDataThrottling");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.setDataThrottlingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setDataThrottling from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setInitialAttachApn(int serial, DataProfileInfo dataProfileInfo) {
        Log.d(TAG, "setInitialAttachApn");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.setInitialAttachApnResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setInitialAttachApn from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setupDataCall(
            int serial,
            int accessNetwork,
            DataProfileInfo dataProfileInfo,
            boolean roamingAllowed,
            int reason,
            LinkAddress[] addresses,
            String[] dnses,
            int pduSessionId,
            SliceInfo sliceInfo,
            boolean matchAllRuleAllowed) {
        Log.d(TAG, "setupDataCall");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.setupDataCallResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setupDataCall from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startHandover(int serial, int callId) {
        Log.d(TAG, "startHandover");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.startHandoverResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to startHandover from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startKeepalive(int serial, KeepaliveRequest keepalive) {
        Log.d(TAG, "startKeepalive");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.startKeepaliveResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to startKeepalive from AIDL. Exception" + ex);
        }
    }

    @Override
    public void stopKeepalive(int serial, int sessionHandle) {
        Log.d(TAG, "stopKeepalive");
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioDataResponse.stopKeepaliveResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to stopKeepalive from AIDL. Exception" + ex);
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioData.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioData.VERSION;
    }

    public void unsolEmptyDataCallList() {
        Log.d(TAG, "unsolEmptyDataCallList");

        if (mRadioDataIndication != null) {
            android.hardware.radio.data.SetupDataCallResult[] dcList =
                    new android.hardware.radio.data.SetupDataCallResult[0];

            try {
                mRadioDataIndication.dataCallListChanged(RadioIndicationType.UNSOLICITED, dcList);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to invoke dataCallListChanged change from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioDataIndication");
        }
    }
}
