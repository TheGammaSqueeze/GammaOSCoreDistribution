/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>

#include "bta/csis/csis_types.h"
#include "bta_gatt_api_mock.h"
#include "bta_gatt_queue_mock.h"
#include "bta_groups.h"
#include "bta_le_audio_api.h"
#include "btif_storage_mock.h"
#include "btm_api_mock.h"
#include "btm_iso_api.h"
#include "common/message_loop_thread.h"
#include "device/include/controller.h"
#include "fake_osi.h"
#include "gatt/database_builder.h"
#include "hardware/bt_gatt_types.h"
#include "internal_include/stack_config.h"
#include "le_audio_set_configuration_provider.h"
#include "le_audio_types.h"
#include "mock_controller.h"
#include "mock_csis_client.h"
#include "mock_device_groups.h"
#include "mock_iso_manager.h"
#include "mock_state_machine.h"
#include "osi/include/log.h"

using testing::_;
using testing::AnyNumber;
using testing::AtLeast;
using testing::AtMost;
using testing::DoAll;
using testing::Expectation;
using testing::Invoke;
using testing::Matcher;
using testing::Mock;
using testing::MockFunction;
using testing::NotNull;
using testing::Return;
using testing::SaveArg;
using testing::SetArgPointee;
using testing::Test;
using testing::WithArg;

using bluetooth::Uuid;

using namespace bluetooth::le_audio;

using le_audio::LeAudioCodecConfiguration;
using le_audio::LeAudioSinkAudioHalClient;
using le_audio::LeAudioSourceAudioHalClient;

extern struct fake_osi_alarm_set_on_mloop fake_osi_alarm_set_on_mloop_;

std::map<std::string, int> mock_function_count_map;
constexpr int max_num_of_ases = 5;

static constexpr char kNotifyUpperLayerAboutGroupBeingInIdleDuringCall[] =
    "persist.bluetooth.leaudio.notify.idle.during.call";
const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    "INIT_leaudio_targeted_announcement_reconnection_mode=true",
    nullptr,
};

void osi_property_set_bool(const char* key, bool value);

// Disables most likely false-positives from base::SplitString()
extern "C" const char* __asan_default_options() {
  return "detect_container_overflow=0";
}

std::atomic<int> num_async_tasks;
bluetooth::common::MessageLoopThread message_loop_thread("test message loop");
bluetooth::common::MessageLoopThread* get_main_thread() {
  return &message_loop_thread;
}
bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task) {
  // Wrap the task with task counter so we could later know if there are
  // any callbacks scheduled and we should wait before performing some actions
  if (!message_loop_thread.DoInThread(
          from_here,
          base::BindOnce(
              [](base::OnceClosure task, std::atomic<int>& num_async_tasks) {
                std::move(task).Run();
                num_async_tasks--;
              },
              std::move(task), std::ref(num_async_tasks)))) {
    LOG(ERROR) << __func__ << ": failed from " << from_here.ToString();
    return BT_STATUS_FAIL;
  }
  num_async_tasks++;
  return BT_STATUS_SUCCESS;
}

bt_status_t do_in_main_thread_delayed(const base::Location& from_here,
                                      base::OnceClosure task,
                                      const base::TimeDelta& delay) {
  /* For testing purpose it is ok to just skip delay */
  return do_in_main_thread(from_here, std::move(task));
}

static base::MessageLoop* message_loop_;
base::MessageLoop* get_main_message_loop() { return message_loop_; }

static void init_message_loop_thread() {
  num_async_tasks = 0;
  message_loop_thread.StartUp();
  if (!message_loop_thread.IsRunning()) {
    FAIL() << "unable to create message loop thread.";
  }

  if (!message_loop_thread.EnableRealTimeScheduling())
    LOG(ERROR) << "Unable to set real time scheduling";

  message_loop_ = message_loop_thread.message_loop();
  if (message_loop_ == nullptr) FAIL() << "unable to get message loop.";
}

static void cleanup_message_loop_thread() {
  message_loop_ = nullptr;
  message_loop_thread.ShutDown();
}

void invoke_switch_codec_cb(bool is_low_latency_buffer_size) {}
void invoke_switch_buffer_size_cb(bool is_low_latency_buffer_size) {}

const std::string kSmpOptions("mock smp options");
bool get_trace_config_enabled(void) { return false; }
bool get_pts_avrcp_test(void) { return false; }
bool get_pts_secure_only_mode(void) { return false; }
bool get_pts_conn_updates_disabled(void) { return false; }
bool get_pts_crosskey_sdp_disable(void) { return false; }
const std::string* get_pts_smp_options(void) { return &kSmpOptions; }
int get_pts_smp_failure_case(void) { return 123; }
bool get_pts_force_eatt_for_notifications(void) { return false; }
bool get_pts_connect_eatt_unconditionally(void) { return false; }
bool get_pts_connect_eatt_before_encryption(void) { return false; }
bool get_pts_unencrypt_broadcast(void) { return false; }
bool get_pts_eatt_peripheral_collision_support(void) { return false; }
bool get_pts_force_le_audio_multiple_contexts_metadata(void) { return false; }
bool get_pts_le_audio_disable_ases_before_stopping(void) { return false; }
config_t* get_all(void) { return nullptr; }

stack_config_t mock_stack_config{
    .get_trace_config_enabled = get_trace_config_enabled,
    .get_pts_avrcp_test = get_pts_avrcp_test,
    .get_pts_secure_only_mode = get_pts_secure_only_mode,
    .get_pts_conn_updates_disabled = get_pts_conn_updates_disabled,
    .get_pts_crosskey_sdp_disable = get_pts_crosskey_sdp_disable,
    .get_pts_smp_options = get_pts_smp_options,
    .get_pts_smp_failure_case = get_pts_smp_failure_case,
    .get_pts_force_eatt_for_notifications =
        get_pts_force_eatt_for_notifications,
    .get_pts_connect_eatt_unconditionally =
        get_pts_connect_eatt_unconditionally,
    .get_pts_connect_eatt_before_encryption =
        get_pts_connect_eatt_before_encryption,
    .get_pts_unencrypt_broadcast = get_pts_unencrypt_broadcast,
    .get_pts_eatt_peripheral_collision_support =
        get_pts_eatt_peripheral_collision_support,
    .get_pts_force_le_audio_multiple_contexts_metadata =
        get_pts_force_le_audio_multiple_contexts_metadata,
    .get_pts_le_audio_disable_ases_before_stopping =
        get_pts_le_audio_disable_ases_before_stopping,
    .get_all = get_all,
};
const stack_config_t* stack_config_get_interface(void) {
  return &mock_stack_config;
}

namespace le_audio {
class MockLeAudioSourceHalClient;
MockLeAudioSourceHalClient* mock_le_audio_source_hal_client_;
std::unique_ptr<LeAudioSourceAudioHalClient>
    owned_mock_le_audio_source_hal_client_;
bool is_audio_unicast_source_acquired;

std::unique_ptr<LeAudioSourceAudioHalClient>
LeAudioSourceAudioHalClient::AcquireUnicast() {
  if (is_audio_unicast_source_acquired) return nullptr;
  is_audio_unicast_source_acquired = true;
  return std::move(owned_mock_le_audio_source_hal_client_);
}

void LeAudioSourceAudioHalClient::DebugDump(int fd) {}

class MockLeAudioSinkHalClient;
MockLeAudioSinkHalClient* mock_le_audio_sink_hal_client_;
std::unique_ptr<LeAudioSinkAudioHalClient> owned_mock_le_audio_sink_hal_client_;
bool is_audio_unicast_sink_acquired;

std::unique_ptr<LeAudioSinkAudioHalClient>
LeAudioSinkAudioHalClient::AcquireUnicast() {
  if (is_audio_unicast_sink_acquired) return nullptr;
  is_audio_unicast_sink_acquired = true;
  return std::move(owned_mock_le_audio_sink_hal_client_);
}

void LeAudioSinkAudioHalClient::DebugDump(int fd) {}

class MockAudioHalClientCallbacks
    : public bluetooth::le_audio::LeAudioClientCallbacks {
 public:
  MOCK_METHOD((void), OnInitialized, (), (override));
  MOCK_METHOD((void), OnConnectionState,
              (ConnectionState state, const RawAddress& address), (override));
  MOCK_METHOD((void), OnGroupStatus, (int group_id, GroupStatus group_status),
              (override));
  MOCK_METHOD((void), OnGroupNodeStatus,
              (const RawAddress& bd_addr, int group_id,
               GroupNodeStatus node_status),
              (override));
  MOCK_METHOD((void), OnAudioConf,
              (uint8_t direction, int group_id, uint32_t snk_audio_location,
               uint32_t src_audio_location, uint16_t avail_cont),
              (override));
  MOCK_METHOD((void), OnSinkAudioLocationAvailable,
              (const RawAddress& bd_addr, uint32_t snk_audio_location),
              (override));
  MOCK_METHOD(
      (void), OnAudioLocalCodecCapabilities,
      (std::vector<btle_audio_codec_config_t> local_input_capa_codec_conf,
       std::vector<btle_audio_codec_config_t> local_output_capa_codec_conf),
      (override));
  MOCK_METHOD(
      (void), OnAudioGroupCodecConf,
      (int group_id, btle_audio_codec_config_t input_codec_conf,
       btle_audio_codec_config_t output_codec_conf,
       std::vector<btle_audio_codec_config_t> input_selectable_codec_conf,
       std::vector<btle_audio_codec_config_t> output_selectable_codec_conf),
      (override));
};

class MockLeAudioSinkHalClient : public LeAudioSinkAudioHalClient {
 public:
  MockLeAudioSinkHalClient() = default;
  MOCK_METHOD((bool), Start,
              (const LeAudioCodecConfiguration& codecConfiguration,
               LeAudioSinkAudioHalClient::Callbacks* audioReceiver),
              (override));
  MOCK_METHOD((void), Stop, (), (override));
  MOCK_METHOD((size_t), SendData, (uint8_t * data, uint16_t size), (override));
  MOCK_METHOD((void), ConfirmStreamingRequest, (), (override));
  MOCK_METHOD((void), CancelStreamingRequest, (), (override));
  MOCK_METHOD((void), UpdateRemoteDelay, (uint16_t delay), (override));
  MOCK_METHOD((void), UpdateAudioConfigToHal,
              (const ::le_audio::offload_config&), (override));
  MOCK_METHOD((void), SuspendedForReconfiguration, (), (override));
  MOCK_METHOD((void), ReconfigurationComplete, (), (override));

  MOCK_METHOD((void), OnDestroyed, ());
  virtual ~MockLeAudioSinkHalClient() override { OnDestroyed(); }
};

class MockLeAudioSourceHalClient : public LeAudioSourceAudioHalClient {
 public:
  MockLeAudioSourceHalClient() = default;
  MOCK_METHOD((bool), Start,
              (const LeAudioCodecConfiguration& codecConfiguration,
               LeAudioSourceAudioHalClient::Callbacks* audioReceiver),
              (override));
  MOCK_METHOD((void), Stop, (), (override));
  MOCK_METHOD((void), ConfirmStreamingRequest, (), (override));
  MOCK_METHOD((void), CancelStreamingRequest, (), (override));
  MOCK_METHOD((void), UpdateRemoteDelay, (uint16_t delay), (override));
  MOCK_METHOD((void), UpdateAudioConfigToHal,
              (const ::le_audio::offload_config&), (override));
  MOCK_METHOD((void), UpdateBroadcastAudioConfigToHal,
              (const ::le_audio::broadcast_offload_config&), (override));
  MOCK_METHOD((void), SuspendedForReconfiguration, (), (override));
  MOCK_METHOD((void), ReconfigurationComplete, (), (override));

  MOCK_METHOD((void), OnDestroyed, ());
  virtual ~MockLeAudioSourceHalClient() override { OnDestroyed(); }
};

class UnicastTestNoInit : public Test {
 protected:
  void SetUpMockAudioHal() {
    bluetooth::common::InitFlags::Load(test_flags);

    /* Since these are returned by the Acquire() methods as unique_ptrs, we
     * will not free them manually.
     */

    owned_mock_le_audio_sink_hal_client_.reset(new MockLeAudioSinkHalClient());
    mock_le_audio_sink_hal_client_ =
        (MockLeAudioSinkHalClient*)owned_mock_le_audio_sink_hal_client_.get();

    owned_mock_le_audio_source_hal_client_.reset(
        new MockLeAudioSourceHalClient());
    mock_le_audio_source_hal_client_ =
        (MockLeAudioSourceHalClient*)
            owned_mock_le_audio_source_hal_client_.get();

    is_audio_unicast_source_acquired = false;
    ON_CALL(*mock_le_audio_source_hal_client_, Start(_, _))
        .WillByDefault(
            [this](const LeAudioCodecConfiguration& codec_configuration,
                   LeAudioSourceAudioHalClient::Callbacks* audioReceiver) {
              unicast_source_hal_cb_ = audioReceiver;
              return true;
            });
    ON_CALL(*mock_le_audio_source_hal_client_, OnDestroyed).WillByDefault([]() {
      mock_le_audio_source_hal_client_ = nullptr;
      is_audio_unicast_source_acquired = false;
    });

    is_audio_unicast_sink_acquired = false;
    ON_CALL(*mock_le_audio_sink_hal_client_, Start(_, _))
        .WillByDefault(
            [this](const LeAudioCodecConfiguration& codec_configuration,
                   LeAudioSinkAudioHalClient::Callbacks* audioReceiver) {
              unicast_sink_hal_cb_ = audioReceiver;
              return true;
            });
    ON_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed).WillByDefault([]() {
      mock_le_audio_sink_hal_client_ = nullptr;
      is_audio_unicast_sink_acquired = false;
    });

    ON_CALL(*mock_le_audio_sink_hal_client_, SendData)
        .WillByDefault([](uint8_t* data, uint16_t size) { return size; });

    // HAL
    ON_CALL(mock_hal_2_1_verifier, Call()).WillByDefault([]() -> bool {
      return true;
    });
  }

  void InjectGroupDeviceRemoved(const RawAddress& address, int group_id) {
    group_callbacks_->OnGroupMemberRemoved(address, group_id);
  }

  void InjectGroupDeviceAdded(const RawAddress& address, int group_id) {
    bluetooth::Uuid uuid = le_audio::uuid::kCapServiceUuid;

    int group_members_num = 0;
    for (const auto& [addr, id] : groups) {
      if (id == group_id) group_members_num++;
    }

    bool first_device = (group_members_num == 1);
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](const RawAddress& addr, int group_id, bluetooth::Uuid uuid,
               bluetooth::groups::DeviceGroupsCallbacks* group_callbacks,
               bool first_device) {
              if (first_device) {
                group_callbacks->OnGroupAdded(addr, uuid, group_id);
              } else {
                group_callbacks->OnGroupMemberAdded(addr, group_id);
              }
            },
            address, group_id, uuid, base::Unretained(this->group_callbacks_),
            first_device));
  }

  void InjectConnectedEvent(const RawAddress& address, uint16_t conn_id,
                            tGATT_STATUS status = GATT_SUCCESS) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    tBTA_GATTC_OPEN event_data = {
        .status = status,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = address,
        .transport = GATT_TRANSPORT_LE,
        .mtu = 240,
    };

    ASSERT_NE(peer_devices.count(conn_id), 0u);
    peer_devices.at(conn_id)->connected = true;
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](tBTA_GATTC_CBACK* gatt_callback, tBTA_GATTC_OPEN event_data) {
              gatt_callback(BTA_GATTC_OPEN_EVT, (tBTA_GATTC*)&event_data);
            },
            base::Unretained(this->gatt_callback), event_data));
  }

  void InjectDisconnectedEvent(
      uint16_t conn_id,
      tGATT_DISCONN_REASON reason = GATT_CONN_TERMINATE_LOCAL_HOST) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    ASSERT_NE(peer_devices.count(conn_id), 0u);

    tBTA_GATTC_CLOSE event_data = {
        .status = GATT_SUCCESS,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = peer_devices.at(conn_id)->addr,
        .reason = reason,
    };

    peer_devices.at(conn_id)->connected = false;
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](tBTA_GATTC_CBACK* gatt_callback, tBTA_GATTC_CLOSE event_data) {
              gatt_callback(BTA_GATTC_CLOSE_EVT, (tBTA_GATTC*)&event_data);
            },
            base::Unretained(this->gatt_callback), event_data));
  }

  void InjectSearchCompleteEvent(uint16_t conn_id) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    tBTA_GATTC_SEARCH_CMPL event_data = {
        .status = GATT_SUCCESS,
        .conn_id = conn_id,
    };

    do_in_main_thread(FROM_HERE,
                      base::BindOnce(
                          [](tBTA_GATTC_CBACK* gatt_callback,
                             tBTA_GATTC_SEARCH_CMPL event_data) {
                            gatt_callback(BTA_GATTC_SEARCH_CMPL_EVT,
                                          (tBTA_GATTC*)&event_data);
                          },
                          base::Unretained(this->gatt_callback), event_data));
  }

  void InjectNotificationEvent(const RawAddress& test_address, uint16_t conn_id,
                               uint16_t handle, std::vector<uint8_t> value) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    tBTA_GATTC_NOTIFY event_data = {
        .conn_id = conn_id,
        .bda = test_address,
        .handle = handle,
        .len = (uint8_t)value.size(),
        .is_notify = true,
    };

    std::copy(value.begin(), value.end(), event_data.value);
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](tBTA_GATTC_CBACK* gatt_callback, tBTA_GATTC_NOTIFY event_data) {
              gatt_callback(BTA_GATTC_NOTIF_EVT, (tBTA_GATTC*)&event_data);
            },
            base::Unretained(this->gatt_callback), event_data));
  }

  void SetUpMockGatt() {
    // default action for GetCharacteristic function call
    ON_CALL(mock_gatt_interface_, GetCharacteristic(_, _))
        .WillByDefault(
            Invoke([&](uint16_t conn_id,
                       uint16_t handle) -> const gatt::Characteristic* {
              std::list<gatt::Service>& services =
                  peer_devices.at(conn_id)->services;
              for (auto const& service : services) {
                for (auto const& characteristic : service.characteristics) {
                  if (characteristic.value_handle == handle) {
                    return &characteristic;
                  }
                }
              }

              return nullptr;
            }));

    // default action for GetOwningService function call
    ON_CALL(mock_gatt_interface_, GetOwningService(_, _))
        .WillByDefault(Invoke(
            [&](uint16_t conn_id, uint16_t handle) -> const gatt::Service* {
              std::list<gatt::Service>& services =
                  peer_devices.at(conn_id)->services;
              for (auto const& service : services) {
                if (service.handle <= handle && service.end_handle >= handle) {
                  return &service;
                }
              }

              return nullptr;
            }));

    // default action for ServiceSearchRequest function call
    ON_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _))
        .WillByDefault(WithArg<0>(Invoke(
            [&](uint16_t conn_id) { InjectSearchCompleteEvent(conn_id); })));

    // default action for GetServices function call
    ON_CALL(mock_gatt_interface_, GetServices(_))
        .WillByDefault(WithArg<0>(
            Invoke([&](uint16_t conn_id) -> std::list<gatt::Service>* {
              return &peer_devices.at(conn_id)->services;
            })));

    // default action for RegisterForNotifications function call
    ON_CALL(mock_gatt_interface_, RegisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for DeregisterForNotifications function call
    ON_CALL(mock_gatt_interface_, DeregisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for WriteDescriptor function call
    ON_CALL(mock_gatt_queue_, WriteDescriptor(_, _, _, _, _, _))
        .WillByDefault(Invoke([](uint16_t conn_id, uint16_t handle,
                                 std::vector<uint8_t> value,
                                 tGATT_WRITE_TYPE write_type,
                                 GATT_WRITE_OP_CB cb, void* cb_data) -> void {
          if (cb)
            do_in_main_thread(
                FROM_HERE,
                base::BindOnce(
                    [](GATT_WRITE_OP_CB cb, uint16_t conn_id, uint16_t handle,
                       uint16_t len, uint8_t* value, void* cb_data) {
                      cb(conn_id, GATT_SUCCESS, handle, len, value, cb_data);
                    },
                    cb, conn_id, handle, value.size(), value.data(), cb_data));
        }));

    global_conn_id = 1;
    ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, _))
        .WillByDefault(
            Invoke([&](tGATT_IF client_if, const RawAddress& remote_bda,
                       bool is_direct, bool opportunistic) {
              InjectConnectedEvent(remote_bda, global_conn_id++);
            }));

    ON_CALL(mock_gatt_interface_, Close(_))
        .WillByDefault(Invoke(
            [&](uint16_t conn_id) { InjectDisconnectedEvent(conn_id); }));

    // default Characteristic read handler dispatches requests to service mocks
    ON_CALL(mock_gatt_queue_, ReadCharacteristic(_, _, _, _))
        .WillByDefault(Invoke([&](uint16_t conn_id, uint16_t handle,
                                  GATT_READ_OP_CB cb, void* cb_data) {
          do_in_main_thread(
              FROM_HERE,
              base::BindOnce(
                  [](std::map<uint16_t, std::unique_ptr<MockDeviceWrapper>>*
                         peer_devices,
                     uint16_t conn_id, uint16_t handle, GATT_READ_OP_CB cb,
                     void* cb_data) -> void {
                    if (peer_devices->count(conn_id)) {
                      auto& device = peer_devices->at(conn_id);
                      auto svc = std::find_if(
                          device->services.begin(), device->services.end(),
                          [handle](const gatt::Service& svc) {
                            return (handle >= svc.handle) &&
                                   (handle <= svc.end_handle);
                          });
                      if (svc == device->services.end()) return;

                      // Dispatch to mockable handler functions
                      if (svc->handle == device->csis->start) {
                        device->csis->OnReadCharacteristic(handle, cb, cb_data);
                      } else if (svc->handle == device->cas->start) {
                        device->cas->OnReadCharacteristic(handle, cb, cb_data);
                      } else if (svc->handle == device->ascs->start) {
                        device->ascs->OnReadCharacteristic(handle, cb, cb_data);
                      } else if (svc->handle == device->pacs->start) {
                        device->pacs->OnReadCharacteristic(handle, cb, cb_data);
                      }
                    }
                  },
                  &peer_devices, conn_id, handle, cb, cb_data));
        }));
  }

  void SetUpMockGroups() {
    MockCsisClient::SetMockInstanceForTesting(&mock_csis_client_module_);
    MockDeviceGroups::SetMockInstanceForTesting(&mock_groups_module_);
    MockLeAudioGroupStateMachine::SetMockInstanceForTesting(
        &mock_state_machine_);

    ON_CALL(mock_csis_client_module_, Get())
        .WillByDefault(Return(&mock_csis_client_module_));

    // Store group callbacks so that we could inject grouping events
    group_callbacks_ = nullptr;
    ON_CALL(mock_groups_module_, Initialize(_))
        .WillByDefault(SaveArg<0>(&group_callbacks_));

    ON_CALL(mock_groups_module_, GetGroupId(_, _))
        .WillByDefault([this](const RawAddress& addr, bluetooth::Uuid uuid) {
          if (groups.find(addr) != groups.end()) return groups.at(addr);
          return bluetooth::groups::kGroupUnknown;
        });

    ON_CALL(mock_groups_module_, RemoveDevice(_, _))
        .WillByDefault([this](const RawAddress& addr, int group_id_) {
          int group_id = -1;
          if (groups.find(addr) != groups.end()) {
            group_id = groups[addr];
            groups.erase(addr);
          }
          if (group_id < 0) return;

          do_in_main_thread(
              FROM_HERE,
              base::BindOnce(
                  [](const RawAddress& address, int group_id,
                     bluetooth::groups::DeviceGroupsCallbacks*
                         group_callbacks) {
                    group_callbacks->OnGroupMemberRemoved(address, group_id);
                  },
                  addr, group_id, base::Unretained(group_callbacks_)));
        });

    // Our test devices have unique LSB - use it for unique grouping when
    // devices added with a non-CIS context and no grouping info
    ON_CALL(mock_groups_module_,
            AddDevice(_, le_audio::uuid::kCapServiceUuid, _))
        .WillByDefault(
            [this](const RawAddress& addr,
                   bluetooth::Uuid uuid = le_audio::uuid::kCapServiceUuid,
                   int group_id = bluetooth::groups::kGroupUnknown) -> int {
              if (group_id == bluetooth::groups::kGroupUnknown) {
                /* Generate group id from address */
                groups[addr] = addr.address[RawAddress::kLength - 1];
                group_id = groups[addr];
              } else {
                groups[addr] = group_id;
              }

              InjectGroupDeviceAdded(addr, groups[addr]);
              return addr.address[RawAddress::kLength - 1];
            });

    ON_CALL(mock_state_machine_, Initialize(_))
        .WillByDefault(SaveArg<0>(&state_machine_callbacks_));

    ON_CALL(mock_state_machine_, ConfigureStream(_, _, _, _))
        .WillByDefault([this](LeAudioDeviceGroup* group,
                              types::LeAudioContextType context_type,
                              types::AudioContexts metadata_context_type,
                              std::vector<uint8_t> ccid_list) {
          bool isReconfiguration = group->IsPendingConfiguration();

          /* This shall be called only for user reconfiguration */
          if (!isReconfiguration) return false;

          /* Do what ReleaseCisIds(group) does: start */
          LeAudioDevice* leAudioDevice = group->GetFirstDevice();
          while (leAudioDevice != nullptr) {
            for (auto& ase : leAudioDevice->ases_) {
              ase.cis_id = le_audio::kInvalidCisId;
            }
            leAudioDevice = group->GetNextDevice(leAudioDevice);
          }
          group->CigClearCis();
          /* end */

          if (!group->Configure(context_type, metadata_context_type,
                                ccid_list)) {
            LOG_ERROR("Could not configure ASEs for group %d content type %d",
                      group->group_id_, int(context_type));

            return false;
          }

          group->CigGenerateCisIds(context_type);

          for (LeAudioDevice* device = group->GetFirstDevice();
               device != nullptr; device = group->GetNextDevice(device)) {
            for (auto& ase : device->ases_) {
              ase.data_path_state = types::AudioStreamDataPathState::IDLE;
              ase.active = false;
              ase.state =
                  types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED;
            }
          }

          // Inject the state
          group->SetTargetState(
              types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);
          group->SetState(group->GetTargetState());
          group->ClearPendingConfiguration();
          do_in_main_thread(
              FROM_HERE, base::BindOnce(
                             [](int group_id,
                                le_audio::LeAudioGroupStateMachine::Callbacks*
                                    state_machine_callbacks) {
                               state_machine_callbacks->StatusReportCb(
                                   group_id,
                                   GroupStreamStatus::CONFIGURED_BY_USER);
                             },
                             group->group_id_,
                             base::Unretained(this->state_machine_callbacks_)));
          return true;
        });

    ON_CALL(mock_state_machine_, AttachToStream(_, _))
        .WillByDefault([](LeAudioDeviceGroup* group,
                          LeAudioDevice* leAudioDevice) {
          if (group->GetState() !=
              types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
            return false;
          }

          group->Configure(group->GetConfigurationContextType(),
                           group->GetMetadataContexts(), {});
          if (!group->CigAssignCisIds(leAudioDevice)) return false;
          group->CigAssignCisConnHandlesToAses(leAudioDevice);

          auto* stream_conf = &group->stream_conf;

          for (auto& ase : leAudioDevice->ases_) {
            if (!ase.active) continue;

            // And also skip the ase establishment procedure which should
            // be tested as part of the state machine unit tests
            ase.data_path_state =
                types::AudioStreamDataPathState::DATA_PATH_ESTABLISHED;
            ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING;

            uint16_t cis_conn_hdl = ase.cis_conn_hdl;

            /* Copied from state_machine.cc ProcessHciNotifSetupIsoDataPath */
            if (ase.direction == le_audio::types::kLeAudioDirectionSource) {
              auto iter = std::find_if(stream_conf->source_streams.begin(),
                                       stream_conf->source_streams.end(),
                                       [cis_conn_hdl](auto& pair) {
                                         return cis_conn_hdl == pair.first;
                                       });

              if (iter == stream_conf->source_streams.end()) {
                stream_conf->source_streams.emplace_back(
                    std::make_pair(ase.cis_conn_hdl,
                                   *ase.codec_config.audio_channel_allocation));

                stream_conf->source_num_of_devices++;
                stream_conf->source_num_of_channels +=
                    ase.codec_config.channel_count;

                LOG_INFO(
                    " Added Source Stream Configuration. CIS Connection "
                    "Handle: %d"
                    ", Audio Channel Allocation: %d"
                    ", Source Number Of Devices: %d"
                    ", Source Number Of Channels: %d",
                    +ase.cis_conn_hdl,
                    +(*ase.codec_config.audio_channel_allocation),
                    +stream_conf->source_num_of_devices,
                    +stream_conf->source_num_of_channels);
              }
            } else {
              auto iter = std::find_if(stream_conf->sink_streams.begin(),
                                       stream_conf->sink_streams.end(),
                                       [cis_conn_hdl](auto& pair) {
                                         return cis_conn_hdl == pair.first;
                                       });

              if (iter == stream_conf->sink_streams.end()) {
                stream_conf->sink_streams.emplace_back(
                    std::make_pair(ase.cis_conn_hdl,
                                   *ase.codec_config.audio_channel_allocation));

                stream_conf->sink_num_of_devices++;
                stream_conf->sink_num_of_channels +=
                    ase.codec_config.channel_count;

                LOG_INFO(
                    " Added Sink Stream Configuration. CIS Connection Handle: "
                    "%d"
                    ", Audio Channel Allocation: %d"
                    ", Sink Number Of Devices: %d"
                    ", Sink Number Of Channels: %d",
                    +ase.cis_conn_hdl,
                    +(*ase.codec_config.audio_channel_allocation),
                    +stream_conf->sink_num_of_devices,
                    +stream_conf->sink_num_of_channels);
              }
            }
          }

          return true;
        });

    ON_CALL(mock_state_machine_, StartStream(_, _, _, _))
        .WillByDefault([this](LeAudioDeviceGroup* group,
                              types::LeAudioContextType context_type,
                              types::AudioContexts metadata_context_type,
                              std::vector<uint8_t> ccid_list) {
          /* Do what ReleaseCisIds(group) does: start */
          LeAudioDevice* leAudioDevice = group->GetFirstDevice();
          while (leAudioDevice != nullptr) {
            for (auto& ase : leAudioDevice->ases_) {
              ase.cis_id = le_audio::kInvalidCisId;
            }
            leAudioDevice = group->GetNextDevice(leAudioDevice);
          }
          group->CigClearCis();
          /* end */

          if (!group->Configure(context_type, metadata_context_type,
                                ccid_list)) {
            LOG(ERROR) << __func__ << ", failed to set ASE configuration";
            return false;
          }

          if (group->GetState() ==
              types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE) {
            group->CigGenerateCisIds(context_type);

            std::vector<uint16_t> conn_handles;
            for (uint8_t i = 0; i < (uint8_t)(group->cises_.size()); i++) {
              conn_handles.push_back(iso_con_counter_++);
            }
            group->CigAssignCisConnHandles(conn_handles);
            for (LeAudioDevice* device = group->GetFirstActiveDevice();
                 device != nullptr;
                 device = group->GetNextActiveDevice(device)) {
              if (!group->CigAssignCisIds(device)) return false;
              group->CigAssignCisConnHandlesToAses(device);
            }
          }

          auto* stream_conf = &group->stream_conf;

          // Fake ASE configuration
          for (LeAudioDevice* device = group->GetFirstActiveDevice();
               device != nullptr; device = group->GetNextActiveDevice(device)) {
            for (auto& ase : device->ases_) {
              if (!ase.active) continue;

              // And also skip the ase establishment procedure which should
              // be tested as part of the state machine unit tests
              ase.data_path_state =
                  types::AudioStreamDataPathState::DATA_PATH_ESTABLISHED;
              ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING;

              uint16_t cis_conn_hdl = ase.cis_conn_hdl;

              /* Copied from state_machine.cc ProcessHciNotifSetupIsoDataPath */
              if (ase.direction == le_audio::types::kLeAudioDirectionSource) {
                auto iter = std::find_if(stream_conf->source_streams.begin(),
                                         stream_conf->source_streams.end(),
                                         [cis_conn_hdl](auto& pair) {
                                           return cis_conn_hdl == pair.first;
                                         });

                if (iter == stream_conf->source_streams.end()) {
                  stream_conf->source_streams.emplace_back(std::make_pair(
                      ase.cis_conn_hdl,
                      *ase.codec_config.audio_channel_allocation));

                  stream_conf->source_num_of_devices++;
                  stream_conf->source_num_of_channels +=
                      ase.codec_config.channel_count;
                  stream_conf->source_audio_channel_allocation |=
                      *ase.codec_config.audio_channel_allocation;

                  if (stream_conf->source_sample_frequency_hz == 0) {
                    stream_conf->source_sample_frequency_hz =
                        ase.codec_config.GetSamplingFrequencyHz();
                  } else {
                    ASSERT_LOG(stream_conf->source_sample_frequency_hz ==
                                   ase.codec_config.GetSamplingFrequencyHz(),
                               "sample freq mismatch: %d!=%d",
                               stream_conf->source_sample_frequency_hz,
                               ase.codec_config.GetSamplingFrequencyHz());
                  }

                  if (stream_conf->source_octets_per_codec_frame == 0) {
                    stream_conf->source_octets_per_codec_frame =
                        *ase.codec_config.octets_per_codec_frame;
                  } else {
                    ASSERT_LOG(stream_conf->source_octets_per_codec_frame ==
                                   *ase.codec_config.octets_per_codec_frame,
                               "octets per frame mismatch: %d!=%d",
                               stream_conf->source_octets_per_codec_frame,
                               *ase.codec_config.octets_per_codec_frame);
                  }

                  if (stream_conf->source_codec_frames_blocks_per_sdu == 0) {
                    stream_conf->source_codec_frames_blocks_per_sdu =
                        *ase.codec_config.codec_frames_blocks_per_sdu;
                  } else {
                    ASSERT_LOG(
                        stream_conf->source_codec_frames_blocks_per_sdu ==
                            *ase.codec_config.codec_frames_blocks_per_sdu,
                        "codec_frames_blocks_per_sdu: %d!=%d",
                        stream_conf->source_codec_frames_blocks_per_sdu,
                        *ase.codec_config.codec_frames_blocks_per_sdu);
                  }

                  LOG_INFO(
                      " Added Source Stream Configuration. CIS Connection "
                      "Handle: %d"
                      ", Audio Channel Allocation: %d"
                      ", Source Number Of Devices: %d"
                      ", Source Number Of Channels: %d",
                      +ase.cis_conn_hdl,
                      +(*ase.codec_config.audio_channel_allocation),
                      +stream_conf->source_num_of_devices,
                      +stream_conf->source_num_of_channels);
                }
              } else {
                auto iter = std::find_if(stream_conf->sink_streams.begin(),
                                         stream_conf->sink_streams.end(),
                                         [cis_conn_hdl](auto& pair) {
                                           return cis_conn_hdl == pair.first;
                                         });

                if (iter == stream_conf->sink_streams.end()) {
                  stream_conf->sink_streams.emplace_back(std::make_pair(
                      ase.cis_conn_hdl,
                      *ase.codec_config.audio_channel_allocation));

                  stream_conf->sink_num_of_devices++;
                  stream_conf->sink_num_of_channels +=
                      ase.codec_config.channel_count;

                  stream_conf->sink_audio_channel_allocation |=
                      *ase.codec_config.audio_channel_allocation;

                  if (stream_conf->sink_sample_frequency_hz == 0) {
                    stream_conf->sink_sample_frequency_hz =
                        ase.codec_config.GetSamplingFrequencyHz();
                  } else {
                    ASSERT_LOG(stream_conf->sink_sample_frequency_hz ==
                                   ase.codec_config.GetSamplingFrequencyHz(),
                               "sample freq mismatch: %d!=%d",
                               stream_conf->sink_sample_frequency_hz,
                               ase.codec_config.GetSamplingFrequencyHz());
                  }

                  if (stream_conf->sink_octets_per_codec_frame == 0) {
                    stream_conf->sink_octets_per_codec_frame =
                        *ase.codec_config.octets_per_codec_frame;
                  } else {
                    ASSERT_LOG(stream_conf->sink_octets_per_codec_frame ==
                                   *ase.codec_config.octets_per_codec_frame,
                               "octets per frame mismatch: %d!=%d",
                               stream_conf->sink_octets_per_codec_frame,
                               *ase.codec_config.octets_per_codec_frame);
                  }

                  if (stream_conf->sink_codec_frames_blocks_per_sdu == 0) {
                    stream_conf->sink_codec_frames_blocks_per_sdu =
                        *ase.codec_config.codec_frames_blocks_per_sdu;
                  } else {
                    ASSERT_LOG(
                        stream_conf->sink_codec_frames_blocks_per_sdu ==
                            *ase.codec_config.codec_frames_blocks_per_sdu,
                        "codec_frames_blocks_per_sdu: %d!=%d",
                        stream_conf->sink_codec_frames_blocks_per_sdu,
                        *ase.codec_config.codec_frames_blocks_per_sdu);
                  }

                  LOG_INFO(
                      " Added Sink Stream Configuration. CIS Connection "
                      "Handle: %d"
                      ", Audio Channel Allocation: %d"
                      ", Sink Number Of Devices: %d"
                      ", Sink Number Of Channels: %d",
                      +ase.cis_conn_hdl,
                      +(*ase.codec_config.audio_channel_allocation),
                      +stream_conf->sink_num_of_devices,
                      +stream_conf->sink_num_of_channels);
                }
              }
            }
          }

          // Inject the state
          group->SetTargetState(
              types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
          group->SetState(group->GetTargetState());
          streaming_groups[group->group_id_] = group;

          /* Assume CIG is created */
          group->cig_state_ = le_audio::types::CigState::CREATED;

          do_in_main_thread(
              FROM_HERE, base::BindOnce(
                             [](int group_id,
                                le_audio::LeAudioGroupStateMachine::Callbacks*
                                    state_machine_callbacks) {
                               state_machine_callbacks->StatusReportCb(
                                   group_id, GroupStreamStatus::STREAMING);
                             },
                             group->group_id_,
                             base::Unretained(this->state_machine_callbacks_)));
          return true;
        });

    ON_CALL(mock_state_machine_, SuspendStream(_))
        .WillByDefault([this](LeAudioDeviceGroup* group) {
          // Fake ASE state
          for (LeAudioDevice* device = group->GetFirstDevice();
               device != nullptr; device = group->GetNextDevice(device)) {
            for (auto& ase : device->ases_) {
              ase.data_path_state =
                  types::AudioStreamDataPathState::CIS_ESTABLISHED;
              ase.active = false;
              ase.state =
                  types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED;
            }
          }

          // Inject the state
          group->SetTargetState(
              types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);
          group->SetState(group->GetTargetState());
          state_machine_callbacks_->StatusReportCb(
              group->group_id_, GroupStreamStatus::SUSPENDED);
        });

    ON_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
        .WillByDefault([this](LeAudioDeviceGroup* group,
                              LeAudioDevice* leAudioDevice) {
          if (!group) return;
          auto* stream_conf = &group->stream_conf;
          if (!stream_conf->sink_streams.empty() ||
              !stream_conf->source_streams.empty()) {
            stream_conf->sink_streams.erase(
                std::remove_if(stream_conf->sink_streams.begin(),
                               stream_conf->sink_streams.end(),
                               [leAudioDevice, &stream_conf](auto& pair) {
                                 auto ases = leAudioDevice->GetAsesByCisConnHdl(
                                     pair.first);
                                 if (ases.sink) {
                                   stream_conf->sink_num_of_devices--;
                                   stream_conf->sink_num_of_channels -=
                                       ases.sink->codec_config.channel_count;

                                   LOG_INFO(
                                       ", Source Number Of Devices: %d"
                                       ", Source Number Of Channels: %d",
                                       +stream_conf->source_num_of_devices,
                                       +stream_conf->source_num_of_channels);
                                 }
                                 return ases.sink;
                               }),
                stream_conf->sink_streams.end());

            stream_conf->source_streams.erase(
                std::remove_if(stream_conf->source_streams.begin(),
                               stream_conf->source_streams.end(),
                               [leAudioDevice, &stream_conf](auto& pair) {
                                 auto ases = leAudioDevice->GetAsesByCisConnHdl(
                                     pair.first);
                                 if (ases.source) {
                                   stream_conf->source_num_of_devices--;
                                   stream_conf->source_num_of_channels -=
                                       ases.source->codec_config.channel_count;

                                   LOG_INFO(
                                       ", Source Number Of Devices: %d"
                                       ", Source Number Of Channels: %d",
                                       +stream_conf->source_num_of_devices,
                                       +stream_conf->source_num_of_channels);
                                 }
                                 return ases.source;
                               }),
                stream_conf->source_streams.end());
          }

          group->CigUnassignCis(leAudioDevice);

          if (group->IsEmpty()) {
            group->cig_state_ = le_audio::types::CigState::NONE;
            InjectCigRemoved(group->group_id_);
          }
        });

    ON_CALL(mock_state_machine_, ProcessHciNotifCisDisconnected(_, _, _))
        .WillByDefault(
            [](LeAudioDeviceGroup* group, LeAudioDevice* leAudioDevice,
               const bluetooth::hci::iso_manager::cis_disconnected_evt* event) {
              if (!group) return;
              auto ases_pair =
                  leAudioDevice->GetAsesByCisConnHdl(event->cis_conn_hdl);
              if (ases_pair.sink) {
                ases_pair.sink->data_path_state =
                    types::AudioStreamDataPathState::CIS_ASSIGNED;
                ases_pair.sink->active = false;
              }
              if (ases_pair.source) {
                ases_pair.source->active = false;
                ases_pair.source->data_path_state =
                    types::AudioStreamDataPathState::CIS_ASSIGNED;
              }
              /* Invalidate stream configuration if needed */
              auto* stream_conf = &group->stream_conf;
              if (!stream_conf->sink_streams.empty() ||
                  !stream_conf->source_streams.empty()) {
                stream_conf->sink_streams.erase(
                    std::remove_if(
                        stream_conf->sink_streams.begin(),
                        stream_conf->sink_streams.end(),
                        [leAudioDevice, &stream_conf](auto& pair) {
                          auto ases =
                              leAudioDevice->GetAsesByCisConnHdl(pair.first);

                          LOG_INFO(
                              ", sink ase to delete. Cis handle: %d"
                              ", ase pointer: %p",
                              +(int)(pair.first), +ases.sink);
                          if (ases.sink) {
                            stream_conf->sink_num_of_devices--;
                            stream_conf->sink_num_of_channels -=
                                ases.sink->codec_config.channel_count;

                            LOG_INFO(
                                " Sink Number Of Devices: %d"
                                ", Sink Number Of Channels: %d",
                                +stream_conf->sink_num_of_devices,
                                +stream_conf->sink_num_of_channels);
                          }
                          return ases.sink;
                        }),
                    stream_conf->sink_streams.end());

                stream_conf->source_streams.erase(
                    std::remove_if(
                        stream_conf->source_streams.begin(),
                        stream_conf->source_streams.end(),
                        [leAudioDevice, &stream_conf](auto& pair) {
                          auto ases =
                              leAudioDevice->GetAsesByCisConnHdl(pair.first);

                          LOG_INFO(
                              ", source to delete. Cis handle: %d"
                              ", ase pointer: %p",
                              +(int)(pair.first), ases.source);
                          if (ases.source) {
                            stream_conf->source_num_of_devices--;
                            stream_conf->source_num_of_channels -=
                                ases.source->codec_config.channel_count;

                            LOG_INFO(
                                ", Source Number Of Devices: %d"
                                ", Source Number Of Channels: %d",
                                +stream_conf->source_num_of_devices,
                                +stream_conf->source_num_of_channels);
                          }
                          return ases.source;
                        }),
                    stream_conf->source_streams.end());
              }

              group->CigUnassignCis(leAudioDevice);
            });

    ON_CALL(mock_state_machine_, StopStream(_))
        .WillByDefault([this](LeAudioDeviceGroup* group) {
          for (LeAudioDevice* device = group->GetFirstDevice();
               device != nullptr; device = group->GetNextDevice(device)) {
            /* Invalidate stream configuration if needed */
            auto* stream_conf = &group->stream_conf;
            if (!stream_conf->sink_streams.empty() ||
                !stream_conf->source_streams.empty()) {
              stream_conf->sink_streams.erase(
                  std::remove_if(stream_conf->sink_streams.begin(),
                                 stream_conf->sink_streams.end(),
                                 [device, &stream_conf](auto& pair) {
                                   auto ases =
                                       device->GetAsesByCisConnHdl(pair.first);

                                   LOG_INFO(
                                       ", sink ase to delete. Cis handle: %d"
                                       ", ase pointer: %p",
                                       +(int)(pair.first), +ases.sink);
                                   if (ases.sink) {
                                     stream_conf->sink_num_of_devices--;
                                     stream_conf->sink_num_of_channels -=
                                         ases.sink->codec_config.channel_count;

                                     LOG_INFO(
                                         " Sink Number Of Devices: %d"
                                         ", Sink Number Of Channels: %d",
                                         +stream_conf->sink_num_of_devices,
                                         +stream_conf->sink_num_of_channels);
                                   }
                                   return ases.sink;
                                 }),
                  stream_conf->sink_streams.end());

              stream_conf->source_streams.erase(
                  std::remove_if(
                      stream_conf->source_streams.begin(),
                      stream_conf->source_streams.end(),
                      [device, &stream_conf](auto& pair) {
                        auto ases = device->GetAsesByCisConnHdl(pair.first);

                        LOG_INFO(
                            ", source to delete. Cis handle: %d"
                            ", ase pointer: %p",
                            +(int)(pair.first), +ases.source);
                        if (ases.source) {
                          stream_conf->source_num_of_devices--;
                          stream_conf->source_num_of_channels -=
                              ases.source->codec_config.channel_count;

                          LOG_INFO(
                              ", Source Number Of Devices: %d"
                              ", Source Number Of Channels: %d",
                              +stream_conf->source_num_of_devices,
                              +stream_conf->source_num_of_channels);
                        }
                        return ases.source;
                      }),
                  stream_conf->source_streams.end());
            }

            group->CigUnassignCis(device);

            for (auto& ase : device->ases_) {
              ase.data_path_state = types::AudioStreamDataPathState::IDLE;
              ase.active = false;
              ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE;
              ase.cis_id = 0;
              ase.cis_conn_hdl = 0;
            }
          }

          // Inject the state
          group->SetTargetState(types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
          group->SetState(group->GetTargetState());
          state_machine_callbacks_->StatusReportCb(
              group->group_id_, GroupStreamStatus::RELEASING);
          state_machine_callbacks_->StatusReportCb(group->group_id_,
                                                   GroupStreamStatus::IDLE);
        });
  }

  void SetUp() override {
    init_message_loop_thread();
    ON_CALL(controller_interface_, SupportsBleConnectedIsochronousStreamCentral)
        .WillByDefault(Return(true));
    ON_CALL(controller_interface_,
            SupportsBleConnectedIsochronousStreamPeripheral)
        .WillByDefault(Return(true));

    controller::SetMockControllerInterface(&controller_interface_);
    bluetooth::manager::SetMockBtmInterface(&mock_btm_interface_);
    gatt::SetMockBtaGattInterface(&mock_gatt_interface_);
    gatt::SetMockBtaGattQueue(&mock_gatt_queue_);
    bluetooth::storage::SetMockBtifStorageInterface(&mock_btif_storage_);

    iso_manager_ = bluetooth::hci::IsoManager::GetInstance();
    ASSERT_NE(iso_manager_, nullptr);
    iso_manager_->Start();

    mock_iso_manager_ = MockIsoManager::GetInstance();
    ON_CALL(*mock_iso_manager_, RegisterCigCallbacks(_))
        .WillByDefault(SaveArg<0>(&cig_callbacks_));

    SetUpMockAudioHal();
    SetUpMockGroups();
    SetUpMockGatt();

    supported_snk_context_types_ = 0xffff;
    supported_src_context_types_ = 0xffff;
    le_audio::AudioSetConfigurationProvider::Initialize();
    ASSERT_FALSE(LeAudioClient::IsLeAudioClientRunning());
  }

  void TearDown() override {
    if (is_audio_unicast_source_acquired) {
      if (unicast_source_hal_cb_ != nullptr) {
        EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(1);
      }
      EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
    }

    if (is_audio_unicast_sink_acquired) {
      if (unicast_sink_hal_cb_ != nullptr) {
        EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop).Times(1);
      }
      EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
    }

    // Message loop cleanup should wait for all the 'till now' scheduled calls
    // so it should be called right at the very begginning of teardown.
    cleanup_message_loop_thread();

    // This is required since Stop() and Cleanup() may trigger some callbacks or
    // drop unique pointers to mocks we have raw pointer for and we want to
    // verify them all.
    Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

    if (LeAudioClient::IsLeAudioClientRunning()) {
      EXPECT_CALL(mock_gatt_interface_, AppDeregister(gatt_if)).Times(1);
      LeAudioClient::Cleanup(base::DoNothing());
      ASSERT_FALSE(LeAudioClient::IsLeAudioClientRunning());
    }

    if (le_audio::AudioSetConfigurationProvider::Get())
      le_audio::AudioSetConfigurationProvider::Cleanup();

    iso_manager_->Stop();
  }

 protected:
  class MockDeviceWrapper {
    class IGattHandlers {
     public:
      // IGattHandlers() = default;
      virtual ~IGattHandlers() = default;
      virtual void OnReadCharacteristic(uint16_t handle, GATT_READ_OP_CB cb,
                                        void* cb_data) = 0;
      virtual void OnWriteCharacteristic(uint16_t handle,
                                         std::vector<uint8_t> value,
                                         tGATT_WRITE_TYPE write_type,
                                         GATT_WRITE_OP_CB cb,
                                         void* cb_data) = 0;
    };

   public:
    struct csis_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t end = 0;
      uint16_t sirk_char = 0;
      uint16_t sirk_ccc = 0;
      uint16_t size_char = 0;
      uint16_t size_ccc = 0;
      uint16_t lock_char = 0;
      uint16_t lock_ccc = 0;
      uint16_t rank_char = 0;

      int rank = 0;
      int size = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    struct cas_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t end = 0;
      uint16_t csis_include = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    struct pacs_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t sink_pac_char = 0;
      uint16_t sink_pac_ccc = 0;
      uint16_t sink_audio_loc_char = 0;
      uint16_t sink_audio_loc_ccc = 0;
      uint16_t source_pac_char = 0;
      uint16_t source_pac_ccc = 0;
      uint16_t source_audio_loc_char = 0;
      uint16_t source_audio_loc_ccc = 0;
      uint16_t avail_contexts_char = 0;
      uint16_t avail_contexts_ccc = 0;
      uint16_t supp_contexts_char = 0;
      uint16_t supp_contexts_ccc = 0;
      uint16_t end = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    struct ascs_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t sink_ase_char[max_num_of_ases] = {0};
      uint16_t sink_ase_ccc[max_num_of_ases] = {0};
      uint16_t source_ase_char[max_num_of_ases] = {0};
      uint16_t source_ase_ccc[max_num_of_ases] = {0};
      uint16_t ctp_char = 0;
      uint16_t ctp_ccc = 0;
      uint16_t end = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    MockDeviceWrapper(RawAddress addr, const std::list<gatt::Service>& services,
                      std::unique_ptr<MockDeviceWrapper::csis_mock> csis,
                      std::unique_ptr<MockDeviceWrapper::cas_mock> cas,
                      std::unique_ptr<MockDeviceWrapper::ascs_mock> ascs,
                      std::unique_ptr<MockDeviceWrapper::pacs_mock> pacs)
        : addr(addr) {
      this->services = services;
      this->csis = std::move(csis);
      this->cas = std::move(cas);
      this->ascs = std::move(ascs);
      this->pacs = std::move(pacs);
    }

    ~MockDeviceWrapper() {
      Mock::VerifyAndClearExpectations(csis.get());
      Mock::VerifyAndClearExpectations(cas.get());
      Mock::VerifyAndClearExpectations(ascs.get());
      Mock::VerifyAndClearExpectations(pacs.get());
    }

    RawAddress addr;
    bool connected = false;

    // A list of services and their useful params
    std::list<gatt::Service> services;
    std::unique_ptr<csis_mock> csis;
    std::unique_ptr<cas_mock> cas;
    std::unique_ptr<ascs_mock> ascs;
    std::unique_ptr<pacs_mock> pacs;
  };

  void SyncOnMainLoop() {
    // Wait for the main loop to flush
    // WARNING: Not tested with Timers pushing periodic tasks to the main loop
    while (num_async_tasks > 0)
      ;
  }

  void ConnectLeAudio(const RawAddress& address, bool isEncrypted = true) {
    // by default indicate link as encrypted
    ON_CALL(mock_btm_interface_, BTM_IsEncrypted(address, _))
        .WillByDefault(DoAll(Return(isEncrypted)));

    EXPECT_CALL(mock_gatt_interface_,
                Open(gatt_if, address, BTM_BLE_DIRECT_CONNECTION, _))
        .Times(1);

    do_in_main_thread(
        FROM_HERE, base::Bind(&LeAudioClient::Connect,
                              base::Unretained(LeAudioClient::Get()), address));

    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  }

  void DisconnectLeAudio(const RawAddress& address, uint16_t conn_id) {
    SyncOnMainLoop();
    EXPECT_CALL(mock_gatt_interface_, Close(conn_id)).Times(1);
    EXPECT_CALL(mock_audio_hal_client_callbacks_,
                OnConnectionState(ConnectionState::DISCONNECTED, address))
        .Times(1);
    do_in_main_thread(
        FROM_HERE, base::Bind(&LeAudioClient::Disconnect,
                              base::Unretained(LeAudioClient::Get()), address));
  }

  void ConnectCsisDevice(const RawAddress& addr, uint16_t conn_id,
                         uint32_t sink_audio_allocation,
                         uint32_t source_audio_allocation, uint8_t group_size,
                         int group_id, uint8_t rank,
                         bool connect_through_csis = false,
                         bool new_device = true) {
    SetSampleDatabaseEarbudsValid(conn_id, addr, sink_audio_allocation,
                                  source_audio_allocation, default_channel_cnt,
                                  default_channel_cnt,
                                  0x0004, /* source sample freq 16khz */
                                  true,   /*add_csis*/
                                  true,   /*add_cas*/
                                  true,   /*add_pacs*/
                                  true,   /*add_ascs*/
                                  group_size, rank);
    EXPECT_CALL(mock_audio_hal_client_callbacks_,
                OnConnectionState(ConnectionState::CONNECTED, addr))
        .Times(1);

    if (new_device) {
      EXPECT_CALL(mock_audio_hal_client_callbacks_,
                  OnGroupNodeStatus(addr, group_id, GroupNodeStatus::ADDED))
          .Times(1);
    }

    if (connect_through_csis) {
      // Add it the way CSIS would do: add to group and then connect
      do_in_main_thread(
          FROM_HERE,
          base::Bind(&LeAudioClient::GroupAddNode,
                     base::Unretained(LeAudioClient::Get()), group_id, addr));
      ConnectLeAudio(addr);
    } else {
      // The usual connect
      // Since device has CSIS, lets add it here to groups already now
      groups[addr] = group_id;
      ConnectLeAudio(addr);
      InjectGroupDeviceAdded(addr, group_id);
    }
  }

  void ConnectNonCsisDevice(const RawAddress& addr, uint16_t conn_id,
                            uint32_t sink_audio_allocation,
                            uint32_t source_audio_allocation) {
    SetSampleDatabaseEarbudsValid(
        conn_id, addr, sink_audio_allocation, source_audio_allocation,
        default_channel_cnt, default_channel_cnt, 0x0004,
        /* source sample freq 16khz */ false, /*add_csis*/
        true,                                 /*add_cas*/
        true,                                 /*add_pacs*/
        true,                                 /*add_ascs*/
        0, 0);
    EXPECT_CALL(mock_audio_hal_client_callbacks_,
                OnConnectionState(ConnectionState::CONNECTED, addr))
        .Times(1);

    ConnectLeAudio(addr);
  }

  void UpdateMetadata(audio_usage_t usage, audio_content_type_t content_type,
                      bool reconfigure_existing_stream = false) {
    std::vector<struct playback_track_metadata> source_metadata = {
        {{AUDIO_USAGE_UNKNOWN, AUDIO_CONTENT_TYPE_UNKNOWN, 0},
         {AUDIO_USAGE_UNKNOWN, AUDIO_CONTENT_TYPE_UNKNOWN, 0}}};

    source_metadata[0].usage = usage;
    source_metadata[0].content_type = content_type;

    if (reconfigure_existing_stream) {
      Expectation reconfigure = EXPECT_CALL(*mock_le_audio_source_hal_client_,
                                            SuspendedForReconfiguration())
                                    .Times(1);
      EXPECT_CALL(*mock_le_audio_source_hal_client_, CancelStreamingRequest())
          .Times(1);
      EXPECT_CALL(*mock_le_audio_source_hal_client_, ReconfigurationComplete())
          .Times(1)
          .After(reconfigure);
    } else {
      EXPECT_CALL(*mock_le_audio_source_hal_client_,
                  SuspendedForReconfiguration())
          .Times(0);
      EXPECT_CALL(*mock_le_audio_source_hal_client_, ReconfigurationComplete())
          .Times(0);
    }

    ASSERT_NE(unicast_source_hal_cb_, nullptr);
    unicast_source_hal_cb_->OnAudioMetadataUpdate(source_metadata);
  }

  void UpdateSourceMetadata(audio_source_t audio_source) {
    std::vector<struct record_track_metadata> sink_metadata = {
        {{AUDIO_SOURCE_INVALID, 0.5, AUDIO_DEVICE_NONE, "00:11:22:33:44:55"},
         {AUDIO_SOURCE_MIC, 0.7, AUDIO_DEVICE_OUT_BLE_HEADSET,
          "AA:BB:CC:DD:EE:FF"}}};

    sink_metadata[1].source = audio_source;
    unicast_sink_hal_cb_->OnAudioMetadataUpdate(sink_metadata);
  }

  void SinkAudioResume(void) {
    EXPECT_CALL(*mock_le_audio_source_hal_client_, ConfirmStreamingRequest())
        .Times(1);
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(
                          [](LeAudioSourceAudioHalClient::Callbacks* cb) {
                            cb->OnAudioResume();
                          },
                          unicast_source_hal_cb_));

    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&*mock_le_audio_source_hal_client_);
  }

  void StartStreaming(audio_usage_t usage, audio_content_type_t content_type,
                      int group_id,
                      audio_source_t audio_source = AUDIO_SOURCE_INVALID,
                      bool reconfigure_existing_stream = false) {
    ASSERT_NE(unicast_source_hal_cb_, nullptr);

    UpdateMetadata(usage, content_type, reconfigure_existing_stream);
    if (audio_source != AUDIO_SOURCE_INVALID) {
      UpdateSourceMetadata(audio_source);
    }

    /* Stream has been automatically restarted on UpdateMetadata */
    if (reconfigure_existing_stream) return;

    SinkAudioResume();
    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&mock_state_machine_);

    if (usage == AUDIO_USAGE_VOICE_COMMUNICATION ||
        audio_source != AUDIO_SOURCE_INVALID) {
      ASSERT_NE(unicast_sink_hal_cb_, nullptr);
      do_in_main_thread(FROM_HERE,
                        base::BindOnce(
                            [](LeAudioSinkAudioHalClient::Callbacks* cb) {
                              cb->OnAudioResume();
                            },
                            unicast_sink_hal_cb_));
    }
  }

  void StopStreaming(int group_id, bool suspend_source = false) {
    ASSERT_NE(unicast_source_hal_cb_, nullptr);

    /* TODO We should have a way to confirm Stop() otherwise, audio framework
     * might have different state that it is in the le_audio code - as tearing
     * down CISes might take some time
     */
    std::promise<void> do_suspend_sink_promise;
    auto do_suspend_sink_future = do_suspend_sink_promise.get_future();
    /* It's enough to call only one resume even if it'll be bi-directional
     * streaming. First suspend will trigger GroupStop.
     *
     * There is no - 'only source receiver' scenario (e.g. single microphone).
     * If there will be such test oriented scenario, such resume choose logic
     * should be applied.
     */
    unicast_source_hal_cb_->OnAudioSuspend(std::move(do_suspend_sink_promise));
    do_suspend_sink_future.wait();

    if (suspend_source) {
      ASSERT_NE(unicast_sink_hal_cb_, nullptr);
      std::promise<void> do_suspend_source_promise;
      auto do_suspend_source_future = do_suspend_source_promise.get_future();
      unicast_sink_hal_cb_->OnAudioSuspend(
          std::move(do_suspend_source_promise));
      do_suspend_source_future.wait();
    }
  }

  void set_sample_database(uint16_t conn_id, RawAddress addr,
                           std::unique_ptr<MockDeviceWrapper::csis_mock> csis,
                           std::unique_ptr<MockDeviceWrapper::cas_mock> cas,
                           std::unique_ptr<MockDeviceWrapper::ascs_mock> ascs,
                           std::unique_ptr<MockDeviceWrapper::pacs_mock> pacs) {
    gatt::DatabaseBuilder bob;

    /* Generic Access Service */
    bob.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    /* Device Name Char. */
    bob.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                          GATT_CHAR_PROP_BIT_READ);

    if (csis->start) {
      bool is_primary = true;
      bob.AddService(csis->start, csis->end, bluetooth::csis::kCsisServiceUuid,
                     is_primary);
      if (csis->sirk_char) {
        bob.AddCharacteristic(
            csis->sirk_char, csis->sirk_char + 1,
            bluetooth::csis::kCsisSirkUuid,
            GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
        if (csis->sirk_ccc)
          bob.AddDescriptor(csis->sirk_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (csis->size_char) {
        bob.AddCharacteristic(
            csis->size_char, csis->size_char + 1,
            bluetooth::csis::kCsisSizeUuid,
            GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
        if (csis->size_ccc)
          bob.AddDescriptor(csis->size_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (csis->lock_char) {
        bob.AddCharacteristic(csis->lock_char, csis->lock_char + 1,
                              bluetooth::csis::kCsisLockUuid,
                              GATT_CHAR_PROP_BIT_READ |
                                  GATT_CHAR_PROP_BIT_NOTIFY |
                                  GATT_CHAR_PROP_BIT_WRITE);
        if (csis->lock_ccc)
          bob.AddDescriptor(csis->lock_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (csis->rank_char)
        bob.AddCharacteristic(csis->rank_char, csis->rank_char + 1,
                              bluetooth::csis::kCsisRankUuid,
                              GATT_CHAR_PROP_BIT_READ);
    }

    if (cas->start) {
      bool is_primary = true;
      bob.AddService(cas->start, cas->end, le_audio::uuid::kCapServiceUuid,
                     is_primary);
      // Include CSIS service inside
      if (cas->csis_include)
        bob.AddIncludedService(cas->csis_include,
                               bluetooth::csis::kCsisServiceUuid, csis->start,
                               csis->end);
    }

    if (pacs->start) {
      bool is_primary = true;
      bob.AddService(pacs->start, pacs->end,
                     le_audio::uuid::kPublishedAudioCapabilityServiceUuid,
                     is_primary);

      if (pacs->sink_pac_char) {
        bob.AddCharacteristic(
            pacs->sink_pac_char, pacs->sink_pac_char + 1,
            le_audio::uuid::kSinkPublishedAudioCapabilityCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->sink_pac_ccc)
          bob.AddDescriptor(pacs->sink_pac_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->sink_audio_loc_char) {
        bob.AddCharacteristic(
            pacs->sink_audio_loc_char, pacs->sink_audio_loc_char + 1,
            le_audio::uuid::kSinkAudioLocationCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->sink_audio_loc_ccc)
          bob.AddDescriptor(pacs->sink_audio_loc_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->source_pac_char) {
        bob.AddCharacteristic(
            pacs->source_pac_char, pacs->source_pac_char + 1,
            le_audio::uuid::kSourcePublishedAudioCapabilityCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->source_pac_ccc)
          bob.AddDescriptor(pacs->source_pac_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->source_audio_loc_char) {
        bob.AddCharacteristic(
            pacs->source_audio_loc_char, pacs->source_audio_loc_char + 1,
            le_audio::uuid::kSourceAudioLocationCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->source_audio_loc_ccc)
          bob.AddDescriptor(pacs->source_audio_loc_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->avail_contexts_char) {
        bob.AddCharacteristic(
            pacs->avail_contexts_char, pacs->avail_contexts_char + 1,
            le_audio::uuid::kAudioContextAvailabilityCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->avail_contexts_ccc)
          bob.AddDescriptor(pacs->avail_contexts_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->supp_contexts_char) {
        bob.AddCharacteristic(
            pacs->supp_contexts_char, pacs->supp_contexts_char + 1,
            le_audio::uuid::kAudioSupportedContextCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->supp_contexts_ccc)
          bob.AddDescriptor(pacs->supp_contexts_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }
    }

    if (ascs->start) {
      bool is_primary = true;
      bob.AddService(ascs->start, ascs->end,
                     le_audio::uuid::kAudioStreamControlServiceUuid,
                     is_primary);
      for (int i = 0; i < max_num_of_ases; i++) {
        if (ascs->sink_ase_char[i]) {
          bob.AddCharacteristic(ascs->sink_ase_char[i],
                                ascs->sink_ase_char[i] + 1,
                                le_audio::uuid::kSinkAudioStreamEndpointUuid,
                                GATT_CHAR_PROP_BIT_READ);
          if (ascs->sink_ase_ccc[i])
            bob.AddDescriptor(ascs->sink_ase_ccc[i],
                              Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
        }
        if (ascs->source_ase_char[i]) {
          bob.AddCharacteristic(ascs->source_ase_char[i],
                                ascs->source_ase_char[i] + 1,
                                le_audio::uuid::kSourceAudioStreamEndpointUuid,
                                GATT_CHAR_PROP_BIT_READ);
          if (ascs->source_ase_ccc[i])
            bob.AddDescriptor(ascs->source_ase_ccc[i],
                              Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
        }
      }
      if (ascs->ctp_char) {
        bob.AddCharacteristic(
            ascs->ctp_char, ascs->ctp_char + 1,
            le_audio::uuid::kAudioStreamEndpointControlPointCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (ascs->ctp_ccc)
          bob.AddDescriptor(ascs->ctp_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }
    }

    // Assign conn_id to a certain device - this does not mean it is connected
    auto dev_wrapper = std::make_unique<MockDeviceWrapper>(
        addr, bob.Build().Services(), std::move(csis), std::move(cas),
        std::move(ascs), std::move(pacs));
    peer_devices.emplace(conn_id, std::move(dev_wrapper));
  }

  void SetSampleDatabaseEmpty(uint16_t conn_id, RawAddress addr) {
    auto csis = std::make_unique<MockDeviceWrapper::csis_mock>();
    auto cas = std::make_unique<MockDeviceWrapper::cas_mock>();
    auto pacs = std::make_unique<MockDeviceWrapper::pacs_mock>();
    auto ascs = std::make_unique<MockDeviceWrapper::ascs_mock>();
    set_sample_database(conn_id, addr, std::move(csis), std::move(cas),
                        std::move(ascs), std::move(pacs));
  }

  void SetSampleDatabaseEarbudsValid(
      uint16_t conn_id, RawAddress addr, uint32_t sink_audio_allocation,
      uint32_t source_audio_allocation, uint8_t sink_channel_cnt = 0x03,
      uint8_t source_channel_cnt = 0x03, uint16_t sample_freq_mask = 0x0004,
      bool add_csis = true, bool add_cas = true, bool add_pacs = true,
      int add_ascs_cnt = 1, uint8_t set_size = 2, uint8_t rank = 1) {
    auto csis = std::make_unique<MockDeviceWrapper::csis_mock>();
    if (add_csis) {
      // attribute handles
      csis->start = 0x0010;
      csis->sirk_char = 0x0020;
      csis->sirk_ccc = 0x0022;
      csis->size_char = 0x0023;
      csis->size_ccc = 0x0025;
      csis->lock_char = 0x0026;
      csis->lock_ccc = 0x0028;
      csis->rank_char = 0x0029;
      csis->end = 0x0030;
      // other params
      csis->size = set_size;
      csis->rank = rank;
    }

    auto cas = std::make_unique<MockDeviceWrapper::cas_mock>();
    if (add_cas) {
      // attribute handles
      cas->start = 0x0040;
      if (add_csis) cas->csis_include = 0x0041;
      cas->end = 0x0050;
      // other params
    }

    auto pacs = std::make_unique<MockDeviceWrapper::pacs_mock>();
    if (add_pacs) {
      // attribute handles
      pacs->start = 0x0060;
      pacs->sink_pac_char = 0x0061;
      pacs->sink_pac_ccc = 0x0063;
      pacs->sink_audio_loc_char = 0x0064;
      pacs->sink_audio_loc_ccc = 0x0066;
      pacs->source_pac_char = 0x0067;
      pacs->source_pac_ccc = 0x0069;
      pacs->source_audio_loc_char = 0x0070;
      pacs->source_audio_loc_ccc = 0x0072;
      pacs->avail_contexts_char = 0x0073;
      pacs->avail_contexts_ccc = 0x0075;
      pacs->supp_contexts_char = 0x0076;
      pacs->supp_contexts_ccc = 0x0078;
      pacs->end = 0x0080;
      // other params
    }

    auto ascs = std::make_unique<MockDeviceWrapper::ascs_mock>();
    if (add_ascs_cnt > 0) {
      // attribute handles
      ascs->start = 0x0090;
      uint16_t handle = 0x0091;
      for (int i = 0; i < add_ascs_cnt; i++) {
        if (sink_audio_allocation != 0) {
          ascs->sink_ase_char[i] = handle;
          handle += 2;
          ascs->sink_ase_ccc[i] = handle;
          handle++;
        }

        if (source_audio_allocation != 0) {
          ascs->source_ase_char[i] = handle;
          handle += 2;
          ascs->source_ase_ccc[i] = handle;
          handle++;
        }
      }
      ascs->ctp_char = handle;
      handle += 2;
      ascs->ctp_ccc = handle;
      handle++;
      ascs->end = handle;
      // other params
    }

    set_sample_database(conn_id, addr, std::move(csis), std::move(cas),
                        std::move(ascs), std::move(pacs));

    if (add_pacs) {
      uint8_t snk_allocation[4];
      uint8_t src_allocation[4];

      snk_allocation[0] = (uint8_t)(sink_audio_allocation);
      snk_allocation[1] = (uint8_t)(sink_audio_allocation >> 8);
      snk_allocation[2] = (uint8_t)(sink_audio_allocation >> 16);
      snk_allocation[3] = (uint8_t)(sink_audio_allocation >> 24);

      src_allocation[0] = (uint8_t)(source_audio_allocation);
      src_allocation[1] = (uint8_t)(source_audio_allocation >> 8);
      src_allocation[2] = (uint8_t)(source_audio_allocation >> 16);
      src_allocation[3] = (uint8_t)(source_audio_allocation >> 24);

      uint8_t sample_freq[2];
      sample_freq[0] = (uint8_t)(sample_freq_mask);
      sample_freq[1] = (uint8_t)(sample_freq_mask >> 8);

      // Set pacs default read values
      ON_CALL(*peer_devices.at(conn_id)->pacs, OnReadCharacteristic(_, _, _))
          .WillByDefault(
              [this, conn_id, snk_allocation, src_allocation, sample_freq,
               sink_channel_cnt, source_channel_cnt](
                  uint16_t handle, GATT_READ_OP_CB cb, void* cb_data) {
                auto& pacs = peer_devices.at(conn_id)->pacs;
                std::vector<uint8_t> value;
                if (handle == pacs->sink_pac_char + 1) {
                  value = {
                      // Num records
                      0x02,
                      // Codec_ID
                      0x06,
                      0x00,
                      0x00,
                      0x00,
                      0x00,
                      // Codec Spec. Caps. Len
                      0x10,
                      0x03, /* sample freq */
                      0x01,
                      sample_freq[0],
                      sample_freq[1],
                      0x02,
                      0x02, /* frame duration */
                      0x03,
                      0x02, /* channel count */
                      0x03,
                      sink_channel_cnt,
                      0x05,
                      0x04,
                      0x1E,
                      0x00,
                      0x78,
                      0x00,
                      // Metadata Length
                      0x00,
                      // Codec_ID
                      0x06,
                      0x00,
                      0x00,
                      0x00,
                      0x00,
                      // Codec Spec. Caps. Len
                      0x10,
                      0x03, /* sample freq */
                      0x01,
                      0x80,
                      0x00,
                      0x02, /* frame duration */
                      0x02,
                      0x03,
                      0x02, /* channel count */
                      0x03,
                      sink_channel_cnt,
                      0x05, /* octects per frame */
                      0x04,
                      0x78,
                      0x00,
                      0x78,
                      0x00,
                      // Metadata Length
                      0x00,
                  };
                } else if (handle == pacs->sink_audio_loc_char + 1) {
                  value = {
                      // Audio Locations
                      snk_allocation[0],
                      snk_allocation[1],
                      snk_allocation[2],
                      snk_allocation[3],
                  };
                } else if (handle == pacs->source_pac_char + 1) {
                  value = {
                      // Num records
                      0x02,
                      // Codec_ID
                      0x06,
                      0x00,
                      0x00,
                      0x00,
                      0x00,
                      // Codec Spec. Caps. Len
                      0x10,
                      0x03,
                      0x01,
                      sample_freq[0],
                      sample_freq[1],
                      0x02,
                      0x02,
                      0x03,
                      0x02,
                      0x03,
                      source_channel_cnt,
                      0x05,
                      0x04,
                      0x1E,
                      0x00,
                      0x78,
                      0x00,
                      // Metadata Length
                      0x00,
                      // Codec_ID
                      0x06,
                      0x00,
                      0x00,
                      0x00,
                      0x00,
                      // Codec Spec. Caps. Len
                      0x10,
                      0x03,
                      0x01,
                      0x24,
                      0x00,
                      0x02,
                      0x02,
                      0x03,
                      0x02,
                      0x03,
                      source_channel_cnt,
                      0x05,
                      0x04,
                      0x1E,
                      0x00,
                      0x50,
                      0x00,
                      // Metadata Length
                      0x00,
                  };
                } else if (handle == pacs->source_audio_loc_char + 1) {
                  value = {
                      // Audio Locations
                      src_allocation[0],
                      src_allocation[1],
                      src_allocation[2],
                      src_allocation[3],
                  };
                } else if (handle == pacs->avail_contexts_char + 1) {
                  value = {
                      // Sink Avail Contexts
                      (uint8_t)(supported_snk_context_types_ >> 8),
                      (uint8_t)(supported_snk_context_types_),
                      // Source Avail Contexts
                      (uint8_t)(supported_src_context_types_ >> 8),
                      (uint8_t)(supported_src_context_types_),
                  };
                } else if (handle == pacs->supp_contexts_char + 1) {
                  value = {
                      // Sink Avail Contexts
                      (uint8_t)(supported_snk_context_types_ >> 8),
                      (uint8_t)(supported_snk_context_types_),
                      // Source Avail Contexts
                      (uint8_t)(supported_src_context_types_ >> 8),
                      (uint8_t)(supported_src_context_types_),
                  };
                }
                cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
                   cb_data);
              });
    }

    if (add_ascs_cnt > 0) {
      // Set ascs default read values
      ON_CALL(*peer_devices.at(conn_id)->ascs, OnReadCharacteristic(_, _, _))
          .WillByDefault([this, conn_id](uint16_t handle, GATT_READ_OP_CB cb,
                                         void* cb_data) {
            auto& ascs = peer_devices.at(conn_id)->ascs;
            std::vector<uint8_t> value;
            bool is_ase_sink_request = false;
            bool is_ase_src_request = false;
            uint8_t idx;
            for (idx = 0; idx < max_num_of_ases; idx++) {
              if (handle == ascs->sink_ase_char[idx] + 1) {
                is_ase_sink_request = true;
                break;
              }
              if (handle == ascs->source_ase_char[idx] + 1) {
                is_ase_src_request = true;
                break;
              }
            }

            if (is_ase_sink_request) {
              value = {
                  // ASE ID
                  static_cast<uint8_t>(idx + 1),
                  // State
                  static_cast<uint8_t>(
                      le_audio::types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE),
                  // No Additional ASE params for IDLE state
              };
            } else if (is_ase_src_request) {
              value = {
                  // ASE ID
                  static_cast<uint8_t>(idx + 6),
                  // State
                  static_cast<uint8_t>(
                      le_audio::types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE),
                  // No Additional ASE params for IDLE state
              };
            }
            cb(conn_id, GATT_SUCCESS, handle, value.size(), value.data(),
               cb_data);
          });
    }
  }

  void TestAudioDataTransfer(int group_id, uint8_t cis_count_out,
                             uint8_t cis_count_in, int data_len,
                             int in_data_len = 40) {
    ASSERT_NE(unicast_source_hal_cb_, nullptr);

    // Expect two channels ISO Data to be sent
    std::vector<uint16_t> handles;
    EXPECT_CALL(*mock_iso_manager_, SendIsoData(_, _, _))
        .Times(cis_count_out)
        .WillRepeatedly(
            [&handles](uint16_t iso_handle, const uint8_t* data,
                       uint16_t data_len) { handles.push_back(iso_handle); });
    std::vector<uint8_t> data(data_len);
    unicast_source_hal_cb_->OnAudioDataReady(data);

    // Inject microphone data from group
    EXPECT_CALL(*mock_le_audio_sink_hal_client_, SendData(_, _))
        .Times(cis_count_in > 0 ? 1 : 0);
    ASSERT_EQ(streaming_groups.count(group_id), 1u);

    if (cis_count_in) {
      ASSERT_NE(unicast_sink_hal_cb_, nullptr);

      auto group = streaming_groups.at(group_id);
      for (LeAudioDevice* device = group->GetFirstDevice(); device != nullptr;
           device = group->GetNextDevice(device)) {
        for (auto& ase : device->ases_) {
          if (ase.direction == le_audio::types::kLeAudioDirectionSource) {
            InjectIncomingIsoData(group_id, ase.cis_conn_hdl, in_data_len);
            --cis_count_in;
            if (!cis_count_in) break;
          }
        }
        if (!cis_count_in) break;
      }
    }

    SyncOnMainLoop();
    std::sort(handles.begin(), handles.end());
    ASSERT_EQ(cis_count_in, 0);
    handles.clear();

    Mock::VerifyAndClearExpectations(mock_iso_manager_);
  }

  void InjectIncomingIsoData(uint16_t cig_id, uint16_t cis_con_hdl,
                             size_t payload_size) {
    BT_HDR* bt_hdr = (BT_HDR*)malloc(sizeof(BT_HDR) + payload_size);

    bt_hdr->offset = 0;
    bt_hdr->len = payload_size;

    bluetooth::hci::iso_manager::cis_data_evt cis_evt;
    cis_evt.cig_id = cig_id;
    cis_evt.cis_conn_hdl = cis_con_hdl;
    cis_evt.ts = 0;
    cis_evt.evt_lost = 0;
    cis_evt.p_msg = bt_hdr;

    ASSERT_NE(cig_callbacks_, nullptr);
    cig_callbacks_->OnCisEvent(
        bluetooth::hci::iso_manager::kIsoEventCisDataAvailable, &cis_evt);
    free(bt_hdr);
  }

  void InjectCisDisconnected(uint16_t cig_id, uint16_t cis_con_hdl,
                             uint8_t reason = 0) {
    bluetooth::hci::iso_manager::cis_disconnected_evt cis_evt;
    cis_evt.cig_id = cig_id;
    cis_evt.cis_conn_hdl = cis_con_hdl;
    cis_evt.reason = reason;

    ASSERT_NE(cig_callbacks_, nullptr);
    cig_callbacks_->OnCisEvent(
        bluetooth::hci::iso_manager::kIsoEventCisDisconnected, &cis_evt);
  }

  void InjectCigRemoved(uint8_t cig_id) {
    bluetooth::hci::iso_manager::cig_remove_cmpl_evt evt;
    evt.status = 0;
    evt.cig_id = cig_id;

    ASSERT_NE(cig_callbacks_, nullptr);
    cig_callbacks_->OnCisEvent(
        bluetooth::hci::iso_manager::kIsoEventCigOnRemoveCmpl, &evt);
  }

  MockAudioHalClientCallbacks mock_audio_hal_client_callbacks_;
  LeAudioSourceAudioHalClient::Callbacks* unicast_source_hal_cb_ = nullptr;
  LeAudioSinkAudioHalClient::Callbacks* unicast_sink_hal_cb_ = nullptr;

  uint8_t default_channel_cnt = 0x03;
  uint8_t default_ase_cnt = 1;

  MockCsisClient mock_csis_client_module_;
  MockDeviceGroups mock_groups_module_;
  bluetooth::groups::DeviceGroupsCallbacks* group_callbacks_;
  MockLeAudioGroupStateMachine mock_state_machine_;

  MockFunction<void()> mock_storage_load;
  MockFunction<bool()> mock_hal_2_1_verifier;

  controller::MockControllerInterface controller_interface_;
  bluetooth::manager::MockBtmInterface mock_btm_interface_;
  gatt::MockBtaGattInterface mock_gatt_interface_;
  gatt::MockBtaGattQueue mock_gatt_queue_;
  tBTA_GATTC_CBACK* gatt_callback;
  const uint8_t gatt_if = 0xfe;
  uint8_t global_conn_id = 1;
  le_audio::LeAudioGroupStateMachine::Callbacks* state_machine_callbacks_;
  std::map<int, LeAudioDeviceGroup*> streaming_groups;

  bluetooth::hci::IsoManager* iso_manager_;
  MockIsoManager* mock_iso_manager_;
  bluetooth::hci::iso_manager::CigCallbacks* cig_callbacks_ = nullptr;
  uint16_t iso_con_counter_ = 1;

  uint16_t supported_snk_context_types_ = 0xffff;
  uint16_t supported_src_context_types_ = 0xffff;

  bluetooth::storage::MockBtifStorageInterface mock_btif_storage_;

  std::map<uint16_t, std::unique_ptr<MockDeviceWrapper>> peer_devices;
  std::list<int> group_locks;
  std::map<RawAddress, int> groups;
};

class UnicastTest : public UnicastTestNoInit {
 protected:
  void SetUp() override {
    UnicastTestNoInit::SetUp();

    EXPECT_CALL(mock_hal_2_1_verifier, Call()).Times(1);
    EXPECT_CALL(mock_storage_load, Call()).Times(1);

    std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
        framework_encode_preference;
    BtaAppRegisterCallback app_register_callback;
    EXPECT_CALL(mock_gatt_interface_, AppRegister(_, _, _))
        .WillOnce(DoAll(SaveArg<0>(&gatt_callback),
                        SaveArg<1>(&app_register_callback)));
    LeAudioClient::Initialize(
        &mock_audio_hal_client_callbacks_,
        base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                   &mock_storage_load),
        base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                   &mock_hal_2_1_verifier),
        framework_encode_preference);

    SyncOnMainLoop();
    ASSERT_TRUE(gatt_callback);
    ASSERT_TRUE(group_callbacks_);
    ASSERT_TRUE(app_register_callback);
    app_register_callback.Run(gatt_if, GATT_SUCCESS);
    Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  }

  void TearDown() override {
    groups.clear();
    UnicastTestNoInit::TearDown();
  }
};

RawAddress GetTestAddress(uint8_t index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {{0xC0, 0xDE, 0xC0, 0xDE, 0x00, index}};
  return result;
}

TEST_F(UnicastTest, Initialize) {
  ASSERT_NE(LeAudioClient::Get(), nullptr);
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
}

TEST_F(UnicastTestNoInit, InitializeNoHal_2_1) {
  ASSERT_FALSE(LeAudioClient::IsLeAudioClientRunning());

  // Report False when asked for Audio HAL 2.1 support
  ON_CALL(mock_hal_2_1_verifier, Call()).WillByDefault([]() -> bool {
    return false;
  });

  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;

  EXPECT_DEATH(
      LeAudioClient::Initialize(
          &mock_audio_hal_client_callbacks_,
          base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                     &mock_storage_load),
          base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                     &mock_hal_2_1_verifier),
          framework_encode_preference),
      ", LE Audio Client requires Bluetooth Audio HAL V2.1 at least. Either "
      "disable LE Audio Profile, or update your HAL");
}

TEST_F(UnicastTest, ConnectOneEarbudEmpty) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEmpty(1, test_address0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoPacs) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      false,                               /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoAscs) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      0 /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoCas) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;
  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      false,                               /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt /*add_ascs*/);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoCsis) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false, /*add_csis*/
      true,                                 /*add_cas*/
      true,                                 /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectDisconnectOneEarbud) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(1, test_address0,
                                codec_spec_conf::kLeAudioLocationStereo,
                                codec_spec_conf::kLeAudioLocationStereo);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
  DisconnectLeAudio(test_address0, 1);
}

/* same as above case except the disconnect is initiated by remote */
TEST_F(UnicastTest, ConnectRemoteDisconnectOneEarbud) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(1, test_address0,
                                codec_spec_conf::kLeAudioLocationStereo,
                                codec_spec_conf::kLeAudioLocationStereo);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  /* For remote disconnection, expect stack to try background re-connect */
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, ConnectTwoEarbudsCsisGrouped) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, false))
      .Times(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, false))
      .Times(1);

  // Verify grouping information
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  DisconnectLeAudio(test_address0, 1);
  DisconnectLeAudio(test_address1, 2);
}

TEST_F(UnicastTest, ConnectTwoEarbudsCsisGroupUnknownAtConnect) {
  uint8_t group_size = 2;
  uint8_t group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud connects without known grouping
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  // Verify grouping information
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, false))
      .Times(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, false))
      .Times(1);
  DisconnectLeAudio(test_address0, 1);
  DisconnectLeAudio(test_address1, 2);
}

TEST_F(UnicastTestNoInit, LoadStoredEarbudsCsisGrouped) {
  // Prepare two devices
  uint8_t group_size = 2;
  uint8_t group_id = 2;

  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationFrontLeft,
      codec_spec_conf::kLeAudioLocationFrontLeft, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 1);

  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseEarbudsValid(
      2, test_address1, codec_spec_conf::kLeAudioLocationFrontRight,
      codec_spec_conf::kLeAudioLocationFrontRight, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 2);

  // Load devices from the storage when storage API is called
  bool autoconnect = true;

  /* Common storage values */
  std::vector<uint8_t> handles;
  LeAudioClient::GetHandlesForStorage(test_address0, handles);

  std::vector<uint8_t> ases;
  LeAudioClient::GetAsesForStorage(test_address0, ases);

  std::vector<uint8_t> src_pacs;
  LeAudioClient::GetSourcePacsForStorage(test_address0, src_pacs);

  std::vector<uint8_t> snk_pacs;
  LeAudioClient::GetSinkPacsForStorage(test_address0, snk_pacs);

  EXPECT_CALL(mock_storage_load, Call()).WillOnce([&]() {
    do_in_main_thread(
        FROM_HERE,
        base::Bind(&LeAudioClient::AddFromStorage, test_address0, autoconnect,
                   codec_spec_conf::kLeAudioLocationFrontLeft,
                   codec_spec_conf::kLeAudioLocationFrontLeft, 0xff, 0xff,
                   std::move(handles), std::move(snk_pacs), std::move(src_pacs),
                   std::move(ases)));
    do_in_main_thread(
        FROM_HERE,
        base::Bind(&LeAudioClient::AddFromStorage, test_address1, autoconnect,
                   codec_spec_conf::kLeAudioLocationFrontRight,
                   codec_spec_conf::kLeAudioLocationFrontRight, 0xff, 0xff,
                   std::move(handles), std::move(snk_pacs), std::move(src_pacs),
                   std::move(ases)));
  });

  // Expect stored device0 to connect automatically
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  // Expect stored device1 to connect automatically
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address1))
      .Times(1);
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address1, _))
      .WillByDefault(DoAll(Return(true)));
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  ON_CALL(mock_groups_module_, GetGroupId(_, _))
      .WillByDefault(DoAll(Return(group_id)));

  ON_CALL(mock_btm_interface_,
          GetSecurityFlagsByTransport(test_address0, NotNull(), _))
      .WillByDefault(
          DoAll(SetArgPointee<1>(BTM_SEC_FLAG_ENCRYPTED), Return(true)));

  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;

  // Initialize
  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  LeAudioClient::Initialize(
      &mock_audio_hal_client_callbacks_,
      base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                 &mock_storage_load),
      base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                 &mock_hal_2_1_verifier),
      framework_encode_preference);
  if (app_register_callback) app_register_callback.Run(gatt_if, GATT_SUCCESS);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  InjectConnectedEvent(test_address1, 2);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());

  // Verify if all went well and we got the proper group
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  DisconnectLeAudio(test_address0, 1);
  DisconnectLeAudio(test_address1, 2);
}

TEST_F(UnicastTestNoInit, LoadStoredEarbudsCsisGroupedDifferently) {
  // Prepare two devices
  uint8_t group_size = 1;

  // Device 0
  uint8_t group_id0 = 2;
  bool autoconnect0 = true;
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationFrontLeft,
      codec_spec_conf::kLeAudioLocationFrontLeft, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      true,                                /*add_ascs*/
      group_size, 1);

  ON_CALL(mock_groups_module_, GetGroupId(test_address0, _))
      .WillByDefault(DoAll(Return(group_id0)));

  // Device 1
  uint8_t group_id1 = 3;
  bool autoconnect1 = false;
  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseEarbudsValid(
      2, test_address1, codec_spec_conf::kLeAudioLocationFrontRight,
      codec_spec_conf::kLeAudioLocationFrontRight, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 2);

  ON_CALL(mock_groups_module_, GetGroupId(test_address1, _))
      .WillByDefault(DoAll(Return(group_id1)));

  /* Commont storage values */
  std::vector<uint8_t> handles;
  LeAudioClient::GetHandlesForStorage(test_address0, handles);

  std::vector<uint8_t> ases;
  LeAudioClient::GetAsesForStorage(test_address0, ases);

  std::vector<uint8_t> src_pacs;
  LeAudioClient::GetSourcePacsForStorage(test_address0, src_pacs);

  std::vector<uint8_t> snk_pacs;
  LeAudioClient::GetSinkPacsForStorage(test_address0, snk_pacs);

  // Load devices from the storage when storage API is called
  EXPECT_CALL(mock_storage_load, Call()).WillOnce([&]() {
    do_in_main_thread(
        FROM_HERE,
        base::Bind(&LeAudioClient::AddFromStorage, test_address0, autoconnect0,
                   codec_spec_conf::kLeAudioLocationFrontLeft,
                   codec_spec_conf::kLeAudioLocationFrontLeft, 0xff, 0xff,
                   std::move(handles), std::move(snk_pacs), std::move(src_pacs),
                   std::move(ases)));
    do_in_main_thread(
        FROM_HERE,
        base::Bind(&LeAudioClient::AddFromStorage, test_address1, autoconnect1,
                   codec_spec_conf::kLeAudioLocationFrontRight,
                   codec_spec_conf::kLeAudioLocationFrontRight, 0xff, 0xff,
                   std::move(handles), std::move(snk_pacs), std::move(src_pacs),
                   std::move(ases)));
  });

  // Expect stored device0 to connect automatically
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  // Expect stored device1 to NOT connect automatically
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address1))
      .Times(0);
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address1, _))
      .WillByDefault(DoAll(Return(true)));
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(0);

  // Initialize
  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;
  LeAudioClient::Initialize(
      &mock_audio_hal_client_callbacks_,
      base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                 &mock_storage_load),
      base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                 &mock_hal_2_1_verifier),
      framework_encode_preference);
  if (app_register_callback) app_register_callback.Run(gatt_if, GATT_SUCCESS);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());

  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_EQ(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  devs = LeAudioClient::Get()->GetGroupDevices(group_id1);
  ASSERT_EQ(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  DisconnectLeAudio(test_address0, 1);
}

TEST_F(UnicastTest, GroupingAddRemove) {
  // Earbud connects without known grouping
  uint8_t group_id0 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address0 = GetTestAddress(0);

  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectNonCsisDevice(test_address0, 1 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft);

  group_id0 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address0);

  // Earbud connects without known grouping
  uint8_t group_id1 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectNonCsisDevice(test_address1, 2 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight);

  group_id1 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  // Verify individual groups
  ASSERT_NE(group_id0, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id1, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id0, group_id1);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 1u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 1u);

  // Expectations on reassigning second earbud to the first group
  int dev1_storage_group = bluetooth::groups::kGroupUnknown;
  int dev1_new_group = bluetooth::groups::kGroupUnknown;

  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address1, group_id1, GroupNodeStatus::REMOVED))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address1, _, GroupNodeStatus::ADDED))
      .WillRepeatedly(SaveArg<1>(&dev1_new_group));
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address1, group_id1))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, _))
      .Times(AnyNumber());

  LeAudioClient::Get()->GroupRemoveNode(group_id1, test_address1);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_groups_module_);

  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, group_id0))
      .Times(1);

  LeAudioClient::Get()->GroupAddNode(group_id0, test_address1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);

  dev1_storage_group =
      MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);

  // Verify regrouping results
  EXPECT_EQ(dev1_new_group, group_id0);
  EXPECT_EQ(dev1_new_group, dev1_storage_group);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 0u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 2u);
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());
}

TEST_F(UnicastTest, RemoveNodeWhileStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  constexpr uint8_t cis_count_out = 1;
  constexpr uint8_t cis_count_in = 0;

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);

  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, {{gmcs_ccid}}))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address0, group_id))
      .Times(1);
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(1);
  EXPECT_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
      .Times(0);
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address0, group_id, GroupNodeStatus::REMOVED));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(0);

  LeAudioClient::Get()->GroupRemoveNode(group_id, test_address0);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, GroupingAddTwiceNoRemove) {
  // Earbud connects without known grouping
  uint8_t group_id0 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .WillOnce(Return())
      .RetiresOnSaturation();
  ConnectNonCsisDevice(test_address0, 1 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft);

  group_id0 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address0);

  // Earbud connects without known grouping
  uint8_t group_id1 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .WillOnce(Return())
      .RetiresOnSaturation();
  ConnectNonCsisDevice(test_address1, 2 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  group_id1 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);
  // Verify individual groups
  ASSERT_NE(group_id0, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id1, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id0, group_id1);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 1u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 1u);

  // Expectations on reassigning second earbud to the first group
  int dev1_storage_group = bluetooth::groups::kGroupUnknown;
  int dev1_new_group = bluetooth::groups::kGroupUnknown;

  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address1, group_id1, GroupNodeStatus::REMOVED))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address1, _, GroupNodeStatus::ADDED))
      .WillRepeatedly(SaveArg<1>(&dev1_new_group));

  // FIXME: We should expect removal with group_id context. No such API exists.
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address1, group_id1))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, _))
      .Times(AnyNumber());
  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, group_id0))
      .Times(1);

  // Regroup device: assign new group without removing it from the first one
  LeAudioClient::Get()->GroupAddNode(group_id0, test_address1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);

  dev1_storage_group =
      MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);

  // Verify regrouping results
  EXPECT_EQ(dev1_new_group, group_id0);
  EXPECT_EQ(dev1_new_group, dev1_storage_group);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 0u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 2u);
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());
}

TEST_F(UnicastTest, RemoveTwoEarbudsCsisGrouped) {
  uint8_t group_size = 2;
  int group_id0 = 2;
  int group_id1 = 3;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First group - First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id0, 1 /* rank*/);

  // First group - Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id0, 2 /* rank*/, true /*connect_through_csis*/);

  // Second group - First earbud
  const RawAddress test_address2 = GetTestAddress(2);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address2, true))
      .Times(1);
  ConnectCsisDevice(test_address2, 3 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id1, 1 /* rank*/);

  // Second group - Second earbud
  const RawAddress test_address3 = GetTestAddress(3);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address3, true))
      .Times(1);
  ConnectCsisDevice(test_address3, 4 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id1, 2 /* rank*/, true /*connect_through_csis*/);

  // First group - verify grouping information
  std::vector<RawAddress> group0_devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(group0_devs.begin(), group0_devs.end(), test_address0),
            group0_devs.end());
  ASSERT_NE(std::find(group0_devs.begin(), group0_devs.end(), test_address1),
            group0_devs.end());

  // Second group - verify grouping information
  std::vector<RawAddress> group1_devs =
      LeAudioClient::Get()->GetGroupDevices(group_id1);
  ASSERT_NE(std::find(group1_devs.begin(), group1_devs.end(), test_address2),
            group1_devs.end());
  ASSERT_NE(std::find(group1_devs.begin(), group1_devs.end(), test_address3),
            group1_devs.end());
  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  // Expect one of the groups to be dropped and devices to be disconnected
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address0, group_id0))
      .Times(1);
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address1, group_id0))
      .Times(1);
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address0, group_id0, GroupNodeStatus::REMOVED));
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address1, group_id0, GroupNodeStatus::REMOVED));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address1))
      .Times(1);

  // Expect the other groups to be left as is
  EXPECT_CALL(mock_audio_hal_client_callbacks_, OnGroupStatus(group_id1, _))
      .Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address2))
      .Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address3))
      .Times(0);

  do_in_main_thread(
      FROM_HERE, base::Bind(&LeAudioClient::GroupDestroy,
                            base::Unretained(LeAudioClient::Get()), group_id0));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_btif_storage_);
}

TEST_F(UnicastTest, RemoveWhileStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  constexpr uint8_t cis_count_out = 1;
  constexpr uint8_t cis_count_in = 0;

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);

  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, {{gmcs_ccid}}))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address0, group_id))
      .Times(1);

  LeAudioDeviceGroup* group = nullptr;
  EXPECT_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
      .WillOnce(DoAll(SaveArg<0>(&group)));
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address0, group_id, GroupNodeStatus::REMOVED));

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);

  LeAudioClient::Get()->RemoveDevice(test_address0);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  ASSERT_NE(group, nullptr);
}

TEST_F(UnicastTest, EarbudsTwsStyleStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, 0x01, 0x01, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, 2 /*add_ascs_cnt*/, 1 /*set_size*/, 0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Suspend
  /*TODO Need a way to verify STOP */
  LeAudioClient::Get()->GroupSuspend(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Resume
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, SpeakerFailedConversationalStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  supported_src_context_types_ = 0;
  supported_snk_context_types_ = 0x0004;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      0, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  LeAudioClient::Get()->GroupSetActive(group_id);

  /* Nothing to do - expect no crash */
}

TEST_F(UnicastTest, SpeakerStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Suspend
  /*TODO Need a way to verify STOP */
  LeAudioClient::Get()->GroupSuspend(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Resume
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, SpeakerStreamingAutonomousRelease) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, 1 /* cis_count_out */, 0 /* cis_count_in */,
                        1920);

  // Inject the IDLE state as if an autonomous release happened
  auto group = streaming_groups.at(group_id);
  ASSERT_NE(group, nullptr);
  for (LeAudioDevice* device = group->GetFirstDevice(); device != nullptr;
       device = group->GetNextDevice(device)) {
    for (auto& ase : device->ases_) {
      ase.data_path_state = types::AudioStreamDataPathState::IDLE;
      ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE;
      InjectCisDisconnected(group_id, ase.cis_conn_hdl);
    }
  }

  // Verify no Data transfer after the autonomous release
  TestAudioDataTransfer(group_id, 0 /* cis_count_out */, 0 /* cis_count_in */,
                        1920);
}

TEST_F(UnicastTest, TwoEarbudsStreaming) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // Suspend
  LeAudioClient::Get()->GroupSuspend(group_id);
  SyncOnMainLoop();

  // Resume
  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer still works
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // Stop
  StopStreaming(group_id, true);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, TwoEarbudsStreamingContextSwitchNoReconfigure) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Start streaming with new metadata, but use the existing configuration
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(
          _, types::LeAudioContextType::MEDIA,
          types::AudioContexts(types::LeAudioContextType::NOTIFICATIONS), _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_NOTIFICATION, AUDIO_CONTENT_TYPE_UNKNOWN,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Do a metadata content switch to ALERTS but stay on MEDIA configuration
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start).Times(0);
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(
          _, le_audio::types::LeAudioContextType::MEDIA,
          types::AudioContexts(le_audio::types::LeAudioContextType::ALERTS), _))
      .Times(1);
  UpdateMetadata(AUDIO_USAGE_ALARM, AUDIO_CONTENT_TYPE_UNKNOWN);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Do a metadata content switch to EMERGENCY but stay on MEDIA configuration
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start).Times(0);

  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA,
                  types::AudioContexts(
                      le_audio::types::LeAudioContextType::EMERGENCYALARM),
                  _))
      .Times(1);
  UpdateMetadata(AUDIO_USAGE_EMERGENCY, AUDIO_CONTENT_TYPE_UNKNOWN);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Do a metadata content switch to INSTRUCTIONAL but stay on MEDIA
  // configuration
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start).Times(0);
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA,
                  types::AudioContexts(
                      le_audio::types::LeAudioContextType::INSTRUCTIONAL),
                  _))
      .Times(1);
  UpdateMetadata(AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                 AUDIO_CONTENT_TYPE_UNKNOWN);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, TwoEarbudsStreamingContextSwitchReconfigure) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming MEDIA
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);

  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, {{gmcs_ccid}}))
      .Times(1);
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Stop
  StopStreaming(group_id);
  // simulate suspend timeout passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, {{gtbs_ccid}}))
      .Times(1);
  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  cis_count_out = 2;
  cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);
}

TEST_F(UnicastTest, TwoEarbuds2ndLateConnect) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect one iso channel to be fed with data
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Second earbud connects during stream
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  cis_count_out = 2;
  cis_count_in = 0;

  /* The above will trigger reconfiguration. After that Audio Hal action
   * is needed to restart the stream */
  SinkAudioResume();

  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, TwoEarbuds2ndDisconnected) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect two iso channels to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Disconnect one device and expect the group to keep on streaming
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);
  auto group = streaming_groups.at(group_id);
  auto device = group->GetFirstDevice();
  for (auto& ase : device->ases_) {
    InjectCisDisconnected(group_id, ase.cis_conn_hdl);
  }

  EXPECT_CALL(mock_gatt_interface_,
              Open(_, device->address_,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, false))
      .Times(1);

  auto conn_id = device->conn_id_;
  InjectDisconnectedEvent(device->conn_id_, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Expect one channel ISO Data to be sent
  cis_count_out = 1;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  InjectConnectedEvent(device->address_, conn_id);
  SyncOnMainLoop();

  // Expect two iso channels to be fed with data
  cis_count_out = 2;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, TwoEarbudsStreamingProfileDisconnect) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect two iso channels to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Disconnect one device and expect the group to keep on streaming
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(1);
  EXPECT_CALL(mock_gatt_interface_, Open(_, _, _, _)).Times(0);

  DisconnectLeAudio(test_address0, 1);
  DisconnectLeAudio(test_address1, 2);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, TwoEarbudsWithSourceSupporting32kHz) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = 0;
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0024,
      /* source sample freq 32/16khz */ true, /*add_csis*/
      true,                                   /*add_cas*/
      true,                                   /*add_pacs*/
      default_ase_cnt /*add_ascs_cnt*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);

  // LeAudioCodecConfiguration received_af_sink_config;
  const LeAudioCodecConfiguration expected_af_sink_config = {
      .num_channels = 2,
      .sample_rate = bluetooth::audio::le_audio::kSampleRate32000,
      .bits_per_sample = bluetooth::audio::le_audio::kBitsPerSample16,
      .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us,
  };

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_,
              Start(expected_af_sink_config, _))
      .Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, MicrophoneAttachToCurrentMediaScenario) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0024, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  EXPECT_CALL(mock_state_machine_,
              StartStream(_, le_audio::types::LeAudioContextType::LIVE, _, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id,
                 AUDIO_SOURCE_MIC);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Suspend
  /*TODO Need a way to verify STOP */
  LeAudioClient::Get()->GroupSuspend(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Resume
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id,
                 AUDIO_SOURCE_MIC);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, StartNotSupportedContextType) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  LeAudioClient::Get()->SetInCall(true);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  LeAudioClient::Get()->SetInCall(false);

  /* Fallback scenario now supports 48Khz just like Media so we will reconfigure
   * Note: Fallback is forced by the frequency on the remote device.
   */
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(1);
  UpdateMetadata(AUDIO_USAGE_GAME, AUDIO_CONTENT_TYPE_UNKNOWN, true);

  /* The above will trigger reconfiguration. After that Audio Hal action
   * is needed to restart the stream */
  SinkAudioResume();
}

TEST_F(UnicastTest, NotifyAboutGroupTunrnedIdleEnabled) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  osi_property_set_bool(kNotifyUpperLayerAboutGroupBeingInIdleDuringCall, true);

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  LeAudioClient::Get()->SetInCall(true);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Release

  /* To be called twice
   * 1. GroupStatus::INACTIVE
   * 2. GroupStatus::TURNED_IDLE_DURING_CALL
   */
  EXPECT_CALL(mock_audio_hal_client_callbacks_, OnGroupStatus(group_id, _))
      .Times(2);

  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);

  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  LeAudioClient::Get()->SetInCall(false);
  osi_property_set_bool(kNotifyUpperLayerAboutGroupBeingInIdleDuringCall,
                        false);
}

TEST_F(UnicastTest, NotifyAboutGroupTunrnedIdleDisabled) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  LeAudioClient::Get()->SetInCall(true);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Release

  /* To be called once only
   * 1. GroupStatus::INACTIVE
   */
  EXPECT_CALL(mock_audio_hal_client_callbacks_, OnGroupStatus(group_id, _))
      .Times(1);

  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  LeAudioClient::Get()->SetInCall(false);
}

TEST_F(UnicastTest, HandleDatabaseOutOfSync) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // default action for WriteDescriptor function call
  ON_CALL(mock_gatt_queue_, WriteDescriptor(_, _, _, _, _, _))
      .WillByDefault(Invoke([](uint16_t conn_id, uint16_t handle,
                               std::vector<uint8_t> value,
                               tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                               void* cb_data) -> void {
        if (cb)
          do_in_main_thread(
              FROM_HERE,
              base::BindOnce(
                  [](GATT_WRITE_OP_CB cb, uint16_t conn_id, uint16_t handle,
                     uint16_t len, uint8_t* value, void* cb_data) {
                    cb(conn_id, GATT_DATABASE_OUT_OF_SYNC, handle, len, value,
                       cb_data);
                  },
                  cb, conn_id, handle, value.size(), value.data(), cb_data));
      }));

  ON_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _))
      .WillByDefault(Return());
  EXPECT_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _));

  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

}  // namespace le_audio
