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
use std::iter::zip;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use tokio::sync::{mpsc, Notify};
use tokio::time::timeout;

use crate::uci::error::{Error, Result};
use crate::uci::notification::UciNotification;
use crate::uci::params::{
    app_config_tlvs_eq, device_config_tlvs_eq, AppConfigTlv, AppConfigTlvType, CapTlv, Controlee,
    CoreSetConfigResponse, CountryCode, DeviceConfigId, DeviceConfigTlv, GetDeviceInfoResponse,
    PowerStats, RawVendorMessage, ResetConfig, SessionId, SessionState, SessionType,
    SetAppConfigResponse, UpdateMulticastListAction,
};
use crate::uci::uci_manager::UciManager;

#[derive(Default, Clone)]
pub(crate) struct MockUciManager {
    expected_calls: Arc<Mutex<VecDeque<ExpectedCall>>>,
    notf_sender: Option<mpsc::UnboundedSender<UciNotification>>,
    expect_call_consumed: Arc<Notify>,
}

impl MockUciManager {
    pub fn new() -> Self {
        Default::default()
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

    pub fn expect_open_hal(&mut self, notfs: Vec<UciNotification>, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::OpenHal { notfs, out });
    }

    pub fn expect_close_hal(&mut self, out: Result<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::CloseHal { out });
    }

    pub fn expect_device_reset(&mut self, expected_reset_config: ResetConfig, out: Result<()>) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::DeviceReset { expected_reset_config, out });
    }

    pub fn expect_core_get_device_info(&mut self, out: Result<GetDeviceInfoResponse>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::CoreGetDeviceInfo { out });
    }

    pub fn expect_core_get_caps_info(&mut self, out: Result<Vec<CapTlv>>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::CoreGetCapsInfo { out });
    }

    pub fn expect_core_set_config(
        &mut self,
        expected_config_tlvs: Vec<DeviceConfigTlv>,
        out: Result<CoreSetConfigResponse>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::CoreSetConfig { expected_config_tlvs, out });
    }

    pub fn expect_core_get_config(
        &mut self,
        expected_config_ids: Vec<DeviceConfigId>,
        out: Result<Vec<DeviceConfigTlv>>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::CoreGetConfig { expected_config_ids, out });
    }

    pub fn expect_session_init(
        &mut self,
        expected_session_id: SessionId,
        expected_session_type: SessionType,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionInit {
            expected_session_id,
            expected_session_type,
            notfs,
            out,
        });
    }

    pub fn expect_session_deinit(&mut self, expected_session_id: SessionId, out: Result<()>) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::SessionDeinit { expected_session_id, out });
    }

    pub fn expect_session_set_app_config(
        &mut self,
        expected_session_id: SessionId,
        expected_config_tlvs: Vec<AppConfigTlv>,
        notfs: Vec<UciNotification>,
        out: Result<SetAppConfigResponse>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionSetAppConfig {
            expected_session_id,
            expected_config_tlvs,
            notfs,
            out,
        });
    }

    pub fn expect_session_get_app_config(
        &mut self,
        expected_session_id: SessionId,
        expected_config_ids: Vec<AppConfigTlvType>,
        out: Result<Vec<AppConfigTlv>>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionGetAppConfig {
            expected_session_id,
            expected_config_ids,
            out,
        });
    }

    pub fn expect_session_get_count(&mut self, out: Result<usize>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::SessionGetCount { out });
    }

    pub fn expect_session_get_state(
        &mut self,
        expected_session_id: SessionId,
        out: Result<SessionState>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::SessionGetState { expected_session_id, out });
    }

    pub fn expect_session_update_controller_multicast_list(
        &mut self,
        expected_session_id: SessionId,
        expected_action: UpdateMulticastListAction,
        expected_controlees: Vec<Controlee>,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(
            ExpectedCall::SessionUpdateControllerMulticastList {
                expected_session_id,
                expected_action,
                expected_controlees,
                notfs,
                out,
            },
        );
    }

    pub fn expect_range_start(
        &mut self,
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::RangeStart {
            expected_session_id,
            notfs,
            out,
        });
    }

    pub fn expect_range_stop(
        &mut self,
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::RangeStop {
            expected_session_id,
            notfs,
            out,
        });
    }

    pub fn expect_range_get_ranging_count(
        &mut self,
        expected_session_id: SessionId,
        out: Result<usize>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::RangeGetRangingCount { expected_session_id, out });
    }

    pub fn expect_android_set_country_code(
        &mut self,
        expected_country_code: CountryCode,
        out: Result<()>,
    ) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedCall::AndroidSetCountryCode { expected_country_code, out });
    }

    pub fn expect_android_get_power_stats(&mut self, out: Result<PowerStats>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::AndroidGetPowerStats { out });
    }

    pub fn expect_raw_vendor_cmd(
        &mut self,
        expected_gid: u32,
        expected_oid: u32,
        expected_payload: Vec<u8>,
        out: Result<RawVendorMessage>,
    ) {
        self.expected_calls.lock().unwrap().push_back(ExpectedCall::RawVendorCmd {
            expected_gid,
            expected_oid,
            expected_payload,
            out,
        });
    }
}

#[async_trait]
impl UciManager for MockUciManager {
    async fn open_hal(
        &mut self,
        notf_sender: mpsc::UnboundedSender<UciNotification>,
    ) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::OpenHal { notfs, out }) => {
                self.expect_call_consumed.notify_one();
                self.notf_sender = Some(notf_sender);
                for notf in notfs.into_iter() {
                    let _ = self.notf_sender.as_mut().unwrap().send(notf);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn close_hal(&mut self) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CloseHal { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn device_reset(&mut self, reset_config: ResetConfig) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::DeviceReset { expected_reset_config, out })
                if expected_reset_config == reset_config =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn core_get_device_info(&mut self) -> Result<GetDeviceInfoResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreGetDeviceInfo { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn core_get_caps_info(&mut self) -> Result<Vec<CapTlv>> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreGetCapsInfo { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn core_set_config(
        &mut self,
        config_tlvs: Vec<DeviceConfigTlv>,
    ) -> Result<CoreSetConfigResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreSetConfig { expected_config_tlvs, out })
                if device_config_tlvs_eq(&expected_config_tlvs, &config_tlvs) =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn core_get_config(
        &mut self,
        config_ids: Vec<DeviceConfigId>,
    ) -> Result<Vec<DeviceConfigTlv>> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::CoreGetConfig { expected_config_ids, out })
                if expected_config_ids == config_ids =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn session_init(
        &mut self,
        session_id: SessionId,
        session_type: SessionType,
    ) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionInit {
                expected_session_id,
                expected_session_type,
                notfs,
                out,
            }) if expected_session_id == session_id && expected_session_type == session_type => {
                self.expect_call_consumed.notify_one();
                for notf in notfs.into_iter() {
                    let _ = self.notf_sender.as_mut().unwrap().send(notf);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn session_deinit(&mut self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionDeinit { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn session_set_app_config(
        &mut self,
        session_id: SessionId,
        config_tlvs: Vec<AppConfigTlv>,
    ) -> Result<SetAppConfigResponse> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionSetAppConfig {
                expected_session_id,
                expected_config_tlvs,
                notfs,
                out,
            }) if expected_session_id == session_id
                && app_config_tlvs_eq(&expected_config_tlvs, &config_tlvs) =>
            {
                self.expect_call_consumed.notify_one();
                for notf in notfs.into_iter() {
                    let _ = self.notf_sender.as_mut().unwrap().send(notf);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn session_get_app_config(
        &mut self,
        session_id: SessionId,
        config_ids: Vec<AppConfigTlvType>,
    ) -> Result<Vec<AppConfigTlv>> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionGetAppConfig {
                expected_session_id,
                expected_config_ids,
                out,
            }) if expected_session_id == session_id && expected_config_ids == config_ids => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn session_get_count(&mut self) -> Result<usize> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionGetCount { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn session_get_state(&mut self, session_id: SessionId) -> Result<SessionState> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionGetState { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn session_update_controller_multicast_list(
        &mut self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
    ) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SessionUpdateControllerMulticastList {
                expected_session_id,
                expected_action,
                expected_controlees,
                notfs,
                out,
            }) if expected_session_id == session_id
                && expected_action == action
                && zip(&expected_controlees, &controlees).all(|(a, b)| {
                    a.short_address == b.short_address && a.subsession_id == b.subsession_id
                }) =>
            {
                self.expect_call_consumed.notify_one();
                for notf in notfs.into_iter() {
                    let _ = self.notf_sender.as_mut().unwrap().send(notf);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn range_start(&mut self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RangeStart { expected_session_id, notfs, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                for notf in notfs.into_iter() {
                    let _ = self.notf_sender.as_mut().unwrap().send(notf);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn range_stop(&mut self, session_id: SessionId) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RangeStop { expected_session_id, notfs, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                for notf in notfs.into_iter() {
                    let _ = self.notf_sender.as_mut().unwrap().send(notf);
                }
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn range_get_ranging_count(&mut self, session_id: SessionId) -> Result<usize> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RangeGetRangingCount { expected_session_id, out })
                if expected_session_id == session_id =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn android_set_country_code(&mut self, country_code: CountryCode) -> Result<()> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::AndroidSetCountryCode { expected_country_code, out })
                if expected_country_code == country_code =>
            {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn android_get_power_stats(&mut self) -> Result<PowerStats> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::AndroidGetPowerStats { out }) => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }

    async fn raw_vendor_cmd(
        &mut self,
        gid: u32,
        oid: u32,
        payload: Vec<u8>,
    ) -> Result<RawVendorMessage> {
        let mut expected_calls = self.expected_calls.lock().unwrap();
        match expected_calls.pop_front() {
            Some(ExpectedCall::RawVendorCmd {
                expected_gid,
                expected_oid,
                expected_payload,
                out,
            }) if expected_gid == gid && expected_oid == oid && expected_payload == payload => {
                self.expect_call_consumed.notify_one();
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(Error::WrongState)
            }
            None => Err(Error::WrongState),
        }
    }
}

#[derive(Clone)]
enum ExpectedCall {
    OpenHal {
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    CloseHal {
        out: Result<()>,
    },
    DeviceReset {
        expected_reset_config: ResetConfig,
        out: Result<()>,
    },
    CoreGetDeviceInfo {
        out: Result<GetDeviceInfoResponse>,
    },
    CoreGetCapsInfo {
        out: Result<Vec<CapTlv>>,
    },
    CoreSetConfig {
        expected_config_tlvs: Vec<DeviceConfigTlv>,
        out: Result<CoreSetConfigResponse>,
    },
    CoreGetConfig {
        expected_config_ids: Vec<DeviceConfigId>,
        out: Result<Vec<DeviceConfigTlv>>,
    },
    SessionInit {
        expected_session_id: SessionId,
        expected_session_type: SessionType,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    SessionDeinit {
        expected_session_id: SessionId,
        out: Result<()>,
    },
    SessionSetAppConfig {
        expected_session_id: SessionId,
        expected_config_tlvs: Vec<AppConfigTlv>,
        notfs: Vec<UciNotification>,
        out: Result<SetAppConfigResponse>,
    },
    SessionGetAppConfig {
        expected_session_id: SessionId,
        expected_config_ids: Vec<AppConfigTlvType>,
        out: Result<Vec<AppConfigTlv>>,
    },
    SessionGetCount {
        out: Result<usize>,
    },
    SessionGetState {
        expected_session_id: SessionId,
        out: Result<SessionState>,
    },
    SessionUpdateControllerMulticastList {
        expected_session_id: SessionId,
        expected_action: UpdateMulticastListAction,
        expected_controlees: Vec<Controlee>,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    RangeStart {
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    RangeStop {
        expected_session_id: SessionId,
        notfs: Vec<UciNotification>,
        out: Result<()>,
    },
    RangeGetRangingCount {
        expected_session_id: SessionId,
        out: Result<usize>,
    },
    AndroidSetCountryCode {
        expected_country_code: CountryCode,
        out: Result<()>,
    },
    AndroidGetPowerStats {
        out: Result<PowerStats>,
    },
    RawVendorCmd {
        expected_gid: u32,
        expected_oid: u32,
        expected_payload: Vec<u8>,
        out: Result<RawVendorMessage>,
    },
}
