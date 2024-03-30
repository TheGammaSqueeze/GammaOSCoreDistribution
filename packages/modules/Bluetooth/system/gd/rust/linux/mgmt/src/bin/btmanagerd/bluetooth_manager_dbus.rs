use dbus::arg::RefArg;
use dbus::strings::Path;
use dbus_macros::{dbus_method, dbus_propmap, dbus_proxy_obj, generate_dbus_exporter};
use dbus_projection::{dbus_generated, DisconnectWatcher};

use manager_service::iface_bluetooth_manager::{
    AdapterWithEnabled, IBluetoothManager, IBluetoothManagerCallback,
};
use manager_service::RPCProxy;

use crate::dbus_arg::{DBusArg, DBusArgError, RefArgToRust};

#[dbus_propmap(AdapterWithEnabled)]
pub struct AdapterWithEnabledDbus {
    hci_interface: i32,
    enabled: bool,
}

/// D-Bus projection of IBluetoothManager.
struct BluetoothManagerDBus {}

#[generate_dbus_exporter(export_bluetooth_manager_dbus_obj, "org.chromium.bluetooth.Manager")]
impl IBluetoothManager for BluetoothManagerDBus {
    #[dbus_method("Start")]
    fn start(&mut self, hci_interface: i32) {
        dbus_generated!()
    }

    #[dbus_method("Stop")]
    fn stop(&mut self, hci_interface: i32) {
        dbus_generated!()
    }

    #[dbus_method("GetAdapterEnabled")]
    fn get_adapter_enabled(&mut self, hci_interface: i32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn IBluetoothManagerCallback + Send>) {
        dbus_generated!()
    }

    #[dbus_method("GetFlossEnabled")]
    fn get_floss_enabled(&mut self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetFlossEnabled")]
    fn set_floss_enabled(&mut self, enabled: bool) {
        dbus_generated!()
    }

    #[dbus_method("GetAvailableAdapters")]
    fn get_available_adapters(&mut self) -> Vec<AdapterWithEnabled> {
        dbus_generated!()
    }
}

/// D-Bus projection of IBluetoothManagerCallback.
struct BluetoothManagerCallbackDBus {}

#[dbus_proxy_obj(BluetoothManagerCallback, "org.chromium.bluetooth.ManagerCallback")]
impl IBluetoothManagerCallback for BluetoothManagerCallbackDBus {
    #[dbus_method("OnHciDeviceChanged")]
    fn on_hci_device_changed(&self, hci_interface: i32, present: bool) {}

    #[dbus_method("OnHciEnabledChanged")]
    fn on_hci_enabled_changed(&self, hci_interface: i32, enabled: bool) {}
}
