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

package android.nearby.fastpair.seeker

import android.nearby.FastPairAccountKeyDeviceMetadata
import android.nearby.FastPairAntispoofKeyDeviceMetadata
import android.nearby.FastPairDeviceMetadata
import android.nearby.FastPairDiscoveryItem
import com.google.common.io.BaseEncoding
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

/** Manage a cache of Fast Pair test data for testing. */
class FastPairTestDataCache {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val accountKeyDeviceMetadataList = mutableListOf<FastPairAccountKeyDeviceMetadata>()
    private val antispoofKeyDeviceMetadataDataMap =
        mutableMapOf<String, FastPairAntispoofKeyDeviceMetadataData>()

    fun putAccountKeyDeviceMetadataJsonArray(json: String) {
        accountKeyDeviceMetadataList +=
            gson.fromJson(json, Array<FastPairAccountKeyDeviceMetadataData>::class.java)
                .map { it.toFastPairAccountKeyDeviceMetadata() }
    }

    fun putAccountKeyDeviceMetadataJsonObject(json: String) {
        accountKeyDeviceMetadataList +=
            gson.fromJson(json, FastPairAccountKeyDeviceMetadataData::class.java)
                .toFastPairAccountKeyDeviceMetadata()
    }

    fun putAccountKeyDeviceMetadata(accountKeyDeviceMetadata: FastPairAccountKeyDeviceMetadata) {
        accountKeyDeviceMetadataList += accountKeyDeviceMetadata
    }

    fun getAccountKeyDeviceMetadataList(): List<FastPairAccountKeyDeviceMetadata> =
        accountKeyDeviceMetadataList.toList()

    fun dumpAccountKeyDeviceMetadataAsJson(metadata: FastPairAccountKeyDeviceMetadata): String =
        gson.toJson(FastPairAccountKeyDeviceMetadataData(metadata))

    fun dumpAccountKeyDeviceMetadataListAsJson(): String =
        gson.toJson(accountKeyDeviceMetadataList.map { FastPairAccountKeyDeviceMetadataData(it) })

    fun putAntispoofKeyDeviceMetadata(modelId: String, json: String) {
        antispoofKeyDeviceMetadataDataMap[modelId] =
            gson.fromJson(json, FastPairAntispoofKeyDeviceMetadataData::class.java)
    }

    fun getAntispoofKeyDeviceMetadata(modelId: String): FastPairAntispoofKeyDeviceMetadata? {
        return antispoofKeyDeviceMetadataDataMap[modelId]?.toFastPairAntispoofKeyDeviceMetadata()
    }

    fun getFastPairDeviceMetadata(modelId: String): FastPairDeviceMetadata? =
        antispoofKeyDeviceMetadataDataMap[modelId]?.deviceMeta?.toFastPairDeviceMetadata()

    fun reset() {
        accountKeyDeviceMetadataList.clear()
        antispoofKeyDeviceMetadataDataMap.clear()
    }

    data class FastPairAccountKeyDeviceMetadataData(
        @SerializedName("account_key") val accountKey: String?,
        @SerializedName("sha256_account_key_public_address") val accountKeyPublicAddress: String?,
        @SerializedName("fast_pair_device_metadata") val deviceMeta: FastPairDeviceMetadataData?,
        @SerializedName("fast_pair_discovery_item") val discoveryItem: FastPairDiscoveryItemData?
    ) {
        constructor(meta: FastPairAccountKeyDeviceMetadata) : this(
            accountKey = meta.deviceAccountKey?.base64Encode(),
            accountKeyPublicAddress = meta.sha256DeviceAccountKeyPublicAddress?.base64Encode(),
            deviceMeta = meta.fastPairDeviceMetadata?.let { FastPairDeviceMetadataData(it) },
            discoveryItem = meta.fastPairDiscoveryItem?.let { FastPairDiscoveryItemData(it) }
        )

        fun toFastPairAccountKeyDeviceMetadata(): FastPairAccountKeyDeviceMetadata {
            return FastPairAccountKeyDeviceMetadata.Builder()
                .setDeviceAccountKey(accountKey?.base64Decode())
                .setSha256DeviceAccountKeyPublicAddress(accountKeyPublicAddress?.base64Decode())
                .setFastPairDeviceMetadata(deviceMeta?.toFastPairDeviceMetadata())
                .setFastPairDiscoveryItem(discoveryItem?.toFastPairDiscoveryItem())
                .build()
        }
    }

    data class FastPairAntispoofKeyDeviceMetadataData(
        @SerializedName("anti_spoofing_public_key_str") val antispoofPublicKey: String?,
        @SerializedName("fast_pair_device_metadata") val deviceMeta: FastPairDeviceMetadataData?
    ) {
        fun toFastPairAntispoofKeyDeviceMetadata(): FastPairAntispoofKeyDeviceMetadata {
            return FastPairAntispoofKeyDeviceMetadata.Builder()
                .setAntispoofPublicKey(antispoofPublicKey?.base64Decode())
                .setFastPairDeviceMetadata(deviceMeta?.toFastPairDeviceMetadata())
                .build()
        }
    }

    data class FastPairDeviceMetadataData(
        @SerializedName("ble_tx_power") val bleTxPower: Int,
        @SerializedName("connect_success_companion_app_installed") val compAppInstalled: String?,
        @SerializedName("connect_success_companion_app_not_installed") val comAppNotIns: String?,
        @SerializedName("device_type") val deviceType: Int,
        @SerializedName("download_companion_app_description") val downloadComApp: String?,
        @SerializedName("fail_connect_go_to_settings_description") val failConnectDes: String?,
        @SerializedName("image_url") val imageUrl: String?,
        @SerializedName("initial_notification_description") val initNotification: String?,
        @SerializedName("initial_notification_description_no_account") val initNoAccount: String?,
        @SerializedName("initial_pairing_description") val initialPairingDescription: String?,
        @SerializedName("intent_uri") val intentUri: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("open_companion_app_description") val openCompanionAppDescription: String?,
        @SerializedName("retroactive_pairing_description") val retroactivePairingDes: String?,
        @SerializedName("subsequent_pairing_description") val subsequentPairingDescription: String?,
        @SerializedName("trigger_distance") val triggerDistance: Double,
        @SerializedName("case_url") val trueWirelessImageUrlCase: String?,
        @SerializedName("left_bud_url") val trueWirelessImageUrlLeftBud: String?,
        @SerializedName("right_bud_url") val trueWirelessImageUrlRightBud: String?,
        @SerializedName("unable_to_connect_description") val unableToConnectDescription: String?,
        @SerializedName("unable_to_connect_title") val unableToConnectTitle: String?,
        @SerializedName("update_companion_app_description") val updateCompAppDes: String?,
        @SerializedName("wait_launch_companion_app_description") val waitLaunchCompApp: String?
    ) {
        constructor(meta: FastPairDeviceMetadata) : this(
            bleTxPower = meta.bleTxPower,
            compAppInstalled = meta.connectSuccessCompanionAppInstalled,
            comAppNotIns = meta.connectSuccessCompanionAppNotInstalled,
            deviceType = meta.deviceType,
            downloadComApp = meta.downloadCompanionAppDescription,
            failConnectDes = meta.failConnectGoToSettingsDescription,
            imageUrl = meta.imageUrl,
            initNotification = meta.initialNotificationDescription,
            initNoAccount = meta.initialNotificationDescriptionNoAccount,
            initialPairingDescription = meta.initialPairingDescription,
            intentUri = meta.intentUri,
            name = meta.name,
            openCompanionAppDescription = meta.openCompanionAppDescription,
            retroactivePairingDes = meta.retroactivePairingDescription,
            subsequentPairingDescription = meta.subsequentPairingDescription,
            triggerDistance = meta.triggerDistance.toDouble(),
            trueWirelessImageUrlCase = meta.trueWirelessImageUrlCase,
            trueWirelessImageUrlLeftBud = meta.trueWirelessImageUrlLeftBud,
            trueWirelessImageUrlRightBud = meta.trueWirelessImageUrlRightBud,
            unableToConnectDescription = meta.unableToConnectDescription,
            unableToConnectTitle = meta.unableToConnectTitle,
            updateCompAppDes = meta.updateCompanionAppDescription,
            waitLaunchCompApp = meta.waitLaunchCompanionAppDescription
        )

        fun toFastPairDeviceMetadata(): FastPairDeviceMetadata {
            return FastPairDeviceMetadata.Builder()
                .setBleTxPower(bleTxPower)
                .setConnectSuccessCompanionAppInstalled(compAppInstalled)
                .setConnectSuccessCompanionAppNotInstalled(comAppNotIns)
                .setDeviceType(deviceType)
                .setDownloadCompanionAppDescription(downloadComApp)
                .setFailConnectGoToSettingsDescription(failConnectDes)
                .setImageUrl(imageUrl)
                .setInitialNotificationDescription(initNotification)
                .setInitialNotificationDescriptionNoAccount(initNoAccount)
                .setInitialPairingDescription(initialPairingDescription)
                .setIntentUri(intentUri)
                .setName(name)
                .setOpenCompanionAppDescription(openCompanionAppDescription)
                .setRetroactivePairingDescription(retroactivePairingDes)
                .setSubsequentPairingDescription(subsequentPairingDescription)
                .setTriggerDistance(triggerDistance.toFloat())
                .setTrueWirelessImageUrlCase(trueWirelessImageUrlCase)
                .setTrueWirelessImageUrlLeftBud(trueWirelessImageUrlLeftBud)
                .setTrueWirelessImageUrlRightBud(trueWirelessImageUrlRightBud)
                .setUnableToConnectDescription(unableToConnectDescription)
                .setUnableToConnectTitle(unableToConnectTitle)
                .setUpdateCompanionAppDescription(updateCompAppDes)
                .setWaitLaunchCompanionAppDescription(waitLaunchCompApp)
                .build()
        }
    }

    data class FastPairDiscoveryItemData(
        @SerializedName("action_url") val actionUrl: String?,
        @SerializedName("action_url_type") val actionUrlType: Int,
        @SerializedName("app_name") val appName: String?,
        @SerializedName("authentication_public_key_secp256r1") val authenticationPublicKey: String?,
        @SerializedName("description") val description: String?,
        @SerializedName("device_name") val deviceName: String?,
        @SerializedName("display_url") val displayUrl: String?,
        @SerializedName("first_observation_timestamp_millis") val firstObservationMs: Long,
        @SerializedName("icon_fife_url") val iconFfeUrl: String?,
        @SerializedName("icon_png") val iconPng: String?,
        @SerializedName("id") val id: String?,
        @SerializedName("last_observation_timestamp_millis") val lastObservationMs: Long,
        @SerializedName("mac_address") val macAddress: String?,
        @SerializedName("package_name") val packageName: String?,
        @SerializedName("pending_app_install_timestamp_millis") val pendingAppInstallMs: Long,
        @SerializedName("rssi") val rssi: Int,
        @SerializedName("state") val state: Int,
        @SerializedName("title") val title: String?,
        @SerializedName("trigger_id") val triggerId: String?,
        @SerializedName("tx_power") val txPower: Int
    ) {
        constructor(item: FastPairDiscoveryItem) : this(
            actionUrl = item.actionUrl,
            actionUrlType = item.actionUrlType,
            appName = item.appName,
            authenticationPublicKey = item.authenticationPublicKeySecp256r1?.base64Encode(),
            description = item.description,
            deviceName = item.deviceName,
            displayUrl = item.displayUrl,
            firstObservationMs = item.firstObservationTimestampMillis,
            iconFfeUrl = item.iconFfeUrl,
            iconPng = item.iconPng?.base64Encode(),
            id = item.id,
            lastObservationMs = item.lastObservationTimestampMillis,
            macAddress = item.macAddress,
            packageName = item.packageName,
            pendingAppInstallMs = item.pendingAppInstallTimestampMillis,
            rssi = item.rssi,
            state = item.state,
            title = item.title,
            triggerId = item.triggerId,
            txPower = item.txPower
        )

        fun toFastPairDiscoveryItem(): FastPairDiscoveryItem {
            return FastPairDiscoveryItem.Builder()
                .setActionUrl(actionUrl)
                .setActionUrlType(actionUrlType)
                .setAppName(appName)
                .setAuthenticationPublicKeySecp256r1(authenticationPublicKey?.base64Decode())
                .setDescription(description)
                .setDeviceName(deviceName)
                .setDisplayUrl(displayUrl)
                .setFirstObservationTimestampMillis(firstObservationMs)
                .setIconFfeUrl(iconFfeUrl)
                .setIconPng(iconPng?.base64Decode())
                .setId(id)
                .setLastObservationTimestampMillis(lastObservationMs)
                .setMacAddress(macAddress)
                .setPackageName(packageName)
                .setPendingAppInstallTimestampMillis(pendingAppInstallMs)
                .setRssi(rssi)
                .setState(state)
                .setTitle(title)
                .setTriggerId(triggerId)
                .setTxPower(txPower)
                .build()
        }
    }
}

private fun String.base64Decode(): ByteArray = BaseEncoding.base64().decode(this)

private fun ByteArray.base64Encode(): String = BaseEncoding.base64().encode(this)
