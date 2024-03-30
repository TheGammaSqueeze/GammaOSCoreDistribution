/******************************************************************************
 *
 *  Copyright 2018-2020 NXP
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
#define LOG_TAG "NxpEseHal"
#include <log/log.h>

#include <errno.h>
#include <fcntl.h>
#include <phNxpEsePal.h>
#include <phNxpEse_Internal.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include "phNxpEseFeatures.h"
#include "phNxpEse_Spm.h"

/*********************** Global Variables *************************************/
extern bool ese_debug_enabled;

static void* pEseDeviceHandle = NULL;
#define MAX_ESE_ACCESS_TIME_OUT_MS 2000 /*2 seconds*/

static spm_power_t gsCurIoctlRequest;
extern phNxpEse_Context_t nxpese_ctxt;
extern void phNxpEse_GetMaxTimer(unsigned long *pMaxTimer);
static void phNxpEse_secureTimerExpired(union sigval sv);
void phNxpEse_secureTimerStop();
static ESESTATUS phNxpEse_secureTimerStart(unsigned long timeInMilliSec);
static IntervalTimer &getSecureTimerInstance();
/* Dummy handle for ioctl call in case secure timer expired*/
#define SECURE_TIMER_MAGIC_HANDLE 0xFF

/**
 * \addtogroup SPI_Power_Management
 *
 * @{ */
/******************************************************************************
\section Introduction Introduction

 * This module provide power request to Pn54x nfc-i2c driver, it cheks if
 * wired access is already granted. It should have access to pn54x drive.
 * Below are the apis provided by the SPM module.
 ******************************************************************************/
/******************************************************************************
 * Function         phNxpEse_SPM_Init
 *
 * Description      This function opens the nfc i2c driver to manage power
 *                  and synchronization for ese secure element.
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_Init(void* pDevHandle) {
  ESESTATUS status = ESESTATUS_SUCCESS;
  pEseDeviceHandle = pDevHandle;
  if (NULL == pEseDeviceHandle) {
    ALOGE("%s : failed, device handle is null", __FUNCTION__);
    status = ESESTATUS_FAILED;
  }
  ALOGD_IF(ese_debug_enabled, "%s : exit status = %d", __FUNCTION__, status);

  return status;
}

/******************************************************************************
 * Function         phNxpEse_SPM_DeInit
 *
 * Description      This function closes the nfc i2c driver node.
 *
 * Returns          Always returns ESESTATUS_SUCCESS
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_DeInit(void) {
  pEseDeviceHandle = NULL;
  return ESESTATUS_SUCCESS;
}

/******************************************************************************
 * Function         phNxpEse_SPM_ConfigPwr
 *
 * Description      This function request to the nfc i2c driver
 *                  to enable/disable power to ese. This api should be called
 *before
 *                  sending any apdu to ese/once apdu exchange is done.
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_ConfigPwr(spm_power_t arg) {
  int32_t ret = -1;
  unsigned long timeInMilliSec;
  ESESTATUS wSpmStatus = ESESTATUS_SUCCESS;
  spm_state_t current_spm_state = SPM_STATE_INVALID;

  ALOGD_IF(ese_debug_enabled, "%s Enter", __FUNCTION__);
  ALOGD_IF(ese_debug_enabled,
           "%ssecureTimer1 0x%x secureTimer2 0x%x secureTimer3 0x%x",
           __FUNCTION__, nxpese_ctxt.secureTimerParams.secureTimer1,
           nxpese_ctxt.secureTimerParams.secureTimer2,
           nxpese_ctxt.secureTimerParams.secureTimer3);
  gsCurIoctlRequest = arg;
  phNxpEse_GetMaxTimer(&timeInMilliSec);
  if (timeInMilliSec != 0 &&
      (arg == SPM_POWER_DISABLE || arg == SPM_POWER_RESET)) {
    wSpmStatus = phNxpEse_secureTimerStart(timeInMilliSec);
    if (wSpmStatus != ESESTATUS_SUCCESS) {
      ALOGE("%s phNxpEse_secureTimerStart: failed", __FUNCTION__);
    } else {
      ALOGD("%s Disable Ese power After Secure Timer Expired", __FUNCTION__);
      wSpmStatus = ESESTATUS_SUCCESS;
      ret = 0;
    }
  } else {
    ret = phPalEse_ioctl(phPalEse_e_ChipRst, pEseDeviceHandle, arg);
  }
  switch (arg) {
    case SPM_POWER_DISABLE: {
      if (ret < 0) {
        ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
        wSpmStatus = ESESTATUS_FAILED;
      }
    } break;
    case SPM_POWER_ENABLE: {
      if (ret < 0) {
        ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
        if (errno == -EBUSY) {
          wSpmStatus = phNxpEse_SPM_GetState(&current_spm_state);
          if (wSpmStatus != ESESTATUS_SUCCESS) {
            ALOGE(" %s : phNxpEse_SPM_GetPwrState Failed", __FUNCTION__);
            return wSpmStatus;
          } else {
            if (current_spm_state & SPM_STATE_DWNLD) {
              wSpmStatus = ESESTATUS_DWNLD_BUSY;
            } else {
              wSpmStatus = ESESTATUS_BUSY;
            }
          }

        } else {
          wSpmStatus = ESESTATUS_FAILED;
        }
      }
    } break;
    case SPM_POWER_RESET: {
      if (ret < 0) {
        ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
        if (errno == -EBUSY) {
          wSpmStatus = phNxpEse_SPM_GetState(&current_spm_state);
          if (wSpmStatus != ESESTATUS_SUCCESS) {
            ALOGE(" %s : phNxpEse_SPM_GetPwrState Failed", __FUNCTION__);
            return wSpmStatus;
          } else {
            if (current_spm_state & SPM_STATE_DWNLD) {
              wSpmStatus = ESESTATUS_DWNLD_BUSY;
            } else {
              wSpmStatus = ESESTATUS_BUSY;
            }
          }
        } else {
          wSpmStatus = ESESTATUS_FAILED;
        }
      }
    } break;
    case SPM_POWER_PRIO_ENABLE: {
      if (ret < 0) {
        ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
        if (errno == -EBUSY) {
          wSpmStatus = phNxpEse_SPM_GetState(&current_spm_state);
          if (wSpmStatus != ESESTATUS_SUCCESS) {
            ALOGE(" %s : phNxpEse_SPM_GetPwrState Failed", __FUNCTION__);
            return wSpmStatus;
          } else {
            if (current_spm_state & SPM_STATE_DWNLD) {
              wSpmStatus = ESESTATUS_DWNLD_BUSY;
            } else {
              wSpmStatus = ESESTATUS_BUSY;
            }
          }

        } else {
          wSpmStatus = ESESTATUS_FAILED;
        }
      }
    } break;
    case SPM_POWER_PRIO_DISABLE: {
      if (ret < 0) {
        ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
        wSpmStatus = ESESTATUS_FAILED;
      }
    } break;
  }
  return wSpmStatus;
}

/******************************************************************************
 * Function         phNxpEse_SPM_EnablePwr
 *
 * Description      This function request to the nfc i2c driver
 *                  to enable power to ese. This api should be called before
 *                  sending any apdu to ese.
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_EnablePwr(void) {
  int32_t ret = -1;
  ESESTATUS wSpmStatus = ESESTATUS_SUCCESS;
  spm_state_t current_spm_state = SPM_STATE_INVALID;
  ALOGD_IF(ese_debug_enabled, "%s : phNxpEse_SPM_EnablePwr is set to  = 0x%d",
           __FUNCTION__, 0);
  ret = phPalEse_ioctl(phPalEse_e_ChipRst, pEseDeviceHandle, 0);
  if (ret < 0) {
    ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
    if (errno == -EBUSY) {
      wSpmStatus = phNxpEse_SPM_GetState(&current_spm_state);
      if (wSpmStatus != ESESTATUS_SUCCESS) {
        ALOGE(" %s : phNxpEse_SPM_GetPwrState Failed", __FUNCTION__);
        return wSpmStatus;
      } else {
        if (current_spm_state == SPM_STATE_DWNLD) {
          wSpmStatus = ESESTATUS_DWNLD_BUSY;
        } else {
          wSpmStatus = ESESTATUS_BUSY;
        }
      }

    } else {
      wSpmStatus = ESESTATUS_FAILED;
    }
  }

  return wSpmStatus;
}

/******************************************************************************
 * Function         phNxpEse_SPM_DisablePwr
 *
 * Description      This function request to the nfc i2c driver
 *                  to disable power to ese. This api should be called
 *                  once apdu exchange is done.
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_DisablePwr(void) {
  int32_t ret = -1;
  ESESTATUS status = ESESTATUS_SUCCESS;
  ALOGD_IF(ese_debug_enabled, "%s : phNxpEse_SPM_DisablePwr is set to  = 0x%d",
           __FUNCTION__, 1);
  ret = phPalEse_ioctl(phPalEse_e_ChipRst, pEseDeviceHandle, 1);
  if (ret < 0) {
    ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
    status = ESESTATUS_FAILED;
  }

  return status;
}
/******************************************************************************
 * Function         phNxpEse_SPM_SetPwrScheme
 *
 * Description      This function request to the nfc i2c driver
 *                  to set the chip type and power scheme.
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_SetPwrScheme(long arg) {
  int32_t ret = -1;
  ESESTATUS status = ESESTATUS_SUCCESS;

  ALOGD_IF(ese_debug_enabled, "%s : Power scheme is set to  = 0x%ld",
           __FUNCTION__, arg);
  ret = phPalEse_ioctl(phPalEse_e_SetPowerScheme, pEseDeviceHandle, arg);
  if (ret < 0) {
    ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
    status = ESESTATUS_FAILED;
  }

  return status;
}

/******************************************************************************
 * Function         phNxpEseP61_SPM_EnableDisablePwrCntrl
 *
 * Description      This function request to the nfc i2c driver
 *                  to set the chip type and power scheme.
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_DisablePwrControl(unsigned long arg) {
  int32_t ret = -1;
  ESESTATUS status = ESESTATUS_SUCCESS;

  ALOGD_IF(ese_debug_enabled, "%s : Inhibit power control is set to  = 0x%ld",
           __FUNCTION__, arg);
  ret = phPalEse_ioctl(phPalEse_e_ChipRst, pEseDeviceHandle, arg);
  if (ret < 0) {
    ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
    status = ESESTATUS_FAILED;
  }

  return status;
}

/******************************************************************************
 * Function         phNxpEse_SPM_GetState
 *
 * Description      This function gets the current power state of ESE
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_GetState(spm_state_t* current_state) {
  int32_t ret = -1;
  ESESTATUS status = ESESTATUS_SUCCESS;
  spm_state_t ese_current_state  = SPM_STATE_INVALID;

  if (current_state == NULL) {
    ALOGE("%s : failed Invalid argument", __FUNCTION__);
    return ESESTATUS_FAILED;
  }
  ret = phPalEse_ioctl(phPalEse_e_GetSPMStatus, pEseDeviceHandle,
                       (unsigned long)&ese_current_state);
  if (ret < 0) {
    ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
    status = ESESTATUS_FAILED;
  } else {
    *current_state = ese_current_state; /* Current ESE state */
  }

  return status;
}
#ifdef NXP_ESE_JCOP_DWNLD_PROTECTION
/******************************************************************************
 * Function         phNxpEse_SPM_SetEseClientUpdateState
 *
 * Description      This function is used to set the ese Update state
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
ESESTATUS phNxpEse_SPM_SetEseClientUpdateState(long arg) {
  int ret = -1;
  ESESTATUS status = ESESTATUS_SUCCESS;

  ALOGD_IF(ese_debug_enabled, "%s :phNxpEse_SPM_SetEseClientUpdateState  = 0x%ld", __FUNCTION__, arg);
  ret = phPalEse_ioctl(phPalEse_e_SetEseUpdateStatus, pEseDeviceHandle, arg);
  if (ret < 0) {
    ALOGE("%s : failed errno = 0x%x", __FUNCTION__, errno);
    status = ESESTATUS_FAILED;
  }

  return status;
}
#endif

/******************************************************************************
 * Function         getSecureTimerInstance
 *
 * Description      This function used to get SecureTimer Instance
 *
 * Returns          returns IntervalTimer instance reference
 *
 ******************************************************************************/
static IntervalTimer &getSecureTimerInstance() {
  static IntervalTimer sTimerInstance;
  return sTimerInstance;
}

/******************************************************************************
 * Function         phNxpEse_secureTimerExpired
 *
 * Description      This callback function is triggered during secure timer
 *                  timeout
 *
 * Returns          None
 *
 ******************************************************************************/
static void phNxpEse_secureTimerExpired(union sigval) {
  int32_t ret = -1;
  ALOGD_IF(ese_debug_enabled, "phNxpEse_secureTimerExpired callback triggered");
  if (gsCurIoctlRequest == SPM_POWER_DISABLE) {
    ret = phPalEse_ioctl(phPalEse_e_ChipRst,
                         (void *)((intptr_t)SECURE_TIMER_MAGIC_HANDLE),
                         gsCurIoctlRequest);
  }
  getSecureTimerInstance().kill();
}

/******************************************************************************
 * Function         phNxpEse_secureTimerStart
 *
 * Description      This function is called during response timer values for
 *                  ese iterface reset and end of APDU  are non zero,To
 *                  synchronize secure timer between Jcop and MW.
 *                  Timer argutment in ms.
 *
 *
 * Returns          This function return ESESTATUS_SUCCES (0) in case of success
 *                  In case of failure returns other failure value.
 *
 ******************************************************************************/
ESESTATUS phNxpEse_secureTimerStart(unsigned long timeInMilliSec) {
  ESESTATUS wConfigStatus = ESESTATUS_SUCCESS;
  ALOGD_IF(ese_debug_enabled,
           "Enter phNxpEse_secureTimerStart time value : %ld ms",
           timeInMilliSec);
  if (getSecureTimerInstance().set(timeInMilliSec,
                                   phNxpEse_secureTimerExpired) == true) {
    ALOGD_IF(ese_debug_enabled, "secure timer started........");
  } else {
    ALOGD_IF(ese_debug_enabled, "failed to set secure timer");
    wConfigStatus = ESESTATUS_FAILED;
  }

  return wConfigStatus;
}

/*******************************************************************************
**
** Function         phPalEse_spi_stop_debounce_timer
**
** Description      Stops the secure timer.
**
** Parameters       none
**
** Returns          none
**
*******************************************************************************/
void phNxpEse_secureTimerStop() {
  ALOGD_IF(ese_debug_enabled, "Stopping Secure timer...");
  getSecureTimerInstance().kill();
}
