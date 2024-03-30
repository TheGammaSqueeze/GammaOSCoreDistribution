/******************************************************************************
 *
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

#define LOG_TAG "StateMachine"

#include "StateMachine.h"
#include "EseHalStates.h"
#include <log/log.h>

extern bool state_machine_debug;
StateMachine StateMachine::sStateMachine;

StateMachine::StateMachine() {
  mPtrCurrentState = StateBase::InitializeStates();
}

StateMachine::~StateMachine() {}

StateMachine &StateMachine::GetInstance() { return sStateMachine; }

eStates_t StateMachine::GetCurrentState() {
  return mPtrCurrentState->GetState();
}

eStatus_t StateMachine::ProcessExtEvent(eExtEvent_t event) {
  AutoMutex guard(mProcessExtEventLock);
  ALOGD_IF(state_machine_debug, "%s: enter state:%d event:%d", __FUNCTION__,
           mPtrCurrentState->GetState(), event);
  mPtrCurrentState = mPtrCurrentState->ProcessEvent(event);
  ALOGD_IF(state_machine_debug, "%s: exit state:%d", __FUNCTION__,
           mPtrCurrentState->GetState());
  return SM_STATUS_SUCCESS;
}

bool StateMachine::isSpiTxRxAllowed() {
  if (!((eStates_t)ST_SPI_OPEN_RF_IDLE ==
            (eStates_t)StateMachine::GetInstance().GetCurrentState() ||
        (eStates_t)ST_SPI_OPEN_RESUMED_RF_BUSY ==
            (eStates_t)StateMachine::GetInstance().GetCurrentState() ||
        (eStates_t)ST_SPI_OPEN_FOR_ESEUPDATE_RF_IDLE ==
            (eStates_t)StateMachine::GetInstance().GetCurrentState())) {
    return false;
  } else {
    return true;
  }
}
