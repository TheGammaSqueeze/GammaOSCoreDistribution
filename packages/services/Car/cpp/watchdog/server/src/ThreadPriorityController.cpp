/**
 * Copyright (c) 2022, The Android Open Source Project
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

#include "ThreadPriorityController.h"

#include "UidProcStatsCollector.h"

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::android::automotive::watchdog::internal::ThreadPolicyWithPriority;
using ::android::base::Result;
using ::android::binder::Status;

Status fromExceptionCode(int32_t exceptionCode, const std::string& message) {
    ALOGW("%s", message.c_str());
    return Status::fromExceptionCode(exceptionCode, message.c_str());
}

Status fromServiceSpecificError(const std::string& message) {
    ALOGW("%s", message.c_str());
    return Status::fromServiceSpecificError(/*exceptionCode=*/0, message.c_str());
}

constexpr int PRIORITY_MIN = 1;
constexpr int PRIORITY_MAX = 99;

}  // namespace

Status ThreadPriorityController::checkPidTidUid(pid_t pid, pid_t tid, uid_t uid) {
    auto tidStatus = mSystemCallsInterface->readPidStatusFileForPid(tid);
    if (!tidStatus.ok()) {
        return fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                 StringPrintf("invalid thread ID: %d", tid));
    }
    uid_t uidForThread = std::get<0>(*tidStatus);
    pid_t tgid = std::get<1>(*tidStatus);
    if (pid != tgid) {
        return fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                 StringPrintf("invalid process ID: %d", pid));
    }
    if (uid != uidForThread) {
        return fromExceptionCode(Status::EX_ILLEGAL_STATE,
                                 StringPrintf("invalid user ID: %d", uid));
    }
    return Status::ok();
}

Status ThreadPriorityController::setThreadPriority(int pid, int tid, int uid, int policy,
                                                   int priority) {
    pid_t tpid = static_cast<pid_t>(tid);
    pid_t ppid = static_cast<pid_t>(pid);
    uid_t uuid = static_cast<uid_t>(uid);
    Status status = checkPidTidUid(ppid, tpid, uuid);
    if (!status.isOk()) {
        return status;
    }

    if (policy != SCHED_FIFO && policy != SCHED_RR && policy != SCHED_OTHER) {
        return fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                 StringPrintf("invalid policy: %d, only support SCHED_OTHER(%d)"
                                              ", SCHED_FIFO(%d) and SCHED_RR(%d)",
                                              policy, SCHED_OTHER, SCHED_FIFO, SCHED_RR));
    }

    if (policy == SCHED_OTHER) {
        priority = 0;
    } else if (priority < PRIORITY_MIN || priority > PRIORITY_MAX) {
        return fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT,
                                 StringPrintf("invalid priority: %d for policy: (%d), "
                                              "must be within %d and %d",
                                              priority, policy, PRIORITY_MIN, PRIORITY_MAX));
    }

    sched_param param{.sched_priority = priority};
    errno = 0;
    if (mSystemCallsInterface->setScheduler(tpid, policy, &param) != 0) {
        return fromServiceSpecificError(
                StringPrintf("sched_setscheduler failed, errno: %d", errno));
    }
    return Status::ok();
}

Status ThreadPriorityController::getThreadPriority(int pid, int tid, int uid,
                                                   ThreadPolicyWithPriority* result) {
    pid_t tpid = static_cast<pid_t>(tid);
    pid_t ppid = static_cast<pid_t>(pid);
    uid_t uuid = static_cast<uid_t>(uid);
    Status status = checkPidTidUid(ppid, tpid, uuid);
    if (!status.isOk()) {
        return status;
    }

    errno = 0;
    int policy = mSystemCallsInterface->getScheduler(tpid);
    if (policy < 0) {
        return fromServiceSpecificError(
                StringPrintf("sched_getscheduler failed, errno: %d", errno));
    }

    sched_param param = {};
    errno = 0;
    int callResult = mSystemCallsInterface->getParam(tpid, &param);
    if (callResult != 0) {
        return fromServiceSpecificError(StringPrintf("sched_getparam failed, errno: %d", errno));
    }

    result->policy = policy;
    result->priority = param.sched_priority;
    return Status::ok();
}

int ThreadPriorityController::SystemCalls::setScheduler(pid_t tid, int policy,
                                                        const sched_param* param) {
    return sched_setscheduler(tid, policy, param);
}

int ThreadPriorityController::SystemCalls::getScheduler(pid_t tid) {
    return sched_getscheduler(tid);
}

int ThreadPriorityController::SystemCalls::getParam(pid_t tid, sched_param* param) {
    return sched_getparam(tid, param);
}

Result<std::tuple<uid_t, pid_t>> ThreadPriorityController::SystemCalls::readPidStatusFileForPid(
        pid_t pid) {
    return UidProcStatsCollector::readPidStatusFileForPid(pid);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
