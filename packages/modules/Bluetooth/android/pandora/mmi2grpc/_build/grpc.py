#!/usr/bin/env python3

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

"""Build gRPC pandora interfaces."""

import os
import pkg_resources
from grpc_tools import protoc

package_directory = os.path.dirname(os.path.realpath(__file__))


def build():

    os.environ['PATH'] = package_directory + ':' + os.environ['PATH']

    proto_include = pkg_resources.resource_filename('grpc_tools', '_proto')

    files = [
        f'pandora/{f}' for f in os.listdir('proto/pandora') if f.endswith('.proto')]
    protoc.main([
        'grpc_tools.protoc',
        '-Iproto',
        f'-I{proto_include}',
        '--python_out=.',
        '--custom_grpc_out=.',
    ] + files)


if __name__ == '__main__':
    build()
