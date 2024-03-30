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
import android.hardware.radio.voice.IRadioVoice;
import android.hardware.radio.voice.IRadioVoiceIndication;
import android.hardware.radio.voice.IRadioVoiceResponse;
import android.os.RemoteException;
import android.util.Log;

public class IRadioVoiceImpl extends IRadioVoice.Stub {
    private static final String TAG = "MRVOICE";

    private final MockModemService mService;
    private IRadioVoiceResponse mRadioVoiceResponse;
    private IRadioVoiceIndication mRadioVoiceIndication;

    public IRadioVoiceImpl(MockModemService service) {
        Log.d(TAG, "Instantiated");

        this.mService = service;
    }

    // Implementation of IRadioVoice functions
    @Override
    public void setResponseFunctions(
            IRadioVoiceResponse radioVoiceResponse, IRadioVoiceIndication radioVoiceIndication) {
        Log.d(TAG, "setResponseFunctions");
        mRadioVoiceResponse = radioVoiceResponse;
        mRadioVoiceIndication = radioVoiceIndication;
        mService.countDownLatch(MockModemService.LATCH_RADIO_INTERFACES_READY);
    }

    @Override
    public void acceptCall(int serial) {
        Log.d(TAG, "acceptCall");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.acceptCallResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to acceptCall from AIDL. Exception" + ex);
        }
    }

    @Override
    public void cancelPendingUssd(int serial) {
        Log.d(TAG, "cancelPendingUssd");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.cancelPendingUssdResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to cancelPendingUssd from AIDL. Exception" + ex);
        }
    }

    @Override
    public void conference(int serial) {
        Log.d(TAG, "conference");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.conferenceResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to conference from AIDL. Exception" + ex);
        }
    }

    @Override
    public void dial(int serial, android.hardware.radio.voice.Dial dialInfo) {
        Log.d(TAG, "dial");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.dialResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to dial from AIDL. Exception" + ex);
        }
    }

    @Override
    public void emergencyDial(
            int serial,
            android.hardware.radio.voice.Dial dialInfo,
            int categories,
            String[] urns,
            int routing,
            boolean hasKnownUserIntentEmergency,
            boolean isTesting) {
        Log.d(TAG, "emergencyDial");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.emergencyDialResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to emergencyDial from AIDL. Exception" + ex);
        }
    }

    @Override
    public void exitEmergencyCallbackMode(int serial) {
        Log.d(TAG, "exitEmergencyCallbackMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.exitEmergencyCallbackModeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to exitEmergencyCallbackMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void explicitCallTransfer(int serial) {
        Log.d(TAG, "explicitCallTransfer");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.explicitCallTransferResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to explicitCallTransfer from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCallForwardStatus(
            int serial, android.hardware.radio.voice.CallForwardInfo callInfo) {
        Log.d(TAG, "getCallForwardStatus");

        android.hardware.radio.voice.CallForwardInfo[] callForwardInfos =
                new android.hardware.radio.voice.CallForwardInfo[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getCallForwardStatusResponse(rsp, callForwardInfos);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCallForwardStatus from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCallWaiting(int serial, int serviceClass) {
        Log.d(TAG, "getCallWaiting");

        boolean enable = false;
        int rspServiceClass = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getCallWaitingResponse(rsp, enable, rspServiceClass);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCallWaiting from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getClip(int serial) {
        Log.d(TAG, "getClip");

        int status = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getClipResponse(rsp, status);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getClip from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getClir(int serial) {
        Log.d(TAG, "getClir");

        int n = 0;
        int m = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getClirResponse(rsp, n, m);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getClir from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getCurrentCalls(int serial) {
        Log.d(TAG, "getCurrentCalls");

        android.hardware.radio.voice.Call[] calls = new android.hardware.radio.voice.Call[0];
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getCurrentCallsResponse(rsp, calls);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getCurrentCalls from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getLastCallFailCause(int serial) {
        Log.d(TAG, "getLastCallFailCause");

        android.hardware.radio.voice.LastCallFailCauseInfo failCauseinfo =
                new android.hardware.radio.voice.LastCallFailCauseInfo();
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getLastCallFailCauseResponse(rsp, failCauseinfo);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getLastCallFailCause from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getMute(int serial) {
        Log.d(TAG, "getMute");

        boolean enable = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getMuteResponse(rsp, enable);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getMute from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getPreferredVoicePrivacy(int serial) {
        Log.d(TAG, "getPreferredVoicePrivacy");

        boolean enable = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getPreferredVoicePrivacyResponse(rsp, enable);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getPreferredVoicePrivacy from AIDL. Exception" + ex);
        }
    }

    @Override
    public void getTtyMode(int serial) {
        Log.d(TAG, "getTtyMode");

        int mode = 0;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.getTtyModeResponse(rsp, mode);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to getTtyMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void handleStkCallSetupRequestFromSim(int serial, boolean accept) {
        Log.d(TAG, "handleStkCallSetupRequestFromSim");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.handleStkCallSetupRequestFromSimResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to handleStkCallSetupRequestFromSim from AIDL. Exception" + ex);
        }
    }

    @Override
    public void hangup(int serial, int gsmIndex) {
        Log.d(TAG, "hangup");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.hangupConnectionResponse(rsp); // TODO: no hangupResponse
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to hangup from AIDL. Exception" + ex);
        }
    }

    @Override
    public void hangupForegroundResumeBackground(int serial) {
        Log.d(TAG, "hangupForegroundResumeBackground");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.hangupForegroundResumeBackgroundResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to hangupForegroundResumeBackground from AIDL. Exception" + ex);
        }
    }

    @Override
    public void hangupWaitingOrBackground(int serial) {
        Log.d(TAG, "hangupWaitingOrBackground");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.hangupWaitingOrBackgroundResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to hangupWaitingOrBackground from AIDL. Exception" + ex);
        }
    }

    @Override
    public void isVoNrEnabled(int serial) {
        Log.d(TAG, "isVoNrEnabled");

        boolean enable = false;
        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.isVoNrEnabledResponse(rsp, enable);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to isVoNrEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void rejectCall(int serial) {
        Log.d(TAG, "rejectCall");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.rejectCallResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to rejectCall from AIDL. Exception" + ex);
        }
    }

    @Override
    public void responseAcknowledgement() {
        Log.d(TAG, "responseAcknowledgement");
        // TODO
    }

    @Override
    public void sendBurstDtmf(int serial, String dtmf, int on, int off) {
        Log.d(TAG, "sendBurstDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendBurstDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendBurstDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendCdmaFeatureCode(int serial, String featureCode) {
        Log.d(TAG, "sendCdmaFeatureCode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendCdmaFeatureCodeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendCdmaFeatureCode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendDtmf(int serial, String s) {
        Log.d(TAG, "sendDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void sendUssd(int serial, String ussd) {
        Log.d(TAG, "sendUssd");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.sendUssdResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to sendUssd from AIDL. Exception" + ex);
        }
    }

    @Override
    public void separateConnection(int serial, int gsmIndex) {
        Log.d(TAG, "separateConnection");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.separateConnectionResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to separateConnection from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCallForward(int serial, android.hardware.radio.voice.CallForwardInfo callInfo) {
        Log.d(TAG, "setCallForward");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setCallForwardResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCallForward from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setCallWaiting(int serial, boolean enable, int serviceClass) {
        Log.d(TAG, "setCallWaiting");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setCallWaitingResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setCallWaiting from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setClir(int serial, int status) {
        Log.d(TAG, "setClir");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setClirResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setClir from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setMute(int serial, boolean enable) {
        Log.d(TAG, "setMute");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setMuteResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setMute from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setPreferredVoicePrivacy(int serial, boolean enable) {
        Log.d(TAG, "setPreferredVoicePrivacy");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setPreferredVoicePrivacyResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setPreferredVoicePrivacy from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setTtyMode(int serial, int mode) {
        Log.d(TAG, "setTtyMode");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setTtyModeResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setTtyMode from AIDL. Exception" + ex);
        }
    }

    @Override
    public void setVoNrEnabled(int serial, boolean enable) {
        Log.d(TAG, "setVoNrEnabled");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.setVoNrEnabledResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to setVoNrEnabled from AIDL. Exception" + ex);
        }
    }

    @Override
    public void startDtmf(int serial, String s) {
        Log.d(TAG, "startDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.startDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to startDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void stopDtmf(int serial) {
        Log.d(TAG, "stopDtmf");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.stopDtmfResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to stopDtmf from AIDL. Exception" + ex);
        }
    }

    @Override
    public void switchWaitingOrHoldingAndActive(int serial) {
        Log.d(TAG, "switchWaitingOrHoldingAndActive");

        RadioResponseInfo rsp = mService.makeSolRsp(serial, RadioError.REQUEST_NOT_SUPPORTED);
        try {
            mRadioVoiceResponse.switchWaitingOrHoldingAndActiveResponse(rsp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to switchWaitingOrHoldingAndActive from AIDL. Exception" + ex);
        }
    }

    public void callRing(boolean isGsm, android.hardware.radio.voice.CdmaSignalInfoRecord record) {
        Log.d(TAG, "callRing");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.callRing(RadioIndicationType.UNSOLICITED, isGsm, record);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to callRing indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void callStateChanged() {
        Log.d(TAG, "callStateChanged");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.callStateChanged(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to callStateChanged indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void cdmaCallWaiting(android.hardware.radio.voice.CdmaCallWaiting callWaitingRecord) {
        Log.d(TAG, "cdmaCallWaiting");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.cdmaCallWaiting(
                        RadioIndicationType.UNSOLICITED, callWaitingRecord);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to cdmaCallWaiting indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void cdmaInfoRec(android.hardware.radio.voice.CdmaInformationRecord[] records) {
        Log.d(TAG, "cdmaInfoRec");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.cdmaInfoRec(RadioIndicationType.UNSOLICITED, records);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to cdmaInfoRec indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void cdmaOtaProvisionStatus(int status) {
        Log.d(TAG, "cdmaOtaProvisionStatus");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.cdmaOtaProvisionStatus(
                        RadioIndicationType.UNSOLICITED, status);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to cdmaOtaProvisionStatus indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void currentEmergencyNumberList(
            android.hardware.radio.voice.EmergencyNumber[] emergencyNumberList) {
        Log.d(TAG, "currentEmergencyNumberList");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.currentEmergencyNumberList(
                        RadioIndicationType.UNSOLICITED, emergencyNumberList);
            } catch (RemoteException ex) {
                Log.e(
                        TAG,
                        "Failed to currentEmergencyNumberList indication from AIDL. Exception"
                                + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void enterEmergencyCallbackMode() {
        Log.d(TAG, "enterEmergencyCallbackMode");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.enterEmergencyCallbackMode(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(
                        TAG,
                        "Failed to enterEmergencyCallbackMode indication from AIDL. Exception"
                                + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void exitEmergencyCallbackMode() {
        Log.d(TAG, "exitEmergencyCallbackMode");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.exitEmergencyCallbackMode(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(
                        TAG,
                        "Failed to exitEmergencyCallbackMode indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void indicateRingbackTone(boolean start) {
        Log.d(TAG, "indicateRingbackTone");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.indicateRingbackTone(RadioIndicationType.UNSOLICITED, start);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to indicateRingbackTone indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void onSupplementaryServiceIndication(
            android.hardware.radio.voice.StkCcUnsolSsResult ss) {
        Log.d(TAG, "onSupplementaryServiceIndication");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.onSupplementaryServiceIndication(
                        RadioIndicationType.UNSOLICITED, ss);
            } catch (RemoteException ex) {
                Log.e(
                        TAG,
                        "Failed to onSupplementaryServiceIndication indication from AIDL. Exception"
                                + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void onUssd(int modeType, String msg) {
        Log.d(TAG, "onUssd");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.onUssd(RadioIndicationType.UNSOLICITED, modeType, msg);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to onUssd indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void resendIncallMute() {
        Log.d(TAG, "resendIncallMute");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.resendIncallMute(RadioIndicationType.UNSOLICITED);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to resendIncallMute indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void srvccStateNotify(int state) {
        Log.d(TAG, "srvccStateNotify");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.srvccStateNotify(RadioIndicationType.UNSOLICITED, state);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to srvccStateNotify indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void stkCallControlAlphaNotify(String alpha) {
        Log.d(TAG, "stkCallControlAlphaNotify");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.stkCallControlAlphaNotify(
                        RadioIndicationType.UNSOLICITED, alpha);
            } catch (RemoteException ex) {
                Log.e(
                        TAG,
                        "Failed to stkCallControlAlphaNotify indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    public void stkCallSetup(long timeout) {
        Log.d(TAG, "stkCallSetup");

        if (mRadioVoiceIndication != null) {
            try {
                mRadioVoiceIndication.stkCallSetup(RadioIndicationType.UNSOLICITED, timeout);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to stkCallSetup indication from AIDL. Exception" + ex);
            }
        } else {
            Log.e(TAG, "null mRadioVoiceIndication");
        }
    }

    @Override
    public String getInterfaceHash() {
        return IRadioVoice.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioVoice.VERSION;
    }
}
