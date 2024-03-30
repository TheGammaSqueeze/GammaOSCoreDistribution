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

package android.nearby.fastpair.provider.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nearby.fastpair.provider.FastPairSimulator
import android.nearby.fastpair.provider.utils.Logger
import android.os.SystemClock
import android.provider.Settings

/** Controls the local Bluetooth adapter for Fast Pair testing. */
class BluetoothController(
    private val context: Context,
    private val listener: EventListener
) : BroadcastReceiver() {
    private val mLogger = Logger(TAG)
    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter!!
    private var remoteDevice: BluetoothDevice? = null
    private var remoteDeviceConnectionState: Int = BluetoothAdapter.STATE_DISCONNECTED
    private var a2dpSinkProxy: BluetoothProfile? = null

    /** Turns on the local Bluetooth adapter */
    fun enableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            bluetoothAdapter.enable()
            waitForBluetoothState(BluetoothAdapter.STATE_ON)
        }
    }

    /**
     * Sets the Input/Output capability of the device for both classic Bluetooth and BLE operations.
     * Note: In order to let changes take effect, this method will make sure the Bluetooth stack is
     * restarted by blocking calling thread.
     *
     * @param ioCapabilityClassic One of {@link #IO_CAPABILITY_IO}, {@link #IO_CAPABILITY_NONE},
     * ```
     *     {@link #IO_CAPABILITY_KBDISP} or more in {@link BluetoothAdapter}.
     * @param ioCapabilityBLE
     * ```
     * One of {@link #IO_CAPABILITY_IO}, {@link #IO_CAPABILITY_NONE}, {@link
     * ```
     *     #IO_CAPABILITY_KBDISP} or more in {@link BluetoothAdapter}.
     * ```
     */
    fun setIoCapability(ioCapabilityClassic: Int, ioCapabilityBLE: Int) {
        bluetoothAdapter.ioCapability = ioCapabilityClassic
        bluetoothAdapter.leIoCapability = ioCapabilityBLE

        // Toggling airplane mode on/off to restart Bluetooth stack and reset the BLE.
        try {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                TURN_AIRPLANE_MODE_ON
            )
        } catch (expectedOnNonCustomAndroid: SecurityException) {
            mLogger.log(
                expectedOnNonCustomAndroid,
                "Requires custom Android to toggle airplane mode"
            )
            // Fall back to turn off Bluetooth.
            bluetoothAdapter.disable()
        }
        waitForBluetoothState(BluetoothAdapter.STATE_OFF)
        try {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                TURN_AIRPLANE_MODE_OFF
            )
        } catch (expectedOnNonCustomAndroid: SecurityException) {
            mLogger.log(
                expectedOnNonCustomAndroid,
                "SecurityException while toggled airplane mode."
            )
        } finally {
            // Double confirm that Bluetooth is turned on.
            bluetoothAdapter.enable()
        }
        waitForBluetoothState(BluetoothAdapter.STATE_ON)
    }

    /** Registers this Bluetooth state change receiver. */
    fun registerBluetoothStateReceiver() {
        val bondStateFilter =
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED).apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            }
        context.registerReceiver(
            this,
            bondStateFilter,
            /* broadcastPermission= */ null,
            /* scheduler= */ null
        )
    }

    /** Unregisters this Bluetooth state change receiver. */
    fun unregisterBluetoothStateReceiver() {
        context.unregisterReceiver(this)
    }

    /** Clears current remote device. */
    fun clearRemoteDevice() {
        remoteDevice = null
    }

    /** Gets current remote device. */
    fun getRemoteDevice(): BluetoothDevice? = remoteDevice

    /** Gets current remote device as string. */
    fun getRemoteDeviceAsString(): String = remoteDevice?.remoteDeviceToString() ?: "none"

    /** Connects the Bluetooth A2DP sink profile service. */
    fun connectA2DPSinkProfile() {
        // Get the A2DP proxy before continuing with initialization.
        bluetoothAdapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    // When Bluetooth turns off and then on again, this is called again. But we only care
                    // the first time. There doesn't seem to be a way to unregister our listener.
                    if (a2dpSinkProxy == null) {
                        a2dpSinkProxy = proxy
                        listener.onA2DPSinkProfileConnected()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {}
            },
            BluetoothProfile.A2DP_SINK
        )
    }

    /** Get the current Bluetooth scan mode of the local Bluetooth adapter. */
    fun getScanMode(): Int = bluetoothAdapter.scanMode

    /** Return true if the remote device is connected to the local adapter. */
    fun isConnected(): Boolean = remoteDeviceConnectionState == BluetoothAdapter.STATE_CONNECTED

    /** Return true if the remote device is bonded (paired) to the local adapter. */
    fun isPaired(): Boolean = bluetoothAdapter.bondedDevices.contains(remoteDevice)

    /** Gets the A2DP sink profile proxy. */
    fun getA2DPSinkProfileProxy(): BluetoothProfile? = a2dpSinkProxy

    /**
     * Callback method for receiving Intent broadcast of Bluetooth state.
     *
     * See [BroadcastReceiver#onReceive].
     *
     * @param context the Context in which the receiver is running.
     * @param intent the Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                // After a device starts bonding, we only pay attention to intents about that device.
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                remoteDevice =
                    when (bondState) {
                        BluetoothDevice.BOND_BONDING, BluetoothDevice.BOND_BONDED -> device
                        BluetoothDevice.BOND_NONE -> null
                        else -> remoteDevice
                    }
                mLogger.log(
                    "ACTION_BOND_STATE_CHANGED, the bound state of " +
                            "the remote device (%s) change to %s.",
                    remoteDevice?.remoteDeviceToString(),
                    bondState.bondStateToString()
                )
                listener.onBondStateChanged(bondState)
            }
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                remoteDeviceConnectionState =
                    intent.getIntExtra(
                        BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED
                    )
                mLogger.log(
                    "ACTION_CONNECTION_STATE_CHANGED, the new connectionState: %s",
                    remoteDeviceConnectionState
                )
                listener.onConnectionStateChanged(remoteDeviceConnectionState)
            }
            BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
                val scanMode =
                    intent.getIntExtra(
                        BluetoothAdapter.EXTRA_SCAN_MODE,
                        BluetoothAdapter.SCAN_MODE_NONE
                    )
                mLogger.log(
                    "ACTION_SCAN_MODE_CHANGED, the new scanMode: %s",
                    FastPairSimulator.scanModeToString(scanMode)
                )
                listener.onScanModeChange(scanMode)
            }
            else -> {}
        }
    }

    private fun waitForBluetoothState(state: Int) {
        while (bluetoothAdapter.state != state) {
            SystemClock.sleep(1000)
        }
    }

    private fun BluetoothDevice.remoteDeviceToString(): String = "${this.name}-${this.address}"

    private fun Int.bondStateToString(): String =
        when (this) {
            BluetoothDevice.BOND_NONE -> "BOND_NONE"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING"
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED"
            else -> "BOND_ERROR"
        }

    /** Interface for listening the events from Bluetooth controller. */
    interface EventListener {
        /** The callback for the first onServiceConnected of A2DP sink profile. */
        fun onA2DPSinkProfileConnected()

        /**
         * Reports the current bond state of the remote device.
         *
         * @param bondState the bond state of the remote device.
         */
        fun onBondStateChanged(bondState: Int)

        /**
         * Reports the current connection state of the remote device.
         *
         * @param connectionState the bond state of the remote device.
         */
        fun onConnectionStateChanged(connectionState: Int)

        /**
         * Reports the current scan mode of the local Adapter.
         *
         * @param mode the current scan mode of the local Adapter.
         */
        fun onScanModeChange(mode: Int)
    }

    companion object {
        private const val TAG = "BluetoothController"

        private const val TURN_AIRPLANE_MODE_OFF = 0
        private const val TURN_AIRPLANE_MODE_ON = 1
    }
}