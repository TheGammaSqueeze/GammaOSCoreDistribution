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

load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cpp_toolchain")
load(
    ":cc_library_common.bzl",
    "get_includes_paths",
    "is_external_directory",
    "system_dynamic_deps_defaults",
    "parse_sdk_version")
load(":cc_constants.bzl", "constants")
load(":stl.bzl", "static_stl_deps")

# "cc_object" module copts, taken from build/soong/cc/object.go
_CC_OBJECT_COPTS = ["-fno-addrsig"]

# partialLd module link opts, taken from build/soong/cc/builder.go
# https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/builder.go;l=87;drc=f2be52c4dcc2e3d743318e106633e61de0ad2afd
_CC_OBJECT_LINKOPTS = [
    "-fuse-ld=lld",
    "-nostdlib",
    "-no-pie",
    "-Wl,-r",
]

CcObjectInfo = provider(fields = [
    # The merged compilation outputs for this cc_object and its transitive
    # dependencies.
    "objects",
])

def split_srcs_hdrs(files):
    headers = []
    non_headers_as = []
    non_headers_c = []
    for f in files:
        if f.extension in constants.hdr_exts:
            headers += [f]
        elif f.extension in constants.as_src_exts:
            non_headers_as += [f]
        else:
            non_headers_c += [f]
    return non_headers_c, non_headers_as, headers

def _cc_object_impl(ctx):
    cc_toolchain = ctx.toolchains["//prebuilts/clang/host/linux-x86:nocrt_toolchain"].cc

    extra_features = []

    extra_disabled_features = [
        "disable_pack_relocations",
        "dynamic_executable",
        "dynamic_linker",
        "linker_flags",
        "no_undefined_symbols",
        "pack_dynamic_relocations",
        "strip_debug_symbols",
        # TODO(cparsons): Look into disabling this feature for nocrt toolchain?
        "use_libcrt",
    ]
    if is_external_directory(ctx.label.package):
        extra_disabled_features.append("non_external_compiler_flags")
        extra_features.append("external_compiler_flags")

    if ctx.attr.min_sdk_version:
        extra_disabled_features.append("sdk_version_default")
        extra_features.append("sdk_version_" + parse_sdk_version(ctx.attr.min_sdk_version))

    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
        requested_features = ctx.features + extra_features,
        unsupported_features = ctx.disabled_features + extra_disabled_features,
    )

    compilation_contexts = []
    deps_objects = []
    for obj in ctx.attr.deps:
        compilation_contexts.append(obj[CcInfo].compilation_context)
        deps_objects.append(obj[CcObjectInfo].objects)
    for includes_dep in ctx.attr.includes_deps:
        compilation_contexts.append(includes_dep[CcInfo].compilation_context)

    product_variables = ctx.attr._android_product_variables[platform_common.TemplateVariableInfo]
    asflags = [ctx.expand_make_variables("asflags", flag, product_variables.variables) for flag in ctx.attr.asflags]

    srcs_c, srcs_as, private_hdrs = split_srcs_hdrs(ctx.files.srcs)

    (compilation_context, compilation_outputs_c) = cc_common.compile(
        name = ctx.label.name,
        actions = ctx.actions,
        feature_configuration = feature_configuration,
        cc_toolchain = cc_toolchain,
        srcs = srcs_c,
        includes = get_includes_paths(ctx, ctx.attr.local_includes) + get_includes_paths(ctx, ctx.attr.absolute_includes, package_relative = False),
        public_hdrs = ctx.files.hdrs,
        private_hdrs = private_hdrs,
        user_compile_flags = ctx.attr.copts,
        compilation_contexts = compilation_contexts,
    )

    (compilation_context, compilation_outputs_as) = cc_common.compile(
        name = ctx.label.name,
        actions = ctx.actions,
        feature_configuration = feature_configuration,
        cc_toolchain = cc_toolchain,
        srcs = srcs_as,
        includes = get_includes_paths(ctx, ctx.attr.local_includes) + get_includes_paths(ctx, ctx.attr.absolute_includes, package_relative = False),
        public_hdrs = ctx.files.hdrs,
        private_hdrs = private_hdrs,
        user_compile_flags = ctx.attr.copts + asflags,
        compilation_contexts = compilation_contexts,
    )

    # do not propagate includes
    compilation_context = cc_common.create_compilation_context(
        headers = compilation_context.headers,
        defines = compilation_context.defines,
        local_defines = compilation_context.local_defines,
    )

    objects_to_link = cc_common.merge_compilation_outputs(compilation_outputs = deps_objects + [compilation_outputs_c, compilation_outputs_as])

    user_link_flags = []
    user_link_flags.extend(_CC_OBJECT_LINKOPTS)
    additional_inputs = []

    if ctx.attr.linker_script != None:
        linker_script = ctx.files.linker_script[0]
        user_link_flags.append("-Wl,-T," + linker_script.path)
        additional_inputs.append(linker_script)

    # partially link if there are multiple object files
    if len(objects_to_link.objects) + len(objects_to_link.pic_objects) > 1:
        linking_output = cc_common.link(
            name = ctx.label.name + ".o",
            actions = ctx.actions,
            feature_configuration = feature_configuration,
            cc_toolchain = cc_toolchain,
            user_link_flags = user_link_flags,
            compilation_outputs = objects_to_link,
            additional_inputs = additional_inputs,
        )
        files = depset([linking_output.executable])
    else:
        files = depset(objects_to_link.objects + objects_to_link.pic_objects)

    return [
        DefaultInfo(files = files),
        CcInfo(compilation_context = compilation_context),
        CcObjectInfo(objects = objects_to_link),
    ]

_cc_object = rule(
    implementation = _cc_object_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = constants.all_dot_exts),
        "hdrs": attr.label_list(allow_files = constants.hdr_dot_exts),
        "absolute_includes": attr.string_list(),
        "local_includes": attr.string_list(),
        "copts": attr.string_list(),
        "asflags": attr.string_list(),
        "deps": attr.label_list(providers = [CcInfo, CcObjectInfo]),
        "includes_deps": attr.label_list(providers = [CcInfo]),
        "linker_script": attr.label(allow_single_file = True),
        "sdk_version": attr.string(),
        "min_sdk_version": attr.string(),
        "_android_product_variables": attr.label(
            default = Label("//build/bazel/platforms:android_target_product_vars"),
            providers = [platform_common.TemplateVariableInfo],
        ),
    },
    toolchains = ["//prebuilts/clang/host/linux-x86:nocrt_toolchain"],
    fragments = ["cpp"],
)

def cc_object(
        name,
        copts = [],
        hdrs = [],
        asflags = [],
        srcs = [],
        srcs_as = [],
        deps = [],
        native_bridge_supported = False,  # TODO: not supported yet.
        stl = "",
        system_dynamic_deps = None,
        sdk_version = "",
        min_sdk_version = "",
        **kwargs):
    "Build macro to correspond with the cc_object Soong module."

    if system_dynamic_deps == None:
        system_dynamic_deps = system_dynamic_deps_defaults

    _cc_object(
        name = name,
        hdrs = hdrs,
        asflags = asflags,
        copts = _CC_OBJECT_COPTS + copts,
        srcs = srcs + srcs_as,
        deps = deps,
        includes_deps = static_stl_deps(stl) + system_dynamic_deps,
        sdk_version = sdk_version,
        min_sdk_version = min_sdk_version,
        **kwargs
    )
