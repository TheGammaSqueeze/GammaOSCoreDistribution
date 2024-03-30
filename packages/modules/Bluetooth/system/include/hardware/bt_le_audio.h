/*
 * Copyright 2019 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
 * www.ehima.com
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

#include <array>
#include <map>
#include <optional>
#include <vector>

#include "raw_address.h"

namespace bluetooth {
namespace le_audio {

enum class ConnectionState {
  DISCONNECTED = 0,
  CONNECTING,
  CONNECTED,
  DISCONNECTING
};

enum class GroupStatus {
  INACTIVE = 0,
  ACTIVE,
  TURNED_IDLE_DURING_CALL,
};

enum class GroupStreamStatus {
  IDLE = 0,
  STREAMING,
  RELEASING,
  SUSPENDING,
  SUSPENDED,
  CONFIGURED_AUTONOMOUS,
  CONFIGURED_BY_USER,
  DESTROYED,
};

enum class GroupNodeStatus {
  ADDED = 1,
  REMOVED,
};

typedef enum {
  LE_AUDIO_CODEC_INDEX_SOURCE_LC3 = 0,
  LE_AUDIO_CODEC_INDEX_SOURCE_MAX
} btle_audio_codec_index_t;

typedef struct {
  btle_audio_codec_index_t codec_type;

  std::string ToString() const {
    std::string codec_name_str;

    switch (codec_type) {
      case LE_AUDIO_CODEC_INDEX_SOURCE_LC3:
        codec_name_str = "LC3";
        break;
      default:
        codec_name_str = "Unknown LE codec " + std::to_string(codec_type);
        break;
    }
    return "codec: " + codec_name_str;
  }
} btle_audio_codec_config_t;

class LeAudioClientCallbacks {
 public:
  virtual ~LeAudioClientCallbacks() = default;

  /* Callback to notify Java that stack is ready */
  virtual void OnInitialized(void) = 0;

  /** Callback for profile connection state change */
  virtual void OnConnectionState(ConnectionState state,
                                 const RawAddress& address) = 0;

  /* Callback with group status update */
  virtual void OnGroupStatus(int group_id, GroupStatus group_status) = 0;

  /* Callback with node status update */
  virtual void OnGroupNodeStatus(const RawAddress& bd_addr, int group_id,
                                 GroupNodeStatus node_status) = 0;
  /* Callback for newly recognized or reconfigured existing le audio group */
  virtual void OnAudioConf(uint8_t direction, int group_id,
                           uint32_t snk_audio_location,
                           uint32_t src_audio_location,
                           uint16_t avail_cont) = 0;
  /* Callback for sink audio location recognized */
  virtual void OnSinkAudioLocationAvailable(const RawAddress& address,
                                            uint32_t snk_audio_locations) = 0;
  /* Callback with local codec capabilities */
  virtual void OnAudioLocalCodecCapabilities(
      std::vector<btle_audio_codec_config_t> local_input_capa_codec_conf,
      std::vector<btle_audio_codec_config_t> local_output_capa_codec_conf) = 0;
  /* Callback with group codec configurations */
  virtual void OnAudioGroupCodecConf(
      int group_id, btle_audio_codec_config_t input_codec_conf,
      btle_audio_codec_config_t output_codec_conf,
      std::vector<btle_audio_codec_config_t> input_selectable_codec_conf,
      std::vector<btle_audio_codec_config_t> output_selectable_codec_conf) = 0;
};

class LeAudioClientInterface {
 public:
  virtual ~LeAudioClientInterface() = default;

  /* Register the LeAudio callbacks */
  virtual void Initialize(
      LeAudioClientCallbacks* callbacks,
      const std::vector<btle_audio_codec_config_t>& offloading_preference) = 0;

  /** Connect to LEAudio */
  virtual void Connect(const RawAddress& address) = 0;

  /** Disconnect from LEAudio */
  virtual void Disconnect(const RawAddress& address) = 0;

  /* Cleanup the LeAudio */
  virtual void Cleanup(void) = 0;

  /* Called when LeAudio is unbonded. */
  virtual void RemoveDevice(const RawAddress& address) = 0;

  /* Attach le audio node to group */
  virtual void GroupAddNode(int group_id, const RawAddress& addr) = 0;

  /* Detach le audio node from a group */
  virtual void GroupRemoveNode(int group_id, const RawAddress& addr) = 0;

  /* Set active le audio group */
  virtual void GroupSetActive(int group_id) = 0;

  /* Set codec config preference */
  virtual void SetCodecConfigPreference(
      int group_id, btle_audio_codec_config_t input_codec_config,
      btle_audio_codec_config_t output_codec_config) = 0;

  /* Set Ccid for context type */
  virtual void SetCcidInformation(int ccid, int context_type) = 0;

  /* Set In call flag */
  virtual void SetInCall(bool in_call) = 0;
};

/* Represents the broadcast source state. */
enum class BroadcastState {
  STOPPED = 0,
  CONFIGURING,
  CONFIGURED,
  STOPPING,
  STREAMING,
};

using BroadcastId = uint32_t;
static constexpr BroadcastId kBroadcastIdInvalid = 0x00000000;
using BroadcastCode = std::array<uint8_t, 16>;

/* Content Metadata LTV Types */
constexpr uint8_t kLeAudioMetadataTypePreferredAudioContext = 0x01;
constexpr uint8_t kLeAudioMetadataTypeStreamingAudioContext = 0x02;
constexpr uint8_t kLeAudioMetadataTypeProgramInfo = 0x03;
constexpr uint8_t kLeAudioMetadataTypeLanguage = 0x04;
constexpr uint8_t kLeAudioMetadataTypeCcidList = 0x05;

/* Codec specific LTV Types */
constexpr uint8_t kLeAudioCodecLC3TypeSamplingFreq = 0x01;
constexpr uint8_t kLeAudioCodecLC3TypeFrameDuration = 0x02;
constexpr uint8_t kLeAudioCodecLC3TypeAudioChannelAllocation = 0x03;
constexpr uint8_t kLeAudioCodecLC3TypeOctetPerFrame = 0x04;
constexpr uint8_t kLeAudioCodecLC3TypeCodecFrameBlocksPerSdu = 0x05;

struct BasicAudioAnnouncementCodecConfig {
  /* 5 octets for the Codec ID */
  uint8_t codec_id;
  uint16_t vendor_company_id;
  uint16_t vendor_codec_id;

  /* Codec params - series of LTV formatted triplets */
  std::map<uint8_t, std::vector<uint8_t>> codec_specific_params;
};

struct BasicAudioAnnouncementBisConfig {
  std::map<uint8_t, std::vector<uint8_t>> codec_specific_params;
  uint8_t bis_index;
};

struct BasicAudioAnnouncementSubgroup {
  /* Subgroup specific codec configuration and metadata */
  BasicAudioAnnouncementCodecConfig codec_config;
  // Content metadata
  std::map<uint8_t, std::vector<uint8_t>> metadata;
  // Broadcast channel configuration
  std::vector<BasicAudioAnnouncementBisConfig> bis_configs;
};

struct BasicAudioAnnouncementData {
  /* Announcement Header fields */
  uint32_t presentation_delay;

  /* Subgroup specific configurations */
  std::vector<BasicAudioAnnouncementSubgroup> subgroup_configs;
};

struct BroadcastMetadata {
  uint16_t pa_interval;
  RawAddress addr;
  uint8_t addr_type;
  uint8_t adv_sid;

  BroadcastId broadcast_id;
  std::optional<BroadcastCode> broadcast_code;

  /* Presentation delay and subgroup configurations */
  BasicAudioAnnouncementData basic_audio_announcement;
};

class LeAudioBroadcasterCallbacks {
 public:
  virtual ~LeAudioBroadcasterCallbacks() = default;
  /* Callback for the newly created broadcast event. */
  virtual void OnBroadcastCreated(uint32_t broadcast_id, bool success) = 0;

  /* Callback for the destroyed broadcast event. */
  virtual void OnBroadcastDestroyed(uint32_t broadcast_id) = 0;
  /* Callback for the broadcast source state event. */
  virtual void OnBroadcastStateChanged(uint32_t broadcast_id,
                                       BroadcastState state) = 0;
  /* Callback for the broadcast metadata change. */
  virtual void OnBroadcastMetadataChanged(
      uint32_t broadcast_id, const BroadcastMetadata& broadcast_metadata) = 0;
};

class LeAudioBroadcasterInterface {
 public:
  virtual ~LeAudioBroadcasterInterface() = default;
  /* Register the LeAudio Broadcaster callbacks */
  virtual void Initialize(LeAudioBroadcasterCallbacks* callbacks) = 0;
  /* Stop the LeAudio Broadcaster and all active broadcasts */
  virtual void Stop(void) = 0;
  /* Cleanup the LeAudio Broadcaster */
  virtual void Cleanup(void) = 0;
  /* Create Broadcast instance */
  virtual void CreateBroadcast(std::vector<uint8_t> metadata,
                               std::optional<BroadcastCode> broadcast_code) = 0;
  /* Update the ongoing Broadcast metadata */
  virtual void UpdateMetadata(uint32_t broadcast_id,
                              std::vector<uint8_t> metadata) = 0;

  /* Start the existing Broadcast stream */
  virtual void StartBroadcast(uint32_t broadcast_id) = 0;
  /* Pause the ongoing Broadcast stream */
  virtual void PauseBroadcast(uint32_t broadcast_id) = 0;
  /* Stop the Broadcast (no stream, no periodic advertisements */
  virtual void StopBroadcast(uint32_t broadcast_id) = 0;
  /* Destroy the existing Broadcast instance */
  virtual void DestroyBroadcast(uint32_t broadcast_id) = 0;
  /* Get Broadcast Metadata */
  virtual void GetBroadcastMetadata(uint32_t broadcast_id) = 0;
};

} /* namespace le_audio */
} /* namespace bluetooth */
