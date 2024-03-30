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

package android.nearby.multidevices.fastpair.provider.controller

import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.nearby.fastpair.provider.FastPairSimulator
import android.nearby.fastpair.provider.bluetooth.BluetoothController
import com.google.android.mobly.snippet.util.Log
import com.google.common.io.BaseEncoding.base64

class FastPairProviderSimulatorController(private val context: Context) :
    FastPairSimulator.AdvertisingChangedCallback, BluetoothController.EventListener {
    private lateinit var bluetoothController: BluetoothController
    private lateinit var eventListener: EventListener
    private var simulator: FastPairSimulator? = null

    fun setupProviderSimulator(listener: EventListener) {
        eventListener = listener

        bluetoothController = BluetoothController(context, this)
        bluetoothController.registerBluetoothStateReceiver()
        bluetoothController.enableBluetooth()
        bluetoothController.connectA2DPSinkProfile()
    }

    fun teardownProviderSimulator() {
        simulator?.destroy()
        bluetoothController.unregisterBluetoothStateReceiver()
    }

    fun startModelIdAdvertising(
        modelId: String,
        antiSpoofingKeyString: String,
        listener: EventListener
    ) {
        eventListener = listener

        val antiSpoofingKey = base64().decode(antiSpoofingKeyString)
        simulator = FastPairSimulator(
            context, FastPairSimulator.Options.builder(modelId)
                .setAdvertisingModelId(modelId)
                .setBluetoothAddress(null)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setAdvertisingChangedCallback(this)
                .setAntiSpoofingPrivateKey(antiSpoofingKey)
                .setUseRandomSaltForAccountKeyRotation(false)
                .setDataOnlyConnection(false)
                .setShowsPasskeyConfirmation(false)
                .setRemoveAllDevicesDuringPairing(true)
                .build()
        )
    }

    fun getProviderSimulatorBleAddress() = simulator!!.bleAddress!!

    fun getLatestReceivedAccountKey() =
        simulator!!.accountKey?.let { base64().encode(it.toByteArray()) }

    /**
     * Called when we change our BLE advertisement.
     *
     * @param isAdvertising the advertising status.
     */
    override fun onAdvertisingChanged(isAdvertising: Boolean) {
        Log.i("FastPairSimulator onAdvertisingChanged(isAdvertising: $isAdvertising)")
        eventListener.onAdvertisingChange(isAdvertising)
    }

    /** The callback for the first onServiceConnected of A2DP sink profile. */
    override fun onA2DPSinkProfileConnected() {
        eventListener.onA2DPSinkProfileConnected()
    }

    /**
     * Reports the current bond state of the remote device.
     *
     * @param bondState the bond state of the remote device.
     */
    override fun onBondStateChanged(bondState: Int) {
    }

    /**
     * Reports the current connection state of the remote device.
     *
     * @param connectionState the bond state of the remote device.
     */
    override fun onConnectionStateChanged(connectionState: Int) {
    }

    /**
     * Reports the current scan mode of the local Adapter.
     *
     * @param mode the current scan mode of the local Adapter.
     */
    override fun onScanModeChange(mode: Int) {
        eventListener.onScanModeChange(FastPairSimulator.scanModeToString(mode))
    }

    /** Interface for listening the events from Fast Pair Provider Simulator. */
    interface EventListener {
        /** Reports the first onServiceConnected of A2DP sink profile. */
        fun onA2DPSinkProfileConnected()

        /**
         * Reports the current scan mode of the local Adapter.
         *
         * @param mode the current scan mode in string.
         */
        fun onScanModeChange(mode: String)

        /**
         * Indicates the advertising state of the Fast Pair provider simulator has changed.
         *
         * @param isAdvertising the current advertising state, true if advertising otherwise false.
         */
        fun onAdvertisingChange(isAdvertising: Boolean)
    }
}