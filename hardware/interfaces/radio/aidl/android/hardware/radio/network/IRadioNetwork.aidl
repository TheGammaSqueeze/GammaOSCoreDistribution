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

package android.hardware.radio.network;

import android.hardware.radio.AccessNetwork;
import android.hardware.radio.network.CdmaRoamingType;
import android.hardware.radio.network.IRadioNetworkIndication;
import android.hardware.radio.network.IRadioNetworkResponse;
import android.hardware.radio.network.IndicationFilter;
import android.hardware.radio.network.NetworkScanRequest;
import android.hardware.radio.network.NrDualConnectivityState;
import android.hardware.radio.network.RadioAccessSpecifier;
import android.hardware.radio.network.RadioBandMode;
import android.hardware.radio.network.SignalThresholdInfo;
import android.hardware.radio.network.UsageSetting;

/**
 * This interface is used by telephony and telecom to talk to cellular radio for network APIs.
 * All the functions have minimum one parameter:
 * serial: which corresponds to serial no. of request. Serial numbers must only be memorized for the
 * duration of a method call. If clients provide colliding serials (including passing the same
 * serial to different methods), multiple responses (one for each method call) must still be served.
 * setResponseFunctions must work with IRadioNetworkResponse and IRadioNetworkIndication.
 */
@VintfStability
oneway interface IRadioNetwork {
    /**
     * Requests bitmap representing the currently allowed network types.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getAllowedNetworkTypesBitmapResponse()
     */
    void getAllowedNetworkTypesBitmap(in int serial);

    /**
     * Get the list of band modes supported by RF.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getAvailableBandModesResponse()
     */
    void getAvailableBandModes(in int serial);

    /**
     * Scans for available networks
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getAvailableNetworksResponse()
     */
    void getAvailableNetworks(in int serial);

    /**
     * Get all the barring info for the current camped cell applicable to the current user.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getBarringInfoResponse()
     */
    void getBarringInfo(in int serial);

    /**
     * Request the actual setting of the roaming preferences in CDMA in the modem
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getCdmaRoamingPreferenceResponse()
     */
    void getCdmaRoamingPreference(in int serial);

    /**
     * Request all of the current cell information known to the radio. The radio must return a list
     * of all current cells, including the neighboring cells. If for a particular cell information
     * isn't known then the appropriate unknown value will be returned.
     * This does not cause or change the rate of unsolicited cellInfoList().
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getCellInfoListResponse()
     */
    void getCellInfoList(in int serial);

    /**
     * Request current data registration state.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getDataRegistrationStateResponse()
     */
    void getDataRegistrationState(in int serial);

    /**
     * Request current IMS registration state
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getImsRegistrationStateResponse()
     */
    void getImsRegistrationState(in int serial);

    /**
     * Query current network selection mode
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getNetworkSelectionModeResponse()
     */
    void getNetworkSelectionMode(in int serial);

    /**
     * Request current operator ONS or EONS
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getOperatorResponse()
     */
    void getOperator(in int serial);

    /**
     * Requests current signal strength and associated information. Must succeed if radio is on.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getSignalStrengthResponse()
     */
    void getSignalStrength(in int serial);

    /**
     * Get which bands the modem's background scan is acting on.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getSystemSelectionChannelsResponse()
     */
    void getSystemSelectionChannels(in int serial);

    /**
     * Query the radio technology type (3GPP/3GPP2) used for voice. Query is valid only
     * when radio state is not RADIO_STATE_UNAVAILABLE
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getVoiceRadioTechnologyResponse()
     */
    void getVoiceRadioTechnology(in int serial);

    /**
     * Request current voice registration state.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.getVoiceRegistrationStateResponse()
     */
    void getVoiceRegistrationState(in int serial);

    /**
     * Is E-UTRA-NR Dual Connectivity enabled
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.isNrDualConnectivityEnabledResponse()
     */
    void isNrDualConnectivityEnabled(in int serial);

    /**
     * When response type received from a radio indication or radio response is
     * RadioIndicationType:UNSOLICITED_ACK_EXP or RadioResponseType:SOLICITED_ACK_EXP respectively,
     * acknowledge the receipt of those messages by sending responseAcknowledgement().
     */
    void responseAcknowledgement();

    /**
     * Requests to set the network type for searching and registering. Instruct the radio to
     * *only* accept the types of network provided. In case of an emergency call, the modem is
     * authorized to bypass this restriction.
     *
     * @param serial Serial number of request.
     * @param networkTypeBitmap a 32-bit bearer bitmap of RadioAccessFamily
     *
     * Response function is IRadioNetworkResponse.setAllowedNetworkTypesBitmapResponse()
     */
    void setAllowedNetworkTypesBitmap(in int serial, in int networkTypeBitmap);

    /**
     * Assign a specified band for RF configuration.
     *
     * @param serial Serial number of request.
     * @param mode RadioBandMode
     *
     * Response function is IRadioNetworkResponse.setBandModeResponse()
     */
    void setBandMode(in int serial, in RadioBandMode mode);

    /**
     * Change call barring facility password
     *
     * @param serial Serial number of request.
     * @param facility facility string code from TS 27.007 7.4 (eg "AO" for BAOC)
     * @param oldPassword old password
     * @param newPassword new password
     *
     * Response function is IRadioNetworkResponse.setBarringPasswordResponse()
     */
    void setBarringPassword(
            in int serial, in String facility, in String oldPassword, in String newPassword);

    /**
     * Request to set the roaming preferences in CDMA
     *
     * @param serial Serial number of request.
     * @param type CdmaRoamingType defined in types.hal
     *
     * Response function is IRadioNetworkResponse.setCdmaRoamingPreferenceResponse()
     */
    void setCdmaRoamingPreference(in int serial, in CdmaRoamingType type);

    /**
     * Sets the minimum time between when unsolicited cellInfoList() must be invoked.
     * A value of 0, means invoke cellInfoList() when any of the reported information changes.
     * Setting the value to INT_MAX(0x7fffffff) means never issue a unsolicited cellInfoList().
     *
     * @param serial Serial number of request.
     * @param rate minimum time in milliseconds to indicate time between unsolicited cellInfoList()
     *
     * Response function is IRadioNetworkResponse.setCellInfoListRateResponse()
     */
    void setCellInfoListRate(in int serial, in int rate);

    /**
     * Sets the indication filter. Prevents the reporting of specified unsolicited indications from
     * the radio. This is used for power saving in instances when those indications are not needed.
     * If unset, defaults to IndicationFilter:ALL.
     *
     * @param serial Serial number of request.
     * @param indicationFilter 32-bit bitmap of IndicationFilter. Bits set to 1 indicate the
     *        indications are enabled. See IndicationFilter for the definition of each bit.
     *
     * Response function is IRadioNetworkResponse.setIndicationFilterResponse()
     */
    void setIndicationFilter(in int serial, in int indicationFilter);

    /**
     * Sets the link capacity reporting criteria. The resulting reporting criteria are the AND of
     * all the supplied criteria. Note that reporting criteria must be individually set for each
     * RAN. If unset, reporting criteria for that RAN are implementation-defined.
     *
     * @param serial Serial number of request.
     * @param hysteresisMs A hysteresis time in milliseconds to prevent flapping. A value of 0
     *        disables hysteresis.
     * @param hysteresisDlKbps An interval in kbps defining the required magnitude change between DL
     *        reports. hysteresisDlKbps must be smaller than the smallest threshold delta. A value
     *        of 0 disables hysteresis.
     * @param hysteresisUlKbps An interval in kbps defining the required magnitude change between UL
     *        reports. hysteresisUlKbps must be smaller than the smallest threshold delta. A value
     *        of 0 disables hysteresis.
     * @param thresholdsDownlinkKbps A vector of trigger thresholds in kbps for downlink reports. A
     *        vector size of 0 disables the use of DL thresholds for reporting.
     * @param thresholdsUplinkKbps A vector of trigger thresholds in kbps for uplink reports. A
     *        vector size of 0 disables the use of UL thresholds for reporting.
     * @param accessNetwork The type of network for which to apply these thresholds.
     *
     * Response function is IRadioNetworkResponse.setLinkCapacityReportingCriteriaResponse().
     */
    void setLinkCapacityReportingCriteria(in int serial, in int hysteresisMs,
            in int hysteresisDlKbps, in int hysteresisUlKbps, in int[] thresholdsDownlinkKbps,
            in int[] thresholdsUplinkKbps, in AccessNetwork accessNetwork);

    /**
     * Enables/disables network state change notifications due to changes in LAC and/or CID (for
     * GSM) or BID/SID/NID/latitude/longitude (for CDMA). Basically +CREG=2 vs. +CREG=1 (TS 27.007).
     * The Radio implementation must default to "updates enabled" when the screen is on and
     * "updates disabled" when the screen is off.
     *
     * @param serial Serial number of request.
     * @param enable true=updates enabled (+CREG=2), false=updates disabled (+CREG=1)
     *
     * Response function is IRadioNetworkResponse.setLocationUpdatesResponse()
     */
    void setLocationUpdates(in int serial, in boolean enable);

    /**
     * Specify that the network must be selected automatically.
     * This request must not respond until the new operator is selected and registered.
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.setNetworkSelectionModeAutomaticResponse()
     */
    void setNetworkSelectionModeAutomatic(in int serial);

    /**
     * Manually select a specified network. This request must not respond until the new operator is
     * selected and registered. Per TS 23.122, the RAN is just the initial suggested value.
     * If registration fails, the RAN is not available afterwards, or the RAN is not within the
     * network types specified by IRadioNetwork::setAllowedNetworkTypeBitmap, then the modem will
     * need to select the next best RAN for network registration.
     *
     * @param serial Serial number of request.
     * @param operatorNumeric String specifying MCCMNC of network to select (eg "310170").
     * @param ran Initial suggested access network type. If value is UNKNOWN, the modem will select
     *        the next best RAN for network registration.
     *
     * Response function is IRadioNetworkResponse.setNetworkSelectionModeManualResponse()
     */
    void setNetworkSelectionModeManual(
            in int serial, in String operatorNumeric, in AccessNetwork ran);

    /**
     * Enable or disable E-UTRA-NR dual connectivity. If disabled then UE will not connect
     * to secondary carrier.
     *
     * @param serial Serial number of request.
     * @param nrDualConnectivityState expected NR dual connectivity state.
     *        1: Enable NR dual connectivity {NrDualConnectivityState:ENABLE}
     *        2: Disable NR dual connectivity {NrDualConnectivityState:DISABLE}
     *        3: Disable NR dual connectivity and force secondary cell to be released
     *           {NrDualConnectivityState:DISABLE_IMMEDIATE}
     *
     * Response function is IRadioNetworkResponse.setNrDualConnectivityStateResponse()
     */
    void setNrDualConnectivityState(
            in int serial, in NrDualConnectivityState nrDualConnectivityState);

    /**
     * Set response functions for network radio requests and indications.
     *
     * @param radioNetworkResponse Object containing response functions
     * @param radioNetworkIndication Object containing radio indications
     */
    void setResponseFunctions(in IRadioNetworkResponse radioNetworkResponse,
            in IRadioNetworkIndication radioNetworkIndication);

    /**
     * Sets or clears the signal strength reporting criteria for multiple RANs in one request.
     *
     * The reporting criteria are set individually for each combination of RAN and measurement type.
     * For each RAN type, if no reporting criteria are set, then the reporting of SignalStrength for
     * that RAN is implementation-defined. If any criteria are supplied for a RAN type, then
     * SignalStrength is only reported as specified by those criteria. For any RAN types not defined
     * by this HAL, reporting is implementation-defined.
     *
     * @param serial Serial number of request.
     * @param signalThresholdInfos Collection of SignalThresholdInfo specifying the reporting
     *        criteria. See SignalThresholdInfo for details.
     *
     * Response function is IRadioNetworkResponse.setSignalStrengthReportingCriteriaResponse()
     */
    void setSignalStrengthReportingCriteria(
            in int serial, in SignalThresholdInfo[] signalThresholdInfos);

    /**
     * Enables/disables supplementary service related notifications from the network.
     * Notifications are reported via unsolSuppSvcNotification().
     *
     * @param serial Serial number of request.
     * @param enable true = notifications enabled, false = notifications disabled.
     *
     * Response function is IRadioNetworkResponse.setSuppServiceNotificationsResponse()
     */
    void setSuppServiceNotifications(in int serial, in boolean enable);

    /**
     * Specify which bands modem's background scan must act on. If specifyChannels is true, it only
     * scans bands specified in specifiers. If specifyChannels is false, it scans all bands. For
     * example, CBRS is only on LTE band 48. By specifying this band, modem saves more power.
     *
     * @param serial Serial number of request.
     * @param specifyChannels whether to scan bands defined in specifiers.
     * @param specifiers which bands to scan. Only used if specifyChannels is true.
     *
     * Response function is IRadioNetworkResponse.setSystemSelectionChannelsResponse()
     */
    void setSystemSelectionChannels(
            in int serial, in boolean specifyChannels, in RadioAccessSpecifier[] specifiers);

    /**
     * Starts a network scan.
     *
     * @param serial Serial number of request.
     * @param request Defines the radio networks/bands/channels which need to be scanned.
     *
     * Response function is IRadioNetworkResponse.startNetworkScanResponse()
     */
    void startNetworkScan(in int serial, in NetworkScanRequest request);

    /**
     * Stops ongoing network scan
     *
     * @param serial Serial number of request.
     *
     * Response function is IRadioNetworkResponse.stopNetworkScanResponse()
     */
    void stopNetworkScan(in int serial);

    /**
     * Requests that network personalization be deactivated
     *
     * @param serial Serial number of request.
     * @param netPin Network depersonlization code
     *
     * Response function is IRadioNetworkResponse.supplyNetworkDepersonalizationResponse()
     */
    void supplyNetworkDepersonalization(in int serial, in String netPin);

    /**
     * Set the UE usage setting for data/voice centric usage.
     *
     * <p>Sets the usage setting in accordance with 3gpp 24.301 sec 4.3 and 3gpp 24.501 sec 4.3.
     * <p>This value must be independently preserved for each SIM; (setting the value is not a
     * "global" override).
     *
     * @param serial Serial number of request.
     * @param usageSetting the usage setting for the current SIM.
     */
    oneway void setUsageSetting(in int serial, in UsageSetting usageSetting);

    /**
     * Get the UE usage setting for data/voice centric usage.
     *
     * <p>Gets the usage setting in accordance with 3gpp 24.301 sec 4.3 and 3gpp 24.501 sec 4.3.
     *
     * @param serial Serial number of request.
     */
    oneway void getUsageSetting(in int serial);
}
