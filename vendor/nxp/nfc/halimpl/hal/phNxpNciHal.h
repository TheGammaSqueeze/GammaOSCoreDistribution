/*
 * Copyright (C) 2015-2020 NXP Semiconductors
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
#ifndef _PHNXPNCIHAL_H_
#define _PHNXPNCIHAL_H_

#include "NxpMfcReader.h"
#include <hardware/nfc.h>
#include <phNxpNciHal_utils.h>
#include "NxpNfcCapability.h"
#include <vendor/nxp/nxpnfc/2.0/types.h>
#include "DwpEseUpdater.h"
#include "EseUpdateChecker.h"

using namespace std;

/********************* Definitions and structures *****************************/
#define MAX_RETRY_COUNT 5
#define NCI_MAX_DATA_LEN 300
#define NCI_POLL_DURATION 500
#define HAL_NFC_ENABLE_I2C_FRAGMENTATION_EVT 0x07
#define HAL_NFC_POST_MIN_INIT_CPLT_EVT  0x09
#define NXP_STAG_TIMEOUT_BUF_LEN 0x04 /*FIXME:TODO:remove*/
#define NXP_WIREDMODE_RESUME_TIMEOUT_LEN 0x04
#define NFCC_DECIDES 00
#define POWER_ALWAYS_ON 01
#define LINK_ALWAYS_ON 02
#undef P2P_PRIO_LOGIC_HAL_IMP

#define NCI_VERSION_2_0 0x20
#define NCI_VERSION_1_1 0x11
#define NCI_VERSION_1_0 0x10
#define NCI_VERSION_UNKNOWN 0x00

/*Mem alloc with 8 byte alignment*/
#define size_align(sz) ((((sz)-1) | 7) + 1)
#define nxp_malloc(size) malloc(size_align((size)))

typedef void(phNxpNciHal_control_granted_callback_t)();

/*ROM CODE VERSION FW*/
#define FW_MOBILE_ROM_VERSION_PN551 0x10
#define FW_MOBILE_ROM_VERSION_PN553 0x11
#define FW_MOBILE_ROM_VERSION_PN548AD 0x10
#define FW_MOBILE_ROM_VERSION_PN547C2 0x08
#define FW_MOBILE_ROM_VERSION_PN557 0x12

#define FW_DBG_REASON_AVAILABLE     (0xA3)


/* NCI Data */
#define NCI_MT_CMD  0x20
#define NCI_MT_RSP  0x40
#define NCI_MT_NTF  0x60

#define CORE_RESET_TRIGGER_TYPE_CORE_RESET_CMD_RECEIVED 0x02
#define CORE_RESET_TRIGGER_TYPE_POWERED_ON              0x01
#define NCI_MSG_CORE_RESET           0x00
#define NCI_MSG_CORE_INIT            0x01
#define NCI_MT_MASK                  0xE0
#define NCI_OID_MASK                 0x3F

#define NXP_MAX_CONFIG_STRING_LEN 260
#define NCI_HEADER_SIZE 3

#define MAX_IOCTL_TRANSCEIVE_CMD_LEN 256
#define MAX_IOCTL_TRANSCEIVE_RESP_LEN 256

typedef struct nci_data {
  uint16_t len;
  uint8_t p_data[NCI_MAX_DATA_LEN];
} nci_data_t;

typedef enum {
  HAL_STATUS_CLOSE = 0,
  HAL_STATUS_OPEN,
  HAL_STATUS_MIN_OPEN
} phNxpNci_HalStatus;
typedef enum {
  GPIO_UNKNOWN = 0x00,
  GPIO_STORE = 0x01,
  GPIO_STORE_DONE = 0x02,
  GPIO_RESTORE = 0x10,
  GPIO_RESTORE_DONE = 0x20,
  GPIO_CLEAR = 0xFF
} phNxpNciHal_GpioInfoState;

enum NxpNfcHalStatus {
    /** In case of an error, HCI network needs to be re-initialized */
    HAL_NFC_STATUS_RESTART = 0x30,
    HAL_NFC_HCI_NV_RESET = 0x40,
    HAL_NFC_CONFIG_ESE_LINK_COMPLETE = 0x50
};

typedef struct phNxpNciGpioInfo {
  phNxpNciHal_GpioInfoState state;
  uint8_t values[2];
} phNxpNciGpioInfo_t;

extern ESE_UPDATE_STATE eseUpdateSpi;
extern ESE_UPDATE_STATE eseUpdateDwp;

/* Macros to enable and disable extensions */
#define HAL_ENABLE_EXT() (nxpncihal_ctrl.hal_ext_enabled = 1)
#define HAL_DISABLE_EXT() (nxpncihal_ctrl.hal_ext_enabled = 0)

typedef struct phNxpNciInfo {
  uint8_t   nci_version;
  bool_t    wait_for_ntf;
}phNxpNciInfo_t;
/* NCI Control structure */
typedef struct phNxpNciHal_Control {
  phNxpNci_HalStatus halStatus; /* Indicate if hal is open or closed */
  pthread_t client_thread;      /* Integration thread handle */
  uint8_t thread_running;       /* Thread running if set to 1, else set to 0 */
  phLibNfc_sConfig_t gDrvCfg;   /* Driver config data */

  /* Rx data */
  uint8_t* p_rx_data;
  uint16_t rx_data_len;

  /* Rx data */
  uint8_t* p_rx_ese_data;
  uint16_t rx_ese_data_len;
  /* libnfc-nci callbacks */
  nfc_stack_callback_t* p_nfc_stack_cback;
  nfc_stack_data_callback_t* p_nfc_stack_data_cback;

  /* control granted callback */
  phNxpNciHal_control_granted_callback_t* p_control_granted_cback;

  /* HAL open status */
  bool_t hal_open_status;

  bool_t is_wait_for_ce_ntf;

  /* HAL extensions */
  uint8_t hal_ext_enabled;

  /* Waiting semaphore */
  phNxpNciHal_Sem_t ext_cb_data;
  sem_t syncSpiNfc;

  uint16_t cmd_len;
  uint8_t p_cmd_data[NCI_MAX_DATA_LEN];
  uint16_t rsp_len;
  uint8_t p_rsp_data[NCI_MAX_DATA_LEN];

  /* retry count used to force download */
  uint16_t retry_cnt;
  uint8_t read_retry_cnt;
  phNxpNciInfo_t nci_info;
  uint8_t hal_boot_mode;
  tNFC_chipType chipType;
  /* to store and restore gpio values */
  phNxpNciGpioInfo_t phNxpNciGpioInfo;
  bool bIsForceFwDwnld;
  bool isHciCfgEvtRequested;
} phNxpNciHal_Control_t;

typedef struct {
  uint8_t fw_update_reqd;
  uint8_t rf_update_reqd;
} phNxpNciHal_FwRfupdateInfo_t;

typedef struct phNxpNciClock {
  bool_t isClockSet;
  uint8_t p_rx_data[20];
} phNxpNciClock_t;

typedef struct phNxpNciRfSetting {
  bool_t isGetRfSetting;
  uint8_t p_rx_data[20];
} phNxpNciRfSetting_t;

/*set config management*/

#define TOTAL_DURATION 0x00
#define ATR_REQ_GEN_BYTES_POLL 0x29
#define ATR_REQ_GEN_BYTES_LIS 0x61
#define LEN_WT 0x60

/*Whenever a new get cfg need to be sent,
 * array must be updated with defined config type*/
static const uint8_t get_cfg_arr[] = {TOTAL_DURATION, ATR_REQ_GEN_BYTES_POLL,
                                      ATR_REQ_GEN_BYTES_LIS, LEN_WT};

typedef enum {
  EEPROM_RF_CFG,
  EEPROM_FW_DWNLD,
  EEPROM_WIREDMODE_RESUME_TIMEOUT,
  EEPROM_ESE_SVDD_POWER,
  EEPROM_ESE_POWER_EXT_PMU,
  EEPROM_PROP_ROUTING,
  EEPROM_ESE_SESSION_ID,
  EEPROM_SWP1_INTF,
  EEPROM_SWP1A_INTF,
  EEPROM_SWP2_INTF,
  EEPROM_NDEF_INTF_CFG
} phNxpNci_EEPROM_request_type_t;

typedef enum {
  phNxpNciHalNfc_e_SetIdleMode, /* Set the current mode of operation to Idle*/
  phNxpNciHalNfc_e_SetWiredMode,  /* Set the current mode of operation to Wired*/
} phNxpNfcHalEseState;

typedef enum {
  phNxpNfcHciInitStatus_e_Complete = 0x00,
  phNxpNfcHciInitStatus_e_Start = 0x01
} phNxpNfcHciInitStatus;

typedef struct phNxpNci_Extn_Cmd{
  uint16_t cmd_len;
  uint8_t p_cmd[MAX_IOCTL_TRANSCEIVE_CMD_LEN];
}phNxpNci_Extn_Cmd_t;

typedef struct phNxpNci_Extn_Resp{
  uint32_t status;
  uint16_t rsp_len;
  uint8_t p_rsp[MAX_IOCTL_TRANSCEIVE_RESP_LEN];
}phNxpNci_Extn_Resp_t;

typedef struct phNxpNci_EEPROM_info {
  uint8_t request_mode;
  phNxpNci_EEPROM_request_type_t request_type;
  uint8_t update_mode;
  uint8_t* buffer;
  uint8_t bufflen;
} phNxpNci_EEPROM_info_t;

typedef struct phNxpNci_getCfg_info {
  bool_t isGetcfg;
  uint8_t total_duration[4];
  uint8_t total_duration_len;
  uint8_t atr_req_gen_bytes[48];
  uint8_t atr_req_gen_bytes_len;
  uint8_t atr_res_gen_bytes[48];
  uint8_t atr_res_gen_bytes_len;
  uint8_t pmid_wt[3];
  uint8_t pmid_wt_len;
} phNxpNci_getCfg_info_t;

typedef enum {
  NFC_FORUM_PROFILE,
  EMV_CO_PROFILE,
  INVALID_PROFILe
} phNxpNciProfile_t;

typedef enum {
  NFC_NORMAL_BOOT_MODE,
  NFC_FAST_BOOT_MODE,
  NFC_OSU_BOOT_MODE
} phNxpNciBootMode;
/* NXP Poll Profile control structure */
typedef struct phNxpNciProfile_Control {
  phNxpNciProfile_t profile_type;
  uint8_t bClkSrcVal; /* Holds the System clock source read from config file */
  uint8_t
      bClkFreqVal;  /* Holds the System clock frequency read from config file */
  uint8_t bTimeout; /* Holds the Timeout Value */
} phNxpNciProfile_Control_t;

struct phNxpNfcScrResetEmvcoCmd{
  uint64_t len;
  uint8_t cmd[10];
};

struct phNxpNfcHalConfig {
  uint8_t ese_listen_tech_mask;
  uint8_t default_nfcee_disc_timeout;
  uint8_t default_nfcee_timeout;
  uint8_t ese_wired_prt_mask;
  uint8_t uicc_wired_prt_mask;
  uint8_t wired_mode_rf_field_enable;
  uint8_t aid_block_route;
  uint8_t esePowerDhControl;
  uint8_t tagOpTimeout;
  uint8_t loaderServiceVersion;
  uint8_t defaultNfceeDiscTimeout;
  uint8_t dualUiccEnable;
  uint8_t ceRouteStrictDisable;
  uint32_t osDownloadTimeoutValue;
  uint8_t defaultAidRoute;
  uint8_t defaultAidPwrState;
  uint8_t defaultRoutePwrState;
  uint8_t defaultOffHostPwrState;
  uint8_t jcopDlAtBootEnable;
  uint8_t defaultNfceeTimeout;
  uint8_t nxpNfcChip;
  uint8_t coreScrnOffAutonomousEnable;
  uint8_t p61LsDefaultInterface;
  uint8_t p61JcopDefaultInterface;
  uint8_t agcDebugEnable;
  uint8_t felicaCltPowerState;
  uint32_t cmdRspTimeoutValue;
  uint8_t checkDefaultProtoSeId;
  uint8_t nfccPassiveListenTimeout;
  uint32_t nfccStandbyTimeout;
  uint32_t wmMaxWtxCount;
  uint32_t nfccRfFieldEventTimeout;
  uint8_t allowWiredInMifareDesfireClt;
  uint8_t dwpIntfResetEnable;
  uint8_t nxpLogHalLoglevel;
  uint8_t nxpLogExtnsLogLevel;
  uint8_t nxpLogTmlLogLevel;
  uint8_t nxpLogFwDnldLogLevel;
  uint8_t nxpLogNcixLogLevel;
  uint8_t nxpLogNcirLogLevel;
  uint8_t scrCfgFormat;
  uint8_t etsiReaderEnable;
  uint8_t techAbfRoute;
  uint8_t techAbfPwrState;
  uint8_t wTagSupport;
  uint8_t t4tNfceePwrState;
  phNxpNfcScrResetEmvcoCmd scrResetEmvco;
};
/* Internal messages to handle callbacks */
#define NCI_HAL_OPEN_CPLT_MSG 0x411
#define NCI_HAL_CLOSE_CPLT_MSG 0x412
#define NCI_HAL_POST_INIT_CPLT_MSG 0x413
#define NCI_HAL_PRE_DISCOVER_CPLT_MSG 0x414
#define NCI_HAL_ERROR_MSG 0x415
#define NCI_HAL_HCI_NETWORK_RESET_MSG 0x416
#define NCI_HAL_RX_MSG 0xF01
#define NCI_HAL_POST_MIN_INIT_CPLT_MSG 0xF02
#define NCIHAL_CMD_CODE_LEN_BYTE_OFFSET (2U)
#define NCIHAL_CMD_CODE_BYTE_LEN (3U)

/******************** NCI HAL exposed functions *******************************/

int phNxpNciHal_check_ncicmd_write_window(uint16_t cmd_len, uint8_t* p_cmd);
void phNxpNciHal_request_control(void);
void phNxpNciHal_release_control(void);
NFCSTATUS phNxpNciHal_send_get_cfgs();
int phNxpNciHal_write_unlocked(uint16_t data_len, const uint8_t* p_data);
NFCSTATUS request_EEPROM(phNxpNci_EEPROM_info_t* mEEPROM_info);
NFCSTATUS phNxpNciHal_send_nfcee_pwr_cntl_cmd(uint8_t type);
string phNxpNciHal_getSystemProperty(string key);
bool phNxpNciHal_setSystemProperty(string key, string value);
void seteSEClientState(uint8_t state);
void eSEClientUpdate_NFC_Thread();
bool phNxpNciHal_Abort();
bool getJcopUpdateRequired();
bool getLsUpdateRequired();

/*******************************************************************************
**
** Function         phNxpNciHal_configFeatureList
**
** Description      Configures the featureList based on chip type
**                  HW Version information number will provide chipType.
**                  HW Version can be obtained from CORE_INIT_RESPONSE(NCI 1.0)
**                  or CORE_RST_NTF(NCI 2.0)
**
** Parameters       CORE_INIT_RESPONSE/CORE_RST_NTF, len
**
** Returns          none
*******************************************************************************/
void phNxpNciHal_configFeatureList(uint8_t* init_rsp, uint16_t rsp_len);

/*******************************************************************************
**
** Function         phNxpNciHal_getChipType
**
** Description      Gets the chipType which is configured during bootup
**
** Parameters       none
**
** Returns          chipType
*******************************************************************************/
tNFC_chipType phNxpNciHal_getChipType();

/*******************************************************************************
**
** Function         phNxpNciHal_getNxpConfig
**
** Description      Read vendor configuration macro values
**
** Parameters       ioctl input/output struct.
**
** Returns          none
*******************************************************************************/
void phNxpNciHal_getNxpConfig(phNxpNfcHalConfig *pNxpNfcHalConfig);
/******************************************************************************
 * Function         phNxpNciHal_getNxpTransitConfig
 *
 * Description      This function overwrite libnfc-nxpTransit.conf file
 *                  with transitConfValue.
 *
 * Returns          status.
 *
 ******************************************************************************/
bool phNxpNciHal_setNxpTransitConfig(char *transitConfValue);
#endif /* _PHNXPNCIHAL_H_ */

/******************************************************************************
 * Function         phNxpNciHal_setEseState
 *
 * Description      This function is called for to update ese state
 *
 * Returns          void.
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_setEseState(phNxpNfcHalEseState eSEstate);

/*******************************************************************************
 **
 ** Function         phNxpNciHal_getchipType
 **
 ** Description      Gets the chipType from hal which is already configured
 **                  during init time.
 **
 ** Returns          chipType
 *******************************************************************************/
uint8_t phNxpHal_getchipType();

/*******************************************************************************
**
** Function         phNxpNciHal_setNfcServicePid
**
** Description      This function request to pn54x driver to
**                  update NFC service process ID for signalling.
**
** Returns          0 if api call success, else -1
**
*******************************************************************************/
uint16_t phNxpNciHal_setNfcServicePid(uint64_t pid);

/******************************************************************************
 * Function         phNxpNciHal_getEseState
 *
 * Description      This function is called for to get  ese state
 *
 * Returns          void.
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_getEseState();

/******************************************************************************
 * Function         phNxpNciHal_ReleaseSVDDWait
 *
 * Description      This function release wait for svdd change
 *                  of P61.
 *
 * Returns          NFCSTATUS.
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_ReleaseSVDDWait(uint32_t level);

/******************************************************************************
 * Function         phNxpNciHal_ReleaseDWPOnOffWait
 *
 * Description      This function release wait for DWP On/Off
 *                  of P73. output returned as Status
 * Returns          NFCSTATUS.
 *
 ******************************************************************************/
NFCSTATUS phNxpNciHal_ReleaseDWPOnOffWait(uint32_t level);

/******************************************************************************
 * Function         phNxpNciHal_getSPMStatus
 *
 * Description      This function gets the SPM state before FW download
 *
 * Returns          0 as success -1 as failed.
 *
 *******************************************************************************/
int phNxpNciHal_getSPMStatus(uint32_t level);

/******************************************************************************
 * Function         phNxpNciHal_hciInitUpdateState
 *
 * Description      This function Sends HCI Events to nfc HAL
 *
 * Returns          0 as success -1 as failed.
 *
 *******************************************************************************/
int32_t phNxpNciHal_hciInitUpdateState(phNxpNfcHciInitStatus HciStatus);

/******************************************************************************
 * Function         phNxpNciHal_hciInitUpdateStateComplete
 *
 * Description       This function posts event HAL_NFC_CONFIG_ESE_LINK_COMPLETE
 *
 * Returns          0 as success -1 as failed..
 *
 *******************************************************************************/
int32_t phNxpNciHal_hciInitUpdateStateComplete();

/******************************************************************************
 * Function         phNxpNciHal_GetCachedNfccConfig
 *
 * Description      This function receives the cached configinfo.
 *
 * Returns          void.
 *
 *******************************************************************************/
void phNxpNciHal_GetCachedNfccConfig(phNxpNci_getCfg_info_t *pGetCfg_info);

/*******************************************************************************
**
** Function         phNxpNciHal_resetEse
**
** Description      It shall be used to to reset eSE by proprietary command.
**
** Parameters       None
**
** Returns          status of eSE reset response
*******************************************************************************/
NFCSTATUS phNxpNciHal_resetEse(uint64_t resetType);

/******************************************************************************
 * Function         phNxpNciHal_nciTransceive
 *
 * Description      This function does tarnsceive of nci command
 *
 * Returns          void.
 *
 *******************************************************************************/
void phNxpNciHal_nciTransceive(phNxpNci_Extn_Cmd_t *in, phNxpNci_Extn_Resp_t *out);

/******************************************************************************
 * Function         phNxpNciHal_nciTransceive
 *
 * Description      This function does tarnsceive of proprietary nci command
 *
 * Returns          void.
 *
 *******************************************************************************/
int phNxpNciHal_SendHalCmd(phNxpNci_Extn_Cmd_t *in, phNxpNci_Extn_Resp_t *out);
