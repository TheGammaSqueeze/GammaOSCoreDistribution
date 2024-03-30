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

//! Provides routines to read/write on the instance disk.
//!
//! Instance disk is a disk where the identity of a VM instance is recorded. The identity usually
//! includes certificates of the VM payload that is trusted, but not limited to it. Instance disk
//! is empty when a VM is first booted. The identity data is filled in during the first boot, and
//! then encrypted and signed. Subsequent boots decrypts and authenticates the data and uses the
//! identity data to further verify the payload (e.g. against the certificate).
//!
//! Instance disk consists of a disk header and one or more partitions each of which consists of a
//! header and payload. Each header (both the disk header and a partition header) is 512 bytes
//! long. Payload is just next to the header and its size can be arbitrary. Headers are located at
//! 512 bytes boundaries. So, when the size of a payload is not multiple of 512, there exists a gap
//! between the end of the payload and the start of the next partition (if there is any).
//!
//! Each partition is identified by a UUID. A partition is created for a program loader that
//! participates in the boot chain of the VM. Each program loader is expected to locate the
//! partition that corresponds to the loader using the UUID that is assigned to the loader.
//!
//! The payload of a partition is encrypted/signed by a key that is unique to the loader and to the
//! VM as well. Failing to decrypt/authenticate a partition by a loader stops the boot process.

use crate::ioutil;

use android_security_dice::aidl::android::security::dice::IDiceNode::IDiceNode;
use anyhow::{anyhow, bail, Context, Result};
use binder::wait_for_interface;
use byteorder::{LittleEndian, ReadBytesExt, WriteBytesExt};
use ring::aead::{Aad, Algorithm, LessSafeKey, Nonce, UnboundKey, AES_256_GCM};
use ring::hkdf::{Salt, HKDF_SHA256};
use serde::{Deserialize, Serialize};
use std::fs::{File, OpenOptions};
use std::io::{Read, Seek, SeekFrom, Write};
use uuid::Uuid;

/// Path to the instance disk inside the VM
const INSTANCE_IMAGE_PATH: &str = "/dev/block/by-name/vm-instance";

/// Magic string in the instance disk header
const DISK_HEADER_MAGIC: &str = "Android-VM-instance";

/// Version of the instance disk format
const DISK_HEADER_VERSION: u16 = 1;

/// Size of the headers in the instance disk
const DISK_HEADER_SIZE: u64 = 512;
const PARTITION_HEADER_SIZE: u64 = 512;

/// UUID of the partition that microdroid manager uses
const MICRODROID_PARTITION_UUID: &str = "cf9afe9a-0662-11ec-a329-c32663a09d75";

/// Encryption algorithm used to cipher payload
static ENCRYPT_ALG: &Algorithm = &AES_256_GCM;

/// Handle to the instance disk
pub struct InstanceDisk {
    file: File,
}

/// Information from a partition header
struct PartitionHeader {
    uuid: Uuid,
    payload_size: u64, // in bytes
}

/// Offset of a partition in the instance disk
type PartitionOffset = u64;

impl InstanceDisk {
    /// Creates handle to instance disk
    pub fn new() -> Result<Self> {
        let mut file = OpenOptions::new()
            .read(true)
            .write(true)
            .open(INSTANCE_IMAGE_PATH)
            .with_context(|| format!("Failed to open {}", INSTANCE_IMAGE_PATH))?;

        // Check if this file is a valid instance disk by examining the header (the first block)
        let mut magic = [0; DISK_HEADER_MAGIC.len()];
        file.read_exact(&mut magic)?;
        if magic != DISK_HEADER_MAGIC.as_bytes() {
            bail!("invalid magic: {:?}", magic);
        }

        let version = file.read_u16::<LittleEndian>()?;
        if version == 0 {
            bail!("invalid version: {}", version);
        }
        if version > DISK_HEADER_VERSION {
            bail!("unsupported version: {}", version);
        }

        Ok(Self { file })
    }

    /// Reads the identity data that was written by microdroid manager. The returned data is
    /// plaintext, although it is stored encrypted. In case when the partition for microdroid
    /// manager doesn't exist, which can happen if it's the first boot, `Ok(None)` is returned.
    pub fn read_microdroid_data(&mut self) -> Result<Option<MicrodroidData>> {
        let (header, offset) = self.locate_microdroid_header()?;
        if header.is_none() {
            return Ok(None);
        }
        let header = header.unwrap();
        let payload_offset = offset + PARTITION_HEADER_SIZE;
        self.file.seek(SeekFrom::Start(payload_offset))?;

        // Read the 12-bytes nonce (unencrypted)
        let mut nonce = [0; 12];
        self.file.read_exact(&mut nonce)?;
        let nonce = Nonce::assume_unique_for_key(nonce);

        // Read the encrypted payload
        let payload_size = header.payload_size - 12; // we already have read the nonce
        let mut data = vec![0; payload_size as usize];
        self.file.read_exact(&mut data)?;

        // Read the header as well because it's part of the signed data (though not encrypted).
        let mut header = [0; PARTITION_HEADER_SIZE as usize];
        self.file.seek(SeekFrom::Start(offset))?;
        self.file.read_exact(&mut header)?;

        // Decrypt and authenticate the data (along with the header). The data is decrypted in
        // place. `open_in_place` returns slice to the decrypted part in the buffer.
        let plaintext_len = get_key()?.open_in_place(nonce, Aad::from(&header), &mut data)?.len();
        // Truncate to remove the tag
        data.truncate(plaintext_len);

        let microdroid_data = serde_cbor::from_slice(data.as_slice())?;
        Ok(Some(microdroid_data))
    }

    /// Writes identity data to the partition for microdroid manager. The partition is appended
    /// if it doesn't exist. The data is stored encrypted.
    pub fn write_microdroid_data(&mut self, microdroid_data: &MicrodroidData) -> Result<()> {
        let (header, offset) = self.locate_microdroid_header()?;

        let mut data = serde_cbor::to_vec(microdroid_data)?;

        // By encrypting and signing the data, tag will be appended. The tag also becomes part of
        // the encrypted payload which will be written. In addition, a 12-bytes nonce will be
        // prepended (non-encrypted).
        let payload_size = (data.len() + ENCRYPT_ALG.tag_len() + 12) as u64;

        // If the partition exists, make sure we don't change the partition size. If not (i.e.
        // partition is not found), write the header at the empty place.
        if let Some(header) = header {
            if header.payload_size != payload_size {
                bail!("Can't change payload size from {} to {}", header.payload_size, payload_size);
            }
        } else {
            let uuid = Uuid::parse_str(MICRODROID_PARTITION_UUID)?;
            self.write_header_at(offset, &uuid, payload_size)?;
        }

        // Read the header as it is used as additionally authenticated data (AAD).
        let mut header = [0; PARTITION_HEADER_SIZE as usize];
        self.file.seek(SeekFrom::Start(offset))?;
        self.file.read_exact(&mut header)?;

        // Generate a nonce randomly and recorde it on the disk first.
        let nonce = Nonce::assume_unique_for_key(rand::random::<[u8; 12]>());
        self.file.seek(SeekFrom::Start(offset + PARTITION_HEADER_SIZE))?;
        self.file.write_all(nonce.as_ref())?;

        // Then encrypt and sign the data. The non-encrypted input data is copied to a vector
        // because it is encrypted in place, and also the tag is appended.
        get_key()?.seal_in_place_append_tag(nonce, Aad::from(&header), &mut data)?;

        // Persist the encrypted payload data
        self.file.write_all(&data)?;
        ioutil::blkflsbuf(&mut self.file)?;

        Ok(())
    }

    /// Read header at `header_offset` and parse it into a `PartitionHeader`.
    fn read_header_at(&mut self, header_offset: u64) -> Result<PartitionHeader> {
        assert!(
            header_offset % PARTITION_HEADER_SIZE == 0,
            "header offset {} is not aligned to 512 bytes",
            header_offset
        );

        let mut uuid = [0; 16];
        self.file.seek(SeekFrom::Start(header_offset))?;
        self.file.read_exact(&mut uuid)?;
        let uuid = Uuid::from_bytes(uuid);
        let payload_size = self.file.read_u64::<LittleEndian>()?;

        Ok(PartitionHeader { uuid, payload_size })
    }

    /// Write header at `header_offset`
    fn write_header_at(
        &mut self,
        header_offset: u64,
        uuid: &Uuid,
        payload_size: u64,
    ) -> Result<()> {
        self.file.seek(SeekFrom::Start(header_offset))?;
        self.file.write_all(uuid.as_bytes())?;
        self.file.write_u64::<LittleEndian>(payload_size)?;
        Ok(())
    }

    /// Locate the header of the partition for microdroid manager. A pair of `PartitionHeader` and
    /// the offset of the partition in the disk is returned. If the partition is not found,
    /// `PartitionHeader` is `None` and the offset points to the empty partition that can be used
    /// for the partition.
    fn locate_microdroid_header(&mut self) -> Result<(Option<PartitionHeader>, PartitionOffset)> {
        let microdroid_uuid = Uuid::parse_str(MICRODROID_PARTITION_UUID)?;

        // the first partition header is located just after the disk header
        let mut header_offset = DISK_HEADER_SIZE;
        loop {
            let header = self.read_header_at(header_offset)?;
            if header.uuid == microdroid_uuid {
                // found a matching header
                return Ok((Some(header), header_offset));
            } else if header.uuid == Uuid::nil() {
                // found an empty space
                return Ok((None, header_offset));
            }
            // Move to the next partition. Be careful about overflow.
            let payload_size = round_to_multiple(header.payload_size, PARTITION_HEADER_SIZE)?;
            let part_size = payload_size
                .checked_add(PARTITION_HEADER_SIZE)
                .ok_or_else(|| anyhow!("partition too large"))?;
            header_offset = header_offset
                .checked_add(part_size)
                .ok_or_else(|| anyhow!("next partition at invalid offset"))?;
        }
    }
}

/// Round `n` up to the nearest multiple of `unit`
fn round_to_multiple(n: u64, unit: u64) -> Result<u64> {
    assert!((unit & (unit - 1)) == 0, "{} is not power of two", unit);
    let ret = (n + unit - 1) & !(unit - 1);
    if ret < n {
        bail!("overflow")
    }
    Ok(ret)
}

struct ZeroOnDropKey(LessSafeKey);

impl Drop for ZeroOnDropKey {
    fn drop(&mut self) {
        // Zeroize the key by overwriting it with a key constructed from zeros of same length
        // This works because the raw key bytes are allocated inside the struct, not on the heap
        let zero = [0; 32];
        let zero_key = LessSafeKey::new(UnboundKey::new(ENCRYPT_ALG, &zero).unwrap());
        unsafe {
            ::std::ptr::write_volatile::<LessSafeKey>(&mut self.0, zero_key);
        }
    }
}

impl std::ops::Deref for ZeroOnDropKey {
    type Target = LessSafeKey;
    fn deref(&self) -> &LessSafeKey {
        &self.0
    }
}

/// Returns the key that is used to encrypt the microdroid manager partition. It is derived from
/// the sealing CDI of the previous stage, which is Android Boot Loader (ABL).
fn get_key() -> Result<ZeroOnDropKey> {
    // Sealing CDI from the previous stage.
    let diced = wait_for_interface::<dyn IDiceNode>("android.security.dice.IDiceNode")
        .context("IDiceNode service not found")?;
    let bcc_handover = diced.derive(&[]).context("Failed to get BccHandover")?;

    // Derive a key from the Sealing CDI
    // Step 1 is extraction: https://datatracker.ietf.org/doc/html/rfc5869#section-2.2 where a
    // pseduo random key (PRK) is extracted from (Input Keying Material - IKM, which is secret) and
    // optional salt.
    let salt = Salt::new(HKDF_SHA256, &[]); // use 0 as salt
    let prk = salt.extract(&bcc_handover.cdiSeal); // Sealing CDI as IKM

    // Step 2 is expansion: https://datatracker.ietf.org/doc/html/rfc5869#section-2.3 where the PRK
    // (optionally with the `info` which gives contextual information) is expanded into the output
    // keying material (OKM). Note that the process fails only when the size of OKM is longer than
    // 255 * SHA256_HASH_SIZE (32), which isn't the case here.
    let info = [b"microdroid_manager_key".as_ref()];
    let okm = prk.expand(&info, HKDF_SHA256).unwrap(); // doesn't fail as explained above
    let mut key = [0; 32];
    okm.fill(&mut key).unwrap(); // doesn't fail as explained above

    // The term LessSafe might be misleading here. LessSafe here just means that the API can
    // possibly accept same nonces for different messages. However, since we encrypt/decrypt only a
    // single message (the microdroid_manager partition payload) with a randomly generated nonce,
    // this is safe enough.
    let ret = ZeroOnDropKey(LessSafeKey::new(UnboundKey::new(ENCRYPT_ALG, &key).unwrap()));

    // Don't forget to zeroize the raw key array as well
    unsafe {
        ::std::ptr::write_volatile::<[u8; 32]>(&mut key, [0; 32]);
    }

    Ok(ret)
}

#[derive(Debug, Serialize, Deserialize, PartialEq)]
pub struct MicrodroidData {
    pub salt: Vec<u8>, // Should be [u8; 64] but that isn't serializable.
    pub apk_data: ApkData,
    pub extra_apks_data: Vec<ApkData>,
    pub apex_data: Vec<ApexData>,
}

#[derive(Debug, Serialize, Deserialize, PartialEq)]
pub struct ApkData {
    pub root_hash: Box<RootHash>,
    pub pubkey: Box<[u8]>,
}

pub type RootHash = [u8];

#[derive(Debug, Serialize, Deserialize, PartialEq)]
pub struct ApexData {
    pub name: String,
    pub public_key: Vec<u8>,
    pub root_digest: Vec<u8>,
    pub last_update_seconds: u64,
    pub is_factory: bool,
}
