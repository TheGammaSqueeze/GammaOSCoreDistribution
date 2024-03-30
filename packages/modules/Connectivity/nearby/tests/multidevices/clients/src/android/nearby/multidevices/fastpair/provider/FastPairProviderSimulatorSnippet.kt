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

package android.nearby.multidevices.fastpair.provider

import android.annotation.TargetApi
import android.content.Context
import android.nearby.multidevices.fastpair.provider.controller.FastPairProviderSimulatorController
import android.nearby.multidevices.fastpair.provider.events.ProviderStatusEvents
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.mobly.snippet.Snippet
import com.google.android.mobly.snippet.rpc.AsyncRpc
import com.google.android.mobly.snippet.rpc.Rpc

/** Expose Mobly RPC methods for Python side to simulate fast pair provider role. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class FastPairProviderSimulatorSnippet : Snippet {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val fastPairProviderSimulatorController = FastPairProviderSimulatorController(context)

    /** Sets up the Fast Pair provider simulator. */
    @AsyncRpc(description = "Sets up FP provider simulator.")
    fun setupProviderSimulator(callbackId: String) {
        fastPairProviderSimulatorController.setupProviderSimulator(ProviderStatusEvents(callbackId))
    }

    /**
     * Starts model id advertising for scanning and initial pairing.
     *
     * @param callbackId the callback ID corresponding to the
     * [FastPairProviderSimulatorSnippet#startProviderSimulator] call that started the scanning.
     * @param modelId a 3-byte hex string for seeker side to recognize the device (ex: 0x00000C).
     * @param antiSpoofingKeyString a public key for registered headsets.
     */
    @AsyncRpc(description = "Starts model id advertising for scanning and initial pairing.")
    fun startModelIdAdvertising(
        callbackId: String,
        modelId: String,
        antiSpoofingKeyString: String
    ) {
        fastPairProviderSimulatorController.startModelIdAdvertising(
            modelId,
            antiSpoofingKeyString,
            ProviderStatusEvents(callbackId)
        )
    }

    /** Tears down the Fast Pair provider simulator. */
    @Rpc(description = "Tears down FP provider simulator.")
    fun teardownProviderSimulator() {
        fastPairProviderSimulatorController.teardownProviderSimulator()
    }

    /** Gets BLE mac address of the Fast Pair provider simulator. */
    @Rpc(description = "Gets BLE mac address of the Fast Pair provider simulator.")
    fun getBluetoothLeAddress(): String {
        return fastPairProviderSimulatorController.getProviderSimulatorBleAddress()
    }

    /** Gets the latest account key received on the Fast Pair provider simulator */
    @Rpc(description = "Gets the latest account key received on the Fast Pair provider simulator.")
    fun getLatestReceivedAccountKey(): String? {
        return fastPairProviderSimulatorController.getLatestReceivedAccountKey()
    }
}
