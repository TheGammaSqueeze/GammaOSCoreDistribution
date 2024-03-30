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

//! MockUwbAdaptation

use crate::adaptation::UwbAdaptation;
use crate::error::UwbErr;
use crate::uci::uci_hrcv;
use crate::uci::HalCallback;
use android_hardware_uwb::aidl::android::hardware::uwb::{
    UwbEvent::UwbEvent, UwbStatus::UwbStatus,
};
use async_trait::async_trait;
use log::warn;
use std::collections::VecDeque;
use std::sync::Mutex as StdMutex;
use tokio::sync::mpsc;
use uwb_uci_packets::{Packet, UciCommandPacket};

type Result<T> = std::result::Result<T, UwbErr>;

#[cfg(any(test, fuzzing))]
enum ExpectedCall {
    Finalize {
        expected_exit_status: bool,
    },
    HalOpen {
        out: Result<()>,
    },
    HalClose {
        out: Result<()>,
    },
    CoreInitialization {
        out: Result<()>,
    },
    SessionInitialization {
        expected_session_id: i32,
        out: Result<()>,
    },
    SendUciMessage {
        expected_cmd: UciCommandPacket,
        rsp: Option<uci_hrcv::UciResponse>,
        notf: Option<uci_hrcv::UciNotification>,
        out: Result<()>,
    },
}

#[cfg(any(test, fuzzing))]
pub struct MockUwbAdaptation {
    rsp_sender: mpsc::UnboundedSender<HalCallback>,
    expected_calls: StdMutex<VecDeque<ExpectedCall>>,
}

#[cfg(any(test, fuzzing))]
impl MockUwbAdaptation {
    pub fn new(rsp_sender: mpsc::UnboundedSender<HalCallback>) -> Self {
        Self { rsp_sender, expected_calls: StdMutex::new(VecDeque::new()) }
    }

    pub fn expect_finalize(&self, expected_exit_status: bool) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::Finalize { expected_exit_status });
    }
    pub fn expect_hal_open(&self, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::HalOpen { out });
    }
    pub fn expect_hal_close(&self, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::HalClose { out });
    }
    pub fn expect_core_initialization(&self, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::CoreInitialization { out });
    }
    pub fn expect_session_initialization(&self, expected_session_id: i32, out: Result<()>) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::SessionInitialization { expected_session_id, out });
    }
    pub fn expect_send_uci_message(
        &self,
        expected_cmd: UciCommandPacket,
        rsp: Option<uci_hrcv::UciResponse>,
        notf: Option<uci_hrcv::UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SendUciMessage {
            expected_cmd,
            rsp,
            notf,
            out,
        });
    }

    pub fn clear_expected_calls(&self) {
        self.expected_calls.lock().unwrap().clear();
    }

    async fn send_hal_event(&self, event: UwbEvent, event_status: UwbStatus) {
        self.rsp_sender.send(HalCallback::Event { event, event_status }).unwrap();
    }

    async fn send_uci_response(&self, rsp: uci_hrcv::UciResponse) {
        self.rsp_sender.send(HalCallback::UciRsp(rsp)).unwrap();
    }

    async fn send_uci_notification(&self, ntf: uci_hrcv::UciNotification) {
        self.rsp_sender.send(HalCallback::UciNtf(ntf)).unwrap();
    }
}

#[cfg(any(test, fuzzing))]
impl Drop for MockUwbAdaptation {
    fn drop(&mut self) {
        assert!(self.expected_calls.lock().unwrap().is_empty());
    }
}

#[cfg(any(test, fuzzing))]
#[async_trait]
impl UwbAdaptation for MockUwbAdaptation {
    async fn finalize(&mut self, exit_status: bool) {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::Finalize { expected_exit_status })
                if expected_exit_status == exit_status =>
            {
                return;
            }
            Some(call) => {
                expected_calls.push_front(call);
            }
            None => {}
        }
        warn!("unpected finalize() called");
    }

    async fn hal_open(&self) -> Result<()> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::HalOpen { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => {
                let status = if out.is_ok() { UwbStatus::OK } else { UwbStatus::FAILED };
                self.send_hal_event(UwbEvent::OPEN_CPLT, status).await;
                out
            }
            None => {
                warn!("unpected hal_open() called");
                Err(UwbErr::Undefined)
            }
        }
    }

    async fn hal_close(&self) -> Result<()> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::HalClose { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => {
                let status = if out.is_ok() { UwbStatus::OK } else { UwbStatus::FAILED };
                self.send_hal_event(UwbEvent::CLOSE_CPLT, status).await;
                out
            }
            None => {
                warn!("unpected hal_close() called");
                Err(UwbErr::Undefined)
            }
        }
    }

    async fn core_initialization(&self) -> Result<()> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::CoreInitialization { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => {
                let status = if out.is_ok() { UwbStatus::OK } else { UwbStatus::FAILED };
                self.send_hal_event(UwbEvent::POST_INIT_CPLT, status).await;
                out
            }
            None => {
                warn!("unpected core_initialization() called");
                Err(UwbErr::Undefined)
            }
        }
    }

    async fn session_initialization(&self, session_id: i32) -> Result<()> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::SessionInitialization { expected_session_id, out })
                    if expected_session_id == session_id =>
                {
                    Some(out)
                }
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => out,
            None => {
                warn!("unpected session_initialization() called");
                Err(UwbErr::Undefined)
            }
        }
    }

    async fn send_uci_message(&self, cmd: UciCommandPacket) -> Result<()> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::SendUciMessage {
                    expected_cmd,
                    rsp,
                    notf,
                    out,
                    // PDL generated packets do not implement PartialEq, so use the raw bytes for comparison.
                }) if expected_cmd.clone().to_bytes() == cmd.to_bytes() => Some((rsp, notf, out)),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some((rsp, notf, out)) => {
                if let Some(notf) = notf {
                    self.send_uci_notification(notf).await;
                }
                if let Some(rsp) = rsp {
                    self.send_uci_response(rsp).await;
                }
                out
            }
            None => {
                warn!("unpected send_uci_message() called");
                Err(UwbErr::Undefined)
            }
        }
    }
}
