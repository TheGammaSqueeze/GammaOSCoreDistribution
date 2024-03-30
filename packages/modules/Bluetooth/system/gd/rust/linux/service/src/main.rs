use dbus::{channel::MatchingReceiver, message::MatchRule};
use dbus_crossroads::Crossroads;
use dbus_tokio::connection;
use futures::future;
use log::LevelFilter;
use std::error::Error;
use std::sync::{Arc, Mutex};
use syslog::{BasicLogger, Facility, Formatter3164};

use bt_topshim::{btif::get_btinterface, topstack};
use btstack::{
    bluetooth::{get_bt_dispatcher, Bluetooth, IBluetooth},
    bluetooth_gatt::BluetoothGatt,
    bluetooth_media::BluetoothMedia,
    suspend::Suspend,
    Stack,
};
use dbus_projection::DisconnectWatcher;

mod dbus_arg;
mod iface_bluetooth;
mod iface_bluetooth_gatt;
mod iface_bluetooth_media;
mod iface_suspend;

const DBUS_SERVICE_NAME: &str = "org.chromium.bluetooth";

/// Check command line arguments for target hci adapter (--hci=N). If no adapter
/// is set, default to 0.
fn get_adapter_index(args: &Vec<String>) -> i32 {
    for arg in args {
        if arg.starts_with("--hci=") {
            let num = (&arg[6..]).parse::<i32>();
            if num.is_ok() {
                return num.unwrap();
            }
        }
    }

    0
}

fn make_object_name(idx: i32, name: &str) -> String {
    String::from(format!("/org/chromium/bluetooth/hci{}/{}", idx, name))
}

/// Runs the Bluetooth daemon serving D-Bus IPC.
fn main() -> Result<(), Box<dyn Error>> {
    let formatter = Formatter3164 {
        facility: Facility::LOG_USER,
        hostname: None,
        process: "btadapterd".into(),
        pid: 0,
    };

    let logger = syslog::unix(formatter).expect("could not connect to syslog");
    let _ = log::set_boxed_logger(Box::new(BasicLogger::new(logger)))
        .map(|()| log::set_max_level(LevelFilter::Info));

    let (tx, rx) = Stack::create_channel();

    let intf = Arc::new(Mutex::new(get_btinterface().unwrap()));
    let suspend = Arc::new(Mutex::new(Box::new(Suspend::new(tx.clone()))));
    let bluetooth_gatt = Arc::new(Mutex::new(Box::new(BluetoothGatt::new(intf.clone()))));
    let bluetooth_media =
        Arc::new(Mutex::new(Box::new(BluetoothMedia::new(tx.clone(), intf.clone()))));
    let bluetooth = Arc::new(Mutex::new(Box::new(Bluetooth::new(
        tx.clone(),
        intf.clone(),
        bluetooth_media.clone(),
    ))));

    // Args don't include arg[0] which is the binary name
    let all_args = std::env::args().collect::<Vec<String>>();
    let args = all_args[1..].to_vec();

    let adapter_index = get_adapter_index(&args);

    topstack::get_runtime().block_on(async {
        // Connect to D-Bus system bus.
        let (resource, conn) = connection::new_system_sync()?;

        // The `resource` is a task that should be spawned onto a tokio compatible
        // reactor ASAP. If the resource ever finishes, we lost connection to D-Bus.
        tokio::spawn(async {
            let err = resource.await;
            panic!("Lost connection to D-Bus: {}", err);
        });

        // Request a service name and quit if not able to.
        conn.request_name(DBUS_SERVICE_NAME, false, true, false).await?;

        // Prepare D-Bus interfaces.
        let mut cr = Crossroads::new();
        cr.set_async_support(Some((
            conn.clone(),
            Box::new(|x| {
                tokio::spawn(x);
            }),
        )));

        // Run the stack main dispatch loop.
        topstack::get_runtime().spawn(Stack::dispatch(
            rx,
            bluetooth.clone(),
            bluetooth_gatt.clone(),
            bluetooth_media.clone(),
            suspend.clone(),
        ));

        // Set up the disconnect watcher to monitor client disconnects.
        let disconnect_watcher = Arc::new(Mutex::new(DisconnectWatcher::new()));
        disconnect_watcher.lock().unwrap().setup_watch(conn.clone()).await;

        // Register D-Bus method handlers of IBluetooth.
        iface_bluetooth::export_bluetooth_dbus_obj(
            make_object_name(adapter_index, "adapter"),
            conn.clone(),
            &mut cr,
            bluetooth.clone(),
            disconnect_watcher.clone(),
        );
        // Register D-Bus method handlers of IBluetoothGatt.
        iface_bluetooth_gatt::export_bluetooth_gatt_dbus_obj(
            make_object_name(adapter_index, "gatt"),
            conn.clone(),
            &mut cr,
            bluetooth_gatt.clone(),
            disconnect_watcher.clone(),
        );

        iface_bluetooth_media::export_bluetooth_media_dbus_obj(
            make_object_name(adapter_index, "media"),
            conn.clone(),
            &mut cr,
            bluetooth_media.clone(),
            disconnect_watcher.clone(),
        );

        iface_suspend::export_suspend_dbus_obj(
            make_object_name(adapter_index, "suspend"),
            conn.clone(),
            &mut cr,
            suspend,
            disconnect_watcher.clone(),
        );

        // Hold locks and initialize all interfaces. This must be done AFTER DBus is
        // initialized so DBus can properly enforce user policies.
        {
            intf.lock().unwrap().initialize(get_bt_dispatcher(tx.clone()), args);

            bluetooth_media.lock().unwrap().set_adapter(bluetooth.clone());

            let mut bluetooth = bluetooth.lock().unwrap();
            bluetooth.init_profiles();
            bluetooth.enable();

            bluetooth_gatt.lock().unwrap().init_profiles(tx.clone());
        }

        // Start listening on DBus after exporting interfaces and initializing
        // all bluetooth objects.
        conn.start_receive(
            MatchRule::new_method_call(),
            Box::new(move |msg, conn| {
                cr.handle_message(msg, conn).unwrap();
                true
            }),
        );

        // Serve clients forever.
        future::pending::<()>().await;
        unreachable!()
    })
}

#[cfg(test)]
mod tests {
    use crate::get_adapter_index;

    #[test]
    fn device_index_parsed() {
        // A few failing cases
        assert_eq!(get_adapter_index(&vec! {}), 0);
        assert_eq!(get_adapter_index(&vec! {"--bar".to_string(), "--hci".to_string()}), 0);
        assert_eq!(get_adapter_index(&vec! {"--hci=foo".to_string()}), 0);
        assert_eq!(get_adapter_index(&vec! {"--hci=12t".to_string()}), 0);

        // Some passing cases
        assert_eq!(get_adapter_index(&vec! {"--hci=12".to_string()}), 12);
        assert_eq!(get_adapter_index(&vec! {"--hci=1".to_string(), "--hci=2".to_string()}), 1);
    }
}
