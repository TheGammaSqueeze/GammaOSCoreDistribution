/*
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_VHAL_CLIENT_INCLUDE_AIDLHALPROPCONFIG_H_
#define CPP_VHAL_CLIENT_INCLUDE_AIDLHALPROPCONFIG_H_

#include "IHalPropConfig.h"

#include <aidl/android/hardware/automotive/vehicle/VehicleAreaConfig.h>
#include <aidl/android/hardware/automotive/vehicle/VehiclePropConfig.h>

#include <string>
#include <vector>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

class AidlHalAreaConfig : public IHalAreaConfig {
public:
    explicit AidlHalAreaConfig(
            ::aidl::android::hardware::automotive::vehicle::VehicleAreaConfig&& areaConfig);

    int32_t getAreaId() const override;

    int32_t getMinInt32Value() const override;

    int32_t getMaxInt32Value() const override;

    int64_t getMinInt64Value() const override;

    int64_t getMaxInt64Value() const override;

    float getMinFloatValue() const override;

    float getMaxFloatValue() const override;

private:
    ::aidl::android::hardware::automotive::vehicle::VehicleAreaConfig mAreaConfig;
};

class AidlHalPropConfig : public IHalPropConfig {
public:
    explicit AidlHalPropConfig(
            ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig&& config);

    int32_t getPropId() const override;

    int32_t getAccess() const override;

    int32_t getChangeMode() const override;

    const IHalAreaConfig* getAreaConfigs() const override;

    size_t getAreaConfigSize() const override;

    std::vector<int32_t> getConfigArray() const override;

    std::string getConfigString() const override;

    float getMinSampleRate() const override;

    float getMaxSampleRate() const override;

private:
    ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig mPropConfig;
    std::vector<AidlHalAreaConfig> mAreaConfigs;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_AIDLHALPROPCONFIG_H_
