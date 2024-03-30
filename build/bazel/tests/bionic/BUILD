load("@soong_injection//cc_toolchain:constants.bzl", "constants")

# This test requires bp2build to run and the generated BUILD files in the source tree.
sh_test(
    name = "verify_bionic_outputs",
    srcs = ["verify_bionic_outputs.sh"],
    data = [
        "//bionic/libc",
        "//bionic/libc:libc_bp2build_cc_library_static",
        "//bionic/libdl:libdl_android",
        "//bionic/libdl:libdl_android_bp2build_cc_library_static",
        "//bionic/linker:ld-android",
        "//bionic/linker:ld-android_bp2build_cc_library_static",
        "//prebuilts/clang/host/linux-x86:test_tools",
    ],
    env = {"CLANG_DEFAULT_VERSION": constants.CLANG_DEFAULT_VERSION},
    deps = ["@bazel_tools//tools/bash/runfiles"],
)

sh_test(
    name = "compare_libc_stripping",
    srcs = ["compare_libc_stripping.sh"],
    data = [
        "//bionic/libc",
        "//bionic/libc:libc_unstripped",
    ],
    env = {"CLANG_DEFAULT_VERSION": constants.CLANG_DEFAULT_VERSION},
    deps = ["@bazel_tools//tools/bash/runfiles"],
)
