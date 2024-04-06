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
#define LOG_TAG "hwc-video-producer"

#include "rockchip/utils/drmdebug.h"
#include "rockchip/producer/drmvideoproducer.h"
#include <utils/Trace.h>

#include <dlfcn.h>
#include <fcntl.h>
namespace android {

#if defined(__arm64__) || defined(__aarch64__)
#define RK_LIB_VT_PATH "/vendor/lib64/librkvt.so"
#else
#define RK_LIB_VT_PATH "/vendor/lib/librkvt.so"
#endif

// Next Hdr
typedef int (*rk_vt_open_func)(void);
typedef int (*rk_vt_close_func)(int fd);
typedef int (*rk_vt_connect_func)(int fd, int tunnel_id, int role);
typedef int (*rk_vt_disconnect_func)(int fd, int tunnel_id, int role);
typedef int (*rk_vt_acquire_buffer_func)(int fd,
                                         int tunnel_id,
                                         int timeout_ms,
                                         vt_buffer_t **buffer,
                                         int64_t *expected_present_time);
typedef int (*rk_vt_release_buffer_func)(int fd, int tunnel_id, vt_buffer_t *buffer);

struct rkvt_ops {
    int (*rk_vt_open)(void);
    int (*rk_vt_close)(int fd);
    int (*rk_vt_connect)(int fd, int tunnel_id, int role);
    int (*rk_vt_disconnect)(int fd, int tunnel_id, int role);
    int (*rk_vt_acquire_buffer)(int fd,
                                     int tunnel_id,
                                     int timeout_ms,
                                     vt_buffer_t **buffer,
                                     int64_t *expected_present_time);
    int (*rk_vt_release_buffer)(int fd, int tunnel_id, vt_buffer_t *buffer);
};

static struct rkvt_ops g_rkvt_ops;
static void * g_rkvt_lib_handle = NULL;

DrmVideoProducer::DrmVideoProducer()
  : bInit_(false),
    iTunnelFd_(-1){}

DrmVideoProducer::~DrmVideoProducer(){
  std::lock_guard<std::mutex> lock(mtx_);
  if(iTunnelFd_ > 0){
    int ret = g_rkvt_ops.rk_vt_close(iTunnelFd_);
    if (ret < 0) {
      HWC2_ALOGE("rk_vt_close fail ret=%d", ret);
    }
  }
}

// Init video tunel.
int DrmVideoProducer::Init(){
  std::lock_guard<std::mutex> lock(mtx_);
  if(bInit_)
    return 0;

  if(InitLibHandle()){
    HWC2_ALOGE("init fail, disable VideoProducer function.");
    return -1;
  }

  if(iTunnelFd_ < 0){
    iTunnelFd_ = g_rkvt_ops.rk_vt_open();
    if(iTunnelFd_ < 0){
        HWC2_ALOGE("rk_vt_open fail ret=%d", iTunnelFd_);
        return -1;
    }
  }

  HWC2_ALOGI("Init success fd=%d", iTunnelFd_);
  bInit_ = true;
  return 0;
}

int DrmVideoProducer::InitLibHandle(){
  g_rkvt_lib_handle = dlopen(RK_LIB_VT_PATH, RTLD_NOW);
  if (g_rkvt_lib_handle == NULL) {
      HWC2_ALOGE("cat not open %s\n", RK_LIB_VT_PATH);
      return -1;
  }else{
      g_rkvt_ops.rk_vt_open = (rk_vt_open_func)dlsym(g_rkvt_lib_handle, "rk_vt_open");
      g_rkvt_ops.rk_vt_close = (rk_vt_close_func)dlsym(g_rkvt_lib_handle, "rk_vt_close");
      g_rkvt_ops.rk_vt_connect = (rk_vt_connect_func)dlsym(g_rkvt_lib_handle, "rk_vt_connect");
      g_rkvt_ops.rk_vt_disconnect = (rk_vt_disconnect_func)dlsym(g_rkvt_lib_handle, "rk_vt_disconnect");
      g_rkvt_ops.rk_vt_acquire_buffer = (rk_vt_acquire_buffer_func)dlsym(g_rkvt_lib_handle, "rk_vt_acquire_buffer");
      g_rkvt_ops.rk_vt_release_buffer = (rk_vt_release_buffer_func)dlsym(g_rkvt_lib_handle, "rk_vt_release_buffer");

      if(g_rkvt_ops.rk_vt_open == NULL||
         g_rkvt_ops.rk_vt_close == NULL ||
         g_rkvt_ops.rk_vt_connect == NULL ||
         g_rkvt_ops.rk_vt_disconnect == NULL ||
         g_rkvt_ops.rk_vt_acquire_buffer == NULL ||
         g_rkvt_ops.rk_vt_release_buffer == NULL){
        HWC2_ALOGD_IF_ERR("cat not dlsym open=%p close=%p connect=%p disconnect=%p acquire_buffer=%p release_buffer=%p\n",
                          g_rkvt_ops.rk_vt_open,
                          g_rkvt_ops.rk_vt_close,
                          g_rkvt_ops.rk_vt_connect,
                          g_rkvt_ops.rk_vt_disconnect,
                          g_rkvt_ops.rk_vt_acquire_buffer,
                          g_rkvt_ops.rk_vt_release_buffer);
        return -1;
      }
  }
  HWC2_ALOGE("InitLibHandle %s success!\n", RK_LIB_VT_PATH);
  return 0;
}


bool DrmVideoProducer::IsValid(){
  return bInit_;
}

// Create tunnel connection.
int DrmVideoProducer::CreateConnection(int display_id, int tunnel_id){
  std::lock_guard<std::mutex> lock(mtx_);
  if(!bInit_){
    HWC2_ALOGE(" fail, display-id=%d bInit_=%d tunnel-fd=%d", display_id, bInit_, iTunnelFd_);
    return -1;
  }

  if(mMapCtx_.count(tunnel_id)){
    std::shared_ptr<VpContext> ctx = mMapCtx_[tunnel_id];
    if(!ctx->AddConnRef(display_id)){
      HWC2_ALOGI("display-id=%d tunnel_id=%d success, connections size=%d", display_id, tunnel_id, ctx->ConnectionCnt());
    }
    return 0;
  }

  int ret = g_rkvt_ops.rk_vt_connect(iTunnelFd_, tunnel_id, RKVT_ROLE_CONSUMER);
  if (ret < 0) {
      return ret;
  }

  HWC2_ALOGI("display-id=%d tunnel_id=%d success", display_id, tunnel_id);
  mMapCtx_[tunnel_id] = std::make_shared<VpContext>(tunnel_id);
  std::shared_ptr<VpContext> ctx = mMapCtx_[tunnel_id];
  ctx->AddConnRef(display_id);
  return 0;
}

// Destory Connection
int DrmVideoProducer::DestoryConnection(int display_id, int tunnel_id){
  std::lock_guard<std::mutex> lock(mtx_);

  if(!bInit_){
    HWC2_ALOGE("fail, display=%d bInit_=%d tunnel-fd=%d", display_id, bInit_, iTunnelFd_);
    return -1;
  }

  if(mMapCtx_.count(tunnel_id) == 0){
    HWC2_ALOGE("display_id=%d mMapCtx_ can't find tunnel_id=%d", display_id, tunnel_id);
    return -1;
  }

  std::shared_ptr<VpContext> ctx = mMapCtx_[tunnel_id];
  ctx->ReleaseConnRef(display_id);
  if(ctx->ConnectionCnt() > 0){
    HWC2_ALOGD_IF_DEBUG("display=%d tunnel_id=%d connection cnt=%d, no need to destory. ",
                         display_id, tunnel_id, ctx->ConnectionCnt());
    return 0;
  }

  int ret = g_rkvt_ops.rk_vt_disconnect(iTunnelFd_, ctx->GetTunnelId(), RKVT_ROLE_CONSUMER);
  if (ret < 0) {
      HWC2_ALOGE("display_id=%d rk_vt_disconnect fail TunnelId=%d", display_id,  ctx->GetTunnelId());
      return ret;
  }

  mMapCtx_.erase(tunnel_id);
  HWC2_ALOGD_IF_DEBUG("display=%d tunnel_id=%d connection cnt=%d success! ",
                        display_id, tunnel_id, ctx->ConnectionCnt());
  return 0;
}

// Get Last video buffer
std::shared_ptr<DrmBuffer> DrmVideoProducer::AcquireBuffer(int display_id,
                                                           int tunnel_id,
                                                           vt_rect_t *dis_rect,
                                                           int timeout_ms){
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(mtx_);

  if(!bInit_){
    HWC2_ALOGE("fail, display-id=%d bInit_=%d tunnel-fd=%d", display_id, bInit_, iTunnelFd_);
    return NULL;
  }

  if(!mMapCtx_.count(tunnel_id)){
    HWC2_ALOGE("display=%d mMapCtx_ can't find tunnel_id=%d", display_id, tunnel_id);
    return NULL;
  }

  // 获取 VideoProducer 上下文
  std::shared_ptr<VpContext> ctx = mMapCtx_[tunnel_id];

  // 请求最新帧
  vt_buffer_t *acquire_buffer = NULL;
  int64_t queue_timestamp = 0;
  int ret = g_rkvt_ops.rk_vt_acquire_buffer(iTunnelFd_, ctx->GetTunnelId(), timeout_ms, &acquire_buffer, &queue_timestamp);
  if (ret != 0) { // 若当前请求无法获得新 buffer, 则判断是否需要获取上一帧 Buffer
      HWC2_ALOGD_IF_WARN("display=%d rk_vt_acquire_buffer fail, bInit_=%d tunnel-fd=%d tunnel-id=%d" ,
                          display_id, bInit_, iTunnelFd_, tunnel_id);
    uint64_t last_handle_buffer_id = ctx->GetLastHandleBufferId();
    if(last_handle_buffer_id > 0){
      std::shared_ptr<DrmBuffer> buffer = ctx->GetLastBufferCache(last_handle_buffer_id);
      if(buffer == NULL){
        HWC2_ALOGD_IF_WARN("display=%d BufferId=%" PRIu64" GetLastBufferCache fail.", display_id, last_handle_buffer_id);
        return NULL;
      }else{
        HWC2_ALOGI("display=%d BufferId=%" PRIu64"", display_id, last_handle_buffer_id);
        return buffer;
      }
    }
      return NULL;
  }

  // 设置时间戳信息
  ctx->SetTimeStamp(queue_timestamp);

  // 设置目标显示区域
  acquire_buffer->dis_rect.left   = dis_rect->left;
  acquire_buffer->dis_rect.top    = dis_rect->top;
  acquire_buffer->dis_rect.right  = dis_rect->right;
  acquire_buffer->dis_rect.bottom = dis_rect->bottom;

  // 获取 buffer cache信息
  std::shared_ptr<DrmBuffer> buffer = ctx->GetBufferCache(acquire_buffer);
  if(!buffer->initCheck()){
    HWC2_ALOGI("display=%d DrmBuffer import fail, acquire_buffer=%p present_time=%" PRIi64 ,
             display_id, acquire_buffer, queue_timestamp);
    // ctx->GetLastBufferCache();
    return NULL;
  }

  // 创建 ReleaseFence
  ret = ctx->AddReleaseFence(acquire_buffer->buffer_id);
  if(ret){
    HWC2_ALOGE("display=%d BufferId=%" PRIu64" AddReleaseFence fail.", display_id, acquire_buffer->buffer_id);
    return NULL;
  }

  ret = ctx->AddReleaseFenceRefCnt(display_id, acquire_buffer->buffer_id);
  if(ret){
    HWC2_ALOGE("display=%d BufferId=%" PRIu64" AddReleaseFenceRefCnt fail.", display_id, acquire_buffer->buffer_id);
    return NULL;
  }

  HWC2_ALOGD_IF_INFO("display=%d tunnel-id=%d success, acquire_buffer=%p crop=[%d,%d,%d,%d] BufferId=%" PRIu64 " present_time=%" PRIi64 ,
             display_id,
             ctx->GetTunnelId(), acquire_buffer,
             acquire_buffer->crop.left,
             acquire_buffer->crop.top,
             acquire_buffer->crop.right,
             acquire_buffer->crop.bottom,
             acquire_buffer->buffer_id, queue_timestamp);
  return buffer;
}

// Release video buffer
int DrmVideoProducer::ReleaseBuffer(int display_id, int tunnel_id, uint64_t buffer_id){
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(mtx_);

  if(!bInit_){
    HWC2_ALOGE(" fail, display=%d bInit_=%d tunnel_id=%d",
              display_id, bInit_, tunnel_id);
    return -1;
  }

  if(!mMapCtx_.count(tunnel_id)){
    HWC2_ALOGE("display=%d mMapCtx_ can't find tunnel_id=%d", display_id, tunnel_id);
    return -1;
  }

  std::shared_ptr<VpContext> ctx = mMapCtx_[tunnel_id];
  // 打印时延
  ctx->VpPrintTimestamp();
  // 获取
  vt_buffer_t* vt_buffer_info = ctx->GetVpBufferInfo(buffer_id);
  if(vt_buffer_info == NULL){
    HWC2_ALOGE("display=%d vt_buffer_info is null tunnel_id=%d", display_id, tunnel_id);
    return -1;
  }

  vt_buffer_info->fence_fd = -1;
  sp<ReleaseFence> release_fence = ctx->GetReleaseFence(buffer_id);
  if(release_fence != NULL){
    vt_buffer_info->fence_fd = dup(release_fence->getFd());
  }
  int ret = g_rkvt_ops.rk_vt_release_buffer(iTunnelFd_, ctx->GetTunnelId(), vt_buffer_info);
  if(ret){
    HWC2_ALOGE("display=%d BufferId=%" PRIu64 " release fail.", display_id, buffer_id);
    return -1;
  }

  ctx->ReleaseBufferInfo(buffer_id);

  HWC2_ALOGD_IF_INFO("display=%d tunnel-id=%d BufferId=%" PRIu64 " ReleaseBuffer success", display_id, tunnel_id, buffer_id);
  return 0;
}

// Release video buffer
int DrmVideoProducer::SignalReleaseFence(int display_id, int tunnel_id, uint64_t buffer_id){
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(mtx_);

  if(!bInit_){
    HWC2_ALOGE(" fail, display=%d bInit_=%d tunnel_id=%d",
              display_id, bInit_, tunnel_id);
    return -1;
  }

  if(!mMapCtx_.count(tunnel_id)){
    HWC2_ALOGE("display=%d mMapCtx_ can't find tunnel_id=%d", display_id, tunnel_id);
    return -1;
  }

  std::shared_ptr<VpContext> ctx = mMapCtx_[tunnel_id];
  return ctx->SignalReleaseFence(display_id, buffer_id);;
}

};