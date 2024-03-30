load("//build/bazel/rules/android:android_binary.bzl", "android_binary")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("@rules_android//rules:rules.bzl", "android_library")

android_binary(
    name = "app",
    manifest = "AndroidManifest.xml",
    deps = [
        ":applib",
    ],
)

android_binary(
    name = "app-cert-string",
    certificate_name = "platform",
    manifest = "AndroidManifest.xml",
    deps = [
        ":applib",
    ],
)

android_binary(
    name = "app-cert-module",
    certificate = "//build/make/target/product/security:aosp-testkey",
    manifest = "AndroidManifest.xml",
    deps = [
        ":applib",
    ],
)

android_library(
    name = "applib",
    srcs = [
        "Jni.java",
        "MainActivity.java",
    ],
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        ":jni",
        ":lib",
    ],
)

android_library(
    name = "lib",
    srcs = ["Lib.java"],
)

cc_library_shared(
    name = "jni",
    srcs = ["jni.cc"],
    deps = [":jni_dep"],
)

cc_library_static(
    name = "jni_dep",
    srcs = ["jni_dep.cc"],
    hdrs = ["jni_dep.h"],
    deps = ["//libnativehelper:jni_headers"],
)
