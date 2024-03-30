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

#include "HidlCamera.h"

#include "AidlCameraStream.h"
#include "AidlDisplay.h"
#include "utils/include/Utils.h"

#include <android-base/logging.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCameraStream;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::ParameterRange;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::ndk::ScopedAStatus;

HidlCamera::~HidlCamera() {
    mAidlCamera = nullptr;
    mAidlStream = nullptr;
}

Return<void> HidlCamera::getCameraInfo(getCameraInfo_cb _hidl_cb) {
    CameraDesc aidlDesc;
    if (auto status = mAidlCamera->getCameraInfo(&aidlDesc); !status.isOk()) {
        LOG(WARNING) << "Failed to get a camera information, status = "
                     << status.getServiceSpecificError();
    }

    _hidl_cb(std::move(Utils::makeToHidlV1_0(aidlDesc)));
    return {};
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::setMaxFramesInFlight(uint32_t bufferCount) {
    auto status = mAidlCamera->setMaxFramesInFlight(static_cast<int32_t>(bufferCount));
    if (!status.isOk()) {
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::startVideoStream(
        const ::android::sp<hidlevs::V1_0::IEvsCameraStream>& stream) {
    if (!stream) {
        return hidlevs::V1_0::EvsResult::INVALID_ARG;
    } else if (mAidlStream) {
        return hidlevs::V1_0::EvsResult::STREAM_ALREADY_RUNNING;
    }

    // Creates a wrapper object and requests a video stream
    mAidlStream = ::ndk::SharedRefBase::make<AidlCameraStream>(stream);
    if (auto status = mAidlCamera->startVideoStream(mAidlStream); !status.isOk()) {
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<void> HidlCamera::doneWithFrame(const hidlevs::V1_0::BufferDesc& buffer) {
    BufferDesc aidlBuffer;
    if (!mAidlStream->getBuffer(buffer.bufferId, &aidlBuffer)) {
        LOG(WARNING) << "Ignores an unknown buffer " << buffer.bufferId;
        return {};
    }

    std::vector<BufferDesc> buffersToReturn(1);
    buffersToReturn[0] = std::move(aidlBuffer);
    if (auto status = mAidlCamera->doneWithFrame(std::move(buffersToReturn)); !status.isOk()) {
        LOG(WARNING) << "Failed to return a buffer " << aidlBuffer.bufferId
                     << ", status = " << status.getServiceSpecificError();
    }

    return {};
}

Return<void> HidlCamera::stopVideoStream() {
    if (!mAidlStream) {
        return {};
    }

    mAidlCamera->stopVideoStream();
    return {};
}

Return<int32_t> HidlCamera::getExtendedInfo(uint32_t opaqueIdentifier) {
    std::vector<uint8_t> value;
    if (!mAidlCamera->getExtendedInfo(static_cast<int32_t>(opaqueIdentifier), &value).isOk()) {
        return 0;
    }

    return *reinterpret_cast<int32_t*>(value.data());
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::setExtendedInfo(uint32_t opaqueIdentifier,
                                                             int32_t opaqueValue) {
    std::vector<uint8_t> value;
    *reinterpret_cast<int32_t*>(value.data()) = opaqueValue;
    auto status = mAidlCamera->setExtendedInfo(static_cast<int32_t>(opaqueIdentifier), value);
    if (!status.isOk()) {
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }
    return hidlevs::V1_0::EvsResult::OK;
}

// Methods from ::android::hardware::automotive::evs::V1_1::IEvsCamera follow.
Return<void> HidlCamera::getCameraInfo_1_1(getCameraInfo_1_1_cb _hidl_cb) {
    CameraDesc aidlDesc;
    if (auto status = mAidlCamera->getCameraInfo(&aidlDesc); !status.isOk()) {
        LOG(WARNING) << "Failed to get a camera information, status = "
                     << status.getServiceSpecificError();
        return {};
    }

    _hidl_cb(std::move(Utils::makeToHidlV1_1(aidlDesc)));
    return {};
}

Return<void> HidlCamera::getPhysicalCameraInfo(const hidl_string& deviceId,
                                               getPhysicalCameraInfo_cb _hidl_cb) {
    CameraDesc aidlDesc;
    if (auto status = mAidlCamera->getPhysicalCameraInfo(deviceId, &aidlDesc); !status.isOk()) {
        LOG(WARNING) << "Failed to read information of a camera " << deviceId
                     << ", status = " << status.getServiceSpecificError();
        _hidl_cb({});
    } else {
        _hidl_cb(Utils::makeToHidlV1_1(aidlDesc));
    }

    return {};
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::doneWithFrame_1_1(
        const hidl_vec<hidlevs::V1_1::BufferDesc>& buffers) {
    std::vector<BufferDesc> buffersToReturn(buffers.size());
    for (auto i = 0; i < buffers.size(); ++i) {
        BufferDesc aidlBuffer;
        if (!mAidlStream->getBuffer(buffers[i].bufferId, &aidlBuffer)) {
            LOG(WARNING) << "Ignores an unknown buffer " << buffers[i].bufferId;
            continue;
        }

        buffersToReturn[i] = std::move(aidlBuffer);
    }

    if (auto status = mAidlCamera->doneWithFrame(std::move(buffersToReturn)); !status.isOk()) {
        LOG(ERROR) << "Failed to return buffers, status = " << status.getServiceSpecificError();
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::setMaster() {
    if (auto status = mAidlCamera->setPrimaryClient(); !status.isOk()) {
        EvsResult err = static_cast<EvsResult>(status.getServiceSpecificError());
        if (err == EvsResult::PERMISSION_DENIED) {
            // HIDL EvsManager implementations return EvsResult::OWNERSHIP_LOST
            // if the primary client exists already.
            err = EvsResult::OWNERSHIP_LOST;
        }
        return Utils::makeToHidl(err);
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::forceMaster(
        const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) {
    auto status = mAidlCamera->forcePrimaryClient(::ndk::SharedRefBase::make<AidlDisplay>(display));
    if (!status.isOk()) {
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::unsetMaster() {
    if (auto status = mAidlCamera->unsetPrimaryClient(); !status.isOk()) {
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<void> HidlCamera::getParameterList(getParameterList_cb _hidl_cb) {
    std::vector<CameraParam> aidlList;
    if (auto status = mAidlCamera->getParameterList(&aidlList); !status.isOk()) {
        LOG(WARNING) << "Failed to get a parameter list, status = "
                     << status.getServiceSpecificError();
        _hidl_cb({});
        return {};
    }

    hidl_vec<hidlevs::V1_1::CameraParam> hidlList;
    hidlList.resize(aidlList.size());
    for (auto i = 0; i < aidlList.size(); ++i) {
        hidlList[i] = Utils::makeToHidl(aidlList[i]);
    }
    _hidl_cb(hidlList);
    return {};
}

Return<void> HidlCamera::getIntParameterRange(hidlevs::V1_1::CameraParam id,
                                              getIntParameterRange_cb _hidl_cb) {
    ParameterRange aidlRange;
    if (auto status = mAidlCamera->getIntParameterRange(Utils::makeFromHidl(id), &aidlRange);
        !status.isOk()) {
        _hidl_cb(0, 0, 0);
        return {};
    }

    _hidl_cb(aidlRange.min, aidlRange.max, aidlRange.step);
    return {};
}

Return<void> HidlCamera::setIntParameter(hidlevs::V1_1::CameraParam id, int32_t value,
                                         setIntParameter_cb _hidl_cb) {
    std::vector<int32_t> aidlValues;
    auto status = mAidlCamera->setIntParameter(Utils::makeFromHidl(id), value, &aidlValues);
    if (!status.isOk()) {
        EvsResult err = static_cast<EvsResult>(status.getServiceSpecificError());
        if (err == EvsResult::PERMISSION_DENIED) {
            // HIDL EvsManager implementations return EvsResult::INVALID_ARG if
            // the client is not permitted to change parameters.
            err = EvsResult::INVALID_ARG;
        }
        _hidl_cb(Utils::makeToHidl(err), {value});
        return {};
    }

    _hidl_cb(hidlevs::V1_0::EvsResult::OK, aidlValues);
    return {};
}

Return<void> HidlCamera::getIntParameter(hidlevs::V1_1::CameraParam id,
                                         getIntParameter_cb _hidl_cb) {
    std::vector<int32_t> aidlValues;
    auto status = mAidlCamera->getIntParameter(Utils::makeFromHidl(id), &aidlValues);
    if (!status.isOk()) {
        _hidl_cb(Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError())), {});
        return {};
    }

    _hidl_cb(hidlevs::V1_0::EvsResult::OK, aidlValues);
    return {};
}

Return<hidlevs::V1_0::EvsResult> HidlCamera::setExtendedInfo_1_1(
        uint32_t opaqueIdentifier, const hidl_vec<uint8_t>& opaqueValue) {
    std::vector<uint8_t> value(opaqueValue);
    auto status = mAidlCamera->setExtendedInfo(static_cast<int32_t>(opaqueIdentifier), value);
    if (!status.isOk()) {
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<void> HidlCamera::getExtendedInfo_1_1(uint32_t opaqueIdentifier,
                                             getExtendedInfo_1_1_cb _hidl_cb) {
    std::vector<uint8_t> value;
    auto status = mAidlCamera->getExtendedInfo(static_cast<int32_t>(opaqueIdentifier), &value);
    if (!status.isOk()) {
        _hidl_cb(Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError())), {});
    } else {
        _hidl_cb(hidlevs::V1_0::EvsResult::OK, value);
    }

    return {};
}

Return<void> HidlCamera::importExternalBuffers(const hidl_vec<hidlevs::V1_1::BufferDesc>& buffers,
                                               importExternalBuffers_cb _hidl_cb) {
    std::vector<BufferDesc> aidlBuffers(buffers.size());
    for (auto i = 0; i < buffers.size(); ++i) {
        aidlBuffers[i] = std::move(Utils::makeFromHidl(buffers[i]));
    }

    int32_t delta = 0;
    if (auto status = mAidlCamera->importExternalBuffers(aidlBuffers, &delta); !status.isOk()) {
        _hidl_cb(Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError())),
                 delta);
    } else {
        _hidl_cb(hidlevs::V1_0::EvsResult::OK, delta);
    }

    return {};
}

}  // namespace aidl::android::automotive::evs::implementation
