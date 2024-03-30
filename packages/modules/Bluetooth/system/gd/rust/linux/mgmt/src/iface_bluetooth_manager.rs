use crate::RPCProxy;

#[derive(Debug, Default)]
pub struct AdapterWithEnabled {
    pub hci_interface: i32,
    pub enabled: bool,
}

/// Bluetooth stack management API.
pub trait IBluetoothManager {
    /// Starts the Bluetooth stack.
    fn start(&mut self, hci_interface: i32);

    /// Stops the Bluetooth stack.
    fn stop(&mut self, hci_interface: i32);

    /// Returns whether an adapter is enabled.
    fn get_adapter_enabled(&mut self, hci_interface: i32) -> bool;

    /// Registers a callback to the Bluetooth manager state.
    fn register_callback(&mut self, callback: Box<dyn IBluetoothManagerCallback + Send>);

    /// Returns whether Floss is enabled.
    fn get_floss_enabled(&mut self) -> bool;

    /// Enables/disables Floss.
    fn set_floss_enabled(&mut self, enabled: bool);

    /// Returns a list of available HCI devices and if they are enabled.
    fn get_available_adapters(&mut self) -> Vec<AdapterWithEnabled>;
}

/// Interface of Bluetooth Manager callbacks.
pub trait IBluetoothManagerCallback: RPCProxy {
    fn on_hci_device_changed(&self, hci_interface: i32, present: bool);
    fn on_hci_enabled_changed(&self, hci_interface: i32, enabled: bool);
}
