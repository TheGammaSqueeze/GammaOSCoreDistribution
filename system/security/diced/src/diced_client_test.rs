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

use android_hardware_security_dice::aidl::android::hardware::security::dice::{
    Config::Config as BinderConfig, InputValues::InputValues as BinderInputValues,
    Mode::Mode as BinderMode,
};
use android_security_dice::aidl::android::security::dice::IDiceMaintenance::IDiceMaintenance;
use android_security_dice::aidl::android::security::dice::IDiceNode::IDiceNode;
use binder::Strong;
use diced_open_dice_cbor as dice;
use nix::libc::uid_t;
use std::convert::TryInto;

static DICE_NODE_SERVICE_NAME: &str = "android.security.dice.IDiceNode";
static DICE_MAINTENANCE_SERVICE_NAME: &str = "android.security.dice.IDiceMaintenance";

fn get_dice_node() -> Strong<dyn IDiceNode> {
    binder::get_interface(DICE_NODE_SERVICE_NAME).unwrap()
}

fn get_dice_maintenance() -> Strong<dyn IDiceMaintenance> {
    binder::get_interface(DICE_MAINTENANCE_SERVICE_NAME).unwrap()
}

static TEST_MESSAGE: &[u8] = &[
    // "My test message!"
    0x4d, 0x79, 0x20, 0x74, 0x65, 0x73, 0x74, 0x20, 0x6d, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x21,
    0x0a,
];

// This test calls derive with an empty argument vector and with a set of three input values.
// It then performs the same three derivation steps on the result of the former and compares
// the result to the result of the latter.
fn equivalence_test() {
    let node = get_dice_node();
    let input_values = diced_sample_inputs::get_input_values_vector();
    let former = node.derive(&[]).expect("Trying to call derive.");
    let latter = node.derive(&input_values).expect("Trying to call derive with input values.");
    let artifacts =
        diced_utils::ResidentArtifacts::new(&former.cdiAttest, &former.cdiSeal, &former.bcc.data)
            .unwrap();

    let input_values: Vec<diced_utils::InputValues> =
        input_values.iter().map(|v| v.into()).collect();

    let artifacts =
        artifacts.execute_steps(input_values.iter().map(|v| v as &dyn dice::InputValues)).unwrap();
    let (cdi_attest, cdi_seal, bcc) = artifacts.into_tuple();
    let from_former = diced_utils::make_bcc_handover(
        cdi_attest[..].try_into().unwrap(),
        cdi_seal[..].try_into().unwrap(),
        &bcc,
    )
    .unwrap();
    // TODO when we have a parser/verifier, check equivalence rather
    // than bit by bit equality.
    assert_eq!(latter, from_former);
}

fn sign_and_verify() {
    let node = get_dice_node();
    let _signature = node.sign(&[], TEST_MESSAGE).expect("Trying to call sign.");

    let _bcc = node.getAttestationChain(&[]).expect("Trying to call getAttestationChain.");
    // TODO b/204938506 check the signature with the bcc when the verifier is available.
}

// This test calls derive with an empty argument vector, then demotes the itself using
// a set of three input values, and then calls derive with empty argument vector again.
// It then performs the same three derivation steps on the result of the former and compares
// the result to the result of the latter.
fn demote_test() {
    let node = get_dice_node();
    let input_values = diced_sample_inputs::get_input_values_vector();
    let former = node.derive(&[]).expect("Trying to call derive.");
    node.demote(&input_values).expect("Trying to call demote with input values.");

    let latter = node.derive(&[]).expect("Trying to call derive after demote.");

    let artifacts = diced_utils::ResidentArtifacts::new(
        former.cdiAttest[..].try_into().unwrap(),
        former.cdiSeal[..].try_into().unwrap(),
        &former.bcc.data,
    )
    .unwrap();

    let input_values: Vec<diced_utils::InputValues> =
        input_values.iter().map(|v| v.into()).collect();

    let artifacts =
        artifacts.execute_steps(input_values.iter().map(|v| v as &dyn dice::InputValues)).unwrap();
    let (cdi_attest, cdi_seal, bcc) = artifacts.into_tuple();
    let from_former = diced_utils::make_bcc_handover(
        cdi_attest[..].try_into().unwrap(),
        cdi_seal[..].try_into().unwrap(),
        &bcc,
    )
    .unwrap();
    // TODO b/204938506 when we have a parser/verifier, check equivalence rather
    // than bit by bit equality.
    assert_eq!(latter, from_former);
}

fn client_input_values(uid: uid_t) -> BinderInputValues {
    BinderInputValues {
        codeHash: [0; dice::HASH_SIZE],
        config: BinderConfig {
            desc: dice::bcc::format_config_descriptor(Some(&format!("{}", uid)), None, true)
                .unwrap(),
        },
        authorityHash: [0; dice::HASH_SIZE],
        authorityDescriptor: None,
        mode: BinderMode::NORMAL,
        hidden: [0; dice::HIDDEN_SIZE],
    }
}

// This test calls derive with an empty argument vector `former` which look like this:
// <common root> | <caller>
// It then demotes diced using a set of three input values prefixed with the uid based input
// values that diced would add to any call. It then calls derive with empty argument vector
// again which will add another step using the identity of the caller. If diced was demoted
// correctly the chain of `latter` will
// look as follows:
// <common root> | <caller> | <the three sample inputs> | <caller>
//
// It then performs the same three derivation steps followed by a set of caller input values
// on `former` and compares it to `latter`.
fn demote_self_test() {
    let maintenance = get_dice_maintenance();
    let node = get_dice_node();
    let input_values = diced_sample_inputs::get_input_values_vector();
    let former = node.derive(&[]).expect("Trying to call derive.");

    let client = client_input_values(nix::unistd::getuid().into());

    let mut demote_vector = vec![client.clone()];
    demote_vector.append(&mut input_values.clone());
    maintenance.demoteSelf(&demote_vector).expect("Trying to call demote_self with input values.");

    let latter = node.derive(&[]).expect("Trying to call derive after demote.");

    let artifacts = diced_utils::ResidentArtifacts::new(
        former.cdiAttest[..].try_into().unwrap(),
        former.cdiSeal[..].try_into().unwrap(),
        &former.bcc.data,
    )
    .unwrap();

    let client = [client];
    let input_values: Vec<diced_utils::InputValues> =
        input_values.iter().chain(client.iter()).map(|v| v.into()).collect();

    let artifacts =
        artifacts.execute_steps(input_values.iter().map(|v| v as &dyn dice::InputValues)).unwrap();
    let (cdi_attest, cdi_seal, bcc) = artifacts.into_tuple();
    let from_former = diced_utils::make_bcc_handover(
        cdi_attest[..].try_into().unwrap(),
        cdi_seal[..].try_into().unwrap(),
        &bcc,
    )
    .unwrap();
    // TODO b/204938506 when we have a parser/verifier, check equivalence rather
    // than bit by bit equality.
    assert_eq!(latter, from_former);
}

#[test]
fn run_serialized_test() {
    equivalence_test();
    sign_and_verify();
    // The demote self test must run before the demote test or the test fails.
    // And since demotion is not reversible the test can only pass once per boot.
    demote_self_test();
    demote_test();
}
