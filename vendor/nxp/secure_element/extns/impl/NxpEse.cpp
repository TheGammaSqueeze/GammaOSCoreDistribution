/******************************************************************************
 *
 *  Copyright (C) 2018-2019 NXP Semiconductors
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

#define LOG_TAG "vendor.nxp.nxpese@1.0-impl"
#include "NxpEse.h"
#include "LsClient.h"
#include "phNxpEse_Api.h"
#include <log/log.h>
#include "SpiEseUpdater.h"

namespace vendor {
namespace nxp {
namespace nxpese {
namespace V1_0 {
namespace implementation {
using ::android::hardware::hidl_vec;
// Methods from ::vendor::nxp::nxpese::V1_0::INxpEse follow.

Return<void> NxpEse::ioctl(uint64_t ioctlType,
                           const hidl_vec<uint8_t>& inOutData,
                           ioctl_cb _hidl_cb) {
  ALOGD("NxpEse::ioctl(): enter");
  ese_nxp_IoctlInOutData_t inpOutData;
  memset(&inpOutData, 0, sizeof(inpOutData));
  ese_nxp_IoctlInOutData_t* pInOutData =
      (ese_nxp_IoctlInOutData_t*)&inOutData[0];

  /*data from proxy->stub is copied to local data which can be updated by
   * underlying HAL implementation since its an inout argument*/
  if(ioctlType == HAL_ESE_IOCTL_NFC_JCOP_DWNLD) {
    memcpy(&inpOutData, pInOutData, sizeof(ese_nxp_IoctlInOutData_t));
  } else {
    inpOutData.inp.data.nxpCmd.cmd_len = inOutData.size();
    memcpy(&inpOutData.inp.data.nxpCmd.p_cmd, pInOutData,
           inpOutData.inp.data.nxpCmd.cmd_len);
  }
  ESESTATUS status = phNxpEse_spiIoctl(ioctlType, &inpOutData);
  if(HAL_ESE_IOCTL_NFC_JCOP_DWNLD == ioctlType) {
      ALOGD("NxpEse::ioctl == HAL_ESE_IOCTL_NFC_JCOP_DWNLD");
      if(pInOutData->inp.data.nxpCmd.p_cmd[0] == ESE_JCOP_UPDATE_COMPLETED
          || pInOutData->inp.data.nxpCmd.p_cmd[0] == ESE_LS_UPDATE_COMPLETED) {
        ALOGD("NxpEse::ioctl state == ESE_UPDATE_COMPLETED");
        SpiEseUpdater::setDwpEseClientState(ESE_UPDATE_COMPLETED);
        spiEseUpdater.reInitSeService();
      }
  }

  /*copy data and additional fields indicating status of ioctl operation
   * and context of the caller. Then invoke the corresponding proxy callback*/
  inpOutData.out.ioctlType = ioctlType;
  inpOutData.out.context = pInOutData->inp.context;
  inpOutData.out.result = status;
  hidl_vec<uint8_t> outputData;
  outputData.setToExternal((uint8_t*)&inpOutData.out,
                           sizeof(ese_nxp_ExtnOutputData_t));
  ALOGD("GET ESE update state = %d",inpOutData.out.data.status);
  _hidl_cb(outputData);
  ALOGD("NxpEse::ioctl(): exit");
  return Void();
}

// Methods from ::android::hidl::base::V1_0::IBase follow.

}  // namespace implementation
}  // namespace V1_0
}  // namespace nxpese
}  // namespace nxp
}  // namespace vendor
