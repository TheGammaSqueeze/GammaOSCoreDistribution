//! This module provides functions for validating chains of bcc certificates

use crate::dice;
use crate::publickey;
use crate::valueas::ValueAs;

use self::entry::SubjectPublicKey;
use anyhow::{anyhow, bail, ensure, Context, Result};
use coset::AsCborValue;
use coset::{
    cbor::value::Value::{self, Array},
    iana::{self, EnumI64},
    Algorithm, CborSerializable,
    CoseError::{self, EncodeFailed, UnexpectedItem},
    CoseKey, CoseSign1, Header, RegisteredLabel,
};
use std::fmt;
use std::io::Read;

/// Represents a full Boot Certificate Chain (BCC). This consists of the root public key (which
/// signs the first certificate), followed by a chain of BccEntry certificates. Apart from the
/// first, the issuer of each cert if the subject of the previous one.
pub struct Chain {
    public_key: CoseKey,
    entries: Vec<CoseSign1>,
}

impl Chain {
    /// Read a Chain from a file containing the CBOR encoding. This fails if the representation is
    /// ill-formed.
    pub fn read(fname: &str) -> Result<Chain> {
        let mut f = std::fs::File::open(fname)?;
        let mut content = Vec::new();
        f.read_to_end(&mut content)?;
        Chain::from_slice(&content).map_err(cose_error)
    }

    /// Check all certificates are correctly signed, contain the required fields, and are otherwise
    /// semantically correct.
    pub fn check(&self) -> Result<Vec<entry::Payload>> {
        let public_key = SubjectPublicKey::from_cose_key(self.public_key.clone());
        public_key.check().context("Invalid root key")?;

        let mut it = self.entries.iter();
        let entry = it.next().unwrap();
        let mut payload = entry::Payload::check_sign1_signature(&public_key, entry)
            .context("Failed initial signature check.")?;
        let mut payloads = Vec::with_capacity(self.entries.len());

        for entry in it {
            payload.check().context("Invalid BccPayload")?;
            let next_payload = payload.check_sign1(entry)?;
            payloads.push(payload);
            payload = next_payload;
        }
        payloads.push(payload);
        Ok(payloads)
    }

    /// Return the public key that can be used to verify the signature on the first certificate in
    /// the chain.
    pub fn get_root_public_key(&self) -> SubjectPublicKey {
        SubjectPublicKey::from_cose_key(self.public_key.clone())
    }
}

impl AsCborValue for Chain {
    /*
     * CDDL (from keymint/ProtectedData.aidl):
     *
     * Bcc = [
     *     PubKeyEd25519 / PubKeyECDSA256, // DK_pub
     *     + BccEntry,                     // Root -> leaf (KM_pub)
     * ]
     */

    fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
        let a = match value {
            Array(a) if a.len() >= 2 => a,
            _ => return Err(UnexpectedItem("something", "an array with 2 or more items")),
        };
        let mut it = a.into_iter();
        let public_key = CoseKey::from_cbor_value(it.next().unwrap())?;
        let entries = it.map(CoseSign1::from_cbor_value).collect::<Result<Vec<_>, _>>()?;
        Ok(Chain { public_key, entries })
    }

    fn to_cbor_value(self) -> Result<Value, CoseError> {
        // TODO: Implement when needed
        Err(EncodeFailed)
    }
}

impl CborSerializable for Chain {}

fn cose_error(ce: coset::CoseError) -> anyhow::Error {
    anyhow!("CoseError: {:?}", ce)
}

/// Get the value corresponding to the provided label within the supplied CoseKey
/// or error if it's not present.
pub fn get_label_value(key: &coset::CoseKey, label: i64) -> Result<&Value> {
    Ok(&key
        .params
        .iter()
        .find(|(k, _)| k == &coset::Label::Int(label))
        .ok_or_else(|| anyhow!("Label {:?} not found", label))?
        .1)
}

/// Get the byte string for the corresponding label within the key if the label exists
/// and the value is actually a byte array.
pub fn get_label_value_as_bytes(key: &coset::CoseKey, label: i64) -> Result<&Vec<u8>> {
    get_label_value(key, label)?.as_bytes().ok_or_else(|| anyhow!("Value not a bstr."))
}
/// This module wraps the certificate validation functions intended for BccEntry.
pub mod entry {
    use std::fmt::{Display, Formatter, Write};

    use super::*;

    /// Read a series of bcc file certificates and verify that the public key of
    /// any given cert's payload in the series correctly signs the next cose
    /// sign1 cert.
    pub fn check_sign1_cert_chain(certs: &[&str]) -> Result<()> {
        ensure!(!certs.is_empty());
        let mut payload = Payload::from_sign1(&read(certs[0])?)
            .context("Failed to read the first bccEntry payload")?;
        for item in certs.iter().skip(1) {
            payload.check().context("Validation of BccPayload entries failed.")?;
            payload =
                payload.check_sign1(&read(item).context("Failed to read the bccEntry payload")?)?;
        }
        Ok(())
    }

    /// Read a given cbor array containing bcc entries and verify that the public key
    /// of any given cert's payload in the series correctly signs the next cose sign1
    /// cert.
    pub fn check_sign1_chain_array(cbor_arr: &[Value]) -> Result<()> {
        ensure!(!cbor_arr.is_empty());

        let mut writeme: Vec<u8> = Vec::new();
        ciborium::ser::into_writer(&cbor_arr[0], &mut writeme)?;
        let mut payload =
            Payload::from_sign1(&CoseSign1::from_slice(&writeme).map_err(cose_error)?)
                .context("Failed to read bccEntry payload")?;
        for item in cbor_arr.iter().skip(1) {
            payload.check().context("Validation of BccPayload entries failed")?;
            writeme = Vec::new();
            ciborium::ser::into_writer(item, &mut writeme)?;
            let next_sign1 = &CoseSign1::from_slice(&writeme).map_err(cose_error)?;
            payload = payload.check_sign1(next_sign1).context("Failed to read bccEntry payload")?;
        }
        Ok(())
    }

    /// Read a file name as string and create the BccEntry as COSE_sign1 structure.
    pub fn read(fname: &str) -> Result<CoseSign1> {
        let mut f = std::fs::File::open(fname)?;
        let mut content = Vec::new();
        f.read_to_end(&mut content)?;
        CoseSign1::from_slice(&content).map_err(cose_error)
    }

    /// Validate the protected header of a bcc entry with respect to the provided
    /// alg (typically originating from the subject public key of the payload).
    pub fn check_protected_header(alg: &Option<Algorithm>, header: &Header) -> Result<()> {
        ensure!(&header.alg == alg);
        ensure!(header
            .crit
            .iter()
            .all(|l| l == &RegisteredLabel::Assigned(iana::HeaderParameter::Alg)));
        Ok(())
    }
    /// Struct describing BccPayload cbor of the BccEntry.
    #[derive(Debug)]
    pub struct Payload(Value);
    impl Payload {
        /// Construct the Payload from the parent BccEntry COSE_sign1 structure.
        pub fn from_sign1(sign1: &CoseSign1) -> Result<Payload> {
            Self::from_slice(sign1.payload.as_ref().ok_or_else(|| anyhow!("no payload"))?)
        }

        /// Validate entries in the Payload to be correct.
        pub fn check(&self) -> Result<()> {
            // Validate required fields.
            self.map_lookup(dice::ISS)?.as_string()?;
            self.map_lookup(dice::SUB)?.as_string()?;
            SubjectPublicKey::from_payload(self)?.check().context("Public key failed checking")?;
            self.map_lookup(dice::KEY_USAGE)?
                .as_bytes()
                .ok_or_else(|| anyhow!("Payload Key usage not bytes"))?;

            // Validate required and optional fields. The required fields are those defined
            // to be present for CDI_Certificates in the open-DICE profile.
            // TODO: Check if the optional fields are present, and if so, ensure that
            //       the operations applied to the mandatory fields actually reproduce the
            //       values in the optional fields as specified in open-DICE.
            self.0.map_lookup(dice::CODE_HASH).context("Code hash must be present.")?;
            self.0.map_lookup(dice::CONFIG_DESC).context("Config descriptor must be present.")?;
            self.0.map_lookup(dice::AUTHORITY_HASH).context("Authority hash must be present.")?;
            self.0.map_lookup(dice::MODE).context("Mode must be present.")?;

            // Verify that each key that does exist has the expected type.
            self.0
                .check_bytes_val_if_key_in_map(dice::CODE_HASH)
                .context("Code Hash value not bytes.")?;
            self.0
                .check_bytes_val_if_key_in_map(dice::CODE_DESC)
                .context("Code Descriptor value not bytes.")?;
            self.0
                .check_bytes_val_if_key_in_map(dice::CONFIG_HASH)
                .context("Configuration Hash value not bytes.")?;
            self.0
                .check_bytes_val_if_key_in_map(dice::CONFIG_DESC)
                .context("Configuration descriptor value not bytes.")?;
            self.0
                .check_bytes_val_if_key_in_map(dice::AUTHORITY_HASH)
                .context("Authority Hash value not bytes.")?;
            self.0
                .check_bytes_val_if_key_in_map(dice::AUTHORITY_DESC)
                .context("Authority descriptor value not bytes.")?;
            self.0.check_bytes_val_if_key_in_map(dice::MODE).context("Mode value not bytes.")?;
            Ok(())
        }

        /// Verify that the public key of this payload correctly signs the provided
        /// BccEntry sign1 object.
        pub fn check_sign1(&self, sign1: &CoseSign1) -> Result<Payload> {
            let pkey = SubjectPublicKey::from_payload(self)
                .context("Failed to construct Public key from the Bcc payload.")?;
            let new_payload = Self::check_sign1_signature(&pkey, sign1)?;
            ensure!(
                self.map_lookup(dice::SUB)? == new_payload.map_lookup(dice::ISS)?,
                "Subject/Issuer mismatch"
            );
            Ok(new_payload)
        }

        pub(super) fn check_sign1_signature(
            pkey: &SubjectPublicKey,
            sign1: &CoseSign1,
        ) -> Result<Payload> {
            check_protected_header(&pkey.0.alg, &sign1.protected.header)
                .context("Validation of bcc entry protected header failed.")?;
            let v = publickey::PublicKey::from_cose_key(&pkey.0)
                .context("Extracting the Public key from coseKey failed.")?;
            sign1
                .verify_signature(b"", |s, m| v.verify(s, m, &pkey.0.alg))
                .context("public key incorrectly signs the given cose_sign1 cert.")?;
            let new_payload = Payload::from_sign1(sign1)
                .context("Failed to extract bcc payload from cose_sign1")?;
            Ok(new_payload)
        }

        fn from_slice(b: &[u8]) -> Result<Self> {
            Ok(Payload(coset::cbor::de::from_reader(b).map_err(|e| anyhow!("CborError: {}", e))?))
        }

        fn map_lookup(&self, key: i64) -> Result<&Value> {
            Ok(&self
                .0
                .as_map()
                .ok_or_else(|| anyhow!("not a map"))?
                .iter()
                .find(|(k, _v)| k == &Value::from(key))
                .ok_or_else(|| anyhow!("missing key {}", key))?
                .1)
        }
    }

    /// Struct wrapping the CoseKey for BccEntry.BccPayload.SubjectPublicKey
    /// and the methods used for its validation.
    pub struct SubjectPublicKey(CoseKey);
    impl SubjectPublicKey {
        pub(super) fn from_cose_key(cose_key: CoseKey) -> Self {
            Self(cose_key)
        }

        /// Construct the SubjectPublicKey from the (bccEntry's) Payload.
        pub fn from_payload(payload: &Payload) -> Result<SubjectPublicKey> {
            let bytes = payload
                .map_lookup(dice::SUBJECT_PUBLIC_KEY)?
                .as_bytes()
                .ok_or_else(|| anyhow!("public key not bytes"))?;
            Self::from_slice(bytes)
        }

        fn from_slice(bytes: &[u8]) -> Result<SubjectPublicKey> {
            Ok(SubjectPublicKey(CoseKey::from_slice(bytes).map_err(cose_error)?))
        }

        /// Perform validation on the items in the public key.
        pub fn check(&self) -> Result<()> {
            let pkey = &self.0;
            if !pkey.key_ops.is_empty() {
                ensure!(pkey
                    .key_ops
                    .contains(&coset::KeyOperation::Assigned(iana::KeyOperation::Verify)));
            }
            match pkey.kty {
                coset::KeyType::Assigned(iana::KeyType::OKP) => {
                    ensure!(pkey.alg == Some(coset::Algorithm::Assigned(iana::Algorithm::EdDSA)));
                    let crv = get_label_value(pkey, iana::OkpKeyParameter::Crv as i64)?;
                    ensure!(crv == &Value::from(iana::EllipticCurve::Ed25519 as i64));
                }
                coset::KeyType::Assigned(iana::KeyType::EC2) => {
                    ensure!(pkey.alg == Some(coset::Algorithm::Assigned(iana::Algorithm::ES256)));
                    let crv = get_label_value(pkey, iana::Ec2KeyParameter::Crv as i64)?;
                    ensure!(crv == &Value::from(iana::EllipticCurve::P_256 as i64));
                }
                _ => bail!("Unexpected KeyType value: {:?}", pkey.kty),
            }
            Ok(())
        }
    }

    struct ConfigDesc(Vec<(Value, Value)>);

    impl AsCborValue for ConfigDesc {
        /*
         * CDDL (from keymint/ProtectedData.aidl):
         *
         *  bstr .cbor {      // Configuration Descriptor
         *     ? -70002 : tstr,           // Component name
         *     ? -70003 : int,            // Firmware version
         *     ? -70004 : null,           // Resettable
         * },
         */

        fn from_cbor_value(value: Value) -> Result<Self, CoseError> {
            match value {
                Value::Map(m) => Ok(Self(m)),
                _ => Err(UnexpectedItem("something", "a map")),
            }
        }

        fn to_cbor_value(self) -> Result<Value, CoseError> {
            // TODO: Implement when needed
            Err(EncodeFailed)
        }
    }

    impl CborSerializable for ConfigDesc {}

    impl Display for ConfigDesc {
        fn fmt(&self, f: &mut Formatter) -> Result<(), fmt::Error> {
            write_payload_label(f, dice::CONFIG_DESC)?;
            f.write_str(":\n")?;
            for (label, value) in &self.0 {
                f.write_str("  ")?;
                if let Ok(i) = label.as_i64() {
                    write_config_desc_label(f, i)?;
                } else {
                    write_value(f, label)?;
                }
                f.write_str(": ")?;
                write_value(f, value)?;
                f.write_char('\n')?;
            }
            Ok(())
        }
    }

    fn write_config_desc_label(f: &mut Formatter, label: i64) -> Result<(), fmt::Error> {
        match label {
            dice::COMPONENT_NAME => f.write_str("Component Name"),
            dice::FIRMWARE_VERSION => f.write_str("Firmware Version"),
            dice::RESETTABLE => f.write_str("Resettable"),
            _ => write!(f, "{}", label),
        }
    }

    impl Display for SubjectPublicKey {
        fn fmt(&self, f: &mut Formatter) -> Result<(), fmt::Error> {
            let pkey = &self.0;
            if pkey.kty != coset::KeyType::Assigned(iana::KeyType::OKP)
                || pkey.alg != Some(coset::Algorithm::Assigned(iana::Algorithm::EdDSA))
            {
                return Err(fmt::Error);
            }

            let mut separator = "";
            for (label, value) in &pkey.params {
                use coset::Label;
                use iana::OkpKeyParameter;
                if let Label::Int(i) = label {
                    match OkpKeyParameter::from_i64(*i) {
                        Some(OkpKeyParameter::Crv) => {
                            if let Some(crv) =
                                value.as_i64().ok().and_then(iana::EllipticCurve::from_i64)
                            {
                                f.write_str(separator)?;
                                write!(f, "Curve: {:?}", crv)?;
                                separator = " ";
                            }
                        }
                        Some(OkpKeyParameter::X) => {
                            if let Ok(x) = ValueAs::as_bytes(value) {
                                f.write_str(separator)?;
                                f.write_str("X: ")?;
                                write_bytes_in_hex(f, x)?;
                                separator = " ";
                            }
                        }
                        _ => (),
                    }
                }
            }

            Ok(())
        }
    }

    impl Display for Payload {
        fn fmt(&self, f: &mut Formatter) -> Result<(), fmt::Error> {
            for (label, value) in self.0.as_map().ok_or(fmt::Error)? {
                if let Ok(i) = label.as_i64() {
                    if i == dice::CONFIG_DESC {
                        write_config_desc(f, value)?;
                        continue;
                    } else if i == dice::SUBJECT_PUBLIC_KEY {
                        write_payload_label(f, i)?;
                        f.write_str(": ")?;
                        write_subject_public_key(f, value)?;
                        continue;
                    }
                    write_payload_label(f, i)?;
                } else {
                    write_value(f, label)?;
                }
                f.write_str(": ")?;
                write_value(f, value)?;
                f.write_char('\n')?;
            }
            Ok(())
        }
    }

    fn write_payload_label(f: &mut Formatter, label: i64) -> Result<(), fmt::Error> {
        match label {
            dice::ISS => f.write_str("Issuer"),
            dice::SUB => f.write_str("Subject"),
            dice::CODE_HASH => f.write_str("Code Hash"),
            dice::CODE_DESC => f.write_str("Code Desc"),
            dice::CONFIG_DESC => f.write_str("Config Desc"),
            dice::CONFIG_HASH => f.write_str("Config Hash"),
            dice::AUTHORITY_HASH => f.write_str("Authority Hash"),
            dice::AUTHORITY_DESC => f.write_str("Authority Desc"),
            dice::MODE => f.write_str("Mode"),
            dice::SUBJECT_PUBLIC_KEY => f.write_str("Subject Public Key"),
            dice::KEY_USAGE => f.write_str("Key Usage"),
            _ => write!(f, "{}", label),
        }
    }

    fn write_config_desc(f: &mut Formatter, value: &Value) -> Result<(), fmt::Error> {
        let bytes = value.as_bytes().ok_or(fmt::Error)?;
        let config_desc = ConfigDesc::from_slice(bytes).map_err(|_| fmt::Error)?;
        write!(f, "{}", config_desc)
    }

    fn write_subject_public_key(f: &mut Formatter, value: &Value) -> Result<(), fmt::Error> {
        let bytes = value.as_bytes().ok_or(fmt::Error)?;
        let subject_public_key = SubjectPublicKey::from_slice(bytes).map_err(|_| fmt::Error)?;
        write!(f, "{}", subject_public_key)
    }

    fn write_value(f: &mut Formatter, value: &Value) -> Result<(), fmt::Error> {
        if let Some(bytes) = value.as_bytes() {
            write_bytes_in_hex(f, bytes)
        } else if let Some(text) = value.as_text() {
            write!(f, "\"{}\"", text)
        } else if let Ok(integer) = value.as_i64() {
            write!(f, "{}", integer)
        } else {
            write!(f, "{:?}", value)
        }
    }

    fn write_bytes_in_hex(f: &mut Formatter, bytes: &[u8]) -> Result<(), fmt::Error> {
        for b in bytes {
            write!(f, "{:02x}", b)?
        }
        Ok(())
    }
}
