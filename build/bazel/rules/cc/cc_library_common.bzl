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

load("//build/bazel/product_variables:constants.bzl", "constants")
load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cpp_toolchain")
load("@soong_injection//api_levels:api_levels.bzl", "api_levels")

_bionic_targets = ["//bionic/libc", "//bionic/libdl", "//bionic/libm"]
_static_bionic_targets = ["//bionic/libc:libc_bp2build_cc_library_static", "//bionic/libdl:libdl_bp2build_cc_library_static", "//bionic/libm:libm_bp2build_cc_library_static"]

# The default system_dynamic_deps value for cc libraries. This value should be
# used if no value for system_dynamic_deps is specified.
system_dynamic_deps_defaults = select({
    constants.ArchVariantToConstraints["linux_bionic"]: _bionic_targets,
    constants.ArchVariantToConstraints["android"]: _bionic_targets,
    "//conditions:default": [],
})

system_static_deps_defaults = select({
    constants.ArchVariantToConstraints["linux_bionic"]: _static_bionic_targets,
    constants.ArchVariantToConstraints["android"]: _static_bionic_targets,
    "//conditions:default": [],
})

def add_lists_defaulting_to_none(*args):
    """Adds multiple lists, but is well behaved with a `None` default."""
    combined = None
    for arg in args:
        if arg != None:
            if combined == None:
                combined = []
            combined += arg

    return combined

# By default, crtbegin/crtend linking is enabled for shared libraries and cc_binary.
def disable_crt_link(features):
    return features + ["-link_crt"]

# get_includes_paths expects a rule context, a list of directories, and
# whether the directories are package-relative and returns a list of exec
# root-relative paths. This handles the need to search for files both in the
# source tree and generated files.
def get_includes_paths(ctx, dirs, package_relative = True):
    execution_relative_dirs = []
    for rel_dir in dirs:
        if rel_dir == ".":
            rel_dir = ""
        execution_rel_dir = rel_dir
        if package_relative:
            execution_rel_dir = ctx.label.package
            if len(rel_dir) > 0:
                execution_rel_dir = execution_rel_dir + "/" + rel_dir
        execution_relative_dirs.append(execution_rel_dir)

        # to support generated files, we also need to export includes relatives to the bin directory
        if not execution_rel_dir.startswith("/"):
            execution_relative_dirs.append(ctx.bin_dir.path + "/" + execution_rel_dir)
    return execution_relative_dirs

def create_ccinfo_for_includes(
        ctx,
        includes = [],
        absolute_includes = [],
        system_includes = [],
        deps = []):
    cc_toolchain = find_cpp_toolchain(ctx)

    # Create a compilation context using the string includes of this target.
    compilation_context = cc_common.create_compilation_context(
        includes = depset(
            get_includes_paths(ctx, includes) +
            get_includes_paths(ctx, absolute_includes, package_relative = False),
        ),
        system_includes = depset(get_includes_paths(ctx, system_includes)),
    )

    # Combine this target's compilation context with those of the deps; use only
    # the compilation context of the combined CcInfo.
    cc_infos = [dep[CcInfo] for dep in deps]
    cc_infos += [CcInfo(compilation_context = compilation_context)]
    combined_info = cc_common.merge_cc_infos(cc_infos = cc_infos)

    return CcInfo(compilation_context = combined_info.compilation_context)


def is_external_directory(package_name):
  if package_name.startswith('external'):
    return True
  if package_name.startswith('hardware'):
    paths = package_name.split("/")
    if len(paths) < 2:
      return True
    secondary_path = paths[1]
    if secondary_path in ["google", "interfaces", "ril"]:
      return True
    return secondary_path.startswith("libhardware")
  if package_name.startswith("vendor"):
    paths = package_name.split("/")
    if len(paths) < 2:
      return True
    secondary_path = paths[1]
    return secondary_path.contains("google")
  return False

def parse_sdk_version(version):
    future_version = "10000"

    if version == "" or version == "current":
        return future_version
    elif version.isdigit() and int(version) in api_levels.values():
        return version
    elif version in api_levels.keys():
        return str(api_levels[version])
    # We need to handle this case properly later
    elif version == "apex_inherit":
        return future_version
    else:
        fail("Unknown sdk version: %s" % (version))
