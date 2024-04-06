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

#include "Buffer.h"

#include <ui/GraphicBuffer.h>
#include <queue>
namespace android {

#define DRM_RGA_BUFFERQUEUE_MAX_SIZE 3

class BufferQueue{
public:
  BufferQueue();
  ~BufferQueue();
  bool NeedsReallocation(int w, int h, int format);
  std::shared_ptr<Buffer> FrontBuffer();
  std::shared_ptr<Buffer> BackBuffer();

  std::shared_ptr<Buffer> DequeueBuffer(int w, int h, int format, std::string name);
  int QueueBuffer(const std::shared_ptr<Buffer> buffer);

private:
  std::string sName_;
  int iMaxBufferSize_;
  std::shared_ptr<Buffer> currentBuffer_;
  std::queue<std::shared_ptr<Buffer>> bufferQueue_;
};

}// namespace android