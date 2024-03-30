//! Topshim is the main entry point from Rust code to C++.
//!
//! The Bluetooth stack is split into two parts: libbluetooth and the framework
//! above it. Libbluetooth is a combination of C/C++ and Rust that provides the
//! core Bluetooth functionality. It exposes top level apis in `bt_interface_t`
//! which can be used to drive the underlying implementation. Topshim provides
//! Rust apis to access this C/C++ interface and other top level interfaces in
//! order to use libbluetooth.
//!
//! The expected users of Topshim:
//!     * Floss (ChromeOS + Linux Bluetooth stack; uses D-Bus)
//!     * Topshim facade (used for testing)

#[macro_use]
extern crate lazy_static;
#[macro_use]
extern crate num_derive;
#[macro_use]
extern crate bitflags;

/// Bindgen bindings for accessing libbluetooth.
pub mod bindings;

pub mod btif;

/// Helper module for the topshim facade.
pub mod controller;

pub mod profiles;

pub mod topstack;
