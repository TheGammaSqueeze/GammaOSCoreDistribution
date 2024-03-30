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
import grpc

from blueberry.facade.topshim import facade_pb2
from blueberry.facade.topshim import facade_pb2_grpc

from google.protobuf import empty_pb2 as empty_proto


class AdapterAutomationHelper():
    # Timeout for async wait
    DEFAULT_TIMEOUT = 6
    """Invoke gRPC on topshim for Adapter testing"""

    def __init__(self, port=8999):
        self.channel = grpc.aio.insecure_channel("localhost:%d" % port)
        self.adapter_stub = facade_pb2_grpc.AdapterServiceStub(self.channel)

        self.pending_future = None

    """Start fetching events"""

    def fetch_events(self, async_event_loop):
        self.adapter_event_stream = self.adapter_stub.FetchEvents(facade_pb2.FetchEventsRequest())
        self.event_handler = async_event_loop.create_task(self.get_next_event())

    """Enable/disable the stack"""

    async def toggle_stack(self, is_start=True):
        await self.adapter_stub.ToggleStack(facade_pb2.ToggleStackRequest(start_stack=is_start))

    """Enable page scan (might be used for A2dp sink to be discoverable)"""

    async def set_enable_page_scan(self):
        await self.adapter_stub.SetDiscoveryMode(facade_pb2.SetDiscoveryModeRequest(enable_page_scan=True))

    """Get the future of next event from the stream"""

    async def get_next_event(self):
        while True:
            e = await self.adapter_event_stream.read()
            # Match event by some condition.
            if e.event_type == facade_pb2.EventType.ADAPTER_STATE and e.data == "ON" and self.pending_future is not None:
                self.pending_future.set_result(True)

    async def verify_adapter_started(self):
        await asyncio.wait_for(self.pending_future, AdapterAutomationHelper.DEFAULT_TIMEOUT)

    async def clear_event_filter(self):
        await self.adapter_stub.ClearEventFilter(empty_proto.Empty())


class A2dpAutomationHelper():
    """Invoke gRPC on topshim for A2DP testing"""

    def __init__(self, port=8999):
        self.channel = grpc.insecure_channel("localhost:%d" % port)
        self.media_stub = facade_pb2_grpc.MediaServiceStub(self.channel)

    """Start A2dp source profile service"""

    def start_source(self):
        self.media_stub.StartA2dp(facade_pb2.StartA2dpRequest(start_a2dp_source=True))

    """Start A2dp sink profile service"""

    def start_sink(self):
        self.media_stub.StartA2dp(facade_pb2.StartA2dpRequest(start_a2dp_sink=True))

    """Initialize an A2dp connection from source to sink"""

    def source_connect_to_remote(self, address="11:22:33:44:55:66"):
        self.media_stub.A2dpSourceConnect(facade_pb2.A2dpSourceConnectRequest(address=address))
