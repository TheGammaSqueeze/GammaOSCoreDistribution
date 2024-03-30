// Copyright 2021, The Android Open Source Project
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

//! This connects to "rootcanal" and provides a simulated
//! Nfc chip as well as a simulated environment.

use bytes::{BufMut, BytesMut};
use log::{debug, Level};
use logger::{self, Config};
use nfc_packets::nci;
use nfc_packets::nci::{CommandChild, NciChild};
use nfc_packets::nci::{
    ConfigParams, ConfigStatus, GetConfigResponseBuilder, NciVersion, ParamIds,
    ResetNotificationBuilder, ResetResponseBuilder, ResetTrigger, ResetType,
    SetConfigResponseBuilder,
};
use nfc_packets::nci::{InitResponseBuilder, NfccFeatures, RfInterface};
use nfc_packets::nci::{NciMsgType, NciPacket, Packet, PacketBoundaryFlag};
use std::collections::HashMap;
use std::convert::TryInto;
use std::mem::size_of_val;
use std::sync::Arc;
use thiserror::Error;
use tokio::io;
use tokio::io::{AsyncReadExt, AsyncWriteExt, BufReader, ErrorKind};
use tokio::net::TcpListener;
use tokio::sync::RwLock;

/// Result type
type Result<T> = std::result::Result<T, RootcanalError>;

#[derive(Debug, Error)]
enum RootcanalError {
    #[error("Termination request")]
    TerminateTask,
    #[error("Socket error")]
    IoError(#[from] io::Error),
    #[error("Unsupported command packet")]
    UnsupportedCommand,
    #[error("Packet did not parse correctly")]
    InvalidPacket,
    #[error("Packet type not supported")]
    UnsupportedPacket,
}

/// Provides storage for internal configuration parameters
#[derive(Clone)]
pub struct InternalConfiguration {
    map: Arc<RwLock<HashMap<ParamIds, Vec<u8>>>>,
}

impl InternalConfiguration {
    /// InternalConfiguration constructor
    pub async fn new() -> Self {
        let ic = InternalConfiguration { map: Arc::new(RwLock::new(HashMap::new())) };
        let mut map = ic.map.write().await;
        map.insert(ParamIds::LfT3tMax, vec![0x10u8]);
        drop(map);
        ic
    }

    /// Set a configuration parameter
    pub async fn set(&mut self, parameter: ParamIds, value: Vec<u8>) {
        self.map.write().await.insert(parameter, value);
    }

    /// Gets a parameter value or None
    pub async fn get(&mut self, parameter: ParamIds) -> Option<Vec<u8>> {
        self.map.read().await.get(&parameter).map(|v| (*v).clone())
    }

    /// Clears the allocated storage
    pub async fn clear(&mut self) {
        self.map.write().await.clear();
    }
}

const TERMINATION: u8 = 4u8;

#[tokio::main]
async fn main() -> io::Result<()> {
    logger::init(Config::default().with_tag_on_device("nfc-rc").with_min_level(Level::Trace));

    let listener = TcpListener::bind("127.0.0.1:54323").await?;

    for _ in 0..2 {
        let (mut sock, _) = listener.accept().await?;

        tokio::spawn(async move {
            let (rd, mut wr) = sock.split();
            let mut rd = BufReader::new(rd);
            let config = InternalConfiguration::new().await;
            loop {
                if let Err(e) = process(config.clone(), &mut rd, &mut wr).await {
                    match e {
                        RootcanalError::TerminateTask => break,
                        RootcanalError::IoError(e) => {
                            if e.kind() == ErrorKind::UnexpectedEof {
                                break;
                            }
                        }
                        _ => panic!("Communication error: {:?}", e),
                    }
                }
            }
        })
        .await?;
    }
    Ok(())
}

async fn process<R, W>(config: InternalConfiguration, reader: &mut R, writer: &mut W) -> Result<()>
where
    R: AsyncReadExt + Unpin,
    W: AsyncWriteExt + Unpin,
{
    let mut buffer = BytesMut::with_capacity(1024);
    let len: usize = reader.read_u16().await?.into();
    buffer.resize(len, 0);
    reader.read_exact(&mut buffer).await?;
    let frozen = buffer.freeze();
    debug!("{:?}", &frozen);
    let pkt_type = (frozen[0] >> 5) & 0x7;
    debug!("packet {} received len={}", &pkt_type, &len);
    if pkt_type == NciMsgType::Command as u8 {
        match NciPacket::parse(&frozen) {
            Ok(p) => command_response(config, writer, p).await,
            Err(_) => Err(RootcanalError::InvalidPacket),
        }
    } else if pkt_type == TERMINATION {
        Err(RootcanalError::TerminateTask)
    } else {
        Err(RootcanalError::UnsupportedPacket)
    }
}

const MAX_PAYLOAD: u8 = 255;

async fn command_response<W>(
    mut config: InternalConfiguration,
    out: &mut W,
    cmd: NciPacket,
) -> Result<()>
where
    W: AsyncWriteExt + Unpin,
{
    let pbf = PacketBoundaryFlag::CompleteOrFinal;
    let gid = 0u8;
    let mut status = nci::Status::Ok;
    match cmd.specialize() {
        NciChild::Command(cmd) => match cmd.specialize() {
            CommandChild::ResetCommand(rst) => {
                write_nci(out, (ResetResponseBuilder { gid, pbf, status }).build()).await?;
                write_nci(
                    out,
                    (ResetNotificationBuilder {
                        gid,
                        pbf,
                        trigger: ResetTrigger::ResetCommand,
                        config_status: if rst.get_reset_type() == ResetType::KeepConfig {
                            ConfigStatus::ConfigKept
                        } else {
                            ConfigStatus::ConfigReset
                        },
                        nci_version: NciVersion::Version20,
                        manufacturer_id: 0,
                        mfsi: Vec::new(),
                    })
                    .build(),
                )
                .await
            }
            CommandChild::InitCommand(_) => {
                let nfcc_feat = [0u8; 5];
                let rf_int = [0u8; 2];
                write_nci(
                    out,
                    (InitResponseBuilder {
                        gid,
                        pbf,
                        status,
                        nfcc_features: NfccFeatures::parse(&nfcc_feat).unwrap(),
                        max_log_conns: 0,
                        max_rout_tbls_size: 0x0000,
                        max_ctrl_payload: MAX_PAYLOAD,
                        max_data_payload: MAX_PAYLOAD,
                        num_of_credits: 0,
                        max_nfcv_rf_frame_sz: 64,
                        rf_interface: vec![RfInterface::parse(&rf_int).unwrap(); 1],
                    })
                    .build(),
                )
                .await
            }
            CommandChild::SetConfigCommand(sc) => {
                for cp in sc.get_params() {
                    if cp.valm.len() > 251 {
                        status = nci::Status::InvalidParam;
                        break;
                    }
                    config.set(cp.paramid, cp.valm.clone()).await;
                }
                write_nci(
                    out,
                    (SetConfigResponseBuilder { gid, pbf, status, paramids: Vec::new() }).build(),
                )
                .await
            }
            CommandChild::GetConfigCommand(gc) => {
                let mut cpv: Vec<ConfigParams> = Vec::new();
                for paramid in gc.get_paramids() {
                    let mut cp = ConfigParams { paramid: paramid.pids, valm: Vec::new() };
                    if status == nci::Status::Ok {
                        if let Some(val) = config.get(paramid.pids).await {
                            cp.valm = val;
                        } else {
                            status = nci::Status::InvalidParam;
                            cpv.clear();
                        }
                    } else if config.get(paramid.pids).await.is_some() {
                        continue;
                    }
                    cpv.push(cp);
                    // The Status field takes a byte
                    if size_of_val(&*cpv) > (MAX_PAYLOAD - 1).into() {
                        cpv.pop();
                        if status == nci::Status::Ok {
                            status = nci::Status::MessageSizeExceeded;
                        }
                        break;
                    }
                }
                write_nci(out, (GetConfigResponseBuilder { gid, pbf, status, params: cpv }).build())
                    .await
            }
            _ => Err(RootcanalError::UnsupportedCommand),
        },
        _ => Err(RootcanalError::InvalidPacket),
    }
}

async fn write_nci<W, T>(writer: &mut W, rsp: T) -> Result<()>
where
    W: AsyncWriteExt + Unpin,
    T: Into<NciPacket>,
{
    let pkt = rsp.into();
    let b = pkt.to_bytes();
    let mut data = BytesMut::with_capacity(b.len() + 2);
    data.put_u16(b.len().try_into().unwrap());
    data.extend(b);
    let frozen = data.freeze();
    writer.write_all(frozen.as_ref()).await?;
    debug!("command written");
    Ok(())
}
