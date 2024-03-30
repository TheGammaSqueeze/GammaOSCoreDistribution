/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "NetdUpdatable"

#include "NetdUpdatable.h"

#include <android-base/logging.h>
#include <netdutils/Status.h>

#include "NetdUpdatablePublic.h"

int libnetd_updatable_init(const char* cg2_path) {
    android::base::InitLogging(/*argv=*/nullptr);
    LOG(INFO) << __func__ << ": Initializing";

    android::net::gNetdUpdatable = android::net::NetdUpdatable::getInstance();
    android::netdutils::Status ret = android::net::gNetdUpdatable->mBpfHandler.init(cg2_path);
    if (!android::netdutils::isOk(ret)) {
        LOG(ERROR) << __func__ << ": BPF handler init failed";
        return -ret.code();
    }
    return 0;
}

int libnetd_updatable_tagSocket(int sockFd, uint32_t tag, uid_t chargeUid, uid_t realUid) {
    if (android::net::gNetdUpdatable == nullptr) return -EPERM;
    return android::net::gNetdUpdatable->mBpfHandler.tagSocket(sockFd, tag, chargeUid, realUid);
}

int libnetd_updatable_untagSocket(int sockFd) {
    if (android::net::gNetdUpdatable == nullptr) return -EPERM;
    return android::net::gNetdUpdatable->mBpfHandler.untagSocket(sockFd);
}

namespace android {
namespace net {

NetdUpdatable* gNetdUpdatable = nullptr;

NetdUpdatable* NetdUpdatable::getInstance() {
    // Instantiated on first use.
    static NetdUpdatable instance;
    return &instance;
}

}  // namespace net
}  // namespace android
