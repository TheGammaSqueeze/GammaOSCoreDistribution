"""
Copied from tools/rootcanal/scripts/test_channel.py
"""

import socket
from time import sleep


class Connection:

    def __init__(self, port):
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._socket.connect(("localhost", port))

    def close(self):
        self._socket.close()

    def send(self, data):
        self._socket.sendall(data.encode())

    def receive(self, size):
        return self._socket.recv(size)


class TestChannel:

    def __init__(self, port):
        self._connection = Connection(port)
        self._closed = False

    def close(self):
        self._connection.close()
        self._closed = True

    def send_command(self, name, args):
        args = [str(arg) for arg in args]
        name_size = len(name)
        args_size = len(args)
        self.lint_command(name, args, name_size, args_size)
        encoded_name = chr(name_size) + name
        encoded_args = chr(args_size) + "".join(chr(len(arg)) + arg for arg in args)
        command = encoded_name + encoded_args
        if self._closed:
            return
        self._connection.send(command)
        if name != "CLOSE_TEST_CHANNEL":
            return self.receive_response().decode()

    def receive_response(self):
        if self._closed:
            return b"Closed"
        size_chars = self._connection.receive(4)
        if not size_chars:
            return b"No response, assuming that the connection is broken"
        response_size = 0
        for i in range(0, len(size_chars) - 1):
            response_size |= size_chars[i] << (8 * i)
        response = self._connection.receive(response_size)
        return response

    def lint_command(self, name, args, name_size, args_size):
        assert name_size == len(name) and args_size == len(args)
        try:
            name.encode()
            for arg in args:
                arg.encode()
        except UnicodeError:
            print("Unrecognized characters.")
            raise
        if name_size > 255 or args_size > 255:
            raise ValueError  # Size must be encodable in one octet.
        for arg in args:
            if len(arg) > 255:
                raise ValueError  # Size must be encodable in one octet.


class RootCanal:

    def __init__(self, port):
        self.channel = TestChannel(port)
        self.disconnected_dev_phys = None

        # discard initialization messages
        self.channel.receive_response()

    def close(self):
        self.channel.close()

    @staticmethod
    def _parse_device_list(raw):
        # time for some cursed parsing!
        categories = {}
        curr_category = None
        for line in raw.split("\n"):
            line = line.strip()
            if not line:
                continue
            if line[0].isdigit():
                # list entry
                if curr_category is None or ":" not in line:
                    raise Exception("Failed to parse rootcanal device list output")
                curr_category.append(line.split(":", 1)[1])
            else:
                if line.endswith(":"):
                    line = line[:-1]
                curr_category = []
                categories[line] = curr_category
        return categories

    @staticmethod
    def _parse_phy(raw):
        transport, idxs = raw.split(":")
        idxs = [int(x) for x in idxs.split(",") if x.strip()]
        return transport, idxs

    def reconnect_phone(self):
        raw_devices = None
        try:
            raw_devices = self.channel.send_command("list", [])
            devices = self._parse_device_list(raw_devices)

            for dev_i, name in enumerate(devices["Devices"]):
                # the default transports are always 0 and 1
                classic_phy = 0
                le_phy = 1
                if "beacon" in name:
                    target_phys = [le_phy]
                elif "hci_device" in name:
                    target_phys = [classic_phy, le_phy]
                else:
                    target_phys = []

                for phy in target_phys:
                    if dev_i not in self._parse_phy(devices["Phys"][phy])[1]:
                        self.channel.send_command("add_device_to_phy", [dev_i, phy])
        except Exception as e:
            print(raw_devices, e)

    def disconnect_phy(self):
        # first, list all devices
        devices = self.channel.send_command("list", [])
        devices = self._parse_device_list(devices)
        dev_phys = []

        for phy_i, phy in enumerate(devices["Phys"]):
            _, idxs = self._parse_phy(phy)

            for dev_i in idxs:
                dev_phys.append((dev_i, phy_i))

        # now, disconnect all pairs
        for dev_i, phy_i in dev_phys:
            self.channel.send_command("del_device_from_phy", [dev_i, phy_i])

        devices = self.channel.send_command("list", [])
        devices = self._parse_device_list(devices)

        self.disconnected_dev_phys = dev_phys

    def reconnect_phy_if_needed(self):
        if self.disconnected_dev_phys is not None:
            for dev_i, phy_i in self.disconnected_dev_phys:
                self.channel.send_command("add_device_to_phy", [dev_i, phy_i])

            self.disconnected_dev_phys = None

    def reconnect_phy(self):
        if self.disconnected_dev_phys is None:
            raise Exception("cannot reconnect_phy before disconnect_phy")

        self.reconnect_phy_if_needed()
