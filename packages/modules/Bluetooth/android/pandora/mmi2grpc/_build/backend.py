# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""PEP517 build backend."""

from .grpc import build as build_pandora_grpc

from flit_core.wheel import WheelBuilder
# Use all build hooks from flit
from flit_core.buildapi import *


# Build grpc interfaces when this build backend is invoked
build_pandora_grpc()

# flit only supports copying one module, but we need to copy two of them
# because protobuf forces the use of absolute imports.
# So Monkey patches WheelBuilder#copy_module to copy pandora folder too.
# To avoid breaking this, the version of flit_core is pinned in pyproject.toml.
old_copy_module = WheelBuilder.copy_module


def copy_module(self):
    from flit_core.common import Module

    old_copy_module(self)

    module = self.module

    self.module = Module('pandora', self.directory)
    old_copy_module(self)

    self.module = module


WheelBuilder.copy_module = copy_module
