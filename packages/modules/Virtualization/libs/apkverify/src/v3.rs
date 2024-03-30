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

//! Verifies APK Signature Scheme V3

// TODO(jooyung) remove this
#![allow(dead_code)]

use anyhow::{anyhow, bail, Context, Result};
use bytes::Bytes;
use ring::signature::{
    UnparsedPublicKey, VerificationAlgorithm, ECDSA_P256_SHA256_ASN1, RSA_PKCS1_2048_8192_SHA256,
    RSA_PKCS1_2048_8192_SHA512, RSA_PSS_2048_8192_SHA256, RSA_PSS_2048_8192_SHA512,
};
use std::fs::File;
use std::io::{Read, Seek};
use std::ops::Range;
use std::path::Path;
use x509_parser::{parse_x509_certificate, prelude::FromDer, x509::SubjectPublicKeyInfo};

use crate::bytes_ext::{BytesExt, LengthPrefixed, ReadFromBytes};
use crate::sigutil::*;

pub const APK_SIGNATURE_SCHEME_V3_BLOCK_ID: u32 = 0xf05368c0;

// TODO(jooyung): get "ro.build.version.sdk"
const SDK_INT: u32 = 31;

/// Data model for Signature Scheme V3
/// https://source.android.com/security/apksigning/v3#verification

type Signers = LengthPrefixed<Vec<LengthPrefixed<Signer>>>;

struct Signer {
    signed_data: LengthPrefixed<Bytes>, // not verified yet
    min_sdk: u32,
    max_sdk: u32,
    signatures: LengthPrefixed<Vec<LengthPrefixed<Signature>>>,
    public_key: LengthPrefixed<Bytes>,
}

impl Signer {
    fn sdk_range(&self) -> Range<u32> {
        self.min_sdk..self.max_sdk
    }
}

struct SignedData {
    digests: LengthPrefixed<Vec<LengthPrefixed<Digest>>>,
    certificates: LengthPrefixed<Vec<LengthPrefixed<X509Certificate>>>,
    min_sdk: u32,
    max_sdk: u32,
    additional_attributes: LengthPrefixed<Vec<LengthPrefixed<AdditionalAttributes>>>,
}

impl SignedData {
    fn sdk_range(&self) -> Range<u32> {
        self.min_sdk..self.max_sdk
    }
}

#[derive(Debug)]
struct Signature {
    signature_algorithm_id: u32,
    signature: LengthPrefixed<Bytes>,
}

struct Digest {
    signature_algorithm_id: u32,
    digest: LengthPrefixed<Bytes>,
}

type X509Certificate = Bytes;
type AdditionalAttributes = Bytes;

/// Verifies APK Signature Scheme v3 signatures of the provided APK and returns the public key
/// associated with the signer.
pub fn verify<P: AsRef<Path>>(path: P) -> Result<Box<[u8]>> {
    let f = File::open(path.as_ref())?;
    let mut sections = ApkSections::new(f)?;
    find_signer_and_then(&mut sections, |(signer, sections)| signer.verify(sections))
}

/// Finds the supported signer and execute a function on it.
fn find_signer_and_then<R, U, F>(sections: &mut ApkSections<R>, f: F) -> Result<U>
where
    R: Read + Seek,
    F: FnOnce((&Signer, &mut ApkSections<R>)) -> Result<U>,
{
    let mut block = sections.find_signature(APK_SIGNATURE_SCHEME_V3_BLOCK_ID)?;
    // parse v3 scheme block
    let signers = block.read::<Signers>()?;

    // find supported by platform
    let supported = signers.iter().filter(|s| s.sdk_range().contains(&SDK_INT)).collect::<Vec<_>>();

    // there should be exactly one
    if supported.len() != 1 {
        bail!(
            "APK Signature Scheme V3 only supports one signer: {} signers found.",
            supported.len()
        )
    }

    // Call the supplied function
    f((supported[0], sections))
}

/// Gets the public key (in DER format) that was used to sign the given APK/APEX file
pub fn get_public_key_der<P: AsRef<Path>>(path: P) -> Result<Box<[u8]>> {
    let f = File::open(path.as_ref())?;
    let mut sections = ApkSections::new(f)?;
    find_signer_and_then(&mut sections, |(signer, _)| {
        Ok(signer.public_key.to_vec().into_boxed_slice())
    })
}

impl Signer {
    fn verify<R: Read + Seek>(&self, sections: &mut ApkSections<R>) -> Result<Box<[u8]>> {
        // 1. Choose the strongest supported signature algorithm ID from signatures. The strength
        //    ordering is up to each implementation/platform version.
        let strongest: &Signature = self
            .signatures
            .iter()
            .filter(|sig| is_supported_signature_algorithm(sig.signature_algorithm_id))
            .max_by_key(|sig| rank_signature_algorithm(sig.signature_algorithm_id).unwrap())
            .ok_or_else(|| anyhow!("No supported signatures found"))?;

        // 2. Verify the corresponding signature from signatures against signed data using public key.
        //    (It is now safe to parse signed data.)
        let (_, key_info) = SubjectPublicKeyInfo::from_der(self.public_key.as_ref())?;
        verify_signed_data(&self.signed_data, strongest, &key_info)?;

        // It is now safe to parse signed data.
        let signed_data: SignedData = self.signed_data.slice(..).read()?;

        // 3. Verify the min and max SDK versions in the signed data match those specified for the
        //    signer.
        if self.sdk_range() != signed_data.sdk_range() {
            bail!("SDK versions mismatch between signed and unsigned in v3 signer block.");
        }

        // 4. Verify that the ordered list of signature algorithm IDs in digests and signatures is
        //    identical. (This is to prevent signature stripping/addition.)
        if !self
            .signatures
            .iter()
            .map(|sig| sig.signature_algorithm_id)
            .eq(signed_data.digests.iter().map(|dig| dig.signature_algorithm_id))
        {
            bail!("Signature algorithms don't match between digests and signatures records");
        }

        // 5. Compute the digest of APK contents using the same digest algorithm as the digest
        //    algorithm used by the signature algorithm.
        let digest = signed_data
            .digests
            .iter()
            .find(|&dig| dig.signature_algorithm_id == strongest.signature_algorithm_id)
            .unwrap(); // ok to unwrap since we check if two lists are the same above
        let computed = sections.compute_digest(digest.signature_algorithm_id)?;

        // 6. Verify that the computed digest is identical to the corresponding digest from digests.
        if computed != digest.digest.as_ref() {
            bail!(
                "Digest mismatch: computed={:?} vs expected={:?}",
                to_hex_string(&computed),
                to_hex_string(&digest.digest),
            );
        }

        // 7. Verify that SubjectPublicKeyInfo of the first certificate of certificates is identical
        //    to public key.
        let cert = signed_data.certificates.first().context("No certificates listed")?;
        let (_, cert) = parse_x509_certificate(cert.as_ref())?;
        if cert.tbs_certificate.subject_pki != key_info {
            bail!("Public key mismatch between certificate and signature record");
        }

        // TODO(jooyung) 8. If the proof-of-rotation attribute exists for the signer verify that the struct is valid and this signer is the last certificate in the list.
        Ok(self.public_key.to_vec().into_boxed_slice())
    }
}

fn verify_signed_data(
    data: &Bytes,
    signature: &Signature,
    key_info: &SubjectPublicKeyInfo,
) -> Result<()> {
    let verification_alg: &dyn VerificationAlgorithm = match signature.signature_algorithm_id {
        SIGNATURE_RSA_PSS_WITH_SHA256 => &RSA_PSS_2048_8192_SHA256,
        SIGNATURE_RSA_PSS_WITH_SHA512 => &RSA_PSS_2048_8192_SHA512,
        SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA256 | SIGNATURE_VERITY_RSA_PKCS1_V1_5_WITH_SHA256 => {
            &RSA_PKCS1_2048_8192_SHA256
        }
        SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA512 => &RSA_PKCS1_2048_8192_SHA512,
        SIGNATURE_ECDSA_WITH_SHA256 | SIGNATURE_VERITY_ECDSA_WITH_SHA256 => &ECDSA_P256_SHA256_ASN1,
        // TODO(b/190343842) not implemented signature algorithm
        SIGNATURE_ECDSA_WITH_SHA512
        | SIGNATURE_DSA_WITH_SHA256
        | SIGNATURE_VERITY_DSA_WITH_SHA256 => {
            bail!(
                "TODO(b/190343842) not implemented signature algorithm: {:#x}",
                signature.signature_algorithm_id
            );
        }
        _ => bail!("Unsupported signature algorithm: {:#x}", signature.signature_algorithm_id),
    };
    let key = UnparsedPublicKey::new(verification_alg, &key_info.subject_public_key);
    key.verify(data.as_ref(), signature.signature.as_ref())?;
    Ok(())
}

// ReadFromBytes implementations
// TODO(jooyung): add derive macro: #[derive(ReadFromBytes)]

impl ReadFromBytes for Signer {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        Ok(Self {
            signed_data: buf.read()?,
            min_sdk: buf.read()?,
            max_sdk: buf.read()?,
            signatures: buf.read()?,
            public_key: buf.read()?,
        })
    }
}

impl ReadFromBytes for SignedData {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        Ok(Self {
            digests: buf.read()?,
            certificates: buf.read()?,
            min_sdk: buf.read()?,
            max_sdk: buf.read()?,
            additional_attributes: buf.read()?,
        })
    }
}

impl ReadFromBytes for Signature {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        Ok(Signature { signature_algorithm_id: buf.read()?, signature: buf.read()? })
    }
}

impl ReadFromBytes for Digest {
    fn read_from_bytes(buf: &mut Bytes) -> Result<Self> {
        Ok(Self { signature_algorithm_id: buf.read()?, digest: buf.read()? })
    }
}

#[inline]
fn to_hex_string(buf: &[u8]) -> String {
    buf.iter().map(|b| format!("{:02X}", b)).collect()
}
