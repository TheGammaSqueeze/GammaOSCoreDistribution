//! Anything related to the GATT API (IBluetoothGatt).

use btif_macros::{btif_callback, btif_callbacks_dispatcher};

use bt_topshim::bindings::root::bluetooth::Uuid;
use bt_topshim::btif::{BluetoothInterface, RawAddress, Uuid128Bit};
use bt_topshim::profiles::gatt::{
    BtGattDbElement, BtGattNotifyParams, BtGattReadParams, Gatt, GattClientCallbacks,
    GattClientCallbacksDispatcher, GattScannerCallbacksDispatcher, GattServerCallbacksDispatcher,
    GattStatus,
};
use bt_topshim::topstack;

use log::{debug, warn};
use num_traits::cast::{FromPrimitive, ToPrimitive};
use std::collections::HashSet;
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::Sender;

use crate::{Message, RPCProxy};

struct Client {
    id: Option<i32>,
    uuid: Uuid128Bit,
    callback: Box<dyn IBluetoothGattCallback + Send>,
    is_congested: bool,

    // Queued on_characteristic_write callback.
    congestion_queue: Vec<(String, i32, i32)>,
}

struct Connection {
    conn_id: i32,
    address: String,
    client_id: i32,
}

struct ContextMap {
    // TODO(b/196635530): Consider using `multimap` for a more efficient implementation of get by
    // multiple keys.
    clients: Vec<Client>,
    connections: Vec<Connection>,
}

impl ContextMap {
    fn new() -> ContextMap {
        ContextMap { clients: vec![], connections: vec![] }
    }

    fn get_by_uuid(&self, uuid: &Uuid128Bit) -> Option<&Client> {
        self.clients.iter().find(|client| client.uuid == *uuid)
    }

    fn get_by_client_id(&self, client_id: i32) -> Option<&Client> {
        self.clients.iter().find(|client| client.id.is_some() && client.id.unwrap() == client_id)
    }

    fn get_by_client_id_mut(&mut self, client_id: i32) -> Option<&mut Client> {
        self.clients
            .iter_mut()
            .find(|client| client.id.is_some() && client.id.unwrap() == client_id)
    }

    fn get_address_by_conn_id(&self, conn_id: i32) -> Option<String> {
        match self.connections.iter().find(|conn| conn.conn_id == conn_id) {
            None => None,
            Some(conn) => Some(conn.address.clone()),
        }
    }

    fn get_client_by_conn_id(&self, conn_id: i32) -> Option<&Client> {
        match self.connections.iter().find(|conn| conn.conn_id == conn_id) {
            None => None,
            Some(conn) => self.get_by_client_id(conn.client_id),
        }
    }

    fn get_client_by_conn_id_mut(&mut self, conn_id: i32) -> Option<&mut Client> {
        let client_id = match self.connections.iter().find(|conn| conn.conn_id == conn_id) {
            None => return None,
            Some(conn) => conn.client_id,
        };

        self.get_by_client_id_mut(client_id)
    }

    fn add(&mut self, uuid: &Uuid128Bit, callback: Box<dyn IBluetoothGattCallback + Send>) {
        if self.get_by_uuid(uuid).is_some() {
            return;
        }

        self.clients.push(Client {
            id: None,
            uuid: uuid.clone(),
            callback,
            is_congested: false,
            congestion_queue: vec![],
        });
    }

    fn remove(&mut self, id: i32) {
        self.clients.retain(|client| !(client.id.is_some() && client.id.unwrap() == id));
    }

    fn set_client_id(&mut self, uuid: &Uuid128Bit, id: i32) {
        let client = self.clients.iter_mut().find(|client| client.uuid == *uuid);
        if client.is_none() {
            return;
        }

        client.unwrap().id = Some(id);
    }

    fn add_connection(&mut self, client_id: i32, conn_id: i32, address: &String) {
        if self.get_conn_id_from_address(client_id, address).is_some() {
            return;
        }

        self.connections.push(Connection { conn_id, address: address.clone(), client_id });
    }

    fn remove_connection(&mut self, _client_id: i32, conn_id: i32) {
        self.connections.retain(|conn| conn.conn_id != conn_id);
    }

    fn get_conn_id_from_address(&self, client_id: i32, address: &String) -> Option<i32> {
        match self
            .connections
            .iter()
            .find(|conn| conn.client_id == client_id && conn.address == *address)
        {
            None => None,
            Some(conn) => Some(conn.conn_id),
        }
    }
}

/// Defines the GATT API.
pub trait IBluetoothGatt {
    fn register_scanner(&self, callback: Box<dyn IScannerCallback + Send>);

    fn unregister_scanner(&self, scanner_id: i32);

    fn start_scan(&self, scanner_id: i32, settings: ScanSettings, filters: Vec<ScanFilter>);
    fn stop_scan(&self, scanner_id: i32);

    /// Registers a GATT Client.
    fn register_client(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattCallback + Send>,
        eatt_support: bool,
    );

    /// Unregisters a GATT Client.
    fn unregister_client(&mut self, client_id: i32);

    /// Initiates a GATT connection to a peer device.
    fn client_connect(
        &self,
        client_id: i32,
        addr: String,
        is_direct: bool,
        transport: i32,
        opportunistic: bool,
        phy: i32,
    );

    /// Disconnects a GATT connection.
    fn client_disconnect(&self, client_id: i32, addr: String);

    /// Sets preferred PHY.
    fn client_set_preferred_phy(
        &self,
        client_id: i32,
        addr: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    );

    /// Reads the PHY used by a peer.
    fn client_read_phy(&mut self, client_id: i32, addr: String);

    /// Clears the attribute cache of a device.
    fn refresh_device(&self, client_id: i32, addr: String);

    /// Enumerates all GATT services on a connected device.
    fn discover_services(&self, client_id: i32, addr: String);

    /// Search a GATT service on a connected device based on a UUID.
    fn discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String);

    /// Reads a characteristic on a remote device.
    fn read_characteristic(&self, client_id: i32, addr: String, handle: i32, auth_req: i32);

    /// Reads a characteristic on a remote device.
    fn read_using_characteristic_uuid(
        &self,
        client_id: i32,
        addr: String,
        uuid: String,
        start_handle: i32,
        end_handle: i32,
        auth_req: i32,
    );

    /// Writes a remote characteristic.
    fn write_characteristic(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        write_type: GattWriteType,
        auth_req: i32,
        value: Vec<u8>,
    ) -> GattWriteRequestStatus;

    /// Reads the descriptor for a given characteristic.
    fn read_descriptor(&self, client_id: i32, addr: String, handle: i32, auth_req: i32);

    /// Writes a remote descriptor for a given characteristic.
    fn write_descriptor(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        auth_req: i32,
        value: Vec<u8>,
    );

    /// Registers to receive notifications or indications for a given characteristic.
    fn register_for_notification(&self, client_id: i32, addr: String, handle: i32, enable: bool);

    /// Begins reliable write.
    fn begin_reliable_write(&mut self, client_id: i32, addr: String);

    /// Ends reliable write.
    fn end_reliable_write(&mut self, client_id: i32, addr: String, execute: bool);

    /// Requests RSSI for a given remote device.
    fn read_remote_rssi(&self, client_id: i32, addr: String);

    /// Configures the MTU of a given connection.
    fn configure_mtu(&self, client_id: i32, addr: String, mtu: i32);

    /// Requests a connection parameter update.
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
    );
}

#[derive(Debug, Default)]
/// Represents a GATT Descriptor.
pub struct BluetoothGattDescriptor {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub permissions: i32,
}

impl BluetoothGattDescriptor {
    fn new(uuid: Uuid128Bit, instance_id: i32, permissions: i32) -> BluetoothGattDescriptor {
        BluetoothGattDescriptor { uuid, instance_id, permissions }
    }
}

#[derive(Debug, Default)]
/// Represents a GATT Characteristic.
pub struct BluetoothGattCharacteristic {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub properties: i32,
    pub permissions: i32,
    pub key_size: i32,
    pub write_type: GattWriteType,
    pub descriptors: Vec<BluetoothGattDescriptor>,
}

impl BluetoothGattCharacteristic {
    pub const PROPERTY_BROADCAST: i32 = 0x01;
    pub const PROPERTY_READ: i32 = 0x02;
    pub const PROPERTY_WRITE_NO_RESPONSE: i32 = 0x04;
    pub const PROPERTY_WRITE: i32 = 0x08;
    pub const PROPERTY_NOTIFY: i32 = 0x10;
    pub const PROPERTY_INDICATE: i32 = 0x20;
    pub const PROPERTY_SIGNED_WRITE: i32 = 0x40;
    pub const PROPERTY_EXTENDED_PROPS: i32 = 0x80;

    fn new(
        uuid: Uuid128Bit,
        instance_id: i32,
        properties: i32,
        permissions: i32,
    ) -> BluetoothGattCharacteristic {
        BluetoothGattCharacteristic {
            uuid,
            instance_id,
            properties,
            permissions,
            write_type: if properties & BluetoothGattCharacteristic::PROPERTY_WRITE_NO_RESPONSE != 0
            {
                GattWriteType::WriteNoRsp
            } else {
                GattWriteType::Write
            },
            key_size: 16,
            descriptors: vec![],
        }
    }
}

#[derive(Debug, Default)]
/// Represents a GATT Service.
pub struct BluetoothGattService {
    pub uuid: Uuid128Bit,
    pub instance_id: i32,
    pub service_type: i32,
    pub characteristics: Vec<BluetoothGattCharacteristic>,
    pub included_services: Vec<BluetoothGattService>,
}

impl BluetoothGattService {
    fn new(uuid: Uuid128Bit, instance_id: i32, service_type: i32) -> BluetoothGattService {
        BluetoothGattService {
            uuid,
            instance_id,
            service_type,
            characteristics: vec![],
            included_services: vec![],
        }
    }
}

/// Callback for GATT Client API.
pub trait IBluetoothGattCallback: RPCProxy {
    /// When the `register_client` request is done.
    fn on_client_registered(&self, status: i32, client_id: i32);

    /// When there is a change in the state of a GATT client connection.
    fn on_client_connection_state(
        &self,
        status: i32,
        client_id: i32,
        connected: bool,
        addr: String,
    );

    /// When there is a change of PHY.
    fn on_phy_update(&self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus);

    /// The completion of IBluetoothGatt::read_phy.
    fn on_phy_read(&self, addr: String, tx_phy: LePhy, rx_phy: LePhy, status: GattStatus);

    /// When GATT db is available.
    fn on_search_complete(&self, addr: String, services: Vec<BluetoothGattService>, status: i32);

    /// The completion of IBluetoothGatt::read_characteristic.
    fn on_characteristic_read(&self, addr: String, status: i32, handle: i32, value: Vec<u8>);

    /// The completion of IBluetoothGatt::write_characteristic.
    fn on_characteristic_write(&self, addr: String, status: i32, handle: i32);

    /// When a reliable write is completed.
    fn on_execute_write(&self, addr: String, status: i32);

    /// The completion of IBluetoothGatt::read_descriptor.
    fn on_descriptor_read(&self, addr: String, status: i32, handle: i32, value: Vec<u8>);

    /// The completion of IBluetoothGatt::write_descriptor.
    fn on_descriptor_write(&self, addr: String, status: i32, handle: i32);

    /// When notification or indication is received.
    fn on_notify(&self, addr: String, handle: i32, value: Vec<u8>);

    /// The completion of IBluetoothGatt::read_remote_rssi.
    fn on_read_remote_rssi(&self, addr: String, rssi: i32, status: i32);

    /// The completion of IBluetoothGatt::configure_mtu.
    fn on_configure_mtu(&self, addr: String, mtu: i32, status: i32);

    /// When a connection parameter changes.
    fn on_connection_updated(
        &self,
        addr: String,
        interval: i32,
        latency: i32,
        timeout: i32,
        status: i32,
    );

    /// When there is an addition, removal, or change of a GATT service.
    fn on_service_changed(&self, addr: String);
}

/// Interface for scanner callbacks to clients, passed to `IBluetoothGatt::register_scanner`.
pub trait IScannerCallback {
    /// When the `register_scanner` request is done.
    fn on_scanner_registered(&self, status: i32, scanner_id: i32);
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u8)]
/// GATT write type.
enum GattDbElementType {
    PrimaryService = 0,
    SecondaryService = 1,
    IncludedService = 2,
    Characteristic = 3,
    Descriptor = 4,
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u8)]
/// GATT write type.
pub enum GattWriteType {
    Invalid = 0,
    WriteNoRsp = 1,
    Write = 2,
    WritePrepare = 3,
}

impl Default for GattWriteType {
    fn default() -> Self {
        GattWriteType::Write
    }
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u8)]
/// Represents LE PHY.
pub enum LePhy {
    Invalid = 0,
    Phy1m = 1,
    Phy2m = 2,
    PhyCoded = 3,
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u32)]
/// Scan type configuration.
pub enum ScanType {
    Active = 0,
    Passive = 1,
}

impl Default for ScanType {
    fn default() -> Self {
        ScanType::Active
    }
}

/// Represents RSSI configurations for hardware offloaded scanning.
// TODO(b/200066804): This is still a placeholder struct, not yet complete.
#[derive(Debug, Default)]
pub struct RSSISettings {
    pub low_threshold: i32,
    pub high_threshold: i32,
}

/// Represents scanning configurations to be passed to `IBluetoothGatt::start_scan`.
#[derive(Debug, Default)]
pub struct ScanSettings {
    pub interval: i32,
    pub window: i32,
    pub scan_type: ScanType,
    pub rssi_settings: RSSISettings,
}

/// Represents a scan filter to be passed to `IBluetoothGatt::start_scan`.
#[derive(Debug, Default)]
pub struct ScanFilter {}

/// Implementation of the GATT API (IBluetoothGatt).
pub struct BluetoothGatt {
    intf: Arc<Mutex<BluetoothInterface>>,
    gatt: Option<Gatt>,

    context_map: ContextMap,
    reliable_queue: HashSet<String>,
}

impl BluetoothGatt {
    /// Constructs a new IBluetoothGatt implementation.
    pub fn new(intf: Arc<Mutex<BluetoothInterface>>) -> BluetoothGatt {
        BluetoothGatt {
            intf: intf,
            gatt: None,
            context_map: ContextMap::new(),
            reliable_queue: HashSet::new(),
        }
    }

    pub fn init_profiles(&mut self, tx: Sender<Message>) {
        self.gatt = Gatt::new(&self.intf.lock().unwrap());
        self.gatt.as_mut().unwrap().initialize(
            GattClientCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    let tx_clone = tx.clone();
                    topstack::get_runtime().spawn(async move {
                        let _ = tx_clone.send(Message::GattClient(cb)).await;
                    });
                }),
            },
            GattServerCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    // TODO(b/193685149): Implement the callbacks
                    debug!("received Gatt server callback: {:?}", cb);
                }),
            },
            GattScannerCallbacksDispatcher {
                dispatch: Box::new(move |cb| {
                    debug!("received Gatt scanner callback: {:?}", cb);
                }),
            },
        );
    }
}

// Temporary util that covers only basic string conversion.
// TODO(b/193685325): Implement more UUID utils by using Uuid from gd/hci/uuid.h with cxx.
fn parse_uuid_string<T: Into<String>>(uuid: T) -> Option<Uuid> {
    let uuid = uuid.into();

    if uuid.len() != 32 {
        return None;
    }

    let mut raw = [0; 16];

    for i in 0..16 {
        let byte = u8::from_str_radix(&uuid[i * 2..i * 2 + 2], 16);
        if byte.is_err() {
            return None;
        }
        raw[i] = byte.unwrap();
    }

    Some(Uuid { uu: raw })
}

#[derive(Debug, FromPrimitive, ToPrimitive)]
#[repr(u8)]
/// Status of WriteCharacteristic methods.
pub enum GattWriteRequestStatus {
    Success = 0,
    Fail = 1,
    Busy = 2,
}

impl IBluetoothGatt for BluetoothGatt {
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

    fn register_client(
        &mut self,
        app_uuid: String,
        callback: Box<dyn IBluetoothGattCallback + Send>,
        eatt_support: bool,
    ) {
        let uuid = parse_uuid_string(app_uuid).unwrap();
        self.context_map.add(&uuid.uu, callback);
        self.gatt.as_ref().unwrap().client.register_client(&uuid, eatt_support);
    }

    fn unregister_client(&mut self, client_id: i32) {
        self.context_map.remove(client_id);
        self.gatt.as_ref().unwrap().client.unregister_client(client_id);
    }

    fn client_connect(
        &self,
        client_id: i32,
        addr: String,
        is_direct: bool,
        transport: i32,
        opportunistic: bool,
        phy: i32,
    ) {
        let address = match RawAddress::from_string(addr.clone()) {
            None => return,
            Some(addr) => addr,
        };

        self.gatt.as_ref().unwrap().client.connect(
            client_id,
            &address,
            is_direct,
            transport,
            opportunistic,
            phy,
        );
    }

    fn client_disconnect(&self, client_id: i32, address: String) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &address);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().client.disconnect(
            client_id,
            &RawAddress::from_string(address).unwrap(),
            conn_id.unwrap(),
        );
    }

    fn client_set_preferred_phy(
        &self,
        client_id: i32,
        address: String,
        tx_phy: LePhy,
        rx_phy: LePhy,
        phy_options: i32,
    ) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &address);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().client.set_preferred_phy(
            &RawAddress::from_string(address).unwrap(),
            tx_phy.to_u8().unwrap(),
            rx_phy.to_u8().unwrap(),
            phy_options as u16,
        );
    }

    fn client_read_phy(&mut self, client_id: i32, addr: String) {
        let address = match RawAddress::from_string(addr.clone()) {
            None => return,
            Some(addr) => addr,
        };

        self.gatt.as_mut().unwrap().client.read_phy(client_id, &address);
    }

    fn refresh_device(&self, client_id: i32, addr: String) {
        self.gatt
            .as_ref()
            .unwrap()
            .client
            .refresh(client_id, &RawAddress::from_string(addr).unwrap());
    }

    fn discover_services(&self, client_id: i32, addr: String) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().client.search_service(conn_id.unwrap(), None);
    }

    fn discover_service_by_uuid(&self, client_id: i32, addr: String, uuid: String) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        let uuid = parse_uuid_string(uuid);
        if uuid.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().client.search_service(conn_id.unwrap(), uuid);
    }

    fn read_characteristic(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().client.read_characteristic(
            conn_id.unwrap(),
            handle as u16,
            auth_req,
        );
    }

    fn read_using_characteristic_uuid(
        &self,
        client_id: i32,
        addr: String,
        uuid: String,
        start_handle: i32,
        end_handle: i32,
        auth_req: i32,
    ) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        let uuid = parse_uuid_string(uuid);
        if uuid.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().client.read_using_characteristic_uuid(
            conn_id.unwrap(),
            &uuid.unwrap(),
            start_handle as u16,
            end_handle as u16,
            auth_req,
        );
    }

    fn write_characteristic(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        mut write_type: GattWriteType,
        auth_req: i32,
        value: Vec<u8>,
    ) -> GattWriteRequestStatus {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return GattWriteRequestStatus::Fail;
        }

        if self.reliable_queue.contains(&addr) {
            write_type = GattWriteType::WritePrepare;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        // TODO(b/200070162): Handle concurrent write characteristic.

        self.gatt.as_ref().unwrap().client.write_characteristic(
            conn_id.unwrap(),
            handle as u16,
            write_type.to_i32().unwrap(),
            auth_req,
            &value,
        );

        return GattWriteRequestStatus::Success;
    }

    fn read_descriptor(&self, client_id: i32, addr: String, handle: i32, auth_req: i32) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().client.read_descriptor(
            conn_id.unwrap(),
            handle as u16,
            auth_req,
        );
    }

    fn write_descriptor(
        &self,
        client_id: i32,
        addr: String,
        handle: i32,
        auth_req: i32,
        value: Vec<u8>,
    ) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        self.gatt.as_ref().unwrap().client.write_descriptor(
            conn_id.unwrap(),
            handle as u16,
            auth_req,
            &value,
        );
    }

    fn register_for_notification(&self, client_id: i32, addr: String, handle: i32, enable: bool) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        // TODO(b/200065274): Perform check on restricted handles.

        if enable {
            self.gatt.as_ref().unwrap().client.register_for_notification(
                client_id,
                &RawAddress::from_string(addr).unwrap(),
                handle as u16,
            );
        } else {
            self.gatt.as_ref().unwrap().client.deregister_for_notification(
                client_id,
                &RawAddress::from_string(addr).unwrap(),
                handle as u16,
            );
        }
    }

    fn begin_reliable_write(&mut self, _client_id: i32, addr: String) {
        self.reliable_queue.insert(addr);
    }

    fn end_reliable_write(&mut self, client_id: i32, addr: String, execute: bool) {
        self.reliable_queue.remove(&addr);

        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        self.gatt
            .as_ref()
            .unwrap()
            .client
            .execute_write(conn_id.unwrap(), if execute { 1 } else { 0 });
    }

    fn read_remote_rssi(&self, client_id: i32, addr: String) {
        self.gatt
            .as_ref()
            .unwrap()
            .client
            .read_remote_rssi(client_id, &RawAddress::from_string(addr).unwrap());
    }

    fn configure_mtu(&self, client_id: i32, addr: String, mtu: i32) {
        let conn_id = self.context_map.get_conn_id_from_address(client_id, &addr);
        if conn_id.is_none() {
            return;
        }

        self.gatt.as_ref().unwrap().client.configure_mtu(conn_id.unwrap(), mtu);
    }

    fn connection_parameter_update(
        &self,
        _client_id: i32,
        addr: String,
        min_interval: i32,
        max_interval: i32,
        latency: i32,
        timeout: i32,
        min_ce_len: u16,
        max_ce_len: u16,
    ) {
        self.gatt.as_ref().unwrap().client.conn_parameter_update(
            &RawAddress::from_string(addr).unwrap(),
            min_interval,
            max_interval,
            latency,
            timeout,
            min_ce_len,
            max_ce_len,
        );
    }
}

#[btif_callbacks_dispatcher(BluetoothGatt, dispatch_gatt_client_callbacks, GattClientCallbacks)]
pub(crate) trait BtifGattClientCallbacks {
    #[btif_callback(RegisterClient)]
    fn register_client_cb(&mut self, status: i32, client_id: i32, app_uuid: Uuid);

    #[btif_callback(Connect)]
    fn connect_cb(&mut self, conn_id: i32, status: i32, client_id: i32, addr: RawAddress);

    #[btif_callback(Disconnect)]
    fn disconnect_cb(&mut self, conn_id: i32, status: i32, client_id: i32, addr: RawAddress);

    #[btif_callback(SearchComplete)]
    fn search_complete_cb(&mut self, conn_id: i32, status: i32);

    #[btif_callback(RegisterForNotification)]
    fn register_for_notification_cb(
        &mut self,
        conn_id: i32,
        registered: i32,
        status: i32,
        handle: u16,
    );

    #[btif_callback(Notify)]
    fn notify_cb(&mut self, conn_id: i32, data: BtGattNotifyParams);

    #[btif_callback(ReadCharacteristic)]
    fn read_characteristic_cb(&mut self, conn_id: i32, status: i32, data: BtGattReadParams);

    #[btif_callback(WriteCharacteristic)]
    fn write_characteristic_cb(
        &mut self,
        conn_id: i32,
        status: i32,
        handle: u16,
        len: u16,
        value: *const u8,
    );

    #[btif_callback(ReadDescriptor)]
    fn read_descriptor_cb(&mut self, conn_id: i32, status: i32, data: BtGattReadParams);

    #[btif_callback(WriteDescriptor)]
    fn write_descriptor_cb(
        &mut self,
        conn_id: i32,
        status: i32,
        handle: u16,
        len: u16,
        value: *const u8,
    );

    #[btif_callback(ExecuteWrite)]
    fn execute_write_cb(&mut self, conn_id: i32, status: i32);

    #[btif_callback(ReadRemoteRssi)]
    fn read_remote_rssi_cb(&mut self, client_id: i32, addr: RawAddress, rssi: i32, status: i32);

    #[btif_callback(ConfigureMtu)]
    fn configure_mtu_cb(&mut self, conn_id: i32, status: i32, mtu: i32);

    #[btif_callback(Congestion)]
    fn congestion_cb(&mut self, conn_id: i32, congested: bool);

    #[btif_callback(GetGattDb)]
    fn get_gatt_db_cb(&mut self, conn_id: i32, elements: Vec<BtGattDbElement>, count: i32);

    #[btif_callback(PhyUpdated)]
    fn phy_updated_cb(&mut self, conn_id: i32, tx_phy: u8, rx_phy: u8, status: u8);

    #[btif_callback(ConnUpdated)]
    fn conn_updated_cb(
        &mut self,
        conn_id: i32,
        interval: u16,
        latency: u16,
        timeout: u16,
        status: u8,
    );

    #[btif_callback(ServiceChanged)]
    fn service_changed_cb(&self, conn_id: i32);

    #[btif_callback(ReadPhy)]
    fn read_phy_cb(&mut self, client_id: i32, addr: RawAddress, tx_phy: u8, rx_phy: u8, status: u8);
}

impl BtifGattClientCallbacks for BluetoothGatt {
    fn register_client_cb(&mut self, status: i32, client_id: i32, app_uuid: Uuid) {
        self.context_map.set_client_id(&app_uuid.uu, client_id);

        let client = self.context_map.get_by_uuid(&app_uuid.uu);
        if client.is_none() {
            warn!("Warning: Client not registered for UUID {:?}", app_uuid.uu);
            return;
        }

        let callback = &client.unwrap().callback;
        callback.on_client_registered(status, client_id);
    }

    fn connect_cb(&mut self, conn_id: i32, status: i32, client_id: i32, addr: RawAddress) {
        if status == 0 {
            self.context_map.add_connection(client_id, conn_id, &addr.to_string());
        }

        let client = self.context_map.get_by_client_id(client_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_client_connection_state(
            status,
            client_id,
            match GattStatus::from_i32(status) {
                None => false,
                Some(gatt_status) => gatt_status == GattStatus::Success,
            },
            addr.to_string(),
        );
    }

    fn disconnect_cb(&mut self, conn_id: i32, status: i32, client_id: i32, addr: RawAddress) {
        self.context_map.remove_connection(client_id, conn_id);
        let client = self.context_map.get_by_client_id(client_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_client_connection_state(
            status,
            client_id,
            match GattStatus::from_i32(status) {
                None => false,
                Some(gatt_status) => gatt_status == GattStatus::Success,
            },
            addr.to_string(),
        );
    }

    fn search_complete_cb(&mut self, conn_id: i32, _status: i32) {
        // Gatt DB is ready!
        self.gatt.as_ref().unwrap().client.get_gatt_db(conn_id);
    }

    fn register_for_notification_cb(
        &mut self,
        _conn_id: i32,
        _registered: i32,
        _status: i32,
        _handle: u16,
    ) {
        // No-op.
    }

    fn notify_cb(&mut self, conn_id: i32, data: BtGattNotifyParams) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_notify(
            RawAddress { val: data.bda.address }.to_string(),
            data.handle as i32,
            data.value[0..data.len as usize].to_vec(),
        );
    }

    fn read_characteristic_cb(&mut self, conn_id: i32, status: i32, data: BtGattReadParams) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_characteristic_read(
            address.unwrap().to_string(),
            status,
            data.handle as i32,
            data.value.value[0..data.value.len as usize].to_vec(),
        );
    }

    fn write_characteristic_cb(
        &mut self,
        conn_id: i32,
        mut status: i32,
        handle: u16,
        _len: u16,
        _value: *const u8,
    ) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        // TODO(b/200070162): Design how to handle concurrent write characteristic to the same
        // peer.

        let client = self.context_map.get_client_by_conn_id_mut(conn_id);
        if client.is_none() {
            return;
        }

        let client = client.unwrap();

        if client.is_congested {
            if status == GattStatus::Congested.to_i32().unwrap() {
                status = GattStatus::Success.to_i32().unwrap();
            }

            client.congestion_queue.push((address.unwrap().to_string(), status, handle as i32));
            return;
        }

        client.callback.on_characteristic_write(
            address.unwrap().to_string(),
            status,
            handle as i32,
        );
    }

    fn read_descriptor_cb(&mut self, conn_id: i32, status: i32, data: BtGattReadParams) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_descriptor_read(
            address.unwrap().to_string(),
            status,
            data.handle as i32,
            data.value.value[0..data.value.len as usize].to_vec(),
        );
    }

    fn write_descriptor_cb(
        &mut self,
        conn_id: i32,
        status: i32,
        handle: u16,
        _len: u16,
        _value: *const u8,
    ) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_descriptor_write(
            address.unwrap().to_string(),
            status,
            handle as i32,
        );
    }

    fn execute_write_cb(&mut self, conn_id: i32, status: i32) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_execute_write(address.unwrap().to_string(), status);
    }

    fn read_remote_rssi_cb(&mut self, client_id: i32, addr: RawAddress, rssi: i32, status: i32) {
        let client = self.context_map.get_by_client_id(client_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_read_remote_rssi(addr.to_string(), rssi, status);
    }

    fn configure_mtu_cb(&mut self, conn_id: i32, status: i32, mtu: i32) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        let addr = self.context_map.get_address_by_conn_id(conn_id);
        if addr.is_none() {
            return;
        }

        client.unwrap().callback.on_configure_mtu(addr.unwrap(), mtu, status);
    }

    fn congestion_cb(&mut self, conn_id: i32, congested: bool) {
        let client = self.context_map.get_client_by_conn_id_mut(conn_id);
        if client.is_none() {
            return;
        }

        let client = client.unwrap();

        client.is_congested = congested;
        if !client.is_congested {
            for callback in client.congestion_queue.iter() {
                client.callback.on_characteristic_write(callback.0.clone(), callback.1, callback.2);
            }
            client.congestion_queue.clear();
        }
    }

    fn get_gatt_db_cb(&mut self, conn_id: i32, elements: Vec<BtGattDbElement>, _count: i32) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        let mut db_out: Vec<BluetoothGattService> = vec![];

        for elem in elements {
            match GattDbElementType::from_u32(elem.type_).unwrap() {
                GattDbElementType::PrimaryService | GattDbElementType::SecondaryService => {
                    db_out.push(BluetoothGattService::new(
                        elem.uuid.uu,
                        elem.id as i32,
                        elem.type_ as i32,
                    ));
                    // TODO(b/200065274): Mark restricted services.
                }

                GattDbElementType::Characteristic => {
                    match db_out.last_mut() {
                        Some(s) => s.characteristics.push(BluetoothGattCharacteristic::new(
                            elem.uuid.uu,
                            elem.id as i32,
                            elem.properties as i32,
                            0,
                        )),
                        None => {
                            // TODO(b/193685325): Log error.
                        }
                    }
                    // TODO(b/200065274): Mark restricted characteristics.
                }

                GattDbElementType::Descriptor => {
                    match db_out.last_mut() {
                        Some(s) => match s.characteristics.last_mut() {
                            Some(c) => c.descriptors.push(BluetoothGattDescriptor::new(
                                elem.uuid.uu,
                                elem.id as i32,
                                0,
                            )),
                            None => {
                                // TODO(b/193685325): Log error.
                            }
                        },
                        None => {
                            // TODO(b/193685325): Log error.
                        }
                    }
                    // TODO(b/200065274): Mark restricted descriptors.
                }

                GattDbElementType::IncludedService => {
                    match db_out.last_mut() {
                        Some(s) => {
                            s.included_services.push(BluetoothGattService::new(
                                elem.uuid.uu,
                                elem.id as i32,
                                elem.type_ as i32,
                            ));
                        }
                        None => {
                            // TODO(b/193685325): Log error.
                        }
                    }
                }
            }
        }

        client.unwrap().callback.on_search_complete(address.unwrap().to_string(), db_out, 0);
    }

    fn phy_updated_cb(&mut self, conn_id: i32, tx_phy: u8, rx_phy: u8, status: u8) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        client.unwrap().callback.on_phy_update(
            address.unwrap(),
            LePhy::from_u8(tx_phy).unwrap(),
            LePhy::from_u8(rx_phy).unwrap(),
            GattStatus::from_u8(status).unwrap(),
        );
    }

    fn read_phy_cb(
        &mut self,
        client_id: i32,
        addr: RawAddress,
        tx_phy: u8,
        rx_phy: u8,
        status: u8,
    ) {
        let client = self.context_map.get_by_client_id(client_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_phy_read(
            addr.to_string(),
            LePhy::from_u8(tx_phy).unwrap(),
            LePhy::from_u8(rx_phy).unwrap(),
            GattStatus::from_u8(status).unwrap(),
        );
    }

    fn conn_updated_cb(
        &mut self,
        conn_id: i32,
        interval: u16,
        latency: u16,
        timeout: u16,
        status: u8,
    ) {
        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        client.unwrap().callback.on_connection_updated(
            address.unwrap(),
            interval as i32,
            latency as i32,
            timeout as i32,
            status as i32,
        );
    }

    fn service_changed_cb(&self, conn_id: i32) {
        let address = self.context_map.get_address_by_conn_id(conn_id);
        if address.is_none() {
            return;
        }

        let client = self.context_map.get_client_by_conn_id(conn_id);
        if client.is_none() {
            return;
        }

        client.unwrap().callback.on_service_changed(address.unwrap());
    }
}

#[cfg(test)]
mod tests {
    struct TestBluetoothGattCallback {
        id: String,
    }

    impl TestBluetoothGattCallback {
        fn new(id: String) -> TestBluetoothGattCallback {
            TestBluetoothGattCallback { id }
        }
    }

    impl IBluetoothGattCallback for TestBluetoothGattCallback {
        fn on_client_registered(&self, _status: i32, _client_id: i32) {}
        fn on_client_connection_state(
            &self,
            _status: i32,
            _client_id: i32,
            _connected: bool,
            _addr: String,
        ) {
        }

        fn on_phy_update(
            &self,
            _addr: String,
            _tx_phy: LePhy,
            _rx_phy: LePhy,
            _status: GattStatus,
        ) {
        }

        fn on_phy_read(&self, _addr: String, _tx_phy: LePhy, _rx_phy: LePhy, _status: GattStatus) {}

        fn on_search_complete(
            &self,
            _addr: String,
            _services: Vec<BluetoothGattService>,
            _status: i32,
        ) {
        }

        fn on_characteristic_read(
            &self,
            _addr: String,
            _status: i32,
            _handle: i32,
            _value: Vec<u8>,
        ) {
        }

        fn on_characteristic_write(&self, _addr: String, _status: i32, _handle: i32) {}

        fn on_execute_write(&self, _addr: String, _status: i32) {}

        fn on_descriptor_read(&self, _addr: String, _status: i32, _handle: i32, _value: Vec<u8>) {}

        fn on_descriptor_write(&self, _addr: String, _status: i32, _handle: i32) {}

        fn on_notify(&self, _addr: String, _handle: i32, _value: Vec<u8>) {}

        fn on_read_remote_rssi(&self, _addr: String, _rssi: i32, _status: i32) {}

        fn on_configure_mtu(&self, _addr: String, _mtu: i32, _status: i32) {}

        fn on_connection_updated(
            &self,
            _addr: String,
            _interval: i32,
            _latency: i32,
            _timeout: i32,
            _status: i32,
        ) {
        }

        fn on_service_changed(&self, _addr: String) {}
    }

    impl RPCProxy for TestBluetoothGattCallback {
        fn register_disconnect(&mut self, _f: Box<dyn Fn(u32) + Send>) -> u32 {
            0
        }

        fn get_object_id(&self) -> String {
            self.id.clone()
        }

        fn unregister(&mut self, _id: u32) -> bool {
            false
        }

        fn export_for_rpc(self: Box<Self>) {}
    }

    use super::*;

    #[test]
    fn test_uuid_from_string() {
        let uuid = parse_uuid_string("abcdef");
        assert!(uuid.is_none());

        let uuid = parse_uuid_string("0123456789abcdef0123456789abcdef");
        assert!(uuid.is_some());
        let expected: [u8; 16] = [
            0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef, 0x01, 0x23, 0x45, 0x67, 0x89, 0xab,
            0xcd, 0xef,
        ];
        assert_eq!(Uuid { uu: expected }, uuid.unwrap());
    }

    #[test]
    fn test_context_map_clients() {
        let mut map = ContextMap::new();

        // Add client 1.
        let callback1 = Box::new(TestBluetoothGattCallback::new(String::from("Callback 1")));
        let uuid1 = parse_uuid_string("00000000000000000000000000000001").unwrap().uu;
        map.add(&uuid1, callback1);
        let found = map.get_by_uuid(&uuid1);
        assert!(found.is_some());
        assert_eq!("Callback 1", found.unwrap().callback.get_object_id());

        // Add client 2.
        let callback2 = Box::new(TestBluetoothGattCallback::new(String::from("Callback 2")));
        let uuid2 = parse_uuid_string("00000000000000000000000000000002").unwrap().uu;
        map.add(&uuid2, callback2);
        let found = map.get_by_uuid(&uuid2);
        assert!(found.is_some());
        assert_eq!("Callback 2", found.unwrap().callback.get_object_id());

        // Set client ID and get by client ID.
        map.set_client_id(&uuid1, 3);
        let found = map.get_by_client_id(3);
        assert!(found.is_some());

        // Remove client 1.
        map.remove(3);
        let found = map.get_by_uuid(&uuid1);
        assert!(found.is_none());
    }

    #[test]
    fn test_context_map_connections() {
        let mut map = ContextMap::new();
        let client_id = 1;

        map.add_connection(client_id, 3, &String::from("aa:bb:cc:dd:ee:ff"));
        map.add_connection(client_id, 4, &String::from("11:22:33:44:55:66"));

        let found = map.get_conn_id_from_address(client_id, &String::from("aa:bb:cc:dd:ee:ff"));
        assert!(found.is_some());
        assert_eq!(3, found.unwrap());

        let found = map.get_conn_id_from_address(client_id, &String::from("11:22:33:44:55:66"));
        assert!(found.is_some());
        assert_eq!(4, found.unwrap());
    }
}
