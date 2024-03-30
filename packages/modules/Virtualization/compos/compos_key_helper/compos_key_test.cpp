/*
 * Copyright 2022 The Android Open Source Project
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

#include "compos_key.h"

#include <vector>

#include "gtest/gtest.h"

using namespace compos_key;

const std::vector<uint8_t> secret = {1, 2, 3};
const std::vector<uint8_t> other_secret = {3, 2, 3};
const std::vector<uint8_t> data = {42, 180, 65, 0};

struct ComposKeyTest : public testing::Test {
    Ed25519KeyPair key_pair;

    void SetUp() override {
        auto key_pair = deriveKeyFromSecret(secret.data(), secret.size());
        ASSERT_TRUE(key_pair.ok()) << key_pair.error();
        this->key_pair = *key_pair;
    }
};

TEST_F(ComposKeyTest, SameSecretSameKey) {
    auto other_key_pair = deriveKeyFromSecret(secret.data(), secret.size());
    ASSERT_TRUE(other_key_pair.ok()) << other_key_pair.error();

    ASSERT_EQ(key_pair.private_key, other_key_pair->private_key);
    ASSERT_EQ(key_pair.public_key, other_key_pair->public_key);
}

TEST_F(ComposKeyTest, DifferentSecretDifferentKey) {
    auto other_key_pair = deriveKeyFromSecret(other_secret.data(), other_secret.size());
    ASSERT_TRUE(other_key_pair.ok()) << other_key_pair.error();

    ASSERT_NE(key_pair.private_key, other_key_pair->private_key);
    ASSERT_NE(key_pair.public_key, other_key_pair->public_key);
}

TEST_F(ComposKeyTest, CanVerifyValidSignature) {
    auto signature = sign(key_pair.private_key, data.data(), data.size());
    ASSERT_TRUE(signature.ok()) << signature.error();

    bool verified = verify(key_pair.public_key, *signature, data.data(), data.size());
    ASSERT_TRUE(verified);
}

TEST_F(ComposKeyTest, WrongSignatureDoesNotVerify) {
    auto signature = sign(key_pair.private_key, data.data(), data.size());
    ASSERT_TRUE(signature.ok()) << signature.error();

    (*signature)[0] ^= 1;

    bool verified = verify(key_pair.public_key, *signature, data.data(), data.size());
    ASSERT_FALSE(verified);
}

TEST_F(ComposKeyTest, WrongDataDoesNotVerify) {
    auto signature = sign(key_pair.private_key, data.data(), data.size());
    ASSERT_TRUE(signature.ok()) << signature.error();

    auto other_data = data;
    other_data[0] ^= 1;

    bool verified = verify(key_pair.public_key, *signature, other_data.data(), other_data.size());
    ASSERT_FALSE(verified);
}

TEST_F(ComposKeyTest, WrongKeyDoesNotVerify) {
    auto signature = sign(key_pair.private_key, data.data(), data.size());

    auto other_key_pair = deriveKeyFromSecret(other_secret.data(), other_secret.size());
    ASSERT_TRUE(other_key_pair.ok()) << other_key_pair.error();

    bool verified = verify(other_key_pair->public_key, *signature, data.data(), data.size());
    ASSERT_FALSE(verified);
}
