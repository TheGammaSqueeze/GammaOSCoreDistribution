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
import android.hardware.radio.messaging.IRadioMessaging;
import android.hardware.radio.messaging.IRadioMessagingIndication;
import android.hardware.radio.messaging.IRadioMessagingResponse;
import android.os.RemoteException;
import android.support.annotation.GuardedBy;
import android.util.ArraySet;
import android.util.Log;

import java.util.Set;

public class IRadioMessagingImpl extends IRadioMessaging.Stub {
    private static final String TAG = "MRMSG";

    private final MockModemService mService;
    private IRadioMessagingResponse mRadioMessagingResponse;
    private IRadioMessagingIndication mRadioMessagingIndication;
    @GuardedBy("mGsmBroadcastConfigSet")
    private final Set<Integer> mGsmBroadcastConfigSet = new ArraySet<Integer>();
    @GuardedBy("mCdmaBroadcastConfigSet")
    private final Set<Integer> mCdmaBroadcastConfigSet = new ArraySet<Integer>();

    public IRadioMessagingImpl(MockModemService service) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
    }

    // Implementation of IRadioMessaging functions
    @Override
    public void setResponseFunctions(
            IRadioMessagingResponse radioMessagingResponse,
            IRadioMessagingIndication radioMessagingIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioMessagingResponse = radioMessagingResponse;
        mRadioMessagingIndication = radioMessagingIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(int serial, boolean success, String ackPdu) {
        Log.d(TAG, "acknowledgeIncomingGsmSmsWithPdu");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.acknowledgeIncomingGsmSmsWithPduResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to acknowledgeIncomingGsmSmsWithPdu from AIDL. Exception" + ex);
        }
    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(
            int serial, android.hardware.radio.messaging.CdmaSmsAck smsAck) {
        Log.d(TAG, "acknowledgeLastIncomingCdmaSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.acknowledgeLastIncomingCdmaSmsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to acknowledgeLastIncomingCdmaSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void acknowledgeLastIncomingGsmSms(int serial, boolean success, int cause) {
        Log.d(TAG, "acknowledgeLastIncomingGsmSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.acknowledgeLastIncomingGsmSmsResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to acknowledgeLastIncomingGsmSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void deleteSmsOnRuim(int serial, int index) {
        Log.d(TAG, "deleteSmsOnRuim");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.deleteSmsOnRuimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to deleteSmsOnRuim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void deleteSmsOnSim(int serial, int index) {
        Log.d(TAG, "deleteSmsOnSim");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.deleteSmsOnSimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to deleteSmsOnSim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCdmaBroadcastConfig(int serial) {
        Log.d(TAG, "getCdmaBroadcastConfig");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.getCdmaBroadcastConfigResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCdmaBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getGsmBroadcastConfig(int serial) {
        Log.d(TAG, "getGsmBroadcastConfig");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.getGsmBroadcastConfigResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getGsmBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getSmscAddress(int serial) {
        Log.d(TAG, "getSmscAddress");

        String smsc = "";
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.getSmscAddressResponse(rsp, smsc);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getSmscAddress from AIDL. Exception" + ex);
        }
    }

    @Override
    public void reportSmsMemoryStatus(int serial, boolean available) {
        Log.d(TAG, "reportSmsMemoryStatus");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.reportSmsMemoryStatusResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to reportSmsMemoryStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(TAG, "responseAcknowledgement");
        // TODO
    }

    @Override
    public void sendCdmaSms(int serial, android.hardware.radio.messaging.CdmaSmsMessage sms) {
        Log.d(TAG, "sendCdmaSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendCdmaSmsResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendCdmaSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendCdmaSmsExpectMore(
            int serial, android.hardware.radio.messaging.CdmaSmsMessage sms) {
        Log.d(TAG, "sendCdmaSmsExpectMore");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendCdmaSmsExpectMoreResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendCdmaSmsExpectMore from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendImsSms(int serial, android.hardware.radio.messaging.ImsSmsMessage message) {
        Log.d(TAG, "sendImsSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendImsSmsResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendImsSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendSms(int serial, android.hardware.radio.messaging.GsmSmsMessage message) {
        Log.d(TAG, "sendSms");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendSmsResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendSms from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendSmsExpectMore(
            int serial, android.hardware.radio.messaging.GsmSmsMessage message) {
        Log.d(TAG, "sendSmsExpectMore");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.sendSmsExpectMoreResponse(rsp, null);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendSmsExpectMore from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCdmaBroadcastActivation(int serial, boolean activate) {
        Log.d(TAG, "setCdmaBroadcastActivation");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.setCdmaBroadcastActivationResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCdmaBroadcastActivation from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCdmaBroadcastConfig(
            int serial, android.hardware.radio.messaging.CdmaBroadcastSmsConfigInfo[] configInfo) {
        Log.d(TAG, "setCdmaBroadcastConfig");

        int error = RadioError.NONE;
        if (configInfo == null || configInfo.length == 0) {
            error = RadioError.INVALID_ARGUMENTS;
        } else {
            synchronized (mCdmaBroadcastConfigSet) {
                mCdmaBroadcastConfigSet.clear();
                for (int i = 0; i < configInfo.length; i++) {
                    Log.d(TAG, "configInfo serviceCategory"
                            + configInfo[i].serviceCategory);
                    mCdmaBroadcastConfigSet.add(configInfo[i].serviceCategory);
                }
            }
        }
        RadioResponseInfo rsp = mService.makeSolRsp(serial, error);
        try {
            mRadioMessagingResponse.setCdmaBroadcastConfigResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCdmaBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setGsmBroadcastActivation(int serial, boolean activate) {
        Log.d(TAG, "setGsmBroadcastActivation");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.setGsmBroadcastActivationResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setGsmBroadcastActivation from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setGsmBroadcastConfig(
            int serial, android.hardware.radio.messaging.GsmBroadcastSmsConfigInfo[] configInfo) {
        Log.d(TAG, "setGsmBroadcastConfig");

        int error = RadioError.NONE;
        if (configInfo == null || configInfo.length == 0) {
            error = RadioError.INVALID_ARGUMENTS;
        } else {
            synchronized (mGsmBroadcastConfigSet) {
                mGsmBroadcastConfigSet.clear();
                for (int i = 0; i < configInfo.length; i++) {
                    int startId = configInfo[i].fromServiceId;
                    int endId = configInfo[i].toServiceId;
                    boolean selected  = configInfo[i].selected;
                    Log.d(TAG, "configInfo from: " + startId + ", to: " + endId
                            + ", selected: " + selected);
                    if (selected) {
                        for (int j = startId; j <= endId; j++) {
                            mGsmBroadcastConfigSet.add(j);
                        }
                    }
                }
            }
        }
        RadioResponseInfo rsp = mService.makeSolRsp(serial, error);
        try {
            mRadioMessagingResponse.setGsmBroadcastConfigResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setGsmBroadcastConfig from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setSmscAddress(int serial, String smsc) {
        Log.d(TAG, "setSmscAddress");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.setSmscAddressResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setSmscAddress from AIDL. Exception" + ex);
        }
    }

    @Override
    public void writeSmsToRuim(
            int serial, android.hardware.radio.messaging.CdmaSmsWriteArgs cdmaSms) {
        Log.d(TAG, "writeSmsToRuim");

        int index = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.writeSmsToRuimResponse(rsp, index);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to writeSmsToRuim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void writeSmsToSim(
            int serial, android.hardware.radio.messaging.SmsWriteArgs smsWriteArgs) {
        Log.d(TAG, "writeSmsToSim");

        int index = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioMessagingResponse.writeSmsToSimResponse(rsp, index);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to writeSmsToSim from AIDL. Exception" + ex);
        }
    }

    public void cdmaNewSms(android.hardware.radio.messaging.CdmaSmsMessage msg) {
        Log.d(TAG, "cdmaNewSms");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.cdmaNewSms(RadioIndicationType.UNSOLICITED, msg);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to cdmaNewSms indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioMessagingIndication");
        }
    }

    public void cdmaRuimSmsStorageFull() {
        Log.d(TAG, "cdmaRuimSmsStorageFull");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.cdmaRuimSmsStorageFull(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to cdmaRuimSmsStorageFull indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioMessagingIndication");
        }
    }

    public void newBroadcastSms(byte[] data) {
        Log.d(TAG, "newBroadcastSms");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newBroadcastSms(RadioIndicationType.UNSOLICITED, data);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to newBroadcastSms indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioMessagingIndication");
        }
    }

    public void newSms(byte[] pdu) {
        Log.d(TAG, "newSms");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newSms(RadioIndicationType.UNSOLICITED, pdu);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to newSms indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioMessagingIndication");
        }
    }

    public void newSmsOnSim(int recordNumber) {
        Log.d(TAG, "newSmsOnSim");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newSmsOnSim(
                        RadioIndicationType.UNSOLICITED, recordNumber);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to newSmsOnSim indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioMessagingIndication");
        }
    }

    public void newSmsStatusReport(byte[] pdu) {
        Log.d(TAG, "newSmsStatusReport");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.newSmsStatusReport(RadioIndicationType.UNSOLICITED, pdu);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to newSmsStatusReport indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioMessagingIndication");
        }
    }

    public void simSmsStorageFull() {
        Log.d(TAG, "simSmsStorageFull");

        if (mRadioMessagingIndication != null) {
            try {
                mRadioMessagingIndication.simSmsStorageFull(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to simSmsStorageFull indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioMessagingIndication");
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioMessaging.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioMessaging.VERSION;
    }

    public Set<Integer> getGsmBroadcastConfigSet() {
        synchronized (mGsmBroadcastConfigSet) {
            Log.d(TAG, "getBroadcastConfigSet. " + mGsmBroadcastConfigSet);
            return mGsmBroadcastConfigSet;
        }
    }

    public Set<Integer> getCdmaBroadcastConfigSet() {
        synchronized (mCdmaBroadcastConfigSet) {
            Log.d(TAG, "getBroadcastConfigSet. " + mCdmaBroadcastConfigSet);
            return mCdmaBroadcastConfigSet;
        }
    }
}
