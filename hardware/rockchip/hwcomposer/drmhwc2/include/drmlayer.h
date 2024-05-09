/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_DRM_HWCOMPOSER_H_
#define ANDROID_DRM_HWCOMPOSER_H_

#include <stdbool.h>
#include <stdint.h>

#include <vector>
#include <utils/String8.h>

#include <hardware/hardware.h>
#include <hardware/hwcomposer2.h>

#include "utils/autofd.h"
#include "drmhwcgralloc.h"
#include "rockchip/drmtype.h"
#include "utils/drmfence.h"
#include "drmbuffer.h"
#include "rockchip/hdr/drmhdrparser.h"

struct hwc_import_context;

#include "rockchip/drmgralloc.h"

int hwc_import_init(struct hwc_import_context **ctx);
int hwc_import_destroy(struct hwc_import_context *ctx);

int hwc_import_bo_create(int fd, struct hwc_import_context *ctx,
                         buffer_handle_t buf, struct hwc_drm_bo *bo);
bool hwc_import_bo_release(int fd, struct hwc_import_context *ctx,
                           struct hwc_drm_bo *bo);

namespace android {
class Importer;

/*
 * Base_parameter is used for 3328_8.0 , by libin end.
 */
enum
{
    VIDEO_SCALE_FULL_SCALE = 0,
    VIDEO_SCALE_AUTO_SCALE,
    VIDEO_SCALE_4_3_SCALE ,
    VIDEO_SCALE_16_9_SCALE,
    VIDEO_SCALE_ORIGINAL,
    VIDEO_SCALE_OVERSCREEN,
    VIDEO_SCALE_LR_BOX,
    VIDEO_SCALE_TB_BOX,
};

class DrmHwcBuffer {
 public:
  DrmHwcBuffer() = default;
  DrmHwcBuffer(const hwc_drm_bo &bo, Importer *importer)
      : bo_(bo), importer_(importer) {
  }
  DrmHwcBuffer(DrmHwcBuffer &&rhs) : bo_(rhs.bo_), importer_(rhs.importer_) {
    rhs.importer_ = NULL;
  }

  ~DrmHwcBuffer() {
    Clear();
  }

  DrmHwcBuffer &operator=(DrmHwcBuffer &&rhs) {
    Clear();
    importer_ = rhs.importer_;
    rhs.importer_ = NULL;
    bo_ = rhs.bo_;
    return *this;
  }

  operator bool() const {
    return importer_ != NULL;
  }

  const hwc_drm_bo *operator->() const;

  void Clear();

  int ImportBuffer(buffer_handle_t handle, Importer *importer);

  int SetBoInfo(uint32_t fd, uint32_t width,
                uint32_t height, uint32_t height_stride,
                uint32_t format, uint32_t hal_format,
                uint64_t modifier,
                uint64_t usage, uint32_t byte_stride,
                uint32_t gem_handle, uint32_t offset[4],
                std::vector<uint32_t> &plane_byte_stride);
 private:
  hwc_drm_bo bo_;
  Importer *importer_ = NULL;
};

class DrmHwcNativeHandle {
 public:
  DrmHwcNativeHandle() = default;

  DrmHwcNativeHandle(native_handle_t *handle) : handle_(handle) {
  }

  DrmHwcNativeHandle(DrmHwcNativeHandle &&rhs) {
    handle_ = rhs.handle_;
    rhs.handle_ = NULL;
  }

  ~DrmHwcNativeHandle();

  DrmHwcNativeHandle &operator=(DrmHwcNativeHandle &&rhs) {
    Clear();
    handle_ = rhs.handle_;
    rhs.handle_ = NULL;
    return *this;
  }

  int CopyBufferHandle(buffer_handle_t handle, int width, int height,
                       int layerCount, int format, uint64_t usage, int stride);

  void Clear();

  buffer_handle_t get() const {
    return handle_;
  }

 private:
  native_handle_t *handle_ = NULL;
};

//Drm driver version is 2.0.0 use these.
enum DrmHwcTransform {
    kIdentity = 0,
    kRotate0 = 1 << 0,
    kRotate90 = 1 << 1,
    kRotate180 = 1 << 2,
    kRotate270 = 1 << 3,
    kFlipH = 1 << 4,
    kFlipV = 1 << 5,
};


enum class DrmHwcBlending : int32_t {
  kNone = HWC_BLENDING_NONE,
  kPreMult = HWC_BLENDING_PREMULT,
  kCoverage = HWC_BLENDING_COVERAGE,
};

struct DrmLayerInfoStore{
  bool valid_ = false;

  buffer_handle_t sf_handle = NULL;
  uint32_t transform;
  hwc_frect_t source_crop;
  hwc_rect_t display_frame;

  // Buffer info
  int iFd_;
  int iFormat_;
  int iWidth_;
  int iHeight_;
  int iStride_;
  int iHeightStride_;
  int iByteStride_;
  int iSize_;
  uint64_t iUsage;
  uint32_t uFourccFormat_;
  uint64_t uModifier_;
  uint64_t uBufferId_;
  uint32_t uGemHandle_=0;
  android_dataspace_t eDataSpace_;
  std::string sLayerName_;
  std::vector<uint32_t> uByteStridePlanes_;
};

struct DrmHwcLayer {
  buffer_handle_t sf_handle = NULL;
  uint64_t gralloc_buffer_usage = 0;
  DrmHwcBuffer buffer;
  DrmHwcNativeHandle handle;
  uint32_t transform;
  DrmHwcBlending blending = DrmHwcBlending::kNone;
  HWC2::Composition sf_composition;
  uint16_t alpha = 0xff;
  hwc_frect_t source_crop;
  hwc_rect_t display_frame;
  hwc_rect_t display_frame_sf;

  // Commit mirror function
  int iFbWidth_;
  int iFbHeight_;
  float fHScaleMulMirror_;
  float fVScaleMulMirror_;
  hwc_rect_t display_frame_mirror;

  sp<AcquireFence> acquire_fence = AcquireFence::NO_FENCE;
  sp<ReleaseFence> release_fence = ReleaseFence::NO_FENCE;

  // Display info
  uint32_t uAclk_=0;
  uint32_t uDclk_=0;

  // Frame info
  uint32_t uId_;
  uint32_t uFrameNo_;
  int  iZpos_;
  int  iDrmZpos_;
  bool bFbTarget_=false;
  bool bAfbcd_=false;
  bool bYuv_;
  bool bScale_;
  bool bHdr_;
  bool bNextHdr_;
  // Only RK3528 Support
  bool bMetadataHdr_;
  bool bYuv10bit_;

  bool bSkipLayer_;
  float fHScaleMul_;
  float fVScaleMul_;

  // Buffer info
  uint64_t uBufferId_;
  int iFd_;
  int iFormat_;
  int iWidth_;
  int iHeight_;
  int iStride_;
  int iHeightStride_;
  int iByteStride_;
  int iSize_;
  uint64_t iUsage;
  uint32_t uFourccFormat_;
  uint32_t uGemHandle_;
  uint64_t uModifier_;
  std::string sLayerName_;
  // Tip: NV24 have 2 plane byte stride
  std::vector<uint32_t> uByteStridePlanes_;

  bool bMatch_;
  bool bUse_;
  bool bMix_;

  bool bGlesCompose_=false;

  int iBestPlaneType=0;

  int iGroupId_;
  int iShareId_;
  int iSkipLine_;

  // Android definition
  android_dataspace_t eDataSpace_;
  drm_colorspace uColorSpace;
  uint16_t uEOTF=0;

  // Sideband Stream
  int iTunnelId_ = 0;
  bool bSideband2_ = false;
  bool bSidebandStreamLayer_;

  // 手写加速图层
  bool bAccelerateLayer_;

  // Use Rga
  bool bUseRga_;
  std::shared_ptr<DrmBuffer> pRgaBuffer_;

  bool bUseSr_;
  std::shared_ptr<DrmBuffer> pSrBuffer_;

  bool bUseMemc_;
  std::shared_ptr<DrmBuffer> pMemcBuffer_;

  bool bUsePq_;
  std::shared_ptr<DrmBuffer> pPqBuffer_;

  DrmLayerInfoStore storeLayerInfo_;

  // next hdr
  bool IsMetadataHdr_;
  rk_hdr_parser_params_t metadataHdrParam_;
  rk_hdr_fmt_info_t metadataHdrFmtInfo_;

  // fps
  float fRealFps_;
  int fRealMaxFps_;

#ifdef RK3528
  // RK3528 vpu prescale info.
  bool bNeedPreScale_;
  bool bIsPreScale_;
  DrmLayerInfoStore storePreScaleInfo_;
  metadata_for_rkvdec_scaling_t mMetadata_;

  int SwitchPreScaleBufferInfo();
  int ResetInfoFromPreScaleStore();
#endif

  int ImportBuffer(Importer *importer);
  int Init();
  int InitFromDrmHwcLayer(DrmHwcLayer *layer, Importer *importer);
  void SetBlend(HWC2::BlendMode blend);
  void SetTransform(HWC2::Transform sf_transform);
  void SetSourceCrop(hwc_frect_t const &crop);
  void SetDisplayFrame(hwc_rect_t const &frame, hwc2_drm_display_t *ctx);
  void ModifyDisplayFrameForOverscan(hwc2_drm_display_t *ctx);
  void SetDisplayFrameMirror(hwc_rect_t const &frame);
  void UpdateAndStoreInfoFromDrmBuffer(buffer_handle_t handle,
      int fd, int format, int w, int h, int stride, int h_stride, int size,
      int byte_stride, uint64_t usage, uint32_t fourcc, uint64_t modefier,
      std::vector<uint32_t> byte_stride_planes,
      std::string name, hwc_frect_t &intput_crop, uint64_t buffer_id,
      uint32_t gemhandle, uint32_t replace_transform);
  void ResetInfoFromStore();

  buffer_handle_t get_usable_handle() const {
    return handle.get() != NULL ? handle.get() : sf_handle;
  }

  bool protected_usage() const {
    return (gralloc_buffer_usage & GRALLOC_USAGE_PROTECTED) ==
           GRALLOC_USAGE_PROTECTED;
  }
  bool IsYuvFormat(int format,uint32_t fourcc_format);
  bool Is10bitYuv(int format,uint32_t fourcc_format);
  bool IsScale(hwc_frect_t &source_crop, hwc_rect_t &display_frame, int transform);
  bool IsAfbcModifier(uint64_t modifier);
  bool IsSkipLayer();
#ifdef RK3528
  void ModifyDisplayFrame();
  bool IsPreScaleVideo(uint64_t usage);
#endif
  bool IsHdr(uint64_t usage, android_dataspace_t dataspace);
  bool IsMetadataHdr(uint64_t usage);
  int GetSkipLine();
  drm_colorspace GetColorSpace(android_dataspace_t dataspace);
  supported_eotf_type GetEOTF(android_dataspace_t dataspace);
  std::string TransformToString(uint32_t transform) const;
  std::string BlendingToString(DrmHwcBlending blending) const;
  int DumpInfo(String8 &out);
  int DumpData();
};

struct DrmHwcDisplayContents {
  OutputFd retire_fence;
  std::vector<DrmHwcLayer> layers;
};
}  // namespace android

#endif
