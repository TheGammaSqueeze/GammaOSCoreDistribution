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

use std::convert::TryInto;
use std::time::Duration;

use async_trait::async_trait;
use log::{debug, error, warn};
use tokio::sync::{mpsc, oneshot};
use uwb_uci_packets::{Packet, UciCommandPacket};

use crate::uci::command::UciCommand;
use crate::uci::error::{Error, Result};
use crate::uci::message::UciMessage;
use crate::uci::notification::{CoreNotification, SessionNotification, UciNotification};
use crate::uci::params::{
    AppConfigTlv, AppConfigTlvType, CapTlv, Controlee, CoreSetConfigResponse, CountryCode,
    DeviceConfigId, DeviceConfigTlv, DeviceState, GetDeviceInfoResponse, PowerStats,
    RawVendorMessage, ResetConfig, SessionId, SessionState, SessionType, SetAppConfigResponse,
    UpdateMulticastListAction,
};
use crate::uci::response::UciResponse;
use crate::uci::timeout_uci_hal::TimeoutUciHal;
use crate::uci::uci_hal::{RawUciMessage, UciHal};
use crate::utils::PinSleep;

const UCI_TIMEOUT_MS: u64 = 800;
const MAX_RETRY_COUNT: usize = 3;

/// The UciManager organizes the state machine of the UWB HAL, and provides the interface which
/// abstracts the UCI commands, responses, and notifications.
#[async_trait]
pub(crate) trait UciManager: 'static + Send + Clone {
    // Open the UCI HAL.
    // All the other methods should be called after the open_hal() completes successfully.
    async fn open_hal(&mut self, notf_sender: mpsc::UnboundedSender<UciNotification>)
        -> Result<()>;

    // Close the UCI HAL.
    async fn close_hal(&mut self) -> Result<()>;

    // Send the standard UCI Commands.
    async fn device_reset(&mut self, reset_config: ResetConfig) -> Result<()>;
    async fn core_get_device_info(&mut self) -> Result<GetDeviceInfoResponse>;
    async fn core_get_caps_info(&mut self) -> Result<Vec<CapTlv>>;
    async fn core_set_config(
        &mut self,
        config_tlvs: Vec<DeviceConfigTlv>,
    ) -> Result<CoreSetConfigResponse>;
    async fn core_get_config(
        &mut self,
        config_ids: Vec<DeviceConfigId>,
    ) -> Result<Vec<DeviceConfigTlv>>;
    async fn session_init(
        &mut self,
        session_id: SessionId,
        session_type: SessionType,
    ) -> Result<()>;
    async fn session_deinit(&mut self, session_id: SessionId) -> Result<()>;
    async fn session_set_app_config(
        &mut self,
        session_id: SessionId,
        config_tlvs: Vec<AppConfigTlv>,
    ) -> Result<SetAppConfigResponse>;
    async fn session_get_app_config(
        &mut self,
        session_id: SessionId,
        config_ids: Vec<AppConfigTlvType>,
    ) -> Result<Vec<AppConfigTlv>>;
    async fn session_get_count(&mut self) -> Result<usize>;
    async fn session_get_state(&mut self, session_id: SessionId) -> Result<SessionState>;
    async fn session_update_controller_multicast_list(
        &mut self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
    ) -> Result<()>;
    async fn range_start(&mut self, session_id: SessionId) -> Result<()>;
    async fn range_stop(&mut self, session_id: SessionId) -> Result<()>;
    async fn range_get_ranging_count(&mut self, session_id: SessionId) -> Result<usize>;

    // Send the Android-specific UCI commands
    async fn android_set_country_code(&mut self, country_code: CountryCode) -> Result<()>;
    async fn android_get_power_stats(&mut self) -> Result<PowerStats>;

    // Send a raw vendor command.
    async fn raw_vendor_cmd(
        &mut self,
        gid: u32,
        oid: u32,
        payload: Vec<u8>,
    ) -> Result<RawVendorMessage>;
}

/// UciManagerImpl is the main implementation of UciManager. Using the actor model, UciManagerImpl
/// delegates the requests to UciManagerActor.
#[derive(Clone)]
pub(crate) struct UciManagerImpl {
    cmd_sender: mpsc::UnboundedSender<(UciManagerCmd, oneshot::Sender<Result<UciResponse>>)>,
}

impl UciManagerImpl {
    pub fn new<T: UciHal>(hal: T) -> Self {
        let (cmd_sender, cmd_receiver) = mpsc::unbounded_channel();
        let mut actor = UciManagerActor::new(hal, cmd_receiver);
        tokio::spawn(async move { actor.run().await });

        Self { cmd_sender }
    }

    // Send the |cmd| to the UciManagerActor.
    async fn send_cmd(&self, cmd: UciManagerCmd) -> Result<UciResponse> {
        let (result_sender, result_receiver) = oneshot::channel();
        match self.cmd_sender.send((cmd, result_sender)) {
            Ok(()) => result_receiver.await.unwrap_or(Err(Error::HalFailed)),
            Err(cmd) => {
                error!("Failed to send cmd: {:?}", cmd.0);
                Err(Error::HalFailed)
            }
        }
    }
}

#[async_trait]
impl UciManager for UciManagerImpl {
    async fn open_hal(
        &mut self,
        notf_sender: mpsc::UnboundedSender<UciNotification>,
    ) -> Result<()> {
        match self.send_cmd(UciManagerCmd::OpenHal { notf_sender }).await {
            Ok(UciResponse::OpenHal) => Ok(()),
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn close_hal(&mut self) -> Result<()> {
        match self.send_cmd(UciManagerCmd::CloseHal).await {
            Ok(UciResponse::CloseHal) => Ok(()),
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn device_reset(&mut self, reset_config: ResetConfig) -> Result<()> {
        let cmd = UciCommand::DeviceReset { reset_config };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::DeviceReset(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn core_get_device_info(&mut self) -> Result<GetDeviceInfoResponse> {
        let cmd = UciCommand::CoreGetDeviceInfo;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreGetDeviceInfo(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn core_get_caps_info(&mut self) -> Result<Vec<CapTlv>> {
        let cmd = UciCommand::CoreGetCapsInfo;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreGetCapsInfo(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn core_set_config(
        &mut self,
        config_tlvs: Vec<DeviceConfigTlv>,
    ) -> Result<CoreSetConfigResponse> {
        let cmd = UciCommand::CoreSetConfig { config_tlvs };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreSetConfig(resp)) => Ok(resp),
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn core_get_config(
        &mut self,
        cfg_id: Vec<DeviceConfigId>,
    ) -> Result<Vec<DeviceConfigTlv>> {
        let cmd = UciCommand::CoreGetConfig { cfg_id };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::CoreGetConfig(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn session_init(
        &mut self,
        session_id: SessionId,
        session_type: SessionType,
    ) -> Result<()> {
        let cmd = UciCommand::SessionInit { session_id, session_type };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionInit(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn session_deinit(&mut self, session_id: SessionId) -> Result<()> {
        let cmd = UciCommand::SessionDeinit { session_id };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionDeinit(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn session_set_app_config(
        &mut self,
        session_id: SessionId,
        config_tlvs: Vec<AppConfigTlv>,
    ) -> Result<SetAppConfigResponse> {
        let cmd = UciCommand::SessionSetAppConfig { session_id, config_tlvs };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionSetAppConfig(resp)) => Ok(resp),
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn session_get_app_config(
        &mut self,
        session_id: SessionId,
        app_cfg: Vec<AppConfigTlvType>,
    ) -> Result<Vec<AppConfigTlv>> {
        let cmd = UciCommand::SessionGetAppConfig { session_id, app_cfg };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionGetAppConfig(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn session_get_count(&mut self) -> Result<usize> {
        let cmd = UciCommand::SessionGetCount;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionGetCount(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn session_get_state(&mut self, session_id: SessionId) -> Result<SessionState> {
        let cmd = UciCommand::SessionGetState { session_id };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionGetState(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn session_update_controller_multicast_list(
        &mut self,
        session_id: SessionId,
        action: UpdateMulticastListAction,
        controlees: Vec<Controlee>,
    ) -> Result<()> {
        if !(1..=8).contains(&controlees.len()) {
            warn!("Number of controlees should be between 1 to 8");
            return Err(Error::InvalidArgs);
        }
        let cmd =
            UciCommand::SessionUpdateControllerMulticastList { session_id, action, controlees };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::SessionUpdateControllerMulticastList(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn range_start(&mut self, session_id: SessionId) -> Result<()> {
        let cmd = UciCommand::RangeStart { session_id };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::RangeStart(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn range_stop(&mut self, session_id: SessionId) -> Result<()> {
        let cmd = UciCommand::RangeStop { session_id };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::RangeStop(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn range_get_ranging_count(&mut self, session_id: SessionId) -> Result<usize> {
        let cmd = UciCommand::RangeGetRangingCount { session_id };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::RangeGetRangingCount(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn android_set_country_code(&mut self, country_code: CountryCode) -> Result<()> {
        let cmd = UciCommand::AndroidSetCountryCode { country_code };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::AndroidSetCountryCode(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn android_get_power_stats(&mut self) -> Result<PowerStats> {
        let cmd = UciCommand::AndroidGetPowerStats;
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::AndroidGetPowerStats(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }

    async fn raw_vendor_cmd(
        &mut self,
        gid: u32,
        oid: u32,
        payload: Vec<u8>,
    ) -> Result<RawVendorMessage> {
        let cmd = UciCommand::RawVendorCmd { gid, oid, payload };
        match self.send_cmd(UciManagerCmd::SendUciCommand { cmd }).await {
            Ok(UciResponse::RawVendorCmd(resp)) => resp,
            Ok(_) => Err(Error::ResponseMismatched),
            Err(e) => Err(e),
        }
    }
}

struct UciManagerActor<T: UciHal> {
    // The UCI HAL.
    hal: TimeoutUciHal<T>,
    // Receive the commands and the corresponding response senders from UciManager.
    cmd_receiver: mpsc::UnboundedReceiver<(UciManagerCmd, oneshot::Sender<Result<UciResponse>>)>,

    // Set to true when |hal| is opened successfully.
    is_hal_opened: bool,
    // Receive the response and the notification from |hal|. Only used when |hal| is opened
    // successfully.
    msg_receiver: mpsc::UnboundedReceiver<RawUciMessage>,
    // Send the notification to the UciManager. Only valid when |hal| is opened successfully.
    notf_sender: Option<mpsc::UnboundedSender<UciNotification>>,
    // Defrag the UCI packets.
    defrager: uwb_uci_packets::PacketDefrager,

    // The response sender of UciManager's open_hal() method. Used to wait for the device ready
    // notification.
    open_hal_result_sender: Option<oneshot::Sender<Result<UciResponse>>>,
    // The timeout of waiting for the notification of device ready notification.
    wait_device_status_timeout: PinSleep,

    // Used for the logic of retrying the command. Only valid when waiting for the response of a
    // UCI command.
    retryer: Option<Retryer>,
    // The timeout of waiting for the response. Only used when waiting for the response of a UCI
    // command.
    wait_resp_timeout: PinSleep,
}

impl<T: UciHal> UciManagerActor<T> {
    fn new(
        hal: T,
        cmd_receiver: mpsc::UnboundedReceiver<(
            UciManagerCmd,
            oneshot::Sender<Result<UciResponse>>,
        )>,
    ) -> Self {
        Self {
            hal: TimeoutUciHal::new(hal),
            cmd_receiver,
            is_hal_opened: false,
            msg_receiver: mpsc::unbounded_channel().1,
            notf_sender: None,
            defrager: Default::default(),
            open_hal_result_sender: None,
            wait_device_status_timeout: PinSleep::new(Duration::MAX),
            retryer: None,
            wait_resp_timeout: PinSleep::new(Duration::MAX),
        }
    }

    async fn run(&mut self) {
        loop {
            tokio::select! {
                // Handle the next command. Only when the previous command already received the
                // response.
                cmd = self.cmd_receiver.recv(), if !self.is_waiting_resp() => {
                    match cmd {
                        None => {
                            debug!("UciManager is about to drop.");
                            break;
                        },
                        Some((cmd, result_sender)) => {
                            self.handle_cmd(cmd, result_sender).await;
                        }
                    }
                }

                // Handle the UCI response or notification from HAL. Only when HAL is opened.
                msg = self.msg_receiver.recv(), if self.is_hal_opened => {
                    match msg {
                        None => {
                            warn!("UciHal dropped the msg_sender unexpectedly.");
                            self.on_hal_closed();
                        },
                        Some(msg) => {
                            if let Some(packet) = self.defrager.defragment_packet(&msg) {
                                match packet.try_into() {
                                    Ok(UciMessage::Response(resp)) => {
                                        self.handle_response(resp).await;
                                    }
                                    Ok(UciMessage::Notification(notf)) => {
                                        self.handle_notification(notf).await;
                                    }
                                    Err(e)=> {
                                        error!("Failed to parse received message: {:?}", e);
                                    }
                                }
                            }
                        },
                    }
                }

                // Timeout waiting for the response of the UCI command.
                _ = &mut self.wait_resp_timeout, if self.is_waiting_resp() => {
                    self.retryer.take().unwrap().send_result(Err(Error::Timeout));
                }

                // Timeout waiting for the notification of the device status.
                _ = &mut self.wait_device_status_timeout, if self.is_waiting_device_status() => {
                    if let Some(result_sender) = self.open_hal_result_sender.take() {
                        let _ = result_sender.send(Err(Error::Timeout));
                    }
                }
            }
        }

        if self.is_hal_opened {
            debug!("The HAL is still opened when exit, close the HAL");
            let _ = self.hal.close().await;
            self.on_hal_closed();
        }
    }

    async fn handle_cmd(
        &mut self,
        cmd: UciManagerCmd,
        result_sender: oneshot::Sender<Result<UciResponse>>,
    ) {
        debug!("Received cmd: {:?}", cmd);

        match cmd {
            UciManagerCmd::OpenHal { notf_sender } => {
                if self.is_hal_opened {
                    warn!("The UCI HAL is already opened, skip.");
                    let _ = result_sender.send(Err(Error::WrongState));
                    return;
                }

                let (msg_sender, msg_receiver) = mpsc::unbounded_channel();
                match self.hal.open(msg_sender).await {
                    Ok(()) => {
                        self.on_hal_open(msg_receiver, notf_sender);
                        self.wait_device_status_timeout =
                            PinSleep::new(Duration::from_millis(UCI_TIMEOUT_MS));
                        self.open_hal_result_sender.replace(result_sender);
                    }
                    Err(e) => {
                        error!("Failed to open hal: {:?}", e);
                        let _ = result_sender.send(Err(e));
                    }
                }
            }

            UciManagerCmd::CloseHal => {
                if !self.is_hal_opened {
                    warn!("The UCI HAL is already closed, skip.");
                    let _ = result_sender.send(Err(Error::WrongState));
                    return;
                }

                let result = self.hal.close().await.map(|_| UciResponse::CloseHal);
                if result.is_ok() {
                    self.on_hal_closed();
                }
                let _ = result_sender.send(result);
            }

            UciManagerCmd::SendUciCommand { cmd } => {
                debug_assert!(self.retryer.is_none());
                self.retryer = Some(Retryer { cmd, result_sender, retry_count: MAX_RETRY_COUNT });
                self.retry_command().await;
            }
        }
    }

    async fn retry_command(&mut self) {
        if let Some(mut retryer) = self.retryer.take() {
            if !retryer.could_retry() {
                retryer.send_result(Err(Error::Timeout));
                return;
            }

            match self.send_uci_command(retryer.cmd.clone()).await {
                Ok(_) => {
                    self.wait_resp_timeout = PinSleep::new(Duration::from_millis(UCI_TIMEOUT_MS));
                    self.retryer = Some(retryer);
                }
                Err(e) => {
                    retryer.send_result(Err(e));
                }
            }
        }
    }

    async fn send_uci_command(&mut self, cmd: UciCommand) -> Result<()> {
        if !self.is_hal_opened {
            warn!("The UCI HAL is already closed, skip.");
            return Err(Error::WrongState);
        }

        let packet = TryInto::<UciCommandPacket>::try_into(cmd)?;
        self.hal.send_command(packet.to_vec()).await?;
        Ok(())
    }

    async fn handle_response(&mut self, resp: UciResponse) {
        if resp.need_retry() {
            self.retry_command().await;
            return;
        }

        if let Some(retryer) = self.retryer.take() {
            retryer.send_result(Ok(resp));
        } else {
            warn!("Received an UCI response unexpectedly: {:?}", resp);
        }
    }

    async fn handle_notification(&mut self, notf: UciNotification) {
        if notf.need_retry() {
            self.retry_command().await;
            return;
        }

        match notf.clone() {
            UciNotification::Core(CoreNotification::DeviceStatus(status)) => {
                if let Some(result_sender) = self.open_hal_result_sender.take() {
                    let result = match status {
                        DeviceState::DeviceStateReady | DeviceState::DeviceStateActive => {
                            Ok(UciResponse::OpenHal)
                        }
                        _ => Err(Error::HalFailed),
                    };
                    let _ = result_sender.send(result);
                }
            }
            UciNotification::Session(SessionNotification::Status {
                session_id,
                session_state,
                reason_code: _,
            }) => {
                if matches!(session_state, SessionState::SessionStateInit) {
                    if let Err(e) = self.hal.notify_session_initialized(session_id).await {
                        warn!("notify_session_initialized() failed: {:?}", e);
                    }
                }
            }
            _ => {}
        }

        if let Some(notf_sender) = self.notf_sender.as_mut() {
            let _ = notf_sender.send(notf);
        }
    }

    fn on_hal_open(
        &mut self,
        msg_receiver: mpsc::UnboundedReceiver<RawUciMessage>,
        notf_sender: mpsc::UnboundedSender<UciNotification>,
    ) {
        self.is_hal_opened = true;
        self.msg_receiver = msg_receiver;
        self.notf_sender = Some(notf_sender);
    }

    fn on_hal_closed(&mut self) {
        self.is_hal_opened = false;
        self.msg_receiver = mpsc::unbounded_channel().1;
        self.notf_sender = None;
    }

    fn is_waiting_resp(&self) -> bool {
        self.retryer.is_some()
    }
    fn is_waiting_device_status(&self) -> bool {
        self.open_hal_result_sender.is_some()
    }
}

struct Retryer {
    cmd: UciCommand,
    result_sender: oneshot::Sender<Result<UciResponse>>,
    retry_count: usize,
}

impl Retryer {
    fn could_retry(&mut self) -> bool {
        if self.retry_count == 0 {
            return false;
        }
        self.retry_count -= 1;
        true
    }

    fn send_result(self, result: Result<UciResponse>) {
        let _ = self.result_sender.send(result);
    }
}

#[derive(Debug)]
enum UciManagerCmd {
    OpenHal { notf_sender: mpsc::UnboundedSender<UciNotification> },
    CloseHal,
    SendUciCommand { cmd: UciCommand },
}

#[cfg(test)]
mod tests {
    use super::*;

    use bytes::Bytes;
    use num_traits::ToPrimitive;

    use crate::uci::error::StatusCode;
    use crate::uci::mock_uci_hal::MockUciHal;
    use crate::uci::params::{
        app_config_tlvs_eq, cap_tlv_eq, device_config_tlvs_eq, power_stats_eq, CapTlvType,
    };
    use crate::utils::init_test_logging;

    fn into_raw_messages<T: Into<uwb_uci_packets::UciPacketPacket>>(
        builder: T,
    ) -> Vec<RawUciMessage> {
        let packets: Vec<uwb_uci_packets::UciPacketHalPacket> = builder.into().into();
        packets.into_iter().map(|packet| packet.into()).collect()
    }

    async fn setup_uci_manager_with_open_hal(
        setup_hal_fn: fn(&mut MockUciHal),
    ) -> (UciManagerImpl, mpsc::UnboundedReceiver<UciNotification>, MockUciHal) {
        init_test_logging();

        let mut hal = MockUciHal::new();
        let notf = into_raw_messages(uwb_uci_packets::DeviceStatusNtfBuilder {
            device_state: uwb_uci_packets::DeviceState::DeviceStateReady,
        });
        hal.expected_open(Some(notf), Ok(()));
        setup_hal_fn(&mut hal);

        let (notf_sender, notf_receiver) = mpsc::unbounded_channel();
        // Verify open_hal() is working.
        let mut uci_manager = UciManagerImpl::new(hal.clone());
        let result = uci_manager.open_hal(notf_sender).await;
        assert!(result.is_ok());

        (uci_manager, notf_receiver, hal)
    }

    #[tokio::test]
    async fn test_close_hal_explicitly() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            hal.expected_close(Ok(()));
        })
        .await;

        let result = uci_manager.close_hal().await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_close_hal_when_exit() {
        let (uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            // UciManager should close the hal if the hal is still opened when exit.
            hal.expected_close(Ok(()));
        })
        .await;

        drop(uci_manager);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_close_hal_without_open_hal() {
        init_test_logging();

        let mut hal = MockUciHal::new();
        let mut uci_manager = UciManagerImpl::new(hal.clone());

        let result = uci_manager.close_hal().await;
        assert!(matches!(result, Err(Error::WrongState)));
        assert!(hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_device_reset_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::DeviceResetCmdBuilder {
                reset_config: uwb_uci_packets::ResetConfig::UwbsReset,
            }
            .build()
            .into();
            let resp = into_raw_messages(uwb_uci_packets::DeviceResetRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let result = uci_manager.device_reset(ResetConfig::UwbsReset).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_core_get_device_info_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::GetDeviceInfoCmdBuilder {}.build().into();
            let resp = into_raw_messages(uwb_uci_packets::GetDeviceInfoRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                uci_version: 0x1234,
                mac_version: 0x5678,
                phy_version: 0x90ab,
                uci_test_version: 0x1357,
                vendor_spec_info: vec![0x1, 0x2],
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let expected_result = GetDeviceInfoResponse {
            uci_version: 0x1234,
            mac_version: 0x5678,
            phy_version: 0x90ab,
            uci_test_version: 0x1357,
            vendor_spec_info: vec![0x1, 0x2],
        };
        let result = uci_manager.core_get_device_info().await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_core_get_caps_info_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let tlv = uwb_uci_packets::CapTlv {
                t: uwb_uci_packets::CapTlvType::SupportedFiraPhyVersionRange,
                v: vec![0x12, 0x34, 0x56],
            };
            let cmd = uwb_uci_packets::GetCapsInfoCmdBuilder {}.build().into();
            let resp = into_raw_messages(uwb_uci_packets::GetCapsInfoRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                tlvs: vec![tlv],
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let tlv = CapTlv { t: CapTlvType::SupportedFiraPhyVersionRange, v: vec![0x12, 0x34, 0x56] };
        let result = uci_manager.core_get_caps_info().await.unwrap();
        assert!(cap_tlv_eq(&result[0], &tlv));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_core_set_config_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let tlv = uwb_uci_packets::DeviceConfigTlv {
                cfg_id: uwb_uci_packets::DeviceConfigId::DeviceState,
                v: vec![0x12, 0x34, 0x56],
            };
            let cmd = uwb_uci_packets::SetConfigCmdBuilder { tlvs: vec![tlv] }.build().into();
            let resp = into_raw_messages(uwb_uci_packets::SetConfigRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                cfg_status: vec![],
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let config_tlv =
            DeviceConfigTlv { cfg_id: DeviceConfigId::DeviceState, v: vec![0x12, 0x34, 0x56] };
        let expected_result =
            CoreSetConfigResponse { status: StatusCode::UciStatusOk, config_status: vec![] };
        let result = uci_manager.core_set_config(vec![config_tlv]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_core_get_config_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cfg_id = uwb_uci_packets::DeviceConfigId::DeviceState;
            let tlv = uwb_uci_packets::DeviceConfigTlv { cfg_id, v: vec![0x12, 0x34, 0x56] };
            let cmd =
                uwb_uci_packets::GetConfigCmdBuilder { cfg_id: vec![cfg_id.to_u8().unwrap()] }
                    .build()
                    .into();
            let resp = into_raw_messages(uwb_uci_packets::GetConfigRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                tlvs: vec![tlv],
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let config_id = DeviceConfigId::DeviceState;
        let expected_result = vec![DeviceConfigTlv {
            cfg_id: DeviceConfigId::DeviceState,
            v: vec![0x12, 0x34, 0x56],
        }];
        CoreSetConfigResponse { status: StatusCode::UciStatusOk, config_status: vec![] };
        let result = uci_manager.core_get_config(vec![config_id]).await.unwrap();
        assert!(device_config_tlvs_eq(&result, &expected_result));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_init_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let session_id = 0x123;
            let cmd = uwb_uci_packets::SessionInitCmdBuilder {
                session_id,
                session_type: uwb_uci_packets::SessionType::FiraRangingSession,
            }
            .build()
            .into();
            let mut resp = into_raw_messages(uwb_uci_packets::SessionInitRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
            });
            let mut notf = into_raw_messages(uwb_uci_packets::SessionStatusNtfBuilder {
                session_id,
                session_state: uwb_uci_packets::SessionState::SessionStateInit,
                reason_code: uwb_uci_packets::ReasonCode::StateChangeWithSessionManagementCommands,
            });
            resp.append(&mut notf);

            hal.expected_send_command(cmd, resp, Ok(()));
            hal.expected_notify_session_initialized(session_id, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let session_type = SessionType::FiraRangingSession;
        let result = uci_manager.session_init(session_id, session_type).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_deinit_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::SessionDeinitCmdBuilder { session_id: 0x123 }.build().into();
            let resp = into_raw_messages(uwb_uci_packets::SessionDeinitRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let result = uci_manager.session_deinit(session_id).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_set_app_config_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let tlv = uwb_uci_packets::AppConfigTlv {
                cfg_id: uwb_uci_packets::AppConfigTlvType::DeviceType,
                v: vec![0x12, 0x34, 0x56],
            };
            let cmd = uwb_uci_packets::SessionSetAppConfigCmdBuilder {
                session_id: 0x123,
                tlvs: vec![tlv],
            }
            .build()
            .into();
            let resp = into_raw_messages(uwb_uci_packets::SessionSetAppConfigRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                cfg_status: vec![],
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let config_tlv =
            AppConfigTlv { cfg_id: AppConfigTlvType::DeviceType, v: vec![0x12, 0x34, 0x56] };
        let expected_result =
            SetAppConfigResponse { status: StatusCode::UciStatusOk, config_status: vec![] };
        let result =
            uci_manager.session_set_app_config(session_id, vec![config_tlv]).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_app_config_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cfg_id = uwb_uci_packets::AppConfigTlvType::DeviceType;
            let tlv = uwb_uci_packets::AppConfigTlv { cfg_id, v: vec![0x12, 0x34, 0x56] };
            let cmd = uwb_uci_packets::SessionGetAppConfigCmdBuilder {
                session_id: 0x123,
                app_cfg: vec![cfg_id.to_u8().unwrap()],
            }
            .build()
            .into();
            let resp = into_raw_messages(uwb_uci_packets::SessionGetAppConfigRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                tlvs: vec![tlv],
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let config_id = AppConfigTlvType::DeviceType;
        let expected_result =
            vec![AppConfigTlv { cfg_id: AppConfigTlvType::DeviceType, v: vec![0x12, 0x34, 0x56] }];
        let result = uci_manager.session_get_app_config(session_id, vec![config_id]).await.unwrap();
        assert!(app_config_tlvs_eq(&result, &expected_result));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::SessionGetCountCmdBuilder {}.build().into();
            let resp = into_raw_messages(uwb_uci_packets::SessionGetCountRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                session_count: 5,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let result = uci_manager.session_get_count().await.unwrap();
        assert_eq!(result, 5);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_state_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd =
                uwb_uci_packets::SessionGetStateCmdBuilder { session_id: 0x123 }.build().into();
            let resp = into_raw_messages(uwb_uci_packets::SessionGetStateRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                session_state: uwb_uci_packets::SessionState::SessionStateActive,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let result = uci_manager.session_get_state(session_id).await.unwrap();
        assert_eq!(result, SessionState::SessionStateActive);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_update_controller_multicast_list_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let controlee =
                uwb_uci_packets::Controlee { short_address: 0x4567, subsession_id: 0x90ab };
            let cmd = uwb_uci_packets::SessionUpdateControllerMulticastListCmdBuilder {
                session_id: 0x123,
                action: UpdateMulticastListAction::AddControlee,
                controlees: vec![controlee],
            }
            .build()
            .into();
            let resp = into_raw_messages(
                uwb_uci_packets::SessionUpdateControllerMulticastListRspBuilder {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                },
            );

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let action = UpdateMulticastListAction::AddControlee;
        let controlee = Controlee { short_address: 0x4567, subsession_id: 0x90ab };
        let result = uci_manager
            .session_update_controller_multicast_list(session_id, action, vec![controlee])
            .await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_range_start_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::RangeStartCmdBuilder { session_id: 0x123 }.build().into();
            let resp = into_raw_messages(uwb_uci_packets::RangeStartRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let result = uci_manager.range_start(session_id).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_range_stop_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::RangeStopCmdBuilder { session_id: 0x123 }.build().into();
            let resp = into_raw_messages(uwb_uci_packets::RangeStopRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let result = uci_manager.range_stop(session_id).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_range_get_ranging_count_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::RangeGetRangingCountCmdBuilder { session_id: 0x123 }
                .build()
                .into();
            let resp = into_raw_messages(uwb_uci_packets::RangeGetRangingCountRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                count: 3,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let session_id = 0x123;
        let result = uci_manager.range_get_ranging_count(session_id).await.unwrap();
        assert_eq!(result, 3);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_android_set_country_code_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd =
                uwb_uci_packets::AndroidSetCountryCodeCmdBuilder { country_code: b"US".to_owned() }
                    .build()
                    .into();
            let resp = into_raw_messages(uwb_uci_packets::AndroidSetCountryCodeRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let country_code = CountryCode::new(b"US").unwrap();
        let result = uci_manager.android_set_country_code(country_code).await;
        assert!(result.is_ok());
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_android_get_power_stats_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::AndroidGetPowerStatsCmdBuilder {}.build().into();
            let resp = into_raw_messages(uwb_uci_packets::AndroidGetPowerStatsRspBuilder {
                stats: uwb_uci_packets::PowerStats {
                    status: uwb_uci_packets::StatusCode::UciStatusOk,
                    idle_time_ms: 123,
                    tx_time_ms: 456,
                    rx_time_ms: 789,
                    total_wake_count: 5,
                },
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let expected_result = PowerStats {
            status: StatusCode::UciStatusOk,
            idle_time_ms: 123,
            tx_time_ms: 456,
            rx_time_ms: 789,
            total_wake_count: 5,
        };
        let result = uci_manager.android_get_power_stats().await.unwrap();
        assert!(power_stats_eq(&result, &expected_result));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_raw_vendor_cmd_ok() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd = uwb_uci_packets::UciVendor_F_CommandBuilder {
                opcode: 0x3,
                payload: Some(Bytes::from(vec![0x11, 0x22, 0x33, 0x44])),
            }
            .build()
            .into();
            let resp = into_raw_messages(uwb_uci_packets::UciVendor_F_ResponseBuilder {
                opcode: 0x3,
                payload: Some(Bytes::from(vec![0x55, 0x66, 0x77, 0x88])),
            });

            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let gid = 0xF;
        let oid = 0x3;
        let payload = vec![0x11, 0x22, 0x33, 0x44];
        let expected_result = RawVendorMessage { gid, oid, payload: vec![0x55, 0x66, 0x77, 0x88] };
        let result = uci_manager.raw_vendor_cmd(gid, oid, payload).await.unwrap();
        assert_eq!(result, expected_result);
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_retry_no_response() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd: RawUciMessage = uwb_uci_packets::SessionGetCountCmdBuilder {}.build().into();

            hal.expected_send_command(cmd, vec![], Ok(()));
        })
        .await;

        let result = uci_manager.session_get_count().await;
        assert!(matches!(result, Err(Error::Timeout)));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_timeout() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd: RawUciMessage = uwb_uci_packets::SessionGetCountCmdBuilder {}.build().into();

            hal.expected_send_command(cmd, vec![], Err(Error::Timeout));
        })
        .await;

        let result = uci_manager.session_get_count().await;
        assert!(matches!(result, Err(Error::Timeout)));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_retry_too_many_times() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd: RawUciMessage = uwb_uci_packets::SessionGetCountCmdBuilder {}.build().into();
            let retry_resp = into_raw_messages(uwb_uci_packets::SessionGetCountRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusCommandRetry,
                session_count: 0,
            });

            for _ in 0..MAX_RETRY_COUNT {
                hal.expected_send_command(cmd.clone(), retry_resp.clone(), Ok(()));
            }
        })
        .await;

        let result = uci_manager.session_get_count().await;
        assert!(matches!(result, Err(Error::Timeout)));
        assert!(mock_hal.wait_expected_calls_done().await);
    }

    #[tokio::test]
    async fn test_session_get_count_retry_notification() {
        let (mut uci_manager, _, mut mock_hal) = setup_uci_manager_with_open_hal(|hal| {
            let cmd: RawUciMessage = uwb_uci_packets::SessionGetCountCmdBuilder {}.build().into();
            let retry_resp = into_raw_messages(uwb_uci_packets::SessionGetCountRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusCommandRetry,
                session_count: 0,
            });
            let resp = into_raw_messages(uwb_uci_packets::SessionGetCountRspBuilder {
                status: uwb_uci_packets::StatusCode::UciStatusOk,
                session_count: 5,
            });

            hal.expected_send_command(cmd.clone(), retry_resp.clone(), Ok(()));
            hal.expected_send_command(cmd.clone(), retry_resp, Ok(()));
            hal.expected_send_command(cmd, resp, Ok(()));
        })
        .await;

        let result = uci_manager.session_get_count().await.unwrap();
        assert_eq!(result, 5);
        assert!(mock_hal.wait_expected_calls_done().await);
    }
}
