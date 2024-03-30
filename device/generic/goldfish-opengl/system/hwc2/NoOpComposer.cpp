/*
 * Copyright 2022 The Android Open Source Project
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

#include "NoOpComposer.h"

#include "Device.h"
#include "Display.h"
#include "Drm.h"
#include "Layer.h"

namespace android {

NoOpComposer::NoOpComposer() {}

HWC2::Error NoOpComposer::init() {
  DEBUG_LOG("%s", __FUNCTION__);

  return HWC2::Error::None;
}

HWC2::Error NoOpComposer::onDisplayCreate(Display*) {
  DEBUG_LOG("%s", __FUNCTION__);

  return HWC2::Error::None;
}

HWC2::Error NoOpComposer::onDisplayDestroy(Display*) {
  DEBUG_LOG("%s", __FUNCTION__);

  return HWC2::Error::None;
}

HWC2::Error NoOpComposer::onDisplayClientTargetSet(Display*) {
  DEBUG_LOG("%s", __FUNCTION__);

  return HWC2::Error::None;
}

HWC2::Error NoOpComposer::onActiveConfigChange(Display*) {
  DEBUG_LOG("%s", __FUNCTION__);

  return HWC2::Error::None;
};

HWC2::Error NoOpComposer::validateDisplay(
    Display* display, std::unordered_map<hwc2_layer_t, HWC2::Composition>*) {
  const auto displayId = display->getId();
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  return HWC2::Error::None;
}

std::tuple<HWC2::Error, base::unique_fd> NoOpComposer::presentDisplay(
    Display*) {
  ATRACE_CALL();
  DEBUG_LOG("%s", __FUNCTION__);

  base::unique_fd emptyFence;
  return std::make_tuple(HWC2::Error::None, std::move(emptyFence));
}

}  // namespace android
