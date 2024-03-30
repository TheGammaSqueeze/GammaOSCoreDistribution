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

package android.nearby.fastpair.seeker.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nearby.FastPairAccountKeyDeviceMetadata
import android.nearby.fastpair.seeker.ACTION_RESET_TEST_DATA_CACHE
import android.nearby.fastpair.seeker.ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.ACTION_WRITE_ACCOUNT_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.DATA_JSON_STRING_KEY
import android.nearby.fastpair.seeker.DATA_MODEL_ID_STRING_KEY
import android.nearby.fastpair.seeker.FastPairTestDataCache
import android.util.Log

/** Manage local FastPairTestDataCache and receive/update the remote cache in test snippet. */
class FastPairTestDataManager(private val context: Context) : BroadcastReceiver() {
    val testDataCache = FastPairTestDataCache()

    /** Writes a FastPairAccountKeyDeviceMetadata into local and remote cache.
     *
     * @param accountKeyDeviceMetadata the FastPairAccountKeyDeviceMetadata to write.
     * @return a json object string of the accountKeyDeviceMetadata.
     */
    fun writeAccountKeyDeviceMetadata(
        accountKeyDeviceMetadata: FastPairAccountKeyDeviceMetadata
    ): String {
        testDataCache.putAccountKeyDeviceMetadata(accountKeyDeviceMetadata)

        val json =
            testDataCache.dumpAccountKeyDeviceMetadataAsJson(accountKeyDeviceMetadata)
        Intent().also { intent ->
            intent.action = ACTION_WRITE_ACCOUNT_KEY_DEVICE_METADATA
            intent.putExtra(DATA_JSON_STRING_KEY, json)
            context.sendBroadcast(intent)
        }
        return json
    }

    /**
     * Callback method for receiving Intent broadcast from test snippet.
     *
     * See [BroadcastReceiver#onReceive].
     *
     * @param context the Context in which the receiver is running.
     * @param intent the Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA -> {
                Log.d(TAG, "ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA received!")
                val modelId = intent.getStringExtra(DATA_MODEL_ID_STRING_KEY)!!
                val json = intent.getStringExtra(DATA_JSON_STRING_KEY)!!
                testDataCache.putAntispoofKeyDeviceMetadata(modelId, json)
            }
            ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA -> {
                Log.d(TAG, "ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA received!")
                val json = intent.getStringExtra(DATA_JSON_STRING_KEY)!!
                testDataCache.putAccountKeyDeviceMetadataJsonArray(json)
            }
            ACTION_RESET_TEST_DATA_CACHE -> {
                Log.d(TAG, "ACTION_RESET_TEST_DATA_CACHE received!")
                testDataCache.reset()
            }
            else -> Log.d(TAG, "Unknown action received!")
        }
    }

    companion object {
        private const val TAG = "FastPairTestDataManager"
    }
}