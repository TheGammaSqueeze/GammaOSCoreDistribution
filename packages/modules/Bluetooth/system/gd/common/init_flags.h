/*
 * Copyright 2019 The Android Open Source Project
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

#include <stdexcept>
#include <string>
#include <unordered_map>

#include "src/init_flags.rs.h"

namespace bluetooth {
namespace common {

class InitFlags final {
 public:
  inline static void Load(const char** flags) {
    rust::Vec<rust::String> rusted_flags = rust::Vec<rust::String>();
    while (flags != nullptr && *flags != nullptr) {
      rusted_flags.push_back(rust::String(*flags));
      flags++;
    }
    init_flags::load(std::move(rusted_flags));
  }

  inline static bool IsDebugLoggingEnabledForTag(const std::string& tag) {
    return init_flags::is_debug_logging_enabled_for_tag(tag);
  }

  inline static bool IsDebugLoggingEnabledForAll() {
    return init_flags::logging_debug_enabled_for_all_is_enabled();
  }

  inline static bool IsBtmDmFlushDiscoveryQueueOnSearchCancel() {
    return init_flags::btm_dm_flush_discovery_queue_on_search_cancel_is_enabled();
  }

  inline static bool IsTargetedAnnouncementReconnectionMode() {
    return init_flags::leaudio_targeted_announcement_reconnection_mode_is_enabled();
  }

  inline static int GetAdapterIndex() {
    return init_flags::get_hci_adapter();
  }

  inline static void SetAllForTesting() {
    init_flags::set_all_for_testing();
  }
};

}  // namespace common
}  // namespace bluetooth
