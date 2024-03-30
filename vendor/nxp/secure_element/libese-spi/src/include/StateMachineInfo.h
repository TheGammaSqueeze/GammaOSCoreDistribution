/******************************************************************************
 *  Copyright 2018-2019 NXP
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

#ifndef LIBESE_SPI_SRC_INCLUDE_STATEMACHINEINFO_H_
#define LIBESE_SPI_SRC_INCLUDE_STATEMACHINEINFO_H_

typedef enum {
  EVT_UNKNOWN = 0,
  EVT_RF_ON = 1,
  EVT_RF_ON_FELICA_APP = 2,
  EVT_RF_OFF = 3,
  EVT_RF_ACT_NTF_ESE = 4,
  EVT_SPI_HW_SERVICE_START = 5,
  EVT_SPI_SESSION_OPEN = 6,
  EVT_SPI_SESSION_CLOSE = 7,
  EVT_SPI_DEVICE_OPEN = 8,
  EVT_SPI_DEVICE_CLOSE = 9,
  EVT_SPI_TX = 10,
  EVT_SPI_TX_WTX_RSP = 11,
  EVT_SPI_RX = 12,
  EVT_SPI_RX_WTX_REQ = 13,
  EVT_SPI_TIMER_START = 14,
  EVT_SPI_TIMER_STOP = 15,
  EVT_SPI_TIMER_EXPIRED = 16,
  EVT_HCI_INIT_START = 17,
  EVT_HCI_INIT_COMPLETE = 18
} eExtEvent_t;

typedef enum {
  ST_UNKNOWN = 0,
  ST_SPI_CLOSED_RF_IDLE = 1,
  ST_SPI_CLOSED_RF_BUSY = 2,
  ST_SPI_OPEN_RF_IDLE = 3,
  ST_SPI_BUSY_RF_IDLE = 4,
  ST_SPI_RX_PENDING_RF_PENDING = 5,
  ST_SPI_RX_PENDING_RF_PENDING_FELICA = 6,
  ST_SPI_OPEN_SUSPENDED_RF_BUSY = 7,
  ST_SPI_OPEN_RESUMED_RF_BUSY = 8,
  ST_SPI_BUSY_RF_BUSY = 9,
  ST_SPI_BUSY_RF_BUSY_TIMER_EXPIRED = 10,
  ST_SPI_SESSION_OPEN_RF_IDLE = 11,
  ST_SPI_SESSION_OPEN_SUSPENDED_RF_BUSY = 12,
  ST_SPI_SESSION_OPEN_RESUMED_RF_BUSY = 13,
  ST_SPI_OPEN_FOR_ESEUPDATE_RF_IDLE = 14
} eStates_t;

typedef enum { SM_STATUS_SUCCESS, SM_STATUS_FAILED } eStatus_t;

#endif /* LIBESE_SPI_SRC_INCLUDE_STATEMACHINEINFO_H_ */
