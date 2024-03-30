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

load("@bazel_skylib//lib:dicts.bzl", "dicts")
load("//build/bazel/platforms:rule_utilities.bzl", "ARCH_CONSTRAINT_ATTRS", "get_arch")

# This file contains the implementation for the cc_stub_library rule.
#
# TODO(b/207812332):
# - ndk_api_coverage_parser: https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/coverage.go;l=248-262;drc=master

CcStubInfo = provider(
    fields = {
        "stub_map": "The .map file containing library symbols for the specific API version.",
        "version": "The API version of this library.",
        "abi_symbol_list": "A plain-text list of all symbols of this library for the specific API version."
    }
)

def _cc_stub_gen_impl(ctx):
    # The name of this target.
    name = ctx.attr.name

    # All declared outputs of ndkstubgen.
    out_stub_c = ctx.actions.declare_file("/".join([name, "stub.c"]))
    out_stub_map = ctx.actions.declare_file("/".join([name, "stub.map"]))
    out_abi_symbol_list = ctx.actions.declare_file("/".join([name, "abi_symbol_list.txt"]))

    outputs = [out_stub_c, out_stub_map, out_abi_symbol_list]

    arch = get_arch(ctx)

    ndkstubgen_args = ctx.actions.args()
    ndkstubgen_args.add_all(["--arch", arch])
    ndkstubgen_args.add_all(["--api", ctx.attr.version])
    ndkstubgen_args.add_all(["--api-map", ctx.file._api_levels_file])
    # TODO(b/207812332): This always parses and builds the stub library as a dependency of an APEX. Parameterize this
    # for non-APEX use cases.
    ndkstubgen_args.add_all(["--apex", ctx.file.symbol_file])
    ndkstubgen_args.add_all(outputs)
    ctx.actions.run(
        executable = ctx.executable._ndkstubgen,
        inputs = [
            ctx.file.symbol_file,
            ctx.file._api_levels_file,
        ],
        outputs = outputs,
        arguments = [ndkstubgen_args],
    )

    return [
        # DefaultInfo.files contains the .stub.c file only so that this target
        # can be used directly in the srcs of a cc_library.
        DefaultInfo(files = depset([out_stub_c])),
        CcStubInfo(
            stub_map = out_stub_map,
            abi_symbol_list = out_abi_symbol_list,
            version = ctx.attr.version,
        ),
    ]

cc_stub_gen = rule(
    implementation = _cc_stub_gen_impl,
    attrs = dicts.add({
        # Public attributes
        "symbol_file": attr.label(mandatory = True, allow_single_file = [".map.txt"]),
        "version": attr.string(mandatory = True, default = "current"),
        # Private attributes
        "_api_levels_file": attr.label(default = "@soong_injection//api_levels:api_levels.json", allow_single_file = True),
        # TODO(b/199038020): Use //build/soong/cc/ndkstubgen when py_runtime is set up on CI for hermetic python usage.
        # "_ndkstubgen": attr.label(default = "@make_injection//:host/linux-x86/bin/ndkstubgen", executable = True, cfg = "host", allow_single_file = True),
        "_ndkstubgen": attr.label(default = "//build/soong/cc/ndkstubgen", executable = True, cfg = "host"),
    }, ARCH_CONSTRAINT_ATTRS),
)

