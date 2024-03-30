//! Floss Bluetooth stack.
//!
//! This crate provides the API implementation of the Fluoride/GD Bluetooth
//! stack, independent of any RPC projection.

#[macro_use]
extern crate num_derive;

pub mod bluetooth;
pub mod bluetooth_gatt;
pub mod bluetooth_media;
pub mod suspend;
pub mod uuid;

use log::debug;
use std::sync::{Arc, Mutex};
use tokio::sync::mpsc::channel;
use tokio::sync::mpsc::{Receiver, Sender};

use crate::bluetooth::Bluetooth;
use crate::bluetooth_gatt::BluetoothGatt;
use crate::bluetooth_media::{BluetoothMedia, MediaActions};
use crate::suspend::Suspend;
use bt_topshim::{
    btif::BaseCallbacks,
    profiles::{
        a2dp::A2dpCallbacks, avrcp::AvrcpCallbacks, gatt::GattClientCallbacks,
        gatt::GattServerCallbacks, hfp::HfpCallbacks, hid_host::HHCallbacks, sdp::SdpCallbacks,
    },
};

#[derive(Clone, Debug)]
pub enum BluetoothCallbackType {
    Adapter,
    Connection,
}

/// Message types that are sent to the stack main dispatch loop.
pub enum Message {
    // Callbacks from libbluetooth
    A2dp(A2dpCallbacks),
    Avrcp(AvrcpCallbacks),
    Base(BaseCallbacks),
    GattClient(GattClientCallbacks),
    GattServer(GattServerCallbacks),
    HidHost(HHCallbacks),
    Hfp(HfpCallbacks),
    Sdp(SdpCallbacks),

    // Actions within the stack
    Media(MediaActions),

    // Client callback disconnections
    BluetoothCallbackDisconnected(u32, BluetoothCallbackType),

    // Update list of found devices and remove old instances.
    DeviceFreshnessCheck,

    // Suspend related
    SuspendCallbackRegistered(u32),
    SuspendCallbackDisconnected(u32),
}

/// Umbrella class for the Bluetooth stack.
pub struct Stack {}

impl Stack {
    /// Creates an mpsc channel for passing messages to the main dispatch loop.
    pub fn create_channel() -> (Sender<Message>, Receiver<Message>) {
        channel::<Message>(1)
    }

    /// Runs the main dispatch loop.
    pub async fn dispatch(
        mut rx: Receiver<Message>,
        bluetooth: Arc<Mutex<Box<Bluetooth>>>,
        bluetooth_gatt: Arc<Mutex<Box<BluetoothGatt>>>,
        bluetooth_media: Arc<Mutex<Box<BluetoothMedia>>>,
        suspend: Arc<Mutex<Box<Suspend>>>,
    ) {
        loop {
            let m = rx.recv().await;

            if m.is_none() {
                eprintln!("Message dispatch loop quit");
                break;
            }

            match m.unwrap() {
                Message::A2dp(a) => {
                    bluetooth_media.lock().unwrap().dispatch_a2dp_callbacks(a);
                }

                Message::Avrcp(av) => {
                    bluetooth_media.lock().unwrap().dispatch_avrcp_callbacks(av);
                }

                Message::Base(b) => {
                    bluetooth.lock().unwrap().dispatch_base_callbacks(b);
                }

                Message::GattClient(m) => {
                    bluetooth_gatt.lock().unwrap().dispatch_gatt_client_callbacks(m);
                }

                Message::GattServer(m) => {
                    // TODO(b/193685149): dispatch GATT server callbacks.
                    debug!("Unhandled Message::GattServer: {:?}", m);
                }

                Message::Hfp(hf) => {
                    bluetooth_media.lock().unwrap().dispatch_hfp_callbacks(hf);
                }

                Message::HidHost(_h) => {
                    // TODO(abps) - Handle hid host callbacks
                    debug!("Received HH callback");
                }

                Message::Sdp(s) => {
                    bluetooth.lock().unwrap().dispatch_sdp_callbacks(s);
                }

                Message::Media(action) => {
                    bluetooth_media.lock().unwrap().dispatch_media_actions(action);
                }

                Message::BluetoothCallbackDisconnected(id, cb_type) => {
                    bluetooth.lock().unwrap().callback_disconnected(id, cb_type);
                }

                Message::DeviceFreshnessCheck => {
                    bluetooth.lock().unwrap().trigger_freshness_check();
                }

                Message::SuspendCallbackRegistered(id) => {
                    suspend.lock().unwrap().callback_registered(id);
                }

                Message::SuspendCallbackDisconnected(id) => {
                    suspend.lock().unwrap().remove_callback(id);
                }
            }
        }
    }
}

/// Signifies that the object may be a proxy to a remote RPC object.
///
/// An object that implements RPCProxy trait signifies that the object may be a proxy to a remote
/// RPC object. Therefore the object may be disconnected and thus should implement
/// `register_disconnect` to let others observe the disconnection event.
pub trait RPCProxy {
    /// Registers disconnect observer that will be notified when the remote object is disconnected.
    fn register_disconnect(&mut self, f: Box<dyn Fn(u32) + Send>) -> u32;

    /// Returns the ID of the object. For example this would be an object path in D-Bus RPC.
    fn get_object_id(&self) -> String;

    /// Unregisters callback with this id.
    fn unregister(&mut self, id: u32) -> bool;

    /// Makes this object available for remote call.
    fn export_for_rpc(self: Box<Self>);
}
