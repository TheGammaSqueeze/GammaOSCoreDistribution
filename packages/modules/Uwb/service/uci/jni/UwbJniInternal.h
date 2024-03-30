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

#ifndef _UWBAPI_INTERNAL_H_
#define _UWBAPI_INTERNAL_H_

#include <nativehelper/ScopedLocalRef.h>

#include "UwbJniTypes.h"
#include "UwbJniUtil.h"
#include "uwa_api.h"

namespace android {

#define UWB_CMD_TIMEOUT 4000 // JNI API wait timout

/* extern declarations */
extern bool uwb_debug_enabled;
extern bool gIsUwaEnabled;

void clearRfTestContext();
void uwaRfTestDeviceManagementCallback(uint8_t dmEvent,
                                       tUWA_DM_TEST_CBACK_DATA *eventData);
} // namespace android
#endif
