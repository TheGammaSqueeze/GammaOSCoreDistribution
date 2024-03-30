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

#include "btaa/attribution_processor.h"
#include "common/strings.h"

#include "os/log.h"

namespace bluetooth {
namespace activity_attribution {

constexpr char kActivityAttributionTimeFormat[] = "%Y-%m-%d %H:%M:%S";
static const std::string kUnknownPackageInfo = "UNKNOWN";
// A device-activity aggregation entry expires after two days (172800 seconds)
static const int kDurationToKeepDeviceActivityEntrySecs = 172800;
// A transient device-activity aggregation entry is defined as an entry with very few Byte count
// (200 Bytes, this is about the size of 5 advertising packets) over a period of time (15 minutes)
static const int kByteCountTransientDeviceActivityEntry = 200;
static const int kDurationTransientDeviceActivityEntrySecs = 900;
static const int kMapSizeTrimDownAggregationEntry = 200;

void AttributionProcessor::OnBtaaPackets(std::vector<BtaaHciPacket> btaa_packets) {
  AddressActivityKey key;

  for (auto& btaa_packet : btaa_packets) {
    key.address = btaa_packet.address;
    key.activity = btaa_packet.activity;

    if (wakelock_duration_aggregator_.find(key) == wakelock_duration_aggregator_.end()) {
      wakelock_duration_aggregator_[key] = {};
    }
    wakelock_duration_aggregator_[key].byte_count += btaa_packet.byte_count;

    if (wakeup_) {
      wakelock_duration_aggregator_[key].wakeup_count += 1;
      device_wakeup_aggregator_.Push(std::move(DeviceWakeupDescriptor(btaa_packet.activity, btaa_packet.address)));
      std::string package_info = kUnknownPackageInfo;
      std::string address = btaa_packet.address.ToString();
      if (address_app_map_.find(address) != address_app_map_.end()) {
        package_info = address_app_map_[address];
      }
      app_wakeup_aggregator_.Push(std::move(AppWakeupDescriptor(btaa_packet.activity, package_info)));
    }
  }
  wakeup_ = false;
}

void AttributionProcessor::OnWakelockReleased(uint32_t duration_ms) {
  uint32_t total_byte_count = 0;

  for (auto& it : wakelock_duration_aggregator_) {
    total_byte_count += it.second.byte_count;
  }

  if (total_byte_count == 0) {
    return;
  }

  auto cur_time = now_func_();
  for (auto& it : wakelock_duration_aggregator_) {
    it.second.wakelock_duration_ms = (uint64_t)duration_ms * it.second.byte_count / total_byte_count;
    if (btaa_aggregator_.find(it.first) == btaa_aggregator_.end()) {
      btaa_aggregator_[it.first] = {};
      btaa_aggregator_[it.first].creation_time = cur_time;
    }

    auto elapsed_time_sec =
        std::chrono::duration_cast<std::chrono::seconds>(cur_time - btaa_aggregator_[it.first].creation_time).count();
    if (elapsed_time_sec > kDurationToKeepDeviceActivityEntrySecs) {
      btaa_aggregator_[it.first].wakeup_count = 0;
      btaa_aggregator_[it.first].byte_count = 0;
      btaa_aggregator_[it.first].wakelock_duration_ms = 0;
      btaa_aggregator_[it.first].creation_time = cur_time;
    }

    btaa_aggregator_[it.first].wakeup_count += it.second.wakeup_count;
    btaa_aggregator_[it.first].byte_count += it.second.byte_count;
    btaa_aggregator_[it.first].wakelock_duration_ms += it.second.wakelock_duration_ms;

    std::string address = it.first.address.ToString();
    std::string package_info = kUnknownPackageInfo;
    if (address_app_map_.find(address) != address_app_map_.end()) {
      package_info = address_app_map_[address];
    }
    AppActivityKey key;
    key.app = package_info;
    key.activity = it.first.activity;

    if (app_activity_aggregator_.find(key) == app_activity_aggregator_.end()) {
      app_activity_aggregator_[key] = {};
      app_activity_aggregator_[key].creation_time = cur_time;
    }

    elapsed_time_sec =
        std::chrono::duration_cast<std::chrono::seconds>(cur_time - app_activity_aggregator_[key].creation_time)
            .count();
    if (elapsed_time_sec > kDurationToKeepDeviceActivityEntrySecs) {
      app_activity_aggregator_[key].wakeup_count = 0;
      app_activity_aggregator_[key].byte_count = 0;
      app_activity_aggregator_[key].wakelock_duration_ms = 0;
      app_activity_aggregator_[key].creation_time = cur_time;
    }

    app_activity_aggregator_[key].wakeup_count += it.second.wakeup_count;
    app_activity_aggregator_[key].byte_count += it.second.byte_count;
    app_activity_aggregator_[key].wakelock_duration_ms += it.second.wakelock_duration_ms;
  }
  wakelock_duration_aggregator_.clear();

  if (btaa_aggregator_.size() <= kMapSizeTrimDownAggregationEntry &&
      app_activity_aggregator_.size() <= kMapSizeTrimDownAggregationEntry) {
    return;
  }
  // Trim down the transient entries in the aggregator to avoid that it overgrows
  if (btaa_aggregator_.size() > kMapSizeTrimDownAggregationEntry) {
    auto it = btaa_aggregator_.begin();
    while (it != btaa_aggregator_.end()) {
      auto elapsed_time_sec =
          std::chrono::duration_cast<std::chrono::seconds>(cur_time - it->second.creation_time).count();
      if (elapsed_time_sec > kDurationTransientDeviceActivityEntrySecs &&
          it->second.byte_count < kByteCountTransientDeviceActivityEntry) {
        it = btaa_aggregator_.erase(it);
      } else {
        it++;
      }
    }
  }

  if (app_activity_aggregator_.size() > kMapSizeTrimDownAggregationEntry) {
    auto it = app_activity_aggregator_.begin();
    while (it != app_activity_aggregator_.end()) {
      auto elapsed_time_sec =
          std::chrono::duration_cast<std::chrono::seconds>(cur_time - it->second.creation_time).count();
      if (elapsed_time_sec > kDurationTransientDeviceActivityEntrySecs &&
          it->second.byte_count < kByteCountTransientDeviceActivityEntry) {
        it = app_activity_aggregator_.erase(it);
      } else {
        it++;
      }
    }
  }
}

void AttributionProcessor::OnWakeup() {
  if (wakeup_) {
    LOG_INFO("Previous wakeup notification is not consumed.");
  }
  wakeup_ = true;
}

void AttributionProcessor::NotifyActivityAttributionInfo(
    int uid, const std::string& package_name, const std::string& device_address) {
  if (address_app_map_.size() > kMapSizeTrimDownAggregationEntry) {
    LOG_INFO("The map from device address and app info overflows.");
    return;
  }
  address_app_map_[device_address] = package_name + "/" + std::to_string(uid);
}

void AttributionProcessor::Dump(
    std::promise<flatbuffers::Offset<ActivityAttributionData>> promise, flatbuffers::FlatBufferBuilder* fb_builder) {
  // Dump device-based wakeup attribution data
  auto title_device_wakeup = fb_builder->CreateString("----- Device-based Wakeup Attribution Dumpsys -----");
  std::vector<common::TimestampedEntry<DeviceWakeupDescriptor>> device_wakeup_aggregator =
      device_wakeup_aggregator_.Pull();
  std::vector<flatbuffers::Offset<WakeupEntry>> device_wakeup_entry_offsets;
  for (auto& it : device_wakeup_aggregator) {
    WakeupEntryBuilder wakeup_entry_builder(*fb_builder);
    std::chrono::milliseconds duration(it.timestamp);
    std::chrono::time_point<std::chrono::system_clock> wakeup_time(duration);
    wakeup_entry_builder.add_wakeup_time(fb_builder->CreateString(
        bluetooth::common::StringFormatTimeWithMilliseconds(kActivityAttributionTimeFormat, wakeup_time).c_str()));
    wakeup_entry_builder.add_activity(fb_builder->CreateString((ActivityToString(it.entry.activity_))));
    wakeup_entry_builder.add_address(fb_builder->CreateString(it.entry.address_.ToString()));
    device_wakeup_entry_offsets.push_back(wakeup_entry_builder.Finish());
  }
  auto device_wakeup_entries = fb_builder->CreateVector(device_wakeup_entry_offsets);

  // Dump device-based activity aggregation data
  auto title_device_activity = fb_builder->CreateString("----- Device-based Activity Attribution Dumpsys -----");
  std::vector<flatbuffers::Offset<ActivityAggregationEntry>> device_aggregation_entry_offsets;
  for (auto& it : btaa_aggregator_) {
    ActivityAggregationEntryBuilder device_entry_builder(*fb_builder);
    device_entry_builder.add_address(fb_builder->CreateString(it.first.address.ToString()));
    device_entry_builder.add_activity(fb_builder->CreateString((ActivityToString(it.first.activity))));
    device_entry_builder.add_wakeup_count(it.second.wakeup_count);
    device_entry_builder.add_byte_count(it.second.byte_count);
    device_entry_builder.add_wakelock_duration_ms(it.second.wakelock_duration_ms);
    device_entry_builder.add_creation_time(fb_builder->CreateString(
        bluetooth::common::StringFormatTimeWithMilliseconds(kActivityAttributionTimeFormat, it.second.creation_time)
            .c_str()));
    device_aggregation_entry_offsets.push_back(device_entry_builder.Finish());
  }
  auto device_aggregation_entries = fb_builder->CreateVector(device_aggregation_entry_offsets);

  // Dump App-based wakeup attribution data
  auto title_app_wakeup = fb_builder->CreateString("----- App-based Wakeup Attribution Dumpsys -----");
  std::vector<common::TimestampedEntry<AppWakeupDescriptor>> app_wakeup_aggregator = app_wakeup_aggregator_.Pull();
  std::vector<flatbuffers::Offset<WakeupEntry>> app_wakeup_entry_offsets;
  for (auto& it : app_wakeup_aggregator) {
    WakeupEntryBuilder wakeup_entry_builder(*fb_builder);
    std::chrono::milliseconds duration(it.timestamp);
    std::chrono::time_point<std::chrono::system_clock> wakeup_time(duration);
    wakeup_entry_builder.add_wakeup_time(fb_builder->CreateString(
        bluetooth::common::StringFormatTimeWithMilliseconds(kActivityAttributionTimeFormat, wakeup_time).c_str()));
    wakeup_entry_builder.add_activity(fb_builder->CreateString((ActivityToString(it.entry.activity_))));
    wakeup_entry_builder.add_package_info(fb_builder->CreateString(it.entry.package_info_));
    app_wakeup_entry_offsets.push_back(wakeup_entry_builder.Finish());
  }
  auto app_wakeup_entries = fb_builder->CreateVector(app_wakeup_entry_offsets);

  // Dump app-based activity aggregation data
  auto title_app_activity = fb_builder->CreateString("----- App-based Activity Attribution Dumpsys -----");
  std::vector<flatbuffers::Offset<ActivityAggregationEntry>> app_aggregation_entry_offsets;
  for (auto& it : app_activity_aggregator_) {
    ActivityAggregationEntryBuilder app_entry_builder(*fb_builder);
    app_entry_builder.add_package_info(fb_builder->CreateString(it.first.app));
    app_entry_builder.add_activity(fb_builder->CreateString((ActivityToString(it.first.activity))));
    app_entry_builder.add_wakeup_count(it.second.wakeup_count);
    app_entry_builder.add_byte_count(it.second.byte_count);
    app_entry_builder.add_wakelock_duration_ms(it.second.wakelock_duration_ms);
    app_entry_builder.add_creation_time(fb_builder->CreateString(
        bluetooth::common::StringFormatTimeWithMilliseconds(kActivityAttributionTimeFormat, it.second.creation_time)
            .c_str()));
    app_aggregation_entry_offsets.push_back(app_entry_builder.Finish());
  }
  auto app_aggregation_entries = fb_builder->CreateVector(app_aggregation_entry_offsets);

  ActivityAttributionDataBuilder builder(*fb_builder);
  builder.add_title_device_wakeup(title_device_wakeup);
  builder.add_num_device_wakeup(device_wakeup_aggregator.size());
  builder.add_device_wakeup_attribution(device_wakeup_entries);
  builder.add_title_device_activity(title_device_activity);
  builder.add_num_device_activity(btaa_aggregator_.size());
  builder.add_device_activity_aggregation(device_aggregation_entries);
  btaa_aggregator_.clear();

  builder.add_title_app_wakeup(title_app_wakeup);
  builder.add_num_app_wakeup(app_wakeup_aggregator.size());
  builder.add_app_wakeup_attribution(app_wakeup_entries);
  builder.add_title_app_activity(title_app_activity);
  builder.add_num_app_activity(app_activity_aggregator_.size());
  builder.add_app_activity_aggregation(app_aggregation_entries);
  app_activity_aggregator_.clear();

  flatbuffers::Offset<ActivityAttributionData> dumpsys_data = builder.Finish();
  promise.set_value(dumpsys_data);
}

#ifndef CASE_RETURN_TEXT
#define CASE_RETURN_TEXT(code) \
  case code:                   \
    return #code
#endif

const char* AttributionProcessor::ActivityToString(Activity activity) {
  switch (activity) {
    CASE_RETURN_TEXT(Activity::ACL);
    CASE_RETURN_TEXT(Activity::ADVERTISE);
    CASE_RETURN_TEXT(Activity::CONNECT);
    CASE_RETURN_TEXT(Activity::CONTROL);
    CASE_RETURN_TEXT(Activity::HFP);
    CASE_RETURN_TEXT(Activity::ISO);
    CASE_RETURN_TEXT(Activity::SCAN);
    CASE_RETURN_TEXT(Activity::VENDOR);
    default:
      return "UNKNOWN";
  }
}

}  // namespace activity_attribution
}  // namespace bluetooth
