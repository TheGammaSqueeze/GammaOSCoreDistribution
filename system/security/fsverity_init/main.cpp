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

#include <string>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <fsverity_init.h>
#include <log/log.h>
#include <mini_keyctl_utils.h>

int main(int argc, const char** argv) {
    if (argc < 2) {
        LOG(ERROR) << "Not enough arguments";
        return -1;
    }

    key_serial_t keyring_id = android::GetKeyringId(".fs-verity");
    if (keyring_id < 0) {
        LOG(ERROR) << "Failed to find .fs-verity keyring id";
        return -1;
    }

    const std::string_view command = argv[1];

    if (command == "--load-verified-keys") {
        LoadKeyFromVerifiedPartitions(keyring_id);
    } else if (command == "--load-extra-key") {
        if (argc != 3) {
            LOG(ERROR) << "--load-extra-key requires <key_name> argument.";
            return -1;
        }
        if (!LoadKeyFromStdin(keyring_id, argv[2])) {
            return -1;
        }
    } else if (command == "--lock") {
        // Requires files backed by fs-verity to be verified with a key in .fs-verity
        // keyring.
        if (!android::base::WriteStringToFile("1", "/proc/sys/fs/verity/require_signatures")) {
            PLOG(ERROR) << "Failed to enforce fs-verity signature";
        }

        if (!android::base::GetBoolProperty("ro.debuggable", false)) {
            if (keyctl_restrict_keyring(keyring_id, nullptr, nullptr) < 0) {
                PLOG(ERROR) << "Cannot restrict .fs-verity keyring";
            }
        }
    } else {
        LOG(ERROR) << "Unknown argument(s).";
        return -1;
    }

    return 0;
}
