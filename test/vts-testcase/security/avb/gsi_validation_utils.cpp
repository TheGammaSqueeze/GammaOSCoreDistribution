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

#include <android-base/file.h>
#include <android-base/properties.h>
#include <gtest/gtest.h>
#include <openssl/sha.h>

#include "gsi_validation_utils.h"

uint8_t HexDigitToByte(char c) {
  if (c >= '0' && c <= '9') {
    return c - '0';
  }
  if (c >= 'a' && c <= 'f') {
    return c - 'a' + 10;
  }
  if (c >= 'A' && c <= 'Z') {
    return c - 'A' + 10;
  }
  return 0xff;
}

bool HexToBytes(const std::string &hex, std::vector<uint8_t> *bytes) {
  if (hex.size() % 2 != 0) {
    return false;
  }
  bytes->resize(hex.size() / 2);
  for (unsigned i = 0; i < bytes->size(); i++) {
    uint8_t hi = HexDigitToByte(hex[i * 2]);
    uint8_t lo = HexDigitToByte(hex[i * 2 + 1]);
    if (lo > 0xf || hi > 0xf) {
      return false;
    }
    bytes->at(i) = (hi << 4) | lo;
  }
  return true;
}

std::unique_ptr<ShaHasher> CreateShaHasher(const std::string &algorithm) {
  if (algorithm == "sha1") {
    return std::make_unique<ShaHasherImpl<SHA_CTX>>(
        SHA1_Init, SHA1_Update, SHA1_Final, SHA_DIGEST_LENGTH);
  }
  if (algorithm == "sha256") {
    return std::make_unique<ShaHasherImpl<SHA256_CTX>>(
        SHA256_Init, SHA256_Update, SHA256_Final, SHA256_DIGEST_LENGTH);
  }
  if (algorithm == "sha512") {
    return std::make_unique<ShaHasherImpl<SHA512_CTX>>(
        SHA512_Init, SHA512_Update, SHA512_Final, SHA512_DIGEST_LENGTH);
  }
  return nullptr;
}

bool ValidatePublicKeyBlob(const std::string &key_blob_to_validate) {
  if (key_blob_to_validate.empty()) {
    GTEST_LOG_(ERROR) << "Failed to validate an empty key";
    return false;
  }

  const std::string exec_dir = android::base::GetExecutableDirectory();
  std::vector<std::string> allowed_key_names = {
      "q-gsi.avbpubkey", "r-gsi.avbpubkey",    "s-gsi.avbpubkey",
      "t-gsi.avbpubkey", "qcar-gsi.avbpubkey",
  };
  for (const auto &key_name : allowed_key_names) {
    const auto key_path = exec_dir + "/" + key_name;
    std::string allowed_key_blob;
    if (android::base::ReadFileToString(key_path, &allowed_key_blob)) {
      if (key_blob_to_validate == allowed_key_blob) {
        GTEST_LOG_(INFO) << "Found matching GSI key: " << key_path;
        return true;
      }
    }
  }
  return false;
}

const uint32_t kCurrentApiLevel = 10000;

static uint32_t ReadApiLevelProps(
    const std::vector<std::string> &api_level_props) {
  uint32_t api_level = kCurrentApiLevel;
  for (const auto &api_level_prop : api_level_props) {
    api_level = android::base::GetUintProperty<uint32_t>(api_level_prop,
                                                         kCurrentApiLevel);
    if (api_level != kCurrentApiLevel) {
      break;
    }
  }
  return api_level;
}

uint32_t GetProductFirstApiLevel() {
  uint32_t product_api_level =
      ReadApiLevelProps({"ro.product.first_api_level", "ro.build.version.sdk"});
  if (product_api_level == kCurrentApiLevel) {
    ADD_FAILURE() << "Failed to determine product first API level";
    return 0;
  }
  return product_api_level;
}

uint32_t GetBoardApiLevel() {
  // "ro.vendor.api_level" is added in Android T.
  uint32_t vendor_api_level = ReadApiLevelProps({"ro.vendor.api_level"});
  if (vendor_api_level != kCurrentApiLevel) {
    return vendor_api_level;
  }
  // For pre-T devices, determine the board API level by ourselves.
  uint32_t product_api_level = GetProductFirstApiLevel();
  uint32_t board_api_level =
      ReadApiLevelProps({"ro.board.api_level", "ro.board.first_api_level"});
  uint32_t api_level = std::min(board_api_level, product_api_level);
  if (api_level == kCurrentApiLevel) {
    ADD_FAILURE() << "Failed to determine board API level";
    return 0;
  }
  return api_level;
}
