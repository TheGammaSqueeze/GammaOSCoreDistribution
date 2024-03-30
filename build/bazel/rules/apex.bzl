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

load(":apex_key.bzl", "ApexKeyInfo")
load(":prebuilt_file.bzl", "PrebuiltFileInfo")
load(":sh_binary.bzl", "ShBinaryInfo")
load("//build/bazel/rules/cc:stripped_cc_common.bzl", "StrippedCcBinaryInfo")
load("//build/bazel/rules/android:android_app_certificate.bzl", "AndroidAppCertificateInfo")
load("//build/bazel/rules/apex:transition.bzl", "apex_transition", "shared_lib_transition_32", "shared_lib_transition_64")
load("//build/bazel/rules/apex:cc.bzl", "ApexCcInfo", "apex_cc_aspect")

DIR_LIB = "lib"
DIR_LIB64 = "lib64"

ApexInfo = provider(
    "ApexInfo has no field currently and is used by apex rule dependents to ensure an attribute is a target of apex rule.",
    fields = {},
)

# Prepare the input files info for bazel_apexer_wrapper to generate APEX filesystem image.
def _prepare_apexer_wrapper_inputs(ctx):
    # dictionary to return in the format:
    # apex_manifest[(image_file_dirname, image_file_basename)] = bazel_output_file
    apex_manifest = {}

    x86_constraint = ctx.attr._x86_constraint[platform_common.ConstraintValueInfo]
    x86_64_constraint = ctx.attr._x86_64_constraint[platform_common.ConstraintValueInfo]
    arm_constraint = ctx.attr._arm_constraint[platform_common.ConstraintValueInfo]
    arm64_constraint = ctx.attr._arm64_constraint[platform_common.ConstraintValueInfo]

    if ctx.target_platform_has_constraint(x86_constraint):
        _add_libs_32_target(ctx, "x86", apex_manifest)
    elif ctx.target_platform_has_constraint(x86_64_constraint):
        _add_libs_64_target(ctx, "x86", "x86_64", apex_manifest)
    elif ctx.target_platform_has_constraint(arm_constraint):
        _add_libs_32_target(ctx, "arm", apex_manifest)
    elif ctx.target_platform_has_constraint(arm64_constraint):
        _add_libs_64_target(ctx, "arm", "arm64", apex_manifest)

    # Handle prebuilts
    for dep in ctx.attr.prebuilts:
        prebuilt_file_info = dep[PrebuiltFileInfo]
        if prebuilt_file_info.filename:
            filename = prebuilt_file_info.filename
        else:
            filename = dep.label.name
        apex_manifest[(prebuilt_file_info.dir, filename)] = prebuilt_file_info.src

    # Handle binaries
    for dep in ctx.attr.binaries:
        if ShBinaryInfo in dep:
            # sh_binary requires special handling on directory/filename construction.
            sh_binary_info = dep[ShBinaryInfo]
            default_info = dep[DefaultInfo]
            if sh_binary_info != None:
                directory = "bin"
                if sh_binary_info.sub_dir != None and sh_binary_info.sub_dir != "":
                    directory = "/".join([directory, sh_binary_info.sub_dir])

                if sh_binary_info.filename != None and sh_binary_info.filename != "":
                    filename = sh_binary_info.filename
                else:
                    filename = dep.label.name

                apex_manifest[(directory, filename)] = default_info.files_to_run.executable
        elif CcInfo in dep:
            # cc_binary just takes the final executable from the runfiles.
            apex_manifest[("bin", dep.label.name)] = dep[DefaultInfo].files_to_run.executable

    apex_content_inputs = []

    bazel_apexer_wrapper_manifest = ctx.actions.declare_file("%s_bazel_apexer_wrapper_manifest" % ctx.attr.name)
    file_lines = []

    # Store the apex file target directory, file name and the path in the source tree in a file.
    # This file will be read by the bazel_apexer_wrapper to create the apex input directory.
    # Here is an example:
    # {etc/tz,tz_version,system/timezone/output_data/version/tz_version}
    for (apex_dirname, apex_basename), bazel_input_file in apex_manifest.items():
        apex_content_inputs.append(bazel_input_file)
        file_lines += [",".join([apex_dirname, apex_basename, bazel_input_file.path])]

    ctx.actions.write(bazel_apexer_wrapper_manifest, "\n".join(file_lines))

    return apex_content_inputs, bazel_apexer_wrapper_manifest

def _add_libs_32_target(ctx, key, apex_manifest):
    if len(ctx.split_attr.native_shared_libs_32.keys()) > 0:
        _add_lib_file(DIR_LIB, ctx.split_attr.native_shared_libs_32[key], apex_manifest)

def _add_libs_64_target(ctx, key_32, key_64, apex_manifest):
    _add_libs_32_target(ctx, key_32, apex_manifest)
    if len(ctx.split_attr.native_shared_libs_64.keys()) > 0:
        _add_lib_file(DIR_LIB64, ctx.split_attr.native_shared_libs_64[key_64], apex_manifest)

def _add_lib_file(dir, libs, apex_manifest):
    for dep in libs:
        apex_cc_info = dep[ApexCcInfo]
        for lib_file in apex_cc_info.transitive_shared_libs.to_list():
            apex_manifest[(dir, lib_file.basename)] = lib_file

# conv_apex_manifest - Convert the JSON APEX manifest to protobuf, which is needed by apexer.
def _convert_apex_manifest_json_to_pb(ctx, apex_toolchain):
    apex_manifest_json = ctx.file.manifest
    apex_manifest_pb = ctx.actions.declare_file("apex_manifest.pb")

    ctx.actions.run(
        outputs = [apex_manifest_pb],
        inputs = [ctx.file.manifest],
        executable = apex_toolchain.conv_apex_manifest,
        arguments = [
            "proto",
            apex_manifest_json.path,
            "-o",
            apex_manifest_pb.path,
        ],
        mnemonic = "ConvApexManifest",
    )

    return apex_manifest_pb

# apexer - generate the APEX file.
def _run_apexer(ctx, apex_toolchain, apex_content_inputs, bazel_apexer_wrapper_manifest, apex_manifest_pb):
    # Inputs
    file_contexts = ctx.file.file_contexts
    apex_key_info = ctx.attr.key[ApexKeyInfo]
    privkey = apex_key_info.private_key
    pubkey = apex_key_info.public_key
    android_jar = apex_toolchain.android_jar
    android_manifest = ctx.file.android_manifest

    # Outputs
    apex_output_file = ctx.actions.declare_file(ctx.attr.name + ".apex.unsigned")

    # Arguments
    args = ctx.actions.args()
    args.add_all(["--manifest", apex_manifest_pb.path])
    args.add_all(["--file_contexts", file_contexts.path])
    args.add_all(["--key", privkey.path])
    args.add_all(["--pubkey", pubkey.path])
    min_sdk_version = ctx.attr.min_sdk_version

    # TODO(b/215339575): This is a super rudimentary way to convert "current" to a numerical number.
    # Generalize this to API level handling logic in a separate Starlark utility, preferably using
    # API level maps dumped from api_levels.go
    if min_sdk_version == "current":
        min_sdk_version = "10000"
    args.add_all(["--min_sdk_version", min_sdk_version])
    args.add_all(["--bazel_apexer_wrapper_manifest", bazel_apexer_wrapper_manifest])
    args.add_all(["--apexer_path", apex_toolchain.apexer])

    # apexer needs the list of directories containing all auxilliary tools invoked during
    # the creation of an apex
    avbtool_files = apex_toolchain.avbtool[DefaultInfo].files_to_run
    e2fsdroid_files = apex_toolchain.e2fsdroid[DefaultInfo].files_to_run
    mke2fs_files = apex_toolchain.mke2fs[DefaultInfo].files_to_run
    resize2fs_files = apex_toolchain.resize2fs[DefaultInfo].files_to_run
    apexer_tool_paths = [
        # These are built by make_injection
        apex_toolchain.apexer.dirname,

        # These are real Bazel targets
        apex_toolchain.aapt2.dirname,
        avbtool_files.executable.dirname,
        e2fsdroid_files.executable.dirname,
        mke2fs_files.executable.dirname,
        resize2fs_files.executable.dirname,
    ]

    args.add_all(["--apexer_tool_path", ":".join(apexer_tool_paths)])
    args.add_all(["--apex_output_file", apex_output_file])

    if android_manifest != None:
        args.add_all(["--android_manifest", android_manifest.path])

    inputs = apex_content_inputs + [
        bazel_apexer_wrapper_manifest,
        apex_manifest_pb,
        file_contexts,
        privkey,
        pubkey,
        android_jar,
    ]

    tools = [
        avbtool_files,
        e2fsdroid_files,
        mke2fs_files,
        resize2fs_files,
        apex_toolchain.aapt2,

        apex_toolchain.apexer,
        apex_toolchain.sefcontext_compile,
    ]

    if android_manifest != None:
        inputs.append(android_manifest)

    ctx.actions.run(
        inputs = inputs,
        tools = tools,
        outputs = [apex_output_file],
        executable = ctx.executable._bazel_apexer_wrapper,
        arguments = [args],
        mnemonic = "BazelApexerWrapper",
    )

    return apex_output_file

# Sign a file with signapk.
def _run_signapk(ctx, unsigned_file, signed_file, private_key, public_key, mnemonic):
    # Inputs
    inputs = [
        unsigned_file,
        private_key,
        public_key,
        ctx.executable._signapk,
    ]

    # Outputs
    outputs = [signed_file]

    # Arguments
    args = ctx.actions.args()
    args.add_all(["-a", 4096])
    args.add_all(["--align-file-size"])
    args.add_all([public_key, private_key])
    args.add_all([unsigned_file, signed_file])

    ctx.actions.run(
        inputs = inputs,
        outputs = outputs,
        executable = ctx.executable._signapk,
        arguments = [args],
        mnemonic = mnemonic,
    )

    return signed_file

# Compress a file with apex_compression_tool.
def _run_apex_compression_tool(ctx, apex_toolchain, input_file, output_file_name):
    # Inputs
    inputs = [
        input_file,
    ]

    avbtool_files = apex_toolchain.avbtool[DefaultInfo].files_to_run
    tools = [
        avbtool_files,
        apex_toolchain.apex_compression_tool,
        apex_toolchain.soong_zip,
    ]

    # Outputs
    compressed_file = ctx.actions.declare_file(output_file_name)
    outputs = [compressed_file]

    # Arguments
    args = ctx.actions.args()
    args.add_all(["compress"])
    tool_dirs = [apex_toolchain.soong_zip.dirname, avbtool_files.executable.dirname]
    args.add_all(["--apex_compression_tool", ":".join(tool_dirs)])
    args.add_all(["--input", input_file])
    args.add_all(["--output", compressed_file])

    ctx.actions.run(
        inputs = inputs,
        tools = tools,
        outputs = outputs,
        executable = apex_toolchain.apex_compression_tool,
        arguments = [args],
        mnemonic = "BazelApexCompressing",
    )
    return compressed_file

# See the APEX section in the README on how to use this rule.
def _apex_rule_impl(ctx):
    apex_toolchain = ctx.toolchains["//build/bazel/rules/apex:apex_toolchain_type"].toolchain_info

    apex_content_inputs, bazel_apexer_wrapper_manifest = _prepare_apexer_wrapper_inputs(ctx)
    apex_manifest_pb = _convert_apex_manifest_json_to_pb(ctx, apex_toolchain)

    unsigned_apex_output_file = _run_apexer(ctx, apex_toolchain, apex_content_inputs, bazel_apexer_wrapper_manifest, apex_manifest_pb)

    apex_cert_info = ctx.attr.certificate[AndroidAppCertificateInfo]
    private_key = apex_cert_info.pk8
    public_key = apex_cert_info.pem

    signed_apex = ctx.outputs.apex_output
    _run_signapk(ctx, unsigned_apex_output_file, signed_apex, private_key, public_key, "BazelApexSigning")
    output_file = signed_apex

    if ctx.attr.compressible:
        compressed_apex_output_file = _run_apex_compression_tool(ctx, apex_toolchain, signed_apex, ctx.attr.name + ".capex.unsigned")
        signed_capex = ctx.outputs.capex_output
        _run_signapk(ctx, compressed_apex_output_file, signed_capex, private_key, public_key, "BazelCompressedApexSigning")

    files_to_build = depset([output_file])
    return [DefaultInfo(files = files_to_build), ApexInfo()]

_apex = rule(
    implementation = _apex_rule_impl,
    attrs = {
        "manifest": attr.label(allow_single_file = [".json"]),
        "android_manifest": attr.label(allow_single_file = [".xml"]),
        "file_contexts": attr.label(allow_single_file = True, mandatory = True),
        "key": attr.label(providers = [ApexKeyInfo]),
        "certificate": attr.label(providers = [AndroidAppCertificateInfo]),
        "min_sdk_version": attr.string(default = "current"),
        "updatable": attr.bool(default = True),
        "installable": attr.bool(default = True),
        "compressible": attr.bool(default = False),
        "native_shared_libs_32": attr.label_list(
            providers = [ApexCcInfo],
            aspects = [apex_cc_aspect],
            cfg = shared_lib_transition_32,
            doc = "The libs compiled for 32-bit",
        ),
        "native_shared_libs_64": attr.label_list(
            providers = [ApexCcInfo],
            aspects = [apex_cc_aspect],
            cfg = shared_lib_transition_64,
            doc = "The libs compiled for 64-bit",
        ),
        "binaries": attr.label_list(
            providers = [
                # The dependency must produce _all_ of the providers in _one_ of these lists.
                [ShBinaryInfo],  # sh_binary
                [StrippedCcBinaryInfo, CcInfo],  # cc_binary (stripped)
            ],
            cfg = apex_transition,
        ),
        "prebuilts": attr.label_list(providers = [PrebuiltFileInfo], cfg = apex_transition),
        "apex_output": attr.output(doc = "signed .apex output"),
        "capex_output": attr.output(doc = "signed .capex output"),

        # Required to use apex_transition. This is an acknowledgement to the risks of memory bloat when using transitions.
        "_allowlist_function_transition": attr.label(default = "@bazel_tools//tools/allowlists/function_transition_allowlist"),
        "_bazel_apexer_wrapper": attr.label(
            cfg = "host",
            doc = "The apexer wrapper to avoid the problem where symlinks are created inside apex image.",
            executable = True,
            default = "//build/bazel/rules/apex:bazel_apexer_wrapper",
        ),
        "_signapk": attr.label(
            cfg = "host",
            doc = "The signapk tool.",
            executable = True,
            default = "//build/make/tools/signapk",
        ),
        "_x86_constraint": attr.label(
            default = Label("//build/bazel/platforms/arch:x86"),
        ),
        "_x86_64_constraint": attr.label(
            default = Label("//build/bazel/platforms/arch:x86_64"),
        ),
        "_arm_constraint": attr.label(
            default = Label("//build/bazel/platforms/arch:arm"),
        ),
        "_arm64_constraint": attr.label(
            default = Label("//build/bazel/platforms/arch:arm64"),
        ),
    },
    toolchains = ["//build/bazel/rules/apex:apex_toolchain_type"],
    fragments = ["platform"],
)

def apex(
        name,
        manifest = "apex_manifest.json",
        android_manifest = None,
        file_contexts = None,
        key = None,
        certificate = None,
        min_sdk_version = None,
        updatable = True,
        installable = True,
        compressible = False,
        native_shared_libs_32 = [],
        native_shared_libs_64 = [],
        binaries = [],
        prebuilts = [],
        **kwargs):
    "Bazel macro to correspond with the APEX bundle Soong module."

    # If file_contexts is not specified, then use the default from //system/sepolicy/apex.
    # https://cs.android.com/android/platform/superproject/+/master:build/soong/apex/builder.go;l=259-263;drc=b02043b84d86fe1007afef1ff012a2155172215c
    if file_contexts == None:
        file_contexts = "//system/sepolicy/apex:" + name + "-file_contexts"

    apex_output = name + ".apex"
    capex_output = None
    if compressible:
        capex_output = name + ".capex"

    _apex(
        name = name,
        manifest = manifest,
        android_manifest = android_manifest,
        file_contexts = file_contexts,
        key = key,
        certificate = certificate,
        min_sdk_version = min_sdk_version,
        updatable = updatable,
        installable = installable,
        compressible = compressible,
        native_shared_libs_32 = native_shared_libs_32,
        native_shared_libs_64 = native_shared_libs_64,
        binaries = binaries,
        prebuilts = prebuilts,

        # Enables predeclared output builds from command line directly, e.g.
        #
        # $ bazel build //path/to/module:com.android.module.apex
        # $ bazel build //path/to/module:com.android.module.capex
        apex_output = apex_output,
        capex_output = capex_output,
        **kwargs
    )
