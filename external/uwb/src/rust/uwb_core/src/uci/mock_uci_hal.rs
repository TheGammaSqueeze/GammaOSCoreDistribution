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

use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use tokio::sync::{mpsc, Notify};
use tokio::time::timeout;

use crate::uci::error::{Error, Result};
use crate::uci::params::SessionId;
use crate::uci::uci_hal::{RawUciMessage, UciHal};

/// The mock implementation of UciHal.
#[derive(Default, Clone)]
pub struct MockUciHal {
    msg_sender: Option<mpsc::UnboundedSender<RawUciMessage>>,
    expected_calls: Arc<Mutex<VecDeque<ExpectedCall>>>,
    expect_call_consumed: Arc<Notify>,
}

impl MockUciHal {
    pub fn new() -> Self {
        Default::default()
    }

    pub fn expected_open(&mut self, msgs: Option<Vec<RawUciMessage>>, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::Open { msgs, out });
    }

    pub fn expected_close(&mut self, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::Close { out });
    }

    pub fn expected_send_command(
        &mut self,
        expected_cmd: RawUciMessage,
        msgs: Vec<RawUciMessage>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SendCommand {
            expected_cmd,
            msgs,
            out,
        });
    }

    pub fn expected_notify_session_initialized(
        &mut self,
        expected_session_id: SessionId,
        out: Result<()>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::NotifySessionInitialized { expected_session_id, out });
    }

    pub async fn wait_expected_calls_done(&mut self) -> bool {
        while !self.expected_calls.lock().unwrap().is_empty() {
            if timeout(Duration::from_secs(1), self.expect_call_consumed.notified()).await.is_err()
            {
                return false;
            }
        }
        true
    }
}

#[async_trait]
impl UciHal for MockUciHal {
    async fn open(&mut self, msg_sender: mpsc::UnboundedSender<RawUciMessage>) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::Open { msgs, out }) => {
                self.expect_call_consumed.notify_one();
                if let Some(msgs) = msgs {
                    for msg in msgs.into_iter() {
                        let _ = msg_sender.send(msg);
                    }
                }
                if out.is_ok() {
                    self.msg_sender.replace(msg_sender);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn close(&mut self) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::Close { out }) => {
                self.expect_call_consumed.notify_one();
                if out.is_ok() {
                    self.msg_sender = None;
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn send_command(&mut self, cmd: RawUciMessage) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SendCommand { expected_cmd, msgs, out }) if expected_cmd == cmd => {
                self.expect_call_consumed.notify_one();
                let msg_sender = self.msg_sender.as_mut().unwrap();
                for msg in msgs.into_iter() {
                    let _ = msg_sender.send(msg);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }

    async fn notify_session_initialized(&mut self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::NotifySessionInitialized { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::MockUndefined)
            }
            None => Err(Error::MockUndefined),
        }
    }
}

enum ExpectedCall {
    Open { msgs: Option<Vec<RawUciMessage>>, out: Result<()> },
    Close { out: Result<()> },
    SendCommand { expected_cmd: RawUciMessage, msgs: Vec<RawUciMessage>, out: Result<()> },
    NotifySessionInitialized { expected_session_id: SessionId, out: Result<()> },
}
