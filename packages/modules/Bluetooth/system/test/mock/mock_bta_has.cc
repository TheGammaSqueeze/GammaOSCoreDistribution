/*
 * Copyright 2022 The Android Open Source Project
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

#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

#include <base/bind.h>
#include <base/bind_helpers.h>

#include "bta/include/bta_has_api.h"
#include "types/raw_address.h"

#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

namespace le_audio {
namespace has {

void HasClient::Initialize(bluetooth::has::HasClientCallbacks*,
                           base::RepeatingCallback<void()>) {
  mock_function_count_map[__func__]++;
}
void HasClient::CleanUp() { mock_function_count_map[__func__]++; }
void HasClient::DebugDump(int) { mock_function_count_map[__func__]++; }
bool HasClient::IsHasClientRunning() {
  mock_function_count_map[__func__]++;
  return false;
}
void HasClient::AddFromStorage(RawAddress const&, unsigned char,
                               unsigned short) {
  mock_function_count_map[__func__]++;
}
HasClient* HasClient::Get() {
  mock_function_count_map[__func__]++;
  return nullptr;
}

}  // namespace has
}  // namespace le_audio
