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

#include "HostFrameComposer.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <hardware/hwcomposer2.h>
#include <poll.h>
#include <sync/sync.h>
#include <ui/GraphicBuffer.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>

#include <optional>
#include <tuple>

#include "../egl/goldfish_sync.h"
#include "Display.h"
#include "HostUtils.h"
#include "virtgpu_drm.h"

namespace aidl::android::hardware::graphics::composer3::impl {
namespace {

hwc_rect AsHwcRect(const common::Rect& rect) {
  hwc_rect out;
  out.left = rect.left;
  out.top = rect.top;
  out.right = rect.right;
  out.bottom = rect.bottom;
  return out;
}

hwc_frect AsHwcFrect(const common::FRect& rect) {
  hwc_frect out;
  out.left = rect.left;
  out.top = rect.top;
  out.right = rect.right;
  out.bottom = rect.bottom;
  return out;
}

hwc_color AsHwcColor(const Color& color) {
  hwc_color out;
  out.r = color.r;
  out.g = color.g;
  out.b = color.b;
  out.a = color.a;
  return out;
}

hwc_transform_t AsHwcTransform(const common::Transform& transform) {
  switch (transform) {
    case common::Transform::NONE:
      return static_cast<hwc_transform_t>(0);
    case common::Transform::FLIP_H:
      return HWC_TRANSFORM_FLIP_H;
    case common::Transform::FLIP_V:
      return HWC_TRANSFORM_FLIP_V;
    case common::Transform::ROT_90:
      return HWC_TRANSFORM_ROT_90;
    case common::Transform::ROT_180:
      return HWC_TRANSFORM_ROT_180;
    case common::Transform::ROT_270:
      return HWC_TRANSFORM_ROT_270;
  }
}

static bool isMinigbmFromProperty() {
  static constexpr const auto kGrallocProp = "ro.hardware.gralloc";

  const auto grallocProp = ::android::base::GetProperty(kGrallocProp, "");
  DEBUG_LOG("%s: prop value is: %s", __FUNCTION__, grallocProp.c_str());

  if (grallocProp == "minigbm") {
    DEBUG_LOG("%s: Using minigbm, in minigbm mode.\n", __FUNCTION__);
    return true;
  } else {
    DEBUG_LOG("%s: Is not using minigbm, in goldfish mode.\n", __FUNCTION__);
    return false;
  }
}

static bool useAngleFromProperty() {
  static constexpr const auto kEglProp = "ro.hardware.egl";

  const auto eglProp = ::android::base::GetProperty(kEglProp, "");
  DEBUG_LOG("%s: prop value is: %s", __FUNCTION__, eglProp.c_str());

  if (eglProp == "angle") {
    DEBUG_LOG("%s: Using ANGLE.\n", __FUNCTION__);
    return true;
  } else {
    DEBUG_LOG("%s: Not using ANGLE.\n", __FUNCTION__);
    return false;
  }
}

typedef struct compose_layer {
  uint32_t cbHandle;
  hwc2_composition_t composeMode;
  hwc_rect_t displayFrame;
  hwc_frect_t crop;
  int32_t blendMode;
  float alpha;
  hwc_color_t color;
  hwc_transform_t transform;
} ComposeLayer;

typedef struct compose_device {
  uint32_t version;
  uint32_t targetHandle;
  uint32_t numLayers;
  struct compose_layer layer[0];
} ComposeDevice;

typedef struct compose_device_v2 {
  uint32_t version;
  uint32_t displayId;
  uint32_t targetHandle;
  uint32_t numLayers;
  struct compose_layer layer[0];
} ComposeDevice_v2;

class ComposeMsg {
 public:
  ComposeMsg(uint32_t layerCnt = 0)
      : mData(sizeof(ComposeDevice) + layerCnt * sizeof(ComposeLayer)) {
    mComposeDevice = reinterpret_cast<ComposeDevice*>(mData.data());
    mLayerCnt = layerCnt;
  }

  ComposeDevice* get() { return mComposeDevice; }

  uint32_t getLayerCnt() { return mLayerCnt; }

 private:
  std::vector<uint8_t> mData;
  uint32_t mLayerCnt;
  ComposeDevice* mComposeDevice;
};

class ComposeMsg_v2 {
 public:
  ComposeMsg_v2(uint32_t layerCnt = 0)
      : mData(sizeof(ComposeDevice_v2) + layerCnt * sizeof(ComposeLayer)) {
    mComposeDevice = reinterpret_cast<ComposeDevice_v2*>(mData.data());
    mLayerCnt = layerCnt;
  }

  ComposeDevice_v2* get() { return mComposeDevice; }

  uint32_t getLayerCnt() { return mLayerCnt; }

 private:
  std::vector<uint8_t> mData;
  uint32_t mLayerCnt;
  ComposeDevice_v2* mComposeDevice;
};

const native_handle_t* AllocateDisplayColorBuffer(int width, int height) {
  const uint32_t layerCount = 1;
  const uint64_t graphicBufferId = 0;  // not used
  buffer_handle_t handle;
  uint32_t stride;

  if (::android::GraphicBufferAllocator::get().allocate(
          width, height, ::android::PIXEL_FORMAT_RGBA_8888, layerCount,
          ::android::GraphicBuffer::USAGE_HW_COMPOSER |
              ::android::GraphicBuffer::USAGE_HW_RENDER,
          &handle, &stride, graphicBufferId, "EmuHWC2") == ::android::OK) {
    return static_cast<const native_handle_t*>(handle);
  } else {
    return nullptr;
  }
}

void FreeDisplayColorBuffer(const native_handle_t* h) {
  ::android::GraphicBufferAllocator::get().free(h);
}

}  // namespace

HWC3::Error HostFrameComposer::init() {
  mIsMinigbm = isMinigbmFromProperty();
  mUseAngle = useAngleFromProperty();

  if (mIsMinigbm) {
    mDrmPresenter.emplace();

    HWC3::Error error = mDrmPresenter->init();
    if (error != HWC3::Error::None) {
      ALOGE("%s: failed to initialize DrmPresenter", __FUNCTION__);
      return error;
    }
  } else {
    mSyncDeviceFd = goldfish_sync_open();
  }

  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::registerOnHotplugCallback(
    const HotplugCallback& cb) {
  if (mDrmPresenter) {
    mDrmPresenter->registerOnHotplugCallback(cb);
  }
  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::unregisterOnHotplugCallback() {
  if (mDrmPresenter) {
    mDrmPresenter->unregisterOnHotplugCallback();
  }
  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::createHostComposerDisplayInfo(
    Display* display, uint32_t hostDisplayId) {
  HWC3::Error error = HWC3::Error::None;

  int64_t displayId = display->getId();
  int32_t displayConfigId;
  int32_t displayWidth;
  int32_t displayHeight;

  error = display->getActiveConfig(&displayConfigId);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " has no active config", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttribute(displayConfigId, DisplayAttribute::WIDTH,
                                       &displayWidth);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get width", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttribute(
      displayConfigId, DisplayAttribute::HEIGHT, &displayHeight);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get height", __FUNCTION__,
          displayId);
    return error;
  }

  HostComposerDisplayInfo& displayInfo = mDisplayInfos[displayId];

  displayInfo.hostDisplayId = hostDisplayId;

  if (displayInfo.compositionResultBuffer) {
    FreeDisplayColorBuffer(displayInfo.compositionResultBuffer);
  }
  displayInfo.compositionResultBuffer =
      AllocateDisplayColorBuffer(displayWidth, displayHeight);
  if (displayInfo.compositionResultBuffer == nullptr) {
    ALOGE("%s: display:%" PRIu64 " failed to create target buffer",
          __FUNCTION__, displayId);
    return HWC3::Error::NoResources;
  }

  if (mDrmPresenter) {
    auto [drmBufferCreateError, drmBuffer] =
        mDrmPresenter->create(displayInfo.compositionResultBuffer);
    if (drmBufferCreateError != HWC3::Error::None) {
      ALOGE("%s: display:%" PRIu64 " failed to create target drm buffer",
            __FUNCTION__, displayId);
      return HWC3::Error::NoResources;
    }
    displayInfo.compositionResultDrmBuffer = std::move(drmBuffer);
  }

  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::onDisplayCreate(Display* display) {
  HWC3::Error error = HWC3::Error::None;

  int64_t displayId = display->getId();
  int32_t displayConfigId;
  int32_t displayWidth;
  int32_t displayHeight;
  int32_t displayDpiX;

  error = display->getActiveConfig(&displayConfigId);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " has no active config", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttribute(displayConfigId, DisplayAttribute::WIDTH,
                                       &displayWidth);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get width", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttribute(
      displayConfigId, DisplayAttribute::HEIGHT, &displayHeight);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get height", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttribute(displayConfigId, DisplayAttribute::DPI_X,
                                       &displayDpiX);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get height", __FUNCTION__,
          displayId);
    return error;
  }

  uint32_t hostDisplayId = 0;

  DEFINE_AND_VALIDATE_HOST_CONNECTION
  if (displayId == 0) {
    // Primary display:
    hostCon->lock();
    if (rcEnc->rcCreateDisplayById(rcEnc, displayId)) {
      ALOGE("%s host failed to create display %" PRIu64, __func__, displayId);
      hostCon->unlock();
      return HWC3::Error::NoResources;
    }
    if (rcEnc->rcSetDisplayPoseDpi(rcEnc, displayId, -1, -1, displayWidth,
                                   displayHeight, displayDpiX / 1000)) {
      ALOGE("%s host failed to set display %" PRIu64, __func__, displayId);
      hostCon->unlock();
      return HWC3::Error::NoResources;
    }
    hostCon->unlock();
  } else {
    // Secondary display:
    static constexpr const uint32_t kHostDisplayIdStart = 6;

    uint32_t expectedHostDisplayId = kHostDisplayIdStart + displayId - 1;
    uint32_t actualHostDisplayId = 0;

    hostCon->lock();
    rcEnc->rcDestroyDisplay(rcEnc, expectedHostDisplayId);
    rcEnc->rcCreateDisplay(rcEnc, &actualHostDisplayId);
    rcEnc->rcSetDisplayPose(rcEnc, actualHostDisplayId, -1, -1, displayWidth,
                            displayHeight);
    hostCon->unlock();

    if (actualHostDisplayId != expectedHostDisplayId) {
      ALOGE(
          "Something wrong with host displayId allocation, expected %d "
          "but received %d",
          expectedHostDisplayId, actualHostDisplayId);
    }

    hostDisplayId = actualHostDisplayId;
  }

  error = createHostComposerDisplayInfo(display, hostDisplayId);
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to initialize host info for display:%" PRIu64,
          __FUNCTION__, displayId);
    return error;
  }

  std::optional<std::vector<uint8_t>> edid;
  if (mDrmPresenter) {
    edid = mDrmPresenter->getEdid(displayId);
    if (edid) {
      display->setEdid(*edid);
    }
  }

  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::onDisplayDestroy(Display* display) {
  int64_t displayId = display->getId();

  auto it = mDisplayInfos.find(displayId);
  if (it == mDisplayInfos.end()) {
    ALOGE("%s: display:%" PRIu64 " missing display buffers?", __FUNCTION__,
          displayId);
    return HWC3::Error::BadDisplay;
  }

  HostComposerDisplayInfo& displayInfo = mDisplayInfos[displayId];

  if (displayId != 0) {
    DEFINE_AND_VALIDATE_HOST_CONNECTION
    hostCon->lock();
    rcEnc->rcDestroyDisplay(rcEnc, displayInfo.hostDisplayId);
    hostCon->unlock();
  }

  FreeDisplayColorBuffer(displayInfo.compositionResultBuffer);

  mDisplayInfos.erase(it);

  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::onDisplayClientTargetSet(Display* display) {
  int64_t displayId = display->getId();

  auto it = mDisplayInfos.find(displayId);
  if (it == mDisplayInfos.end()) {
    ALOGE("%s: display:%" PRIu64 " missing display buffers?", __FUNCTION__,
          displayId);
    return HWC3::Error::BadDisplay;
  }

  HostComposerDisplayInfo& displayInfo = mDisplayInfos[displayId];

  if (mIsMinigbm) {
    FencedBuffer& clientTargetFencedBuffer = display->getClientTarget();

    auto [drmBufferCreateError, drmBuffer] =
        mDrmPresenter->create(clientTargetFencedBuffer.getBuffer());
    if (drmBufferCreateError != HWC3::Error::None) {
      ALOGE("%s: display:%" PRIu64 " failed to create client target drm buffer",
            __FUNCTION__, displayId);
      return HWC3::Error::NoResources;
    }
    displayInfo.clientTargetDrmBuffer = std::move(drmBuffer);
  }

  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::validateDisplay(Display* display,
                                               DisplayChanges* outChanges) {
  const auto& displayId = display->getId();

  DEFINE_AND_VALIDATE_HOST_CONNECTION
  hostCon->lock();
  bool hostCompositionV1 = rcEnc->hasHostCompositionV1();
  bool hostCompositionV2 = rcEnc->hasHostCompositionV2();
  hostCon->unlock();

  const std::vector<Layer*> layers = display->getOrderedLayers();
  for (const auto& layer : layers) {
    switch (layer->getCompositionType()) {
      case Composition::INVALID:
        // Log error for unused layers, layer leak?
        ALOGE("%s layer:%" PRIu64 " CompositionType not set", __FUNCTION__,
              layer->getId());
        break;
      case Composition::DISPLAY_DECORATION:
        return HWC3::Error::Unsupported;
      default:
        break;
    }
  }

  // If one layer requires a fall back to the client composition type, all
  // layers will fall back to the client composition type.
  bool fallBackToClient = (!hostCompositionV1 && !hostCompositionV2) ||
                          display->hasColorTransform();

  if (!fallBackToClient) {
    for (const auto& layer : layers) {
      const auto& layerId = layer->getId();
      const auto& layerCompositionType = layer->getCompositionType();

      std::optional<Composition> layerFallBackTo = std::nullopt;
      switch (layerCompositionType) {
        case Composition::CLIENT:
        case Composition::SIDEBAND:
          ALOGI("%s: layer %" PRIu32 " CompositionType %d, fallback to client",
                __FUNCTION__, static_cast<uint32_t>(layer->getId()),
                layerCompositionType);
          layerFallBackTo = Composition::CLIENT;
          break;
        case Composition::CURSOR:
          ALOGI("%s: layer %" PRIu32 " CompositionType %d, fallback to device",
                __FUNCTION__, static_cast<uint32_t>(layer->getId()),
                layerCompositionType);
          layerFallBackTo = Composition::DEVICE;
          break;
        case Composition::INVALID:
        case Composition::DEVICE:
        case Composition::SOLID_COLOR:
          layerFallBackTo = std::nullopt;
          break;
        default:
          ALOGE("%s: layer %" PRIu32 " has an unknown composition type: %d",
                __FUNCTION__, static_cast<uint32_t>(layer->getId()),
                layerCompositionType);
      }
      if (layerFallBackTo == Composition::CLIENT) {
        fallBackToClient = true;
      }
      if (layerFallBackTo.has_value()) {
        outChanges->addLayerCompositionChange(displayId, layerId,
                                              *layerFallBackTo);
      }
    }
  }

  if (fallBackToClient) {
    outChanges->clearLayerCompositionChanges();
    for (auto& layer : layers) {
      const auto& layerId = layer->getId();
      if (layer->getCompositionType() == Composition::INVALID) {
        continue;
      }
      if (layer->getCompositionType() != Composition::CLIENT) {
        outChanges->addLayerCompositionChange(displayId, layerId,
                                              Composition::CLIENT);
      }
    }
  }

  return HWC3::Error::None;
}

HWC3::Error HostFrameComposer::presentDisplay(
    Display* display, ::android::base::unique_fd* outDisplayFence,
    std::unordered_map<int64_t, ::android::base::unique_fd>* outLayerFences) {
  auto displayId = display->getId();
  auto displayInfoIt = mDisplayInfos.find(displayId);
  if (displayInfoIt == mDisplayInfos.end()) {
    ALOGE("%s: failed to find display buffers for display:%" PRIu64,
          __FUNCTION__, displayId);
    return HWC3::Error::BadDisplay;
  }

  HostComposerDisplayInfo& displayInfo = displayInfoIt->second;

  HostConnection* hostCon;
  ExtendedRCEncoderContext* rcEnc;
  HWC3::Error error = getAndValidateHostConnection(&hostCon, &rcEnc);
  if (error != HWC3::Error::None) {
    return error;
  }
  hostCon->lock();
  bool hostCompositionV1 = rcEnc->hasHostCompositionV1();
  bool hostCompositionV2 = rcEnc->hasHostCompositionV2();
  hostCon->unlock();

  // Ff we supports v2, then discard v1
  if (hostCompositionV2) {
    hostCompositionV1 = false;
  }

  const std::vector<Layer*> layers = display->getOrderedLayers();
  if (hostCompositionV2 || hostCompositionV1) {
    uint32_t numLayer = 0;
    for (auto layer : layers) {
      if (layer->getCompositionType() == Composition::DEVICE ||
          layer->getCompositionType() == Composition::SOLID_COLOR) {
        numLayer++;
      }
    }

    DEBUG_LOG("%s: presenting display:%" PRIu64 " with %d layers", __FUNCTION__,
              displayId, static_cast<int>(layers.size()));

    if (numLayer == 0) {
      ALOGV(
          "%s display has no layers to compose, flushing client target buffer.",
          __FUNCTION__);

      FencedBuffer& displayClientTarget = display->getClientTarget();
      if (displayClientTarget.getBuffer() != nullptr) {
        ::android::base::unique_fd fence = displayClientTarget.getFence();
        if (mIsMinigbm) {
          auto [_, flushCompleteFence] = mDrmPresenter->flushToDisplay(
              displayId, *displayInfo.clientTargetDrmBuffer, fence);

          *outDisplayFence = std::move(flushCompleteFence);
        } else {
          post(hostCon, rcEnc, displayClientTarget.getBuffer());
          *outDisplayFence = std::move(fence);
        }
      }
      return HWC3::Error::None;
    }

    std::unique_ptr<ComposeMsg> composeMsg;
    std::unique_ptr<ComposeMsg_v2> composeMsgV2;

    if (hostCompositionV1) {
      composeMsg.reset(new ComposeMsg(numLayer));
    } else {
      composeMsgV2.reset(new ComposeMsg_v2(numLayer));
    }

    // Handle the composition
    ComposeDevice* p;
    ComposeDevice_v2* p2;
    ComposeLayer* l;

    if (hostCompositionV1) {
      p = composeMsg->get();
      l = p->layer;
    } else {
      p2 = composeMsgV2->get();
      l = p2->layer;
    }

    std::vector<int64_t> releaseLayerIds;
    for (auto layer : layers) {
      // TODO: use local var composisitonType to store getCompositionType()
      if (layer->getCompositionType() != Composition::DEVICE &&
          layer->getCompositionType() != Composition::SOLID_COLOR) {
        ALOGE("%s: Unsupported composition types %d layer %u", __FUNCTION__,
              layer->getCompositionType(), (uint32_t)layer->getId());
        continue;
      }
      // send layer composition command to host
      if (layer->getCompositionType() == Composition::DEVICE) {
        releaseLayerIds.emplace_back(layer->getId());

        ::android::base::unique_fd fence = layer->getBuffer().getFence();
        if (fence.ok()) {
          int err = sync_wait(fence.get(), 3000);
          if (err < 0 && errno == ETIME) {
            ALOGE("%s waited on fence %d for 3000 ms", __FUNCTION__,
                  fence.get());
          }
        } else {
          ALOGV("%s: acquire fence not set for layer %u", __FUNCTION__,
                (uint32_t)layer->getId());
        }
        const native_handle_t* cb = layer->getBuffer().getBuffer();
        if (cb != nullptr) {
          l->cbHandle = hostCon->grallocHelper()->getHostHandle(cb);
        } else {
          ALOGE("%s null buffer for layer %d", __FUNCTION__,
                (uint32_t)layer->getId());
        }
      } else {
        // solidcolor has no buffer
        l->cbHandle = 0;
      }
      l->composeMode = (hwc2_composition_t)layer->getCompositionType();
      l->displayFrame = AsHwcRect(layer->getDisplayFrame());
      l->crop = AsHwcFrect(layer->getSourceCrop());
      l->blendMode = static_cast<int32_t>(layer->getBlendMode());
      l->alpha = layer->getPlaneAlpha();
      l->color = AsHwcColor(layer->getColor());
      l->transform = AsHwcTransform(layer->getTransform());
      ALOGV(
          "   cb %d blendmode %d alpha %f %d %d %d %d z %d"
          " composeMode %d, transform %d",
          l->cbHandle, l->blendMode, l->alpha, l->displayFrame.left,
          l->displayFrame.top, l->displayFrame.right, l->displayFrame.bottom,
          layer->getZOrder(), l->composeMode, l->transform);
      l++;
    }
    if (hostCompositionV1) {
      p->version = 1;
      p->targetHandle = hostCon->grallocHelper()->getHostHandle(
          displayInfo.compositionResultBuffer);
      p->numLayers = numLayer;
    } else {
      p2->version = 2;
      p2->displayId = displayInfo.hostDisplayId;
      p2->targetHandle = hostCon->grallocHelper()->getHostHandle(
          displayInfo.compositionResultBuffer);
      p2->numLayers = numLayer;
    }

    void* buffer;
    uint32_t bufferSize;
    if (hostCompositionV1) {
      buffer = (void*)p;
      bufferSize = sizeof(ComposeDevice) + numLayer * sizeof(ComposeLayer);
    } else {
      bufferSize = sizeof(ComposeDevice_v2) + numLayer * sizeof(ComposeLayer);
      buffer = (void*)p2;
    }

    ::android::base::unique_fd retire_fd;
    hostCon->lock();
    if (rcEnc->hasAsyncFrameCommands()) {
      if (mIsMinigbm) {
        rcEnc->rcComposeAsyncWithoutPost(rcEnc, bufferSize, buffer);
      } else {
        rcEnc->rcComposeAsync(rcEnc, bufferSize, buffer);
      }
    } else {
      if (mIsMinigbm) {
        rcEnc->rcComposeWithoutPost(rcEnc, bufferSize, buffer);
      } else {
        rcEnc->rcCompose(rcEnc, bufferSize, buffer);
      }
    }
    hostCon->unlock();

    // Send a retire fence and use it as the release fence for all layers,
    // since media expects it
    EGLint attribs[] = {EGL_SYNC_NATIVE_FENCE_ANDROID,
                        EGL_NO_NATIVE_FENCE_FD_ANDROID};

    uint64_t sync_handle, thread_handle;

    // We don't use rc command to sync if we are using ANGLE on the guest with
    // virtio-gpu.
    bool useRcCommandToSync = !(mUseAngle && mIsMinigbm);

    if (useRcCommandToSync) {
      hostCon->lock();
      rcEnc->rcCreateSyncKHR(
          rcEnc, EGL_SYNC_NATIVE_FENCE_ANDROID, attribs, 2 * sizeof(EGLint),
          true /* destroy when signaled */, &sync_handle, &thread_handle);
      hostCon->unlock();
    }

    if (mIsMinigbm) {
      auto [_, fence] = mDrmPresenter->flushToDisplay(
          displayId, *displayInfo.compositionResultDrmBuffer, -1);
      retire_fd = std::move(fence);
    } else {
      int fd;
      goldfish_sync_queue_work(mSyncDeviceFd, sync_handle, thread_handle, &fd);
      retire_fd = ::android::base::unique_fd(fd);
    }

    for (int64_t layerId : releaseLayerIds) {
      (*outLayerFences)[layerId] =
          ::android::base::unique_fd(dup(retire_fd.get()));
    }
    *outDisplayFence = ::android::base::unique_fd(dup(retire_fd.get()));

    if (useRcCommandToSync) {
      hostCon->lock();
      if (rcEnc->hasAsyncFrameCommands()) {
        rcEnc->rcDestroySyncKHRAsync(rcEnc, sync_handle);
      } else {
        rcEnc->rcDestroySyncKHR(rcEnc, sync_handle);
      }
      hostCon->unlock();
    }

  } else {
    // we set all layers Composition::CLIENT, so do nothing.
    FencedBuffer& displayClientTarget = display->getClientTarget();
    ::android::base::unique_fd displayClientTargetFence =
        displayClientTarget.getFence();
    if (mIsMinigbm) {
      auto [_, flushFence] = mDrmPresenter->flushToDisplay(
          displayId, *displayInfo.compositionResultDrmBuffer,
          displayClientTargetFence);
      *outDisplayFence = std::move(flushFence);
    } else {
      post(hostCon, rcEnc, displayClientTarget.getBuffer());
      *outDisplayFence = std::move(displayClientTargetFence);
    }
    ALOGV("%s fallback to post, returns outRetireFence %d", __FUNCTION__,
          outDisplayFence->get());
  }
  return HWC3::Error::None;
}

void HostFrameComposer::post(HostConnection* hostCon,
                             ExtendedRCEncoderContext* rcEnc,
                             buffer_handle_t h) {
  assert(cb && "native_handle_t::from(h) failed");

  hostCon->lock();
  rcEnc->rcFBPost(rcEnc, hostCon->grallocHelper()->getHostHandle(h));
  hostCon->flush();
  hostCon->unlock();
}

HWC3::Error HostFrameComposer::onActiveConfigChange(Display* display) {
  DEBUG_LOG("%s: display:%" PRIu64, __FUNCTION__, display->getId());
  HWC3::Error error = createHostComposerDisplayInfo(display, display->getId());
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to update host info for display:%" PRIu64, __FUNCTION__,
          display->getId());
    return error;
  }
  return HWC3::Error::None;
}

}  // namespace aidl::android::hardware::graphics::composer3::impl
