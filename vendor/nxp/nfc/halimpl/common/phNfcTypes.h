/*
 * Copyright 2015-2020 NXP
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

#ifndef PHNFCTYPES_H
#define PHNFCTYPES_H

#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <stdbool.h>
#include "Nxp_Features.h"

#ifndef true
#define true (0x01) /* Logical True Value */
#endif
#ifndef TRUE
#define TRUE (0x01) /* Logical True Value */
#endif
#ifndef false
#define false (0x00) /* Logical False Value */
#endif
#ifndef FALSE
#define FALSE (0x00) /* Logical False Value */
#endif
typedef uint8_t utf8_t;     /* UTF8 Character String */
typedef uint8_t bool_t;     /* boolean data type */
typedef uint16_t NFCSTATUS; /* Return values */

#define STATIC static

#define PHNFC_MAX_UID_LENGTH 0x0AU /* Maximum UID length expected */
/* Maximum ATR_RES (General Bytes) length expected */
#define PHNFC_MAX_ATR_LENGTH 0x30U
#define PHNFC_NFCID_LENGTH 0x0AU /* Maximum length of NFCID 1.3*/
#define PHNFC_ATQA_LENGTH 0x02U  /* ATQA length */

/*
 * Possible Hardware Configuration exposed to upper layer.
 * Typically this should be port name (Ex:"COM1","COM2") to which PN54X is
 * connected.
 */
typedef enum {
  ENUM_LINK_TYPE_COM1,
  ENUM_LINK_TYPE_COM2,
  ENUM_LINK_TYPE_COM3,
  ENUM_LINK_TYPE_COM4,
  ENUM_LINK_TYPE_COM5,
  ENUM_LINK_TYPE_COM6,
  ENUM_LINK_TYPE_COM7,
  ENUM_LINK_TYPE_COM8,
  ENUM_LINK_TYPE_I2C,
  ENUM_LINK_TYPE_SPI,
  ENUM_LINK_TYPE_USB,
  ENUM_LINK_TYPE_TCP,
  ENUM_LINK_TYPE_NB
} phLibNfc_eConfigLinkType;

/*
 * Deferred message. This message type will be posted to the client application
 * thread
 * to notify that a deferred call must be invoked.
 */
#define PH_LIBNFC_DEFERREDCALL_MSG (0x311)

/*
 * Deferred call declaration.
 * This type of API is called from ClientApplication ( main thread) to notify
 * specific callback.
 */
typedef void (*pphLibNfc_DeferredCallback_t)(void*);

/*
 * Deferred parameter declaration.
 * This type of data is passed as parameter from ClientApplication (main thread)
 * to the
 * callback.
 */
typedef void* pphLibNfc_DeferredParameter_t;

/*
 * Possible Hardware Configuration exposed to upper layer.
 * Typically this should be at least the communication link (Ex:"COM1","COM2")
 * the controller is connected to.
 */
typedef struct phLibNfc_sConfig {
  uint8_t* pLogFile; /* Log File Name*/
  /* Hardware communication link to the controller */
  phLibNfc_eConfigLinkType nLinkType;
  /* The client ID (thread ID or message queue ID) */
  uintptr_t nClientId;
} phLibNfc_sConfig_t, *pphLibNfc_sConfig_t;

/*
 * NFC Message structure contains message specific details like
 * message type, message specific data block details, etc.
 */
typedef struct phLibNfc_Message {
  uint32_t eMsgType; /* Type of the message to be posted*/
  void* pMsgData;    /* Pointer to message specific data block in case any*/
  uint32_t Size;     /* Size of the datablock*/
} phLibNfc_Message_t, *pphLibNfc_Message_t;

/*
 * Deferred message specific info declaration.
 * This type of information is packed as message data when
 * PH_LIBNFC_DEFERREDCALL_MSG
 * type message is posted to message handler thread.
 */
typedef struct phLibNfc_DeferredCall {
  pphLibNfc_DeferredCallback_t pCallback;   /* pointer to Deferred callback */
  pphLibNfc_DeferredParameter_t pParameter; /* pointer to Deferred parameter */
} phLibNfc_DeferredCall_t;

#if (NXP_EXTNS == TRUE)
typedef enum p61_access_state {
  P61_STATE_INVALID = 0x0000,
  P61_STATE_IDLE = 0x0100,         /* p61 is free to use */
  P61_STATE_WIRED = 0x0200,        /* p61 is being accessed by DWP (NFCC)*/
  P61_STATE_SPI = 0x0400,          /* P61 is being accessed by SPI */
  P61_STATE_DWNLD = 0x0800,        /* NFCC fw download is in progress */
  P61_STATE_SPI_PRIO = 0x1000,     /*Start of p61 access by SPI on priority*/
  P61_STATE_SPI_PRIO_END = 0x2000, /*End of p61 access by SPI on priority*/
  P61_STATE_SPI_END = 0x4000,
  P61_STATE_JCP_DWNLD = 0x8000,           /* JCOP downlad in progress */
  P61_STATE_SECURE_MODE = 0x100000,       /* secure mode state*/
  P61_STATE_SPI_SVDD_SYNC_START = 0x0001, /*ESE_VDD Low req by SPI*/
  P61_STATE_SPI_SVDD_SYNC_END = 0x0002,   /*ESE_VDD is Low by SPI*/
  P61_STATE_DWP_SVDD_SYNC_START = 0x0004, /*ESE_VDD  Low req by Nfc*/
  P61_STATE_DWP_SVDD_SYNC_END = 0x0008    /*ESE_VDD is Low by Nfc*/
} p61_access_state_t;

typedef enum jcop_dwnld_state {
  JCP_DWNLD_IDLE = P61_STATE_JCP_DWNLD, /* jcop dwnld is not ongoing*/
  JCP_DWNLD_INIT = 0x8010,              /* jcop dwonload init state*/
  JCP_DWNLD_START = 0x8020,             /* download started */
  JCP_SPI_DWNLD_COMPLETE = 0x8040, /* jcop download complete in spi interface*/
  JCP_DWP_DWNLD_COMPLETE = 0x8080, /* jcop download complete */
} jcop_dwnld_state_t;
#endif
#define UNUSED(X) (void) (X);

#endif /* PHNFCTYPES_H */
