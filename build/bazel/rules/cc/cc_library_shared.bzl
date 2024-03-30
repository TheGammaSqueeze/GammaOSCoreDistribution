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
    "disable_crt_link",
    "parse_sdk_version",
    "system_dynamic_deps_defaults",
)
load(":cc_library_static.bzl", "cc_library_static")
load(":cc_stub_library.bzl", "CcStubInfo", "cc_stub_gen")
load(":generate_toc.bzl", "shared_library_toc", _CcTocInfo = "CcTocInfo")
load(":stl.bzl", "shared_stl_deps")
load(":stripped_cc_common.bzl", "stripped_shared_library")
load(":versioned_cc_common.bzl", "versioned_shared_library")
load("@rules_cc//examples:experimental_cc_shared_library.bzl", "cc_shared_library", _CcSharedLibraryInfo = "CcSharedLibraryInfo")
load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cpp_toolchain")

CcTocInfo = _CcTocInfo
CcSharedLibraryInfo = _CcSharedLibraryInfo

def cc_library_shared(
        name,
        # Common arguments between shared_root and the shared library
        features = [],
        dynamic_deps = [],
        implementation_dynamic_deps = [],
        linkopts = [],
        target_compatible_with = [],
        # Ultimately _static arguments for shared_root production
        srcs = [],
        srcs_c = [],
        srcs_as = [],
        copts = [],
        cppflags = [],
        conlyflags = [],
        asflags = [],
        hdrs = [],
        implementation_deps = [],
        deps = [],
        whole_archive_deps = [],
        system_dynamic_deps = None,
        export_includes = [],
        export_absolute_includes = [],
        export_system_includes = [],
        local_includes = [],
        absolute_includes = [],
        rtti = False,
        use_libcrt = True,  # FIXME: Unused below?
        stl = "",
        cpp_std = "",
        c_std = "",
        link_crt = True,
        additional_linker_inputs = None,

        # Purely _shared arguments
        strip = {},
        soname = "",

        # TODO(b/202299295): Handle data attribute.
        data = [],
        use_version_lib = False,
        stubs_symbol_file = None,
        stubs_versions = [],
        inject_bssl_hash = False,
        sdk_version = "",
        min_sdk_version = "",
        **kwargs):
    "Bazel macro to correspond with the cc_library_shared Soong module."

    shared_root_name = name + "_root"
    unstripped_name = name + "_unstripped"
    stripped_name = name + "_stripped"
    toc_name = name + "_toc"

    if system_dynamic_deps == None:
        system_dynamic_deps = system_dynamic_deps_defaults

    # Force crtbegin and crtend linking unless explicitly disabled (i.e. bionic
    # libraries do this)
    if link_crt == False:
        features = disable_crt_link(features)

    if min_sdk_version:
        features = features + [
            "sdk_version_" + parse_sdk_version(min_sdk_version),
            "-sdk_version_default",
        ]

    # The static library at the root of the shared library.
    # This may be distinct from the static version of the library if e.g.
    # the static-variant srcs are different than the shared-variant srcs.
    cc_library_static(
        name = shared_root_name,
        hdrs = hdrs,
        srcs = srcs,
        srcs_c = srcs_c,
        srcs_as = srcs_as,
        copts = copts,
        cppflags = cppflags,
        conlyflags = conlyflags,
        asflags = asflags,
        export_includes = export_includes,
        export_absolute_includes = export_absolute_includes,
        export_system_includes = export_system_includes,
        local_includes = local_includes,
        absolute_includes = absolute_includes,
        rtti = rtti,
        stl = stl,
        cpp_std = cpp_std,
        c_std = c_std,
        dynamic_deps = dynamic_deps,
        implementation_deps = implementation_deps,
        implementation_dynamic_deps = implementation_dynamic_deps,
        system_dynamic_deps = system_dynamic_deps,
        deps = deps + whole_archive_deps,
        features = features,
        use_version_lib = use_version_lib,
        target_compatible_with = target_compatible_with,
    )

    stl_static, stl_shared = shared_stl_deps(stl)

    # implementation_deps and deps are to be linked into the shared library via
    # --no-whole-archive. In order to do so, they need to be dependencies of
    # a "root" of the cc_shared_library, but may not be roots themselves.
    # Below we define stub roots (which themselves have no srcs) in order to facilitate
    # this.
    imp_deps_stub = name + "_implementation_deps"
    deps_stub = name + "_deps"
    native.cc_library(
        name = imp_deps_stub,
        deps = implementation_deps + stl_static,
        target_compatible_with = target_compatible_with,
    )
    native.cc_library(
        name = deps_stub,
        deps = deps,
        target_compatible_with = target_compatible_with,
    )

    shared_dynamic_deps = add_lists_defaulting_to_none(
        dynamic_deps,
        system_dynamic_deps,
        implementation_dynamic_deps,
        stl_shared,
    )

    if len(soname) == 0:
        soname = name + ".so"
    soname_flag = "-Wl,-soname," + soname

    cc_shared_library(
        name = unstripped_name,
        user_link_flags = linkopts + [soname_flag],
        # b/184806113: Note this is  a workaround so users don't have to
        # declare all transitive static deps used by this target.  It'd be great
        # if a shared library could declare a transitive exported static dep
        # instead of needing to declare each target transitively.
        static_deps = ["//:__subpackages__"] + [shared_root_name, imp_deps_stub, deps_stub],
        dynamic_deps = shared_dynamic_deps,
        additional_linker_inputs = additional_linker_inputs,
        roots = [shared_root_name, imp_deps_stub, deps_stub] + whole_archive_deps,
        features = features,
        target_compatible_with = target_compatible_with,
        **kwargs
    )

    hashed_name = name + "_hashed"
    _bssl_hash_injection(
        name = hashed_name,
        src = unstripped_name,
        inject_bssl_hash = inject_bssl_hash,
    )

    versioned_name = name + "_versioned"
    versioned_shared_library(
        name = versioned_name,
        src = hashed_name,
        stamp_build_number = use_version_lib,
    )

    stripped_shared_library(
        name = stripped_name,
        src = versioned_name,
        target_compatible_with = target_compatible_with,
        **strip
    )

    shared_library_toc(
        name = toc_name,
        src = stripped_name,
        target_compatible_with = target_compatible_with,
    )

    # Emit the stub version of this library (e.g. for libraries that are
    # provided by the NDK)
    stub_shared_libraries = []
    if stubs_symbol_file and len(stubs_versions) > 0:
        # TODO(b/193663198): This unconditionally creates stubs for every version, but
        # that's not always true depending on whether this module is available
        # on the host, ramdisk, vendor ramdisk. We currently don't have
        # information about the image variant yet, so we'll create stub targets
        # for all shared libraries with the stubs property for now.
        #
        # See: https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/library.go;l=2316-2377;drc=3d3b35c94ed2a3432b2e5e7e969a3a788a7a80b5
        for version in stubs_versions:
            stubs_library_name = "_".join([name, version, "stubs"])
            cc_stub_library_shared(
                name = stubs_library_name,
                stubs_symbol_file = stubs_symbol_file,
                version = version,
                target_compatible_with = target_compatible_with,
                features = features,
            )
            stub_shared_libraries.append(stubs_library_name)

    _cc_library_shared_proxy(
        name = name,
        shared = stripped_name,
        root = shared_root_name,
        table_of_contents = toc_name,
        output_file = soname,
        target_compatible_with = target_compatible_with,
        stub_shared_libraries = stub_shared_libraries,
    )

# cc_stub_library_shared creates a cc_library_shared target, but using stub C source files generated
# from a library's .map.txt files and ndkstubgen. The top level target returns the same
# providers as a cc_library_shared, with the addition of a CcStubInfo
# containing metadata files and versions of the stub library.
def cc_stub_library_shared(name, stubs_symbol_file, version, target_compatible_with, features):
    # Call ndkstubgen to generate the stub.c source file from a .map.txt file. These
    # are accessible in the CcStubInfo provider of this target.
    cc_stub_gen(
        name = name + "_files",
        symbol_file = stubs_symbol_file,
        version = version,
        target_compatible_with = target_compatible_with,
    )

    # The static library at the root of the stub shared library.
    cc_library_static(
        name = name + "_root",
        srcs_c = [name + "_files"],  # compile the stub.c file
        features = disable_crt_link(features) + \
            [
                # Enable the stub library compile flags
                "stub_library",
                # Disable all include-related features to avoid including any headers
                # that may cause conflicting type errors with the symbols in the
                # generated stubs source code.
                #  e.g.
                #  double acos(double); // in header
                #  void acos() {} // in the generated source code
                # See https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/library.go;l=942-946;drc=d8a72d7dc91b2122b7b10b47b80cf2f7c65f9049
                "-toolchain_include_directories",
                "-includes",
                "-include_paths",
            ],
        target_compatible_with = target_compatible_with,
        stl = "none",
        system_dynamic_deps = [],
    )

    # Create a .so for the stub library. This library is self contained, has
    # no deps, and doesn't link against crt.
    cc_shared_library(
        name = name + "_so",
        roots = [name + "_root"],
        features = disable_crt_link(features),
        target_compatible_with = target_compatible_with,
    )

    # Create a target with CcSharedLibraryInfo and CcStubInfo providers.
    _cc_stub_library_shared(
        name = name,
        stub_target = name + "_files",
        library_target = name + "_so",
    )

def _cc_stub_library_shared_impl(ctx):
    return [
        ctx.attr.library_target[DefaultInfo],
        ctx.attr.library_target[CcSharedLibraryInfo],
        ctx.attr.stub_target[CcStubInfo],
    ]

_cc_stub_library_shared = rule(
    implementation = _cc_stub_library_shared_impl,
    doc = "Top level rule to merge CcStubInfo and CcSharedLibraryInfo into a single target",
    attrs = {
        "stub_target": attr.label(mandatory = True),
        "library_target": attr.label(mandatory = True),
    },
)

def _swap_shared_linker_input(ctx, shared_info, new_output):
    old_library_to_link = shared_info.linker_input.libraries[0]

    cc_toolchain = find_cpp_toolchain(ctx)
    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
    )

    new_library_to_link = cc_common.create_library_to_link(
        actions = ctx.actions,
        dynamic_library = new_output,
        feature_configuration = feature_configuration,
        cc_toolchain = cc_toolchain,
    )

    new_linker_input = cc_common.create_linker_input(
        owner = shared_info.linker_input.owner,
        libraries = depset([new_library_to_link]),
    )

    return CcSharedLibraryInfo(
        dynamic_deps = shared_info.dynamic_deps,
        exports = shared_info.exports,
        link_once_static_libs = shared_info.link_once_static_libs,
        linker_input = new_linker_input,
        preloaded_deps = shared_info.preloaded_deps,
    )

CcStubLibrariesInfo = provider(
    fields = {
        "infos": "A list of dict, where each dict contains the CcStubInfo, CcSharedLibraryInfo and DefaultInfo of a version of a stub library.",
    },
)

def _cc_library_shared_proxy_impl(ctx):
    root_files = ctx.attr.root[DefaultInfo].files.to_list()
    shared_files = ctx.attr.shared[DefaultInfo].files.to_list()

    if len(shared_files) != 1:
        fail("Expected only one shared library file")

    shared_lib = shared_files[0]

    ctx.actions.symlink(
        output = ctx.outputs.output_file,
        target_file = shared_lib,
    )

    files = root_files + [ctx.outputs.output_file, ctx.files.table_of_contents[0]]

    stub_library_infos = []
    for stub_library in ctx.attr.stub_shared_libraries:
        providers = {
            "CcStubInfo": stub_library[CcStubInfo],
            "CcSharedLibraryInfo": stub_library[CcSharedLibraryInfo],
            "DefaultInfo": stub_library[DefaultInfo],
        }
        stub_library_infos.append(providers)

    return [
        DefaultInfo(
            files = depset(direct = files),
            runfiles = ctx.runfiles(files = [ctx.outputs.output_file]),
        ),
        _swap_shared_linker_input(ctx, ctx.attr.shared[CcSharedLibraryInfo], ctx.outputs.output_file),
        ctx.attr.table_of_contents[CcTocInfo],
        # Propagate only includes from the root. Do not re-propagate linker inputs.
        CcInfo(compilation_context = ctx.attr.root[CcInfo].compilation_context),
        CcStubLibrariesInfo(infos = stub_library_infos),
    ]

_cc_library_shared_proxy = rule(
    implementation = _cc_library_shared_proxy_impl,
    attrs = {
        "shared": attr.label(mandatory = True, providers = [CcSharedLibraryInfo]),
        "root": attr.label(mandatory = True, providers = [CcInfo]),
        "output_file": attr.output(mandatory = True),
        "table_of_contents": attr.label(
            mandatory = True,
            # TODO(b/217908237): reenable allow_single_file
            # allow_single_file = True,
            providers = [CcTocInfo],
        ),
        "stub_shared_libraries": attr.label_list(providers = [CcStubInfo, CcSharedLibraryInfo]),
    },
    fragments = ["cpp"],
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
)

def _bssl_hash_injection_impl(ctx):
    if len(ctx.files.src) != 1:
        fail("Expected only one shared library file")

    hashed_file = ctx.files.src[0]
    if ctx.attr.inject_bssl_hash:
        hashed_file = ctx.actions.declare_file("lib" + ctx.attr.name + ".so")
        args = ctx.actions.args()
        args.add_all(["-sha256"])
        args.add_all(["-in-object", ctx.files.src[0]])
        args.add_all(["-o", hashed_file])

        ctx.actions.run(
            inputs = ctx.files.src,
            outputs = [hashed_file],
            executable = ctx.executable._bssl_inject_hash,
            arguments = [args],
            tools = [ctx.executable._bssl_inject_hash],
            mnemonic = "BsslInjectHash",
        )

    return [
        DefaultInfo(files = depset([hashed_file])),
        ctx.attr.src[CcSharedLibraryInfo],
    ]

_bssl_hash_injection = rule(
    implementation = _bssl_hash_injection_impl,
    attrs = {
        "src": attr.label(
            mandatory = True,
            # TODO(b/217908237): reenable allow_single_file
            # allow_single_file = True,
            providers = [CcSharedLibraryInfo],
        ),
        "inject_bssl_hash": attr.bool(
            default = False,
            doc = "Whether inject BSSL hash",
        ),
        "_bssl_inject_hash": attr.label(
            cfg = "exec",
            doc = "The BSSL hash injection tool.",
            executable = True,
            default = "//prebuilts/build-tools:linux-x86/bin/bssl_inject_hash",
            allow_single_file = True,
        ),
    },
)
