/*******************************************************************************
 *
 *  Copyright 2019 NXP
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

#pragma once

#include "EseUpdateChecker.h"
#include "ISeChannelCallback.h"
#include "ISeEvtCallback.h"

#define jcDnld (JcDnld::getInstance())

typedef std::shared_ptr<ISeChannelCallback> spSeChannel;
typedef std::shared_ptr<ISeEvtCallback> spSeEvt;

typedef struct JcopOs_TranscieveInfo {
  int32_t timeout;
  uint8_t sRecvData[1024];
  uint8_t *sSendData;
  uint32_t sSendlength;
  int sRecvlength;
} JcopOs_TranscieveInfo_t;

typedef struct JcopOs_Version_Info {
  uint8_t osid;
  uint8_t ver1;
  uint8_t ver0;
  uint8_t OtherValid;
  uint8_t ver_status;
} JcopOs_Version_Info_t;

typedef struct JcopOs_ImageInfo {
  FILE *fp;
  int fls_size;
  char fls_path[256];
  int index;
  uint8_t cur_state;
  JcopOs_Version_Info_t version_info;
} JcopOs_ImageInfo_t;

typedef struct JcopOs_Dwnld_Context {
  JcopOs_Version_Info_t version_info;
  JcopOs_ImageInfo_t Image_info;
  JcopOs_TranscieveInfo_t pJcopOs_TransInfo;
} * pJcopOs_Dwnld_Context_t;

static const uint8_t TRIGGER_APDU[] = {0x4F, 0x70, 0x80, 0x13, 0x04,
                                       0xDE, 0xAD, 0xBE, 0xEF, 0x00};
static const uint8_t GETINFO_APDU[] = {
    0x00,                    // CLA
    0xA4, 0x04, 0x00, 0x0C,  // INS, P1, P2, Lc
    0xD2, 0x76, 0x00, 0x00, 0x85, 0x41,
    0x00, 0x00, 0x00, 0x00, 0x20, 0x00,  // Data
    0x00                                 // Le
};

typedef enum {
  JCOP_UPDATE_STATE0 = 0,
  JCOP_UPDATE_STATE1,
  JCOP_UPDATE_STATE2,
  JCOP_UPDATE_STATE3,
  JCOP_UPDATE_STATE_TRIGGER_APDU
} JCOP_UPDATE_STATE;

typedef enum {
  STATUS_SUCCESS = 0x00,
  STATUS_UPTO_DATE = 0x01,
  STATUS_FAILED = 0x03,
  STATUS_INUSE = 0x04,
  STATUS_FILE_NOT_FOUND = 0x05
} STATUS_JCOP_OSU;

#define EE_ERROR_OPEN_FAIL -1
#define JCOP_MAX_BUF_SIZE 10240

class JcDnld {
 public:
   /*****************************************************************************
   **
   ** Function:        getInstance
   **
   ** Description:     Get the JcDnld singleton object.
   **
   ** Returns:         JcDnld object.
   **
   *******************************************************************************/
   static JcDnld &getInstance();

   /*****************************************************************************
    **
    ** Function:        registerSeCallback
    **
    ** Description:     registers seChannel callback and seEvent callback
    **
    ** Returns:         None
    **
    *******************************************************************************/
   void registerSeCallback(const spSeChannel &pSeChannelCallback,
                           const spSeEvt &pSeEvtCallback);

   /*****************************************************************************
   **
   ** Function:        doUpdate
   **
   ** Description:     handles JCOP OS download
   **
   ** Returns:         returns STATUS_JCOP_OSU
   **
   *******************************************************************************/
   STATUS_JCOP_OSU doUpdate();

   /*****************************************************************************
   **
   ** Function:        SwitchToJcopOsuState
   **
   ** Description:     Switch JCOP to OS update state.
   **
   ** Returns:         returns STATUS_JCOP_OSU.
   **
   *******************************************************************************/
   STATUS_JCOP_OSU SwitchToJcopOsuState(JcopOs_ImageInfo_t *pVersionInfo,
                                        STATUS_JCOP_OSU status,
                                        JcopOs_TranscieveInfo_t *pTranscv_Info);

   /*****************************************************************************
   **
   ** Function:        GetInfo
   **
   ** Description:     Get the JCOP OS info.
   **
   ** Returns:         returns STATUS_JCOP_OSU.
   **
   *******************************************************************************/
   STATUS_JCOP_OSU GetInfo(JcopOs_ImageInfo_t *pVersionInfo,
                           STATUS_JCOP_OSU status,
                           JcopOs_TranscieveInfo_t *pTranscv_Info);

   /*****************************************************************************
   **
   ** Function:        LoadJcopOSImage
   **
   ** Description:     Used to update the JCOP OS.
   **
   ** Returns:         returns STATUS_JCOP_OSU.
   **
   *******************************************************************************/
   STATUS_JCOP_OSU LoadJcopOSImage(JcopOs_ImageInfo_t *Os_info,
                                   STATUS_JCOP_OSU status,
                                   JcopOs_TranscieveInfo_t *pTranscv_Info);

 private:
  bool mIsInit;
  static JcDnld sOsuInstance;
  static spSeChannel mspSeChannelCallback;
  static spSeEvt mspSeEvtCallback;
  static bool msIsOsuOngoing;
  static int16_t msOsuHandle;

  /*****************************************************************************
  **
  ** Function:        JcDnld
  **
  ** Description:     Private Constructor.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  JcDnld();

  /*******************************************************************************
  **
  ** Function:        Init
  **
  ** Description:     Initializes the JCOP library and opens the DWP
  **                  communication channel
  **
  ** Returns:         returns STATUS_JCOP_OSU.
  **
  *******************************************************************************/
  STATUS_JCOP_OSU Init();

  /*******************************************************************************
  **
  ** Function:        StartDownload
  **
  ** Description:     Starts the JCOP update
  **
  ** Returns:         returns STATUS_JCOP_OSU.
  **
  *******************************************************************************/
  STATUS_JCOP_OSU StartDownload();

  /*******************************************************************************
  **
  ** Function:        DeInit
  **
  ** Description:     Deinitializes the JCOP Lib
  **
  ** Returns:         true if ok.
  **
  *******************************************************************************/
  bool DeInit();

  /*******************************************************************************
  **
  ** Function:        getJcopOsFileInfo
  **
  ** Description:     Verify all the updater files required for download
  **                  are present or not
  **
  ** Returns:         True if ok.
  **
  *******************************************************************************/
  bool getJcopOsFileInfo();

  /*******************************************************************************
  **
  ** Function:        initializeOsuContext
  **
  ** Description:     Initialize all member variables.
  **                  native: Native data.
  **
  ** Returns:         True if ok.
  **
  *******************************************************************************/
  bool initializeOsuContext();

  /*******************************************************************************
  **
  ** Function:        finalize
  **
  ** Description:     Release all resources.
  **
  ** Returns:         None
  **
  *******************************************************************************/
  void finalize();

  /*******************************************************************************
  **
  ** Function:        osuUpdateSeqHandler
  **
  ** Description:     Performs the JcopOS download sequence
  **
  ** Returns:         returns STATUS_JCOP_OSU.
  **
  *******************************************************************************/
  STATUS_JCOP_OSU osuUpdateSeqHandler();

  /*******************************************************************************
  **
  ** Function:        GetJcopOsState
  **
  ** Description:     Used to update the JCOP OS state
  **
  ** Returns:         returns STATUS_JCOP_OSU.
  **
  *******************************************************************************/
  STATUS_JCOP_OSU GetJcopOsState(JcopOs_ImageInfo_t *Os_info, uint8_t *counter);

  /*******************************************************************************
  **
  ** Function:        SetJcopOsState
  **
  ** Description:     Used to set the JCOP OS state.
  **
  ** Returns:         returns STATUS_JCOP_OSU.
  **
  *******************************************************************************/
  STATUS_JCOP_OSU SetJcopOsState(JcopOs_ImageInfo_t *Os_info, uint8_t state);

  /*******************************************************************************
  **
  ** Function:        osuHandlerThread
  **
  ** Description:     Perform JCOP Download.
  **
  ** Returns:         None
  **
  *******************************************************************************/
  static void *osuHandlerThread(void *data);
};
