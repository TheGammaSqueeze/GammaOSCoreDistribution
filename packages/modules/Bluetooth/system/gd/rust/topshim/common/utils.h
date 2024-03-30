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
#ifndef GD_RUST_TOPSHIM_COMMON_UTILS_H
#define GD_RUST_TOPSHIM_COMMON_UTILS_H

#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

struct RustRawAddress;

RustRawAddress CopyToRustAddress(const RawAddress& address);
RawAddress CopyFromRustAddress(const RustRawAddress& rust_address);

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth

#endif  // GD_RUST_TOPSHIM_COMMON_UTILS_H
