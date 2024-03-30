/*
 * Copyright (C) 2021 The Android Open Source Project
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

extern crate libc;

use crate::uci::uci_logger::UciLogger;
use crate::uci::UwbErr;
use async_trait::async_trait;
use std::path::Path;
use uwb_uci_packets::{UciCommandPacket, UciNotificationPacket, UciResponsePacket};

#[cfg(test)]
pub struct MockUciLogger {}

#[cfg(test)]
impl MockUciLogger {
    pub fn new() -> Self {
        MockUciLogger {}
    }
}

#[cfg(test)]
impl Default for MockUciLogger {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
#[async_trait]
impl UciLogger for MockUciLogger {
    async fn log_uci_command(&self, _cmd: UciCommandPacket) {}
    async fn log_uci_response(&self, _rsp: UciResponsePacket) {}
    async fn log_uci_notification(&self, _ntf: UciNotificationPacket) {}
    async fn close_file(&self) {}
}

#[cfg(test)]
pub async fn create_dir(_path: impl AsRef<Path>) -> Result<(), UwbErr> {
    Ok(())
}

#[cfg(test)]
pub async fn remove_file(_path: impl AsRef<Path>) -> Result<(), UwbErr> {
    Ok(())
}

#[cfg(test)]
pub async fn rename(_from: impl AsRef<Path>, _to: impl AsRef<Path>) -> Result<(), UwbErr> {
    Ok(())
}
