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

#include <map>

#include "osi/include/log.h"
#include "test/common/mock_functions.h"

std::map<std::string, int> mock_function_count_map;

void reset_mock_function_count_map() { mock_function_count_map.clear(); }

void dump_mock_function_count_map() {
  LOG_INFO("Mock function count map size:%zu", mock_function_count_map.size());

  for (auto it : mock_function_count_map) {
    LOG_INFO("function:%s: call_count:%d", it.first.c_str(), it.second);
  }
}
