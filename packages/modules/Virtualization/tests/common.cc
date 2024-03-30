/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android/sysprop/HypervisorProperties.sysprop.h>

#include "virt/VirtualizationTest.h"

using android::sysprop::HypervisorProperties::hypervisor_protected_vm_supported;
using android::sysprop::HypervisorProperties::hypervisor_vm_supported;

namespace {

bool isVmSupported() {
    bool has_capability = hypervisor_vm_supported().value_or(false) ||
            hypervisor_protected_vm_supported().value_or(false);
    if (!has_capability) {
        return false;
    }
    const std::array<const char *, 2> needed_files = {
            "/apex/com.android.virt/bin/crosvm",
            "/apex/com.android.virt/bin/virtualizationservice",
    };
    return std::all_of(needed_files.begin(), needed_files.end(),
                       [](const char *file) { return access(file, F_OK) == 0; });
}

} // namespace

namespace virt {

void VirtualizationTest::SetUp() {
    if (!isVmSupported()) {
        GTEST_SKIP() << "Device doesn't support KVM.";
    }

    mVirtualizationService = waitForService<IVirtualizationService>(
            String16("android.system.virtualizationservice"));
    ASSERT_NE(mVirtualizationService, nullptr);
}

} // namespace virt
