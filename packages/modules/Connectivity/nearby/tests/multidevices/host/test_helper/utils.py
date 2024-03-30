#  Copyright (C) 2022 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import json
import pathlib
import sys
from typing import Any, Dict

# Type definition
JsonObject = Dict[str, Any]


def load_json_fast_pair_test_data(json_file_name: str) -> JsonObject:
    """Loads a JSON text file from test data directory into a Json object.

    Args:
      json_file_name: The name of the JSON file.
    """
    return json.loads(
        pathlib.Path(sys.argv[0]).parent.joinpath(
            'test_data', 'fastpair', json_file_name).read_text()
    )


def serialize_as_simplified_json_str(json_data: JsonObject) -> str:
    """Serializes a JSON object into a string without empty space.

    Args:
      json_data: The JSON object to be serialized.
    """
    return json.dumps(json_data, separators=(',', ':'))
