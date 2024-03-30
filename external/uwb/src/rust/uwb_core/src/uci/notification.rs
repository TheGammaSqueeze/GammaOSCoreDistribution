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

use std::convert::{TryFrom, TryInto};
use std::iter::zip;

use num_traits::ToPrimitive;
use uwb_uci_packets::Packet;

use crate::uci::error::{Error, Result as UciResult, StatusCode};
use crate::uci::params::{
    ControleeStatus, DeviceState, ExtendedAddressTwoWayRangingMeasurement, RangingMeasurementType,
    RawVendorMessage, ReasonCode, SessionId, SessionState, ShortAddressTwoWayRangingMeasurement,
};

#[derive(Debug, Clone)]
pub(crate) enum UciNotification {
    Core(CoreNotification),
    Session(SessionNotification),
    Vendor(RawVendorMessage),
}

#[derive(Debug, Clone)]
pub(crate) enum CoreNotification {
    DeviceStatus(DeviceState),
    GenericError(StatusCode),
}

#[derive(Debug, Clone)]
pub(crate) enum SessionNotification {
    Status {
        session_id: SessionId,
        session_state: SessionState,
        reason_code: ReasonCode,
    },
    UpdateControllerMulticastList {
        session_id: SessionId,
        remaining_multicast_list_size: usize,
        status_list: Vec<ControleeStatus>,
    },
    RangeData(SessionRangeData),
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct SessionRangeData {
    pub sequence_number: u32,
    pub session_id: SessionId,
    pub current_ranging_interval_ms: u32,
    pub ranging_measurement_type: RangingMeasurementType,
    pub ranging_measurements: RangingMeasurements,
}

#[derive(Debug, Clone)]
pub(crate) enum RangingMeasurements {
    Short(Vec<ShortAddressTwoWayRangingMeasurement>),
    Extended(Vec<ExtendedAddressTwoWayRangingMeasurement>),
}

impl PartialEq for RangingMeasurements {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Self::Short(a_vec), Self::Short(b_vec)) => {
                a_vec.len() == b_vec.len()
                    && zip(a_vec, b_vec)
                        .all(|(a, b)| short_address_two_way_ranging_measurement_eq(a, b))
            }
            (Self::Extended(a_vec), Self::Extended(b_vec)) => {
                a_vec.len() == b_vec.len()
                    && zip(a_vec, b_vec)
                        .all(|(a, b)| extended_address_two_way_ranging_measurement_eq(a, b))
            }
            _ => false,
        }
    }
}

fn short_address_two_way_ranging_measurement_eq(
    a: &ShortAddressTwoWayRangingMeasurement,
    b: &ShortAddressTwoWayRangingMeasurement,
) -> bool {
    a.mac_address == b.mac_address
        && a.status == b.status
        && a.nlos == b.nlos
        && a.distance == b.distance
        && a.aoa_azimuth == b.aoa_azimuth
        && a.aoa_azimuth_fom == b.aoa_azimuth_fom
        && a.aoa_elevation == b.aoa_elevation
        && a.aoa_elevation_fom == b.aoa_elevation_fom
        && a.aoa_destination_azimuth == b.aoa_destination_azimuth
        && a.aoa_destination_azimuth_fom == b.aoa_destination_azimuth_fom
        && a.aoa_destination_elevation == b.aoa_destination_elevation
        && a.aoa_destination_elevation_fom == b.aoa_destination_elevation_fom
        && a.slot_index == b.slot_index
}

fn extended_address_two_way_ranging_measurement_eq(
    a: &ExtendedAddressTwoWayRangingMeasurement,
    b: &ExtendedAddressTwoWayRangingMeasurement,
) -> bool {
    a.mac_address == b.mac_address
        && a.status == b.status
        && a.nlos == b.nlos
        && a.distance == b.distance
        && a.aoa_azimuth == b.aoa_azimuth
        && a.aoa_azimuth_fom == b.aoa_azimuth_fom
        && a.aoa_elevation == b.aoa_elevation
        && a.aoa_elevation_fom == b.aoa_elevation_fom
        && a.aoa_destination_azimuth == b.aoa_destination_azimuth
        && a.aoa_destination_azimuth_fom == b.aoa_destination_azimuth_fom
        && a.aoa_destination_elevation == b.aoa_destination_elevation
        && a.aoa_destination_elevation_fom == b.aoa_destination_elevation_fom
        && a.slot_index == b.slot_index
}

impl UciNotification {
    pub fn need_retry(&self) -> bool {
        matches!(
            self,
            Self::Core(CoreNotification::GenericError(StatusCode::UciStatusCommandRetry))
        )
    }
}

impl TryFrom<uwb_uci_packets::UciNotificationPacket> for UciNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::UciNotificationPacket) -> Result<Self, Self::Error> {
        use uwb_uci_packets::UciNotificationChild;
        match evt.specialize() {
            UciNotificationChild::CoreNotification(evt) => Ok(Self::Core(evt.try_into()?)),
            UciNotificationChild::SessionNotification(evt) => Ok(Self::Session(evt.try_into()?)),
            UciNotificationChild::RangingNotification(evt) => Ok(Self::Session(evt.try_into()?)),
            UciNotificationChild::AndroidNotification(evt) => evt.try_into(),
            UciNotificationChild::UciVendor_9_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_A_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_B_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_E_Notification(evt) => vendor_notification(evt.into()),
            UciNotificationChild::UciVendor_F_Notification(evt) => vendor_notification(evt.into()),
            _ => Err(Error::Specialize(evt.to_vec())),
        }
    }
}

impl TryFrom<uwb_uci_packets::CoreNotificationPacket> for CoreNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::CoreNotificationPacket) -> Result<Self, Self::Error> {
        use uwb_uci_packets::CoreNotificationChild;
        match evt.specialize() {
            CoreNotificationChild::DeviceStatusNtf(evt) => {
                Ok(Self::DeviceStatus(evt.get_device_state()))
            }
            CoreNotificationChild::GenericError(evt) => Ok(Self::GenericError(evt.get_status())),
            _ => Err(Error::Specialize(evt.to_vec())),
        }
    }
}

impl TryFrom<uwb_uci_packets::SessionNotificationPacket> for SessionNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::SessionNotificationPacket) -> Result<Self, Self::Error> {
        use uwb_uci_packets::SessionNotificationChild;
        match evt.specialize() {
            SessionNotificationChild::SessionStatusNtf(evt) => Ok(Self::Status {
                session_id: evt.get_session_id(),
                session_state: evt.get_session_state(),
                reason_code: evt.get_reason_code(),
            }),
            SessionNotificationChild::SessionUpdateControllerMulticastListNtf(evt) => {
                Ok(Self::UpdateControllerMulticastList {
                    session_id: evt.get_session_id(),
                    remaining_multicast_list_size: evt.get_remaining_multicast_list_size() as usize,
                    status_list: evt.get_controlee_status().clone(),
                })
            }
            _ => Err(Error::Specialize(evt.to_vec())),
        }
    }
}

impl TryFrom<uwb_uci_packets::RangingNotificationPacket> for SessionNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::RangingNotificationPacket) -> Result<Self, Self::Error> {
        use uwb_uci_packets::RangingNotificationChild;
        match evt.specialize() {
            RangingNotificationChild::RangeDataNtf(evt) => evt.try_into(),
            _ => Err(Error::Specialize(evt.to_vec())),
        }
    }
}

impl TryFrom<uwb_uci_packets::RangeDataNtfPacket> for SessionNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::RangeDataNtfPacket) -> Result<Self, Self::Error> {
        use uwb_uci_packets::RangeDataNtfChild;
        let ranging_measurements = match evt.specialize() {
            RangeDataNtfChild::ShortMacTwoWayRangeDataNtf(evt) => {
                RangingMeasurements::Short(evt.get_two_way_ranging_measurements().clone())
            }
            RangeDataNtfChild::ExtendedMacTwoWayRangeDataNtf(evt) => {
                RangingMeasurements::Extended(evt.get_two_way_ranging_measurements().clone())
            }
            _ => return Err(Error::Specialize(evt.to_vec())),
        };
        Ok(Self::RangeData(SessionRangeData {
            sequence_number: evt.get_sequence_number(),
            session_id: evt.get_session_id(),
            current_ranging_interval_ms: evt.get_current_ranging_interval(),
            ranging_measurement_type: evt.get_ranging_measurement_type(),
            ranging_measurements,
        }))
    }
}

impl TryFrom<uwb_uci_packets::AndroidNotificationPacket> for UciNotification {
    type Error = Error;
    fn try_from(evt: uwb_uci_packets::AndroidNotificationPacket) -> Result<Self, Self::Error> {
        Err(Error::Specialize(evt.to_vec()))
    }
}

fn vendor_notification(evt: uwb_uci_packets::UciNotificationPacket) -> UciResult<UciNotification> {
    Ok(UciNotification::Vendor(RawVendorMessage {
        gid: evt.get_group_id().to_u32().ok_or_else(|| Error::Specialize(evt.clone().to_vec()))?,
        oid: evt.get_opcode().to_u32().ok_or_else(|| Error::Specialize(evt.clone().to_vec()))?,
        payload: get_vendor_uci_payload(evt)?,
    }))
}

fn get_vendor_uci_payload(evt: uwb_uci_packets::UciNotificationPacket) -> UciResult<Vec<u8>> {
    match evt.specialize() {
        uwb_uci_packets::UciNotificationChild::UciVendor_9_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_9_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_9_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_A_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_A_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_A_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_B_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_B_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_B_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_E_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_E_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_E_NotificationChild::None => Ok(Vec::new()),
            }
        }
        uwb_uci_packets::UciNotificationChild::UciVendor_F_Notification(evt) => {
            match evt.specialize() {
                uwb_uci_packets::UciVendor_F_NotificationChild::Payload(payload) => {
                    Ok(payload.to_vec())
                }
                uwb_uci_packets::UciVendor_F_NotificationChild::None => Ok(Vec::new()),
            }
        }
        _ => Err(Error::Specialize(evt.to_vec())),
    }
}
