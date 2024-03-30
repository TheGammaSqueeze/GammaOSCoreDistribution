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

#ifndef LIBESE_SPI_SRC_SYNC_ESEHALSTATES_H_
#define LIBESE_SPI_SRC_SYNC_ESEHALSTATES_H_

#include <IntervalTimer.h>
#include <StateMachineInfo.h>
#include <map>
#include <string>

using namespace std;

class StateBase {
public:
  StateBase();
  virtual ~StateBase(){};
  static StateBase *InitializeStates();
  virtual eStates_t GetState() = 0;
  virtual StateBase *ProcessEvent(eExtEvent_t) = 0;
  eStatus_t mLastProcessEventStatus;

protected:
  static map<eStates_t, StateBase *> sListOfStates;
  static IntervalTimer sTimerInstance;
  eStatus_t SendOMAPISessionOpenCmd();
  eStatus_t SendOMAPISessionCloseCmd();
  eStatus_t SendSwpSwitchAllowCmd();
  eStatus_t SendOMAPICommand(uint8_t cmd[], uint8_t cmd_len);
  eStatus_t NotifyHciEvtProcessComplete();
  static void TimerStart(unsigned long millisecs);
  static void TimerStop();
  static void TimerTimeoutCallback(union sigval);
};

#endif /* LIBESE_SPI_SRC_SYNC_ESEHALSTATES_H_ */
