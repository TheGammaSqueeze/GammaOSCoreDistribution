#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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
"""A json-module-graph postprocessing script to generate a bp2build progress tracker.

Usage:
  ./bp2build-progress.py [report|graph] -m <module name>

Example:

  To generate a report on the `adbd` module, run:
    ./bp2build-progress report -m adbd

  To generate a graph on the `adbd` module, run:
    ./bp2build-progress graph -m adbd > graph.in && dot -Tpng -o graph.png
    graph.in

"""

import argparse
import collections
import datetime
import dependency_analysis
import os.path
import queue
import subprocess
import sys

_ModuleInfo = collections.namedtuple("_ModuleInfo", [
    "name",
    "kind",
    "dirname",
])

_ReportData = collections.namedtuple("_ReportData", [
    "input_module",
    "all_unconverted_modules",
    "blocked_modules",
    "dirs_with_unconverted_modules",
    "kind_of_unconverted_modules",
    "converted",
])


def combine_report_data(data):
  ret = _ReportData(
      input_module=set(),
      all_unconverted_modules=collections.defaultdict(set),
      blocked_modules=collections.defaultdict(set),
      dirs_with_unconverted_modules=set(),
      kind_of_unconverted_modules=set(),
      converted=set(),
  )
  for item in data:
    ret.input_module.add(item.input_module)
    for key, value in item.all_unconverted_modules.items():
      ret.all_unconverted_modules[key].update(value)
    for key, value in item.blocked_modules.items():
      ret.blocked_modules[key].update(value)
    ret.dirs_with_unconverted_modules.update(item.dirs_with_unconverted_modules)
    ret.kind_of_unconverted_modules.update(item.kind_of_unconverted_modules)
    if len(ret.converted) == 0:
      ret.converted.update(item.converted)
  return ret


# Generate a dot file containing the transitive closure of the module.
def generate_dot_file(modules, converted, module):
  DOT_TEMPLATE = """
digraph mygraph {{
  node [shape=box];

  %s
}}
"""

  make_node = lambda module, color: \
      ('"{name}" [label="{name}\\n{kind}" color=black, style=filled, '
       "fillcolor={color}]").format(name=module.name, kind=module.kind, color=color)
  make_edge = lambda module, dep: \
      '"%s" -> "%s"' % (module.name, dep)

  # Check that all modules in the argument are in the list of converted modules
  all_converted = lambda modules: all(map(lambda m: m in converted, modules))

  dot_entries = []

  for module, deps in modules.items():
    if module.name in converted:
      # Skip converted modules (nodes)
      continue
    elif module.name not in converted:
      if all_converted(deps):
        dot_entries.append(make_node(module, "yellow"))
      else:
        dot_entries.append(make_node(module, "tomato"))

    # Print all edges for this module
    for dep in list(deps):
      # Skip converted deps (edges)
      if dep not in converted:
        dot_entries.append(make_edge(module, dep))

  print(DOT_TEMPLATE % "\n  ".join(dot_entries))


# Generate a report for each module in the transitive closure, and the blockers for each module
def generate_report_data(modules, converted, input_module):
  # Map of [number of unconverted deps] to list of entries,
  # with each entry being the string: "<module>: <comma separated list of unconverted modules>"
  blocked_modules = collections.defaultdict(set)

  # Map of unconverted modules to the modules they're blocking
  # (i.e. reverse deps)
  all_unconverted_modules = collections.defaultdict(set)

  dirs_with_unconverted_modules = set()
  kind_of_unconverted_modules = set()

  for module, deps in sorted(modules.items()):
    unconverted_deps = set(dep for dep in deps if dep not in converted)
    for dep in unconverted_deps:
      all_unconverted_modules[dep].add(module)

    unconverted_count = len(unconverted_deps)
    if module.name not in converted:
      report_entry = "{name} [{kind}] [{dirname}]: {unconverted_deps}".format(
          name=module.name,
          kind=module.kind,
          dirname=module.dirname,
          unconverted_deps=", ".join(sorted(unconverted_deps)))
      blocked_modules[unconverted_count].add(report_entry)
      dirs_with_unconverted_modules.add(module.dirname)
      kind_of_unconverted_modules.add(module.kind)

  return _ReportData(
      input_module=input_module,
      all_unconverted_modules=all_unconverted_modules,
      blocked_modules=blocked_modules,
      dirs_with_unconverted_modules=dirs_with_unconverted_modules,
      kind_of_unconverted_modules=kind_of_unconverted_modules,
      converted=converted,
  )


def generate_report(report_data):
  report_lines = []
  input_modules = sorted(report_data.input_module)

  report_lines.append("# bp2build progress report for: %s\n" % input_modules)
  report_lines.append("Ignored module types: %s\n" %
                      sorted(dependency_analysis.IGNORED_KINDS))
  report_lines.append("# Transitive dependency closure:")

  for count, modules in sorted(report_data.blocked_modules.items()):
    report_lines.append("\n%d unconverted deps remaining:" % count)
    for module_string in sorted(modules):
      report_lines.append("  " + module_string)

  report_lines.append("\n")
  report_lines.append("# Unconverted deps of {}:\n".format(input_modules))
  for count, dep in sorted(
      ((len(unconverted), dep)
       for dep, unconverted in report_data.all_unconverted_modules.items()),
      reverse=True):
    report_lines.append("%s: blocking %d modules" % (dep, count))

  report_lines.append("\n")
  report_lines.append("# Dirs with unconverted modules:\n\n{}".format("\n".join(
      sorted(report_data.dirs_with_unconverted_modules))))

  report_lines.append("\n")
  report_lines.append("# Kinds with unconverted modules:\n\n{}".format(
      "\n".join(sorted(report_data.kind_of_unconverted_modules))))

  report_lines.append("\n")
  report_lines.append("# Converted modules:\n\n%s" %
                      "\n".join(sorted(report_data.converted)))

  report_lines.append("\n")
  report_lines.append(
      "Generated by: https://cs.android.com/android/platform/superproject/+/master:build/bazel/scripts/bp2build-progress/bp2build-progress.py"
  )
  report_lines.append("Generated at: %s" %
                      datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S %z"))
  print("\n".join(report_lines))


def adjacency_list_from_json(module_graph, ignore_by_name, top_level_module):
  # The set of ignored modules. These modules (and their dependencies) are not shown
  # in the graph or report.
  ignored = set()

  # A map of module name to _ModuleInfo
  name_to_info = dict()
  module_graph_map = dict()
  q = queue.Queue()

  # Do a single pass to find all top-level modules to be ignored
  for module in module_graph:
    name = module["Name"]
    if dependency_analysis.is_windows_variation(module):
      continue
    if ignore_kind(module["Type"]) or name in ignore_by_name:
      ignored.add(module["Name"])
      continue
    name_to_info[name] = _ModuleInfo(
        name=name,
        kind=module["Type"],
        dirname=os.path.dirname(module["Blueprint"]))
    module_graph_map[module["Name"]] = module
    if module["Name"] == top_level_module:
      q.put(module["Name"])

  # An adjacency list for all modules in the transitive closure, excluding ignored modules.
  module_adjacency_list = {}
  visited = set()
  # Create the adjacency list.
  while not q.empty():
    module_name = q.get()
    module = module_graph_map[module_name]
    visited.add(module_name)
    if module_name in ignored:
      continue
    if dependency_analysis.is_windows_variation(module):
      # ignore the windows variations of modules
      continue

    module_info = name_to_info[module_name]
    module_adjacency_list[module_info] = set()
    for dep in module["Deps"]:
      dep_name = dep["Name"]
      if dep_name in ignored or dep_name == module_name:
        continue
      module_adjacency_list[module_info].add(dep_name)
      if dep_name not in visited:
        q.put(dep_name)

  return module_adjacency_list


def ignore_kind(kind):
  return kind in dependency_analysis.IGNORED_KINDS or "defaults" in kind


def bazel_target_to_dir(full_target):
  dirname, _ = full_target.split(":")
  return dirname[2:]


def adjacency_list_from_queryview_xml(module_graph, ignore_by_name,
                                      top_level_module):
  # The set of ignored modules. These modules (and their dependencies) are
  # not shown in the graph or report.
  ignored = set()

  # A map of module name to ModuleInfo
  name_to_info = dict()

  # queryview embeds variant in long name, keep a map of the name with vaiarnt
  # to just name
  name_with_variant_to_name = dict()

  module_graph_map = dict()
  q = queue.Queue()

  for module in module_graph:
    ignore = False
    if module.tag != "rule":
      continue
    kind = module.attrib["class"]
    name_with_variant = module.attrib["name"]
    name = None
    variant = ""
    for attr in module:
      attr_name = attr.attrib["name"]
      if attr_name == "soong_module_name":
        name = attr.attrib["value"]
      elif attr_name == "soong_module_variant":
        variant = attr.attrib["value"]
      elif attr_name == "soong_module_type" and kind == "generic_soong_module":
        kind = attr.attrib["value"]
      # special handling for filegroup srcs, if a source has the same name as
      # the module, we don't convert it
      elif kind == "filegroup" and attr_name == "srcs":
        for item in attr:
          if item.attrib["value"] == name:
            ignore = True
    if name in ignore_by_name:
      ignore = True

    if ignore_kind(kind) or variant.startswith("windows") or ignore:
      ignored.add(name_with_variant)
    else:
      if name == top_level_module:
        q.put(name_with_variant)
      name_with_variant_to_name.setdefault(name_with_variant, name)
      name_to_info.setdefault(
          name,
          _ModuleInfo(
              name=name,
              kind=kind,
              dirname=bazel_target_to_dir(name_with_variant),
          ))
      module_graph_map[name_with_variant] = module

  # An adjacency list for all modules in the transitive closure, excluding ignored modules.
  module_adjacency_list = dict()
  visited = set()
  while not q.empty():
    name_with_variant = q.get()
    module = module_graph_map[name_with_variant]
    if module.tag != "rule":
      continue
    visited.add(name_with_variant)
    if name_with_variant in ignored:
      continue

    name = name_with_variant_to_name[name_with_variant]
    module_info = name_to_info[name]
    module_adjacency_list[module_info] = set()
    for attr in module:
      if attr.tag != "rule-input":
        continue
      dep_name_with_variant = attr.attrib["name"]
      if dep_name_with_variant in ignored:
        continue
      dep_name = name_with_variant_to_name[dep_name_with_variant]
      if name == dep_name:
        continue
      if dep_name_with_variant not in visited:
        q.put(dep_name_with_variant)
      module_adjacency_list[module_info].add(dep_name)

  return module_adjacency_list


def get_module_adjacency_list(top_level_module, use_queryview, ignore_by_name):
  # The main module graph containing _all_ modules in the Soong build,
  # and the list of converted modules.
  try:
    module_graph = dependency_analysis.get_queryview_module_info(
        top_level_module
    ) if use_queryview else dependency_analysis.get_json_module_info(
        top_level_module)
    converted = dependency_analysis.get_bp2build_converted_modules()
  except subprocess.CalledProcessError as err:
    print("Error running: '%s':", " ".join(err.cmd))
    print("Output:\n%s" % err.output.decode("utf-8"))
    print("Error:\n%s" % err.stderr.decode("utf-8"))
    sys.exit(-1)

  module_adjacency_list = None
  if use_queryview:
    module_adjacency_list = adjacency_list_from_queryview_xml(
        module_graph, ignore_by_name, top_level_module)
  else:
    module_adjacency_list = adjacency_list_from_json(module_graph,
                                                     ignore_by_name,
                                                     top_level_module)

  return module_adjacency_list, converted


def main():
  parser = argparse.ArgumentParser(description="")
  parser.add_argument("mode", help="mode: graph or report")
  parser.add_argument(
      "--module",
      "-m",
      action="append",
      help="name(s) of Soong module(s). Multiple modules only supported for report"
  )
  parser.add_argument(
      "--use_queryview",
      type=bool,
      default=False,
      required=False,
      help="whether to use queryview or module_info")
  parser.add_argument(
      "--ignore_by_name",
      type=str,
      default="",
      required=False,
      help="Comma-separated list. When building the tree of transitive dependencies, will not follow dependency edges pointing to module names listed by this flag."
  )
  args = parser.parse_args()

  if len(args.module) > 1 and args.mode != "report":
    print("Can only support one module with mode {}", args.mode)

  mode = args.mode
  use_queryview = args.use_queryview
  ignore_by_name = args.ignore_by_name

  report_infos = []
  for top_level_module in args.module:
    module_adjacency_list, converted = get_module_adjacency_list(
        top_level_module, use_queryview, ignore_by_name)

    if mode == "graph":
      generate_dot_file(module_adjacency_list, converted, top_level_module)
    elif mode == "report":
      report_infos.append(
          generate_report_data(module_adjacency_list, converted,
                               top_level_module))
    else:
      raise RuntimeError("unknown mode: %s" % mode)

  if mode == "report":
    combined_data = combine_report_data(report_infos)
    generate_report(combined_data)


if __name__ == "__main__":
  main()
