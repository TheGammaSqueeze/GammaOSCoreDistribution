/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_PIXEL_HEALTH_HEALTHHELPER_H
#define HARDWARE_GOOGLE_PIXEL_HEALTH_HEALTHHELPER_H

#include <aidl/android/hardware/health/HealthInfo.h>
#include <batteryservice/BatteryService.h>

namespace hardware {
namespace google {
namespace pixel {
namespace health {

// One-way conversion from BatteryProperties to AIDL HealthInfo
aidl::android::hardware::health::HealthInfo ToHealthInfo(
        const struct android::BatteryProperties *props);

}  // namespace health
}  // namespace pixel
}  // namespace google
}  // namespace hardware

#endif  // HARDWARE_GOOGLE_PIXEL_HEALTH_HEALTHHELPER_H
