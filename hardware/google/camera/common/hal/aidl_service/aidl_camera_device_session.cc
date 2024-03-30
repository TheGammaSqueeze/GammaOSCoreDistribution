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

#define LOG_TAG "GCH_AidlCameraDeviceSession"
#define ATRACE_TAG ATRACE_TAG_CAMERA
//#define LOG_NDEBUG 0
#include "aidl_camera_device_session.h"

#include <android/binder_ibinder_platform.h>
#include <cutils/properties.h>
#include <cutils/trace.h>
#include <log/log.h>
#include <malloc.h>
#include <utils/Trace.h>

#include "aidl_profiler.h"
#include "aidl_utils.h"
namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace implementation {

namespace aidl_utils = ::android::hardware::camera::implementation::aidl_utils;

using aidl::android::hardware::camera::common::Status;
using aidl::android::hardware::camera::device::BufferRequest;
using aidl::android::hardware::camera::device::BufferRequestStatus;
using aidl::android::hardware::camera::device::CaptureResult;
using aidl::android::hardware::camera::device::HalStream;
using aidl::android::hardware::camera::device::NotifyMsg;
using aidl::android::hardware::camera::device::StreamBuffer;
using aidl::android::hardware::camera::device::StreamBufferRet;
using aidl::android::hardware::camera::device::StreamBuffersVal;
using aidl_utils::ConvertToAidlReturn;
using ::android::hardware::thermal::V1_0::ThermalStatus;
using ::android::hardware::thermal::V1_0::ThermalStatusCode;
using ::android::hardware::thermal::V2_0::Temperature;
using ::android::hardware::thermal::V2_0::TemperatureType;

std::shared_ptr<AidlCameraDeviceSession> AidlCameraDeviceSession::Create(
    const std::shared_ptr<ICameraDeviceCallback>& callback,
    std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session,
    std::shared_ptr<AidlProfiler> aidl_profiler) {
  ATRACE_NAME("AidlCameraDeviceSession::Create");
  auto session = ndk::SharedRefBase::make<AidlCameraDeviceSession>();
  if (session == nullptr) {
    ALOGE("%s: Cannot create a AidlCameraDeviceSession.", __FUNCTION__);
    return nullptr;
  }

  status_t res =
      session->Initialize(callback, std::move(device_session), aidl_profiler);
  if (res != OK) {
    ALOGE("%s: Initializing AidlCameraDeviceSession failed: %s(%d)",
          __FUNCTION__, strerror(-res), res);
    return nullptr;
  }

  return session;
}

AidlCameraDeviceSession::~AidlCameraDeviceSession() {
  ATRACE_NAME("AidlCameraDeviceSession::~AidlCameraDeviceSession");
  close();
  // camera's closing, so flush any unused malloc pages
  mallopt(M_PURGE, 0);
}

void AidlCameraDeviceSession::ProcessCaptureResult(
    std::unique_ptr<google_camera_hal::CaptureResult> hal_result) {
  std::shared_lock lock(aidl_device_callback_lock_);
  if (aidl_device_callback_ == nullptr) {
    ALOGE("%s: aidl_device_callback_ is nullptr", __FUNCTION__);
    return;
  }

  {
    std::lock_guard<std::mutex> pending_lock(pending_first_frame_buffers_mutex_);
    if (!hal_result->output_buffers.empty() &&
        num_pending_first_frame_buffers_ > 0 &&
        first_request_frame_number_ == hal_result->frame_number) {
      num_pending_first_frame_buffers_ -= hal_result->output_buffers.size();
      if (num_pending_first_frame_buffers_ == 0) {
        ALOGI("%s: First frame done", __FUNCTION__);
        aidl_profiler_->FirstFrameEnd();
        ATRACE_ASYNC_END("first_frame", 0);
        ATRACE_ASYNC_END("switch_mode", 0);
      }
    }
  }
  for (auto& buffer : hal_result->output_buffers) {
    aidl_profiler_->ProfileFrameRate("Stream " +
                                     std::to_string(buffer.stream_id));
  }

  std::vector<CaptureResult> aidl_results(1);
  status_t res = aidl_utils::ConvertToAidlCaptureResult(
      result_metadata_queue_.get(), std::move(hal_result), &aidl_results[0]);
  if (res != OK) {
    ALOGE("%s: Converting to AIDL result failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return;
  }

  auto aidl_res = aidl_device_callback_->processCaptureResult(aidl_results);
  if (!aidl_res.isOk()) {
    ALOGE("%s: processCaptureResult transaction failed: %s.", __FUNCTION__,
          aidl_res.getMessage());
    return;
  }
}

void AidlCameraDeviceSession::NotifyHalMessage(
    const google_camera_hal::NotifyMessage& hal_message) {
  std::shared_lock lock(aidl_device_callback_lock_);
  if (aidl_device_callback_ == nullptr) {
    ALOGE("%s: aidl_device_callback_ is nullptr", __FUNCTION__);
    return;
  }

  std::vector<NotifyMsg> aidl_messages(1);
  status_t res =
      aidl_utils::ConverToAidlNotifyMessage(hal_message, &aidl_messages[0]);
  if (res != OK) {
    ALOGE("%s: Converting to AIDL message failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return;
  }

  auto aidl_res = aidl_device_callback_->notify(aidl_messages);
  if (!aidl_res.isOk()) {
    ALOGE("%s: notify transaction failed: %s.", __FUNCTION__,
          aidl_res.getMessage());
    return;
  }
}

static void cleanupHandles(std::vector<native_handle_t*>& handles_to_delete) {
  for (auto& handle : handles_to_delete) {
    native_handle_delete(handle);
  }
}

google_camera_hal::BufferRequestStatus
AidlCameraDeviceSession::RequestStreamBuffers(
    const std::vector<google_camera_hal::BufferRequest>& hal_buffer_requests,
    std::vector<google_camera_hal::BufferReturn>* hal_buffer_returns) {
  std::shared_lock lock(aidl_device_callback_lock_);
  if (aidl_device_callback_ == nullptr) {
    ALOGE("%s: aidl_device_callback_ is nullptr", __FUNCTION__);
    return google_camera_hal::BufferRequestStatus::kFailedUnknown;
  }

  if (hal_buffer_returns == nullptr) {
    ALOGE("%s: hal_buffer_returns is nullptr", __FUNCTION__);
    return google_camera_hal::BufferRequestStatus::kFailedUnknown;
  }

  std::vector<BufferRequest> aidl_buffer_requests;
  status_t res = aidl_utils::ConvertToAidlBufferRequest(hal_buffer_requests,
                                                        &aidl_buffer_requests);
  if (res != OK) {
    ALOGE("%s: Converting to Aidl buffer request failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return google_camera_hal::BufferRequestStatus::kFailedUnknown;
  }

  BufferRequestStatus aidl_status;
  std::vector<StreamBufferRet> stream_buffer_returns;
  auto cb_status = aidl_device_callback_->requestStreamBuffers(
      aidl_buffer_requests, &stream_buffer_returns, &aidl_status);

  if (!cb_status.isOk()) {
    ALOGE("%s: Transaction request stream buffers error: %s", __FUNCTION__,
          cb_status.getMessage());
    return google_camera_hal::BufferRequestStatus::kFailedUnknown;
  }

  google_camera_hal::BufferRequestStatus hal_buffer_request_status;
  res = aidl_utils::ConvertToHalBufferRequestStatus(aidl_status,
                                                    &hal_buffer_request_status);
  if (res != OK) {
    ALOGE("%s: Converting to Hal buffer request status failed: %s(%d)",
          __FUNCTION__, strerror(-res), res);
    return google_camera_hal::BufferRequestStatus::kFailedUnknown;
  }

  hal_buffer_returns->clear();
  // Converting AIDL stream buffer returns to HAL stream buffer returns.
  for (auto& stream_buffer_return : stream_buffer_returns) {
    google_camera_hal::BufferReturn hal_buffer_return;
    res = aidl_utils::ConvertToHalBufferReturnStatus(stream_buffer_return,
                                                     &hal_buffer_return);
    if (res != OK) {
      ALOGE("%s: Converting to Hal buffer return status failed: %s(%d)",
            __FUNCTION__, strerror(-res), res);
      return google_camera_hal::BufferRequestStatus::kFailedUnknown;
    }

    using Tag = aidl::android::hardware::camera::device::StreamBuffersVal::Tag;
    if (stream_buffer_return.val.getTag() == Tag::buffers) {
      const std::vector<StreamBuffer>& aidl_buffers =
          stream_buffer_return.val.get<Tag::buffers>();
      std::vector<native_handle_t*> native_handles_to_delete;
      for (auto& aidl_buffer : aidl_buffers) {
        google_camera_hal::StreamBuffer hal_buffer = {};
        aidl_utils::ConvertToHalStreamBuffer(aidl_buffer, &hal_buffer,
                                             &native_handles_to_delete);
        if (res != OK) {
          ALOGE("%s: Converting to Hal stream buffer failed: %s(%d)",
                __FUNCTION__, strerror(-res), res);
          return google_camera_hal::BufferRequestStatus::kFailedUnknown;
        }

        if (!aidl_utils::IsAidlNativeHandleNull(aidl_buffer.acquireFence)) {
          hal_buffer.acquire_fence = dupFromAidl(aidl_buffer.acquireFence);
          if (hal_buffer.acquire_fence == nullptr) {
            ALOGE("%s: Cloning Hal stream buffer acquire fence failed",
                  __FUNCTION__);
          }
        }

        hal_buffer.release_fence = nullptr;
        // If buffer handle is not null, we need to import buffer handle and
        // return to the caller.
        if (!aidl_utils::IsAidlNativeHandleNull(aidl_buffer.buffer)) {
          native_handle_t* aidl_buffer_native_handle =
              makeFromAidl(aidl_buffer.buffer);
          if (buffer_mapper_v4_ != nullptr) {
            hal_buffer.buffer = ImportBufferHandle<
                android::hardware::graphics::mapper::V4_0::IMapper,
                android::hardware::graphics::mapper::V4_0::Error>(
                buffer_mapper_v4_, aidl_buffer_native_handle);
          } else if (buffer_mapper_v3_ != nullptr) {
            hal_buffer.buffer = ImportBufferHandle<
                android::hardware::graphics::mapper::V3_0::IMapper,
                android::hardware::graphics::mapper::V3_0::Error>(
                buffer_mapper_v3_, aidl_buffer_native_handle);
          } else {
            hal_buffer.buffer = ImportBufferHandle<
                android::hardware::graphics::mapper::V2_0::IMapper,
                android::hardware::graphics::mapper::V2_0::Error>(
                buffer_mapper_v2_, aidl_buffer_native_handle);
          }
          native_handle_delete(aidl_buffer_native_handle);
        }

        hal_buffer_return.val.buffers.push_back(hal_buffer);
      }

      cleanupHandles(native_handles_to_delete);
    }

    hal_buffer_returns->push_back(hal_buffer_return);
  }

  return hal_buffer_request_status;
}

template <class T, class U>
buffer_handle_t AidlCameraDeviceSession::ImportBufferHandle(
    const sp<T> buffer_mapper_, const hidl_handle& buffer_hidl_handle) {
  U mapper_error;
  buffer_handle_t imported_buffer_handle;

  auto hidl_res = buffer_mapper_->importBuffer(
      buffer_hidl_handle, [&](const auto& error, const auto& buffer_handle) {
        mapper_error = error;
        imported_buffer_handle = static_cast<buffer_handle_t>(buffer_handle);
      });
  if (!hidl_res.isOk() || mapper_error != U::NONE) {
    ALOGE("%s: Importing buffer failed: %s, mapper error %u", __FUNCTION__,
          hidl_res.description().c_str(), mapper_error);
    return nullptr;
  }

  return imported_buffer_handle;
}

void AidlCameraDeviceSession::ReturnStreamBuffers(
    const std::vector<google_camera_hal::StreamBuffer>& return_hal_buffers) {
  std::shared_lock lock(aidl_device_callback_lock_);
  if (aidl_device_callback_ == nullptr) {
    ALOGE("%s: aidl_device_callback_ is nullptr", __FUNCTION__);
    return;
  }

  status_t res = OK;
  std::vector<StreamBuffer> aidl_return_buffers;
  aidl_return_buffers.resize(return_hal_buffers.size());
  for (uint32_t i = 0; i < return_hal_buffers.size(); i++) {
    res = aidl_utils::ConvertToAidlStreamBuffer(return_hal_buffers[i],
                                                &aidl_return_buffers[i]);
    if (res != OK) {
      ALOGE("%s: Converting to Aidl stream buffer failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return;
    }
  }

  auto aidl_res =
      aidl_device_callback_->returnStreamBuffers(aidl_return_buffers);
  if (!aidl_res.isOk()) {
    ALOGE("%s: return stream buffers transaction failed: %s", __FUNCTION__,
          aidl_res.getMessage());
    return;
  }
}

status_t AidlCameraDeviceSession::InitializeBufferMapper() {
  buffer_mapper_v4_ =
      android::hardware::graphics::mapper::V4_0::IMapper::getService();
  if (buffer_mapper_v4_ != nullptr) {
    return OK;
  }

  buffer_mapper_v3_ =
      android::hardware::graphics::mapper::V3_0::IMapper::getService();
  if (buffer_mapper_v3_ != nullptr) {
    return OK;
  }

  buffer_mapper_v2_ =
      android::hardware::graphics::mapper::V2_0::IMapper::getService();
  if (buffer_mapper_v2_ != nullptr) {
    return OK;
  }

  ALOGE("%s: Getting buffer mapper failed.", __FUNCTION__);
  return UNKNOWN_ERROR;
}

status_t AidlCameraDeviceSession::Initialize(
    const std::shared_ptr<ICameraDeviceCallback>& callback,
    std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session,
    std::shared_ptr<AidlProfiler> aidl_profiler) {
  ATRACE_NAME("AidlCameraDeviceSession::Initialize");
  if (device_session == nullptr) {
    ALOGE("%s: device_session is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  if (aidl_profiler == nullptr) {
    ALOGE("%s: aidl_profiler is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  status_t res = CreateMetadataQueue(&request_metadata_queue_,
                                     kRequestMetadataQueueSizeBytes,
                                     "ro.vendor.camera.req.fmq.size");
  if (res != OK) {
    ALOGE("%s: Creating request metadata queue failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  res = CreateMetadataQueue(&result_metadata_queue_,
                            kResultMetadataQueueSizeBytes,
                            "ro.vendor.camera.res.fmq.size");
  if (res != OK) {
    ALOGE("%s: Creating result metadata queue failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  // Initialize buffer mapper
  res = InitializeBufferMapper();
  if (res != OK) {
    ALOGE("%s: Initialize buffer mapper failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  thermal_ = android::hardware::thermal::V2_0::IThermal::getService();
  if (thermal_ == nullptr) {
    ALOGE("%s: Getting thermal failed.", __FUNCTION__);
    // Continue without getting thermal information.
  }

  aidl_device_callback_ = callback;
  device_session_ = std::move(device_session);
  aidl_profiler_ = aidl_profiler;

  SetSessionCallbacks();
  return OK;
}

void AidlCameraDeviceSession::SetSessionCallbacks() {
  google_camera_hal::CameraDeviceSessionCallback session_callback = {
      .process_capture_result = google_camera_hal::ProcessCaptureResultFunc(
          [this](std::unique_ptr<google_camera_hal::CaptureResult> result) {
            ProcessCaptureResult(std::move(result));
          }),
      .notify = google_camera_hal::NotifyFunc(
          [this](const google_camera_hal::NotifyMessage& message) {
            NotifyHalMessage(message);
          }),
      .request_stream_buffers = google_camera_hal::RequestStreamBuffersFunc(
          [this](
              const std::vector<google_camera_hal::BufferRequest>&
                  hal_buffer_requests,
              std::vector<google_camera_hal::BufferReturn>* hal_buffer_returns) {
            return RequestStreamBuffers(hal_buffer_requests, hal_buffer_returns);
          }),
      .return_stream_buffers = google_camera_hal::ReturnStreamBuffersFunc(
          [this](const std::vector<google_camera_hal::StreamBuffer>&
                     return_hal_buffers) {
            ReturnStreamBuffers(return_hal_buffers);
          }),
  };

  google_camera_hal::ThermalCallback thermal_callback = {
      .register_thermal_changed_callback =
          google_camera_hal::RegisterThermalChangedCallbackFunc(
              [this](google_camera_hal::NotifyThrottlingFunc notify_throttling,
                     bool filter_type, google_camera_hal::TemperatureType type) {
                return RegisterThermalChangedCallback(notify_throttling,
                                                      filter_type, type);
              }),
      .unregister_thermal_changed_callback =
          google_camera_hal::UnregisterThermalChangedCallbackFunc(
              [this]() { UnregisterThermalChangedCallback(); }),
  };

  device_session_->SetSessionCallback(session_callback, thermal_callback);
}

status_t AidlCameraDeviceSession::RegisterThermalChangedCallback(
    google_camera_hal::NotifyThrottlingFunc notify_throttling, bool filter_type,
    google_camera_hal::TemperatureType type) {
  std::lock_guard<std::mutex> lock(hidl_thermal_mutex_);
  if (thermal_ == nullptr) {
    ALOGE("%s: thermal was not initialized.", __FUNCTION__);
    return NO_INIT;
  }

  if (thermal_changed_callback_ != nullptr) {
    ALOGE("%s: thermal changed callback is already registered.", __FUNCTION__);
    return ALREADY_EXISTS;
  }

  TemperatureType hidl_type;
  status_t res =
      hidl_thermal_utils::ConvertToHidlTemperatureType(type, &hidl_type);
  if (res != OK) {
    ALOGE("%s: Converting to HIDL type failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  std::unique_ptr<hidl_thermal_utils::HidlThermalChangedCallback> callback =
      hidl_thermal_utils::HidlThermalChangedCallback::Create(notify_throttling);
  thermal_changed_callback_ = callback.release();
  ThermalStatus thermal_status;
  auto hidl_res = thermal_->registerThermalChangedCallback(
      thermal_changed_callback_, filter_type, hidl_type,
      [&](ThermalStatus status) { thermal_status = status; });
  if (!hidl_res.isOk() || thermal_status.code != ThermalStatusCode::SUCCESS) {
    thermal_changed_callback_ = nullptr;
    return UNKNOWN_ERROR;
  }

  return OK;
}

void AidlCameraDeviceSession::UnregisterThermalChangedCallback() {
  std::lock_guard<std::mutex> lock(hidl_thermal_mutex_);
  if (thermal_changed_callback_ == nullptr) {
    // no-op if no thermal changed callback is registered.
    return;
  }

  if (thermal_ == nullptr) {
    ALOGE("%s: thermal was not initialized.", __FUNCTION__);
    return;
  }

  ThermalStatus thermal_status;
  auto hidl_res = thermal_->unregisterThermalChangedCallback(
      thermal_changed_callback_,
      [&](ThermalStatus status) { thermal_status = status; });
  if (!hidl_res.isOk() || thermal_status.code != ThermalStatusCode::SUCCESS) {
    ALOGW("%s: Unregstering thermal callback failed: %s", __FUNCTION__,
          thermal_status.debugMessage.c_str());
  }

  thermal_changed_callback_ = nullptr;
}

status_t AidlCameraDeviceSession::CreateMetadataQueue(
    std::unique_ptr<MetadataQueue>* metadata_queue, uint32_t default_size_bytes,
    const char* override_size_property) {
  if (metadata_queue == nullptr) {
    ALOGE("%s: metadata_queue is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  int32_t size = default_size_bytes;
  if (override_size_property != nullptr) {
    // Try to read the override size from the system property.
    size = property_get_int32(override_size_property, default_size_bytes);
    ALOGV("%s: request metadata queue size overridden to %d", __FUNCTION__,
          size);
  }

  *metadata_queue =
      std::make_unique<MetadataQueue>(static_cast<size_t>(size),
                                      /*configureEventFlagWord*/ false);
  if (!(*metadata_queue)->isValid()) {
    ALOGE("%s: Creating metadata queue (size %d) failed.", __FUNCTION__, size);
    return NO_INIT;
  }

  return OK;
}

ScopedAStatus AidlCameraDeviceSession::constructDefaultRequestSettings(
    RequestTemplate type, CameraMetadata* aidl_return) {
  ATRACE_NAME("AidlCameraDeviceSession::constructDefaultRequestSettings");
  if (aidl_return == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  aidl_return->metadata.clear();
  if (device_session_ == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  google_camera_hal::RequestTemplate hal_type;
  status_t res = aidl_utils::ConvertToHalTemplateType(type, &hal_type);
  if (res != OK) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  std::unique_ptr<google_camera_hal::HalCameraMetadata> settings = nullptr;
  res = device_session_->ConstructDefaultRequestSettings(hal_type, &settings);
  if (res != OK) {
    return aidl_utils::ConvertToAidlReturn(res);
  }

  uint32_t metadata_size = settings->GetCameraMetadataSize();
  uint8_t* settings_p = (uint8_t*)settings->ReleaseCameraMetadata();
  aidl_return->metadata.assign(settings_p, settings_p + metadata_size);
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDeviceSession::configureStreams(
    const StreamConfiguration& requestedConfiguration,
    std::vector<HalStream>* aidl_return) {
  ATRACE_NAME("AidlCameraDeviceSession::configureStreams");
  if (aidl_return == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  aidl_return->clear();
  if (device_session_ == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  auto profiler = aidl_profiler_->MakeScopedProfiler(
      AidlProfiler::ScopedType::kConfigureStream,
      device_session_->GetProfiler(aidl_profiler_->GetCameraId(),
                                   aidl_profiler_->GetLatencyFlag()),
      device_session_->GetProfiler(aidl_profiler_->GetCameraId(),
                                   aidl_profiler_->GetFpsFlag()));

  first_frame_requested_ = false;
  num_pending_first_frame_buffers_ = 0;

  google_camera_hal::StreamConfiguration hal_stream_config;
  status_t res = aidl_utils::ConvertToHalStreamConfig(requestedConfiguration,
                                                      &hal_stream_config);
  if (res != OK) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  std::vector<google_camera_hal::HalStream> hal_configured_streams;
  res = device_session_->ConfigureStreams(hal_stream_config,
                                          &hal_configured_streams);
  if (res != OK) {
    ALOGE("%s: Configuring streams failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return aidl_utils::ConvertToAidlReturn(res);
  }

  res = aidl_utils::ConvertToAidlHalStreamConfig(hal_configured_streams,
                                                 aidl_return);
  if (res != OK) {
    return aidl_utils::ConvertToAidlReturn(res);
  }
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDeviceSession::getCaptureRequestMetadataQueue(
    ::aidl::android::hardware::common::fmq::MQDescriptor<
        int8_t, SynchronizedReadWrite>* aidl_return) {
  *aidl_return = request_metadata_queue_->dupeDesc();
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDeviceSession::getCaptureResultMetadataQueue(
    ::aidl::android::hardware::common::fmq::MQDescriptor<
        int8_t, SynchronizedReadWrite>* aidl_return) {
  *aidl_return = result_metadata_queue_->dupeDesc();
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDeviceSession::processCaptureRequest(
    const std::vector<CaptureRequest>& requests,
    const std::vector<BufferCache>& cachesToRemove, int32_t* aidl_return) {
  if (aidl_return == nullptr || device_session_ == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  *aidl_return = 0;
  bool profile_first_request = false;
  if (!first_frame_requested_) {
    first_frame_requested_ = true;
    profile_first_request = true;
    ATRACE_BEGIN("AidlCameraDeviceSession::FirstRequest");
    num_pending_first_frame_buffers_ = requests[0].outputBuffers.size();
    first_request_frame_number_ = requests[0].frameNumber;
    aidl_profiler_->FirstFrameStart();
    ATRACE_ASYNC_BEGIN("first_frame", 0);
  }

  std::vector<google_camera_hal::BufferCache> hal_buffer_caches;

  status_t res =
      aidl_utils::ConvertToHalBufferCaches(cachesToRemove, &hal_buffer_caches);
  if (res != OK) {
    if (profile_first_request) {
      ATRACE_END();
    }
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  device_session_->RemoveBufferCache(hal_buffer_caches);

  // Converting AIDL requests to HAL requests.
  std::vector<native_handle_t*> handles_to_delete;
  std::vector<google_camera_hal::CaptureRequest> hal_requests;
  for (auto& request : requests) {
    google_camera_hal::CaptureRequest hal_request = {};
    res = aidl_utils::ConvertToHalCaptureRequest(
        request, request_metadata_queue_.get(), &hal_request,
        &handles_to_delete);
    if (res != OK) {
      ALOGE("%s: Converting to HAL capture request failed: %s(%d)",
            __FUNCTION__, strerror(-res), res);
      if (profile_first_request) {
        ATRACE_END();
      }
      cleanupHandles(handles_to_delete);
      return aidl_utils::ConvertToAidlReturn(res);
    }

    hal_requests.push_back(std::move(hal_request));
  }

  uint32_t num_processed_requests = 0;
  res = device_session_->ProcessCaptureRequest(hal_requests,
                                               &num_processed_requests);
  if (res != OK) {
    ALOGE(
        "%s: Processing capture request failed: %s(%d). Only processed %u"
        " out of %zu.",
        __FUNCTION__, strerror(-res), res, num_processed_requests,
        hal_requests.size());
  }
  if (num_processed_requests > INT_MAX) {
    cleanupHandles(handles_to_delete);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  *aidl_return = (int32_t)num_processed_requests;
  if (profile_first_request) {
    ATRACE_END();
  }
  cleanupHandles(handles_to_delete);
  return aidl_utils::ConvertToAidlReturn(res);
}

ScopedAStatus AidlCameraDeviceSession::signalStreamFlush(
    const std::vector<int32_t>&, int32_t) {
  // TODO(b/143902312): Implement this.
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDeviceSession::flush() {
  ATRACE_NAME("AidlCameraDeviceSession::flush");
  ATRACE_ASYNC_BEGIN("switch_mode", 0);
  if (device_session_ == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  auto profiler = aidl_profiler_->MakeScopedProfiler(
      AidlProfiler::ScopedType::kFlush,
      device_session_->GetProfiler(aidl_profiler_->GetCameraId(),
                                   aidl_profiler_->GetLatencyFlag()),
      device_session_->GetProfiler(aidl_profiler_->GetCameraId(),
                                   aidl_profiler_->GetFpsFlag()));

  status_t res = device_session_->Flush();
  if (res != OK) {
    ALOGE("%s: Flushing device failed: %s(%d).", __FUNCTION__, strerror(-res),
          res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDeviceSession::close() {
  ATRACE_NAME("AidlCameraDeviceSession::close");
  if (device_session_ != nullptr) {
    auto profiler = aidl_profiler_->MakeScopedProfiler(
        AidlProfiler::ScopedType::kClose,
        device_session_->GetProfiler(aidl_profiler_->GetCameraId(),
                                     aidl_profiler_->GetLatencyFlag()),
        device_session_->GetProfiler(aidl_profiler_->GetCameraId(),
                                     aidl_profiler_->GetFpsFlag()));
    device_session_ = nullptr;
  }
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraDeviceSession::switchToOffline(
    const std::vector<int32_t>&,
    CameraOfflineSessionInfo* out_offlineSessionInfo,
    std::shared_ptr<ICameraOfflineSession>* aidl_return) {
  *out_offlineSessionInfo = CameraOfflineSessionInfo();
  *aidl_return = nullptr;
  return ScopedAStatus::fromServiceSpecificError(
      static_cast<int32_t>(Status::INTERNAL_ERROR));
}

ScopedAStatus AidlCameraDeviceSession::isReconfigurationRequired(
    const CameraMetadata& oldSessionParams,
    const CameraMetadata& newSessionParams, bool* reconfiguration_required) {
  ATRACE_NAME("AidlCameraDeviceSession::isReconfigurationRequired");
  if (reconfiguration_required == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  *reconfiguration_required = true;
  std::unique_ptr<google_camera_hal::HalCameraMetadata> old_hal_session_metadata;
  status_t res = aidl_utils::ConvertToHalMetadata(
      0, nullptr, oldSessionParams.metadata, &old_hal_session_metadata);
  if (res != OK) {
    ALOGE("%s: Converting to old session metadata failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  std::unique_ptr<google_camera_hal::HalCameraMetadata> new_hal_session_metadata;
  res = aidl_utils::ConvertToHalMetadata(0, nullptr, newSessionParams.metadata,
                                         &new_hal_session_metadata);
  if (res != OK) {
    ALOGE("%s: Converting to new session metadata failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  res = device_session_->IsReconfigurationRequired(
      old_hal_session_metadata.get(), new_hal_session_metadata.get(),
      reconfiguration_required);

  if (res != OK) {
    ALOGE("%s: IsReconfigurationRequired failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  return ScopedAStatus::ok();
}

::ndk::SpAIBinder AidlCameraDeviceSession::createBinder() {
  auto binder = BnCameraDeviceSession::createBinder();
  AIBinder_setInheritRt(binder.get(), true);
  return binder;
}

}  // namespace implementation
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
