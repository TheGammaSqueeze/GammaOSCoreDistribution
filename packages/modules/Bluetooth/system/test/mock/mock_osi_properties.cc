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
 *   Functions generated:4
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>
#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_properties.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_properties {

// Function state capture and return values, if needed
struct osi_property_get osi_property_get;
struct osi_property_get_bool osi_property_get_bool;
struct osi_property_get_int32 osi_property_get_int32;
struct osi_property_set osi_property_set;

}  // namespace osi_properties
}  // namespace mock
}  // namespace test

// Mocked functions, if any
int osi_property_get(const char* key, char* value, const char* default_value) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_properties::osi_property_get(key, value,
                                                      default_value);
}
bool osi_property_get_bool(const char* key, bool default_value) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_properties::osi_property_get_bool(key, default_value);
}
int32_t osi_property_get_int32(const char* key, int32_t default_value) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_properties::osi_property_get_int32(key, default_value);
}
int osi_property_set(const char* key, const char* value) {
  mock_function_count_map[__func__]++;
  return test::mock::osi_properties::osi_property_set(key, value);
}
// Mocked functions complete
// END mockcify generation
