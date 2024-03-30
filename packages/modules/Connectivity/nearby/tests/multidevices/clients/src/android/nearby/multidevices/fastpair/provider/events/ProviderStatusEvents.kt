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

package android.nearby.multidevices.fastpair.provider.events

import android.nearby.multidevices.fastpair.provider.controller.FastPairProviderSimulatorController
import com.google.android.mobly.snippet.util.postSnippetEvent

/** The Mobly snippet events to report to the Python side. */
class ProviderStatusEvents(private val callbackId: String) :
    FastPairProviderSimulatorController.EventListener {

    /** Reports the first onServiceConnected of A2DP sink profile. */
    override fun onA2DPSinkProfileConnected() {
        postSnippetEvent(callbackId, "onA2DPSinkProfileConnected") {}
    }

    /**
     * Indicates the Bluetooth scan mode of the Fast Pair provider simulator has changed.
     *
     * @param mode the current scan mode in String mapping by [FastPairSimulator#scanModeToString].
     */
    override fun onScanModeChange(mode: String) {
        postSnippetEvent(callbackId, "onScanModeChange") { putString("mode", mode) }
    }

    /**
     * Indicates the advertising state of the Fast Pair provider simulator has changed.
     *
     * @param isAdvertising the current advertising state, true if advertising otherwise false.
     */
    override fun onAdvertisingChange(isAdvertising: Boolean) {
        postSnippetEvent(callbackId, "onAdvertisingChange") {
            putBoolean("isAdvertising", isAdvertising)
        }
    }
}