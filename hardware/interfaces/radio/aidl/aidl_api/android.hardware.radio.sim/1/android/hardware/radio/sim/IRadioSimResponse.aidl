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
///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package android.hardware.radio.sim;
@VintfStability
interface IRadioSimResponse {
  oneway void acknowledgeRequest(in int serial);
  oneway void areUiccApplicationsEnabledResponse(in android.hardware.radio.RadioResponseInfo info, in boolean enabled);
  oneway void changeIccPin2ForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int remainingRetries);
  oneway void changeIccPinForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int remainingRetries);
  oneway void enableUiccApplicationsResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void getAllowedCarriersResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.CarrierRestrictions carriers, in android.hardware.radio.sim.SimLockMultiSimPolicy multiSimPolicy);
  oneway void getCdmaSubscriptionResponse(in android.hardware.radio.RadioResponseInfo info, in String mdn, in String hSid, in String hNid, in String min, in String prl);
  oneway void getCdmaSubscriptionSourceResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.CdmaSubscriptionSource source);
  oneway void getFacilityLockForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int response);
  oneway void getIccCardStatusResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.CardStatus cardStatus);
  oneway void getImsiForAppResponse(in android.hardware.radio.RadioResponseInfo info, in String imsi);
  oneway void getSimPhonebookCapacityResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.PhonebookCapacity capacity);
  oneway void getSimPhonebookRecordsResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void iccCloseLogicalChannelResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void iccIoForAppResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.IccIoResult iccIo);
  oneway void iccOpenLogicalChannelResponse(in android.hardware.radio.RadioResponseInfo info, in int channelId, in byte[] selectResponse);
  oneway void iccTransmitApduBasicChannelResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.IccIoResult result);
  oneway void iccTransmitApduLogicalChannelResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.IccIoResult result);
  oneway void reportStkServiceIsRunningResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void requestIccSimAuthenticationResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.IccIoResult result);
  oneway void sendEnvelopeResponse(in android.hardware.radio.RadioResponseInfo info, in String commandResponse);
  oneway void sendEnvelopeWithStatusResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.IccIoResult iccIo);
  oneway void sendTerminalResponseToSimResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void setAllowedCarriersResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void setCarrierInfoForImsiEncryptionResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void setCdmaSubscriptionSourceResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void setFacilityLockForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int retry);
  oneway void setSimCardPowerResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void setUiccSubscriptionResponse(in android.hardware.radio.RadioResponseInfo info);
  oneway void supplyIccPin2ForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int remainingRetries);
  oneway void supplyIccPinForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int remainingRetries);
  oneway void supplyIccPuk2ForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int remainingRetries);
  oneway void supplyIccPukForAppResponse(in android.hardware.radio.RadioResponseInfo info, in int remainingRetries);
  oneway void supplySimDepersonalizationResponse(in android.hardware.radio.RadioResponseInfo info, in android.hardware.radio.sim.PersoSubstate persoType, in int remainingRetries);
  oneway void updateSimPhonebookRecordsResponse(in android.hardware.radio.RadioResponseInfo info, in int updatedRecordIndex);
}
