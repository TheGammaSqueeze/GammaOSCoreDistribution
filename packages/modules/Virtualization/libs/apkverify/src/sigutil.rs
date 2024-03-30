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

//! Utilities for Signature Verification

use anyhow::{anyhow, bail, Result};
use byteorder::{LittleEndian, ReadBytesExt};
use bytes::{Buf, BufMut, Bytes, BytesMut};
use ring::digest;
use std::cmp::min;
use std::io::{Cursor, Read, Seek, SeekFrom, Take};

use crate::ziputil::{set_central_directory_offset, zip_sections};

const APK_SIG_BLOCK_MIN_SIZE: u32 = 32;
const APK_SIG_BLOCK_MAGIC: u128 = 0x3234206b636f6c4220676953204b5041;

// TODO(jooyung): introduce type
pub const SIGNATURE_RSA_PSS_WITH_SHA256: u32 = 0x0101;
pub const SIGNATURE_RSA_PSS_WITH_SHA512: u32 = 0x0102;
pub const SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA256: u32 = 0x0103;
pub const SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA512: u32 = 0x0104;
pub const SIGNATURE_ECDSA_WITH_SHA256: u32 = 0x0201;
pub const SIGNATURE_ECDSA_WITH_SHA512: u32 = 0x0202;
pub const SIGNATURE_DSA_WITH_SHA256: u32 = 0x0301;
pub const SIGNATURE_VERITY_RSA_PKCS1_V1_5_WITH_SHA256: u32 = 0x0421;
pub const SIGNATURE_VERITY_ECDSA_WITH_SHA256: u32 = 0x0423;
pub const SIGNATURE_VERITY_DSA_WITH_SHA256: u32 = 0x0425;

// TODO(jooyung): introduce type
const CONTENT_DIGEST_CHUNKED_SHA256: u32 = 1;
const CONTENT_DIGEST_CHUNKED_SHA512: u32 = 2;
const CONTENT_DIGEST_VERITY_CHUNKED_SHA256: u32 = 3;
#[allow(unused)]
const CONTENT_DIGEST_SHA256: u32 = 4;

const CHUNK_SIZE_BYTES: u64 = 1024 * 1024;

pub struct ApkSections<R> {
    inner: R,
    signing_block_offset: u32,
    signing_block_size: u32,
    central_directory_offset: u32,
    central_directory_size: u32,
    eocd_offset: u32,
    eocd_size: u32,
}

impl<R: Read + Seek> ApkSections<R> {
    pub fn new(reader: R) -> Result<ApkSections<R>> {
        let (mut reader, zip_sections) = zip_sections(reader)?;
        let (signing_block_offset, signing_block_size) =
            find_signing_block(&mut reader, zip_sections.central_directory_offset)?;
        Ok(ApkSections {
            inner: reader,
            signing_block_offset,
            signing_block_size,
            central_directory_offset: zip_sections.central_directory_offset,
            central_directory_size: zip_sections.central_directory_size,
            eocd_offset: zip_sections.eocd_offset,
            eocd_size: zip_sections.eocd_size,
        })
    }

    /// Returns the APK Signature Scheme block contained in the provided file for the given ID
    /// and the additional information relevant for verifying the block against the file.
    pub fn find_signature(&mut self, block_id: u32) -> Result<Bytes> {
        let signing_block = self.bytes(self.signing_block_offset, self.signing_block_size)?;
        // TODO(jooyung): propagate NotFound error so that verification can fallback to V2
        find_signature_scheme_block(Bytes::from(signing_block), block_id)
    }

    /// Computes digest with "signature algorithm" over APK contents, central directory, and EOCD.
    /// 1. The digest of each chunk is computed over the concatenation of byte 0xa5, the chunk’s
    ///    length in bytes (little-endian uint32), and the chunk’s contents.
    /// 2. The top-level digest is computed over the concatenation of byte 0x5a, the number of
    ///    chunks (little-endian uint32), and the concatenation of digests of the chunks in the
    ///    order the chunks appear in the APK.
    /// (see https://source.android.com/security/apksigning/v2#integrity-protected-contents)
    pub fn compute_digest(&mut self, signature_algorithm_id: u32) -> Result<Vec<u8>> {
        let digester = Digester::new(signature_algorithm_id)?;

        let mut digests_of_chunks = BytesMut::new();
        let mut chunk_count = 0u32;
        let mut chunk = vec![0u8; CHUNK_SIZE_BYTES as usize];
        for data in &[
            ApkSections::zip_entries,
            ApkSections::central_directory,
            ApkSections::eocd_for_verification,
        ] {
            let mut data = data(self)?;
            while data.limit() > 0 {
                let chunk_size = min(CHUNK_SIZE_BYTES, data.limit());
                let slice = &mut chunk[..(chunk_size as usize)];
                data.read_exact(slice)?;
                digests_of_chunks.put_slice(
                    digester.digest(slice, CHUNK_HEADER_MID, chunk_size as u32).as_ref(),
                );
                chunk_count += 1;
            }
        }
        Ok(digester.digest(&digests_of_chunks, CHUNK_HEADER_TOP, chunk_count).as_ref().into())
    }

    fn zip_entries(&mut self) -> Result<Take<Box<dyn Read + '_>>> {
        scoped_read(&mut self.inner, 0, self.signing_block_offset as u64)
    }

    fn central_directory(&mut self) -> Result<Take<Box<dyn Read + '_>>> {
        scoped_read(
            &mut self.inner,
            self.central_directory_offset as u64,
            self.central_directory_size as u64,
        )
    }

    fn eocd_for_verification(&mut self) -> Result<Take<Box<dyn Read + '_>>> {
        let mut eocd = self.bytes(self.eocd_offset, self.eocd_size)?;
        // Protection of section 4 (ZIP End of Central Directory) is complicated by the section
        // containing the offset of ZIP Central Directory. The offset changes when the size of the
        // APK Signing Block changes, for instance, when a new signature is added. Thus, when
        // computing digest over the ZIP End of Central Directory, the field containing the offset
        // of ZIP Central Directory must be treated as containing the offset of the APK Signing
        // Block.
        set_central_directory_offset(&mut eocd, self.signing_block_offset)?;
        Ok(Read::take(Box::new(Cursor::new(eocd)), self.eocd_size as u64))
    }

    fn bytes(&mut self, offset: u32, size: u32) -> Result<Vec<u8>> {
        self.inner.seek(SeekFrom::Start(offset as u64))?;
        let mut buf = vec![0u8; size as usize];
        self.inner.read_exact(&mut buf)?;
        Ok(buf)
    }
}

fn scoped_read<'a, R: Read + Seek>(
    src: &'a mut R,
    offset: u64,
    size: u64,
) -> Result<Take<Box<dyn Read + 'a>>> {
    src.seek(SeekFrom::Start(offset))?;
    Ok(Read::take(Box::new(src), size))
}

struct Digester {
    algorithm: &'static digest::Algorithm,
}

const CHUNK_HEADER_TOP: &[u8] = &[0x5a];
const CHUNK_HEADER_MID: &[u8] = &[0xa5];

impl Digester {
    fn new(signature_algorithm_id: u32) -> Result<Digester> {
        let digest_algorithm_id = to_content_digest_algorithm(signature_algorithm_id)?;
        let algorithm = match digest_algorithm_id {
            CONTENT_DIGEST_CHUNKED_SHA256 => &digest::SHA256,
            CONTENT_DIGEST_CHUNKED_SHA512 => &digest::SHA512,
            // TODO(jooyung): implement
            CONTENT_DIGEST_VERITY_CHUNKED_SHA256 => {
                bail!("TODO(b/190343842): CONTENT_DIGEST_VERITY_CHUNKED_SHA256: not implemented")
            }
            _ => bail!("Unknown digest algorithm: {}", digest_algorithm_id),
        };
        Ok(Digester { algorithm })
    }

    // v2/v3 digests are computed after prepending "header" byte and "size" info.
    fn digest(&self, data: &[u8], header: &[u8], size: u32) -> digest::Digest {
        let mut ctx = digest::Context::new(self.algorithm);
        ctx.update(header);
        ctx.update(&size.to_le_bytes());
        ctx.update(data);
        ctx.finish()
    }
}

fn find_signing_block<T: Read + Seek>(
    reader: &mut T,
    central_directory_offset: u32,
) -> Result<(u32, u32)> {
    // FORMAT:
    // OFFSET       DATA TYPE  DESCRIPTION
    // * @+0  bytes uint64:    size in bytes (excluding this field)
    // * @+8  bytes payload
    // * @-24 bytes uint64:    size in bytes (same as the one above)
    // * @-16 bytes uint128:   magic
    if central_directory_offset < APK_SIG_BLOCK_MIN_SIZE {
        bail!(
            "APK too small for APK Signing Block. ZIP Central Directory offset: {}",
            central_directory_offset
        );
    }
    reader.seek(SeekFrom::Start((central_directory_offset - 24) as u64))?;
    let size_in_footer = reader.read_u64::<LittleEndian>()? as u32;
    if reader.read_u128::<LittleEndian>()? != APK_SIG_BLOCK_MAGIC {
        bail!("No APK Signing Block before ZIP Central Directory")
    }
    let total_size = size_in_footer + 8;
    let signing_block_offset = central_directory_offset
        .checked_sub(total_size)
        .ok_or_else(|| anyhow!("APK Signing Block size out of range: {}", size_in_footer))?;
    reader.seek(SeekFrom::Start(signing_block_offset as u64))?;
    let size_in_header = reader.read_u64::<LittleEndian>()? as u32;
    if size_in_header != size_in_footer {
        bail!(
            "APK Signing Block sizes in header and footer do not match: {} vs {}",
            size_in_header,
            size_in_footer
        );
    }
    Ok((signing_block_offset, total_size))
}

fn find_signature_scheme_block(buf: Bytes, block_id: u32) -> Result<Bytes> {
    // FORMAT:
    // OFFSET       DATA TYPE  DESCRIPTION
    // * @+0  bytes uint64:    size in bytes (excluding this field)
    // * @+8  bytes pairs
    // * @-24 bytes uint64:    size in bytes (same as the one above)
    // * @-16 bytes uint128:   magic
    let mut pairs = buf.slice(8..(buf.len() - 24));
    let mut entry_count = 0;
    while pairs.has_remaining() {
        entry_count += 1;
        if pairs.remaining() < 8 {
            bail!("Insufficient data to read size of APK Signing Block entry #{}", entry_count);
        }
        let length = pairs.get_u64_le();
        let mut pair = pairs.split_to(length as usize);
        let id = pair.get_u32_le();
        if id == block_id {
            return Ok(pair);
        }
    }
    // TODO(jooyung): return NotFound error
    bail!("No APK Signature Scheme block in APK Signing Block with ID: {}", block_id)
}

pub fn is_supported_signature_algorithm(algorithm_id: u32) -> bool {
    matches!(
        algorithm_id,
        SIGNATURE_RSA_PSS_WITH_SHA256
            | SIGNATURE_RSA_PSS_WITH_SHA512
            | SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA256
            | SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA512
            | SIGNATURE_ECDSA_WITH_SHA256
            | SIGNATURE_ECDSA_WITH_SHA512
            | SIGNATURE_DSA_WITH_SHA256
            | SIGNATURE_VERITY_RSA_PKCS1_V1_5_WITH_SHA256
            | SIGNATURE_VERITY_ECDSA_WITH_SHA256
            | SIGNATURE_VERITY_DSA_WITH_SHA256
    )
}

fn to_content_digest_algorithm(algorithm_id: u32) -> Result<u32> {
    match algorithm_id {
        SIGNATURE_RSA_PSS_WITH_SHA256
        | SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA256
        | SIGNATURE_ECDSA_WITH_SHA256
        | SIGNATURE_DSA_WITH_SHA256 => Ok(CONTENT_DIGEST_CHUNKED_SHA256),
        SIGNATURE_RSA_PSS_WITH_SHA512
        | SIGNATURE_RSA_PKCS1_V1_5_WITH_SHA512
        | SIGNATURE_ECDSA_WITH_SHA512 => Ok(CONTENT_DIGEST_CHUNKED_SHA512),
        SIGNATURE_VERITY_RSA_PKCS1_V1_5_WITH_SHA256
        | SIGNATURE_VERITY_ECDSA_WITH_SHA256
        | SIGNATURE_VERITY_DSA_WITH_SHA256 => Ok(CONTENT_DIGEST_VERITY_CHUNKED_SHA256),
        _ => bail!("Unknown signature algorithm: {}", algorithm_id),
    }
}

pub fn rank_signature_algorithm(algo: u32) -> Result<u32> {
    rank_content_digest_algorithm(to_content_digest_algorithm(algo)?)
}

fn rank_content_digest_algorithm(id: u32) -> Result<u32> {
    match id {
        CONTENT_DIGEST_CHUNKED_SHA256 => Ok(0),
        CONTENT_DIGEST_VERITY_CHUNKED_SHA256 => Ok(1),
        CONTENT_DIGEST_CHUNKED_SHA512 => Ok(2),
        _ => bail!("Unknown digest algorithm: {}", id),
    }
}
