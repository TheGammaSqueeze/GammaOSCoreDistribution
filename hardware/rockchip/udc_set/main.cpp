#define LOG_TAG "RockchipAndroidUSB"

#include <dirent.h>

#include <log/log.h>
#include <android-base/properties.h>

#ifdef RECOVERY_PROP
#define TARGET_UDC_PROP "sys.usb.controller"
#else
#define TARGET_UDC_PROP "vendor.usb.controller"
#endif
// Set the UDC controller for the ConfigFS USB Gadgets.
// Read the UDC controller in use from "/sys/class/udc".
// In case of multiple UDC controllers select the first one.
// Skipped dummy_udc.0 in GKI mode.
static void SetUsbController() {
    std::unique_ptr<DIR, decltype(&closedir)>dir(opendir("/sys/class/udc"), closedir);
    if (!dir) return;

    dirent* dp;
    while ((dp = readdir(dir.get())) != nullptr) {
        if (dp->d_name[0] == '.' || !strcmp(dp->d_name, "dummy_udc.0")) {
            continue;
        }

        android::base::SetProperty(TARGET_UDC_PROP, dp->d_name);
        ALOGI("USB controller successfully detected: %s", dp->d_name);
        break;
    }
}

int main() {
    SetUsbController();
    return 0;
}
