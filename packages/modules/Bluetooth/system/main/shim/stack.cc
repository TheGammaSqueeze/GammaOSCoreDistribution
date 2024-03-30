/*
 * Copyright 2019 The Android Open Source Project
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

#define LOG_TAG "bt_gd_shim"

#include "device/include/controller.h"

#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <string>

#include "gd/att/att_module.h"
#include "gd/btaa/activity_attribution.h"
#include "gd/common/init_flags.h"
#include "gd/common/strings.h"
#include "gd/hal/hci_hal.h"
#include "gd/hci/acl_manager.h"
#include "gd/hci/controller.h"
#include "gd/hci/hci_layer.h"
#include "gd/hci/le_advertising_manager.h"
#include "gd/hci/le_scanning_manager.h"
#include "gd/hci/vendor_specific_event_manager.h"
#include "gd/l2cap/classic/l2cap_classic_module.h"
#include "gd/l2cap/le/l2cap_le_module.h"
#include "gd/metrics/counter_metrics.h"
#include "gd/neighbor/connectability.h"
#include "gd/neighbor/discoverability.h"
#include "gd/neighbor/inquiry.h"
#include "gd/neighbor/name.h"
#include "gd/neighbor/name_db.h"
#include "gd/neighbor/page.h"
#include "gd/neighbor/scan.h"
#include "gd/os/log.h"
#include "gd/security/security_module.h"
#include "gd/shim/dumpsys.h"
#include "gd/storage/storage_module.h"

#include "main/shim/acl_legacy_interface.h"
#include "main/shim/activity_attribution.h"
#include "main/shim/hci_layer.h"
#include "main/shim/helpers.h"
#include "main/shim/l2c_api.h"
#include "main/shim/le_advertising_manager.h"
#include "main/shim/le_scanning_manager.h"
#include "main/shim/shim.h"
#include "main/shim/stack.h"

namespace bluetooth {
namespace shim {

using ::bluetooth::common::InitFlags;
using ::bluetooth::common::StringFormat;

namespace {
// PID file format
constexpr char pid_file_format[] = "/var/run/bluetooth/bluetooth%d.pid";

void CreatePidFile() {
  std::string pid_file =
      StringFormat(pid_file_format, InitFlags::GetAdapterIndex());
  int pid_fd_ = open(pid_file.c_str(), O_WRONLY | O_CREAT, 0644);
  if (!pid_fd_) return;

  pid_t my_pid = getpid();
  dprintf(pid_fd_, "%d\n", my_pid);
  close(pid_fd_);

  LOG_INFO("%s - Created pid file %s", __func__, pid_file.c_str());
}

void RemovePidFile() {
  std::string pid_file =
      StringFormat(pid_file_format, InitFlags::GetAdapterIndex());
  unlink(pid_file.c_str());
  LOG_INFO("%s - Deleted pid file %s", __func__, pid_file.c_str());
}
}  // namespace

Stack* Stack::GetInstance() {
  static Stack instance;
  return &instance;
}

void Stack::StartIdleMode() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT_LOG(!is_running_, "%s Gd stack already running", __func__);
  LOG_INFO("%s Starting Gd stack", __func__);
  ModuleList modules;
  modules.add<metrics::CounterMetrics>();
  modules.add<storage::StorageModule>();
  Start(&modules);
  // Make sure the leaf modules are started
  ASSERT(stack_manager_.GetInstance<storage::StorageModule>() != nullptr);
  is_running_ = true;
}

void Stack::StartEverything() {
  if (common::init_flags::gd_rust_is_enabled()) {
    if (rust_stack_ == nullptr) {
      rust_stack_ = new ::rust::Box<rust::Stack>(rust::stack_create());
    }
    rust::stack_start(**rust_stack_);

    rust_hci_ = new ::rust::Box<rust::Hci>(rust::get_hci(**rust_stack_));
    rust_controller_ =
        new ::rust::Box<rust::Controller>(rust::get_controller(**rust_stack_));
    bluetooth::shim::hci_on_reset_complete();

    // Create pid since we're up and running
    CreatePidFile();

    // Create the acl shim layer
    acl_ = new legacy::Acl(
        stack_handler_, legacy::GetAclInterface(),
        controller_get_interface()->get_ble_acceptlist_size(),
        controller_get_interface()->get_ble_resolving_list_max_size());
    return;
  }

  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT_LOG(!is_running_, "%s Gd stack already running", __func__);
  LOG_INFO("%s Starting Gd stack", __func__);
  ModuleList modules;

  modules.add<metrics::CounterMetrics>();
  modules.add<hal::HciHal>();
  modules.add<hci::HciLayer>();
  modules.add<storage::StorageModule>();
  modules.add<shim::Dumpsys>();
  modules.add<hci::VendorSpecificEventManager>();

  modules.add<hci::Controller>();
  modules.add<hci::AclManager>();
  if (common::init_flags::gd_l2cap_is_enabled()) {
    modules.add<l2cap::classic::L2capClassicModule>();
    modules.add<l2cap::le::L2capLeModule>();
    modules.add<hci::LeAdvertisingManager>();
  }
  if (common::init_flags::gd_security_is_enabled()) {
    modules.add<security::SecurityModule>();
  }
  modules.add<hci::LeAdvertisingManager>();
  modules.add<hci::LeScanningManager>();
  if (common::init_flags::btaa_hci_is_enabled()) {
    modules.add<activity_attribution::ActivityAttribution>();
  }
  if (common::init_flags::gd_core_is_enabled()) {
    modules.add<att::AttModule>();
    modules.add<neighbor::ConnectabilityModule>();
    modules.add<neighbor::DiscoverabilityModule>();
    modules.add<neighbor::InquiryModule>();
    modules.add<neighbor::NameModule>();
    modules.add<neighbor::NameDbModule>();
    modules.add<neighbor::PageModule>();
    modules.add<neighbor::ScanModule>();
    modules.add<storage::StorageModule>();
  }
  Start(&modules);
  is_running_ = true;
  // Make sure the leaf modules are started
  ASSERT(stack_manager_.GetInstance<storage::StorageModule>() != nullptr);
  ASSERT(stack_manager_.GetInstance<shim::Dumpsys>() != nullptr);
  if (common::init_flags::gd_core_is_enabled()) {
    btm_ = new Btm(stack_handler_,
                   stack_manager_.GetInstance<neighbor::InquiryModule>());
  }
  if (!common::init_flags::gd_core_is_enabled()) {
    if (stack_manager_.IsStarted<hci::Controller>()) {
      acl_ = new legacy::Acl(
          stack_handler_, legacy::GetAclInterface(),
          controller_get_interface()->get_ble_acceptlist_size(),
          controller_get_interface()->get_ble_resolving_list_max_size());
    } else {
      LOG_ERROR(
          "Unable to create shim ACL layer as Controller has not started");
    }
  }

  if (!common::init_flags::gd_core_is_enabled()) {
    bluetooth::shim::hci_on_reset_complete();
  }

  bluetooth::shim::init_advertising_manager();
  bluetooth::shim::init_scanning_manager();

  if (common::init_flags::gd_l2cap_is_enabled() &&
      !common::init_flags::gd_core_is_enabled()) {
    L2CA_UseLegacySecurityModule();
  }
  if (common::init_flags::btaa_hci_is_enabled()) {
    bluetooth::shim::init_activity_attribution();
  }

  // Create pid since we're up and running
  CreatePidFile();
}

void Stack::Start(ModuleList* modules) {
  ASSERT_LOG(!is_running_, "%s Gd stack already running", __func__);
  LOG_INFO("%s Starting Gd stack", __func__);

  stack_thread_ =
      new os::Thread("gd_stack_thread", os::Thread::Priority::REAL_TIME);
  stack_manager_.StartUp(modules, stack_thread_);

  stack_handler_ = new os::Handler(stack_thread_);

  LOG_INFO("%s Successfully toggled Gd stack", __func__);
}

void Stack::Stop() {
  // First remove pid file so clients no stack is going down
  RemovePidFile();

  if (common::init_flags::gd_rust_is_enabled()) {
    if (rust_stack_ != nullptr) {
      rust::stack_stop(**rust_stack_);
    }
    return;
  }

  std::lock_guard<std::recursive_mutex> lock(mutex_);
  if (!common::init_flags::gd_core_is_enabled()) {
    bluetooth::shim::hci_on_shutting_down();
  }

  // Make sure gd acl flag is enabled and we started it up
  if (acl_ != nullptr) {
    acl_->FinalShutdown();
    delete acl_;
    acl_ = nullptr;
  }

  ASSERT_LOG(is_running_, "%s Gd stack not running", __func__);
  is_running_ = false;

  delete btm_;
  btm_ = nullptr;

  stack_handler_->Clear();

  stack_manager_.ShutDown();

  delete stack_handler_;
  stack_handler_ = nullptr;

  stack_thread_->Stop();
  delete stack_thread_;
  stack_thread_ = nullptr;

  LOG_INFO("%s Successfully shut down Gd stack", __func__);
}

bool Stack::IsRunning() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  return is_running_;
}

StackManager* Stack::GetStackManager() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return &stack_manager_;
}

const StackManager* Stack::GetStackManager() const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return &stack_manager_;
}

legacy::Acl* Stack::GetAcl() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  ASSERT_LOG(acl_ != nullptr, "Acl shim layer has not been created");
  return acl_;
}

LinkPolicyInterface* Stack::LinkPolicy() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  ASSERT_LOG(acl_ != nullptr, "Acl shim layer has not been created");
  return acl_;
}

Btm* Stack::GetBtm() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return btm_;
}

os::Handler* Stack::GetHandler() {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  ASSERT(is_running_);
  return stack_handler_;
}

bool Stack::IsDumpsysModuleStarted() const {
  std::lock_guard<std::recursive_mutex> lock(mutex_);
  return GetStackManager()->IsStarted<Dumpsys>();
}

}  // namespace shim
}  // namespace bluetooth
