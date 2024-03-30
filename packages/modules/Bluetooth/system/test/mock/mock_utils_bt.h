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

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.

#include "utils/include/bt_utils.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace utils_bt {

// Shared state between mocked functions and tests
// Name: raise_priority_a2dp
// Params: tHIGH_PRIORITY_TASK high_task
// Return: void
struct raise_priority_a2dp {
  std::function<void(tHIGH_PRIORITY_TASK high_task)> body{
      [](tHIGH_PRIORITY_TASK high_task) {}};
  void operator()(tHIGH_PRIORITY_TASK high_task) { body(high_task); };
};
extern struct raise_priority_a2dp raise_priority_a2dp;

}  // namespace utils_bt
}  // namespace mock
}  // namespace test

// END mockcify generation
