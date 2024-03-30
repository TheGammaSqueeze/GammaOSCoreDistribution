/*
 * Copyright 2021 The Android Open Source Project
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
 * Generated mock file from original source file
 *   Functions generated:7
 */

#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

#include <base/bind.h>
#include <base/logging.h>
#include <base/run_loop.h>
#include <base/threading/thread.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bta/sys/bta_sys.h"
#include "btcore/include/module.h"
#include "btif/include/btif_common.h"
#include "btm_iso_api.h"
#include "common/message_loop_thread.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/bt_hdr.h"

#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

void btu_hci_msg_process(BT_HDR* p_msg) { mock_function_count_map[__func__]++; }
