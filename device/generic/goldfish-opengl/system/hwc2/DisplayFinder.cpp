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

#include "DisplayFinder.h"

#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <device_config_shared.h>

#include "Common.h"
#include "HostUtils.h"

namespace android {
namespace {

constexpr int32_t HertzToPeriodNanos(uint32_t hertz) {
  return 1000 * 1000 * 1000 / hertz;
}

static int getVsyncHzFromProperty() {
  static constexpr const auto kVsyncProp = "ro.boot.qemu.vsync";

  const auto vsyncProp = android::base::GetProperty(kVsyncProp, "");
  DEBUG_LOG("%s: prop value is: %s", __FUNCTION__, vsyncProp.c_str());

  uint64_t vsyncPeriod;
  if (!android::base::ParseUint(vsyncProp, &vsyncPeriod)) {
    ALOGE("%s: failed to parse vsync period '%s', returning default 60",
          __FUNCTION__, vsyncProp.c_str());
    return 60;
  }

  return static_cast<int>(vsyncPeriod);
}

int32_t getVsyncForDisplay(DrmPresenter* drmPresenter, uint32_t displayId) {
  const int32_t vsyncPeriodForDisplay = drmPresenter->refreshRate(displayId);
  return vsyncPeriodForDisplay < 0
             ? HertzToPeriodNanos(getVsyncHzFromProperty())
             : HertzToPeriodNanos(vsyncPeriodForDisplay);
}

HWC2::Error findCuttlefishDisplays(std::vector<DisplayMultiConfigs>& displays) {
  DEBUG_LOG("%s", __FUNCTION__);

  // TODO: replace with initializing directly from DRM info.
  const auto deviceConfig = cuttlefish::GetDeviceConfig();

  hwc2_display_t displayId = 0;
  for (const auto& deviceDisplayConfig : deviceConfig.display_config()) {
    // crosvm does not fully support EDID yet, so we solely rely on the
    // device config for our vsync.
    const auto vsyncPeriodNanos =
        HertzToPeriodNanos(deviceDisplayConfig.refresh_rate_hz());

    DisplayMultiConfigs display = {
        .displayId = displayId,
        .activeConfigId = 0,
        .configs =
            {
                DisplayConfig(0,                             //
                              deviceDisplayConfig.width(),   //
                              deviceDisplayConfig.height(),  //
                              deviceDisplayConfig.dpi(),     //
                              deviceDisplayConfig.dpi(),     //
                              vsyncPeriodNanos),
            },
    };
    displays.push_back(display);
    ++displayId;
  }

  return HWC2::Error::None;
}

HWC2::Error findGoldfishPrimaryDisplay(
    DrmPresenter* drmPresenter, std::vector<DisplayMultiConfigs>& displays) {
  DEBUG_LOG("%s", __FUNCTION__);

  DEFINE_AND_VALIDATE_HOST_CONNECTION
  hostCon->lock();
  DisplayMultiConfigs display;
  display.displayId = 0;
  if (rcEnc->hasHWCMultiConfigs()) {
    int count = rcEnc->rcGetFBDisplayConfigsCount(rcEnc);
    if (count <= 0) {
      ALOGE("%s failed to allocate primary display, config count %d", __func__,
            count);
      return HWC2::Error::NoResources;
    }
    display.activeConfigId = rcEnc->rcGetFBDisplayActiveConfig(rcEnc);
    for (int configId = 0; configId < count; configId++) {
      display.configs.push_back(DisplayConfig(
          configId,                                                       //
          rcEnc->rcGetFBDisplayConfigsParam(rcEnc, configId, FB_WIDTH),   //
          rcEnc->rcGetFBDisplayConfigsParam(rcEnc, configId, FB_HEIGHT),  //
          rcEnc->rcGetFBDisplayConfigsParam(rcEnc, configId, FB_XDPI),    //
          rcEnc->rcGetFBDisplayConfigsParam(rcEnc, configId, FB_YDPI),    //
          getVsyncForDisplay(drmPresenter, display.displayId)             //
          ));
    }
  } else {
    display.activeConfigId = 0;
    display.configs.push_back(DisplayConfig(
        0,                                      //
        rcEnc->rcGetFBParam(rcEnc, FB_WIDTH),   //
        rcEnc->rcGetFBParam(rcEnc, FB_HEIGHT),  //
        rcEnc->rcGetFBParam(rcEnc, FB_XDPI),    //
        rcEnc->rcGetFBParam(rcEnc, FB_YDPI),    //
        getVsyncForDisplay(drmPresenter, 0)     //
        ));
  }
  hostCon->unlock();

  displays.push_back(display);

  return HWC2::Error::None;
}

HWC2::Error findGoldfishSecondaryDisplays(
    std::vector<DisplayMultiConfigs>& displays) {
  DEBUG_LOG("%s", __FUNCTION__);

  static constexpr const char kExternalDisplayProp[] =
      "hwservicemanager.external.displays";

  const auto propString = android::base::GetProperty(kExternalDisplayProp, "");
  DEBUG_LOG("%s: prop value is: %s", __FUNCTION__, propString.c_str());

  if (propString.empty()) {
    return HWC2::Error::None;
  }

  const std::vector<std::string> propStringParts =
      android::base::Split(propString, ",");
  if (propStringParts.size() % 5 != 0) {
    ALOGE("%s: Invalid syntax for system prop %s which is %s", __FUNCTION__,
          kExternalDisplayProp, propString.c_str());
    return HWC2::Error::BadParameter;
  }

  std::vector<int> propIntParts;
  for (const std::string& propStringPart : propStringParts) {
    int propIntPart;
    if (!android::base::ParseInt(propStringPart, &propIntPart)) {
      ALOGE("%s: Invalid syntax for system prop %s which is %s", __FUNCTION__,
            kExternalDisplayProp, propString.c_str());
      return HWC2::Error::BadParameter;
    }
    propIntParts.push_back(propIntPart);
  }

  hwc2_display_t secondaryDisplayId = 1;
  while (!propIntParts.empty()) {
    DisplayMultiConfigs display;
    display.displayId = secondaryDisplayId;
    display.activeConfigId = 0;
    display.configs.push_back(DisplayConfig(
        0,                                       //
        /*width=*/propIntParts[1],               //
        /*heighth=*/propIntParts[2],             //
        /*dpiXh=*/propIntParts[3],               //
        /*dpiYh=*/propIntParts[3],               //
        /*vsyncPeriod=*/HertzToPeriodNanos(160)  //
        ));
    displays.push_back(display);

    ++secondaryDisplayId;

    propIntParts.erase(propIntParts.begin(), propIntParts.begin() + 5);
  }

  return HWC2::Error::None;
}

HWC2::Error findGoldfishDisplays(DrmPresenter* drmPresenter,
                                 std::vector<DisplayMultiConfigs>& displays) {
  HWC2::Error error = findGoldfishPrimaryDisplay(drmPresenter, displays);
  if (error != HWC2::Error::None) {
    ALOGE("%s failed to find Goldfish primary display", __FUNCTION__);
    return error;
  }

  error = findGoldfishSecondaryDisplays(displays);
  if (error != HWC2::Error::None) {
    ALOGE("%s failed to find Goldfish secondary displays", __FUNCTION__);
  }

  return error;
}

// This is currently only used for Gem5 bring-up where virtio-gpu and drm
// are not currently available. For now, just return a placeholder display.
HWC2::Error findNoOpDisplays(std::vector<DisplayMultiConfigs>& displays) {
  displays.push_back(DisplayMultiConfigs{
      .displayId = 0,
      .activeConfigId = 0,
      .configs = {DisplayConfig(0,
                                /*width=*/720,                          //
                                /*heighth=*/1280,                       //
                                /*dpiXh=*/320,                          //
                                /*dpiYh=*/320,                          //
                                /*vsyncPeriod=*/HertzToPeriodNanos(30)  //
                                )},
  });

  return HWC2::Error::None;
}

}  // namespace

HWC2::Error findDisplays(DrmPresenter* drmPresenter,
                         std::vector<DisplayMultiConfigs>& displays) {
  HWC2::Error error = HWC2::Error::None;
  if (IsNoOpMode()) {
    error = findNoOpDisplays(displays);
  } else if (IsCuttlefish()) {
    error = findCuttlefishDisplays(displays);
  } else {
    error = findGoldfishDisplays(drmPresenter, displays);
  }

  if (error != HWC2::Error::None) {
    ALOGE("%s failed to find displays", __FUNCTION__);
    return error;
  }

  for (auto& display : displays) {
    DisplayConfig::addConfigGroups(&display.configs);
  }

  return HWC2::Error::None;
}

}  // namespace android
