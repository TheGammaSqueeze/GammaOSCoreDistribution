// FIXME: your file license if you have one

#pragma once

#include <rockchip/hardware/hdmi/1.0/IHdmi.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

#include "DeviceV4L2Event.h"

namespace rockchip::hardware::hdmi::implementation {

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct Hdmi : public V1_0::IHdmi {
    public:
     Hdmi();
     ~Hdmi();
    // Methods from ::rockchip::hardware::hdmi::V1_0::IHdmi follow.
    Return<void> foundHdmiDevice(const hidl_string& deviceId, const ::android::sp<::rockchip::hardware::hdmi::V1_0::IHdmiRxStatusCallback>& cb) override;
    Return<void> getHdmiDeviceId(getHdmiDeviceId_cb _hidl_cb) override;
    Return<void> getMipiStatus(getMipiStatus_cb _hidl_cb) override;
    Return<void> getHdmiRxStatus(getHdmiRxStatus_cb _hidl_cb) override;
    Return<void> onStatusChange(uint32_t status) override;
    Return<void> registerListener(const sp<::rockchip::hardware::hdmi::V1_0::IHdmiCallback>& cb) override;
    Return<void> unregisterListener(const sp<::rockchip::hardware::hdmi::V1_0::IHdmiCallback>& cb) override;
    Return<void> addAudioListener(const sp<::rockchip::hardware::hdmi::V1_0::IHdmiAudioCallback>& cb) override;
    Return<void> removeAudioListener(const sp<::rockchip::hardware::hdmi::V1_0::IHdmiAudioCallback>& cb) override;
    Return<void> onAudioChange(const ::rockchip::hardware::hdmi::V1_0::HdmiAudioStatus& status) override;
    Return<void> setFrameDecorator(const sp<::rockchip::hardware::hdmi::V1_0::IFrameWarpper>& frameWarpper) override;
    Return<void> decoratorFrame(const ::rockchip::hardware::hdmi::V1_0::FrameInfo& frameInfo, decoratorFrame_cb _hidl_cb) override;

    static V4L2EventCallBack eventCallback(void* sender,int event_type,struct v4l2_event *event);
};

 extern "C" V1_0::IHdmi* HIDL_FETCH_IHdmi(const char* name);

}  // namespace rockchip::hardware::hdmi::implementation
