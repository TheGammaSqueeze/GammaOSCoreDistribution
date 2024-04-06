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

#ifndef RESOURCEMANAGER_H
#define RESOURCEMANAGER_H

#include "drmdevice.h"
#include "platform.h"
#include "rockchip/drmgralloc.h"
#include "rockchip/drmbaseparameter.h"
#include "drmdisplaycompositor.h"
#include "drmhwctwo.h"

#include "drmbufferqueue.h"

#include <im2d.hpp>

#include <string.h>
#include <set>
#include <map>
#include <mutex>
namespace android {
class DrmDisplayCompositor;
class DrmHwcTwo;

typedef std::pair<uint64_t, std::shared_ptr<DrmBuffer>> PAIR_ID_BUFFER;

typedef enum HwVirtualDisplayMode {
  HWC2_DISABLE_HW_VIRTUAL_DISPLAY = 0,
  HWC2_HW_VIRTUAL_DISPLAY_USE_VOP = 1,
  HWC2_HW_VIRTUAL_DISPLAY_USE_RGA = 2,
} HwVirtualDisplayMode_t;

class ResourceManager {
 public:
  static ResourceManager* getInstance(){
    static ResourceManager drmResourceManager_;
    return &drmResourceManager_;
  }

  int Init(DrmHwcTwo *hwc2);
  DrmDevice *GetDrmDevice(int display);
  std::shared_ptr<Importer> GetImporter(int display);
  DrmConnector *AvailableWritebackConnector(int display);
  int InitProperty();
  const std::vector<std::unique_ptr<DrmDevice>> &GetDrmDevices() const {
    return drms_;
  }

  DrmHwcTwo *GetHwc2() const {
    return hwc2_;
  }

  int getDisplayCount() const {
    return num_displays_;
  }

  std::map<int,int> getDisplays() const {
    return displays_;
  }

  uint32_t getActiveDisplayCnt() { return active_display_.size();}

  int getFb0Fd() { return fb0_fd;}
  int getSocId() { return soc_id_;}
  std::shared_ptr<DrmDisplayCompositor> GetDrmDisplayCompositor(DrmCrtc* crtc);

  // WriteBack interface.
  int GetWBDisplay() const;
  bool isWBMode() const;
  const DrmMode &GetWBMode() const;
  int EnableWriteBackMode(int display);
  HwVirtualDisplayMode_t ChooseWriteBackMode(int display);
  // Virtual Display Composition by gles.
  bool IsDisableHwVirtualDisplay();
  // WriteBack by vop
  bool IsWriteBackByVop();
  int WriteBackUseVop(int display);
  int UpdateWriteBackResolutionUseVop(int display);

  // WriteBack by rga
  bool IsWriteBackByRga();
  int WriteBackUseRga(int display);
  int DisableWriteBackMode(int display);
  int UpdateWriteBackResolution(int display);
  std::shared_ptr<DrmBuffer> GetResetWBBuffer();
  std::shared_ptr<DrmBuffer> GetNextWBBuffer();
  std::shared_ptr<DrmBuffer> GetDrawingWBBuffer();
  int GetFinishWBBufferSize();
  int OutputWBBuffer(int display_id,
                     rga_buffer_t &dst,
                     im_rect &src_rect,
                     int32_t *retire_fence,
                     uint64_t *last_frame_no);
  int SwapWBBuffer(uint64_t frame_no);
  // WriteBack interface.

  // 系统属性开关
  bool IsCompositionDropMode() const;
  bool IsDynamicDisplayMode() const;
  bool IsSidebandStream2Mode() const;
  int GetCacheBufferLimitSize() const;

 private:
  ResourceManager();
  ResourceManager(const ResourceManager &) = delete;
  ResourceManager &operator=(const ResourceManager &) = delete;
  int AddDrmDevice();

  int num_displays_;
  std::set<int> active_display_;
  std::vector<std::unique_ptr<DrmDevice>> drms_;
  std::vector<std::shared_ptr<Importer>> importers_;
  std::map<int, std::shared_ptr<DrmDisplayCompositor>> mapDrmDisplayCompositor_;
  std::map<int,int> displays_;
  DrmGralloc *drmGralloc_;
  DrmHwcTwo *hwc2_;
  int fb0_fd;
  int soc_id_;
  int drmVersion_;
  bool dynamic_assigin_enable_;

  // <-- WriteBack Mode info
  // WriteBack 引用计数，若每一个virtual display 请求 WriteBack，则引用计数+1
  int bEnableWriteBackRef_=0;
  // WriteBack 使能 display 的 id
  int iWriteBackDisplayId_=-1;
  // Hw Virtual Display 实现模式, 存在 gles / vop / rga 三种模式
  HwVirtualDisplayMode_t mVDMode_;
  // WriteBack 格式信息
  uint32_t iWBWidth_;
  uint32_t iWBHeight_;
  int iWBFormat_;
  // WriteBack分辨率
  DrmMode mWBMode_;
  // WriteBack BufferQueue 信息，用于实现环形缓冲区结构
  std::shared_ptr<DrmBufferQueue> mWriteBackBQ_;
  std::shared_ptr<DrmBuffer> mResetBackBuffer_;
  std::shared_ptr<DrmBuffer> mNextWriteBackBuffer_;
  std::shared_ptr<DrmBuffer> mDrawingWriteBackBuffer_;
  std::vector<PAIR_ID_BUFFER> mFinishBufferQueue_;
  // -->
  std::map<int, std::set<uint64_t>> mMapDisplayBufferSet_;

  // Composition丢帧模式
  bool mCompositionDropMode_;
  // 使能动态分辨率切换模式
  bool mDynamicDisplayMode_;
  // sideband 2.0
  bool mSidebandStream2Mode_;
  // cache buffer max size limit enable
  int mCacheBufferLimitSize_ = 0;

  mutable std::recursive_mutex mRecursiveMutex;
};
}  // namespace android

#endif  // RESOURCEMANAGER_H
