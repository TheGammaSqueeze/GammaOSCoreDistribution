use log::LevelFilter;
use serde_json::{Map, Value};

// Directory for Bluetooth hci devices
pub const HCI_DEVICES_DIR: &str = "/sys/class/bluetooth";

// File to store the Bluetooth daemon to use (bluez or floss)
const BLUETOOTH_DAEMON_CURRENT: &str = "/var/lib/bluetooth/bluetooth-daemon.current";

// File to store the config for BluetoothManager
const BTMANAGERD_CONF: &str = "/var/lib/bluetooth/btmanagerd.json";

pub fn is_floss_enabled() -> bool {
    match std::fs::read(BLUETOOTH_DAEMON_CURRENT) {
        Ok(v) => {
            let content = std::str::from_utf8(&v);
            match content {
                Ok(version) => version.contains("floss"),
                Err(_) => false,
            }
        }
        Err(_) => false,
    }
}

pub fn write_floss_enabled(enabled: bool) -> bool {
    std::fs::write(
        BLUETOOTH_DAEMON_CURRENT,
        match enabled {
            true => "floss",
            _ => "bluez",
        },
    )
    .is_ok()
}

pub fn read_config() -> std::io::Result<String> {
    std::fs::read_to_string(BTMANAGERD_CONF)
}

pub fn get_log_level() -> Option<LevelFilter> {
    get_log_level_internal(read_config().ok()?)
}

fn get_log_level_internal(config: String) -> Option<LevelFilter> {
    serde_json::from_str::<Value>(config.as_str())
        .ok()?
        .get("log_level")?
        .as_str()?
        .parse::<LevelFilter>()
        .ok()
}

/// Returns whether hci N is enabled in config; defaults to true.
pub fn is_hci_n_enabled(n: i32) -> bool {
    match read_config().ok().and_then(|config| is_hci_n_enabled_internal(config, n)) {
        Some(v) => v,
        _ => true,
    }
}

fn is_hci_n_enabled_internal(config: String, n: i32) -> Option<bool> {
    serde_json::from_str::<Value>(config.as_str())
        .ok()?
        .get(format!("hci{}", n))?
        .as_object()?
        .get("enabled")?
        .as_bool()
}

// When we initialize BluetoothManager, we need to make sure the file is a well-formatted json.
pub fn fix_config_file_format() -> bool {
    match read_config() {
        Ok(s) => match serde_json::from_str::<Value>(s.as_str()) {
            Ok(_) => true,
            _ => std::fs::write(BTMANAGERD_CONF, "{}").is_ok(),
        },
        _ => std::fs::write(BTMANAGERD_CONF, "{}").is_ok(),
    }
}

pub fn modify_hci_n_enabled(n: i32, enabled: bool) -> bool {
    if !fix_config_file_format() {
        false
    } else {
        match read_config()
            .ok()
            .and_then(|config| modify_hci_n_enabled_internal(config, n, enabled))
        {
            Some(s) => std::fs::write(BTMANAGERD_CONF, s).is_ok(),
            _ => false,
        }
    }
}

fn modify_hci_n_enabled_internal(config: String, n: i32, enabled: bool) -> Option<String> {
    let hci_interface = format!("hci{}", n);
    let mut o = serde_json::from_str::<Value>(config.as_str()).ok()?;
    match o.get_mut(hci_interface.clone()) {
        Some(section) => {
            section.as_object_mut()?.insert("enabled".to_string(), Value::Bool(enabled));
            serde_json::ser::to_string_pretty(&o).ok()
        }
        _ => {
            let mut entry_map = Map::new();
            entry_map.insert("enabled".to_string(), Value::Bool(enabled));
            o.as_object_mut()?.insert(hci_interface, Value::Object(entry_map));
            serde_json::ser::to_string_pretty(&o).ok()
        }
    }
}

pub fn list_hci_devices() -> Vec<i32> {
    hci_devices_string_to_int(list_hci_devices_string())
}

fn list_hci_devices_string() -> Vec<String> {
    match std::fs::read_dir(HCI_DEVICES_DIR) {
        Ok(entries) => entries
            .map(|e| e.unwrap().path().file_name().unwrap().to_str().unwrap().to_string())
            .collect::<Vec<_>>(),
        _ => Vec::new(),
    }
}

fn hci_devices_string_to_int(devices: Vec<String>) -> Vec<i32> {
    devices
        .into_iter()
        .filter_map(|e| if e.starts_with("hci") { e[3..].parse::<i32>().ok() } else { None })
        .collect()
}

pub fn list_pid_files(pid_dir: &str) -> Vec<String> {
    match std::fs::read_dir(pid_dir) {
        Ok(entries) => entries
            .map(|e| e.unwrap().path().file_name().unwrap().to_str().unwrap().to_string())
            .collect::<Vec<_>>(),
        _ => Vec::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn is_hci_n_enabled_internal_wrapper(config: String, n: i32) -> bool {
        is_hci_n_enabled_internal(config, n).or(Some(true)).unwrap()
    }

    #[test]
    fn parse_log_level() {
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"error\"}".to_string()).unwrap(),
            LevelFilter::Error
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"warn\"}".to_string()).unwrap(),
            LevelFilter::Warn
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"info\"}".to_string()).unwrap(),
            LevelFilter::Info
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"debug\"}".to_string()).unwrap(),
            LevelFilter::Debug
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"trace\"}".to_string()).unwrap(),
            LevelFilter::Trace
        );
        assert_eq!(
            get_log_level_internal("{\"log_level\": \"random\"}".to_string()).is_none(),
            true
        );
    }

    #[test]
    fn parse_hci0_enabled() {
        assert_eq!(
            is_hci_n_enabled_internal_wrapper("{\"hci0\":\n{\"enabled\": true}}".to_string(), 0),
            true
        );
    }

    #[test]
    fn modify_hci0_enabled() {
        let modified_string =
            modify_hci_n_enabled_internal("{\"hci0\":\n{\"enabled\": false}}".to_string(), 0, true)
                .unwrap();
        assert_eq!(is_hci_n_enabled_internal_wrapper(modified_string, 0), true);
    }

    #[test]
    fn modify_hci0_enabled_from_empty() {
        let modified_string = modify_hci_n_enabled_internal("{}".to_string(), 0, true).unwrap();
        assert_eq!(is_hci_n_enabled_internal_wrapper(modified_string, 0), true);
    }

    #[test]
    fn parse_hci0_not_enabled() {
        assert_eq!(
            is_hci_n_enabled_internal_wrapper("{\"hci0\":\n{\"enabled\": false}}".to_string(), 0),
            false
        );
    }

    #[test]
    fn parse_hci1_not_present() {
        assert_eq!(
            is_hci_n_enabled_internal_wrapper("{\"hci0\":\n{\"enabled\": true}}".to_string(), 1),
            true
        );
    }

    #[test]
    fn test_hci_devices_string_to_int_none() {
        assert_eq!(hci_devices_string_to_int(vec!["somethingelse".to_string()]), Vec::<i32>::new());
    }

    #[test]
    fn test_hci_devices_string_to_int_one() {
        assert_eq!(hci_devices_string_to_int(vec!["hci0".to_string()]), vec![0]);
    }

    #[test]
    fn test_hci_devices_string_to_int_two() {
        assert_eq!(
            hci_devices_string_to_int(vec!["hci0".to_string(), "hci1".to_string()]),
            vec![0, 1]
        );
    }
}
