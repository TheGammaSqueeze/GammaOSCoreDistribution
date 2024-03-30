"""
Copyright (C) 2022 The Android Open Source Project

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
load("//build/bazel/rules:apex.bzl", "ApexInfo")

def _arch_transition_impl(settings, attr):
    """Implementation of arch_transition.
    Four archs are included for mainline modules: x86, x86_64, arm and arm64.
    """
    return {
        "x86": {
            "//command_line_option:platforms": "//build/bazel/platforms:android_x86",
        },
        "x86_64": {
            "//command_line_option:platforms": "//build/bazel/platforms:android_x86_64",
        },
        "arm": {
            "//command_line_option:platforms": "//build/bazel/platforms:android_arm",
        },
        "arm64": {
            "//command_line_option:platforms": "//build/bazel/platforms:android_arm64",
        },
    }

# Multi-arch transition.
arch_transition = transition(
    implementation = _arch_transition_impl,
    inputs = [],
    outputs = [
        "//command_line_option:platforms",
    ],
)

# Arch to ABI map
_arch_abi_map = {
    "arm64": "arm64-v8a",
    "arm": "armeabi-v7a",
    "x86_64": "x86_64",
    "x86": "x86",
}

def _apex_proto_convert(ctx, arch, module_name, apex_file):
    """Run 'aapt2 convert' to convert resource files to protobuf format."""
    # Inputs
    inputs = [
        apex_file,
        ctx.executable._aapt2,
    ]

    # Outputs
    filename = apex_file.basename
    pos_dot = filename.rindex(".")
    proto_convert_file = ctx.actions.declare_file("/".join([
        module_name,
        arch,
        filename[:pos_dot] + ".pb" + filename[pos_dot:]]))
    outputs = [proto_convert_file]

    # Arguments
    args = ctx.actions.args()
    args.add_all(["convert"])
    args.add_all(["--output-format", "proto"])
    args.add_all([apex_file])
    args.add_all(["-o", proto_convert_file.path])

    ctx.actions.run(
        inputs = inputs,
        outputs = outputs,
        executable = ctx.executable._aapt2,
        arguments = [args],
        mnemonic = "ApexProtoConvert",
    )
    return proto_convert_file

def _apex_base_file(ctx, arch, module_name, apex_proto_file):
    """Run zip2zip to transform the apex file the expected directory structure
    with all files that will be included in the base module of aab file."""

    # Inputs
    inputs = [
        apex_proto_file,
        ctx.executable._zip2zip,
    ]

    # Outputs
    base_file = ctx.actions.declare_file("/".join([module_name, arch, module_name + ".base"]))
    outputs = [base_file]

    # Arguments
    args = ctx.actions.args()
    args.add_all(["-i", apex_proto_file])
    args.add_all(["-o", base_file])
    abi = _arch_abi_map[arch]
    args.add_all([
        "apex_payload.img:apex/%s.img" % abi,
        "apex_build_info.pb:apex/%s.build_info.pb" % abi,
        "apex_manifest.json:root/apex_manifest.json",
        "apex_manifest.pb:root/apex_manifest.pb",
        "AndroidManifest.xml:manifest/AndroidManifest.xml",
        "assets/NOTICE.html.gz:assets/NOTICE.html.gz",
    ])

    ctx.actions.run(
        inputs = inputs,
        outputs = outputs,
        executable = ctx.executable._zip2zip,
        arguments = [args],
        mnemonic = "ApexBaseFile",
    )
    return base_file

def _build_bundle_config(ctx, arch, module_name):
    """Create bundle_config.json as configuration for running bundletool."""
    file_content = {
        "compression": {
            "uncompressed_glob": [
                "apex_payload.img",
                "apex_manifest.*",
            ],
        },
        "apex_config": {},
    }
    bundle_config_file = ctx.actions.declare_file("/".join([module_name, "bundle_config.json"]))
    ctx.actions.write(bundle_config_file, json.encode(file_content))

    return bundle_config_file

def _merge_base_files(ctx, module_name, base_files):
    """Run merge_zips to merge all files created for each arch by _apex_base_file."""

    # Inputs
    inputs = base_files + [ctx.executable._merge_zips]

    # Outputs
    merged_base_file = ctx.actions.declare_file(module_name + "/" + module_name + ".zip")
    outputs = [merged_base_file]

    # Arguments
    args = ctx.actions.args()
    args.add_all(["--ignore-duplicates"])
    args.add_all([merged_base_file])
    args.add_all(base_files)

    ctx.actions.run(
        inputs = inputs,
        outputs = outputs,
        executable = ctx.executable._merge_zips,
        arguments = [args],
        mnemonic = "ApexMergeBaseFiles",
    )
    return merged_base_file

def _apex_bundle(ctx, module_name, merged_base_file, bundle_config_file):
    """Run bundletool to create the aab file."""

    # Inputs
    inputs = [
        bundle_config_file,
        merged_base_file,
        ctx.executable._bundletool,
    ]

    # Outputs
    bundle_file = ctx.actions.declare_file(module_name + "/" + module_name + ".aab")
    outputs = [bundle_file]

    # Arguments
    args = ctx.actions.args()
    args.add_all(["build-bundle"])
    args.add_all(["--config", bundle_config_file])
    args.add_all(["--modules", merged_base_file])
    args.add_all(["--output", bundle_file])

    ctx.actions.run(
        inputs = inputs,
        outputs = outputs,
        executable = ctx.executable._bundletool,
        arguments = [args],
        mnemonic = "ApexBundleFile",
    )
    return bundle_file

def _apex_aab_impl(ctx):
    """Implementation of apex_aab rule, which drives the process of creating aab
    file from apex files created for each arch."""
    apex_base_files = []
    bundle_config_file = None
    module_name = ctx.attr.mainline_module[0].label.name
    for arch in ctx.split_attr.mainline_module:
        apex_file = ctx.split_attr.mainline_module[arch].files.to_list()[0]
        proto_convert_file = _apex_proto_convert(ctx, arch, module_name, apex_file)
        base_file = _apex_base_file(ctx, arch, module_name, proto_convert_file)
        apex_base_files.append(base_file)
        # It is assumed that the bundle config is the same for all products.
        if bundle_config_file == None:
            bundle_config_file = _build_bundle_config(ctx, arch, module_name)

    merged_base_file = _merge_base_files(ctx, module_name, apex_base_files)
    bundle_file = _apex_bundle(ctx, module_name, merged_base_file, bundle_config_file)

    return [DefaultInfo(files = depset([bundle_file]))]

# apex_aab rule creates Android Apk Bundle (.aab) file of the APEX specified in mainline_module.
# There is no equivalent Soong module, and it is currently done in shell script by
# invoking Soong multiple times.
apex_aab = rule(
    implementation = _apex_aab_impl,
    attrs = {
        "mainline_module": attr.label(
            mandatory = True,
            cfg = arch_transition,
            providers = [ApexInfo],
            doc = "The label of a mainline module target",
        ),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
            doc = "Allow transition.",
        ),
        "_zipper": attr.label(
            cfg = "host",
            executable = True,
            default = "@bazel_tools//tools/zip:zipper",
        ),
        "_aapt2": attr.label(
            allow_single_file = True,
            cfg = "host",
            executable = True,
            default = "//prebuilts/sdk/tools:linux/bin/aapt2",
        ),
        "_merge_zips": attr.label(
            allow_single_file = True,
            cfg = "host",
            executable = True,
            default = "//prebuilts/build-tools:linux-x86/bin/merge_zips",
        ),
        "_zip2zip": attr.label(
            allow_single_file = True,
            cfg = "host",
            executable = True,
            default = "//prebuilts/build-tools:linux-x86/bin/zip2zip",
        ),
        "_bundletool": attr.label(
            cfg = "host",
            executable = True,
            default = "//prebuilts/bundletool",
        ),
    },
)
