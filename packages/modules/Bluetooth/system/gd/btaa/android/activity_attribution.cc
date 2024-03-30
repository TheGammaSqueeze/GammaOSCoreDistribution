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

#define LOG_TAG "btaa"

#include "btaa/activity_attribution.h"
#include "activity_attribution_generated.h"

#include <aidl/android/system/suspend/BnSuspendCallback.h>
#include <aidl/android/system/suspend/BnWakelockCallback.h>
#include <aidl/android/system/suspend/ISuspendControlService.h>
#include <android/binder_manager.h>

#include "btaa/attribution_processor.h"
#include "btaa/hci_processor.h"
#include "btaa/wakelock_processor.h"
#include "module.h"
#include "os/log.h"

using aidl::android::system::suspend::BnSuspendCallback;
using aidl::android::system::suspend::BnWakelockCallback;
using aidl::android::system::suspend::ISuspendCallback;
using aidl::android::system::suspend::ISuspendControlService;
using Status = ::ndk::ScopedAStatus;
using namespace ndk;

namespace bluetooth {
namespace activity_attribution {

const ModuleFactory ActivityAttribution::Factory = ModuleFactory([]() { return new ActivityAttribution(); });

static const std::string kBtWakelockName("hal_bluetooth_lock");
static const std::string kBtWakeupReason("hs_uart_wakeup");
static const size_t kHciAclHeaderSize = 4;

static std::mutex g_module_mutex;
static ActivityAttribution* g_module = nullptr;
static bool is_wakeup_callback_registered = false;
static bool is_wakelock_callback_registered = false;

struct wakelock_callback : public BnWakelockCallback {
  wakelock_callback() {}

  Status notifyAcquired() override {
    std::lock_guard<std::mutex> guard(g_module_mutex);
    if (g_module != nullptr) {
      g_module->OnWakelockAcquired();
    }
    return Status::ok();
  }
  Status notifyReleased() override {
    std::lock_guard<std::mutex> guard(g_module_mutex);
    if (g_module != nullptr) {
      g_module->OnWakelockReleased();
    }
    return Status::ok();
  }
};

static std::shared_ptr<wakelock_callback> g_wakelock_callback = nullptr;

struct wakeup_callback : public BnSuspendCallback {
  wakeup_callback() {}

  Status notifyWakeup(bool success, const std::vector<std::string>& wakeup_reasons) override {
    for (auto& wakeup_reason : wakeup_reasons) {
      if (wakeup_reason.find(kBtWakeupReason) != std::string::npos) {
        std::lock_guard<std::mutex> guard(g_module_mutex);
        if (g_module != nullptr) {
          g_module->OnWakeup();
        }
        break;
      }
    }
    return Status::ok();
  }
};

static std::shared_ptr<wakeup_callback> g_wakeup_callback = nullptr;

struct ActivityAttribution::impl {
  impl(ActivityAttribution* module) {
    std::lock_guard<std::mutex> guard(g_module_mutex);
    g_module = module;
    if (is_wakeup_callback_registered && is_wakelock_callback_registered) {
      LOG_ERROR("Wakeup and wakelock callbacks are already registered");
      return;
    }

    Status register_callback_status;
    bool is_register_successful = false;
    auto control_service =
        ISuspendControlService::fromBinder(SpAIBinder(AServiceManager_getService("suspend_control")));
    if (!control_service) {
      LOG_ERROR("Fail to obtain suspend_control");
      return;
    }

    if (!is_wakeup_callback_registered) {
      g_wakeup_callback = SharedRefBase::make<wakeup_callback>();
      register_callback_status = control_service->registerCallback(g_wakeup_callback, &is_register_successful);
      if (!is_register_successful || !register_callback_status.isOk()) {
        LOG_ERROR("Fail to register wakeup callback");
        return;
      }
      is_wakeup_callback_registered = true;
    }

    if (!is_wakelock_callback_registered) {
      g_wakelock_callback = SharedRefBase::make<wakelock_callback>();
      register_callback_status =
          control_service->registerWakelockCallback(g_wakelock_callback, kBtWakelockName, &is_register_successful);
      if (!is_register_successful || !register_callback_status.isOk()) {
        LOG_ERROR("Fail to register wakelock callback");
        return;
      }
      is_wakelock_callback_registered = true;
    }
  }

  ~impl() {
    std::lock_guard<std::mutex> guard(g_module_mutex);
    g_module = nullptr;
  }

  void on_hci_packet(hal::HciPacket packet, hal::SnoopLogger::PacketType type, uint16_t length) {
    attribution_processor_.OnBtaaPackets(std::move(hci_processor_.OnHciPacket(std::move(packet), type, length)));
  }

  void on_wakelock_acquired() {
    wakelock_processor_.OnWakelockAcquired();
  }

  void on_wakelock_released() {
    uint32_t wakelock_duration_ms = 0;

    wakelock_duration_ms = wakelock_processor_.OnWakelockReleased();
    if (wakelock_duration_ms != 0) {
      attribution_processor_.OnWakelockReleased(wakelock_duration_ms);
    }
  }

  void on_wakeup() {
    attribution_processor_.OnWakeup();
  }

  void register_callback(ActivityAttributionCallback* callback) {
    callback_ = callback;
  }

  void notify_activity_attribution_info(int uid, const std::string& package_name, const std::string& device_address) {
    attribution_processor_.NotifyActivityAttributionInfo(uid, package_name, device_address);
  }

  void Dump(
      std::promise<flatbuffers::Offset<ActivityAttributionData>> promise, flatbuffers::FlatBufferBuilder* fb_builder) {
    attribution_processor_.Dump(std::move(promise), fb_builder);
  }

  ActivityAttributionCallback* callback_;
  AttributionProcessor attribution_processor_;
  HciProcessor hci_processor_;
  WakelockProcessor wakelock_processor_;
};

void ActivityAttribution::Capture(const hal::HciPacket& packet, hal::SnoopLogger::PacketType type) {
  uint16_t original_length = packet.size();
  uint16_t truncate_length;

  switch (type) {
    case hal::SnoopLogger::PacketType::CMD:
    case hal::SnoopLogger::PacketType::EVT:
      truncate_length = packet.size();
      break;
    case hal::SnoopLogger::PacketType::ACL:
    case hal::SnoopLogger::PacketType::SCO:
    case hal::SnoopLogger::PacketType::ISO:
      truncate_length = kHciAclHeaderSize;
      break;
  }

  if (!truncate_length) {
    return;
  }

  hal::HciPacket truncate_packet(packet.begin(), packet.begin() + truncate_length);
  CallOn(pimpl_.get(), &impl::on_hci_packet, truncate_packet, type, original_length);
}

void ActivityAttribution::OnWakelockAcquired() {
  CallOn(pimpl_.get(), &impl::on_wakelock_acquired);
}

void ActivityAttribution::OnWakelockReleased() {
  CallOn(pimpl_.get(), &impl::on_wakelock_released);
}

void ActivityAttribution::OnWakeup() {
  CallOn(pimpl_.get(), &impl::on_wakeup);
}

void ActivityAttribution::RegisterActivityAttributionCallback(ActivityAttributionCallback* callback) {
  CallOn(pimpl_.get(), &impl::register_callback, callback);
}

void ActivityAttribution::NotifyActivityAttributionInfo(
    int uid, const std::string& package_name, const std::string& device_address) {
  CallOn(pimpl_.get(), &impl::notify_activity_attribution_info, uid, package_name, device_address);
}

std::string ActivityAttribution::ToString() const {
  return "Btaa Module";
}

void ActivityAttribution::ListDependencies(ModuleList* list) const {}

void ActivityAttribution::Start() {
  pimpl_ = std::make_unique<impl>(this);
}

void ActivityAttribution::Stop() {
  pimpl_.reset();
}

DumpsysDataFinisher ActivityAttribution::GetDumpsysData(flatbuffers::FlatBufferBuilder* fb_builder) const {
  ASSERT(fb_builder != nullptr);

  std::promise<flatbuffers::Offset<ActivityAttributionData>> promise;
  auto future = promise.get_future();
  pimpl_->Dump(std::move(promise), fb_builder);

  auto dumpsys_data = future.get();

  return [dumpsys_data](DumpsysDataBuilder* dumpsys_builder) {
    dumpsys_builder->add_activity_attribution_dumpsys_data(dumpsys_data);
  };
}

}  // namespace activity_attribution
}  // namespace bluetooth
