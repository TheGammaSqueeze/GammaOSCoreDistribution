#[cxx::bridge(namespace = bluetooth::common::init_flags)]
mod ffi {
    extern "Rust" {
        fn load(flags: Vec<String>);
        fn set_all_for_testing();

        fn always_send_services_if_gatt_disc_done_is_enabled() -> bool;
        fn asynchronously_start_l2cap_coc_is_enabled() -> bool;
        fn btaa_hci_is_enabled() -> bool;
        fn bta_dm_clear_conn_id_on_client_close_is_enabled() -> bool;
        fn btm_dm_flush_discovery_queue_on_search_cancel_is_enabled() -> bool;
        fn delay_hidh_cleanup_until_hidh_ready_start_is_enabled() -> bool;
        fn clear_hidd_interrupt_cid_on_disconnect_is_enabled() -> bool;
        fn finite_att_timeout_is_enabled() -> bool;
        fn gatt_robust_caching_client_is_enabled() -> bool;
        fn gatt_robust_caching_server_is_enabled() -> bool;
        fn gd_core_is_enabled() -> bool;
        fn gd_l2cap_is_enabled() -> bool;
        fn gd_link_policy_is_enabled() -> bool;
        fn gd_rust_is_enabled() -> bool;
        fn gd_security_is_enabled() -> bool;
        fn get_hci_adapter() -> i32;
        fn irk_rotation_is_enabled() -> bool;
        fn is_debug_logging_enabled_for_tag(tag: &str) -> bool;
        fn leaudio_targeted_announcement_reconnection_mode_is_enabled() -> bool;
        fn logging_debug_enabled_for_all_is_enabled() -> bool;
        fn pass_phy_update_callback_is_enabled() -> bool;
        fn queue_l2cap_coc_while_encrypting_is_enabled() -> bool;
        fn sdp_serialization_is_enabled() -> bool;
        fn sdp_skip_rnr_if_known_is_enabled() -> bool;
        fn trigger_advertising_callbacks_on_first_resume_after_pause_is_enabled() -> bool;
    }
}

use bt_common::init_flags::*;
