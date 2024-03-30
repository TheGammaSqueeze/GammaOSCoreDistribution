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
 *   Functions generated:10
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
#include <string.h>

#include <cstring>

#include "bt_target.h"
#include "bta/hh/bta_hh_int.h"
#include "btif/include/btif_storage.h"
#include "osi/include/osi.h"
#include "stack/include/acl_api.h"
#include "stack/include/btm_client_interface.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace bta_hh_utils {

// Shared state between mocked functions and tests
// Name: bta_hh_add_device_to_list
// Params: tBTA_HH_DEV_CB* p_cb, uint8_t handle, uint16_t attr_mask, const
// tHID_DEV_DSCP_INFO* p_dscp_info, uint8_t sub_class, uint16_t ssr_max_latency,
// uint16_t ssr_min_tout, uint8_t app_id Return: void
struct bta_hh_add_device_to_list {
  std::function<void(tBTA_HH_DEV_CB* p_cb, uint8_t handle, uint16_t attr_mask,
                     const tHID_DEV_DSCP_INFO* p_dscp_info, uint8_t sub_class,
                     uint16_t ssr_max_latency, uint16_t ssr_min_tout,
                     uint8_t app_id)>
      body{[](tBTA_HH_DEV_CB* p_cb, uint8_t handle, uint16_t attr_mask,
              const tHID_DEV_DSCP_INFO* p_dscp_info, uint8_t sub_class,
              uint16_t ssr_max_latency, uint16_t ssr_min_tout,
              uint8_t app_id) {}};
  void operator()(tBTA_HH_DEV_CB* p_cb, uint8_t handle, uint16_t attr_mask,
                  const tHID_DEV_DSCP_INFO* p_dscp_info, uint8_t sub_class,
                  uint16_t ssr_max_latency, uint16_t ssr_min_tout,
                  uint8_t app_id) {
    body(p_cb, handle, attr_mask, p_dscp_info, sub_class, ssr_max_latency,
         ssr_min_tout, app_id);
  };
};
extern struct bta_hh_add_device_to_list bta_hh_add_device_to_list;

// Name: bta_hh_clean_up_kdev
// Params: tBTA_HH_DEV_CB* p_cb
// Return: void
struct bta_hh_clean_up_kdev {
  std::function<void(tBTA_HH_DEV_CB* p_cb)> body{[](tBTA_HH_DEV_CB* p_cb) {}};
  void operator()(tBTA_HH_DEV_CB* p_cb) { body(p_cb); };
};
extern struct bta_hh_clean_up_kdev bta_hh_clean_up_kdev;

// Name: bta_hh_cleanup_disable
// Params: tBTA_HH_STATUS status
// Return: void
struct bta_hh_cleanup_disable {
  std::function<void(tBTA_HH_STATUS status)> body{[](tBTA_HH_STATUS status) {}};
  void operator()(tBTA_HH_STATUS status) { body(status); };
};
extern struct bta_hh_cleanup_disable bta_hh_cleanup_disable;

// Name: bta_hh_dev_handle_to_cb_idx
// Params: uint8_t dev_handle
// Return: uint8_t
struct bta_hh_dev_handle_to_cb_idx {
  uint8_t return_value{0};
  std::function<uint8_t(uint8_t dev_handle)> body{
      [this](uint8_t dev_handle) { return return_value; }};
  uint8_t operator()(uint8_t dev_handle) { return body(dev_handle); };
};
extern struct bta_hh_dev_handle_to_cb_idx bta_hh_dev_handle_to_cb_idx;

// Name: bta_hh_find_cb
// Params: const RawAddress& bda
// Return: uint8_t
struct bta_hh_find_cb {
  uint8_t return_value{0};
  std::function<uint8_t(const RawAddress& bda)> body{
      [this](const RawAddress& bda) { return return_value; }};
  uint8_t operator()(const RawAddress& bda) { return body(bda); };
};
extern struct bta_hh_find_cb bta_hh_find_cb;

// Name: bta_hh_get_cb
// Params: const RawAddress& bda
// Return: tBTA_HH_DEV_CB*
struct bta_hh_get_cb {
  tBTA_HH_DEV_CB* return_value{0};
  std::function<tBTA_HH_DEV_CB*(const RawAddress& bda)> body{
      [this](const RawAddress& bda) { return return_value; }};
  tBTA_HH_DEV_CB* operator()(const RawAddress& bda) { return body(bda); };
};
extern struct bta_hh_get_cb bta_hh_get_cb;

// Name: bta_hh_read_ssr_param
// Params: const RawAddress& bd_addr, uint16_t* p_max_ssr_lat, uint16_t*
// p_min_ssr_tout Return: tBTA_HH_STATUS
struct bta_hh_read_ssr_param {
  tBTA_HH_STATUS return_value{0};
  std::function<tBTA_HH_STATUS(const RawAddress& bd_addr,
                               uint16_t* p_max_ssr_lat,
                               uint16_t* p_min_ssr_tout)>
      body{[this](const RawAddress& bd_addr, uint16_t* p_max_ssr_lat,
                  uint16_t* p_min_ssr_tout) { return return_value; }};
  tBTA_HH_STATUS operator()(const RawAddress& bd_addr, uint16_t* p_max_ssr_lat,
                            uint16_t* p_min_ssr_tout) {
    return body(bd_addr, p_max_ssr_lat, p_min_ssr_tout);
  };
};
extern struct bta_hh_read_ssr_param bta_hh_read_ssr_param;

// Name: bta_hh_tod_spt
// Params: tBTA_HH_DEV_CB* p_cb, uint8_t sub_class
// Return: bool
struct bta_hh_tod_spt {
  bool return_value{false};
  std::function<bool(tBTA_HH_DEV_CB* p_cb, uint8_t sub_class)> body{
      [this](tBTA_HH_DEV_CB* p_cb, uint8_t sub_class) { return return_value; }};
  bool operator()(tBTA_HH_DEV_CB* p_cb, uint8_t sub_class) {
    return body(p_cb, sub_class);
  };
};
extern struct bta_hh_tod_spt bta_hh_tod_spt;

// Name: bta_hh_trace_dev_db
// Params: void
// Return: void
struct bta_hh_trace_dev_db {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct bta_hh_trace_dev_db bta_hh_trace_dev_db;

// Name: bta_hh_update_di_info
// Params: tBTA_HH_DEV_CB* p_cb, uint16_t vendor_id, uint16_t product_id,
// uint16_t version, uint8_t flag Return: void
struct bta_hh_update_di_info {
  std::function<void(tBTA_HH_DEV_CB* p_cb, uint16_t vendor_id,
                     uint16_t product_id, uint16_t version, uint8_t flag)>
      body{[](tBTA_HH_DEV_CB* p_cb, uint16_t vendor_id, uint16_t product_id,
              uint16_t version, uint8_t flag) {}};
  void operator()(tBTA_HH_DEV_CB* p_cb, uint16_t vendor_id, uint16_t product_id,
                  uint16_t version, uint8_t flag) {
    body(p_cb, vendor_id, product_id, version, flag);
  };
};
extern struct bta_hh_update_di_info bta_hh_update_di_info;

}  // namespace bta_hh_utils
}  // namespace mock
}  // namespace test

// END mockcify generation
