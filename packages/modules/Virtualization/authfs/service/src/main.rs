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

//! AuthFsService facilitates authfs mounting (which is a privileged operation) for the client. The
//! client will provide an `AuthFsConfig` which includes the backend address (only port for now) and
//! the filesystem configuration. It is up to the client to ensure the backend server is running. On
//! a successful mount, the client receives an `IAuthFs`, and through the binder object, the client
//! is able to retrieve "remote file descriptors".

mod authfs;

use anyhow::{bail, Context, Result};
use log::*;
use std::ffi::OsString;
use std::fs::{create_dir, read_dir, remove_dir_all, remove_file};
use std::sync::atomic::{AtomicUsize, Ordering};

use authfs_aidl_interface::aidl::com::android::virt::fs::AuthFsConfig::AuthFsConfig;
use authfs_aidl_interface::aidl::com::android::virt::fs::IAuthFs::IAuthFs;
use authfs_aidl_interface::aidl::com::android::virt::fs::IAuthFsService::{
    BnAuthFsService, IAuthFsService,
};
use authfs_aidl_interface::binder::{
    self, add_service, BinderFeatures, ExceptionCode, Interface, ProcessState, Strong,
};
use binder_common::new_binder_exception;

const SERVICE_NAME: &str = "authfs_service";
const SERVICE_ROOT: &str = "/data/misc/authfs";

/// Implementation of `IAuthFsService`.
pub struct AuthFsService {
    serial_number: AtomicUsize,
    debuggable: bool,
}

impl Interface for AuthFsService {}

impl IAuthFsService for AuthFsService {
    fn mount(&self, config: &AuthFsConfig) -> binder::Result<Strong<dyn IAuthFs>> {
        self.validate(config)?;

        let mountpoint = self.get_next_mount_point();

        // The directory is supposed to be deleted when `AuthFs` is dropped.
        create_dir(&mountpoint).map_err(|e| {
            new_binder_exception(
                ExceptionCode::SERVICE_SPECIFIC,
                format!("Cannot create mount directory {:?}: {:?}", &mountpoint, e),
            )
        })?;

        authfs::AuthFs::mount_and_wait(mountpoint, config, self.debuggable).map_err(|e| {
            new_binder_exception(
                ExceptionCode::SERVICE_SPECIFIC,
                format!("mount_and_wait failed: {:?}", e),
            )
        })
    }
}

impl AuthFsService {
    fn new_binder(debuggable: bool) -> Strong<dyn IAuthFsService> {
        let service = AuthFsService { serial_number: AtomicUsize::new(1), debuggable };
        BnAuthFsService::new_binder(service, BinderFeatures::default())
    }

    fn validate(&self, config: &AuthFsConfig) -> binder::Result<()> {
        if config.port < 0 {
            return Err(new_binder_exception(
                ExceptionCode::ILLEGAL_ARGUMENT,
                format!("Invalid port: {}", config.port),
            ));
        }
        Ok(())
    }

    fn get_next_mount_point(&self) -> OsString {
        let previous = self.serial_number.fetch_add(1, Ordering::Relaxed);
        OsString::from(format!("{}/{}", SERVICE_ROOT, previous))
    }
}

fn clean_up_working_directory() -> Result<()> {
    for entry in read_dir(SERVICE_ROOT)? {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            remove_dir_all(path)?;
        } else if path.is_file() {
            remove_file(path)?;
        } else {
            bail!("Unrecognized path type: {:?}", path);
        }
    }
    Ok(())
}

fn try_main() -> Result<()> {
    let debuggable = env!("TARGET_BUILD_VARIANT") != "user";
    let log_level = if debuggable { log::Level::Trace } else { log::Level::Info };
    android_logger::init_once(
        android_logger::Config::default().with_tag("authfs_service").with_min_level(log_level),
    );

    clean_up_working_directory()?;

    ProcessState::start_thread_pool();

    let service = AuthFsService::new_binder(debuggable).as_binder();
    add_service(SERVICE_NAME, service)
        .with_context(|| format!("Failed to register service {}", SERVICE_NAME))?;
    debug!("{} is running", SERVICE_NAME);

    ProcessState::join_thread_pool();
    bail!("Unexpected exit after join_thread_pool")
}

fn main() {
    if let Err(e) = try_main() {
        error!("failed with {:?}", e);
        std::process::exit(1);
    }
}
