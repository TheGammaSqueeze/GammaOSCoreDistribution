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
#ifndef _DRM_VIDEO_PRODUCER_H_
#define _DRM_VIDEO_PRODUCER_H_

#include <mutex>
#include <queue>

#include "utils/drmfence.h"
#include "drmbuffer.h"
#include "rockchip/producer/videotunnel/video_tunnel.h"
#include "rockchip/producer/vpcontext.h"
namespace android {
class DrmVideoProducer{
public:
  static DrmVideoProducer* getInstance(){
    static DrmVideoProducer drmVideoProducer;
    return &drmVideoProducer;
  };

  // Init video tunel.
  int Init();
  // Is invalid.
  bool IsValid();
  // Create tunnel connection.
  int CreateConnection(int display_id, int tunnel_id);
  // Destory Connection
  int DestoryConnection(int display_id, int tunnel_id);
  // Get Last video buffer
  std::shared_ptr<DrmBuffer> AcquireBuffer(int display_id,
                                           int tunnel_id,
                                           vt_rect_t *dis_rect,
                                           int timeout_ms);
  // Release video buffer
  int ReleaseBuffer(int display_id, int tunnel_id, uint64_t buffer_id);
  // Signal buffer's ReleaseFence
  int SignalReleaseFence(int display_id, int tunnel_id, uint64_t buffer_id);

 private:
  DrmVideoProducer();
  ~DrmVideoProducer();
  DrmVideoProducer(const DrmVideoProducer &) = delete;
  DrmVideoProducer &operator=(const DrmVideoProducer &) = delete;
  int InitLibHandle();
  bool bInit_;
  int iTunnelFd_;
  std::map<int, std::shared_ptr<VpContext>> mMapCtx_;
  mutable std::mutex mtx_;
};

}; // namespace android


#endif // _VIDEO_PRODUCER_H_