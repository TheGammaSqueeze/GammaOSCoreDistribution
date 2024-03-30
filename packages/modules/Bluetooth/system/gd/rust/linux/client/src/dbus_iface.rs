//! D-Bus proxy implementations of the APIs.

use bt_topshim::btif::{BtDeviceType, BtSspVariant, BtTransport, Uuid128Bit};
use bt_topshim::profiles::gatt::GattStatus;

use btstack::bluetooth::{
    BluetoothDevice, IBluetooth, IBluetoothCallback, IBluetoothConnectionCallback,
};
use btstack::bluetooth_gatt::{
    BluetoothGattCharacteristic, BluetoothGattDescriptor, BluetoothGattService,
    GattWriteRequestStatus, GattWriteType, IBluetoothGatt, IBluetoothGattCallback,
    IScannerCallback, LePhy, ScanFilter, ScanSettings,
};

use btstack::suspend::{ISuspend, ISuspendCallback, SuspendType};

use btstack::uuid::Profile;
use dbus::arg::{AppendAll, RefArg};
use dbus::nonblock::SyncConnection;

use dbus_projection::{impl_dbus_arg_enum, DisconnectWatcher};

use dbus_macros::{
    dbus_method, dbus_propmap, generate_dbus_exporter, generate_dbus_interface_client,
};

use manager_service::iface_bluetooth_manager::{
    AdapterWithEnabled, IBluetoothManager, IBluetoothManagerCallback,
};

use num_traits::{FromPrimitive, ToPrimitive};

use std::convert::TryInto;
use std::sync::Arc;

use crate::dbus_arg::{DBusArg, DBusArgError, RefArgToRust};

fn make_object_path(idx: i32, name: &str) -> dbus::Path {
    dbus::Path::new(format!("/org/chromium/bluetooth/hci{}/{}", idx, name)).unwrap()
}

impl_dbus_arg_enum!(BtDeviceType);
impl_dbus_arg_enum!(BtSspVariant);
impl_dbus_arg_enum!(BtTransport);
impl_dbus_arg_enum!(GattStatus);
impl_dbus_arg_enum!(GattWriteRequestStatus);
impl_dbus_arg_enum!(GattWriteType);
impl_dbus_arg_enum!(LePhy);
impl_dbus_arg_enum!(Profile);
impl_dbus_arg_enum!(SuspendType);

// Represents Uuid128Bit as an array in D-Bus.
impl DBusArg for Uuid128Bit {
    type DBusType = Vec<u8>;

    fn from_dbus(
        data: Vec<u8>,
        _conn: Option<Arc<SyncConnection>>,
        _remote: Option<dbus::strings::BusName<'static>>,
        _disconnect_watcher: Option<Arc<std::sync::Mutex<DisconnectWatcher>>>,
    ) -> Result<[u8; 16], Box<dyn std::error::Error>> {
        return Ok(data.try_into().unwrap());
    }

    fn to_dbus(data: [u8; 16]) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        return Ok(data.to_vec());
    }
}

#[dbus_propmap(BluetoothGattDescriptor)]
pub struct BluetoothGattDescriptorDBus {
    uuid: Uuid128Bit,
    instance_id: i32,
    permissions: i32,
}

#[dbus_propmap(BluetoothGattCharacteristic)]
pub struct BluetoothGattCharacteristicDBus {
    uuid: Uuid128Bit,
    instance_id: i32,
    properties: i32,
    permissions: i32,
    key_size: i32,
    write_type: GattWriteType,
    descriptors: Vec<BluetoothGattDescriptor>,
}

#[dbus_propmap(BluetoothGattService)]
pub struct BluetoothGattServiceDBus {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub service_type: i32,
    pub characteristics: Vec<BluetoothGattCharacteristic>,
    pub included_services: Vec<BluetoothGattService>,
}

#[dbus_propmap(BluetoothDevice)]
pub struct BluetoothDeviceDBus {
    address: String,
    name: String,
}

struct ClientDBusProxy {
    conn: Arc<SyncConnection>,
    bus_name: String,
    objpath: dbus::Path<'static>,
    interface: String,
}

impl ClientDBusProxy {
    fn create_proxy(&self) -> dbus::nonblock::Proxy<Arc<SyncConnection>> {
        let conn = self.conn.clone();
        dbus::nonblock::Proxy::new(
            self.bus_name.clone(),
            self.objpath.clone(),
            std::time::Duration::from_secs(2),
            conn,
        )
    }

    /// Calls a method and returns the dbus result.
    fn method_withresult<A: AppendAll, T: 'static + dbus::arg::Arg + for<'z> dbus::arg::Get<'z>>(
        &self,
        member: &str,
        args: A,
    ) -> Result<(T,), dbus::Error> {
        let proxy = self.create_proxy();
        // We know that all APIs return immediately, so we can block on it for simplicity.
        return futures::executor::block_on(async {
            proxy.method_call(self.interface.clone(), member, args).await
        });
    }

    fn method<A: AppendAll, T: 'static + dbus::arg::Arg + for<'z> dbus::arg::Get<'z>>(
        &self,
        member: &str,
        args: A,
    ) -> T {
        let (ret,): (T,) = self.method_withresult(member, args).unwrap();
        return ret;
    }

    fn method_noreturn<A: AppendAll>(&self, member: &str, args: A) {
        // The real type should be Result<((),), _> since there is no return value. However, to
        // meet trait constraints, we just use bool and never unwrap the result. This calls the
        // method, waits for the response but doesn't actually attempt to parse the result (on
        // unwrap).
        let _: Result<(bool,), _> = self.method_withresult(member, args);
    }
}

#[allow(dead_code)]
struct IBluetoothCallbackDBus {}

impl btstack::RPCProxy for IBluetoothCallbackDBus {
    // Dummy implementations just to satisfy impl RPCProxy requirements.
    fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
        0
    }
    fn get_object_id(&self) -> String {
        String::from("")
    }
    fn unregister(&mut self, _id: u32) -> bool {
        false
    }
    fn export_for_rpc(self: Box<Self>) {}
}

#[generate_dbus_exporter(
    export_bluetooth_callback_dbus_obj,
    "org.chromium.bluetooth.BluetoothCallback"
)]
impl IBluetoothCallback for IBluetoothCallbackDBus {
    #[dbus_method("OnAddressChanged")]
    fn on_address_changed(&self, addr: String) {}

    #[dbus_method("OnNameChanged")]
    fn on_name_changed(&self, name: String) {}

    #[dbus_method("OnDiscoverableChanged")]
    fn on_discoverable_changed(&self, discoverable: bool) {}

    #[dbus_method("OnDeviceFound")]
    fn on_device_found(&self, remote_device: BluetoothDevice) {}

    #[dbus_method("OnDeviceCleared")]
    fn on_device_cleared(&self, remote_device: BluetoothDevice) {}

    #[dbus_method("OnDiscoveringChanged")]
    fn on_discovering_changed(&self, discovering: bool) {}

    #[dbus_method("OnSspRequest")]
    fn on_ssp_request(
        &self,
        remote_device: BluetoothDevice,
        cod: u32,
        variant: BtSspVariant,
        passkey: u32,
    ) {
    }

    #[dbus_method("OnBondStateChanged")]
    fn on_bond_state_changed(&self, status: u32, address: String, state: u32) {}
}

#[allow(dead_code)]
struct IBluetoothConnectionCallbackDBus {}

impl btstack::RPCProxy for IBluetoothConnectionCallbackDBus {
    // Dummy implementations just to satisfy impl RPCProxy requirements.
    fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
        0
    }
    fn get_object_id(&self) -> String {
        String::from("")
    }
    fn unregister(&mut self, _id: u32) -> bool {
        false
    }
    fn export_for_rpc(self: Box<Self>) {}
}

#[generate_dbus_exporter(
    export_bluetooth_connection_callback_dbus_obj,
    "org.chromium.bluetooth.BluetoothConnectionCallback"
)]
impl IBluetoothConnectionCallback for IBluetoothConnectionCallbackDBus {
    #[dbus_method("OnDeviceConnected")]
    fn on_device_connected(&self, remote_device: BluetoothDevice) {}

    #[dbus_method("OnDeviceDisconnected")]
    fn on_device_disconnected(&self, remote_device: BluetoothDevice) {}
}

pub(crate) struct BluetoothDBus {
    client_proxy: ClientDBusProxy,
}

impl BluetoothDBus {
    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothDBus {
        BluetoothDBus {
            client_proxy: ClientDBusProxy {
                conn: conn.clone(),
                bus_name: String::from("org.chromium.bluetooth"),
                objpath: make_object_path(index, "adapter"),
                interface: String::from("org.chromium.bluetooth.Bluetooth"),
            },
        }
    }
}

#[generate_dbus_interface_client]
impl IBluetooth for BluetoothDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn IBluetoothCallback + Send>) {
        dbus_generated!()
    }

    #[dbus_method("RegisterConnectionCallback")]
    fn register_connection_callback(
        &mut self,
        callback: Box<dyn IBluetoothConnectionCallback + Send>,
    ) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("UnregisterConnectionCallback")]
    fn unregister_connection_callback(&mut self, id: u32) -> bool {
        dbus_generated!()
    }

    fn enable(&mut self) -> bool {
        // Not implemented by server
        true
    }

    fn disable(&mut self) -> bool {
        // Not implemented by server
        true
    }

    #[dbus_method("GetAddress")]
    fn get_address(&self) -> String {
        dbus_generated!()
    }

    #[dbus_method("GetUuids")]
    fn get_uuids(&self) -> Vec<Uuid128Bit> {
        dbus_generated!()
    }

    #[dbus_method("GetName")]
    fn get_name(&self) -> String {
        dbus_generated!()
    }

    #[dbus_method("SetName")]
    fn set_name(&self, name: String) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetBluetoothClass")]
    fn get_bluetooth_class(&self) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("SetBluetoothClass")]
    fn set_bluetooth_class(&self, cod: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetDiscoverable")]
    fn get_discoverable(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetDiscoverableTimeout")]
    fn get_discoverable_timeout(&self) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("SetDiscoverable")]
    fn set_discoverable(&self, mode: bool, duration: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsMultiAdvertisementSupported")]
    fn is_multi_advertisement_supported(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsLeExtendedAdvertisingSupported")]
    fn is_le_extended_advertising_supported(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("StartDiscovery")]
    fn start_discovery(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("CancelDiscovery")]
    fn cancel_discovery(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("IsDiscovering")]
    fn is_discovering(&self) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetDiscoveryEndMillis")]
    fn get_discovery_end_millis(&self) -> u64 {
        dbus_generated!()
    }

    #[dbus_method("CreateBond")]
    fn create_bond(&self, device: BluetoothDevice, transport: BtTransport) -> bool {
        dbus_generated!()
    }

    #[dbus_method("CancelBondProcess")]
    fn cancel_bond_process(&self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("RemoveBond")]
    fn remove_bond(&self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetBondedDevices")]
    fn get_bonded_devices(&self) -> Vec<BluetoothDevice> {
        dbus_generated!()
    }

    #[dbus_method("GetBondState")]
    fn get_bond_state(&self, device: BluetoothDevice) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("SetPin")]
    fn set_pin(&self, device: BluetoothDevice, accept: bool, pin_code: Vec<u8>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetPasskey")]
    fn set_passkey(&self, device: BluetoothDevice, accept: bool, passkey: Vec<u8>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SetPairingConfirmation")]
    fn set_pairing_confirmation(&self, device: BluetoothDevice, accept: bool) -> bool {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteName")]
    fn get_remote_name(&self, device: BluetoothDevice) -> String {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteType")]
    fn get_remote_type(&self, device: BluetoothDevice) -> BtDeviceType {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteAlias")]
    fn get_remote_alias(&self, device: BluetoothDevice) -> String {
        dbus_generated!()
    }

    #[dbus_method("SetRemoteAlias")]
    fn set_remote_alias(&mut self, device: BluetoothDevice, new_alias: String) {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteClass")]
    fn get_remote_class(&self, device: BluetoothDevice) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("GetConnectionState")]
    fn get_connection_state(&self, device: BluetoothDevice) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("GetProfileConnectionState")]
    fn get_profile_connection_state(&self, profile: Profile) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("GetRemoteUuids")]
    fn get_remote_uuids(&self, device: BluetoothDevice) -> Vec<Uuid128Bit> {
        dbus_generated!()
    }

    #[dbus_method("FetchRemoteUuids")]
    fn fetch_remote_uuids(&self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("SdpSearch")]
    fn sdp_search(&self, device: BluetoothDevice, uuid: Uuid128Bit) -> bool {
        dbus_generated!()
    }

    #[dbus_method("ConnectAllEnabledProfiles")]
    fn connect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }

    #[dbus_method("DisconnectAllEnabledProfiles")]
    fn disconnect_all_enabled_profiles(&mut self, device: BluetoothDevice) -> bool {
        dbus_generated!()
    }
}

#[dbus_propmap(AdapterWithEnabled)]
pub struct AdapterWithEnabledDbus {
    hci_interface: i32,
    enabled: bool,
}

pub(crate) struct BluetoothManagerDBus {
    client_proxy: ClientDBusProxy,
}

impl BluetoothManagerDBus {
    pub(crate) fn new(conn: Arc<SyncConnection>) -> BluetoothManagerDBus {
        BluetoothManagerDBus {
            client_proxy: ClientDBusProxy {
                conn: conn.clone(),
                bus_name: String::from("org.chromium.bluetooth.Manager"),
                objpath: dbus::Path::new("/org/chromium/bluetooth/Manager").unwrap(),
                interface: String::from("org.chromium.bluetooth.Manager"),
            },
        }
    }

    pub(crate) fn is_valid(&self) -> bool {
        let result: Result<(bool,), _> = self.client_proxy.method_withresult("GetFlossEnabled", ());
        return result.is_ok();
    }
}

#[generate_dbus_interface_client]
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

#[allow(dead_code)]
struct IBluetoothManagerCallbackDBus {}

impl manager_service::RPCProxy for IBluetoothManagerCallbackDBus {
    // Placeholder implementations just to satisfy impl RPCProxy requirements.
    fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
        0
    }
    fn get_object_id(&self) -> String {
        String::from("")
    }
    fn unregister(&mut self, _id: u32) -> bool {
        false
    }
    fn export_for_rpc(self: Box<Self>) {}
}

#[generate_dbus_exporter(
    export_bluetooth_manager_callback_dbus_obj,
    "org.chromium.bluetooth.ManagerCallback"
)]
impl IBluetoothManagerCallback for IBluetoothManagerCallbackDBus {
    #[dbus_method("OnHciDeviceChanged")]
    fn on_hci_device_changed(&self, hci_interface: i32, present: bool) {}

    #[dbus_method("OnHciEnabledChanged")]
    fn on_hci_enabled_changed(&self, hci_interface: i32, enabled: bool) {}
}

pub(crate) struct BluetoothGattDBus {
    client_proxy: ClientDBusProxy,
}

impl BluetoothGattDBus {
    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> BluetoothGattDBus {
        BluetoothGattDBus {
            client_proxy: ClientDBusProxy {
                conn: conn.clone(),
                bus_name: String::from("org.chromium.bluetooth"),
                objpath: make_object_path(index, "gatt"),
                interface: String::from("org.chromium.bluetooth.BluetoothGatt"),
            },
        }
    }
}

#[generate_dbus_interface_client]
impl IBluetoothGatt for BluetoothGattDBus {
    fn register_scanner(&self, _callback: Box<dyn IScannerCallback + Send>) {
        // TODO(b/200066804): implement
    }

    fn unregister_scanner(&self, _scanner_id: i32) {
        // TODO(b/200066804): implement
    }

    fn start_scan(&self, _scanner_id: i32, _settings: ScanSettings, _filters: Vec<ScanFilter>) {
        // TODO(b/200066804): implement
    }

    fn stop_scan(&self, _scanner_id: i32) {
        // TODO(b/200066804): implement
    }

    #[dbus_method("RegisterClient")]
    fn register_client(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattCallback + Send>,
        eatt_support: bool,
    ) {
        dbus_generated!()
    }

    #[dbus_method("UnregisterClient")]
    fn unregister_client(&mut self, client_id: i32) {
        dbus_generated!()
    }

    #[dbus_method("ClientConnect")]
    fn client_connect(
        &self,
        client_id: i32,
        addr: String,
        is_direct: bool,
        transport: i32,
        opportunistic: bool,
        phy: i32,
    ) {
        dbus_generated!()
    }

    #[dbus_method("ClientDisconnect")]
    fn client_disconnect(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("ClientSetPreferredPhy")]
    fn client_set_preferred_phy(
        &self,
        client_id: i32,
        addr: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    ) {
        dbus_generated!()
    }

    #[dbus_method("ClientReadPhy")]
    fn client_read_phy(&mut self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("RefreshDevice")]
    fn refresh_device(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("DiscoverServices")]
    fn discover_services(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("DiscoverServiceByUuid")]
    fn discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String) {
        dbus_generated!()
    }

    #[dbus_method("ReadCharacteristic")]
    fn read_characteristic(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        dbus_generated!()
    }

    #[dbus_method("ReadUsingCharacteristicUuid")]
    fn read_using_characteristic_uuid(
        &self,
        client_id: i32,
        addr: String,
        uuid: String,
        start_handle: i32,
        end_handle: i32,
        auth_req: i32,
    ) {
        dbus_generated!()
    }

    #[dbus_method("WriteCharacteristic")]
    fn write_characteristic(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        write_type: GattWriteType,
        auth_req: i32,
        value: Vec<u8>,
    ) -> GattWriteRequestStatus {
        dbus_generated!()
    }

    #[dbus_method("ReadDescriptor")]
    fn read_descriptor(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        dbus_generated!()
    }

    #[dbus_method("WriteDescriptor")]
    fn write_descriptor(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        auth_req: i32,
        value: Vec<u8>,
    ) {
        dbus_generated!()
    }

    #[dbus_method("RegisterForNotification")]
    fn register_for_notification(&self, client_id: i32, addr: String, handle: i32, enable: bool) {
        dbus_generated!()
    }

    #[dbus_method("BeginReliableWrite")]
    fn begin_reliable_write(&mut self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("EndReliableWrite")]
    fn end_reliable_write(&mut self, client_id: i32, addr: String, execute: bool) {
        dbus_generated!()
    }

    #[dbus_method("ReadRemoteRssi")]
    fn read_remote_rssi(&self, client_id: i32, addr: String) {
        dbus_generated!()
    }

    #[dbus_method("ConfigureMtu")]
    fn configure_mtu(&self, client_id: i32, addr: String, mtu: i32) {
        dbus_generated!()
    }

    #[dbus_method("ConnectionParameterUpdate")]
    fn connection_parameter_update(
        &self,
        client_id: i32,
        addr: String,
        min_interval: i32,
        max_interval: i32,
        latency: i32,
        timeout: i32,
        min_ce_len: u16,
        max_ce_len: u16,
    ) {
        dbus_generated!()
    }
}

#[allow(dead_code)]
struct IBluetoothGattCallbackDBus {}

impl btstack::RPCProxy for IBluetoothGattCallbackDBus {
    // Placeholder implementations just to satisfy impl RPCProxy requirements.
    fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
        0
    }
    fn get_object_id(&self) -> String {
        String::from("")
    }
    fn unregister(&mut self, _id: u32) -> bool {
        false
    }
    fn export_for_rpc(self: Box<Self>) {}
}

#[generate_dbus_exporter(
    export_bluetooth_gatt_callback_dbus_obj,
    "org.chromium.bluetooth.BluetoothGattCallback"
)]
impl IBluetoothGattCallback for IBluetoothGattCallbackDBus {
    #[dbus_method("OnClientRegistered")]
    fn on_client_registered(&self, status: i32, client_id: i32) {}

    #[dbus_method("OnClientConnectionState")]
    fn on_client_connection_state(
        &self,
        status: i32,
        client_id: i32,
        connected: bool,
        addr: String,
    ) {
    }

    #[dbus_method("OnPhyUpdate")]
    fn on_phy_update(&self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {}

    #[dbus_method("OnPhyRead")]
    fn on_phy_read(&self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus) {}

    #[dbus_method("OnSearchComplete")]
    fn on_search_complete(&self, addr: String, services: Vec<BluetoothGattService>, status: i32) {}

    #[dbus_method("OnCharacteristicRead")]
    fn on_characteristic_read(&self, addr: String, status: i32, handle: i32, value: Vec<u8>) {}

    #[dbus_method("OnCharacteristicWrite")]
    fn on_characteristic_write(&self, addr: String, status: i32, handle: i32) {}

    #[dbus_method("OnExecuteWrite")]
    fn on_execute_write(&self, addr: String, status: i32) {}

    #[dbus_method("OnDescriptorRead")]
    fn on_descriptor_read(&self, addr: String, status: i32, handle: i32, value: Vec<u8>) {}

    #[dbus_method("OnDescriptorWrite")]
    fn on_descriptor_write(&self, addr: String, status: i32, handle: i32) {}

    #[dbus_method("OnNotify")]
    fn on_notify(&self, addr: String, handle: i32, value: Vec<u8>) {}

    #[dbus_method("OnReadRemoteRssi")]
    fn on_read_remote_rssi(&self, addr: String, rssi: i32, status: i32) {}

    #[dbus_method("OnConfigureMtu")]
    fn on_configure_mtu(&self, addr: String, mtu: i32, status: i32) {}

    #[dbus_method("OnConnectionUpdated")]
    fn on_connection_updated(
        &self,
        addr: String,
        interval: i32,
        latency: i32,
        timeout: i32,
        status: i32,
    ) {
    }

    #[dbus_method("OnServiceChanged")]
    fn on_service_changed(&self, addr: String) {}
}

pub(crate) struct SuspendDBus {
    client_proxy: ClientDBusProxy,
}

impl SuspendDBus {
    pub(crate) fn new(conn: Arc<SyncConnection>, index: i32) -> SuspendDBus {
        SuspendDBus {
            client_proxy: ClientDBusProxy {
                conn: conn.clone(),
                bus_name: String::from("org.chromium.bluetooth"),
                objpath: make_object_path(index, "suspend"),
                interface: String::from("org.chromium.bluetooth.Suspend"),
            },
        }
    }
}

#[generate_dbus_interface_client]
impl ISuspend for SuspendDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, _callback: Box<dyn ISuspendCallback + Send>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("UnregisterCallback")]
    fn unregister_callback(&mut self, _callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Suspend")]
    fn suspend(&self, _suspend_type: SuspendType) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("Resume")]
    fn resume(&self) -> bool {
        dbus_generated!()
    }
}

#[allow(dead_code)]
struct ISuspendCallbackDBus {}

impl btstack::RPCProxy for ISuspendCallbackDBus {
    // Placeholder implementations just to satisfy impl RPCProxy requirements.
    fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
        0
    }
    fn get_object_id(&self) -> String {
        String::from("")
    }
    fn unregister(&mut self, _id: u32) -> bool {
        false
    }
    fn export_for_rpc(self: Box<Self>) {}
}

#[generate_dbus_exporter(
    export_suspend_callback_dbus_obj,
    "org.chromium.bluetooth.SuspendCallback"
)]
impl ISuspendCallback for ISuspendCallbackDBus {
    #[dbus_method("OnCallbackRegistered")]
    fn on_callback_registered(&self, callback_id: u32) {}
    #[dbus_method("OnSuspendReady")]
    fn on_suspend_ready(&self, suspend_id: u32) {}
    #[dbus_method("OnResumed")]
    fn on_resumed(&self, suspend_id: u32) {}
}
