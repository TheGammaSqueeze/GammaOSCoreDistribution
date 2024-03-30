/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright 2019-2020 NXP.
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

/*
 *  Synchronize two or more threads using a condition variable and a mutex.
 */
#pragma once
#include <list>

#include "CondVar.h"
#include "Mutex.h"
using namespace std;

class SyncEvent;

extern std::list<SyncEvent *> syncEventList;

class SyncEvent {
public:
  /*******************************************************************************
  **
  ** Function:        ~SyncEvent
  **
  ** Description:     Cleanup all resources.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  ~SyncEvent();

  /*******************************************************************************
  **
  ** Function:        start
  **
  ** Description:     Start a synchronization operation.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  void start();

  /*******************************************************************************
  **
  ** Function:        wait
  **
  ** Description:     Block the thread and wait for the event to occur.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  void wait();

  /*******************************************************************************
  **
  ** Function:        wait
  **
  ** Description:     Block the thread and wait for the event to occur.
  **                  millisec: Timeout in milliseconds.
  **
  ** Returns:         True if wait is successful; false if timeout occurs.
  **
  *******************************************************************************/
  bool wait(long millisec);

  /*******************************************************************************
  **
  ** Function:        notifyOne
  **
  ** Description:     Notify a blocked thread that the event has occurred.
  *Unblocks it.
  **                  Deregisters cached event.
  ** Returns:         None.
  **
  *******************************************************************************/
  void notifyOne();

  /*******************************************************************************
  **
  ** Function:        notify
  **
  ** Description:     Notify a blocked thread that the event has occurred.
  *Unblocks it.
  **                  This function won't deregister cached event
  ** Returns:         None.
  **
  *******************************************************************************/
  void notify();

  /*******************************************************************************
  **
  ** Function:        end
  **
  ** Description:     End a synchronization operation.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  void end();

  /********Implement equality operator for SyncEvent
   * Class***********************/
  bool operator==(const SyncEvent &event) { return (this == &event); }

  /*******************************************************************************
  **
  ** Function:        addEvent
  **
  ** Description:     cache event locally
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  void addEvent();

  /*******************************************************************************
  **
  ** Function:        notifyAll
  **
  ** Description:     Notify all blocked thread that the event has occurred.
  *Unblocks it.
  **                  clears the event cache
  ** Returns:         None.
  **
  *******************************************************************************/
  void notifyAll();

  /*******************************************************************************
  **
  ** Function:        removeEvent
  **
  ** Description:     remove event from cache event.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  void removeEvent();

private:
  CondVar mCondVar;
  Mutex mMutex;
  bool mWait = false;
};

/*****************************************************************************/
/*****************************************************************************/

/*****************************************************************************
**
**  Name:           SyncEventGuard
**
**  Description:    Automatically start and end a synchronization event.
**
*****************************************************************************/
class SyncEventGuard {
public:
  /*******************************************************************************
  **
  ** Function:        SyncEventGuard
  **
  ** Description:     Start a synchronization operation.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  SyncEventGuard(SyncEvent &event) : mEvent(event) {
    event.start(); // automatically start operation
  };

  /*******************************************************************************
  **
  ** Function:        ~SyncEventGuard
  **
  ** Description:     End a synchronization operation.
  **
  ** Returns:         None.
  **
  *******************************************************************************/
  ~SyncEventGuard() {
    mEvent.end(); // automatically end operation
  };

private:
  SyncEvent &mEvent;
};
