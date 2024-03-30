/**
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include "BpfHandler.h"

namespace android {
namespace net {

class NetdUpdatable {
  public:
    NetdUpdatable() = default;
    NetdUpdatable(const NetdUpdatable&) = delete;
    NetdUpdatable& operator=(const NetdUpdatable&) = delete;
    static NetdUpdatable* getInstance();

    BpfHandler mBpfHandler;
};

extern NetdUpdatable* gNetdUpdatable;

}  // namespace net
}  // namespace android