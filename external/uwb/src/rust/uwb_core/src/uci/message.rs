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

use uwb_uci_packets::Packet;

use crate::uci::error::Error;
use crate::uci::notification::UciNotification;
use crate::uci::response::UciResponse;

#[derive(Debug)]
pub(super) enum UciMessage {
    Response(UciResponse),
    Notification(UciNotification),
}

impl TryFrom<uwb_uci_packets::UciPacketPacket> for UciMessage {
    type Error = Error;
    fn try_from(packet: uwb_uci_packets::UciPacketPacket) -> Result<Self, Self::Error> {
        match packet.specialize() {
            uwb_uci_packets::UciPacketChild::UciResponse(evt) => {
                Ok(UciMessage::Response(evt.try_into()?))
            }
            uwb_uci_packets::UciPacketChild::UciNotification(evt) => {
                Ok(UciMessage::Notification(evt.try_into()?))
            }
            _ => Err(Error::Specialize(packet.to_vec())),
        }
    }
}
