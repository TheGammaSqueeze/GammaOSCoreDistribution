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

//! A tool to verify a CompOS signature. It starts a CompOS VM as part of this to retrieve the
//!  public key. The tool is intended to be run by odsign during boot.

use android_logger::LogId;
use anyhow::{bail, Context, Result};
use compos_aidl_interface::binder::ProcessState;
use compos_common::compos_client::{VmInstance, VmParameters};
use compos_common::odrefresh::{
    CURRENT_ARTIFACTS_SUBDIR, ODREFRESH_OUTPUT_ROOT_DIR, PENDING_ARTIFACTS_SUBDIR,
    TEST_ARTIFACTS_SUBDIR,
};
use compos_common::{
    COMPOS_DATA_ROOT, CURRENT_INSTANCE_DIR, IDSIG_FILE, IDSIG_MANIFEST_APK_FILE,
    INSTANCE_IMAGE_FILE, TEST_INSTANCE_DIR,
};
use log::error;
use std::fs::File;
use std::io::Read;
use std::panic;
use std::path::Path;

const MAX_FILE_SIZE_BYTES: u64 = 100 * 1024;

fn main() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("compos_verify")
            .with_min_level(log::Level::Info)
            .with_log_id(LogId::System), // Needed to log successfully early in boot
    );

    // Redirect panic messages to logcat.
    panic::set_hook(Box::new(|panic_info| {
        error!("{}", panic_info);
    }));

    if let Err(e) = try_main() {
        error!("{:?}", e);
        std::process::exit(1)
    }
}

fn try_main() -> Result<()> {
    let matches = clap::App::new("compos_verify")
        .arg(
            clap::Arg::with_name("instance")
                .long("instance")
                .takes_value(true)
                .required(true)
                .possible_values(&["current", "pending", "test"]),
        )
        .arg(clap::Arg::with_name("debug").long("debug"))
        .get_matches();

    let debug_mode = matches.is_present("debug");
    let (instance_dir, artifacts_dir) = match matches.value_of("instance").unwrap() {
        "current" => (CURRENT_INSTANCE_DIR, CURRENT_ARTIFACTS_SUBDIR),
        "pending" => (CURRENT_INSTANCE_DIR, PENDING_ARTIFACTS_SUBDIR),
        "test" => (TEST_INSTANCE_DIR, TEST_ARTIFACTS_SUBDIR),
        _ => unreachable!("Unexpected instance name"),
    };

    let instance_dir = Path::new(COMPOS_DATA_ROOT).join(instance_dir);
    let artifacts_dir = Path::new(ODREFRESH_OUTPUT_ROOT_DIR).join(artifacts_dir);

    if !instance_dir.is_dir() {
        bail!("{:?} is not a directory", instance_dir);
    }

    let instance_image = instance_dir.join(INSTANCE_IMAGE_FILE);
    let idsig = instance_dir.join(IDSIG_FILE);
    let idsig_manifest_apk = instance_dir.join(IDSIG_MANIFEST_APK_FILE);

    let instance_image = File::open(instance_image).context("Failed to open instance image")?;

    let info = artifacts_dir.join("compos.info");
    let signature = artifacts_dir.join("compos.info.signature");

    let info = read_small_file(&info).context("Failed to read compos.info")?;
    let signature = read_small_file(&signature).context("Failed to read compos.info signature")?;

    // We need to start the thread pool to be able to receive Binder callbacks
    ProcessState::start_thread_pool();

    let virtualization_service = VmInstance::connect_to_virtualization_service()?;
    let vm_instance = VmInstance::start(
        &*virtualization_service,
        instance_image,
        &idsig,
        &idsig_manifest_apk,
        &VmParameters { debug_mode, never_log: !debug_mode, ..Default::default() },
    )?;
    let service = vm_instance.get_service()?;

    let public_key = service.getPublicKey().context("Getting public key")?;

    if !compos_verify_native::verify(&public_key, &signature, &info) {
        bail!("Signature verification failed");
    }

    Ok(())
}

fn read_small_file(file: &Path) -> Result<Vec<u8>> {
    let mut file = File::open(file)?;
    if file.metadata()?.len() > MAX_FILE_SIZE_BYTES {
        bail!("File is too big");
    }
    let mut data = Vec::new();
    file.read_to_end(&mut data)?;
    Ok(data)
}
