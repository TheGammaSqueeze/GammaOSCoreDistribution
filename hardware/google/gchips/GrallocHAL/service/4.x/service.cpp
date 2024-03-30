/*
 * Copyright (C) 2020 Arm Limited.
 * SPDX-License-Identifier: Apache-2.0
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
#define LOG_TAG "android.hardware.graphics.allocator@4.0-service"

#include <hidl/HidlTransportSupport.h>
#include <hidl/Status.h>
#include <utils/StrongPointer.h>

#include "4.x/GrallocAllocator.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::setMinSchedulerPolicy;
using arm::allocator::GrallocAllocator;

int main() {
    android::sp<GrallocAllocator> service = new GrallocAllocator();
    configureRpcThreadpool(4, true /* callerWillJoin */);

    if (!setMinSchedulerPolicy(service, SCHED_NORMAL, -20)) {
        ALOGW("Cannot bump allocator priority");
    }

    if (service->registerAsService() != android::OK) {
        ALOGE("Cannot register allocator service");
        return -EINVAL;
    }

    joinRpcThreadpool();
    return 0;
}
