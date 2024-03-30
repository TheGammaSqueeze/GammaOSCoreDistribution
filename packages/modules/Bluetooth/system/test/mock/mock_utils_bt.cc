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
 *   Functions generated:1
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Mock include file to share data between tests and mock
#include "test/mock/mock_utils_bt.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace utils_bt {

// Function state capture and return values, if needed
struct raise_priority_a2dp raise_priority_a2dp;

}  // namespace utils_bt
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void raise_priority_a2dp(tHIGH_PRIORITY_TASK high_task) {
  mock_function_count_map[__func__]++;
  test::mock::utils_bt::raise_priority_a2dp(high_task);
}
// Mocked functions complete
// END mockcify generation
