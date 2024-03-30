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

load(
    ":cc_library_common.bzl",
    "add_lists_defaulting_to_none",
    "parse_sdk_version",
    "system_dynamic_deps_defaults",
    "system_static_deps_defaults",
)
load(":cc_library_static.bzl", "cc_library_static")
load(":stl.bzl", "shared_stl_deps", "static_binary_stl_deps")
load(":stripped_cc_common.bzl", "stripped_binary")
load(":versioned_cc_common.bzl", "versioned_binary")

def cc_binary(
        name,
        dynamic_deps = [],
        srcs = [],
        srcs_c = [],
        srcs_as = [],
        copts = [],
        cppflags = [],
        conlyflags = [],
        asflags = [],
        deps = [],
        whole_archive_deps = [],
        system_deps = None,
        export_includes = [],
        export_system_includes = [],
        local_includes = [],
        absolute_includes = [],
        linkshared = True,
        linkopts = [],
        rtti = False,
        use_libcrt = True,
        stl = "",
        cpp_std = "",
        additional_linker_inputs = None,
        strip = {},
        features = [],
        target_compatible_with = [],
        sdk_version = "",
        min_sdk_version = "",
        use_version_lib = False,
        **kwargs):
    "Bazel macro to correspond with the cc_binary Soong module."

    root_name = name + "_root"
    unstripped_name = name + "_unstripped"

    toolchain_features = []
    toolchain_features += features

    if linkshared:
        toolchain_features.extend(["dynamic_executable", "dynamic_linker"])
    else:
        toolchain_features.extend(["-dynamic_executable", "-dynamic_linker", "static_executable", "static_flag"])

    if not use_libcrt:
        toolchain_features += ["-use_libcrt"]

    if min_sdk_version:
        toolchain_features += [
            "sdk_version_" + parse_sdk_version(min_sdk_version),
            "-sdk_version_default",
        ]

    system_dynamic_deps = []
    system_static_deps = []
    if system_deps == None:
        if linkshared:
            system_deps = system_dynamic_deps_defaults
        else:
            system_deps = system_static_deps_defaults

    if linkshared:
        system_dynamic_deps = system_deps
    else:
        system_static_deps = system_deps

    stl_static, stl_shared = [], []

    if linkshared:
        stl_static, stl_shared = shared_stl_deps(stl)
    else:
        stl_static = static_binary_stl_deps(stl)

    # The static library at the root of the shared library.
    # This may be distinct from the static version of the library if e.g.
    # the static-variant srcs are different than the shared-variant srcs.
    cc_library_static(
        name = root_name,
        absolute_includes = absolute_includes,
        alwayslink = True,
        asflags = asflags,
        conlyflags = conlyflags,
        copts = copts,
        cpp_std = cpp_std,
        cppflags = cppflags,
        deps = deps + whole_archive_deps + stl_static + system_static_deps,
        dynamic_deps = dynamic_deps,
        features = toolchain_features,
        local_includes = local_includes,
        rtti = rtti,
        srcs = srcs,
        srcs_as = srcs_as,
        srcs_c = srcs_c,
        stl = stl,
        system_dynamic_deps = system_dynamic_deps,
        target_compatible_with = target_compatible_with,
        use_version_lib = use_version_lib,
    )

    binary_dynamic_deps = add_lists_defaulting_to_none(
        dynamic_deps,
        system_dynamic_deps,
        stl_shared,
    )

    native.cc_binary(
        name = unstripped_name,
        deps = [root_name] + deps + system_static_deps + stl_static,
        dynamic_deps = binary_dynamic_deps,
        features = toolchain_features,
        linkopts = linkopts,
        additional_linker_inputs = additional_linker_inputs,
        target_compatible_with = target_compatible_with,
        **kwargs
    )

    versioned_name = name + "_versioned"
    versioned_binary(
        name = versioned_name,
        src = unstripped_name,
        stamp_build_number = use_version_lib,
    )

    stripped_binary(
        name = name,
        src = versioned_name,
        target_compatible_with = target_compatible_with,
    )

