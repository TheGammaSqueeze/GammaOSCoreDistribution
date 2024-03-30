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

#ifndef UWB_UCI_LOG_H_
#define UWB_UCI_LOG_H_

#include <log/log.h>

/* global log level Ref */
extern bool uwb_debug_enabled;

static const char* UWB_UCI_CORE_LOG = "UwbUciCore";

#ifndef UNUSED
#define UNUSED(X) (void)X;
#endif

/* define log module included when compile */
#define ENABLE_UCI_LOGGING TRUE

/* ############## Logging APIs of actual modules ################# */
/* Logging APIs used by UCI module */
#if (ENABLE_UCI_LOGGING == TRUE)
#define UCI_TRACE_D(...)                                         \
  {                                                              \
    if (uwb_debug_enabled)                                       \
      LOG_PRI(ANDROID_LOG_DEBUG, UWB_UCI_CORE_LOG, __VA_ARGS__); \
  }
#define UCI_TRACE_I(...)                                        \
  {                                                             \
    if (uwb_debug_enabled)                                      \
      LOG_PRI(ANDROID_LOG_INFO, UWB_UCI_CORE_LOG, __VA_ARGS__); \
  }
#define UCI_TRACE_W(...)                                        \
  {                                                             \
    if (uwb_debug_enabled)                                      \
      LOG_PRI(ANDROID_LOG_WARN, UWB_UCI_CORE_LOG, __VA_ARGS__); \
  }
#define UCI_TRACE_E(...)                                         \
  {                                                              \
    if (uwb_debug_enabled)                                       \
      LOG_PRI(ANDROID_LOG_ERROR, UWB_UCI_CORE_LOG, __VA_ARGS__); \
  }
#else
#define UCI_TRACE_D(...)
#define UCI_TRACE_I(...)
#define UCI_TRACE_W(...)
#define UCI_TRACE_E(...)
#endif /* Logging APIs used by UCI module */

#endif /* UWB_UCI_LOG_H_ */
