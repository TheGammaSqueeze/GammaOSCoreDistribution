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

#![allow(clippy::all)]
#![allow(non_upper_case_globals)]
#![allow(non_camel_case_types)]
#![allow(non_snake_case)]
#![allow(unused)]
#![allow(missing_docs)]

use log::error;
use std::cmp;

include!(concat!(env!("OUT_DIR"), "/uci_packets.rs"));

const MAX_PAYLOAD_LEN: usize = 255;
// TODO: Use a PDL struct to represent the headers and avoid hardcoding
// lengths below.
// Real UCI packet header len.
const UCI_PACKET_HAL_HEADER_LEN: usize = 4;
// Unfragmented UCI packet header len.
const UCI_PACKET_HEADER_LEN: usize = 7;

// Container for UCI packet header fields.
struct UciPacketHeader {
    message_type: MessageType,
    group_id: GroupId,
    opcode: u8,
}

// Ensure that the new packet fragment belong to the same packet.
fn is_same_packet(header: &UciPacketHeader, packet: &UciPacketHalPacket) -> bool {
    header.message_type == packet.get_message_type()
        && header.group_id == packet.get_group_id()
        && header.opcode == packet.get_opcode()
}

// Helper to convert from vector of |UciPacketHalPacket| to |UciPacketPacket|
impl TryFrom<Vec<UciPacketHalPacket>> for UciPacketPacket {
    type Error = Error;

    fn try_from(packets: Vec<UciPacketHalPacket>) -> Result<Self> {
        if packets.is_empty() {
            return Err(Error::InvalidPacketError);
        }
        // Store header info from the first packet.
        let header = UciPacketHeader {
            message_type: packets[0].get_message_type(),
            group_id: packets[0].get_group_id(),
            opcode: packets[0].get_opcode(),
        };

        let mut payload_buf = BytesMut::new();
        // Create the reassembled payload.
        for packet in packets {
            // Ensure that the new fragment is part of the same packet.
            if !is_same_packet(&header, &packet) {
                error!("Received unexpected fragment: {:?}", packet);
                return Err(Error::InvalidPacketError);
            }
            // get payload by stripping the header.
            payload_buf.extend_from_slice(&packet.to_bytes().slice(UCI_PACKET_HAL_HEADER_LEN..))
        }
        // Create assembled |UciPacketPacket| and convert to bytes again since we need to
        // reparse the packet after defragmentation to get the appropriate message.
        UciPacketPacket::parse(
            &UciPacketBuilder {
                message_type: header.message_type,
                group_id: header.group_id,
                opcode: header.opcode,
                payload: Some(payload_buf.into()),
            }
            .build()
            .to_bytes(),
        )
    }
}

// Helper to convert from |UciPacketPacket| to vector of |UciPacketHalPacket|s
impl From<UciPacketPacket> for Vec<UciPacketHalPacket> {
    fn from(packet: UciPacketPacket) -> Self {
        // Store header info.
        let header = UciPacketHeader {
            message_type: packet.get_message_type(),
            group_id: packet.get_group_id(),
            opcode: packet.get_opcode(),
        };
        let mut fragments: Vec<UciPacketHalPacket> = Vec::new();
        // get payload by stripping the header.
        let payload = packet.to_bytes().slice(UCI_PACKET_HEADER_LEN..);
        if payload.is_empty() {
            fragments.push(
                UciPacketHalBuilder {
                    message_type: header.message_type,
                    group_id: header.group_id,
                    opcode: header.opcode,
                    packet_boundary_flag: PacketBoundaryFlag::Complete,
                    payload: None,
                }
                .build(),
            );
        } else {
            let mut fragments_iter = payload.chunks(MAX_PAYLOAD_LEN).peekable();
            while let Some(fragment) = fragments_iter.next() {
                // Set the last fragment complete if this is last fragment.
                let pbf = if let Some(nxt_fragment) = fragments_iter.peek() {
                    PacketBoundaryFlag::NotComplete
                } else {
                    PacketBoundaryFlag::Complete
                };
                fragments.push(
                    UciPacketHalBuilder {
                        message_type: header.message_type,
                        group_id: header.group_id,
                        opcode: header.opcode,
                        packet_boundary_flag: pbf,
                        payload: Some(Bytes::from(fragment.to_owned())),
                    }
                    .build(),
                );
            }
        }
        fragments
    }
}

#[derive(Default, Debug)]
pub struct PacketDefrager {
    // Cache to store incoming fragmented packets in the middle of reassembly.
    // Will be empty if there is no reassembly in progress.
    fragment_cache: Vec<UciPacketHalPacket>,
}

impl PacketDefrager {
    pub fn defragment_packet(&mut self, msg: &[u8]) -> Option<UciPacketPacket> {
        match UciPacketHalPacket::parse(msg) {
            Ok(packet) => {
                let pbf = packet.get_packet_boundary_flag();
                // Add the incoming fragment to the packet cache.
                self.fragment_cache.push(packet);
                if pbf == PacketBoundaryFlag::NotComplete {
                    // Wait for remaining fragments.
                    return None;
                }
                // All fragments received, defragment the packet.
                match self.fragment_cache.drain(..).collect::<Vec<_>>().try_into() {
                    Ok(packet) => Some(packet),
                    Err(e) => {
                        error!("Failed to defragment packet: {:?}", e);
                        None
                    }
                }
            }
            Err(e) => {
                error!("Failed to parse packet: {:?}", e);
                None
            }
        }
    }
}
