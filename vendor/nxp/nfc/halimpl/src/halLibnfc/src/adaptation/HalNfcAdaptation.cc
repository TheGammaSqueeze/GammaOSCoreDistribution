/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
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
/******************************************************************************
 *
 *  The original Work has been changed by NXP Semiconductors.
 *
 *  Copyright 2015-2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
#include "HalNfcAdaptation.h"
#include "nfa_api.h"
#include "nfc_int.h"
#include "nfc_target.h"
/*#include "phNxpNciHal_Adaptation.h"*/
#include <android-base/stringprintf.h>
#include <android/hardware/nfc/1.0/types.h>
#include <android/hardware/nfc/1.1/INfc.h>
#include <base/command_line.h>
#include <base/logging.h>
#include <cutils/properties.h>
#include <hidl/LegacySupport.h>
#include <hwbinder/ProcessState.h>
#include <vector>

using android::OK;
using android::sp;
using android::status_t;

using android::base::StringPrintf;
using android::hardware::ProcessState;
using android::hardware::Return;
using android::hardware::Void;
using android::hardware::nfc::V1_0::INfc;
using android::hardware::nfc::V1_1::PresenceCheckAlgorithm;
using INfcV1_1 = android::hardware::nfc::V1_1::INfc;
using ::android::wp;
using android::hardware::configureRpcThreadpool;
using ::android::hardware::hidl_death_recipient;
using android::hardware::hidl_vec;
using android::hardware::nfc::V1_1::INfcClientCallback;
using ::android::hidl::base::V1_0::IBase;
using vendor::nxp::nxpnfc::V2_0::INxpNfc;
using vendor::nxp::nxpnfclegacy::V1_0::INxpNfcLegacy;

extern bool nfc_debug_enabled;

extern void GKI_shutdown();

HalNfcAdaptation *HalNfcAdaptation::mpInstance = NULL;
HalAdaptationThreadMutex HalNfcAdaptation::sLock;
HalAdaptationThreadMutex HalNfcAdaptation::sIoctlLock;
sp<INxpNfc> HalNfcAdaptation::mHalNxpNfc;
sp<INxpNfcLegacy> HalNfcAdaptation::mHalNxpNfcLegacy;
sp<INfc> HalNfcAdaptation::mHal;
sp<INfcV1_1> HalNfcAdaptation::mHal_1_1;
INfcClientCallback *HalNfcAdaptation::mCallback;
tHAL_NFC_CBACK *HalNfcAdaptation::mHalCallback = NULL;
tHAL_NFC_DATA_CBACK *HalNfcAdaptation::mHalDataCallback = NULL;
HalAdaptationThreadCondVar HalNfcAdaptation::mHalOpenCompletedEvent;
HalAdaptationThreadCondVar HalNfcAdaptation::mHalCloseCompletedEvent;

#if (NXP_EXTNS == TRUE)
HalAdaptationThreadCondVar HalNfcAdaptation::mHalCoreResetCompletedEvent;
HalAdaptationThreadCondVar HalNfcAdaptation::mHalCoreInitCompletedEvent;
HalAdaptationThreadCondVar HalNfcAdaptation::mHalInitCompletedEvent;
#define SIGNAL_NONE 0
#define SIGNAL_SIGNALED 1
#endif

bool nfc_debug_enabled = false;
std::string nfc_storage_path;
uint8_t appl_dta_mode_flag = 0x00;

extern tNFA_DM_CFG nfa_dm_cfg;
extern tNFA_HCI_CFG nfa_hci_cfg;
extern bool nfa_poll_bail_out_mode;

// Whitelist for hosts allowed to create a pipe
// See ADM_CREATE_PIPE command in the ETSI test specification
// ETSI TS 102 622, section 6.1.3.1
static std::vector<uint8_t> host_whitelist;

namespace {
void initializeGlobalDebugEnabledFlag() {
  nfc_debug_enabled = true;
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: level=%u", __func__, nfc_debug_enabled);
}
} // namespace

class NfcClientCallback : public INfcClientCallback {
public:
  NfcClientCallback(tHAL_NFC_CBACK *eventCallback,
                    tHAL_NFC_DATA_CBACK dataCallback) {
    mEventCallback = eventCallback;
    mDataCallback = dataCallback;
  };
  virtual ~NfcClientCallback() = default;
  Return<void> sendEvent_1_1(
      ::android::hardware::nfc::V1_1::NfcEvent event,
      ::android::hardware::nfc::V1_0::NfcStatus event_status) override {
    mEventCallback((uint8_t)event, (tHAL_NFC_STATUS)event_status);
    return Void();
  };
  Return<void>
  sendEvent(::android::hardware::nfc::V1_0::NfcEvent event,
            ::android::hardware::nfc::V1_0::NfcStatus event_status) override {
    mEventCallback((uint8_t)event, (tHAL_NFC_STATUS)event_status);
    return Void();
  };
  Return<void>
  sendData(const ::android::hardware::nfc::V1_0::NfcData &data) override {
    ::android::hardware::nfc::V1_0::NfcData copy = data;
    mDataCallback(copy.size(), &copy[0]);
    return Void();
  };

private:
  tHAL_NFC_CBACK *mEventCallback;
  tHAL_NFC_DATA_CBACK *mDataCallback;
};

class NfcDeathRecipient : public hidl_death_recipient {
public:
  android::sp<android::hardware::nfc::V1_0::INfc> mNfcDeathHal;
  NfcDeathRecipient(android::sp<android::hardware::nfc::V1_0::INfc> &mHal) {
    mNfcDeathHal = mHal;
  }

  virtual void
  serviceDied(uint64_t /* cookie */,
              const wp<::android::hidl::base::V1_0::IBase> & /* who */) {
    ALOGE("NfcDeathRecipient::serviceDied - Nfc service died");
    mNfcDeathHal->unlinkToDeath(this);
    mNfcDeathHal = NULL;
    abort();
  }
};

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalNfcAdaptation()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
HalNfcAdaptation::HalNfcAdaptation() {
  mNfcHalDeathRecipient = new NfcDeathRecipient(mHal);
  memset(&mHalEntryFuncs, 0, sizeof(mHalEntryFuncs));
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::~HalNfcAdaptation()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
HalNfcAdaptation::~HalNfcAdaptation() {}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::GetInstance()
**
** Description: access class singleton
**
** Returns:     pointer to the singleton object
**
*******************************************************************************/
HalNfcAdaptation &HalNfcAdaptation::GetInstance() {
  HalAdaptationAutoThreadMutex a(sLock);

  if (!mpInstance) {
    mpInstance = new HalNfcAdaptation;
    mpInstance->InitializeHalDeviceContext();
  }
  return *mpInstance;
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::Initialize()
**
** Description: class initializer
**
** Returns:     none
**
*******************************************************************************/
void HalNfcAdaptation::Initialize() {
  const char *func = "HalNfcAdaptation::Initialize";
  const char *argv[] = {"halLibnfc"};
  // Init log tag
  base::CommandLine::Init(1, argv);
  nfc_storage_path = "data/vendor/nfc";
    // Android already logs thread_id, proc_id, timestamp, so disable those.
  logging::SetLogItems(false, false, false, false);
  initializeGlobalDebugEnabledFlag();
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", func);

  GKI_init();
  GKI_enable();
  GKI_create_task((TASKPTR)NFCA_TASK, BTU_TASK, (int8_t *)"NFCA_TASK", 0, 0,
                  (pthread_cond_t *)NULL, NULL);
  {
    HalAdaptationAutoThreadMutex guard(mCondVar);
    GKI_create_task((TASKPTR)Thread, MMI_TASK, (int8_t *)"NFCA_THREAD", 0, 0,
                    (pthread_cond_t *)NULL, NULL);
    mCondVar.wait();
  }

  configureRpcThreadpool(2, false);
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: exit", func);
}
#if (NXP_EXTNS == TRUE)
/*******************************************************************************
**
** Function:    HalNfcAdaptation::MinInitialize()
**
** Description: class initializer
**
** Returns:     none
**
*******************************************************************************/
void HalNfcAdaptation::MinInitialize() {
  const char *func = "HalNfcAdaptation::MinInitialize";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", func);
  GKI_init();
  GKI_enable();
  GKI_create_task((TASKPTR)NFCA_TASK, BTU_TASK, (int8_t *)"NFCA_TASK", 0, 0,
                  (pthread_cond_t *)NULL, NULL);
  {
    HalAdaptationAutoThreadMutex guard(mCondVar);
    GKI_create_task((TASKPTR)Thread, MMI_TASK, (int8_t *)"NFCA_THREAD", 0, 0,
                    (pthread_cond_t *)NULL, NULL);
    mCondVar.wait();
  }

  InitializeHalDeviceContext();
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: exit", func);
}
#endif

void HalNfcAdaptation::FactoryReset() {
  if (mHal_1_1 != nullptr) {
    mHal_1_1->factoryReset();
  }
}

void HalNfcAdaptation::DeviceShutdown() {
  if (mHal_1_1 != nullptr) {
    mHal_1_1->closeForPowerOffCase();
  }
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::Finalize()
**
** Description: class finalizer
**
** Returns:     none
**
*******************************************************************************/
void HalNfcAdaptation::Finalize() {
  const char *func = "HalNfcAdaptation::Finalize";
  HalAdaptationAutoThreadMutex a(sLock);

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", func);
  GKI_shutdown();

  mCallback = NULL;
  memset(&mHalEntryFuncs, 0, sizeof(mHalEntryFuncs));

  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: exit", func);
  mpInstance = NULL;
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::Dump
**
** Description: Native support for dumpsys function.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::Dump(__attribute__((unused)) int fd) { }

/*******************************************************************************
**
** Function:    HalNfcAdaptation::signal()
**
** Description: signal the CondVar to release the thread that is waiting
**
** Returns:     none
**
*******************************************************************************/
void HalNfcAdaptation::signal() { mCondVar.signal(); }

/*******************************************************************************
**
** Function:    HalNfcAdaptation::NFCA_TASK()
**
** Description: NFCA_TASK runs the GKI main task
**
** Returns:     none
**
*******************************************************************************/
uint32_t HalNfcAdaptation::NFCA_TASK(__attribute__((unused)) uint32_t arg) {
  const char *func = "HalNfcAdaptation::NFCA_TASK";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", func);
  GKI_run(0);
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: exit", func);
  return 0;
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::Thread()
**
** Description: Creates work threads
**
** Returns:     none
**
*******************************************************************************/
uint32_t HalNfcAdaptation::Thread(__attribute__((unused)) uint32_t arg) {
  const char *func = "HalNfcAdaptation::Thread";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: enter", func);

  {
    HalAdaptationThreadCondVar CondVar;
    HalAdaptationAutoThreadMutex guard(CondVar);
    GKI_create_task((TASKPTR)nfc_task, NFC_TASK, (int8_t *)"NFC_TASK", 0, 0,
                    (pthread_cond_t *)CondVar, (pthread_mutex_t *)CondVar);
    CondVar.wait();
  }

  HalNfcAdaptation::GetInstance().signal();

  GKI_exit_task(GKI_get_taskid());
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: exit", func);
  return 0;
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::GetHalEntryFuncs()
**
** Description: Get the set of HAL entry points.
**
** Returns:     Functions pointers for HAL entry points.
**
*******************************************************************************/
tHAL_NFC_ENTRY *HalNfcAdaptation::GetHalEntryFuncs() { return &mHalEntryFuncs; }

/*******************************************************************************
 **
 ** Function         phNxpNciHal_getchipType
 **
 ** Description      Gets the chipType from hal which is already configured
 **                  during init time.
 **
 ** Returns          chipType
 *******************************************************************************/
uint8_t HalNfcAdaptation::HalgetchipType() {
  const char* func = "NfcAdaptation::HalgetchipType";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s : Enter", func);
  return mHalNxpNfcLegacy->getchipType();
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::InitializeHalDeviceContext
**
** Description: Ask the generic Android HAL to find the Broadcom-specific HAL.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::InitializeHalDeviceContext() {
  const char *func = "HalNfcAdaptation::InitializeHalDeviceContext";

  mHalEntryFuncs.initialize = HalInitialize;
  mHalEntryFuncs.terminate = HalTerminate;
  mHalEntryFuncs.open = HalOpen;
  mHalEntryFuncs.close = HalClose;
  mHalEntryFuncs.core_initialized = HalCoreInitialized;
  mHalEntryFuncs.write = HalWrite;
  mHalEntryFuncs.getchipType = HalgetchipType;
  mHalEntryFuncs.prediscover = HalPrediscover;
  mHalEntryFuncs.control_granted = HalControlGranted;
  LOG(INFO) << StringPrintf("%s: Try INfcV1_1::getService()", func);
  mHal = mHal_1_1 = INfcV1_1::tryGetService();
  if (mHal_1_1 == nullptr) {
    LOG(INFO) << StringPrintf("%s: Try INfc::getService()", func);
    mHal = INfc::tryGetService();
  }
  // LOG_FATAL_IF(mHal == nullptr, "Failed to retrieve the NFC HAL!");
  if (mHal == nullptr) {
    LOG(INFO) << StringPrintf("Failed to retrieve the NFC HAL!");
  } else {
    LOG(INFO) << StringPrintf("%s: INfc::getService() returned %p (%s)", func,
                              mHal.get(),
                              (mHal->isRemote() ? "remote" : "local"));
  }
  mHal->linkToDeath(mNfcHalDeathRecipient, 0);
  LOG(INFO) << StringPrintf("%s: INxpNfc::getService()", func);
  mHalNxpNfc = INxpNfc::tryGetService();
  if (mHalNxpNfc == nullptr) {
    LOG(INFO) << StringPrintf("Failed to retrieve the NXPNFC HAL!");
  } else {
    LOG(INFO) << StringPrintf("%s: INxpNfc::getService() returned %p (%s)",
                              func, mHalNxpNfc.get(),
                              (mHalNxpNfc->isRemote() ? "remote" : "local"));
  }

  LOG(INFO) << StringPrintf("%s: INxpNfcLegacy::getService()", func);
  mHalNxpNfcLegacy = INxpNfcLegacy::tryGetService();
  if(mHalNxpNfcLegacy == nullptr) {
    LOG(INFO) << StringPrintf ( "Failed to retrieve the NXPNFC Legacy HAL!");
  } else {
    LOG(INFO) << StringPrintf("%s: INxpNfcLegacy::getService() returned %p (%s)", func, mHalNxpNfcLegacy.get(),
          (mHalNxpNfcLegacy->isRemote() ? "remote" : "local"));
  }
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalInitialize
**
** Description: Not implemented because this function is only needed
**              within the HAL.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalInitialize() {
  const char *func = "HalNfcAdaptation::HalInitialize";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalTerminate
**
** Description: Not implemented because this function is only needed
**              within the HAL.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalTerminate() {
  const char *func = "HalNfcAdaptation::HalTerminate";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalOpen
**
** Description: Turn on controller, download firmware.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalOpen(tHAL_NFC_CBACK *p_hal_cback,
                               tHAL_NFC_DATA_CBACK *p_data_cback) {
  const char *func = "HalNfcAdaptation::HalOpen";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
  mCallback = new NfcClientCallback(p_hal_cback, p_data_cback);
  if (mHal_1_1 != nullptr) {
    mHal_1_1->open_1_1(mCallback);
  } else {
    mHal->open(mCallback);
  }
}
/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalClose
**
** Description: Turn off controller.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalClose() {
  const char *func = "HalNfcAdaptation::HalClose";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
  mHal->close();
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalDeviceContextCallback
**
** Description: Translate generic Android HAL's callback into Broadcom-specific
**              callback function.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalDeviceContextCallback(nfc_event_t event,
                                                nfc_status_t event_status) {
  const char *func = "HalNfcAdaptation::HalDeviceContextCallback";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s: event=%u", func, event);
  if (mHalCallback)
    mHalCallback(event, (tHAL_NFC_STATUS)event_status);
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalDeviceContextDataCallback
**
** Description: Translate generic Android HAL's callback into Broadcom-specific
**              callback function.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalDeviceContextDataCallback(uint16_t data_len,
                                                    uint8_t *p_data) {
  const char *func = "HalNfcAdaptation::HalDeviceContextDataCallback";
  DLOG_IF(INFO, nfc_debug_enabled)
      << StringPrintf("%s: len=%u", func, data_len);
  if (mHalDataCallback)
    mHalDataCallback(data_len, p_data);
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalWrite
**
** Description: Write NCI message to the controller.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalWrite(uint16_t data_len, uint8_t *p_data) {
  const char *func = "HalNfcAdaptation::HalWrite";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
  ::android::hardware::nfc::V1_0::NfcData data;
  data.setToExternal(p_data, data_len);
  //phNxpNciHal_write(data_len, p_data);
  mHal_1_1->write(data);
}

#if (NXP_EXTNS == TRUE)
/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalGetFwDwnldFlag
**
** Description: Get FW Download Flag.
**
** Returns:     SUCESS or FAIL.
**
*******************************************************************************/
int HalNfcAdaptation::HalGetFwDwnldFlag(__attribute__((unused))
                                        uint8_t *fwDnldRequest) {
  const char *func = "HalNfcAdaptation::HalGetFwDwnldFlag";
  int status = NFA_STATUS_FAILED;
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s : Dummy", func);
  /*FIXME: Additional IOCTL type can be added for this
   * Instead of extra function pointer
   * This is required for a corner case or delayed FW download after SPI session
   * is completed
   * This shall be fixed with a better design later*/
  /*
  if (mHalDeviceContext)
  {
      status = mHalDeviceContext->check_fw_dwnld_flag(mHalDeviceContext,
  fwDnldRequest);
  }*/
  return status;
}
#endif

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalCoreInitialized
**
** Description: Adjust the configurable parameters in the controller.
**
** Returns:     None.
**
*******************************************************************************/
void HalNfcAdaptation::HalCoreInitialized(uint16_t data_len,
                                          uint8_t *p_core_init_rsp_params) {
  const char *func = "HalNfcAdaptation::HalCoreInitialized";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
  hidl_vec<uint8_t> data;
  data.setToExternal(p_core_init_rsp_params, data_len);
  mHal->coreInitialized(data);
}

/*******************************************************************************
**
** Function:    HalNfcAdaptation::HalPrediscover
**
** Description:     Perform any vendor-specific pre-discovery actions (if
**                  needed) If any actions were performed true will be returned,
**                  and HAL_PRE_DISCOVER_CPLT_EVT will notify when actions are
**                  completed.
**
** Returns:         true if vendor-specific pre-discovery actions initialized
**                  false if no vendor-specific pre-discovery actions are
**                  needed.
**
*******************************************************************************/
bool HalNfcAdaptation::HalPrediscover() {
  const char *func = "HalNfcAdaptation::HalPrediscover";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
  bool retval = false;
  if (mHal != nullptr) {
    mHal->prediscover();
  }
  /*FIXME: Additional IOCTL type can be added for this
   * Instead of extra function pointer
   * prediscover in HAL is empty dummy function
   */
  /*if (mHalDeviceContext)
  {
      retval = mHalDeviceContext->pre_discover (mHalDeviceContext);
  }*/
  return retval;
}

/*******************************************************************************
**
** Function:        HAL_NfcControlGranted
**
** Description:     Grant control to HAL control for sending NCI commands.
**                  Call in response to HAL_REQUEST_CONTROL_EVT.
**                  Must only be called when there are no NCI commands pending.
**                  HAL_RELEASE_CONTROL_EVT will notify when HAL no longer
**                  needs control of NCI.
**
** Returns:         void
**
*******************************************************************************/
void HalNfcAdaptation::HalControlGranted() {
  const char *func = "HalNfcAdaptation::HalControlGranted";
  DLOG_IF(INFO, nfc_debug_enabled) << StringPrintf("%s", func);
  mHal->controlGranted();
}

/*******************************************************************************
**
** Function:    HalAdaptationThreadMutex::HalAdaptationThreadMutex()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
HalAdaptationThreadMutex::HalAdaptationThreadMutex() {
  pthread_mutexattr_t mutexAttr;

  pthread_mutexattr_init(&mutexAttr);
  pthread_mutex_init(&mMutex, &mutexAttr);
  pthread_mutexattr_destroy(&mutexAttr);
}

/*******************************************************************************
**
** Function:    HalAdaptationThreadMutex::~HalAdaptationThreadMutex()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
HalAdaptationThreadMutex::~HalAdaptationThreadMutex() { pthread_mutex_destroy(&mMutex); }

/*******************************************************************************
**
** Function:    HalAdaptationThreadMutex::lock()
**
** Description: lock kthe mutex
**
** Returns:     none
**
*******************************************************************************/
void HalAdaptationThreadMutex::lock() { pthread_mutex_lock(&mMutex); }

/*******************************************************************************
**
** Function:    HalAdaptationThreadMutex::unblock()
**
** Description: unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
void HalAdaptationThreadMutex::unlock() { pthread_mutex_unlock(&mMutex); }

/*******************************************************************************
**
** Function:    HalAdaptationThreadCondVar::HalAdaptationThreadCondVar()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
HalAdaptationThreadCondVar::HalAdaptationThreadCondVar() {
  pthread_condattr_t CondAttr;

  pthread_condattr_init(&CondAttr);
  pthread_cond_init(&mCondVar, &CondAttr);

  pthread_condattr_destroy(&CondAttr);
}

/*******************************************************************************
**
** Function:    HalAdaptationThreadCondVar::~HalAdaptationThreadCondVar()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
HalAdaptationThreadCondVar::~HalAdaptationThreadCondVar() { pthread_cond_destroy(&mCondVar); }

/*******************************************************************************
**
** Function:    HalAdaptationThreadCondVar::wait()
**
** Description: wait on the mCondVar
**
** Returns:     none
**
*******************************************************************************/
void HalAdaptationThreadCondVar::wait() {
  pthread_cond_wait(&mCondVar, *this);
  pthread_mutex_unlock(*this);
}

/*******************************************************************************
**
** Function:    HalAdaptationThreadCondVar::signal()
**
** Description: signal the mCondVar
**
** Returns:     none
**
*******************************************************************************/
void HalAdaptationThreadCondVar::signal() {
  HalAdaptationAutoThreadMutex a(*this);
  pthread_cond_signal(&mCondVar);
}

/*******************************************************************************
**
** Function:    HalAdaptationAutoThreadMutex::HalAdaptationAutoThreadMutex()
**
** Description: class constructor, automatically lock the mutex
**
** Returns:     none
**
*******************************************************************************/
HalAdaptationAutoThreadMutex::HalAdaptationAutoThreadMutex(HalAdaptationThreadMutex &m) : mm(m) { mm.lock(); }

/*******************************************************************************
**
** Function:    HalAdaptationAutoThreadMutex::~HalAdaptationAutoThreadMutex()
**
** Description: class destructor, automatically unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
HalAdaptationAutoThreadMutex::~HalAdaptationAutoThreadMutex() { mm.unlock(); }
