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

#[cfg(test)]
use crate::uci::mock_uci_logger::{create_dir, remove_file, rename};
use crate::uci::UwbErr;
use async_trait::async_trait;
use bytes::{BufMut, BytesMut};
use log::{error, info};
use std::marker::Unpin;
use std::sync::Arc;
use std::time::SystemTime;
use tokio::fs::OpenOptions;
#[cfg(not(test))]
use tokio::fs::{create_dir, remove_file, rename};
use tokio::io::{AsyncWrite, AsyncWriteExt};
use tokio::sync::Mutex;
use tokio::{task, time};
use uwb_uci_packets::{
    AppConfigTlv, AppConfigTlvType, MessageType, Packet, SessionCommandChild,
    SessionGetAppConfigRspBuilder, SessionResponseChild, SessionSetAppConfigCmdBuilder,
    UciCommandChild, UciCommandPacket, UciNotificationPacket, UciPacketPacket, UciResponseChild,
    UciResponsePacket,
};

// micros since 0000-01-01
const UCI_EPOCH_DELTA: u64 = 0x00dcddb30f2f8000;
const UCI_LOG_LAST_FILE_STORE_TIME_SEC: u64 = 86400; // 24 hours
const MAX_FILE_SIZE: usize = 102400; // 100 kb
const MAX_BUFFER_SIZE: usize = 10240; // 10 kb
const PKT_LOG_HEADER_SIZE: usize = 25;
const VENDOR_ID: u64 = AppConfigTlvType::VendorId as u64;
const STATIC_STS_IV: u64 = AppConfigTlvType::StaticStsIv as u64;
const LOG_DIR: &str = "/data/misc/apexdata/com.android.uwb/log";
const FILE_NAME: &str = "uwb_uci.log";
const LOG_HEADER: &[u8] = b"ucilogging";

type SyncFile = Arc<Mutex<dyn AsyncWrite + Send + Sync + Unpin>>;
type SyncFactory = Arc<Mutex<dyn FileFactory + Send + Sync>>;

#[derive(Clone, PartialEq, Eq)]
pub enum UciLogMode {
    Disabled,
    Filtered,
    Enabled,
}

#[derive(Clone)]
enum Type {
    Command = 1,
    Response,
    Notification,
}

#[derive(Clone)]
pub struct UciLogConfig {
    path: String,
    mode: UciLogMode,
}

impl UciLogConfig {
    pub fn new(mode: UciLogMode) -> Self {
        Self { path: format!("{}/{}", LOG_DIR, FILE_NAME), mode }
    }
}

#[async_trait]
pub trait UciLogger {
    async fn log_uci_command(&self, cmd: UciCommandPacket);
    async fn log_uci_response(&self, rsp: UciResponsePacket);
    async fn log_uci_notification(&self, ntf: UciNotificationPacket);
    async fn close_file(&self);
}

struct BufferedFile {
    file: Option<SyncFile>,
    size_count: usize,
    buffer: BytesMut,
    deleter_handle: Option<task::JoinHandle<()>>,
}

impl BufferedFile {
    async fn open_next_file(&mut self, factory: SyncFactory, path: &str) -> Result<(), UwbErr> {
        info!("Open next file");
        self.close_file().await;
        if create_dir(LOG_DIR).await.is_err() {
            error!("Failed to create dir");
        }
        if rename(path, path.to_owned() + ".last").await.is_err() {
            error!("Failed to rename the file");
        }
        if let Some(deleter_handle) = self.deleter_handle.take() {
            deleter_handle.abort();
        }
        let last_file_path = path.to_owned() + ".last";
        self.deleter_handle = Some(task::spawn(async {
            time::sleep(time::Duration::from_secs(UCI_LOG_LAST_FILE_STORE_TIME_SEC)).await;
            if remove_file(last_file_path).await.is_err() {
                error!("Failed to remove file!");
            };
        }));
        let mut file = factory.lock().await.create_file_using_open_options(path).await?;
        write(&mut file, LOG_HEADER).await;
        self.file = Some(file);
        Ok(())
    }

    async fn close_file(&mut self) {
        if let Some(file) = &mut self.file {
            info!("UCI log file closing");
            write(file, &self.buffer).await;
            self.file = None;
            self.buffer.clear();
        }
        self.size_count = 0;
    }
}

pub struct UciLoggerImpl {
    config: UciLogConfig,
    buf_file: Mutex<BufferedFile>,
    file_factory: SyncFactory,
}

impl UciLoggerImpl {
    pub async fn new(mode: UciLogMode, file_factory: SyncFactory) -> Self {
        let config = UciLogConfig::new(mode);
        let mut factory = file_factory.lock().await;
        factory.set_config(config.clone()).await;
        let (file, size) = factory.new_file().await;
        let buf_file =
            BufferedFile { size_count: size, file, buffer: BytesMut::new(), deleter_handle: None };
        let ret =
            Self { config, buf_file: Mutex::new(buf_file), file_factory: file_factory.clone() };
        info!("UCI logger created");
        ret
    }

    async fn log_uci_packet(&self, packet: UciPacketPacket) {
        let mt = packet.get_message_type();
        let bytes = packet.to_vec();
        let mt_byte = match mt {
            MessageType::Command => Type::Command as u8,
            MessageType::Response => Type::Response as u8,
            MessageType::Notification => Type::Notification as u8,
        };
        let flags = match mt {
            MessageType::Command => 0b10,      // down direction
            MessageType::Response => 0b01,     // up direction
            MessageType::Notification => 0b01, // up direction
        };
        let timestamp = u64::try_from(
            SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).unwrap().as_micros(),
        )
        .unwrap()
            + UCI_EPOCH_DELTA;

        let length = u32::try_from(bytes.len()).unwrap() + 1;

        // Check whether exceeded the size limit
        let mut buf_file = self.buf_file.lock().await;
        if buf_file.size_count + bytes.len() + PKT_LOG_HEADER_SIZE > MAX_FILE_SIZE {
            match buf_file.open_next_file(self.file_factory.clone(), &self.config.path).await {
                Ok(()) => info!("New file created"),
                Err(e) => error!("Open next file failed: {:?}", e),
            }
        } else if buf_file.buffer.len() + bytes.len() + PKT_LOG_HEADER_SIZE > MAX_BUFFER_SIZE {
            let temp_buf = buf_file.buffer.clone();
            if let Some(file) = &mut buf_file.file {
                write(file, &temp_buf).await;
                buf_file.buffer.clear();
            }
        }
        buf_file.buffer.put_u32(length); // original length
        buf_file.buffer.put_u32(length); // captured length
        buf_file.buffer.put_u32(flags); // flags
        buf_file.buffer.put_u32(0); // dropped packets
        buf_file.buffer.put_u64(timestamp); // timestamp
        buf_file.buffer.put_u8(mt_byte); // type
        buf_file.buffer.put_slice(&bytes); // full packet.
        buf_file.size_count += bytes.len() + PKT_LOG_HEADER_SIZE;
    }
}

async fn write(file: &mut SyncFile, buffer: &[u8]) {
    let mut locked_file = file.lock().await;
    if locked_file.write_all(buffer).await.is_err() {
        error!("Failed to write");
    }
    if locked_file.flush().await.is_err() {
        error!("Failed to flush");
    }
}

#[async_trait]
impl UciLogger for UciLoggerImpl {
    async fn log_uci_command(&self, cmd: UciCommandPacket) {
        match self.config.mode {
            UciLogMode::Disabled => return,
            UciLogMode::Enabled => self.log_uci_packet(cmd.into()).await,
            UciLogMode::Filtered => {
                let filtered_cmd: UciCommandPacket = match cmd.specialize() {
                    UciCommandChild::SessionCommand(session_cmd) => {
                        match session_cmd.specialize() {
                            SessionCommandChild::SessionSetAppConfigCmd(set_config_cmd) => {
                                let session_id = set_config_cmd.get_session_id();
                                let tlvs = set_config_cmd.get_tlvs();
                                let mut filtered_tlvs = Vec::new();
                                for tlv in tlvs {
                                    if VENDOR_ID == tlv.cfg_id as u64
                                        || STATIC_STS_IV == tlv.cfg_id as u64
                                    {
                                        filtered_tlvs.push(AppConfigTlv {
                                            cfg_id: tlv.cfg_id,
                                            v: vec![0; tlv.v.len()],
                                        });
                                    } else {
                                        filtered_tlvs.push(tlv.clone());
                                    }
                                }
                                SessionSetAppConfigCmdBuilder { session_id, tlvs: filtered_tlvs }
                                    .build()
                                    .into()
                            }
                            _ => session_cmd.into(),
                        }
                    }
                    _ => cmd,
                };
                self.log_uci_packet(filtered_cmd.into()).await;
            }
        }
    }

    async fn log_uci_response(&self, rsp: UciResponsePacket) {
        match self.config.mode {
            UciLogMode::Disabled => return,
            UciLogMode::Enabled => self.log_uci_packet(rsp.into()).await,
            UciLogMode::Filtered => {
                let filtered_rsp: UciResponsePacket = match rsp.specialize() {
                    UciResponseChild::SessionResponse(session_rsp) => {
                        match session_rsp.specialize() {
                            SessionResponseChild::SessionGetAppConfigRsp(rsp) => {
                                let status = rsp.get_status();
                                let tlvs = rsp.get_tlvs();
                                let mut filtered_tlvs = Vec::new();
                                for tlv in tlvs {
                                    if VENDOR_ID == tlv.cfg_id as u64
                                        || STATIC_STS_IV == tlv.cfg_id as u64
                                    {
                                        filtered_tlvs.push(AppConfigTlv {
                                            cfg_id: tlv.cfg_id,
                                            v: vec![0; tlv.v.len()],
                                        });
                                    } else {
                                        filtered_tlvs.push(tlv.clone());
                                    }
                                }
                                SessionGetAppConfigRspBuilder { status, tlvs: filtered_tlvs }
                                    .build()
                                    .into()
                            }
                            _ => session_rsp.into(),
                        }
                    }
                    _ => rsp,
                };
                self.log_uci_packet(filtered_rsp.into()).await;
            }
        }
    }

    async fn log_uci_notification(&self, ntf: UciNotificationPacket) {
        if self.config.mode == UciLogMode::Disabled {
            return;
        }
        // No notifications to be filtered.
        self.log_uci_packet(ntf.into()).await;
    }

    async fn close_file(&self) {
        if self.config.mode == UciLogMode::Disabled {
            return;
        }
        self.buf_file.lock().await.close_file().await;
    }
}

#[async_trait]
pub trait FileFactory {
    async fn new_file(&self) -> (Option<SyncFile>, usize);
    async fn create_file_using_open_options(&self, path: &str) -> Result<SyncFile, UwbErr>;
    async fn create_file_at_path(&self, path: &str) -> Option<SyncFile>;
    async fn set_config(&mut self, config: UciLogConfig);
}

#[derive(Default)]
pub struct RealFileFactory {
    config: Option<UciLogConfig>,
}

#[async_trait]
impl FileFactory for RealFileFactory {
    async fn new_file(&self) -> (Option<SyncFile>, usize) {
        match OpenOptions::new()
            .append(true)
            .custom_flags(libc::O_NOFOLLOW)
            .open(&self.config.as_ref().unwrap().path)
            .await
            .ok()
        {
            Some(f) => {
                let size = match f.metadata().await {
                    Ok(md) => {
                        let duration = match md.modified() {
                            Ok(modified_date) => {
                                match SystemTime::now().duration_since(modified_date) {
                                    Ok(duration) => duration.as_secs(),
                                    Err(e) => {
                                        error!("Failed to convert to duration {:?}", e);
                                        0
                                    }
                                }
                            }
                            Err(e) => {
                                error!("Failed to convert to duration {:?}", e);
                                0
                            }
                        };
                        if duration > UCI_LOG_LAST_FILE_STORE_TIME_SEC {
                            0
                        } else {
                            md.len().try_into().unwrap()
                        }
                    }
                    Err(e) => {
                        error!("Failed to get metadata {:?}", e);
                        0
                    }
                };
                match size {
                    0 => {
                        (self.create_file_at_path(&self.config.as_ref().unwrap().path).await, size)
                    }
                    _ => (Some(Arc::new(Mutex::new(f))), size),
                }
            }
            None => (self.create_file_at_path(&self.config.as_ref().unwrap().path).await, 0),
        }
    }

    async fn set_config(&mut self, config: UciLogConfig) {
        self.config = Some(config);
    }

    async fn create_file_using_open_options(&self, path: &str) -> Result<SyncFile, UwbErr> {
        Ok(Arc::new(Mutex::new(OpenOptions::new().write(true).create_new(true).open(path).await?)))
    }
    async fn create_file_at_path(&self, path: &str) -> Option<SyncFile> {
        if create_dir(LOG_DIR).await.is_err() {
            error!("Failed to create dir");
        }
        if remove_file(path).await.is_err() {
            error!("Failed to remove file!");
        }
        match self.create_file_using_open_options(path).await {
            Ok(mut f) => {
                write(&mut f, LOG_HEADER).await;
                Some(f)
            }
            Err(e) => {
                error!("Failed to create file {:?}", e);
                None
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use core::pin::Pin;
    use core::task::{Context, Poll};
    use log::debug;
    use std::io::Error;
    use uwb_uci_packets::{
        AppConfigTlvType, DeviceState, DeviceStatusNtfBuilder, GetDeviceInfoCmdBuilder,
        GetDeviceInfoRspBuilder, StatusCode,
    };

    struct MockLogFile;

    impl MockLogFile {
        #[allow(dead_code)]
        async fn write_all(&mut self, _data: &[u8]) -> Result<(), UwbErr> {
            debug!("Write to fake file");
            Ok(())
        }
        #[allow(dead_code)]
        async fn flush(&self) -> Result<(), UwbErr> {
            debug!("Fake file flush success");
            Ok(())
        }
    }

    impl AsyncWrite for MockLogFile {
        fn poll_write(
            self: Pin<&mut Self>,
            _cx: &mut Context<'_>,
            _buf: &[u8],
        ) -> Poll<Result<usize, Error>> {
            Poll::Ready(Ok(0))
        }

        fn poll_flush(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            Poll::Ready(Ok(()))
        }

        fn poll_shutdown(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            Poll::Ready(Ok(()))
        }
    }

    struct MockFileFactory;

    #[async_trait]
    impl FileFactory for MockFileFactory {
        async fn new_file(&self) -> (Option<SyncFile>, usize) {
            (Some(Arc::new(Mutex::new(MockLogFile {}))), 0)
        }
        async fn set_config(&mut self, _config: UciLogConfig) {}
        async fn create_file_using_open_options(&self, _path: &str) -> Result<SyncFile, UwbErr> {
            Ok(Arc::new(Mutex::new(MockLogFile {})))
        }
        async fn create_file_at_path(&self, _path: &str) -> Option<SyncFile> {
            Some(Arc::new(Mutex::new(MockLogFile {})))
        }
    }

    #[tokio::test]
    async fn test_log_command() -> Result<(), UwbErr> {
        let logger =
            UciLoggerImpl::new(UciLogMode::Filtered, Arc::new(Mutex::new(MockFileFactory {})))
                .await;
        let cmd: UciCommandPacket = GetDeviceInfoCmdBuilder {}.build().into();
        logger.log_uci_command(cmd).await;
        let data = [0x20, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00];
        let buf_file = logger.buf_file.lock().await;
        assert_eq!(1, buf_file.buffer[buf_file.buffer.len() - data.len() - 1]);
        assert_eq!(data, buf_file.buffer[(buf_file.buffer.len() - data.len())..]);
        Ok(())
    }

    #[tokio::test]
    async fn test_log_response() -> Result<(), UwbErr> {
        let logger =
            UciLoggerImpl::new(UciLogMode::Filtered, Arc::new(Mutex::new(MockFileFactory {})))
                .await;
        let rsp = GetDeviceInfoRspBuilder {
            status: StatusCode::UciStatusOk,
            uci_version: 0,
            mac_version: 0,
            phy_version: 0,
            uci_test_version: 0,
            vendor_spec_info: vec![],
        }
        .build()
        .into();
        logger.log_uci_response(rsp).await;
        let data = [
            0x40, 0x02, 0x00, 0x0a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00,
        ];
        let buf_file = logger.buf_file.lock().await;
        assert_eq!(2, buf_file.buffer[buf_file.buffer.len() - data.len() - 1]);
        assert_eq!(data, buf_file.buffer[(buf_file.buffer.len() - data.len())..]);
        Ok(())
    }

    #[tokio::test]
    async fn test_log_notification() -> Result<(), UwbErr> {
        let logger =
            UciLoggerImpl::new(UciLogMode::Filtered, Arc::new(Mutex::new(MockFileFactory {})))
                .await;
        let ntf =
            DeviceStatusNtfBuilder { device_state: DeviceState::DeviceStateReady }.build().into();
        logger.log_uci_notification(ntf).await;
        let data = [0x60, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01];
        let buf_file = logger.buf_file.lock().await;
        assert_eq!(3, buf_file.buffer[buf_file.buffer.len() - data.len() - 1]);
        assert_eq!(data, buf_file.buffer[(buf_file.buffer.len() - data.len())..]);
        Ok(())
    }

    #[tokio::test]
    async fn test_disabled_log() -> Result<(), UwbErr> {
        let logger =
            UciLoggerImpl::new(UciLogMode::Disabled, Arc::new(Mutex::new(MockFileFactory {})))
                .await;
        let cmd: UciCommandPacket = GetDeviceInfoCmdBuilder {}.build().into();
        logger.log_uci_command(cmd).await;
        let buf_file = logger.buf_file.lock().await;
        assert!(buf_file.buffer.is_empty());
        Ok(())
    }

    #[tokio::test]
    async fn test_filter_log() -> Result<(), UwbErr> {
        let logger =
            UciLoggerImpl::new(UciLogMode::Filtered, Arc::new(Mutex::new(MockFileFactory {})))
                .await;
        let rsp = SessionGetAppConfigRspBuilder {
            status: StatusCode::UciStatusOk,
            tlvs: vec![AppConfigTlv { cfg_id: AppConfigTlvType::VendorId, v: vec![0x02, 0x02] }],
        }
        .build()
        .into();
        logger.log_uci_response(rsp).await;
        let data = [0x41, 0x04, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00, 0x01, 0x27, 0x02, 0x00, 0x00];
        let buf_file = logger.buf_file.lock().await;
        assert_eq!(2, buf_file.buffer[buf_file.buffer.len() - data.len() - 1]);
        assert_eq!(data, buf_file.buffer[(buf_file.buffer.len() - data.len())..]);
        Ok(())
    }
}
