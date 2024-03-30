/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef ANDROID_HWC_HOSTCOMPOSER_H
#define ANDROID_HWC_HOSTCOMPOSER_H

#include <android-base/unique_fd.h>

#include <tuple>
#include <vector>

#include "Common.h"
#include "Composer.h"
#include "DrmPresenter.h"
#include "FencedBuffer.h"
#include "HostConnection.h"

namespace android {

class HostComposer : public Composer {
 public:
  HostComposer(DrmPresenter* drmPresenter, bool isMinigbm);

  HostComposer(const HostComposer&) = delete;
  HostComposer& operator=(const HostComposer&) = delete;

  HostComposer(HostComposer&&) = delete;
  HostComposer& operator=(HostComposer&&) = delete;

  HWC2::Error init() override;

  HWC2::Error onDisplayCreate(Display* display) override;

  HWC2::Error onDisplayDestroy(Display* display) override;

  HWC2::Error onDisplayClientTargetSet(Display* display) override;

  // Determines if this composer can compose the given layers on the given
  // display and requests changes for layers that can't not be composed.
  HWC2::Error validateDisplay(
      Display* display, std::unordered_map<hwc2_layer_t, HWC2::Composition>*
                            outLayerCompositionChanges) override;

  // Performs the actual composition of layers and presents the composed result
  // to the display.
  std::tuple<HWC2::Error, base::unique_fd> presentDisplay(
      Display* display) override;

  HWC2::Error onActiveConfigChange(Display* display) override;

 private:
  HWC2::Error createHostComposerDisplayInfo(Display* display,
                                            uint32_t hostDisplayId);

  void post(HostConnection* hostCon, ExtendedRCEncoderContext* rcEnc,
            buffer_handle_t h);

  bool mIsMinigbm = false;

  int mSyncDeviceFd = -1;

  class CompositionResultBuffer {
   public:
    static std::unique_ptr<CompositionResultBuffer> create(int32_t width,
                                                           int32_t height);
    static std::unique_ptr<CompositionResultBuffer> createWithDrmBuffer(
        int32_t width, int32_t height, DrmPresenter&);
    ~CompositionResultBuffer();

    DrmBuffer& waitAndGetDrmBuffer();
    buffer_handle_t waitAndGetBufferHandle();
    bool isReady() const;
    void setFence(base::unique_fd fence);

   private:
    CompositionResultBuffer() = default;

    void waitForFence();

    std::unique_ptr<FencedBuffer> mFencedBuffer;
    // Drm info for the additional composition result buffer.
    std::unique_ptr<DrmBuffer> mDrmBuffer;
  };
  class HostComposerDisplayInfo {
   public:
    HostComposerDisplayInfo() = default;
    void resetCompositionResultBuffers(
        std::vector<std::unique_ptr<CompositionResultBuffer>>);
    CompositionResultBuffer& getNextCompositionResultBuffer();

    uint32_t hostDisplayId = 0;
    // Drm info for the displays client target buffer.
    std::unique_ptr<DrmBuffer> clientTargetDrmBuffer;

   private:
    // Additional per display buffer for the composition result.
    std::vector<std::unique_ptr<CompositionResultBuffer>>
        compositionResultBuffers;
  };

  std::unordered_map<hwc2_display_t, HostComposerDisplayInfo> mDisplayInfos;
  DrmPresenter* mDrmPresenter;
};

}  // namespace android

#endif
