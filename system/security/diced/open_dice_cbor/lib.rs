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

//! Implements safe wrappers around the public API of libopen-dice.
//! ## Example:
//! ```
//! use diced_open_dice_cbor as dice;
//!
//! let context = dice::dice::OpenDiceCborContext::new()
//! let parent_cdi_attest = [1u8, dice::CDI_SIZE];
//! let parent_cdi_seal = [2u8, dice::CDI_SIZE];
//! let input_values = dice::InputValuesOwned {
//!     code_hash: [3u8, dice::HASH_SIZE],
//!     config: dice::ConfigOwned::Descriptor("My descriptor".as_bytes().to_vec()),
//!     authority_hash: [0u8, dice::HASH_SIZE],
//!     mode: dice::Mode::Normal,
//!     hidden: [0u8, dice::HIDDEN_SIZE],
//! };
//! let (cdi_attest, cdi_seal, cert_chain) = context
//!     .main_flow(&parent_cdi_attest, &parent_cdi_seal, &input_values)?;
//! ```

use keystore2_crypto::{zvec, ZVec};
use open_dice_bcc_bindgen::BccMainFlow;
use open_dice_cbor_bindgen::{
    DiceConfigType, DiceDeriveCdiCertificateId, DiceDeriveCdiPrivateKeySeed,
    DiceGenerateCertificate, DiceHash, DiceInputValues, DiceKdf, DiceKeypairFromSeed, DiceMainFlow,
    DiceMode, DiceResult, DiceSign, DiceVerify, DICE_CDI_SIZE, DICE_HASH_SIZE, DICE_HIDDEN_SIZE,
    DICE_ID_SIZE, DICE_INLINE_CONFIG_SIZE, DICE_PRIVATE_KEY_SEED_SIZE, DICE_PRIVATE_KEY_SIZE,
    DICE_PUBLIC_KEY_SIZE, DICE_SIGNATURE_SIZE,
};
use open_dice_cbor_bindgen::{
    DiceConfigType_kDiceConfigTypeDescriptor as DICE_CONFIG_TYPE_DESCRIPTOR,
    DiceConfigType_kDiceConfigTypeInline as DICE_CONFIG_TYPE_INLINE,
    DiceMode_kDiceModeDebug as DICE_MODE_DEBUG,
    DiceMode_kDiceModeMaintenance as DICE_MODE_RECOVERY,
    DiceMode_kDiceModeNormal as DICE_MODE_NORMAL,
    DiceMode_kDiceModeNotInitialized as DICE_MODE_NOT_CONFIGURED,
    DiceResult_kDiceResultBufferTooSmall as DICE_RESULT_BUFFER_TOO_SMALL,
    DiceResult_kDiceResultInvalidInput as DICE_RESULT_INVALID_INPUT,
    DiceResult_kDiceResultOk as DICE_RESULT_OK,
    DiceResult_kDiceResultPlatformError as DICE_RESULT_PLATFORM_ERROR,
};
use std::ffi::{c_void, NulError};

/// The size of a DICE hash.
pub const HASH_SIZE: usize = DICE_HASH_SIZE as usize;
/// The size of the DICE hidden value.
pub const HIDDEN_SIZE: usize = DICE_HIDDEN_SIZE as usize;
/// The size of a DICE inline config.
pub const INLINE_CONFIG_SIZE: usize = DICE_INLINE_CONFIG_SIZE as usize;
/// The size of a private key seed.
pub const PRIVATE_KEY_SEED_SIZE: usize = DICE_PRIVATE_KEY_SEED_SIZE as usize;
/// The size of a CDI.
pub const CDI_SIZE: usize = DICE_CDI_SIZE as usize;
/// The size of an ID.
pub const ID_SIZE: usize = DICE_ID_SIZE as usize;
/// The size of a private key.
pub const PRIVATE_KEY_SIZE: usize = DICE_PRIVATE_KEY_SIZE as usize;
/// The size of a public key.
pub const PUBLIC_KEY_SIZE: usize = DICE_PUBLIC_KEY_SIZE as usize;
/// The size of a signature.
pub const SIGNATURE_SIZE: usize = DICE_SIGNATURE_SIZE as usize;

/// Open dice wrapper error type.
#[derive(Debug, thiserror::Error, PartialEq)]
pub enum Error {
    /// The libopen-dice backend reported InvalidInput.
    #[error("Open dice backend: Invalid input")]
    InvalidInput,
    /// The libopen-dice backend reported BufferTooSmall.
    #[error("Open dice backend: Buffer too small")]
    BufferTooSmall,
    /// The libopen-dice backend reported PlatformError.
    #[error("Open dice backend: Platform error")]
    PlatformError,
    /// The libopen-dice backend reported an error that is outside of the defined range of errors.
    /// The returned error code is embedded in this value.
    #[error("Open dice backend returned an unexpected error code: {0:?}")]
    Unexpected(u32),

    /// The allocation of a ZVec failed. Most likely due to a failure during the call to mlock.
    #[error("ZVec allocation failed")]
    ZVec(#[from] zvec::Error),

    /// Functions that have to convert str to CString may fail if the string has an interior
    /// nul byte.
    #[error("Input string has an interior nul byte.")]
    CStrNulError(#[from] NulError),
}

/// Open dice result type.
pub type Result<T> = std::result::Result<T, Error>;

impl From<DiceResult> for Error {
    fn from(result: DiceResult) -> Self {
        match result {
            DICE_RESULT_INVALID_INPUT => Error::InvalidInput,
            DICE_RESULT_BUFFER_TOO_SMALL => Error::BufferTooSmall,
            DICE_RESULT_PLATFORM_ERROR => Error::PlatformError,
            r => Error::Unexpected(r),
        }
    }
}

fn check_result(result: DiceResult) -> Result<()> {
    if result == DICE_RESULT_OK {
        Ok(())
    } else {
        Err(result.into())
    }
}

/// Configuration descriptor for dice input values.
#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
pub enum Config<'a> {
    /// A reference to an inline descriptor.
    Inline(&'a [u8; INLINE_CONFIG_SIZE]),
    /// A reference to a free form descriptor that will be hashed by the implementation.
    Descriptor(&'a [u8]),
}

enum ConfigOwned {
    Inline([u8; INLINE_CONFIG_SIZE]),
    Descriptor(Vec<u8>),
}

impl Config<'_> {
    fn get_type(&self) -> DiceConfigType {
        match self {
            Self::Inline(_) => DICE_CONFIG_TYPE_INLINE,
            Self::Descriptor(_) => DICE_CONFIG_TYPE_DESCRIPTOR,
        }
    }

    fn get_inline(&self) -> [u8; INLINE_CONFIG_SIZE] {
        match self {
            Self::Inline(inline) => **inline,
            _ => [0u8; INLINE_CONFIG_SIZE],
        }
    }

    fn get_descriptor_as_ptr(&self) -> *const u8 {
        match self {
            Self::Descriptor(descriptor) => descriptor.as_ptr(),
            _ => std::ptr::null(),
        }
    }

    fn get_descriptor_size(&self) -> usize {
        match self {
            Self::Descriptor(descriptor) => descriptor.len(),
            _ => 0,
        }
    }
}

impl From<Config<'_>> for ConfigOwned {
    fn from(config: Config) -> Self {
        match config {
            Config::Inline(inline) => ConfigOwned::Inline(*inline),
            Config::Descriptor(descriptor) => ConfigOwned::Descriptor(descriptor.to_owned()),
        }
    }
}

/// DICE modes as defined here:
/// https://pigweed.googlesource.com/open-dice/+/refs/heads/main/docs/specification.md#mode-value-details
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Mode {
    /// See documentation linked above.
    NotConfigured = 0,
    /// See documentation linked above.
    Normal = 1,
    /// See documentation linked above.
    Debug = 2,
    /// See documentation linked above.
    Recovery = 3,
}

impl Mode {
    fn get_internal(&self) -> DiceMode {
        match self {
            Self::NotConfigured => DICE_MODE_NOT_CONFIGURED,
            Self::Normal => DICE_MODE_NORMAL,
            Self::Debug => DICE_MODE_DEBUG,
            Self::Recovery => DICE_MODE_RECOVERY,
        }
    }
}

/// This trait allows API users to supply DICE input values without copying.
pub trait InputValues {
    /// Returns the code hash.
    fn code_hash(&self) -> &[u8; HASH_SIZE];
    /// Returns the config.
    fn config(&self) -> Config;
    /// Returns the authority hash.
    fn authority_hash(&self) -> &[u8; HASH_SIZE];
    /// Returns the authority descriptor.
    fn authority_descriptor(&self) -> Option<&[u8]>;
    /// Returns the mode.
    fn mode(&self) -> Mode;
    /// Returns the hidden value.
    fn hidden(&self) -> &[u8; HIDDEN_SIZE];
}

/// An owning convenience type implementing `InputValues`.
pub struct InputValuesOwned {
    code_hash: [u8; HASH_SIZE],
    config: ConfigOwned,
    authority_hash: [u8; HASH_SIZE],
    authority_descriptor: Option<Vec<u8>>,
    mode: Mode,
    hidden: [u8; HIDDEN_SIZE],
}

impl InputValuesOwned {
    /// Construct a new instance of InputValuesOwned.
    pub fn new(
        code_hash: [u8; HASH_SIZE],
        config: Config,
        authority_hash: [u8; HASH_SIZE],
        authority_descriptor: Option<Vec<u8>>,
        mode: Mode,
        hidden: [u8; HIDDEN_SIZE],
    ) -> Self {
        Self {
            code_hash,
            config: config.into(),
            authority_hash,
            authority_descriptor,
            mode,
            hidden,
        }
    }
}

impl InputValues for InputValuesOwned {
    fn code_hash(&self) -> &[u8; HASH_SIZE] {
        &self.code_hash
    }
    fn config(&self) -> Config {
        match &self.config {
            ConfigOwned::Inline(inline) => Config::Inline(inline),
            ConfigOwned::Descriptor(descriptor) => Config::Descriptor(descriptor.as_slice()),
        }
    }
    fn authority_hash(&self) -> &[u8; HASH_SIZE] {
        &self.authority_hash
    }
    fn authority_descriptor(&self) -> Option<&[u8]> {
        self.authority_descriptor.as_deref()
    }
    fn mode(&self) -> Mode {
        self.mode
    }
    fn hidden(&self) -> &[u8; HIDDEN_SIZE] {
        &self.hidden
    }
}

fn call_with_input_values<T: InputValues + ?Sized, F, R>(input_values: &T, f: F) -> Result<R>
where
    F: FnOnce(*const DiceInputValues) -> Result<R>,
{
    let input_values = DiceInputValues {
        code_hash: *input_values.code_hash(),
        code_descriptor: std::ptr::null(),
        code_descriptor_size: 0,
        config_type: input_values.config().get_type(),
        config_value: input_values.config().get_inline(),
        config_descriptor: input_values.config().get_descriptor_as_ptr(),
        config_descriptor_size: input_values.config().get_descriptor_size(),
        authority_hash: *input_values.authority_hash(),
        authority_descriptor: input_values
            .authority_descriptor()
            .map_or_else(std::ptr::null, <[u8]>::as_ptr),
        authority_descriptor_size: input_values.authority_descriptor().map_or(0, <[u8]>::len),
        mode: input_values.mode().get_internal(),
        hidden: *input_values.hidden(),
    };

    f(&input_values as *const DiceInputValues)
}

/// Multiple of the open dice function required preallocated output buffer
/// which may be too small, this function implements the retry logic to handle
/// too small buffer allocations.
/// The callback `F` must expect a mutable reference to a buffer and a size hint
/// field. The callback is called repeatedly as long as it returns
/// `Err(Error::BufferTooSmall)`. If the size hint remains 0, the buffer size is
/// doubled with each iteration. If the size hint is set by the callback, the buffer
/// will be set to accommodate at least this many bytes.
/// If the callback returns `Ok(())`, the buffer is truncated to the size hint
/// exactly.
/// The function panics if the callback returns `Ok(())` and the size hint is
/// larger than the buffer size.
fn retry_while_adjusting_output_buffer<F>(mut f: F) -> Result<Vec<u8>>
where
    F: FnMut(&mut Vec<u8>, &mut usize) -> Result<()>,
{
    let mut buffer = vec![0; INITIAL_OUT_BUFFER_SIZE];
    let mut actual_size: usize = 0;
    loop {
        match f(&mut buffer, &mut actual_size) {
            // If Error::BufferTooSmall was returned, the allocated certificate
            // buffer was to small for the output. So the buffer is resized to the actual
            // size, and a second attempt is made with the new buffer.
            Err(Error::BufferTooSmall) => {
                let new_size = if actual_size == 0 {
                    // Due to an off spec implementation of open dice cbor, actual size
                    // does not return the required size if the buffer was too small. So
                    // we have to try and approach it gradually.
                    buffer.len() * 2
                } else {
                    actual_size
                };
                buffer.resize(new_size, 0);
                continue;
            }
            Err(e) => return Err(e),
            Ok(()) => {
                if actual_size > buffer.len() {
                    panic!(
                        "actual_size larger than buffer size: open-dice function
                         may have written past the end of the buffer."
                    );
                }
                // Truncate the certificate buffer to the actual size because it may be
                // smaller than the original allocation.
                buffer.truncate(actual_size);
                return Ok(buffer);
            }
        }
    }
}

/// Some libopen-dice variants use a context. Developers that want to customize these
/// bindings may want to implement their own Context factory that creates a context
/// useable by their preferred backend.
pub trait Context {
    /// # Safety
    /// The return value of get_context is passed to any open dice function.
    /// Implementations must explain why the context pointer returned is safe
    /// to be used by the open dice library.
    unsafe fn get_context(&mut self) -> *mut c_void;
}

impl<T: Context + Send> ContextImpl for T {}

/// This represents a context for the open dice library. The wrapped open dice instance, which
/// is based on boringssl and cbor, does not use a context, so that this type is empty.
#[derive(Default)]
pub struct OpenDiceCborContext();

impl OpenDiceCborContext {
    /// Construct a new instance of OpenDiceCborContext.
    pub fn new() -> Self {
        Default::default()
    }
}

impl Context for OpenDiceCborContext {
    unsafe fn get_context(&mut self) -> *mut c_void {
        // # Safety
        // The open dice cbor implementation does not use a context. It is safe
        // to return NULL.
        std::ptr::null_mut()
    }
}

/// Type alias for ZVec indicating that it holds a CDI_ATTEST secret.
pub type CdiAttest = ZVec;

/// Type alias for ZVec indicating that it holds a CDI_SEAL secret.
pub type CdiSeal = ZVec;

/// Type alias for Vec<u8> indicating that it hold a DICE certificate.
pub type Cert = Vec<u8>;

/// Type alias for Vec<u8> indicating that it holds a BCC certificate chain.
pub type Bcc = Vec<u8>;

const INITIAL_OUT_BUFFER_SIZE: usize = 1024;

/// ContextImpl is a mixin trait that implements the safe wrappers around the open dice
/// library calls. Implementations must implement Context::get_context(). As of
/// this writing, the only implementation is OpenDiceCborContext, which returns NULL.
pub trait ContextImpl: Context + Send {
    /// Safe wrapper around open-dice DiceDeriveCdiPrivateKeySeed, see open dice
    /// documentation for details.
    fn derive_cdi_private_key_seed(&mut self, cdi_attest: &[u8; CDI_SIZE]) -> Result<ZVec> {
        let mut seed = ZVec::new(PRIVATE_KEY_SEED_SIZE)?;
        // SAFETY:
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument is expected to be a const array of size CDI_SIZE.
        // * The third argument is expected to be a non const array of size
        //   PRIVATE_KEY_SEED_SIZE which is fulfilled if the call to ZVec::new above
        //   succeeds.
        // * No pointers are expected to be valid beyond the scope of the function
        //   call.
        check_result(unsafe {
            DiceDeriveCdiPrivateKeySeed(self.get_context(), cdi_attest.as_ptr(), seed.as_mut_ptr())
        })?;
        Ok(seed)
    }

    /// Safe wrapper around open-dice DiceDeriveCdiCertificateId, see open dice
    /// documentation for details.
    fn derive_cdi_certificate_id(&mut self, cdi_public_key: &[u8]) -> Result<ZVec> {
        let mut id = ZVec::new(ID_SIZE)?;
        // SAFETY:
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument is expected to be a const array with a size given by the
        //   third argument.
        // * The fourth argument is expected to be a non const array of size
        //   ID_SIZE which is fulfilled if the call to ZVec::new above succeeds.
        // * No pointers are expected to be valid beyond the scope of the function
        //   call.
        check_result(unsafe {
            DiceDeriveCdiCertificateId(
                self.get_context(),
                cdi_public_key.as_ptr(),
                cdi_public_key.len(),
                id.as_mut_ptr(),
            )
        })?;
        Ok(id)
    }

    /// Safe wrapper around open-dice DiceMainFlow, see open dice
    /// documentation for details.
    /// Returns a tuple of:
    ///  * The next attestation CDI,
    ///  * the next seal CDI, and
    ///  * the next attestation certificate.
    /// `(next_attest_cdi, next_seal_cdi, next_attestation_cert)`
    fn main_flow<T: InputValues + ?Sized>(
        &mut self,
        current_cdi_attest: &[u8; CDI_SIZE],
        current_cdi_seal: &[u8; CDI_SIZE],
        input_values: &T,
    ) -> Result<(CdiAttest, CdiSeal, Cert)> {
        let mut next_attest = CdiAttest::new(CDI_SIZE)?;
        let mut next_seal = CdiSeal::new(CDI_SIZE)?;

        // SAFETY (DiceMainFlow):
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument and the third argument are const arrays of size CDI_SIZE.
        //   This is fulfilled as per the definition of the arguments `current_cdi_attest`
        //   and `current_cdi_seal.
        // * The fourth argument is a pointer to `DiceInputValues`. It, and its indirect
        //   references must be valid for the duration of the function call which
        //   is guaranteed by `call_with_input_values` which puts `DiceInputValues`
        //   on the stack and initializes it from the `input_values` argument which
        //   implements the `InputValues` trait.
        // * The fifth and sixth argument are the length of and the pointer to the
        //   allocated certificate buffer respectively. They are used to return
        //   the generated certificate.
        // * The seventh argument is a pointer to a mutable usize object. It is
        //   used to return the actual size of the output certificate.
        // * The eighth argument and the ninth argument are pointers to mutable buffers of size
        //   CDI_SIZE. This is fulfilled if the allocation above succeeded.
        // * No pointers are expected to be valid beyond the scope of the function
        //   call.
        call_with_input_values(input_values, |input_values| {
            let cert = retry_while_adjusting_output_buffer(|cert, actual_size| {
                check_result(unsafe {
                    DiceMainFlow(
                        self.get_context(),
                        current_cdi_attest.as_ptr(),
                        current_cdi_seal.as_ptr(),
                        input_values,
                        cert.len(),
                        cert.as_mut_ptr(),
                        actual_size as *mut _,
                        next_attest.as_mut_ptr(),
                        next_seal.as_mut_ptr(),
                    )
                })
            })?;
            Ok((next_attest, next_seal, cert))
        })
    }

    /// Safe wrapper around open-dice DiceHash, see open dice
    /// documentation for details.
    fn hash(&mut self, input: &[u8]) -> Result<Vec<u8>> {
        let mut output: Vec<u8> = vec![0; HASH_SIZE];

        // SAFETY:
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument and the third argument are the pointer to and length of the given
        //   input buffer respectively.
        // * The fourth argument must be a pointer to a mutable buffer of size HASH_SIZE
        //   which is fulfilled by the allocation above.
        check_result(unsafe {
            DiceHash(self.get_context(), input.as_ptr(), input.len(), output.as_mut_ptr())
        })?;
        Ok(output)
    }

    /// Safe wrapper around open-dice DiceKdf, see open dice
    /// documentation for details.
    fn kdf(&mut self, length: usize, input_key: &[u8], salt: &[u8], info: &[u8]) -> Result<ZVec> {
        let mut output = ZVec::new(length)?;

        // SAFETY:
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument is primitive.
        // * The third argument and the fourth argument are the pointer to and length of the given
        //   input key.
        // * The fifth argument and the sixth argument are the pointer to and length of the given
        //   salt.
        // * The seventh argument and the eighth argument are the pointer to and length of the
        //   given info field.
        // * The ninth argument is a pointer to the output buffer which must have the
        //   length given by the `length` argument (see second argument). This is
        //   fulfilled if the allocation of `output` succeeds.
        // * All pointers must be valid for the duration of the function call, but not
        //   longer.
        check_result(unsafe {
            DiceKdf(
                self.get_context(),
                length,
                input_key.as_ptr(),
                input_key.len(),
                salt.as_ptr(),
                salt.len(),
                info.as_ptr(),
                info.len(),
                output.as_mut_ptr(),
            )
        })?;
        Ok(output)
    }

    /// Safe wrapper around open-dice DiceKeyPairFromSeed, see open dice
    /// documentation for details.
    fn keypair_from_seed(&mut self, seed: &[u8; PRIVATE_KEY_SEED_SIZE]) -> Result<(Vec<u8>, ZVec)> {
        let mut private_key = ZVec::new(PRIVATE_KEY_SIZE)?;
        let mut public_key = vec![0u8; PUBLIC_KEY_SIZE];

        // SAFETY:
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument is a pointer to a const buffer of size `PRIVATE_KEY_SEED_SIZE`
        //   fulfilled by the definition of the argument.
        // * The third argument and the fourth argument are mutable buffers of size
        //   `PRIVATE_KEY_SIZE` and `PUBLIC_KEY_SIZE` respectively. This is fulfilled by the
        //   allocations above.
        // * All pointers must be valid for the duration of the function call but not beyond.
        check_result(unsafe {
            DiceKeypairFromSeed(
                self.get_context(),
                seed.as_ptr(),
                public_key.as_mut_ptr(),
                private_key.as_mut_ptr(),
            )
        })?;
        Ok((public_key, private_key))
    }

    /// Safe wrapper around open-dice DiceSign, see open dice
    /// documentation for details.
    fn sign(&mut self, message: &[u8], private_key: &[u8; PRIVATE_KEY_SIZE]) -> Result<Vec<u8>> {
        let mut signature = vec![0u8; SIGNATURE_SIZE];

        // SAFETY:
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument and the third argument are the pointer to and length of the given
        //   message buffer.
        // * The fourth argument is a const buffer of size `PRIVATE_KEY_SIZE`. This is fulfilled
        //   by the definition of `private key`.
        // * The fifth argument is mutable buffer of size `SIGNATURE_SIZE`. This is fulfilled
        //   by the allocation above.
        // * All pointers must be valid for the duration of the function call but not beyond.
        check_result(unsafe {
            DiceSign(
                self.get_context(),
                message.as_ptr(),
                message.len(),
                private_key.as_ptr(),
                signature.as_mut_ptr(),
            )
        })?;
        Ok(signature)
    }

    /// Safe wrapper around open-dice DiceVerify, see open dice
    /// documentation for details.
    fn verify(
        &mut self,
        message: &[u8],
        signature: &[u8; SIGNATURE_SIZE],
        public_key: &[u8; PUBLIC_KEY_SIZE],
    ) -> Result<()> {
        // SAFETY:
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument and the third argument are the pointer to and length of the given
        //   message buffer.
        // * The fourth argument is a const buffer of size `SIGNATURE_SIZE`. This is fulfilled
        //   by the definition of `signature`.
        // * The fifth argument is a const buffer of size `PUBLIC_KEY_SIZE`. This is fulfilled
        //   by the definition of `public_key`.
        // * All pointers must be valid for the duration of the function call but not beyond.
        check_result(unsafe {
            DiceVerify(
                self.get_context(),
                message.as_ptr(),
                message.len(),
                signature.as_ptr(),
                public_key.as_ptr(),
            )
        })
    }

    /// Safe wrapper around open-dice DiceGenerateCertificate, see open dice
    /// documentation for details.
    fn generate_certificate<T: InputValues>(
        &mut self,
        subject_private_key_seed: &[u8; PRIVATE_KEY_SEED_SIZE],
        authority_private_key_seed: &[u8; PRIVATE_KEY_SEED_SIZE],
        input_values: &T,
    ) -> Result<Vec<u8>> {
        // SAFETY (DiceMainFlow):
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument and the third argument are const arrays of size
        //   `PRIVATE_KEY_SEED_SIZE`. This is fulfilled as per the definition of the arguments.
        // * The fourth argument is a pointer to `DiceInputValues` it, and its indirect
        //   references must be valid for the duration of the function call which
        //   is guaranteed by `call_with_input_values` which puts `DiceInputValues`
        //   on the stack and initializes it from the `input_values` argument which
        //   implements the `InputValues` trait.
        // * The fifth argument and the sixth argument are the length of and the pointer to the
        //   allocated certificate buffer respectively. They are used to return
        //   the generated certificate.
        // * The seventh argument is a pointer to a mutable usize object. It is
        //   used to return the actual size of the output certificate.
        // * All pointers must be valid for the duration of the function call but not beyond.
        call_with_input_values(input_values, |input_values| {
            let cert = retry_while_adjusting_output_buffer(|cert, actual_size| {
                check_result(unsafe {
                    DiceGenerateCertificate(
                        self.get_context(),
                        subject_private_key_seed.as_ptr(),
                        authority_private_key_seed.as_ptr(),
                        input_values,
                        cert.len(),
                        cert.as_mut_ptr(),
                        actual_size as *mut _,
                    )
                })
            })?;
            Ok(cert)
        })
    }

    /// Safe wrapper around open-dice BccDiceMainFlow, see open dice
    /// documentation for details.
    /// Returns a tuple of:
    ///  * The next attestation CDI,
    ///  * the next seal CDI, and
    ///  * the next bcc adding the new certificate to the given bcc.
    /// `(next_attest_cdi, next_seal_cdi, next_bcc)`
    fn bcc_main_flow<T: InputValues + ?Sized>(
        &mut self,
        current_cdi_attest: &[u8; CDI_SIZE],
        current_cdi_seal: &[u8; CDI_SIZE],
        bcc: &[u8],
        input_values: &T,
    ) -> Result<(CdiAttest, CdiSeal, Bcc)> {
        let mut next_attest = CdiAttest::new(CDI_SIZE)?;
        let mut next_seal = CdiSeal::new(CDI_SIZE)?;

        // SAFETY (BccMainFlow):
        // * The first context argument may be NULL and is unused by the wrapped
        //   implementation.
        // * The second argument and the third argument are const arrays of size CDI_SIZE.
        //   This is fulfilled as per the definition of the arguments `current_cdi_attest`
        //   and `current_cdi_seal`.
        // * The fourth argument and the fifth argument are the pointer to and size of the buffer
        //   holding the current bcc.
        // * The sixth argument is a pointer to `DiceInputValues` it, and its indirect
        //   references must be valid for the duration of the function call which
        //   is guaranteed by `call_with_input_values` which puts `DiceInputValues`
        //   on the stack and initializes it from the `input_values` argument which
        //   implements the `InputValues` trait.
        // * The seventh argument and the eighth argument are the length of and the pointer to the
        //   allocated certificate buffer respectively. They are used to return the generated
        //   certificate.
        // * The ninth argument is a pointer to a mutable usize object. It is
        //   used to return the actual size of the output certificate.
        // * The tenth argument and the eleventh argument are pointers to mutable buffers of
        //   size CDI_SIZE. This is fulfilled if the allocation above succeeded.
        // * No pointers are expected to be valid beyond the scope of the function
        //   call.
        call_with_input_values(input_values, |input_values| {
            let next_bcc = retry_while_adjusting_output_buffer(|next_bcc, actual_size| {
                check_result(unsafe {
                    BccMainFlow(
                        self.get_context(),
                        current_cdi_attest.as_ptr(),
                        current_cdi_seal.as_ptr(),
                        bcc.as_ptr(),
                        bcc.len(),
                        input_values,
                        next_bcc.len(),
                        next_bcc.as_mut_ptr(),
                        actual_size as *mut _,
                        next_attest.as_mut_ptr(),
                        next_seal.as_mut_ptr(),
                    )
                })
            })?;
            Ok((next_attest, next_seal, next_bcc))
        })
    }
}

/// This submodule provides additional support for the Boot Certificate Chain (BCC)
/// specification.
/// See https://cs.android.com/android/platform/superproject/+/master:hardware/interfaces/security/keymint/aidl/android/hardware/security/keymint/ProtectedData.aidl
pub mod bcc {
    use super::{check_result, retry_while_adjusting_output_buffer, Result};
    use open_dice_bcc_bindgen::{
        BccConfigValues, BccFormatConfigDescriptor, BCC_INPUT_COMPONENT_NAME,
        BCC_INPUT_COMPONENT_VERSION, BCC_INPUT_RESETTABLE,
    };
    use std::ffi::CString;

    /// Safe wrapper around BccFormatConfigDescriptor, see open dice documentation for details.
    pub fn format_config_descriptor(
        component_name: Option<&str>,
        component_version: Option<u64>,
        resettable: bool,
    ) -> Result<Vec<u8>> {
        let component_name = match component_name {
            Some(n) => Some(CString::new(n)?),
            None => None,
        };
        let input = BccConfigValues {
            inputs: if component_name.is_some() { BCC_INPUT_COMPONENT_NAME } else { 0 }
                | if component_version.is_some() { BCC_INPUT_COMPONENT_VERSION } else { 0 }
                | if resettable { BCC_INPUT_RESETTABLE } else { 0 },
            // SAFETY: The as_ref() in the line below is vital to keep the component_name object
            //         alive. Removing as_ref will move the component_name and the pointer will
            //         become invalid after this statement.
            component_name: component_name.as_ref().map_or(std::ptr::null(), |s| s.as_ptr()),
            component_version: component_version.unwrap_or(0),
        };

        // SAFETY:
        // * The first argument is a pointer to the BccConfigValues input assembled above.
        //   It and its indirections must be valid for the duration of the function call.
        // * The second argument and the third argument are the length of and the pointer to the
        //   allocated output buffer respectively. The buffer must be at least as long
        //   as indicated by the size argument.
        // * The forth argument is a pointer to the actual size returned by the function.
        // * All pointers must be valid for the duration of the function call but not beyond.
        retry_while_adjusting_output_buffer(|config_descriptor, actual_size| {
            check_result(unsafe {
                BccFormatConfigDescriptor(
                    &input as *const BccConfigValues,
                    config_descriptor.len(),
                    config_descriptor.as_mut_ptr(),
                    actual_size as *mut _,
                )
            })
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use diced_sample_inputs::make_sample_bcc_and_cdis;
    use std::convert::TryInto;

    static SEED_TEST_VECTOR: &[u8] = &[
        0xfa, 0x3c, 0x2f, 0x58, 0x37, 0xf5, 0x8e, 0x96, 0x16, 0x09, 0xf5, 0x22, 0xa1, 0xf1, 0xba,
        0xaa, 0x19, 0x95, 0x01, 0x79, 0x2e, 0x60, 0x56, 0xaf, 0xf6, 0x41, 0xe7, 0xff, 0x48, 0xf5,
        0x3a, 0x08, 0x84, 0x8a, 0x98, 0x85, 0x6d, 0xf5, 0x69, 0x21, 0x03, 0xcd, 0x09, 0xc3, 0x28,
        0xd6, 0x06, 0xa7, 0x57, 0xbd, 0x48, 0x4b, 0x0f, 0x79, 0x0f, 0xf8, 0x2f, 0xf0, 0x0a, 0x41,
        0x94, 0xd8, 0x8c, 0xa8,
    ];

    static CDI_ATTEST_TEST_VECTOR: &[u8] = &[
        0xfa, 0x3c, 0x2f, 0x58, 0x37, 0xf5, 0x8e, 0x96, 0x16, 0x09, 0xf5, 0x22, 0xa1, 0xf1, 0xba,
        0xaa, 0x19, 0x95, 0x01, 0x79, 0x2e, 0x60, 0x56, 0xaf, 0xf6, 0x41, 0xe7, 0xff, 0x48, 0xf5,
        0x3a, 0x08,
    ];
    static CDI_PRIVATE_KEY_SEED_TEST_VECTOR: &[u8] = &[
        0x5f, 0xcc, 0x8e, 0x1a, 0xd1, 0xc2, 0xb3, 0xe9, 0xfb, 0xe1, 0x68, 0xf0, 0xf6, 0x98, 0xfe,
        0x0d, 0xee, 0xd4, 0xb5, 0x18, 0xcb, 0x59, 0x70, 0x2d, 0xee, 0x06, 0xe5, 0x70, 0xf1, 0x72,
        0x02, 0x6e,
    ];

    static PUB_KEY_TEST_VECTOR: &[u8] = &[
        0x47, 0x42, 0x4b, 0xbd, 0xd7, 0x23, 0xb4, 0xcd, 0xca, 0xe2, 0x8e, 0xdc, 0x6b, 0xfc, 0x23,
        0xc9, 0x21, 0x5c, 0x48, 0x21, 0x47, 0xee, 0x5b, 0xfa, 0xaf, 0x88, 0x9a, 0x52, 0xf1, 0x61,
        0x06, 0x37,
    ];
    static PRIV_KEY_TEST_VECTOR: &[u8] = &[
        0x5f, 0xcc, 0x8e, 0x1a, 0xd1, 0xc2, 0xb3, 0xe9, 0xfb, 0xe1, 0x68, 0xf0, 0xf6, 0x98, 0xfe,
        0x0d, 0xee, 0xd4, 0xb5, 0x18, 0xcb, 0x59, 0x70, 0x2d, 0xee, 0x06, 0xe5, 0x70, 0xf1, 0x72,
        0x02, 0x6e, 0x47, 0x42, 0x4b, 0xbd, 0xd7, 0x23, 0xb4, 0xcd, 0xca, 0xe2, 0x8e, 0xdc, 0x6b,
        0xfc, 0x23, 0xc9, 0x21, 0x5c, 0x48, 0x21, 0x47, 0xee, 0x5b, 0xfa, 0xaf, 0x88, 0x9a, 0x52,
        0xf1, 0x61, 0x06, 0x37,
    ];

    static SIGNATURE_TEST_VECTOR: &[u8] = &[
        0x44, 0xae, 0xcc, 0xe2, 0xb9, 0x96, 0x18, 0x39, 0x0e, 0x61, 0x0f, 0x53, 0x07, 0xbf, 0xf2,
        0x32, 0x3d, 0x44, 0xd4, 0xf2, 0x07, 0x23, 0x30, 0x85, 0x32, 0x18, 0xd2, 0x69, 0xb8, 0x29,
        0x3c, 0x26, 0xe6, 0x0d, 0x9c, 0xa5, 0xc2, 0x73, 0xcd, 0x8c, 0xb8, 0x3c, 0x3e, 0x5b, 0xfd,
        0x62, 0x8d, 0xf6, 0xc4, 0x27, 0xa6, 0xe9, 0x11, 0x06, 0x5a, 0xb2, 0x2b, 0x64, 0xf7, 0xfc,
        0xbb, 0xab, 0x4a, 0x0e,
    ];

    #[test]
    fn hash_derive_sign_verify() {
        let mut ctx = OpenDiceCborContext::new();
        let seed = ctx.hash("MySeedString".as_bytes()).unwrap();
        assert_eq!(seed, SEED_TEST_VECTOR);
        let cdi_attest = &seed[..CDI_SIZE];
        assert_eq!(cdi_attest, CDI_ATTEST_TEST_VECTOR);
        let cdi_private_key_seed =
            ctx.derive_cdi_private_key_seed(cdi_attest.try_into().unwrap()).unwrap();
        assert_eq!(&cdi_private_key_seed[..], CDI_PRIVATE_KEY_SEED_TEST_VECTOR);
        let (pub_key, priv_key) =
            ctx.keypair_from_seed(cdi_private_key_seed[..].try_into().unwrap()).unwrap();
        assert_eq!(&pub_key, PUB_KEY_TEST_VECTOR);
        assert_eq!(&priv_key[..], PRIV_KEY_TEST_VECTOR);
        let mut signature =
            ctx.sign("MyMessage".as_bytes(), priv_key[..].try_into().unwrap()).unwrap();
        assert_eq!(&signature, SIGNATURE_TEST_VECTOR);
        assert!(ctx
            .verify(
                "MyMessage".as_bytes(),
                signature[..].try_into().unwrap(),
                pub_key[..].try_into().unwrap()
            )
            .is_ok());
        assert!(ctx
            .verify(
                "MyMessage_fail".as_bytes(),
                signature[..].try_into().unwrap(),
                pub_key[..].try_into().unwrap()
            )
            .is_err());
        signature[0] += 1;
        assert!(ctx
            .verify(
                "MyMessage".as_bytes(),
                signature[..].try_into().unwrap(),
                pub_key[..].try_into().unwrap()
            )
            .is_err());
    }

    static SAMPLE_CDI_ATTEST_TEST_VECTOR: &[u8] = &[
        0x3e, 0x57, 0x65, 0x5d, 0x48, 0x02, 0xbd, 0x5c, 0x66, 0xcc, 0x1f, 0x0f, 0xbe, 0x5e, 0x32,
        0xb6, 0x9e, 0x3d, 0x04, 0xaf, 0x00, 0x15, 0xbc, 0xdd, 0x1f, 0xbc, 0x59, 0xe4, 0xc3, 0x87,
        0x95, 0x5e,
    ];

    static SAMPLE_CDI_SEAL_TEST_VECTOR: &[u8] = &[
        0x36, 0x1b, 0xd2, 0xb3, 0xc4, 0xda, 0x77, 0xb2, 0x9c, 0xba, 0x39, 0x53, 0x82, 0x93, 0xd9,
        0xb8, 0x9f, 0x73, 0x2d, 0x27, 0x06, 0x15, 0xa8, 0xcb, 0x6d, 0x1d, 0xf2, 0xb1, 0x54, 0xbb,
        0x62, 0xf1,
    ];

    static SAMPLE_BCC_TEST_VECTOR: &[u8] = &[
        0x84, 0xa5, 0x01, 0x01, 0x03, 0x27, 0x04, 0x02, 0x20, 0x06, 0x21, 0x58, 0x20, 0x3e, 0x85,
        0xe5, 0x72, 0x75, 0x55, 0xe5, 0x1e, 0xe7, 0xf3, 0x35, 0x94, 0x8e, 0xbb, 0xbd, 0x74, 0x1e,
        0x1d, 0xca, 0x49, 0x9c, 0x97, 0x39, 0x77, 0x06, 0xd3, 0xc8, 0x6e, 0x8b, 0xd7, 0x33, 0xf9,
        0x84, 0x43, 0xa1, 0x01, 0x27, 0xa0, 0x59, 0x01, 0x8a, 0xa9, 0x01, 0x78, 0x28, 0x34, 0x32,
        0x64, 0x38, 0x38, 0x36, 0x34, 0x66, 0x39, 0x37, 0x62, 0x36, 0x35, 0x34, 0x37, 0x61, 0x35,
        0x30, 0x63, 0x31, 0x65, 0x30, 0x61, 0x37, 0x34, 0x39, 0x66, 0x38, 0x65, 0x66, 0x38, 0x62,
        0x38, 0x31, 0x65, 0x63, 0x36, 0x32, 0x61, 0x66, 0x02, 0x78, 0x28, 0x31, 0x66, 0x36, 0x39,
        0x36, 0x66, 0x30, 0x37, 0x32, 0x35, 0x32, 0x66, 0x32, 0x39, 0x65, 0x39, 0x33, 0x66, 0x65,
        0x34, 0x64, 0x65, 0x31, 0x39, 0x65, 0x65, 0x33, 0x32, 0x63, 0x64, 0x38, 0x31, 0x64, 0x63,
        0x34, 0x30, 0x34, 0x65, 0x37, 0x36, 0x3a, 0x00, 0x47, 0x44, 0x50, 0x58, 0x40, 0x16, 0x48,
        0xf2, 0x55, 0x53, 0x23, 0xdd, 0x15, 0x2e, 0x83, 0x38, 0xc3, 0x64, 0x38, 0x63, 0x26, 0x0f,
        0xcf, 0x5b, 0xd1, 0x3a, 0xd3, 0x40, 0x3e, 0x23, 0xf8, 0x34, 0x4c, 0x6d, 0xa2, 0xbe, 0x25,
        0x1c, 0xb0, 0x29, 0xe8, 0xc3, 0xfb, 0xb8, 0x80, 0xdc, 0xb1, 0xd2, 0xb3, 0x91, 0x4d, 0xd3,
        0xfb, 0x01, 0x0f, 0xe4, 0xe9, 0x46, 0xa2, 0xc0, 0x26, 0x57, 0x5a, 0xba, 0x30, 0xf7, 0x15,
        0x98, 0x14, 0x3a, 0x00, 0x47, 0x44, 0x53, 0x56, 0xa3, 0x3a, 0x00, 0x01, 0x11, 0x71, 0x63,
        0x41, 0x42, 0x4c, 0x3a, 0x00, 0x01, 0x11, 0x72, 0x01, 0x3a, 0x00, 0x01, 0x11, 0x73, 0xf6,
        0x3a, 0x00, 0x47, 0x44, 0x52, 0x58, 0x40, 0x47, 0xae, 0x42, 0x27, 0x4c, 0xcb, 0x65, 0x4d,
        0xee, 0x74, 0x2d, 0x05, 0x78, 0x2a, 0x08, 0x2a, 0xa5, 0xf0, 0xcf, 0xea, 0x3e, 0x60, 0xee,
        0x97, 0x11, 0x4b, 0x5b, 0xe6, 0x05, 0x0c, 0xe8, 0x90, 0xf5, 0x22, 0xc4, 0xc6, 0x67, 0x7a,
        0x22, 0x27, 0x17, 0xb3, 0x79, 0xcc, 0x37, 0x64, 0x5e, 0x19, 0x4f, 0x96, 0x37, 0x67, 0x3c,
        0xd0, 0xc5, 0xed, 0x0f, 0xdd, 0xe7, 0x2e, 0x4f, 0x70, 0x97, 0x30, 0x3a, 0x00, 0x47, 0x44,
        0x54, 0x58, 0x40, 0xf9, 0x00, 0x9d, 0xc2, 0x59, 0x09, 0xe0, 0xb6, 0x98, 0xbd, 0xe3, 0x97,
        0x4a, 0xcb, 0x3c, 0xe7, 0x6b, 0x24, 0xc3, 0xe4, 0x98, 0xdd, 0xa9, 0x6a, 0x41, 0x59, 0x15,
        0xb1, 0x23, 0xe6, 0xc8, 0xdf, 0xfb, 0x52, 0xb4, 0x52, 0xc1, 0xb9, 0x61, 0xdd, 0xbc, 0x5b,
        0x37, 0x0e, 0x12, 0x12, 0xb2, 0xfd, 0xc1, 0x09, 0xb0, 0xcf, 0x33, 0x81, 0x4c, 0xc6, 0x29,
        0x1b, 0x99, 0xea, 0xae, 0xfd, 0xaa, 0x0d, 0x3a, 0x00, 0x47, 0x44, 0x56, 0x41, 0x01, 0x3a,
        0x00, 0x47, 0x44, 0x57, 0x58, 0x2d, 0xa5, 0x01, 0x01, 0x03, 0x27, 0x04, 0x81, 0x02, 0x20,
        0x06, 0x21, 0x58, 0x20, 0xb1, 0x02, 0xcc, 0x2c, 0xb2, 0x6a, 0x3b, 0xe9, 0xc1, 0xd3, 0x95,
        0x10, 0xa0, 0xe1, 0xff, 0x51, 0xde, 0x57, 0xd5, 0x65, 0x28, 0xfd, 0x7f, 0xeb, 0xd4, 0xca,
        0x15, 0xf3, 0xca, 0xdf, 0x37, 0x88, 0x3a, 0x00, 0x47, 0x44, 0x58, 0x41, 0x20, 0x58, 0x40,
        0x58, 0xd8, 0x03, 0x24, 0x53, 0x60, 0x57, 0xa9, 0x09, 0xfa, 0xab, 0xdc, 0x57, 0x1e, 0xf0,
        0xe5, 0x1e, 0x51, 0x6f, 0x9e, 0xa3, 0x42, 0xe6, 0x6a, 0x8c, 0xaa, 0xad, 0x08, 0x48, 0xde,
        0x7f, 0x4f, 0x6e, 0x2f, 0x7f, 0x39, 0x6c, 0xa1, 0xf8, 0x42, 0x71, 0xfe, 0x17, 0x3d, 0xca,
        0x31, 0x83, 0x92, 0xed, 0xbb, 0x40, 0xb8, 0x10, 0xe0, 0xf2, 0x5a, 0x99, 0x53, 0x38, 0x46,
        0x33, 0x97, 0x78, 0x05, 0x84, 0x43, 0xa1, 0x01, 0x27, 0xa0, 0x59, 0x01, 0x8a, 0xa9, 0x01,
        0x78, 0x28, 0x31, 0x66, 0x36, 0x39, 0x36, 0x66, 0x30, 0x37, 0x32, 0x35, 0x32, 0x66, 0x32,
        0x39, 0x65, 0x39, 0x33, 0x66, 0x65, 0x34, 0x64, 0x65, 0x31, 0x39, 0x65, 0x65, 0x33, 0x32,
        0x63, 0x64, 0x38, 0x31, 0x64, 0x63, 0x34, 0x30, 0x34, 0x65, 0x37, 0x36, 0x02, 0x78, 0x28,
        0x32, 0x35, 0x39, 0x34, 0x38, 0x39, 0x65, 0x36, 0x39, 0x37, 0x34, 0x38, 0x37, 0x30, 0x35,
        0x64, 0x65, 0x33, 0x65, 0x32, 0x66, 0x34, 0x34, 0x32, 0x36, 0x37, 0x65, 0x61, 0x34, 0x39,
        0x33, 0x38, 0x66, 0x66, 0x36, 0x61, 0x35, 0x37, 0x32, 0x35, 0x3a, 0x00, 0x47, 0x44, 0x50,
        0x58, 0x40, 0xa4, 0x0c, 0xcb, 0xc1, 0xbf, 0xfa, 0xcc, 0xfd, 0xeb, 0xf4, 0xfc, 0x43, 0x83,
        0x7f, 0x46, 0x8d, 0xd8, 0xd8, 0x14, 0xc1, 0x96, 0x14, 0x1f, 0x6e, 0xb3, 0xa0, 0xd9, 0x56,
        0xb3, 0xbf, 0x2f, 0xfa, 0x88, 0x70, 0x11, 0x07, 0x39, 0xa4, 0xd2, 0xa9, 0x6b, 0x18, 0x28,
        0xe8, 0x29, 0x20, 0x49, 0x0f, 0xbb, 0x8d, 0x08, 0x8c, 0xc6, 0x54, 0xe9, 0x71, 0xd2, 0x7e,
        0xa4, 0xfe, 0x58, 0x7f, 0xd3, 0xc7, 0x3a, 0x00, 0x47, 0x44, 0x53, 0x56, 0xa3, 0x3a, 0x00,
        0x01, 0x11, 0x71, 0x63, 0x41, 0x56, 0x42, 0x3a, 0x00, 0x01, 0x11, 0x72, 0x01, 0x3a, 0x00,
        0x01, 0x11, 0x73, 0xf6, 0x3a, 0x00, 0x47, 0x44, 0x52, 0x58, 0x40, 0x93, 0x17, 0xe1, 0x11,
        0x27, 0x59, 0xd0, 0xef, 0x75, 0x0b, 0x2b, 0x1c, 0x0f, 0x5f, 0x52, 0xc3, 0x29, 0x23, 0xb5,
        0x2a, 0xe6, 0x12, 0x72, 0x6f, 0x39, 0x86, 0x65, 0x2d, 0xf2, 0xe4, 0xe7, 0xd0, 0xaf, 0x0e,
        0xa7, 0x99, 0x16, 0x89, 0x97, 0x21, 0xf7, 0xdc, 0x89, 0xdc, 0xde, 0xbb, 0x94, 0x88, 0x1f,
        0xda, 0xe2, 0xf3, 0xe0, 0x54, 0xf9, 0x0e, 0x29, 0xb1, 0xbd, 0xe1, 0x0c, 0x0b, 0xd7, 0xf6,
        0x3a, 0x00, 0x47, 0x44, 0x54, 0x58, 0x40, 0xb2, 0x69, 0x05, 0x48, 0x56, 0xb5, 0xfa, 0x55,
        0x6f, 0xac, 0x56, 0xd9, 0x02, 0x35, 0x2b, 0xaa, 0x4c, 0xba, 0x28, 0xdd, 0x82, 0x3a, 0x86,
        0xf5, 0xd4, 0xc2, 0xf1, 0xf9, 0x35, 0x7d, 0xe4, 0x43, 0x13, 0xbf, 0xfe, 0xd3, 0x36, 0xd8,
        0x1c, 0x12, 0x78, 0x5c, 0x9c, 0x3e, 0xf6, 0x66, 0xef, 0xab, 0x3d, 0x0f, 0x89, 0xa4, 0x6f,
        0xc9, 0x72, 0xee, 0x73, 0x43, 0x02, 0x8a, 0xef, 0xbc, 0x05, 0x98, 0x3a, 0x00, 0x47, 0x44,
        0x56, 0x41, 0x01, 0x3a, 0x00, 0x47, 0x44, 0x57, 0x58, 0x2d, 0xa5, 0x01, 0x01, 0x03, 0x27,
        0x04, 0x81, 0x02, 0x20, 0x06, 0x21, 0x58, 0x20, 0x96, 0x6d, 0x96, 0x42, 0xda, 0x64, 0x51,
        0xad, 0xfa, 0x00, 0xbc, 0xbc, 0x95, 0x8a, 0xb0, 0xb9, 0x76, 0x01, 0xe6, 0xbd, 0xc0, 0x26,
        0x79, 0x26, 0xfc, 0x0f, 0x1d, 0x87, 0x65, 0xf1, 0xf3, 0x99, 0x3a, 0x00, 0x47, 0x44, 0x58,
        0x41, 0x20, 0x58, 0x40, 0x10, 0x7f, 0x77, 0xad, 0x70, 0xbd, 0x52, 0x81, 0x28, 0x8d, 0x24,
        0x81, 0xb4, 0x3f, 0x21, 0x68, 0x9f, 0xc3, 0x80, 0x68, 0x86, 0x55, 0xfb, 0x2e, 0x6d, 0x96,
        0xe1, 0xe1, 0xb7, 0x28, 0x8d, 0x63, 0x85, 0xba, 0x2a, 0x01, 0x33, 0x87, 0x60, 0x63, 0xbb,
        0x16, 0x3f, 0x2f, 0x3d, 0xf4, 0x2d, 0x48, 0x5b, 0x87, 0xed, 0xda, 0x34, 0xeb, 0x9c, 0x4d,
        0x14, 0xac, 0x65, 0xf4, 0xfa, 0xef, 0x45, 0x0b, 0x84, 0x43, 0xa1, 0x01, 0x27, 0xa0, 0x59,
        0x01, 0x8f, 0xa9, 0x01, 0x78, 0x28, 0x32, 0x35, 0x39, 0x34, 0x38, 0x39, 0x65, 0x36, 0x39,
        0x37, 0x34, 0x38, 0x37, 0x30, 0x35, 0x64, 0x65, 0x33, 0x65, 0x32, 0x66, 0x34, 0x34, 0x32,
        0x36, 0x37, 0x65, 0x61, 0x34, 0x39, 0x33, 0x38, 0x66, 0x66, 0x36, 0x61, 0x35, 0x37, 0x32,
        0x35, 0x02, 0x78, 0x28, 0x35, 0x64, 0x34, 0x65, 0x64, 0x37, 0x66, 0x34, 0x31, 0x37, 0x61,
        0x39, 0x35, 0x34, 0x61, 0x31, 0x38, 0x31, 0x34, 0x30, 0x37, 0x62, 0x35, 0x38, 0x38, 0x35,
        0x61, 0x66, 0x64, 0x37, 0x32, 0x61, 0x35, 0x62, 0x66, 0x34, 0x30, 0x64, 0x61, 0x36, 0x3a,
        0x00, 0x47, 0x44, 0x50, 0x58, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3a, 0x00, 0x47, 0x44, 0x53,
        0x58, 0x1a, 0xa3, 0x3a, 0x00, 0x01, 0x11, 0x71, 0x67, 0x41, 0x6e, 0x64, 0x72, 0x6f, 0x69,
        0x64, 0x3a, 0x00, 0x01, 0x11, 0x72, 0x0c, 0x3a, 0x00, 0x01, 0x11, 0x73, 0xf6, 0x3a, 0x00,
        0x47, 0x44, 0x52, 0x58, 0x40, 0x26, 0x1a, 0xbd, 0x26, 0xd8, 0x37, 0x8f, 0x4a, 0xf2, 0x9e,
        0x49, 0x4d, 0x93, 0x23, 0xc4, 0x6e, 0x02, 0xda, 0xe0, 0x00, 0x02, 0xe7, 0xed, 0x29, 0xdf,
        0x2b, 0xb3, 0x69, 0xf3, 0x55, 0x0e, 0x4c, 0x22, 0xdc, 0xcf, 0xf5, 0x92, 0xc9, 0xfa, 0x78,
        0x98, 0xf1, 0x0e, 0x55, 0x5f, 0xf4, 0x45, 0xed, 0xc0, 0x0a, 0x72, 0x2a, 0x7a, 0x3a, 0xd2,
        0xb1, 0xf7, 0x76, 0xfe, 0x2a, 0x6b, 0x7b, 0x2a, 0x53, 0x3a, 0x00, 0x47, 0x44, 0x54, 0x58,
        0x40, 0x04, 0x25, 0x5d, 0x60, 0x5f, 0x5c, 0x45, 0x0d, 0xf2, 0x9a, 0x6e, 0x99, 0x30, 0x03,
        0xb8, 0xd6, 0xe1, 0x99, 0x71, 0x1b, 0xf8, 0x44, 0xfa, 0xb5, 0x31, 0x79, 0x1c, 0x37, 0x68,
        0x4e, 0x1d, 0xc0, 0x24, 0x74, 0x68, 0xf8, 0x80, 0x20, 0x3e, 0x44, 0xb1, 0x43, 0xd2, 0x9c,
        0xfc, 0x12, 0x9e, 0x77, 0x0a, 0xde, 0x29, 0x24, 0xff, 0x2e, 0xfa, 0xc7, 0x10, 0xd5, 0x73,
        0xd4, 0xc6, 0xdf, 0x62, 0x9f, 0x3a, 0x00, 0x47, 0x44, 0x56, 0x41, 0x01, 0x3a, 0x00, 0x47,
        0x44, 0x57, 0x58, 0x2d, 0xa5, 0x01, 0x01, 0x03, 0x27, 0x04, 0x81, 0x02, 0x20, 0x06, 0x21,
        0x58, 0x20, 0xdb, 0xe7, 0x5b, 0x3f, 0xa3, 0x42, 0xb0, 0x9c, 0xf8, 0x40, 0x8c, 0xb0, 0x9c,
        0xf0, 0x0a, 0xaf, 0xdf, 0x6f, 0xe5, 0x09, 0x21, 0x11, 0x92, 0xe1, 0xf8, 0xc5, 0x09, 0x02,
        0x3d, 0x1f, 0xb7, 0xc5, 0x3a, 0x00, 0x47, 0x44, 0x58, 0x41, 0x20, 0x58, 0x40, 0xc4, 0xc1,
        0xd7, 0x1c, 0x2d, 0x26, 0x89, 0x22, 0xcf, 0xa6, 0x99, 0x77, 0x30, 0x84, 0x86, 0x27, 0x59,
        0x8f, 0xd8, 0x08, 0x75, 0xe0, 0xb2, 0xef, 0xf9, 0xfa, 0xa5, 0x40, 0x8c, 0xd3, 0xeb, 0xbb,
        0xda, 0xf2, 0xc8, 0xae, 0x41, 0x22, 0x50, 0x9c, 0xe8, 0xb2, 0x9c, 0x9b, 0x3f, 0x8a, 0x78,
        0x76, 0xab, 0xd0, 0xbe, 0xfc, 0xe4, 0x79, 0xcb, 0x1b, 0x2b, 0xaa, 0x4d, 0xdd, 0x15, 0x61,
        0x42, 0x06,
    ];

    // This test invokes make_sample_bcc_and_cdis and compares the result bitwise to the target
    // vectors. The function uses main_flow, bcc_main_flow, format_config_descriptor,
    // derive_cdi_private_key_seed, and keypair_from_seed. This test is sensitive to errors
    // and changes in any of those functions.
    #[test]
    fn main_flow_and_bcc_main_flow() {
        let (cdi_attest, cdi_seal, bcc) = make_sample_bcc_and_cdis().unwrap();
        assert_eq!(&cdi_attest[..], SAMPLE_CDI_ATTEST_TEST_VECTOR);
        assert_eq!(&cdi_seal[..], SAMPLE_CDI_SEAL_TEST_VECTOR);
        assert_eq!(&bcc[..], SAMPLE_BCC_TEST_VECTOR);
    }

    static DERIVED_KEY_TEST_VECTOR: &[u8] = &[
        0x0e, 0xd6, 0x07, 0x0e, 0x1c, 0x38, 0x2c, 0x76, 0x13, 0xc6, 0x76, 0x25, 0x7e, 0x07, 0x6f,
        0xdb, 0x1d, 0xb1, 0x0f, 0x3f, 0xed, 0xc5, 0x2b, 0x95, 0xd1, 0x32, 0xf1, 0x63, 0x2f, 0x2a,
        0x01, 0x5e,
    ];

    #[test]
    fn kdf() {
        let mut ctx = OpenDiceCborContext::new();
        let derived_key = ctx
            .kdf(
                PRIVATE_KEY_SEED_SIZE,
                "myKey".as_bytes(),
                "mySalt".as_bytes(),
                "myInfo".as_bytes(),
            )
            .unwrap();
        assert_eq!(&derived_key[..], DERIVED_KEY_TEST_VECTOR);
    }

    static CERT_ID_TEST_VECTOR: &[u8] = &[
        0x7a, 0x36, 0x45, 0x2c, 0x02, 0xf6, 0x2b, 0xec, 0xf9, 0x80, 0x06, 0x75, 0x87, 0xa5, 0xc1,
        0x44, 0x0c, 0xd3, 0xc0, 0x6d,
    ];

    #[test]
    fn derive_cdi_certificate_id() {
        let mut ctx = OpenDiceCborContext::new();
        let cert_id = ctx.derive_cdi_certificate_id("MyPubKey".as_bytes()).unwrap();
        assert_eq!(&cert_id[..], CERT_ID_TEST_VECTOR);
    }
}
