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

package android.hardware.drm;

@VintfStability
@Backing(type="int")
enum OfflineLicenseState {
    /**
     * Offline license state is unknown
     */
    UNKNOWN,
    /**
     * Offline license state is usable, the keys are usable for decryption.
     */
    USABLE,
    /**
     * Offline license state is inactive, the keys have been marked for
     * release using {@link #getKeyRequest} with KEY_TYPE_RELEASE but the
     * key response has not been received.
     */
    INACTIVE,
}
