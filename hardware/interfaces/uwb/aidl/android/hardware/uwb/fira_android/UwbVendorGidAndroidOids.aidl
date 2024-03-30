/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.uwb.fira_android;

/**
 * Android specific vendor command OIDs should be defined here.
 *
 * For use with Android GID - 0xC.
 */
@VintfStability
@Backing(type="byte")
enum UwbVendorGidAndroidOids {
    // Used by the command and response to get UWB power related stats.
    // Supported only if the UwbVendorCapabilityTlvTypes.SUPPORTED_POWER_STATS_QUERY
    // set to 1.
    ANDROID_GET_POWER_STATS = 0x0,
    // Used to set the current regulatory country code (determined usinag
    // SIM or hardcoded by OEM).
    ANDROID_SET_COUNTRY_CODE = 0x1,
}
