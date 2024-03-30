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
"""SDP proxy module."""

from mmi2grpc._helpers import assert_description
from mmi2grpc._proxy import ProfileProxy

import sys
import threading
import os
import socket


class SDPProxy(ProfileProxy):

    def __init__(self, channel: str):
        super().__init__(channel)

    @assert_description
    def _mmi_6000(self, **kwargs):
        """
        If necessary take action to accept the SDP channel connection.
        """

        return "OK"

    @assert_description
    def _mmi_6001(self, **kwargs):
        """
        If necessary take action to respond to the Service Attribute operation
        appropriately.
        """

        return "OK"

    @assert_description
    def _mmi_6002(self, **kwargs):
        """
        If necessary take action to accept the Service Search operation.
        """

        return "OK"

    @assert_description
    def _mmi_6003(self, **kwargs):
        """
        If necessary take action to respond to the Service Search Attribute
        operation appropriately.
        """

        return "OK"

    @assert_description
    def TSC_SDP_mmi_verify_browsable_services(self, **kwargs):
        """
        Are all browsable service classes listed below?

        0x1800, 0x110A, 0x110C,
        0x110E, 0x1112, 0x1203, 0x111F, 0x1203, 0x1132, 0x1116, 0x1115, 0x112F,
        0x1105
        """
        """
        This is the decoded list of UUIDs:
            Service Classes and Profiles 0x1105 OBEXObjectPush
            Service Classes and Profiles 0x110A AudioSource
            Service Classes and Profiles 0x110C A/V_RemoteControlTarget
            Service Classes and Profiles 0x110E A/V_RemoteControl
            Service Classes and Profiles 0x1112 Headset - Audio Gateway
            Service Classes and Profiles 0x1115 PANU
            Service Classes and Profiles 0x1116 NAP
            Service Classes and Profiles 0x111F HandsfreeAudioGateway
            Service Classes and Profiles 0x112F Phonebook Access - PSE
            Service Classes and Profiles 0x1132 Message Access Server
            Service Classes and Profiles 0x1203 GenericAudio
            GATT Service 0x1800 Generic Access
            GATT Service 0x1855 TMAS

        The Android API only returns a subset of the profiles:
            0x110A, 0x1112, 0x111F, 0x112F, 0x1132,

        Since the API doesn't return the full set, this test uses the
        description to check that the number of profiles does not change
        from the last time the test was successfully run.

        Adding or Removing services from Android will cause this
        test to be fail.  Updating the description above will cause
        it to pass again.

        The other option is to add a call to btif_enable_service() for each
        profile which is browsable in SDP.  Then you can add a Host GRPC call
        to BluetoothAdapter.getUuidsList and match the returned UUIDs to the
        list given by PTS.
        """
        return "OK"
