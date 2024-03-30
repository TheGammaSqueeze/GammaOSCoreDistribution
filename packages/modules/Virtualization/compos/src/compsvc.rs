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

//! compsvc is a service to run compilation tasks in a PVM upon request. It is able to set up
//! file descriptors backed by authfs (via authfs_service) and pass the file descriptors to the
//! actual compiler.

use anyhow::{bail, Context, Result};
use binder_common::new_binder_exception;
use log::error;
use rustutils::system_properties;
use std::default::Default;
use std::fs::read_dir;
use std::iter::zip;
use std::path::{Path, PathBuf};
use std::sync::RwLock;

use crate::artifact_signer::ArtifactSigner;
use crate::compilation::{odrefresh, OdrefreshContext};
use crate::compos_key;
use compos_aidl_interface::aidl::com::android::compos::ICompOsService::{
    BnCompOsService, CompilationMode::CompilationMode, ICompOsService,
};
use compos_aidl_interface::binder::{
    BinderFeatures, ExceptionCode, Interface, Result as BinderResult, Strong,
};
use compos_common::binder::to_binder_result;
use compos_common::odrefresh::{is_system_property_interesting, ODREFRESH_PATH};

const AUTHFS_SERVICE_NAME: &str = "authfs_service";

/// Constructs a binder object that implements ICompOsService.
pub fn new_binder() -> Result<Strong<dyn ICompOsService>> {
    let service = CompOsService {
        odrefresh_path: PathBuf::from(ODREFRESH_PATH),
        initialized: RwLock::new(None),
    };
    Ok(BnCompOsService::new_binder(service, BinderFeatures::default()))
}

struct CompOsService {
    odrefresh_path: PathBuf,

    /// A locked protected tri-state.
    ///  * None: uninitialized
    ///  * Some(true): initialized successfully
    ///  * Some(false): failed to initialize
    initialized: RwLock<Option<bool>>,
}

impl Interface for CompOsService {}

impl ICompOsService for CompOsService {
    fn initializeSystemProperties(&self, names: &[String], values: &[String]) -> BinderResult<()> {
        let mut initialized = self.initialized.write().unwrap();
        if initialized.is_some() {
            return Err(new_binder_exception(
                ExceptionCode::ILLEGAL_STATE,
                format!("Already initialized: {:?}", initialized),
            ));
        }
        *initialized = Some(false);

        if names.len() != values.len() {
            return Err(new_binder_exception(
                ExceptionCode::ILLEGAL_ARGUMENT,
                format!(
                    "Received inconsistent number of keys ({}) and values ({})",
                    names.len(),
                    values.len()
                ),
            ));
        }
        for (name, value) in zip(names, values) {
            if !is_system_property_interesting(name) {
                return Err(new_binder_exception(
                    ExceptionCode::ILLEGAL_ARGUMENT,
                    format!("Received invalid system property {}", &name),
                ));
            }
            let result = system_properties::write(name, value);
            if result.is_err() {
                error!("Failed to setprop {}", &name);
                return to_binder_result(result);
            }
        }
        *initialized = Some(true);
        Ok(())
    }

    fn odrefresh(
        &self,
        compilation_mode: CompilationMode,
        system_dir_fd: i32,
        output_dir_fd: i32,
        staging_dir_fd: i32,
        target_dir_name: &str,
        zygote_arch: &str,
        system_server_compiler_filter: &str,
    ) -> BinderResult<i8> {
        let initialized = *self.initialized.read().unwrap();
        if !initialized.unwrap_or(false) {
            return Err(new_binder_exception(
                ExceptionCode::ILLEGAL_STATE,
                "Service has not been initialized",
            ));
        }

        let context = to_binder_result(OdrefreshContext::new(
            compilation_mode,
            system_dir_fd,
            output_dir_fd,
            staging_dir_fd,
            target_dir_name,
            zygote_arch,
            system_server_compiler_filter,
        ))?;

        let authfs_service = authfs_aidl_interface::binder::get_interface(AUTHFS_SERVICE_NAME)?;
        let exit_code = to_binder_result(
            odrefresh(&self.odrefresh_path, context, authfs_service, |output_dir| {
                // authfs only shows us the files we created, so it's ok to just sign everything
                // under the output directory.
                let mut artifact_signer = ArtifactSigner::new(&output_dir);
                add_artifacts(&output_dir, &mut artifact_signer)?;

                artifact_signer.write_info_and_signature(&output_dir.join("compos.info"))
            })
            .context("odrefresh failed"),
        )?;
        Ok(exit_code as i8)
    }

    fn getPublicKey(&self) -> BinderResult<Vec<u8>> {
        to_binder_result(compos_key::get_public_key())
    }
}

fn add_artifacts(target_dir: &Path, artifact_signer: &mut ArtifactSigner) -> Result<()> {
    for entry in
        read_dir(&target_dir).with_context(|| format!("Traversing {}", target_dir.display()))?
    {
        let entry = entry?;
        let file_type = entry.file_type()?;
        if file_type.is_dir() {
            add_artifacts(&entry.path(), artifact_signer)?;
        } else if file_type.is_file() {
            artifact_signer.add_artifact(&entry.path())?;
        } else {
            // authfs shouldn't create anything else, but just in case
            bail!("Unexpected file type in artifacts: {:?}", entry);
        }
    }
    Ok(())
}
