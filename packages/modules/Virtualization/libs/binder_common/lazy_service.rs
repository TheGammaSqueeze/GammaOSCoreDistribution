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

//! Rust API for lazy (aka dynamic) AIDL services.
//! See https://source.android.com/devices/architecture/aidl/dynamic-aidl.

use binder::force_lazy_services_persist;
use lazy_static::lazy_static;
use std::sync::Mutex;

// TODO(b/200924402): Move this class to libbinder_rs once the infrastructure needed exists.

/// An RAII object to ensure a server of lazy services is not killed. During the lifetime of any of
/// these objects the service manager will not not kill the current process even if none of its
/// lazy services are in use.
#[must_use]
#[derive(Debug)]
pub struct LazyServiceGuard {
    // Prevent construction outside this module.
    _private: (),
}

lazy_static! {
    // Count of how many LazyServiceGuard objects are in existence.
    static ref GUARD_COUNT: Mutex<u64> = Mutex::new(0);
}

impl LazyServiceGuard {
    /// Create a new LazyServiceGuard to prevent the service manager prematurely killing this
    /// process.
    pub fn new() -> Self {
        let mut count = GUARD_COUNT.lock().unwrap();
        *count += 1;
        if *count == 1 {
            // It's important that we make this call with the mutex held, to make sure
            // that multiple calls (e.g. if the count goes 1 -> 0 -> 1) are correctly
            // sequenced. (That also means we can't just use an AtomicU64.)
            force_lazy_services_persist(true);
        }
        Self { _private: () }
    }
}

impl Drop for LazyServiceGuard {
    fn drop(&mut self) {
        let mut count = GUARD_COUNT.lock().unwrap();
        *count -= 1;
        if *count == 0 {
            force_lazy_services_persist(false);
        }
    }
}

impl Clone for LazyServiceGuard {
    fn clone(&self) -> Self {
        Self::new()
    }
}

impl Default for LazyServiceGuard {
    fn default() -> Self {
        Self::new()
    }
}
