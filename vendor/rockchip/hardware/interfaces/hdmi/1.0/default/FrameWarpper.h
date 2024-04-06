// FIXME: your file license if you have one

#pragma once

#include <rockchip/hardware/hdmi/1.0/IFrameWarpper.h>
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

struct FrameWarpper : public V1_0::IFrameWarpper {
    // Methods from ::rockchip::hardware::hdmi::V1_0::IFrameWarpper follow.
    Return<void> onFrame(const ::rockchip::hardware::hdmi::V1_0::FrameInfo& FrameInfo, onFrame_cb _hidl_cb) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.

};

// FIXME: most likely delete, this is only for passthrough implementations
// extern "C" IFrameWarpper* HIDL_FETCH_IFrameWarpper(const char* name);

}  // namespace rockchip::hardware::hdmi::implementation
