use log::{error, info, warn};

use manager_service::iface_bluetooth_manager::{
    AdapterWithEnabled, IBluetoothManager, IBluetoothManagerCallback,
};

use std::collections::HashMap;
use std::process::Command;
use std::sync::atomic::Ordering;

use crate::{config_util, state_machine, ManagerContext};

const BLUEZ_INIT_TARGET: &str = "bluetoothd";

/// Implementation of IBluetoothManager.
pub struct BluetoothManager {
    manager_context: ManagerContext,
    callbacks: HashMap<u32, Box<dyn IBluetoothManagerCallback + Send>>,
    cached_devices: HashMap<i32, bool>,
}

impl BluetoothManager {
    pub(crate) fn new(manager_context: ManagerContext) -> BluetoothManager {
        BluetoothManager {
            manager_context,
            callbacks: HashMap::new(),
            cached_devices: HashMap::new(),
        }
    }

    pub(crate) fn callback_hci_device_change(&mut self, hci_device: i32, present: bool) {
        if present {
            // Default device to false or whatever was already existing in cache
            self.cached_devices.entry(hci_device).or_insert(false);
        } else {
            // Remove device and ignore if it's not there
            self.cached_devices.remove(&hci_device);
        }

        for (_, callback) in &self.callbacks {
            callback.on_hci_device_changed(hci_device, present);
        }
    }

    pub(crate) fn callback_hci_enabled_change(&mut self, hci_device: i32, enabled: bool) {
        // Update existing entry or insert new one
        match self.cached_devices.get_mut(&hci_device) {
            Some(dev) => {
                *dev = enabled;
            }
            _ => {
                self.cached_devices.insert(hci_device, enabled);
            }
        };

        for (_, callback) in &self.callbacks {
            callback.on_hci_enabled_changed(hci_device, enabled);
        }
    }

    pub(crate) fn callback_disconnected(&mut self, id: u32) {
        self.callbacks.remove(&id);
    }

    pub(crate) fn get_floss_enabled_internal(&mut self) -> bool {
        let enabled = self.manager_context.floss_enabled.load(Ordering::Relaxed);
        enabled
    }
}

impl IBluetoothManager for BluetoothManager {
    fn start(&mut self, hci_interface: i32) {
        info!("Starting {}", hci_interface);
        if !config_util::modify_hci_n_enabled(hci_interface, true) {
            error!("Config is not successfully modified");
        }

        // Ignore the request if adapter is already enabled.
        if *self.cached_devices.get(&hci_interface).unwrap_or(&false) {
            return;
        }

        self.manager_context.proxy.start_bluetooth(hci_interface);
    }

    fn stop(&mut self, hci_interface: i32) {
        info!("Stopping {}", hci_interface);
        if !config_util::modify_hci_n_enabled(hci_interface, false) {
            error!("Config is not successfully modified");
        }

        // Ignore the request if adapter is already disabled.
        if !*self.cached_devices.get(&hci_interface).unwrap_or(&false) {
            return;
        }

        self.manager_context.proxy.stop_bluetooth(hci_interface);
    }

    fn get_adapter_enabled(&mut self, _hci_interface: i32) -> bool {
        let proxy = self.manager_context.proxy.clone();

        // TODO(b/189501676) - State should depend on given adapter.
        let state = proxy.get_state();
        let result = state_machine::state_to_enabled(state);
        result
    }

    fn register_callback(&mut self, mut callback: Box<dyn IBluetoothManagerCallback + Send>) {
        let tx = self.manager_context.proxy.get_tx();

        let id = callback.register_disconnect(Box::new(move |cb_id| {
            let tx = tx.clone();
            tokio::spawn(async move {
                let _result = tx.send(state_machine::Message::CallbackDisconnected(cb_id)).await;
            });
        }));

        self.callbacks.insert(id, callback);
    }

    fn get_floss_enabled(&mut self) -> bool {
        self.get_floss_enabled_internal()
    }

    fn set_floss_enabled(&mut self, enabled: bool) {
        let prev = self.manager_context.floss_enabled.swap(enabled, Ordering::Relaxed);
        config_util::write_floss_enabled(enabled);
        if prev != enabled && enabled {
            if let Err(e) = Command::new("initctl").args(&["stop", BLUEZ_INIT_TARGET]).output() {
                warn!("Failed to stop bluetoothd: {}", e);
            }
            // TODO: Implement multi-hci case
            let default_device = config_util::list_hci_devices()[0];
            if config_util::is_hci_n_enabled(default_device) {
                let _ = self.manager_context.proxy.start_bluetooth(default_device);
            }
        } else if prev != enabled {
            // TODO: Implement multi-hci case
            let default_device = config_util::list_hci_devices()[0];
            self.manager_context.proxy.stop_bluetooth(default_device);
            if let Err(e) = Command::new("initctl").args(&["start", BLUEZ_INIT_TARGET]).output() {
                warn!("Failed to start bluetoothd: {}", e);
            }
        }
    }

    fn get_available_adapters(&mut self) -> Vec<AdapterWithEnabled> {
        let adapters = config_util::list_hci_devices()
            .iter()
            .map(|hci_interface| {
                let enabled: bool = *self.cached_devices.get(&hci_interface).unwrap_or(&false);
                AdapterWithEnabled { hci_interface: *hci_interface, enabled }
            })
            .collect::<Vec<AdapterWithEnabled>>();

        adapters
    }
}
