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

#include "UwbAdaptation.h"

#include <aidl/android/hardware/uwb/BnUwb.h>
#include <aidl/android/hardware/uwb/BnUwbClientCallback.h>
#include <aidl/android/hardware/uwb/IUwb.h>
#include <aidl/android/hardware/uwb/IUwbChip.h>
#include <android-base/logging.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IMemory.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>
#include <cutils/properties.h>
#include <pthread.h>

#include <algorithm>

#include "uci_log.h"
#include "uwa_api.h"
#include "uwb_config.h"
#include "uwb_hal_int.h"
#include "uwb_int.h"
#include "uwb_target.h"

using IUwbV1_0 = aidl::android::hardware::uwb::IUwb;
using IUwbChipV1_0 = aidl::android::hardware::uwb::IUwbChip;
using UwbStatus = aidl::android::hardware::uwb::UwbStatus;
using aidl::android::hardware::uwb::BnUwbClientCallback;
using aidl::android::hardware::uwb::IUwbClientCallback;

std::string UWB_HAL_SERVICE_NAME = "android.hardware.uwb.IUwb/default";
extern bool uwb_debug_enabled;

bool uwb_debug_enabled = false;
bool IsdebugLogEnabled = false;

extern void phUwb_GKI_shutdown();

UwbAdaptation* UwbAdaptation::mpInstance = NULL;
ThreadMutex UwbAdaptation::sLock;
ThreadMutex UwbAdaptation::sIoctlLock;
std::mutex sIoctlMutex;

tHAL_UWB_CBACK* UwbAdaptation::mHalCallback = NULL;
tHAL_UWB_DATA_CBACK* UwbAdaptation::mHalDataCallback = NULL;
std::shared_ptr<IUwbChipV1_0> mHal = nullptr;

namespace {
void initializeGlobalDebugEnabledFlag() {
  uwb_debug_enabled = true;

  UCI_TRACE_I("%s: Debug log is enabled =%u", __func__, uwb_debug_enabled);
}

std::shared_ptr<IUwbChipV1_0> getHalService() {
  ::ndk::SpAIBinder binder(AServiceManager_getService(UWB_HAL_SERVICE_NAME.c_str()));
  std::shared_ptr<IUwbV1_0> iUwb = IUwbV1_0::fromBinder(binder);
  if (iUwb == nullptr) {
      ALOGE("Failed to connect to the AIDL HAL service.");
      return nullptr;
  }
  std::vector<std::string> chipNames;
  ndk::ScopedAStatus status = iUwb->getChips(&chipNames);
  if (!status.isOk() || chipNames.empty()) {
    ALOGE("Failed to retrieve the HAL chip names");
    return nullptr;
  }
  // TODO (b/197638976): We pick the first chip here. Need to fix this
  // for supporting multiple chips in the future.
  std::shared_ptr<IUwbChipV1_0> iUwbChip;
  status = iUwb->getChip(chipNames.front(), &iUwbChip);
  if (!status.isOk() || iUwbChip == nullptr) {
    ALOGE("Failed to retrieve the HAL chip");
    return nullptr;
  }
  return iUwbChip;
}

}  // namespace

class UwbClientCallback
    : public aidl::android::hardware::uwb::BnUwbClientCallback {
 public:
  UwbClientCallback(tHAL_UWB_CBACK* eventCallback,
                    tHAL_UWB_DATA_CBACK dataCallback) {
    mEventCallback = eventCallback;
    mDataCallback = dataCallback;
  };
  virtual ~UwbClientCallback() = default;

  ::ndk::ScopedAStatus onHalEvent(
      aidl::android::hardware::uwb::UwbEvent event,
      aidl::android::hardware::uwb::UwbStatus event_status) override {
    mEventCallback((uint8_t)event, (uint16_t)event_status);
    return ::ndk::ScopedAStatus::ok();
  };

  ::ndk::ScopedAStatus onUciMessage(const std::vector<uint8_t>& data) override {
    std::vector<uint8_t> copy = data;
    mDataCallback(copy.size(), &copy[0]);
    return ::ndk::ScopedAStatus::ok();
  };

 private:
  tHAL_UWB_CBACK* mEventCallback;
  tHAL_UWB_DATA_CBACK* mDataCallback;
};

/*******************************************************************************
**
** Function:    UwbAdaptation::UwbAdaptation()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
UwbAdaptation::UwbAdaptation() {
  memset(&mHalEntryFuncs, 0, sizeof(mHalEntryFuncs));
}

/*******************************************************************************
**
** Function:    UwbAdaptation::~UwbAdaptation()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
UwbAdaptation::~UwbAdaptation() { mpInstance = NULL; }

/*******************************************************************************
**
** Function:    UwbAdaptation::GetInstance()
**
** Description: access class singleton
**
** Returns:     pointer to the singleton object
**
*******************************************************************************/
UwbAdaptation& UwbAdaptation::GetInstance() {
  AutoThreadMutex a(sLock);

  if (!mpInstance) mpInstance = new UwbAdaptation;
  CHECK(mpInstance);
  return *mpInstance;
}

/*******************************************************************************
**
** Function:    UwbAdaptation::Initialize()
**
** Description: class initializer
**
** Returns:     none
**
*******************************************************************************/
void UwbAdaptation::Initialize() {
  const char* func = "UwbAdaptation::Initialize";
  UNUSED(func);
  UCI_TRACE_I("%s: enter", func);
  initializeGlobalDebugEnabledFlag();
  phUwb_GKI_init();
  phUwb_GKI_enable();
  phUwb_GKI_create_task((TASKPTR)UWBA_TASK, BTU_TASK, (int8_t*)"UWBA_TASK", 0,
                        0, (pthread_cond_t*)NULL, NULL);
  {
    AutoThreadMutex guard(mCondVar);
    phUwb_GKI_create_task((TASKPTR)Thread, MMI_TASK, (int8_t*)"UWBA_THREAD", 0,
                          0, (pthread_cond_t*)NULL, NULL);
    mCondVar.wait();
  }

  mHalCallback = NULL;
  memset(&mHalEntryFuncs, 0, sizeof(mHalEntryFuncs));
  InitializeHalDeviceContext();
  UCI_TRACE_I("%s: exit", func);
}

/*******************************************************************************
**
** Function:    UwbAdaptation::Finalize(bool exitStatus)
**
** Description: class finalizer
**
** Returns:     none
**
*******************************************************************************/
void UwbAdaptation::Finalize(bool graceExit) {
  const char* func = "UwbAdaptation::Finalize";
  UNUSED(func);
  AutoThreadMutex a(sLock);

  UCI_TRACE_I("%s: enter, graceful: %d", func, graceExit);
  phUwb_GKI_shutdown();

  memset(&mHalEntryFuncs, 0, sizeof(mHalEntryFuncs));
  if (graceExit) {
    UwbConfig::clear();
  }

  UCI_TRACE_I("%s: exit", func);
  delete this;
}

/*******************************************************************************
**
** Function:    UwbAdaptation::signal()
**
** Description: signal the CondVar to release the thread that is waiting
**
** Returns:     none
**
*******************************************************************************/
void UwbAdaptation::signal() { mCondVar.signal(); }

/*******************************************************************************
**
** Function:    UwbAdaptation::UWBA_TASK()
**
** Description: UWBA_TASK runs the GKI main task
**
** Returns:     none
**
*******************************************************************************/
uint32_t UwbAdaptation::UWBA_TASK(__attribute__((unused)) uint32_t arg) {
  const char* func = "UwbAdaptation::UWBA_TASK";
  UNUSED(func);
  UCI_TRACE_I("%s: enter", func);
  phUwb_GKI_run(0);
  UCI_TRACE_I("%s: exit", func);
  return 0;
}

/*******************************************************************************
**
** Function:    UwbAdaptation::Thread()
**
** Description: Creates work threads
**
** Returns:     none
**
*******************************************************************************/
uint32_t UwbAdaptation::Thread(__attribute__((unused)) uint32_t arg) {
  const char* func = "UwbAdaptation::Thread";
  UNUSED(func);
  UCI_TRACE_I("%s: enter", func);

  {
    ThreadCondVar CondVar;
    AutoThreadMutex guard(CondVar);
    phUwb_GKI_create_task((TASKPTR)uwb_task, UWB_TASK, (int8_t*)"UWB_TASK", 0,
                          0, (pthread_cond_t*)CondVar,
                          (pthread_mutex_t*)CondVar);
    CondVar.wait();
  }

  UwbAdaptation::GetInstance().signal();

  phUwb_GKI_exit_task(phUwb_GKI_get_taskid());
  UCI_TRACE_I("%s: exit", func);
  return 0;
}

/*******************************************************************************
**
** Function:    UwbAdaptation::GetHalEntryFuncs()
**
** Description: Get the set of HAL entry points.
**
** Returns:     Functions pointers for HAL entry points.
**
*******************************************************************************/
tHAL_UWB_ENTRY* UwbAdaptation::GetHalEntryFuncs() { return &mHalEntryFuncs; }

/*******************************************************************************
**
** Function:    UwbAdaptation::InitializeHalDeviceContext
**
** Description: Ask the generic Android HAL to find the Broadcom-specific HAL.
**
** Returns:     None.
**
*******************************************************************************/
void UwbAdaptation::InitializeHalDeviceContext() {
  const char* func = "UwbAdaptation::InitializeHalDeviceContext";
  UNUSED(func);
  UCI_TRACE_I("%s: enter", func);

  mHalEntryFuncs.open = HalOpen;
  mHalEntryFuncs.close = HalClose;
  mHalEntryFuncs.write = HalWrite;
  mHalEntryFuncs.CoreInitialization = CoreInitialization;
  mHalEntryFuncs.SessionInitialization = SessionInitialization;
  mHal = getHalService();
  if (mHal == nullptr) {
    UCI_TRACE_I("%s: Failed to retrieve the UWB HAL!", func);
  } else {
    UCI_TRACE_I("%s: IUwb::getService() returned %p (%s)", func, mHal.get(),
                (mHal->isRemote() ? "remote" : "local"));
  }
}

/*******************************************************************************
**
** Function:    UwbAdaptation::HalOpen
**
** Description: Turn on controller, download firmware.
**
** Returns:     None.
**
*******************************************************************************/
void UwbAdaptation::HalOpen(tHAL_UWB_CBACK* p_hal_cback,
                            tHAL_UWB_DATA_CBACK* p_data_cback) {
  const char* func = "UwbAdaptation::HalOpen";
  UNUSED(func);
  UCI_TRACE_I("%s", func);
  ndk::ScopedAStatus status;
  std::shared_ptr<IUwbClientCallback> mCallback;
  mCallback =
      ndk::SharedRefBase::make<UwbClientCallback>(p_hal_cback, p_data_cback);

  if (mHal != nullptr) {
    status = mHal->open(mCallback);
  } else {
    UCI_TRACE_E("%s mHal is NULL", func);
  }
}
/*******************************************************************************
**
** Function:    UwbAdaptation::HalClose
**
** Description: Turn off controller.
**
** Returns:     None.
**
*******************************************************************************/
void UwbAdaptation::HalClose() {
  const char* func = "UwbAdaptation::HalClose";
  UNUSED(func);
  ndk::ScopedAStatus status;
  UCI_TRACE_I("%s HalClose Enter", func);
  if (mHal != nullptr) status = mHal->close();
}

/*******************************************************************************
**
** Function:    UwbAdaptation::HalWrite
**
** Description: Write UCI message to the controller.
**
** Returns:     None.
**
*******************************************************************************/
void UwbAdaptation::HalWrite(__attribute__((unused)) uint16_t data_len,
                             __attribute__((unused)) uint8_t* p_data) {
  const char* func = "UwbAdaptation::HalWrite";
  UNUSED(func);
  UCI_TRACE_I("%s: Enter", func);
  std::vector<uint8_t> data;
  if (p_data == NULL) {
    UCI_TRACE_E("p_data is null");
    return;
  }
  int ret;
  copy(&p_data[0], &p_data[data_len], back_inserter(data));
  if (mHal != nullptr) {
    mHal->sendUciMessage(data, &ret);
  } else {
    UCI_TRACE_E("mHal is NULL");
  }
}

/*******************************************************************************
**
** Function:    UwbAdaptation::CoreInitialization
**
** Description: Performs UWB CoreInitialization.
**
** Returns:     UwbStatus::OK on success and UwbStatus::FAILED on error.
**
*******************************************************************************/
tUWB_STATUS UwbAdaptation::CoreInitialization() {
  const char* func = "UwbAdaptation::CoreInitialization";
  UNUSED(func);
  UCI_TRACE_I("%s: enter", func);
  if (mHal != nullptr) {
    if (!mHal->coreInit().isOk()) return UWB_STATUS_FAILED;
  } else {
    UCI_TRACE_E("mHal is NULL");
    return UWB_STATUS_FAILED;
  }
  return UWB_STATUS_OK;
}

/*******************************************************************************
**
** Function:    UwbAdaptation::SessionInitialization
**
** Description: Performs UWB SessionInitialization.
**
** Returns:     UwbStatus::OK on success and UwbStatus::FAILED on error.
**
*******************************************************************************/
tUWB_STATUS UwbAdaptation::SessionInitialization(int sessionId) {
  const char* func = "UwbAdaptation::SessionInitialization";
  UNUSED(func);
  UCI_TRACE_I("%s: enter", func);
  if (mHal != nullptr) {
    if (!mHal->sessionInit(sessionId).isOk()) return UWB_STATUS_FAILED;
  } else {
    UCI_TRACE_E("mHal is NULL");
    return UWB_STATUS_FAILED;
  }
  return UWB_STATUS_OK;
}

/*******************************************************************************
**
** Function:    ThreadMutex::ThreadMutex()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
ThreadMutex::ThreadMutex() {
  pthread_mutexattr_t mutexAttr;

  pthread_mutexattr_init(&mutexAttr);
  pthread_mutex_init(&mMutex, &mutexAttr);
  pthread_mutexattr_destroy(&mutexAttr);
}

/*******************************************************************************
**
** Function:    ThreadMutex::~ThreadMutex()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
ThreadMutex::~ThreadMutex() { pthread_mutex_destroy(&mMutex); }

/*******************************************************************************
**
** Function:    ThreadMutex::lock()
**
** Description: lock the mutex
**
** Returns:     none
**
*******************************************************************************/
void ThreadMutex::lock() { pthread_mutex_lock(&mMutex); }

/*******************************************************************************
**
** Function:    ThreadMutex::unblock()
**
** Description: unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
void ThreadMutex::unlock() { pthread_mutex_unlock(&mMutex); }

/*******************************************************************************
**
** Function:    ThreadCondVar::ThreadCondVar()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
ThreadCondVar::ThreadCondVar() {
  pthread_condattr_t CondAttr;

  pthread_condattr_init(&CondAttr);
  pthread_cond_init(&mCondVar, &CondAttr);

  pthread_condattr_destroy(&CondAttr);
}

/*******************************************************************************
**
** Function:    ThreadCondVar::~ThreadCondVar()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
ThreadCondVar::~ThreadCondVar() { pthread_cond_destroy(&mCondVar); }

/*******************************************************************************
**
** Function:    ThreadCondVar::wait()
**
** Description: wait on the mCondVar
**
** Returns:     none
**
*******************************************************************************/
void ThreadCondVar::wait() {
  pthread_cond_wait(&mCondVar, *this);
  pthread_mutex_unlock(*this);
}

/*******************************************************************************
**
** Function:    ThreadCondVar::signal()
**
** Description: signal the mCondVar
**
** Returns:     none
**
*******************************************************************************/
void ThreadCondVar::signal() {
  AutoThreadMutex a(*this);
  pthread_cond_signal(&mCondVar);
}

/*******************************************************************************
**
** Function:    AutoThreadMutex::AutoThreadMutex()
**
** Description: class constructor, automatically lock the mutex
**
** Returns:     none
**
*******************************************************************************/
AutoThreadMutex::AutoThreadMutex(ThreadMutex& m) : mm(m) { mm.lock(); }

/*******************************************************************************
**
** Function:    AutoThreadMutex::~AutoThreadMutex()
**
** Description: class destructor, automatically unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
AutoThreadMutex::~AutoThreadMutex() { mm.unlock(); }
