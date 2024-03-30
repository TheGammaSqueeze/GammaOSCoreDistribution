# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Aspects used to transform certain providers into a TradefedTestInfo.

Tradefed tests require a TradefedTestInfo provider that is not usually
returned by most rules. Instead of creating custom rules to adapt build
rule providers, we use Bazel aspects to convert the input rule's provider
into a suitable type.

See https://docs.bazel.build/versions/main/skylark/aspects.html#aspects
for more information on how aspects work.
"""

load("//bazel/rules:soong_prebuilt.bzl", "SoongPrebuiltInfo")
load("//bazel/rules:tradefed_test_info.bzl", "TradefedTestInfo")

def _soong_prebuilt_tradefed_aspect_impl(target, ctx):
    test_config_files = []
    test_binary_files = []

    # Partition files into config files and test binaries.
    for f in target[SoongPrebuiltInfo].files.to_list():
        if f.extension == "config" or f.extension == "xml":
            test_config_files.append(f)
        else:
            test_binary_files.append(f)

    return [
        TradefedTestInfo(
            module_name = target[SoongPrebuiltInfo].module_name,
            test_binaries = test_binary_files,
            test_configs = test_config_files,
        ),
    ]

soong_prebuilt_tradefed_test_aspect = aspect(
    attr_aspects = ["test"],
    implementation = _soong_prebuilt_tradefed_aspect_impl,
)
