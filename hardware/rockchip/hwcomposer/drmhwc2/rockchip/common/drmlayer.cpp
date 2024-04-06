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
#define LOG_TAG "hwc-drm-utils"

#include "drmlayer.h"
#include "platform.h"

#include <drm_fourcc.h>

#include <log/log.h>
#include <ui/GraphicBufferMapper.h>
#include <cutils/properties.h>

#define hwcMIN(x, y)			(((x) <= (y)) ?  (x) :  (y))
#define hwcMAX(x, y)			(((x) >= (y)) ?  (x) :  (y))

#ifndef IS_ALIGN
#define IS_ALIGN(val, align) (((val) & (align - 1)) == 0)
#endif

#ifndef ALIGN
#define ALIGN(value, base) (((value) + ((base)-1)) & ~((base)-1))
#endif

#ifndef ALIGN_DOWN
#define ALIGN_DOWN(value, base) (value & (~(base - 1)))
#endif

namespace android {

const hwc_drm_bo *DrmHwcBuffer::operator->() const {
  if (importer_ == NULL) {
    ALOGE("Access of non-existent BO");
    exit(1);
    return NULL;
  }
  return &bo_;
}

void DrmHwcBuffer::Clear() {
  if (importer_ != NULL) {
    importer_->ReleaseBuffer(&bo_);
    importer_ = NULL;
  }
}

int DrmHwcBuffer::ImportBuffer(buffer_handle_t handle, Importer *importer) {
  int ret = importer->ImportBuffer(handle, &bo_);
  if (ret)
    return ret;

  if (importer_ != NULL) {
    importer_->ReleaseBuffer(&bo_);
  }

  importer_ = importer;

  return 0;
}

int DrmHwcBuffer::SetBoInfo(uint32_t fd, uint32_t width,
                            uint32_t height, uint32_t height_stride,
                            uint32_t format, uint32_t hal_format,
                            uint64_t modifier,
                            uint64_t usage, uint32_t byte_stride,
                            uint32_t gem_handle, uint32_t offset[4],
                            std::vector<uint32_t> &plane_byte_stride){
  memset(&bo_,0x00,sizeof(struct hwc_drm_bo));
  bo_.fd = fd;
  bo_.width = width;
  bo_.height = height;
  bo_.height_stride = height_stride;
  bo_.usage = usage;
  bo_.hal_format = hal_format;
  bo_.format = format;
  bo_.modifier = modifier;
  bo_.byte_stride = byte_stride;
  bo_.gem_handles[0] = gem_handle;
  bo_.offsets[0] = offset[0];
  bo_.offsets[1] = offset[1];
  bo_.offsets[2] = offset[2];
  bo_.offsets[3] = offset[3];
  for(int i = 0; i < plane_byte_stride.size(); i++){
    bo_.pitches[i] = plane_byte_stride[i];
  }
  return 0;
}

int DrmHwcNativeHandle::CopyBufferHandle(buffer_handle_t handle, int width,
                                         int height, int layerCount, int format,
                                         uint64_t usage, int stride) {
  native_handle_t *handle_copy;
  GraphicBufferMapper &gm(GraphicBufferMapper::get());
  int ret;

#ifdef HWC2_USE_OLD_GB_IMPORT
  UNUSED(width);
  UNUSED(height);
  UNUSED(layerCount);
  UNUSED(format);
  UNUSED(usage);
  UNUSED(stride);
  ret = gm.importBuffer(handle, const_cast<buffer_handle_t *>(&handle_copy));
#else
  ret = gm.importBuffer(handle, width, height, layerCount, format, usage,
                        stride, const_cast<buffer_handle_t *>(&handle_copy));
#endif
  if (ret) {
    ALOGE("Failed to import buffer handle %d", ret);
    return ret;
  }

  Clear();

  handle_ = handle_copy;

  return 0;
}

DrmHwcNativeHandle::~DrmHwcNativeHandle() {
  Clear();
}

void DrmHwcNativeHandle::Clear() {
  if (handle_ != NULL) {
    GraphicBufferMapper &gm(GraphicBufferMapper::get());
    int ret = gm.freeBuffer(handle_);
    if (ret) {
      ALOGE("Failed to free buffer handle %d", ret);
    }
    handle_ = NULL;
  }
}

int DrmHwcLayer::ImportBuffer(Importer *importer) {
  uint32_t offsets[4] = {0};
#ifdef RK3528
  if(bIsPreScale_){
    offsets[0] = mMetadata_.offset[0];
    offsets[1] = mMetadata_.offset[1];
    offsets[2] = mMetadata_.offset[2];
    offsets[3] = mMetadata_.offset[3];
  }
#endif
  buffer.SetBoInfo(iFd_, iWidth_, iHeight_, iHeightStride_, uFourccFormat_,
                   iFormat_, uModifier_, iUsage, iByteStride_, uGemHandle_,
                   offsets, uByteStridePlanes_);
  int ret = buffer.ImportBuffer(sf_handle, importer);
  if (ret)
    return ret;

  const hwc_drm_bo *bo = buffer.operator->();

  // Fix YUV can't importBuffer bug.
  // layerCount is always 1 and pixel_stride is always 0.
  ret = handle.CopyBufferHandle(sf_handle, bo->width, bo->height, 1/*bo->layer_cnt*/,
                                bo->hal_format, bo->usage, 0/*bo->pixel_stride*/);
  if (ret)
    return ret;

  gralloc_buffer_usage = bo->usage;

  return 0;
}
int DrmHwcLayer::Init() {
  bYuv_ = IsYuvFormat(iFormat_,uFourccFormat_);
  bYuv10bit_ = Is10bitYuv(iFormat_,uFourccFormat_);
  bScale_  = IsScale(source_crop, display_frame, transform);
  iSkipLine_  = GetSkipLine();
  bAfbcd_ = IsAfbcModifier(uModifier_);
  bSkipLayer_ = IsSkipLayer();

  // HDR
  bHdr_ = IsHdr(iUsage, eDataSpace_);
  bMetadataHdr_ = IsMetadataHdr(iUsage);
  uColorSpace = GetColorSpace(eDataSpace_);
  uEOTF = GetEOTF(eDataSpace_);

#ifdef RK3528
  bIsPreScale_ = IsPreScaleVideo(iUsage);
  ModifyDisplayFrame();
#endif
  return 0;
}

int DrmHwcLayer::InitFromDrmHwcLayer(DrmHwcLayer *src_layer,
                                     Importer *importer) {
  blending = src_layer->blending;
  sf_handle = src_layer->sf_handle;
  acquire_fence = AcquireFence::NO_FENCE;
  display_frame = src_layer->display_frame;
  alpha = src_layer->alpha;
  source_crop = src_layer->source_crop;
  transform = src_layer->transform;
  return ImportBuffer(importer);
}

void DrmHwcLayer::SetBlend(HWC2::BlendMode blend) {
  switch (blend) {
    case HWC2::BlendMode::None:
      blending = DrmHwcBlending::kNone;
      break;
    case HWC2::BlendMode::Premultiplied:
      blending = DrmHwcBlending::kPreMult;
      break;
    case HWC2::BlendMode::Coverage:
      blending = DrmHwcBlending::kCoverage;
      break;
    default:
      ALOGE("Unknown blending mode b=%d", blend);
      blending = DrmHwcBlending::kNone;
      break;
  }
}

void DrmHwcLayer::SetSourceCrop(hwc_frect_t const &crop) {
  source_crop = crop;
}

void DrmHwcLayer::SetDisplayFrame(hwc_rect_t const &frame,
                                  hwc2_drm_display_t *ctx) {
  float left_scale   = 1;
  float right_scale  = 1;
  float top_scale    = 1;
  float bottom_scale = 1;

  // 保存SurfaceFlinger display frame 信息
  display_frame_sf.left   = frame.left;
  display_frame_sf.right  = frame.right;
  display_frame_sf.top    = frame.top;
  display_frame_sf.bottom = frame.bottom;


  if(!ctx->bStandardSwitchResolution){
    left_scale   = ctx->rel_xres / (float)ctx->framebuffer_width;
    right_scale  = ctx->rel_xres / (float)ctx->framebuffer_width;
    top_scale    = ctx->rel_yres / (float)ctx->framebuffer_height;
    bottom_scale = ctx->rel_yres / (float)ctx->framebuffer_height;
  }

  display_frame.left   = (int)(frame.left   * left_scale  ) + ctx->rel_xoffset;
  display_frame.right  = (int)(frame.right  * right_scale ) + ctx->rel_xoffset;
  display_frame.top    = (int)(frame.top    * top_scale   ) + ctx->rel_yoffset;
  display_frame.bottom = (int)(frame.bottom * bottom_scale) + ctx->rel_yoffset;
}

#define OVERSCAN_MIN_VALUE              (60)
#define OVERSCAN_MAX_VALUE              (100)
void DrmHwcLayer::ModifyDisplayFrameForOverscan(hwc2_drm_display_t *ctx){
  int left_margin = 100, right_margin= 100, top_margin = 100, bottom_margin = 100;
  sscanf(ctx->overscan_value, "overscan %d,%d,%d,%d", &left_margin,
                                                      &top_margin,
                                                      &right_margin,
                                                      &bottom_margin);

  float left_margin_f, right_margin_f, top_margin_f, bottom_margin_f;
  float lscale = 0, tscale = 0, rscale = 0, bscale = 0;
  int disp_old_l,disp_old_t,disp_old_r,disp_old_b;
  int dst_w = (int)(display_frame.right - display_frame.left);
  int dst_h = (int)(display_frame.bottom - display_frame.top);

  //limit overscan to (OVERSCAN_MIN_VALUE,OVERSCAN_MAX_VALUE)
  if (left_margin   < OVERSCAN_MIN_VALUE) left_margin   = OVERSCAN_MIN_VALUE;
  if (top_margin    < OVERSCAN_MIN_VALUE) top_margin    = OVERSCAN_MIN_VALUE;
  if (right_margin  < OVERSCAN_MIN_VALUE) right_margin  = OVERSCAN_MIN_VALUE;
  if (bottom_margin < OVERSCAN_MIN_VALUE) bottom_margin = OVERSCAN_MIN_VALUE;

  if (left_margin   > OVERSCAN_MAX_VALUE) left_margin   = OVERSCAN_MAX_VALUE;
  if (top_margin    > OVERSCAN_MAX_VALUE) top_margin    = OVERSCAN_MAX_VALUE;
  if (right_margin  > OVERSCAN_MAX_VALUE) right_margin  = OVERSCAN_MAX_VALUE;
  if (bottom_margin > OVERSCAN_MAX_VALUE) bottom_margin = OVERSCAN_MAX_VALUE;

  left_margin_f   = (float)(100 - left_margin   ) / 2;
  top_margin_f    = (float)(100 - top_margin    ) / 2;
  right_margin_f  = (float)(100 - right_margin  ) / 2;
  bottom_margin_f = (float)(100 - bottom_margin) / 2;

  lscale = ((float)left_margin_f   / 100);
  tscale = ((float)top_margin_f    / 100);
  rscale = ((float)right_margin_f  / 100);
  bscale = ((float)bottom_margin_f / 100);

  disp_old_l = display_frame.left;
  disp_old_t = display_frame.top;
  disp_old_r = display_frame.right;
  disp_old_b = display_frame.bottom;

  display_frame.left = ((int)(display_frame.left  * (1.0 - lscale - rscale)) + (int)(ctx->rel_xres * lscale));
  display_frame.top =  ((int)(display_frame.top   * (1.0 - tscale - bscale)) + (int)(ctx->rel_yres * tscale));
  dst_w -= ((int)(dst_w * lscale) + (int)(dst_w * rscale));
  dst_h -= ((int)(dst_h * tscale) + (int)(dst_h * bscale));
  display_frame.right  = display_frame.left + dst_w;
  display_frame.bottom = display_frame.top  + dst_h;

  HWC2_ALOGD_IF_VERBOSE("overscan(%d,%d,%d,%d) display_frame(%d,%d,%d,%d) => (%d,%d,%d,%d)",
    left_margin, top_margin, right_margin, bottom_margin,
    disp_old_l, disp_old_t, disp_old_r, disp_old_b,
    display_frame.left, display_frame.top, display_frame.right, display_frame.bottom);

  bScale_  = IsScale(source_crop, display_frame, transform);
  return;
}

void DrmHwcLayer::SetDisplayFrameMirror(hwc_rect_t const &frame) {
  display_frame_mirror = frame;
}

void DrmHwcLayer::SetTransform(HWC2::Transform sf_transform) {
  switch (sf_transform) {
      case HWC2::Transform::None:
        transform = DRM_MODE_ROTATE_0;
        break;
      case HWC2::Transform::FlipH :
        transform = DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X;
        break;
      case HWC2::Transform::FlipV :
        transform = DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y;
        break;
      case HWC2::Transform::Rotate90 :
        transform = DRM_MODE_ROTATE_90;
        break;
      case HWC2::Transform::Rotate180 :
        //transform = DRM_MODE_ROTATE_180;
        transform = DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_REFLECT_Y;
        break;
      case HWC2::Transform::Rotate270 :
        transform = DRM_MODE_ROTATE_270;
        break;
      case HWC2::Transform::FlipHRotate90 :
        transform = DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_ROTATE_90;
        break;
      case HWC2::Transform::FlipVRotate90 :
        transform = DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y | DRM_MODE_ROTATE_90;
        break;
      default:
        transform = -1;
        ALOGE_IF(LogLevel(DBG_DEBUG),"Unknow sf transform 0x%x",sf_transform);
  }
}
bool DrmHwcLayer::IsYuvFormat(int format, uint32_t fourcc_format){

  switch(fourcc_format){
    case DRM_FORMAT_NV12:
    case DRM_FORMAT_NV12_10:
    case DRM_FORMAT_NV21:
    case DRM_FORMAT_NV16:
    case DRM_FORMAT_NV61:
    case DRM_FORMAT_YUV420:
    case DRM_FORMAT_YVU420:
    case DRM_FORMAT_YUV422:
    case DRM_FORMAT_YVU422:
    case DRM_FORMAT_YUV444:
    case DRM_FORMAT_YVU444:
    case DRM_FORMAT_UYVY:
    case DRM_FORMAT_VYUY:
    case DRM_FORMAT_YUYV:
    case DRM_FORMAT_YVYU:
    case DRM_FORMAT_YUV420_8BIT:
    case DRM_FORMAT_YUV420_10BIT:
      return true;
    default:
      break;
  }

  switch(format){
    case HAL_PIXEL_FORMAT_YCrCb_NV12:
    case HAL_PIXEL_FORMAT_YCrCb_NV12_10:
    case HAL_PIXEL_FORMAT_YCrCb_NV12_VIDEO:
    case HAL_PIXEL_FORMAT_YCbCr_422_SP_10:
    case HAL_PIXEL_FORMAT_YCrCb_420_SP_10:
    case HAL_PIXEL_FORMAT_YCBCR_422_I:
    case HAL_PIXEL_FORMAT_YUV420_8BIT_I:
    case HAL_PIXEL_FORMAT_YUV420_10BIT_I:
    case HAL_PIXEL_FORMAT_Y210:
      return true;
    default:
      return false;
  }
}

bool DrmHwcLayer::Is10bitYuv(int format,uint32_t fourcc_format){
  switch(fourcc_format){
    case DRM_FORMAT_NV12_10:
    case DRM_FORMAT_YUV420_10BIT:
      return true;
    default:
      break;
  }

  switch(format){
    case HAL_PIXEL_FORMAT_YCrCb_NV12_10:
    case HAL_PIXEL_FORMAT_YCbCr_422_SP_10:
    case HAL_PIXEL_FORMAT_YCrCb_420_SP_10:
    case HAL_PIXEL_FORMAT_YUV420_10BIT_I:
      return true;
    default:
      return false;
  }
}

#ifdef RK3528
int DrmHwcLayer::SwitchPreScaleBufferInfo(){
 DrmGralloc* gralloc =  DrmGralloc::getInstance();
  if(gralloc == NULL){
    return -1;
  }

  // sf_handle 为 null 并且不是 SidebandHandle
  if((sf_handle == NULL && !bSidebandStreamLayer_) || !bYuv_){
    return -1;
  }

  storePreScaleInfo_.valid_       = true;
  storePreScaleInfo_.sf_handle    = sf_handle;
  storePreScaleInfo_.transform    = transform;
  storePreScaleInfo_.source_crop  = source_crop;
  storePreScaleInfo_.display_frame  = display_frame;
  storePreScaleInfo_.iFd_         = iFd_;
  storePreScaleInfo_.iFormat_     = iFormat_;
  storePreScaleInfo_.iWidth_      = iWidth_;
  storePreScaleInfo_.iHeight_     = iHeight_;
  storePreScaleInfo_.iStride_     = iStride_;
  storePreScaleInfo_.iHeightStride_ = iHeightStride_;
  storePreScaleInfo_.iByteStride_ = iByteStride_;
  storePreScaleInfo_.iSize_       = iSize_;
  storePreScaleInfo_.iUsage       = iUsage;
  storePreScaleInfo_.uFourccFormat_ = uFourccFormat_;
  storePreScaleInfo_.uModifier_     = uModifier_;
  storePreScaleInfo_.sLayerName_    = sLayerName_;
  storePreScaleInfo_.uBufferId_    = uBufferId_;
  storePreScaleInfo_.uGemHandle_   = uGemHandle_;

  metadata_for_rkvdec_scaling_t* metadata = NULL;
  gralloc->lock_rkvdec_scaling_metadata(sf_handle, &metadata);
  HWC2_ALOGD_IF_INFO("lock_rkvdec_scaling_metadata sf_handle=%p metadata=%p", sf_handle, metadata);
  if(metadata != NULL){
    metadata->requestMask = 1;

    if(metadata->replyMask > 0){
      bIsPreScale_ = true;
      memcpy(&mMetadata_, metadata, sizeof(metadata_for_rkvdec_scaling_t));

      hwc_frect source_crop;
      source_crop.top    = metadata->srcTop;
      source_crop.left   = metadata->srcLeft;
      source_crop.right  = metadata->srcRight;
      source_crop.bottom = metadata->srcBottom;
      SetSourceCrop(source_crop);

      iWidth_  = metadata->width;
      iHeight_ = metadata->height;
      iStride_ = metadata->pixel_stride;
      iFormat_ = metadata->format;
      iUsage   = metadata->usage;
      iByteStride_     = metadata->byteStride[0];
      uModifier_       = metadata->modifier;
      uFourccFormat_ = gralloc->hwc_get_fourcc_from_hal_format(metadata->format);
      Init();
    }

    // 打印参数
    HWC2_ALOGD_IF_INFO("Name=%s metadata = %p", sLayerName_.c_str(), metadata);
    HWC2_ALOGD_IF_INFO("version=0x%" PRIx64 " requestMask=0x%" PRIx64" "
                      "replyMask=0x%" PRIx64 " BufferId=0x%" PRIx64,
                        metadata->version,
                        metadata->requestMask,
                        metadata->replyMask,
                        uBufferId_);
    HWC2_ALOGD_IF_INFO("w=%d h=%d s=%d f=%d m=0x%" PRIx64 " usage=0x%x ",
                                                        metadata->width,
                                                        metadata->height,
                                                        metadata->pixel_stride,
                                                        metadata->format,
                                                        metadata->modifier,
                                                        metadata->usage);
    HWC2_ALOGD_IF_INFO("crop=(%d,%d,%d,%d) ", metadata->srcLeft,
                                      metadata->srcTop,
                                      metadata->srcRight,
                                      metadata->srcBottom);
    HWC2_ALOGD_IF_INFO("layer_cnt=%d offset=%d,%d,%d,%d byteStride=%d,%d,%d,%d) ",
                                      metadata->layer_cnt,
                                      metadata->offset[0],
                                      metadata->offset[1],
                                      metadata->offset[2],
                                      metadata->offset[3],
                                      metadata->byteStride[0],
                                      metadata->byteStride[1],
                                      metadata->byteStride[2],
                                      metadata->byteStride[3]);
    gralloc->unlock_rkvdec_scaling_metadata(sf_handle);
  }

  // 还未获取到预缩小Buffer
  if(bIsPreScale_ == false){
    iWidth_        = iWidth_ / 2;
    iHeight_       = iHeight_ / 2;
    iStride_       = iStride_ / 2;
    iHeightStride_ = iHeightStride_ / 2;
    iByteStride_   = iByteStride_ / 2;
    iSize_         = iSize_ / 2;
    uModifier_     = 0;
    uFourccFormat_ = DRM_FORMAT_NV12;

    // source_crop.left   = source_crop.left;
    // source_crop.top    = source_crop.top;
    source_crop.right  = source_crop.right / 2;
    source_crop.bottom = source_crop.bottom / 2;

    Init();
    HWC2_ALOGD_IF_DEBUG(
          "PreScale : LayerId[%u] Fourcc=%c%c%c%c Buf[w,h,s,hs,size]=[%4d,%4d,%4d,%4d,%4d]  src=[%5.0f,%5.0f,%5.0f,%5.0f] dis=[%4d,%4d,%4d,%4d] Transform=%-8.8s(0x%x)\n"
          "                       Fourcc=%c%c%c%c Buf[w,h,s,hs,size]=[%4d,%4d,%4d,%4d,%4d]  src=[%5.0f,%5.0f,%5.0f,%5.0f] dis=[%4d,%4d,%4d,%4d] Transform=%-8.8s(0x%x)\n",
                uId_,
                storePreScaleInfo_.uFourccFormat_,storePreScaleInfo_.uFourccFormat_>>8,
                storePreScaleInfo_.uFourccFormat_>>16,storePreScaleInfo_.uFourccFormat_>>24,
                storePreScaleInfo_.iWidth_,storePreScaleInfo_.iHeight_,storePreScaleInfo_.iStride_,
                storePreScaleInfo_.iHeightStride_,storePreScaleInfo_.iSize_,
                storePreScaleInfo_.source_crop.left,storePreScaleInfo_.source_crop.top,
                storePreScaleInfo_.source_crop.right,storePreScaleInfo_.source_crop.bottom,
                storePreScaleInfo_.display_frame.left,storePreScaleInfo_.display_frame.top,
                storePreScaleInfo_.display_frame.right,storePreScaleInfo_.display_frame.bottom,
                TransformToString(storePreScaleInfo_.transform).c_str(),storePreScaleInfo_.transform,
                uFourccFormat_,uFourccFormat_>>8,uFourccFormat_>>16,uFourccFormat_>>24,
                iWidth_,iHeight_,iStride_,iHeightStride_,iSize_,
                source_crop.left,source_crop.top,source_crop.right,source_crop.bottom,
                display_frame.left,display_frame.top,display_frame.right,display_frame.bottom,
                TransformToString(transform).c_str(),transform);
  }

  return 0;

}

int DrmHwcLayer::ResetInfoFromPreScaleStore(){
  if(!storePreScaleInfo_.valid_){
    HWC2_ALOGE("ResetInfoFromStore fail, There may be some errors.");
    return -1;
  }

  // 关闭解码预缩小
 DrmGralloc* gralloc =  DrmGralloc::getInstance();
  if(gralloc == NULL){
    return -1;
  }
  metadata_for_rkvdec_scaling_t* metadata = NULL;
  gralloc->lock_rkvdec_scaling_metadata(sf_handle, &metadata);
  HWC2_ALOGD_IF_INFO("lock_rkvdec_scaling_metadata sf_handle=%p metadata=%p", sf_handle, metadata);
  if(metadata != NULL){
    bIsPreScale_ = false;
    metadata->requestMask = 2;
    memset(&mMetadata_, 0x0, sizeof(metadata_for_rkvdec_scaling_t));

    // 打印参数
    HWC2_ALOGD_IF_INFO("Name=%s metadata = %p", sLayerName_.c_str(), metadata);
    HWC2_ALOGD_IF_INFO("version=0x%" PRIx64 " requestMask=0x%" PRIx64" "
                      "replyMask=0x%" PRIx64 " BufferId=0x%" PRIx64,
                        metadata->version,
                        metadata->requestMask,
                        metadata->replyMask,
                        uBufferId_);
    gralloc->unlock_rkvdec_scaling_metadata(sf_handle);
  }

  sf_handle    = storePreScaleInfo_.sf_handle;
  transform    = storePreScaleInfo_.transform;
  source_crop  = storePreScaleInfo_.source_crop;
  iFd_         = storePreScaleInfo_.iFd_;
  iFormat_     = storePreScaleInfo_.iFormat_;
  iWidth_      = storePreScaleInfo_.iWidth_ ;
  iHeight_     = storePreScaleInfo_.iHeight_;
  iStride_     = storePreScaleInfo_.iStride_;
  iByteStride_ = storePreScaleInfo_.iByteStride_;
  iUsage       = storePreScaleInfo_.iUsage;
  uFourccFormat_ = storePreScaleInfo_.uFourccFormat_;
  uModifier_     = storePreScaleInfo_.uModifier_;
  sLayerName_    = storePreScaleInfo_.sLayerName_;
  uBufferId_     = storePreScaleInfo_.uBufferId_;
  uGemHandle_    = storePreScaleInfo_.uGemHandle_;
  bIsPreScale_ = false;
  Init();
  HWC2_ALOGD_IF_DEBUG(
             "PreScale reset:DrmHwcLayer[%4u] Buffer[w/h/s/format]=[%4d,%4d,%4d,%4d] Fourcc=%c%c%c%c Transform=%-8.8s(0x%x) Blend[a=%d]=%-8.8s "
             "source_crop[l,t,r,b]=[%5.0f,%5.0f,%5.0f,%5.0f] display_frame[l,t,r,b]=[%4d,%4d,%4d,%4d],skip=%d,afbcd=%d,gles=%d\n",
             uId_,iWidth_,iHeight_,iStride_,iFormat_,uFourccFormat_,uFourccFormat_>>8,uFourccFormat_>>16,uFourccFormat_>>24,
             TransformToString(transform).c_str(),transform,alpha,BlendingToString(blending).c_str(),
             source_crop.left,source_crop.top,source_crop.right,source_crop.bottom,
             display_frame.left,display_frame.top,display_frame.right,display_frame.bottom,bSkipLayer_,bAfbcd_,bGlesCompose_);

  memset(&storePreScaleInfo_, 0x00, sizeof(storePreScaleInfo_));
  storePreScaleInfo_.valid_ = false;

  return 0;
}

void DrmHwcLayer::ModifyDisplayFrame(){
  if(gIsRK3528()){
    if(!bYuv_){
      return;
    }
    char value_yuv[PROPERTY_VALUE_MAX];
    int scaleMode = 0;
    property_get("persist.vendor.video.cvrs",value_yuv, "0");
    scaleMode = atoi(value_yuv);
    if(scaleMode > 0){
      float s_letf, s_top, s_right, s_bottom;
      float s_width,s_height;
      int d_letf, d_top, d_right, d_bottom;
      int d_width,d_height;

      s_letf   = source_crop.left;
      s_top    = source_crop.top;
      s_right  = source_crop.right;
      s_bottom = source_crop.bottom;
      s_width  = s_right - s_letf;
      s_height = s_bottom - s_top;

      d_letf   = display_frame.left;
      d_top    = display_frame.top;
      d_right  = display_frame.right;
      d_bottom = display_frame.bottom;
      d_width  = d_right - d_letf;
      d_height = d_bottom - d_top;

      switch (scaleMode){
          case VIDEO_SCALE_AUTO_SCALE :
              if(s_width * d_height > s_height * d_width){
                  d_top += ( d_height - s_height * d_width / s_width ) / 2;
                  d_bottom -= ( d_height - s_height * d_width / s_width ) / 2;
              }else{
                  d_letf += ( d_width - s_width * d_height / s_height) / 2;
                  d_right -= ( d_width - s_width * d_height / s_height) / 2;
              }
              break;
          case VIDEO_SCALE_4_3_SCALE :
              if(4 * d_height  < 3 * d_width){
                  d_letf += (d_width - d_height * 4 / 3) / 2;
                  d_right -= (d_width - d_height * 4 / 3) / 2;
              }else if(4 * d_height  > 3 * d_width){
                  d_top += (d_height - d_width * 3 / 4) / 2;
                  d_bottom -= (d_height - d_width * 3 / 4) / 2;
              }
              break;
          case VIDEO_SCALE_16_9_SCALE :
              if(16 * d_height  < 9 * d_width){
                  d_letf += (d_width - d_height * 16 / 9) / 2;
                  d_right -= (d_width - d_height * 16 / 9) / 2;
              }else if(16 * d_height  > 9 * d_width){
                  d_top += (d_height - d_width * 9 / 16) / 2;
                  d_bottom -= (d_height - d_width * 9 / 16) / 2;
              }
              break;
          case VIDEO_SCALE_ORIGINAL :
              if(s_width > d_width){
                  // d_letf = 0;
                  //d_right = d_right;
              }else{
                  d_letf = (d_width - s_width) / 2;
                  d_right -= (d_width - s_width) / 2;
              }
              if(s_height > d_height){
                  // d_top = 0;
                  //d_bottom = d_bottom;
              }else{
                  d_top = (d_height - s_height) / 2;
                  d_bottom -= (d_height - s_height ) / 2;
              }
              break;
          default :
              ALOGE("ScaleMode[%d] is invalid ",scaleMode);
              return;
      }
      HWC2_ALOGD_IF_DEBUG("Video area change [%d,%d,%d,%d]:[%d,%d,%d,%d] => [%d,%d,%d,%d]",
      (int)source_crop.left,(int)source_crop.top,(int)source_crop.right,(int)source_crop.bottom,
      display_frame.left,display_frame.top,display_frame.right,display_frame.bottom,
      d_letf,d_top,d_right,d_bottom);

      display_frame.left = d_letf;
      display_frame.top = d_top;
      display_frame.right = d_right;
      display_frame.bottom = d_bottom;
      bScale_  = IsScale(source_crop, display_frame, transform);
    }
  }
}

bool DrmHwcLayer::IsPreScaleVideo(uint64_t usage){
  // RK3528 usage 0x01000000U 认为是 MetadataHdr 图层
  // 定义位于 Android 9.0 libhardware/../gralloc.h GRALLOC_USAGE_RKVDEC_SCALING
  if(gIsRK3528()){
    if(((usage & 0x01000000U) > 0)){
      return true;
    }
  }
  return false;
}

#endif

bool DrmHwcLayer::IsScale(hwc_frect_t &source_crop, hwc_rect_t &display_frame, int transform){
  int src_w, src_h, dst_w, dst_h;
  src_w = (int)(source_crop.right - source_crop.left);
  src_h = (int)(source_crop.bottom - source_crop.top);
  dst_w = (int)(display_frame.right - display_frame.left);
  dst_h = (int)(display_frame.bottom - display_frame.top);

  if((transform == DrmHwcTransform::kRotate90) || (transform == DrmHwcTransform::kRotate270)){
    if(bYuv_){
        //rga need this alignment.
        src_h = ALIGN_DOWN(src_h, 8);
        src_w = ALIGN_DOWN(src_w, 2);
    }
    fHScaleMul_ = (src_h * 1.0)/(dst_w);
    fVScaleMul_ =  (src_w * 1.0)/(dst_h);
  } else {
    fHScaleMul_ = (src_w * 1.0)/(dst_w);
    fVScaleMul_ = (src_h * 1.0)/(dst_h);
  }
  return (fHScaleMul_ != 1.0 ) || ( fVScaleMul_ != 1.0);
}

bool DrmHwcLayer::IsMetadataHdr(uint64_t usage){
  // RK3528 usage 0x02000000 认为是 MetadataHdr 图层
  // 定义位于 Android 9.0 libhardware/../gralloc.h GRALLOC_USAGE_DYNAMIC_HDR
  if(gIsRK3528()){
    if(((usage & 0x02000000) > 0)){
      return true;
    }
  }
  return false;
}

bool DrmHwcLayer::IsHdr(uint64_t usage, android_dataspace_t dataspace){
  // RK3528 usage 0x02000000 为 GRALLOC_USAGE_DYNAMIC_HDR
  // 与其他平台存在冲突，故排除RK3528平台
  if(!gIsRK3528()){
    if(((usage & 0x0F000000) == HDR_ST2084_USAGE ||
        (usage & 0x0F000000) == HDR_HLG_USAGE)){
      return true;
    }
  }

  if(((dataspace & HAL_DATASPACE_TRANSFER_ST2084) == HAL_DATASPACE_TRANSFER_ST2084) ||
     ((dataspace & HAL_DATASPACE_TRANSFER_HLG) == HAL_DATASPACE_TRANSFER_HLG)){
    return true;
  }

  return false;
}
bool DrmHwcLayer::IsAfbcModifier(uint64_t modifier){
  if(bFbTarget_){
    return hwc_get_int_property("vendor.gralloc.no_afbc_for_fb_target_layer","0") == 0;
  }else
    return AFBC_FORMAT_MOD_BLOCK_SIZE_16x16 == (modifier & AFBC_FORMAT_MOD_BLOCK_SIZE_16x16);             // for Midgard gralloc r14
}

bool DrmHwcLayer::IsSkipLayer(){
  if(bSidebandStreamLayer_){
    return false;
  }

  return (!sf_handle ? true:false);
}

int DrmHwcLayer::GetSkipLine(){
    int skip_line = 0;
    if(bYuv_){
      if(iWidth_ >= 3840){
        if(fHScaleMul_ > 1.0 || fVScaleMul_ > 1.0){
            skip_line = 2;
        }
        if(iFormat_ == HAL_PIXEL_FORMAT_YCrCb_NV12_10 && fHScaleMul_ >= (3840 / 1600)){
            skip_line = 3;
        }
      }
      int video_skipline = property_get_int32("vendor.video.skipline", 0);
      if (video_skipline == 2){
        skip_line = 2;
      }else if(video_skipline == 3){
        skip_line = 3;
      }
    }
    return (skip_line >= 0 ? skip_line : 0);
}

#define CONTAIN_VALUE(value,mask) ((dataspace & mask) == value)
drm_colorspace DrmHwcLayer::GetColorSpace(android_dataspace_t dataspace){

  drm_colorspace output_colorspace;
  if (CONTAIN_VALUE(HAL_DATASPACE_STANDARD_BT2020, HAL_DATASPACE_STANDARD_MASK)){
      // BT2020
      if(gIsDrmVerison6_1()){
        output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT2020;
      }else{
        output_colorspace.colorspace_kernel_510_ = V4L2_COLORSPACE_BT2020;
      }
      return output_colorspace;
  }else if (CONTAIN_VALUE(HAL_DATASPACE_STANDARD_BT601_625, HAL_DATASPACE_STANDARD_MASK) &&
          CONTAIN_VALUE(HAL_DATASPACE_TRANSFER_SMPTE_170M, HAL_DATASPACE_TRANSFER_MASK)){

      // BT601 确定，下面判断色彩范围
      if (CONTAIN_VALUE(HAL_DATASPACE_RANGE_FULL, HAL_DATASPACE_RANGE_MASK)){
        // BT601 Full range
        if(gIsDrmVerison6_1()){
          output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT601;
          output_colorspace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_FULL_RANGE;
        }else{
          output_colorspace.colorspace_kernel_510_ = V4L2_COLORSPACE_JPEG;
        }
      }else if (CONTAIN_VALUE(HAL_DATASPACE_RANGE_LIMITED, HAL_DATASPACE_RANGE_MASK)){
        // BT601 Limit range
        if(gIsDrmVerison6_1()){
          output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT601;
          output_colorspace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_LIMITED_RANGE;
        }else{
          output_colorspace.colorspace_kernel_510_ = V4L2_COLORSPACE_SMPTE170M;
        }
      }
      return output_colorspace;
  }
  else if (CONTAIN_VALUE(HAL_DATASPACE_STANDARD_BT601_525, HAL_DATASPACE_STANDARD_MASK) &&
          CONTAIN_VALUE(HAL_DATASPACE_TRANSFER_SMPTE_170M, HAL_DATASPACE_TRANSFER_MASK) &&
          CONTAIN_VALUE(HAL_DATASPACE_RANGE_LIMITED, HAL_DATASPACE_RANGE_MASK)){
            // BT601 Limit range
            if(gIsDrmVerison6_1()){
              output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT601;
              output_colorspace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_LIMITED_RANGE;
            }else{
              output_colorspace.colorspace_kernel_510_ = V4L2_COLORSPACE_SMPTE170M;
            }
            return output_colorspace;
  }
  else if (CONTAIN_VALUE(HAL_DATASPACE_STANDARD_BT709, HAL_DATASPACE_STANDARD_MASK) &&
      CONTAIN_VALUE(HAL_DATASPACE_TRANSFER_SMPTE_170M, HAL_DATASPACE_TRANSFER_MASK) &&
      CONTAIN_VALUE(HAL_DATASPACE_RANGE_LIMITED, HAL_DATASPACE_RANGE_MASK)){
        // BT709 Limit range
        if(gIsDrmVerison6_1()){
          output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT709;
          output_colorspace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_LIMITED_RANGE;
        }else{
          output_colorspace.colorspace_kernel_510_ = V4L2_COLORSPACE_REC709;
        }
        return output_colorspace;
  }
  else if (CONTAIN_VALUE(HAL_DATASPACE_TRANSFER_SRGB, HAL_DATASPACE_TRANSFER_MASK)){
        // BT709 Limit range
        if(gIsDrmVerison6_1()){
          output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT709;
          output_colorspace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_FULL_RANGE;
        }else{
          output_colorspace.colorspace_kernel_510_ = V4L2_COLORSPACE_SRGB;
        }
        return output_colorspace;
  }
  /*
    * Default colorspace, i.e. let the driver figure it out.
    * Can only be used with video capture.
    * CSC：RGB : BT709 Full range
    *      YUV : BT601 limit range
    */
  if(gIsDrmVerison6_1()){
    if(bYuv_ == true){
      output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT601;
      output_colorspace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_LIMITED_RANGE;
    }else{
      output_colorspace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT709;
      output_colorspace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_FULL_RANGE;
    }
  }else{
    output_colorspace.colorspace_kernel_510_ = V4L2_COLORSPACE_DEFAULT;
  }
  return output_colorspace;
}

supported_eotf_type DrmHwcLayer::GetEOTF(android_dataspace_t dataspace){
  if(bYuv_){
    if((dataspace & HAL_DATASPACE_TRANSFER_MASK) == HAL_DATASPACE_TRANSFER_ST2084){
        ALOGD_IF(LogLevel(DBG_VERBOSE),"%s:line=%d has st2084",__FUNCTION__,__LINE__);
        return SMPTE_ST2084;
    }else if((dataspace & HAL_DATASPACE_TRANSFER_MASK) == HAL_DATASPACE_TRANSFER_HLG){
        ALOGD_IF(LogLevel(DBG_VERBOSE),"%s:line=%d has HLG",__FUNCTION__,__LINE__);
        return HLG;
    }else{
        //ALOGE("Unknow etof %d",eotf);
        return TRADITIONAL_GAMMA_SDR;
    }
  }

  return TRADITIONAL_GAMMA_SDR;
}

void DrmHwcLayer::UpdateAndStoreInfoFromDrmBuffer(buffer_handle_t handle,
      int fd, int format, int w, int h, int stride, int h_stride, int byte_stride,
      int size, uint64_t usage, uint32_t fourcc, uint64_t modefier,
      std::vector<uint32_t> byte_stride_planes,
      std::string name, hwc_frect_t &intput_crop, uint64_t buffer_id,
      uint32_t gemhandle, uint32_t replace_transform){

  storeLayerInfo_.valid_       = true;
  storeLayerInfo_.sf_handle    = sf_handle;
  storeLayerInfo_.transform    = transform;
  storeLayerInfo_.source_crop  = source_crop;
  storeLayerInfo_.display_frame  = display_frame;
  storeLayerInfo_.iFd_         = iFd_;
  storeLayerInfo_.iFormat_     = iFormat_;
  storeLayerInfo_.iWidth_      = iWidth_;
  storeLayerInfo_.iHeight_     = iHeight_;
  storeLayerInfo_.iStride_     = iStride_;
  storeLayerInfo_.iHeightStride_ = iHeightStride_;
  storeLayerInfo_.iByteStride_ = iByteStride_;
  storeLayerInfo_.iSize_       = iSize_;
  storeLayerInfo_.iUsage       = iUsage;
  storeLayerInfo_.uFourccFormat_ = uFourccFormat_;
  storeLayerInfo_.uModifier_     = uModifier_;
  storeLayerInfo_.sLayerName_    = sLayerName_;
  storeLayerInfo_.uBufferId_    = uBufferId_;
  storeLayerInfo_.uGemHandle_   = uGemHandle_;
  storeLayerInfo_.uByteStridePlanes_   = uByteStridePlanes_;
  storeLayerInfo_.eDataSpace_   = eDataSpace_;
  sf_handle      = handle;
  iFd_           = fd;
  iFormat_       = format;
  iWidth_        = w;
  iHeight_       = h;
  iStride_       = stride;
  iHeightStride_ = h_stride;
  iByteStride_   = byte_stride;
  iSize_         = size;
  iUsage         = usage;
  uFourccFormat_ = fourcc;
  uModifier_     = modefier;
  sLayerName_    = name;
  uBufferId_     = buffer_id;
  uGemHandle_    = gemhandle;
  uByteStridePlanes_ = byte_stride_planes;

  iBestPlaneType = PLANE_RK3588_ALL_ESMART_MASK;

  source_crop.left   = intput_crop.left;
  source_crop.top    = intput_crop.top;
  source_crop.right  = intput_crop.right;
  source_crop.bottom = intput_crop.bottom;

  transform = replace_transform;
  Init();
  HWC2_ALOGD_IF_DEBUG(
        "SrTransform : LayerId[%u] Fourcc=%c%c%c%c Buf[w,h,s,hs,size]=[%4d,%4d,%4d,%4d,%4d]  src=[%5.0f,%5.0f,%5.0f,%5.0f] dis=[%4d,%4d,%4d,%4d] Transform=%-8.8s(0x%x) gemhandle=%d\n"
        "                            Fourcc=%c%c%c%c Buf[w,h,s,hs,size]=[%4d,%4d,%4d,%4d,%4d]  src=[%5.0f,%5.0f,%5.0f,%5.0f] dis=[%4d,%4d,%4d,%4d] Transform=%-8.8s(0x%x) gemhandle=%d\n",
             uId_,
             storeLayerInfo_.uFourccFormat_,storeLayerInfo_.uFourccFormat_>>8,
             storeLayerInfo_.uFourccFormat_>>16,storeLayerInfo_.uFourccFormat_>>24,
             storeLayerInfo_.iWidth_,storeLayerInfo_.iHeight_,storeLayerInfo_.iStride_,
             storeLayerInfo_.iHeightStride_,storeLayerInfo_.iSize_,
             storeLayerInfo_.source_crop.left,storeLayerInfo_.source_crop.top,
             storeLayerInfo_.source_crop.right,storeLayerInfo_.source_crop.bottom,
             storeLayerInfo_.display_frame.left,storeLayerInfo_.display_frame.top,
             storeLayerInfo_.display_frame.right,storeLayerInfo_.display_frame.bottom,
             TransformToString(storeLayerInfo_.transform).c_str(),storeLayerInfo_.transform,
             storeLayerInfo_.uGemHandle_,
             uFourccFormat_,uFourccFormat_>>8,uFourccFormat_>>16,uFourccFormat_>>24,
             iWidth_,iHeight_,iStride_,iHeightStride_,iSize_,
             source_crop.left,source_crop.top,source_crop.right,source_crop.bottom,
             display_frame.left,display_frame.top,display_frame.right,display_frame.bottom,
             TransformToString(transform).c_str(),transform,
             uGemHandle_);
  return;
}

void DrmHwcLayer::ResetInfoFromStore(){
  if(!storeLayerInfo_.valid_){
    HWC2_ALOGE("ResetInfoFromStore fail, There may be some errors.");
    return;
  }

  sf_handle    = storeLayerInfo_.sf_handle;
  transform    = storeLayerInfo_.transform;
  source_crop  = storeLayerInfo_.source_crop;
  iFd_         = storeLayerInfo_.iFd_;
  iFormat_     = storeLayerInfo_.iFormat_;
  iWidth_      = storeLayerInfo_.iWidth_ ;
  iHeight_     = storeLayerInfo_.iHeight_;
  iStride_     = storeLayerInfo_.iStride_;
  iByteStride_ = storeLayerInfo_.iByteStride_;
  iUsage       = storeLayerInfo_.iUsage;
  uFourccFormat_ = storeLayerInfo_.uFourccFormat_;
  uModifier_     = storeLayerInfo_.uModifier_;
  sLayerName_    = storeLayerInfo_.sLayerName_;
  uBufferId_     = storeLayerInfo_.uBufferId_;
  uGemHandle_    = storeLayerInfo_.uGemHandle_;
  uByteStridePlanes_ = storeLayerInfo_.uByteStridePlanes_;
  eDataSpace_ = storeLayerInfo_.eDataSpace_;

  Init();
  HWC2_ALOGD_IF_DEBUG(
             "reset:DrmHwcLayer[%4u] Buffer[w/h/s/format]=[%4d,%4d,%4d,%4d] Fourcc=%c%c%c%c Transform=%-8.8s(0x%x) Blend[a=%d]=%-8.8s "
             "source_crop[l,t,r,b]=[%5.0f,%5.0f,%5.0f,%5.0f] display_frame[l,t,r,b]=[%4d,%4d,%4d,%4d],skip=%d,afbcd=%d,gles=%d\n",
             uId_,iWidth_,iHeight_,iStride_,iFormat_,uFourccFormat_,uFourccFormat_>>8,uFourccFormat_>>16,uFourccFormat_>>24,
             TransformToString(transform).c_str(),transform,alpha,BlendingToString(blending).c_str(),
             source_crop.left,source_crop.top,source_crop.right,source_crop.bottom,
             display_frame.left,display_frame.top,display_frame.right,display_frame.bottom,bSkipLayer_,bAfbcd_,bGlesCompose_);

  memset(&storeLayerInfo_, 0x00, sizeof(storeLayerInfo_));
  storeLayerInfo_.valid_ = false;

  return;
}



std::string DrmHwcLayer::TransformToString(uint32_t transform) const{
  switch (transform) {
      case DRM_MODE_ROTATE_0:
        return "None";
      case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X:
        return "FlipH";
      case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y:
        return "FlipV";
      case DRM_MODE_ROTATE_90:
        return "Rotate90";
      case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_REFLECT_Y:
        return "Rotate180";
      case DRM_MODE_ROTATE_270:
        return "Rotate270";
      case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_ROTATE_90:
        return "FlipHRotate90";
      case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y | DRM_MODE_ROTATE_90:
        return "FlipVRotate90";
      default:
        return "Unknown";
  }
}
std::string DrmHwcLayer::BlendingToString(DrmHwcBlending blending) const{
  switch (blending) {
    case DrmHwcBlending::kNone:
      return "NONE";
    case DrmHwcBlending::kPreMult:
      return "PREMULT";
    case DrmHwcBlending::kCoverage:
      return "COVERAGE";
    default:
      return "<invalid>";
  }
}

int DrmHwcLayer::DumpInfo(String8 &out){
    if(bFbTarget_)
      out.appendFormat( "DrmHwcFBtar[%4u] Buffer[w/h/s/bs/hs/format]=[%4d,%4d,%4d,%4d,%4d,%4d] Fourcc=%c%c%c%c Transform=%-8.8s(0x%x) Blend[a=%d]=%-8.8s "
                    "source_crop[l,t,r,b]=[%5.0f,%5.0f,%5.0f,%5.0f] display_frame[l,t,r,b]=[%4d,%4d,%4d,%4d],afbcd=%d hdr=%d fps=%f \n",
                   uId_,iWidth_,iHeight_,iStride_,iHeightStride_,iByteStride_,iFormat_,uFourccFormat_,uFourccFormat_>>8,uFourccFormat_>>16,uFourccFormat_>>24,
                   TransformToString(transform).c_str(),transform,alpha,BlendingToString(blending).c_str(),
                   source_crop.left,source_crop.top,source_crop.right,source_crop.bottom,
                   display_frame.left,display_frame.top,display_frame.right,display_frame.bottom,bAfbcd_,
                   bHdr_, fRealFps_);
    else
      out.appendFormat( "DrmHwcLayer[%4u] Buffer[w/h/s/bs/format]=[%4d,%4d,%4d,%4d,%4d,%4d] Fourcc=%c%c%c%c Transform=%-8.8s(0x%x) Blend[a=%d]=%-8.8s "
                        "source_crop[l,t,r,b]=[%5.0f,%5.0f,%5.0f,%5.0f] display_frame[l,t,r,b]=[%4d,%4d,%4d,%4d],skip=%d,afbcd=%d hdr=%d fps=%f \n",
                       uId_,iWidth_,iHeight_,iStride_,iHeightStride_,iByteStride_,iFormat_,uFourccFormat_,uFourccFormat_>>8,uFourccFormat_>>16,uFourccFormat_>>24,
                       TransformToString(transform).c_str(),transform,alpha,BlendingToString(blending).c_str(),
                       source_crop.left,source_crop.top,source_crop.right,source_crop.bottom,
                       display_frame.left,display_frame.top,display_frame.right,display_frame.bottom,bSkipLayer_,bAfbcd_,
                       bHdr_, fRealFps_);
    return 0;
}

int DrmHwcLayer::DumpData(){
  if(!sf_handle){
    ALOGI_IF(LogLevel(DBG_INFO),"%s,line=%d LayerId=%u Buffer is null.",__FUNCTION__,__LINE__,uId_);
    return -1;
  }

  DrmGralloc *drm_gralloc = DrmGralloc::getInstance();
  if(!drm_gralloc){
    ALOGI_IF(LogLevel(DBG_INFO),"%s,line=%d LayerId=%u drm_gralloc is null.",__FUNCTION__,__LINE__,uId_);
    return -1;
  }
  void* cpu_addr = NULL;
  static int frame_cnt =0;
  int width,height,stride,byte_stride,format,size;
  int ret = 0;
  width  = drm_gralloc->hwc_get_handle_attibute(sf_handle,ATT_WIDTH);
  height = drm_gralloc->hwc_get_handle_attibute(sf_handle,ATT_HEIGHT);
  stride = drm_gralloc->hwc_get_handle_attibute(sf_handle,ATT_STRIDE);
  format = drm_gralloc->hwc_get_handle_attibute(sf_handle,ATT_FORMAT);
  size   = drm_gralloc->hwc_get_handle_attibute(sf_handle,ATT_SIZE);
  byte_stride = drm_gralloc->hwc_get_handle_attibute(sf_handle,ATT_BYTE_STRIDE);

  cpu_addr = drm_gralloc->hwc_get_handle_lock(sf_handle,width,height);
  if (cpu_addr == NULL) {
    ALOGE("%s,line=%d, LayerId=%u, lock fail", __FUNCTION__, __LINE__, uId_);
    return -1;
  }

  FILE * pfile = NULL;
  char data_name[100] ;
  system("mkdir /data/dump/ && chmod /data/dump/ 777 ");
  sprintf(data_name,"/data/dump/%d_%15.15s_id-%d_%dx%d_f-%d.bin",
          frame_cnt++,sLayerName_.size() < 5 ? "unset" : sLayerName_.c_str(),
          uId_,stride,height,format);

  pfile = fopen(data_name,"wb");
  if(pfile)
  {
      fwrite((const void *)cpu_addr,(size_t)(size),1,pfile);
      fflush(pfile);
      fclose(pfile);
      ALOGD(" dump surface layer_id=%d ,data_name %s,w:%d,h:%d,stride :%d,size=%d,cpu_addr=%p",
          uId_,data_name,width,height,byte_stride,size,cpu_addr);
  }
  else
  {
      ALOGE("Open %s fail", data_name);
      ALOGD(" dump surface layer_id=%d ,data_name %s,w:%d,h:%d,stride :%d,size=%d,cpu_addr=%p",
          uId_,data_name,width,height,byte_stride,size,cpu_addr);
  }


  ret = drm_gralloc->hwc_get_handle_unlock(sf_handle);
  if(ret){
    ALOGE("%s,line=%d, LayerId=%u, unlock fail ret = %d ",__FUNCTION__,__LINE__,uId_,ret);
    return ret;
  }
  return 0;
}


}  // namespace android
