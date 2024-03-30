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

package android.nearby.multidevices.fastpair.seeker

import android.content.Context
import android.nearby.FastPairDeviceMetadata
import android.nearby.NearbyManager
import android.nearby.ScanCallback
import android.nearby.ScanRequest
import android.nearby.fastpair.seeker.FAKE_TEST_ACCOUNT_NAME
import android.nearby.integration.ui.CheckNearbyHalfSheetUiTest
import android.nearby.integration.ui.DismissNearbyHalfSheetUiTest
import android.nearby.integration.ui.PairByNearbyHalfSheetUiTest
import android.nearby.multidevices.fastpair.seeker.data.FastPairTestDataManager
import android.nearby.multidevices.fastpair.seeker.events.PairingCallbackEvents
import android.nearby.multidevices.fastpair.seeker.events.ScanCallbackEvents
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.android.mobly.snippet.Snippet
import com.google.android.mobly.snippet.rpc.AsyncRpc
import com.google.android.mobly.snippet.rpc.Rpc
import com.google.android.mobly.snippet.util.Log

/** Expose Mobly RPC methods for Python side to test fast pair seeker role. */
class FastPairSeekerSnippet : Snippet {
    private val appContext = ApplicationProvider.getApplicationContext<Context>()
    private val nearbyManager = appContext.getSystemService(Context.NEARBY_SERVICE) as NearbyManager
    private val fastPairTestDataManager = FastPairTestDataManager(appContext)
    private lateinit var scanCallback: ScanCallback

    /**
     * Starts scanning as a Fast Pair seeker to find provider devices.
     *
     * @param callbackId the callback ID corresponding to the {@link FastPairSeekerSnippet#startScan}
     * call that started the scanning.
     */
    @AsyncRpc(description = "Starts scanning as Fast Pair seeker to find provider devices.")
    fun startScan(callbackId: String) {
        val scanRequest = ScanRequest.Builder()
            .setScanMode(ScanRequest.SCAN_MODE_LOW_LATENCY)
            .setScanType(ScanRequest.SCAN_TYPE_FAST_PAIR)
            .setBleEnabled(true)
            .build()
        scanCallback = ScanCallbackEvents(callbackId)

        Log.i("Start Fast Pair scanning via BLE...")
        nearbyManager.startScan(scanRequest, /* executor */ { it.run() }, scanCallback)
    }

    /** Stops the Fast Pair seeker scanning. */
    @Rpc(description = "Stops the Fast Pair seeker scanning.")
    fun stopScan() {
        Log.i("Stop Fast Pair scanning.")
        nearbyManager.stopScan(scanCallback)
    }

    /** Waits and asserts the HalfSheet showed for Fast Pair pairing.
     *
     * @param modelId the expected model id to be associated with the HalfSheet.
     * @param timeout the number of seconds to wait before giving up.
     */
    @Rpc(description = "Waits the HalfSheet showed for Fast Pair pairing.")
    fun waitAndAssertHalfSheetShowed(modelId: String, timeout: Int) {
        Log.i("Waits and asserts the HalfSheet showed for Fast Pair model $modelId.")

        val deviceMetadata: FastPairDeviceMetadata =
            fastPairTestDataManager.testDataCache.getFastPairDeviceMetadata(modelId)
                ?: throw IllegalArgumentException(
                    "Can't find $modelId-FastPairAntispoofKeyDeviceMetadata pair in " +
                            "FastPairTestDataCache."
                )
        val deviceName = deviceMetadata.name!!
        val initialPairingDescriptionTemplateText = deviceMetadata.initialPairingDescription!!

        CheckNearbyHalfSheetUiTest().apply {
            updateTestArguments(
                waitHalfSheetPopupTimeoutSeconds = timeout,
                halfSheetTitleText = deviceName,
                halfSheetSubtitleText = initialPairingDescriptionTemplateText.format(
                    deviceName,
                    FAKE_TEST_ACCOUNT_NAME
                )
            )
            checkNearbyHalfSheetUi()
        }
    }

    /** Puts a model id to FastPairAntispoofKeyDeviceMetadata pair into test data cache.
     *
     * @param modelId a string of model id to be associated with.
     * @param json a string of FastPairAntispoofKeyDeviceMetadata JSON object.
     */
    @Rpc(
        description =
        "Puts a model id to FastPairAntispoofKeyDeviceMetadata pair into test data cache."
    )
    fun putAntispoofKeyDeviceMetadata(modelId: String, json: String) {
        Log.i("Puts a model id to FastPairAntispoofKeyDeviceMetadata pair into test data cache.")
        fastPairTestDataManager.sendAntispoofKeyDeviceMetadata(modelId, json)
    }

    /** Puts an array of FastPairAccountKeyDeviceMetadata into test data cache.
     *
     * @param json a string of FastPairAccountKeyDeviceMetadata JSON array.
     */
    @Rpc(description = "Puts an array of FastPairAccountKeyDeviceMetadata into test data cache.")
    fun putAccountKeyDeviceMetadata(json: String) {
        Log.i("Puts an array of FastPairAccountKeyDeviceMetadata into test data cache.")
        fastPairTestDataManager.sendAccountKeyDeviceMetadataJsonArray(json)
    }

    /** Dumps all FastPairAccountKeyDeviceMetadata from the test data cache. */
    @Rpc(description = "Dumps all FastPairAccountKeyDeviceMetadata from the test data cache.")
    fun dumpAccountKeyDeviceMetadata(): String {
        Log.i("Dumps all FastPairAccountKeyDeviceMetadata from the test data cache.")
        return fastPairTestDataManager.testDataCache.dumpAccountKeyDeviceMetadataListAsJson()
    }

    /** Writes into {@link Settings} whether Fast Pair scan is enabled.
     *
     * @param enable whether the Fast Pair scan should be enabled.
     */
    @Rpc(description = "Writes into Settings whether Fast Pair scan is enabled.")
    fun setFastPairScanEnabled(enable: Boolean) {
        Log.i("Writes into Settings whether Fast Pair scan is enabled.")
        // TODO(b/228406038): Change back to use NearbyManager.setFastPairScanEnabled once un-hide.
        val resolver = appContext.contentResolver
        Settings.Secure.putInt(resolver, "fast_pair_scan_enabled", if (enable) 1 else 0)
    }

    /** Dismisses the half sheet UI if showed. */
    @Rpc(description = "Dismisses the half sheet UI if showed.")
    fun dismissHalfSheet() {
        Log.i("Dismisses the half sheet UI if showed.")

        DismissNearbyHalfSheetUiTest().dismissHalfSheet()
    }

    /** Starts pairing by interacting with half sheet UI.
     *
     * @param callbackId the callback ID corresponding to the
     * {@link FastPairSeekerSnippet#startPairing} call that started the pairing.
     */
    @AsyncRpc(description = "Starts pairing by interacting with half sheet UI.")
    fun startPairing(callbackId: String) {
        Log.i("Starts pairing by interacting with half sheet UI.")

        PairByNearbyHalfSheetUiTest().clickConnectButton()
        fastPairTestDataManager.registerDataReceiveListener(PairingCallbackEvents(callbackId))
    }

    /** Invokes when the snippet runner shutting down. */
    override fun shutdown() {
        super.shutdown()

        Log.i("Resets the Fast Pair test data cache.")
        fastPairTestDataManager.unregisterDataReceiveListener()
        fastPairTestDataManager.sendResetCache()
    }
}