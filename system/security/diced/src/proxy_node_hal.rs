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

//! A proxy dice node delegates all accesses to CDI_attest and CDI_seal to a parent
//! node, here an implementation of android.hardware.security.dice.IDiceDevice.

#![allow(dead_code)]

use crate::DiceNodeImpl;
use android_hardware_security_dice::aidl::android::hardware::security::dice::{
    Bcc::Bcc, BccHandover::BccHandover, IDiceDevice::IDiceDevice,
    InputValues::InputValues as BinderInputValues, Signature::Signature,
};
use anyhow::{Context, Result};
use binder::Strong;
use std::collections::HashMap;
use std::sync::RwLock;

/// The ProxyNodeHal implements a IDiceNode backend delegating crypto operations
/// to the corresponding HAL.
pub struct ProxyNodeHal {
    parent: Strong<dyn IDiceDevice>,
    demotion_db: RwLock<HashMap<BinderInputValues, Vec<BinderInputValues>>>,
}

impl ProxyNodeHal {
    /// Creates a new proxy node with a reference to the parent service.
    pub fn new(parent: Strong<dyn IDiceDevice>) -> Result<Self> {
        Ok(ProxyNodeHal { parent, demotion_db: Default::default() })
    }

    fn get_effective_input_values(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Vec<BinderInputValues> {
        let demotion_db = self.demotion_db.read().unwrap();

        let client_arr = [client];

        demotion_db
            .get(&client_arr[0])
            .map(|v| v.iter())
            .unwrap_or_else(|| client_arr.iter())
            .chain(input_values.iter())
            .cloned()
            .collect()
    }
}

impl DiceNodeImpl for ProxyNodeHal {
    fn sign(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
        message: &[u8],
    ) -> Result<Signature> {
        self.parent
            .sign(&self.get_effective_input_values(client, input_values), message)
            .context("In ProxyNodeHal::sign:")
    }

    fn get_attestation_chain(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Result<Bcc> {
        self.parent
            .getAttestationChain(&self.get_effective_input_values(client, input_values))
            .context("In ProxyNodeHal::get_attestation_chain:")
    }

    fn derive(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Result<BccHandover> {
        self.parent
            .derive(&self.get_effective_input_values(client, input_values))
            .context("In ProxyNodeHal::derive:")
    }

    fn demote(&self, client: BinderInputValues, input_values: &[BinderInputValues]) -> Result<()> {
        let mut demotion_db = self.demotion_db.write().unwrap();

        let client_arr = [client];

        // The following statement consults demotion database which yields an optional demotion
        // path. It then constructs an iterator over the following elements, then clones and
        // collects them into a new vector:
        // [ demotion path | client ], input_values
        let new_path: Vec<BinderInputValues> = demotion_db
            .get(&client_arr[0])
            .map(|v| v.iter())
            .unwrap_or_else(|| client_arr.iter())
            .chain(input_values)
            .cloned()
            .collect();

        let [client] = client_arr;
        demotion_db.insert(client, new_path);
        Ok(())
    }

    fn demote_self(&self, input_values: &[BinderInputValues]) -> Result<()> {
        self.parent.demote(input_values).context("In ProxyNodeHal::demote_self:")
    }
}
