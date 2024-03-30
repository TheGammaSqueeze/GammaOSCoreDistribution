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

//! Helpers for implementing an RPC Binder server.

use binder::unstable_api::AsNative;
use binder::SpIBinder;
use std::os::raw;

/// Run a binder RPC server, serving the supplied binder service implementation on the given vsock
/// port.
/// If and when the server is ready for connections (it is listening on the port) on_ready
/// is called to allow appropriate action to be taken - e.g. to notify clients they
/// may now attempt to connect.
/// The current thread is joined to the binder thread pool to handle incoming messages.
/// Returns true if the server has shutdown normally, false if it failed in some way.
pub fn run_rpc_server<F>(service: SpIBinder, port: u32, on_ready: F) -> bool
where
    F: FnOnce(),
{
    let mut ready_notifier = ReadyNotifier(Some(on_ready));
    ready_notifier.run_server(service, port)
}

struct ReadyNotifier<F>(Option<F>)
where
    F: FnOnce();

impl<F> ReadyNotifier<F>
where
    F: FnOnce(),
{
    fn run_server(&mut self, mut service: SpIBinder, port: u32) -> bool {
        let service = service.as_native_mut() as *mut binder_rpc_unstable_bindgen::AIBinder;
        let param = self.as_void_ptr();

        // SAFETY: Service ownership is transferring to the server and won't be valid afterward.
        // Plus the binder objects are threadsafe.
        // RunRpcServerCallback does not retain a reference to ready_callback, and only ever
        // calls it with the param we provide during the lifetime of self.
        unsafe {
            binder_rpc_unstable_bindgen::RunRpcServerCallback(
                service,
                port,
                Some(Self::ready_callback),
                param,
            )
        }
    }

    fn as_void_ptr(&mut self) -> *mut raw::c_void {
        self as *mut _ as *mut raw::c_void
    }

    unsafe extern "C" fn ready_callback(param: *mut raw::c_void) {
        // SAFETY: This is only ever called by RunRpcServerCallback, within the lifetime of the
        // ReadyNotifier, with param taking the value returned by as_void_ptr (so a properly aligned
        // non-null pointer to an initialized instance).
        let ready_notifier = param as *mut Self;
        ready_notifier.as_mut().unwrap().notify()
    }

    fn notify(&mut self) {
        if let Some(on_ready) = self.0.take() {
            on_ready();
        }
    }
}
