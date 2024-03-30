/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <assert.h>
#include <ctype.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include <android/api-level.h>
#include <log/log.h>
#include <sys/system_properties.h>

#include "sdk_level.h"

namespace android {
namespace modules {
namespace sdklevel {
namespace unbounded {

inline auto getVersionInt(const char *version) {
  LOG_ALWAYS_FATAL_IF(version[0] == '\0', "empty version");
  char *next_char = 0;
  const long versionInt = strtol(version, &next_char, 10);
  LOG_ALWAYS_FATAL_IF(*next_char != '\0', "no conversion from \"%s\" to long",
                      version);
  LOG_ALWAYS_FATAL_IF(versionInt <= 0, "negative version: %s", version);
  LOG_ALWAYS_FATAL_IF(versionInt > INT_MAX, "version too large: %s", version);
  return (int)versionInt;
}

inline bool isCodename(const char *version) {
  LOG_ALWAYS_FATAL_IF(version[0] == '\0', "empty version");
  return isupper(version[0]);
}

// Checks if the device is running a specific version or newer.
// Always use specific methods IsAtLeast*() available in sdk_level.h when the
// version is known at build time. This should only be used when a dynamic
// runtime check is needed.
inline bool IsAtLeast(const char *version) {
  char device_codename[PROP_VALUE_MAX];
  detail::GetCodename(device_codename);
  if (!strcmp("REL", device_codename)) {
    return android_get_device_api_level() >= getVersionInt(version);
  }
  if (isCodename(version)) {
    return strcmp(device_codename, version) >= 0;
  }
  return android_get_device_api_level() >= getVersionInt(version);
}

// Checks if the device is running a specific version or older.
// Always use specific methods IsAtLeast*() available in sdk_level.h when the
// version is known at build time. This should only be used when a dynamic
// runtime check is needed.
inline bool IsAtMost(const char *version) {
  char device_codename[PROP_VALUE_MAX];
  detail::GetCodename(device_codename);
  if (!strcmp("REL", device_codename)) {
    return android_get_device_api_level() <= getVersionInt(version);
  }
  if (isCodename(version)) {
    return strcmp(device_codename, version) <= 0;
  }
  return android_get_device_api_level() < getVersionInt(version);
}

} // namespace unbounded
} // namespace sdklevel
} // namespace modules
} // namespace android
