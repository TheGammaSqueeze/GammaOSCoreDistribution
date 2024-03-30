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

package android.nearby.fastpair.seeker.dataprovider

import android.accounts.Account
import android.content.IntentFilter
import android.nearby.FastPairDataProviderService
import android.nearby.FastPairEligibleAccount
import android.nearby.fastpair.seeker.ACTION_RESET_TEST_DATA_CACHE
import android.nearby.fastpair.seeker.ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.FAKE_TEST_ACCOUNT_NAME
import android.nearby.fastpair.seeker.data.FastPairTestDataManager
import android.util.Log

/**
 * Fast Pair Test Data Provider Service entry point for platform overlay.
 */
class FastPairTestDataProviderService : FastPairDataProviderService(TAG) {
    private lateinit var testDataManager: FastPairTestDataManager

    override fun onCreate() {
        Log.d(TAG, "onCreate()")
        testDataManager = FastPairTestDataManager(this)

        val bondStateFilter = IntentFilter(ACTION_RESET_TEST_DATA_CACHE).apply {
            addAction(ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA)
            addAction(ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA)
        }
        registerReceiver(testDataManager, bondStateFilter)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        unregisterReceiver(testDataManager)

        super.onDestroy()
    }

    override fun onLoadFastPairAntispoofKeyDeviceMetadata(
        request: FastPairAntispoofKeyDeviceMetadataRequest,
        callback: FastPairAntispoofKeyDeviceMetadataCallback
    ) {
        val requestedModelId = request.modelId.bytesToStringLowerCase()
        Log.d(TAG, "onLoadFastPairAntispoofKeyDeviceMetadata(modelId: $requestedModelId)")

        val fastPairAntispoofKeyDeviceMetadata =
            testDataManager.testDataCache.getAntispoofKeyDeviceMetadata(requestedModelId)
        if (fastPairAntispoofKeyDeviceMetadata != null) {
            callback.onFastPairAntispoofKeyDeviceMetadataReceived(
                fastPairAntispoofKeyDeviceMetadata
            )
        } else {
            Log.d(TAG, "No metadata available for $requestedModelId!")
            callback.onError(ERROR_CODE_BAD_REQUEST, "No metadata available for $requestedModelId")
        }
    }

    override fun onLoadFastPairAccountDevicesMetadata(
        request: FastPairAccountDevicesMetadataRequest,
        callback: FastPairAccountDevicesMetadataCallback
    ) {
        val requestedAccount = request.account
        val requestedAccountKeys = request.deviceAccountKeys
        Log.d(
            TAG, "onLoadFastPairAccountDevicesMetadata(" +
                    "account: $requestedAccount, accountKeys:$requestedAccountKeys)"
        )
        Log.d(TAG, testDataManager.testDataCache.dumpAccountKeyDeviceMetadataListAsJson())

        callback.onFastPairAccountDevicesMetadataReceived(
            testDataManager.testDataCache.getAccountKeyDeviceMetadataList()
        )
    }

    override fun onLoadFastPairEligibleAccounts(
        request: FastPairEligibleAccountsRequest,
        callback: FastPairEligibleAccountsCallback
    ) {
        Log.d(TAG, "onLoadFastPairEligibleAccounts()")
        callback.onFastPairEligibleAccountsReceived(ELIGIBLE_ACCOUNTS_TEST_CONSTANT)
    }

    override fun onManageFastPairAccount(
        request: FastPairManageAccountRequest,
        callback: FastPairManageActionCallback
    ) {
        val requestedAccount = request.account
        val requestType = request.requestType
        Log.d(TAG, "onManageFastPairAccount(account: $requestedAccount, requestType: $requestType)")

        callback.onSuccess()
    }

    override fun onManageFastPairAccountDevice(
        request: FastPairManageAccountDeviceRequest,
        callback: FastPairManageActionCallback
    ) {
        val requestedAccount = request.account
        val requestType = request.requestType
        val requestTypeString = if (requestType == MANAGE_REQUEST_ADD) "Add" else "Remove"
        val requestedAccountKeyDeviceMetadata = request.accountKeyDeviceMetadata
        Log.d(
            TAG,
            "onManageFastPairAccountDevice(requestedAccount: $requestedAccount, " +
                    "requestType: $requestTypeString,"
        )

        val requestedAccountKeyDeviceMetadataInJson =
            testDataManager.writeAccountKeyDeviceMetadata(requestedAccountKeyDeviceMetadata)
        Log.d(TAG, "requestedAccountKeyDeviceMetadata: $requestedAccountKeyDeviceMetadataInJson)")

        callback.onSuccess()
    }

    companion object {
        private const val TAG = "FastPairTestDataProviderService"
        private val ELIGIBLE_ACCOUNTS_TEST_CONSTANT = listOf(
            FastPairEligibleAccount.Builder()
                .setAccount(Account(FAKE_TEST_ACCOUNT_NAME, "FakeTestAccount"))
                .setOptIn(true)
                .build()
        )

        private fun ByteArray.bytesToStringLowerCase(): String =
            joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
    }
}
