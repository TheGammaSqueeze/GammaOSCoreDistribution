/*
 * Copyright (C) 2022 The Android Open Source Project
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
#ifndef ANDROID_HARDWARE_VIBRATOR_TEST_MOCKS_H
#define ANDROID_HARDWARE_VIBRATOR_TEST_MOCKS_H

#include <aidl/android/hardware/vibrator/BnVibratorCallback.h>

#include "Vibrator.h"

class MockApi : public ::aidl::android::hardware::vibrator::Vibrator::HwApi {
  public:
    MOCK_METHOD0(destructor, void());
    MOCK_METHOD1(setF0, bool(std::string value));
    MOCK_METHOD1(setF0Offset, bool(uint32_t value));
    MOCK_METHOD1(setRedc, bool(std::string value));
    MOCK_METHOD1(setQ, bool(std::string value));
    MOCK_METHOD1(getEffectCount, bool(uint32_t *value));
    MOCK_METHOD2(pollVibeState, bool(uint32_t value, int32_t timeoutMs));
    MOCK_METHOD0(hasOwtFreeSpace, bool());
    MOCK_METHOD1(getOwtFreeSpace, bool(uint32_t *value));
    MOCK_METHOD1(setF0CompEnable, bool(bool value));
    MOCK_METHOD1(setRedcCompEnable, bool(bool value));
    MOCK_METHOD1(setMinOnOffInterval, bool(uint32_t value));
    MOCK_METHOD2(setFFGain, bool(int fd, uint16_t value));
    MOCK_METHOD3(setFFEffect, bool(int fd, struct ff_effect *effect, uint16_t timeoutMs));
    MOCK_METHOD3(setFFPlay, bool(int fd, int8_t index, bool value));
    MOCK_METHOD2(getHapticAlsaDevice, bool(int *card, int *device));
    MOCK_METHOD4(setHapticPcmAmp, bool(struct pcm **haptic_pcm, bool enable, int card, int device));
    MOCK_METHOD6(uploadOwtEffect,
                 bool(int fd, uint8_t *owtData, uint32_t numBytes, struct ff_effect *effect,
                      uint32_t *outEffectIndex, int *status));
    MOCK_METHOD3(eraseOwtEffect, bool(int fd, int8_t effectIndex, std::vector<ff_effect> *effect));
    MOCK_METHOD1(debug, void(int fd));

    ~MockApi() override { destructor(); };
};

class MockCal : public ::aidl::android::hardware::vibrator::Vibrator::HwCal {
  public:
    MOCK_METHOD0(destructor, void());
    MOCK_METHOD1(getVersion, bool(uint32_t *value));
    MOCK_METHOD1(getF0, bool(std::string &value));
    MOCK_METHOD1(getRedc, bool(std::string &value));
    MOCK_METHOD1(getQ, bool(std::string &value));
    MOCK_METHOD1(getLongFrequencyShift, bool(int32_t *value));
    MOCK_METHOD1(getTickVolLevels, bool(std::array<uint32_t, 2> *value));
    MOCK_METHOD1(getClickVolLevels, bool(std::array<uint32_t, 2> *value));
    MOCK_METHOD1(getLongVolLevels, bool(std::array<uint32_t, 2> *value));
    MOCK_METHOD0(isChirpEnabled, bool());
    MOCK_METHOD1(getSupportedPrimitives, bool(uint32_t *value));
    MOCK_METHOD0(isF0CompEnabled, bool());
    MOCK_METHOD0(isRedcCompEnabled, bool());
    MOCK_METHOD1(debug, void(int fd));

    ~MockCal() override { destructor(); };
    // b/132668253: Workaround gMock Compilation Issue
    bool getF0(std::string *value) { return getF0(*value); }
    bool getRedc(std::string *value) { return getRedc(*value); }
    bool getQ(std::string *value) { return getQ(*value); }
};

class MockVibratorCallback : public aidl::android::hardware::vibrator::BnVibratorCallback {
  public:
    MOCK_METHOD(ndk::ScopedAStatus, onComplete, ());
};

#endif  // ANDROID_HARDWARE_VIBRATOR_TEST_MOCKS_H
