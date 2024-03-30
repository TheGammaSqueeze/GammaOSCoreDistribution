/******************************************************************************
 *
 *  Copyright 2015 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
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

#include <mutex>

#include <base/logging.h>
#include <resolv.h>
#include <zlib.h>

#include "internal_include/bt_target.h"
#include "osi/include/properties.h"
#include "osi/include/ringbuffer.h"

#define REDUCE_HCI_TYPE_TO_SIGNIFICANT_BITS(type) ((type) >> 8)

// Total btsnoop memory log buffer size
#ifndef BTSNOOP_MEM_BUFFER_SIZE
#endif

#ifndef OS_ANDROID
#else
#endif
