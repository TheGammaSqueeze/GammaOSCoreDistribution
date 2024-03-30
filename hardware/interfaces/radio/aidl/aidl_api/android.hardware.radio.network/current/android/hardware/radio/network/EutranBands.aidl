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

package android.hardware.radio.network;
@Backing(type="int") @JavaDerive(toString=true) @VintfStability
enum EutranBands {
  BAND_1 = 1,
  BAND_2 = 2,
  BAND_3 = 3,
  BAND_4 = 4,
  BAND_5 = 5,
  BAND_6 = 6,
  BAND_7 = 7,
  BAND_8 = 8,
  BAND_9 = 9,
  BAND_10 = 10,
  BAND_11 = 11,
  BAND_12 = 12,
  BAND_13 = 13,
  BAND_14 = 14,
  BAND_17 = 17,
  BAND_18 = 18,
  BAND_19 = 19,
  BAND_20 = 20,
  BAND_21 = 21,
  BAND_22 = 22,
  BAND_23 = 23,
  BAND_24 = 24,
  BAND_25 = 25,
  BAND_26 = 26,
  BAND_27 = 27,
  BAND_28 = 28,
  BAND_30 = 30,
  BAND_31 = 31,
  BAND_33 = 33,
  BAND_34 = 34,
  BAND_35 = 35,
  BAND_36 = 36,
  BAND_37 = 37,
  BAND_38 = 38,
  BAND_39 = 39,
  BAND_40 = 40,
  BAND_41 = 41,
  BAND_42 = 42,
  BAND_43 = 43,
  BAND_44 = 44,
  BAND_45 = 45,
  BAND_46 = 46,
  BAND_47 = 47,
  BAND_48 = 48,
  BAND_65 = 65,
  BAND_66 = 66,
  BAND_68 = 68,
  BAND_70 = 70,
  BAND_49 = 49,
  BAND_50 = 50,
  BAND_51 = 51,
  BAND_52 = 52,
  BAND_53 = 53,
  BAND_71 = 71,
  BAND_72 = 72,
  BAND_73 = 73,
  BAND_74 = 74,
  BAND_85 = 85,
  BAND_87 = 87,
  BAND_88 = 88,
}
