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

#include "chre_host/wifi_ext_hal_handler.h"

namespace android {
namespace chre {

WifiExtHalHandler::~WifiExtHalHandler() {
  notifyThreadToExit();
  mThread.join();
}

WifiExtHalHandler::WifiExtHalHandler(
    const std::function<void(bool)> &statusChangeCallback) {
  mEnableConfig.reset();
  mThread = std::thread(&WifiExtHalHandler::wifiExtHandlerThreadEntry, this);
  auto cb = [&]() { onWifiExtHalServiceDeath(); };
  mDeathRecipient = new WifiExtHalDeathRecipient(cb);
  mCallback = new WifiExtCallback(statusChangeCallback);
}

void WifiExtHalHandler::handleConfigurationRequest(bool enable) {
  std::lock_guard<std::mutex> lock(mMutex);
  mEnableConfig = enable;
  mCondVar.notify_one();
}

void WifiExtHalHandler::dispatchConfigurationRequest(bool enable) {
  auto hidlCb = [this, enable](const WifiStatus &status) {
    bool success = (status.code == WifiStatusCode::SUCCESS) ? true : false;
    if (!success) {
      LOGE("wifi ext hal config request for %s failed with code: %d (%s)",
           (enable == true) ? "Enable" : "Disable", status.code,
           status.description.c_str());
    }
    mCallback->onStatusChanged(success);
  };

  if (checkWifiExtHalConnected()) {
    auto result = mService->requestWifiChreNanRtt(enable, hidlCb);
    if (!result.isOk()) {
      LOGE("Failed to %s NAN: %s", (enable == true) ? "Enable" : "Disable",
           result.description().c_str());
    }
  }
}

bool WifiExtHalHandler::checkWifiExtHalConnected() {
  bool success = false;
  if (mService == nullptr) {
    mService = IWifiExt::getService();
    if (mService != nullptr) {
      LOGD("Connected to Wifi Ext HAL service");
      mService->linkToDeath(mDeathRecipient, 0 /*cookie*/);

      auto hidlCb = [&success](const WifiStatus &status) {
        success = (status.code == WifiStatusCode::SUCCESS);
        if (!success) {
          LOGE("Failed to register CHRE callback with WifiExt: %s",
               status.description.c_str());
        }
      };
      auto result = mService->registerChreCallback(mCallback, hidlCb);
      if (!result.isOk()) {
        LOGE("Failed to register CHRE callback with WifiEmDeathRecipientxt: %s",
             result.description().c_str());
      } else {
        success = true;
      }
    } else {
      LOGE("Failed to connect to Wifi Ext HAL service");
    }
  }
  return success;
}

void WifiExtHalHandler::onWifiExtHalServiceDeath() {
  LOGI("WiFi Ext HAL service died");
  mService = nullptr;
  // TODO(b/204226580): Figure out if wifi ext HAL is stateful and if it
  // isn't, notify CHRE of a NAN disabled status change to enable nanoapps
  // to not expect NAN data until the service is back up, and expect it to
  // do a re-enable when needed. Or we could store the current status of
  // enablement, and do a re-enable/disable when the service is back up.
}

void WifiExtHalHandler::wifiExtHandlerThreadEntry() {
  while (mThreadRunning) {
    std::unique_lock<std::mutex> lock(mMutex);
    mCondVar.wait(
        lock, [this] { return mEnableConfig.has_value() || !mThreadRunning; });

    if (mThreadRunning) {
      dispatchConfigurationRequest(mEnableConfig.value());
      mEnableConfig.reset();
    }
  }
}

void WifiExtHalHandler::notifyThreadToExit() {
  std::lock_guard<std::mutex> lock(mMutex);
  mThreadRunning = false;
  mCondVar.notify_one();
}

}  // namespace chre
}  // namespace android
