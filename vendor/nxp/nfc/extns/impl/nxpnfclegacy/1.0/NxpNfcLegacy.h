/******************************************************************************
 *
 *  Copyright 2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#ifndef VENDOR_NXP_NXPNFCLEGACY_V1_0_NXPNFCLEGACY_H
#define VENDOR_NXP_NXPNFCLEGACY_V1_0_NXPNFCLEGACY_H

#include <vendor/nxp/nxpnfclegacy/1.0/INxpNfcLegacy.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>



namespace vendor {
namespace nxp {
namespace nxpnfclegacy {
namespace V1_0 {
namespace implementation {

using ::android::hidl::base::V1_0::IBase;
using ::vendor::nxp::nxpnfclegacy::V1_0::INxpNfcLegacy;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;

struct NxpNfcLegacy : public INxpNfcLegacy{
  Return<uint8_t> setEseState(NxpNfcHalEseState EseState) override;
  Return<uint8_t> getchipType() override;
  Return<uint16_t> setNfcServicePid(uint64_t pid) override;
  Return<uint16_t> getEseState() override;
  Return<uint16_t> spiDwpSync(uint32_t level) override;
  Return<uint16_t> RelForceDwpOnOffWait(uint32_t level) override;
  Return<int8_t> getSPMStatus(uint32_t level) override;
  Return<int32_t> hciInitUpdateState(NfcHciInitStatus HciStatus) override;
  Return<int32_t> hciInitUpdateStateComplete() override;
  Return<void> getCachedNfccConfig(getCachedNfccConfig_cb _hidl_cb) override;
  Return<void> getNxpConfig(getNxpConfig_cb _hidl_cb) override;
  Return<void> nciTransceive(const NxpNciExtnCmd &in,nciTransceive_cb _hidl_cb);
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace nxpnfclegacy
}  // namespace nxp
}  // namespace vendor

#endif  // VENDOR_NXP_NXPNFCLEGACY_V1_0_NXPNFCLEGACY_H
