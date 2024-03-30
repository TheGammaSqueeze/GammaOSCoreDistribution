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

// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_allocation_tracker.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_allocation_tracker {

// Function state capture and return values, if needed
struct allocation_tracker_expect_no_allocations
    allocation_tracker_expect_no_allocations;
struct allocation_tracker_init allocation_tracker_init;
struct allocation_tracker_notify_alloc allocation_tracker_notify_alloc;
struct allocation_tracker_notify_free allocation_tracker_notify_free;
struct allocation_tracker_reset allocation_tracker_reset;
struct allocation_tracker_resize_for_canary
    allocation_tracker_resize_for_canary;
struct allocation_tracker_uninit allocation_tracker_uninit;
struct osi_allocator_debug_dump osi_allocator_debug_dump;

}  // namespace osi_allocation_tracker
}  // namespace mock
}  // namespace test

// Mocked functions, if any
size_t allocation_tracker_expect_no_allocations(void) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocation_tracker::
      allocation_tracker_expect_no_allocations();
}
void allocation_tracker_init(void) {
  mock_function_count_map[__func__]++;
  test::mock::osi_allocation_tracker::allocation_tracker_init();
}
void* allocation_tracker_notify_alloc(uint8_t allocator_id, void* ptr,
                                      size_t requested_size) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocation_tracker::allocation_tracker_notify_alloc(
      allocator_id, ptr, requested_size);
}
void* allocation_tracker_notify_free(uint8_t allocator_id, void* ptr) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocation_tracker::allocation_tracker_notify_free(
      allocator_id, ptr);
}
void allocation_tracker_reset(void) {
  mock_function_count_map[__func__]++;
  test::mock::osi_allocation_tracker::allocation_tracker_reset();
}
size_t allocation_tracker_resize_for_canary(size_t size) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocation_tracker::
      allocation_tracker_resize_for_canary(size);
}
void allocation_tracker_uninit(void) {
  mock_function_count_map[__func__]++;
  test::mock::osi_allocation_tracker::allocation_tracker_uninit();
}
void osi_allocator_debug_dump(int fd) {
  mock_function_count_map[__func__]++;
  test::mock::osi_allocation_tracker::osi_allocator_debug_dump(fd);
}
// Mocked functions complete
// END mockcify generation
