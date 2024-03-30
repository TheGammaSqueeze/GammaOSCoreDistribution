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

#pragma once

#include <android-base/macros.h>
#include <android/hardware/automotive/can/1.0/ICloseHandle.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {
namespace utils {

struct CloseHandleWrapper {
    CloseHandleWrapper();
    CloseHandleWrapper(sp<ICloseHandle> handle);
    virtual ~CloseHandleWrapper();

    CloseHandleWrapper& operator=(CloseHandleWrapper&& other);

    void close();

  private:
    sp<ICloseHandle> mHandle;
    std::atomic<bool> mIsClosed = false;

    DISALLOW_COPY_AND_ASSIGN(CloseHandleWrapper);
};

}  // namespace utils
}  // namespace V1_0
}  // namespace can
}  // namespace automotive
}  // namespace hardware
}  // namespace android
