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

//! A resident dice node keeps CDI_attest and CDI_seal memory resident and can serve
//! its clients directly by performing all crypto operations including derivations and
//! certificate generation itself.

use crate::DiceNodeImpl;
use android_hardware_security_dice::aidl::android::hardware::security::dice::{
    Bcc::Bcc, BccHandover::BccHandover, InputValues::InputValues as BinderInputValues,
    Signature::Signature,
};
use anyhow::{Context, Result};
use dice::{ContextImpl, OpenDiceCborContext};
use diced_open_dice_cbor as dice;
use diced_utils::{self as utils, InputValues, ResidentArtifacts};
use std::collections::HashMap;
use std::convert::TryInto;
use std::sync::RwLock;

/// The ResidentNode implements a IDiceNode backend with memory resident DICE secrets.
pub struct ResidentNode {
    artifacts: RwLock<ResidentArtifacts>,
    demotion_db: RwLock<HashMap<BinderInputValues, Vec<BinderInputValues>>>,
}

impl ResidentNode {
    /// Creates a new Resident node with the given dice secrets and certificate chain.
    pub fn new(
        cdi_attest: &[u8; dice::CDI_SIZE],
        cdi_seal: &[u8; dice::CDI_SIZE],
        bcc: Vec<u8>,
    ) -> Result<Self> {
        Ok(ResidentNode {
            artifacts: RwLock::new(
                ResidentArtifacts::new(cdi_attest, cdi_seal, &bcc)
                    .context("In ResidentNode::new: Trying to initialize ResidentArtifacts")?,
            ),
            demotion_db: Default::default(),
        })
    }

    fn get_effective_artifacts(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Result<ResidentArtifacts> {
        let artifacts = self.artifacts.read().unwrap().try_clone()?;
        let demotion_db = self.demotion_db.read().unwrap();

        let client_arr = [client];

        let input_values: Vec<utils::InputValues> = demotion_db
            .get(&client_arr[0])
            .map(|v| v.iter())
            .unwrap_or_else(|| client_arr.iter())
            .chain(input_values.iter())
            .map(|v| v.into())
            .collect();

        artifacts
            .execute_steps(input_values.iter().map(|v| v as &dyn dice::InputValues))
            .context("In get_effective_artifacts:")
    }
}

impl DiceNodeImpl for ResidentNode {
    fn sign(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
        message: &[u8],
    ) -> Result<Signature> {
        let (cdi_attest, _, _) = self
            .get_effective_artifacts(client, input_values)
            .context("In ResidentNode::sign: Failed to get effective_artifacts.")?
            .into_tuple();
        let mut dice = OpenDiceCborContext::new();
        let seed = dice
            .derive_cdi_private_key_seed(cdi_attest[..].try_into().with_context(|| {
                format!(
                    "In ResidentNode::sign: Failed to convert cdi_attest (length: {}).",
                    cdi_attest.len()
                )
            })?)
            .context("In ResidentNode::sign: Failed to derive seed from cdi_attest.")?;
        let (_public_key, private_key) = dice
            .keypair_from_seed(seed[..].try_into().with_context(|| {
                format!("In ResidentNode::sign: Failed to convert seed (length: {}).", seed.len())
            })?)
            .context("In ResidentNode::sign: Failed to derive keypair from seed.")?;
        Ok(Signature {
            data: dice
                .sign(
                    message,
                    private_key[..].try_into().with_context(|| {
                        format!(
                            "In ResidentNode::sign: Failed to convert private_key (length: {}).",
                            private_key.len()
                        )
                    })?,
                )
                .context("In ResidentNode::sign: Failed to sign.")?,
        })
    }

    fn get_attestation_chain(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Result<Bcc> {
        let (_, _, bcc) = self
            .get_effective_artifacts(client, input_values)
            .context("In ResidentNode::get_attestation_chain: Failed to get effective_artifacts.")?
            .into_tuple();

        Ok(Bcc { data: bcc })
    }

    fn derive(
        &self,
        client: BinderInputValues,
        input_values: &[BinderInputValues],
    ) -> Result<BccHandover> {
        let (cdi_attest, cdi_seal, bcc) =
            self.get_effective_artifacts(client, input_values)?.into_tuple();

        utils::make_bcc_handover(
            &cdi_attest[..]
                .try_into()
                .context("In ResidentNode::derive: Trying to convert cdi_attest to sized array.")?,
            &cdi_seal[..]
                .try_into()
                .context("In ResidentNode::derive: Trying to convert cdi_attest to sized array.")?,
            &bcc,
        )
        .context("In ResidentNode::derive: Trying to format bcc handover.")
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
        let mut artifacts = self.artifacts.write().unwrap();

        let input_values = input_values
            .iter()
            .map(|v| {
                v.try_into().with_context(|| format!("Failed to convert input values: {:#?}", v))
            })
            .collect::<Result<Vec<InputValues>>>()
            .context("In ResidentNode::demote_self:")?;

        *artifacts = artifacts
            .try_clone()
            .context("In ResidentNode::demote_self: Failed to clone resident artifacts")?
            .execute_steps(input_values.iter().map(|v| v as &dyn dice::InputValues))
            .context("In ResidentNode::demote_self:")?;
        Ok(())
    }
}
