/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright 2021 NXP.
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

#include "SyncEvent.h"

#include <android-base/stringprintf.h>
#include <android-base/logging.h>

using android::base::StringPrintf;

std::list<SyncEvent *> syncEventList;
std::mutex syncEventListMutex;

SyncEvent::~SyncEvent() { mWait = false; }

void SyncEvent::start() {
  mWait = false;
  mMutex.lock();
}

void SyncEvent::wait() {
  mWait = true;
  addEvent();
  while (mWait) {
    mCondVar.wait(mMutex);
  }
}

bool SyncEvent::wait(long millisec) {
  bool retVal;
  mWait = true;
  addEvent();
  while (mWait) {
    retVal = mCondVar.wait(mMutex, millisec);
    if (!retVal)
      mWait = false;
  }
  return retVal;
}

void SyncEvent::notifyOne() {
  mWait = false;
  removeEvent();
  mCondVar.notifyOne();
}

void SyncEvent::notify() {
  mWait = false;
  mCondVar.notifyOne();
}

void SyncEvent::end() {
  mWait = false;
  mMutex.unlock();
}

void SyncEvent::addEvent() {
  std::lock_guard<std::mutex> guard(
      syncEventListMutex); // with lock access list
  bool contains = (std::find(syncEventList.begin(), syncEventList.end(),
                             this) != syncEventList.end());
  if (!contains)
    syncEventList.push_back(this);
}

void SyncEvent::removeEvent() {
  std::lock_guard<std::mutex> guard(
      syncEventListMutex); // with lock access list
  syncEventList.remove(this);
}

void SyncEvent::notifyAll() {
  std::lock_guard<std::mutex> guard(
      syncEventListMutex); // with lock access list
  for (auto &i : syncEventList) {
    if (i != NULL)
      i->notify();
  }
  syncEventList.clear();
}
