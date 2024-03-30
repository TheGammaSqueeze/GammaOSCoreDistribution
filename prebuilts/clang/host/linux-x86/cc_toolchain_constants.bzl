load("@bazel_tools//tools/build_defs/cc:action_names.bzl", "ACTION_NAMES")
load("@soong_injection//cc_toolchain:constants.bzl", _generated_constants = "constants")

# This file uses structs to organize and control the visibility of symbols.

# Handcrafted default flags.
flags = struct(
    # =============
    # Compiler flags
    # =============
    compiler_flags = [
        "-fPIC",
    ],
    asm_compiler_flags = [
        "-D__ASSEMBLY__",
    ],
    # ============
    # Linker flags
    # ============
    bionic_linker_flags = [
        # These are the linker flags for OSes that use Bionic: LinuxBionic, Android
        "-nostdlib",
        "-Wl,--gc-sections",
    ],
    bionic_static_executable_linker_flags = [
        "-Bstatic",
    ],
    bionic_dynamic_executable_linker_flags = [
        "-pie",
        "-Bdynamic",
        "-Wl,-z,nocopyreloc",
    ],
    # ===========
    # Other flags
    # ===========
    non_external_defines = [
        # These defines should only apply to targets which are not under
        # @external/. This can be controlled by adding "-non_external_compiler_flags"
        # to the features list for external/ packages.
        # This corresponds to special-casing in Soong (see "external/" in build/soong/cc/compiler.go).
        "-DANDROID_STRICT",
    ],
)

# Generated flags dumped from Soong's cc toolchain code.
generated_constants = _generated_constants

# The set of C and C++ actions used in the Android build. There are other types
# of actions available in ACTION_NAMES, but those are not used in
# Android yet.
actions = struct(
    compile = [
        ACTION_NAMES.c_compile,
        ACTION_NAMES.cpp_compile,
        ACTION_NAMES.assemble,
        ACTION_NAMES.preprocess_assemble,
    ],
    c_compile = ACTION_NAMES.c_compile,
    cpp_compile = ACTION_NAMES.cpp_compile,
    # Assembler actions for .s and .S files.
    assemble = [
        ACTION_NAMES.assemble,
        ACTION_NAMES.preprocess_assemble,
    ],
    # Link actions
    link = [
        ACTION_NAMES.cpp_link_executable,
        ACTION_NAMES.cpp_link_dynamic_library,
        ACTION_NAMES.cpp_link_nodeps_dynamic_library,
    ],
    # Differentiate archive actions from link actions
    archive = [
        ACTION_NAMES.cpp_link_static_library,
    ],
    cpp_link_dynamic_library = ACTION_NAMES.cpp_link_dynamic_library,
    cpp_link_nodeps_dynamic_library = ACTION_NAMES.cpp_link_nodeps_dynamic_library,
    cpp_link_static_library = ACTION_NAMES.cpp_link_static_library,
    cpp_link_executable = ACTION_NAMES.cpp_link_executable,
    strip = ACTION_NAMES.strip,
)

bionic_crt = struct(
    # crtbegin and crtend libraries for compiling cc_library_shared and
    # cc_binary against the Bionic runtime
    shared_library_crtbegin = "//bionic/libc:crtbegin_so",
    shared_library_crtend = "//bionic/libc:crtend_so",
    shared_binary_crtbegin = "//bionic/libc:crtbegin_dynamic",
    static_binary_crtbegin = "//bionic/libc:crtbegin_static",
    binary_crtend = "//bionic/libc:crtend_android",
)

default_cpp_std_version = "gnu++17"
cpp_std_versions = [
    "gnu++98",
    "gnu++11",
    "gnu++17",
    "gnu++2a",
    "c++98",
    "c++11",
    "c++17",
    "c++2a",
]

default_c_std_version = "gnu99"
c_std_versions = [
    "gnu11",
    "gnu99",
    "c11",
    "c99",
]

# Added by linker.go for non-bionic, non-musl, non-windows toolchains.
# Should be added to host builds to match the default behavior of device builds.
device_compatibility_flags_non_windows = [
    "-ldl",
    "-lpthread",
    "-lm",
]

# Added by linker.go for non-bionic, non-musl, non-darwin toolchains.
# Should be added to host builds to match the default behavior of device builds.
device_compatibility_flags_non_darwin = ["-lrt"]

arches = struct(
    Arm = "arm",
    Arm64 = "arm64",
    X86 = "x86",
    X86_64 = "x86_64",
)

def _variant_combinations(arch_variants = {}, cpu_variants = {}):
    combinations = []
    for arch in arch_variants:
        if "" not in cpu_variants:
            combinations.append(struct(arch_variant = arch, cpu_variant = ""))
        for cpu in cpu_variants:
            combinations.append(struct(arch_variant = arch, cpu_variant = cpu))
    return combinations

arch_to_variants = {
    arches.Arm: _variant_combinations(arch_variants = _generated_constants.ArmArchVariantCflags, cpu_variants = _generated_constants.ArmCpuVariantCflags),
    arches.Arm64: _variant_combinations(arch_variants = _generated_constants.Arm64ArchVariantCflags, cpu_variants = _generated_constants.Arm64CpuVariantCflags),
    arches.X86: _variant_combinations(arch_variants = _generated_constants.X86ArchVariantCflags),
    arches.X86_64: _variant_combinations(arch_variants = _generated_constants.X86_64ArchVariantCflags),
}

def arm_extra_ldflags(variant):
    if variant.arch_variant == "armv7-a-neon":
        if variant.cpu_variant in ("cortex-a8", ""):
            return _generated_constants.ArmFixCortexA8LdFlags
        else:
            return _generated_constants.ArmNoFixCortexA8LdFlags
    elif variant.arch_variant == "armv7-a":
        return _generated_constants.ArmFixCortexA8LdFlags
    return []

# enabled_features returns a list of enabled features for the given arch variant, defaults to empty list
def enabled_features(arch_variant, arch_variant_to_features = {}):
    if arch_variant == None:
        arch_variant = ""
    return arch_variant_to_features.get(arch_variant, [])

# variant_name creates a name based on a variant struct with arch_variant and cpu_variant
def variant_name(variant):
    ret = ""
    if variant.arch_variant:
        ret += "_" + variant.arch_variant
    if variant.cpu_variant:
        ret += "_" + variant.cpu_variant
    return ret

# variant_constraints gets constraints based on variant struct and arch_variant_features
def variant_constraints(variant, arch_variant_features = {}):
    ret = []
    if variant.arch_variant:
        ret.append("//build/bazel/platforms/arch/variants:" + variant.arch_variant)
    if variant.cpu_variant:
        ret.append("//build/bazel/platforms/arch/variants:" + variant.cpu_variant)
    features = enabled_features(variant.arch_variant, arch_variant_features)
    for feature in features:
        ret.append("//build/bazel/platforms/arch/variants:" + feature)
    return ret
