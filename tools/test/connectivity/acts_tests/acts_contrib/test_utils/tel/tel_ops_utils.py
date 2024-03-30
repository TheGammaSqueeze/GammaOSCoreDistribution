#!/usr/bin/env python3
#
#   Copyright 2022 - Google
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

import time
from acts_contrib.test_utils.net import ui_utils
from acts_contrib.test_utils.tel.tel_defines import MOBILE_DATA
from acts_contrib.test_utils.tel.tel_defines import USE_SIM
from acts_contrib.test_utils.tel.tel_data_utils import active_file_download_task
from acts_contrib.test_utils.tel.tel_defines import WAIT_TIME_BETWEEN_STATE_CHECK
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_voice_utils import hangup_call
from acts_contrib.test_utils.tel.tel_voice_utils import initiate_call
from acts_contrib.test_utils.tel.tel_voice_utils import wait_and_answer_call


def initiate_call_verify_operation(log,
                                    caller,
                                    callee,
                                    download=False):
    """Initiate call and verify operations with an option of data idle or data download

    Args:
        log: log object.
        caller:  android device object as caller.
        callee:  android device object as callee.
        download: True if download operation is to be performed else False

    Return:
        True: if call initiated and verified operations successfully
        False: for errors
    """
    caller_number = caller.telephony['subscription'][
        get_outgoing_voice_sub_id(caller)]['phone_num']
    callee_number = callee.telephony['subscription'][
        get_outgoing_voice_sub_id(callee)]['phone_num']
    if not initiate_call(log, caller, callee_number):
        caller.log.error("Phone was unable to initate a call")
        return False

    if not wait_and_answer_call(log, callee, caller_number):
        callee.log.error("Callee failed to receive incoming call or answered the call.")
        return False

    if download:
        if not active_file_download_task(log, caller, "10MB"):
            caller.log.error("Unable to download file")
            return False

    if not hangup_call(log, caller):
        caller.log.error("Unable to hang up the call")
        return False
    return True

def get_resource_value(ad, label_text= None):
    """Get current resource value

    Args:
        ad:  android device object as caller.
        label_text: Enter text to be detected

    Return:
        node attribute value
    """
    if label_text == USE_SIM:
        resource_id = 'android:id/switch_widget'
        label_resource_id = 'com.android.settings:id/switch_text'
        node_attribute = 'checked'
    elif label_text == MOBILE_DATA:
        resource_id = 'android:id/switch_widget'
        label_resource_id = 'android:id/widget_frame'
        label_text = ''
        node_attribute = 'checked'
    else:
        ad.log.error(
            'Missing arguments, resource_id, label_text and node_attribute'
            )

    resource = {
        'resource_id': resource_id,
    }
    node = ui_utils.wait_and_get_xml_node(ad,
                                        timeout=30,
                                        sibling=resource,
                                        text=label_text,
                                        resource_id=label_resource_id)
    return node.attributes[node_attribute].value

def wait_and_click_element(ad, label_text=None, label_resource_id=None):
    """Wait for a UI element to appear and click on it.

    This function locates a UI element on the screen by matching attributes of
    nodes in XML DOM, calculates a point's coordinates within the boundary of the
    element, and clicks on the point marked by the coordinates.

  Args:
    ad: AndroidDevice object.
    label_text: Identify the key value parameter
    label_text: Identifies the resource id
  """
    if label_resource_id is not None:
        ui_utils.wait_and_click(ad, text=label_text, resource_id=label_resource_id)
    else:
        ui_utils.wait_and_click(ad, text=label_text)
    time.sleep(WAIT_TIME_BETWEEN_STATE_CHECK)
