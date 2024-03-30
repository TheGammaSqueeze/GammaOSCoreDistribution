/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
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

/*
 * This file contains definitions for Basic Audio Profile / Audio Stream Control
 * and Published Audio Capabilities definitions, structures etc.
 */

#pragma once

#include <stdint.h>

#include <bitset>
#include <map>
#include <optional>
#include <string>
#include <variant>
#include <vector>

#include "bta_groups.h"
#include "bta_le_audio_api.h"
#include "bta_le_audio_uuids.h"
#include "btm_iso_api_types.h"

namespace le_audio {

#define UINT8_TO_VEC_UINT8(u8) \
  std::vector<uint8_t> { u8 }
#define UINT16_TO_VEC_UINT8(u16) \
  std::vector<uint8_t>((uint8_t*)&u16, (uint8_t*)&u16 + sizeof(u16))
#define UINT32_TO_VEC_UINT8(u32) \
  std::vector<uint8_t>((uint8_t*)&u32, (uint8_t*)&u32 + sizeof(u32))

#define VEC_UINT8_TO_UINT8(vec) vec.data()[0]
#define VEC_UINT8_TO_UINT16(vec) ((vec.data()[1] << 8) + vec.data()[0])
#define OFF_VEC_UINT8_TO_UINT16(vec, off) \
  ((vec.data()[1 + off] << 8) + vec.data()[0 + off])
#define VEC_UINT8_TO_UINT32(vec)                                          \
  ((vec.data()[3] << 24) + (vec.data()[2] << 16) + (vec.data()[1] << 8) + \
   vec.data()[0])

namespace uuid {
/* CAP service
 * This service is used to identify peer role (which we are not using for now)
 * and to wrap CSIS service as this is required to understand the context of the
 * CSIS
 */
static const bluetooth::Uuid kCapServiceUuid =
    bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE);

/* Assigned numbers for attributes */
static const bluetooth::Uuid kPublishedAudioCapabilityServiceUuid =
    bluetooth::Uuid::From16Bit(0x1850);
static const bluetooth::Uuid kAudioStreamControlServiceUuid =
    bluetooth::Uuid::From16Bit(0x184E);

static const bluetooth::Uuid kTelephonyMediaAudioServiceUuid =
    bluetooth::Uuid::From16Bit(0x1855);

/* Published Audio Capabilities Service Characteristics */
static const bluetooth::Uuid kSinkPublishedAudioCapabilityCharacteristicUuid =
    bluetooth::Uuid::From16Bit(0x2BC9);
static const bluetooth::Uuid kSourcePublishedAudioCapabilityCharacteristicUuid =
    bluetooth::Uuid::From16Bit(0x2BCB);
static const bluetooth::Uuid kSinkAudioLocationCharacteristicUuid =
    bluetooth::Uuid::From16Bit(0x2BCA);
static const bluetooth::Uuid kSourceAudioLocationCharacteristicUuid =
    bluetooth::Uuid::From16Bit(0x2BCC);

/* Audio Stream Control Service Characteristics */
static const bluetooth::Uuid kAudioContextAvailabilityCharacteristicUuid =
    bluetooth::Uuid::From16Bit(0x2BCD);
static const bluetooth::Uuid kAudioSupportedContextCharacteristicUuid =
    bluetooth::Uuid::From16Bit(0x2BCE);

/* Audio Stream Control Service Characteristics */
static const bluetooth::Uuid kSinkAudioStreamEndpointUuid =
    bluetooth::Uuid::From16Bit(0x2BC4);
static const bluetooth::Uuid kSourceAudioStreamEndpointUuid =
    bluetooth::Uuid::From16Bit(0x2BC5);
static const bluetooth::Uuid
    kAudioStreamEndpointControlPointCharacteristicUuid =
        bluetooth::Uuid::From16Bit(0x2BC6);

/* Telephony and Media Audio Service Characteristics */
static const bluetooth::Uuid kTelephonyMediaAudioProfileRoleCharacteristicUuid =
    bluetooth::Uuid::From16Bit(0x2B51);
}  // namespace uuid

namespace codec_spec_conf {
/* LTV Types */
constexpr uint8_t kLeAudioCodecLC3TypeSamplingFreq = 0x01;
constexpr uint8_t kLeAudioCodecLC3TypeFrameDuration = 0x02;
constexpr uint8_t kLeAudioCodecLC3TypeAudioChannelAllocation = 0x03;
constexpr uint8_t kLeAudioCodecLC3TypeOctetPerFrame = 0x04;
constexpr uint8_t kLeAudioCodecLC3TypeCodecFrameBlocksPerSdu = 0x05;

/* Sampling Frequencies */
constexpr uint8_t kLeAudioSamplingFreq8000Hz = 0x01;
constexpr uint8_t kLeAudioSamplingFreq11025Hz = 0x02;
constexpr uint8_t kLeAudioSamplingFreq16000Hz = 0x03;
constexpr uint8_t kLeAudioSamplingFreq22050Hz = 0x04;
constexpr uint8_t kLeAudioSamplingFreq24000Hz = 0x05;
constexpr uint8_t kLeAudioSamplingFreq32000Hz = 0x06;
constexpr uint8_t kLeAudioSamplingFreq44100Hz = 0x07;
constexpr uint8_t kLeAudioSamplingFreq48000Hz = 0x08;
constexpr uint8_t kLeAudioSamplingFreq88200Hz = 0x09;
constexpr uint8_t kLeAudioSamplingFreq96000Hz = 0x0A;
constexpr uint8_t kLeAudioSamplingFreq176400Hz = 0x0B;
constexpr uint8_t kLeAudioSamplingFreq192000Hz = 0x0C;
constexpr uint8_t kLeAudioSamplingFreq384000Hz = 0x0D;

/* Frame Durations */
constexpr uint8_t kLeAudioCodecLC3FrameDur7500us = 0x00;
constexpr uint8_t kLeAudioCodecLC3FrameDur10000us = 0x01;

/* Audio Allocations */
constexpr uint32_t kLeAudioLocationNotAllowed = 0x00000000;
constexpr uint32_t kLeAudioLocationFrontLeft = 0x00000001;
constexpr uint32_t kLeAudioLocationFrontRight = 0x00000002;
constexpr uint32_t kLeAudioLocationFrontCenter = 0x00000004;
constexpr uint32_t kLeAudioLocationLowFreqEffects1 = 0x00000008;
constexpr uint32_t kLeAudioLocationBackLeft = 0x00000010;
constexpr uint32_t kLeAudioLocationBackRight = 0x00000020;
constexpr uint32_t kLeAudioLocationFrontLeftOfCenter = 0x00000040;
constexpr uint32_t kLeAudioLocationFrontRightOfCenter = 0x00000080;
constexpr uint32_t kLeAudioLocationBackCenter = 0x00000100;
constexpr uint32_t kLeAudioLocationLowFreqEffects2 = 0x00000200;
constexpr uint32_t kLeAudioLocationSideLeft = 0x00000400;
constexpr uint32_t kLeAudioLocationSideRight = 0x00000800;
constexpr uint32_t kLeAudioLocationTopFrontLeft = 0x00001000;
constexpr uint32_t kLeAudioLocationTopFrontRight = 0x00002000;
constexpr uint32_t kLeAudioLocationTopFrontCenter = 0x00004000;
constexpr uint32_t kLeAudioLocationTopCenter = 0x00008000;
constexpr uint32_t kLeAudioLocationTopBackLeft = 0x00010000;
constexpr uint32_t kLeAudioLocationTopBackRight = 0x00020000;
constexpr uint32_t kLeAudioLocationTopSideLeft = 0x00040000;
constexpr uint32_t kLeAudioLocationTopSideRight = 0x00080000;
constexpr uint32_t kLeAudioLocationTopBackCenter = 0x00100000;
constexpr uint32_t kLeAudioLocationBottomFrontCenter = 0x00200000;
constexpr uint32_t kLeAudioLocationBottomFrontLeft = 0x00400000;
constexpr uint32_t kLeAudioLocationBottomFrontRight = 0x00800000;
constexpr uint32_t kLeAudioLocationFrontLeftWide = 0x01000000;
constexpr uint32_t kLeAudioLocationFrontRightWide = 0x02000000;
constexpr uint32_t kLeAudioLocationLeftSurround = 0x04000000;
constexpr uint32_t kLeAudioLocationRightSurround = 0x08000000;

constexpr uint32_t kLeAudioLocationAnyLeft =
    kLeAudioLocationFrontLeft | kLeAudioLocationBackLeft |
    kLeAudioLocationFrontLeftOfCenter | kLeAudioLocationSideLeft |
    kLeAudioLocationTopFrontLeft | kLeAudioLocationTopBackLeft |
    kLeAudioLocationTopSideLeft | kLeAudioLocationBottomFrontLeft |
    kLeAudioLocationFrontLeftWide | kLeAudioLocationLeftSurround;

constexpr uint32_t kLeAudioLocationAnyRight =
    kLeAudioLocationFrontRight | kLeAudioLocationBackRight |
    kLeAudioLocationFrontRightOfCenter | kLeAudioLocationSideRight |
    kLeAudioLocationTopFrontRight | kLeAudioLocationTopBackRight |
    kLeAudioLocationTopSideRight | kLeAudioLocationBottomFrontRight |
    kLeAudioLocationFrontRightWide | kLeAudioLocationRightSurround;

constexpr uint32_t kLeAudioLocationStereo =
    kLeAudioLocationFrontLeft | kLeAudioLocationFrontRight;

/* Octets Per Frame */
constexpr uint16_t kLeAudioCodecLC3FrameLen30 = 30;
constexpr uint16_t kLeAudioCodecLC3FrameLen40 = 40;
constexpr uint16_t kLeAudioCodecLC3FrameLen60 = 60;
constexpr uint16_t kLeAudioCodecLC3FrameLen80 = 80;
constexpr uint16_t kLeAudioCodecLC3FrameLen120 = 120;

}  // namespace codec_spec_conf

constexpr uint8_t kInvalidCisId = 0xFF;

namespace codec_spec_caps {
uint16_t constexpr SamplingFreqConfig2Capability(uint8_t conf) {
  return (1 << (conf - 1));
}

uint8_t constexpr FrameDurationConfig2Capability(uint8_t conf) {
  return (0x01 << (conf));
}

inline uint8_t GetAudioChannelCounts(std::bitset<32> allocation) {
  /*
   * BAP d09r07 B4.2.3 Audio_Channel_Allocation
   * "(...) Audio_Channel_Allocation bitmap value of all zeros or the
   * absence of the Audio_Channel_Allocation LTV structure within a
   * Codec_Specific_Configuration field shall be interpreted as defining a
   * single audio channel of Mono audio (a single channel of no specified
   * Audio Location).
   */
  uint8_t audio_channel_counts = allocation.count() ?: 1;
  return (0x01 << (audio_channel_counts - 1));
}

/* LTV Types - same values as in Codec Specific Configurations but 0x03 is
 * named differently.
 */
constexpr uint8_t kLeAudioCodecLC3TypeSamplingFreq =
    codec_spec_conf::kLeAudioCodecLC3TypeSamplingFreq;
constexpr uint8_t kLeAudioCodecLC3TypeFrameDuration =
    codec_spec_conf::kLeAudioCodecLC3TypeFrameDuration;
constexpr uint8_t kLeAudioCodecLC3TypeAudioChannelCounts =
    codec_spec_conf::kLeAudioCodecLC3TypeAudioChannelAllocation;
constexpr uint8_t kLeAudioCodecLC3TypeOctetPerFrame =
    codec_spec_conf::kLeAudioCodecLC3TypeOctetPerFrame;
constexpr uint8_t kLeAudioCodecLC3TypeMaxCodecFramesPerSdu =
    codec_spec_conf::kLeAudioCodecLC3TypeCodecFrameBlocksPerSdu;

/* Sampling Frequencies */
constexpr uint16_t kLeAudioSamplingFreq8000Hz =
    SamplingFreqConfig2Capability(codec_spec_conf::kLeAudioSamplingFreq8000Hz);
constexpr uint16_t kLeAudioSamplingFreq16000Hz =
    SamplingFreqConfig2Capability(codec_spec_conf::kLeAudioSamplingFreq16000Hz);
constexpr uint16_t kLeAudioSamplingFreq24000Hz =
    SamplingFreqConfig2Capability(codec_spec_conf::kLeAudioSamplingFreq24000Hz);
constexpr uint16_t kLeAudioSamplingFreq32000Hz =
    SamplingFreqConfig2Capability(codec_spec_conf::kLeAudioSamplingFreq32000Hz);
constexpr uint16_t kLeAudioSamplingFreq44100Hz =
    SamplingFreqConfig2Capability(codec_spec_conf::kLeAudioSamplingFreq44100Hz);
constexpr uint16_t kLeAudioSamplingFreq48000Hz =
    SamplingFreqConfig2Capability(codec_spec_conf::kLeAudioSamplingFreq48000Hz);

/* Frame Durations */
constexpr uint8_t kLeAudioCodecLC3FrameDur7500us =
    FrameDurationConfig2Capability(
        codec_spec_conf::kLeAudioCodecLC3FrameDur7500us);
constexpr uint8_t kLeAudioCodecLC3FrameDur10000us =
    FrameDurationConfig2Capability(
        codec_spec_conf::kLeAudioCodecLC3FrameDur10000us);
constexpr uint8_t kLeAudioCodecLC3FrameDurPrefer7500us = 0x10;
constexpr uint8_t kLeAudioCodecLC3FrameDurPrefer10000us = 0x20;

/* Audio Channel Counts */
/* Each bit represents support for additional channel: bit 0 - one channel,
 * bit 1 - two, bit 3 - four channels. Multiple bits can be enabled at once.
 */
constexpr uint8_t kLeAudioCodecLC3ChannelCountNone = 0x00;
constexpr uint8_t kLeAudioCodecLC3ChannelCountSingleChannel = 0x01;
constexpr uint8_t kLeAudioCodecLC3ChannelCountTwoChannel = 0x02;

/* Octets Per Frame - same as in Codec Specific Configurations but in
 * capabilities we get two values: min and max.
 */
constexpr uint16_t kLeAudioCodecLC3FrameLen30 =
    codec_spec_conf::kLeAudioCodecLC3FrameLen30;
constexpr uint16_t kLeAudioCodecLC3FrameLen40 =
    codec_spec_conf::kLeAudioCodecLC3FrameLen40;
constexpr uint16_t kLeAudioCodecLC3FrameLen120 =
    codec_spec_conf::kLeAudioCodecLC3FrameLen120;

};  // namespace codec_spec_caps

namespace types {
constexpr uint8_t kLeAudioCodingFormatLC3 = bluetooth::hci::kIsoCodingFormatLc3;
constexpr uint8_t kLeAudioCodingFormatVendorSpecific =
    bluetooth::hci::kIsoCodingFormatVendorSpecific;
constexpr uint16_t kLeAudioVendorCompanyIdUndefined = 0x00;
constexpr uint16_t kLeAudioVendorCodecIdUndefined = 0x00;

/* Metadata types from Assigned Numbers */
constexpr uint8_t kLeAudioMetadataTypePreferredAudioContext = 0x01;
constexpr uint8_t kLeAudioMetadataTypeStreamingAudioContext = 0x02;
constexpr uint8_t kLeAudioMetadataTypeProgramInfo = 0x03;
constexpr uint8_t kLeAudioMetadataTypeLanguage = 0x04;
constexpr uint8_t kLeAudioMetadataTypeCcidList = 0x05;

constexpr uint8_t kLeAudioMetadataTypeLen = 1;
constexpr uint8_t kLeAudioMetadataLenLen = 1;

constexpr uint8_t kLeAudioMetadataStreamingAudioContextLen = 2;

/* CSIS Types */
constexpr uint8_t kDefaultScanDurationS = 5;
constexpr uint8_t kDefaultCsisSetSize = 2;

constexpr uint8_t kLeAudioDirectionSink = 0x01;
constexpr uint8_t kLeAudioDirectionSource = 0x02;

/* Audio stream config types */
constexpr uint8_t kFramingUnframedPduSupported = 0x00;
constexpr uint8_t kFramingUnframedPduUnsupported = 0x01;

constexpr uint8_t kTargetLatencyLower = 0x01;
constexpr uint8_t kTargetLatencyBalancedLatencyReliability = 0x02;
constexpr uint8_t kTargetLatencyHigherReliability = 0x03;

constexpr uint8_t kTargetPhy1M = 0x01;
constexpr uint8_t kTargetPhy2M = 0x02;
constexpr uint8_t kTargetPhyCoded = 0x03;

constexpr uint32_t kPresDelayNoPreference = 0x00000000;

constexpr uint16_t kMaxTransportLatencyMin = 0x0005;
constexpr uint16_t kMaxTransportLatencyMax = 0x0FA0;

enum class CigState : uint8_t { NONE, CREATING, CREATED, REMOVING, RECOVERING };

/* ASE states according to BAP defined state machine states */
enum class AseState : uint8_t {
  BTA_LE_AUDIO_ASE_STATE_IDLE = 0x00,
  BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED = 0x01,
  BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED = 0x02,
  BTA_LE_AUDIO_ASE_STATE_ENABLING = 0x03,
  BTA_LE_AUDIO_ASE_STATE_STREAMING = 0x04,
  BTA_LE_AUDIO_ASE_STATE_DISABLING = 0x05,
  BTA_LE_AUDIO_ASE_STATE_RELEASING = 0x06,
};

enum class AudioStreamDataPathState {
  IDLE,
  CIS_DISCONNECTING,
  CIS_ASSIGNED,
  CIS_PENDING,
  CIS_ESTABLISHED,
  DATA_PATH_ESTABLISHED,
};

enum class CisType {
  CIS_TYPE_BIDIRECTIONAL,
  CIS_TYPE_UNIDIRECTIONAL_SINK,
  CIS_TYPE_UNIDIRECTIONAL_SOURCE,
};

struct cis {
  uint8_t id;
  CisType type;
  uint16_t conn_handle;
  RawAddress addr;
};

enum class CodecLocation {
  HOST,
  ADSP,
  CONTROLLER,
};

/* Context Types */
enum class LeAudioContextType : uint16_t {
  UNINITIALIZED = 0x0000,
  UNSPECIFIED = 0x0001,
  CONVERSATIONAL = 0x0002,
  MEDIA = 0x0004,
  GAME = 0x0008,
  INSTRUCTIONAL = 0x0010,
  VOICEASSISTANTS = 0x0020,
  LIVE = 0x0040,
  SOUNDEFFECTS = 0x0080,
  NOTIFICATIONS = 0x0100,
  RINGTONE = 0x0200,
  ALERTS = 0x0400,
  EMERGENCYALARM = 0x0800,
  RFU = 0x1000,
};

class AudioContexts {
  using T = std::underlying_type<LeAudioContextType>::type;
  T mValue;

 public:
  explicit constexpr AudioContexts()
      : mValue(static_cast<T>(LeAudioContextType::UNINITIALIZED)) {}
  explicit constexpr AudioContexts(const T& v) : mValue(v) {}
  explicit constexpr AudioContexts(const LeAudioContextType& v)
      : mValue(static_cast<T>(v)) {}
  constexpr AudioContexts(const AudioContexts& other)
      : mValue(static_cast<T>(other.value())) {}

  constexpr T value() const { return mValue; }
  T& value_ref() { return mValue; }
  bool none() const {
    return mValue == static_cast<T>(LeAudioContextType::UNINITIALIZED);
  }
  bool any() const { return !none(); }

  void set(LeAudioContextType const& v) { mValue |= static_cast<T>(v); }
  void unset(const LeAudioContextType& v) { mValue &= ~static_cast<T>(v); }

  bool test(const LeAudioContextType& v) const {
    return (mValue & static_cast<T>(v)) != 0;
  }
  bool test_all(const AudioContexts& v) const {
    return (mValue & v.value()) == v.value();
  }
  bool test_any(const AudioContexts& v) const {
    return (mValue & v.value()) != 0;
  }
  void clear() { mValue = static_cast<T>(LeAudioContextType::UNINITIALIZED); }

  std::string to_string() const;

  AudioContexts& operator=(AudioContexts&& other) = default;
  AudioContexts& operator=(const AudioContexts&) = default;
  bool operator==(const AudioContexts& other) const {
    return value() == other.value();
  };
  constexpr AudioContexts operator~() const { return AudioContexts(~value()); }
};

AudioContexts operator|(std::underlying_type<LeAudioContextType>::type lhs,
                        const LeAudioContextType rhs);
AudioContexts& operator|=(AudioContexts& lhs, AudioContexts const& rhs);
AudioContexts& operator&=(AudioContexts& lhs, AudioContexts const& rhs);

constexpr AudioContexts operator^(const AudioContexts& lhs,
                                  const AudioContexts& rhs) {
  return AudioContexts(lhs.value() ^ rhs.value());
}
constexpr AudioContexts operator|(const AudioContexts& lhs,
                                  const AudioContexts& rhs) {
  return AudioContexts(lhs.value() | rhs.value());
}
constexpr AudioContexts operator&(const AudioContexts& lhs,
                                  const AudioContexts& rhs) {
  return AudioContexts(lhs.value() & rhs.value());
}
constexpr AudioContexts operator|(const LeAudioContextType& lhs,
                                  const LeAudioContextType& rhs) {
  using T = std::underlying_type<LeAudioContextType>::type;
  return AudioContexts(static_cast<T>(lhs) | static_cast<T>(rhs));
}
constexpr AudioContexts operator|(const LeAudioContextType& lhs,
                                  const AudioContexts& rhs) {
  return AudioContexts(lhs) | rhs;
}
constexpr AudioContexts operator|(const AudioContexts& lhs,
                                  const LeAudioContextType& rhs) {
  return lhs | AudioContexts(rhs);
}

std::string ToHexString(const types::LeAudioContextType& value);

template <typename T>
struct BidirectionalPair {
  T sink;
  T source;
};

template <typename T>
T get_bidirectional(BidirectionalPair<T> p);

template <>
AudioContexts get_bidirectional(BidirectionalPair<AudioContexts> p);

/* Configuration strategy */
enum class LeAudioConfigurationStrategy : uint8_t {
  MONO_ONE_CIS_PER_DEVICE = 0x00, /* Common true wireless speakers */
  STEREO_TWO_CISES_PER_DEVICE =
      0x01, /* Requires 2 ASEs and 2 Audio Allocation for left/right */
  STEREO_ONE_CIS_PER_DEVICE = 0x02, /* Requires channel count 2*/
  RFU = 0x03,
};

constexpr LeAudioContextType kLeAudioContextAllTypesArray[] = {
    LeAudioContextType::UNSPECIFIED,   LeAudioContextType::CONVERSATIONAL,
    LeAudioContextType::MEDIA,         LeAudioContextType::GAME,
    LeAudioContextType::INSTRUCTIONAL, LeAudioContextType::VOICEASSISTANTS,
    LeAudioContextType::LIVE,          LeAudioContextType::SOUNDEFFECTS,
    LeAudioContextType::NOTIFICATIONS, LeAudioContextType::RINGTONE,
    LeAudioContextType::ALERTS,        LeAudioContextType::EMERGENCYALARM,
};

constexpr AudioContexts kLeAudioContextAllTypes =
    LeAudioContextType::UNSPECIFIED | LeAudioContextType::CONVERSATIONAL |
    LeAudioContextType::MEDIA | LeAudioContextType::GAME |
    LeAudioContextType::INSTRUCTIONAL | LeAudioContextType::VOICEASSISTANTS |
    LeAudioContextType::LIVE | LeAudioContextType::SOUNDEFFECTS |
    LeAudioContextType::NOTIFICATIONS | LeAudioContextType::RINGTONE |
    LeAudioContextType::ALERTS | LeAudioContextType::EMERGENCYALARM;

/* Structures */
class LeAudioLtvMap {
 public:
  LeAudioLtvMap() {}
  LeAudioLtvMap(std::map<uint8_t, std::vector<uint8_t>> values)
      : values(std::move(values)) {}

  std::optional<std::vector<uint8_t>> Find(uint8_t type) const;
  void Add(uint8_t type, std::vector<uint8_t> value) {
    values.insert_or_assign(type, std::move(value));
  }
  void Remove(uint8_t type) { values.erase(type); }
  bool IsEmpty() const { return values.empty(); }
  void Clear() { values.clear(); }
  size_t Size() const { return values.size(); }
  const std::map<uint8_t, std::vector<uint8_t>>& Values() const {
    return values;
  }
  std::string ToString() const;
  size_t RawPacketSize() const;
  uint8_t* RawPacket(uint8_t* p_buf) const;
  std::vector<uint8_t> RawPacket() const;
  static LeAudioLtvMap Parse(const uint8_t* value, uint8_t len, bool& success);
  void Append(const LeAudioLtvMap& other);

 private:
  std::map<uint8_t, std::vector<uint8_t>> values;
};

struct LeAudioLc3Config {
  static const std::map<uint8_t, uint32_t> sampling_freq_map;
  static const std::map<uint8_t, uint32_t> frame_duration_map;

  std::optional<uint8_t> sampling_frequency;
  std::optional<uint8_t> frame_duration;
  std::optional<uint32_t> audio_channel_allocation;
  std::optional<uint16_t> octets_per_codec_frame;
  std::optional<uint8_t> codec_frames_blocks_per_sdu;

  uint8_t channel_count;

  /** Returns the sampling frequency representation in Hz */
  uint32_t GetSamplingFrequencyHz() const {
    if (sampling_frequency)
      return sampling_freq_map.count(*sampling_frequency)
                 ? sampling_freq_map.at(*sampling_frequency)
                 : 0;
    return 0;
  }

  /** Returns the frame duration representation in us */
  uint32_t GetFrameDurationUs() const {
    if (frame_duration)
      return frame_duration_map.count(*frame_duration)
                 ? frame_duration_map.at(*frame_duration)
                 : 0;

    return 0;
  }

  uint8_t GetChannelCount(void) const {
    if (channel_count) return channel_count;

    return 0;
  }

  LeAudioLtvMap GetAsLtvMap() const {
    std::map<uint8_t, std::vector<uint8_t>> values;

    if (sampling_frequency) {
      values[codec_spec_conf::kLeAudioCodecLC3TypeSamplingFreq] =
          UINT8_TO_VEC_UINT8(*sampling_frequency);
    }

    if (frame_duration) {
      values[codec_spec_conf::kLeAudioCodecLC3TypeFrameDuration] =
          UINT8_TO_VEC_UINT8(*frame_duration);
    }

    if (audio_channel_allocation) {
      values[codec_spec_conf::kLeAudioCodecLC3TypeAudioChannelAllocation] =
          UINT32_TO_VEC_UINT8(*audio_channel_allocation);
    }

    if (octets_per_codec_frame) {
      values[codec_spec_conf::kLeAudioCodecLC3TypeOctetPerFrame] =
          UINT16_TO_VEC_UINT8(*octets_per_codec_frame);
    }

    if (codec_frames_blocks_per_sdu) {
      values[codec_spec_conf::kLeAudioCodecLC3TypeCodecFrameBlocksPerSdu] =
          UINT8_TO_VEC_UINT8(*codec_frames_blocks_per_sdu);
    }

    return LeAudioLtvMap(values);
  }
};

struct LeAudioCodecId {
  uint8_t coding_format;
  uint16_t vendor_company_id;
  uint16_t vendor_codec_id;

  friend bool operator==(const LeAudioCodecId& lhs, const LeAudioCodecId& rhs) {
    if (lhs.coding_format != rhs.coding_format) return false;

    if (lhs.coding_format == kLeAudioCodingFormatVendorSpecific &&
        (lhs.vendor_company_id != rhs.vendor_company_id ||
         lhs.vendor_codec_id != rhs.vendor_codec_id))
      return false;

    return true;
  }

  friend bool operator!=(const LeAudioCodecId& lhs, const LeAudioCodecId& rhs) {
    return !(lhs == rhs);
  }
};

struct hdl_pair {
  hdl_pair() = default;
  hdl_pair(uint16_t val_hdl, uint16_t ccc_hdl)
      : val_hdl(val_hdl), ccc_hdl(ccc_hdl) {}

  uint16_t val_hdl = 0;
  uint16_t ccc_hdl = 0;
};

struct ase {
  static constexpr uint8_t kAseIdInvalid = 0x00;

  ase(uint16_t val_hdl, uint16_t ccc_hdl, uint8_t direction,
      uint8_t initial_id = kAseIdInvalid)
      : hdls(val_hdl, ccc_hdl),
        id(initial_id),
        cis_id(kInvalidCisId),
        direction(direction),
        target_latency(types::kTargetLatencyBalancedLatencyReliability),
        active(false),
        reconfigure(false),
        data_path_state(AudioStreamDataPathState::IDLE),
        configured_for_context_type(LeAudioContextType::UNINITIALIZED),
        preferred_phy(0),
        max_sdu_size(0),
        retrans_nb(0),
        max_transport_latency(0),
        pres_delay_min(0),
        pres_delay_max(0),
        preferred_pres_delay_min(0),
        preferred_pres_delay_max(0),
        state(AseState::BTA_LE_AUDIO_ASE_STATE_IDLE) {}

  struct hdl_pair hdls;
  uint8_t id;
  uint8_t cis_id;
  const uint8_t direction;
  uint8_t target_latency;
  uint16_t cis_conn_hdl = 0;

  bool active;
  bool reconfigure;
  AudioStreamDataPathState data_path_state;
  LeAudioContextType configured_for_context_type;

  /* Codec configuration */
  LeAudioCodecId codec_id;
  LeAudioLc3Config codec_config;
  uint8_t framing;
  uint8_t preferred_phy;

  /* Qos configuration */
  uint16_t max_sdu_size;
  uint8_t retrans_nb;
  uint16_t max_transport_latency;
  uint32_t pres_delay_min;
  uint32_t pres_delay_max;
  uint32_t preferred_pres_delay_min;
  uint32_t preferred_pres_delay_max;

  std::vector<uint8_t> metadata;

  AseState state;
};

struct BidirectAsesPair {
  struct ase* sink;
  struct ase* source;
};

struct acs_ac_record {
  LeAudioCodecId codec_id;
  LeAudioLtvMap codec_spec_caps;
  std::vector<uint8_t> metadata;
};

using PublishedAudioCapabilities =
    std::vector<std::tuple<hdl_pair, std::vector<acs_ac_record>>>;
using AudioLocations = std::bitset<32>;

std::ostream& operator<<(std::ostream& os, const AseState& state);
std::ostream& operator<<(std::ostream& os, const CigState& state);
std::ostream& operator<<(std::ostream& os, const LeAudioLc3Config& config);
std::ostream& operator<<(std::ostream& os, const LeAudioContextType& context);
std::ostream& operator<<(std::ostream& os,
                         const AudioStreamDataPathState& state);
std::ostream& operator<<(std::ostream& os, const AudioContexts& contexts);
}  // namespace types

namespace set_configurations {

struct CodecCapabilitySetting {
  types::LeAudioCodecId id;

  /* Codec Specific Configuration variant */
  std::variant<types::LeAudioLc3Config> config;

  /* Sampling freqency requested for codec */
  uint32_t GetConfigSamplingFrequency() const;
  /* Data fetch/feed interval for codec in microseconds */
  uint32_t GetConfigDataIntervalUs() const;
  /* Audio bit depth required for codec */
  uint8_t GetConfigBitsPerSample() const;
  /* Audio channels number for stream */
  uint8_t GetConfigChannelCount() const;
};

struct QosConfigSetting {
  uint8_t retransmission_number;
  uint16_t max_transport_latency;
};

struct SetConfiguration {
  SetConfiguration(uint8_t direction, uint8_t device_cnt, uint8_t ase_cnt,
                   uint8_t target_latency, CodecCapabilitySetting codec,
                   QosConfigSetting qos = {.retransmission_number = 0,
                                           .max_transport_latency = 0},
                   le_audio::types::LeAudioConfigurationStrategy strategy =
                       le_audio::types::LeAudioConfigurationStrategy::
                           MONO_ONE_CIS_PER_DEVICE)
      : direction(direction),
        device_cnt(device_cnt),
        ase_cnt(ase_cnt),
        target_latency(target_latency),
        codec(codec),
        qos(qos),
        strategy(strategy) {}

  uint8_t direction;  /* Direction of set */
  uint8_t device_cnt; /* How many devices must be in set */
  uint8_t ase_cnt;    /* How many ASE we need in configuration */
  uint8_t target_latency;
  CodecCapabilitySetting codec;
  QosConfigSetting qos;
  types::LeAudioConfigurationStrategy strategy;
};

/* Defined audio scenarios */
struct AudioSetConfiguration {
  std::string name;
  std::vector<struct SetConfiguration> confs;
};

using AudioSetConfigurations = std::vector<const AudioSetConfiguration*>;

const types::LeAudioCodecId LeAudioCodecIdLc3 = {
    .coding_format = types::kLeAudioCodingFormatLC3,
    .vendor_company_id = types::kLeAudioVendorCompanyIdUndefined,
    .vendor_codec_id = types::kLeAudioVendorCodecIdUndefined};

static constexpr uint32_t kChannelAllocationStereo =
    codec_spec_conf::kLeAudioLocationFrontLeft |
    codec_spec_conf::kLeAudioLocationFrontRight;

/* Declarations */
void get_cis_count(const AudioSetConfigurations& audio_set_configurations,
                   int expected_device_cnt,
                   types::LeAudioConfigurationStrategy strategy,
                   int group_ase_snk_cnt, int group_ase_src_count,
                   uint8_t& cis_count_bidir, uint8_t& cis_count_unidir_sink,
                   uint8_t& cis_count_unidir_source);
bool check_if_may_cover_scenario(
    const AudioSetConfigurations* audio_set_configurations, uint8_t group_size);
bool check_if_may_cover_scenario(
    const AudioSetConfiguration* audio_set_configuration, uint8_t group_size);
bool IsCodecCapabilitySettingSupported(
    const types::acs_ac_record& pac_record,
    const CodecCapabilitySetting& codec_capability_setting);
uint8_t get_num_of_devices_in_configuration(
    const AudioSetConfiguration* audio_set_configuration);
}  // namespace set_configurations

struct stream_configuration {
  bool pending_configuration;

  types::LeAudioCodecId id;

  /* Pointer to chosen req */
  const le_audio::set_configurations::AudioSetConfiguration* conf;

  /* Sink configuration */
  /* For now we have always same frequency for all the channels */
  uint32_t sink_sample_frequency_hz;
  uint32_t sink_frame_duration_us;
  uint16_t sink_octets_per_codec_frame;
  uint32_t sink_audio_channel_allocation;
  uint8_t sink_codec_frames_blocks_per_sdu;
  /* Number of channels is what we will request from audio framework */
  uint8_t sink_num_of_channels;
  int sink_num_of_devices;
  /* cis_handle, audio location*/
  std::vector<std::pair<uint16_t, uint32_t>> sink_streams;
  /* cis_handle, target allocation */
  std::vector<std::pair<uint16_t, uint32_t>>
      sink_offloader_streams_target_allocation;
  /* cis_handle, current allocation */
  std::vector<std::pair<uint16_t, uint32_t>>
      sink_offloader_streams_current_allocation;
  bool sink_offloader_changed;
  bool sink_is_initial;

  /* Source configuration */
  /* For now we have always same frequency for all the channels */
  uint32_t source_sample_frequency_hz;
  uint32_t source_frame_duration_us;
  uint16_t source_octets_per_codec_frame;
  uint32_t source_audio_channel_allocation;
  uint8_t source_codec_frames_blocks_per_sdu;
  /* Number of channels is what we will request from audio framework */
  uint8_t source_num_of_channels;
  int source_num_of_devices;
  /* cis_handle, audio location*/
  std::vector<std::pair<uint16_t, uint32_t>> source_streams;
  /* cis_handle, target allocation */
  std::vector<std::pair<uint16_t, uint32_t>>
      source_offloader_streams_target_allocation;
  /* cis_handle, current allocation */
  std::vector<std::pair<uint16_t, uint32_t>>
      source_offloader_streams_current_allocation;
  bool source_offloader_changed;
  bool source_is_initial;
};

void AppendMetadataLtvEntryForCcidList(std::vector<uint8_t>& metadata,
                                       const std::vector<uint8_t>& ccid_list);
void AppendMetadataLtvEntryForStreamingContext(
    std::vector<uint8_t>& metadata, types::AudioContexts context_type);
uint8_t GetMaxCodecFramesPerSduFromPac(const types::acs_ac_record* pac_record);
uint32_t AdjustAllocationForOffloader(uint32_t allocation);
}  // namespace le_audio