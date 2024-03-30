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

//! This module provides the functionalities related to UWB Command Interface (UCI).

// TODO(akahuang): remove it after implementing the client of each component.
#![allow(dead_code)]

mod command;
mod message;
mod response;
mod timeout_uci_hal;

pub(crate) mod error;
pub(crate) mod notification;
pub(crate) mod params;
pub(crate) mod uci_manager;

pub mod uci_hal;

#[cfg(test)]
pub(crate) mod mock_uci_hal;
#[cfg(test)]
pub(crate) mod mock_uci_manager;
