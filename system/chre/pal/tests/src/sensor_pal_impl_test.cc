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

#include <cstdint>

#include "chre/pal/sensor.h"
#include "chre/platform/condition_variable.h"
#include "chre/platform/mutex.h"
#include "chre/platform/shared/pal_system_api.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/lock_guard.h"
#include "chre/util/macros.h"
#include "chre/util/optional.h"
#include "chre/util/time.h"
#include "chre/util/unique_ptr.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace {

using ::chre::ConditionVariable;
using ::chre::FixedSizeVector;
using ::chre::gChrePalSystemApi;
using ::chre::kOneMillisecondInNanoseconds;
using ::chre::LockGuard;
using ::chre::MakeUnique;
using ::chre::Mutex;
using ::chre::Nanoseconds;
using ::chre::Optional;
using ::chre::UniquePtr;
using ::testing::ElementsAre;

class Callbacks {
 public:
  void samplingStatusUpdateCallback(uint32_t sensorInfoIndex,
                                    struct chreSensorSamplingStatus *status) {
    LockGuard<Mutex> lock(mMutex);
    if (!mStatusSensorIndex.has_value()) {
      mStatusSensorIndex = sensorInfoIndex;
      mStatus = status;
      mCondVarStatus.notify_one();
    }
  }

  void dataEventCallback(uint32_t sensorInfoIndex, void *data) {
    LockGuard<Mutex> lock(mMutex);
    if (!mEventSensorIndices.full()) {
      mEventSensorIndices.push_back(sensorInfoIndex);
      mEventData.push_back(data);
      if (mEventSensorIndices.full()) {
        mCondVarEvents.notify_one();
      }
    }
  }

  void biasEventCallback(uint32_t sensorInfoIndex, void *biasData) {
    UNUSED_VAR(sensorInfoIndex);
    UNUSED_VAR(biasData);
  }

  void flushCompleteCallback(uint32_t sensorInfoIndex, uint32_t flushRequestId,
                             uint8_t errorCode) {
    UNUSED_VAR(sensorInfoIndex);
    UNUSED_VAR(flushRequestId);
    UNUSED_VAR(errorCode);
  }

  static constexpr uint32_t kNumEvents = 3;

  Optional<uint32_t> mStatusSensorIndex;
  Optional<struct chreSensorSamplingStatus *> mStatus;

  FixedSizeVector<uint32_t, kNumEvents> mEventSensorIndices;
  FixedSizeVector<void *, kNumEvents> mEventData;

  //! Synchronize access to class members.
  Mutex mMutex;
  ConditionVariable mCondVarEvents;
  ConditionVariable mCondVarStatus;
};

UniquePtr<Callbacks> gCallbacks = nullptr;

void samplingStatusUpdateCallback(uint32_t sensorInfoIndex,
                                  struct chreSensorSamplingStatus *status) {
  if (gCallbacks != nullptr) {
    gCallbacks->samplingStatusUpdateCallback(sensorInfoIndex, status);
  }
}

void dataEventCallback(uint32_t sensorInfoIndex, void *data) {
  if (gCallbacks != nullptr) {
    gCallbacks->dataEventCallback(sensorInfoIndex, data);
  }
}

void biasEventCallback(uint32_t sensorInfoIndex, void *biasData) {
  if (gCallbacks != nullptr) {
    gCallbacks->biasEventCallback(sensorInfoIndex, biasData);
  }
}

void flushCompleteCallback(uint32_t sensorInfoIndex, uint32_t flushRequestId,
                           uint8_t errorCode) {
  if (gCallbacks != nullptr) {
    gCallbacks->flushCompleteCallback(sensorInfoIndex, flushRequestId,
                                      errorCode);
  }
}

class PalSensorTest : public testing::Test {
 protected:
  void SetUp() override {
    gCallbacks = MakeUnique<Callbacks>();
    mApi = chrePalSensorGetApi(CHRE_PAL_SENSOR_API_CURRENT_VERSION);
    ASSERT_NE(mApi, nullptr);
    EXPECT_EQ(mApi->moduleVersion, CHRE_PAL_SENSOR_API_CURRENT_VERSION);
    ASSERT_TRUE(mApi->open(&gChrePalSystemApi, &mPalCallbacks));
  }

  void TearDown() override {
    gCallbacks = nullptr;
    if (mApi != nullptr) {
      mApi->close();
    }
  }

  //! CHRE PAL implementation API.
  const struct chrePalSensorApi *mApi;

  const struct chrePalSensorCallbacks mPalCallbacks = {
      .samplingStatusUpdateCallback = samplingStatusUpdateCallback,
      .dataEventCallback = dataEventCallback,
      .biasEventCallback = biasEventCallback,
      .flushCompleteCallback = flushCompleteCallback,
  };
};

TEST_F(PalSensorTest, GetTheListOfSensors) {
  const struct chreSensorInfo *sensors;
  uint32_t arraySize;

  EXPECT_TRUE(mApi->getSensors(&sensors, &arraySize));
  EXPECT_EQ(arraySize, 1);
  EXPECT_STREQ(sensors[0].sensorName, "Test Accelerometer");
}

TEST_F(PalSensorTest, EnableAContinuousSensor) {
  EXPECT_TRUE(mApi->configureSensor(
      0 /*sensorInfoIndex*/, CHRE_SENSOR_CONFIGURE_MODE_CONTINUOUS,
      kOneMillisecondInNanoseconds /*intervalNs*/, 0 /*latencyNs*/));

  LockGuard<Mutex> lock(gCallbacks->mMutex);
  gCallbacks->mCondVarStatus.wait_for(
      gCallbacks->mMutex, Nanoseconds(kOneMillisecondInNanoseconds));
  EXPECT_TRUE(gCallbacks->mStatusSensorIndex.has_value());
  EXPECT_EQ(gCallbacks->mStatusSensorIndex.value(), 0);
  EXPECT_TRUE(gCallbacks->mStatus.has_value());
  EXPECT_TRUE(gCallbacks->mStatus.value()->enabled);

  gCallbacks->mCondVarEvents.wait_for(
      gCallbacks->mMutex,
      Nanoseconds((2 + gCallbacks->kNumEvents) * kOneMillisecondInNanoseconds));
  EXPECT_TRUE(gCallbacks->mEventSensorIndices.full());
  EXPECT_THAT(gCallbacks->mEventSensorIndices, ElementsAre(0, 0, 0));
  EXPECT_TRUE(gCallbacks->mEventData.full());
  for (void *data : gCallbacks->mEventData) {
    auto threeAxisData =
        static_cast<const struct chreSensorThreeAxisData *>(data);
    EXPECT_EQ(threeAxisData->header.readingCount, 1);
    mApi->releaseSensorDataEvent(data);
  }
}

TEST_F(PalSensorTest, DisableAContinuousSensor) {
  EXPECT_TRUE(mApi->configureSensor(
      0 /*sensorInfoIndex*/, CHRE_SENSOR_CONFIGURE_MODE_DONE,
      kOneMillisecondInNanoseconds /*intervalNs*/, 0 /*latencyNs*/));

  LockGuard<Mutex> lock(gCallbacks->mMutex);
  gCallbacks->mCondVarStatus.wait_for(
      gCallbacks->mMutex, Nanoseconds(kOneMillisecondInNanoseconds));
  EXPECT_TRUE(gCallbacks->mStatusSensorIndex.has_value());
  EXPECT_EQ(gCallbacks->mStatusSensorIndex.value(), 0);
  EXPECT_TRUE(gCallbacks->mStatus.has_value());
  EXPECT_FALSE(gCallbacks->mStatus.value()->enabled);
}

}  // namespace