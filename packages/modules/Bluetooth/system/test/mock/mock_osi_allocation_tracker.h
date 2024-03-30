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
 *   Functions generated:8
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
#include <base/logging.h>
#include <stdlib.h>
#include <string.h>

#include <mutex>
#include <unordered_map>

#include "check.h"
#include "osi/include/allocation_tracker.h"
#include "osi/include/allocator.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_allocation_tracker {

// Shared state between mocked functions and tests
// Name: allocation_tracker_expect_no_allocations
// Params: void
// Return: size_t
struct allocation_tracker_expect_no_allocations {
  size_t return_value{0};
  std::function<size_t(void)> body{[this](void) { return return_value; }};
  size_t operator()(void) { return body(); };
};
extern struct allocation_tracker_expect_no_allocations
    allocation_tracker_expect_no_allocations;

// Name: allocation_tracker_init
// Params: void
// Return: void
struct allocation_tracker_init {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct allocation_tracker_init allocation_tracker_init;

// Name: allocation_tracker_notify_alloc
// Params: uint8_t allocator_id, void* ptr, size_t requested_size
// Return: void*
struct allocation_tracker_notify_alloc {
  void* return_value{};
  std::function<void*(uint8_t allocator_id, void* ptr, size_t requested_size)>
      body{[this](uint8_t allocator_id, void* ptr, size_t requested_size) {
        return return_value;
      }};
  void* operator()(uint8_t allocator_id, void* ptr, size_t requested_size) {
    return body(allocator_id, ptr, requested_size);
  };
};
extern struct allocation_tracker_notify_alloc allocation_tracker_notify_alloc;

// Name: allocation_tracker_notify_free
// Params:  uint8_t allocator_id, void* ptr
// Return: void*
struct allocation_tracker_notify_free {
  void* return_value{};
  std::function<void*(uint8_t allocator_id, void* ptr)> body{
      [this](uint8_t allocator_id, void* ptr) { return return_value; }};
  void* operator()(uint8_t allocator_id, void* ptr) {
    return body(allocator_id, ptr);
  };
};
extern struct allocation_tracker_notify_free allocation_tracker_notify_free;

// Name: allocation_tracker_reset
// Params: void
// Return: void
struct allocation_tracker_reset {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct allocation_tracker_reset allocation_tracker_reset;

// Name: allocation_tracker_resize_for_canary
// Params: size_t size
// Return: size_t
struct allocation_tracker_resize_for_canary {
  size_t return_value{0};
  std::function<size_t(size_t size)> body{
      [this](size_t size) { return return_value; }};
  size_t operator()(size_t size) { return body(size); };
};
extern struct allocation_tracker_resize_for_canary
    allocation_tracker_resize_for_canary;

// Name: allocation_tracker_uninit
// Params: void
// Return: void
struct allocation_tracker_uninit {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct allocation_tracker_uninit allocation_tracker_uninit;

// Name: osi_allocator_debug_dump
// Params: int fd
// Return: void
struct osi_allocator_debug_dump {
  std::function<void(int fd)> body{[](int fd) {}};
  void operator()(int fd) { body(fd); };
};
extern struct osi_allocator_debug_dump osi_allocator_debug_dump;

}  // namespace osi_allocation_tracker
}  // namespace mock
}  // namespace test

// END mockcify generation