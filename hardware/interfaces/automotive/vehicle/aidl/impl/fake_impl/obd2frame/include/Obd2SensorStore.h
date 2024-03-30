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

#ifndef android_hardware_automotive_vehicle_aidl_impl_fake_impl_obd2frame_include_Obd2SensorStore_H_
#define android_hardware_automotive_vehicle_aidl_impl_fake_impl_obd2frame_include_Obd2SensorStore_H_

#include <VehicleHalTypes.h>
#include <VehicleObjectPool.h>
#include <VehicleUtils.h>

#include <android-base/result.h>

#include <memory>
#include <vector>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace fake {
namespace obd2frame {

// This class wraps all the logic required to create an OBD2 frame.
// It allows storing sensor values, setting appropriate bitmasks as needed, and returning
// appropriately laid out storage of sensor values suitable for being returned via a VehicleHal
// implementation.
class Obd2SensorStore final {
  public:
    // Creates a sensor storage with a given number of vendor-specific sensors.
    Obd2SensorStore(std::shared_ptr<VehiclePropValuePool> valuePool, size_t numVendorIntegerSensors,
                    size_t numVendorFloatSensors);

    template <class T>
    static int getLastIndex() {
        auto range = ndk::enum_range<T>();
        auto it = range.begin();
        while (std::next(it) != range.end()) {
            it++;
        }
        return toInt(*it);
    }

    // Stores an integer-valued sensor.
    aidl::android::hardware::automotive::vehicle::StatusCode setIntegerSensor(
            aidl::android::hardware::automotive::vehicle::DiagnosticIntegerSensorIndex index,
            int32_t value);
    // Stores an integer-valued sensor.
    aidl::android::hardware::automotive::vehicle::StatusCode setIntegerSensor(size_t index,
                                                                              int32_t value);
    // Stores a float-valued sensor.
    aidl::android::hardware::automotive::vehicle::StatusCode setFloatSensor(
            aidl::android::hardware::automotive::vehicle::DiagnosticFloatSensorIndex index,
            float value);
    // Stores a float-valued sensor.
    aidl::android::hardware::automotive::vehicle::StatusCode setFloatSensor(size_t index,
                                                                            float value);

    // Returns a sensor property value using the given DTC.
    VehiclePropValuePool::RecyclableType getSensorProperty(const std::string& dtc) const;

  private:
    class BitmaskInVector final {
      public:
        explicit BitmaskInVector(size_t numBits = 0);
        void resize(size_t numBits);
        android::base::Result<bool> get(size_t index) const;
        android::base::Result<void> set(size_t index, bool value);

        const std::vector<uint8_t>& getBitmask() const;

      private:
        std::vector<uint8_t> mStorage;
        size_t mNumBits;
    };

    std::vector<int32_t> mIntegerSensors;
    std::vector<float> mFloatSensors;
    BitmaskInVector mSensorsBitmask;
    std::shared_ptr<VehiclePropValuePool> mValuePool;

    // Returns a vector that contains all integer sensors stored.
    const std::vector<int32_t>& getIntegerSensors() const;
    // Returns a vector that contains all float sensors stored.
    const std::vector<float>& getFloatSensors() const;
    // Returns a vector that contains a bitmask for all stored sensors.
    const std::vector<uint8_t>& getSensorsBitmask() const;
};

}  // namespace obd2frame
}  // namespace fake
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // android_hardware_automotive_vehicle_aidl_impl_fake_impl_obd2frame_include_Obd2SensorStore_H_
