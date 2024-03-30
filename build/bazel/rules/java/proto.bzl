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
load("@bazel_skylib//lib:paths.bzl", "paths")
load(":library.bzl", "java_library")

def _java_proto_sources_gen_rule_impl(ctx):
    out_flags = []
    plugin_executable = None
    out_arg = None
    if ctx.attr.plugin:
        plugin_executable = ctx.executable.plugin
    else:
        out_arg = "--java_out"
        if ctx.attr.out_format:
            out_flags.append(ctx.attr.out_format)

    srcs = []
    for dep in ctx.attr.deps:
        proto_info = dep[ProtoInfo]
        out_jar = _generate_java_proto_action(
            proto_info = proto_info,
            protoc = ctx.executable._protoc,
            ctx = ctx,
            out_flags = out_flags,
            plugin_executable = plugin_executable,
            out_arg = out_arg,
        )
        srcs.append(out_jar)

    return [
        DefaultInfo(files = depset(direct = srcs)),
    ]

_java_proto_sources_gen = rule(
    implementation = _java_proto_sources_gen_rule_impl,
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
    toolchains = ["@bazel_tools//tools/jdk:toolchain_type"],
)

def _generate_java_proto_action(
        proto_info,
        protoc,
        ctx,
        plugin_executable,
        out_arg,
        out_flags):
    return proto_file_utils.generate_jar_proto_action(
        proto_info,
        protoc,
        ctx,
        out_flags,
        plugin_executable = plugin_executable,
        out_arg = out_arg,
        mnemonic = "JavaProtoGen",
    )

def _java_proto_library(
        name,
        deps = [],
        plugin = None,
        target_compatible_with = [],
        out_format = None,
        proto_dep = None):
    proto_sources_name = name + "_proto_gen"

    _java_proto_sources_gen(
        name = proto_sources_name,
        deps = deps,
        plugin = plugin,
        out_format = out_format,
    )

    if proto_dep:
        deps = [proto_dep]
    else:
        deps = []

    java_library(
        name = name,
        srcs = [proto_sources_name],
        deps = deps,
        target_compatible_with = target_compatible_with,
    )

def java_nano_proto_library(
        name,
        deps = [],
        plugin = "//external/protobuf:protoc-gen-javanano",
        target_compatible_with = []):
    _java_proto_library(
        name,
        deps = deps,
        plugin = plugin,
        target_compatible_with = target_compatible_with,
        proto_dep = "//external/protobuf:libprotobuf-java-nano",
    )

def java_micro_proto_library(
        name,
        deps = [],
        plugin = "//external/protobuf:protoc-gen-javamicro",
        target_compatible_with = []):
    _java_proto_library(
        name,
        deps = deps,
        plugin = plugin,
        target_compatible_with = target_compatible_with,
        proto_dep = "//external/protobuf:libprotobuf-java-micro",
    )

def java_lite_proto_library(
        name,
        deps = [],
        plugin = None,
        target_compatible_with = []):
    _java_proto_library(
        name,
        deps = deps,
        plugin = plugin,
        target_compatible_with = target_compatible_with,
        out_format = "lite",
        proto_dep = "//external/protobuf:libprotobuf-java-lite",
    )

def java_stream_proto_library(
        name,
        deps = [],
        plugin = "//frameworks/base/tools/streaming_proto:protoc-gen-javastream",
        target_compatible_with = []):
    _java_proto_library(
        name,
        deps = deps,
        plugin = plugin,
        target_compatible_with = target_compatible_with,
    )

def java_proto_library(
        name,
        deps = [],
        plugin = None,
        target_compatible_with = []):
    _java_proto_library(
        name,
        deps = deps,
        plugin = plugin,
        target_compatible_with = target_compatible_with,
        proto_dep = "//external/protobuf:libprotobuf-java-full",
    )
