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

#ifndef ANDROID_DRM_KMS_PLAN_H_
#define ANDROID_DRM_KMS_PLAN_H_

#include <memory>
#include <vector>

#include "drmhwcomposer.h"

namespace android {

class DrmDevice;

struct DrmKmsPlan {
  struct LayerToPlaneJoining {
    DrmHwcLayer layer;
    std::shared_ptr<BindingOwner<DrmPlane>> plane;
    int z_pos;
  };

  std::vector<LayerToPlaneJoining> plan;

  static auto CreateDrmKmsPlan(DrmDisplayPipeline &pipe,
                               std::vector<DrmHwcLayer> composition)
      -> std::unique_ptr<DrmKmsPlan>;
};

}  // namespace android
#endif
