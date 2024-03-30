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

#pragma once

#include <memory>

#include "gd/rust/topshim/btav_sink/btav_sink_shim.h"
#include "include/hardware/bt_av.h"
#include "rust/cxx.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

struct RustRawAddress;

class A2dpSinkIntf {
 public:
  A2dpSinkIntf(const btav_sink_interface_t* intf) : intf_(intf){};
  ~A2dpSinkIntf();

  // interface for Settings
  int init() const;
  int connect(RustRawAddress bt_addr) const;
  int disconnect(RustRawAddress bt_addr) const;
  int set_active_device(RustRawAddress bt_addr) const;
  void cleanup() const;

 private:
  const btav_sink_interface_t* intf_;
};

std::unique_ptr<A2dpSinkIntf> GetA2dpSinkProfile(const unsigned char* btif);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
