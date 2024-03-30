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

#include "SessionConfigurationUtilsHost.h"

namespace android {
namespace camera3 {
namespace SessionConfigurationUtils {

int32_t getAppropriateModeTag(int32_t defaultTag, bool maxResolution) {
    if (!maxResolution) {
        return defaultTag;
    }
    switch (defaultTag) {
        case ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS:
            return ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS:
            return ANDROID_SCALER_AVAILABLE_MIN_FRAME_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_SCALER_AVAILABLE_STALL_DURATIONS:
            return ANDROID_SCALER_AVAILABLE_STALL_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS:
            return ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS:
            return ANDROID_DEPTH_AVAILABLE_DEPTH_MIN_FRAME_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS:
            return ANDROID_DEPTH_AVAILABLE_DEPTH_STALL_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS:
            return ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS:
            return ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_MIN_FRAME_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS:
            return ANDROID_DEPTH_AVAILABLE_DYNAMIC_DEPTH_STALL_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS:
            return ANDROID_HEIC_AVAILABLE_HEIC_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS:
            return ANDROID_HEIC_AVAILABLE_HEIC_MIN_FRAME_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS:
            return ANDROID_HEIC_AVAILABLE_HEIC_STALL_DURATIONS_MAXIMUM_RESOLUTION;
        case ANDROID_SENSOR_OPAQUE_RAW_SIZE:
            return ANDROID_SENSOR_OPAQUE_RAW_SIZE_MAXIMUM_RESOLUTION;
        case ANDROID_LENS_INTRINSIC_CALIBRATION:
            return ANDROID_LENS_INTRINSIC_CALIBRATION_MAXIMUM_RESOLUTION;
        case ANDROID_LENS_DISTORTION:
            return ANDROID_LENS_DISTORTION_MAXIMUM_RESOLUTION;
        default:
            ALOGE("%s: Tag %d doesn't have a maximum resolution counterpart", __FUNCTION__,
                    defaultTag);
            return -1;
    }
    return -1;
}

bool isUltraHighResolutionSensor(const CameraMetadata &deviceInfo) {
    camera_metadata_ro_entry_t entryCap;
    entryCap = deviceInfo.find(ANDROID_REQUEST_AVAILABLE_CAPABILITIES);
    // Go through the capabilities and check if it has
    // ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
    for (size_t i = 0; i < entryCap.count; ++i) {
        uint8_t capability = entryCap.data.u8[i];
        if (capability == ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR) {
            return true;
        }
    }
    return false;
}

bool getArrayWidthAndHeight(const CameraMetadata *deviceInfo,
        int32_t arrayTag, int32_t *width, int32_t *height) {
    if (width == nullptr || height == nullptr) {
        ALOGE("%s: width / height nullptr", __FUNCTION__);
        return false;
    }
    camera_metadata_ro_entry_t entry;
    entry = deviceInfo->find(arrayTag);
    if (entry.count != 4) return false;
    *width = entry.data.i32[2];
    *height = entry.data.i32[3];
    return true;
}

} // namespace SessionConfigurationUtils
} // namespace camera3
} // namespace android