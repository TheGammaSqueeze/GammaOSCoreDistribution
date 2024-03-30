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

pub use uwb_uci_packets::StatusCode;

/// The error code for UCI module.
#[derive(Debug, thiserror::Error, Clone)]
pub enum Error {
    #[error("Call the mothod in the wrong state")]
    WrongState,
    #[error("UCI response is mismatched with the pending command")]
    ResponseMismatched,
    #[error("Error occurs inside UciHal")]
    HalFailed,
    #[error("UCI response is not received in timeout")]
    Timeout,
    #[error("Could not parse: {0}")]
    Parse(String),
    #[error("Could not specialize packet: {0:?}")]
    Specialize(Vec<u8>),
    #[error("Invalid args")]
    InvalidArgs,
    #[error("Error UCI status code: {0:?}")]
    Status(StatusCode),
    #[cfg(test)]
    #[error("The result of the mock method is not assigned.")]
    MockUndefined,
}

// Because uwb_uci_packets::Error doesn't derive Clone trait, we convert it to Error::Parse with
// the formatted string.
impl From<uwb_uci_packets::Error> for Error {
    fn from(err: uwb_uci_packets::Error) -> Self {
        Error::Parse(format!("{:?}", err))
    }
}

pub type Result<T> = std::result::Result<T, Error>;
