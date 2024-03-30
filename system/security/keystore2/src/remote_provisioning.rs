// Copyright 2020, The Android Open Source Project
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

//! This is the implementation for the remote provisioning AIDL interface between
//! the network providers for remote provisioning and the system. This interface
//! allows the caller to prompt the Remote Provisioning HAL to generate keys and
//! CBOR blobs that can be ferried to a provisioning server that will return
//! certificate chains signed by some root authority and stored in a keystore SQLite
//! DB.

use std::collections::HashMap;

use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
    Algorithm::Algorithm, AttestationKey::AttestationKey, Certificate::Certificate,
    DeviceInfo::DeviceInfo, IRemotelyProvisionedComponent::IRemotelyProvisionedComponent,
    KeyParameter::KeyParameter, KeyParameterValue::KeyParameterValue,
    MacedPublicKey::MacedPublicKey, ProtectedData::ProtectedData, SecurityLevel::SecurityLevel,
    Tag::Tag,
};
use android_security_remoteprovisioning::aidl::android::security::remoteprovisioning::{
    AttestationPoolStatus::AttestationPoolStatus, IRemoteProvisioning::BnRemoteProvisioning,
    IRemoteProvisioning::IRemoteProvisioning,
    IRemotelyProvisionedKeyPool::BnRemotelyProvisionedKeyPool,
    IRemotelyProvisionedKeyPool::IRemotelyProvisionedKeyPool, ImplInfo::ImplInfo,
    RemotelyProvisionedKey::RemotelyProvisionedKey,
};
use android_security_remoteprovisioning::binder::{BinderFeatures, Strong};
use android_system_keystore2::aidl::android::system::keystore2::{
    Domain::Domain, KeyDescriptor::KeyDescriptor, ResponseCode::ResponseCode,
};
use anyhow::{Context, Result};
use keystore2_crypto::parse_subject_from_certificate;
use serde_cbor::Value;
use std::collections::BTreeMap;
use std::sync::atomic::{AtomicBool, Ordering};

use crate::database::{CertificateChain, KeyIdGuard, KeystoreDB, Uuid};
use crate::error::{self, map_or_log_err, map_rem_prov_error, Error};
use crate::globals::{get_keymint_device, get_remotely_provisioned_component, DB};
use crate::metrics_store::log_rkp_error_stats;
use crate::permission::KeystorePerm;
use crate::utils::{check_keystore_permission, watchdog as wd};
use android_security_metrics::aidl::android::security::metrics::RkpError::RkpError as MetricsRkpError;

/// Contains helper functions to check if remote provisioning is enabled on the system and, if so,
/// to assign and retrieve attestation keys and certificate chains.
#[derive(Default)]
pub struct RemProvState {
    security_level: SecurityLevel,
    km_uuid: Uuid,
    is_hal_present: AtomicBool,
}

static COSE_KEY_XCOORD: Value = Value::Integer(-2);
static COSE_KEY_YCOORD: Value = Value::Integer(-3);
static COSE_MAC0_LEN: usize = 4;
static COSE_MAC0_PAYLOAD: usize = 2;

impl RemProvState {
    /// Creates a RemProvState struct.
    pub fn new(security_level: SecurityLevel, km_uuid: Uuid) -> Self {
        Self { security_level, km_uuid, is_hal_present: AtomicBool::new(true) }
    }

    /// Returns the uuid for the KM instance attached to this RemProvState struct.
    pub fn get_uuid(&self) -> Uuid {
        self.km_uuid
    }

    fn is_rkp_only(&self) -> bool {
        let default_value = false;

        let property_name = match self.security_level {
            SecurityLevel::STRONGBOX => "remote_provisioning.strongbox.rkp_only",
            SecurityLevel::TRUSTED_ENVIRONMENT => "remote_provisioning.tee.rkp_only",
            _ => return default_value,
        };

        rustutils::system_properties::read_bool(property_name, default_value)
            .unwrap_or(default_value)
    }

    /// Checks if remote provisioning is enabled and partially caches the result. On a hybrid system
    /// remote provisioning can flip from being disabled to enabled depending on responses from the
    /// server, so unfortunately caching the presence or absence of the HAL is not enough to fully
    /// make decisions about the state of remote provisioning during runtime.
    fn check_rem_prov_enabled(&self, db: &mut KeystoreDB) -> Result<bool> {
        if self.is_rkp_only() {
            return Ok(true);
        }
        if !self.is_hal_present.load(Ordering::Relaxed)
            || get_remotely_provisioned_component(&self.security_level).is_err()
        {
            self.is_hal_present.store(false, Ordering::Relaxed);
            return Ok(false);
        }
        // To check if remote provisioning is enabled on a system that supports both remote
        // provisioning and factory provisioned keys, we only need to check if there are any
        // keys at all generated to indicate if the app has gotten the signal to begin filling
        // the key pool from the server.
        let pool_status = db
            .get_attestation_pool_status(0 /* date */, &self.km_uuid)
            .context("In check_rem_prov_enabled: failed to get attestation pool status.")?;
        Ok(pool_status.total != 0)
    }

    fn is_asymmetric_key(&self, params: &[KeyParameter]) -> bool {
        params.iter().any(|kp| {
            matches!(
                kp,
                KeyParameter {
                    tag: Tag::ALGORITHM,
                    value: KeyParameterValue::Algorithm(Algorithm::RSA)
                } | KeyParameter {
                    tag: Tag::ALGORITHM,
                    value: KeyParameterValue::Algorithm(Algorithm::EC)
                }
            )
        })
    }

    /// Checks to see (1) if the key in question should be attested to based on the algorithm and
    /// (2) if remote provisioning is present and enabled on the system. If these conditions are
    /// met, it makes an attempt to fetch the attestation key assigned to the `caller_uid`.
    ///
    /// It returns the ResponseCode `OUT_OF_KEYS` if there is not one key currently assigned to the
    /// `caller_uid` and there are none available to assign.
    pub fn get_remotely_provisioned_attestation_key_and_certs(
        &self,
        key: &KeyDescriptor,
        caller_uid: u32,
        params: &[KeyParameter],
        db: &mut KeystoreDB,
    ) -> Result<Option<(KeyIdGuard, AttestationKey, Certificate)>> {
        if !self.is_asymmetric_key(params) || !self.check_rem_prov_enabled(db)? {
            // There is no remote provisioning component for this security level on the
            // device. Return None so the underlying KM instance knows to use its
            // factory provisioned key instead. Alternatively, it's not an asymmetric key
            // and therefore will not be attested.
            Ok(None)
        } else {
            match get_rem_prov_attest_key(key.domain, caller_uid, db, &self.km_uuid) {
                Err(e) => {
                    log::error!(
                        "In get_remote_provisioning_key_and_certs: Error occurred: {:?}",
                        e
                    );
                    if self.is_rkp_only() {
                        return Err(e);
                    }
                    log_rkp_error_stats(MetricsRkpError::FALL_BACK_DURING_HYBRID,
                            &self.security_level);
                    Ok(None)
                }
                Ok(v) => match v {
                    Some((guard, cert_chain)) => Ok(Some((
                        guard,
                        AttestationKey {
                            keyBlob: cert_chain.private_key.to_vec(),
                            attestKeyParams: vec![],
                            issuerSubjectName: parse_subject_from_certificate(
                                &cert_chain.batch_cert,
                            )
                            .context(concat!(
                                "In get_remote_provisioning_key_and_certs: Failed to ",
                                "parse subject."
                            ))?,
                        },
                        Certificate { encodedCertificate: cert_chain.cert_chain },
                    ))),
                    None => Ok(None),
                },
            }
        }
    }
}
/// Implementation of the IRemoteProvisioning service.
#[derive(Default)]
pub struct RemoteProvisioningService {
    device_by_sec_level: HashMap<SecurityLevel, Strong<dyn IRemotelyProvisionedComponent>>,
    curve_by_sec_level: HashMap<SecurityLevel, i32>,
}

impl RemoteProvisioningService {
    fn get_dev_by_sec_level(
        &self,
        sec_level: &SecurityLevel,
    ) -> Result<&dyn IRemotelyProvisionedComponent> {
        if let Some(dev) = self.device_by_sec_level.get(sec_level) {
            Ok(dev.as_ref())
        } else {
            Err(error::Error::sys()).context(concat!(
                "In get_dev_by_sec_level: Remote instance for requested security level",
                " not found."
            ))
        }
    }

    /// Creates a new instance of the remote provisioning service
    pub fn new_native_binder() -> Result<Strong<dyn IRemoteProvisioning>> {
        let mut result: Self = Default::default();
        let dev = get_remotely_provisioned_component(&SecurityLevel::TRUSTED_ENVIRONMENT)
            .context("In new_native_binder: Failed to get TEE Remote Provisioner instance.")?;
        result.curve_by_sec_level.insert(
            SecurityLevel::TRUSTED_ENVIRONMENT,
            dev.getHardwareInfo()
                .context("In new_native_binder: Failed to get hardware info for the TEE.")?
                .supportedEekCurve,
        );
        result.device_by_sec_level.insert(SecurityLevel::TRUSTED_ENVIRONMENT, dev);
        if let Ok(dev) = get_remotely_provisioned_component(&SecurityLevel::STRONGBOX) {
            result.curve_by_sec_level.insert(
                SecurityLevel::STRONGBOX,
                dev.getHardwareInfo()
                    .context("In new_native_binder: Failed to get hardware info for StrongBox.")?
                    .supportedEekCurve,
            );
            result.device_by_sec_level.insert(SecurityLevel::STRONGBOX, dev);
        }
        Ok(BnRemoteProvisioning::new_binder(result, BinderFeatures::default()))
    }

    fn extract_payload_from_cose_mac(data: &[u8]) -> Result<Value> {
        let cose_mac0: Vec<Value> = serde_cbor::from_slice(data).context(
            "In extract_payload_from_cose_mac: COSE_Mac0 returned from IRPC cannot be parsed",
        )?;
        if cose_mac0.len() != COSE_MAC0_LEN {
            return Err(error::Error::sys()).context(format!(
                "In extract_payload_from_cose_mac: COSE_Mac0 has improper length. \
                    Expected: {}, Actual: {}",
                COSE_MAC0_LEN,
                cose_mac0.len(),
            ));
        }
        match &cose_mac0[COSE_MAC0_PAYLOAD] {
            Value::Bytes(key) => Ok(serde_cbor::from_slice(key)
                .context("In extract_payload_from_cose_mac: COSE_Mac0 payload is malformed.")?),
            _ => Err(error::Error::sys()).context(
                "In extract_payload_from_cose_mac: COSE_Mac0 payload is the wrong type.",
            )?,
        }
    }

    /// Generates a CBOR blob which will be assembled by the calling code into a larger
    /// CBOR blob intended for delivery to a provisioning serever. This blob will contain
    /// `num_csr` certificate signing requests for attestation keys generated in the TEE,
    /// along with a server provided `eek` and `challenge`. The endpoint encryption key will
    /// be used to encrypt the sensitive contents being transmitted to the server, and the
    /// challenge will ensure freshness. A `test_mode` flag will instruct the remote provisioning
    /// HAL if it is okay to accept EEKs that aren't signed by something that chains back to the
    /// baked in root of trust in the underlying IRemotelyProvisionedComponent instance.
    #[allow(clippy::too_many_arguments)]
    pub fn generate_csr(
        &self,
        test_mode: bool,
        num_csr: i32,
        eek: &[u8],
        challenge: &[u8],
        sec_level: SecurityLevel,
        protected_data: &mut ProtectedData,
        device_info: &mut DeviceInfo,
    ) -> Result<Vec<u8>> {
        let dev = self.get_dev_by_sec_level(&sec_level)?;
        let (_, _, uuid) = get_keymint_device(&sec_level)?;
        let keys_to_sign = DB.with::<_, Result<Vec<MacedPublicKey>>>(|db| {
            let mut db = db.borrow_mut();
            Ok(db
                .fetch_unsigned_attestation_keys(num_csr, &uuid)?
                .iter()
                .map(|key| MacedPublicKey { macedKey: key.to_vec() })
                .collect())
        })?;
        let mac = map_rem_prov_error(dev.generateCertificateRequest(
            test_mode,
            &keys_to_sign,
            eek,
            challenge,
            device_info,
            protected_data,
        ))
        .context("In generate_csr: Failed to generate csr")?;
        let mut mac_and_keys: Vec<Value> = vec![Value::from(mac)];
        for maced_public_key in keys_to_sign {
            mac_and_keys.push(
                Self::extract_payload_from_cose_mac(&maced_public_key.macedKey)
                    .context("In generate_csr: Failed to get the payload from the COSE_Mac0")?,
            )
        }
        let cbor_array: Value = Value::Array(mac_and_keys);
        serde_cbor::to_vec(&cbor_array)
            .context("In generate_csr: Failed to serialize the mac and keys array")
    }

    /// Provisions a certificate chain for a key whose CSR was included in generate_csr. The
    /// `public_key` is used to index into the SQL database in order to insert the `certs` blob
    /// which represents a PEM encoded X.509 certificate chain. The `expiration_date` is provided
    /// as a convenience from the caller to avoid having to parse the certificates semantically
    /// here.
    pub fn provision_cert_chain(
        &self,
        db: &mut KeystoreDB,
        public_key: &[u8],
        batch_cert: &[u8],
        certs: &[u8],
        expiration_date: i64,
        sec_level: SecurityLevel,
    ) -> Result<()> {
        let (_, _, uuid) = get_keymint_device(&sec_level)?;
        db.store_signed_attestation_certificate_chain(
            public_key,
            batch_cert,
            certs, /* DER encoded certificate chain */
            expiration_date,
            &uuid,
        )
    }

    fn parse_cose_mac0_for_coords(data: &[u8]) -> Result<Vec<u8>> {
        let cose_mac0: Vec<Value> = serde_cbor::from_slice(data).context(
            "In parse_cose_mac0_for_coords: COSE_Mac0 returned from IRPC cannot be parsed",
        )?;
        if cose_mac0.len() != COSE_MAC0_LEN {
            return Err(error::Error::sys()).context(format!(
                "In parse_cose_mac0_for_coords: COSE_Mac0 has improper length. \
                    Expected: {}, Actual: {}",
                COSE_MAC0_LEN,
                cose_mac0.len(),
            ));
        }
        let cose_key: BTreeMap<Value, Value> = match &cose_mac0[COSE_MAC0_PAYLOAD] {
            Value::Bytes(key) => serde_cbor::from_slice(key)
                .context("In parse_cose_mac0_for_coords: COSE_Key is malformed.")?,
            _ => Err(error::Error::sys())
                .context("In parse_cose_mac0_for_coords: COSE_Mac0 payload is the wrong type.")?,
        };
        if !cose_key.contains_key(&COSE_KEY_XCOORD) || !cose_key.contains_key(&COSE_KEY_YCOORD) {
            return Err(error::Error::sys()).context(
                "In parse_cose_mac0_for_coords: \
                COSE_Key returned from IRPC is lacking required fields",
            );
        }
        let mut raw_key: Vec<u8> = vec![0; 64];
        match &cose_key[&COSE_KEY_XCOORD] {
            Value::Bytes(x_coord) if x_coord.len() == 32 => {
                raw_key[0..32].clone_from_slice(x_coord)
            }
            Value::Bytes(x_coord) => {
                return Err(error::Error::sys()).context(format!(
                "In parse_cose_mac0_for_coords: COSE_Key X-coordinate is not the right length. \
                Expected: 32; Actual: {}",
                    x_coord.len()
                ))
            }
            _ => {
                return Err(error::Error::sys())
                    .context("In parse_cose_mac0_for_coords: COSE_Key X-coordinate is not a bstr")
            }
        }
        match &cose_key[&COSE_KEY_YCOORD] {
            Value::Bytes(y_coord) if y_coord.len() == 32 => {
                raw_key[32..64].clone_from_slice(y_coord)
            }
            Value::Bytes(y_coord) => {
                return Err(error::Error::sys()).context(format!(
                "In parse_cose_mac0_for_coords: COSE_Key Y-coordinate is not the right length. \
                Expected: 32; Actual: {}",
                    y_coord.len()
                ))
            }
            _ => {
                return Err(error::Error::sys())
                    .context("In parse_cose_mac0_for_coords: COSE_Key Y-coordinate is not a bstr")
            }
        }
        Ok(raw_key)
    }

    /// Submits a request to the Remote Provisioner HAL to generate a signing key pair.
    /// `is_test_mode` indicates whether or not the returned public key should be marked as being
    /// for testing in order to differentiate them from private keys. If the call is successful,
    /// the key pair is then added to the database.
    pub fn generate_key_pair(
        &self,
        db: &mut KeystoreDB,
        is_test_mode: bool,
        sec_level: SecurityLevel,
    ) -> Result<()> {
        let (_, _, uuid) = get_keymint_device(&sec_level)?;
        let dev = self.get_dev_by_sec_level(&sec_level).context(format!(
            "In generate_key_pair: Failed to get device for security level {:?}",
            sec_level
        ))?;
        let mut maced_key = MacedPublicKey { macedKey: Vec::new() };
        let priv_key =
            map_rem_prov_error(dev.generateEcdsaP256KeyPair(is_test_mode, &mut maced_key))
                .context("In generate_key_pair: Failed to generated ECDSA keypair.")?;
        let raw_key = Self::parse_cose_mac0_for_coords(&maced_key.macedKey)
            .context("In generate_key_pair: Failed to parse raw key")?;
        db.create_attestation_key_entry(&maced_key.macedKey, &raw_key, &priv_key, &uuid)
            .context("In generate_key_pair: Failed to insert attestation key entry")
    }

    /// Checks the security level of each available IRemotelyProvisionedComponent hal and returns
    /// all levels in an array to the caller.
    pub fn get_implementation_info(&self) -> Result<Vec<ImplInfo>> {
        Ok(self
            .curve_by_sec_level
            .iter()
            .map(|(sec_level, curve)| ImplInfo { secLevel: *sec_level, supportedCurve: *curve })
            .collect())
    }

    /// Deletes all attestation keys generated by the IRemotelyProvisionedComponent from the device,
    /// regardless of what state of the attestation key lifecycle they were in.
    pub fn delete_all_keys(&self) -> Result<i64> {
        DB.with::<_, Result<i64>>(|db| {
            let mut db = db.borrow_mut();
            db.delete_all_attestation_keys()
        })
    }
}

/// Populates the AttestationPoolStatus parcelable with information about how many
/// certs will be expiring by the date provided in `expired_by` along with how many
/// keys have not yet been assigned.
pub fn get_pool_status(expired_by: i64, sec_level: SecurityLevel) -> Result<AttestationPoolStatus> {
    let (_, _, uuid) = get_keymint_device(&sec_level)?;
    DB.with::<_, Result<AttestationPoolStatus>>(|db| {
        let mut db = db.borrow_mut();
        // delete_expired_attestation_keys is always safe to call, and will remove anything
        // older than the date at the time of calling. No work should be done on the
        // attestation keys unless the pool status is checked first, so this call should be
        // enough to routinely clean out expired keys.
        db.delete_expired_attestation_keys()?;
        db.get_attestation_pool_status(expired_by, &uuid)
    })
}

/// Fetches a remote provisioning attestation key and certificate chain inside of the
/// returned `CertificateChain` struct if one exists for the given caller_uid. If one has not
/// been assigned, this function will assign it. If there are no signed attestation keys
/// available to be assigned, it will return the ResponseCode `OUT_OF_KEYS`
fn get_rem_prov_attest_key(
    domain: Domain,
    caller_uid: u32,
    db: &mut KeystoreDB,
    km_uuid: &Uuid,
) -> Result<Option<(KeyIdGuard, CertificateChain)>> {
    match domain {
        Domain::APP => {
            // Attempt to get an Attestation Key once. If it fails, then the app doesn't
            // have a valid chain assigned to it. The helper function will return None after
            // attempting to assign a key. An error will be thrown if the pool is simply out
            // of usable keys. Then another attempt to fetch the just-assigned key will be
            // made. If this fails too, something is very wrong.
            get_rem_prov_attest_key_helper(domain, caller_uid, db, km_uuid)
                .context("In get_rem_prov_attest_key: Failed to get a key")?
                .map_or_else(
                    || get_rem_prov_attest_key_helper(domain, caller_uid, db, km_uuid),
                    |v| Ok(Some(v)),
                )
                .context(concat!(
                    "In get_rem_prov_attest_key: Failed to get a key after",
                    "attempting to assign one."
                ))?
                .map_or_else(
                    || {
                        Err(Error::sys()).context(concat!(
                            "In get_rem_prov_attest_key: Attempted to assign a ",
                            "key and failed silently. Something is very wrong."
                        ))
                    },
                    |(guard, cert_chain)| Ok(Some((guard, cert_chain))),
                )
        }
        _ => Ok(None),
    }
}

/// Returns None if an AttestationKey fails to be assigned. Errors if no keys are available.
fn get_rem_prov_attest_key_helper(
    domain: Domain,
    caller_uid: u32,
    db: &mut KeystoreDB,
    km_uuid: &Uuid,
) -> Result<Option<(KeyIdGuard, CertificateChain)>> {
    let guard_and_chain = db
        .retrieve_attestation_key_and_cert_chain(domain, caller_uid as i64, km_uuid)
        .context("In get_rem_prov_attest_key_helper: Failed to retrieve a key + cert chain")?;
    match guard_and_chain {
        Some((guard, cert_chain)) => Ok(Some((guard, cert_chain))),
        // Either this app needs to be assigned a key, or the pool is empty. An error will
        // be thrown if there is no key available to assign. This will indicate that the app
        // should be nudged to provision more keys so keystore can retry.
        None => {
            db.assign_attestation_key(domain, caller_uid as i64, km_uuid)
                .context("In get_rem_prov_attest_key_helper: Failed to assign a key")?;
            Ok(None)
        }
    }
}

impl binder::Interface for RemoteProvisioningService {}

// Implementation of IRemoteProvisioning. See AIDL spec at
// :aidl/android/security/remoteprovisioning/IRemoteProvisioning.aidl
impl IRemoteProvisioning for RemoteProvisioningService {
    fn getPoolStatus(
        &self,
        expired_by: i64,
        sec_level: SecurityLevel,
    ) -> binder::Result<AttestationPoolStatus> {
        let _wp = wd::watch_millis("IRemoteProvisioning::getPoolStatus", 500);
        map_or_log_err(get_pool_status(expired_by, sec_level), Ok)
    }

    fn generateCsr(
        &self,
        test_mode: bool,
        num_csr: i32,
        eek: &[u8],
        challenge: &[u8],
        sec_level: SecurityLevel,
        protected_data: &mut ProtectedData,
        device_info: &mut DeviceInfo,
    ) -> binder::Result<Vec<u8>> {
        let _wp = wd::watch_millis("IRemoteProvisioning::generateCsr", 500);
        map_or_log_err(
            self.generate_csr(
                test_mode,
                num_csr,
                eek,
                challenge,
                sec_level,
                protected_data,
                device_info,
            ),
            Ok,
        )
    }

    fn provisionCertChain(
        &self,
        public_key: &[u8],
        batch_cert: &[u8],
        certs: &[u8],
        expiration_date: i64,
        sec_level: SecurityLevel,
    ) -> binder::Result<()> {
        let _wp = wd::watch_millis("IRemoteProvisioning::provisionCertChain", 500);
        DB.with::<_, binder::Result<()>>(|db| {
            map_or_log_err(
                self.provision_cert_chain(
                    &mut db.borrow_mut(),
                    public_key,
                    batch_cert,
                    certs,
                    expiration_date,
                    sec_level,
                ),
                Ok,
            )
        })
    }

    fn generateKeyPair(&self, is_test_mode: bool, sec_level: SecurityLevel) -> binder::Result<()> {
        let _wp = wd::watch_millis("IRemoteProvisioning::generateKeyPair", 500);
        DB.with::<_, binder::Result<()>>(|db| {
            map_or_log_err(
                self.generate_key_pair(&mut db.borrow_mut(), is_test_mode, sec_level),
                Ok,
            )
        })
    }

    fn getImplementationInfo(&self) -> binder::Result<Vec<ImplInfo>> {
        let _wp = wd::watch_millis("IRemoteProvisioning::getSecurityLevels", 500);
        map_or_log_err(self.get_implementation_info(), Ok)
    }

    fn deleteAllKeys(&self) -> binder::Result<i64> {
        let _wp = wd::watch_millis("IRemoteProvisioning::deleteAllKeys", 500);
        map_or_log_err(self.delete_all_keys(), Ok)
    }
}

/// Implementation of the IRemotelyProvisionedKeyPool service.
#[derive(Default)]
pub struct RemotelyProvisionedKeyPoolService {
    unique_id_to_sec_level: HashMap<String, SecurityLevel>,
}

impl RemotelyProvisionedKeyPoolService {
    /// Fetches a remotely provisioned certificate chain and key for the given client uid that
    /// was provisioned using the IRemotelyProvisionedComponent with the given id. The same key
    /// will be returned for a given caller_uid on every request. If there are no attestation keys
    /// available, `OUT_OF_KEYS` is returned.
    fn get_attestation_key(
        &self,
        db: &mut KeystoreDB,
        caller_uid: i32,
        irpc_id: &str,
    ) -> Result<RemotelyProvisionedKey> {
        log::info!("get_attestation_key(self, {}, {}", caller_uid, irpc_id);

        let sec_level = self
            .unique_id_to_sec_level
            .get(irpc_id)
            .ok_or(Error::Rc(ResponseCode::INVALID_ARGUMENT))
            .context(format!("In get_attestation_key: unknown irpc id '{}'", irpc_id))?;
        let (_, _, km_uuid) = get_keymint_device(sec_level)?;

        let guard_and_cert_chain =
            get_rem_prov_attest_key(Domain::APP, caller_uid as u32, db, &km_uuid)
                .context("In get_attestation_key")?;
        match guard_and_cert_chain {
            Some((_, chain)) => Ok(RemotelyProvisionedKey {
                keyBlob: chain.private_key.to_vec(),
                encodedCertChain: chain.cert_chain,
            }),
            // It should be impossible to get `None`, but handle it just in case as a
            // precaution against future behavioral changes in `get_rem_prov_attest_key`.
            None => Err(error::Error::Rc(ResponseCode::OUT_OF_KEYS))
                .context("In get_attestation_key: No available attestation keys"),
        }
    }

    /// Creates a new instance of the remotely provisioned key pool service, used for fetching
    /// remotely provisioned attestation keys.
    pub fn new_native_binder() -> Result<Strong<dyn IRemotelyProvisionedKeyPool>> {
        let mut result: Self = Default::default();

        let dev = get_remotely_provisioned_component(&SecurityLevel::TRUSTED_ENVIRONMENT)
            .context("In new_native_binder: Failed to get TEE Remote Provisioner instance.")?;
        if let Some(id) = dev.getHardwareInfo()?.uniqueId {
            result.unique_id_to_sec_level.insert(id, SecurityLevel::TRUSTED_ENVIRONMENT);
        }

        if let Ok(dev) = get_remotely_provisioned_component(&SecurityLevel::STRONGBOX) {
            if let Some(id) = dev.getHardwareInfo()?.uniqueId {
                if result.unique_id_to_sec_level.contains_key(&id) {
                    anyhow::bail!("In new_native_binder: duplicate irpc id found: '{}'", id)
                }
                result.unique_id_to_sec_level.insert(id, SecurityLevel::STRONGBOX);
            }
        }

        // If none of the remotely provisioned components have unique ids, then we shouldn't
        // bother publishing the service, as it's impossible to match keys with their backends.
        if result.unique_id_to_sec_level.is_empty() {
            anyhow::bail!(
                "In new_native_binder: No remotely provisioned components have unique ids"
            )
        }

        Ok(BnRemotelyProvisionedKeyPool::new_binder(
            result,
            BinderFeatures { set_requesting_sid: true, ..BinderFeatures::default() },
        ))
    }
}

impl binder::Interface for RemotelyProvisionedKeyPoolService {}

// Implementation of IRemotelyProvisionedKeyPool. See AIDL spec at
// :aidl/android/security/remoteprovisioning/IRemotelyProvisionedKeyPool.aidl
impl IRemotelyProvisionedKeyPool for RemotelyProvisionedKeyPoolService {
    fn getAttestationKey(
        &self,
        caller_uid: i32,
        irpc_id: &str,
    ) -> binder::Result<RemotelyProvisionedKey> {
        let _wp = wd::watch_millis("IRemotelyProvisionedKeyPool::getAttestationKey", 500);
        map_or_log_err(check_keystore_permission(KeystorePerm::GetAttestationKey), Ok)?;
        DB.with::<_, binder::Result<RemotelyProvisionedKey>>(|db| {
            map_or_log_err(self.get_attestation_key(&mut db.borrow_mut(), caller_uid, irpc_id), Ok)
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_cbor::Value;
    use std::collections::BTreeMap;
    use std::sync::{Arc, Mutex};
    use android_hardware_security_keymint::aidl::android::hardware::security::keymint::{
        RpcHardwareInfo::RpcHardwareInfo,
    };

    #[derive(Default)]
    struct MockRemotelyProvisionedComponentValues {
        hw_info: RpcHardwareInfo,
        private_key: Vec<u8>,
        maced_public_key: Vec<u8>,
    }

    // binder::Interface requires the Send trait, so we have to use a Mutex even though the test
    // is single threaded.
    #[derive(Default)]
    struct MockRemotelyProvisionedComponent(Arc<Mutex<MockRemotelyProvisionedComponentValues>>);

    impl binder::Interface for MockRemotelyProvisionedComponent {}

    impl IRemotelyProvisionedComponent for MockRemotelyProvisionedComponent {
        fn getHardwareInfo(&self) -> binder::Result<RpcHardwareInfo> {
            Ok(self.0.lock().unwrap().hw_info.clone())
        }

        fn generateEcdsaP256KeyPair(
            &self,
            test_mode: bool,
            maced_public_key: &mut MacedPublicKey,
        ) -> binder::Result<Vec<u8>> {
            assert!(test_mode);
            maced_public_key.macedKey = self.0.lock().unwrap().maced_public_key.clone();
            Ok(self.0.lock().unwrap().private_key.clone())
        }

        fn generateCertificateRequest(
            &self,
            _test_mode: bool,
            _keys_to_sign: &[MacedPublicKey],
            _eek: &[u8],
            _challenge: &[u8],
            _device_info: &mut DeviceInfo,
            _protected_data: &mut ProtectedData,
        ) -> binder::Result<Vec<u8>> {
            Err(binder::StatusCode::INVALID_OPERATION.into())
        }
    }

    // Hard coded cert that can be parsed -- the content doesn't matter for testing, only that it's valid.
    fn get_fake_cert() -> Vec<u8> {
        vec![
            0x30, 0x82, 0x01, 0xbb, 0x30, 0x82, 0x01, 0x61, 0xa0, 0x03, 0x02, 0x01, 0x02, 0x02,
            0x14, 0x3a, 0xd5, 0x67, 0xce, 0xfe, 0x93, 0xe1, 0xea, 0xb7, 0xe4, 0xbf, 0x64, 0x19,
            0xa4, 0x11, 0xe1, 0x87, 0x40, 0x20, 0x37, 0x30, 0x0a, 0x06, 0x08, 0x2a, 0x86, 0x48,
            0xce, 0x3d, 0x04, 0x03, 0x02, 0x30, 0x33, 0x31, 0x0b, 0x30, 0x09, 0x06, 0x03, 0x55,
            0x04, 0x06, 0x13, 0x02, 0x55, 0x54, 0x31, 0x13, 0x30, 0x11, 0x06, 0x03, 0x55, 0x04,
            0x08, 0x0c, 0x0a, 0x53, 0x6f, 0x6d, 0x65, 0x2d, 0x53, 0x74, 0x61, 0x74, 0x65, 0x31,
            0x0f, 0x30, 0x0d, 0x06, 0x03, 0x55, 0x04, 0x0a, 0x0c, 0x06, 0x47, 0x6f, 0x6f, 0x67,
            0x6c, 0x65, 0x30, 0x1e, 0x17, 0x0d, 0x32, 0x31, 0x31, 0x32, 0x31, 0x30, 0x32, 0x32,
            0x30, 0x38, 0x35, 0x32, 0x5a, 0x17, 0x0d, 0x34, 0x39, 0x30, 0x34, 0x32, 0x36, 0x32,
            0x32, 0x30, 0x38, 0x35, 0x32, 0x5a, 0x30, 0x33, 0x31, 0x0b, 0x30, 0x09, 0x06, 0x03,
            0x55, 0x04, 0x06, 0x13, 0x02, 0x55, 0x54, 0x31, 0x13, 0x30, 0x11, 0x06, 0x03, 0x55,
            0x04, 0x08, 0x0c, 0x0a, 0x53, 0x6f, 0x6d, 0x65, 0x2d, 0x53, 0x74, 0x61, 0x74, 0x65,
            0x31, 0x0f, 0x30, 0x0d, 0x06, 0x03, 0x55, 0x04, 0x0a, 0x0c, 0x06, 0x47, 0x6f, 0x6f,
            0x67, 0x6c, 0x65, 0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d,
            0x02, 0x01, 0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42,
            0x00, 0x04, 0x1e, 0xac, 0x0c, 0xe0, 0x0d, 0xc5, 0x25, 0x84, 0x1b, 0xd2, 0x77, 0x2d,
            0xe7, 0xba, 0xf1, 0xde, 0xa7, 0xf6, 0x39, 0x7f, 0x38, 0x91, 0xbf, 0xa4, 0x58, 0xf5,
            0x62, 0x6b, 0xce, 0x06, 0xcf, 0xb9, 0x73, 0x91, 0x0d, 0x8a, 0x60, 0xa0, 0xc6, 0xa2,
            0x22, 0xe6, 0x51, 0x2e, 0x58, 0xd6, 0x43, 0x02, 0x80, 0x43, 0x44, 0x29, 0x38, 0x9a,
            0x99, 0xf3, 0xa4, 0xdd, 0xd0, 0xb4, 0x6f, 0x8b, 0x44, 0x2d, 0xa3, 0x53, 0x30, 0x51,
            0x30, 0x1d, 0x06, 0x03, 0x55, 0x1d, 0x0e, 0x04, 0x16, 0x04, 0x14, 0xdb, 0x13, 0x68,
            0xe0, 0x0e, 0x47, 0x10, 0xf8, 0xcb, 0x88, 0x83, 0xfe, 0x42, 0x3c, 0xd9, 0x3f, 0x1a,
            0x33, 0xe9, 0xaa, 0x30, 0x1f, 0x06, 0x03, 0x55, 0x1d, 0x23, 0x04, 0x18, 0x30, 0x16,
            0x80, 0x14, 0xdb, 0x13, 0x68, 0xe0, 0x0e, 0x47, 0x10, 0xf8, 0xcb, 0x88, 0x83, 0xfe,
            0x42, 0x3c, 0xd9, 0x3f, 0x1a, 0x33, 0xe9, 0xaa, 0x30, 0x0f, 0x06, 0x03, 0x55, 0x1d,
            0x13, 0x01, 0x01, 0xff, 0x04, 0x05, 0x30, 0x03, 0x01, 0x01, 0xff, 0x30, 0x0a, 0x06,
            0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x02, 0x03, 0x48, 0x00, 0x30, 0x45,
            0x02, 0x20, 0x10, 0xdf, 0x40, 0xc3, 0x20, 0x54, 0x36, 0xb5, 0xc9, 0x3c, 0x70, 0xe3,
            0x55, 0x37, 0xd2, 0x04, 0x51, 0xeb, 0x0f, 0x18, 0x83, 0xd0, 0x58, 0xa1, 0x08, 0x77,
            0x8d, 0x4d, 0xa4, 0x20, 0xee, 0x33, 0x02, 0x21, 0x00, 0x8d, 0xe3, 0xa6, 0x6c, 0x0d,
            0x86, 0x25, 0xdc, 0x59, 0x0d, 0x21, 0x43, 0x22, 0x3a, 0xb9, 0xa1, 0x73, 0x28, 0xc9,
            0x16, 0x9e, 0x91, 0x15, 0xc4, 0xc3, 0xd7, 0xeb, 0xe5, 0xce, 0xdc, 0x1c, 0x1b,
        ]
    }

    // Generate a fake COSE_Mac0 with a key that's just `byte` repeated
    fn generate_maced_pubkey(byte: u8) -> Vec<u8> {
        vec![
            0x84, 0x43, 0xA1, 0x01, 0x05, 0xA0, 0x58, 0x4D, 0xA5, 0x01, 0x02, 0x03, 0x26, 0x20,
            0x01, 0x21, 0x58, 0x20, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte,
            byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte,
            byte, byte, byte, byte, byte, byte, byte, byte, 0x22, 0x58, 0x20, byte, byte, byte,
            byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte,
            byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte,
            byte, 0x58, 0x20, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte,
            byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte, byte,
            byte, byte, byte, byte, byte, byte, byte,
        ]
    }

    #[test]
    fn test_parse_cose_mac0_for_coords_raw_bytes() -> Result<()> {
        let cose_mac0: Vec<u8> = vec![
            0x84, 0x01, 0x02, 0x58, 0x4D, 0xA5, 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58,
            0x20, 0x1A, 0xFB, 0xB2, 0xD9, 0x9D, 0xF6, 0x2D, 0xF0, 0xC3, 0xA8, 0xFC, 0x7E, 0xC9,
            0x21, 0x26, 0xED, 0xB5, 0x4A, 0x98, 0x9B, 0xF3, 0x0D, 0x91, 0x3F, 0xC6, 0x42, 0x5C,
            0x43, 0x22, 0xC8, 0xEE, 0x03, 0x22, 0x58, 0x20, 0x40, 0xB3, 0x9B, 0xFC, 0x47, 0x95,
            0x90, 0xA7, 0x5C, 0x5A, 0x16, 0x31, 0x34, 0xAF, 0x0C, 0x5B, 0xF2, 0xB2, 0xD8, 0x2A,
            0xA3, 0xB3, 0x1A, 0xB4, 0x4C, 0xA6, 0x3B, 0xE7, 0x22, 0xEC, 0x41, 0xDC, 0x03,
        ];
        let raw_key = RemoteProvisioningService::parse_cose_mac0_for_coords(&cose_mac0)?;
        assert_eq!(
            raw_key,
            vec![
                0x1A, 0xFB, 0xB2, 0xD9, 0x9D, 0xF6, 0x2D, 0xF0, 0xC3, 0xA8, 0xFC, 0x7E, 0xC9, 0x21,
                0x26, 0xED, 0xB5, 0x4A, 0x98, 0x9B, 0xF3, 0x0D, 0x91, 0x3F, 0xC6, 0x42, 0x5C, 0x43,
                0x22, 0xC8, 0xEE, 0x03, 0x40, 0xB3, 0x9B, 0xFC, 0x47, 0x95, 0x90, 0xA7, 0x5C, 0x5A,
                0x16, 0x31, 0x34, 0xAF, 0x0C, 0x5B, 0xF2, 0xB2, 0xD8, 0x2A, 0xA3, 0xB3, 0x1A, 0xB4,
                0x4C, 0xA6, 0x3B, 0xE7, 0x22, 0xEC, 0x41, 0xDC,
            ]
        );
        Ok(())
    }

    #[test]
    fn test_parse_cose_mac0_for_coords_constructed_mac() -> Result<()> {
        let x_coord: Vec<u8> = vec![0; 32];
        let y_coord: Vec<u8> = vec![1; 32];
        let mut expected_key: Vec<u8> = Vec::new();
        expected_key.extend(&x_coord);
        expected_key.extend(&y_coord);
        let key_map: BTreeMap<Value, Value> = BTreeMap::from([
            (Value::Integer(1), Value::Integer(2)),
            (Value::Integer(3), Value::Integer(-7)),
            (Value::Integer(-1), Value::Integer(1)),
            (Value::Integer(-2), Value::Bytes(x_coord)),
            (Value::Integer(-3), Value::Bytes(y_coord)),
        ]);
        let cose_mac0: Vec<Value> = vec![
            Value::Integer(0),
            Value::Integer(1),
            Value::from(serde_cbor::to_vec(&key_map)?),
            Value::Integer(2),
        ];
        let raw_key = RemoteProvisioningService::parse_cose_mac0_for_coords(&serde_cbor::to_vec(
            &Value::from(cose_mac0),
        )?)?;
        assert_eq!(expected_key, raw_key);
        Ok(())
    }

    #[test]
    fn test_extract_payload_from_cose_mac() -> Result<()> {
        let key_map = Value::Map(BTreeMap::from([(Value::Integer(1), Value::Integer(2))]));
        let payload = Value::Bytes(serde_cbor::to_vec(&key_map)?);
        let cose_mac0 =
            Value::Array(vec![Value::Integer(0), Value::Integer(1), payload, Value::Integer(3)]);
        let extracted_map = RemoteProvisioningService::extract_payload_from_cose_mac(
            &serde_cbor::to_vec(&cose_mac0)?,
        )?;
        assert_eq!(key_map, extracted_map);
        Ok(())
    }

    #[test]
    fn test_extract_payload_from_cose_mac_fails_malformed_payload() -> Result<()> {
        let payload = Value::Bytes(vec![5; 10]);
        let cose_mac0 =
            Value::Array(vec![Value::Integer(0), Value::Integer(1), payload, Value::Integer(3)]);
        let extracted_payload = RemoteProvisioningService::extract_payload_from_cose_mac(
            &serde_cbor::to_vec(&cose_mac0)?,
        );
        assert!(extracted_payload.is_err());
        Ok(())
    }

    #[test]
    fn test_extract_payload_from_cose_mac_fails_type() -> Result<()> {
        let payload = Value::Integer(1);
        let cose_mac0 =
            Value::Array(vec![Value::Integer(0), Value::Integer(1), payload, Value::Integer(3)]);
        let extracted_payload = RemoteProvisioningService::extract_payload_from_cose_mac(
            &serde_cbor::to_vec(&cose_mac0)?,
        );
        assert!(extracted_payload.is_err());
        Ok(())
    }

    #[test]
    fn test_extract_payload_from_cose_mac_fails_length() -> Result<()> {
        let cose_mac0 = Value::Array(vec![Value::Integer(0), Value::Integer(1)]);
        let extracted_payload = RemoteProvisioningService::extract_payload_from_cose_mac(
            &serde_cbor::to_vec(&cose_mac0)?,
        );
        assert!(extracted_payload.is_err());
        Ok(())
    }

    #[test]
    #[ignore] // b/215746308
    fn test_get_attestation_key_no_keys_provisioned() {
        let mut db = crate::database::tests::new_test_db().unwrap();
        let mock_rpc = Box::<MockRemotelyProvisionedComponent>::default();
        mock_rpc.0.lock().unwrap().hw_info.uniqueId = Some(String::from("mallory"));

        let mut service: RemotelyProvisionedKeyPoolService = Default::default();
        service
            .unique_id_to_sec_level
            .insert(String::from("mallory"), SecurityLevel::TRUSTED_ENVIRONMENT);

        assert_eq!(
            service
                .get_attestation_key(&mut db, 0, "mallory")
                .unwrap_err()
                .downcast::<error::Error>()
                .unwrap(),
            error::Error::Rc(ResponseCode::OUT_OF_KEYS)
        );
    }

    #[test]
    #[ignore] // b/215746308
    fn test_get_attestation_key() {
        let mut db = crate::database::tests::new_test_db().unwrap();
        let sec_level = SecurityLevel::TRUSTED_ENVIRONMENT;
        let irpc_id = "paul";
        let caller_uid = 0;

        let mock_rpc = Box::<MockRemotelyProvisionedComponent>::default();
        let mock_values = mock_rpc.0.clone();
        let mut remote_provisioning: RemoteProvisioningService = Default::default();
        remote_provisioning.device_by_sec_level.insert(sec_level, Strong::new(mock_rpc));
        let mut key_pool: RemotelyProvisionedKeyPoolService = Default::default();
        key_pool.unique_id_to_sec_level.insert(String::from(irpc_id), sec_level);

        mock_values.lock().unwrap().hw_info.uniqueId = Some(String::from(irpc_id));
        mock_values.lock().unwrap().private_key = vec![8, 6, 7, 5, 3, 0, 9];
        mock_values.lock().unwrap().maced_public_key = generate_maced_pubkey(0x11);
        remote_provisioning.generate_key_pair(&mut db, true, sec_level).unwrap();

        let public_key = RemoteProvisioningService::parse_cose_mac0_for_coords(
            mock_values.lock().unwrap().maced_public_key.as_slice(),
        )
        .unwrap();
        let batch_cert = get_fake_cert();
        let certs = &[5, 6, 7, 8];
        assert!(remote_provisioning
            .provision_cert_chain(
                &mut db,
                public_key.as_slice(),
                batch_cert.as_slice(),
                certs,
                0,
                sec_level
            )
            .is_ok());

        // ensure we got the key we expected
        let first_key = key_pool
            .get_attestation_key(&mut db, caller_uid, irpc_id)
            .context("get first key")
            .unwrap();
        assert_eq!(first_key.keyBlob, mock_values.lock().unwrap().private_key);
        assert_eq!(first_key.encodedCertChain, certs);

        // ensure that multiple calls get the same key
        assert_eq!(
            first_key,
            key_pool
                .get_attestation_key(&mut db, caller_uid, irpc_id)
                .context("get second key")
                .unwrap()
        );

        // no more keys for new clients
        assert_eq!(
            key_pool
                .get_attestation_key(&mut db, caller_uid + 1, irpc_id)
                .unwrap_err()
                .downcast::<error::Error>()
                .unwrap(),
            error::Error::Rc(ResponseCode::OUT_OF_KEYS)
        );
    }

    #[test]
    #[ignore] // b/215746308
    fn test_get_attestation_key_gets_different_key_for_different_client() {
        let mut db = crate::database::tests::new_test_db().unwrap();
        let sec_level = SecurityLevel::TRUSTED_ENVIRONMENT;
        let irpc_id = "ringo";
        let first_caller = 0;
        let second_caller = first_caller + 1;

        let mock_rpc = Box::<MockRemotelyProvisionedComponent>::default();
        let mock_values = mock_rpc.0.clone();
        let mut remote_provisioning: RemoteProvisioningService = Default::default();
        remote_provisioning.device_by_sec_level.insert(sec_level, Strong::new(mock_rpc));
        let mut key_pool: RemotelyProvisionedKeyPoolService = Default::default();
        key_pool.unique_id_to_sec_level.insert(String::from(irpc_id), sec_level);

        // generate two distinct keys and provision them with certs
        mock_values.lock().unwrap().hw_info.uniqueId = Some(String::from(irpc_id));
        mock_values.lock().unwrap().private_key = vec![3, 1, 4, 1, 5];
        mock_values.lock().unwrap().maced_public_key = generate_maced_pubkey(0x11);
        assert!(remote_provisioning.generate_key_pair(&mut db, true, sec_level).is_ok());
        let public_key = RemoteProvisioningService::parse_cose_mac0_for_coords(
            mock_values.lock().unwrap().maced_public_key.as_slice(),
        )
        .unwrap();
        assert!(remote_provisioning
            .provision_cert_chain(
                &mut db,
                public_key.as_slice(),
                get_fake_cert().as_slice(),
                &[1],
                0,
                sec_level
            )
            .is_ok());

        mock_values.lock().unwrap().hw_info.uniqueId = Some(String::from(irpc_id));
        mock_values.lock().unwrap().private_key = vec![9, 0, 2, 1, 0];
        mock_values.lock().unwrap().maced_public_key = generate_maced_pubkey(0x22);
        assert!(remote_provisioning.generate_key_pair(&mut db, true, sec_level).is_ok());
        let public_key = RemoteProvisioningService::parse_cose_mac0_for_coords(
            mock_values.lock().unwrap().maced_public_key.as_slice(),
        )
        .unwrap();
        assert!(remote_provisioning
            .provision_cert_chain(
                &mut db,
                public_key.as_slice(),
                get_fake_cert().as_slice(),
                &[2],
                0,
                sec_level
            )
            .is_ok());

        // make sure each caller gets a distinct key
        assert_ne!(
            key_pool
                .get_attestation_key(&mut db, first_caller, irpc_id)
                .context("get first key")
                .unwrap(),
            key_pool
                .get_attestation_key(&mut db, second_caller, irpc_id)
                .context("get second key")
                .unwrap()
        );

        // repeated calls should return the same key for a given caller
        assert_eq!(
            key_pool
                .get_attestation_key(&mut db, first_caller, irpc_id)
                .context("first caller a")
                .unwrap(),
            key_pool
                .get_attestation_key(&mut db, first_caller, irpc_id)
                .context("first caller b")
                .unwrap(),
        );

        assert_eq!(
            key_pool
                .get_attestation_key(&mut db, second_caller, irpc_id)
                .context("second caller a")
                .unwrap(),
            key_pool
                .get_attestation_key(&mut db, second_caller, irpc_id)
                .context("second caller b")
                .unwrap()
        );
    }
}
