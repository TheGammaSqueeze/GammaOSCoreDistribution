# Lint as: python3
"""Porting UWB tests from mobly."""
import sys
import time

import logging
logging.basicConfig(filename="/tmp/uwb_test_log.txt", level=logging.INFO)

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from pprint import pprint

UWB_SNIPPET_PACKAGE = 'com.google.snippet.uwb'

class UwbManagerTest(base_test.BaseTestClass):

  def setup_class(self):
    # Declare that two Android devices are needed.
    self.initiator, self.responder = self.register_controller(
        android_device, min_number=2)

    def setup_device(device):
      # Expect uwb apk to be installed as it is configured to install
      # with the module configuration AndroidTest.xml on both devices.
      device.adb.shell([
          'pm', 'grant', UWB_SNIPPET_PACKAGE,
          'android.permission.UWB_RANGING'
      ])
      device.load_snippet('uwb_snippet', UWB_SNIPPET_PACKAGE)

    # Sets up devices in parallel to save time.
    utils.concurrent_exec(
        setup_device, ((self.initiator,), (self.responder,)),
        max_workers=2,
        raise_on_exception=True)


  def test_default_uwb_state(self):
    """Verifies default UWB state is On after flashing the device."""
    asserts.assert_true(self.initiator.uwb_snippet.isUwbEnabled(),
                        "UWB state: Off; Expected: On.")
    asserts.assert_true(self.responder.uwb_snippet.isUwbEnabled(),
                        "UWB state: Off; Expected: On.")

  def test_uwb_toggle(self):
      """Verifies UWB toggle on/off """
      self.initiator.uwb_snippet.setUwbEnabled(False)
      self.responder.uwb_snippet.setUwbEnabled(False)
      # TODO: Use callback to wait for toggle off completion.
      time.sleep(5)
      asserts.assert_false(self.initiator.uwb_snippet.isUwbEnabled(),
                          "UWB state: Off; Expected: On.")
      asserts.assert_false(self.responder.uwb_snippet.isUwbEnabled(),
                          "UWB state: Off; Expected: On.")

      self.initiator.uwb_snippet.setUwbEnabled(True)
      self.responder.uwb_snippet.setUwbEnabled(True)
      # TODO: Use callback to wait for toggle off completion.
      time.sleep(5)
      asserts.assert_true(self.initiator.uwb_snippet.isUwbEnabled(),
                          "UWB state: Off; Expected: On.")
      asserts.assert_true(self.responder.uwb_snippet.isUwbEnabled(),
                          "UWB state: Off; Expected: On.")

if __name__ == '__main__':
  # Take test args
  index = sys.argv.index('--')
  sys.argv = sys.argv[:1] + sys.argv[index + 1:]

  test_runner.main()
