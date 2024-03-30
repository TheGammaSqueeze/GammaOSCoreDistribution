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

//! This module implements test utils to generate various types of keys.

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, Digest::Digest, EcCurve::EcCurve, KeyPurpose::KeyPurpose,
};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, IKeystoreSecurityLevel::IKeystoreSecurityLevel, KeyDescriptor::KeyDescriptor,
    KeyMetadata::KeyMetadata,
};

use crate::authorizations::AuthSetBuilder;

const SELINUX_SHELL_NAMESPACE: i64 = 1;

/// Generate attested EC Key blob using given security level with below key parameters -
///     Purposes: SIGN and VERIFY
///     Digest: SHA_2_256
///     Curve: P_256
pub fn generate_ec_p256_signing_key_with_attestation(
    sec_level: &binder::Strong<dyn IKeystoreSecurityLevel>,
) -> binder::Result<KeyMetadata> {
    let att_challenge: &[u8] = b"foo";
    let att_app_id: &[u8] = b"bar";
    let gen_params = AuthSetBuilder::new()
        .algorithm(Algorithm::EC)
        .purpose(KeyPurpose::SIGN)
        .purpose(KeyPurpose::VERIFY)
        .digest(Digest::SHA_2_256)
        .ec_curve(EcCurve::P_256)
        .attestation_challenge(att_challenge.to_vec())
        .attestation_app_id(att_app_id.to_vec());

    match sec_level.generateKey(
        &KeyDescriptor {
            domain: Domain::BLOB,
            nspace: SELINUX_SHELL_NAMESPACE,
            alias: None,
            blob: None,
        },
        None,
        &gen_params,
        0,
        b"entropy",
    ) {
        Ok(key_metadata) => {
            assert!(key_metadata.certificate.is_some());
            assert!(key_metadata.certificateChain.is_some());
            assert!(key_metadata.key.blob.is_some());

            Ok(key_metadata)
        }
        Err(e) => Err(e),
    }
}
