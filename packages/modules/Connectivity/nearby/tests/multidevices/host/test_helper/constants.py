#  Copyright (C) 2022 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Default model ID to simulate on provider side.
DEFAULT_MODEL_ID = '00000c'

# Default public key to simulate as registered headsets.
DEFAULT_ANTI_SPOOFING_KEY = 'Cbj9eCJrTdDgSYxLkqtfADQi86vIaMvxJsQ298sZYWE='

# Default anti-spoof Key Device Metadata JSON file for data provider at seeker side.
DEFAULT_KDM_JSON_FILE = 'simulator_antispoofkey_devicemeta_json.txt'

# Time in seconds for events waiting according to Fast Pair certification guidelines:
# https://developers.google.com/nearby/fast-pair/certification-guideline
SETUP_TIMEOUT_SEC = 5
BECOME_DISCOVERABLE_TIMEOUT_SEC = 10
START_ADVERTISING_TIMEOUT_SEC = 5
SCAN_TIMEOUT_SEC = 5
HALF_SHEET_POPUP_TIMEOUT_SEC = 5
AVERAGE_PAIRING_TIMEOUT_SEC = 12

# The phone to simulate Fast Pair provider (like headphone) needs changes in Android system:
# 1. System permission check removal
# 2. Adjusts Bluetooth profile configurations
# The build fingerprint of the custom ROM for Fast Pair provider simulator.
FAST_PAIR_PROVIDER_SIMULATOR_BUILD_FINGERPRINT = (
    'google/bramble/bramble:Tiramisu/MASTER/eng.hylo.20211019.091550:userdebug/dev-keys')
