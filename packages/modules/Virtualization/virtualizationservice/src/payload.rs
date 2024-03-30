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

//! Payload disk image

use android_system_virtualizationservice::aidl::android::system::virtualizationservice::{
    DiskImage::DiskImage, Partition::Partition, VirtualMachineAppConfig::DebugLevel::DebugLevel,
    VirtualMachineAppConfig::VirtualMachineAppConfig,
    VirtualMachineRawConfig::VirtualMachineRawConfig,
};
use android_system_virtualizationservice::binder::ParcelFileDescriptor;
use anyhow::{anyhow, bail, Context, Result};
use binder::wait_for_interface;
use log::{info, warn};
use microdroid_metadata::{ApexPayload, ApkPayload, Metadata};
use microdroid_payload_config::{ApexConfig, VmPayloadConfig};
use once_cell::sync::OnceCell;
use packagemanager_aidl::aidl::android::content::pm::{
    IPackageManagerNative::IPackageManagerNative, StagedApexInfo::StagedApexInfo,
};
use regex::Regex;
use serde::Deserialize;
use serde_xml_rs::from_reader;
use std::collections::HashSet;
use std::fs::{metadata, File, OpenOptions};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::SystemTime;
use vmconfig::open_parcel_file;

/// The list of APEXes which microdroid requires.
// TODO(b/192200378) move this to microdroid.json?
const MICRODROID_REQUIRED_APEXES: [&str; 1] = ["com.android.os.statsd"];
const MICRODROID_REQUIRED_APEXES_DEBUG: [&str; 1] = ["com.android.adbd"];

const APEX_INFO_LIST_PATH: &str = "/apex/apex-info-list.xml";

const PACKAGE_MANAGER_NATIVE_SERVICE: &str = "package_native";

/// Represents the list of APEXes
#[derive(Clone, Debug, Deserialize, Eq, PartialEq)]
struct ApexInfoList {
    #[serde(rename = "apex-info")]
    list: Vec<ApexInfo>,
}

#[derive(Clone, Debug, Default, Deserialize, Eq, PartialEq)]
struct ApexInfo {
    #[serde(rename = "moduleName")]
    name: String,
    #[serde(rename = "versionCode")]
    version: u64,
    #[serde(rename = "modulePath")]
    path: PathBuf,

    #[serde(default)]
    has_classpath_jar: bool,

    // The field claims to be milliseconds but is actually seconds.
    #[serde(rename = "lastUpdateMillis")]
    last_update_seconds: u64,

    #[serde(rename = "isFactory")]
    is_factory: bool,

    #[serde(rename = "isActive")]
    is_active: bool,

    #[serde(rename = "provideSharedApexLibs")]
    provide_shared_apex_libs: bool,
}

impl ApexInfoList {
    /// Loads ApexInfoList
    fn load() -> Result<&'static ApexInfoList> {
        static INSTANCE: OnceCell<ApexInfoList> = OnceCell::new();
        INSTANCE.get_or_try_init(|| {
            let apex_info_list = File::open(APEX_INFO_LIST_PATH)
                .context(format!("Failed to open {}", APEX_INFO_LIST_PATH))?;
            let mut apex_info_list: ApexInfoList = from_reader(apex_info_list)
                .context(format!("Failed to parse {}", APEX_INFO_LIST_PATH))?;

            // For active APEXes, we run derive_classpath and parse its output to see if it
            // contributes to the classpath(s). (This allows us to handle any new classpath env
            // vars seamlessly.)
            let classpath_vars = run_derive_classpath()?;
            let classpath_apexes = find_apex_names_in_classpath(&classpath_vars)?;

            for apex_info in apex_info_list.list.iter_mut() {
                apex_info.has_classpath_jar = classpath_apexes.contains(&apex_info.name);
            }

            Ok(apex_info_list)
        })
    }

    // Override apex info with the staged one
    fn override_staged_apex(&mut self, staged_apex_info: &StagedApexInfo) -> Result<()> {
        let mut need_to_add: Option<ApexInfo> = None;
        for apex_info in self.list.iter_mut() {
            if staged_apex_info.moduleName == apex_info.name {
                if apex_info.is_active && apex_info.is_factory {
                    // Copy the entry to the end as factory/non-active after the loop
                    // to keep the factory version. Typically this step is unncessary,
                    // but some apexes (like sharedlibs) need to be kept even if it's inactive.
                    need_to_add.replace(ApexInfo { is_active: false, ..apex_info.clone() });
                    // And make this one as non-factory. Note that this one is still active
                    // and overridden right below.
                    apex_info.is_factory = false;
                }
                // Active one is overridden with the staged one.
                if apex_info.is_active {
                    apex_info.version = staged_apex_info.versionCode as u64;
                    apex_info.path = PathBuf::from(&staged_apex_info.diskImagePath);
                    apex_info.has_classpath_jar = staged_apex_info.hasClassPathJars;
                    apex_info.last_update_seconds = last_updated(&apex_info.path)?;
                }
            }
        }
        if let Some(info) = need_to_add {
            self.list.push(info);
        }
        Ok(())
    }
}

fn last_updated<P: AsRef<Path>>(path: P) -> Result<u64> {
    let metadata = metadata(path)?;
    Ok(metadata.modified()?.duration_since(SystemTime::UNIX_EPOCH)?.as_secs())
}

impl ApexInfo {
    fn matches(&self, apex_config: &ApexConfig) -> bool {
        // Match with pseudo name "{CLASSPATH}" which represents APEXes contributing
        // to any derive_classpath environment variable
        if apex_config.name == "{CLASSPATH}" && self.has_classpath_jar {
            return true;
        }
        if apex_config.name == self.name {
            return true;
        }
        false
    }
}

struct PackageManager {
    apex_info_list: &'static ApexInfoList,
}

impl PackageManager {
    fn new() -> Result<Self> {
        let apex_info_list = ApexInfoList::load()?;
        Ok(Self { apex_info_list })
    }

    fn get_apex_list(&self, prefer_staged: bool) -> Result<ApexInfoList> {
        // get the list of active apexes
        let mut list = self.apex_info_list.clone();
        // When prefer_staged, we override ApexInfo by consulting "package_native"
        if prefer_staged {
            let pm =
                wait_for_interface::<dyn IPackageManagerNative>(PACKAGE_MANAGER_NATIVE_SERVICE)
                    .context("Failed to get service when prefer_staged is set.")?;
            let staged =
                pm.getStagedApexModuleNames().context("getStagedApexModuleNames failed")?;
            for name in staged {
                if let Some(staged_apex_info) =
                    pm.getStagedApexInfo(&name).context("getStagedApexInfo failed")?
                {
                    list.override_staged_apex(&staged_apex_info)?;
                }
            }
        }
        Ok(list)
    }
}

fn make_metadata_file(
    config_path: &str,
    apex_infos: &[&ApexInfo],
    temporary_directory: &Path,
) -> Result<ParcelFileDescriptor> {
    let metadata_path = temporary_directory.join("metadata");
    let metadata = Metadata {
        version: 1,
        apexes: apex_infos
            .iter()
            .enumerate()
            .map(|(i, apex_info)| {
                Ok(ApexPayload {
                    name: apex_info.name.clone(),
                    partition_name: format!("microdroid-apex-{}", i),
                    last_update_seconds: apex_info.last_update_seconds,
                    is_factory: apex_info.is_factory,
                    ..Default::default()
                })
            })
            .collect::<Result<_>>()?,
        apk: Some(ApkPayload {
            name: "apk".to_owned(),
            payload_partition_name: "microdroid-apk".to_owned(),
            idsig_partition_name: "microdroid-apk-idsig".to_owned(),
            ..Default::default()
        })
        .into(),
        payload_config_path: format!("/mnt/apk/{}", config_path),
        ..Default::default()
    };

    // Write metadata to file.
    let mut metadata_file = OpenOptions::new()
        .create_new(true)
        .read(true)
        .write(true)
        .open(&metadata_path)
        .with_context(|| format!("Failed to open metadata file {:?}", metadata_path))?;
    microdroid_metadata::write_metadata(&metadata, &mut metadata_file)?;

    // Re-open the metadata file as read-only.
    open_parcel_file(&metadata_path, false)
}

/// Creates a DiskImage with partitions:
///   metadata: metadata
///   microdroid-apex-0: apex 0
///   microdroid-apex-1: apex 1
///   ..
///   microdroid-apk: apk
///   microdroid-apk-idsig: idsig
///   extra-apk-0:   additional apk 0
///   extra-idsig-0: additional idsig 0
///   extra-apk-1:   additional apk 1
///   extra-idsig-1: additional idsig 1
///   ..
fn make_payload_disk(
    app_config: &VirtualMachineAppConfig,
    apk_file: File,
    idsig_file: File,
    vm_payload_config: &VmPayloadConfig,
    temporary_directory: &Path,
) -> Result<DiskImage> {
    if vm_payload_config.extra_apks.len() != app_config.extraIdsigs.len() {
        bail!(
            "payload config has {} apks, but app config has {} idsigs",
            vm_payload_config.extra_apks.len(),
            app_config.extraIdsigs.len()
        );
    }

    let pm = PackageManager::new()?;
    let apex_list = pm.get_apex_list(vm_payload_config.prefer_staged)?;

    // collect APEXes from config
    let mut apex_infos =
        collect_apex_infos(&apex_list, &vm_payload_config.apexes, app_config.debugLevel);

    // Pass sorted list of apexes. Sorting key shouldn't use `path` because it will change after
    // reboot with prefer_staged. `last_update_seconds` is added to distinguish "samegrade"
    // update.
    apex_infos.sort_by_key(|info| (&info.name, &info.version, &info.last_update_seconds));
    info!("Microdroid payload APEXes: {:?}", apex_infos.iter().map(|ai| &ai.name));

    let metadata_file =
        make_metadata_file(&app_config.configPath, &apex_infos, temporary_directory)?;
    // put metadata at the first partition
    let mut partitions = vec![Partition {
        label: "payload-metadata".to_owned(),
        image: Some(metadata_file),
        writable: false,
    }];

    for (i, apex_info) in apex_infos.iter().enumerate() {
        let apex_file = open_parcel_file(&apex_info.path, false)?;
        partitions.push(Partition {
            label: format!("microdroid-apex-{}", i),
            image: Some(apex_file),
            writable: false,
        });
    }
    partitions.push(Partition {
        label: "microdroid-apk".to_owned(),
        image: Some(ParcelFileDescriptor::new(apk_file)),
        writable: false,
    });
    partitions.push(Partition {
        label: "microdroid-apk-idsig".to_owned(),
        image: Some(ParcelFileDescriptor::new(idsig_file)),
        writable: false,
    });

    // we've already checked that extra_apks and extraIdsigs are in the same size.
    let extra_apks = &vm_payload_config.extra_apks;
    let extra_idsigs = &app_config.extraIdsigs;
    for (i, (extra_apk, extra_idsig)) in extra_apks.iter().zip(extra_idsigs.iter()).enumerate() {
        partitions.push(Partition {
            label: format!("extra-apk-{}", i),
            image: Some(ParcelFileDescriptor::new(File::open(PathBuf::from(&extra_apk.path))?)),
            writable: false,
        });

        partitions.push(Partition {
            label: format!("extra-idsig-{}", i),
            image: Some(ParcelFileDescriptor::new(extra_idsig.as_ref().try_clone()?)),
            writable: false,
        });
    }

    Ok(DiskImage { image: None, partitions, writable: false })
}

fn run_derive_classpath() -> Result<String> {
    let result = Command::new("/apex/com.android.sdkext/bin/derive_classpath")
        .arg("/proc/self/fd/1")
        .output()
        .context("Failed to run derive_classpath")?;

    if !result.status.success() {
        bail!("derive_classpath returned {}", result.status);
    }

    String::from_utf8(result.stdout).context("Converting derive_classpath output")
}

fn find_apex_names_in_classpath(classpath_vars: &str) -> Result<HashSet<String>> {
    // Each line should be in the format "export <var name> <paths>", where <paths> is a
    // colon-separated list of paths to JARs. We don't care about the var names, and we're only
    // interested in paths that look like "/apex/<apex name>/<anything>" so we know which APEXes
    // contribute to at least one var.
    let mut apexes = HashSet::new();

    let pattern = Regex::new(r"^export [^ ]+ ([^ ]+)$").context("Failed to construct Regex")?;
    for line in classpath_vars.lines() {
        if let Some(captures) = pattern.captures(line) {
            if let Some(paths) = captures.get(1) {
                apexes.extend(paths.as_str().split(':').filter_map(|path| {
                    let path = path.strip_prefix("/apex/")?;
                    Some(path[..path.find('/')?].to_owned())
                }));
                continue;
            }
        }
        warn!("Malformed line from derive_classpath: {}", line);
    }

    Ok(apexes)
}

// Collect ApexInfos from VM config
fn collect_apex_infos<'a>(
    apex_list: &'a ApexInfoList,
    apex_configs: &[ApexConfig],
    debug_level: DebugLevel,
) -> Vec<&'a ApexInfo> {
    let mut additional_apexes: Vec<&str> = MICRODROID_REQUIRED_APEXES.to_vec();
    if debug_level != DebugLevel::NONE {
        additional_apexes.extend(MICRODROID_REQUIRED_APEXES_DEBUG.to_vec());
    }

    apex_list
        .list
        .iter()
        .filter(|ai| {
            apex_configs.iter().any(|cfg| ai.matches(cfg) && ai.is_active)
                || additional_apexes.iter().any(|name| name == &ai.name && ai.is_active)
                || ai.provide_shared_apex_libs
        })
        .collect()
}

pub fn add_microdroid_images(
    config: &VirtualMachineAppConfig,
    temporary_directory: &Path,
    apk_file: File,
    idsig_file: File,
    instance_file: File,
    vm_payload_config: &VmPayloadConfig,
    vm_config: &mut VirtualMachineRawConfig,
) -> Result<()> {
    vm_config.disks.push(make_payload_disk(
        config,
        apk_file,
        idsig_file,
        vm_payload_config,
        temporary_directory,
    )?);

    vm_config.disks[1].partitions.push(Partition {
        label: "vbmeta".to_owned(),
        image: Some(open_parcel_file(
            Path::new("/apex/com.android.virt/etc/fs/microdroid_vbmeta_bootconfig.img"),
            false,
        )?),
        writable: false,
    });
    let bootconfig_image = "/apex/com.android.virt/etc/microdroid_bootconfig.".to_owned()
        + match config.debugLevel {
            DebugLevel::NONE => "normal",
            DebugLevel::APP_ONLY => "app_debuggable",
            DebugLevel::FULL => "full_debuggable",
            _ => return Err(anyhow!("unsupported debug level: {:?}", config.debugLevel)),
        };
    vm_config.disks[1].partitions.push(Partition {
        label: "bootconfig".to_owned(),
        image: Some(open_parcel_file(Path::new(&bootconfig_image), false)?),
        writable: false,
    });

    // instance image is at the second partition in the second disk.
    vm_config.disks[1].partitions.push(Partition {
        label: "vm-instance".to_owned(),
        image: Some(ParcelFileDescriptor::new(instance_file)),
        writable: true,
    });

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::NamedTempFile;

    #[test]
    fn test_find_apex_names_in_classpath() {
        let vars = r#"
export FOO /apex/unterminated
export BAR /apex/valid.apex/something
wrong
export EMPTY
export OTHER /foo/bar:/baz:/apex/second.valid.apex/:gibberish:"#;
        let expected = vec!["valid.apex", "second.valid.apex"];
        let expected: HashSet<_> = expected.into_iter().map(ToString::to_string).collect();

        assert_eq!(find_apex_names_in_classpath(vars).unwrap(), expected);
    }

    #[test]
    fn test_collect_apexes() {
        let apex_info_list = ApexInfoList {
            list: vec![
                ApexInfo {
                    // 0
                    name: "com.android.adbd".to_string(),
                    path: PathBuf::from("adbd"),
                    has_classpath_jar: false,
                    last_update_seconds: 12345678,
                    is_factory: true,
                    is_active: true,
                    ..Default::default()
                },
                ApexInfo {
                    // 1
                    name: "com.android.os.statsd".to_string(),
                    path: PathBuf::from("statsd"),
                    has_classpath_jar: false,
                    last_update_seconds: 12345678,
                    is_factory: true,
                    is_active: false,
                    ..Default::default()
                },
                ApexInfo {
                    // 2
                    name: "com.android.os.statsd".to_string(),
                    path: PathBuf::from("statsd/updated"),
                    has_classpath_jar: false,
                    last_update_seconds: 12345678 + 1,
                    is_factory: false,
                    is_active: true,
                    ..Default::default()
                },
                ApexInfo {
                    // 3
                    name: "no_classpath".to_string(),
                    path: PathBuf::from("no_classpath"),
                    has_classpath_jar: false,
                    last_update_seconds: 12345678,
                    is_factory: true,
                    is_active: true,
                    ..Default::default()
                },
                ApexInfo {
                    // 4
                    name: "has_classpath".to_string(),
                    path: PathBuf::from("has_classpath"),
                    has_classpath_jar: true,
                    last_update_seconds: 87654321,
                    is_factory: true,
                    is_active: false,
                    ..Default::default()
                },
                ApexInfo {
                    // 5
                    name: "has_classpath".to_string(),
                    path: PathBuf::from("has_classpath/updated"),
                    has_classpath_jar: true,
                    last_update_seconds: 87654321 + 1,
                    is_factory: false,
                    is_active: true,
                    ..Default::default()
                },
                ApexInfo {
                    // 6
                    name: "apex-foo".to_string(),
                    path: PathBuf::from("apex-foo"),
                    has_classpath_jar: false,
                    last_update_seconds: 87654321,
                    is_factory: true,
                    is_active: false,
                    ..Default::default()
                },
                ApexInfo {
                    // 7
                    name: "apex-foo".to_string(),
                    path: PathBuf::from("apex-foo/updated"),
                    has_classpath_jar: false,
                    last_update_seconds: 87654321 + 1,
                    is_factory: false,
                    is_active: true,
                    ..Default::default()
                },
                ApexInfo {
                    // 8
                    name: "sharedlibs".to_string(),
                    path: PathBuf::from("apex-foo"),
                    last_update_seconds: 87654321,
                    is_factory: true,
                    provide_shared_apex_libs: true,
                    ..Default::default()
                },
                ApexInfo {
                    // 9
                    name: "sharedlibs".to_string(),
                    path: PathBuf::from("apex-foo/updated"),
                    last_update_seconds: 87654321 + 1,
                    is_active: true,
                    provide_shared_apex_libs: true,
                    ..Default::default()
                },
            ],
        };
        let apex_configs = vec![
            ApexConfig { name: "apex-foo".to_string() },
            ApexConfig { name: "{CLASSPATH}".to_string() },
        ];
        assert_eq!(
            collect_apex_infos(&apex_info_list, &apex_configs, DebugLevel::FULL),
            vec![
                // Pass active/required APEXes
                &apex_info_list.list[0],
                &apex_info_list.list[2],
                // Pass active APEXes specified in the config
                &apex_info_list.list[5],
                &apex_info_list.list[7],
                // Pass both preinstalled(inactive) and updated(active) for "sharedlibs" APEXes
                &apex_info_list.list[8],
                &apex_info_list.list[9],
            ]
        );
    }

    #[test]
    fn test_prefer_staged_apex_with_factory_active_apex() {
        let single_apex = ApexInfo {
            name: "foo".to_string(),
            version: 1,
            path: PathBuf::from("foo.apex"),
            is_factory: true,
            is_active: true,
            ..Default::default()
        };
        let mut apex_info_list = ApexInfoList { list: vec![single_apex.clone()] };

        let staged = NamedTempFile::new().unwrap();
        apex_info_list
            .override_staged_apex(&StagedApexInfo {
                moduleName: "foo".to_string(),
                versionCode: 2,
                diskImagePath: staged.path().to_string_lossy().to_string(),
                ..Default::default()
            })
            .expect("should be ok");

        assert_eq!(
            apex_info_list,
            ApexInfoList {
                list: vec![
                    ApexInfo {
                        version: 2,
                        is_factory: false,
                        path: staged.path().to_owned(),
                        last_update_seconds: last_updated(staged.path()).unwrap(),
                        ..single_apex.clone()
                    },
                    ApexInfo { is_active: false, ..single_apex },
                ],
            }
        );
    }

    #[test]
    fn test_prefer_staged_apex_with_factory_and_inactive_apex() {
        let factory_apex = ApexInfo {
            name: "foo".to_string(),
            version: 1,
            path: PathBuf::from("foo.apex"),
            is_factory: true,
            ..Default::default()
        };
        let active_apex = ApexInfo {
            name: "foo".to_string(),
            version: 2,
            path: PathBuf::from("foo.downloaded.apex"),
            is_active: true,
            ..Default::default()
        };
        let mut apex_info_list =
            ApexInfoList { list: vec![factory_apex.clone(), active_apex.clone()] };

        let staged = NamedTempFile::new().unwrap();
        apex_info_list
            .override_staged_apex(&StagedApexInfo {
                moduleName: "foo".to_string(),
                versionCode: 3,
                diskImagePath: staged.path().to_string_lossy().to_string(),
                ..Default::default()
            })
            .expect("should be ok");

        assert_eq!(
            apex_info_list,
            ApexInfoList {
                list: vec![
                    // factory apex isn't touched
                    factory_apex,
                    // update active one
                    ApexInfo {
                        version: 3,
                        path: staged.path().to_owned(),
                        last_update_seconds: last_updated(staged.path()).unwrap(),
                        ..active_apex
                    },
                ],
            }
        );
    }
}
