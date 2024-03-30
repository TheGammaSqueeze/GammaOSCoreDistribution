/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "hwc-uevent-listener"

#include "UEventListener.h"

#include <cerrno>

#include "utils/log.h"

/* Originally defined in system/core/libsystem/include/system/graphics.h as
 * #define HAL_PRIORITY_URGENT_DISPLAY (-8)*/
constexpr int kHalPriorityUrgentDisplay = -8;

namespace android {

UEventListener::UEventListener()
    : Worker("uevent-listener", kHalPriorityUrgentDisplay){};

int UEventListener::Init() {
  uevent_ = UEvent::CreateInstance();
  if (!uevent_) {
    return -ENODEV;
  }

  return InitWorker();
}

void UEventListener::Routine() {
  while (true) {
    auto uevent_str = uevent_->ReadNext();

    if (!hotplug_handler_ || !uevent_str)
      continue;

    bool drm_event = uevent_str->find("DEVTYPE=drm_minor") != std::string::npos;
    bool hotplug_event = uevent_str->find("HOTPLUG=1") != std::string::npos;

    if (drm_event && hotplug_event) {
      constexpr useconds_t kDelayAfterUeventUs = 200000;
      /* We need some delay to ensure DrmConnector::UpdateModes() will query
       * correct modes list, otherwise at least RPI4 board may report 0 modes */
      usleep(kDelayAfterUeventUs);
      hotplug_handler_();
    }
  }
}
}  // namespace android
