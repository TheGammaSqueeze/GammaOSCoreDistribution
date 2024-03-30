/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "hwc-resource-manager"

#include "ResourceManager.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <ctime>
#include <sstream>

#include "bufferinfo/BufferInfoGetter.h"
#include "drm/DrmAtomicStateManager.h"
#include "drm/DrmDevice.h"
#include "drm/DrmDisplayPipeline.h"
#include "drm/DrmPlane.h"
#include "utils/log.h"
#include "utils/properties.h"

namespace android {

ResourceManager::ResourceManager(
    PipelineToFrontendBindingInterface *p2f_bind_interface)
    : frontend_interface_(p2f_bind_interface) {
  if (uevent_listener_.Init() != 0) {
    ALOGE("Can't initialize event listener");
  }
}

ResourceManager::~ResourceManager() {
  uevent_listener_.Exit();
}

void ResourceManager::Init() {
  if (initialized_) {
    ALOGE("Already initialized");
    return;
  }

  char path_pattern[PROPERTY_VALUE_MAX];
  // Could be a valid path or it can have at the end of it the wildcard %
  // which means that it will try open all devices until an error is met.
  int path_len = property_get("vendor.hwc.drm.device", path_pattern,
                              "/dev/dri/card%");
  if (path_pattern[path_len - 1] != '%') {
    AddDrmDevice(std::string(path_pattern));
  } else {
    path_pattern[path_len - 1] = '\0';
    for (int idx = 0;; ++idx) {
      std::ostringstream path;
      path << path_pattern << idx;

      struct stat buf {};
      if (stat(path.str().c_str(), &buf) != 0)
        break;

      if (DrmDevice::IsKMSDev(path.str().c_str())) {
        AddDrmDevice(path.str());
      }
    }
  }

  char scale_with_gpu[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.drm.scale_with_gpu", scale_with_gpu, "0");
  scale_with_gpu_ = bool(strncmp(scale_with_gpu, "0", 1));

  if (BufferInfoGetter::GetInstance() == nullptr) {
    ALOGE("Failed to initialize BufferInfoGetter");
    return;
  }

  uevent_listener_.RegisterHotplugHandler([this] {
    const std::lock_guard<std::mutex> lock(GetMainLock());
    UpdateFrontendDisplays();
  });

  UpdateFrontendDisplays();

  initialized_ = true;
}

void ResourceManager::DeInit() {
  if (!initialized_) {
    ALOGE("Not initialized");
    return;
  }

  uevent_listener_.RegisterHotplugHandler([] {});

  DetachAllFrontendDisplays();
  drms_.clear();

  initialized_ = false;
}

int ResourceManager::AddDrmDevice(const std::string &path) {
  auto drm = std::make_unique<DrmDevice>();
  int ret = drm->Init(path.c_str());
  drms_.push_back(std::move(drm));
  return ret;
}

auto ResourceManager::GetTimeMonotonicNs() -> int64_t {
  struct timespec ts {};
  clock_gettime(CLOCK_MONOTONIC, &ts);
  constexpr int64_t kNsInSec = 1000000000LL;
  return int64_t(ts.tv_sec) * kNsInSec + int64_t(ts.tv_nsec);
}

void ResourceManager::UpdateFrontendDisplays() {
  auto ordered_connectors = GetOrderedConnectors();

  for (auto *conn : ordered_connectors) {
    conn->UpdateModes();
    bool connected = conn->IsConnected();
    bool attached = attached_pipelines_.count(conn) != 0;

    if (connected != attached) {
      ALOGI("%s connector %s", connected ? "Attaching" : "Detaching",
            conn->GetName().c_str());

      if (connected) {
        auto pipeline = DrmDisplayPipeline::CreatePipeline(*conn);
        frontend_interface_->BindDisplay(pipeline.get());
        attached_pipelines_[conn] = std::move(pipeline);
      } else {
        auto &pipeline = attached_pipelines_[conn];
        frontend_interface_->UnbindDisplay(pipeline.get());
        attached_pipelines_.erase(conn);
      }
    }
  }
  frontend_interface_->FinalizeDisplayBinding();
}

void ResourceManager::DetachAllFrontendDisplays() {
  for (auto &p : attached_pipelines_) {
    frontend_interface_->UnbindDisplay(p.second.get());
  }
  attached_pipelines_.clear();
  frontend_interface_->FinalizeDisplayBinding();
}

auto ResourceManager::GetOrderedConnectors() -> std::vector<DrmConnector *> {
  /* Put internal displays first then external to
   * ensure Internal will take Primary slot
   */

  std::vector<DrmConnector *> ordered_connectors;

  for (auto &drm : drms_) {
    for (const auto &conn : drm->GetConnectors()) {
      if (conn->IsInternal()) {
        ordered_connectors.emplace_back(conn.get());
      }
    }
  }

  for (auto &drm : drms_) {
    for (const auto &conn : drm->GetConnectors()) {
      if (conn->IsExternal()) {
        ordered_connectors.emplace_back(conn.get());
      }
    }
  }

  return ordered_connectors;
}
}  // namespace android
