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

import typing
if typing.TYPE_CHECKING:
    from acts.controllers.fuchsia_device import FuchsiaDevice


class FuchsiaSessionManagerLib():
    def __init__(self, fuchsia_device):
        self.device: FuchsiaDevice = fuchsia_device

    def resumeSession(self):
        """Resumes a previously paused session

        Returns:
            Dictionary:
                error: None, unless an error occurs
                result: 'Success' or None if error
        """
        try:
            self.device.ffx.run(
                "component start /core/session-manager/session:session")
            return {'error': None, 'result': 'Success'}
        except Exception as e:
            return {'error': e, 'result': None}

    def pauseSession(self):
        """Pause the session, allowing for later resumption

        Returns:
            Dictionary:
                error: None, unless an error occurs
                result: 'Success', 'NoSessionToPause', or None if error
        """
        result = self.device.ffx.run(
            "component stop -r /core/session-manager/session:session",
            skip_status_code_check=True)

        if result.exit_status == 0:
            return {'error': None, 'result': 'Success'}
        else:
            if "InstanceNotFound" in result.stderr:
                return {'error': None, 'result': 'NoSessionToPause'}
            else:
                return {'error': result, 'result': None}
