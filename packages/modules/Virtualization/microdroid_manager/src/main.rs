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

//! Microdroid Manager

mod instance;
mod ioutil;
mod payload;

use crate::instance::{ApkData, InstanceDisk, MicrodroidData, RootHash};
use android_hardware_security_dice::aidl::android::hardware::security::dice::{
    Config::Config, InputValues::InputValues, Mode::Mode,
};
use android_security_dice::aidl::android::security::dice::IDiceMaintenance::IDiceMaintenance;
use anyhow::{anyhow, bail, ensure, Context, Error, Result};
use apkverify::{get_public_key_der, verify};
use binder::unstable_api::{new_spibinder, AIBinder};
use binder::{wait_for_interface, FromIBinder, Strong};
use diced_utils::cbor::encode_header;
use glob::glob;
use idsig::V4Signature;
use itertools::sorted;
use log::{error, info};
use microdroid_metadata::{write_metadata, Metadata};
use microdroid_payload_config::{Task, TaskType, VmPayloadConfig};
use payload::{get_apex_data_from_payload, load_metadata, to_metadata};
use rand::Fill;
use ring::digest;
use rustutils::system_properties;
use rustutils::system_properties::PropertyWatcher;
use std::convert::TryInto;
use std::fs::{self, create_dir, File, OpenOptions};
use std::os::unix::io::{FromRawFd, IntoRawFd};
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::str;
use std::time::{Duration, SystemTime};
use vsock::VsockStream;

use android_system_virtualmachineservice::aidl::android::system::virtualmachineservice::IVirtualMachineService::{
    ERROR_PAYLOAD_CHANGED, ERROR_PAYLOAD_VERIFICATION_FAILED, ERROR_PAYLOAD_INVALID_CONFIG, ERROR_UNKNOWN, VM_BINDER_SERVICE_PORT, VM_STREAM_SERVICE_PORT, IVirtualMachineService,
};

const WAIT_TIMEOUT: Duration = Duration::from_secs(10);
const MAIN_APK_PATH: &str = "/dev/block/by-name/microdroid-apk";
const MAIN_APK_IDSIG_PATH: &str = "/dev/block/by-name/microdroid-apk-idsig";
const MAIN_APK_DEVICE_NAME: &str = "microdroid-apk";
const EXTRA_APK_PATH_PATTERN: &str = "/dev/block/by-name/extra-apk-*";
const EXTRA_IDSIG_PATH_PATTERN: &str = "/dev/block/by-name/extra-idsig-*";
const DM_MOUNTED_APK_PATH: &str = "/dev/block/mapper/microdroid-apk";
const APKDMVERITY_BIN: &str = "/system/bin/apkdmverity";
const ZIPFUSE_BIN: &str = "/system/bin/zipfuse";
const AVF_STRICT_BOOT: &str = "/sys/firmware/devicetree/base/chosen/avf,strict-boot";
const AVF_NEW_INSTANCE: &str = "/sys/firmware/devicetree/base/chosen/avf,new-instance";

/// The CID representing the host VM
const VMADDR_CID_HOST: u32 = 2;

const APEX_CONFIG_DONE_PROP: &str = "apex_config.done";
const LOGD_ENABLED_PROP: &str = "ro.boot.logd.enabled";
const APP_DEBUGGABLE_PROP: &str = "ro.boot.microdroid.app_debuggable";

#[derive(thiserror::Error, Debug)]
enum MicrodroidError {
    #[error("Payload has changed: {0}")]
    PayloadChanged(String),
    #[error("Payload verification has failed: {0}")]
    PayloadVerificationFailed(String),
    #[error("Payload config is invalid: {0}")]
    InvalidConfig(String),
}

fn translate_error(err: &Error) -> (i32, String) {
    if let Some(e) = err.downcast_ref::<MicrodroidError>() {
        match e {
            MicrodroidError::PayloadChanged(msg) => (ERROR_PAYLOAD_CHANGED, msg.to_string()),
            MicrodroidError::PayloadVerificationFailed(msg) => {
                (ERROR_PAYLOAD_VERIFICATION_FAILED, msg.to_string())
            }
            MicrodroidError::InvalidConfig(msg) => (ERROR_PAYLOAD_INVALID_CONFIG, msg.to_string()),
        }
    } else {
        (ERROR_UNKNOWN, err.to_string())
    }
}

fn get_vms_rpc_binder() -> Result<Strong<dyn IVirtualMachineService>> {
    // SAFETY: AIBinder returned by RpcClient has correct reference count, and the ownership can be
    // safely taken by new_spibinder.
    let ibinder = unsafe {
        new_spibinder(binder_rpc_unstable_bindgen::RpcClient(
            VMADDR_CID_HOST,
            VM_BINDER_SERVICE_PORT as u32,
        ) as *mut AIBinder)
    };
    if let Some(ibinder) = ibinder {
        <dyn IVirtualMachineService>::try_from(ibinder).context("Cannot connect to RPC service")
    } else {
        bail!("Invalid raw AIBinder")
    }
}

fn main() {
    if let Err(e) = try_main() {
        error!("Failed with {:?}. Shutting down...", e);
        if let Err(e) = system_properties::write("sys.powerctl", "shutdown") {
            error!("failed to shutdown {:?}", e);
        }
        std::process::exit(1);
    }
}

fn try_main() -> Result<()> {
    let _ = kernlog::init();
    info!("started.");

    let service = get_vms_rpc_binder().context("cannot connect to VirtualMachineService")?;
    match try_run_payload(&service) {
        Ok(code) => {
            info!("notifying payload finished");
            service.notifyPayloadFinished(code)?;
            if code == 0 {
                info!("task successfully finished");
            } else {
                error!("task exited with exit code: {}", code);
            }
            Ok(())
        }
        Err(err) => {
            error!("task terminated: {:?}", err);
            let (error_code, message) = translate_error(&err);
            service.notifyError(error_code, &message)?;
            Err(err)
        }
    }
}

fn dice_derivation(verified_data: &MicrodroidData, payload_config_path: &str) -> Result<()> {
    // Calculate compound digests of code and authorities
    let mut code_hash_ctx = digest::Context::new(&digest::SHA512);
    let mut authority_hash_ctx = digest::Context::new(&digest::SHA512);
    code_hash_ctx.update(verified_data.apk_data.root_hash.as_ref());
    authority_hash_ctx.update(verified_data.apk_data.pubkey.as_ref());
    for extra_apk in &verified_data.extra_apks_data {
        code_hash_ctx.update(extra_apk.root_hash.as_ref());
        authority_hash_ctx.update(extra_apk.pubkey.as_ref());
    }
    for apex in &verified_data.apex_data {
        code_hash_ctx.update(apex.root_digest.as_ref());
        authority_hash_ctx.update(apex.public_key.as_ref());
    }
    let code_hash = code_hash_ctx.finish().as_ref().try_into().unwrap();
    let authority_hash = authority_hash_ctx.finish().as_ref().try_into().unwrap();

    // {
    //   -70002: "Microdroid payload",
    //   -71000: payload_config_path
    // }
    let mut config_desc = vec![
        0xa2, 0x3a, 0x00, 0x01, 0x11, 0x71, 0x72, 0x4d, 0x69, 0x63, 0x72, 0x6f, 0x64, 0x72, 0x6f,
        0x69, 0x64, 0x20, 0x70, 0x61, 0x79, 0x6c, 0x6f, 0x61, 0x64, 0x3a, 0x00, 0x01, 0x15, 0x57,
    ];
    let config_path_bytes = payload_config_path.as_bytes();
    encode_header(3, config_path_bytes.len().try_into().unwrap(), &mut config_desc)?;
    config_desc.extend_from_slice(config_path_bytes);

    // Check app debuggability, conervatively assuming it is debuggable
    let app_debuggable = system_properties::read_bool(APP_DEBUGGABLE_PROP, true)?;

    // Send the details to diced
    let diced =
        wait_for_interface::<dyn IDiceMaintenance>("android.security.dice.IDiceMaintenance")
            .context("IDiceMaintenance service not found")?;
    diced
        .demoteSelf(&[InputValues {
            codeHash: code_hash,
            config: Config { desc: config_desc },
            authorityHash: authority_hash,
            authorityDescriptor: None,
            mode: if app_debuggable { Mode::DEBUG } else { Mode::NORMAL },
            hidden: verified_data.salt.clone().try_into().unwrap(),
        }])
        .context("IDiceMaintenance::demoteSelf failed")?;
    Ok(())
}

fn is_strict_boot() -> bool {
    Path::new(AVF_STRICT_BOOT).exists()
}

fn is_new_instance() -> bool {
    Path::new(AVF_NEW_INSTANCE).exists()
}

fn try_run_payload(service: &Strong<dyn IVirtualMachineService>) -> Result<i32> {
    let metadata = load_metadata().context("Failed to load payload metadata")?;

    let mut instance = InstanceDisk::new().context("Failed to load instance.img")?;
    let saved_data = instance.read_microdroid_data().context("Failed to read identity data")?;

    if is_strict_boot() {
        // Provisioning must happen on the first boot and never again.
        if is_new_instance() {
            ensure!(
                saved_data.is_none(),
                MicrodroidError::InvalidConfig("Found instance data on first boot.".to_string())
            );
        } else {
            ensure!(
                saved_data.is_some(),
                MicrodroidError::InvalidConfig("Instance data not found.".to_string())
            );
        };
    }

    // Verify the payload before using it.
    let verified_data =
        verify_payload(&metadata, saved_data.as_ref()).context("Payload verification failed")?;
    if let Some(saved_data) = saved_data {
        ensure!(
            saved_data == verified_data,
            MicrodroidError::PayloadChanged(String::from(
                "Detected an update of the payload which isn't supported yet."
            ))
        );
        info!("Saved data is verified.");
    } else {
        info!("Saving verified data.");
        instance.write_microdroid_data(&verified_data).context("Failed to write identity data")?;
    }

    // To minimize the exposure to untrusted data, derive dice profile as soon as possible.
    info!("DICE derivation for payload");
    dice_derivation(&verified_data, &metadata.payload_config_path)?;

    // Before reading a file from the APK, start zipfuse
    run_zipfuse(
        "fscontext=u:object_r:zipfusefs:s0,context=u:object_r:system_file:s0",
        Path::new("/dev/block/mapper/microdroid-apk"),
        Path::new("/mnt/apk"),
    )
    .context("Failed to run zipfuse")?;

    ensure!(
        !metadata.payload_config_path.is_empty(),
        MicrodroidError::InvalidConfig("No payload_config_path in metadata".to_string())
    );

    let config = load_config(Path::new(&metadata.payload_config_path))?;

    // Start tombstone_transmit if enabled
    if config.export_tombstones {
        system_properties::write("ctl.start", "tombstone_transmit")
            .context("Failed to start tombstone_transmit")?;
    }

    if config.extra_apks.len() != verified_data.extra_apks_data.len() {
        return Err(anyhow!(
            "config expects {} extra apks, but found only {}",
            config.extra_apks.len(),
            verified_data.extra_apks_data.len()
        ));
    }
    mount_extra_apks(&config)?;

    // Wait until apex config is done. (e.g. linker configuration for apexes)
    // TODO(jooyung): wait until sys.boot_completed?
    wait_for_apex_config_done()?;

    ensure!(
        config.task.is_some(),
        MicrodroidError::InvalidConfig("No task in VM config".to_string())
    );
    exec_task(&config.task.unwrap(), service)
}

struct ApkDmverityArgument<'a> {
    apk: &'a str,
    idsig: &'a str,
    name: &'a str,
    saved_root_hash: Option<&'a RootHash>,
}

fn run_apkdmverity(args: &[ApkDmverityArgument]) -> Result<Child> {
    let mut cmd = Command::new(APKDMVERITY_BIN);

    cmd.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());

    for argument in args {
        cmd.arg("--apk").arg(argument.apk).arg(argument.idsig).arg(argument.name);
        if let Some(root_hash) = argument.saved_root_hash {
            cmd.arg(&to_hex_string(root_hash));
        } else {
            cmd.arg("none");
        }
    }

    cmd.spawn().context("Spawn apkdmverity")
}

fn run_zipfuse(option: &str, zip_path: &Path, mount_dir: &Path) -> Result<Child> {
    Command::new(ZIPFUSE_BIN)
        .arg("-o")
        .arg(option)
        .arg(zip_path)
        .arg(mount_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .context("Spawn zipfuse")
}

// Verify payload before executing it. For APK payload, Full verification (which is slow) is done
// when the root_hash values from the idsig file and the instance disk are different. This function
// returns the verified root hash (for APK payload) and pubkeys (for APEX payloads) that can be
// saved to the instance disk.
fn verify_payload(
    metadata: &Metadata,
    saved_data: Option<&MicrodroidData>,
) -> Result<MicrodroidData> {
    let start_time = SystemTime::now();

    // Verify main APK
    let root_hash = saved_data.map(|d| &d.apk_data.root_hash);
    let root_hash_from_idsig = get_apk_root_hash_from_idsig(MAIN_APK_IDSIG_PATH)?;
    let root_hash_trustful = root_hash == Some(&root_hash_from_idsig);

    // If root_hash can be trusted, pass it to apkdmverity so that it uses the passed root_hash
    // instead of the value read from the idsig file.
    let main_apk_argument = {
        ApkDmverityArgument {
            apk: MAIN_APK_PATH,
            idsig: MAIN_APK_IDSIG_PATH,
            name: MAIN_APK_DEVICE_NAME,
            saved_root_hash: if root_hash_trustful {
                Some(root_hash_from_idsig.as_ref())
            } else {
                None
            },
        }
    };
    let mut apkdmverity_arguments = vec![main_apk_argument];

    // Verify extra APKs
    // For now, we can't read the payload config, so glob APKs and idsigs.
    // Later, we'll see if it matches with the payload config.

    // sort globbed paths to match apks (extra-apk-{idx}) and idsigs (extra-idsig-{idx})
    // e.g. "extra-apk-0" corresponds to "extra-idsig-0"
    let extra_apks =
        sorted(glob(EXTRA_APK_PATH_PATTERN)?.collect::<Result<Vec<_>, _>>()?).collect::<Vec<_>>();
    let extra_idsigs =
        sorted(glob(EXTRA_IDSIG_PATH_PATTERN)?.collect::<Result<Vec<_>, _>>()?).collect::<Vec<_>>();
    if extra_apks.len() != extra_idsigs.len() {
        return Err(anyhow!(
            "Extra apks/idsigs mismatch: {} apks but {} idsigs",
            extra_apks.len(),
            extra_idsigs.len()
        ));
    }
    let extra_apks_count = extra_apks.len();

    let (extra_apk_names, extra_root_hashes_from_idsig): (Vec<_>, Vec<_>) = extra_idsigs
        .iter()
        .enumerate()
        .map(|(i, extra_idsig)| {
            (
                format!("extra-apk-{}", i),
                get_apk_root_hash_from_idsig(extra_idsig.to_str().unwrap())
                    .expect("Can't find root hash from extra idsig"),
            )
        })
        .unzip();

    let saved_extra_root_hashes: Vec<_> = saved_data
        .map(|d| d.extra_apks_data.iter().map(|apk_data| &apk_data.root_hash).collect())
        .unwrap_or_else(Vec::new);
    let extra_root_hashes_trustful: Vec<_> = extra_root_hashes_from_idsig
        .iter()
        .enumerate()
        .map(|(i, root_hash_from_idsig)| {
            saved_extra_root_hashes.get(i).copied() == Some(root_hash_from_idsig)
        })
        .collect();

    for i in 0..extra_apks_count {
        apkdmverity_arguments.push({
            ApkDmverityArgument {
                apk: extra_apks[i].to_str().unwrap(),
                idsig: extra_idsigs[i].to_str().unwrap(),
                name: &extra_apk_names[i],
                saved_root_hash: if extra_root_hashes_trustful[i] {
                    Some(&extra_root_hashes_from_idsig[i])
                } else {
                    None
                },
            }
        });
    }

    // Start apkdmverity and wait for the dm-verify block
    let mut apkdmverity_child = run_apkdmverity(&apkdmverity_arguments)?;

    // While waiting for apkdmverity to mount APK, gathers public keys and root digests from
    // APEX payload.
    let apex_data_from_payload = get_apex_data_from_payload(metadata)?;
    if let Some(saved_data) = saved_data.map(|d| &d.apex_data) {
        // We don't support APEX updates. (assuming that update will change root digest)
        ensure!(
            saved_data == &apex_data_from_payload,
            MicrodroidError::PayloadChanged(String::from("APEXes have changed."))
        );
        let apex_metadata = to_metadata(&apex_data_from_payload);
        // Pass metadata(with public keys and root digests) to apexd so that it uses the passed
        // metadata instead of the default one (/dev/block/by-name/payload-metadata)
        OpenOptions::new()
            .create_new(true)
            .write(true)
            .open("/apex/vm-payload-metadata")
            .context("Failed to open /apex/vm-payload-metadata")
            .and_then(|f| write_metadata(&apex_metadata, f))?;
    }
    // Start apexd to activate APEXes
    system_properties::write("ctl.start", "apexd-vm")?;

    // TODO(inseob): add timeout
    apkdmverity_child.wait()?;

    // Do the full verification if the root_hash is un-trustful. This requires the full scanning of
    // the APK file and therefore can be very slow if the APK is large. Note that this step is
    // taken only when the root_hash is un-trustful which can be either when this is the first boot
    // of the VM or APK was updated in the host.
    // TODO(jooyung): consider multithreading to make this faster
    let main_apk_pubkey = get_public_key_from_apk(DM_MOUNTED_APK_PATH, root_hash_trustful)?;
    let extra_apks_data = extra_root_hashes_from_idsig
        .into_iter()
        .enumerate()
        .map(|(i, extra_root_hash)| {
            let mount_path = format!("/dev/block/mapper/{}", &extra_apk_names[i]);
            let apk_pubkey = get_public_key_from_apk(&mount_path, extra_root_hashes_trustful[i])?;
            Ok(ApkData { root_hash: extra_root_hash, pubkey: apk_pubkey })
        })
        .collect::<Result<Vec<_>>>()?;

    info!("payload verification successful. took {:#?}", start_time.elapsed().unwrap());

    // Use the salt from a verified instance, or generate a salt for a new instance.
    let salt = if let Some(saved_data) = saved_data {
        saved_data.salt.clone()
    } else {
        let mut salt = vec![0u8; 64];
        salt.as_mut_slice().try_fill(&mut rand::thread_rng())?;
        salt
    };

    // At this point, we can ensure that the root_hash from the idsig file is trusted, either by
    // fully verifying the APK or by comparing it with the saved root_hash.
    Ok(MicrodroidData {
        salt,
        apk_data: ApkData { root_hash: root_hash_from_idsig, pubkey: main_apk_pubkey },
        extra_apks_data,
        apex_data: apex_data_from_payload,
    })
}

fn mount_extra_apks(config: &VmPayloadConfig) -> Result<()> {
    // For now, only the number of apks is important, as the mount point and dm-verity name is fixed
    for i in 0..config.extra_apks.len() {
        let mount_dir = format!("/mnt/extra-apk/{}", i);
        create_dir(Path::new(&mount_dir)).context("Failed to create mount dir for extra apks")?;

        // don't wait, just detach
        run_zipfuse(
            "fscontext=u:object_r:zipfusefs:s0,context=u:object_r:extra_apk_file:s0",
            Path::new(&format!("/dev/block/mapper/extra-apk-{}", i)),
            Path::new(&mount_dir),
        )
        .context("Failed to zipfuse extra apks")?;
    }

    Ok(())
}

// Waits until linker config is generated
fn wait_for_apex_config_done() -> Result<()> {
    let mut prop = PropertyWatcher::new(APEX_CONFIG_DONE_PROP)?;
    loop {
        prop.wait()?;
        if system_properties::read_bool(APEX_CONFIG_DONE_PROP, false)? {
            break;
        }
    }
    Ok(())
}

fn get_apk_root_hash_from_idsig(path: &str) -> Result<Box<RootHash>> {
    let mut idsig = File::open(path)?;
    let idsig = V4Signature::from(&mut idsig)?;
    Ok(idsig.hashing_info.raw_root_hash)
}

fn get_public_key_from_apk(apk: &str, root_hash_trustful: bool) -> Result<Box<[u8]>> {
    if !root_hash_trustful {
        verify(apk).context(MicrodroidError::PayloadVerificationFailed(format!(
            "failed to verify {}",
            apk
        )))
    } else {
        get_public_key_der(apk)
    }
}

fn load_config(path: &Path) -> Result<VmPayloadConfig> {
    info!("loading config from {:?}...", path);
    let file = ioutil::wait_for_file(path, WAIT_TIMEOUT)?;
    Ok(serde_json::from_reader(file)?)
}

/// Executes the given task. Stdout of the task is piped into the vsock stream to the
/// virtualizationservice in the host side.
fn exec_task(task: &Task, service: &Strong<dyn IVirtualMachineService>) -> Result<i32> {
    info!("executing main task {:?}...", task);
    let mut command = build_command(task)?;

    info!("notifying payload started");
    service.notifyPayloadStarted()?;

    // Start logging if enabled
    // TODO(b/200914564) set filterspec if debug_level is app_only
    if system_properties::read_bool(LOGD_ENABLED_PROP, false)? {
        system_properties::write("ctl.start", "seriallogging")?;
    }

    let exit_status = command.spawn()?.wait()?;
    exit_status.code().ok_or_else(|| anyhow!("Failed to get exit_code from the paylaod."))
}

fn build_command(task: &Task) -> Result<Command> {
    const VMADDR_CID_HOST: u32 = 2;

    let mut command = match task.type_ {
        TaskType::Executable => {
            let mut command = Command::new(&task.command);
            command.args(&task.args);
            command
        }
        TaskType::MicrodroidLauncher => {
            let mut command = Command::new("/system/bin/microdroid_launcher");
            command.arg(find_library_path(&task.command)?).args(&task.args);
            command
        }
    };

    match VsockStream::connect_with_cid_port(VMADDR_CID_HOST, VM_STREAM_SERVICE_PORT as u32) {
        Ok(stream) => {
            // SAFETY: the ownership of the underlying file descriptor is transferred from stream
            // to the file object, and then into the Command object. When the command is finished,
            // the file descriptor is closed.
            let file = unsafe { File::from_raw_fd(stream.into_raw_fd()) };
            command
                .stdin(Stdio::from(file.try_clone()?))
                .stdout(Stdio::from(file.try_clone()?))
                .stderr(Stdio::from(file));
        }
        Err(e) => {
            error!("failed to connect to virtualization service: {}", e);
            // Don't fail hard here. Even if we failed to connect to the virtualizationservice,
            // we keep executing the task. This can happen if the owner of the VM doesn't register
            // callback to accept the stream. Use /dev/null as the stream so that the task can
            // make progress without waiting for someone to consume the output.
            command.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
        }
    }

    Ok(command)
}

fn find_library_path(name: &str) -> Result<String> {
    let mut watcher = PropertyWatcher::new("ro.product.cpu.abilist")?;
    let value = watcher.read(|_name, value| Ok(value.trim().to_string()))?;
    let abi = value.split(',').next().ok_or_else(|| anyhow!("no abilist"))?;
    let path = format!("/mnt/apk/lib/{}/{}", abi, name);

    let metadata = fs::metadata(&path)?;
    if !metadata.is_file() {
        bail!("{} is not a file", &path);
    }

    Ok(path)
}

fn to_hex_string(buf: &[u8]) -> String {
    buf.iter().map(|b| format!("{:02X}", b)).collect()
}
