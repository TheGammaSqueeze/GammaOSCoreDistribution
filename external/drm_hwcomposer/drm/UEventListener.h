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

#ifndef ANDROID_UEVENT_LISTENER_H_
#define ANDROID_UEVENT_LISTENER_H_

#include <functional>

#include "utils/UEvent.h"
#include "utils/Worker.h"

namespace android {

class UEventListener : public Worker {
 public:
  UEventListener();
  ~UEventListener() override = default;

  int Init();

  void RegisterHotplugHandler(std::function<void()> hotplug_handler) {
    hotplug_handler_ = std::move(hotplug_handler);
  }

 protected:
  void Routine() override;

 private:
  std::unique_ptr<UEvent> uevent_;

  std::function<void()> hotplug_handler_;
};
}  // namespace android

#endif
