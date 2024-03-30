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

#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <utils/SystemClock.h>

#include <thread>

#include <aidl/android/hardware/sensors/BnSensors.h>

using aidl::android::hardware::sensors::Event;
using aidl::android::hardware::sensors::ISensors;
using aidl::android::hardware::sensors::SensorInfo;
using aidl::android::hardware::sensors::SensorStatus;
using aidl::android::hardware::sensors::SensorType;

std::shared_ptr<ISensors> startSensorInjection() {
  auto sensors = ISensors::fromBinder(ndk::SpAIBinder(
      AServiceManager_getService("android.hardware.sensors.ISensors/default")));
  if (sensors == nullptr) {
    LOG(FATAL) << "Unable to get ISensors.";
  }

  // Place the ISensors HAL into DATA_INJECTION mode so that we can
  // inject events.
  auto result =
      sensors->setOperationMode(ISensors::OperationMode::DATA_INJECTION);
  if (!result.isOk()) {
    LOG(FATAL) << "Unable to set ISensors operation mode to DATA_INJECTION: "
               << result.getDescription();
  }

  return sensors;
}

int getSensorHandle(SensorType type, const std::shared_ptr<ISensors> sensors) {
  // Find the first available sensor of the given type.
  int handle = -1;
  std::vector<SensorInfo> sensors_list;
  auto result = sensors->getSensorsList(&sensors_list);
  if (!result.isOk()) {
    LOG(FATAL) << "Unable to get ISensors sensors list: "
               << result.getDescription();
  }
  for (const SensorInfo& sensor : sensors_list) {
    if (sensor.type == type) {
      handle = sensor.sensorHandle;
      break;
    }
  }
  if (handle == -1) {
    LOG(FATAL) << "Unable to find sensor.";
  }
  return handle;
}

void endSensorInjection(const std::shared_ptr<ISensors> sensors) {
  // Return the ISensors HAL back to NORMAL mode.
  auto result = sensors->setOperationMode(ISensors::OperationMode::NORMAL);
  if (!result.isOk()) {
    LOG(FATAL) << "Unable to set sensors operation mode to NORMAL: "
               << result.getDescription();
  }
}

// Inject ACCELEROMETER events to corresponding to a given physical
// device orientation: portrait or landscape.
void InjectOrientation(bool portrait) {
  auto sensors = startSensorInjection();
  int handle = getSensorHandle(SensorType::ACCELEROMETER, sensors);

  // Create a base ISensors accelerometer event.
  Event event;
  event.sensorHandle = handle;
  event.sensorType = SensorType::ACCELEROMETER;
  Event::EventPayload::Vec3 vec3;
  if (portrait) {
    vec3.x = 0;
    vec3.y = 9.2;
  } else {
    vec3.x = 9.2;
    vec3.y = 0;
  }
  vec3.z = 3.5;
  vec3.status = SensorStatus::ACCURACY_HIGH;
  event.payload.set<Event::EventPayload::Tag::vec3>(vec3);

  // Repeatedly inject accelerometer events. The WindowManager orientation
  // listener responds to sustained accelerometer data, not just a single event.
  android::base::Timer timer;
  while (timer.duration() < 1s) {
    event.timestamp = android::elapsedRealtimeNano();
    auto result = sensors->injectSensorData(event);
    if (!result.isOk()) {
      LOG(FATAL) << "Unable to inject ISensors accelerometer event: "
                 << result.getDescription();
    }
    std::this_thread::sleep_for(10ms);
  }

  endSensorInjection(sensors);
}

// Inject a single HINGE_ANGLE event at the given angle.
void InjectHingeAngle(int angle) {
  auto sensors = startSensorInjection();
  int handle = getSensorHandle(SensorType::HINGE_ANGLE, sensors);

  // Create a base ISensors hinge_angle event.
  Event event;
  event.sensorHandle = handle;
  event.sensorType = SensorType::HINGE_ANGLE;
  event.payload.set<Event::EventPayload::Tag::scalar>((float)angle);
  event.timestamp = android::elapsedRealtimeNano();

  auto result = sensors->injectSensorData(event);
  if (!result.isOk()) {
    LOG(FATAL) << "Unable to inject HINGE_ANGLE data"
               << result.getDescription();
  }

  endSensorInjection(sensors);
}

int main(int argc, char** argv) {
  if (argc == 2) {
    LOG(FATAL) << "Expected command line args 'rotate <portrait|landscape>' or "
                  "'hinge_angle <value>'";
  }

  if (!strcmp(argv[1], "rotate")) {
    bool portrait = true;
    if (!strcmp(argv[2], "portrait")) {
      portrait = true;
    } else if (!strcmp(argv[2], "landscape")) {
      portrait = false;
    } else {
      LOG(FATAL) << "Expected command line arg 'portrait' or 'landscape'";
    }
    InjectOrientation(portrait);
  } else if (!strcmp(argv[1], "hinge_angle")) {
    int angle = std::stoi(argv[2]);
    if (angle < 0 || angle > 360) {
      LOG(FATAL) << "Bad hinge_angle value: " << argv[2];
    }
    InjectHingeAngle(angle);
  } else {
    LOG(FATAL) << "Unknown arg: " << argv[1];
  }
}
