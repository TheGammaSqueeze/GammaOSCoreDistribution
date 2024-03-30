# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Rule used to import artifacts prebuilt by Soong into the Bazel workspace.

The rule returns a DefaultInfo provider with all artifacts and runtime dependencies,
and a SoongPrebuiltInfo provider with the original Soong module name and artifacts.
"""

SoongPrebuiltInfo = provider(
    doc = "Info about a prebuilt Soong build module",
    fields = {
        "files": "Files imported from Soong outputs",
        "module_name": "Name of the original Soong build module",
    },
)

def _soong_prebuilt_impl(ctx):

    files = ctx.files.files

    # Ensure that soong_prebuilt targets always have at least one file to avoid
    # evaluation errors when running Bazel cquery on a clean tree to find
    # dependencies.
    #
    # This happens because soong_prebuilt dependency target globs don't match
    # any files when the workspace symlinks are broken and point to build
    # artifacts that still don't exist. This in turn causes errors in rules
    # that reference these targets via attributes with allow_single_file=True
    # and which expect a file to be present.
    #
    # Note that the below action is never really executed during cquery
    # evaluation but fails when run as part of a test execution to signal that
    # prebuilts were not correctly imported.
    if not files:
        placeholder_file = ctx.actions.declare_file(ctx.label.name + ".missing")

        progress_message = (
            "Attempting to import missing artifacts for Soong module '%s'; " +
            "please make sure that the module is built with Soong before " +
            "running Bazel"
        ) % ctx.attr.module_name

        # Note that we don't write the file for the action to always be
        # executed and display the warning message.
        ctx.actions.run_shell(
            outputs=[placeholder_file],
            command="/bin/false",
            progress_message=progress_message
        )
        files = [placeholder_file]

    deps = []
    deps.extend(ctx.attr.runtime_deps)
    deps.extend(ctx.attr.data)
    runfiles = ctx.runfiles(files = files).merge_all([
        dep[DefaultInfo].default_runfiles
        for dep in deps
    ])

    return [
        SoongPrebuiltInfo(
            files = depset(files),
            module_name = ctx.attr.module_name,
        ),
        DefaultInfo(
            files = depset(files),
            runfiles = runfiles,
        ),
    ]

soong_prebuilt = rule(
    attrs = {
        "module_name": attr.string(),
        # Artifacts prebuilt by Soong.
        "files": attr.label_list(allow_files = True),
        # Targets that are needed by this target during runtime.
        "runtime_deps": attr.label_list(),
        "data": attr.label_list(),
    },
    implementation = _soong_prebuilt_impl,
    doc = "A rule that imports artifacts prebuilt by Soong into the Bazel workspace",
)

def _soong_uninstalled_prebuilt_impl(ctx):

    runfiles = ctx.runfiles().merge_all([
        dep[DefaultInfo].default_runfiles
        for dep in ctx.attr.runtime_deps
    ])

    return [
        SoongPrebuiltInfo(
            module_name = ctx.attr.module_name,
        ),
        DefaultInfo(
            runfiles = runfiles,
        ),
    ]

soong_uninstalled_prebuilt = rule(
    attrs = {
        "module_name": attr.string(),
        "runtime_deps": attr.label_list(),
    },
    implementation = _soong_uninstalled_prebuilt_impl,
    doc = "A rule for targets with no runtime outputs",
)
