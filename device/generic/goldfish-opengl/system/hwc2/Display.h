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

#ifndef ANDROID_HWC_DISPLAY_H
#define ANDROID_HWC_DISPLAY_H

#include <android-base/unique_fd.h>
#include <android/hardware/graphics/common/1.0/types.h>

#include <array>
#include <mutex>
#include <optional>
#include <set>
#include <thread>
#include <unordered_map>
#include <vector>

#include "Common.h"
#include "Composer.h"
#include "DisplayConfig.h"
#include "DisplayFinder.h"
#include "FencedBuffer.h"
#include "Layer.h"
#include "VsyncThread.h"

namespace android {

class Composer;
class Device;

struct ColorTransformWithMatrix {
  android::hardware::graphics::common::V1_0::ColorTransform transformType;
  std::optional<std::array<float, 16>> transformMatrixOpt;
};

class Display {
 public:
  Display(Composer* composer, hwc2_display_t id);
  ~Display();

  Display(const Display& display) = delete;
  Display& operator=(const Display& display) = delete;

  Display(Display&& display) = delete;
  Display& operator=(Display&& display) = delete;

  HWC2::Error init(
      const std::vector<DisplayConfig>& configs, hwc2_config_t activeConfigId,
      const std::optional<std::vector<uint8_t>>& edid = std::nullopt);

  HWC2::Error updateParameters(
      uint32_t width, uint32_t height, uint32_t dpiX, uint32_t dpiY,
      uint32_t refreshRateHz,
      const std::optional<std::vector<uint8_t>>& edid = std::nullopt);

  hwc2_display_t getId() const { return mId; }

  Layer* getLayer(hwc2_layer_t layerHandle);

  FencedBuffer& getClientTarget() { return mClientTarget; }
  buffer_handle_t waitAndGetClientTargetBuffer();

  const std::vector<Layer*>& getOrderedLayers() { return mOrderedLayers; }

  HWC2::Error acceptChanges();
  HWC2::Error createLayer(hwc2_layer_t* outLayerId);
  HWC2::Error destroyLayer(hwc2_layer_t layerId);
  HWC2::Error getActiveConfig(hwc2_config_t* outConfigId);
  HWC2::Error getDisplayAttribute(hwc2_config_t configId, int32_t attribute,
                                  int32_t* outValue);
  HWC2::Error getDisplayAttributeEnum(hwc2_config_t configId,
                                      HWC2::Attribute attribute,
                                      int32_t* outValue);
  HWC2::Error getChangedCompositionTypes(uint32_t* outNumElements,
                                         hwc2_layer_t* outLayers,
                                         int32_t* outTypes);
  HWC2::Error getColorModes(uint32_t* outNumModes, int32_t* outModes);
  HWC2::Error getConfigs(uint32_t* outNumConfigs, hwc2_config_t* outConfigIds);
  HWC2::Error getDozeSupport(int32_t* outSupport);
  HWC2::Error getHdrCapabilities(uint32_t* outNumTypes, int32_t* outTypes,
                                 float* outMaxLuminance,
                                 float* outMaxAverageLuminance,
                                 float* outMinLuminance);
  HWC2::Error getName(uint32_t* outSize, char* outName);
  HWC2::Error addReleaseFenceLocked(hwc2_layer_t layerId,
                                    base::unique_fd fence);
  HWC2::Error getReleaseFences(uint32_t* outNumElements,
                               hwc2_layer_t* outLayers, int32_t* outFences);
  HWC2::Error clearReleaseFencesAndIdsLocked();
  HWC2::Error getRequests(int32_t* outDisplayRequests, uint32_t* outNumElements,
                          hwc2_layer_t* outLayers, int32_t* outLayerRequests);
  HWC2::Error getType(int32_t* outType);
  HWC2::Error present(int32_t* outRetireFence);
  HWC2::Error setActiveConfig(hwc2_config_t configId);
  HWC2::Error setClientTarget(buffer_handle_t target, int32_t acquireFence,
                              int32_t dataspace, hwc_region_t damage);
  HWC2::Error setColorMode(int32_t mode);
  HWC2::Error setColorTransform(const float* matrix, int transform);
  HWC2::Error setColorTransformEnum(
      const float* matrix,
      android::hardware::graphics::common::V1_0::ColorTransform transform);
  bool hasColorTransform() const { return mColorTransform.has_value(); }
  ColorTransformWithMatrix getColorTransform() const {
    return *mColorTransform;
  }

  HWC2::Error setOutputBuffer(buffer_handle_t buffer, int32_t releaseFence);
  HWC2::Error setPowerMode(int32_t mode);
  HWC2::Error setVsyncEnabled(int32_t enabled);
  HWC2::Error setVsyncCallback(HWC2_PFN_VSYNC callback,
                               hwc2_callback_data_t data);
  HWC2::Error setVsync24Callback(HWC2_PFN_VSYNC_2_4 callback,
                                 hwc2_callback_data_t data);
  HWC2::Error getDisplayVsyncPeriod(hwc2_vsync_period_t* outVsyncPeriod);
  HWC2::Error setActiveConfigWithConstraints(
      hwc2_config_t config,
      hwc_vsync_period_change_constraints_t* vsyncPeriodChangeConstraints,
      hwc_vsync_period_change_timeline_t* timeline);
  HWC2::Error getDisplayConnectionType(uint32_t* outType);

  HWC2::Error validate(uint32_t* outNumTypes, uint32_t* outNumRequests);
  HWC2::Error updateLayerZ(hwc2_layer_t layerId, uint32_t z);
  HWC2::Error getClientTargetSupport(uint32_t width, uint32_t height,
                                     int32_t format, int32_t dataspace);
  HWC2::Error setEdid(std::vector<uint8_t> edid);
  HWC2::Error getDisplayIdentificationData(uint8_t* outPort,
                                           uint32_t* outDataSize,
                                           uint8_t* outData);
  HWC2::Error getDisplayCapabilities(uint32_t* outNumCapabilities,
                                     uint32_t* outCapabilities);
  HWC2::Error getDisplayBrightnessSupport(bool* out_support);
  HWC2::Error setDisplayBrightness(float brightness);
  HWC2::Error setAutoLowLatencyMode(bool on);
  HWC2::Error getSupportedContentTypes(
      uint32_t* outNumSupportedContentTypes,
      const uint32_t* outSupportedContentTypes);
  HWC2::Error setContentType(int32_t contentType);
  void lock() { mStateMutex.lock(); }
  void unlock() { mStateMutex.unlock(); }

 private:
  // Stores changes requested from the device upon calling prepare().
  // Handles change request to:
  //   - Layer composition type.
  //   - Layer hints.
  class Changes {
   public:
    uint32_t getNumTypes() const {
      return static_cast<uint32_t>(mTypeChanges.size());
    }

    uint32_t getNumLayerRequests() const {
      return static_cast<uint32_t>(mLayerRequests.size());
    }

    const std::unordered_map<hwc2_layer_t, HWC2::Composition>& getTypeChanges()
        const {
      return mTypeChanges;
    }

    const std::unordered_map<hwc2_layer_t, HWC2::LayerRequest>&
    getLayerRequests() const {
      return mLayerRequests;
    }

    void addTypeChange(hwc2_layer_t layerId, HWC2::Composition type) {
      mTypeChanges.insert({layerId, type});
    }

    void clearTypeChanges() { mTypeChanges.clear(); }

    void addLayerRequest(hwc2_layer_t layerId, HWC2::LayerRequest request) {
      mLayerRequests.insert({layerId, request});
    }

   private:
    std::unordered_map<hwc2_layer_t, HWC2::Composition> mTypeChanges;
    std::unordered_map<hwc2_layer_t, HWC2::LayerRequest> mLayerRequests;
  };

 private:
  // The state of this display should only be modified from
  // SurfaceFlinger's main loop, with the exception of when dump is
  // called. To prevent a bad state from crashing us during a dump
  // call, all public calls into Display must acquire this mutex.
  mutable std::recursive_mutex mStateMutex;

  Composer* mComposer = nullptr;
  const hwc2_display_t mId;
  std::string mName;
  HWC2::DisplayType mType = HWC2::DisplayType::Physical;
  HWC2::PowerMode mPowerMode = HWC2::PowerMode::Off;
  sp<VsyncThread> mVsyncThread;
  FencedBuffer mClientTarget;
  // Will only be non-null after the Display has been validated and
  // before it has been presented
  std::unique_ptr<Changes> mChanges;

  std::unordered_map<hwc2_layer_t, std::unique_ptr<Layer>> mLayers;
  // Ordered layers available after validate().
  std::vector<Layer*> mOrderedLayers;

  std::unordered_map<hwc2_layer_t, base::unique_fd> mReleaseFences;
  std::optional<hwc2_config_t> mActiveConfigId;
  std::unordered_map<hwc2_config_t, DisplayConfig> mConfigs;
  std::set<android_color_mode_t> mColorModes = {HAL_COLOR_MODE_NATIVE};
  android_color_mode_t mActiveColorMode = HAL_COLOR_MODE_NATIVE;
  std::optional<ColorTransformWithMatrix> mColorTransform;
  std::optional<std::vector<uint8_t>> mEdid;
};

}  // namespace android

#endif
