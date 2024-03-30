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

//! Command to run a VM.

use crate::create_partition::command_create_partition;
use crate::sync::AtomicFlag;
use android_system_virtualizationservice::aidl::android::system::virtualizationservice::{
    DeathReason::DeathReason, IVirtualMachine::IVirtualMachine,
    IVirtualMachineCallback::BnVirtualMachineCallback,
    IVirtualMachineCallback::IVirtualMachineCallback,
    IVirtualizationService::IVirtualizationService, PartitionType::PartitionType,
    VirtualMachineAppConfig::DebugLevel::DebugLevel,
    VirtualMachineAppConfig::VirtualMachineAppConfig, VirtualMachineConfig::VirtualMachineConfig,
    VirtualMachineState::VirtualMachineState,
};
use android_system_virtualizationservice::binder::{
    BinderFeatures, DeathRecipient, IBinder, ParcelFileDescriptor, Strong,
};
use android_system_virtualizationservice::binder::{Interface, Result as BinderResult};
use anyhow::{bail, Context, Error};
use microdroid_payload_config::VmPayloadConfig;
use std::fs::File;
use std::io::{self, BufRead, BufReader};
use std::os::unix::io::{AsRawFd, FromRawFd};
use std::path::{Path, PathBuf};
use vmconfig::{open_parcel_file, VmConfig};
use zip::ZipArchive;

/// Run a VM from the given APK, idsig, and config.
#[allow(clippy::too_many_arguments)]
pub fn command_run_app(
    service: Strong<dyn IVirtualizationService>,
    apk: &Path,
    idsig: &Path,
    instance: &Path,
    config_path: &str,
    daemonize: bool,
    console_path: Option<&Path>,
    log_path: Option<&Path>,
    debug_level: DebugLevel,
    protected: bool,
    mem: Option<u32>,
    cpus: Option<u32>,
    cpu_affinity: Option<String>,
    task_profiles: Vec<String>,
    extra_idsigs: &[PathBuf],
) -> Result<(), Error> {
    let extra_apks = parse_extra_apk_list(apk, config_path)?;
    if extra_apks.len() != extra_idsigs.len() {
        bail!(
            "Found {} extra apks, but there are {} extra idsigs",
            extra_apks.len(),
            extra_idsigs.len()
        )
    }

    for i in 0..extra_apks.len() {
        let extra_apk_fd = ParcelFileDescriptor::new(File::open(&extra_apks[i])?);
        let extra_idsig_fd = ParcelFileDescriptor::new(File::create(&extra_idsigs[i])?);
        service.createOrUpdateIdsigFile(&extra_apk_fd, &extra_idsig_fd)?;
    }

    let apk_file = File::open(apk).context("Failed to open APK file")?;
    let idsig_file = File::create(idsig).context("Failed to create idsig file")?;

    let apk_fd = ParcelFileDescriptor::new(apk_file);
    let idsig_fd = ParcelFileDescriptor::new(idsig_file);
    service.createOrUpdateIdsigFile(&apk_fd, &idsig_fd)?;

    let idsig_file = File::open(idsig).context("Failed to open idsig file")?;
    let idsig_fd = ParcelFileDescriptor::new(idsig_file);

    if !instance.exists() {
        const INSTANCE_FILE_SIZE: u64 = 10 * 1024 * 1024;
        command_create_partition(
            service.clone(),
            instance,
            INSTANCE_FILE_SIZE,
            PartitionType::ANDROID_VM_INSTANCE,
        )?;
    }

    let extra_idsig_files: Result<Vec<File>, _> = extra_idsigs.iter().map(File::open).collect();
    let extra_idsig_fds = extra_idsig_files?.into_iter().map(ParcelFileDescriptor::new).collect();

    let config = VirtualMachineConfig::AppConfig(VirtualMachineAppConfig {
        apk: apk_fd.into(),
        idsig: idsig_fd.into(),
        extraIdsigs: extra_idsig_fds,
        instanceImage: open_parcel_file(instance, true /* writable */)?.into(),
        configPath: config_path.to_owned(),
        debugLevel: debug_level,
        protectedVm: protected,
        memoryMib: mem.unwrap_or(0) as i32, // 0 means use the VM default
        numCpus: cpus.unwrap_or(1) as i32,
        cpuAffinity: cpu_affinity,
        taskProfiles: task_profiles,
    });
    run(
        service,
        &config,
        &format!("{:?}!{:?}", apk, config_path),
        daemonize,
        console_path,
        log_path,
    )
}

/// Run a VM from the given configuration file.
#[allow(clippy::too_many_arguments)]
pub fn command_run(
    service: Strong<dyn IVirtualizationService>,
    config_path: &Path,
    daemonize: bool,
    console_path: Option<&Path>,
    log_path: Option<&Path>,
    mem: Option<u32>,
    cpus: Option<u32>,
    cpu_affinity: Option<String>,
    task_profiles: Vec<String>,
) -> Result<(), Error> {
    let config_file = File::open(config_path).context("Failed to open config file")?;
    let mut config =
        VmConfig::load(&config_file).context("Failed to parse config file")?.to_parcelable()?;
    if let Some(mem) = mem {
        config.memoryMib = mem as i32;
    }
    if let Some(cpus) = cpus {
        config.numCpus = cpus as i32;
    }
    config.cpuAffinity = cpu_affinity;
    config.taskProfiles = task_profiles;
    run(
        service,
        &VirtualMachineConfig::RawConfig(config),
        &format!("{:?}", config_path),
        daemonize,
        console_path,
        log_path,
    )
}

fn state_to_str(vm_state: VirtualMachineState) -> &'static str {
    match vm_state {
        VirtualMachineState::NOT_STARTED => "NOT_STARTED",
        VirtualMachineState::STARTING => "STARTING",
        VirtualMachineState::STARTED => "STARTED",
        VirtualMachineState::READY => "READY",
        VirtualMachineState::FINISHED => "FINISHED",
        VirtualMachineState::DEAD => "DEAD",
        _ => "(invalid state)",
    }
}

fn run(
    service: Strong<dyn IVirtualizationService>,
    config: &VirtualMachineConfig,
    config_path: &str,
    daemonize: bool,
    console_path: Option<&Path>,
    log_path: Option<&Path>,
) -> Result<(), Error> {
    let console = if let Some(console_path) = console_path {
        Some(ParcelFileDescriptor::new(
            File::create(console_path)
                .with_context(|| format!("Failed to open console file {:?}", console_path))?,
        ))
    } else if daemonize {
        None
    } else {
        Some(ParcelFileDescriptor::new(duplicate_stdout()?))
    };
    let log = if let Some(log_path) = log_path {
        Some(ParcelFileDescriptor::new(
            File::create(log_path)
                .with_context(|| format!("Failed to open log file {:?}", log_path))?,
        ))
    } else if daemonize {
        None
    } else {
        Some(ParcelFileDescriptor::new(duplicate_stdout()?))
    };

    let vm =
        service.createVm(config, console.as_ref(), log.as_ref()).context("Failed to create VM")?;

    let cid = vm.getCid().context("Failed to get CID")?;
    println!(
        "Created VM from {} with CID {}, state is {}.",
        config_path,
        cid,
        state_to_str(vm.getState()?)
    );
    vm.start()?;
    println!("Started VM, state now {}.", state_to_str(vm.getState()?));

    if daemonize {
        // Pass the VM reference back to VirtualizationService and have it hold it in the
        // background.
        service.debugHoldVmRef(&vm).context("Failed to pass VM to VirtualizationService")
    } else {
        // Wait until the VM or VirtualizationService dies. If we just returned immediately then the
        // IVirtualMachine Binder object would be dropped and the VM would be killed.
        wait_for_vm(vm)
    }
}

/// Wait until the given VM or the VirtualizationService itself dies.
fn wait_for_vm(vm: Strong<dyn IVirtualMachine>) -> Result<(), Error> {
    let dead = AtomicFlag::default();
    let callback = BnVirtualMachineCallback::new_binder(
        VirtualMachineCallback { dead: dead.clone() },
        BinderFeatures::default(),
    );
    vm.registerCallback(&callback)?;
    let death_recipient = wait_for_death(&mut vm.as_binder(), dead.clone())?;
    dead.wait();
    // Ensure that death_recipient isn't dropped before we wait on the flag, as it is removed
    // from the Binder when it's dropped.
    drop(death_recipient);
    Ok(())
}

/// Raise the given flag when the given Binder object dies.
///
/// If the returned DeathRecipient is dropped then this will no longer do anything.
fn wait_for_death(binder: &mut impl IBinder, dead: AtomicFlag) -> Result<DeathRecipient, Error> {
    let mut death_recipient = DeathRecipient::new(move || {
        eprintln!("VirtualizationService unexpectedly died");
        dead.raise();
    });
    binder.link_to_death(&mut death_recipient)?;
    Ok(death_recipient)
}

fn parse_extra_apk_list(apk: &Path, config_path: &str) -> Result<Vec<String>, Error> {
    let mut archive = ZipArchive::new(File::open(apk)?)?;
    let config_file = archive.by_name(config_path)?;
    let config: VmPayloadConfig = serde_json::from_reader(config_file)?;
    Ok(config.extra_apks.into_iter().map(|x| x.path).collect())
}

#[derive(Debug)]
struct VirtualMachineCallback {
    dead: AtomicFlag,
}

impl Interface for VirtualMachineCallback {}

impl IVirtualMachineCallback for VirtualMachineCallback {
    fn onPayloadStarted(
        &self,
        _cid: i32,
        stream: Option<&ParcelFileDescriptor>,
    ) -> BinderResult<()> {
        // Show the output of the payload
        if let Some(stream) = stream {
            let mut reader = BufReader::new(stream.as_ref());
            loop {
                let mut s = String::new();
                match reader.read_line(&mut s) {
                    Ok(0) => break,
                    Ok(_) => print!("{}", s),
                    Err(e) => eprintln!("error reading from virtual machine: {}", e),
                };
            }
        }
        Ok(())
    }

    fn onPayloadReady(&self, _cid: i32) -> BinderResult<()> {
        eprintln!("payload is ready");
        Ok(())
    }

    fn onPayloadFinished(&self, _cid: i32, exit_code: i32) -> BinderResult<()> {
        eprintln!("payload finished with exit code {}", exit_code);
        Ok(())
    }

    fn onError(&self, _cid: i32, error_code: i32, message: &str) -> BinderResult<()> {
        eprintln!("VM encountered an error: code={}, message={}", error_code, message);
        Ok(())
    }

    fn onDied(&self, _cid: i32, reason: DeathReason) -> BinderResult<()> {
        self.dead.raise();

        match reason {
            DeathReason::INFRASTRUCTURE_ERROR => println!("Error waiting for VM to finish."),
            DeathReason::KILLED => println!("VM was killed."),
            DeathReason::UNKNOWN => println!("VM died for an unknown reason."),
            DeathReason::SHUTDOWN => println!("VM shutdown cleanly."),
            DeathReason::ERROR => println!("Error starting VM."),
            DeathReason::REBOOT => println!("VM tried to reboot, possibly due to a kernel panic."),
            DeathReason::CRASH => println!("VM crashed."),
            _ => println!("VM died for an unrecognised reason."),
        }
        Ok(())
    }
}

/// Safely duplicate the standard output file descriptor.
fn duplicate_stdout() -> io::Result<File> {
    let stdout_fd = io::stdout().as_raw_fd();
    // Safe because this just duplicates a file descriptor which we know to be valid, and we check
    // for an error.
    let dup_fd = unsafe { libc::dup(stdout_fd) };
    if dup_fd < 0 {
        Err(io::Error::last_os_error())
    } else {
        // Safe because we have just duplicated the file descriptor so we own it, and `from_raw_fd`
        // takes ownership of it.
        Ok(unsafe { File::from_raw_fd(dup_fd) })
    }
}
