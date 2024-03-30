/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use apkverify::{testing::assert_contains, verify};
use std::matches;

#[test]
fn test_verify_v3() {
    assert!(verify("tests/data/test.apex").is_ok());
}

#[test]
fn test_verify_v3_digest_mismatch() {
    let res = verify("tests/data/v3-only-with-rsa-pkcs1-sha512-8192-digest-mismatch.apk");
    assert!(res.is_err());
    assert_contains(&res.unwrap_err().to_string(), "Digest mismatch");
}

#[test]
fn test_verify_v3_cert_and_public_key_mismatch() {
    let res = verify("tests/data/v3-only-cert-and-public-key-mismatch.apk");
    assert!(res.is_err());
    assert_contains(&res.unwrap_err().to_string(), "Public key mismatch");
}

#[test]
fn test_verify_truncated_cd() {
    use zip::result::ZipError;
    let res = verify("tests/data/v2-only-truncated-cd.apk");
    // TODO(jooyung): consider making a helper for err assertion
    assert!(matches!(
        res.unwrap_err().root_cause().downcast_ref::<ZipError>().unwrap(),
        ZipError::InvalidArchive(_),
    ));
}
