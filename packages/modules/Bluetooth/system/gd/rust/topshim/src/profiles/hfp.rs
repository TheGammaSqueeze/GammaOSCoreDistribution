use crate::btif::{BluetoothInterface, RawAddress};
use crate::topstack::get_dispatchers;

use num_traits::cast::FromPrimitive;
use std::convert::{TryFrom, TryInto};
use std::sync::{Arc, Mutex};
use topshim_macros::cb_variant;

#[derive(Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BthfConnectionState {
    Disconnected = 0,
    Connecting,
    Connected,
    SlcConnected,
    Disconnecting,
}

impl From<u32> for BthfConnectionState {
    fn from(item: u32) -> Self {
        BthfConnectionState::from_u32(item).unwrap()
    }
}

#[derive(Debug, FromPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BthfAudioState {
    Disconnected = 0,
    Connecting,
    Connected,
    Disconnecting,
}

impl From<u32> for BthfAudioState {
    fn from(item: u32) -> Self {
        BthfAudioState::from_u32(item).unwrap()
    }
}

bitflags! {
    #[derive(Default)]
    pub struct HfpCodecCapability: i32 {
        const UNSUPPORTED = 0b00;
        const CVSD = 0b01;
        const MSBC = 0b10;
    }
}

impl TryInto<i32> for HfpCodecCapability {
    type Error = ();
    fn try_into(self) -> Result<i32, Self::Error> {
        Ok(self.bits())
    }
}

impl TryFrom<i32> for HfpCodecCapability {
    type Error = ();
    fn try_from(val: i32) -> Result<Self, Self::Error> {
        Self::from_bits(val).ok_or(())
    }
}

#[cxx::bridge(namespace = bluetooth::topshim::rust)]
pub mod ffi {
    #[derive(Debug, Copy, Clone)]
    pub struct RustRawAddress {
        address: [u8; 6],
    }

    unsafe extern "C++" {
        include!("hfp/hfp_shim.h");

        type HfpIntf;

        unsafe fn GetHfpProfile(btif: *const u8) -> UniquePtr<HfpIntf>;

        fn init(self: Pin<&mut HfpIntf>) -> i32;
        fn connect(self: Pin<&mut HfpIntf>, bt_addr: RustRawAddress) -> i32;
        fn connect_audio(self: Pin<&mut HfpIntf>, bt_addr: RustRawAddress) -> i32;
        fn disconnect(self: Pin<&mut HfpIntf>, bt_addr: RustRawAddress) -> i32;
        fn disconnect_audio(self: Pin<&mut HfpIntf>, bt_addr: RustRawAddress) -> i32;
        fn cleanup(self: Pin<&mut HfpIntf>);

    }
    extern "Rust" {
        fn hfp_connection_state_callback(state: u32, addr: RustRawAddress);
        fn hfp_audio_state_callback(state: u32, addr: RustRawAddress);
    }
}

impl From<RawAddress> for ffi::RustRawAddress {
    fn from(addr: RawAddress) -> Self {
        ffi::RustRawAddress { address: addr.val }
    }
}

impl Into<RawAddress> for ffi::RustRawAddress {
    fn into(self) -> RawAddress {
        RawAddress { val: self.address }
    }
}

#[derive(Debug)]
pub enum HfpCallbacks {
    ConnectionState(BthfConnectionState, RawAddress),
    AudioState(BthfAudioState, RawAddress),
}

pub struct HfpCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(HfpCallbacks) + Send>,
}

type HfpCb = Arc<Mutex<HfpCallbacksDispatcher>>;

cb_variant!(
    HfpCb,
    hfp_connection_state_callback -> HfpCallbacks::ConnectionState,
    u32 -> BthfConnectionState, ffi::RustRawAddress -> RawAddress, {
        let _1 = _1.into();
    }
);

cb_variant!(
    HfpCb,
    hfp_audio_state_callback -> HfpCallbacks::AudioState,
    u32 -> BthfAudioState, ffi::RustRawAddress -> RawAddress, {
        let _1 = _1.into();
    }
);

pub struct Hfp {
    internal: cxx::UniquePtr<ffi::HfpIntf>,
    _is_init: bool,
}

// For *const u8 opaque btif
unsafe impl Send for Hfp {}

impl Hfp {
    pub fn new(intf: &BluetoothInterface) -> Hfp {
        let hfpif: cxx::UniquePtr<ffi::HfpIntf>;
        unsafe {
            hfpif = ffi::GetHfpProfile(intf.as_raw_ptr());
        }

        Hfp { internal: hfpif, _is_init: false }
    }

    pub fn initialize(&mut self, callbacks: HfpCallbacksDispatcher) -> bool {
        if get_dispatchers().lock().unwrap().set::<HfpCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for HFP callbacks while it already exists");
        }
        self.internal.pin_mut().init();
        true
    }

    pub fn connect(&mut self, addr: RawAddress) {
        self.internal.pin_mut().connect(addr.into());
    }

    pub fn connect_audio(&mut self, addr: RawAddress) -> i32 {
        self.internal.pin_mut().connect_audio(addr.into())
    }

    pub fn disconnect(&mut self, addr: RawAddress) {
        self.internal.pin_mut().disconnect(addr.into());
    }

    pub fn disconnect_audio(&mut self, addr: RawAddress) -> i32 {
        self.internal.pin_mut().disconnect_audio(addr.into())
    }

    pub fn cleanup(&mut self) -> bool {
        self.internal.pin_mut().cleanup();
        true
    }
}
