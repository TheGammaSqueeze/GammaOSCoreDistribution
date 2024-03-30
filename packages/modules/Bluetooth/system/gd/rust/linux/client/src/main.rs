use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use dbus::channel::MatchingReceiver;
use dbus::message::MatchRule;
use dbus::nonblock::SyncConnection;
use dbus_crossroads::Crossroads;
use tokio::sync::mpsc;

use crate::callbacks::{BtCallback, BtConnectionCallback, BtManagerCallback, SuspendCallback};
use crate::command_handler::CommandHandler;
use crate::dbus_iface::{BluetoothDBus, BluetoothGattDBus, BluetoothManagerDBus, SuspendDBus};
use crate::editor::AsyncEditor;
use bt_topshim::topstack;
use btstack::bluetooth::{BluetoothDevice, IBluetooth};
use btstack::suspend::ISuspend;
use manager_service::iface_bluetooth_manager::IBluetoothManager;

mod callbacks;
mod command_handler;
mod console;
mod dbus_arg;
mod dbus_iface;
mod editor;

/// Context structure for the client. Used to keep track details about the active adapter and its
/// state.
pub(crate) struct ClientContext {
    /// List of adapters and whether they are enabled.
    pub(crate) adapters: HashMap<i32, bool>,

    // TODO(abps) - Change once we have multi-adapter support.
    /// The default adapter is also the active adapter. Defaults to 0.
    pub(crate) default_adapter: i32,

    /// Current adapter is enabled?
    pub(crate) enabled: bool,

    /// Current adapter is ready to be used?
    pub(crate) adapter_ready: bool,

    /// Current adapter address if known.
    pub(crate) adapter_address: Option<String>,

    /// Currently active bonding attempt. If it is not none, we are currently attempting to bond
    /// this device.
    pub(crate) bonding_attempt: Option<BluetoothDevice>,

    /// Is adapter discovering?
    pub(crate) discovering_state: bool,

    /// Devices found in current discovery session. List should be cleared when a new discovery
    /// session starts so that previous results don't pollute current search.
    pub(crate) found_devices: HashMap<String, BluetoothDevice>,

    /// If set, the registered GATT client id. None otherwise.
    pub(crate) gatt_client_id: Option<i32>,

    /// Proxy for manager interface.
    pub(crate) manager_dbus: BluetoothManagerDBus,

    /// Proxy for adapter interface. Only exists when the default adapter is enabled.
    pub(crate) adapter_dbus: Option<BluetoothDBus>,

    /// Proxy for GATT interface.
    pub(crate) gatt_dbus: Option<BluetoothGattDBus>,

    /// Proxy for suspend interface.
    pub(crate) suspend_dbus: Option<SuspendDBus>,

    /// Channel to send actions to take in the foreground
    fg: mpsc::Sender<ForegroundActions>,

    /// Internal DBus connection object.
    dbus_connection: Arc<SyncConnection>,

    /// Internal DBus crossroads object.
    dbus_crossroads: Arc<Mutex<Crossroads>>,
}

impl ClientContext {
    pub fn new(
        dbus_connection: Arc<SyncConnection>,
        dbus_crossroads: Arc<Mutex<Crossroads>>,
        tx: mpsc::Sender<ForegroundActions>,
    ) -> ClientContext {
        // Manager interface is almost always available but adapter interface
        // requires that the specific adapter is enabled.
        let manager_dbus = BluetoothManagerDBus::new(dbus_connection.clone());

        ClientContext {
            adapters: HashMap::new(),
            default_adapter: 0,
            enabled: false,
            adapter_ready: false,
            adapter_address: None,
            bonding_attempt: None,
            discovering_state: false,
            found_devices: HashMap::new(),
            gatt_client_id: None,
            manager_dbus,
            adapter_dbus: None,
            gatt_dbus: None,
            suspend_dbus: None,
            fg: tx,
            dbus_connection,
            dbus_crossroads,
        }
    }

    // Sets required values for the adapter when enabling or disabling
    fn set_adapter_enabled(&mut self, hci_interface: i32, enabled: bool) {
        print_info!("hci{} enabled = {}", hci_interface, enabled);

        self.adapters.entry(hci_interface).and_modify(|v| *v = enabled).or_insert(enabled);

        // When the default adapter's state is updated, we need to modify a few more things.
        // Only do this if we're not repeating the previous state.
        let prev_enabled = self.enabled;
        let default_adapter = self.default_adapter;
        if hci_interface == default_adapter && prev_enabled != enabled {
            self.enabled = enabled;
            self.adapter_ready = false;
            if enabled {
                self.create_adapter_proxy(hci_interface);
            } else {
                self.adapter_dbus = None;
            }
        }
    }

    // Creates adapter proxy, registers callbacks and initializes address.
    fn create_adapter_proxy(&mut self, idx: i32) {
        let conn = self.dbus_connection.clone();

        let dbus = BluetoothDBus::new(conn.clone(), idx);
        self.adapter_dbus = Some(dbus);

        let gatt_dbus = BluetoothGattDBus::new(conn.clone(), idx);
        self.gatt_dbus = Some(gatt_dbus);

        self.suspend_dbus = Some(SuspendDBus::new(conn.clone(), idx));

        // Trigger callback registration in the foreground
        let fg = self.fg.clone();
        tokio::spawn(async move {
            let adapter = String::from(format!("adapter{}", idx));
            let _ = fg.send(ForegroundActions::RegisterAdapterCallback(adapter)).await;
        });
    }

    // Foreground-only: Updates the adapter address.
    fn update_adapter_address(&mut self) -> String {
        let address = self.adapter_dbus.as_ref().unwrap().get_address();
        self.adapter_address = Some(address.clone());

        address
    }

    fn connect_all_enabled_profiles(&mut self, device: BluetoothDevice) {
        let fg = self.fg.clone();
        tokio::spawn(async move {
            let _ = fg.send(ForegroundActions::ConnectAllEnabledProfiles(device)).await;
        });
    }

    fn run_callback(&mut self, callback: Box<dyn Fn(Arc<Mutex<ClientContext>>) + Send>) {
        let fg = self.fg.clone();
        tokio::spawn(async move {
            let _ = fg.send(ForegroundActions::RunCallback(callback)).await;
        });
    }
}

/// Actions to take on the foreground loop. This allows us to queue actions in
/// callbacks that get run in the foreground context.
enum ForegroundActions {
    ConnectAllEnabledProfiles(BluetoothDevice), // Connect all enabled profiles for this device
    RunCallback(Box<dyn Fn(Arc<Mutex<ClientContext>>) + Send>), // Run callback in foreground
    RegisterAdapterCallback(String),            // Register callbacks for this adapter
    Readline(rustyline::Result<String>),        // Readline result from rustyline
}

/// Runs a command line program that interacts with a Bluetooth stack.
fn main() -> Result<(), Box<dyn std::error::Error>> {
    // TODO: Process command line arguments.

    topstack::get_runtime().block_on(async move {
        // Connect to D-Bus system bus.
        let (resource, conn) = dbus_tokio::connection::new_system_sync()?;

        // The `resource` is a task that should be spawned onto a tokio compatible
        // reactor ASAP. If the resource ever finishes, we lost connection to D-Bus.
        tokio::spawn(async {
            let err = resource.await;
            panic!("Lost connection to D-Bus: {}", err);
        });

        // Sets up Crossroads for receiving callbacks.
        let cr = Arc::new(Mutex::new(Crossroads::new()));
        cr.lock().unwrap().set_async_support(Some((
            conn.clone(),
            Box::new(|x| {
                tokio::spawn(x);
            }),
        )));
        let cr_clone = cr.clone();
        conn.start_receive(
            MatchRule::new_method_call(),
            Box::new(move |msg, conn| {
                cr_clone.lock().unwrap().handle_message(msg, conn).unwrap();
                true
            }),
        );

        // Accept foreground actions with mpsc
        let (tx, rx) = mpsc::channel::<ForegroundActions>(10);

        // Create the context needed for handling commands
        let context =
            Arc::new(Mutex::new(ClientContext::new(conn.clone(), cr.clone(), tx.clone())));

        // Check if manager interface is valid. We only print some help text before failing on the
        // first actual access to the interface (so we can also capture the actual reason the
        // interface isn't valid).
        if !context.lock().unwrap().manager_dbus.is_valid() {
            println!("Bluetooth manager doesn't seem to be working correctly.");
            println!("Check if service is running.");
            println!("...");
        }

        // TODO: Registering the callback should be done when btmanagerd is ready (detect with
        // ObjectManager).
        context.lock().unwrap().manager_dbus.register_callback(Box::new(BtManagerCallback::new(
            String::from("/org/chromium/bluetooth/client/bluetooth_manager_callback"),
            context.clone(),
            conn.clone(),
            cr.clone(),
        )));

        // Check if the default adapter is enabled. If yes, we should create the adapter proxy
        // right away.
        let default_adapter = context.lock().unwrap().default_adapter;
        if context.lock().unwrap().manager_dbus.get_adapter_enabled(default_adapter) {
            context.lock().unwrap().set_adapter_enabled(default_adapter, true);
        }

        let mut handler = CommandHandler::new(context.clone());

        let args: Vec<String> = std::env::args().collect();

        // Allow command line arguments to be read
        if args.len() > 1 {
            handler.process_cmd_line(&args[1], &args[2..].to_vec());
        } else {
            start_interactive_shell(handler, tx, rx, context).await;
        }
        return Result::Ok(());
    })
}

async fn start_interactive_shell(
    mut handler: CommandHandler,
    tx: mpsc::Sender<ForegroundActions>,
    mut rx: mpsc::Receiver<ForegroundActions>,
    context: Arc<Mutex<ClientContext>>,
) {
    let command_list = handler.get_command_list().clone();

    let semaphore_fg = Arc::new(tokio::sync::Semaphore::new(1));

    // Async task to keep reading new lines from user
    let semaphore = semaphore_fg.clone();
    tokio::spawn(async move {
        let editor = AsyncEditor::new(command_list);

        loop {
            // Wait until ForegroundAction::Readline finishes its task.
            let permit = semaphore.acquire().await;
            if permit.is_err() {
                break;
            };
            // Let ForegroundAction::Readline decide when it's done.
            permit.unwrap().forget();

            // It's good to do readline now.
            let result = editor.readline().await;
            let _ = tx.send(ForegroundActions::Readline(result)).await;
        }
    });

    loop {
        let m = rx.recv().await;

        if m.is_none() {
            break;
        }

        match m.unwrap() {
            ForegroundActions::ConnectAllEnabledProfiles(device) => {
                if context.lock().unwrap().adapter_ready {
                    context
                        .lock()
                        .unwrap()
                        .adapter_dbus
                        .as_mut()
                        .unwrap()
                        .connect_all_enabled_profiles(device);
                } else {
                    println!("Adapter isn't ready to connect profiles.");
                }
            }
            ForegroundActions::RunCallback(callback) => {
                callback(context.clone());
            }
            // Once adapter is ready, register callbacks, get the address and mark it as ready
            ForegroundActions::RegisterAdapterCallback(adapter) => {
                let cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/bluetooth_callback", adapter);
                let conn_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/bluetooth_conn_callback", adapter);
                let suspend_cb_objpath: String =
                    format!("/org/chromium/bluetooth/client/{}/suspend_callback", adapter);

                let dbus_connection = context.lock().unwrap().dbus_connection.clone();
                let dbus_crossroads = context.lock().unwrap().dbus_crossroads.clone();

                context.lock().unwrap().adapter_dbus.as_mut().unwrap().register_callback(Box::new(
                    BtCallback::new(
                        cb_objpath.clone(),
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    ),
                ));
                context
                    .lock()
                    .unwrap()
                    .adapter_dbus
                    .as_mut()
                    .unwrap()
                    .register_connection_callback(Box::new(BtConnectionCallback::new(
                        conn_cb_objpath,
                        context.clone(),
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    )));

                // When adapter is ready, Suspend API is also ready. Register as an observer.
                // TODO(b/224606285): Implement suspend debug utils in btclient.
                context.lock().unwrap().suspend_dbus.as_mut().unwrap().register_callback(Box::new(
                    SuspendCallback::new(
                        suspend_cb_objpath,
                        dbus_connection.clone(),
                        dbus_crossroads.clone(),
                    ),
                ));

                context.lock().unwrap().adapter_ready = true;
                let adapter_address = context.lock().unwrap().update_adapter_address();
                print_info!("Adapter {} is ready", adapter_address);
            }
            ForegroundActions::Readline(result) => match result {
                Err(_err) => {
                    break;
                }
                Ok(line) => {
                    let command_vec =
                        line.split(" ").map(|s| String::from(s)).collect::<Vec<String>>();
                    let cmd = &command_vec[0];
                    if cmd.eq("quit") {
                        break;
                    }
                    handler.process_cmd_line(
                        &String::from(cmd),
                        &command_vec[1..command_vec.len()].to_vec(),
                    );
                    // Ready to do readline again.
                    semaphore_fg.add_permits(1);
                }
            },
        }
    }

    semaphore_fg.close();

    print_info!("Client exiting");
}
