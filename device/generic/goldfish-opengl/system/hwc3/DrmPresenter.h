/*
 * Copyright 2022 The Android Open Source Project
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

#ifndef ANDROID_HWC_DRMPRESENTER_H
#define ANDROID_HWC_DRMPRESENTER_H

#include <android-base/unique_fd.h>
#include <cutils/native_handle.h>
#include <utils/Thread.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <map>
#include <memory>
#include <tuple>
#include <vector>

#include "Common.h"
#include "LruCache.h"
#include "android/base/synchronization/AndroidLock.h"

namespace aidl::android::hardware::graphics::composer3::impl {

class DrmBuffer;
class DrmPresenter;

// A RAII object that will clear a drm framebuffer upon destruction.
class DrmBuffer {
 public:
  ~DrmBuffer();

  DrmBuffer(const DrmBuffer&) = delete;
  DrmBuffer& operator=(const DrmBuffer&) = delete;

  DrmBuffer(DrmBuffer&&) = delete;
  DrmBuffer& operator=(DrmBuffer&&) = delete;

 private:
  friend class DrmPresenter;
  DrmBuffer(DrmPresenter& drmPresenter);

  DrmPresenter& mDrmPresenter;

  uint32_t mWidth = 0;
  uint32_t mHeight = 0;
  uint32_t mDrmFormat = 0;
  uint32_t mPlaneFds[4] = {0, 0, 0, 0};
  uint32_t mPlaneHandles[4] = {0, 0, 0, 0};
  uint32_t mPlanePitches[4] = {0, 0, 0, 0};
  uint32_t mPlaneOffsets[4] = {0, 0, 0, 0};
  std::optional<uint32_t> mDrmFramebuffer;
};

class DrmPresenter {
 public:
  DrmPresenter() = default;
  ~DrmPresenter();

  DrmPresenter(const DrmPresenter&) = delete;
  DrmPresenter& operator=(const DrmPresenter&) = delete;

  DrmPresenter(DrmPresenter&&) = delete;
  DrmPresenter& operator=(DrmPresenter&&) = delete;

  HWC3::Error init();

  struct DisplayConfig {
    uint32_t id;
    uint32_t width;
    uint32_t height;
    uint32_t dpiX;
    uint32_t dpiY;
    uint32_t refreshRateHz;
  };

  HWC3::Error getDisplayConfigs(std::vector<DisplayConfig>* configs) const;

  using HotplugCallback = std::function<void(bool /*connected*/,   //
                                             uint32_t /*id*/,      //
                                             uint32_t /*width*/,   //
                                             uint32_t /*height*/,  //
                                             uint32_t /*dpiX*/,    //
                                             uint32_t /*dpiY*/,    //
                                             uint32_t /*refreshRate*/)>;

  HWC3::Error registerOnHotplugCallback(const HotplugCallback& cb);
  HWC3::Error unregisterOnHotplugCallback();

  uint32_t refreshRate() const { return mConnectors[0].mRefreshRateAsInteger; }

  std::tuple<HWC3::Error, std::shared_ptr<DrmBuffer>> create(
      const native_handle_t* handle);

  std::tuple<HWC3::Error, ::android::base::unique_fd> flushToDisplay(
      int display, const DrmBuffer& buffer,
      ::android::base::borrowed_fd inWaitSyncFd);

  std::optional<std::vector<uint8_t>> getEdid(uint32_t id);

 private:
  // TODO: make this cache per display when enabling hotplug support.
  using DrmPrimeBufferHandle = uint32_t;
  using DrmBufferCache = LruCache<DrmPrimeBufferHandle, std::shared_ptr<DrmBuffer>>;
  std::unique_ptr<DrmBufferCache> mBufferCache;

  // Grant visibility to destroyDrmFramebuffer to DrmBuffer.
  friend class DrmBuffer;
  HWC3::Error destroyDrmFramebuffer(DrmBuffer* buffer);

  // Grant visibility for handleHotplug to DrmEventListener.
  bool handleHotplug();

  bool initDrmElementsLocked();
  void resetDrmElementsLocked();

  // Drm device.
  ::android::base::unique_fd mFd;

  std::optional<HotplugCallback> mHotplugCallback;

  // Protects access to the below drm structs.
  mutable ::android::base::guest::ReadWriteLock mStateMutex;

  struct DrmPlane {
    uint32_t mId = -1;
    uint32_t mCrtcPropertyId = -1;
    uint32_t mInFenceFdPropertyId = -1;
    uint32_t mFbPropertyId = -1;
    uint32_t mCrtcXPropertyId = -1;
    uint32_t mCrtcYPropertyId = -1;
    uint32_t mCrtcWPropertyId = -1;
    uint32_t mCrtcHPropertyId = -1;
    uint32_t mSrcXPropertyId = -1;
    uint32_t mSrcYPropertyId = -1;
    uint32_t mSrcWPropertyId = -1;
    uint32_t mSrcHPropertyId = -1;
    uint32_t mTypePropertyId = -1;
    uint64_t mType = -1;
  };
  std::map<uint32_t, DrmPlane> mPlanes;

  struct DrmCrtc {
    uint32_t mId = -1;
    uint32_t mActivePropertyId = -1;
    uint32_t mModePropertyId = -1;
    uint32_t mOutFencePtrPropertyId = -1;
    uint32_t mPlaneId = -1;

    bool mDidSetCrtc = false;
  };
  std::vector<DrmCrtc> mCrtcs;

  struct DrmConnector {
    uint32_t mId = -1;
    uint32_t mCrtcPropertyId = -1;
    drmModeModeInfo mMode;
    int32_t dpiX;
    int32_t dpiY;
    drmModeConnection connection;
    uint32_t mModeBlobId = 0;
    float mRefreshRateAsFloat;
    uint32_t mRefreshRateAsInteger;
    uint64_t mEdidBlobId = -1;
  };
  std::vector<DrmConnector> mConnectors;

  class DrmEventListener : public ::android::Thread {
   public:
    DrmEventListener(DrmPresenter& presenter);
    virtual ~DrmEventListener();

    bool init();

   private:
    bool threadLoop() final;
    void eventThreadLoop();
    void processHotplug(uint64_t timestamp);

    DrmPresenter& mPresenter;
    ::android::base::unique_fd mEventFd;
    int mMaxFd;
    fd_set mMonitoredFds;
  };
  ::android::sp<DrmEventListener> mDrmEventListener;
};

}  // namespace aidl::android::hardware::graphics::composer3::impl

#endif
