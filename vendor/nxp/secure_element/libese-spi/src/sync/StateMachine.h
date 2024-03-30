/******************************************************************************
 *
 *  Copyright 2018 NXP
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

#ifndef LIBESE_SPI_SRC_SYNC_STATEMACHINE_H_
#define LIBESE_SPI_SRC_SYNC_STATEMACHINE_H_

#include "EseHalStates.h"
#include <SyncEvent.h>

#include "StateMachineInfo.h"

class StateMachine {
private:
  Mutex mProcessExtEventLock;
  StateMachine();
  static StateMachine sStateMachine;
  StateBase *mPtrCurrentState;

public:
  ~StateMachine();
  static StateMachine &GetInstance();
  bool isSpiTxRxAllowed();
  eStates_t GetCurrentState();
  eStatus_t ProcessExtEvent(eExtEvent_t);
};

#endif /* LIBESE_SPI_SRC_SYNC_STATEMACHINE_H_ */
