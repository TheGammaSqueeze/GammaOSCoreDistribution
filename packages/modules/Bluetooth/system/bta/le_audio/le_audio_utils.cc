/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "le_audio_utils.h"

#include "bta/le_audio/content_control_id_keeper.h"
#include "gd/common/strings.h"
#include "le_audio_types.h"
#include "osi/include/log.h"

using bluetooth::common::ToString;
using le_audio::types::AudioContexts;
using le_audio::types::LeAudioContextType;

namespace le_audio {
namespace utils {

/* The returned LeAudioContextType should have its entry in the
 * AudioSetConfigurationProvider's ContextTypeToScenario mapping table.
 * Otherwise the AudioSetConfigurationProvider will fall back
 * to default scenario.
 */
LeAudioContextType AudioContentToLeAudioContext(
    audio_content_type_t content_type, audio_usage_t usage) {
  /* Check audio attribute usage of stream */
  switch (usage) {
    case AUDIO_USAGE_MEDIA:
      return LeAudioContextType::MEDIA;
    case AUDIO_USAGE_ASSISTANT:
      return LeAudioContextType::VOICEASSISTANTS;
    case AUDIO_USAGE_VOICE_COMMUNICATION:
    case AUDIO_USAGE_CALL_ASSISTANT:
      return LeAudioContextType::CONVERSATIONAL;
    case AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING:
      if (content_type == AUDIO_CONTENT_TYPE_SPEECH)
        return LeAudioContextType::CONVERSATIONAL;
      else
        return LeAudioContextType::MEDIA;
    case AUDIO_USAGE_GAME:
      return LeAudioContextType::GAME;
    case AUDIO_USAGE_NOTIFICATION:
      return LeAudioContextType::NOTIFICATIONS;
    case AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE:
      return LeAudioContextType::RINGTONE;
    case AUDIO_USAGE_ALARM:
      return LeAudioContextType::ALERTS;
    case AUDIO_USAGE_EMERGENCY:
      return LeAudioContextType::EMERGENCYALARM;
    case AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
      return LeAudioContextType::INSTRUCTIONAL;
    case AUDIO_USAGE_ASSISTANCE_SONIFICATION:
      return LeAudioContextType::SOUNDEFFECTS;
    default:
      break;
  }

  return LeAudioContextType::MEDIA;
}

static std::string usageToString(audio_usage_t usage) {
  switch (usage) {
    case AUDIO_USAGE_UNKNOWN:
      return "USAGE_UNKNOWN";
    case AUDIO_USAGE_MEDIA:
      return "USAGE_MEDIA";
    case AUDIO_USAGE_VOICE_COMMUNICATION:
      return "USAGE_VOICE_COMMUNICATION";
    case AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING:
      return "USAGE_VOICE_COMMUNICATION_SIGNALLING";
    case AUDIO_USAGE_ALARM:
      return "USAGE_ALARM";
    case AUDIO_USAGE_NOTIFICATION:
      return "USAGE_NOTIFICATION";
    case AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE:
      return "USAGE_NOTIFICATION_TELEPHONY_RINGTONE";
    case AUDIO_USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
      return "USAGE_NOTIFICATION_COMMUNICATION_REQUEST";
    case AUDIO_USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
      return "USAGE_NOTIFICATION_COMMUNICATION_INSTANT";
    case AUDIO_USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
      return "USAGE_NOTIFICATION_COMMUNICATION_DELAYED";
    case AUDIO_USAGE_NOTIFICATION_EVENT:
      return "USAGE_NOTIFICATION_EVENT";
    case AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY:
      return "USAGE_ASSISTANCE_ACCESSIBILITY";
    case AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
      return "USAGE_ASSISTANCE_NAVIGATION_GUIDANCE";
    case AUDIO_USAGE_ASSISTANCE_SONIFICATION:
      return "USAGE_ASSISTANCE_SONIFICATION";
    case AUDIO_USAGE_GAME:
      return "USAGE_GAME";
    case AUDIO_USAGE_ASSISTANT:
      return "USAGE_ASSISTANT";
    case AUDIO_USAGE_CALL_ASSISTANT:
      return "USAGE_CALL_ASSISTANT";
    case AUDIO_USAGE_EMERGENCY:
      return "USAGE_EMERGENCY";
    case AUDIO_USAGE_SAFETY:
      return "USAGE_SAFETY";
    case AUDIO_USAGE_VEHICLE_STATUS:
      return "USAGE_VEHICLE_STATUS";
    case AUDIO_USAGE_ANNOUNCEMENT:
      return "USAGE_ANNOUNCEMENT";
    default:
      return "unknown usage ";
  }
}

static std::string contentTypeToString(audio_content_type_t content_type) {
  switch (content_type) {
    case AUDIO_CONTENT_TYPE_UNKNOWN:
      return "CONTENT_TYPE_UNKNOWN";
    case AUDIO_CONTENT_TYPE_SPEECH:
      return "CONTENT_TYPE_SPEECH";
    case AUDIO_CONTENT_TYPE_MUSIC:
      return "CONTENT_TYPE_MUSIC";
    case AUDIO_CONTENT_TYPE_MOVIE:
      return "CONTENT_TYPE_MOVIE";
    case AUDIO_CONTENT_TYPE_SONIFICATION:
      return "CONTENT_TYPE_SONIFICATION";
    default:
      return "unknown content type ";
  }
}

static const char* audioSourceToStr(audio_source_t source) {
  const char* strArr[] = {
      "AUDIO_SOURCE_DEFAULT",           "AUDIO_SOURCE_MIC",
      "AUDIO_SOURCE_VOICE_UPLINK",      "AUDIO_SOURCE_VOICE_DOWNLINK",
      "AUDIO_SOURCE_VOICE_CALL",        "AUDIO_SOURCE_CAMCORDER",
      "AUDIO_SOURCE_VOICE_RECOGNITION", "AUDIO_SOURCE_VOICE_COMMUNICATION",
      "AUDIO_SOURCE_REMOTE_SUBMIX",     "AUDIO_SOURCE_UNPROCESSED",
      "AUDIO_SOURCE_VOICE_PERFORMANCE"};

  if (static_cast<uint32_t>(source) < (sizeof(strArr) / sizeof(strArr[0])))
    return strArr[source];
  return "UNKNOWN";
}

AudioContexts GetAllowedAudioContextsFromSourceMetadata(
    const std::vector<struct playback_track_metadata>& source_metadata,
    AudioContexts allowed_contexts) {
  AudioContexts track_contexts;
  for (auto& track : source_metadata) {
    if (track.content_type == 0 && track.usage == 0) continue;

    LOG_INFO("%s: usage=%s(%d), content_type=%s(%d), gain=%f", __func__,
             usageToString(track.usage).c_str(), track.usage,
             contentTypeToString(track.content_type).c_str(),
             track.content_type, track.gain);

    track_contexts.set(
        AudioContentToLeAudioContext(track.content_type, track.usage));
  }
  track_contexts &= allowed_contexts;
  LOG_INFO("%s: allowed context= %s", __func__,
           track_contexts.to_string().c_str());

  return track_contexts;
}

AudioContexts GetAllowedAudioContextsFromSinkMetadata(
    const std::vector<struct record_track_metadata>& sink_metadata,
    AudioContexts allowed_contexts) {
  AudioContexts all_track_contexts;

  for (auto& track : sink_metadata) {
    if (track.source == AUDIO_SOURCE_INVALID) continue;
    LeAudioContextType track_context;

    LOG_DEBUG(
        "source=%s(0x%02x), gain=%f, destination device=0x%08x, destination "
        "device address=%.32s, allowed_contexts=%s",
        audioSourceToStr(track.source), track.source, track.gain,
        track.dest_device, track.dest_device_address,
        bluetooth::common::ToString(allowed_contexts).c_str());

    if ((track.source == AUDIO_SOURCE_MIC) &&
        (allowed_contexts.test(LeAudioContextType::LIVE))) {
      track_context = LeAudioContextType::LIVE;

    } else if ((track.source == AUDIO_SOURCE_VOICE_COMMUNICATION) &&
               (allowed_contexts.test(LeAudioContextType::CONVERSATIONAL))) {
      track_context = LeAudioContextType::CONVERSATIONAL;

    } else if (allowed_contexts.test(LeAudioContextType::VOICEASSISTANTS)) {
      /* Fallback to voice assistant
       * This will handle also a case when the device is
       * AUDIO_SOURCE_VOICE_RECOGNITION
       */
      track_context = LeAudioContextType::VOICEASSISTANTS;
      LOG_WARN(
          "Could not match the recording track type to group available "
          "context. Using context %s.",
          ToString(track_context).c_str());
    }

    all_track_contexts.set(track_context);
  }

  if (all_track_contexts.none()) {
    all_track_contexts = AudioContexts(
        static_cast<std::underlying_type<LeAudioContextType>::type>(
            LeAudioContextType::UNSPECIFIED));
    LOG_DEBUG(
        "Unable to find supported audio source context for the remote audio "
        "sink device. This may result in voice back channel malfunction.");
  }

  LOG_DEBUG("Allowed contexts from sink metadata: %s (0x%08hx)",
            bluetooth::common::ToString(all_track_contexts).c_str(),
            all_track_contexts.value());
  return all_track_contexts;
}

std::vector<uint8_t> GetAllCcids(const AudioContexts& contexts) {
  auto ccid_keeper = ContentControlIdKeeper::GetInstance();
  std::vector<uint8_t> ccid_vec;

  for (LeAudioContextType context : types::kLeAudioContextAllTypesArray) {
    if (!contexts.test(context)) continue;
    using T = std::underlying_type<LeAudioContextType>::type;
    auto ccid = ccid_keeper->GetCcid(static_cast<T>(context));
    if (ccid != -1) {
      ccid_vec.push_back(static_cast<uint8_t>(ccid));
    }
  }

  return ccid_vec;
}

}  // namespace utils
}  // namespace le_audio
