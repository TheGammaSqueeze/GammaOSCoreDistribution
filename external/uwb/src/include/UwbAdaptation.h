/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
 *  Copyright 2018-2019 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#pragma once
#include <pthread.h>
#include <utils/RefBase.h>

#include "config.h"
#include "hal_uwb.h"
#include "uwb_hal_api.h"
#include "uwb_hal_int.h"
#include "uwb_target.h"

class ThreadMutex {
 public:
  ThreadMutex();
  virtual ~ThreadMutex();
  void lock();
  void unlock();
  operator pthread_mutex_t*() { return &mMutex; }

 private:
  pthread_mutex_t mMutex;
};

class ThreadCondVar : public ThreadMutex {
 public:
  ThreadCondVar();
  virtual ~ThreadCondVar();
  void signal();
  void wait();
  operator pthread_cond_t*() { return &mCondVar; }
  operator pthread_mutex_t*() {
    return ThreadMutex::operator pthread_mutex_t*();
  }

 private:
  pthread_cond_t mCondVar;
};

class AutoThreadMutex {
 public:
  AutoThreadMutex(ThreadMutex& m);
  virtual ~AutoThreadMutex();
  operator ThreadMutex&() { return mm; }
  operator pthread_mutex_t*() { return (pthread_mutex_t*)mm; }

 private:
  ThreadMutex& mm;
};

class UwbAdaptation {
 public:
  virtual ~UwbAdaptation();
  void Initialize();
  void Finalize(bool graceExit);
  static UwbAdaptation& GetInstance();
  tHAL_UWB_ENTRY* GetHalEntryFuncs();
  static tUWB_STATUS CoreInitialization();
  static tUWB_STATUS SessionInitialization(int sessionId);

 private:
  UwbAdaptation();
  void signal();
  static UwbAdaptation* mpInstance;
  static ThreadMutex sLock;
  static ThreadMutex sIoctlLock;
  ThreadCondVar mCondVar;
  tHAL_UWB_ENTRY mHalEntryFuncs;  // function pointers for HAL entry points

  static tHAL_UWB_CBACK* mHalCallback;
  static tHAL_UWB_DATA_CBACK* mHalDataCallback;

  static uint32_t UWBA_TASK(uint32_t arg);
  static uint32_t Thread(uint32_t arg);
  void InitializeHalDeviceContext();
  static void HalOpen(tHAL_UWB_CBACK* p_hal_cback,
                      tHAL_UWB_DATA_CBACK* p_data_cback);
  static void HalClose();
  static void HalWrite(uint16_t data_len, uint8_t* p_data);
};
