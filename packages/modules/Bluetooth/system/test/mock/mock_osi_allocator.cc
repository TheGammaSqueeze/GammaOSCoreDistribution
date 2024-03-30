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

// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_allocator.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_allocator {

// Function state capture and return values, if needed
struct osi_calloc osi_calloc;
struct osi_free osi_free;
struct osi_free_and_reset osi_free_and_reset;
struct osi_malloc osi_malloc;
struct osi_strdup osi_strdup;
struct osi_strndup osi_strndup;

}  // namespace osi_allocator
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void* osi_calloc(size_t size) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocator::osi_calloc(size);
}
void osi_free(void* ptr) {
  mock_function_count_map[__func__]++;
  test::mock::osi_allocator::osi_free(ptr);
}
void osi_free_and_reset(void** p_ptr) {
  mock_function_count_map[__func__]++;
  test::mock::osi_allocator::osi_free_and_reset(p_ptr);
}
void* osi_malloc(size_t size) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocator::osi_malloc(size);
}
char* osi_strdup(const char* str) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocator::osi_strdup(str);
}
char* osi_strndup(const char* str, size_t len) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_allocator::osi_strndup(str, len);
}
// Mocked functions complete
// END mockcify generation
