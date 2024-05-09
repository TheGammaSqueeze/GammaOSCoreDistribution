#define LOG_TAG "rockchip.hardware.hdmi@1.0-service"

#include <rockchip/hardware/hdmi/1.0/IHdmi.h>
#include <hidl/LegacySupport.h>
#include <binder/ProcessState.h>

using rockchip::hardware::hdmi::V1_0::IHdmi;
using android::hardware::defaultPassthroughServiceImplementation;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::sp;

int main() {
    android::ProcessState::initWithDriver("/dev/vndbinder");
    return defaultPassthroughServiceImplementation<IHdmi>("default", /*maxThreads*/ 6);
}