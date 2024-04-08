#include <hardware/tv_input.h>
#include <android-base/logging.h>

#include "TvInputExt.h"

#ifndef container_of
#define container_of(ptr, type, member) \
    (type *)((char*)(ptr) - offsetof(type, member))
#endif

namespace rockchip {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {
namespace implementation {

static_assert(TV_INPUT_EVENT_CAPTURE_SUCCEEDED == static_cast<int>(
    TvInputEventType::STREAM_CAPTURE_SUCCEEDED),
    "TvInputEventType::STREAM_CAPTURE_SUCCEEDED must match legacy value.");
static_assert(TV_INPUT_EVENT_CAPTURE_FAILED == static_cast<int>(
    TvInputEventType::STREAM_CAPTURE_FAILED),
    "TvInputEventType::STREAM_CAPTURE_FAILED must match legacy value.");
static_assert(TV_INPUT_EVENT_PRIV_CMD_TO_APP == static_cast<int>(
    TvInputEventType::PRIV_CMD_TO_APP),
    "TvInputEventType::STREAM_CAPTURE_FAILED must match legacy value.");

sp<ICallbackExt> TvInputExt::mExtCallback = nullptr;

// Get implementation from tv_input.xxx.so
TvInputExt::TvInputExt(HwITvInput*&& tvInput) : mTvInput(tvInput) {
    int ret = 0;
    const hw_module_t* hw_module = nullptr;
    tv_input_device_t* input_device;
    ret = hw_get_module(TV_INPUT_HARDWARE_MODULE_ID, &hw_module);
    if (ret == 0 && hw_module->methods->open != nullptr) {
        ret = hw_module->methods->open(hw_module, TV_INPUT_DEFAULT_DEVICE,
                reinterpret_cast<hw_device_t**>(&input_device));
        if (ret != 0) {
            LOG(ERROR) << "Failed to acquire legacy tv_input";
        }
        mDevice = input_device;
    } else {
        LOG(ERROR) << "Failed to get tv_input hw module";
    }
    mCallbackOpsExt.notify_ext = &TvInputExt::notify_ext;
}

TvInputExt::~TvInputExt() {
    if (mDevice != nullptr) {
        free(mDevice);
    }
}

Return<void> TvInputExt::getStreamConfigurations_ext(int32_t deviceId, getStreamConfigurations_ext_cb cb)  {
    int32_t configCount = 0;
    const tv_stream_config_ext_t* configs = nullptr;
    int ret = mDevice->get_stream_configurations_ext(mDevice, deviceId, &configCount, &configs);
    Result res = Result::UNKNOWN;
    hidl_vec<TvStreamConfig> tvStreamConfigs;
    if (ret == 0) {
        res = Result::OK;
        // @kenjc rewrite this logic
        // tvStreamConfigs.resize(getSupportedConfigCount(configCount, configs));
        tvStreamConfigs.resize(configCount);
        int32_t pos = 0;
        for (int32_t i = 0; i < configCount; i++) {
            tvStreamConfigs[pos].base.streamId = configs[i].base_config.stream_id;
            tvStreamConfigs[pos].base.maxVideoWidth = configs[i].base_config.max_video_width;
            tvStreamConfigs[pos].base.maxVideoHeight = configs[i].base_config.max_video_height;
            if (configs[i].base_config.type == TV_STREAM_TYPE_BUFFER_PRODUCER) {
                tvStreamConfigs[pos].format = configs[i].format;
                tvStreamConfigs[pos].usage = configs[i].usage;
                tvStreamConfigs[pos].width = configs[i].width;
                tvStreamConfigs[pos].height = configs[i].height;
                tvStreamConfigs[pos].buffCount = configs[i].buffCount;
            }
            pos++;
        }
    } else if (ret == -EINVAL) {
        res = Result::INVALID_ARGUMENTS;
    }
    cb(res, tvStreamConfigs);
    return Void();
}

Return<void> TvInputExt::setExtCallback(const sp<ICallbackExt>& callback)  {
    mExtCallback = callback;
    if (mExtCallback != nullptr) {
        mDevice->initialize_ext(mDevice, &mCallbackOpsExt, nullptr);
    }
    return Void();
}

Return<Result> TvInputExt::privCmdFromApp(const PrivAppCmdInfo& cmdInfo) {
    std::map<std::string, std::string> data;
    for (size_t i = 0; i < cmdInfo.data.size(); i++) {
        data.insert({cmdInfo.data[i].key, cmdInfo.data[i].value});
    }
    mDevice->priv_cmd_from_app(cmdInfo.action.c_str(), data);
    return Result::OK;
}

Return<Result> TvInputExt::requestCapture(int32_t deviceId, int32_t streamId,
                                          uint64_t buffId, const hidl_handle& buffer,
                                          int32_t seq) {
    mDevice->request_capture_ext(mDevice, deviceId,
                                 streamId, buffId, buffer, seq);
    return Result::OK;
}

Return<void> TvInputExt::cancelCapture(int32_t deviceId, int32_t streamId, int32_t seq) {
    mDevice->cancel_capture(mDevice, deviceId, streamId, seq);
    return Void();
}

Return<Result> TvInputExt::setPreviewInfo(int32_t deviceId, int32_t streamId,
                                          int32_t top, int32_t left,
                                          int32_t width, int32_t height,
                                          int32_t extInfo) {
    int ret = mDevice->set_preview_info(deviceId, streamId,
                                                  top, left, width, height,
                                                  extInfo);
    Result res = Result::UNKNOWN;
    if (ret == 0) {
        res = Result::OK;
    } else if (ret == -ENOENT) {
        res = Result::INVALID_STATE;
    } else if (ret == -EINVAL) {
        res = Result::INVALID_ARGUMENTS;
    }
    return res;
}

Return<void> TvInputExt::setSinglePreviewBuffer(const PreviewBuffer& buff) {
    mDevice->set_preview_buffer(buff.buffer, buff.bufferId);
    return Void();
}

Return<void> TvInputExt::openStream_ext(int32_t deviceId, int32_t streamId,
                                        int32_t streamType, openStream_ext_cb cb)  {
    tv_stream_ext_t stream;
    stream.base_stream.stream_id = streamId;
    stream.base_stream.type = streamType;
    int ret = mDevice->open_stream_ext(mDevice, deviceId, &stream);
    Result res = Result::UNKNOWN;
    native_handle_t* sidebandStream = nullptr;
    native_handle_t* sidebandCancelStream = nullptr;
    if (ret == 0) {
        // if (isSupportedStreamType(stream.type)) {
        if (stream.base_stream.type != TV_STREAM_TYPE_BUFFER_PRODUCER) {
            res = Result::OK;
            sidebandStream = stream.base_stream.sideband_stream_source_handle;
            sidebandCancelStream = stream.sideband_cancel_stream_source_handle;
        } else {
            res = Result::OK;
        }
    } else {
        if (ret == -EBUSY) {
            res = Result::NO_RESOURCE;
        } else if (ret == -EEXIST) {
            res = Result::INVALID_STATE;
        } else if (ret == -EINVAL) {
            res = Result::INVALID_ARGUMENTS;
        }
    }
    cb(res, sidebandStream, sidebandCancelStream);
    return Void();
}

// static
void TvInputExt::notify_ext(struct tv_input_device* __unused, tv_input_event_ext_t* event,
                     void* optionalStatus) {
    // remove
    (void)optionalStatus;
    if (mExtCallback != nullptr && event != nullptr) {
        TvInputEventExt tvInputEvent;
        tvInputEvent.type = static_cast<TvInputEventType>(event->base_event.type);
        if (event->base_event.type == TV_INPUT_EVENT_PRIV_CMD_TO_APP) {
            tvInputEvent.deviceInfo.base.deviceId = event->priv_app_cmd.device_id;
            tvInputEvent.priv_app_cmd.action = event->priv_app_cmd.action;
            //TODO
            //tvInputEvent.priv_app_cmd.data.clear();
            /*for (size_t i = 0; i < event->priv_app_cmd.data.size(); i++) {
                PrivAppCmdBundle bundle;
                bundle.key = event->priv_app_cmd.data[i].key;
                bundle.value = event->priv_app_cmd.data[i].value;
                tvInputEvent.priv_app_cmd.data.push_back(bundle);
            }*/
            mExtCallback->notify_ext(tvInputEvent);
        } else if (event->base_event.type >= TV_INPUT_EVENT_CAPTURE_SUCCEEDED) {
            tvInputEvent.deviceInfo.base.deviceId = event->base_event.capture_result.device_id;
            tvInputEvent.deviceInfo.streamId = event->base_event.capture_result.stream_id;
            tvInputEvent.capture_result.buffId = event->buff_id;
            tvInputEvent.capture_result.buffSeq = event->base_event.capture_result.seq;
            // tvInputEvent.capture_result.buffer = event->capture_result.buffer;
            mExtCallback->notify_ext(tvInputEvent);
        } else {
            // android tv_input event
            tvInputEvent.deviceInfo.base.deviceId = event->base_event.device_info.device_id;
            tvInputEvent.deviceInfo.base.type = static_cast<TvInputType>(
                    event->base_event.device_info.type);
            tvInputEvent.deviceInfo.base.portId = event->base_event.device_info.hdmi.port_id;
            tvInputEvent.deviceInfo.base.cableConnectionStatus = CableConnectionStatus::UNKNOWN;
            // TODO: Ensure the legacy audio type code is the same once audio HAL default
            // implementation is ready.
            tvInputEvent.deviceInfo.base.audioType = static_cast<AudioDevice>(
                    event->base_event.device_info.audio_type);
            memset(tvInputEvent.deviceInfo.base.audioAddress.data(), 0,
                    tvInputEvent.deviceInfo.base.audioAddress.size());
            const char* address = event->base_event.device_info.audio_address;
            if (address != nullptr) {
                size_t size = strlen(address);
                if (size > tvInputEvent.deviceInfo.base.audioAddress.size()) {
                    LOG(ERROR) << "Audio address is too long. Address:" << address << "";
                    return;
                }
                for (size_t i = 0; i < size; ++i) {
                    tvInputEvent.deviceInfo.base.audioAddress[i] =
                        static_cast<uint8_t>(event->base_event.device_info.audio_address[i]);
                }
            }
            mExtCallback->notify_ext(tvInputEvent);
        }
    }
}

} // namespace implementation
} // namespace V1_0
} // namespace input
} // namespace tv
} // namespace hardware
} // namespace rockchip
