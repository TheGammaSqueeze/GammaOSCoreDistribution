# Lint as: python3
"""CTS-V Wifi test reimplemented in Mobly."""
import sys
import os.path

import logging
logging.basicConfig(filename="/tmp/wifi_aware_test_log.txt", level=logging.INFO)

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

WIFI_AWARE_SNIPPET_PATH = 'wifi_aware_snippet.apk'

WIFI_AWARE_SNIPPET_PACKAGE = 'com.google.snippet'

TEST_MESSAGE = 'test message!'

MESSAGE_ID = 1234


class WifiAwareTest(base_test.BaseTestClass):

  def setup_class(self):
    # Declare that two Android devices are needed.
    self.publisher, self.subscriber = self.register_controller(
      android_device, min_number=2)

    def setup_device(device):
      # Expect wifi_aware apk to be installed as it is configured to install
      # with the module configuration AndroidTest.xml on both devices.
      device.adb.shell([
          'pm', 'grant', WIFI_AWARE_SNIPPET_PACKAGE,
          'android.permission.ACCESS_FINE_LOCATION'
      ])
      device.load_snippet('wifi_aware_snippet', WIFI_AWARE_SNIPPET_PACKAGE)

    # Sets up devices in parallel to save time.
    utils.concurrent_exec(
        setup_device, ((self.publisher,), (self.subscriber,)),
        max_workers=2,
        raise_on_exception=True)

  def test_discovery_base_test_case(self):
    is_unsolicited = True
    is_ranging_required = False
    self.subscriber.wifi_aware_snippet.attach()
    self.publisher.wifi_aware_snippet.attach()

    self.publisher.wifi_aware_snippet.publish(is_unsolicited,
                                              is_ranging_required)
    self.subscriber.wifi_aware_snippet.subscribe(is_unsolicited,
                                                 is_ranging_required)

    self.subscriber.wifi_aware_snippet.sendMessage(MESSAGE_ID, TEST_MESSAGE)
    received_message = self.publisher.wifi_aware_snippet.receiveMessage()
    asserts.assert_equal(
        received_message, TEST_MESSAGE,
        'Message received by publisher does not match the message sent by subscriber.'
    )

if __name__ == '__main__':
  # Take test args
  index = sys.argv.index('--')
  sys.argv = sys.argv[:1] + sys.argv[index + 1:]

  test_runner.main()
