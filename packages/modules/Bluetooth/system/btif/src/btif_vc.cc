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

/* Volume Control Interface */

#include <base/bind.h>
#include <base/location.h>
#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_vc.h>

#include "bta_vc_api.h"
#include "btif_common.h"
#include "stack/include/btu.h"
#include "types/raw_address.h"

using base::Bind;
using base::Unretained;
using bluetooth::vc::ConnectionState;
using bluetooth::vc::VolumeControlCallbacks;
using bluetooth::vc::VolumeControlInterface;

namespace {
std::unique_ptr<VolumeControlInterface> vc_instance;

class VolumeControlInterfaceImpl : public VolumeControlInterface,
                                   public VolumeControlCallbacks {
  ~VolumeControlInterfaceImpl() override = default;

  void Init(VolumeControlCallbacks* callbacks) override {
    DVLOG(2) << __func__;
    this->callbacks_ = callbacks;
    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::Initialize, this));
  }

  void OnConnectionState(ConnectionState state,
                         const RawAddress& address) override {
    DVLOG(2) << __func__ << " address: " << address;
    do_in_jni_thread(FROM_HERE, Bind(&VolumeControlCallbacks::OnConnectionState,
                                     Unretained(callbacks_), state, address));
  }

  void OnVolumeStateChanged(const RawAddress& address, uint8_t volume,
                            bool mute, bool isAutonomous) override {
    DVLOG(2) << __func__ << " address: " << address << " volume: " << volume
             << " mute: " << mute << " isAutonomous: " << isAutonomous;
    do_in_jni_thread(
        FROM_HERE,
        Bind(&VolumeControlCallbacks::OnVolumeStateChanged,
             Unretained(callbacks_), address, volume, mute, isAutonomous));
  }

  void OnGroupVolumeStateChanged(int group_id, uint8_t volume, bool mute,
                                 bool isAutonomous) override {
    DVLOG(2) << __func__ << " group_id: " << group_id << " volume: " << volume
             << " mute: " << mute << " isAutonomous: " << isAutonomous;
    do_in_jni_thread(
        FROM_HERE,
        Bind(&VolumeControlCallbacks::OnGroupVolumeStateChanged,
             Unretained(callbacks_), group_id, volume, mute, isAutonomous));
  }

  void OnDeviceAvailable(const RawAddress& address,
                         uint8_t num_offset) override {
    DVLOG(2) << __func__ << " address: " << address;
    do_in_jni_thread(FROM_HERE,
                     Bind(&VolumeControlCallbacks::OnDeviceAvailable,
                          Unretained(callbacks_), address, num_offset));
  }

  /* Callbacks for Volume Offset Control Service (VOCS) - Extended Audio Outputs
   */

  void OnExtAudioOutVolumeOffsetChanged(const RawAddress& address,
                                        uint8_t ext_output_id,
                                        int16_t offset) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id: " << ext_output_id << "offset:" << offset;

    do_in_jni_thread(
        FROM_HERE,
        Bind(&VolumeControlCallbacks::OnExtAudioOutVolumeOffsetChanged,
             Unretained(callbacks_), address, ext_output_id, offset));
  }

  void OnExtAudioOutLocationChanged(const RawAddress& address,
                                    uint8_t ext_output_id,
                                    uint32_t location) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id: " << ext_output_id
             << "location:" << loghex(location);

    do_in_jni_thread(
        FROM_HERE,
        Bind(&VolumeControlCallbacks::OnExtAudioOutLocationChanged,
             Unretained(callbacks_), address, ext_output_id, location));
  }

  void OnExtAudioOutDescriptionChanged(const RawAddress& address,
                                       uint8_t ext_output_id,
                                       std::string descr) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id: " << ext_output_id << "descr:" << descr;
    do_in_jni_thread(
        FROM_HERE,
        Bind(&VolumeControlCallbacks::OnExtAudioOutDescriptionChanged,
             Unretained(callbacks_), address, ext_output_id, descr));
  }

  void Connect(const RawAddress& address) override {
    DVLOG(2) << __func__ << " address: " << address;
    do_in_main_thread(FROM_HERE,
                      Bind(&VolumeControl::Connect,
                           Unretained(VolumeControl::Get()), address));
  }

  void Disconnect(const RawAddress& address) override {
    DVLOG(2) << __func__ << " address: " << address;
    do_in_main_thread(FROM_HERE,
                      Bind(&VolumeControl::Disconnect,
                           Unretained(VolumeControl::Get()), address));
  }

  void SetVolume(std::variant<RawAddress, int> addr_or_group_id,
                 uint8_t volume) override {
    DVLOG(2) << __func__ << " volume: " << volume;
    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::SetVolume,
                                      Unretained(VolumeControl::Get()),
                                      std::move(addr_or_group_id), volume));
  }

  void Mute(std::variant<RawAddress, int> addr_or_group_id) override {
    DVLOG(2) << __func__;
    do_in_main_thread(
        FROM_HERE, Bind(&VolumeControl::Mute, Unretained(VolumeControl::Get()),
                        std::move(addr_or_group_id)));
  }

  void Unmute(std::variant<RawAddress, int> addr_or_group_id) override {
    DVLOG(2) << __func__;
    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::UnMute,
                                      Unretained(VolumeControl::Get()),
                                      std::move(addr_or_group_id)));
  }

  void RemoveDevice(const RawAddress& address) override {
    DVLOG(2) << __func__ << " address: " << address;

    /* RemoveDevice can be called on devices that don't have HA enabled */
    if (VolumeControl::IsVolumeControlRunning()) {
      do_in_main_thread(FROM_HERE,
                        Bind(&VolumeControl::Disconnect,
                             Unretained(VolumeControl::Get()), address));
    }

    /* Placeholder: Remove things from storage here */
  }

  void GetExtAudioOutVolumeOffset(const RawAddress& address,
                                  uint8_t ext_output_id) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id:" << ext_output_id;
    do_in_main_thread(
        FROM_HERE,
        Bind(&VolumeControl::GetExtAudioOutVolumeOffset,
             Unretained(VolumeControl::Get()), address, ext_output_id));
  }

  void SetExtAudioOutVolumeOffset(const RawAddress& address,
                                  uint8_t ext_output_id,
                                  int16_t offset_val) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id:" << ext_output_id
             << "ext_output_id:" << offset_val;
    do_in_main_thread(FROM_HERE,
                      Bind(&VolumeControl::SetExtAudioOutVolumeOffset,
                           Unretained(VolumeControl::Get()), address,
                           ext_output_id, offset_val));
  }

  void GetExtAudioOutLocation(const RawAddress& address,
                              uint8_t ext_output_id) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id:" << ext_output_id;

    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::GetExtAudioOutLocation,
                                      Unretained(VolumeControl::Get()), address,
                                      ext_output_id));
  }

  void SetExtAudioOutLocation(const RawAddress& address, uint8_t ext_output_id,
                              uint32_t location) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id:" << ext_output_id
             << "location:" << loghex(location);
    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::SetExtAudioOutLocation,
                                      Unretained(VolumeControl::Get()), address,
                                      ext_output_id, location));
  }

  void GetExtAudioOutDescription(const RawAddress& address,
                                 uint8_t ext_output_id) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id:" << ext_output_id;

    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::GetExtAudioOutDescription,
                                      Unretained(VolumeControl::Get()), address,
                                      ext_output_id));
  }

  void SetExtAudioOutDescription(const RawAddress& address,
                                 uint8_t ext_output_id,
                                 std::string descr) override {
    DVLOG(2) << __func__ << " address: " << address
             << "ext_output_id:" << ext_output_id << "description:" << descr;

    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::SetExtAudioOutDescription,
                                      Unretained(VolumeControl::Get()), address,
                                      ext_output_id, descr));
  }

  void Cleanup(void) override {
    DVLOG(2) << __func__;
    do_in_main_thread(FROM_HERE, Bind(&VolumeControl::CleanUp));
  }

 private:
  VolumeControlCallbacks* callbacks_;
};

} /* namespace */

VolumeControlInterface* btif_volume_control_get_interface(void) {
  if (!vc_instance) vc_instance.reset(new VolumeControlInterfaceImpl());

  return vc_instance.get();
}
