use num_traits::cast::{FromPrimitive, ToPrimitive};
use std::os::raw::c_char;
use std::sync::{Arc, Mutex};
use std::vec::Vec;

use crate::bindings::root as bindings;
use crate::btif::{
    ascii_to_string, ptr_to_vec, BluetoothInterface, BtStatus, FfiAddress, RawAddress,
    SupportedProfiles, Uuid,
};
use crate::topstack::get_dispatchers;
use crate::{cast_to_ffi_address, ccall, deref_const_ffi_address};
use topshim_macros::cb_variant;

#[derive(Clone, Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtSdpType {
    Raw = 0,
    MapMas,
    MapMns,
    PbapPse,
    PbapPce,
    OppServer,
    SapServer,
    Dip,
}

impl From<bindings::bluetooth_sdp_types> for BtSdpType {
    fn from(item: bindings::bluetooth_sdp_types) -> Self {
        BtSdpType::from_u32(item).unwrap_or(BtSdpType::Raw)
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpHeader {
    sdp_type: BtSdpType,
    uuid: Uuid,
    service_name_length: u32,
    service_name: String,
    rfcomm_channel_number: i32,
    l2cap_psm: i32,
    profile_version: i32,
}

impl From<bindings::_bluetooth_sdp_hdr> for BtSdpHeader {
    fn from(item: bindings::_bluetooth_sdp_hdr) -> Self {
        let service_name = ascii_to_string(
            unsafe {
                std::slice::from_raw_parts(
                    item.service_name as *const u8,
                    item.service_name_length as usize,
                )
            },
            item.service_name_length as usize,
        );
        BtSdpHeader {
            sdp_type: BtSdpType::from(item.type_),
            uuid: item.uuid,
            service_name_length: item.service_name_length,
            service_name,
            rfcomm_channel_number: item.rfcomm_channel_number,
            l2cap_psm: item.l2cap_psm,
            profile_version: item.profile_version,
        }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpHeaderOverlay {
    hdr: BtSdpHeader,
    user1_len: i32,
    user1_data: Vec<u8>,
    user2_len: i32,
    user2_data: Vec<u8>,
}

impl From<bindings::_bluetooth_sdp_hdr_overlay> for BtSdpHeaderOverlay {
    fn from(item: bindings::_bluetooth_sdp_hdr_overlay) -> Self {
        let user1_len = item.user1_ptr_len;
        let user1_data = unsafe {
            std::slice::from_raw_parts(item.user1_ptr, item.user1_ptr_len as usize).to_vec()
        };
        let user2_len = item.user2_ptr_len;
        let user2_data = unsafe {
            std::slice::from_raw_parts(item.user2_ptr, item.user2_ptr_len as usize).to_vec()
        };

        BtSdpHeaderOverlay {
            hdr: unsafe {
                BtSdpHeader::from(
                    *((&item as *const bindings::_bluetooth_sdp_hdr_overlay)
                        as *const bindings::_bluetooth_sdp_hdr),
                )
            },
            user1_len,
            user1_data,
            user2_len,
            user2_data,
        }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpMasRecord {
    hdr: BtSdpHeaderOverlay,
    mas_instance_id: u32,
    supported_features: u32,
    supported_message_types: u32,
}

impl From<bindings::_bluetooth_sdp_mas_record> for BtSdpMasRecord {
    fn from(item: bindings::_bluetooth_sdp_mas_record) -> Self {
        BtSdpMasRecord {
            hdr: BtSdpHeaderOverlay::from(item.hdr),
            mas_instance_id: item.mas_instance_id,
            supported_features: item.supported_features,
            supported_message_types: item.supported_message_types,
        }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpMnsRecord {
    hdr: BtSdpHeaderOverlay,
    supported_features: u32,
}

impl From<bindings::_bluetooth_sdp_mns_record> for BtSdpMnsRecord {
    fn from(item: bindings::_bluetooth_sdp_mns_record) -> Self {
        BtSdpMnsRecord {
            hdr: BtSdpHeaderOverlay::from(item.hdr),
            supported_features: item.supported_features,
        }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpPseRecord {
    hdr: BtSdpHeaderOverlay,
    supported_features: u32,
    supported_repositories: u32,
}

impl From<bindings::_bluetooth_sdp_pse_record> for BtSdpPseRecord {
    fn from(item: bindings::_bluetooth_sdp_pse_record) -> Self {
        BtSdpPseRecord {
            hdr: BtSdpHeaderOverlay::from(item.hdr),
            supported_features: item.supported_features,
            supported_repositories: item.supported_repositories,
        }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpPceRecord {
    hdr: BtSdpHeaderOverlay,
}

impl From<bindings::_bluetooth_sdp_pce_record> for BtSdpPceRecord {
    fn from(item: bindings::_bluetooth_sdp_pce_record) -> Self {
        BtSdpPceRecord { hdr: BtSdpHeaderOverlay::from(item.hdr) }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpOpsRecord {
    hdr: BtSdpHeaderOverlay,
    supported_formats_list_len: i32,
    supported_formats_list: [u8; 15usize],
}

impl From<bindings::_bluetooth_sdp_ops_record> for BtSdpOpsRecord {
    fn from(item: bindings::_bluetooth_sdp_ops_record) -> Self {
        BtSdpOpsRecord {
            hdr: BtSdpHeaderOverlay::from(item.hdr),
            supported_formats_list_len: item.supported_formats_list_len,
            supported_formats_list: item.supported_formats_list,
        }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpSapRecord {
    hdr: BtSdpHeaderOverlay,
}

impl From<bindings::_bluetooth_sdp_sap_record> for BtSdpSapRecord {
    fn from(item: bindings::_bluetooth_sdp_sap_record) -> Self {
        BtSdpSapRecord { hdr: BtSdpHeaderOverlay::from(item.hdr) }
    }
}

#[derive(Clone, Debug)]
pub struct BtSdpDipRecord {
    hdr: BtSdpHeaderOverlay,
    spec_id: u16,
    vendor: u16,
    vendor_id_source: u16,
    product: u16,
    version: u16,
    primary_record: bool,
}

impl From<bindings::_bluetooth_sdp_dip_record> for BtSdpDipRecord {
    fn from(item: bindings::_bluetooth_sdp_dip_record) -> Self {
        BtSdpDipRecord {
            hdr: BtSdpHeaderOverlay::from(item.hdr),
            spec_id: item.spec_id,
            vendor: item.vendor,
            vendor_id_source: item.vendor_id_source,
            product: item.product,
            version: item.version,
            primary_record: item.primary_record,
        }
    }
}

#[derive(Clone, Debug)]
pub enum BtSdpRecord {
    HeaderOverlay(BtSdpHeaderOverlay),
    MapMas(BtSdpMasRecord),
    MapMns(BtSdpMnsRecord),
    PbapPse(BtSdpPseRecord),
    PbapPce(BtSdpPceRecord),
    OppServer(BtSdpOpsRecord),
    SapServer(BtSdpSapRecord),
    Dip(BtSdpDipRecord),
}

impl From<bindings::bluetooth_sdp_record> for BtSdpRecord {
    fn from(item: bindings::bluetooth_sdp_record) -> Self {
        let sdp_type = unsafe { BtSdpType::from(item.hdr.type_) };

        match sdp_type {
            BtSdpType::Raw => unsafe {
                BtSdpRecord::HeaderOverlay(BtSdpHeaderOverlay::from(item.hdr))
            },
            BtSdpType::MapMas => unsafe { BtSdpRecord::MapMas(BtSdpMasRecord::from(item.mas)) },
            BtSdpType::MapMns => unsafe { BtSdpRecord::MapMns(BtSdpMnsRecord::from(item.mns)) },
            BtSdpType::PbapPse => unsafe { BtSdpRecord::PbapPse(BtSdpPseRecord::from(item.pse)) },
            BtSdpType::PbapPce => unsafe { BtSdpRecord::PbapPce(BtSdpPceRecord::from(item.pce)) },
            BtSdpType::OppServer => unsafe {
                BtSdpRecord::OppServer(BtSdpOpsRecord::from(item.ops))
            },
            BtSdpType::SapServer => unsafe {
                BtSdpRecord::SapServer(BtSdpSapRecord::from(item.sap))
            },
            BtSdpType::Dip => unsafe { BtSdpRecord::Dip(BtSdpDipRecord::from(item.dip)) },
        }
    }
}

impl BtSdpRecord {
    fn convert_header<'a>(hdr: &'a mut BtSdpHeaderOverlay) -> bindings::bluetooth_sdp_hdr_overlay {
        bindings::bluetooth_sdp_hdr_overlay {
            type_: hdr.hdr.sdp_type.to_u32().unwrap(),
            uuid: hdr.hdr.uuid,
            service_name_length: hdr.hdr.service_name_length,
            service_name: hdr.hdr.service_name.as_mut_ptr() as *mut c_char,
            rfcomm_channel_number: hdr.hdr.rfcomm_channel_number,
            l2cap_psm: hdr.hdr.l2cap_psm,
            profile_version: hdr.hdr.profile_version,
            user1_ptr_len: hdr.user1_len,
            user1_ptr: hdr.user1_data.as_mut_ptr(),
            user2_ptr_len: hdr.user2_len,
            user2_ptr: hdr.user2_data.as_mut_ptr(),
        }
    }

    // Get sdp record with lifetime tied to self
    fn get_unsafe_record<'a>(&'a mut self) -> bindings::bluetooth_sdp_record {
        match self {
            BtSdpRecord::HeaderOverlay(ref mut hdr) => {
                bindings::bluetooth_sdp_record { hdr: BtSdpRecord::convert_header(hdr) }
            }
            BtSdpRecord::MapMas(mas) => bindings::bluetooth_sdp_record {
                mas: bindings::_bluetooth_sdp_mas_record {
                    hdr: BtSdpRecord::convert_header(&mut mas.hdr),
                    mas_instance_id: mas.mas_instance_id,
                    supported_features: mas.supported_features,
                    supported_message_types: mas.supported_message_types,
                },
            },
            BtSdpRecord::MapMns(mns) => bindings::bluetooth_sdp_record {
                mns: bindings::_bluetooth_sdp_mns_record {
                    hdr: BtSdpRecord::convert_header(&mut mns.hdr),
                    supported_features: mns.supported_features,
                },
            },
            BtSdpRecord::PbapPse(pse) => bindings::bluetooth_sdp_record {
                pse: bindings::_bluetooth_sdp_pse_record {
                    hdr: BtSdpRecord::convert_header(&mut pse.hdr),
                    supported_features: pse.supported_features,
                    supported_repositories: pse.supported_repositories,
                },
            },
            BtSdpRecord::PbapPce(pce) => bindings::bluetooth_sdp_record {
                pce: bindings::_bluetooth_sdp_pce_record {
                    hdr: BtSdpRecord::convert_header(&mut pce.hdr),
                },
            },
            BtSdpRecord::OppServer(ops) => bindings::bluetooth_sdp_record {
                ops: bindings::_bluetooth_sdp_ops_record {
                    hdr: BtSdpRecord::convert_header(&mut ops.hdr),
                    supported_formats_list_len: ops.supported_formats_list_len,
                    supported_formats_list: ops.supported_formats_list,
                },
            },
            BtSdpRecord::SapServer(sap) => bindings::bluetooth_sdp_record {
                sap: bindings::_bluetooth_sdp_sap_record {
                    hdr: BtSdpRecord::convert_header(&mut sap.hdr),
                },
            },
            BtSdpRecord::Dip(dip) => bindings::bluetooth_sdp_record {
                dip: bindings::_bluetooth_sdp_dip_record {
                    hdr: BtSdpRecord::convert_header(&mut dip.hdr),
                    spec_id: dip.spec_id,
                    vendor: dip.vendor,
                    vendor_id_source: dip.vendor_id_source,
                    product: dip.product,
                    version: dip.version,
                    primary_record: dip.primary_record,
                },
            },
        }
    }
}

#[derive(Debug)]
pub enum SdpCallbacks {
    SdpSearch(BtStatus, RawAddress, Uuid, i32, Vec<BtSdpRecord>),
}

pub struct SdpCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(SdpCallbacks) + Send>,
}

type SdpCb = Arc<Mutex<SdpCallbacksDispatcher>>;

cb_variant!(SdpCb, sdp_search_cb -> SdpCallbacks::SdpSearch,
bindings::bt_status_t -> BtStatus,
*const FfiAddress, *const Uuid, i32,
*mut bindings::bluetooth_sdp_record, {
    let _1 = unsafe { deref_const_ffi_address!(_1) };
    let _2 = unsafe { *_2 };
    let _4 = ptr_to_vec(_4, _3 as usize);
});

struct RawSdpWrapper {
    pub raw: *const bindings::btsdp_interface_t,
}

unsafe impl Send for RawSdpWrapper {}

pub struct Sdp {
    internal: RawSdpWrapper,
    is_init: bool,
    callbacks: Option<Box<bindings::btsdp_callbacks_t>>,
}

impl Sdp {
    pub fn new(intf: &BluetoothInterface) -> Sdp {
        let r = intf.get_profile_interface(SupportedProfiles::Sdp);
        Sdp {
            internal: RawSdpWrapper { raw: r as *const bindings::btsdp_interface_t },
            is_init: false,
            callbacks: None,
        }
    }

    pub fn is_initialized(&self) -> bool {
        self.is_init
    }

    pub fn initialize(&mut self, callbacks: SdpCallbacksDispatcher) -> bool {
        if get_dispatchers().lock().unwrap().set::<SdpCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for SdpCallbacks but it already existed");
        }

        let mut callbacks = Box::new(bindings::btsdp_callbacks_t {
            size: 2 * 8,
            sdp_search_cb: Some(sdp_search_cb),
        });

        let rawcb = &mut *callbacks;

        let init = ccall!(self, init, rawcb);
        self.is_init = BtStatus::from(init) == BtStatus::Success;
        self.callbacks = Some(callbacks);

        return self.is_init;
    }

    pub fn sdp_search(&self, address: &mut RawAddress, uuid: &Uuid) -> BtStatus {
        let ffi_addr = cast_to_ffi_address!(address as *mut RawAddress);
        BtStatus::from(ccall!(self, sdp_search, ffi_addr, uuid))
    }

    pub fn create_sdp_record(&self, record: &mut BtSdpRecord, handle: &mut i32) -> BtStatus {
        let mut converted = record.get_unsafe_record();
        let ptr = (&mut converted) as *mut bindings::bluetooth_sdp_record;
        BtStatus::from(ccall!(self, create_sdp_record, ptr, handle))
    }

    pub fn remove_sdp_record(&self, handle: i32) -> BtStatus {
        BtStatus::from(ccall!(self, remove_sdp_record, handle))
    }
}
