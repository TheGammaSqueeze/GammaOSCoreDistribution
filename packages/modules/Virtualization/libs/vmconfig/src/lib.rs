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

//! Struct for VM configuration with JSON (de)serialization and AIDL parcelables

use android_system_virtualizationservice::{
    aidl::android::system::virtualizationservice::DiskImage::DiskImage as AidlDiskImage,
    aidl::android::system::virtualizationservice::Partition::Partition as AidlPartition,
    aidl::android::system::virtualizationservice::VirtualMachineRawConfig::VirtualMachineRawConfig,
    binder::ParcelFileDescriptor,
};

use anyhow::{bail, Context, Error, Result};
use semver::VersionReq;
use serde::{Deserialize, Serialize};
use std::convert::TryInto;
use std::fs::{File, OpenOptions};
use std::io::BufReader;
use std::num::NonZeroU32;
use std::path::{Path, PathBuf};

/// Configuration for a particular VM to be started.
#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub struct VmConfig {
    /// The filename of the kernel image, if any.
    pub kernel: Option<PathBuf>,
    /// The filename of the initial ramdisk for the kernel, if any.
    pub initrd: Option<PathBuf>,
    /// Parameters to pass to the kernel. As far as the VMM and boot protocol are concerned this is
    /// just a string, but typically it will contain multiple parameters separated by spaces.
    pub params: Option<String>,
    /// The bootloader to use. If this is supplied then the kernel and initrd must not be supplied;
    /// the bootloader is instead responsibly for loading the kernel from one of the disks.
    pub bootloader: Option<PathBuf>,
    /// Disk images to be made available to the VM.
    #[serde(default)]
    pub disks: Vec<DiskImage>,
    /// Whether the VM should be a protected VM.
    #[serde(default)]
    pub protected: bool,
    /// The amount of RAM to give the VM, in MiB.
    #[serde(default)]
    pub memory_mib: Option<NonZeroU32>,
    /// Version or range of versions of the virtual platform that this config is compatible with.
    /// The format follows SemVer (https://semver.org).
    pub platform_version: VersionReq,
}

impl VmConfig {
    /// Ensure that the configuration has a valid combination of fields set, or return an error if
    /// not.
    pub fn validate(&self) -> Result<(), Error> {
        if self.bootloader.is_none() && self.kernel.is_none() {
            bail!("VM must have either a bootloader or a kernel image.");
        }
        if self.bootloader.is_some() && (self.kernel.is_some() || self.initrd.is_some()) {
            bail!("Can't have both bootloader and kernel/initrd image.");
        }
        for disk in &self.disks {
            if disk.image.is_none() == disk.partitions.is_empty() {
                bail!("Exactly one of image and partitions must be specified. (Was {:?}.)", disk);
            }
        }
        Ok(())
    }

    /// Load the configuration for a VM from the given JSON file, and check that it is valid.
    pub fn load(file: &File) -> Result<VmConfig, Error> {
        let buffered = BufReader::new(file);
        let config: VmConfig = serde_json::from_reader(buffered)?;
        config.validate()?;
        Ok(config)
    }

    /// Convert the `VmConfig` to a [`VirtualMachineConfig`] which can be passed to the Virt
    /// Manager.
    pub fn to_parcelable(&self) -> Result<VirtualMachineRawConfig, Error> {
        let memory_mib = if let Some(memory_mib) = self.memory_mib {
            memory_mib.get().try_into().context("Invalid memory_mib")?
        } else {
            0
        };
        Ok(VirtualMachineRawConfig {
            kernel: maybe_open_parcel_file(&self.kernel, false)?,
            initrd: maybe_open_parcel_file(&self.initrd, false)?,
            params: self.params.clone(),
            bootloader: maybe_open_parcel_file(&self.bootloader, false)?,
            disks: self.disks.iter().map(DiskImage::to_parcelable).collect::<Result<_, Error>>()?,
            protectedVm: self.protected,
            memoryMib: memory_mib,
            platformVersion: self.platform_version.to_string(),
            ..Default::default()
        })
    }
}

/// A disk image to be made available to the VM.
#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub struct DiskImage {
    /// The filename of the disk image, if it already exists. Exactly one of this and `partitions`
    /// must be specified.
    #[serde(default)]
    pub image: Option<PathBuf>,
    /// A set of partitions to be assembled into a composite image.
    #[serde(default)]
    pub partitions: Vec<Partition>,
    /// Whether this disk should be writable by the VM.
    pub writable: bool,
}

impl DiskImage {
    fn to_parcelable(&self) -> Result<AidlDiskImage, Error> {
        let partitions =
            self.partitions.iter().map(Partition::to_parcelable).collect::<Result<_>>()?;
        Ok(AidlDiskImage {
            image: maybe_open_parcel_file(&self.image, self.writable)?,
            writable: self.writable,
            partitions,
        })
    }
}

/// A partition to be assembled into a composite image.
#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub struct Partition {
    /// A label for the partition.
    pub label: String,
    /// The filename of the partition image.
    pub path: PathBuf,
    /// Whether the partition should be writable.
    #[serde(default)]
    pub writable: bool,
}

impl Partition {
    fn to_parcelable(&self) -> Result<AidlPartition> {
        Ok(AidlPartition {
            image: Some(open_parcel_file(&self.path, self.writable)?),
            writable: self.writable,
            label: self.label.to_owned(),
        })
    }
}

/// Try to open the given file and wrap it in a [`ParcelFileDescriptor`].
pub fn open_parcel_file(filename: &Path, writable: bool) -> Result<ParcelFileDescriptor> {
    Ok(ParcelFileDescriptor::new(
        OpenOptions::new()
            .read(true)
            .write(writable)
            .open(filename)
            .with_context(|| format!("Failed to open {:?}", filename))?,
    ))
}

/// If the given filename is `Some`, try to open it and wrap it in a [`ParcelFileDescriptor`].
fn maybe_open_parcel_file(
    filename: &Option<PathBuf>,
    writable: bool,
) -> Result<Option<ParcelFileDescriptor>> {
    filename.as_deref().map(|filename| open_parcel_file(filename, writable)).transpose()
}
