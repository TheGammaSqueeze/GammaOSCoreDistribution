#ifndef ROCKCHIP_HARDWARE_TV_INPUT_V1_0_TVINPUT_H
#define ROCKCHIP_HARDWARE_TV_INPUT_V1_0_TVINPUT_H

#include <hidl/Status.h>
#include <string>

#include <hardware/tv_input.h>
#include <hidl/MQDescriptor.h>

#include <rockchip/hardware/tv/input/1.0/ITvInput.h>
#include "TvInput.h"

namespace rockchip {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::tv::input::V1_0::ITvInputCallback;
using ::android::hardware::tv::input::V1_0::CableConnectionStatus;
using ::android::hardware::tv::input::V1_0::Result;
using ::android::hardware::tv::input::V1_0::TvInputType;
using ::rockchip::hardware::tv::input::V1_0::PreviewRequest;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

using android::hardware::hidl_handle;
using HwITvInput = ::android::hardware::tv::input::V1_0::ITvInput;
using ICallbackExt = ::rockchip::hardware::tv::input::V1_0::ITvInputCallback;
using HwTvInputEvent = ::android::hardware::tv::input::V1_0::TvInputEvent;

struct TvInputExt : public ::rockchip::hardware::tv::input::V1_0::ITvInput {
    TvInputExt(HwITvInput*&& tvInput);
    ~TvInputExt();
    // Methods from ::rockchip::hardware::tv::input::1.0::ITvInput follow.
    Return<void> setCallback(const sp<ITvInputCallback>& callback) override {
        return mTvInput->setCallback(callback);
    }
    Return<void> getStreamConfigurations(int32_t deviceId,
            getStreamConfigurations_cb _hidl_cb) override {
        return mTvInput->getStreamConfigurations(deviceId, _hidl_cb);
    }
    Return<void> openStream(int32_t deviceId, int32_t streamId,
            openStream_cb _hidl_cb) override {
        return mTvInput->openStream(deviceId, streamId, _hidl_cb);
    }
    Return<Result> closeStream(int32_t deviceId, int32_t streamId) override {
        return mTvInput->closeStream(deviceId, streamId);
    }

    // Methods from ::rockchip::hardware::tv::input::V1_0::ITvInput follow.
    Return<void> setExtCallback(const sp<ICallbackExt>& callback) override;
    Return<Result> privCmdFromApp(const PrivAppCmdInfo& cmdInfo) override;
    Return<Result> requestCapture(int32_t deviceId, int32_t streamId,
            uint64_t buffId, const hidl_handle& buffer, int32_t seq) override;
    Return<void> cancelCapture(int32_t deviceId, int32_t streamId,
            int32_t seq) override;
    Return<Result> setPreviewInfo(int32_t deviceId, int32_t streamId,
            int32_t top, int32_t left, int32_t width, int32_t height,
            int32_t extInfo) override;
    Return<void> setSinglePreviewBuffer(const PreviewBuffer& buff) override;
    Return<void> getStreamConfigurations_ext(int32_t deviceId,
            getStreamConfigurations_ext_cb _hidl_cb) override;
    Return<void> openStream_ext(int32_t deviceId, int32_t streamId, int32_t streamType,
            openStream_ext_cb _hidl_cb) override;
    static void notify_ext(struct tv_input_device* __unused, tv_input_event_ext_t* event,
            void* __unused);

private:
    std::unique_ptr<HwITvInput> mTvInput;
    static sp<ICallbackExt> mExtCallback;
    tv_input_callback_ops_ext_t mCallbackOpsExt;
    tv_input_device_t* mDevice;
};

} // namespace implementation
} // namespace V1_0
} // namespace input
} // namespace tv
} // namespace hardware
} // namespace rockchip

#endif // ROCKCHIP_HARDWARE_TV_INPUT_V1_0_TVINPUT_H
