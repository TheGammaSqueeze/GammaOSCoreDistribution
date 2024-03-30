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

#pragma once
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>

#include <algorithm>
#include <map>
#include <vector>

#include "bta_csis_api.h"
#include "bta_gatt_api.h"
#include "bta_groups.h"
#include "gap_api.h"
#include "gd/common/init_flags.h"
#include "gd/common/strings.h"
#include "stack/crypto_toolbox/crypto_toolbox.h"

namespace bluetooth {
namespace csis {

using bluetooth::csis::CsisLockCb;

// CSIP additions
/* Generic UUID is used when CSIS is not included in any context */
static const bluetooth::Uuid kCsisServiceUuid = bluetooth::Uuid::From16Bit(0x1846);
static const bluetooth::Uuid kCsisSirkUuid = bluetooth::Uuid::From16Bit(0x2B84);
static const bluetooth::Uuid kCsisSizeUuid = bluetooth::Uuid::From16Bit(0x2B85);
static const bluetooth::Uuid kCsisLockUuid = bluetooth::Uuid::From16Bit(0x2B86);
static const bluetooth::Uuid kCsisRankUuid = bluetooth::Uuid::From16Bit(0x2B87);

static constexpr uint8_t kCsisErrorCodeLockDenied = 0x80;
static constexpr uint8_t kCsisErrorCodeReleaseNotAllowed = 0x81;
static constexpr uint8_t kCsisErrorCodeInvalidValue = 0x82;
static constexpr uint8_t kCsisErrorCodeLockAccessSirkRejected = 0x83;
static constexpr uint8_t kCsisErrorCodeLockOobSirkOnly = 0x84;
static constexpr uint8_t kCsisErrorCodeLockAlreadyGranted = 0x85;

static constexpr uint8_t kCsisSirkTypeEncrypted = 0x00;
static constexpr uint8_t kCsisSirkCharLen = 17;

struct hdl_pair {
  hdl_pair() {}
  hdl_pair(uint16_t val_hdl, uint16_t ccc_hdl) : val_hdl(val_hdl), ccc_hdl(ccc_hdl) {}

  uint16_t val_hdl;
  uint16_t ccc_hdl;
};

/* CSIS Types */
static constexpr uint8_t kDefaultScanDurationS = 5;
static constexpr uint8_t kDefaultCsisSetSize = 1;
static constexpr uint8_t kUnknownRank = 0xff;

/* Enums */
enum class CsisLockState : uint8_t {
  CSIS_STATE_UNSET = 0x00,
  CSIS_STATE_UNLOCKED,
  CSIS_STATE_LOCKED
};

enum class CsisDiscoveryState : uint8_t {
  CSIS_DISCOVERY_IDLE = 0x00,
  CSIS_DISCOVERY_ONGOING,
  CSIS_DISCOVERY_COMPLETED,
};

class GattServiceDevice {
 public:
  RawAddress addr;
  /*
   * This is true only during first connection to profile, until we store the
   * device.
   */
  bool first_connection;

  /*
   * We are making active attempt to connect to this device, 'direct connect'.
   */
  bool connecting_actively = false;

  uint16_t conn_id = GATT_INVALID_CONN_ID;
  uint16_t service_handle = GAP_INVALID_HANDLE;
  bool is_gatt_service_valid = false;

  GattServiceDevice(const RawAddress& addr, bool first_connection)
      : addr(addr), first_connection(first_connection) {}

  GattServiceDevice() : GattServiceDevice(RawAddress::kEmpty, false) {}

  bool IsConnected() const { return (conn_id != GATT_INVALID_CONN_ID); }

  class MatchAddress {
   private:
    RawAddress addr;

   public:
    MatchAddress(const RawAddress& addr) : addr(addr) {}
    bool operator()(const std::shared_ptr<GattServiceDevice>& other) const {
      return (addr == other->addr);
    }
  };

  class MatchConnId {
   private:
    uint16_t conn_id;

   public:
    MatchConnId(uint16_t conn_id) : conn_id(conn_id) {}
    bool operator()(const std::shared_ptr<GattServiceDevice>& other) const {
      return (conn_id == other->conn_id);
    }
  };
};

/*
 * CSIS instance represents single CSIS service on the remote device
 * along with the handle in database and specific data to control CSIS like:
 * rank, lock state.
 *
 * It also inclues UUID of the primary service which includes that CSIS
 * instance. If this is 0x0000 it means CSIS is per device and not for specific
 * service.
 */
class CsisInstance {
 public:
  bluetooth::Uuid coordinated_service = bluetooth::groups::kGenericContextUuid;

  struct SvcData {
    uint16_t start_handle;
    uint16_t end_handle;
    struct hdl_pair sirk_handle;
    struct hdl_pair lock_handle;
    uint16_t rank_handle;
    struct hdl_pair size_handle;
  } svc_data = {
      GAP_INVALID_HANDLE,
      GAP_INVALID_HANDLE,
      {GAP_INVALID_HANDLE, GAP_INVALID_HANDLE},
      {GAP_INVALID_HANDLE, GAP_INVALID_HANDLE},
      GAP_INVALID_HANDLE,
      {GAP_INVALID_HANDLE, GAP_INVALID_HANDLE},
  };

  CsisInstance(uint16_t start_handle, uint16_t end_handle, const bluetooth::Uuid& uuid)
      : coordinated_service(uuid),
        group_id_(bluetooth::groups::kGroupUnknown),
        rank_(kUnknownRank),
        lock_state_(CsisLockState::CSIS_STATE_UNSET) {
    svc_data.start_handle = start_handle;
    svc_data.end_handle = end_handle;
  }

  void SetLockState(CsisLockState state) {
    DLOG(INFO) << __func__ << " current lock state: " << (int)(lock_state_)
               << " new lock state: " << (int)(state);
    lock_state_ = state;
  }
  CsisLockState GetLockState(void) const { return lock_state_; }
  uint8_t GetRank(void) const { return rank_; }
  void SetRank(uint8_t rank) {
    DLOG(INFO) << __func__ << " current rank state: " << loghex(rank_)
               << " new rank state: " << loghex(rank);
    rank_ = rank;
  }

  void SetGroupId(int group_id) {
    LOG(INFO) << __func__ << " set group id: " << group_id
              << " instance handle: " << loghex(svc_data.start_handle);
    group_id_ = group_id;
  }

  int GetGroupId(void) const { return group_id_; }

  bool HasSameUuid(const CsisInstance& csis_instance) const {
    return (csis_instance.coordinated_service == coordinated_service);
  }

  const bluetooth::Uuid& GetUuid(void) const { return coordinated_service; }
  bool IsForUuid(const bluetooth::Uuid& uuid) const { return coordinated_service == uuid; }

 private:
  int group_id_;
  uint8_t rank_;
  CsisLockState lock_state_;
};

/*
 * Csis Device represents remote device and its all CSIS instances.
 * It can happen that device can have more than one CSIS service instance
 * if those instances are included in other services. In this way, coordinated
 * set is within the context of the primary service which includes the instance.
 *
 * CsisDevice contains vector of the instances.
 */
class CsisDevice : public GattServiceDevice {
 public:
  using GattServiceDevice::GattServiceDevice;

  void ClearSvcData() {
    GattServiceDevice::service_handle = GAP_INVALID_HANDLE;
    GattServiceDevice::is_gatt_service_valid = false;

    csis_instances_.clear();
  }

  std::shared_ptr<CsisInstance> GetCsisInstanceByOwningHandle(uint16_t handle) {
    uint16_t hdl = 0;
    for (const auto& [h, inst] : csis_instances_) {
      if (handle >= inst->svc_data.start_handle && handle <= inst->svc_data.end_handle) {
        hdl = h;
        DLOG(INFO) << __func__ << " found " << loghex(hdl);
        break;
      }
    }
    return (hdl > 0) ? csis_instances_.at(hdl) : nullptr;
  }

  std::shared_ptr<CsisInstance> GetCsisInstanceByGroupId(int group_id) {
    uint16_t hdl = 0;
    for (const auto& [handle, inst] : csis_instances_) {
      if (inst->GetGroupId() == group_id) {
        hdl = handle;
        break;
      }
    }
    return (hdl > 0) ? csis_instances_.at(hdl) : nullptr;
  }

  void SetCsisInstance(uint16_t handle, std::shared_ptr<CsisInstance> csis_instance) {
    if (csis_instances_.count(handle)) {
      DLOG(INFO) << __func__ << " instance is already here: " << csis_instance->GetUuid();
      return;
    }

    csis_instances_.insert({handle, csis_instance});
    DLOG(INFO) << __func__ << " instance added: " << loghex(handle) << "device: " << addr;
  }

  void RemoveCsisInstance(int group_id) {
    for (auto it = csis_instances_.begin(); it != csis_instances_.end(); it++) {
      if (it->second->GetGroupId() == group_id) {
        csis_instances_.erase(it);
        return;
      }
    }
  }

  int GetNumberOfCsisInstances(void) { return csis_instances_.size(); }

  void ForEachCsisInstance(std::function<void(const std::shared_ptr<CsisInstance>&)> cb) {
    for (auto const& kv_pair : csis_instances_) {
      cb(kv_pair.second);
    }
  }

 private:
  /* Instances per start handle  */
  std::map<uint16_t, std::shared_ptr<CsisInstance>> csis_instances_;
};

/*
 * CSIS group gathers devices which belongs to specific group.
 * It also contains methond to decode encrypted SIRK and also to
 * resolve PRSI in order to find out if device belongs to given group
 */
class CsisGroup {
 public:
  CsisGroup(int group_id, const bluetooth::Uuid& uuid)
      : group_id_(group_id),
        size_(kDefaultCsisSetSize),
        uuid_(uuid),
        member_discovery_state_(CsisDiscoveryState::CSIS_DISCOVERY_IDLE),
        lock_state_(CsisLockState::CSIS_STATE_UNSET),
        target_lock_state_(CsisLockState::CSIS_STATE_UNSET),
        lock_transition_cnt_(0) {
    devices_.clear();
  }

  void AddDevice(std::shared_ptr<CsisDevice> csis_device) {
    auto it =
        find_if(devices_.begin(), devices_.end(), CsisDevice::MatchAddress(csis_device->addr));
    if (it != devices_.end()) return;

    devices_.push_back(std::move(csis_device));
  }

  void RemoveDevice(const RawAddress& bd_addr) {
    auto it = find_if(devices_.begin(), devices_.end(), CsisDevice::MatchAddress(bd_addr));
    if (it != devices_.end()) devices_.erase(it);
  }

  int GetCurrentSize(void) const { return devices_.size(); }
  bluetooth::Uuid GetUuid() const { return uuid_; }
  void SetUuid(const bluetooth::Uuid& uuid) { uuid_ = uuid; }
  int GetGroupId(void) const { return group_id_; }
  int GetDesiredSize(void) const { return size_; }
  void SetDesiredSize(int size) { size_ = size; }
  bool IsGroupComplete(void) const { return size_ == (int)devices_.size(); }
  bool IsEmpty(void) const { return devices_.empty(); }

  bool IsDeviceInTheGroup(std::shared_ptr<CsisDevice>& csis_device) {
    auto it =
        find_if(devices_.begin(), devices_.end(), CsisDevice::MatchAddress(csis_device->addr));
    return (it != devices_.end());
  }
  bool IsRsiMatching(const RawAddress& rsi) const { return is_rsi_match_sirk(rsi, GetSirk()); }
  bool IsSirkBelongsToGroup(Octet16 sirk) const { return (sirk_available_ && sirk_ == sirk); }
  Octet16 GetSirk(void) const { return sirk_; }
  void SetSirk(Octet16& sirk) {
    if (sirk_available_) {
      DLOG(INFO) << __func__ << " Updating SIRK";
    }
    sirk_available_ = true;
    sirk_ = sirk;
  }

  int GetNumOfConnectedDevices(void) {
    return std::count_if(devices_.begin(), devices_.end(),
                         [](auto& d) { return d->IsConnected(); });
  }

  CsisDiscoveryState GetDiscoveryState(void) const { return member_discovery_state_; }
  void SetDiscoveryState(CsisDiscoveryState state) {
    DLOG(INFO) << __func__ << " current discovery state: " << (int)(member_discovery_state_)
               << " new discovery state: " << (int)(state);
    member_discovery_state_ = state;
  }

  void SetCurrentLockState(CsisLockState state) { lock_state_ = state; }

  void SetTargetLockState(CsisLockState state, CsisLockCb cb = base::DoNothing()) {
    target_lock_state_ = state;
    cb_ = std::move(cb);
    switch (state) {
      case CsisLockState::CSIS_STATE_LOCKED:
        lock_transition_cnt_ = GetNumOfConnectedDevices();
        break;
      case CsisLockState::CSIS_STATE_UNLOCKED:
      case CsisLockState::CSIS_STATE_UNSET:
        lock_transition_cnt_ = 0;
        break;
    }
  }

  CsisLockCb GetLockCb(void) { return std::move(cb_); }

  CsisLockState GetCurrentLockState(void) const { return lock_state_; }
  CsisLockState GetTargetLockState(void) const { return target_lock_state_; }

  bool IsAvailableForCsisLockOperation(void) {
    int id = group_id_;
    int number_of_connected = 0;
    auto iter = std::find_if(
        devices_.begin(), devices_.end(), [id, &number_of_connected](auto& d) {
          if (!d->IsConnected()) {
            LOG_DEBUG("Device %s is not connected in group %d",
                      d->addr.ToString().c_str(), id);
            return false;
          }
          auto inst = d->GetCsisInstanceByGroupId(id);
          if (!inst) {
            LOG_DEBUG("Instance not available for group %d", id);
            return false;
          }
          number_of_connected++;
          LOG_DEBUG("Device %s,  lock state: %d", d->addr.ToString().c_str(),
                    (int)inst->GetLockState());
          return inst->GetLockState() == CsisLockState::CSIS_STATE_LOCKED;
        });

    LOG_DEBUG("Locked set: %d, number of connected %d", iter != devices_.end(),
              number_of_connected);
    /* If there is no locked device, we are good to go */
    if (iter != devices_.end()) {
      LOG_WARN("Device %s is locked ", (*iter)->addr.ToString().c_str());
      return false;
    }

    return (number_of_connected > 0);
  }

  void SortByCsisRank(void) {
    int id = group_id_;
    std::sort(devices_.begin(), devices_.end(), [id](auto& dev1, auto& dev2) {
      auto inst1 = dev1->GetCsisInstanceByGroupId(id);
      auto inst2 = dev2->GetCsisInstanceByGroupId(id);
      if (!inst1 || !inst2) {
        /* One of the device is not connected */
        DLOG(INFO) << __func__ << " one of the device is not connected: inst1: " << inst1
                   << " inst2: " << inst2;
        return dev1->IsConnected();
      }
      return (inst1->GetRank() < inst2->GetRank());
    });
  }

  std::shared_ptr<CsisDevice> GetFirstDevice(void) { return (devices_.front()); }
  std::shared_ptr<CsisDevice> GetLastDevice(void) { return (devices_.back()); }
  std::shared_ptr<CsisDevice> GetNextDevice(std::shared_ptr<CsisDevice>& device) {
    auto iter =
        std::find_if(devices_.begin(), devices_.end(), CsisDevice::MatchAddress(device->addr));

    /* If reference device not found */
    if (iter == devices_.end()) return nullptr;

    iter++;
    /* If reference device is last in group */
    if (iter == devices_.end()) return nullptr;

    return (*iter);
  }
  std::shared_ptr<CsisDevice> GetPrevDevice(std::shared_ptr<CsisDevice>& device) {
    auto iter =
        std::find_if(devices_.rbegin(), devices_.rend(), CsisDevice::MatchAddress(device->addr));

    /* If reference device not found */
    if (iter == devices_.rend()) return nullptr;

    iter++;

    if (iter == devices_.rend()) return nullptr;
    return (*iter);
  }

  int GetLockTransitionCnt(void) const { return lock_transition_cnt_; }
  int UpdateLockTransitionCnt(int i) {
    lock_transition_cnt_ += i;
    return lock_transition_cnt_;
  }

  /* Return true if given Autoset Private Address |srpa| matches Set Identity
   * Resolving Key |sirk| */
  static bool is_rsi_match_sirk(const RawAddress& rsi, const Octet16& sirk) {
    /* use the 3 MSB of bd address as prand */
    uint8_t rand[3];
    rand[0] = rsi.address[2];
    rand[1] = rsi.address[1];
    rand[2] = rsi.address[0];
    DLOG(INFO) << "Prand " << base::HexEncode(rand, 3);

    DLOG(INFO) << "SIRK " << base::HexEncode(sirk.data(), 16);
    /* generate X = E irk(R0, R1, R2) and R is random address 3 LSO */
    Octet16 x = crypto_toolbox::aes_128(sirk, &rand[0], 3);

    DLOG(INFO) << "X" << base::HexEncode(x.data(), 16);

    rand[0] = rsi.address[5];
    rand[1] = rsi.address[4];
    rand[2] = rsi.address[3];

    DLOG(INFO) << "Hash " << base::HexEncode(rand, 3);

    if (memcmp(x.data(), &rand[0], 3) == 0) {
      // match
      return true;
    }
    // not a match
    return false;
  }

 private:
  int group_id_;
  Octet16 sirk_ = {0};
  bool sirk_available_ = false;
  int size_;
  bluetooth::Uuid uuid_;

  std::vector<std::shared_ptr<CsisDevice>> devices_;
  CsisDiscoveryState member_discovery_state_;

  CsisLockState lock_state_;
  CsisLockState target_lock_state_;
  int lock_transition_cnt_;

  CsisLockCb cb_;
};

}  // namespace csis
}  // namespace bluetooth
