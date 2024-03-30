#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import asyncio
from mobly import test_runner
from blueberry.tests.gd.rust.topshim.facade import topshim_base_test
from blueberry.tests.gd.rust.topshim.facade.automation_helper import AdapterAutomationHelper


class AdapterTest(topshim_base_test.TopshimBaseTest):

    async def _test_verify_adapter_started(self):
        self.dut_adapter = AdapterAutomationHelper(port=self.dut_port)
        event_loop = asyncio.get_running_loop()
        self.dut_adapter.fetch_events(event_loop)
        self.dut_adapter.pending_future = event_loop.create_future()
        await self.dut_adapter.toggle_stack()
        await self.dut_adapter.verify_adapter_started()
        self.dut_adapter.event_handler.cancel()

    def test_verify_adapter_started(self):
        asyncio.run(self._test_verify_adapter_started())


if __name__ == "__main__":
    test_runner.main()
