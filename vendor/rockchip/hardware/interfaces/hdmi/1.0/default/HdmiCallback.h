// FIXME: your file license if you have one

#pragma once

#include <rockchip/hardware/hdmi/1.0/IHdmiCallback.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace rockchip::hardware::hdmi::implementation {

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct HdmiCallback : public V1_0::IHdmiCallback {
    // Methods from ::rockchip::hardware::hdmi::V1_0::IHdmiCallback follow.
    Return<void> onConnect(const hidl_string& deviceId) override;
    Return<void> onFormatChange(const hidl_string& deviceId,uint32_t width,uint32_t height) override;
    Return<void> onDisconnect(const hidl_string& deviceId) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.

};

// FIXME: most likely delete, this is only for passthrough implementations
// extern "C" IHdmiCallback* HIDL_FETCH_IHdmiCallback(const char* name);

}  // namespace rockchip::hardware::hdmi::implementation
