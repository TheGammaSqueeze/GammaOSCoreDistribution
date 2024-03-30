/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "ComposerClient.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <android/binder_ibinder_platform.h>

#include "Common.h"
#include "Device.h"
#include "GuestFrameComposer.h"
#include "HostFrameComposer.h"

namespace aidl::android::hardware::graphics::composer3::impl {
namespace {

#define GET_DISPLAY_OR_RETURN_ERROR()                                    \
  Display* display = getDisplay(displayId);                              \
  if (display == nullptr) {                                              \
    ALOGE("%s failed to get display:%" PRIu64, __FUNCTION__, displayId); \
    return ToBinderStatus(HWC3::Error::BadDisplay);                      \
  }

}  // namespace

using ::aidl::android::hardware::graphics::common::PixelFormat;

class ComposerClient::CommandResultWriter {
 public:
  CommandResultWriter(std::vector<CommandResultPayload>* results)
      : mIndex(0), mResults(results) {}

  void nextCommand() { ++mIndex; }

  void addError(HWC3::Error error) {
    CommandError commandErrorResult;
    commandErrorResult.commandIndex = mIndex;
    commandErrorResult.errorCode = static_cast<int32_t>(error);
    mResults->emplace_back(std::move(commandErrorResult));
  }

  void addPresentFence(int64_t displayId, ::android::base::unique_fd fence) {
    if (fence >= 0) {
      PresentFence presentFenceResult;
      presentFenceResult.display = displayId;
      presentFenceResult.fence = ndk::ScopedFileDescriptor(fence.release());
      mResults->emplace_back(std::move(presentFenceResult));
    }
  }

  void addReleaseFences(
      int64_t displayId,
      std::unordered_map<int64_t, ::android::base::unique_fd> layerFences) {
    ReleaseFences releaseFencesResult;
    releaseFencesResult.display = displayId;
    for (auto& [layer, layerFence] : layerFences) {
      if (layerFence >= 0) {
        ReleaseFences::Layer releaseFencesLayerResult;
        releaseFencesLayerResult.layer = layer;
        releaseFencesLayerResult.fence =
            ndk::ScopedFileDescriptor(layerFence.release());
        releaseFencesResult.layers.emplace_back(
            std::move(releaseFencesLayerResult));
      }
    }
    mResults->emplace_back(std::move(releaseFencesResult));
  }

  void addChanges(const DisplayChanges& changes) {
    if (changes.compositionChanges) {
      mResults->emplace_back(*changes.compositionChanges);
    }
    if (changes.displayRequestChanges) {
      mResults->emplace_back(*changes.displayRequestChanges);
    }
  }

  void addPresentOrValidateResult(int64_t displayId,
                                  PresentOrValidate::Result pov) {
    PresentOrValidate result;
    result.display = displayId;
    result.result = pov;
    mResults->emplace_back(std::move(result));
  }

 private:
  int32_t mIndex = 0;
  std::vector<CommandResultPayload>* mResults = nullptr;
};

ComposerClient::ComposerClient() { DEBUG_LOG("%s", __FUNCTION__); }

ComposerClient::~ComposerClient() {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  destroyDisplaysLocked();

  if (mOnClientDestroyed) {
    mOnClientDestroyed();
  }
}

HWC3::Error ComposerClient::init() {
  DEBUG_LOG("%s", __FUNCTION__);

  HWC3::Error error = HWC3::Error::None;

  std::unique_lock<std::mutex> lock(mStateMutex);

  mResources = std::make_unique<ComposerResources>();
  if (!mResources) {
    ALOGE("%s failed to allocate ComposerResources", __FUNCTION__);
    return HWC3::Error::NoResources;
  }

  error = mResources->init();
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to initialize ComposerResources", __FUNCTION__);
    return error;
  }

  error = Device::getInstance().getComposer(&mComposer);
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to get FrameComposer", __FUNCTION__);
    return error;
  }

  const auto HotplugCallback = [this](bool connected,   //
                                      int32_t id,       //
                                      uint32_t width,   //
                                      uint32_t height,  //
                                      uint32_t dpiX,    //
                                      uint32_t dpiY,    //
                                      uint32_t refreshRate) {
    handleHotplug(connected, id, width, height, dpiX, dpiY, refreshRate);
  };
  error = mComposer->registerOnHotplugCallback(HotplugCallback);
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to register hotplug callback", __FUNCTION__);
    return error;
  }

  error = createDisplaysLocked();
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to create displays.", __FUNCTION__);
    return error;
  }

  DEBUG_LOG("%s initialized!", __FUNCTION__);
  return HWC3::Error::None;
}

ndk::ScopedAStatus ComposerClient::createLayer(int64_t displayId,
                                               int32_t bufferSlotCount,
                                               int64_t* layerId) {
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  HWC3::Error error = display->createLayer(layerId);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to create layer", __FUNCTION__,
          displayId);
    return ToBinderStatus(error);
  }

  error = mResources->addLayer(displayId, *layerId, bufferSlotCount);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " resources failed to create layer",
          __FUNCTION__, displayId);
    return ToBinderStatus(error);
  }

  return ToBinderStatus(HWC3::Error::None);
}

ndk::ScopedAStatus ComposerClient::createVirtualDisplay(
    int32_t /*width*/, int32_t /*height*/, PixelFormat /*formatHint*/,
    int32_t /*outputBufferSlotCount*/, VirtualDisplay* /*display*/) {
  DEBUG_LOG("%s", __FUNCTION__);

  return ToBinderStatus(HWC3::Error::Unsupported);
}

ndk::ScopedAStatus ComposerClient::destroyLayer(int64_t displayId,
                                                int64_t layerId) {
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  HWC3::Error error = display->destroyLayer(layerId);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " failed to destroy layer:%" PRIu64,
          __FUNCTION__, displayId, layerId);
    return ToBinderStatus(error);
  }

  error = mResources->removeLayer(displayId, layerId);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRIu64 " resources failed to destroy layer:%" PRIu64,
          __FUNCTION__, displayId, layerId);
    return ToBinderStatus(error);
  }

  return ToBinderStatus(HWC3::Error::None);
}

ndk::ScopedAStatus ComposerClient::destroyVirtualDisplay(
    int64_t /*displayId*/) {
  DEBUG_LOG("%s", __FUNCTION__);

  return ToBinderStatus(HWC3::Error::Unsupported);
}

ndk::ScopedAStatus ComposerClient::executeCommands(
    const std::vector<DisplayCommand>& commands,
    std::vector<CommandResultPayload>* commandResultPayloads) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  mCommandResults =
      std::make_unique<CommandResultWriter>(commandResultPayloads);

  for (const DisplayCommand& command : commands) {
    executeDisplayCommand(command);
    mCommandResults->nextCommand();
  }

  mCommandResults.reset();

  return ToBinderStatus(HWC3::Error::None);
}

ndk::ScopedAStatus ComposerClient::getActiveConfig(int64_t displayId,
                                                   int32_t* config) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getActiveConfig(config));
}

ndk::ScopedAStatus ComposerClient::getColorModes(
    int64_t displayId, std::vector<ColorMode>* colorModes) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getColorModes(colorModes));
}

ndk::ScopedAStatus ComposerClient::getDataspaceSaturationMatrix(
    common::Dataspace dataspace, std::vector<float>* matrix) {
  DEBUG_LOG("%s", __FUNCTION__);

  if (dataspace != common::Dataspace::SRGB_LINEAR) {
    return ToBinderStatus(HWC3::Error::BadParameter);
  }

  // clang-format off
  constexpr std::array<float, 16> kUnit {
    1.0f, 0.0f, 0.0f, 0.0f,
    0.0f, 1.0f, 0.0f, 0.0f,
    0.0f, 0.0f, 1.0f, 0.0f,
    0.0f, 0.0f, 0.0f, 1.0f,
  };
  // clang-format on
  matrix->clear();
  matrix->insert(matrix->begin(), kUnit.begin(), kUnit.end());

  return ToBinderStatus(HWC3::Error::None);
}

ndk::ScopedAStatus ComposerClient::getDisplayAttribute(
    int64_t displayId, int32_t config, DisplayAttribute attribute,
    int32_t* value) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDisplayAttribute(config, attribute, value));
}

ndk::ScopedAStatus ComposerClient::getDisplayCapabilities(
    int64_t displayId, std::vector<DisplayCapability>* outCaps) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDisplayCapabilities(outCaps));
}

ndk::ScopedAStatus ComposerClient::getDisplayConfigs(
    int64_t displayId, std::vector<int32_t>* outConfigs) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDisplayConfigs(outConfigs));
}

ndk::ScopedAStatus ComposerClient::getDisplayConnectionType(
    int64_t displayId, DisplayConnectionType* outType) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDisplayConnectionType(outType));
}

ndk::ScopedAStatus ComposerClient::getDisplayIdentificationData(
    int64_t displayId, DisplayIdentification* outIdentification) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(
      display->getDisplayIdentificationData(outIdentification));
}

ndk::ScopedAStatus ComposerClient::getDisplayName(int64_t displayId,
                                                  std::string* outName) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDisplayName(outName));
}

ndk::ScopedAStatus ComposerClient::getDisplayVsyncPeriod(
    int64_t displayId, int32_t* outVsyncPeriod) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDisplayVsyncPeriod(outVsyncPeriod));
}

ndk::ScopedAStatus ComposerClient::getDisplayedContentSample(
    int64_t displayId, int64_t maxFrames, int64_t timestamp,
    DisplayContentSample* outSamples) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(
      display->getDisplayedContentSample(maxFrames, timestamp, outSamples));
}

ndk::ScopedAStatus ComposerClient::getDisplayedContentSamplingAttributes(
    int64_t displayId, DisplayContentSamplingAttributes* outAttributes) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(
      display->getDisplayedContentSamplingAttributes(outAttributes));
}

ndk::ScopedAStatus ComposerClient::getDisplayPhysicalOrientation(
    int64_t displayId, common::Transform* outOrientation) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDisplayPhysicalOrientation(outOrientation));
}

ndk::ScopedAStatus ComposerClient::getHdrCapabilities(
    int64_t displayId, HdrCapabilities* outCapabilities) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getHdrCapabilities(outCapabilities));
}

ndk::ScopedAStatus ComposerClient::getMaxVirtualDisplayCount(
    int32_t* outCount) {
  DEBUG_LOG("%s", __FUNCTION__);

  // Not supported.
  *outCount = 0;

  return ToBinderStatus(HWC3::Error::None);
}

ndk::ScopedAStatus ComposerClient::getPerFrameMetadataKeys(
    int64_t displayId, std::vector<PerFrameMetadataKey>* outKeys) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getPerFrameMetadataKeys(outKeys));
}

ndk::ScopedAStatus ComposerClient::getReadbackBufferAttributes(
    int64_t displayId, ReadbackBufferAttributes* outAttributes) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getReadbackBufferAttributes(outAttributes));
}

ndk::ScopedAStatus ComposerClient::getReadbackBufferFence(
    int64_t displayId, ndk::ScopedFileDescriptor* outAcquireFence) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getReadbackBufferFence(outAcquireFence));
}

ndk::ScopedAStatus ComposerClient::getRenderIntents(
    int64_t displayId, ColorMode mode, std::vector<RenderIntent>* outIntents) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getRenderIntents(mode, outIntents));
}

ndk::ScopedAStatus ComposerClient::getSupportedContentTypes(
    int64_t displayId, std::vector<ContentType>* outTypes) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getSupportedContentTypes(outTypes));
}

ndk::ScopedAStatus ComposerClient::getDisplayDecorationSupport(
    int64_t displayId,
    std::optional<common::DisplayDecorationSupport>* outSupport) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getDecorationSupport(outSupport));
}

ndk::ScopedAStatus ComposerClient::registerCallback(
    const std::shared_ptr<IComposerCallback>& callback) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  const bool isFirstRegisterCallback = mCallbacks == nullptr;

  mCallbacks = callback;

  for (auto& [_, display] : mDisplays) {
    display->registerCallback(callback);
  }

  if (isFirstRegisterCallback) {
    lock.unlock();
    for (auto& [displayId, _] : mDisplays) {
      mCallbacks->onHotplug(displayId, /*connected=*/true);
    }
  }

  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ComposerClient::setActiveConfig(int64_t displayId,
                                                   int32_t configId) {
  DEBUG_LOG("%s display:%" PRIu64 " config:%" PRIu32, __FUNCTION__, displayId,
            configId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setActiveConfig(configId));
}

ndk::ScopedAStatus ComposerClient::setActiveConfigWithConstraints(
    int64_t displayId, int32_t configId,
    const VsyncPeriodChangeConstraints& constraints,
    VsyncPeriodChangeTimeline* outTimeline) {
  DEBUG_LOG("%s display:%" PRIu64 " config:%" PRIu32, __FUNCTION__, displayId,
            configId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setActiveConfigWithConstraints(
      configId, constraints, outTimeline));
}

ndk::ScopedAStatus ComposerClient::setBootDisplayConfig(int64_t displayId,
                                                        int32_t configId) {
  DEBUG_LOG("%s display:%" PRIu64 " config:%" PRIu32, __FUNCTION__, displayId,
            configId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setBootConfig(configId));
}

ndk::ScopedAStatus ComposerClient::clearBootDisplayConfig(int64_t displayId) {
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->clearBootConfig());
}

ndk::ScopedAStatus ComposerClient::getPreferredBootDisplayConfig(
    int64_t displayId, int32_t* outConfigId) {
  DEBUG_LOG("%s display:%" PRIu64, __FUNCTION__, displayId);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->getPreferredBootConfig(outConfigId));
}

ndk::ScopedAStatus ComposerClient::setAutoLowLatencyMode(int64_t displayId,
                                                         bool on) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setAutoLowLatencyMode(on));
}

ndk::ScopedAStatus ComposerClient::setClientTargetSlotCount(int64_t displayId,
                                                            int32_t count) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(
      mResources->setDisplayClientTargetCacheSize(displayId, count));
}

ndk::ScopedAStatus ComposerClient::setColorMode(int64_t displayId,
                                                ColorMode mode,
                                                RenderIntent intent) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setColorMode(mode, intent));
}

ndk::ScopedAStatus ComposerClient::setContentType(int64_t displayId,
                                                  ContentType type) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setContentType(type));
}

ndk::ScopedAStatus ComposerClient::setDisplayedContentSamplingEnabled(
    int64_t displayId, bool enable, FormatColorComponent componentMask,
    int64_t maxFrames) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setDisplayedContentSamplingEnabled(
      enable, componentMask, maxFrames));
}

ndk::ScopedAStatus ComposerClient::setPowerMode(int64_t displayId,
                                                PowerMode mode) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setPowerMode(mode));
}

ndk::ScopedAStatus ComposerClient::setReadbackBuffer(
    int64_t displayId,
    const aidl::android::hardware::common::NativeHandle& buffer,
    const ndk::ScopedFileDescriptor& releaseFence) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  // Owned by mResources.
  buffer_handle_t importedBuffer = nullptr;

  auto releaser = mResources->createReleaser(true /* isBuffer */);
  auto error = mResources->getDisplayReadbackBuffer(
      displayId, buffer, &importedBuffer, releaser.get());
  if (error != HWC3::Error::None) {
    ALOGE("%s: failed to get readback buffer from resources.", __FUNCTION__);
    return ToBinderStatus(error);
  }

  error = display->setReadbackBuffer(importedBuffer, releaseFence);
  if (error != HWC3::Error::None) {
    ALOGE("%s: failed to set readback buffer to display.", __FUNCTION__);
    return ToBinderStatus(error);
  }

  return ToBinderStatus(HWC3::Error::None);
}

ndk::ScopedAStatus ComposerClient::setVsyncEnabled(int64_t displayId,
                                                   bool enabled) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setVsyncEnabled(enabled));
}

ndk::ScopedAStatus ComposerClient::setIdleTimerEnabled(int64_t displayId,
                                                       int32_t timeoutMs) {
  DEBUG_LOG("%s", __FUNCTION__);

  std::unique_lock<std::mutex> lock(mStateMutex);

  GET_DISPLAY_OR_RETURN_ERROR();

  return ToBinderStatus(display->setIdleTimerEnabled(timeoutMs));
}

ndk::SpAIBinder ComposerClient::createBinder() {
  auto binder = BnComposerClient::createBinder();
  AIBinder_setInheritRt(binder.get(), true);
  return binder;
}

namespace {

#define DISPATCH_LAYER_COMMAND(layerCmd, display, layer, field, funcName)     \
  do {                                                                        \
    if (layerCmd.field) {                                                     \
      ComposerClient::executeLayerCommandSetLayer##funcName(display, layer,   \
                                                            *layerCmd.field); \
    }                                                                         \
  } while (0)

#define DISPATCH_DISPLAY_COMMAND(displayCmd, display, field, funcName) \
  do {                                                                 \
    if (displayCmd.field) {                                            \
      executeDisplayCommand##funcName(display, *displayCmd.field);     \
    }                                                                  \
  } while (0)

#define DISPATCH_DISPLAY_BOOL_COMMAND(displayCmd, display, field, funcName) \
  do {                                                                      \
    if (displayCmd.field) {                                                 \
      executeDisplayCommand##funcName(display);                             \
    }                                                                       \
  } while (0)

#define DISPATCH_DISPLAY_BOOL_COMMAND_AND_DATA(displayCmd, display, field, \
                                               data, funcName)             \
  do {                                                                     \
    if (displayCmd.field) {                                                \
      executeDisplayCommand##funcName(display, displayCmd.data);           \
    }                                                                      \
  } while (0)

#define LOG_DISPLAY_COMMAND_ERROR(display, error)                 \
  do {                                                            \
    const std::string errorString = toString(error);              \
    ALOGE("%s: display:%" PRId64 " failed with:%s", __FUNCTION__, \
          display->getId(), errorString.c_str());                 \
  } while (0)

#define LOG_LAYER_COMMAND_ERROR(display, layer, error)                \
  do {                                                                \
    const std::string errorString = toString(error);                  \
    ALOGE("%s: display:%" PRId64 " layer:%" PRId64 " failed with:%s", \
          __FUNCTION__, display->getId(), layer->getId(),             \
          errorString.c_str());                                       \
  } while (0)

}  // namespace

void ComposerClient::executeDisplayCommand(
    const DisplayCommand& displayCommand) {
  Display* display = getDisplay(displayCommand.display);
  if (display == nullptr) {
    mCommandResults->addError(HWC3::Error::BadDisplay);
    return;
  }

  for (const LayerCommand& layerCmd : displayCommand.layers) {
    executeLayerCommand(display, layerCmd);
  }

  DISPATCH_DISPLAY_COMMAND(displayCommand, display, colorTransformMatrix,
                           SetColorTransform);
  DISPATCH_DISPLAY_COMMAND(displayCommand, display, brightness, SetBrightness);
  DISPATCH_DISPLAY_COMMAND(displayCommand, display, clientTarget,
                           SetClientTarget);
  DISPATCH_DISPLAY_COMMAND(displayCommand, display, virtualDisplayOutputBuffer,
                           SetOutputBuffer);
  DISPATCH_DISPLAY_BOOL_COMMAND_AND_DATA(displayCommand, display,
                                         validateDisplay, expectedPresentTime,
                                         ValidateDisplay);
  DISPATCH_DISPLAY_BOOL_COMMAND(displayCommand, display, acceptDisplayChanges,
                                AcceptDisplayChanges);
  DISPATCH_DISPLAY_BOOL_COMMAND(displayCommand, display, presentDisplay,
                                PresentDisplay);
  DISPATCH_DISPLAY_BOOL_COMMAND_AND_DATA(
      displayCommand, display, presentOrValidateDisplay, expectedPresentTime,
      PresentOrValidateDisplay);
}

void ComposerClient::executeLayerCommand(Display* display,
                                         const LayerCommand& layerCommand) {
  Layer* layer = display->getLayer(layerCommand.layer);
  if (layer == nullptr) {
    mCommandResults->addError(HWC3::Error::BadLayer);
    return;
  }

  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, cursorPosition,
                         CursorPosition);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, buffer, Buffer);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, damage, SurfaceDamage);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, blendMode, BlendMode);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, color, Color);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, composition,
                         Composition);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, dataspace, Dataspace);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, displayFrame,
                         DisplayFrame);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, planeAlpha, PlaneAlpha);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, sidebandStream,
                         SidebandStream);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, sourceCrop, SourceCrop);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, transform, Transform);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, visibleRegion,
                         VisibleRegion);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, z, ZOrder);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, colorTransform,
                         ColorTransform);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, brightness, Brightness);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, perFrameMetadata,
                         PerFrameMetadata);
  DISPATCH_LAYER_COMMAND(layerCommand, display, layer, perFrameMetadataBlob,
                         PerFrameMetadataBlobs);
}

void ComposerClient::executeDisplayCommandSetColorTransform(
    Display* display, const std::vector<float>& matrix) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = display->setColorTransform(matrix);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeDisplayCommandSetBrightness(
    Display* display, const DisplayBrightness& brightness) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = display->setBrightness(brightness.brightness);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeDisplayCommandSetClientTarget(
    Display* display, const ClientTarget& clientTarget) {
  DEBUG_LOG("%s", __FUNCTION__);

  // Owned by mResources.
  buffer_handle_t importedBuffer = nullptr;

  auto releaser = mResources->createReleaser(/*isBuffer=*/true);
  auto error = mResources->getDisplayClientTarget(
      display->getId(), clientTarget.buffer, &importedBuffer, releaser.get());
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
    return;
  }

  error = display->setClientTarget(importedBuffer, clientTarget.buffer.fence,
                                   clientTarget.dataspace, clientTarget.damage);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
    return;
  }
}

void ComposerClient::executeDisplayCommandSetOutputBuffer(
    Display* display, const Buffer& buffer) {
  DEBUG_LOG("%s", __FUNCTION__);

  // Owned by mResources.
  buffer_handle_t importedBuffer = nullptr;

  auto releaser = mResources->createReleaser(/*isBuffer=*/true);
  auto error = mResources->getDisplayOutputBuffer(
      display->getId(), buffer, &importedBuffer, releaser.get());
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
    return;
  }

  error = display->setOutputBuffer(importedBuffer, buffer.fence);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
    return;
  }
}

void ComposerClient::executeDisplayCommandValidateDisplay(
    Display* display,
    const std::optional<ClockMonotonicTimestamp> expectedPresentTime) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = display->setExpectedPresentTime(expectedPresentTime);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  }

  DisplayChanges changes;

  error = display->validate(&changes);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  } else {
    mCommandResults->addChanges(changes);
  }

  mResources->setDisplayMustValidateState(display->getId(), false);
}

void ComposerClient::executeDisplayCommandAcceptDisplayChanges(
    Display* display) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = display->acceptChanges();
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeDisplayCommandPresentOrValidateDisplay(
    Display* display,
    const std::optional<ClockMonotonicTimestamp> expectedPresentTime) {
  DEBUG_LOG("%s", __FUNCTION__);

  // TODO: Support SKIP_VALIDATE.

  auto error = display->setExpectedPresentTime(expectedPresentTime);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  }

  DisplayChanges changes;

  error = display->validate(&changes);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  } else {
    const int64_t displayId = display->getId();
    mCommandResults->addChanges(changes);
    mCommandResults->addPresentOrValidateResult(
        displayId, PresentOrValidate::Result::Validated);
  }

  mResources->setDisplayMustValidateState(display->getId(), false);
}

void ComposerClient::executeDisplayCommandPresentDisplay(Display* display) {
  DEBUG_LOG("%s", __FUNCTION__);

  if (mResources->mustValidateDisplay(display->getId())) {
    ALOGE("%s: display:%" PRIu64 " not validated", __FUNCTION__,
          display->getId());
    mCommandResults->addError(HWC3::Error::NotValidated);
    return;
  }

  ::android::base::unique_fd displayFence;
  std::unordered_map<int64_t, ::android::base::unique_fd> layerFences;

  auto error = display->present(&displayFence, &layerFences);
  if (error != HWC3::Error::None) {
    LOG_DISPLAY_COMMAND_ERROR(display, error);
    mCommandResults->addError(error);
  } else {
    const int64_t displayId = display->getId();
    mCommandResults->addPresentFence(displayId, std::move(displayFence));
    mCommandResults->addReleaseFences(displayId, std::move(layerFences));
  }
}

void ComposerClient::executeLayerCommandSetLayerCursorPosition(
    Display* display, Layer* layer, const common::Point& cursorPosition) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setCursorPosition(cursorPosition);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerBuffer(Display* display,
                                                       Layer* layer,
                                                       const Buffer& buffer) {
  DEBUG_LOG("%s", __FUNCTION__);

  // Owned by mResources.
  buffer_handle_t importedBuffer = nullptr;

  auto releaser = mResources->createReleaser(/*isBuffer=*/true);
  auto error =
      mResources->getLayerBuffer(display->getId(), layer->getId(), buffer,
                                 &importedBuffer, releaser.get());
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
    return;
  }

  error = layer->setBuffer(importedBuffer, buffer.fence);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerSurfaceDamage(
    Display* display, Layer* layer,
    const std::vector<std::optional<common::Rect>>& damage) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setSurfaceDamage(damage);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerBlendMode(
    Display* display, Layer* layer, const ParcelableBlendMode& blendMode) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setBlendMode(blendMode.blendMode);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerColor(Display* display,
                                                      Layer* layer,
                                                      const Color& color) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setColor(color);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerComposition(
    Display* display, Layer* layer, const ParcelableComposition& composition) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setCompositionType(composition.composition);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerDataspace(
    Display* display, Layer* layer, const ParcelableDataspace& dataspace) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setDataspace(dataspace.dataspace);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerDisplayFrame(
    Display* display, Layer* layer, const common::Rect& rect) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setDisplayFrame(rect);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerPlaneAlpha(
    Display* display, Layer* layer, const PlaneAlpha& planeAlpha) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setPlaneAlpha(planeAlpha.alpha);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerSidebandStream(
    Display* display, Layer* layer,
    const aidl::android::hardware::common::NativeHandle& handle) {
  DEBUG_LOG("%s", __FUNCTION__);

  // Owned by mResources.
  buffer_handle_t importedStream = nullptr;

  auto releaser = mResources->createReleaser(/*isBuffer=*/false);
  auto error = mResources->getLayerSidebandStream(
      display->getId(), layer->getId(), handle, &importedStream,
      releaser.get());
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
    return;
  }

  error = layer->setSidebandStream(importedStream);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerSourceCrop(
    Display* display, Layer* layer, const common::FRect& sourceCrop) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setSourceCrop(sourceCrop);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerTransform(
    Display* display, Layer* layer, const ParcelableTransform& transform) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setTransform(transform.transform);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerVisibleRegion(
    Display* display, Layer* layer,
    const std::vector<std::optional<common::Rect>>& visibleRegion) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setVisibleRegion(visibleRegion);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerZOrder(Display* display,
                                                       Layer* layer,
                                                       const ZOrder& zOrder) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setZOrder(zOrder.z);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerPerFrameMetadata(
    Display* display, Layer* layer,
    const std::vector<std::optional<PerFrameMetadata>>& perFrameMetadata) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setPerFrameMetadata(perFrameMetadata);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerColorTransform(
    Display* display, Layer* layer, const std::vector<float>& colorTransform) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setColorTransform(colorTransform);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerBrightness(
    Display* display, Layer* layer, const LayerBrightness& brightness) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setBrightness(brightness.brightness);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

void ComposerClient::executeLayerCommandSetLayerPerFrameMetadataBlobs(
    Display* display, Layer* layer,
    const std::vector<std::optional<PerFrameMetadataBlob>>&
        perFrameMetadataBlob) {
  DEBUG_LOG("%s", __FUNCTION__);

  auto error = layer->setPerFrameMetadataBlobs(perFrameMetadataBlob);
  if (error != HWC3::Error::None) {
    LOG_LAYER_COMMAND_ERROR(display, layer, error);
    mCommandResults->addError(error);
  }
}

Display* ComposerClient::getDisplay(int64_t displayId) {
  auto it = mDisplays.find(displayId);
  if (it == mDisplays.end()) {
    ALOGE("%s: no display:%" PRIu64, __FUNCTION__, displayId);
    return nullptr;
  }
  return it->second.get();
}

HWC3::Error ComposerClient::createDisplaysLocked() {
  DEBUG_LOG("%s", __FUNCTION__);

  if (!mComposer) {
    ALOGE("%s composer not initialized!", __FUNCTION__);
    return HWC3::Error::NoResources;
  }

  std::vector<DisplayMultiConfigs> displays;

  HWC3::Error error = findDisplays(mComposer->getDrmPresenter(), &displays);
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to find display configs", __FUNCTION__);
    return error;
  }

  for (const auto& iter : displays) {
    error =
        createDisplayLocked(iter.displayId, iter.activeConfigId, iter.configs);
    if (error != HWC3::Error::None) {
      ALOGE("%s failed to create display from config", __FUNCTION__);
      return error;
    }
  }

  return HWC3::Error::None;
}

HWC3::Error ComposerClient::createDisplayLocked(
    int64_t displayId, int32_t activeConfigId,
    const std::vector<DisplayConfig>& configs) {
  DEBUG_LOG("%s", __FUNCTION__);

  if (!mComposer) {
    ALOGE("%s composer not initialized!", __FUNCTION__);
    return HWC3::Error::NoResources;
  }

  auto display = std::make_unique<Display>(mComposer, displayId);
  if (display == nullptr) {
    ALOGE("%s failed to allocate display", __FUNCTION__);
    return HWC3::Error::NoResources;
  }

  HWC3::Error error = display->init(configs, activeConfigId);
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to initialize display:%" PRIu64, __FUNCTION__, displayId);
    return error;
  }

  error = mComposer->onDisplayCreate(display.get());
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to register display:%" PRIu64 " with composer",
          __FUNCTION__, displayId);
    return error;
  }

  DEBUG_LOG("%s: adding display:%" PRIu64, __FUNCTION__, displayId);
  mDisplays.emplace(displayId, std::move(display));

  error = mResources->addPhysicalDisplay(displayId);
  if (error != HWC3::Error::None) {
    ALOGE("%s failed to initialize display:%" PRIu64 " resources", __FUNCTION__,
          displayId);
    return error;
  }

  return HWC3::Error::None;
}

HWC3::Error ComposerClient::destroyDisplaysLocked() {
  DEBUG_LOG("%s", __FUNCTION__);

  std::vector<int64_t> displayIds;
  for (const auto& [displayId, _] : mDisplays) {
    displayIds.push_back(displayId);
  }
  for (const int64_t displayId : displayIds) {
    destroyDisplayLocked(displayId);
  }

  return HWC3::Error::None;
}

HWC3::Error ComposerClient::destroyDisplayLocked(int64_t displayId) {
  DEBUG_LOG("%s display:%" PRId64, __FUNCTION__, displayId);

  auto it = mDisplays.find(displayId);
  if (it == mDisplays.end()) {
    ALOGE("%s: display:%" PRId64 " no such display?", __FUNCTION__, displayId);
    return HWC3::Error::BadDisplay;
  }

  HWC3::Error error = mComposer->onDisplayDestroy(it->second.get());
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRId64 " failed to destroy with frame composer",
          __FUNCTION__, displayId);
  }

  error = mResources->removeDisplay(displayId);
  if (error != HWC3::Error::None) {
    ALOGE("%s: display:%" PRId64 " failed to destroy with resources",
          __FUNCTION__, displayId);
  }

  mDisplays.erase(it);

  return HWC3::Error::None;
}

HWC3::Error ComposerClient::handleHotplug(bool connected, uint32_t id,
                                          uint32_t width, uint32_t height,
                                          uint32_t dpiX, uint32_t dpiY,
                                          uint32_t refreshRate) {
  std::unique_lock<std::mutex> lock(mStateMutex);

  if (!mCallbacks) {
    return HWC3::Error::None;
  }

  const int64_t displayId = static_cast<int64_t>(id);

  Display* display = getDisplay(displayId);
  if (display != nullptr) {
    ALOGI("Disconnecting display:%" PRIu64, displayId);
    mCallbacks->onHotplug(displayId, /*connected=*/false);

    destroyDisplayLocked(displayId);
  }

  if (connected) {
    const int32_t configId = static_cast<int32_t>(id);
    const std::vector<DisplayConfig> configs = {
        DisplayConfig(configId, static_cast<int>(width),
                      static_cast<int>(height), static_cast<int>(dpiX),
                      static_cast<int>(dpiY), static_cast<int>(refreshRate))};

    createDisplayLocked(displayId, configId, configs);

    ALOGI("Connecting display:%" PRIu32 " w:%" PRIu32 " h:%" PRIu32
          " dpiX:%" PRIu32 " dpiY %" PRIu32 "fps %" PRIu32,
          id, width, height, dpiX, dpiY, refreshRate);

    mCallbacks->onHotplug(displayId, /*connected=*/true);
  }

  return HWC3::Error::None;
}

}  // namespace aidl::android::hardware::graphics::composer3::impl