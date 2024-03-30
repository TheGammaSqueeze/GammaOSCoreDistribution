#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Utility functions to produce module or module type dependency graphs using json-module-graph or queryview."""

import json
import os
import os.path
import subprocess
import xml.etree.ElementTree

# This list of module types are omitted from the report and graph
# for brevity and simplicity. Presence in this list doesn't mean
# that they shouldn't be converted, but that they are not that useful
# to be recorded in the graph or report currently.
IGNORED_KINDS = set([
    "license_kind",
    "license",
    "cc_defaults",
    "cc_prebuilt_object",
    "cc_prebuilt_library_headers",
    "cc_prebuilt_library_shared",
    "cc_prebuilt_library_static",
    "cc_prebuilt_library_static",
    "cc_prebuilt_library",
    "java_defaults",
    "ndk_prebuilt_static_stl",
    "ndk_library",
])

SRC_ROOT_DIR = os.path.abspath(__file__ + "/../../../../..")


def _build_with_soong(target):
  subprocess.check_output(
      [
          "build/soong/soong_ui.bash",
          "--make-mode",
          "--skip-soong-tests",
          target,
      ],
      cwd=SRC_ROOT_DIR,
      env={
          # Use aosp_arm as the canonical target product.
          "TARGET_PRODUCT": "aosp_arm",
          "TARGET_BUILD_VARIANT": "userdebug",
      },
  )


def get_queryview_module_info(module):
  """Returns the list of transitive dependencies of input module as built by queryview."""
  _build_with_soong("queryview")

  result = subprocess.check_output(
      [
          "tools/bazel", "query", "--config=ci", "--config=queryview",
          "--output=xml",
          'deps(attr("soong_module_name", "^{}$", //...))'.format(module)
      ],
      cwd=SRC_ROOT_DIR,
  )
  return xml.etree.ElementTree.fromstring(result)


def get_json_module_info(module):
  """Returns the list of transitive dependencies of input module as provided by Soong's json module graph."""
  _build_with_soong("json-module-graph")
  # Run query.sh on the module graph for the top level module
  result = subprocess.check_output(
      [
          "build/bazel/json_module_graph/query.sh", "fullTransitiveDeps",
          "out/soong/module-graph.json", module
      ],
      cwd=SRC_ROOT_DIR,
  )
  return json.loads(result)


def get_bp2build_converted_modules():
  """ Returns the list of modules that bp2build can currently convert. """
  _build_with_soong("bp2build")
  # Parse the list of converted module names from bp2build
  with open(
      os.path.join(
          SRC_ROOT_DIR,
          "out/soong/soong_injection/metrics/converted_modules.txt")) as f:
    # Read line by line, excluding comments.
    # Each line is a module name.
    ret = [line.strip() for line in f.readlines() if not line.startswith("#")]
  return set(ret)


def get_json_module_type_info(module_type):
  """Returns the combined transitive dependency closures of all modules of module_type."""
  _build_with_soong("json-module-graph")
  # Run query.sh on the module graph for the top level module type
  result = subprocess.check_output(
      [
          "build/bazel/json_module_graph/query.sh",
          "fullTransitiveModuleTypeDeps", "out/soong/module-graph.json",
          module_type
      ],
      cwd=SRC_ROOT_DIR,
  )
  return json.loads(result)


def is_windows_variation(module):
  """Returns True if input module's variant is Windows.

  Args:
    module: an entry parsed from Soong's json-module-graph
  """
  dep_variations = module.get("Variations")
  dep_variation_os = ""
  if dep_variations != None:
    dep_variation_os = dep_variations.get("os")
  return dep_variation_os == "windows"


def ignore_kind(kind):
  return kind in IGNORED_KINDS or "defaults" in kind
