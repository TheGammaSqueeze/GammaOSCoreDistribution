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
#ifndef _VP_CONTEXT_H_
#define _VP_CONTEXT_H_

#include <mutex>
#include <map>
#include <set>
#include <utility>

#include "utils/drmfence.h"
#include "drmbuffer.h"
#include "rockchip/drmgralloc.h"
#include "rockchip/producer/videotunnel/video_tunnel.h"

namespace android {

class VpBufferInfo{
public:
  VpBufferInfo(vt_buffer_t* vp_buffer, std::shared_ptr<DrmBuffer> drm_buffer)
    : pVpBuffer_(vp_buffer),
      mDrmBuffer_(drm_buffer),
      mReleaseFence_(ReleaseFence::NO_FENCE){
    mReleaseRefCnt_.clear();
  };

  ~VpBufferInfo(){};
  VpBufferInfo(const VpBufferInfo &rhs) = delete;
  VpBufferInfo &operator=(const VpBufferInfo &rhs) = delete;

  void SetVpBuffer(vt_buffer_t* vp_buffer){
    std::lock_guard<std::mutex> lock(mtx_);
    pVpBuffer_ = vp_buffer;
  }

  // Get VpBuffer
  vt_buffer_t* GetVpBuffer() const{
    std::lock_guard<std::mutex> lock(mtx_);
    return pVpBuffer_;
  }
  // Get VpBuffer
  std::shared_ptr<DrmBuffer> GetDrmBuffer() const{
    std::lock_guard<std::mutex> lock(mtx_);
    return mDrmBuffer_;
  }

  // Add ref cnt
  void AddReleaseRefCnt(int display_id){
    std::lock_guard<std::mutex> lock(mtx_);
    mReleaseRefCnt_.insert(display_id);
    if(mReleaseFence_ != NULL){
      HWC2_ALOGD_IF_INFO("Add refCnt display-id=%d Name=%s" ,display_id, mReleaseFence_->getName().c_str());
    }
  }

  // Get ReleaseFence
  void SetReleaseFence(sp<ReleaseFence> release_fence){
    std::lock_guard<std::mutex> lock(mtx_);
    mReleaseFence_ = release_fence;
  }

  // Get ReleaseFence
  sp<ReleaseFence> GetReleaseFence() const{
    std::lock_guard<std::mutex> lock(mtx_);
    return mReleaseFence_;
  }

  // Get ReleaseFence
  int SignalReleaseFence(int display_id) {
    std::lock_guard<std::mutex> lock(mtx_);
    mReleaseRefCnt_.erase(display_id);
    if(mReleaseFence_ != NULL){
      HWC2_ALOGD_IF_INFO("want to signal display_id=%d %s", display_id, mReleaseFence_->getName().c_str());
    }
    // 若ReleaseFence引用计数为0，则释放ReleaseFence
    if(mReleaseFence_ != NULL && mReleaseRefCnt_.size() == 0){
      int act = mReleaseFence_->getActiveCount();
      int sig = mReleaseFence_->getSignaledCount();
      mReleaseFence_->signal();
      HWC2_ALOGD_IF_INFO("Signal %s Name=%s Info: size=%d act=%d signal=%d err=%d SignalTime=%s" ,
                      act == 1 && sig == 0 && mReleaseFence_->getActiveCount() == 0 &&
                      mReleaseFence_->getSignaledCount() == 1 ? "Sucess" : "Fail",
                      mReleaseFence_->getName().c_str(),
                      mReleaseFence_->getSize(),
                      mReleaseFence_->getActiveCount(),
                      mReleaseFence_->getSignaledCount(),
                      mReleaseFence_->getErrorCount(),
                      mReleaseFence_->dump().c_str());
      mReleaseFence_ = NULL;
    }
    return 0;
  }


private:
  vt_buffer_t* pVpBuffer_;
  std::shared_ptr<DrmBuffer> mDrmBuffer_;
  sp<ReleaseFence> mReleaseFence_;
  std::set<int> mReleaseRefCnt_;
  mutable std::mutex mtx_;
};

// DrmVideoProducer Context
class VpContext{
public:
  VpContext(int tunnel_id);
  ~VpContext();

  // Get tunnel fd
  int GetTunnelId();
  // AddConnectionRef
  int AddConnRef(int display_id);
  // ReleaseConnRef
  int ReleaseConnRef(int display_id);
  // ConnectionCnt
  int ConnectionCnt();
  // Get Buffer cache
  std::shared_ptr<DrmBuffer> GetBufferCache(vt_buffer_t* buffer);
  // Get VpBuffer
  vt_buffer_t* GetVpBufferInfo(uint64_t buffer_id);
  // Release VpBuffer
  int ReleaseBufferInfo(uint64_t buffer_id);
  // Get Last Buffer Id
  uint64_t GetLastHandleBufferId();
  // Get Last Buffer cache
  std::shared_ptr<DrmBuffer> GetLastBufferCache(uint64_t buffer_id);
  // Add ReleaseFence
  int AddReleaseFence(uint64_t buffer_id);
  // Add ReleaseFenceRefCnt
  int AddReleaseFenceRefCnt(int display_id, uint64_t buffer_id);
  // Get ReleaseFence
  sp<ReleaseFence> GetReleaseFence(uint64_t buffer_id);
  // Signal ReleaseFence
  int SignalReleaseFence(int display_id, uint64_t buffer_id);
  // Record timestamp
  int SetTimeStamp(int64_t queue_time);
  // Get queue timestamp
  int64_t GetQueueTime();
  // Record timestamp
  int64_t GetAcquireTime();
  // Print timestamp
  int VpPrintTimestamp();

private:
  DrmGralloc* mDrmGralloc_;
  int iTunnelId_;
  uint64_t uFrameNo_;
  std::map<int, std::shared_ptr<VpBufferInfo>> mMapBuffer_;
  int64_t mQueueFrameTimestamp_;
  int64_t mAcquireFrameTimestamp_;
  int64_t mCommitFrameTimestamp_;
  uint64_t mLastHandleBufferId_;

  std::set<int> refDpyConnection_;

  SyncTimeline mTimeLine_;
  mutable std::mutex mtx_;
};
}; // namespace android


#endif // _VP_CONTEXT_H_