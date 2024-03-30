"""Constants for arch/cpu variants/features."""

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load(
    "@soong_injection//product_config:arch_configuration.bzl",
    _android_arch_feature_for_arch_variant = "android_arch_feature_for_arch_variants",
    _arch_to_cpu_variants = "arch_to_cpu_variants",
    _arch_to_features = "arch_to_features",
    _arch_to_variants = "arch_to_variants",
)

def _flatten_string_list_dict_to_set(string_list_dict):
    ret = {}
    for l in string_list_dict.values():
        for i in l:
            ret[i] = True
    return ret

_arch_variants = _flatten_string_list_dict_to_set(_arch_to_variants)
_cpu_variants = _flatten_string_list_dict_to_set(_arch_to_cpu_variants)
_arch_features = _flatten_string_list_dict_to_set(_arch_to_features)

constants = struct(
    AvailableArchVariants = _arch_variants,
    AvailableCpuVariants = _cpu_variants,
    AvailableArchFeatures = _arch_features,
    ArchToVariants = _arch_to_variants,
    CpuToVariants = _arch_to_cpu_variants,
    ArchToFeatures = _arch_to_features,
    AndroidArchToVariantToFeatures = _android_arch_feature_for_arch_variant,
)
