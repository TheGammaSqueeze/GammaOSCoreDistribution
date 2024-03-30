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
import struct
import collections
import logging
import functools
from colors import color

from .core import *

# -----------------------------------------------------------------------------
# Logging
# -----------------------------------------------------------------------------
logger = logging.getLogger(__name__)


# -----------------------------------------------------------------------------
# Utils
# -----------------------------------------------------------------------------
def hci_command_op_code(ogf, ocf):
    return (ogf << 10 | ocf)


def key_with_value(dictionary, target_value):
    for key, value in dictionary.items():
        if value == target_value:
            return key
    return None


def indent_lines(str):
    return '\n'.join(['  ' + line for line in str.split('\n')])


def map_null_terminated_utf8_string(utf8_bytes):
    try:
        terminator = utf8_bytes.find(0)
        if terminator < 0:
            return utf8_bytes
        return utf8_bytes[0:terminator].decode('utf8')
    except UnicodeDecodeError:
        return utf8_bytes


def map_class_of_device(class_of_device):
    service_classes, major_device_class, minor_device_class = DeviceClass.split_class_of_device(class_of_device)
    return f'[{class_of_device:06X}] Services({",".join(DeviceClass.service_class_labels(service_classes))}),Class({DeviceClass.major_device_class_name(major_device_class)}|{DeviceClass.minor_device_class_name(major_device_class, minor_device_class)})'


# -----------------------------------------------------------------------------
# Constants
# -----------------------------------------------------------------------------

# HCI Version
HCI_VERSION_BLUETOOTH_CORE_1_0B    = 0
HCI_VERSION_BLUETOOTH_CORE_1_1     = 1
HCI_VERSION_BLUETOOTH_CORE_1_2     = 2
HCI_VERSION_BLUETOOTH_CORE_2_0_EDR = 3
HCI_VERSION_BLUETOOTH_CORE_2_1_EDR = 4
HCI_VERSION_BLUETOOTH_CORE_3_0_HS  = 5
HCI_VERSION_BLUETOOTH_CORE_4_0     = 6
HCI_VERSION_BLUETOOTH_CORE_4_1     = 7
HCI_VERSION_BLUETOOTH_CORE_4_2     = 8
HCI_VERSION_BLUETOOTH_CORE_5_0     = 9
HCI_VERSION_BLUETOOTH_CORE_5_1     = 10
HCI_VERSION_BLUETOOTH_CORE_5_2     = 11
HCI_VERSION_BLUETOOTH_CORE_5_3     = 12

# HCI Packet types
HCI_COMMAND_PACKET          = 0x01
HCI_ACL_DATA_PACKET         = 0x02
HCI_SYNCHRONOUS_DATA_PACKET = 0x03
HCI_EVENT_PACKET            = 0x04

# HCI Event Codes
HCI_INQUIRY_COMPLETE_EVENT                            = 0x01
HCI_INQUIRY_RESULT_EVENT                              = 0x02
HCI_CONNECTION_COMPLETE_EVENT                         = 0x03
HCI_CONNECTION_REQUEST_EVENT                          = 0x04
HCI_DISCONNECTION_COMPLETE_EVENT                      = 0x05
HCI_AUTHENTICATION_COMPLETE_EVENT                     = 0x06
HCI_REMOTE_NAME_REQUEST_COMPLETE_EVENT                = 0x07
HCI_ENCRYPTION_CHANGE_EVENT                           = 0x08
HCI_CHANGE_CONNECTION_LINK_KEY_COMPLETE_EVENT         = 0x09
HCI_LINK_KEY_TYPE_CHANGED_EVENT                       = 0x0A
HCI_READ_REMOTE_SUPPORTED_FEATURES_COMPLETE_EVENT     = 0x0B
HCI_READ_REMOTE_VERSION_INFORMATION_COMPLETE_EVENT    = 0x0C
HCI_QOS_SETUP_COMPLETE_EVENT                          = 0x0D
HCI_COMMAND_COMPLETE_EVENT                            = 0x0E
HCI_COMMAND_STATUS_EVENT                              = 0x0F
HCI_HARDWARE_ERROR_EVENT                              = 0x10
HCI_FLUSH_OCCURRED_EVENT                              = 0x11
HCI_ROLE_CHANGE_EVENT                                 = 0x12
HCI_NUMBER_OF_COMPLETED_PACKETS_EVENT                 = 0x13
HCI_MODE_CHANGE_EVENT                                 = 0x14
HCI_RETURN_LINK_KEYS_EVENT                            = 0x15
HCI_PIN_CODE_REQUEST_EVENT                            = 0x16
HCI_LINK_KEY_REQUEST_EVENT                            = 0x17
HCI_LINK_KEY_NOTIFICATION_EVENT                       = 0x18
HCI_LOOPBACK_COMMAND_EVENT                            = 0x19
HCI_DATA_BUFFER_OVERFLOW_EVENT                        = 0x1A
HCI_MAX_SLOTS_CHANGE_EVENT                            = 0x1B
HCI_READ_CLOCK_OFFSET_COMPLETE_EVENT                  = 0x1C
HCI_CONNECTION_PACKET_TYPE_CHANGED_EVENT              = 0x1D
HCI_QOS_VIOLATION_EVENT                               = 0x1E
HCI_PAGE_SCAN_REPETITION_MODE_CHANGE_EVENT            = 0x20
HCI_FLOW_SPECIFICATION_COMPLETE_EVENT                 = 0x21
HCI_INQUIRY_RESULT_WITH_RSSI_EVENT                    = 0x22
HCI_READ_REMOTE_EXTENDED_FEATURES_COMPLETE_EVENT      = 0x23
HCI_SYNCHRONOUS_CONNECTION_COMPLETE_EVENT             = 0x2C
HCI_SYNCHRONOUS_CONNECTION_CHANGED_EVENT              = 0x2D
HCI_SNIFF_SUBRATING_EVENT                             = 0x2E
HCI_EXTENDED_INQUIRY_RESULT_EVENT                     = 0x2F
HCI_ENCRYPTION_KEY_REFRESH_COMPLETE_EVENT             = 0x30
HCI_IO_CAPABILITY_REQUEST_EVENT                       = 0x31
HCI_IO_CAPABILITY_RESPONSE_EVENT                      = 0x32
HCI_USER_CONFIRMATION_REQUEST_EVENT                   = 0x33
HCI_USER_PASSKEY_REQUEST_EVENT                        = 0x34
HCI_REMOTE_OOB_DATA_REQUEST                           = 0x35
HCI_SIMPLE_PAIRING_COMPLETE_EVENT                     = 0x36
HCI_LINK_SUPERVISION_TIMEOUT_CHANGED_EVENT            = 0x38
HCI_ENHANCED_FLUSH_COMPLETE_EVENT                     = 0x39
HCI_USER_PASSKEY_NOTIFICATION_EVENT                   = 0x3B
HCI_KEYPRESS_NOTIFICATION_EVENT                       = 0x3C
HCI_REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION_EVENT = 0x3D
HCI_LE_META_EVENT                                     = 0x3E
HCI_NUMBER_OF_COMPLETED_DATA_BLOCKS_EVENT             = 0x48

HCI_EVENT_NAMES = {
    HCI_INQUIRY_COMPLETE_EVENT:                            'HCI_INQUIRY_COMPLETE_EVENT',
    HCI_INQUIRY_RESULT_EVENT:                              'HCI_INQUIRY_RESULT_EVENT',
    HCI_CONNECTION_COMPLETE_EVENT:                         'HCI_CONNECTION_COMPLETE_EVENT',
    HCI_CONNECTION_REQUEST_EVENT:                          'HCI_CONNECTION_REQUEST_EVENT',
    HCI_DISCONNECTION_COMPLETE_EVENT:                      'HCI_DISCONNECTION_COMPLETE_EVENT',
    HCI_AUTHENTICATION_COMPLETE_EVENT:                     'HCI_AUTHENTICATION_COMPLETE_EVENT',
    HCI_REMOTE_NAME_REQUEST_COMPLETE_EVENT:                'HCI_REMOTE_NAME_REQUEST_COMPLETE_EVENT',
    HCI_ENCRYPTION_CHANGE_EVENT:                           'HCI_ENCRYPTION_CHANGE_EVENT',
    HCI_CHANGE_CONNECTION_LINK_KEY_COMPLETE_EVENT:         'HCI_CHANGE_CONNECTION_LINK_KEY_COMPLETE_EVENT',
    HCI_LINK_KEY_TYPE_CHANGED_EVENT:                       'HCI_LINK_KEY_TYPE_CHANGED_EVENT',
    HCI_INQUIRY_RESULT_WITH_RSSI_EVENT:                    'HCI_INQUIRY_RESULT_WITH_RSSI_EVENT',
    HCI_READ_REMOTE_SUPPORTED_FEATURES_COMPLETE_EVENT:     'HCI_READ_REMOTE_SUPPORTED_FEATURES_COMPLETE_EVENT',
    HCI_READ_REMOTE_VERSION_INFORMATION_COMPLETE_EVENT:    'HCI_READ_REMOTE_VERSION_INFORMATION_COMPLETE_EVENT',
    HCI_QOS_SETUP_COMPLETE_EVENT:                          'HCI_QOS_SETUP_COMPLETE_EVENT',
    HCI_SYNCHRONOUS_CONNECTION_COMPLETE_EVENT:             'HCI_SYNCHRONOUS_CONNECTION_COMPLETE_EVENT',
    HCI_SYNCHRONOUS_CONNECTION_CHANGED_EVENT:              'HCI_SYNCHRONOUS_CONNECTION_CHANGED_EVENT',
    HCI_SNIFF_SUBRATING_EVENT:                             'HCI_SNIFF_SUBRATING_EVENT',
    HCI_COMMAND_COMPLETE_EVENT:                            'HCI_COMMAND_COMPLETE_EVENT',
    HCI_COMMAND_STATUS_EVENT:                              'HCI_COMMAND_STATUS_EVENT',
    HCI_HARDWARE_ERROR_EVENT:                              'HCI_HARDWARE_ERROR_EVENT',
    HCI_FLUSH_OCCURRED_EVENT:                              'HCI_FLUSH_OCCURRED_EVENT',
    HCI_ROLE_CHANGE_EVENT:                                 'HCI_ROLE_CHANGE_EVENT',
    HCI_NUMBER_OF_COMPLETED_PACKETS_EVENT:                 'HCI_NUMBER_OF_COMPLETED_PACKETS_EVENT',
    HCI_MODE_CHANGE_EVENT:                                 'HCI_MODE_CHANGE_EVENT',
    HCI_RETURN_LINK_KEYS_EVENT:                            'HCI_RETURN_LINK_KEYS_EVENT',
    HCI_PIN_CODE_REQUEST_EVENT:                            'HCI_PIN_CODE_REQUEST_EVENT',
    HCI_LINK_KEY_REQUEST_EVENT:                            'HCI_LINK_KEY_REQUEST_EVENT',
    HCI_LINK_KEY_NOTIFICATION_EVENT:                       'HCI_LINK_KEY_NOTIFICATION_EVENT',
    HCI_LOOPBACK_COMMAND_EVENT:                            'HCI_LOOPBACK_COMMAND_EVENT',
    HCI_DATA_BUFFER_OVERFLOW_EVENT:                        'HCI_DATA_BUFFER_OVERFLOW_EVENT',
    HCI_MAX_SLOTS_CHANGE_EVENT:                            'HCI_MAX_SLOTS_CHANGE_EVENT',
    HCI_READ_CLOCK_OFFSET_COMPLETE_EVENT:                  'HCI_READ_CLOCK_OFFSET_COMPLETE_EVENT',
    HCI_CONNECTION_PACKET_TYPE_CHANGED_EVENT:              'HCI_CONNECTION_PACKET_TYPE_CHANGED_EVENT',
    HCI_QOS_VIOLATION_EVENT:                               'HCI_QOS_VIOLATION_EVENT',
    HCI_PAGE_SCAN_REPETITION_MODE_CHANGE_EVENT:            'HCI_PAGE_SCAN_REPETITION_MODE_CHANGE_EVENT',
    HCI_FLOW_SPECIFICATION_COMPLETE_EVENT:                 'HCI_FLOW_SPECIFICATION_COMPLETE_EVENT',
    HCI_READ_REMOTE_EXTENDED_FEATURES_COMPLETE_EVENT:      'HCI_READ_REMOTE_EXTENDED_FEATURES_COMPLETE_EVENT',
    HCI_EXTENDED_INQUIRY_RESULT_EVENT:                     'HCI_EXTENDED_INQUIRY_RESULT_EVENT',
    HCI_ENCRYPTION_KEY_REFRESH_COMPLETE_EVENT:             'HCI_ENCRYPTION_KEY_REFRESH_COMPLETE_EVENT',
    HCI_IO_CAPABILITY_REQUEST_EVENT:                       'HCI_IO_CAPABILITY_REQUEST_EVENT',
    HCI_IO_CAPABILITY_RESPONSE_EVENT:                      'HCI_IO_CAPABILITY_RESPONSE_EVENT',
    HCI_USER_CONFIRMATION_REQUEST_EVENT:                   'HCI_USER_CONFIRMATION_REQUEST_EVENT',
    HCI_USER_PASSKEY_REQUEST_EVENT:                        'HCI_USER_PASSKEY_REQUEST_EVENT',
    HCI_REMOTE_OOB_DATA_REQUEST:                           'HCI_REMOTE_OOB_DATA_REQUEST',
    HCI_SIMPLE_PAIRING_COMPLETE_EVENT:                     'HCI_SIMPLE_PAIRING_COMPLETE_EVENT',
    HCI_LINK_SUPERVISION_TIMEOUT_CHANGED_EVENT:            'HCI_LINK_SUPERVISION_TIMEOUT_CHANGED_EVENT',
    HCI_ENHANCED_FLUSH_COMPLETE_EVENT:                     'HCI_ENHANCED_FLUSH_COMPLETE_EVENT',
    HCI_USER_PASSKEY_NOTIFICATION_EVENT:                   'HCI_USER_PASSKEY_NOTIFICATION_EVENT',
    HCI_KEYPRESS_NOTIFICATION_EVENT:                       'HCI_KEYPRESS_NOTIFICATION_EVENT',
    HCI_REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION_EVENT: 'HCI_REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION_EVENT',
    HCI_LE_META_EVENT:                                     'HCI_LE_META_EVENT'
}

# HCI Subevent Codes
HCI_LE_CONNECTION_COMPLETE_EVENT                   = 0x01
HCI_LE_ADVERTISING_REPORT_EVENT                    = 0x02
HCI_LE_CONNECTION_UPDATE_COMPLETE_EVENT            = 0x03
HCI_LE_READ_REMOTE_FEATURES_COMPLETE_EVENT         = 0x04
HCI_LE_LONG_TERM_KEY_REQUEST_EVENT                 = 0x05
HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_EVENT   = 0x06
HCI_LE_DATA_LENGTH_CHANGE_EVENT                    = 0x07
HCI_LE_READ_LOCAL_P_256_PUBLIC_KEY_COMPLETE_EVENT  = 0x08
HCI_LE_GENERATE_DHKEY_COMPLETE_EVENT               = 0x09
HCI_LE_ENHANCED_CONNECTION_COMPLETE_EVENT          = 0x0A
HCI_LE_DIRECTED_ADVERTISING_REPORT_EVENT           = 0x0B
HCI_LE_PHY_UPDATE_COMPLETE_EVENT                   = 0x0C
HCI_LE_EXTENDED_ADVERTISING_REPORT_EVENT           = 0x0D
HCI_LE_PERIODIC_ADVERTISING_SYNC_ESTABLISHED_EVENT = 0x0E
HCI_LE_PERIODIC_ADVERTISING_REPORT_EVENT           = 0x0F
HCI_LE_PERIODIC_ADVERTISING_SYNC_LOST_EVENT        = 0x10
HCI_LE_SCAN_TIMEOUT_EVENT                          = 0x11
HCI_LE_ADVERTISING_SET_TERMINATED_EVENT            = 0x12
HCI_LE_SCAN_REQUEST_RECEIVED_EVENT                 = 0x13
HCI_LE_CHANNEL_SELECTION_ALGORITHM_EVENT           = 0x14

HCI_SUBEVENT_NAMES = {
    HCI_LE_CONNECTION_COMPLETE_EVENT:                   'HCI_LE_CONNECTION_COMPLETE_EVENT',
    HCI_LE_ADVERTISING_REPORT_EVENT:                    'HCI_LE_ADVERTISING_REPORT_EVENT',
    HCI_LE_CONNECTION_UPDATE_COMPLETE_EVENT:            'HCI_LE_CONNECTION_UPDATE_COMPLETE_EVENT',
    HCI_LE_READ_REMOTE_FEATURES_COMPLETE_EVENT:         'HCI_LE_READ_REMOTE_FEATURES_COMPLETE_EVENT',
    HCI_LE_LONG_TERM_KEY_REQUEST_EVENT:                 'HCI_LE_LONG_TERM_KEY_REQUEST_EVENT',
    HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_EVENT:   'HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_EVENT',
    HCI_LE_DATA_LENGTH_CHANGE_EVENT:                    'HCI_LE_DATA_LENGTH_CHANGE_EVENT',
    HCI_LE_READ_LOCAL_P_256_PUBLIC_KEY_COMPLETE_EVENT:  'HCI_LE_READ_LOCAL_P_256_PUBLIC_KEY_COMPLETE_EVENT',
    HCI_LE_GENERATE_DHKEY_COMPLETE_EVENT:               'HCI_LE_GENERATE_DHKEY_COMPLETE_EVENT',
    HCI_LE_ENHANCED_CONNECTION_COMPLETE_EVENT:          'HCI_LE_ENHANCED_CONNECTION_COMPLETE_EVENT',
    HCI_LE_DIRECTED_ADVERTISING_REPORT_EVENT:           'HCI_LE_DIRECTED_ADVERTISING_REPORT_EVENT',
    HCI_LE_PHY_UPDATE_COMPLETE_EVENT:                   'HCI_LE_PHY_UPDATE_COMPLETE_EVENT',
    HCI_LE_EXTENDED_ADVERTISING_REPORT_EVENT:           'HCI_LE_EXTENDED_ADVERTISING_REPORT_EVENT',
    HCI_LE_PERIODIC_ADVERTISING_SYNC_ESTABLISHED_EVENT: 'HCI_LE_PERIODIC_ADVERTISING_SYNC_ESTABLISHED_EVENT',
    HCI_LE_PERIODIC_ADVERTISING_REPORT_EVENT:           'HCI_LE_PERIODIC_ADVERTISING_REPORT_EVENT',
    HCI_LE_PERIODIC_ADVERTISING_SYNC_LOST_EVENT:        'HCI_LE_PERIODIC_ADVERTISING_SYNC_LOST_EVENT',
    HCI_LE_SCAN_TIMEOUT_EVENT:                          'HCI_LE_SCAN_TIMEOUT_EVENT',
    HCI_LE_ADVERTISING_SET_TERMINATED_EVENT:            'HCI_LE_ADVERTISING_SET_TERMINATED_EVENT',
    HCI_LE_SCAN_REQUEST_RECEIVED_EVENT:                 'HCI_LE_SCAN_REQUEST_RECEIVED_EVENT',
    HCI_LE_CHANNEL_SELECTION_ALGORITHM_EVENT:           'HCI_LE_CHANNEL_SELECTION_ALGORITHM_EVENT'
}

# HCI Command
HCI_INQUIRY_COMMAND                                               = hci_command_op_code(0x01, 0x0001)
HCI_INQUIRY_CANCEL_COMMAND                                        = hci_command_op_code(0x01, 0x0002)
HCI_CREATE_CONNECTION_COMMAND                                     = hci_command_op_code(0x01, 0x0005)
HCI_DISCONNECT_COMMAND                                            = hci_command_op_code(0x01, 0x0006)
HCI_ACCEPT_CONNECTION_REQUEST_COMMAND                             = hci_command_op_code(0x01, 0x0009)
HCI_LINK_KEY_REQUEST_REPLY_COMMAND                                = hci_command_op_code(0x01, 0x000B)
HCI_LINK_KEY_REQUEST_NEGATIVE_REPLY_COMMAND                       = hci_command_op_code(0x01, 0x000C)
HCI_PIN_CODE_REQUEST_NEGATIVE_REPLY_COMMAND                       = hci_command_op_code(0x01, 0x000E)
HCI_CHANGE_CONNECTION_PACKET_TYPE_COMMAND                         = hci_command_op_code(0x01, 0x000F)
HCI_AUTHENTICATION_REQUESTED_COMMAND                              = hci_command_op_code(0x01, 0x0011)
HCI_SET_CONNECTION_ENCRYPTION_COMMAND                             = hci_command_op_code(0x01, 0x0013)
HCI_REMOTE_NAME_REQUEST_COMMAND                                   = hci_command_op_code(0x01, 0x0019)
HCI_READ_REMOTE_SUPPORTED_FEATURES_COMMAND                        = hci_command_op_code(0x01, 0x001B)
HCI_READ_REMOTE_EXTENDED_FEATURES_COMMAND                         = hci_command_op_code(0x01, 0x001C)
HCI_READ_REMOTE_VERSION_INFORMATION_COMMAND                       = hci_command_op_code(0x01, 0x001D)
HCI_READ_CLOCK_OFFSET_COMMAND                                     = hci_command_op_code(0x01, 0x001F)
HCI_IO_CAPABILITY_REQUEST_REPLY_COMMAND                           = hci_command_op_code(0x01, 0x002B)
HCI_USER_CONFIRMATION_REQUEST_REPLY_COMMAND                       = hci_command_op_code(0x01, 0x002C)
HCI_USER_CONFIRMATION_REQUEST_NEGATIVE_REPLY_COMMAND              = hci_command_op_code(0x01, 0x002D)
HCI_USER_PASSKEY_REQUEST_REPLY_COMMAND                            = hci_command_op_code(0x01, 0x002E)
HCI_USER_PASSKEY_REQUEST_NEGATIVE_REPLY_COMMAND                   = hci_command_op_code(0x01, 0x002F)
HCI_ENHANCED_SETUP_SYNCHRONOUS_CONNECTION_COMMAND                 = hci_command_op_code(0x01, 0x003D)
HCI_SNIFF_MODE_COMMAND                                            = hci_command_op_code(0x02, 0x0003)
HCI_EXIT_SNIFF_MODE_COMMAND                                       = hci_command_op_code(0x02, 0x0004)
HCI_SWITCH_ROLE_COMMAND                                           = hci_command_op_code(0x02, 0x000B)
HCI_WRITE_LINK_POLICY_SETTINGS_COMMAND                            = hci_command_op_code(0x02, 0x000D)
HCI_WRITE_DEFAULT_LINK_POLICY_SETTINGS_COMMAND                    = hci_command_op_code(0x02, 0x000F)
HCI_SNIFF_SUBRATING_COMMAND                                       = hci_command_op_code(0x02, 0x0011)
HCI_SET_EVENT_MASK_COMMAND                                        = hci_command_op_code(0x03, 0x0001)
HCI_RESET_COMMAND                                                 = hci_command_op_code(0x03, 0x0003)
HCI_SET_EVENT_FILTER_COMMAND                                      = hci_command_op_code(0x03, 0x0005)
HCI_READ_STORED_LINK_KEY_COMMAND                                  = hci_command_op_code(0x03, 0x000D)
HCI_DELETE_STORED_LINK_KEY_COMMAND                                = hci_command_op_code(0x03, 0x0012)
HCI_WRITE_LOCAL_NAME_COMMAND                                      = hci_command_op_code(0x03, 0x0013)
HCI_READ_LOCAL_NAME_COMMAND                                       = hci_command_op_code(0x03, 0x0014)
HCI_WRITE_CONNECTION_ACCEPT_TIMEOUT_COMMAND                       = hci_command_op_code(0x03, 0x0016)
HCI_WRITE_PAGE_TIMEOUT_COMMAND                                    = hci_command_op_code(0x03, 0x0018)
HCI_WRITE_SCAN_ENABLE_COMMAND                                     = hci_command_op_code(0x03, 0x001A)
HCI_READ_PAGE_SCAN_ACTIVITY_COMMAND                               = hci_command_op_code(0x03, 0x001B)
HCI_WRITE_PAGE_SCAN_ACTIVITY_COMMAND                              = hci_command_op_code(0x03, 0x001C)
HCI_WRITE_INQUIRY_SCAN_ACTIVITY_COMMAND                           = hci_command_op_code(0x03, 0x001E)
HCI_READ_CLASS_OF_DEVICE_COMMAND                                  = hci_command_op_code(0x03, 0x0023)
HCI_WRITE_CLASS_OF_DEVICE_COMMAND                                 = hci_command_op_code(0x03, 0x0024)
HCI_READ_VOICE_SETTING_COMMAND                                    = hci_command_op_code(0x03, 0x0025)
HCI_WRITE_VOICE_SETTING_COMMAND                                   = hci_command_op_code(0x03, 0x0026)
HCI_READ_SYNCHRONOUS_FLOW_CONTROL_ENABLE_COMMAND                  = hci_command_op_code(0x03, 0x002E)
HCI_WRITE_SYNCHRONOUS_FLOW_CONTROL_ENABLE_COMMAND                 = hci_command_op_code(0x03, 0x002F)
HCI_HOST_BUFFER_SIZE_COMMAND                                      = hci_command_op_code(0x03, 0x0033)
HCI_WRITE_LINK_SUPERVISION_TIMEOUT_COMMAND                        = hci_command_op_code(0x03, 0x0037)
HCI_READ_NUMBER_OF_SUPPORTED_IAC_COMMAND                          = hci_command_op_code(0x03, 0x0038)
HCI_READ_CURRENT_IAC_LAP_COMMAND                                  = hci_command_op_code(0x03, 0x0039)
HCI_WRITE_INQUIRY_SCAN_TYPE_COMMAND                               = hci_command_op_code(0x03, 0x0043)
HCI_WRITE_INQUIRY_MODE_COMMAND                                    = hci_command_op_code(0x03, 0x0045)
HCI_READ_PAGE_SCAN_TYPE_COMMAND                                   = hci_command_op_code(0x03, 0x0046)
HCI_WRITE_PAGE_SCAN_TYPE_COMMAND                                  = hci_command_op_code(0x03, 0x0047)
HCI_WRITE_EXTENDED_INQUIRY_RESPONSE_COMMAND                       = hci_command_op_code(0x03, 0x0052)
HCI_WRITE_SIMPLE_PAIRING_MODE_COMMAND                             = hci_command_op_code(0x03, 0x0056)
HCI_READ_INQUIRY_RESPONSE_TRANSMIT_POWER_LEVEL_COMMAND            = hci_command_op_code(0x03, 0x0058)
HCI_SET_EVENT_MASK_PAGE_2_COMMAND                                 = hci_command_op_code(0x03, 0x0063)
HCI_READ_DEFAULT_ERRONEOUS_DATA_REPORTING_COMMAND                 = hci_command_op_code(0x03, 0x005A)
HCI_READ_LE_HOST_SUPPORT_COMMAND                                  = hci_command_op_code(0x03, 0x006C)
HCI_WRITE_LE_HOST_SUPPORT_COMMAND                                 = hci_command_op_code(0x03, 0x006D)
HCI_WRITE_SECURE_CONNECTIONS_HOST_SUPPORT_COMMAND                 = hci_command_op_code(0x03, 0x007A)
HCI_WRITE_AUTHENTICATED_PAYLOAD_TIMEOUT_COMMAND                   = hci_command_op_code(0x03, 0x007C)
HCI_READ_LOCAL_VERSION_INFORMATION_COMMAND                        = hci_command_op_code(0x04, 0x0001)
HCI_READ_LOCAL_SUPPORTED_COMMANDS_COMMAND                         = hci_command_op_code(0x04, 0x0002)
HCI_READ_LOCAL_SUPPORTED_FEATURES_COMMAND                         = hci_command_op_code(0x04, 0x0003)
HCI_READ_LOCAL_EXTENDED_FEATURES_COMMAND                          = hci_command_op_code(0x04, 0x0004)
HCI_READ_BUFFER_SIZE_COMMAND                                      = hci_command_op_code(0x04, 0x0005)
HCI_READ_BD_ADDR_COMMAND                                          = hci_command_op_code(0x04, 0x0009)
HCI_READ_LOCAL_SUPPORTED_CODECS_COMMAND                           = hci_command_op_code(0x04, 0x000B)
HCI_READ_ENCRYPTION_KEY_SIZE_COMMAND                              = hci_command_op_code(0x05, 0x0008)
HCI_LE_SET_EVENT_MASK_COMMAND                                     = hci_command_op_code(0x08, 0x0001)
HCI_LE_READ_BUFFER_SIZE_COMMAND                                   = hci_command_op_code(0x08, 0x0002)
HCI_LE_READ_LOCAL_SUPPORTED_FEATURES_COMMAND                      = hci_command_op_code(0x08, 0x0003)
HCI_LE_SET_RANDOM_ADDRESS_COMMAND                                 = hci_command_op_code(0x08, 0x0005)
HCI_LE_SET_ADVERTISING_PARAMETERS_COMMAND                         = hci_command_op_code(0x08, 0x0006)
HCI_LE_READ_ADVERTISING_CHANNEL_TX_POWER_COMMAND                  = hci_command_op_code(0x08, 0x0007)
HCI_LE_SET_ADVERTISING_DATA_COMMAND                               = hci_command_op_code(0x08, 0x0008)
HCI_LE_SET_SCAN_RESPONSE_DATA_COMMAND                             = hci_command_op_code(0x08, 0x0009)
HCI_LE_SET_ADVERTISING_ENABLE_COMMAND                             = hci_command_op_code(0x08, 0x000A)
HCI_LE_SET_SCAN_PARAMETERS_COMMAND                                = hci_command_op_code(0x08, 0x000B)
HCI_LE_SET_SCAN_ENABLE_COMMAND                                    = hci_command_op_code(0x08, 0x000C)
HCI_LE_CREATE_CONNECTION_COMMAND                                  = hci_command_op_code(0x08, 0x000D)
HCI_LE_CREATE_CONNECTION_CANCEL_COMMAND                           = hci_command_op_code(0x08, 0x000E)
HCI_LE_READ_WHITE_LIST_SIZE_COMMAND                               = hci_command_op_code(0x08, 0x000F)
HCI_LE_CLEAR_WHITE_LIST_COMMAND                                   = hci_command_op_code(0x08, 0x0010)
HCI_LE_ADD_DEVICE_TO_WHITE_LIST_COMMAND                           = hci_command_op_code(0x08, 0x0011)
HCI_LE_REMOVE_DEVICE_FROM_WHITE_LIST_COMMAND                      = hci_command_op_code(0x08, 0x0012)
HCI_LE_CONNECTION_UPDATE_COMMAND                                  = hci_command_op_code(0x08, 0x0013)
HCI_LE_SET_HOST_CHANNEL_CLASSIFICATION_COMMAND                    = hci_command_op_code(0x08, 0x0014)
HCI_LE_READ_CHANNEL_MAP_COMMAND                                   = hci_command_op_code(0x08, 0x0015)
HCI_LE_READ_REMOTE_FEATURES_COMMAND                               = hci_command_op_code(0x08, 0x0016)
HCI_LE_ENCRYPT_COMMAND                                            = hci_command_op_code(0x08, 0x0017)
HCI_LE_RAND_COMMAND                                               = hci_command_op_code(0x08, 0x0018)
HCI_LE_START_ENCRYPTION_COMMAND                                   = hci_command_op_code(0x08, 0x0019)
HCI_LE_LONG_TERM_KEY_REQUEST_REPLY_COMMAND                        = hci_command_op_code(0x08, 0x001A)
HCI_LE_LONG_TERM_KEY_REQUEST_NEGATIVE_REPLY_COMMAND               = hci_command_op_code(0x08, 0x001B)
HCI_LE_READ_SUPPORTED_STATES_COMMAND                              = hci_command_op_code(0x08, 0x001C)
HCI_LE_RECEIVER_TEST_COMMAND                                      = hci_command_op_code(0x08, 0x001D)
HCI_LE_TRANSMITTER_TEST_COMMAND                                   = hci_command_op_code(0x08, 0x001E)
HCI_LE_TEST_END_COMMAND                                           = hci_command_op_code(0x08, 0x001F)
HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_REPLY_COMMAND          = hci_command_op_code(0x08, 0x0020)
HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_NEGATIVE_REPLY_COMMAND = hci_command_op_code(0x08, 0x0021)
HCI_LE_SET_DATA_LENGTH_COMMAND                                    = hci_command_op_code(0x08, 0x0022)
HCI_LE_READ_SUGGESTED_DEFAULT_DATA_LENGTH_COMMAND                 = hci_command_op_code(0x08, 0x0023)
HCI_LE_WRITE_SUGGESTED_DEFAULT_DATA_LENGTH_COMMAND                = hci_command_op_code(0x08, 0x0024)
HCI_LE_READ_LOCAL_P_256_PUBLIC_KEY_COMMAND                        = hci_command_op_code(0x08, 0x0025)
HCI_LE_GENERATE_DHKEY_COMMAND                                     = hci_command_op_code(0x08, 0x0026)
HCI_LE_ADD_DEVICE_TO_RESOLVING_LIST_COMMAND                       = hci_command_op_code(0x08, 0x0027)
HCI_LE_REMOVE_DEVICE_FROM_RESOLVING_LIST_COMMAND                  = hci_command_op_code(0x08, 0x0028)
HCI_LE_CLEAR_RESOLVING_LIST_COMMAND                               = hci_command_op_code(0x08, 0x0029)
HCI_LE_READ_RESOLVING_LIST_SIZE_COMMAND                           = hci_command_op_code(0x08, 0x002A)
HCI_LE_READ_PEER_RESOLVABLE_ADDRESS_COMMAND                       = hci_command_op_code(0x08, 0x002B)
HCI_LE_READ_LOCAL_RESOLVABLE_ADDRESS_COMMAND                      = hci_command_op_code(0x08, 0x002C)
HCI_LE_SET_ADDRESS_RESOLUTION_ENABLE_COMMAND                      = hci_command_op_code(0x08, 0x002D)
HCI_LE_SET_RESOLVABLE_PRIVATE_ADDRESS_TIMEOUT_COMMAND             = hci_command_op_code(0x08, 0x002E)
HCI_LE_READ_MAXIMUM_DATA_LENGTH_COMMAND                           = hci_command_op_code(0x08, 0x002F)
HCI_LE_READ_PHY_COMMAND                                           = hci_command_op_code(0x08, 0x0030)
HCI_LE_SET_DEFAULT_PHY_COMMAND                                    = hci_command_op_code(0x08, 0x0031)
HCI_LE_SET_PHY_COMMAND                                            = hci_command_op_code(0x08, 0x0032)
HCI_LE_ENHANCED_RECEIVER_TEST_COMMAND                             = hci_command_op_code(0x08, 0x0033)
HCI_LE_ENHANCED_TRANSMITTER_TEST_COMMAND                          = hci_command_op_code(0x08, 0x0034)
HCI_LE_SET_ADVERTISING_SET_RANDOM_ADDRESS_COMMAND                 = hci_command_op_code(0x08, 0x0035)
HCI_LE_SET_EXTENDED_ADVERTISING_PARAMETERS_COMMAND                = hci_command_op_code(0x08, 0x0036)
HCI_LE_SET_EXTENDED_ADVERTISING_DATA_COMMAND                      = hci_command_op_code(0x08, 0x0037)
HCI_LE_SET_EXTENDED_SCAN_RESPONSE_DATA_COMMAND                    = hci_command_op_code(0x08, 0x0038)
HCI_LE_SET_EXTENDED_ADVERTISING_ENABLE_COMMAND                    = hci_command_op_code(0x08, 0x0039)
HCI_LE_READ_MAXIMUM_ADVERTISING_DATA_LENGTH_COMMAND               = hci_command_op_code(0x08, 0x003A)
HCI_LE_READ_NUMBER_OF_SUPPORTED_ADVERETISING_SETS_COMMAND         = hci_command_op_code(0x08, 0x003B)
HCI_LE_REMOVE_ADVERTISING_SET_COMMAND                             = hci_command_op_code(0x08, 0x003C)
HCI_LE_CLEAR_ADVERTISING_SETS_COMMAND                             = hci_command_op_code(0x08, 0x003D)
HCI_LE_SET_PERIODIC_ADVERTISING_PARAMETERS_COMMAND                = hci_command_op_code(0x08, 0x003E)
HCI_LE_SET_PERIODIC_ADVERTISING_DATA_COMMAND                      = hci_command_op_code(0x08, 0x003F)
HCI_LE_SET_PERIODIC_ADVERTISING_ENABLE_COMMAND                    = hci_command_op_code(0x08, 0x0040)
HCI_LE_SET_EXTENDED_SCAN_PARAMETERS_COMMAND                       = hci_command_op_code(0x08, 0x0041)
HCI_LE_SET_EXTENDED_SCAN_ENABLE_COMMAND                           = hci_command_op_code(0x08, 0x0042)
HCI_LE_SET_EXTENDED_CREATE_CONNECTION_COMMAND                     = hci_command_op_code(0x08, 0x0043)
HCI_LE_PERIODIC_ADVERTISING_CREATE_SYNC_COMMAND                   = hci_command_op_code(0x08, 0x0044)
HCI_LE_PERIODIC_ADVERTISING_CREATE_SYNC_CANCEL_COMMAND            = hci_command_op_code(0x08, 0x0045)
HCI_LE_PERIODIC_ADVERTISING_TERMINATE_SYNC_COMMAND                = hci_command_op_code(0x08, 0x0046)
HCI_LE_ADD_DEVICE_TO_PERIODIC_ADVERTISER_LIST_COMMAND             = hci_command_op_code(0x08, 0x0047)
HCI_LE_REMOVE_DEVICE_FROM_PERIODIC_ADVERTISER_LIST_COMMAND        = hci_command_op_code(0x08, 0x0048)
HCI_LE_CLEAR_PERIODIC_ADVERTISER_LIST_COMMAND                     = hci_command_op_code(0x08, 0x0049)
HCI_LE_READ_PERIODIC_ADVERTISER_LIST_SIZE_COMMAND                 = hci_command_op_code(0x08, 0x004A)
HCI_LE_READ_TRANSMIT_POWER_COMMAND                                = hci_command_op_code(0x08, 0x004B)
HCI_LE_READ_RF_PATH_COMPENSATION_COMMAND                          = hci_command_op_code(0x08, 0x004C)
HCI_LE_WRITE_RF_PATH_COMPENSATION_COMMAND                         = hci_command_op_code(0x08, 0x004D)
HCI_LE_SET_PRIVACY_MODE_COMMAND                                   = hci_command_op_code(0x08, 0x004E)


HCI_COMMAND_NAMES = {
    HCI_INQUIRY_COMMAND:                                               'HCI_INQUIRY_COMMAND',
    HCI_INQUIRY_CANCEL_COMMAND:                                        'HCI_INQUIRY_CANCEL_COMMAND',
    HCI_CREATE_CONNECTION_COMMAND:                                     'HCI_CREATE_CONNECTION_COMMAND',
    HCI_DISCONNECT_COMMAND:                                            'HCI_DISCONNECT_COMMAND',
    HCI_ACCEPT_CONNECTION_REQUEST_COMMAND:                             'HCI_ACCEPT_CONNECTION_REQUEST_COMMAND',
    HCI_LINK_KEY_REQUEST_REPLY_COMMAND:                                'HCI_LINK_KEY_REQUEST_REPLY_COMMAND',
    HCI_LINK_KEY_REQUEST_NEGATIVE_REPLY_COMMAND:                       'HCI_LINK_KEY_REQUEST_NEGATIVE_REPLY_COMMAND',
    HCI_PIN_CODE_REQUEST_NEGATIVE_REPLY_COMMAND:                       'HCI_PIN_CODE_REQUEST_NEGATIVE_REPLY_COMMAND',
    HCI_CHANGE_CONNECTION_PACKET_TYPE_COMMAND:                         'HCI_CHANGE_CONNECTION_PACKET_TYPE_COMMAND',
    HCI_AUTHENTICATION_REQUESTED_COMMAND:                              'HCI_AUTHENTICATION_REQUESTED_COMMAND',
    HCI_SET_CONNECTION_ENCRYPTION_COMMAND:                             'HCI_SET_CONNECTION_ENCRYPTION_COMMAND',
    HCI_REMOTE_NAME_REQUEST_COMMAND:                                   'HCI_REMOTE_NAME_REQUEST_COMMAND',
    HCI_READ_REMOTE_SUPPORTED_FEATURES_COMMAND:                        'HCI_READ_REMOTE_SUPPORTED_FEATURES_COMMAND',
    HCI_READ_REMOTE_EXTENDED_FEATURES_COMMAND:                         'HCI_READ_REMOTE_EXTENDED_FEATURES_COMMAND',
    HCI_READ_REMOTE_VERSION_INFORMATION_COMMAND:                       'HCI_READ_REMOTE_VERSION_INFORMATION_COMMAND',
    HCI_READ_CLOCK_OFFSET_COMMAND:                                     'HCI_READ_CLOCK_OFFSET_COMMAND',
    HCI_IO_CAPABILITY_REQUEST_REPLY_COMMAND:                           'HCI_IO_CAPABILITY_REQUEST_REPLY_COMMAND',
    HCI_USER_CONFIRMATION_REQUEST_REPLY_COMMAND:                       'HCI_USER_CONFIRMATION_REQUEST_REPLY_COMMAND',
    HCI_USER_CONFIRMATION_REQUEST_NEGATIVE_REPLY_COMMAND:              'HCI_USER_CONFIRMATION_REQUEST_NEGATIVE_REPLY_COMMAND',
    HCI_USER_PASSKEY_REQUEST_REPLY_COMMAND:                            'HCI_USER_PASSKEY_REQUEST_REPLY_COMMAND',
    HCI_USER_PASSKEY_REQUEST_NEGATIVE_REPLY_COMMAND:                   'HCI_USER_PASSKEY_REQUEST_NEGATIVE_REPLY_COMMAND',
    HCI_ENHANCED_SETUP_SYNCHRONOUS_CONNECTION_COMMAND:                 'HCI_ENHANCED_SETUP_SYNCHRONOUS_CONNECTION_COMMAND',
    HCI_SNIFF_MODE_COMMAND:                                            'HCI_SNIFF_MODE_COMMAND',
    HCI_EXIT_SNIFF_MODE_COMMAND:                                       'HCI_EXIT_SNIFF_MODE_COMMAND',
    HCI_SWITCH_ROLE_COMMAND:                                           'HCI_SWITCH_ROLE_COMMAND',
    HCI_WRITE_LINK_POLICY_SETTINGS_COMMAND:                            'HCI_WRITE_LINK_POLICY_SETTINGS_COMMAND',
    HCI_WRITE_DEFAULT_LINK_POLICY_SETTINGS_COMMAND:                    'HCI_WRITE_DEFAULT_LINK_POLICY_SETTINGS_COMMAND',
    HCI_SNIFF_SUBRATING_COMMAND:                                       'HCI_SNIFF_SUBRATING_COMMAND',
    HCI_SET_EVENT_MASK_COMMAND:                                        'HCI_SET_EVENT_MASK_COMMAND',
    HCI_RESET_COMMAND:                                                 'HCI_RESET_COMMAND',
    HCI_SET_EVENT_FILTER_COMMAND:                                      'HCI_SET_EVENT_FILTER_COMMAND',
    HCI_READ_STORED_LINK_KEY_COMMAND:                                  'HCI_READ_STORED_LINK_KEY_COMMAND',
    HCI_DELETE_STORED_LINK_KEY_COMMAND:                                'HCI_DELETE_STORED_LINK_KEY_COMMAND',
    HCI_WRITE_LOCAL_NAME_COMMAND:                                      'HCI_WRITE_LOCAL_NAME_COMMAND',
    HCI_READ_LOCAL_NAME_COMMAND:                                       'HCI_READ_LOCAL_NAME_COMMAND',
    HCI_WRITE_CONNECTION_ACCEPT_TIMEOUT_COMMAND:                       'HCI_WRITE_CONNECTION_ACCEPT_TIMEOUT_COMMAND',
    HCI_WRITE_PAGE_TIMEOUT_COMMAND:                                    'HCI_WRITE_PAGE_TIMEOUT_COMMAND',
    HCI_WRITE_SCAN_ENABLE_COMMAND:                                     'HCI_WRITE_SCAN_ENABLE_COMMAND',
    HCI_READ_PAGE_SCAN_ACTIVITY_COMMAND:                               'HCI_READ_PAGE_SCAN_ACTIVITY_COMMAND',
    HCI_WRITE_PAGE_SCAN_ACTIVITY_COMMAND:                              'HCI_WRITE_PAGE_SCAN_ACTIVITY_COMMAND',
    HCI_WRITE_INQUIRY_SCAN_ACTIVITY_COMMAND:                           'HCI_WRITE_INQUIRY_SCAN_ACTIVITY_COMMAND',
    HCI_READ_CLASS_OF_DEVICE_COMMAND:                                  'HCI_READ_CLASS_OF_DEVICE_COMMAND',
    HCI_WRITE_CLASS_OF_DEVICE_COMMAND:                                 'HCI_WRITE_CLASS_OF_DEVICE_COMMAND',
    HCI_READ_VOICE_SETTING_COMMAND:                                    'HCI_READ_VOICE_SETTING_COMMAND',
    HCI_WRITE_VOICE_SETTING_COMMAND:                                   'HCI_WRITE_VOICE_SETTING_COMMAND',
    HCI_READ_SYNCHRONOUS_FLOW_CONTROL_ENABLE_COMMAND:                  'HCI_READ_SYNCHRONOUS_FLOW_CONTROL_ENABLE_COMMAND',
    HCI_WRITE_SYNCHRONOUS_FLOW_CONTROL_ENABLE_COMMAND:                 'HCI_WRITE_SYNCHRONOUS_FLOW_CONTROL_ENABLE_COMMAND',
    HCI_HOST_BUFFER_SIZE_COMMAND:                                      'HCI_HOST_BUFFER_SIZE_COMMAND',
    HCI_WRITE_LINK_SUPERVISION_TIMEOUT_COMMAND:                        'HCI_WRITE_LINK_SUPERVISION_TIMEOUT_COMMAND',
    HCI_READ_NUMBER_OF_SUPPORTED_IAC_COMMAND:                          'HCI_READ_NUMBER_OF_SUPPORTED_IAC_COMMAND',
    HCI_READ_CURRENT_IAC_LAP_COMMAND:                                  'HCI_READ_CURRENT_IAC_LAP_COMMAND',
    HCI_WRITE_INQUIRY_SCAN_TYPE_COMMAND:                               'HCI_WRITE_INQUIRY_SCAN_TYPE_COMMAND',
    HCI_WRITE_INQUIRY_MODE_COMMAND:                                    'HCI_WRITE_INQUIRY_MODE_COMMAND',
    HCI_READ_PAGE_SCAN_TYPE_COMMAND:                                   'HCI_READ_PAGE_SCAN_TYPE_COMMAND',
    HCI_WRITE_PAGE_SCAN_TYPE_COMMAND:                                  'HCI_WRITE_PAGE_SCAN_TYPE_COMMAND',
    HCI_WRITE_EXTENDED_INQUIRY_RESPONSE_COMMAND:                       'HCI_WRITE_EXTENDED_INQUIRY_RESPONSE_COMMAND',
    HCI_WRITE_SIMPLE_PAIRING_MODE_COMMAND:                             'HCI_WRITE_SIMPLE_PAIRING_MODE_COMMAND',
    HCI_READ_INQUIRY_RESPONSE_TRANSMIT_POWER_LEVEL_COMMAND:            'HCI_READ_INQUIRY_RESPONSE_TRANSMIT_POWER_LEVEL_COMMAND',
    HCI_SET_EVENT_MASK_PAGE_2_COMMAND:                                 'HCI_SET_EVENT_MASK_PAGE_2_COMMAND',
    HCI_READ_DEFAULT_ERRONEOUS_DATA_REPORTING_COMMAND:                 'HCI_READ_DEFAULT_ERRONEOUS_DATA_REPORTING_COMMAND',
    HCI_READ_LOCAL_VERSION_INFORMATION_COMMAND:                        'HCI_READ_LOCAL_VERSION_INFORMATION_COMMAND',
    HCI_READ_LOCAL_SUPPORTED_COMMANDS_COMMAND:                         'HCI_READ_LOCAL_SUPPORTED_COMMANDS_COMMAND',
    HCI_READ_LOCAL_SUPPORTED_FEATURES_COMMAND:                         'HCI_READ_LOCAL_SUPPORTED_FEATURES_COMMAND',
    HCI_READ_LOCAL_EXTENDED_FEATURES_COMMAND:                          'HCI_READ_LOCAL_EXTENDED_FEATURES_COMMAND',
    HCI_READ_BUFFER_SIZE_COMMAND:                                      'HCI_READ_BUFFER_SIZE_COMMAND',
    HCI_READ_LE_HOST_SUPPORT_COMMAND:                                  'HCI_READ_LE_HOST_SUPPORT_COMMAND',
    HCI_WRITE_LE_HOST_SUPPORT_COMMAND:                                 'HCI_WRITE_LE_HOST_SUPPORT_COMMAND',
    HCI_WRITE_SECURE_CONNECTIONS_HOST_SUPPORT_COMMAND:                 'HCI_WRITE_SECURE_CONNECTIONS_HOST_SUPPORT_COMMAND',
    HCI_WRITE_AUTHENTICATED_PAYLOAD_TIMEOUT_COMMAND:                   'HCI_WRITE_AUTHENTICATED_PAYLOAD_TIMEOUT_COMMAND',
    HCI_READ_BD_ADDR_COMMAND:                                          'HCI_READ_BD_ADDR_COMMAND',
    HCI_READ_LOCAL_SUPPORTED_CODECS_COMMAND:                           'HCI_READ_LOCAL_SUPPORTED_CODECS_COMMAND',
    HCI_READ_ENCRYPTION_KEY_SIZE_COMMAND:                              'HCI_READ_ENCRYPTION_KEY_SIZE_COMMAND',
    HCI_LE_SET_EVENT_MASK_COMMAND:                                     'HCI_LE_SET_EVENT_MASK_COMMAND',
    HCI_LE_READ_BUFFER_SIZE_COMMAND:                                   'HCI_LE_READ_BUFFER_SIZE_COMMAND',
    HCI_LE_READ_LOCAL_SUPPORTED_FEATURES_COMMAND:                      'HCI_LE_READ_LOCAL_SUPPORTED_FEATURES_COMMAND',
    HCI_LE_SET_RANDOM_ADDRESS_COMMAND:                                 'HCI_LE_SET_RANDOM_ADDRESS_COMMAND',
    HCI_LE_SET_ADVERTISING_PARAMETERS_COMMAND:                         'HCI_LE_SET_ADVERTISING_PARAMETERS_COMMAND',
    HCI_LE_READ_ADVERTISING_CHANNEL_TX_POWER_COMMAND:                  'HCI_LE_READ_ADVERTISING_CHANNEL_TX_POWER_COMMAND',
    HCI_LE_SET_ADVERTISING_DATA_COMMAND:                               'HCI_LE_SET_ADVERTISING_DATA_COMMAND',
    HCI_LE_SET_SCAN_RESPONSE_DATA_COMMAND:                             'HCI_LE_SET_SCAN_RESPONSE_DATA_COMMAND',
    HCI_LE_SET_ADVERTISING_ENABLE_COMMAND:                             'HCI_LE_SET_ADVERTISING_ENABLE_COMMAND',
    HCI_LE_SET_SCAN_PARAMETERS_COMMAND:                                'HCI_LE_SET_SCAN_PARAMETERS_COMMAND',
    HCI_LE_SET_SCAN_ENABLE_COMMAND:                                    'HCI_LE_SET_SCAN_ENABLE_COMMAND',
    HCI_LE_CREATE_CONNECTION_COMMAND:                                  'HCI_LE_CREATE_CONNECTION_COMMAND',
    HCI_LE_CREATE_CONNECTION_CANCEL_COMMAND:                           'HCI_LE_CREATE_CONNECTION_CANCEL_COMMAND',
    HCI_LE_READ_WHITE_LIST_SIZE_COMMAND:                               'HCI_LE_READ_WHITE_LIST_SIZE_COMMAND',
    HCI_LE_CLEAR_WHITE_LIST_COMMAND:                                   'HCI_LE_CLEAR_WHITE_LIST_COMMAND',
    HCI_LE_ADD_DEVICE_TO_WHITE_LIST_COMMAND:                           'HCI_LE_ADD_DEVICE_TO_WHITE_LIST_COMMAND',
    HCI_LE_REMOVE_DEVICE_FROM_WHITE_LIST_COMMAND:                      'HCI_LE_REMOVE_DEVICE_FROM_WHITE_LIST_COMMAND',
    HCI_LE_CONNECTION_UPDATE_COMMAND:                                  'HCI_LE_CONNECTION_UPDATE_COMMAND',
    HCI_LE_SET_HOST_CHANNEL_CLASSIFICATION_COMMAND:                    'HCI_LE_SET_HOST_CHANNEL_CLASSIFICATION_COMMAND',
    HCI_LE_READ_CHANNEL_MAP_COMMAND:                                   'HCI_LE_READ_CHANNEL_MAP_COMMAND',
    HCI_LE_READ_REMOTE_FEATURES_COMMAND:                               'HCI_LE_READ_REMOTE_FEATURES_COMMAND',
    HCI_LE_ENCRYPT_COMMAND:                                            'HCI_LE_ENCRYPT_COMMAND',
    HCI_LE_RAND_COMMAND:                                               'HCI_LE_RAND_COMMAND',
    HCI_LE_START_ENCRYPTION_COMMAND:                                   'HCI_LE_START_ENCRYPTION_COMMAND',
    HCI_LE_LONG_TERM_KEY_REQUEST_REPLY_COMMAND:                        'HCI_LE_LONG_TERM_KEY_REQUEST_REPLY_COMMAND',
    HCI_LE_LONG_TERM_KEY_REQUEST_NEGATIVE_REPLY_COMMAND:               'HCI_LE_LONG_TERM_KEY_REQUEST_NEGATIVE_REPLY_COMMAND',
    HCI_LE_READ_SUPPORTED_STATES_COMMAND:                              'HCI_LE_READ_SUPPORTED_STATES_COMMAND',
    HCI_LE_RECEIVER_TEST_COMMAND:                                      'HCI_LE_RECEIVER_TEST_COMMAND',
    HCI_LE_TRANSMITTER_TEST_COMMAND:                                   'HCI_LE_TRANSMITTER_TEST_COMMAND',
    HCI_LE_TEST_END_COMMAND:                                           'HCI_LE_TEST_END_COMMAND',
    HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_REPLY_COMMAND:          'HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_REPLY_COMMAND',
    HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_NEGATIVE_REPLY_COMMAND: 'HCI_LE_REMOTE_CONNECTION_PARAMETER_REQUEST_NEGATIVE_REPLY_COMMAND',
    HCI_LE_SET_DATA_LENGTH_COMMAND:                                    'HCI_LE_SET_DATA_LENGTH_COMMAND',
    HCI_LE_READ_SUGGESTED_DEFAULT_DATA_LENGTH_COMMAND:                 'HCI_LE_READ_SUGGESTED_DEFAULT_DATA_LENGTH_COMMAND',
    HCI_LE_WRITE_SUGGESTED_DEFAULT_DATA_LENGTH_COMMAND:                'HCI_LE_WRITE_SUGGESTED_DEFAULT_DATA_LENGTH_COMMAND',
    HCI_LE_READ_LOCAL_P_256_PUBLIC_KEY_COMMAND:                        'HCI_LE_READ_LOCAL_P_256_PUBLIC_KEY_COMMAND',
    HCI_LE_GENERATE_DHKEY_COMMAND:                                     'HCI_LE_GENERATE_DHKEY_COMMAND',
    HCI_LE_ADD_DEVICE_TO_RESOLVING_LIST_COMMAND:                       'HCI_LE_ADD_DEVICE_TO_RESOLVING_LIST_COMMAND',
    HCI_LE_REMOVE_DEVICE_FROM_RESOLVING_LIST_COMMAND:                  'HCI_LE_REMOVE_DEVICE_FROM_RESOLVING_LIST_COMMAND',
    HCI_LE_CLEAR_RESOLVING_LIST_COMMAND:                               'HCI_LE_CLEAR_RESOLVING_LIST_COMMAND',
    HCI_LE_READ_RESOLVING_LIST_SIZE_COMMAND:                           'HCI_LE_READ_RESOLVING_LIST_SIZE_COMMAND',
    HCI_LE_READ_PEER_RESOLVABLE_ADDRESS_COMMAND:                       'HCI_LE_READ_PEER_RESOLVABLE_ADDRESS_COMMAND',
    HCI_LE_READ_LOCAL_RESOLVABLE_ADDRESS_COMMAND:                      'HCI_LE_READ_LOCAL_RESOLVABLE_ADDRESS_COMMAND',
    HCI_LE_SET_ADDRESS_RESOLUTION_ENABLE_COMMAND:                      'HCI_LE_SET_ADDRESS_RESOLUTION_ENABLE_COMMAND',
    HCI_LE_SET_RESOLVABLE_PRIVATE_ADDRESS_TIMEOUT_COMMAND:             'HCI_LE_SET_RESOLVABLE_PRIVATE_ADDRESS_TIMEOUT_COMMAND',
    HCI_LE_READ_MAXIMUM_DATA_LENGTH_COMMAND:                           'HCI_LE_READ_MAXIMUM_DATA_LENGTH_COMMAND',
    HCI_LE_READ_PHY_COMMAND:                                           'HCI_LE_READ_PHY_COMMAND',
    HCI_LE_SET_DEFAULT_PHY_COMMAND:                                    'HCI_LE_SET_DEFAULT_PHY_COMMAND',
    HCI_LE_SET_PHY_COMMAND:                                            'HCI_LE_SET_PHY_COMMAND',
    HCI_LE_ENHANCED_RECEIVER_TEST_COMMAND:                             'HCI_LE_ENHANCED_RECEIVER_TEST_COMMAND',
    HCI_LE_ENHANCED_TRANSMITTER_TEST_COMMAND:                          'HCI_LE_ENHANCED_TRANSMITTER_TEST_COMMAND',
    HCI_LE_SET_ADVERTISING_SET_RANDOM_ADDRESS_COMMAND:                 'HCI_LE_SET_ADVERTISING_SET_RANDOM_ADDRESS_COMMAND',
    HCI_LE_SET_EXTENDED_ADVERTISING_PARAMETERS_COMMAND:                'HCI_LE_SET_EXTENDED_ADVERTISING_PARAMETERS_COMMAND',
    HCI_LE_SET_EXTENDED_ADVERTISING_DATA_COMMAND:                      'HCI_LE_SET_EXTENDED_ADVERTISING_DATA_COMMAND',
    HCI_LE_SET_EXTENDED_SCAN_RESPONSE_DATA_COMMAND:                    'HCI_LE_SET_EXTENDED_SCAN_RESPONSE_DATA_COMMAND',
    HCI_LE_SET_EXTENDED_ADVERTISING_ENABLE_COMMAND:                    'HCI_LE_SET_EXTENDED_ADVERTISING_ENABLE_COMMAND',
    HCI_LE_READ_MAXIMUM_ADVERTISING_DATA_LENGTH_COMMAND:               'HCI_LE_READ_MAXIMUM_ADVERTISING_DATA_LENGTH_COMMAND',
    HCI_LE_READ_NUMBER_OF_SUPPORTED_ADVERETISING_SETS_COMMAND:         'HCI_LE_READ_NUMBER_OF_SUPPORTED_ADVERETISING_SETS_COMMAND',
    HCI_LE_REMOVE_ADVERTISING_SET_COMMAND:                             'HCI_LE_REMOVE_ADVERTISING_SET_COMMAND',
    HCI_LE_CLEAR_ADVERTISING_SETS_COMMAND:                             'HCI_LE_CLEAR_ADVERTISING_SETS_COMMAND',
    HCI_LE_SET_PERIODIC_ADVERTISING_PARAMETERS_COMMAND:                'HCI_LE_SET_PERIODIC_ADVERTISING_PARAMETERS_COMMAND',
    HCI_LE_SET_PERIODIC_ADVERTISING_DATA_COMMAND:                      'HCI_LE_SET_PERIODIC_ADVERTISING_DATA_COMMAND',
    HCI_LE_SET_PERIODIC_ADVERTISING_ENABLE_COMMAND:                    'HCI_LE_SET_PERIODIC_ADVERTISING_ENABLE_COMMAND',
    HCI_LE_SET_EXTENDED_SCAN_PARAMETERS_COMMAND:                       'HCI_LE_SET_EXTENDED_SCAN_PARAMETERS_COMMAND',
    HCI_LE_SET_EXTENDED_SCAN_ENABLE_COMMAND:                           'HCI_LE_SET_EXTENDED_SCAN_ENABLE_COMMAND',
    HCI_LE_SET_EXTENDED_CREATE_CONNECTION_COMMAND:                     'HCI_LE_SET_EXTENDED_CREATE_CONNECTION_COMMAND',
    HCI_LE_PERIODIC_ADVERTISING_CREATE_SYNC_COMMAND:                   'HCI_LE_PERIODIC_ADVERTISING_CREATE_SYNC_COMMAND',
    HCI_LE_PERIODIC_ADVERTISING_CREATE_SYNC_CANCEL_COMMAND:            'HCI_LE_PERIODIC_ADVERTISING_CREATE_SYNC_CANCEL_COMMAND',
    HCI_LE_PERIODIC_ADVERTISING_TERMINATE_SYNC_COMMAND:                'HCI_LE_PERIODIC_ADVERTISING_TERMINATE_SYNC_COMMAND',
    HCI_LE_ADD_DEVICE_TO_PERIODIC_ADVERTISER_LIST_COMMAND:             'HCI_LE_ADD_DEVICE_TO_PERIODIC_ADVERTISER_LIST_COMMAND',
    HCI_LE_REMOVE_DEVICE_FROM_PERIODIC_ADVERTISER_LIST_COMMAND:        'HCI_LE_REMOVE_DEVICE_FROM_PERIODIC_ADVERTISER_LIST_COMMAND',
    HCI_LE_CLEAR_PERIODIC_ADVERTISER_LIST_COMMAND:                     'HCI_LE_CLEAR_PERIODIC_ADVERTISER_LIST_COMMAND',
    HCI_LE_READ_PERIODIC_ADVERTISER_LIST_SIZE_COMMAND:                 'HCI_LE_READ_PERIODIC_ADVERTISER_LIST_SIZE_COMMAND',
    HCI_LE_READ_TRANSMIT_POWER_COMMAND:                                'HCI_LE_READ_TRANSMIT_POWER_COMMAND',
    HCI_LE_READ_RF_PATH_COMPENSATION_COMMAND:                          'HCI_LE_READ_RF_PATH_COMPENSATION_COMMAND',
    HCI_LE_WRITE_RF_PATH_COMPENSATION_COMMAND:                         'HCI_LE_WRITE_RF_PATH_COMPENSATION_COMMAND',
    HCI_LE_SET_PRIVACY_MODE_COMMAND:                                   'HCI_LE_SET_PRIVACY_MODE_COMMAND'
}


# HCI Error Codes
# See Bluetooth spec Vol 2, Part D - 1.3 LIST OF ERROR CODES
HCI_SUCCESS                                                        = 0x00
HCI_UNKNOWN_HCI_COMMAND_ERROR                                      = 0x01
HCI_UNKNOWN_CONNECTION_IDENTIFIER_ERROR                            = 0x02
HCI_HARDWARE_FAILURE_ERROR                                         = 0x03
HCI_PAGE_TIMEOUT_ERROR                                             = 0x04
HCI_AUTHENTICATION_FAILURE_ERROR                                   = 0x05
HCI_PIN_OR_KEY_MISSING_ERROR                                       = 0x06
HCI_MEMORY_CAPACITY_EXCEEDED_ERROR                                 = 0x07
HCI_CONNECTION_TIMEOUT_ERROR                                       = 0x08
HCI_CONNECTION_LIMIT_EXCEEDED_ERROR                                = 0x09
HCI_SYNCHRONOUS_CONNECTION_LIMIT_TO_A_DEVICE_EXCEEDED_ERROR        = 0x0A
HCI_CONNECTION_ALREADY_EXISTS_ERROR                                = 0x0B
HCI_COMMAND_DISALLOWED_ERROR                                       = 0x0C
HCI_CONNECTION_REJECTED_DUE_TO_LIMITED_RESOURCES_ERROR             = 0x0D
HCI_CONNECTION_REJECTED_DUE_TO_SECURITY_REASONS_ERROR              = 0x0E
HCI_CONNECTION_REJECTED_DUE_TO_UNACCEPTABLE_BD_ADDR_ERROR          = 0x0F
HCI_CONNECTION_ACCEPT_TIMEOUT_ERROR                                = 0x10
HCI_UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE_ERROR                   = 0x11
HCI_INVALID_HCI_COMMAND_PARAMETERS_ERROR                           = 0x12
HCI_REMOTE_USER_TERMINATED_CONNECTION_ERROR                        = 0x13
HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_DUE_TO_LOW_RESOURCES_ERROR = 0x14
HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_DUE_TO_POWER_OFF_ERROR     = 0x15
HCI_CONNECTION_TERMINATED_BY_LOCAL_HOST_ERROR                      = 0x16
HCI_UNACCEPTABLE_CONNECTION_PARAMETERS_ERROR                       = 0x3B
HCI_CONNECTION_FAILED_TO_BE_ESTABLISHED_ERROR                      = 0x3E
# TODO: more error codes

HCI_ERROR_NAMES = {
    HCI_SUCCESS:                                                        'HCI_SUCCESS',
    HCI_UNKNOWN_HCI_COMMAND_ERROR:                                      'HCI_UNKNOWN_HCI_COMMAND_ERROR',
    HCI_UNKNOWN_CONNECTION_IDENTIFIER_ERROR:                            'HCI_UNKNOWN_CONNECTION_IDENTIFIER_ERROR',
    HCI_HARDWARE_FAILURE_ERROR:                                         'HCI_HARDWARE_FAILURE_ERROR',
    HCI_PAGE_TIMEOUT_ERROR:                                             'HCI_PAGE_TIMEOUT_ERROR',
    HCI_AUTHENTICATION_FAILURE_ERROR:                                   'HCI_AUTHENTICATION_FAILURE_ERROR',
    HCI_PIN_OR_KEY_MISSING_ERROR:                                       'HCI_PIN_OR_KEY_MISSING_ERROR',
    HCI_MEMORY_CAPACITY_EXCEEDED_ERROR:                                 'HCI_MEMORY_CAPACITY_EXCEEDED_ERROR',
    HCI_CONNECTION_TIMEOUT_ERROR:                                       'HCI_CONNECTION_TIMEOUT_ERROR',
    HCI_CONNECTION_LIMIT_EXCEEDED_ERROR:                                'HCI_CONNECTION_LIMIT_EXCEEDED_ERROR',
    HCI_SYNCHRONOUS_CONNECTION_LIMIT_TO_A_DEVICE_EXCEEDED_ERROR:        'HCI_SYNCHRONOUS_CONNECTION_LIMIT_TO_A_DEVICE_EXCEEDED_ERROR',
    HCI_CONNECTION_ALREADY_EXISTS_ERROR:                                'HCI_CONNECTION_ALREADY_EXISTS_ERROR',
    HCI_COMMAND_DISALLOWED_ERROR:                                       'HCI_COMMAND_DISALLOWED_ERROR',
    HCI_CONNECTION_REJECTED_DUE_TO_LIMITED_RESOURCES_ERROR:             'HCI_CONNECTION_REJECTED_DUE_TO_LIMITED_RESOURCES_ERROR',
    HCI_CONNECTION_REJECTED_DUE_TO_SECURITY_REASONS_ERROR:              'HCI_CONNECTION_REJECTED_DUE_TO_SECURITY_REASONS_ERROR',
    HCI_CONNECTION_REJECTED_DUE_TO_UNACCEPTABLE_BD_ADDR_ERROR:          'HCI_CONNECTION_REJECTED_DUE_TO_UNACCEPTABLE_BD_ADDR_ERROR',
    HCI_CONNECTION_ACCEPT_TIMEOUT_ERROR:                                'HCI_CONNECTION_ACCEPT_TIMEOUT_ERROR',
    HCI_UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE_ERROR:                   'HCI_UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE_ERROR',
    HCI_INVALID_HCI_COMMAND_PARAMETERS_ERROR:                           'HCI_INVALID_HCI_COMMAND_PARAMETERS_ERROR',
    HCI_REMOTE_USER_TERMINATED_CONNECTION_ERROR:                        'HCI_REMOTE_USER_TERMINATED_CONNECTION_ERROR',
    HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_DUE_TO_LOW_RESOURCES_ERROR: 'HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_DUE_TO_LOW_RESOURCES_ERROR',
    HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_DUE_TO_POWER_OFF_ERROR:     'HCI_REMOTE_DEVICE_TERMINATED_CONNECTION_DUE_TO_POWER_OFF_ERROR',
    HCI_CONNECTION_TERMINATED_BY_LOCAL_HOST_ERROR:                      'HCI_CONNECTION_TERMINATED_BY_LOCAL_HOST_ERROR',
    HCI_UNACCEPTABLE_CONNECTION_PARAMETERS_ERROR:                       'HCI_UNACCEPTABLE_CONNECTION_PARAMETERS_ERROR',
    HCI_CONNECTION_FAILED_TO_BE_ESTABLISHED_ERROR:                      'HCI_CONNECTION_FAILED_TO_BE_ESTABLISHED_ERROR'
}

# Command Status codes
HCI_COMMAND_STATUS_PENDING = 0

# LE Event Masks
LE_CONNECTION_COMPLETE_EVENT_MASK                   = (1 << 0)
LE_ADVERTISING_REPORT_EVENT_MASK                    = (1 << 1)
LE_CONNECTION_UPDATE_COMPLETE_EVENT_MASK            = (1 << 2)
LE_READ_REMOTE_FEATURES_COMPLETE_EVENT_MASK         = (1 << 3)
LE_LONG_TERM_KEY_REQUEST_EVENT_MASK                 = (1 << 4)
LE_REMOTE_CONNECTION_PARAMETER_REQUEST_EVENT_MASK   = (1 << 5)
LE_DATA_LENGTH_CHANGE_EVENT_MASK                    = (1 << 6)
LE_READ_LOCAL_P_256_PUBLIC_KEY_COMPLETE_EVENT_MASK  = (1 << 7)
LE_GENERATE_DHKEY_COMPLETE_EVENT_MASK               = (1 << 8)
LE_ENHANCED_CONNECTION_COMPLETE_EVENT_MASK          = (1 << 9)
LE_DIRECTED_ADVERTISING_REPORT_EVENT_MASK           = (1 << 10)
LE_PHY_UPDATE_COMPLETE_EVENT_MASK                   = (1 << 11)
LE_EXTENDED_ADVERTISING_REPORT_EVENT_MASK           = (1 << 12)
LE_PERIODIC_ADVERTISING_SYNC_ESTABLISHED_EVENT_MASK = (1 << 13)
LE_PERIODIC_ADVERTISING_REPORT_EVENT_MASK           = (1 << 14)
LE_PERIODIC_ADVERTISING_SYNC_LOST_EVENT_MASK        = (1 << 15)
LE_EXTENDED_SCAN_TIMEOUT_EVENT_MASK                 = (1 << 16)
LE_EXTENDED_ADVERTISING_SET_TERMINATED_EVENT_MASK   = (1 << 17)
LE_SCAN_REQUEST_RECEIVED_EVENT_MASK                 = (1 << 18)
LE_CHANNEL_SELECTION_ALGORITHM_EVENT_MASK           = (1 << 19)

# ACL
HCI_ACL_PB_FIRST_NON_FLUSHABLE = 0
HCI_ACL_PB_CONTINUATION        = 1
HCI_ACL_PB_FIRST_FLUSHABLE     = 2
HCI_ACK_PB_COMPLETE_L2CAP      = 3

# Roles
HCI_CENTRAL_ROLE    = 0
HCI_PERIPHERAL_ROLE = 1

HCI_ROLE_NAMES = {
    HCI_CENTRAL_ROLE:    'CENTRAL',
    HCI_PERIPHERAL_ROLE: 'PERIPHERAL'
}

# LE PHY Types
HCI_LE_1M_PHY    = 1
HCI_LE_2M_PHY    = 2
HCI_LE_CODED_PHY = 3

HCI_LE_PHY_NAMES = {
    HCI_LE_1M_PHY:    'LE 1M',
    HCI_LE_2M_PHY:    'L2 2M',
    HCI_LE_CODED_PHY: 'LE Coded'
}

# Connection Parameters
HCI_CONNECTION_INTERVAL_MS_PER_UNIT = 1.25
HCI_CONNECTION_LATENCY_MS_PER_UNIT  = 1.25
HCI_SUPERVISION_TIMEOUT_MS_PER_UNIT = 10

# Inquiry LAP
HCI_LIMITED_DEDICATED_INQUIRY_LAP = 0x9E8B00
HCI_GENERAL_INQUIRY_LAP           = 0x9E8B33
HCI_INQUIRY_LAP_NAMES = {
    HCI_LIMITED_DEDICATED_INQUIRY_LAP: 'Limited Dedicated Inquiry',
    HCI_GENERAL_INQUIRY_LAP:           'General Inquiry'
}

# Inquiry Mode
HCI_STANDARD_INQUIRY_MODE  = 0x00
HCI_INQUIRY_WITH_RSSI_MODE = 0x01
HCI_EXTENDED_INQUIRY_MODE  = 0x02

# Page Scan Repetition Mode
HCI_R0_PAGE_SCAN_REPETITION_MODE = 0x00
HCI_R1_PAGE_SCAN_REPETITION_MODE = 0x01
HCI_R2_PAGE_SCAN_REPETITION_MODE = 0x02

# IO Capability
HCI_DISPLAY_ONLY_IO_CAPABILITY       = 0x00
HCI_DISPLAY_YES_NO_IO_CAPABILITY     = 0x01
HCI_KEYBOARD_ONLY_IO_CAPABILITY      = 0x02
HCI_NO_INPUT_NO_OUTPUT_IO_CAPABILITY = 0x03

HCI_IO_CAPABILITY_NAMES = {
    HCI_DISPLAY_ONLY_IO_CAPABILITY:       'HCI_DISPLAY_ONLY_IO_CAPABILITY',
    HCI_DISPLAY_YES_NO_IO_CAPABILITY:     'HCI_DISPLAY_YES_NO_IO_CAPABILITY',
    HCI_KEYBOARD_ONLY_IO_CAPABILITY:      'HCI_KEYBOARD_ONLY_IO_CAPABILITY',
    HCI_NO_INPUT_NO_OUTPUT_IO_CAPABILITY: 'HCI_NO_INPUT_NO_OUTPUT_IO_CAPABILITY'
}

# Authentication Requirements
HCI_MITM_NOT_REQUIRED_NO_BONDING_AUTHENTICATION_REQUIREMENTS        = 0x00
HCI_MITM_REQUIRED_NO_BONDING_AUTHENTICATION_REQUIREMENTS            = 0x01
HCI_MITM_NOT_REQUIRED_DEDICATED_BONDING_AUTHENTICATION_REQUIREMENTS = 0x02
HCI_MITM_REQUIRED_DEDICATED_BONDING_AUTHENTICATION_REQUIREMENTS     = 0x03
HCI_MITM_NOT_REQUIRED_GENERAL_BONDING_AUTHENTICATION_REQUIREMENTS   = 0x04
HCI_MITM_REQUIRED_GENERAL_BONDING_AUTHENTICATION_REQUIREMENTS       = 0x05

HCI_AUTHENTICATION_REQUIREMENTS_NAMES = {
    HCI_MITM_NOT_REQUIRED_NO_BONDING_AUTHENTICATION_REQUIREMENTS:        'HCI_MITM_NOT_REQUIRED_NO_BONDING_AUTHENTICATION_REQUIREMENTS',
    HCI_MITM_REQUIRED_NO_BONDING_AUTHENTICATION_REQUIREMENTS:            'HCI_MITM_REQUIRED_NO_BONDING_AUTHENTICATION_REQUIREMENTS',
    HCI_MITM_NOT_REQUIRED_DEDICATED_BONDING_AUTHENTICATION_REQUIREMENTS: 'HCI_MITM_NOT_REQUIRED_DEDICATED_BONDING_AUTHENTICATION_REQUIREMENTS',
    HCI_MITM_REQUIRED_DEDICATED_BONDING_AUTHENTICATION_REQUIREMENTS:     'HCI_MITM_REQUIRED_DEDICATED_BONDING_AUTHENTICATION_REQUIREMENTS',
    HCI_MITM_NOT_REQUIRED_GENERAL_BONDING_AUTHENTICATION_REQUIREMENTS:   'HCI_MITM_NOT_REQUIRED_GENERAL_BONDING_AUTHENTICATION_REQUIREMENTS',
    HCI_MITM_REQUIRED_GENERAL_BONDING_AUTHENTICATION_REQUIREMENTS:       'HCI_MITM_REQUIRED_GENERAL_BONDING_AUTHENTICATION_REQUIREMENTS'
}

# Link Key Types
HCI_COMBINATION_KEY_TYPE                                      = 0X00
HCI_LOCAL_UNIT_KEY_TYPE                                       = 0X01
HCI_REMOTE_UNIT_KEY_TYPE                                      = 0X02
HCI_DEBUG_COMBINATION_KEY_TYPE                                = 0X03
HCI_UNAUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_192_TYPE = 0X04
HCI_AUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_192_TYPE   = 0X05
HCI_CHANGED_COMBINATION_KEY_TYPE                              = 0X06
HCI_UNAUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_256_TYPE = 0X07
HCI_AUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_256_TYPE   = 0X08

HCI_LINK_TYPE_NAMES = {
    HCI_COMBINATION_KEY_TYPE:                                      'HCI_COMBINATION_KEY_TYPE',
    HCI_LOCAL_UNIT_KEY_TYPE:                                       'HCI_LOCAL_UNIT_KEY_TYPE',
    HCI_REMOTE_UNIT_KEY_TYPE:                                      'HCI_REMOTE_UNIT_KEY_TYPE',
    HCI_DEBUG_COMBINATION_KEY_TYPE:                                'HCI_DEBUG_COMBINATION_KEY_TYPE',
    HCI_UNAUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_192_TYPE: 'HCI_UNAUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_192_TYPE',
    HCI_AUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_192_TYPE:   'HCI_AUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_192_TYPE',
    HCI_CHANGED_COMBINATION_KEY_TYPE:                              'HCI_CHANGED_COMBINATION_KEY_TYPE',
    HCI_UNAUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_256_TYPE: 'HCI_UNAUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_256_TYPE',
    HCI_AUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_256_TYPE:   'HCI_AUTHENTICATED_COMBINATION_KEY_GENERATED_FROM_P_256_TYPE'
}

# Address types
HCI_PUBLIC_DEVICE_ADDRESS_TYPE   = 0x00
HCI_RANDOM_DEVICE_ADDRESS_TYPE   = 0x01
HCI_PUBLIC_IDENTITY_ADDRESS_TYPE = 0x02
HCI_RANDOM_IDENTITY_ADDRESS_TYPE = 0x03

# -----------------------------------------------------------------------------
STATUS_SPEC = {'size': 1, 'mapper': lambda x: HCI_Constant.status_name(x)}


# -----------------------------------------------------------------------------
class HCI_Constant:
    @staticmethod
    def status_name(status):
        return HCI_ERROR_NAMES.get(status, f'0x{status:02X}')

    @staticmethod
    def error_name(status):
        return HCI_ERROR_NAMES.get(status, f'0x{status:02X}')

    @staticmethod
    def role_name(role):
        return HCI_ROLE_NAMES.get(role, str(role))

    @staticmethod
    def le_phy_name(phy):
        return HCI_LE_PHY_NAMES.get(phy, str(phy))

    @staticmethod
    def inquiry_lap_name(lap):
        return HCI_INQUIRY_LAP_NAMES.get(lap, f'0x{lap:06X}')

    @staticmethod
    def io_capability_name(io_capability):
        return HCI_IO_CAPABILITY_NAMES.get(io_capability, f'0x{io_capability:02X}')

    @staticmethod
    def authentication_requirements_name(authentication_requirements):
        return HCI_AUTHENTICATION_REQUIREMENTS_NAMES.get(
            authentication_requirements,
            f'0x{authentication_requirements:02X}'
        )

    @staticmethod
    def link_key_type_name(link_key_type):
        return HCI_LINK_TYPE_NAMES.get(link_key_type, f'0x{link_key_type:02X}')


# -----------------------------------------------------------------------------
class HCI_Error(ProtocolError):
    def __init__(self, error_code):
        super().__init__(error_code, 'hci', HCI_Constant.error_name(error_code))


# -----------------------------------------------------------------------------
# Generic HCI object
# -----------------------------------------------------------------------------
class HCI_Object:
    @staticmethod
    def init_from_fields(object, fields, values):
        if type(values) is dict:
            for field_name, _ in fields:
                setattr(object, field_name, values[field_name])
        else:
            for field_name, field_value in zip(fields, values):
                setattr(object, field_name, field_value)

    @staticmethod
    def init_from_bytes(object, data, offset, fields):
        parsed = HCI_Object.dict_from_bytes(data, offset, fields)
        HCI_Object.init_from_fields(object, parsed.keys(), parsed.values())

    @staticmethod
    def dict_from_bytes(data, offset, fields):
        result = collections.OrderedDict()
        for (field_name, field_type) in fields:
            # The field_type may be a dictionnary with a mapper, parser, and/or size
            if type(field_type) is dict:
                if 'size' in field_type:
                    field_type = field_type['size']
                elif 'parser' in field_type:
                    field_type = field_type['parser']

            # Parse the field
            if field_type == '*':
                # The rest of the bytes
                field_value = data[offset:]
                offset += len(field_value)
            elif field_type == 1:
                # 8-bit unsigned
                field_value = data[offset]
                offset += 1
            elif field_type == -1:
                # 8-bit signed
                field_value = struct.unpack_from('b', data, offset)[0]
                offset += 1
            elif field_type == 2:
                # 16-bit unsigned
                field_value = struct.unpack_from('<H', data, offset)[0]
                offset += 2
            elif field_type == '>2':
                # 16-bit unsigned big-endian
                field_value = struct.unpack_from('>H', data, offset)[0]
                offset += 2
            elif field_type == -2:
                # 16-bit signed
                field_value = struct.unpack_from('<h', data, offset)[0]
                offset += 1
            elif field_type == 3:
                # 24-bit unsigned
                padded = data[offset:offset + 3] + bytes([0])
                field_value = struct.unpack('<I', padded)[0]
                offset += 3
            elif field_type == 4:
                # 32-bit unsigned
                field_value = struct.unpack_from('<I', data, offset)[0]
                offset += 4
            elif field_type == '>4':
                # 32-bit unsigned big-endian
                field_value = struct.unpack_from('>I', data, offset)[0]
                offset += 4
            elif type(field_type) is int and field_type > 4 and field_type <= 256:
                # Byte array (from 5 up to 256 bytes)
                field_value = data[offset:offset + field_type]
                offset += field_type
            elif callable(field_type):
                offset, field_value = field_type(data, offset)
            else:
                raise ValueError(f'unknown field type {field_type}')

            result[field_name] = field_value

        return result

    @staticmethod
    def dict_to_bytes(object, fields):
        result = bytearray()
        for (field_name, field_type) in fields:
            # The field_type may be a dictionnary with a mapper, parser, serializer, and/or size
            serializer = None
            if type(field_type) is dict:
                if 'serializer' in field_type:
                    serializer = field_type['serializer']
                if 'size' in field_type:
                    field_type = field_type['size']

            # Serialize the field
            field_value = object[field_name]
            if serializer:
                field_bytes = serializer(field_value)
            elif field_type == 1:
                # 8-bit unsigned
                field_bytes = bytes([field_value])
            elif field_type == -1:
                # 8-bit signed
                field_bytes = struct.pack('b', field_value)
            elif field_type == 2:
                # 16-bit unsigned
                field_bytes = struct.pack('<H', field_value)
            elif field_type == '>2':
                # 16-bit unsigned big-endian
                field_bytes = struct.pack('>H', field_value)
            elif field_type == -2:
                # 16-bit signed
                field_bytes = struct.pack('<h', field_value)
            elif field_type == 3:
                # 24-bit unsigned
                field_bytes = struct.pack('<I', field_value)[0:3]
            elif field_type == 4:
                # 32-bit unsigned
                field_bytes = struct.pack('<I', field_value)
            elif field_type == '>4':
                # 32-bit unsigned big-endian
                field_bytes = struct.pack('>I', field_value)
            elif field_type == '*':
                if type(field_value) is int:
                    if field_value >= 0 and field_value <= 255:
                        field_bytes = bytes([field_value])
                    else:
                        raise ValueError('value too large for *-typed field')
                else:
                    field_bytes = bytes(field_value)
            elif type(field_value) is bytes or type(field_value) is bytearray or hasattr(field_value, 'to_bytes'):
                field_bytes = bytes(field_value)
                if type(field_type) is int and field_type > 4 and field_type <= 256:
                    # Truncate or Pad with zeros if the field is too long or too short
                    if len(field_bytes) < field_type:
                        field_bytes += bytes(field_type - len(field_bytes))
                    elif len(field_bytes) > field_type:
                        field_bytes = field_bytes[:field_type]
            else:
                raise ValueError(f"don't know how to serialize type {type(field_value)}")

            result += field_bytes

        return bytes(result)

    @staticmethod
    def from_bytes(data, offset, fields):
        return HCI_Object(fields, **HCI_Object.dict_from_bytes(data, offset, fields))

    def to_bytes(self):
        return HCI_Object.dict_to_bytes(self.__dict__, self.fields)

    @staticmethod
    def parse_length_prefixed_bytes(data, offset):
        length = data[offset]
        return offset + 1 + length, data[offset + 1:offset + 1 + length]

    @staticmethod
    def serialize_length_prefixed_bytes(data, padded_size=0):
        prefixed_size = 1 + len(data)
        padding = bytes(padded_size - prefixed_size) if prefixed_size < padded_size else b''
        return bytes([len(data)]) + data + padding

    @staticmethod
    def format_field_value(value, indentation):
        if type(value) is bytes:
            return value.hex()
        elif isinstance(value, HCI_Object):
            return '\n' + value.to_string(indentation)
        else:
            return str(value)

    @staticmethod
    def format_fields(object, keys, indentation='', value_mappers={}):
        if not keys:
            return ''

        # Measure the widest field name
        max_field_name_length = max([len(key[0] if type(key) is tuple else key) for key in keys])

        # Build array of formatted key:value pairs
        fields = []
        for key in keys:
            value_mapper = None
            if type(key) is tuple:
                # The key has an associated specifier
                key, specifier = key

                # Get the value mapper from the specifier
                if type(specifier) is dict:
                    value_mapper = specifier.get('mapper')

            # Get the value for the field
            value = object[key]

            # Map the value if needed
            value_mapper = value_mappers.get(key, value_mapper)
            if value_mapper is not None:
                value = value_mapper(value)

            # Get the string representation of the value
            value_str = HCI_Object.format_field_value(value, indentation = indentation + '  ')

            # Add the field to the formatted result
            key_str = color(f'{key + ":":{1 + max_field_name_length}}', 'cyan')
            fields.append(f'{indentation}{key_str} {value_str}')

        return '\n'.join(fields)

    def __bytes__(self):
        return self.to_bytes()

    def __init__(self, fields, **kwargs):
        self.fields = fields
        self.init_from_fields(self, fields, kwargs)

    def to_string(self, indentation='', value_mappers={}):
        return HCI_Object.format_fields(self.__dict__, self.fields, indentation, value_mappers)

    def __str__(self):
        return self.to_string()


# -----------------------------------------------------------------------------
# Bluetooth Address
# -----------------------------------------------------------------------------
class Address:
    '''
    Bluetooth Address (see Bluetooth spec Vol 6, Part B - 1.3 DEVICE ADDRESS)
    NOTE: the address bytes are stored in little-endian byte order here, so
    address[0] is the LSB of the address, address[5] is the MSB.
    '''

    PUBLIC_DEVICE_ADDRESS   = 0x00
    RANDOM_DEVICE_ADDRESS   = 0x01
    PUBLIC_IDENTITY_ADDRESS = 0x02
    RANDOM_IDENTITY_ADDRESS = 0x03

    ADDRESS_TYPE_NAMES = {
        PUBLIC_DEVICE_ADDRESS:   'PUBLIC_DEVICE_ADDRESS',
        RANDOM_DEVICE_ADDRESS:   'RANDOM_DEVICE_ADDRESS',
        PUBLIC_IDENTITY_ADDRESS: 'PUBLIC_IDENTITY_ADDRESS',
        RANDOM_IDENTITY_ADDRESS: 'RANDOM_IDENTITY_ADDRESS'
    }

    ADDRESS_TYPE_SPEC = {'size': 1, 'mapper': lambda x: Address.address_type_name(x)}

    @staticmethod
    def address_type_name(address_type):
        return name_or_number(Address.ADDRESS_TYPE_NAMES, address_type)

    @staticmethod
    def parse_address(data, offset):
        # Fix the type to a default value. This is used for parsing type-less Classic addresses
        return Address.parse_address_with_type(data, offset, Address.PUBLIC_DEVICE_ADDRESS)

    @staticmethod
    def parse_address_with_type(data, offset, address_type):
        return offset + 6, Address(data[offset:offset + 6], address_type)

    @staticmethod
    def parse_address_preceded_by_type(data, offset):
        address_type = data[offset - 1]
        return Address.parse_address_with_type(data, offset, address_type)

    def __init__(self, address, address_type = RANDOM_DEVICE_ADDRESS):
        '''
        Initialize an instance. `address` may be a byte array in little-endian
        format, or a hex string in big-endian format (with optional ':'
        separators between the bytes).
        If the address is a string suffixed with '/P', `address_type` is ignored and the type
        is set to PUBLIC_DEVICE_ADDRESS.
        '''
        if type(address) is bytes:
            self.address_bytes = address
        else:
            # Check if there's a '/P' type specifier
            if address.endswith('P'):
                address_type = Address.PUBLIC_DEVICE_ADDRESS
                address = address[:-2]

            if len(address) == 12 + 5:
                # Form with ':' separators
                address = address.replace(':', '')
            self.address_bytes = bytes(reversed(bytes.fromhex(address)))

        if len(self.address_bytes) != 6:
            raise ValueError('invalid address length')

        self.address_type = address_type

    @property
    def is_public(self):
        return self.address_type == self.PUBLIC_DEVICE_ADDRESS or self.address_type == self.PUBLIC_IDENTITY_ADDRESS

    @property
    def is_random(self):
        return not self.is_public

    @property
    def is_resolved(self):
        return self.address_type == self.PUBLIC_IDENTITY_ADDRESS or self.address_type == self.RANDOM_IDENTITY_ADDRESS

    @property
    def is_resolvable(self):
        return self.address_type == self.RANDOM_DEVICE_ADDRESS and (self.address_bytes[5] >> 6 == 1)

    @property
    def is_static(self):
        return self.is_random and (self.address_bytes[5] >> 6 == 3)

    def to_bytes(self):
        return self.address_bytes

    def __bytes__(self):
        return self.to_bytes()

    def __hash__(self):
        return hash(self.address_bytes)

    def __eq__(self, other):
        return self.address_bytes == other.address_bytes and self.is_public == other.is_public

    def __str__(self):
        '''
        String representation of the address, MSB first
        '''
        return ':'.join([f'{x:02X}' for x in reversed(self.address_bytes)])


# -----------------------------------------------------------------------------
class HCI_Packet:
    '''
    Abstract Base class for HCI packets
    '''

    @staticmethod
    def from_bytes(packet):
        packet_type = packet[0]
        if packet_type == HCI_COMMAND_PACKET:
            return HCI_Command.from_bytes(packet)
        elif packet_type == HCI_ACL_DATA_PACKET:
            return HCI_AclDataPacket.from_bytes(packet)
        elif packet_type == HCI_EVENT_PACKET:
            return HCI_Event.from_bytes(packet)
        else:
            return HCI_CustomPacket(packet)

    def __init__(self, name):
        self.name = name

    def __repr__(self) -> str:
        return self.name


# -----------------------------------------------------------------------------
class HCI_CustomPacket(HCI_Packet):
    def __init__(self, payload):
        super().__init__('HCI_CUSTOM_PACKET')
        self.hci_packet_type = payload[0]
        self.payload         = payload


# -----------------------------------------------------------------------------
class HCI_Command(HCI_Packet):
    '''
    See Bluetooth spec @ Vol 2, Part E - 5.4.1 HCI Command Packet
    '''
    hci_packet_type = HCI_COMMAND_PACKET
    command_classes = {}

    @staticmethod
    def command(fields=[], return_parameters_fields=[]):
        '''
        Decorator used to declare and register subclasses
        '''

        def inner(cls):
            cls.name = cls.__name__.upper()
            cls.op_code = key_with_value(HCI_COMMAND_NAMES, cls.name)
            if cls.op_code is None:
                raise KeyError('command not found in HCI_COMMAND_NAMES')
            cls.fields = fields
            cls.return_parameters_fields = return_parameters_fields

            # Patch the __init__ method to fix the op_code
            def init(self, parameters=None, **kwargs):
                return HCI_Command.__init__(self, cls.op_code, parameters, **kwargs)
            cls.__init__ = init

            # Register a factory for this class
            HCI_Command.command_classes[cls.op_code] = cls

            return cls

        return inner

    @staticmethod
    def from_bytes(packet):
        op_code, length = struct.unpack_from('<HB', packet, 1)
        parameters = packet[4:]
        if len(parameters) != length:
            raise ValueError('invalid packet length')

        # Look for a registered class
        cls = HCI_Command.command_classes.get(op_code)
        if cls is None:
            # No class registered, just use a generic instance
            return HCI_Command(op_code, parameters)

        # Create a new instance
        self = cls.__new__(cls)
        HCI_Command.__init__(self, op_code, parameters)
        if fields := getattr(self, 'fields', None):
            HCI_Object.init_from_bytes(self, parameters, 0, fields)
        return self

    @staticmethod
    def command_name(op_code):
        name = HCI_COMMAND_NAMES.get(op_code)
        if name is not None:
            return name
        return f'[OGF=0x{op_code >> 10:02x}, OCF=0x{op_code & 0x3FF:04x}]'

    @classmethod
    def create_return_parameters(cls, **kwargs):
        return HCI_Object(cls.return_parameters_fields, **kwargs)

    def __init__(self, op_code, parameters=None, **kwargs):
        super().__init__(HCI_Command.command_name(op_code))
        if (fields := getattr(self, 'fields', None)) and kwargs:
            HCI_Object.init_from_fields(self, fields, kwargs)
            if parameters is None:
                parameters = HCI_Object.dict_to_bytes(kwargs, fields)
        self.op_code    = op_code
        self.parameters = parameters

    def to_bytes(self):
        parameters = b'' if self.parameters is None else self.parameters
        return struct.pack('<BHB', HCI_COMMAND_PACKET, self.op_code, len(parameters)) + parameters

    def __bytes__(self):
        return self.to_bytes()

    def __str__(self):
        result = color(self.name, 'green')
        if fields := getattr(self, 'fields', None):
            result += ':\n' + HCI_Object.format_fields(self.__dict__, fields, '  ')
        else:
            if self.parameters:
                result += f': {self.parameters.hex()}'
        return result


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('lap',            {'size': 3, 'mapper': HCI_Constant.inquiry_lap_name}),
    ('inquiry_length', 1),
    ('num_responses',  1)
])
class HCI_Inquiry_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.1 Inquiry Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_Inquiry_Cancel_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.2 Inquiry Cancel Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('bd_addr',                   Address.parse_address),
    ('packet_type',               2),
    ('page_scan_repetition_mode', 1),
    ('reserved',                  1),
    ('clock_offset',              2),
    ('allow_role_switch',         1)
])
class HCI_Create_Connection_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.5 Create Connection Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2),
    ('reason',            {'size': 1, 'mapper': HCI_Constant.error_name})
])
class HCI_Disconnect_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.6 Disconnect Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('bd_addr', Address.parse_address),
    ('role',    1)
])
class HCI_Accept_Connection_Request_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.8 Accept Connection Request Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('bd_addr',  Address.parse_address),
    ('link_key', 16)
])
class HCI_Link_Key_Request_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.10 Link Key Request Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr', Address.parse_address)
    ],
    return_parameters_fields=[
        ('status',  STATUS_SPEC),
        ('bd_addr', Address.parse_address)
    ]
)
class HCI_Link_Key_Request_Negative_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.11 Link Key Request Negative Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr', Address.parse_address)
    ],
    return_parameters_fields=[
        ('status',  STATUS_SPEC),
        ('bd_addr', Address.parse_address)
    ]
)
class HCI_PIN_Code_Request_Negative_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.13 PIN Code Request Negative Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2),
    ('packet_type',       2)
])
class HCI_Change_Connection_Packet_Type_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.14 Change Connection Packet Type Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2)
])
class HCI_Authentication_Requested_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.15 Authentication Requested Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2),
    ('encryption_enable', 1)
])
class HCI_Set_Connection_Encryption_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.16 Set Connection Encryption Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('bd_addr',                   Address.parse_address),
    ('page_scan_repetition_mode', 1),
    ('reserved',                  1),
    ('clock_offset',              2)
])
class HCI_Remote_Name_Request_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.19 Remote Name Request Command
    '''
    R0 = 0x00
    R1 = 0x01
    R2 = 0x02


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2)
])
class HCI_Read_Remote_Supported_Features_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.21 Read Remote Supported Features Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2),
    ('page_number',       1)
])
class HCI_Read_Remote_Extended_Features_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.22 Read Remote Extended Features Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2)
])
class HCI_Read_Remote_Version_Information_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.23 Read Remote Version Information Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2)
])
class HCI_Read_Clock_Offset_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.23 Read Clock Offset Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr',                     Address.parse_address),
        ('io_capability',               {'size': 1, 'mapper': HCI_Constant.io_capability_name}),
        ('oob_data_present',            1),
        ('authentication_requirements', {'size': 1, 'mapper': HCI_Constant.authentication_requirements_name})
    ],
    return_parameters_fields=[
        ('status',  STATUS_SPEC),
        ('bd_addr', Address.parse_address)
    ]
)
class HCI_IO_Capability_Request_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.29 IO Capability Request Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr', Address.parse_address)
    ],
    return_parameters_fields=[
        ('status',  STATUS_SPEC),
        ('bd_addr', Address.parse_address)
    ]
)
class HCI_User_Confirmation_Request_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.30 User Confirmation Request Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr', Address.parse_address)
    ],
    return_parameters_fields=[
        ('status',  STATUS_SPEC),
        ('bd_addr', Address.parse_address)
    ]
)
class HCI_User_Confirmation_Request_Negative_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.31 User Confirmation Request Negative Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr', Address.parse_address),
        ('numeric_value', 4)
    ],
    return_parameters_fields=[
        ('status',  STATUS_SPEC),
        ('bd_addr', Address.parse_address)
    ]
)
class HCI_User_Passkey_Request_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.32 User Passkey Request Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr', Address.parse_address)
    ],
    return_parameters_fields=[
        ('status',  STATUS_SPEC),
        ('bd_addr', Address.parse_address)
    ]
)
class HCI_User_Passkey_Request_Negative_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.33 User Passkey Request Negative Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle',                      2),
    ('transmit_bandwidth',                     4),
    ('receive_bandwidth',                      4),
    ('transmit_coding_format',                 5),
    ('receive_coding_format',                  5),
    ('transmit_codec_frame_size',              2),
    ('receive_codec_frame_size',               2),
    ('input_bandwidth',                        4),
    ('output_bandwidth',                       4),
    ('input_coding_format',                    5),
    ('output_coding_format',                   5),
    ('input_coded_data_size',                  2),
    ('output_coded_data_size',                 2),
    ('input_pcm_data_format',                  1),
    ('output_pcm_data_format',                 1),
    ('input_pcm_sample_payload_msb_position',  1),
    ('output_pcm_sample_payload_msb_position', 1),
    ('input_data_path',                        1),
    ('output_data_path',                       1),
    ('input_transport_unit_size',              1),
    ('output_transport_unit_size',             1),
    ('max_latency',                            2),
    ('packet_type',                            2),
    ('retransmission_effort',                  1)
])
class HCI_Enhanced_Setup_Synchronous_Connection_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.1.45 Enhanced Setup Synchronous Connection Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle',  2),
    ('sniff_max_interval', 2),
    ('sniff_min_interval', 2),
    ('sniff_attempt',      2),
    ('sniff_timeout',      2)
])
class HCI_Sniff_Mode_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.2.2 Sniff Mode Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2)
])
class HCI_Exit_Sniff_Mode_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.2.3 Exit Sniff Mode Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('bd_addr', Address.parse_address),
    ('role',    {'size': 1, 'mapper': HCI_Constant.role_name})
])
class HCI_Switch_Role_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.2.8 Switch Role Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle',    2),
    ('link_policy_settings', 2)
])
class HCI_Write_Link_Policy_Settings_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.2.10 Write Link Policy Settings Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('default_link_policy_settings', 2)
])
class HCI_Write_Default_Link_Policy_Settings_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.2.12 Write Default Link Policy Settings Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle',      2),
    ('maximum_latency',        2),
    ('minimum_remote_timeout', 2),
    ('minimum_local_timeout',  2)
])
class HCI_Sniff_Subrating_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.2.14 Sniff Subrating Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('event_mask', 8)
])
class HCI_Set_Event_Mask_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.1 Set Event Mask Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_Reset_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.2 Reset Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('filter_type',       1),
    ('filter_condition', '*'),
])
class HCI_Set_Event_Filter_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.3 Set Event Filter Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr',       Address.parse_address),
        ('read_all_flag', 1)
    ],
    return_parameters_fields=[
        ('status',        STATUS_SPEC),
        ('max_num_keys',  2),
        ('num_keys_read', 2)
    ]
)
class HCI_Read_Stored_Link_Key_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.8 Read Stored Link Key Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('bd_addr',         Address.parse_address),
        ('delete_all_flag', 1)
    ],
    return_parameters_fields=[
        ('status',           STATUS_SPEC),
        ('num_keys_deleted', 2)
    ]
)
class HCI_Delete_Stored_Link_Key_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.10 Delete Stored Link Key Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('local_name', {'size': 248, 'mapper': map_null_terminated_utf8_string})
])
class HCI_Write_Local_Name_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.11 Write Local Name Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',     STATUS_SPEC),
    ('local_name', {'size': 248, 'mapper': map_null_terminated_utf8_string})
])
class HCI_Read_Local_Name_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.12 Read Local Name Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('conn_accept_timeout', 2)
])
class HCI_Write_Connection_Accept_Timeout_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.14 Write Connection Accept Timeout Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('page_timeout', 2)
])
class HCI_Write_Page_Timeout_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.16 Write Page Timeout Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('scan_enable', 1)
])
class HCI_Write_Scan_Enable_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.18 Write Scan Enable Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',             STATUS_SPEC),
    ('page_scan_interval', 2),
    ('page_scan_window',   2)
])
class HCI_Read_Page_Scan_Activity_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.19 Read Page Scan Activity Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('page_scan_interval', 2),
    ('page_scan_window',   2)
])
class HCI_Write_Page_Scan_Activity_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.20 Write Page Scan Activity Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('inquiry_scan_interval', 2),
    ('inquiry_scan_window',   2)
])
class HCI_Write_Inquiry_Scan_Activity_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.22 Write Inquiry Scan Activity Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',          STATUS_SPEC),
    ('class_of_device', {'size': 3, 'mapper': map_class_of_device})
])
class HCI_Read_Class_Of_Device_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.25 Read Class of Device Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('class_of_device', {'size': 3, 'mapper': map_class_of_device})
])
class HCI_Write_Class_Of_Device_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.26 Write Class of Device Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',        STATUS_SPEC),
    ('voice_setting', 2)
])
class HCI_Read_Voice_Setting_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.27 Read Voice Setting Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('voice_setting', 2)
])
class HCI_Write_Voice_Setting_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.28 Write Voice Setting Command
    '''


# -----------------------------------------------------------------------------
class HCI_Read_Synchronous_Flow_Control_Enable_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.36 Write Synchronous Flow Control Enable Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('synchronous_flow_control_enable', 1)
])
class HCI_Write_Synchronous_Flow_Control_Enable_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.37 Write Synchronous Flow Control Enable Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('host_acl_data_packet_length',             2),
    ('host_synchronous_data_packet_length',     1),
    ('host_total_num_acl_data_packets',         2),
    ('host_total_num_synchronous_data_packets', 2)
])
class HCI_Host_Buffer_Size_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.39 Host Buffer Size Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('handle',                   2),
        ('link_supervision_timeout', 2)
    ],
    return_parameters_fields=[
        ('status', STATUS_SPEC),
        ('handle', 2),
    ]
)
class HCI_Write_Link_Supervision_Timeout_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.42 Write Link Supervision Timeout Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',          STATUS_SPEC),
    ('num_support_iac', 1)
])
class HCI_Read_Number_Of_Supported_IAC_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.43 Read Number Of Supported IAC Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',          STATUS_SPEC),
    ('num_current_iac', 1),
    ('iac_lap',         '*')  # TODO: this should be parsed as an array
])
class HCI_Read_Current_IAC_LAP_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.44 Read Current IAC LAP Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('scan_type', 1)
])
class HCI_Write_Inquiry_Scan_Type_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.48 Write Inquiry Scan Type Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('inquiry_mode', 1)
])
class HCI_Write_Inquiry_Mode_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.50 Write Inquiry Mode Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',         STATUS_SPEC),
    ('page_scan_type', 1)
])
class HCI_Read_Page_Scan_Type_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.51 Read Page Scan Type Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('page_scan_type', 1)
])
class HCI_Write_Page_Scan_Type_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.52 Write Page Scan Type Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('fec_required',              1),
    ('extended_inquiry_response', {'size': 240, 'serializer': lambda x: padded_bytes(x, 240)})
])
class HCI_Write_Extended_Inquiry_Response_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.56 Write Extended Inquiry Response Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('simple_pairing_mode', 1)
])
class HCI_Write_Simple_Pairing_Mode_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.59 Write Simple Pairing Mode Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',   STATUS_SPEC),
    ('tx_power', -1)
])
class HCI_Read_Inquiry_Response_Transmit_Power_Level_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.61 Read Inquiry Response Transmit Power Level Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',                   STATUS_SPEC),
    ('erroneous_data_reporting', 1)
])
class HCI_Read_Default_Erroneous_Data_Reporting_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.64 Read Default Erroneous Data Reporting Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('event_mask_page_2', 8)
])
class HCI_Set_Event_Mask_Page_2_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.69 Set Event Mask Page 2 Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_Read_LE_Host_Support_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.78 Read LE Host Support Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('le_supported_host',    1),
    ('simultaneous_le_host', 1)
])
class HCI_Write_LE_Host_Support_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.79 Write LE Host Support Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('secure_connections_host_support', 1)
])
class HCI_Write_Secure_Connections_Host_Support_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.92 Write Secure Connections Host Support Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle',             2),
    ('authenticated_payload_timeout', 2)
])
class HCI_Write_Authenticated_Payload_Timeout_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.3.94 Write Authenticated Payload Timeout Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',             STATUS_SPEC),
    ('hci_version',        1),
    ('hci_revsion',        2),
    ('lmp_pal_version',    1),
    ('manufacturer_name',  2),
    ('lmp_pal_subversion', 2)
])
class HCI_Read_Local_Version_Information_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.4.1 Read Local Version Information Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',             STATUS_SPEC),
    ('supported_commands', 64)
])
class HCI_Read_Local_Supported_Commands_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.4.2 Read Local Supported Commands Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_Read_Local_Supported_Features_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.4.3 Read Local Supported Features Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('page_number', 1)
    ],
    return_parameters_fields=[
        ('status',                STATUS_SPEC),
        ('page_number',           1),
        ('maximum_page_number',   1),
        ('extended_lmp_features', 8)
    ]
)
class HCI_Read_Local_Extended_Features_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.4.4 Read Local Extended Features Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',                                STATUS_SPEC),
    ('hc_acl_data_packet_length',             2),
    ('hc_synchronous_data_packet_length',     1),
    ('hc_total_num_acl_data_packets',         2),
    ('hc_total_num_synchronous_data_packets', 2)
])
class HCI_Read_Buffer_Size_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.4.5 Read Buffer Size Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',  STATUS_SPEC),
    ('bd_addr', Address.parse_address)
])
class HCI_Read_BD_ADDR_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.4.6 Read BD_ADDR Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_Read_Local_Supported_Codecs_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.4.8 Read Local Supported Codecs Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(
    fields=[
        ('connection_handle', 2)
    ],
    return_parameters_fields=[
        ('status',            STATUS_SPEC),
        ('connection_handle', 2),
        ('key_size',          1)
    ]
)
class HCI_Read_Encryption_Key_Size_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.5.7 Read Encryption Key Size Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('le_event_mask', 8)
])
class HCI_LE_Set_Event_Mask_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.1 LE Set Event Mask Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command(return_parameters_fields=[
    ('status',                           STATUS_SPEC),
    ('hc_le_acl_data_packet_length',     2),
    ('hc_total_num_le_acl_data_packets', 1)
])
class HCI_LE_Read_Buffer_Size_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.2 LE Read Buffer Size Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('random_address', lambda data, offset: Address.parse_address_with_type(data, offset, Address.RANDOM_DEVICE_ADDRESS))
])
class HCI_LE_Set_Random_Address_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.4 LE Set Random Address Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('advertising_interval_min',  2),
    ('advertising_interval_max',  2),
    ('advertising_type',          {'size': 1, 'mapper': lambda x: HCI_LE_Set_Advertising_Parameters_Command.advertising_type_name(x)}),
    ('own_address_type',          Address.ADDRESS_TYPE_SPEC),
    ('peer_address_type',         Address.ADDRESS_TYPE_SPEC),
    ('peer_address',              Address.parse_address_preceded_by_type),
    ('advertising_channel_map',   1),
    ('advertising_filter_policy', 1),
])
class HCI_LE_Set_Advertising_Parameters_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.5 LE Set Advertising Parameters Command
    '''

    ADV_IND         = 0x00
    ADV_DIRECT_IND  = 0x01
    ADV_SCAN_IND    = 0x02
    ADV_NONCONN_IND = 0x03
    ADV_DIRECT_IND  = 0x04

    ADVERTISING_TYPE_NAMES = {
        ADV_IND:         'ADV_IND',
        ADV_DIRECT_IND:  'ADV_DIRECT_IND',
        ADV_SCAN_IND:    'ADV_SCAN_IND',
        ADV_NONCONN_IND: 'ADV_NONCONN_IND',
        ADV_DIRECT_IND:  'ADV_DIRECT_IND'
    }

    @classmethod
    def advertising_type_name(cls, advertising_type):
        return name_or_number(cls.ADVERTISING_TYPE_NAMES, advertising_type)


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_LE_Read_Advertising_Channel_Tx_Power_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.6 LE Read Advertising Channel Tx Power Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('advertising_data', {
        'parser':     HCI_Object.parse_length_prefixed_bytes,
        'serializer': functools.partial(HCI_Object.serialize_length_prefixed_bytes, padded_size=32)
    })
])
class HCI_LE_Set_Advertising_Data_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.7 LE Set Advertising Data Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('scan_response_data', {
        'parser':     HCI_Object.parse_length_prefixed_bytes,
        'serializer': functools.partial(HCI_Object.serialize_length_prefixed_bytes, padded_size=32)
    })
])
class HCI_LE_Set_Scan_Response_Data_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.8 LE Set Scan Response Data Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('advertising_enable', 1)
])
class HCI_LE_Set_Advertising_Enable_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.9 LE Set Advertising Enable Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('le_scan_type',           1),
    ('le_scan_interval',       2),
    ('le_scan_window',         2),
    ('own_address_type',       Address.ADDRESS_TYPE_SPEC),
    ('scanning_filter_policy', 1)
])
class HCI_LE_Set_Scan_Parameters_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.10 LE Set Scan Parameters Command
    '''
    PASSIVE_SCANNING = 0
    ACTIVE_SCANNING  = 1

    BASIC_UNFILTERED_POLICY    = 0x00
    BASIC_FILTERED_POLICY      = 0x01
    EXTENDED_UNFILTERED_POLICY = 0x02
    EXTENDED_FILTERED_POLICY   = 0x03


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('le_scan_enable',    1),
    ('filter_duplicates', 1),
])
class HCI_LE_Set_Scan_Enable_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.11 LE Set Scan Enable Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('le_scan_interval',        2),
    ('le_scan_window',          2),
    ('initiator_filter_policy', 1),
    ('peer_address_type',       Address.ADDRESS_TYPE_SPEC),
    ('peer_address',            Address.parse_address_preceded_by_type),
    ('own_address_type',        Address.ADDRESS_TYPE_SPEC),
    ('conn_interval_min',       2),
    ('conn_interval_max',       2),
    ('conn_latency',            2),
    ('supervision_timeout',     2),
    ('minimum_ce_length',       2),
    ('maximum_ce_length',       2)
])
class HCI_LE_Create_Connection_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.12 LE Create Connection Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_LE_Create_Connection_Cancel_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.13 LE Create Connection Cancel Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_LE_Read_White_List_Size_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.14 LE Read White List Size Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_LE_Clear_White_List_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.15 LE Clear White List Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('address_type', Address.ADDRESS_TYPE_SPEC),
    ('address',      Address.parse_address_preceded_by_type)
])
class HCI_LE_Add_Device_To_White_List_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.16 LE Add Device To White List Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('address_type', Address.ADDRESS_TYPE_SPEC),
    ('address',      Address.parse_address_preceded_by_type)
])
class HCI_LE_Remove_Device_From_White_List_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.17 LE Remove Device From White List Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle',   2),
    ('conn_interval_min',   2),
    ('conn_interval_max',   2),
    ('conn_latency',        2),
    ('supervision_timeout', 2),
    ('minimum_ce_length',   2),
    ('maximum_ce_length',   2)
])
class HCI_LE_Connection_Update_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.18 LE Connection Update Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2)
])
class HCI_LE_Read_Remote_Features_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.21 LE Read Remote Features Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle',     2),
    ('random_number',         8),
    ('encrypted_diversifier', 2),
    ('long_term_key',         16)
])
class HCI_LE_Start_Encryption_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.24 LE Start Encryption Command
    (renamed to "LE Enable Encryption Command" in version 5.2 of the specification)
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2),
    ('long_term_key',     16)
])
class HCI_LE_Long_Term_Key_Request_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.25 LE Long Term Key Request Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2)
])
class HCI_LE_Long_Term_Key_Request_Negative_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.26 LE Long Term Key Request Negative Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_LE_Read_Supported_States_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.27 LE Read Supported States Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2),
    ('interval_min',      2),
    ('interval_max',      2),
    ('latency',           2),
    ('timeout',           2),
    ('minimum_ce_length', 2),
    ('maximum_ce_length', 2)
])
class HCI_LE_Remote_Connection_Parameter_Request_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.31 LE Remote Connection Parameter Request Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('connection_handle', 2),
    ('reason',            {'size': 1, 'mapper': HCI_Constant.error_name})
])
class HCI_LE_Remote_Connection_Parameter_Request_Negative_Reply_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.32 LE Remote Connection Parameter Request Negative Reply Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('suggested_max_tx_octets', 2),
    ('suggested_max_tx_time',   2)
])
class HCI_LE_Write_Suggested_Default_Data_Length_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.35 LE Write Suggested Default Data Length Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('peer_identity_address_type', Address.ADDRESS_TYPE_SPEC),
    ('peer_identity_address',      Address.parse_address_preceded_by_type),
    ('peer_irk',                   16),
    ('local_irk',                  16),
])
class HCI_LE_Add_Device_To_Resolving_List_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.38 LE Add Device To Resolving List Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command()
class HCI_LE_Clear_Resolving_List_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.40 LE Clear Resolving List Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('all_phys', 1),
    ('tx_phys',  1),
    ('rx_phys',  1)
])
class HCI_LE_Set_Default_PHY_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.48 LE Set Default PHY Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('address_resolution_enable', 1)
])
class HCI_LE_Set_Address_Resolution_Enable_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.44 LE Set Address Resolution Enable Command
    '''


# -----------------------------------------------------------------------------
@HCI_Command.command([
    ('rpa_timeout', 2)
])
class HCI_LE_Set_Resolvable_Private_Address_Timeout_Command(HCI_Command):
    '''
    See Bluetooth spec @ 7.8.45 LE Set Resolvable Private Address Timeout Command
    '''


# -----------------------------------------------------------------------------
# HCI Events
# -----------------------------------------------------------------------------
class HCI_Event(HCI_Packet):
    '''
    See Bluetooth spec @ Vol 2, Part E - 5.4.4 HCI Event Packet
    '''
    hci_packet_type    = HCI_EVENT_PACKET
    event_classes      = {}
    meta_event_classes = {}

    @staticmethod
    def event(fields=[]):
        '''
        Decorator used to declare and register subclasses
        '''

        def inner(cls):
            cls.name = cls.__name__.upper()
            cls.event_code = key_with_value(HCI_EVENT_NAMES, cls.name)
            if cls.event_code is None:
                raise KeyError('event not found in HCI_EVENT_NAMES')
            cls.fields = fields

            # Patch the __init__ method to fix the event_code
            def init(self, parameters=None, **kwargs):
                return HCI_Event.__init__(self, cls.event_code, parameters, **kwargs)
            cls.__init__ = init

            # Register a factory for this class
            HCI_Event.event_classes[cls.event_code] = cls

            return cls

        return inner

    @staticmethod
    def registered(cls):
        cls.name = cls.__name__.upper()
        cls.event_code = key_with_value(HCI_EVENT_NAMES, cls.name)
        if cls.event_code is None:
            raise KeyError('event not found in HCI_EVENT_NAMES')

        # Register a factory for this class
        HCI_Event.event_classes[cls.event_code] = cls

        return cls

    @staticmethod
    def from_bytes(packet):
        event_code = packet[1]
        length     = packet[2]
        parameters = packet[3:]
        if len(parameters) != length:
            raise ValueError('invalid packet length')

        if event_code == HCI_LE_META_EVENT:
            # We do this dispatch here and not in the subclass in order to avoid call loops
            subevent_code = parameters[0]
            cls = HCI_Event.meta_event_classes.get(subevent_code)
            if cls is None:
                # No class registered, just use a generic class instance
                return HCI_LE_Meta_Event(subevent_code, parameters)

        else:
            cls = HCI_Event.event_classes.get(event_code)
            if cls is None:
                # No class registered, just use a generic class instance
                return HCI_Event(event_code, parameters)

        # Invoke the factory to create a new instance
        return cls.from_parameters(parameters)

    @classmethod
    def from_parameters(cls, parameters):
        self = cls.__new__(cls)
        HCI_Event.__init__(self, self.event_code, parameters)
        if fields := getattr(self, 'fields', None):
            HCI_Object.init_from_bytes(self, parameters, 0, fields)
        return self

    @staticmethod
    def event_name(event_code):
        return name_or_number(HCI_EVENT_NAMES, event_code)

    def __init__(self, event_code, parameters=None, **kwargs):
        super().__init__(HCI_Event.event_name(event_code))
        if (fields := getattr(self, 'fields', None)) and kwargs:
            HCI_Object.init_from_fields(self, fields, kwargs)
            if parameters is None:
                parameters = HCI_Object.dict_to_bytes(kwargs, fields)
        self.event_code = event_code
        self.parameters = parameters

    def to_bytes(self):
        parameters = b'' if self.parameters is None else self.parameters
        return bytes([HCI_EVENT_PACKET, self.event_code, len(parameters)]) + parameters

    def __str__(self):
        result = color(self.name, 'magenta')
        if fields := getattr(self, 'fields', None):
            result += ':\n' + HCI_Object.format_fields(self.__dict__, fields, '  ')
        else:
            if self.parameters:
                result += f': {self.parameters.hex()}'
        return result


# -----------------------------------------------------------------------------
class HCI_LE_Meta_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.65 LE Meta Event
    '''

    @staticmethod
    def event(fields=[]):
        '''
        Decorator used to declare and register subclasses
        '''

        def inner(cls):
            cls.name = cls.__name__.upper()
            cls.subevent_code = key_with_value(HCI_SUBEVENT_NAMES, cls.name)
            if cls.subevent_code is None:
                raise KeyError('subevent not found in HCI_SUBEVENT_NAMES')
            cls.fields = fields

            # Patch the __init__ method to fix the subevent_code
            def init(self, parameters=None, **kwargs):
                return HCI_LE_Meta_Event.__init__(self, cls.subevent_code, parameters, **kwargs)
            cls.__init__ = init

            # Register a factory for this class
            HCI_Event.meta_event_classes[cls.subevent_code] = cls

            return cls

        return inner

    @classmethod
    def from_parameters(cls, parameters):
        self = cls.__new__(cls)
        HCI_LE_Meta_Event.__init__(self, self.subevent_code, parameters)
        if fields := getattr(self, 'fields', None):
            HCI_Object.init_from_bytes(self, parameters, 1, fields)
        return self

    @staticmethod
    def subevent_name(subevent_code):
        return name_or_number(HCI_SUBEVENT_NAMES, subevent_code)

    def __init__(self, subevent_code, parameters, **kwargs):
        self.subevent_code = subevent_code
        if parameters is None and (fields := getattr(self, 'fields', None)) and kwargs:
            parameters = bytes([subevent_code]) + HCI_Object.dict_to_bytes(kwargs, fields)
        super().__init__(HCI_LE_META_EVENT, parameters, **kwargs)

        # Override the name in order to adopt the subevent name instead
        self.name = self.subevent_name(subevent_code)

    def __str__(self):
        result = color(self.subevent_name(self.subevent_code), 'magenta')
        if fields := getattr(self, 'fields', None):
            result += ':\n' + HCI_Object.format_fields(self.__dict__, fields, '  ')
        else:
            if self.parameters:
                result += f': {self.parameters.hex()}'
        return result


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('status',                STATUS_SPEC),
    ('connection_handle',     2),
    ('role',                  {'size': 1, 'mapper': lambda x: 'CENTRAL' if x == 0 else 'PERIPHERAL'}),
    ('peer_address_type',     Address.ADDRESS_TYPE_SPEC),
    ('peer_address',          Address.parse_address_preceded_by_type),
    ('conn_interval',         2),
    ('conn_latency',          2),
    ('supervision_timeout',   2),
    ('master_clock_accuracy', 1)
])
class HCI_LE_Connection_Complete_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.1 LE Connection Complete Event
    '''


# -----------------------------------------------------------------------------
class HCI_LE_Advertising_Report_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.2 LE Advertising Report Event
    '''
    subevent_code = HCI_LE_ADVERTISING_REPORT_EVENT

    # Event Types
    ADV_IND         = 0x00
    ADV_DIRECT_IND  = 0x01
    ADV_SCAN_IND    = 0x02
    ADV_NONCONN_IND = 0x03
    SCAN_RSP        = 0x04

    EVENT_TYPE_NAMES = {
        ADV_IND:         'ADV_IND',          # Connectable and scannable undirected advertising
        ADV_DIRECT_IND:  'ADV_DIRECT_IND',   # Connectable directed advertising
        ADV_SCAN_IND:    'ADV_SCAN_IND',     # Scannable undirected advertising
        ADV_NONCONN_IND: 'ADV_NONCONN_IND',  # Non connectable undirected advertising
        SCAN_RSP:        'SCAN_RSP'          # Scan Response
    }

    REPORT_FIELDS = [
        ('event_type',   1),
        ('address_type', Address.ADDRESS_TYPE_SPEC),
        ('address',      Address.parse_address_preceded_by_type),
        ('data',         {'parser': HCI_Object.parse_length_prefixed_bytes, 'serializer': HCI_Object.serialize_length_prefixed_bytes}),
        ('rssi',         -1)
    ]

    @classmethod
    def event_type_name(cls, event_type):
        return name_or_number(cls.EVENT_TYPE_NAMES, event_type)

    @staticmethod
    def from_parameters(parameters):
        num_reports = parameters[1]
        reports = []
        offset = 2
        for _ in range(num_reports):
            report = HCI_Object.from_bytes(parameters, offset, HCI_LE_Advertising_Report_Event.REPORT_FIELDS)
            offset += 10 + len(report.data)
            reports.append(report)

        return HCI_LE_Advertising_Report_Event(reports)

    def __init__(self, reports):
        self.reports = reports[:]

        # Serialize the fields
        parameters = bytes([HCI_LE_ADVERTISING_REPORT_EVENT, len(reports)]) + b''.join([bytes(report) for report in reports])

        super().__init__(self.subevent_code, parameters)

    def __str__(self):
        reports = '\n'.join([report.to_string('  ', {
            'event_type':   self.event_type_name,
            'address_type': Address.address_type_name,
            'data': lambda x: str(AdvertisingData.from_bytes(x))
        }) for report in self.reports])
        return f'{color(self.subevent_name(self.subevent_code), "magenta")}:\n{reports}'


HCI_Event.meta_event_classes[HCI_LE_ADVERTISING_REPORT_EVENT] = HCI_LE_Advertising_Report_Event


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('status',              STATUS_SPEC),
    ('connection_handle',   2),
    ('conn_interval',       2),
    ('conn_latency',        2),
    ('supervision_timeout', 2)
])
class HCI_LE_Connection_Update_Complete_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.3 LE Connection Update Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('le_features',       8)
])
class HCI_LE_Read_Remote_Features_Complete_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.4 LE Read Remote Features Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('connection_handle',     2),
    ('random_number',         8),
    ('encryption_diversifier', 2)
])
class HCI_LE_Long_Term_Key_Request_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.5 LE Long Term Key Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('connection_handle', 2),
    ('interval_min',      2),
    ('interval_max',      2),
    ('latency',           2),
    ('timeout',           2)
])
class HCI_LE_Remote_Connection_Parameter_Request_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.6 LE Remote Connection Parameter Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('connection_handle', 2),
    ('max_tx_octets',     2),
    ('max_tx_time',       2),
    ('max_rx_octets',     2),
    ('max_rx_time',       2),
])
class HCI_LE_Data_Length_Change_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.7 LE Data Length Change Event
    '''


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('status',                           STATUS_SPEC),
    ('connection_handle',                2),
    ('role',                             {'size': 1, 'mapper': lambda x: 'CENTRAL' if x == 0 else 'PERIPHERAL'}),
    ('peer_address_type',                Address.ADDRESS_TYPE_SPEC),
    ('peer_address',                     Address.parse_address_preceded_by_type),
    ('local_resolvable_private_address', Address.parse_address),
    ('peer_resolvable_private_address',  Address.parse_address),
    ('conn_interval',                    2),
    ('conn_latency',                     2),
    ('supervision_timeout',              2),
    ('master_clock_accuracy',            1)
])
class HCI_LE_Enhanced_Connection_Complete_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.10 LE Enhanced Connection Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('tx_phy',            {'size': 1, 'mapper': HCI_Constant.le_phy_name}),
    ('rx_phy',            {'size': 1, 'mapper': HCI_Constant.le_phy_name})
])
class HCI_LE_PHY_Update_Complete_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.12 LE PHY Update Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_LE_Meta_Event.event([
    ('connection_handle',           2),
    ('channel_selection_algorithm', 1)
])
class HCI_LE_Channel_Selection_Algorithm_Event(HCI_LE_Meta_Event):
    '''
    See Bluetooth spec @ 7.7.65.20 LE Channel Selection Algorithm Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status', STATUS_SPEC)
])
class HCI_Inquiry_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.1 Inquiry Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.registered
class HCI_Inquiry_Result_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.2 Inquiry Result Event
    '''

    RESPONSE_FIELDS = [
        ('bd_addr',                   Address.parse_address),
        ('page_scan_repetition_mode', 1),
        ('reserved',                  1),
        ('reserved',                  1),
        ('class_of_device',           {'size': 3, 'mapper': map_class_of_device}),
        ('clock_offset',              2)
    ]

    @staticmethod
    def from_parameters(parameters):
        num_responses = parameters[0]
        responses = []
        offset = 1
        for _ in range(num_responses):
            response = HCI_Object.from_bytes(parameters, offset, HCI_Inquiry_Result_Event.RESPONSE_FIELDS)
            offset += 14
            responses.append(response)

        return HCI_Inquiry_Result_Event(responses)

    def __init__(self, responses):
        self.responses = responses[:]

        # Serialize the fields
        parameters = bytes([HCI_INQUIRY_RESULT_EVENT, len(responses)]) + b''.join([bytes(response) for response in responses])

        super().__init__(HCI_INQUIRY_RESULT_EVENT, parameters)

    def __str__(self):
        responses = '\n'.join([response.to_string(indentation='  ') for response in self.responses])
        return f'{color("HCI_INQUIRY_RESULT_EVENT", "magenta")}:\n{responses}'


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',             STATUS_SPEC),
    ('connection_handle',  2),
    ('bd_addr',            Address.parse_address),
    ('link_type',          {'size': 1, 'mapper': lambda x: HCI_Connection_Complete_Event.link_type_name(x)}),
    ('encryption_enabled', 1)
])
class HCI_Connection_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.3 Connection Complete Event
    '''

    SCO_LINK_TYPE  = 0x00
    ACL_LINK_TYPE  = 0x01
    ESCO_LINK_TYPE = 0x02

    LINK_TYPE_NAMES = {
        SCO_LINK_TYPE:  'SCO',
        ACL_LINK_TYPE:  'ACL',
        ESCO_LINK_TYPE: 'eSCO'
    }

    @staticmethod
    def link_type_name(link_type):
        return name_or_number(HCI_Connection_Complete_Event.LINK_TYPE_NAMES, link_type)


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr',         Address.parse_address),
    ('class_of_device', 3),
    ('link_type',       {'size': 1, 'mapper': lambda x: HCI_Connection_Complete_Event.link_type_name(x)})
])
class HCI_Connection_Request_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.4 Connection Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('reason',            {'size': 1, 'mapper': HCI_Constant.error_name})
])
class HCI_Disconnection_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.5 Disconnection Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2)
])
class HCI_Authentication_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.6 Authentication Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',      STATUS_SPEC),
    ('bd_addr',     Address.parse_address),
    ('remote_name', {'size': 248, 'mapper': map_null_terminated_utf8_string})
])
class HCI_Remote_Name_Request_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.7 Remote Name Request Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',             STATUS_SPEC),
    ('connection_handle',  2),
    ('encryption_enabled', {'size': 1, 'mapper': lambda x: HCI_Encryption_Change_Event.encryption_enabled_name(x)})
])
class HCI_Encryption_Change_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.8 Encryption Change Event
    '''

    OFF           = 0x00
    E0_OR_AES_CCM = 0x01
    AES_CCM       = 0x02

    ENCYRPTION_ENABLED_NAMES = {
        OFF:           'OFF',
        E0_OR_AES_CCM: 'E0_OR_AES_CCM',
        AES_CCM:       'AES_CCM'
    }

    @staticmethod
    def encryption_enabled_name(encryption_enabled):
        return name_or_number(HCI_Encryption_Change_Event.ENCYRPTION_ENABLED_NAMES, encryption_enabled)


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('lmp_features',      8)
])
class HCI_Read_Remote_Supported_Features_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.11 Read Remote Supported Features Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('version',           1),
    ('manufacturer_name', 2),
    ('subversion',        2)
])
class HCI_Read_Remote_Version_Information_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.12 Read Remote Version Information Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('num_hci_command_packets', 1),
    ('command_opcode',          {'size': 2, 'mapper': HCI_Command.command_name}),
    ('return_parameters',       '*')
])
class HCI_Command_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.14 Command Complete Event
    '''

    def map_return_parameters(self, return_parameters):
        # Map simple 'status' return parameters to their named constant form
        if type(return_parameters) is bytes and len(return_parameters) == 1:
            # Byte-array form
            return HCI_Constant.status_name(return_parameters[0])
        elif type(return_parameters) is int:
            # Already converted to an integer status code
            return HCI_Constant.status_name(return_parameters)
        else:
            return return_parameters

    @staticmethod
    def from_parameters(parameters):
        self = HCI_Command_Complete_Event.__new__(HCI_Command_Complete_Event)
        HCI_Event.__init__(self, self.event_code, parameters)
        HCI_Object.init_from_bytes(self, parameters, 0, HCI_Command_Complete_Event.fields)

        # Parse the return parameters
        if type(self.return_parameters) is bytes and len(self.return_parameters) == 1:
            # All commands with 1-byte return parameters return a 'status' field, convert it to an integer
            self.return_parameters = self.return_parameters[0]
        else:
            cls = HCI_Command.command_classes.get(self.command_opcode)
            if cls and cls.return_parameters_fields:
                self.return_parameters = HCI_Object.from_bytes(self.return_parameters, 0, cls.return_parameters_fields)
                self.return_parameters.fields = cls.return_parameters_fields

        return self

    def __str__(self):
        return f'{color(self.name, "magenta")}:\n' + HCI_Object.format_fields(self.__dict__, self.fields, '  ', {
            'return_parameters': self.map_return_parameters
        })


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',                  {'size': 1, 'mapper': lambda x: HCI_Command_Status_Event.status_name(x)}),
    ('num_hci_command_packets', 1),
    ('command_opcode',          {'size': 2, 'mapper': HCI_Command.command_name})
])
class HCI_Command_Status_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.15 Command Complete Event
    '''
    PENDING = 0

    @staticmethod
    def status_name(status):
        if status == HCI_Command_Status_Event.PENDING:
            return 'PENDING'
        else:
            return HCI_Constant.error_name(status)


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',   STATUS_SPEC),
    ('bd_addr',  Address.parse_address),
    ('new_role', {'size': 1, 'mapper': HCI_Constant.role_name})
])
class HCI_Role_Change_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.18 Role Change Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.registered
class HCI_Number_Of_Completed_Packets_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.19 Number Of Completed Packets Event
    '''

    @classmethod
    def from_parameters(cls, parameters):
        self = cls.__new__(cls)
        self.parameters = parameters
        num_handles = parameters[0]
        self.connection_handles = []
        self.num_completed_packets = []
        for i in range(num_handles):
            self.connection_handles.append(
                struct.unpack_from('<H', parameters, 1 + i * 4)[0]
            )
            self.num_completed_packets.append(
                struct.unpack_from('<H', parameters, 1 + i * 4 + 2)[0]
            )

        return self

    def __init__(self, connection_handle_and_completed_packets_list):
        self.connection_handles = []
        self.num_completed_packets = []
        parameters = bytes([len(connection_handle_and_completed_packets_list)])
        for handle, completed_packets in connection_handle_and_completed_packets_list:
            self.connection_handles.append(handle)
            self.num_completed_packets.append(completed_packets)
            parameters += struct.pack('<H', handle)
            parameters += struct.pack('<H', completed_packets)
        super().__init__(HCI_NUMBER_OF_COMPLETED_PACKETS_EVENT, parameters)

    def __str__(self):
        lines = [
            color(self.name, 'magenta') + ':',
            color('  number_of_handles:         ', 'cyan') + f'{len(self.connection_handles)}'
        ]
        for i in range(len(self.connection_handles)):
            lines.append(color(f'  connection_handle[{i}]:     ', 'cyan') + f'{self.connection_handles[i]}')
            lines.append(color(f'  num_completed_packets[{i}]: ', 'cyan') + f'{self.num_completed_packets[i]}')
        return '\n'.join(lines)


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('current_mode',      {'size': 1, 'mapper': lambda x: HCI_Mode_Change_Event.mode_name(x)}),
    ('interval',          2)
])
class HCI_Mode_Change_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.20 Mode Change Event
    '''

    ACTIVE_MODE = 0x00
    HOLD_MODE   = 0x01
    SNIFF_MODE  = 0x02

    MODE_NAMES = {
        ACTIVE_MODE: 'ACTIVE_MODE',
        HOLD_MODE:   'HOLD_MODE',
        SNIFF_MODE:  'SNIFF_MODE'
    }

    @staticmethod
    def mode_name(mode):
        return name_or_number(HCI_Mode_Change_Event.MODE_NAMES, mode)


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr', Address.parse_address)
])
class HCI_PIN_Code_Request_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.22 PIN Code Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr',  Address.parse_address)
])
class HCI_Link_Key_Request_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.24 7.7.23 Link Key Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr',  Address.parse_address),
    ('link_key', 16),
    ('key_type', {'size': 1, 'mapper': HCI_Constant.link_key_type_name})
])
class HCI_Link_Key_Notification_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.24 Link Key Notification Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('connection_handle', 2),
    ('lmp_max_slots',     1)
])
class HCI_Max_Slots_Change_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.27 Max Slots Change Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('clock_offset',      2)
])
class HCI_Read_Clock_Offset_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.28 Read Clock Offset Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2),
    ('packet_type',       2)
])
class HCI_Connection_Packet_Type_Changed_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.29 Connection Packet Type Changed Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr',                   Address.parse_address),
    ('page_scan_repetition_mode', 1)
])
class HCI_Page_Scan_Repetition_Mode_Change_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.31 Page Scan Repetition Mode Change Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.registered
class HCI_Inquiry_Result_With_Rssi_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.33 Inquiry Result with RSSI Event
    '''

    RESPONSE_FIELDS = [
        ('bd_addr',                   Address.parse_address),
        ('page_scan_repetition_mode', 1),
        ('reserved',                  1),
        ('class_of_device',           {'size': 3, 'mapper': map_class_of_device}),
        ('clock_offset',              2),
        ('rssi',                      -1)
    ]

    @staticmethod
    def from_parameters(parameters):
        num_responses = parameters[0]
        responses = []
        offset = 1
        for _ in range(num_responses):
            response = HCI_Object.from_bytes(parameters, offset, HCI_Inquiry_Result_With_Rssi_Event.RESPONSE_FIELDS)
            offset += 14
            responses.append(response)

        return HCI_Inquiry_Result_With_Rssi_Event(responses)

    def __init__(self, responses):
        self.responses = responses[:]

        # Serialize the fields
        parameters = bytes([HCI_INQUIRY_RESULT_WITH_RSSI_EVENT, len(responses)]) + b''.join([bytes(response) for response in responses])

        super().__init__(HCI_INQUIRY_RESULT_WITH_RSSI_EVENT, parameters)

    def __str__(self):
        responses = '\n'.join([response.to_string(indentation='  ') for response in self.responses])
        return f'{color("HCI_INQUIRY_RESULT_WITH_RSSI_EVENT", "magenta")}:\n{responses}'


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',                STATUS_SPEC),
    ('connection_handle',     2),
    ('page_number',           1),
    ('maximum_page_number',   1),
    ('extended_lmp_features', 8)
])
class HCI_Read_Remote_Extended_Features_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.34 Read Remote Extended Features Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',                STATUS_SPEC),
    ('connection_handle',     2),
    ('bd_addr',               Address.parse_address),
    ('link_type',             {'size': 1, 'mapper': lambda x: HCI_Synchronous_Connection_Complete_Event.link_type_name(x)}),
    ('transmission_interval', 1),
    ('retransmission_window', 1),
    ('rx_packet_length',      2),
    ('tx_packet_length',      2),
    ('air_mode',              {'size': 1, 'mapper': lambda x: HCI_Synchronous_Connection_Complete_Event.air_mode_name(x)}),
])
class HCI_Synchronous_Connection_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.35 Synchronous Connection Complete Event
    '''

    SCO_CONNECTION_LINK_TYPE = 0x00
    ESCO_CONNECTION_LINK_TYPE = 0x02

    LINK_TYPE_NAMES = {
        SCO_CONNECTION_LINK_TYPE:  'SCO',
        ESCO_CONNECTION_LINK_TYPE: 'eSCO'
    }

    U_LAW_LOG_AIR_MODE        = 0x00
    A_LAW_LOG_AIR_MORE        = 0x01
    CVSD_AIR_MODE             = 0x02
    TRANSPARENT_DATA_AIR_MODE = 0x03

    AIR_MODE_NAMES = {
        U_LAW_LOG_AIR_MODE:        'u-law log',
        A_LAW_LOG_AIR_MORE:        'A-law log',
        CVSD_AIR_MODE:             'CVSD',
        TRANSPARENT_DATA_AIR_MODE: 'Transparend Data'
    }

    @staticmethod
    def link_type_name(link_type):
        return name_or_number(HCI_Synchronous_Connection_Complete_Event.LINK_TYPE_NAMES, link_type)

    @staticmethod
    def air_mode_name(air_mode):
        return name_or_number(HCI_Synchronous_Connection_Complete_Event.AIR_MODE_NAMES, air_mode)


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',                STATUS_SPEC),
    ('connection_handle',     2),
    ('transmission_interval', 1),
    ('retransmission_window', 1),
    ('rx_packet_length',      2),
    ('tx_packet_length',      2)
])
class HCI_Synchronous_Connection_Changed_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.36 Synchronous Connection Changed Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('num_responses',             1),
    ('bd_addr',                   Address.parse_address),
    ('page_scan_repetition_mode', 1),
    ('reserved',                  1),
    ('class_of_device',          {'size': 3, 'mapper': map_class_of_device}),
    ('clock_offset',              2),
    ('rssi',                     -1),
    ('extended_inquiry_response', 240),
])
class HCI_Extended_Inquiry_Result_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.38 Extended Inquiry Result Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',            STATUS_SPEC),
    ('connection_handle', 2)
])
class HCI_Encryption_Key_Refresh_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.39 Encryption Key Refresh Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr', Address.parse_address)
])
class HCI_IO_Capability_Request_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.40 IO Capability Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr',                     Address.parse_address),
    ('io_capability',               {'size': 1, 'mapper': HCI_Constant.io_capability_name}),
    ('oob_data_present',            1),
    ('authentication_requirements', {'size': 1, 'mapper': HCI_Constant.authentication_requirements_name})
])
class HCI_IO_Capability_Response_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.41 IO Capability Response Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr',       Address.parse_address),
    ('numeric_value', 4)
])
class HCI_User_Confirmation_Request_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.42 User Confirmation Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr', Address.parse_address)
])
class HCI_User_Passkey_Request_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.43 User Passkey Request Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('status',  STATUS_SPEC),
    ('bd_addr', Address.parse_address)
])
class HCI_Simple_Pairing_Complete_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.45 Simple Pairing Complete Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('connection_handle',        2),
    ('link_supervision_timeout', 2)
])
class HCI_Link_Supervision_Timeout_Changed_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.46 Link Supervision Timeout Changed Event
    '''


# -----------------------------------------------------------------------------
@HCI_Event.event([
    ('bd_addr',                 Address.parse_address),
    ('host_supported_features', 8)
])
class HCI_Remote_Host_Supported_Features_Notification_Event(HCI_Event):
    '''
    See Bluetooth spec @ 7.7.50 Remote Host Supported Features Notification Event
    '''


# -----------------------------------------------------------------------------
class HCI_AclDataPacket(HCI_Packet):
    '''
    See Bluetooth spec @ 5.4.2 HCI ACL Data Packets
    '''
    hci_packet_type = HCI_ACL_DATA_PACKET

    @staticmethod
    def from_bytes(packet):
        # Read the header
        h, data_total_length = struct.unpack_from('<HH', packet, 1)
        connection_handle = h & 0xFFF
        pb_flag = (h >> 12) & 3
        bc_flag = (h >> 14) & 3
        data = packet[5:]
        if len(data) != data_total_length:
            raise ValueError('invalid packet length')
        return HCI_AclDataPacket(connection_handle, pb_flag, bc_flag, data_total_length, data)

    def to_bytes(self):
        h = (self.pb_flag << 12) | (self.bc_flag << 14) | self.connection_handle
        return struct.pack('<BHH', HCI_ACL_DATA_PACKET, h, self.data_total_length) + self.data

    def __init__(self, connection_handle, pb_flag, bc_flag, data_total_length, data):
        self.connection_handle = connection_handle
        self.pb_flag = pb_flag
        self.bc_flag = bc_flag
        self.data_total_length = data_total_length
        self.data = data

    def __bytes__(self):
        return self.to_bytes()

    def __str__(self):
        return f'{color("ACL", "blue")}: handle=0x{self.connection_handle:04x}, pb={self.pb_flag}, bc={self.bc_flag}, data_total_length={self.data_total_length}, data={self.data.hex()}'


# -----------------------------------------------------------------------------
class HCI_AclDataPacketAssembler:
    def __init__(self, callback):
        self.callback         = callback
        self.current_data     = None
        self.l2cap_pdu_length = 0

    def feed_packet(self, packet):
        if packet.pb_flag == HCI_ACL_PB_FIRST_NON_FLUSHABLE or packet.pb_flag == HCI_ACL_PB_FIRST_FLUSHABLE:
            (l2cap_pdu_length,)   = struct.unpack_from('<H', packet.data, 0)
            self.current_data     = packet.data
            self.l2cap_pdu_length = l2cap_pdu_length
        elif packet.pb_flag == HCI_ACL_PB_CONTINUATION:
            if self.current_data is None:
                logger.warning('!!! ACL continuation without start')
                return
            self.current_data += packet.data

        if len(self.current_data) == self.l2cap_pdu_length + 4:
            # The packet is complete, invoke the callback
            logger.debug(f'<<< ACL PDU: {self.current_data.hex()}')
            self.callback(self.current_data)

            # Reset
            self.current_data     = None
            self.l2cap_pdu_length = 0
        else:
            # Sanity check
            if len(self.current_data) > self.l2cap_pdu_length + 4:
                logger.warning('!!! ACL data exceeds L2CAP PDU')
                self.current_data     = None
                self.l2cap_pdu_length = 0
