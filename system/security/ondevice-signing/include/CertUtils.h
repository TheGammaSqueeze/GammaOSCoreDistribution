/*
 * Copyright (C) 2020 The Android Open Source Project
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

#pragma once

#include <functional>
#include <string>
#include <vector>

#include <android-base/result.h>

// Information extracted from a certificate.
struct CertInfo {
    std::string subjectCn;
    std::vector<uint8_t> subjectRsaPublicKey;
};

// Subjects of certificates we issue.
struct CertSubject {
    const char* commonName;
    unsigned serialNumber;
};

// These are all the certificates we ever sign (the first one being our
// self-signed cert).  We shouldn't really re-use serial numbers for different
// certificates for the same subject but we do; only one should be in use at a
// time though.
inline const CertSubject kRootSubject{"ODS", 1};
inline const CertSubject kCompOsSubject{"CompOs", 2};

android::base::Result<void> createSelfSignedCertificate(
    const std::vector<uint8_t>& publicKey,
    const std::function<android::base::Result<std::string>(const std::string&)>& signFunction,
    const std::string& path);

android::base::Result<void> createLeafCertificate(
    const CertSubject& subject, const std::vector<uint8_t>& publicKey,
    const std::function<android::base::Result<std::string>(const std::string&)>& signFunction,
    const std::string& issuerCertPath, const std::string& outPath);

android::base::Result<std::vector<uint8_t>> createPkcs7(const std::vector<uint8_t>& signedData,
                                                        const CertSubject& signer);

android::base::Result<std::vector<uint8_t>>
extractPublicKeyFromX509(const std::vector<uint8_t>& x509);
android::base::Result<std::vector<uint8_t>>
extractPublicKeyFromSubjectPublicKeyInfo(const std::vector<uint8_t>& subjectKeyInfo);
android::base::Result<std::vector<uint8_t>> extractPublicKeyFromX509(const std::string& path);

android::base::Result<CertInfo>
verifyAndExtractCertInfoFromX509(const std::string& path, const std::vector<uint8_t>& publicKey);

android::base::Result<void> verifySignature(const std::string& message,
                                            const std::string& signature,
                                            const std::vector<uint8_t>& publicKey);

android::base::Result<void> verifyRsaPublicKeySignature(const std::string& message,
                                                        const std::string& signature,
                                                        const std::vector<uint8_t>& rsaPublicKey);
