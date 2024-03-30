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
 *   Functions generated:2
 *
 *  mockcify.pl ver 0.3.0
 */

#include <sys/types.h>

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

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_thread_scheduler {

// Shared state between mocked functions and tests
// Name: osi_enable_real_time_scheduling
// Params: pid_t linux_tid
// Return: bool
struct thread_scheduler_enable_real_time {
  bool return_value{false};
  std::function<bool(pid_t linux_tid)> body{
      [this](pid_t linux_tid) { return return_value; }};
  bool operator()(pid_t linux_tid) { return body(linux_tid); };
};
extern struct thread_scheduler_enable_real_time
    thread_scheduler_enable_real_time;

// Name: osi_fifo_scheduing_priority_range
// Params: int& min, int& max
// Return: bool
struct thread_scheduler_get_priority_range {
  bool return_value{false};
  std::function<bool(int& min, int& max)> body{
      [this](int& min, int& max) { return return_value; }};
  bool operator()(int& min, int& max) { return body(min, max); };
};
extern struct thread_scheduler_get_priority_range
    thread_scheduler_get_priority_range;

}  // namespace osi_thread_scheduler
}  // namespace mock
}  // namespace test

// END mockcify generation
