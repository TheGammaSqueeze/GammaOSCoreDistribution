/*
 * Copyright 2021 The Android Open Source Project
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

#include "EvsServiceContext.h"

#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidl/android/hardware/graphics/common/HardwareBuffer.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#include <android-base/logging.h>
#include <android-base/scopeguard.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/hardware_buffer_jni.h>  // for AHardwareBuffer_toHardwareBuffer
#include <cutils/native_handle.h>
#include <nativehelper/JNIHelp.h>
#include <vndk/hardware_buffer.h>  // for AHARDWAREBUFFER_CREATE_FROM_HANDLE_METHOD_CLONE

namespace {

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCamera;
using ::aidl::android::hardware::automotive::evs::IEvsEnumerator;
using ::aidl::android::hardware::automotive::evs::Stream;
using ::aidl::android::hardware::automotive::evs::StreamType;
using ::aidl::android::hardware::common::NativeHandle;
using ::aidl::android::hardware::graphics::common::HardwareBuffer;
using AidlPixelFormat = ::aidl::android::hardware::graphics::common::PixelFormat;

jmethodID getMethodIDOrDie(JNIEnv* env, jclass clazz, const char* name, const char* signature) {
    jmethodID res = env->GetMethodID(clazz, name, signature);
    if (res == nullptr) {
        LOG(FATAL) << "Unable to find method " << name << " with signature = " << signature;
    }

    return res;
}

Stream selectStreamConfiguration(const std::vector<Stream>& list) {
    for (const auto& cfg : list) {
        // TODO(b/223905367): this logic simply selects the first output stream
        // configuration that generates RGBA8888 data stream.
        if (cfg.streamType == StreamType::OUTPUT && cfg.format == AidlPixelFormat::RGBA_8888) {
            LOG(INFO) << "Selected stream configuration: width = " << cfg.width
                      << ", height = " << cfg.height
                      << ", format = " << static_cast<int>(cfg.format);
            return std::move(cfg);
        }
    }

    return {};
}

native_handle_t* makeFromAidl(const NativeHandle& handle) {
    // Create native_handle_t from
    // ::aidl::android::hardware::common::NativeHandle.  See also
    // ::android::makeFromAidl() and native_handle_create().
    const auto numFds = handle.fds.size();
    const auto numInts = handle.ints.size();

    if (numFds < 0 || numInts < 0 || numFds > NATIVE_HANDLE_MAX_FDS ||
        numInts > NATIVE_HANDLE_MAX_INTS) {
        return nullptr;
    }

    const auto mallocSize = sizeof(native_handle_t) + (sizeof(int) * (numFds + numInts));
    native_handle_t* h = static_cast<native_handle_t*>(malloc(mallocSize));
    if (h == nullptr) {
        return nullptr;
    }

    h->version = sizeof(native_handle_t);
    h->numFds = numFds;
    h->numInts = numInts;
    for (auto i = 0; i < handle.fds.size(); ++i) {
        h->data[i] = handle.fds[i].get();
    }
    memcpy(h->data + handle.fds.size(), handle.ints.data(), handle.ints.size() * sizeof(int));

    return h;
}

// "default" is reserved for the latest version of EVS manager.
constexpr const char kEvsManagerServiceName[] =
        "android.hardware.automotive.evs.IEvsEnumerator/default";

}  // namespace

namespace android::automotive::evs {

EvsServiceContext::EvsServiceContext(JavaVM* vm, jclass clazz) :
      mVm(vm), mCallbackThread(vm), mCarEvsServiceObj(nullptr) {
    JNIEnv* env = nullptr;
    vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_4);
    if (env != nullptr) {
        // Registers post-native handlers
        mDeathHandlerMethodId = getMethodIDOrDie(env, clazz, "postNativeDeathHandler", "()V");
        mEventHandlerMethodId = getMethodIDOrDie(env, clazz, "postNativeEventHandler", "(I)V");
        mFrameHandlerMethodId = getMethodIDOrDie(env, clazz, "postNativeFrameHandler",
                                                 "(ILandroid/hardware/HardwareBuffer;)V");
    } else {
        jniThrowException(env, "java/lang/IllegalArgumentException",
                          "Failed to get JNIEnv from a given VM instance.");
    }
}

EvsServiceContext::~EvsServiceContext() {
    {
        std::lock_guard<std::mutex> lock(mLock);
        if (mService) {
            ::AIBinder_DeathRecipient_delete(mDeathRecipient.get());
        }
        mService = nullptr;
        mCamera = nullptr;
        mStreamHandler = nullptr;
    }

    // Stops the callback thread
    mCallbackThread.stop();

    // Deletes a global reference to the CarEvsService object
    JNIEnv* env = nullptr;
    if (mVm != nullptr) {
        mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_4);
        if (env != nullptr) {
            env->DeleteGlobalRef(mCarEvsServiceObj);
        }
    }
}

bool EvsServiceContext::initialize(JNIEnv* env, jobject thiz) {
    bool isDeclared = ::AServiceManager_isDeclared(kEvsManagerServiceName);
    if (!isDeclared) {
        LOG(ERROR) << kEvsManagerServiceName << " is not available.";
        return false;
    }

    AIBinder* binder = ::AServiceManager_checkService(kEvsManagerServiceName);
    if (binder == nullptr) {
        LOG(ERROR) << "IEvsEnumerator is not ready yet.";
        return false;
    }

    std::shared_ptr<IEvsEnumerator> service = IEvsEnumerator::fromBinder(::ndk::SpAIBinder(binder));
    if (!service) {
        LOG(ERROR) << "Failed to connect to EVS service.";
        return false;
    }

    auto deathRecipient = ::AIBinder_DeathRecipient_new(EvsServiceContext::onEvsServiceBinderDied);
    auto status = ::ndk::ScopedAStatus::fromStatus(
            ::AIBinder_linkToDeath(service->asBinder().get(), deathRecipient, this));
    if (!status.isOk()) {
        LOG(WARNING) << "Failed to register a death recipient; continuing anyway: "
                     << status.getMessage();
    }

    {
        std::lock_guard<std::mutex> lock(mLock);
        mService = service;
        mDeathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(deathRecipient);
        if (!mCarEvsServiceObj) {
            mCarEvsServiceObj = env->NewGlobalRef(thiz);
        }

        // Reset a stored camera id and a display handle
        mCameraIdInUse.clear();
        mDisplay = nullptr;
    }

    // Fetch a list of available camera devices
    status = service->getCameraList(&mCameraList);
    if (!status.isOk()) {
        LOG(ERROR) << "Failed to load a camera list, error = " << status.getServiceSpecificError();
        return false;
    } else if (mCameraList.size() < 1) {
        LOG(ERROR) << "No camera device is available";
        return false;
    }

    LOG(INFO) << mCameraList.size() << " camera devices are listed.";
    return true;
}

bool EvsServiceContext::openCamera(const char* id) {
    if (!isAvailable()) {
        LOG(ERROR) << "Has not connected to EVS service yet.";
        return false;
    }

    if (isCameraOpened()) {
        if (mCameraIdInUse == id) {
            LOG(DEBUG) << "Camera " << id << " is has opened already.";
            return true;
        } else {
            std::lock_guard<std::mutex> lock(mLock);
            if (mService) {
                // Close a current camera device.
                if (!mService->closeCamera(mCamera).isOk()) {
                    LOG(WARNING) << "Failed to close a current camera device";
                }
            }
        }
    }

    auto it = std::find_if(mCameraList.begin(), mCameraList.end(),
                           [target = std::string(id)](const CameraDesc& desc) {
                               return target == desc.id;
                           });
    if (it == mCameraList.end()) {
        LOG(ERROR) << id << " is not available";
        return false;
    }

    std::vector<Stream> availableStreams;
    {
        std::lock_guard<std::mutex> lock(mLock);
        if (!mService) {
            return false;
        }
        mService->getStreamList(*it, &availableStreams);

        Stream streamConfig = selectStreamConfiguration(availableStreams);
        std::shared_ptr<IEvsCamera> camObj;
        if (!mService || !mService->openCamera(id, streamConfig, &camObj).isOk() || !camObj) {
            LOG(ERROR) << "Failed to open a camera " << id;
            return false;
        }

        std::shared_ptr<StreamHandler> streamHandler =
                ::ndk::SharedRefBase::make<StreamHandler>(camObj, this,
                                                          EvsServiceContext::kMaxNumFramesInFlight);
        if (!streamHandler) {
            LOG(ERROR) << "Failed to initialize a stream streamHandler.";
            if (!mService->closeCamera(camObj).isOk()) {
                LOG(ERROR) << "Failed to close a temporary camera device";
            }
            return false;
        }

        mCamera = std::move(camObj);
        mStreamHandler = std::move(streamHandler);
        mCameraIdInUse = id;
    }

    return true;
}

void EvsServiceContext::closeCamera() {
    if (!isCameraOpened()) {
        LOG(DEBUG) << "Camera has not opened yet.";
        return;
    }

    {
        std::lock_guard<std::mutex> lock(mLock);
        if (!mService->closeCamera(mCamera).isOk()) {
            LOG(WARNING) << "Failed to close a current camera device.";
        }
    }

    // Reset a camera reference and id in use.
    mCamera.reset();
    mCameraIdInUse.clear();
}

bool EvsServiceContext::startVideoStream() {
    if (!isCameraOpened()) {
        LOG(ERROR) << "Camera has not opened yet.";
        return JNI_FALSE;
    }

    return mStreamHandler->startStream();
}

void EvsServiceContext::stopVideoStream() {
    if (!isCameraOpened()) {
        LOG(DEBUG) << "Camera has not opened; a request to stop a video steram is ignored.";
        return;
    }

    if (!mStreamHandler->asyncStopStream()) {
        LOG(WARNING) << "Failed to stop a video stream.  EVS service may die.";
    }
}

void EvsServiceContext::acquireCameraAndDisplayLocked() {
    if (!mCamera) {
        LOG(DEBUG) << "A target camera is not available.";
        return;
    }

    // Acquires the display ownership.  Because EVS awards this to the single
    // client, no other clients can use EvsDisplay as long as CarEvsManager
    // alives.
    ::ndk::ScopedAStatus status =
            mService->openDisplay(EvsServiceContext::kExclusiveMainDisplayId, &mDisplay);
    if (!status.isOk() || !mDisplay) {
        LOG(WARNING) << "Failed to acquire the display ownership.  "
                     << "CarEvsManager may not be able to render "
                     << "the contents on the screen.";
        return;
    }

    // Attempts to become a primary owner
    status = mCamera->forcePrimaryClient(mDisplay);
    if (!status.isOk() ||
        static_cast<EvsResult>(status.getServiceSpecificError()) != EvsResult::OK) {
        LOG(WARNING) << "Failed to own a camera device: " << status.getMessage();
    }
}

void EvsServiceContext::doneWithFrame(int bufferId) {
    {
        std::lock_guard<std::mutex> lock(mLock);
        if (!mStreamHandler) {
            LOG(DEBUG) << "A stream handler is not available.";
            return;
        }

        auto it = mBufferRecords.find(bufferId);
        if (it == mBufferRecords.end()) {
            LOG(WARNING) << "Unknown buffer is requested to return.";
            return;
        }

        mBufferRecords.erase(it);

        // If this is the first frame since current video stream started, we'd claim
        // the exclusive ownership of the camera and the display and keep for the rest
        // of the lifespan.
        if (!mDisplay) {
            acquireCameraAndDisplayLocked();
        }
    }
    mStreamHandler->doneWithFrame(bufferId);
}

/*
 * Forwards EVS stream events to the client.  This method will run in the
 * context of EvsCallbackThread.
 */
void EvsServiceContext::onNewEvent(const EvsEventDesc& event) {
    mCallbackThread.enqueue([event, this](JNIEnv* env) {
        // Gives an event callback
        env->CallVoidMethod(mCarEvsServiceObj, mEventHandlerMethodId,
                            static_cast<jint>(event.aType));
    });
}

/*
 * Forwards EVS frames to the client.  This method will run in the context of
 * EvsCallbackThread.
 */
bool EvsServiceContext::onNewFrame(const BufferDesc& bufferDesc) {
    // Create AHardwareBuffer from ::aidl::android::hardware::automotive::evs::BufferDesc
    native_handle_t* nativeHandle = makeFromAidl(bufferDesc.buffer.handle);
    const auto handleGuard = ::android::base::make_scope_guard([nativeHandle] {
        // We only need to free an allocated memory because a source buffer is
        // owned by EVS HAL implementation.
        free(nativeHandle);
    });

    if (nativeHandle == nullptr ||
        !std::all_of(nativeHandle->data + 0, nativeHandle->data + nativeHandle->numFds,
                     [](int fd) { return fd >= 0; })) {
        LOG(ERROR) << " android::makeFromAidl returned an invalid native handle";
        return false;
    }

    const AHardwareBuffer_Desc desc{
            .width = static_cast<uint32_t>(bufferDesc.buffer.description.width),
            .height = static_cast<uint32_t>(bufferDesc.buffer.description.height),
            .layers = static_cast<uint32_t>(bufferDesc.buffer.description.layers),
            .format = static_cast<uint32_t>(bufferDesc.buffer.description.format),
            .usage = static_cast<uint64_t>(bufferDesc.buffer.description.usage),
            .stride = static_cast<uint32_t>(bufferDesc.buffer.description.stride),
    };

    AHardwareBuffer* ahwb = nullptr;
    const auto status =
            AHardwareBuffer_createFromHandle(&desc, nativeHandle,
                                             AHARDWAREBUFFER_CREATE_FROM_HANDLE_METHOD_CLONE,
                                             &ahwb);
    if (status != android::NO_ERROR) {
        LOG(ERROR) << "Failed to create a raw hardware buffer from a native handle, "
                   << "status = " << statusToString(status);
        mStreamHandler->doneWithFrame(bufferDesc.bufferId);
        return false;
    }

    mCallbackThread.enqueue([ahwb, bufferId = bufferDesc.bufferId, this](JNIEnv* env) {
        {
            std::lock_guard lock(mLock);
            mBufferRecords.insert(bufferId);
        }

        // Forward AHardwareBuffer to the client
        jobject hwBuffer = AHardwareBuffer_toHardwareBuffer(env, ahwb);
        if (!hwBuffer) {
            LOG(WARNING) << "Failed to create HardwareBuffer from AHardwareBuffer.";
            mStreamHandler->doneWithFrame(bufferId);
        } else {
            env->CallVoidMethod(mCarEvsServiceObj, mFrameHandlerMethodId, bufferId, hwBuffer);
            env->DeleteLocalRef(hwBuffer);
        }

        // We're done
        AHardwareBuffer_release(ahwb);
    });

    return true;
}

/*
 * Handles an unexpected death of EVS service.  This method will run in the
 * context of EvsCallbackThread.
 */
void EvsServiceContext::onEvsServiceDiedImpl() {
    mCallbackThread.enqueue([this](JNIEnv* env) {
        // Drops invalidated service handles.  We will re-initialize them when
        // we try to reconnect.  The buffer record would be cleared safely
        // because all buffer references get invalidated upon the death of the
        // native EVS service.
        {
            std::lock_guard<std::mutex> lock(mLock);
            mCamera = nullptr;
            mService = nullptr;
            mStreamHandler = nullptr;
            mBufferRecords.clear();
            mCameraIdInUse.clear();
        }

        LOG(ERROR) << "The native EVS service has died.";
        // EVS service has died but CarEvsManager instance still alives.
        // Only a service handle needs to be destroyed; this will be
        // re-created when CarEvsManager successfully connects to EVS service
        // when it comes back.
        env->CallVoidMethod(mCarEvsServiceObj, mDeathHandlerMethodId);
    });
}

void EvsServiceContext::onEvsServiceBinderDied(void* cookie) {
    auto thiz = static_cast<EvsServiceContext*>(cookie);
    if (!thiz) {
        LOG(WARNING) << "A death of the EVS service is detected but ignored "
                     << "because of the invalid service context.";
        return;
    }

    thiz->onEvsServiceDiedImpl();
}

}  // namespace android::automotive::evs
