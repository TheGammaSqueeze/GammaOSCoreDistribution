#include <hardware/tv_input.h>
#include <hidl/LegacySupport.h>
#include <rockchip/hardware/tv/input/1.0/ITvInput.h>

#include "TvInputExt.h"

namespace android {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {
namespace implementation {

extern ITvInput* HIDL_FETCH_ITvInput(const char* /* name */);

}  // namespace implementation
}  // namespace V1_0
}  // namespace input
}  // namespace tv
}  // namespace hardware
}  // namespace android

using rockchip::hardware::tv::input::V1_0::ITvInput;
using rockchip::hardware::tv::input::V1_0::implementation::TvInputExt;

extern "C" ITvInput *HIDL_FETCH_ITvInput(const char * /*instance*/) {
  return new TvInputExt{
      android::hardware::tv::input::V1_0::implementation::HIDL_FETCH_ITvInput(
          nullptr)};
}
