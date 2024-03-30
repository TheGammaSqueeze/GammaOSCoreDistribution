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

#include "ClientComposer.h"

#include "Device.h"
#include "Display.h"
#include "Drm.h"
#include "Layer.h"

namespace android {

ClientComposer::ClientComposer(DrmPresenter* drmPresenter)
    : mDrmPresenter(drmPresenter) {}

HWC2::Error ClientComposer::init() {
  DEBUG_LOG("%s", __FUNCTION__);

  return HWC2::Error::None;
}

HWC2::Error ClientComposer::onDisplayCreate(Display* display) {
  const auto displayId = display->getId();
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  // Ensure created.
  mDisplayInfos.emplace(displayId, DisplayInfo{});

  return HWC2::Error::None;
}

HWC2::Error ClientComposer::onDisplayDestroy(Display* display) {
  const auto displayId = display->getId();
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  auto it = mDisplayInfos.find(displayId);
  if (it == mDisplayInfos.end()) {
    ALOGE("%s: display:%" PRIu64 " missing display buffers?", __FUNCTION__,
          displayId);
    return HWC2::Error::BadDisplay;
  }

  mDisplayInfos.erase(it);

  return HWC2::Error::None;
}

HWC2::Error ClientComposer::onDisplayClientTargetSet(Display* display) {
  const auto displayId = display->getId();
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  auto it = mDisplayInfos.find(displayId);
  if (it == mDisplayInfos.end()) {
    ALOGE("%s: display:%" PRIu64 " missing display buffers?", __FUNCTION__,
          displayId);
    return HWC2::Error::BadDisplay;
  }

  DisplayInfo& displayInfo = it->second;

  auto clientTargetNativeBuffer = display->getClientTarget().getBuffer();
  auto clientTargetDrmBuffer =
    std::make_unique<DrmBuffer>(clientTargetNativeBuffer, mDrmPresenter);
  if (!clientTargetDrmBuffer) {
    ALOGE("%s: display:%" PRIu64 " failed to create client target drm buffer",
          __FUNCTION__, displayId);
    return HWC2::Error::NoResources;
  }

  displayInfo.clientTargetDrmBuffer = std::move(clientTargetDrmBuffer);

  return HWC2::Error::None;
}

HWC2::Error ClientComposer::onActiveConfigChange(Display*) {
  DEBUG_LOG("%s", __FUNCTION__);

  return HWC2::Error::None;
};

HWC2::Error ClientComposer::validateDisplay(
    Display* display, std::unordered_map<hwc2_layer_t, HWC2::Composition>* changes) {
  const auto displayId = display->getId();
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);
  (void)displayId;

  const std::vector<Layer*>& layers = display->getOrderedLayers();

  for (Layer* layer : layers) {
    const auto layerId = layer->getId();
    const auto layerCompositionType = layer->getCompositionType();

    if (layerCompositionType != HWC2::Composition::Client) {
      (*changes)[layerId] = HWC2::Composition::Client;
    }
  }

  return HWC2::Error::None;
}

std::tuple<HWC2::Error, base::unique_fd> ClientComposer::presentDisplay(
    Display* display) {
  ATRACE_CALL();

  const auto displayId = display->getId();
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  auto displayInfoIt = mDisplayInfos.find(displayId);
  if (displayInfoIt == mDisplayInfos.end()) {
    ALOGE("%s: failed to find display buffers for display:%" PRIu64,
          __FUNCTION__, displayId);
    return std::make_tuple(HWC2::Error::BadDisplay, base::unique_fd());
  }

  DisplayInfo& displayInfo = displayInfoIt->second;

  auto clientTargetFence = display->getClientTarget().getFence();

  auto [error, presentFence] =
      displayInfo.clientTargetDrmBuffer->flushToDisplay(
          static_cast<int>(displayId), clientTargetFence);
  if (error != HWC2::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to flush drm buffer" PRIu64,
          __FUNCTION__, displayId);
    return std::make_tuple(error, base::unique_fd());
  }

  return std::make_tuple(HWC2::Error::None, std::move(presentFence));
}

}  // namespace android