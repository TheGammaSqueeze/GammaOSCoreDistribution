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

use crate::uci::params::{SessionId, SessionState};

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum Error {
    #[error("Error occurs inside UciManager")]
    Uci,
    #[error("Failed to pass the message via tokio")]
    TokioFailure,
    #[error("Max session exceeded")]
    MaxSessionsExceeded,
    #[error("Duplicated SessionId: {0}")]
    DuplicatedSessionId(SessionId),
    #[error("Unknown SessionId: {0}")]
    UnknownSessionId(SessionId),
    #[error("Invalid arguments")]
    InvalidArguments,
    #[error("Wrong SessionState: {0}")]
    WrongState(SessionState),
    #[error("Notification is not received in timeout")]
    Timeout,
}

pub type Result<T> = std::result::Result<T, Error>;
