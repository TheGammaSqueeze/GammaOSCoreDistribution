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

//! This module implements test utils to create Autherizations.

use std::ops::Deref;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, Digest::Digest, EcCurve::EcCurve, KeyParameter::KeyParameter,
    KeyParameterValue::KeyParameterValue, KeyPurpose::KeyPurpose, Tag::Tag,
};

/// Helper struct to create set of Authorizations.
pub struct AuthSetBuilder(Vec<KeyParameter>);

impl Default for AuthSetBuilder {
    fn default() -> Self {
        Self::new()
    }
}

impl AuthSetBuilder {
    /// Creates new Authorizations list.
    pub fn new() -> Self {
        Self(Vec::new())
    }

    /// Add Purpose.
    pub fn purpose(mut self, p: KeyPurpose) -> Self {
        self.0.push(KeyParameter { tag: Tag::PURPOSE, value: KeyParameterValue::KeyPurpose(p) });
        self
    }

    /// Add Digest.
    pub fn digest(mut self, d: Digest) -> Self {
        self.0.push(KeyParameter { tag: Tag::DIGEST, value: KeyParameterValue::Digest(d) });
        self
    }

    /// Add Algorithm.
    pub fn algorithm(mut self, a: Algorithm) -> Self {
        self.0.push(KeyParameter { tag: Tag::ALGORITHM, value: KeyParameterValue::Algorithm(a) });
        self
    }

    /// Add EC-Curve.
    pub fn ec_curve(mut self, e: EcCurve) -> Self {
        self.0.push(KeyParameter { tag: Tag::EC_CURVE, value: KeyParameterValue::EcCurve(e) });
        self
    }

    /// Add Attestation-Challenge.
    pub fn attestation_challenge(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_CHALLENGE,
            value: KeyParameterValue::Blob(b),
        });
        self
    }

    /// Add Attestation-ID.
    pub fn attestation_app_id(mut self, b: Vec<u8>) -> Self {
        self.0.push(KeyParameter {
            tag: Tag::ATTESTATION_APPLICATION_ID,
            value: KeyParameterValue::Blob(b),
        });
        self
    }
}

impl Deref for AuthSetBuilder {
    type Target = Vec<KeyParameter>;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}
