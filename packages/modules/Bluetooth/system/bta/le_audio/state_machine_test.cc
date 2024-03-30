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

#include "state_machine.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <functional>

#include "bta/le_audio/content_control_id_keeper.h"
#include "bta_gatt_api_mock.h"
#include "bta_gatt_queue_mock.h"
#include "btm_api_mock.h"
#include "client_parser.h"
#include "fake_osi.h"
#include "gd/common/init_flags.h"
#include "le_audio_set_configuration_provider.h"
#include "mock_codec_manager.h"
#include "mock_controller.h"
#include "mock_csis_client.h"
#include "mock_iso_manager.h"
#include "types/bt_transport.h"

using ::le_audio::DeviceConnectState;
using ::le_audio::codec_spec_caps::kLeAudioCodecLC3ChannelCountSingleChannel;
using ::le_audio::codec_spec_caps::kLeAudioCodecLC3ChannelCountTwoChannel;
using ::le_audio::types::LeAudioContextType;
using ::testing::_;
using ::testing::AnyNumber;
using ::testing::AtLeast;
using ::testing::DoAll;
using ::testing::Invoke;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::SaveArg;
using ::testing::Test;

std::map<std::string, int> mock_function_count_map;
extern struct fake_osi_alarm_set_on_mloop fake_osi_alarm_set_on_mloop_;

void osi_property_set_bool(const char* key, bool value);
static const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

constexpr uint8_t media_ccid = 0xC0;
constexpr auto media_context =
    static_cast<std::underlying_type<LeAudioContextType>::type>(
        LeAudioContextType::MEDIA);

constexpr uint8_t call_ccid = 0xD0;
constexpr auto call_context =
    static_cast<std::underlying_type<LeAudioContextType>::type>(
        LeAudioContextType::CONVERSATIONAL);

namespace le_audio {
namespace internal {

// Just some arbitrary initial handles - it has no real meaning
#define ATTR_HANDLE_ASCS_POOL_START (0x0000 | 32)
#define ATTR_HANDLE_PACS_POOL_START (0xFF00 | 64)

constexpr LeAudioContextType kContextTypeUnspecified =
    static_cast<LeAudioContextType>(0x0001);
constexpr LeAudioContextType kContextTypeConversational =
    static_cast<LeAudioContextType>(0x0002);
constexpr LeAudioContextType kContextTypeMedia =
    static_cast<LeAudioContextType>(0x0004);
constexpr LeAudioContextType kContextTypeSoundEffects =
    static_cast<LeAudioContextType>(0x0080);
constexpr LeAudioContextType kContextTypeRingtone =
    static_cast<LeAudioContextType>(0x0200);

namespace codec_specific {

constexpr uint8_t kLc3CodingFormat = 0x06;

// Reference Codec Capabilities values to test against
constexpr uint8_t kCapTypeSupportedSamplingFrequencies = 0x01;
constexpr uint8_t kCapTypeSupportedFrameDurations = 0x02;
constexpr uint8_t kCapTypeAudioChannelCount = 0x03;
constexpr uint8_t kCapTypeSupportedOctetsPerCodecFrame = 0x04;
// constexpr uint8_t kCapTypeSupportedLc3CodecFramesPerSdu = 0x05;

// constexpr uint8_t kCapSamplingFrequency8000Hz = 0x0001;
// constexpr uint8_t kCapSamplingFrequency11025Hz = 0x0002;
constexpr uint8_t kCapSamplingFrequency16000Hz = 0x0004;
// constexpr uint8_t kCapSamplingFrequency22050Hz = 0x0008;
// constexpr uint8_t kCapSamplingFrequency24000Hz = 0x0010;
constexpr uint8_t kCapSamplingFrequency32000Hz = 0x0020;
// constexpr uint8_t kCapSamplingFrequency44100Hz = 0x0040;
constexpr uint8_t kCapSamplingFrequency48000Hz = 0x0080;
// constexpr uint8_t kCapSamplingFrequency88200Hz = 0x0100;
// constexpr uint8_t kCapSamplingFrequency96000Hz = 0x0200;
// constexpr uint8_t kCapSamplingFrequency176400Hz = 0x0400;
// constexpr uint8_t kCapSamplingFrequency192000Hz = 0x0800;
// constexpr uint8_t kCapSamplingFrequency384000Hz = 0x1000;

constexpr uint8_t kCapFrameDuration7p5ms = 0x01;
constexpr uint8_t kCapFrameDuration10ms = 0x02;
// constexpr uint8_t kCapFrameDuration7p5msPreferred = 0x10;
constexpr uint8_t kCapFrameDuration10msPreferred = 0x20;
}  // namespace codec_specific

namespace ascs {
constexpr uint8_t kAseStateIdle = 0x00;
constexpr uint8_t kAseStateCodecConfigured = 0x01;
constexpr uint8_t kAseStateQoSConfigured = 0x02;
constexpr uint8_t kAseStateEnabling = 0x03;
constexpr uint8_t kAseStateStreaming = 0x04;
constexpr uint8_t kAseStateDisabling = 0x05;
constexpr uint8_t kAseStateReleasing = 0x06;

// constexpr uint8_t kAseParamDirectionServerIsAudioSink = 0x01;
// constexpr uint8_t kAseParamDirectionServerIsAudioSource = 0x02;

constexpr uint8_t kAseParamFramingUnframedSupported = 0x00;
// constexpr uint8_t kAseParamFramingUnframedNotSupported = 0x01;

// constexpr uint8_t kAseParamPreferredPhy1M = 0x01;
// constexpr uint8_t kAseParamPreferredPhy2M = 0x02;
// constexpr uint8_t kAseParamPreferredPhyCoded = 0x04;

constexpr uint8_t kAseCtpOpcodeConfigureCodec = 0x01;
constexpr uint8_t kAseCtpOpcodeConfigureQos = 0x02;
constexpr uint8_t kAseCtpOpcodeEnable = 0x03;
constexpr uint8_t kAseCtpOpcodeReceiverStartReady = 0x04;
constexpr uint8_t kAseCtpOpcodeDisable = 0x05;
constexpr uint8_t kAseCtpOpcodeReceiverStopReady = 0x06;
// constexpr uint8_t kAseCtpOpcodeUpdateMetadata = 0x07;
constexpr uint8_t kAseCtpOpcodeRelease = 0x08;
constexpr uint8_t kAseCtpOpcodeMaxVal = kAseCtpOpcodeRelease;

}  // namespace ascs

static RawAddress GetTestAddress(uint8_t index) {
  return {{0xC0, 0xDE, 0xC0, 0xDE, 0x00, index}};
}

class MockLeAudioGroupStateMachineCallbacks
    : public LeAudioGroupStateMachine::Callbacks {
 public:
  MockLeAudioGroupStateMachineCallbacks() = default;
  MockLeAudioGroupStateMachineCallbacks(
      const MockLeAudioGroupStateMachineCallbacks&) = delete;
  MockLeAudioGroupStateMachineCallbacks& operator=(
      const MockLeAudioGroupStateMachineCallbacks&) = delete;

  ~MockLeAudioGroupStateMachineCallbacks() override = default;
  MOCK_METHOD((void), StatusReportCb,
              (int group_id, bluetooth::le_audio::GroupStreamStatus status),
              (override));
  MOCK_METHOD((void), OnStateTransitionTimeout, (int group_id), (override));
};

class StateMachineTest : public Test {
 protected:
  uint8_t ase_id_last_assigned = types::ase::kAseIdInvalid;
  uint8_t additional_snk_ases = 0;
  uint8_t additional_src_ases = 0;
  uint8_t channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel;
  uint16_t sample_freq_ = codec_specific::kCapSamplingFrequency16000Hz;

  void SetUp() override {
    bluetooth::common::InitFlags::Load(test_flags);
    mock_function_count_map.clear();
    controller::SetMockControllerInterface(&mock_controller_);
    bluetooth::manager::SetMockBtmInterface(&btm_interface);
    gatt::SetMockBtaGattInterface(&gatt_interface);
    gatt::SetMockBtaGattQueue(&gatt_queue);

    ::le_audio::AudioSetConfigurationProvider::Initialize();
    LeAudioGroupStateMachine::Initialize(&mock_callbacks_);

    ContentControlIdKeeper::GetInstance()->Start();

    MockCsisClient::SetMockInstanceForTesting(&mock_csis_client_module_);
    ON_CALL(mock_csis_client_module_, Get())
        .WillByDefault(Return(&mock_csis_client_module_));
    ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
        .WillByDefault(Return(true));
    ON_CALL(mock_csis_client_module_, GetDeviceList(_))
        .WillByDefault(Invoke([this](int group_id) { return addresses_; }));
    ON_CALL(mock_csis_client_module_, GetDesiredSize(_))
        .WillByDefault(
            Invoke([this](int group_id) { return (int)(addresses_.size()); }));

    // Support 2M Phy
    ON_CALL(mock_controller_, SupportsBle2mPhy()).WillByDefault(Return(true));
    ON_CALL(btm_interface, IsPhy2mSupported(_, _)).WillByDefault(Return(true));
    ON_CALL(btm_interface, GetHCIConnHandle(_, _))
        .WillByDefault(
            Invoke([](RawAddress const& remote_bda, tBT_TRANSPORT transport) {
              return remote_bda.IsEmpty()
                         ? HCI_INVALID_HANDLE
                         : ((uint16_t)(remote_bda.address[0] ^
                                       remote_bda.address[1] ^
                                       remote_bda.address[2]))
                                   << 8 |
                               (remote_bda.address[3] ^ remote_bda.address[4] ^
                                remote_bda.address[5]);
            }));

    ON_CALL(gatt_queue, WriteCharacteristic(_, _, _, GATT_WRITE_NO_RSP, _, _))
        .WillByDefault(Invoke([this](uint16_t conn_id, uint16_t handle,
                                     std::vector<uint8_t> value,
                                     tGATT_WRITE_TYPE write_type,
                                     GATT_WRITE_OP_CB cb, void* cb_data) {
          for (auto& dev : le_audio_devices_) {
            if (dev->conn_id_ == conn_id) {
              // Control point write handler
              if (dev->ctp_hdls_.val_hdl == handle) {
                HandleCtpOperation(dev.get(), value, cb, cb_data);
              }
              break;
            }
          }
        }));

    ConfigureIsoManagerMock();
    ConfigCodecManagerMock();
  }

  void HandleCtpOperation(LeAudioDevice* device, std::vector<uint8_t> value,
                          GATT_WRITE_OP_CB cb, void* cb_data) {
    auto opcode = value[0];

    // Verify against valid opcode range
    ASSERT_LT(opcode, ascs::kAseCtpOpcodeMaxVal + 1);
    ASSERT_NE(opcode, 0);

    if (ase_ctp_handlers[opcode])
      ase_ctp_handlers[opcode](device, std::move(value), cb, cb_data);
  }

/* Helper function to make a deterministic (and unique on the entire device)
 * connection handle for a given cis.
 */
#define UNIQUE_CIS_CONN_HANDLE(cig_id, cis_index) (cig_id << 8 | cis_index)

  void ConfigureIsoManagerMock() {
    iso_manager_ = bluetooth::hci::IsoManager::GetInstance();
    ASSERT_NE(iso_manager_, nullptr);
    iso_manager_->Start();

    mock_iso_manager_ = MockIsoManager::GetInstance();
    ASSERT_NE(mock_iso_manager_, nullptr);

    ON_CALL(*mock_iso_manager_, CreateCig)
        .WillByDefault(
            [this](uint8_t cig_id,
                   bluetooth::hci::iso_manager::cig_create_params p) {
              DLOG(INFO) << "CreateCig";

              auto& group = le_audio_device_groups_[cig_id];
              if (group) {
                std::vector<uint16_t> conn_handles;
                // Fake connection ID for each cis in a request
                for (auto i = 0u; i < p.cis_cfgs.size(); ++i) {
                  conn_handles.push_back(UNIQUE_CIS_CONN_HANDLE(cig_id, i));
                }
                auto status = HCI_SUCCESS;
                if (group_create_command_disallowed_) {
                  group_create_command_disallowed_ = false;
                  status = HCI_ERR_COMMAND_DISALLOWED;
                }

                LeAudioGroupStateMachine::Get()->ProcessHciNotifOnCigCreate(
                    group.get(), status, cig_id, conn_handles);
              }
            });

    ON_CALL(*mock_iso_manager_, RemoveCig)
        .WillByDefault([this](uint8_t cig_id, bool force) {
          DLOG(INFO) << "CreateRemove";

          auto& group = le_audio_device_groups_[cig_id];
          if (group) {
            // Fake connection ID for each cis in a request
            LeAudioGroupStateMachine::Get()->ProcessHciNotifOnCigRemove(
                0, group.get());
          }
        });

    ON_CALL(*mock_iso_manager_, SetupIsoDataPath)
        .WillByDefault([this](uint16_t conn_handle,
                              bluetooth::hci::iso_manager::iso_data_path_params
                                  p) {
          DLOG(INFO) << "SetupIsoDataPath";

          auto dev_it =
              std::find_if(le_audio_devices_.begin(), le_audio_devices_.end(),
                           [&conn_handle](auto& dev) {
                             auto ases = dev->GetAsesByCisConnHdl(conn_handle);
                             return (ases.sink || ases.source);
                           });
          if (dev_it == le_audio_devices_.end()) {
            DLOG(ERROR) << "Device not found";
            return;
          }

          for (auto& kv_pair : le_audio_device_groups_) {
            auto& group = kv_pair.second;
            if (group->IsDeviceInTheGroup(dev_it->get())) {
              LeAudioGroupStateMachine::Get()->ProcessHciNotifSetupIsoDataPath(
                  group.get(), dev_it->get(), 0, conn_handle);
              return;
            }
          }
        });

    ON_CALL(*mock_iso_manager_, RemoveIsoDataPath)
        .WillByDefault([this](uint16_t conn_handle, uint8_t iso_direction) {
          DLOG(INFO) << "RemoveIsoDataPath";

          auto dev_it =
              std::find_if(le_audio_devices_.begin(), le_audio_devices_.end(),
                           [&conn_handle](auto& dev) {
                             auto ases = dev->GetAsesByCisConnHdl(conn_handle);
                             return (ases.sink || ases.source);
                           });
          if (dev_it == le_audio_devices_.end()) {
            DLOG(ERROR) << "Device not found";
            return;
          }

          for (auto& kv_pair : le_audio_device_groups_) {
            auto& group = kv_pair.second;
            if (group->IsDeviceInTheGroup(dev_it->get())) {
              LeAudioGroupStateMachine::Get()->ProcessHciNotifRemoveIsoDataPath(
                  group.get(), dev_it->get(), 0, conn_handle);
              return;
            }
          }
        });

    ON_CALL(*mock_iso_manager_, EstablishCis)
        .WillByDefault([this](bluetooth::hci::iso_manager::cis_establish_params
                                  conn_params) {
          DLOG(INFO) << "EstablishCis";

          for (auto& pair : conn_params.conn_pairs) {
            auto dev_it = std::find_if(
                le_audio_devices_.begin(), le_audio_devices_.end(),
                [&pair](auto& dev) {
                  auto ases = dev->GetAsesByCisConnHdl(pair.cis_conn_handle);
                  return (ases.sink || ases.source);
                });
            if (dev_it == le_audio_devices_.end()) {
              DLOG(ERROR) << "Device not found";
              return;
            }

            for (auto& kv_pair : le_audio_device_groups_) {
              auto& group = kv_pair.second;
              if (group->IsDeviceInTheGroup(dev_it->get())) {
                bluetooth::hci::iso_manager::cis_establish_cmpl_evt evt;

                // Fill proper values if needed
                evt.status = 0x00;
                evt.cig_id = group->group_id_;
                evt.cis_conn_hdl = pair.cis_conn_handle;
                evt.cig_sync_delay = 0;
                evt.cis_sync_delay = 0;
                evt.trans_lat_mtos = 0;
                evt.trans_lat_stom = 0;
                evt.phy_mtos = 0;
                evt.phy_stom = 0;
                evt.nse = 0;
                evt.bn_mtos = 0;
                evt.bn_stom = 0;
                evt.ft_mtos = 0;
                evt.ft_stom = 0;
                evt.max_pdu_mtos = 0;
                evt.max_pdu_stom = 0;
                evt.iso_itv = 0;

                LeAudioGroupStateMachine::Get()->ProcessHciNotifCisEstablished(
                    group.get(), dev_it->get(), &evt);
                break;
              }
            }
          }
        });

    ON_CALL(*mock_iso_manager_, DisconnectCis)
        .WillByDefault([this](uint16_t cis_handle, uint8_t reason) {
          DLOG(INFO) << "DisconnectCis";

          auto dev_it =
              std::find_if(le_audio_devices_.begin(), le_audio_devices_.end(),
                           [&cis_handle](auto& dev) {
                             auto ases = dev->GetAsesByCisConnHdl(cis_handle);
                             return (ases.sink || ases.source);
                           });
          if (dev_it == le_audio_devices_.end()) {
            DLOG(ERROR) << "Device not found";
            return;
          }

          // When we disconnect the remote with HCI_ERR_PEER_USER, we
          // should be getting HCI_ERR_CONN_CAUSE_LOCAL_HOST from HCI.
          if (reason == HCI_ERR_PEER_USER) {
            reason = HCI_ERR_CONN_CAUSE_LOCAL_HOST;
          }

          for (auto& kv_pair : le_audio_device_groups_) {
            auto& group = kv_pair.second;
            if (group->IsDeviceInTheGroup(dev_it->get())) {
              bluetooth::hci::iso_manager::cis_disconnected_evt evt{
                  .reason = reason,
                  .cig_id = static_cast<uint8_t>(group->group_id_),
                  .cis_conn_hdl = cis_handle,
              };
              LeAudioGroupStateMachine::Get()->ProcessHciNotifCisDisconnected(
                  group.get(), dev_it->get(), &evt);
              return;
            }
          }
        });
  }

  void ConfigCodecManagerMock() {
    codec_manager_ = le_audio::CodecManager::GetInstance();
    ASSERT_NE(codec_manager_, nullptr);
    std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
        mock_offloading_preference(0);
    codec_manager_->Start(mock_offloading_preference);
    mock_codec_manager_ = MockCodecManager::GetInstance();
    ASSERT_NE(mock_codec_manager_, nullptr);
    ON_CALL(*mock_codec_manager_, GetCodecLocation())
        .WillByDefault(Return(types::CodecLocation::HOST));
  }

  void TearDown() override {
    /* Clear the alarm on tear down in case test case ends when the
     * alarm is scheduled
     */
    alarm_cancel(nullptr);

    iso_manager_->Stop();
    mock_iso_manager_ = nullptr;
    codec_manager_->Stop();
    mock_codec_manager_ = nullptr;

    gatt::SetMockBtaGattQueue(nullptr);
    gatt::SetMockBtaGattInterface(nullptr);
    bluetooth::manager::SetMockBtmInterface(nullptr);
    controller::SetMockControllerInterface(nullptr);

    for (auto i = 0u; i <= ascs::kAseCtpOpcodeMaxVal; ++i)
      ase_ctp_handlers[i] = nullptr;

    le_audio_devices_.clear();
    addresses_.clear();
    cached_codec_configuration_map_.clear();
    cached_ase_to_cis_id_map_.clear();
    LeAudioGroupStateMachine::Cleanup();
    ::le_audio::AudioSetConfigurationProvider::Cleanup();
  }

  std::shared_ptr<LeAudioDevice> PrepareConnectedDevice(
      uint8_t id, DeviceConnectState initial_connect_state, uint8_t num_ase_snk,
      uint8_t num_ase_src) {
    auto leAudioDevice = std::make_shared<LeAudioDevice>(GetTestAddress(id),
                                                         initial_connect_state);
    leAudioDevice->conn_id_ = id;
    leAudioDevice->SetConnectionState(DeviceConnectState::CONNECTED);

    uint16_t attr_handle = ATTR_HANDLE_ASCS_POOL_START;
    leAudioDevice->snk_audio_locations_hdls_.val_hdl = attr_handle++;
    leAudioDevice->snk_audio_locations_hdls_.ccc_hdl = attr_handle++;
    leAudioDevice->src_audio_locations_hdls_.val_hdl = attr_handle++;
    leAudioDevice->src_audio_locations_hdls_.ccc_hdl = attr_handle++;
    leAudioDevice->audio_avail_hdls_.val_hdl = attr_handle++;
    leAudioDevice->audio_avail_hdls_.ccc_hdl = attr_handle++;
    leAudioDevice->audio_supp_cont_hdls_.val_hdl = attr_handle++;
    leAudioDevice->audio_supp_cont_hdls_.ccc_hdl = attr_handle++;
    leAudioDevice->ctp_hdls_.val_hdl = attr_handle++;
    leAudioDevice->ctp_hdls_.ccc_hdl = attr_handle++;

    // Add some Sink ASEs
    while (num_ase_snk) {
      types::ase ase(0, 0, 0x01);
      ase.hdls.val_hdl = attr_handle++;
      ase.hdls.ccc_hdl = attr_handle++;

      leAudioDevice->ases_.emplace_back(std::move(ase));
      num_ase_snk--;
    }

    // Add some Source ASEs
    while (num_ase_src) {
      types::ase ase(0, 0, 0x02);
      ase.hdls.val_hdl = attr_handle++;
      ase.hdls.ccc_hdl = attr_handle++;

      leAudioDevice->ases_.emplace_back(std::move(ase));
      num_ase_src--;
    }

    le_audio_devices_.push_back(leAudioDevice);
    addresses_.push_back(leAudioDevice->address_);

    return std::move(leAudioDevice);
  }

  LeAudioDeviceGroup* GroupTheDevice(
      int group_id, const std::shared_ptr<LeAudioDevice>& leAudioDevice) {
    if (le_audio_device_groups_.count(group_id) == 0) {
      le_audio_device_groups_[group_id] =
          std::make_unique<LeAudioDeviceGroup>(group_id);
    }

    auto& group = le_audio_device_groups_[group_id];

    group->AddNode(leAudioDevice);
    if (group->IsEmpty()) return nullptr;

    return &(*group);
  }

  void InjectAseStateNotification(types::ase* ase, LeAudioDevice* device,
                                  LeAudioDeviceGroup* group, uint8_t new_state,
                                  void* new_state_params) {
    // Prepare additional params
    switch (new_state) {
      case ascs::kAseStateCodecConfigured: {
        client_parser::ascs::ase_codec_configured_state_params* conf =
            static_cast<
                client_parser::ascs::ase_codec_configured_state_params*>(
                new_state_params);
        std::vector<uint8_t> notif_value(25 + conf->codec_spec_conf.size());
        auto* p = notif_value.data();

        UINT8_TO_STREAM(p, ase->id == types::ase::kAseIdInvalid
                               ? ++ase_id_last_assigned
                               : ase->id);
        UINT8_TO_STREAM(p, new_state);

        UINT8_TO_STREAM(p, conf->framing);
        UINT8_TO_STREAM(p, conf->preferred_phy);
        UINT8_TO_STREAM(p, conf->preferred_retrans_nb);
        UINT16_TO_STREAM(p, conf->max_transport_latency);
        UINT24_TO_STREAM(p, conf->pres_delay_min);
        UINT24_TO_STREAM(p, conf->pres_delay_max);
        UINT24_TO_STREAM(p, conf->preferred_pres_delay_min);
        UINT24_TO_STREAM(p, conf->preferred_pres_delay_max);

        // CodecID:
        UINT8_TO_STREAM(p, conf->codec_id.coding_format);
        UINT16_TO_STREAM(p, conf->codec_id.vendor_company_id);
        UINT16_TO_STREAM(p, conf->codec_id.vendor_codec_id);

        // Codec Spec. Conf. Length and Data
        UINT8_TO_STREAM(p, conf->codec_spec_conf.size());
        memcpy(p, conf->codec_spec_conf.data(), conf->codec_spec_conf.size());

        LeAudioGroupStateMachine::Get()->ProcessGattNotifEvent(
            notif_value.data(), notif_value.size(), ase, device, group);
      } break;

      case ascs::kAseStateQoSConfigured: {
        client_parser::ascs::ase_qos_configured_state_params* conf =
            static_cast<client_parser::ascs::ase_qos_configured_state_params*>(
                new_state_params);
        std::vector<uint8_t> notif_value(17);
        auto* p = notif_value.data();

        // Prepare header
        UINT8_TO_STREAM(p, ase->id);
        UINT8_TO_STREAM(p, new_state);

        UINT8_TO_STREAM(p, conf->cig_id);
        UINT8_TO_STREAM(p, conf->cis_id);
        UINT24_TO_STREAM(p, conf->sdu_interval);
        UINT8_TO_STREAM(p, conf->framing);
        UINT8_TO_STREAM(p, conf->phy);
        UINT16_TO_STREAM(p, conf->max_sdu);
        UINT8_TO_STREAM(p, conf->retrans_nb);
        UINT16_TO_STREAM(p, conf->max_transport_latency);
        UINT24_TO_STREAM(p, conf->pres_delay);

        LeAudioGroupStateMachine::Get()->ProcessGattNotifEvent(
            notif_value.data(), notif_value.size(), ase, device, group);
      } break;

      case ascs::kAseStateEnabling:
        // fall-through
      case ascs::kAseStateStreaming:
        // fall-through
      case ascs::kAseStateDisabling: {
        client_parser::ascs::ase_transient_state_params* params =
            static_cast<client_parser::ascs::ase_transient_state_params*>(
                new_state_params);
        std::vector<uint8_t> notif_value(5 + params->metadata.size());
        auto* p = notif_value.data();

        // Prepare header
        UINT8_TO_STREAM(p, ase->id);
        UINT8_TO_STREAM(p, new_state);

        UINT8_TO_STREAM(p, group->group_id_);
        UINT8_TO_STREAM(p, ase->cis_id);
        UINT8_TO_STREAM(p, params->metadata.size());
        memcpy(p, params->metadata.data(), params->metadata.size());

        LeAudioGroupStateMachine::Get()->ProcessGattNotifEvent(
            notif_value.data(), notif_value.size(), ase, device, group);
      } break;

      case ascs::kAseStateReleasing:
        // fall-through
      case ascs::kAseStateIdle: {
        std::vector<uint8_t> notif_value(2);
        auto* p = notif_value.data();

        // Prepare header
        UINT8_TO_STREAM(p, ase->id == types::ase::kAseIdInvalid
                               ? ++ase_id_last_assigned
                               : ase->id);
        UINT8_TO_STREAM(p, new_state);

        LeAudioGroupStateMachine::Get()->ProcessGattNotifEvent(
            notif_value.data(), notif_value.size(), ase, device, group);
      } break;

      default:
        break;
    };
  }

  static void InsertPacRecord(
      std::vector<types::acs_ac_record>& recs,
      uint16_t sampling_frequencies_bitfield,
      uint8_t supported_frame_durations_bitfield,
      uint8_t audio_channel_count_bitfield,
      uint16_t supported_octets_per_codec_frame_min,
      uint16_t supported_octets_per_codec_frame_max,
      uint8_t coding_format = codec_specific::kLc3CodingFormat,
      uint16_t vendor_company_id = 0x0000, uint16_t vendor_codec_id = 0x0000,
      std::vector<uint8_t> metadata = {}) {
    recs.push_back({
        .codec_id =
            {
                .coding_format = coding_format,
                .vendor_company_id = vendor_company_id,
                .vendor_codec_id = vendor_codec_id,
            },
        .codec_spec_caps = types::LeAudioLtvMap({
            {codec_specific::kCapTypeSupportedSamplingFrequencies,
             {(uint8_t)(sampling_frequencies_bitfield),
              (uint8_t)(sampling_frequencies_bitfield >> 8)}},
            {codec_specific::kCapTypeSupportedFrameDurations,
             {supported_frame_durations_bitfield}},
            {codec_specific::kCapTypeAudioChannelCount,
             {audio_channel_count_bitfield}},
            {codec_specific::kCapTypeSupportedOctetsPerCodecFrame,
             {
                 // Min
                 (uint8_t)(supported_octets_per_codec_frame_min),
                 (uint8_t)(supported_octets_per_codec_frame_min >> 8),
                 // Max
                 (uint8_t)(supported_octets_per_codec_frame_max),
                 (uint8_t)(supported_octets_per_codec_frame_max >> 8),
             }},
        }),
        .metadata = std::move(metadata),
    });
  }

  void InjectInitialIdleNotification(LeAudioDeviceGroup* group) {
    for (auto* device = group->GetFirstDevice(); device != nullptr;
         device = group->GetNextDevice(device)) {
      for (auto& ase : device->ases_) {
        InjectAseStateNotification(&ase, device, group, ascs::kAseStateIdle,
                                   nullptr);
      }
    }
  }

  void MultipleTestDevicePrepare(int leaudio_group_id,
                                 LeAudioContextType context_type,
                                 uint16_t device_cnt,
                                 types::AudioContexts update_contexts,
                                 bool insert_default_pac_records = true) {
    // Prepare fake connected device group
    DeviceConnectState initial_connect_state =
        DeviceConnectState::CONNECTING_BY_USER;
    int total_devices = device_cnt;
    le_audio::LeAudioDeviceGroup* group = nullptr;

    uint8_t num_ase_snk;
    uint8_t num_ase_src;
    switch (context_type) {
      case kContextTypeRingtone:
        num_ase_snk = 1 + additional_snk_ases;
        num_ase_src = 0 + additional_src_ases;
        break;

      case kContextTypeMedia:
        num_ase_snk = 2 + additional_snk_ases;
        num_ase_src = 0 + additional_src_ases;
        break;

      case kContextTypeConversational:
        num_ase_snk = 1 + additional_snk_ases;
        num_ase_src = 1 + additional_src_ases;
        break;

      default:
        ASSERT_TRUE(false);
    }

    while (device_cnt) {
      auto leAudioDevice = PrepareConnectedDevice(
          device_cnt--, initial_connect_state, num_ase_snk, num_ase_src);

      if (insert_default_pac_records) {
        uint16_t attr_handle = ATTR_HANDLE_PACS_POOL_START;

        /* As per spec, unspecified shall be supported */
        auto snk_context_type = kContextTypeUnspecified | update_contexts;
        auto src_context_type = kContextTypeUnspecified | update_contexts;

        // Prepare Sink Published Audio Capability records
        if ((kContextTypeRingtone | kContextTypeMedia |
             kContextTypeConversational)
                .test(context_type)) {
          // Set target ASE configurations
          std::vector<types::acs_ac_record> pac_recs;

          InsertPacRecord(pac_recs, sample_freq_,
                          codec_specific::kCapFrameDuration10ms |
                              codec_specific::kCapFrameDuration7p5ms |
                              codec_specific::kCapFrameDuration10msPreferred,
                          channel_count_, 30, 120);

          types::hdl_pair handle_pair;
          handle_pair.val_hdl = attr_handle++;
          handle_pair.ccc_hdl = attr_handle++;

          leAudioDevice->snk_pacs_.emplace_back(
              std::make_tuple(std::move(handle_pair), pac_recs));

          snk_context_type.set(static_cast<LeAudioContextType>(context_type));
          leAudioDevice->snk_audio_locations_ =
              ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft |
              ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
        }

        // Prepare Source Published Audio Capability records
        if (context_type == kContextTypeConversational) {
          // Set target ASE configurations
          std::vector<types::acs_ac_record> pac_recs;

          InsertPacRecord(pac_recs,
                          codec_specific::kCapSamplingFrequency16000Hz,
                          codec_specific::kCapFrameDuration10ms |
                              codec_specific::kCapFrameDuration7p5ms |
                              codec_specific::kCapFrameDuration10msPreferred,
                          0b00000001, 30, 120);

          types::hdl_pair handle_pair;
          handle_pair.val_hdl = attr_handle++;
          handle_pair.ccc_hdl = attr_handle++;

          leAudioDevice->src_pacs_.emplace_back(
              std::make_tuple(std::move(handle_pair), pac_recs));
          src_context_type.set(
              static_cast<LeAudioContextType>(kContextTypeConversational));

          leAudioDevice->src_audio_locations_ =
              ::le_audio::codec_spec_conf::kLeAudioLocationFrontLeft |
              ::le_audio::codec_spec_conf::kLeAudioLocationFrontRight;
        }

        leAudioDevice->SetSupportedContexts(snk_context_type, src_context_type);
        leAudioDevice->SetAvailableContexts(snk_context_type, src_context_type);
      }

      group = GroupTheDevice(leaudio_group_id, std::move(leAudioDevice));
      /* Set the location and direction to the group (done in client.cc)*/
      group->ReloadAudioLocations();
      group->ReloadAudioDirections();
    }

    /* Stimulate update of available context map */
    auto types_set = update_contexts.any() ? context_type | update_contexts
                                           : types::AudioContexts(context_type);
    group->UpdateAudioContextTypeAvailability(types_set);

    ASSERT_NE(group, nullptr);
    ASSERT_EQ(group->Size(), total_devices);
  }

  LeAudioDeviceGroup* PrepareSingleTestDeviceGroup(
      int leaudio_group_id, LeAudioContextType context_type,
      uint16_t device_cnt = 1,
      types::AudioContexts update_contexts = types::AudioContexts()) {
    MultipleTestDevicePrepare(leaudio_group_id, context_type, device_cnt,
                              update_contexts);
    return le_audio_device_groups_.count(leaudio_group_id)
               ? le_audio_device_groups_[leaudio_group_id].get()
               : nullptr;
  }

  void PrepareConfigureCodecHandler(LeAudioDeviceGroup* group,
                                    int verify_ase_count = 0,
                                    bool caching = false) {
    ase_ctp_handlers[ascs::kAseCtpOpcodeConfigureCodec] =
        [group, verify_ase_count, caching, this](
            LeAudioDevice* device, std::vector<uint8_t> value,
            GATT_WRITE_OP_CB cb, void* cb_data) {
          auto num_ase = value[1];

          // Verify ase count if needed
          if (verify_ase_count) ASSERT_EQ(verify_ase_count, num_ase);

          // Inject Configured ASE state notification for each requested ASE
          auto* ase_p = &value[2];
          for (auto i = 0u; i < num_ase; ++i) {
            client_parser::ascs::ase_codec_configured_state_params
                codec_configured_state_params;

            /* Check if this is a valid ASE ID  */
            auto ase_id = *ase_p++;
            auto it = std::find_if(
                device->ases_.begin(), device->ases_.end(),
                [ase_id](auto& ase) { return (ase.id == ase_id); });
            ASSERT_NE(it, device->ases_.end());
            const auto ase = &(*it);

            // Skip target latency param
            ase_p++;

            codec_configured_state_params.preferred_phy = *ase_p++;
            codec_configured_state_params.codec_id.coding_format = ase_p[0];
            codec_configured_state_params.codec_id.vendor_company_id =
                (uint16_t)(ase_p[1] << 8 | ase_p[2]),
            codec_configured_state_params.codec_id.vendor_codec_id =
                (uint16_t)(ase_p[3] << 8 | ase_p[4]),
            ase_p += 5;

            auto codec_spec_param_len = *ase_p++;
            auto num_handled_bytes = ase_p - value.data();
            codec_configured_state_params.codec_spec_conf =
                std::vector<uint8_t>(
                    value.begin() + num_handled_bytes,
                    value.begin() + num_handled_bytes + codec_spec_param_len);
            ase_p += codec_spec_param_len;

            // Some initial QoS settings
            codec_configured_state_params.framing =
                ascs::kAseParamFramingUnframedSupported;
            codec_configured_state_params.preferred_retrans_nb = 0x04;
            codec_configured_state_params.max_transport_latency = 0x0010;
            codec_configured_state_params.pres_delay_min = 0xABABAB;
            codec_configured_state_params.pres_delay_max = 0xCDCDCD;
            codec_configured_state_params.preferred_pres_delay_min =
                types::kPresDelayNoPreference;
            codec_configured_state_params.preferred_pres_delay_max =
                types::kPresDelayNoPreference;

            if (caching) {
              cached_codec_configuration_map_[ase_id] =
                  codec_configured_state_params;
            }
            InjectAseStateNotification(ase, device, group,
                                       ascs::kAseStateCodecConfigured,
                                       &codec_configured_state_params);
          }
        };
  }

  void PrepareConfigureQosHandler(LeAudioDeviceGroup* group,
                                  int verify_ase_count = 0,
                                  bool caching = false) {
    ase_ctp_handlers[ascs::kAseCtpOpcodeConfigureQos] =
        [group, verify_ase_count, caching, this](
            LeAudioDevice* device, std::vector<uint8_t> value,
            GATT_WRITE_OP_CB cb, void* cb_data) {
          auto num_ase = value[1];

          // Verify ase count if needed
          if (verify_ase_count) ASSERT_EQ(verify_ase_count, num_ase);

          // Inject Configured QoS state notification for each requested ASE
          auto* ase_p = &value[2];
          for (auto i = 0u; i < num_ase; ++i) {
            client_parser::ascs::ase_qos_configured_state_params
                qos_configured_state_params;

            /* Check if this is a valid ASE ID  */
            auto ase_id = *ase_p++;
            auto it = std::find_if(
                device->ases_.begin(), device->ases_.end(),
                [ase_id](auto& ase) { return (ase.id == ase_id); });
            ASSERT_NE(it, device->ases_.end());
            const auto ase = &(*it);

            qos_configured_state_params.cig_id = *ase_p++;
            qos_configured_state_params.cis_id = *ase_p++;

            qos_configured_state_params.sdu_interval =
                (uint32_t)((ase_p[0] << 16) | (ase_p[1] << 8) | ase_p[2]);
            ase_p += 3;

            qos_configured_state_params.framing = *ase_p++;
            qos_configured_state_params.phy = *ase_p++;
            qos_configured_state_params.max_sdu =
                (uint16_t)((ase_p[0] << 8) | ase_p[1]);
            ase_p += 2;

            qos_configured_state_params.retrans_nb = *ase_p++;
            qos_configured_state_params.max_transport_latency =
                (uint16_t)((ase_p[0] << 8) | ase_p[1]);
            ase_p += 2;

            qos_configured_state_params.pres_delay =
                (uint16_t)((ase_p[0] << 16) | (ase_p[1] << 8) | ase_p[2]);
            ase_p += 3;

            if (caching) {
              LOG(INFO) << __func__ << " Device: " << device->address_;
              if (cached_ase_to_cis_id_map_.count(device->address_) > 0) {
                auto ase_list = cached_ase_to_cis_id_map_.at(device->address_);
                if (ase_list.count(ase_id) > 0) {
                  auto cis_id = ase_list.at(ase_id);
                  ASSERT_EQ(cis_id, qos_configured_state_params.cis_id);
                } else {
                  ase_list[ase_id] = qos_configured_state_params.cis_id;
                }
              } else {
                std::map<int, int> ase_map;
                ase_map[ase_id] = qos_configured_state_params.cis_id;

                cached_ase_to_cis_id_map_[device->address_] = ase_map;
              }
            }

            InjectAseStateNotification(ase, device, group,
                                       ascs::kAseStateQoSConfigured,
                                       &qos_configured_state_params);
          }
        };
  }

  void PrepareEnableHandler(LeAudioDeviceGroup* group, int verify_ase_count = 0,
                            bool inject_enabling = true) {
    ase_ctp_handlers[ascs::kAseCtpOpcodeEnable] =
        [group, verify_ase_count, inject_enabling, this](
            LeAudioDevice* device, std::vector<uint8_t> value,
            GATT_WRITE_OP_CB cb, void* cb_data) {
          auto num_ase = value[1];

          // Verify ase count if needed
          if (verify_ase_count) ASSERT_EQ(verify_ase_count, num_ase);

          // Inject Streaming ASE state notification for each requested ASE
          auto* ase_p = &value[2];
          for (auto i = 0u; i < num_ase; ++i) {
            /* Check if this is a valid ASE ID  */
            auto ase_id = *ase_p++;
            auto it = std::find_if(
                device->ases_.begin(), device->ases_.end(),
                [ase_id](auto& ase) { return (ase.id == ase_id); });
            ASSERT_NE(it, device->ases_.end());
            const auto ase = &(*it);

            auto meta_len = *ase_p++;
            auto num_handled_bytes = ase_p - value.data();
            ase_p += meta_len;

            client_parser::ascs::ase_transient_state_params enable_params = {
                .metadata = std::vector<uint8_t>(
                    value.begin() + num_handled_bytes,
                    value.begin() + num_handled_bytes + meta_len)};

            // Server does the 'ReceiverStartReady' on its own - goes to
            // Streaming, when in Sink role
            if (ase->direction & le_audio::types::kLeAudioDirectionSink) {
              if (inject_enabling)
                InjectAseStateNotification(ase, device, group,
                                           ascs::kAseStateEnabling,
                                           &enable_params);
              InjectAseStateNotification(
                  ase, device, group, ascs::kAseStateStreaming, &enable_params);
            } else {
              InjectAseStateNotification(
                  ase, device, group, ascs::kAseStateEnabling, &enable_params);
            }
          }
        };
  }

  void PrepareDisableHandler(LeAudioDeviceGroup* group,
                             int verify_ase_count = 0) {
    ase_ctp_handlers[ascs::kAseCtpOpcodeDisable] =
        [group, verify_ase_count, this](LeAudioDevice* device,
                                        std::vector<uint8_t> value,
                                        GATT_WRITE_OP_CB cb, void* cb_data) {
          auto num_ase = value[1];

          // Verify ase count if needed
          if (verify_ase_count) ASSERT_EQ(verify_ase_count, num_ase);
          ASSERT_EQ(value.size(), 2ul + num_ase);

          // Inject Disabling & QoS Conf. ASE state notification for each ASE
          auto* ase_p = &value[2];
          for (auto i = 0u; i < num_ase; ++i) {
            /* Check if this is a valid ASE ID  */
            auto ase_id = *ase_p++;
            auto it = std::find_if(
                device->ases_.begin(), device->ases_.end(),
                [ase_id](auto& ase) { return (ase.id == ase_id); });
            ASSERT_NE(it, device->ases_.end());
            const auto ase = &(*it);

            // The Disabling state is present for Source ASE
            if (ase->direction & le_audio::types::kLeAudioDirectionSource) {
              client_parser::ascs::ase_transient_state_params disabling_params =
                  {.metadata = {}};
              InjectAseStateNotification(ase, device, group,
                                         ascs::kAseStateDisabling,
                                         &disabling_params);
            }

            // Server does the 'ReceiverStopReady' on its own - goes to
            // Streaming, when in Sink role
            if (ase->direction & le_audio::types::kLeAudioDirectionSink) {
              // FIXME: For now our fake peer does not remember qos params
              client_parser::ascs::ase_qos_configured_state_params
                  qos_configured_state_params;
              InjectAseStateNotification(ase, device, group,
                                         ascs::kAseStateQoSConfigured,
                                         &qos_configured_state_params);
            }
          }
        };
  }

  void PrepareReceiverStartReady(LeAudioDeviceGroup* group,
                                 int verify_ase_count = 0) {
    ase_ctp_handlers[ascs::kAseCtpOpcodeReceiverStartReady] =
        [group, verify_ase_count, this](LeAudioDevice* device,
                                        std::vector<uint8_t> value,
                                        GATT_WRITE_OP_CB cb, void* cb_data) {
          auto num_ase = value[1];

          // Verify ase count if needed
          if (verify_ase_count) ASSERT_EQ(verify_ase_count, num_ase);

          // Inject Streaming ASE state notification for each Source ASE
          auto* ase_p = &value[2];
          for (auto i = 0u; i < num_ase; ++i) {
            /* Check if this is a valid ASE ID  */
            auto ase_id = *ase_p++;
            auto it = std::find_if(
                device->ases_.begin(), device->ases_.end(),
                [ase_id](auto& ase) { return (ase.id == ase_id); });
            ASSERT_NE(it, device->ases_.end());

            // Once we did the 'ReceiverStartReady' the server goes to
            // Streaming, when in Source role
            auto meta_len = *ase_p++;
            auto num_handled_bytes = ase_p - value.data();
            ase_p += num_handled_bytes;

            const auto& ase = &(*it);
            client_parser::ascs::ase_transient_state_params enable_params = {
                .metadata = std::vector<uint8_t>(
                    value.begin() + num_handled_bytes,
                    value.begin() + num_handled_bytes + meta_len)};
            InjectAseStateNotification(
                ase, device, group, ascs::kAseStateStreaming, &enable_params);
          }
        };
  }

  void PrepareReceiverStopReady(LeAudioDeviceGroup* group,
                                int verify_ase_count = 0) {
    ase_ctp_handlers[ascs::kAseCtpOpcodeReceiverStopReady] =
        [group, verify_ase_count, this](LeAudioDevice* device,
                                        std::vector<uint8_t> value,
                                        GATT_WRITE_OP_CB cb, void* cb_data) {
          auto num_ase = value[1];

          // Verify ase count if needed
          if (verify_ase_count) ASSERT_EQ(verify_ase_count, num_ase);

          // Inject QoS configured ASE state notification for each Source ASE
          auto* ase_p = &value[2];
          for (auto i = 0u; i < num_ase; ++i) {
            /* Check if this is a valid ASE ID  */
            auto ase_id = *ase_p++;
            auto it = std::find_if(
                device->ases_.begin(), device->ases_.end(),
                [ase_id](auto& ase) { return (ase.id == ase_id); });
            ASSERT_NE(it, device->ases_.end());

            const auto& ase = &(*it);

            // FIXME: For now our fake peer does not remember qos params
            client_parser::ascs::ase_qos_configured_state_params
                qos_configured_state_params;
            InjectAseStateNotification(ase, device, group,
                                       ascs::kAseStateQoSConfigured,
                                       &qos_configured_state_params);
          }
        };
  }

  void PrepareReleaseHandler(LeAudioDeviceGroup* group,
                             int verify_ase_count = 0) {
    ase_ctp_handlers[ascs::kAseCtpOpcodeRelease] =
        [group, verify_ase_count, this](LeAudioDevice* device,
                                        std::vector<uint8_t> value,
                                        GATT_WRITE_OP_CB cb, void* cb_data) {
          auto num_ase = value[1];

          // Verify ase count if needed
          if (verify_ase_count) ASSERT_EQ(verify_ase_count, num_ase);
          ASSERT_EQ(value.size(), 2ul + num_ase);

          // Inject Releasing & Idle ASE state notification for each ASE
          auto* ase_p = &value[2];
          for (auto i = 0u; i < num_ase; ++i) {
            /* Check if this is a valid ASE ID  */
            auto ase_id = *ase_p++;
            auto it = std::find_if(
                device->ases_.begin(), device->ases_.end(),
                [ase_id](auto& ase) { return (ase.id == ase_id); });
            ASSERT_NE(it, device->ases_.end());
            const auto ase = &(*it);

            InjectAseStateNotification(ase, device, group,
                                       ascs::kAseStateReleasing, nullptr);

            /* Check if codec configuration is cached */
            if (cached_codec_configuration_map_.count(ase_id) > 0) {
              InjectAseStateNotification(
                  ase, device, group, ascs::kAseStateCodecConfigured,
                  &cached_codec_configuration_map_[ase_id]);
            } else {
              // Release - no caching
              InjectAseStateNotification(ase, device, group,
                                         ascs::kAseStateIdle, nullptr);
            }
          }
        };
  }

  MockCsisClient mock_csis_client_module_;
  NiceMock<controller::MockControllerInterface> mock_controller_;
  NiceMock<bluetooth::manager::MockBtmInterface> btm_interface;
  gatt::MockBtaGattInterface gatt_interface;
  gatt::MockBtaGattQueue gatt_queue;

  bluetooth::hci::IsoManager* iso_manager_;
  MockIsoManager* mock_iso_manager_;
  le_audio::CodecManager* codec_manager_;
  MockCodecManager* mock_codec_manager_;

  std::function<void(LeAudioDevice* device, std::vector<uint8_t> value,
                     GATT_WRITE_OP_CB cb, void* cb_data)>
      ase_ctp_handlers[ascs::kAseCtpOpcodeMaxVal + 1] = {nullptr};
  std::map<int, client_parser::ascs::ase_codec_configured_state_params>
      cached_codec_configuration_map_;

  std::map<RawAddress, std::map<int, int>> cached_ase_to_cis_id_map_;

  MockLeAudioGroupStateMachineCallbacks mock_callbacks_;
  std::vector<std::shared_ptr<LeAudioDevice>> le_audio_devices_;
  std::vector<RawAddress> addresses_;
  std::map<uint8_t, std::unique_ptr<LeAudioDeviceGroup>>
      le_audio_device_groups_;
  bool group_create_command_disallowed_ = false;
};

TEST_F(StateMachineTest, testInit) {
  ASSERT_NE(LeAudioGroupStateMachine::Get(), nullptr);
}

TEST_F(StateMachineTest, testCleanup) {
  ASSERT_NE(LeAudioGroupStateMachine::Get(), nullptr);
  LeAudioGroupStateMachine::Cleanup();
  EXPECT_DEATH(LeAudioGroupStateMachine::Get(), "");
}

TEST_F(StateMachineTest, testConfigureCodecSingle) {
  /* Device is banded headphones with 1x snk + 0x src ase
   * (1xunidirectional CIS) with channel count 2 (for stereo
   */
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 2;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  auto* leAudioDevice = group->GetFirstDevice();
  PrepareConfigureCodecHandler(group, 1);

  /* Start the configuration and stream Media content.
   * Expect 1 time for the Codec Config call only. */
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(1);

  /* Do nothing on the CigCreate, so the state machine stays in the configure
   * state */
  ON_CALL(*mock_iso_manager_, CreateCig).WillByDefault(Return());
  EXPECT_CALL(*mock_iso_manager_, CreateCig).Times(1);

  InjectInitialIdleNotification(group);

  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);

  /* Cancel is called when group goes to streaming. */
  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testConfigureCodecMulti) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 2;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);

  auto expected_devices_written = 0;
  auto* leAudioDevice = group->GetFirstDevice();
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(1));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  InjectInitialIdleNotification(group);

  /* Do nothing on the CigCreate, so the state machine stays in the configure
   * state */
  ON_CALL(*mock_iso_manager_, CreateCig).WillByDefault(Return());
  EXPECT_CALL(*mock_iso_manager_, CreateCig).Times(1);

  // Start the configuration and stream the content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);

  /* Cancel is called when group goes to streaming. */
  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testConfigureQosSingle) {
  /* Device is banded headphones with 2x snk + 1x src ase
   * (1x bidirectional + 1xunidirectional CIS)
   */
  additional_snk_ases = 1;
  additional_src_ases = 1;
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 3;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  auto* leAudioDevice = group->GetFirstDevice();
  PrepareConfigureCodecHandler(group, 2);
  PrepareConfigureQosHandler(group, 2);

  // Start the configuration and stream Media content
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(3);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, context_type, types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);

  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testConfigureQosSingleRecoverCig) {
  /* Device is banded headphones with 2x snk + 1x src ase
   * (1x bidirectional + 1xunidirectional CIS)
   */
  additional_snk_ases = 1;
  additional_src_ases = 1;
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 3;

  /* Assume that on previous BT OFF CIG was not removed */
  group_create_command_disallowed_ = true;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  auto* leAudioDevice = group->GetFirstDevice();
  PrepareConfigureCodecHandler(group, 2);
  PrepareConfigureQosHandler(group, 2);

  // Start the configuration and stream Media content
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(3);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);
  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testConfigureQosMultiple) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 3;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(2));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);
  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testStreamSingle) {
  /* Device is banded headphones with 1x snk + 0x src ase
   * (1xunidirectional CIS) with channel count 2 (for stereo
   */
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Ringtone with channel count 1 for single device and 1 ASE sink will
   * end up with 1 Sink ASE being configured.
   */
  PrepareConfigureCodecHandler(group, 1);
  PrepareConfigureQosHandler(group, 1);
  PrepareEnableHandler(group, 1);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(3);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testStreamSkipEnablingSink) {
  /* Device is banded headphones with 2x snk + none src ase
   * (2x unidirectional CIS)
   */
  const auto context_type = kContextTypeMedia;
  const int leaudio_group_id = 4;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* For Media context type with channel count 1 and two ASEs,
   * there should have be 2 Ases configured configured.
   */
  PrepareConfigureCodecHandler(group, 2);
  PrepareConfigureQosHandler(group, 2);
  PrepareEnableHandler(group, 2, false);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(3);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testStreamSkipEnablingSinkSource) {
  /* Device is banded headphones with 2x snk + 1x src ase
   * (1x bidirectional CIS)
   */
  const auto context_type = kContextTypeConversational;
  const int leaudio_group_id = 4;

  additional_snk_ases = 1;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Conversional context in mind,
   * 2 Sink ASEs and 1 Source ASE should have been configured.
   */
  PrepareConfigureCodecHandler(group, 3);
  PrepareConfigureQosHandler(group, 3);
  PrepareEnableHandler(group, 3, false);
  PrepareReceiverStartReady(group, 1);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(4);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(3);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testStreamMultipleConversational) {
  const auto context_type = kContextTypeConversational;
  const auto leaudio_group_id = 4;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareReceiverStartReady(group);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(AtLeast(1));
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(4);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(4);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testStreamMultiple) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 4;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(AtLeast(1));
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(3));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testUpdateMetadataMultiple) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 4;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(AtLeast(1));
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(3));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  testing::Mock::VerifyAndClearExpectations(&gatt_queue);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Make sure all devices get the metadata update
  leAudioDevice = group->GetFirstDevice();
  expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(1);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  const auto metadata_context_type =
      kContextTypeMedia | kContextTypeSoundEffects;
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      metadata_context_type));

  /* This is just update metadata - watchdog is not used */
  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testDisableSingle) {
  /* Device is banded headphones with 2x snk + 0x src ase
   * (2xunidirectional CIS)
   */
  additional_snk_ases = 1;
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Ringtone context plus additional ASE with channel count 1
   * gives us 2 ASE which should have been configured.
   */
  PrepareConfigureCodecHandler(group, 2);
  PrepareConfigureQosHandler(group, 2);
  PrepareEnableHandler(group, 2);
  PrepareDisableHandler(group, 2);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(4);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(
      *mock_iso_manager_,
      RemoveIsoDataPath(
          _, bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput))
      .Times(2);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::SUSPENDING));
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::SUSPENDED));

  // Suspend the stream
  LeAudioGroupStateMachine::Get()->SuspendStream(group);

  // Check if group has transition to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testDisableMultiple) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 4;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(4));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(
      *mock_iso_manager_,
      RemoveIsoDataPath(
          _, bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput))
      .Times(2);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::SUSPENDING));
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::SUSPENDED));

  // Suspend the stream
  LeAudioGroupStateMachine::Get()->SuspendStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);
  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testDisableBidirectional) {
  /* Device is banded headphones with 2x snk + 1x src ase
   * (1x bidirectional + 1xunidirectional CIS)
   */
  additional_snk_ases = 1;
  const auto context_type = kContextTypeConversational;
  const int leaudio_group_id = 4;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Conversional context in mind, Sink and Source
   * ASEs should have been configured.
   */
  PrepareConfigureCodecHandler(group, 3);
  PrepareConfigureQosHandler(group, 3);
  PrepareEnableHandler(group, 3);
  PrepareDisableHandler(group, 3);
  PrepareReceiverStartReady(group, 1);
  PrepareReceiverStopReady(group, 1);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(4));

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(3);
  bool removed_bidirectional = false;
  bool removed_unidirectional = false;

  /* Check data path removal */
  ON_CALL(*mock_iso_manager_, RemoveIsoDataPath)
      .WillByDefault(Invoke([&removed_bidirectional, &removed_unidirectional,
                             this](uint16_t conn_handle,
                                   uint8_t data_path_dir) {
        /* Set flags for verification */
        if (data_path_dir ==
            (bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput |
             bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionOutput)) {
          removed_bidirectional = true;
        } else if (data_path_dir == bluetooth::hci::iso_manager::
                                        kRemoveIsoDataPathDirectionInput) {
          removed_unidirectional = true;
        }

        /* Copied from default handler of RemoveIsoDataPath*/
        auto dev_it =
            std::find_if(le_audio_devices_.begin(), le_audio_devices_.end(),
                         [&conn_handle](auto& dev) {
                           auto ases = dev->GetAsesByCisConnHdl(conn_handle);
                           return (ases.sink || ases.source);
                         });
        if (dev_it == le_audio_devices_.end()) {
          return;
        }

        for (auto& kv_pair : le_audio_device_groups_) {
          auto& group = kv_pair.second;
          if (group->IsDeviceInTheGroup(dev_it->get())) {
            LeAudioGroupStateMachine::Get()->ProcessHciNotifRemoveIsoDataPath(
                group.get(), dev_it->get(), 0, conn_handle);
            return;
          }
        }
        /* End of copy */
      }));

  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::SUSPENDING));
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::SUSPENDED));

  // Suspend the stream
  LeAudioGroupStateMachine::Get()->SuspendStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);
  ASSERT_EQ(removed_bidirectional, true);
  ASSERT_EQ(removed_unidirectional, true);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testReleaseSingle) {
  /* Device is banded headphones with 1x snk + 0x src ase
   * (1xunidirectional CIS) with channel count 2 (for stereo)
   */
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  PrepareConfigureCodecHandler(group, 1);
  PrepareConfigureQosHandler(group, 1);
  PrepareEnableHandler(group, 1);
  PrepareDisableHandler(group, 1);
  PrepareReleaseHandler(group, 1);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(4);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;
  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));
  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::IDLE));

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(), types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testReleaseCachingSingle) {
  /* Device is banded headphones with 1x snk + 0x src ase
   * (1xunidirectional CIS)
   */
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  PrepareConfigureCodecHandler(group, 1, true);
  PrepareConfigureQosHandler(group, 1);
  PrepareEnableHandler(group, 1);
  PrepareDisableHandler(group, 1);
  PrepareReleaseHandler(group, 1);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(4);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  InjectInitialIdleNotification(group);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS));
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest,
       testStreamCaching_NoReconfigurationNeeded_SingleDevice) {
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  additional_snk_ases = 2;
  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind and with no Source
   * ASEs, therefor only one ASE should have been configured.
   */
  PrepareConfigureCodecHandler(group, 1, true);
  PrepareConfigureQosHandler(group, 1, true);
  PrepareEnableHandler(group, 1);
  PrepareDisableHandler(group, 1);
  PrepareReleaseHandler(group, 1);

  /* Ctp messages we expect:
   * 1. Codec Config
   * 2. QoS Config
   * 3. Enable
   * 4. Release
   * 5. QoS Config (because device stays in Configured state)
   * 6. Enable
   */
  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(6);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  InjectInitialIdleNotification(group);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS));

  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::STREAMING))
      .Times(2);

  // Start the configuration and stream Ringtone content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;
}

TEST_F(StateMachineTest,
       test_StreamCaching_ReconfigureForContextChange_SingleDevice) {
  auto context_type = kContextTypeConversational;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  additional_snk_ases = 2;
  /* Prepare fake connected device group with update of Media and Conversational
   * contexts
   */
  auto* group = PrepareSingleTestDeviceGroup(
      leaudio_group_id, context_type, 1,
      kContextTypeConversational | kContextTypeMedia);

  /* Don't validate ASE here, as after reconfiguration different ASE number
   * will be used.
   * For the first configuration (CONVERSTATIONAL) there will be 2 ASEs (Sink
   * and Source) After reconfiguration (MEDIA) there will be single ASE.
   */
  PrepareConfigureCodecHandler(group, 0, true);
  PrepareConfigureQosHandler(group, 0, true);
  PrepareEnableHandler(group);
  PrepareReceiverStartReady(group);
  PrepareReleaseHandler(group);

  /* Ctp messages we expect:
   * 1. Codec Config
   * 2. QoS Config
   * 3. Enable
   * 4. Release
   * 5. Codec Config
   * 6. QoS Config
   * 7. Enable
   */
  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(8);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(2);

  /* 2 times for first configuration (1 Sink, 1 Source), 1 time for second
   * configuration (1 Sink)*/
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(3);

  uint8_t value =
      bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionOutput |
      bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput;
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, value)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  InjectInitialIdleNotification(group);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS));

  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::STREAMING))
      .Times(2);

  // Start the configuration and stream Conversational content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Start the configuration and stream Media content
  context_type = kContextTypeMedia;
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testReleaseMultiple) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(4));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));
  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::IDLE));

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(), types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testReleaseBidirectional) {
  /* Device is banded headphones with 2x snk + 1x src ase
   * (1x bidirectional + 1xunidirectional CIS)
   */
  additional_snk_ases = 1;
  const auto context_type = kContextTypeConversational;
  const auto leaudio_group_id = 6;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Conversional context in mind, Sink and Source
   * ASEs should have been configured.
   */
  PrepareConfigureCodecHandler(group, 3);
  PrepareConfigureQosHandler(group, 3);
  PrepareEnableHandler(group, 3);
  PrepareDisableHandler(group, 3);
  PrepareReceiverStartReady(group, 1);
  PrepareReleaseHandler(group, 3);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(4));

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(3);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(), types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;
}

TEST_F(StateMachineTest, testDisableAndReleaseBidirectional) {
  /* Device is banded headphones with 2x snk + 1x src ase
   * (1x bidirectional + 1xunidirectional CIS)
   */
  additional_snk_ases = 1;
  const auto context_type = kContextTypeConversational;
  const int leaudio_group_id = 4;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Conversional context in mind, Sink and Source
   * ASEs should have been configured.
   */
  PrepareConfigureCodecHandler(group, 3);
  PrepareConfigureQosHandler(group, 3);
  PrepareEnableHandler(group, 3);
  PrepareDisableHandler(group, 3);
  PrepareReceiverStartReady(group, 1);
  PrepareReceiverStopReady(group, 1);
  PrepareReleaseHandler(group, 3);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(4));

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(3);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Suspend the stream
  LeAudioGroupStateMachine::Get()->SuspendStream(group);

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(), types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
}

TEST_F(StateMachineTest, testAseIdAssignmentIdle) {
  const auto context_type = kContextTypeConversational;
  const auto leaudio_group_id = 6;
  const auto num_devices = 1;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  // Should not trigger any action on our side
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  for (auto* device = group->GetFirstDevice(); device != nullptr;
       device = group->GetNextDevice(device)) {
    for (auto& ase : device->ases_) {
      ASSERT_EQ(ase.id, le_audio::types::ase::kAseIdInvalid);
      InjectAseStateNotification(&ase, device, group, ascs::kAseStateIdle,
                                 nullptr);
      ASSERT_EQ(ase.id, ase_id_last_assigned);
    }
  }
}

TEST_F(StateMachineTest, testAseIdAssignmentCodecConfigured) {
  const auto context_type = kContextTypeConversational;
  const auto leaudio_group_id = 6;
  const auto num_devices = 1;

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  // Should not trigger any action on our side
  EXPECT_CALL(gatt_queue, WriteCharacteristic(_, _, _, _, _, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(0);

  for (auto* device = group->GetFirstDevice(); device != nullptr;
       device = group->GetNextDevice(device)) {
    for (auto& ase : device->ases_) {
      client_parser::ascs::ase_codec_configured_state_params
          codec_configured_state_params;

      ASSERT_EQ(ase.id, le_audio::types::ase::kAseIdInvalid);
      InjectAseStateNotification(&ase, device, group,
                                 ascs::kAseStateCodecConfigured,
                                 &codec_configured_state_params);
      ASSERT_EQ(ase.id, ase_id_last_assigned);
    }
  }
}

TEST_F(StateMachineTest, testAseAutonomousRelease) {
  /* Device is banded headphones with 2x snk + 1x src ase
   * (1x bidirectional + 1xunidirectional CIS)
   */
  additional_snk_ases = 1;
  const auto context_type = kContextTypeConversational;
  const int leaudio_group_id = 4;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Conversional context in mind, Sink and Source
   * ASEs should have been configured.
   */
  PrepareConfigureCodecHandler(group, 3);
  PrepareConfigureQosHandler(group, 3);
  PrepareEnableHandler(group, 3);
  PrepareDisableHandler(group, 3);
  PrepareReceiverStartReady(group, 1);
  PrepareReceiverStopReady(group, 1);
  PrepareReleaseHandler(group, 3);

  InjectInitialIdleNotification(group);

  // Validate initial GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Validate new GroupStreamStatus
  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::IDLE))
      .Times(AtLeast(1));

  /* Single disconnect as it is bidirectional Cis*/
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(2);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  for (auto* device = group->GetFirstDevice(); device != nullptr;
       device = group->GetNextDevice(device)) {
    for (auto& ase : device->ases_) {
      client_parser::ascs::ase_codec_configured_state_params
          codec_configured_state_params;

      ASSERT_EQ(ase.state, types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

      // Each one does the autonomous release
      InjectAseStateNotification(&ase, device, group, ascs::kAseStateReleasing,
                                 &codec_configured_state_params);
      InjectAseStateNotification(&ase, device, group, ascs::kAseStateIdle,
                                 &codec_configured_state_params);
    }
  }

  // Verify we've handled the release and updated all states
  for (auto* device = group->GetFirstDevice(); device != nullptr;
       device = group->GetNextDevice(device)) {
    for (auto& ase : device->ases_) {
      ASSERT_EQ(ase.state, types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
    }
  }

  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, testAseAutonomousRelease2Devices) {
  const auto context_type = kContextTypeConversational;
  const int leaudio_group_id = 4;
  const int num_of_devices = 2;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type,
                                             num_of_devices);

  /* Since we prepared device with Conversional context in mind, Sink and Source
   * ASEs should have been configured.
   */
  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReceiverStartReady(group);
  PrepareReceiverStopReady(group);
  PrepareReleaseHandler(group);

  InjectInitialIdleNotification(group);

  // Validate initial GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check streaming will continue
  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::IDLE))
      .Times(0);

  /* Single disconnect as it is bidirectional Cis*/
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(1);

  auto device = group->GetFirstDevice();
  for (auto& ase : device->ases_) {
    client_parser::ascs::ase_codec_configured_state_params
        codec_configured_state_params;

    ASSERT_EQ(ase.state, types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

    // Simulate autonomus release for one device.
    InjectAseStateNotification(&ase, device, group, ascs::kAseStateReleasing,
                               &codec_configured_state_params);
    InjectAseStateNotification(&ase, device, group, ascs::kAseStateIdle,
                               &codec_configured_state_params);
  }
}

TEST_F(StateMachineTest, testStateTransitionTimeoutOnIdleState) {
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(1);

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Disconnect device
  LeAudioGroupStateMachine::Get()->ProcessHciNotifAclDisconnected(
      group, leAudioDevice);

  // Make sure timeout is cleared
  ASSERT_TRUE(fake_osi_alarm_set_on_mloop_.cb == nullptr);
}

TEST_F(StateMachineTest, testStateTransitionTimeout) {
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  PrepareConfigureCodecHandler(group, 1);
  PrepareConfigureQosHandler(group, 1);

  auto* leAudioDevice = group->GetFirstDevice();
  EXPECT_CALL(gatt_queue,
              WriteCharacteristic(1, leAudioDevice->ctp_hdls_.val_hdl, _,
                                  GATT_WRITE_NO_RSP, _, _))
      .Times(3);

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if timeout is fired
  EXPECT_CALL(mock_callbacks_, OnStateTransitionTimeout(leaudio_group_id));

  // simulate timeout seconds passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  ASSERT_EQ(1, mock_function_count_map["alarm_set_on_mloop"]);
}

MATCHER_P(dataPathIsEq, expected, "") { return (arg.data_path_id == expected); }

TEST_F(StateMachineTest, testConfigureDataPathForHost) {
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  /* Should be called 3 times because
   * 1 - calling GetConfigurations just after connection
   * (UpdateAudioContextTypeAvailability)
   * 2 - when doing configuration of the context type
   * 3 - AddCisToStreamConfiguration -> CreateStreamVectorForOffloader
   * 4 - Data Path
   */
  EXPECT_CALL(*mock_codec_manager_, GetCodecLocation())
      .Times(4)
      .WillRepeatedly(Return(types::CodecLocation::HOST));

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  PrepareConfigureCodecHandler(group, 1);
  PrepareConfigureQosHandler(group, 1);
  PrepareEnableHandler(group, 1);

  EXPECT_CALL(
      *mock_iso_manager_,
      SetupIsoDataPath(
          _, dataPathIsEq(bluetooth::hci::iso_manager::kIsoDataPathHci)))
      .Times(1);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));
}
TEST_F(StateMachineTest, testConfigureDataPathForAdsp) {
  const auto context_type = kContextTypeRingtone;
  const int leaudio_group_id = 4;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  /* Should be called 3 times because
   * 1 - calling GetConfigurations just after connection
   * (UpdateAudioContextTypeAvailability)
   * 2 - when doing configuration of the context type
   * 3 - AddCisToStreamConfiguration -> CreateStreamVectorForOffloader
   * 4 - data path
   */
  EXPECT_CALL(*mock_codec_manager_, GetCodecLocation())
      .Times(4)
      .WillRepeatedly(Return(types::CodecLocation::ADSP));

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(leaudio_group_id, context_type);

  /* Since we prepared device with Ringtone context in mind, only one ASE
   * should have been configured.
   */
  PrepareConfigureCodecHandler(group, 1);
  PrepareConfigureQosHandler(group, 1);
  PrepareEnableHandler(group, 1);

  EXPECT_CALL(
      *mock_iso_manager_,
      SetupIsoDataPath(
          _, dataPathIsEq(
                 bluetooth::hci::iso_manager::kIsoDataPathPlatformDefault)))
      .Times(1);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));
}

static void InjectAclDisconnected(LeAudioDeviceGroup* group,
                                  LeAudioDevice* leAudioDevice) {
  LeAudioGroupStateMachine::Get()->ProcessHciNotifAclDisconnected(
      group, leAudioDevice);
}

TEST_F(StateMachineTest, testStreamConfigurationAdspDownMix) {
  const auto context_type = kContextTypeConversational;
  const int leaudio_group_id = 4;
  const int num_devices = 2;

  // Prepare fake connected device group
  auto* group = PrepareSingleTestDeviceGroup(
      leaudio_group_id, context_type, num_devices,
      types::AudioContexts(kContextTypeConversational));

  /* Should be called 5 times because
   * 1 - calling GetConfigurations just after connection
   * (UpdateAudioContextTypeAvailability),
   * 2 - when doing configuration of the context type
   * 3 - AddCisToStreamConfiguration -> CreateStreamVectorForOffloader (sink)
   * 4 - AddCisToStreamConfiguration -> CreateStreamVectorForOffloader (source)
   * 5,6 - Data Path
   */
  EXPECT_CALL(*mock_codec_manager_, GetCodecLocation())
      .Times(6)
      .WillRepeatedly(Return(types::CodecLocation::ADSP));

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareReceiverStartReady(group);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  InjectAclDisconnected(group, leAudioDevice);

  // Start the configuration and stream Media content
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  ASSERT_EQ(
      static_cast<int>(
          group->stream_conf.sink_offloader_streams_target_allocation.size()),
      2);
  ASSERT_EQ(
      static_cast<int>(
          group->stream_conf.source_offloader_streams_target_allocation.size()),
      2);

  ASSERT_EQ(
      static_cast<int>(
          group->stream_conf.sink_offloader_streams_current_allocation.size()),
      2);
  ASSERT_EQ(
      static_cast<int>(group->stream_conf
                           .source_offloader_streams_current_allocation.size()),
      2);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  uint32_t allocation = 0;
  for (const auto& s :
       group->stream_conf.sink_offloader_streams_target_allocation) {
    allocation |= s.second;
    ASSERT_FALSE(allocation == 0);
  }
  ASSERT_TRUE(allocation == codec_spec_conf::kLeAudioLocationStereo);

  allocation = 0;
  for (const auto& s :
       group->stream_conf.source_offloader_streams_target_allocation) {
    allocation |= s.second;
    ASSERT_FALSE(allocation == 0);
  }
  ASSERT_TRUE(allocation == codec_spec_conf::kLeAudioLocationStereo);

  for (const auto& s :
       group->stream_conf.sink_offloader_streams_current_allocation) {
    ASSERT_TRUE((s.second == 0) ||
                (s.second == codec_spec_conf::kLeAudioLocationStereo));
  }

  for (const auto& s :
       group->stream_conf.source_offloader_streams_current_allocation) {
    ASSERT_TRUE((s.second == 0) ||
                (s.second == codec_spec_conf::kLeAudioLocationStereo));
  }
}

static void InjectCisDisconnected(LeAudioDeviceGroup* group,
                                  LeAudioDevice* leAudioDevice,
                                  uint8_t reason) {
  bluetooth::hci::iso_manager::cis_disconnected_evt event;

  for (auto const ase : leAudioDevice->ases_) {
    if (ase.data_path_state != types::AudioStreamDataPathState::CIS_ASSIGNED &&
        ase.data_path_state != types::AudioStreamDataPathState::IDLE) {
      event.reason = reason;
      event.cig_id = group->group_id_;
      event.cis_conn_hdl = ase.cis_conn_hdl;
      LeAudioGroupStateMachine::Get()->ProcessHciNotifCisDisconnected(
          group, leAudioDevice, &event);
    }
  }
}

TEST_F(StateMachineTest, testAttachDeviceToTheStream) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 2;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  LeAudioDevice* lastDevice;
  LeAudioDevice* fistDevice = leAudioDevice;

  auto expected_devices_written = 0;
  while (leAudioDevice) {
    /* Three Writes:
     * 1: Codec Config
     * 2: Codec QoS
     * 3: Enabling
     */
    lastDevice = leAudioDevice;
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(3));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  testing::Mock::VerifyAndClearExpectations(&mock_iso_manager_);

  // Inject CIS and ACL disconnection of first device
  InjectCisDisconnected(group, lastDevice, HCI_ERR_CONNECTION_TOUT);
  InjectAclDisconnected(group, lastDevice);

  // Check if group keeps streaming
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  lastDevice->conn_id_ = 3;
  group->UpdateAudioContextTypeAvailability();

  // Make sure ASE with disconnected CIS are not left in STREAMING
  ASSERT_EQ(lastDevice->GetFirstAseWithState(
                ::le_audio::types::kLeAudioDirectionSink,
                types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING),
            nullptr);
  ASSERT_EQ(lastDevice->GetFirstAseWithState(
                ::le_audio::types::kLeAudioDirectionSource,
                types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING),
            nullptr);

  EXPECT_CALL(gatt_queue, WriteCharacteristic(lastDevice->conn_id_,
                                              lastDevice->ctp_hdls_.val_hdl, _,
                                              GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(3));

  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(1);
  LeAudioGroupStateMachine::Get()->AttachToStream(group, lastDevice);

  // Check if group keeps streaming
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  // Verify that the joining device receives the right CCID list
  auto lastMeta = lastDevice->GetFirstActiveAse()->metadata;
  bool parsedOk = false;
  auto ltv = le_audio::types::LeAudioLtvMap::Parse(lastMeta.data(),
                                                   lastMeta.size(), parsedOk);
  ASSERT_TRUE(parsedOk);

  auto ccids = ltv.Find(le_audio::types::kLeAudioMetadataTypeCcidList);
  ASSERT_TRUE(ccids.has_value());
  ASSERT_NE(std::find(ccids->begin(), ccids->end(), media_ccid), ccids->end());

  /* Verify that ASE of first device are still good*/
  auto ase = fistDevice->GetFirstActiveAse();
  ASSERT_NE(ase->max_transport_latency, 0);
  ASSERT_NE(ase->retrans_nb, 0);
}

TEST_F(StateMachineTest, testAttachDeviceToTheConversationalStream) {
  const auto context_type = kContextTypeConversational;
  const auto leaudio_group_id = 6;
  const auto num_devices = 2;

  ContentControlIdKeeper::GetInstance()->SetCcid(call_context, call_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareReceiverStartReady(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  LeAudioDevice* lastDevice;
  LeAudioDevice* fistDevice = leAudioDevice;

  auto expected_devices_written = 0;
  while (leAudioDevice) {
    /* Three Writes:
     * 1: Codec Config
     * 2: Codec QoS
     * 3: Enabling
     */
    lastDevice = leAudioDevice;
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(AtLeast(3));
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(4);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Conversational content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  testing::Mock::VerifyAndClearExpectations(&mock_iso_manager_);

  // Inject CIS and ACL disconnection of first device
  InjectCisDisconnected(group, lastDevice, HCI_ERR_CONNECTION_TOUT);
  InjectAclDisconnected(group, lastDevice);

  // Check if group keeps streaming
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  lastDevice->conn_id_ = 3;
  group->UpdateAudioContextTypeAvailability();

  // Make sure ASE with disconnected CIS are not left in STREAMING
  ASSERT_EQ(lastDevice->GetFirstAseWithState(
                ::le_audio::types::kLeAudioDirectionSink,
                types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING),
            nullptr);
  ASSERT_EQ(lastDevice->GetFirstAseWithState(
                ::le_audio::types::kLeAudioDirectionSource,
                types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING),
            nullptr);

  EXPECT_CALL(gatt_queue, WriteCharacteristic(lastDevice->conn_id_,
                                              lastDevice->ctp_hdls_.val_hdl, _,
                                              GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(3));

  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);
  LeAudioGroupStateMachine::Get()->AttachToStream(group, lastDevice);

  // Check if group keeps streaming
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);

  // Verify that the joining device receives the right CCID list
  auto lastMeta = lastDevice->GetFirstActiveAse()->metadata;
  bool parsedOk = false;
  auto ltv = le_audio::types::LeAudioLtvMap::Parse(lastMeta.data(),
                                                   lastMeta.size(), parsedOk);
  ASSERT_TRUE(parsedOk);

  auto ccids = ltv.Find(le_audio::types::kLeAudioMetadataTypeCcidList);
  ASSERT_TRUE(ccids.has_value());
  ASSERT_NE(std::find(ccids->begin(), ccids->end(), call_ccid), ccids->end());

  /* Verify that ASE of first device are still good*/
  auto ase = fistDevice->GetFirstActiveAse();
  ASSERT_NE(ase->max_transport_latency, 0);
  ASSERT_NE(ase->retrans_nb, 0);

  // Make sure ASEs with reconnected CIS are in STREAMING state
  ASSERT_TRUE(lastDevice->HaveAllActiveAsesSameState(
      types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING));
}

TEST_F(StateMachineTest, StartStreamAfterConfigure) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 2;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group, 0, true);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    /* Three Writes:
     * 1. Codec configure
     * 2: Codec QoS
     * 3: Enabling
     */
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(3);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(
                  leaudio_group_id,
                  bluetooth::le_audio::GroupStreamStatus::CONFIGURED_BY_USER));

  // Start the configuration and stream Media content
  group->SetPendingConfiguration();
  LeAudioGroupStateMachine::Get()->ConfigureStream(
      group, context_type, types::AudioContexts(context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  group->ClearPendingConfiguration();
  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, context_type, types::AudioContexts(context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
}

TEST_F(StateMachineTest, StartStreamCachedConfig) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 2;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group, 0, true);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    /* Three Writes:
     * 1: Codec config
     * 2: Codec QoS (+1 after restart)
     * 3: Enabling (+1 after restart)
     * 4: Release (1)
     */
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(6);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, context_type, types::AudioContexts(context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS));
  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StopStream(group);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Restart stream
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, context_type, types::AudioContexts(context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, BoundedHeadphonesConversationalToMediaChannelCount_2) {
  const auto initial_context_type = kContextTypeConversational;
  const auto new_context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 1;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel |
                   kLeAudioCodecLC3ChannelCountTwoChannel;

  sample_freq_ |= codec_specific::kCapSamplingFrequency48000Hz |
                  codec_specific::kCapSamplingFrequency32000Hz;
  additional_snk_ases = 3;
  additional_src_ases = 1;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);
  ContentControlIdKeeper::GetInstance()->SetCcid(call_context, call_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group = PrepareSingleTestDeviceGroup(
      leaudio_group_id, initial_context_type, num_devices,
      kContextTypeConversational | kContextTypeMedia);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group, 0, true);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);
  PrepareReceiverStartReady(group);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    /* 8 Writes:
     * 1: Codec config (+1 after reconfig)
     * 2: Codec QoS (+1 after reconfig)
     * 3: Enabling (+1 after reconfig)
     * 4: ReceiverStartReady (only for conversational)
     * 5: Release
     */
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(8);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, initial_context_type, types::AudioContexts(initial_context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS));
  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StopStream(group);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  // Restart stream
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, new_context_type, types::AudioContexts(new_context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
}

TEST_F(StateMachineTest, BoundedHeadphonesConversationalToMediaChannelCount_1) {
  const auto initial_context_type = kContextTypeConversational;
  const auto new_context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 1;
  channel_count_ = kLeAudioCodecLC3ChannelCountSingleChannel;

  sample_freq_ |= codec_specific::kCapSamplingFrequency48000Hz |
                  codec_specific::kCapSamplingFrequency32000Hz;
  additional_snk_ases = 3;
  additional_src_ases = 1;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);
  ContentControlIdKeeper::GetInstance()->SetCcid(call_context, call_ccid);

  // Prepare one fake connected devices in a group
  auto* group = PrepareSingleTestDeviceGroup(
      leaudio_group_id, initial_context_type, num_devices,
      kContextTypeConversational | kContextTypeMedia);
  ASSERT_EQ(group->Size(), num_devices);

  // Cannot verify here as we will change the number of ases on reconfigure
  PrepareConfigureCodecHandler(group, 0, true);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);
  PrepareReceiverStartReady(group);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    /* 8 Writes:
     * 1: Codec config (+1 after reconfig)
     * 2: Codec QoS (+1 after reconfig)
     * 3: Enabling (+1 after reconfig)
     * 4: ReceiverStartReady (only for conversational)
     * 5: Release
     */
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(8);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, initial_context_type, types::AudioContexts(initial_context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS));
  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StopStream(group);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  // Restart stream
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, new_context_type, types::AudioContexts(new_context_type));

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, lateCisDisconnectedEvent_ConfiguredByUser) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 1;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group, 0, true);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;

  /* Three Writes:
   * 1: Codec Config
   * 2: Codec QoS
   * 3: Enabling
   */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(leAudioDevice->conn_id_,
                                              leAudioDevice->ctp_hdls_.val_hdl,
                                              _, GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(3));
  expected_devices_written++;

  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  testing::Mock::VerifyAndClearExpectations(&mock_iso_manager_);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  /* Prepare DisconnectCis mock to not symulate CisDisconnection */
  ON_CALL(*mock_iso_manager_, DisconnectCis).WillByDefault(Return());

  /* Do reconfiguration */
  group->SetPendingConfiguration();

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(
                  leaudio_group_id,
                  bluetooth::le_audio::GroupStreamStatus::CONFIGURED_BY_USER))
      .Times(0);
  LeAudioGroupStateMachine::Get()->StopStream(group);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);

  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(
                  leaudio_group_id,
                  bluetooth::le_audio::GroupStreamStatus::CONFIGURED_BY_USER));

  // Inject CIS and ACL disconnection of first device
  InjectCisDisconnected(group, leAudioDevice, HCI_ERR_CONN_CAUSE_LOCAL_HOST);
  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, lateCisDisconnectedEvent_AutonomousConfigured) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 1;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group, 0, true);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;

  /* Three Writes:
   * 1: Codec Config
   * 2: Codec QoS
   * 3: Enabling
   */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(leAudioDevice->conn_id_,
                                              leAudioDevice->ctp_hdls_.val_hdl,
                                              _, GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(3));
  expected_devices_written++;

  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  testing::Mock::VerifyAndClearExpectations(&mock_iso_manager_);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;

  /* Prepare DisconnectCis mock to not symulate CisDisconnection */
  ON_CALL(*mock_iso_manager_, DisconnectCis).WillByDefault(Return());

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS))
      .Times(0);

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);

  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(
          leaudio_group_id,
          bluetooth::le_audio::GroupStreamStatus::CONFIGURED_AUTONOMOUS));

  // Inject CIS and ACL disconnection of first device
  InjectCisDisconnected(group, leAudioDevice, HCI_ERR_CONN_CAUSE_LOCAL_HOST);
  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, lateCisDisconnectedEvent_Idle) {
  const auto context_type = kContextTypeMedia;
  const auto leaudio_group_id = 6;
  const auto num_devices = 1;

  ContentControlIdKeeper::GetInstance()->SetCcid(media_context, media_ccid);

  // Prepare multiple fake connected devices in a group
  auto* group =
      PrepareSingleTestDeviceGroup(leaudio_group_id, context_type, num_devices);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareDisableHandler(group);
  PrepareReleaseHandler(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;

  /* Three Writes:
   * 1: Codec Config
   * 2: Codec QoS
   * 3: Enabling
   */
  EXPECT_CALL(gatt_queue, WriteCharacteristic(leAudioDevice->conn_id_,
                                              leAudioDevice->ctp_hdls_.val_hdl,
                                              _, GATT_WRITE_NO_RSP, _, _))
      .Times(AtLeast(3));
  expected_devices_written++;

  ASSERT_EQ(expected_devices_written, num_devices);

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(1);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(2);

  InjectInitialIdleNotification(group);

  // Start the configuration and stream Media content
  LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  testing::Mock::VerifyAndClearExpectations(&mock_iso_manager_);

  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  mock_function_count_map["alarm_cancel"] = 0;
  /* Prepare DisconnectCis mock to not symulate CisDisconnection */
  ON_CALL(*mock_iso_manager_, DisconnectCis).WillByDefault(Return());

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::RELEASING));

  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::IDLE))
      .Times(0);

  // Stop the stream
  LeAudioGroupStateMachine::Get()->StopStream(group);

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(), types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
  ASSERT_EQ(0, mock_function_count_map["alarm_cancel"]);

  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  EXPECT_CALL(mock_callbacks_,
              StatusReportCb(leaudio_group_id,
                             bluetooth::le_audio::GroupStreamStatus::IDLE));

  // Inject CIS and ACL disconnection of first device
  InjectCisDisconnected(group, leAudioDevice, HCI_ERR_CONN_CAUSE_LOCAL_HOST);
  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
}

TEST_F(StateMachineTest, StreamReconfigureAfterCisLostTwoDevices) {
  auto context_type = kContextTypeConversational;
  const auto leaudio_group_id = 4;
  const auto num_devices = 2;

  // Prepare multiple fake connected devices in a group
  auto* group = PrepareSingleTestDeviceGroup(
      leaudio_group_id, context_type, num_devices,
      kContextTypeConversational | kContextTypeMedia);
  ASSERT_EQ(group->Size(), num_devices);

  PrepareConfigureCodecHandler(group);
  PrepareConfigureQosHandler(group);
  PrepareEnableHandler(group);
  PrepareReceiverStartReady(group);

  /* Prepare DisconnectCis mock to not symulate CisDisconnection */
  ON_CALL(*mock_iso_manager_, DisconnectCis).WillByDefault(Return());

  EXPECT_CALL(*mock_iso_manager_, CreateCig(_, _)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, EstablishCis(_)).Times(2);
  EXPECT_CALL(*mock_iso_manager_, SetupIsoDataPath(_, _)).Times(6);
  EXPECT_CALL(*mock_iso_manager_, RemoveIsoDataPath(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, DisconnectCis(_, _)).Times(0);
  EXPECT_CALL(*mock_iso_manager_, RemoveCig(_, _)).Times(1);

  InjectInitialIdleNotification(group);

  auto* leAudioDevice = group->GetFirstDevice();
  auto expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(3);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Media content
  context_type = kContextTypeMedia;
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(1, mock_function_count_map["alarm_cancel"]);
  testing::Mock::VerifyAndClearExpectations(&mock_iso_manager_);
  testing::Mock::VerifyAndClearExpectations(&gatt_queue);
  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);

  // Device disconnects due to timeout of CIS
  leAudioDevice = group->GetFirstDevice();
  while (leAudioDevice) {
    InjectCisDisconnected(group, leAudioDevice, HCI_ERR_CONN_CAUSE_LOCAL_HOST);
    // Disconnect device
    LeAudioGroupStateMachine::Get()->ProcessHciNotifAclDisconnected(
        group, leAudioDevice);

    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }

  LOG(INFO) << "GK A1";
  group->ReloadAudioLocations();
  group->ReloadAudioDirections();
  group->UpdateAudioContextTypeAvailability();

  // Start conversational scenario
  leAudioDevice = group->GetFirstDevice();
  int device_cnt = num_devices;
  while (leAudioDevice) {
    LOG(INFO) << "GK A11";
    leAudioDevice->conn_id_ = device_cnt--;
    leAudioDevice->SetConnectionState(DeviceConnectState::CONNECTED);
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }

  LOG(INFO) << "GK A2";
  InjectInitialIdleNotification(group);

  group->ReloadAudioLocations();
  group->ReloadAudioDirections();
  group->UpdateAudioContextTypeAvailability(kContextTypeConversational |
                                            kContextTypeMedia);

  leAudioDevice = group->GetFirstDevice();
  expected_devices_written = 0;
  while (leAudioDevice) {
    EXPECT_CALL(gatt_queue,
                WriteCharacteristic(leAudioDevice->conn_id_,
                                    leAudioDevice->ctp_hdls_.val_hdl, _,
                                    GATT_WRITE_NO_RSP, _, _))
        .Times(4);
    expected_devices_written++;
    leAudioDevice = group->GetNextDevice(leAudioDevice);
  }
  ASSERT_EQ(expected_devices_written, num_devices);

  // Validate GroupStreamStatus
  EXPECT_CALL(
      mock_callbacks_,
      StatusReportCb(leaudio_group_id,
                     bluetooth::le_audio::GroupStreamStatus::STREAMING));

  // Start the configuration and stream Conversational content
  context_type = kContextTypeConversational;
  ASSERT_TRUE(LeAudioGroupStateMachine::Get()->StartStream(
      group, static_cast<LeAudioContextType>(context_type),
      types::AudioContexts(context_type)));

  // Check if group has transitioned to a proper state
  ASSERT_EQ(group->GetState(),
            types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
  ASSERT_EQ(2, mock_function_count_map["alarm_cancel"]);
  testing::Mock::VerifyAndClearExpectations(&mock_iso_manager_);
  testing::Mock::VerifyAndClearExpectations(&gatt_queue);
  testing::Mock::VerifyAndClearExpectations(&mock_callbacks_);
}

}  // namespace internal
}  // namespace le_audio
