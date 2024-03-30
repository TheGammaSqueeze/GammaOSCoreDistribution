//! Module containing validation functions for CertificateRequest.DeviceInfo

use crate::valueas::ValueAs;
use anyhow::{anyhow, ensure, Context, Result};
use ciborium::value::Value;

mod val {
    pub const BOOTLOADER_STATE: [&str; 2] = ["locked", "unlocked"];
    pub const FUSED: [u64; 2] = [1, 0];
    pub const SECURITY_LEVEL: [&str; 2] = ["tee", "strongbox"];
    pub const VBSTATE: [&str; 3] = ["green", "yellow", "orange"];
}

/// Unwrap the DeviceInfo array containing VerifiedDeviceInfo
/// and UnverifiedDeviceInfo from DeviceInfo cbor.
pub fn extract(filevalue: &Value) -> Result<&[Value]> {
    let filevalue = filevalue.as_array_of_len(2)?;
    Ok(filevalue)
}

/// Perform validation on DeviceInfo.
pub fn check(deviceinfo: &[Value]) -> Result<()> {
    let verified_info = &deviceinfo[0];
    let unverified_info = &deviceinfo[1];
    let version = verified_info.map_lookup(String::from("version"))?.as_i64()?;
    verified_info.check_string_val_if_key_in_map(String::from("os_version"))?;

    match version {
        1 => {
            verified_info.check_string_val_if_key_in_map(String::from("brand"))?;
            verified_info.check_string_val_if_key_in_map(String::from("manufacturer"))?;
            verified_info.check_string_val_if_key_in_map(String::from("product"))?;
            verified_info.check_string_val_if_key_in_map(String::from("model"))?;
            verified_info.check_string_val_if_key_in_map(String::from("board"))?;
            verified_info.check_string_val_if_key_in_map(String::from("device"))?;
            verified_info.check_arr_val_if_key_in_map(String::from("vb_state"), &val::VBSTATE)?;
            verified_info.check_arr_val_if_key_in_map(
                String::from("bootloader_state"),
                &val::BOOTLOADER_STATE,
            )?;
            verified_info
                .check_bytes_val_if_key_in_map(String::from("vbmeta_digest"))
                .context("vbmeta_digest not bytes")?;
            verified_info.check_date_val_if_key_in_map(String::from("system_patch_level"))?;
            verified_info.check_date_val_if_key_in_map(String::from("boot_patch_level"))?;
            verified_info.check_date_val_if_key_in_map(String::from("vendor_patch_level"))?;
        }
        2 => {
            verified_info.map_lookup(String::from("brand"))?.as_string()?;
            verified_info.map_lookup(String::from("manufacturer"))?.as_string()?;
            verified_info.map_lookup(String::from("product"))?.as_string()?;
            verified_info.map_lookup(String::from("model"))?.as_string()?;
            verified_info.map_lookup(String::from("device"))?.as_string()?;
            verified_info.check_arr_val_for_key_in_map(String::from("vb_state"), &val::VBSTATE)?;
            verified_info.check_arr_val_for_key_in_map(
                String::from("bootloader_state"),
                &val::BOOTLOADER_STATE,
            )?;
            verified_info
                .map_lookup(String::from("vbmeta_digest"))?
                .as_bytes()
                .ok_or_else(|| anyhow!("vbmeta_digest not bytes"))?;
            verified_info.map_lookup(String::from("system_patch_level"))?.as_date()?;
            verified_info.map_lookup(String::from("boot_patch_level"))?.as_date()?;
            verified_info.map_lookup(String::from("vendor_patch_level"))?.as_date()?;
            let fused = verified_info.map_lookup(String::from("fused"))?.as_u64()?;
            ensure!(val::FUSED.iter().any(|&x| x == fused));
        }
        _ => return Err(anyhow!("Invalid version")),
    }

    let security_level = verified_info.map_lookup(String::from("security_level"))?.as_string()?;
    ensure!(val::SECURITY_LEVEL.iter().any(|&x| x.eq(&security_level)));
    unverified_info.check_string_val_if_key_in_map(String::from("fingerprint"))?;
    Ok(())
}
