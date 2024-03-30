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

package android.hardware.radio.voice;

@VintfStability
@Backing(type="int")
@JavaDerive(toString=true)
enum CdmaOtaProvisionStatus {
    SPL_UNLOCKED,
    SPC_RETRIES_EXCEEDED,
    A_KEY_EXCHANGED,
    SSD_UPDATED,
    NAM_DOWNLOADED,
    MDN_DOWNLOADED,
    IMSI_DOWNLOADED,
    PRL_DOWNLOADED,
    COMMITTED,
    OTAPA_STARTED,
    OTAPA_STOPPED,
    OTAPA_ABORTED,
}
