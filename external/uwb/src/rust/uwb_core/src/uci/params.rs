// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Define the structs and enums for the parameters or responses of the UciManager's methods.
//! Most of them are re-exported uwb_uci_packets's structure.

#![allow(clippy::eq_op)]

use std::collections::{hash_map::RandomState, HashMap};
use std::iter::{zip, FromIterator};

use crate::uci::error::StatusCode;

// Re-export enums and structs from uwb_uci_packets.
pub use uwb_uci_packets::{
    AppConfigStatus, AppConfigTlv, AppConfigTlvType, CapTlv, CapTlvType, Controlee,
    ControleeStatus, DeviceConfigId, DeviceConfigStatus, DeviceConfigTlv, DeviceState,
    ExtendedAddressTwoWayRangingMeasurement, MulticastUpdateStatusCode, PowerStats,
    RangingMeasurementType, ReasonCode, ResetConfig, SessionState, SessionType,
    ShortAddressTwoWayRangingMeasurement, UpdateMulticastListAction,
};

pub type SessionId = u32;
pub type SubSessionId = u32;

// Workaround: uwb_uci_packets's struct doesn't derive PartialEq trait.
// Implement the eq functions for each struct instead.
pub fn app_config_status_eq(a: &AppConfigStatus, b: &AppConfigStatus) -> bool {
    a.cfg_id == a.cfg_id && a.status == b.status
}

pub fn device_config_status_eq(a: &DeviceConfigStatus, b: &DeviceConfigStatus) -> bool {
    a.cfg_id == b.cfg_id && a.status == b.status
}

pub fn power_stats_eq(a: &PowerStats, b: &PowerStats) -> bool {
    a.status == b.status
        && a.idle_time_ms == b.idle_time_ms
        && a.tx_time_ms == b.tx_time_ms
        && a.rx_time_ms == b.rx_time_ms
        && a.total_wake_count == b.total_wake_count
}

pub fn cap_tlv_eq(a: &CapTlv, b: &CapTlv) -> bool {
    a.t == b.t && a.v == b.v
}

pub fn app_config_tlvs_eq(a: &[AppConfigTlv], b: &[AppConfigTlv]) -> bool {
    app_config_tlvs_to_map(a) == app_config_tlvs_to_map(b)
}

fn app_config_tlvs_to_map(
    tlvs: &[AppConfigTlv],
) -> HashMap<AppConfigTlvType, &Vec<u8>, RandomState> {
    HashMap::from_iter(tlvs.iter().map(|config| (config.cfg_id, &config.v)))
}

pub fn device_config_tlvs_eq(a: &[DeviceConfigTlv], b: &[DeviceConfigTlv]) -> bool {
    device_config_tlvs_to_map(a) == device_config_tlvs_to_map(b)
}

fn device_config_tlvs_to_map(
    tlvs: &[DeviceConfigTlv],
) -> HashMap<DeviceConfigId, &Vec<u8>, RandomState> {
    HashMap::from_iter(tlvs.iter().map(|config| (config.cfg_id, &config.v)))
}

#[derive(Debug, Clone)]
pub struct CoreSetConfigResponse {
    pub status: StatusCode,
    pub config_status: Vec<DeviceConfigStatus>,
}

impl PartialEq for CoreSetConfigResponse {
    fn eq(&self, other: &Self) -> bool {
        self.status == other.status
            && zip(&self.config_status, &other.config_status)
                .all(|(a, b)| device_config_status_eq(a, b))
    }
}

#[derive(Debug, Clone)]
pub struct SetAppConfigResponse {
    pub status: StatusCode,
    pub config_status: Vec<AppConfigStatus>,
}

impl PartialEq for SetAppConfigResponse {
    fn eq(&self, other: &Self) -> bool {
        self.status == other.status
            && zip(&self.config_status, &other.config_status)
                .all(|(a, b)| app_config_status_eq(a, b))
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CountryCode([u8; 2]);

impl CountryCode {
    pub fn new(code: &[u8; 2]) -> Option<Self> {
        if !code[0].is_ascii_uppercase() || !code[1].is_ascii_uppercase() {
            None
        } else {
            Some(Self(*code))
        }
    }
}

impl From<CountryCode> for [u8; 2] {
    fn from(item: CountryCode) -> [u8; 2] {
        item.0
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GetDeviceInfoResponse {
    pub uci_version: u16,
    pub mac_version: u16,
    pub phy_version: u16,
    pub uci_test_version: u16,
    pub vendor_spec_info: Vec<u8>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RawVendorMessage {
    pub gid: u32,
    pub oid: u32,
    pub payload: Vec<u8>,
}
