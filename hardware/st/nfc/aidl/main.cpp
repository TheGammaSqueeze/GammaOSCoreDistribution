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

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <dlfcn.h>

#include "Nfc.h"

#define VENDOR_LIB_PATH "/vendor/lib64/"
#define VENDOR_LIB_EXT ".so"

using ::aidl::android::hardware::nfc::Nfc;

typedef int (*STEseReset)(void);

int main() {
  void* stdll = nullptr;
  LOG(INFO) << "NFC AIDL HAL Service is starting up";

  std::string valueStr =
      android::base::GetProperty("persist.vendor.nfc.streset", "");
  if (valueStr.length() > 0) {
    stdll = dlopen(valueStr.c_str(), RTLD_NOW);
    if (!stdll) {
      valueStr = VENDOR_LIB_PATH + valueStr + VENDOR_LIB_EXT;
      stdll = dlopen(valueStr.c_str(), RTLD_NOW);
    }
    if (stdll) {
      LOG(INFO) << "ST NFC HAL STReset starting.";
      STEseReset fn = (STEseReset)dlsym(stdll, "boot_reset");
      if (fn) {
        int ret = fn();
        LOG(INFO) << "STReset Result= " << ret;
      }
      LOG(INFO) << ("ST NFC HAL STReset Done.");
    }
  }
  if (!ABinderProcess_setThreadPoolMaxThreadCount(1)) {
    LOG(INFO) << "failed to set thread pool max thread count";
    return 1;
  }
  std::shared_ptr<Nfc> nfc_service = ndk::SharedRefBase::make<Nfc>();

  const std::string instance = std::string() + Nfc::descriptor + "/default";
  binder_status_t status = AServiceManager_addService(
      nfc_service->asBinder().get(), instance.c_str());
  CHECK(status == STATUS_OK);
  ABinderProcess_joinThreadPool();
  return 0;
}
