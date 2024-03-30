/*
 * Copyright 2021 The Android Open Source Project
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
#include <vector>

#include <aidl/android/hardware/security/keymint/IRemotelyProvisionedComponent.h>
#include <android/binder_manager.h>
#include <cppbor.h>
#include <gflags/gflags.h>
#include <keymaster/cppcose/cppcose.h>
#include <openssl/base64.h>
#include <remote_prov/remote_prov_utils.h>
#include <sys/random.h>

using aidl::android::hardware::security::keymint::DeviceInfo;
using aidl::android::hardware::security::keymint::IRemotelyProvisionedComponent;
using aidl::android::hardware::security::keymint::MacedPublicKey;
using aidl::android::hardware::security::keymint::ProtectedData;
using aidl::android::hardware::security::keymint::RpcHardwareInfo;
using aidl::android::hardware::security::keymint::remote_prov::generateEekChain;
using aidl::android::hardware::security::keymint::remote_prov::getProdEekChain;
using aidl::android::hardware::security::keymint::remote_prov::jsonEncodeCsrWithBuild;

using namespace cppbor;
using namespace cppcose;

DEFINE_bool(test_mode, false, "If enabled, a fake EEK key/cert are used.");

DEFINE_string(output_format, "csr", "How to format the output. Defaults to 'csr'.");

namespace {

// Various supported --output_format values.
constexpr std::string_view kBinaryCsrOutput = "csr";     // Just the raw csr as binary
constexpr std::string_view kBuildPlusCsr = "build+csr";  // Text-encoded (JSON) build
                                                         // fingerprint plus CSR.

constexpr size_t kChallengeSize = 16;

std::string toBase64(const std::vector<uint8_t>& buffer) {
    size_t base64Length;
    int rc = EVP_EncodedLength(&base64Length, buffer.size());
    if (!rc) {
        std::cerr << "Error getting base64 length. Size overflow?" << std::endl;
        exit(-1);
    }

    std::string base64(base64Length, ' ');
    rc = EVP_EncodeBlock(reinterpret_cast<uint8_t*>(base64.data()), buffer.data(), buffer.size());
    ++rc;  // Account for NUL, which BoringSSL does not for some reason.
    if (rc != base64Length) {
        std::cerr << "Error writing base64. Expected " << base64Length
                  << " bytes to be written, but " << rc << " bytes were actually written."
                  << std::endl;
        exit(-1);
    }
    return base64;
}

std::vector<uint8_t> generateChallenge() {
    std::vector<uint8_t> challenge(kChallengeSize);

    ssize_t bytesRemaining = static_cast<ssize_t>(challenge.size());
    uint8_t* writePtr = challenge.data();
    while (bytesRemaining > 0) {
        int bytesRead = getrandom(writePtr, bytesRemaining, /*flags=*/0);
        if (bytesRead < 0) {
            if (errno == EINTR) {
                continue;
            } else {
                std::cerr << errno << ": " << strerror(errno) << std::endl;
                exit(-1);
            }
        }
        bytesRemaining -= bytesRead;
        writePtr += bytesRead;
    }

    return challenge;
}

Array composeCertificateRequest(const ProtectedData& protectedData,
                                const DeviceInfo& verifiedDeviceInfo,
                                const std::vector<uint8_t>& challenge,
                                const std::vector<uint8_t>& keysToSignMac) {
    Array macedKeysToSign = Array()
                                .add(std::vector<uint8_t>(0))  // empty protected headers as bstr
                                .add(Map())                    // empty unprotected headers
                                .add(Null())                   // nil for the payload
                                .add(keysToSignMac);           // MAC as returned from the HAL

    Array deviceInfo =
        Array().add(EncodedItem(verifiedDeviceInfo.deviceInfo)).add(Map());  // Empty device info

    Array certificateRequest = Array()
                                   .add(std::move(deviceInfo))
                                   .add(challenge)
                                   .add(EncodedItem(protectedData.protectedData))
                                   .add(std::move(macedKeysToSign));
    return certificateRequest;
}

std::vector<uint8_t> getEekChain(uint32_t curve) {
    if (FLAGS_test_mode) {
        const std::vector<uint8_t> kFakeEekId = {'f', 'a', 'k', 'e', 0};
        auto eekOrErr = generateEekChain(curve, 3 /* chainlength */, kFakeEekId);
        if (!eekOrErr) {
            std::cerr << "Failed to generate test EEK somehow: " << eekOrErr.message() << std::endl;
            exit(-1);
        }
        auto [eek, pubkey, privkey] = eekOrErr.moveValue();
        std::cout << "EEK raw keypair:" << std::endl;
        std::cout << "  pub:  " << toBase64(pubkey) << std::endl;
        std::cout << "  priv: " << toBase64(privkey) << std::endl;
        return eek;
    }

    return getProdEekChain(curve);
}

void writeOutput(const std::string instance_name, const Array& csr) {
    if (FLAGS_output_format == kBinaryCsrOutput) {
        auto bytes = csr.encode();
        std::copy(bytes.begin(), bytes.end(), std::ostream_iterator<char>(std::cout));
    } else if (FLAGS_output_format == kBuildPlusCsr) {
        auto [json, error] = jsonEncodeCsrWithBuild(instance_name, csr);
        if (!error.empty()) {
            std::cerr << "Error JSON encoding the output: " << error;
            exit(1);
        }
        std::cout << json << std::endl;
    } else {
        std::cerr << "Unexpected output_format '" << FLAGS_output_format << "'" << std::endl;
        std::cerr << "Valid formats:" << std::endl;
        std::cerr << "  " << kBinaryCsrOutput << std::endl;
        std::cerr << "  " << kBuildPlusCsr << std::endl;
        exit(1);
    }
}

// Callback for AServiceManager_forEachDeclaredInstance that writes out a CSR
// for every IRemotelyProvisionedComponent.
void getCsrForInstance(const char* name, void* /*context*/) {
    const std::vector<uint8_t> challenge = generateChallenge();

    auto fullName = std::string(IRemotelyProvisionedComponent::descriptor) + "/" + name;
    AIBinder* rkpAiBinder = AServiceManager_getService(fullName.c_str());
    ::ndk::SpAIBinder rkp_binder(rkpAiBinder);
    auto rkp_service = IRemotelyProvisionedComponent::fromBinder(rkp_binder);
    if (!rkp_service) {
        std::cerr << "Unable to get binder object for '" << fullName << "', skipping.";
        exit(-1);
    }

    std::vector<uint8_t> keysToSignMac;
    std::vector<MacedPublicKey> emptyKeys;
    DeviceInfo verifiedDeviceInfo;
    ProtectedData protectedData;
    RpcHardwareInfo hwInfo;
    ::ndk::ScopedAStatus status = rkp_service->getHardwareInfo(&hwInfo);
    if (!status.isOk()) {
        std::cerr << "Failed to get hardware info for '" << fullName
                  << "'. Error code: " << status.getServiceSpecificError() << "." << std::endl;
        exit(-1);
    }
    status = rkp_service->generateCertificateRequest(
        FLAGS_test_mode, emptyKeys, getEekChain(hwInfo.supportedEekCurve), challenge,
        &verifiedDeviceInfo, &protectedData, &keysToSignMac);
    if (!status.isOk()) {
        std::cerr << "Bundle extraction failed for '" << fullName
                  << "'. Error code: " << status.getServiceSpecificError() << "." << std::endl;
        exit(-1);
    }
    auto request =
        composeCertificateRequest(protectedData, verifiedDeviceInfo, challenge, keysToSignMac);
    writeOutput(std::string(name), request);
}

}  // namespace

int main(int argc, char** argv) {
    gflags::ParseCommandLineFlags(&argc, &argv, /*remove_flags=*/true);

    AServiceManager_forEachDeclaredInstance(IRemotelyProvisionedComponent::descriptor,
                                            /*context=*/nullptr, getCsrForInstance);

    return 0;
}
