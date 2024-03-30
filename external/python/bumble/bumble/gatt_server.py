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
# GATT - Generic Attribute Profile
# Server
#
# See Bluetooth spec @ Vol 3, Part G
#
# -----------------------------------------------------------------------------

# -----------------------------------------------------------------------------
# Imports
# -----------------------------------------------------------------------------
import asyncio
import logging
from collections import defaultdict
from pyee import EventEmitter
from colors import color

from .core import *
from .hci import *
from .att import *
from .gatt import *

# -----------------------------------------------------------------------------
# Logging
# -----------------------------------------------------------------------------
logger = logging.getLogger(__name__)


# -----------------------------------------------------------------------------
# GATT Server
# -----------------------------------------------------------------------------
class Server(EventEmitter):
    def __init__(self, device):
        super().__init__()
        self.device                = device
        self.attributes            = []  # Attributes, ordered by increasing handle values
        self.attributes_by_handle  = {}  # Map for fast attribute access by handle
        self.max_mtu               = 23  # FIXME: 517  # The max MTU we're willing to negotiate
        self.subscribers           = {}  # Map of subscriber states by connection handle and attribute handle
        self.mtus                  = {}  # Map of ATT MTU values by connection handle
        self.indication_semaphores = defaultdict(lambda: asyncio.Semaphore(1))
        self.pending_confirmations = defaultdict(lambda: None)

    def send_gatt_pdu(self, connection_handle, pdu):
        self.device.send_l2cap_pdu(connection_handle, ATT_CID, pdu)

    def next_handle(self):
        return 1 + len(self.attributes)

    def get_attribute(self, handle):
        attribute = self.attributes_by_handle.get(handle)
        if attribute:
            return attribute

        # Not in the cached map, perform a linear lookup
        for attribute in self.attributes:
            if attribute.handle == handle:
                # Store in cached map
                self.attributes_by_handle[handle] = attribute
                return attribute
        return None

    def add_attribute(self, attribute):
        # Assign a handle to this attribute
        attribute.handle = self.next_handle()
        attribute.end_group_handle = attribute.handle  # TODO: keep track of descriptors in the group

        # Add this attribute to the list
        self.attributes.append(attribute)

    def add_service(self, service):
        # Add the service attribute to the DB
        self.add_attribute(service)

        # TODO: add included services

        # Add all characteristics
        for characteristic in service.characteristics:
            # Add a Characteristic Declaration (Vol 3, Part G - 3.3.1 Characteristic Declaration)
            declaration_bytes = struct.pack(
                '<BH',
                characteristic.properties,
                self.next_handle() + 1,  # The value will be the next attribute after this declaration
            ) + characteristic.uuid.to_pdu_bytes()
            characteristic_declaration = Attribute(
                GATT_CHARACTERISTIC_ATTRIBUTE_TYPE,
                Attribute.READABLE,
                declaration_bytes
            )
            self.add_attribute(characteristic_declaration)

            # Add the characteristic value
            self.add_attribute(characteristic)

            # Add the descriptors
            for descriptor in characteristic.descriptors:
                self.add_attribute(descriptor)

            # If the characteristic supports subscriptions, add a CCCD descriptor
            # unless there is one already
            if (
                characteristic.properties & (Characteristic.NOTIFY | Characteristic.INDICATE) and
                characteristic.get_descriptor(GATT_CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR) is None
            ):
                self.add_attribute(
                    Descriptor(
                        GATT_CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR,
                        Attribute.READABLE | Attribute.WRITEABLE,
                        CharacteristicValue(
                            read=lambda connection, characteristic=characteristic: self.read_cccd(connection, characteristic),
                            write=lambda connection, value, characteristic=characteristic: self.write_cccd(connection, characteristic, value)
                        )
                    )
                )

            # Update the service and characteristic group ends
            characteristic_declaration.end_group_handle = self.attributes[-1].handle
            characteristic.end_group_handle = self.attributes[-1].handle

        # Update the service group end
        service.end_group_handle = self.attributes[-1].handle

    def add_services(self, services):
        for service in services:
            self.add_service(service)

    def read_cccd(self, connection, characteristic):
        if connection is None:
            return bytes([0, 0])

        subscribers = self.subscribers.get(connection.handle)
        cccd = None
        if subscribers:
            cccd = subscribers.get(characteristic.handle)

        return cccd or bytes([0, 0])

    def write_cccd(self, connection, characteristic, value):
        logger.debug(f'Subscription update for connection={connection.handle:04X}, handle={characteristic.handle:04X}: {value.hex()}')

        # Sanity check
        if len(value) != 2:
            logger.warn('CCCD value not 2 bytes long')
            return

        cccds = self.subscribers.setdefault(connection.handle, {})
        cccds[characteristic.handle] = value
        logger.debug(f'CCCDs: {cccds}')
        notify_enabled = (value[0] & 0x01 != 0)
        indicate_enabled = (value[0] & 0x02 != 0)
        characteristic.emit('subscription', connection, notify_enabled, indicate_enabled)
        self.emit('characteristic_subscription', connection, characteristic, notify_enabled, indicate_enabled)

    def send_response(self, connection, response):
        logger.debug(f'GATT Response from server: [0x{connection.handle:04X}] {response}')
        self.send_gatt_pdu(connection.handle, response.to_bytes())

    async def notify_subscriber(self, connection, attribute, force=False):
        # Check if there's a subscriber
        if not force:
            subscribers = self.subscribers.get(connection.handle)
            if not subscribers:
                logger.debug('not notifying, no subscribers')
                return
            cccd = subscribers.get(attribute.handle)
            if not cccd:
                logger.debug(f'not notifying, no subscribers for handle {attribute.handle:04X}')
                return
            if len(cccd) != 2 or (cccd[0] & 0x01 == 0):
                logger.debug(f'not notifying, cccd={cccd.hex()}')
                return

        # Get the value
        value = attribute.read_value(connection)

        # Truncate if needed
        mtu = self.get_mtu(connection)
        if len(value) > mtu - 3:
            value = value[:mtu - 3]

        # Notify
        notification = ATT_Handle_Value_Notification(
            attribute_handle = attribute.handle,
            attribute_value  = value
        )
        logger.debug(f'GATT Notify from server: [0x{connection.handle:04X}] {notification}')
        self.send_gatt_pdu(connection.handle, notification.to_bytes())

    async def notify_subscribers(self, attribute, force=False):
        # Get all the connections for which there's at least one subscription
        connections = [
            connection for connection in [
                self.device.lookup_connection(connection_handle)
                for (connection_handle, subscribers) in self.subscribers.items()
                if force or subscribers.get(attribute.handle)
            ]
            if connection is not None
        ]

        # Notify for each connection
        if connections:
            await asyncio.wait([
                self.notify_subscriber(connection, attribute, force)
                for connection in connections
            ])

    async def indicate_subscriber(self, connection, attribute, force=False):
        # Check if there's a subscriber
        if not force:
            subscribers = self.subscribers.get(connection.handle)
            if not subscribers:
                logger.debug('not indicating, no subscribers')
                return
            cccd = subscribers.get(attribute.handle)
            if not cccd:
                logger.debug(f'not indicating, no subscribers for handle {attribute.handle:04X}')
                return
            if len(cccd) != 2 or (cccd[0] & 0x02 == 0):
                logger.debug(f'not indicating, cccd={cccd.hex()}')
                return

        # Get the value
        value = attribute.read_value(connection)

        # Truncate if needed
        mtu = self.get_mtu(connection)
        if len(value) > mtu - 3:
            value = value[:mtu - 3]

        # Indicate
        indication = ATT_Handle_Value_Indication(
            attribute_handle = attribute.handle,
            attribute_value  = value
        )
        logger.debug(f'GATT Indicate from server: [0x{connection.handle:04X}] {indication}')

        # Wait until we can send (only one pending indication at a time per connection)
        async with self.indication_semaphores[connection.handle]:
            assert(self.pending_confirmations[connection.handle] is None)

            # Create a future value to hold the eventual response
            self.pending_confirmations[connection.handle] = asyncio.get_running_loop().create_future()

            try:
                self.send_gatt_pdu(connection.handle, indication.to_bytes())
                await asyncio.wait_for(self.pending_confirmations[connection.handle], GATT_REQUEST_TIMEOUT)
            except asyncio.TimeoutError:
                logger.warning(color('!!! GATT Indicate timeout', 'red'))
                raise TimeoutError(f'GATT timeout for {indication.name}')
            finally:
                self.pending_confirmations[connection.handle] = None

    async def indicate_subscribers(self, attribute):
        # Get all the connections for which there's at least one subscription
        connections = [
            connection for connection in [
                self.device.lookup_connection(connection_handle)
                for (connection_handle, subscribers) in self.subscribers.items()
                if subscribers.get(attribute.handle)
            ]
            if connection is not None
        ]

        # Indicate for each connection
        if connections:
            await asyncio.wait([
                self.indicate_subscriber(connection, attribute)
                for connection in connections
            ])

    def on_disconnection(self, connection):
        if connection.handle in self.mtus:
            del self.mtus[connection.handle]
        if connection.handle in self.subscribers:
            del self.subscribers[connection.handle]
        if connection.handle in self.indication_semaphores:
            del self.indication_semaphores[connection.handle]
        if connection.handle in self.pending_confirmations:
            del self.pending_confirmations[connection.handle]

    def on_gatt_pdu(self, connection, att_pdu):
        logger.debug(f'GATT Request to server: [0x{connection.handle:04X}] {att_pdu}')
        handler_name = f'on_{att_pdu.name.lower()}'
        handler = getattr(self, handler_name, None)
        if handler is not None:
            try:
                handler(connection, att_pdu)
            except ATT_Error as error:
                logger.debug(f'normal exception returned by handler: {error}')
                response = ATT_Error_Response(
                    request_opcode_in_error   = att_pdu.op_code,
                    attribute_handle_in_error = error.att_handle,
                    error_code                = error.error_code
                )
                self.send_response(connection, response)
            except Exception as error:
                logger.warning(f'{color("!!! Exception in handler:", "red")} {error}')
                response = ATT_Error_Response(
                    request_opcode_in_error   = att_pdu.op_code,
                    attribute_handle_in_error = 0x0000,
                    error_code                = ATT_UNLIKELY_ERROR_ERROR
                )
                self.send_response(connection, response)
                raise error
        else:
            # No specific handler registered
            if att_pdu.op_code in ATT_REQUESTS:
                # Invoke the generic handler
                self.on_att_request(connection, att_pdu)
            else:
                # Just ignore
                logger.warning(f'{color("--- Ignoring GATT Request from [0x{connection.handle:04X}]:", "red")}  {att_pdu}')

    def get_mtu(self, connection):
        return self.mtus.get(connection.handle, ATT_DEFAULT_MTU)

    #######################################################
    # ATT handlers
    #######################################################
    def on_att_request(self, connection, pdu):
        '''
        Handler for requests without a more specific handler
        '''
        logger.warning(f'{color(f"--- Unsupported ATT Request from [0x{connection.handle:04X}]:", "red")} {pdu}')
        response = ATT_Error_Response(
            request_opcode_in_error   = pdu.op_code,
            attribute_handle_in_error = 0x0000,
            error_code                = ATT_REQUEST_NOT_SUPPORTED_ERROR
        )
        self.send_response(connection, response)

    def on_att_exchange_mtu_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.2.1 Exchange MTU Request
        '''
        mtu = max(ATT_DEFAULT_MTU, min(self.max_mtu, request.client_rx_mtu))
        self.mtus[connection.handle] = mtu
        self.send_response(connection, ATT_Exchange_MTU_Response(server_rx_mtu = mtu))

        # Notify the device
        self.device.on_connection_att_mtu_update(connection.handle, mtu)

    def on_att_find_information_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.3.1 Find Information Request
        '''

        # Check the request parameters
        if request.starting_handle == 0 or request.starting_handle > request.ending_handle:
            self.send_response(connection, ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.starting_handle,
                error_code                = ATT_INVALID_HANDLE_ERROR
            ))
            return

        # Build list of returned attributes
        pdu_space_available = self.get_mtu(connection) - 2
        attributes = []
        uuid_size = 0
        for attribute in (
            attribute for attribute in self.attributes if
            attribute.handle >= request.starting_handle and
            attribute.handle <= request.ending_handle
        ):
            # TODO: check permissions

            this_uuid_size = len(attribute.type.to_pdu_bytes())

            if attributes:
                # Check if this attribute has the same type size as the previous one
                if this_uuid_size != uuid_size:
                    break

            # Check if there's enough space for one more entry
            uuid_size = this_uuid_size
            if pdu_space_available < 2 + uuid_size:
                break

            # Add the attribute to the list
            attributes.append(attribute)
            pdu_space_available -= 2 + uuid_size

        # Return the list of attributes
        if attributes:
            information_data_list = [
                struct.pack('<H', attribute.handle) + attribute.type.to_pdu_bytes()
                for attribute in attributes
            ]
            response = ATT_Find_Information_Response(
                format           = 1 if len(attributes[0].type.to_pdu_bytes()) == 2 else 2,
                information_data = b''.join(information_data_list)
            )
        else:
            response = ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.starting_handle,
                error_code                = ATT_ATTRIBUTE_NOT_FOUND_ERROR
            )

        self.send_response(connection, response)

    def on_att_find_by_type_value_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.3.3 Find By Type Value Request
        '''

        # Build list of returned attributes
        pdu_space_available = self.get_mtu(connection) - 2
        attributes = []
        for attribute in (
            attribute for attribute in self.attributes if
            attribute.handle                 >= request.starting_handle and
            attribute.handle                 <= request.ending_handle   and
            attribute.type                   == request.attribute_type  and
            attribute.read_value(connection) == request.attribute_value and
            pdu_space_available >= 4
        ):
            # TODO: check permissions

            # Add the attribute to the list
            attributes.append(attribute)
            pdu_space_available -= 4

        # Return the list of attributes
        if attributes:
            handles_information_list = []
            for attribute in attributes:
                if attribute.type in {
                    GATT_PRIMARY_SERVICE_ATTRIBUTE_TYPE,
                    GATT_SECONDARY_SERVICE_ATTRIBUTE_TYPE,
                    GATT_CHARACTERISTIC_ATTRIBUTE_TYPE
                }:
                    # Part of a group
                    group_end_handle = attribute.end_group_handle
                else:
                    # Not part of a group
                    group_end_handle = attribute.handle
                handles_information_list.append(struct.pack('<HH', attribute.handle, group_end_handle))
            response = ATT_Find_By_Type_Value_Response(
                handles_information_list = b''.join(handles_information_list)
            )
        else:
            response = ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.starting_handle,
                error_code                = ATT_ATTRIBUTE_NOT_FOUND_ERROR
            )

        self.send_response(connection, response)

    def on_att_read_by_type_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.4.1 Read By Type Request
        '''

        mtu = self.get_mtu(connection)
        pdu_space_available = mtu - 2
        attributes = []
        for attribute in (
            attribute for attribute in self.attributes if
            attribute.type   == request.attribute_type  and
            attribute.handle >= request.starting_handle and
            attribute.handle <= request.ending_handle   and
            pdu_space_available
        ):
            # TODO: check permissions

            # Check the attribute value size
            attribute_value = attribute.read_value(connection)
            max_attribute_size = min(mtu - 4, 253)
            if len(attribute_value) > max_attribute_size:
                # We need to truncate
                attribute_value = attribute_value[:max_attribute_size]
            if attributes and len(attributes[0][1]) != len(attribute_value):
                # Not the same size as previous attribute, stop here
                break

            # Check if there is enough space
            entry_size = 2 + len(attribute_value)
            if pdu_space_available < entry_size:
                break

            # Add the attribute to the list
            attributes.append((attribute.handle, attribute_value))
            pdu_space_available -= entry_size

        if attributes:
            attribute_data_list = [struct.pack('<H', handle) + value for handle, value in attributes]
            response = ATT_Read_By_Type_Response(
                length              = entry_size,
                attribute_data_list = b''.join(attribute_data_list)
            )
        else:
            response = ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.starting_handle,
                error_code                = ATT_ATTRIBUTE_NOT_FOUND_ERROR
            )

        self.send_response(connection, response)

    def on_att_read_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.4.3 Read Request
        '''

        if attribute := self.get_attribute(request.attribute_handle):
            # TODO: check permissions
            value = attribute.read_value(connection)
            value_size = min(self.get_mtu(connection) - 1, len(value))
            response = ATT_Read_Response(
                attribute_value = value[:value_size]
            )
        else:
            response = ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.attribute_handle,
                error_code                = ATT_INVALID_HANDLE_ERROR
            )
        self.send_response(connection, response)

    def on_att_read_blob_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.4.5 Read Blob Request
        '''

        if attribute := self.get_attribute(request.attribute_handle):
            # TODO: check permissions
            mtu = self.get_mtu(connection)
            value = attribute.read_value(connection)
            if request.value_offset > len(value):
                response = ATT_Error_Response(
                    request_opcode_in_error = request.op_code,
                    attribute_handle_in_error = request.attribute_handle,
                    error_code                = ATT_INVALID_OFFSET_ERROR
                )
            elif len(value) <= mtu - 1:
                response = ATT_Error_Response(
                    request_opcode_in_error = request.op_code,
                    attribute_handle_in_error = request.attribute_handle,
                    error_code                = ATT_ATTRIBUTE_NOT_LONG_ERROR
                )
            else:
                part_size = min(mtu - 1, len(value) - request.value_offset)
                response = ATT_Read_Blob_Response(
                    part_attribute_value = value[request.value_offset:request.value_offset + part_size]
                )
        else:
            response = ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.attribute_handle,
                error_code                = ATT_INVALID_HANDLE_ERROR
            )
        self.send_response(connection, response)

    def on_att_read_by_group_type_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.4.9 Read by Group Type Request
        '''
        if request.attribute_group_type not in {
            GATT_PRIMARY_SERVICE_ATTRIBUTE_TYPE,
            GATT_SECONDARY_SERVICE_ATTRIBUTE_TYPE,
            GATT_INCLUDE_ATTRIBUTE_TYPE
        }:
            response = ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.starting_handle,
                error_code                = ATT_UNSUPPORTED_GROUP_TYPE_ERROR
            )
            self.send_response(connection, response)
            return

        mtu = self.get_mtu(connection)
        pdu_space_available = mtu - 2
        attributes = []
        for attribute in (
            attribute for attribute in self.attributes if
            attribute.type   == request.attribute_group_type and
            attribute.handle >= request.starting_handle      and
            attribute.handle <= request.ending_handle        and
            pdu_space_available
        ):
            # Check the attribute value size
            attribute_value = attribute.read_value(connection)
            max_attribute_size = min(mtu - 6, 251)
            if len(attribute_value) > max_attribute_size:
                # We need to truncate
                attribute_value = attribute_value[:max_attribute_size]
            if attributes and len(attributes[0][2]) != len(attribute_value):
                # Not the same size as previous attributes, stop here
                break

            # Check if there is enough space
            entry_size = 4 + len(attribute_value)
            if pdu_space_available < entry_size:
                break

            # Add the attribute to the list
            attributes.append((attribute.handle, attribute.end_group_handle, attribute_value))
            pdu_space_available -= entry_size

        if attributes:
            attribute_data_list = [
                struct.pack('<HH', handle, end_group_handle) + value
                for handle, end_group_handle, value in attributes
            ]
            response = ATT_Read_By_Group_Type_Response(
                length              = len(attribute_data_list[0]),
                attribute_data_list = b''.join(attribute_data_list)
            )
        else:
            response = ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.starting_handle,
                error_code                = ATT_ATTRIBUTE_NOT_FOUND_ERROR
            )

        self.send_response(connection, response)

    def on_att_write_request(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.5.1 Write Request
        '''

        # Check  that the attribute exists
        attribute = self.get_attribute(request.attribute_handle)
        if attribute is None:
            self.send_response(connection, ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.attribute_handle,
                error_code                = ATT_INVALID_HANDLE_ERROR
            ))
            return

        # TODO: check permissions

        # Check the request parameters
        if len(request.attribute_value) > GATT_MAX_ATTRIBUTE_VALUE_SIZE:
            self.send_response(connection, ATT_Error_Response(
                request_opcode_in_error   = request.op_code,
                attribute_handle_in_error = request.attribute_handle,
                error_code                = ATT_INVALID_ATTRIBUTE_LENGTH_ERROR
            ))
            return

        # Accept the value
        attribute.write_value(connection, request.attribute_value)

        # Done
        self.send_response(connection, ATT_Write_Response())

    def on_att_write_command(self, connection, request):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.5.3 Write Command
        '''

        # Check that the attribute exists
        attribute = self.get_attribute(request.attribute_handle)
        if attribute is None:
            return

        # TODO: check permissions

        # Check the request parameters
        if len(request.attribute_value) > GATT_MAX_ATTRIBUTE_VALUE_SIZE:
            return

        # Accept the value
        try:
            attribute.write_value(connection, request.attribute_value)
        except Exception as error:
            logger.warning(f'!!! ignoring exception: {error}')

    def on_att_handle_value_confirmation(self, connection, confirmation):
        '''
        See Bluetooth spec Vol 3, Part F - 3.4.7.3 Handle Value Confirmation
        '''
        if self.pending_confirmations[connection.handle] is None:
            # Not expected!
            logger.warning('!!! unexpected confirmation, there is no pending indication')
            return

        self.pending_confirmations[connection.handle].set_result(None)
