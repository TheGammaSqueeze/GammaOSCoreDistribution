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
#include "test/mock/mock_common_os_utils.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace common_os_utils {

// Function state capture and return values, if needed
struct is_bluetooth_uid is_bluetooth_uid;

}  // namespace common_os_utils
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace common_os_utils {

bool is_bluetooth_uid::return_value = false;

}  // namespace common_os_utils
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool is_bluetooth_uid() {
  mock_function_count_map[__func__]++;
  return test::mock::common_os_utils::is_bluetooth_uid();
}
// Mocked functions complete
// END mockcify generation
