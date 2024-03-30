# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Utilities for rule implementations to interact with platform definitions."""

# Merge ARCH_CONSTRAINT_ATTRS with the rule attrs to use get_arch(ctx).
ARCH_CONSTRAINT_ATTRS = {
    "_x86_constraint": attr.label(default = Label("//build/bazel/platforms/arch:x86")),
    "_x86_64_constraint": attr.label(default = Label("//build/bazel/platforms/arch:x86_64")),
    "_arm_constraint": attr.label(default = Label("//build/bazel/platforms/arch:arm")),
    "_arm64_constraint": attr.label(default = Label("//build/bazel/platforms/arch:arm64")),
}

# get_arch takes a rule context with ARCH_CONSTRAINT_ATTRS and returns the string representation
# of the target platform by executing the target_platform_has_constraint boilerplate.
def get_arch(ctx):
    if not hasattr(ctx.attr, "_x86_constraint") or \
      not hasattr(ctx.attr, "_x86_64_constraint") or \
      not hasattr(ctx.attr, "_arm_constraint") or \
      not hasattr(ctx.attr, "_arm64_constraint"):
      fail("Could not get the target architecture of this rule due to missing constraint attrs.",
           "Have you merged ARCH_CONSTRAINT_ATTRS into this rule's attributes?")

    x86_constraint = ctx.attr._x86_constraint[platform_common.ConstraintValueInfo]
    x86_64_constraint = ctx.attr._x86_64_constraint[platform_common.ConstraintValueInfo]
    arm_constraint = ctx.attr._arm_constraint[platform_common.ConstraintValueInfo]
    arm64_constraint = ctx.attr._arm64_constraint[platform_common.ConstraintValueInfo]

    if ctx.target_platform_has_constraint(x86_constraint):
        return "x86"
    elif ctx.target_platform_has_constraint(x86_64_constraint):
        return "x86_64"
    elif ctx.target_platform_has_constraint(arm_constraint):
        return "arm"
    elif ctx.target_platform_has_constraint(arm64_constraint):
        return "arm64"
