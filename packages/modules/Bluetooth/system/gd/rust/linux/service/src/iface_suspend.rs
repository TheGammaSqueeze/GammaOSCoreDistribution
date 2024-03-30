use btstack::suspend::{ISuspend, ISuspendCallback, SuspendType};
use btstack::RPCProxy;

use crate::dbus_arg::{DBusArg, DBusArgError};

use dbus_macros::{dbus_method, dbus_proxy_obj, generate_dbus_exporter};

use dbus_projection::{dbus_generated, impl_dbus_arg_enum, DisconnectWatcher};

use dbus::nonblock::SyncConnection;
use dbus::strings::Path;

use num_traits::cast::{FromPrimitive, ToPrimitive};

use std::sync::Arc;

impl_dbus_arg_enum!(SuspendType);

#[allow(dead_code)]
struct ISuspendDBus {}

#[generate_dbus_exporter(export_suspend_dbus_obj, "org.chromium.bluetooth.Suspend")]
impl ISuspend for ISuspendDBus {
    #[dbus_method("RegisterCallback")]
    fn register_callback(&mut self, callback: Box<dyn ISuspendCallback + Send>) -> bool {
        dbus_generated!()
    }

    #[dbus_method("UnregisterCallback")]
    fn unregister_callback(&mut self, callback_id: u32) -> bool {
        dbus_generated!()
    }

    #[dbus_method("Suspend")]
    fn suspend(&self, suspend_type: SuspendType) -> u32 {
        dbus_generated!()
    }

    #[dbus_method("Resume")]
    fn resume(&self) -> bool {
        dbus_generated!()
    }
}

#[allow(dead_code)]
struct SuspendCallbackDBus {}

#[dbus_proxy_obj(SuspendCallback, "org.chromium.bluetooth.SuspendCallback")]
impl ISuspendCallback for SuspendCallbackDBus {
    #[dbus_method("OnCallbackRegistered")]
    fn on_callback_registered(&self, callback_id: u32) {
        dbus_generated!()
    }
    #[dbus_method("OnSuspendReady")]
    fn on_suspend_ready(&self, suspend_id: u32) {
        dbus_generated!()
    }
    #[dbus_method("OnResumed")]
    fn on_resumed(&self, suspend_id: u32) {
        dbus_generated!()
    }
}
