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

#include <aidl/android/hardware/radio/config/IRadioConfig.h>
#include <aidl/android/hardware/radio/voice/EmergencyServiceCategory.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>

#include "radio_voice_utils.h"

#define ASSERT_OK(ret) ASSERT_TRUE(ret.isOk())

void RadioVoiceTest::SetUp() {
    std::string serviceName = GetParam();

    if (!isServiceValidForDeviceConfiguration(serviceName)) {
        ALOGI("Skipped the test due to device configuration.");
        GTEST_SKIP();
    }

    radio_voice = IRadioVoice::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService(GetParam().c_str())));
    ASSERT_NE(nullptr, radio_voice.get());

    radioRsp_voice = ndk::SharedRefBase::make<RadioVoiceResponse>(*this);
    ASSERT_NE(nullptr, radioRsp_voice.get());

    count_ = 0;

    radioInd_voice = ndk::SharedRefBase::make<RadioVoiceIndication>(*this);
    ASSERT_NE(nullptr, radioInd_voice.get());

    radio_voice->setResponseFunctions(radioRsp_voice, radioInd_voice);

    // Assert IRadioSim exists and SIM is present before testing
    radio_sim = sim::IRadioSim::fromBinder(ndk::SpAIBinder(
            AServiceManager_waitForService("android.hardware.radio.sim.IRadioSim/slot1")));
    ASSERT_NE(nullptr, radio_sim.get());
    updateSimCardStatus();
    EXPECT_EQ(CardStatus::STATE_PRESENT, cardStatus.cardState);

    // Assert IRadioConfig exists before testing
    radio_config = config::IRadioConfig::fromBinder(ndk::SpAIBinder(
            AServiceManager_waitForService("android.hardware.radio.config.IRadioConfig/default")));
    ASSERT_NE(nullptr, radio_config.get());

    if (isDsDsEnabled() || isTsTsEnabled()) {
        radio_network = IRadioNetwork::fromBinder(ndk::SpAIBinder(AServiceManager_waitForService(
                "android.hardware.radio.network.IRadioNetwork/slot1")));
        ASSERT_NE(nullptr, radio_network.get());
        radioRsp_network = ndk::SharedRefBase::make<RadioNetworkResponse>(*this);
        radioInd_network = ndk::SharedRefBase::make<RadioNetworkIndication>(*this);
        radio_network->setResponseFunctions(radioRsp_network, radioInd_network);
    }
}

ndk::ScopedAStatus RadioVoiceTest::clearPotentialEstablishedCalls() {
    // Get the current call Id to hangup the established emergency call.
    serial = GetRandomSerialNumber();
    radio_voice->getCurrentCalls(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());

    // Hang up to disconnect the established call channels.
    for (const Call& call : radioRsp_voice->currentCalls) {
        serial = GetRandomSerialNumber();
        radio_voice->hangup(serial, call.index);
        ALOGI("Hang up to disconnect the established call channel: %d", call.index);
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        // Give some time for modem to disconnect the established call channel.
        sleep(MODEM_EMERGENCY_CALL_DISCONNECT_TIME);
    }

    // Verify there are no more current calls.
    serial = GetRandomSerialNumber();
    radio_voice->getCurrentCalls(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(0, radioRsp_voice->currentCalls.size());
    return ndk::ScopedAStatus::ok();
}

/*
 * Test IRadioVoice.emergencyDial() for the response returned.
 */
TEST_P(RadioVoiceTest, emergencyDial) {
    if (!deviceSupportsFeature(FEATURE_VOICE_CALL)) {
        ALOGI("Skipping emergencyDial because voice call is not supported in device");
        return;
    } else if (!deviceSupportsFeature(FEATURE_TELEPHONY_GSM) &&
               !deviceSupportsFeature(FEATURE_TELEPHONY_CDMA)) {
        ALOGI("Skipping emergencyDial because gsm/cdma radio is not supported in device");
        return;
    } else {
        ALOGI("Running emergencyDial because voice call is supported in device");
    }

    serial = GetRandomSerialNumber();

    Dial dialInfo;
    dialInfo.address = std::string("911");
    int32_t categories = static_cast<int32_t>(EmergencyServiceCategory::UNSPECIFIED);
    std::vector<std::string> urns = {""};
    EmergencyCallRouting routing = EmergencyCallRouting::UNKNOWN;

    ndk::ScopedAStatus res =
            radio_voice->emergencyDial(serial, dialInfo, categories, urns, routing, true, true);
    ASSERT_OK(res);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    ALOGI("emergencyDial, rspInfo.error = %s\n", toString(radioRsp_voice->rspInfo.error).c_str());

    RadioError rspEmergencyDial = radioRsp_voice->rspInfo.error;
    // In DSDS or TSTS, we only check the result if the current slot is IN_SERVICE
    // or Emergency_Only.
    if (isDsDsEnabled() || isTsTsEnabled()) {
        serial = GetRandomSerialNumber();
        radio_network->getVoiceRegistrationState(serial);
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        if (isVoiceEmergencyOnly(radioRsp_network->voiceRegResp.regState) ||
            isVoiceInService(radioRsp_network->voiceRegResp.regState)) {
            EXPECT_EQ(RadioError::NONE, rspEmergencyDial);
        }
    } else {
        EXPECT_EQ(RadioError::NONE, rspEmergencyDial);
    }

    // Give some time for modem to establish the emergency call channel.
    sleep(MODEM_EMERGENCY_CALL_ESTABLISH_TIME);

    // Disconnect all the potential established calls to prevent them affecting other tests.
    clearPotentialEstablishedCalls();
}

/*
 * Test IRadioVoice.emergencyDial() with specified service and its response returned.
 */
TEST_P(RadioVoiceTest, emergencyDial_withServices) {
    if (!deviceSupportsFeature(FEATURE_VOICE_CALL)) {
        ALOGI("Skipping emergencyDial because voice call is not supported in device");
        return;
    } else if (!deviceSupportsFeature(FEATURE_TELEPHONY_GSM) &&
               !deviceSupportsFeature(FEATURE_TELEPHONY_CDMA)) {
        ALOGI("Skipping emergencyDial because gsm/cdma radio is not supported in device");
        return;
    } else {
        ALOGI("Running emergencyDial because voice call is supported in device");
    }

    serial = GetRandomSerialNumber();

    Dial dialInfo;
    dialInfo.address = std::string("911");
    int32_t categories = static_cast<int32_t>(EmergencyServiceCategory::AMBULANCE);
    std::vector<std::string> urns = {"urn:service:sos.ambulance"};
    EmergencyCallRouting routing = EmergencyCallRouting::UNKNOWN;

    ndk::ScopedAStatus res =
            radio_voice->emergencyDial(serial, dialInfo, categories, urns, routing, true, true);
    ASSERT_OK(res);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    ALOGI("emergencyDial_withServices, rspInfo.error = %s\n",
          toString(radioRsp_voice->rspInfo.error).c_str());
    RadioError rspEmergencyDial = radioRsp_voice->rspInfo.error;

    // In DSDS or TSTS, we only check the result if the current slot is IN_SERVICE
    // or Emergency_Only.
    if (isDsDsEnabled() || isTsTsEnabled()) {
        serial = GetRandomSerialNumber();
        radio_network->getVoiceRegistrationState(serial);
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        if (isVoiceEmergencyOnly(radioRsp_network->voiceRegResp.regState) ||
            isVoiceInService(radioRsp_network->voiceRegResp.regState)) {
            EXPECT_EQ(RadioError::NONE, rspEmergencyDial);
        }
    } else {
        EXPECT_EQ(RadioError::NONE, rspEmergencyDial);
    }
    // Give some time for modem to establish the emergency call channel.
    sleep(MODEM_EMERGENCY_CALL_ESTABLISH_TIME);

    // Disconnect all the potential established calls to prevent them affecting other tests.
    clearPotentialEstablishedCalls();
}

/*
 * Test IRadioVoice.emergencyDial() with known emergency call routing and its response returned.
 */
TEST_P(RadioVoiceTest, emergencyDial_withEmergencyRouting) {
    if (!deviceSupportsFeature(FEATURE_VOICE_CALL)) {
        ALOGI("Skipping emergencyDial because voice call is not supported in device");
        return;
    } else if (!deviceSupportsFeature(FEATURE_TELEPHONY_GSM) &&
               !deviceSupportsFeature(FEATURE_TELEPHONY_CDMA)) {
        ALOGI("Skipping emergencyDial because gsm/cdma radio is not supported in device");
        return;
    } else {
        ALOGI("Running emergencyDial because voice call is supported in device");
    }

    serial = GetRandomSerialNumber();

    Dial dialInfo;
    dialInfo.address = std::string("911");
    int32_t categories = static_cast<int32_t>(EmergencyServiceCategory::UNSPECIFIED);
    std::vector<std::string> urns = {""};
    EmergencyCallRouting routing = EmergencyCallRouting::EMERGENCY;

    ndk::ScopedAStatus res =
            radio_voice->emergencyDial(serial, dialInfo, categories, urns, routing, true, true);
    ASSERT_OK(res);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    ALOGI("emergencyDial_withEmergencyRouting, rspInfo.error = %s\n",
          toString(radioRsp_voice->rspInfo.error).c_str());
    RadioError rspEmergencyDial = radioRsp_voice->rspInfo.error;

    // In DSDS or TSTS, we only check the result if the current slot is IN_SERVICE
    // or Emergency_Only.
    if (isDsDsEnabled() || isTsTsEnabled()) {
        serial = GetRandomSerialNumber();
        radio_network->getVoiceRegistrationState(serial);
        EXPECT_EQ(std::cv_status::no_timeout, wait());
        if (isVoiceEmergencyOnly(radioRsp_network->voiceRegResp.regState) ||
            isVoiceInService(radioRsp_network->voiceRegResp.regState)) {
            EXPECT_EQ(RadioError::NONE, rspEmergencyDial);
        }
    } else {
        EXPECT_EQ(RadioError::NONE, rspEmergencyDial);
    }

    // Give some time for modem to establish the emergency call channel.
    sleep(MODEM_EMERGENCY_CALL_ESTABLISH_TIME);

    // Disconnect all the potential established calls to prevent them affecting other tests.
    clearPotentialEstablishedCalls();
}

/*
 * Test IRadioVoice.getCurrentCalls() for the response returned.
 */
TEST_P(RadioVoiceTest, getCurrentCalls) {
    serial = GetRandomSerialNumber();
    radio_voice->getCurrentCalls(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);
    EXPECT_EQ(RadioError::NONE, radioRsp_voice->rspInfo.error);
}

/*
 * Test IRadioVoice.getClir() for the response returned.
 */
TEST_P(RadioVoiceTest, getClir) {
    serial = GetRandomSerialNumber();

    radio_voice->getClir(serial);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error, {RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
}

/*
 * Test IRadioVoice.setClir() for the response returned.
 */
TEST_P(RadioVoiceTest, setClir) {
    serial = GetRandomSerialNumber();
    int32_t status = 1;

    radio_voice->setClir(serial, status);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        EXPECT_EQ(RadioError::NONE, radioRsp_voice->rspInfo.error);
    }
}

/*
 * Test IRadioVoice.getClip() for the response returned.
 */
TEST_P(RadioVoiceTest, getClip) {
    serial = GetRandomSerialNumber();

    radio_voice->getClip(serial);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error, {RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
}

/*
 * Test IRadioVoice.getTtyMode() for the response returned.
 */
TEST_P(RadioVoiceTest, getTtyMode) {
    LOG(DEBUG) << "getTtyMode";
    serial = GetRandomSerialNumber();

    radio_voice->getTtyMode(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        EXPECT_EQ(RadioError::NONE, radioRsp_voice->rspInfo.error);
    }
    LOG(DEBUG) << "getTtyMode finished";
}

/*
 * Test IRadioVoice.setTtyMode() for the response returned.
 */
TEST_P(RadioVoiceTest, setTtyMode) {
    LOG(DEBUG) << "setTtyMode";
    serial = GetRandomSerialNumber();

    radio_voice->setTtyMode(serial, TtyMode::OFF);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        EXPECT_EQ(RadioError::NONE, radioRsp_voice->rspInfo.error);
    }
    LOG(DEBUG) << "setTtyMode finished";
}

/*
 * Test IRadioVoice.setPreferredVoicePrivacy() for the response returned.
 */
TEST_P(RadioVoiceTest, setPreferredVoicePrivacy) {
    LOG(DEBUG) << "setPreferredVoicePrivacy";
    serial = GetRandomSerialNumber();

    radio_voice->setPreferredVoicePrivacy(serial, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::NONE, RadioError::REQUEST_NOT_SUPPORTED}));
    }
    LOG(DEBUG) << "setPreferredVoicePrivacy finished";
}

/*
 * Test IRadioVoice.getPreferredVoicePrivacy() for the response returned.
 */
TEST_P(RadioVoiceTest, getPreferredVoicePrivacy) {
    LOG(DEBUG) << "getPreferredVoicePrivacy";
    serial = GetRandomSerialNumber();

    radio_voice->getPreferredVoicePrivacy(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::NONE, RadioError::REQUEST_NOT_SUPPORTED}));
    }
    LOG(DEBUG) << "getPreferredVoicePrivacy finished";
}

/*
 * Test IRadioVoice.exitEmergencyCallbackMode() for the response returned.
 */
TEST_P(RadioVoiceTest, exitEmergencyCallbackMode) {
    LOG(DEBUG) << "exitEmergencyCallbackMode";
    serial = GetRandomSerialNumber();

    radio_voice->exitEmergencyCallbackMode(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::NONE, RadioError::REQUEST_NOT_SUPPORTED, RadioError::SIM_ABSENT}));
    }
    LOG(DEBUG) << "exitEmergencyCallbackMode finished";
}

/*
 * Test IRadioVoice.handleStkCallSetupRequestFromSim() for the response returned.
 */
TEST_P(RadioVoiceTest, handleStkCallSetupRequestFromSim) {
    LOG(DEBUG) << "handleStkCallSetupRequestFromSim";
    serial = GetRandomSerialNumber();
    bool accept = false;

    radio_voice->handleStkCallSetupRequestFromSim(serial, accept);

    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::NONE, RadioError::INVALID_ARGUMENTS,
                                      RadioError::MODEM_ERR, RadioError::SIM_ABSENT},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "handleStkCallSetupRequestFromSim finished";
}

/*
 * Test IRadioVoice.dial() for the response returned.
 */
TEST_P(RadioVoiceTest, dial) {
    LOG(DEBUG) << "dial";
    serial = GetRandomSerialNumber();

    Dial dialInfo;
    memset(&dialInfo, 0, sizeof(dialInfo));
    dialInfo.address = std::string("123456789");

    radio_voice->dial(serial, dialInfo);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::CANCELLED, RadioError::DEVICE_IN_USE, RadioError::FDN_CHECK_FAILURE,
                 RadioError::INVALID_ARGUMENTS, RadioError::INVALID_CALL_ID,
                 RadioError::INVALID_MODEM_STATE, RadioError::INVALID_STATE, RadioError::MODEM_ERR,
                 RadioError::NO_NETWORK_FOUND, RadioError::NO_SUBSCRIPTION,
                 RadioError::OPERATION_NOT_ALLOWED},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "dial finished";
}

/*
 * Test IRadioVoice.hangup() for the response returned.
 */
TEST_P(RadioVoiceTest, hangup) {
    LOG(DEBUG) << "hangup";
    serial = GetRandomSerialNumber();

    radio_voice->hangup(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::INVALID_ARGUMENTS, RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "hangup finished";
}

/*
 * Test IRadioVoice.hangupWaitingOrBackground() for the response returned.
 */
TEST_P(RadioVoiceTest, hangupWaitingOrBackground) {
    LOG(DEBUG) << "hangupWaitingOrBackground";
    serial = GetRandomSerialNumber();

    radio_voice->hangupWaitingOrBackground(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "hangupWaitingOrBackground finished";
}

/*
 * Test IRadioVoice.hangupForegroundResumeBackground() for the response returned.
 */
TEST_P(RadioVoiceTest, hangupForegroundResumeBackground) {
    LOG(DEBUG) << "hangupForegroundResumeBackground";
    serial = GetRandomSerialNumber();

    radio_voice->hangupForegroundResumeBackground(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "hangupForegroundResumeBackground finished";
}

/*
 * Test IRadioVoice.switchWaitingOrHoldingAndActive() for the response returned.
 */
TEST_P(RadioVoiceTest, switchWaitingOrHoldingAndActive) {
    LOG(DEBUG) << "switchWaitingOrHoldingAndActive";
    serial = GetRandomSerialNumber();

    radio_voice->switchWaitingOrHoldingAndActive(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "switchWaitingOrHoldingAndActive finished";
}

/*
 * Test IRadioVoice.conference() for the response returned.
 */
TEST_P(RadioVoiceTest, conference) {
    LOG(DEBUG) << "conference";
    serial = GetRandomSerialNumber();

    radio_voice->conference(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "conference finished";
}

/*
 * Test IRadioVoice.rejectCall() for the response returned.
 */
TEST_P(RadioVoiceTest, rejectCall) {
    LOG(DEBUG) << "rejectCall";
    serial = GetRandomSerialNumber();

    radio_voice->rejectCall(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "rejectCall finished";
}

/*
 * Test IRadioVoice.getLastCallFailCause() for the response returned.
 */
TEST_P(RadioVoiceTest, getLastCallFailCause) {
    LOG(DEBUG) << "getLastCallFailCause";
    serial = GetRandomSerialNumber();

    radio_voice->getLastCallFailCause(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error, {RadioError::NONE},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "getLastCallFailCause finished";
}

/*
 * Test IRadioVoice.getCallForwardStatus() for the response returned.
 */
TEST_P(RadioVoiceTest, getCallForwardStatus) {
    LOG(DEBUG) << "getCallForwardStatus";
    serial = GetRandomSerialNumber();
    CallForwardInfo callInfo;
    memset(&callInfo, 0, sizeof(callInfo));
    callInfo.number = std::string();

    radio_voice->getCallForwardStatus(serial, callInfo);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::INVALID_ARGUMENTS, RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "getCallForwardStatus finished";
}

/*
 * Test IRadioVoice.setCallForward() for the response returned.
 */
TEST_P(RadioVoiceTest, setCallForward) {
    LOG(DEBUG) << "setCallForward";
    serial = GetRandomSerialNumber();
    CallForwardInfo callInfo;
    memset(&callInfo, 0, sizeof(callInfo));
    callInfo.number = std::string();

    radio_voice->setCallForward(serial, callInfo);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::INVALID_ARGUMENTS, RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "setCallForward finished";
}

/*
 * Test IRadioVoice.getCallWaiting() for the response returned.
 */
TEST_P(RadioVoiceTest, getCallWaiting) {
    LOG(DEBUG) << "getCallWaiting";
    serial = GetRandomSerialNumber();

    radio_voice->getCallWaiting(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::NONE, RadioError::INVALID_ARGUMENTS, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "getCallWaiting finished";
}

/*
 * Test IRadioVoice.setCallWaiting() for the response returned.
 */
TEST_P(RadioVoiceTest, setCallWaiting) {
    LOG(DEBUG) << "setCallWaiting";
    serial = GetRandomSerialNumber();

    radio_voice->setCallWaiting(serial, true, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::INVALID_ARGUMENTS, RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "setCallWaiting finished";
}

/*
 * Test IRadioVoice.acceptCall() for the response returned.
 */
TEST_P(RadioVoiceTest, acceptCall) {
    LOG(DEBUG) << "acceptCall";
    serial = GetRandomSerialNumber();

    radio_voice->acceptCall(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "acceptCall finished";
}

/*
 * Test IRadioVoice.separateConnection() for the response returned.
 */
TEST_P(RadioVoiceTest, separateConnection) {
    LOG(DEBUG) << "separateConnection";
    serial = GetRandomSerialNumber();

    radio_voice->separateConnection(serial, 1);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::INVALID_ARGUMENTS, RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "separateConnection finished";
}

/*
 * Test IRadioVoice.explicitCallTransfer() for the response returned.
 */
TEST_P(RadioVoiceTest, explicitCallTransfer) {
    LOG(DEBUG) << "explicitCallTransfer";
    serial = GetRandomSerialNumber();

    radio_voice->explicitCallTransfer(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "explicitCallTransfer finished";
}

/*
 * Test IRadioVoice.sendCdmaFeatureCode() for the response returned.
 */
TEST_P(RadioVoiceTest, sendCdmaFeatureCode) {
    LOG(DEBUG) << "sendCdmaFeatureCode";
    serial = GetRandomSerialNumber();

    radio_voice->sendCdmaFeatureCode(serial, std::string());
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::NONE, RadioError::INVALID_ARGUMENTS,
                                      RadioError::INVALID_CALL_ID, RadioError::INVALID_MODEM_STATE,
                                      RadioError::MODEM_ERR, RadioError::OPERATION_NOT_ALLOWED},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "sendCdmaFeatureCode finished";
}

/*
 * Test IRadioVoice.sendDtmf() for the response returned.
 */
TEST_P(RadioVoiceTest, sendDtmf) {
    LOG(DEBUG) << "sendDtmf";
    serial = GetRandomSerialNumber();

    radio_voice->sendDtmf(serial, "1");
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::NONE, RadioError::INVALID_ARGUMENTS, RadioError::INVALID_CALL_ID,
                 RadioError::INVALID_MODEM_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "sendDtmf finished";
}

/*
 * Test IRadioVoice.startDtmf() for the response returned.
 */
TEST_P(RadioVoiceTest, startDtmf) {
    LOG(DEBUG) << "startDtmf";
    serial = GetRandomSerialNumber();

    radio_voice->startDtmf(serial, "1");
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::NONE, RadioError::INVALID_ARGUMENTS, RadioError::INVALID_CALL_ID,
                 RadioError::INVALID_MODEM_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "startDtmf finished";
}

/*
 * Test IRadioVoice.stopDtmf() for the response returned.
 */
TEST_P(RadioVoiceTest, stopDtmf) {
    LOG(DEBUG) << "stopDtmf";
    serial = GetRandomSerialNumber();

    radio_voice->stopDtmf(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::NONE, RadioError::INVALID_CALL_ID,
                                      RadioError::INVALID_MODEM_STATE, RadioError::MODEM_ERR},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "stopDtmf finished";
}

/*
 * Test IRadioVoice.setMute() for the response returned.
 */
TEST_P(RadioVoiceTest, setMute) {
    LOG(DEBUG) << "setMute";
    serial = GetRandomSerialNumber();

    radio_voice->setMute(serial, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::NONE, RadioError::INVALID_ARGUMENTS},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "setMute finished";
}

/*
 * Test IRadioVoice.getMute() for the response returned.
 */
TEST_P(RadioVoiceTest, getMute) {
    LOG(DEBUG) << "getMute";
    serial = GetRandomSerialNumber();

    radio_voice->getMute(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        EXPECT_EQ(RadioError::NONE, radioRsp_voice->rspInfo.error);
    }
    LOG(DEBUG) << "getMute finished";
}

/*
 * Test IRadioVoice.sendBurstDtmf() for the response returned.
 */
TEST_P(RadioVoiceTest, sendBurstDtmf) {
    LOG(DEBUG) << "sendBurstDtmf";
    serial = GetRandomSerialNumber();

    radio_voice->sendBurstDtmf(serial, "1", 0, 0);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                     {RadioError::INVALID_ARGUMENTS, RadioError::INVALID_STATE,
                                      RadioError::MODEM_ERR, RadioError::OPERATION_NOT_ALLOWED},
                                     CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "sendBurstDtmf finished";
}

/*
 * Test IRadioVoice.sendUssd() for the response returned.
 */
TEST_P(RadioVoiceTest, sendUssd) {
    LOG(DEBUG) << "sendUssd";
    serial = GetRandomSerialNumber();
    radio_voice->sendUssd(serial, std::string("test"));
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::INVALID_ARGUMENTS, RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "sendUssd finished";
}

/*
 * Test IRadioVoice.cancelPendingUssd() for the response returned.
 */
TEST_P(RadioVoiceTest, cancelPendingUssd) {
    LOG(DEBUG) << "cancelPendingUssd";
    serial = GetRandomSerialNumber();

    radio_voice->cancelPendingUssd(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    if (cardStatus.cardState == CardStatus::STATE_ABSENT) {
        ASSERT_TRUE(CheckAnyOfErrors(
                radioRsp_voice->rspInfo.error,
                {RadioError::NONE, RadioError::INVALID_STATE, RadioError::MODEM_ERR},
                CHECK_GENERAL_ERROR));
    }
    LOG(DEBUG) << "cancelPendingUssd finished";
}

/*
 * Test IRadioVoice.isVoNrEnabled() for the response returned.
 */
TEST_P(RadioVoiceTest, isVoNrEnabled) {
    LOG(DEBUG) << "isVoNrEnabled";
    serial = GetRandomSerialNumber();

    radio_voice->isVoNrEnabled(serial);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                 {RadioError::REQUEST_NOT_SUPPORTED, RadioError::NONE}));
    LOG(DEBUG) << "isVoNrEnabled finished";
}

/*
 * Test IRadioVoice.setVoNrEnabled() for the response returned.
 */
TEST_P(RadioVoiceTest, setVoNrEnabled) {
    LOG(DEBUG) << "setVoNrEnabled";
    serial = GetRandomSerialNumber();

    radio_voice->setVoNrEnabled(serial, true);
    EXPECT_EQ(std::cv_status::no_timeout, wait());
    EXPECT_EQ(RadioResponseType::SOLICITED, radioRsp_voice->rspInfo.type);
    EXPECT_EQ(serial, radioRsp_voice->rspInfo.serial);

    ASSERT_TRUE(CheckAnyOfErrors(radioRsp_voice->rspInfo.error,
                                 {RadioError::REQUEST_NOT_SUPPORTED, RadioError::NONE}));
    LOG(DEBUG) << "setVoNrEnabled finished";
}
