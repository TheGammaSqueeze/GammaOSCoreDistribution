"""
Copyright (C) 2021 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

def prebuilt_library_static(
        name,
        static_library,
        alwayslink = None,
        export_includes = [],
        export_system_includes = [],
        **kwargs):
    "Bazel macro to correspond with the *_prebuilt_library_static Soong module types"

    # TODO: Handle includes similarly to cc_library_static
    # e.g. includes = ["clang-r416183b/prebuilt_include/llvm/lib/Fuzzer"],
    native.cc_import(
        name = name,
        static_library = static_library,
        alwayslink = alwayslink,
        **kwargs
    )

    native.cc_import(
        name = name + "_alwayslink",
        static_library = static_library,
        alwayslink = True,
        **kwargs
    )
