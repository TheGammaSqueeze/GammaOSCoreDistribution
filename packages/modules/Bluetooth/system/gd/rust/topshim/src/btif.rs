//! Shim for `bt_interface_t`, providing access to libbluetooth.
//!
//! This is a shim interface for calling the C++ bluetooth interface via Rust.

use crate::bindings::root as bindings;
use crate::topstack::get_dispatchers;
use num_traits::cast::{FromPrimitive, ToPrimitive};
use std::cmp;
use std::fmt::{Debug, Formatter, Result};
use std::mem;
use std::os::raw::c_char;
use std::sync::{Arc, Mutex};
use std::vec::Vec;
use topshim_macros::cb_variant;

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtState {
    Off = 0,
    On,
}

impl From<bindings::bt_state_t> for BtState {
    fn from(item: bindings::bt_state_t) -> Self {
        BtState::from_u32(item).unwrap_or_else(|| BtState::Off)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtTransport {
    Auto = 0,
    Bredr,
    Le,
}

impl From<i32> for BtTransport {
    fn from(item: i32) -> Self {
        BtTransport::from_i32(item).unwrap_or_else(|| BtTransport::Auto)
    }
}

impl From<BtTransport> for i32 {
    fn from(item: BtTransport) -> Self {
        item.to_i32().unwrap_or_else(|| 0)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtSspVariant {
    PasskeyConfirmation = 0,
    PasskeyEntry,
    Consent,
    PasskeyNotification,
}

impl From<bindings::bt_ssp_variant_t> for BtSspVariant {
    fn from(item: bindings::bt_ssp_variant_t) -> Self {
        BtSspVariant::from_u32(item).unwrap_or_else(|| BtSspVariant::PasskeyConfirmation)
    }
}

impl From<BtSspVariant> for bindings::bt_ssp_variant_t {
    fn from(item: BtSspVariant) -> Self {
        item.to_u32().unwrap_or_else(|| 0)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtBondState {
    NotBonded = 0,
    Bonding,
    Bonded,
}

impl From<bindings::bt_bond_state_t> for BtBondState {
    fn from(item: bindings::bt_bond_state_t) -> Self {
        BtBondState::from_u32(item).unwrap_or_else(|| BtBondState::NotBonded)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtConnectionState {
    NotConnected = 0,
    ConnectedOnly = 1,
    EncryptedBredr = 3,
    EncryptedLe = 5,
}

impl From<i32> for BtConnectionState {
    fn from(item: i32) -> Self {
        let fallback = if item > 0 {
            BtConnectionState::ConnectedOnly
        } else {
            BtConnectionState::NotConnected
        };

        BtConnectionState::from_i32(item).unwrap_or(fallback)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtAclState {
    Connected = 0,
    Disconnected,
}

impl From<bindings::bt_acl_state_t> for BtAclState {
    fn from(item: bindings::bt_acl_state_t) -> Self {
        BtAclState::from_u32(item).unwrap_or_else(|| BtAclState::Disconnected)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtDeviceType {
    Bredr,
    Ble,
    Dual,
    Unknown,
}

#[derive(Clone, Debug, Eq, Hash, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtPropertyType {
    BdName = 0x1,
    BdAddr,
    Uuids,
    ClassOfDevice,
    TypeOfDevice,
    ServiceRecord,
    AdapterScanMode,
    AdapterBondedDevices,
    AdapterDiscoverableTimeout,
    RemoteFriendlyName,
    RemoteRssi,
    RemoteVersionInfo,
    LocalLeFeatures,
    LocalIoCaps,
    LocalIoCapsBle,
    DynamicAudioBuffer,

    Unknown = 0xFE,
    RemoteDeviceTimestamp = 0xFF,
}

impl From<u32> for BtPropertyType {
    fn from(item: u32) -> Self {
        BtPropertyType::from_u32(item).unwrap_or_else(|| BtPropertyType::Unknown)
    }
}

impl From<BtPropertyType> for u32 {
    fn from(item: BtPropertyType) -> Self {
        item.to_u32().unwrap_or_else(|| 0)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtDiscoveryState {
    Stopped = 0x0,
    Started,
}

impl From<u32> for BtDiscoveryState {
    fn from(item: u32) -> Self {
        BtDiscoveryState::from_u32(item).unwrap_or_else(|| BtDiscoveryState::Stopped)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtStatus {
    Success = 0,
    Fail,
    NotReady,
    NoMemory,
    Busy,
    Done,
    Unsupported,
    InvalidParam,
    Unhandled,
    AuthFailure,
    RemoteDeviceDown,
    AuthRejected,
    JniEnvironmentError,
    JniThreadAttachError,
    WakeLockError,

    // Any statuses that couldn't be cleanly converted
    Unknown = 0xff,
}

pub fn ascii_to_string(data: &[u8], length: usize) -> String {
    // We need to reslice data because from_utf8 tries to interpret the
    // whole slice and not just what is before the null terminated portion
    let ascii = data
        .iter()
        .enumerate()
        .take_while(|&(pos, &c)| c != 0 && pos < length)
        .map(|(_pos, &x)| x.clone())
        .collect::<Vec<u8>>();

    return String::from_utf8(ascii).unwrap_or_default();
}

fn u32_from_bytes(item: &[u8]) -> u32 {
    let mut u: [u8; 4] = [0; 4];
    let len = std::cmp::min(item.len(), 4);
    u[0..len].copy_from_slice(&item);
    u32::from_ne_bytes(u)
}

impl From<bindings::bt_status_t> for BtStatus {
    fn from(item: bindings::bt_status_t) -> Self {
        match BtStatus::from_u32(item) {
            Some(x) => x,
            _ => BtStatus::Unknown,
        }
    }
}

impl From<bindings::bt_bdname_t> for String {
    fn from(item: bindings::bt_bdname_t) -> Self {
        ascii_to_string(&item.name, item.name.len())
    }
}

#[derive(Debug, Clone)]
pub struct BtServiceRecord {
    pub uuid: bindings::bluetooth::Uuid,
    pub channel: u16,
    pub name: String,
}

impl From<bindings::bt_service_record_t> for BtServiceRecord {
    fn from(item: bindings::bt_service_record_t) -> Self {
        let name = item.name.iter().map(|&x| x.clone() as u8).collect::<Vec<u8>>();

        BtServiceRecord {
            uuid: item.uuid,
            channel: item.channel,
            name: ascii_to_string(name.as_slice(), name.len()),
        }
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtScanMode {
    None_,
    Connectable,
    ConnectableDiscoverable,
}

impl From<bindings::bt_scan_mode_t> for BtScanMode {
    fn from(item: bindings::bt_scan_mode_t) -> Self {
        BtScanMode::from_u32(item).unwrap_or(BtScanMode::None_)
    }
}

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtIoCap {
    Out,
    InOut,
    In,
    None_,
    KbDisp,
    Max,
    Unknown = 0xff,
}

impl From<bindings::bt_io_cap_t> for BtIoCap {
    fn from(item: bindings::bt_io_cap_t) -> Self {
        BtIoCap::from_u32(item).unwrap_or(BtIoCap::Unknown)
    }
}

pub type BtHciErrorCode = u8;
pub type BtLocalLeFeatures = bindings::bt_local_le_features_t;
pub type BtPinCode = bindings::bt_pin_code_t;
pub type BtRemoteVersion = bindings::bt_remote_version_t;
pub type Uuid = bindings::bluetooth::Uuid;
pub type Uuid128Bit = bindings::bluetooth::Uuid_UUID128Bit;

/// All supported Bluetooth properties after conversion.
#[derive(Debug, Clone)]
pub enum BluetoothProperty {
    BdName(String),
    BdAddr(RawAddress),
    Uuids(Vec<Uuid>),
    ClassOfDevice(u32),
    TypeOfDevice(BtDeviceType),
    ServiceRecord(BtServiceRecord),
    AdapterScanMode(BtScanMode),
    AdapterBondedDevices(Vec<RawAddress>),
    AdapterDiscoverableTimeout(u32),
    RemoteFriendlyName(String),
    RemoteRssi(i8),
    RemoteVersionInfo(BtRemoteVersion),
    LocalLeFeatures(BtLocalLeFeatures),
    LocalIoCaps(BtIoCap),
    LocalIoCapsBle(BtIoCap),
    DynamicAudioBuffer(),
    RemoteDeviceTimestamp(),

    Unknown(),
}

/// Wherever names are sent in bindings::bt_property_t, the size of the character
/// arrays are 256. Keep one extra byte for null termination.
const PROPERTY_NAME_MAX: usize = 255;

impl BluetoothProperty {
    pub fn get_type(&self) -> BtPropertyType {
        match &*self {
            BluetoothProperty::BdName(_) => BtPropertyType::BdName,
            BluetoothProperty::BdAddr(_) => BtPropertyType::BdAddr,
            BluetoothProperty::Uuids(_) => BtPropertyType::Uuids,
            BluetoothProperty::ClassOfDevice(_) => BtPropertyType::ClassOfDevice,
            BluetoothProperty::TypeOfDevice(_) => BtPropertyType::TypeOfDevice,
            BluetoothProperty::ServiceRecord(_) => BtPropertyType::ServiceRecord,
            BluetoothProperty::AdapterScanMode(_) => BtPropertyType::AdapterScanMode,
            BluetoothProperty::AdapterBondedDevices(_) => BtPropertyType::AdapterBondedDevices,
            BluetoothProperty::AdapterDiscoverableTimeout(_) => {
                BtPropertyType::AdapterDiscoverableTimeout
            }
            BluetoothProperty::RemoteFriendlyName(_) => BtPropertyType::RemoteFriendlyName,
            BluetoothProperty::RemoteRssi(_) => BtPropertyType::RemoteRssi,
            BluetoothProperty::RemoteVersionInfo(_) => BtPropertyType::RemoteVersionInfo,
            BluetoothProperty::LocalLeFeatures(_) => BtPropertyType::LocalLeFeatures,
            BluetoothProperty::LocalIoCaps(_) => BtPropertyType::LocalIoCaps,
            BluetoothProperty::LocalIoCapsBle(_) => BtPropertyType::LocalIoCapsBle,
            BluetoothProperty::DynamicAudioBuffer() => BtPropertyType::DynamicAudioBuffer,
            BluetoothProperty::RemoteDeviceTimestamp() => BtPropertyType::RemoteDeviceTimestamp,
            BluetoothProperty::Unknown() => BtPropertyType::Unknown,
        }
    }

    fn get_len(&self) -> usize {
        match &*self {
            BluetoothProperty::BdName(name) => cmp::min(PROPERTY_NAME_MAX, name.len() + 1),
            BluetoothProperty::BdAddr(addr) => addr.val.len(),
            BluetoothProperty::Uuids(uulist) => uulist.len() * mem::size_of::<Uuid>(),
            BluetoothProperty::ClassOfDevice(_) => mem::size_of::<u32>(),
            BluetoothProperty::TypeOfDevice(_) => mem::size_of::<BtDeviceType>(),
            BluetoothProperty::ServiceRecord(rec) => {
                mem::size_of::<BtServiceRecord>() + cmp::min(PROPERTY_NAME_MAX, rec.name.len() + 1)
            }
            BluetoothProperty::AdapterScanMode(_) => mem::size_of::<BtScanMode>(),
            BluetoothProperty::AdapterBondedDevices(devlist) => {
                devlist.len() * mem::size_of::<RawAddress>()
            }
            BluetoothProperty::AdapterDiscoverableTimeout(_) => mem::size_of::<u32>(),
            BluetoothProperty::RemoteFriendlyName(name) => {
                cmp::min(PROPERTY_NAME_MAX, name.len() + 1)
            }
            BluetoothProperty::RemoteRssi(_) => mem::size_of::<i8>(),
            BluetoothProperty::RemoteVersionInfo(_) => mem::size_of::<BtRemoteVersion>(),
            BluetoothProperty::LocalLeFeatures(_) => mem::size_of::<BtLocalLeFeatures>(),
            BluetoothProperty::LocalIoCaps(_) => mem::size_of::<BtIoCap>(),
            BluetoothProperty::LocalIoCapsBle(_) => mem::size_of::<BtIoCap>(),

            // TODO(abps) - Figure out sizes for these
            BluetoothProperty::DynamicAudioBuffer() => 0,
            BluetoothProperty::RemoteDeviceTimestamp() => 0,
            BluetoothProperty::Unknown() => 0,
        }
    }

    /// Given a mutable array, this will copy the data to that array and return a
    /// pointer to it.
    ///
    /// The lifetime of the returned pointer is tied to that of the slice given.
    fn get_data_ptr<'a>(&'a self, data: &'a mut [u8]) -> *mut u8 {
        let len = self.get_len();
        match &*self {
            BluetoothProperty::BdName(name) => {
                let copy_len = len - 1;
                data[0..copy_len].copy_from_slice(&name.as_bytes()[0..copy_len]);
                data[copy_len] = 0;
            }
            BluetoothProperty::BdAddr(addr) => {
                data.copy_from_slice(&addr.val);
            }
            BluetoothProperty::Uuids(uulist) => {
                for (idx, &uuid) in uulist.iter().enumerate() {
                    let start = idx * mem::size_of::<Uuid>();
                    let end = start + mem::size_of::<Uuid>();
                    data[start..end].copy_from_slice(&uuid.uu);
                }
            }
            BluetoothProperty::ClassOfDevice(cod) => {
                data.copy_from_slice(&cod.to_ne_bytes());
            }
            BluetoothProperty::TypeOfDevice(tod) => {
                data.copy_from_slice(&BtDeviceType::to_u32(tod).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::ServiceRecord(sr) => {
                // Do an unsafe cast to binding:: type and assign the values
                // The underlying memory location is provided by |data| which will
                // have enough space because it uses get_len()
                let mut record =
                    unsafe { &mut *(data.as_mut_ptr() as *mut bindings::bt_service_record_t) };
                record.uuid = sr.uuid;
                record.channel = sr.channel;
                let name_len = len - mem::size_of::<BtServiceRecord>() - 1;
                record.name[0..name_len].copy_from_slice(
                    &(sr.name.as_bytes().iter().map(|x| *x as c_char).collect::<Vec<c_char>>())
                        [0..name_len],
                );
                record.name[name_len] = 0;
            }
            BluetoothProperty::AdapterScanMode(sm) => {
                data.copy_from_slice(&BtScanMode::to_u32(sm).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::AdapterBondedDevices(devlist) => {
                for (idx, &dev) in devlist.iter().enumerate() {
                    let start = idx * mem::size_of::<RawAddress>();
                    let end = idx + mem::size_of::<RawAddress>();
                    data[start..end].copy_from_slice(&dev.val);
                }
            }
            BluetoothProperty::AdapterDiscoverableTimeout(timeout) => {
                data.copy_from_slice(&timeout.to_ne_bytes());
            }
            BluetoothProperty::RemoteFriendlyName(name) => {
                let copy_len = len - 1;
                data[0..copy_len].copy_from_slice(&name.as_bytes()[0..copy_len]);
                data[copy_len] = 0;
            }
            BluetoothProperty::RemoteRssi(rssi) => {
                data[0] = *rssi as u8;
            }
            BluetoothProperty::RemoteVersionInfo(rvi) => {
                let ptr: *const BtRemoteVersion = rvi;
                let slice = unsafe {
                    std::slice::from_raw_parts(ptr as *mut u8, mem::size_of::<BtRemoteVersion>())
                };
                data.copy_from_slice(&slice);
            }
            BluetoothProperty::LocalLeFeatures(llf) => {
                let ptr: *const BtLocalLeFeatures = llf;
                let slice = unsafe {
                    std::slice::from_raw_parts(ptr as *mut u8, mem::size_of::<BtLocalLeFeatures>())
                };
                data.copy_from_slice(&slice);
            }
            BluetoothProperty::LocalIoCaps(iocap) => {
                data.copy_from_slice(&BtIoCap::to_u32(iocap).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::LocalIoCapsBle(iocap) => {
                data.copy_from_slice(&BtIoCap::to_u32(iocap).unwrap_or_default().to_ne_bytes());
            }
            BluetoothProperty::DynamicAudioBuffer() => (),
            BluetoothProperty::RemoteDeviceTimestamp() => (),
            BluetoothProperty::Unknown() => (),
        };

        data.as_mut_ptr()
    }
}

// TODO(abps) - Check that sizes are correct when given a BtProperty
impl From<bindings::bt_property_t> for BluetoothProperty {
    fn from(prop: bindings::bt_property_t) -> Self {
        let slice: &[u8] =
            unsafe { std::slice::from_raw_parts(prop.val as *mut u8, prop.len as usize) };
        let len = prop.len as usize;

        match BtPropertyType::from(prop.type_) {
            BtPropertyType::BdName => BluetoothProperty::BdName(ascii_to_string(slice, len)),
            BtPropertyType::BdAddr => {
                BluetoothProperty::BdAddr(RawAddress::from_bytes(slice).unwrap_or_default())
            }
            BtPropertyType::Uuids => {
                let count = len / mem::size_of::<Uuid>();
                BluetoothProperty::Uuids(ptr_to_vec(prop.val as *mut Uuid, count))
            }
            BtPropertyType::ClassOfDevice => {
                BluetoothProperty::ClassOfDevice(u32_from_bytes(slice))
            }
            BtPropertyType::TypeOfDevice => BluetoothProperty::TypeOfDevice(
                BtDeviceType::from_u32(u32_from_bytes(slice)).unwrap_or(BtDeviceType::Bredr),
            ),
            BtPropertyType::ServiceRecord => {
                let v = unsafe { *(prop.val as *const bindings::bt_service_record_t) };
                BluetoothProperty::ServiceRecord(BtServiceRecord::from(v))
            }
            BtPropertyType::AdapterScanMode => BluetoothProperty::AdapterScanMode(
                BtScanMode::from_u32(u32_from_bytes(slice)).unwrap_or(BtScanMode::None_),
            ),
            BtPropertyType::AdapterBondedDevices => {
                let count = len / mem::size_of::<RawAddress>();
                BluetoothProperty::AdapterBondedDevices(ptr_to_vec(
                    prop.val as *mut RawAddress,
                    count,
                ))
            }
            BtPropertyType::AdapterDiscoverableTimeout => {
                BluetoothProperty::AdapterDiscoverableTimeout(u32_from_bytes(slice))
            }
            BtPropertyType::RemoteFriendlyName => {
                BluetoothProperty::RemoteFriendlyName(ascii_to_string(slice, len))
            }
            BtPropertyType::RemoteRssi => BluetoothProperty::RemoteRssi(slice[0] as i8),
            BtPropertyType::RemoteVersionInfo => {
                let v = unsafe { *(prop.val as *const BtRemoteVersion) };
                BluetoothProperty::RemoteVersionInfo(v.clone())
            }
            BtPropertyType::LocalLeFeatures => {
                let v = unsafe { *(prop.val as *const BtLocalLeFeatures) };
                BluetoothProperty::LocalLeFeatures(v.clone())
            }
            BtPropertyType::LocalIoCaps => BluetoothProperty::LocalIoCaps(
                BtIoCap::from_u32(u32_from_bytes(slice)).unwrap_or(BtIoCap::Unknown),
            ),
            BtPropertyType::LocalIoCapsBle => BluetoothProperty::LocalIoCapsBle(
                BtIoCap::from_u32(u32_from_bytes(slice)).unwrap_or(BtIoCap::Unknown),
            ),

            // TODO(abps) - Figure out if these values should actually have contents
            BtPropertyType::DynamicAudioBuffer => BluetoothProperty::DynamicAudioBuffer(),
            BtPropertyType::RemoteDeviceTimestamp => BluetoothProperty::RemoteDeviceTimestamp(),
            _ => BluetoothProperty::Unknown(),
        }
    }
}

impl From<BluetoothProperty> for (Box<[u8]>, bindings::bt_property_t) {
    fn from(prop: BluetoothProperty) -> Self {
        let dvec: Vec<u8> = vec![0; prop.get_len()];
        let mut data: Box<[u8]> = dvec.into_boxed_slice();
        let prop = bindings::bt_property_t {
            type_: prop.get_type().into(),
            len: prop.get_len() as i32,
            val: prop.get_data_ptr(&mut data) as *mut std::os::raw::c_void,
        };

        (data, prop)
    }
}

pub enum SupportedProfiles {
    HidHost,
    Hfp,
    A2dp,
    Gatt,
    Sdp,
}

impl From<SupportedProfiles> for Vec<u8> {
    fn from(item: SupportedProfiles) -> Self {
        match item {
            SupportedProfiles::HidHost => "hidhost",
            SupportedProfiles::Hfp => "hfp",
            SupportedProfiles::A2dp => "a2dp",
            SupportedProfiles::Gatt => "gatt",
            SupportedProfiles::Sdp => "sdp",
        }
        .bytes()
        .chain("\0".bytes())
        .collect::<Vec<u8>>()
    }
}

#[cxx::bridge(namespace = bluetooth::topshim::rust)]
mod ffi {
    #[derive(Debug, Copy, Clone)]
    pub struct RustRawAddress {
        address: [u8; 6],
    }

    unsafe extern "C++" {
        include!("btif/btif_shim.h");

        // For converting init flags from Vec<String> to const char **
        type InitFlags;

        // Convert flgas into an InitFlags object
        fn ConvertFlags(flags: Vec<String>) -> UniquePtr<InitFlags>;
        fn GetFlagsPtr(self: &InitFlags) -> *mut *const c_char;
    }
}

// Export the raw address type directly from the bindings
pub type FfiAddress = bindings::RawAddress;

/// A shared address structure that has the same representation as
/// bindings::RawAddress. Macros `deref_ffi_address` and `cast_to_ffi_address`
/// are used for transforming between bindings::RawAddress at ffi boundaries.
#[derive(Copy, Clone, Hash, Eq, PartialEq)]
#[repr(C)]
pub struct RawAddress {
    pub val: [u8; 6],
}

impl Debug for RawAddress {
    fn fmt(&self, f: &mut Formatter<'_>) -> Result {
        f.write_fmt(format_args!(
            "{:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
            self.val[0], self.val[1], self.val[2], self.val[3], self.val[4], self.val[5]
        ))
    }
}

impl Default for RawAddress {
    fn default() -> Self {
        Self { val: [0; 6] }
    }
}

impl ToString for RawAddress {
    fn to_string(&self) -> String {
        String::from(format!(
            "{:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
            self.val[0], self.val[1], self.val[2], self.val[3], self.val[4], self.val[5]
        ))
    }
}

impl RawAddress {
    /// Constructs a RawAddress from a slice of 6 bytes.
    pub fn from_bytes(raw_addr: &[u8]) -> Option<RawAddress> {
        if raw_addr.len() != 6 {
            return None;
        }
        let mut raw: [u8; 6] = [0; 6];
        raw.copy_from_slice(raw_addr);
        return Some(RawAddress { val: raw });
    }

    pub fn from_string<S: Into<String>>(addr: S) -> Option<RawAddress> {
        let addr: String = addr.into();
        let s = addr.split(':').collect::<Vec<&str>>();

        if s.len() != 6 {
            return None;
        }

        let mut raw: [u8; 6] = [0; 6];
        for i in 0..s.len() {
            raw[i] = match u8::from_str_radix(s[i], 16) {
                Ok(res) => res,
                Err(_) => {
                    return None;
                }
            };
        }

        Some(RawAddress { val: raw })
    }

    pub fn to_byte_arr(&self) -> [u8; 6] {
        self.val.clone()
    }
}

#[macro_export]
macro_rules! deref_ffi_address {
    ($ffi_addr:ident) => {
        *($ffi_addr as *mut RawAddress)
    };
}

#[macro_export]
macro_rules! deref_const_ffi_address {
    ($ffi_addr:ident) => {
        *($ffi_addr as *const RawAddress)
    };
}

#[macro_export]
macro_rules! cast_to_ffi_address {
    ($raw_addr:expr) => {
        $raw_addr as *mut FfiAddress
    };
}

#[macro_export]
macro_rules! cast_to_const_ffi_address {
    ($raw_addr:expr) => {
        $raw_addr as *const FfiAddress
    };
}

/// An enum representing `bt_callbacks_t` from btif.
#[derive(Clone, Debug)]
pub enum BaseCallbacks {
    AdapterState(BtState),
    AdapterProperties(BtStatus, i32, Vec<BluetoothProperty>),
    RemoteDeviceProperties(BtStatus, RawAddress, i32, Vec<BluetoothProperty>),
    DeviceFound(i32, Vec<BluetoothProperty>),
    DiscoveryState(BtDiscoveryState),
    PinRequest(RawAddress, String, u32, bool),
    SspRequest(RawAddress, String, u32, BtSspVariant, u32),
    BondState(BtStatus, RawAddress, BtBondState, i32),
    AddressConsolidate(RawAddress, RawAddress),
    LeAddressAssociate(RawAddress, RawAddress),
    AclState(BtStatus, RawAddress, BtAclState, BtTransport, BtHciErrorCode),
    // Unimplemented so far:
    // thread_evt_cb
    // dut_mode_recv_cb
    // le_test_mode_cb
    // energy_info_cb
    // link_quality_report_cb
    // generate_local_oob_data_cb
    // switch_buffer_size_cb
    // switch_codec_cb
}

pub struct BaseCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(BaseCallbacks) + Send>,
}

type BaseCb = Arc<Mutex<BaseCallbacksDispatcher>>;

cb_variant!(BaseCb, adapter_state_cb -> BaseCallbacks::AdapterState, u32 -> BtState);
cb_variant!(BaseCb, adapter_properties_cb -> BaseCallbacks::AdapterProperties,
u32 -> BtStatus, i32, *mut bindings::bt_property_t, {
    let _2 = ptr_to_vec(_2, _1 as usize);
});
cb_variant!(BaseCb, remote_device_properties_cb -> BaseCallbacks::RemoteDeviceProperties,
u32 -> BtStatus, *mut FfiAddress -> RawAddress, i32, *mut bindings::bt_property_t, {
    let _1 = unsafe { *(_1 as *const RawAddress) };
    let _3 = ptr_to_vec(_3, _2 as usize);
});
cb_variant!(BaseCb, device_found_cb -> BaseCallbacks::DeviceFound,
i32, *mut bindings::bt_property_t, {
    let _1 = ptr_to_vec(_1, _0 as usize);
});
cb_variant!(BaseCb, discovery_state_cb -> BaseCallbacks::DiscoveryState,
    bindings::bt_discovery_state_t -> BtDiscoveryState);
cb_variant!(BaseCb, pin_request_cb -> BaseCallbacks::PinRequest,
*mut FfiAddress, *mut bindings::bt_bdname_t, u32, bool, {
    let _0 = unsafe { *(_0 as *const RawAddress)};
    let _1 = String::from(unsafe{*_1});
});
cb_variant!(BaseCb, ssp_request_cb -> BaseCallbacks::SspRequest,
*mut FfiAddress, *mut bindings::bt_bdname_t, u32, bindings::bt_ssp_variant_t -> BtSspVariant, u32, {
    let _0 = unsafe { *(_0 as *const RawAddress) };
    let _1 = String::from(unsafe{*_1});
});
cb_variant!(BaseCb, bond_state_cb -> BaseCallbacks::BondState,
u32 -> BtStatus, *mut FfiAddress, bindings::bt_bond_state_t -> BtBondState, i32, {
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

cb_variant!(BaseCb, address_consolidate_cb -> BaseCallbacks::AddressConsolidate,
*mut FfiAddress, *mut FfiAddress, {
    let _0 = unsafe { *(_0 as *const RawAddress) };
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

cb_variant!(BaseCb, le_address_associate_cb -> BaseCallbacks::LeAddressAssociate,
*mut FfiAddress, *mut FfiAddress, {
    let _0 = unsafe { *(_0 as *const RawAddress) };
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

cb_variant!(BaseCb, acl_state_cb -> BaseCallbacks::AclState,
u32 -> BtStatus, *mut FfiAddress, bindings::bt_acl_state_t -> BtAclState, i32 -> BtTransport, bindings::bt_hci_error_code_t -> BtHciErrorCode, {
    let _1 = unsafe { *(_1 as *const RawAddress) };
});

struct RawInterfaceWrapper {
    pub raw: *const bindings::bt_interface_t,
}

unsafe impl Send for RawInterfaceWrapper {}

/// Macro to call functions via function pointers. Expects the self object to
/// have a raw interface wrapper at `self.internal`. The actual function call is
/// marked unsafe since it will need to dereference a C object. This can cause
/// segfaults if not validated beforehand.
///
/// Example:
///     ccall!(self, foobar, arg1, arg2)
///     Expands to: unsafe {((*self.internal.raw).foobar.unwrap())(arg1, arg2)}
#[macro_export]
macro_rules! ccall {
    ($self:ident,$fn_name:ident) => {
        unsafe {
            ((*$self.internal.raw).$fn_name.unwrap())()
        }
    };
    ($self:ident,$fn_name:ident, $($args:expr),*) => {
        unsafe {
            ((*$self.internal.raw).$fn_name.unwrap())($($args),*)
        }
    };
}

/// Macro to call const functions via cxx. Expects the self object to have the
/// cxx object to be called at `self.internal_cxx`.
///
/// Example:
///     cxxcall!(self, foobar, arg1, arg2)
///     Expands to: self.internal_cxx.foobar(arg1, arg2)
#[macro_export]
macro_rules! cxxcall {
    ($self:expr,$fn_name:ident) => {
        $self.internal_cxx.$fn_name()
    };
    ($self:expr,$fn_name:ident, $($args:expr),*) => {
        $self.internal_cxx.$fn_name($($args),*)
    };
}

/// Macro to call mutable functions via cxx. Mutable functions are always
/// required to be defined with `self: Pin<&mut Self>`. The self object must
/// have the cxx object at `self.internal_cxx`.
///
/// Example:
///     mutcxxcall!(self, foobar, arg1, arg2)
///     Expands to: self.internal_cxx.pin_mut().foobar(arg1, arg2)
#[macro_export]
macro_rules! mutcxxcall {
    ($self:expr,$fn_name:ident) => {
        $self.internal_cxx.pin_mut().$fn_name()
    };
    ($self:expr,$fn_name:ident, $($args:expr),*) => {
        $self.internal_cxx.pin_mut().$fn_name($($args),*)
    };
}

/// Rust wrapper around `bt_interface_t`.
pub struct BluetoothInterface {
    internal: RawInterfaceWrapper,

    /// Set to true after `initialize` is called.
    pub is_init: bool,

    // Need to take ownership of callbacks so it doesn't get freed after init
    callbacks: Option<Box<bindings::bt_callbacks_t>>,
}

impl BluetoothInterface {
    pub fn is_initialized(&self) -> bool {
        self.is_init
    }

    /// Initialize the Bluetooth interface by setting up the underlying interface.
    ///
    /// # Arguments
    ///
    /// * `callbacks` - Dispatcher struct that accepts [`BaseCallbacks`]
    /// * `init_flags` - List of flags sent to libbluetooth for init.
    pub fn initialize(
        &mut self,
        callbacks: BaseCallbacksDispatcher,
        init_flags: Vec<String>,
    ) -> bool {
        // Init flags need to be converted from string to null terminated bytes
        let converted: cxx::UniquePtr<ffi::InitFlags> = ffi::ConvertFlags(init_flags);
        let flags = (*converted).GetFlagsPtr();

        if get_dispatchers().lock().unwrap().set::<BaseCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for BaseCallbacks but it already existed");
        }

        // Fill up callbacks struct to pass to init function (will be copied so
        // no need to worry about ownership)
        let mut callbacks = Box::new(bindings::bt_callbacks_t {
            size: 16 * 8,
            adapter_state_changed_cb: Some(adapter_state_cb),
            adapter_properties_cb: Some(adapter_properties_cb),
            remote_device_properties_cb: Some(remote_device_properties_cb),
            device_found_cb: Some(device_found_cb),
            discovery_state_changed_cb: Some(discovery_state_cb),
            pin_request_cb: Some(pin_request_cb),
            ssp_request_cb: Some(ssp_request_cb),
            bond_state_changed_cb: Some(bond_state_cb),
            address_consolidate_cb: Some(address_consolidate_cb),
            le_address_associate_cb: Some(le_address_associate_cb),
            acl_state_changed_cb: Some(acl_state_cb),
            thread_evt_cb: None,
            dut_mode_recv_cb: None,
            le_test_mode_cb: None,
            energy_info_cb: None,
            link_quality_report_cb: None,
            generate_local_oob_data_cb: None,
            switch_buffer_size_cb: None,
            switch_codec_cb: None,
        });

        let rawcb: *mut bindings::bt_callbacks_t = &mut *callbacks;

        let (guest_mode, is_common_criteria_mode, config_compare_result, is_atv) =
            (false, false, 0, false);

        let init = ccall!(
            self,
            init,
            rawcb,
            guest_mode,
            is_common_criteria_mode,
            config_compare_result,
            flags,
            is_atv,
            std::ptr::null()
        );

        self.is_init = init == 0;
        self.callbacks = Some(callbacks);

        return self.is_init;
    }

    pub fn cleanup(&self) {
        ccall!(self, cleanup)
    }

    pub fn enable(&self) -> i32 {
        ccall!(self, enable)
    }

    pub fn disable(&self) -> i32 {
        ccall!(self, disable)
    }

    pub fn get_adapter_properties(&self) -> i32 {
        ccall!(self, get_adapter_properties)
    }

    pub fn get_adapter_property(&self, prop: BtPropertyType) -> i32 {
        let converted_type = bindings::bt_property_type_t::from(prop);
        ccall!(self, get_adapter_property, converted_type)
    }

    pub fn set_adapter_property(&self, prop: BluetoothProperty) -> i32 {
        let prop_pair: (Box<[u8]>, bindings::bt_property_t) = prop.into();
        ccall!(self, set_adapter_property, &prop_pair.1)
    }

    pub fn get_remote_device_properties(&self, addr: &mut RawAddress) -> i32 {
        let ffi_addr = cast_to_ffi_address!(addr as *mut RawAddress);
        ccall!(self, get_remote_device_properties, ffi_addr)
    }

    pub fn get_remote_device_property(
        &self,
        addr: &mut RawAddress,
        prop_type: BtPropertyType,
    ) -> i32 {
        let converted_type = bindings::bt_property_type_t::from(prop_type);
        let ffi_addr = cast_to_ffi_address!(addr as *mut RawAddress);
        ccall!(self, get_remote_device_property, ffi_addr, converted_type)
    }

    pub fn set_remote_device_property(
        &self,
        addr: &mut RawAddress,
        prop: BluetoothProperty,
    ) -> i32 {
        let prop_pair: (Box<[u8]>, bindings::bt_property_t) = prop.into();
        let ffi_addr = cast_to_ffi_address!(addr as *const RawAddress);
        ccall!(self, set_remote_device_property, ffi_addr, &prop_pair.1)
    }

    pub fn get_remote_services(&self, addr: &mut RawAddress, transport: BtTransport) -> i32 {
        let ffi_addr = cast_to_ffi_address!(addr as *const RawAddress);
        ccall!(self, get_remote_services, ffi_addr, transport.to_i32().unwrap())
    }

    pub fn start_discovery(&self) -> i32 {
        ccall!(self, start_discovery)
    }

    pub fn cancel_discovery(&self) -> i32 {
        ccall!(self, cancel_discovery)
    }

    pub fn create_bond(&self, addr: &RawAddress, transport: BtTransport) -> i32 {
        let ctransport: i32 = transport.into();
        let ffi_addr = cast_to_const_ffi_address!(addr as *const RawAddress);
        ccall!(self, create_bond, ffi_addr, ctransport)
    }

    pub fn remove_bond(&self, addr: &RawAddress) -> i32 {
        let ffi_addr = cast_to_const_ffi_address!(addr as *const RawAddress);
        ccall!(self, remove_bond, ffi_addr)
    }

    pub fn cancel_bond(&self, addr: &RawAddress) -> i32 {
        let ffi_addr = cast_to_const_ffi_address!(addr as *const RawAddress);
        ccall!(self, cancel_bond, ffi_addr)
    }

    pub fn get_connection_state(&self, addr: &RawAddress) -> u32 {
        let ffi_addr = cast_to_const_ffi_address!(addr as *const RawAddress);
        ccall!(self, get_connection_state, ffi_addr).to_u32().unwrap()
    }

    pub fn pin_reply(
        &self,
        addr: &RawAddress,
        accept: u8,
        pin_len: u8,
        pin_code: &mut BtPinCode,
    ) -> i32 {
        let ffi_addr = cast_to_const_ffi_address!(addr as *const RawAddress);
        ccall!(self, pin_reply, ffi_addr, accept, pin_len, pin_code)
    }

    pub fn ssp_reply(
        &self,
        addr: &RawAddress,
        variant: BtSspVariant,
        accept: u8,
        passkey: u32,
    ) -> i32 {
        let cvariant = bindings::bt_ssp_variant_t::from(variant);
        let ffi_addr = cast_to_const_ffi_address!(addr as *const RawAddress);
        ccall!(self, ssp_reply, ffi_addr, cvariant, accept, passkey)
    }

    pub fn clear_event_filter(&self) -> i32 {
        ccall!(self, clear_event_filter)
    }

    pub(crate) fn get_profile_interface(
        &self,
        profile: SupportedProfiles,
    ) -> *const std::os::raw::c_void {
        let cprofile = Vec::<u8>::from(profile);
        ccall!(
            self,
            get_profile_interface,
            cprofile.as_slice().as_ptr() as *const std::os::raw::c_char
        )
    }

    pub(crate) fn as_raw_ptr(&self) -> *const u8 {
        self.internal.raw as *const u8
    }
}

pub fn get_btinterface() -> Option<BluetoothInterface> {
    let mut ret: Option<BluetoothInterface> = None;
    let mut ifptr: *const bindings::bt_interface_t = std::ptr::null();

    unsafe {
        if bindings::hal_util_load_bt_library(&mut ifptr) == 0 {
            ret = Some(BluetoothInterface {
                internal: RawInterfaceWrapper { raw: ifptr },
                is_init: false,
                callbacks: None,
            });
        }
    }

    ret
}

// Turns C-array T[] to Vec<U>.
pub(crate) fn ptr_to_vec<T: Copy, U: From<T>>(start: *const T, length: usize) -> Vec<U> {
    unsafe { (0..length).map(|i| U::from(*start.offset(i as isize))).collect::<Vec<U>>() }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::mem;

    #[test]
    fn test_addr_size() {
        assert_eq!(mem::size_of::<RawAddress>(), mem::size_of::<FfiAddress>());
    }

    #[test]
    fn test_offset() {
        let r = RawAddress { val: [1, 2, 3, 4, 5, 6] };
        let f = FfiAddress { address: [1, 2, 3, 4, 5, 6] };
        assert_eq!(
            &f as *const _ as usize - &f.address as *const _ as usize,
            &r as *const _ as usize - &r.val as *const _ as usize
        );
    }

    #[test]
    fn test_alignment() {
        assert_eq!(std::mem::align_of::<RawAddress>(), std::mem::align_of::<FfiAddress>());
    }

    fn make_bdname_from_slice(slice: &[u8]) -> bindings::bt_bdname_t {
        // Length of slice must be less than bd_name max
        assert!(slice.len() <= 249);

        let mut bdname = bindings::bt_bdname_t { name: [128; 249] };

        for (i, v) in slice.iter().enumerate() {
            bdname.name[i] = v.clone();
        }

        bdname
    }

    #[test]
    fn test_bdname_conversions() {
        let hello_bdname = make_bdname_from_slice(&[72, 69, 76, 76, 79, 0]);
        assert_eq!("HELLO".to_string(), String::from(hello_bdname));

        let empty_bdname = make_bdname_from_slice(&[0]);
        assert_eq!("".to_string(), String::from(empty_bdname));

        let no_nullterm_bdname = make_bdname_from_slice(&[72, 69, 76, 76, 79]);
        assert_eq!("".to_string(), String::from(no_nullterm_bdname));

        let invalid_bdname = make_bdname_from_slice(&[128; 249]);
        assert_eq!("".to_string(), String::from(invalid_bdname));
    }

    #[test]
    fn test_ptr_to_vec() {
        let arr: [i32; 3] = [1, 2, 3];
        let vec: Vec<i32> = ptr_to_vec(arr.as_ptr(), arr.len());
        let expected: Vec<i32> = vec![1, 2, 3];
        assert_eq!(expected, vec);
    }

    #[test]
    fn test_property_with_string_conversions() {
        {
            let bdname = BluetoothProperty::BdName("FooBar".into());
            let prop_pair: (Box<[u8]>, bindings::bt_property_t) = bdname.into();
            let converted: BluetoothProperty = prop_pair.1.into();
            assert!(match converted {
                BluetoothProperty::BdName(name) => "FooBar".to_string() == name,
                _ => false,
            });
        }

        {
            let orig_record = BtServiceRecord {
                uuid: Uuid { uu: [0; 16] },
                channel: 3,
                name: "FooBar".to_string(),
            };
            let service_record = BluetoothProperty::ServiceRecord(orig_record.clone());
            let prop_pair: (Box<[u8]>, bindings::bt_property_t) = service_record.into();
            let converted: BluetoothProperty = prop_pair.1.into();
            assert!(match converted {
                BluetoothProperty::ServiceRecord(sr) => {
                    sr.uuid == orig_record.uuid
                        && sr.channel == orig_record.channel
                        && sr.name == orig_record.name
                }
                _ => false,
            });
        }

        {
            let rfname = BluetoothProperty::RemoteFriendlyName("FooBizz".into());
            let prop_pair: (Box<[u8]>, bindings::bt_property_t) = rfname.into();
            let converted: BluetoothProperty = prop_pair.1.into();
            assert!(match converted {
                BluetoothProperty::RemoteFriendlyName(name) => "FooBizz".to_string() == name,
                _ => false,
            });
        }
    }
}
