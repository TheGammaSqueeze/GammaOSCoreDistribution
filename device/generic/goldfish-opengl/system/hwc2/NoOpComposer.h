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

#ifndef ANDROID_HWC_NOOPCOMPOSER_H
#define ANDROID_HWC_NOOPCOMPOSER_H

#include "Common.h"
#include "Composer.h"
#include "Display.h"
#include "DrmPresenter.h"
#include "Gralloc.h"
#include "Layer.h"

namespace android {

class NoOpComposer : public Composer {
 public:
  NoOpComposer();

  NoOpComposer(const NoOpComposer&) = delete;
  NoOpComposer& operator=(const NoOpComposer&) = delete;

  NoOpComposer(NoOpComposer&&) = delete;
  NoOpComposer& operator=(NoOpComposer&&) = delete;

  HWC2::Error init() override;

  HWC2::Error onDisplayCreate(Display*) override;

  HWC2::Error onDisplayDestroy(Display*) override;

  HWC2::Error onDisplayClientTargetSet(Display*) override;

  HWC2::Error onActiveConfigChange(Display*) override;

  // Determines if this composer can compose the given layers on the given
  // display and requests changes for layers that can't not be composed.
  HWC2::Error validateDisplay(
      Display* display, std::unordered_map<hwc2_layer_t, HWC2::Composition>*
                            outLayerCompositionChanges) override;

  // Performs the actual composition of layers and presents the composed result
  // to the display.
  std::tuple<HWC2::Error, base::unique_fd> presentDisplay(
      Display* display) override;
};

}  // namespace android

#endif
