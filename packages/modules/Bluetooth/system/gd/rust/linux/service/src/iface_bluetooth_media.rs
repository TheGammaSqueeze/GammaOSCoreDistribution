use bt_topshim::profiles::a2dp::{A2dpCodecConfig, PresentationPosition};
use bt_topshim::profiles::hfp::HfpCodecCapability;
use btstack::bluetooth_media::{BluetoothAudioDevice, IBluetoothMedia, IBluetoothMediaCallback};
use btstack::RPCProxy;

use dbus::arg::RefArg;
use dbus::nonblock::SyncConnection;
use dbus::strings::Path;

use dbus_macros::{dbus_method, dbus_propmap, dbus_proxy_obj, generate_dbus_exporter};

use dbus_projection::DisconnectWatcher;
use dbus_projection::{dbus_generated, impl_dbus_arg_from_into};

use crate::dbus_arg::{DBusArg, DBusArgError, RefArgToRust};

use std::convert::{TryFrom, TryInto};
use std::sync::Arc;

#[allow(dead_code)]
struct BluetoothMediaCallbackDBus {}

#[dbus_propmap(A2dpCodecConfig)]
pub struct A2dpCodecConfigDBus {
    codec_type: i32,
    codec_priority: i32,
    sample_rate: i32,
    bits_per_sample: i32,
    channel_mode: i32,
    codec_specific_1: i64,
    codec_specific_2: i64,
    codec_specific_3: i64,
    codec_specific_4: i64,
}

#[dbus_propmap(BluetoothAudioDevice)]
pub struct BluetoothAudioDeviceDBus {
    address: String,
    name: String,
    a2dp_caps: Vec<A2dpCodecConfig>,
    hfp_cap: HfpCodecCapability,
    absolute_volume: bool,
}

impl_dbus_arg_from_into!(HfpCodecCapability, i32);

#[dbus_proxy_obj(BluetoothMediaCallback, "org.chromium.bluetooth.BluetoothMediaCallback")]
impl IBluetoothMediaCallback for BluetoothMediaCallbackDBus {
    #[dbus_method("OnBluetoothAudioDeviceAdded")]
    fn on_bluetooth_audio_device_added(&self, device: BluetoothAudioDevice) {
        dbus_generated!()
    }

    #[dbus_method("OnBluetoothAudioDeviceRemoved")]
    fn on_bluetooth_audio_device_removed(&self, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("OnAbsoluteVolumeSupportedChanged")]
    fn on_absolute_volume_supported_changed(&self, supported: bool) {
        dbus_generated!()
    }

    #[dbus_method("OnAbsoluteVolumeChanged")]
    fn on_absolute_volume_changed(&self, volume: i32) {
        dbus_generated!()
    }
}

#[allow(dead_code)]
struct IBluetoothMediaDBus {}

#[dbus_propmap(PresentationPosition)]
pub struct PresentationPositionDBus {
    remote_delay_report_ns: u64,
    total_bytes_read: u64,
    data_position_sec: i64,
    data_position_nsec: i32,
}

#[generate_dbus_exporter(export_bluetooth_media_dbus_obj, "org.chromium.bluetooth.BluetoothMedia")]
impl IBluetoothMedia for IBluetoothMediaDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn IBluetoothMediaCallback + Send>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Initialize")]
    fn initialize(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Cleanup")]
    fn cleanup(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Connect")]
    fn connect(&mut self, device: String) {
        dbus_generated!()
    }

    #[dbus_method("SetActiveDevice")]
    fn set_active_device(&mut self, device: String) {
        dbus_generated!()
    }

    #[dbus_method("Disconnect")]
    fn disconnect(&mut self, device: String) {
        dbus_generated!()
    }

    #[dbus_method("SetAudioConfig")]
    fn set_audio_config(
        &mut self,
        sample_rate: i32,
        bits_per_sample: i32,
        channel_mode: i32,
    ) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetVolume")]
    fn set_volume(&mut self, volume: i32) {
        dbus_generated!()
    }

    #[dbus_method("StartAudioRequest")]
    fn start_audio_request(&mut self) {
        dbus_generated!()
    }

    #[dbus_method("StopAudioRequest")]
    fn stop_audio_request(&mut self) {
        dbus_generated!()
    }

    #[dbus_method("StartScoCall")]
    fn start_sco_call(&mut self, device: String) {
        dbus_generated!()
    }

    #[dbus_method("StopScoCall")]
    fn stop_sco_call(&mut self, device: String) {
        dbus_generated!()
    }

    #[dbus_method("GetPresentationPosition")]
    fn get_presentation_position(&mut self) -> PresentationPosition {
        dbus_generated!()
    }
}
