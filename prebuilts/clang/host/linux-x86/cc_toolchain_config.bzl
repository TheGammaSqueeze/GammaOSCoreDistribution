load(
    "@bazel_tools//tools/cpp:cc_toolchain_config_lib.bzl",
    "action_config",
    "tool",
    "tool_path",
)
load(
    ":cc_toolchain_constants.bzl",
    _actions = "actions",
    _bionic_crt = "bionic_crt",
    _flags = "flags",
    _generated_constants = "generated_constants",
    _enabled_features = "enabled_features",
)
load(":cc_toolchain_features.bzl", "get_features")

# Clang-specific configuration.
_ClangVersionInfo = provider(fields = ["directory", "includes"])

def _clang_version_impl(ctx):
    directory = ctx.file.directory
    provider = _ClangVersionInfo(
        directory = directory,
        includes = [directory.short_path + "/" + d for d in ctx.attr.includes],
    )
    return [provider]

clang_version = rule(
    implementation = _clang_version_impl,
    attrs = {
        "directory": attr.label(allow_single_file = True, mandatory = True),
        "includes": attr.string_list(default = []),
    },
)

def _tool_paths(clang_version_info):
    return [
        tool_path(
            name = "gcc",
            path = clang_version_info.directory.basename + "/bin/clang",
        ),
        tool_path(
            name = "ld",
            path = clang_version_info.directory.basename + "/bin/ld.lld",
        ),
        tool_path(
            name = "ar",
            path = clang_version_info.directory.basename + "/bin/llvm-ar",
        ),
        tool_path(
            name = "cpp",
            path = "/bin/false",
        ),
        tool_path(
            name = "gcov",
            path = "/bin/false",
        ),
        tool_path(
            name = "nm",
            path = clang_version_info.directory.basename + "/bin/llvm-nm",
        ),
        tool_path(
            name = "objdump",
            path = clang_version_info.directory.basename + "/bin/llvm-objdump",
        ),
        # Soong has a wrapper around strip.
        # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/strip.go;l=62;drc=master
        # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/builder.go;l=991-1025;drc=master
        tool_path(
            name = "strip",
            path = clang_version_info.directory.basename + "/bin/llvm-strip",
        ),
        tool_path(
            name = "clang++",
            path = clang_version_info.directory.basename + "/bin/clang++",
        ),
    ]

# Set tools used for all actions in Android's C++ builds.
def _create_action_configs(tool_paths, target_os):
    action_configs = []

    tool_name_to_tool = {}
    for tool_path in tool_paths:
        tool_name_to_tool[tool_path.name] = tool(path = tool_path.path)

    # use clang for assembler actions
    # https://cs.android.com/android/_/android/platform/build/soong/+/a14b18fb31eada7b8b58ecd469691c20dcb371b3:cc/builder.go;l=616;drc=769a51cc6aa9402c1c55e978e72f528c26b6a48f
    for action_name in _actions.assemble:
        action_configs.append(action_config(
            action_name = action_name,
            enabled = True,
            tools = [tool_name_to_tool["gcc"]],
            implies = [
                "user_compile_flags",
                "compiler_input_flags",
                "compiler_output_flags",
            ],
        ))

    # use clang++ for compiling C++
    # https://cs.android.com/android/_/android/platform/build/soong/+/a14b18fb31eada7b8b58ecd469691c20dcb371b3:cc/builder.go;l=627;drc=769a51cc6aa9402c1c55e978e72f528c26b6a48f
    action_configs.append(action_config(
        action_name = _actions.cpp_compile,
        enabled = True,
        tools = [tool_name_to_tool["clang++"]],
        implies = [
            "user_compile_flags",
            "compiler_input_flags",
            "compiler_output_flags",
        ],
    ))

    # use clang for compiling C
    # https://cs.android.com/android/_/android/platform/build/soong/+/a14b18fb31eada7b8b58ecd469691c20dcb371b3:cc/builder.go;l=623;drc=769a51cc6aa9402c1c55e978e72f528c26b6a48f
    action_configs.append(action_config(
        action_name = _actions.c_compile,
        enabled = True,
        # this is clang, but needs to be called gcc for legacy reasons.
        # to avoid this, we need to set `no_legacy_features`: b/201257475
        # http://google3/third_party/bazel/src/main/java/com/google/devtools/build/lib/rules/cpp/CcModule.java;l=1106-1122;rcl=398974497
        # http://google3/third_party/bazel/src/main/java/com/google/devtools/build/lib/rules/cpp/CcModule.java;l=1185-1187;rcl=398974497
        tools = [tool_name_to_tool["gcc"]],
        implies = [
            "user_compile_flags",
            "compiler_input_flags",
            "compiler_output_flags",
        ],
    ))

    rpath_features = []
    if target_os not in ("android", "windows"):
        rpath_features.append("runtime_library_search_directories")

    # use clang++ for dynamic linking
    # https://cs.android.com/android/_/android/platform/build/soong/+/a14b18fb31eada7b8b58ecd469691c20dcb371b3:cc/builder.go;l=790;drc=769a51cc6aa9402c1c55e978e72f528c26b6a48f
    for action_name in [_actions.cpp_link_dynamic_library, _actions.cpp_link_nodeps_dynamic_library]:
        action_configs.append(action_config(
            action_name = action_name,
            enabled = True,
            tools = [tool_name_to_tool["clang++"]],
            implies = [
                "shared_flag",
                "linkstamps",
                "output_execpath_flags",
                "library_search_directories",
                "libraries_to_link",
                "pic",
                "user_link_flags",
                "linker_param_file",
            ] + rpath_features,
        ))

    # use clang++ for linking cc executables
    action_configs.append(action_config(
        action_name = _actions.cpp_link_executable,
        enabled = True,
        tools = [tool_name_to_tool["clang++"]],
        implies = [
            "linkstamps",
            "output_execpath_flags",
            "library_search_directories",
            "libraries_to_link",
            "user_link_flags",
            "linker_param_file",
        ] + rpath_features,
    ))

    # use llvm-ar for creating static archives
    action_configs.append(action_config(
        action_name = _actions.cpp_link_static_library,
        enabled = True,
        tools = [tool_name_to_tool["ar"]],
        implies = ["archiver_flags"],
    ))

    # unused, but Bazel complains if there isn't an action config for strip
    action_configs.append(action_config(
        action_name = _actions.strip,
        enabled = True,
        tools = [tool_name_to_tool["strip"]],
        # This doesn't imply any feature, because Bazel currently mimics
        # Soong by running strip actions in a rule (stripped_shared_library).
    ))

    return action_configs

def _cc_toolchain_config_impl(ctx):
    clang_version_info = ctx.attr.clang_version[_ClangVersionInfo]
    tool_paths = _tool_paths(clang_version_info)

    action_configs = _create_action_configs(tool_paths, ctx.attr.target_os)

    # This is so that Bazel doesn't validate .d files against the set of headers
    # declared in BUILD files (Blueprint files don't contain that data)
    builtin_include_dirs = ["/"]
    builtin_include_dirs.extend(clang_version_info.includes)

    # b/186035856: Do not add anything to this list.
    builtin_include_dirs.extend(_generated_constants.CommonGlobalIncludes)

    crt_files = struct(
        shared_library_crtbegin = ctx.file.shared_library_crtbegin,
        shared_library_crtend = ctx.file.shared_library_crtend,
        shared_binary_crtbegin = ctx.file.shared_binary_crtbegin,
        static_binary_crtbegin = ctx.file.static_binary_crtbegin,
        binary_crtend = ctx.file.binary_crtend,
    )

    features = get_features(
        ctx.attr.target_os,
        ctx.attr.target_arch,
        ctx.attr.target_flags,
        ctx.attr.compiler_flags,
        ctx.attr.linker_flags,
        builtin_include_dirs,
        ctx.file.libclang_rt_builtin,
        crt_files,
        ctx.attr.rtti_toggle,
    )

    return cc_common.create_cc_toolchain_config_info(
        ctx = ctx,
        toolchain_identifier = ctx.attr.toolchain_identifier,
        tool_paths = tool_paths,
        features = features,
        action_configs = action_configs,
        cxx_builtin_include_directories = builtin_include_dirs,
        target_cpu = "_".join([ctx.attr.target_os, ctx.attr.target_arch]),
        # The attributes below are required by the constructor, but don't
        # affect actions at all.
        host_system_name = "__toolchain_host_system_name__",
        target_system_name = "__toolchain_target_system_name__",
        target_libc = "__toolchain_target_libc__",
        compiler = "__toolchain_compiler__",
        abi_version = "__toolchain_abi_version__",
        abi_libc_version = "__toolchain_abi_libc_version__",
    )

_cc_toolchain_config = rule(
    implementation = _cc_toolchain_config_impl,
    attrs = {
        "target_os": attr.string(mandatory = True),
        "target_arch": attr.string(mandatory = True),
        "toolchain_identifier": attr.string(mandatory = True),
        "clang_version": attr.label(mandatory = True, providers = [_ClangVersionInfo]),
        "target_flags": attr.string_list(default = []),
        "compiler_flags": attr.string_list(default = []),
        "linker_flags": attr.string_list(default = []),
        "libclang_rt_builtin": attr.label(allow_single_file = True),
        # crtbegin and crtend libraries for compiling cc_library_shared and
        # cc_binary against the Bionic runtime
        "shared_library_crtbegin": attr.label(allow_single_file = True, cfg = "target"),
        "shared_library_crtend": attr.label(allow_single_file = True, cfg = "target"),
        "shared_binary_crtbegin": attr.label(allow_single_file = True, cfg = "target"),
        "static_binary_crtbegin": attr.label(allow_single_file = True, cfg = "target"),
        "binary_crtend": attr.label(allow_single_file = True, cfg = "target"),
        "rtti_toggle": attr.bool(default = True),
    },
    provides = [CcToolchainConfigInfo],
)

# macro to expand feature flags for a toolchain
# we do not pass these directly to the toolchain so the order can
# be specified per toolchain
def expand_feature_flags(arch_variant, arch_variant_to_features = {}, flag_map = {}):
    flags = []
    features = _enabled_features(arch_variant, arch_variant_to_features)
    for feature in features:
        flags.extend(flag_map.get(feature, []))
    return flags

# Macro to set up both the toolchain and the config.
def android_cc_toolchain(
        name,
        target_os = None,
        target_arch = None,
        clang_version = None,
        # This should come from the clang_version provider.
        # Instead, it's hard-coded because this is a macro, not a rule.
        clang_version_directory = None,
        gcc_toolchain = None,
        # If false, the crt version and "normal" version of this toolchain are identical.
        crt = True,
        libclang_rt_builtin = None,
        target_flags = [],
        compiler_flags = [],
        linker_flags = [],
        toolchain_identifier = None,
        rtti_toggle = True):
    extra_linker_paths = []
    libclang_rt_path = None
    if libclang_rt_builtin:
        libclang_rt_path = libclang_rt_builtin
        extra_linker_paths.append(":" + libclang_rt_path)
    if gcc_toolchain:
        gcc_toolchain_path = "//%s:tools" % gcc_toolchain
        extra_linker_paths.append(gcc_toolchain_path)

    common_toolchain_config = dict(
        [
            ("target_os", target_os),
            ("target_arch", target_arch),
            ("clang_version", clang_version),
            ("libclang_rt_builtin", libclang_rt_path),
            ("target_flags", target_flags),
            ("compiler_flags", compiler_flags),
            ("linker_flags", linker_flags),
            ("rtti_toggle", rtti_toggle),
        ],
    )

    _cc_toolchain_config(
        name = "%s_nocrt_config" % name,
        toolchain_identifier = toolchain_identifier + "_nocrt",
        **common_toolchain_config
    )

    # Create the filegroups needed for sandboxing toolchain inputs to C++ actions.
    native.filegroup(
        name = "%s_compiler_clang_includes" % name,
        srcs = native.glob([clang_version_directory + "/lib64/clang/*/include/**"]),
    )

    native.filegroup(
        name = "%s_compiler_binaries" % name,
        srcs = native.glob([clang_version_directory + "/bin/clang*"]),
    )

    native.filegroup(
        name = "%s_linker_binaries" % name,
        srcs = native.glob([
            clang_version_directory + "/bin/*",
        ]),
    )

    native.filegroup(
        name = "%s_ar_files" % name,
        srcs = [clang_version_directory + "/bin/llvm-ar"],
    )

    native.filegroup(
        name = "%s_compiler_files" % name,
        srcs = [
            "%s_compiler_binaries" % name,
            "%s_compiler_clang_includes" % name,
        ],
    )

    native.filegroup(
        name = "%s_linker_files" % name,
        srcs = ["%s_linker_binaries" % name] + extra_linker_paths,
    )

    native.filegroup(
        name = "%s_all_files" % name,
        srcs = [
            "%s_compiler_files" % name,
            "%s_linker_files" % name,
            "%s_ar_files" % name,
        ],
    )

    native.cc_toolchain(
        name = name + "_nocrt",
        all_files = "%s_all_files" % name,
        as_files = "//:empty",  # Note the "//" prefix, see comment above
        ar_files = "%s_ar_files" % name,
        compiler_files = "%s_compiler_files" % name,
        dwp_files = ":empty",
        linker_files = "%s_linker_files" % name,
        objcopy_files = ":empty",
        strip_files = ":empty",
        supports_param_files = 0,
        toolchain_config = ":%s_nocrt_config" % name,
        toolchain_identifier = toolchain_identifier + "_nocrt",
    )

    if crt:
        # Write the toolchain config.
        _cc_toolchain_config(
            name = "%s_config" % name,
            toolchain_identifier = toolchain_identifier,
            shared_library_crtbegin = _bionic_crt.shared_library_crtbegin,
            shared_library_crtend = _bionic_crt.shared_library_crtend,
            shared_binary_crtbegin = _bionic_crt.shared_binary_crtbegin,
            static_binary_crtbegin = _bionic_crt.static_binary_crtbegin,
            binary_crtend = _bionic_crt.binary_crtend,
            **common_toolchain_config
        )

        native.filegroup(
            name = "%s_crt_libs" % name,
            srcs = [
                _bionic_crt.shared_library_crtbegin,
                _bionic_crt.shared_library_crtend,
                _bionic_crt.shared_binary_crtbegin,
                _bionic_crt.static_binary_crtbegin,
                _bionic_crt.binary_crtend,
            ],
        )

        native.filegroup(
            name = "%s_linker_files_with_crt" % name,
            srcs = [
                "%s_linker_files" % name,
                "%s_crt_libs" % name,
            ],
        )

        # Create the actual cc_toolchain.
        # The dependency on //:empty is intentional; it's necessary so that Bazel
        # can parse .d files correctly (see the comment in $TOP/BUILD)
        native.cc_toolchain(
            name = name,
            all_files = "%s_all_files" % name,
            as_files = "//:empty",  # Note the "//" prefix, see comment above
            ar_files = "%s_ar_files" % name,
            compiler_files = "%s_compiler_files" % name,
            dwp_files = ":empty",
            linker_files = "%s_linker_files_with_crt" % name,
            objcopy_files = ":empty",
            strip_files = ":empty",
            supports_param_files = 0,
            toolchain_config = ":%s_config" % name,
            toolchain_identifier = toolchain_identifier,
            exec_transition_for_inputs = False,
        )
    else:
        _cc_toolchain_config(
            name = "%s_config" % name,
            toolchain_identifier = toolchain_identifier,
            **common_toolchain_config
        )

        native.alias(
            name = name,
            actual = name + "_nocrt",
        )
