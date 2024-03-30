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
#include <base/logging.h>
#include <errno.h>
#include <fcntl.h>
#include <malloc.h>
#include <string.h>
#include <sys/eventfd.h>
#include <unistd.h>

#include "check.h"
#include "osi/include/allocator.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "osi/include/semaphore.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace osi_semaphore {

// Shared state between mocked functions and tests
// Name: semaphore_free
// Params: semaphore_t* semaphore
// Return: void
struct semaphore_free {
  std::function<void(semaphore_t* semaphore)> body{
      [](semaphore_t* semaphore) {}};
  void operator()(semaphore_t* semaphore) { body(semaphore); };
};
extern struct semaphore_free semaphore_free;

// Name: semaphore_get_fd
// Params: const semaphore_t* semaphore
// Return: int
struct semaphore_get_fd {
  int return_value{0};
  std::function<int(const semaphore_t* semaphore)> body{
      [this](const semaphore_t* semaphore) { return return_value; }};
  int operator()(const semaphore_t* semaphore) { return body(semaphore); };
};
extern struct semaphore_get_fd semaphore_get_fd;

// Name: semaphore_new
// Params: unsigned int value
// Return: semaphore_t*
struct semaphore_new {
  semaphore_t* return_value{0};
  std::function<semaphore_t*(unsigned int value)> body{
      [this](unsigned int value) { return return_value; }};
  semaphore_t* operator()(unsigned int value) { return body(value); };
};
extern struct semaphore_new semaphore_new;

// Name: semaphore_post
// Params: semaphore_t* semaphore
// Return: void
struct semaphore_post {
  std::function<void(semaphore_t* semaphore)> body{
      [](semaphore_t* semaphore) {}};
  void operator()(semaphore_t* semaphore) { body(semaphore); };
};
extern struct semaphore_post semaphore_post;

// Name: semaphore_try_wait
// Params: semaphore_t* semaphore
// Return: bool
struct semaphore_try_wait {
  bool return_value{false};
  std::function<bool(semaphore_t* semaphore)> body{
      [this](semaphore_t* semaphore) { return return_value; }};
  bool operator()(semaphore_t* semaphore) { return body(semaphore); };
};
extern struct semaphore_try_wait semaphore_try_wait;

// Name: semaphore_wait
// Params: semaphore_t* semaphore
// Return: void
struct semaphore_wait {
  std::function<void(semaphore_t* semaphore)> body{
      [](semaphore_t* semaphore) {}};
  void operator()(semaphore_t* semaphore) { body(semaphore); };
};
extern struct semaphore_wait semaphore_wait;

}  // namespace osi_semaphore
}  // namespace mock
}  // namespace test

// END mockcify generation