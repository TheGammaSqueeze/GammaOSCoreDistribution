/**
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "libsysfsmonitor"
#define DEBUG false

#include "SysfsMonitor.h"

#include <android-base/stringprintf.h>
#include <log/log.h>

#include <sys/epoll.h>

namespace {

using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringPrintf;

// The maximum number of sysfs files to monitor.
constexpr int32_t EPOLL_MAX_EVENTS = 10;

}  // namespace

namespace android {
namespace automotive {

Result<void> SysfsMonitor::init(CallbackFunc callback) {
    if (mEpollFd >= 0) {
        return Error() << "Epoll instance was already created";
    }
    if (mEpollFd.reset(epoll_create1(EPOLL_CLOEXEC)); mEpollFd < 0) {
        return Error() << "Cannot create epoll instance: errno = " << errno;
    }
    mCallback = callback;
    return {};
}

Result<void> SysfsMonitor::release() {
    if (mEpollFd < 0) {
        return Error() << "Epoll instance wasn't created";
    }
    for (const int32_t fd : mMonitoringFds) {
        if (epoll_ctl(mEpollFd, EPOLL_CTL_DEL, fd, /*event=*/nullptr)) {
            ALOGW("Failed to deregister fd(%d) from epoll instance: errno = %d", fd, errno);
        }
    }
    mMonitoringFds.clear();
    mEpollFd.reset();
    mCallback = nullptr;
    return {};
}

Result<void> SysfsMonitor::registerFd(int32_t fd) {
    if (fd < 0) {
        return Error() << StringPrintf("fd(%d) is invalid", fd);
    }
    if (mMonitoringFds.count(fd) > 0) {
        return Error() << StringPrintf("fd(%d) is already being monitored", fd);
    }
    if (mMonitoringFds.size() == EPOLL_MAX_EVENTS) {
        return Error() << "Cannot monitor more than " << EPOLL_MAX_EVENTS << " sysfs files";
    }
    struct epoll_event eventItem = {};
    eventItem.events = EPOLLIN | EPOLLPRI | EPOLLET;
    eventItem.data.fd = fd;
    if (int result = epoll_ctl(mEpollFd, EPOLL_CTL_ADD, fd, &eventItem); result != 0) {
        return Error() << StringPrintf("Failed to add fd(%d) to epoll instance: errno = %d", fd,
                                       errno);
    }
    mMonitoringFds.insert(fd);
    return {};
}

Result<void> SysfsMonitor::unregisterFd(int32_t fd) {
    if (fd < 0) {
        return Error() << StringPrintf("fd(%d) is invalid", fd);
    }
    if (mMonitoringFds.count(fd) == 0) {
        return Error() << StringPrintf("fd(%d) is not being monitored", fd);
    }
    // Even when epoll_ctl() fails, we proceed to handle the request.
    if (epoll_ctl(mEpollFd, EPOLL_CTL_DEL, fd, /*event=*/nullptr)) {
        ALOGW("Failed to deregister fd(%d) from epoll instance: errno = %d", fd, errno);
    }
    mMonitoringFds.erase(fd);
    return {};
}

Result<void> SysfsMonitor::observe() {
    if (mEpollFd < 0) {
        return Error() << "Epoll instance is not initialized";
    }

    struct epoll_event events[EPOLL_MAX_EVENTS];
    while (true) {
        int pollResult = epoll_wait(mEpollFd, events, EPOLL_MAX_EVENTS, /*timeout=*/-1);
        if (pollResult < 0) {
            ALOGW("Polling sysfs failed, but continue polling: errno = %d", errno);
            continue;
        }
        std::vector<int32_t> fds;
        for (int i = 0; i < pollResult; i++) {
            int fd = events[i].data.fd;
            if (mMonitoringFds.count(fd) == 0) {
                continue;
            }
            if (events[i].events & EPOLLIN) {
                fds.push_back(fd);
            } else if (events[i].events & EPOLLERR) {
                ALOGW("An error occurred when polling fd(%d)", fd);
            }
        }
        if (mCallback && fds.size() > 0) {
            mCallback(fds);
        }
    }
    return {};
}

}  // namespace automotive
}  // namespace android
