//! Suspend/Resume API.

use crate::{Message, RPCProxy};
use log::warn;
use std::collections::HashMap;
use tokio::sync::mpsc::Sender;

/// Defines the Suspend/Resume API.
///
/// This API is exposed by `btadapterd` and independent of the suspend/resume detection mechanism
/// which depends on the actual operating system the daemon runs on. Possible clients of this API
/// include `btmanagerd` with Chrome OS `powerd` integration, `btmanagerd` with systemd Inhibitor
/// interface, or any script hooked to suspend/resume events.
pub trait ISuspend {
    /// Adds an observer to suspend events.
    ///
    /// Returns true if the callback can be registered.
    fn register_callback(&mut self, callback: Box<dyn ISuspendCallback + Send>) -> bool;

    /// Removes an observer to suspend events.
    ///
    /// Returns true if the callback can be removed, false if `callback_id` is not recognized.
    fn unregister_callback(&mut self, callback_id: u32) -> bool;

    /// Prepares the stack for suspend, identified by `suspend_id`.
    ///
    /// Returns a positive number identifying the suspend if it can be started. If there is already
    /// a suspend, that active suspend id is returned.
    fn suspend(&self, suspend_type: SuspendType) -> u32;

    /// Undoes previous suspend preparation identified by `suspend_id`.
    ///
    /// Returns true if suspend can be resumed, and false if there is no suspend to resume.
    fn resume(&self) -> bool;
}

/// Suspend events.
pub trait ISuspendCallback: RPCProxy {
    /// Triggered when a callback is registered and given an identifier `callback_id`.
    fn on_callback_registered(&self, callback_id: u32);

    /// Triggered when the stack is ready for suspend and tell the observer the id of the suspend.
    fn on_suspend_ready(&self, suspend_id: u32);

    /// Triggered when the stack has resumed the previous suspend.
    fn on_resumed(&self, suspend_id: u32);
}

#[derive(FromPrimitive, ToPrimitive)]
#[repr(u32)]
pub enum SuspendType {
    NoWakesAllowed,
    AllowWakeFromHid,
    Other,
}

/// Implementation of the suspend API.
pub struct Suspend {
    tx: Sender<Message>,
    callbacks: HashMap<u32, Box<dyn ISuspendCallback + Send>>,
}

impl Suspend {
    pub fn new(tx: Sender<Message>) -> Suspend {
        Self { tx, callbacks: HashMap::new() }
    }

    pub(crate) fn callback_registered(&mut self, id: u32) {
        match self.callbacks.get(&id) {
            Some(callback) => callback.on_callback_registered(id),
            None => warn!("Suspend callback {} does not exist", id),
        }
    }

    pub(crate) fn remove_callback(&mut self, id: u32) -> bool {
        match self.callbacks.get_mut(&id) {
            Some(callback) => {
                callback.unregister(id);
                self.callbacks.remove(&id);
                true
            }
            None => false,
        }
    }
}

impl ISuspend for Suspend {
    fn register_callback(&mut self, mut callback: Box<dyn ISuspendCallback + Send>) -> bool {
        let tx = self.tx.clone();

        let id = callback.register_disconnect(Box::new(move |cb_id| {
            let tx = tx.clone();
            tokio::spawn(async move {
                let _result = tx.send(Message::SuspendCallbackDisconnected(cb_id)).await;
            });
        }));

        let tx = self.tx.clone();
        tokio::spawn(async move {
            let _result = tx.send(Message::SuspendCallbackRegistered(id)).await;
        });

        self.callbacks.insert(id, callback);
        true
    }

    fn unregister_callback(&mut self, callback_id: u32) -> bool {
        self.remove_callback(callback_id)
    }

    fn suspend(&self, _suspend_type: SuspendType) -> u32 {
        todo!()
    }

    fn resume(&self) -> bool {
        todo!()
    }
}
