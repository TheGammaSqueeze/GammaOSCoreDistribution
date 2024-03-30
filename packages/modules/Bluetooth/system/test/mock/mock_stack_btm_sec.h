/*
 * Copyright 2022 The Android Open Source Project
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

#include <map>
#include <string>

extern std::map<std::string, int> mock_function_count_map;

namespace test {
namespace mock {
namespace stack_btm_sec {

// Function state capture and return values, if needed
struct BTM_SetEncryption {
  std::function<tBTM_STATUS(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                            tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                            tBTM_BLE_SEC_ACT sec_act)>
      body{};
  tBTM_STATUS operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                         tBTM_SEC_CALLBACK* p_callback, void* p_ref_data,
                         tBTM_BLE_SEC_ACT sec_act) {
    return body(bd_addr, transport, p_callback, p_ref_data, sec_act);
  };
};
extern struct BTM_SetEncryption BTM_SetEncryption;

}  // namespace stack_btm_sec
}  // namespace mock
}  // namespace test
