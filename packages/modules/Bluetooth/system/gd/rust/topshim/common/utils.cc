/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "gd/rust/topshim/common/utils.h"

#include "src/btif.rs.h"

using bluetooth::topshim::rust::RustRawAddress;

namespace bluetooth {
namespace topshim {
namespace rust {

RustRawAddress CopyToRustAddress(const RawAddress& address) {
  RustRawAddress raddr;
  std::copy(std::begin(address.address), std::end(address.address), std::begin(raddr.address));
  return raddr;
}

RawAddress CopyFromRustAddress(const RustRawAddress& rust_address) {
  RawAddress addr;
  addr.FromOctets(rust_address.address.data());
  return addr;
}

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth
