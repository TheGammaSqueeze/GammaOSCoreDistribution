# Copyright 2021-2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# -----------------------------------------------------------------------------
# Imports
# -----------------------------------------------------------------------------
import asyncio
import sys
import os

from bumble.hci import *
from bumble.controller import *
from bumble.device import *
from bumble.transport import *
from bumble.host import *
from bumble.transport import open_transport_or_link


# -----------------------------------------------------------------------------
async def main():
    if len(sys.argv) != 3:
        print('Usage: run_advertiser.py <config-file> <transport-spec>')
        print('example: run_advertiser.py device1.json link-relay:ws://localhost:8888/test')
        return

    print('<<< connecting to HCI...')
    async with await open_transport_or_link(sys.argv[2]) as (hci_source, hci_sink):
        print('<<< connected')

        device = Device.from_config_file_with_hci(sys.argv[1], hci_source, hci_sink)
        await device.power_on()
        await device.start_advertising()
        await hci_source.wait_for_termination()

# -----------------------------------------------------------------------------
logging.basicConfig(level = os.environ.get('BUMBLE_LOGLEVEL', 'DEBUG').upper())
asyncio.run(main())
