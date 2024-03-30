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

#ifndef ANDROID_EVENT_WORKER_H_
#define ANDROID_EVENT_WORKER_H_

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>
#include <hardware/hwcomposer2.h>

#include <atomic>
#include <cstdint>
#include <functional>
#include <map>

#include "DrmDevice.h"
#include "utils/Worker.h"

namespace android {

class VSyncWorker : public Worker {
 public:
  VSyncWorker();
  ~VSyncWorker() override = default;

  auto Init(DrmDisplayPipeline *pipe,
            std::function<void(uint64_t /*timestamp*/)> callback) -> int;

  void VSyncControl(bool enabled);

 protected:
  void Routine() override;

 private:
  int64_t GetPhasedVSync(int64_t frame_ns, int64_t current) const;
  int SyntheticWaitVBlank(int64_t *timestamp);

  std::function<void(uint64_t /*timestamp*/)> callback_;

  DrmDisplayPipeline *pipe_ = nullptr;
  std::atomic_bool enabled_ = false;
  int64_t last_timestamp_ = -1;
};
}  // namespace android

#endif
