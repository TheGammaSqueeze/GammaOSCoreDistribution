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

#ifndef ANDROID_HWC_DISPLAYFINDER_H
#define ANDROID_HWC_DISPLAYFINDER_H

#include <vector>

#include "Common.h"
#include "DisplayConfig.h"
#include "DrmPresenter.h"

namespace android {

struct DisplayMultiConfigs {
  hwc2_display_t displayId;
  hwc2_config_t activeConfigId;
  // Modes that this display can be configured to use.
  std::vector<DisplayConfig> configs;
};

HWC2::Error findDisplays(DrmPresenter* drmPresenter,
                         std::vector<DisplayMultiConfigs>& displays);

}  // namespace android

#endif
