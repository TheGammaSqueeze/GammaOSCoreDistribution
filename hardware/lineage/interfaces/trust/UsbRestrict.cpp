/*
 * Copyright (C) 2019 The LineageOS Project
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
#include <fstream>

#include "UsbRestrict.h"

#include <android-base/logging.h>

namespace vendor {
namespace lineage {
namespace trust {
namespace V1_0 {
namespace implementation {

// Methods from ::vendor::lineage::trust::V1_0::IUsbRestrict follow.
Return<bool> UsbRestrict::isEnabled() {
    std::ifstream file(USB_CONTROL_PATH);
    std::string content;
    file >> content;
    file.close();
    return !file.fail() && content == USB_CONTROL_ENABLE;
}

Return<void> UsbRestrict::setEnabled(bool enabled) {
    std::ofstream file(USB_CONTROL_PATH);
    if (file.is_open()) {
        file << (enabled ? USB_CONTROL_ENABLE : USB_CONTROL_DISABLE);
        file.close();
    } else {
        LOG(ERROR) << "Failed to open " << USB_CONTROL_PATH << ", error=" << errno
                   << " (" << strerror(errno) << ")";
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace trust
}  // namespace lineage
}  // namespace vendor
