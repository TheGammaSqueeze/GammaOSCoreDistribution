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

//! Helpers for implementing an RPC Binder client.

use binder::unstable_api::{new_spibinder, AIBinder};
use binder::{StatusCode, Strong};

/// Connects to a binder RPC server.
pub fn connect_rpc_binder<T: binder::FromIBinder + ?Sized>(
    cid: u32,
    port: u32,
) -> Result<Strong<T>, StatusCode> {
    // SAFETY: AIBinder returned by RpcClient has correct reference count, and the ownership can be
    // safely taken by new_spibinder.
    let ibinder = unsafe {
        new_spibinder(binder_rpc_unstable_bindgen::RpcClient(cid, port) as *mut AIBinder)
    };
    if let Some(ibinder) = ibinder {
        <T>::try_from(ibinder)
    } else {
        Err(StatusCode::BAD_VALUE)
    }
}
