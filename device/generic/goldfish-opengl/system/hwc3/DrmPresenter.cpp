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

#include "DrmPresenter.h"

#include <cros_gralloc_handle.h>
#include <linux/netlink.h>
#include <sys/socket.h>

#include <chrono>
#include <thread>

using ::android::base::guest::AutoReadLock;
using ::android::base::guest::AutoWriteLock;
using ::android::base::guest::ReadWriteLock;

namespace aidl::android::hardware::graphics::composer3::impl {
namespace {

uint64_t addressAsUint(int* pointer) {
  return static_cast<uint64_t>(reinterpret_cast<uintptr_t>(pointer));
}

}  // namespace

DrmPresenter::~DrmPresenter() {
  if (mFd > 0) {
    drmDropMaster(mFd.get());
  }
}

HWC3::Error DrmPresenter::init() {
  DEBUG_LOG("%s", __FUNCTION__);

  mFd = ::android::base::unique_fd(open("/dev/dri/card0", O_RDWR | O_CLOEXEC));
  if (mFd < 0) {
    ALOGE("%s: failed to open drm device: %s", __FUNCTION__, strerror(errno));
    return HWC3::Error::NoResources;
  }

  int ret = drmSetClientCap(mFd.get(), DRM_CLIENT_CAP_UNIVERSAL_PLANES, 1);
  if (ret) {
    ALOGE("%s: failed to set cap universal plane %s\n", __FUNCTION__,
          strerror(errno));
    return HWC3::Error::NoResources;
  }

  ret = drmSetClientCap(mFd.get(), DRM_CLIENT_CAP_ATOMIC, 1);
  if (ret) {
    ALOGE("%s: failed to set cap atomic %s\n", __FUNCTION__, strerror(errno));
    return HWC3::Error::NoResources;
  }

  drmSetMaster(mFd.get());

  if (!drmIsMaster(mFd.get())) {
    ALOGE("%s: failed to get master drm device", __FUNCTION__);
    return HWC3::Error::NoResources;
  }

  {
    AutoWriteLock lock(mStateMutex);
    bool initDrmRet = initDrmElementsLocked();
    if (initDrmRet) {
      DEBUG_LOG("%s: Successfully initialized DRM backend", __FUNCTION__);
    } else {
      ALOGE("%s: Failed to initialize DRM backend", __FUNCTION__);
      return HWC3::Error::NoResources;
    }

    constexpr const std::size_t kCachedBuffersPerDisplay = 3;
    std::size_t numDisplays = 0;
    for (const DrmConnector& connector : mConnectors) {
      if (connector.connection == DRM_MODE_CONNECTED) {
        ++numDisplays;
      }
    }
    const std::size_t bufferCacheSize = kCachedBuffersPerDisplay * numDisplays;
    DEBUG_LOG("%s: initializing DRM buffer cache to size %zu",
              __FUNCTION__, bufferCacheSize);
    mBufferCache = std::make_unique<DrmBufferCache>(bufferCacheSize);
  }

  mDrmEventListener = ::android::sp<DrmEventListener>::make(*this);
  if (mDrmEventListener->init()) {
    DEBUG_LOG("%s: Successfully initialized DRM event listener", __FUNCTION__);
  } else {
    ALOGE("%s: Failed to initialize DRM event listener", __FUNCTION__);
  }
  mDrmEventListener->run("", ANDROID_PRIORITY_URGENT_DISPLAY);

  return HWC3::Error::None;
}

HWC3::Error DrmPresenter::getDisplayConfigs(std::vector<DisplayConfig>* configs) const {
  AutoReadLock lock(mStateMutex);

  configs->clear();

  for (uint32_t i = 0; i < mConnectors.size(); i++) {
    const auto& connector = mConnectors[i];

    if (connector.connection != DRM_MODE_CONNECTED) {
      continue;
    }

    configs->emplace_back(DisplayConfig{
        .id = i,
        .width = connector.mMode.hdisplay,
        .height = connector.mMode.vdisplay,
        .dpiX = 160, //static_cast<uint32_t>(connector.dpiX),
        .dpiY = 160, //static_cast<uint32_t>(connector.dpiY),
        .refreshRateHz = connector.mRefreshRateAsInteger,
    });
  }

  return HWC3::Error::None;
}

HWC3::Error DrmPresenter::registerOnHotplugCallback(const HotplugCallback& cb) {
  mHotplugCallback = cb;
  return HWC3::Error::None;
}

HWC3::Error DrmPresenter::unregisterOnHotplugCallback() {
  mHotplugCallback.reset();
  return HWC3::Error::None;
}

bool DrmPresenter::initDrmElementsLocked() {
  drmModeRes* res;
  static const int32_t kUmPerInch = 25400;

  res = drmModeGetResources(mFd.get());
  if (res == nullptr) {
    ALOGE("%s HWC3::Error reading drm resources: %d", __FUNCTION__, errno);
    mFd.reset();
    return false;
  }

  DEBUG_LOG(
      "drmModeRes count fbs %d crtc %d connector %d encoder %d min w %d max w "
      "%d min h %d max h %d",
      res->count_fbs, res->count_crtcs, res->count_connectors,
      res->count_encoders, res->min_width, res->max_width, res->min_height,
      res->max_height);

  for (uint32_t i = 0; i < res->count_crtcs; i++) {
    DrmCrtc crtc = {};

    drmModeCrtcPtr c = drmModeGetCrtc(mFd.get(), res->crtcs[i]);
    crtc.mId = c->crtc_id;

    drmModeObjectPropertiesPtr crtcProps =
        drmModeObjectGetProperties(mFd.get(), c->crtc_id, DRM_MODE_OBJECT_CRTC);

    for (uint32_t crtcPropsIndex = 0; crtcPropsIndex < crtcProps->count_props;
         crtcPropsIndex++) {
      drmModePropertyPtr crtcProp =
          drmModeGetProperty(mFd.get(), crtcProps->props[crtcPropsIndex]);

      if (!strcmp(crtcProp->name, "OUT_FENCE_PTR")) {
        crtc.mOutFencePtrPropertyId = crtcProp->prop_id;
      } else if (!strcmp(crtcProp->name, "ACTIVE")) {
        crtc.mActivePropertyId = crtcProp->prop_id;
      } else if (!strcmp(crtcProp->name, "MODE_ID")) {
        crtc.mModePropertyId = crtcProp->prop_id;
      }

      drmModeFreeProperty(crtcProp);
    }

    drmModeFreeObjectProperties(crtcProps);

    mCrtcs.push_back(crtc);
  }

  drmModePlaneResPtr planeRes = drmModeGetPlaneResources(mFd.get());
  for (uint32_t i = 0; i < planeRes->count_planes; ++i) {
    DrmPlane plane = {};

    drmModePlanePtr p = drmModeGetPlane(mFd.get(), planeRes->planes[i]);
    plane.mId = p->plane_id;

    DEBUG_LOG(
        "%s: plane id: %u crtcid %u fbid %u crtc xy %d %d xy %d %d "
        "possible ctrcs 0x%x",
        __FUNCTION__, p->plane_id, p->crtc_id, p->fb_id, p->crtc_x, p->crtc_y,
        p->x, p->y, p->possible_crtcs);

    drmModeObjectPropertiesPtr planeProps =
        drmModeObjectGetProperties(mFd.get(), plane.mId, DRM_MODE_OBJECT_PLANE);

    for (uint32_t planePropIndex = 0; planePropIndex < planeProps->count_props;
         ++planePropIndex) {
      drmModePropertyPtr planeProp =
          drmModeGetProperty(mFd.get(), planeProps->props[planePropIndex]);

      if (!strcmp(planeProp->name, "CRTC_ID")) {
        plane.mCrtcPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "IN_FENCE_FD")) {
        plane.mInFenceFdPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "FB_ID")) {
        plane.mFbPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "CRTC_X")) {
        plane.mCrtcXPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "CRTC_Y")) {
        plane.mCrtcYPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "CRTC_W")) {
        plane.mCrtcWPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "CRTC_H")) {
        plane.mCrtcHPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "SRC_X")) {
        plane.mSrcXPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "SRC_Y")) {
        plane.mSrcYPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "SRC_W")) {
        plane.mSrcWPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "SRC_H")) {
        plane.mSrcHPropertyId = planeProp->prop_id;
      } else if (!strcmp(planeProp->name, "type")) {
        plane.mTypePropertyId = planeProp->prop_id;
        uint64_t type = planeProp->values[0];
        switch (type) {
          case DRM_PLANE_TYPE_OVERLAY:
            plane.mType = type;
            DEBUG_LOG("%s: plane %" PRIu32 " is DRM_PLANE_TYPE_OVERLAY",
                      __FUNCTION__, plane.mId);
            break;
          case DRM_PLANE_TYPE_PRIMARY:
            plane.mType = type;
            DEBUG_LOG("%s: plane %" PRIu32 " is DRM_PLANE_TYPE_PRIMARY",
                      __FUNCTION__, plane.mId);
            break;
          default:
            break;
        }
      }

      drmModeFreeProperty(planeProp);
    }

    drmModeFreeObjectProperties(planeProps);

    bool isPrimaryOrOverlay = plane.mType == DRM_PLANE_TYPE_OVERLAY ||
                              plane.mType == DRM_PLANE_TYPE_PRIMARY;
    if (isPrimaryOrOverlay) {
      for (uint32_t j = 0; j < mCrtcs.size(); j++) {
        if ((0x1 << j) & p->possible_crtcs) {
          DEBUG_LOG("%s: plane %" PRIu32 " compatible with crtc mask %" PRIu32,
                    __FUNCTION__, plane.mId, p->possible_crtcs);
          if (mCrtcs[j].mPlaneId == -1) {
            mCrtcs[j].mPlaneId = plane.mId;
            DEBUG_LOG("%s: plane %" PRIu32 " associated with crtc %" PRIu32,
                      __FUNCTION__, plane.mId, j);
            break;
          }
        }
      }
    }

    drmModeFreePlane(p);
    mPlanes[plane.mId] = plane;
  }
  drmModeFreePlaneResources(planeRes);

  for (uint32_t i = 0; i < res->count_connectors; ++i) {
    DrmConnector connector = {};
    connector.mId = res->connectors[i];

    {
      drmModeObjectPropertiesPtr connectorProps = drmModeObjectGetProperties(
          mFd.get(), connector.mId, DRM_MODE_OBJECT_CONNECTOR);

      for (uint32_t connectorPropIndex = 0;
           connectorPropIndex < connectorProps->count_props;
           ++connectorPropIndex) {
        drmModePropertyPtr connectorProp = drmModeGetProperty(
            mFd.get(), connectorProps->props[connectorPropIndex]);
        if (!strcmp(connectorProp->name, "CRTC_ID")) {
          connector.mCrtcPropertyId = connectorProp->prop_id;
        } else if (!strcmp(connectorProp->name, "EDID")) {
          connector.mEdidBlobId =
              connectorProps->prop_values[connectorPropIndex];
        }
        drmModeFreeProperty(connectorProp);
      }

      drmModeFreeObjectProperties(connectorProps);
    }
    {
      drmModeConnector* c = drmModeGetConnector(mFd.get(), connector.mId);
      if (c == nullptr) {
        ALOGE("%s: Failed to get connector %" PRIu32 ": %d", __FUNCTION__,
              connector.mId, errno);
        return false;
      }
      connector.connection = c->connection;
      if (c->count_modes > 0) {
        memcpy(&connector.mMode, &c->modes[0], sizeof(drmModeModeInfo));
        drmModeCreatePropertyBlob(mFd.get(), &connector.mMode,
                                  sizeof(connector.mMode),
                                  &connector.mModeBlobId);

        // Dots per 1000 inches
        connector.dpiX =
            c->mmWidth ? (c->modes[0].hdisplay * kUmPerInch) / (c->mmWidth)
                       : -1;
        // Dots per 1000 inches
        connector.dpiY =
            c->mmHeight ? (c->modes[0].vdisplay * kUmPerInch) / (c->mmHeight)
                        : -1;
      }
      DEBUG_LOG("%s connector %" PRIu32 " dpiX %" PRIi32 " dpiY %" PRIi32
                " connection %d",
                __FUNCTION__, connector.mId, connector.dpiX, connector.dpiY,
                connector.connection);

      drmModeFreeConnector(c);

      connector.mRefreshRateAsFloat =
          1000.0f * connector.mMode.clock /
          ((float)connector.mMode.vtotal * (float)connector.mMode.htotal);
      connector.mRefreshRateAsInteger =
          (uint32_t)(connector.mRefreshRateAsFloat + 0.5f);
    }

    mConnectors.push_back(connector);
  }

  drmModeFreeResources(res);
  return true;
}

void DrmPresenter::resetDrmElementsLocked() {
  for (auto& c : mConnectors) {
    if (c.mModeBlobId) {
      if (drmModeDestroyPropertyBlob(mFd.get(), c.mModeBlobId)) {
        ALOGE("%s: Error destroy PropertyBlob %" PRIu32, __func__,
              c.mModeBlobId);
      }
    }
  }
  mConnectors.clear();
  mCrtcs.clear();
  mPlanes.clear();
}

std::tuple<HWC3::Error, std::shared_ptr<DrmBuffer>> DrmPresenter::create(
    const native_handle_t* handle) {
  cros_gralloc_handle* crosHandle = (cros_gralloc_handle*)handle;
  if (crosHandle == nullptr) {
    ALOGE("%s: invalid cros_gralloc_handle", __FUNCTION__);
    return std::make_tuple(HWC3::Error::NoResources, nullptr);
  }

  DrmPrimeBufferHandle primeHandle = 0;
  int ret = drmPrimeFDToHandle(mFd.get(), crosHandle->fds[0], &primeHandle);
  if (ret) {
    ALOGE("%s: drmPrimeFDToHandle failed: %s (errno %d)", __FUNCTION__,
          strerror(errno), errno);
    return std::make_tuple(HWC3::Error::NoResources, nullptr);
  }

  auto drmBufferPtr = mBufferCache->get(primeHandle);
  if (drmBufferPtr != nullptr) {
    return std::make_tuple(HWC3::Error::None,
                           std::shared_ptr<DrmBuffer>(*drmBufferPtr));
  }

  auto buffer = std::shared_ptr<DrmBuffer>(new DrmBuffer(*this));
  buffer->mWidth = crosHandle->width;
  buffer->mHeight = crosHandle->height;
  buffer->mDrmFormat = crosHandle->format;
  buffer->mPlaneFds[0] = crosHandle->fds[0];
  buffer->mPlaneHandles[0] = primeHandle;
  buffer->mPlanePitches[0] = crosHandle->strides[0];
  buffer->mPlaneOffsets[0] = crosHandle->offsets[0];

  uint32_t framebuffer = 0;
  ret = drmModeAddFB2(mFd.get(),
                      buffer->mWidth,
                      buffer->mHeight,
                      buffer->mDrmFormat,
                      buffer->mPlaneHandles,
                      buffer->mPlanePitches,
                      buffer->mPlaneOffsets,
                      &framebuffer,
                      0);
  if (ret) {
    ALOGE("%s: drmModeAddFB2 failed: %s (errno %d)", __FUNCTION__,
          strerror(errno), errno);
    return std::make_tuple(HWC3::Error::NoResources, nullptr);
  }
  DEBUG_LOG("%s: created framebuffer:%" PRIu32, __FUNCTION__, framebuffer);
  buffer->mDrmFramebuffer = framebuffer;

  mBufferCache->set(primeHandle, std::shared_ptr<DrmBuffer>(buffer));

  return std::make_tuple(HWC3::Error::None,
                         std::shared_ptr<DrmBuffer>(buffer));
}

HWC3::Error DrmPresenter::destroyDrmFramebuffer(DrmBuffer* buffer) {
  if (buffer->mDrmFramebuffer) {
    uint32_t framebuffer = *buffer->mDrmFramebuffer;
    if (drmModeRmFB(mFd.get(), framebuffer)) {
      ALOGE("%s: drmModeRmFB failed: %s (errno %d)", __FUNCTION__,
            strerror(errno), errno);
      return HWC3::Error::NoResources;
    }
    DEBUG_LOG("%s: destroyed framebuffer:%" PRIu32, __FUNCTION__, framebuffer);
    buffer->mDrmFramebuffer.reset();
  }
  if (buffer->mPlaneHandles[0]) {
    struct drm_gem_close gem_close = {};
    gem_close.handle = buffer->mPlaneHandles[0];
    if (drmIoctl(mFd.get(), DRM_IOCTL_GEM_CLOSE, &gem_close)) {
      ALOGE("%s: DRM_IOCTL_GEM_CLOSE failed: %s (errno %d)", __FUNCTION__,
            strerror(errno), errno);
      return HWC3::Error::NoResources;
    }

    mBufferCache->remove(buffer->mPlaneHandles[0]);
  }

  return HWC3::Error::None;
}

bool DrmPresenter::handleHotplug() {
  std::vector<DrmConnector> oldConnectors(mConnectors);
  {
    AutoReadLock lock(mStateMutex);
    oldConnectors.assign(mConnectors.begin(), mConnectors.end());
  }
  {
    AutoWriteLock lock(mStateMutex);
    resetDrmElementsLocked();
    if (!initDrmElementsLocked()) {
      ALOGE(
          "%s: failed to initialize drm elements during hotplug. Displays may "
          "not function correctly!",
          __FUNCTION__);
      return false;
    }
  }

  AutoReadLock lock(mStateMutex);
  for (int i = 0; i < mConnectors.size(); i++) {
    bool changed =
        oldConnectors[i].dpiX != mConnectors[i].dpiX ||
        oldConnectors[i].dpiY != mConnectors[i].dpiY ||
        oldConnectors[i].connection != mConnectors[i].connection ||
        oldConnectors[i].mMode.hdisplay != mConnectors[i].mMode.hdisplay ||
        oldConnectors[i].mMode.vdisplay != mConnectors[i].mMode.vdisplay;
    if (changed) {
      if (i == 0) {
        ALOGE(
            "%s: Ignoring changes to display:0 which is not configurable by "
            "multi-display interface.",
            __FUNCTION__);
        continue;
      }

      const bool connected =
          mConnectors[i].connection == DRM_MODE_CONNECTED ? true : false;
      if (mHotplugCallback) {
        (*mHotplugCallback)(connected,                      //
                            i,                              //
                            mConnectors[i].mMode.hdisplay,  //
                            mConnectors[i].mMode.vdisplay,  //
                            mConnectors[i].dpiX,            //
                            mConnectors[i].dpiY,            //
                            mConnectors[i].mRefreshRateAsInteger);
      }
    }
  }
  return true;
}

std::tuple<HWC3::Error, ::android::base::unique_fd>
DrmPresenter::flushToDisplay(int display, const DrmBuffer& buffer,
                             ::android::base::borrowed_fd inSyncFd) {
  ATRACE_CALL();

  if (!buffer.mDrmFramebuffer) {
    ALOGE("%s: failed, no framebuffer created.", __FUNCTION__);
    return std::make_tuple(HWC3::Error::NoResources,
                           ::android::base::unique_fd());
  }

  AutoReadLock lock(mStateMutex);

  DrmConnector& connector = mConnectors[display];
  DrmCrtc& crtc = mCrtcs[display];

  HWC3::Error error = HWC3::Error::None;

  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();

  int ret;

  if (!crtc.mDidSetCrtc) {
    DEBUG_LOG("%s: Setting crtc.\n", __FUNCTION__);
    ret = drmModeAtomicAddProperty(pset, crtc.mId, crtc.mActivePropertyId, 1);
    if (ret < 0) {
      ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
    }
    ret = drmModeAtomicAddProperty(pset, crtc.mId, crtc.mModePropertyId,
                                   connector.mModeBlobId);
    if (ret < 0) {
      ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
    }
    ret = drmModeAtomicAddProperty(pset, connector.mId,
                                   connector.mCrtcPropertyId, crtc.mId);
    if (ret < 0) {
      ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
    }

    crtc.mDidSetCrtc = true;
  } else {
    DEBUG_LOG("%s: Already set crtc\n", __FUNCTION__);
  }

  int flushFenceFd = -1;
  ret = drmModeAtomicAddProperty(pset, crtc.mId, crtc.mOutFencePtrPropertyId,
                                 addressAsUint(&flushFenceFd));
  if (ret < 0) {
    ALOGE("%s:%d: set OUT_FENCE_PTR failed %d errno %d\n", __FUNCTION__,
          __LINE__, ret, errno);
  }

  if (crtc.mPlaneId == -1) {
    ALOGE("%s:%d: no plane available for crtc id %" PRIu32, __FUNCTION__,
          __LINE__, crtc.mId);
    return std::make_tuple(HWC3::Error::NoResources,
                           ::android::base::unique_fd());
  }

  DrmPlane& plane = mPlanes[crtc.mPlaneId];

  DEBUG_LOG("%s: set plane: plane id %d crtc id %d fbid %d bo w h %d %d\n",
            __FUNCTION__, plane.mId, crtc.mId, *buffer.mDrmFramebuffer,
            buffer.mWidth, buffer.mHeight);

  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mCrtcPropertyId,
                                 crtc.mId);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mInFenceFdPropertyId,
                                 inSyncFd.get());
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mFbPropertyId,
                                 *buffer.mDrmFramebuffer);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mCrtcXPropertyId, 0);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mCrtcYPropertyId, 0);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mCrtcWPropertyId,
                                 buffer.mWidth);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mCrtcHPropertyId,
                                 buffer.mHeight);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mSrcXPropertyId, 0);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mSrcYPropertyId, 0);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mSrcWPropertyId,
                                 buffer.mWidth << 16);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }
  ret = drmModeAtomicAddProperty(pset, plane.mId, plane.mSrcHPropertyId,
                                 buffer.mHeight << 16);
  if (ret < 0) {
    ALOGE("%s:%d: failed: %s\n", __FUNCTION__, __LINE__, strerror(errno));
  }

  constexpr const uint32_t kCommitFlags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  ret = drmModeAtomicCommit(mFd.get(), pset, kCommitFlags, 0);

  if (ret) {
    ALOGE("%s:%d: atomic commit failed: %s\n", __FUNCTION__, __LINE__,
          strerror(errno));
    error = HWC3::Error::NoResources;
    flushFenceFd = -1;
  }

  if (pset) {
    drmModeAtomicFree(pset);
  }

  DEBUG_LOG("%s: flush fence:%d\n", __FUNCTION__, flushFenceFd);
  return std::make_tuple(error, ::android::base::unique_fd(flushFenceFd));
}

std::optional<std::vector<uint8_t>> DrmPresenter::getEdid(uint32_t id) {
  AutoReadLock lock(mStateMutex);

  if (mConnectors[id].mEdidBlobId == -1) {
    DEBUG_LOG("%s: EDID not supported", __func__);
    return std::nullopt;
  }
  drmModePropertyBlobPtr blob =
      drmModeGetPropertyBlob(mFd.get(), mConnectors[id].mEdidBlobId);
  if (!blob) {
    ALOGE("%s: fail to read EDID from DRM", __func__);
    return std::nullopt;
  }

  std::vector<uint8_t> edid;
  uint8_t* start = static_cast<uint8_t*>(blob->data);
  edid.insert(edid.begin(), start, start + blob->length);

  drmModeFreePropertyBlob(blob);

  return edid;
}

DrmBuffer::DrmBuffer(DrmPresenter& DrmPresenter)
    : mDrmPresenter(DrmPresenter) {}

DrmBuffer::~DrmBuffer() { mDrmPresenter.destroyDrmFramebuffer(this); }

DrmPresenter::DrmEventListener::DrmEventListener(DrmPresenter& presenter)
    : mPresenter(presenter) {}

DrmPresenter::DrmEventListener::~DrmEventListener() {}

bool DrmPresenter::DrmEventListener::init() {
  mEventFd = ::android::base::unique_fd(
      socket(PF_NETLINK, SOCK_DGRAM, NETLINK_KOBJECT_UEVENT));
  if (!mEventFd.ok()) {
    ALOGE("Failed to open uevent socket: %s", strerror(errno));
    return false;
  }
  struct sockaddr_nl addr;
  memset(&addr, 0, sizeof(addr));
  addr.nl_family = AF_NETLINK;
  addr.nl_pid = 0;
  addr.nl_groups = 0xFFFFFFFF;

  int ret = bind(mEventFd, (struct sockaddr*)&addr, sizeof(addr));
  if (ret) {
    ALOGE("Failed to bind uevent socket: %s", strerror(errno));
    return false;
  }

  FD_ZERO(&mMonitoredFds);
  FD_SET(mPresenter.mFd.get(), &mMonitoredFds);
  FD_SET(mEventFd.get(), &mMonitoredFds);
  mMaxFd = std::max(mPresenter.mFd.get(), mEventFd.get());

  return true;
}

bool DrmPresenter::DrmEventListener::threadLoop() {
  int ret;
  do {
    ret = select(mMaxFd + 1, &mMonitoredFds, NULL, NULL, NULL);
  } while (ret == -1 && errno == EINTR);

  // if (FD_ISSET(mPresenter.mFd, &mFds)) {
  //   TODO: handle drm related events
  // }

  if (FD_ISSET(mEventFd.get(), &mMonitoredFds)) {
    eventThreadLoop();
  }
  return true;
}

void DrmPresenter::DrmEventListener::eventThreadLoop() {
  char buffer[1024];
  int ret;

  struct timespec ts;
  uint64_t timestamp = 0;
  ret = clock_gettime(CLOCK_MONOTONIC, &ts);
  if (!ret) {
    timestamp = ts.tv_sec * 1000 * 1000 * 1000 + ts.tv_nsec;
  } else {
    ALOGE("Failed to get monotonic clock on hotplug %d", ret);
  }

  while (true) {
    ret = read(mEventFd.get(), &buffer, sizeof(buffer));
    if (ret == 0) {
      return;
    } else if (ret < 0) {
      ALOGE("Got error reading uevent %d", ret);
      return;
    }

    bool drmEvent = false, hotplugEvent = false;
    for (int i = 0; i < ret;) {
      char* event = buffer + i;
      if (strcmp(event, "DEVTYPE=drm_minor")) {
        drmEvent = true;
      } else if (strcmp(event, "HOTPLUG=1")) {
        hotplugEvent = true;
      }

      i += strlen(event) + 1;
    }

    if (drmEvent && hotplugEvent) {
      processHotplug(timestamp);
    }
  }
}

void DrmPresenter::DrmEventListener::processHotplug(uint64_t timestamp) {
  ALOGD("DrmEventListener detected hotplug event %" PRIu64, timestamp);
  mPresenter.handleHotplug();
}

}  // namespace aidl::android::hardware::graphics::composer3::impl
