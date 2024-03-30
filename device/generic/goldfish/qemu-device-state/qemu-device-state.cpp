/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* this program is used to read a set of system properties and their values
 * from the emulator program and set them in the currently-running emulated
 * system. It does so by connecting to the 'boot-properties' qemud service.
 *
 * This program should be run as root and called from
 * /system/etc/init.ranchu.rc exclusively.
 */

#define LOG_TAG  "qemu-device-state"

#define DEBUG  0
//#define LOG_NDEBUG 0

#include <fstream>
#include <iostream>
#include <string.h>

#include <log/log.h>

#if DEBUG
#  define  DD(...)    ALOGD(__VA_ARGS__)
#else
#  define  DD(...)    ((void)0)
#endif

#define DEVIE_STATE_PROP "qemu.device_state"
// init will copy over this file to
// /data/system/devicestate/device_state_configuration.xml
#define DEVICE_STATE_FILE "/data/vendor/devicestate/device_state_configuration.xml"

extern bool fs_mgr_get_boot_config(const std::string& key, std::string* out_val);

int main(void) {
    std::string deviceState;
    if (!fs_mgr_get_boot_config(DEVIE_STATE_PROP, &deviceState)) {
        ALOGV("%s bootconfig prop not set", DEVIE_STATE_PROP);
        return 0;
    }

    std::ofstream f;
    f.open(DEVICE_STATE_FILE);
    if (!f.is_open()) {
        ALOGE("Failed to open %s\n", DEVICE_STATE_FILE);
        exit(1);
    }

    ALOGI("write to %s", DEVICE_STATE_FILE);
    f << deviceState;
    f.close();
    return 0;
}
