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

#ifndef RESOURCE_CACHE_H
#define RESOURCE_CACHE_H

#include <android-base/unique_fd.h>

#include "rockchip/drmgralloc.h"

namespace android {

class GemHandle {
  public:
    GemHandle();
    ~GemHandle();
    GemHandle(const GemHandle&) = delete;
    GemHandle& operator=(const GemHandle&) = delete;
    int InitGemHandle(const char *name, uint64_t buffer_fd, uint64_t buffer_id);
    uint32_t GetGemHandle();
    bool isValid();

  private:
    DrmGralloc *drmGralloc_;
    uint64_t uBufferId_=0;
    uint32_t uGemHandle_=0;
    const char *name_;
};

class LayerInfoCache{
  public:
  LayerInfoCache();
  ~LayerInfoCache();
  LayerInfoCache(const LayerInfoCache&) = delete;
  LayerInfoCache& operator=(const LayerInfoCache&) = delete;

  buffer_handle_t native_buffer_ = NULL;
  base::unique_fd iFd_;
  int iFormat_=0;
  int iWidth_=0;
  int iHeight_=0;
  int iStride_=0;
  int iHeightStride_=0;
  int iSize_=0;
  int iByteStride_=0;
  std::vector<uint32_t> uByteStridePlanes_;
  uint64_t iUsage_=0;
  uint32_t uFourccFormat_=0;
  uint32_t uGemHandle_=0;
  uint64_t uModifier_=0;
  uint64_t uBufferId_;
  GemHandle gemHandle_;
  std::string sLayerName_;
};

};
#endif