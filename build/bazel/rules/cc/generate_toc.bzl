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

"""A macro to generate table of contents files of symbols from a shared library."""

CcTocInfo = provider(
    "Information about the table of contents of a shared library",
    fields = {
        "toc": "The single file for the table of contents",
    },
)

def _shared_library_toc_impl(ctx):
    so_name = "lib" + ctx.attr.name + ".so"
    toc_name = so_name + ".toc"
    out_file = ctx.actions.declare_file(toc_name)
    d_file = ctx.actions.declare_file(toc_name + ".d")
    ctx.actions.run(
        env = {
            "CLANG_BIN": ctx.executable._readelf.dirname,
        },
        inputs = ctx.files.src,
        tools = [
            ctx.executable._readelf,
        ],
        outputs = [out_file, d_file],
        executable = ctx.executable._toc_script,
        arguments = [
            # Only Linux shared libraries for now.
            "--elf",
            "-i",
            ctx.files.src[0].path,
            "-o",
            out_file.path,
            "-d",
            d_file.path,
        ],
    )

    return [
        CcTocInfo(toc = out_file),
        DefaultInfo(files = depset([out_file])),
    ]

shared_library_toc = rule(
    implementation = _shared_library_toc_impl,
    attrs = {
        "src": attr.label(
            # TODO(b/217908237): reenable allow_single_file
            # allow_single_file = True,
            mandatory = True,
        ),
        "_toc_script": attr.label(
            cfg = "host",
            executable = True,
            allow_single_file = True,
            default = "//build/soong/scripts:toc.sh",
        ),
        "_readelf": attr.label(
            cfg = "host",
            executable = True,
            allow_single_file = True,
            default = "//prebuilts/clang/host/linux-x86:llvm-readelf",
        ),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
)
