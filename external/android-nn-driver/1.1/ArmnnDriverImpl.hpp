//
// Copyright © 2017 Arm Ltd. All rights reserved.
// SPDX-License-Identifier: MIT
//

#pragma once

#include <HalInterfaces.h>

#include "NamespaceAdaptor.hpp"
#include "../DriverOptions.hpp"

#include <armnn/ArmNN.hpp>

#if ARMNN_ANDROID_S
using namespace android::nn;
#elif ARMNN_ANDROID_R
using namespace android::nn::hal;
#endif


namespace V1_0 = ::android::hardware::neuralnetworks::V1_0;
namespace V1_1 = ::android::hardware::neuralnetworks::V1_1;

namespace armnn_driver
{
namespace hal_1_1
{

class ArmnnDriverImpl
{
public:
    static Return<void> getCapabilities_1_1(const armnn::IRuntimePtr& runtime,
                                            V1_1::IDevice::getCapabilities_1_1_cb cb);
};

} // namespace hal_1_1
} // namespace armnn_driver
