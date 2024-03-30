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

//! NCI API module

use crate::{CommandSender, Result};
use bytes::Bytes;
use log::debug;
use nfc_hal::{HalEvent, HalEventRegistry, HalEventStatus};
use nfc_packets::nci::{self, CommandBuilder, Opcode};
use nfc_packets::nci::{FeatureEnable, PacketBoundaryFlag, ResetType};
use nfc_packets::nci::{InitCommandBuilder, ResetCommandBuilder};
use nfc_packets::nci::{InitResponsePacket, ResponseChild};
use tokio::sync::oneshot;

/// NCI API object to manage static API data
pub struct NciApi {
    /// Command Sender external interface
    commands: Option<CommandSender>,
    /// The NFC response callback
    callback: Option<fn(u16, &[u8])>,
    /// HalEventRegistry is used to register for HAL events
    hal_events: Option<HalEventRegistry>,
    nfc_data: NfcData,
}

struct NfcData {
    init_response: Option<InitResponsePacket>,
}

impl NciApi {
    /// NciApi constructor
    pub fn new() -> NciApi {
        let nfc_data = NfcData { init_response: None };
        NciApi { commands: None, callback: None, hal_events: None, nfc_data }
    }

    /** ****************************************************************************
     **
     ** Function         nfc_enable
     **
     ** Description      This function enables NFC. Prior to calling NFC_Enable:
     **                  - the NFCC must be powered up, and ready to receive
     **                    commands.
     **
     **                  This function opens the NCI transport (if applicable),
     **                  resets the NFC controller, and initializes the NFC
     **                  subsystems.
     **
     **                  When the NFC startup procedure is completed, an
     **                  NFC_ENABLE_REVT is returned to the application using the
     **                  tNFC_RESPONSE_CBACK.
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    /// extern tNFC_STATUS NFC_Enable(tNFC_RESPONSE_CBACK* p_cback);
    pub async fn nfc_enable(&mut self, callback: fn(u16, &[u8])) {
        let nci = crate::init().await;

        self.commands = Some(nci.commands);
        self.callback = Some(callback);
        self.hal_events = Some(nci.hal_events);
    }
    /** ****************************************************************************
     **
     ** Function         NFC_Disable
     **
     ** Description      This function performs clean up routines for shutting down
     **                  NFC and closes the NCI transport (if using dedicated NCI
     **                  transport).
     **
     **                  When the NFC shutdown procedure is completed, an
     **                  NFC_DISABLED_REVT is returned to the application using the
     **                  tNFC_RESPONSE_CBACK.
     **
     ** Returns          nothing
     **
     *******************************************************************************/
    /// extern void NFC_Disable(void);
    pub async fn nfc_disable(&mut self) {
        let (tx, rx) = oneshot::channel::<HalEventStatus>();
        if let Some(mut hr) = self.hal_events.take() {
            hr.register(HalEvent::CloseComplete, tx).await;

            if let Some(cmd) = self.commands.take() {
                drop(cmd);
            }
            let status = rx.await.unwrap();
            debug!("Shutdown complete {:?}.", status);

            if let Some(cb) = self.callback.take() {
                cb(1, &[]);
            }
        }
    }

    /** ****************************************************************************
     **
     ** Function         NFC_Init
     **
     ** Description      This function initializes control blocks for NFC
     **
     ** Returns          nothing
     **
     *******************************************************************************/
    /// extern void NFC_Init(tHAL_NFC_ENTRY* p_hal_entry_tbl);
    pub async fn nfc_init(&mut self) -> Result<()> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        if let Some(cmd) = self.commands.as_mut() {
            let reset = cmd
                .send_and_notify(
                    ResetCommandBuilder { gid: 0, pbf, reset_type: ResetType::ResetConfig }
                        .build()
                        .into(),
                )
                .await?;
            let _notification_packet = reset.notification.await?;
            let init = cmd
                .send(
                    InitCommandBuilder { gid: 0, pbf, feature_enable: FeatureEnable::Rfu }
                        .build()
                        .into(),
                )
                .await?;
            if let ResponseChild::InitResponse(irp) = init.specialize() {
                self.nfc_data.init_response = Some(irp);
            }
        }
        Ok(())
    }

    /** *****************************************************************************
     **
     ** Function         NFC_GetLmrtSize
     **
     ** Description      Called by application wto query the Listen Mode Routing
     **                  Table size supported by NFCC
     **
     ** Returns          Listen Mode Routing Table size
     **
     *******************************************************************************/
    /// extern uint16_t NFC_GetLmrtSize(void);
    pub async fn nfc_get_lmrt_size(&mut self) -> u16 {
        if let Some(ir) = &self.nfc_data.init_response {
            ir.get_max_rout_tbls_size()
        } else {
            0
        }
    }

    /** *****************************************************************************
     **
     ** Function         NFC_SetConfig
     **
     ** Description      This function is called to send the configuration parameter
     **                  TLV to NFCC. The response from NFCC is reported by
     **                  tNFC_RESPONSE_CBACK as NFC_SET_CONFIG_REVT.
     **
     ** Parameters       tlv_size - the length of p_param_tlvs.
     **                  p_param_tlvs - the parameter ID/Len/Value list
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    /// extern tNFC_STATUS NFC_SetConfig(uint8_t tlv_size, uint8_t* p_param_tlvs);
    pub async fn nfc_set_config(&mut self, param_tlvs: &[u8]) -> Result<u8> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        if let Some(cmd) = self.commands.as_mut() {
            let rp = cmd
                .send(
                    CommandBuilder {
                        gid: 0,
                        pbf,
                        op: Opcode::CoreSetConfig,
                        payload: Some(Bytes::copy_from_slice(param_tlvs)),
                    }
                    .build(),
                )
                .await?;
            let raw = Bytes::from(rp);
            if let Some(cb) = self.callback {
                cb(2, &raw[3..]);
            }
            Ok(raw[3])
        } else {
            Ok(nci::Status::NotInitialized as u8)
        }
    }

    /** *****************************************************************************
     **
     ** Function         NFC_GetConfig
     **
     ** Description      This function is called to retrieve the parameter TLV from
     **                  NFCC. The response from NFCC is reported by
     **                  tNFC_RESPONSE_CBACK as NFC_GET_CONFIG_REVT.
     **
     ** Parameters       num_ids - the number of parameter IDs
     **                  p_param_ids - the parameter ID list.
     **
     ** Returns          tNFC_STATUS
     **
     *******************************************************************************/
    /// extern tNFC_STATUS NFC_GetConfig(uint8_t num_ids, uint8_t* p_param_ids);
    pub async fn nfc_get_config(&mut self, param_tlvs: &[u8]) -> Result<u8> {
        let pbf = PacketBoundaryFlag::CompleteOrFinal;
        if let Some(cmd) = self.commands.as_mut() {
            let rp = cmd
                .send(
                    CommandBuilder {
                        gid: 0,
                        pbf,
                        op: Opcode::CoreGetConfig,
                        payload: Some(Bytes::copy_from_slice(param_tlvs)),
                    }
                    .build(),
                )
                .await?;
            let raw = Bytes::from(rp);
            if let Some(cb) = self.callback {
                cb(3, &raw[3..]);
            }
            Ok(raw[3])
        } else {
            Ok(nci::Status::NotInitialized as u8)
        }
    }
}

impl Default for NciApi {
    fn default() -> Self {
        Self::new()
    }
}
