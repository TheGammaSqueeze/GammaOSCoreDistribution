/* Copyright (c) 2015, 2020-2021, The Linux Foundataion. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

/*
* Changes from Qualcomm Innovation Center are provided under the following license:
*
* Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted (subject to the limitations in the
* disclaimer below) provided that the following conditions are met:
*
*    * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*
*    * Redistributions in binary form must reproduce the above
*      copyright notice, this list of conditions and the following
*      disclaimer in the documentation and/or other materials provided
*      with the distribution.
*
*    * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
*      contributors may be used to endorse or promote products derived
*      from this software without specific prior written permission.
*
* NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
* GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
* HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
* IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
* GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
* IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
* OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <cutils/properties.h>
#include <dlfcn.h>
#include <thread>
#include <utils/debug.h>

#include "cpuhint.h"
#include "hwc_debugger.h"

#define __CLASS__ "CPUHint"

namespace sdm {

DisplayError CPUHint::Init(HWCDebugHandler *debug_handler) {
  char path[PROPERTY_VALUE_MAX];
  if (debug_handler->GetProperty("ro.vendor.extension_library", path) != kErrorNone) {
    DLOGI("Vendor Extension Library not enabled");
    return kErrorNotSupported;
  }

  if (vendor_ext_lib_.Open(path)) {
    if (!vendor_ext_lib_.Sym("perf_hint_acq_rel_offload",
                             reinterpret_cast<void **>(&fn_perf_hint_acq_rel_offload_)) ||
        !vendor_ext_lib_.Sym("perf_lock_rel_offload",
                             reinterpret_cast<void **>(&fn_perf_lock_rel_offload_)) ||
        !vendor_ext_lib_.Sym("perf_hint",
                             reinterpret_cast<void **>(&fn_perf_hint_)) ||
        !vendor_ext_lib_.Sym("perf_event",
                             reinterpret_cast<void **>(&fn_perf_event_))) {
      DLOGW("Failed to load symbols for Vendor Extension Library");
      return kErrorNotSupported;
    }
    DLOGI("Successfully Loaded Vendor Extension Library symbols");
    enabled_ = (fn_perf_hint_acq_rel_offload_ != NULL &&
                fn_perf_lock_rel_offload_ != NULL && fn_perf_hint_ != NULL &&
                fn_perf_event_ != NULL);
  } else {
    DLOGW("Failed to open %s : %s", path, vendor_ext_lib_.Error());
  }

  return enabled_ ? kErrorNone : kErrorNotSupported;
}

int CPUHint::ReqHintsOffload(int hint, int tid) {
  if(enabled_ && hint > 0) {
    if (large_comp_cycle_.status == kActive) {
      nsecs_t currentTime = systemTime(SYSTEM_TIME_MONOTONIC);
      nsecs_t difference = currentTime-large_comp_cycle_.startTime;

      if (nanoseconds_to_seconds(difference) >= 4) {
        DLOGV_IF(kTagCpuHint, "Renew large composition hint:%d [start_time:%" PRIu64
                 " - current_time:%" PRIu64 " = %" PRIu64 "]", large_comp_cycle_.handleId,
                 large_comp_cycle_.startTime, currentTime, difference);

        large_comp_cycle_.status = kRenew;
      }

      if (tid != 0 && tid != large_comp_cycle_.tid) {
        DLOGV_IF(kTagCpuHint, "Renew large composition hint:%d [oldTid:%d newTid:%d]",
                 large_comp_cycle_.handleId, large_comp_cycle_.tid, tid);

        large_comp_cycle_.status = kRenew;
      }
    }

    if (large_comp_cycle_.status == kInactive || large_comp_cycle_.status == kRenew) {
      PerfHintStatus current_status = large_comp_cycle_.status;
      int handle = fn_perf_hint_acq_rel_offload_(large_comp_cycle_.handleId, hint, nullptr,
                                                 tid, 0, 0, nullptr);
      if (handle < 0) {
        DLOGW("Failed to request large composition hint ret:%d", handle);
        return -1;
      }

      large_comp_cycle_.handleId = handle;
      large_comp_cycle_.tid = (tid != 0) ? tid : large_comp_cycle_.tid;
      large_comp_cycle_.startTime = systemTime(SYSTEM_TIME_MONOTONIC);
      large_comp_cycle_.status = kActive;
      DLOGV_IF(kTagCpuHint, "Successfully %s large comp hint: handle_id:%d type:0x%x startTime:%"
               PRIu64 " status:%d", (current_status == kInactive) ? "initialized" : "renewed",
               large_comp_cycle_.handleId, kLargeComposition, large_comp_cycle_.startTime,
               large_comp_cycle_.status);
    }
  }

  return 0;
}

int CPUHint::ReqHintRelease() {
  if (large_comp_cycle_.status == kActive || large_comp_cycle_.status == kRenew) {
    int ret = fn_perf_lock_rel_offload_(large_comp_cycle_.handleId);
    if (ret < 0) {
      DLOGV_IF(kTagCpuHint, "Failed to release large comp hint ret:%d", ret);
      return -1;
    }

    DLOGV_IF(kTagCpuHint, "Release large comp hint ret:%d", ret);
    large_comp_cycle_.handleId = 0;
    large_comp_cycle_.tid = 0;
    large_comp_cycle_.startTime = 0;
    large_comp_cycle_.status = kInactive;
  }
  return 0;
}

int CPUHint::ReqHint(PerfHintThreadType type, int tid) {
  std::lock_guard<std::mutex> lock(tid_lock_);

  std::thread worker(
    [this](uint32_t tid, PerfHintThreadType type) {
      int ret = fn_perf_hint_(kHintPassPid, nullptr, tid, type);
      if (ret == kPassPidSuccess) {
        DLOGV_IF(kTagCpuHint, "Successfully sent HWC's tid:%d", tid);
        return 0;
      } else {
        DLOGW("Failed to send HWC's tid:%d", tid);
        return -1;
      }
    }, tid, type);

  worker.detach();
  return 0;
}

void CPUHint::ReqEvent(int event) {
  if(enabled_ && event > 0) {
      fn_perf_event_(event, nullptr, 0, nullptr);
  }
}

}  // namespace sdm
