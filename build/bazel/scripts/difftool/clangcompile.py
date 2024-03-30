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
# limitations under the License."""

"""Helpers pertaining to clang compile actions."""

import collections
import difflib
import pathlib
import subprocess
from typing import Callable
from commands import CommandInfo
from commands import flag_repr
from commands import is_flag_starts_with
from commands import parse_flag_groups


class ClangCompileInfo(CommandInfo):
  """Contains information about a clang compile action commandline."""

  def __init__(self, tool, args):
    CommandInfo.__init__(self, tool, args)

    flag_groups = parse_flag_groups(args, _custom_flag_group)

    misc = []
    i_includes = []
    iquote_includes = []
    isystem_includes = []
    defines = []
    warnings = []
    file_flags = []
    for g in flag_groups:
      if is_flag_starts_with("D", g) or is_flag_starts_with("U", g):
        defines += [g]
      elif is_flag_starts_with("I", g):
        i_includes += [g]
      elif is_flag_starts_with("isystem", g):
        isystem_includes += [g]
      elif is_flag_starts_with("iquote", g):
        iquote_includes += [g]
      elif is_flag_starts_with("W", g) or is_flag_starts_with("w", g):
        warnings += [g]
      elif (is_flag_starts_with("MF", g) or is_flag_starts_with("o", g) or
            _is_src_group(g)):
        file_flags += [g]
      else:
        misc += [g]
    self.misc_flags = sorted(misc, key=flag_repr)
    self.i_includes = _process_includes(i_includes)
    self.iquote_includes = _process_includes(iquote_includes)
    self.isystem_includes = _process_includes(isystem_includes)
    self.defines = _process_defines(defines)
    self.warnings = warnings
    self.file_flags = file_flags

  def _str_for_field(self, field_name, values):
    s = "  " + field_name + ":\n"
    for x in values:
      s += "    " + flag_repr(x) + "\n"
    return s

  def __str__(self):
    s = "ClangCompileInfo:\n"
    s += self._str_for_field("Includes (-I)", self.i_includes)
    s += self._str_for_field("Includes (-iquote)", self.iquote_includes)
    s += self._str_for_field("Includes (-isystem)", self.isystem_includes)
    s += self._str_for_field("Defines", self.defines)
    s += self._str_for_field("Warnings", self.warnings)
    s += self._str_for_field("Files", self.file_flags)
    s += self._str_for_field("Misc", self.misc_flags)
    return s


def _is_src_group(x):
  """Returns true if the given flag group describes a source file."""
  return isinstance(x, str) and x.endswith(".cpp")


def _custom_flag_group(x):
  """Identifies single-arg flag groups for clang compiles.

  Returns a flag group if the given argument corresponds to a single-argument
  flag group for clang compile. (For example, `-c` is a single-arg flag for
  clang compiles, but may not be for other tools.)

  See commands.parse_flag_groups documentation for signature details."""
  if x.startswith("-I") and len(x) > 2:
    return ("I", x[2:])
  if x.startswith("-W") and len(x) > 2:
    return (x)
  elif x == "-c":
    return x
  return None


def _process_defines(defs):
  """Processes and returns deduplicated define flags from all define args."""
  # TODO(cparsons): Determine and return effective defines (returning the last
  # set value).
  defines_by_var = collections.defaultdict(list)
  for x in defs:
    if isinstance(x, tuple):
      var_name = x[0][2:]
    else:
      var_name = x[2:]
    defines_by_var[var_name].append(x)
  result = []
  for k in sorted(defines_by_var):
    d = defines_by_var[k]
    for x in d:
      result += [x]
  return result


def _process_includes(includes):
  # Drop genfiles directories; makes diffing easier.
  result = []
  for x in includes:
    if isinstance(x, tuple):
      if not x[1].startswith("bazel-out"):
        result += [x]
    else:
      result += [x]
  return result


# given a file, give a list of "information" about it
ExtractInfo = Callable[[pathlib.Path], list[str]]


def _diff(left_path: pathlib.Path, right_path: pathlib.Path, tool_name: str,
    tool: ExtractInfo) -> list[str]:
  """Returns a list of strings describing differences in `.o` files.
  Returns the empty list if these files are deemed "similar enough".

  The given files must exist and must be object (.o) files."""
  errors = []

  left = tool(left_path)
  right = tool(right_path)
  comparator = difflib.context_diff(left, right)
  difflines = list(comparator)
  if difflines:
    err = "\n".join(difflines)
    errors.append(
      f"{left_path}\ndiffers from\n{right_path}\nper {tool_name}:\n{err}")
  return errors


def _external_tool(*args) -> ExtractInfo:
  return lambda file: subprocess.run([*args, str(file)],
                                     check=True, capture_output=True,
                                     encoding="utf-8").stdout.splitlines()


# TODO(usta) use nm as a data dependency
def nm_differences(left_path: pathlib.Path, right_path: pathlib.Path) -> list[
  str]:
  """Returns differences in symbol tables.
  Returns the empty list if these files are deemed "similar enough".

  The given files must exist and must be object (.o) files."""
  return _diff(left_path, right_path, "symbol tables", _external_tool("nm"))


# TODO(usta) use readelf as a data dependency
def elf_differences(left_path: pathlib.Path, right_path: pathlib.Path) -> list[
  str]:
  """Returns differences in elf headers.
  Returns the empty list if these files are deemed "similar enough".

  The given files must exist and must be object (.o) files."""
  return _diff(left_path, right_path, "elf headers",
               _external_tool("readelf", "-h"))
