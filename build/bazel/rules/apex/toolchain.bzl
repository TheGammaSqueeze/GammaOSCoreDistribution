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

ApexToolchainInfo = provider(
    doc = "APEX toolchain",
    fields = [
        "aapt2",
        "avbtool",
        "apexer",
        "mke2fs",
        "resize2fs",
        "e2fsdroid",
        "sefcontext_compile",
        "conv_apex_manifest",
        "android_jar",
        "apex_compression_tool",
        "soong_zip",
    ],
)

def _apex_toolchain_impl(ctx):
    toolchain_info = platform_common.ToolchainInfo(
        toolchain_info = ApexToolchainInfo(
            aapt2 = ctx.file.aapt2,
            avbtool = ctx.attr.avbtool,
            apexer = ctx.file.apexer,
            mke2fs = ctx.attr.mke2fs,
            resize2fs = ctx.attr.resize2fs,
            e2fsdroid = ctx.attr.e2fsdroid,
            sefcontext_compile = ctx.file.sefcontext_compile,
            conv_apex_manifest = ctx.file.conv_apex_manifest,
            android_jar = ctx.file.android_jar,
            apex_compression_tool = ctx.file.apex_compression_tool,
            soong_zip = ctx.file.soong_zip,
        ),
    )
    return [toolchain_info]

apex_toolchain = rule(
    implementation = _apex_toolchain_impl,
    attrs = {
        "aapt2": attr.label(allow_single_file = True, cfg = "host", executable = True),
        "avbtool": attr.label(cfg = "host", executable = True),
        "apexer": attr.label(allow_single_file = True, cfg = "host", executable = True),
        "mke2fs": attr.label(cfg = "host", executable = True),
        "resize2fs": attr.label(cfg = "host", executable = True),
        "e2fsdroid": attr.label(cfg = "host", executable = True),
        "sefcontext_compile": attr.label(allow_single_file = True, cfg = "host", executable = True),
        "conv_apex_manifest": attr.label(allow_single_file = True, cfg = "host", executable = True),
        "android_jar": attr.label(allow_single_file = True, cfg = "host"),
        "apex_compression_tool": attr.label(allow_single_file = True, cfg = "host", executable = True),
        # soong_zip is added as a dependency of apex_compression_tool which uses
        # soong_zip to compress APEX files. avbtool is also used in apex_compression tool
        # and has been added to apex toolchain previously.
        "soong_zip": attr.label(allow_single_file = True, cfg = "host", executable = True),
    },
)
