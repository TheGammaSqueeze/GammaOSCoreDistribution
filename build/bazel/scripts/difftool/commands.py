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

"""Helper functions and types for command processing for difftool."""


class CommandInfo:
  """Contains information about an action commandline."""

  def __init__(self, tool, args):
    self.tool = tool
    self.args = args

  def __str__(self):
    s = "CommandInfo:\n"
    s += "  Tool:\n"
    s += "    " + self.tool + "\n"
    s += "  Args:\n"
    for x in self.args:
      s += "    " + x + "\n"
    return s


def parse_flag_groups(args, custom_flag_group=None):
  """Returns a list of flag groups based on the given args.

  An arg group consists of one-arg flags, two-arg groups, or positional args.

  Positional arguments (for example `a.out`) are returned as strings in the
    list.
  One-arg groups consist of a flag with no argument (for example, `--verbose`),
    and are returned as a tuple of size one in the list.
  Two-arg groups consist of a flag with a single argument (for example,
    `--file bar.txt` or `--mode=verbose`),
    and are returned as a tuple of size two in the list.

  Also accepts an optional function `custom_flag_group` to determine if a
  single arg comprises a group. (custom_flag_group(x) should return a flag
  group abiding by the above convention, or None to use non-custom logic.
  This may be required to accurately parse arg groups. For example, `-a b` may
  be either a one-arg group `-a` followed by a positonal group `b`, or a two-arg
  group `-a b`."""
  flag_groups = []

  i = 0
  while i < len(args):
    if custom_flag_group:
      g = custom_flag_group(args[i])
      if g is not None:
        flag_groups += [g]
        i += 1
        continue

    g = one_arg_group(args[i])
    if g is not None:
      flag_groups += [g]
      i += 1
      continue

    # Look for a two-arg group if there are at least 2 elements left.
    if i < len(args) - 1:
      g = two_arg_group(args[i], args[i+1])
      if g is not None:
        flag_groups += [g]
        i += 2
        continue

    # Not a recognized one arg group or two arg group.
    if args[i].startswith("-"):
      flag_groups += [(args[i])]
    else:
      flag_groups += [args[i]]
    i += 1

  return flag_groups


def remove_hyphens(x):
  """Returns the given string with leading '--' or '-' removed."""
  if x.startswith("--"):
    return x[2:]
  elif x.startswith("-"):
    return x[1:]
  else:
    return x


def two_arg_group(a, b):
  """Determines whether two consecutive args belong to a single flag group.

  Two arguments belong to a single flag group if the first arg contains
  a hyphen and the second does not. For example: `-foo bar` is a flag,
  but `foo bar` and `--foo --bar` are not.

  Returns:
    A tuple of the two args without hyphens if they belong to a single
    flag, or None if they do not. """
  if a.startswith("-") and (not b.startswith("-")):
    return (remove_hyphens(a), b)
  else:
    return None


def one_arg_group(x):
  """Determines whether an arg comprises a complete flag group.

  An argument comprises a single flag group if it is of the form of
  `-key=value` or `--key=value`.

  Returns:
    A tuple of `(key, value)` of the flag group, if the arg comprises a
    complete flag group, or None if it does not."""
  tokens = x.split("=")
  if len(tokens) == 2:
    return (remove_hyphens(tokens[0]), tokens[1])
  else:
    return None


def is_flag_starts_with(prefix, x):
  if isinstance(x, tuple):
    return x[0].startswith(prefix)
  else:
    return x.startswith("--" + prefix) or x.startswith("-" + prefix)


def flag_repr(x):
  if isinstance(x, tuple):
    return f"-{x[0]} {x[1]}"
  else:
    return x

