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
package android.platform.helpers.foldable

import android.hardware.Sensor
import android.hardware.devicestate.DeviceStateManager
import android.hardware.devicestate.DeviceStateRequest
import android.platform.test.rule.isLargeScreen
import android.platform.uiautomator_helpers.DeviceHelpers.isScreenOnSettled
import android.platform.uiautomator_helpers.DeviceHelpers.printInstrumentationStatus
import android.platform.uiautomator_helpers.DeviceHelpers.uiDevice
import android.platform.uiautomator_helpers.TracingUtils.trace
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.util.Log
import androidx.annotation.FloatRange
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.R
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates.notNull
import org.junit.Assume.assumeTrue

/** Helper to set the folded state to a device. */
internal class FoldableDeviceController {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val resources = context.resources
    private val deviceStateManager = context.getSystemService(DeviceStateManager::class.java)!!
    private val hingeAngleSensor = SensorInjectionController(Sensor.TYPE_HINGE_ANGLE)

    private var foldedState by notNull<Int>()
    private var unfoldedState by notNull<Int>()
    // [currentState] is meant to be not null only when there is an override active.
    private var currentState: Int? = null

    private var deviceStateLatch = CountDownLatch(1)
    private var pendingRequest: DeviceStateRequest? = null

    /** Sets device state to folded. */
    fun fold() {
        trace("FoldableDeviceController#fold") {
            printInstrumentationStatus(TAG, "Folding")
            setDeviceState(foldedState)
        }
    }

    /** Sets device state to an unfolded state. */
    fun unfold() {
        trace("FoldableDeviceController#unfold") {
            printInstrumentationStatus(TAG, "Unfolding")
            setDeviceState(unfoldedState)
        }
    }

    /** Removes the override on the device state. */
    private fun resetDeviceState() {
        printInstrumentationStatus(TAG, "resetDeviceState")
        deviceStateManager.cancelBaseStateOverride()
        // This might cause the screen to turn off if the default state is folded.
        if (!uiDevice.isScreenOnSettled) {
            uiDevice.wakeUp()
            ensureThat("screen is on after cancelling base state override.") { uiDevice.isScreenOn }
        }
    }

    /** Fetches folded and unfolded state identifier from the device. */
    fun init() {
        findFoldedUnfoldedStates()
        currentState = if (isLargeScreen()) unfoldedState else foldedState
        Log.d(TAG, "Initial state. Folded=$isFolded")
        hingeAngleSensor.init()
    }

    fun uninit() {
        resetDeviceState()
        hingeAngleSensor.uninit()
    }

    val isFolded: Boolean
        get() {
            check(currentState != null) {
                "Trying to get the current state while there is no state override set."
            }
            return currentState == foldedState
        }

    fun setHingeAngle(@FloatRange(from = 0.0, to = 180.0) angle: Float) {
        hingeAngleSensor.setValue(angle)
    }

    private fun findFoldedUnfoldedStates() {
        val foldedStates = resources.getIntArray(R.array.config_foldedDeviceStates)
        assumeTrue("Skipping on non-foldable devices", foldedStates.isNotEmpty())
        foldedState = foldedStates[0]
        unfoldedState =
            deviceStateManager.supportedStates.firstOrNull { it != foldedState }
                ?: throw IllegalStateException("Unfolded state not found.")
    }

    private fun setDeviceState(state: Int) {
        if (currentState == state) {
            Log.e(TAG, "setting device state to the same state already set.")
            return
        }
        deviceStateLatch = CountDownLatch(1)
        val request = DeviceStateRequest.newBuilder(state).build()
        pendingRequest = request
        trace("Requesting base state override to ${state.desc()}") {
            deviceStateManager.requestBaseStateOverride(
                request,
                context.mainExecutor,
                deviceStateRequestCallback
            )
            deviceStateLatch.await { "Device state didn't change within the timeout" }
            ensureStateSet(state)
        }
        Log.d(TAG, "Device state set to ${state.desc()}")
    }

    private fun ensureStateSet(state: Int) {
        when (state) {
            foldedState -> ensureThat("Device folded") { !isLargeScreen() }
            unfoldedState -> ensureThat("Device unfolded") { isLargeScreen() }
            else -> TODO("Implement a way to know if states other than un/folded are set.")
        }
    }

    private fun Int.desc() =
        when (this) {
            foldedState -> "Folded"
            unfoldedState -> "Unfolded"
            else -> "unknown"
        }

    private fun CountDownLatch.await(error: () -> String) {
        check(this.await(DEVICE_STATE_MAX_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), error)
    }

    private val deviceStateRequestCallback =
        object : DeviceStateRequest.Callback {
            override fun onRequestActivated(request: DeviceStateRequest) {
                Log.d(TAG, "Request activated: ${request.state.desc()}")
                if (request == pendingRequest) {
                    deviceStateLatch.countDown()
                }
                currentState = request.state
            }

            override fun onRequestCanceled(request: DeviceStateRequest) {
                Log.d(TAG, "Request cancelled: ${request.state.desc()}")
                if (currentState == request.state) {
                    currentState = null
                }
            }

            override fun onRequestSuspended(request: DeviceStateRequest) {
                Log.d(TAG, "Request suspended: ${request.state.desc()}")
                if (currentState == request.state) {
                    currentState = null
                }
            }
        }

    private companion object {
        const val TAG = "FoldableController"
        val DEVICE_STATE_MAX_TIMEOUT = Duration.ofSeconds(10)
    }
}
