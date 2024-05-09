// FIXME: your file license if you have one

#include "HdmiCallback.h"
#include "log/log.h"
namespace rockchip::hardware::hdmi::implementation {

// Methods from ::rockchip::hardware::hdmi::V1_0::IHdmiCallback follow.
Return<void> HdmiCallback::onConnect(const hidl_string& deviceId) {
    ALOGE("@%s",__FUNCTION__);
    return Void();
}

Return<void> HdmiCallback::onFormatChange(const hidl_string& deviceId,uint32_t width,uint32_t height) {
    ALOGE("@%s",__FUNCTION__);
    return Void();
}

Return<void> HdmiCallback::onDisconnect(const hidl_string& deviceId) {
    ALOGE("@%s",__FUNCTION__);
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

//IHdmiCallback* HIDL_FETCH_IHdmiCallback(const char* /* name */) {
    //return new HdmiCallback();
//}
//
}  // namespace rockchip::hardware::hdmi::implementation
