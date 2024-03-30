/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.multidevices.fastpair.seeker.events

import android.nearby.NearbyDevice
import android.nearby.ScanCallback
import com.google.android.mobly.snippet.util.postSnippetEvent

/** The Mobly snippet events to report to the Python side. */
class ScanCallbackEvents(private val callbackId: String) : ScanCallback {

    override fun onDiscovered(device: NearbyDevice) {
        postSnippetEvent(callbackId, "onDiscovered") {
            putString("device", device.toString())
        }
    }

    override fun onUpdated(device: NearbyDevice) {
        postSnippetEvent(callbackId, "onUpdated") {
            putString("device", device.toString())
        }
    }

    override fun onLost(device: NearbyDevice) {
        postSnippetEvent(callbackId, "onLost") {
            putString("device", device.toString())
        }
    }
}