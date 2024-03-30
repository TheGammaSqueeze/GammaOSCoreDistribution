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

PrebuiltFileInfo = provider(
    "Info needed for prebuilt_file modules",
    fields = {
        "src": "Source file of this prebuilt",
        "dir": "Directory into which to install",
        "filename": "Optional name for the installed file",
        "installable": "Whether this is directly installable into one of the partitions",
    },
)
_handled_dirs = ["etc", "usr/share"]

def _prebuilt_file_rule_impl(ctx):
    srcs = ctx.files.src
    if len(srcs) != 1:
        fail("src for", ctx.label.name, "is expected to be singular, but is of len", len(srcs), ":\n", srcs)

    # Is this an acceptable directory, or a subdir under one?
    dir = ctx.attr.dir
    acceptable = False
    for d in _handled_dirs:
        if dir == d or dir.startswith(d + "/"):
            acceptable = True
            break
    if not acceptable:
        fail("dir for", ctx.label.name, "is `", dir, "`, but we only handle these:\n", _handled_dirs)

    return [
        PrebuiltFileInfo(
            src = srcs[0],
            dir = dir,
            filename = ctx.attr.filename,
            installable = ctx.attr.installable,
        ),
        DefaultInfo(
            files = depset(srcs),
        ),
    ]

_prebuilt_file = rule(
    implementation = _prebuilt_file_rule_impl,
    attrs = {
        "src": attr.label(
            mandatory = True,
            allow_files = True,
            # TODO(b/217908237): reenable allow_single_file
            # allow_single_file = True,
        ),
        "dir": attr.string(mandatory = True),
        "filename": attr.string(),
        "installable": attr.bool(default = True),
    },
)

def prebuilt_file(
        name,
        src,
        dir,
        filename = None,
        installable = True,
        # TODO(b/207489266): Fully support;
        # data is currently dropped to prevent breakages from e.g. prebuilt_etc
        data = [],
        **kwargs):
    "Bazel macro to correspond with the e.g. prebuilt_etc and prebuilt_usr_share Soong modules."

    _prebuilt_file(
        name = name,
        src = src,
        dir = dir,
        filename = filename,
        installable = installable,
        **kwargs
    )
