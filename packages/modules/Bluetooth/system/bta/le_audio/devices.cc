/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA
 * - www.ehima.com
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

#include "devices.h"

#include <base/strings/string_number_conversions.h>

#include <map>

#include "audio_hal_client/audio_hal_client.h"
#include "bta_csis_api.h"
#include "bta_gatt_queue.h"
#include "bta_groups.h"
#include "bta_le_audio_api.h"
#include "btif_storage.h"
#include "btm_iso_api.h"
#include "btm_iso_api_types.h"
#include "device/include/controller.h"
#include "gd/common/strings.h"
#include "le_audio_set_configuration_provider.h"
#include "metrics_collector.h"
#include "osi/include/log.h"
#include "stack/include/acl_api.h"

using bluetooth::hci::kIsoCigFramingFramed;
using bluetooth::hci::kIsoCigFramingUnframed;
using bluetooth::hci::kIsoCigPackingSequential;
using bluetooth::hci::kIsoCigPhy1M;
using bluetooth::hci::kIsoCigPhy2M;
using bluetooth::hci::iso_manager::kIsoSca0To20Ppm;
using le_audio::AudioSetConfigurationProvider;
using le_audio::DeviceConnectState;
using le_audio::set_configurations::CodecCapabilitySetting;
using le_audio::types::ase;
using le_audio::types::AseState;
using le_audio::types::AudioContexts;
using le_audio::types::AudioLocations;
using le_audio::types::AudioStreamDataPathState;
using le_audio::types::BidirectAsesPair;
using le_audio::types::CisType;
using le_audio::types::LeAudioCodecId;
using le_audio::types::LeAudioContextType;
using le_audio::types::LeAudioLc3Config;

namespace le_audio {
std::ostream& operator<<(std::ostream& os, const DeviceConnectState& state) {
  const char* char_value_ = "UNKNOWN";

  switch (state) {
    case DeviceConnectState::CONNECTED:
      char_value_ = "CONNECTED";
      break;
    case DeviceConnectState::DISCONNECTED:
      char_value_ = "DISCONNECTED";
      break;
    case DeviceConnectState::REMOVING:
      char_value_ = "REMOVING";
      break;
    case DeviceConnectState::DISCONNECTING:
      char_value_ = "DISCONNECTING";
      break;
    case DeviceConnectState::PENDING_REMOVAL:
      char_value_ = "PENDING_REMOVAL";
      break;
    case DeviceConnectState::CONNECTING_BY_USER:
      char_value_ = "CONNECTING_BY_USER";
      break;
    case DeviceConnectState::CONNECTED_BY_USER_GETTING_READY:
      char_value_ = "CONNECTED_BY_USER_GETTING_READY";
      break;
    case DeviceConnectState::CONNECTING_AUTOCONNECT:
      char_value_ = "CONNECTING_AUTOCONNECT";
      break;
    case DeviceConnectState::CONNECTED_AUTOCONNECT_GETTING_READY:
      char_value_ = "CONNECTED_AUTOCONNECT_GETTING_READY";
      break;
  }

  os << char_value_ << " ("
     << "0x" << std::setfill('0') << std::setw(2) << static_cast<int>(state)
     << ")";
  return os;
}

/* LeAudioDeviceGroup Class methods implementation */
void LeAudioDeviceGroup::AddNode(
    const std::shared_ptr<LeAudioDevice>& leAudioDevice) {
  leAudioDevice->group_id_ = group_id_;
  leAudioDevices_.push_back(std::weak_ptr<LeAudioDevice>(leAudioDevice));
  MetricsCollector::Get()->OnGroupSizeUpdate(group_id_, leAudioDevices_.size());
}

void LeAudioDeviceGroup::RemoveNode(
    const std::shared_ptr<LeAudioDevice>& leAudioDevice) {
  /* Group information cleaning in the device. */
  leAudioDevice->group_id_ = bluetooth::groups::kGroupUnknown;
  for (auto ase : leAudioDevice->ases_) {
    ase.active = false;
    ase.cis_conn_hdl = 0;
  }

  leAudioDevices_.erase(
      std::remove_if(
          leAudioDevices_.begin(), leAudioDevices_.end(),
          [&leAudioDevice](auto& d) { return d.lock() == leAudioDevice; }),
      leAudioDevices_.end());
  MetricsCollector::Get()->OnGroupSizeUpdate(group_id_, leAudioDevices_.size());
}

bool LeAudioDeviceGroup::IsEmpty(void) { return leAudioDevices_.size() == 0; }

bool LeAudioDeviceGroup::IsAnyDeviceConnected(void) {
  return (NumOfConnected() != 0);
}

int LeAudioDeviceGroup::Size(void) { return leAudioDevices_.size(); }

int LeAudioDeviceGroup::NumOfConnected(types::LeAudioContextType context_type) {
  if (leAudioDevices_.empty()) return 0;

  bool check_context_type = (context_type != LeAudioContextType::RFU);
  AudioContexts type_set(context_type);

  /* return number of connected devices from the set*/
  return std::count_if(
      leAudioDevices_.begin(), leAudioDevices_.end(),
      [type_set, check_context_type](auto& iter) {
        if (iter.expired()) return false;
        if (iter.lock()->conn_id_ == GATT_INVALID_CONN_ID) return false;

        if (!check_context_type) return true;

        return iter.lock()->GetAvailableContexts().test_any(type_set);
      });
}

void LeAudioDeviceGroup::ClearSinksFromConfiguration(void) {
  LOG_INFO("Group %p, group_id %d", this, group_id_);
  stream_conf.sink_streams.clear();
  stream_conf.sink_offloader_streams_target_allocation.clear();
  stream_conf.sink_offloader_streams_current_allocation.clear();
  stream_conf.sink_audio_channel_allocation = 0;
  stream_conf.sink_num_of_channels = 0;
  stream_conf.sink_num_of_devices = 0;
  stream_conf.sink_sample_frequency_hz = 0;
  stream_conf.sink_codec_frames_blocks_per_sdu = 0;
  stream_conf.sink_octets_per_codec_frame = 0;
  stream_conf.sink_frame_duration_us = 0;
}

void LeAudioDeviceGroup::ClearSourcesFromConfiguration(void) {
  LOG_INFO("Group %p, group_id %d", this, group_id_);
  stream_conf.source_streams.clear();
  stream_conf.source_offloader_streams_target_allocation.clear();
  stream_conf.source_offloader_streams_current_allocation.clear();
  stream_conf.source_audio_channel_allocation = 0;
  stream_conf.source_num_of_channels = 0;
  stream_conf.source_num_of_devices = 0;
  stream_conf.source_sample_frequency_hz = 0;
  stream_conf.source_codec_frames_blocks_per_sdu = 0;
  stream_conf.source_octets_per_codec_frame = 0;
  stream_conf.source_frame_duration_us = 0;
}

void LeAudioDeviceGroup::CigClearCis(void) {
  LOG_INFO("group_id: %d", group_id_);
  cises_.clear();
  ClearSinksFromConfiguration();
  ClearSourcesFromConfiguration();
}

void LeAudioDeviceGroup::Cleanup(void) {
  /* Bluetooth is off while streaming - disconnect CISes and remove CIG */
  if (GetState() == AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
    if (!stream_conf.sink_streams.empty()) {
      for (auto [cis_handle, audio_location] : stream_conf.sink_streams) {
        bluetooth::hci::IsoManager::GetInstance()->DisconnectCis(
            cis_handle, HCI_ERR_PEER_USER);

        if (stream_conf.source_streams.empty()) {
          continue;
        }
        uint16_t cis_hdl = cis_handle;
        stream_conf.source_streams.erase(
            std::remove_if(
                stream_conf.source_streams.begin(),
                stream_conf.source_streams.end(),
                [cis_hdl](auto& pair) { return pair.first == cis_hdl; }),
            stream_conf.source_streams.end());
      }
    }

    if (!stream_conf.source_streams.empty()) {
      for (auto [cis_handle, audio_location] : stream_conf.source_streams) {
        bluetooth::hci::IsoManager::GetInstance()->DisconnectCis(
            cis_handle, HCI_ERR_PEER_USER);
      }
    }
  }

  /* Note: CIG will stay in the controller. We cannot remove it here, because
   * Cises are not yet disconnected.
   * When user start Bluetooth, HCI Reset should remove it
   */

  leAudioDevices_.clear();
  this->CigClearCis();
}

void LeAudioDeviceGroup::Deactivate(void) {
  for (auto* leAudioDevice = GetFirstActiveDevice(); leAudioDevice;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    for (auto* ase = leAudioDevice->GetFirstActiveAse(); ase;
         ase = leAudioDevice->GetNextActiveAse(ase)) {
      ase->active = false;
    }
  }
}

le_audio::types::CigState LeAudioDeviceGroup::GetCigState(void) {
  return cig_state_;
}

void LeAudioDeviceGroup::SetCigState(le_audio::types::CigState state) {
  LOG_VERBOSE("%s -> %s", bluetooth::common::ToString(cig_state_).c_str(),
              bluetooth::common::ToString(state).c_str());
  cig_state_ = state;
}

bool LeAudioDeviceGroup::Activate(LeAudioContextType context_type) {
  bool is_activate = false;
  for (auto leAudioDevice : leAudioDevices_) {
    if (leAudioDevice.expired()) continue;

    bool activated = leAudioDevice.lock()->ActivateConfiguredAses(context_type);
    LOG_INFO("Device %s is %s",
             leAudioDevice.lock().get()->address_.ToString().c_str(),
             activated ? "activated" : " not activated");
    if (activated) {
      if (!CigAssignCisIds(leAudioDevice.lock().get())) {
        return false;
      }
      is_activate = true;
    }
  }
  return is_activate;
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstDevice(void) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [](auto& iter) { return !iter.expired(); });

  if (iter == leAudioDevices_.end()) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstDeviceWithActiveContext(
    types::LeAudioContextType context_type) {
  auto iter = std::find_if(
      leAudioDevices_.begin(), leAudioDevices_.end(),
      [&context_type](auto& iter) {
        if (iter.expired()) return false;
        return iter.lock()->GetAvailableContexts().test(context_type);
      });

  if ((iter == leAudioDevices_.end()) || (iter->expired())) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextDevice(LeAudioDevice* leAudioDevice) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  /* If reference device not found */
  if (iter == leAudioDevices_.end()) return nullptr;

  std::advance(iter, 1);
  /* If reference device is last in group */
  if (iter == leAudioDevices_.end()) return nullptr;

  if (iter->expired()) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextDeviceWithActiveContext(
    LeAudioDevice* leAudioDevice, types::LeAudioContextType context_type) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  /* If reference device not found */
  if (iter == leAudioDevices_.end()) return nullptr;

  std::advance(iter, 1);
  /* If reference device is last in group */
  if (iter == leAudioDevices_.end()) return nullptr;

  iter = std::find_if(iter, leAudioDevices_.end(), [&context_type](auto& d) {
    if (d.expired())
      return false;
    else
      return d.lock()->GetAvailableContexts().test(context_type);
    ;
  });

  return (iter == leAudioDevices_.end()) ? nullptr : (iter->lock()).get();
}

bool LeAudioDeviceGroup::IsDeviceInTheGroup(LeAudioDevice* leAudioDevice) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  if ((iter == leAudioDevices_.end()) || (iter->expired())) return false;

  return true;
}

bool LeAudioDeviceGroup::HaveAllActiveDevicesAsesTheSameState(AseState state) {
  auto iter = std::find_if(
      leAudioDevices_.begin(), leAudioDevices_.end(), [&state](auto& d) {
        if (d.expired())
          return false;
        else
          return !(((d.lock()).get())->HaveAllActiveAsesSameState(state));
      });

  return iter == leAudioDevices_.end();
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstActiveDevice(void) {
  auto iter =
      std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(), [](auto& d) {
        if (d.expired())
          return false;
        else
          return ((d.lock()).get())->HaveActiveAse();
      });

  if (iter == leAudioDevices_.end() || iter->expired()) return nullptr;

  return (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextActiveDevice(
    LeAudioDevice* leAudioDevice) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired())
                               return false;
                             else
                               return (d.lock()).get() == leAudioDevice;
                           });

  if (iter == leAudioDevices_.end() ||
      std::distance(iter, leAudioDevices_.end()) < 1)
    return nullptr;

  iter = std::find_if(std::next(iter, 1), leAudioDevices_.end(), [](auto& d) {
    if (d.expired())
      return false;
    else
      return ((d.lock()).get())->HaveActiveAse();
  });

  return (iter == leAudioDevices_.end()) ? nullptr : (iter->lock()).get();
}

LeAudioDevice* LeAudioDeviceGroup::GetFirstActiveDeviceByDataPathState(
    AudioStreamDataPathState data_path_state) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&data_path_state](auto& d) {
                             if (d.expired()) {
                               return false;
                             }

                             return (((d.lock()).get())
                                         ->GetFirstActiveAseByDataPathState(
                                             data_path_state) != nullptr);
                           });

  if (iter == leAudioDevices_.end()) {
    return nullptr;
  }

  return iter->lock().get();
}

LeAudioDevice* LeAudioDeviceGroup::GetNextActiveDeviceByDataPathState(
    LeAudioDevice* leAudioDevice, AudioStreamDataPathState data_path_state) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&leAudioDevice](auto& d) {
                             if (d.expired()) {
                               return false;
                             }

                             return d.lock().get() == leAudioDevice;
                           });

  if (std::distance(iter, leAudioDevices_.end()) < 1) {
    return nullptr;
  }

  iter = std::find_if(
      std::next(iter, 1), leAudioDevices_.end(), [&data_path_state](auto& d) {
        if (d.expired()) {
          return false;
        }

        return (((d.lock()).get())
                    ->GetFirstActiveAseByDataPathState(data_path_state) !=
                nullptr);
      });

  if (iter == leAudioDevices_.end()) {
    return nullptr;
  }

  return iter->lock().get();
}

uint32_t LeAudioDeviceGroup::GetSduInterval(uint8_t direction) {
  for (LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
       leAudioDevice != nullptr;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    struct ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
    if (!ase) continue;

    return ase->codec_config.GetFrameDurationUs();
  }

  return 0;
}

uint8_t LeAudioDeviceGroup::GetSCA(void) {
  uint8_t sca = kIsoSca0To20Ppm;

  for (const auto& leAudioDevice : leAudioDevices_) {
    uint8_t dev_sca =
        BTM_GetPeerSCA(leAudioDevice.lock()->address_, BT_TRANSPORT_LE);

    /* If we could not read SCA from the peer device or sca is 0,
     * then there is no reason to continue.
     */
    if ((dev_sca == 0xFF) || (dev_sca == 0)) return 0;

    /* The Slaves_Clock_Accuracy parameter shall be the worst-case sleep clock
     *accuracy of all the slaves that will participate in the CIG.
     */
    if (dev_sca < sca) {
      sca = dev_sca;
    }
  }

  return sca;
}

uint8_t LeAudioDeviceGroup::GetPacking(void) {
  /* TODO: Decide about packing */
  return kIsoCigPackingSequential;
}

uint8_t LeAudioDeviceGroup::GetFraming(void) {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    struct ase* ase = leAudioDevice->GetFirstActiveAse();
    if (!ase) continue;

    do {
      if (ase->framing == types::kFramingUnframedPduUnsupported)
        return kIsoCigFramingFramed;
    } while ((ase = leAudioDevice->GetNextActiveAse(ase)));
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return kIsoCigFramingUnframed;
}

/* TODO: Preferred parameter may be other than minimum */
static uint16_t find_max_transport_latency(LeAudioDeviceGroup* group,
                                           uint8_t direction) {
  uint16_t max_transport_latency = 0;

  for (LeAudioDevice* leAudioDevice = group->GetFirstActiveDevice();
       leAudioDevice != nullptr;
       leAudioDevice = group->GetNextActiveDevice(leAudioDevice)) {
    for (ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
         ase != nullptr;
         ase = leAudioDevice->GetNextActiveAseWithSameDirection(ase)) {
      if (!ase) break;

      if (!max_transport_latency)
        // first assignment
        max_transport_latency = ase->max_transport_latency;
      else if (ase->max_transport_latency < max_transport_latency)
        max_transport_latency = ase->max_transport_latency;
    }
  }

  if (max_transport_latency < types::kMaxTransportLatencyMin)
    max_transport_latency = types::kMaxTransportLatencyMin;
  else if (max_transport_latency > types::kMaxTransportLatencyMax)
    max_transport_latency = types::kMaxTransportLatencyMax;

  return max_transport_latency;
}

uint16_t LeAudioDeviceGroup::GetMaxTransportLatencyStom(void) {
  return find_max_transport_latency(this, types::kLeAudioDirectionSource);
}

uint16_t LeAudioDeviceGroup::GetMaxTransportLatencyMtos(void) {
  return find_max_transport_latency(this, types::kLeAudioDirectionSink);
}

uint32_t LeAudioDeviceGroup::GetTransportLatencyUs(uint8_t direction) {
  if (direction == types::kLeAudioDirectionSink) {
    return transport_latency_mtos_us_;
  } else if (direction == types::kLeAudioDirectionSource) {
    return transport_latency_stom_us_ ;
  } else {
    LOG(ERROR) << __func__ << ", invalid direction";
    return 0;
  }
}

void LeAudioDeviceGroup::SetTransportLatency(uint8_t direction,
                                             uint32_t new_transport_latency_us) {
  uint32_t* transport_latency_us;

  if (direction == types::kLeAudioDirectionSink) {
    transport_latency_us = &transport_latency_mtos_us_;
  } else if (direction == types::kLeAudioDirectionSource) {
    transport_latency_us = &transport_latency_stom_us_;
  } else {
    LOG(ERROR) << __func__ << ", invalid direction";
    return;
  }

  if (*transport_latency_us == new_transport_latency_us) return;

  if ((*transport_latency_us != 0) &&
      (*transport_latency_us != new_transport_latency_us)) {
    LOG(WARNING) << __func__ << ", Different transport latency for group: "
                 << " old: " << static_cast<int>(*transport_latency_us)
                 << " [us], new: " << static_cast<int>(new_transport_latency_us)
                 << " [us]";
    return;
  }

  LOG(INFO) << __func__ << ", updated group " << static_cast<int>(group_id_)
            << " transport latency: " << static_cast<int>(new_transport_latency_us)
            << " [us]";
  *transport_latency_us = new_transport_latency_us;
}

uint8_t LeAudioDeviceGroup::GetRtn(uint8_t direction, uint8_t cis_id) {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    auto ases_pair = leAudioDevice->GetAsesByCisId(cis_id);

    if (ases_pair.sink && direction == types::kLeAudioDirectionSink) {
      return ases_pair.sink->retrans_nb;
    } else if (ases_pair.source &&
               direction == types::kLeAudioDirectionSource) {
      return ases_pair.source->retrans_nb;
    }
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return 0;
}

uint16_t LeAudioDeviceGroup::GetMaxSduSize(uint8_t direction, uint8_t cis_id) {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    auto ases_pair = leAudioDevice->GetAsesByCisId(cis_id);

    if (ases_pair.sink && direction == types::kLeAudioDirectionSink) {
      return ases_pair.sink->max_sdu_size;
    } else if (ases_pair.source &&
               direction == types::kLeAudioDirectionSource) {
      return ases_pair.source->max_sdu_size;
    }
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return 0;
}

uint8_t LeAudioDeviceGroup::GetPhyBitmask(uint8_t direction) {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  // local supported PHY's
  uint8_t phy_bitfield = kIsoCigPhy1M;
  if (controller_get_interface()->supports_ble_2m_phy())
    phy_bitfield |= kIsoCigPhy2M;

  if (!leAudioDevice) {
    LOG(ERROR) << "No active leaudio device for direction?: " << +direction;
    return phy_bitfield;
  }

  do {
    struct ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
    if (!ase) return phy_bitfield;

    do {
      if (direction == ase->direction) {
        phy_bitfield &= leAudioDevice->GetPhyBitmask();

        // A value of 0x00 denotes no preference
        if (ase->preferred_phy && (phy_bitfield & ase->preferred_phy)) {
          phy_bitfield &= ase->preferred_phy;
          LOG_DEBUG("Using ASE preferred phy 0x%02x",
                    static_cast<int>(phy_bitfield));
        } else {
          LOG_WARN(
              "ASE preferred 0x%02x has nothing common with phy_bitfield "
              "0x%02x ",
              static_cast<int>(ase->preferred_phy),
              static_cast<int>(phy_bitfield));
        }
      }
    } while ((ase = leAudioDevice->GetNextActiveAseWithSameDirection(ase)));
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  return phy_bitfield;
}

uint8_t LeAudioDeviceGroup::GetTargetPhy(uint8_t direction) {
  uint8_t phy_bitfield = GetPhyBitmask(direction);

  // prefer to use 2M if supported
  if (phy_bitfield & kIsoCigPhy2M)
    return types::kTargetPhy2M;
  else if (phy_bitfield & kIsoCigPhy1M)
    return types::kTargetPhy1M;
  else
    return 0;
}

bool LeAudioDeviceGroup::GetPresentationDelay(uint32_t* delay,
                                              uint8_t direction) {
  uint32_t delay_min = 0;
  uint32_t delay_max = UINT32_MAX;
  uint32_t preferred_delay_min = delay_min;
  uint32_t preferred_delay_max = delay_max;

  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  LOG_ASSERT(leAudioDevice)
      << __func__ << " Shouldn't be called without an active device.";

  do {
    struct ase* ase = leAudioDevice->GetFirstActiveAseByDirection(direction);
    if (!ase) continue;  // device has no active ASEs in this direction

    do {
      /* No common range check */
      if (ase->pres_delay_min > delay_max || ase->pres_delay_max < delay_min)
        return false;

      if (ase->pres_delay_min > delay_min) delay_min = ase->pres_delay_min;
      if (ase->pres_delay_max < delay_max) delay_max = ase->pres_delay_max;
      if (ase->preferred_pres_delay_min > preferred_delay_min)
        preferred_delay_min = ase->preferred_pres_delay_min;
      if (ase->preferred_pres_delay_max < preferred_delay_max &&
          ase->preferred_pres_delay_max != types::kPresDelayNoPreference)
        preferred_delay_max = ase->preferred_pres_delay_max;
    } while ((ase = leAudioDevice->GetNextActiveAseWithSameDirection(ase)));
  } while ((leAudioDevice = GetNextActiveDevice(leAudioDevice)));

  if (preferred_delay_min <= preferred_delay_max &&
      preferred_delay_min > delay_min && preferred_delay_min < delay_max) {
    *delay = preferred_delay_min;
  } else {
    *delay = delay_min;
  }

  return true;
}

uint16_t LeAudioDeviceGroup::GetRemoteDelay(uint8_t direction) {
  uint16_t remote_delay_ms = 0;
  uint32_t presentation_delay;

  if (!GetPresentationDelay(&presentation_delay, direction)) {
    /* This should never happens at stream request time but to be safe return
     * some sample value to not break streaming
     */
    return 100;
  }

  /* us to ms */
  remote_delay_ms = presentation_delay / 1000;
  remote_delay_ms += GetTransportLatencyUs(direction) / 1000;

  return remote_delay_ms;
}

void LeAudioDeviceGroup::UpdateAudioContextTypeAvailability(void) {
  LOG_DEBUG(" group id: %d, available contexts: %s", group_id_,
            group_available_contexts_.to_string().c_str());
  UpdateAudioContextTypeAvailability(group_available_contexts_);
}

/* Returns true if support for any type in the whole group has changed,
 * otherwise false. */
bool LeAudioDeviceGroup::UpdateAudioContextTypeAvailability(
    AudioContexts update_contexts) {
  auto new_contexts = AudioContexts();
  bool active_contexts_has_been_modified = false;

  if (update_contexts.none()) {
    LOG_DEBUG("No context updated");
    return false;
  }

  LOG_DEBUG("Updated context: %s", update_contexts.to_string().c_str());

  for (LeAudioContextType ctx_type : types::kLeAudioContextAllTypesArray) {
    LOG_DEBUG("Checking context: %s", ToHexString(ctx_type).c_str());

    if (!update_contexts.test(ctx_type)) {
      LOG_DEBUG("Configuration not in updated context");
      /* Fill context bitset for possible returned value if updated */
      if (available_context_to_configuration_map.count(ctx_type) > 0)
        new_contexts.set(ctx_type);

      continue;
    }

    auto new_conf = FindFirstSupportedConfiguration(ctx_type);

    bool ctx_previously_not_supported =
        (available_context_to_configuration_map.count(ctx_type) == 0 ||
         available_context_to_configuration_map[ctx_type] == nullptr);
    /* Check if support for context type has changed */
    if (ctx_previously_not_supported) {
      /* Current configuration for context type is empty */
      if (new_conf == nullptr) {
        /* Configuration remains empty */
        continue;
      } else {
        /* Configuration changes from empty to some */
        new_contexts.set(ctx_type);
        active_contexts_has_been_modified = true;
      }
    } else {
      /* Current configuration for context type is not empty */
      if (new_conf == nullptr) {
        /* Configuration changed to empty */
        new_contexts.unset(ctx_type);
        active_contexts_has_been_modified = true;
      } else if (new_conf != available_context_to_configuration_map[ctx_type]) {
        /* Configuration changed to any other */
        new_contexts.set(ctx_type);
        active_contexts_has_been_modified = true;
      } else {
        /* Configuration is the same */
        new_contexts.set(ctx_type);
        continue;
      }
    }

    LOG_INFO(
        "updated context: %s, %s -> %s", ToHexString(ctx_type).c_str(),
        (ctx_previously_not_supported
             ? "empty"
             : available_context_to_configuration_map[ctx_type]->name.c_str()),
        (new_conf != nullptr ? new_conf->name.c_str() : "empty"));

    available_context_to_configuration_map[ctx_type] = new_conf;
  }

  /* Some contexts have changed, return new available context bitset */
  if (active_contexts_has_been_modified) {
    group_available_contexts_ = new_contexts;
  }

  return active_contexts_has_been_modified;
}

bool LeAudioDeviceGroup::ReloadAudioLocations(void) {
  AudioLocations updated_snk_audio_locations_ =
      codec_spec_conf::kLeAudioLocationNotAllowed;
  AudioLocations updated_src_audio_locations_ =
      codec_spec_conf::kLeAudioLocationNotAllowed;

  for (const auto& device : leAudioDevices_) {
    if (device.expired() || (device.lock().get()->GetConnectionState() !=
                             DeviceConnectState::CONNECTED))
      continue;
    updated_snk_audio_locations_ |= device.lock().get()->snk_audio_locations_;
    updated_src_audio_locations_ |= device.lock().get()->src_audio_locations_;
  }

  /* Nothing has changed */
  if ((updated_snk_audio_locations_ == snk_audio_locations_) &&
      (updated_src_audio_locations_ == src_audio_locations_))
    return false;

  snk_audio_locations_ = updated_snk_audio_locations_;
  src_audio_locations_ = updated_src_audio_locations_;

  return true;
}

bool LeAudioDeviceGroup::ReloadAudioDirections(void) {
  uint8_t updated_audio_directions = 0x00;

  for (const auto& device : leAudioDevices_) {
    if (device.expired() || (device.lock().get()->GetConnectionState() !=
                             DeviceConnectState::CONNECTED))
      continue;
    updated_audio_directions |= device.lock().get()->audio_directions_;
  }

  /* Nothing has changed */
  if (updated_audio_directions == audio_directions_) return false;

  audio_directions_ = updated_audio_directions;

  return true;
}

bool LeAudioDeviceGroup::IsInTransition(void) {
  return target_state_ != current_state_;
}

bool LeAudioDeviceGroup::IsReleasingOrIdle(void) {
  return (target_state_ == AseState::BTA_LE_AUDIO_ASE_STATE_IDLE) ||
         (current_state_ == AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
}

bool LeAudioDeviceGroup::IsGroupStreamReady(void) {
  auto iter =
      std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(), [](auto& d) {
        if (d.expired())
          return false;
        else
          return !(((d.lock()).get())->HaveAllActiveAsesCisEst());
      });

  return iter == leAudioDevices_.end();
}

bool LeAudioDeviceGroup::HaveAllCisesDisconnected(void) {
  for (auto const dev : leAudioDevices_) {
    if (dev.expired()) continue;
    if (dev.lock().get()->HaveAnyCisConnected()) return false;
  }
  return true;
}

uint8_t LeAudioDeviceGroup::GetFirstFreeCisId(void) {
  for (uint8_t id = 0; id < UINT8_MAX; id++) {
    auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                             [id](auto& d) {
                               if (d.expired())
                                 return false;
                               else
                                 return ((d.lock()).get())->HasCisId(id);
                             });

    if (iter == leAudioDevices_.end()) return id;
  }

  return kInvalidCisId;
}

uint8_t LeAudioDeviceGroup::GetFirstFreeCisId(CisType cis_type) {
  LOG_DEBUG("Group: %p, group_id: %d cis_type: %d", this, group_id_,
            static_cast<int>(cis_type));
  for (size_t id = 0; id < cises_.size(); id++) {
    if (cises_[id].addr.IsEmpty() && cises_[id].type == cis_type) {
      return id;
    }
  }
  return kInvalidCisId;
}

types::LeAudioConfigurationStrategy LeAudioDeviceGroup::GetGroupStrategy(void) {
  /* Simple strategy picker */
  LOG_INFO(" Group %d size %d", group_id_, Size());
  if (Size() > 1) {
    return types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE;
  }

  LOG_INFO("audio location 0x%04lx", snk_audio_locations_.to_ulong());
  if (!(snk_audio_locations_.to_ulong() &
        codec_spec_conf::kLeAudioLocationAnyLeft) ||
      !(snk_audio_locations_.to_ulong() &
        codec_spec_conf::kLeAudioLocationAnyRight)) {
    return types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE;
  }

  auto device = GetFirstDevice();
  auto channel_cnt =
      device->GetLc3SupportedChannelCount(types::kLeAudioDirectionSink);
  LOG_INFO("Channel count for group %d is %d (device %s)", group_id_,
           channel_cnt, device->address_.ToString().c_str());
  if (channel_cnt == 1) {
    return types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE;
  }

  return types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE;
}

int LeAudioDeviceGroup::GetAseCount(uint8_t direction) {
  int result = 0;
  for (const auto& device_iter : leAudioDevices_) {
    result += device_iter.lock()->GetAseCount(direction);
  }

  return result;
}

void LeAudioDeviceGroup::CigGenerateCisIds(
    types::LeAudioContextType context_type) {
  LOG_INFO("Group %p, group_id: %d, context_type: %s", this, group_id_,
           bluetooth::common::ToString(context_type).c_str());

  if (cises_.size() > 0) {
    LOG_INFO("CIS IDs already generated");
    return;
  }

  const set_configurations::AudioSetConfigurations* confs =
      AudioSetConfigurationProvider::Get()->GetConfigurations(context_type);

  uint8_t cis_count_bidir = 0;
  uint8_t cis_count_unidir_sink = 0;
  uint8_t cis_count_unidir_source = 0;
  int csis_group_size =
      bluetooth::csis::CsisClient::Get()->GetDesiredSize(group_id_);
  /* If this is CSIS group, the csis_group_size will be > 0, otherwise -1.
   * If the last happen it means, group size is 1 */
  int group_size = csis_group_size > 0 ? csis_group_size : 1;

  get_cis_count(*confs, group_size, GetGroupStrategy(),
                GetAseCount(types::kLeAudioDirectionSink),
                GetAseCount(types::kLeAudioDirectionSource), cis_count_bidir,
                cis_count_unidir_sink, cis_count_unidir_source);

  uint8_t idx = 0;
  while (cis_count_bidir > 0) {
    struct le_audio::types::cis cis_entry = {
        .id = idx,
        .addr = RawAddress::kEmpty,
        .type = CisType::CIS_TYPE_BIDIRECTIONAL,
        .conn_handle = 0,
    };
    cises_.push_back(cis_entry);
    cis_count_bidir--;
    idx++;
  }

  while (cis_count_unidir_sink > 0) {
    struct le_audio::types::cis cis_entry = {
        .id = idx,
        .addr = RawAddress::kEmpty,
        .type = CisType::CIS_TYPE_UNIDIRECTIONAL_SINK,
        .conn_handle = 0,
    };
    cises_.push_back(cis_entry);
    cis_count_unidir_sink--;
    idx++;
  }

  while (cis_count_unidir_source > 0) {
    struct le_audio::types::cis cis_entry = {
        .id = idx,
        .addr = RawAddress::kEmpty,
        .type = CisType::CIS_TYPE_UNIDIRECTIONAL_SOURCE,
        .conn_handle = 0,
    };
    cises_.push_back(cis_entry);
    cis_count_unidir_source--;
    idx++;
  }
}

bool LeAudioDeviceGroup::CigAssignCisIds(LeAudioDevice* leAudioDevice) {
  ASSERT_LOG(leAudioDevice, "invalid device");
  LOG_INFO("device: %s", leAudioDevice->address_.ToString().c_str());

  struct ase* ase = leAudioDevice->GetFirstActiveAse();
  if (!ase) {
    LOG_ERROR(" Device %s shouldn't be called without an active ASE",
              leAudioDevice->address_.ToString().c_str());
    return false;
  }

  for (; ase != nullptr; ase = leAudioDevice->GetNextActiveAse(ase)) {
    uint8_t cis_id = kInvalidCisId;
    /* CIS ID already set */
    if (ase->cis_id != kInvalidCisId) {
      LOG_INFO("ASE ID: %d, is already assigned CIS ID: %d, type %d", ase->id,
               ase->cis_id, cises_[ase->cis_id].type);
      if (!cises_[ase->cis_id].addr.IsEmpty()) {
        LOG_INFO("Bidirectional ASE already assigned");
        continue;
      }
      /* Reuse existing CIS ID if available*/
      cis_id = ase->cis_id;
    }

    /* First check if we have bidirectional ASEs. If so, assign same CIS ID.*/
    struct ase* matching_bidir_ase =
        leAudioDevice->GetNextActiveAseWithDifferentDirection(ase);

    if (matching_bidir_ase) {
      if (cis_id == kInvalidCisId) {
        cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_BIDIRECTIONAL);
      }

      if (cis_id != kInvalidCisId) {
        ase->cis_id = cis_id;
        matching_bidir_ase->cis_id = cis_id;
        cises_[cis_id].addr = leAudioDevice->address_;

        LOG_INFO(
            " ASE ID: %d and ASE ID: %d, assigned Bi-Directional CIS ID: %d",
            +ase->id, +matching_bidir_ase->id, +ase->cis_id);
        continue;
      }

      LOG_WARN(
          " ASE ID: %d, unable to get free Bi-Directional CIS ID but maybe "
          "thats fine. Try using unidirectional.",
          ase->id);
    }

    if (ase->direction == types::kLeAudioDirectionSink) {
      if (cis_id == kInvalidCisId) {
        cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_UNIDIRECTIONAL_SINK);
      }

      if (cis_id == kInvalidCisId) {
        LOG_WARN(
            " Unable to get free Uni-Directional Sink CIS ID - maybe there is "
            "bi-directional available");
        /* This could happen when scenarios for given context type allows for
         * Sink and Source configuration but also only Sink configuration.
         */
        cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_BIDIRECTIONAL);
        if (cis_id == kInvalidCisId) {
          LOG_ERROR("Unable to get free Uni-Directional Sink CIS ID");
          return false;
        }
      }

      ase->cis_id = cis_id;
      cises_[cis_id].addr = leAudioDevice->address_;
      LOG_INFO("ASE ID: %d, assigned Uni-Directional Sink CIS ID: %d", ase->id,
               ase->cis_id);
      continue;
    }

    /* Source direction */
    ASSERT_LOG(ase->direction == types::kLeAudioDirectionSource,
               "Expected Source direction, actual=%d", ase->direction);

    if (cis_id == kInvalidCisId) {
      cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_UNIDIRECTIONAL_SOURCE);
    }

    if (cis_id == kInvalidCisId) {
      /* This could happen when scenarios for given context type allows for
       * Sink and Source configuration but also only Sink configuration.
       */
      LOG_WARN(
          "Unable to get free Uni-Directional Source CIS ID - maybe there "
          "is bi-directional available");
      cis_id = GetFirstFreeCisId(CisType::CIS_TYPE_BIDIRECTIONAL);
      if (cis_id == kInvalidCisId) {
        LOG_ERROR("Unable to get free Uni-Directional Source CIS ID");
        return false;
      }
    }

    ase->cis_id = cis_id;
    cises_[cis_id].addr = leAudioDevice->address_;
    LOG_INFO("ASE ID: %d, assigned Uni-Directional Source CIS ID: %d", ase->id,
             ase->cis_id);
  }

  return true;
}

void LeAudioDeviceGroup::CigAssignCisConnHandles(
    const std::vector<uint16_t>& conn_handles) {
  LOG_INFO("num of cis handles %d", static_cast<int>(conn_handles.size()));
  for (size_t i = 0; i < cises_.size(); i++) {
    cises_[i].conn_handle = conn_handles[i];
    LOG_INFO("assigning cis[%d] conn_handle: %d", cises_[i].id,
             cises_[i].conn_handle);
  }
}

void LeAudioDeviceGroup::CigAssignCisConnHandlesToAses(
    LeAudioDevice* leAudioDevice) {
  ASSERT_LOG(leAudioDevice, "Invalid device");
  LOG_INFO("group: %p, group_id: %d, device: %s", this, group_id_,
           leAudioDevice->address_.ToString().c_str());

  /* Assign all CIS connection handles to ases */
  struct le_audio::types::ase* ase =
      leAudioDevice->GetFirstActiveAseByDataPathState(
          AudioStreamDataPathState::IDLE);
  if (!ase) {
    LOG_WARN("No active ASE with AudioStreamDataPathState IDLE");
    return;
  }

  for (; ase != nullptr; ase = leAudioDevice->GetFirstActiveAseByDataPathState(
                             AudioStreamDataPathState::IDLE)) {
    auto ases_pair = leAudioDevice->GetAsesByCisId(ase->cis_id);

    if (ases_pair.sink && ases_pair.sink->active) {
      ases_pair.sink->cis_conn_hdl = cises_[ase->cis_id].conn_handle;
      ases_pair.sink->data_path_state = AudioStreamDataPathState::CIS_ASSIGNED;
    }
    if (ases_pair.source && ases_pair.source->active) {
      ases_pair.source->cis_conn_hdl = cises_[ase->cis_id].conn_handle;
      ases_pair.source->data_path_state =
          AudioStreamDataPathState::CIS_ASSIGNED;
    }
  }
}

void LeAudioDeviceGroup::CigAssignCisConnHandlesToAses(void) {
  LeAudioDevice* leAudioDevice = GetFirstActiveDevice();
  ASSERT_LOG(leAudioDevice, "Shouldn't be called without an active device.");

  LOG_INFO("Group %p, group_id %d", this, group_id_);

  /* Assign all CIS connection handles to ases */
  for (; leAudioDevice != nullptr;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    CigAssignCisConnHandlesToAses(leAudioDevice);
  }
}

void LeAudioDeviceGroup::CigUnassignCis(LeAudioDevice* leAudioDevice) {
  ASSERT_LOG(leAudioDevice, "Invalid device");

  LOG_INFO("Group %p, group_id %d, device: %s", this, group_id_,
           leAudioDevice->address_.ToString().c_str());

  for (struct le_audio::types::cis& cis_entry : cises_) {
    if (cis_entry.addr == leAudioDevice->address_) {
      cis_entry.addr = RawAddress::kEmpty;
    }
  }
}

bool CheckIfStrategySupported(types::LeAudioConfigurationStrategy strategy,
                              types::AudioLocations audio_locations,
                              uint8_t requested_channel_count,
                              uint8_t channel_count_mask) {
  DLOG(INFO) << __func__ << " strategy: " << (int)strategy
             << " locations: " << +audio_locations.to_ulong();

  switch (strategy) {
    case types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE:
      return audio_locations.any();
    case types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE:
      if ((audio_locations.to_ulong() &
           codec_spec_conf::kLeAudioLocationAnyLeft) &&
          (audio_locations.to_ulong() &
           codec_spec_conf::kLeAudioLocationAnyRight))
        return true;
      else
        return false;
    case types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE:
      if (!(audio_locations.to_ulong() &
            codec_spec_conf::kLeAudioLocationAnyLeft) ||
          !(audio_locations.to_ulong() &
            codec_spec_conf::kLeAudioLocationAnyRight))
        return false;

      DLOG(INFO) << __func__ << " requested chan cnt "
                 << +requested_channel_count
                 << " chan mask: " << loghex(channel_count_mask);

      /* Return true if requested channel count is set in the channel count
       * mask. In the channel_count_mask, bit0 is set when 1 channel is
       * supported.
       */
      return ((1 << (requested_channel_count - 1)) & channel_count_mask);
    default:
      return false;
  }

  return false;
}

/* This method check if group support given audio configuration
 * requirement for connected devices in the group and available ASEs
 * (no matter on the ASE state) and for given context type
 */
bool LeAudioDeviceGroup::IsConfigurationSupported(
    const set_configurations::AudioSetConfiguration* audio_set_conf,
    types::LeAudioContextType context_type) {
  if (!set_configurations::check_if_may_cover_scenario(
          audio_set_conf, NumOfConnected(context_type))) {
    LOG_DEBUG(" cannot cover scenario  %s: size of for context type %d",
              bluetooth::common::ToString(context_type).c_str(),
              +NumOfConnected(context_type));
    return false;
  }

  auto required_snk_strategy = GetGroupStrategy();

  /* TODO For now: set ase if matching with first pac.
   * 1) We assume as well that devices will match requirements in order
   *    e.g. 1 Device - 1 Requirement, 2 Device - 2 Requirement etc.
   * 2) ASEs should be active only if best (according to priority list) full
   *    scenarion will be covered.
   * 3) ASEs should be filled according to performance profile.
   */
  for (const auto& ent : (*audio_set_conf).confs) {
    LOG_DEBUG(" Looking for configuration: %s - %s",
              audio_set_conf->name.c_str(),
              (ent.direction == types::kLeAudioDirectionSink ? "snk" : "src"));

    uint8_t required_device_cnt = ent.device_cnt;
    uint8_t max_required_ase_per_dev =
        ent.ase_cnt / ent.device_cnt + (ent.ase_cnt % ent.device_cnt);
    uint8_t active_ase_num = 0;
    auto strategy = ent.strategy;

    LOG_DEBUG(
        " Number of devices: %d, number of ASEs: %d,  Max ASE per device: %d "
        "strategy: %d",
        +required_device_cnt, +ent.ase_cnt, +max_required_ase_per_dev,
        static_cast<int>(strategy));

    if (ent.direction == types::kLeAudioDirectionSink &&
        strategy != required_snk_strategy) {
      LOG_INFO(" Sink strategy mismatch group!=cfg.entry (%d!=%d)",
               static_cast<int>(required_snk_strategy),
               static_cast<int>(strategy));
      return false;
    }

    for (auto* device = GetFirstDeviceWithActiveContext(context_type);
         device != nullptr && required_device_cnt > 0;
         device = GetNextDeviceWithActiveContext(device, context_type)) {
      /* Skip if device has ASE configured in this direction already */

      if (device->ases_.empty()) continue;

      if (!device->GetCodecConfigurationSupportedPac(ent.direction, ent.codec))
        continue;

      int needed_ase = std::min(static_cast<int>(max_required_ase_per_dev),
                                static_cast<int>(ent.ase_cnt - active_ase_num));

      /* If we required more ASEs per device which means we would like to
       * create more CISes to one device, we should also check the allocation
       * if it allows us to do this.
       */

      types::AudioLocations audio_locations = 0;
      /* Check direction and if audio location allows to create more cise */
      if (ent.direction == types::kLeAudioDirectionSink)
        audio_locations = device->snk_audio_locations_;
      else
        audio_locations = device->src_audio_locations_;

      /* TODO Make it no Lc3 specific */
      if (!CheckIfStrategySupported(
              strategy, audio_locations,
              std::get<LeAudioLc3Config>(ent.codec.config).GetChannelCount(),
              device->GetLc3SupportedChannelCount(ent.direction))) {
        LOG_DEBUG(" insufficient device audio allocation: %lu",
                  audio_locations.to_ulong());
        continue;
      }

      for (auto& ase : device->ases_) {
        if (ase.direction != ent.direction) continue;

        active_ase_num++;
        needed_ase--;

        if (needed_ase == 0) break;
      }

      if (needed_ase > 0) {
        LOG_DEBUG("Device has too less ASEs. Still needed ases %d", needed_ase);
        return false;
      }

      required_device_cnt--;
    }

    if (required_device_cnt > 0) {
      /* Don't left any active devices if requirements are not met */
      LOG_DEBUG(" could not configure all the devices");
      return false;
    }
  }

  LOG_DEBUG("Chosen ASE Configuration for group: %d, configuration: %s",
            this->group_id_, audio_set_conf->name.c_str());
  return true;
}

static uint32_t GetFirstLeft(const types::AudioLocations& audio_locations) {
  uint32_t audio_location_ulong = audio_locations.to_ulong();

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontLeft)
    return codec_spec_conf::kLeAudioLocationFrontLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBackLeft)
    return codec_spec_conf::kLeAudioLocationBackLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontLeftOfCenter)
    return codec_spec_conf::kLeAudioLocationFrontLeftOfCenter;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationSideLeft)
    return codec_spec_conf::kLeAudioLocationSideLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopFrontLeft)
    return codec_spec_conf::kLeAudioLocationTopFrontLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopBackLeft)
    return codec_spec_conf::kLeAudioLocationTopBackLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopSideLeft)
    return codec_spec_conf::kLeAudioLocationTopSideLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBottomFrontLeft)
    return codec_spec_conf::kLeAudioLocationBottomFrontLeft;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontLeftWide)
    return codec_spec_conf::kLeAudioLocationFrontLeftWide;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationLeftSurround)
    return codec_spec_conf::kLeAudioLocationLeftSurround;

  return 0;
}

static uint32_t GetFirstRight(const types::AudioLocations& audio_locations) {
  uint32_t audio_location_ulong = audio_locations.to_ulong();

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontRight)
    return codec_spec_conf::kLeAudioLocationFrontRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBackRight)
    return codec_spec_conf::kLeAudioLocationBackRight;

  if (audio_location_ulong &
      codec_spec_conf::kLeAudioLocationFrontRightOfCenter)
    return codec_spec_conf::kLeAudioLocationFrontRightOfCenter;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationSideRight)
    return codec_spec_conf::kLeAudioLocationSideRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopFrontRight)
    return codec_spec_conf::kLeAudioLocationTopFrontRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopBackRight)
    return codec_spec_conf::kLeAudioLocationTopBackRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationTopSideRight)
    return codec_spec_conf::kLeAudioLocationTopSideRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationBottomFrontRight)
    return codec_spec_conf::kLeAudioLocationBottomFrontRight;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationFrontRightWide)
    return codec_spec_conf::kLeAudioLocationFrontRightWide;

  if (audio_location_ulong & codec_spec_conf::kLeAudioLocationRightSurround)
    return codec_spec_conf::kLeAudioLocationRightSurround;

  return 0;
}

uint32_t PickAudioLocation(types::LeAudioConfigurationStrategy strategy,
                           types::AudioLocations device_locations,
                           types::AudioLocations* group_locations) {
  LOG_DEBUG("strategy: %d, locations: 0x%lx, group locations: 0x%lx",
            (int)strategy, device_locations.to_ulong(),
            group_locations->to_ulong());

  auto is_left_not_yet_assigned =
      !(group_locations->to_ulong() & codec_spec_conf::kLeAudioLocationAnyLeft);
  auto is_right_not_yet_assigned = !(group_locations->to_ulong() &
                                     codec_spec_conf::kLeAudioLocationAnyRight);
  uint32_t left_device_loc = GetFirstLeft(device_locations);
  uint32_t right_device_loc = GetFirstRight(device_locations);

  if (left_device_loc == 0 && right_device_loc == 0) {
    LOG_WARN("Can't find device able to render left  and right audio channel");
  }

  switch (strategy) {
    case types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE:
    case types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE:
      if (left_device_loc && is_left_not_yet_assigned) {
        *group_locations |= left_device_loc;
        return left_device_loc;
      }

      if (right_device_loc && is_right_not_yet_assigned) {
        *group_locations |= right_device_loc;
        return right_device_loc;
      }
      break;

    case types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE:
      if (left_device_loc && right_device_loc) {
        *group_locations |= left_device_loc | right_device_loc;
        return left_device_loc | right_device_loc;
      }
      break;
    default:
      LOG_ALWAYS_FATAL("%s: Unknown strategy: %hhu", __func__, strategy);
      return 0;
  }

  LOG_ERROR(
      "Can't find device for left/right channel. Strategy: %hhu, "
      "device_locations: %lx, group_locations: %lx.",
      strategy, device_locations.to_ulong(), group_locations->to_ulong());

  /* Return either any left or any right audio location. It might result with
   * multiple devices within the group having the same location.
   */
  return left_device_loc ? left_device_loc : right_device_loc;
}

bool LeAudioDevice::ConfigureAses(
    const le_audio::set_configurations::SetConfiguration& ent,
    types::LeAudioContextType context_type,
    uint8_t* number_of_already_active_group_ase,
    types::AudioLocations& group_snk_audio_locations,
    types::AudioLocations& group_src_audio_locations, bool reuse_cis_id,
    AudioContexts metadata_context_type,
    const std::vector<uint8_t>& ccid_list) {
  /* First try to use the already configured ASE */
  auto ase = GetFirstActiveAseByDirection(ent.direction);
  if (ase) {
    LOG_INFO("Using an already active ASE id=%d", ase->id);
  } else {
    ase = GetFirstInactiveAse(ent.direction, reuse_cis_id);
  }

  if (!ase) {
    LOG_ERROR("Unable to find an ASE to configure");
    return false;
  }

  /* The number_of_already_active_group_ase keeps all the active ases
   * in other devices in the group.
   * This function counts active ases only for this device, and we count here
   * new active ases and already active ases which we want to reuse in the
   * scenario
   */
  uint8_t active_ases = *number_of_already_active_group_ase;
  uint8_t max_required_ase_per_dev =
      ent.ase_cnt / ent.device_cnt + (ent.ase_cnt % ent.device_cnt);
  le_audio::types::LeAudioConfigurationStrategy strategy = ent.strategy;

  auto pac = GetCodecConfigurationSupportedPac(ent.direction, ent.codec);
  if (!pac) return false;

  int needed_ase = std::min((int)(max_required_ase_per_dev),
                            (int)(ent.ase_cnt - active_ases));

  types::AudioLocations audio_locations = 0;
  types::AudioLocations* group_audio_locations;
  /* Check direction and if audio location allows to create more cise */
  if (ent.direction == types::kLeAudioDirectionSink) {
    audio_locations = snk_audio_locations_;
    group_audio_locations = &group_snk_audio_locations;
  } else {
    audio_locations = src_audio_locations_;
    group_audio_locations = &group_src_audio_locations;
  }

  for (; needed_ase && ase; needed_ase--) {
    ase->active = true;
    ase->configured_for_context_type = context_type;
    active_ases++;

    /* In case of late connect, we could be here for STREAMING ase.
     * in such case, it is needed to mark ase as known active ase which
     * is important to validate scenario and is done already few lines above.
     * Nothing more to do is needed here.
     */
    if (ase->state != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
      if (ase->state == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED)
        ase->reconfigure = true;

      ase->target_latency = ent.target_latency;
      ase->codec_id = ent.codec.id;
      /* TODO: find better way to not use LC3 explicitly */
      ase->codec_config = std::get<LeAudioLc3Config>(ent.codec.config);

      /*Let's choose audio channel allocation if not set */
      ase->codec_config.audio_channel_allocation =
          PickAudioLocation(strategy, audio_locations, group_audio_locations);

      /* Get default value if no requirement for specific frame blocks per sdu
       */
      if (!ase->codec_config.codec_frames_blocks_per_sdu) {
        ase->codec_config.codec_frames_blocks_per_sdu =
            GetMaxCodecFramesPerSduFromPac(pac);
      }
      ase->max_sdu_size = codec_spec_caps::GetAudioChannelCounts(
                              *ase->codec_config.audio_channel_allocation) *
                          *ase->codec_config.octets_per_codec_frame *
                          *ase->codec_config.codec_frames_blocks_per_sdu;

      ase->retrans_nb = ent.qos.retransmission_number;
      ase->max_transport_latency = ent.qos.max_transport_latency;

      /* Filter multidirectional audio context for each ase direction */
      auto directional_audio_context =
          metadata_context_type & GetAvailableContexts(ase->direction);
      if (directional_audio_context.any()) {
        ase->metadata = GetMetadata(directional_audio_context, ccid_list);
      } else {
        ase->metadata =
            GetMetadata(AudioContexts(LeAudioContextType::UNSPECIFIED),
                        std::vector<uint8_t>());
      }
    }

    LOG_DEBUG(
        "device=%s, activated ASE id=%d, direction=%s, max_sdu_size=%d, "
        "cis_id=%d, target_latency=%d",
        address_.ToString().c_str(), ase->id,
        (ent.direction == 1 ? "snk" : "src"), ase->max_sdu_size, ase->cis_id,
        ent.target_latency);

    /* Try to use the already active ASE */
    ase = GetNextActiveAseWithSameDirection(ase);
    if (ase == nullptr) {
      ase = GetFirstInactiveAse(ent.direction, reuse_cis_id);
    }
  }

  *number_of_already_active_group_ase = active_ases;
  return true;
}

/* This method should choose aproperiate ASEs to be active and set a cached
 * configuration for codec and qos.
 */
bool LeAudioDeviceGroup::ConfigureAses(
    const set_configurations::AudioSetConfiguration* audio_set_conf,
    types::LeAudioContextType context_type, AudioContexts metadata_context_type,
    const std::vector<uint8_t>& ccid_list) {
  if (!set_configurations::check_if_may_cover_scenario(
          audio_set_conf, NumOfConnected(context_type)))
    return false;

  bool reuse_cis_id =
      GetState() == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED;

  /* TODO For now: set ase if matching with first pac.
   * 1) We assume as well that devices will match requirements in order
   *    e.g. 1 Device - 1 Requirement, 2 Device - 2 Requirement etc.
   * 2) ASEs should be active only if best (according to priority list) full
   *    scenarion will be covered.
   * 3) ASEs should be filled according to performance profile.
   */

  types::AudioLocations group_snk_audio_locations = 0;
  types::AudioLocations group_src_audio_locations = 0;

  for (const auto& ent : (*audio_set_conf).confs) {
    LOG_DEBUG(" Looking for requirements: %s,  - %s",
              audio_set_conf->name.c_str(),
              (ent.direction == 1 ? "snk" : "src"));

    uint8_t required_device_cnt = ent.device_cnt;
    uint8_t max_required_ase_per_dev =
        ent.ase_cnt / ent.device_cnt + (ent.ase_cnt % ent.device_cnt);
    uint8_t active_ase_num = 0;
    le_audio::types::LeAudioConfigurationStrategy strategy = ent.strategy;

    LOG_DEBUG(
        "Number of devices: %d number of ASEs: %d, Max ASE per device: %d "
        "strategy: %d",
        required_device_cnt, ent.ase_cnt, max_required_ase_per_dev,
        (int)strategy);

    for (auto* device = GetFirstDeviceWithActiveContext(context_type);
         device != nullptr && required_device_cnt > 0;
         device = GetNextDeviceWithActiveContext(device, context_type)) {
      /* For the moment, we configure only connected devices and when it is
       * ready to stream i.e. All ASEs are discovered and device is reported as
       * connected
       */
      if (device->GetConnectionState() != DeviceConnectState::CONNECTED) {
        LOG_WARN(
            "Device %s, in the state %s", device->address_.ToString().c_str(),
            bluetooth::common::ToString(device->GetConnectionState()).c_str());
        continue;
      }

      if (!device->ConfigureAses(ent, context_type, &active_ase_num,
                                 group_snk_audio_locations,
                                 group_src_audio_locations, reuse_cis_id,
                                 metadata_context_type, ccid_list))
        continue;

      required_device_cnt--;
    }

    if (required_device_cnt > 0) {
      /* Don't left any active devices if requirements are not met */
      LOG_ERROR(" could not configure all the devices");
      Deactivate();
      return false;
    }
  }

  LOG_INFO("Choosed ASE Configuration for group: %d, configuration: %s",
           group_id_, audio_set_conf->name.c_str());

  configuration_context_type_ = context_type;
  metadata_context_type_ = metadata_context_type;
  return true;
}

const set_configurations::AudioSetConfiguration*
LeAudioDeviceGroup::GetActiveConfiguration(void) {
  return available_context_to_configuration_map[configuration_context_type_];
}

std::optional<LeAudioCodecConfiguration>
LeAudioDeviceGroup::GetCodecConfigurationByDirection(
    types::LeAudioContextType group_context_type, uint8_t direction) const {
  if (available_context_to_configuration_map.count(group_context_type) == 0) {
    LOG_DEBUG("Context type %s, not supported",
              bluetooth::common::ToString(group_context_type).c_str());
    return std::nullopt;
  }

  const set_configurations::AudioSetConfiguration* audio_set_conf =
      available_context_to_configuration_map.at(group_context_type);
  LeAudioCodecConfiguration group_config = {0, 0, 0, 0};
  if (!audio_set_conf) return std::nullopt;

  for (const auto& conf : audio_set_conf->confs) {
    if (conf.direction != direction) continue;

    if (group_config.sample_rate != 0 &&
        conf.codec.GetConfigSamplingFrequency() != group_config.sample_rate) {
      LOG(WARNING) << __func__
                   << ", stream configuration could not be "
                      "determined (sampling frequency differs) for direction: "
                   << loghex(direction);
      return std::nullopt;
    }
    group_config.sample_rate = conf.codec.GetConfigSamplingFrequency();

    if (group_config.data_interval_us != 0 &&
        conf.codec.GetConfigDataIntervalUs() != group_config.data_interval_us) {
      LOG(WARNING) << __func__
                   << ", stream configuration could not be "
                      "determined (data interval differs) for direction: "
                   << loghex(direction);
      return std::nullopt;
    }
    group_config.data_interval_us = conf.codec.GetConfigDataIntervalUs();

    if (group_config.bits_per_sample != 0 &&
        conf.codec.GetConfigBitsPerSample() != group_config.bits_per_sample) {
      LOG(WARNING) << __func__
                   << ", stream configuration could not be "
                      "determined (bits per sample differs) for direction: "
                   << loghex(direction);
      return std::nullopt;
    }
    group_config.bits_per_sample = conf.codec.GetConfigBitsPerSample();

    group_config.num_channels +=
        conf.codec.GetConfigChannelCount() * conf.device_cnt;
  }

  if (group_config.IsInvalid()) return std::nullopt;

  return group_config;
}

bool LeAudioDeviceGroup::IsContextSupported(
    types::LeAudioContextType group_context_type) {
  auto iter = available_context_to_configuration_map.find(group_context_type);
  if (iter == available_context_to_configuration_map.end()) return false;

  return available_context_to_configuration_map[group_context_type] != nullptr;
}

bool LeAudioDeviceGroup::IsMetadataChanged(
    types::AudioContexts context_type, const std::vector<uint8_t>& ccid_list) {
  for (auto* leAudioDevice = GetFirstActiveDevice(); leAudioDevice;
       leAudioDevice = GetNextActiveDevice(leAudioDevice)) {
    if (leAudioDevice->IsMetadataChanged(context_type, ccid_list)) return true;
  }

  return false;
}

void LeAudioDeviceGroup::StreamOffloaderUpdated(uint8_t direction) {
  if (direction == le_audio::types::kLeAudioDirectionSource) {
    stream_conf.source_is_initial = false;
  } else {
    stream_conf.sink_is_initial = false;
  }
}

void LeAudioDeviceGroup::CreateStreamVectorForOffloader(uint8_t direction) {
  if (CodecManager::GetInstance()->GetCodecLocation() !=
      le_audio::types::CodecLocation::ADSP) {
    return;
  }

  CisType cis_type;
  std::vector<std::pair<uint16_t, uint32_t>>* streams;
  std::vector<std::pair<uint16_t, uint32_t>>*
      offloader_streams_target_allocation;
  std::vector<std::pair<uint16_t, uint32_t>>*
      offloader_streams_current_allocation;
  std::string tag;
  uint32_t available_allocations = 0;
  bool* changed_flag;
  bool* is_initial;
  if (direction == le_audio::types::kLeAudioDirectionSource) {
    changed_flag = &stream_conf.source_offloader_changed;
    is_initial = &stream_conf.source_is_initial;
    cis_type = CisType::CIS_TYPE_UNIDIRECTIONAL_SOURCE;
    streams = &stream_conf.source_streams;
    offloader_streams_target_allocation =
        &stream_conf.source_offloader_streams_target_allocation;
    offloader_streams_current_allocation =
        &stream_conf.source_offloader_streams_current_allocation;
    tag = "Source";
    available_allocations = AdjustAllocationForOffloader(
        stream_conf.source_audio_channel_allocation);
  } else {
    changed_flag = &stream_conf.sink_offloader_changed;
    is_initial = &stream_conf.sink_is_initial;
    cis_type = CisType::CIS_TYPE_UNIDIRECTIONAL_SINK;
    streams = &stream_conf.sink_streams;
    offloader_streams_target_allocation =
        &stream_conf.sink_offloader_streams_target_allocation;
    offloader_streams_current_allocation =
        &stream_conf.sink_offloader_streams_current_allocation;
    tag = "Sink";
    available_allocations =
        AdjustAllocationForOffloader(stream_conf.sink_audio_channel_allocation);
  }

  if (available_allocations == 0) {
    LOG_ERROR("There is no CIS connected");
    return;
  }

  if (offloader_streams_target_allocation->size() == 0) {
    *is_initial = true;
  } else if (*is_initial) {
    // As multiple CISes phone call case, the target_allocation already have the
    // previous data, but the is_initial flag not be cleared. We need to clear
    // here to avoid make duplicated target allocation stream map.
    offloader_streams_target_allocation->clear();
  }

  offloader_streams_current_allocation->clear();
  *changed_flag = true;
  bool not_all_cises_connected = false;
  if (available_allocations != codec_spec_conf::kLeAudioLocationStereo) {
    not_all_cises_connected = true;
  }

  /* If the all cises are connected as stream started, reset changed_flag that
   * the bt stack wouldn't send another audio configuration for the connection
   * status */
  if (*is_initial && !not_all_cises_connected) {
    *changed_flag = false;
  }

  /* Note: For the offloader case we simplify allocation to only Left and Right.
   * If we need 2 CISes and only one is connected, the connected one will have
   * allocation set to stereo (left | right) and other one will have allocation
   * set to 0. Offloader in this case shall mix left and right and send it on
   * connected CIS. If there is only single CIS with stereo allocation, it means
   * that peer device support channel count 2 and offloader shall send two
   * channels in the single CIS.
   */

  for (auto& cis_entry : cises_) {
    if ((cis_entry.type == CisType::CIS_TYPE_BIDIRECTIONAL ||
         cis_entry.type == cis_type) &&
        cis_entry.conn_handle != 0) {
      uint32_t target_allocation = 0;
      uint32_t current_allocation = 0;
      for (const auto& s : *streams) {
        if (s.first == cis_entry.conn_handle) {
          target_allocation = AdjustAllocationForOffloader(s.second);
          current_allocation = target_allocation;
          if (not_all_cises_connected) {
            /* Tell offloader to mix on this CIS.*/
            current_allocation = codec_spec_conf::kLeAudioLocationStereo;
          }
          break;
        }
      }

      if (target_allocation == 0) {
        /* Take missing allocation for that one .*/
        target_allocation =
            codec_spec_conf::kLeAudioLocationStereo & ~available_allocations;
      }

      LOG_INFO(
          "%s: Cis handle 0x%04x, target allocation  0x%08x, current "
          "allocation 0x%08x",
          tag.c_str(), cis_entry.conn_handle, target_allocation,
          current_allocation);
      if (*is_initial) {
        offloader_streams_target_allocation->emplace_back(
            std::make_pair(cis_entry.conn_handle, target_allocation));
      }
      offloader_streams_current_allocation->emplace_back(
          std::make_pair(cis_entry.conn_handle, current_allocation));
    }
  }
}

bool LeAudioDeviceGroup::IsPendingConfiguration(void) {
  return stream_conf.pending_configuration;
}

void LeAudioDeviceGroup::SetPendingConfiguration(void) {
  stream_conf.pending_configuration = true;
}

void LeAudioDeviceGroup::ClearPendingConfiguration(void) {
  stream_conf.pending_configuration = false;
}

bool LeAudioDeviceGroup::IsConfigurationSupported(
    LeAudioDevice* leAudioDevice,
    const set_configurations::AudioSetConfiguration* audio_set_conf) {
  for (const auto& ent : (*audio_set_conf).confs) {
    LOG_INFO("Looking for requirements: %s - %s", audio_set_conf->name.c_str(),
             (ent.direction == 1 ? "snk" : "src"));
    auto pac = leAudioDevice->GetCodecConfigurationSupportedPac(ent.direction,
                                                                ent.codec);
    if (pac != nullptr) {
      LOG_INFO("Configuration is supported by device %s",
               leAudioDevice->address_.ToString().c_str());
      return true;
    }
  }

  LOG_INFO("Configuration is NOT supported by device %s",
           leAudioDevice->address_.ToString().c_str());
  return false;
}

const set_configurations::AudioSetConfiguration*
LeAudioDeviceGroup::FindFirstSupportedConfiguration(
    LeAudioContextType context_type) {
  const set_configurations::AudioSetConfigurations* confs =
      AudioSetConfigurationProvider::Get()->GetConfigurations(context_type);

  LOG_DEBUG("context type: %s,  number of connected devices: %d",
            bluetooth::common::ToString(context_type).c_str(),
            +NumOfConnected());

  /* Filter out device set for all scenarios */
  if (!set_configurations::check_if_may_cover_scenario(confs,
                                                       NumOfConnected())) {
    LOG_ERROR(", group is unable to cover scenario");
    return nullptr;
  }

  /* Filter out device set for each end every scenario */

  for (const auto& conf : *confs) {
    if (IsConfigurationSupported(conf, context_type)) {
      LOG_DEBUG("found: %s", conf->name.c_str());
      return conf;
    }
  }

  return nullptr;
}

/* This method should choose aproperiate ASEs to be active and set a cached
 * configuration for codec and qos.
 */
bool LeAudioDeviceGroup::Configure(LeAudioContextType context_type,
                                   AudioContexts metadata_context_type,
                                   std::vector<uint8_t> ccid_list) {
  const set_configurations::AudioSetConfiguration* conf =
      available_context_to_configuration_map[context_type];

  if (!conf) {
    LOG_ERROR(
        ", requested context type: %s , is in mismatch with cached available "
        "contexts ",
        bluetooth::common::ToString(context_type).c_str());
    return false;
  }

  LOG_DEBUG(" setting context type: %s",
            bluetooth::common::ToString(context_type).c_str());

  if (!ConfigureAses(conf, context_type, metadata_context_type, ccid_list)) {
    LOG_ERROR(
        ", requested context type: %s , is in mismatch with cached available "
        "contexts",
        bluetooth::common::ToString(context_type).c_str());
    return false;
  }

  /* Store selected configuration at once it is chosen.
   * It might happen it will get unavailable in some point of time
   */
  stream_conf.conf = conf;
  return true;
}

LeAudioDeviceGroup::~LeAudioDeviceGroup(void) { this->Cleanup(); }

void LeAudioDeviceGroup::PrintDebugState(void) {
  auto* active_conf = GetActiveConfiguration();
  std::stringstream debug_str;

  debug_str << "\n Groupd id: " << group_id_
            << ", state: " << bluetooth::common::ToString(GetState())
            << ", target state: "
            << bluetooth::common::ToString(GetTargetState())
            << ", cig state: " << bluetooth::common::ToString(cig_state_)
            << ", \n group available contexts: "
            << bluetooth::common::ToString(GetAvailableContexts())
            << ", \n configuration context type: "
            << bluetooth::common::ToString(GetConfigurationContextType())
            << ", \n active configuration name: "
            << (active_conf ? active_conf->name : " not set");

  if (cises_.size() > 0) {
    LOG_INFO("\n Allocated CISes: %d", static_cast<int>(cises_.size()));
    for (auto cis : cises_) {
      LOG_INFO("\n cis id: %d, type: %d, conn_handle %d, addr: %s", cis.id,
               cis.type, cis.conn_handle, cis.addr.ToString().c_str());
    }
  }

  if (GetFirstActiveDevice() != nullptr) {
    uint32_t sink_delay = 0;
    uint32_t source_delay = 0;
    GetPresentationDelay(&sink_delay, le_audio::types::kLeAudioDirectionSink);
    GetPresentationDelay(&source_delay,
                         le_audio::types::kLeAudioDirectionSource);
    auto phy_mtos = GetPhyBitmask(le_audio::types::kLeAudioDirectionSink);
    auto phy_stom = GetPhyBitmask(le_audio::types::kLeAudioDirectionSource);
    auto max_transport_latency_mtos = GetMaxTransportLatencyMtos();
    auto max_transport_latency_stom = GetMaxTransportLatencyStom();
    auto sdu_mts = GetSduInterval(le_audio::types::kLeAudioDirectionSink);
    auto sdu_stom = GetSduInterval(le_audio::types::kLeAudioDirectionSource);

    debug_str << "\n resentation_delay for sink (speaker): " << +sink_delay
              << " us, presentation_delay for source (microphone): "
              << +source_delay << "us, \n MtoS transport latency:  "
              << +max_transport_latency_mtos
              << ", StoM transport latency: " << +max_transport_latency_stom
              << ", \n MtoS Phy: " << loghex(phy_mtos)
              << ", MtoS sdu: " << loghex(phy_stom)
              << " \n MtoS sdu: " << +sdu_mts << ", StoM sdu: " << +sdu_stom;
  }

  LOG_INFO("%s", debug_str.str().c_str());

  for (const auto& device_iter : leAudioDevices_) {
    device_iter.lock()->PrintDebugState();
  }
}

void LeAudioDeviceGroup::Dump(int fd, int active_group_id) {
  bool is_active = (group_id_ == active_group_id);
  std::stringstream stream;
  auto* active_conf = GetActiveConfiguration();

  stream << "\n    == Group id: " << group_id_
         << " == " << (is_active ? ",\tActive\n" : ",\tInactive\n")
         << "      state: " << GetState()
         << ",\ttarget state: " << GetTargetState()
         << ",\tcig state: " << cig_state_ << "\n"
         << "      group available contexts: " << GetAvailableContexts()
         << "      configuration context type: "
         << bluetooth::common::ToString(GetConfigurationContextType()).c_str()
         << "      active configuration name: "
         << (active_conf ? active_conf->name : " not set") << "\n"
         << "      stream configuration: "
         << (stream_conf.conf != nullptr ? stream_conf.conf->name : " unknown ")
         << "\n"
         << "      codec id: " << +(stream_conf.id.coding_format)
         << ",\tpending_configuration: " << stream_conf.pending_configuration
         << "\n"
         << "      num of devices(connected): " << Size() << "("
         << NumOfConnected() << ")\n"
         << ",     num of sinks(connected): " << stream_conf.sink_num_of_devices
         << "(" << stream_conf.sink_streams.size() << ")\n"
         << "      num of sources(connected): "
         << stream_conf.source_num_of_devices << "("
         << stream_conf.source_streams.size() << ")\n"
         << "      allocated CISes: " << static_cast<int>(cises_.size());

  if (cises_.size() > 0) {
    stream << "\n\t == CISes == ";
    for (auto cis : cises_) {
      stream << "\n\t cis id: " << static_cast<int>(cis.id)
             << ",\ttype: " << static_cast<int>(cis.type)
             << ",\tconn_handle: " << static_cast<int>(cis.conn_handle)
             << ",\taddr: " << cis.addr;
    }
    stream << "\n\t ====";
  }

  if (GetFirstActiveDevice() != nullptr) {
    uint32_t sink_delay;
    if (GetPresentationDelay(&sink_delay,
                             le_audio::types::kLeAudioDirectionSink)) {
      stream << "\n      presentation_delay for sink (speaker): " << sink_delay
             << " us";
    }

    uint32_t source_delay;
    if (GetPresentationDelay(&source_delay,
                             le_audio::types::kLeAudioDirectionSource)) {
      stream << "\n      presentation_delay for source (microphone): "
             << source_delay << " us";
    }
  }

  stream << "\n      == devices: ==";

  dprintf(fd, "%s", stream.str().c_str());

  for (const auto& device_iter : leAudioDevices_) {
    device_iter.lock()->Dump(fd);
  }
}

/* LeAudioDevice Class methods implementation */
void LeAudioDevice::SetConnectionState(DeviceConnectState state) {
  LOG_DEBUG(" %s --> %s",
            bluetooth::common::ToString(connection_state_).c_str(),
            bluetooth::common::ToString(state).c_str());
  connection_state_ = state;
}

DeviceConnectState LeAudioDevice::GetConnectionState(void) {
  return connection_state_;
}

void LeAudioDevice::ClearPACs(void) {
  snk_pacs_.clear();
  src_pacs_.clear();
}

LeAudioDevice::~LeAudioDevice(void) {
  alarm_free(link_quality_timer);
  this->ClearPACs();
}

void LeAudioDevice::RegisterPACs(
    std::vector<struct types::acs_ac_record>* pac_db,
    std::vector<struct types::acs_ac_record>* pac_recs) {
  /* Clear PAC database for characteristic in case if re-read, indicated */
  if (!pac_db->empty()) {
    DLOG(INFO) << __func__ << ", upgrade PACs for characteristic";
    pac_db->clear();
  }

  /* TODO wrap this logging part with debug flag */
  for (const struct types::acs_ac_record& pac : *pac_recs) {
    LOG(INFO) << "Registering PAC"
              << "\n\tCoding format: " << loghex(pac.codec_id.coding_format)
              << "\n\tVendor codec company ID: "
              << loghex(pac.codec_id.vendor_company_id)
              << "\n\tVendor codec ID: " << loghex(pac.codec_id.vendor_codec_id)
              << "\n\tCodec spec caps:\n"
              << pac.codec_spec_caps.ToString() << "\n\tMetadata: "
              << base::HexEncode(pac.metadata.data(), pac.metadata.size());
  }

  pac_db->insert(pac_db->begin(), pac_recs->begin(), pac_recs->end());
}

struct ase* LeAudioDevice::GetAseByValHandle(uint16_t val_hdl) {
  auto iter = std::find_if(
      ases_.begin(), ases_.end(),
      [&val_hdl](const auto& ase) { return ase.hdls.val_hdl == val_hdl; });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

int LeAudioDevice::GetAseCount(uint8_t direction) {
  return std::count_if(ases_.begin(), ases_.end(), [direction](const auto& a) {
    return a.direction == direction;
  });
}

struct ase* LeAudioDevice::GetFirstAseWithState(uint8_t direction,
                                                AseState state) {
  auto iter = std::find_if(
      ases_.begin(), ases_.end(), [direction, state](const auto& ase) {
        return ((ase.direction == direction) && (ase.state == state));
      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetFirstActiveAse(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [](const auto& ase) { return ase.active; });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetFirstActiveAseByDirection(uint8_t direction) {
  auto iter =
      std::find_if(ases_.begin(), ases_.end(), [direction](const auto& ase) {
        return (ase.active && (ase.direction == direction));
      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetNextActiveAseWithSameDirection(
    struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [&base_ase](auto& ase) { return base_ase == &ase; });

  /* Invalid ase given */
  if (iter == ases_.end() || std::distance(iter, ases_.end()) < 1)
    return nullptr;

  iter =
      std::find_if(std::next(iter, 1), ases_.end(), [&iter](const auto& ase) {
        return ase.active && (*iter).direction == ase.direction;
      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetNextActiveAseWithDifferentDirection(
    struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [&base_ase](auto& ase) { return base_ase == &ase; });

  /* Invalid ase given */
  if (std::distance(iter, ases_.end()) < 1) {
    LOG_DEBUG("ASE %d does not use bidirectional CIS", base_ase->id);
    return nullptr;
  }

  iter =
      std::find_if(std::next(iter, 1), ases_.end(), [&iter](const auto& ase) {
        return ase.active && iter->direction != ase.direction;
      });

  if (iter == ases_.end()) {
    return nullptr;
  }

  return &(*iter);
}

struct ase* LeAudioDevice::GetFirstActiveAseByDataPathState(
    types::AudioStreamDataPathState state) {
  auto iter =
      std::find_if(ases_.begin(), ases_.end(), [state](const auto& ase) {
        return (ase.active && (ase.data_path_state == state));
      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetFirstInactiveAse(uint8_t direction,
                                               bool reuse_cis_id) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [direction, reuse_cis_id](const auto& ase) {
                             if (ase.active || (ase.direction != direction))
                               return false;

                             if (!reuse_cis_id) return true;

                             return (ase.cis_id != kInvalidCisId);
                           });
  /* If ASE is found, return it */
  if (iter != ases_.end()) return &(*iter);

  /* If reuse was not set, that means there is no inactive ASE available. */
  if (!reuse_cis_id) return nullptr;

  /* Since there is no ASE with assigned CIS ID, it means new configuration
   * needs more ASEs then it was configured before.
   * Let's find just inactive one */
  iter = std::find_if(ases_.begin(), ases_.end(),
                      [direction](const auto& ase) {
                        if (ase.active || (ase.direction != direction))
                          return false;
                        return true;
                      });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetNextActiveAse(struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [&base_ase](auto& ase) { return base_ase == &ase; });

  /* Invalid ase given */
  if (iter == ases_.end() || std::distance(iter, ases_.end()) < 1)
    return nullptr;

  iter = std::find_if(std::next(iter, 1), ases_.end(),
                      [](const auto& ase) { return ase.active; });

  return (iter == ases_.end()) ? nullptr : &(*iter);
}

struct ase* LeAudioDevice::GetAseToMatchBidirectionCis(struct ase* base_ase) {
  auto iter = std::find_if(ases_.begin(), ases_.end(), [&base_ase](auto& ase) {
    return (base_ase->cis_conn_hdl == ase.cis_conn_hdl) &&
           (base_ase->direction != ase.direction);
  });
  return (iter == ases_.end()) ? nullptr : &(*iter);
}

BidirectAsesPair LeAudioDevice::GetAsesByCisConnHdl(uint16_t conn_hdl) {
  BidirectAsesPair ases = {nullptr, nullptr};

  for (auto& ase : ases_) {
    if (ase.cis_conn_hdl == conn_hdl) {
      if (ase.direction == types::kLeAudioDirectionSink) {
        ases.sink = &ase;
      } else {
        ases.source = &ase;
      }
    }
  }

  return ases;
}

BidirectAsesPair LeAudioDevice::GetAsesByCisId(uint8_t cis_id) {
  BidirectAsesPair ases = {nullptr, nullptr};

  for (auto& ase : ases_) {
    if (ase.cis_id == cis_id) {
      if (ase.direction == types::kLeAudioDirectionSink) {
        ases.sink = &ase;
      } else {
        ases.source = &ase;
      }
    }
  }

  return ases;
}

bool LeAudioDevice::HaveActiveAse(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(),
                           [](const auto& ase) { return ase.active; });

  return iter != ases_.end();
}

bool LeAudioDevice::HaveAnyUnconfiguredAses(void) {
  /* In configuring state when active in Idle or Configured and reconfigure */
  auto iter = std::find_if(ases_.begin(), ases_.end(), [](const auto& ase) {
    if (!ase.active) return false;

    if (ase.state == AseState::BTA_LE_AUDIO_ASE_STATE_IDLE ||
        ((ase.state == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED) &&
         ase.reconfigure))
      return true;

    return false;
  });

  return iter != ases_.end();
}

bool LeAudioDevice::HaveAllActiveAsesSameState(AseState state) {
  auto iter = std::find_if(
      ases_.begin(), ases_.end(),
      [&state](const auto& ase) { return ase.active && (ase.state != state); });

  return iter == ases_.end();
}

bool LeAudioDevice::IsReadyToCreateStream(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(), [](const auto& ase) {
    if (!ase.active) return false;

    if (ase.direction == types::kLeAudioDirectionSink &&
        (ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING &&
         ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_ENABLING))
      return true;

    if (ase.direction == types::kLeAudioDirectionSource &&
        ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_ENABLING)
      return true;

    return false;
  });

  return iter == ases_.end();
}

bool LeAudioDevice::IsReadyToSuspendStream(void) {
  auto iter = std::find_if(ases_.begin(), ases_.end(), [](const auto& ase) {
    if (!ase.active) return false;

    if (ase.direction == types::kLeAudioDirectionSink &&
        ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED)
      return true;

    if (ase.direction == types::kLeAudioDirectionSource &&
        ase.state != AseState::BTA_LE_AUDIO_ASE_STATE_DISABLING)
      return true;

    return false;
  });

  return iter == ases_.end();
}

bool LeAudioDevice::HaveAllActiveAsesCisEst(void) {
  if (ases_.empty()) {
    LOG_WARN("No ases for device %s", address_.ToString().c_str());
    return false;
  }

  auto iter = std::find_if(ases_.begin(), ases_.end(), [](const auto& ase) {
    return ase.active &&
           (ase.data_path_state != AudioStreamDataPathState::CIS_ESTABLISHED);
  });

  return iter == ases_.end();
}

bool LeAudioDevice::HaveAnyCisConnected(void) {
  /* Pending and Disconnecting is considered as connected in this function */
  for (auto const ase : ases_) {
    if (ase.data_path_state != AudioStreamDataPathState::CIS_ASSIGNED &&
        ase.data_path_state != AudioStreamDataPathState::IDLE) {
      return true;
    }
  }
  return false;
}

bool LeAudioDevice::HasCisId(uint8_t id) {
  struct ase* ase = GetFirstActiveAse();

  while (ase) {
    if (ase->cis_id == id) return true;
    ase = GetNextActiveAse(ase);
  }

  return false;
}

uint8_t LeAudioDevice::GetMatchingBidirectionCisId(
    const struct types::ase* base_ase) {
  for (auto& ase : ases_) {
    auto& cis = ase.cis_id;
    if (!ase.active) continue;

    int num_cises =
        std::count_if(ases_.begin(), ases_.end(), [&cis](const auto& iter_ase) {
          return iter_ase.active && iter_ase.cis_id == cis;
        });

    /*
     * If there is only one ASE for device with unique CIS ID and opposite to
     * direction - it may be bi-directional/completive.
     */
    if (num_cises == 1 &&
        ((base_ase->direction == types::kLeAudioDirectionSink &&
          ase.direction == types::kLeAudioDirectionSource) ||
         (base_ase->direction == types::kLeAudioDirectionSource &&
          ase.direction == types::kLeAudioDirectionSink))) {
      return ase.cis_id;
    }
  }

  return kInvalidCisId;
}

uint8_t LeAudioDevice::GetLc3SupportedChannelCount(uint8_t direction) {
  auto& pacs =
      direction == types::kLeAudioDirectionSink ? snk_pacs_ : src_pacs_;

  if (pacs.size() == 0) {
    LOG(ERROR) << __func__ << " missing PAC for direction " << +direction;
    return 0;
  }

  for (const auto& pac_tuple : pacs) {
    /* Get PAC records from tuple as second element from tuple */
    auto& pac_recs = std::get<1>(pac_tuple);

    for (const auto pac : pac_recs) {
      if (pac.codec_id.coding_format != types::kLeAudioCodingFormatLC3)
        continue;

      auto supported_channel_count_ltv = pac.codec_spec_caps.Find(
          codec_spec_caps::kLeAudioCodecLC3TypeAudioChannelCounts);

      if (supported_channel_count_ltv == std::nullopt ||
          supported_channel_count_ltv->size() == 0L) {
        return 1;
      }

      return VEC_UINT8_TO_UINT8(supported_channel_count_ltv.value());
    };
  }

  return 0;
}

const struct types::acs_ac_record*
LeAudioDevice::GetCodecConfigurationSupportedPac(
    uint8_t direction, const CodecCapabilitySetting& codec_capability_setting) {
  auto& pacs =
      direction == types::kLeAudioDirectionSink ? snk_pacs_ : src_pacs_;

  if (pacs.size() == 0) {
    LOG_ERROR("missing PAC for direction %d", direction);
    return nullptr;
  }

  /* TODO: Validate channel locations */

  for (const auto& pac_tuple : pacs) {
    /* Get PAC records from tuple as second element from tuple */
    auto& pac_recs = std::get<1>(pac_tuple);

    for (const auto& pac : pac_recs) {
      if (!IsCodecCapabilitySettingSupported(pac, codec_capability_setting))
        continue;

      return &pac;
    };
  }

  /* Doesn't match required configuration with any PAC */
  return nullptr;
}

/**
 * Returns supported PHY's bitfield
 */
uint8_t LeAudioDevice::GetPhyBitmask(void) {
  uint8_t phy_bitfield = kIsoCigPhy1M;

  if (BTM_IsPhy2mSupported(address_, BT_TRANSPORT_LE))
    phy_bitfield |= kIsoCigPhy2M;

  return phy_bitfield;
}

void LeAudioDevice::SetSupportedContexts(AudioContexts snk_contexts,
                                         AudioContexts src_contexts) {
  supp_contexts_.sink = snk_contexts;
  supp_contexts_.source = src_contexts;
}

void LeAudioDevice::PrintDebugState(void) {
  std::stringstream debug_str;

  debug_str << " address: " << address_ << ", "
            << bluetooth::common::ToString(connection_state_)
            << ", conn_id: " << +conn_id_ << ", mtu: " << +mtu_
            << ", num_of_ase: " << static_cast<int>(ases_.size());

  if (ases_.size() > 0) {
    debug_str << "\n  == ASEs == ";
    for (auto& ase : ases_) {
      debug_str << "\n  id: " << +ase.id << ", active: " << ase.active
                << ", dir: "
                << (ase.direction == types::kLeAudioDirectionSink ? "sink"
                                                                  : "source")
                << ", cis_id: " << +ase.cis_id
                << ", cis_handle: " << +ase.cis_conn_hdl << ", state: "
                << bluetooth::common::ToString(ase.data_path_state)
                << "\n ase max_latency: " << +ase.max_transport_latency
                << ", rtn: " << +ase.retrans_nb
                << ", max_sdu: " << +ase.max_sdu_size
                << ", target latency: " << +ase.target_latency;
    }
  }

  LOG_INFO("%s", debug_str.str().c_str());
}

void LeAudioDevice::Dump(int fd) {
  uint16_t acl_handle = BTM_GetHCIConnHandle(address_, BT_TRANSPORT_LE);
  std::string location = "unknown location";

  if (snk_audio_locations_.to_ulong() &
      codec_spec_conf::kLeAudioLocationAnyLeft) {
    std::string location_left = "left";
    location.swap(location_left);
  } else if (snk_audio_locations_.to_ulong() &
             codec_spec_conf::kLeAudioLocationAnyRight) {
    std::string location_right = "right";
    location.swap(location_right);
  }

  std::stringstream stream;
  stream << "\n\taddress: " << address_ << ": " << connection_state_ << ": "
         << (conn_id_ == GATT_INVALID_CONN_ID ? "" : std::to_string(conn_id_))
         << ", acl_handle: " << std::to_string(acl_handle) << ", " << location
         << ",\t" << (encrypted_ ? "Encrypted" : "Unecrypted")
         << ",mtu: " << std::to_string(mtu_)
         << "\n\tnumber of ases_: " << static_cast<int>(ases_.size());

  if (ases_.size() > 0) {
    stream << "\n\t== ASEs == \n\t";
    stream
        << "id  active dir     cis_id  cis_handle  sdu  latency rtn  state";
    for (auto& ase : ases_) {
      stream << std::setfill('\xA0') << "\n\t" << std::left << std::setw(4)
             << static_cast<int>(ase.id) << std::left << std::setw(7)
             << (ase.active ? "true" : "false") << std::left << std::setw(8)
             << (ase.direction == types::kLeAudioDirectionSink ? "sink"
                                                               : "source")
             << std::left << std::setw(8) << static_cast<int>(ase.cis_id)
             << std::left << std::setw(12) << ase.cis_conn_hdl << std::left
             << std::setw(5) << ase.max_sdu_size << std::left << std::setw(8)
             << ase.max_transport_latency << std::left << std::setw(5)
             << static_cast<int>(ase.retrans_nb) << std::left << std::setw(12)
             << bluetooth::common::ToString(ase.data_path_state);
    }
  }
  stream << "\n\t====";

  dprintf(fd, "%s", stream.str().c_str());
}

void LeAudioDevice::DisconnectAcl(void) {
  if (conn_id_ == GATT_INVALID_CONN_ID) return;

  uint16_t acl_handle =
      BTM_GetHCIConnHandle(address_, BT_TRANSPORT_LE);
  if (acl_handle != HCI_INVALID_HANDLE) {
    acl_disconnect_from_handle(acl_handle, HCI_ERR_PEER_USER,
                               "bta::le_audio::client disconnect");
  }
}

types::AudioContexts LeAudioDevice::GetAvailableContexts(int direction) {
  if (direction ==
      (types::kLeAudioDirectionSink | types::kLeAudioDirectionSource)) {
    return get_bidirectional(avail_contexts_);
  } else if (direction == types::kLeAudioDirectionSink) {
    return avail_contexts_.sink;
  }
  return avail_contexts_.source;
}

/* Returns XOR of updated sink and source bitset context types */
AudioContexts LeAudioDevice::SetAvailableContexts(AudioContexts snk_contexts,
                                                  AudioContexts src_contexts) {
  AudioContexts updated_contexts;

  updated_contexts = snk_contexts ^ avail_contexts_.sink;
  updated_contexts |= src_contexts ^ avail_contexts_.source;

  LOG_DEBUG(
      "\n\t avail_contexts_.sink: %s \n\t avail_contexts_.source: %s  \n\t "
      "snk_contexts: %s \n\t src_contexts: %s \n\t updated_contexts: %s",
      avail_contexts_.sink.to_string().c_str(),
      avail_contexts_.source.to_string().c_str(),
      snk_contexts.to_string().c_str(), src_contexts.to_string().c_str(),
      updated_contexts.to_string().c_str());

  avail_contexts_.sink = snk_contexts;
  avail_contexts_.source = src_contexts;

  return updated_contexts;
}

bool LeAudioDevice::ActivateConfiguredAses(LeAudioContextType context_type) {
  if (conn_id_ == GATT_INVALID_CONN_ID) {
    LOG_WARN(" Device %s is not connected ", address_.ToString().c_str());
    return false;
  }

  bool ret = false;

  LOG_INFO(" Configuring device %s", address_.ToString().c_str());
  for (auto& ase : ases_) {
    if (ase.state == AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED &&
        ase.configured_for_context_type == context_type) {
      LOG_INFO(
          " conn_id: %d, ase id %d, cis id %d, cis_handle 0x%04x is activated.",
          conn_id_, ase.id, ase.cis_id, ase.cis_conn_hdl);
      ase.active = true;
      ret = true;
    }
  }

  return ret;
}

void LeAudioDevice::DeactivateAllAses(void) {
  for (auto& ase : ases_) {
    if (ase.active == false &&
        ase.data_path_state != AudioStreamDataPathState::IDLE) {
      LOG_WARN(
          " %s, ase_id: %d, ase.cis_id: %d, cis_handle: 0x%02x, "
          "ase.data_path=%s",
          address_.ToString().c_str(), ase.id, ase.cis_id, ase.cis_conn_hdl,
          bluetooth::common::ToString(ase.data_path_state).c_str());
    }
    ase.state = AseState::BTA_LE_AUDIO_ASE_STATE_IDLE;
    ase.data_path_state = AudioStreamDataPathState::IDLE;
    ase.active = false;
    ase.cis_id = le_audio::kInvalidCisId;
    ase.cis_conn_hdl = 0;
  }
}

std::vector<uint8_t> LeAudioDevice::GetMetadata(
    AudioContexts context_type, const std::vector<uint8_t>& ccid_list) {
  std::vector<uint8_t> metadata;

  AppendMetadataLtvEntryForStreamingContext(metadata, context_type);
  AppendMetadataLtvEntryForCcidList(metadata, ccid_list);

  return std::move(metadata);
}

bool LeAudioDevice::IsMetadataChanged(AudioContexts context_type,
                                      const std::vector<uint8_t>& ccid_list) {
  for (auto* ase = this->GetFirstActiveAse(); ase;
       ase = this->GetNextActiveAse(ase)) {
    if (this->GetMetadata(context_type, ccid_list) != ase->metadata)
      return true;
  }

  return false;
}

LeAudioDeviceGroup* LeAudioDeviceGroups::Add(int group_id) {
  /* Get first free group id */
  if (FindById(group_id)) {
    LOG(ERROR) << __func__
               << ", group already exists, id: " << loghex(group_id);
    return nullptr;
  }

  return (groups_.emplace_back(std::make_unique<LeAudioDeviceGroup>(group_id)))
      .get();
}

void LeAudioDeviceGroups::Remove(int group_id) {
  auto iter = std::find_if(
      groups_.begin(), groups_.end(),
      [&group_id](auto const& group) { return group->group_id_ == group_id; });

  if (iter == groups_.end()) {
    LOG(ERROR) << __func__ << ", no such group_id: " << group_id;
    return;
  }

  groups_.erase(iter);
}

LeAudioDeviceGroup* LeAudioDeviceGroups::FindById(int group_id) {
  auto iter = std::find_if(
      groups_.begin(), groups_.end(),
      [&group_id](auto const& group) { return group->group_id_ == group_id; });

  return (iter == groups_.end()) ? nullptr : iter->get();
}

void LeAudioDeviceGroups::Cleanup(void) {
  for (auto& g : groups_) {
    g->Cleanup();
  }

  groups_.clear();
}

void LeAudioDeviceGroups::Dump(int fd, int active_group_id) {
  for (auto& g : groups_) {
    g->Dump(fd, active_group_id);
  }
}

bool LeAudioDeviceGroups::IsAnyInTransition(void) {
  for (auto& g : groups_) {
    if (g->IsInTransition()) {
      DLOG(INFO) << __func__ << " group: " << g->group_id_
                 << " is in transition";
      return true;
    }
  }
  return false;
}

size_t LeAudioDeviceGroups::Size() { return (groups_.size()); }

std::vector<int> LeAudioDeviceGroups::GetGroupsIds(void) {
  std::vector<int> result;

  for (auto const& group : groups_) {
    result.push_back(group->group_id_);
  }

  return result;
}

/* LeAudioDevices Class methods implementation */
void LeAudioDevices::Add(const RawAddress& address, DeviceConnectState state,
                         int group_id) {
  auto device = FindByAddress(address);
  if (device != nullptr) {
    LOG(ERROR) << __func__ << ", address: " << address
               << " is already assigned to group: " << device->group_id_;
    return;
  }

  leAudioDevices_.emplace_back(
      std::make_shared<LeAudioDevice>(address, state, group_id));
}

void LeAudioDevices::Remove(const RawAddress& address) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&address](auto const& leAudioDevice) {
                             return leAudioDevice->address_ == address;
                           });

  if (iter == leAudioDevices_.end()) {
    LOG(ERROR) << __func__ << ", no such address: " << address;
    return;
  }

  leAudioDevices_.erase(iter);
}

LeAudioDevice* LeAudioDevices::FindByAddress(const RawAddress& address) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&address](auto const& leAudioDevice) {
                             return leAudioDevice->address_ == address;
                           });

  return (iter == leAudioDevices_.end()) ? nullptr : iter->get();
}

std::shared_ptr<LeAudioDevice> LeAudioDevices::GetByAddress(
    const RawAddress& address) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&address](auto const& leAudioDevice) {
                             return leAudioDevice->address_ == address;
                           });

  return (iter == leAudioDevices_.end()) ? nullptr : *iter;
}

LeAudioDevice* LeAudioDevices::FindByConnId(uint16_t conn_id) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&conn_id](auto const& leAudioDevice) {
                             return leAudioDevice->conn_id_ == conn_id;
                           });

  return (iter == leAudioDevices_.end()) ? nullptr : iter->get();
}

LeAudioDevice* LeAudioDevices::FindByCisConnHdl(uint8_t cig_id,
                                                uint16_t conn_hdl) {
  auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                           [&conn_hdl, &cig_id](auto& d) {
                             LeAudioDevice* dev;
                             BidirectAsesPair ases;

                             dev = d.get();
                             if (dev->group_id_ != cig_id) {
                               return false;
                             }

                             ases = dev->GetAsesByCisConnHdl(conn_hdl);
                             if (ases.sink || ases.source)
                               return true;
                             else
                               return false;
                           });

  if (iter == leAudioDevices_.end()) return nullptr;

  return iter->get();
}

void LeAudioDevices::SetInitialGroupAutoconnectState(
    int group_id, int gatt_if, tBTM_BLE_CONN_TYPE reconnection_mode,
    bool current_dev_autoconnect_flag) {
  if (!current_dev_autoconnect_flag) {
    /* If current device autoconnect flag is false, check if there is other
     * device in the group which is in autoconnect mode.
     * If yes, assume whole group is in autoconnect.
     */
    auto iter = std::find_if(leAudioDevices_.begin(), leAudioDevices_.end(),
                             [&group_id](auto& d) {
                               LeAudioDevice* dev;
                               dev = d.get();
                               if (dev->group_id_ != group_id) {
                                 return false;
                               }
                               return dev->autoconnect_flag_;
                             });

    current_dev_autoconnect_flag = !(iter == leAudioDevices_.end());
  }

  if (!current_dev_autoconnect_flag) {
    return;
  }

  for (auto dev : leAudioDevices_) {
    if ((dev->group_id_ == group_id) &&
        (dev->GetConnectionState() == DeviceConnectState::DISCONNECTED)) {
      dev->SetConnectionState(DeviceConnectState::CONNECTING_AUTOCONNECT);
      dev->autoconnect_flag_ = true;
      btif_storage_set_leaudio_autoconnect(dev->address_, true);
      BTA_GATTC_Open(gatt_if, dev->address_, reconnection_mode, false);
    }
  }
}

size_t LeAudioDevices::Size() { return (leAudioDevices_.size()); }

void LeAudioDevices::Dump(int fd, int group_id) {
  for (auto const& device : leAudioDevices_) {
    if (device->group_id_ == group_id) {
      device->Dump(fd);
    }
  }
}

void LeAudioDevices::Cleanup(tGATT_IF client_if) {
  for (auto const& device : leAudioDevices_) {
    auto connection_state = device->GetConnectionState();
    if (connection_state == DeviceConnectState::DISCONNECTED) {
      continue;
    }

    if (connection_state == DeviceConnectState::CONNECTING_AUTOCONNECT) {
      BTA_GATTC_CancelOpen(client_if, device->address_, false);
    } else {
      BtaGattQueue::Clean(device->conn_id_);
      BTA_GATTC_Close(device->conn_id_);
      device->DisconnectAcl();
    }
  }
  leAudioDevices_.clear();
}

}  // namespace le_audio
