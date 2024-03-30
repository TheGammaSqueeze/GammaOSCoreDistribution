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
 *   Functions generated:7
 *
 *  mockcify.pl ver 0.2.1
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
//       may need attention to prune the inclusion set.
#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_pan.h>
#include <string.h>

#include "bta/include/bta_pan_api.h"

// Mocked compile conditionals, if any
#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

namespace test {
namespace mock {
namespace btif_bta_pan_co_rx {

// Shared state between mocked functions and tests
// Name: bta_pan_co_init
// Params: uint8_t* q_level
// Returns: uint8_t
struct bta_pan_co_init {
  std::function<uint8_t(uint8_t* q_level)> body{
      [](uint8_t* q_level) { return 0; }};
  uint8_t operator()(uint8_t* q_level) { return body(q_level); };
};
extern struct bta_pan_co_init bta_pan_co_init;
// Name: bta_pan_co_close
// Params: uint16_t handle, uint8_t app_id
// Returns: void
struct bta_pan_co_close {
  std::function<void(uint16_t handle, uint8_t app_id)> body{
      [](uint16_t handle, uint8_t app_id) { ; }};
  void operator()(uint16_t handle, uint8_t app_id) { body(handle, app_id); };
};
extern struct bta_pan_co_close bta_pan_co_close;
// Name: bta_pan_co_mfilt_ind
// Params: UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication, UNUSED_ATTR
// tBTA_PAN_STATUS result, UNUSED_ATTR uint16_t len, UNUSED_ATTR uint8_t*
// p_filters Returns: void
struct bta_pan_co_mfilt_ind {
  std::function<void(UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication,
                     UNUSED_ATTR tBTA_PAN_STATUS result,
                     UNUSED_ATTR uint16_t len, UNUSED_ATTR uint8_t* p_filters)>
      body{[](UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication,
              UNUSED_ATTR tBTA_PAN_STATUS result, UNUSED_ATTR uint16_t len,
              UNUSED_ATTR uint8_t* p_filters) { ; }};
  void operator()(UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication,
                  UNUSED_ATTR tBTA_PAN_STATUS result, UNUSED_ATTR uint16_t len,
                  UNUSED_ATTR uint8_t* p_filters) {
    body(handle, indication, result, len, p_filters);
  };
};
extern struct bta_pan_co_mfilt_ind bta_pan_co_mfilt_ind;
// Name: bta_pan_co_pfilt_ind
// Params: UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication, UNUSED_ATTR
// tBTA_PAN_STATUS result, UNUSED_ATTR uint16_t len, UNUSED_ATTR uint8_t*
// p_filters Returns: void
struct bta_pan_co_pfilt_ind {
  std::function<void(UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication,
                     UNUSED_ATTR tBTA_PAN_STATUS result,
                     UNUSED_ATTR uint16_t len, UNUSED_ATTR uint8_t* p_filters)>
      body{[](UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication,
              UNUSED_ATTR tBTA_PAN_STATUS result, UNUSED_ATTR uint16_t len,
              UNUSED_ATTR uint8_t* p_filters) { ; }};
  void operator()(UNUSED_ATTR uint16_t handle, UNUSED_ATTR bool indication,
                  UNUSED_ATTR tBTA_PAN_STATUS result, UNUSED_ATTR uint16_t len,
                  UNUSED_ATTR uint8_t* p_filters) {
    body(handle, indication, result, len, p_filters);
  };
};
extern struct bta_pan_co_pfilt_ind bta_pan_co_pfilt_ind;
// Name: bta_pan_co_rx_flow
// Params: UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id, UNUSED_ATTR
// bool enable Returns: void
struct bta_pan_co_rx_flow {
  std::function<void(UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id,
                     UNUSED_ATTR bool enable)>
      body{[](UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id,
              UNUSED_ATTR bool enable) { ; }};
  void operator()(UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id,
                  UNUSED_ATTR bool enable) {
    body(handle, app_id, enable);
  };
};
extern struct bta_pan_co_rx_flow bta_pan_co_rx_flow;
// Name: bta_pan_co_rx_path
// Params: UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id
// Returns: void
struct bta_pan_co_rx_path {
  std::function<void(UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id)>
      body{[](UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id) { ; }};
  void operator()(UNUSED_ATTR uint16_t handle, UNUSED_ATTR uint8_t app_id) {
    body(handle, app_id);
  };
};
extern struct bta_pan_co_rx_path bta_pan_co_rx_path;
// Name: bta_pan_co_tx_path
// Params: uint16_t handle, uint8_t app_id
// Returns: void
struct bta_pan_co_tx_path {
  std::function<void(uint16_t handle, uint8_t app_id)> body{
      [](uint16_t handle, uint8_t app_id) { ; }};
  void operator()(uint16_t handle, uint8_t app_id) { body(handle, app_id); };
};
extern struct bta_pan_co_tx_path bta_pan_co_tx_path;

}  // namespace btif_bta_pan_co_rx
}  // namespace mock
}  // namespace test

// END mockcify generation
