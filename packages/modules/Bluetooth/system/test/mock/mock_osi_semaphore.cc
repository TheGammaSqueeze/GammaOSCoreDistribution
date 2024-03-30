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
#include "test/mock/mock_osi_semaphore.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_semaphore {

// Function state capture and return values, if needed
struct semaphore_free semaphore_free;
struct semaphore_get_fd semaphore_get_fd;
struct semaphore_new semaphore_new;
struct semaphore_post semaphore_post;
struct semaphore_try_wait semaphore_try_wait;
struct semaphore_wait semaphore_wait;

}  // namespace osi_semaphore
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void semaphore_free(semaphore_t* semaphore) {
  mock_function_count_map[__func__]++;
  test::mock::osi_semaphore::semaphore_free(semaphore);
}
int semaphore_get_fd(const semaphore_t* semaphore) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_semaphore::semaphore_get_fd(semaphore);
}
semaphore_t* semaphore_new(unsigned int value) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_semaphore::semaphore_new(value);
}
void semaphore_post(semaphore_t* semaphore) {
  mock_function_count_map[__func__]++;
  test::mock::osi_semaphore::semaphore_post(semaphore);
}
bool semaphore_try_wait(semaphore_t* semaphore) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_semaphore::semaphore_try_wait(semaphore);
}
void semaphore_wait(semaphore_t* semaphore) {
  mock_function_count_map[__func__]++;
  test::mock::osi_semaphore::semaphore_wait(semaphore);
}
// Mocked functions complete
// END mockcify generation
