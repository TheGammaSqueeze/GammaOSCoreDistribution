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
#include <aidl/android/security/dice/IDiceNode.h>
#include <aidl/android/system/virtualmachineservice/IVirtualMachineService.h>
#include <aidl/com/android/microdroid/testservice/BnTestService.h>
#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/result.h>
#include <android/binder_auto_utils.h>
#include <android/binder_manager.h>
#include <fcntl.h>
#include <fsverity_digests.pb.h>
#include <linux/vm_sockets.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include <binder_rpc_unstable.hpp>
#include <string>

using aidl::android::hardware::security::dice::BccHandover;
using aidl::android::security::dice::IDiceNode;

using aidl::android::system::virtualmachineservice::IVirtualMachineService;

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;

extern void testlib_sub();

namespace {

template <typename T>
Result<T> report_test(std::string name, Result<T> result) {
    auto property = "debug.microdroid.test." + name;
    std::stringstream outcome;
    if (result.ok()) {
        outcome << "PASS";
    } else {
        outcome << "FAIL: " << result.error();
        // Pollute stderr with the error in case the property is truncated.
        std::cerr << "[" << name << "] test failed: " << result.error() << "\n";
    }
    __system_property_set(property.c_str(), outcome.str().c_str());
    return result;
}

Result<void> start_test_service() {
    class TestService : public aidl::com::android::microdroid::testservice::BnTestService {
        ndk::ScopedAStatus addInteger(int32_t a, int32_t b, int32_t* out) override {
            *out = a + b;
            return ndk::ScopedAStatus::ok();
        }

        ndk::ScopedAStatus readProperty(const std::string& prop, std::string* out) override {
            *out = android::base::GetProperty(prop, "");
            if (out->empty()) {
                std::string msg = "cannot find property " + prop;
                return ndk::ScopedAStatus::fromExceptionCodeWithMessage(EX_SERVICE_SPECIFIC,
                                                                        msg.c_str());
            }

            return ndk::ScopedAStatus::ok();
        }

        ndk::ScopedAStatus insecurelyExposeSealingCdi(std::vector<uint8_t>* out) override {
            ndk::SpAIBinder binder(AServiceManager_getService("android.security.dice.IDiceNode"));
            auto service = IDiceNode::fromBinder(binder);
            if (service == nullptr) {
                return ndk::ScopedAStatus::
                        fromServiceSpecificErrorWithMessage(0, "Failed to find diced");
            }
            BccHandover handover;
            auto deriveStatus = service->derive({}, &handover);
            if (!deriveStatus.isOk()) {
                return ndk::ScopedAStatus::fromServiceSpecificErrorWithMessage(0,
                                                                               "Failed call diced");
            }
            *out = {handover.cdiSeal.begin(), handover.cdiSeal.end()};
            return ndk::ScopedAStatus::ok();
        }

        ndk::ScopedAStatus insecurelyExposeAttestationCdi(std::vector<uint8_t>* out) override {
            ndk::SpAIBinder binder(AServiceManager_getService("android.security.dice.IDiceNode"));
            auto service = IDiceNode::fromBinder(binder);
            if (service == nullptr) {
                return ndk::ScopedAStatus::
                        fromServiceSpecificErrorWithMessage(0, "Failed to find diced");
            }
            BccHandover handover;
            auto deriveStatus = service->derive({}, &handover);
            if (!deriveStatus.isOk()) {
                return ndk::ScopedAStatus::fromServiceSpecificErrorWithMessage(0,
                                                                               "Failed call diced");
            }
            *out = {handover.cdiAttest.begin(), handover.cdiAttest.end()};
            return ndk::ScopedAStatus::ok();
        }

        ndk::ScopedAStatus getBcc(std::vector<uint8_t>* out) override {
            ndk::SpAIBinder binder(AServiceManager_getService("android.security.dice.IDiceNode"));
            auto service = IDiceNode::fromBinder(binder);
            if (service == nullptr) {
                return ndk::ScopedAStatus::
                        fromServiceSpecificErrorWithMessage(0, "Failed to find diced");
            }
            BccHandover handover;
            auto deriveStatus = service->derive({}, &handover);
            if (!deriveStatus.isOk()) {
                return ndk::ScopedAStatus::fromServiceSpecificErrorWithMessage(0,
                                                                               "Failed call diced");
            }
            *out = {handover.bcc.data.begin(), handover.bcc.data.end()};
            return ndk::ScopedAStatus::ok();
        }
    };
    auto testService = ndk::SharedRefBase::make<TestService>();

    auto callback = []([[maybe_unused]] void* param) {
        // Tell microdroid_manager that we're ready.
        // Failing to notify is not a fatal error; the payload can continue.
        ndk::SpAIBinder binder(
                RpcClient(VMADDR_CID_HOST, IVirtualMachineService::VM_BINDER_SERVICE_PORT));
        auto virtualMachineService = IVirtualMachineService::fromBinder(binder);
        if (virtualMachineService == nullptr) {
            std::cerr << "failed to connect VirtualMachineService";
            return;
        }
        if (!virtualMachineService->notifyPayloadReady().isOk()) {
            std::cerr << "failed to notify payload ready to virtualizationservice";
        }
    };

    if (!RunRpcServerCallback(testService->asBinder().get(), testService->SERVICE_PORT, callback,
                              nullptr)) {
        return Error() << "RPC Server failed to run";
    }

    return {};
}

Result<void> verify_apk() {
    const char* path = "/mnt/extra-apk/0/assets/build_manifest.pb";

    std::string str;
    if (!android::base::ReadFileToString(path, &str)) {
        return ErrnoError() << "failed to read build_manifest.pb";
    }

    if (!android::security::fsverity::FSVerityDigests().ParseFromString(str)) {
        return Error() << "invalid build_manifest.pb";
    }

    return {};
}

} // Anonymous namespace

extern "C" int android_native_main(int argc, char* argv[]) {
    // disable buffering to communicate seamlessly
    setvbuf(stdin, nullptr, _IONBF, 0);
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);

    printf("Hello Microdroid ");
    for (int i = 0; i < argc; i++) {
        printf("%s", argv[i]);
        bool last = i == (argc - 1);
        if (!last) {
            printf(" ");
        }
    }
    testlib_sub();
    printf("\n");

    // Extra apks may be missing; this is not a fatal error
    report_test("extra_apk", verify_apk());

    __system_property_set("debug.microdroid.app.run", "true");

    if (auto res = start_test_service(); res.ok()) {
        return 0;
    } else {
        std::cerr << "starting service failed: " << res.error();
        return 1;
    }
}
