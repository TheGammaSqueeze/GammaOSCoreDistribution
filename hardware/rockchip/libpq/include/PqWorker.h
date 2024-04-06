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

#include "worker.h"
#include "Buffer.h"
#include "BufferQueue.h"
#include "PqType.h"
#include "rkpq_api.h"
#include "j2s.h"
#include "cJSON_Utils.h"
#include "hardware/hardware_rockchip.h"
#include <RgaUtils.h>

#include <map>
#include <queue>
#include <memory>

namespace android {

class PqWorker : public Worker {
  public:
  PqWorker();
  ~PqWorker() override;

  int Init();
  int DeInit();
  int InitPq(std::shared_ptr<PqBackendContext> abCtx);
  void Queue(std::shared_ptr<PqBackendContext> abCtx);


  protected:
  void Routine() override;
  int PqRun(std::shared_ptr<PqBackendContext> abCtx);
  int ConvertByRga(std::shared_ptr<PqBackendContext> abCtx);
  int SignalFinishFence(std::shared_ptr<PqBackendContext> abCtx);
  int PqProcess(std::shared_ptr<PqBackendContext> abCtx);
  void updateRKPQProcParams(RKPQ_Proc_Params* params); 
  private:
  std::queue<std::shared_ptr<PqBackendContext>> abCtxQueue_;
  char cFenceName_[50];
  int iTimelineFd_;
  int iTimeline_;
  int iCurrentTimeline_;
};
} //namespace android
