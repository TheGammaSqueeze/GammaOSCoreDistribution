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

#define LOG_TAG "StateMachine"

#include <NfcAdaptation.h>
#include <hal_nxpese.h>
#include <log/log.h>
#include <stdint.h>
#include <string.h>

#include "EseHalStates.h"
#include "StateMachine.h"

#define HAL_NFC_SPI_DWP_SYNC 21

extern int omapi_status;
extern unsigned long gFelicaAppTimeout;
bool state_machine_debug = true;

map<eStates_t, StateBase *> StateBase::sListOfStates;
IntervalTimer StateBase::sTimerInstance;

StateBase::StateBase() { mLastProcessEventStatus = SM_STATUS_FAILED; }

class StateSpiBusyRfBusy : public StateBase {
public:
  StateSpiBusyRfBusy() {}
  ~StateSpiBusyRfBusy() {}

  eStates_t GetState() { return ST_SPI_BUSY_RF_BUSY; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      TimerStop();
      PtrNextState = sListOfStates.find(ST_SPI_BUSY_RF_IDLE)->second;
      break;
    case EVT_SPI_RX:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RESUMED_RF_BUSY)->second;
      break;
    case EVT_SPI_TIMER_EXPIRED:
      PtrNextState =
          sListOfStates.find(ST_SPI_BUSY_RF_BUSY_TIMER_EXPIRED)->second;
      break;
    case EVT_SPI_RX_WTX_REQ:
      break;
    case EVT_SPI_TX_WTX_RSP:
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiBusyRfBusyTimerExpired : public StateBase {
public:
  StateSpiBusyRfBusyTimerExpired() {}
  ~StateSpiBusyRfBusyTimerExpired() {}

  eStates_t GetState() { return ST_SPI_BUSY_RF_BUSY_TIMER_EXPIRED; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_SPI_RX:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_SUSPENDED_RF_BUSY)->second;
      SendSwpSwitchAllowCmd();
      break;
    case EVT_SPI_RX_WTX_REQ:
      break;
    case EVT_SPI_TX_WTX_RSP:
      break;
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      PtrNextState = sListOfStates.find(ST_SPI_BUSY_RF_IDLE)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiBusyRfIdle : public StateBase {
public:
  StateSpiBusyRfIdle() {}
  ~StateSpiBusyRfIdle() {}

  eStates_t GetState() { return ST_SPI_BUSY_RF_IDLE; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_START:
      [[fallthrough]];
    case EVT_RF_ON:
      PtrNextState = sListOfStates.find(ST_SPI_RX_PENDING_RF_PENDING)->second;
      break;
    case EVT_RF_ON_FELICA_APP:
      PtrNextState =
          sListOfStates.find(ST_SPI_RX_PENDING_RF_PENDING_FELICA)->second;
      break;
    case EVT_SPI_RX:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RF_IDLE)->second;
      break;
    case EVT_SPI_RX_WTX_REQ:
      break;
    case EVT_SPI_TX_WTX_RSP:
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiClosedRfBusy : public StateBase {
public:
  StateSpiClosedRfBusy() {}
  ~StateSpiClosedRfBusy() {}

  eStates_t GetState() { return ST_SPI_CLOSED_RF_BUSY; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_IDLE)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiClosedRfIdle : public StateBase {
public:
  StateSpiClosedRfIdle() {}
  ~StateSpiClosedRfIdle() {}

  eStates_t GetState() { return ST_SPI_CLOSED_RF_IDLE; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_SPI_HW_SERVICE_START:
      break;
    case EVT_HCI_INIT_START:
      NotifyHciEvtProcessComplete();
      [[fallthrough]];
    case EVT_RF_ON:
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_BUSY)->second;
      break;
    case EVT_SPI_SESSION_OPEN:
      eStatus_t statusOmapicmd;
      statusOmapicmd = SendOMAPISessionOpenCmd();
      if (statusOmapicmd == SM_STATUS_SUCCESS) {
        PtrNextState = sListOfStates.find(ST_SPI_SESSION_OPEN_RF_IDLE)->second;
      }
      PtrNextState->mLastProcessEventStatus = statusOmapicmd;
      break;
    case EVT_SPI_DEVICE_OPEN:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_FOR_ESEUPDATE_RF_IDLE)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiOpenForEseUpdateRfIdle : public StateBase {
public:
  StateSpiOpenForEseUpdateRfIdle() {}
  ~StateSpiOpenForEseUpdateRfIdle() {}

  eStates_t GetState() { return ST_SPI_OPEN_FOR_ESEUPDATE_RF_IDLE; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_SPI_DEVICE_CLOSE:
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_IDLE)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};


class StateSpiOpenResumedRfBusy : public StateBase {
public:
  StateSpiOpenResumedRfBusy() {}
  ~StateSpiOpenResumedRfBusy() {}

  eStates_t GetState() { return ST_SPI_OPEN_RESUMED_RF_BUSY; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      TimerStop();
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RF_IDLE)->second;
      break;
    case EVT_SPI_TX:
      PtrNextState = sListOfStates.find(ST_SPI_BUSY_RF_BUSY)->second;
      break;
    case EVT_SPI_SESSION_CLOSE:
      ALOGE("Unexpected: SPI SESSION CLOSE evt RECVD!! ");
      SendOMAPISessionCloseCmd();
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_BUSY)->second;
      break;
    case EVT_SPI_TIMER_EXPIRED:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_SUSPENDED_RF_BUSY)->second;
      SendSwpSwitchAllowCmd();
      break;
    case EVT_SPI_DEVICE_CLOSE:
      PtrNextState =
          sListOfStates.find(ST_SPI_SESSION_OPEN_RESUMED_RF_BUSY)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiSessionOpenResumedRfBusy : public StateBase {
public:
  StateSpiSessionOpenResumedRfBusy() {}
  ~StateSpiSessionOpenResumedRfBusy() {}

  eStates_t GetState() { return ST_SPI_SESSION_OPEN_RESUMED_RF_BUSY; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      TimerStop();
      PtrNextState = sListOfStates.find(ST_SPI_SESSION_OPEN_RF_IDLE)->second;
      break;
    case EVT_SPI_DEVICE_OPEN:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RESUMED_RF_BUSY)->second;
      break;
    case EVT_SPI_SESSION_CLOSE:
      SendOMAPISessionCloseCmd();
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_BUSY)->second;
      break;
    case EVT_SPI_TIMER_EXPIRED:
      SendSwpSwitchAllowCmd();
      PtrNextState =
          sListOfStates.find(ST_SPI_SESSION_OPEN_SUSPENDED_RF_BUSY)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiSessionOpenRfIdle : public StateBase {
public:
  StateSpiSessionOpenRfIdle() {}
  ~StateSpiSessionOpenRfIdle() {}

  eStates_t GetState() { return ST_SPI_SESSION_OPEN_RF_IDLE; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_START:
      [[fallthrough]];
    case EVT_RF_ON:
      SendSwpSwitchAllowCmd();
      PtrNextState =
          sListOfStates.find(ST_SPI_SESSION_OPEN_SUSPENDED_RF_BUSY)->second;
      break;
    case EVT_RF_ON_FELICA_APP:
      PtrNextState =
          sListOfStates.find(ST_SPI_SESSION_OPEN_RESUMED_RF_BUSY)->second;
      TimerStart(gFelicaAppTimeout);
      break;
    case EVT_SPI_DEVICE_CLOSE:
      PtrNextState = sListOfStates.find(ST_SPI_SESSION_OPEN_RF_IDLE)->second;
      break;
    case EVT_SPI_SESSION_CLOSE:
      SendOMAPISessionCloseCmd();
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_IDLE)->second;
      break;
    case EVT_SPI_DEVICE_OPEN:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RF_IDLE)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiOpenRfIdle : public StateBase {
public:
  StateSpiOpenRfIdle() {}
  ~StateSpiOpenRfIdle() {}

  eStates_t GetState() { return ST_SPI_OPEN_RF_IDLE; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_START:
      [[fallthrough]];
    case EVT_RF_ON:
      SendSwpSwitchAllowCmd();
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_SUSPENDED_RF_BUSY)->second;
      break;
    case EVT_RF_ON_FELICA_APP:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RESUMED_RF_BUSY)->second;
      TimerStart(gFelicaAppTimeout);
      break;
    case EVT_SPI_TX:
      PtrNextState = sListOfStates.find(ST_SPI_BUSY_RF_IDLE)->second;
      break;
    case EVT_SPI_DEVICE_CLOSE:
      PtrNextState = sListOfStates.find(ST_SPI_SESSION_OPEN_RF_IDLE)->second;
      break;
    case EVT_SPI_SESSION_CLOSE:
      ALOGE("Unexpected: SPI SESSION CLOSE evt RECVD!! ");
      SendOMAPISessionCloseCmd();
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_IDLE)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiOpenSuspendedRfBusy : public StateBase {
public:
  StateSpiOpenSuspendedRfBusy() {}
  ~StateSpiOpenSuspendedRfBusy() {}

  eStates_t GetState() { return ST_SPI_OPEN_SUSPENDED_RF_BUSY; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      SendOMAPISessionOpenCmd();
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RF_IDLE)->second;
      break;
    case EVT_RF_ACT_NTF_ESE:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RESUMED_RF_BUSY)->second;
      break;
    case EVT_SPI_TX:
      break;
    case EVT_SPI_DEVICE_CLOSE:
      PtrNextState =
          sListOfStates.find(ST_SPI_SESSION_OPEN_SUSPENDED_RF_BUSY)->second;
      break;
    case EVT_SPI_SESSION_CLOSE:
      ALOGE("Unexpected: SPI SESSION CLOSE evt RECVD!! ");
      SendOMAPISessionCloseCmd();
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_BUSY)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiSessionOpenSuspendedRfBusy : public StateBase {
public:
  StateSpiSessionOpenSuspendedRfBusy() {}
  ~StateSpiSessionOpenSuspendedRfBusy() {}

  eStates_t GetState() { return ST_SPI_SESSION_OPEN_SUSPENDED_RF_BUSY; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      SendOMAPISessionOpenCmd();
      PtrNextState = sListOfStates.find(ST_SPI_SESSION_OPEN_RF_IDLE)->second;
      break;
    case EVT_RF_ACT_NTF_ESE:
      PtrNextState =
          sListOfStates.find(ST_SPI_SESSION_OPEN_RESUMED_RF_BUSY)->second;
      break;
    case EVT_SPI_SESSION_CLOSE:
      SendOMAPISessionCloseCmd();
      PtrNextState = sListOfStates.find(ST_SPI_CLOSED_RF_BUSY)->second;
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiRxPendingRfPending : public StateBase {
public:
  StateSpiRxPendingRfPending() {}
  ~StateSpiRxPendingRfPending() {}

  eStates_t GetState() { return ST_SPI_RX_PENDING_RF_PENDING; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      PtrNextState = sListOfStates.find(ST_SPI_BUSY_RF_IDLE)->second;
      break;
    case EVT_SPI_RX:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_SUSPENDED_RF_BUSY)->second;
      SendSwpSwitchAllowCmd();
      break;
    case EVT_SPI_RX_WTX_REQ:
      break;
    case EVT_SPI_TX_WTX_RSP:
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

class StateSpiRxPendingRfPendingFelica : public StateBase {
public:
  StateSpiRxPendingRfPendingFelica() {}
  ~StateSpiRxPendingRfPendingFelica() {}

  eStates_t GetState() { return ST_SPI_RX_PENDING_RF_PENDING_FELICA; }

  StateBase *ProcessEvent(eExtEvent_t event) {
    StateBase *PtrNextState = this;
    switch (event) {
    case EVT_HCI_INIT_COMPLETE:
    case EVT_RF_OFF:
      PtrNextState = sListOfStates.find(ST_SPI_BUSY_RF_IDLE)->second;
      break;
    case EVT_SPI_RX:
      PtrNextState = sListOfStates.find(ST_SPI_OPEN_RESUMED_RF_BUSY)->second;
      TimerStart(gFelicaAppTimeout);
      break;
    case EVT_SPI_RX_WTX_REQ:
      break;
    case EVT_SPI_TX_WTX_RSP:
      break;
    default:
      break;
    }
    return PtrNextState;
  }
};

/************************State Base Class Definition***************************/

StateBase *StateBase::InitializeStates() {
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_CLOSED_RF_IDLE, new StateSpiClosedRfIdle()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_CLOSED_RF_BUSY, new StateSpiClosedRfBusy()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_OPEN_RF_IDLE, new StateSpiOpenRfIdle()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_BUSY_RF_IDLE, new StateSpiBusyRfIdle()));
  StateBase::sListOfStates.insert(make_pair(ST_SPI_RX_PENDING_RF_PENDING,
                                            new StateSpiRxPendingRfPending()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_RX_PENDING_RF_PENDING_FELICA,
                new StateSpiRxPendingRfPendingFelica()));
  StateBase::sListOfStates.insert(make_pair(ST_SPI_OPEN_SUSPENDED_RF_BUSY,
                                            new StateSpiOpenSuspendedRfBusy()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_OPEN_RESUMED_RF_BUSY, new StateSpiOpenResumedRfBusy()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_BUSY_RF_BUSY, new StateSpiBusyRfBusy()));
  StateBase::sListOfStates.insert(make_pair(
      ST_SPI_BUSY_RF_BUSY_TIMER_EXPIRED, new StateSpiBusyRfBusyTimerExpired()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_SESSION_OPEN_RF_IDLE, new StateSpiSessionOpenRfIdle()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_SESSION_OPEN_SUSPENDED_RF_BUSY,
                new StateSpiSessionOpenSuspendedRfBusy()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_SESSION_OPEN_RESUMED_RF_BUSY,
                new StateSpiSessionOpenResumedRfBusy()));
  StateBase::sListOfStates.insert(
      make_pair(ST_SPI_OPEN_FOR_ESEUPDATE_RF_IDLE,
                new StateSpiOpenForEseUpdateRfIdle()));

  StateBase *PtrCurrentState =
      StateBase::sListOfStates.find(ST_SPI_CLOSED_RF_IDLE)->second;
  return PtrCurrentState;
}

eStatus_t StateBase::SendOMAPICommand(uint8_t cmd[], uint8_t cmd_len) {
  int nfc_access_retryCnt = 0;
  int retval;
  phNxpNci_Extn_Cmd_t inData;
  phNxpNci_Extn_Resp_t outData;
  memset(&inData, 0x00, sizeof(phNxpNci_Extn_Cmd_t));
  memset(&outData, 0x00, sizeof(phNxpNci_Extn_Resp_t));
  inData.cmd_len = cmd_len;
  memcpy(inData.p_cmd, cmd, cmd_len);
  omapi_status = ESESTATUS_FAILED;
retry_nfc_access:
  retval =
      NfcAdaptation::GetInstance().HalNciTransceive(&inData,&outData);
  if (omapi_status != 0) {
    ALOGE_IF(state_machine_debug, "omapi_status return failed");
    nfc_access_retryCnt++;
    usleep(2000000);
    if (nfc_access_retryCnt < 5)
      goto retry_nfc_access;
    return SM_STATUS_FAILED;
  }

  return SM_STATUS_SUCCESS;
}

eStatus_t StateBase::NotifyHciEvtProcessComplete() {
  ese_nxp_IoctlInOutData_t inpOutData;
  memset(&inpOutData, 0x00, sizeof(ese_nxp_IoctlInOutData_t));
  ESESTATUS retval =
      NfcAdaptation::GetInstance().notifyHciEvtProcessComplete();
  if (retval != ESESTATUS_SUCCESS) return SM_STATUS_FAILED;
  return SM_STATUS_SUCCESS;
}

eStatus_t StateBase::SendOMAPISessionOpenCmd() {
  uint8_t cmd_omapi_session_open[] = {0x2F, 0x01, 0x01, 0x01};
  return SendOMAPICommand(cmd_omapi_session_open,
                          sizeof(cmd_omapi_session_open));
}

eStatus_t StateBase::SendOMAPISessionCloseCmd() {
  uint8_t cmd_omapi_session_close[] = {0x2F, 0x01, 0x01, 0x00};
  return SendOMAPICommand(cmd_omapi_session_close,
                          sizeof(cmd_omapi_session_close));
}

eStatus_t StateBase::SendSwpSwitchAllowCmd() {
  uint8_t cmd_clt_session_allow[] = {0x2F, 0x01, 0x01, 0x02};
  return SendOMAPICommand(cmd_clt_session_allow, sizeof(cmd_clt_session_allow));
}

void StateBase::TimerTimeoutCallback(union sigval) {
  ALOGD_IF(state_machine_debug, "Timer expired...");
  StateMachine::GetInstance().ProcessExtEvent(EVT_SPI_TIMER_EXPIRED);
}

void StateBase::TimerStart(unsigned long sec) {
  if (StateBase::sTimerInstance.set(sec * 1000,
                                    StateBase::TimerTimeoutCallback) == true) {
    ALOGD_IF(state_machine_debug, "Starting %ld milliseconds timer",
             sec * 1000);
  } else {
    ALOGE_IF(state_machine_debug, "Error, Starting timer...");
  }
}

void StateBase::TimerStop() {
  ALOGD_IF(state_machine_debug, "Stopping timer...");
  StateBase::sTimerInstance.kill();
}
