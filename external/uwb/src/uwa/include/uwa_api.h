/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
 *  Copyright 2018-2020 NXP
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
 *  This is the public interface file for UWA, NXP's UWB application
 *  layer for mobile phones/IOT devices.
 *
 ******************************************************************************/
#ifndef UWA_API_H
#define UWA_API_H

#include "uci_defs.h"
#include "uwb_api.h"
#include "uwb_gki.h"
#include "uwb_hal_api.h"
#include "uwb_target.h"

/*****************************************************************************
**  Constants and data types
*****************************************************************************/

/* UWA API return status codes */
/* Command succeeded    */
#define UWA_STATUS_OK UCI_STATUS_OK
/* Command is rejected. */
#define UWA_STATUS_REJECTED UCI_STATUS_REJECTED
/* failed               */
#define UWA_STATUS_FAILED UCI_STATUS_FAILED
/* Syntax error         */
#define UWA_STATUS_SYNTAX_ERROR UCI_STATUS_SYNTAX_ERROR
/* Invalid Parameter    */
#define UWA_STATUS_INVALID_PARAM UCI_STATUS_INVALID_PARAM
/* Invalid Range    */
#define UWA_STATUS_INVALID_RANGE UCI_STATUS_INVALID_RANGE
/*Invalid payload size in UCI packet */
#define UWA_STATUS_INVALID_MSG_SIZE UCI_STATUS_INVALID_MSG_SIZE
/* Unknown GID          */
#define UWA_STATUS_UNKNOWN_GID UCI_STATUS_UNKNOWN_GID
/* Unknown OID          */
#define UWA_STATUS_UNKNOWN_OID UCI_STATUS_UNKNOWN_OID
/* Read only            */
#define UWA_STATUS_READ_ONLY UCI_STATUS_READ_ONLY
/* retry the command */
#define UWA_STATUS_COMMAND_RETRY UCI_STATUS_COMMAND_RETRY

/* UWA session Specific status code */
/* session is not exist in UWBD */
#define UWA_STATUS_SESSSION_NOT_EXIST UCI_STATUS_SESSSION_NOT_EXIST
/* Session is already exist/duplicate */
#define UWA_STATUS_SESSSION_DUPLICATE UCI_STATUS_SESSSION_DUPLICATE
/* Session is in active state */
#define UWA_STATUS_SESSSION_ACTIVE UCI_STATUS_SESSSION_ACTIVE
/* Maximum sessions are reached */
#define UWA_STATUS_MAX_SESSSIONS_EXCEEDED UCI_STATUS_MAX_SESSSIONS_EXCEEDED
/*session not configured */
#define UWA_STATUS_SESSION_NOT_CONFIGURED UCI_STATUS_SESSION_NOT_CONFIGURED

/* UWA Ranging Session Specific Status Codes */
/* Ranging tx failed */
#define UWA_STATUS_RANGING_TX_FAILED UCI_STATUS_RANGING_TX_FAILED
/* Ranging rx timeout */
#define UWA_STATUS_RANGING_RX_TIMEOUT UCI_STATUS_RANGING_RX_TIMEOUT
/* Physical layer decoding failed */
#define UWA_STATUS_RANGING_RX_PHY_DEC_FAILED \
  UCI_STATUS_RANGING_RX_PHY_DEC_FAILED
/* Physical layer TOA failed */
#define UWA_STATUS_RANGING_RX_PHY_TOA_FAILED \
  UCI_STATUS_RANGING_RX_PHY_TOA_FAILED
/* Physical layer STS failed */
#define UWA_STATUS_RANGING_RX_PHY_STS_FAILED \
  UCI_STATUS_RANGING_RX_PHY_STS_FAILED
/* MAC decoding failed */
#define UWA_STATUS_RANGING_RX_MAC_DEC_FAILED \
  UCI_STATUS_RANGING_RX_MAC_DEC_FAILED
/* MAC information decoding failed */
#define UWA_STATUS_RANGING_RX_MAC_IE_DEC_FAILED \
  UCI_STATUS_RANGING_RX_MAC_IE_DEC_FAILED
/* MAC information missing */
#define UWA_STATUS_RANGING_RX_MAC_IE_MISSING \
  UCI_STATUS_RANGING_RX_MAC_IE_MISSING

/* UWA Data Session Specific Status Codes */
#define UWA_STATUS_DATA_MAX_TX_PSDU_SIZE_EXCEEDED \
  UCI_STATUS_DATA_MAX_TX_PSDU_SIZE_EXCEEDED
#define UWA_STATUS_DATA_RX_CRC_ERROR UCI_STATUS_DATA_RX_CRC_ERROR

typedef uint8_t tUWA_STATUS;

/* Handle for UWA registrations and connections */
typedef uint16_t tUWA_HANDLE;
#define UWA_HANDLE_INVALID (0xFFFF)

/* UWA_DM callback events */

#define UWA_DM_EVENT 0x00
#define UWA_TEST_DM_EVENT 0xA0

enum {
  UWA_DM_ENABLE_EVT = UWA_DM_EVENT,    /* Result of UWA_Enable             */
  UWA_DM_DISABLE_EVT,                  /* Result of UWA_Disable            */
  UWA_DM_REGISTER_EXT_CB_EVT,          /* Result of UWA Register Ext Callback */
  UWA_DM_UWBS_RESP_TIMEOUT_EVT,        /* Result of command response timeout */
  UWA_DM_UWBS_ERROR_EVT,               /* Result of UWB error ntf */
  UWA_DM_CORE_GET_DEVICE_INFO_RSP_EVT, /* Result of get device info */
  UWA_DM_CORE_SET_CONFIG_RSP_EVT,     /* Result of UWA_SetCoreConfig          */
  UWA_DM_CORE_GET_CONFIG_RSP_EVT,     /* Result of UWA_GetCoreConfig          */
  UWA_DM_DEVICE_RESET_RSP_EVT,        /* Result of Core Dev Reset */
  UWA_DM_DEVICE_STATUS_NTF_EVT,       /* Result of device status ntf */
  UWA_DM_CORE_GEN_ERR_STATUS_EVT,     /* Result of core generic error status */
  UWA_DM_SESSION_INIT_RSP_EVT,        /* Result of session Init cmd */
  UWA_DM_SESSION_DEINIT_RSP_EVT,      /* Result of session Deinit cmd */
  UWA_DM_SESSION_STATUS_NTF_EVT,      /* Result of session NTF  */
  UWA_DM_SESSION_SET_CONFIG_RSP_EVT,  /* Result of setAppConfig */
  UWA_DM_SESSION_GET_CONFIG_RSP_EVT,  /* Result of getAppConfig */
  UWA_DM_SESSION_GET_COUNT_RSP_EVT,   /* Result of get session count */
  UWA_DM_SESSION_GET_STATE_RSP_EVT,   /* Result of get session count */
  UWA_DM_RANGE_START_RSP_EVT,         /* Result of range start cmd */
  UWA_DM_RANGE_STOP_RSP_EVT,          /* Result of range start cmd */
  UWA_DM_RANGE_DATA_NTF_EVT,          /* Result of range data ntf */
  UWA_DM_GET_RANGE_COUNT_RSP_EVT,     /* Result of range get count */
  UWA_DM_GET_CORE_DEVICE_CAP_RSP_EVT, /* Results of UWA_GetCoreDeviceCapability
                                       */
  UWA_DM_SESSION_MC_LIST_UPDATE_RSP_EVT, /* Session Update Multicast List resp
                                            event*/
  UWA_DM_SESSION_MC_LIST_UPDATE_NTF_EVT, /* Session Update Multicast List ntf
                                            event*/
  UWA_DM_SEND_BLINK_DATA_RSP_EVT,        /* Blink Data Tx event*/
  UWA_DM_SEND_BLINK_DATA_NTF_EVT,        /* Blink Data Tx ntf event*/
  UWA_DM_CONFORMANCE_NTF_EVT,            /* Conformance Test Ntf Event */
  UWA_DM_SET_COUNTRY_CODE_RSP_EVT,       /* Country code update resp event */
  UWA_VENDOR_SPECIFIC_UCI_NTF_EVT,       /* Proprietary Ntf Event */
};

/* UWA_DM callback events for UWB RF events */

enum {
  UWA_DM_TEST_SET_CONFIG_RSP_EVT =
      UWA_TEST_DM_EVENT,            /* Results of set test config event*/
  UWA_DM_TEST_GET_CONFIG_RSP_EVT,   /* Results of get test config event*/
  UWA_DM_TEST_STOP_SESSION_RSP_EVT, /* Result of test session stop response */
  UWA_DM_TEST_PERIODIC_TX_RSP_EVT,  /* Results of periodic tx response event*/
  UWA_DM_TEST_PERIODIC_TX_NTF_EVT,  /* Results of periodic tx test NTF event*/
  UWA_DM_TEST_PER_RX_RSP_EVT,       /* Results of per rx response event*/
  UWA_DM_TEST_PER_RX_NTF_EVT,       /* Results of per rx test NTF  event*/
  UWA_DM_TEST_LOOPBACK_RSP_EVT,     /* Rf Loop Back Test resp event*/
  UWA_DM_TEST_LOOPBACK_NTF_EVT,     /* Rf Loop Back Test NTF event*/
  UWA_DM_TEST_RX_RSP_EVT,           /* Rf RX Test resp event*/
  UWA_DM_TEST_RX_NTF_EVT            /* Rf RX Test ntf event*/
};

/* UCI Parameter IDs */
typedef uint8_t tUWA_PMID;

/* Data for UWA_DM_DEVICE_STATUS_NTF_EVT */
typedef struct {
  uint8_t status; /* device status             */
} tUWA_DEVICE_STATUS_NTF;

/* Data for UWA_DM_CORE_GEN_ERR_STATUS_EVT */
typedef struct {
  uint8_t status; /* generic error             */
} tUWA_CORE_GEN_ERR_STATUS_NTF;

/* the data type associated with UWB_GET_DEVICE_INFO_REVT */
typedef struct {
  uint8_t status;
  uint16_t uci_version;
  uint16_t mac_version;
  uint16_t phy_version;
  uint16_t uciTest_version;
  uint8_t vendor_info_len;
  uint8_t vendor_info[UCI_VENDOR_INFO_MAX_SIZE];
} tUWA_GET_DEVICE_INFO_REVT;

/* Data for UWA_DM_CORE_SET_CONFIG_RSP_EVT */
typedef struct {
  tUWA_STATUS status;                   /* UWA_STATUS_OK if successful  */
  uint8_t num_param_id;                 /* Number of rejected Param ID  */
  tUWA_PMID param_ids[UWB_MAX_NUM_IDS]; /* Rejected Param ID            */
  uint16_t tlv_size;                    /* The length of TLV */
} tUWA_SET_CORE_CONFIG;

/* Data for UWA_DM_CORE_GET_CONFIG_RSP_EVT */
typedef struct {
  tUWA_STATUS status; /* UWA_STATUS_OK if successful              */
  uint8_t no_of_ids;
  uint8_t param_tlvs[UCI_MAX_PAYLOAD_SIZE]; /* TLV (Parameter ID-Len-Value byte
                                               stream) */
  uint16_t tlv_size; /* The length of TLV                        */
} tUWA_GET_CORE_CONFIG;

/* Data for UWA_DM_DEVICE_RESET_RSP_EVT */
typedef struct {
  tUWA_STATUS status; /* UWA_STATUS_OK if successful  */
} tUWA_DEVICE_RESET;

/* Data for UWA_DM_SESSION_GET_COUNT_RSP_EVT */
typedef struct {
  tUWA_STATUS status; /* device status             */
  uint8_t count;      /* active session count      */
} tUWA_SESSION_GET_COUNT;

/* Data for UWA_DM_SESSION_GET_STATE_RSP_EVT */
typedef struct {
  tUWA_STATUS status;    /* device status             */
  uint8_t session_state; /* current session state      */
} tUWA_SESSION_GET_STATE;

/* Data for UWA_DM_SESSION_GET_CONFIG_RSP_EVT */
typedef struct {
  tUWA_STATUS status; /* UWA_STATUS_OK if successful              */
  uint8_t tlv_size;   /* The length of TLV                        */
  uint8_t param_tlvs[UCI_MAX_PAYLOAD_SIZE]; /* TLV (Parameter ID-Len-Value byte
                                               stream) */
} tUWA_GET_SESSION_APP_CONFIG;

/* Data for UWA_DM_SESSION_SET_CONFIG_RSP_EVT */
typedef struct {
  tUWA_STATUS status;                   /* UWA_STATUS_OK if successful  */
  uint8_t num_param_id;                 /* Number of rejected Param ID  */
  tUWA_PMID param_ids[UWB_MAX_NUM_IDS]; /* Rejected Param ID            */
} tUWA_SET_SESSION_APP_CONFIG;

/* the data type associated with UWB_SESSION_STATUS_NTF_REVT */
typedef struct {
  uint32_t session_id;
  uint8_t state;
  uint8_t reason_code;
} tUWA_SESSION_STATUS_NTF_REVT;

/* Data for UWA_DM_RANGE_DATA_NTF_EVT */
/* the data type associated with UWB_RANGE_DATA_REVT */
typedef struct {
  uint8_t mac_addr[8];
  uint8_t status;
  uint8_t nLos; /* non line of sight */
  uint16_t distance;
  uint16_t aoa_azimuth;
  uint8_t aoa_azimuth_FOM;
  uint16_t aoa_elevation;
  uint8_t aoa_elevation_FOM;
  uint16_t aoa_dest_azimuth;
  uint8_t aoa_dest_azimuth_FOM;
  uint16_t aoa_dest_elevation;
  uint8_t aoa_dest_elevation_FOM;
  uint8_t slot_index;
  uint8_t rfu[12];
} tUWA_TWR_RANGING_MEASR;

typedef struct {
  uint8_t mac_addr[8];
  uint8_t frame_type;
  uint8_t nLos; /* non line of sight */
  uint16_t aoa_azimuth;
  uint8_t aoa_azimuth_FOM;
  uint16_t aoa_elevation;
  uint8_t aoa_elevation_FOM;
  uint64_t timeStamp;
  uint32_t blink_frame_number; /* blink frame number received from tag/master
                                  anchor */
  uint8_t rfu[12];
  uint8_t device_info_size;    /* Size of Device Specific Information */
  uint8_t* device_info;        /* Device Specific Information */
  uint8_t blink_payload_size;  /* Size of Blink Payload Data */
  uint8_t* blink_payload_data; /* Blink Payload Data */
} tUWA_TDoA_RANGING_MEASR;

typedef union {
  tUWA_TWR_RANGING_MEASR twr_range_measr[MAX_NUM_RESPONDERS];
  tUWA_TDoA_RANGING_MEASR tdoa_range_measr[MAX_NUM_OF_TDOA_MEASURES];
} tUWA_RANGING_MEASR;

typedef struct {
  uint16_t ranging_data_len;
  uint32_t seq_counter;
  uint32_t session_id;
  uint8_t rcr_indication;
  uint32_t curr_range_interval;
  uint8_t ranging_measure_type;
  uint8_t rfu;
  uint8_t mac_addr_mode_indicator;
  uint8_t reserved[8];
  uint8_t no_of_measurements;
  tUWA_RANGING_MEASR ranging_measures;
} tUWA_RANGE_DATA_NTF;

/* the data type associated with UWB_GET_RANGE_COUNT_REVT */
typedef struct {
  uint8_t status;
  uint32_t count;
} tUWA_RANGE_GET_RNG_COUNT_REVT;

/* Data for UWA_DM_GET_CORE_DEVICE_CAP_RSP_EVT */
typedef struct {
  tUWA_STATUS status; /* UWA_STATUS_OK if successful              */
  uint8_t no_of_tlvs;
  uint8_t tlv_buffer[UCI_MAX_PAYLOAD_SIZE]; /* TLV (Parameter ID-Len-Value byte
                                               stream) */
  uint16_t tlv_buffer_len; /* The length of TLV                        */
} tUWA_GET_CORE_DEVICE_CAPABILITY;

/* the data type associated with UWB_SESSION_UPDATE_MULTICAST_LIST_NTF */
typedef struct {
  uint32_t session_id;
  uint8_t remaining_list;
  uint8_t no_of_controlees;
  uint16_t controlee_mac_address_list[MAX_NUM_CONTROLLEES];
  uint32_t subsession_id_list[MAX_NUM_CONTROLLEES];
  uint8_t status_list[MAX_NUM_CONTROLLEES];
} tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF;

/* the data type associated with UWB_BLINK_DATA_TX_NTF */
typedef struct {
  uint8_t repetition_count_status; /* Status to indicate Repetition Count is
                                      exhausted */
} tUWA_SEND_BLINK_DATA_NTF;

/* the data type associated with UWB_CONFORMANCE_TEST_DATA */
typedef struct {
  uint16_t length;
  uint8_t data[CONFORMANCE_TEST_MAX_UCI_PKT_LENGTH];
} tUWA_CONFORMANCE_TEST_DATA;

/* the data type associated with vendor notification */
typedef struct {
  uint16_t len;
  uint8_t data[UCI_VENDOR_INFO_MAX_SIZE];
}tUWA_VENDOR_SPECIFIC_NTF;

/* Union of all DM callback structures */
typedef union {
  tUWA_STATUS status;                /* UWA_DM_ENABLE_EVT        */
  tUWA_DEVICE_STATUS_NTF dev_status; /*UWA_DM_DEVICE_STATUS_NTF_EVT */
  tUWA_CORE_GEN_ERR_STATUS_NTF
      sCore_gen_err_status;        /*UWA_DM_CORE_GEN_ERR_STATUS_EVT */
  tUWA_DEVICE_RESET sDevice_reset; /* UWA_DM_DEVICE_RESET_RSP_EVT    */
  tUWA_GET_DEVICE_INFO_REVT
      sGet_device_info; /* UWA_DM_CORE_GET_DEVICE_INFO_RSP_EVT */
  tUWA_SET_CORE_CONFIG sCore_set_config; /* UWA_DM_CORE_SET_CONFIG_RSP_EVT    */
  tUWA_GET_CORE_CONFIG sCore_get_config; /* UWA_DM_CORE_GET_CONFIG_RSP_EVT    */
  tUWA_GET_CORE_CONFIG
      sApp_get_config; /* UWA_DM_SESSION_GET_CONFIG_RSP_EVT    */
  tUWA_SET_CORE_CONFIG
      sApp_set_config; /* UWA_DM_SESSION_SET_CONFIG_RSP_EVT    */
  tUWA_SESSION_STATUS_NTF_REVT
      sSessionStatus; /* UWA_DM_SESSION_STATUS_NTF_EVT */
  tUWA_SESSION_GET_COUNT
      sGet_session_cnt; /* UWA_DM_SESSION_GET_COUNT_RSP_EVT    */
  tUWA_SESSION_GET_STATE
      sGet_session_state;          /* UWA_DM_SESSION_GET_STATE_RSP_EVT    */
  tUWA_RANGE_DATA_NTF sRange_data; /*UWA_DM_RANGE_DATA_NTF_EVT */
  tUWA_RANGE_GET_RNG_COUNT_REVT
      sGet_range_cnt; /*UWA_DM_GET_RANGE_COUNT_RSP_EVT */
  tUWA_GET_CORE_DEVICE_CAPABILITY
      sGet_device_capability; /*UWA_DM_GET_CORE_DEVICE_CAP_RSP_EVT*/
  tUWA_SESSION_UPDATE_MULTICAST_LIST_NTF
      sMulticast_list_ntf; /*UWA_DM_SESSION_MC_LIST_UPDATE_NTF_EVT*/
  tUWA_SEND_BLINK_DATA_NTF sBlink_data_ntf; /*UWA_DM_SEND_BLINK_DATA_NTF_EVT*/
  tUWA_CONFORMANCE_TEST_DATA sConformance_ntf; /* UWA_DM_CONFORMANCE_NTF_EVT */
  tUWA_VENDOR_SPECIFIC_NTF sVendor_specific_ntf; /*Vendor Specific ntf data */
  void* p_vs_evt_data;                         /* Vendor-specific evt data */
} tUWA_DM_CBACK_DATA;

/* Data for UWA_DM_RF_TEST_DATA_EVT */
typedef struct {
  uint16_t length;
  uint8_t data[UCI_MAX_PAYLOAD_SIZE];
} tUWA_RF_TEST_DATA;

/* Union of all DM callback structures */
typedef union {
  tUWA_STATUS status; /*  Response status for RF Test commands        */
  tUWA_GET_CORE_CONFIG sTest_get_config; /* UWA_DM_TEST_GET_CONFIG_RSP_EVT    */
  tUWA_SET_CORE_CONFIG sTest_set_config; /* UWA_DM_TEST_SET_CONFIG_RSP_EVT    */
  tUWA_RF_TEST_DATA rf_test_data;        /*UWA_DM_RF_TEST_DATA_EVT*/
} tUWA_DM_TEST_CBACK_DATA;

/* UWA_DM callback */
typedef void(tUWA_DM_CBACK)(uint8_t event, tUWA_DM_CBACK_DATA* p_data);
typedef void(tUWA_DM_TEST_CBACK)(uint8_t event,
                                 tUWA_DM_TEST_CBACK_DATA* p_data);

/* UWA_RAW_CMD callback */
typedef void(tUWA_RAW_CMD_CBACK)(uint8_t event, uint16_t param_len,
                                 uint8_t* p_param);

/*****************************************************************************
**  External Function Declarations
*****************************************************************************/

/*******************************************************************************
**
** Function         UWA_Init
**
** Description      This function initializes control blocks for UWA
**
**                  p_hal_entry_tbl points to a table of HAL entry points
**
**                  NOTE: the buffer that p_hal_entry_tbl points must be
**                  persistent until UWA is disabled.
**
**
** Returns          none
**
*******************************************************************************/
extern void UWA_Init(tHAL_UWB_ENTRY* p_hal_entry_tbl);

/*******************************************************************************
**
** Function         UWA_Enable
**
** Description      This function enables UWB. Prior to calling UWA_Enable,
**                  the UWBC must be powered up, and ready to receive commands.
**                  This function enables the tasks needed by UWB, opens the UCI
**                  transport, resets the UWB Subsystem, downloads patches to
**                  the UWBC (if necessary), and initializes the UWB subsystems.
**l
**
** Returns          UWA_STATUS_OK if successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_Enable(tUWA_DM_CBACK* p_dm_cback,
                              tUWA_DM_TEST_CBACK* p_dm_test_cback);

/*******************************************************************************
**
** Function         UWA_Disable
**
** Description      This function is called to shutdown UWB. The tasks for UWB
**                  are terminated, and clean up routines are performed. This
**                  function is typically called during platform shut-down, or
**                  when UWB is disabled from a settings UI. When the UWB
**                  shutdown procedure is completed, an UWA_DM_DISABLE_EVT is
**                  returned to the application using the tUWA_DM_CBACK.
**
** Returns          UWA_STATUS_OK if successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_Disable(bool graceful);

/*******************************************************************************
**
** Function:        UWA_GetDeviceInfo
**
** Description:     This function gets the UWB Subsystem Information
**
** Returns:         UCI version and manufacturer specific information
**
*******************************************************************************/
extern tUWA_STATUS UWA_GetDeviceInfo();

/*******************************************************************************
**
** Function         UWA_SetCoreConfig
**
** Description      Set the configuration parameters to UWBS. The result is
**                  reported with an UWA_DM_CORE_SET_CONFIG_RSP_EVT in the
**                  tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_SetCoreConfig(tUWA_PMID param_id, uint8_t length,
                                     uint8_t* p_data);

/*******************************************************************************
**
** Function         UWA_GetCoreConfig
**
** Description      Get the configuration parameters from UWBS. The result is
**                  reported with an UWA_DM_CORE_GET_CONFIG_RSP_EVT in the
**                  tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_GetCoreConfig(uint8_t num_ids, tUWA_PMID* p_param_ids);

/*******************************************************************************
**
** Function         UWA_SendDeviceReset
**
** Description      Send Device Reset Command to UWBS. The result is
**                  reported with an UWA_DM_DEVICE_RESET_RSP_EVT in the
**                  tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_SendDeviceReset(uint8_t resetConfig);

/*******************************************************************************
**
** Function         UWA_SendSessionInit
**
** Description      This function is called to send session init command.
**                  The result is reported with an UWA_DM_SESSION_INIT_RSP_EVT
**                  in the tUWA_DM_CBACK callback
**
**                  session id - value of particular session ID
**                  session type - type of session to start ex: ranging,app etc
**
** Returns          UWA_STATUS_OK if successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_SendSessionInit(uint32_t session_id,
                                       uint8_t sessionType);

/*******************************************************************************
**
** Function         UWA_SendSessionDeInit
**
** Description      This function is called to send session deinit command.
**                  The result is reported with an UWA_DM_SESSION_DEINIT_RSP_EVT
**                  in the tUWA_DM_CBACK callback
**
**                  session id - value of particular session ID
**
** Returns          UWA_STATUS_OK if successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_SendSessionDeInit(uint32_t session_id);

/*******************************************************************************
**
** Function         UWA_GetSessionCount
**
** Description      This function is called to send get session count command.
**                  The result is reported with an
**                  UWA_DM_SESSION_GET_COUNT_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is successfully sent
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_GetSessionCount();

/*******************************************************************************
**
** Function         UWA_SetAppConfig
**
** Description      Set the configuration parameters to UWBS. The result is
**                  reported with an UWA_DM_SESSION_SET_CONFIG_RSP_EVT in the
**                  tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_SetAppConfig(uint32_t session_id, uint8_t noOfParams,
                                    uint8_t appConfigParamLen,
                                    uint8_t AppConfigParmas[]);

/*******************************************************************************
**
** Function         UWA_GetAppConfig
**
** Description      Get the configuration parameters from UWBS. The result is
**                  reported with an UWA_DM_SESSION_GET_CONFIG_RSP_EVT in the
**                  tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_GetAppConfig(uint32_t session_id, uint8_t num_ids,
                                    uint8_t paramLen, tUWA_PMID* p_param_ids);

/*******************************************************************************
**
** Function         UWA_StartRangingSession
**
** Description      start the ranging session.
**                  The result is reported with an UWA_DM_RANGE_START_RSP_EVT in
**                  the tUWA_DM_CBACK callback
**
** Returns          UWA_STATUS_OK if ranging started successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_StartRangingSession(uint32_t session_id);

/*******************************************************************************
**
** Function         UWA_StopRangingSession
**
** Description      stop the ranging session.
**                  The result is reported with an UWA_DM_RANGE_STOP_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if ranging is stopped successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_StopRangingSession(uint32_t session_id);

/*******************************************************************************
**
** Function         UWA_GetRangingCount
**
** Description      Get ranging count.
**                  The result is reported with an
**                  UWA_DM_GET_RANGE_COUNT_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_GetRangingCount(uint32_t session_id);

/*******************************************************************************
**
** Function         UWA_GetSessionStatus
**
** Description      Get session status.
**                  The result is reported with an
**                  UWA_DM_SESSION_GET_STATE_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_GetSessionStatus(uint32_t session_id);

/*******************************************************************************
**
** Function         UWA_GetCoreGetDeviceCapability
**
** Description      Get core device capability info command.
**                  The result is reported with an
**                  UWA_DM_GET_CORE_DEVICE_CAP_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
**
*******************************************************************************/
extern tUWA_STATUS UWA_GetCoreGetDeviceCapability(void);

/*******************************************************************************
**
** Function         UWA_ControllerMulticastListUpdate
**
** Description      This function is called to send Controller Multicast List
**                  Update.
**                  The result is reported with an
**                  UWA_DM_SESSION_MC_LIST_UPDATE_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_ControllerMulticastListUpdate(
    uint32_t session_id, uint8_t action, uint8_t noOfControlees,
    uint16_t* shortAddressList, uint32_t* subSessionIdList);

/*******************************************************************************
**
** Function         UWA_ControllerSetCountryCode
**
** Description      This function is called to set country code.
**                  The result is reported with an
**                  UWA_DM_SET_COUNTRY_CODE_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_ControllerSetCountryCode(uint8_t* countryCodeArray);

/*******************************************************************************
**
** Function         UWA_SendBlinkData
**
** Description      This function is called to send Blink Data Tx.
**                  The result is reported with an
**                  UWA_DM_SEND_BLINK_DATA_RSP_EVT
**                  in the tUWA_DM_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_SendBlinkData(uint32_t session_id,
                                     uint8_t repetition_count,
                                     uint8_t app_data_len, uint8_t* app_data);

/*                      UWA APIs for RF Test functionality */

/*******************************************************************************
**
** Function         UWA_TestSetConfig
**
** Description      Set the configuration parameters to UWBS.
**                     The result is  reported with an
**                     UWA_DM_TEST_SET_CONFIG_RSP_EVT
**                     in the tUWA_DM_TEST_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_TestSetConfig(uint32_t session_id, uint8_t noOfParams,
                                     uint8_t testConfigParamLen,
                                     uint8_t testConfigParmas[]);

/*******************************************************************************
**
** Function         UWA_TestGetConfig
**
** Description      Get the configuration parameters from UWBS.
**                  The result is reported with an
**                  UWA_DM_TEST_GET_CONFIG_RSP_EVT
**                  in the tUWA_DM_TEST_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_TestGetConfig(uint32_t session_id, uint8_t num_ids,
                                     uint8_t paramLen, tUWA_PMID* p_param_ids);

/*******************************************************************************
**
** Function         UWA_PeriodicTxTest
**
** Description      This function is called to trigger the periodic Tx Test.
**                  The result is reported with an
**                  UWA_DM_TEST_PERIODIC_TX_RSP_EVT
**                  in the tUWA_DM_TEST_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_PeriodicTxTest(uint16_t psduLen, uint8_t psduData[]);

/*******************************************************************************
**
** Function         UWA_PerRxTest
**
** Description      This function is called to trigger the PER Rx Tx Test.
**                  The result is reported with an UWA_DM_TEST_PER_RX_RSP_EVT
**                  in the tUWA_DM_TEST_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_PerRxTest(uint16_t psduLen, uint8_t psduData[]);

/*******************************************************************************
**
** Function         UWA_UwbLoopBackTest
**
** Description      This function is called to trigger the loop back Test.
**                  The result is reported with an UWA_DM_TEST_LOOPBACK_RSP_EVT
*in the
**                  tUWA_DM_TEST_CBACK callback.
**
** Returns          UWA_STATUS_OK if command is sent successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_UwbLoopBackTest(uint16_t psduLen, uint8_t psduData[]);

/*******************************************************************************
**
** Function         UWA_RxTest
**
** Description      This function is called to trigger the loop back Test.
**                  The result is reported with an UWA_DM_TEST_RX_EVT in the
**                  tUWA_DM_TEST_CBACK callback.
**
** Returns          UWA_STATUS_OK if Per Session stopped successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_RxTest();

/*******************************************************************************
**
** Function         UWA_TestStopSession
**
** Description      This function is called to stop the ongoing test session
**                  The result is reported with an
**                  UWA_DM_TEST_STOP_SESSION_RSP_EVT in the
**                  tUWA_DM_TEST_CBACK callback.
**
** Returns          UWA_STATUS_OK if Per Session stopped successfully
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_TestStopSession(void);

/*******************************************************************************
**
** Function         UWA_SendRawCommand
**
** Description      This function is called to send raw vendor specific
**                  command to Helios.
**
**                  cmd_params_len  - The command parameter len
**                  p_cmd_params    - The command parameter
**                  p_cback         - The callback function to receive the
**                                    command
**
** Returns          UWA_STATUS_OK if successfully initiated
**                  UWA_STATUS_FAILED otherwise
**
*******************************************************************************/
extern tUWA_STATUS UWA_SendRawCommand(uint16_t cmd_params_len,
                                      uint8_t* p_cmd_params,
                                      tUWA_RAW_CMD_CBACK* p_cback);

#endif /* UWA_API_H */
