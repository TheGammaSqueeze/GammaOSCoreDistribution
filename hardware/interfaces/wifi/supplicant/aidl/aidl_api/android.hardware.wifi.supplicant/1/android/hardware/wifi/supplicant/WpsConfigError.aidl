/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package android.hardware.wifi.supplicant;
@Backing(type="int") @VintfStability
enum WpsConfigError {
  NO_ERROR = 0,
  OOB_IFACE_READ_ERROR = 1,
  DECRYPTION_CRC_FAILURE = 2,
  CHAN_24_NOT_SUPPORTED = 3,
  CHAN_50_NOT_SUPPORTED = 4,
  SIGNAL_TOO_WEAK = 5,
  NETWORK_AUTH_FAILURE = 6,
  NETWORK_ASSOC_FAILURE = 7,
  NO_DHCP_RESPONSE = 8,
  FAILED_DHCP_CONFIG = 9,
  IP_ADDR_CONFLICT = 10,
  NO_CONN_TO_REGISTRAR = 11,
  MULTIPLE_PBC_DETECTED = 12,
  ROGUE_SUSPECTED = 13,
  DEVICE_BUSY = 14,
  SETUP_LOCKED = 15,
  MSG_TIMEOUT = 16,
  REG_SESS_TIMEOUT = 17,
  DEV_PASSWORD_AUTH_FAILURE = 18,
  CHAN_60G_NOT_SUPPORTED = 19,
  PUBLIC_KEY_HASH_MISMATCH = 20,
}
