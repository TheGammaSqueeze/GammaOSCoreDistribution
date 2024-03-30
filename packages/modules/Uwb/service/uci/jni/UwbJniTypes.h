/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Copyright 2021 NXP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _UWB_JNI_TYPES_
#define _UWB_JNI_TYPES_

#include <array>
#include <deque>
#include <map>
#include <mutex>
#include <numeric>

#include "SyncEvent.h"
#include "uci_defs.h"
#include "uwa_api.h"

typedef struct UwbDeviceInfo {
  uint16_t uciVersion;
  uint16_t macVersion;
  uint16_t phyVersion;
  uint16_t uciTestVersion;
} deviceInfo_t;

typedef struct conformanceTestData {
  SyncEvent ConfigEvt;
  tUWA_STATUS wstatus;
  uint8_t rsp_data[CONFORMANCE_TEST_MAX_UCI_PKT_LENGTH];
  uint8_t rsp_len;
} conformanceTestData_t;

/* Session Data contains M distance samples of N Anchors in order to provide
 * averaged distance for every anchor */
/* N is Maximum Number of Anchors(MAX_NUM_RESPONDERS) */
/* Where M is sampling Rate, the Max value is defined by Service */
typedef struct sessionRangingData {
  uint8_t samplingRate;
  std::array<std::deque<uint32_t>, MAX_NUM_RESPONDERS> anchors;
} SessionRangingData;

#endif
