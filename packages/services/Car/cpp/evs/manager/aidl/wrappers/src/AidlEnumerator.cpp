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

#include "AidlEnumerator.h"

#include "AidlCamera.h"
#include "AidlDisplay.h"
#include "utils/include/Utils.h"

#include <aidl/android/hardware/automotive/evs/Rotation.h>
#include <aidl/android/hardware/automotive/evs/StreamType.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>

namespace {

using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCamera;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::IEvsEnumeratorStatusCallback;
using ::aidl::android::hardware::automotive::evs::IEvsUltrasonicsArray;
using ::aidl::android::hardware::automotive::evs::Rotation;
using ::aidl::android::hardware::automotive::evs::Stream;
using ::aidl::android::hardware::automotive::evs::StreamType;
using ::aidl::android::hardware::automotive::evs::UltrasonicsArrayDesc;
using ::aidl::android::hardware::graphics::common::BufferUsage;
using ::aidl::android::hardware::graphics::common::PixelFormat;
using ::ndk::ScopedAStatus;

struct StreamConfiguration {
    int id;
    int width;
    int height;
    PixelFormat format;
    int type;
    int framerate;
};

}  // namespace

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

AidlEnumerator::AidlEnumerator(const ::android::sp<hidlevs::V1_0::IEvsEnumerator>& service) {
    auto serviceV1 = hidlevs::V1_1::IEvsEnumerator::castFrom(service).withDefault(nullptr);
    if (!serviceV1) {
        mImpl = std::make_shared<ImplV0>(service);
    } else {
        mImpl = std::make_shared<ImplV1>(serviceV1);
    }

    if (!mImpl) {
        LOG(ERROR) << "Failed to initialize AidlEnumerator instance";
    }
}

// Methods from ::aidl::android::hardware::automotive::evs::IEvsEnumerator
ScopedAStatus AidlEnumerator::isHardware(bool* flag) {
    LOG(DEBUG) << __FUNCTION__;

    // Always returns true because this class represents a HIDL EVS HAL
    // implementation
    *flag = true;
    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::getCameraList(std::vector<CameraDesc>* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;
    return mImpl->getCameraList(_aidl_return);
}

ScopedAStatus AidlEnumerator::getStreamList(const CameraDesc& desc,
                                            std::vector<Stream>* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;

    if (desc.metadata.empty()) {
        LOG(DEBUG) << "Camera metadata is empty.";
        return ScopedAStatus::ok();
    }

    camera_metadata_t* pMetadata = const_cast<camera_metadata_t*>(
            reinterpret_cast<const camera_metadata_t*>(desc.metadata.data()));
    const size_t expectedSize = desc.metadata.size();
    if (validate_camera_metadata_structure(pMetadata, &expectedSize) != ::android::OK) {
        LOG(WARNING) << "Camera metadata is invalid.";
        return ScopedAStatus::fromServiceSpecificError(static_cast<int>(EvsResult::INVALID_ARG));
    }

    camera_metadata_entry_t streamConfig;
    if (find_camera_metadata_entry(pMetadata, ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                                   &streamConfig) != ::android::OK) {
        LOG(DEBUG) << "ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS does not exist in the camera "
                      "metadata.";
        return ScopedAStatus::ok();
    }

    const unsigned numStreamConfigs = streamConfig.count / sizeof(StreamConfiguration);
    _aidl_return->resize(numStreamConfigs);
    const StreamConfiguration* pCurrentConfig =
            reinterpret_cast<StreamConfiguration*>(streamConfig.data.i32);
    for (unsigned i = 0; i < numStreamConfigs; ++i, ++pCurrentConfig) {
        Stream current = {
                .id = pCurrentConfig->id,
                .streamType =
                        pCurrentConfig->type == ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_INPUT
                        ? StreamType::INPUT
                        : StreamType::OUTPUT,
                .width = pCurrentConfig->width,
                .height = pCurrentConfig->height,
                .format = static_cast<PixelFormat>(pCurrentConfig->format),
                .usage = BufferUsage::CAMERA_INPUT,
                .rotation = Rotation::ROTATION_0,
        };

        (*_aidl_return)[i] = std::move(current);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::closeCamera(const std::shared_ptr<IEvsCamera>& cameraObj) {
    LOG(DEBUG) << __FUNCTION__;

    if (!cameraObj) {
        LOG(WARNING) << "Ignoring a call with an invalid camera object";
        return ScopedAStatus::ok();
    }

    AidlCamera* aidlCamera = reinterpret_cast<AidlCamera*>(cameraObj.get());
    return mImpl->closeCamera(aidlCamera->getHidlCamera());
}

ScopedAStatus AidlEnumerator::openCamera(const std::string& id, const Stream& cfg,
                                         std::shared_ptr<IEvsCamera>* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;
    return mImpl->openCamera(id, cfg, _aidl_return);
}

ScopedAStatus AidlEnumerator::openDisplay(int32_t id, std::shared_ptr<IEvsDisplay>* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;
    auto hidlDisplay = mImpl->openDisplay(id);
    if (!hidlDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    mHidlDisplay = hidlDisplay;

    auto aidlDisplay = ::ndk::SharedRefBase::make<AidlDisplay>(hidlDisplay);
    mAidlDisplay = aidlDisplay;
    *_aidl_return = std::move(aidlDisplay);

    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::closeDisplay(const std::shared_ptr<IEvsDisplay>& displayToClose) {
    LOG(DEBUG) << __FUNCTION__;

    if (displayToClose != mAidlDisplay.lock()) {
        return ScopedAStatus::ok();
    }

    auto pActiveDisplay = mHidlDisplay.promote();
    if (pActiveDisplay) {
        mImpl->closeDisplay(pActiveDisplay);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::getDisplayState(DisplayState* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;

    auto pActiveDisplay = mHidlDisplay.promote();
    if (!pActiveDisplay) {
        // We don't have a live display right now
        mHidlDisplay = nullptr;
        *_aidl_return = DisplayState::NOT_OPEN;
    } else {
        *_aidl_return =
                std::move(Utils::makeFromHidl(std::move(pActiveDisplay->getDisplayState())));
    }

    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::getDisplayIdList(std::vector<uint8_t>* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;
    return mImpl->getDisplayIdList(_aidl_return);
}

ScopedAStatus AidlEnumerator::registerStatusCallback(
        [[maybe_unused]] const std::shared_ptr<IEvsEnumeratorStatusCallback>& callback) {
    // This method always returns NOT_SUPPORTED because this class wraps around
    // HIDL EVS HAL implementations, which do not support this callback
    // interface.
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
}

ScopedAStatus AidlEnumerator::getUltrasonicsArrayList(
        [[maybe_unused]] std::vector<UltrasonicsArrayDesc>* list) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_IMPLEMENTED);
}

ScopedAStatus AidlEnumerator::openUltrasonicsArray(
        [[maybe_unused]] const std::string& id,
        [[maybe_unused]] std::shared_ptr<IEvsUltrasonicsArray>* obj) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_IMPLEMENTED);
}

ScopedAStatus AidlEnumerator::closeUltrasonicsArray(
        [[maybe_unused]] const std::shared_ptr<IEvsUltrasonicsArray>& obj) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_IMPLEMENTED);
}

ScopedAStatus AidlEnumerator::ImplV0::getCameraList(std::vector<CameraDesc>* _aidl_return) {
    mHidlEnumerator->getCameraList([&_aidl_return](auto hidl_cameras) {
        _aidl_return->resize(hidl_cameras.size());
        auto it = _aidl_return->begin();
        for (const auto& camera : hidl_cameras) {
            *it++ = std::move(Utils::makeFromHidl(camera));
        }
    });

    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::ImplV0::closeCamera(
        const ::android::sp<hidlevs::V1_0::IEvsCamera>& cameraObj) {
    mHidlEnumerator->closeCamera(cameraObj);
    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::ImplV0::openCamera(const std::string& id, const Stream& /*cfg*/,
                                                 std::shared_ptr<IEvsCamera>* _aidl_return) {
    ::android::sp<hidlevs::V1_0::IEvsCamera> hidlCamera = mHidlEnumerator->openCamera(id);
    if (!hidlCamera) {
        LOG(ERROR) << "Failed to open a camera " << id;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    *_aidl_return = std::move(::ndk::SharedRefBase::make<AidlCamera>(hidlCamera));

    return ScopedAStatus::ok();
}

::android::sp<hidlevs::V1_0::IEvsDisplay> AidlEnumerator::ImplV0::openDisplay(int32_t /*id*/) {
    return mHidlEnumerator->openDisplay();
}

ScopedAStatus AidlEnumerator::ImplV0::closeDisplay(
        const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) {
    mHidlEnumerator->closeDisplay(display);
    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::ImplV0::getDisplayIdList(std::vector<uint8_t>* /*_aidl_return*/) {
    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::ImplV1::closeCamera(
        const ::android::sp<hidlevs::V1_0::IEvsCamera>& cameraObj) {
    mHidlEnumerator->closeCamera(cameraObj);
    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::ImplV1::getCameraList(std::vector<CameraDesc>* _aidl_return) {
    mHidlEnumerator->getCameraList_1_1([&_aidl_return](auto hidl_cameras) {
        _aidl_return->resize(hidl_cameras.size());
        auto it = _aidl_return->begin();
        for (const auto& camera : hidl_cameras) {
            *it++ = std::move(Utils::makeFromHidl(camera));
        }
    });

    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::ImplV1::openCamera(const std::string& id, const Stream& cfg,
                                                 std::shared_ptr<IEvsCamera>* _aidl_return) {
    auto hidlStreamConfig = std::move(Utils::makeToHidl(cfg));
    ::android::sp<hidlevs::V1_1::IEvsCamera> hidlCamera =
            mHidlEnumerator->openCamera_1_1(id, hidlStreamConfig);
    if (!hidlCamera) {
        LOG(ERROR) << "Failed to open a camera " << id;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    *_aidl_return = std::move(::ndk::SharedRefBase::make<AidlCamera>(hidlCamera));

    return ScopedAStatus::ok();
}

::android::sp<hidlevs::V1_0::IEvsDisplay> AidlEnumerator::ImplV1::openDisplay(int32_t id) {
    ::android::sp<hidlevs::V1_1::IEvsDisplay> hidlDisplay = mHidlEnumerator->openDisplay_1_1(id);
    return hidlDisplay;
}

ScopedAStatus AidlEnumerator::ImplV1::closeDisplay(
        const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) {
    mHidlEnumerator->closeDisplay(display);
    return ScopedAStatus::ok();
}

ScopedAStatus AidlEnumerator::ImplV1::getDisplayIdList(std::vector<uint8_t>* _aidl_return) {
    mHidlEnumerator->getDisplayIdList(
            [&_aidl_return](auto& list) { *_aidl_return = std::move(list); });
    return ScopedAStatus::ok();
}

}  // namespace aidl::android::automotive::evs::implementation
