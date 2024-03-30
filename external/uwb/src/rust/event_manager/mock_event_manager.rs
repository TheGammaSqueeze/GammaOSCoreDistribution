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

//! MockEventManager

use crate::event_manager::EventManager;
use jni::errors::{Error, JniError, Result};
use log::warn;
use std::collections::VecDeque;
use std::sync::Mutex;
use uwb_uci_packets::{
    DeviceStatusNtfPacket, ExtendedMacTwoWayRangeDataNtfPacket, GenericErrorPacket,
    SessionStatusNtfPacket, SessionUpdateControllerMulticastListNtfPacket,
    ShortMacTwoWayRangeDataNtfPacket, UciNotificationPacket,
};

#[cfg(any(test, fuzzing))]
enum ExpectedCall {
    DeviceStatus { out: Result<()> },
    CoreGenericError { out: Result<()> },
    SessionStatus { out: Result<()> },
    ShortRangeData { out: Result<()> },
    ExtendedRangeData { out: Result<()> },
    SessionUpdateControllerMulticastList { out: Result<()> },
    VendorUci { out: Result<()> },
}

#[cfg(any(test, fuzzing))]
#[derive(Default)]
pub struct MockEventManager {
    expected_calls: Mutex<VecDeque<ExpectedCall>>,
}

#[cfg(any(test, fuzzing))]
impl MockEventManager {
    pub fn new() -> Self {
        Default::default()
    }

    pub fn expect_device_status_notification_received(&mut self, out: Result<()>) {
        self.add_expected_call(ExpectedCall::DeviceStatus { out });
    }

    pub fn expect_core_generic_error_notification_received(&mut self, out: Result<()>) {
        self.add_expected_call(ExpectedCall::CoreGenericError { out });
    }

    pub fn expect_session_status_notification_received(&mut self, out: Result<()>) {
        self.add_expected_call(ExpectedCall::SessionStatus { out });
    }

    pub fn expect_short_range_data_notification_received(&mut self, out: Result<()>) {
        self.add_expected_call(ExpectedCall::ShortRangeData { out });
    }

    pub fn expect_extended_range_data_notification_received(&mut self, out: Result<()>) {
        self.add_expected_call(ExpectedCall::ExtendedRangeData { out });
    }

    pub fn expect_session_update_controller_multicast_list_notification_received(
        &mut self,
        out: Result<()>,
    ) {
        self.add_expected_call(ExpectedCall::SessionUpdateControllerMulticastList { out });
    }

    pub fn expect_vendor_uci_notification_received(&mut self, out: Result<()>) {
        self.add_expected_call(ExpectedCall::VendorUci { out });
    }

    fn add_expected_call(&mut self, call: ExpectedCall) {
        self.expected_calls.lock().unwrap().push_back(call);
    }

    fn unwrap_out(&self, out: Option<Result<()>>, method_name: &str) -> Result<()> {
        out.unwrap_or_else(move || {
            warn!("unpected {:?}() called", method_name);
            Err(Error::JniCall(JniError::Unknown))
        })
    }

    pub fn clear_expected_calls(&self) {
        self.expected_calls.lock().unwrap().clear();
    }
}

#[cfg(any(test, fuzzing))]
impl Drop for MockEventManager {
    fn drop(&mut self) {
        assert!(self.expected_calls.lock().unwrap().is_empty());
    }
}
#[cfg(any(test, fuzzing))]
impl EventManager for MockEventManager {
    fn device_status_notification_received(&self, _data: DeviceStatusNtfPacket) -> Result<()> {
        let out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::DeviceStatus { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        self.unwrap_out(out, "device_status_notification_received")
    }

    fn core_generic_error_notification_received(&self, _data: GenericErrorPacket) -> Result<()> {
        let out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::CoreGenericError { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        self.unwrap_out(out, "core_generic_error_notification_received")
    }

    fn session_status_notification_received(&self, _data: SessionStatusNtfPacket) -> Result<()> {
        let out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::SessionStatus { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        self.unwrap_out(out, "session_status_notification_received")
    }

    fn short_range_data_notification_received(
        &self,
        _data: ShortMacTwoWayRangeDataNtfPacket,
    ) -> Result<()> {
        let out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::ShortRangeData { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        self.unwrap_out(out, "short_range_data_notification_received")
    }
    fn extended_range_data_notification_received(
        &self,
        _data: ExtendedMacTwoWayRangeDataNtfPacket,
    ) -> Result<()> {
        let out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::ExtendedRangeData { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        self.unwrap_out(out, "extended_range_data_notification_received")
    }

    fn session_update_controller_multicast_list_notification_received(
        &self,
        _data: SessionUpdateControllerMulticastListNtfPacket,
    ) -> Result<()> {
        let out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::SessionUpdateControllerMulticastList { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        self.unwrap_out(out, "session_update_controller_multicast_list_notification_received")
    }

    fn vendor_uci_notification_received(&self, _data: UciNotificationPacket) -> Result<()> {
        let out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedCall::VendorUci { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        self.unwrap_out(out, "vendor_uci_notification_received")
    }
}
