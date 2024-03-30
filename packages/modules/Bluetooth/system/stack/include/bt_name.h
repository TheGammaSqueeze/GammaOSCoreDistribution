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

#pragma once

#ifdef __cplusplus
#include <cstdint>
#include <cstring>
#else
#include <stdint.h>
#include <string.h>
#endif

// NOTE: Shared with internal_include/bt_target.h
/* Maximum device name length used in btm database. */
#ifndef BTM_MAX_REM_BD_NAME_LEN
#define BTM_MAX_REM_BD_NAME_LEN 248
#endif

/* Maximum local device name length stored btm database */
#ifndef BTM_MAX_LOC_BD_NAME_LEN
#define BTM_MAX_LOC_BD_NAME_LEN 248
#endif

#define BD_NAME_LEN 248
typedef uint8_t BD_NAME[BD_NAME_LEN + 1]; /* Device name */

/* Device name of peer (may be truncated to save space in BTM database) */
typedef uint8_t tBTM_BD_NAME[BTM_MAX_REM_BD_NAME_LEN + 1];

typedef uint8_t tBTM_LOC_BD_NAME[BTM_MAX_LOC_BD_NAME_LEN + 1];

#ifdef __cplusplus
inline constexpr tBTM_BD_NAME kBtmBdNameEmpty = {};
#endif
