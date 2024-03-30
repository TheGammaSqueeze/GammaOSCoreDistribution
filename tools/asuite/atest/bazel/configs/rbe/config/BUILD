package(default_visibility = ["//visibility:public"])

platform(
    name = "platform",
    constraint_values = [
        "@bazel_tools//platforms:linux",
        "@bazel_tools//platforms:x86_64",
    ],
    exec_properties = {
        "container-image": "docker://l.gcr.io/google/rbe-ubuntu16-04@sha256:f6568d8168b14aafd1b707019927a63c2d37113a03bcee188218f99bd0327ea1",
        "OSFamily": "Linux",
    },
    parents = ["@local_config_platform//:host"],
)
