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

#include <string.h>

#include <android/api-level.h>
#include <sys/system_properties.h>

namespace android {
namespace modules {
namespace sdklevel {

namespace detail {

inline void GetCodename(char (&codename)[PROP_VALUE_MAX]) {
  // ro. properties can be longer than PROP_VALUE_MAX, but *this* property
  // is not likely to be very long.
  __system_property_get("ro.build.version.codename", codename);
}

} // namespace detail

// Checks if the device is running on release version of Android R or newer.
inline bool IsAtLeastR() { return android_get_device_api_level() >= 30; }

// Checks if the device is running on release version of Android S or newer.
inline bool IsAtLeastS() { return android_get_device_api_level() >= 31; }

// Checks if the device is running on release version of Android T or newer.
inline bool IsAtLeastT() { return android_get_device_api_level() >= 33; }

} // namespace sdklevel
} // namespace modules
} // namespace android
