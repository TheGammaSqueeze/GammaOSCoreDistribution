//! The cert validator library provides validation functions for the CBOR-CDDL
//! based certificate request, allowing validation of BCC certificate chain,
//! deviceinfo among other things.

pub mod bcc;
pub mod deviceinfo;
pub mod dice;
pub mod publickey;
pub mod valueas;

use anyhow::{Context, Result};
use ciborium::{de::from_reader, value::Value};

/// Reads the provided binary cbor-encoded file and returns a
/// ciborium::Value struct wrapped in Result.
pub fn file_value(fname: &str) -> Result<Value> {
    let f = std::fs::File::open(fname)?;
    from_reader(f).with_context(|| format!("Decoding {}", fname))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::valueas::ValueAs;
    use coset::{iana, Header, Label, RegisteredLabel};

    #[test]
    fn test_bcc_payload_check() {
        let payload = bcc::entry::Payload::from_sign1(
            &bcc::entry::read("testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_0.cert")
                .unwrap(),
        );
        assert!(payload.is_ok());

        let payload = payload.unwrap();
        assert!(payload.check().is_ok());
    }

    #[test]
    fn test_bcc_payload_check_sign1() {
        let payload = bcc::entry::Payload::from_sign1(
            &bcc::entry::read("testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_0.cert")
                .unwrap(),
        );
        assert!(payload.is_ok(), "Payload not okay: {:?}", payload);
        let payload = payload.unwrap().check_sign1(
            &bcc::entry::read("testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_1.cert")
                .unwrap(),
        );
        assert!(payload.is_ok(), "Payload not okay: {:?}", payload);
        let payload = payload.unwrap().check_sign1(
            &bcc::entry::read("testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_2.cert")
                .unwrap(),
        );
        assert!(payload.is_ok(), "Payload not okay: {:?}", payload);
    }

    #[test]
    fn test_check_sign1_cert_chain() {
        let arr: Vec<&str> = vec![
            "testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_0.cert",
            "testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_1.cert",
            "testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_2.cert",
        ];
        assert!(bcc::entry::check_sign1_cert_chain(&arr).is_ok());
    }

    #[test]
    fn test_check_sign1_cert_chain_invalid() {
        let arr: Vec<&str> = vec![
            "testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_0.cert",
            "testdata/open-dice/_CBOR_Ed25519_cert_full_cert_chain_2.cert",
        ];
        assert!(bcc::entry::check_sign1_cert_chain(&arr).is_err());
    }

    #[test]
    fn test_check_sign1_chain_array() {
        let cbor_file = &file_value("testdata/open-dice/_CBOR_bcc_entry_cert_array.cert").unwrap();
        let cbor_arr = ValueAs::as_array(cbor_file).unwrap();
        assert_eq!(cbor_arr.len(), 3);
        assert!(bcc::entry::check_sign1_chain_array(cbor_arr).is_ok());
    }

    #[test]
    fn test_check_chain_valid() -> Result<()> {
        let chain = bcc::Chain::read("testdata/bcc/valid.chain").unwrap();
        let payloads = chain.check()?;
        assert_eq!(payloads.len(), 8);
        Ok(())
    }

    #[test]
    fn test_check_chain_valid_p256() -> Result<()> {
        let chain = bcc::Chain::read("testdata/bcc/valid_p256.chain").unwrap();
        let payloads = chain.check()?;
        assert_eq!(payloads.len(), 3);
        Ok(())
    }

    #[test]
    fn test_check_chain_bad_p256() {
        let chain = bcc::Chain::read("testdata/bcc/bad_p256.chain").unwrap();
        assert!(chain.check().is_err());
    }

    #[test]
    fn test_check_chain_bad_pub_key() {
        let chain = bcc::Chain::read("testdata/bcc/bad_pub_key.chain").unwrap();
        assert!(chain.check().is_err());
    }

    #[test]
    fn test_check_chain_bad_final_signature() {
        let chain = bcc::Chain::read("testdata/bcc/bad_final_signature.chain").unwrap();
        assert!(chain.check().is_err());
    }

    #[test]
    fn deviceinfo_validation() {
        let val = &file_value("testdata/device-info/_CBOR_device_info_0.cert").unwrap();
        let deviceinfo = deviceinfo::extract(val);
        assert!(deviceinfo.is_ok());
        assert!(deviceinfo::check(deviceinfo.unwrap()).is_ok());
    }

    #[test]
    fn test_check_bcc_entry_protected_header() -> Result<()> {
        let eddsa = Some(coset::Algorithm::Assigned(iana::Algorithm::EdDSA));
        let header = Header { alg: (&eddsa).clone(), ..Default::default() };
        bcc::entry::check_protected_header(&eddsa, &header).context("Only alg allowed")?;
        let header = Header { alg: Some(coset::Algorithm::PrivateUse(1000)), ..Default::default() };
        assert!(bcc::entry::check_protected_header(&eddsa, &header).is_err());
        let mut header = Header { alg: (&eddsa).clone(), ..Default::default() };
        header.rest.push((Label::Int(1000), Value::from(2000u16)));
        bcc::entry::check_protected_header(&eddsa, &header).context("non-crit header allowed")?;
        let mut header = Header { alg: (&eddsa).clone(), ..Default::default() };
        header.crit.push(RegisteredLabel::Assigned(iana::HeaderParameter::Alg));
        bcc::entry::check_protected_header(&eddsa, &header).context("OK to say alg is critical")?;
        let mut header = Header { alg: (&eddsa).clone(), ..Default::default() };
        header.crit.push(RegisteredLabel::Assigned(iana::HeaderParameter::CounterSignature));
        assert!(bcc::entry::check_protected_header(&eddsa, &header).is_err());
        Ok(())
    }
}
