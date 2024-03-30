/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define LOG_TAG "fsverity_init"

#include <sys/types.h>

#include <filesystem>
#include <string>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <log/log.h>
#include <mini_keyctl_utils.h>

bool LoadKeyToKeyring(key_serial_t keyring_id, const char* desc, const char* data, size_t size) {
    key_serial_t key = add_key("asymmetric", desc, data, size, keyring_id);
    if (key < 0) {
        PLOG(ERROR) << "Failed to add key";
        return false;
    }
    return true;
}

bool LoadKeyFromStdin(key_serial_t keyring_id, const char* keyname) {
    std::string content;
    if (!android::base::ReadFdToString(STDIN_FILENO, &content)) {
        LOG(ERROR) << "Failed to read key from stdin";
        return false;
    }
    if (!LoadKeyToKeyring(keyring_id, keyname, content.c_str(), content.size())) {
        LOG(ERROR) << "Failed to load key from stdin";
        return false;
    }
    return true;
}

void LoadKeyFromFile(key_serial_t keyring_id, const char* keyname, const std::string& path) {
    LOG(INFO) << "LoadKeyFromFile path=" << path << " keyname=" << keyname;
    std::string content;
    if (!android::base::ReadFileToString(path, &content)) {
        LOG(ERROR) << "Failed to read key from " << path;
        return;
    }
    if (!LoadKeyToKeyring(keyring_id, keyname, content.c_str(), content.size())) {
        LOG(ERROR) << "Failed to load key from " << path;
    }
}

void LoadKeyFromDirectory(key_serial_t keyring_id, const char* keyname_prefix, const char* dir) {
    if (!std::filesystem::exists(dir)) {
        return;
    }
    int counter = 0;
    for (const auto& entry : std::filesystem::directory_iterator(dir)) {
        if (!android::base::EndsWithIgnoreCase(entry.path().c_str(), ".der")) continue;
        std::string keyname = keyname_prefix + std::to_string(counter);
        counter++;
        LoadKeyFromFile(keyring_id, keyname.c_str(), entry.path());
    }
}

void LoadKeyFromVerifiedPartitions(key_serial_t keyring_id) {
    // NB: Directories need to be synced with FileIntegrityService.java in
    // frameworks/base.
    LoadKeyFromDirectory(keyring_id, "fsv_system_", "/system/etc/security/fsverity");
    LoadKeyFromDirectory(keyring_id, "fsv_product_", "/product/etc/security/fsverity");
}
