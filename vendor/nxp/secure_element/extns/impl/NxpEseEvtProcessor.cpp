/******************************************************************************
 *
 *  Copyright 2019 NXP
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

#define LOG_TAG "vendor.nxp.eventprocessor@1.0-impl"
#include "NxpEseEvtProcessor.h"
#include "LsClient.h"
#include "phNxpEse_Api.h"
#include <log/log.h>

namespace vendor {
namespace nxp {
namespace eventprocessor {
namespace V1_0 {
namespace implementation {
using ::android::hardware::hidl_vec;
// Methods from ::vendor::nxp::eventprocessor::V1_0::INxpEseNfccNtf follow.

Return<void> NxpEseEvtProcessor::nfccNtf(uint64_t ntfType,
                             const hidl_vec<uint8_t> &ntfData) {
  ALOGD("NxpEseEvtProcessor::nfccNtf(): enter");
  ese_nxp_IoctlInOutData_t inpOutData;
  ese_nxp_IoctlInOutData_t *pInOutData =
      (ese_nxp_IoctlInOutData_t *)&ntfData[0];
  /*data from proxy->stub is copied to local data*/
  memcpy(&inpOutData, pInOutData, sizeof(ese_nxp_IoctlInOutData_t));
  phNxpEse_spiIoctl(ntfType, &inpOutData);
  ALOGD("NxpEseEvtProcessor::nfccNtf(): exit");
  return Void();
}

// Methods from ::android::hidl::base::V1_0::IBase follow.

}  // namespace implementation
}  // namespace V1_0
}  // namespace eventprocessor
}  // namespace nxp
}  // namespace vendor
