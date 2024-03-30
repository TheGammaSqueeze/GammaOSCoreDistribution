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

"""Rules used to run tests using Tradefed."""

load("//bazel/rules:platform_transitions.bzl", "host_transition", "device_transition")
load("//bazel/rules:tradefed_test_aspects.bzl", "soong_prebuilt_tradefed_test_aspect")
load("//bazel/rules:tradefed_test_info.bzl", "TradefedTestInfo")
load("//bazel/rules:common_settings.bzl", "BuildSettingInfo")
load("//:constants.bzl",
    "aapt_label",
    "adb_label",
    "atest_script_help_sh_label",
    "atest_tradefed_label",
    "atest_tradefed_sh_label",
    "bazel_result_reporter_label",
    "tradefed_label",
    "tradefed_test_framework_label"
)

_BAZEL_WORK_DIR = "${TEST_SRCDIR}/${TEST_WORKSPACE}/"
_PY_TOOLCHAIN = "@bazel_tools//tools/python:toolchain_type"
_TOOLCHAINS = [_PY_TOOLCHAIN]

_TRADEFED_TEST_ATTRIBUTES = {
    "_tradefed_test_template": attr.label(
        default = "//bazel/rules:tradefed_test.sh.template",
        allow_single_file = True,
    ),
    "_tradefed_classpath_jars": attr.label_list(
        default = [
            atest_tradefed_label,
            tradefed_label,
            tradefed_test_framework_label,
            bazel_result_reporter_label,
        ],
        cfg = host_transition
    ),
    "_atest_tradefed_launcher": attr.label(
        default = atest_tradefed_sh_label,
        allow_single_file = True,
        cfg = host_transition,
    ),
    "_atest_helper": attr.label(
        default = atest_script_help_sh_label,
        allow_single_file = True,
        cfg = host_transition,
    ),
    "_adb": attr.label(
        default = adb_label,
        allow_single_file = True,
        cfg = host_transition,
    ),
    "_extra_tradefed_result_reporters": attr.label(
        default = "//bazel/rules:extra_tradefed_result_reporters",
    ),
    # This attribute is required to use Starlark transitions. It allows
    # allowlisting usage of this rule. For more information, see
    # https://docs.bazel.build/versions/master/skylark/config.html#user-defined-transitions
    "_allowlist_function_transition": attr.label(
        default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
    ),
}

def _add_dicts(*dictionaries):
    """Creates a new `dict` that has all the entries of the given dictionaries.

    This function serves as a replacement for the `+` operator which does not
    work with dictionaries. The implementation is inspired by Skylib's
    `dict.add` and duplicated to avoid the dependency. See
    https://github.com/bazelbuild/bazel/issues/6461 for more details.

    Note, if the same key is present in more than one of the input dictionaries,
    the last of them in the argument list overrides any earlier ones.

    Args:
        *dictionaries: Dictionaries to be added.

    Returns:
        A new `dict` that has all the entries of the given dictionaries.
    """
    result = {}
    for d in dictionaries:
        result.update(d)
    return result

def _tradefed_deviceless_test_impl(ctx):
    return _tradefed_test_impl(
        ctx,
        tradefed_options = [
            "-n",
            "--prioritize-host-config",
            "--skip-host-arch-check",
        ],
        host_deps = ctx.attr.test
    )

tradefed_deviceless_test = rule(
    attrs = _add_dicts(
        _TRADEFED_TEST_ATTRIBUTES,
        {
            "test": attr.label(
                mandatory = True,
                cfg = host_transition,
                aspects = [soong_prebuilt_tradefed_test_aspect],
            ),
        },
    ),
    test = True,
    implementation = _tradefed_deviceless_test_impl,
    toolchains = _TOOLCHAINS,
    doc = "A rule used to run host-side deviceless tests using Tradefed",
)

def _tradefed_device_test_impl(ctx):
    return _tradefed_test_impl(
        ctx,
        host_deps = ctx.attr._aapt,
        device_deps = ctx.attr.test,
        path_additions = [
            _BAZEL_WORK_DIR + ctx.file._aapt.dirname,
        ]
    )

tradefed_device_test = rule(
    attrs = _add_dicts(
        _TRADEFED_TEST_ATTRIBUTES,
        {
            "test": attr.label(
                mandatory = True,
                cfg = device_transition,
                aspects = [soong_prebuilt_tradefed_test_aspect],
            ),
            "_aapt": attr.label(
                default = aapt_label,
                allow_single_file = True,
                cfg = host_transition,
            ),
        },
    ),
    test = True,
    implementation = _tradefed_device_test_impl,
    toolchains = _TOOLCHAINS,
    doc = "A rule used to run device tests using Tradefed",
)

def _tradefed_test_impl(
        ctx,
        tradefed_options=[],
        host_deps=[],
        device_deps=[],
        path_additions=[],
    ):

    path_additions = path_additions + [_BAZEL_WORK_DIR + ctx.file._adb.dirname]

    tradefed_classpath = []
    for tradefed_classpath_jar in ctx.attr._tradefed_classpath_jars:
        for f in tradefed_classpath_jar.files.to_list():
            tradefed_classpath.append(_BAZEL_WORK_DIR + f.short_path)
    tradefed_classpath = ":".join(tradefed_classpath)

    tradefed_host_deps = []
    tradefed_host_deps.extend(ctx.attr._tradefed_classpath_jars)
    tradefed_host_deps.extend(ctx.attr._atest_tradefed_launcher)
    tradefed_host_deps.extend(ctx.attr._atest_helper)
    tradefed_host_deps.extend(ctx.attr._adb)
    host_runfiles = _get_runfiles_from_targets(
        ctx,
        tradefed_host_deps + host_deps,
    )

    shared_lib_dirs = []
    for f in host_runfiles.files.to_list():
        if f.extension == "so":
            shared_lib_dirs.append(_BAZEL_WORK_DIR + f.dirname)
    shared_lib_dirs = ":".join(shared_lib_dirs)

    # Configure the Python toolchain.
    py_toolchain_info = ctx.toolchains[_PY_TOOLCHAIN]
    py2_interpreter = py_toolchain_info.py2_runtime.interpreter
    py3_interpreter = py_toolchain_info.py3_runtime.interpreter

    # Create `python` and `python3` symlinks in the runfiles tree and add them to the executable
    # path. This is required because scripts reference these commands in their shebang line.
    host_runfiles = host_runfiles.merge(ctx.runfiles(symlinks = {
        "/".join([py2_interpreter.dirname, "python"]): py2_interpreter,
        "/".join([py3_interpreter.dirname, "python3"]): py3_interpreter,
    }))
    path_additions = path_additions + [
        _BAZEL_WORK_DIR + py2_interpreter.dirname,
        _BAZEL_WORK_DIR + py3_interpreter.dirname,
    ]

    result_reporters = [
        "com.android.tradefed.result.BazelExitCodeResultReporter",
        "com.android.tradefed.result.BazelXmlResultReporter",
    ]

    result_reporters.extend(ctx.attr._extra_tradefed_result_reporters[BuildSettingInfo].value)

    result_reporters_config_file = ctx.actions.declare_file("result-reporters-%s.xml" % ctx.label.name)
    _write_reporters_config_file(
        ctx, result_reporters_config_file, result_reporters)
    reporter_runfiles = ctx.runfiles(files = [result_reporters_config_file])

    script = ctx.actions.declare_file("tradefed_test_%s.sh" % ctx.label.name)
    ctx.actions.expand_template(
        template = ctx.file._tradefed_test_template,
        output = script,
        is_executable = True,
        substitutions = {
            "{module_name}": ctx.attr.test[0][TradefedTestInfo].module_name,
            "{atest_tradefed_launcher}": _BAZEL_WORK_DIR + ctx.file._atest_tradefed_launcher.short_path,
            "{atest_helper}": _BAZEL_WORK_DIR + ctx.file._atest_helper.short_path,
            "{tradefed_tests_dir}": _BAZEL_WORK_DIR + ctx.attr.test[0].label.package,
            "{tradefed_classpath}": tradefed_classpath,
            "{shared_lib_dirs}": shared_lib_dirs,
            "{path_additions}": ":".join(path_additions),
            "{additional_tradefed_options}": " ".join(tradefed_options),
            "{result_reporters_config_file}": _BAZEL_WORK_DIR + result_reporters_config_file.short_path,
        },
    )

    device_runfiles = _get_runfiles_from_targets(ctx, device_deps)
    return [DefaultInfo(executable = script,
                        runfiles = host_runfiles.merge_all([device_runfiles, reporter_runfiles]))]

def _write_reporters_config_file(ctx, config_file, result_reporters):
    config_lines = [
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
        "<configuration>"
    ]

    for result_reporter in result_reporters:
        config_lines.append("    <result_reporter class=\"%s\" />" % result_reporter)

    config_lines.append("</configuration>")

    ctx.actions.write(config_file, "\n".join(config_lines))

def _get_runfiles_from_targets(ctx, targets):
    return ctx.runfiles().merge_all([
        target[DefaultInfo].default_runfiles for target in targets
    ])
