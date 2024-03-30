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

//! Common items useful for binder clients and/or servers.

pub mod lazy_service;
pub mod rpc_client;
pub mod rpc_server;

use binder::{ExceptionCode, Status};
use std::ffi::CString;

/// Constructs a new Binder error `Status` with the given `ExceptionCode` and message.
pub fn new_binder_exception<T: AsRef<str>>(exception: ExceptionCode, message: T) -> Status {
    match exception {
        ExceptionCode::SERVICE_SPECIFIC => new_binder_service_specific_error(-1, message),
        _ => Status::new_exception(exception, to_cstring(message).as_deref()),
    }
}

/// Constructs a Binder `Status` representing a service-specific exception with the given code and
/// message.
pub fn new_binder_service_specific_error<T: AsRef<str>>(code: i32, message: T) -> Status {
    Status::new_service_specific_error(code, to_cstring(message).as_deref())
}

fn to_cstring<T: AsRef<str>>(message: T) -> Option<CString> {
    CString::new(message.as_ref()).ok()
}
