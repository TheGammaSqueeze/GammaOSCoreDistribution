/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
#define LOG_TAG "hwc-drm-atomic-state-manager"

#include "DrmAtomicStateManager.h"

#include <drm/drm_mode.h>
#include <pthread.h>
#include <sched.h>
#include <sync/sync.h>
#include <utils/Trace.h>

#include <array>
#include <cstdlib>
#include <ctime>
#include <sstream>
#include <vector>

#include "drm/DrmCrtc.h"
#include "drm/DrmDevice.h"
#include "drm/DrmPlane.h"
#include "drm/DrmUnique.h"
#include "utils/log.h"

namespace android {

// NOLINTNEXTLINE (readability-function-cognitive-complexity): Fixme
auto DrmAtomicStateManager::CommitFrame(AtomicCommitArgs &args) -> int {
  ATRACE_CALL();

  if (args.active && *args.active == active_frame_state_.crtc_active_state) {
    /* Don't set the same state twice */
    args.active.reset();
  }

  if (!args.HasInputs()) {
    /* nothing to do */
    return 0;
  }

  if (!active_frame_state_.crtc_active_state) {
    /* Force activate display */
    args.active = true;
  }

  auto new_frame_state = NewFrameState();

  auto *drm = pipe_->device;
  auto *connector = pipe_->connector->Get();
  auto *crtc = pipe_->crtc->Get();

  auto pset = MakeDrmModeAtomicReqUnique();
  if (!pset) {
    ALOGE("Failed to allocate property set");
    return -ENOMEM;
  }

  int64_t out_fence = -1;
  if (crtc->GetOutFencePtrProperty() &&
      !crtc->GetOutFencePtrProperty().AtomicSet(*pset, uint64_t(&out_fence))) {
    return -EINVAL;
  }

  if (args.active) {
    new_frame_state.crtc_active_state = *args.active;
    if (!crtc->GetActiveProperty().AtomicSet(*pset, *args.active ? 1 : 0) ||
        !connector->GetCrtcIdProperty().AtomicSet(*pset, crtc->GetId())) {
      return -EINVAL;
    }
  }

  if (args.display_mode) {
    new_frame_state.mode_blob = args.display_mode.value().CreateModeBlob(*drm);

    if (!new_frame_state.mode_blob) {
      ALOGE("Failed to create mode_blob");
      return -EINVAL;
    }

    if (!crtc->GetModeProperty().AtomicSet(*pset, *new_frame_state.mode_blob)) {
      return -EINVAL;
    }
  }

  auto unused_planes = new_frame_state.used_planes;

  if (args.composition) {
    new_frame_state.used_framebuffers.clear();
    new_frame_state.used_planes.clear();

    for (auto &joining : args.composition->plan) {
      DrmPlane *plane = joining.plane->Get();
      DrmHwcLayer &layer = joining.layer;

      new_frame_state.used_framebuffers.emplace_back(layer.fb_id_handle);
      new_frame_state.used_planes.emplace_back(joining.plane);

      /* Remove from 'unused' list, since plane is re-used */
      auto &v = unused_planes;
      v.erase(std::remove(v.begin(), v.end(), joining.plane), v.end());

      if (plane->AtomicSetState(*pset, layer, joining.z_pos, crtc->GetId()) !=
          0) {
        return -EINVAL;
      }
    }
  }

  if (args.composition) {
    for (auto &plane : unused_planes) {
      if (plane->Get()->AtomicDisablePlane(*pset) != 0) {
        return -EINVAL;
      }
    }
  }

  uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  if (args.test_only)
    flags |= DRM_MODE_ATOMIC_TEST_ONLY;

  int err = drmModeAtomicCommit(drm->GetFd(), pset.get(), flags, drm);
  if (err != 0) {
    if (!args.test_only)
      ALOGE("Failed to commit pset ret=%d\n", err);
    return err;
  }

  if (!args.test_only) {
    if (args.display_mode) {
      /* TODO(nobody): we still need this for synthetic vsync, remove after
       * vsync reworked */
      connector->SetActiveMode(*args.display_mode);
    }

    active_frame_state_ = std::move(new_frame_state);

    if (crtc->GetOutFencePtrProperty()) {
      args.out_fence = UniqueFd((int)out_fence);
    }
  }

  return 0;
}

auto DrmAtomicStateManager::ExecuteAtomicCommit(AtomicCommitArgs &args) -> int {
  int err = CommitFrame(args);

  if (!args.test_only) {
    if (err != 0) {
      ALOGE("Composite failed for pipeline %s",
            pipe_->connector->Get()->GetName().c_str());
      // Disable the hw used by the last active composition. This allows us to
      // signal the release fences from that composition to avoid hanging.
      AtomicCommitArgs cl_args{};
      cl_args.composition = std::make_shared<DrmKmsPlan>();
      if (CommitFrame(cl_args) != 0) {
        ALOGE("Failed to clean-up active composition for pipeline %s",
              pipe_->connector->Get()->GetName().c_str());
      }
      return err;
    }
  }

  return err;
}  // namespace android

auto DrmAtomicStateManager::ActivateDisplayUsingDPMS() -> int {
  return drmModeConnectorSetProperty(pipe_->device->GetFd(),
                                     pipe_->connector->Get()->GetId(),
                                     pipe_->connector->Get()
                                         ->GetDpmsProperty()
                                         .id(),
                                     DRM_MODE_DPMS_ON);
}

}  // namespace android
