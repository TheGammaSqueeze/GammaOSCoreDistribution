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

//! Functions for running instances of `crosvm`.

use crate::aidl::VirtualMachineCallbacks;
use crate::Cid;
use anyhow::{bail, Error};
use command_fds::CommandFdExt;
use log::{debug, error, info};
use semver::{Version, VersionReq};
use nix::{fcntl::OFlag, unistd::pipe2};
use shared_child::SharedChild;
use std::fs::{remove_dir_all, File};
use std::io::{self, Read};
use std::mem;
use std::num::NonZeroU32;
use std::os::unix::io::{AsRawFd, RawFd, FromRawFd};
use std::path::PathBuf;
use std::process::{Command, ExitStatus};
use std::sync::{Arc, Mutex};
use std::thread;
use vsock::VsockStream;
use android_system_virtualizationservice::aidl::android::system::virtualizationservice::DeathReason::DeathReason;
use android_system_virtualmachineservice::binder::Strong;
use android_system_virtualmachineservice::aidl::android::system::virtualmachineservice::IVirtualMachineService::IVirtualMachineService;

const CROSVM_PATH: &str = "/apex/com.android.virt/bin/crosvm";

/// Version of the platform that crosvm currently implements. The format follows SemVer. This
/// should be updated when there is a platform change in the crosvm side. Having this value here is
/// fine because virtualizationservice and crosvm are supposed to be updated together in the virt
/// APEX.
const CROSVM_PLATFORM_VERSION: &str = "1.0.0";

/// The exit status which crosvm returns when it has an error starting a VM.
const CROSVM_ERROR_STATUS: i32 = 1;
/// The exit status which crosvm returns when a VM requests a reboot.
const CROSVM_REBOOT_STATUS: i32 = 32;
/// The exit status which crosvm returns when it crashes due to an error.
const CROSVM_CRASH_STATUS: i32 = 33;

/// Configuration for a VM to run with crosvm.
#[derive(Debug)]
pub struct CrosvmConfig {
    pub cid: Cid,
    pub bootloader: Option<File>,
    pub kernel: Option<File>,
    pub initrd: Option<File>,
    pub disks: Vec<DiskFile>,
    pub params: Option<String>,
    pub protected: bool,
    pub memory_mib: Option<NonZeroU32>,
    pub cpus: Option<NonZeroU32>,
    pub cpu_affinity: Option<String>,
    pub task_profiles: Vec<String>,
    pub console_fd: Option<File>,
    pub log_fd: Option<File>,
    pub indirect_files: Vec<File>,
    pub platform_version: VersionReq,
}

/// A disk image to pass to crosvm for a VM.
#[derive(Debug)]
pub struct DiskFile {
    pub image: File,
    pub writable: bool,
}

/// The lifecycle state which the payload in the VM has reported itself to be in.
///
/// Note that the order of enum variants is significant; only forward transitions are allowed by
/// [`VmInstance::update_payload_state`].
#[derive(Copy, Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum PayloadState {
    Starting,
    Started,
    Ready,
    Finished,
}

/// The current state of the VM itself.
#[derive(Debug)]
pub enum VmState {
    /// The VM has not yet tried to start.
    NotStarted {
        ///The configuration needed to start the VM, if it has not yet been started.
        config: CrosvmConfig,
    },
    /// The VM has been started.
    Running {
        /// The crosvm child process.
        child: Arc<SharedChild>,
    },
    /// The VM died or was killed.
    Dead,
    /// The VM failed to start.
    Failed,
}

impl VmState {
    /// Tries to start the VM, if it is in the `NotStarted` state.
    ///
    /// Returns an error if the VM is in the wrong state, or fails to start.
    fn start(&mut self, instance: Arc<VmInstance>) -> Result<(), Error> {
        let state = mem::replace(self, VmState::Failed);
        if let VmState::NotStarted { config } = state {
            let (failure_pipe_read, failure_pipe_write) = create_pipe()?;

            // If this fails and returns an error, `self` will be left in the `Failed` state.
            let child = Arc::new(run_vm(config, failure_pipe_write)?);

            let child_clone = child.clone();
            thread::spawn(move || {
                instance.monitor(child_clone, failure_pipe_read);
            });

            // If it started correctly, update the state.
            *self = VmState::Running { child };
            Ok(())
        } else {
            *self = state;
            bail!("VM already started or failed")
        }
    }
}

/// Information about a particular instance of a VM which may be running.
#[derive(Debug)]
pub struct VmInstance {
    /// The current state of the VM.
    pub vm_state: Mutex<VmState>,
    /// The CID assigned to the VM for vsock communication.
    pub cid: Cid,
    /// Whether the VM is a protected VM.
    pub protected: bool,
    /// Directory of temporary files used by the VM while it is running.
    pub temporary_directory: PathBuf,
    /// The UID of the process which requested the VM.
    pub requester_uid: u32,
    /// The SID of the process which requested the VM.
    pub requester_sid: String,
    /// The PID of the process which requested the VM. Note that this process may no longer exist
    /// and the PID may have been reused for a different process, so this should not be trusted.
    pub requester_debug_pid: i32,
    /// Callbacks to clients of the VM.
    pub callbacks: VirtualMachineCallbacks,
    /// Input/output stream of the payload run in the VM.
    pub stream: Mutex<Option<VsockStream>>,
    /// VirtualMachineService binder object for the VM.
    pub vm_service: Mutex<Option<Strong<dyn IVirtualMachineService>>>,
    /// The latest lifecycle state which the payload reported itself to be in.
    payload_state: Mutex<PayloadState>,
}

impl VmInstance {
    /// Validates the given config and creates a new `VmInstance` but doesn't start running it.
    pub fn new(
        config: CrosvmConfig,
        temporary_directory: PathBuf,
        requester_uid: u32,
        requester_sid: String,
        requester_debug_pid: i32,
    ) -> Result<VmInstance, Error> {
        validate_config(&config)?;
        let cid = config.cid;
        let protected = config.protected;
        Ok(VmInstance {
            vm_state: Mutex::new(VmState::NotStarted { config }),
            cid,
            protected,
            temporary_directory,
            requester_uid,
            requester_sid,
            requester_debug_pid,
            callbacks: Default::default(),
            stream: Mutex::new(None),
            vm_service: Mutex::new(None),
            payload_state: Mutex::new(PayloadState::Starting),
        })
    }

    /// Starts an instance of `crosvm` to manage the VM. The `crosvm` instance will be killed when
    /// the `VmInstance` is dropped.
    pub fn start(self: &Arc<Self>) -> Result<(), Error> {
        self.vm_state.lock().unwrap().start(self.clone())
    }

    /// Waits for the crosvm child process to finish, then marks the VM as no longer running and
    /// calls any callbacks.
    ///
    /// This takes a separate reference to the `SharedChild` rather than using the one in
    /// `self.vm_state` to avoid holding the lock on `vm_state` while it is running.
    fn monitor(&self, child: Arc<SharedChild>, mut failure_pipe_read: File) {
        let result = child.wait();
        match &result {
            Err(e) => error!("Error waiting for crosvm({}) instance to die: {}", child.id(), e),
            Ok(status) => info!("crosvm({}) exited with status {}", child.id(), status),
        }

        let mut vm_state = self.vm_state.lock().unwrap();
        *vm_state = VmState::Dead;
        // Ensure that the mutex is released before calling the callbacks.
        drop(vm_state);

        let mut failure_string = String::new();
        let failure_read_result = failure_pipe_read.read_to_string(&mut failure_string);
        if let Err(e) = &failure_read_result {
            error!("Error reading VM failure reason from pipe: {}", e);
        }
        if !failure_string.is_empty() {
            info!("VM returned failure reason '{}'", failure_string);
        }

        self.callbacks.callback_on_died(self.cid, death_reason(&result, &failure_string));

        // Delete temporary files.
        if let Err(e) = remove_dir_all(&self.temporary_directory) {
            error!("Error removing temporary directory {:?}: {}", self.temporary_directory, e);
        }
    }

    /// Returns the last reported state of the VM payload.
    pub fn payload_state(&self) -> PayloadState {
        *self.payload_state.lock().unwrap()
    }

    /// Updates the payload state to the given value, if it is a valid state transition.
    pub fn update_payload_state(&self, new_state: PayloadState) -> Result<(), Error> {
        let mut state_locked = self.payload_state.lock().unwrap();
        // Only allow forward transitions, e.g. from starting to started or finished, not back in
        // the other direction.
        if new_state > *state_locked {
            *state_locked = new_state;
            Ok(())
        } else {
            bail!("Invalid payload state transition from {:?} to {:?}", *state_locked, new_state)
        }
    }

    /// Kills the crosvm instance, if it is running.
    pub fn kill(&self) {
        let vm_state = &*self.vm_state.lock().unwrap();
        if let VmState::Running { child } = vm_state {
            let id = child.id();
            debug!("Killing crosvm({})", id);
            // TODO: Talk to crosvm to shutdown cleanly.
            if let Err(e) = child.kill() {
                error!("Error killing crosvm({}) instance: {}", id, e);
            }
        }
    }
}

fn death_reason(result: &Result<ExitStatus, io::Error>, failure_reason: &str) -> DeathReason {
    if let Ok(status) = result {
        match failure_reason {
            "PVM_FIRMWARE_PUBLIC_KEY_MISMATCH" => {
                return DeathReason::PVM_FIRMWARE_PUBLIC_KEY_MISMATCH
            }
            "PVM_FIRMWARE_INSTANCE_IMAGE_CHANGED" => {
                return DeathReason::PVM_FIRMWARE_INSTANCE_IMAGE_CHANGED
            }
            "BOOTLOADER_PUBLIC_KEY_MISMATCH" => return DeathReason::BOOTLOADER_PUBLIC_KEY_MISMATCH,
            "BOOTLOADER_INSTANCE_IMAGE_CHANGED" => {
                return DeathReason::BOOTLOADER_INSTANCE_IMAGE_CHANGED
            }
            _ => {}
        }
        match status.code() {
            None => DeathReason::KILLED,
            Some(0) => DeathReason::SHUTDOWN,
            Some(CROSVM_ERROR_STATUS) => DeathReason::ERROR,
            Some(CROSVM_REBOOT_STATUS) => DeathReason::REBOOT,
            Some(CROSVM_CRASH_STATUS) => DeathReason::CRASH,
            Some(_) => DeathReason::UNKNOWN,
        }
    } else {
        DeathReason::INFRASTRUCTURE_ERROR
    }
}

/// Starts an instance of `crosvm` to manage a new VM.
fn run_vm(config: CrosvmConfig, failure_pipe_write: File) -> Result<SharedChild, Error> {
    validate_config(&config)?;

    let mut command = Command::new(CROSVM_PATH);
    // TODO(qwandor): Remove --disable-sandbox.
    command
        .arg("--extended-status")
        .arg("run")
        .arg("--disable-sandbox")
        .arg("--cid")
        .arg(config.cid.to_string());

    if config.protected {
        command.arg("--protected-vm");

        // 3 virtio-console devices + vsock = 4.
        let virtio_pci_device_count = 4 + config.disks.len();
        // crosvm virtio queue has 256 entries, so 2 MiB per device (2 pages per entry) should be
        // enough.
        let swiotlb_size_mib = 2 * virtio_pci_device_count;
        command.arg("--swiotlb").arg(swiotlb_size_mib.to_string());
    }

    if let Some(memory_mib) = config.memory_mib {
        command.arg("--mem").arg(memory_mib.to_string());
    }

    if let Some(cpus) = config.cpus {
        command.arg("--cpus").arg(cpus.to_string());
    }

    if let Some(cpu_affinity) = config.cpu_affinity {
        command.arg("--cpu-affinity").arg(cpu_affinity);
    }

    if !config.task_profiles.is_empty() {
        command.arg("--task-profiles").arg(config.task_profiles.join(","));
    }

    // Keep track of what file descriptors should be mapped to the crosvm process.
    let mut preserved_fds = config.indirect_files.iter().map(|file| file.as_raw_fd()).collect();

    // Setup the serial devices.
    // 1. uart device: used as the output device by bootloaders and as early console by linux
    // 2. uart device: used to report the reason for the VM failing.
    // 3. virtio-console device: used as the console device where kmsg is redirected to
    // 4. virtio-console device: used as the androidboot.console device (not used currently)
    // 5. virtio-console device: used as the logcat output
    //
    // When [console|log]_fd is not specified, the devices are attached to sink, which means what's
    // written there is discarded.
    let console_arg = format_serial_arg(&mut preserved_fds, &config.console_fd);
    let log_arg = format_serial_arg(&mut preserved_fds, &config.log_fd);
    let failure_serial_path = add_preserved_fd(&mut preserved_fds, &failure_pipe_write);

    // Warning: Adding more serial devices requires you to shift the PCI device ID of the boot
    // disks in bootconfig.x86_64. This is because x86 crosvm puts serial devices and the block
    // devices in the same PCI bus and serial devices comes before the block devices. Arm crosvm
    // doesn't have the issue.
    // /dev/ttyS0
    command.arg(format!("--serial={},hardware=serial,num=1", &console_arg));
    // /dev/ttyS1
    command.arg(format!("--serial=type=file,path={},hardware=serial,num=2", &failure_serial_path));
    // /dev/hvc0
    command.arg(format!("--serial={},hardware=virtio-console,num=1", &console_arg));
    // /dev/hvc1 (not used currently)
    command.arg("--serial=type=sink,hardware=virtio-console,num=2");
    // /dev/hvc2
    command.arg(format!("--serial={},hardware=virtio-console,num=3", &log_arg));

    if let Some(bootloader) = &config.bootloader {
        command.arg("--bios").arg(add_preserved_fd(&mut preserved_fds, bootloader));
    }

    if let Some(initrd) = &config.initrd {
        command.arg("--initrd").arg(add_preserved_fd(&mut preserved_fds, initrd));
    }

    if let Some(params) = &config.params {
        command.arg("--params").arg(params);
    }

    for disk in &config.disks {
        command
            .arg(if disk.writable { "--rwdisk" } else { "--disk" })
            .arg(add_preserved_fd(&mut preserved_fds, &disk.image));
    }

    if let Some(kernel) = &config.kernel {
        command.arg(add_preserved_fd(&mut preserved_fds, kernel));
    }

    debug!("Preserving FDs {:?}", preserved_fds);
    command.preserved_fds(preserved_fds);

    info!("Running {:?}", command);
    let result = SharedChild::spawn(&mut command)?;
    debug!("Spawned crosvm({}).", result.id());
    Ok(result)
}

/// Ensure that the configuration has a valid combination of fields set, or return an error if not.
fn validate_config(config: &CrosvmConfig) -> Result<(), Error> {
    if config.bootloader.is_none() && config.kernel.is_none() {
        bail!("VM must have either a bootloader or a kernel image.");
    }
    if config.bootloader.is_some() && (config.kernel.is_some() || config.initrd.is_some()) {
        bail!("Can't have both bootloader and kernel/initrd image.");
    }
    let version = Version::parse(CROSVM_PLATFORM_VERSION).unwrap();
    if !config.platform_version.matches(&version) {
        bail!(
            "Incompatible platform version. The config is compatible with platform version(s) \
              {}, but the actual platform version is {}",
            config.platform_version,
            version
        );
    }

    Ok(())
}

/// Adds the file descriptor for `file` to `preserved_fds`, and returns a string of the form
/// "/proc/self/fd/N" where N is the file descriptor.
fn add_preserved_fd(preserved_fds: &mut Vec<RawFd>, file: &File) -> String {
    let fd = file.as_raw_fd();
    preserved_fds.push(fd);
    format!("/proc/self/fd/{}", fd)
}

/// Adds the file descriptor for `file` (if any) to `preserved_fds`, and returns the appropriate
/// string for a crosvm `--serial` flag. If `file` is none, creates a dummy sink device.
fn format_serial_arg(preserved_fds: &mut Vec<RawFd>, file: &Option<File>) -> String {
    if let Some(file) = file {
        format!("type=file,path={}", add_preserved_fd(preserved_fds, file))
    } else {
        "type=sink".to_string()
    }
}

/// Creates a new pipe with the `O_CLOEXEC` flag set, and returns the read side and write side.
fn create_pipe() -> Result<(File, File), Error> {
    let (raw_read, raw_write) = pipe2(OFlag::O_CLOEXEC)?;
    // SAFETY: We are the sole owners of these fds as they were just created.
    let read_fd = unsafe { File::from_raw_fd(raw_read) };
    let write_fd = unsafe { File::from_raw_fd(raw_write) };
    Ok((read_fd, write_fd))
}
