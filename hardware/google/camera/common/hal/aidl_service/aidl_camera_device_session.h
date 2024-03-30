/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_SESSION_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_SESSION_H_

#include <aidl/android/hardware/camera/device/BnCameraDeviceSession.h>
#include <aidl/android/hardware/camera/device/ICameraDevice.h>
#include <aidl/android/hardware/camera/device/ICameraDeviceCallback.h>
#include <android/hardware/thermal/2.0/IThermal.h>
#include <fmq/AidlMessageQueue.h>

#include <shared_mutex>

#include "aidl_profiler.h"
#include "camera_device_session.h"
#include "hidl_thermal_utils.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace implementation {

using ::aidl::android::hardware::camera::device::BnCameraDeviceSession;
using ::aidl::android::hardware::camera::device::BufferCache;
using ::aidl::android::hardware::camera::device::CameraMetadata;
using ::aidl::android::hardware::camera::device::CameraOfflineSessionInfo;
using ::aidl::android::hardware::camera::device::CaptureRequest;
using ::aidl::android::hardware::camera::device::HalStream;
using ::aidl::android::hardware::camera::device::ICameraDeviceCallback;
using ::aidl::android::hardware::camera::device::ICameraOfflineSession;
using ::aidl::android::hardware::camera::device::RequestTemplate;
using ::aidl::android::hardware::camera::device::StreamConfiguration;
using ::aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using ::android::hardware::camera::implementation::AidlProfiler;
using ndk::ScopedAStatus;

using MetadataQueue = AidlMessageQueue<int8_t, SynchronizedReadWrite>;

// AidlCameraDeviceSession implements the AIDL camera device session interface,
// ICameraDeviceSession, that contains the methods to configure and request
// captures from an active camera device.
class AidlCameraDeviceSession : public BnCameraDeviceSession {
 public:
  // Create a AidlCameraDeviceSession.
  // device_session is a google camera device session that
  // AidlCameraDeviceSession is going to manage. Creating a
  // AidlCameraDeviceSession will fail if device_session is
  // nullptr.
  static std::shared_ptr<AidlCameraDeviceSession> Create(
      const std::shared_ptr<ICameraDeviceCallback>& callback,
      std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session,
      std::shared_ptr<AidlProfiler> aidl_profiler);

  virtual ~AidlCameraDeviceSession();

  // functions in ICameraDeviceSession

  ScopedAStatus close() override;

  ScopedAStatus configureStreams(const StreamConfiguration&,
                                 std::vector<HalStream>*) override;

  ScopedAStatus constructDefaultRequestSettings(
      RequestTemplate in_type, CameraMetadata* _aidl_return) override;

  ScopedAStatus flush() override;

  ScopedAStatus getCaptureRequestMetadataQueue(
      ::aidl::android::hardware::common::fmq::MQDescriptor<
          int8_t, SynchronizedReadWrite>* _aidl_return) override;

  ScopedAStatus getCaptureResultMetadataQueue(
      ::aidl::android::hardware::common::fmq::MQDescriptor<
          int8_t, SynchronizedReadWrite>* _aidl_return) override;

  ScopedAStatus isReconfigurationRequired(
      const CameraMetadata& in_oldSessionParams,
      const CameraMetadata& in_newSessionParams, bool* _aidl_return) override;

  ScopedAStatus processCaptureRequest(
      const std::vector<CaptureRequest>& in_requests,
      const std::vector<BufferCache>& in_cachesToRemove,
      int32_t* _aidl_return) override;

  ScopedAStatus signalStreamFlush(const std::vector<int32_t>& in_streamIds,
                                  int32_t in_streamConfigCounter) override;

  ScopedAStatus switchToOffline(
      const std::vector<int32_t>& in_streamsToKeep,
      CameraOfflineSessionInfo* out_offlineSessionInfo,
      std::shared_ptr<ICameraOfflineSession>* _aidl_return) override;

  ScopedAStatus repeatingRequestEnd(
      int32_t /*in_frameNumber*/,
      const std::vector<int32_t>& /*in_streamIds*/) override {
    return ScopedAStatus::ok();
  };

  AidlCameraDeviceSession() = default;

 protected:
  ::ndk::SpAIBinder createBinder() override;

 private:
  static constexpr uint32_t kRequestMetadataQueueSizeBytes = 1 << 20;  // 1MB
  static constexpr uint32_t kResultMetadataQueueSizeBytes = 1 << 20;   // 1MB

  // Initialize the latest available gralloc buffer mapper.
  status_t InitializeBufferMapper();

  // Initialize AidlCameraDeviceSession with a CameraDeviceSession.
  status_t Initialize(
      const std::shared_ptr<ICameraDeviceCallback>& callback,
      std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session,
      std::shared_ptr<AidlProfiler> aidl_profiler);

  // Create a metadata queue.
  // If override_size_property contains a valid size, it will create a metadata
  // queue of that size. If it override_size_property doesn't contain a valid
  // size, it will create a metadata queue of the default size.
  // default_size_bytes is the default size of the message queue in bytes.
  // override_size_property is the name of the system property that contains
  // the message queue size.
  status_t CreateMetadataQueue(std::unique_ptr<MetadataQueue>* metadata_queue,
                               uint32_t default_size_bytes,
                               const char* override_size_property);

  // Invoked when receiving a result from HAL.
  void ProcessCaptureResult(
      std::unique_ptr<google_camera_hal::CaptureResult> hal_result);

  // Invoked when receiving a message from HAL.
  void NotifyHalMessage(const google_camera_hal::NotifyMessage& hal_message);

  // Invoked when requesting stream buffers from HAL.
  google_camera_hal::BufferRequestStatus RequestStreamBuffers(
      const std::vector<google_camera_hal::BufferRequest>& hal_buffer_requests,
      std::vector<google_camera_hal::BufferReturn>* hal_buffer_returns);

  // Invoked when returning stream buffers from HAL.
  void ReturnStreamBuffers(
      const std::vector<google_camera_hal::StreamBuffer>& return_hal_buffers);

  // Import a buffer handle.
  template <class T, class U>
  buffer_handle_t ImportBufferHandle(const sp<T> buffer_mapper_,
                                     const hidl_handle& buffer_hidl_handle);

  // Set camera device session callbacks.
  void SetSessionCallbacks();

  // Register a thermal changed callback.
  // notify_throttling will be invoked when thermal status changes.
  // If filter_type is false, type will be ignored and all types will be
  // monitored.
  // If filter_type is true, only type will be monitored.
  status_t RegisterThermalChangedCallback(
      google_camera_hal::NotifyThrottlingFunc notify_throttling,
      bool filter_type, google_camera_hal::TemperatureType type);

  // Unregister thermal changed callback.
  void UnregisterThermalChangedCallback();

  std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session_;

  // Metadata queue to read the request metadata from.
  std::unique_ptr<MetadataQueue> request_metadata_queue_;

  // Metadata queue to write the result metadata to.
  std::unique_ptr<MetadataQueue> result_metadata_queue_;

  // Assuming callbacks to framework is thread-safe, the shared mutex is only
  // used to protect member variable writing and reading.
  std::shared_mutex aidl_device_callback_lock_;
  // Protected by aidl_device_callback_lock_
  std::shared_ptr<ICameraDeviceCallback> aidl_device_callback_;

  sp<android::hardware::graphics::mapper::V2_0::IMapper> buffer_mapper_v2_;
  sp<android::hardware::graphics::mapper::V3_0::IMapper> buffer_mapper_v3_;
  sp<android::hardware::graphics::mapper::V4_0::IMapper> buffer_mapper_v4_;

  std::mutex hidl_thermal_mutex_;
  sp<android::hardware::thermal::V2_0::IThermal> thermal_;

  // Must be protected by hidl_thermal_mutex_.
  sp<android::hardware::thermal::V2_0::IThermalChangedCallback>
      thermal_changed_callback_;

  // Flag for profiling first frame processing time.
  bool first_frame_requested_ = false;

  // The frame number of first capture request after configure stream
  uint32_t first_request_frame_number_ = 0;

  std::mutex pending_first_frame_buffers_mutex_;
  // Profiling first frame process time. Stop timer when it become 0.
  // Must be protected by pending_first_frame_buffers_mutex_
  size_t num_pending_first_frame_buffers_ = 0;

  std::shared_ptr<AidlProfiler> aidl_profiler_;
};

}  // namespace implementation
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_SESSION_H_
