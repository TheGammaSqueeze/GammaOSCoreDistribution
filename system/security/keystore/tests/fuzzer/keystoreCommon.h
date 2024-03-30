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
#ifndef KEYSTORECOMMON_H
#define KEYSTORECOMMON_H

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <keystore/KeyAttestationPackageInfo.h>
#include <keystore/Signature.h>
#include <vector>

#include "fuzzer/FuzzedDataProvider.h"

using namespace android;
using namespace std;
using ::content::pm::Signature;
using ::security::keymaster::KeyAttestationPackageInfo;

constexpr size_t kSignatureSizeMin = 1;
constexpr size_t kSignatureSizeMax = 1000;
constexpr size_t kRandomStringLength = 256;
constexpr size_t kSignatureVectorSizeMin = 1;
constexpr size_t kSignatureVectorSizeMax = 1000;

struct PackageInfoData {
    string packageName;
    int64_t versionCode;
    KeyAttestationPackageInfo::SharedSignaturesVector sharedSignaturesVector;
};

inline void invokeReadWriteParcel(Parcelable* obj) {
    Parcel parcel;
    obj->writeToParcel(&parcel);
    parcel.setDataPosition(0);
    obj->readFromParcel(&parcel);
}

inline vector<uint8_t> initSignatureData(FuzzedDataProvider* fdp) {
    size_t signatureSize = fdp->ConsumeIntegralInRange(kSignatureSizeMin, kSignatureSizeMax);
    vector<uint8_t> signatureData = fdp->ConsumeBytes<uint8_t>(signatureSize);
    return signatureData;
}

inline PackageInfoData initPackageInfoData(FuzzedDataProvider* fdp) {
    PackageInfoData packageInfoData;
    packageInfoData.packageName = fdp->ConsumeRandomLengthString(kRandomStringLength);
    packageInfoData.versionCode = fdp->ConsumeIntegral<int64_t>();
    size_t signatureVectorSize =
        fdp->ConsumeIntegralInRange(kSignatureVectorSizeMin, kSignatureVectorSizeMax);
    KeyAttestationPackageInfo::SignaturesVector signatureVector;
    for (size_t size = 0; size < signatureVectorSize; ++size) {
        bool shouldUseParameterizedConstructor = fdp->ConsumeBool();
        if (shouldUseParameterizedConstructor) {
            vector<uint8_t> signatureData = initSignatureData(fdp);
            signatureVector.push_back(make_optional<Signature>(signatureData));
        } else {
            signatureVector.push_back(std::nullopt);
        }
    }
    packageInfoData.sharedSignaturesVector =
        make_shared<KeyAttestationPackageInfo::SignaturesVector>(move(signatureVector));
    return packageInfoData;
}
#endif  // KEYSTORECOMMON_H
