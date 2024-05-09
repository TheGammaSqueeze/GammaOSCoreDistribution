// FIXME: your file license if you have one

#include "HdmiAudioCallback.h"

namespace rockchip::hardware::hdmi::implementation {

// Methods from ::rockchip::hardware::hdmi::V1_0::IHdmiAudioCallback follow.
Return<void> HdmiAudioCallback::onConnect(const hidl_string& deviceId) {
    // TODO implement
    return Void();
}

Return<void> HdmiAudioCallback::onDisconnect(const hidl_string& deviceId) {
    // TODO implement
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

//IHdmiAudioCallback* HIDL_FETCH_IHdmiAudioCallback(const char* /* name */) {
    //return new HdmiAudioCallback();
//}
//
}  // namespace rockchip::hardware::hdmi::implementation
