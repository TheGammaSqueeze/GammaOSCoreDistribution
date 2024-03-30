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

//! Android VM control tool.

mod create_idsig;
mod create_partition;
mod run;
mod sync;

use android_system_virtualizationservice::aidl::android::system::virtualizationservice::{
    IVirtualizationService::IVirtualizationService, PartitionType::PartitionType,
    VirtualMachineAppConfig::DebugLevel::DebugLevel,
};
use android_system_virtualizationservice::binder::{wait_for_interface, ProcessState, Strong};
use anyhow::{Context, Error};
use create_idsig::command_create_idsig;
use create_partition::command_create_partition;
use run::{command_run, command_run_app};
use rustutils::system_properties;
use std::path::{Path, PathBuf};
use structopt::clap::AppSettings;
use structopt::StructOpt;

const VIRTUALIZATION_SERVICE_BINDER_SERVICE_IDENTIFIER: &str =
    "android.system.virtualizationservice";

#[derive(Debug)]
struct Idsigs(Vec<PathBuf>);

#[derive(StructOpt)]
#[structopt(no_version, global_settings = &[AppSettings::DisableVersion])]
enum Opt {
    /// Run a virtual machine with a config in APK
    RunApp {
        /// Path to VM Payload APK
        #[structopt(parse(from_os_str))]
        apk: PathBuf,

        /// Path to idsig of the APK
        #[structopt(parse(from_os_str))]
        idsig: PathBuf,

        /// Path to the instance image. Created if not exists.
        #[structopt(parse(from_os_str))]
        instance: PathBuf,

        /// Path to VM config JSON within APK (e.g. assets/vm_config.json)
        config_path: String,

        /// Detach VM from the terminal and run in the background
        #[structopt(short, long)]
        daemonize: bool,

        /// Path to file for VM console output.
        #[structopt(long)]
        console: Option<PathBuf>,

        /// Path to file for VM log output.
        #[structopt(long)]
        log: Option<PathBuf>,

        /// Debug level of the VM. Supported values: "none" (default), "app_only", and "full".
        #[structopt(long, default_value = "none", parse(try_from_str=parse_debug_level))]
        debug: DebugLevel,

        /// Run VM in protected mode.
        #[structopt(short, long)]
        protected: bool,

        /// Memory size (in MiB) of the VM. If unspecified, defaults to the value of `memory_mib`
        /// in the VM config file.
        #[structopt(short, long)]
        mem: Option<u32>,

        /// Number of vCPUs in the VM. If unspecified, defaults to 1.
        #[structopt(long)]
        cpus: Option<u32>,

        /// Host CPUs where vCPUs are run on. If unspecified, vCPU runs on any host CPU.
        #[structopt(long)]
        cpu_affinity: Option<String>,

        /// Comma separated list of task profile names to apply to the VM
        #[structopt(long)]
        task_profiles: Vec<String>,

        /// Paths to extra idsig files.
        #[structopt(long = "extra-idsig")]
        extra_idsigs: Vec<PathBuf>,
    },
    /// Run a virtual machine
    Run {
        /// Path to VM config JSON
        #[structopt(parse(from_os_str))]
        config: PathBuf,

        /// Detach VM from the terminal and run in the background
        #[structopt(short, long)]
        daemonize: bool,

        /// Number of vCPUs in the VM. If unspecified, defaults to 1.
        #[structopt(long)]
        cpus: Option<u32>,

        /// Host CPUs where vCPUs are run on. If unspecified, vCPU runs on any host CPU. The format
        /// can be either a comma-separated list of CPUs or CPU ranges to run vCPUs on (e.g.
        /// "0,1-3,5" to choose host CPUs 0, 1, 2, 3, and 5, or a colon-separated list of
        /// assignments of vCPU-to-host-CPU assignments e.g. "0=0:1=1:2=2" to map vCPU 0 to host
        /// CPU 0 and so on.
        #[structopt(long)]
        cpu_affinity: Option<String>,

        /// Comma separated list of task profile names to apply to the VM
        #[structopt(long)]
        task_profiles: Vec<String>,

        /// Path to file for VM console output.
        #[structopt(long)]
        console: Option<PathBuf>,

        /// Path to file for VM log output.
        #[structopt(long)]
        log: Option<PathBuf>,
    },
    /// Stop a virtual machine running in the background
    Stop {
        /// CID of the virtual machine
        cid: u32,
    },
    /// List running virtual machines
    List,
    /// Print information about virtual machine support
    Info,
    /// Create a new empty partition to be used as a writable partition for a VM
    CreatePartition {
        /// Path at which to create the image file
        #[structopt(parse(from_os_str))]
        path: PathBuf,

        /// The desired size of the partition, in bytes.
        size: u64,

        /// Type of the partition
        #[structopt(short="t", long="type", default_value="raw", parse(try_from_str=parse_partition_type))]
        partition_type: PartitionType,
    },
    /// Creates or update the idsig file by digesting the input APK file.
    CreateIdsig {
        /// Path to VM Payload APK
        #[structopt(parse(from_os_str))]
        apk: PathBuf,
        /// Path to idsig of the APK
        #[structopt(parse(from_os_str))]
        path: PathBuf,
    },
}

fn parse_debug_level(s: &str) -> Result<DebugLevel, String> {
    match s {
        "none" => Ok(DebugLevel::NONE),
        "app_only" => Ok(DebugLevel::APP_ONLY),
        "full" => Ok(DebugLevel::FULL),
        _ => Err(format!("Invalid debug level {}", s)),
    }
}

fn parse_partition_type(s: &str) -> Result<PartitionType, String> {
    match s {
        "raw" => Ok(PartitionType::RAW),
        "instance" => Ok(PartitionType::ANDROID_VM_INSTANCE),
        _ => Err(format!("Invalid partition type {}", s)),
    }
}

fn main() -> Result<(), Error> {
    env_logger::init();
    let opt = Opt::from_args();

    // We need to start the thread pool for Binder to work properly, especially link_to_death.
    ProcessState::start_thread_pool();

    let service = wait_for_interface(VIRTUALIZATION_SERVICE_BINDER_SERVICE_IDENTIFIER)
        .context("Failed to find VirtualizationService")?;

    match opt {
        Opt::RunApp {
            apk,
            idsig,
            instance,
            config_path,
            daemonize,
            console,
            log,
            debug,
            protected,
            mem,
            cpus,
            cpu_affinity,
            task_profiles,
            extra_idsigs,
        } => command_run_app(
            service,
            &apk,
            &idsig,
            &instance,
            &config_path,
            daemonize,
            console.as_deref(),
            log.as_deref(),
            debug,
            protected,
            mem,
            cpus,
            cpu_affinity,
            task_profiles,
            &extra_idsigs,
        ),
        Opt::Run { config, daemonize, cpus, cpu_affinity, task_profiles, console, log } => {
            command_run(
                service,
                &config,
                daemonize,
                console.as_deref(),
                log.as_deref(),
                /* mem */ None,
                cpus,
                cpu_affinity,
                task_profiles,
            )
        }
        Opt::Stop { cid } => command_stop(service, cid),
        Opt::List => command_list(service),
        Opt::Info => command_info(),
        Opt::CreatePartition { path, size, partition_type } => {
            command_create_partition(service, &path, size, partition_type)
        }
        Opt::CreateIdsig { apk, path } => command_create_idsig(service, &apk, &path),
    }
}

/// Retrieve reference to a previously daemonized VM and stop it.
fn command_stop(service: Strong<dyn IVirtualizationService>, cid: u32) -> Result<(), Error> {
    service
        .debugDropVmRef(cid as i32)
        .context("Failed to get VM from VirtualizationService")?
        .context("CID does not correspond to a running background VM")?;
    Ok(())
}

/// List the VMs currently running.
fn command_list(service: Strong<dyn IVirtualizationService>) -> Result<(), Error> {
    let vms = service.debugListVms().context("Failed to get list of VMs")?;
    println!("Running VMs: {:#?}", vms);
    Ok(())
}

/// Print information about supported VM types.
fn command_info() -> Result<(), Error> {
    let unprotected_vm_supported =
        system_properties::read_bool("ro.boot.hypervisor.vm.supported", false)?;
    let protected_vm_supported =
        system_properties::read_bool("ro.boot.hypervisor.protected_vm.supported", false)?;
    match (unprotected_vm_supported, protected_vm_supported) {
        (false, false) => println!("VMs are not supported."),
        (false, true) => println!("Only protected VMs are supported."),
        (true, false) => println!("Only unprotected VMs are supported."),
        (true, true) => println!("Both protected and unprotected VMs are supported."),
    }

    if let Some(version) = system_properties::read("ro.boot.hypervisor.version")? {
        println!("Hypervisor version: {}", version);
    } else {
        println!("Hypervisor version not set.");
    }

    if Path::new("/dev/kvm").exists() {
        println!("/dev/kvm exists.");
    } else {
        println!("/dev/kvm does not exist.");
    }

    Ok(())
}
