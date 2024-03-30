/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "gd/rust/topshim/gatt/gatt_ble_scanner_shim.h"

#include <base/bind.h>
#include <base/callback.h>

#include <algorithm>
#include <iterator>
#include <memory>
#include <vector>

#include "bind_helpers.h"
#include "gd/rust/topshim/common/utils.h"
#include "include/hardware/bt_common_types.h"
#include "rust/cxx.h"
#include "src/profiles/gatt.rs.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

namespace rusty = ::bluetooth::topshim::rust;

namespace internal {
ApcfCommand ConvertApcfFromRust(const RustApcfCommand& command) {
  RawAddress address = rusty::CopyFromRustAddress(command.address);

  // Copy vectors + arrays
  std::vector<uint8_t> name, data, data_mask;
  std::array<uint8_t, 16> irk;
  std::copy(command.name.begin(), command.name.end(), std::back_inserter(name));
  std::copy(command.data.begin(), command.data.end(), std::back_inserter(data));
  std::copy(command.data_mask.begin(), command.data_mask.end(), std::back_inserter(data_mask));
  std::copy(command.irk.begin(), command.irk.end(), std::begin(irk));

  ApcfCommand converted = {
      .type = command.type_,
      .address = address,
      .addr_type = command.addr_type,
      .uuid = bluetooth::Uuid::From128BitBE(command.uuid.uu),
      .uuid_mask = bluetooth::Uuid::From128BitBE(command.uuid_mask.uu),
      .name = name,
      .company = command.company,
      .company_mask = command.company_mask,
      .ad_type = command.ad_type,
      .data = data,
      .data_mask = data_mask,
      .irk = irk,
  };

  return converted;
}

std::vector<ApcfCommand> ConvertApcfVec(const ::rust::Vec<RustApcfCommand>& rustvec) {
  std::vector<ApcfCommand> converted;

  for (const RustApcfCommand& command : rustvec) {
    converted.push_back(ConvertApcfFromRust(command));
  }

  return converted;
}

::btgatt_filt_param_setup_t ConvertRustFilterParam(const RustGattFilterParam& param) {
  ::btgatt_filt_param_setup_t converted = {
      .feat_seln = param.feat_seln,
      .list_logic_type = param.list_logic_type,
      .filt_logic_type = param.filt_logic_type,
      .rssi_high_thres = param.rssi_high_thres,
      .rssi_low_thres = param.rssi_low_thres,
      .dely_mode = param.delay_mode,
      .found_timeout = param.found_timeout,
      .lost_timeout = param.lost_timeout,
      .found_timeout_cnt = param.found_timeout_count,
      .num_of_tracking_entries = param.num_of_tracking_entries,
  };

  return converted;
}
}  // namespace internal

// ScanningCallbacks implementations

void BleScannerIntf::OnScannerRegistered(const bluetooth::Uuid app_uuid, uint8_t scannerId, uint8_t status) {
  rusty::gdscan_on_scanner_registered(reinterpret_cast<const signed char*>(&app_uuid), scannerId, status);
}

void BleScannerIntf::OnSetScannerParameterComplete(uint8_t scannerId, uint8_t status) {
  rusty::gdscan_on_set_scanner_parameter_complete(scannerId, status);
}

void BleScannerIntf::OnScanResult(
    uint16_t event_type,
    uint8_t addr_type,
    RawAddress bda,
    uint8_t primary_phy,
    uint8_t secondary_phy,
    uint8_t advertising_sid,
    int8_t tx_power,
    int8_t rssi,
    uint16_t periodic_adv_int,
    std::vector<uint8_t> adv_data) {
  RustRawAddress raw_address = rusty::CopyToRustAddress(bda);
  rusty::gdscan_on_scan_result(
      event_type,
      addr_type,
      reinterpret_cast<const signed char*>(&raw_address),
      primary_phy,
      secondary_phy,
      advertising_sid,
      tx_power,
      rssi,
      periodic_adv_int,
      adv_data.data(),
      adv_data.size());
}

void BleScannerIntf::OnTrackAdvFoundLost(AdvertisingTrackInfo ati) {
  rusty::RustRawAddress addr = rusty::CopyToRustAddress(ati.advertiser_address);
  rusty::RustAdvertisingTrackInfo rust_info = {
      .scanner_id = ati.scanner_id,
      .filter_index = ati.filter_index,
      .advertiser_state = ati.advertiser_state,
      .advertiser_info_present = ati.advertiser_info_present,
      .advertiser_address = addr,
      .advertiser_address_type = ati.advertiser_address_type,
      .tx_power = ati.tx_power,
      .rssi = ati.rssi,
      .timestamp = ati.time_stamp,
      .adv_packet_len = ati.adv_packet_len,
      // .adv_packet is copied below
      .scan_response_len = ati.scan_response_len,
      // .scan_response is copied below
  };

  std::copy(rust_info.adv_packet.begin(), rust_info.adv_packet.end(), std::back_inserter(ati.adv_packet));
  std::copy(rust_info.scan_response.begin(), rust_info.scan_response.end(), std::back_inserter(ati.scan_response));

  rusty::gdscan_on_track_adv_found_lost(rust_info);
}

void BleScannerIntf::OnBatchScanReports(
    int client_if, int status, int report_format, int num_records, std::vector<uint8_t> data) {
  rusty::gdscan_on_batch_scan_reports(client_if, status, report_format, num_records, data.data(), data.size());
}

void BleScannerIntf::OnBatchScanThresholdCrossed(int client_if) {
  rusty::gdscan_on_batch_scan_threshold_crossed(client_if);
}

// BleScannerInterface implementations

void BleScannerIntf::RegisterScanner(RustUuid uuid) {
  bluetooth::Uuid converted = bluetooth::Uuid::From128BitBE(uuid.uu);
  scanner_intf_->RegisterScanner(
      converted, base::Bind(&BleScannerIntf::OnRegisterCallback, base::Unretained(this), uuid));
}

void BleScannerIntf::Unregister(uint8_t scanner_id) {
  scanner_intf_->Unregister(scanner_id);
}

void BleScannerIntf::Scan(bool start) {
  scanner_intf_->Scan(start);
}

void BleScannerIntf::ScanFilterParamSetup(
    uint8_t scanner_id, uint8_t action, uint8_t filter_index, RustGattFilterParam filter_param) {
  std::unique_ptr<::btgatt_filt_param_setup_t> converted =
      std::make_unique<::btgatt_filt_param_setup_t>(std::move(internal::ConvertRustFilterParam(filter_param)));

  scanner_intf_->ScanFilterParamSetup(
      scanner_id,
      action,
      filter_index,
      std::move(converted),
      base::Bind(&BleScannerIntf::OnFilterParamSetupCallback, base::Unretained(this), scanner_id));
}

void BleScannerIntf::ScanFilterAdd(uint8_t filter_index, ::rust::Vec<RustApcfCommand> filters) {
  auto converted = internal::ConvertApcfVec(filters);
  scanner_intf_->ScanFilterAdd(
      filter_index,
      converted,
      base::Bind(&BleScannerIntf::OnFilterConfigCallback, base::Unretained(this), filter_index));
}

void BleScannerIntf::ScanFilterClear(uint8_t filter_index) {
  scanner_intf_->ScanFilterClear(
      filter_index, base::Bind(&BleScannerIntf::OnFilterConfigCallback, base::Unretained(this), filter_index));
}

void BleScannerIntf::ScanFilterEnable(bool enable) {
  scanner_intf_->ScanFilterEnable(enable, base::Bind(&BleScannerIntf::OnEnableCallback, base::Unretained(this)));
}

void BleScannerIntf::SetScanParameters(uint8_t scanner_id, uint16_t scan_interval, uint16_t scan_window) {
  scanner_intf_->SetScanParameters(
      scanner_id,
      scan_interval,
      scan_window,
      base::Bind(&BleScannerIntf::OnStatusCallback, base::Unretained(this), scanner_id));
}

void BleScannerIntf::BatchscanConfigStorage(
    uint8_t scanner_id,
    int32_t batch_scan_full_max,
    int32_t batch_scan_trunc_max,
    int32_t batch_scan_notify_threshold) {
  scanner_intf_->BatchscanConfigStorage(
      scanner_id,
      batch_scan_full_max,
      batch_scan_trunc_max,
      batch_scan_notify_threshold,
      base::Bind(&BleScannerIntf::OnStatusCallback, base::Unretained(this), scanner_id));
}

void BleScannerIntf::BatchscanEnable(
    int32_t scan_mode, uint16_t scan_interval, uint16_t scan_window, int32_t addr_type, int32_t discard_rule) {
  scanner_intf_->BatchscanEnable(
      scan_mode,
      scan_interval,
      scan_window,
      addr_type,
      discard_rule,
      base::Bind(&BleScannerIntf::OnStatusCallback, base::Unretained(this), 0));
}

void BleScannerIntf::BatchscanDisable() {
  scanner_intf_->BatchscanDisable(base::Bind(&BleScannerIntf::OnStatusCallback, base::Unretained(this), 0));
}

void BleScannerIntf::BatchscanReadReports(uint8_t scanner_id, int32_t scan_mode) {
  scanner_intf_->BatchscanReadReports(scanner_id, scan_mode);
}

void BleScannerIntf::StartSync(uint8_t sid, RustRawAddress address, uint16_t skip, uint16_t timeout) {
  RawAddress converted = rusty::CopyFromRustAddress(address);
  scanner_intf_->StartSync(sid, converted, skip, timeout, 0 /* place holder */);
}

void BleScannerIntf::StopSync(uint16_t handle) {
  scanner_intf_->StopSync(handle);
}

void BleScannerIntf::CancelCreateSync(uint8_t sid, RustRawAddress address) {
  RawAddress converted = rusty::CopyFromRustAddress(address);
  scanner_intf_->CancelCreateSync(sid, converted);
}

void BleScannerIntf::TransferSync(RustRawAddress address, uint16_t service_data, uint16_t sync_handle) {
  RawAddress converted = rusty::CopyFromRustAddress(address);
  scanner_intf_->TransferSync(converted, service_data, sync_handle, 0 /* place holder */);
}

void BleScannerIntf::TransferSetInfo(RustRawAddress address, uint16_t service_data, uint8_t adv_handle) {
  RawAddress converted = rusty::CopyFromRustAddress(address);
  scanner_intf_->TransferSetInfo(converted, service_data, adv_handle, 0 /* place holder */);
}

void BleScannerIntf::SyncTxParameters(RustRawAddress address, uint8_t mode, uint16_t skip, uint16_t timeout) {
  RawAddress converted = rusty::CopyFromRustAddress(address);
  scanner_intf_->SyncTxParameters(converted, mode, skip, timeout, 0 /* place holder */);
}

void BleScannerIntf::OnRegisterCallback(RustUuid uuid, uint8_t scanner_id, uint8_t btm_status) {
  rusty::gdscan_register_callback(uuid, scanner_id, btm_status);
}

void BleScannerIntf::OnStatusCallback(uint8_t scanner_id, uint8_t btm_status) {
  rusty::gdscan_status_callback(scanner_id, btm_status);
}

void BleScannerIntf::OnEnableCallback(uint8_t action, uint8_t btm_status) {
  rusty::gdscan_enable_callback(action, btm_status);
}

void BleScannerIntf::OnFilterParamSetupCallback(
    uint8_t scanner_id, uint8_t avbl_space, uint8_t action_type, uint8_t btm_status) {
  rusty::gdscan_filter_param_setup_callback(scanner_id, avbl_space, action_type, btm_status);
}

void BleScannerIntf::OnFilterConfigCallback(
    uint8_t filter_index, uint8_t filt_type, uint8_t avbl_space, uint8_t action, uint8_t btm_status) {
  rusty::gdscan_filter_config_callback(filter_index, filt_type, avbl_space, action, btm_status);
}

void BleScannerIntf::OnPeriodicSyncStarted(
    int,
    uint8_t status,
    uint16_t sync_handle,
    uint8_t advertising_sid,
    uint8_t address_type,
    RawAddress address,
    uint8_t phy,
    uint16_t interval) {
  RustRawAddress converted = rusty::CopyToRustAddress(address);
  rusty::gdscan_start_sync_callback(status, sync_handle, advertising_sid, address_type, &converted, phy, interval);
}

void BleScannerIntf::OnPeriodicSyncReport(
    uint16_t sync_handle, int8_t tx_power, int8_t rssi, uint8_t status, std::vector<uint8_t> data) {
  rusty::gdscan_sync_report_callback(sync_handle, tx_power, rssi, status, data.data(), data.size());
}

void BleScannerIntf::OnPeriodicSyncLost(uint16_t sync_handle) {
  rusty::gdscan_sync_lost_callback(sync_handle);
}

void BleScannerIntf::OnPeriodicSyncTransferred(int, uint8_t status, RawAddress address) {
  RustRawAddress converted = rusty::CopyToRustAddress(address);
  rusty::gdscan_sync_transfer_callback(status, &converted);
}

void BleScannerIntf::RegisterCallbacks() {
  // Register self as a callback handler. We will dispatch to Rust callbacks.
  scanner_intf_->RegisterCallbacks(this);
}

// ScanningCallbacks overrides
std::unique_ptr<BleScannerIntf> GetBleScannerIntf(const unsigned char* gatt_intf) {
  return std::make_unique<BleScannerIntf>(reinterpret_cast<const btgatt_interface_t*>(gatt_intf)->scanner);
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
