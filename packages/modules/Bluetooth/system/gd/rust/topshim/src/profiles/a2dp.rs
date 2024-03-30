use crate::btif::{BluetoothInterface, RawAddress};
use crate::topstack::get_dispatchers;

use num_traits::cast::FromPrimitive;
use std::sync::{Arc, Mutex};
use topshim_macros::cb_variant;

#[derive(Debug, FromPrimitive, ToPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtavConnectionState {
    Disconnected = 0,
    Connecting,
    Connected,
    Disconnecting,
}

impl From<u32> for BtavConnectionState {
    fn from(item: u32) -> Self {
        BtavConnectionState::from_u32(item).unwrap()
    }
}

#[derive(Debug, FromPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum BtavAudioState {
    RemoteSuspend = 0,
    Stopped,
    Started,
}

impl From<u32> for BtavAudioState {
    fn from(item: u32) -> Self {
        BtavAudioState::from_u32(item).unwrap()
    }
}

#[derive(Debug, FromPrimitive, PartialEq, PartialOrd)]
#[repr(u32)]
pub enum A2dpCodecIndex {
    SrcSbc = 0,
    SrcAac,
    SrcAptx,
    SrcAptxHD,
    SrcLdac,
    SinkSbc,
    SinkAac,
    SinkLdac,
    Max,
}

impl A2dpCodecIndex {
    pub const SRC_MIN: A2dpCodecIndex = A2dpCodecIndex::SrcSbc;
    pub const SRC_MAX: A2dpCodecIndex = A2dpCodecIndex::SinkSbc;
    pub const SINK_MIN: A2dpCodecIndex = A2dpCodecIndex::SinkSbc;
    pub const SINK_MAX: A2dpCodecIndex = A2dpCodecIndex::Max;
    pub const MAX: A2dpCodecIndex = A2dpCodecIndex::Max;
    pub const MIN: A2dpCodecIndex = A2dpCodecIndex::SrcSbc;
}

impl From<i32> for A2dpCodecIndex {
    fn from(item: i32) -> Self {
        A2dpCodecIndex::from_i32(item).unwrap_or_else(|| A2dpCodecIndex::MIN)
    }
}

#[derive(Debug, FromPrimitive, PartialEq, PartialOrd)]
#[repr(i32)]
pub enum A2dpCodecPriority {
    Disabled = -1,
    Default = 0,
    Highest = 1_000_000,
}

impl From<i32> for A2dpCodecPriority {
    fn from(item: i32) -> Self {
        A2dpCodecPriority::from_i32(item).unwrap_or_else(|| A2dpCodecPriority::Default)
    }
}

bitflags! {
    pub struct A2dpCodecSampleRate: i32 {
        const RATE_NONE = 0x0;
        const RATE_44100 = 0x01;
        const RATE_48000 = 0x02;
        const RATE_88200 = 0x04;
        const RATE_96000 = 0x08;
        const RATE_176400 = 0x10;
        const RATE_192000 = 0x20;
        const RATE_16000 = 0x40;
        const RATE_24000 = 0x80;
    }
}

impl A2dpCodecSampleRate {
    pub fn validate_bits(val: i32) -> bool {
        val <= A2dpCodecSampleRate::all().bits()
    }
}

bitflags! {
    pub struct A2dpCodecBitsPerSample: i32 {
        const SAMPLE_NONE = 0x0;
        const SAMPLE_16 = 0x01;
        const SAMPLE_24 = 0x02;
        const SAMPLE_32 = 0x04;
    }
}

impl A2dpCodecBitsPerSample {
    pub fn validate_bits(val: i32) -> bool {
        val <= A2dpCodecBitsPerSample::all().bits()
    }
}

bitflags! {
    pub struct A2dpCodecChannelMode: i32 {
        const MODE_NONE = 0x0;
        const MODE_MONO = 0x01;
        const MODE_STEREO = 0x02;
    }
}

impl A2dpCodecChannelMode {
    pub fn validate_bits(val: i32) -> bool {
        val <= A2dpCodecChannelMode::all().bits()
    }
}

#[cxx::bridge(namespace = bluetooth::topshim::rust)]
pub mod ffi {
    #[derive(Debug, Copy, Clone)]
    pub struct RustRawAddress {
        address: [u8; 6],
    }

    #[derive(Debug, Copy, Clone)]
    pub struct A2dpCodecConfig {
        pub codec_type: i32,
        pub codec_priority: i32,
        pub sample_rate: i32,
        pub bits_per_sample: i32,
        pub channel_mode: i32,
        pub codec_specific_1: i64,
        pub codec_specific_2: i64,
        pub codec_specific_3: i64,
        pub codec_specific_4: i64,
    }

    #[derive(Debug, Default)]
    pub struct RustPresentationPosition {
        remote_delay_report_ns: u64,
        total_bytes_read: u64,
        data_position_sec: i64,
        data_position_nsec: i32,
    }

    unsafe extern "C++" {
        include!("btav/btav_shim.h");
        include!("btav_sink/btav_sink_shim.h");

        type A2dpIntf;
        type A2dpSinkIntf;

        unsafe fn GetA2dpProfile(btif: *const u8) -> UniquePtr<A2dpIntf>;

        fn init(self: &A2dpIntf) -> i32;
        fn connect(self: &A2dpIntf, bt_addr: RustRawAddress) -> i32;
        fn disconnect(self: &A2dpIntf, bt_addr: RustRawAddress) -> i32;
        fn set_silence_device(self: &A2dpIntf, bt_addr: RustRawAddress, silent: bool) -> i32;
        fn set_active_device(self: &A2dpIntf, bt_addr: RustRawAddress) -> i32;
        fn config_codec(
            self: &A2dpIntf,
            bt_addr: RustRawAddress,
            codec_preferences: Vec<A2dpCodecConfig>,
        ) -> i32;
        fn set_audio_config(self: &A2dpIntf, config: A2dpCodecConfig) -> bool;
        fn start_audio_request(self: &A2dpIntf) -> bool;
        fn stop_audio_request(self: &A2dpIntf) -> bool;
        fn cleanup(self: &A2dpIntf);
        fn get_presentation_position(self: &A2dpIntf) -> RustPresentationPosition;
        // A2dp sink functions

        unsafe fn GetA2dpSinkProfile(btif: *const u8) -> UniquePtr<A2dpSinkIntf>;

        fn init(self: &A2dpSinkIntf) -> i32;
        fn connect(self: &A2dpSinkIntf, bt_addr: RustRawAddress) -> i32;
        fn disconnect(self: &A2dpSinkIntf, bt_addr: RustRawAddress) -> i32;
        fn set_active_device(self: &A2dpSinkIntf, bt_addr: RustRawAddress) -> i32;
        fn cleanup(self: &A2dpSinkIntf);
    }
    extern "Rust" {
        fn connection_state_callback(addr: RustRawAddress, state: u32);
        fn audio_state_callback(addr: RustRawAddress, state: u32);
        fn audio_config_callback(
            addr: RustRawAddress,
            codec_config: A2dpCodecConfig,
            codecs_local_capabilities: Vec<A2dpCodecConfig>,
            codecs_selectable_capabilities: Vec<A2dpCodecConfig>,
        );
        fn mandatory_codec_preferred_callback(addr: RustRawAddress);
    }
}

pub type FfiAddress = ffi::RustRawAddress;
pub type A2dpCodecConfig = ffi::A2dpCodecConfig;
pub type PresentationPosition = ffi::RustPresentationPosition;

impl From<RawAddress> for FfiAddress {
    fn from(addr: RawAddress) -> Self {
        FfiAddress { address: addr.val }
    }
}

impl Into<RawAddress> for FfiAddress {
    fn into(self) -> RawAddress {
        RawAddress { val: self.address }
    }
}

impl Default for A2dpCodecConfig {
    fn default() -> A2dpCodecConfig {
        A2dpCodecConfig {
            codec_type: 0,
            codec_priority: 0,
            sample_rate: 0,
            bits_per_sample: 0,
            channel_mode: 0,
            codec_specific_1: 0,
            codec_specific_2: 0,
            codec_specific_3: 0,
            codec_specific_4: 0,
        }
    }
}

#[derive(Debug)]
pub enum A2dpCallbacks {
    ConnectionState(RawAddress, BtavConnectionState),
    AudioState(RawAddress, BtavAudioState),
    AudioConfig(RawAddress, A2dpCodecConfig, Vec<A2dpCodecConfig>, Vec<A2dpCodecConfig>),
    MandatoryCodecPreferred(RawAddress),
}

pub struct A2dpCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(A2dpCallbacks) + Send>,
}

type A2dpCb = Arc<Mutex<A2dpCallbacksDispatcher>>;

cb_variant!(A2dpCb, connection_state_callback -> A2dpCallbacks::ConnectionState,
FfiAddress -> RawAddress, u32 -> BtavConnectionState, {
    let _0 = _0.into();
});

cb_variant!(A2dpCb, audio_state_callback -> A2dpCallbacks::AudioState,
FfiAddress -> RawAddress, u32 -> BtavAudioState, {
    let _0 = _0.into();
});

cb_variant!(A2dpCb, mandatory_codec_preferred_callback -> A2dpCallbacks::MandatoryCodecPreferred,
FfiAddress -> RawAddress, {
    let _0 = _0.into();
});

cb_variant!(A2dpCb, audio_config_callback -> A2dpCallbacks::AudioConfig,
FfiAddress -> RawAddress, A2dpCodecConfig, Vec<A2dpCodecConfig>, Vec<A2dpCodecConfig>, {
    let _0 = _0.into();
});

pub struct A2dp {
    internal: cxx::UniquePtr<ffi::A2dpIntf>,
    _is_init: bool,
}

// For *const u8 opaque btif
unsafe impl Send for A2dp {}

impl A2dp {
    pub fn new(intf: &BluetoothInterface) -> A2dp {
        let a2dpif: cxx::UniquePtr<ffi::A2dpIntf>;
        unsafe {
            a2dpif = ffi::GetA2dpProfile(intf.as_raw_ptr());
        }

        A2dp { internal: a2dpif, _is_init: false }
    }

    pub fn initialize(&mut self, callbacks: A2dpCallbacksDispatcher) -> bool {
        if get_dispatchers().lock().unwrap().set::<A2dpCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for A2dp callbacks while it already exists");
        }
        self.internal.init();
        true
    }

    pub fn connect(&mut self, addr: RawAddress) {
        self.internal.connect(addr.into());
    }

    pub fn set_active_device(&mut self, addr: RawAddress) {
        self.internal.set_active_device(addr.into());
    }

    pub fn disconnect(&mut self, addr: RawAddress) {
        self.internal.disconnect(addr.into());
    }

    pub fn set_audio_config(&self, sample_rate: i32, bits_per_sample: i32, channel_mode: i32) {
        let config =
            A2dpCodecConfig { sample_rate, bits_per_sample, channel_mode, ..Default::default() };
        self.internal.set_audio_config(config);
    }
    pub fn start_audio_request(&self) {
        self.internal.start_audio_request();
    }

    pub fn stop_audio_request(&self) {
        self.internal.stop_audio_request();
    }

    pub fn get_presentation_position(&self) -> PresentationPosition {
        self.internal.get_presentation_position()
    }
}

#[derive(Debug)]
pub enum A2dpSinkCallbacks {
    ConnectionState(RawAddress, BtavConnectionState),
}

pub struct A2dpSinkCallbacksDispatcher {
    pub dispatch: Box<dyn Fn(A2dpSinkCallbacks) + Send>,
}

type A2dpSinkCb = Arc<Mutex<A2dpSinkCallbacksDispatcher>>;

pub struct A2dpSink {
    internal: cxx::UniquePtr<ffi::A2dpSinkIntf>,
    _is_init: bool,
}

// For *const u8 opaque btif
unsafe impl Send for A2dpSink {}

impl A2dpSink {
    pub fn new(intf: &BluetoothInterface) -> A2dpSink {
        let a2dp_sink: cxx::UniquePtr<ffi::A2dpSinkIntf>;
        unsafe {
            a2dp_sink = ffi::GetA2dpSinkProfile(intf.as_raw_ptr());
        }

        A2dpSink { internal: a2dp_sink, _is_init: false }
    }

    pub fn initialize(&mut self, callbacks: A2dpSinkCallbacksDispatcher) -> bool {
        if get_dispatchers().lock().unwrap().set::<A2dpSinkCb>(Arc::new(Mutex::new(callbacks))) {
            panic!("Tried to set dispatcher for A2dp Sink Callbacks while it already exists");
        }
        self.internal.init();
        true
    }

    pub fn connect(&mut self, bt_addr: RawAddress) {
        self.internal.connect(bt_addr.into());
    }

    pub fn disconnect(&mut self, bt_addr: RawAddress) {
        self.internal.disconnect(bt_addr.into());
    }

    pub fn set_active_device(&mut self, bt_addr: RawAddress) {
        self.internal.set_active_device(bt_addr.into());
    }

    pub fn cleanup(&mut self) {}
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validate_sample_rate() {
        assert!(!A2dpCodecSampleRate::validate_bits(256));
        assert!(A2dpCodecSampleRate::validate_bits(2 + 32 + 128));
    }

    #[test]
    fn validate_bits_per_sample() {
        assert!(!A2dpCodecBitsPerSample::validate_bits(8));
        assert!(A2dpCodecBitsPerSample::validate_bits(1 + 4));
    }

    #[test]
    fn validate_channel_mode() {
        assert!(!A2dpCodecChannelMode::validate_bits(4));
        assert!(A2dpCodecChannelMode::validate_bits(1 + 2));
    }
}
