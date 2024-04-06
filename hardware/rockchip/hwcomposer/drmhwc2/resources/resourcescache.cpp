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
#define ATRACE_TAG ATRACE_TAG_GRAPHICS
#define LOG_TAG "hwc-resource-cache"

#include <inttypes.h>
#include "resources/resourcescache.h"
#include "rockchip/utils/drmdebug.h"

namespace android {
GemHandle::GemHandle() : drmGralloc_(DrmGralloc::getInstance()), name_(NULL){};
GemHandle::~GemHandle(){
  if(drmGralloc_ == NULL || name_ == NULL)
    return;

  if(uBufferId_ == 0 || uGemHandle_ == 0)
    return;

  int ret = drmGralloc_->hwc_free_gemhandle(uBufferId_);
  if(ret){
    HWC2_ALOGE("%s hwc_free_gemhandle fail, buffer_id =%" PRIx64, name_, uBufferId_);
  }
}

int GemHandle::InitGemHandle(const char *name,
                  uint64_t buffer_fd,
                  uint64_t buffer_id){
    name_ = name;
    uBufferId_ = buffer_id;
    int ret = drmGralloc_->hwc_get_gemhandle_from_fd(buffer_fd, uBufferId_, &uGemHandle_);
    if(ret){
      HWC2_ALOGE("%s hwc_get_gemhandle_from_fd fail, buffer_id =%" PRIx64, name_, uBufferId_);
    }
    return ret;
}
uint32_t GemHandle::GetGemHandle(){ return uGemHandle_;}
bool GemHandle::isValid(){ return uGemHandle_ != 0;}


LayerInfoCache::LayerInfoCache(): native_buffer_(NULL){};
LayerInfoCache::~LayerInfoCache(){
  if(native_buffer_!=NULL){
    DrmGralloc* drmgralloc = DrmGralloc::getInstance();
    int ret = drmgralloc->freeBuffer(native_buffer_);
    if(ret){
      HWC2_ALOGD_IF_WARN("buffer-id=0x%" PRIx64 " freeBuffer fail.", uBufferId_);
    }
  }
}

};