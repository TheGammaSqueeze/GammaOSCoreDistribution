load("@rules_android//rules:rules.bzl", "android_binary")
load("//build/make/tools:event_log_tags.bzl", "event_log_tags")

event_log_tags(
    name = "genlogtags",
    srcs = glob(["src/**/*.logtags"]),
)

android_binary(
    name = "QuickSearchBox",
    srcs = glob(["src/**/*.java"]) + [
        ":genlogtags",
    ],
    custom_package = "com.android.quicksearchbox",
    javacopts = ["-Xep:ArrayToString:OFF"],
    manifest = "AndroidManifest.xml",
    # TODO(182591919): uncomment the below once android rules are integrated with r8.
    # proguard_specs = ["proguard.flags"],
    resource_files = glob(["res/**"]),
    deps = [
        "//external/guava",
        "//frameworks/ex/common:android-common",
    ],
)
