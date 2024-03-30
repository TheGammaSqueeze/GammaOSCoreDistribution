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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H
#define ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H

#include <aidl/android/hardware/contexthub/BnContextHub.h>
#include <android-base/file.h>
#include <log/log.h>
#include <map>
#include <mutex>
#include <optional>
#include <unordered_set>

#include "event_logger.h"
#include "hal_chre_socket_connection.h"

namespace aidl {
namespace android {
namespace hardware {
namespace contexthub {

class ContextHub : public BnContextHub,
                   public ::android::hardware::contexthub::common::
                       implementation::IChreSocketCallback {
 public:
  ContextHub()
      : mDeathRecipient(
            AIBinder_DeathRecipient_new(ContextHub::onServiceDied)) {}

  ::ndk::ScopedAStatus getContextHubs(
      std::vector<ContextHubInfo> *out_contextHubInfos) override;
  ::ndk::ScopedAStatus loadNanoapp(int32_t contextHubId,
                                   const NanoappBinary &appBinary,
                                   int32_t transactionId) override;
  ::ndk::ScopedAStatus unloadNanoapp(int32_t contextHubId, int64_t appId,
                                     int32_t transactionId) override;
  ::ndk::ScopedAStatus disableNanoapp(int32_t contextHubId, int64_t appId,
                                      int32_t transactionId) override;
  ::ndk::ScopedAStatus enableNanoapp(int32_t contextHubId, int64_t appId,
                                     int32_t transactionId) override;
  ::ndk::ScopedAStatus onSettingChanged(Setting setting, bool enabled) override;
  ::ndk::ScopedAStatus queryNanoapps(int32_t contextHubId) override;
  ::ndk::ScopedAStatus registerCallback(
      int32_t contextHubId,
      const std::shared_ptr<IContextHubCallback> &cb) override;
  ::ndk::ScopedAStatus sendMessageToHub(
      int32_t contextHubId, const ContextHubMessage &message) override;
  ::ndk::ScopedAStatus onHostEndpointConnected(
      const HostEndpointInfo &in_info) override;
  ::ndk::ScopedAStatus onHostEndpointDisconnected(
      char16_t in_hostEndpointId) override;

  void onNanoappMessage(const ::chre::fbs::NanoappMessageT &message) override;

  void onNanoappListResponse(
      const ::chre::fbs::NanoappListResponseT &response) override;

  void onTransactionResult(uint32_t transactionId, bool success) override;

  void onContextHubRestarted() override;

  void onDebugDumpData(const ::chre::fbs::DebugDumpDataT &data) override;

  void onDebugDumpComplete(
      const ::chre::fbs::DebugDumpResponseT &response) override;

  void handleServiceDeath();
  static void onServiceDied(void *cookie);

  binder_status_t dump(int fd, const char **args, uint32_t numArgs) override;

 private:
  ::android::hardware::contexthub::common::implementation::
      HalChreSocketConnection mConnection{this};

  // A mutex to protect concurrent modifications to the callback pointer and
  // access (invocations).
  std::mutex mCallbackMutex;
  std::shared_ptr<IContextHubCallback> mCallback;

  ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;

  std::map<Setting, bool> mSettingEnabled;
  std::optional<bool> mIsWifiAvailable;
  std::optional<bool> mIsBleAvailable;

  std::mutex mConnectedHostEndpointsMutex;
  std::unordered_set<char16_t> mConnectedHostEndpoints;

  // Variables related to debug dump.
  static constexpr int kInvalidFd = -1;
  int mDebugFd = kInvalidFd;
  bool mDebugDumpPending = false;
  std::mutex mDebugDumpMutex;
  std::condition_variable mDebugDumpCond;

  // Logs events to be reported in debug dumps.
  EventLogger mEventLogger;

  bool isSettingEnabled(Setting setting) {
    return mSettingEnabled.count(setting) > 0 ? mSettingEnabled[setting]
                                              : false;
  }

  chre::fbs::SettingState toFbsSettingState(bool enabled) const {
    return enabled ? chre::fbs::SettingState::ENABLED
                   : chre::fbs::SettingState::DISABLED;
  }

  // Write a string to mDebugFd
  void writeToDebugFile(const std::string &str) {
    if (!::android::base::WriteStringToFd(str, mDebugFd)) {
      ALOGW("Failed to write %zu bytes to debug dump fd", str.size());
    }
  }

  void writeToDebugFile(const char *str) {
    writeToDebugFile(str, strlen(str));
  }

  void writeToDebugFile(const char *str, size_t len) {
    std::string s(str, len);
    writeToDebugFile(s);
  }
};

}  // namespace contexthub
}  // namespace hardware
}  // namespace android
}  // namespace aidl

#endif  // ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H
