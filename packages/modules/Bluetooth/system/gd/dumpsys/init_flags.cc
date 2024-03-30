/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "common/init_flags.h"

#include "dumpsys/init_flags.h"
#include "init_flags_generated.h"

namespace initFlags = bluetooth::common::init_flags;

// LINT.IfChange
flatbuffers::Offset<bluetooth::common::InitFlagsData> bluetooth::dumpsys::InitFlags::Dump(
    flatbuffers::FlatBufferBuilder* fb_builder) {
  auto title = fb_builder->CreateString("----- Init Flags -----");
  common::InitFlagsDataBuilder builder(*fb_builder);
  builder.add_title(title);
  builder.add_gd_advertising_enabled(true);
  builder.add_gd_scanning_enabled(true);
  builder.add_gd_acl_enabled(true);
  builder.add_gd_hci_enabled(true);
  builder.add_gd_controller_enabled(true);

  builder.add_always_send_services_if_gatt_disc_done_is_enabled(
      initFlags::always_send_services_if_gatt_disc_done_is_enabled());
  builder.add_asynchronously_start_l2cap_coc_is_enabled(initFlags::asynchronously_start_l2cap_coc_is_enabled());
  builder.add_btaa_hci_is_enabled(initFlags::btaa_hci_is_enabled());
  builder.add_bta_dm_clear_conn_id_on_client_close_is_enabled(
      initFlags::bta_dm_clear_conn_id_on_client_close_is_enabled());
  builder.add_btm_dm_flush_discovery_queue_on_search_cancel_is_enabled(
      initFlags::btm_dm_flush_discovery_queue_on_search_cancel_is_enabled());
  builder.add_clear_hidd_interrupt_cid_on_disconnect_is_enabled(
      initFlags::clear_hidd_interrupt_cid_on_disconnect_is_enabled());
  builder.add_delay_hidh_cleanup_until_hidh_ready_start_is_enabled(
      initFlags::delay_hidh_cleanup_until_hidh_ready_start_is_enabled());
  builder.add_gatt_robust_caching_server_is_enabled(initFlags::gatt_robust_caching_server_is_enabled());
  builder.add_gd_core_is_enabled(initFlags::gd_core_is_enabled());
  builder.add_gd_l2cap_is_enabled(initFlags::gd_l2cap_is_enabled());
  builder.add_gd_link_policy_is_enabled(initFlags::gd_link_policy_is_enabled());
  builder.add_gd_rust_is_enabled(initFlags::gd_rust_is_enabled());
  builder.add_gd_security_is_enabled(initFlags::gd_security_is_enabled());
  builder.add_get_hci_adapter(initFlags::get_hci_adapter());
  builder.add_irk_rotation_is_enabled(initFlags::irk_rotation_is_enabled());
  // is_debug_logging_enabled_for_tag -- skipped in dumpsys
  builder.add_leaudio_targeted_announcement_reconnection_mode_is_enabled(
      initFlags::leaudio_targeted_announcement_reconnection_mode_is_enabled());
  builder.add_logging_debug_enabled_for_all_is_enabled(initFlags::logging_debug_enabled_for_all_is_enabled());
  builder.add_pass_phy_update_callback_is_enabled(initFlags::pass_phy_update_callback_is_enabled());
  builder.add_queue_l2cap_coc_while_encrypting_is_enabled(initFlags::queue_l2cap_coc_while_encrypting_is_enabled());
  builder.add_sdp_serialization_is_enabled(initFlags::sdp_serialization_is_enabled());
  builder.add_sdp_skip_rnr_if_known_is_enabled(initFlags::sdp_skip_rnr_if_known_is_enabled());
  builder.add_trigger_advertising_callbacks_on_first_resume_after_pause_is_enabled(
      initFlags::trigger_advertising_callbacks_on_first_resume_after_pause_is_enabled());

  return builder.Finish();
}
// LINT.ThenChange(/system/gd/rust/common/src/init_flags.rs)
