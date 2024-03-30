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

#include <fuzzer/FuzzedDataProvider.h>

#include <string>

#include "esco_parameters.h"
#include "interop.h"

using namespace std;
constexpr size_t kNumAddressOctets = 6;
constexpr size_t kMaxStringLength = 10;
constexpr interop_feature_t kInteropFeature[] = {
    interop_feature_t::INTEROP_DISABLE_LE_SECURE_CONNECTIONS,
    interop_feature_t::INTEROP_AUTO_RETRY_PAIRING,
    interop_feature_t::INTEROP_DISABLE_ABSOLUTE_VOLUME,
    interop_feature_t::INTEROP_DISABLE_AUTO_PAIRING,
    interop_feature_t::INTEROP_KEYBOARD_REQUIRES_FIXED_PIN,
    interop_feature_t::INTEROP_2MBPS_LINK_ONLY,
    interop_feature_t::INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S,
    interop_feature_t::INTEROP_GATTC_NO_SERVICE_CHANGED_IND,
    interop_feature_t::INTEROP_DISABLE_AVDTP_RECONFIGURE,
    interop_feature_t::INTEROP_DYNAMIC_ROLE_SWITCH,
    interop_feature_t::INTEROP_DISABLE_ROLE_SWITCH,
    interop_feature_t::INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL,
    interop_feature_t::INTEROP_DISABLE_NAME_REQUEST,
    interop_feature_t::INTEROP_AVRCP_1_4_ONLY,
    interop_feature_t::INTEROP_DISABLE_SNIFF,
    interop_feature_t::INTEROP_DISABLE_AVDTP_SUSPEND,
    interop_feature_t::INTEROP_SLC_SKIP_BIND_COMMAND,
    interop_feature_t::INTEROP_AVRCP_1_3_ONLY,
};
constexpr esco_codec_t kEscoCodec[] = {
    esco_codec_t::SCO_CODEC_CVSD_D1,  esco_codec_t::ESCO_CODEC_CVSD_S3,
    esco_codec_t::ESCO_CODEC_CVSD_S4, esco_codec_t::ESCO_CODEC_MSBC_T1,
    esco_codec_t::ESCO_CODEC_MSBC_T2,
};

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider mFuzzedDataProvider = FuzzedDataProvider(data, size);
  RawAddress fuzzAddress;
  string addressString;
  for (size_t i = 0; i < kNumAddressOctets; ++i) {
    addressString.append(
        mFuzzedDataProvider.ConsumeBytesAsString(sizeof(uint8_t)));
    if (i != kNumAddressOctets - 1) {
      addressString.append(":");
    }
  }
  RawAddress::FromString(addressString, fuzzAddress);
  interop_feature_t interopFeature =
      mFuzzedDataProvider.PickValueInArray(kInteropFeature);
  interop_match_addr(interopFeature, &fuzzAddress);
  interop_database_add(interopFeature, &fuzzAddress,
                       mFuzzedDataProvider.ConsumeIntegralInRange<int32_t>(
                           1, RawAddress::kLength - 1));
  interop_database_clear();
  interop_match_name(
      interopFeature,
      mFuzzedDataProvider.ConsumeRandomLengthString(kMaxStringLength).c_str());
  esco_codec_t escoCodec = mFuzzedDataProvider.PickValueInArray(kEscoCodec);
  esco_parameters_for_codec(escoCodec);
  return 0;
}
