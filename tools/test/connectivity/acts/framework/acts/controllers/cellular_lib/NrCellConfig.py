#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import acts.controllers.cellular_lib.BaseCellConfig as base_cell


class NrCellConfig(base_cell.BaseCellConfig):
    """ NR cell configuration class.

    Attributes:
        band: an integer indicating the required band number.
        bandwidth: a integer indicating the required channel bandwidth
    """

    PARAM_BAND = "band"
    PARAM_BW = "bw"

    def __init__(self, log):
        """ Initialize the base station config by setting all its
        parameters to None.
        Args:
            log: logger object.
        """
        super().__init__(log)
        self.band = None
        self.bandwidth = None

    def configure(self, parameters):
        """ Configures an NR cell using a dictionary of parameters.

        Args:
            parameters: a configuration dictionary
        """
        if self.PARAM_BAND not in parameters:
            raise ValueError(
                "The configuration dictionary must include a key '{}' with "
                "the required band number.".format(self.PARAM_BAND))

        self.band = parameters[self.PARAM_BAND]

        if self.PARAM_BW not in parameters:
            raise ValueError(
                "The config dictionary must include parameter {} with an "
                "int value (to indicate 1.4 MHz use 14).".format(
                    self.PARAM_BW))

        self.bandwidth = parameters[self.PARAM_BW]
