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

load(":cc_library_static.bzl", "cc_library_static")

def cc_library_headers(
        name,
        implementation_deps = [],
        deps = [],
        hdrs = [],
        export_includes = [],
        export_absolute_includes = [],
        export_system_includes = [],
        native_bridge_supported = False,  # TODO: not supported yet.
        sdk_version = "",
        min_sdk_version = "",
        **kwargs):
    "Bazel macro to correspond with the cc_library_headers Soong module."

    cc_library_static(
        name = name,
        implementation_deps = implementation_deps,
        deps = deps,
        export_includes = export_includes,
        export_absolute_includes = export_absolute_includes,
        export_system_includes = export_system_includes,
        hdrs = hdrs,
        native_bridge_supported = native_bridge_supported,
        # do not automatically add libcrt dependency to header libraries
        use_libcrt = False,
        stl = "none",
        sdk_version = sdk_version,
        min_sdk_version = min_sdk_version,
        **kwargs
    )
