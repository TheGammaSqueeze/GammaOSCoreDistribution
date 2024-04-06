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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
#define LOG_TAG "hwc-drm-display-compositor"

#include "drmdisplaycompositor.h"

#include <pthread.h>
#include <sched.h>
#include <stdlib.h>
#include <time.h>
#include <sstream>
#include <vector>

#include "drm/drm_mode.h"
#include <log/log.h>
#include <sync/sync.h>
#include <utils/Trace.h>

// System property
#include <cutils/properties.h>

#include "utils/autolock.h"
#include "drmcrtc.h"
#include "drmdevice.h"
#include "drmplane.h"
#include "rockchip/drmtype.h"
#include "rockchip/utils/drmdebug.h"
#include "rockchip/producer/drmvideoproducer.h"

#include <rga.h>

#define DRM_DISPLAY_COMPOSITOR_MAX_QUEUE_DEPTH 1

static const uint32_t kWaitWritebackFence = 100;  // ms
static const int64_t kOneSecondNs = 1 * 1000 * 1000 * 1000;

#define hwcMIN(x, y)			(((x) <= (y)) ?  (x) :  (y))
#define hwcMAX(x, y)			(((x) >= (y)) ?  (x) :  (y))

#ifndef ALIGN
#define ALIGN( value, base ) (((value) + ((base) - 1)) & ~((base) - 1))
#endif

#define ALIGN_DOWN_INT( value, base)	(((int)(value)) & (~(base-1)) )

#ifndef YUV_ALIGN
#define YUV_ALIGN 2
#endif

#ifndef RGB_ALIGN
#define RGB_ALIGN 1
#endif

#ifndef IS_ALIGN
#define IS_ALIGN(val, align) (((val) & (align - 1)) == 0)
#endif

#ifndef ALIGN_DOWN
#define ALIGN_DOWN(value, base) (value & (~(base - 1)))
#endif

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]))
#endif


namespace android {

class CompositorVsyncCallback : public VsyncCallback {
 public:
  explicit CompositorVsyncCallback(DrmDisplayCompositor *compositor)
      : compositor_(compositor) {
  }

  void Callback(int display, int64_t timestamp) {
    compositor_->Vsync(display, timestamp);
  }

 private:
  DrmDisplayCompositor *compositor_;
};

DrmDisplayCompositor::DrmDisplayCompositor()
    : resource_manager_(NULL),
      display_(-1),
      worker_(this),
      initialized_(false),
      active_(false),
      use_hw_overlays_(true),
      dump_frames_composited_(0),
      dump_last_timestamp_ns_(0),
      flatten_countdown_(FLATTEN_COUNTDOWN_INIT),
      writeback_fence_(-1) {
  struct timespec ts;
  if (clock_gettime(CLOCK_MONOTONIC, &ts))
    return;
  dump_last_timestamp_ns_ = ts.tv_sec * 1000 * 1000 * 1000 + ts.tv_nsec;
}

DrmDisplayCompositor::~DrmDisplayCompositor() {
  if (!initialized_)
    return;

  //vsync_worker_.Exit();
  int ret = pthread_mutex_lock(&lock_);
  if (ret)
    ALOGE("Failed to acquire compositor lock %d", ret);

  worker_.Exit();

  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  if (mode_.blob_id)
    drm->DestroyPropertyBlob(mode_.blob_id);
  if (mode_.old_blob_id)
    drm->DestroyPropertyBlob(mode_.old_blob_id);


  while (!composite_queue_.empty()) {
    composite_queue_.front().reset();
    composite_queue_.pop();
  }

  active_composition_.reset();

  ret = pthread_mutex_unlock(&lock_);
  if (ret)
    ALOGE("Failed to acquire compositor lock %d", ret);

  pthread_mutex_destroy(&lock_);
  pthread_cond_destroy(&composite_queue_cond_);
}

int DrmDisplayCompositor::Init(ResourceManager *resource_manager, int display) {
  resource_manager_ = resource_manager;
  display_ = display;
  DrmDevice *drm = resource_manager_->GetDrmDevice(display);
  if (!drm) {
    ALOGE("Could not find drmdevice for display %d",display);
    return -EINVAL;
  }

  if(initialized_)
    return 0;

  int ret = pthread_mutex_init(&lock_, NULL);
  if (ret) {
    ALOGE("Failed to initialize drm compositor lock %d\n", ret);
    return ret;
  }
  planner_ = Planner::CreateInstance(drm);

  ret = worker_.Init();
  if (ret) {
    pthread_mutex_destroy(&lock_);
    ALOGE("Failed to initialize compositor worker %d\n", ret);
    return ret;
  }

  pthread_cond_init(&composite_queue_cond_, NULL);

//  vsync_worker_.Init(drm, display_);
//  auto callback = std::make_shared<CompositorVsyncCallback>(this);
//  vsync_worker_.RegisterCallback(callback);

  DrmVideoProducer* dvp = DrmVideoProducer::getInstance();
  if(dvp->Init()){
    HWC2_ALOGI("DrmVideoProducer Init fail.");
  }

  initialized_ = true;
  return 0;
}

std::unique_ptr<DrmDisplayComposition> DrmDisplayCompositor::CreateComposition()
    const {
  return std::unique_ptr<DrmDisplayComposition>(new DrmDisplayComposition());
}

int DrmDisplayCompositor::QueueComposition(
    std::unique_ptr<DrmDisplayComposition> composition) {
  ATRACE_CALL();
  switch (composition->type()) {
    case DRM_COMPOSITION_TYPE_FRAME:
      if (!active_){
        HWC2_ALOGD_IF_INFO("active_=%d skip frame_no=%" PRIu64 , active_, composition->frame_no());
        return -ENODEV;
      }
      break;
    case DRM_COMPOSITION_TYPE_DPMS:
      /*
       * Update the state as soon as we get it so we can start/stop queuing
       * frames asap.
       */
      active_ = (composition->dpms_mode() == DRM_MODE_DPMS_ON);
      return 0;
    case DRM_COMPOSITION_TYPE_MODESET:
      break;
    case DRM_COMPOSITION_TYPE_EMPTY:
      return 0;
    default:
      ALOGE("Unknown composition type %d/%d", composition->type(), composition->display());
      return -ENOENT;
  }

  if(!initialized_)
    return -EPERM;


  AutoLock lock(&lock_, __func__);
  if (lock.Lock())
    return -EAGAIN;

  if (!active_){
    HWC2_ALOGD_IF_INFO("active_=%d skip frame_no=%" PRIu64 , active_, composition->frame_no());
    return -ENODEV;
  }

  display_ = composition->display();
  // Block the queue if it gets too large. Otherwise, SurfaceFlinger will start
  // to eat our buffer handles when we get about 1 second behind.

  while(mapDisplayHaveQeueuCnt_[composition->display()]
        >= GetCompositeQueueMaxSize(composition.get())){
    pthread_cond_wait(&composite_queue_cond_,&lock_);
  }

  mapDisplayHaveQeueuCnt_[composition->display()]++;
  composite_queue_.push(std::move(composition));
  clear_ = false;
  lock.Unlock();

  worker_.Signal();
  return 0;
}


std::unique_ptr<DrmDisplayComposition>
DrmDisplayCompositor::CreateInitializedComposition() const {
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    ALOGE("Failed to find crtc for display = %d", display_);
    return std::unique_ptr<DrmDisplayComposition>();
  }
  std::unique_ptr<DrmDisplayComposition> comp = CreateComposition();
  std::shared_ptr<Importer> importer = resource_manager_->GetImporter(display_);
  if (!importer) {
    ALOGE("Failed to find resources for display = %d", display_);
    return std::unique_ptr<DrmDisplayComposition>();
  }
  int ret = comp->Init(drm, crtc, importer.get(), planner_.get(), 0, -1);
  if (ret) {
    ALOGE("Failed to init composition for display = %d", display_);
    return std::unique_ptr<DrmDisplayComposition>();
  }
  return comp;
}

std::tuple<uint32_t, uint32_t, int>
DrmDisplayCompositor::GetActiveModeResolution() {
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  DrmConnector *connector = drm->GetConnectorForDisplay(display_);
  if (connector == NULL) {
    ALOGE("Failed to determine display mode: no connector for display %d",
          display_);
    return std::make_tuple(0, 0, -ENODEV);
  }

  const DrmMode &mode = connector->active_mode();
  return std::make_tuple(mode.h_display(), mode.v_display(), 0);
}

int DrmDisplayCompositor::DisablePlanes(DrmDisplayComposition *display_comp) {
  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    ALOGE("Failed to allocate property set");
    return -ENOMEM;
  }

  int ret;
  std::vector<DrmCompositionPlane> &comp_planes = display_comp
                                                      ->composition_planes();
  for (DrmCompositionPlane &comp_plane : comp_planes) {
    DrmPlane *plane = comp_plane.plane();
    if(!plane)
      continue;
    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                   plane->crtc_property().id(), 0) < 0 ||
          drmModeAtomicAddProperty(pset, plane->id(), plane->fb_property().id(),
                                   0) < 0;
    if (ret) {
      ALOGE("Failed to add plane %d disable to pset", plane->id());
      drmModeAtomicFree(pset);
      pset=NULL;
      return ret;
    }
  }
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  ret = drmModeAtomicCommit(drm->fd(), pset, 0, drm);
  if (ret) {
    ALOGE("Failed to commit pset ret=%d\n", ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }
  drmModeAtomicFree(pset);
  pset=NULL;
  return 0;
}

int DrmDisplayCompositor::SetupWritebackCommit(drmModeAtomicReqPtr pset,
                                               uint32_t crtc_id,
                                               DrmConnector *writeback_conn,
                                               DrmHwcBuffer *writeback_buffer) {

  int ret = 0;

  if(writeback_conn == NULL){
    return 0;
  }
  if (writeback_conn->writeback_fb_id().id() == 0 ||
      writeback_conn->writeback_out_fence().id() == 0) {
    ALOGE("Writeback properties don't exit");
    return -EINVAL;
  }
  ret = resource_manager_->UpdateWriteBackResolution(display_);
  if(ret){
    HWC2_ALOGE("UpdateWriteBackResolution fail.");
    return -1;
  }
  std::shared_ptr<DrmBuffer> wbBuffer = resource_manager_->GetNextWBBuffer();
  if(!wbBuffer->initCheck()){
    HWC2_ALOGE("wbBuffer init fail.");
    return -1;
  }

  ret = drmModeAtomicAddProperty(pset, writeback_conn->id(),
                                 writeback_conn->writeback_fb_id().id(),
                                 wbBuffer->GetFbId());
  if (ret < 0) {
    ALOGE("Failed to add writeback_fb_id");
    return ret;
  }

  if(writeback_fence_ > 0){
    close(writeback_fence_);
    writeback_fence_ = -1;
  }

  ret = drmModeAtomicAddProperty(pset, writeback_conn->id(),
                                 writeback_conn->writeback_out_fence().id(),
                                 (uint64_t)&writeback_fence_);
  if (ret < 0) {
    ALOGE("Failed to add writeback_out_fence");
    return ret;
  }

#ifndef BOARD_BUILD_GKI
  // 20230516,GKI版本若设置wb-connector crtc=0，则会使整个显示通路黑屏
  // 故暂时关闭此选项， wb-crtc属性伴随主屏 enable/disable状态进行切换
  // 下列流程移动到主屏 power on 流程
  ret = drmModeAtomicAddProperty(pset, writeback_conn->id(),
                                 writeback_conn->crtc_id_property().id(),
                                 crtc_id);
  if (ret < 0) {
    ALOGE("Failed to  attach writeback");
    return ret;
  }
#endif

  bWriteBackEnable_ = true;

  HWC2_ALOGD_IF_DEBUG("WB: id=%" PRIu64 " fbid=%d conn-id=%d crtc_id=%d", wbBuffer->GetId(),
                                                                 wbBuffer->GetFbId(),
                                                                 writeback_conn->id(),
                                                                 crtc_id);
  return 0;
}

int DrmDisplayCompositor::DisableWritebackCommit(drmModeAtomicReqPtr pset,
                                                 DrmConnector *writeback_conn) {

  int ret = 0;
  if(!bWriteBackEnable_){
    return 0;
  }

  if(writeback_conn == NULL){
    return 0;
  }

  if (writeback_conn->writeback_fb_id().id() == 0 ||
      writeback_conn->writeback_out_fence().id() == 0) {
    ALOGE("Writeback properties don't exit");
    return -EINVAL;
  }

  ret = drmModeAtomicAddProperty(pset, writeback_conn->id(),
                                 writeback_conn->writeback_fb_id().id(),
                                 0);
  if (ret < 0) {
    ALOGE("Failed to add writeback_fb_id");
    return ret;
  }

#ifndef BOARD_BUILD_GKI
  // 20230516,GKI版本若设置wb-connector crtc=0，则会使整个显示通路黑屏
  // 故暂时关闭此选项， wb-crtc属性伴随主屏 enable/disable状态进行切换
  // 下面逻辑切换到主屏 power down 流程
  ret = drmModeAtomicAddProperty(pset, writeback_conn->id(),
                                 writeback_conn->crtc_id_property().id(),
                                 0);
  if (ret < 0) {
    ALOGE("Failed to  attach writeback");
    return ret;
  }
#endif

  bWriteBackRequestDisable_ = true;
  HWC2_ALOGD_IF_DEBUG("Reset WB: conn-id=%d ", writeback_conn->id());
  return 0;
}


int DrmDisplayCompositor::CheckOverscan(drmModeAtomicReqPtr pset, DrmCrtc* crtc, int display, const char *unique_name){
  int ret = 0;
  char overscan_value[PROPERTY_VALUE_MAX]={0};
  char overscan_pro[PROPERTY_VALUE_MAX]={0};
  int left_margin = 100, right_margin= 100, top_margin = 100, bottom_margin = 100;

  snprintf(overscan_pro,PROPERTY_VALUE_MAX,"persist.vendor.overscan.%s",unique_name);
  ret = property_get(overscan_pro,overscan_value,"");
  if(!ret){
    if(display == HWC_DISPLAY_PRIMARY){
      property_get("persist.vendor.overscan.main", overscan_value, "overscan 100,100,100,100");
    }else{
      property_get("persist.vendor.overscan.aux", overscan_value, "overscan 100,100,100,100");
    }
  }

  sscanf(overscan_value, "overscan %d,%d,%d,%d", &left_margin, &top_margin,
           &right_margin, &bottom_margin);
  ALOGD_IF(LogLevel(DBG_DEBUG),"display=%d , overscan(%d,%d,%d,%d)",display,
            left_margin,top_margin,right_margin,bottom_margin);

  if (left_margin   < OVERSCAN_MIN_VALUE) left_margin   = OVERSCAN_MIN_VALUE;
  if (top_margin    < OVERSCAN_MIN_VALUE) top_margin    = OVERSCAN_MIN_VALUE;
  if (right_margin  < OVERSCAN_MIN_VALUE) right_margin  = OVERSCAN_MIN_VALUE;
  if (bottom_margin < OVERSCAN_MIN_VALUE) bottom_margin = OVERSCAN_MIN_VALUE;

  if (left_margin   > OVERSCAN_MAX_VALUE) left_margin   = OVERSCAN_MAX_VALUE;
  if (top_margin    > OVERSCAN_MAX_VALUE) top_margin    = OVERSCAN_MAX_VALUE;
  if (right_margin  > OVERSCAN_MAX_VALUE) right_margin  = OVERSCAN_MAX_VALUE;
  if (bottom_margin > OVERSCAN_MAX_VALUE) bottom_margin = OVERSCAN_MAX_VALUE;

  ret = drmModeAtomicAddProperty(pset, crtc->id(), crtc->left_margin_property().id(), left_margin) < 0 ||
        drmModeAtomicAddProperty(pset, crtc->id(), crtc->right_margin_property().id(), right_margin) < 0 ||
        drmModeAtomicAddProperty(pset, crtc->id(), crtc->top_margin_property().id(), top_margin) < 0 ||
        drmModeAtomicAddProperty(pset, crtc->id(), crtc->bottom_margin_property().id(), bottom_margin) < 0;
  if (ret) {
    ALOGE("Failed to add overscan to pset");
    return ret;
  }

  return ret;
}

int DrmDisplayCompositor::GetTimestamp() {
  struct timespec current_time;
  clock_gettime(CLOCK_MONOTONIC, &current_time);
  last_timestamp_ = current_time.tv_sec * kOneSecondNs + current_time.tv_nsec;
  return 0;
}

/*
 * Returns the timestamp of the next vsync in phase with last_timestamp_.
 * For example:
 *  last_timestamp_ = 137
 *  frame_ns = 50
 *  current = 683
 *
 *  ret = (50 * ((683 - 137)/50 + 1)) + 137
 *  ret = 687
 *
 *  Thus, we must sleep until timestamp 687 to maintain phase with the last
 *  timestamp.
 */
int64_t DrmDisplayCompositor::GetPhasedVSync(int64_t frame_ns, int64_t current) {
  if (last_timestamp_ < 0)
    return current + frame_ns;

  return frame_ns * ((current - last_timestamp_) / frame_ns + 1) +
         last_timestamp_;
}

int DrmDisplayCompositor::SyntheticWaitVBlank() {
  ATRACE_CALL();

  // WriteBack by RGA 不需要等待时间
  if(resource_manager_->isWBMode() && resource_manager_->IsWriteBackByRga()){
    return 0;
  }

  int ret = clock_gettime(CLOCK_MONOTONIC, &vsync_);
  float refresh = 60.0f;  // Default to 60Hz refresh rate
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  DrmConnector *conn = drm->GetConnectorForDisplay(display_);
  if (conn && conn->state() == DRM_MODE_CONNECTED) {
    if (conn->active_mode().v_refresh() > 0.0f)
      refresh = conn->active_mode().v_refresh();
  }

  float percentage = 0.1f; // 10% Remaining Time to the drm driver。
  int64_t phased_timestamp = GetPhasedVSync(kOneSecondNs / refresh * percentage,
                                            vsync_.tv_sec * kOneSecondNs +
                                                vsync_.tv_nsec);
  vsync_.tv_sec = phased_timestamp / kOneSecondNs;
  vsync_.tv_nsec = phased_timestamp - (vsync_.tv_sec * kOneSecondNs);
  do {
    ret = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &vsync_, NULL);
  } while (ret == -1 && errno == EINTR);
  if (ret)
    return ret;
  return 0;
}

int DrmDisplayCompositor::CommitSidebandStream(drmModeAtomicReqPtr pset,
                                               DrmPlane* plane,
                                               DrmHwcLayer &layer,
                                               int zpos,
                                               int crtc_id){
  uint16_t eotf       = TRADITIONAL_GAMMA_SDR;
  bool     afbcd      = layer.bAfbcd_;
  bool     yuv        = layer.bYuv_;
  uint32_t rotation   = layer.transform;
  bool     sideband   = layer.bSidebandStreamLayer_;
  uint64_t blend      = 0;
  uint64_t alpha      = 0xFFFF;

  int ret = -1;
  if (layer.blending == DrmHwcBlending::kPreMult)
    alpha             = layer.alpha << 8;

  eotf                = layer.uEOTF;
  drm_colorspace colorspace   = layer.uColorSpace;

  // TvInput 暂时不需要这个属性，20220816
  // static char last_prop[100] = {0};
  // char prop[100] = {0};
  // sprintf(prop, "%d-%d-%d-%d-%d-%d-%d-%d", \
  //     (int)layer.source_crop.left,\
  //     (int)layer.source_crop.top,\
  //     (int)layer.source_crop.right,\
  //     (int)layer.source_crop.bottom,\
  //     layer.display_frame.left,\
  //     layer.display_frame.top,\
  //     layer.display_frame.right,\
  //     layer.display_frame.bottom);
  // if(strcmp(prop,last_prop)){
  //   property_set("vendor.hwc.sideband.crop", prop);
  //   strncpy(last_prop, prop, sizeof(last_prop));
  // }

  if (plane->blend_property().id()) {
    switch (layer.blending) {
      case DrmHwcBlending::kPreMult:
        std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
            "Pre-multiplied");
        break;
      case DrmHwcBlending::kCoverage:
        std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
            "Coverage");
        break;
      case DrmHwcBlending::kNone:
      default:
        std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
            "None");
        break;
    }
  }

  ret = drmModeAtomicAddProperty(pset, plane->id(),
                                plane->zpos_property().id(),
                                zpos) < 0;

  if(plane->async_commit_property().id()) {
    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                  plane->async_commit_property().id(),
                                  sideband == true ? 1 : 0) < 0;
    if (ret) {
      ALOGE("Failed to add async_commit_property property %d to plane %d",
            plane->async_commit_property().id(), plane->id());
      return ret;
    }
  }

  if (plane->rotation_property().id()) {
    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                  plane->rotation_property().id(),
                                  rotation) < 0;
    if (ret) {
      ALOGE("Failed to add rotation property %d to plane %d",
            plane->rotation_property().id(), plane->id());
      return ret;
    }
  }

  if (plane->alpha_property().id()) {
    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                  plane->alpha_property().id(),
                                  alpha) < 0;
    if (ret) {
      ALOGE("Failed to add alpha property %d to plane %d",
            plane->alpha_property().id(), plane->id());
      return ret;
    }
  }

  if (plane->blend_property().id()) {
    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                  plane->blend_property().id(),
                                  blend) < 0;
    if (ret) {
      ALOGE("Failed to add pixel blend mode property %d to plane %d",
            plane->blend_property().id(), plane->id());
      return ret;
    }
  }

  if(plane->get_hdr2sdr() && plane->eotf_property().id()) {
    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                  plane->eotf_property().id(),
                                  eotf) < 0;
    if (ret) {
      ALOGE("Failed to add eotf property %d to plane %d",
            plane->eotf_property().id(), plane->id());
      return ret;
    }
  }

  /*if(plane->colorspace_property().id()) {
    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                  plane->colorspace_property().id(),
                                  colorspace) < 0;
    if (ret) {
      ALOGE("Failed to add colorspace property %d to plane %d",
            plane->colorspace_property().id(), plane->id());
      return ret;
    }
  }*/

  HWC2_ALOGD_IF_INFO("SidebandStreamLayer plane-id=%d name=%s zpos=%d crtc-id=%d not to commit frame.",
                      plane->id(), plane->name(),zpos, crtc_id);
  return 0;
}

int DrmDisplayCompositor::CollectModeSetInfo(drmModeAtomicReqPtr pset,
                                             DrmDisplayComposition *display_comp,
                                             bool is_sideband_collect) {
  ATRACE_CALL();
  int ret = 0;

  // RK3528 平台 Sideband 后续流程会处理
  if(gIsRK3528() && IsSidebandMode() && !is_sideband_collect){
    HWC2_ALOGD_IF_INFO("SidebandMode skip normal hdr modeset");
    return 0;
  }

  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  //uint64_t out_fences[drm->crtcs().size()];

  DrmConnector *connector = drm->GetConnectorForDisplay(display_);
  if (!connector) {
    ALOGE("Could not locate connector for display %d", display_);
    return -ENODEV;
  }
  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    ALOGE("Could not locate crtc for display %d", display_);
    return -ENODEV;
  }

  // 由 VividHdr 切换到其他 HDR 状态
  if(display_comp->hdr_mode() != DRM_HWC_METADATA_HDR){
    if(current_mode_set_.hdr_.mode_ == DRM_HWC_METADATA_HDR){
      // 释放上一次的 Blob
      if (hdr_blob_id_){
          drm->DestroyPropertyBlob(hdr_blob_id_);
          hdr_blob_id_ = 0;
      }
      ret = drmModeAtomicAddProperty(pset, crtc->id(), crtc->hdr_ext_data().id(), hdr_blob_id_);
      if (ret < 0) {
        HWC2_ALOGE("Failed to add metadata-Hdr crtc-id=%d hdr_ext_data-prop[%d]",
                    crtc->id(), crtc->hdr_ext_data().id());
      }
    }

    if(display_comp->hdr_mode() != current_mode_set_.hdr_.mode_ ||
       display_comp->has_10bit_Yuv() != current_mode_set_.hdr_.bHasYuv10bit_){
      // 进入HDR10/SDR的处理逻辑
      ret = connector->switch_hdmi_hdr_mode(pset, display_comp->dataspace(), display_comp->has_10bit_Yuv());
      if(ret){
        ALOGE("display %d enable hdr fail. datespace=%x",
                display_, display_comp->dataspace());
      }else{
        HWC2_ALOGD_IF_INFO("%s HDR mode %s.", display_comp->hdr_mode() ? "Enable" : "Disable",
                                              display_comp->has_10bit_Yuv() ? "10bit" : "8bit");
        request_mode_set_.hdr_.mode_    = display_comp->hdr_mode();
        request_mode_set_.hdr_.bHasYuv10bit_    = display_comp->has_10bit_Yuv();
        request_mode_set_.hdr_.datespace_ = display_comp->dataspace();
        need_mode_set_ = true;
      }
    }
  }else{ // 进入 Metadata Hdr 状态
    for(auto &layer : display_comp->layers()){
      if(layer.IsMetadataHdr_){
        // 进入HDR10/SDR的处理逻辑
        hdr_output_metadata hdr_metadata;
        memcpy(&hdr_metadata,
                &layer.metadataHdrParam_.target_display_data,
                sizeof(struct hdr_output_metadata));
        ret = connector->switch_hdmi_hdr_mode_by_medadata(pset,
                                                              layer.metadataHdrParam_.hdr_hdmi_meta.color_prim,
                                                              &hdr_metadata,
                                                              layer.bYuv10bit_);
        if(ret){
          ALOGE("display %d enable hdr fail.", display_);
        }else{
          HWC2_ALOGD_IF_INFO("%s HDR mode %s.", display_comp->hdr_mode() ? "Enable" : "Disable",
                                            display_comp->has_10bit_Yuv() ? "10bit" : "8bit");
          request_mode_set_.hdr_.mode_    = display_comp->hdr_mode();
          request_mode_set_.hdr_.bHasYuv10bit_    = display_comp->has_10bit_Yuv();
          request_mode_set_.hdr_.datespace_ = display_comp->dataspace();
          need_mode_set_ = true;
        }
        // 释放上一次的 Blob
        if (hdr_blob_id_){
            drm->DestroyPropertyBlob(hdr_blob_id_);
            hdr_blob_id_ = 0;
        }
        ret = drmModeCreatePropertyBlob(drm->fd(), (void *)(&layer.metadataHdrParam_.hdr_reg),
                                  sizeof(layer.metadataHdrParam_.hdr_reg), &hdr_blob_id_);
        if(ret < 0){
          HWC2_ALOGE("Failed to drmModeCreatePropertyBlob crtci-id=%d hdr_ext_data-prop[%d]",
                      crtc->id(), crtc->hdr_ext_data().id());
        }
        ret = 0;
        ret = drmModeAtomicAddProperty(pset, crtc->id(), crtc->hdr_ext_data().id(), hdr_blob_id_);
        if (ret < 0) {
          HWC2_ALOGE("Failed to add metadata_hdr crtci-id=%d hdr_ext_data-prop[%d]",
                      crtc->id(), crtc->hdr_ext_data().id());
        }else{
          HWC2_ALOGD_IF_INFO("%s MetadataHdr mode.", display_comp->hdr_mode() ? "Enable" : "Disable");
          request_mode_set_.hdr_.mode_    = display_comp->hdr_mode();
          request_mode_set_.hdr_.bHasYuv10bit_  = display_comp->has_10bit_Yuv();
          request_mode_set_.hdr_.datespace_ = display_comp->dataspace();
          need_mode_set_ = true;
        }
      }
    }
  }

  return 0;
}

int DrmDisplayCompositor::UpdateModeSetState() {
  ATRACE_CALL();
  AutoLock lock(&lock_, __func__);
  if (lock.Lock())
    return -1;

  if(!need_mode_set_){
    return 0;
  }

  // Update HDR state：
  current_mode_set_.hdr_.mode_    = request_mode_set_.hdr_.mode_;
  current_mode_set_.hdr_.bHasYuv10bit_ = request_mode_set_.hdr_.bHasYuv10bit_ ;
  current_mode_set_.hdr_.datespace_ = request_mode_set_.hdr_.datespace_;

  need_mode_set_ = false;
  return 0;
}

int DrmDisplayCompositor::UpdateSidebandState() {
  ATRACE_CALL();
  AutoLock lock(&lock_, __func__);
  if (lock.Lock())
    return -1;

  DrmVideoProducer* dvp = DrmVideoProducer::getInstance();
  if(!dvp->IsValid()){
    HWC2_ALOGD_IF_ERR("SidebandStream: DrmVideoProducer is invalidate.");
    return -1;
  }


  // 1. ct != dt, 进入切换逻辑
  if(current_sideband2_.tunnel_id_ != drawing_sideband2_.tunnel_id_){
    if(current_sideband2_.tunnel_id_ > 0){
      //  1-1. 如果 ct > 0, dt == 0 : 开启 sideband
      if(drawing_sideband2_.tunnel_id_ == 0){
        // swap
        drawing_sideband2_.enable_ = current_sideband2_.enable_;
        drawing_sideband2_.tunnel_id_ = current_sideband2_.tunnel_id_;
        drawing_sideband2_.buffer_ = current_sideband2_.buffer_;
      }else{ //  1-2. 如果 ct > 0, dt > 0 : 切换 sideband
        // 连接改变，断开连接前，先释放上一帧 ReleaseFence
        if(drawing_sideband2_.buffer_ != NULL){
          if(dvp->SignalReleaseFence(display_,
                                    drawing_sideband2_.tunnel_id_,
                                    drawing_sideband2_.buffer_->GetExternalId())){
            HWC2_ALOGE("SidebandStream: display-id=%d SignalReleaseFence fail, last buffer id=%" PRIu64 ,
                        display_, drawing_sideband2_.buffer_->GetId());
          }
        }
        // 断开旧连接
        int ret = dvp->DestoryConnection(display_, drawing_sideband2_.tunnel_id_);
        if(ret){
          HWC2_ALOGE("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " fail.",
                      display_, drawing_sideband2_.tunnel_id_);
        }else{
          HWC2_ALOGI("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " Success.",
                      display_, drawing_sideband2_.tunnel_id_);
        }
        drawing_sideband2_.enable_ = current_sideband2_.enable_;
        drawing_sideband2_.tunnel_id_ = current_sideband2_.tunnel_id_;
        drawing_sideband2_.buffer_ = current_sideband2_.buffer_;
      }
    }else{ //  1-3. 如果 ct == 0 , dt > 0 : 关闭 sideband
        if(drawing_sideband2_.buffer_ != NULL){
          if(dvp->SignalReleaseFence(display_,
                                    drawing_sideband2_.tunnel_id_,
                                    drawing_sideband2_.buffer_->GetExternalId())){
            HWC2_ALOGE("SidebandStream: display-id=%d SignalReleaseFence fail, last buffer id=%" PRIu64 ,
                        display_, drawing_sideband2_.buffer_->GetId());
          }
        }
        // 断开旧连接
        int ret = dvp->DestoryConnection(display_, drawing_sideband2_.tunnel_id_);
        if(ret){
          HWC2_ALOGE("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " fail.",
                      display_, drawing_sideband2_.tunnel_id_);
        }else{
          HWC2_ALOGI("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " Success.",
                      display_, drawing_sideband2_.tunnel_id_);
        }
        drawing_sideband2_.enable_ = current_sideband2_.enable_;
        drawing_sideband2_.tunnel_id_ = current_sideband2_.tunnel_id_;
        drawing_sideband2_.buffer_ = current_sideband2_.buffer_;
    }
  }else if(current_sideband2_.tunnel_id_ > 0){  // 2. ct == dt, 进入送显逻辑
    // 若上一帧已显示完成，且当前帧与上一帧不同
    if(drawing_sideband2_.buffer_ != NULL && drawing_sideband2_.buffer_ != current_sideband2_.buffer_){
      if(dvp->SignalReleaseFence(display_,
                                drawing_sideband2_.tunnel_id_,
                                drawing_sideband2_.buffer_->GetExternalId())){
        HWC2_ALOGE("SidebandStream: SignalReleaseFence fail, last buffer id=%" PRIu64 ,
                    drawing_sideband2_.buffer_->GetId());
      }
    }
    drawing_sideband2_.enable_ = current_sideband2_.enable_;
    drawing_sideband2_.buffer_ = current_sideband2_.buffer_;
  }

  // current_sideband2_.enable_ = false;
  // current_sideband2_.tunnel_id_ = 0;
  // current_sideband2_.buffer_ = NULL;
  return 0;
}

int DrmDisplayCompositor::CollectCommitInfo(drmModeAtomicReqPtr pset,
                                            DrmDisplayComposition *display_comp,
                                            bool test_only,
                                            DrmConnector *writeback_conn,
                                            DrmHwcBuffer *writeback_buffer) {
  ATRACE_CALL();

  int ret = 0;

  std::vector<DrmHwcLayer> &layers = display_comp->layers();
  std::vector<DrmCompositionPlane> &comp_planes = display_comp
                                                      ->composition_planes();
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  //uint64_t out_fences[drm->crtcs().size()];

  DrmConnector *connector = drm->GetConnectorForDisplay(display_);
  if (!connector) {
    ALOGE("Could not locate connector for display %d", display_);
    return -ENODEV;
  }

  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    ALOGE("Could not locate crtc for display %d", display_);
    return -ENODEV;
  }


  frame_no_ = display_comp->frame_no();
  // Enable DrmDisplayCompositor sideband2 mode
  current_sideband2_.enable_ = display_comp->has_sideband2();
  current_sideband2_.tunnel_id_ = display_comp->get_sideband_tunnel_id();
  current_sideband2_.buffer_ = NULL;

  // WriteBack Mode
  if(!test_only){
    // 只有 WriteBack by vop 需要执行以下步骤
    if(resource_manager_->isWBMode() &&
       resource_manager_->IsWriteBackByVop()){
      int wbDisplay = resource_manager_->GetWBDisplay();
      if(wbDisplay == display_){
        ret = SetupWritebackCommit(pset,
                                  crtc->id(),
                                  drm->GetWritebackConnectorForDisplay(wbDisplay),
                                  NULL);
        if (ret < 0) {
          ALOGE("Failed to Setup Writeback Commit ret = %d", ret);
          return ret;
        }
      }
    }else{
      DisableWritebackCommit(pset, drm->GetWritebackConnectorForDisplay(0));
    }
  }

  if (crtc->can_overscan()) {
    // 如果当前显示分辨率为隔行扫描，则不不建议使用 overscan 功能，建议采用图层scale实现
    if(connector->current_mode().id() > 0 && connector->current_mode().interlaced() == 0){
      ret = CheckOverscan(pset,crtc,display_,connector->unique_name());
      if(ret < 0){
        return ret;
      }
    }else{
      ret = drmModeAtomicAddProperty(pset, crtc->id(), crtc->left_margin_property().id(), 100) < 0  ||
                drmModeAtomicAddProperty(pset, crtc->id(), crtc->right_margin_property().id(), 100) < 0 ||
                drmModeAtomicAddProperty(pset, crtc->id(), crtc->top_margin_property().id(), 100) < 0   ||
                drmModeAtomicAddProperty(pset, crtc->id(), crtc->bottom_margin_property().id(), 100) < 0;
      if (ret) {
        ALOGE("Failed to add overscan to pset");
        return ret;
      }
    }
  }

  // RK3566 mirror commit
  bool mirror_commit = false;
  DrmCrtc *mirror_commit_crtc = NULL;
  for (DrmCompositionPlane &comp_plane : comp_planes) {
    if(comp_plane.mirror()){
      mirror_commit = true;
      mirror_commit_crtc = comp_plane.crtc();
      break;
    }
  }
  if(mirror_commit){
    if (mirror_commit_crtc->can_overscan()) {
      int mirror_display_id = mirror_commit_crtc->display();
      DrmConnector *mirror_connector = drm->GetConnectorForDisplay(mirror_display_id);
      if (!mirror_connector) {
        ALOGE("Could not locate connector for display %d", mirror_display_id);
      }else{
        // 如果当前显示分辨率为隔行扫描，则不不建议使用 overscan 功能，建议采用图层scale实现
        if(mirror_connector->current_mode().id() > 0 &&
          mirror_connector->current_mode().interlaced() == 0){
          ret = CheckOverscan(pset, mirror_commit_crtc,
                                  mirror_display_id,
                                  mirror_connector->unique_name());
          if(ret < 0){
            return ret;
          }
        }else{
          ret = drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->left_margin_property().id(), 100) < 0  ||
                    drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->right_margin_property().id(), 100) < 0 ||
                    drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->top_margin_property().id(), 100) < 0   ||
                    drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->bottom_margin_property().id(), 100) < 0;
          if (ret) {
            ALOGE("Failed to add overscan to pset");
            return ret;
          }
        }
      }
    }
  }

  int zpos = -1;

  for (DrmCompositionPlane &comp_plane : comp_planes) {
    DrmPlane *plane = comp_plane.plane();
    std::vector<size_t> &source_layers = comp_plane.source_layers();
    int fb_id = -1;
    hwc_rect_t display_frame;
    // Commit mirror function
    hwc_rect_t display_frame_mirror;
    hwc_frect_t source_crop;
    uint64_t rotation = 0;
    uint64_t alpha = 0xFFFF;
    uint64_t blend = 0;
    uint16_t eotf = TRADITIONAL_GAMMA_SDR;
    drm_colorspace  colorspace;

    int dst_l,dst_t,dst_w,dst_h;
    int src_l,src_t,src_w,src_h;
    bool afbcd = false, yuv = false, yuv10bit = false, sideband = false;
    bool is_metadata_hdr = false;

    crtc = comp_plane.crtc();

    if (comp_plane.type() != DrmCompositionPlane::Type::kDisable) {

      if(source_layers.empty()){
        ALOGE("Can't handle empty source layer CompositionPlane.");
        continue;
      }

      if (source_layers.size() > 1) {
        ALOGE("Can't handle more than one source layer sz=%zu type=%d",
              source_layers.size(), comp_plane.type());
        continue;
      }

      if (source_layers.front() >= layers.size()) {
        ALOGE("Source layer index %zu out of bounds %zu type=%d",
              source_layers.front(), layers.size(), comp_plane.type());
        break;
      }

      DrmHwcLayer &layer = layers[source_layers.front()];

      if (!test_only && layer.acquire_fence->isValid()){
        if(layer.acquire_fence->wait(500)){
          HWC2_ALOGE("display=%d Wait AcquireFence 500ms failed! frame = %" PRIu64 " Info: size=%d act=%d signal=%d err=%d ,LayerName=%s ",
                            display_,
                            display_comp->frame_no(),
                            layer.acquire_fence->getSize(),
                            layer.acquire_fence->getActiveCount(),
                            layer.acquire_fence->getSignaledCount(),
                            layer.acquire_fence->getErrorCount(),
                            layer.sLayerName_.c_str());
        }
        layer.acquire_fence->destroy();
      }

      if (!layer.buffer && !layer.bSidebandStreamLayer_) {
        ALOGE("Expected a valid framebuffer for pset");
        break;
      }

      zpos = comp_plane.get_zpos();
      if(display_comp->display() > 0xf)
        zpos=1;
      if(zpos < 0)
        ALOGE("The zpos(%d) is invalid", zpos);

	    // todo
      sideband = layer.bSidebandStreamLayer_;
      if(sideband){
        if(!layer.bSideband2_){
          ret = CommitSidebandStream(pset, plane, layer, zpos, crtc->id());
          if(ret){
            HWC2_ALOGE("CommitSidebandStream fail");
          }
        }
        continue;
      }

#ifdef RK3528
      if(layer.bNeedPreScale_ && !layer.bIsPreScale_){
        HWC2_ALOGD_IF_WARN("%s bNeedPreScale_=%d bIsPreScale_=%d skip until PreScale ready.",
                            layer.sLayerName_.c_str(),
                            layer.bNeedPreScale_,
                            layer.bIsPreScale_);
        continue;
      }
#endif

      fb_id = layer.buffer->fb_id;
      display_frame = layer.display_frame;
      display_frame_mirror = layer.display_frame_mirror;
      source_crop = layer.source_crop;
      if (layer.blending == DrmHwcBlending::kPreMult) alpha = layer.alpha << 8;
      eotf = layer.uEOTF;
      afbcd = layer.bAfbcd_;
      yuv = layer.bYuv_;
      colorspace = layer.uColorSpace;
      yuv10bit = layer.bYuv10bit_;
      if (plane->blend_property().id()) {
        switch (layer.blending) {
          case DrmHwcBlending::kPreMult:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "Pre-multiplied");
            break;
          case DrmHwcBlending::kCoverage:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "Coverage");
            break;
          case DrmHwcBlending::kNone:
          default:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "None");
            break;
        }
      }

      rotation = layer.transform;
      is_metadata_hdr = layer.IsMetadataHdr_;
    }

    // Disable the plane if there's no framebuffer
    if (fb_id < 0) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->crtc_property().id(), 0) < 0 ||
            drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->fb_property().id(), 0) < 0;
      // set async_cmmit = 0
      if(plane->async_commit_property().id()) {
        ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->async_commit_property().id(),
                                      0) < 0;
        if (ret) {
          ALOGE("Failed to add async_commit_property property %d to plane %d",
                plane->async_commit_property().id(), plane->id());
          continue;
        }
      }
      if (ret) {
        ALOGE("Failed to add plane %d disable to pset", plane->id());
        break;
      }
      continue;
    }
    src_l = (int)source_crop.left;
    src_t = (int)source_crop.top;
    src_w = (int)(source_crop.right - source_crop.left);
    src_h = (int)(source_crop.bottom - source_crop.top);

    // Commit mirror function
    if(comp_plane.mirror()){
      dst_l = display_frame_mirror.left;
      dst_t = display_frame_mirror.top;
      dst_w = display_frame_mirror.right - display_frame_mirror.left;
      dst_h = display_frame_mirror.bottom - display_frame_mirror.top;
    }else{
      dst_l = display_frame.left;
      dst_t = display_frame.top;
      dst_w = display_frame.right - display_frame.left;
      dst_h = display_frame.bottom - display_frame.top;
    }

    if(yuv){
      src_l = ALIGN_DOWN(src_l, 2);
      src_t = ALIGN_DOWN(src_t, 2);
    }

    // 非afbc 10bit 片源 x_offset 需要8对齐
    if(yuv10bit && !afbcd){
      src_l = ALIGN_DOWN(src_l, 8);
    }

    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                   plane->crtc_property().id(), crtc->id()) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->fb_property().id(), fb_id) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_x_property().id(),
                                    dst_l) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_y_property().id(),
                                    dst_t) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_w_property().id(),
                                    dst_w) <
           0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_h_property().id(),
                                    dst_h) <
           0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_x_property().id(),
                                    (int)(src_l) << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_y_property().id(),
                                    (int)(src_t) << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_w_property().id(),
                                    (int)(src_w)
                                        << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_h_property().id(),
                                    (int)(src_h)
                                        << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                   plane->zpos_property().id(), zpos) < 0;
    if (ret) {
      ALOGE("Failed to add plane %d to set", plane->id());
      break;
    }

    size_t index=0;
    std::ostringstream out_log;

    out_log << "DrmDisplayCompositor[" << index << "]"
            << " frame_no=" << display_comp->frame_no()
            << " display=" << display_comp->display()
            << " plane=" << (plane ? plane->name() : "Unknow")
            << " crct id=" << crtc->id()
            << " fb id=" << fb_id
            << " display_frame[" << dst_l << ","
            << dst_t << "," << dst_w
            << "," << dst_h << "]"
            << " source_crop[" << src_l << ","
            << src_t << "," << src_w
            << "," << src_h << "]"
            << ", zpos=" << zpos
            ;
    index++;

    if (plane->rotation_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->rotation_property().id(),
                                     rotation) < 0;
      if (ret) {
        ALOGE("Failed to add rotation property %d to plane %d",
              plane->rotation_property().id(), plane->id());
        break;
      }
      out_log << " rotation=" << rotation;
    }

    if (plane->alpha_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->alpha_property().id(), alpha) < 0;
      if (ret) {
        ALOGE("Failed to add alpha property %d to plane %d",
              plane->alpha_property().id(), plane->id());
        break;
      }
      out_log << " alpha=" << std::hex <<  alpha;
    }

    if (plane->blend_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->blend_property().id(), blend) < 0;
      if (ret) {
        ALOGE("Failed to add pixel blend mode property %d to plane %d",
              plane->blend_property().id(), plane->id());
        break;
      }
      out_log << " blend mode =" << blend;
    }

    if(plane->get_hdr2sdr() && plane->eotf_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->eotf_property().id(),
                                     eotf) < 0;
      if (ret) {
        ALOGE("Failed to add eotf property %d to plane %d",
              plane->eotf_property().id(), plane->id());
        break;
      }
      out_log << " eotf=" << std::hex <<  eotf;
    }

    if(gIsDrmVerison6_1()){
      if(plane->kernel6_1_color_encoding().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->kernel6_1_color_encoding().id(),
                                      colorspace.colorspace_kernel_6_1_.color_encoding_) < 0;
        if (ret) {
          ALOGE("Failed to add kernel6_1_color_encoding property %d to plane %d",
                plane->kernel6_1_color_encoding().id(), plane->id());
          break;
        }
        out_log << " color_encoding=" << std::hex <<  colorspace.colorspace_kernel_6_1_.color_encoding_;
      }

      if(plane->kernel6_1_color_range().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->kernel6_1_color_range().id(),
                                      colorspace.colorspace_kernel_6_1_.color_range_) < 0;
        if (ret) {
          ALOGE("Failed to add kernel6_1_color_range property %d to plane %d",
                plane->kernel6_1_color_range().id(), plane->id());
          break;
        }
        out_log << " color_range=" << std::hex <<  colorspace.colorspace_kernel_6_1_.color_range_;
      }
    }else{
      if(plane->colorspace_property().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->colorspace_property().id(),
                                      colorspace.colorspace_kernel_510_) < 0;
        if (ret) {
          ALOGE("Failed to add colorspace property %d to plane %d",
                plane->colorspace_property().id(), plane->id());
          break;
        }
        out_log << " colorspace=" << std::hex <<  colorspace.colorspace_kernel_510_;
      }
    }

    if(plane->async_commit_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->async_commit_property().id(),
                                    sideband == true ? 1 : 0) < 0;
      if (ret) {
        ALOGE("Failed to add async_commit_property property %d to plane %d",
              plane->async_commit_property().id(), plane->id());
        break;
      }

      out_log << " async_commit=" << sideband;
    }

    // Next hdr base layer
    if(is_metadata_hdr) {
      out_log << " is_metadata_hdr=" << is_metadata_hdr;
    }

    HWC2_ALOGD_IF_DEBUG("%s",out_log.str().c_str());
    out_log.clear();
  }
  return ret;
}

int DrmDisplayCompositor::CollectInfo(
    std::unique_ptr<DrmDisplayComposition> composition,
    int status, bool writeback) {
  ATRACE_CALL();

  if(!pset_){
    pset_ = drmModeAtomicAlloc();
    if (!pset_) {
      ALOGE("Failed to allocate property set");
      return -1 ;
    }
  }

  int ret = status;
  if (!ret && !clear_) {
    if (writeback && !CountdownExpired()) {
      ALOGE("Abort playing back scene");
      return -1;
    }
    ret = CollectCommitInfo(pset_, composition.get(), false);
    if (ret) {
      ALOGE("CollectCommitInfo failed for display %d", display_);
      // Disable the hw used by the last active composition. This allows us to
      // signal the release fences from that composition to avoid hanging.
      drmModeAtomicFree(pset_);
      pset_ = NULL;
      return ret;
    }

    // 配置 modeset 信息
    ret = CollectModeSetInfo(pset_, composition.get(), false);
    if (ret) {
      ALOGE("CollectModeSetInfo failed for display %d", display_);
      // Disable the hw used by the last active composition. This allows us to
      // signal the release fences from that composition to avoid hanging.
      drmModeAtomicFree(pset_);
      pset_ = NULL;
      return ret;
    }
  }

  collect_composition_map_.insert(std::make_pair<int,std::unique_ptr<DrmDisplayComposition>>(composition->display(),std::move(composition)));
  return 0;
}

void DrmDisplayCompositor::Commit() {
  ATRACE_CALL();

  if (!active_){
    HWC2_ALOGD_IF_INFO("active_=%d skip frame_no=%" PRIu64 , active_, frame_no_);
    drmModeAtomicFree(pset_);
    pset_=NULL;
    return;
  }

  if(!pset_){
    ALOGE("pset_ is NULL");
    return;
  }

  // 如果 WriteBack 使用 RGA 模式， 则 调用 WriteBackByRGA 实现合成
  if(resource_manager_->isWBMode() && resource_manager_->IsWriteBackByRga()){
    int wbDisplay = resource_manager_->GetWBDisplay();
    if(wbDisplay == display_){
      WriteBackByRGA();
    }
  }


  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  int ret = drmModeAtomicCommit(drm->fd(), pset_, flags, drm);
  if (ret) {
    ALOGE("Failed to commit pset ret=%d\n", ret);
    drmModeAtomicFree(pset_);
    pset_=NULL;
  }else{
    GetTimestamp();
    UpdateModeSetState();
    UpdateSidebandState();
  }

  AutoLock lock(&lock_, __func__);
  if (lock.Lock())
    return;

  // WriteBack Fence handle.
  // 只有 WriteBack by vop 需要执行以下步骤
  if(writeback_fence_ > 0){
    if(resource_manager_->isWBMode() && resource_manager_->IsWriteBackByVop()){
      int wbDisplay = resource_manager_->GetWBDisplay();
      if(wbDisplay == display_){
        std::shared_ptr<DrmBuffer> wbBuffer = resource_manager_->GetNextWBBuffer();
        wbBuffer->SetFinishFence(writeback_fence_);
        writeback_fence_ = -1;
        resource_manager_->SwapWBBuffer(frame_no_);
      }
    }else{
      close(writeback_fence_);
      writeback_fence_ = -1;
      if(bWriteBackRequestDisable_ && ret == 0){
        bWriteBackEnable_ = false;
      }
    }
  }

  if (pset_){
    drmModeAtomicFree(pset_);
    pset_=NULL;
  }

  ++dump_frames_composited_;
  // Signal 上一帧VOP显示的ReleaseFence
  for(auto &collect_composition : collect_composition_map_){
    auto active_composition = active_composition_map_.find(collect_composition.first);
    if(active_composition != active_composition_map_.end()){
      active_composition->second->SignalCompositionDone();
      active_composition_map_.erase(active_composition);
    }
  }


  // Signal 当前帧之前被丢弃的帧的ReleaseFence
  for(auto &collect_composition : collect_composition_map_){
    // 丢帧模式下， useless_composition_queue 会存在需要被丢弃的帧
    auto &useless_queue = collect_composition.second->useless_composition_queue();
    if(useless_queue.size() > 0){
      uint64_t useless_size = useless_queue.size();
      uint64_t useless_frame_no_start = UINT64_MAX;
      uint64_t useless_frame_no_end = 0;
      while(useless_queue.size() > 0){
        std::unique_ptr<DrmDisplayComposition>  composition =
            std::move(useless_queue.front());
        if(composition->frame_no() <  useless_frame_no_start){
          useless_frame_no_start = composition->frame_no();
        }

        if(composition->frame_no() >  useless_frame_no_end){
          useless_frame_no_end = composition->frame_no();
        }

        useless_queue.pop();
        composition->SignalCompositionDone();
      }
      HWC2_ALOGD_IF_DEBUG("signal useless compositions: display=%d "
                          "size=%" PRIu64 " frame_no=%" PRIu64"->%" PRIu64 ,
                          display_,
                          useless_size,
                          useless_frame_no_start,
                          useless_frame_no_end);
    }
    // 保存当前正在显示的帧
    active_composition_map_.insert(std::move(collect_composition));
  }
  collect_composition_map_.clear();
  //flatten_countdown_ = FLATTEN_COUNTDOWN_INIT;
  //vsync_worker_.VSyncControl(!writeback);
}

int DrmDisplayCompositor::CommitFrame(DrmDisplayComposition *display_comp,
                                      bool test_only,
                                      DrmConnector *writeback_conn,
                                      DrmHwcBuffer *writeback_buffer) {
  ATRACE_CALL();

  int ret = 0;
  std::vector<DrmHwcLayer> &layers = display_comp->layers();
  std::vector<DrmCompositionPlane> &comp_planes = display_comp
                                                      ->composition_planes();
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  //uint64_t out_fences[drm->crtcs().size()];

  DrmConnector *connector = drm->GetConnectorForDisplay(display_);
  if (!connector) {
    ALOGE("Could not locate connector for display %d", display_);
    return -ENODEV;
  }
  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    ALOGE("Could not locate crtc for display %d", display_);
    return -ENODEV;
  }

  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    ALOGE("Failed to allocate property set");
    return -ENOMEM;
  }

  if (writeback_buffer != NULL) {
    if (writeback_conn == NULL) {
      ALOGE("Invalid arguments requested writeback without writeback conn");
      return -EINVAL;
    }
    ret = SetupWritebackCommit(pset, crtc->id(), writeback_conn,
                               writeback_buffer);
    if (ret < 0) {
      ALOGE("Failed to Setup Writeback Commit ret = %d", ret);
      return ret;
    }
  }

  if (crtc->can_overscan()) {
    // 如果当前显示分辨率为隔行扫描，则不不建议使用 overscan 功能，建议采用图层scale实现
    if(connector && connector->current_mode().id() > 0 && connector->current_mode().interlaced() == 0){
      ret = CheckOverscan(pset,crtc,display_,connector->unique_name());
      if(ret < 0){
        return ret;
      }
    }else{
      ret = drmModeAtomicAddProperty(pset, crtc->id(), crtc->left_margin_property().id(), 100) < 0  ||
                drmModeAtomicAddProperty(pset, crtc->id(), crtc->right_margin_property().id(), 100) < 0 ||
                drmModeAtomicAddProperty(pset, crtc->id(), crtc->top_margin_property().id(), 100) < 0   ||
                drmModeAtomicAddProperty(pset, crtc->id(), crtc->bottom_margin_property().id(), 100) < 0;
      if (ret) {
        ALOGE("Failed to add overscan to pset");
        return ret;
      }
    }
  }

  // RK3566 mirror commit
  bool mirror_commit = false;
  DrmCrtc *mirror_commit_crtc = NULL;
  for (DrmCompositionPlane &comp_plane : comp_planes) {
    if(comp_plane.mirror()){
      mirror_commit = true;
      mirror_commit_crtc = comp_plane.crtc();
      break;
    }
  }
  if(mirror_commit){
    if (mirror_commit_crtc->can_overscan()) {
      int mirror_display_id = mirror_commit_crtc->display();
      DrmConnector *mirror_connector = drm->GetConnectorForDisplay(mirror_display_id);
      if (!mirror_connector) {
        ALOGE("Could not locate connector for display %d", mirror_display_id);
      }else{
        // 如果当前显示分辨率为隔行扫描，则不不建议使用 overscan 功能，建议采用图层scale实现
        if (mirror_connector->current_mode().id() > 0 &&
          mirror_connector->current_mode().interlaced() == 0){
          ret = CheckOverscan(pset, mirror_commit_crtc,
                                  mirror_display_id,
                                  mirror_connector->unique_name());
          if(ret < 0){
            return ret;
          }
        }else{
          ret = drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->left_margin_property().id(), 100) < 0  ||
                    drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->right_margin_property().id(), 100) < 0 ||
                    drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->top_margin_property().id(), 100) < 0   ||
                    drmModeAtomicAddProperty(pset, mirror_commit_crtc->id(), mirror_commit_crtc->bottom_margin_property().id(), 100) < 0;
          if (ret) {
            ALOGE("Failed to add overscan to pset");
            return ret;
          }
        }
      }
    }
  }

  int zpos = -1;

  for (DrmCompositionPlane &comp_plane : comp_planes) {
    DrmPlane *plane = comp_plane.plane();
    std::vector<size_t> &source_layers = comp_plane.source_layers();

    int fb_id = -1;
    hwc_rect_t display_frame;
    // Commit mirror function
    hwc_rect_t display_frame_mirror;
    hwc_frect_t source_crop;
    uint64_t rotation = 0;
    uint64_t alpha = 0xFFFF;
    uint64_t blend = 0;
    uint16_t eotf = TRADITIONAL_GAMMA_SDR;
    drm_colorspace colorspace;

    int dst_l,dst_t,dst_w,dst_h;
    int src_l,src_t,src_w,src_h;
    bool yuv = false;

    crtc = comp_plane.crtc();

    if (comp_plane.type() != DrmCompositionPlane::Type::kDisable) {
      bool afbcd = false;

      if(source_layers.empty()){
        ALOGE("Can't handle empty source layer CompositionPlane.");
        continue;
      }

      if (source_layers.size() > 1) {
        ALOGE("Can't handle more than one source layer sz=%zu type=%d",
              source_layers.size(), comp_plane.type());
        continue;
      }

      if (source_layers.front() >= layers.size()) {
        ALOGE("Source layer index %zu out of bounds %zu type=%d",
              source_layers.front(), layers.size(), comp_plane.type());
        break;
      }

      DrmHwcLayer &layer = layers[source_layers.front()];

      if (!test_only && layer.acquire_fence->isValid()){
        if(layer.acquire_fence->wait(500)){
          HWC2_ALOGE("Wait AcquireFence 500ms failed! frame = %" PRIu64 " Info: size=%d act=%d signal=%d err=%d ,LayerName=%s ",
                            display_comp->frame_no(), layer.acquire_fence->getSize(),
                            layer.acquire_fence->getActiveCount(), layer.acquire_fence->getSignaledCount(),
                            layer.acquire_fence->getErrorCount(),layer.sLayerName_.c_str());
        }
        layer.acquire_fence->destroy();
      }
      if (!layer.buffer) {
        ALOGE("Expected a valid framebuffer for pset");
        break;
      }

      if(layer.bSidebandStreamLayer_){
        HWC2_ALOGI("SidebandLayer continue, iTunnelId = %d", layer.iTunnelId_);
        continue;
      }

      fb_id = layer.buffer->fb_id;
      display_frame = layer.display_frame;
      display_frame_mirror = layer.display_frame_mirror;
      source_crop = layer.source_crop;
      if (layer.blending == DrmHwcBlending::kPreMult) alpha = layer.alpha << 8;
      eotf = layer.uEOTF;
      colorspace = layer.uColorSpace;
      afbcd = layer.bAfbcd_;
      yuv = layer.bYuv_;

      if (plane->blend_property().id()) {
        switch (layer.blending) {
          case DrmHwcBlending::kPreMult:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "Pre-multiplied");
            break;
          case DrmHwcBlending::kCoverage:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "Coverage");
            break;
          case DrmHwcBlending::kNone:
          default:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "None");
            break;
        }
      }
      zpos = comp_plane.get_zpos();
      if(display_comp->display() > 0xf)
        zpos = 1;

      if(zpos < 0)
        ALOGE("The zpos(%d) is invalid", zpos);

      rotation = layer.transform;
    }

    // Disable the plane if there's no framebuffer
    if (fb_id < 0) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->crtc_property().id(), 0) < 0 ||
            drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->fb_property().id(), 0) < 0;
      if (ret) {
        ALOGE("Failed to add plane %d disable to pset", plane->id());
        break;
      }
      continue;
    }
    src_l = (int)source_crop.left;
    src_t = (int)source_crop.top;
    src_w = (int)(source_crop.right - source_crop.left);
    src_h = (int)(source_crop.bottom - source_crop.top);

    // Commit mirror function
    if(comp_plane.mirror()){
      dst_l = display_frame_mirror.left;
      dst_t = display_frame_mirror.top;
      dst_w = display_frame_mirror.right - display_frame_mirror.left;
      dst_h = display_frame_mirror.bottom - display_frame_mirror.top;
    }else{
      dst_l = display_frame.left;
      dst_t = display_frame.top;
      dst_w = display_frame.right - display_frame.left;
      dst_h = display_frame.bottom - display_frame.top;
    }

    if(yuv){
      src_l = ALIGN_DOWN(src_l, 2);
      src_t = ALIGN_DOWN(src_t, 2);
    }


    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                   plane->crtc_property().id(), crtc->id()) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->fb_property().id(), fb_id) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_x_property().id(),
                                    dst_l) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_y_property().id(),
                                    dst_t) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_w_property().id(),
                                    dst_w) <
           0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_h_property().id(),
                                    dst_h) <
           0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_x_property().id(),
                                    (int)(src_l) << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_y_property().id(),
                                    (int)(src_t) << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_w_property().id(),
                                    (int)(src_w)
                                        << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_h_property().id(),
                                    (int)(src_h)
                                        << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                   plane->zpos_property().id(), zpos) < 0;
    if (ret) {
      ALOGE("Failed to add plane %d to set", plane->id());
      break;
    }

    size_t index=0;
    std::ostringstream out_log;

    out_log << "DrmDisplayCompositor[" << index << "]"
            << " frame_no=" << display_comp->frame_no()
            << " display=" << display_comp->display()
            << " plane=" << (plane ? plane->name() : "Unknow")
            << " crct id=" << crtc->id()
            << " fb id=" << fb_id
            << " display_frame[" << dst_l << ","
            << dst_t << "," << dst_w
            << "," << dst_h << "]"
            << " source_crop[" << src_l << ","
            << src_t << "," << src_w
            << "," << src_h << "]"
            << ", zpos=" << zpos
            ;
    index++;

    if (plane->rotation_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->rotation_property().id(),
                                     rotation) < 0;
      if (ret) {
        ALOGE("Failed to add rotation property %d to plane %d",
              plane->rotation_property().id(), plane->id());
        break;
      }
      out_log << " rotation=" << rotation;
    }

    if (plane->alpha_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->alpha_property().id(), alpha) < 0;
      if (ret) {
        ALOGE("Failed to add alpha property %d to plane %d",
              plane->alpha_property().id(), plane->id());
        break;
      }
      out_log << " alpha=" << std::hex <<  alpha;
    }

    if (plane->blend_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->blend_property().id(), blend) < 0;
      if (ret) {
        ALOGE("Failed to add pixel blend mode property %d to plane %d",
              plane->blend_property().id(), plane->id());
        break;
      }
      out_log << " blend mode =" << blend;
    }

    if(plane->get_hdr2sdr() && plane->eotf_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->eotf_property().id(),
                                     eotf) < 0;
      if (ret) {
        ALOGE("Failed to add eotf property %d to plane %d",
              plane->eotf_property().id(), plane->id());
        break;
      }
      out_log << " eotf=" << std::hex <<  eotf;
    }

    if(gIsDrmVerison6_1()){
      if(plane->kernel6_1_color_encoding().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->kernel6_1_color_encoding().id(),
                                      colorspace.colorspace_kernel_6_1_.color_encoding_) < 0;
        if (ret) {
          ALOGE("Failed to add kernel6_1_color_encoding property %d to plane %d",
                plane->kernel6_1_color_encoding().id(), plane->id());
          break;
        }
        out_log << " color_encoding=" << std::hex <<  colorspace.colorspace_kernel_6_1_.color_encoding_;
      }

      if(plane->kernel6_1_color_range().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->kernel6_1_color_range().id(),
                                      colorspace.colorspace_kernel_6_1_.color_range_) < 0;
        if (ret) {
          ALOGE("Failed to add kernel6_1_color_range property %d to plane %d",
                plane->kernel6_1_color_range().id(), plane->id());
          break;
        }
        out_log << " color_range=" << std::hex <<  colorspace.colorspace_kernel_6_1_.color_range_;
      }
    }else{
      if(plane->colorspace_property().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->colorspace_property().id(),
                                      colorspace.colorspace_kernel_510_) < 0;
        if (ret) {
          ALOGE("Failed to add colorspace property %d to plane %d",
                plane->colorspace_property().id(), plane->id());
          break;
        }
        out_log << " colorspace=" << std::hex <<  colorspace.colorspace_kernel_510_;
      }
    }

    ALOGD_IF(LogLevel(DBG_INFO),"%s",out_log.str().c_str());
    out_log.clear();
  }

  if (!ret) {
    uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    if (test_only)
      flags |= DRM_MODE_ATOMIC_TEST_ONLY;

    ret = drmModeAtomicCommit(drm->fd(), pset, flags, drm);
    if (ret) {
      if (!test_only)
        ALOGE("Failed to commit pset ret=%d\n", ret);
      return ret;
    }
  }
  return ret;
}

int DrmDisplayCompositor::ApplyDpms(DrmDisplayComposition *display_comp) {
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  DrmConnector *conn = drm->GetConnectorForDisplay(display_);
  if (!conn) {
    ALOGE("Failed to get DrmConnector for display %d", display_);
    return -ENODEV;
  }

  const DrmProperty &prop = conn->dpms_property();
  int ret = drmModeConnectorSetProperty(drm->fd(), conn->id(), prop.id(),
                                        display_comp->dpms_mode());
  if (ret) {
    ALOGE("Failed to set DPMS property for connector %d", conn->id());
    return ret;
  }
  return 0;
}

std::tuple<int, uint32_t> DrmDisplayCompositor::CreateModeBlob(
    const DrmMode &mode) {
  struct drm_mode_modeinfo drm_mode;
  memset(&drm_mode, 0, sizeof(drm_mode));
  mode.ToDrmModeModeInfo(&drm_mode);

  uint32_t id = 0;
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  int ret = drm->CreatePropertyBlob(&drm_mode, sizeof(struct drm_mode_modeinfo),
                                    &id);
  if (ret) {
    ALOGE("Failed to create mode property blob %d", ret);
    return std::make_tuple(ret, 0);
  }
  ALOGE("Create blob_id %" PRIu32 "\n", id);
  return std::make_tuple(ret, id);
}


void DrmDisplayCompositor::SingalCompsition(std::unique_ptr<DrmDisplayComposition> composition) {

  if(!composition)
    return;

  if (DisablePlanes(composition.get()))
    return;

  //wait and close acquire fence.
  std::vector<DrmHwcLayer> &layers = composition->layers();
  std::vector<DrmCompositionPlane> &comp_planes = composition->composition_planes();

  for (DrmCompositionPlane &comp_plane : comp_planes) {
      std::vector<size_t> &source_layers = comp_plane.source_layers();
      if (comp_plane.type() != DrmCompositionPlane::Type::kDisable) {
          if (source_layers.size() > 1) {
              ALOGE("Can't handle more than one source layer sz=%zu type=%d",
              source_layers.size(), comp_plane.type());
              continue;
          }

          if (source_layers.empty() || source_layers.front() >= layers.size()) {
              ALOGE("Source layer index %zu out of bounds %zu type=%d",
              source_layers.front(), layers.size(), comp_plane.type());
              break;
          }
          DrmHwcLayer &layer = layers[source_layers.front()];
          if (layer.acquire_fence->isValid()) {
            if(layer.acquire_fence->wait(500)){
              ALOGE("Failed to wait for acquire %d 500ms", layer.acquire_fence->getFd());
            }
            layer.acquire_fence->destroy();
          }
      }
  }

  composition->SignalCompositionDone();

  composition.reset(NULL);
}

#ifdef RK3528
void DrmDisplayCompositor::ClearDisplayHdrState() {
  if(current_mode_set_.hdr_.mode_ != DRM_HWC_SDR){
    drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
    if (!pset) {
      HWC2_ALOGE("display=%d Failed to allocate property set", display_);
      return;
    }

    DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
    DrmConnector *connector = drm->GetConnectorForDisplay(display_);
    if (!connector) {
      HWC2_ALOGE("Could not locate connector for display %d", display_);
      drmModeAtomicFree(pset);
      pset=NULL;
      return;
    }
    DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
    if (!crtc) {
      HWC2_ALOGE("Could not locate crtc for display %d", display_);
      drmModeAtomicFree(pset);
      pset=NULL;
      return;
    }
    // 释放上一次的 Blob
    if (hdr_blob_id_){
        int ret = drm->DestroyPropertyBlob(hdr_blob_id_);
        if(ret){
          HWC2_ALOGE("display=%d Failed to DestroyPropertyBlob crtc-id=%d hdr_ext_data-prop[%d]",
                      display_, crtc->id(), hdr_blob_id_);
        }else{
          hdr_blob_id_ = 0;
        }
    }

    if(crtc->hdr_ext_data().id() > 0){
      int ret = drmModeAtomicAddProperty(pset, crtc->id(), crtc->hdr_ext_data().id(), hdr_blob_id_);
      if (ret < 0) {
        HWC2_ALOGE("display=%d Failed to add metadata-Hdr crtc-id=%d hdr_ext_data-prop[%d]",
                    display_, crtc->id(), crtc->hdr_ext_data().id());
      }
    }
    // 进入HDR10/SDR的处理逻辑
    int ret = connector->switch_hdmi_hdr_mode(pset, HAL_DATASPACE_UNKNOWN, false);
    if(ret){
      HWC2_ALOGE("display %d enable hdr fail. datespace=%x",
              display_, HAL_DATASPACE_UNKNOWN);
    }

    uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    ret = drmModeAtomicCommit(drm->fd(), pset, flags, drm);
    if (ret) {
      HWC2_ALOGE("display=%d Failed to commit pset ret=%d\n",display_, ret);
      drmModeAtomicFree(pset);
      pset=NULL;
      return;
    }else{
      drmModeAtomicFree(pset);
      pset=NULL;
      current_mode_set_.hdr_.mode_ = DRM_HWC_SDR;
      current_mode_set_.hdr_.bHasYuv10bit_ = false;
      current_mode_set_.hdr_.datespace_ = HAL_DATASPACE_UNKNOWN;
    }
  }
  return;
}
#endif
void DrmDisplayCompositor::ClearDisplay() {
  if(!initialized_)
    return;

  AutoLock lock(&lock_, __func__);
  if (lock.Lock()){
    return;
  }

  // Bug: #363288 #361559
  // 清空 DrmDisplayComposition 前需要将已经送显的图层统一关闭后再进行RMFB
  // 如果上层直接 RMFB 的话，底层会自动关闭对应图层，因为关闭有先后顺序
  // 可能会导致屏幕非预期闪屏，例如zpos=1先被关闭，zpos=0图层就显示到屏幕上一帧
  for(auto &map : active_composition_map_){
    if(map.second != NULL)
      SingalCompsition(std::move(map.second));
  }
  active_composition_map_.clear();

  // 清空
  for(auto &map : collect_composition_map_){
    if(map.second != NULL)
      SingalCompsition(std::move(map.second));
  }
  collect_composition_map_.clear();

  //Singal the remainder fences in composite queue.
  while(!composite_queue_.empty())
  {
    std::unique_ptr<DrmDisplayComposition> remain_composition(
      std::move(composite_queue_.front()));

    if(remain_composition)
      ALOGD_IF(LogLevel(DBG_DEBUG),"ClearDisplay: composite_queue_ size=%zu frame_no=%" PRIu64 "",composite_queue_.size(), remain_composition->frame_no());

    SingalCompsition(std::move(remain_composition));
    composite_queue_.pop();
    pthread_cond_signal(&composite_queue_cond_);
  }
  mapDisplayHaveQeueuCnt_.clear();

  if(bWriteBackEnable_){
    drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
    if (!pset) {
      HWC2_ALOGE("Failed to allocate property set");
      return;
    }
    DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
    DisableWritebackCommit(pset, drm->GetWritebackConnectorForDisplay(0));
    uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    int ret = drmModeAtomicCommit(drm->fd(), pset, flags, drm);
    if (ret) {
      HWC2_ALOGE("Failed to commit pset ret=%d\n", ret);
    }

    drmModeAtomicFree(pset);
    pset = NULL;
    bWriteBackEnable_ = false;
  }

  // 重置HDR状态
#ifdef RK3528
  ClearDisplayHdrState();
#endif

  DrmVideoProducer* dvp = DrmVideoProducer::getInstance();
  if(!dvp->IsValid()){
    HWC2_ALOGD_IF_ERR("SidebandStream: DrmVideoProducer is invalidate.");
  }else{
    if(current_sideband2_.enable_ || drawing_sideband2_.enable_){
      if(drawing_sideband2_.buffer_ != NULL){
        // 释放上一帧 ReleaseFence
        if(dvp->SignalReleaseFence(display_,
                                  drawing_sideband2_.tunnel_id_,
                                  drawing_sideband2_.buffer_->GetExternalId())){
          HWC2_ALOGE("SidebandStream: display-id=%d SignalReleaseFence fail, last buffer id=%" PRIu64 ,
                      display_, drawing_sideband2_.buffer_->GetId());
        }
      }

      // 当前帧存在 Sideband Stream Buffer
      if(current_sideband2_.enable_ &&
        current_sideband2_.buffer_ != NULL){
        // 释放上一帧 ReleaseFence
        if(dvp->SignalReleaseFence(display_,
                                  current_sideband2_.tunnel_id_,
                                  current_sideband2_.buffer_->GetExternalId())){
          HWC2_ALOGE("SidebandStream: display-id=%d SignalReleaseFence fail, last buffer id=%" PRIu64 ,
                      display_, current_sideband2_.buffer_->GetId());
        }
      }

      if(current_sideband2_.tunnel_id_ > 0){
        int ret = dvp->DestoryConnection(display_, current_sideband2_.tunnel_id_);
        if(ret){
          HWC2_ALOGE("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " fail.",
                      display_, current_sideband2_.tunnel_id_);
          current_sideband2_.enable_ = false;
          current_sideband2_.buffer_ = NULL;
          current_sideband2_.tunnel_id_ = 0;
        }else{
          HWC2_ALOGI("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " Success.",
                      display_, current_sideband2_.tunnel_id_);
        }
      }

      if(drawing_sideband2_.tunnel_id_ > 0){
        int ret = dvp->DestoryConnection(display_, drawing_sideband2_.tunnel_id_);
        if(ret){
          HWC2_ALOGE("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " fail.",
                      display_, drawing_sideband2_.tunnel_id_);
          drawing_sideband2_.enable_ = false;
          drawing_sideband2_.buffer_ = NULL;
          drawing_sideband2_.tunnel_id_ = 0;
        }else{
          HWC2_ALOGI("SidebandStream: display-id=%d DestoryConnection old tunnel-id=%" PRIu64 " Success.",
                      display_, drawing_sideband2_.tunnel_id_);
        }
      }
    }
  }

  clear_ = true;
  //vsync_worker_.VSyncControl(false);
}

void DrmDisplayCompositor::ApplyFrame(
    std::unique_ptr<DrmDisplayComposition> composition, int status,
    bool writeback) {
  ATRACE_CALL();
  int ret = status;

  if (!ret && !clear_) {
    if (writeback && !CountdownExpired()) {
      ALOGE("Abort playing back scene");
      return;
    }
    ret = CommitFrame(composition.get(), false);
  }

  if (ret) {
    ALOGE("Composite failed for display %d", display_);
    // Disable the hw used by the last active composition. This allows us to
    // signal the release fences from that composition to avoid hanging.
    ClearDisplay();
    return;
  }

  AutoLock lock(&lock_, __func__);
  if (lock.Lock())
    return;
  ++dump_frames_composited_;
  if(active_composition_){
      active_composition_->SignalCompositionDone();
  }

  // Enter ClearDisplay state must to SignalCompositionDone
  if(clear_){
    SingalCompsition(std::move(composition));
  }else{
    active_composition_.swap(composition);
  }

  //flatten_countdown_ = FLATTEN_COUNTDOWN_INIT;
  //vsync_worker_.VSyncControl(!writeback);
}

// 顺序获取待送显的 DrmDisplayComposition
int DrmDisplayCompositor::CollectSFInfoBySequence() {
  ATRACE_CALL();
  int ret = 0;
  std::unique_ptr<DrmDisplayComposition> composition;
  std::set<int> exist_display;
  exist_display.clear();
  if (!composite_queue_.empty()) {
    while(composite_queue_.size() > 0){
      composition = std::move(composite_queue_.front());
      composite_queue_.pop();
      if(exist_display.count(composition->display())){
        composite_queue_temp_.push(std::move(composition));
        continue;
      }
      mapDisplayHaveQeueuCnt_[composition->display()]--;
      exist_display.insert(composition->display());
      CollectInfo(std::move(composition), 0);
    }
    while(composite_queue_temp_.size()){
      composite_queue_.push(std::move(composite_queue_temp_.front()));
      composite_queue_temp_.pop();
    }
  }

  return ret;
}

// 处理来自SurfaceFlinger的请求
int DrmDisplayCompositor::CollectSFInfoByDrop() {
  ATRACE_CALL();
  int ret = 0;
  std::set<int> exist_display;
  exist_display.clear();
  if (!composite_queue_.empty()) {
    std::map<int, std::unique_ptr<DrmDisplayComposition>>  latest_composition_map;
    // 找到最新的 composition,并且把不需要送显的composition存放在composite_queue_temp_队列中
    while(composite_queue_.size() > 0){
      std::unique_ptr<DrmDisplayComposition> composition = std::move(composite_queue_.front());
      int composition_display = composition->display();
      mapDisplayHaveQeueuCnt_[composition_display]--;
      composite_queue_.pop();
      if(latest_composition_map[composition_display] == NULL){
        latest_composition_map[composition_display] = std::move(composition);
        composition = NULL;
      }else if(composition->frame_no() > latest_composition_map[composition_display]->frame_no()){
        composite_queue_temp_.push(std::move(latest_composition_map[composition_display]));
        latest_composition_map[composition_display] = std::move(composition);
      }else{
        composite_queue_temp_.push(std::move(composition));
      }
    }

    // 将存放在composite_queue_temp_队列中的composition移动到最新的DrmDisplayComposition中
    // 待 DrmDisplayComposition 显示完成以后，进行 Signal ReleaseFence.
    if(latest_composition_map.size() > 0){
      while(composite_queue_temp_.size() > 0){
        std::unique_ptr<DrmDisplayComposition>  composition =
            std::move(composite_queue_temp_.front());
        int composition_display = composition->display();
        composite_queue_temp_.pop();
        for(auto &last_composition_pair : latest_composition_map){
          if(composition_display == last_composition_pair.first){
            auto &useless_queue = last_composition_pair.second->useless_composition_queue();
            useless_queue.push(std::move(composition));
          }

        }
      }

      for(auto &last_composition_pair : latest_composition_map){
        CollectInfo(std::move(last_composition_pair.second), 0);
      }
    }
  }
  return ret;
}

// 处理来自SurfaceFlinger的请求
int DrmDisplayCompositor::CollectSFInfo() {
  ATRACE_CALL();
  int ret = pthread_mutex_lock(&lock_);
  if (ret) {
    ALOGE("Failed to acquire compositor lock %d", ret);
    return ret;
  }

  if (composite_queue_.empty()) {
    ret = pthread_mutex_unlock(&lock_);
    if (ret){
      ALOGE("Failed to release compositor lock %d", ret);
    }
    return ret;
  }

  if (!composite_queue_.empty()) {
    if(drop_mode_){
      CollectSFInfoByDrop();
    }else{
      CollectSFInfoBySequence();
    }
  }else{ // frame_queue_ is empty
    ALOGW_IF(LogLevel(DBG_DEBUG),"%s,line=%d composite_queue_ is empty, skip ApplyFrame",__FUNCTION__,__LINE__);
    ret = pthread_mutex_unlock(&lock_);
    if (ret) {
      ALOGE("Failed to release compositor lock %d", ret);
      return ret;
    }
    return 0;
  }

  // ALOGI("rk-debug display=%d signal cond=%p",display(),&composite_queue_cond_);
  pthread_cond_signal(&composite_queue_cond_);

  ret = pthread_mutex_unlock(&lock_);
  if (ret) {
    ALOGE("Failed to release compositor lock %d", ret);
    return ret;
  }

  return ret;
}

// 收集来自 VideoProducer 的送显信息
int DrmDisplayCompositor::CollectVPInfo() {
  ATRACE_CALL();
  int ret = 0;
  if(!pset_){
    pset_ = drmModeAtomicAlloc();
    if (!pset_) {
      ALOGE("Failed to allocate property set");
      return -1 ;
    }
  }

  drmModeAtomicReqPtr pset = pset_;
  DrmDisplayComposition* current_composition = NULL;
  bool sf_update = false;
  // collect_composition_map_ 有值则表示当前帧包含 SF 刷新
  if(collect_composition_map_.count(display_) > 0){
    current_composition = collect_composition_map_[display_].get();
    sf_update = true;
  // collect_composition_map_ 无值，则表示当前帧包含不包含
  }else if(active_composition_map_.count(display_) > 0){
    current_composition = active_composition_map_[display_].get();
  }

  if(current_composition == NULL){
    HWC2_ALOGE("can't find suitable active DrmDisplayComposition");
    return 0;
  }

  std::vector<DrmHwcLayer> &layers = current_composition->layers();
  std::vector<DrmCompositionPlane> &comp_planes = current_composition
                                                      ->composition_planes();
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);

  DrmConnector *connector = drm->GetConnectorForDisplay(display_);
  if (!connector) {
    ALOGE("Could not locate connector for display %d", display_);
    return -ENODEV;
  }

  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    HWC2_ALOGE("Could not locate crtc for display %d", display_);
    return -ENODEV;
  }

  int zpos = -1;

  for (DrmCompositionPlane &comp_plane : comp_planes) {
    DrmPlane *plane = comp_plane.plane();
    std::vector<size_t> &source_layers = comp_plane.source_layers();

    int fb_id = -1;
    hwc_rect_t display_frame;
    // Commit mirror function
    hwc_rect_t display_frame_mirror;
    hwc_frect_t source_crop;
    uint64_t rotation = 0;
    uint64_t alpha = 0xFFFF;
    uint64_t blend = 0;
    uint16_t eotf = TRADITIONAL_GAMMA_SDR;
    drm_colorspace colorspace;

    int dst_l,dst_t,dst_w,dst_h;
    int src_l,src_t,src_w,src_h;
    bool afbcd = false, yuv = false, sideband = false;

    crtc = comp_plane.crtc();

    if (comp_plane.type() != DrmCompositionPlane::Type::kDisable) {

      if(source_layers.empty()){
        ALOGE("Can't handle empty source layer CompositionPlane.");
        continue;
      }

      if (source_layers.size() > 1) {
        ALOGE("Can't handle more than one source layer sz=%zu type=%d",
              source_layers.size(), comp_plane.type());
        continue;
      }

      if (source_layers.front() >= layers.size()) {
        ALOGE("Source layer index %zu out of bounds %zu type=%d",
              source_layers.front(), layers.size(), comp_plane.type());
        break;
      }

      DrmHwcLayer &layer = layers[source_layers.front()];
      if (!layer.bSidebandStreamLayer_ && sf_update) {
        continue;
      }

      if(layer.bSidebandStreamLayer_){
        DrmVideoProducer* dvp = DrmVideoProducer::getInstance();

        ret = dvp->CreateConnection(display_, layer.iTunnelId_);
        if(ret < 0){
          HWC2_ALOGI("SidebandStream: display-id=%d CreateConnection fail, iTunnelId = %d",
                     display_, layer.iTunnelId_);
        }

        vt_rect_t dis_rect = {0,0,0,0};
        dis_rect.left   = layer.display_frame.left;
        dis_rect.top    = layer.display_frame.top;
        dis_rect.right  = layer.display_frame.right;
        dis_rect.bottom = layer.display_frame.bottom;
        std::shared_ptr<DrmBuffer> buffer = dvp->AcquireBuffer(display_, layer.iTunnelId_, &dis_rect, 0);
        if(buffer == NULL){
          HWC2_ALOGD_IF_WARN("SidebandStream: display-id=%d AcquireBuffer fail, iTunnelId = %d",
                     display_, layer.iTunnelId_);
          continue;
        }
#ifdef RK3528
        if(layer.bNeedPreScale_){
          ret = buffer->SwitchToPreScaleBuffer();
          if(ret){
            HWC2_ALOGD_IF_WARN("SidebandStream: SwitchToPreScaleBuffer fail, iTunnelId = %d",
                        layer.iTunnelId_);
          }else{
            // 更新 sideband Buffer 参数
            fb_id = buffer->GetPreScaleFbId();
            yuv = layer.bYuv_;
            afbcd = buffer->GetModifier() > 0;
            int left = 0, top = 0, right = 0, bottom = 0;
            buffer->GetCrop(&left, &top, &right, &bottom);
            source_crop.left   = (float)left;
            source_crop.top    = (float)top;
            source_crop.right  = (float)right;
            source_crop.bottom = (float)bottom;

            layer.sf_handle = buffer->GetHandle();
            layer.bIsPreScale_ = true;
          }
        }else
#endif
        {
          // 更新 sideband Buffer 参数
          fb_id = buffer->GetFbId();
          yuv = layer.bYuv_;
          afbcd = buffer->GetModifier() > 0;
          int left = 0, top = 0, right = 0, bottom = 0;
          buffer->GetCrop(&left, &top, &right, &bottom);
          source_crop.left   = (float)left;
          source_crop.top    = (float)top;
          source_crop.right  = (float)right;
          source_crop.bottom = (float)bottom;

          layer.sf_handle = buffer->GetHandle();
        }

        // 更新Sideband请求状态状态
        current_sideband2_.enable_ = true;
        current_sideband2_.tunnel_id_ = layer.iTunnelId_;
        current_sideband2_.buffer_ = buffer;

        // RK3528 更新HDR信息, 若无报错，则使用 metadata Hdr模式
        if(gIsRK3528()){
          if(comp_plane.get_zpos() == 0 && !CollectVPHdrInfo(layer)){
            current_composition->SetDisplayHdrMode(DRM_HWC_METADATA_HDR, layer.eDataSpace_);
          }else{
            current_composition->SetDisplayHdrMode(DRM_HWC_SDR, HAL_DATASPACE_UNKNOWN);
          }
          CollectModeSetInfo(pset, current_composition, true);
        }

        // Release 当前帧
        ret = dvp->ReleaseBuffer(display_,
                                 layer.iTunnelId_,
                                 buffer->GetExternalId());
        if(ret){
          HWC2_ALOGE("SidebandStream: display-id=%d ReleaseBuffer fail, buffer id=%" PRIu64 ,
                     display_, buffer->GetId());
        }
      }else{
        fb_id = layer.buffer->fb_id;
        afbcd = layer.bAfbcd_;
        yuv = layer.bYuv_;
        source_crop = layer.source_crop;
      }

#ifdef RK3528
      if(layer.bNeedPreScale_ && !layer.bIsPreScale_){
        HWC2_ALOGD_IF_WARN("%s bNeedPreScale_=%d bIsPreScale_=%d skip until PreScale ready.",
                            layer.sLayerName_.c_str(),
                            layer.bNeedPreScale_,
                            layer.bIsPreScale_);
        continue;
      }
#endif

      display_frame = layer.display_frame;
      display_frame_mirror = layer.display_frame_mirror;
      if (layer.blending == DrmHwcBlending::kPreMult) alpha = layer.alpha << 8;
      eotf = layer.uEOTF;
      colorspace = layer.uColorSpace;

      if (plane->blend_property().id()) {
        switch (layer.blending) {
          case DrmHwcBlending::kPreMult:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "Pre-multiplied");
            break;
          case DrmHwcBlending::kCoverage:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "Coverage");
            break;
          case DrmHwcBlending::kNone:
          default:
            std::tie(blend, ret) = plane->blend_property().GetEnumValueWithName(
                "None");
            break;
        }
      }

      zpos = comp_plane.get_zpos();
      if(current_composition->display() > 0xf)
        zpos=1;
      if(zpos < 0)
        ALOGE("The zpos(%d) is invalid", zpos);

      rotation = layer.transform;
    }

    // Disable the plane if there's no framebuffer
    if (fb_id < 0) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->crtc_property().id(), 0) < 0 ||
            drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->fb_property().id(), 0) < 0;
      if (ret) {
        ALOGE("Failed to add plane %d disable to pset", plane->id());
        continue;
      }
      continue;
    }
    src_l = (int)source_crop.left;
    src_t = (int)source_crop.top;
    src_w = (int)(source_crop.right - source_crop.left);
    src_h = (int)(source_crop.bottom - source_crop.top);

    // Commit mirror function
    if(comp_plane.mirror()){
      dst_l = display_frame_mirror.left;
      dst_t = display_frame_mirror.top;
      dst_w = display_frame_mirror.right - display_frame_mirror.left;
      dst_h = display_frame_mirror.bottom - display_frame_mirror.top;
    }else{
      dst_l = display_frame.left;
      dst_t = display_frame.top;
      dst_w = display_frame.right - display_frame.left;
      dst_h = display_frame.bottom - display_frame.top;
    }

    if(yuv){
      src_l = ALIGN_DOWN(src_l, 2);
      src_t = ALIGN_DOWN(src_t, 2);
      src_w = ALIGN_DOWN(src_w, 2);
      src_h = ALIGN_DOWN(src_h, 2);
    }


    ret = drmModeAtomicAddProperty(pset, plane->id(),
                                   plane->crtc_property().id(), crtc->id()) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->fb_property().id(), fb_id) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_x_property().id(),
                                    dst_l) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_y_property().id(),
                                    dst_t) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_w_property().id(),
                                    dst_w) <
           0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->crtc_h_property().id(),
                                    dst_h) <
           0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_x_property().id(),
                                    (int)(src_l) << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_y_property().id(),
                                    (int)(src_t) << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_w_property().id(),
                                    (int)(src_w)
                                        << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->src_h_property().id(),
                                    (int)(src_h)
                                        << 16) < 0;
    ret |= drmModeAtomicAddProperty(pset, plane->id(),
                                   plane->zpos_property().id(), zpos) < 0;
    if (ret) {
      ALOGE("Failed to add plane %d to set", plane->id());
      break;
    }

    size_t index=0;
    std::ostringstream out_log;

    out_log << "DrmDisplayCompositor[" << index << "]"
            << " frame_no=" << current_composition->frame_no()
            << " display=" << current_composition->display()
            << " plane=" << (plane ? plane->name() : "Unknow")
            << " crct id=" << crtc->id()
            << " fb id=" << fb_id
            << " display_frame[" << dst_l << ","
            << dst_t << "," << dst_w
            << "," << dst_h << "]"
            << " source_crop[" << src_l << ","
            << src_t << "," << src_w
            << "," << src_h << "]"
            << ", zpos=" << zpos
            ;
    index++;

    if (plane->rotation_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->rotation_property().id(),
                                     rotation) < 0;
      if (ret) {
        ALOGE("Failed to add rotation property %d to plane %d",
              plane->rotation_property().id(), plane->id());
        break;
      }
      out_log << " rotation=" << rotation;
    }

    if (plane->alpha_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->alpha_property().id(), alpha) < 0;
      if (ret) {
        ALOGE("Failed to add alpha property %d to plane %d",
              plane->alpha_property().id(), plane->id());
        break;
      }
      out_log << " alpha=" << std::hex <<  alpha;
    }

    if (plane->blend_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->blend_property().id(), blend) < 0;
      if (ret) {
        ALOGE("Failed to add pixel blend mode property %d to plane %d",
              plane->blend_property().id(), plane->id());
        break;
      }
      out_log << " blend mode =" << blend;
    }

    if(plane->get_hdr2sdr() && plane->eotf_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                     plane->eotf_property().id(),
                                     eotf) < 0;
      if (ret) {
        ALOGE("Failed to add eotf property %d to plane %d",
              plane->eotf_property().id(), plane->id());
        break;
      }
      out_log << " eotf=" << std::hex <<  eotf;
    }

    if(gIsDrmVerison6_1()){
      if(plane->kernel6_1_color_encoding().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->kernel6_1_color_encoding().id(),
                                      colorspace.colorspace_kernel_6_1_.color_encoding_) < 0;
        if (ret) {
          ALOGE("Failed to add kernel6_1_color_encoding property %d to plane %d",
                plane->kernel6_1_color_encoding().id(), plane->id());
          break;
        }
        out_log << " color_encoding=" << std::hex <<  colorspace.colorspace_kernel_6_1_.color_encoding_;
      }

      if(plane->kernel6_1_color_range().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->kernel6_1_color_range().id(),
                                      colorspace.colorspace_kernel_6_1_.color_range_) < 0;
        if (ret) {
          ALOGE("Failed to add kernel6_1_color_range property %d to plane %d",
                plane->kernel6_1_color_range().id(), plane->id());
          break;
        }
        out_log << " color_range=" << std::hex <<  colorspace.colorspace_kernel_6_1_.color_range_;
      }
    }else{
      if(plane->colorspace_property().id()) {
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->colorspace_property().id(),
                                      colorspace.colorspace_kernel_510_) < 0;
        if (ret) {
          ALOGE("Failed to add colorspace property %d to plane %d",
                plane->colorspace_property().id(), plane->id());
          break;
        }
        out_log << " colorspace=" << std::hex <<  colorspace.colorspace_kernel_510_;
      }
    }

    if(plane->async_commit_property().id()) {
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                    plane->async_commit_property().id(),
                                    sideband == true ? 1 : 0) < 0;
      if (ret) {
        ALOGE("Failed to add async_commit_property property %d to plane %d",
              plane->async_commit_property().id(), plane->id());
        break;
      }

      out_log << " async_commit=" << sideband;
    }

    HWC2_ALOGD_IF_DEBUG("SidebandStream: %s",out_log.str().c_str());
    out_log.clear();
  }

  return ret;
}
static inline long __currentTime(){
  struct timeval tp;
  gettimeofday(&tp, NULL);
  return static_cast<long>(tp.tv_sec) * 1000000 + tp.tv_usec;
}

int DrmDisplayCompositor::CollectVPHdrInfo(DrmHwcLayer &hdrLayer){
  HWC2_ALOGD_IF_INFO("Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());

  // 算法解析库是否存在
  DrmHdrParser* dhp = DrmHdrParser::Get();
  if(dhp == NULL){
    HWC2_ALOGD_IF_ERR("Fail to get DrmHdrParser, use SDR mode, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
    return -1;
  }
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);

  DrmConnector *connector_ = drm->GetConnectorForDisplay(display_);
  if (!connector_) {
    ALOGE("Could not locate connector for display %d", display_);
    return -ENODEV;
  }

  if(connector_->type() == DRM_MODE_CONNECTOR_TV){
    HWC2_ALOGD_IF_INFO("RK3528 TV unsupport HDR2SDR, Id=%d Name=%s eDataSpace_=0x%x eotf=%d",
                      hdrLayer.uId_, hdrLayer.sLayerName_.c_str(),
                      hdrLayer.eDataSpace_,
                      hdrLayer.uEOTF);
    return -1;
  }
  // 显示器是否支持HDR
  bool is_hdr_display = connector_->is_hdmi_support_hdr();
  // 是否为 HDR 片源
  bool is_input_hdr = hdrLayer.bHdr_;
  // 2:自动模式: 电视支持 HDR模式播放HDR视频则切换HDR模式，否则使用SDR模式
  // 1:HDR模式: 等同自动模式
  // 0:SDR模式: 电视强制使用SDR模式，HDR片源也采用SDR显示
  int user_hdr_mode = hwc_get_int_property("persist.sys.vivid.hdr_mode", "2");
  // 可能存在模式：SDR2SDR,HDR2SDR,SDR2HDR,HDR2HDR
  bool is_output_hdr = false;
  // 2:自动模式: 电视支持 HDR模式播放HDR视频则切换HDR模式，否则使用SDR模式
  // 1:HDR模式: 电视支持 HDR模式则强制使用HDR模式，SDR片源也采用HDR模式输出
  if((user_hdr_mode == 2 && is_hdr_display && is_input_hdr) ||
     (user_hdr_mode == 1 && is_hdr_display && is_input_hdr)){
    is_output_hdr = true;
  }else{
    is_output_hdr = false;
  }

  // 如果输入是 SDR 且输出为SDR,则不需要进行任何处理
  if(is_input_hdr == false && is_output_hdr == false){
    HWC2_ALOGD_IF_INFO("Use SDR2SDR mode.");
    return -1;
  }

  DrmGralloc* gralloc = DrmGralloc::getInstance();
  if(gralloc == NULL){
    HWC2_ALOGD_IF_INFO("DrmGralloc is null, Use SDR2SDR mode.");
    return -1;
  }

  // debug 打印耗时
  long t0 = __currentTime();

  // 用于判断是否存在 metadata 信息
  bool codec_meta_exist = false;
  // 获取存储 metadata 信息的 offset
  int64_t offset = gralloc->hwc_get_offset_of_dynamic_hdr_metadata(hdrLayer.sf_handle);
  if(offset < 0){
    HWC2_ALOGD_IF_ERR("Fail to get hdr metadata offset, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
  }
  // offset > 0 则认为存在 Metadata
  codec_meta_exist = offset > 0;
  HWC2_ALOGD_IF_INFO("dynamic_hdr_metadata offset=%" PRIi64, offset);

  // 初始化参数
  memset(&hdrLayer.metadataHdrParam_, 0x00, sizeof(rk_hdr_parser_params_t));
  // 如果输出模式为HDR
  if(is_output_hdr){

    // Android bt2020 or bt709
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_STANDARD_MASK){
      case HAL_DATASPACE_STANDARD_BT2020:
      case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE :
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = COLOR_PRIM_BT2020;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = COLOR_PRIM_BT709;
        break;
    }


    // 片源为 HLG，且电视支持 HLG ，则选择 HLG bypass 模式
    if(hdrLayer.uEOTF == HLG && connector_->isSupportHLG()){
      hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_HLG;
    // 片源为 HDR10，且电视支持 HDR10 ，则选择 HDR10 bypass 模式
    }else if(hdrLayer.uEOTF == SMPTE_ST2084 && connector_->isSupportSt2084()){
      hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
    // 若没有匹配的 HDR 模式，则优先使用 HDR10 输出
    }else{
      if(connector_->isSupportSt2084()){
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
      }else if(connector_->isSupportHLG()){
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_HLG;
      }
    }
    // hdr10 最小亮度应该是0.05,算法提供接口是要求外部数值 0.05*100=5
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min = 5;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max = hwc_get_int_property("persist.sys.vivid.max_brightness", "1000") * 100;
  }else{
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = COLOR_PRIM_BT709;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_GAMMA_SDR;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min = 10;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max = hwc_get_int_property("persist.sys.vivid.max_brightness", "100") * 100;
  }

  void *cpu_addr = NULL;
  if(codec_meta_exist){
    // 获取Medata地址
    cpu_addr = gralloc->hwc_get_handle_lock(hdrLayer.sf_handle, hdrLayer.iWidth_, hdrLayer.iHeight_);
    if(cpu_addr == NULL){
      HWC2_ALOGD_IF_ERR("Fail to lock dma buffer, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
      hdrLayer.metadataHdrParam_.codec_meta_exist = false;
      hdrLayer.metadataHdrParam_.p_hdr_codec_meta = NULL;
    }else{
      uint16_t *u16_cpu_metadata = (uint16_t *)((uint8_t *)cpu_addr + offset);
      hdrLayer.metadataHdrParam_.codec_meta_exist = codec_meta_exist;
      hdrLayer.metadataHdrParam_.p_hdr_codec_meta = (RkMetaHdrHeader*)u16_cpu_metadata;

      // 如果当前设置 hdr 显示模式为 HLG bypass，则需要检测 HLG 片源是否为 dynamic Hdr
      // 若为 dynamic Hdr，则需要将输出模式修改为 Hdr10,若不支持Hdr10,则输出SDR
      // 原因是目前 VOP3 是参考 VividHdr标准实现，标准内部没有支持 dynamic hlg hdr 直出模式
      if(hdrLayer.uEOTF == HLG &&
         hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf == SINK_EOTF_HLG){
        int ret = dhp->MetadataHdrparserFormat(&hdrLayer.metadataHdrParam_,
                                               &hdrLayer.metadataHdrFmtInfo_);
        if(ret){
          HWC2_ALOGD_IF_ERR("MetadataHdrparserFormat, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
          hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
        }else{
          if(hdrLayer.metadataHdrFmtInfo_.hdr_format == HDRVIVID){
            if(connector_->isSupportSt2084()){
              hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
              HWC2_ALOGD_IF_INFO("Id=%d Name=%s is HLG dynamic, convert to HDR10.", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
            }else{
              hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_GAMMA_SDR;
              HWC2_ALOGD_IF_INFO("Id=%d Name=%s is HLG dynamic, convert to SDR.", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
            }
          }
        }
      }
    }
  }else{
    // Metadata 不存在，则使用 Android Dataspace
    hdrLayer.metadataHdrParam_.codec_meta_exist = false;
    hdrLayer.metadataHdrParam_.p_hdr_codec_meta = NULL;

    // Android bt2020 or bt709
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_STANDARD_MASK){
      case HAL_DATASPACE_STANDARD_BT2020:
      case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE :
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim = COLOR_PRIM_BT2020;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim = COLOR_PRIM_BT709;
        break;
    }

    // Android st2084 / HLG / SDR
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_TRANSFER_MASK){
      case HAL_DATASPACE_TRANSFER_ST2084:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = SINK_EOTF_ST2084;
        break;
      case HAL_DATASPACE_TRANSFER_HLG :
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = SINK_EOTF_HLG;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = SINK_EOTF_GAMMA_SDR;
        break;
    }

    // Android full / limit range
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_RANGE_MASK){
      case HAL_DATASPACE_RANGE_FULL:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = RANGE_FULL;
        break;
      case HAL_DATASPACE_RANGE_LIMITED :
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = RANGE_LIMITED;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = RANGE_LIMITED;
        break;
    }
  }

  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_pq_max_y_mode = 0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_dst_gamma = 2.2;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sm_ratio = 1.0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_scale_ratio = 1.0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sdr_color_space = 2;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.print_input_meta = 0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.hdr_log_level = 0;

  if(hwc_get_int_property("vendor.hwc.vivid_hdr_debug", "0") > 0){
    hdrLayer.uEOTF = hwc_get_int_property("vendor.hwc.vivid_layer_eotf", "0");
    hdrLayer.metadataHdrParam_.codec_meta_exist = hwc_get_bool_property("vendor.hwc.vivid_codec_meta_exist", "true");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = hwc_get_int_property("vendor.hwc.vivid_color_prim", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = hwc_get_int_property("vendor.hwc.vivid_eotf", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_x = hwc_get_int_property("vendor.hwc.vivid_red_x", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_y = hwc_get_int_property("vendor.hwc.vivid_red_y", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_x = hwc_get_int_property("vendor.hwc.vivid_green_x", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_y = hwc_get_int_property("vendor.hwc.vivid_green_y", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_x = hwc_get_int_property("vendor.hwc.vivid_white_point_x", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_y = hwc_get_int_property("vendor.hwc.vivid_white_point_y", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min = hwc_get_int_property("vendor.hwc.vivid_dst_min", "10");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max = hwc_get_int_property("vendor.hwc.vivid_dst_max", "10000");

    hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim = hwc_get_int_property("vendor.hwc.vivid_dataspace_pri", "0");
    hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = hwc_get_int_property("vendor.hwc.vivid_dataspace_eotf", "0");
    hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = hwc_get_int_property("vendor.hwc.vivid_dataspace_range", "0");

    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_pq_max_y_mode = hwc_get_int_property("vendor.hwc.vivid_hdr_pq_max_y_mode", "0");
    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_dst_gamma = (hwc_get_int_property("vendor.hwc.vivid_hdr_dst_gamma", "22") * 1.0 / 10);
    hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sm_ratio = hwc_get_int_property("vendor.hwc.vivid_s2h_sm_ratio", "10") * 1.0 / 10;
    hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_scale_ratio = hwc_get_int_property("vendor.hwc.vivid_s2h_scale_ratio", "10") * 1.0 / 10;
    hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sdr_color_space = hwc_get_int_property("vendor.hwc.vivid_s2h_sdr_color_space", "2");
    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.print_input_meta = hwc_get_int_property("vendor.hwc.vivid_print_input_meta", "1");
    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.hdr_log_level = hwc_get_int_property("vendor.hwc.vivid_hdr_log_level", "7");
  }
  HWC2_ALOGD_IF_INFO("hdr_hdmi_meta: user_hdr_mode(%d) layer eDataSpace_=0x%x eotf=%d => codec_meta_exist(%d) hdr_dataspace_info: color_prim=%d eotf=%d range=%d",
            user_hdr_mode,
            hdrLayer.eDataSpace_,
            hdrLayer.uEOTF,
            hdrLayer.metadataHdrParam_.codec_meta_exist,
            hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim,
            hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf,
            hdrLayer.metadataHdrParam_.hdr_dataspace_info.range);
  HWC2_ALOGD_IF_INFO("hdr_hdmi_meta: color_prim=%d eotf=%d red_x=%d red_y=%d green_x=%d green_y=%d white_point_x=%d white_point_y=%d dst_min=%d dst_max=%d",
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_x,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_y,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_x,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_y,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_x,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_y,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max);

  HWC2_ALOGD_IF_INFO("hdr_user_cfg: hdr_pq_max_y_mode=%d hdr_dst_gamma=%f s2h_sm_ratio=%f s2h_scale_ratio=%f s2h_sdr_color_space=%d print_input_meta=%d hdr_log_level=%d",
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_pq_max_y_mode,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_dst_gamma,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sm_ratio,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_scale_ratio,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sdr_color_space,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.print_input_meta,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.hdr_log_level);

  int ret = dhp->MetadataHdrParser(&hdrLayer.metadataHdrParam_);
  if(ret){
    HWC2_ALOGD_IF_ERR("Fail to call MetadataHdrParser ret=%d Id=%d Name=%s ",
                      ret,
                      hdrLayer.uId_,
                      hdrLayer.sLayerName_.c_str());
    if(cpu_addr != NULL)
      gralloc->hwc_get_handle_unlock(hdrLayer.sf_handle);
    return ret;
  }

  if(cpu_addr != NULL)
    gralloc->hwc_get_handle_unlock(hdrLayer.sf_handle);

  hdrLayer.IsMetadataHdr_ = true;
  HWC2_ALOGD_IF_INFO("Use HdrParser mode.");
  return 0;
}


int DrmDisplayCompositor::WriteBackByRGA() {
  ATRACE_CALL();
  int ret = 0;

  DrmDisplayComposition* current_composition = NULL;
  bool sf_update = false, sideband_update = false;
  // collect_composition_map_ 有值则表示当前帧包含 SF 刷新
  if(collect_composition_map_.count(display_) > 0){
    current_composition = collect_composition_map_[display_].get();
    sf_update = true;
  // collect_composition_map_ 无值，则表示当前帧包含不包含
  }else if(active_composition_map_.count(display_) > 0){
    current_composition = active_composition_map_[display_].get();
  }

  if(current_composition == NULL){
    HWC2_ALOGE("can't find suitable active DrmDisplayComposition");
    return 0;
  }

  if(current_sideband2_.buffer_ != NULL){
    sideband_update = true;
  }

  if(!sf_update && !sideband_update){
    HWC2_ALOGI("not update, skip rga compose.");
    return 0;
  }

  std::vector<DrmHwcLayer> &layers = current_composition->layers();

  if(layers.size() == 0){
    HWC2_ALOGE("layers size is 0");
    return 0;
  }
  std::vector<DrmCompositionPlane> &comp_planes = current_composition
                                                      ->composition_planes();
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);

  DrmConnector *connector = drm->GetConnectorForDisplay(display_);
  if (!connector) {
    ALOGE("Could not locate connector for display %d", display_);
    return -ENODEV;
  }

  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    ALOGE("Could not locate crtc for display %d", display_);
    return -ENODEV;
  }

  int wbDisplay = resource_manager_->GetWBDisplay();
  if(wbDisplay != display_){
    HWC2_ALOGD_IF_WARN("display=%d is not wbDisplay, skip.", display_);
    return -1;
  }

  ret = resource_manager_->UpdateWriteBackResolution(display_);
  if(ret){
    HWC2_ALOGE("UpdateWriteBackResolution fail.");
    return -1;
  }
  // 获取下一帧的 WB buffer
  std::shared_ptr<DrmBuffer> dst_buffer = resource_manager_->GetNextWBBuffer();
  if(!dst_buffer->initCheck()){
    HWC2_ALOGE("wbBuffer init fail.");
    return -1;
  }

  bWriteBackEnable_ = true;
  int zpos = -1;
  int releaseFence = -1;
  for (DrmCompositionPlane &comp_plane : comp_planes) {
    DrmPlane *plane = comp_plane.plane();
    std::vector<size_t> &source_layers = comp_plane.source_layers();

    crtc = comp_plane.crtc();

    if (comp_plane.type() != DrmCompositionPlane::Type::kDisable) {
      if(source_layers.empty()){
        ALOGE("Can't handle empty source layer CompositionPlane.");
        continue;
      }

      if (source_layers.size() > 1) {
        ALOGE("Can't handle more than one source layer sz=%zu type=%d",
              source_layers.size(), comp_plane.type());
        continue;
      }

      if (source_layers.front() >= layers.size()) {
        ALOGE("Source layer index %zu out of bounds %zu type=%d",
              source_layers.front(), layers.size(), comp_plane.type());
        break;
      }

      DrmHwcLayer &layer = layers[source_layers.front()];

      if (!layer.buffer && !layer.bSidebandStreamLayer_) {
        ALOGE("Expected a valid framebuffer for pset");
        continue;
      }

      zpos = comp_plane.get_zpos();
      if(zpos < 0){
        ALOGE("The zpos(%d) is invalid", zpos);
        continue;
      }

      if(zpos == 0){
        rga_buffer_t src;
        rga_buffer_t dst;
        rga_buffer_t pat;
        im_rect src_rect;
        im_rect dst_rect;
        im_rect pat_rect;

        memset(&src, 0x0, sizeof(rga_buffer_t));
        memset(&dst, 0x0, sizeof(rga_buffer_t));
        memset(&pat, 0x0, sizeof(rga_buffer_t));
        memset(&src_rect, 0x0, sizeof(im_rect));
        memset(&dst_rect, 0x0, sizeof(im_rect));
        memset(&pat_rect, 0x0, sizeof(im_rect));

        if(layer.bSidebandStreamLayer_){
          std::shared_ptr<DrmBuffer> avtive_buffer = NULL;
          if(current_sideband2_.buffer_ != NULL){
            avtive_buffer = current_sideband2_.buffer_;
          }else if(drawing_sideband2_.buffer_ != NULL){
            avtive_buffer = drawing_sideband2_.buffer_;
          }

          if(avtive_buffer == NULL){
            HWC2_ALOGI("avtive_buffer==null, return");
            continue;
          }

          // avtive_buffer->DumpData();
          HWC2_ALOGD_IF_INFO("src buffer-id=0x%" PRIx64 " avtive_buffer=%p fd=%d w=%d h=%d s=%d hs=%d size=%d format=%d",
                avtive_buffer->GetBufferId(),
                avtive_buffer.get(),
                avtive_buffer->GetFd(),
                avtive_buffer->GetWidth(),
                avtive_buffer->GetHeight(),
                avtive_buffer->GetStride(),
                avtive_buffer->GetHeightStride(),
                avtive_buffer->GetSize(),
                avtive_buffer->GetFormat());

          // 利用PQ实现 YUV444 转 YUV420, 若未使能PQ，则RGA无法处理该数据
          if(avtive_buffer->GetFormat() == HAL_PIXEL_FORMAT_YCbCr_444_888 ||
             avtive_buffer->GetFormat() == HAL_PIXEL_FORMAT_NV30){
#ifdef USE_LIBPQ
            bool need_realloc = false;
            if(sidbenad_pq_tmp_buffer_ != NULL){
              if(sidbenad_pq_tmp_buffer_->GetWidth() != avtive_buffer->GetWidth() ||
                 sidbenad_pq_tmp_buffer_->GetHeight() != avtive_buffer->GetHeight()){
                  need_realloc = true;
              }
            }else{
              need_realloc = true;
            }

            if(need_realloc){
              sidbenad_pq_tmp_buffer_ = std::make_shared<DrmBuffer>(avtive_buffer->GetWidth(),
                                                        avtive_buffer->GetHeight(),
                                                        HAL_PIXEL_FORMAT_YCbCr_420_888,
                                                        RK_GRALLOC_USAGE_STRIDE_ALIGN_64 |
                                                        MALI_GRALLOC_USAGE_NO_AFBC,
                                                        "SidebandYuv444TmpBuffer",
                                                        0);
              if(sidbenad_pq_tmp_buffer_->Init()){
                HWC2_ALOGE("DrmBuffer Init fail, w=%d h=%d format=%d name=%s",
                          avtive_buffer->GetWidth(),
                          avtive_buffer->GetHeight(),
                          HAL_PIXEL_FORMAT_YCbCr_420_888,
                          "SidebandYuv444TmpBuffer");
                return -1;
              }
            }
            bool need_reinit_pq = false;
            if(pq_ == NULL ||
               pq_last_init_format_ != avtive_buffer->GetFormat()){
              need_reinit_pq = true;
              pq_last_init_format_ = avtive_buffer->GetFormat();
            }

            if(need_reinit_pq){
              int rkpq_intput_fmt = RKPQ_IMG_FMT_YUV_MIN;
              switch(avtive_buffer->GetFormat()){
                case HAL_PIXEL_FORMAT_YCbCr_444_888:
                  rkpq_intput_fmt = RKPQ_IMG_FMT_NV24;
                  break;
                case HAL_PIXEL_FORMAT_NV30:
                  rkpq_intput_fmt = RKPQ_IMG_FMT_NV30;
                  break;
                default:
                  rkpq_intput_fmt = RKPQ_IMG_FMT_NV24;
                  break;
              }

              /* dataspace可能值为：
                HAL_DATASPACE_STANDARD_BT601_625
                HAL_DATASPACE_BT709
                HAL_DATASPACE_RANGE_LIMITED
                HAL_DATASPACE_RANGE_FULL
                */
              HWC2_ALOGD_IF_INFO("layer.eDataSpace_=0x%x" , layer.eDataSpace_);
              int rkpq_intput_fmt_colorspace = RKPQ_CLR_SPC_YUV_601_FULL;
              if(layer.bYuv_ && layer.eDataSpace_){
                if((layer.eDataSpace_ & HAL_DATASPACE_STANDARD_BT601_625) == HAL_DATASPACE_STANDARD_BT601_625){
                  if((layer.eDataSpace_ & HAL_DATASPACE_RANGE_LIMITED) == HAL_DATASPACE_RANGE_LIMITED){
                    rkpq_intput_fmt_colorspace = RKPQ_CLR_SPC_YUV_601_LIMITED;
                  }

                  if((layer.eDataSpace_ & HAL_DATASPACE_RANGE_FULL) == HAL_DATASPACE_RANGE_FULL){
                    rkpq_intput_fmt_colorspace = RKPQ_CLR_SPC_YUV_601_FULL;
                  }
                }

                if((layer.eDataSpace_ & HAL_DATASPACE_BT709) == HAL_DATASPACE_BT709){
                  if((layer.eDataSpace_ & HAL_DATASPACE_RANGE_LIMITED) == HAL_DATASPACE_RANGE_LIMITED){
                    rkpq_intput_fmt_colorspace = RKPQ_CLR_SPC_YUV_709_LIMITED;
                  }else{
                    rkpq_intput_fmt_colorspace = RKPQ_CLR_SPC_YUV_709_FULL;
                  }
                }
              }

              pq_ = std::make_shared<rkpq>();
              uint32_t src_stride[3] = {0,0,0};
              pq_->init(avtive_buffer->GetWidth(),
                        avtive_buffer->GetHeight(),
                        src_stride,
                        sidbenad_pq_tmp_buffer_->GetWidth(),
                        sidbenad_pq_tmp_buffer_->GetHeight(),
                        64,
                        rkpq_intput_fmt,
                        rkpq_intput_fmt_colorspace,
                        RKPQ_IMG_FMT_NV12,
                        RKPQ_CLR_SPC_YUV_601_FULL,
                        RKPQ_FLAG_HIGH_PERFORM);

              HWC2_ALOGD_IF_INFO("PQ: reinit src: w=%d h=%d fmt=%d colorspace=%d"
                                 " dst: w=%d h=%d fmt=%d colorspace=%d perf=%d",
                                 avtive_buffer->GetWidth(),
                                 avtive_buffer->GetHeight(),
                                 rkpq_intput_fmt,
                                 rkpq_intput_fmt_colorspace,
                                 sidbenad_pq_tmp_buffer_->GetWidth(),
                                 sidbenad_pq_tmp_buffer_->GetHeight(),
                                 RKPQ_IMG_FMT_NV12,
                                 RKPQ_CLR_SPC_YUV_601_FULL,
                                 RKPQ_FLAG_HIGH_PERFORM);
            }

            pq_->dopq(avtive_buffer->GetFd(),
                      sidbenad_pq_tmp_buffer_->GetFd(),
                      PQ_LF_RANGE);
            // avtive_buffer->DumpData();
            // sidbenad_pq_tmp_buffer_->DumpData();
            avtive_buffer = sidbenad_pq_tmp_buffer_;
            layer.eDataSpace_ = HAL_DATASPACE_V0_BT601_625;
#else       // RGA 无法处理 NV24/NV42/NV30 等格式，直接跳过
            continue;
#endif
          }

          // Set src buffer info
          src.fd     = avtive_buffer->GetFd();
          src.width  = avtive_buffer->GetWidth();
          src.height = avtive_buffer->GetHeight();
          src.wstride = avtive_buffer->GetStride();
          src.hstride = avtive_buffer->GetHeightStride();
          // RGA 有一些特殊格式不支持输出，需要转换为 RGA 格式
          // bgr888:HAL_PIXEL_FORMAT_BGR_888
          // nv12:HAL_PIXEL_FORMAT_YCrCb_NV12
          // nv16:HAL_PIXEL_FORMAT_YCbCr_422_SP
          // nv24:HAL_PIXEL_FORMAT_YCbCr_444_888 RGA 不支持，建议HDMI-IN处理掉
          // nv15:HAL_PIXEL_FORMAT_YCrCb_NV12_10
          // nv30:HAL_PIXEL_FORMAT_NV30 RGA 不支持，建议HDMI-IN处理掉
          switch(avtive_buffer->GetFormat()){
            case HAL_PIXEL_FORMAT_BGR_888:
                src.format = RK_FORMAT_BGR_888;
                break;
            case HAL_PIXEL_FORMAT_YCbCr_422_SP:
                src.format = RK_FORMAT_YCbCr_422_SP;
                break;
            case HAL_PIXEL_FORMAT_YCrCb_NV12_10:
                src.format = RK_FORMAT_YCrCb_420_SP_10B;
                break;
            // YUV444格式为 HAL_PIXEL_FORMAT_YCbCr_444_888
            // 故需要转换格式为 RK_FORMAT_YCbCr_420_SP
            case HAL_PIXEL_FORMAT_YCbCr_420_888:
                src.format = RK_FORMAT_YCbCr_420_SP;
                break;
            default:
                src.format = avtive_buffer->GetFormat();
          }

          // Set src rect info
          src_rect.x = ALIGN_DOWN_INT(layer.source_crop.left, YUV_ALIGN);
          src_rect.y = ALIGN_DOWN_INT(layer.source_crop.top, YUV_ALIGN);
          src_rect.width = ALIGN_DOWN_INT(layer.source_crop.right - layer.source_crop.left, YUV_ALIGN);
          src_rect.height = ALIGN_DOWN_INT(layer.source_crop.bottom - layer.source_crop.top, YUV_ALIGN);

          if(layer.uModifier_ > 0)
            src.rd_mode = IM_FBC_MODE;

          /* dataspace可能值为：
            HAL_DATASPACE_STANDARD_BT601_625
            HAL_DATASPACE_BT709
            HAL_DATASPACE_RANGE_LIMITED
            HAL_DATASPACE_RANGE_FULL
            */
          HWC2_ALOGD_IF_INFO("layer.eDataSpace_=0x%x" , layer.eDataSpace_);
          src.color_space_mode = IM_YUV_TO_RGB_BT601_FULL;
          if(layer.bYuv_ && layer.eDataSpace_){
            if((layer.eDataSpace_ & HAL_DATASPACE_STANDARD_BT601_625) == HAL_DATASPACE_STANDARD_BT601_625){
              if((layer.eDataSpace_ & HAL_DATASPACE_RANGE_LIMITED) == HAL_DATASPACE_RANGE_LIMITED){
                src.color_space_mode = IM_YUV_TO_RGB_BT601_LIMIT;
              }

              if((layer.eDataSpace_ & HAL_DATASPACE_RANGE_FULL) == HAL_DATASPACE_RANGE_FULL){
                src.color_space_mode = IM_YUV_TO_RGB_BT601_FULL;
              }
            }

            if((layer.eDataSpace_ & HAL_DATASPACE_BT709) == HAL_DATASPACE_BT709){
              if((layer.eDataSpace_ & HAL_DATASPACE_RANGE_LIMITED) == HAL_DATASPACE_RANGE_LIMITED){
                src.color_space_mode = IM_YUV_TO_RGB_BT709_LIMIT;
              }
            }
          }
        }else{

          // PQ 模式开启后，FbTarget 会被转换为YUV444，此时RGA无法支持输入
          // 目前做法是采用FbTarget原来的数据，即RGBA8888格式作为RGA的合成输入即可实现录屏需求
          if(layer.bFbTarget_  && layer.iFormat_ == HAL_PIXEL_FORMAT_YCbCr_444_888){
              // Set src buffer info
              src.fd     = layer.storeLayerInfo_.iFd_;
              src.width  = layer.storeLayerInfo_.iWidth_;
              src.height = layer.storeLayerInfo_.iHeight_;
              src.wstride = layer.storeLayerInfo_.iStride_;
              src.hstride = layer.storeLayerInfo_.iHeightStride_;
              src.format = layer.storeLayerInfo_.iFormat_;

              // Set src rect info
              src_rect.x = ALIGN_DOWN_INT(layer.storeLayerInfo_.source_crop.left, YUV_ALIGN);
              src_rect.y = ALIGN_DOWN_INT(layer.storeLayerInfo_.source_crop.top, YUV_ALIGN);
              src_rect.width = ALIGN_DOWN_INT(layer.storeLayerInfo_.source_crop.right - layer.storeLayerInfo_.source_crop.left, YUV_ALIGN);
              src_rect.height = ALIGN_DOWN_INT(layer.storeLayerInfo_.source_crop.bottom - layer.storeLayerInfo_.source_crop.top, YUV_ALIGN);

              if(layer.storeLayerInfo_.uModifier_ > 0)
                src.rd_mode = IM_FBC_MODE;
          }else{
              // Set src buffer info
              src.fd     = layer.iFd_;
              src.width  = layer.iWidth_;
              src.height = layer.iHeight_;
              src.wstride = layer.iStride_;
              src.hstride = layer.iHeightStride_;
              src.format = layer.iFormat_;

              // Set src rect info
              src_rect.x = ALIGN_DOWN_INT(layer.source_crop.left, YUV_ALIGN);
              src_rect.y = ALIGN_DOWN_INT(layer.source_crop.top, YUV_ALIGN);
              src_rect.width = ALIGN_DOWN_INT(layer.source_crop.right - layer.source_crop.left, YUV_ALIGN);
              src_rect.height = ALIGN_DOWN_INT(layer.source_crop.bottom - layer.source_crop.top, YUV_ALIGN);

              if(layer.uModifier_ > 0)
                src.rd_mode = IM_FBC_MODE;
              }
        }

        // Set dst buffer info
        dst.fd     = dst_buffer->GetFd();
        dst.width  = dst_buffer->GetWidth();
        dst.height = dst_buffer->GetHeight();
        dst.wstride = dst_buffer->GetStride();
        dst.hstride = dst_buffer->GetHeightStride();
        dst.format = dst_buffer->GetFormat();

        // Set src rect info
        dst_rect.x = ALIGN_DOWN_INT(layer.display_frame_sf.left, YUV_ALIGN);
        dst_rect.y = ALIGN_DOWN_INT(layer.display_frame_sf.top, YUV_ALIGN);
        dst_rect.width = ALIGN_DOWN_INT(layer.display_frame_sf.right - layer.display_frame_sf.left, YUV_ALIGN);
        dst_rect.height = ALIGN_DOWN_INT(layer.display_frame_sf.bottom - layer.display_frame_sf.top, YUV_ALIGN);

        IM_STATUS im_state;
        im_opt_t opt;
        memset(&opt, 0x00, sizeof(im_opt_t));

        int usage = IM_SYNC;
        // Call Im2d 格式转换
        im_state = improcess(src, dst, pat, src_rect, dst_rect, pat_rect,  -1, NULL, &opt, usage);
        if (im_state == IM_STATUS_SUCCESS) {
            HWC2_ALOGD_IF_INFO("%s running success! zpos==0 \n", LOG_TAG);
        } else {
            HWC2_ALOGE("%s running failed,  zpos==0  %s\n", LOG_TAG, imStrError((IM_STATUS)ret));
        }
      }else{
        rga_buffer_t src;
        rga_buffer_t dst;
        rga_buffer_t pat;
        im_rect src_rect;
        im_rect dst_rect;
        im_rect pat_rect;

        memset(&src, 0x0, sizeof(rga_buffer_t));
        memset(&dst, 0x0, sizeof(rga_buffer_t));
        memset(&pat, 0x0, sizeof(rga_buffer_t));
        memset(&src_rect, 0x0, sizeof(im_rect));
        memset(&dst_rect, 0x0, sizeof(im_rect));
        memset(&pat_rect, 0x0, sizeof(im_rect));

        // Set dst buffer info
        src.fd     = layer.iFd_;
        src.width  = layer.iWidth_;
        src.height = layer.iHeight_;
        src.wstride = layer.iStride_;
        src.hstride = layer.iHeightStride_;
        src.format = layer.iFormat_;

        // Set src rect info
        src_rect.x = ALIGN_DOWN_INT(layer.source_crop.left, YUV_ALIGN);
        src_rect.y = ALIGN_DOWN_INT(layer.source_crop.top, YUV_ALIGN);
        src_rect.width = ALIGN_DOWN_INT(layer.source_crop.right - layer.source_crop.left, YUV_ALIGN);
        src_rect.height = ALIGN_DOWN_INT(layer.source_crop.bottom - layer.source_crop.top, YUV_ALIGN);

        if(layer.uModifier_ > 0)
          src.rd_mode = IM_FBC_MODE;

        // Set dst buffer info
        dst.fd     = dst_buffer->GetFd();
        dst.width  = dst_buffer->GetWidth();
        dst.height = dst_buffer->GetHeight();
        dst.wstride = dst_buffer->GetStride();
        dst.hstride = dst_buffer->GetHeightStride();
        dst.format = dst_buffer->GetFormat();

        // Set src rect info
        dst_rect.x = ALIGN_DOWN_INT(layer.source_crop.left, YUV_ALIGN);
        dst_rect.y = ALIGN_DOWN_INT(layer.source_crop.top, YUV_ALIGN);
        dst_rect.width = ALIGN_DOWN_INT(layer.source_crop.right - layer.source_crop.left, YUV_ALIGN);
        dst_rect.height = ALIGN_DOWN_INT(layer.source_crop.bottom - layer.source_crop.top, YUV_ALIGN);

        IM_STATUS im_state;
        im_opt_t opt;
        memset(&opt, 0x00, sizeof(im_opt_t));

        int usage = IM_ASYNC | IM_ALPHA_BLEND_SRC_OVER | IM_ALPHA_BLEND_PRE_MUL;
        // Call Im2d 格式转换
        im_state = improcess(src, dst, pat, src_rect, dst_rect, pat_rect,  0, &releaseFence, &opt, usage);
        if (im_state == IM_STATUS_SUCCESS) {
            HWC2_ALOGD_IF_INFO("%s running success! zpos==0 \n", LOG_TAG);
        } else {
            HWC2_ALOGE("%s running failed,  zpos==0  %s\n", LOG_TAG, imStrError((IM_STATUS)ret));
        }
      }
    }
  }

  // WriteBack Fence handle.
  if(resource_manager_->isWBMode()){
    wbDisplay = resource_manager_->GetWBDisplay();
    if(wbDisplay == display_){
      std::shared_ptr<DrmBuffer> wbBuffer = resource_manager_->GetNextWBBuffer();
      if(releaseFence > 0){
        wbBuffer->SetFinishFence(releaseFence);
      }
      resource_manager_->SwapWBBuffer(frame_no_);
    }
  }
  return ret;
}

int DrmDisplayCompositor::Composite() {
  ATRACE_CALL();

  // 收集来自 SurfaceFlinger 端的送显需求
  if(CollectSFInfo()){
    HWC2_ALOGE("CollectSFInfo fail.");
  }

  int ret = pthread_mutex_lock(&lock_);
  if (ret) {
    ALOGE("Failed to acquire compositor lock %d", ret);
    return ret;
  }
  if(IsSidebandMode() && CollectVPInfo()){
    HWC2_ALOGE("CollectVPInfo fail.");
  }

  ret = pthread_mutex_unlock(&lock_);
  if (ret) {
    ALOGE("Failed to release compositor lock %d", ret);
    return ret;
  }

  Commit();
  SyntheticWaitVBlank();

  // 若 DrmDisplayCompositor 电源被关闭，则直接不处理后续的刷新请求
  // DrmHwc2 前端处理热插拔逻辑，可能会遗漏部分 Compsition 到 DrmDisplayCompositor线程
  // 需要在此处拦截，并清空送显队列
  if (!active_){
    HWC2_ALOGD_IF_INFO("display=%d active_=%d not to Composite()", display_, active_);
    ClearDisplay();
    return 0;
  }
  return 0;
}

bool DrmDisplayCompositor::HaveQueuedComposites() const {
  int ret = pthread_mutex_lock(&lock_);
  if (ret) {
    ALOGE("Failed to acquire compositor lock %d", ret);
    return false;
  }

  bool empty_ret = !composite_queue_.empty();

  ret = pthread_mutex_unlock(&lock_);
  if (ret) {
    ALOGE("Failed to release compositor lock %d", ret);
    return false;
  }

  return empty_ret;
}

bool DrmDisplayCompositor::IsSidebandMode() const{
  return current_sideband2_.enable_;
}

int DrmDisplayCompositor::GetCompositeQueueMaxSize(DrmDisplayComposition* composition){
  // SVEP 要求缓存3帧，以便得到流畅的SVEP播放效果
  if(composition->has_svep()){
      return 3;
  }

  // 丢帧模式，暂定最大缓存帧数等于 10,实际缓存不会达到这个数值
  if(composition->IsDropMode()){
      drop_mode_ = true;
      return 10;
  }

  // 一般情况下，只考虑最多缓存1帧
  return 1;
}

int DrmDisplayCompositor::TestComposition(DrmDisplayComposition *composition) {
  return CommitFrame(composition, true);
}

// Flatten a scene on the display by using a writeback connector
// and returns the composition result as a DrmHwcLayer.
int DrmDisplayCompositor::FlattenOnDisplay(
    std::unique_ptr<DrmDisplayComposition> &src, DrmConnector *writeback_conn,
    DrmMode &src_mode, DrmHwcLayer *writeback_layer) {
  int ret = 0;
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  ret = writeback_conn->UpdateModes();
  if (ret) {
    ALOGE("Failed to update modes %d", ret);
    return ret;
  }
  for (const DrmMode &mode : writeback_conn->modes()) {
    if (mode.h_display() == src_mode.h_display() &&
        mode.v_display() == src_mode.v_display()) {
      mode_.mode = mode;
      if (mode_.blob_id)
        drm->DestroyPropertyBlob(mode_.blob_id);
      std::tie(ret, mode_.blob_id) = CreateModeBlob(mode_.mode);
      if (ret) {
        ALOGE("Failed to create mode blob for display %d", display_);
        return ret;
      }
      mode_.needs_modeset = true;
      break;
    }
  }
  if (mode_.blob_id <= 0) {
    ALOGE("Failed to find similar mode");
    return -EINVAL;
  }

  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    ALOGE("Failed to find crtc for display %d", display_);
    return -EINVAL;
  }
  // TODO what happens if planes could go to both CRTCs, I don't think it's
  // handled anywhere
  std::vector<DrmPlane *> primary_planes;
  std::vector<DrmPlane *> overlay_planes;
  for (auto &plane : drm->planes()) {
    if (!plane->GetCrtcSupported(*crtc))
      continue;
    if (plane->type() == DRM_PLANE_TYPE_PRIMARY)
      primary_planes.push_back(plane.get());
    else if (plane->type() == DRM_PLANE_TYPE_OVERLAY)
      overlay_planes.push_back(plane.get());
  }

  ret = src->DisableUnusedPlanes();
  if (ret) {
    ALOGE("Failed to plan the composition ret = %d", ret);
    return ret;
  }

  AutoLock lock(&lock_, __func__);
  ret = lock.Lock();
  if (ret)
    return ret;
  DrmFramebuffer *writeback_fb = &framebuffers_[framebuffer_index_];
  framebuffer_index_ = (framebuffer_index_ + 1) % DRM_DISPLAY_BUFFERS;
  if (!writeback_fb->Allocate(mode_.mode.h_display(), mode_.mode.v_display())) {
    ALOGE("Failed to allocate writeback buffer");
    return -ENOMEM;
  }
  DrmHwcBuffer *writeback_buffer = &writeback_layer->buffer;
  writeback_layer->sf_handle = writeback_fb->buffer()->handle;
  ret = writeback_layer->ImportBuffer(
      resource_manager_->GetImporter(display_).get());
  if (ret) {
    ALOGE("Failed to import writeback buffer");
    return ret;
  }

  ret = CommitFrame(src.get(), true, writeback_conn, writeback_buffer);
  if (ret) {
    ALOGE("Atomic check failed");
    return ret;
  }
  ret = CommitFrame(src.get(), false, writeback_conn, writeback_buffer);
  if (ret) {
    ALOGE("Atomic commit failed");
    return ret;
  }

  ret = sync_wait(writeback_fence_, kWaitWritebackFence);
  writeback_layer->acquire_fence = sp<AcquireFence>(new AcquireFence(writeback_fence_));
  writeback_fence_ = -1;
  if (ret) {
    ALOGE("Failed to wait on writeback fence");
    return ret;
  }
  return 0;
}

// Flatten a scene by enabling the writeback connector attached
// to the same CRTC as the one driving the display.
int DrmDisplayCompositor::FlattenSerial(DrmConnector *writeback_conn) {
  ALOGV("FlattenSerial by enabling writeback connector to the same crtc");
  // Flattened composition with only one layer that is obtained
  // using the writeback connector
  std::unique_ptr<DrmDisplayComposition>
      writeback_comp = CreateInitializedComposition();
  if (!writeback_comp)
    return -EINVAL;

  AutoLock lock(&lock_, __func__);
  int ret = lock.Lock();
  if (ret)
    return ret;
  if (!CountdownExpired() || active_composition_->layers().size() < 2) {
    ALOGV("Flattening is not needed");
    return -EALREADY;
  }

  DrmFramebuffer *writeback_fb = &framebuffers_[framebuffer_index_];
  framebuffer_index_ = (framebuffer_index_ + 1) % DRM_DISPLAY_BUFFERS;
  lock.Unlock();

  if (!writeback_fb->Allocate(mode_.mode.h_display(), mode_.mode.v_display())) {
    ALOGE("Failed to allocate writeback buffer");
    return -ENOMEM;
  }
  writeback_comp->layers().emplace_back();

  DrmHwcLayer &writeback_layer = writeback_comp->layers().back();
  writeback_layer.sf_handle = writeback_fb->buffer()->handle;
  writeback_layer.source_crop = {0, 0, (float)mode_.mode.h_display(),
                                 (float)mode_.mode.v_display()};
  writeback_layer.display_frame = {0, 0, (int)mode_.mode.h_display(),
                                   (int)mode_.mode.v_display()};
  ret = writeback_layer.ImportBuffer(
      resource_manager_->GetImporter(display_).get());
  if (ret || writeback_comp->layers().size() != 1) {
    ALOGE("Failed to import writeback buffer");
    return ret;
  }

  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    ALOGE("Failed to allocate property set");
    return -ENOMEM;
  }
  DrmDevice *drm = resource_manager_->GetDrmDevice(display_);
  DrmCrtc *crtc = drm->GetCrtcForDisplay(display_);
  if (!crtc) {
    ALOGE("Failed to find crtc for display %d", display_);
    drmModeAtomicFree(pset);
    pset=NULL;
    return -EINVAL;
  }
  ret = SetupWritebackCommit(pset, crtc->id(), writeback_conn,
                             &writeback_layer.buffer);
  if (ret < 0) {
    ALOGE("Failed to Setup Writeback Commit");
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }
  ret = drmModeAtomicCommit(drm->fd(), pset, 0, drm);
  if (ret) {
    ALOGE("Failed to enable writeback %d", ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }
  drmModeAtomicFree(pset);
  pset=NULL;

  ret = sync_wait(writeback_fence_, kWaitWritebackFence);
  writeback_layer.acquire_fence = sp<AcquireFence>(new AcquireFence(writeback_fence_));
  writeback_fence_ = -1;
  if (ret) {
    ALOGE("Failed to wait on writeback fence");
    return ret;
  }

  DrmCompositionPlane squashed_comp(DrmCompositionPlane::Type::kLayer, NULL,
                                    crtc);
  for (auto &drmplane : drm->planes()) {
    if (!drmplane->GetCrtcSupported(*crtc))
      continue;
    if (!squashed_comp.plane() && drmplane->type() == DRM_PLANE_TYPE_PRIMARY)
      squashed_comp.set_plane(drmplane.get());
    else
      writeback_comp->AddPlaneDisable(drmplane.get());
  }
  squashed_comp.source_layers().push_back(0);
  ret = writeback_comp->AddPlaneComposition(std::move(squashed_comp));
  if (ret) {
    ALOGE("Failed to add flatten scene");
    return ret;
  }

  ApplyFrame(std::move(writeback_comp), 0, true);
  return 0;
}

// Flatten a scene by using a crtc which works concurrent with
// the one driving the display.
int DrmDisplayCompositor::FlattenConcurrent(DrmConnector *writeback_conn) {
  ALOGV("FlattenConcurrent by using an unused crtc/display");
  int ret = 0;
  DrmDisplayCompositor drmdisplaycompositor;
  ret = drmdisplaycompositor.Init(resource_manager_, writeback_conn->display());
  if (ret) {
    ALOGE("Failed to init  drmdisplaycompositor = %d", ret);
    return ret;
  }
  // Copy of the active_composition, needed because of two things:
  // 1) Not to hold the lock for the whole time we are accessing
  //    active_composition
  // 2) It will be committed on a crtc that might not be on the same
  //     dri node, so buffers need to be imported on the right node.
  std::unique_ptr<DrmDisplayComposition>
      copy_comp = drmdisplaycompositor.CreateInitializedComposition();

  // Writeback composition that will be committed to the display.
  std::unique_ptr<DrmDisplayComposition>
      writeback_comp = CreateInitializedComposition();

  if (!copy_comp || !writeback_comp)
    return -EINVAL;
  AutoLock lock(&lock_, __func__);
  ret = lock.Lock();
  if (ret)
    return ret;
  if (!CountdownExpired() || active_composition_->layers().size() < 2) {
    ALOGV("Flattening is not needed");
    return -EALREADY;
  }
  DrmCrtc *crtc = active_composition_->crtc();

  std::vector<DrmHwcLayer> copy_layers;
  for (DrmHwcLayer &src_layer : active_composition_->layers()) {
    DrmHwcLayer copy;
    ret = copy.InitFromDrmHwcLayer(&src_layer,
                                   resource_manager_
                                       ->GetImporter(writeback_conn->display())
                                       .get());
    if (ret) {
      ALOGE("Failed to import buffer ret = %d", ret);
      return -EINVAL;
    }
    copy_layers.emplace_back(std::move(copy));
  }
  ret = copy_comp->SetLayers(copy_layers.data(), copy_layers.size(), true);
  if (ret) {
    ALOGE("Failed to set copy_comp layers");
    return ret;
  }

  lock.Unlock();
  DrmHwcLayer writeback_layer;
  ret = drmdisplaycompositor.FlattenOnDisplay(copy_comp, writeback_conn,
                                              mode_.mode, &writeback_layer);
  if (ret) {
    ALOGE("Failed to flatten on display ret = %d", ret);
    return ret;
  }

  DrmCompositionPlane squashed_comp(DrmCompositionPlane::Type::kLayer, NULL,
                                    crtc);
  for (auto &drmplane : resource_manager_->GetDrmDevice(display_)->planes()) {
    if (!drmplane->GetCrtcSupported(*crtc))
      continue;
    if (drmplane->type() == DRM_PLANE_TYPE_PRIMARY)
      squashed_comp.set_plane(drmplane.get());
    else
      writeback_comp->AddPlaneDisable(drmplane.get());
  }
  writeback_comp->layers().emplace_back();
  DrmHwcLayer &next_layer = writeback_comp->layers().back();
  next_layer.sf_handle = writeback_layer.get_usable_handle();
  next_layer.blending = DrmHwcBlending::kPreMult;
  next_layer.source_crop = {0, 0, (float)mode_.mode.h_display(),
                            (float)mode_.mode.v_display()};
  next_layer.display_frame = {0, 0, (int)mode_.mode.h_display(),
                              (int)mode_.mode.v_display()};
  ret = next_layer.ImportBuffer(resource_manager_->GetImporter(display_).get());
  if (ret) {
    ALOGE("Failed to import framebuffer for display %d", ret);
    return ret;
  }
  squashed_comp.source_layers().push_back(0);
  ret = writeback_comp->AddPlaneComposition(std::move(squashed_comp));
  if (ret) {
    ALOGE("Failed to add plane composition %d", ret);
    return ret;
  }
  ApplyFrame(std::move(writeback_comp), 0, true);
  return ret;
}

int DrmDisplayCompositor::FlattenActiveComposition() {
  DrmConnector *writeback_conn = resource_manager_->AvailableWritebackConnector(
      display_);
  if (!active_composition_ || !writeback_conn) {
    ALOGV("No writeback connector available");
    return -EINVAL;
  }

  if (writeback_conn->display() != display_) {
    return FlattenConcurrent(writeback_conn);
  } else {
    return FlattenSerial(writeback_conn);
  }

  return 0;
}

bool DrmDisplayCompositor::CountdownExpired() const {
  return flatten_countdown_ <= 0;
}

void DrmDisplayCompositor::Vsync(int display, int64_t timestamp) {
  AutoLock lock(&lock_, __func__);
  if (lock.Lock())
    return;
  flatten_countdown_--;
  if (!CountdownExpired())
    return;
  lock.Unlock();
  int ret = FlattenActiveComposition();
  ALOGV("scene flattening triggered for display %d at timestamp %" PRIu64
        " result = %d \n",
        display, timestamp, ret);
}

void DrmDisplayCompositor::Dump(std::ostringstream *out) const {
  int ret = pthread_mutex_lock(&lock_);
  if (ret)
    return;

  uint64_t num_frames = dump_frames_composited_;
  dump_frames_composited_ = 0;

  struct timespec ts;
  ret = clock_gettime(CLOCK_MONOTONIC, &ts);
  if (ret) {
    pthread_mutex_unlock(&lock_);
    return;
  }

  uint64_t cur_ts = ts.tv_sec * 1000 * 1000 * 1000 + ts.tv_nsec;
  uint64_t num_ms = (cur_ts - dump_last_timestamp_ns_) / (1000 * 1000);
  float fps = num_ms ? (num_frames * 1000.0f) / (num_ms) : 0.0f;

  *out << "--DrmDisplayCompositor[" << display_
       << "]: num_frames=" << num_frames << " num_ms=" << num_ms
       << " fps=" << fps << "\n";

  dump_last_timestamp_ns_ = cur_ts;

  pthread_mutex_unlock(&lock_);
}
}  // namespace android
