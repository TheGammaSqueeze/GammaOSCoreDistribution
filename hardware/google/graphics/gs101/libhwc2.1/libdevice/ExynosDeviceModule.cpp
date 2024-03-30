/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "ExynosDeviceModule.h"

#include "ExynosDisplayDrmInterfaceModule.h"

extern struct exynos_hwc_control exynosHWCControl;

using namespace gs101;

ExynosDeviceModule::ExynosDeviceModule() : ExynosDevice(), mDisplayColorLoader(DISPLAY_COLOR_LIB) {
    exynosHWCControl.skipStaticLayers = false;

    std::vector<displaycolor::DisplayInfo> display_info;
    for (uint32_t i = 0; i < mDisplays.size(); i++) {
        ExynosDisplay* display = mDisplays[i];
        ExynosDisplayDrmInterfaceModule* moduleDisplayInterface =
                (ExynosDisplayDrmInterfaceModule*)(display->mDisplayInterface.get());

        if (display->mType == HWC_DISPLAY_PRIMARY) {
            moduleDisplayInterface->getDisplayInfo(display_info);
        }
    }
    initDisplayColor(display_info);
}

ExynosDeviceModule::~ExynosDeviceModule() {
}

int ExynosDeviceModule::initDisplayColor(
        const std::vector<displaycolor::DisplayInfo>& display_info) {
    mDisplayColorInterface = mDisplayColorLoader.GetDisplayColorGS101(display_info);
    if (mDisplayColorInterface == nullptr) {
        ALOGW("%s failed to load displaycolor", __func__);
    }

    return NO_ERROR;
}
