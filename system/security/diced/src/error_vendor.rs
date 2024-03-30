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

use android_hardware_security_dice::aidl::android::hardware::security::dice::ResponseCode::ResponseCode;
use anyhow::Result;
use binder::{ExceptionCode, Result as BinderResult, Status as BinderStatus, StatusCode};
use std::ffi::CString;

/// This is the error type for DICE HAL implementations. It wraps
/// `android::hardware::security::dice::ResponseCode` generated
/// from AIDL in the `Rc` variant and Binder and BinderTransaction errors in the respective
/// variants.
#[allow(dead_code)] // Binder error forwarding will be needed when proxy nodes are implemented.
#[derive(Debug, thiserror::Error, Eq, PartialEq, Clone)]
pub enum Error {
    /// Wraps a dice `ResponseCode` as defined by the Keystore AIDL interface specification.
    #[error("Error::Rc({0:?})")]
    Rc(ResponseCode),
    /// Wraps a Binder exception code other than a service specific exception.
    #[error("Binder exception code {0:?}, {1:?}")]
    Binder(ExceptionCode, i32),
    /// Wraps a Binder status code.
    #[error("Binder transaction error {0:?}")]
    BinderTransaction(StatusCode),
}

/// This function should be used by dice service calls to translate error conditions
/// into service specific exceptions.
///
/// All error conditions get logged by this function.
///
/// All `Error::Rc(x)` variants get mapped onto a service specific error code of x.
/// `selinux::Error::PermissionDenied` is mapped on `ResponseCode::PERMISSION_DENIED`.
///
/// All non `Error` error conditions and the Error::Binder variant get mapped onto
/// ResponseCode::SYSTEM_ERROR`.
///
/// `handle_ok` will be called if `result` is `Ok(value)` where `value` will be passed
/// as argument to `handle_ok`. `handle_ok` must generate a `BinderResult<T>`, but it
/// typically returns Ok(value).
///
/// # Examples
///
/// ```
/// fn do_something() -> anyhow::Result<Vec<u8>> {
///     Err(anyhow!(Error::Rc(ResponseCode::NOT_IMPLEMENTED)))
/// }
///
/// map_or_log_err(do_something(), Ok)
/// ```
pub fn map_or_log_err<T, U, F>(result: Result<U>, handle_ok: F) -> BinderResult<T>
where
    F: FnOnce(U) -> BinderResult<T>,
{
    map_err_with(
        result,
        |e| {
            log::error!("{:?}", e);
            e
        },
        handle_ok,
    )
}

/// This function behaves similar to map_or_log_error, but it does not log the errors, instead
/// it calls map_err on the error before mapping it to a binder result allowing callers to
/// log or transform the error before mapping it.
fn map_err_with<T, U, F1, F2>(result: Result<U>, map_err: F1, handle_ok: F2) -> BinderResult<T>
where
    F1: FnOnce(anyhow::Error) -> anyhow::Error,
    F2: FnOnce(U) -> BinderResult<T>,
{
    result.map_or_else(
        |e| {
            let e = map_err(e);
            let msg = match CString::new(format!("{:?}", e)) {
                Ok(msg) => Some(msg),
                Err(_) => {
                    log::warn!(
                        "Cannot convert error message to CStr. It contained a nul byte.
                         Omitting message from service specific error."
                    );
                    None
                }
            };
            let rc = get_error_code(&e);
            Err(BinderStatus::new_service_specific_error(rc, msg.as_deref()))
        },
        handle_ok,
    )
}

/// Extracts the error code from an `anyhow::Error` mapping any error that does not have a
/// root cause of `Error::Rc` onto `ResponseCode::SYSTEM_ERROR` and to `e` with `Error::Rc(e)`
/// otherwise.
fn get_error_code(e: &anyhow::Error) -> i32 {
    let root_cause = e.root_cause();
    match root_cause.downcast_ref::<Error>() {
        Some(Error::Rc(rcode)) => rcode.0,
        // If an Error::Binder reaches this stage we report a system error.
        // The exception code and possible service specific error will be
        // printed in the error log above.
        Some(Error::Binder(_, _)) | Some(Error::BinderTransaction(_)) => {
            ResponseCode::SYSTEM_ERROR.0
        }
        None => ResponseCode::SYSTEM_ERROR.0,
    }
}
