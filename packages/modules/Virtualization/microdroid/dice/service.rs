// Copyright 2022, The Android Open Source Project
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

//! Main entry point for the microdroid IDiceDevice HAL implementation.

use anyhow::{bail, Error, Result};
use byteorder::{NativeEndian, ReadBytesExt};
use diced::{
    dice,
    hal_node::{DiceArtifacts, DiceDevice, ResidentHal, UpdatableDiceArtifacts},
};
use libc::{c_void, mmap, munmap, MAP_FAILED, MAP_PRIVATE, PROT_READ};
use serde::{Deserialize, Serialize};
use std::fs;
use std::os::unix::io::AsRawFd;
use std::panic;
use std::path::{Path, PathBuf};
use std::ptr::null_mut;
use std::slice;
use std::sync::Arc;

const AVF_STRICT_BOOT: &str = "/sys/firmware/devicetree/base/chosen/avf,strict-boot";
const DICE_HAL_SERVICE_NAME: &str = "android.hardware.security.dice.IDiceDevice/default";

/// Artifacts that are mapped into the process address space from the driver.
struct MappedDriverArtifacts<'a> {
    mmap_addr: *mut c_void,
    mmap_size: usize,
    cdi_attest: &'a [u8; dice::CDI_SIZE],
    cdi_seal: &'a [u8; dice::CDI_SIZE],
    bcc: &'a [u8],
}

impl MappedDriverArtifacts<'_> {
    fn new(driver_path: &Path) -> Result<Self> {
        let mut file = fs::File::open(driver_path)
            .map_err(|error| Error::new(error).context("Opening driver"))?;
        let mmap_size =
            file.read_u64::<NativeEndian>()
                .map_err(|error| Error::new(error).context("Reading driver"))? as usize;
        // It's safe to map the driver as the service will only create a single
        // mapping per process.
        let mmap_addr = unsafe {
            let fd = file.as_raw_fd();
            mmap(null_mut(), mmap_size, PROT_READ, MAP_PRIVATE, fd, 0)
        };
        if mmap_addr == MAP_FAILED {
            bail!("Failed to mmap {:?}", driver_path);
        }
        // The slice is created for the region of memory that was just
        // successfully mapped into the process address space so it will be
        // accessible and not referenced from anywhere else.
        let mmap_buf =
            unsafe { slice::from_raw_parts((mmap_addr as *const u8).as_ref().unwrap(), mmap_size) };
        // Very inflexible parsing / validation of the BccHandover data. Assumes deterministically
        // encoded CBOR.
        //
        // BccHandover = {
        //   1 : bstr .size 32,     ; CDI_Attest
        //   2 : bstr .size 32,     ; CDI_Seal
        //   3 : Bcc,               ; Certificate chain
        // }
        if mmap_buf[0..4] != [0xa3, 0x01, 0x58, 0x20]
            || mmap_buf[36..39] != [0x02, 0x58, 0x20]
            || mmap_buf[71] != 0x03
        {
            bail!("BccHandover format mismatch");
        }
        Ok(Self {
            mmap_addr,
            mmap_size,
            cdi_attest: mmap_buf[4..36].try_into().unwrap(),
            cdi_seal: mmap_buf[39..71].try_into().unwrap(),
            bcc: &mmap_buf[72..],
        })
    }
}

impl Drop for MappedDriverArtifacts<'_> {
    fn drop(&mut self) {
        // All references to the mapped region have the same lifetime as self.
        // Since self is being dropped, so are all the references to the mapped
        // region meaning its safe to unmap.
        let ret = unsafe { munmap(self.mmap_addr, self.mmap_size) };
        if ret != 0 {
            log::warn!("Failed to munmap ({})", ret);
        }
    }
}

impl DiceArtifacts for MappedDriverArtifacts<'_> {
    fn cdi_attest(&self) -> &[u8; dice::CDI_SIZE] {
        self.cdi_attest
    }
    fn cdi_seal(&self) -> &[u8; dice::CDI_SIZE] {
        self.cdi_seal
    }
    fn bcc(&self) -> Vec<u8> {
        // The BCC only contains public information so it's fine to copy.
        self.bcc.to_vec()
    }
}

/// Artifacts that are kept in the process address space after the artifacts
/// from the driver have been consumed.
#[derive(Clone, Serialize, Deserialize)]
struct RawArtifacts {
    cdi_attest: [u8; dice::CDI_SIZE],
    cdi_seal: [u8; dice::CDI_SIZE],
    bcc: Vec<u8>,
}

impl DiceArtifacts for RawArtifacts {
    fn cdi_attest(&self) -> &[u8; dice::CDI_SIZE] {
        &self.cdi_attest
    }
    fn cdi_seal(&self) -> &[u8; dice::CDI_SIZE] {
        &self.cdi_seal
    }
    fn bcc(&self) -> Vec<u8> {
        // The BCC only contains public information so it's fine to copy.
        self.bcc.clone()
    }
}

#[derive(Clone, Serialize, Deserialize)]
enum DriverArtifactManager {
    Invalid,
    Driver(PathBuf),
    Updated(RawArtifacts),
}

impl DriverArtifactManager {
    fn new(driver_path: &Path) -> Self {
        if driver_path.exists() {
            log::info!("Using DICE values from driver");
            Self::Driver(driver_path.to_path_buf())
        } else if Path::new(AVF_STRICT_BOOT).exists() {
            log::error!("Strict boot requires DICE value from driver but none were found");
            Self::Invalid
        } else {
            log::warn!("Using sample DICE values");
            let (cdi_attest, cdi_seal, bcc) = diced_sample_inputs::make_sample_bcc_and_cdis()
                .expect("Failed to create sample dice artifacts.");
            Self::Updated(RawArtifacts {
                cdi_attest: cdi_attest[..].try_into().unwrap(),
                cdi_seal: cdi_seal[..].try_into().unwrap(),
                bcc,
            })
        }
    }
}

impl UpdatableDiceArtifacts for DriverArtifactManager {
    fn with_artifacts<F, T>(&self, f: F) -> Result<T>
    where
        F: FnOnce(&dyn DiceArtifacts) -> Result<T>,
    {
        match self {
            Self::Invalid => bail!("No DICE artifacts available."),
            Self::Driver(driver_path) => f(&MappedDriverArtifacts::new(driver_path.as_path())?),
            Self::Updated(raw_artifacts) => f(raw_artifacts),
        }
    }
    fn update(self, new_artifacts: &impl DiceArtifacts) -> Result<Self> {
        if let Self::Invalid = self {
            bail!("Cannot update invalid DICE artifacts.");
        }
        if let Self::Driver(driver_path) = self {
            // Writing to the device wipes the artifcates. The string is ignored
            // by the driver but included for documentation.
            fs::write(driver_path, "wipe")
                .map_err(|error| Error::new(error).context("Wiping driver"))?;
        }
        Ok(Self::Updated(RawArtifacts {
            cdi_attest: *new_artifacts.cdi_attest(),
            cdi_seal: *new_artifacts.cdi_seal(),
            bcc: new_artifacts.bcc(),
        }))
    }
}

fn main() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("android.hardware.security.dice")
            .with_min_level(log::Level::Debug),
    );
    // Redirect panic messages to logcat.
    panic::set_hook(Box::new(|panic_info| {
        log::error!("{}", panic_info);
    }));

    // Saying hi.
    log::info!("android.hardware.security.dice is starting.");

    let hal_impl = Arc::new(
        unsafe {
            // Safety: ResidentHal cannot be used in multi threaded processes.
            // This service does not start a thread pool. The main thread is the only thread
            // joining the thread pool, thereby keeping the process single threaded.
            ResidentHal::new(DriverArtifactManager::new(Path::new("/dev/open-dice0")))
        }
        .expect("Failed to create ResidentHal implementation."),
    );

    let hal = DiceDevice::new_as_binder(hal_impl).expect("Failed to construct hal service.");

    binder::add_service(DICE_HAL_SERVICE_NAME, hal.as_binder())
        .expect("Failed to register IDiceDevice Service");

    log::info!("Joining thread pool now.");
    binder::ProcessState::join_thread_pool();
}
