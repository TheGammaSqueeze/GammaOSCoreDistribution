mod bluetooth_manager;
mod bluetooth_manager_dbus;
mod config_util;
mod dbus_arg;
mod state_machine;

use crate::bluetooth_manager::BluetoothManager;
use dbus::channel::MatchingReceiver;
use dbus::message::MatchRule;
use dbus_crossroads::Crossroads;
use dbus_projection::DisconnectWatcher;
use dbus_tokio::connection;
use log::LevelFilter;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Mutex};
use syslog::{BasicLogger, Facility, Formatter3164};

#[derive(Clone)]
struct ManagerContext {
    proxy: state_machine::StateMachineProxy,
    floss_enabled: Arc<AtomicBool>,
}

#[tokio::main]
pub async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let formatter = Formatter3164 {
        facility: Facility::LOG_USER,
        hostname: None,
        process: "btmanagerd".into(),
        pid: 0,
    };

    let logger = syslog::unix(formatter).expect("could not connect to syslog");
    let _ = log::set_boxed_logger(Box::new(BasicLogger::new(logger)))
        .map(|()| log::set_max_level(config_util::get_log_level().unwrap_or(LevelFilter::Info)));

    // Initialize config util
    config_util::fix_config_file_format();

    // Connect to the D-Bus system bus (this is blocking, unfortunately).
    let (resource, conn) = connection::new_system_sync()?;

    // Determine whether to use upstart or systemd
    let args: Vec<String> = std::env::args().collect();
    let invoker = if args.len() > 1 {
        match &args[1][0..] {
            "--systemd" | "-s" => state_machine::Invoker::SystemdInvoker,
            _ => state_machine::Invoker::UpstartInvoker,
        }
    } else {
        state_machine::Invoker::UpstartInvoker
    };

    let context = state_machine::start_new_state_machine_context(invoker);
    let proxy = context.get_proxy();
    let manager_context = ManagerContext {
        proxy: proxy,
        floss_enabled: Arc::new(AtomicBool::new(config_util::is_floss_enabled())),
    };

    // The resource is a task that should be spawned onto a tokio compatible
    // reactor ASAP. If the resource ever finishes, you lost connection to D-Bus.
    tokio::spawn(async {
        let err = resource.await;
        panic!("Lost connection to D-Bus: {}", err);
    });

    // Let's request a name on the bus, so that clients can find us.
    conn.request_name("org.chromium.bluetooth.Manager", false, true, false).await?;

    // Create a new crossroads instance.
    // The instance is configured so that introspection and properties interfaces
    // are added by default on object path additions.
    let mut cr = Crossroads::new();

    // Enable async support for the crossroads instance.
    cr.set_async_support(Some((
        conn.clone(),
        Box::new(|x| {
            tokio::spawn(x);
        }),
    )));

    // Object manager is necessary for clients (to inform them when Bluetooth is
    // available). Create it at root (/) so subsequent additions generate
    // InterfaceAdded and InterfaceRemoved signals.
    cr.set_object_manager_support(Some(conn.clone()));
    cr.insert("/", &[cr.object_manager()], {});

    let bluetooth_manager = Arc::new(Mutex::new(Box::new(BluetoothManager::new(manager_context))));

    // Set up the disconnect watcher to monitor client disconnects.
    let disconnect_watcher = Arc::new(Mutex::new(DisconnectWatcher::new()));
    disconnect_watcher.lock().unwrap().setup_watch(conn.clone()).await;

    // Let's add the "/org/chromium/bluetooth/Manager" path, which implements
    // the org.chromium.bluetooth.Manager interface, to the crossroads instance.
    bluetooth_manager_dbus::export_bluetooth_manager_dbus_obj(
        "/org/chromium/bluetooth/Manager",
        conn.clone(),
        &mut cr,
        bluetooth_manager.clone(),
        disconnect_watcher.clone(),
    );

    // We add the Crossroads instance to the connection so that incoming method calls will be handled.
    conn.start_receive(
        MatchRule::new_method_call(),
        Box::new(move |msg, conn| {
            cr.handle_message(msg, conn).unwrap();
            true
        }),
    );

    tokio::spawn(async move {
        state_machine::mainloop(context, bluetooth_manager).await;
    });

    std::future::pending::<()>().await;

    // Run forever.
    unreachable!()
}
