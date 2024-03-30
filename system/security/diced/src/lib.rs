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

//! Implement the android.security.dice.IDiceNode service.

mod error;
mod permission;
mod proxy_node_hal;
mod resident_node;

pub use crate::proxy_node_hal::ProxyNodeHal;
pub use crate::resident_node::ResidentNode;
use android_hardware_security_dice::aidl::android::hardware::security::dice::{
    Bcc::Bcc, BccHandover::BccHandover, Config::Config as BinderConfig,
    InputValues::InputValues as BinderInputValues, Mode::Mode, Signature::Signature,
};
use android_security_dice::aidl::android::security::dice::{
    IDiceMaintenance::BnDiceMaintenance, IDiceMaintenance::IDiceMaintenance, IDiceNode::BnDiceNode,
    IDiceNode::IDiceNode, ResponseCode::ResponseCode,
};
use anyhow::{Context, Result};
use binder::{BinderFeatures, Result as BinderResult, Strong, ThreadState};
pub use diced_open_dice_cbor as dice;
use error::{map_or_log_err, Error};
use keystore2_selinux as selinux;
use libc::uid_t;
use permission::Permission;
use std::sync::Arc;

/// A DiceNode backend implementation.
/// All functions except demote_self derive effective dice artifacts staring from
/// this node and iterating through `{ [client | demotion path], input_values }`
/// in ascending order.
pub trait DiceNodeImpl {
    /// Signs the message using the effective dice artifacts and Ed25519Pure.
    fn sign(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
        message: &[u8],
    ) -> Result<Signature>;
    /// Returns the effective attestation chain.
    fn get_attestation_chain(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Result<Bcc>;
    /// Returns the effective dice artifacts.
    fn derive(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Result<BccHandover>;
    /// Adds [ `client` | `input_values` ] to the demotion path of the given client.
    /// This changes the effective dice artifacts for all subsequent API calls of the
    /// given client.
    fn demote(&self, client: BinderInputValues, input_values: &[BinderInputValues]) -> Result<()>;
    /// This demotes the implementation itself. I.e. a resident node would replace its resident
    /// with the effective artifacts derived using `input_values`. A proxy node would
    /// simply call `demote` on its parent node. This is not reversible and changes
    /// the effective dice artifacts of all clients.
    fn demote_self(&self, input_values: &[BinderInputValues]) -> Result<()>;
}

/// Wraps a DiceNodeImpl and implements the actual IDiceNode AIDL API.
pub struct DiceNode {
    node_impl: Arc<dyn DiceNodeImpl + Sync + Send>,
}

/// This function uses its namesake in the permission module and in
/// combination with with_calling_sid from the binder crate to check
/// if the caller has the given keystore permission.
pub fn check_caller_permission<T: selinux::ClassPermission>(perm: T) -> Result<()> {
    ThreadState::with_calling_sid(|calling_sid| {
        let target_context =
            selinux::getcon().context("In check_caller_permission: getcon failed.")?;

        selinux::check_permission(
            calling_sid.ok_or(Error::Rc(ResponseCode::SYSTEM_ERROR)).context(
                "In check_keystore_permission: Cannot check permission without calling_sid.",
            )?,
            &target_context,
            perm,
        )
    })
}

fn client_input_values(uid: uid_t) -> Result<BinderInputValues> {
    Ok(BinderInputValues {
        codeHash: [0; dice::HASH_SIZE],
        config: BinderConfig {
            desc: dice::bcc::format_config_descriptor(Some(&format!("{}", uid)), None, false)
                .context("In client_input_values: failed to format config descriptor")?,
        },
        authorityHash: [0; dice::HASH_SIZE],
        authorityDescriptor: None,
        hidden: [0; dice::HIDDEN_SIZE],
        mode: Mode::NORMAL,
    })
}

impl DiceNode {
    /// Constructs an instance of DiceNode, wraps it with a BnDiceNode object and
    /// returns a strong pointer to the binder. The result can be used to register
    /// the service with service manager.
    pub fn new_as_binder(
        node_impl: Arc<dyn DiceNodeImpl + Sync + Send>,
    ) -> Result<Strong<dyn IDiceNode>> {
        let result = BnDiceNode::new_binder(
            DiceNode { node_impl },
            BinderFeatures { set_requesting_sid: true, ..BinderFeatures::default() },
        );
        Ok(result)
    }

    fn sign(&self, input_values: &[BinderInputValues], message: &[u8]) -> Result<Signature> {
        check_caller_permission(Permission::UseSign).context("In DiceNode::sign:")?;
        let client =
            client_input_values(ThreadState::get_calling_uid()).context("In DiceNode::sign:")?;
        self.node_impl.sign(client, input_values, message)
    }
    fn get_attestation_chain(&self, input_values: &[BinderInputValues]) -> Result<Bcc> {
        check_caller_permission(Permission::GetAttestationChain)
            .context("In DiceNode::get_attestation_chain:")?;
        let client = client_input_values(ThreadState::get_calling_uid())
            .context("In DiceNode::get_attestation_chain:")?;
        self.node_impl.get_attestation_chain(client, input_values)
    }
    fn derive(&self, input_values: &[BinderInputValues]) -> Result<BccHandover> {
        check_caller_permission(Permission::Derive).context("In DiceNode::derive:")?;
        let client =
            client_input_values(ThreadState::get_calling_uid()).context("In DiceNode::extend:")?;
        self.node_impl.derive(client, input_values)
    }
    fn demote(&self, input_values: &[BinderInputValues]) -> Result<()> {
        check_caller_permission(Permission::Demote).context("In DiceNode::demote:")?;
        let client =
            client_input_values(ThreadState::get_calling_uid()).context("In DiceNode::demote:")?;
        self.node_impl.demote(client, input_values)
    }
}

impl binder::Interface for DiceNode {}

impl IDiceNode for DiceNode {
    fn sign(&self, input_values: &[BinderInputValues], message: &[u8]) -> BinderResult<Signature> {
        map_or_log_err(self.sign(input_values, message), Ok)
    }
    fn getAttestationChain(&self, input_values: &[BinderInputValues]) -> BinderResult<Bcc> {
        map_or_log_err(self.get_attestation_chain(input_values), Ok)
    }
    fn derive(&self, input_values: &[BinderInputValues]) -> BinderResult<BccHandover> {
        map_or_log_err(self.derive(input_values), Ok)
    }
    fn demote(&self, input_values: &[BinderInputValues]) -> BinderResult<()> {
        map_or_log_err(self.demote(input_values), Ok)
    }
}

/// Wraps a DiceNodeImpl and implements the IDiceMaintenance AIDL API.
pub struct DiceMaintenance {
    node_impl: Arc<dyn DiceNodeImpl + Sync + Send>,
}

impl DiceMaintenance {
    /// Constructs an instance of DiceMaintenance, wraps it with a BnDiceMaintenance object and
    /// returns a strong pointer to the binder. The result can be used to register the service
    /// with service manager.
    pub fn new_as_binder(
        node_impl: Arc<dyn DiceNodeImpl + Sync + Send>,
    ) -> Result<Strong<dyn IDiceMaintenance>> {
        let result = BnDiceMaintenance::new_binder(
            DiceMaintenance { node_impl },
            BinderFeatures { set_requesting_sid: true, ..BinderFeatures::default() },
        );
        Ok(result)
    }

    fn demote_self(&self, input_values: &[BinderInputValues]) -> Result<()> {
        check_caller_permission(Permission::DemoteSelf)
            .context("In DiceMaintenance::demote_self:")?;
        self.node_impl.demote_self(input_values)
    }
}

impl binder::Interface for DiceMaintenance {}

impl IDiceMaintenance for DiceMaintenance {
    fn demoteSelf(&self, input_values: &[BinderInputValues]) -> BinderResult<()> {
        map_or_log_err(self.demote_self(input_values), Ok)
    }
}
