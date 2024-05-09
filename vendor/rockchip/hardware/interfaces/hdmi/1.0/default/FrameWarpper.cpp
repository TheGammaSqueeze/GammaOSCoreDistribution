// FIXME: your file license if you have one

#include "FrameWarpper.h"

namespace rockchip::hardware::hdmi::implementation {

// Methods from ::rockchip::hardware::hdmi::V1_0::IFrameWarpper follow.
Return<void> FrameWarpper::onFrame(const ::rockchip::hardware::hdmi::V1_0::FrameInfo& FrameInfo, onFrame_cb _hidl_cb) {
    // TODO implement
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

//IFrameWarpper* HIDL_FETCH_IFrameWarpper(const char* /* name */) {
    //return new FrameWarpper();
//}
//
}  // namespace rockchip::hardware::hdmi::implementation
