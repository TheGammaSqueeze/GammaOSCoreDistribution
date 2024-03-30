/*
 * Copyright 2021 The Android Open Source Project
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

#include "HostComposer.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <poll.h>
#include <sync/sync.h>
#include <ui/GraphicBuffer.h>
#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>

#include <algorithm>
#include <iterator>
#include <optional>
#include <tuple>

#include "../egl/goldfish_sync.h"
#include "Device.h"
#include "Display.h"
#include "HostUtils.h"
#include "virtgpu_drm.h"

namespace android {
namespace {

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

}  // namespace

std::unique_ptr<HostComposer::CompositionResultBuffer>
HostComposer::CompositionResultBuffer::create(int32_t width, int32_t height) {
  const uint32_t layerCount = 1;
  const uint64_t graphicBufferId = 0;  // not used
  buffer_handle_t handle;
  uint32_t stride;

  if (GraphicBufferAllocator::get().allocate(
          width, height, PIXEL_FORMAT_RGBA_8888, layerCount,
          (GraphicBuffer::USAGE_HW_COMPOSER | GraphicBuffer::USAGE_HW_RENDER),
          &handle, &stride, graphicBufferId, "EmuHWC2") != OK) {
    return nullptr;
  }

  std::unique_ptr<FencedBuffer> fencedBuffer = std::make_unique<FencedBuffer>();
  fencedBuffer->setBuffer(handle);
  std::unique_ptr<CompositionResultBuffer> res(new CompositionResultBuffer());
  res->mFencedBuffer = std::move(fencedBuffer);
  return res;
}

std::unique_ptr<HostComposer::CompositionResultBuffer>
HostComposer::CompositionResultBuffer::createWithDrmBuffer(
    int32_t width, int32_t height, DrmPresenter& drmPresenter) {
  std::unique_ptr<CompositionResultBuffer> res = create(width, height);
  if (!res) {
    return nullptr;
  }
  res->mDrmBuffer = std::make_unique<DrmBuffer>(res->mFencedBuffer->getBuffer(),
                                                &drmPresenter);
  return res;
}

HostComposer::CompositionResultBuffer::~CompositionResultBuffer() {
  mDrmBuffer = nullptr;
  waitForFence();
  buffer_handle_t handle = mFencedBuffer->getBuffer();
  mFencedBuffer = nullptr;
  GraphicBufferAllocator::get().free(handle);
}

DrmBuffer& HostComposer::CompositionResultBuffer::waitAndGetDrmBuffer() {
  waitForFence();
  return *mDrmBuffer;
}

buffer_handle_t
HostComposer::CompositionResultBuffer::waitAndGetBufferHandle() {
  waitForFence();
  return mFencedBuffer->getBuffer();
}

bool HostComposer::CompositionResultBuffer::isReady() const {
  base::unique_fd fence = mFencedBuffer->getFence();
  if (!fence.ok()) {
    return true;
  }
  if (sync_wait(fence, 0) == 0) {
    return true;
  }
  if (errno != ETIME) {
    ALOGE("%s: fail when calling sync_wait: %s(%d).", __func__, strerror(errno),
          errno);
  }
  return false;
}

void HostComposer::CompositionResultBuffer::setFence(base::unique_fd fence) {
  mFencedBuffer->setFence(std::move(fence));
}

void HostComposer::CompositionResultBuffer::waitForFence() {
  base::unique_fd fence = mFencedBuffer->getFence();
  if (!fence.ok()) {
    return;
  }
  constexpr int kWaitInterval = 3000;
  while (true) {
    int ret = sync_wait(fence, kWaitInterval);
    if (ret == 0) {
      return;
    }
    if (errno == ETIME) {
      ALOGI(
          "%s: timeout when calling sync_wait with fence = %d, timeout = %d, "
          "retry.",
          __func__, static_cast<int>(fence), kWaitInterval);
      continue;
    }
    ALOGE(
        "%s: error when calling sync_wait with fence = %d, timeout = %d: "
        "%s(%d). Quit.",
        __func__, static_cast<int>(fence), kWaitInterval, strerror(errno),
        errno);
    return;
  }
}

void HostComposer::HostComposerDisplayInfo::resetCompositionResultBuffers(
    std::vector<std::unique_ptr<CompositionResultBuffer>>
        newCompositionResultBuffers) {
  compositionResultBuffers = std::move(newCompositionResultBuffers);
}

HostComposer::CompositionResultBuffer&
HostComposer::HostComposerDisplayInfo::getNextCompositionResultBuffer() {
  auto i = compositionResultBuffers.begin();
  // Find the buffer that is already ready for the next composition.
  for (; i != compositionResultBuffers.end(); i++) {
    if ((*i)->isReady()) {
      break;
    }
  }
  // If no buffers are ready, choose the first buffer which should be the
  // earliest buffer sent for composition.
  if (i == compositionResultBuffers.end()) {
    i = compositionResultBuffers.begin();
  }
  // Move the selected buffer to the end of compositionResultBuffers without
  // changing the existing order of other buffers.
  std::rotate(i, std::next(i), compositionResultBuffers.end());
  return *compositionResultBuffers.back();
}

HostComposer::HostComposer(DrmPresenter* drmPresenter, bool isMinigbm)
    : mDrmPresenter(drmPresenter), mIsMinigbm(isMinigbm) {}

HWC2::Error HostComposer::init() {
  if (!mIsMinigbm) {
    mSyncDeviceFd = goldfish_sync_open();
  }

  return HWC2::Error::None;
}

HWC2::Error HostComposer::createHostComposerDisplayInfo(
    Display* display, uint32_t hostDisplayId) {
  HWC2::Error error = HWC2::Error::None;

  hwc2_display_t displayId = display->getId();
  hwc2_config_t displayConfigId;
  int32_t displayWidth;
  int32_t displayHeight;

  error = display->getActiveConfig(&displayConfigId);
  if (error != HWC2::Error::None) {
    ALOGE("%s: display:%" PRIu64 " has no active config", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttributeEnum(
      displayConfigId, HWC2::Attribute::Width, &displayWidth);
  if (error != HWC2::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get width", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttributeEnum(
      displayConfigId, HWC2::Attribute::Height, &displayHeight);
  if (error != HWC2::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get height", __FUNCTION__,
          displayId);
    return error;
  }

  HostComposerDisplayInfo& displayInfo = mDisplayInfos[displayId];

  displayInfo.hostDisplayId = hostDisplayId;

  std::vector<std::unique_ptr<CompositionResultBuffer>>
      compositionResultBuffers;
  constexpr uint32_t kCompositionInFlight = 3;
  for (uint32_t i = 0; i < kCompositionInFlight; i++) {
    std::unique_ptr<CompositionResultBuffer> buffer = nullptr;
    if (mIsMinigbm) {
      buffer = CompositionResultBuffer::createWithDrmBuffer(
          displayWidth, displayHeight, *mDrmPresenter);
    } else {
      buffer = CompositionResultBuffer::create(displayWidth, displayHeight);
    }
    if (!buffer) {
      ALOGE("%s: display:%" PRIu64
            " failed to create composition target buffer",
            __FUNCTION__, displayId);
      return HWC2::Error::NoResources;
    }
    compositionResultBuffers.emplace_back(std::move(buffer));
  }
  displayInfo.resetCompositionResultBuffers(
      std::move(compositionResultBuffers));

  return HWC2::Error::None;
}

HWC2::Error HostComposer::onDisplayCreate(Display* display) {
  HWC2::Error error = HWC2::Error::None;

  hwc2_display_t displayId = display->getId();
  hwc2_config_t displayConfigId;
  int32_t displayWidth;
  int32_t displayHeight;
  int32_t displayDpiX;

  error = display->getActiveConfig(&displayConfigId);
  if (error != HWC2::Error::None) {
    ALOGE("%s: display:%" PRIu64 " has no active config", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttributeEnum(
      displayConfigId, HWC2::Attribute::Width, &displayWidth);
  if (error != HWC2::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get width", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttributeEnum(
      displayConfigId, HWC2::Attribute::Height, &displayHeight);
  if (error != HWC2::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to get height", __FUNCTION__,
          displayId);
    return error;
  }

  error = display->getDisplayAttributeEnum(displayConfigId,
                                           HWC2::Attribute::DpiX, &displayDpiX);
  if (error != HWC2::Error::None) {
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
      return HWC2::Error::NoResources;
    }
    if (rcEnc->rcSetDisplayPoseDpi(rcEnc, displayId, -1, -1, displayWidth,
                                   displayHeight, displayDpiX / 1000)) {
      ALOGE("%s host failed to set display %" PRIu64, __func__, displayId);
      hostCon->unlock();
      return HWC2::Error::NoResources;
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
  if (error != HWC2::Error::None) {
    ALOGE("%s failed to initialize host info for display:%" PRIu64,
          __FUNCTION__, displayId);
    return error;
  }

  std::optional<std::vector<uint8_t>> edid;
  if (mIsMinigbm) {
    edid = mDrmPresenter->getEdid(displayId);
    if (edid) {
      display->setEdid(*edid);
    }
  }

  return HWC2::Error::None;
}

HWC2::Error HostComposer::onDisplayDestroy(Display* display) {
  hwc2_display_t displayId = display->getId();

  auto it = mDisplayInfos.find(displayId);
  if (it == mDisplayInfos.end()) {
    ALOGE("%s: display:%" PRIu64 " missing display buffers?", __FUNCTION__,
          displayId);
    return HWC2::Error::BadDisplay;
  }

  HostComposerDisplayInfo& displayInfo = mDisplayInfos[displayId];

  if (displayId != 0) {
    DEFINE_AND_VALIDATE_HOST_CONNECTION
    hostCon->lock();
    rcEnc->rcDestroyDisplay(rcEnc, displayInfo.hostDisplayId);
    hostCon->unlock();
  }

  mDisplayInfos.erase(it);

  return HWC2::Error::None;
}

HWC2::Error HostComposer::onDisplayClientTargetSet(Display* display) {
  hwc2_display_t displayId = display->getId();

  auto it = mDisplayInfos.find(displayId);
  if (it == mDisplayInfos.end()) {
    ALOGE("%s: display:%" PRIu64 " missing display buffers?", __FUNCTION__,
          displayId);
    return HWC2::Error::BadDisplay;
  }

  HostComposerDisplayInfo& displayInfo = mDisplayInfos[displayId];

  if (mIsMinigbm) {
    FencedBuffer& clientTargetFencedBuffer = display->getClientTarget();

    displayInfo.clientTargetDrmBuffer.reset(
        new DrmBuffer(clientTargetFencedBuffer.getBuffer(), mDrmPresenter));
  }

  return HWC2::Error::None;
}

HWC2::Error HostComposer::validateDisplay(
    Display* display, std::unordered_map<hwc2_layer_t, HWC2::Composition>*
                          layerCompositionChanges) {
  DEFINE_AND_VALIDATE_HOST_CONNECTION
  hostCon->lock();
  bool hostCompositionV1 = rcEnc->hasHostCompositionV1();
  bool hostCompositionV2 = rcEnc->hasHostCompositionV2();
  hostCon->unlock();

  const std::vector<Layer*> layers = display->getOrderedLayers();
  for (const auto& layer : layers) {
    if (layer->getCompositionType() == HWC2::Composition::Invalid) {
      // Log error for unused layers, layer leak?
      ALOGE("%s layer %" PRIu32 " CompositionType(%d) not set", __FUNCTION__,
            (uint32_t)layer->getId(), layer->getCompositionType());
    }
  }

  // If one layer requires a fall back to the client composition type, all
  // layers will fall back to the client composition type.
  bool fallBackToClient = (!hostCompositionV1 && !hostCompositionV2) ||
                          display->hasColorTransform();
  std::unordered_map<hwc2_layer_t, HWC2::Composition> changes;

  if (!fallBackToClient) {
    for (const auto& layer : layers) {
      HWC2::Composition layerCompositionType = layer->getCompositionType();
      std::optional<HWC2::Composition> layerFallBackTo = std::nullopt;
      switch (layerCompositionType) {
        case HWC2::Composition::Client:
        case HWC2::Composition::Sideband:
          ALOGI("%s: layer %" PRIu32 " CompositionType %d, fallback to client",
                __FUNCTION__, static_cast<uint32_t>(layer->getId()),
                layerCompositionType);
          layerFallBackTo = HWC2::Composition::Client;
          break;
        case HWC2::Composition::Cursor:
          ALOGI("%s: layer %" PRIu32 " CompositionType %d, fallback to device",
                __FUNCTION__, static_cast<uint32_t>(layer->getId()),
                layerCompositionType);
          layerFallBackTo = HWC2::Composition::Device;
          break;
        case HWC2::Composition::Invalid:
        case HWC2::Composition::Device:
        case HWC2::Composition::SolidColor:
          layerFallBackTo = std::nullopt;
          break;
        default:
          ALOGE("%s: layer %" PRIu32 " has an unknown composition type: %d",
                __FUNCTION__, static_cast<uint32_t>(layer->getId()),
                layerCompositionType);
      }
      if (layerFallBackTo == HWC2::Composition::Client) {
        fallBackToClient = true;
      }
      if (layerFallBackTo.has_value()) {
        changes[layer->getId()] = layerFallBackTo.value();
      }
    }
  }

  if (fallBackToClient) {
    changes.clear();
    for (auto& layer : layers) {
      if (layer->getCompositionType() == HWC2::Composition::Invalid) {
        continue;
      }
      if (layer->getCompositionType() != HWC2::Composition::Client) {
        changes[layer->getId()] = HWC2::Composition::Client;
      }
    }
  }

  *layerCompositionChanges = std::move(changes);

  return HWC2::Error::None;
}

std::tuple<HWC2::Error, base::unique_fd> HostComposer::presentDisplay(
    Display* display) {
  auto it = mDisplayInfos.find(display->getId());
  base::unique_fd outRetireFence;
  if (it == mDisplayInfos.end()) {
    ALOGE("%s: failed to find display buffers for display:%" PRIu64,
          __FUNCTION__, display->getId());
    return std::make_tuple(HWC2::Error::BadDisplay, base::unique_fd());
  }

  HostComposerDisplayInfo& displayInfo = it->second;

  HostConnection* hostCon;
  ExtendedRCEncoderContext* rcEnc;
  HWC2::Error res = getAndValidateHostConnection(&hostCon, &rcEnc);
  if (res != HWC2::Error::None) {
    return std::make_tuple(res, base::unique_fd());
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
      if (layer->getCompositionType() == HWC2::Composition::Device ||
          layer->getCompositionType() == HWC2::Composition::SolidColor) {
        numLayer++;
      }
    }

    DEBUG_LOG("%s: presenting display:%" PRIu64 " with %d layers", __FUNCTION__,
              display->getId(), static_cast<int>(layers.size()));

    display->clearReleaseFencesAndIdsLocked();

    if (numLayer == 0) {
      ALOGV(
          "%s display has no layers to compose, flushing client target buffer.",
          __FUNCTION__);

      FencedBuffer& displayClientTarget = display->getClientTarget();
      if (displayClientTarget.getBuffer() != nullptr) {
        base::unique_fd fence = displayClientTarget.getFence();
        if (mIsMinigbm) {
          auto [_, flushCompleteFence] =
              displayInfo.clientTargetDrmBuffer->flushToDisplay(
                  display->getId(), fence);
          outRetireFence = std::move(flushCompleteFence);
        } else {
          post(hostCon, rcEnc, displayClientTarget.getBuffer());
          outRetireFence = std::move(fence);
        }
      }
      return std::make_tuple(HWC2::Error::None, std::move(outRetireFence));
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

    std::vector<hwc2_layer_t> releaseLayerIds;
    for (auto layer : layers) {
      // TODO: use local var composisitonType to store getCompositionType()
      if (layer->getCompositionType() != HWC2::Composition::Device &&
          layer->getCompositionType() != HWC2::Composition::SolidColor) {
        ALOGE("%s: Unsupported composition types %d layer %u", __FUNCTION__,
              layer->getCompositionType(), (uint32_t)layer->getId());
        continue;
      }
      // send layer composition command to host
      if (layer->getCompositionType() == HWC2::Composition::Device) {
        releaseLayerIds.emplace_back(layer->getId());

        base::unique_fd fence = layer->getBuffer().getFence();
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
      l->displayFrame = layer->getDisplayFrame();
      l->crop = layer->getSourceCrop();
      l->blendMode = static_cast<int32_t>(layer->getBlendMode());
      l->alpha = layer->getPlaneAlpha();
      l->color = layer->getColor();
      l->transform = layer->getTransform();
      ALOGV(
          "   cb %d blendmode %d alpha %f %d %d %d %d z %d"
          " composeMode %d, transform %d",
          l->cbHandle, l->blendMode, l->alpha, l->displayFrame.left,
          l->displayFrame.top, l->displayFrame.right, l->displayFrame.bottom,
          layer->getZ(), l->composeMode, l->transform);
      l++;
    }

    auto& compositionResultBuffer =
        displayInfo.getNextCompositionResultBuffer();
    if (hostCompositionV1) {
      p->version = 1;
      p->targetHandle = hostCon->grallocHelper()->getHostHandle(
          compositionResultBuffer.waitAndGetBufferHandle());
      p->numLayers = numLayer;
    } else {
      p2->version = 2;
      p2->displayId = displayInfo.hostDisplayId;
      p2->targetHandle = hostCon->grallocHelper()->getHostHandle(
          compositionResultBuffer.waitAndGetBufferHandle());
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

    base::unique_fd retire_fd;
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

    // We don't use rc command to sync if we are using virtio-gpu, which is
    // proxied by minigbm.
    bool useRcCommandToSync = !mIsMinigbm;

    if (useRcCommandToSync) {
      hostCon->lock();
      rcEnc->rcCreateSyncKHR(
          rcEnc, EGL_SYNC_NATIVE_FENCE_ANDROID, attribs, 2 * sizeof(EGLint),
          true /* destroy when signaled */, &sync_handle, &thread_handle);
      hostCon->unlock();
    }

    if (mIsMinigbm) {
      auto [_, fence] =
          compositionResultBuffer.waitAndGetDrmBuffer().flushToDisplay(
              display->getId(), -1);
      retire_fd = std::move(fence);
    } else {
      int fd;
      goldfish_sync_queue_work(mSyncDeviceFd, sync_handle, thread_handle, &fd);
      retire_fd = base::unique_fd(fd);
    }

    for (hwc2_layer_t layerId : releaseLayerIds) {
      display->addReleaseFenceLocked(layerId,
                                     base::unique_fd(dup(retire_fd.get())));
    }

    outRetireFence = base::unique_fd(dup(retire_fd.get()));
    if (useRcCommandToSync) {
      hostCon->lock();
      if (rcEnc->hasAsyncFrameCommands()) {
        rcEnc->rcDestroySyncKHRAsync(rcEnc, sync_handle);
      } else {
        rcEnc->rcDestroySyncKHR(rcEnc, sync_handle);
      }
      hostCon->unlock();
    }
    compositionResultBuffer.setFence(base::unique_fd(dup(retire_fd.get())));
  } else {
    // we set all layers Composition::Client, so do nothing.
    FencedBuffer& displayClientTarget = display->getClientTarget();
    base::unique_fd fence = displayClientTarget.getFence();
    if (mIsMinigbm) {
      auto [_, outRetireFence] =
          displayInfo.clientTargetDrmBuffer->flushToDisplay(display->getId(),
                                                            fence);
      outRetireFence = std::move(fence);
    } else {
      post(hostCon, rcEnc, displayClientTarget.getBuffer());
      outRetireFence = std::move(fence);
    }
    ALOGV("%s fallback to post, returns outRetireFence %d", __FUNCTION__,
          outRetireFence.get());
  }

  return std::make_tuple(HWC2::Error::None, std::move(outRetireFence));
}

void HostComposer::post(HostConnection* hostCon,
                        ExtendedRCEncoderContext* rcEnc, buffer_handle_t h) {
  assert(cb && "native_handle_t::from(h) failed");

  hostCon->lock();
  rcEnc->rcFBPost(rcEnc, hostCon->grallocHelper()->getHostHandle(h));
  hostCon->flush();
  hostCon->unlock();
}

HWC2::Error HostComposer::onActiveConfigChange(Display* display) {
  DEBUG_LOG("%s: display:%" PRIu64, __FUNCTION__, display->getId());
  HWC2::Error error = createHostComposerDisplayInfo(display, display->getId());
  if (error != HWC2::Error::None) {
    ALOGE("%s failed to update host info for display:%" PRIu64, __FUNCTION__,
          display->getId());
    return error;
  }
  return HWC2::Error::None;
}

}  // namespace android
