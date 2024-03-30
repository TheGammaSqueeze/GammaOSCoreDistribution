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

#ifndef ANDROID_HWC_CLIENTFRAMECOMPOSER_H
#define ANDROID_HWC_CLIENTFRAMECOMPOSER_H

#include "Common.h"
#include "Display.h"
#include "DrmPresenter.h"
#include "FrameComposer.h"
#include "Gralloc.h"
#include "Layer.h"

namespace aidl::android::hardware::graphics::composer3::impl {

// A frame composer which always fallsback to client composition
// (a.k.a make SurfaceFlinger do the composition).
class ClientFrameComposer : public FrameComposer {
 public:
  ClientFrameComposer() = default;

  ClientFrameComposer(const ClientFrameComposer&) = delete;
  ClientFrameComposer& operator=(const ClientFrameComposer&) = delete;

  ClientFrameComposer(ClientFrameComposer&&) = delete;
  ClientFrameComposer& operator=(ClientFrameComposer&&) = delete;

  HWC3::Error init() override;

  HWC3::Error registerOnHotplugCallback(const HotplugCallback& cb) override;

  HWC3::Error unregisterOnHotplugCallback() override;

  HWC3::Error onDisplayCreate(Display* display) override;

  HWC3::Error onDisplayDestroy(Display* display) override;

  HWC3::Error onDisplayClientTargetSet(Display* display) override;

  HWC3::Error onActiveConfigChange(Display* display) override;

  // Determines if this composer can compose the given layers on the given
  // display and requests changes for layers that can't not be composed.
  HWC3::Error validateDisplay(Display* display,
                              DisplayChanges* outChanges) override;

  // Performs the actual composition of layers and presents the composed result
  // to the display.
  HWC3::Error presentDisplay(
      Display* display, ::android::base::unique_fd* outDisplayFence,
      std::unordered_map<int64_t, ::android::base::unique_fd>* outLayerFences)
      override;

  const DrmPresenter* getDrmPresenter() const override {
    return &mDrmPresenter;
  }

 private:
  struct DisplayInfo {
    std::shared_ptr<DrmBuffer> clientTargetDrmBuffer;
  };

  std::unordered_map<int64_t, DisplayInfo> mDisplayInfos;

  DrmPresenter mDrmPresenter;
};

}  // namespace aidl::android::hardware::graphics::composer3::impl

#endif
