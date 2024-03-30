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

use anyhow::{anyhow, bail, Context, Result};
use byteorder::{LittleEndian, ReadBytesExt, WriteBytesExt};
use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::{FromPrimitive, ToPrimitive};
use std::io::{copy, Cursor, Read, Seek, SeekFrom, Write};

use crate::hashtree::*;

// `apksigv4` module provides routines to decode and encode the idsig file as defined in [APK
// signature scheme v4] (https://source.android.com/security/apksigning/v4).

/// `V4Signature` provides access to the various fields in an idsig file.
#[derive(Default)]
pub struct V4Signature<R: Read + Seek> {
    /// Version of the header. Should be 2.
    pub version: Version,
    /// Provides access to the information about how the APK is hashed.
    pub hashing_info: HashingInfo,
    /// Provides access to the information that can be used to verify this file
    pub signing_info: SigningInfo,
    /// Total size of the merkle tree
    pub merkle_tree_size: u32,
    /// Offset of the merkle tree in the idsig file
    pub merkle_tree_offset: u64,

    // Provides access to the underlying data
    data: R,
}

/// `HashingInfo` provides information about how the APK is hashed.
#[derive(Default)]
pub struct HashingInfo {
    /// Hash algorithm used when creating the merkle tree for the APK.
    pub hash_algorithm: HashAlgorithm,
    /// The log size of a block used when creating the merkle tree. 12 if 4k block was used.
    pub log2_blocksize: u8,
    /// The salt used when creating the merkle tree. 32 bytes max.
    pub salt: Box<[u8]>,
    /// The root hash of the merkle tree created.
    pub raw_root_hash: Box<[u8]>,
}

/// `SigningInfo` provides information that can be used to verify the idsig file.
#[derive(Default)]
pub struct SigningInfo {
    /// Digest of the APK that this idsig file is for.
    pub apk_digest: Box<[u8]>,
    /// Certificate of the signer that signed this idsig file. ASN.1 DER form.
    pub x509_certificate: Box<[u8]>,
    /// A free-form binary data
    pub additional_data: Box<[u8]>,
    /// Public key of the signer in ASN.1 DER form. This must match the `x509_certificate` field.
    pub public_key: Box<[u8]>,
    /// Signature algorithm used to sign this file.
    pub signature_algorithm_id: SignatureAlgorithmId,
    /// The signature of this file.
    pub signature: Box<[u8]>,
}

/// Version of the idsig file format
#[derive(Debug, PartialEq, FromPrimitive, ToPrimitive)]
#[repr(u32)]
pub enum Version {
    /// Version 2, the only supported version.
    V2 = 2,
}

impl Version {
    fn from(val: u32) -> Result<Version> {
        Self::from_u32(val).ok_or_else(|| anyhow!("{} is an unsupported version", val))
    }
}

impl Default for Version {
    fn default() -> Self {
        Version::V2
    }
}

/// Hash algorithm that can be used for idsig file.
#[derive(Debug, PartialEq, FromPrimitive, ToPrimitive)]
#[repr(u32)]
pub enum HashAlgorithm {
    /// SHA2-256
    SHA256 = 1,
}

impl HashAlgorithm {
    fn from(val: u32) -> Result<HashAlgorithm> {
        Self::from_u32(val).ok_or_else(|| anyhow!("{} is an unsupported hash algorithm", val))
    }
}

impl Default for HashAlgorithm {
    fn default() -> Self {
        HashAlgorithm::SHA256
    }
}

/// Signature algorithm that can be used for idsig file
#[derive(Debug, PartialEq, FromPrimitive, ToPrimitive)]
#[allow(non_camel_case_types)]
#[repr(u32)]
pub enum SignatureAlgorithmId {
    /// RSASSA-PSS with SHA2-256 digest, SHA2-256 MGF1, 32 bytes of salt, trailer: 0xbc
    RSASSA_PSS_SHA2_256 = 0x0101,
    /// RSASSA-PSS with SHA2-512 digest, SHA2-512 MGF1, 64 bytes of salt, trailer: 0xbc
    RSASSA_PSS_SHA2_512 = 0x0102,
    /// RSASSA-PKCS1-v1_5 with SHA2-256 digest.
    RSASSA_PKCS1_SHA2_256 = 0x0103,
    /// RSASSA-PKCS1-v1_5 with SHA2-512 digest.
    RSASSA_PKCS1_SHA2_512 = 0x0104,
    /// ECDSA with SHA2-256 digest.
    ECDSA_SHA2_256 = 0x0201,
    /// ECDSA with SHA2-512 digest.
    ECDSA_SHA2_512 = 0x0202,
    /// DSA with SHA2-256 digest
    DSA_SHA2_256 = 0x0301,
}

impl SignatureAlgorithmId {
    fn from(val: u32) -> Result<SignatureAlgorithmId> {
        Self::from_u32(val)
            .with_context(|| format!("{:#06x} is an unsupported signature algorithm", val))
    }
}

impl Default for SignatureAlgorithmId {
    fn default() -> Self {
        SignatureAlgorithmId::DSA_SHA2_256
    }
}

impl<R: Read + Seek> V4Signature<R> {
    /// Consumes a stream for an idsig file into a `V4Signature` struct.
    pub fn from(mut r: R) -> Result<V4Signature<R>> {
        Ok(V4Signature {
            version: Version::from(r.read_u32::<LittleEndian>()?)?,
            hashing_info: HashingInfo::from(&mut r)?,
            signing_info: SigningInfo::from(&mut r)?,
            merkle_tree_size: r.read_u32::<LittleEndian>()?,
            merkle_tree_offset: r.stream_position()?,
            data: r,
        })
    }

    /// Read a stream for an APK file and creates a corresponding `V4Signature` struct that digests
    /// the APK file. Note that the signing is not done.
    pub fn create(
        mut apk: &mut R,
        block_size: usize,
        salt: &[u8],
        algorithm: HashAlgorithm,
    ) -> Result<V4Signature<Cursor<Vec<u8>>>> {
        // Determine the size of the apk
        let start = apk.stream_position()?;
        let size = apk.seek(SeekFrom::End(0))? as usize;
        apk.seek(SeekFrom::Start(start))?;

        // Create hash tree (and root hash)
        let algorithm = match algorithm {
            HashAlgorithm::SHA256 => &ring::digest::SHA256,
        };
        let hash_tree = HashTree::from(&mut apk, size, salt, block_size, algorithm)?;

        let mut ret = V4Signature {
            version: Version::default(),
            hashing_info: HashingInfo::default(),
            signing_info: SigningInfo::default(),
            merkle_tree_size: hash_tree.tree.len() as u32,
            merkle_tree_offset: 0, // merkle tree starts from the beginning of `data`
            data: Cursor::new(hash_tree.tree),
        };
        ret.hashing_info.raw_root_hash = hash_tree.root_hash.into_boxed_slice();
        ret.hashing_info.log2_blocksize = log2(block_size);

        // TODO(jiyong): fill the signing_info struct by reading the APK file. The information,
        // especially `apk_digest` is needed to check if `V4Signature` is outdated, in which case
        // it needs to be created from the updated APK.

        Ok(ret)
    }

    /// Writes the data into a writer
    pub fn write_into<W: Write + Seek>(&mut self, mut w: &mut W) -> Result<()> {
        // Writes the header part
        w.write_u32::<LittleEndian>(self.version.to_u32().unwrap())?;
        self.hashing_info.write_into(&mut w)?;
        self.signing_info.write_into(&mut w)?;
        w.write_u32::<LittleEndian>(self.merkle_tree_size)?;

        // Writes the merkle tree
        self.data.seek(SeekFrom::Start(self.merkle_tree_offset))?;
        let copied_size = copy(&mut self.data, &mut w)?;
        if copied_size != self.merkle_tree_size as u64 {
            bail!(
                "merkle tree is {} bytes, but only {} bytes are written.",
                self.merkle_tree_size,
                copied_size
            );
        }
        Ok(())
    }

    /// Returns the bytes that represents the merkle tree
    pub fn merkle_tree(&mut self) -> Result<Vec<u8>> {
        self.data.seek(SeekFrom::Start(self.merkle_tree_offset))?;
        let mut out = Vec::new();
        self.data.read_to_end(&mut out)?;
        Ok(out)
    }
}

impl HashingInfo {
    fn from(mut r: &mut dyn Read) -> Result<HashingInfo> {
        // Size of the entire hashing_info struct. We don't need this because each variable-sized
        // fields in the struct are also length encoded.
        r.read_u32::<LittleEndian>()?;
        Ok(HashingInfo {
            hash_algorithm: HashAlgorithm::from(r.read_u32::<LittleEndian>()?)?,
            log2_blocksize: r.read_u8()?,
            salt: read_sized_array(&mut r)?,
            raw_root_hash: read_sized_array(&mut r)?,
        })
    }

    fn write_into<W: Write + Seek>(&self, mut w: &mut W) -> Result<()> {
        let start = w.stream_position()?;
        // Size of the entire hashing_info struct. Since we don't know the size yet, fill the place
        // with 0. The exact size will then be written below.
        w.write_u32::<LittleEndian>(0)?;

        w.write_u32::<LittleEndian>(self.hash_algorithm.to_u32().unwrap())?;
        w.write_u8(self.log2_blocksize)?;
        write_sized_array(&mut w, &self.salt)?;
        write_sized_array(&mut w, &self.raw_root_hash)?;

        // Determine the size of hashing_info, and write it in front of the struct where the value
        // was initialized to zero.
        let end = w.stream_position()?;
        let size = end - start - std::mem::size_of::<u32>() as u64;
        w.seek(SeekFrom::Start(start))?;
        w.write_u32::<LittleEndian>(size as u32)?;
        w.seek(SeekFrom::Start(end))?;
        Ok(())
    }
}

impl SigningInfo {
    fn from(mut r: &mut dyn Read) -> Result<SigningInfo> {
        // Size of the entire signing_info struct. We don't need this because each variable-sized
        // fields in the struct are also length encoded.
        r.read_u32::<LittleEndian>()?;
        Ok(SigningInfo {
            apk_digest: read_sized_array(&mut r)?,
            x509_certificate: read_sized_array(&mut r)?,
            additional_data: read_sized_array(&mut r)?,
            public_key: read_sized_array(&mut r)?,
            signature_algorithm_id: SignatureAlgorithmId::from(r.read_u32::<LittleEndian>()?)?,
            signature: read_sized_array(&mut r)?,
        })
    }

    fn write_into<W: Write + Seek>(&self, mut w: &mut W) -> Result<()> {
        let start = w.stream_position()?;
        // Size of the entire signing_info struct. Since we don't know the size yet, fill the place
        // with 0. The exact size will then be written below.
        w.write_u32::<LittleEndian>(0)?;

        write_sized_array(&mut w, &self.apk_digest)?;
        write_sized_array(&mut w, &self.x509_certificate)?;
        write_sized_array(&mut w, &self.additional_data)?;
        write_sized_array(&mut w, &self.public_key)?;
        w.write_u32::<LittleEndian>(self.signature_algorithm_id.to_u32().unwrap())?;
        write_sized_array(&mut w, &self.signature)?;

        // Determine the size of signing_info, and write it in front of the struct where the value
        // was initialized to zero.
        let end = w.stream_position()?;
        let size = end - start - std::mem::size_of::<u32>() as u64;
        w.seek(SeekFrom::Start(start))?;
        w.write_u32::<LittleEndian>(size as u32)?;
        w.seek(SeekFrom::Start(end))?;
        Ok(())
    }
}

fn read_sized_array(r: &mut dyn Read) -> Result<Box<[u8]>> {
    let size = r.read_u32::<LittleEndian>()?;
    let mut data = vec![0; size as usize];
    r.read_exact(&mut data)?;
    Ok(data.into_boxed_slice())
}

fn write_sized_array(w: &mut dyn Write, data: &[u8]) -> Result<()> {
    w.write_u32::<LittleEndian>(data.len() as u32)?;
    Ok(w.write_all(data)?)
}

fn log2(n: usize) -> u8 {
    let num_bits = std::mem::size_of::<usize>() * 8;
    (num_bits as u32 - n.leading_zeros() - 1) as u8
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    fn hexstring_from(s: &[u8]) -> String {
        s.iter().map(|byte| format!("{:02x}", byte)).reduce(|i, j| i + &j).unwrap_or_default()
    }

    #[test]
    fn parse_idsig_file() {
        let idsig = Cursor::new(include_bytes!("../testdata/test.apk.idsig"));
        let parsed = V4Signature::from(idsig).unwrap();

        assert_eq!(Version::V2, parsed.version);

        let hi = parsed.hashing_info;
        assert_eq!(HashAlgorithm::SHA256, hi.hash_algorithm);
        assert_eq!(12, hi.log2_blocksize);
        assert_eq!("", hexstring_from(hi.salt.as_ref()));
        assert_eq!(
            "ce1194fdb3cb2537daf0ac8cdf4926754adcbce5abeece7945fe25d204a0df6a",
            hexstring_from(hi.raw_root_hash.as_ref())
        );

        let si = parsed.signing_info;
        assert_eq!(
            "b5225523a813fb84ed599dd649698c080bcfed4fb19ddb00283a662a2683bc15",
            hexstring_from(si.apk_digest.as_ref())
        );
        assert_eq!("", hexstring_from(si.additional_data.as_ref()));
        assert_eq!(
            "303d021c77304d0f4732a90372bbfce095223e4ba82427ceb381f69bc6762d78021d008b99924\
                   a8585c38d7f654835eb219ae9e176b44e86dcb23153e3d9d6",
            hexstring_from(si.signature.as_ref())
        );
        assert_eq!(SignatureAlgorithmId::DSA_SHA2_256, si.signature_algorithm_id);

        assert_eq!(36864, parsed.merkle_tree_size);
        assert_eq!(2251, parsed.merkle_tree_offset);
    }

    /// Parse an idsig file into V4Signature and write it. The written date must be the same as
    /// the input file.
    #[test]
    fn parse_and_compose() {
        let input = Cursor::new(include_bytes!("../testdata/test.apk.idsig"));
        let mut parsed = V4Signature::from(input.clone()).unwrap();

        let mut output = Cursor::new(Vec::new());
        parsed.write_into(&mut output).unwrap();

        assert_eq!(input.get_ref().as_ref(), output.get_ref().as_slice());
    }

    /// Create V4Signature by hashing an APK. Merkle tree and the root hash should be the same
    /// as those in the idsig file created by the signapk tool.
    #[test]
    fn digest_from_apk() {
        let mut input = Cursor::new(include_bytes!("../testdata/test.apk"));
        let mut created =
            V4Signature::create(&mut input, 4096, &[], HashAlgorithm::SHA256).unwrap();

        let golden = Cursor::new(include_bytes!("../testdata/test.apk.idsig"));
        let mut golden = V4Signature::from(golden).unwrap();

        // Compare the root hash
        assert_eq!(
            created.hashing_info.raw_root_hash.as_ref(),
            golden.hashing_info.raw_root_hash.as_ref()
        );

        // Compare the merkle tree
        assert_eq!(
            created.merkle_tree().unwrap().as_slice(),
            golden.merkle_tree().unwrap().as_slice()
        );
    }
}
