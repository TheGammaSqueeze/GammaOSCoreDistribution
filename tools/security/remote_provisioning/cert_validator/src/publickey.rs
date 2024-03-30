//! This module describes the public key (PubKeyEd25519 or PubKeyECDSA256)
//! used in the BccPayload. The key itself is stored as a simple byte array in
//! a vector. For now, only PubKeyEd25519 types of cbor public keys are supported.

use crate::bcc::get_label_value_as_bytes;
use anyhow::{bail, ensure, Context, Result};
use coset::{iana, Algorithm, CoseKey};
use std::ptr;

/// Length of an Ed25519 public key.
pub const ED25519_PUBLIC_KEY_LEN: usize = ssl_bindgen::ED25519_PUBLIC_KEY_LEN as usize;
/// Length of an Ed25519 signatures.
pub const ED25519_SIG_LEN: usize = ssl_bindgen::ED25519_SIGNATURE_LEN as usize;
/// Length of a P256 coordinate.
pub const P256_COORD_LEN: usize = 32;
/// Length of a P256 signature.
pub const P256_SIG_LEN: usize = 64;

enum PubKey {
    Ed25519 { pub_key: [u8; ED25519_PUBLIC_KEY_LEN] },
    P256 { x_coord: [u8; P256_COORD_LEN], y_coord: [u8; P256_COORD_LEN] },
}
/// Struct wrapping the public key byte array, and the relevant validation methods.
pub struct PublicKey {
    key: PubKey,
}

impl PublicKey {
    /// Extract the PublicKey from Subject Public Key.
    /// (CertificateRequest.BccEntry.payload[SubjectPublicKey].X)
    pub fn from_cose_key(pkey: &CoseKey) -> Result<Self> {
        let x = get_label_value_as_bytes(pkey, iana::OkpKeyParameter::X as i64)?;
        match pkey.alg {
            Some(coset::Algorithm::Assigned(iana::Algorithm::EdDSA)) => {
                PublicKey::new(PubKey::Ed25519 {
                    pub_key: x.as_slice().try_into().context(format!(
                        "Failed to convert x_coord to array. Len: {:?}",
                        x.len()
                    ))?,
                })
            }
            Some(coset::Algorithm::Assigned(iana::Algorithm::ES256)) => {
                let y = get_label_value_as_bytes(pkey, iana::Ec2KeyParameter::Y as i64)?;
                PublicKey::new(PubKey::P256 {
                    x_coord: x.as_slice().try_into().context(format!(
                        "Failed to convert x_coord to array. Len: {:?}",
                        x.len()
                    ))?,
                    y_coord: y.as_slice().try_into().context(format!(
                        "Failed to convert y_coord to array. Len: {:?}",
                        y.len()
                    ))?,
                })
            }
            _ => bail!("Unsupported signature algorithm: {:?}", pkey.alg),
        }
    }

    fn new(key: PubKey) -> Result<Self> {
        Ok(Self { key })
    }

    fn sha256(message: &[u8]) -> Result<[u8; 32]> {
        let mut digest: [u8; 32] = [0; 32];
        // SAFETY: This function is safe due to message only being read, with the associated length
        // on the slice passed in to ensure no buffer overreads. Additionally, the digest is sized
        // accordingly to the output size of SHA256. No memory is allocated.
        unsafe {
            if ssl_bindgen::SHA256(message.as_ptr(), message.len(), digest.as_mut_ptr()).is_null() {
                bail!("Failed to hash the message.");
            }
        }
        Ok(digest)
    }

    fn raw_p256_sig_to_der(signature: &[u8]) -> Result<Vec<u8>> {
        ensure!(
            signature.len() == P256_SIG_LEN,
            "Unexpected signature length: {:?}",
            signature.len()
        );
        let mut der_sig: *mut u8 = ptr::null_mut();
        let mut der_sig_len: usize = 0;
        // SAFETY: The signature slice is verified to contain the expected length before it is
        // indexed as read only memory for the boringssl code to generate a DER encoded signature.
        // The final result from the boringssl operations is copied out to a standard vector so
        // the specific boringSSL deallocators can be used on the memory buffers that were
        // allocated, and a standard, safe Rust Vec can be returned.
        unsafe {
            let der_encoder = ssl_bindgen::ECDSA_SIG_new();
            if der_encoder.is_null() {
                bail!("Failed to allocate ECDSA_SIG");
            }
            let mut encoder_closure = || {
                ssl_bindgen::BN_bin2bn(signature.as_ptr(), 32, (*der_encoder).r);
                ssl_bindgen::BN_bin2bn(signature.as_ptr().offset(32), 32, (*der_encoder).s);
                if (*der_encoder).r.is_null() || (*der_encoder).s.is_null() {
                    bail!("Failed to allocate BigNum.");
                }
                // ECDSA_SIG_to_bytes takes a uint8_t** and allocates a buffer
                if ssl_bindgen::ECDSA_SIG_to_bytes(
                    &mut der_sig,
                    &mut der_sig_len as *mut usize,
                    der_encoder,
                ) == 0
                {
                    bail!("Failed to encode ECDSA_SIG into a DER byte array.");
                }
                // Copy the data out of der_sig so that the unsafe pointer and associated memory
                // can be properly freed.
                let mut safe_copy = Vec::with_capacity(der_sig_len);
                ptr::copy(der_sig, safe_copy.as_mut_ptr(), der_sig_len);
                safe_copy.set_len(der_sig_len);
                Ok(safe_copy)
            };
            let safe_copy = encoder_closure();
            ssl_bindgen::ECDSA_SIG_free(der_encoder);
            ssl_bindgen::OPENSSL_free(der_sig as *mut std::ffi::c_void);
            safe_copy
        }
    }

    fn verify_p256(signature: &[u8], message: &[u8], ec_point: &[u8]) -> Result<i32> {
        // len(0x04 || r || s) should be 65 for a p256 public key.
        ensure!(ec_point.len() == 65);
        let mut key_bytes: *const u8 = ec_point.as_ptr();
        let digest = PublicKey::sha256(message)?;
        let der_sig = PublicKey::raw_p256_sig_to_der(signature)?;
        // SAFETY: The following unsafe block allocates and creates an EC_KEY, using that struct
        // in conjunction with read only access to length checked rust slices to verify the
        // signature. The boringSSL allocated memory is then freed, regardless of failures during
        // the verification process.
        unsafe {
            let mut key = ssl_bindgen::EC_KEY_new_by_curve_name(
                ssl_bindgen::NID_X9_62_prime256v1.try_into()?,
            );
            // Use a closure just to simplify freeing allocated memory and error
            // handling in the event an error occurs.
            let mut verifier_closure = || {
                if key.is_null() {
                    bail!("Failed to allocate a new EC_KEY.");
                }
                if ssl_bindgen::o2i_ECPublicKey(
                    &mut key,
                    &mut key_bytes,
                    ec_point.len().try_into()?,
                )
                .is_null()
                {
                    bail!("Failed to convert key byte array into an EC_KEY structure.");
                }
                Ok(ssl_bindgen::ECDSA_verify(
                    0, /* type */
                    digest.as_ptr(),
                    digest.len(),
                    der_sig.as_slice().as_ptr(),
                    der_sig.len(),
                    key,
                ))
            };
            let result = verifier_closure();
            ssl_bindgen::EC_KEY_free(key);
            result
        }
    }

    /// Verify that the signature obtained from signing the given message
    /// with the PublicKey matches the signature provided.
    pub fn verify(&self, signature: &[u8], message: &[u8], alg: &Option<Algorithm>) -> Result<()> {
        match self.key {
            PubKey::Ed25519 { pub_key } => {
                ensure!(
                    *alg == Some(coset::Algorithm::Assigned(iana::Algorithm::EdDSA)),
                    "Unexpected algorithm. Ed25519 key, but alg is: {:?}",
                    *alg
                );
                ensure!(
                    signature.len() == ED25519_SIG_LEN,
                    "Unexpected signature length: {:?}",
                    signature.len()
                );
                ensure!(
                    pub_key.len() == ED25519_PUBLIC_KEY_LEN,
                    "Unexpected public key length {:?}:",
                    pub_key.len()
                );
                ensure!(
                    // SAFETY: The underlying API only reads from the provided pointers, which are
                    // themselves standard slices with their corresponding expended lengths checked
                    // before the function call.
                    unsafe {
                        ssl_bindgen::ED25519_verify(
                            message.as_ptr(),
                            message.len(),
                            signature.as_ptr(),
                            pub_key.as_ptr(),
                        )
                    } == 1,
                    "Signature verification failed."
                );
            }
            PubKey::P256 { x_coord, y_coord } => {
                ensure!(
                    *alg == Some(coset::Algorithm::Assigned(iana::Algorithm::ES256)),
                    "Unexpected algorithm. P256 key, but alg is: {:?}",
                    *alg
                );
                let mut ec_point_uncompressed: Vec<u8> = vec![0x04];
                ec_point_uncompressed.extend_from_slice(&x_coord);
                ec_point_uncompressed.extend_from_slice(&y_coord);
                ensure!(ec_point_uncompressed.len() == 65);
                let ec_point_slice = ec_point_uncompressed.as_slice();
                ensure!(
                    PublicKey::verify_p256(signature, message, ec_point_slice)? == 1,
                    "Signature verification failed."
                );
            }
        }
        Ok(())
    }
}
