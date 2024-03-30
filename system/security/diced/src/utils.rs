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

//! Implements utility functions and types for diced and the dice HAL.

use android_hardware_security_dice::aidl::android::hardware::security::dice::{
    Bcc::Bcc, BccHandover::BccHandover, InputValues::InputValues as BinderInputValues,
    Mode::Mode as BinderMode,
};
use anyhow::{Context, Result};
use dice::ContextImpl;
use diced_open_dice_cbor as dice;
use keystore2_crypto::ZVec;
use std::convert::TryInto;

/// This new type wraps a reference to BinderInputValues and implements the open dice
/// InputValues trait.
#[derive(Debug)]
pub struct InputValues<'a>(&'a BinderInputValues);

impl<'a> From<&'a BinderInputValues> for InputValues<'a> {
    fn from(input_values: &'a BinderInputValues) -> InputValues<'a> {
        Self(input_values)
    }
}

impl From<&InputValues<'_>> for BinderInputValues {
    fn from(input_values: &InputValues) -> BinderInputValues {
        input_values.0.clone()
    }
}
impl From<InputValues<'_>> for BinderInputValues {
    fn from(input_values: InputValues) -> BinderInputValues {
        input_values.0.clone()
    }
}

impl dice::InputValues for InputValues<'_> {
    fn code_hash(&self) -> &[u8; dice::HASH_SIZE] {
        &self.0.codeHash
    }

    fn config(&self) -> dice::Config {
        dice::Config::Descriptor(self.0.config.desc.as_slice())
    }

    fn authority_hash(&self) -> &[u8; dice::HASH_SIZE] {
        &self.0.authorityHash
    }

    fn authority_descriptor(&self) -> Option<&[u8]> {
        self.0.authorityDescriptor.as_deref()
    }

    fn mode(&self) -> dice::Mode {
        match self.0.mode {
            BinderMode::NOT_INITIALIZED => dice::Mode::NotConfigured,
            BinderMode::NORMAL => dice::Mode::Normal,
            BinderMode::DEBUG => dice::Mode::Debug,
            BinderMode::RECOVERY => dice::Mode::Recovery,
            _ => dice::Mode::NotConfigured,
        }
    }

    fn hidden(&self) -> &[u8; dice::HIDDEN_SIZE] {
        // If `self` was created using try_from the length was checked and this cannot panic.
        &self.0.hidden
    }
}

/// Initializes an aidl defined BccHandover object with the arguments `cdi_attest`, `cdi_seal`,
/// and `bcc`.
pub fn make_bcc_handover(
    cdi_attest: &[u8; dice::CDI_SIZE],
    cdi_seal: &[u8; dice::CDI_SIZE],
    bcc: &[u8],
) -> Result<BccHandover> {
    Ok(BccHandover { cdiAttest: *cdi_attest, cdiSeal: *cdi_seal, bcc: Bcc { data: bcc.to_vec() } })
}

/// ResidentArtifacts stores a set of dice artifacts comprising CDI_ATTEST, CDI_SEAL,
/// and the BCC formatted attestation certificate chain. The sensitive secrets are
/// stored in zeroing vectors, and it implements functionality to perform DICE
/// derivation steps using libopen-dice-cbor.
pub struct ResidentArtifacts {
    cdi_attest: ZVec,
    cdi_seal: ZVec,
    bcc: Vec<u8>,
}

impl ResidentArtifacts {
    /// Create a ResidentArtifacts object. The parameters ensure that the stored secrets
    /// can only have the appropriate size, so that subsequent casts to array references
    /// cannot fail.
    pub fn new(
        cdi_attest: &[u8; dice::CDI_SIZE],
        cdi_seal: &[u8; dice::CDI_SIZE],
        bcc: &[u8],
    ) -> Result<Self> {
        Ok(ResidentArtifacts {
            cdi_attest: cdi_attest[..]
                .try_into()
                .context("In ResidentArtifacts::new: Trying to convert cdi_attest to ZVec.")?,
            cdi_seal: cdi_seal[..]
                .try_into()
                .context("In ResidentArtifacts::new: Trying to convert cdi_seal to ZVec.")?,
            bcc: bcc.to_vec(),
        })
    }

    /// Creates a ResidentArtifacts object from another one implementing the DiceArtifacts
    /// trait. Like `new` this function can only create artifacts of appropriate size
    /// because DiceArtifacts returns array references of appropriate size.
    pub fn new_from<T: DiceArtifacts + ?Sized>(artifacts: &T) -> Result<Self> {
        Ok(ResidentArtifacts {
            cdi_attest: artifacts.cdi_attest()[..].try_into()?,
            cdi_seal: artifacts.cdi_seal()[..].try_into()?,
            bcc: artifacts.bcc(),
        })
    }

    /// Attempts to clone the artifacts. This operation is fallible due to the fallible
    /// nature of ZVec.
    pub fn try_clone(&self) -> Result<Self> {
        Ok(ResidentArtifacts {
            cdi_attest: self
                .cdi_attest
                .try_clone()
                .context("In ResidentArtifacts::new: Trying to clone cdi_attest.")?,
            cdi_seal: self
                .cdi_seal
                .try_clone()
                .context("In ResidentArtifacts::new: Trying to clone cdi_seal.")?,
            bcc: self.bcc.clone(),
        })
    }

    /// Deconstruct the Artifacts into a tuple.
    /// (CDI_ATTEST, CDI_SEAL, BCC)
    pub fn into_tuple(self) -> (ZVec, ZVec, Vec<u8>) {
        let ResidentArtifacts { cdi_attest, cdi_seal, bcc } = self;
        (cdi_attest, cdi_seal, bcc)
    }

    fn execute_step(self, input_values: &dyn dice::InputValues) -> Result<Self> {
        let ResidentArtifacts { cdi_attest, cdi_seal, bcc } = self;

        let (cdi_attest, cdi_seal, bcc) = dice::OpenDiceCborContext::new()
            .bcc_main_flow(
                cdi_attest[..].try_into().with_context(|| {
                    format!("Trying to convert cdi_attest. (length: {})", cdi_attest.len())
                })?,
                cdi_seal[..].try_into().with_context(|| {
                    format!("Trying to convert cdi_seal. (length: {})", cdi_seal.len())
                })?,
                &bcc,
                input_values,
            )
            .context("In ResidentArtifacts::execute_step:")?;
        Ok(ResidentArtifacts { cdi_attest, cdi_seal, bcc })
    }

    /// Iterate through the iterator of dice input values performing one
    /// BCC main flow step on each element.
    pub fn execute_steps<'a, Iter>(self, input_values: Iter) -> Result<Self>
    where
        Iter: IntoIterator<Item = &'a dyn dice::InputValues>,
    {
        input_values
            .into_iter()
            .try_fold(self, |acc, input_values| acc.execute_step(input_values))
            .context("In ResidentArtifacts::execute_step:")
    }
}

/// An object that implements this trait provides the typical DICE artifacts.
/// CDI_ATTEST, CDI_SEAL, and a certificate chain up to the public key that
/// can be derived from CDI_ATTEST. Implementations should check the length of
/// the stored CDI_* secrets on creation so that any valid instance returns the
/// correct secrets in an infallible way.
pub trait DiceArtifacts {
    /// Returns CDI_ATTEST.
    fn cdi_attest(&self) -> &[u8; dice::CDI_SIZE];
    /// Returns CDI_SEAL.
    fn cdi_seal(&self) -> &[u8; dice::CDI_SIZE];
    /// Returns the attestation certificate chain in BCC format.
    fn bcc(&self) -> Vec<u8>;
}

/// Implement this trait to provide read and write access to a secure artifact
/// storage that can be used by the ResidentHal implementation.
pub trait UpdatableDiceArtifacts {
    /// With artifacts provides access to the stored artifacts for the duration
    /// of the function call by means of calling the callback.
    fn with_artifacts<F, T>(&self, f: F) -> Result<T>
    where
        F: FnOnce(&dyn DiceArtifacts) -> Result<T>;

    /// Consumes the object and returns a an updated version of itself.
    fn update(self, new_artifacts: &impl DiceArtifacts) -> Result<Self>
    where
        Self: Sized;
}

impl DiceArtifacts for ResidentArtifacts {
    fn cdi_attest(&self) -> &[u8; dice::CDI_SIZE] {
        self.cdi_attest[..].try_into().unwrap()
    }
    fn cdi_seal(&self) -> &[u8; dice::CDI_SIZE] {
        self.cdi_seal[..].try_into().unwrap()
    }
    fn bcc(&self) -> Vec<u8> {
        self.bcc.clone()
    }
}

/// This submodule implements a limited set of CBOR generation functionality. Essentially,
/// a cbor header generator and some convenience functions for number and BSTR encoding.
pub mod cbor {
    use anyhow::{anyhow, Context, Result};
    use std::convert::TryInto;
    use std::io::Write;

    /// CBOR encodes a positive number.
    pub fn encode_number(n: u64, buffer: &mut dyn Write) -> Result<()> {
        encode_header(0, n, buffer)
    }

    /// CBOR encodes a binary string.
    pub fn encode_bstr(bstr: &[u8], buffer: &mut dyn Write) -> Result<()> {
        encode_header(
            2,
            bstr.len().try_into().context("In encode_bstr: Failed to convert usize to u64.")?,
            buffer,
        )
        .context("In encode_bstr: While writing header.")?;
        let written = buffer.write(bstr).context("In encode_bstr: While writing payload.")?;
        if written != bstr.len() {
            return Err(anyhow!("In encode_bstr: Buffer too small. ({}, {})", written, bstr.len()));
        }
        Ok(())
    }

    /// Formats a CBOR header. `t` is the type, and n is the header argument.
    pub fn encode_header(t: u8, n: u64, buffer: &mut dyn Write) -> Result<()> {
        match n {
            n if n < 24 => {
                let written = buffer
                    .write(&u8::to_be_bytes(((t as u8) << 5) | (n as u8 & 0x1F)))
                    .with_context(|| {
                    format!("In encode_header: Failed to write header ({}, {})", t, n)
                })?;
                if written != 1 {
                    return Err(anyhow!("In encode_header: Buffer to small. ({}, {})", t, n));
                }
            }
            n if n <= 0xFF => {
                let written =
                    buffer.write(&u8::to_be_bytes(((t as u8) << 5) | (24u8 & 0x1F))).with_context(
                        || format!("In encode_header: Failed to write header ({}, {})", t, n),
                    )?;
                if written != 1 {
                    return Err(anyhow!("In encode_header: Buffer to small. ({}, {})", t, n));
                }
                let written = buffer.write(&u8::to_be_bytes(n as u8)).with_context(|| {
                    format!("In encode_header: Failed to write size ({}, {})", t, n)
                })?;
                if written != 1 {
                    return Err(anyhow!(
                        "In encode_header while writing size: Buffer to small. ({}, {})",
                        t,
                        n
                    ));
                }
            }
            n if n <= 0xFFFF => {
                let written =
                    buffer.write(&u8::to_be_bytes(((t as u8) << 5) | (25u8 & 0x1F))).with_context(
                        || format!("In encode_header: Failed to write header ({}, {})", t, n),
                    )?;
                if written != 1 {
                    return Err(anyhow!("In encode_header: Buffer to small. ({}, {})", t, n));
                }
                let written = buffer.write(&u16::to_be_bytes(n as u16)).with_context(|| {
                    format!("In encode_header: Failed to write size ({}, {})", t, n)
                })?;
                if written != 2 {
                    return Err(anyhow!(
                        "In encode_header while writing size: Buffer to small. ({}, {})",
                        t,
                        n
                    ));
                }
            }
            n if n <= 0xFFFFFFFF => {
                let written =
                    buffer.write(&u8::to_be_bytes(((t as u8) << 5) | (26u8 & 0x1F))).with_context(
                        || format!("In encode_header: Failed to write header ({}, {})", t, n),
                    )?;
                if written != 1 {
                    return Err(anyhow!("In encode_header: Buffer to small. ({}, {})", t, n));
                }
                let written = buffer.write(&u32::to_be_bytes(n as u32)).with_context(|| {
                    format!("In encode_header: Failed to write size ({}, {})", t, n)
                })?;
                if written != 4 {
                    return Err(anyhow!(
                        "In encode_header while writing size: Buffer to small. ({}, {})",
                        t,
                        n
                    ));
                }
            }
            n => {
                let written =
                    buffer.write(&u8::to_be_bytes(((t as u8) << 5) | (27u8 & 0x1F))).with_context(
                        || format!("In encode_header: Failed to write header ({}, {})", t, n),
                    )?;
                if written != 1 {
                    return Err(anyhow!("In encode_header: Buffer to small. ({}, {})", t, n));
                }
                let written = buffer.write(&u64::to_be_bytes(n as u64)).with_context(|| {
                    format!("In encode_header: Failed to write size ({}, {})", t, n)
                })?;
                if written != 8 {
                    return Err(anyhow!(
                        "In encode_header while writing size: Buffer to small. ({}, {})",
                        t,
                        n
                    ));
                }
            }
        }
        Ok(())
    }

    #[cfg(test)]
    mod test {
        use super::*;

        fn encode_header_helper(t: u8, n: u64) -> Vec<u8> {
            let mut b: Vec<u8> = vec![];
            encode_header(t, n, &mut b).unwrap();
            b
        }

        #[test]
        fn encode_header_test() {
            assert_eq!(&encode_header_helper(0, 0), &[0b000_00000]);
            assert_eq!(&encode_header_helper(0, 23), &[0b000_10111]);
            assert_eq!(&encode_header_helper(0, 24), &[0b000_11000, 24]);
            assert_eq!(&encode_header_helper(0, 0xff), &[0b000_11000, 0xff]);
            assert_eq!(&encode_header_helper(0, 0x100), &[0b000_11001, 0x01, 0x00]);
            assert_eq!(&encode_header_helper(0, 0xffff), &[0b000_11001, 0xff, 0xff]);
            assert_eq!(&encode_header_helper(0, 0x10000), &[0b000_11010, 0x00, 0x01, 0x00, 0x00]);
            assert_eq!(
                &encode_header_helper(0, 0xffffffff),
                &[0b000_11010, 0xff, 0xff, 0xff, 0xff]
            );
            assert_eq!(
                &encode_header_helper(0, 0x100000000),
                &[0b000_11011, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00]
            );
            assert_eq!(
                &encode_header_helper(0, 0xffffffffffffffff),
                &[0b000_11011, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff]
            );
        }
    }
}
