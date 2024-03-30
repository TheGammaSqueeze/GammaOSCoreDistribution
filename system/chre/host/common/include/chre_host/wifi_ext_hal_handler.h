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

// Undefine the NAN macro (similar to how it's done in the wifi utils library)
// to avoid symbol clashes between the NAN (Not-A-Number) macro in the bionic
// library headers, and the NAN (Neighbor-Aware-Networking) enum value in the
// WiFi ext interface.
#ifdef NAN
#undef NAN
#endif

#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <thread>

#include <vendor/google/wifi_ext/1.3/IWifiExt.h>
#include <vendor/google/wifi_ext/1.3/IWifiExtChreCallback.h>

#include "chre_host/log.h"

namespace android {
namespace chre {

/**
 * Handles interactions with the Wifi Ext HAL, to issue configuration
 * requests to enable or disable NAN (Neighbor-Aware Networking) functionality.
 */
class WifiExtHalHandler {
 public:
  using hidl_death_recipient = hardware::hidl_death_recipient;
  using WifiStatus = hardware::wifi::V1_0::WifiStatus;
  using WifiStatusCode = hardware::wifi::V1_0::WifiStatusCode;
  using IBase = hidl::base::V1_0::IBase;
  using IWifiExt = ::vendor::google::wifi_ext::V1_3::IWifiExt;
  using IWifiExtChreNanCallback =
      ::vendor::google::wifi_ext::V1_3::IWifiExtChreCallback;
  using WifiChreNanRttState =
      ::vendor::google::wifi_ext::V1_3::WifiChreNanRttState;

  ~WifiExtHalHandler();

  /**
   * Construct a new Wifi Ext Hal Handler object, initiate a connection to
   * the Wifi ext HAL service.
   *
   * @param statusChangeCallback Callback set by the daemon to be invoked on a
   *        status change to NAN's enablement.
   */
  WifiExtHalHandler(const std::function<void(bool)> &statusChangeCallback);

  /**
   * Invoked by the CHRE daemon when it receives a request to enable or disable
   * NAN from CHRE.
   *
   * @param enable true if CHRE is requesting NAN to be enabled, false if the
   *        request is for a disable.
   */
  void handleConfigurationRequest(bool enable);

 private:
  //! CHRE NAN availability status change handler.
  class WifiExtCallback : public IWifiExtChreNanCallback {
   public:
    WifiExtCallback(std::function<void(bool)> cb) : mCallback(cb) {}

    hardware::Return<void> onChreNanRttStateChanged(WifiChreNanRttState state) {
      bool enabled = (state == WifiChreNanRttState::CHRE_AVAILABLE);
      onStatusChanged(enabled);
      return hardware::Void();
    }

    void onStatusChanged(bool enabled) {
      mCallback(enabled);
    }

   private:
    std::function<void(bool)> mCallback;
  };

  //! Handler for when a connected Wifi ext HAL service dies.
  class WifiExtHalDeathRecipient : public hidl_death_recipient {
   public:
    WifiExtHalDeathRecipient() = delete;
    explicit WifiExtHalDeathRecipient(std::function<void()> cb)
        : mCallback(cb) {}

    virtual void serviceDied(uint64_t /*cookie*/,
                             const wp<IBase> & /*who*/) override {
      mCallback();
    }

   private:
    std::function<void()> mCallback;
  };

  bool mThreadRunning = true;
  std::thread mThread;
  std::mutex mMutex;
  std::condition_variable mCondVar;

  //! Flag used to indicate the state of the configuration request ('enable' if
  //! true, 'disable' otherwise) if it has a value.
  std::optional<bool> mEnableConfig;

  sp<WifiExtHalDeathRecipient> mDeathRecipient;
  sp<IWifiExt> mService;
  sp<WifiExtCallback> mCallback;

  /**
   * Entry point for the thread that handles all interactions with the WiFi ext
   * HAL. This is required since a connection initiation can potentially block
   * indefinitely.
   */
  void wifiExtHandlerThreadEntry();

  /**
   * Notifies the WifiExtHalHandler processing thread of a daemon shutdown.
   */
  void notifyThreadToExit();

  /**
   * Checks for a valid connection to the Wifi ext HAL service, reconnects if
   * not already connected.
   *
   * @return true if connected or upon successful reconnection, false
   *         otherwise.
   */
  bool checkWifiExtHalConnected();

  /**
   * Invoked by the HAL service death callback.
   */
  void onWifiExtHalServiceDeath();

  /**
   * Dispatch a configuration request to the WiFi Ext HAL.
   *
   * @param enable true if the request is to enable NAN, false if
   *        to disable.
   */
  void dispatchConfigurationRequest(bool enable);
};

}  // namespace chre
}  // namespace android