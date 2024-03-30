/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "gd/rust/topshim/btav/btav_shim.h"

#include <cstdio>
#include <memory>

#include "base/callback.h"
#include "gd/rust/topshim/common/utils.h"
#include "include/hardware/avrcp/avrcp.h"
#include "include/hardware/bluetooth.h"
#include "rust/cxx.h"
#include "src/profiles/a2dp.rs.h"
#include "src/profiles/avrcp.rs.h"
#include "types/raw_address.h"

namespace rusty = ::bluetooth::topshim::rust;

namespace bluetooth::avrcp {
class AvrcpMediaInterfaceImpl : public MediaInterface {
 public:
  void SendKeyEvent([[maybe_unused]] uint8_t key, [[maybe_unused]] KeyState state) {}

  void GetSongInfo([[maybe_unused]] SongInfoCallback cb) override {}

  void GetPlayStatus([[maybe_unused]] PlayStatusCallback cb) override {}

  void GetNowPlayingList([[maybe_unused]] NowPlayingCallback cb) override {}

  void GetMediaPlayerList([[maybe_unused]] MediaListCallback cb) override {}

  void GetFolderItems(
      [[maybe_unused]] uint16_t player_id,
      [[maybe_unused]] std::string media_id,
      [[maybe_unused]] FolderItemsCallback folder_cb) override {}

  void SetBrowsedPlayer(
      [[maybe_unused]] uint16_t player_id, [[maybe_unused]] SetBrowsedPlayerCallback browse_cb) override {}

  void RegisterUpdateCallback([[maybe_unused]] MediaCallbacks* callback) override {}

  void UnregisterUpdateCallback([[maybe_unused]] MediaCallbacks* callback) override {}

  void PlayItem(
      [[maybe_unused]] uint16_t player_id,
      [[maybe_unused]] bool now_playing,
      [[maybe_unused]] std::string media_id) override {}

  void SetActiveDevice([[maybe_unused]] const RawAddress& address) override {}
};

class VolumeInterfaceImpl : public VolumeInterface {
 public:
  void DeviceConnected([[maybe_unused]] const RawAddress& bdaddr) override {
    rusty::avrcp_absolute_volume_enabled(false);
  }

  void DeviceConnected([[maybe_unused]] const RawAddress& bdaddr, VolumeChangedCb cb) override {
    volumeCb = std::move(cb);
    rusty::avrcp_absolute_volume_enabled(true);
  }

  void DeviceDisconnected([[maybe_unused]] const RawAddress& bdaddr) override {
    volumeCb.Reset();
    rusty::avrcp_absolute_volume_enabled(false);
  }

  // Set TG's (Android, ChromeOS) volume.
  void SetVolume(int8_t volume) override {
    rusty::avrcp_absolute_volume_update(volume);
  }

  // Set CT's (headsets, speakers) volume.
  void SetDeviceVolume(int8_t volume) {
    if (!volumeCb || volume < 0) return;

    volumeCb.Run(volume);
  }

 private:
  VolumeInterface::VolumeChangedCb volumeCb;
};

}  // namespace bluetooth::avrcp

namespace bluetooth {
namespace topshim {
namespace rust {
namespace internal {
static A2dpIntf* g_a2dpif;
static AvrcpIntf* g_avrcpif;

static A2dpCodecConfig to_rust_codec_config(const btav_a2dp_codec_config_t& config) {
  A2dpCodecConfig rconfig = {.codec_type = static_cast<uint8_t>(config.codec_type),
                             .codec_priority = config.codec_priority,
                             .sample_rate = static_cast<uint8_t>(config.sample_rate),
                             .bits_per_sample = static_cast<uint8_t>(config.bits_per_sample),
                             .channel_mode = static_cast<uint8_t>(config.channel_mode),
                             .codec_specific_1 = config.codec_specific_1,
                             .codec_specific_2 = config.codec_specific_2,
                             .codec_specific_3 = config.codec_specific_3,
                             .codec_specific_4 = config.codec_specific_4};
  return rconfig;
}

static btav_a2dp_codec_config_t from_rust_codec_config(const A2dpCodecConfig& rconfig) {
  btav_a2dp_codec_config_t config = {
      .codec_type = static_cast<btav_a2dp_codec_index_t>(rconfig.codec_type),
      .codec_priority = static_cast<btav_a2dp_codec_priority_t>(rconfig.codec_priority),
      .sample_rate = static_cast<btav_a2dp_codec_sample_rate_t>(rconfig.sample_rate),
      .bits_per_sample = static_cast<btav_a2dp_codec_bits_per_sample_t>(rconfig.bits_per_sample),
      .channel_mode = static_cast<btav_a2dp_codec_channel_mode_t>(rconfig.channel_mode),
      .codec_specific_1 = rconfig.codec_specific_1,
      .codec_specific_2 = rconfig.codec_specific_2,
      .codec_specific_3 = rconfig.codec_specific_3,
      .codec_specific_4 = rconfig.codec_specific_4,
  };
  return config;
}

static ::rust::Vec<A2dpCodecConfig> to_rust_codec_config_vec(const std::vector<btav_a2dp_codec_config_t>& configs) {
  ::rust::Vec<A2dpCodecConfig> rconfigs;

  for (btav_a2dp_codec_config_t c : configs) {
    rconfigs.push_back(to_rust_codec_config(c));
  }
  return rconfigs;
}

static void connection_state_cb(const RawAddress& bd_addr, btav_connection_state_t state) {
  RustRawAddress addr = rusty::CopyToRustAddress(bd_addr);
  rusty::connection_state_callback(addr, state);
}
static void audio_state_cb(const RawAddress& bd_addr, btav_audio_state_t state) {
  RustRawAddress addr = rusty::CopyToRustAddress(bd_addr);
  rusty::audio_state_callback(addr, state);
}
static void audio_config_cb(
    const RawAddress& bd_addr,
    btav_a2dp_codec_config_t codec_config,
    std::vector<btav_a2dp_codec_config_t> codecs_local_capabilities,
    std::vector<btav_a2dp_codec_config_t> codecs_selectable_capabilities) {
  RustRawAddress addr = rusty::CopyToRustAddress(bd_addr);
  A2dpCodecConfig cfg = to_rust_codec_config(codec_config);
  ::rust::Vec<A2dpCodecConfig> lcaps = to_rust_codec_config_vec(codecs_local_capabilities);
  ::rust::Vec<A2dpCodecConfig> scaps = to_rust_codec_config_vec(codecs_selectable_capabilities);
  rusty::audio_config_callback(addr, cfg, lcaps, scaps);
}
static bool mandatory_codec_preferred_cb(const RawAddress& bd_addr) {
  RustRawAddress addr = rusty::CopyToRustAddress(bd_addr);
  rusty::mandatory_codec_preferred_callback(addr);
  return true;
}

btav_source_callbacks_t g_callbacks = {
    sizeof(btav_source_callbacks_t),
    connection_state_cb,
    audio_state_cb,
    audio_config_cb,
    mandatory_codec_preferred_cb,
};
}  // namespace internal

A2dpIntf::~A2dpIntf() {
  // TODO
}

std::unique_ptr<A2dpIntf> GetA2dpProfile(const unsigned char* btif) {
  if (internal::g_a2dpif) std::abort();

  const bt_interface_t* btif_ = reinterpret_cast<const bt_interface_t*>(btif);

  auto a2dpif = std::make_unique<A2dpIntf>(
      reinterpret_cast<const btav_source_interface_t*>(btif_->get_profile_interface("a2dp")));
  internal::g_a2dpif = a2dpif.get();
  return a2dpif;
}

int A2dpIntf::init() const {
  std::vector<btav_a2dp_codec_config_t> a;
  std::vector<btav_a2dp_codec_config_t> b;
  return intf_->init(&internal::g_callbacks, 1, a, b);
}

int A2dpIntf::connect(RustRawAddress bt_addr) const {
  RawAddress addr = rusty::CopyFromRustAddress(bt_addr);
  return intf_->connect(addr);
}
int A2dpIntf::disconnect(RustRawAddress bt_addr) const {
  RawAddress addr = rusty::CopyFromRustAddress(bt_addr);
  return intf_->disconnect(addr);
}
int A2dpIntf::set_silence_device(RustRawAddress bt_addr, bool silent) const {
  RawAddress addr = rusty::CopyFromRustAddress(bt_addr);
  return intf_->set_silence_device(addr, silent);
}
int A2dpIntf::set_active_device(RustRawAddress bt_addr) const {
  RawAddress addr = rusty::CopyFromRustAddress(bt_addr);
  return intf_->set_active_device(addr);
}
int A2dpIntf::config_codec(RustRawAddress bt_addr, ::rust::Vec<A2dpCodecConfig> codec_preferences) const {
  RawAddress addr = rusty::CopyFromRustAddress(bt_addr);
  std::vector<btav_a2dp_codec_config_t> prefs;
  for (size_t i = 0; i < codec_preferences.size(); ++i) {
    prefs.push_back(internal::from_rust_codec_config(codec_preferences[i]));
  }
  return intf_->config_codec(addr, prefs);
}

void A2dpIntf::cleanup() const {
  // TODO: Implement.
}
bool A2dpIntf::set_audio_config(A2dpCodecConfig rconfig) const {
  bluetooth::audio::a2dp::AudioConfig config = {
      .sample_rate = static_cast<btav_a2dp_codec_sample_rate_t>(rconfig.sample_rate),
      .bits_per_sample = static_cast<btav_a2dp_codec_bits_per_sample_t>(rconfig.bits_per_sample),
      .channel_mode = static_cast<btav_a2dp_codec_channel_mode_t>(rconfig.channel_mode),
  };
  return bluetooth::audio::a2dp::SetAudioConfig(config);
}
bool A2dpIntf::start_audio_request() const {
  return bluetooth::audio::a2dp::StartRequest();
}
bool A2dpIntf::stop_audio_request() const {
  return bluetooth::audio::a2dp::StopRequest();
}
RustPresentationPosition A2dpIntf::get_presentation_position() const {
  bluetooth::audio::a2dp::PresentationPosition p = bluetooth::audio::a2dp::GetPresentationPosition();
  RustPresentationPosition rposition = {
      .remote_delay_report_ns = p.remote_delay_report_ns,
      .total_bytes_read = p.total_bytes_read,
      .data_position_sec = p.data_position.tv_sec,
      .data_position_nsec = static_cast<int32_t>(p.data_position.tv_nsec),
  };
  return rposition;
}

// AVRCP

static bluetooth::avrcp::AvrcpMediaInterfaceImpl mAvrcpInterface;
static bluetooth::avrcp::VolumeInterfaceImpl mVolumeInterface;

std::unique_ptr<AvrcpIntf> GetAvrcpProfile(const unsigned char* btif) {
  if (internal::g_avrcpif) std::abort();

  const bt_interface_t* btif_ = reinterpret_cast<const bt_interface_t*>(btif);

  auto avrcpif = std::make_unique<AvrcpIntf>(reinterpret_cast<avrcp::ServiceInterface*>(btif_->get_avrcp_service()));
  internal::g_avrcpif = avrcpif.get();
  return avrcpif;
}

AvrcpIntf::~AvrcpIntf() {}

void AvrcpIntf::init() {
  intf_->Init(&mAvrcpInterface, &mVolumeInterface);
}

void AvrcpIntf::cleanup() {
  intf_->Cleanup();
}

int AvrcpIntf::connect(RustRawAddress bt_addr) {
  RawAddress addr = rusty::CopyFromRustAddress(bt_addr);
  return intf_->ConnectDevice(addr);
}
int AvrcpIntf::disconnect(RustRawAddress bt_addr) {
  RawAddress addr = rusty::CopyFromRustAddress(bt_addr);
  return intf_->DisconnectDevice(addr);
}

void AvrcpIntf::set_volume(int8_t volume) {
  return mVolumeInterface.SetDeviceVolume(volume);
}
}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
