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
 *   Functions generated:6
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
#include <errno.h>
#include <fcntl.h>
#include <hardware/bluetooth.h>
#include <inttypes.h>
#include <limits.h>
#include <pthread.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include <mutex>
#include <string>

#include "base/logging.h"
#include "check.h"
#include "common/metrics.h"
#include "osi/include/alarm.h"
#include "osi/include/allocator.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "osi/include/thread.h"
#include "osi/include/wakelock.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_wakelock {

// Shared state between mocked functions and tests
// Name: wakelock_acquire
// Params: void
// Return: bool
struct wakelock_acquire {
  bool return_value{false};
  std::function<bool(void)> body{[this](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct wakelock_acquire wakelock_acquire;

// Name: wakelock_cleanup
// Params: void
// Return: void
struct wakelock_cleanup {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct wakelock_cleanup wakelock_cleanup;

// Name: wakelock_debug_dump
// Params: int fd
// Return: void
struct wakelock_debug_dump {
  std::function<void(int fd)> body{[](int fd) {}};
  void operator()(int fd) { body(fd); };
};
extern struct wakelock_debug_dump wakelock_debug_dump;

// Name: wakelock_release
// Params: void
// Return: bool
struct wakelock_release {
  bool return_value{false};
  std::function<bool(void)> body{[this](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct wakelock_release wakelock_release;

// Name: wakelock_set_os_callouts
// Params: bt_os_callouts_t* callouts
// Return: void
struct wakelock_set_os_callouts {
  std::function<void(bt_os_callouts_t* callouts)> body{
      [](bt_os_callouts_t* callouts) {}};
  void operator()(bt_os_callouts_t* callouts) { body(callouts); };
};
extern struct wakelock_set_os_callouts wakelock_set_os_callouts;

// Name: wakelock_set_paths
// Params: const char* lock_path, const char* unlock_path
// Return: void
struct wakelock_set_paths {
  std::function<void(const char* lock_path, const char* unlock_path)> body{
      [](const char* lock_path, const char* unlock_path) {}};
  void operator()(const char* lock_path, const char* unlock_path) {
    body(lock_path, unlock_path);
  };
};
extern struct wakelock_set_paths wakelock_set_paths;

}  // namespace osi_wakelock
}  // namespace mock
}  // namespace test

// END mockcify generation