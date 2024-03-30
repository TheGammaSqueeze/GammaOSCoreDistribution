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

#include <openssl/digest.h>
#include <openssl/hkdf.h>
#include <openssl/mem.h>

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;
using compos_key::Ed25519KeyPair;
using compos_key::Signature;

// Used to ensure the key we derive is distinct from any other.
constexpr const char* kSigningKeyInfo = "CompOS signing key";

namespace compos_key {
Result<Ed25519KeyPair> deriveKeyFromSecret(const uint8_t* secret, size_t secret_size) {
    // Ed25519 private keys are derived from a 32 byte seed:
    // https://datatracker.ietf.org/doc/html/rfc8032#section-5.1.5
    std::array<uint8_t, 32> seed;

    // We derive the seed from the secret using HKDF - see
    // https://datatracker.ietf.org/doc/html/rfc5869#section-2.
    if (!HKDF(seed.data(), seed.size(), EVP_sha256(), secret, secret_size, /*salt=*/nullptr,
              /*salt_len=*/0, reinterpret_cast<const uint8_t*>(kSigningKeyInfo),
              strlen(kSigningKeyInfo))) {
        return Error() << "HKDF failed";
    }

    Ed25519KeyPair result;
    ED25519_keypair_from_seed(result.public_key.data(), result.private_key.data(), seed.data());
    return result;
}

Result<Signature> sign(const PrivateKey& private_key, const uint8_t* data, size_t data_size) {
    Signature result;
    if (!ED25519_sign(result.data(), data, data_size, private_key.data())) {
        return Error() << "Failed to sign";
    }
    return result;
}

bool verify(const PublicKey& public_key, const Signature& signature, const uint8_t* data,
            size_t data_size) {
    return ED25519_verify(data, data_size, signature.data(), public_key.data()) == 1;
}
} // namespace compos_key
