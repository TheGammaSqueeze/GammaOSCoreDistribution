pub mod iface_bluetooth_manager;

// TODO: This is a copy of RPCProxy that is in btstack create. Find a better home for this struct
// that avoids code duplication.
/// Signifies that the object may be a proxy to a remote RPC object.
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
