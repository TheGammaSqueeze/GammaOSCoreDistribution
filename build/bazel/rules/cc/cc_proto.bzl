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

load("//build/bazel/rules:proto_file_utils.bzl", "proto_file_utils")
load(":cc_library_common.bzl", "create_ccinfo_for_includes")
load(":cc_library_static.bzl", "cc_library_static")
load("@bazel_skylib//lib:paths.bzl", "paths")

CcProtoGenInfo = provider(fields = ["headers", "sources"])

_SOURCES_KEY = "sources"
_HEADERS_KEY = "headers"

def _cc_proto_sources_gen_rule_impl(ctx):
    out_flags = []
    plugin_executable = None
    out_arg = None
    if ctx.attr.plugin:
        plugin_executable = ctx.executable.plugin
    else:
        out_arg = "--cpp_out"
        if ctx.attr.out_format:
            out_flags.append(ctx.attr.out_format)


    srcs = []
    hdrs = []
    includes = []
    for dep in ctx.attr.deps:
        proto_info = dep[ProtoInfo]
        if proto_info.proto_source_root == ".":
            includes.append(paths.join(ctx.label.name, ctx.label.package))
        includes.append(ctx.label.name)
        outs = _generate_cc_proto_action(
            proto_info = proto_info,
            protoc = ctx.executable._protoc,
            ctx = ctx,
            is_cc = True,
            out_flags = out_flags,
            plugin_executable = plugin_executable,
            out_arg = out_arg,
        )
        srcs.extend(outs[_SOURCES_KEY])
        hdrs.extend(outs[_HEADERS_KEY])

    return [
        DefaultInfo(files = depset(direct = srcs + hdrs)),
        create_ccinfo_for_includes(ctx, includes = includes),
        CcProtoGenInfo(
            headers = hdrs,
            sources = srcs,
        ),
    ]

_cc_proto_sources_gen = rule(
    implementation = _cc_proto_sources_gen_rule_impl,
    attrs = {
        "deps": attr.label_list(
            providers = [ProtoInfo],
            doc = """
proto_library or any other target exposing ProtoInfo provider with *.proto files
""",
            mandatory = True,
        ),
        "_protoc": attr.label(
            default = Label("//external/protobuf:aprotoc"),
            executable = True,
            cfg = "exec",
        ),
        "plugin": attr.label(
            executable = True,
            cfg = "exec",
        ),
        "out_format": attr.string(
            doc = """
Optional argument specifying the out format, e.g. lite.
If not provided, defaults to full protos.
""",
        ),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    provides = [CcInfo, CcProtoGenInfo],
)

def _src_extension(is_cc):
    if is_cc:
        return "cc"
    return "c"

def _generate_cc_proto_action(
        proto_info,
        protoc,
        ctx,
        plugin_executable,
        out_arg,
        out_flags,
        is_cc):
    type_dictionary = {
        _SOURCES_KEY: ".pb." + _src_extension(is_cc),
        _HEADERS_KEY: ".pb.h",
    }
    return proto_file_utils.generate_proto_action(
        proto_info,
        protoc,
        ctx,
        type_dictionary,
        out_flags,
        plugin_executable = plugin_executable,
        out_arg = out_arg,
        mnemonic = "CcProtoGen",
    )

def _cc_proto_sources_impl(ctx):
    srcs = ctx.attr.src[CcProtoGenInfo].sources
    return [
        DefaultInfo(files = depset(direct = srcs)),
    ]

_cc_proto_sources = rule(
    implementation = _cc_proto_sources_impl,
    attrs = {
        "src": attr.label(
            providers = [CcProtoGenInfo],
        ),
    },
)

def _cc_proto_headers_impl(ctx):
    hdrs = ctx.attr.src[CcProtoGenInfo].headers
    return [
        DefaultInfo(files = depset(direct = hdrs)),
    ]

_cc_proto_headers = rule(
    implementation = _cc_proto_headers_impl,
    attrs = {
        "src": attr.label(
            providers = [CcProtoGenInfo],
        ),
    },
)

def _cc_proto_library(
        name,
        deps = [],
        plugin = None,
        target_compatible_with = [],
        out_format = None,
        proto_dep = None):
    proto_lib_name = name + "_proto_gen"
    srcs_name = name + "_proto_sources"
    hdrs_name = name + "_proto_headers"

    _cc_proto_sources_gen(
        name = proto_lib_name,
        deps = deps,
        plugin = plugin,
        out_format = out_format,
    )

    _cc_proto_sources(
        name = srcs_name,
        src = proto_lib_name,
    )

    _cc_proto_headers(
        name = hdrs_name,
        src = proto_lib_name,
    )

    cc_library_static(
        name = name,
        srcs = [":" + srcs_name],
        hdrs = [":" + hdrs_name],
        deps = [
            proto_lib_name,
            proto_dep,
        ],
        local_includes = ["."],
        target_compatible_with = target_compatible_with,
    )

def cc_lite_proto_library(
        name,
        deps = [],
        plugin = None,
        target_compatible_with = []):
    _cc_proto_library(
        name,
        deps = deps,
        plugin = plugin,
        target_compatible_with = target_compatible_with,
        out_format = "lite",
        proto_dep = "//external/protobuf:libprotobuf-cpp-lite",
    )

def cc_proto_library(
        name,
        deps = [],
        plugin = None,
        target_compatible_with = []):
    _cc_proto_library(
        name,
        deps = deps,
        plugin = plugin,
        target_compatible_with = target_compatible_with,
        proto_dep = "//external/protobuf:libprotobuf-cpp-full",
    )
