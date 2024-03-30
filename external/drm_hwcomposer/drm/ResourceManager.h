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

#ifndef RESOURCEMANAGER_H
#define RESOURCEMANAGER_H

#include <cstring>

#include "DrmDevice.h"
#include "DrmDisplayPipeline.h"
#include "DrmFbImporter.h"
#include "UEventListener.h"

namespace android {

class PipelineToFrontendBindingInterface {
 public:
  virtual ~PipelineToFrontendBindingInterface() = default;
  virtual bool BindDisplay(DrmDisplayPipeline *);
  virtual bool UnbindDisplay(DrmDisplayPipeline *);
  virtual void FinalizeDisplayBinding();
};

class ResourceManager {
 public:
  explicit ResourceManager(
      PipelineToFrontendBindingInterface *p2f_bind_interface);
  ResourceManager(const ResourceManager &) = delete;
  ResourceManager &operator=(const ResourceManager &) = delete;
  ResourceManager(const ResourceManager &&) = delete;
  ResourceManager &&operator=(const ResourceManager &&) = delete;
  ~ResourceManager();

  void Init();

  void DeInit();

  bool ForcedScalingWithGpu() const {
    return scale_with_gpu_;
  }

  auto &GetMainLock() {
    return main_lock_;
  }

  static auto GetTimeMonotonicNs() -> int64_t;

 private:
  auto AddDrmDevice(std::string const &path) -> int;
  auto GetOrderedConnectors() -> std::vector<DrmConnector *>;
  void UpdateFrontendDisplays();
  void DetachAllFrontendDisplays();

  std::vector<std::unique_ptr<DrmDevice>> drms_;

  bool scale_with_gpu_{};

  UEventListener uevent_listener_;

  std::mutex main_lock_;

  std::map<DrmConnector *, std::unique_ptr<DrmDisplayPipeline>>
      attached_pipelines_;

  PipelineToFrontendBindingInterface *const frontend_interface_;

  bool initialized_{};
};
}  // namespace android

#endif  // RESOURCEMANAGER_H
