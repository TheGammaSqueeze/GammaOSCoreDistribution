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

#include "autolock.h"
#include "PqType.h"
#include "PqBackend.h"

namespace android {

class Pq {
public:
  static Pq* Get() { return GetInstance(); }
  ~Pq();
  Pq(const Pq&)=delete;
  Pq& operator=(const Pq&)=delete;

  // Init Context info
  int InitCtx(PqContext &ctx);
  // Set src-image info
  int SetSrcImage(PqContext &ctx, const PqImageInfo &src);
  // Set dst-image info
  int SetDstImage(PqContext &ctx, const PqImageInfo &dst);
  // Run and get FinishFence
  int Run(const PqContext &ctx);
  // Run and get FinishFence
  int RunAsync(const PqContext &ctx, int *outFence);
  // Get dst format
  int GetDstFormat();
  // Get dst color space
  int GetDstColorSpace();
  // Deinit pq
  int DeInit();

private:
  Pq();
  int Init();
  static Pq* GetInstance(){
    static Pq pq;
    if(pq.Init())
      return NULL;
    else
      return &pq;
  }

  // Verify dlss context
  int VerifyCtx(const PqContext &ctx, const PqStage stage);
  // Verify src info
  int VerifySrcInfo(PqContext &ctx, const PqImageInfo &src);
  // Verify dst info
  int VerifyDstInfo(PqContext &ctx, const PqImageInfo &dst);
  // Dump Ctx info
  int DumpCtx(const PqContext &ctx);
  PqError bInitState_ = PqError::PqUnInit;
  PqVersion mVersion_;
  PqBackend mPqBackend_;
  mutable pthread_mutex_t mLock_;
};
} //namespace android
