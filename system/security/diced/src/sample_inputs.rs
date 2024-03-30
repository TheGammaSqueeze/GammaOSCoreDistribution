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

//! This module provides a set of sample input values for a DICE chain, a sample UDS,
//! as well as tuple of CDIs and BCC derived thereof.

use android_hardware_security_dice::aidl::android::hardware::security::dice::{
    Config::Config as BinderConfig, InputValues::InputValues as BinderInputValues, Mode::Mode,
};
use anyhow::{Context, Result};
use dice::ContextImpl;
use diced_open_dice_cbor as dice;
use diced_utils::cbor;
use diced_utils::InputValues;
use keystore2_crypto::ZVec;
use std::convert::{TryFrom, TryInto};
use std::io::Write;

/// Sample UDS used to perform the root dice flow by `make_sample_bcc_and_cdis`.
pub static UDS: &[u8; dice::CDI_SIZE] = &[
    0x65, 0x4f, 0xab, 0xa9, 0xa5, 0xad, 0x0f, 0x5e, 0x15, 0xc3, 0x12, 0xf7, 0x77, 0x45, 0xfa, 0x55,
    0x18, 0x6a, 0xa6, 0x34, 0xb6, 0x7c, 0x82, 0x7b, 0x89, 0x4c, 0xc5, 0x52, 0xd3, 0x27, 0x35, 0x8e,
];

fn encode_pub_key_ed25519(pub_key: &[u8], stream: &mut dyn Write) -> Result<()> {
    cbor::encode_header(5 /* CBOR MAP */, 5, stream)
        .context("In encode_pub_key_ed25519: Trying to encode map header.")?;
    cbor::encode_number(1, stream)
        .context("In encode_pub_key_ed25519: Trying to encode Key type tag.")?;
    cbor::encode_number(1, stream)
        .context("In encode_pub_key_ed25519: Trying to encode Key type.")?;
    cbor::encode_number(3, stream)
        .context("In encode_pub_key_ed25519: Trying to encode algorithm tag.")?;
    // Encoding a -8 for AlgorithmEdDSA. The encoded number is -1 - <header argument>,
    // the an argument of 7 below.
    cbor::encode_header(1 /* CBOR NEGATIVE INT */, 7 /* -1 -7 = -8*/, stream)
        .context("In encode_pub_key_ed25519: Trying to encode algorithm.")?;
    cbor::encode_number(4, stream)
        .context("In encode_pub_key_ed25519: Trying to encode ops tag.")?;
    // Ops 2 for verify.
    cbor::encode_number(2, stream).context("In encode_pub_key_ed25519: Trying to encode ops.")?;
    cbor::encode_header(1 /* CBOR NEGATIVE INT */, 0 /* -1 -0 = -1*/, stream)
        .context("In encode_pub_key_ed25519: Trying to encode curve tag.")?;
    // Curve 6 for Ed25519
    cbor::encode_number(6, stream).context("In encode_pub_key_ed25519: Trying to encode curve.")?;
    cbor::encode_header(1 /* CBOR NEGATIVE INT */, 1 /* -1 -1 = -2*/, stream)
        .context("In encode_pub_key_ed25519: Trying to encode X coordinate tag.")?;
    cbor::encode_bstr(pub_key, stream)
        .context("In encode_pub_key_ed25519: Trying to encode X coordinate.")?;
    Ok(())
}

/// Derives a tuple of (CDI_ATTEST, CDI_SEAL, BCC) derived of the vector of input values returned
/// by `get_input_values_vector`.
pub fn make_sample_bcc_and_cdis() -> Result<(ZVec, ZVec, Vec<u8>)> {
    let mut dice_ctx = dice::OpenDiceCborContext::new();
    let private_key_seed = dice_ctx
        .derive_cdi_private_key_seed(UDS)
        .context("In make_sample_bcc_and_cdis: Trying to derive private key seed.")?;

    let (public_key, _) =
        dice_ctx
            .keypair_from_seed(&private_key_seed[..].try_into().context(
                "In make_sample_bcc_and_cids: Failed to convert seed to array reference.",
            )?)
            .context("In make_sample_bcc_and_cids: Failed to generate key pair.")?;

    let input_values_vector = get_input_values_vector();

    let (cdi_attest, cdi_seal, mut cert) = dice_ctx
        .main_flow(
            UDS,
            UDS,
            &InputValues::try_from(&input_values_vector[0])
                .context("In make_sample_bcc_and_cdis: Trying to convert input values. (0)")?,
        )
        .context("In make_sample_bcc_and_cdis: Trying to run first main flow.")?;

    let mut bcc: Vec<u8> = vec![];

    cbor::encode_header(4 /* CBOR ARRAY */, 2, &mut bcc)
        .context("In make_sample_bcc_and_cdis: Trying to encode array header.")?;
    encode_pub_key_ed25519(&public_key, &mut bcc)
        .context("In make_sample_bcc_and_cdis: Trying encode pub_key.")?;

    bcc.append(&mut cert);

    let (cdi_attest, cdi_seal, bcc) = dice_ctx
        .bcc_main_flow(
            &cdi_attest[..].try_into().context(
                "In make_sample_bcc_and_cdis: Failed to convert cdi_attest to array reference. (1)",
            )?,
            &cdi_seal[..].try_into().context(
                "In make_sample_bcc_and_cdis: Failed to convert cdi_seal to array reference. (1)",
            )?,
            &bcc,
            &InputValues::try_from(&input_values_vector[1])
                .context("In make_sample_bcc_and_cdis: Trying to convert input values. (1)")?,
        )
        .context("In make_sample_bcc_and_cdis: Trying to run first bcc main flow.")?;
    dice_ctx
        .bcc_main_flow(
            &cdi_attest[..].try_into().context(
                "In make_sample_bcc_and_cdis: Failed to convert cdi_attest to array reference. (2)",
            )?,
            &cdi_seal[..].try_into().context(
                "In make_sample_bcc_and_cdis: Failed to convert cdi_seal to array reference. (2)",
            )?,
            &bcc,
            &InputValues::try_from(&input_values_vector[2])
                .context("In make_sample_bcc_and_cdis: Trying to convert input values. (2)")?,
        )
        .context("In make_sample_bcc_and_cdis: Trying to run second bcc main flow.")
}

fn make_input_values(
    code_hash: &[u8; dice::HASH_SIZE],
    authority_hash: &[u8; dice::HASH_SIZE],
    config_name: &str,
    config_version: u64,
    config_resettable: bool,
    mode: Mode,
    hidden: &[u8; dice::HIDDEN_SIZE],
) -> Result<BinderInputValues> {
    Ok(BinderInputValues {
        codeHash: *code_hash,
        config: BinderConfig {
            desc: dice::bcc::format_config_descriptor(
                Some(config_name),
                Some(config_version),
                config_resettable,
            )
            .context("In make_input_values: Failed to format config descriptor.")?,
        },
        authorityHash: *authority_hash,
        authorityDescriptor: None,
        hidden: *hidden,
        mode,
    })
}

/// Returns a set of sample input for a dice chain comprising the android boot loader ABL,
/// the verified boot information AVB, and Android S.
pub fn get_input_values_vector() -> Vec<BinderInputValues> {
    vec![
        make_input_values(
            &[
                // code hash
                0x16, 0x48, 0xf2, 0x55, 0x53, 0x23, 0xdd, 0x15, 0x2e, 0x83, 0x38, 0xc3, 0x64, 0x38,
                0x63, 0x26, 0x0f, 0xcf, 0x5b, 0xd1, 0x3a, 0xd3, 0x40, 0x3e, 0x23, 0xf8, 0x34, 0x4c,
                0x6d, 0xa2, 0xbe, 0x25, 0x1c, 0xb0, 0x29, 0xe8, 0xc3, 0xfb, 0xb8, 0x80, 0xdc, 0xb1,
                0xd2, 0xb3, 0x91, 0x4d, 0xd3, 0xfb, 0x01, 0x0f, 0xe4, 0xe9, 0x46, 0xa2, 0xc0, 0x26,
                0x57, 0x5a, 0xba, 0x30, 0xf7, 0x15, 0x98, 0x14,
            ],
            &[
                // authority hash
                0xf9, 0x00, 0x9d, 0xc2, 0x59, 0x09, 0xe0, 0xb6, 0x98, 0xbd, 0xe3, 0x97, 0x4a, 0xcb,
                0x3c, 0xe7, 0x6b, 0x24, 0xc3, 0xe4, 0x98, 0xdd, 0xa9, 0x6a, 0x41, 0x59, 0x15, 0xb1,
                0x23, 0xe6, 0xc8, 0xdf, 0xfb, 0x52, 0xb4, 0x52, 0xc1, 0xb9, 0x61, 0xdd, 0xbc, 0x5b,
                0x37, 0x0e, 0x12, 0x12, 0xb2, 0xfd, 0xc1, 0x09, 0xb0, 0xcf, 0x33, 0x81, 0x4c, 0xc6,
                0x29, 0x1b, 0x99, 0xea, 0xae, 0xfd, 0xaa, 0x0d,
            ],
            "ABL", // config name
            1,     // config version
            true,  // resettable
            Mode::NORMAL,
            &[
                // hidden
                0xa2, 0x01, 0xd0, 0xc0, 0xaa, 0x75, 0x3c, 0x06, 0x43, 0x98, 0x6c, 0xc3, 0x5a, 0xb5,
                0x5f, 0x1f, 0x0f, 0x92, 0x44, 0x3b, 0x0e, 0xd4, 0x29, 0x75, 0xe3, 0xdb, 0x36, 0xda,
                0xc8, 0x07, 0x97, 0x4d, 0xff, 0xbc, 0x6a, 0xa4, 0x8a, 0xef, 0xc4, 0x7f, 0xf8, 0x61,
                0x7d, 0x51, 0x4d, 0x2f, 0xdf, 0x7e, 0x8c, 0x3d, 0xa3, 0xfc, 0x63, 0xd4, 0xd4, 0x74,
                0x8a, 0xc4, 0x14, 0x45, 0x83, 0x6b, 0x12, 0x7e,
            ],
        )
        .unwrap(),
        make_input_values(
            &[
                // code hash
                0xa4, 0x0c, 0xcb, 0xc1, 0xbf, 0xfa, 0xcc, 0xfd, 0xeb, 0xf4, 0xfc, 0x43, 0x83, 0x7f,
                0x46, 0x8d, 0xd8, 0xd8, 0x14, 0xc1, 0x96, 0x14, 0x1f, 0x6e, 0xb3, 0xa0, 0xd9, 0x56,
                0xb3, 0xbf, 0x2f, 0xfa, 0x88, 0x70, 0x11, 0x07, 0x39, 0xa4, 0xd2, 0xa9, 0x6b, 0x18,
                0x28, 0xe8, 0x29, 0x20, 0x49, 0x0f, 0xbb, 0x8d, 0x08, 0x8c, 0xc6, 0x54, 0xe9, 0x71,
                0xd2, 0x7e, 0xa4, 0xfe, 0x58, 0x7f, 0xd3, 0xc7,
            ],
            &[
                // authority hash
                0xb2, 0x69, 0x05, 0x48, 0x56, 0xb5, 0xfa, 0x55, 0x6f, 0xac, 0x56, 0xd9, 0x02, 0x35,
                0x2b, 0xaa, 0x4c, 0xba, 0x28, 0xdd, 0x82, 0x3a, 0x86, 0xf5, 0xd4, 0xc2, 0xf1, 0xf9,
                0x35, 0x7d, 0xe4, 0x43, 0x13, 0xbf, 0xfe, 0xd3, 0x36, 0xd8, 0x1c, 0x12, 0x78, 0x5c,
                0x9c, 0x3e, 0xf6, 0x66, 0xef, 0xab, 0x3d, 0x0f, 0x89, 0xa4, 0x6f, 0xc9, 0x72, 0xee,
                0x73, 0x43, 0x02, 0x8a, 0xef, 0xbc, 0x05, 0x98,
            ],
            "AVB", // config name
            1,     // config version
            true,  // resettable
            Mode::NORMAL,
            &[
                // hidden
                0x5b, 0x3f, 0xc9, 0x6b, 0xe3, 0x95, 0x59, 0x40, 0x5e, 0x64, 0xe5, 0x64, 0x3f, 0xfd,
                0x21, 0x09, 0x9d, 0xf3, 0xcd, 0xc7, 0xa4, 0x2a, 0xe2, 0x97, 0xdd, 0xe2, 0x4f, 0xb0,
                0x7d, 0x7e, 0xf5, 0x8e, 0xd6, 0x4d, 0x84, 0x25, 0x54, 0x41, 0x3f, 0x8f, 0x78, 0x64,
                0x1a, 0x51, 0x27, 0x9d, 0x55, 0x8a, 0xe9, 0x90, 0x35, 0xab, 0x39, 0x80, 0x4b, 0x94,
                0x40, 0x84, 0xa2, 0xfd, 0x73, 0xeb, 0x35, 0x7a,
            ],
        )
        .unwrap(),
        make_input_values(
            &[
                // code hash
                0; dice::HASH_SIZE
            ],
            &[
                // authority hash
                0x04, 0x25, 0x5d, 0x60, 0x5f, 0x5c, 0x45, 0x0d, 0xf2, 0x9a, 0x6e, 0x99, 0x30, 0x03,
                0xb8, 0xd6, 0xe1, 0x99, 0x71, 0x1b, 0xf8, 0x44, 0xfa, 0xb5, 0x31, 0x79, 0x1c, 0x37,
                0x68, 0x4e, 0x1d, 0xc0, 0x24, 0x74, 0x68, 0xf8, 0x80, 0x20, 0x3e, 0x44, 0xb1, 0x43,
                0xd2, 0x9c, 0xfc, 0x12, 0x9e, 0x77, 0x0a, 0xde, 0x29, 0x24, 0xff, 0x2e, 0xfa, 0xc7,
                0x10, 0xd5, 0x73, 0xd4, 0xc6, 0xdf, 0x62, 0x9f,
            ],
            "Android", // config name
            12,        // config version
            true,      // resettable
            Mode::NORMAL,
            &[
                // hidden
                0; dice::HIDDEN_SIZE
            ],
        )
        .unwrap(),
    ]
}

#[cfg(test)]
mod test {
    use super::*;

    // This simple test checks if the invocation succeeds, essentially it tests
    // if the initial bcc is accepted by `DiceContext::bcc_main_flow`.
    #[test]
    fn make_sample_bcc_and_cdis_test() {
        make_sample_bcc_and_cdis().unwrap();
    }
}
