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

#ifndef DRM_HWC_TWO_H
#define DRM_HWC_TWO_H
#include "drmdisplaycompositor.h"
#include "drmlayer.h"
#include "resources/resourcemanager.h"
#include "vsyncworker.h"
#include "platform.h"
#include "rockchip/drmgralloc.h"
#include "rockchip/invalidateworker.h"
#include "utils/drmfence.h"
#include "rockchip/producer/drmvideoproducer.h"
#include "resources/resourcescache.h"

#include <android-base/unique_fd.h>
#include "drmbufferqueue.h"

#ifdef USE_LIBPQ
#include "Pq.h"
#endif
#include <hardware/hwcomposer2.h>

#include <utils/Timers.h>

#include <map>
#include <atomic>
#include <vector>

namespace android {

class DrmGralloc;
class DrmHwcTwo;
class GemHandle;
class ResourceManager;
class DrmDisplayCompositor;

static DrmHwcTwo *g_ctx = NULL;

#define MAX_NUM_BUFFER_SLOTS 32
#define MAX_NUM_FRAME_TIMESTAMP_CNT 20
class DrmHwcTwo : public hwc2_device_t {
 public:
  static int HookDevOpen(const struct hw_module_t *module, const char *name,
                         struct hw_device_t **dev);

  DrmHwcTwo();

  HWC2::Error Init();

  hwc2_drm_display_t* GetDisplayCtxPtr(hwc2_display_t display_id);

 private:

  class HwcLayer {
   public:
    HwcLayer(uint32_t layer_id, DrmDevice *drm){
      id_ = layer_id;
      drmGralloc_ = DrmGralloc::getInstance();
      drm_ = drm;
      bufferInfoMap_.clear();
      bSideband2_=false;
      sidebandStreamHandle_ = NULL;
      memset(&mSidebandInfo_, 0x00, sizeof(vt_sideband_data_t));
    };

    void clear(){
      buffer_ = NULL;
      if(!bSideband2_ && sidebandStreamHandle_ != NULL){
        int ret = drmGralloc_->freeBuffer(sidebandStreamHandle_);
        if(ret){
          HWC2_ALOGE("freeBuffer sidebandStreamHandle = %p fail, ret=%d",sidebandStreamHandle_,ret);
        }
        sidebandStreamHandle_ = NULL;
      }
    }

    HWC2::Composition sf_type() const {
      return mCurrentState.sf_type_;
    }
    HWC2::Composition validated_type() const {
      return mCurrentState.validated_type_;
    }
    void accept_type_change() {
      mCurrentState.sf_type_ = mCurrentState.validated_type_;
    }
    void set_validated_type(HWC2::Composition type) {
      mCurrentState.validated_type_ = type;
    }
    bool type_changed() const {
      return mCurrentState.sf_type_ != mCurrentState.validated_type_;
    }

    uint32_t z_order() const {
      return mCurrentState.z_order_;
    }

    typedef struct Hwc2LayerState{
      Hwc2LayerState& operator=(Hwc2LayerState& rhs){
        buffer_         = rhs.buffer_;
        sf_type_        = rhs.sf_type_;
        validated_type_ = rhs.validated_type_;
        alpha_          = rhs.alpha_;
        blending_       = rhs.blending_;
        transform_      = rhs.transform_;
        dataspace_      = rhs.dataspace_;
        source_crop_    = rhs.source_crop_;
        display_frame_  = rhs.display_frame_;
        cursor_x_       = rhs.cursor_x_;
        cursor_y_       = rhs.cursor_y_;
        color_          = rhs.color_;
        z_order_        = rhs.z_order_;
        sidebandStreamHandle_ = rhs.sidebandStreamHandle_;
        return *this;
      }

      bool operator==(Hwc2LayerState& rhs){
        if( buffer_         == rhs.buffer_ &&
            sf_type_        == rhs.sf_type_ &&
            validated_type_ == rhs.validated_type_ &&
            alpha_          == rhs.alpha_ &&
            blending_       == rhs.blending_ &&
            transform_      == rhs.transform_ &&
            dataspace_      == rhs.dataspace_ &&
            cursor_x_       == rhs.cursor_x_ &&
            cursor_y_       == rhs.cursor_y_ &&
            color_.r        == rhs.color_.r &&
            color_.g        == rhs.color_.g &&
            color_.b        == rhs.color_.b &&
            color_.a        == rhs.color_.a &&
            z_order_        == rhs.z_order_ &&
            sidebandStreamHandle_   == rhs.sidebandStreamHandle_ &&
            source_crop_.left       == rhs.source_crop_.left &&
            source_crop_.top        == rhs.source_crop_.top &&
            source_crop_.right      == rhs.source_crop_.right &&
            source_crop_.bottom     == rhs.source_crop_.bottom &&
            display_frame_.left   == rhs.display_frame_.left &&
            display_frame_.top    == rhs.display_frame_.top &&
            display_frame_.right  == rhs.display_frame_.right &&
            display_frame_.bottom == rhs.display_frame_.bottom)
        {
          return true;
        }
        return false;
      }
      // BufferHandle
      buffer_handle_t buffer_ = NULL;
      // Sidebande
      buffer_handle_t sidebandStreamHandle_ = NULL;
      // sf_type_ stores the initial type given to us by surfaceflinger,
      // validated_type_ stores the type after running ValidateDisplay
      HWC2::Composition sf_type_ = HWC2::Composition::Invalid;
      HWC2::Composition validated_type_ = HWC2::Composition::Invalid;

      float alpha_ = 1.0f;
      HWC2::BlendMode blending_ = HWC2::BlendMode::None;
      HWC2::Transform transform_ = HWC2::Transform::None;
      android_dataspace_t dataspace_ = HAL_DATASPACE_UNKNOWN;

      hwc_rect_t display_frame_;
      hwc_frect_t source_crop_;

      int32_t cursor_x_;
      int32_t cursor_y_;

      hwc_color_t color_;

      uint32_t z_order_ = 0;
    } Hwc2LayerState_t;


    buffer_handle_t buffer() {
      return buffer_;
    }

    void CacheBufferInfo(buffer_handle_t buffer) {
      buffer_ = buffer;
      mCurrentState.buffer_ = buffer;

      // Bufferinfo Cache
      uint64_t buffer_id;
      drmGralloc_->hwc_get_handle_buffer_id(buffer_, &buffer_id);

      // Get Buffer info
      const auto mapBuffer = bufferInfoMap_.find(buffer_id);
      if(mapBuffer == bufferInfoMap_.end()){
        // If bHasCache_ is true, the new buffer_id need to reset mapBuffer
        if(bHasCache_){
          HWC2_ALOGD_IF_VERBOSE("bHasCache=%d to reset bufferInfoMap_ BufferId=%" PRIx64 " Name=%s",
                               bHasCache_,buffer_id,pBufferInfo_->sLayerName_.c_str());
          bufferInfoMap_.clear();
          bHasCache_  = false;
        }

        // If size is too big, the new buffer_id need to reset mapBuffer
        if(bufferInfoMap_.size() > MAX_NUM_BUFFER_SLOTS){
          HWC2_ALOGD_IF_VERBOSE("MapSize=%zu too large to reset bufferInfoMap_ BufferId=%" PRIx64 " Name=%s",
                               bufferInfoMap_.size(),buffer_id,pBufferInfo_->sLayerName_.c_str());
          bufferInfoMap_.clear();
        }

        auto ret = bufferInfoMap_.emplace(std::make_pair(buffer_id, std::make_shared<LayerInfoCache>()));
        if(ret.second == false){
          HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ emplace fail! BufferHandle=%p",buffer_);
        }else{
          pBufferInfo_ = ret.first->second;
          pBufferInfo_->uBufferId_ = buffer_id;
          int ret = drmGralloc_->importBuffer(buffer_, &pBufferInfo_->native_buffer_);
          if(ret){
            HWC2_ALOGD_IF_WARN("buffer-id=0x%" PRIx64 " importBuffer fail.", buffer_id);
          }
          // Bug:#426310
          // 多路视频同时输出，SurfaceFlinger可能会频繁触发 buffer_handle_t import/release行为
          // 可能会导致HWC本地cache的fd失效，故需要本地dup dma-buffer-fd副本，确保fd有效
          pBufferInfo_->iFd_     = base::unique_fd(dup(drmGralloc_->hwc_get_handle_primefd(buffer_)));
          pBufferInfo_->iWidth_  = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_WIDTH);
          pBufferInfo_->iHeight_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT);
          pBufferInfo_->iStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_STRIDE);
          pBufferInfo_->iSize_   = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_SIZE);
          pBufferInfo_->iHeightStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT_STRIDE);
          pBufferInfo_->iByteStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_BYTE_STRIDE_WORKROUND);
          pBufferInfo_->iFormat_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_FORMAT);
          pBufferInfo_->iUsage_   = drmGralloc_->hwc_get_handle_usage(buffer_);
          pBufferInfo_->uFourccFormat_ = drmGralloc_->hwc_get_handle_fourcc_format(buffer_);
          pBufferInfo_->uModifier_ = drmGralloc_->hwc_get_handle_format_modifier(buffer_);
          drmGralloc_->hwc_get_handle_plane_bytes_stride(buffer_, pBufferInfo_->uByteStridePlanes_);
          drmGralloc_->hwc_get_handle_name(buffer_,pBufferInfo_->sLayerName_);
          layer_name_ = pBufferInfo_->sLayerName_;
          HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ size = %zu insert success! BufferId=%" PRIx64
                                "w=%d h=%d format=%d fourcc=%c%c%c%c Name=%s",
                               bufferInfoMap_.size(),buffer_id,
                               pBufferInfo_->iWidth_,
                               pBufferInfo_->iHeight_,
                               pBufferInfo_->iFormat_,
                               pBufferInfo_->uFourccFormat_,
                               pBufferInfo_->uFourccFormat_ >> 8,
                               pBufferInfo_->uFourccFormat_ >> 16,
                               pBufferInfo_->uFourccFormat_ >> 24,
                               pBufferInfo_->sLayerName_.c_str());
        }
      }else{
        bHasCache_ = true;
        pBufferInfo_ = mapBuffer->second;
        HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ size = %zu has cache! BufferId=%" PRIx64 " Name=%s",
                             bufferInfoMap_.size(),buffer_id,pBufferInfo_->sLayerName_.c_str());
      }

      if(mDrawingState.buffer_ != mCurrentState.buffer_){
          nsecs_t current_time = systemTime();
          qFrameTimestamp_.push(current_time);
          qFrameTimestampBack_.push(current_time);
          while(qFrameTimestamp_.size() > MAX_NUM_FRAME_TIMESTAMP_CNT){
            qFrameTimestamp_.pop();
            qFrameTimestampBack_.pop();
          }
        }
      // ALOGI("rk-debug Name=%s mFps=%f", pBufferInfo_->sLayerName_.c_str(), GetFps());;
    }

    void NoCacheBufferInfo(buffer_handle_t buffer) {
      // clear cache
      bufferInfoMap_.clear();
      bHasCache_  = false;

      buffer_ = buffer;
      mCurrentState.buffer_ = buffer;

      // Bufferinfo Cache
      uint64_t buffer_id;
      drmGralloc_->hwc_get_handle_buffer_id(buffer_, &buffer_id);
      pBufferInfo_ = std::make_shared<LayerInfoCache>();
      pBufferInfo_->uBufferId_ = buffer_id;
      int ret = drmGralloc_->importBuffer(buffer_, &pBufferInfo_->native_buffer_);
      if(ret){
        HWC2_ALOGD_IF_WARN("buffer-id=0x%" PRIx64 " importBuffer fail.", buffer_id);
      }
      pBufferInfo_->iFd_     = base::unique_fd(dup(drmGralloc_->hwc_get_handle_primefd(buffer_)));
      pBufferInfo_->iWidth_  = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_WIDTH);
      pBufferInfo_->iHeight_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT);
      pBufferInfo_->iStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_STRIDE);
      pBufferInfo_->iSize_   = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_SIZE);
      pBufferInfo_->iHeightStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT_STRIDE);
      pBufferInfo_->iByteStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_BYTE_STRIDE_WORKROUND);
      pBufferInfo_->iFormat_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_FORMAT);
      pBufferInfo_->iUsage_   = drmGralloc_->hwc_get_handle_usage(buffer_);
      pBufferInfo_->uFourccFormat_ = drmGralloc_->hwc_get_handle_fourcc_format(buffer_);
      pBufferInfo_->uModifier_ = drmGralloc_->hwc_get_handle_format_modifier(buffer_);
      drmGralloc_->hwc_get_handle_name(buffer_,pBufferInfo_->sLayerName_);
      layer_name_ = pBufferInfo_->sLayerName_;
      HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ size = %zu insert success! BufferId=%" PRIx64
                            " fd=%d w=%d h=%d format=%d fourcc=%c%c%c%c usage=%" PRIx64 " modifier=%" PRIx64 " Name=%s",
                            bufferInfoMap_.size(),buffer_id,
                            pBufferInfo_->iFd_.get(),
                            pBufferInfo_->iWidth_,
                            pBufferInfo_->iHeight_,
                            pBufferInfo_->iFormat_,
                            pBufferInfo_->uFourccFormat_,
                            pBufferInfo_->uFourccFormat_ >> 8,
                            pBufferInfo_->uFourccFormat_ >> 16,
                            pBufferInfo_->uFourccFormat_ >> 24,
                            pBufferInfo_->iUsage_,
                            pBufferInfo_->uModifier_,
                            pBufferInfo_->sLayerName_.c_str());

      if(mDrawingState.buffer_ != mCurrentState.buffer_){
        nsecs_t current_time = systemTime();
        qFrameTimestamp_.push(current_time);
        qFrameTimestampBack_.push(current_time);
        while(qFrameTimestamp_.size() > MAX_NUM_FRAME_TIMESTAMP_CNT){
          qFrameTimestamp_.pop();
          qFrameTimestampBack_.pop();
        }
      }
    }


    void set_output_buffer(buffer_handle_t buffer) {
      buffer_ = buffer;
      mCurrentState.buffer_ = buffer;

      // Bufferinfo Cache
      uint64_t buffer_id;
      drmGralloc_->hwc_get_handle_buffer_id(buffer_, &buffer_id);
      pBufferInfo_ = std::make_shared<LayerInfoCache>();
      int ret = drmGralloc_->importBuffer(buffer_, &pBufferInfo_->native_buffer_);
      if(ret){
        HWC2_ALOGD_IF_WARN("buffer-id=0x%" PRIx64 " importBuffer fail.", buffer_id);
      }
      pBufferInfo_->uBufferId_ = buffer_id;
      pBufferInfo_->iFd_     = base::unique_fd(dup(drmGralloc_->hwc_get_handle_primefd(buffer_)));
      pBufferInfo_->iWidth_  = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_WIDTH);
      pBufferInfo_->iHeight_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT);
      pBufferInfo_->iStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_STRIDE);
      pBufferInfo_->iSize_   = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_SIZE);
      pBufferInfo_->iHeightStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT_STRIDE);
      pBufferInfo_->iByteStride_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_BYTE_STRIDE_WORKROUND);
      pBufferInfo_->iFormat_ = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_FORMAT);
      pBufferInfo_->iUsage_   = drmGralloc_->hwc_get_handle_usage(buffer_);
      pBufferInfo_->uFourccFormat_ = drmGralloc_->hwc_get_handle_fourcc_format(buffer_);
      pBufferInfo_->uModifier_ = drmGralloc_->hwc_get_handle_format_modifier(buffer_);
      drmGralloc_->hwc_get_handle_name(buffer_,pBufferInfo_->sLayerName_);
      layer_name_ = pBufferInfo_->sLayerName_;
      HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ size = %zu insert success! BufferId=%" PRIx64
                            " fd=%d w=%d h=%d format=%d fourcc=%c%c%c%c usage=%" PRIx64 " modifier=%" PRIx64 " Name=%s",
                            bufferInfoMap_.size(),buffer_id,
                            pBufferInfo_->iFd_.get(),
                            pBufferInfo_->iWidth_,
                            pBufferInfo_->iHeight_,
                            pBufferInfo_->iFormat_,
                            pBufferInfo_->uFourccFormat_,
                            pBufferInfo_->uFourccFormat_ >> 8,
                            pBufferInfo_->uFourccFormat_ >> 16,
                            pBufferInfo_->uFourccFormat_ >> 24,
                            pBufferInfo_->iUsage_,
                            pBufferInfo_->uModifier_,
                            pBufferInfo_->sLayerName_.c_str());
    }

    int initOrGetGemhanleFromCache(DrmHwcLayer* drmHwcLayer) {
      if(pBufferInfo_ == NULL){
        HWC2_ALOGD_IF_VERBOSE("Id=%d pBufferInfo_ is NULL can't find BufferInfoCache!",id_);
        return -1;
      }

      // Bufferinfo Cache
      uint64_t buffer_id = pBufferInfo_->uBufferId_;
      if(!pBufferInfo_->gemHandle_.isValid()){
        pBufferInfo_->gemHandle_.InitGemHandle(pBufferInfo_->sLayerName_.c_str(),
                                               pBufferInfo_->iFd_.get(),
                                               buffer_id);
        pBufferInfo_->uGemHandle_ = pBufferInfo_->gemHandle_.GetGemHandle();
        drmHwcLayer->uGemHandle_ = pBufferInfo_->gemHandle_.GetGemHandle();
        HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ init GemHandle success! BufferId=%" PRIx64 " Name=%s GemHandle=%d ptr=%p",
                              buffer_id,pBufferInfo_->sLayerName_.c_str(),
                              drmHwcLayer->uGemHandle_,
                              drmHwcLayer);
        return 0;
      }

      drmHwcLayer->uGemHandle_ = pBufferInfo_->gemHandle_.GetGemHandle();
      HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ GemHandle cache! BufferId=%" PRIx64 " Name=%s  GemHandle=%d ptr=%p",
                            buffer_id,pBufferInfo_->sLayerName_.c_str(),
                            drmHwcLayer->uGemHandle_,
                            drmHwcLayer);
      return 0;
    }

    void setSidebandStream(buffer_handle_t stream) {
      if(stream != sidebandStreamHandle_){
        if(sidebandStreamHandle_ != NULL){
          int ret = drmGralloc_->freeBuffer(sidebandStreamHandle_);
          if(ret){
            ALOGE("freeBuffer sidebandStreamHandle = %p fail, ret=%d",sidebandStreamHandle_,ret);
          }
          sidebandStreamHandle_ = NULL;
        }

        if(stream != NULL){
          buffer_handle_t tempHandle;
          int ret = drmGralloc_->importBuffer(stream,&tempHandle);
          if(ret){
            ALOGE("importBuffer stream=%p, tempHandle=%p fail, ret=%d",stream,tempHandle,ret);
          }
          sidebandStreamHandle_ = tempHandle;
          mCurrentState.sidebandStreamHandle_ = tempHandle;
        }
      }
      // Bufferinfo Cache
      uint64_t buffer_id;
      drmGralloc_->hwc_get_handle_buffer_id(sidebandStreamHandle_, &buffer_id);

      bufferInfoMap_.clear();
      auto ret = bufferInfoMap_.emplace(std::make_pair(buffer_id, std::make_shared<LayerInfoCache>()));
      if(ret.second == false){
        HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ emplace fail! BufferHandle=%p",sidebandStreamHandle_);
      }else{
        pBufferInfo_ = ret.first->second;
        pBufferInfo_->uBufferId_ = buffer_id;
        pBufferInfo_->iFd_     = base::unique_fd(dup(drmGralloc_->hwc_get_handle_primefd(sidebandStreamHandle_)));
        pBufferInfo_->iWidth_  = drmGralloc_->hwc_get_handle_attibute(sidebandStreamHandle_,ATT_WIDTH);
        pBufferInfo_->iHeight_ = drmGralloc_->hwc_get_handle_attibute(sidebandStreamHandle_,ATT_HEIGHT);
        pBufferInfo_->iStride_ = drmGralloc_->hwc_get_handle_attibute(sidebandStreamHandle_,ATT_STRIDE);
        pBufferInfo_->iHeightStride_ = drmGralloc_->hwc_get_handle_attibute(sidebandStreamHandle_,ATT_HEIGHT_STRIDE);
        pBufferInfo_->iByteStride_ = drmGralloc_->hwc_get_handle_attibute(sidebandStreamHandle_,ATT_BYTE_STRIDE_WORKROUND);
        pBufferInfo_->iFormat_ = drmGralloc_->hwc_get_handle_attibute(sidebandStreamHandle_,ATT_FORMAT);
        pBufferInfo_->iUsage_   = drmGralloc_->hwc_get_handle_usage(sidebandStreamHandle_);
        pBufferInfo_->uFourccFormat_ = drmGralloc_->hwc_get_handle_fourcc_format(sidebandStreamHandle_);
        pBufferInfo_->uModifier_ = drmGralloc_->hwc_get_handle_format_modifier(sidebandStreamHandle_);
        drmGralloc_->hwc_get_handle_name(sidebandStreamHandle_,pBufferInfo_->sLayerName_);
        layer_name_ = pBufferInfo_->sLayerName_;
        pBufferInfo_->gemHandle_.InitGemHandle(pBufferInfo_->sLayerName_.c_str(), pBufferInfo_->iFd_, buffer_id);
        pBufferInfo_->uGemHandle_ = pBufferInfo_->gemHandle_.GetGemHandle();
        HWC2_ALOGD_IF_VERBOSE("bufferInfoMap_ size = %zu insert success! BufferId=%" PRIx64 " Name=%s",
                            bufferInfoMap_.size(),buffer_id,pBufferInfo_->sLayerName_.c_str());

      }
    }


    void set_acquire_fence(sp<AcquireFence> af) {
      acquire_fence_ = af;
    }

    const sp<AcquireFence> acquire_fence(){
      return acquire_fence_;
    }

    void set_release_fence(sp<ReleaseFence> rf) {
      release_fence_.add(rf);
    }

    const sp<ReleaseFence> release_fence(){
      return release_fence_.get();
    }

    const sp<ReleaseFence> back_release_fence(){
      return release_fence_.get_back();
    }

    uint32_t id(){ return id_; }
    // SidebandStream layer
    void PopulateSidebandLayer(DrmHwcLayer *layer, hwc2_drm_display_t* ctx);
    // Normal layer
    void PopulateNormalLayer(DrmHwcLayer *layer, hwc2_drm_display_t* ctx);
    void PopulateDrmLayer(hwc2_layer_t layer_id,
                          DrmHwcLayer *layer,
                          hwc2_drm_display_t* ctx,
                          uint32_t frame_no);

    void PopulateFB(hwc2_layer_t layer_id,
                    DrmHwcLayer *drmHwcLayer,
                    hwc2_drm_display_t* ctx,
                    uint32_t frame_no,
                    bool validate);

    const std::shared_ptr<LayerInfoCache> GetBufferInfo() { return pBufferInfo_;};
    void DumpLayerInfo(String8 &output);

    int DumpData();

    void EnableAfbc() { is_afbc_ = true;};
    void DisableAfbc() { is_afbc_ = false;};
    bool isAfbc() { return is_afbc_;};
    bool StateChange() {
      if(mCurrentState == mDrawingState){
        return false;
      }else{
        mDrawingState = mCurrentState;
        return true;
      }
    };

    bool isSidebandLayer() { return bSideband2_; }
    int getTunnelId() { return mSidebandInfo_.tunnel_id; }

    float GetFps(){
      nsecs_t current_time = systemTime();
      nsecs_t start_time = qFrameTimestamp_.front();
      while( !qFrameTimestamp_.empty() && (current_time - start_time > float(s2ns(1)) )){
        qFrameTimestamp_.pop();
        start_time = qFrameTimestamp_.front();
      }
      if(!qFrameTimestamp_.empty()){
        mFps_ =  qFrameTimestamp_.size() * float(s2ns(1)) / (current_time - start_time);
      }else{
        mFps_ = 0;
      }

      return mFps_;
    }

    float GetRealFps(){
      nsecs_t end_time = qFrameTimestampBack_.back();
      nsecs_t start_time = qFrameTimestampBack_.front();
      if(qFrameTimestampBack_.size() > 10){
        mRealFps_ =  qFrameTimestampBack_.size() * float(s2ns(1)) / (end_time - start_time);
        if(mRealMaxFps_ < mRealFps_){
          mRealMaxFps_ = (int)mRealFps_;
        }
      }else{
        mRealFps_ = 60;
      }

      return mRealFps_;
    }

    int GetRealMaxFps(){
      if(mRealMaxFps_ != -1){
        return mRealMaxFps_;
      }

      return 60;
    }

    int DoSvep(bool validate, DrmHwcLayer *drmHwcLayer);
    int DoPq(bool validate, DrmHwcLayer *drmHwcLayer, hwc2_drm_display_t* ctx);

    // Layer hooks
    HWC2::Error SetCursorPosition(int32_t x, int32_t y);
    HWC2::Error SetLayerBlendMode(int32_t mode);
    HWC2::Error SetLayerBuffer(buffer_handle_t buffer, int32_t acquire_fence);
    HWC2::Error SetLayerColor(hwc_color_t color);
    HWC2::Error SetLayerCompositionType(int32_t type);
    HWC2::Error SetLayerDataspace(int32_t dataspace);
    HWC2::Error SetLayerDisplayFrame(hwc_rect_t frame);
    HWC2::Error SetLayerPlaneAlpha(float alpha);
    HWC2::Error SetLayerSidebandStream(const native_handle_t *stream);
    HWC2::Error SetLayerSourceCrop(hwc_frect_t crop);
    HWC2::Error SetLayerSurfaceDamage(hwc_region_t damage);
    HWC2::Error SetLayerTransform(int32_t transform);
    HWC2::Error SetLayerVisibleRegion(hwc_region_t visible);
    HWC2::Error SetLayerZOrder(uint32_t z);

   private:
    // Hwc2Layer id
    uint32_t id_;
    // Buffer Handle
    buffer_handle_t buffer_ = NULL;
    // SidebandStream Handle
    bool bSideband2_=false;
    bool bSideband2Valid_=false;
    buffer_handle_t sidebandStreamHandle_ = NULL;
    // current state
    vt_sideband_data_t mSidebandInfo_;
    // current frame state
    Hwc2LayerState_t mCurrentState;
    // last frame state
    Hwc2LayerState_t mDrawingState;
    // Fence
    sp<AcquireFence> acquire_fence_ = AcquireFence::NO_FENCE;
    DeferredReleaseFence release_fence_;

    // Buffer info map
    bool bHasCache_ = false;
    std::map<uint64_t, std::shared_ptr<LayerInfoCache>> bufferInfoMap_;
    std::string layer_name_;
    bool is_afbc_;

    // Buffer info point
    std::shared_ptr<LayerInfoCache> pBufferInfo_ = NULL;

    // Hwc2Layer fps, for debug.
    std::queue<nsecs_t> qFrameTimestamp_;
    std::queue<nsecs_t> qFrameTimestampBack_;
    // 考虑世界时间的fps, 1s内不刷新则刷新率为0
    float mFps_ = 0;
    // 图层更新的fps，不考虑世界时间，提供已刷新的图层刷新率
    float mRealFps_ = 0;
    // 记录图层真实最高刷新率
    int mRealMaxFps_ = -1;

    // DRM Resource
    DrmGralloc *drmGralloc_;
    DrmDevice *drm_;
#ifdef USE_LIBPQ
    std::shared_ptr<DrmBufferQueue> bufferQueue_;
    Pq* pq_;
    bool bPqReady_;
    PqContext pqCtx_;
#endif
  };

  struct HwcCallback {
    HwcCallback(hwc2_callback_data_t d, hwc2_function_pointer_t f)
        : data(d), func(f) {
    }
    hwc2_callback_data_t data;
    hwc2_function_pointer_t func;
  };

  class HwcDisplay {
   public:
    HwcDisplay(ResourceManager *resource_manager, DrmDevice *drm,
               std::shared_ptr<Importer> importer, hwc2_display_t handle,
               HWC2::DisplayType type);
    HwcDisplay(const HwcDisplay &) = delete;
    HWC2::Error Init();

    HWC2::Error InitVirtual();

    HWC2::Error CheckStateAndReinit(bool clear_layer = false);

    HWC2::Error RegisterVsyncCallback(hwc2_callback_data_t data,
                                      hwc2_function_pointer_t func);
    HWC2::Error UnregisterVsyncCallback();

    HWC2::Error RegisterInvalidateCallback(hwc2_callback_data_t data,
                                      hwc2_function_pointer_t func);

    HWC2::Error UnregisterInvalidateCallback();

    int ClearDisplay();

    HWC2::Error CheckDisplayState();

    // HWC Hooks
    HWC2::Error AcceptDisplayChanges();
    HWC2::Error CreateLayer(hwc2_layer_t *layer);
    HWC2::Error DestroyLayer(hwc2_layer_t layer);
    HWC2::Error GetActiveConfig(hwc2_config_t *config);
    HWC2::Error GetChangedCompositionTypes(uint32_t *num_elements,
                                           hwc2_layer_t *layers,
                                           int32_t *types);
    HWC2::Error GetClientTargetSupport(uint32_t width, uint32_t height,
                                       int32_t format, int32_t dataspace);
    HWC2::Error GetColorModes(uint32_t *num_modes, int32_t *modes);
    HWC2::Error GetDisplayAttribute(hwc2_config_t config, int32_t attribute,
                                    int32_t *value);
    HWC2::Error GetDisplayConfigs(uint32_t *num_configs,
                                  hwc2_config_t *configs);
    HWC2::Error GetDisplayName(uint32_t *size, char *name);
    HWC2::Error GetDisplayRequests(int32_t *display_requests,
                                   uint32_t *num_elements, hwc2_layer_t *layers,
                                   int32_t *layer_requests);
    HWC2::Error GetDisplayType(int32_t *type);
    HWC2::Error GetDozeSupport(int32_t *support);
    HWC2::Error GetHdrCapabilities(uint32_t *num_types, int32_t *types,
                                   float *max_luminance,
                                   float *max_average_luminance,
                                   float *min_luminance);
    HWC2::Error GetReleaseFences(uint32_t *num_elements, hwc2_layer_t *layers,
                                 int32_t *fences);
    HWC2::Error PresentVirtualDisplay(int32_t *retire_fence);
    HWC2::Error PresentDisplay(int32_t *retire_fence);
    HWC2::Error SetActiveConfig(hwc2_config_t config);
    // RK:VRR
    HWC2::Error UpdateRefreshRate(hwc2_config_t config);
    HWC2::Error ChosePreferredConfig();
    HWC2::Error SetClientTarget(buffer_handle_t target, int32_t acquire_fence,
                                int32_t dataspace, hwc_region_t damage);
    HWC2::Error SetColorMode(int32_t mode);
    HWC2::Error SetColorTransform(const float *matrix, int32_t hint);
    HWC2::Error SetOutputBuffer(buffer_handle_t buffer, int32_t release_fence);
    HWC2::Error SetPowerMode(int32_t mode);
    HWC2::Error SyncPowerMode();
    HWC2::Error SetVsyncEnabled(int32_t enabled);
    HWC2::Error ValidateDisplay(uint32_t *num_types, uint32_t *num_requests);
    HWC2::Error ValidateVirtualDisplay(uint32_t *num_types, uint32_t *num_requests);

#ifdef ANDROID_S
	//composer 2.4
	HWC2::Error GetDisplayConnectionType(uint32_t *outType);
	HWC2::Error GetDisplayVsyncPeriod(hwc2_vsync_period_t *outVsyncPeriod);
#endif

    std::map<hwc2_layer_t, HwcLayer> &get_layers(){
        return layers_;
    }
    bool has_layer(hwc2_layer_t layer) {
      return layers_.count(layer) > 0;
    }
    HwcLayer &get_layer(hwc2_layer_t layer) {
      return layers_.at(layer);
    }

   int DumpDisplayInfo(String8 &output);
   int DumpDisplayLayersInfo(String8 &output);
   int DumpDisplayLayersInfo();
   int DumpAllLayerData();
   bool PresentFinish(void) { return present_finish_; };
   int HoplugEventTmeline();
   int UpdateDisplayMode();
   int UpdateDisplayInfo();
   int UpdateHdmiOutputFormat();
   int UpdateBCSH();
   int UpdateOverscan();
   int UpdateSidebandMode();
   int SwitchHdrMode();
   bool DisableHdrModeRK3588();
   bool DisableHdrMode();
   int EnableMetadataHdrMode(DrmHwcLayer& hdrLayer);
   int EnableHdrMode(DrmHwcLayer& hdrLayer);
   void UpdateSvepState();
   int ActiveModeChange(bool change);
   bool IsActiveModeChange();

   hwc2_drm_display_t* GetDisplayCtxPtr();
   // Static Screen opt function
   int UpdateTimerEnable();
   int UpdateTimerState(bool gles_comp);
   // SelfRefresh function
   int SelfRefreshEnable();
   int EntreStaticScreen(uint64_t refresh, int refresh_cnt);
   int InvalidateControl(uint64_t refresh, int refresh_cnt);
   int isVirtual() { return type_ == HWC2::DisplayType::Virtual;}


   private:
    HWC2::Error ValidatePlanes();
    HWC2::Error InitDrmHwcLayer();
    HWC2::Error CreateComposition();
    HWC2::Error ModifyHwcLayerDisplayFrame(bool only_fb_scale);
    int ImportBuffers();
    void AddFenceToRetireFence(int fd);
    int DoMirrorDisplay(int32_t *retire_fence);

    ResourceManager *resource_manager_;
    DrmDevice *drm_;
    std::shared_ptr<DrmDisplayCompositor> compositor_;
    std::shared_ptr<Importer> importer_;
    std::unique_ptr<Planner> planner_;

    std::vector<DrmHwcLayer> drm_hwc_layers_;
    std::vector<DrmCompositionPlane> composition_planes_;

    std::vector<PlaneGroup*> plane_group;

    VSyncWorker vsync_worker_;
    InvalidateWorker invalidate_worker_;
    DrmConnector *connector_ = NULL;
    DrmCrtc *crtc_ = NULL;
    std::vector<DrmMode> sf_modes_;
    hwc2_display_t handle_;
    HWC2::DisplayType type_;
    uint32_t layer_idx_ = 1;
    std::map<hwc2_layer_t, HwcLayer> layers_;
    HwcLayer client_layer_;
    // WriteBack 需要使用
    HwcLayer output_layer_;
    std::set<uint64_t> mHasResetBufferId_;

    int32_t color_mode_;
    bool init_success_;
    bool validate_success_;
    bool present_finish_;
    hwc2_drm_display_t ctx_;
    bool static_screen_timer_enable_;
    bool static_screen_opt_;
    bool force_gles_;
    bool bNeedSyncPMState_;
    HWC2::PowerMode mPowerMode_;
    int fb_blanked;
    int iLastLayerSize_;

    uint32_t frame_no_ = 0;
    uint64_t wb_frame_no_ = 0;
    SyncTimeline sync_timeline_;
    DeferredRetireFence d_retire_fence_;
    bool bDropFrame_;
    bool bLastSvepState_;
    bool bVrrDisplay_;
    bool bActiveModeChange_;

    bool bUseWriteBack_;
    int iLastTunnelId_=0;
  };


  enum PLUG_EVENT_TYPE{
    DRM_HOTPLUG_NONE = 0,
    DRM_HOTPLUG_PLUG_EVENT = 1,
    DRM_HOTPLUG_UNPLUG_EVENT = 2,
  };

  class DrmHotplugHandler : public DrmEventHandler {
   public:
    DrmHotplugHandler(DrmHwcTwo *hwc2, DrmDevice *drm)
        : hwc2_(hwc2), drm_(drm) {
    }
    void HdmiTvOnlyOne(PLUG_EVENT_TYPE hdmi_hotplug_state);
    void HandleEvent(uint64_t timestamp_us);
    void HandleResolutionSwitchEvent(int display_id);

   private:
    DrmHwcTwo *hwc2_;
    DrmDevice *drm_;
  };

  static DrmHwcTwo *toDrmHwcTwo(hwc2_device_t *dev) {
    return static_cast<DrmHwcTwo *>(dev);
  }

  template <typename PFN, typename T>
  static hwc2_function_pointer_t ToHook(T function) {
    static_assert(std::is_same<PFN, T>::value, "Incompatible fn pointer");
    return reinterpret_cast<hwc2_function_pointer_t>(function);
  }

  template <typename T, typename HookType, HookType func, typename... Args>
  static T DeviceHook(hwc2_device_t *dev, Args... args) {
    DrmHwcTwo *hwc = toDrmHwcTwo(dev);
    return static_cast<T>(((*hwc).*func)(std::forward<Args>(args)...));
  }

  template <typename HookType, HookType func, typename... Args>
  static int32_t DisplayHook(hwc2_device_t *dev, hwc2_display_t display_handle,
                             Args... args) {
    DrmHwcTwo *hwc = toDrmHwcTwo(dev);
    if(hwc->displays_.count(display_handle)){
      HwcDisplay &display = hwc->displays_.at(display_handle);
      return static_cast<int32_t>((display.*func)(std::forward<Args>(args)...));
    }else{
      return static_cast<int32_t>(HWC2::Error::BadDisplay);
    }
  }

  template <typename HookType, HookType func, typename... Args>
  static int32_t LayerHook(hwc2_device_t *dev, hwc2_display_t display_handle,
                           hwc2_layer_t layer_handle, Args... args) {
    DrmHwcTwo *hwc = toDrmHwcTwo(dev);
    if(hwc->displays_.count(display_handle)){
      HwcDisplay &display = hwc->displays_.at(display_handle);
      if(display.has_layer(layer_handle)){
        HwcLayer &layer = display.get_layer(layer_handle);
        return static_cast<int32_t>((layer.*func)(std::forward<Args>(args)...));
      }else{
        return static_cast<int32_t>(HWC2::Error::BadLayer);
      }
    }else{
      return static_cast<int32_t>(HWC2::Error::BadDisplay);
    }
  }

  // hwc2_device_t hooks
  static int HookDevClose(hw_device_t *dev);
  static void HookDevGetCapabilities(hwc2_device_t *dev, uint32_t *out_count,
                                     int32_t *out_capabilities);
  static hwc2_function_pointer_t HookDevGetFunction(struct hwc2_device *device,
                                                    int32_t descriptor);

  // Device functions
  HWC2::Error CreateVirtualDisplay(uint32_t width, uint32_t height,
                                   int32_t *format, hwc2_display_t *display);
  HWC2::Error DestroyVirtualDisplay(hwc2_display_t display);
  void Dump(uint32_t *size, char *buffer);
  uint32_t GetMaxVirtualDisplayCount();
  HWC2::Error RegisterCallback(int32_t descriptor, hwc2_callback_data_t data,
                               hwc2_function_pointer_t function);
  HWC2::Error CreateDisplay(hwc2_display_t displ, HWC2::DisplayType type);
  void HandleDisplayHotplug(hwc2_display_t displayid, int state);
  void HandleInitialHotplugState(DrmDevice *drmDevice);
  bool IsHasRegisterDisplayId(hwc2_display_t displayid);

  static void StaticScreenOptHandler(int sig){
    if (sig == SIGALRM)
      if(g_ctx!=NULL){
        HwcDisplay &display = g_ctx->displays_.at(0);
        display.EntreStaticScreen(60,1);
    }
    return;
};

  ResourceManager *resource_manager_;
  std::map<hwc2_display_t, HwcDisplay> displays_;
  std::map<HWC2::Callback, HwcCallback> callbacks_;
  std::string mDumpString;
  std::atomic<int> mVirtualDisplayCount_;
  // 通过 mHasRegisterDisplay_ 存储已向SurfaceFlinger注册的display
  std::set<hwc2_display_t> mHasRegisterDisplay_;
};
}  // namespace android
#endif // DRM_HWC_TWO_H
