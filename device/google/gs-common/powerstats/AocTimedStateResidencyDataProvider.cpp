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

#include "AocTimedStateResidencyDataProvider.h"

#include <android-base/logging.h>
#include <chrono>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {

AocTimedStateResidencyDataProvider::AocTimedStateResidencyDataProvider(
    std::vector<std::pair<std::string, std::string>> ids,
    std::vector<std::pair<std::string, std::string>> states,
    const uint64_t timeoutMillis,
    const uint64_t aocClock)
    : AocStateResidencyDataProvider(ids, states, aocClock) {
  static const uint64_t DEFAULT_MAX_TIME_PER_STATE_MILLIS = 120;

  mTimeoutMillis =
      timeoutMillis == 0 ? DEFAULT_MAX_TIME_PER_STATE_MILLIS * states.size() : timeoutMillis;

  mAsyncThread = std::thread(&AocTimedStateResidencyDataProvider::getStateResidenciesAsync, this);
}

bool AocTimedStateResidencyDataProvider::getStateResidencies(
    std::unordered_map<std::string, std::vector<StateResidency>> *residencies) {
  bool ret = true;
  std::unique_lock<std::mutex> statusLock(mStatusMutex);

  if (mAsyncStatus != COMPLETED) {
    LOG(ERROR) << "The async thread is not ready: " << mAsyncStatus;
    return false;
  }

  mStateResidencies.clear();

  mAsyncStatus = RUN;
  mRunCond.notify_one();

  auto timeout = std::chrono::steady_clock::now() + std::chrono::milliseconds(mTimeoutMillis);
  auto isCompleted =
      mCompletedCond.wait_until(statusLock, timeout, [this]{ return mAsyncStatus == COMPLETED; });

  if (isCompleted) {
    for (const auto &residency : mStateResidencies) {
      residencies->emplace(residency.first, residency.second);
    }
  } else {
    LOG(ERROR) << __func__ << " for AoC timed out: " << mTimeoutMillis << " ms";
    ret = false;
  }

  return ret;
}

void AocTimedStateResidencyDataProvider::getStateResidenciesAsync() {
  std::unique_lock<std::mutex> statusLock(mStatusMutex);

  mAsyncStatus = COMPLETED;

  while (1) {
    mRunCond.wait(statusLock, [this]{ return mAsyncStatus == RUN; });

    mAsyncStatus = RUNNING;
    statusLock.unlock();

    // States from the same power entity are merged.
    for (const auto &providerList : mProviders) {
      int32_t stateId = 0;
      std::string curEntity = providerList.first;
      std::vector<StateResidency> stateResidencies;

      // Iterate over each provider in the providerList, appending each of the states
      for (const auto &provider : providerList.second) {
        std::unordered_map<std::string, std::vector<StateResidency>> residency;
        provider->getStateResidencies(&residency);

        // Each provider should only return data for curEntity but checking anyway
        if (residency.find(curEntity) != residency.end()) {
          for (auto &r : residency.at(curEntity)) {
            /*
             * Modifying stateId here because we are stitching together infos from
             * multiple GenericStateResidencyDataProviders. stateId must be modified
             * to maintain uniqueness for a given entity
             */
            r.id = stateId++;
            stateResidencies.push_back(r);
          }
        }
      }
      mStateResidencies.emplace(curEntity, stateResidencies);
    }

    statusLock.lock();
    mAsyncStatus = COMPLETED;
    mCompletedCond.notify_one();
  } // while loop
}

}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
