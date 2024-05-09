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

#define LOG_TAG "rockchip.hardware.tv_input@1.0-service"

#include <rockchip/hardware/tv/input/1.0/ITvInput.h>

#include <hidl/LegacySupport.h>

#include "TvInputExt.h"

namespace android {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {
namespace implementation {

extern ITvInput* HIDL_FETCH_ITvInput(const char* /* name */);

} // namespace implementation
}
}
}
}
}

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using rockchip::hardware::tv::input::V1_0::implementation::TvInputExt;
using hwTvInput = rockchip::hardware::tv::input::V1_0::ITvInput;
using android::sp;

int main() {
    configureRpcThreadpool(1, true /*willJoinThreadpool*/);

    android::sp<hwTvInput> tv_input = new TvInputExt{
        android::hardware::tv::input::V1_0::implementation::HIDL_FETCH_ITvInput(
            nullptr)};
    auto ret = tv_input->registerAsService();
    if (ret != android::OK) {
        ALOGE("Open tv_input service failed, ret=%d", ret);
        return 1;
    }
    joinRpcThreadpool();
    return 1;
}
