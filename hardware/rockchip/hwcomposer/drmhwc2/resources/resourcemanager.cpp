/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "hwc-resource-manager"

#include "resources/resourcemanager.h"
#include "drmlayer.h"

#include <cutils/properties.h>
#include <log/log.h>
#include <sstream>
#include <string>

#include <drm_fourcc.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <utils/Trace.h>

#include <rga.h>

//XML prase
#include <tinyxml2.h>

namespace android {

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

// define from hardware/rockchip/libgralloc/bifrost/src/mali_gralloc_usages.h
#ifndef RK_GRALLOC_USAGE_WITHIN_4G
#define RK_GRALLOC_USAGE_WITHIN_4G (1ULL << 56)
#endif

#ifndef RK_GRALLOC_USAGE_STRIDE_ALIGN_16
#define RK_GRALLOC_USAGE_STRIDE_ALIGN_16 (1ULL << 57)
#endif

/* Gralloc 4.0 中, 表征 "调用 alloc() 的 client 要求分配的 buffer 不是 AFBC 格式".
*/
#ifndef MALI_GRALLOC_USAGE_NO_AFBC
#define MALI_GRALLOC_USAGE_NO_AFBC (1ULL << 29)
#endif

#define WB_BUFFERQUEUE_MAX_SIZE 4

ResourceManager::ResourceManager() :
  num_displays_(0) {
  drmGralloc_ = DrmGralloc::getInstance();
}

int ResourceManager::Init(DrmHwcTwo *hwc2) {
  hwc2_ = hwc2;
  int ret = AddDrmDevice();
  if(ret){
    ALOGE("Failed to AddDrmDevice ");
  }

  if (!num_displays_) {
    ALOGE("Failed to initialize any displays");
    return ret ? -EINVAL : ret;
  }


  fb0_fd = open("/dev/graphics/fb0", O_RDWR, 0);
  if(fb0_fd < 0){
    ALOGE("Open fb0 fail in %s",__FUNCTION__);
  }

  DrmDevice *drm = drms_.front().get();
  for(auto &crtc : drm->crtcs()){
    mapDrmDisplayCompositor_.insert(
      std::pair<int, std::shared_ptr<DrmDisplayCompositor>>(crtc->id(),std::make_shared<DrmDisplayCompositor>()));
    HWC2_ALOGI("Create DrmDisplayCompositor crtc=%d",crtc->id());
  }

  displays_ = drm->GetDisplays();
  if(displays_.size() == 0){
    ALOGE("Failed to initialize any displays");
    return ret ? -EINVAL : ret;
  }

  // 更新全局平台版本信息
  gSetSocId(drm->getSocId());
  // 更新全局 kernel drm 版本信息
  gSetDrmVersion(drm->getDrmVersion());

  // 更新配置
  InitProperty();
  return 0;
}

int ResourceManager::AddDrmDevice() {
  std::unique_ptr<DrmDevice> drm = std::make_unique<DrmDevice>();
  int displays_added, ret;
  std::tie(ret, displays_added) = drm->Init(num_displays_);
  if (ret < 0)
    return ret;

  //Get soc id
  soc_id_ = drm->getSocId();
  //DrmVersion
  drmVersion_ = drm->getDrmVersion();
  drmGralloc_->set_drm_version(dup(drm->fd()),drmVersion_);

  std::shared_ptr<Importer> importer;
  importer.reset(Importer::CreateInstance(drm.get()));
  if (!importer) {
    ALOGE("Failed to create importer instance");
    return -ENODEV;
  }
  importers_.push_back(std::move(importer));
  drms_.push_back(std::move(drm));
  num_displays_ += displays_added;
  return ret;
}

DrmConnector *ResourceManager::AvailableWritebackConnector(int display) {
  DrmDevice *drm_device = GetDrmDevice(display);
  DrmConnector *writeback_conn = NULL;
  if (drm_device) {
    writeback_conn = drm_device->AvailableWritebackConnector(display);
    if (writeback_conn)
      return writeback_conn;
  }
  for (auto &drm : drms_) {
    if (drm.get() == drm_device)
      continue;
    writeback_conn = drm->AvailableWritebackConnector(display);
    if (writeback_conn)
      return writeback_conn;
  }
  return writeback_conn;
}

int ResourceManager::InitProperty() {
  char property_value[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.enable_composition_drop_mode", property_value, "0");
  mCompositionDropMode_ = atoi(property_value) != 0;

  property_get("vendor.hwc.enable_dynamic_display_mode", property_value, "0");
  mDynamicDisplayMode_ = atoi(property_value) > 0;

  property_get("vendor.hwc.enable_sideband_stream_2_mode", property_value, "0");
  mSidebandStream2Mode_ = atoi(property_value) > 0;

  property_get("vendor.hwc.video_buf_cache_max_size", property_value, "0");
  mCacheBufferLimitSize_ = atoi(property_value);

  return 0;
}

bool ResourceManager::IsCompositionDropMode() const {
  return mCompositionDropMode_;
}

bool ResourceManager::IsDynamicDisplayMode() const {
  return mDynamicDisplayMode_;
}

bool ResourceManager::IsSidebandStream2Mode() const {
  // RK3528 默认开启 Sideband2.0支持
  if(gIsRK3528()){
    return true;
  }
  return mSidebandStream2Mode_;
}

int ResourceManager::GetCacheBufferLimitSize() const{
  return mCacheBufferLimitSize_;
}

DrmDevice *ResourceManager::GetDrmDevice(int display) {
  for (auto &drm : drms_) {
    if (drm->HandlesDisplay(display & ~DRM_CONNECTOR_SPILT_MODE_MASK))
      return drm.get();
  }
  return NULL;
}

std::shared_ptr<Importer> ResourceManager::GetImporter(int display) {
  for (unsigned int i = 0; i < drms_.size(); i++) {
    if (drms_[i]->HandlesDisplay(display & ~DRM_CONNECTOR_SPILT_MODE_MASK))
      return importers_[i];
  }
  return NULL;
}

std::shared_ptr<DrmDisplayCompositor> ResourceManager::GetDrmDisplayCompositor(DrmCrtc* crtc){
  if(!crtc){
    HWC2_ALOGE("crtc is null");
    return NULL;
  }

  if(mapDrmDisplayCompositor_.size() == 0){
    HWC2_ALOGE("mapDrmDisplayCompositor_.size()=0");
    return NULL;
  }

  auto pairDrmDisplayCompositor = mapDrmDisplayCompositor_.find(crtc->id());
  return pairDrmDisplayCompositor->second;
}

int ResourceManager::GetWBDisplay() const {
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return iWriteBackDisplayId_;
}

bool ResourceManager::isWBMode() const {
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return bEnableWriteBackRef_ > 0;
}

const DrmMode& ResourceManager::GetWBMode() const {
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return mWBMode_;
}

bool ResourceManager::IsDisableHwVirtualDisplay(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return mVDMode_ == HWC2_DISABLE_HW_VIRTUAL_DISPLAY;
}

bool ResourceManager::IsWriteBackByVop(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return mVDMode_ == HWC2_HW_VIRTUAL_DISPLAY_USE_VOP;
}

bool ResourceManager::IsWriteBackByRga(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return mVDMode_ == HWC2_HW_VIRTUAL_DISPLAY_USE_RGA;
}

HwVirtualDisplayMode_t ResourceManager::ChooseWriteBackMode(int display){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  // 2. 获取待 WriteBack display状态，状态异常则直接关闭 WriteBack 模式
  DrmDevice *drmDevice = GetDrmDevice(display);
  DrmConnector *writeBackConn = drmDevice->GetConnectorForDisplay(display);
  if(!writeBackConn){
    HWC2_ALOGE("display=%d WriteBackConn is NULL", display);
    return HWC2_DISABLE_HW_VIRTUAL_DISPLAY;
  }

  if(writeBackConn->state() != DRM_MODE_CONNECTED){
    HWC2_ALOGE("display=%d WriteBackConn state isn't connected(%d)",
                display, writeBackConn->state());
    return HWC2_DISABLE_HW_VIRTUAL_DISPLAY;
  }

  // 3. 获取待 WriteBack 当前分辨率，用于申请 WriteBackBuffer
  // 4. WriteBack 硬件要求 16对齐，否则超出部分会直接丢弃
  mWBMode_ = writeBackConn->current_mode();
  if(mWBMode_.width() > 4096 || mWBMode_.height() > 2160){
    HWC2_ALOGI("Primary resolution=%dx%d, use WriteBack by RGA", mWBMode_.width(), mWBMode_.height());
    return HWC2_HW_VIRTUAL_DISPLAY_USE_RGA;
  }

  HWC2_ALOGI("Primary resolution=%dx%d, use WriteBack by Vop WriteBack", mWBMode_.width(), mWBMode_.height());
  return HWC2_HW_VIRTUAL_DISPLAY_USE_VOP;
}

// 使用 Vop 作为 WriteBack 内容输出单元
int ResourceManager::WriteBackUseVop(int display){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  iWBWidth_  = ALIGN_DOWN(mWBMode_.width(),16);
  iWBHeight_ = mWBMode_.height();
  iWBFormat_ = HAL_PIXEL_FORMAT_YCrCb_NV12;

  // 5. 创建 WriteBackBuffer BufferQueue，并且申请 WB Buffer.
  if(mWriteBackBQ_ == NULL){
    mWriteBackBQ_ = std::make_shared<DrmBufferQueue>(WB_BUFFERQUEUE_MAX_SIZE);
    mNextWriteBackBuffer_
      = mWriteBackBQ_->DequeueDrmBuffer(iWBWidth_,
                                        iWBHeight_,
                                        iWBFormat_,
                                        RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                        MALI_GRALLOC_USAGE_NO_AFBC,
                                        "WriteBackBuffer");
    if(!mNextWriteBackBuffer_->initCheck()){
      HWC2_ALOGE("display=%d WBBuffer Dequeue fail, w=%d h=%d format=%d",
                                        display,
                                        iWBWidth_,
                                        iWBHeight_,
                                        iWBFormat_);
      return -1;
    }
  }

  bEnableWriteBackRef_++;
  iWriteBackDisplayId_ = display;
  return 0;
}

// 使用 Rga 作为 WriteBack 内容输出单元
int ResourceManager::WriteBackUseRga(int display){
  // 利用 RGA 进行 WriteBack, RGA alpha blend 要求点对点，不允许缩放
  // 故要求申请内存与系统UI分辨率一致
  if(hwc2_->GetDisplayCtxPtr(display) != NULL){
    hwc2_drm_display_t* dpy_ctx = hwc2_->GetDisplayCtxPtr(display);
    iWBWidth_ = dpy_ctx->framebuffer_width;
    iWBHeight_ = dpy_ctx->framebuffer_height;
    iWBFormat_ = HAL_PIXEL_FORMAT_RGBA_8888;
  }else{
    iWBWidth_  = mWBMode_.h_display();
    iWBHeight_ = mWBMode_.v_display();
    iWBFormat_ = HAL_PIXEL_FORMAT_RGBA_8888;
  }

  // 5. 创建 WriteBackBuffer BufferQueue，并且申请 WB Buffer.
  if(mWriteBackBQ_ == NULL){
    mWriteBackBQ_ = std::make_shared<DrmBufferQueue>();

    mNextWriteBackBuffer_
      = mWriteBackBQ_->DequeueDrmBuffer(iWBWidth_,
                                        iWBHeight_,
                                        iWBFormat_,
                                        RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                        MALI_GRALLOC_USAGE_NO_AFBC,
                                        "WriteBackBuffer");
    if(!mNextWriteBackBuffer_->initCheck()){
      HWC2_ALOGE("display=%d WBBuffer Dequeue fail, w=%d h=%d format=%d",
                                        display,
                                        iWBWidth_,
                                        iWBHeight_,
                                        iWBFormat_);
      return -1;
    }
  }

  bEnableWriteBackRef_++;
  iWriteBackDisplayId_ = display;
  return 0;
}

int ResourceManager::EnableWriteBackMode(int display){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);

  // 1. 检查 WB 模块是否已经被绑定
  if(bEnableWriteBackRef_ > 0){
    if(iWriteBackDisplayId_ != display){
      HWC2_ALOGE("WriteBack has bind display %d, so display=%d WB request can't handle.",
                iWriteBackDisplayId_, display);
      return -1;
    }else{
      bEnableWriteBackRef_++;
      return 0;
    }
  }

  // 2. 根据 primary 分辨率模式选择 WriteBack 模式
  mVDMode_ = ChooseWriteBackMode(display);
  switch(mVDMode_){
    case HWC2_HW_VIRTUAL_DISPLAY_USE_VOP:
      return WriteBackUseVop(display);
    case HWC2_HW_VIRTUAL_DISPLAY_USE_RGA:
      return WriteBackUseRga(display);
    case HWC2_DISABLE_HW_VIRTUAL_DISPLAY:
    default:
      HWC2_ALOGE("display=%d can't find any suitable WriteBack mode, roll back to GLES display, VDMode=%d",
                 display, mVDMode_);
      return -1;
  }

  return -1;
}


int ResourceManager::UpdateWriteBackResolutionUseVop(int display){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);

  // 2. 获取待 WriteBack display状态，状态异常则直接关闭 WriteBack 模式
  DrmDevice *drmDevice = GetDrmDevice(display);
  DrmConnector *writeBackConn = drmDevice->GetConnectorForDisplay(display);
  if(!writeBackConn){
    HWC2_ALOGE("display=%d WriteBackConn is NULL", display);
    return -1;
  }

  if(writeBackConn->state() != DRM_MODE_CONNECTED){
    HWC2_ALOGE("display=%d WriteBackConn state isn't connected(%d)",
                display, writeBackConn->state());
    return -1;
  }

  // 3. 获取待 WriteBack 当前分辨率，用于申请 WriteBackBuffer
  DrmMode currentMode = writeBackConn->current_mode();
  mWBMode_ = currentMode;
  int tempWBWidth  = ALIGN_DOWN(mWBMode_.width(),16);
  int tempWBHeight = mWBMode_.height();
  if(tempWBWidth == iWBWidth_ &&
     tempWBHeight == iWBHeight_){
    return 0;
  }else{
    HWC2_ALOGI("display=%d update WriteBack resolution(%dx%d)=>(%dx%d)",
                display, iWBWidth_, iWBHeight_,
                currentMode.width(), currentMode.height());
  }

  iWBWidth_  = tempWBWidth;
  iWBHeight_ = tempWBHeight;
  iWBFormat_ = HAL_PIXEL_FORMAT_YCrCb_NV12;

  // 4. 创建 WriteBackBuffer BufferQueue，并且申请 WB Buffer.
  if(mWriteBackBQ_ == NULL){
    mWriteBackBQ_ = std::make_shared<DrmBufferQueue>(WB_BUFFERQUEUE_MAX_SIZE);
  }

  mNextWriteBackBuffer_
    = mWriteBackBQ_->DequeueDrmBuffer(iWBWidth_,
                                      iWBHeight_,
                                      iWBFormat_,
                                      RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                      MALI_GRALLOC_USAGE_NO_AFBC,
                                      "WriteBackBuffer");
  if(!mNextWriteBackBuffer_->initCheck()){
    HWC2_ALOGE("display=%d WBBuffer Dequeue fail, w=%d h=%d format=%d",
                display, iWBWidth_, iWBHeight_, iWBFormat_);
    return -1;
  }
  return 0;
}

int ResourceManager::UpdateWriteBackResolution(int display){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);

  // 1. 检查 WB 模块是否已经被绑定
  if(bEnableWriteBackRef_ > 0){
    if(iWriteBackDisplayId_ != display){
      HWC2_ALOGE("WriteBack has bind display %d, so display=%d WB request can't handle.",
                iWriteBackDisplayId_, display);
      return -1;
    }
  }

  // 根据 WriteBack mode 处理 update WriteBack resolution 请求
  switch(mVDMode_){
    case HWC2_HW_VIRTUAL_DISPLAY_USE_VOP:
      return UpdateWriteBackResolutionUseVop(display);
    case HWC2_HW_VIRTUAL_DISPLAY_USE_RGA:
      // RGA 模式不需要考虑分辨率切换的场景
      return 0;
    case HWC2_DISABLE_HW_VIRTUAL_DISPLAY:
    default:
      HWC2_ALOGE("display=%d can't find any suitable WriteBack mode, VDMode=%d ", display, mVDMode_);
      return -1;
  }
  return 0;
}

int ResourceManager::DisableWriteBackMode(int display){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  if(display != iWriteBackDisplayId_)
    return 0;

  bEnableWriteBackRef_--;
  if(bEnableWriteBackRef_ <= 0){
    iWriteBackDisplayId_ = -1;
    mFinishBufferQueue_.clear();
    mVDMode_ = HWC2_DISABLE_HW_VIRTUAL_DISPLAY;
  }
  return 0;
}

std::shared_ptr<DrmBuffer> ResourceManager::GetResetWBBuffer(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  if(mResetBackBuffer_ == NULL){
    mResetBackBuffer_ =  std::make_shared<DrmBuffer>(640,
                                                     360,
                                                     HAL_PIXEL_FORMAT_YCrCb_NV12,
                                                     RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                                     RK_GRALLOC_USAGE_WITHIN_4G,
                                                     "WBResetBuffer");
    if(mResetBackBuffer_->Init()){
      HWC2_ALOGE("DrmBuffer Init fail, w=%d h=%d format=%d name=%s",
                  640, 360, HAL_PIXEL_FORMAT_YCrCb_NV12, "WBResetBuffer");
      mResetBackBuffer_ = NULL;
      return NULL;
    }

    rga_buffer_t src;
    im_rect src_rect;

    memset(&src, 0x0, sizeof(rga_buffer_t));
    memset(&src_rect, 0x0, sizeof(im_rect));

    // Set src buffer info
    src.fd      = mResetBackBuffer_->GetFd();
    src.width   = mResetBackBuffer_->GetWidth();
    src.height  = mResetBackBuffer_->GetHeight();
    src.wstride = mResetBackBuffer_->GetStride();
    src.hstride = mResetBackBuffer_->GetHeightStride();
    src.format  = mResetBackBuffer_->GetFormat();

    // Set src rect info
    src_rect.x = 0;
    src_rect.y = 0;
    src_rect.width  = src.width ;
    src_rect.height = src.height;

    src.color_space_mode = IM_RGB_TO_YUV_BT601_LIMIT;

    // Set Dataspace
    // if((buffer.mBufferInfo_.uDataSpace_ & HAL_DATASPACE_STANDARD_BT709) == HAL_DATASPACE_STANDARD_BT709){
    //   dst.color_space_mode = IM_YUV_TO_RGB_BT709_LIMIT;
    //   SVEP_ALOGD_IF("color_space_mode = BT709 dataspace=0x%" PRIx64,buffer.mBufferInfo_.uDataSpace_);
    // }else{
    //   SVEP_ALOGD_IF("color_space_mode = BT601 dataspace=0x%" PRIx64,buffer.mBufferInfo_.uDataSpace_);
    // }

    IM_STATUS im_state;

    // Call Im2d 格式转换
    im_state = imfill(src, src_rect, 0x0);

    if(im_state != IM_STATUS_SUCCESS){
      HWC2_ALOGE("call im2d reset Fail!");
    }
  }
  return mResetBackBuffer_;
}
std::shared_ptr<DrmBuffer> ResourceManager::GetNextWBBuffer(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return mNextWriteBackBuffer_;
}

std::shared_ptr<DrmBuffer> ResourceManager::GetDrawingWBBuffer(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return mDrawingWriteBackBuffer_;;
}

int ResourceManager::GetFinishWBBufferSize(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return mFinishBufferQueue_.size();
}

int ResourceManager::OutputWBBuffer(int display_id,
                                    rga_buffer_t &dst,
                                    im_rect &dst_rect,
                                    int32_t *retire_fence,
                                    uint64_t *last_frame_no){

  ATRACE_CALL();
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  if(mFinishBufferQueue_.size() == 0){
    HWC2_ALOGE("mFinishBufferQueue_ size is 0");
    return -1;
  }

  std::shared_ptr<DrmBuffer> output_buffer = NULL;
  uint64_t output_frame_no = 0;
  std::queue<PAIR_ID_BUFFER> tmp_bufferqueue;
  for(auto &FinishBufferPair : mFinishBufferQueue_){
    if(FinishBufferPair.first > *last_frame_no){
      output_buffer = FinishBufferPair.second;
      output_frame_no = FinishBufferPair.first;
      break;
    }
  }

  if(output_buffer == NULL && mFinishBufferQueue_.size() >0){
    output_buffer = mFinishBufferQueue_.back().second;
    output_frame_no = mFinishBufferQueue_.back().first;
    HWC2_ALOGW("VDS may output a same image frame_no=%" PRIu64 " Last_frame_no=%" PRIu64 , output_frame_no, *last_frame_no);
  }

  if(output_buffer == NULL){
    HWC2_ALOGE("output_buffer is NULL");
    return -1;
  }

  HWC2_ALOGD_IF_DEBUG("WB: display=%d frame_no=%" PRIu64 " id=%" PRIu64 " queue.size=%zu" ,
                        display_id, output_frame_no, output_buffer->GetId(),mFinishBufferQueue_.size());

  output_buffer->WaitFinishFence();
  // 添加调试接口，抓打印WriteBack Buffer
  char value[PROPERTY_VALUE_MAX];
  property_get("debug.wb.dump", value, "0");
  if(atoi(value) > 0){
    output_buffer->DumpData();
  }

  rga_buffer_t src;
  rga_buffer_t pat;
  im_rect src_rect;
  im_rect pat_rect;

  memset(&src, 0x0, sizeof(rga_buffer_t));
  memset(&pat, 0x0, sizeof(rga_buffer_t));
  memset(&src_rect, 0x0, sizeof(im_rect));
  memset(&src_rect, 0x0, sizeof(im_rect));

  // Set src buffer info
  src.fd      = output_buffer->GetFd();
  src.width   = output_buffer->GetWidth();
  src.height  = output_buffer->GetHeight();
  src.wstride = output_buffer->GetStride();
  src.hstride = output_buffer->GetHeightStride();
  src.format  = output_buffer->GetFormat();

  // 由于WriteBack仅支持BGR888(B:G:R little endian)，故需要使用RGA做格式转换
  if(src.format == HAL_PIXEL_FORMAT_RGB_888)
    src.format = RK_FORMAT_BGR_888;
  // 由于WriteBack仅支持BGR565(B:G:R little endian)，故需要使用RGA做格式转换
  if(src.format == HAL_PIXEL_FORMAT_RGB_565)
    src.format = RK_FORMAT_BGR_565;


  // Set src rect info
  src_rect.x = 0;
  src_rect.y = 0;
  src_rect.width  = output_buffer->GetWidth();
  src_rect.height = output_buffer->GetHeight();

  // Set Dataspace
  // if((srcBuffer.mBufferInfo_.uDataSpace_ & HAL_DATASPACE_STANDARD_BT709) == HAL_DATASPACE_STANDARD_BT709){
  //   dst.color_space_mode = IM_YUV_TO_RGB_BT709_LIMIT;
  //   SVEP_ALOGD_IF("color_space_mode = BT709 dataspace=0x%" PRIx64,srcBuffer.mBufferInfo_.uDataSpace_);
  // }else{
  //   SVEP_ALOGD_IF("color_space_mode = BT601 dataspace=0x%" PRIx64,srcBuffer.mBufferInfo_.uDataSpace_);
  // }

  IM_STATUS im_state;

  im_opt_t imOpt;
  memset(&imOpt, 0x00, sizeof(im_opt_t));
  imOpt.core = IM_SCHEDULER_RGA3_CORE0 | IM_SCHEDULER_RGA3_CORE1;

  // Call Im2d 格式转换
  im_state = improcess(src, dst, pat, src_rect, dst_rect, pat_rect, 0, NULL, &imOpt, IM_SYNC);

  if(im_state == IM_STATUS_SUCCESS){
    HWC2_ALOGD_IF_VERBOSE("call im2d convert to rgb888 Success");
    *retire_fence = -1;
  }else{
    HWC2_ALOGD_IF_DEBUG("call im2d fail, ret=%d Error=%s", im_state, imStrError(im_state));
    *retire_fence = -1;
    return -1;
  }

  *last_frame_no = output_frame_no;

  return 0;
}

int ResourceManager::SwapWBBuffer(uint64_t frame_no){

  ATRACE_CALL();
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  if(bEnableWriteBackRef_ <= 0){
    HWC2_ALOGE("");
    return -1;
  }

  // 1. Drawing 切换为 Finish 状态, 并 push 进队列
  if(mDrawingWriteBackBuffer_ != NULL){
    mFinishBufferQueue_.push_back(PAIR_ID_BUFFER{frame_no, mDrawingWriteBackBuffer_});
    HWC2_ALOGD_IF_VERBOSE("WB: frame_no=%" PRIu64 " id=%" PRIu64 " queue.size=%zu" ,frame_no, mDrawingWriteBackBuffer_->GetId(),mFinishBufferQueue_.size());
    // 环形缓冲区，若达到环形缓冲区最大尺寸，则需要丢掉最旧的一帧缓存
    if(mFinishBufferQueue_.size() > (WB_BUFFERQUEUE_MAX_SIZE - 1)){
      auto last_buffer_pair = mFinishBufferQueue_.begin();
      mFinishBufferQueue_.erase(last_buffer_pair);
      HWC2_ALOGD_IF_WARN("WB: lost frame_no=%" PRIu64 " id=%" PRIu64 " queue.size=%zu" ,
                 last_buffer_pair->first,
                 ((last_buffer_pair->second != NULL) ? last_buffer_pair->second->GetId() : -1),
                 mFinishBufferQueue_.size());

    }
  }
  // 2. Next 切换为 Drawing 状态
  mDrawingWriteBackBuffer_ = mNextWriteBackBuffer_;
  if(mWriteBackBQ_->QueueBuffer(mNextWriteBackBuffer_)){
    HWC2_ALOGE("display=%d WBBuffer Queue fail, w=%d h=%d format=%d",
                                      iWriteBackDisplayId_,
                                      iWBWidth_,
                                      iWBHeight_,
                                      iWBFormat_);
    return -1;
  }

  // 3. 申请 Next Buffer
  std::shared_ptr<DrmBuffer> next
    = mWriteBackBQ_->DequeueDrmBuffer(iWBWidth_,
                                      iWBHeight_,
                                      iWBFormat_,
                                      RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                      MALI_GRALLOC_USAGE_NO_AFBC,
                                      "WriteBackBuffer");
  if(!next->initCheck()){
    HWC2_ALOGE("display=%d WBBuffer Dequeue fail, w=%d h=%d format=%d",
                                      iWriteBackDisplayId_,
                                      iWBWidth_,
                                      iWBHeight_,
                                      iWBFormat_);
    return -1;
  }

  HWC2_ALOGD_IF_INFO("display=%d success, w=%d h=%d format=%d",
                                    iWriteBackDisplayId_,
                                    iWBWidth_,
                                    iWBHeight_,
                                    iWBFormat_);

  mNextWriteBackBuffer_ = next;
  return 0;
}
}  // namespace android
