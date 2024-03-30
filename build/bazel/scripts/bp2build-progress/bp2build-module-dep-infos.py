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
"""A script to produce a csv report of all modules of a given type.

There is one output row per module of the input type, each column corresponds
to one of the fields of the _ModuleTypeInfo named tuple described below.
The script allows to ignore certain dependency edges based on the target module
name, or the dependency tag name.

Usage:
  ./bp2build-module-dep-infos.py -m <module type>
                                 --ignore_by_name <modules to ignore>
                                 --ignore_by_tag <dependency tags to ignore>

"""

import argparse
import collections
import csv
import dependency_analysis
import sys

_ModuleTypeInfo = collections.namedtuple(
    "_ModuleTypeInfo",
    [
        # map of module type to the set of properties used by modules
        # of the given type in the dependency tree.
        "type_to_properties",
        # [java modules only] list of source file extensions used by this module.
        "java_source_extensions",
    ])

_DependencyRelation = collections.namedtuple("_DependencyRelation", [
    "transitive_dependency",
    "top_level_module",
])


def _get_java_source_extensions(module):
  out = set()
  if "Module" not in module:
    return out
  if "Java" not in module["Module"]:
    return out
  if "SourceExtensions" not in module["Module"]["Java"]:
    return out
  if module["Module"]["Java"]["SourceExtensions"]:
    out.update(module["Module"]["Java"]["SourceExtensions"])
  return out


def _get_set_properties(module):
  set_properties = set()
  if "Module" not in module:
    return set_properties
  if "Android" not in module["Module"]:
    return set_properties
  if "SetProperties" not in module["Module"]["Android"]:
    return set_properties
  for prop in module["Module"]["Android"]["SetProperties"]:
    set_properties.add(prop["Name"])
  return set_properties


def _should_ignore(module, ignored_names):
  return (dependency_analysis.is_windows_variation(module) or
          module["Name"] in ignored_names or
          dependency_analysis.ignore_kind(module["Type"]))

def _update_infos(module_name, type_infos, module_graph_map, ignored_dep_names):
  module = module_graph_map[module_name]
  if _should_ignore(module, ignored_dep_names) or module_name in type_infos:
    return
  for dep in module["Deps"]:
    dep_name = dep["Name"]
    if dep_name == module_name:
      continue
    _update_infos(dep_name, type_infos, module_graph_map, ignored_dep_names)

  java_source_extensions = _get_java_source_extensions(module)
  type_to_properties = collections.defaultdict(set)
  if module["Type"]:
    type_to_properties[module["Type"]].update(_get_set_properties(module))
  for dep in module["Deps"]:
    dep_name = dep["Name"]
    if _should_ignore(module_graph_map[dep_name], ignored_dep_names):
      continue
    if dep_name == module_name:
      continue
    for dep_type, dep_type_properties in type_infos[dep_name].type_to_properties.items():
      type_to_properties[dep_type].update(dep_type_properties)
      java_source_extensions.update(type_infos[dep_name].java_source_extensions)
  type_infos[module_name] = _ModuleTypeInfo(
      type_to_properties=type_to_properties,
      java_source_extensions=java_source_extensions)


def module_type_info_from_json(module_graph, module_type, ignored_dep_names):
  """Builds a map of module name to _ModuleTypeInfo for each module of module_type.

     Dependency edges pointing to modules in ignored_dep_names are not followed.
  """
  module_graph_map = dict()
  module_stack = []
  for module in module_graph:
    # Windows variants have incomplete dependency information in the json module graph.
    if dependency_analysis.is_windows_variation(module):
      continue
    module_graph_map[module["Name"]] = module
    if module["Type"] == module_type:
      module_stack.append(module["Name"])
  # dictionary of module name to _ModuleTypeInfo.
  type_infos = {}
  for module_name in module_stack:
    # post-order traversal of the dependency graph builds the type_infos
    # dictionary from the leaves so that common dependencies are visited
    # only once.
    _update_infos(module_name, type_infos, module_graph_map, ignored_dep_names)

  return {
      name: info
      for name, info in type_infos.items()
      if module_graph_map[name]["Type"] == module_type
  }


def main():
  parser = argparse.ArgumentParser(description="")
  parser.add_argument("--module_type", "-m", help="name of Soong module type.")
  parser.add_argument(
      "--ignore_by_name",
      type=str,
      default="",
      required=False,
      help="Comma-separated list. When building the tree of transitive dependencies, will not follow dependency edges pointing to module names listed by this flag."
  )
  args = parser.parse_args()

  module_type = args.module_type
  ignore_by_name = args.ignore_by_name

  module_graph = dependency_analysis.get_json_module_type_info(module_type)
  type_infos = module_type_info_from_json(module_graph, module_type,
                                          ignore_by_name.split(","))
  writer = csv.writer(sys.stdout)
  writer.writerow([
      "module name",
      "properties",
      "java source extensions",
  ])
  for module, module_type_info in type_infos.items():
    writer.writerow([
        module,
        ("[\"%s\"]" % '"\n"'.join([
            "%s: %s" % (mtype, ",".join(properties)) for mtype, properties in
            module_type_info.type_to_properties.items()
        ]) if len(module_type_info.type_to_properties) else "[]"),
        ("[\"%s\"]" % '", "'.join(module_type_info.java_source_extensions)
         if len(module_type_info.java_source_extensions) else "[]"),
    ])


if __name__ == "__main__":
  main()
