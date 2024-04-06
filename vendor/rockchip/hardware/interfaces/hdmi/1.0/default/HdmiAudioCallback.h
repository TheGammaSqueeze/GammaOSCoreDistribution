// FIXME: your file license if you have one

#pragma once

#include <rockchip/hardware/hdmi/1.0/IHdmiAudioCallback.h>
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

struct HdmiAudioCallback : public V1_0::IHdmiAudioCallback {
    // Methods from ::rockchip::hardware::hdmi::V1_0::IHdmiAudioCallback follow.
    Return<void> onConnect(const hidl_string& deviceId) override;
    Return<void> onDisconnect(const hidl_string& deviceId) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.

};

// FIXME: most likely delete, this is only for passthrough implementations
// extern "C" IHdmiAudioCallback* HIDL_FETCH_IHdmiAudioCallback(const char* name);

}  // namespace rockchip::hardware::hdmi::implementation
