#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import logging

from blueberry.tests.gd.cert.gd_device import GdHostOnlyDevice
from blueberry.tests.gd.cert.gd_device import MOBLY_CONTROLLER_CONFIG_NAME
from blueberry.tests.gd.cert.os_utils import get_gd_root


def create(configs):
    return get_instances_with_configs(configs)


def destroy(devices):
    pass


def replace_vars_for_topshim(string, config):
    serial_number = config.get("serial_number")
    if serial_number is None:
        serial_number = ""
    rootcanal_port = config.get("rootcanal_port")
    if rootcanal_port is None:
        rootcanal_port = ""
    if serial_number == "DUT" or serial_number == "CERT":
        raise Exception("Did you forget to configure the serial number?")
    # We run bt_topshim_facade instead of bluetooth_stack_with_facade
    return string.replace("$GD_ROOT", get_gd_root()) \
                 .replace("bluetooth_stack_with_facade", "bt_topshim_facade") \
                 .replace("$(grpc_port)", config.get("grpc_port")) \
                 .replace("$(grpc_root_server_port)", config.get("grpc_root_server_port")) \
                 .replace("$(rootcanal_port)", rootcanal_port) \
                 .replace("$(signal_port)", config.get("signal_port")) \
                 .replace("$(serial_number)", serial_number)


def get_instances_with_configs(configs):
    logging.info(configs)
    devices = []
    for config in configs:
        resolved_cmd = []
        for arg in config["cmd"]:
            logging.debug(arg)
            resolved_cmd.append(replace_vars_for_topshim(arg, config))
        verbose_mode = bool(config.get('verbose_mode', False))
        device = GdHostOnlyDevice(config["grpc_port"], "-1", config["signal_port"], resolved_cmd, config["label"],
                                  MOBLY_CONTROLLER_CONFIG_NAME, config["name"], verbose_mode)
        device.setup()
        devices.append(device)
    return devices
