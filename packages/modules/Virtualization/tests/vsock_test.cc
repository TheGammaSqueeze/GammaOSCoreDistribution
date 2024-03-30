/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include <linux/kvm.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

// Needs to be included after sys/socket.h
#include <linux/vm_sockets.h>

#include <algorithm>
#include <array>
#include <iostream>
#include <optional>

#include "android-base/file.h"
#include "android-base/logging.h"
#include "android-base/parseint.h"
#include "android-base/unique_fd.h"
#include "android/system/virtualizationservice/VirtualMachineConfig.h"
#include "android/system/virtualizationservice/VirtualMachineRawConfig.h"
#include "virt/VirtualizationTest.h"

#define KVM_CAP_ARM_PROTECTED_VM 0xffbadab1

using namespace android::base;
using namespace android::os;

namespace virt {

static constexpr int kGuestPort = 45678;
static constexpr const char kVmKernelPath[] = "/data/local/tmp/virt-test/kernel";
static constexpr const char kVmInitrdPath[] = "/data/local/tmp/virt-test/initramfs";
static constexpr const char kVmParams[] = "rdinit=/bin/init bin/vsock_client 2 45678 HelloWorld";
static constexpr const char kTestMessage[] = "HelloWorld";
static constexpr const char kPlatformVersion[] = "~1.0";

/** Returns true if the kernel supports unprotected VMs. */
bool isUnprotectedVmSupported() {
    return android::sysprop::HypervisorProperties::hypervisor_vm_supported().value_or(false);
}

TEST_F(VirtualizationTest, TestVsock) {
    if (!isUnprotectedVmSupported()) {
        GTEST_SKIP() << "Skipping as unprotected VMs are not supported on this device.";
    }

    binder::Status status;

    unique_fd server_fd(TEMP_FAILURE_RETRY(socket(AF_VSOCK, SOCK_STREAM, 0)));
    ASSERT_GE(server_fd, 0) << strerror(errno);

    struct sockaddr_vm server_sa = (struct sockaddr_vm){
            .svm_family = AF_VSOCK,
            .svm_port = kGuestPort,
            .svm_cid = VMADDR_CID_ANY,
    };

    int ret = TEMP_FAILURE_RETRY(bind(server_fd, (struct sockaddr *)&server_sa, sizeof(server_sa)));
    ASSERT_EQ(ret, 0) << strerror(errno);

    LOG(INFO) << "Listening on port " << kGuestPort << "...";
    ret = TEMP_FAILURE_RETRY(listen(server_fd, 1));
    ASSERT_EQ(ret, 0) << strerror(errno);

    VirtualMachineRawConfig raw_config;
    raw_config.kernel = ParcelFileDescriptor(unique_fd(open(kVmKernelPath, O_RDONLY | O_CLOEXEC)));
    raw_config.initrd = ParcelFileDescriptor(unique_fd(open(kVmInitrdPath, O_RDONLY | O_CLOEXEC)));
    raw_config.params = kVmParams;
    raw_config.protectedVm = false;
    raw_config.platformVersion = kPlatformVersion;

    VirtualMachineConfig config(std::move(raw_config));
    sp<IVirtualMachine> vm;
    status = mVirtualizationService->createVm(config, std::nullopt, std::nullopt, &vm);
    ASSERT_TRUE(status.isOk()) << "Error creating VM: " << status;

    int32_t cid;
    status = vm->getCid(&cid);
    ASSERT_TRUE(status.isOk()) << "Error getting CID: " << status;
    LOG(INFO) << "VM starting with CID " << cid;

    status = vm->start();
    ASSERT_TRUE(status.isOk()) << "Error starting VM: " << status;

    LOG(INFO) << "Accepting connection...";
    struct sockaddr_vm client_sa;
    socklen_t client_sa_len = sizeof(client_sa);
    unique_fd client_fd(
            TEMP_FAILURE_RETRY(accept(server_fd, (struct sockaddr *)&client_sa, &client_sa_len)));
    ASSERT_GE(client_fd, 0) << strerror(errno);
    LOG(INFO) << "Connection from CID " << client_sa.svm_cid << " on port " << client_sa.svm_port;

    LOG(INFO) << "Reading message from the client...";
    std::string msg;
    ASSERT_TRUE(ReadFdToString(client_fd, &msg));

    LOG(INFO) << "Received message: " << msg;
    ASSERT_EQ(msg, kTestMessage);
}

TEST_F(VirtualizationTest, RejectIncompatiblePlatformVersion) {
    VirtualMachineRawConfig raw_config;
    raw_config.kernel = ParcelFileDescriptor(unique_fd(open(kVmKernelPath, O_RDONLY | O_CLOEXEC)));
    raw_config.initrd = ParcelFileDescriptor(unique_fd(open(kVmInitrdPath, O_RDONLY | O_CLOEXEC)));
    raw_config.params = kVmParams;
    raw_config.platformVersion = "~2.0"; // The current platform version is 1.0.0.

    VirtualMachineConfig config(std::move(raw_config));
    sp<IVirtualMachine> vm;
    auto status = mVirtualizationService->createVm(config, std::nullopt, std::nullopt, &vm);
    ASSERT_FALSE(status.isOk());
}

} // namespace virt
