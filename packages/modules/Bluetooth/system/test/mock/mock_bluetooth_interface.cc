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

#include <cstdint>

#include "btif/include/stack_manager.h"
#include "device/include/interop.h"
#include "hardware/bluetooth.h"
#include "stack/include/bt_octets.h"
#include "types/raw_address.h"

void set_hal_cbacks(bt_callbacks_t* callbacks) {}

static int init(bt_callbacks_t* callbacks, bool start_restricted,
                bool is_common_criteria_mode, int config_compare_result,
                const char** init_flags, bool is_atv) {
  return BT_STATUS_SUCCESS;
}

static int enable() { return BT_STATUS_SUCCESS; }

static int disable(void) { return BT_STATUS_SUCCESS; }

static void cleanup(void) {}

bool is_restricted_mode() { return false; }
bool is_common_criteria_mode() { return false; }

int get_common_criteria_config_compare_result() { return BT_STATUS_SUCCESS; }

bool is_atv_device() { return false; }

static int get_adapter_properties(void) { return BT_STATUS_SUCCESS; }

static int get_adapter_property(bt_property_type_t type) {
  return BT_STATUS_SUCCESS;
}

static int set_adapter_property(const bt_property_t* property) {
  return BT_STATUS_SUCCESS;
}

int get_remote_device_properties(RawAddress* remote_addr) {
  return BT_STATUS_SUCCESS;
}

int get_remote_device_property(RawAddress* remote_addr,
                               bt_property_type_t type) {
  return BT_STATUS_SUCCESS;
}

int set_remote_device_property(RawAddress* remote_addr,
                               const bt_property_t* property) {
  return BT_STATUS_SUCCESS;
}

int get_remote_services(RawAddress* remote_addr, int transport) {
  return BT_STATUS_SUCCESS;
}

static int start_discovery(void) { return BT_STATUS_SUCCESS; }

static int cancel_discovery(void) { return BT_STATUS_SUCCESS; }

static int create_bond(const RawAddress* bd_addr, int transport) {
  return BT_STATUS_SUCCESS;
}

static int create_bond_out_of_band(const RawAddress* bd_addr, int transport,
                                   const bt_oob_data_t* p192_data,
                                   const bt_oob_data_t* p256_data) {
  return BT_STATUS_SUCCESS;
}

static int generate_local_oob_data(tBT_TRANSPORT transport) {
  return BT_STATUS_SUCCESS;
}

static int cancel_bond(const RawAddress* bd_addr) { return BT_STATUS_SUCCESS; }

static int remove_bond(const RawAddress* bd_addr) { return BT_STATUS_SUCCESS; }

static int get_connection_state(const RawAddress* bd_addr) {
  return BT_STATUS_SUCCESS;
}

static int pin_reply(const RawAddress* bd_addr, uint8_t accept, uint8_t pin_len,
                     bt_pin_code_t* pin_code) {
  return BT_STATUS_SUCCESS;
}

static int ssp_reply(const RawAddress* bd_addr, bt_ssp_variant_t variant,
                     uint8_t accept, uint32_t passkey) {
  return BT_STATUS_SUCCESS;
}

static int read_energy_info() { return BT_STATUS_SUCCESS; }

static void dump(int fd, const char** arguments) {}

static void dumpMetrics(std::string* output) {}

static const void* get_profile_interface(const char* profile_id) {
  return nullptr;
}

int dut_mode_configure(uint8_t enable) { return BT_STATUS_SUCCESS; }

int dut_mode_send(uint16_t opcode, uint8_t* buf, uint8_t len) {
  return BT_STATUS_SUCCESS;
}

int le_test_mode(uint16_t opcode, uint8_t* buf, uint8_t len) {
  return BT_STATUS_SUCCESS;
}

static int set_os_callouts(bt_os_callouts_t* callouts) {
  return BT_STATUS_SUCCESS;
}

static int config_clear(void) { return 0; }

static bluetooth::avrcp::ServiceInterface* get_avrcp_service(void) {
  return nullptr;
}

static std::string obfuscate_address(const RawAddress& address) {
  return std::string("Test");
}

static int get_metric_id(const RawAddress& address) { return 0; }

static int set_dynamic_audio_buffer_size(int codec, int size) { return 0; }

static bool allow_low_latency_audio(bool allowed, const RawAddress& address) {
  return true;
}

static int clear_event_filter(void) { return 0; }

static void metadata_changed(const RawAddress& remote_bd_addr, int key,
                             std::vector<uint8_t> value) {}

EXPORT_SYMBOL bt_interface_t bluetoothInterface = {
    sizeof(bluetoothInterface),
    init,
    enable,
    disable,
    cleanup,
    get_adapter_properties,
    get_adapter_property,
    set_adapter_property,
    get_remote_device_properties,
    get_remote_device_property,
    set_remote_device_property,
    nullptr,
    get_remote_services,
    start_discovery,
    cancel_discovery,
    create_bond,
    create_bond_out_of_band,
    remove_bond,
    cancel_bond,
    get_connection_state,
    pin_reply,
    ssp_reply,
    get_profile_interface,
    dut_mode_configure,
    dut_mode_send,
    le_test_mode,
    set_os_callouts,
    read_energy_info,
    dump,
    dumpMetrics,
    config_clear,
    interop_database_clear,
    interop_database_add,
    get_avrcp_service,
    obfuscate_address,
    get_metric_id,
    set_dynamic_audio_buffer_size,
    generate_local_oob_data,
    allow_low_latency_audio,
    clear_event_filter,
    metadata_changed};

// callback reporting helpers

bt_property_t* property_deep_copy_array(int num_properties,
                                        bt_property_t* properties) {
  return nullptr;
}

void invoke_adapter_state_changed_cb(bt_state_t state) {}

void invoke_adapter_properties_cb(bt_status_t status, int num_properties,
                                  bt_property_t* properties) {}

void invoke_remote_device_properties_cb(bt_status_t status, RawAddress bd_addr,
                                        int num_properties,
                                        bt_property_t* properties) {}

void invoke_device_found_cb(int num_properties, bt_property_t* properties) {}

void invoke_discovery_state_changed_cb(bt_discovery_state_t state) {}

void invoke_pin_request_cb(RawAddress bd_addr, bt_bdname_t bd_name,
                           uint32_t cod, bool min_16_digit) {}

void invoke_ssp_request_cb(RawAddress bd_addr, bt_bdname_t bd_name,
                           uint32_t cod, bt_ssp_variant_t pairing_variant,
                           uint32_t pass_key) {}

void invoke_oob_data_request_cb(tBT_TRANSPORT t, bool valid, Octet16 c,
                                Octet16 r, RawAddress raw_address,
                                uint8_t address_type) {}

void invoke_bond_state_changed_cb(bt_status_t status, RawAddress bd_addr,
                                  bt_bond_state_t state, int fail_reason) {}

void invoke_address_consolidate_cb(RawAddress main_bd_addr,
                                   RawAddress secondary_bd_addr) {}

void invoke_le_address_associate_cb(RawAddress main_bd_addr,
                                    RawAddress secondary_bd_addr) {}

void invoke_acl_state_changed_cb(bt_status_t status, RawAddress bd_addr,
                                 bt_acl_state_t state, int transport_link_type,
                                 bt_hci_error_code_t hci_reason) {}

void invoke_thread_evt_cb(bt_cb_thread_evt event) {}

void invoke_le_test_mode_cb(bt_status_t status, uint16_t count) {}

// takes ownership of |uid_data|
void invoke_energy_info_cb(bt_activity_energy_info energy_info,
                           bt_uid_traffic_t* uid_data) {}

void invoke_link_quality_report_cb(uint64_t timestamp, int report_id, int rssi,
                                   int snr, int retransmission_count,
                                   int packets_not_receive_count,
                                   int negative_acknowledgement_count) {}
