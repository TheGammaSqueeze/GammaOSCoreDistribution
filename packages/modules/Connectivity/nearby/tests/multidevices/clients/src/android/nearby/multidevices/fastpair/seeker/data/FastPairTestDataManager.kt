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

package android.nearby.multidevices.fastpair.seeker.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nearby.fastpair.seeker.ACTION_RESET_TEST_DATA_CACHE
import android.nearby.fastpair.seeker.ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.ACTION_WRITE_ACCOUNT_KEY_DEVICE_METADATA
import android.nearby.fastpair.seeker.DATA_JSON_STRING_KEY
import android.nearby.fastpair.seeker.DATA_MODEL_ID_STRING_KEY
import android.nearby.fastpair.seeker.FastPairTestDataCache
import android.util.Log

/** Manage local FastPairTestDataCache and send to/sync from the remote cache in data provider. */
class FastPairTestDataManager(private val context: Context) : BroadcastReceiver() {
    val testDataCache = FastPairTestDataCache()
    var listener: EventListener? = null

    /** Puts a model id to FastPairAntispoofKeyDeviceMetadata pair into local and remote cache.
     *
     * @param modelId a string of model id to be associated with.
     * @param json a string of FastPairAntispoofKeyDeviceMetadata JSON object.
     */
    fun sendAntispoofKeyDeviceMetadata(modelId: String, json: String) {
        Intent().also { intent ->
            intent.action = ACTION_SEND_ANTISPOOF_KEY_DEVICE_METADATA
            intent.putExtra(DATA_MODEL_ID_STRING_KEY, modelId)
            intent.putExtra(DATA_JSON_STRING_KEY, json)
            context.sendBroadcast(intent)
        }
        testDataCache.putAntispoofKeyDeviceMetadata(modelId, json)
    }

    /** Puts account key device metadata array to local and remote cache.
     *
     * @param json a string of FastPairAccountKeyDeviceMetadata JSON array.
     */
    fun sendAccountKeyDeviceMetadataJsonArray(json: String) {
        Intent().also { intent ->
            intent.action = ACTION_SEND_ACCOUNT_KEY_DEVICE_METADATA
            intent.putExtra(DATA_JSON_STRING_KEY, json)
            context.sendBroadcast(intent)
        }
        testDataCache.putAccountKeyDeviceMetadataJsonArray(json)
    }

    /** Clears local and remote cache. */
    fun sendResetCache() {
        context.sendBroadcast(Intent(ACTION_RESET_TEST_DATA_CACHE))
        testDataCache.reset()
    }

    /**
     * Callback method for receiving Intent broadcast from FastPairTestDataProvider.
     *
     * See [BroadcastReceiver#onReceive].
     *
     * @param context the Context in which the receiver is running.
     * @param intent the Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WRITE_ACCOUNT_KEY_DEVICE_METADATA -> {
                Log.d(TAG, "ACTION_WRITE_ACCOUNT_KEY_DEVICE_METADATA received!")
                val json = intent.getStringExtra(DATA_JSON_STRING_KEY)!!
                testDataCache.putAccountKeyDeviceMetadataJsonObject(json)
                listener?.onManageFastPairAccountDevice(json)
            }
            else -> Log.d(TAG, "Unknown action received!")
        }
    }

    fun registerDataReceiveListener(listener: EventListener) {
        this.listener = listener
        val bondStateFilter = IntentFilter(ACTION_WRITE_ACCOUNT_KEY_DEVICE_METADATA)
        context.registerReceiver(this, bondStateFilter)
    }

    fun unregisterDataReceiveListener() {
        this.listener = null
        context.unregisterReceiver(this)
    }

    /** Interface for listening the data receive from the remote cache in data provider. */
    interface EventListener {
        /** Reports a FastPairAccountKeyDeviceMetadata write into the cache.
         *
         * @param json the FastPairAccountKeyDeviceMetadata as JSON object string.
         */
        fun onManageFastPairAccountDevice(json: String)
    }

    companion object {
        private const val TAG = "FastPairTestDataManager"
    }
}