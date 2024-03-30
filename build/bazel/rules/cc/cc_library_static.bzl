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
    "create_ccinfo_for_includes",
    "is_external_directory",
    "parse_sdk_version",
    "system_dynamic_deps_defaults",
)
load(":stl.bzl", "static_stl_deps")
load("@bazel_skylib//lib:collections.bzl", "collections")
load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cpp_toolchain")
load("@rules_cc//examples:experimental_cc_shared_library.bzl", "CcSharedLibraryInfo")
load("//build/bazel/product_variables:constants.bzl", "constants")

CcStaticLibraryInfo = provider(fields = ["root_static_archive", "objects"])

def cc_library_static(
        name,
        deps = [],
        implementation_deps = [],
        dynamic_deps = [],
        implementation_dynamic_deps = [],
        whole_archive_deps = [],
        implementation_whole_archive_deps = [],
        system_dynamic_deps = None,
        export_absolute_includes = [],
        export_includes = [],
        export_system_includes = [],
        local_includes = [],
        absolute_includes = [],
        hdrs = [],
        native_bridge_supported = False,  # TODO: not supported yet.
        use_libcrt = True,
        rtti = False,
        stl = "",
        cpp_std = "",
        c_std = "",
        # Flags for C and C++
        copts = [],
        # C++ attributes
        srcs = [],
        cppflags = [],
        # C attributes
        srcs_c = [],
        conlyflags = [],
        # asm attributes
        srcs_as = [],
        asflags = [],
        features = [],
        alwayslink = None,
        target_compatible_with = [],
        # TODO(b/202299295): Handle data attribute.
        data = [],
        sdk_version = "",
        min_sdk_version = "",
        use_version_lib = False):
    "Bazel macro to correspond with the cc_library_static Soong module."

    exports_name = "%s_exports" % name
    locals_name = "%s_locals" % name
    cpp_name = "%s_cpp" % name
    c_name = "%s_c" % name
    asm_name = "%s_asm" % name

    toolchain_features = []
    toolchain_features += features

    if is_external_directory(native.package_name()):
        toolchain_features += [
            "-non_external_compiler_flags",
            "external_compiler_flags",
        ]

    if use_version_lib:
        libbuildversionLabel = "//build/soong/cc/libbuildversion:libbuildversion"
        whole_archive_deps = whole_archive_deps + [libbuildversionLabel]

    if rtti:
        toolchain_features += ["rtti"]
    if not use_libcrt:
        toolchain_features += ["use_libcrt"]
    if cpp_std:
        toolchain_features += [cpp_std, "-cpp_std_default"]
    if c_std:
        toolchain_features += [c_std, "-c_std_default"]

    if min_sdk_version:
        toolchain_features += [
            "sdk_version_" + parse_sdk_version(min_sdk_version),
            "-sdk_version_default",
        ]

    if system_dynamic_deps == None:
        system_dynamic_deps = system_dynamic_deps_defaults

    _cc_includes(
        name = exports_name,
        includes = export_includes,
        absolute_includes = export_absolute_includes,
        system_includes = export_system_includes,
        # whole archive deps always re-export their includes, etc
        deps = deps + whole_archive_deps + dynamic_deps,
        target_compatible_with = target_compatible_with,
    )

    _cc_includes(
        name = locals_name,
        includes = local_includes,
        absolute_includes = absolute_includes,
        deps = implementation_deps + implementation_dynamic_deps + system_dynamic_deps + static_stl_deps(stl) + implementation_whole_archive_deps,
        target_compatible_with = target_compatible_with,
    )

    # Silently drop these attributes for now:
    # - native_bridge_supported
    common_attrs = dict(
        [
            # TODO(b/199917423): This may be superfluous. Investigate and possibly remove.
            ("linkstatic", True),
            ("hdrs", hdrs),
            # Add dynamic_deps to implementation_deps, as the include paths from the
            # dynamic_deps are also needed.
            ("implementation_deps", [locals_name]),
            ("deps", [exports_name]),
            ("features", toolchain_features),
            ("toolchains", ["//build/bazel/platforms:android_target_product_vars"]),
            ("alwayslink", alwayslink),
            ("target_compatible_with", target_compatible_with),
        ],
    )

    native.cc_library(
        name = cpp_name,
        srcs = srcs,
        copts = copts + cppflags,
        **common_attrs
    )
    native.cc_library(
        name = c_name,
        srcs = srcs_c,
        copts = copts + conlyflags,
        **common_attrs
    )
    native.cc_library(
        name = asm_name,
        srcs = srcs_as,
        copts = asflags,
        **common_attrs
    )

    # Root target to handle combining of the providers of the language-specific targets.
    _cc_library_combiner(
        name = name,
        deps = [cpp_name, c_name, asm_name] + whole_archive_deps + implementation_whole_archive_deps,
        target_compatible_with = target_compatible_with,
    )

# Returns a CcInfo object which combines one or more CcInfo objects, except that all
# linker inputs owned by  owners in `old_owner_labels` are relinked and owned by the current target.
#
# This is useful in the "macro with proxy rule" pattern, as some rules upstream
# may expect they are depending directly on a target which generates linker inputs,
# as opposed to a proxy target which is a level of indirection to such a target.
def _cc_library_combiner_impl(ctx):
    old_owner_labels = []
    cc_infos = []
    for dep in ctx.attr.deps:
        old_owner_labels.append(dep.label)
        cc_infos.append(dep[CcInfo])
    combined_info = cc_common.merge_cc_infos(cc_infos = cc_infos)

    objects_to_link = []

    # This is not ideal, as it flattens a depset.
    for old_linker_input in combined_info.linking_context.linker_inputs.to_list():
        if old_linker_input.owner in old_owner_labels:
            for lib in old_linker_input.libraries:
                # These objects will be recombined into the root archive.
                objects_to_link.extend(lib.objects)
        else:
            # Android macros don't handle transitive linker dependencies because
            # it's unsupported in legacy. We may want to change this going forward,
            # but for now it's good to validate that this invariant remains.
            fail("cc_static_library %s given transitive linker dependency from %s" % (ctx.label, old_linker_input.owner))

    cc_toolchain = find_cpp_toolchain(ctx)
    CPP_LINK_STATIC_LIBRARY_ACTION_NAME = "c++-link-static-library"
    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
        requested_features = ctx.features,
        unsupported_features = ctx.disabled_features + ["linker_flags"],
    )

    output_file = ctx.actions.declare_file("lib" + ctx.label.name + ".a")
    linker_input = cc_common.create_linker_input(
        owner = ctx.label,
        libraries = depset(direct = [
            cc_common.create_library_to_link(
                actions = ctx.actions,
                feature_configuration = feature_configuration,
                cc_toolchain = cc_toolchain,
                static_library = output_file,
                objects = objects_to_link,
            ),
        ]),
    )

    linking_context = cc_common.create_linking_context(linker_inputs = depset(direct = [linker_input]))

    archiver_path = cc_common.get_tool_for_action(
        feature_configuration = feature_configuration,
        action_name = CPP_LINK_STATIC_LIBRARY_ACTION_NAME,
    )
    archiver_variables = cc_common.create_link_variables(
        feature_configuration = feature_configuration,
        cc_toolchain = cc_toolchain,
        output_file = output_file.path,
        is_using_linker = False,
    )
    command_line = cc_common.get_memory_inefficient_command_line(
        feature_configuration = feature_configuration,
        action_name = CPP_LINK_STATIC_LIBRARY_ACTION_NAME,
        variables = archiver_variables,
    )
    args = ctx.actions.args()
    args.add_all(command_line)
    args.add_all(objects_to_link)

    ctx.actions.run(
        executable = archiver_path,
        arguments = [args],
        inputs = depset(
            direct = objects_to_link,
            transitive = [
                cc_toolchain.all_files,
            ],
        ),
        outputs = [output_file],
    )
    return [
        DefaultInfo(files = depset(direct = [output_file]), data_runfiles = ctx.runfiles(files = [output_file])),
        CcInfo(compilation_context = combined_info.compilation_context, linking_context = linking_context),
        CcStaticLibraryInfo(root_static_archive = output_file, objects = objects_to_link),
    ]

# A rule which combines objects of oen or more cc_library targets into a single
# static linker input. This outputs a single archive file combining the objects
# of its direct deps, and propagates Cc providers describing that these objects
# should be linked for linking rules upstream.
# This rule is useful for maintaining the illusion that the target's deps are
# comprised by a single consistent rule:
#   - A single archive file is always output by this rule.
#   - A single linker input struct is always output by this rule, and it is 'owned'
#       by this rule.
_cc_library_combiner = rule(
    implementation = _cc_library_combiner_impl,
    attrs = {
        "deps": attr.label_list(providers = [CcInfo]),
        "_cc_toolchain": attr.label(
            default = Label("@local_config_cc//:toolchain"),
            providers = [cc_common.CcToolchainInfo],
        ),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    provides = [CcInfo],
    fragments = ["cpp"],
)

def _cc_includes_impl(ctx):
    return [create_ccinfo_for_includes(
        ctx,
        includes = ctx.attr.includes,
        absolute_includes = ctx.attr.absolute_includes,
        system_includes = ctx.attr.system_includes,
        deps = ctx.attr.deps,
    )]

# Bazel's native cc_library rule supports specifying include paths two ways:
# 1. non-exported includes can be specified via copts attribute
# 2. exported -isystem includes can be specified via includes attribute
#
# In order to guarantee a correct inclusion search order, we need to export
# includes paths for both -I and -isystem; however, there is no native Bazel
# support to export both of these, this rule provides a CcInfo to propagate the
# given package-relative include/system include paths as exec root relative
# include/system include paths.
_cc_includes = rule(
    implementation = _cc_includes_impl,
    attrs = {
        "absolute_includes": attr.string_list(doc = "List of exec-root relative or absolute search paths for headers, usually passed with -I"),
        "includes": attr.string_list(doc = "Package-relative list of search paths for headers, usually passed with -I"),
        "system_includes": attr.string_list(doc = "Package-relative list of search paths for headers, usually passed with -isystem"),
        "deps": attr.label_list(doc = "Re-propagates the includes obtained from these dependencies.", providers = [CcInfo]),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    fragments = ["cpp"],
    provides = [CcInfo],
)
