/*
 * Copyright (C) 2016 The Android Open Source Project
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
#pragma once

#include "PqType.h"
#include "Buffer.h"
#include "BufferQueue.h"

#include "PqWorker.h"

#include <map>
#include <queue>
#include <memory>

namespace android {

// PqBackend 主要职责为申请中间缓存，调用NPU/GPU实现AIEE
class PqBackend {
public:
  PqBackend();
  ~PqBackend();
  int Init();
  int DeInit();
  int InitPq(const PqContext &ctx);
  int QueueCtxAndRun(const PqContext &ctx);
  int QueueCtxAndRunAsync(const PqContext &ctx, int *outFence);

private:
  bool mInit_;
  std::shared_ptr<BufferQueue> mPtrBufferQueue_;
  PqWorker PqWorker_;
};

} //namespace android