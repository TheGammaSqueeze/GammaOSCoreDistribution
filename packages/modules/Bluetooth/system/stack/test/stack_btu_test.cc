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

#include <gtest/gtest.h>

#include <cstdint>
#include <map>
#include <string>

#include "stack/include/btu.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hcidefs.h"

std::map<std::string, int> mock_function_count_map;

/* Function for test provided by btu_hcif.cc */
void btu_hcif_hdl_command_status(uint16_t opcode, uint8_t status,
                                 const uint8_t* p_cmd,
                                 void* p_vsc_status_cback);

void LogMsg(uint32_t trace_set_mask, const char* fmt_str, ...) {}

class StackBtuTest : public ::testing::Test {
 protected:
  void SetUp() override { mock_function_count_map.clear(); }
};

TEST_F(StackBtuTest, post_on_main) {}

TEST_F(StackBtuTest, btm_sco_connection_failed_called) {
  uint8_t p_cmd[10];  // garbage data for testing
  bluetooth::legacy::testing::btu_hcif_hdl_command_status(
      HCI_SETUP_ESCO_CONNECTION, HCI_ERR_UNSPECIFIED, p_cmd, nullptr);
  ASSERT_EQ(1, mock_function_count_map["btm_sco_connection_failed"]);
}
