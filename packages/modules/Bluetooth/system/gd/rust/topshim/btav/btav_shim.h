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
#ifndef GD_RUST_TOPSHIM_BTAV_BTAV_SHIM_H
#define GD_RUST_TOPSHIM_BTAV_BTAV_SHIM_H

#include <memory>

#include "audio_hal_interface/a2dp_encoding_host.h"
#include "include/hardware/avrcp/avrcp.h"
#include "include/hardware/bt_av.h"
#include "rust/cxx.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

struct A2dpCodecConfig;
struct RustPresentationPosition;
struct RustRawAddress;

class A2dpIntf {
 public:
  A2dpIntf(const btav_source_interface_t* intf) : intf_(intf){};
  ~A2dpIntf();

  // interface for Settings
  int init() const;
  int connect(RustRawAddress bt_addr) const;
  int disconnect(RustRawAddress bt_addr) const;
  int set_silence_device(RustRawAddress bt_addr, bool silent) const;
  int set_active_device(RustRawAddress bt_addr) const;
  int config_codec(RustRawAddress bt_addr, ::rust::Vec<A2dpCodecConfig> codec_preferences) const;
  void cleanup() const;

  // interface for Audio server
  bool set_audio_config(A2dpCodecConfig rconfig) const;
  bool start_audio_request() const;
  bool stop_audio_request() const;
  RustPresentationPosition get_presentation_position() const;

 private:
  const btav_source_interface_t* intf_;
};

std::unique_ptr<A2dpIntf> GetA2dpProfile(const unsigned char* btif);

class AvrcpIntf {
 public:
  AvrcpIntf(bluetooth::avrcp::ServiceInterface* intf) : intf_(intf) {}
  ~AvrcpIntf();

  void init();
  void cleanup();
  int connect(RustRawAddress bt_addr);
  int disconnect(RustRawAddress bt_addr);

  // interface for Audio server
  void set_volume(int8_t volume);

 private:
  bluetooth::avrcp::ServiceInterface* intf_;
};

std::unique_ptr<AvrcpIntf> GetAvrcpProfile(const unsigned char* btif);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth

#endif  // GD_RUST_TOPSHIM_BTAV_BTAV_SHIM_H
