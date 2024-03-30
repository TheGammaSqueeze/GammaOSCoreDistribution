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

"""This is a shared library to help handling Mobly event waiting logic."""

import time
from typing import Callable

from mobly.controllers.android_device_lib import callback_handler
from mobly.controllers.android_device_lib import snippet_event

# Abbreviations for common use type
CallbackHandler = callback_handler.CallbackHandler
SnippetEvent = snippet_event.SnippetEvent

# Type definition for the callback functions to make code formatted nicely
OnReceivedCallback = Callable[[SnippetEvent, int], bool]
OnWaitingCallback = Callable[[int], None]
OnMissedCallback = Callable[[], None]


def wait_callback_event(callback_event_handler: CallbackHandler,
                        event_name: str, timeout_seconds: int,
                        on_received: OnReceivedCallback,
                        on_waiting: OnWaitingCallback,
                        on_missed: OnMissedCallback) -> None:
    """Waits until the matched event has been received or timeout.

    Here we keep waitAndGet for event callback from EventSnippet.
    We loop until over timeout_seconds instead of directly
    waitAndGet(timeout=teardown_timeout_seconds). Because there is
    MAX_TIMEOUT limitation in callback_handler of Mobly.

    Args:
      callback_event_handler: Mobly callback events handler.
      event_name: the specific name of the event to wait.
      timeout_seconds: the number of seconds to wait before giving up.
      on_received: calls when event received, return false to keep waiting.
      on_waiting: calls when waitAndGet timeout.
      on_missed: calls when giving up.
    """
    start_time = time.perf_counter()
    deadline = start_time + timeout_seconds
    while time.perf_counter() < deadline:
        remaining_time_sec = min(callback_handler.DEFAULT_TIMEOUT,
                                 deadline - time.perf_counter())
        try:
            event = callback_event_handler.waitAndGet(
                event_name, timeout=remaining_time_sec)
        except callback_handler.TimeoutError:
            elapsed_time = int(time.perf_counter() - start_time)
            on_waiting(elapsed_time)
        else:
            elapsed_time = int(time.perf_counter() - start_time)
            if on_received(event, elapsed_time):
                break
    else:
        on_missed()
