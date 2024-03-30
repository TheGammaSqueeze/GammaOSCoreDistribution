/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
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

#include <base/bind.h>
#include <base/strings/string_number_conversions.h>

#include <deque>
#include <optional>

#include "advertise_data_parser.h"
#include "audio_hal_client/audio_hal_client.h"
#include "audio_hal_interface/le_audio_software.h"
#include "bta/csis/csis_types.h"
#include "bta_api.h"
#include "bta_gatt_api.h"
#include "bta_gatt_queue.h"
#include "bta_groups.h"
#include "bta_le_audio_api.h"
#include "btif_storage.h"
#include "btm_iso_api.h"
#include "client_parser.h"
#include "codec_manager.h"
#include "common/time_util.h"
#include "content_control_id_keeper.h"
#include "device/include/controller.h"
#include "devices.h"
#include "embdrv/lc3/include/lc3.h"
#include "gatt/bta_gattc_int.h"
#include "gd/common/strings.h"
#include "internal_include/stack_config.h"
#include "le_audio_set_configuration_provider.h"
#include "le_audio_types.h"
#include "le_audio_utils.h"
#include "metrics_collector.h"
#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "osi/include/properties.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/btu.h"  // do_in_main_thread
#include "state_machine.h"
#include "storage_helper.h"

using base::Closure;
using bluetooth::Uuid;
using bluetooth::common::ToString;
using bluetooth::groups::DeviceGroups;
using bluetooth::groups::DeviceGroupsCallbacks;
using bluetooth::hci::IsoManager;
using bluetooth::hci::iso_manager::cig_create_cmpl_evt;
using bluetooth::hci::iso_manager::cig_remove_cmpl_evt;
using bluetooth::hci::iso_manager::CigCallbacks;
using bluetooth::le_audio::ConnectionState;
using bluetooth::le_audio::GroupNodeStatus;
using bluetooth::le_audio::GroupStatus;
using bluetooth::le_audio::GroupStreamStatus;
using le_audio::CodecManager;
using le_audio::ContentControlIdKeeper;
using le_audio::DeviceConnectState;
using le_audio::LeAudioCodecConfiguration;
using le_audio::LeAudioDevice;
using le_audio::LeAudioDeviceGroup;
using le_audio::LeAudioDeviceGroups;
using le_audio::LeAudioDevices;
using le_audio::LeAudioGroupStateMachine;
using le_audio::LeAudioSinkAudioHalClient;
using le_audio::LeAudioSourceAudioHalClient;
using le_audio::types::ase;
using le_audio::types::AseState;
using le_audio::types::AudioContexts;
using le_audio::types::AudioLocations;
using le_audio::types::AudioStreamDataPathState;
using le_audio::types::BidirectionalPair;
using le_audio::types::hdl_pair;
using le_audio::types::kDefaultScanDurationS;
using le_audio::types::LeAudioContextType;
using le_audio::utils::GetAllCcids;
using le_audio::utils::GetAllowedAudioContextsFromSinkMetadata;
using le_audio::utils::GetAllowedAudioContextsFromSourceMetadata;
using le_audio::utils::IsContextForAudioSource;

using le_audio::client_parser::ascs::
    kCtpResponseCodeInvalidConfigurationParameterValue;
using le_audio::client_parser::ascs::kCtpResponseCodeSuccess;
using le_audio::client_parser::ascs::kCtpResponseInvalidAseCisMapping;
using le_audio::client_parser::ascs::kCtpResponseNoReason;

/* Enums */
enum class AudioReconfigurationResult {
  RECONFIGURATION_NEEDED = 0x00,
  RECONFIGURATION_NOT_NEEDED,
  RECONFIGURATION_NOT_POSSIBLE
};

enum class AudioState {
  IDLE = 0x00,
  READY_TO_START,
  STARTED,
  READY_TO_RELEASE,
  RELEASING,
};

std::ostream& operator<<(std::ostream& os,
                         const AudioReconfigurationResult& state) {
  switch (state) {
    case AudioReconfigurationResult::RECONFIGURATION_NEEDED:
      os << "RECONFIGURATION_NEEDED";
      break;
    case AudioReconfigurationResult::RECONFIGURATION_NOT_NEEDED:
      os << "RECONFIGURATION_NOT_NEEDED";
      break;
    case AudioReconfigurationResult::RECONFIGURATION_NOT_POSSIBLE:
      os << "RECONFIGRATION_NOT_POSSIBLE";
      break;
    default:
      os << "UNKNOWN";
      break;
  }
  return os;
}

std::ostream& operator<<(std::ostream& os, const AudioState& audio_state) {
  switch (audio_state) {
    case AudioState::IDLE:
      os << "IDLE";
      break;
    case AudioState::READY_TO_START:
      os << "READY_TO_START";
      break;
    case AudioState::STARTED:
      os << "STARTED";
      break;
    case AudioState::READY_TO_RELEASE:
      os << "READY_TO_RELEASE";
      break;
    case AudioState::RELEASING:
      os << "RELEASING";
      break;
    default:
      os << "UNKNOWN";
      break;
  }
  return os;
}

namespace {
void le_audio_gattc_callback(tBTA_GATTC_EVT event, tBTA_GATTC* p_data);

inline uint8_t bits_to_bytes_per_sample(uint8_t bits_per_sample) {
  // 24 bit audio stream is sent as unpacked, each sample takes 4 bytes.
  if (bits_per_sample == 24) return 4;

  return bits_per_sample / 8;
}

inline lc3_pcm_format bits_to_lc3_bits(uint8_t bits_per_sample) {
  if (bits_per_sample == 16) return LC3_PCM_FORMAT_S16;

  if (bits_per_sample == 24) return LC3_PCM_FORMAT_S24;

  LOG_ALWAYS_FATAL("Encoder/decoder don't know how to handle %d",
                   bits_per_sample);
  return LC3_PCM_FORMAT_S16;
}

class LeAudioClientImpl;
LeAudioClientImpl* instance;
LeAudioSourceAudioHalClient::Callbacks* audioSinkReceiver;
LeAudioSinkAudioHalClient::Callbacks* audioSourceReceiver;
CigCallbacks* stateMachineHciCallbacks;
LeAudioGroupStateMachine::Callbacks* stateMachineCallbacks;
DeviceGroupsCallbacks* device_group_callbacks;

/*
 * Coordinatet Set Identification Profile (CSIP) based on CSIP 1.0
 * and Coordinatet Set Identification Service (CSIS) 1.0
 *
 * CSIP allows to organize audio servers into sets e.g. Stereo Set, 5.1 Set
 * and speed up connecting it.
 *
 * Since leaudio has already grouping API it was decided to integrate here CSIS
 * and allow it to group devices semi-automatically.
 *
 * Flow:
 * If connected device contains CSIS services, and it is included into CAP
 * service, implementation marks device as a set member and waits for the
 * bta/csis to learn about groups and notify implementation about assigned
 * group id.
 *
 */
/* LeAudioClientImpl class represents main implementation class for le audio
 * feature in stack. This class implements GATT, le audio and ISO related parts.
 *
 * This class is represented in single instance and manages a group of devices,
 * and devices. All devices calls back static method from it and are dispatched
 * to target receivers (e.g. ASEs, devices).
 *
 * This instance also implements a LeAudioClient which is a upper layer API.
 * Also LeAudioClientCallbacks are callbacks for upper layer.
 *
 * This class may be bonded with Test socket which allows to drive an instance
 * for test purposes.
 */
class LeAudioClientImpl : public LeAudioClient {
 public:
  ~LeAudioClientImpl() {
    alarm_free(close_vbc_timeout_);
    alarm_free(disable_timer_);
    alarm_free(suspend_timeout_);
  };

  LeAudioClientImpl(
      bluetooth::le_audio::LeAudioClientCallbacks* callbacks_,
      LeAudioGroupStateMachine::Callbacks* state_machine_callbacks_,
      base::Closure initCb)
      : gatt_if_(0),
        callbacks_(callbacks_),
        active_group_id_(bluetooth::groups::kGroupUnknown),
        configuration_context_type_(LeAudioContextType::UNINITIALIZED),
        metadata_context_types_(
            {sink : AudioContexts(), source : AudioContexts()}),
        stream_setup_start_timestamp_(0),
        stream_setup_end_timestamp_(0),
        audio_receiver_state_(AudioState::IDLE),
        audio_sender_state_(AudioState::IDLE),
        in_call_(false),
        current_source_codec_config({0, 0, 0, 0}),
        current_sink_codec_config({0, 0, 0, 0}),
        lc3_encoder_left_mem(nullptr),
        lc3_encoder_right_mem(nullptr),
        lc3_decoder_left_mem(nullptr),
        lc3_decoder_right_mem(nullptr),
        lc3_decoder_left(nullptr),
        lc3_decoder_right(nullptr),
        le_audio_source_hal_client_(nullptr),
        le_audio_sink_hal_client_(nullptr),
        close_vbc_timeout_(alarm_new("LeAudioCloseVbcTimeout")),
        suspend_timeout_(alarm_new("LeAudioSuspendTimeout")),
        disable_timer_(alarm_new("LeAudioDisableTimer")) {
    LeAudioGroupStateMachine::Initialize(state_machine_callbacks_);
    groupStateMachine_ = LeAudioGroupStateMachine::Get();

    if (bluetooth::common::InitFlags::
            IsTargetedAnnouncementReconnectionMode()) {
      LOG_INFO(" Reconnection mode: TARGETED_ANNOUNCEMENTS");
      reconnection_mode_ = BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS;
    } else {
      LOG_INFO(" Reconnection mode: ALLOW_LIST");
      reconnection_mode_ = BTM_BLE_BKG_CONNECT_ALLOW_LIST;
    }

    BTA_GATTC_AppRegister(
        le_audio_gattc_callback,
        base::Bind(
            [](base::Closure initCb, uint8_t client_id, uint8_t status) {
              if (status != GATT_SUCCESS) {
                LOG(ERROR) << "Can't start LeAudio profile - no gatt "
                              "clients left!";
                return;
              }
              instance->gatt_if_ = client_id;
              initCb.Run();
            },
            initCb),
        true);

    DeviceGroups::Get()->Initialize(device_group_callbacks);
  }

  void ReconfigureAfterVbcClose() {
    LOG_DEBUG("VBC close timeout");

    auto group = aseGroups_.FindById(active_group_id_);
    if (!group) {
      LOG_ERROR("Invalid group: %d", active_group_id_);
      return;
    }

    /* For sonification events we don't really need to reconfigure to HQ
     * configuration, but if the previous configuration was for HQ Media,
     * we might want to go back to that scenario.
     */

    if ((configuration_context_type_ != LeAudioContextType::MEDIA) &&
        (configuration_context_type_ != LeAudioContextType::GAME)) {
      LOG_INFO(
          "Keeping the old configuration as no HQ Media playback is needed "
          "right now.");
      return;
    }

    /* Test the existing metadata against the recent availability */
    metadata_context_types_.sink &= group->GetAvailableContexts();
    if (metadata_context_types_.sink.none()) {
      LOG_WARN("invalid/unknown context metadata, using 'MEDIA' instead");
      metadata_context_types_.sink = AudioContexts(LeAudioContextType::MEDIA);
    }

    /* Choose the right configuration context */
    auto new_configuration_context =
        ChooseConfigurationContextType(metadata_context_types_.sink);

    LOG_DEBUG("new_configuration_context= %s",
              ToString(new_configuration_context).c_str());
    ReconfigureOrUpdateMetadata(group, new_configuration_context,
                                metadata_context_types_.sink);
  }

  void StartVbcCloseTimeout() {
    if (alarm_is_scheduled(close_vbc_timeout_)) {
      StopVbcCloseTimeout();
    }

    static const uint64_t timeoutMs = 2000;
    LOG_DEBUG("Start VBC close timeout with %lu ms",
              static_cast<unsigned long>(timeoutMs));

    alarm_set_on_mloop(
        close_vbc_timeout_, timeoutMs,
        [](void*) {
          if (instance) instance->ReconfigureAfterVbcClose();
        },
        nullptr);
  }

  void StopVbcCloseTimeout() {
    if (alarm_is_scheduled(close_vbc_timeout_)) {
      LOG_DEBUG("Cancel VBC close timeout");
      alarm_cancel(close_vbc_timeout_);
    }
  }

  void AseInitialStateReadRequest(LeAudioDevice* leAudioDevice) {
    int ases_num = leAudioDevice->ases_.size();
    void* notify_flag_ptr = NULL;

    for (int i = 0; i < ases_num; i++) {
      /* Last read ase characteristic should issue connected state callback
       * to upper layer
       */

      if (leAudioDevice->notify_connected_after_read_ &&
          (i == (ases_num - 1))) {
        notify_flag_ptr =
            INT_TO_PTR(leAudioDevice->notify_connected_after_read_);
      }

      BtaGattQueue::ReadCharacteristic(leAudioDevice->conn_id_,
                                       leAudioDevice->ases_[i].hdls.val_hdl,
                                       OnGattReadRspStatic, notify_flag_ptr);
    }
  }

  void OnGroupAddedCb(const RawAddress& address, const bluetooth::Uuid& uuid,
                      int group_id) {
    LOG(INFO) << __func__ << " address: " << address << " group uuid " << uuid
              << " group_id: " << group_id;

    /* We are interested in the groups which are in the context of CAP */
    if (uuid != le_audio::uuid::kCapServiceUuid) return;

    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (!leAudioDevice) return;
    if (leAudioDevice->group_id_ != bluetooth::groups::kGroupUnknown) {
      LOG(INFO) << __func__
                << " group already set: " << leAudioDevice->group_id_;
      return;
    }

    group_add_node(group_id, address);
  }

  /* If device participates in streaming the group, it has to be stopped and
   * group needs to be reconfigured if needed to new configuration without
   * considering this removing device.
   */
  void SetDeviceAsRemovePendingAndStopGroup(LeAudioDevice* leAudioDevice) {
    LOG_INFO("device %s", leAudioDevice->address_.ToString().c_str());
    leAudioDevice->SetConnectionState(DeviceConnectState::PENDING_REMOVAL);
    GroupStop(leAudioDevice->group_id_);
  }

  void OnGroupMemberAddedCb(const RawAddress& address, int group_id) {
    LOG(INFO) << __func__ << " address: " << address
              << " group_id: " << group_id;

    auto group = aseGroups_.FindById(group_id);
    if (!group) {
      LOG(ERROR) << __func__ << " Not interested in group id: " << group_id;
      return;
    }

    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (!leAudioDevice) return;
    if (leAudioDevice->group_id_ != bluetooth::groups::kGroupUnknown) {
      LOG(INFO) << __func__
                << " group already set: " << leAudioDevice->group_id_;
      return;
    }

    group_add_node(group_id, address);
  }

  void OnGroupMemberRemovedCb(const RawAddress& address, int group_id) {
    LOG(INFO) << __func__ << " address: " << address
              << " group_id: " << group_id;

    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (!leAudioDevice) return;
    if (leAudioDevice->group_id_ != group_id) {
      LOG_WARN("Device: %s not assigned to the group.",
               leAudioDevice->address_.ToString().c_str());
      return;
    }

    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);
    if (group == NULL) {
      LOG(INFO) << __func__
                << " device not in the group: " << leAudioDevice->address_
                << ", " << group_id;
      return;
    }

    if (leAudioDevice->HaveActiveAse()) {
      SetDeviceAsRemovePendingAndStopGroup(leAudioDevice);
      return;
    }

    group_remove_node(group, address);
  }

  /* This callback happens if kLeAudioDeviceSetStateTimeoutMs timeout happens
   * during transition from origin to target state
   */
  void OnLeAudioDeviceSetStateTimeout(int group_id) {
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);

    if (!group) {
      /* Group removed */
      return;
    }

    LOG_ERROR(
        " State not achieved on time for group: group id %d, current state %s, "
        "target state: %s",
        group_id, ToString(group->GetState()).c_str(),
        ToString(group->GetTargetState()).c_str());
    group->SetTargetState(AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);

    group->PrintDebugState();

    /* There is an issue with a setting up stream or any other operation which
     * are gatt operations. It means peer is not responsable. Lets close ACL
     */
    CancelStreamingRequest();
    LeAudioDevice* leAudioDevice = group->GetFirstActiveDevice();
    if (leAudioDevice == nullptr) {
      LOG_ERROR(" Shouldn't be called without an active device.");
      leAudioDevice = group->GetFirstDevice();
      if (leAudioDevice == nullptr) {
        LOG_ERROR(" Front device is null. Number of devices: %d",
                  group->Size());
        return;
      }
    }

    do {
      if (instance) instance->DisconnectDevice(leAudioDevice, true);
      leAudioDevice = group->GetNextActiveDevice(leAudioDevice);
    } while (leAudioDevice);
  }

  void UpdateContextAndLocations(LeAudioDeviceGroup* group,
                                 LeAudioDevice* leAudioDevice) {
    if (leAudioDevice->GetConnectionState() != DeviceConnectState::CONNECTED) {
      LOG_DEBUG("%s not yet connected ",
                leAudioDevice->address_.ToString().c_str());
      return;
    }

    /* Make sure location and direction are updated for the group. */
    auto location_update = group->ReloadAudioLocations();
    group->ReloadAudioDirections();

    auto contexts_updated = group->UpdateAudioContextTypeAvailability(
        leAudioDevice->GetAvailableContexts());

    if (contexts_updated || location_update) {
      callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                              group->snk_audio_locations_.to_ulong(),
                              group->src_audio_locations_.to_ulong(),
                              group->GetAvailableContexts().value());
    }
  }

  void SuspendedForReconfiguration() {
    if (audio_sender_state_ > AudioState::IDLE) {
      le_audio_source_hal_client_->SuspendedForReconfiguration();
    }
    if (audio_receiver_state_ > AudioState::IDLE) {
      le_audio_sink_hal_client_->SuspendedForReconfiguration();
    }
  }

  void ReconfigurationComplete(uint8_t directions) {
    if (directions & le_audio::types::kLeAudioDirectionSink) {
      le_audio_source_hal_client_->ReconfigurationComplete();
    }
    if (directions & le_audio::types::kLeAudioDirectionSource) {
      le_audio_sink_hal_client_->ReconfigurationComplete();
    }
  }

  void CancelStreamingRequest() {
    if (audio_sender_state_ >= AudioState::READY_TO_START) {
      le_audio_source_hal_client_->CancelStreamingRequest();
      audio_sender_state_ = AudioState::IDLE;
    }

    if (audio_receiver_state_ >= AudioState::READY_TO_START) {
      le_audio_sink_hal_client_->CancelStreamingRequest();
      audio_receiver_state_ = AudioState::IDLE;
    }
  }

  void ControlPointNotificationHandler(
      struct le_audio::client_parser::ascs::ctp_ntf& ntf) {
    for (auto& entry : ntf.entries) {
      switch (entry.response_code) {
        case kCtpResponseCodeInvalidConfigurationParameterValue:
          switch (entry.reason) {
            case kCtpResponseInvalidAseCisMapping:
              CancelStreamingRequest();
              break;
            case kCtpResponseNoReason:
            default:
              break;
          }
          break;
        case kCtpResponseCodeSuccess:
          FALLTHROUGH;
        default:
          break;
      }
    }
  }

  void group_add_node(const int group_id, const RawAddress& address,
                      bool update_group_module = false) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    LeAudioDeviceGroup* new_group;
    LeAudioDeviceGroup* old_group = nullptr;
    int old_group_id = bluetooth::groups::kGroupUnknown;

    if (!leAudioDevice) {
      /* TODO This part possible to remove as this is to handle adding device to
       * the group which is unknown and not connected.
       */
      LOG(INFO) << __func__ << ", leAudioDevice unknown , address: " << address
                << " group: " << loghex(group_id);

      if (group_id == bluetooth::groups::kGroupUnknown) return;

      LOG(INFO) << __func__ << "Set member adding ...";
      leAudioDevices_.Add(address, DeviceConnectState::CONNECTING_BY_USER);
      leAudioDevice = leAudioDevices_.FindByAddress(address);
    } else {
      if (leAudioDevice->group_id_ != bluetooth::groups::kGroupUnknown) {
        old_group = aseGroups_.FindById(leAudioDevice->group_id_);
        old_group_id = old_group->group_id_;
      }
    }

    auto id = DeviceGroups::Get()->GetGroupId(address,
                                              le_audio::uuid::kCapServiceUuid);
    if (group_id == bluetooth::groups::kGroupUnknown) {
      if (id == bluetooth::groups::kGroupUnknown) {
        DeviceGroups::Get()->AddDevice(address,
                                       le_audio::uuid::kCapServiceUuid);
        /* We will get back here when group will be created */
        return;
      }

      new_group = aseGroups_.Add(id);
      if (!new_group) {
        LOG(ERROR) << __func__
                   << ", can't create group - group is already there?";
        return;
      }
    } else {
      ASSERT_LOG(id == group_id,
                 " group id missmatch? leaudio id: %d, groups module %d",
                 group_id, id);
      new_group = aseGroups_.FindById(group_id);
      if (!new_group) {
        new_group = aseGroups_.Add(group_id);
      } else {
        if (new_group->IsDeviceInTheGroup(leAudioDevice)) return;
      }
    }

    LOG_DEBUG("New group %p, id: %d", new_group, new_group->group_id_);

    /* If device was in the group and it was not removed by the application,
     * lets do it now
     */
    if (old_group) group_remove_node(old_group, address, update_group_module);

    new_group->AddNode(leAudioDevices_.GetByAddress(address));

    callbacks_->OnGroupNodeStatus(address, new_group->group_id_,
                                  GroupNodeStatus::ADDED);

    /* If device is connected and added to the group, lets read ASE states */
    if (leAudioDevice->conn_id_ != GATT_INVALID_CONN_ID)
      AseInitialStateReadRequest(leAudioDevice);

    /* Group may be destroyed once moved its last node to new group */
    if (aseGroups_.FindById(old_group_id) != nullptr) {
      /* Removing node from group may touch its context integrity */
      auto contexts_updated = old_group->UpdateAudioContextTypeAvailability(
          old_group->GetAvailableContexts());

      bool group_conf_changed = old_group->ReloadAudioLocations();
      group_conf_changed |= old_group->ReloadAudioDirections();
      group_conf_changed |= contexts_updated;

      if (group_conf_changed) {
        callbacks_->OnAudioConf(old_group->audio_directions_, old_group_id,
                                old_group->snk_audio_locations_.to_ulong(),
                                old_group->src_audio_locations_.to_ulong(),
                                old_group->GetAvailableContexts().value());
      }
    }

    UpdateContextAndLocations(new_group, leAudioDevice);
  }

  void GroupAddNode(const int group_id, const RawAddress& address) override {
    auto id = DeviceGroups::Get()->GetGroupId(address,
                                              le_audio::uuid::kCapServiceUuid);
    if (id == group_id) return;

    if (id != bluetooth::groups::kGroupUnknown) {
      DeviceGroups::Get()->RemoveDevice(address, id);
    }

    DeviceGroups::Get()->AddDevice(address, le_audio::uuid::kCapServiceUuid,
                                   group_id);
  }

  void remove_group_if_possible(LeAudioDeviceGroup* group) {
    if (!group) {
      LOG_DEBUG("group is null");
      return;
    }
    LOG_DEBUG("Group %p, id: %d, size: %d, is cig_state %s", group,
              group->group_id_, group->Size(),
              ToString(group->cig_state_).c_str());
    if (group->IsEmpty() &&
        (group->cig_state_ == le_audio::types::CigState::NONE)) {
      aseGroups_.Remove(group->group_id_);
    }
  }

  void group_remove_node(LeAudioDeviceGroup* group, const RawAddress& address,
                         bool update_group_module = false) {
    int group_id = group->group_id_;
    group->RemoveNode(leAudioDevices_.GetByAddress(address));

    if (update_group_module) {
      int groups_group_id = DeviceGroups::Get()->GetGroupId(
          address, le_audio::uuid::kCapServiceUuid);
      if (groups_group_id == group_id) {
        DeviceGroups::Get()->RemoveDevice(address, group_id);
      }
    }

    callbacks_->OnGroupNodeStatus(address, group_id, GroupNodeStatus::REMOVED);

    /* Remove group if this was the last leAudioDevice in this group */
    if (group->IsEmpty()) {
      remove_group_if_possible(group);
      return;
    }

    /* Removing node from group touch its context integrity */
    bool contexts_updated = group->UpdateAudioContextTypeAvailability(
        group->GetAvailableContexts());

    bool group_conf_changed = group->ReloadAudioLocations();
    group_conf_changed |= group->ReloadAudioDirections();
    group_conf_changed |= contexts_updated;

    if (group_conf_changed)
      callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                              group->snk_audio_locations_.to_ulong(),
                              group->src_audio_locations_.to_ulong(),
                              group->GetAvailableContexts().value());
  }

  void GroupRemoveNode(const int group_id, const RawAddress& address) override {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);

    LOG(INFO) << __func__ << " group_id: " << group_id
              << " address: " << address;

    if (!leAudioDevice) {
      LOG(ERROR) << __func__
                 << ", Skipping unknown leAudioDevice, address: " << address;
      return;
    }

    if (leAudioDevice->group_id_ != group_id) {
      LOG(ERROR) << __func__ << "Device is not in group_id: " << group_id
                 << ", but in group_id: " << leAudioDevice->group_id_;
      return;
    }

    if (group == NULL) {
      LOG(ERROR) << __func__ << " device not in the group ?!";
      return;
    }

    if (leAudioDevice->HaveActiveAse()) {
      SetDeviceAsRemovePendingAndStopGroup(leAudioDevice);
      return;
    }

    group_remove_node(group, address, true);
  }

  AudioContexts ChooseMetadataContextType(AudioContexts metadata_context_type) {
    /* This function takes already filtered contexts which we are plannig to use
     * in the Enable or UpdateMetadata command.
     * Note we are not changing stream configuration here, but just the list of
     * the contexts in the Metadata which will be provide to remote side.
     * Ideally, we should send all the bits we have, but not all headsets like
     * it.
     */
    if (osi_property_get_bool(kAllowMultipleContextsInMetadata, true)) {
      return metadata_context_type;
    }

    LOG_DEBUG("Converting to single context type: %s",
              metadata_context_type.to_string().c_str());

    /* Mini policy */
    if (metadata_context_type.any()) {
      LeAudioContextType context_priority_list[] = {
          /* Highest priority first */
          LeAudioContextType::CONVERSATIONAL,
          LeAudioContextType::RINGTONE,
          LeAudioContextType::LIVE,
          LeAudioContextType::VOICEASSISTANTS,
          LeAudioContextType::GAME,
          LeAudioContextType::MEDIA,
          LeAudioContextType::EMERGENCYALARM,
          LeAudioContextType::ALERTS,
          LeAudioContextType::INSTRUCTIONAL,
          LeAudioContextType::NOTIFICATIONS,
          LeAudioContextType::SOUNDEFFECTS,
      };
      for (auto ct : context_priority_list) {
        if (metadata_context_type.test(ct)) {
          LOG_DEBUG("Converted to single context type: %s",
                    ToString(ct).c_str());
          return AudioContexts(ct);
        }
      }
    }

    /* Fallback to BAP mandated context type */
    LOG_WARN("Invalid/unknown context, using 'UNSPECIFIED'");
    return AudioContexts(LeAudioContextType::UNSPECIFIED);
  }

  bool GroupStream(const int group_id, LeAudioContextType context_type,
                   AudioContexts metadata_context_type) {
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);
    auto final_context_type = context_type;

    auto adjusted_metadata_context_type =
        ChooseMetadataContextType(metadata_context_type);
    DLOG(INFO) << __func__;
    if (context_type >= LeAudioContextType::RFU) {
      LOG(ERROR) << __func__ << ", stream context type is not supported: "
                 << ToHexString(context_type);
      return false;
    }

    if (!group) {
      LOG(ERROR) << __func__ << ", unknown group id: " << group_id;
      return false;
    }

    LOG_DEBUG("group state=%s, target_state=%s",
              ToString(group->GetState()).c_str(),
              ToString(group->GetTargetState()).c_str());

    if (!group->GetAvailableContexts().test(context_type)) {
      LOG(ERROR) << " Unsupported context type by remote device: "
                 << ToHexString(context_type) << ". Switching to unspecified";
      final_context_type = LeAudioContextType::UNSPECIFIED;
    }

    if (!group->IsAnyDeviceConnected()) {
      LOG(ERROR) << __func__ << ", group " << group_id << " is not connected ";
      return false;
    }

    /* Check if any group is in the transition state. If so, we don't allow to
     * start new group to stream
     */
    if (group->IsInTransition()) {
      /* WARNING: Due to group state machine limitations, we should not
       * interrupt any ongoing transition. We will check if another
       * reconfiguration is needed once the group reaches streaming state.
       */
      LOG_WARN(
          "Group is already in the transition state. Waiting for the target "
          "state to be reached.");
      return false;
    }

    if (group->IsPendingConfiguration()) {
      LOG_WARN("Group %d is reconfiguring right now. Drop the update",
               group->group_id_);
      return false;
    }

    if (group->GetState() != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
      stream_setup_start_timestamp_ =
          bluetooth::common::time_get_os_boottime_us();
    }

    bool result = groupStateMachine_->StartStream(
        group, final_context_type, adjusted_metadata_context_type,
        GetAllCcids(adjusted_metadata_context_type));

    return result;
  }

  void GroupStream(const int group_id, const uint16_t context_type) override {
    GroupStream(group_id, LeAudioContextType(context_type),
                AudioContexts(context_type));
  }

  void GroupSuspend(const int group_id) override {
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);

    if (!group) {
      LOG(ERROR) << __func__ << ", unknown group id: " << group_id;
      return;
    }

    if (!group->IsAnyDeviceConnected()) {
      LOG(ERROR) << __func__ << ", group is not connected";
      return;
    }

    if (group->IsInTransition()) {
      LOG_INFO(", group is in transition from: %s to: %s",
               ToString(group->GetState()).c_str(),
               ToString(group->GetTargetState()).c_str());
      return;
    }

    if (group->GetState() != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
      LOG_ERROR(", invalid current state of group: %s",
                ToString(group->GetState()).c_str());
      return;
    }

    groupStateMachine_->SuspendStream(group);
  }

  void GroupStop(const int group_id) override {
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);

    if (!group) {
      LOG(ERROR) << __func__ << ", unknown group id: " << group_id;
      return;
    }

    if (group->IsEmpty()) {
      LOG(ERROR) << __func__ << ", group is empty";
      return;
    }

    if (group->GetState() == AseState::BTA_LE_AUDIO_ASE_STATE_IDLE) {
      LOG_ERROR(", group already stopped: %s",
                ToString(group->GetState()).c_str());

      return;
    }

    groupStateMachine_->StopStream(group);
  }

  void GroupDestroy(const int group_id) override {
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);

    if (!group) {
      LOG(ERROR) << __func__ << ", unknown group id: " << group_id;
      return;
    }

    // Disconnect and remove each device within the group
    auto* dev = group->GetFirstDevice();
    while (dev) {
      auto* next_dev = group->GetNextDevice(dev);
      RemoveDevice(dev->address_);
      dev = next_dev;
    }
  }

  void SetCodecConfigPreference(
      int group_id,
      bluetooth::le_audio::btle_audio_codec_config_t input_codec_config,
      bluetooth::le_audio::btle_audio_codec_config_t output_codec_config)
      override {
    // TODO Implement
  }

  void SetCcidInformation(int ccid, int context_type) override {
    LOG_DEBUG("Ccid: %d, context type %d", ccid, context_type);
    ContentControlIdKeeper::GetInstance()->SetCcid(context_type, ccid);
  }

  void SetInCall(bool in_call) override {
    LOG_DEBUG("in_call: %d", in_call);
    in_call_ = in_call;
  }

  void StartAudioSession(LeAudioDeviceGroup* group,
                         LeAudioCodecConfiguration* source_config,
                         LeAudioCodecConfiguration* sink_config) {
    /* This function is called when group is not yet set to active.
     * This is why we don't have to check if session is started already.
     * Just check if it is acquired.
     */
    ASSERT_LOG(active_group_id_ == bluetooth::groups::kGroupUnknown,
               "Active group is not set.");
    ASSERT_LOG(le_audio_source_hal_client_, "Source session not acquired");
    ASSERT_LOG(le_audio_sink_hal_client_, "Sink session not acquired");

    /* We assume that peer device always use same frame duration */
    uint32_t frame_duration_us = 0;
    if (!source_config->IsInvalid()) {
      frame_duration_us = source_config->data_interval_us;
    } else if (!sink_config->IsInvalid()) {
      frame_duration_us = sink_config->data_interval_us;
    } else {
      ASSERT_LOG(true, "Both configs are invalid");
    }

    audio_framework_source_config.data_interval_us = frame_duration_us;
    le_audio_source_hal_client_->Start(audio_framework_source_config,
                                       audioSinkReceiver);

    /* We use same frame duration for sink/source */
    audio_framework_sink_config.data_interval_us = frame_duration_us;

    /* If group supports more than 16kHz for the microphone in converstional
     * case let's use that also for Audio Framework.
     */
    std::optional<LeAudioCodecConfiguration> sink_configuration =
        group->GetCodecConfigurationByDirection(
            LeAudioContextType::CONVERSATIONAL,
            le_audio::types::kLeAudioDirectionSource);
    if (sink_configuration &&
        sink_configuration->sample_rate >
            bluetooth::audio::le_audio::kSampleRate16000) {
      audio_framework_sink_config.sample_rate = sink_configuration->sample_rate;
    }

    le_audio_sink_hal_client_->Start(audio_framework_sink_config,
                                     audioSourceReceiver);
  }

  void GroupSetActive(const int group_id) override {
    DLOG(INFO) << __func__ << " group_id: " << group_id;

    if (group_id == bluetooth::groups::kGroupUnknown) {
      if (active_group_id_ == bluetooth::groups::kGroupUnknown) {
        /* Nothing to do */
        return;
      }

      auto group_id_to_close = active_group_id_;
      active_group_id_ = bluetooth::groups::kGroupUnknown;

      if (alarm_is_scheduled(suspend_timeout_)) alarm_cancel(suspend_timeout_);

      StopAudio();
      ClientAudioIntefraceRelease();

      GroupStop(group_id_to_close);
      callbacks_->OnGroupStatus(group_id_to_close, GroupStatus::INACTIVE);
      return;
    }

    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);
    if (!group) {
      LOG(ERROR) << __func__
                 << ", Invalid group: " << static_cast<int>(group_id);
      return;
    }

    if (active_group_id_ != bluetooth::groups::kGroupUnknown) {
      if (active_group_id_ == group_id) {
        LOG(INFO) << __func__ << ", Group is already active: "
                  << static_cast<int>(active_group_id_);
        callbacks_->OnGroupStatus(active_group_id_, GroupStatus::ACTIVE);
        return;
      }
      LOG(INFO) << __func__ << ", switching active group to: " << group_id;
    }

    if (!le_audio_source_hal_client_) {
      le_audio_source_hal_client_ =
          LeAudioSourceAudioHalClient::AcquireUnicast();
      if (!le_audio_source_hal_client_) {
        LOG(ERROR) << __func__ << ", could not acquire audio source interface";
        return;
      }
    }

    if (!le_audio_sink_hal_client_) {
      le_audio_sink_hal_client_ = LeAudioSinkAudioHalClient::AcquireUnicast();
      if (!le_audio_sink_hal_client_) {
        LOG(ERROR) << __func__ << ", could not acquire audio sink interface";
        return;
      }
    }

    /* Mini policy: Try configure audio HAL sessions with most frequent context.
     * If reconfiguration is not needed it means, context type is not supported.
     * If most frequest scenario is not supported, try to find first supported.
     */
    LeAudioContextType default_context_type = LeAudioContextType::UNSPECIFIED;
    if (group->IsContextSupported(LeAudioContextType::MEDIA)) {
      default_context_type = LeAudioContextType::MEDIA;
    } else {
      for (LeAudioContextType context_type :
           le_audio::types::kLeAudioContextAllTypesArray) {
        if (group->IsContextSupported(context_type)) {
          default_context_type = context_type;
          break;
        }
      }
    }
    UpdateConfigAndCheckIfReconfigurationIsNeeded(group_id,
                                                  default_context_type);
    if (current_source_codec_config.IsInvalid() &&
        current_sink_codec_config.IsInvalid()) {
      LOG(WARNING) << __func__ << ", unsupported device configurations";
      return;
    }

    if (active_group_id_ == bluetooth::groups::kGroupUnknown) {
      /* Expose audio sessions if there was no previous active group */
      StartAudioSession(group, &current_source_codec_config,
                        &current_sink_codec_config);
    } else {
      /* In case there was an active group. Stop the stream */
      GroupStop(active_group_id_);
      callbacks_->OnGroupStatus(active_group_id_, GroupStatus::INACTIVE);
    }

    active_group_id_ = group_id;
    callbacks_->OnGroupStatus(active_group_id_, GroupStatus::ACTIVE);
  }

  void RemoveDevice(const RawAddress& address) override {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (!leAudioDevice) {
      return;
    }

    if (leAudioDevice->conn_id_ != GATT_INVALID_CONN_ID) {
      Disconnect(address);
      leAudioDevice->SetConnectionState(DeviceConnectState::REMOVING);
      return;
    }

    /* Remove the group assignment if not yet removed. It might happen that the
     * group module has already called the appropriate callback and we have
     * already removed the group assignment.
     */
    if (leAudioDevice->group_id_ != bluetooth::groups::kGroupUnknown) {
      auto group = aseGroups_.FindById(leAudioDevice->group_id_);
      group_remove_node(group, address, true);
    }

    leAudioDevices_.Remove(address);
  }

  void Connect(const RawAddress& address) override {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (!leAudioDevice) {
      leAudioDevices_.Add(address, DeviceConnectState::CONNECTING_BY_USER);
    } else {
      auto current_connect_state = leAudioDevice->GetConnectionState();
      if ((current_connect_state == DeviceConnectState::CONNECTED) ||
          (current_connect_state == DeviceConnectState::CONNECTING_BY_USER)) {
        LOG_ERROR("Device %s is in invalid state: %s",
                  leAudioDevice->address_.ToString().c_str(),
                  bluetooth::common::ToString(current_connect_state).c_str());

        return;
      }
      leAudioDevice->SetConnectionState(DeviceConnectState::CONNECTING_BY_USER);

      le_audio::MetricsCollector::Get()->OnConnectionStateChanged(
          leAudioDevice->group_id_, address, ConnectionState::CONNECTING,
          le_audio::ConnectionStatus::SUCCESS);
    }

    BTA_GATTC_Open(gatt_if_, address, BTM_BLE_DIRECT_CONNECTION, false);
  }

  std::vector<RawAddress> GetGroupDevices(const int group_id) override {
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);
    std::vector<RawAddress> all_group_device_addrs;

    if (group != nullptr) {
      LeAudioDevice* leAudioDevice = group->GetFirstDevice();
      while (leAudioDevice) {
        all_group_device_addrs.push_back(leAudioDevice->address_);
        leAudioDevice = group->GetNextDevice(leAudioDevice);
      };
    }

    return all_group_device_addrs;
  }

  /* Restore paired device from storage to recreate groups */
  void AddFromStorage(const RawAddress& address, bool autoconnect,
                      int sink_audio_location, int source_audio_location,
                      int sink_supported_context_types,
                      int source_supported_context_types,
                      const std::vector<uint8_t>& handles,
                      const std::vector<uint8_t>& sink_pacs,
                      const std::vector<uint8_t>& source_pacs,
                      const std::vector<uint8_t>& ases) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);

    if (leAudioDevice) {
      LOG_ERROR("Device is already loaded. Nothing to do.");
      return;
    }

    LOG_INFO(
        "restoring: %s, autoconnect %d, sink_audio_location: %d, "
        "source_audio_location: %d, sink_supported_context_types : 0x%04x, "
        "source_supported_context_types 0x%04x ",
        address.ToString().c_str(), autoconnect, sink_audio_location,
        source_audio_location, sink_supported_context_types,
        source_supported_context_types);

    leAudioDevices_.Add(address, DeviceConnectState::DISCONNECTED);
    leAudioDevice = leAudioDevices_.FindByAddress(address);

    int group_id = DeviceGroups::Get()->GetGroupId(
        address, le_audio::uuid::kCapServiceUuid);
    if (group_id != bluetooth::groups::kGroupUnknown) {
      group_add_node(group_id, address);
    }

    leAudioDevice->snk_audio_locations_ = sink_audio_location;
    if (sink_audio_location != 0) {
      leAudioDevice->audio_directions_ |=
          le_audio::types::kLeAudioDirectionSink;
    }

    callbacks_->OnSinkAudioLocationAvailable(
        leAudioDevice->address_,
        leAudioDevice->snk_audio_locations_.to_ulong());

    leAudioDevice->src_audio_locations_ = source_audio_location;
    if (source_audio_location != 0) {
      leAudioDevice->audio_directions_ |=
          le_audio::types::kLeAudioDirectionSource;
    }

    leAudioDevice->SetSupportedContexts(
        AudioContexts(sink_supported_context_types),
        AudioContexts(source_supported_context_types));

    /* Use same as or supported ones for now. */
    leAudioDevice->SetAvailableContexts(
        AudioContexts(sink_supported_context_types),
        AudioContexts(source_supported_context_types));

    if (!DeserializeHandles(leAudioDevice, handles)) {
      LOG_WARN("Could not load Handles");
    }

    if (!DeserializeSinkPacs(leAudioDevice, sink_pacs)) {
      LOG_WARN("Could not load sink pacs");
    }

    if (!DeserializeSourcePacs(leAudioDevice, source_pacs)) {
      LOG_WARN("Could not load source pacs");
    }

    if (!DeserializeAses(leAudioDevice, ases)) {
      LOG_WARN("Could not load ases");
    }

    leAudioDevice->autoconnect_flag_ = autoconnect;
    /* When adding from storage, make sure that autoconnect is used
     * by all the devices in the group.
     */
    leAudioDevices_.SetInitialGroupAutoconnectState(
        group_id, gatt_if_, reconnection_mode_, autoconnect);
  }

  bool GetHandlesForStorage(const RawAddress& addr, std::vector<uint8_t>& out) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(addr);
    return SerializeHandles(leAudioDevice, out);
  }

  bool GetSinkPacsForStorage(const RawAddress& addr,
                             std::vector<uint8_t>& out) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(addr);
    return SerializeSinkPacs(leAudioDevice, out);
  }

  bool GetSourcePacsForStorage(const RawAddress& addr,
                               std::vector<uint8_t>& out) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(addr);
    return SerializeSourcePacs(leAudioDevice, out);
  }

  bool GetAsesForStorage(const RawAddress& addr, std::vector<uint8_t>& out) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(addr);

    return SerializeAses(leAudioDevice, out);
  }

  void BackgroundConnectIfGroupConnected(LeAudioDevice* leAudioDevice) {
    DLOG(INFO) << __func__ << leAudioDevice->address_;
    auto group = aseGroups_.FindById(leAudioDevice->group_id_);
    if (!group) {
      DLOG(INFO) << __func__ << " Device is not yet part of the group. ";
      return;
    }

    if (!group->IsAnyDeviceConnected()) {
      DLOG(INFO) << __func__ << " group: " << leAudioDevice->group_id_
                 << " is not connected";
      return;
    }

    DLOG(INFO) << __func__ << " Add " << leAudioDevice->address_
               << " to background connect to connected group: "
               << leAudioDevice->group_id_;

    leAudioDevice->SetConnectionState(
        DeviceConnectState::CONNECTING_AUTOCONNECT);
    BTA_GATTC_Open(gatt_if_, leAudioDevice->address_, reconnection_mode_,
                   false);
  }

  void Disconnect(const RawAddress& address) override {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);

    if (!leAudioDevice) {
      LOG(ERROR) << __func__ << ", leAudioDevice not connected (" << address
                 << ")";
      return;
    }

    /* cancel pending direct connect */
    if (leAudioDevice->GetConnectionState() ==
        DeviceConnectState::CONNECTING_BY_USER) {
      BTA_GATTC_CancelOpen(gatt_if_, address, true);
    }

    /* Removes all registrations for connection */
    BTA_GATTC_CancelOpen(0, address, false);

    if (leAudioDevice->conn_id_ != GATT_INVALID_CONN_ID) {
      /* User is disconnecting the device, we shall remove the autoconnect
       * flag for this device and all others
       */
      LOG_INFO("Removing autoconnect flag for group_id %d",
               leAudioDevice->group_id_);

      auto group = aseGroups_.FindById(leAudioDevice->group_id_);

      if (leAudioDevice->autoconnect_flag_) {
        btif_storage_set_leaudio_autoconnect(address, false);
        leAudioDevice->autoconnect_flag_ = false;
      }

      if (group) {
        /* Remove devices from auto connect mode */
        for (auto dev = group->GetFirstDevice(); dev;
             dev = group->GetNextDevice(dev)) {
          if (dev->GetConnectionState() ==
              DeviceConnectState::CONNECTING_AUTOCONNECT) {
            btif_storage_set_leaudio_autoconnect(address, false);
            dev->autoconnect_flag_ = false;
            BTA_GATTC_CancelOpen(gatt_if_, address, false);
            dev->SetConnectionState(DeviceConnectState::DISCONNECTED);
          }
        }
      }

      if (group &&
          group->GetState() ==
              le_audio::types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
        leAudioDevice->closing_stream_for_disconnection_ = true;
        groupStateMachine_->StopStream(group);
        return;
      }
      DisconnectDevice(leAudioDevice);
      return;
    }

    /* If this is a device which is a part of the group which is connected,
     * lets start backgroup connect
     */
    BackgroundConnectIfGroupConnected(leAudioDevice);
  }

  void DisconnectDevice(LeAudioDevice* leAudioDevice,
                        bool acl_force_disconnect = false) {
    if (leAudioDevice->conn_id_ == GATT_INVALID_CONN_ID) {
      return;
    }

    leAudioDevice->SetConnectionState(DeviceConnectState::DISCONNECTING);

    BtaGattQueue::Clean(leAudioDevice->conn_id_);
    BTA_GATTC_Close(leAudioDevice->conn_id_);
    leAudioDevice->conn_id_ = GATT_INVALID_CONN_ID;
    leAudioDevice->mtu_ = 0;

    /* Remote in bad state, force ACL Disconnection. */
    if (acl_force_disconnect) {
      leAudioDevice->DisconnectAcl();
    }
  }

  void DeregisterNotifications(LeAudioDevice* leAudioDevice) {
    /* GATTC will ommit not registered previously handles */
    for (auto pac_tuple : leAudioDevice->snk_pacs_) {
      BTA_GATTC_DeregisterForNotifications(gatt_if_, leAudioDevice->address_,
                                           std::get<0>(pac_tuple).val_hdl);
    }
    for (auto pac_tuple : leAudioDevice->src_pacs_) {
      BTA_GATTC_DeregisterForNotifications(gatt_if_, leAudioDevice->address_,
                                           std::get<0>(pac_tuple).val_hdl);
    }

    if (leAudioDevice->snk_audio_locations_hdls_.val_hdl != 0)
      BTA_GATTC_DeregisterForNotifications(
          gatt_if_, leAudioDevice->address_,
          leAudioDevice->snk_audio_locations_hdls_.val_hdl);
    if (leAudioDevice->src_audio_locations_hdls_.val_hdl != 0)
      BTA_GATTC_DeregisterForNotifications(
          gatt_if_, leAudioDevice->address_,
          leAudioDevice->src_audio_locations_hdls_.val_hdl);
    if (leAudioDevice->audio_avail_hdls_.val_hdl != 0)
      BTA_GATTC_DeregisterForNotifications(
          gatt_if_, leAudioDevice->address_,
          leAudioDevice->audio_avail_hdls_.val_hdl);
    if (leAudioDevice->audio_supp_cont_hdls_.val_hdl != 0)
      BTA_GATTC_DeregisterForNotifications(
          gatt_if_, leAudioDevice->address_,
          leAudioDevice->audio_supp_cont_hdls_.val_hdl);
    if (leAudioDevice->ctp_hdls_.val_hdl != 0)
      BTA_GATTC_DeregisterForNotifications(gatt_if_, leAudioDevice->address_,
                                           leAudioDevice->ctp_hdls_.val_hdl);

    for (struct ase& ase : leAudioDevice->ases_)
      BTA_GATTC_DeregisterForNotifications(gatt_if_, leAudioDevice->address_,
                                           ase.hdls.val_hdl);
  }

  /* This is a generic read/notify/indicate handler for gatt. Here messages
   * are dispatched to correct elements e.g. ASEs, PACs, audio locations etc.
   */
  void LeAudioCharValueHandle(uint16_t conn_id, uint16_t hdl, uint16_t len,
                              uint8_t* value, bool notify = false) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByConnId(conn_id);
    struct ase* ase;

    if (!leAudioDevice) {
      LOG(ERROR) << __func__ << ", no leAudioDevice assigned to connection id: "
                 << static_cast<int>(conn_id);
      return;
    }

    ase = leAudioDevice->GetAseByValHandle(hdl);

    if (ase) {
      LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);
      groupStateMachine_->ProcessGattNotifEvent(value, len, ase, leAudioDevice,
                                                group);

      return;
    }

    auto snk_pac_ent = std::find_if(
        leAudioDevice->snk_pacs_.begin(), leAudioDevice->snk_pacs_.end(),
        [&hdl](auto& pac_ent) { return std::get<0>(pac_ent).val_hdl == hdl; });
    if (snk_pac_ent != leAudioDevice->snk_pacs_.end()) {
      std::vector<struct le_audio::types::acs_ac_record> pac_recs;

      /* Guard consistency of PAC records structure */
      if (!le_audio::client_parser::pacs::ParsePacs(pac_recs, len, value))
        return;

      LOG(INFO) << __func__ << ", Registering sink PACs";
      leAudioDevice->RegisterPACs(&std::get<1>(*snk_pac_ent), &pac_recs);

      /* Update supported context types including internal capabilities */
      LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);

      /* Available context map should be considered to be updated in response to
       * PACs update.
       * Read of available context during initial attribute discovery.
       * Group would be assigned once service search is completed.
       */
      if (group && group->UpdateAudioContextTypeAvailability(
                       leAudioDevice->GetAvailableContexts())) {
        callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                                group->snk_audio_locations_.to_ulong(),
                                group->src_audio_locations_.to_ulong(),
                                group->GetAvailableContexts().value());
      }
      if (notify) {
        btif_storage_leaudio_update_pacs_bin(leAudioDevice->address_);
      }
      return;
    }

    auto src_pac_ent = std::find_if(
        leAudioDevice->src_pacs_.begin(), leAudioDevice->src_pacs_.end(),
        [&hdl](auto& pac_ent) { return std::get<0>(pac_ent).val_hdl == hdl; });
    if (src_pac_ent != leAudioDevice->src_pacs_.end()) {
      std::vector<struct le_audio::types::acs_ac_record> pac_recs;

      /* Guard consistency of PAC records structure */
      if (!le_audio::client_parser::pacs::ParsePacs(pac_recs, len, value))
        return;

      LOG(INFO) << __func__ << ", Registering source PACs";
      leAudioDevice->RegisterPACs(&std::get<1>(*src_pac_ent), &pac_recs);

      /* Update supported context types including internal capabilities */
      LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);

      /* Available context map should be considered to be updated in response to
       * PACs update.
       * Read of available context during initial attribute discovery.
       * Group would be assigned once service search is completed.
       */
      if (group && group->UpdateAudioContextTypeAvailability(
                       leAudioDevice->GetAvailableContexts())) {
        callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                                group->snk_audio_locations_.to_ulong(),
                                group->src_audio_locations_.to_ulong(),
                                group->GetAvailableContexts().value());
      }

      if (notify) {
        btif_storage_leaudio_update_pacs_bin(leAudioDevice->address_);
      }
      return;
    }

    if (hdl == leAudioDevice->snk_audio_locations_hdls_.val_hdl) {
      AudioLocations snk_audio_locations;

      le_audio::client_parser::pacs::ParseAudioLocations(snk_audio_locations,
                                                         len, value);

      /* Value may not change */
      if ((leAudioDevice->audio_directions_ &
           le_audio::types::kLeAudioDirectionSink) &&
          (leAudioDevice->snk_audio_locations_ ^ snk_audio_locations).none())
        return;

      /* Presence of PAC characteristic for source means support for source
       * audio location. Value of 0x00000000 means mono/unspecified
       */
      leAudioDevice->audio_directions_ |=
          le_audio::types::kLeAudioDirectionSink;
      leAudioDevice->snk_audio_locations_ = snk_audio_locations;

      LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);
      callbacks_->OnSinkAudioLocationAvailable(leAudioDevice->address_,
                                               snk_audio_locations.to_ulong());

      if (notify) {
        btif_storage_set_leaudio_audio_location(
            leAudioDevice->address_,
            leAudioDevice->snk_audio_locations_.to_ulong(),
            leAudioDevice->src_audio_locations_.to_ulong());
      }

      /* Read of source audio locations during initial attribute discovery.
       * Group would be assigned once service search is completed.
       */
      if (!group) return;

      bool group_conf_changed = group->ReloadAudioLocations();
      group_conf_changed |= group->ReloadAudioDirections();

      if (group_conf_changed) {
        callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                                group->snk_audio_locations_.to_ulong(),
                                group->src_audio_locations_.to_ulong(),
                                group->GetAvailableContexts().value());
      }
    } else if (hdl == leAudioDevice->src_audio_locations_hdls_.val_hdl) {
      AudioLocations src_audio_locations;

      le_audio::client_parser::pacs::ParseAudioLocations(src_audio_locations,
                                                         len, value);

      /* Value may not change */
      if ((leAudioDevice->audio_directions_ &
           le_audio::types::kLeAudioDirectionSource) &&
          (leAudioDevice->src_audio_locations_ ^ src_audio_locations).none())
        return;

      /* Presence of PAC characteristic for source means support for source
       * audio location. Value of 0x00000000 means mono/unspecified
       */
      leAudioDevice->audio_directions_ |=
          le_audio::types::kLeAudioDirectionSource;
      leAudioDevice->src_audio_locations_ = src_audio_locations;

      LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);

      if (notify) {
        btif_storage_set_leaudio_audio_location(
            leAudioDevice->address_,
            leAudioDevice->snk_audio_locations_.to_ulong(),
            leAudioDevice->src_audio_locations_.to_ulong());
      }

      /* Read of source audio locations during initial attribute discovery.
       * Group would be assigned once service search is completed.
       */
      if (!group) return;

      bool group_conf_changed = group->ReloadAudioLocations();
      group_conf_changed |= group->ReloadAudioDirections();

      if (group_conf_changed) {
        callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                                group->snk_audio_locations_.to_ulong(),
                                group->src_audio_locations_.to_ulong(),
                                group->GetAvailableContexts().value());
      }
    } else if (hdl == leAudioDevice->audio_avail_hdls_.val_hdl) {
      le_audio::client_parser::pacs::acs_available_audio_contexts
          avail_audio_contexts;
      le_audio::client_parser::pacs::ParseAvailableAudioContexts(
          avail_audio_contexts, len, value);

      auto updated_avail_contexts = leAudioDevice->SetAvailableContexts(
          avail_audio_contexts.snk_avail_cont,
          avail_audio_contexts.src_avail_cont);

      if (updated_avail_contexts.any()) {
        /* Update scenario map considering changed available context types */
        LeAudioDeviceGroup* group =
            aseGroups_.FindById(leAudioDevice->group_id_);
        /* Read of available context during initial attribute discovery.
         * Group would be assigned once service search is completed.
         */
        if (group) {
          /* Update of available context may happen during state transition
           * or while streaming. Don't bother current transition or streaming
           * process. Update configuration once group became idle.
           */
          if (group->IsInTransition() ||
              (group->GetState() ==
               AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING)) {
            group->SetPendingAvailableContextsChange(updated_avail_contexts);
            return;
          }

          auto contexts_updated =
              group->UpdateAudioContextTypeAvailability(updated_avail_contexts);
          if (contexts_updated) {
            callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                                    group->snk_audio_locations_.to_ulong(),
                                    group->src_audio_locations_.to_ulong(),
                                    group->GetAvailableContexts().value());
          }
        }
      }
    } else if (hdl == leAudioDevice->audio_supp_cont_hdls_.val_hdl) {
      le_audio::client_parser::pacs::acs_supported_audio_contexts
          supp_audio_contexts;
      le_audio::client_parser::pacs::ParseSupportedAudioContexts(
          supp_audio_contexts, len, value);
      /* Just store if for now */
      leAudioDevice->SetSupportedContexts(supp_audio_contexts.snk_supp_cont,
                                          supp_audio_contexts.src_supp_cont);

      btif_storage_set_leaudio_supported_context_types(
          leAudioDevice->address_, supp_audio_contexts.snk_supp_cont.value(),
          supp_audio_contexts.src_supp_cont.value());

    } else if (hdl == leAudioDevice->ctp_hdls_.val_hdl) {
      auto ntf =
          std::make_unique<struct le_audio::client_parser::ascs::ctp_ntf>();

      if (ParseAseCtpNotification(*ntf, len, value))
        ControlPointNotificationHandler(*ntf);
    } else if (hdl == leAudioDevice->tmap_role_hdl_) {
      le_audio::client_parser::tmap::ParseTmapRole(leAudioDevice->tmap_role_,
                                                   len, value);
    } else {
      LOG(ERROR) << __func__ << ", Unknown attribute read: " << loghex(hdl);
    }
  }

  void OnGattReadRsp(uint16_t conn_id, tGATT_STATUS status, uint16_t hdl,
                     uint16_t len, uint8_t* value, void* data) {
    LeAudioCharValueHandle(conn_id, hdl, len, value);
  }

  void OnGattConnected(tGATT_STATUS status, uint16_t conn_id,
                       tGATT_IF client_if, RawAddress address,
                       tBT_TRANSPORT transport, uint16_t mtu) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);

    if (!leAudioDevice) return;

    if (status != GATT_SUCCESS) {
      /* autoconnect connection failed, that's ok */
      if (leAudioDevice->GetConnectionState() ==
          DeviceConnectState::CONNECTING_AUTOCONNECT) {
        leAudioDevice->SetConnectionState(DeviceConnectState::DISCONNECTED);
        return;
      }

      leAudioDevice->SetConnectionState(DeviceConnectState::DISCONNECTED);

      LOG(ERROR) << "Failed to connect to LeAudio leAudioDevice, status: "
                 << +status;
      callbacks_->OnConnectionState(ConnectionState::DISCONNECTED, address);
      le_audio::MetricsCollector::Get()->OnConnectionStateChanged(
          leAudioDevice->group_id_, address, ConnectionState::CONNECTED,
          le_audio::ConnectionStatus::FAILED);
      return;
    }

    if (controller_get_interface()->supports_ble_2m_phy()) {
      LOG(INFO) << address << " set preferred PHY to 2M";
      BTM_BleSetPhy(address, PHY_LE_2M, PHY_LE_2M, 0);
    }

    BTM_RequestPeerSCA(leAudioDevice->address_, transport);

    if (leAudioDevice->GetConnectionState() ==
        DeviceConnectState::CONNECTING_AUTOCONNECT) {
      leAudioDevice->SetConnectionState(
          DeviceConnectState::CONNECTED_AUTOCONNECT_GETTING_READY);
    } else {
      leAudioDevice->SetConnectionState(
          DeviceConnectState::CONNECTED_BY_USER_GETTING_READY);
    }

    leAudioDevice->conn_id_ = conn_id;
    leAudioDevice->mtu_ = mtu;

    if (BTM_SecIsSecurityPending(address)) {
      /* if security collision happened, wait for encryption done
       * (BTA_GATTC_ENC_CMPL_CB_EVT) */
      return;
    }

    /* verify bond */
    if (BTM_IsEncrypted(address, BT_TRANSPORT_LE)) {
      /* if link has been encrypted */
      OnEncryptionComplete(address, BTM_SUCCESS);
      return;
    }

    if (BTM_IsLinkKeyKnown(address, BT_TRANSPORT_LE)) {
      int result = BTM_SetEncryption(address, BT_TRANSPORT_LE, nullptr, nullptr,
                                     BTM_BLE_SEC_ENCRYPT);

      LOG(INFO) << __func__
                << "Encryption required. Request result: " << result;
      return;
    }

    LOG(ERROR) << __func__ << " Encryption error";
    le_audio::MetricsCollector::Get()->OnConnectionStateChanged(
        leAudioDevice->group_id_, address, ConnectionState::CONNECTED,
        le_audio::ConnectionStatus::FAILED);
  }

  void RegisterKnownNotifications(LeAudioDevice* leAudioDevice) {
    LOG(INFO) << __func__ << " device: " << leAudioDevice->address_;

    if (leAudioDevice->ctp_hdls_.val_hdl == 0) {
      LOG_ERROR(
          "Control point characteristic is mandatory - disconnecting device %s",
          leAudioDevice->address_.ToString().c_str());
      DisconnectDevice(leAudioDevice);
      return;
    }

    /* GATTC will ommit not registered previously handles */
    for (auto pac_tuple : leAudioDevice->snk_pacs_) {
      subscribe_for_notification(leAudioDevice->conn_id_,
                                 leAudioDevice->address_,
                                 std::get<0>(pac_tuple));
    }
    for (auto pac_tuple : leAudioDevice->src_pacs_) {
      subscribe_for_notification(leAudioDevice->conn_id_,
                                 leAudioDevice->address_,
                                 std::get<0>(pac_tuple));
    }

    if (leAudioDevice->snk_audio_locations_hdls_.val_hdl != 0)
      subscribe_for_notification(leAudioDevice->conn_id_,
                                 leAudioDevice->address_,
                                 leAudioDevice->snk_audio_locations_hdls_);
    if (leAudioDevice->src_audio_locations_hdls_.val_hdl != 0)
      subscribe_for_notification(leAudioDevice->conn_id_,
                                 leAudioDevice->address_,
                                 leAudioDevice->src_audio_locations_hdls_);

    if (leAudioDevice->audio_avail_hdls_.val_hdl != 0)
      subscribe_for_notification(leAudioDevice->conn_id_,
                                 leAudioDevice->address_,
                                 leAudioDevice->audio_avail_hdls_);

    if (leAudioDevice->audio_supp_cont_hdls_.val_hdl != 0)
      subscribe_for_notification(leAudioDevice->conn_id_,
                                 leAudioDevice->address_,
                                 leAudioDevice->audio_supp_cont_hdls_);

    for (struct ase& ase : leAudioDevice->ases_)
      subscribe_for_notification(leAudioDevice->conn_id_,
                                 leAudioDevice->address_, ase.hdls);

    subscribe_for_notification(leAudioDevice->conn_id_, leAudioDevice->address_,
                               leAudioDevice->ctp_hdls_);
  }

  void changeMtuIfPossible(LeAudioDevice* leAudioDevice) {
    if (leAudioDevice->mtu_ == GATT_DEF_BLE_MTU_SIZE) {
      LOG(INFO) << __func__ << ", Configure MTU";
      BtaGattQueue::ConfigureMtu(leAudioDevice->conn_id_, GATT_MAX_MTU_SIZE);
    }
  }

  void OnEncryptionComplete(const RawAddress& address, uint8_t status) {
    LOG(INFO) << __func__ << " " << address << "status: " << int{status};

    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (leAudioDevice == NULL) {
      LOG(WARNING) << "Skipping unknown device" << address;
      return;
    }

    if (status != BTM_SUCCESS) {
      LOG(ERROR) << "Encryption failed"
                 << " status: " << int{status};
      if (leAudioDevice->GetConnectionState() ==
          DeviceConnectState::CONNECTED_BY_USER_GETTING_READY) {
        callbacks_->OnConnectionState(ConnectionState::DISCONNECTED, address);
        le_audio::MetricsCollector::Get()->OnConnectionStateChanged(
            leAudioDevice->group_id_, address, ConnectionState::CONNECTED,
            le_audio::ConnectionStatus::FAILED);
      }

      leAudioDevice->SetConnectionState(DeviceConnectState::DISCONNECTING);

      BTA_GATTC_Close(leAudioDevice->conn_id_);
      return;
    }

    if (leAudioDevice->encrypted_) {
      LOG(INFO) << __func__ << " link already encrypted, nothing to do";
      return;
    }

    changeMtuIfPossible(leAudioDevice);

    /* If we know services, register for notifications */
    if (leAudioDevice->known_service_handles_)
      RegisterKnownNotifications(leAudioDevice);

    leAudioDevice->encrypted_ = true;

    /* If we know services and read is not ongoing, this is reconnection and
     * just notify connected  */
    if (leAudioDevice->known_service_handles_ &&
        !leAudioDevice->notify_connected_after_read_) {
      LOG_INFO("Wait for CCC registration and MTU change request");
      return;
    }

    BTA_GATTC_ServiceSearchRequest(
        leAudioDevice->conn_id_,
        &le_audio::uuid::kPublishedAudioCapabilityServiceUuid);
  }

  void OnGattDisconnected(uint16_t conn_id, tGATT_IF client_if,
                          RawAddress address, tGATT_DISCONN_REASON reason) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);

    if (!leAudioDevice) {
      LOG(ERROR) << ", skipping unknown leAudioDevice, address: " << address;
      return;
    }

    BtaGattQueue::Clean(leAudioDevice->conn_id_);
    LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);

    groupStateMachine_->ProcessHciNotifAclDisconnected(group, leAudioDevice);

    DeregisterNotifications(leAudioDevice);

    callbacks_->OnConnectionState(ConnectionState::DISCONNECTED, address);
    leAudioDevice->conn_id_ = GATT_INVALID_CONN_ID;
    leAudioDevice->mtu_ = 0;
    leAudioDevice->closing_stream_for_disconnection_ = false;
    leAudioDevice->encrypted_ = false;

    le_audio::MetricsCollector::Get()->OnConnectionStateChanged(
        leAudioDevice->group_id_, address, ConnectionState::DISCONNECTED,
        le_audio::ConnectionStatus::SUCCESS);

    if (leAudioDevice->GetConnectionState() == DeviceConnectState::REMOVING) {
      if (leAudioDevice->group_id_ != bluetooth::groups::kGroupUnknown) {
        auto group = aseGroups_.FindById(leAudioDevice->group_id_);
        group_remove_node(group, address, true);
      }
      leAudioDevices_.Remove(address);
      return;
    }
    /* Attempt background re-connect if disconnect was not intended locally
     * or if autoconnect is set and device got disconnected because of some
     * issues
     */
    if (reason != GATT_CONN_TERMINATE_LOCAL_HOST ||
        leAudioDevice->autoconnect_flag_) {
      leAudioDevice->SetConnectionState(
          DeviceConnectState::CONNECTING_AUTOCONNECT);
      BTA_GATTC_Open(gatt_if_, address, reconnection_mode_, false);
    } else {
      leAudioDevice->SetConnectionState(DeviceConnectState::DISCONNECTED);
    }
  }

  bool subscribe_for_notification(
      uint16_t conn_id, const RawAddress& address,
      struct le_audio::types::hdl_pair handle_pair) {
    std::vector<uint8_t> value(2);
    uint8_t* ptr = value.data();
    uint16_t handle = handle_pair.val_hdl;
    uint16_t ccc_handle = handle_pair.ccc_hdl;

    LOG_INFO("conn id %d", conn_id);
    if (BTA_GATTC_RegisterForNotifications(gatt_if_, address, handle) !=
        GATT_SUCCESS) {
      LOG(ERROR) << __func__ << ", cannot register for notification: "
                 << static_cast<int>(handle);
      return false;
    }

    UINT16_TO_STREAM(ptr, GATT_CHAR_CLIENT_CONFIG_NOTIFICATION);

    BtaGattQueue::WriteDescriptor(
        conn_id, ccc_handle, std::move(value), GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           const uint8_t* value, void* data) {
          if (instance) instance->OnGattWriteCcc(conn_id, status, handle, data);
        },
        nullptr);
    return true;
  }

  /* Find the handle for the client characteristics configuration of a given
   * characteristics.
   */
  uint16_t find_ccc_handle(const gatt::Characteristic& charac) {
    auto iter = std::find_if(
        charac.descriptors.begin(), charac.descriptors.end(),
        [](const auto& desc) {
          return desc.uuid == Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG);
        });

    return iter == charac.descriptors.end() ? 0 : (*iter).handle;
  }

  void ClearDeviceInformationAndStartSearch(LeAudioDevice* leAudioDevice) {
    if (!leAudioDevice) {
      LOG_WARN("leAudioDevice is null");
      return;
    }

    LOG_INFO("%s", leAudioDevice->address_.ToString().c_str());

    if (leAudioDevice->known_service_handles_ == false) {
      LOG_DEBUG("Database already invalidated");
      return;
    }

    leAudioDevice->known_service_handles_ = false;
    leAudioDevice->csis_member_ = false;
    BtaGattQueue::Clean(leAudioDevice->conn_id_);
    DeregisterNotifications(leAudioDevice);

    if (leAudioDevice->GetConnectionState() == DeviceConnectState::CONNECTED) {
      leAudioDevice->SetConnectionState(
          DeviceConnectState::CONNECTED_BY_USER_GETTING_READY);
    }

    btif_storage_remove_leaudio(leAudioDevice->address_);

    BTA_GATTC_ServiceSearchRequest(
        leAudioDevice->conn_id_,
        &le_audio::uuid::kPublishedAudioCapabilityServiceUuid);
  }

  void OnServiceChangeEvent(const RawAddress& address) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (!leAudioDevice) {
      LOG_WARN("Skipping unknown leAudioDevice %s", address.ToString().c_str());
      return;
    }
    ClearDeviceInformationAndStartSearch(leAudioDevice);
  }

  void OnMtuChanged(uint16_t conn_id, uint16_t mtu) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByConnId(conn_id);
    if (!leAudioDevice) {
      LOG_DEBUG("Unknown connectect id %d", conn_id);
      return;
    }

    leAudioDevice->mtu_ = mtu;
  }

  void OnGattServiceDiscoveryDone(const RawAddress& address) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(address);
    if (!leAudioDevice) {
      DLOG(ERROR) << __func__
                  << ", skipping unknown leAudioDevice, address: " << address;
      return;
    }

    if (!leAudioDevice->encrypted_) {
      LOG_DEBUG("Wait for device to be encrypted");
      return;
    }

    if (!leAudioDevice->known_service_handles_)
      BTA_GATTC_ServiceSearchRequest(
          leAudioDevice->conn_id_,
          &le_audio::uuid::kPublishedAudioCapabilityServiceUuid);
  }
  /* This method is called after connection beginning to identify and initialize
   * a le audio device. Any missing mandatory attribute will result in reverting
   * and cleaning up device.
   */
  void OnServiceSearchComplete(uint16_t conn_id, tGATT_STATUS status) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByConnId(conn_id);

    if (!leAudioDevice) {
      DLOG(ERROR) << __func__ << ", skipping unknown leAudioDevice, conn_id: "
                  << loghex(conn_id);
      return;
    }

    LOG(INFO) << __func__ << " test csis_member "
              << leAudioDevice->csis_member_;

    if (status != GATT_SUCCESS) {
      /* close connection and report service discovery complete with error */
      LOG(ERROR) << "Service discovery failed";

      DisconnectDevice(leAudioDevice);
      return;
    }

    const std::list<gatt::Service>* services = BTA_GATTC_GetServices(conn_id);

    const gatt::Service* pac_svc = nullptr;
    const gatt::Service* ase_svc = nullptr;
    const gatt::Service* tmas_svc = nullptr;

    std::vector<uint16_t> csis_primary_handles;
    uint16_t cas_csis_included_handle = 0;

    for (const gatt::Service& tmp : *services) {
      if (tmp.uuid == le_audio::uuid::kPublishedAudioCapabilityServiceUuid) {
        LOG(INFO) << "Found Audio Capability service, handle: "
                  << loghex(tmp.handle);
        pac_svc = &tmp;
      } else if (tmp.uuid == le_audio::uuid::kAudioStreamControlServiceUuid) {
        LOG(INFO) << "Found Audio Stream Endpoint service, handle: "
                  << loghex(tmp.handle);
        ase_svc = &tmp;
      } else if (tmp.uuid == bluetooth::csis::kCsisServiceUuid) {
        LOG(INFO) << "Found CSIS service, handle: " << loghex(tmp.handle)
                  << " is primary? " << tmp.is_primary;
        if (tmp.is_primary) csis_primary_handles.push_back(tmp.handle);
      } else if (tmp.uuid == le_audio::uuid::kCapServiceUuid) {
        LOG(INFO) << "Found CAP Service, handle: " << loghex(tmp.handle);

        /* Try to find context for CSIS instances */
        for (auto& included_srvc : tmp.included_services) {
          if (included_srvc.uuid == bluetooth::csis::kCsisServiceUuid) {
            LOG(INFO) << __func__ << " CSIS included into CAS";
            if (bluetooth::csis::CsisClient::IsCsisClientRunning())
              cas_csis_included_handle = included_srvc.start_handle;

            break;
          }
        }
      } else if (tmp.uuid == le_audio::uuid::kTelephonyMediaAudioServiceUuid) {
        LOG_INFO(", Found Telephony and Media Audio service, handle: %04x",
                 tmp.handle);
        tmas_svc = &tmp;
      }
    }

    /* Check if CAS includes primary CSIS service */
    if (!csis_primary_handles.empty() && cas_csis_included_handle) {
      auto iter =
          std::find(csis_primary_handles.begin(), csis_primary_handles.end(),
                    cas_csis_included_handle);
      if (iter != csis_primary_handles.end())
        leAudioDevice->csis_member_ = true;
    }

    if (!pac_svc || !ase_svc) {
      LOG(ERROR) << "No mandatory le audio services found";

      DisconnectDevice(leAudioDevice);
      return;
    }

    /* Refresh PACs handles */
    leAudioDevice->ClearPACs();

    for (const gatt::Characteristic& charac : pac_svc->characteristics) {
      if (charac.uuid ==
          le_audio::uuid::kSinkPublishedAudioCapabilityCharacteristicUuid) {
        struct hdl_pair hdl_pair;
        hdl_pair.val_hdl = charac.value_handle;
        hdl_pair.ccc_hdl = find_ccc_handle(charac);

        if (hdl_pair.ccc_hdl == 0) {
          LOG(ERROR) << __func__ << ", snk pac char doesn't have ccc";

          DisconnectDevice(leAudioDevice);
          return;
        }

        if (!subscribe_for_notification(conn_id, leAudioDevice->address_,
                                        hdl_pair)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        /* Obtain initial state of sink PACs */
        BtaGattQueue::ReadCharacteristic(conn_id, hdl_pair.val_hdl,
                                         OnGattReadRspStatic, NULL);

        leAudioDevice->snk_pacs_.push_back(std::make_tuple(
            hdl_pair, std::vector<struct le_audio::types::acs_ac_record>()));

        LOG(INFO) << "Found Sink PAC characteristic, handle: "
                  << loghex(charac.value_handle)
                  << ", ccc handle: " << loghex(hdl_pair.ccc_hdl);
      } else if (charac.uuid ==
                 le_audio::uuid::
                     kSourcePublishedAudioCapabilityCharacteristicUuid) {
        struct hdl_pair hdl_pair;
        hdl_pair.val_hdl = charac.value_handle;
        hdl_pair.ccc_hdl = find_ccc_handle(charac);

        if (hdl_pair.ccc_hdl == 0) {
          LOG(ERROR) << __func__ << ", src pac char doesn't have ccc";

          DisconnectDevice(leAudioDevice);
          return;
        }

        if (!subscribe_for_notification(conn_id, leAudioDevice->address_,
                                        hdl_pair)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        /* Obtain initial state of source PACs */
        BtaGattQueue::ReadCharacteristic(conn_id, hdl_pair.val_hdl,
                                         OnGattReadRspStatic, NULL);

        leAudioDevice->src_pacs_.push_back(std::make_tuple(
            hdl_pair, std::vector<struct le_audio::types::acs_ac_record>()));

        LOG(INFO) << "Found Source PAC characteristic, handle: "
                  << loghex(charac.value_handle)
                  << ", ccc handle: " << loghex(hdl_pair.ccc_hdl);
      } else if (charac.uuid ==
                 le_audio::uuid::kSinkAudioLocationCharacteristicUuid) {
        leAudioDevice->snk_audio_locations_hdls_.val_hdl = charac.value_handle;
        leAudioDevice->snk_audio_locations_hdls_.ccc_hdl =
            find_ccc_handle(charac);

        if (leAudioDevice->snk_audio_locations_hdls_.ccc_hdl == 0)
          LOG(INFO) << __func__
                    << ", snk audio locations char doesn't have"
                       "ccc";

        if (leAudioDevice->snk_audio_locations_hdls_.ccc_hdl != 0 &&
            !subscribe_for_notification(
                conn_id, leAudioDevice->address_,
                leAudioDevice->snk_audio_locations_hdls_)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        /* Obtain initial state of sink audio locations */
        BtaGattQueue::ReadCharacteristic(
            conn_id, leAudioDevice->snk_audio_locations_hdls_.val_hdl,
            OnGattReadRspStatic, NULL);

        LOG(INFO) << "Found Sink audio locations characteristic, handle: "
                  << loghex(charac.value_handle) << ", ccc handle: "
                  << loghex(leAudioDevice->snk_audio_locations_hdls_.ccc_hdl);
      } else if (charac.uuid ==
                 le_audio::uuid::kSourceAudioLocationCharacteristicUuid) {
        leAudioDevice->src_audio_locations_hdls_.val_hdl = charac.value_handle;
        leAudioDevice->src_audio_locations_hdls_.ccc_hdl =
            find_ccc_handle(charac);

        if (leAudioDevice->src_audio_locations_hdls_.ccc_hdl == 0)
          LOG(INFO) << __func__
                    << ", snk audio locations char doesn't have"
                       "ccc";

        if (leAudioDevice->src_audio_locations_hdls_.ccc_hdl != 0 &&
            !subscribe_for_notification(
                conn_id, leAudioDevice->address_,
                leAudioDevice->src_audio_locations_hdls_)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        /* Obtain initial state of source audio locations */
        BtaGattQueue::ReadCharacteristic(
            conn_id, leAudioDevice->src_audio_locations_hdls_.val_hdl,
            OnGattReadRspStatic, NULL);

        LOG(INFO) << "Found Source audio locations characteristic, handle: "
                  << loghex(charac.value_handle) << ", ccc handle: "
                  << loghex(leAudioDevice->src_audio_locations_hdls_.ccc_hdl);
      } else if (charac.uuid ==
                 le_audio::uuid::kAudioContextAvailabilityCharacteristicUuid) {
        leAudioDevice->audio_avail_hdls_.val_hdl = charac.value_handle;
        leAudioDevice->audio_avail_hdls_.ccc_hdl = find_ccc_handle(charac);

        if (leAudioDevice->audio_avail_hdls_.ccc_hdl == 0) {
          LOG(ERROR) << __func__ << ", audio avails char doesn't have ccc";

          DisconnectDevice(leAudioDevice);
          return;
        }

        if (!subscribe_for_notification(conn_id, leAudioDevice->address_,
                                        leAudioDevice->audio_avail_hdls_)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        /* Obtain initial state */
        BtaGattQueue::ReadCharacteristic(
            conn_id, leAudioDevice->audio_avail_hdls_.val_hdl,
            OnGattReadRspStatic, NULL);

        LOG(INFO) << "Found Audio Availability Context characteristic, handle: "
                  << loghex(charac.value_handle) << ", ccc handle: "
                  << loghex(leAudioDevice->audio_avail_hdls_.ccc_hdl);
      } else if (charac.uuid ==
                 le_audio::uuid::kAudioSupportedContextCharacteristicUuid) {
        leAudioDevice->audio_supp_cont_hdls_.val_hdl = charac.value_handle;
        leAudioDevice->audio_supp_cont_hdls_.ccc_hdl = find_ccc_handle(charac);

        if (leAudioDevice->audio_supp_cont_hdls_.ccc_hdl == 0)
          LOG(INFO) << __func__ << ", audio avails char doesn't have ccc";

        if (leAudioDevice->audio_supp_cont_hdls_.ccc_hdl != 0 &&
            !subscribe_for_notification(conn_id, leAudioDevice->address_,
                                        leAudioDevice->audio_supp_cont_hdls_)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        /* Obtain initial state */
        BtaGattQueue::ReadCharacteristic(
            conn_id, leAudioDevice->audio_supp_cont_hdls_.val_hdl,
            OnGattReadRspStatic, NULL);

        LOG(INFO) << "Found Audio Supported Context characteristic, handle: "
                  << loghex(charac.value_handle) << ", ccc handle: "
                  << loghex(leAudioDevice->audio_supp_cont_hdls_.ccc_hdl);
      }
    }

    /* Refresh ASE handles */
    leAudioDevice->ases_.clear();

    for (const gatt::Characteristic& charac : ase_svc->characteristics) {
      LOG(INFO) << "Found characteristic, uuid: " << charac.uuid.ToString();
      if (charac.uuid == le_audio::uuid::kSinkAudioStreamEndpointUuid ||
          charac.uuid == le_audio::uuid::kSourceAudioStreamEndpointUuid) {
        uint16_t ccc_handle = find_ccc_handle(charac);
        if (ccc_handle == 0) {
          LOG(ERROR) << __func__ << ", audio avails char doesn't have ccc";

          DisconnectDevice(leAudioDevice);
          return;
        }
        struct le_audio::types::hdl_pair hdls(charac.value_handle, ccc_handle);
        if (!subscribe_for_notification(conn_id, leAudioDevice->address_,
                                        hdls)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        int direction =
            charac.uuid == le_audio::uuid::kSinkAudioStreamEndpointUuid
                ? le_audio::types::kLeAudioDirectionSink
                : le_audio::types::kLeAudioDirectionSource;

        leAudioDevice->ases_.emplace_back(charac.value_handle, ccc_handle,
                                          direction);

        LOG(INFO) << "Found ASE characteristic, handle: "
                  << loghex(charac.value_handle)
                  << ", ccc handle: " << loghex(ccc_handle)
                  << ", direction: " << direction;
      } else if (charac.uuid ==
                 le_audio::uuid::
                     kAudioStreamEndpointControlPointCharacteristicUuid) {
        leAudioDevice->ctp_hdls_.val_hdl = charac.value_handle;
        leAudioDevice->ctp_hdls_.ccc_hdl = find_ccc_handle(charac);

        if (leAudioDevice->ctp_hdls_.ccc_hdl == 0) {
          LOG(ERROR) << __func__ << ", ase ctp doesn't have ccc";

          DisconnectDevice(leAudioDevice);
          return;
        }

        if (!subscribe_for_notification(conn_id, leAudioDevice->address_,
                                        leAudioDevice->ctp_hdls_)) {
          DisconnectDevice(leAudioDevice);
          return;
        }

        LOG(INFO) << "Found ASE Control Point characteristic, handle: "
                  << loghex(charac.value_handle) << ", ccc handle: "
                  << loghex(leAudioDevice->ctp_hdls_.ccc_hdl);
      }
    }

    if (tmas_svc) {
      for (const gatt::Characteristic& charac : tmas_svc->characteristics) {
        if (charac.uuid ==
            le_audio::uuid::kTelephonyMediaAudioProfileRoleCharacteristicUuid) {
          leAudioDevice->tmap_role_hdl_ = charac.value_handle;

          /* Obtain initial state of TMAP role */
          BtaGattQueue::ReadCharacteristic(conn_id,
                                           leAudioDevice->tmap_role_hdl_,
                                           OnGattReadRspStatic, NULL);

          LOG_INFO(
              ", Found Telephony and Media Profile characteristic, "
              "handle: %04x",
              leAudioDevice->tmap_role_hdl_);
        }
      }
    }

    leAudioDevice->known_service_handles_ = true;
    btif_storage_leaudio_update_handles_bin(leAudioDevice->address_);

    leAudioDevice->notify_connected_after_read_ = true;

    /* If already known group id */
    if (leAudioDevice->group_id_ != bluetooth::groups::kGroupUnknown) {
      AseInitialStateReadRequest(leAudioDevice);
      return;
    }

    /* If device does not belong to any group yet we either add it to the
     * group by our selfs now or wait for Csis to do it. In both cases, let's
     * check if group is already assigned.
     */
    int group_id = DeviceGroups::Get()->GetGroupId(
        leAudioDevice->address_, le_audio::uuid::kCapServiceUuid);
    if (group_id != bluetooth::groups::kGroupUnknown) {
      instance->group_add_node(group_id, leAudioDevice->address_);
      return;
    }

    /* CSIS will trigger adding to group */
    if (leAudioDevice->csis_member_) {
      LOG(INFO) << __func__ << " waiting for CSIS to create group for device "
                << leAudioDevice->address_;
      return;
    }

    /* If there is no Csis just add device by our own */
    DeviceGroups::Get()->AddDevice(leAudioDevice->address_,
                                   le_audio::uuid::kCapServiceUuid);
  }

  void OnGattWriteCcc(uint16_t conn_id, tGATT_STATUS status, uint16_t hdl,
                      void* data) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByConnId(conn_id);
    std::vector<struct ase>::iterator ase_it;

    if (!leAudioDevice) {
      LOG(ERROR) << __func__ << ", unknown conn_id=" << loghex(conn_id);
      return;
    }

    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      LOG_INFO("Database out of sync for %s, conn_id: 0x%04x",
               leAudioDevice->address_.ToString().c_str(), conn_id);
      ClearDeviceInformationAndStartSearch(leAudioDevice);
      return;
    }

    if (status == GATT_SUCCESS) {
      LOG(INFO) << __func__
                << ", successfully registered on ccc: " << loghex(hdl);

      if (leAudioDevice->ctp_hdls_.ccc_hdl == hdl &&
          leAudioDevice->known_service_handles_ &&
          !leAudioDevice->notify_connected_after_read_) {
        /* Reconnection case. Control point is the last CCC LeAudio is
         * registering for on reconnection */
        connectionReady(leAudioDevice);
      }

      return;
    }

    LOG(ERROR) << __func__
               << ", Failed to register for indications: " << loghex(hdl)
               << ", status: " << loghex((int)(status));

    ase_it =
        std::find_if(leAudioDevice->ases_.begin(), leAudioDevice->ases_.end(),
                     [&hdl](const struct ase& ase) -> bool {
                       return ase.hdls.ccc_hdl == hdl;
                     });

    if (ase_it == leAudioDevice->ases_.end()) {
      LOG(ERROR) << __func__
                 << ", unknown ccc handle: " << static_cast<int>(hdl);
      return;
    }

    BTA_GATTC_DeregisterForNotifications(gatt_if_, leAudioDevice->address_,
                                         ase_it->hdls.val_hdl);
  }

  void AttachToStreamingGroupIfNeeded(LeAudioDevice* leAudioDevice) {
    if (leAudioDevice->group_id_ != active_group_id_) {
      LOG(INFO) << __func__ << " group  " << leAudioDevice->group_id_
                << " is not streaming. Nothing to do";
      return;
    }

    LOG_INFO("Attaching to group: %d", leAudioDevice->group_id_);

    /* Restore configuration */
    LeAudioDeviceGroup* group = aseGroups_.FindById(active_group_id_);
    auto* stream_conf = &group->stream_conf;

    if (audio_sender_state_ == AudioState::IDLE &&
        audio_receiver_state_ == AudioState::IDLE) {
      DLOG(INFO) << __func__
                 << " Device not streaming but active - nothing to do";
      return;
    }

    if (!stream_conf->conf) {
      LOG_INFO("Configuration not yet set. Nothing to do now");
      return;
    }

    auto num_of_devices =
        get_num_of_devices_in_configuration(stream_conf->conf);

    if (num_of_devices < group->NumOfConnected() &&
        !group->IsConfigurationSupported(leAudioDevice, stream_conf->conf)) {
      /* Reconfigure if newly connected member device cannot support current
       * codec configuration */
      group->SetPendingConfiguration();
      groupStateMachine_->StopStream(group);
      stream_setup_start_timestamp_ =
          bluetooth::common::time_get_os_boottime_us();
      return;
    }

    if (!groupStateMachine_->AttachToStream(group, leAudioDevice)) {
      LOG_WARN("Could not add device %s to the group %d streaming. ",
               leAudioDevice->address_.ToString().c_str(), group->group_id_);
      scheduleAttachDeviceToTheStream(leAudioDevice->address_);
    } else {
      stream_setup_start_timestamp_ =
          bluetooth::common::time_get_os_boottime_us();
    }
  }

  void restartAttachToTheStream(const RawAddress& addr) {
    LeAudioDevice* leAudioDevice = leAudioDevices_.FindByAddress(addr);
    if (leAudioDevice == nullptr ||
        leAudioDevice->conn_id_ == GATT_INVALID_CONN_ID) {
      LOG_INFO("Device %s not available anymore", addr.ToString().c_str());
      return;
    }
    AttachToStreamingGroupIfNeeded(leAudioDevice);
  }

  void scheduleAttachDeviceToTheStream(const RawAddress& addr) {
    LOG_INFO("Device %s scheduler for stream ", addr.ToString().c_str());
    do_in_main_thread_delayed(
        FROM_HERE,
        base::BindOnce(&LeAudioClientImpl::restartAttachToTheStream,
                       base::Unretained(this), addr),
#if BASE_VER < 931007
        base::TimeDelta::FromMilliseconds(kDeviceAttachDelayMs)
#else
        base::Milliseconds(kDeviceAttachDelayMs)
#endif
    );
  }

  void connectionReady(LeAudioDevice* leAudioDevice) {
    LOG_DEBUG("%s,  %s", leAudioDevice->address_.ToString().c_str(),
              bluetooth::common::ToString(leAudioDevice->GetConnectionState())
                  .c_str());
    callbacks_->OnConnectionState(ConnectionState::CONNECTED,
                                  leAudioDevice->address_);

    if (leAudioDevice->GetConnectionState() ==
            DeviceConnectState::CONNECTED_BY_USER_GETTING_READY &&
        (leAudioDevice->autoconnect_flag_ == false)) {
      btif_storage_set_leaudio_autoconnect(leAudioDevice->address_, true);
      leAudioDevice->autoconnect_flag_ = true;
    }

    leAudioDevice->SetConnectionState(DeviceConnectState::CONNECTED);
    le_audio::MetricsCollector::Get()->OnConnectionStateChanged(
        leAudioDevice->group_id_, leAudioDevice->address_,
        ConnectionState::CONNECTED, le_audio::ConnectionStatus::SUCCESS);

    if (leAudioDevice->group_id_ != bluetooth::groups::kGroupUnknown) {
      LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);
      UpdateContextAndLocations(group, leAudioDevice);
      AttachToStreamingGroupIfNeeded(leAudioDevice);
    }
  }

  bool IsAseAcceptingAudioData(struct ase* ase) {
    if (ase == nullptr) return false;
    if (ase->state != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) return false;
    if (ase->data_path_state != AudioStreamDataPathState::DATA_PATH_ESTABLISHED)
      return false;

    return true;
  }

  // mix stero signal into mono
  std::vector<uint8_t> mono_blend(const std::vector<uint8_t>& buf,
                                  int bytes_per_sample, size_t frames) {
    std::vector<uint8_t> mono_out;
    mono_out.resize(frames * bytes_per_sample);

    if (bytes_per_sample == 2) {
      int16_t* out = (int16_t*)mono_out.data();
      const int16_t* in = (int16_t*)(buf.data());
      for (size_t i = 0; i < frames; ++i) {
        int accum = 0;
        accum += *in++;
        accum += *in++;
        accum /= 2;  // round to 0
        *out++ = accum;
      }
    } else if (bytes_per_sample == 4) {
      int32_t* out = (int32_t*)mono_out.data();
      const int32_t* in = (int32_t*)(buf.data());
      for (size_t i = 0; i < frames; ++i) {
        int accum = 0;
        accum += *in++;
        accum += *in++;
        accum /= 2;  // round to 0
        *out++ = accum;
      }
    } else {
      LOG_ERROR("Don't know how to mono blend that %d!", bytes_per_sample);
    }
    return mono_out;
  }

  void PrepareAndSendToTwoCises(
      const std::vector<uint8_t>& data,
      struct le_audio::stream_configuration* stream_conf) {
    uint16_t byte_count = stream_conf->sink_octets_per_codec_frame;
    uint16_t left_cis_handle = 0;
    uint16_t right_cis_handle = 0;
    uint16_t number_of_required_samples_per_channel;

    int dt_us = current_source_codec_config.data_interval_us;
    int af_hz = audio_framework_source_config.sample_rate;
    number_of_required_samples_per_channel = lc3_frame_samples(dt_us, af_hz);

    lc3_pcm_format bits_per_sample =
        bits_to_lc3_bits(audio_framework_source_config.bits_per_sample);
    uint8_t bytes_per_sample =
        bits_to_bytes_per_sample(audio_framework_source_config.bits_per_sample);

    for (auto [cis_handle, audio_location] : stream_conf->sink_streams) {
      if (audio_location & le_audio::codec_spec_conf::kLeAudioLocationAnyLeft)
        left_cis_handle = cis_handle;
      if (audio_location & le_audio::codec_spec_conf::kLeAudioLocationAnyRight)
        right_cis_handle = cis_handle;
    }

    if (data.size() < bytes_per_sample * 2 /* channels */ *
                          number_of_required_samples_per_channel) {
      LOG(ERROR) << __func__ << " Missing samples. Data size: " << +data.size()
                 << " expected: "
                 << bytes_per_sample * 2 *
                        number_of_required_samples_per_channel;
      return;
    }

    std::vector<uint8_t> chan_left_enc(byte_count, 0);
    std::vector<uint8_t> chan_right_enc(byte_count, 0);

    bool mono = (left_cis_handle == 0) || (right_cis_handle == 0);

    if (!mono) {
      lc3_encode(lc3_encoder_left, bits_per_sample, data.data(), 2,
                 chan_left_enc.size(), chan_left_enc.data());
      lc3_encode(lc3_encoder_right, bits_per_sample,
                 data.data() + bytes_per_sample, 2, chan_right_enc.size(),
                 chan_right_enc.data());
    } else {
      std::vector<uint8_t> mono = mono_blend(
          data, bytes_per_sample, number_of_required_samples_per_channel);
      if (left_cis_handle) {
        lc3_encode(lc3_encoder_left, bits_per_sample, mono.data(), 1,
                   chan_left_enc.size(), chan_left_enc.data());
      }

      if (right_cis_handle) {
        lc3_encode(lc3_encoder_right, bits_per_sample, mono.data(), 1,
                   chan_right_enc.size(), chan_right_enc.data());
      }
    }

    DLOG(INFO) << __func__ << " left_cis_handle: " << +left_cis_handle
               << " right_cis_handle: " << right_cis_handle;
    /* Send data to the controller */
    if (left_cis_handle)
      IsoManager::GetInstance()->SendIsoData(
          left_cis_handle, chan_left_enc.data(), chan_left_enc.size());

    if (right_cis_handle)
      IsoManager::GetInstance()->SendIsoData(
          right_cis_handle, chan_right_enc.data(), chan_right_enc.size());
  }

  void PrepareAndSendToSingleCis(
      const std::vector<uint8_t>& data,
      struct le_audio::stream_configuration* stream_conf) {
    int num_channels = stream_conf->sink_num_of_channels;
    uint16_t byte_count = stream_conf->sink_octets_per_codec_frame;
    auto cis_handle = stream_conf->sink_streams.front().first;
    uint16_t number_of_required_samples_per_channel;

    int dt_us = current_source_codec_config.data_interval_us;
    int af_hz = audio_framework_source_config.sample_rate;
    number_of_required_samples_per_channel = lc3_frame_samples(dt_us, af_hz);
    lc3_pcm_format bits_per_sample =
        bits_to_lc3_bits(audio_framework_source_config.bits_per_sample);
    uint8_t bytes_per_sample =
        bits_to_bytes_per_sample(audio_framework_source_config.bits_per_sample);

    if ((int)data.size() < (2 /* bytes per sample */ * num_channels *
                            number_of_required_samples_per_channel)) {
      LOG(ERROR) << __func__ << "Missing samples";
      return;
    }
    std::vector<uint8_t> chan_encoded(num_channels * byte_count, 0);

    if (num_channels == 1) {
      /* Since we always get two channels from framework, lets make it mono here
       */
      std::vector<uint8_t> mono = mono_blend(
          data, bytes_per_sample, number_of_required_samples_per_channel);

      auto err = lc3_encode(lc3_encoder_left, bits_per_sample, mono.data(), 1,
                            byte_count, chan_encoded.data());

      if (err < 0) {
        LOG(ERROR) << " error while encoding, error code: " << +err;
      }
    } else {
      lc3_encode(lc3_encoder_left, bits_per_sample, (const int16_t*)data.data(),
                 2, byte_count, chan_encoded.data());
      lc3_encode(lc3_encoder_right, bits_per_sample,
                 (const int16_t*)data.data() + 1, 2, byte_count,
                 chan_encoded.data() + byte_count);
    }

    /* Send data to the controller */
    IsoManager::GetInstance()->SendIsoData(cis_handle, chan_encoded.data(),
                                           chan_encoded.size());
  }

  const struct le_audio::stream_configuration* GetStreamSinkConfiguration(
      LeAudioDeviceGroup* group) {
    const struct le_audio::stream_configuration* stream_conf =
        &group->stream_conf;
    LOG_INFO("group_id: %d", group->group_id_);
    if (stream_conf->sink_streams.size() == 0) {
      return nullptr;
    }

    LOG_INFO("configuration: %s", stream_conf->conf->name.c_str());
    return stream_conf;
  }

  void OnAudioDataReady(const std::vector<uint8_t>& data) {
    if ((active_group_id_ == bluetooth::groups::kGroupUnknown) ||
        (audio_sender_state_ != AudioState::STARTED))
      return;

    LeAudioDeviceGroup* group = aseGroups_.FindById(active_group_id_);
    if (!group) {
      LOG(ERROR) << __func__ << "There is no streaming group available";
      return;
    }

    auto stream_conf = group->stream_conf;
    if ((stream_conf.sink_num_of_devices > 2) ||
        (stream_conf.sink_num_of_devices == 0) ||
        stream_conf.sink_streams.empty()) {
      LOG(ERROR) << __func__ << " Stream configufation is not valid.";
      return;
    }

    if (stream_conf.sink_num_of_devices == 2) {
      PrepareAndSendToTwoCises(data, &stream_conf);
    } else if (stream_conf.sink_streams.size() == 2) {
      /* Streaming to one device but 2 CISes */
      PrepareAndSendToTwoCises(data, &stream_conf);
    } else {
      PrepareAndSendToSingleCis(data, &stream_conf);
    }
  }

  void CleanCachedMicrophoneData() {
    cached_channel_data_.clear();
    cached_channel_timestamp_ = 0;
    cached_channel_is_left_ = false;
  }

  /* Handles audio data packets coming from the controller */
  void HandleIncomingCisData(uint8_t* data, uint16_t size,
                             uint16_t cis_conn_hdl, uint32_t timestamp) {
    /* Get only one channel for MONO microphone */
    /* Gather data for channel */
    if ((active_group_id_ == bluetooth::groups::kGroupUnknown) ||
        (audio_receiver_state_ != AudioState::STARTED))
      return;

    LeAudioDeviceGroup* group = aseGroups_.FindById(active_group_id_);
    if (!group) {
      LOG(ERROR) << __func__ << "There is no streaming group available";
      return;
    }

    auto stream_conf = group->stream_conf;

    uint16_t left_cis_handle = 0;
    uint16_t right_cis_handle = 0;
    for (auto [cis_handle, audio_location] : stream_conf.source_streams) {
      if (audio_location & le_audio::codec_spec_conf::kLeAudioLocationAnyLeft) {
        left_cis_handle = cis_handle;
      }
      if (audio_location &
          le_audio::codec_spec_conf::kLeAudioLocationAnyRight) {
        right_cis_handle = cis_handle;
      }
    }

    bool is_left = true;
    if (cis_conn_hdl == left_cis_handle) {
      is_left = true;
    } else if (cis_conn_hdl == right_cis_handle) {
      is_left = false;
    } else {
      LOG_ERROR("Received data for unknown handle: %04x", cis_conn_hdl);
      return;
    }

    uint16_t required_for_channel_byte_count =
        stream_conf.source_octets_per_codec_frame;

    int dt_us = current_sink_codec_config.data_interval_us;
    int af_hz = audio_framework_sink_config.sample_rate;
    lc3_pcm_format bits_per_sample =
        bits_to_lc3_bits(audio_framework_sink_config.bits_per_sample);

    int pcm_size;
    if (dt_us == 10000) {
      if (af_hz == 44100)
        pcm_size = 480;
      else
        pcm_size = af_hz / 100;
    } else if (dt_us == 7500) {
      if (af_hz == 44100)
        pcm_size = 360;
      else
        pcm_size = (af_hz * 3) / 400;
    } else {
      LOG(ERROR) << "BAD dt_us: " << dt_us;
      return;
    }

    std::vector<int16_t> pcm_data_decoded(pcm_size, 0);

    int err = 0;

    if (required_for_channel_byte_count != size) {
      LOG(INFO) << "Insufficient data for decoding and send, required: "
                << int(required_for_channel_byte_count)
                << ", received: " << int(size) << ", will do PLC";
      size = 0;
      data = nullptr;
    }

    lc3_decoder_t decoder_to_use =
        is_left ? lc3_decoder_left : lc3_decoder_right;

    err = lc3_decode(decoder_to_use, data, size, bits_per_sample,
                     pcm_data_decoded.data(), 1 /* pitch */);

    if (err < 0) {
      LOG(ERROR) << " bad decoding parameters: " << static_cast<int>(err);
      return;
    }

    /* AF == Audio Framework */
    bool af_is_stereo = (audio_framework_sink_config.num_channels == 2);

    if (!left_cis_handle || !right_cis_handle) {
      /* mono or just one device connected */
      SendAudioDataToAF(false /* bt_got_stereo */, af_is_stereo,
                        &pcm_data_decoded, nullptr);
      return;
    }
    /* both devices are connected */

    if (cached_channel_timestamp_ == 0 && cached_channel_data_.empty()) {
      /* First packet received, cache it. We need both channel data to send it
       * to AF. */
      cached_channel_data_ = pcm_data_decoded;
      cached_channel_timestamp_ = timestamp;
      cached_channel_is_left_ = is_left;
      return;
    }

    /* We received either data for the other audio channel, or another
     * packet for same channel */

    if (cached_channel_is_left_ != is_left) {
      /* It's data for the 2nd channel */
      if (timestamp == cached_channel_timestamp_) {
        /* Ready to mix data and send out to AF */
        if (is_left) {
          SendAudioDataToAF(true /* bt_got_stereo */, af_is_stereo,
                            &cached_channel_data_, &pcm_data_decoded);
        } else {
          SendAudioDataToAF(true /* bt_got_stereo */, af_is_stereo,
                            &pcm_data_decoded, &cached_channel_data_);
        }

        CleanCachedMicrophoneData();
        return;
      }

      /* 2nd Channel is in the future compared to the cached data.
       Send the cached data to AF, and keep the new channel data in cache.
       This should happen only during stream setup */

      if (cached_channel_is_left_) {
        SendAudioDataToAF(false /* bt_got_stereo */, af_is_stereo,
                          &cached_channel_data_, nullptr);
      } else {
        SendAudioDataToAF(false /* bt_got_stereo */, af_is_stereo, nullptr,
                          &cached_channel_data_);
      }

      cached_channel_data_ = pcm_data_decoded;
      cached_channel_timestamp_ = timestamp;
      cached_channel_is_left_ = is_left;
      return;
    }

    /* Data for same channel received. 2nd channel is down/not sending
     * data */

    /* Send the cached data out */
    if (cached_channel_is_left_) {
      SendAudioDataToAF(false /* bt_got_stereo */, af_is_stereo,
                        &cached_channel_data_, nullptr);
    } else {
      SendAudioDataToAF(false /* bt_got_stereo */, af_is_stereo, nullptr,
                        &cached_channel_data_);
    }

    /* Cache the data in case 2nd channel connects */
    cached_channel_data_ = pcm_data_decoded;
    cached_channel_timestamp_ = timestamp;
    cached_channel_is_left_ = is_left;
  }

  void SendAudioDataToAF(bool bt_got_stereo, bool af_is_stereo,
                         std::vector<int16_t>* left,
                         std::vector<int16_t>* right) {
    uint16_t to_write = 0;
    uint16_t written = 0;
    if (!af_is_stereo) {
      if (!bt_got_stereo) {
        std::vector<int16_t>* mono = left ? left : right;
        /* mono audio over bluetooth, audio framework expects mono */
        to_write = sizeof(int16_t) * mono->size();
        written = le_audio_sink_hal_client_->SendData((uint8_t*)mono->data(),
                                                      to_write);
      } else {
        /* stereo audio over bluetooth, audio framework expects mono */
        for (size_t i = 0; i < left->size(); i++) {
          (*left)[i] = ((*left)[i] + (*right)[i]) / 2;
        }
        to_write = sizeof(int16_t) * left->size();
        written = le_audio_sink_hal_client_->SendData((uint8_t*)left->data(),
                                                      to_write);
      }
    } else {
      /* mono audio over bluetooth, audio framework expects stereo
       * Here we handle stream without checking bt_got_stereo flag.
       */
      const size_t mono_size = left ? left->size() : right->size();
      std::vector<uint16_t> mixed(mono_size * 2);

      for (size_t i = 0; i < mono_size; i++) {
        mixed[2 * i] = left ? (*left)[i] : (*right)[i];
        mixed[2 * i + 1] = right ? (*right)[i] : (*left)[i];
      }
      to_write = sizeof(int16_t) * mixed.size();
      written =
          le_audio_sink_hal_client_->SendData((uint8_t*)mixed.data(), to_write);
    }

    /* TODO: What to do if not all data sinked ? */
    if (written != to_write) LOG(ERROR) << __func__ << ", not all data sinked";
  }

  bool StartSendingAudio(int group_id) {
    LOG(INFO) << __func__;

    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);
    LeAudioDevice* device = group->GetFirstActiveDevice();
    LOG_ASSERT(device) << __func__
                       << " Shouldn't be called without an active device.";

    /* Assume 2 ases max just for now. */
    auto* stream_conf = GetStreamSinkConfiguration(group);
    if (stream_conf == nullptr) {
      LOG(ERROR) << __func__ << " could not get sink configuration";
      return false;
    }

    LOG_DEBUG("Sink stream config (#%d):\n",
              static_cast<int>(stream_conf->sink_streams.size()));
    for (auto stream : stream_conf->sink_streams) {
      LOG_DEBUG("Cis handle: 0x%02x, allocation 0x%04x\n", stream.first,
                stream.second);
    }
    LOG_DEBUG("Source stream config (#%d):\n",
              static_cast<int>(stream_conf->source_streams.size()));
    for (auto stream : stream_conf->source_streams) {
      LOG_DEBUG("Cis handle: 0x%02x, allocation 0x%04x\n", stream.first,
                stream.second);
    }

    uint16_t remote_delay_ms =
        group->GetRemoteDelay(le_audio::types::kLeAudioDirectionSink);
    if (CodecManager::GetInstance()->GetCodecLocation() ==
        le_audio::types::CodecLocation::HOST) {
      if (lc3_encoder_left_mem) {
        LOG(WARNING)
            << " The encoder instance should have been already released.";
        free(lc3_encoder_left_mem);
        lc3_encoder_left_mem = nullptr;
        free(lc3_encoder_right_mem);
        lc3_encoder_right_mem = nullptr;
      }
      int dt_us = current_source_codec_config.data_interval_us;
      int sr_hz = current_source_codec_config.sample_rate;
      int af_hz = audio_framework_source_config.sample_rate;
      unsigned enc_size = lc3_encoder_size(dt_us, af_hz);

      lc3_encoder_left_mem = malloc(enc_size);
      lc3_encoder_right_mem = malloc(enc_size);

      lc3_encoder_left =
          lc3_setup_encoder(dt_us, sr_hz, af_hz, lc3_encoder_left_mem);
      lc3_encoder_right =
          lc3_setup_encoder(dt_us, sr_hz, af_hz, lc3_encoder_right_mem);
    }

    le_audio_source_hal_client_->UpdateRemoteDelay(remote_delay_ms);
    le_audio_source_hal_client_->ConfirmStreamingRequest();
    audio_sender_state_ = AudioState::STARTED;
    /* We update the target audio allocation before streamStarted that the
     * offloder would know how to configure offloader encoder. We should check
     * if we need to update the current
     * allocation here as the target allocation and the current allocation is
     * different */
    updateOffloaderIfNeeded(group);

    return true;
  }

  const struct le_audio::stream_configuration* GetStreamSourceConfiguration(
      LeAudioDeviceGroup* group) {
    const struct le_audio::stream_configuration* stream_conf =
        &group->stream_conf;
    if (stream_conf->source_streams.size() == 0) {
      return nullptr;
    }
    LOG_INFO("configuration: %s", stream_conf->conf->name.c_str());
    return stream_conf;
  }

  void StartReceivingAudio(int group_id) {
    LOG(INFO) << __func__;

    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);

    auto* stream_conf = GetStreamSourceConfiguration(group);
    if (!stream_conf) {
      LOG(WARNING) << " Could not get source configuration for group "
                   << active_group_id_ << " probably microphone not configured";
      return;
    }

    uint16_t remote_delay_ms =
        group->GetRemoteDelay(le_audio::types::kLeAudioDirectionSource);

    CleanCachedMicrophoneData();

    if (CodecManager::GetInstance()->GetCodecLocation() ==
        le_audio::types::CodecLocation::HOST) {
      if (lc3_decoder_left_mem) {
        LOG(WARNING)
            << " The decoder instance should have been already released.";
        free(lc3_decoder_left_mem);
        lc3_decoder_left_mem = nullptr;
        free(lc3_decoder_right_mem);
        lc3_decoder_right_mem = nullptr;
      }

      int dt_us = current_sink_codec_config.data_interval_us;
      int sr_hz = current_sink_codec_config.sample_rate;
      int af_hz = audio_framework_sink_config.sample_rate;
      unsigned dec_size = lc3_decoder_size(dt_us, af_hz);
      lc3_decoder_left_mem = malloc(dec_size);
      lc3_decoder_right_mem = malloc(dec_size);

      lc3_decoder_left =
          lc3_setup_decoder(dt_us, sr_hz, af_hz, lc3_decoder_left_mem);
      lc3_decoder_right =
          lc3_setup_decoder(dt_us, sr_hz, af_hz, lc3_decoder_right_mem);
    }
    le_audio_sink_hal_client_->UpdateRemoteDelay(remote_delay_ms);
    le_audio_sink_hal_client_->ConfirmStreamingRequest();
    audio_receiver_state_ = AudioState::STARTED;
    /* We update the target audio allocation before streamStarted that the
     * offloder would know how to configure offloader decoder. We should check
     * if we need to update the current
     * allocation here as the target allocation and the current allocation is
     * different */
    updateOffloaderIfNeeded(group);
  }

  void SuspendAudio(void) {
    CancelStreamingRequest();

    if (lc3_encoder_left_mem) {
      free(lc3_encoder_left_mem);
      lc3_encoder_left_mem = nullptr;
      free(lc3_encoder_right_mem);
      lc3_encoder_right_mem = nullptr;
    }

    if (lc3_decoder_left_mem) {
      free(lc3_decoder_left_mem);
      lc3_decoder_left_mem = nullptr;
      free(lc3_decoder_right_mem);
      lc3_decoder_right_mem = nullptr;
    }
  }

  void StopAudio(void) { SuspendAudio(); }

  void printSingleConfiguration(int fd, LeAudioCodecConfiguration* conf,
                                bool print_audio_state, bool sender = false) {
    std::stringstream stream;
    if (print_audio_state) {
      if (sender) {
        stream << "\taudio sender state: " << audio_sender_state_ << "\n";
      } else {
        stream << "\taudio receiver state: " << audio_receiver_state_ << "\n";
      }
    }

    stream << "\tsample rate: " << +conf->sample_rate
           << ",\tchan: " << +conf->num_channels
           << ",\tbits: " << +conf->bits_per_sample
           << ",\tdata_interval_us: " << +conf->data_interval_us << "\n";

    dprintf(fd, "%s", stream.str().c_str());
  }

  void printCurrentStreamConfiguration(int fd) {
    auto conf = &audio_framework_source_config;
    dprintf(fd, " Speaker codec config (audio framework) \n");
    if (conf) {
      printSingleConfiguration(fd, conf, false);
    }

    dprintf(fd, " Microphone codec config (audio framework) \n");
    conf = &audio_framework_sink_config;
    if (conf) {
      printSingleConfiguration(fd, conf, false);
    }

    conf = &current_source_codec_config;
    dprintf(fd, " Speaker codec config (Bluetooth)\n");
    if (conf) {
      printSingleConfiguration(fd, conf, true, true);
    }

    conf = &current_sink_codec_config;
    dprintf(fd, " Microphone codec config (Bluetooth)\n");
    if (conf) {
      printSingleConfiguration(fd, conf, true, false);
    }
  }

  void Dump(int fd) {
    dprintf(fd, "  Active group: %d\n", active_group_id_);
    dprintf(fd, "    reconnection mode: %s \n",
            (reconnection_mode_ == BTM_BLE_BKG_CONNECT_ALLOW_LIST
                 ? " Allow List"
                 : " Targeted Announcements"));
    dprintf(fd, "    configuration: %s  (0x%08hx)\n",
            bluetooth::common::ToString(configuration_context_type_).c_str(),
            configuration_context_type_);
    dprintf(fd, "    source metadata context type mask: %s\n",
            metadata_context_types_.source.to_string().c_str());
    dprintf(fd, "    sink metadata context type mask: %s\n",
            metadata_context_types_.sink.to_string().c_str());
    dprintf(fd, "    TBS state: %s\n", in_call_ ? " In call" : "No calls");
    dprintf(fd, "    Start time: ");
    for (auto t : stream_start_history_queue_) {
      dprintf(fd, ", %d ms", static_cast<int>(t));
    }
    dprintf(fd, "\n");
    printCurrentStreamConfiguration(fd);
    dprintf(fd, "  ----------------\n ");
    dprintf(fd, "  LE Audio Groups:\n");
    aseGroups_.Dump(fd, active_group_id_);
    dprintf(fd, "\n  Not grouped devices:\n");
    leAudioDevices_.Dump(fd, bluetooth::groups::kGroupUnknown);
  }

  void Cleanup(base::Callback<void()> cleanupCb) {
    StopVbcCloseTimeout();
    if (alarm_is_scheduled(suspend_timeout_)) alarm_cancel(suspend_timeout_);

    if (active_group_id_ != bluetooth::groups::kGroupUnknown) {
      /* Bluetooth turned off while streaming */
      StopAudio();
      ClientAudioIntefraceRelease();
    }
    groupStateMachine_->Cleanup();
    aseGroups_.Cleanup();
    leAudioDevices_.Cleanup(gatt_if_);
    if (gatt_if_) BTA_GATTC_AppDeregister(gatt_if_);

    std::move(cleanupCb).Run();
  }

  AudioReconfigurationResult UpdateConfigAndCheckIfReconfigurationIsNeeded(
      int group_id, LeAudioContextType context_type) {
    bool reconfiguration_needed = false;
    bool sink_cfg_available = true;
    bool source_cfg_available = true;

    LOG_DEBUG("Checking whether to reconfigure from %s to %s",
              ToString(configuration_context_type_).c_str(),
              ToString(context_type).c_str());

    auto group = aseGroups_.FindById(group_id);
    if (!group) {
      LOG(ERROR) << __func__
                 << ", Invalid group: " << static_cast<int>(group_id);
      return AudioReconfigurationResult::RECONFIGURATION_NOT_NEEDED;
    }

    std::optional<LeAudioCodecConfiguration> source_configuration =
        group->GetCodecConfigurationByDirection(
            context_type, le_audio::types::kLeAudioDirectionSink);
    std::optional<LeAudioCodecConfiguration> sink_configuration =
        group->GetCodecConfigurationByDirection(
            context_type, le_audio::types::kLeAudioDirectionSource);

    if (source_configuration) {
      if (*source_configuration != current_source_codec_config) {
        current_source_codec_config = *source_configuration;
        reconfiguration_needed = true;
      }
    } else {
      if (!current_source_codec_config.IsInvalid()) {
        current_source_codec_config = {0, 0, 0, 0};
        reconfiguration_needed = true;
      }
      source_cfg_available = false;
    }

    if (sink_configuration) {
      if (*sink_configuration != current_sink_codec_config) {
        current_sink_codec_config = *sink_configuration;
        reconfiguration_needed = true;
      }
    } else {
      if (!current_sink_codec_config.IsInvalid()) {
        current_sink_codec_config = {0, 0, 0, 0};
        reconfiguration_needed = true;
      }

      sink_cfg_available = false;
    }

    LOG_DEBUG(
        " Context: %s Reconfiguration_needed = %d, sink_cfg_available = %d, "
        "source_cfg_available = %d",
        ToString(context_type).c_str(), reconfiguration_needed,
        sink_cfg_available, source_cfg_available);

    if (!reconfiguration_needed) {
      return AudioReconfigurationResult::RECONFIGURATION_NOT_NEEDED;
    }

    if (!sink_cfg_available && !source_cfg_available) {
      return AudioReconfigurationResult::RECONFIGURATION_NOT_POSSIBLE;
    }

    LOG_INFO(" Session reconfiguration needed group: %d for context type: %s",
             group->group_id_, ToHexString(context_type).c_str());

    configuration_context_type_ = context_type;
    return AudioReconfigurationResult::RECONFIGURATION_NEEDED;
  }

  bool OnAudioResume(LeAudioDeviceGroup* group) {
    if (group->GetTargetState() == AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
      return true;
    }
    return GroupStream(active_group_id_, configuration_context_type_,
                       get_bidirectional(metadata_context_types_));
  }

  void OnAudioSuspend() {
    if (active_group_id_ == bluetooth::groups::kGroupUnknown) {
      LOG(WARNING) << ", there is no longer active group";
      return;
    }

    if (stack_config_get_interface()
            ->get_pts_le_audio_disable_ases_before_stopping()) {
      LOG_INFO("Stream disable_timer_ started");
      if (alarm_is_scheduled(disable_timer_)) alarm_cancel(disable_timer_);

      alarm_set_on_mloop(
          disable_timer_, kAudioDisableTimeoutMs,
          [](void* data) {
            if (instance) instance->GroupSuspend(PTR_TO_INT(data));
          },
          INT_TO_PTR(active_group_id_));
    }

    /* Group should tie in time to get requested status */
    uint64_t timeoutMs = kAudioSuspentKeepIsoAliveTimeoutMs;
    timeoutMs = osi_property_get_int32(kAudioSuspentKeepIsoAliveTimeoutMsProp,
                                       timeoutMs);

    if (stack_config_get_interface()
           ->get_pts_le_audio_disable_ases_before_stopping()) {
        timeoutMs += kAudioDisableTimeoutMs;
    }

    LOG_DEBUG("Stream suspend_timeout_ started: %d ms",
              static_cast<int>(timeoutMs));
    if (alarm_is_scheduled(suspend_timeout_)) alarm_cancel(suspend_timeout_);

    alarm_set_on_mloop(
        suspend_timeout_, timeoutMs,
        [](void* data) {
          if (instance) instance->GroupStop(PTR_TO_INT(data));
        },
        INT_TO_PTR(active_group_id_));
  }

  void OnLocalAudioSourceSuspend() {
    LOG_INFO("IN: audio_receiver_state_: %s,  audio_sender_state_: %s",
             ToString(audio_receiver_state_).c_str(),
             ToString(audio_sender_state_).c_str());

    /* Note: This callback is from audio hal driver.
     * Bluetooth peer is a Sink for Audio Framework.
     * e.g. Peer is a speaker
     */
    switch (audio_sender_state_) {
      case AudioState::READY_TO_START:
      case AudioState::STARTED:
        audio_sender_state_ = AudioState::READY_TO_RELEASE;
        break;
      case AudioState::RELEASING:
        return;
      case AudioState::IDLE:
        if (audio_receiver_state_ == AudioState::READY_TO_RELEASE) {
          OnAudioSuspend();
        }
        return;
      case AudioState::READY_TO_RELEASE:
        break;
    }

    /* Last suspends group - triggers group stop */
    if ((audio_receiver_state_ == AudioState::IDLE) ||
        (audio_receiver_state_ == AudioState::READY_TO_RELEASE)) {
      OnAudioSuspend();
      le_audio::MetricsCollector::Get()->OnStreamEnded(active_group_id_);
    }

    LOG_INFO("OUT: audio_receiver_state_: %s,  audio_sender_state_: %s",
             ToString(audio_receiver_state_).c_str(),
             ToString(audio_sender_state_).c_str());
  }

  void OnLocalAudioSourceResume() {
    LOG_INFO("IN: audio_receiver_state_: %s,  audio_sender_state_: %s",
             ToString(audio_receiver_state_).c_str(),
             ToString(audio_sender_state_).c_str());
    /* Note: This callback is from audio hal driver.
     * Bluetooth peer is a Sink for Audio Framework.
     * e.g. Peer is a speaker
     */
    auto group = aseGroups_.FindById(active_group_id_);
    if (!group) {
      LOG(ERROR) << __func__
                 << ", Invalid group: " << static_cast<int>(active_group_id_);
      return;
    }

    /* Check if the device resume is expected */
    if (!group->GetCodecConfigurationByDirection(
            configuration_context_type_,
            le_audio::types::kLeAudioDirectionSink)) {
      LOG(ERROR) << __func__ << ", invalid resume request for context type: "
                 << ToHexString(configuration_context_type_);
      le_audio_source_hal_client_->CancelStreamingRequest();
      return;
    }

    DLOG(INFO) << __func__ << " active_group_id: " << active_group_id_ << "\n"
               << " audio_receiver_state: " << audio_receiver_state_ << "\n"
               << " audio_sender_state: " << audio_sender_state_ << "\n"
               << " configuration_context_type_: "
               << ToHexString(configuration_context_type_) << "\n"
               << " group " << (group ? " exist " : " does not exist ") << "\n";

    switch (audio_sender_state_) {
      case AudioState::STARTED:
        /* Looks like previous Confirm did not get to the Audio Framework*/
        le_audio_source_hal_client_->ConfirmStreamingRequest();
        break;
      case AudioState::IDLE:
        switch (audio_receiver_state_) {
          case AudioState::IDLE:
            /* Stream is not started. Try to do it.*/
            if (OnAudioResume(group)) {
              audio_sender_state_ = AudioState::READY_TO_START;
            } else {
              le_audio_source_hal_client_->CancelStreamingRequest();
            }
            break;
          case AudioState::READY_TO_START:
          case AudioState::STARTED:
            audio_sender_state_ = AudioState::READY_TO_START;
            /* If signalling part is completed trigger start receiving audio
             * here, otherwise it'll be called on group streaming state callback
             */
            if (group->GetState() ==
                AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
              StartSendingAudio(active_group_id_);
            }
            break;
          case AudioState::RELEASING:
            /* Group is reconfiguring, reassing state and wait for
             * the stream to be configured
             */
            audio_sender_state_ = audio_receiver_state_;
            break;
          case AudioState::READY_TO_RELEASE:
            LOG_WARN(
                " called in wrong state. \n audio_receiver_state: %s \n"
                "audio_sender_state: %s \n",
                ToString(audio_receiver_state_).c_str(),
                ToString(audio_sender_state_).c_str());
            CancelStreamingRequest();
            break;
        }
        break;
      case AudioState::READY_TO_START:
        LOG_WARN(
            " called in wrong state. \n audio_receiver_state: %s \n"
            "audio_sender_state: %s \n",
            ToString(audio_receiver_state_).c_str(),
            ToString(audio_sender_state_).c_str());
        CancelStreamingRequest();
        break;
      case AudioState::READY_TO_RELEASE:
        switch (audio_receiver_state_) {
          case AudioState::STARTED:
          case AudioState::READY_TO_START:
          case AudioState::IDLE:
          case AudioState::READY_TO_RELEASE:
            /* Stream is up just restore it */
            audio_sender_state_ = AudioState::STARTED;
            if (alarm_is_scheduled(suspend_timeout_))
              alarm_cancel(suspend_timeout_);
            le_audio_source_hal_client_->ConfirmStreamingRequest();
            le_audio::MetricsCollector::Get()->OnStreamStarted(
                active_group_id_, configuration_context_type_);
            break;
          case AudioState::RELEASING:
            /* Keep wainting. After release is done, Audio Hal will be notified
             */
            break;
        }
        break;
      case AudioState::RELEASING:
        /* Keep wainting. After release is done, Audio Hal will be notified */
        break;
    }
  }

  void OnLocalAudioSinkSuspend() {
    LOG_INFO("IN: audio_receiver_state_: %s,  audio_sender_state_: %s",
             ToString(audio_receiver_state_).c_str(),
             ToString(audio_sender_state_).c_str());

    StartVbcCloseTimeout();

    /* Note: This callback is from audio hal driver.
     * Bluetooth peer is a Source for Audio Framework.
     * e.g. Peer is microphone.
     */
    switch (audio_receiver_state_) {
      case AudioState::READY_TO_START:
      case AudioState::STARTED:
        audio_receiver_state_ = AudioState::READY_TO_RELEASE;
        break;
      case AudioState::RELEASING:
        return;
      case AudioState::IDLE:
        if (audio_sender_state_ == AudioState::READY_TO_RELEASE) {
          OnAudioSuspend();
        }
        return;
      case AudioState::READY_TO_RELEASE:
        break;
    }

    /* Last suspends group - triggers group stop */
    if ((audio_sender_state_ == AudioState::IDLE) ||
        (audio_sender_state_ == AudioState::READY_TO_RELEASE))
      OnAudioSuspend();

    LOG_INFO("OUT: audio_receiver_state_: %s,  audio_sender_state_: %s",
             ToString(audio_receiver_state_).c_str(),
             ToString(audio_sender_state_).c_str());
  }

  inline bool IsDirectionAvailableForCurrentConfiguration(
      const LeAudioDeviceGroup* group, uint8_t direction) const {
    return group
        ->GetCodecConfigurationByDirection(configuration_context_type_,
                                           direction)
        .has_value();
  }

  void OnLocalAudioSinkResume() {
    LOG_INFO("IN: audio_receiver_state_: %s,  audio_sender_state_: %s",
             ToString(audio_receiver_state_).c_str(),
             ToString(audio_sender_state_).c_str());
    /* Stop the VBC close watchdog if needed */
    StopVbcCloseTimeout();

    /* Note: This callback is from audio hal driver.
     * Bluetooth peer is a Source for Audio Framework.
     * e.g. Peer is microphone.
     */
    auto group = aseGroups_.FindById(active_group_id_);
    if (!group) {
      LOG(ERROR) << __func__
                 << ", Invalid group: " << static_cast<int>(active_group_id_);
      return;
    }

    /* Check if the device resume is expected */
    if (!group->GetCodecConfigurationByDirection(
            configuration_context_type_,
            le_audio::types::kLeAudioDirectionSource)) {
      LOG(ERROR) << __func__ << ", invalid resume request for context type: "
                 << ToHexString(configuration_context_type_);
      le_audio_sink_hal_client_->CancelStreamingRequest();
      return;
    }

    DLOG(INFO) << __func__ << " active_group_id: " << active_group_id_ << "\n"
               << " audio_receiver_state: " << audio_receiver_state_ << "\n"
               << " audio_sender_state: " << audio_sender_state_ << "\n"
               << " configuration_context_type_: "
               << ToHexString(configuration_context_type_) << "\n"
               << " group " << (group ? " exist " : " does not exist ") << "\n";

    switch (audio_receiver_state_) {
      case AudioState::STARTED:
        le_audio_sink_hal_client_->ConfirmStreamingRequest();
        break;
      case AudioState::IDLE:
        switch (audio_sender_state_) {
          case AudioState::IDLE:
            if (OnAudioResume(group)) {
              audio_receiver_state_ = AudioState::READY_TO_START;
            } else {
              le_audio_sink_hal_client_->CancelStreamingRequest();
            }
            break;
          case AudioState::READY_TO_START:
          case AudioState::STARTED:
            audio_receiver_state_ = AudioState::READY_TO_START;
            /* If signalling part is completed trigger start reveivin audio
             * here, otherwise it'll be called on group streaming state callback
             */
            if (group->GetState() ==
                AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
              if (!IsDirectionAvailableForCurrentConfiguration(
                      group, le_audio::types::kLeAudioDirectionSource)) {
                LOG_WARN(
                    "Local audio sink was resumed when not in a proper "
                    "configuration. This should not happen. Reconfiguring to "
                    "VOICEASSISTANTS.");
                SetConfigurationAndStopStreamWhenNeeded(
                    group, LeAudioContextType::VOICEASSISTANTS);
                break;
              }
              StartReceivingAudio(active_group_id_);
            }
            break;
          case AudioState::RELEASING:
            /* Group is reconfiguring, reassing state and wait for
             * the stream to be configured
             */
            audio_receiver_state_ = audio_sender_state_;
            break;
          case AudioState::READY_TO_RELEASE:
            LOG_WARN(
                " called in wrong state. \n audio_receiver_state: %s \n"
                "audio_sender_state: %s \n",
                ToString(audio_receiver_state_).c_str(),
                ToString(audio_sender_state_).c_str());
            CancelStreamingRequest();
            break;
        }
        break;
      case AudioState::READY_TO_START:
        LOG_WARN(
            " called in wrong state. \n audio_receiver_state: %s \n"
            "audio_sender_state: %s \n",
            ToString(audio_receiver_state_).c_str(),
            ToString(audio_sender_state_).c_str());
        CancelStreamingRequest();
        break;
      case AudioState::READY_TO_RELEASE:
        switch (audio_sender_state_) {
          case AudioState::STARTED:
          case AudioState::IDLE:
          case AudioState::READY_TO_START:
          case AudioState::READY_TO_RELEASE:
            /* Stream is up just restore it */
            audio_receiver_state_ = AudioState::STARTED;
            if (alarm_is_scheduled(suspend_timeout_))
              alarm_cancel(suspend_timeout_);
            le_audio_sink_hal_client_->ConfirmStreamingRequest();
            break;
          case AudioState::RELEASING:
            /* Wait until releasing is completed */
            break;
        }

        break;
      case AudioState::RELEASING:
        /* Wait until releasing is completed */
        break;
    }
  }

  /* Chooses a single context type to use as a key for selecting a single
   * audio set configuration. Contexts used for the metadata can be different
   * than this, but it's reasonable to select a configuration context from
   * the metadata context types.
   */
  LeAudioContextType ChooseConfigurationContextType(
      AudioContexts available_remote_contexts) {
    LOG_DEBUG("Got contexts=%s in config_context=%s",
              bluetooth::common::ToString(available_remote_contexts).c_str(),
              bluetooth::common::ToString(configuration_context_type_).c_str());

    if (in_call_) {
      LOG_DEBUG(" In Call preference used.");
      return LeAudioContextType::CONVERSATIONAL;
    }

    /* Mini policy - always prioritize sink+source configurations so that we are
     * sure that for a mixed content we enable all the needed directions.
     */
    if (available_remote_contexts.any()) {
      LeAudioContextType context_priority_list[] = {
          /* Highest priority first */
          LeAudioContextType::CONVERSATIONAL,
          /* Skip the RINGTONE to avoid reconfigurations when adjusting
           * call volume slider while not in a call.
           * LeAudioContextType::RINGTONE,
           */
          LeAudioContextType::LIVE,
          LeAudioContextType::VOICEASSISTANTS,
          LeAudioContextType::GAME,
          LeAudioContextType::MEDIA,
          LeAudioContextType::EMERGENCYALARM,
          LeAudioContextType::ALERTS,
          LeAudioContextType::INSTRUCTIONAL,
          LeAudioContextType::NOTIFICATIONS,
          LeAudioContextType::SOUNDEFFECTS,
      };
      for (auto ct : context_priority_list) {
        if (available_remote_contexts.test(ct)) {
          LOG_DEBUG("Selecting configuration context type: %s",
                    ToString(ct).c_str());
          return ct;
        }
      }
    }

    /* We keepo the existing configuration, when not in a call, but the user
     * adjusts the ringtone volume while there is no other valid audio stream.
     */
    if (available_remote_contexts.test(LeAudioContextType::RINGTONE)) {
      return configuration_context_type_;
    }

    /* Fallback to BAP mandated context type */
    LOG_WARN("Invalid/unknown context, using 'UNSPECIFIED'");
    return LeAudioContextType::UNSPECIFIED;
  }

  bool SetConfigurationAndStopStreamWhenNeeded(
      LeAudioDeviceGroup* group, LeAudioContextType new_context_type) {
    auto reconfig_result = UpdateConfigAndCheckIfReconfigurationIsNeeded(
        group->group_id_, new_context_type);
    /* Even though the reconfiguration may not be needed, this has
     * to be set here as it might be the initial configuration.
     */
    configuration_context_type_ = new_context_type;

    LOG_INFO("group_id %d, context type %s (%s), %s", group->group_id_,
             ToString(new_context_type).c_str(),
             ToHexString(new_context_type).c_str(),
             ToString(reconfig_result).c_str());
    if (reconfig_result ==
        AudioReconfigurationResult::RECONFIGURATION_NOT_NEEDED) {
      return false;
    }

    if (reconfig_result ==
        AudioReconfigurationResult::RECONFIGURATION_NOT_POSSIBLE) {
      return false;
    }

    if (group->GetState() != AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
      DLOG(INFO) << __func__ << " Group is not streaming ";
      return false;
    }

    if (alarm_is_scheduled(suspend_timeout_)) alarm_cancel(suspend_timeout_);

    /* Need to reconfigure stream */
    group->SetPendingConfiguration();
    groupStateMachine_->StopStream(group);
    return true;
  }

  void OnLocalAudioSourceMetadataUpdate(
      std::vector<struct playback_track_metadata> source_metadata) {
    if (active_group_id_ == bluetooth::groups::kGroupUnknown) {
      LOG(WARNING) << ", cannot start streaming if no active group set";
      return;
    }

    auto group = aseGroups_.FindById(active_group_id_);
    if (!group) {
      LOG(ERROR) << __func__
                 << ", Invalid group: " << static_cast<int>(active_group_id_);
      return;
    }

    /* Stop the VBC close timeout timer, since we will reconfigure anyway if the
     * VBC was suspended.
     */
    StopVbcCloseTimeout();

    LOG_DEBUG("group state=%s, target_state=%s",
              ToString(group->GetState()).c_str(),
              ToString(group->GetTargetState()).c_str());

    auto new_metadata_context_types_ = AudioContexts();

    /* If the local sink is started, ready to start or any direction is
     * reconfiguring to start sit remote source configuration, then take
     * into the account current context type. If the metadata seem
     * invalid, keep the old one, but verify against the availability.
     * Otherwise start empty and add the tracks contexts.
     */
    auto is_releasing_for_reconfiguration =
        (((audio_receiver_state_ == AudioState::RELEASING) ||
          (audio_sender_state_ == AudioState::RELEASING)) &&
         group->IsPendingConfiguration() &&
         IsDirectionAvailableForCurrentConfiguration(
             group, le_audio::types::kLeAudioDirectionSource));
    if (is_releasing_for_reconfiguration ||
        (audio_receiver_state_ == AudioState::STARTED) ||
        (audio_receiver_state_ == AudioState::READY_TO_START)) {
      LOG_DEBUG("Other direction is streaming. Taking its contexts %s",
                ToString(metadata_context_types_.source).c_str());
      new_metadata_context_types_ =
          ChooseMetadataContextType(metadata_context_types_.source);

    } else if (source_metadata.empty()) {
      LOG_DEBUG("Not a valid sink metadata update. Keeping the old contexts");
      new_metadata_context_types_ &= group->GetAvailableContexts();

    } else {
      LOG_DEBUG("No other direction is streaming. Start with empty contexts.");
    }

    /* Set the remote sink metadata context from the playback tracks metadata */
    metadata_context_types_.sink = GetAllowedAudioContextsFromSourceMetadata(
        source_metadata, group->GetAvailableContexts());
    new_metadata_context_types_ |= metadata_context_types_.sink;

    if (stack_config_get_interface()
            ->get_pts_force_le_audio_multiple_contexts_metadata()) {
      // Use common audio stream contexts exposed by the PTS
      metadata_context_types_.sink = AudioContexts(0xFFFF);
      for (auto device = group->GetFirstDevice(); device != nullptr;
           device = group->GetNextDevice(device)) {
        metadata_context_types_.sink &= device->GetAvailableContexts();
      }
      if (metadata_context_types_.sink.value() == 0xFFFF) {
        metadata_context_types_.sink =
            AudioContexts(LeAudioContextType::UNSPECIFIED);
      }
      LOG_WARN("Overriding metadata_context_types_ with: %s",
               metadata_context_types_.sink.to_string().c_str());

      /* Choose the right configuration context */
      auto new_configuration_context =
          ChooseConfigurationContextType(metadata_context_types_.sink);

      LOG_DEBUG("new_configuration_context= %s.",
                ToString(new_configuration_context).c_str());
      GroupStream(active_group_id_, new_configuration_context,
                  metadata_context_types_.sink);
      return;
    }

    if (new_metadata_context_types_.none()) {
      LOG_WARN("invalid/unknown context metadata, using 'UNSPECIFIED' instead");
      new_metadata_context_types_ =
          AudioContexts(LeAudioContextType::UNSPECIFIED);
    }

    /* Choose the right configuration context */
    auto new_configuration_context =
        ChooseConfigurationContextType(new_metadata_context_types_);

    /* For the following contexts we don't actually need HQ audio:
     * LeAudioContextType::NOTIFICATIONS
     * LeAudioContextType::SOUNDEFFECTS
     * LeAudioContextType::INSTRUCTIONAL
     * LeAudioContextType::ALERTS
     * LeAudioContextType::EMERGENCYALARM
     * So do not reconfigure if the remote sink is already available at any
     * quality and these are the only contributors to the current audio stream.
     */
    auto no_reconfigure_contexts =
        LeAudioContextType::NOTIFICATIONS | LeAudioContextType::SOUNDEFFECTS |
        LeAudioContextType::INSTRUCTIONAL | LeAudioContextType::ALERTS |
        LeAudioContextType::EMERGENCYALARM;
    if ((new_metadata_context_types_ & ~no_reconfigure_contexts).none() &&
        IsDirectionAvailableForCurrentConfiguration(
            group, le_audio::types::kLeAudioDirectionSink)) {
      LOG_INFO(
          "There is no need to reconfigure for the sonification events. Keep "
          "the configuration unchanged.");
      new_configuration_context = configuration_context_type_;
    }

    LOG_DEBUG("new_configuration_context= %s",
              ToString(new_configuration_context).c_str());
    ReconfigureOrUpdateMetadata(group, new_configuration_context,
                                std::move(new_metadata_context_types_));
  }

  void OnLocalAudioSinkMetadataUpdate(
      std::vector<struct record_track_metadata> sink_metadata) {
    if (active_group_id_ == bluetooth::groups::kGroupUnknown) {
      LOG(WARNING) << ", cannot start streaming if no active group set";
      return;
    }

    auto group = aseGroups_.FindById(active_group_id_);
    if (!group) {
      LOG(ERROR) << __func__
                 << ", Invalid group: " << static_cast<int>(active_group_id_);
      return;
    }

    LOG_DEBUG("group state=%s, target_state=%s",
              ToString(group->GetState()).c_str(),
              ToString(group->GetTargetState()).c_str());

    auto new_metadata_context_types = AudioContexts();

    /* If the local source is started, ready to start or any direction is
     * reconfiguring to start sit remote sink configuration, then take
     * into the account current context type. If the metadata seem
     * invalid, keep the old one, but verify against the availability.
     * Otherwise start empty and add the tracks contexts.
     */
    auto is_releasing_for_reconfiguration =
        (((audio_receiver_state_ == AudioState::RELEASING) ||
          (audio_sender_state_ == AudioState::RELEASING)) &&
         group->IsPendingConfiguration() &&
         IsDirectionAvailableForCurrentConfiguration(
             group, le_audio::types::kLeAudioDirectionSink));
    if (is_releasing_for_reconfiguration ||
        (audio_sender_state_ == AudioState::STARTED) ||
        (audio_sender_state_ == AudioState::READY_TO_START)) {
      LOG_DEBUG("Other direction is streaming. Taking its contexts %s",
                ToString(metadata_context_types_.sink).c_str());
      new_metadata_context_types =
          ChooseMetadataContextType(metadata_context_types_.sink);

    } else if (sink_metadata.empty()) {
      LOG_DEBUG("Not a valid sink metadata update. Keeping the old contexts");
      new_metadata_context_types &= group->GetAvailableContexts();

    } else {
      LOG_DEBUG("No other direction is streaming. Start with empty contexts.");
    }

    /* Set remote source metadata context from the recording tracks metadata */
    metadata_context_types_.source = GetAllowedAudioContextsFromSinkMetadata(
        sink_metadata, group->GetAvailableContexts());

    /* Make sure we have CONVERSATIONAL when in a call */
    if (in_call_) {
      LOG_DEBUG(" In Call preference used.");
      metadata_context_types_.source |=
          AudioContexts(LeAudioContextType::CONVERSATIONAL);
    }

    /* Append the remote source context types */
    new_metadata_context_types |= metadata_context_types_.source;

    if (stack_config_get_interface()
            ->get_pts_force_le_audio_multiple_contexts_metadata()) {
      // Use common audio stream contexts exposed by the PTS
      new_metadata_context_types = AudioContexts(0xFFFF);
      for (auto device = group->GetFirstDevice(); device != nullptr;
           device = group->GetNextDevice(device)) {
        new_metadata_context_types &= device->GetAvailableContexts();
      }
      if (new_metadata_context_types.value() == 0xFFFF) {
        new_metadata_context_types =
            AudioContexts(LeAudioContextType::UNSPECIFIED);
      }
      LOG_WARN("Overriding new_metadata_context_types with: %su",
               new_metadata_context_types.to_string().c_str());

      /* Choose the right configuration context */
      const auto new_configuration_context =
          ChooseConfigurationContextType(new_metadata_context_types);

      LOG_DEBUG("new_configuration_context= %s.",
                ToString(new_configuration_context).c_str());
      new_metadata_context_types.set(new_configuration_context);
    }

    if (new_metadata_context_types.none()) {
      LOG_WARN("invalid/unknown context metadata, using 'UNSPECIFIED' instead");
      new_metadata_context_types =
          AudioContexts(LeAudioContextType::UNSPECIFIED);
    }

    /* Choose the right configuration context */
    const auto new_configuration_context =
        ChooseConfigurationContextType(new_metadata_context_types);
    LOG_DEBUG("new_configuration_context= %s",
              ToString(new_configuration_context).c_str());

    /* Do nothing if audio source is not valid for the new configuration */
    const auto is_audio_source_context =
        IsContextForAudioSource(new_configuration_context);
    if (!is_audio_source_context) {
      LOG_WARN(
          "No valid remote audio source configuration context in %s, staying "
          "with the existing configuration context of %s",
          ToString(new_configuration_context).c_str(),
          ToString(configuration_context_type_).c_str());
      return;
    }

    /* Do nothing if group already has Voiceback channel configured.
     * WARNING: This eliminates additional reconfigurations but can
     * lead to unsatisfying audio quality when that direction was
     * already configured with a lower quality.
     */
    const auto has_audio_source_configured =
        IsDirectionAvailableForCurrentConfiguration(
            group, le_audio::types::kLeAudioDirectionSource) &&
        (group->GetState() == AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
    if (has_audio_source_configured) {
      LOG_DEBUG(
          "Audio source is already available in the current configuration "
          "context in %s. Not switching to %s right now.",
          ToString(configuration_context_type_).c_str(),
          ToString(new_configuration_context).c_str());
      return;
    }

    ReconfigureOrUpdateMetadata(group, new_configuration_context,
                                std::move(new_metadata_context_types));
  }

  void ReconfigureOrUpdateMetadata(LeAudioDeviceGroup* group,
                                   LeAudioContextType new_configuration_context,
                                   AudioContexts new_metadata_context_types) {
    if (new_configuration_context != configuration_context_type_) {
      LOG_DEBUG(
          "Changing configuration context from %s to %s, new "
          "metadata_contexts: %s",
          ToString(configuration_context_type_).c_str(),
          ToString(new_configuration_context).c_str(),
          ToString(new_metadata_context_types).c_str());
      // TODO: This should also cache the combined metadata context for the
      //       reconfiguration, so that once the group reaches IDLE state and
      //       is about to reconfigure, we would know if we reconfigure with
      //       sink or source or both metadata.
      if (SetConfigurationAndStopStreamWhenNeeded(group,
                                                  new_configuration_context)) {
        return;
      }
    }

    if (group->GetTargetState() == AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
      LOG_DEBUG(
          "The %s configuration did not change. Changing only the metadata "
          "contexts from %s to %s",
          ToString(configuration_context_type_).c_str(),
          ToString(get_bidirectional(metadata_context_types_)).c_str(),
          ToString(new_metadata_context_types).c_str());
      GroupStream(group->group_id_, new_configuration_context,
                  new_metadata_context_types);
    }
  }

  static void OnGattReadRspStatic(uint16_t conn_id, tGATT_STATUS status,
                                  uint16_t hdl, uint16_t len, uint8_t* value,
                                  void* data) {
    if (!instance) return;

    LeAudioDevice* leAudioDevice =
        instance->leAudioDevices_.FindByConnId(conn_id);

    if (status == GATT_SUCCESS) {
      instance->LeAudioCharValueHandle(conn_id, hdl, len, value);
    } else if (status == GATT_DATABASE_OUT_OF_SYNC) {
      instance->ClearDeviceInformationAndStartSearch(leAudioDevice);
      return;
    }

    /* We use data to keep notify connected flag. */
    if (data && !!PTR_TO_INT(data)) {
      leAudioDevice->notify_connected_after_read_ = false;

      /* Update PACs and ASEs when all is read.*/
      btif_storage_leaudio_update_pacs_bin(leAudioDevice->address_);
      btif_storage_leaudio_update_ase_bin(leAudioDevice->address_);

      btif_storage_set_leaudio_audio_location(
          leAudioDevice->address_,
          leAudioDevice->snk_audio_locations_.to_ulong(),
          leAudioDevice->src_audio_locations_.to_ulong());

      instance->connectionReady(leAudioDevice);
    }
  }

  void IsoCigEventsCb(uint16_t event_type, void* data) {
    switch (event_type) {
      case bluetooth::hci::iso_manager::kIsoEventCigOnCreateCmpl: {
        auto* evt = static_cast<cig_create_cmpl_evt*>(data);
        LeAudioDeviceGroup* group = aseGroups_.FindById(evt->cig_id);
        ASSERT_LOG(group, "Group id: %d is null", evt->cig_id);
        groupStateMachine_->ProcessHciNotifOnCigCreate(
            group, evt->status, evt->cig_id, evt->conn_handles);
      } break;
      case bluetooth::hci::iso_manager::kIsoEventCigOnRemoveCmpl: {
        auto* evt = static_cast<cig_remove_cmpl_evt*>(data);
        LeAudioDeviceGroup* group = aseGroups_.FindById(evt->cig_id);
        ASSERT_LOG(group, "Group id: %d is null", evt->cig_id);
        groupStateMachine_->ProcessHciNotifOnCigRemove(evt->status, group);
        remove_group_if_possible(group);
      } break;
      default:
        LOG_ERROR("Invalid event %d", +event_type);
    }
  }

  void IsoCisEventsCb(uint16_t event_type, void* data) {
    switch (event_type) {
      case bluetooth::hci::iso_manager::kIsoEventCisDataAvailable: {
        auto* event =
            static_cast<bluetooth::hci::iso_manager::cis_data_evt*>(data);

        if (audio_receiver_state_ != AudioState::STARTED) {
          LOG_ERROR("receiver state not ready, current state=%s",
                    ToString(audio_receiver_state_).c_str());
          break;
        }

        HandleIncomingCisData(event->p_msg->data + event->p_msg->offset,
                              event->p_msg->len - event->p_msg->offset,
                              event->cis_conn_hdl, event->ts);
      } break;
      case bluetooth::hci::iso_manager::kIsoEventCisEstablishCmpl: {
        auto* event =
            static_cast<bluetooth::hci::iso_manager::cis_establish_cmpl_evt*>(
                data);

        LeAudioDevice* leAudioDevice = leAudioDevices_.FindByCisConnHdl(
            event->cig_id, event->cis_conn_hdl);
        if (!leAudioDevice) {
          LOG(ERROR) << __func__ << ", no bonded Le Audio Device with CIS: "
                     << +event->cis_conn_hdl;
          break;
        }
        LeAudioDeviceGroup* group =
            aseGroups_.FindById(leAudioDevice->group_id_);

        if (event->max_pdu_mtos > 0)
          group->SetTransportLatency(le_audio::types::kLeAudioDirectionSink,
                                     event->trans_lat_mtos);
        if (event->max_pdu_stom > 0)
          group->SetTransportLatency(le_audio::types::kLeAudioDirectionSource,
                                     event->trans_lat_stom);

        groupStateMachine_->ProcessHciNotifCisEstablished(group, leAudioDevice,
                                                          event);
      } break;
      case bluetooth::hci::iso_manager::kIsoEventCisDisconnected: {
        auto* event =
            static_cast<bluetooth::hci::iso_manager::cis_disconnected_evt*>(
                data);

        LeAudioDevice* leAudioDevice = leAudioDevices_.FindByCisConnHdl(
            event->cig_id, event->cis_conn_hdl);
        if (!leAudioDevice) {
          LOG(ERROR) << __func__ << ", no bonded Le Audio Device with CIS: "
                     << +event->cis_conn_hdl;
          break;
        }
        LeAudioDeviceGroup* group =
            aseGroups_.FindById(leAudioDevice->group_id_);

        groupStateMachine_->ProcessHciNotifCisDisconnected(group, leAudioDevice,
                                                           event);
      } break;
      default:
        LOG(INFO) << ", Not handeled ISO event";
        break;
    }
  }

  void IsoSetupIsoDataPathCb(uint8_t status, uint16_t conn_handle,
                             uint8_t cig_id) {
    LeAudioDevice* leAudioDevice =
        leAudioDevices_.FindByCisConnHdl(cig_id, conn_handle);
    /* In case device has been disconnected before data path was setup */
    if (!leAudioDevice) {
      LOG_WARN("Device for CIG %d and using cis_handle 0x%04x is disconnected.",
               cig_id, conn_handle);
      return;
    }
    LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);

    instance->groupStateMachine_->ProcessHciNotifSetupIsoDataPath(
        group, leAudioDevice, status, conn_handle);
  }

  void IsoRemoveIsoDataPathCb(uint8_t status, uint16_t conn_handle,
                              uint8_t cig_id) {
    LeAudioDevice* leAudioDevice =
        leAudioDevices_.FindByCisConnHdl(cig_id, conn_handle);

    /* If CIS has been disconnected just before ACL being disconnected by the
     * remote device, leAudioDevice might be already cleared i.e. has no
     * information about conn_handle, when the data path remove compete arrives.
     */
    if (!leAudioDevice) {
      LOG_WARN("Device for CIG %d and using cis_handle 0x%04x is disconnected.",
               cig_id, conn_handle);
      return;
    }

    LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);

    instance->groupStateMachine_->ProcessHciNotifRemoveIsoDataPath(
        group, leAudioDevice, status, conn_handle);
  }

  void IsoLinkQualityReadCb(
      uint8_t conn_handle, uint8_t cig_id, uint32_t txUnackedPackets,
      uint32_t txFlushedPackets, uint32_t txLastSubeventPackets,
      uint32_t retransmittedPackets, uint32_t crcErrorPackets,
      uint32_t rxUnreceivedPackets, uint32_t duplicatePackets) {
    LeAudioDevice* leAudioDevice =
        leAudioDevices_.FindByCisConnHdl(cig_id, conn_handle);
    if (!leAudioDevice) {
      LOG(WARNING) << __func__ << ", device under connection handle: "
                   << loghex(conn_handle)
                   << ", has been disconnecected in meantime";
      return;
    }
    LeAudioDeviceGroup* group = aseGroups_.FindById(leAudioDevice->group_id_);

    instance->groupStateMachine_->ProcessHciNotifIsoLinkQualityRead(
        group, leAudioDevice, conn_handle, txUnackedPackets, txFlushedPackets,
        txLastSubeventPackets, retransmittedPackets, crcErrorPackets,
        rxUnreceivedPackets, duplicatePackets);
  }

  void HandlePendingAvailableContextsChange(LeAudioDeviceGroup* group) {
    if (!group) return;

    /* Update group configuration with pending available context change */
    auto contexts = group->GetPendingAvailableContextsChange();
    if (contexts.any()) {
      auto success = group->UpdateAudioContextTypeAvailability(contexts);
      if (success) {
        callbacks_->OnAudioConf(group->audio_directions_, group->group_id_,
                                group->snk_audio_locations_.to_ulong(),
                                group->src_audio_locations_.to_ulong(),
                                group->GetAvailableContexts().value());
      }
      group->ClearPendingAvailableContextsChange();
    }
  }

  void HandlePendingDeviceRemove(LeAudioDeviceGroup* group) {
    for (auto device = group->GetFirstDevice(); device != nullptr;
         device = group->GetNextDevice(device)) {
      if (device->GetConnectionState() == DeviceConnectState::PENDING_REMOVAL) {
        if (device->closing_stream_for_disconnection_) {
          device->closing_stream_for_disconnection_ = false;
          LOG_INFO("Disconnecting group id: %d, address: %s", group->group_id_,
                   device->address_.ToString().c_str());
          DisconnectDevice(device);
        }
        group_remove_node(group, device->address_, true);
      }
    }
  }

  void HandlePendingDeviceDisconnection(LeAudioDeviceGroup* group) {
    LOG_DEBUG();
    auto leAudioDevice = group->GetFirstDevice();
    while (leAudioDevice) {
      if (leAudioDevice->closing_stream_for_disconnection_) {
        leAudioDevice->closing_stream_for_disconnection_ = false;
        LOG_DEBUG("Disconnecting group id: %d, address: %s", group->group_id_,
                  leAudioDevice->address_.ToString().c_str());
        DisconnectDevice(leAudioDevice);
      }
      leAudioDevice = group->GetNextDevice(leAudioDevice);
    }
  }

  void updateOffloaderIfNeeded(LeAudioDeviceGroup* group) {
    if (CodecManager::GetInstance()->GetCodecLocation() !=
        le_audio::types::CodecLocation::ADSP) {
      return;
    }

    LOG_INFO("Group %p, group_id %d", group, group->group_id_);

    const auto* stream_conf = &group->stream_conf;

    if (stream_conf->sink_offloader_changed || stream_conf->sink_is_initial) {
      LOG_INFO("Update sink offloader streams");
      uint16_t remote_delay_ms =
          group->GetRemoteDelay(le_audio::types::kLeAudioDirectionSink);
      CodecManager::GetInstance()->UpdateActiveSourceAudioConfig(
          *stream_conf, remote_delay_ms,
          std::bind(&LeAudioSourceAudioHalClient::UpdateAudioConfigToHal,
                    le_audio_source_hal_client_.get(), std::placeholders::_1));
      group->StreamOffloaderUpdated(le_audio::types::kLeAudioDirectionSink);
    }

    if (stream_conf->source_offloader_changed ||
        stream_conf->source_is_initial) {
      LOG_INFO("Update source offloader streams");
      uint16_t remote_delay_ms =
          group->GetRemoteDelay(le_audio::types::kLeAudioDirectionSource);
      CodecManager::GetInstance()->UpdateActiveSinkAudioConfig(
          *stream_conf, remote_delay_ms,
          std::bind(&LeAudioSinkAudioHalClient::UpdateAudioConfigToHal,
                    le_audio_sink_hal_client_.get(), std::placeholders::_1));
      group->StreamOffloaderUpdated(le_audio::types::kLeAudioDirectionSource);
    }
  }

  void NotifyUpperLayerGroupTurnedIdleDuringCall(int group_id) {
    if (!osi_property_get_bool(kNotifyUpperLayerAboutGroupBeingInIdleDuringCall,
                               false)) {
      return;
    }
    /* If group is inactive, phone is in call and Group is not having CIS
     * connected, notify upper layer about it, so it can decide to create SCO if
     * it is in the handover case
     */
    if (in_call_ && active_group_id_ == bluetooth::groups::kGroupUnknown) {
      callbacks_->OnGroupStatus(group_id, GroupStatus::TURNED_IDLE_DURING_CALL);
    }
  }

  void take_stream_time(void) {
    if (stream_setup_start_timestamp_ == 0) {
      return;
    }

    if (stream_start_history_queue_.size() == 10) {
      stream_start_history_queue_.pop_back();
    }

    stream_setup_end_timestamp_ = bluetooth::common::time_get_os_boottime_us();
    stream_start_history_queue_.emplace_front(
        (stream_setup_end_timestamp_ - stream_setup_start_timestamp_) / 1000);

    stream_setup_end_timestamp_ = 0;
    stream_setup_start_timestamp_ = 0;
  }

  void OnStateMachineStatusReportCb(int group_id, GroupStreamStatus status) {
    LOG_INFO("status: %d , audio_sender_state %s, audio_receiver_state %s",
             static_cast<int>(status),
             bluetooth::common::ToString(audio_sender_state_).c_str(),
             bluetooth::common::ToString(audio_receiver_state_).c_str());
    LeAudioDeviceGroup* group = aseGroups_.FindById(group_id);
    switch (status) {
      case GroupStreamStatus::STREAMING:
        ASSERT_LOG(group_id == active_group_id_, "invalid group id %d!=%d",
                   group_id, active_group_id_);

        /* It might happen that the configuration has already changed, while
         * the group was in the ongoing reconfiguration. We should stop the
         * stream and reconfigure once again.
         */
        if (group && group->GetConfigurationContextType() !=
                         configuration_context_type_) {
          LOG_DEBUG(
              "The configuration %s is no longer valid. Stopping the stream to"
              " reconfigure to %s",
              ToString(group->GetConfigurationContextType()).c_str(),
              ToString(configuration_context_type_).c_str());
          group->SetPendingConfiguration();
          groupStateMachine_->StopStream(group);
          stream_setup_start_timestamp_ =
              bluetooth::common::time_get_os_boottime_us();
          return;
        }

        if (group) {
          updateOffloaderIfNeeded(group);
        }

        if (audio_sender_state_ == AudioState::READY_TO_START)
          StartSendingAudio(group_id);
        if (audio_receiver_state_ == AudioState::READY_TO_START)
          StartReceivingAudio(group_id);

        take_stream_time();

        le_audio::MetricsCollector::Get()->OnStreamStarted(
            active_group_id_, configuration_context_type_);
        break;
      case GroupStreamStatus::SUSPENDED:
        stream_setup_end_timestamp_ = 0;
        stream_setup_start_timestamp_ = 0;
        /** Stop Audio but don't release all the Audio resources */
        SuspendAudio();
        break;
      case GroupStreamStatus::CONFIGURED_BY_USER: {
        // Check which directions were suspended
        uint8_t previously_active_directions = 0;
        if (audio_sender_state_ >= AudioState::READY_TO_START) {
          previously_active_directions |=
              le_audio::types::kLeAudioDirectionSink;
        }
        if (audio_receiver_state_ >= AudioState::READY_TO_START) {
          previously_active_directions |=
              le_audio::types::kLeAudioDirectionSource;
        }

        /* We are done with reconfiguration.
         * Clean state and if Audio HAL is waiting, cancel the request
         * so Audio HAL can Resume again.
         */
        CancelStreamingRequest();
        HandlePendingAvailableContextsChange(group);
        ReconfigurationComplete(previously_active_directions);
      } break;
      case GroupStreamStatus::CONFIGURED_AUTONOMOUS:
        /* This state is notified only when
         * groups stays into CONFIGURED state after
         * STREAMING. Peer device uses cache. For the moment
         * it is handled same as IDLE
         */
        FALLTHROUGH;
      case GroupStreamStatus::IDLE: {
        if (group && group->IsPendingConfiguration()) {
          SuspendedForReconfiguration();
          // TODO: It is not certain to which directions we will
          //       reconfigure. We would have know the exact
          //       configuration but this is yet to be selected or have
          //       the metadata cached from earlier when reconfiguration
          //       was scheduled.
          auto adjusted_metedata_context_type = ChooseMetadataContextType(
              get_bidirectional(metadata_context_types_));
          if (groupStateMachine_->ConfigureStream(
                  group, configuration_context_type_,
                  adjusted_metedata_context_type,
                  GetAllCcids(adjusted_metedata_context_type))) {
            /* If configuration succeed wait for new status. */
            return;
          }
        }
        stream_setup_end_timestamp_ = 0;
        stream_setup_start_timestamp_ = 0;
        CancelStreamingRequest();
        if (group) {
          NotifyUpperLayerGroupTurnedIdleDuringCall(group->group_id_);
          HandlePendingAvailableContextsChange(group);
          HandlePendingDeviceRemove(group);
          HandlePendingDeviceDisconnection(group);
        }
        break;
      }
      case GroupStreamStatus::RELEASING:
      case GroupStreamStatus::SUSPENDING:
        if (audio_sender_state_ != AudioState::IDLE)
          audio_sender_state_ = AudioState::RELEASING;

        if (audio_receiver_state_ != AudioState::IDLE)
          audio_receiver_state_ = AudioState::RELEASING;

        break;
      default:
        break;
    }
  }

 private:
  tGATT_IF gatt_if_;
  bluetooth::le_audio::LeAudioClientCallbacks* callbacks_;
  LeAudioDevices leAudioDevices_;
  LeAudioDeviceGroups aseGroups_;
  LeAudioGroupStateMachine* groupStateMachine_;
  int active_group_id_;
  LeAudioContextType configuration_context_type_;
  static constexpr char kAllowMultipleContextsInMetadata[] =
      "persist.bluetooth.leaudio.allow.multiple.contexts";
  BidirectionalPair<AudioContexts> metadata_context_types_;
  uint64_t stream_setup_start_timestamp_;
  uint64_t stream_setup_end_timestamp_;
  std::deque<uint64_t> stream_start_history_queue_;

  /* Microphone (s) */
  AudioState audio_receiver_state_;
  /* Speaker(s) */
  AudioState audio_sender_state_;
  /* Keep in call state. */
  bool in_call_;

  /* Reconnection mode */
  tBTM_BLE_CONN_TYPE reconnection_mode_;

  static constexpr char kNotifyUpperLayerAboutGroupBeingInIdleDuringCall[] =
      "persist.bluetooth.leaudio.notify.idle.during.call";

  /* Current stream configuration */
  LeAudioCodecConfiguration current_source_codec_config;
  LeAudioCodecConfiguration current_sink_codec_config;

  /* Static Audio Framework session configuration.
   *  Resampling will be done inside the bt stack
   */
  LeAudioCodecConfiguration audio_framework_source_config = {
      .num_channels = 2,
      .sample_rate = bluetooth::audio::le_audio::kSampleRate48000,
      .bits_per_sample = bluetooth::audio::le_audio::kBitsPerSample16,
      .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us,
  };

  LeAudioCodecConfiguration audio_framework_sink_config = {
      .num_channels = 2,
      .sample_rate = bluetooth::audio::le_audio::kSampleRate16000,
      .bits_per_sample = bluetooth::audio::le_audio::kBitsPerSample16,
      .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us,
  };

  void* lc3_encoder_left_mem;
  void* lc3_encoder_right_mem;

  lc3_encoder_t lc3_encoder_left;
  lc3_encoder_t lc3_encoder_right;

  void* lc3_decoder_left_mem;
  void* lc3_decoder_right_mem;

  lc3_decoder_t lc3_decoder_left;
  lc3_decoder_t lc3_decoder_right;

  std::vector<uint8_t> encoded_data;
  std::unique_ptr<LeAudioSourceAudioHalClient> le_audio_source_hal_client_;
  std::unique_ptr<LeAudioSinkAudioHalClient> le_audio_sink_hal_client_;
  static constexpr uint64_t kAudioSuspentKeepIsoAliveTimeoutMs = 5000;
  static constexpr uint64_t kAudioDisableTimeoutMs = 3000;
  static constexpr char kAudioSuspentKeepIsoAliveTimeoutMsProp[] =
      "persist.bluetooth.leaudio.audio.suspend.timeoutms";
  alarm_t* close_vbc_timeout_;
  alarm_t* suspend_timeout_;
  alarm_t* disable_timer_;
  static constexpr uint64_t kDeviceAttachDelayMs = 500;

  std::vector<int16_t> cached_channel_data_;
  uint32_t cached_channel_timestamp_ = 0;
  uint32_t cached_channel_is_left_;

  void ClientAudioIntefraceRelease() {
    if (le_audio_source_hal_client_) {
      le_audio_source_hal_client_->Stop();
      le_audio_source_hal_client_.reset();
    }

    if (le_audio_sink_hal_client_) {
      le_audio_sink_hal_client_->Stop();
      le_audio_sink_hal_client_.reset();
    }
    le_audio::MetricsCollector::Get()->OnStreamEnded(active_group_id_);
  }
};

/* This is a generic callback method for gatt client which handles every client
 * application events.
 */
void le_audio_gattc_callback(tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
  if (!p_data || !instance) return;

  LOG_DEBUG("event = %d", static_cast<int>(event));

  switch (event) {
    case BTA_GATTC_DEREG_EVT:
      break;

    case BTA_GATTC_NOTIF_EVT:
      instance->LeAudioCharValueHandle(
          p_data->notify.conn_id, p_data->notify.handle, p_data->notify.len,
          static_cast<uint8_t*>(p_data->notify.value), true);

      if (!p_data->notify.is_notify)
        BTA_GATTC_SendIndConfirm(p_data->notify.conn_id, p_data->notify.handle);

      break;

    case BTA_GATTC_OPEN_EVT:
      instance->OnGattConnected(p_data->open.status, p_data->open.conn_id,
                                p_data->open.client_if, p_data->open.remote_bda,
                                p_data->open.transport, p_data->open.mtu);
      break;

    case BTA_GATTC_ENC_CMPL_CB_EVT: {
      uint8_t encryption_status;
      if (BTM_IsEncrypted(p_data->enc_cmpl.remote_bda, BT_TRANSPORT_LE)) {
        encryption_status = BTM_SUCCESS;
      } else {
        encryption_status = BTM_FAILED_ON_SECURITY;
      }
      instance->OnEncryptionComplete(p_data->enc_cmpl.remote_bda,
                                     encryption_status);
    } break;

    case BTA_GATTC_CLOSE_EVT:
      instance->OnGattDisconnected(
          p_data->close.conn_id, p_data->close.client_if,
          p_data->close.remote_bda, p_data->close.reason);
      break;

    case BTA_GATTC_SEARCH_CMPL_EVT:
      instance->OnServiceSearchComplete(p_data->search_cmpl.conn_id,
                                        p_data->search_cmpl.status);
      break;

    case BTA_GATTC_SRVC_DISC_DONE_EVT:
      instance->OnGattServiceDiscoveryDone(p_data->service_changed.remote_bda);
      break;

    case BTA_GATTC_SRVC_CHG_EVT:
      instance->OnServiceChangeEvent(p_data->remote_bda);
      break;
    case BTA_GATTC_CFG_MTU_EVT:
      instance->OnMtuChanged(p_data->cfg_mtu.conn_id, p_data->cfg_mtu.mtu);
      break;

    default:
      break;
  }
}

class LeAudioStateMachineHciCallbacksImpl : public CigCallbacks {
 public:
  void OnCigEvent(uint8_t event, void* data) override {
    if (instance) instance->IsoCigEventsCb(event, data);
  }

  void OnCisEvent(uint8_t event, void* data) override {
    if (instance) instance->IsoCisEventsCb(event, data);
  }

  void OnSetupIsoDataPath(uint8_t status, uint16_t conn_handle,
                          uint8_t cig_id) override {
    if (instance) instance->IsoSetupIsoDataPathCb(status, conn_handle, cig_id);
  }

  void OnRemoveIsoDataPath(uint8_t status, uint16_t conn_handle,
                           uint8_t cig_id) override {
    if (instance) instance->IsoRemoveIsoDataPathCb(status, conn_handle, cig_id);
  }

  void OnIsoLinkQualityRead(
      uint8_t conn_handle, uint8_t cig_id, uint32_t txUnackedPackets,
      uint32_t txFlushedPackets, uint32_t txLastSubeventPackets,
      uint32_t retransmittedPackets, uint32_t crcErrorPackets,
      uint32_t rxUnreceivedPackets, uint32_t duplicatePackets) {
    if (instance)
      instance->IsoLinkQualityReadCb(conn_handle, cig_id, txUnackedPackets,
                                     txFlushedPackets, txLastSubeventPackets,
                                     retransmittedPackets, crcErrorPackets,
                                     rxUnreceivedPackets, duplicatePackets);
  }
};

LeAudioStateMachineHciCallbacksImpl stateMachineHciCallbacksImpl;

class CallbacksImpl : public LeAudioGroupStateMachine::Callbacks {
 public:
  void StatusReportCb(int group_id, GroupStreamStatus status) override {
    if (instance) instance->OnStateMachineStatusReportCb(group_id, status);
  }

  void OnStateTransitionTimeout(int group_id) override {
    if (instance) instance->OnLeAudioDeviceSetStateTimeout(group_id);
  }
};

CallbacksImpl stateMachineCallbacksImpl;

class SourceCallbacksImpl : public LeAudioSourceAudioHalClient::Callbacks {
 public:
  void OnAudioDataReady(const std::vector<uint8_t>& data) override {
    if (instance) instance->OnAudioDataReady(data);
  }
  void OnAudioSuspend(std::promise<void> do_suspend_promise) override {
    if (instance) instance->OnLocalAudioSourceSuspend();
    do_suspend_promise.set_value();
  }

  void OnAudioResume(void) override {
    if (instance) instance->OnLocalAudioSourceResume();
  }

  void OnAudioMetadataUpdate(
      std::vector<struct playback_track_metadata> source_metadata) override {
    if (instance)
      instance->OnLocalAudioSourceMetadataUpdate(std::move(source_metadata));
  }
};

class SinkCallbacksImpl : public LeAudioSinkAudioHalClient::Callbacks {
 public:
  void OnAudioSuspend(std::promise<void> do_suspend_promise) override {
    if (instance) instance->OnLocalAudioSinkSuspend();
    do_suspend_promise.set_value();
  }
  void OnAudioResume(void) override {
    if (instance) instance->OnLocalAudioSinkResume();
  }

  void OnAudioMetadataUpdate(
      std::vector<struct record_track_metadata> sink_metadata) override {
    if (instance)
      instance->OnLocalAudioSinkMetadataUpdate(std::move(sink_metadata));
  }
};

SourceCallbacksImpl audioSinkReceiverImpl;
SinkCallbacksImpl audioSourceReceiverImpl;

class DeviceGroupsCallbacksImpl : public DeviceGroupsCallbacks {
 public:
  void OnGroupAdded(const RawAddress& address, const bluetooth::Uuid& uuid,
                    int group_id) override {
    if (instance) instance->OnGroupAddedCb(address, uuid, group_id);
  }
  void OnGroupMemberAdded(const RawAddress& address, int group_id) override {
    if (instance) instance->OnGroupMemberAddedCb(address, group_id);
  }
  void OnGroupMemberRemoved(const RawAddress& address, int group_id) override {
    if (instance) instance->OnGroupMemberRemovedCb(address, group_id);
  }
  void OnGroupRemoved(const bluetooth::Uuid& uuid, int group_id) {
    /* to implement if needed */
  }
  void OnGroupAddFromStorage(const RawAddress& address,
                             const bluetooth::Uuid& uuid, int group_id) {
    /* to implement if needed */
  }
};

class DeviceGroupsCallbacksImpl;
DeviceGroupsCallbacksImpl deviceGroupsCallbacksImpl;

}  // namespace

void LeAudioClient::AddFromStorage(
    const RawAddress& addr, bool autoconnect, int sink_audio_location,
    int source_audio_location, int sink_supported_context_types,
    int source_supported_context_types, const std::vector<uint8_t>& handles,
    const std::vector<uint8_t>& sink_pacs,
    const std::vector<uint8_t>& source_pacs, const std::vector<uint8_t>& ases) {
  if (!instance) {
    LOG(ERROR) << "Not initialized yet";
    return;
  }

  instance->AddFromStorage(addr, autoconnect, sink_audio_location,
                           source_audio_location, sink_supported_context_types,
                           source_supported_context_types, handles, sink_pacs,
                           source_pacs, ases);
}

bool LeAudioClient::GetHandlesForStorage(const RawAddress& addr,
                                         std::vector<uint8_t>& out) {
  if (!instance) {
    LOG_ERROR("Not initialized yet");
    return false;
  }

  return instance->GetHandlesForStorage(addr, out);
}

bool LeAudioClient::GetSinkPacsForStorage(const RawAddress& addr,
                                          std::vector<uint8_t>& out) {
  if (!instance) {
    LOG_ERROR("Not initialized yet");
    return false;
  }

  return instance->GetSinkPacsForStorage(addr, out);
}

bool LeAudioClient::GetSourcePacsForStorage(const RawAddress& addr,
                                            std::vector<uint8_t>& out) {
  if (!instance) {
    LOG_ERROR("Not initialized yet");
    return false;
  }

  return instance->GetSourcePacsForStorage(addr, out);
}

bool LeAudioClient::GetAsesForStorage(const RawAddress& addr,
                                      std::vector<uint8_t>& out) {
  if (!instance) {
    LOG_ERROR("Not initialized yet");
    return false;
  }

  return instance->GetAsesForStorage(addr, out);
}

bool LeAudioClient::IsLeAudioClientRunning(void) { return instance != nullptr; }

LeAudioClient* LeAudioClient::Get() {
  CHECK(instance);
  return instance;
}

/* Initializer of main le audio implementation class and its instance */
void LeAudioClient::Initialize(
    bluetooth::le_audio::LeAudioClientCallbacks* callbacks_,
    base::Closure initCb, base::Callback<bool()> hal_2_1_verifier,
    const std::vector<bluetooth::le_audio::btle_audio_codec_config_t>&
        offloading_preference) {
  if (instance) {
    LOG(ERROR) << "Already initialized";
    return;
  }

  if (!controller_get_interface()
           ->supports_ble_connected_isochronous_stream_central() &&
      !controller_get_interface()
           ->supports_ble_connected_isochronous_stream_peripheral()) {
    LOG(ERROR) << "Controller reports no ISO support."
                  " LeAudioClient Init aborted.";
    return;
  }

  LOG_ASSERT(std::move(hal_2_1_verifier).Run())
      << __func__
      << ", LE Audio Client requires Bluetooth Audio HAL V2.1 at least. Either "
         "disable LE Audio Profile, or update your HAL";

  IsoManager::GetInstance()->Start();

  audioSinkReceiver = &audioSinkReceiverImpl;
  audioSourceReceiver = &audioSourceReceiverImpl;
  stateMachineHciCallbacks = &stateMachineHciCallbacksImpl;
  stateMachineCallbacks = &stateMachineCallbacksImpl;
  device_group_callbacks = &deviceGroupsCallbacksImpl;
  instance = new LeAudioClientImpl(callbacks_, stateMachineCallbacks, initCb);

  IsoManager::GetInstance()->RegisterCigCallbacks(stateMachineHciCallbacks);
  CodecManager::GetInstance()->Start(offloading_preference);
  ContentControlIdKeeper::GetInstance()->Start();

  callbacks_->OnInitialized();
}

void LeAudioClient::DebugDump(int fd) {
  DeviceGroups::DebugDump(fd);

  dprintf(fd, "LeAudio Manager: \n");
  if (instance)
    instance->Dump(fd);
  else
    dprintf(fd, "  Not initialized \n");

  LeAudioSinkAudioHalClient::DebugDump(fd);
  LeAudioSourceAudioHalClient::DebugDump(fd);
  le_audio::AudioSetConfigurationProvider::DebugDump(fd);
  IsoManager::GetInstance()->Dump(fd);
  dprintf(fd, "\n");
}

void LeAudioClient::Cleanup(base::Callback<void()> cleanupCb) {
  if (!instance) {
    LOG(ERROR) << "Not initialized";
    return;
  }

  LeAudioClientImpl* ptr = instance;
  instance = nullptr;
  ptr->Cleanup(cleanupCb);
  delete ptr;
  ptr = nullptr;

  CodecManager::GetInstance()->Stop();
  ContentControlIdKeeper::GetInstance()->Stop();
  LeAudioGroupStateMachine::Cleanup();
  IsoManager::GetInstance()->Stop();
  le_audio::MetricsCollector::Get()->Flush();
}
